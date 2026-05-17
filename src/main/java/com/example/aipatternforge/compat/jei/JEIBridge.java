package com.example.aipatternforge.compat.jei;

import net.neoforged.fml.ModList;

/**
 * Safe call surface for the rest of the mod. All access to JEI types is delegated to
 * {@link AIPatternForgeJEIPlugin}; this class guards every call with a ModList check so the JVM
 * never tries to link JEI classes when JEI isn't installed.
 */
public final class JEIBridge {
    private static final boolean JEI_LOADED = ModList.get().isLoaded("jei");

    private JEIBridge() {}

    public static boolean isJEILoaded() {
        return JEI_LOADED;
    }

    public static void pushSearchToJEI(String text) {
        if (!JEI_LOADED) return;
        try {
            AIPatternForgeJEIPlugin.setFilterText(text == null ? "" : text);
        } catch (Throwable ignored) {
            // JEI present but API mismatch — silently no-op.
        }
    }
}
