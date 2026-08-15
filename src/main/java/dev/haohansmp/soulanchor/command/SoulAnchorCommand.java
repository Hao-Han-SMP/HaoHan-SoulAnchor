package dev.haohansmp.soulanchor.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import dev.haohansmp.soulanchor.domain.Anchor;
import dev.haohansmp.soulanchor.SoulAnchorPlugin;

/** Command parsing and permission checks. Gameplay state changes are delegated to the plugin services. */
public final class SoulAnchorCommand {
    private final SoulAnchorPlugin plugin;

    public SoulAnchorCommand(SoulAnchorPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                plugin.sendMessage(sender, "player-only");
                return true;
            }
            plugin.listAnchors(sender, player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "give" -> give(sender, args);
            case "list" -> list(sender, args);
            case "rename" -> rename(sender, args);
            case "share" -> share(sender, args);
            case "reload" -> reload(sender);
            default -> {
                sender.sendMessage(plugin.colorMessage("&3SoulAnchor &7commands: &f/soulanchor give|list|rename|share|reload"));
                yield true;
            }
        };
    }

    public List<String> complete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], List.of("give", "list", "rename", "share", "reload"),
                    new ArrayList<>());
        }
        if (args.length == 2 && List.of("give", "list").contains(args[0].toLowerCase(Locale.ROOT))) {
            return null;
        }
        if (sender instanceof Player player && args.length == 2
                && List.of("rename", "share").contains(args[0].toLowerCase(Locale.ROOT))) {
            return StringUtil.copyPartialMatches(args[1],
                    plugin.ownedAnchors(player.getUniqueId()).stream().map(Anchor::name).toList(), new ArrayList<>());
        }
        if (sender instanceof Player && args.length >= 3 && args[0].equalsIgnoreCase("share")) {
            return StringUtil.copyPartialMatches(args[args.length - 1],
                    Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), new ArrayList<>());
        }
        return List.of();
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("soulanchor.admin.give")) {
            plugin.sendMessage(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.colorMessage("&cUsage: /soulanchor give <player> [amount]"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.colorMessage("&cPlayer not found."));
            return true;
        }
        int amount = args.length >= 3 ? plugin.parseIntValue(args[2], 1) : 1;
        amount = Math.max(1, Math.min(64, amount));
        target.getInventory().addItem(plugin.createAnchorItemValue(amount)).values()
                .forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
        plugin.sendMessage(sender, "given", "{amount}", String.valueOf(amount), "{player}", target.getName());
        return true;
    }

    private boolean list(CommandSender sender, String[] args) {
        if (args.length >= 2 && sender.hasPermission("soulanchor.admin")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(plugin.colorMessage("&cPlayer not found."));
                return true;
            }
            plugin.listAnchors(sender, target);
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, "player-only");
            return true;
        }
        plugin.listAnchors(sender, player);
        return true;
    }

    private boolean rename(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, "player-only");
            return true;
        }
        if (!player.hasPermission("soulanchor.rename")) {
            plugin.sendMessage(player, "no-permission");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(plugin.colorMessage("&cUsage: /soulanchor rename <anchor> <new-name>"));
            return true;
        }
        Anchor anchor = null;
        int newNameStart = -1;
        for (int split = args.length - 1; split >= 2; split--) {
            String query = String.join(" ", List.of(args).subList(1, split));
            anchor = plugin.findOwnedAnchor(player.getUniqueId(), query).orElse(null);
            if (anchor != null) {
                newNameStart = split;
                break;
            }
        }
        if (anchor == null) {
            plugin.sendMessage(player, "not-anchor");
            return true;
        }
        Anchor renamed = anchor.withName(plugin.sanitizeNameValue(
                String.join(" ", List.of(args).subList(newNameStart, args.length))));
        plugin.updateAnchor(renamed);
        plugin.saveAnchorData();
        plugin.sendMessage(player, "anchor-renamed", "{name}", renamed.name());
        return true;
    }

    private boolean share(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, "player-only");
            return true;
        }
        if (!player.hasPermission("soulanchor.share")) {
            plugin.sendMessage(player, "no-permission");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(plugin.colorMessage("&cUsage: /soulanchor share <anchor> <player>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[args.length - 1]);
        if (target == null) {
            plugin.sendMessage(player, "player-not-found");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            plugin.sendMessage(player, "share-self");
            return true;
        }
        String query = String.join(" ", List.of(args).subList(1, args.length - 1));
        Anchor anchor = plugin.findOwnedAnchor(player.getUniqueId(), query).orElse(null);
        if (anchor == null) {
            plugin.sendMessage(player, "not-anchor");
            return true;
        }
        if (anchor.sharedWith().contains(target.getUniqueId())) {
            plugin.sendMessage(player, "anchor-already-shared", "{anchor}", anchor.name(), "{player}", target.getName());
            return true;
        }
        Anchor shared = anchor.withSharedPlayer(target.getUniqueId());
        plugin.updateAnchor(shared);
        plugin.saveAnchorData();
        plugin.sendMessage(player, "anchor-shared", "{anchor}", shared.name(), "{player}", target.getName());
        plugin.sendMessage(target, "anchor-shared-received", "{anchor}", shared.name(), "{player}", player.getName());
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("soulanchor.admin.reload")) {
            plugin.sendMessage(sender, "no-permission");
            return true;
        }
        plugin.reloadPluginState();
        plugin.sendMessage(sender, "reloaded");
        return true;
    }
}
