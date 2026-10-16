package org.kylepitcock.splitscreen.client;

import net.minecraft.client.Minecraft;

/** Console-style viewport arrangements. Index 0 is always the main player. */
public final class ViewportLayout {
    private static final int GAP = 2;

    private ViewportLayout() {}

    public static Viewport[] compute(int count, int w, int h, boolean verticalSplit, Minecraft mc) {
        int opt = mc.options.guiScale().get();
        boolean uni = mc.isEnforceUnicode();
        Viewport[] out = new Viewport[count];
        switch (count) {
            case 1 -> out[0] = Viewport.of(0, 0, w, h, opt, uni);
            case 2 -> {
                if (verticalSplit) {
                    int lw = (w - GAP) / 2;
                    out[0] = Viewport.of(0, 0, lw, h, opt, uni);
                    out[1] = Viewport.of(lw + GAP, 0, w - lw - GAP, h, opt, uni);
                } else {
                    int th = (h - GAP) / 2;
                    out[0] = Viewport.of(0, 0, w, th, opt, uni);
                    out[1] = Viewport.of(0, th + GAP, w, h - th - GAP, opt, uni);
                }
            }
            case 3 -> {
                int th = (h - GAP) / 2;
                int bh = h - th - GAP;
                int lw = (w - GAP) / 2;
                out[0] = Viewport.of(0, 0, w, th, opt, uni);
                out[1] = Viewport.of(0, th + GAP, lw, bh, opt, uni);
                out[2] = Viewport.of(lw + GAP, th + GAP, w - lw - GAP, bh, opt, uni);
            }
            default -> {
                int th = (h - GAP) / 2;
                int bh = h - th - GAP;
                int lw = (w - GAP) / 2;
                int rw = w - lw - GAP;
                out[0] = Viewport.of(0, 0, lw, th, opt, uni);
                out[1] = Viewport.of(lw + GAP, 0, rw, th, opt, uni);
                out[2] = Viewport.of(0, th + GAP, lw, bh, opt, uni);
                out[3] = Viewport.of(lw + GAP, th + GAP, rw, bh, opt, uni);
            }
        }
        return out;
    }
}
