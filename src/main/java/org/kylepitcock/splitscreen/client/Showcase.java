package org.kylepitcock.splitscreen.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.kylepitcock.splitscreen.Splitscreen;
import org.kylepitcock.splitscreen.SplitscreenConfig;

import java.util.List;

/**
 * -Pshowcase: drives a scripted tour through the different layouts and saves a screenshot at each stage, for
 * store pages and documentation. Dev tooling only, enabled by the splitscreen.showcase system property.
 */
public final class Showcase {
    public static final boolean ENABLED = Boolean.getBoolean("splitscreen.showcase");

    private static int ticks;
    /** Key used for the simulated main-player keypress: -Dsplitscreen.showcaseKey=E to try the inventory key. */
    private static final int KEY = Integer.getInteger("splitscreen.showcaseKey", org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);

    private Showcase() {}

    /** Called once per client tick while a world is open. */
    public static void tick(Minecraft mc) {
        ticks++;
        mc.options.pauseOnLostFocus = false;
        // Keep the main player's view clear of stray screens so every shot shows gameplay.
        if (ticks < 1040 && mc.screen != null && !SplitscreenManager.inSecondaryScope()) mc.setScreen(null);
        if (ticks == 2) disableLegacyHints();
        if (ticks >= 1040 && ticks <= 1100 && ticks % 10 == 0) {
            Splitscreen.LOGGER.info("[showcase] tick {} main screen = {}", ticks, mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
        }
        switch (ticks) {
            case 40 -> serverCommands(mc, "time set day", "weather clear", "gamemode creative @a");
            case 140 -> JoinPrompt.showForTest();
            case 180 -> shot(mc, "1-join-prompt");
            case 182 -> JoinPrompt.clearTest();
            case 190 -> ClientEvents.addTestPlayer(mc);
            case 400 -> shot(mc, "2-two-players-horizontal");
            case 410 -> setVertical(true);
            case 500 -> shot(mc, "3-two-players-vertical");
            case 510 -> {
                setVertical(false);
                ClientEvents.addTestPlayer(mc);
            }
            case 720 -> shot(mc, "4-three-players");
            case 730 -> ClientEvents.addTestPlayer(mc);
            case 940 -> shot(mc, "5-four-players");
            case 950 -> {
                List<PlayerContext> players = SplitscreenManager.players();
                if (players.size() >= 3) {
                    SplitscreenManager.run(players.get(1), () -> mc.setScreen(new InventoryScreen(mc.player)));
                    SplitscreenManager.run(players.get(2), () -> mc.setScreen(new PauseScreen(true)));
                }
            }
            case 1030 -> shot(mc, "6-four-players-menus");
            case 1040 -> keyEvent(mc, 1, KEY);
            case 1042 -> keyEvent(mc, 0, KEY);
            case 1100 -> shot(mc, "7-four-players-main-pause");
            case 1130 -> {
                Splitscreen.LOGGER.info("[showcase] done, stopping");
                mc.stop();
            }
            default -> { }
        }
    }

    /** Legacy4J pops up an item description whenever you look at something; it hides too much of a small view. */
    private static void disableLegacyHints() {
        try {
            Object option = Class.forName("wily.legacy.client.LegacyOptions").getField("hints").get(null);
            for (var m : option.getClass().getMethods()) {
                if (m.getName().equals("set") && m.getParameterCount() == 1) {
                    m.invoke(option, false);
                    return;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            Splitscreen.LOGGER.warn("[showcase] could not turn off Legacy4J hints", e);
        }
    }

    /** Feeds a real Esc key event through the keyboard handler, the way GLFW would (action 1 = press, 0 = release). */
    private static void keyEvent(Minecraft mc, int action, int key) {
        try {
            var method = net.minecraft.client.KeyboardHandler.class.getDeclaredMethod("keyPress", long.class, int.class,
                    net.minecraft.client.input.KeyEvent.class);
            method.setAccessible(true);
            method.invoke(mc.keyboardHandler, mc.getWindow().handle(), action,
                    new net.minecraft.client.input.KeyEvent(key, 1, 0));
        } catch (ReflectiveOperationException e) {
            Splitscreen.LOGGER.warn("[showcase] could not send a key event", e);
        }
    }

    private static void setVertical(boolean vertical) {
        SplitscreenConfig.VERTICAL_SPLIT.set(vertical);
        SplitscreenManager.markLayoutDirty();
    }

    private static void serverCommands(Minecraft mc, String... commands) {
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            for (String command : commands) {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
            }
        });
    }

    private static void shot(Minecraft mc, String name) {
        Splitscreen.LOGGER.info("[showcase] screenshot {}", name);
        net.minecraft.client.Screenshot.grab(mc.gameDirectory, "showcase-" + name + ".png", mc.getMainRenderTarget(), 1, m -> {});
    }
}
