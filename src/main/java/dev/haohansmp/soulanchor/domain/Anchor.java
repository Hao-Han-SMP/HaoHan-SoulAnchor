package dev.haohansmp.soulanchor.domain;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;

/** Immutable persisted state for one Soul Anchor. */
public record Anchor(UUID id, UUID ownerId, String name, Location location, float yaw, float pitch,
        long createdAt, Set<UUID> sharedWith, UUID visualId, UUID interactionId) {
    public Anchor {
        sharedWith = Set.copyOf(sharedWith);
    }

    public Anchor withName(String newName) {
        return new Anchor(id, ownerId, newName, location, yaw, pitch, createdAt, sharedWith, visualId, interactionId);
    }

    public Anchor withSharedPlayer(UUID playerId) {
        Set<UUID> updated = new HashSet<>(sharedWith);
        updated.add(playerId);
        return new Anchor(id, ownerId, name, location, yaw, pitch, createdAt, updated, visualId, interactionId);
    }

    public Anchor withoutSharedPlayer(UUID playerId) {
        Set<UUID> updated = new HashSet<>(sharedWith);
        updated.remove(playerId);
        return new Anchor(id, ownerId, name, location, yaw, pitch, createdAt, updated, visualId, interactionId);
    }

    public Anchor withVisuals(UUID newVisualId, UUID newInteractionId) {
        return new Anchor(id, ownerId, name, location, yaw, pitch, createdAt, sharedWith,
                newVisualId, newInteractionId);
    }
}
