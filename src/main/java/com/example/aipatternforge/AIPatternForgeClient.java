package com.example.aipatternforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = AIPatternForgeMod.MOD_ID, value = Dist.CLIENT)
public final class AIPatternForgeClient {
    private AIPatternForgeClient() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(AIPatternForgeMod.AI_PATTERN_FORGE_MENU.get(), AiPatternForgeScreen::new);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        RecipeCatalog.tick();
    }

    @SubscribeEvent
    public static void onRecipesUpdated(RecipesUpdatedEvent event) {
        // Server pushed new recipes (datapack reload or fresh join). Drop the catalog so the
        // next client tick rebuilds it incrementally.
        RecipeCatalog.invalidate();
    }
}
