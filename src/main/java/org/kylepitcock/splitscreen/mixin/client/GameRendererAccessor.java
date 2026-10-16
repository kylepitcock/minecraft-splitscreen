package org.kylepitcock.splitscreen.mixin.client;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("shaders")
    Map<String, ShaderInstance> splitscreen$getShaders();
}
