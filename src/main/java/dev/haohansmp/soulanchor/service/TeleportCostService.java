package dev.haohansmp.soulanchor.service;

import java.util.List;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import dev.haohansmp.soulanchor.domain.Cost;

/** Pure teleport economics and destination-safety rules. */
public final class TeleportCostService {
    private final JavaPlugin plugin;

    public TeleportCostService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Cost calculate(Location source, Location target) {
        int requiredLevels;
        int shards;
        if (!sameWorld(source, target)) {
            requiredLevels = Math.max(0, plugin.getConfig().getInt("cross-dimension.level-cost", 30));
            shards = Math.max(0, plugin.getConfig().getInt("cross-dimension.echo-shard-cost", 1));
        } else {
            double dx = target.getX() - source.getX();
            double dz = target.getZ() - source.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            int blocksPerTier = Math.max(1, plugin.getConfig().getInt("distance.blocks-per-tier", 1000));
            int levelsPerTier = Math.max(1, plugin.getConfig().getInt("distance.levels-per-tier", 10));
            int minCost = Math.max(0, plugin.getConfig().getInt("distance.minimum-level-cost", 10));
            requiredLevels = Math.max(minCost, (int) Math.ceil(distance / blocksPerTier) * levelsPerTier);
            double freeShardDistance = Math.max(0D,
                    plugin.getConfig().getDouble("teleport.echo-shard-free-distance", 2000D));
            shards = distance <= freeShardDistance ? 0
                    : Math.max(0, plugin.getConfig().getInt("teleport.echo-shard-cost", 1));
        }

        int pointsPerRequiredLevel = Math.max(0,
                plugin.getConfig().getInt("teleport.experience-points-per-required-level", 8));
        int experiencePoints = (int) Math.min(Integer.MAX_VALUE, (long) requiredLevels * pointsPerRequiredLevel);
        return new Cost(requiredLevels, experiencePoints, shards);
    }

    public String formatDistance(Location source, Location target) {
        if (!sameWorld(source, target)) {
            return "cross-dimension";
        }
        double dx = target.getX() - source.getX();
        double dz = target.getZ() - source.getZ();
        return String.valueOf((int) Math.round(Math.sqrt(dx * dx + dz * dz)));
    }

    public boolean sameWorld(Location left, Location right) {
        return left.getWorld() != null && right.getWorld() != null
                && left.getWorld().getUID().equals(right.getWorld().getUID());
    }

    public int countEchoShards(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.ECHO_SHARD) {
                count += item.getAmount();
            }
        }
        return count;
    }

    public void removeEchoShards(Player player, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != Material.ECHO_SHARD) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
            if (item.getAmount() <= 0) {
                contents[i] = null;
            }
        }
        player.getInventory().setContents(contents);
    }

    public Location findSafeLocation(Location anchorLocation) {
        int horizontal = plugin.getConfig().getInt("safe-location.horizontal-radius", 3);
        int vertical = plugin.getConfig().getInt("safe-location.vertical-radius", 4);
        World world = anchorLocation.getWorld();
        int baseX = anchorLocation.getBlockX();
        int baseY = anchorLocation.getBlockY();
        int baseZ = anchorLocation.getBlockZ();

        for (int dy = 0; dy <= vertical; dy++) {
            for (int sign : new int[] { 1, -1 }) {
                if (dy == 0 && sign < 0) {
                    continue;
                }
                int y = baseY + dy * sign;
                for (int radius = 1; radius <= horizontal; radius++) {
                    for (int x = baseX - radius; x <= baseX + radius; x++) {
                        for (int z = baseZ - radius; z <= baseZ + radius; z++) {
                            if (Math.max(Math.abs(x - baseX), Math.abs(z - baseZ)) != radius) {
                                continue;
                            }
                            Location candidate = new Location(world, x + 0.5D, y, z + 0.5D,
                                    anchorLocation.getYaw(), anchorLocation.getPitch());
                            if (isSafe(candidate)) {
                                return candidate;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public boolean isWorldBlocked(World world) {
        String mode = plugin.getConfig().getString("worlds.mode", "blacklist").toLowerCase(Locale.ROOT);
        List<String> worlds = plugin.getConfig().getStringList("worlds.list");
        boolean listed = worlds.stream().anyMatch(name -> name.equalsIgnoreCase(world.getName()));
        return mode.equals("blacklist") ? listed : !listed;
    }

    private boolean isSafe(Location location) {
        World world = location.getWorld();
        if (world == null || location.getBlockY() <= world.getMinHeight()
                || location.getBlockY() >= world.getMaxHeight() - 2) {
            return false;
        }
        WorldBorder border = world.getWorldBorder();
        if (!border.isInside(location)) {
            return false;
        }
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block below = feet.getRelative(0, -1, 0);
        return feet.isPassable() && head.isPassable() && below.getType().isSolid()
                && !isHazard(feet.getType()) && !isHazard(head.getType()) && !isHazard(below.getType());
    }

    private boolean isHazard(Material material) {
        return material == Material.LAVA || material == Material.FIRE || material == Material.SOUL_FIRE
                || material == Material.CACTUS || material == Material.POWDER_SNOW || material == Material.MAGMA_BLOCK
                || material == Material.CAMPFIRE || material == Material.SOUL_CAMPFIRE
                || material == Material.END_PORTAL || material == Material.NETHER_PORTAL;
    }
}
