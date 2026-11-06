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
        if (anchor.location().getWorld() == null) {
            return anchor;
        }
        Location displayLoc = anchor.location().clone().add(0.5D, 0.5D, 0.5D);
        if (!displayLoc.isChunkLoaded()) {
            return anchor;
        }

        ItemDisplay display = null;
        Interaction interaction = null;

        Entity existingDisplay = anchor.visualId() == null ? null : Bukkit.getEntity(anchor.visualId());
        if (existingDisplay instanceof ItemDisplay d && d.isValid() && !d.isDead()) {
            display = d;
        }
        Entity existingInteraction = anchor.interactionId() == null ? null : Bukkit.getEntity(anchor.interactionId());
        if (existingInteraction instanceof Interaction i && i.isValid() && !i.isDead()) {
            interaction = i;
        }

        // Search nearby to discover existing entities by PDC tag and prune any duplicate overlays.
        for (Entity e : displayLoc.getWorld().getNearbyEntities(displayLoc, 1.0D, 1.0D, 1.0D)) {
            String tag = e.getPersistentDataContainer().get(items.anchorIdKey(), PersistentDataType.STRING);
            if (!anchor.id().toString().equals(tag)) {
                continue;
            }
            if (e instanceof ItemDisplay d && d.isValid() && !d.isDead()) {
                if (display == null) {
                    display = d;
                } else if (!display.getUniqueId().equals(d.getUniqueId())) {
                    d.remove();
                }
            } else if (e instanceof Interaction i && i.isValid() && !i.isDead()) {
                if (interaction == null) {
                    interaction = i;
                } else if (!interaction.getUniqueId().equals(i.getUniqueId())) {
                    i.remove();
                }
            }
        }

        if (display == null) {
            display = displayLoc.getWorld().spawn(
                    displayLoc, ItemDisplay.class,
                    entity -> configureDisplay(entity, anchor));
        } else {
            if (display.getLocation().distanceSquared(displayLoc) > 0.001D) {
                display.teleport(displayLoc);
            }
        }

        Location interactionLoc = anchor.location().clone().add(0.5D, 0.2D, 0.5D);
        if (interaction == null) {
            interaction = anchor.location().getWorld().spawn(
                    interactionLoc, Interaction.class,
                    entity -> configureInteraction(entity, anchor.id()));
        } else {
            if (interaction.getLocation().distanceSquared(interactionLoc) > 0.001D) {
                interaction.teleport(interactionLoc);
            }
        }
        return anchor.withVisuals(display.getUniqueId(), interaction.getUniqueId());
    }

    public void refreshAll() {
        for (Anchor anchor : new ArrayList<>(anchors.values())) {
            Entity display = anchor.visualId() == null ? null : Bukkit.getEntity(anchor.visualId());
            if (display instanceof ItemDisplay d && d.isValid()) {
                configureDisplay(d, anchor);
            }
            Entity interaction = anchor.interactionId() == null ? null : Bukkit.getEntity(anchor.interactionId());
            if (interaction instanceof Interaction i && i.isValid()) {
                configureInteraction(i, anchor.id());
            }
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

    public void cleanupLocation(Anchor anchor) {
        if (anchor == null || anchor.location() == null || anchor.location().getWorld() == null) {
            return;
        }
        Location loc = anchor.location().clone().add(0.5D, 0.5D, 0.5D);
        if (!loc.isChunkLoaded()) {
            return;
        }
        for (Entity e : loc.getWorld().getNearbyEntities(loc, 1.0D, 1.0D, 1.0D)) {
            String tag = e.getPersistentDataContainer().get(items.anchorIdKey(), PersistentDataType.STRING);
            if (anchor.id().toString().equals(tag)
                    || (anchor.visualId() != null && anchor.visualId().equals(e.getUniqueId()))
                    || (anchor.interactionId() != null && anchor.interactionId().equals(e.getUniqueId()))) {
                e.remove();
            }
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
        entity.setDisplayWidth(2.0F);
        entity.setDisplayHeight(2.0F);
        entity.setViewRange(64.0F);
        entity.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
        entity.setInterpolationDuration(0);
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
