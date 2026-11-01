package org.kylepitcock.splitscreen.client;

import java.util.ArrayList;
import java.util.List;

/** GLFW joystick ids that controller mods (Legacy4J, Controllable) are using for the main player. */
public final class ReservedPads {
    private ReservedPads() {}

    public static List<Integer> jids() {
        List<Integer> out = new ArrayList<>(ControllableCompat.reservedJids());
        int legacy = Legacy4JCompat.mainPlayerJid();
        if (legacy >= 0) out.add(legacy);
        return out;
    }
}
