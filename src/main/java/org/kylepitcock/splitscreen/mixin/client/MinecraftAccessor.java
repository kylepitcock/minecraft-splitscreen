package org.kylepitcock.splitscreen.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Mutable
    @Accessor("gui")
    void splitscreen$setGui(Gui gui);

    @Mutable
    @Accessor("levelRenderer")
    void splitscreen$setLevelRenderer(LevelRenderer renderer);

    @Mutable
    @Accessor("gameRenderer")
    void splitscreen$setGameRenderer(GameRenderer renderer);

    @Accessor("missTime")
    int splitscreen$getMissTime();

    @Accessor("missTime")
    void splitscreen$setMissTime(int value);

    @Accessor("rightClickDelay")
    int splitscreen$getRightClickDelay();

    @Accessor("rightClickDelay")
    void splitscreen$setRightClickDelay(int value);

    @Invoker("handleKeybinds")
    void splitscreen$handleKeybinds();
}
