package com.sockywocky.createaddonorganizer.client;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Set;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.sockywocky.createaddonorganizer.createaddonorganizer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

public final class DevMode {
    private static final long GESTURE_WINDOW_MS = 2000L;
    private static final int GESTURE_PRESSES = 3;

    private static final Set<String> AUTHORIZED_USERNAMES = Set.of("Dev", "SockyWocky7");

    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int PBKDF2_ITERATIONS = 250_000;
    private static final int PBKDF2_KEY_LENGTH_BITS = 256;
    private static final String CODE_SALT_HEX = "c3e41dd1ca7d106db2b5b2a285d5ba12";
    private static final String CODE_HASH_HEX =
            "15d0bfdc5f072584579a06b6490f2fc111432aa5f4e215d3a4687e44249e89c0";

    private static final int MAX_ATTEMPTS_BEFORE_LOCKOUT = 3;
    private static final long LOCKOUT_MS = 5 * 60_000L;

    private static int failedAttempts;
    private static long lockedUntilMillis;

    private static final Field CONFIG_SCREEN_MOD_FIELD = resolveField(ConfigurationScreen.class, "mod");
    private static final Field SECTION_SCREEN_CONTEXT_FIELD = resolveField(ConfigurationSectionScreen.class, "context");

    private static boolean unlocked;
    private static int shiftPressCount;
    private static long firstPressAtMillis;
    private static boolean shiftWasDown;

    private DevMode() {}

    public static boolean isUnlocked() {
        return unlocked;
    }

    public static void tick(Minecraft mc) {
        if (!(mc.screen instanceof Screen screen) || !isOurScreen(screen)) {
            shiftPressCount = 0;
            shiftWasDown = false;
            return;
        }
        long window = mc.getWindow().getWindow();
        boolean down = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        if (down && !shiftWasDown) {
            registerPress(mc, screen);
        }
        shiftWasDown = down;
    }

    private static void registerPress(Minecraft mc, Screen current) {
        long now = System.currentTimeMillis();
        if (shiftPressCount == 0 || now - firstPressAtMillis > GESTURE_WINDOW_MS) {
            shiftPressCount = 1;
            firstPressAtMillis = now;
        } else {
            shiftPressCount++;
        }
        if (shiftPressCount >= GESTURE_PRESSES) {
            shiftPressCount = 0;
            if (unlocked) {
                unlocked = false;
                Notice.show(Component.translatable("createaddonorganizer.devmode.deactivated"), Notice.RED);
            } else {
                mc.setScreen(new DevCodeScreen(current));
            }
        }
    }

    public static boolean isAuthorizedUser() {
        return AUTHORIZED_USERNAMES.contains(Minecraft.getInstance().getUser().getName());
    }

    public static boolean isLockedOut() {
        return System.currentTimeMillis() < lockedUntilMillis;
    }

    public static long lockoutRemainingMillis() {
        return Math.max(0, lockedUntilMillis - System.currentTimeMillis());
    }

    public static boolean checkCode(String code) {
        if (isLockedOut()) {
            return false;
        }
        boolean correct = isAuthorizedUser() && constantTimeEquals(CODE_HASH_HEX, pbkdf2Hex(code));
        if (correct) {
            unlocked = true;
            failedAttempts = 0;
        } else {
            registerFailure();
        }
        return correct;
    }

    private static void registerFailure() {
        failedAttempts++;
        if (failedAttempts >= MAX_ATTEMPTS_BEFORE_LOCKOUT) {
            lockedUntilMillis = System.currentTimeMillis() + LOCKOUT_MS;
            failedAttempts = 0;
        }
    }

    private static boolean constantTimeEquals(String expectedHex, String actualHex) {
        if (actualHex == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedHex.getBytes(StandardCharsets.UTF_8),
                actualHex.getBytes(StandardCharsets.UTF_8));
    }

    private static String pbkdf2Hex(String input) {
        try {
            byte[] salt = hexToBytes(CODE_SALT_HEX);
            PBEKeySpec spec = new PBEKeySpec(input.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            return null;
        }
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static boolean isOurScreen(Screen screen) {
        try {
            if (screen instanceof ConfigurationScreen cs && CONFIG_SCREEN_MOD_FIELD != null) {
                ModContainer mod = (ModContainer) CONFIG_SCREEN_MOD_FIELD.get(cs);
                return mod != null && createaddonorganizer.MODID.equals(mod.getModId());
            }
            if (screen instanceof ConfigurationSectionScreen css && SECTION_SCREEN_CONTEXT_FIELD != null) {
                ConfigurationSectionScreen.Context context =
                        (ConfigurationSectionScreen.Context) SECTION_SCREEN_CONTEXT_FIELD.get(css);
                return context != null && createaddonorganizer.MODID.equals(context.modId());
            }
        } catch (ReflectiveOperationException e) {
            return false;
        }
        return false;
    }

    private static Field resolveField(Class<?> owner, String name) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException | RuntimeException e) {
            createaddonorganizer.LOGGER.warn("[CAO] devmode: could not access {}#{}", owner.getSimpleName(), name, e);
            return null;
        }
    }
}
