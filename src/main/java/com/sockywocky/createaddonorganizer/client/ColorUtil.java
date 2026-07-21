package com.sockywocky.createaddonorganizer.client;

public final class ColorUtil {
    private ColorUtil() {}

    public static int hsvToRgb(float h, float s, float v) {
        int i = (int) Math.floor(h * 6) % 6;
        if (i < 0) {
            i += 6;
        }
        float f = h * 6 - (float) Math.floor(h * 6);
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);
        float r;
        float g;
        float b;
        switch (i) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return (channel(r) << 16) | (channel(g) << 8) | channel(b);
    }

    public static float[] rgbToHsv(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;

        float h;
        if (d == 0) {
            h = 0;
        } else if (max == r) {
            h = (((g - b) / d) % 6) / 6f;
        } else if (max == g) {
            h = (((b - r) / d) + 2) / 6f;
        } else {
            h = (((r - g) / d) + 4) / 6f;
        }
        if (h < 0) {
            h += 1f;
        }
        float s = max == 0 ? 0 : d / max;
        return new float[] {h, s, max};
    }

    public static int lerpArgb(float delta, int from, int to) {
        float d = Math.max(0f, Math.min(1f, delta));
        int a = lerpChannel(d, (from >> 24) & 0xFF, (to >> 24) & 0xFF);
        int r = lerpChannel(d, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = lerpChannel(d, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = lerpChannel(d, from & 0xFF, to & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpChannel(float delta, int from, int to) {
        return Math.round(from + (to - from) * delta);
    }

    public static int brighten(int argb, float factor) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r = (int) (r + (255 - r) * factor);
        g = (int) (g + (255 - g) * factor);
        b = (int) (b + (255 - b) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel(float f) {
        return Math.max(0, Math.min(255, Math.round(f * 255)));
    }
}
