package vn.haohan.soulanchor.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Loads and formats user-facing messages in one place. */
public final class MessageService {
    private final JavaPlugin plugin;
    private final File file;
    private final Function<String, String> color;
    private FileConfiguration messages;

    public MessageService(JavaPlugin plugin, File file, Function<String, String> color) {
        this.plugin = plugin;
        this.file = file;
        this.color = color;
    }

    public void load() {
        messages = YamlConfiguration.loadConfiguration(file);
        try (InputStream stream = plugin.getResource("messages.yml")) {
            if (stream == null) {
                return;
            }
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            messages.setDefaults(defaults);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not load default messages: " + exception.getMessage());
        }
    }

    public void send(CommandSender sender, String key, String... replacements) {
        String text = messages.getString(key, key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        sender.sendMessage(color.apply(messages.getString("prefix", "") + text));
    }
}
