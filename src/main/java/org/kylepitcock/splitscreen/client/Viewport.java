package org.kylepitcock.splitscreen.client;

/**
 * A player's rectangle inside the real window, in framebuffer pixels with a top-left origin,
 * plus the GUI scale that applies to it.
 */
public record Viewport(int x, int y, int width, int height, int guiScale, int guiWidth, int guiHeight) {

    public static Viewport of(int x, int y, int width, int height, int optionScale, boolean forceUnicode) {
        int scale = scaleFor(width, height, optionScale, forceUnicode);
        int gw = (int) Math.ceil(width / (double) scale);
        int gh = (int) Math.ceil(height / (double) scale);
        return new Viewport(x, y, width, height, scale, gw, gh);
    }

    /**
     * Same idea as Window#calculateScale, but each view must keep at least 640x360 GUI units. Legacy4J's
     * screens and HUD are laid out for that size, so anything smaller clips inventories and crowds the HUD.
     */
    static int scaleFor(int w, int h, int optionScale, boolean forceUnicode) {
        int i = 1;
        while (i != optionScale && i < w && i < h && w / (i + 1) >= 640 && h / (i + 1) >= 360) {
            i++;
        }
        if (forceUnicode && i % 2 != 0) {
            i++;
        }
        return i;
    }

    public boolean sameSize(Viewport o) {
        return o != null && o.width == width && o.height == height && o.guiScale == guiScale;
    }
}
