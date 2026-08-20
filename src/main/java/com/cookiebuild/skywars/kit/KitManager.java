package com.cookiebuild.skywars.kit;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.cookiebuild.cookiedough.model.MinigameProgression;
import com.cookiebuild.cookiedough.service.MinigameProgressionService;
import com.cookiebuild.skywars.SkyWars;

/** Cached, coalesced access to the durable kit profile. All database methods are called asynchronously. */
public final class KitManager {
    public static final String MINIGAME_KEY = MinigameProgressionService.SKYWARS;
    private static final long CACHE_MILLIS = 30_000L;
    private static final long ACTION_COOLDOWN_MILLIS = 750L;
    private static final int MAX_TRACKED_PLAYERS = 4_096;

    private static final class ProfileLock {
        private final Object monitor = new Object();
        private final AtomicInteger users = new AtomicInteger();
    }

    public record Profile(int level, int experience, int nextLevelExperience, int coins,
            Set<SkyWarsKit> unlocked, SkyWarsKit selected, long loadedAtMillis) {
        public Profile {
            unlocked = Set.copyOf(unlocked);
        }

        public boolean isUnlocked(SkyWarsKit kit) {
            return kit.defaultUnlocked() || unlocked.contains(kit);
        }
    }

    private final MinigameProgressionService progression;
    private final Map<UUID, Profile> profiles = new ConcurrentHashMap<>();
    private final Set<UUID> profileLoads = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ProfileLock> profileLocks = new ConcurrentHashMap<>();
    private final Set<UUID> mutations = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> releasedProfiles = ConcurrentHashMap.newKeySet();
    private final Object cacheLifecycleLock = new Object();

    public KitManager(MinigameProgressionService progression) {
        this.progression = progression;
    }

    public void preload(UUID playerId) {
        synchronized (cacheLifecycleLock) {
            if (releasedProfiles.contains(playerId)
                    && (profileLoads.contains(playerId) || profileLocks.containsKey(playerId))) {
                return;
            }
            releasedProfiles.remove(playerId);
        }
        Profile cached = profiles.get(playerId);
        if (cached != null && System.currentTimeMillis() - cached.loadedAtMillis() < CACHE_MILLIS) {
            return;
        }
        if (!profileLoads.add(playerId)) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(SkyWars.getInstance(), () -> {
            try {
                loadProfile(playerId);
            } catch (RuntimeException error) {
                SkyWars.getInstance().getLogger().warning(
                        "Could not preload SkyWars kit profile for " + playerId + ": " + error.getMessage());
                cacheProfile(playerId, defaultProfile(), false);
            } finally {
                profileLoads.remove(playerId);
                finishReleaseIfIdle(playerId);
            }
        });
    }

    /** Performs durable reads and must only be invoked from an asynchronous task. */
    public Profile loadProfile(UUID playerId) {
        ProfileLock lock = acquireProfileLock(playerId);
        try {
            synchronized (lock.monitor) {
                Profile cached = profiles.get(playerId);
                if (cached != null && System.currentTimeMillis() - cached.loadedAtMillis() < CACHE_MILLIS) {
                    return cached;
                }
                return readProfile(playerId);
            }
        } finally {
            releaseProfileLock(playerId, lock);
        }
    }

    private Profile refreshProfile(UUID playerId) {
        ProfileLock lock = acquireProfileLock(playerId);
        try {
            synchronized (lock.monitor) {
                return readProfile(playerId);
            }
        } finally {
            releaseProfileLock(playerId, lock);
        }
    }

    private Profile readProfile(UUID playerId) {
        MinigameProgression stats = progression.getOrCreateStats(playerId, MINIGAME_KEY);
        EnumSet<SkyWarsKit> unlocked = EnumSet.noneOf(SkyWarsKit.class);
        Arrays.stream(SkyWarsKit.values())
                .filter(kit -> kit.defaultUnlocked() || stats.hasUnlockedKit(unlockKey(kit)))
                .forEach(unlocked::add);
        SkyWarsKit selected = SkyWarsKit.fromName(stats.getLastSelectedKitName());
        if (selected == null || !unlocked.contains(selected)) selected = SkyWarsKit.SCOUT;
        Profile loaded = new Profile(stats.getLevel(), stats.getExperience(), stats.getExperienceForNextLevel(),
                progression.getCoins(playerId, MINIGAME_KEY), unlocked, selected, System.currentTimeMillis());
        cacheProfile(playerId, loaded, true);
        return loaded;
    }

    public Profile cachedProfile(UUID playerId) {
        return profiles.get(playerId);
    }

    public boolean isProfileReady(UUID playerId) {
        return profiles.containsKey(playerId);
    }

    public boolean isUnlocked(UUID playerId, SkyWarsKit kit) {
        Profile profile = profiles.get(playerId);
        return kit.defaultUnlocked() || profile != null && profile.isUnlocked(kit);
    }

    /** Performs a durable mutation and must only be invoked from an asynchronous task. */
    public boolean purchase(UUID playerId, SkyWarsKit kit) {
        boolean purchased = kit.defaultUnlocked() || progression.purchaseAndUnlockKit(
                playerId, MINIGAME_KEY, unlockKey(kit), kit.price());
        if (purchased) {
            refreshProfile(playerId);
        }
        return purchased;
    }

    /** Performs a durable mutation and must only be invoked from an asynchronous task. */
    public boolean select(UUID playerId, SkyWarsKit kit) {
        Profile profile = profiles.get(playerId);
        if (profile == null) {
            profile = loadProfile(playerId);
        }
        if (!profile.isUnlocked(kit)) {
            return false;
        }
        progression.setLastSelectedKit(playerId, MINIGAME_KEY, kit.key(), 1);
        cacheProfile(playerId, new Profile(profile.level(), profile.experience(), profile.nextLevelExperience(),
                profile.coins(), profile.unlocked(), kit, System.currentTimeMillis()), true);
        return true;
    }

    public SkyWarsKit selected(UUID playerId) {
        Profile profile = profiles.get(playerId);
        return profile == null ? SkyWarsKit.SCOUT : profile.selected();
    }

    /** Never touches the database; safe on Paper's primary thread at match start. */
    public SkyWarsKit equip(Player player) {
        SkyWarsKit kit = selected(player.getUniqueId());
        for (SkyWarsKit.KitItem item : kit.items()) {
            player.getInventory().addItem(new ItemStack(item.material(), item.amount()));
        }
        return kit;
    }

    public boolean tryBeginMutation(UUID playerId) {
        long now = System.currentTimeMillis();
        if (cooldowns.getOrDefault(playerId, 0L) > now) {
            return false;
        }
        return mutations.add(playerId);
    }

    public void finishMutation(UUID playerId) {
        mutations.remove(playerId);
        synchronized (cacheLifecycleLock) {
            if (releasedProfiles.contains(playerId)) {
                finishReleaseIfIdle(playerId);
                return;
            }
            if (cooldowns.size() >= MAX_TRACKED_PLAYERS && !cooldowns.containsKey(playerId)) {
                cooldowns.keySet().stream().findFirst().ifPresent(cooldowns::remove);
            }
            cooldowns.put(playerId, System.currentTimeMillis() + ACTION_COOLDOWN_MILLIS);
        }
    }

    /** Releases every per-player cache entry; an already-running load is prevented from repopulating it. */
    public void release(UUID playerId) {
        synchronized (cacheLifecycleLock) {
            if (profileLoads.contains(playerId) || profileLocks.containsKey(playerId) || mutations.contains(playerId)) {
                releasedProfiles.add(playerId);
            }
            profiles.remove(playerId);
            cooldowns.remove(playerId);
        }
    }

    public void clear() {
        synchronized (cacheLifecycleLock) {
            releasedProfiles.addAll(profileLoads);
            releasedProfiles.addAll(profileLocks.keySet());
            releasedProfiles.addAll(mutations);
            profiles.clear();
            cooldowns.clear();
        }
    }

    int trackedActionCount() {
        return mutations.size() + cooldowns.size();
    }

    private ProfileLock acquireProfileLock(UUID playerId) {
        return profileLocks.compute(playerId, (ignored, existing) -> {
            ProfileLock lock = existing == null ? new ProfileLock() : existing;
            lock.users.incrementAndGet();
            return lock;
        });
    }

    private void releaseProfileLock(UUID playerId, ProfileLock lock) {
        int remaining = lock.users.decrementAndGet();
        if (remaining == 0) {
            profileLocks.remove(playerId, lock);
            finishReleaseIfIdle(playerId);
        }
    }

    private void cacheProfile(UUID playerId, Profile profile, boolean replace) {
        synchronized (cacheLifecycleLock) {
            if (releasedProfiles.contains(playerId)) return;
            if (profiles.size() >= MAX_TRACKED_PLAYERS && !profiles.containsKey(playerId)) {
                profiles.keySet().stream().findFirst().ifPresent(profiles::remove);
            }
            if (replace) profiles.put(playerId, profile);
            else profiles.putIfAbsent(playerId, profile);
        }
    }

    void finishReleaseIfIdle(UUID playerId) {
        synchronized (cacheLifecycleLock) {
            if (!profileLoads.contains(playerId)
                    && !profileLocks.containsKey(playerId)
                    && !mutations.contains(playerId)) {
                releasedProfiles.remove(playerId);
            }
        }
    }

    static String unlockKey(SkyWarsKit kit) {
        return "kit:" + kit.key();
    }

    private static Profile defaultProfile() {
        return new Profile(1, 0, 100, 0, Set.of(SkyWarsKit.SCOUT), SkyWarsKit.SCOUT, 0L);
    }
}
