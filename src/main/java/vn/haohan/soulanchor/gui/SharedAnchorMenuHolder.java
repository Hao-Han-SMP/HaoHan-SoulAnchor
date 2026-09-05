package vn.haohan.soulanchor.gui;

import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class SharedAnchorMenuHolder implements InventoryHolder {
    private final UUID sourceAnchorId;
    private final int page;

    public SharedAnchorMenuHolder(UUID sourceAnchorId, int page) {
        this.sourceAnchorId = sourceAnchorId;
        this.page = page;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }

    public UUID sourceAnchorId() {
        return sourceAnchorId;
    }

    public int page() {
        return page;
    }
}
