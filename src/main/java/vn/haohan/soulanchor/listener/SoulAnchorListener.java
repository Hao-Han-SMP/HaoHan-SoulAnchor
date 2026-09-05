package vn.haohan.soulanchor.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import vn.haohan.soulanchor.SoulAnchorPlugin;

/** Bukkit adapter. Gameplay decisions stay in SoulAnchorPlugin/application services. */
public final class SoulAnchorListener implements Listener {
    private final SoulAnchorPlugin plugin;

    public SoulAnchorListener(SoulAnchorPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) { plugin.onJoin(event); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) { plugin.onPlace(event); }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) { plugin.onInteract(event); }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) { plugin.onInteractEntity(event); }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) { plugin.onInventoryClick(event); }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) { plugin.onInventoryClose(event); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) { plugin.onBreak(event); }

    @EventHandler(ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) { plugin.onPhysics(event); }

    @EventHandler(ignoreCancelled = true)
    public void onFluid(BlockFromToEvent event) { plugin.onFluid(event); }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) { plugin.onPistonExtend(event); }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) { plugin.onPistonRetract(event); }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) { plugin.onEntityExplode(event); }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) { plugin.onBlockExplode(event); }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) { plugin.onMove(event); }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) { plugin.onDamage(event); }

    @EventHandler(ignoreCancelled = true)
    public void onDealDamage(EntityDamageByEntityEvent event) { plugin.onDealDamage(event); }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) { plugin.onFish(event); }

    @EventHandler
    public void onWardenDeath(EntityDeathEvent event) { plugin.onWardenDeath(event); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { plugin.onQuit(event); }
}
