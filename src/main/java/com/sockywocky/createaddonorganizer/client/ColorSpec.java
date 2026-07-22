package com.sockywocky.createaddonorganizer.client;

public record ColorSpec(int color1, Integer color2, Direction direction, Style style) {

    public enum Direction { VERTICAL, HORIZONTAL, DIAGONAL_UP, DIAGONAL_DOWN }

    public enum Style { SMOOTH, DITHER_2X2, DITHER_4X4, DITHER_TRICOLOR, DITHER_QUADCOLOR }

    public static ColorSpec solid(int argb) {
        return new ColorSpec(argb, null, Direction.VERTICAL, Style.SMOOTH);
    }

    public boolean isGradient() {
        return color2 != null;
    }
}
