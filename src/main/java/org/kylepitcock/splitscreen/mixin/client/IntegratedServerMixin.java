package org.kylepitcock.splitscreen.mixin.client;

import net.minecraft.client.server.IntegratedServer;
import org.kylepitcock.splitscreen.client.SplitscreenManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
    /** The singleplayer owner must stay the main player when a secondary player logs in. */
    @Inject(method = "setUUID", at = @At("HEAD"), cancellable = true)
    private void splitscreen$keepOwner(UUID uuid, CallbackInfo ci) {
        if (SplitscreenManager.inSecondaryScope()) ci.cancel();
    }
}
