package br.com.modtiktok.tiktokchaos.avatar;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporaryAvatarCacheTest {
    @Test
    void acceptsOnlyHttpsAllowlistedHostsWithoutCredentialsOrCustomPorts() {
        List<String> allowlist = List.of("*.tiktokcdn.com", "images.example.com");

        assertTrue(TemporaryAvatarCache.isAllowed(
                URI.create("https://p16-sign.tiktokcdn.com/avatar.png"), allowlist));
        assertTrue(TemporaryAvatarCache.isAllowed(URI.create("https://images.example.com/a.png"), allowlist));
        assertFalse(TemporaryAvatarCache.isAllowed(URI.create("http://images.example.com/a.png"), allowlist));
        assertFalse(TemporaryAvatarCache.isAllowed(URI.create("https://evil.example/a.png"), allowlist));
        assertFalse(TemporaryAvatarCache.isAllowed(
                URI.create("https://images.example.com:8443/a.png"), allowlist));
        assertFalse(TemporaryAvatarCache.isAllowed(
                URI.create("https://user:pass@images.example.com/a.png"), allowlist));
    }
}
