package vn.haohan.soulanchor.service;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import vn.haohan.soulanchor.domain.Anchor;

/** Manages the server-side entities that render and receive interaction for an anchor. */
public final class AnchorVisualService {
    private final JavaPlugin plugin;
    private final ItemService items;
    private final Map<UUID, Anchor> anchors;

    public AnchorVisualService(JavaPlugin plugin, ItemService items, Map<UUID, Anchor> anchors) {
        this.plugin = plugin;
        this.items = items;
        this.anchors = anchors;
    }

    public Anchor spawn(Anchor anchor) {
        Entity existingDisplay = anchor.visualId() == null ? null : Bukkit.getEntity(anchor.visualId());
        Entity existingInteraction = anchor.interactionId() == null ? null : Bukkit.getEntity(anchor.interactionId());
        UUID visualId = existingDisplay instanceof ItemDisplay ? existingDisplay.getUniqueId() : null;
        UUID interactionId = existingInteraction instanceof Interaction ? existingInteraction.getUniqueId() : null;

        if (existingDisplay instanceof ItemDisplay display) {
            configureDisplay(display, anchor);
        }
        if (existingInteraction instanceof Interaction interaction) {
            configureInteraction(interaction, anchor.id());
        }
        if (visualId == null) {
            ItemDisplay display = anchor.location().getWorld().spawn(
                    anchor.location().clone().add(0.5D, 0.5D, 0.5D), ItemDisplay.class,
                    entity -> configureDisplay(entity, anchor));
            visualId = display.getUniqueId();
        }
        if (interactionId == null) {
            Interaction interaction = anchor.location().getWorld().spawn(
                    anchor.location().clone().add(0.5D, 0.2D, 0.5D), Interaction.class,
                    entity -> configureInteraction(entity, anchor.id()));
            interactionId = interaction.getUniqueId();
        }
        return anchor.withVisuals(visualId, interactionId);
    }

    public void refreshAll() {
        for (Anchor anchor : new ArrayList<>(anchors.values())) {
            anchors.put(anchor.id(), spawn(anchor));
        }
    }

    public Optional<Anchor> fromEntity(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(items.anchorIdKey(), PersistentDataType.STRING);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(anchors.get(UUID.fromString(raw)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public void removeEntity(UUID id) {
        if (id == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(id);
        if (entity != null) {
            entity.remove();
        }
    }

    private void configureDisplay(ItemDisplay entity, Anchor anchor) {
        entity.teleport(anchor.location().clone().add(0.5D, 0.5D, 0.5D));
        entity.setItemStack(items.createAnchorDisplayItem());
        entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        float scaleX = (float) plugin.getConfig().getDouble("visuals.scale-x", 1.0D);
        float scaleY = (float) plugin.getConfig().getDouble("visuals.scale-y", 0.877D);
        float scaleZ = (float) plugin.getConfig().getDouble("visuals.scale-z", 1.0D);
        entity.setTransformation(new org.bukkit.util.Transformation(
                new org.joml.Vector3f(), new org.joml.Quaternionf(),
                new org.joml.Vector3f(scaleX, scaleY, scaleZ), new org.joml.Quaternionf()));
        entity.setGravity(false);
        entity.setPersistent(true);
        entity.setSilent(true);
        entity.getPersistentDataContainer().set(items.anchorIdKey(), PersistentDataType.STRING, anchor.id().toString());
    }

    private void configureInteraction(Interaction entity, UUID anchorId) {
        entity.setInteractionWidth((float) plugin.getConfig().getDouble("visuals.interaction-width", 1.2D));
        entity.setInteractionHeight((float) plugin.getConfig().getDouble("visuals.interaction-height", 1.1D));
        entity.setGravity(false);
        entity.setPersistent(true);
        entity.setSilent(true);
        entity.getPersistentDataContainer().set(items.anchorIdKey(), PersistentDataType.STRING, anchorId.toString());
    }
}
