package br.com.modtiktok.tiktokchaos.overlay;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** Read-only HTTP overlay bound exclusively to the loopback interface. */
public final class LocalOverlayServer implements AutoCloseable {
    private final Supplier<String> stateSupplier;
    private final String token = randomToken();
    private final String nonce = randomToken();
    private ExecutorService executor;
    private ServerSocket socket;

    public LocalOverlayServer(Supplier<String> stateSupplier) {
        this.stateSupplier = stateSupplier;
    }

    public synchronized void start(int configuredPort) throws IOException {
        if (isRunning()) return;
        ServerSocket server = new ServerSocket();
        server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), Math.max(0, configuredPort)), 16);
        socket = server;
        executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "TikTok-Chaos-Overlay");
            thread.setDaemon(true);
            return thread;
        });
        executor.execute(this::acceptLoop);
    }

    public synchronized boolean isRunning() {
        return socket != null && !socket.isClosed();
    }

    public synchronized String url() {
        return isRunning() ? "http://127.0.0.1:" + socket.getLocalPort() + "/" + token + "/" : "";
    }

    public String token() {
        return token;
    }

    private void acceptLoop() {
        while (isRunning()) {
            try {
                Socket client = socket.accept();
                client.setSoTimeout(2_000);
                handle(client);
            } catch (IOException ignored) {
                if (!isRunning()) return;
            }
        }
    }

    private void handle(Socket client) {
        try (client;
             BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(),
                     StandardCharsets.US_ASCII));
             OutputStream output = client.getOutputStream()) {
            if (!client.getInetAddress().isLoopbackAddress()) {
                respond(output, 403, "text/plain; charset=utf-8", "Forbidden", "default-src 'none'");
                return;
            }
            String firstLine = reader.readLine();
            if (firstLine == null || firstLine.length() > 4_096) return;
            int headerCount = 0;
            String header;
            while ((header = reader.readLine()) != null && !header.isEmpty() && headerCount++ < 32) {
                if (header.length() > 4_096) return;
            }
            String[] request = firstLine.split(" ", 3);
            if (request.length < 2 || !"GET".equals(request[0])) {
                respond(output, 405, "text/plain; charset=utf-8", "Method Not Allowed", "default-src 'none'");
                return;
            }
            String root = "/" + token + "/";
            if (request[1].equals(root + "state.json")) {
                respond(output, 200, "application/json; charset=utf-8", stateSupplier.get(),
                        "default-src 'none'; frame-ancestors 'none'");
            } else if (request[1].equals(root)) {
                respond(output, 200, "text/html; charset=utf-8", html(root),
                        "default-src 'none'; style-src 'unsafe-inline'; script-src 'nonce-" + nonce
                                + "'; connect-src 'self'; img-src 'none'; frame-ancestors 'none'");
            } else {
                respond(output, 404, "text/plain; charset=utf-8", "Not Found", "default-src 'none'");
            }
        } catch (IOException ignored) {
        }
    }

    private String html(String root) {
        return "<!doctype html><html><head><meta charset=utf-8><meta name=viewport content='width=device-width'>"
                + "<style>body{margin:0;background:transparent;color:#fff;font:700 22px sans-serif}"
                + ".card{display:inline-block;padding:14px 18px;border-left:5px solid #e83e8c;"
                + "background:#120d1ddd;border-radius:8px}.muted{font-size:14px;color:#cfc4d6}</style></head>"
                + "<body><div class=card><div id=status>TikTok Chaos</div><div class=muted id=detail></div></div>"
                + "<script nonce='" + nonce + "'>async function u(){try{let r=await fetch('" + root
                + "state.json',{cache:'no-store'}),s=await r.json();status.textContent=s.status+' • '+s.runState;"
                + "detail.textContent='Fila '+s.queue+' • Mobs '+s.mobs+' • Moedas '+s.coins}catch(e){}}"
                + "u();setInterval(u,1000)</script></body></html>";
    }

    private void respond(OutputStream output, int status, String contentType, String body, String csp)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String reason = switch (status) {
            case 200 -> "OK";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            default -> "Error";
        };
        String headers = "HTTP/1.1 " + status + " " + reason + "\r\nContent-Type: " + contentType
                + "\r\nContent-Length: " + bytes.length + "\r\nCache-Control: no-store"
                + "\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: " + csp
                + "\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.US_ASCII));
        output.write(bytes);
    }

    private static String randomToken() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        StringBuilder value = new StringBuilder();
        for (byte item : bytes) value.append(String.format(Locale.ROOT, "%02x", item));
        return value.toString();
    }

    @Override
    public synchronized void close() {
        ServerSocket current = socket;
        socket = null;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
            }
        }
        if (executor != null) executor.shutdownNow();
        executor = null;
    }
}
