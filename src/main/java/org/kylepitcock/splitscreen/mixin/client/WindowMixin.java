package org.kylepitcock.splitscreen.mixin.client;

import com.mojang.blaze3d.platform.Window;
import org.kylepitcock.splitscreen.client.SplitscreenManager;
import org.kylepitcock.splitscreen.client.Viewport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While a player's view is being ticked or rendered, the window reports that player's viewport size
 * so projection, GUI scaling and screen layout all work unchanged.
 */
@Mixin(Window.class)
public abstract class WindowMixin {
    @Shadow
    private int width;
    @Shadow
    private int height;
    @Shadow
    private int framebufferWidth;
    @Shadow
    private int framebufferHeight;

    @Inject(method = "getWidth", at = @At("HEAD"), cancellable = true)
    private void splitscreen$width(CallbackInfoReturnable<Integer> cir) {
        Viewport v = SplitscreenManager.scopedViewport();
        if (v != null) cir.setReturnValue(v.width());
    }

    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
    private void splitscreen$height(CallbackInfoReturnable<Integer> cir) {
        Viewport v = SplitscreenManager.scopedViewport();
        if (v != null) cir.setReturnValue(v.height());
    }

    @Inject(method = "getScreenWidth", at = @At("HEAD"), cancellable = true)
    private void splitscreen$screenWidth(CallbackInfoReturnable<Integer> cir) {
        Viewport v = SplitscreenManager.scopedViewport();
        if (v != null && framebufferWidth > 0) {
            cir.setReturnValue(Math.max(1, (int) Math.round(v.width() * (double) width / framebufferWidth)));
        }
    }

    @Inject(method = "getScreenHeight", at = @At("HEAD"), cancellable = true)
    private void splitscreen$screenHeight(CallbackInfoReturnable<Integer> cir) {
        Viewport v = SplitscreenManager.scopedViewport();
        if (v != null && framebufferHeight > 0) {
            cir.setReturnValue(Math.max(1, (int) Math.round(v.height() * (double) height / framebufferHeight)));
        }
    }

    @Inject(method = "getGuiScaledWidth", at = @At("HEAD"), cancellable = true)
    private void splitscreen$guiWidth(CallbackInfoReturnable<Integer> cir) {
        Viewport v = SplitscreenManager.scopedViewport();
        if (v != null) cir.setReturnValue(v.guiWidth());
    }

    @Inject(method = "getGuiScaledHeight", at = @At("HEAD"), cancellable = true)
    private void splitscreen$guiHeight(CallbackInfoReturnable<Integer> cir) {
        Viewport v = SplitscreenManager.scopedViewport();
        if (v != null) cir.setReturnValue(v.guiHeight());
    }

    @Inject(method = "getGuiScale", at = @At("HEAD"), cancellable = true)
    private void splitscreen$guiScale(CallbackInfoReturnable<Double> cir) {
        Viewport v = SplitscreenManager.scopedViewport();
        if (v != null) cir.setReturnValue((double) v.guiScale());
    }
}
