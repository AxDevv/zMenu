package fr.maxlego08.menu.itemstack;

import org.bukkit.profile.PlayerProfile;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Coalesces skin requests; menu rendering never waits for Mojang. */
public final class SkullProfileCache {
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(16, 0.75F, true);

    public synchronized PlayerProfile resolve(String name, PlayerProfile original) {
        if (!original.getTextures().isEmpty()) return original;
        String key = name.toLowerCase(Locale.ROOT);
        long now = System.nanoTime();
        Entry cached = entries.get(key);
        if (cached == null || now - cached.expiresAt() >= 0) {
            CompletableFuture<PlayerProfile> update = original.update().handle((profile, error) ->
                error == null && profile != null && !profile.getTextures().isEmpty() ? profile : null);
            cached = new Entry(update, now + TimeUnit.MINUTES.toNanos(5));
            entries.put(key, cached);
            if (entries.size() > 1024) entries.pollFirstEntry();
        }
        return cached.profile().getNow(null);
    }

    public synchronized void clear() {
        entries.clear();
    }

    private record Entry(CompletableFuture<PlayerProfile> profile, long expiresAt) {}
}
