package com.sockywocky.createaddonorganizer.client;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;
import com.sockywocky.createaddonorganizer.createaddonorganizer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

public final class BannerFill {
    private BannerFill() {}

    private static final int[][] BAYER_2X2 = {
            {0, 2},
            {3, 1},
    };

    private static final int[][] BAYER_4X4 = {
            {0, 8, 2, 10},
            {12, 4, 14, 6},
            {3, 11, 1, 9},
            {15, 7, 13, 5},
    };

    private static final int[][] BAYER_8X8 = {
            {0, 32, 8, 40, 2, 34, 10, 42},
            {48, 16, 56, 24, 50, 18, 58, 26},
            {12, 44, 4, 36, 14, 46, 6, 38},
            {60, 28, 52, 20, 62, 30, 54, 22},
            {3, 35, 11, 43, 1, 33, 9, 41},
            {51, 19, 59, 27, 49, 17, 57, 25},
            {15, 47, 7, 39, 13, 45, 5, 37},
            {63, 31, 55, 23, 61, 29, 53, 21},
    };

    private static final int CACHE_LIMIT = 64;
    private static final Map<String, CachedTexture> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CachedTexture> eldest) {
            if (size() > CACHE_LIMIT) {
                eldest.getValue().release();
                return true;
            }
            return false;
        }
    };

    public static void draw(GuiGraphics g, int x1, int y1, int x2, int y2, ColorSpec spec) {
        int w = x2 - x1;
        int h = y2 - y1;
        if (w <= 0 || h <= 0) {
            return;
        }
        if (!spec.isGradient()) {
            g.fill(x1, y1, x2, y2, spec.color1());
            return;
        }
        if (spec.style() == ColorSpec.Style.SMOOTH && spec.direction() == ColorSpec.Direction.VERTICAL) {
            g.fillGradient(x1, y1, x2, y2, spec.color1(), spec.color2());
            return;
        }
        ResourceLocation tex = textureFor(spec, w, h);
        g.blit(tex, x1, y1, 0f, 0f, w, h, w, h);
    }

    private static ResourceLocation textureFor(ColorSpec spec, int w, int h) {
        String key = spec.color1() + ":" + spec.color2() + ":" + spec.direction() + ":" + spec.style() + ":" + w + "x" + h;
        CachedTexture cached = CACHE.get(key);
        if (cached != null) {
            return cached.id;
        }

        NativeImage image = new NativeImage(w, h, false);
        int[][] matrix = bayerMatrixFor(spec.style());
        boolean tricolor = spec.style() == ColorSpec.Style.DITHER_TRICOLOR;
        int color2 = spec.color2();
        int midColor = tricolor ? ColorUtil.lerpArgb(0.5f, spec.color1(), color2) : 0;
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                float frac = fraction(spec.direction(), px, py, w, h);
                int argb;
                if (matrix != null) {
                    int n = matrix.length;
                    float threshold = (matrix[py % n][px % n] + 0.5f) / (n * n);
                    if (tricolor) {
                        boolean lowerHalf = frac < 0.5f;
                        int fromColor = lowerHalf ? spec.color1() : midColor;
                        int toColor = lowerHalf ? midColor : color2;
                        float localFrac = lowerHalf ? frac * 2f : (frac - 0.5f) * 2f;
                        argb = localFrac > threshold ? toColor : fromColor;
                    } else {
                        argb = frac > threshold ? color2 : spec.color1();
                    }
                } else {
                    argb = ColorUtil.lerpArgb(frac, spec.color1(), color2);
                }
                image.setPixelRGBA(px, py, FastColor.ABGR32.fromArgb32(argb));
            }
        }

        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(createaddonorganizer.MODID,
                "banner_fill_cache/" + Integer.toHexString(key.hashCode()).toLowerCase(Locale.ROOT));
        DynamicTexture texture = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        CACHE.put(key, new CachedTexture(id, texture));
        return id;
    }

    private static int[][] bayerMatrixFor(ColorSpec.Style style) {
        return switch (style) {
            case SMOOTH -> null;
            case DITHER_2X2 -> BAYER_2X2;
            case DITHER_4X4, DITHER_TRICOLOR -> BAYER_4X4;
            case DITHER_8X8 -> BAYER_8X8;
        };
    }

    private static float fraction(ColorSpec.Direction direction, int px, int py, int w, int h) {
        float fx = w <= 1 ? 0f : (float) px / (w - 1);
        float fy = h <= 1 ? 0f : (float) py / (h - 1);
        return switch (direction) {
            case HORIZONTAL -> fx;
            case VERTICAL -> fy;
            case DIAGONAL_DOWN -> (fx + fy) / 2f;
            case DIAGONAL_UP -> (fx + (1f - fy)) / 2f;
        };
    }

    private static final class CachedTexture {
        final ResourceLocation id;
        final DynamicTexture texture;

        CachedTexture(ResourceLocation id, DynamicTexture texture) {
            this.id = id;
            this.texture = texture;
        }

        void release() {
            Minecraft.getInstance().getTextureManager().release(id);
        }
    }
}
