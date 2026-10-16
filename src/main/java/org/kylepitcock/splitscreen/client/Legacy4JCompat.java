package org.kylepitcock.splitscreen.client;

import net.neoforged.fml.ModList;
import org.kylepitcock.splitscreen.Splitscreen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Integration with Legacy4J (mod id "legacy", a required dependency). Its controller classes are looked
 * up reflectively because their names have moved between releases; any failure just means
 * "Legacy4J isn't claiming a controller".
 * <p>
 * Legacy4J drives the main player's UI and gameplay from one selected controller. That controller must
 * not also be handed to a secondary player, so we ask which one it is and skip it.
 */
public final class Legacy4JCompat {
    public static final String MOD_ID = "legacy";

    private static boolean resolved;
    private static boolean failed;
    private static Field connectedController;
    private static Object controllerManager;
    private static Object selectedControllerOption;
    private static Method optionGet;

    private Legacy4JCompat() {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** GLFW joystick id that Legacy4J currently uses for the main player, or -1 if none. */
    public static int mainPlayerJid() {
        if (!isLoaded() || failed) return -1;
        try {
            resolve();
            if (connectedController.get(controllerManager) == null) return -1;
            Object v = optionGet.invoke(selectedControllerOption);
            return v instanceof Number n ? n.intValue() : -1;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            failed = true;
            Splitscreen.LOGGER.warn("Legacy4J is installed but its controller manager could not be read; "
                    + "all controllers will be offered to secondary players", e);
            return -1;
        }
    }

    private static void resolve() throws ReflectiveOperationException {
        if (resolved) return;
        Class<?> client = Class.forName("wily.legacy.Legacy4JClient");
        controllerManager = client.getField("controllerManager").get(null);
        connectedController = controllerManager.getClass().getField("connectedController");
        Class<?> options;
        try {
            options = Class.forName("wily.legacy.client.LegacyOptions"); // 1.8.x
        } catch (ClassNotFoundException e) {
            options = Class.forName("wily.legacy.client.LegacyOption"); // older builds
        }
        selectedControllerOption = options.getField("selectedController").get(null);
        optionGet = selectedControllerOption.getClass().getMethod("get");
        optionGet.setAccessible(true);
        resolved = true;
    }
}
