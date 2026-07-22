package com.sockywocky.createaddonorganizer.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sockywocky.createaddonorganizer.createaddonorganizer;
import com.wdiscute.utils.ScreenUtils;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;

public final class TwoToneText {
    private static final int SCROLL_CYCLE_MS = 300;

    private static final int VERTICAL_BANDS = 3;

    private static final float SHADOW_BRIGHTNESS = 0.20f;

    private static boolean scrollingBroken = false;

    private TwoToneText() {}

    private static Integer targetHeight = null;
    private static Double targetScale = null;

    public static void setRenderTarget(int framebufferHeight, double pixelsPerUnit) {
        targetHeight = framebufferHeight;
        targetScale = pixelsPerUnit;
    }

    public static void clearRenderTarget() {
        targetHeight = null;
        targetScale = null;
    }

    public static boolean renderTargetActive() {
        return targetHeight != null;
    }

    public static void draw(GuiGraphics g, Font font, Component text, int x, int y, int primaryArgb, int secondaryArgb) {
        draw(g, font, text, x, y, primaryArgb, secondaryArgb, 5f / 9f);
    }

    public static void draw(GuiGraphics g, Font font, Component text, int x, int y, int primaryArgb, int secondaryArgb,
            float splitFraction) {
        g.drawString(font, text, x, y, primaryArgb, true);
        int w = font.width(text);
        int splitY = y + Math.round(font.lineHeight * splitFraction);
        g.pose().pushPose();
        g.pose().translate(0, 0, 1);
        beginScissor(g, x, splitY, x + w, y + font.lineHeight);
        g.drawString(font, text, x, y, secondaryArgb, false);
        endScissor(g);
        g.pose().popPose();
    }

    public static void draw(GuiGraphics g, Font font, Component text, int x, int y, int maxX,
            ColorSpec primary, ColorSpec secondary, float splitFraction,
            boolean vanillaShadow, int manualShadowArgb, ColorSpec outline) {
        int available = Math.max(0, maxX - x);
        boolean scroll = !renderTargetActive() && font.width(text) > available;

        if ((manualShadowArgb >>> 24) != 0) {
            drawPass(g, font, text, x + 1, y + 1, maxX + 1, ColorSpec.solid(manualShadowArgb), false, scroll);
        }

        if (outline != null) {
            drawPass(g, font, text, x, y - 1, maxX, outline, false, scroll);
            drawPass(g, font, text, x, y + 1, maxX, outline, vanillaShadow, scroll);
            drawPass(g, font, text, x + 1, y, maxX + 1, outline, vanillaShadow, scroll);
            drawPass(g, font, text, x - 1, y, maxX - 1, outline, false, scroll);
        }

        drawPass(g, font, text, x, y, maxX, primary, vanillaShadow, scroll);

        if (secondary != null) {
            int w = scroll ? available : font.width(text);
            int splitY = y + Math.round(font.lineHeight * splitFraction);
            g.pose().pushPose();
            g.pose().translate(0, 0, 1);
            beginScissor(g, x, splitY, x + w, y + font.lineHeight);
            drawPass(g, font, text, x, y, maxX, secondary, false, scroll);
            endScissor(g);
            g.pose().popPose();
        }
    }

    private static void drawPass(GuiGraphics g, Font font, Component text, int x, int y, int maxX, ColorSpec spec,
            boolean shadow, boolean scroll) {
        if (scroll) {
            if (spec.isGradient()) {
                drawScrollingGradient(g, font, text, x, y, maxX, spec, shadow);
                return;
            }
            int flat = spec.color1();
            if (!scrollingBroken) {
                try {
                    ScreenUtils.renderScrollingString(g, font, text, x, maxX, y, flat, shadow, SCROLL_CYCLE_MS);
                    return;
                } catch (Throwable t) {
                    scrollingBroken = true;
                    createaddonorganizer.LOGGER.warn(
                            "[CAO] FTS's scrolling-text helper (com.wdiscute.utils.ScreenUtils) is unavailable;"
                                    + " falling back to static (non-scrolling) title text", t);
                }
            }
            drawWithShadow(g, font, text, x, y, flat, shadow);
            return;
        }
        if (!spec.isGradient()) {
            drawWithShadow(g, font, text, x, y, spec.color1(), shadow);
            return;
        }
        drawGradientString(g, font, text, x, y, spec, shadow);
    }

    private static void drawWithShadow(GuiGraphics g, Font font, Component text, int x, int y, int color, boolean shadow) {
        if (shadow) {
            g.drawString(font, text, x + 1, y + 1, darkenForShadow(color), false);
        }
        g.drawString(font, text, x, y, color, false);
    }

    private static int darkenForShadow(int argb) {
        int a = argb >>> 24;
        int r = Math.round(((argb >> 16) & 0xFF) * SHADOW_BRIGHTNESS);
        int gr = Math.round(((argb >> 8) & 0xFF) * SHADOW_BRIGHTNESS);
        int b = Math.round((argb & 0xFF) * SHADOW_BRIGHTNESS);
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }

    private static void drawScrollingGradient(GuiGraphics g, Font font, Component text, int x, int y, int maxX,
            ColorSpec spec, boolean shadow) {
        int textWidth = font.width(text);
        int overflow = textWidth - (maxX - x);
        double cyclePos = Util.getMillis() / (double) SCROLL_CYCLE_MS;
        double period = Math.max(overflow * 0.5, 3.0);
        double wave = Math.sin(Math.cos(cyclePos * Math.PI * 2 / period) * (Math.PI / 2)) / 2.0 + 0.5;
        int drawX = x - (int) (wave * overflow);

        g.pose().pushPose();
        g.pose().translate(0, 0, 1);
        beginScissor(g, x, y - 20, maxX, y + 20);
        drawGradientString(g, font, text, drawX, y, spec, shadow);
        endScissor(g);
        g.pose().popPose();
    }

    private static void drawGradientString(GuiGraphics g, Font font, Component text, int x, int y, ColorSpec spec,
            boolean shadow) {
        if (spec.direction() == ColorSpec.Direction.HORIZONTAL) {
            drawHorizontalBand(g, font, text, x, y, spec, shadow, null);
            return;
        }

        int lineHeight = font.lineHeight;
        int w = font.width(text);
        for (int band = 0; band < VERTICAL_BANDS; band++) {
            float fracY = VERTICAL_BANDS <= 1 ? 0f : (float) band / (VERTICAL_BANDS - 1);
            int bandTop = y + lineHeight * band / VERTICAL_BANDS;
            int bandBottom = y + lineHeight * (band + 1) / VERTICAL_BANDS;
            g.pose().pushPose();
            g.pose().translate(0, 0, 1);
            beginScissor(g, x, bandTop, x + w, bandBottom);
            if (spec.direction() == ColorSpec.Direction.VERTICAL) {
                int color = ColorUtil.lerpArgb(fracY, spec.color1(), spec.color2());
                drawWithShadow(g, font, text, x, y, color, shadow);
            } else {
                float vFrac = spec.direction() == ColorSpec.Direction.DIAGONAL_UP ? 1f - fracY : fracY;
                drawHorizontalBand(g, font, text, x, y, spec, shadow, vFrac);
            }
            endScissor(g);
            g.pose().popPose();
        }
    }

    private static void drawHorizontalBand(GuiGraphics g, Font font, Component text, int x, int y, ColorSpec spec,
            boolean shadow, Float vBlend) {
        String s = text.getString();
        Style style = text.getStyle();
        int total = Math.max(1, font.width(text));
        int cursorX = x;
        for (int i = 0; i < s.length(); i++) {
            Component ch = Component.literal(s.substring(i, i + 1)).setStyle(style);
            int w = font.width(ch);
            float fracX = (cursorX - x + w / 2f) / total;
            float frac = vBlend == null ? fracX : (fracX + vBlend) / 2f;
            int color = ColorUtil.lerpArgb(Mth.clamp(frac, 0f, 1f), spec.color1(), spec.color2());
            drawWithShadow(g, font, ch, cursorX, y, color, shadow);
            cursorX += w;
        }
    }

    public static void beginScissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
        if (targetHeight != null) {
            enableTargetScissor(x1, y1, x2, y2);
        } else {
            g.enableScissor(x1, y1, x2, y2);
        }
    }

    public static void endScissor(GuiGraphics g) {
        if (targetHeight != null) {
            RenderSystem.disableScissor();
        } else {
            g.disableScissor();
        }
    }

    private static void enableTargetScissor(int x1, int y1, int x2, int y2) {
        double scale = targetScale;
        double px = x1 * scale;
        double py = targetHeight - y2 * scale;
        double pw = (x2 - x1) * scale;
        double ph = (y2 - y1) * scale;
        RenderSystem.enableScissor((int) px, (int) py, Math.max(0, (int) pw), Math.max(0, (int) ph));
    }
}
