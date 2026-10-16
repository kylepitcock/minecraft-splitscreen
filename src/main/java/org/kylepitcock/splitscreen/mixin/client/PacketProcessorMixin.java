package org.kylepitcock.splitscreen.mixin.client;

import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.neoforged.neoforge.network.handling.QueuedPacket;
import org.kylepitcock.splitscreen.client.PlayerContext;
import org.kylepitcock.splitscreen.client.SplitscreenManager;
import org.kylepitcock.splitscreen.client.SplitscreenOwned;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client packets are queued and handled on the main thread. Packets for a secondary player must run with that
 * player's state swapped into Minecraft, otherwise they would be applied to the main player's level and entities.
 */
@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
public abstract class PacketProcessorMixin {
    @Shadow
    private PacketListener listener;
    @Shadow
    private Packet<?> packet;

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void splitscreen$route(CallbackInfo ci) {
        if (!(listener instanceof SplitscreenOwned owned)) return;
        PlayerContext ctx = owned.splitscreen$getContext();
        if (ctx == null || SplitscreenManager.isLoaded(ctx)) return;
        ci.cancel();
        SplitscreenManager.run(ctx, () -> ((QueuedPacket) this).handle());
    }
}
