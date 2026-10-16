package org.kylepitcock.splitscreen;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SplitscreenConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_PLAYERS = BUILDER
            .comment("Maximum number of local players, including the main player.")
            .defineInRange("maxPlayers", 4, 2, 4);

    public static final ModConfigSpec.BooleanValue VERTICAL_SPLIT = BUILDER
            .comment("With two players: true = side by side, false = top/bottom (console default).")
            .define("verticalSplit", false);

    public static final ModConfigSpec.BooleanValue JOIN_WITH_START = BUILDER
            .comment("Let an unassigned controller join by pressing Start while in a singleplayer world.")
            .define("joinWithStart", true);

    public static final ModConfigSpec.DoubleValue LOOK_SPEED = BUILDER
            .comment("Camera turn speed for secondary players at full stick deflection, in degrees per second.")
            .defineInRange("lookSpeed", 160.0, 30.0, 720.0);

    public static final ModConfigSpec.DoubleValue DEADZONE = BUILDER
            .comment("Analog stick dead zone for secondary players.")
            .defineInRange("deadzone", 0.2, 0.0, 0.9);

    public static final ModConfigSpec.BooleanValue INVERT_Y = BUILDER
            .comment("Invert the look axis for secondary players.")
            .define("invertLookY", false);

    public static final ModConfigSpec.IntValue MIN_GUI_WIDTH = BUILDER
            .comment("Smallest GUI width, in GUI units, a split view may be scaled down to. Lower = bigger UI.")
            .defineInRange("minGuiWidth", 400, 200, 1000);

    public static final ModConfigSpec.IntValue MIN_GUI_HEIGHT = BUILDER
            .comment("Smallest GUI height, in GUI units, a split view may be scaled down to. Lower = bigger UI.")
            .defineInRange("minGuiHeight", 225, 120, 600);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private SplitscreenConfig() {}
}
