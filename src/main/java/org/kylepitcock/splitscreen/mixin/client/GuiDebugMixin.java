package org.kylepitcock.splitscreen.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.kylepitcock.splitscreen.Splitscreen;
import org.kylepitcock.splitscreen.client.PlayerContext;
import org.kylepitcock.splitscreen.client.SplitscreenManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Diagnostics only (-Dsplitscreen.debugScale=true): what transform is active when the hotbar draws. */
@Mixin(value = Gui.class, priority = 5000)
public abstract class GuiDebugMixin {
    @Inject(method = "renderItemHotbar", at = @At("HEAD"))
    private void splitscreen$debugHotbar(GuiGraphics g, DeltaTracker dt, CallbackInfo ci) {
        if (!Boolean.getBoolean("splitscreen.debugScale")) return;
        PlayerContext c = SplitscreenManager.scopedContext();
        var m = g.pose().last().pose();
        Splitscreen.LOGGER.info("[hotbar] {} guiW={} guiH={} scaleX={} transY={} depth={}", c == null ? "none" : c.name(),
                g.guiWidth(), g.guiHeight(), m.m00(), m.m31(), 0);
    }
}
