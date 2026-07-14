package com.cookiebuild.skywars.kit;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.cookiebuild.cookiedough.model.MinigameProgression;
import com.cookiebuild.cookiedough.service.MinigameProgressionService;

public final class KitManager {
    public static final String MINIGAME_KEY = MinigameProgressionService.SKYWARS;
    private final MinigameProgressionService progression;

    public KitManager(MinigameProgressionService progression) {
        this.progression = progression;
    }

    public boolean isUnlocked(UUID playerId, SkyWarsKit kit) {
        return kit.defaultUnlocked()
                || progression.hasUnlockedKit(playerId, MINIGAME_KEY, unlockKey(kit));
    }

    public boolean purchase(UUID playerId, SkyWarsKit kit) {
        return kit.defaultUnlocked() || progression.purchaseAndUnlockKit(
                playerId, MINIGAME_KEY, unlockKey(kit), kit.price());
    }

    public boolean select(UUID playerId, SkyWarsKit kit) {
        if (!isUnlocked(playerId, kit)) {
            return false;
        }
        progression.setLastSelectedKit(playerId, MINIGAME_KEY, kit.key(), 1);
        return true;
    }

    public SkyWarsKit selected(UUID playerId) {
        MinigameProgression stats = progression.getOrCreateStats(playerId, MINIGAME_KEY);
        SkyWarsKit selected = SkyWarsKit.fromName(stats.getLastSelectedKitName());
        return selected != null && isUnlocked(playerId, selected) ? selected : SkyWarsKit.SCOUT;
    }

    public SkyWarsKit equip(Player player) {
        SkyWarsKit kit = selected(player.getUniqueId());
        for (SkyWarsKit.KitItem item : kit.items()) {
            player.getInventory().addItem(new ItemStack(item.material(), item.amount()));
        }
        return kit;
    }

    private static String unlockKey(SkyWarsKit kit) {
        return "kit:" + kit.key();
    }
}
