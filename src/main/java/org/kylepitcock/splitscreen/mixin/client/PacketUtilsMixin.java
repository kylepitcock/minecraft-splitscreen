package org.kylepitcock.splitscreen.mixin.client;

import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.util.thread.BlockableEventLoop;
import org.kylepitcock.splitscreen.client.PlayerContext;
import org.kylepitcock.splitscreen.client.SplitscreenManager;
import org.kylepitcock.splitscreen.client.SplitscreenOwned;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PacketUtils.class)
public abstract class PacketUtilsMixin {
    /**
     * Packets for a secondary player must run with that player's state swapped into Minecraft,
     * otherwise they would be applied to the main player's level and entities.
     */
    @Inject(
            method = "ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",
            at = @At("HEAD"), cancellable = true)
    private static <T extends PacketListener> void splitscreen$route(Packet<T> packet, T listener, BlockableEventLoop<?> executor, CallbackInfo ci) {
        if (!(listener instanceof SplitscreenOwned owned)) return;
        PlayerContext ctx = owned.splitscreen$getContext();
        if (ctx == null) return;
        if (executor.isSameThread() && SplitscreenManager.isLoaded(ctx)) return;

        executor.executeIfPossible(() -> {
            if (!listener.shouldHandleMessage(packet)) return;
            SplitscreenManager.run(ctx, () -> {
                try {
                    packet.handle(listener);
                } catch (Exception e) {
                    listener.onPacketError(packet, e);
                }
            });
        });
        throw RunningOnDifferentThreadException.RUNNING_ON_DIFFERENT_THREAD;
    }
}
