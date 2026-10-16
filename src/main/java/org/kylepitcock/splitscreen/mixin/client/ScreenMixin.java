package org.kylepitcock.splitscreen.mixin.client;

import net.minecraft.client.gui.screens.Screen;
import org.kylepitcock.splitscreen.client.SplitscreenManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    /** Secondary players have no keyboard; a controller button stands in for shift-click. */
    @Inject(method = "hasShiftDown", at = @At("HEAD"), cancellable = true)
    private static void splitscreen$shift(CallbackInfoReturnable<Boolean> cir) {
        if (SplitscreenManager.inSecondaryScope()) {
            cir.setReturnValue(SplitscreenManager.virtualShift());
        }
    }

    @Inject(method = "hasControlDown", at = @At("HEAD"), cancellable = true)
    private static void splitscreen$control(CallbackInfoReturnable<Boolean> cir) {
        if (SplitscreenManager.inSecondaryScope()) {
            cir.setReturnValue(false);
        }
    }
}
