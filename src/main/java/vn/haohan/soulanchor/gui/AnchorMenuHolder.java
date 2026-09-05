package vn.haohan.soulanchor.gui;

import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class AnchorMenuHolder implements InventoryHolder {
    private final UUID sourceAnchorId;

    public AnchorMenuHolder(UUID sourceAnchorId) {
        this.sourceAnchorId = sourceAnchorId;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }

    public UUID sourceAnchorId() {
        return sourceAnchorId;
    }
}
