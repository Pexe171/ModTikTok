package br.com.modtiktok.tiktokchaos.overlay;

import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalOverlayServerTest {
    @Test
    void bindsToLoopbackUsesTokenAndExposesReadOnlyState() throws Exception {
        try (LocalOverlayServer server = new LocalOverlayServer(() -> "{\"status\":\"ok\"}")) {
            server.start(0);
            assertTrue(server.url().startsWith("http://127.0.0.1:"));

            HttpURLConnection state = (HttpURLConnection) URI.create(server.url() + "state.json")
                    .toURL().openConnection();
            assertEquals(200, state.getResponseCode());
            assertEquals("{\"status\":\"ok\"}", new String(state.getInputStream().readAllBytes()));
            assertEquals("no-store", state.getHeaderField("Cache-Control"));

            HttpURLConnection wrongToken = (HttpURLConnection) URI.create(server.url().replace(server.token(),
                    "wrong-token")).toURL().openConnection();
            assertEquals(404, wrongToken.getResponseCode());

            HttpURLConnection post = (HttpURLConnection) URI.create(server.url()).toURL().openConnection();
            post.setRequestMethod("POST");
            assertEquals(405, post.getResponseCode());
        }
    }
}
