package org.kylepitcock.splitscreen.mixin.client;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("ALL")
    static Map<String, KeyMapping> splitscreen$getAll() {
        throw new AssertionError();
    }

    @Accessor("clickCount")
    int splitscreen$getClickCount();

    @Accessor("clickCount")
    void splitscreen$setClickCount(int count);

    @Accessor("isDown")
    boolean splitscreen$getIsDown();

    @Accessor("isDown")
    void splitscreen$setIsDown(boolean down);
}
