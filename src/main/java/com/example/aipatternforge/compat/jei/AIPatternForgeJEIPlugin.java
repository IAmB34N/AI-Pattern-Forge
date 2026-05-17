package com.example.aipatternforge.compat.jei;

import com.example.aipatternforge.AIPatternForgeMod;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * JEI integration plugin. Caches a reference to the runtime so the terminal screen can push its
 * search query into JEI's ingredient filter when the user enables "Use JEI" or "Sync with JEI".
 *
 * If JEI is not installed at runtime, this class is never loaded by JEI's plugin scanner, so it has
 * no effect. The screen guards all access via {@link JEIBridge} (which checks ModList first).
 */
@JeiPlugin
public class AIPatternForgeJEIPlugin implements IModPlugin {
    @Nullable
    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(AIPatternForgeMod.MOD_ID, "jei_plugin");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    public static void setFilterText(String text) {
        IJeiRuntime current = runtime;
        if (current == null) return;
        current.getIngredientFilter().setFilterText(text);
    }

    @Nullable
    public static String getFilterText() {
        IJeiRuntime current = runtime;
        return current == null ? null : current.getIngredientFilter().getFilterText();
    }
}
