package org.kylepitcock.splitscreen.client;

import net.minecraft.client.player.Input;
import org.kylepitcock.splitscreen.SplitscreenConfig;
import org.lwjgl.glfw.GLFW;

/** Movement input for a secondary player, driven by a gamepad instead of the keyboard. */
public final class GamepadInput extends Input {
    private final Gamepad pad;
    boolean sneakToggled;

    public GamepadInput(Gamepad pad) {
        this.pad = pad;
    }

    @Override
    public void tick(boolean slow, float sneakSpeed) {
        float dz = SplitscreenConfig.DEADZONE.get().floatValue();
        float x = pad.axis(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X, dz);
        float y = pad.axis(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y, dz);
        // Stick right means moving right, which is negative leftImpulse in vanilla.
        this.leftImpulse = -x;
        this.forwardImpulse = -y;
        this.up = forwardImpulse > 0.1f;
        this.down = forwardImpulse < -0.1f;
        this.left = leftImpulse > 0.1f;
        this.right = leftImpulse < -0.1f;
        this.jumping = pad.tickHeld(GLFW.GLFW_GAMEPAD_BUTTON_A);
        this.shiftKeyDown = sneakToggled;
        float len = (float) Math.sqrt(leftImpulse * leftImpulse + forwardImpulse * forwardImpulse);
        if (len > 1f) {
            leftImpulse /= len;
            forwardImpulse /= len;
        }
        if (slow) {
            leftImpulse *= sneakSpeed;
            forwardImpulse *= sneakSpeed;
        }
    }
}
