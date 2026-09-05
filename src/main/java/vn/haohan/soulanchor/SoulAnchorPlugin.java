/*
 * Copyright (C) 2026 HaoHanSMP
 *
 * This file is part of Soul Anchor.
 *
 * Soul Anchor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Soul Anchor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Soul Anchor. If not, see <https://www.gnu.org/licenses/>.
 */

package vn.haohan.soulanchor;

import vn.haohan.soulanchor.command.SoulAnchorCommand;
import vn.haohan.soulanchor.domain.Anchor;
import vn.haohan.soulanchor.domain.Cost;
import vn.haohan.soulanchor.domain.SharedAnchorGroup;
import vn.haohan.soulanchor.domain.Validation;
import vn.haohan.soulanchor.gui.AnchorMenuHolder;
import vn.haohan.soulanchor.gui.SharedAnchorMenuHolder;
import vn.haohan.soulanchor.gui.TrustMenuHolder;
import vn.haohan.soulanchor.listener.SoulAnchorListener;
import vn.haohan.soulanchor.repository.AnchorRepository;
import vn.haohan.soulanchor.service.AnchorVisualService;
import vn.haohan.soulanchor.service.ItemService;
import vn.haohan.soulanchor.service.MessageService;
import vn.haohan.soulanchor.service.TeleportCostService;
import vn.haohan.soulanchor.service.TransientFakePlayer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.StringUtil;
import org.bukkit.util.Vector;
import org.bukkit.util.EulerAngle;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public final class SoulAnchorPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {

    private NamespacedKey itemTypeKey;
    private NamespacedKey anchorIdKey;
    private NamespacedKey anchorNameKey;
    private NamespacedKey trustedPlayersKey;
    private NamespacedKey trustTargetKey;
    private NamespacedKey recipeKey;
    private ItemService itemService;
    private TeleportCostService teleportCostService;
    private AnchorVisualService visualService;
    private SoulAnchorCommand commandHandler;
    private File anchorsFile;
    private File messagesFile;
    private MessageService messageService;
    private AnchorRepository anchorRepository;
    private Map<UUID, Anchor> anchorsById;
    private Map<String, UUID> anchorIdsByLocation;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Warmup> warmups = new ConcurrentHashMap<>();
    private List<UUID> idleParticleAnchorOrder;
    private BukkitTask idleParticlesTask;
    private int idleParticleCursor;
    private BukkitTask anchorMaintenanceTask;

    @Override
    public void onEnable() {
        itemTypeKey = new NamespacedKey(this, "item_type");
        anchorIdKey = new NamespacedKey(this, "anchor_id");
        anchorNameKey = new NamespacedKey(this, "anchor_name");
        trustedPlayersKey = new NamespacedKey(this, "trusted_players");
        trustTargetKey = new NamespacedKey(this, "trust_target");
        recipeKey = NamespacedKey.fromString(getConfig().getString("item.id", "haohan:soul_anchor"));
        if (recipeKey == null) {
            recipeKey = new NamespacedKey(this, "soul_anchor");
        }
        itemService = new ItemService(this, this::color, this::sanitizeName);
        teleportCostService = new TeleportCostService(this);

        saveDefaultConfig();
        migrateLegacyConfig();
        saveResource("messages.yml", false);
        messagesFile = new File(getDataFolder(), "messages.yml");
        messageService = new MessageService(this, messagesFile, this::color);
        loadMessages();
        anchorsFile = new File(getDataFolder(), "anchors.yml");
        anchorRepository = new AnchorRepository(this, anchorsFile);
        anchorsById = anchorRepository.byId();
        anchorIdsByLocation = anchorRepository.byLocation();
        idleParticleAnchorOrder = anchorRepository.particleOrder();
        visualService = new AnchorVisualService(this, itemService, anchorsById);
        commandHandler = new SoulAnchorCommand(this);

        loadAnchors();
        registerRecipe();

        Objects.requireNonNull(getCommand("soulanchor")).setExecutor(this);
        Objects.requireNonNull(getCommand("soulanchor")).setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(new SoulAnchorListener(this), this);
        startAnchorMaintenance();
        startIdleParticles();
        getLogger().info("Loaded " + anchorsById.size() + " Soul Anchors.");
    }

    private void migrateLegacyConfig() {
        boolean changed = false;
        String material = getConfig().getString("item.material", "BARRIER");
        if (material.equalsIgnoreCase("GRINDSTONE") || material.equalsIgnoreCase("JIGSAW")) {
            getConfig().set("item.material", "BARRIER");
            getLogger().info("Migrated Soul Anchor base material from " + material + " to BARRIER.");
            changed = true;
        }
        if (getConfig().getDouble("visuals.scale-y", 0.877D) == 1.6D) {
            getConfig().set("visuals.scale-y", 0.877D);
            changed = true;
        }
        if (getConfig().getDouble("visuals.interaction-height", 1.1D) == 2.0D) {
            getConfig().set("visuals.interaction-height", 1.1D);
            changed = true;
        }
        if (getConfig().getLong("visuals.idle-particle-interval-ticks", 20L) == 20L) {
            getConfig().set("visuals.idle-particle-interval-ticks", 40L);
            changed = true;
        }
        if (!getConfig().contains("visuals.idle-particle-batch-size")) {
            getConfig().set("visuals.idle-particle-batch-size", 32);
            changed = true;
        }
        List<String> lore = new ArrayList<>(getConfig().getStringList("item.lore"));
        boolean loreChanged = false;
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).equals("&fPlus: &b1 Echo Shard")) {
                lore.set(i, "&fEcho Shard: &b1 beyond 2000 blocks");
                loreChanged = true;
                changed = true;
            }
        }
        if (loreChanged) {
            getConfig().set("item.lore", lore);
        }
        if (changed) {
            saveConfig();
        }
    }

    @Override
    public void onDisable() {
        for (Warmup warmup : new ArrayList<>(warmups.values())) {
            warmup.cancel(false);
        }
        warmups.clear();
        if (anchorMaintenanceTask != null) {
            anchorMaintenanceTask.cancel();
        }
        if (idleParticlesTask != null) {
            idleParticlesTask.cancel();
        }
        saveAnchors();
    }

    @EventHandler(ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        if (getConfig().getBoolean("recipe.discover-automatically", true)) {
            event.getPlayer().discoverRecipe(recipeKey);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack handItem = event.getItemInHand();
        if (!isSoulAnchorItem(handItem)) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission("soulanchor.place")) {
            event.setCancelled(true);
            send(player, "no-permission");
            return;
        }
        if (isWorldBlocked(event.getBlockPlaced().getWorld())) {
            event.setCancelled(true);
            send(player, "world-disabled");
            return;
        }

        int limit = getAnchorLimit(player);
        if (limit >= 0 && ownedAnchors(player.getUniqueId()).size() >= limit) {
            event.setCancelled(true);
            send(player, "anchor-limit", "{limit}", String.valueOf(limit));
            return;
        }

        Location location = event.getBlockPlaced().getLocation();
        location.getBlock().setType(getAnchorBlockMaterial(), false);

        String storedName = readPortableAnchorName(handItem);
        String name = storedName == null ? nextDefaultName(player.getUniqueId()) : storedName;
        Set<UUID> trustedPlayers = readPortableTrustedPlayers(handItem);
        trustedPlayers.remove(player.getUniqueId());
        Anchor anchor = new Anchor(UUID.randomUUID(), player.getUniqueId(), name, location, 0F, 0F,
                Instant.now().toEpochMilli(), trustedPlayers, null, null);
        anchor = spawnVisuals(anchor);
        anchorRepository.put(anchor);
        saveAnchors();

        send(player, "anchor-placed", "{name}", anchor.name());
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8F, 1.1F);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getAction().isLeftClick()) {
            return;
        }
        Anchor anchor = anchorAt(event.getClickedBlock()).orElse(null);
        if (anchor == null) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("soulanchor.use")) {
            send(player, "no-permission");
            return;
        }
        if (!canAccessAnchor(player.getUniqueId(), anchor) && !player.hasPermission("soulanchor.admin")) {
            send(player, "anchor-not-owner");
            return;
        }
        openMenu(player, anchor);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Anchor anchor = anchorFromEntity(event.getRightClicked()).orElse(null);
        if (anchor == null) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("soulanchor.use")) {
            send(player, "no-permission");
            return;
        }
        if (!canAccessAnchor(player.getUniqueId(), anchor) && !player.hasPermission("soulanchor.admin")) {
            send(player, "anchor-not-owner");
            return;
        }
        openMenu(player, anchor);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof TrustMenuHolder holder) {
            handleTrustMenuClick(event, holder);
            return;
        }
        if (event.getInventory().getHolder() instanceof SharedAnchorMenuHolder holder) {
            handleSharedAnchorMenuClick(event, holder);
            return;
        }
        if (!(event.getInventory().getHolder() instanceof AnchorMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()) {
            return;
        }
        UUID targetId = readAnchorId(item).orElse(null);
        if (targetId == null) {
            if (event.getRawSlot() == 22 && item.getType() == Material.ENDER_PEARL) {
                Anchor source = anchorsById.get(holder.sourceAnchorId());
                if (source != null) {
                    openSharedAnchorMenu(player, source, 0);
                }
                return;
            }
            if (item.getType() == Material.PLAYER_HEAD) {
                Anchor source = anchorsById.get(holder.sourceAnchorId());
                if (source != null && source.ownerId().equals(player.getUniqueId())) {
                    openTrustMenu(player, source, 0);
                }
                return;
            }
            if (item.getType() == Material.BARRIER) {
                player.closeInventory();
            }
            return;
        }
        Anchor source = anchorsById.get(holder.sourceAnchorId());
        Anchor target = anchorsById.get(targetId);
        if (source == null || target == null || source.id().equals(target.id())) {
            send(player, "teleport-failed");
            return;
        }
        player.closeInventory();
        requestTeleport(player, source.id(), target.id());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof AnchorMenuHolder
                || event.getInventory().getHolder() instanceof SharedAnchorMenuHolder
                || event.getInventory().getHolder() instanceof TrustMenuHolder) {
            event.getInventory().clear();
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        Anchor anchor = anchorAt(event.getBlock()).orElse(null);
        if (anchor == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!canBreakAnchor(player, anchor)) {
            event.setCancelled(true);
            send(player, "anchor-not-owner");
            return;
        }

        removeAnchor(anchor.id());
        cancelWarmupsTouching(anchor.id());
        if (getConfig().getBoolean("breaking.drop-item", true)) {
            event.setDropItems(false);
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation().add(0.5, 0.5, 0.5),
                    createPortableAnchorItem(anchor));
        }
        send(player, "anchor-broken", "{name}", anchor.name());
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8F, 1F);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        if (anchorAt(event.getBlock()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFluid(BlockFromToEvent event) {
        if (anchorAt(event.getToBlock()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> anchorAt(block).isPresent())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> anchorAt(block).isPresent())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> anchorAt(block).isPresent());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> anchorAt(block).isPresent());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Warmup warmup = warmups.get(event.getPlayer().getUniqueId());
        if (warmup == null || event.getTo() == null) {
            return;
        }
        // During the cinematic the player itself is the spectator camera. Its
        // server teleports are intentional and must not be pinned back to the
        // original body location by this movement guard.
        if (event.getPlayer().getGameMode() == GameMode.SPECTATOR
                && event.getPlayer().getSpectatorTarget() == null) {
            return;
        }
        // The player is a spectator during the warmup. Keep the spectator body
        // pinned just behind the original position instead of cancelling the
        // warmup as soon as the client sends a movement packet.
        if (!sameWorld(warmup.lockLocation, event.getTo())
                || warmup.lockLocation.distanceSquared(event.getTo()) > 0.01D) {
            event.setTo(warmup.lockLocation.clone());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            Warmup warmup = warmups.get(player.getUniqueId());
            if (warmup != null) {
                warmup.cancel(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDealDamage(EntityDamageByEntityEvent event) {
        Anchor anchor = anchorFromEntity(event.getEntity()).orElse(null);
        if (anchor != null) {
            event.setCancelled(true);
            Player breaker = damagerPlayer(event.getDamager());
            if (breaker == null) {
                return;
            }
            if (!canBreakAnchor(breaker, anchor)) {
                send(breaker, "anchor-not-owner");
                return;
            }
            removeAnchor(anchor.id());
            cancelWarmupsTouching(anchor.id());
            anchor.location().getBlock().setType(Material.AIR, false);
            if (getConfig().getBoolean("breaking.drop-item", true)) {
                breaker.getWorld().dropItemNaturally(anchor.location().clone().add(0.5, 0.5, 0.5),
                        createPortableAnchorItem(anchor));
            }
            send(breaker, "anchor-broken", "{name}", anchor.name());
            return;
        }

        Player player = damagerPlayer(event.getDamager());
        if (player == null) {
            return;
        }
        Warmup warmup = warmups.get(player.getUniqueId());
        if (warmup != null) {
            warmup.cancel(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH
                || !getConfig().getBoolean("echo-shard-sources.fishing.enabled", true)
                || !(event.getCaught() instanceof Item caughtItem)) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack rod = event.getHand() == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        int luckLevel = rod.getEnchantmentLevel(Enchantment.LUCK_OF_THE_SEA);
        double baseChance = getConfig().getDouble("echo-shard-sources.fishing.base-chance", 0.01D);
        double luckBonus = getConfig().getDouble("echo-shard-sources.fishing.luck-bonus-per-level", 0.0025D);
        double chance = Math.max(0D, Math.min(1D, baseChance + luckLevel * luckBonus));
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }

        caughtItem.setItemStack(new ItemStack(Material.ECHO_SHARD));
        send(player, "echo-shard-fished", "{chance}", formatPercent(chance));
        player.playSound(player.getLocation(), Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.8F, 1.2F);
    }

    @EventHandler
    public void onWardenDeath(EntityDeathEvent event) {
        if (event.getEntity().getType() != org.bukkit.entity.EntityType.WARDEN
                || !getConfig().getBoolean("echo-shard-sources.warden.enabled", true)
                || event.getEntity().getKiller() == null) {
            return;
        }

        double chance = Math.max(0D, Math.min(1D,
                getConfig().getDouble("echo-shard-sources.warden.drop-chance", 0.15D)));
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }

        int amount = Math.max(1, getConfig().getInt("echo-shard-sources.warden.amount", 1));
        event.getDrops().add(new ItemStack(Material.ECHO_SHARD, amount));
        send(event.getEntity().getKiller(), "echo-shard-warden", "{amount}", String.valueOf(amount));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Warmup warmup = warmups.get(event.getPlayer().getUniqueId());
        if (warmup != null) {
            warmup.cancel(false);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return commandHandler.execute(sender, command, label, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return commandHandler.complete(sender, command, alias, args);
    }

    // Package-level application API used by SoulAnchorCommand. Keeping this boundary here
    // prevents command parsing from reaching into Bukkit/configuration internals.
    public void sendMessage(CommandSender sender, String key, String... replacements) {
        send(sender, key, replacements);
    }

    public String colorMessage(String input) {
        return color(input);
    }

    public int parseIntValue(String raw, int fallback) {
        return parseInt(raw, fallback);
    }

    public ItemStack createAnchorItemValue(int amount) {
        return createAnchorItem(amount);
    }

    public void updateAnchor(Anchor anchor) {
        anchorRepository.put(anchor);
    }

    public void saveAnchorData() {
        saveAnchors();
    }

    public String sanitizeNameValue(String input) {
        return sanitizeName(input);
    }

    public void reloadPluginState() {
        reloadConfig();
        loadMessages();
        registerRecipe();
        refreshAnchorVisuals();
        restartIdleParticles();
    }

    public void listAnchors(CommandSender sender, Player owner) {
        List<Anchor> anchors = accessibleAnchors(owner.getUniqueId());
        int ownedCount = ownedAnchors(owner.getUniqueId()).size();
        int sharedCount = anchors.size() - ownedCount;
        sender.sendMessage(color("&3Soul Anchors available to &f" + owner.getName() + "&7 (owned: &f" + ownedCount
                + "/" + getAnchorLimit(owner) + "&7, shared: &f" + sharedCount + "&7)"));
        if (anchors.isEmpty()) {
            sender.sendMessage(color("&7No Soul Anchors yet."));
            return;
        }
        for (Anchor anchor : anchors) {
            Location loc = anchor.location();
            String access = anchor.ownerId().equals(owner.getUniqueId()) ? "" : " &d[shared]";
            sender.sendMessage(color("&b- &f" + anchor.name() + access + " &7" + loc.getWorld().getName() + " "
                    + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()));
        }
    }

    private void requestTeleport(Player player, UUID sourceId, UUID targetId) {
        if (warmups.containsKey(player.getUniqueId())) {
            send(player, "warmup-cancelled");
            return;
        }
        long now = System.currentTimeMillis();
        long remaining = cooldowns.getOrDefault(player.getUniqueId(), 0L) - now;
        if (remaining > 0 && !player.hasPermission("soulanchor.bypass.cooldown")) {
            send(player, "cooldown", "{seconds}", String.valueOf((remaining + 999) / 1000));
            return;
        }

        Anchor source = anchorsById.get(sourceId);
        Anchor target = anchorsById.get(targetId);
        Validation validation = validateTeleport(player, source, target, false);
        if (!validation.ok()) {
            send(player, validation.messageKey(), validation.replacements());
            return;
        }

        // The cinematic must still run for admins with bypass.warmup.
        // Bypass skips the countdown duration, but never skips the soul effect.
        int configuredWarmupSeconds = getConfig().getInt("teleport.warmup-seconds", 5);
        boolean bypassWarmup = player.hasPermission("soulanchor.bypass.warmup");
        int countdownSeconds = bypassWarmup
                ? 1 : Math.max(1, configuredWarmupSeconds);
        int animationSeconds = Math.max(1, Math.max(configuredWarmupSeconds,
                getConfig().getInt("teleport.animation-duration-seconds", 8)));

        send(player, "warmup-start", "{seconds}", String.valueOf(countdownSeconds));
        Warmup warmup = new Warmup(player, sourceId, targetId, player.getLocation().clone(), animationSeconds);
        warmups.put(player.getUniqueId(), warmup);
        long animationIntervalTicks = Math.max(1L, Math.min(4L,
                getConfig().getLong("teleport.animation-update-interval-ticks", 1L)));
        warmup.task = new BukkitRunnable() {
            int elapsedTicks;
            final int totalTicks = animationSeconds * 20;

            @Override
            public void run() {
                if (!warmups.containsKey(player.getUniqueId())) {
                    cancel();
                    return;
                }
                if (elapsedTicks < totalTicks) {
                    if (!bypassWarmup && elapsedTicks % 20 == 0) {
                        int remainingSeconds = Math.max(1, (totalTicks - elapsedTicks + 19) / 20);
                        send(player, "warmup-tick", "{seconds}", String.valueOf(remainingSeconds));
                    }
                    warmup.playTickEffect((elapsedTicks + animationIntervalTicks) / (double) totalTicks);
                    elapsedTicks += (int) animationIntervalTicks;
                    return;
                }
                warmups.remove(player.getUniqueId());
                cancel();
                warmup.finishEffect();
                finishTeleport(player, sourceId, targetId);
            }
        }.runTaskTimer(this, 0L, animationIntervalTicks);
    }

    private void finishTeleport(Player player, UUID sourceId, UUID targetId) {
        Anchor source = anchorsById.get(sourceId);
        Anchor target = anchorsById.get(targetId);
        Validation validation = validateTeleport(player, source, target, true);
        if (!validation.ok()) {
            send(player, validation.messageKey(), validation.replacements());
            return;
        }

        Cost cost = calculateCost(source.location(), target.location());
        Location destination = validation.safeDestination();

        if (!player.hasPermission("soulanchor.bypass.cost")) {
            removeEchoShards(player, cost.shards());
            player.giveExp(-cost.experiencePoints());
        }

        boolean teleported = player.teleport(destination);
        if (!teleported) {
            if (!player.hasPermission("soulanchor.bypass.cost")) {
                player.getInventory().addItem(new ItemStack(Material.ECHO_SHARD, cost.shards()));
                player.giveExp(cost.experiencePoints());
            }
            send(player, "teleport-failed");
            return;
        }

        int cooldownSeconds = getConfig().getInt("teleport.cooldown-seconds", 30);
        if (!player.hasPermission("soulanchor.bypass.cooldown")) {
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + cooldownSeconds * 1000L);
        }
        player.getWorld().spawnParticle(Particle.SCULK_SOUL, player.getLocation().add(0, 1, 0), 16, 0.5, 0.8, 0.5,
                0.02);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.9F, 1.15F);
        send(player, "teleport-success", "{anchor}", target.name());
    }

    private Validation validateTeleport(Player player, Anchor source, Anchor target, boolean requireSafeDestination) {
        if (source == null || target == null || source.id().equals(target.id())) {
            return Validation.fail("teleport-failed");
        }
        if ((!canAccessAnchor(player.getUniqueId(), source) || !canAccessAnchor(player.getUniqueId(), target))
                && !player.hasPermission("soulanchor.admin")) {
            return Validation.fail("anchor-not-owner");
        }
        if (!isAnchorStillPlaced(source) || !isAnchorStillPlaced(target)) {
            return Validation.fail("teleport-failed");
        }
        if (isWorldBlocked(target.location().getWorld())) {
            return Validation.fail("world-disabled");
        }
        if (!sameWorld(source.location(), target.location())
                && !getConfig().getBoolean("cross-dimension.enabled", true)) {
            return Validation.fail("teleport-failed");
        }

        Cost cost = calculateCost(source.location(), target.location());
        if (!player.hasPermission("soulanchor.bypass.cost")) {
            if (player.getLevel() < cost.requiredLevels()) {
                return Validation.fail("not-enough-levels", "{required}", String.valueOf(cost.requiredLevels()),
                        "{current}", String.valueOf(player.getLevel()));
            }
            if (countEchoShards(player) < cost.shards()) {
                return Validation.fail("not-enough-shards", "{amount}", String.valueOf(cost.shards()));
            }
        }

        if (requireSafeDestination) {
            target.location().getChunk().load(true);
            Location safe = findSafeLocation(target.location());
            if (safe == null) {
                return Validation.fail("unsafe-destination");
            }
            return Validation.ok(safe);
        }
        return Validation.ok(null);
    }

    private void openMenu(Player player, Anchor source) {
        Inventory inventory = Bukkit.createInventory(new AnchorMenuHolder(source.id()), 27,
                color("&3Soul Anchor Network"));
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, namedItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        List<Anchor> anchors = ownedAnchors(player.getUniqueId());
        int[] slots = { 11, 13, 15 };
        for (int i = 0; i < Math.min(slots.length, anchors.size()); i++) {
            Anchor target = anchors.get(i);
            inventory.setItem(slots[i], createTeleportIcon(source, target, null));
        }
        int sharedCount = sharedAnchors(player.getUniqueId()).size();
        inventory.setItem(4, namedItem(Material.ECHO_SHARD, "&bNetwork", List
                .of("&7Owned anchors: &f" + anchors.size() + "/" + getAnchorLimit(player),
                        "&7Shared with you: &f" + sharedCount)));
        if (source.ownerId().equals(player.getUniqueId()) && player.hasPermission("soulanchor.share")) {
            inventory.setItem(18, namedItem(Material.PLAYER_HEAD, "&bManage trust", List.of(
                    "&7Share &f" + source.name() + " &7with online players.",
                    "&7Trusted players: &f" + source.sharedWith().size(),
                    "", "&fClick to open")));
        }
        inventory.setItem(22, namedItem(Material.ENDER_PEARL, "&dShared anchors", List.of(
                "&7All teleport points shared with you.",
                "&7Available: &f" + sharedCount,
                "", "&fClick to open")));
        inventory.setItem(26, namedItem(Material.BARRIER, "&cClose"));
        player.openInventory(inventory);
    }

    private ItemStack createTeleportIcon(Anchor source, Anchor target, String ownerName) {
        Cost cost = calculateCost(source.location(), target.location());
        boolean current = source.id().equals(target.id());
        List<String> lore = new ArrayList<>();
        Location loc = target.location();
        if (ownerName != null) {
            lore.add("&dShared by: &f" + ownerName);
        }
        lore.add("&7World: &f" + loc.getWorld().getName());
        lore.add("&7Coords: &f" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
        if (current) {
            lore.add("");
            lore.add("&eYou are at this Soul Anchor.");
        } else {
            lore.add("&7Distance: &f" + formatDistance(source.location(), target.location()) + " blocks");
            lore.add("");
            String requirement = "&7Requires: &a" + cost.requiredLevels() + " levels";
            if (cost.shards() > 0) {
                requirement += " &7+ &b" + cost.shards() + " Echo Shard";
            }
            lore.add(requirement);
            lore.add("&7XP charged: &e" + cost.experiencePoints() + " points");
            lore.add("&fClick to teleport");
        }
        ItemStack icon = namedItem(current ? Material.LODESTONE : Material.RESPAWN_ANCHOR, "&b" + target.name(), lore);
        if (!current) {
            writeAnchorId(icon, target.id());
        }
        return icon;
    }

    private void openSharedAnchorMenu(Player player, Anchor source, int requestedPage) {
        List<SharedAnchorGroup> groups = sharedAnchorGroups(player.getUniqueId());
        int anchorCount = groups.stream().mapToInt(group -> group.anchors().size()).sum();
        int pageCount = Math.max(1, (groups.size() + 4) / 5);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        Inventory inventory = Bukkit.createInventory(new SharedAnchorMenuHolder(source.id(), page), 54,
                color("&3Shared Soul Anchors"));
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, namedItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        int start = page * 5;
        for (int row = 0; row < 5 && start + row < groups.size(); row++) {
            SharedAnchorGroup group = groups.get(start + row);
            String ownerName = playerName(Bukkit.getOfflinePlayer(group.ownerId()));
            int rowStart = row * 9;
            inventory.setItem(rowStart + 1, namedItem(Material.NAME_TAG, "&d" + ownerName, List.of(
                    "&7Shared anchors: &f" + group.anchors().size())));
            int[] anchorSlots = { rowStart + 3, rowStart + 4, rowStart + 5 };
            for (int index = 0; index < Math.min(anchorSlots.length, group.anchors().size()); index++) {
                inventory.setItem(anchorSlots[index],
                        createTeleportIcon(source, group.anchors().get(index), ownerName));
            }
        }

        if (page > 0) {
            inventory.setItem(45, namedItem(Material.ARROW, "&bPrevious page"));
        }
        inventory.setItem(49, namedItem(Material.ENDER_PEARL, "&dShared anchors", List.of(
                "&7Available: &f" + anchorCount,
                "&7Players: &f" + groups.size(),
                "&7Page: &f" + (page + 1) + "/" + pageCount)));
        inventory.setItem(52, namedItem(Material.OAK_DOOR, "&eBack to owned anchors"));
        if (page + 1 < pageCount) {
            inventory.setItem(53, namedItem(Material.ARROW, "&bNext page"));
        } else {
            inventory.setItem(53, namedItem(Material.BARRIER, "&cClose"));
        }
        player.openInventory(inventory);
    }

    private void handleSharedAnchorMenuClick(InventoryClickEvent event, SharedAnchorMenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Anchor source = anchorsById.get(holder.sourceAnchorId());
        if (source == null || (!canAccessAnchor(player.getUniqueId(), source)
                && !player.hasPermission("soulanchor.admin"))) {
            player.closeInventory();
            send(player, "teleport-failed");
            return;
        }
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == 45 && item.getType() == Material.ARROW) {
            openSharedAnchorMenu(player, source, holder.page() - 1);
            return;
        }
        if (slot == 52 && item.getType() == Material.OAK_DOOR) {
            openMenu(player, source);
            return;
        }
        if (slot == 53) {
            if (item.getType() == Material.ARROW) {
                openSharedAnchorMenu(player, source, holder.page() + 1);
            } else if (item.getType() == Material.BARRIER) {
                player.closeInventory();
            }
            return;
        }
        UUID targetId = readAnchorId(item).orElse(null);
        Anchor target = targetId == null ? null : anchorsById.get(targetId);
        if (target == null || !target.sharedWith().contains(player.getUniqueId())) {
            return;
        }
        player.closeInventory();
        requestTeleport(player, source.id(), target.id());
    }

    private void openTrustMenu(Player player, Anchor anchor, int requestedPage) {
        if (!anchor.ownerId().equals(player.getUniqueId()) || !player.hasPermission("soulanchor.share")) {
            send(player, "no-permission");
            return;
        }

        List<? extends Player> targets = Bukkit.getOnlinePlayers().stream()
                .filter(target -> !target.getUniqueId().equals(player.getUniqueId()))
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int pageCount = Math.max(1, (targets.size() + 44) / 45);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        Inventory inventory = Bukkit.createInventory(new TrustMenuHolder(anchor.id(), page), 54,
                color("&3Trust: &f" + anchor.name()));

        int start = page * 45;
        for (int slot = 0; slot < 45 && start + slot < targets.size(); slot++) {
            Player target = targets.get(start + slot);
            boolean trusted = anchor.sharedWith().contains(target.getUniqueId());
            int used = ownedAnchors(target.getUniqueId()).size();
            int limit = getAnchorLimit(target);
            List<String> lore = new ArrayList<>();
            lore.add("&7Anchor: &f" + anchor.name());
            lore.add("&7Status: " + (trusted ? "&aTrusted" : "&cNot trusted"));
            lore.add("&7Owned anchors: &f" + used + "/" + (limit < 0 ? "unlimited" : limit));
            lore.add("");
            lore.add(trusted ? "&eClick to revoke access" : "&aClick to share this anchor");
            inventory.setItem(slot, playerHead(target, trusted ? "&a" + target.getName() : "&f" + target.getName(),
                    lore));
        }

        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, namedItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        if (page > 0) {
            inventory.setItem(45, namedItem(Material.ARROW, "&bPrevious page"));
        }
        inventory.setItem(49, namedItem(Material.RESPAWN_ANCHOR, "&b" + anchor.name(), List.of(
                "&7Online players: &f" + targets.size(),
                "&7Trusted players: &f" + anchor.sharedWith().size(),
                "&7Page: &f" + (page + 1) + "/" + pageCount)));
        if (page + 1 < pageCount) {
            inventory.setItem(53, namedItem(Material.ARROW, "&bNext page"));
        } else {
            inventory.setItem(53, namedItem(Material.BARRIER, "&cBack"));
        }
        player.openInventory(inventory);
    }

    private void handleTrustMenuClick(InventoryClickEvent event, TrustMenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Anchor anchor = anchorsById.get(holder.anchorId());
        if (anchor == null || !anchor.ownerId().equals(player.getUniqueId())
                || !player.hasPermission("soulanchor.share")) {
            player.closeInventory();
            send(player, anchor == null ? "not-anchor" : "no-permission");
            return;
        }

        int slot = event.getRawSlot();
        if (slot == 45 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
            openTrustMenu(player, anchor, holder.page() - 1);
            return;
        }
        if (slot == 53 && event.getCurrentItem() != null) {
            if (event.getCurrentItem().getType() == Material.ARROW) {
                openTrustMenu(player, anchor, holder.page() + 1);
            } else if (event.getCurrentItem().getType() == Material.BARRIER) {
                openMenu(player, anchor);
            }
            return;
        }

        ItemStack item = event.getCurrentItem();
        UUID targetId = readTrustTarget(item).orElse(null);
        Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
        if (target == null) {
            return;
        }
        if (anchor.sharedWith().contains(targetId)) {
            Anchor updated = anchor.withoutSharedPlayer(targetId);
            anchorRepository.put(updated);
            saveAnchors();
            send(player, "anchor-unshared", "{anchor}", updated.name(), "{player}", target.getName());
            send(target, "anchor-unshared-received", "{anchor}", updated.name(), "{player}", player.getName());
            openTrustMenu(player, updated, holder.page());
            return;
        }

        Anchor updated = anchor.withSharedPlayer(targetId);
        anchorRepository.put(updated);
        saveAnchors();
        send(player, "anchor-shared", "{anchor}", updated.name(), "{player}", target.getName());
        send(target, "anchor-shared-received", "{anchor}", updated.name(), "{player}", player.getName());
        openTrustMenu(player, updated, holder.page());
    }

    private void registerRecipe() {
        Bukkit.removeRecipe(recipeKey);
        recipeKey = NamespacedKey.fromString(getConfig().getString("item.id", "haohan:soul_anchor"));
        if (recipeKey == null) {
            recipeKey = new NamespacedKey(this, "soul_anchor");
        }
        if (!getConfig().getBoolean("recipe.enabled", true)) {
            return;
        }
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createAnchorItem(1));
        recipe.shape(" S ", "#o#", "DOD");
        recipe.setIngredient('S', Material.SOUL_LANTERN);
        recipe.setIngredient('#', Material.SOUL_SAND);
        recipe.setIngredient('o', Material.ENDER_PEARL);
        recipe.setIngredient('D', Material.DEEPSLATE);
        recipe.setIngredient('O', Material.OBSIDIAN);
        Bukkit.addRecipe(recipe);
    }

    private ItemStack createAnchorItem(int amount) {
        return itemService.createAnchorItem(amount);
    }

    private ItemStack createPortableAnchorItem(Anchor anchor) {
        return itemService.createPortableAnchorItem(anchor);
    }

    private String readPortableAnchorName(ItemStack item) {
        return itemService.readPortableAnchorName(item);
    }

    private Set<UUID> readPortableTrustedPlayers(ItemStack item) {
        return itemService.readPortableTrustedPlayers(item);
    }

    private ItemStack playerHead(Player target, String displayName, List<String> lore) {
        return itemService.playerHead(target, displayName, lore);
    }

    private String playerName(OfflinePlayer player) {
        return itemService.playerName(player);
    }

    private Optional<UUID> readTrustTarget(ItemStack item) {
        return itemService.readTrustTarget(item);
    }

    /**
     * ItemDisplay-only stack. Kept separate from the craft/place item so the model does not depend on the base item.
     */
    private ItemStack createAnchorDisplayItem() {
        return itemService.createAnchorDisplayItem();
    }

    private boolean isSoulAnchorItem(ItemStack item) {
        return itemService.isSoulAnchorItem(item);
    }

    private ItemStack namedItem(Material material, String name) {
        return namedItem(material, name, List.of());
    }

    private ItemStack namedItem(Material material, String name, List<String> lore) {
        return itemService.namedItem(material, name, lore);
    }

    private void writeAnchorId(ItemStack item, UUID id) {
        itemService.writeAnchorId(item, id);
    }

    private Optional<UUID> readAnchorId(ItemStack item) {
        return itemService.readAnchorId(item);
    }

    private void loadAnchors() {
        anchorRepository.load(getAnchorBlockMaterial(), this::spawnVisuals, this::removeEntity);
    }

    private void saveAnchors() {
        anchorRepository.save();
    }

    private Optional<Anchor> anchorAt(Block block) {
        return anchorRepository.at(block);
    }

    private boolean isAnchorStillPlaced(Anchor anchor) {
        return anchorRepository.isStillPlaced(anchor, getAnchorBlockMaterial());
    }

    private void removeAnchor(UUID id) {
        anchorRepository.remove(id, this::removeEntity);
    }

    private void cancelWarmupsTouching(UUID anchorId) {
        for (Warmup warmup : new ArrayList<>(warmups.values())) {
            if (warmup.sourceId.equals(anchorId) || warmup.targetId.equals(anchorId)) {
                warmup.cancel(true);
            }
        }
    }

    public List<Anchor> ownedAnchors(UUID ownerId) {
        return anchorRepository.owned(ownerId);
    }

    private List<Anchor> accessibleAnchors(UUID playerId) {
        return anchorsById.values().stream()
                .filter(anchor -> canAccessAnchor(playerId, anchor))
                .sorted(Comparator.comparing((Anchor anchor) -> !anchor.ownerId().equals(playerId))
                        .thenComparingLong(Anchor::createdAt))
                .collect(Collectors.toList());
    }

    private List<Anchor> sharedAnchors(UUID playerId) {
        return anchorsById.values().stream()
                .filter(anchor -> !anchor.ownerId().equals(playerId) && anchor.sharedWith().contains(playerId))
                .sorted(Comparator.comparing((Anchor anchor) -> playerName(Bukkit.getOfflinePlayer(anchor.ownerId())),
                        String.CASE_INSENSITIVE_ORDER).thenComparingLong(Anchor::createdAt))
                .collect(Collectors.toList());
    }

    private List<SharedAnchorGroup> sharedAnchorGroups(UUID playerId) {
        Map<UUID, List<Anchor>> anchorsByOwner = new HashMap<>();
        for (Anchor anchor : sharedAnchors(playerId)) {
            anchorsByOwner.computeIfAbsent(anchor.ownerId(), ignored -> new ArrayList<>()).add(anchor);
        }
        return anchorsByOwner.entrySet().stream()
                .sorted(Comparator.comparing(
                        entry -> playerName(Bukkit.getOfflinePlayer(entry.getKey())),
                        String.CASE_INSENSITIVE_ORDER))
                .map(entry -> new SharedAnchorGroup(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
    }

    private boolean canAccessAnchor(UUID playerId, Anchor anchor) {
        return anchor.ownerId().equals(playerId) || anchor.sharedWith().contains(playerId);
    }

    private boolean canBreakAnchor(Player player, Anchor anchor) {
        return player.hasPermission("soulanchor.admin")
                || (anchor.ownerId().equals(player.getUniqueId())
                        && player.hasPermission("soulanchor.break.own"));
    }

    public Optional<Anchor> findOwnedAnchor(UUID ownerId, String nameOrId) {
        return anchorRepository.findOwned(ownerId, nameOrId);
    }

    private String nextDefaultName(UUID ownerId) {
        Set<String> existing = ownedAnchors(ownerId).stream().map(Anchor::name)
                .collect(Collectors.toCollection(HashSet::new));
        for (int i = 1; i <= 99; i++) {
            String name = "Soul Anchor #" + i;
            if (!existing.contains(name)) {
                return name;
            }
        }
        return "Soul Anchor";
    }

    private int getAnchorLimit(Player player) {
        if (player.hasPermission("soulanchor.limit.unlimited")) {
            return -1;
        }
        int limit = getConfig().getInt("limits.default", 3);
        if (getConfig().getBoolean("limits.permission-based", true)) {
            for (String permission : player.getEffectivePermissions().stream().map(info -> info.getPermission())
                    .toList()) {
                if (permission.startsWith("soulanchor.limit.")) {
                    String raw = permission.substring("soulanchor.limit.".length());
                    if (!raw.equals("unlimited")) {
                        limit = Math.max(limit, parseInt(raw, limit));
                    }
                }
            }
        }
        return limit;
    }

    private Cost calculateCost(Location source, Location target) {
        return teleportCostService.calculate(source, target);
    }

    private String formatDistance(Location source, Location target) {
        return teleportCostService.formatDistance(source, target);
    }

    private int countEchoShards(Player player) {
        return teleportCostService.countEchoShards(player);
    }

    private void removeEchoShards(Player player, int amount) {
        teleportCostService.removeEchoShards(player, amount);
    }

    private Location findSafeLocation(Location anchorLocation) {
        return teleportCostService.findSafeLocation(anchorLocation);
    }

    private boolean isWorldBlocked(World world) {
        return teleportCostService.isWorldBlocked(world);
    }

    private Material getAnchorMaterial() {
        return itemService.anchorMaterial();
    }

    private Material getAnchorBlockMaterial() {
        return itemService.anchorBlockMaterial();
    }

    private Material getAnchorDisplayMaterial() {
        return itemService.displayMaterial();
    }

    private Anchor spawnVisuals(Anchor anchor) {
        return visualService.spawn(anchor);
    }

    private void refreshAnchorVisuals() {
        visualService.refreshAll();
        saveAnchors();
    }

    private Optional<Anchor> anchorFromEntity(Entity entity) {
        return visualService.fromEntity(entity);
    }

    private void removeEntity(UUID id) {
        visualService.removeEntity(id);
    }

    private void consumePlacedItem(Player player, EquipmentSlot hand) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack stack = hand == EquipmentSlot.OFF_HAND ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (stack.getAmount() <= 1) {
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
            return;
        }
        stack.setAmount(stack.getAmount() - 1);
    }

    private Player damagerPlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        return null;
    }

    private boolean sameWorld(Location left, Location right) {
        return teleportCostService.sameWorld(left, right);
    }

    private String locationKey(Location location) {
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":"
                + location.getBlockZ();
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String formatPercent(double chance) {
        double percent = chance * 100D;
        if (percent == Math.rint(percent)) {
            return String.format(Locale.ROOT, "%.0f%%", percent);
        }
        return String.format(Locale.ROOT, "%.2f%%", percent);
    }

    private String sanitizeName(String input) {
        String stripped = ChatColor.stripColor(color(input)).replaceAll("[\\p{Cntrl}<>]", "").trim();
        if (stripped.isEmpty()) {
            return "Soul Anchor";
        }
        return stripped.length() > 24 ? stripped.substring(0, 24) : stripped;
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    private void loadMessages() {
        messageService.load();
    }

    private void send(CommandSender sender, String key, String... replacements) {
        messageService.send(sender, key, replacements);
    }

    private void startIdleParticles() {
        if (!getConfig().getBoolean("visuals.idle-particles", true)) {
            return;
        }
        long interval = Math.max(20L, getConfig().getLong("visuals.idle-particle-interval-ticks", 40L));
        int batchSize = Math.max(1, getConfig().getInt("visuals.idle-particle-batch-size", 32));
        double viewDistance = Math.max(1D, getConfig().getDouble("visuals.particle-view-distance", 24D));
        double viewDistanceSquared = viewDistance * viewDistance;
        idleParticlesTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (idleParticleAnchorOrder.isEmpty()) {
                    idleParticleCursor = 0;
                    return;
                }
                if (idleParticleCursor >= idleParticleAnchorOrder.size()) {
                    idleParticleCursor = 0;
                }

                int processed = 0;
                int scanned = 0;
                while (processed < batchSize && scanned < idleParticleAnchorOrder.size()) {
                    UUID anchorId = idleParticleAnchorOrder.get(idleParticleCursor);
                    idleParticleCursor = (idleParticleCursor + 1) % idleParticleAnchorOrder.size();
                    scanned++;

                    Anchor anchor = anchorsById.get(anchorId);
                    if (anchor == null) {
                        continue;
                    }
                    processed++;
                    spawnIdleParticleForNearbyPlayers(anchor, viewDistanceSquared);
                }
            }
        }.runTaskTimer(this, interval, interval);
    }

    private void startAnchorMaintenance() {
        long interval = Math.max(5L, getConfig().getLong("visuals.anchor-maintenance-interval-ticks", 20L));
        anchorMaintenanceTask = new BukkitRunnable() {
            @Override
            public void run() {
                boolean changed = false;
                for (Anchor anchor : new ArrayList<>(anchorsById.values())) {
                    if (!isAnchorStillPlaced(anchor)) {
                        removeAnchor(anchor.id());
                        cancelWarmupsTouching(anchor.id());
                        continue;
                    }

                    Anchor refreshed = spawnVisuals(anchor);
                    if (!refreshed.equals(anchor)) {
                        anchorRepository.put(refreshed);
                        changed = true;
                    }
                }
                if (changed) {
                    saveAnchors();
                }
            }
        }.runTaskTimer(this, interval, interval);
    }

    private void restartIdleParticles() {
        if (idleParticlesTask != null) {
            idleParticlesTask.cancel();
            idleParticlesTask = null;
        }
        idleParticleCursor = 0;
        startIdleParticles();
    }

    private void spawnIdleParticleForNearbyPlayers(Anchor anchor, double viewDistanceSquared) {
        World world = anchor.location().getWorld();
        if (world == null) {
            return;
        }
        Location loc = anchor.location().clone().add(0.5D, 0.9D, 0.5D);
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(loc) <= viewDistanceSquared) {
                player.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 2, 0.15D, 0.25D, 0.15D, 0.005D);
            }
        }
    }

    private final class Warmup {
        private final Player player;
        private final UUID sourceId;
        private final UUID targetId;
        private final Location startLocation;
        private final Location lockLocation;
        private final Location cameraStartLocation;
        private final Location cameraEndLocation;
        private final Location destinationAnchorLocation;
        private final Vector facingDirection;
        private final double baseRiseHeight;
        private final double maxRiseHeight;
        private final Entity fakePlayer;
        private ArmorStand camera;
        private final ItemDisplay flashDisplay;
        private final boolean wasInvisible;
        private final GameMode previousGameMode;
        private int lastFlashStage = -1;
        private int flashTicks;
        private int flashFrameTotal;
        private boolean destinationArrivalEffectPlayed;
        private boolean destinationCameraSnapped;
        private boolean playerCameraActive;
        private BukkitTask task;

        private Warmup(Player player, UUID sourceId, UUID targetId, Location startLocation, int seconds) {
            this.player = player;
            this.sourceId = sourceId;
            this.targetId = targetId;
            this.startLocation = startLocation;
            Anchor destinationAnchor = anchorsById.get(targetId);
            this.destinationAnchorLocation = destinationAnchor == null
                    ? startLocation.clone() : destinationAnchor.location().clone();
            double maxBaseHeight = Math.max(1D,
                    getConfig().getDouble("teleport.animation-base-height-max", 5D));
            double configuredMaxHeight = Math.max(maxBaseHeight + 30D,
                    getConfig().getDouble("teleport.animation-max-height", 35D));
            double horizontalDistance = sameWorld(startLocation, destinationAnchorLocation)
                    ? startLocation.distance(destinationAnchorLocation) : configuredMaxHeight * 2D;
            // Nearby anchors use a lower first lift; distant anchors converge on
            // the same capped cinematic height so duration never depends on distance.
            this.baseRiseHeight = Math.min(maxBaseHeight,
                    Math.max(2.5D, 2.5D + horizontalDistance * 0.02D));
            this.maxRiseHeight = Math.min(configuredMaxHeight, baseRiseHeight + 30D);
            double pushDistance = Math.max(0D,
                    getConfig().getDouble("teleport.warmup-push-distance", 0.35D));
            Vector backward = player.getLocation().getDirection().setY(0).normalize();
            if (backward.lengthSquared() < 0.0001D) {
                backward = new Vector(0, 0, -1);
            }
            this.facingDirection = backward.clone();
            this.lockLocation = startLocation.clone().subtract(backward.multiply(pushDistance));
            double eyeHeight = Math.max(1.0D, player.getEyeHeight());
            // Keep the exact final player view (including yaw/pitch) when the
            // client first enters spectator mode.
            this.cameraStartLocation = startLocation.clone().add(0, eyeHeight, 0);
            this.cameraEndLocation = startLocation.clone().subtract(facingDirection.clone().multiply(0.45D))
                    .add(0, eyeHeight, 0);
            this.wasInvisible = player.isInvisible();
            this.previousGameMode = player.getGameMode();
            this.fakePlayer = createFakePlayer(startLocation, player);
            this.camera = createCamera(startLocation, player);
            this.flashDisplay = createFlashDisplay(cameraStartLocation);
            player.teleport(lockLocation);
            player.setInvisible(true);
            player.setGameMode(GameMode.SPECTATOR);
            // Let the client receive the gamemode/entity spawn before selecting
            // the camera target; otherwise the spectator camera can remain at
            // the old player position on some Paper client combinations.
            // The opening pull-out/rise uses the smooth ArmorStand camera.
            // Once the first height is reached, the animation switches to
            // direct player teleports for the discrete A/B/C states.
            Bukkit.getScheduler().runTask(SoulAnchorPlugin.this, () -> {
                if (player.isOnline() && camera.isValid()) {
                    player.setSpectatorTarget(camera);
                }
            });
            player.playSound(startLocation, Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.8F, 0.8F);
        }

        private ArmorStand createCamera(Location soulLocation, Player player) {
            Location cameraLocation = cameraStartLocation.clone();
            cameraLocation.setYaw(soulLocation.getYaw());
            cameraLocation.setPitch(soulLocation.getPitch());
            return cameraLocation.getWorld().spawn(cameraLocation, ArmorStand.class, entity -> {
                entity.setInvisible(true);
                entity.setMarker(true);
                entity.setGravity(false);
                entity.setInvulnerable(true);
                entity.setSilent(true);
                entity.setCollidable(false);
            });
        }

        private ItemDisplay createFlashDisplay(Location location) {
            return location.getWorld().spawn(location, ItemDisplay.class, display -> {
                display.setItemStack(createFlashStack(910000));
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                display.setBillboard(Display.Billboard.CENTER);
                display.setViewRange(64F);
                display.setBrightness(new Display.Brightness(15, 15));
                display.setShadowStrength(0F);
                display.setShadowRadius(0F);
                display.setInterpolationDuration(1);
                display.setInterpolationDelay(0);
                display.setTransformation(new Transformation(
                        new Vector3f(), new Quaternionf(), new Vector3f(0.01F), new Quaternionf()));
                display.setInvisible(true);
                display.setPersistent(false);
            });
        }

        private Entity createFakePlayer(Location location, Player player) {
            Player transientPlayer = TransientFakePlayer.trySpawn(player, location);
            if (transientPlayer != null) {
                return transientPlayer;
            }
            ArmorStand stand = location.getWorld().spawn(location, ArmorStand.class, entity -> {
                entity.setVisible(true);
                entity.setGravity(false);
                entity.setInvulnerable(true);
                entity.setSilent(true);
                entity.setCollidable(false);
                entity.setBasePlate(false);
                entity.setArms(true);
                entity.setCustomNameVisible(false);

                // Plugin-only approximation of a suspended soul: tilt the body
                // instead of relying on a client-side fake-player model.
                double bodyTilt = Math.toRadians(60D);
                entity.setBodyPose(new EulerAngle(bodyTilt, 0D, 0D));
                entity.setHeadPose(new EulerAngle(Math.toRadians(-12D), 0D, 0D));
                entity.setLeftArmPose(new EulerAngle(Math.toRadians(-18D), 0D, Math.toRadians(-10D)));
                entity.setRightArmPose(new EulerAngle(Math.toRadians(-18D), 0D, Math.toRadians(10D)));
                entity.setLeftLegPose(new EulerAngle(Math.toRadians(12D), 0D, Math.toRadians(-4D)));
                entity.setRightLegPose(new EulerAngle(Math.toRadians(12D), 0D, Math.toRadians(4D)));

                if (entity.getEquipment() != null) {
                    entity.getEquipment().setHelmet(player.getInventory().getHelmet());
                    entity.getEquipment().setChestplate(player.getInventory().getChestplate());
                    entity.getEquipment().setLeggings(player.getInventory().getLeggings());
                    entity.getEquipment().setBoots(player.getInventory().getBoots());
                    entity.getEquipment().setItemInMainHand(player.getInventory().getItemInMainHand());
                    entity.getEquipment().setItemInOffHand(player.getInventory().getItemInOffHand());
                    ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                    SkullMeta meta = (SkullMeta) head.getItemMeta();
                    if (meta != null) {
                        meta.setOwningPlayer(player);
                        head.setItemMeta(meta);
                        entity.getEquipment().setHelmet(head);
                    }
                }
            });
            stand.setRotation(location.getYaw(), location.getPitch());
            return stand;
        }

        private void playTickEffect(double progress) {
            if (!player.isOnline()) {
                return;
            }
            animateCamera(progress);
            if (!playerCameraActive && camera.isValid()
                    && player.getGameMode() == GameMode.SPECTATOR
                    && player.getSpectatorTarget() != camera) {
                player.setSpectatorTarget(camera);
            } else if (playerCameraActive && player.getGameMode() == GameMode.SPECTATOR
                    && player.getSpectatorTarget() != null) {
                player.setSpectatorTarget(null);
            }
            updateFlashDisplay();
        }

        private void animateCamera(double progress) {
            if (!camera.isValid()) {
                return;
            }

            double clamped = Math.max(0D, Math.min(1D, progress));
            double eyeHeight = Math.max(1.0D, player.getEyeHeight());
            Location sourceGround = cameraEndLocation.clone();
            Location sourceHigh = highLocation(sourceGround, maxRiseHeight);
            Location destinationBase = destinationAnchorLocation.clone().add(0D, eyeHeight + baseRiseHeight, 0D);
            Location destinationGround = destinationAnchorLocation.clone().add(0D, eyeHeight, 0D);
            Location destinationHigh = destinationAnchorLocation.clone().add(0D, eyeHeight + maxRiseHeight, 0D);

            // The normalized timeline is deliberately distance-independent.
            // Only the first lift and the final landing are smooth. Every
            // height state between them is an instant camera teleport.
            if (clamped < 0.04D) {
                Location next = interpolate(cameraStartLocation, sourceGround, smoothstep(clamped / 0.04D));
                next.setDirection(cameraStartLocation.getDirection());
                smoothCamera(next);
                return;
            }
            if (clamped < 0.20D) {
                Location firstHigh = highLocation(sourceGround, baseRiseHeight);
                Location next = interpolate(sourceGround, firstHigh, smoothstep((clamped - 0.04D) / 0.16D));
                next.setDirection(new Vector(0D, -1D, 0D));
                smoothCamera(next);
                return;
            }
            if (clamped < 0.26D) {
                Location next = highLocation(sourceGround, baseRiseHeight + 0.20D
                        * smoothstep((clamped - 0.20D) / 0.06D));
                next.setDirection(new Vector(0D, -1D, 0D));
                smoothCamera(next);
                return;
            }

            // Ascending direction: three equal flash states. The camera is
            // teleported to the new height at the state boundary; it never
            // flies through the 15-block gap between states.
            if (clamped < 0.56D) {
                int stage = Math.min(2, (int) ((clamped - 0.26D) / 0.10D));
                double stateHeight = baseRiseHeight + stage * 15D;
                Location next = highLocation(sourceGround, Math.min(maxRiseHeight, stateHeight));
                next.setDirection(new Vector(0D, -1D, 0D));
                if (stage != lastFlashStage) {
                    switchToPlayerCamera(next);
                    lastFlashStage = stage;
                    playFlashEffect(next, stage);
                } else {
                    teleportPlayerCamera(next);
                }
                return;
            }

            // Move only a short visible distance, then switch the camera to
            // the destination high point in one server-side teleport.
            if (clamped < 0.62D) {
                double local = smoothstep((clamped - 0.56D) / 0.06D);
                Location shortMove = sourceHigh.clone().add(
                        destinationAnchorLocation.toVector().subtract(startLocation.toVector())
                                .normalize().multiply(2.0D * local));
                shortMove.setDirection(new Vector(0D, -1D, 0D));
                if (clamped >= 0.59D) {
                    if (!destinationCameraSnapped) {
                        teleportPlayerCamera(destinationHigh);
                        player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 1.0F, 1.0F);
                        destinationCameraSnapped = true;
                    }
                } else {
                    teleportPlayerCamera(shortMove);
                }
                return;
            }

            // Descending direction: play the three height/flash states in
            // reverse order. This is the inverse of the ascending sequence.
            if (clamped < 0.92D) {
                int stage = Math.min(2, (int) ((clamped - 0.62D) / 0.10D));
                double stateHeight = baseRiseHeight + (2 - stage) * 15D;
                Location next = destinationAnchorLocation.clone()
                        .add(0D, eyeHeight + Math.min(maxRiseHeight, stateHeight), 0D);
                next.setDirection(new Vector(0D, -1D, 0D));
                if (stage != lastFlashStage) {
                    teleportPlayerCamera(next);
                    lastFlashStage = stage;
                    playFlashEffect(next, stage);
                } else {
                    teleportPlayerCamera(next);
                }
                return;
            }

            // The final small drop is the reverse of the initial smooth lift.
            Location next = interpolate(destinationBase, destinationGround,
                    smoothstep((clamped - 0.92D) / 0.08D));
            next.setDirection(new Vector(0D, -1D, 0D));
            if (playerCameraActive) {
                playerCameraActive = false;
                camera.teleport(destinationBase);
                if (player.isOnline() && player.getGameMode() == GameMode.SPECTATOR) {
                    player.setSpectatorTarget(camera);
                }
            }
            smoothCamera(next);
        }

        private void teleportPlayerCamera(Location eyeLocation) {
            if (!player.isOnline()) {
                return;
            }
            double eyeHeight = Math.max(1.0D, player.getEyeHeight());
            Location playerLocation = eyeLocation.clone().subtract(0D, eyeHeight, 0D);
            playerLocation.setYaw(eyeLocation.getYaw());
            playerLocation.setPitch(eyeLocation.getPitch());
            player.teleport(playerLocation);
            if (camera != null && camera.isValid()) {
                camera.teleport(eyeLocation);
            }
        }

        private void smoothCamera(Location eyeLocation) {
            if (camera != null && camera.isValid()) {
                camera.teleport(eyeLocation);
            }
        }

        private void switchToPlayerCamera(Location eyeLocation) {
            playerCameraActive = true;
            if (player.isOnline() && player.getGameMode() == GameMode.SPECTATOR) {
                player.setSpectatorTarget(null);
            }
            teleportPlayerCamera(eyeLocation);
        }

        private Location highLocation(Location base, double rise) {
            Location result = base.clone();
            result.setY(base.getY() + rise);
            return result;
        }

        private Vector interpolateDirection(Vector from, Vector to, double progress) {
            return from.clone().multiply(1D - progress).add(to.clone().multiply(progress)).normalize();
        }

        private double smoothstep(double value) {
            double clamped = Math.max(0D, Math.min(1D, value));
            return clamped * clamped * (3D - 2D * clamped);
        }

        private void playFlashEffect(Location location, int highState) {
            World world = location.getWorld();
            if (world == null) {
                return;
            }
            float pitch = 0.85F + highState * 0.18F;
            world.playSound(location, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.0F, pitch);
            if (player.isOnline()) {
                // A flash is intentionally short: roughly 6 server ticks,
                // independent of the configured animation update cadence.
                long updateTicks = Math.max(1L, Math.min(4L,
                        getConfig().getLong("teleport.animation-update-interval-ticks", 1L)));
                flashFrameTotal = Math.max(2, (int) Math.ceil(6D / updateTicks));
                flashTicks = flashFrameTotal;
            }
        }

        private void updateFlashDisplay() {
            if (flashDisplay == null || !flashDisplay.isValid()) {
                return;
            }
            if (flashTicks <= 0 || !camera.isValid()) {
                flashDisplay.setItemStack(createFlashStack(910000));
                flashDisplay.setInvisible(true);
                return;
            }

            Location cameraLocation = camera.getLocation();
            Location displayLocation = cameraLocation.clone()
                    .add(cameraLocation.getDirection().normalize().multiply(0.55D));
            flashDisplay.teleport(displayLocation);
            flashDisplay.setRotation(cameraLocation.getYaw(), cameraLocation.getPitch());

            int frame = flashFrameTotal - flashTicks;
            double t = flashFrameTotal <= 1 ? 1D : frame / (double) (flashFrameTotal - 1);
            double envelope = Math.sin(Math.PI * Math.max(0D, Math.min(1D, t)));
            // Symmetric flash: 0% -> 100% -> 0%.
            float scale = (float) (8D + 42D * envelope);
            flashDisplay.setTransformation(new Transformation(
                    new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, 0.08F), new Quaternionf()));
            int alphaModel = envelope < 0.05D ? 910000
                    : envelope < 0.20D ? 910001
                    : envelope < 0.55D ? 910002
                    : envelope < 0.85D ? 910003
                    : envelope < 0.97D ? 910004 : 910005;
            flashDisplay.setItemStack(createFlashStack(alphaModel));
            flashDisplay.setInvisible(frame == 0 || frame >= flashFrameTotal - 1);
            flashTicks--;
        }

        private ItemStack createFlashStack(int customModelData) {
            ItemStack stack = new ItemStack(Material.PAPER);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(customModelData);
                stack.setItemMeta(meta);
            }
            return stack;
        }

        private Location interpolate(Location from, Location to, double progress) {
            return from.clone().add(to.toVector().subtract(from.toVector()).multiply(progress));
        }

        private void finishEffect() {
            restorePlayer();
            removeFakePlayerVisual();
            if (camera != null && camera.isValid()) {
                camera.remove();
            }
            if (flashDisplay != null && flashDisplay.isValid()) {
                flashDisplay.remove();
            }
        }

        private void restorePlayer() {
            if (player.isOnline()) {
                player.setSpectatorTarget(null);
                player.setGameMode(previousGameMode);
                player.setInvisible(wasInvisible);
            }
        }

        private void cancel(boolean notify) {
            warmups.remove(player.getUniqueId());
            if (task != null) {
                task.cancel();
            }
            restorePlayer();
            if (player.isOnline()) {
                player.teleport(startLocation);
            }
            removeFakePlayerVisual();
            if (camera != null && camera.isValid()) {
                camera.remove();
            }
            if (flashDisplay != null && flashDisplay.isValid()) {
                flashDisplay.remove();
            }
            if (notify && player.isOnline()) {
                send(player, "warmup-cancelled");
                player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.6F, 1F);
            }
        }

        private void removeFakePlayerVisual() {
            if (fakePlayer == null || !fakePlayer.isValid()) {
                return;
            }
            if (fakePlayer instanceof Player fake) {
                TransientFakePlayer.tryRemove(fake);
            } else {
                fakePlayer.remove();
            }
        }
    }
}
