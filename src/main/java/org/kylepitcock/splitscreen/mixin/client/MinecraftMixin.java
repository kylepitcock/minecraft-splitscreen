package org.kylepitcock.splitscreen.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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

    @Redirect(method = "runTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V"))
    private void splitscreen$render(GameRenderer renderer, DeltaTracker tracker, boolean renderLevel) {
        if (SplitscreenManager.shouldSplitRender()) {
            SplitscreenManager.renderAll((Minecraft) (Object) this, tracker, renderLevel);
        } else {
            renderer.render(tracker, renderLevel);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void splitscreen$tick(CallbackInfo ci) {
        SplitscreenManager.tickSecondaries((Minecraft) (Object) this);
    }

    @Inject(method = "resizeDisplay", at = @At("TAIL"))
    private void splitscreen$resized(CallbackInfo ci) {
        SplitscreenManager.onWindowResized();
    }

    /** Skip the nested "forced tick" (which renders and swaps buffers) while a secondary player loads. */
    @Inject(method = "updateScreenAndTick", at = @At("HEAD"), cancellable = true)
    private void splitscreen$noForcedTick(Screen screen, CallbackInfo ci) {
        if (SplitscreenManager.inSecondaryScope()) {
            Minecraft mc = (Minecraft) (Object) this;
            mc.cameraEntity = null;
            mc.setScreen(screen);
            ci.cancel();
        }
    }
}
