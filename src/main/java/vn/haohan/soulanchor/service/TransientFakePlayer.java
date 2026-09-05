package vn.haohan.soulanchor.service;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Minimal reflective ServerPlayer adapter with a safe ArmorStand fallback. */
public final class TransientFakePlayer {
    private static final Map<UUID, Object> NMS_PLAYERS = new ConcurrentHashMap<>();

    private TransientFakePlayer() {}

    public static Player trySpawn(Player source, Location location) {
        try {
            ClassLoader loader = Bukkit.getServer().getClass().getClassLoader();
            Class<?> serverClass = nms(loader, "net.minecraft.server.MinecraftServer");
            Class<?> levelClass = nms(loader, "net.minecraft.server.level.ServerLevel");
            Class<?> playerClass = nms(loader, "net.minecraft.server.level.ServerPlayer");
            Class<?> profileClass = nms(loader, "com.mojang.authlib.GameProfile");
            Object minecraftServer = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            Object serverLevel = location.getWorld().getClass().getMethod("getHandle").invoke(location.getWorld());
            Object profile = profileClass.getConstructor(UUID.class, String.class)
                    .newInstance(UUID.randomUUID(), source.getName());
            copySkinProperties(source, profile, profileClass, loader);
            Object clientInfo = clientInformation(loader);
            Object nmsPlayer = createServerPlayer(playerClass, serverClass, levelClass, profile, clientInfo,
                    minecraftServer, serverLevel);
            if (nmsPlayer == null) return null;
            invokeSetPos(nmsPlayer, location);
            Object connection = createConnection(loader);
            if (connection == null) return null;
            Object cookie = createCookie(loader, profile, clientInfo);
            if (cookie == null) return null;
            Object playerList = serverClass.getMethod("getPlayerList").invoke(minecraftServer);
            Method place = findMethod(playerList.getClass(), "placeNewPlayer", 3);
            if (place == null) return null;
            place.invoke(playerList, connection, nmsPlayer, cookie);
            Player fake = (Player) playerClass.getMethod("getBukkitEntity").invoke(nmsPlayer);
            fake.setGameMode(GameMode.SURVIVAL);
            fake.setRotation(location.getYaw(), location.getPitch());
            fake.setCollidable(false);
            fake.setInvulnerable(true);
            fake.setSilent(true);
            fake.setCustomNameVisible(false);
            setListed(playerClass, nmsPlayer, false);
            NMS_PLAYERS.put(fake.getUniqueId(), nmsPlayer);
            return fake;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean tryRemove(Player fake) {
        if (fake == null) return true;
        Object nmsPlayer = NMS_PLAYERS.remove(fake.getUniqueId());
        try {
            if (nmsPlayer != null) {
                Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
                Object playerList = server.getClass().getMethod("getPlayerList").invoke(server);
                Method remove = findMethod(playerList.getClass(), "remove", 1);
                if (remove != null) remove.invoke(playerList, nmsPlayer);
            }
        } catch (Throwable ignored) {
            // Bukkit kick below remains the final cleanup path.
        }
        if (fake.isOnline()) fake.kickPlayer("");
        return nmsPlayer != null;
    }

    private static Object createConnection(ClassLoader loader) throws Exception {
        Class<?> connectionClass = nms(loader, "net.minecraft.network.Connection");
        Class<?> flowClass = nms(loader, "net.minecraft.network.protocol.PacketFlow");
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object flow = Enum.valueOf((Class<? extends Enum>) flowClass.asSubclass(Enum.class), "SERVERBOUND");
        Object connection = connectionClass.getConstructor(flowClass).newInstance(flow);
        Class<?> channelClass = Class.forName("io.netty.channel.embedded.EmbeddedChannel", true, loader);
        Object channel = channelClass.getConstructor().newInstance();
        Field channelField = findAssignableField(connectionClass, channelClass);
        if (channelField == null) return null;
        channelField.setAccessible(true);
        channelField.set(connection, channel);
        return connection;
    }

    private static Object createCookie(ClassLoader loader, Object profile, Object clientInfo) throws Exception {
        Class<?> cookieClass = nms(loader, "net.minecraft.server.network.CommonListenerCookie");
        try {
            Method factory = cookieClass.getMethod("createInitial", profile.getClass(), boolean.class);
            return factory.invoke(null, profile, false);
        } catch (ReflectiveOperationException ignored) {
            for (Constructor<?> constructor : cookieClass.getDeclaredConstructors()) {
                constructor.setAccessible(true);
                Class<?>[] types = constructor.getParameterTypes();
                try {
                    Object value = switch (types.length) {
                        case 1 -> constructor.newInstance(profile);
                        case 2 -> constructor.newInstance(profile, 0);
                        case 3 -> constructor.newInstance(profile, 0, clientInfo);
                        case 4 -> constructor.newInstance(profile, 0, clientInfo, false);
                        case 5 -> constructor.newInstance(profile, 0, clientInfo, false, false);
                        case 7 -> constructor.newInstance(profile, 0, clientInfo, false,
                                null, Collections.emptySet(), null);
                        default -> null;
                    };
                    if (value != null) return value;
                } catch (ReflectiveOperationException ignoredAgain) {
                    // Try the next mapping variant.
                }
            }
        }
        return null;
    }

    private static Object createServerPlayer(Class<?> playerClass, Class<?> serverClass, Class<?> levelClass,
            Object profile, Object clientInfo, Object server, Object level) throws Exception {
        if (clientInfo != null) {
            for (Constructor<?> constructor : playerClass.getConstructors()) {
                Class<?>[] types = constructor.getParameterTypes();
                if (types.length == 4 && types[0].isAssignableFrom(serverClass)
                        && types[1].isAssignableFrom(levelClass)
                        && types[2].isAssignableFrom(profile.getClass())) {
                    return constructor.newInstance(server, level, profile, clientInfo);
                }
            }
        }
        for (Constructor<?> constructor : playerClass.getConstructors()) {
            Class<?>[] types = constructor.getParameterTypes();
            if (types.length == 3 && types[0].isAssignableFrom(serverClass)
                    && types[1].isAssignableFrom(levelClass)
                    && types[2].isAssignableFrom(profile.getClass())) {
                return constructor.newInstance(server, level, profile);
            }
        }
        return null;
    }

    private static Object clientInformation(ClassLoader loader) {
        try {
            Class<?> type = nms(loader, "net.minecraft.server.level.ClientInformation");
            return type.getMethod("createDefault").invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void copySkinProperties(Player source, Object gameProfile,
            Class<?> gameProfileClass, ClassLoader loader) {
        try {
            Object paperProfile = source.getPlayerProfile();
            Method getProperties = paperProfile.getClass().getMethod("getProperties");
            Object properties = getProperties.invoke(paperProfile);
            if (!(properties instanceof Iterable<?> iterable)) return;

            Method getGameProperties = gameProfileClass.getMethod("getProperties");
            Object gameProperties = getGameProperties.invoke(gameProfile);
            Class<?> propertyClass = Class.forName(
                    "com.mojang.authlib.properties.Property", true, loader);
            Method put = gameProperties.getClass().getMethod("put", Object.class, Object.class);
            for (Object property : iterable) {
                Method name = property.getClass().getMethod("getName");
                if (!"textures".equalsIgnoreCase(String.valueOf(name.invoke(property)))) continue;
                String value = String.valueOf(property.getClass().getMethod("getValue").invoke(property));
                String signature = String.valueOf(property.getClass().getMethod("getSignature").invoke(property));
                Object authProperty;
                if (signature == null || "null".equals(signature) || signature.isBlank()) {
                    authProperty = propertyClass.getConstructor(String.class, String.class)
                            .newInstance("textures", value);
                } else {
                    authProperty = propertyClass.getConstructor(String.class, String.class, String.class)
                            .newInstance("textures", value, signature);
                }
                put.invoke(gameProperties, "textures", authProperty);
                return;
            }
        } catch (Throwable ignored) {
            // A skin is cosmetic; the default skin is a valid fallback.
        }
    }

    private static void invokeSetPos(Object nmsPlayer, org.bukkit.Location location) throws Exception {
        for (Method method : nmsPlayer.getClass().getMethods()) {
            if (method.getName().equals("setPos") && method.getParameterCount() == 3
                    && method.getParameterTypes()[0] == double.class) {
                method.invoke(nmsPlayer, location.getX(), location.getY(), location.getZ());
                return;
            }
        }
    }

    private static void setListed(Class<?> playerClass, Object nmsPlayer, boolean listed) {
        try {
            Field field = playerClass.getDeclaredField("listed");
            field.setAccessible(true);
            field.setBoolean(nmsPlayer, listed);
        } catch (Throwable ignored) {
            // Optional cosmetic flag.
        }
    }

    private static Method findMethod(Class<?> type, String name, int parameters) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameters) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static Field findAssignableField(Class<?> owner, Class<?> valueType) {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getType().isAssignableFrom(valueType)) return field;
            }
        }
        return null;
    }

    private static Class<?> nms(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName(name, true, loader);
    }

}
