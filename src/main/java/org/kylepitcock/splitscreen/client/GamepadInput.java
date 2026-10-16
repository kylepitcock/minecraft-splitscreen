package org.kylepitcock.splitscreen.client;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.kylepitcock.splitscreen.SplitscreenConfig;
import org.lwjgl.glfw.GLFW;

/** Movement input for a secondary player, driven by a gamepad instead of the keyboard. */
public final class GamepadInput extends ClientInput {
    private final Gamepad pad;
    boolean sneakToggled;
    boolean sprintLatched;

    public GamepadInput(Gamepad pad) {
        this.pad = pad;
    }

    @Override
    public void tick() {
        float dz = SplitscreenConfig.DEADZONE.get().floatValue();
        float x = pad.axis(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X, dz);
        float y = pad.axis(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y, dz);
        // Stick right means moving right, which is negative "left" impulse in vanilla.
        float left = -x;
        float forward = -y;
        float len = (float) Math.sqrt(left * left + forward * forward);
        if (len > 1f) {
            left /= len;
            forward /= len;
        }
        this.moveVector = new Vec2(left, forward);
        this.keyPresses = new Input(
                forward > 0.1f,
                forward < -0.1f,
                left > 0.1f,
                left < -0.1f,
                pad.tickHeld(GLFW.GLFW_GAMEPAD_BUTTON_A),
                sneakToggled,
                sprintLatched);
    }
}
