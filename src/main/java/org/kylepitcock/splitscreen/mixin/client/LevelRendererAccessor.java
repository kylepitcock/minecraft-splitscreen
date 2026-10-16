package org.kylepitcock.splitscreen.mixin.client;

import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    @Accessor("cloudRenderer")
    CloudRenderer splitscreen$getCloudRenderer();

    @Mutable
    @Accessor("cloudRenderer")
    void splitscreen$setCloudRenderer(CloudRenderer renderer);
}
