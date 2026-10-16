package org.kylepitcock.splitscreen.client;

import com.mojang.brigadier.Command;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.kylepitcock.splitscreen.Splitscreen;
import org.kylepitcock.splitscreen.SplitscreenConfig;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = Splitscreen.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(Splitscreen.MODID, "main"));
    public static final KeyMapping ADD_TEST_PLAYER = new KeyMapping("key.splitscreen.add_test_player",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, CATEGORY);
    public static final KeyMapping REMOVE_TEST_PLAYER = new KeyMapping("key.splitscreen.remove_test_player",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F10, CATEGORY);

    private static final int AUTOTEST = Integer.getInteger("splitscreen.autotest", 0);
    private static int autotestTicks;

    private ClientEvents() {}

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(ADD_TEST_PLAYER);
        event.register(REMOVE_TEST_PLAYER);
    }

    @SubscribeEvent
    public static void onGuiLayers(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath(Splitscreen.MODID, "join_prompt"), ClientEvents::renderJoinPrompt);
    }

    /** "Press START to join game", drawn over the main player's view while a free controller has been touched. */
    private static void renderJoinPrompt(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker delta) {
        if (!JoinPrompt.visible() || SplitscreenManager.inSecondaryScope()) return;
        Minecraft mc = Minecraft.getInstance();
        var font = mc.font;
        Component title = Component.translatable("splitscreen.press_start");
        String pad = JoinPrompt.controllerName();
        int width = Math.max(font.width(title), pad.isEmpty() ? 0 : font.width(pad)) + 28;
        int height = pad.isEmpty() ? 28 : 40;
        int x = (g.guiWidth() - width) / 2;
        // Lower-middle of the view, clear of the hotbar and control hints.
        int y = (int) (g.guiHeight() * 0.62) - height / 2;
        g.fill(x, y, x + width, y + height, 0xC0101010);
        g.outline(x, y, width, height, 0xFFE0E0E0);
        g.centeredText(font, title, g.guiWidth() / 2, y + 10, 0xFFFFFFFF);
        if (!pad.isEmpty()) g.centeredText(font, pad, g.guiWidth() / 2, y + 24, 0xFFA0A0A0);
    }

    @SubscribeEvent
    public static void onReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(Splitscreen.MODID, "renderers"),
                (ResourceManagerReloadListener) rm -> SplitscreenManager.reloadRenderers(Minecraft.getInstance()));
    }

    /** Press Start on a free controller to join, like starting a second player on a console. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (AUTOTEST > 0 && mc.level == null) createAutotestWorld(mc);
        if (mc.level == null || mc.getSingleplayerServer() == null || SplitscreenManager.inSecondaryScope()) return;
        while (ADD_TEST_PLAYER.consumeClick()) addTestPlayer(mc);
        while (REMOVE_TEST_PLAYER.consumeClick()) removeLastPlayer();
        if (AUTOTEST > 0) autotest(mc);
        int joinJid = JoinPrompt.poll(mc);
        if (joinJid >= 0) {
            String err = SplitscreenManager.join(mc, new Gamepad(joinJid));
            if (err != null && mc.player != null) mc.player.sendSystemMessage(Component.translatable(err));
        }
    }

    private static boolean autotestWorldRequested;
    private static int menuTicks;

    /** -Pautotest: build a fresh creative world from the title screen so the test never waits on a prompt. */
    private static void createAutotestWorld(Minecraft mc) {
        // Legacy4J swaps in its own main menu, so accept any idle menu screen once loading is done.
        if (autotestWorldRequested || mc.getOverlay() != null || mc.screen == null || ++menuTicks < 60) return;
        autotestWorldRequested = true;
        Splitscreen.LOGGER.info("[autotest] creating world from {}", mc.screen.getClass().getName());
        var settings = new net.minecraft.world.level.LevelSettings("autotest-" + System.currentTimeMillis() / 1000,
                net.minecraft.world.level.GameType.CREATIVE,
                new net.minecraft.world.level.LevelSettings.DifficultySettings(net.minecraft.world.Difficulty.PEACEFUL, false, false),
                true, net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(settings.levelName(), settings,
                new net.minecraft.world.level.levelgen.WorldOptions(8675309L, false, false),
                registries -> registries.lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                        .getOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.NORMAL).value().createWorldDimensions(),
                mc.screen);
    }

    /** Adds a scripted player that needs no controller. */
    public static void addTestPlayer(Minecraft mc) {
        String err = SplitscreenManager.join(mc, new Gamepad(-1, true));
        if (err != null && mc.player != null) mc.player.sendSystemMessage(Component.translatable(err));
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
        if (autotestTicks == 100 + 100 * (AUTOTEST + 1)) {
            JoinPrompt.showForTest();
            var ps = SplitscreenManager.players();
            if (ps.size() >= 3) {
                SplitscreenManager.run(ps.get(1), () -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
                SplitscreenManager.run(ps.get(2), () -> mc.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true)));
            }
        }
        if (autotestTicks == 100 + 100 * (AUTOTEST + 2)) {
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, "autotest.png", mc.getMainRenderTarget(), 1, m -> {});
            for (PlayerContext c : SplitscreenManager.players()) {
                Splitscreen.LOGGER.info("[autotest] {} viewport={} target={}x{}", c.name(), c.viewport(),
                        c.target() == null ? "none" : c.target().width, c.target() == null ? 0 : c.target().height);
            }
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
        GuiGraphicsExtractor g = event.getGuiGraphics();
        int x = (int) SplitscreenManager.virtualCursorX();
        int y = (int) SplitscreenManager.virtualCursorY();
        g.fill(x - 4, y - 1, x + 5, y + 2, 0xFF000000);
        g.fill(x - 1, y - 4, x + 2, y + 5, 0xFF000000);
        g.fill(x - 3, y, x + 4, y + 1, 0xFFFFFFFF);
        g.fill(x, y - 3, x + 1, y + 4, 0xFFFFFFFF);
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
