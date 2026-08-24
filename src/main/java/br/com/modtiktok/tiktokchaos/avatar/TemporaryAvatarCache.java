package br.com.modtiktok.tiktokchaos.avatar;

import br.com.modtiktok.tiktokchaos.config.TikTokChaosConfig;

import javax.imageio.ImageIO;
import javax.net.ssl.HttpsURLConnection;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Opt-in, session-only avatar downloader with strict HTTPS and image limits. */
public final class TemporaryAvatarCache implements AutoCloseable {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "TikTok-Chaos-Avatars");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Path> entries = new ConcurrentHashMap<>();
    private volatile Path directory;
    private volatile long generation;

    public CompletableFuture<Optional<Path>> request(String viewerKey, String avatarUrl,
                                                      TikTokChaosConfig.Avatars settings) {
        if (settings == null || !settings.enabled || viewerKey == null || viewerKey.isBlank()
                || avatarUrl == null || avatarUrl.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Path existing = entries.get(viewerKey);
        if (existing != null && Files.isRegularFile(existing)) {
            return CompletableFuture.completedFuture(Optional.of(existing));
        }
        long requestedGeneration = generation;
        return CompletableFuture.supplyAsync(
                () -> download(viewerKey, avatarUrl, settings, requestedGeneration), executor);
    }

    public Optional<Path> find(String viewerKey) {
        Path path = entries.get(viewerKey);
        return path != null && Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    private Optional<Path> download(String viewerKey, String avatarUrl, TikTokChaosConfig.Avatars settings,
                                    long requestedGeneration) {
        try {
            URI uri = URI.create(avatarUrl);
            if (!isAllowed(uri, settings.allowedHosts)) return Optional.empty();
            if (!hasOnlyPublicAddresses(uri.getHost())) return Optional.empty();
            HttpsURLConnection connection = (HttpsURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(4_000);
            connection.setReadTimeout(4_000);
            connection.setRequestProperty("Accept", "image/png,image/jpeg,image/webp");
            connection.setRequestProperty("User-Agent", "TikTok-Chaos-Avatar/1");
            int status = connection.getResponseCode();
            if (status != 200) return Optional.empty();
            int maximumBytes = Math.max(16_384, Math.min(1_048_576, settings.maxBytes));
            long contentLength = connection.getContentLengthLong();
            if (contentLength > maximumBytes) return Optional.empty();
            String contentType = connection.getContentType();
            if (contentType == null || !contentType.toLowerCase().startsWith("image/")) return Optional.empty();
            byte[] bytes;
            try (InputStream input = connection.getInputStream()) {
                bytes = readBounded(input, maximumBytes);
            } finally {
                connection.disconnect();
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            int maximumDimension = Math.max(32, Math.min(1_024, settings.maxDimension));
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0
                    || image.getWidth() > maximumDimension || image.getHeight() > maximumDimension) {
                return Optional.empty();
            }
            return persistIfCurrent(viewerKey, image, requestedGeneration);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public static boolean isAllowed(URI uri, List<String> allowedHosts) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                || uri.getHost() == null || uri.getFragment() != null
                || uri.getPort() != -1 && uri.getPort() != 443 || allowedHosts == null) {
            return false;
        }
        String host = uri.getHost().toLowerCase();
        for (String allowed : allowedHosts) {
            if (allowed == null) continue;
            String rule = allowed.strip().toLowerCase();
            if (rule.startsWith("*.")) {
                String suffix = rule.substring(1);
                if (host.endsWith(suffix) && host.length() > suffix.length()) return true;
            } else if (host.equals(rule)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasOnlyPublicAddresses(String host) throws IOException {
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        }
        return true;
    }

    private byte[] readBounded(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 64 * 1024));
        byte[] buffer = new byte[8_192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > limit) throw new IOException("Avatar excede o limite");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private synchronized Path ensureDirectory() throws IOException {
        if (directory == null) directory = Files.createTempDirectory("tiktok-chaos-avatars-");
        return directory;
    }

    private synchronized Optional<Path> persistIfCurrent(String viewerKey, BufferedImage image,
                                                          long requestedGeneration) throws Exception {
        if (generation != requestedGeneration) return Optional.empty();
        Path target = ensureDirectory().resolve(hash(viewerKey) + ".png");
        if (!ImageIO.write(image, "png", target.toFile())) return Optional.empty();
        entries.put(viewerKey, target);
        return Optional.of(target);
    }

    private String hash(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte item : digest) builder.append(String.format("%02x", item));
        return builder.toString();
    }

    @Override
    public void close() {
        executor.shutdownNow();
        clear();
    }

    public synchronized void clear() {
        generation++;
        entries.clear();
        Path cacheDirectory = directory;
        directory = null;
        if (cacheDirectory == null || !Files.isDirectory(cacheDirectory)) return;
        try (var paths = Files.walk(cacheDirectory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
