package org.kylepitcock.splitscreen;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Console-style splitscreen inside a single Minecraft instance. Client only.
 */
@Mod(value = Splitscreen.MODID, dist = Dist.CLIENT)
public class Splitscreen {
    public static final String MODID = "splitscreen";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Splitscreen(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SplitscreenConfig.SPEC);
    }
}
