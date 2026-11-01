package org.kylepitcock.splitscreen.client;

import net.neoforged.fml.ModList;
import org.kylepitcock.splitscreen.Splitscreen;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Integration with Controllable (mod id "controllable", optional). It gives the main player controller support, so
 * whichever pad it has active must not also be handed to a secondary player. Its classes are looked up reflectively
 * so this mod neither needs it to compile nor breaks when its internals move; any failure just means
 * "Controllable isn't claiming a controller".
 */
public final class ControllableCompat {
    public static final String MOD_ID = "controllable";

    private static boolean resolved;
    private static boolean failed;
    private static Method getController;
    private static Method getJid;
    private static Method getName;
    private static Method isOpen;
    private static Method getControllers;
    private static Class<?> multiController;
    private static boolean glfwBackend;

    private ControllableCompat() {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** True if Controllable is currently driving the main player with the pad at this GLFW joystick id. */
    public static boolean isReserved(int jid) {
        return reservedJids().contains(jid);
    }

    /** GLFW joystick ids of the pads Controllable currently uses for the main player. */
    public static List<Integer> reservedJids() {
        List<Integer> out = new ArrayList<>(2);
        if (!isLoaded() || failed) return out;
        try {
            resolve();
            Object active = getController.invoke(null);
            if (active == null) return out;
            if (multiController.isInstance(active)) {
                for (Object child : (Iterable<?>) getControllers.invoke(active)) addJid(out, child);
            } else {
                addJid(out, active);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            failed = true;
            Splitscreen.LOGGER.warn("Controllable is installed but its active controller could not be read; "
                    + "all controllers will be offered to secondary players", e);
            out.clear();
        }
        return out;
    }

    private static void addJid(List<Integer> out, Object controller) throws ReflectiveOperationException {
        if (!(Boolean) isOpen.invoke(controller)) return;
        if (glfwBackend) {
            if (getJid.invoke(controller) instanceof Number n) out.add(n.intValue());
            return;
        }
        // The SDL backend numbers pads its own way, so find the GLFW joystick with the same name.
        String name = (String) getName.invoke(controller);
        for (int jid = 0; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
            if (!GLFW.glfwJoystickPresent(jid) || out.contains(jid)) continue;
            String glfwName = GLFW.glfwGetGamepadName(jid);
            if (glfwName == null) glfwName = GLFW.glfwGetJoystickName(jid);
            if (name != null && glfwName != null && (name.equalsIgnoreCase(glfwName) || glfwName.contains(name) || name.contains(glfwName))) {
                out.add(jid);
                return;
            }
        }
    }

    private static void resolve() throws ReflectiveOperationException {
        if (resolved) return;
        Class<?> main = Class.forName("com.mrcrayfish.controllable.Controllable");
        getController = main.getMethod("getController");
        Class<?> controller = Class.forName("com.mrcrayfish.controllable.client.input.Controller");
        getJid = controller.getMethod("getJid");
        getName = controller.getMethod("getName");
        isOpen = controller.getMethod("isOpen");
        multiController = Class.forName("com.mrcrayfish.controllable.client.input.MultiController");
        getControllers = multiController.getMethod("getControllers");
        Object manager = main.getMethod("getControllerManager").invoke(null);
        glfwBackend = manager.getClass().getName().contains(".glfw.");
        resolved = true;
    }
}
