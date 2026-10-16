package org.kylepitcock.splitscreen.client;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

/**
 * Thin wrapper over GLFW's gamepad API for one physical controller. A jid of -1 is a "dummy" pad
 * that never produces input (useful for testing layouts).
 */
public final class Gamepad {
    public static final int BUTTON_COUNT = GLFW.GLFW_GAMEPAD_BUTTON_LAST + 1;
    public static final int AXIS_COUNT = GLFW.GLFW_GAMEPAD_AXIS_LAST + 1;

    private final int jid;
    private final GLFWGamepadState state = GLFWGamepadState.calloc();
    private final boolean[] down = new boolean[BUTTON_COUNT];
    private final boolean[] frameBefore = new boolean[BUTTON_COUNT];
    private final boolean[] latched = new boolean[BUTTON_COUNT];
    private final boolean[] tickNow = new boolean[BUTTON_COUNT];
    private final boolean[] tickBefore = new boolean[BUTTON_COUNT];
    private final float[] axes = new float[AXIS_COUNT];
    private boolean freed;

    /** Dummy pads (jid -1) are driven by a simple script so test players visibly move without hardware. */
    private final long scriptStart = System.nanoTime();
    private final boolean scripted;

    public Gamepad(int jid) {
        this(jid, false);
    }

    public Gamepad(int jid, boolean scripted) {
        this.jid = jid;
        this.scripted = scripted;
    }

    public int jid() {
        return jid;
    }

    public boolean isDummy() {
        return jid < 0;
    }

    public boolean isConnected() {
        return isDummy() || GLFW.glfwJoystickIsGamepad(jid);
    }

    public String name() {
        if (isDummy()) return "dummy";
        String n = GLFW.glfwGetGamepadName(jid);
        return n == null ? "controller " + jid : n;
    }

    /** Called once per rendered frame. Remembers presses so short taps are not lost between ticks. */
    public void pollFrame() {
        System.arraycopy(down, 0, frameBefore, 0, BUTTON_COUNT);
        if (isDummy()) {
            java.util.Arrays.fill(down, false);
            java.util.Arrays.fill(axes, 0f);
            axes[GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER] = -1f;
            axes[GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER] = -1f;
            if (scripted) runScript();
            for (int i = 0; i < BUTTON_COUNT; i++) if (down[i]) latched[i] = true;
            return;
        }
        if (freed || !GLFW.glfwGetGamepadState(jid, state)) {
            java.util.Arrays.fill(down, false);
            java.util.Arrays.fill(axes, 0f);
            return;
        }
        for (int i = 0; i < BUTTON_COUNT; i++) {
            down[i] = state.buttons(i) == GLFW.GLFW_PRESS;
            if (down[i]) latched[i] = true;
        }
        for (int i = 0; i < AXIS_COUNT; i++) {
            axes[i] = state.axes(i);
        }
    }

    /** Walk forward, sweep the camera left and right, hop now and then, swing the arm. */
    private void runScript() {
        double t = (System.nanoTime() - scriptStart) / 1.0e9;
        boolean walking = (t % 8.0) < 5.0;
        axes[GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y] = walking ? -1f : 0f;
        axes[GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X] = (float) Math.sin(t * 0.8) * 0.7f;
        down[GLFW.GLFW_GAMEPAD_BUTTON_A] = (t % 3.0) < 0.25;
        if ((t % 4.0) < 0.3) axes[GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER] = 1f;
    }

    /** Called once per client tick, after pollFrame. */
    public void pollTick() {
        System.arraycopy(tickNow, 0, tickBefore, 0, BUTTON_COUNT);
        for (int i = 0; i < BUTTON_COUNT; i++) {
            tickNow[i] = down[i] || latched[i];
            latched[i] = false;
        }
    }

    public boolean held(int button) {
        return down[button];
    }

    public boolean pressedThisFrame(int button) {
        return down[button] && !frameBefore[button];
    }

    public boolean releasedThisFrame(int button) {
        return !down[button] && frameBefore[button];
    }

    public boolean tickHeld(int button) {
        return tickNow[button];
    }

    public boolean tickPressed(int button) {
        return tickNow[button] && !tickBefore[button];
    }

    /** Axis with the dead zone applied and the remaining range rescaled to 0..1. */
    public float axis(int axis, float deadzone) {
        float v = axes[axis];
        float a = Math.abs(v);
        if (a < deadzone) return 0f;
        return Math.signum(v) * Math.min(1f, (a - deadzone) / (1f - deadzone));
    }

    /** Triggers rest at -1 in GLFW; this maps them to 0..1. */
    public float trigger(int axis) {
        return Math.max(0f, Math.min(1f, (axes[axis] + 1f) * 0.5f));
    }

    public void close() {
        if (!freed) {
            freed = true;
            state.free();
        }
    }
}
