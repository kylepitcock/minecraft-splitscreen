package org.kylepitcock.splitscreen.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.kylepitcock.splitscreen.client.SplitscreenManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Invoker("onPress")
    abstract void splitscreen$callOnPress(long window, int button, int action, int mods);

    @Invoker("onScroll")
    abstract void splitscreen$callOnScroll(long window, double x, double y);

    @Invoker("onMove")
    abstract void splitscreen$callOnMove(long window, double x, double y);

    // GLFW mouse callbacks arrive with no player scoped. Re-run them as the main player so the click
    // is interpreted against the main player's viewport size and GUI scale.
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void splitscreen$press(long window, int button, int action, int mods, CallbackInfo ci) {
        if (SplitscreenManager.shouldScopeMainInput()) {
            ci.cancel();
            SplitscreenManager.runMain(() -> splitscreen$callOnPress(window, button, action, mods));
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void splitscreen$scroll(long window, double x, double y, CallbackInfo ci) {
        if (SplitscreenManager.shouldScopeMainInput()) {
            ci.cancel();
            SplitscreenManager.runMain(() -> splitscreen$callOnScroll(window, x, y));
        }
    }

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void splitscreen$move(long window, double x, double y, CallbackInfo ci) {
        if (SplitscreenManager.shouldScopeMainInput()) {
            ci.cancel();
            SplitscreenManager.runMain(() -> splitscreen$callOnMove(window, x, y));
        }
    }

    // Secondary players use a virtual cursor and never touch the real mouse.
    @Inject(method = "xpos", at = @At("HEAD"), cancellable = true)
    private void splitscreen$xpos(CallbackInfoReturnable<Double> cir) {
        if (SplitscreenManager.inSecondaryScope()) {
            var w = minecraft.getWindow();
            cir.setReturnValue(SplitscreenManager.virtualCursorX() * w.getScreenWidth() / (double) w.getGuiScaledWidth());
        }
    }

    @Inject(method = "ypos", at = @At("HEAD"), cancellable = true)
    private void splitscreen$ypos(CallbackInfoReturnable<Double> cir) {
        if (SplitscreenManager.inSecondaryScope()) {
            var w = minecraft.getWindow();
            cir.setReturnValue(SplitscreenManager.virtualCursorY() * w.getScreenHeight() / (double) w.getGuiScaledHeight());
        }
    }

    @Inject(method = "isMouseGrabbed", at = @At("HEAD"), cancellable = true)
    private void splitscreen$grabbed(CallbackInfoReturnable<Boolean> cir) {
        if (SplitscreenManager.inSecondaryScope()) {
            cir.setReturnValue(minecraft.screen == null && minecraft.player != null);
        }
    }

    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void splitscreen$grab(CallbackInfo ci) {
        if (SplitscreenManager.inSecondaryScope()) ci.cancel();
    }

    @Inject(method = "releaseMouse", at = @At("HEAD"), cancellable = true)
    private void splitscreen$release(CallbackInfo ci) {
        if (SplitscreenManager.inSecondaryScope()) ci.cancel();
    }
}
