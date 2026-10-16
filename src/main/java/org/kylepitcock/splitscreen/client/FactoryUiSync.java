package org.kylepitcock.splitscreen.client;

import net.minecraft.client.gui.Gui;
import wily.factoryapi.base.client.UIAccessor;

/**
 * Legacy4J styles the HUD (scale, offsets, visibility) through Factory API "UI definitions" that are stored
 * on the Gui instance and only ever registered on Minecraft's original Gui. Each extra player gets a fresh
 * Gui, so it has to be given the same definitions or its HUD renders unstyled.
 */
public final class FactoryUiSync {
    private FactoryUiSync() {}

    public static void sync(Gui from, Gui to) {
        if (from == null || to == null || from == to) return;
        UIAccessor source = UIAccessor.of(from);
        UIAccessor target = UIAccessor.of(to);
        var defs = target.getDefinitions();
        defs.clear();
        defs.addAll(source.getDefinitions());
        var statics = target.getStaticDefinitions();
        statics.clear();
        statics.addAll(source.getStaticDefinitions());
        target.beforeInit();
    }
}
