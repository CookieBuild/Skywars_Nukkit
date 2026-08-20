package com.cookiebuild.skywars.ui;

import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class KitMenuHolder implements InventoryHolder {
    private final UUID playerId;
    private Inventory inventory;

    KitMenuHolder(UUID playerId) {
        this.playerId = playerId;
    }

    UUID playerId() {
        return playerId;
    }

    void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
