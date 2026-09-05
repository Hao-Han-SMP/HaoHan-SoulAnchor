package vn.haohan.soulanchor.repository;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import vn.haohan.soulanchor.domain.Anchor;
import vn.haohan.soulanchor.domain.SharedAnchorGroup;

/** Owns anchor state, indexes, and persistence. The rest of the plugin never writes anchors.yml directly. */
public final class AnchorRepository {
    private final JavaPlugin plugin;
    private final File file;
    private final FileConfiguration configuration;
    private final Map<UUID, Anchor> byId = new HashMap<>();
    private final Map<String, UUID> byLocation = new HashMap<>();
    private final List<UUID> particleOrder = new ArrayList<>();

    public AnchorRepository(JavaPlugin plugin, File file) {
        this.plugin = plugin;
        this.file = file;
        this.configuration = YamlConfiguration.loadConfiguration(file);
    }

    public Map<UUID, Anchor> byId() {
        return byId;
    }

    public Map<String, UUID> byLocation() {
        return byLocation;
    }

    public List<UUID> particleOrder() {
        return particleOrder;
    }

    public void load(Material blockMaterial, Function<Anchor, Anchor> spawnVisuals, Consumer<UUID> removeEntity) {
        byId.clear();
        byLocation.clear();
        particleOrder.clear();
        ConfigurationSection section = configuration.getConfigurationSection("anchors");
        if (section == null) {
            return;
        }

        boolean removedStaleAnchor = false;
        for (String key : section.getKeys(false)) {
            ConfigurationSection node = section.getConfigurationSection(key);
            if (node == null) {
                continue;
            }
            World world = world(node);
            if (world == null) {
                plugin.getLogger().warning("Skipping Soul Anchor " + key + " because its world is not loaded.");
                continue;
            }

            Location location = new Location(world, node.getInt("x"), node.getInt("y"), node.getInt("z"));
            Set<UUID> sharedWith = node.getStringList("shared-with").stream()
                    .map(this::readUuid)
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            Anchor anchor = new Anchor(
                    UUID.fromString(key),
                    UUID.fromString(node.getString("owner")),
                    node.getString("name", "Soul Anchor"),
                    location,
                    (float) node.getDouble("yaw", 0D),
                    (float) node.getDouble("pitch", 0D),
                    node.getLong("created-at", System.currentTimeMillis()),
                    sharedWith,
                    readUuid(node.getString("visual-uuid")),
                    readUuid(node.getString("interaction-uuid")));

            Material current = location.getBlock().getType();
            if (current == Material.GRINDSTONE || current == Material.JIGSAW) {
                location.getBlock().setType(blockMaterial, false);
            } else if (current != blockMaterial) {
                removeEntity.accept(anchor.visualId());
                removeEntity.accept(anchor.interactionId());
                configuration.set("anchors." + key, null);
                removedStaleAnchor = true;
                continue;
            }

            Anchor visualized = spawnVisuals.apply(anchor);
            byId.put(visualized.id(), visualized);
            byLocation.put(locationKey(location), visualized.id());
            particleOrder.add(visualized.id());
        }

        if (removedStaleAnchor) {
            save();
        }
    }

    public void save() {
        configuration.set("anchors", null);
        for (Anchor anchor : byId.values()) {
            String path = "anchors." + anchor.id();
            Location location = anchor.location();
            configuration.set(path + ".owner", anchor.ownerId().toString());
            configuration.set(path + ".name", anchor.name());
            configuration.set(path + ".world-uuid", location.getWorld().getUID().toString());
            configuration.set(path + ".world-name", location.getWorld().getName());
            configuration.set(path + ".x", location.getBlockX());
            configuration.set(path + ".y", location.getBlockY());
            configuration.set(path + ".z", location.getBlockZ());
            configuration.set(path + ".yaw", anchor.yaw());
            configuration.set(path + ".pitch", anchor.pitch());
            configuration.set(path + ".created-at", anchor.createdAt());
            configuration.set(path + ".shared-with", anchor.sharedWith().stream().map(UUID::toString).sorted().toList());
            configuration.set(path + ".visual-uuid", anchor.visualId() == null ? null : anchor.visualId().toString());
            configuration.set(path + ".interaction-uuid",
                    anchor.interactionId() == null ? null : anchor.interactionId().toString());
        }
        try {
            configuration.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save anchors.yml: " + exception.getMessage());
        }
    }

    public Optional<Anchor> at(Block block) {
        return Optional.ofNullable(byId.get(byLocation.get(locationKey(block.getLocation()))));
    }

    public boolean isStillPlaced(Anchor anchor, Material blockMaterial) {
        return anchor != null
                && byId.containsKey(anchor.id())
                && anchor.location().getBlock().getType() == blockMaterial
                && byLocation.containsKey(locationKey(anchor.location()));
    }

    public void put(Anchor anchor) {
        Anchor previous = byId.put(anchor.id(), anchor);
        if (previous != null && !previous.location().equals(anchor.location())) {
            byLocation.remove(locationKey(previous.location()));
        }
        byLocation.put(locationKey(anchor.location()), anchor.id());
        if (!particleOrder.contains(anchor.id())) {
            particleOrder.add(anchor.id());
        }
    }

    public Anchor remove(UUID id, Consumer<UUID> removeEntity) {
        Anchor anchor = byId.remove(id);
        if (anchor == null) {
            return null;
        }
        byLocation.remove(locationKey(anchor.location()));
        particleOrder.remove(id);
        removeEntity.accept(anchor.visualId());
        removeEntity.accept(anchor.interactionId());
        save();
        return anchor;
    }

    public List<Anchor> owned(UUID ownerId) {
        return byId.values().stream()
                .filter(anchor -> anchor.ownerId().equals(ownerId))
                .sorted(Comparator.comparingLong(Anchor::createdAt))
                .toList();
    }

    public List<Anchor> accessible(UUID playerId) {
        return byId.values().stream()
                .filter(anchor -> canAccess(playerId, anchor))
                .sorted(Comparator.comparing((Anchor anchor) -> !anchor.ownerId().equals(playerId))
                        .thenComparingLong(Anchor::createdAt))
                .toList();
    }

    public List<Anchor> shared(UUID playerId, Function<UUID, String> playerName) {
        return byId.values().stream()
                .filter(anchor -> !anchor.ownerId().equals(playerId) && anchor.sharedWith().contains(playerId))
                .sorted(Comparator.comparing((Anchor anchor) -> playerName.apply(anchor.ownerId()),
                        String.CASE_INSENSITIVE_ORDER).thenComparingLong(Anchor::createdAt))
                .toList();
    }

    public List<SharedAnchorGroup> sharedGroups(UUID playerId, Function<UUID, String> playerName) {
        Map<UUID, List<Anchor>> grouped = new HashMap<>();
        for (Anchor anchor : shared(playerId, playerName)) {
            grouped.computeIfAbsent(anchor.ownerId(), ignored -> new ArrayList<>()).add(anchor);
        }
        return grouped.entrySet().stream()
                .sorted(Comparator.comparing(entry -> playerName.apply(entry.getKey()), String.CASE_INSENSITIVE_ORDER))
                .map(entry -> new SharedAnchorGroup(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
    }

    public boolean canAccess(UUID playerId, Anchor anchor) {
        return anchor.ownerId().equals(playerId) || anchor.sharedWith().contains(playerId);
    }

    public Optional<Anchor> findOwned(UUID ownerId, String nameOrId) {
        String query = nameOrId.toLowerCase(java.util.Locale.ROOT);
        return owned(ownerId).stream()
                .filter(anchor -> anchor.id().toString().equalsIgnoreCase(nameOrId)
                        || anchor.name().toLowerCase(java.util.Locale.ROOT).equals(query))
                .findFirst();
    }

    private World world(ConfigurationSection node) {
        String rawUuid = node.getString("world-uuid");
        World world = rawUuid == null ? null : Bukkit.getWorld(readUuid(rawUuid));
        return world == null ? Bukkit.getWorld(node.getString("world-name", "")) : world;
    }

    private UUID readUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String locationKey(Location location) {
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":"
                + location.getBlockZ();
    }
}
