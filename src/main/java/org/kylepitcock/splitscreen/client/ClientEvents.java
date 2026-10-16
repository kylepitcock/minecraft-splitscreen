package org.kylepitcock.splitscreen.client;

import com.mojang.brigadier.Command;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.kylepitcock.splitscreen.Splitscreen;
import org.kylepitcock.splitscreen.SplitscreenConfig;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

@EventBusSubscriber(modid = Splitscreen.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static final boolean[] START_BEFORE = new boolean[GLFW.GLFW_JOYSTICK_LAST + 1];

    public static final KeyMapping ADD_TEST_PLAYER = new KeyMapping("key.splitscreen.add_test_player",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, "key.categories.splitscreen");
    public static final KeyMapping REMOVE_TEST_PLAYER = new KeyMapping("key.splitscreen.remove_test_player",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F10, "key.categories.splitscreen");

    private static final int AUTOTEST = Integer.getInteger("splitscreen.autotest", 0);
    private static int autotestTicks;

    private ClientEvents() {}

    @EventBusSubscriber(modid = Splitscreen.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(ADD_TEST_PLAYER);
            event.register(REMOVE_TEST_PLAYER);
        }

        @SubscribeEvent
        public static void onReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) rm -> SplitscreenManager.reloadRenderers(Minecraft.getInstance()));
        }
    }

    /** Press Start on a free controller to join, like starting a second player on a console. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getSingleplayerServer() == null || SplitscreenManager.inSecondaryScope()) return;
        while (ADD_TEST_PLAYER.consumeClick()) addTestPlayer(mc);
        while (REMOVE_TEST_PLAYER.consumeClick()) removeLastPlayer();
        if (AUTOTEST > 0) autotest(mc);
        if (!SplitscreenConfig.JOIN_WITH_START.get() || mc.screen != null) {
            java.util.Arrays.fill(START_BEFORE, false);
            return;
        }
        int reserved = Legacy4JCompat.mainPlayerJid();
        GLFWGamepadState state = GLFWGamepadState.calloc();
        try {
            for (int jid = 0; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
                boolean pressed = false;
                if (jid != reserved && !isAssigned(jid) && GLFW.glfwJoystickIsGamepad(jid) && GLFW.glfwGetGamepadState(jid, state)) {
                    pressed = state.buttons(GLFW.GLFW_GAMEPAD_BUTTON_START) == GLFW.GLFW_PRESS;
                }
                boolean edge = pressed && !START_BEFORE[jid];
                START_BEFORE[jid] = pressed;
                if (edge) {
                    String err = SplitscreenManager.join(mc, new Gamepad(jid));
                    if (err != null && mc.player != null) mc.player.displayClientMessage(Component.translatable(err), false);
                    break;
                }
            }
        } finally {
            state.free();
        }
    }

    /** Adds a scripted player that needs no controller. */
    public static void addTestPlayer(Minecraft mc) {
        String err = SplitscreenManager.join(mc, new Gamepad(-1, true));
        if (err != null && mc.player != null) mc.player.displayClientMessage(Component.translatable(err), false);
    }

    public static void removeLastPlayer() {
        var players = SplitscreenManager.players();
        if (players.size() > 1) SplitscreenManager.requestLeave(players.get(players.size() - 1));
    }

    /** -Pautotest=N: after the world loads, add N-1 scripted players one by one and log what happens. */
    private static void autotest(Minecraft mc) {
        autotestTicks++;
        mc.options.pauseOnLostFocus = false;
        if (autotestTicks > 100 && autotestTicks % 100 == 0 && SplitscreenManager.playerCount() < AUTOTEST) {
            Splitscreen.LOGGER.info("[autotest] adding test player (now {})", SplitscreenManager.playerCount());
            addTestPlayer(mc);
        }
        if (autotestTicks % 100 == 0) {
            StringBuilder sb = new StringBuilder("[autotest] tick " + autotestTicks + ":");
            for (PlayerContext c : SplitscreenManager.players()) {
                sb.append(' ').append(c.name()).append(c.isDead() ? "(dead)" : "").append(c.statusSummary());
            }
            Splitscreen.LOGGER.info(sb.toString());
        }
        if (autotestTicks == 100 + 100 * (AUTOTEST + 2)) {
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, "autotest.png", mc.getMainRenderTarget(), m -> {});
        }
        if (autotestTicks == 100 + 100 * (AUTOTEST + 3)) {
            Splitscreen.LOGGER.info("[autotest] done, stopping");
            mc.stop();
        }
    }

    private static boolean isAssigned(int jid) {
        for (PlayerContext c : SplitscreenManager.players()) {
            if (c.pad() != null && c.pad().jid() == jid) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // Only the main player's disconnect matters; secondary connections are cleaned up elsewhere.
        if (!SplitscreenManager.inSecondaryScope()) {
            SplitscreenManager.shutdown(Minecraft.getInstance());
        }
    }

    /** Draws the virtual mouse pointer for secondary players that have a screen open. */
    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!SplitscreenManager.inSecondaryScope()) return;
        GuiGraphics g = event.getGuiGraphics();
        int x = (int) SplitscreenManager.virtualCursorX();
        int y = (int) SplitscreenManager.virtualCursorY();
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(x - 4, y - 1, x + 5, y + 2, 0xFF000000);
        g.fill(x - 1, y - 4, x + 2, y + 5, 0xFF000000);
        g.fill(x - 3, y, x + 4, y + 1, 0xFFFFFFFF);
        g.fill(x, y - 3, x + 1, y + 4, 0xFFFFFFFF);
        g.pose().popPose();
    }

    @SubscribeEvent
    public static void onCommands(RegisterClientCommandsEvent event) {
        var d = event.getDispatcher();
        d.register(Commands.literal("splitscreen")
                .then(Commands.literal("join")
                        .executes(c -> join(c.getSource(), false))
                        .then(Commands.literal("dummy").executes(c -> join(c.getSource(), true))))
                .then(Commands.literal("leave").executes(c -> {
                    var players = SplitscreenManager.players();
                    if (players.size() > 1) SplitscreenManager.requestLeave(players.get(players.size() - 1));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("layout")
                        .then(Commands.literal("horizontal").executes(c -> layout(false)))
                        .then(Commands.literal("vertical").executes(c -> layout(true)))));
    }

    private static int layout(boolean vertical) {
        SplitscreenConfig.VERTICAL_SPLIT.set(vertical);
        SplitscreenManager.markLayoutDirty();
        return Command.SINGLE_SUCCESS;
    }

    private static int join(net.minecraft.commands.CommandSourceStack source, boolean dummy) {
        Minecraft mc = Minecraft.getInstance();
        Gamepad pad = null;
        if (dummy) {
            pad = new Gamepad(-1, true);
        } else {
            int reserved = Legacy4JCompat.mainPlayerJid();
            for (int jid = 0; jid <= GLFW.GLFW_JOYSTICK_LAST && pad == null; jid++) {
                if (jid != reserved && !isAssigned(jid) && GLFW.glfwJoystickIsGamepad(jid)) pad = new Gamepad(jid);
            }
        }
        if (pad == null) {
            source.sendFailure(Component.translatable("splitscreen.no_pad"));
            return 0;
        }
        String err = SplitscreenManager.join(mc, pad);
        if (err != null) {
            pad.close();
            source.sendFailure(Component.translatable(err));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }
}
