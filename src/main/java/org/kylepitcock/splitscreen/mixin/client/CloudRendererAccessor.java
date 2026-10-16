package org.kylepitcock.splitscreen.mixin.client;

import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;

/** Lets an extra view's own cloud renderer load its texture without being registered as a reload listener. */
@Mixin(CloudRenderer.class)
public interface CloudRendererAccessor {
    @Invoker("prepare")
    Optional<?> splitscreen$prepare(ResourceManager manager, ProfilerFiller profiler);

    @Invoker("apply")
    void splitscreen$apply(Optional<?> data, ResourceManager manager, ProfilerFiller profiler);
}
