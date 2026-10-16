package org.kylepitcock.splitscreen.client;

import net.minecraft.client.Minecraft;
import org.kylepitcock.splitscreen.SplitscreenConfig;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

/**
 * The "Press START to join game" prompt from the console editions: when a controller that isn't playing yet is
 * touched, a prompt appears; pressing Start on it adds that controller as the next local player.
 */
public final class JoinPrompt {
    private static final long VISIBLE_MS = 5000;
    private static final float STICK_THRESHOLD = 0.6f;

    private static final boolean[] START_BEFORE = new boolean[GLFW.GLFW_JOYSTICK_LAST + 1];
    private static int promptJid = -1;
    private static long lastActivityMs;
    private static boolean forcedForTest;

    private JoinPrompt() {}

    public static boolean visible() {
        return forcedForTest || (promptJid >= 0 && System.currentTimeMillis() - lastActivityMs < VISIBLE_MS);
    }

    /** Name of the controller the prompt is about, for display. */
    public static String controllerName() {
        if (promptJid >= 0 && GLFW.glfwJoystickIsGamepad(promptJid)) {
            String name = GLFW.glfwGetGamepadName(promptJid);
            if (name != null) return name;
        }
        return "";
    }

    /** Diagnostics: keep the prompt on screen so it can be screenshotted without hardware. */
    public static void showForTest() {
        forcedForTest = true;
    }

    public static void reset() {
        promptJid = -1;
        java.util.Arrays.fill(START_BEFORE, false);
    }

    /**
     * Polls every controller that isn't already a player. Shows the prompt when one is touched and returns its
     * joystick id when Start is pressed on it, otherwise -1. Must run on the main thread, once per client tick.
     */
    public static int poll(Minecraft mc) {
        if (!SplitscreenConfig.JOIN_WITH_START.get() || mc.screen != null
                || SplitscreenManager.playerCount() >= SplitscreenConfig.MAX_PLAYERS.get()) {
            reset();
            return -1;
        }
        int reserved = Legacy4JCompat.mainPlayerJid();
        int joinJid = -1;
        GLFWGamepadState state = GLFWGamepadState.calloc();
        try {
            for (int jid = 0; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
                boolean start = false;
                boolean active = false;
                if (jid != reserved && !isAssigned(jid) && GLFW.glfwJoystickIsGamepad(jid) && GLFW.glfwGetGamepadState(jid, state)) {
                    start = state.buttons(GLFW.GLFW_GAMEPAD_BUTTON_START) == GLFW.GLFW_PRESS;
                    active = start || anyInput(state);
                }
                if (active) {
                    promptJid = jid;
                    lastActivityMs = System.currentTimeMillis();
                }
                if (start && !START_BEFORE[jid] && joinJid < 0 && visibleFor(jid)) {
                    joinJid = jid;
                }
                START_BEFORE[jid] = start;
            }
        } finally {
            state.free();
        }
        if (joinJid >= 0) {
            promptJid = -1;
        }
        return joinJid;
    }

    /** Start only joins once the prompt is up, so a stray tap while the pad is idle never adds a player. */
    private static boolean visibleFor(int jid) {
        return promptJid == jid;
    }

    private static boolean anyInput(GLFWGamepadState state) {
        for (int b = 0; b <= GLFW.GLFW_GAMEPAD_BUTTON_LAST; b++) {
            if (state.buttons(b) == GLFW.GLFW_PRESS) return true;
        }
        for (int a = GLFW.GLFW_GAMEPAD_AXIS_LEFT_X; a <= GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y; a++) {
            if (Math.abs(state.axes(a)) > STICK_THRESHOLD) return true;
        }
        // Triggers rest at -1 and go to +1 when pulled.
        return state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER) > 0f || state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER) > 0f;
    }

    private static boolean isAssigned(int jid) {
        for (PlayerContext c : SplitscreenManager.players()) {
            if (c.pad() != null && c.pad().jid() == jid) return true;
        }
        return false;
    }
}
