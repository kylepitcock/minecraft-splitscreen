package org.kylepitcock.splitscreen.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import org.kylepitcock.splitscreen.client.PlayerContext;
import org.kylepitcock.splitscreen.client.SplitscreenManager;
import org.kylepitcock.splitscreen.client.SplitscreenOwned;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin implements SplitscreenOwned {
    @Unique
    private PlayerContext splitscreen$context;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void splitscreen$tag(Minecraft minecraft, Connection connection, CommonListenerCookie cookie, CallbackInfo ci) {
        this.splitscreen$context = SplitscreenManager.contextForConnection(connection);
    }

    @Override
    public PlayerContext splitscreen$getContext() {
        return splitscreen$context;
    }

    /** A dropped secondary connection must never tear down the whole game. */
    @Inject(method = "onDisconnect", at = @At("HEAD"), cancellable = true)
    private void splitscreen$onDisconnect(DisconnectionDetails details, CallbackInfo ci) {
        if (splitscreen$context != null) {
            ci.cancel();
            SplitscreenManager.connectionLost(splitscreen$context, details.reason());
        }
    }
}
