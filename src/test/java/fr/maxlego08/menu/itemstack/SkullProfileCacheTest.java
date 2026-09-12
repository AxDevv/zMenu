package fr.maxlego08.menu.itemstack;

import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SkullProfileCacheTest {
    @Test void coalescesPendingRequestsAndServesTheCompletedSkin() {
        var calls = new AtomicInteger();
        var update = new CompletableFuture<PlayerProfile>();
        var original = profile(true, calls, update);
        var cache = new SkullProfileCache();
        assertNull(cache.resolve("GonqQq", original));
        assertNull(cache.resolve("gonqqq", original));
        assertEquals(1, calls.get());
        var completed = profile(false, calls, update);
        update.complete(completed);
        assertSame(completed, cache.resolve("GonqQq", original));
        assertEquals(1, calls.get());
    }

    @Test void cachesFailuresInsteadOfRetryingOnEveryRender() {
        var calls = new AtomicInteger();
        var original = profile(true, calls, CompletableFuture.failedFuture(new IllegalStateException("429")));
        var cache = new SkullProfileCache();
        for (int i = 0; i < 20; i++) assertNull(cache.resolve("player", original));
        assertEquals(1, calls.get());
    }

    private PlayerProfile profile(boolean empty, AtomicInteger calls, CompletableFuture<PlayerProfile> update) {
        var textures = (PlayerTextures) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{PlayerTextures.class}, (proxy, method, args) -> {
                if (method.getName().equals("isEmpty")) return empty;
                throw new UnsupportedOperationException(method.getName());
            });
        return (PlayerProfile) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{PlayerProfile.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getTextures" -> textures;
                case "update" -> { calls.incrementAndGet(); yield update; }
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }
}
