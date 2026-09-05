package vn.haohan.soulanchor.gui;

import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class TrustMenuHolder implements InventoryHolder {
    private final UUID anchorId;
    private final int page;

    public TrustMenuHolder(UUID anchorId, int page) {
        this.anchorId = anchorId;
        this.page = page;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }

    public UUID anchorId() {
        return anchorId;
    }

    public int page() {
        return page;
    }
}
