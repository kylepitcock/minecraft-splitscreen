package org.kylepitcock.splitscreen.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.kylepitcock.splitscreen.client.SplitscreenManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    /** While a player's view is scoped, everything renders into that player's own target. */
    @Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void splitscreen$target(CallbackInfoReturnable<RenderTarget> cir) {
        RenderTarget t = SplitscreenManager.scopedTarget();
        if (t != null) cir.setReturnValue(t);
    }

    // The frame is update -> pick -> extract -> render. With several players on screen each phase runs once per
    // player (with that player's state swapped in) and the render phase then composes all views into the window.
    @Redirect(method = "renderFrame",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;update(Lnet/minecraft/client/DeltaTracker;Z)V"))
    private void splitscreen$update(GameRenderer renderer, DeltaTracker tracker, boolean advance) {
        if (SplitscreenManager.shouldSplitRender()) {
            SplitscreenManager.updateAll((Minecraft) (Object) this, tracker, advance);
        } else {
            renderer.update(tracker, advance);
        }
    }

    @Redirect(method = "renderFrame",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;pick(F)V"))
    private void splitscreen$pick(Minecraft mc, float partial) {
        if (SplitscreenManager.shouldSplitRender()) {
            SplitscreenManager.pickAll(mc, partial);
        } else {
            ((MinecraftAccessor) mc).splitscreen$pick(partial);
        }
    }

    @Redirect(method = "renderFrame",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;extract(Lnet/minecraft/client/DeltaTracker;Z)V"))
    private void splitscreen$extract(GameRenderer renderer, DeltaTracker tracker, boolean advance) {
        if (SplitscreenManager.shouldSplitRender()) {
            SplitscreenManager.extractAll((Minecraft) (Object) this, tracker, advance);
        } else {
            renderer.extract(tracker, advance);
        }
    }

    @Redirect(method = "renderFrame",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V"))
    private void splitscreen$render(GameRenderer renderer, DeltaTracker tracker, boolean advance) {
        if (SplitscreenManager.shouldSplitRender()) {
            SplitscreenManager.renderAll((Minecraft) (Object) this, tracker, advance);
        } else {
            renderer.render(tracker, advance);
        }
    }

    /**
     * Screens the main player opens from keyboard handling (Esc, the inventory key, ...) are created outside any
     * player's scope, so they would be laid out for the whole window. Re-run the call inside the main player's
     * scope so the screen is built for their own view, like screens opened from mouse clicks already are.
     */
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void splitscreen$openInMainScope(net.minecraft.client.gui.screens.Screen screen, CallbackInfo ci) {
        if (SplitscreenManager.shouldScopeMainInput()) {
            ci.cancel();
            SplitscreenManager.runMain(() -> ((Minecraft) (Object) this).setScreen(screen));
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void splitscreen$tick(CallbackInfo ci) {
        SplitscreenManager.tickSecondaries((Minecraft) (Object) this);
    }

    @Inject(method = "resizeGui", at = @At("TAIL"))
    private void splitscreen$resized(CallbackInfo ci) {
        SplitscreenManager.onWindowResized();
    }
}
