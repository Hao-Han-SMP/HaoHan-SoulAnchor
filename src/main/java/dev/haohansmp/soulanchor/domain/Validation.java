package dev.haohansmp.soulanchor.domain;

import org.bukkit.Location;

public record Validation(boolean ok, String messageKey, Location safeDestination, String[] replacements) {
    public static Validation ok(Location safeDestination) {
        return new Validation(true, "", safeDestination, new String[0]);
    }

    public static Validation fail(String messageKey, String... replacements) {
        return new Validation(false, messageKey, null, replacements);
    }
}
