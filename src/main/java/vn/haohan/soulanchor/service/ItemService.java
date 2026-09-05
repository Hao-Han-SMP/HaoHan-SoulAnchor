package vn.haohan.soulanchor.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import vn.haohan.soulanchor.domain.Anchor;

/** Creates and reads all plugin-owned item data. NBT details do not leak into event handlers. */
public final class ItemService {
    private final JavaPlugin plugin;
    private final NamespacedKey itemTypeKey;
    private final NamespacedKey anchorIdKey;
    private final NamespacedKey anchorNameKey;
    private final NamespacedKey trustedPlayersKey;
    private final NamespacedKey trustTargetKey;
    private final Function<String, String> color;
    private final Function<String, String> sanitizeName;

    public ItemService(JavaPlugin plugin, Function<String, String> color, Function<String, String> sanitizeName) {
        this.plugin = plugin;
        this.itemTypeKey = new NamespacedKey(plugin, "item_type");
        this.anchorIdKey = new NamespacedKey(plugin, "anchor_id");
        this.anchorNameKey = new NamespacedKey(plugin, "anchor_name");
        this.trustedPlayersKey = new NamespacedKey(plugin, "trusted_players");
        this.trustTargetKey = new NamespacedKey(plugin, "trust_target");
        this.color = color;
        this.sanitizeName = sanitizeName;
    }

    public NamespacedKey anchorIdKey() {
        return anchorIdKey;
    }

    public ItemStack createAnchorItem(int amount) {
        FileConfiguration config = plugin.getConfig();
        ItemStack item = new ItemStack(anchorMaterial(), amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color.apply(config.getString("item.display-name", "&b&lSoul Anchor")));
        meta.setLore(config.getStringList("item.lore").stream().map(color).toList());
        NamespacedKey itemModel = NamespacedKey.fromString(config.getString("item.item-model", "haohan:soul_anchor"));
        if (itemModel != null) {
            meta.setItemModel(itemModel);
        }
        meta.setCustomModelData(config.getInt("item.custom-model-data", 910001));
        meta.getPersistentDataContainer().set(itemTypeKey, PersistentDataType.STRING,
                config.getString("item.id", "haohan:soul_anchor"));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createPortableAnchorItem(Anchor anchor) {
        FileConfiguration config = plugin.getConfig();
        ItemStack item = createAnchorItem(1);
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(anchorNameKey, PersistentDataType.STRING, anchor.name());
        if (!anchor.sharedWith().isEmpty()) {
            String trusted = anchor.sharedWith().stream().map(UUID::toString).sorted().collect(Collectors.joining(","));
            pdc.set(trustedPlayersKey, PersistentDataType.STRING, trusted);
        }
        List<String> lore = new ArrayList<>(meta.getLore() == null ? List.of() : meta.getLore());
        lore.add("");
        lore.add(color.apply("&7Saved name: &f" + anchor.name()));
        lore.add(color.apply("&7Saved trust: &f" + anchor.sharedWith().size() + " player(s)"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public String readPortableAnchorName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String name = item.getItemMeta().getPersistentDataContainer().get(anchorNameKey, PersistentDataType.STRING);
        return name == null || name.isBlank() ? null : sanitizeName.apply(name);
    }

    public Set<UUID> readPortableTrustedPlayers(ItemStack item) {
        Set<UUID> trustedPlayers = new HashSet<>();
        if (item == null || !item.hasItemMeta()) {
            return trustedPlayers;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(trustedPlayersKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return trustedPlayers;
        }
        for (String entry : raw.split(",")) {
            UUID playerId = readUuid(entry);
            if (playerId != null) {
                trustedPlayers.add(playerId);
            }
        }
        return trustedPlayers;
    }

    public ItemStack playerHead(Player target, String displayName, List<String> lore) {
        ItemStack item = namedItem(Material.PLAYER_HEAD, displayName, lore);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setPlayerProfile(target.getPlayerProfile());
        meta.getPersistentDataContainer().set(trustTargetKey, PersistentDataType.STRING,
                target.getUniqueId().toString());
        item.setItemMeta(meta);
        return item;
    }

    public String playerName(OfflinePlayer player) {
        String name = player.getName();
        return name == null || name.isBlank() ? player.getUniqueId().toString().substring(0, 8) : name;
    }

    public Optional<UUID> readTrustTarget(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.ofNullable(readUuid(item.getItemMeta().getPersistentDataContainer()
                .get(trustTargetKey, PersistentDataType.STRING)));
    }

    public ItemStack createAnchorDisplayItem() {
        ItemStack item = new ItemStack(displayMaterial());
        ItemMeta meta = item.getItemMeta();
        NamespacedKey itemModel = NamespacedKey.fromString(plugin.getConfig().getString("item.item-model", "haohan:soul_anchor"));
        if (itemModel != null) {
            meta.setItemModel(itemModel);
        }
        item.setItemMeta(meta);
        return item;
    }

    public boolean isSoulAnchorItem(ItemStack item) {
        if (item == null || item.getType() != anchorMaterial() || !item.hasItemMeta()) {
            return false;
        }
        return plugin.getConfig().getString("item.id", "haohan:soul_anchor")
                .equals(item.getItemMeta().getPersistentDataContainer().get(itemTypeKey, PersistentDataType.STRING));
    }

    public ItemStack namedItem(Material material, String name) {
        return namedItem(material, name, List.of());
    }

    public ItemStack namedItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color.apply(name));
        if (!lore.isEmpty()) {
            meta.setLore(lore.stream().map(color).toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    public void writeAnchorId(ItemStack item, UUID id) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(anchorIdKey, PersistentDataType.STRING, id.toString());
        item.setItemMeta(meta);
    }

    public Optional<UUID> readAnchorId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(anchorIdKey, PersistentDataType.STRING);
        UUID id = readUuid(raw);
        return id == null ? Optional.empty() : Optional.of(id);
    }

    public Material anchorMaterial() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("item.material", "BARRIER"));
        return material == null ? Material.BARRIER : material;
    }

    public Material anchorBlockMaterial() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("item.placed-block", "BARRIER"));
        return material == null ? Material.BARRIER : material;
    }

    public Material displayMaterial() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("visuals.display-material", "STONE"));
        return material == null || !material.isBlock() || !material.isOccluding() ? Material.STONE : material;
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
}
