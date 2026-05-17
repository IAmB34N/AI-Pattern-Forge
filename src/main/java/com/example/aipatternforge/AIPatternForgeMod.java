package com.example.aipatternforge;

import appeng.api.AECapabilities;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(AIPatternForgeMod.MOD_ID)
public class AIPatternForgeMod {
    public static final String MOD_ID = "aipatternforge";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MOD_ID);

    public static final DeferredBlock<Block> AI_PATTERN_FORGE = BLOCKS.registerBlock(
            "ai_pattern_forge",
            AiPatternForgeBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(AiPatternForgeBlock.ONLINE) ? 8 : 0)
    );

    public static final DeferredItem<BlockItem> AI_PATTERN_FORGE_ITEM = ITEMS.registerSimpleBlockItem(
            AI_PATTERN_FORGE,
            new Item.Properties()
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AiPatternForgeBlockEntity>> AI_PATTERN_FORGE_BE =
            BLOCK_ENTITIES.register("ai_pattern_forge", () -> BlockEntityType.Builder.of(
                    AiPatternForgeBlockEntity::new,
                    AI_PATTERN_FORGE.get()
            ).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<AiPatternForgeMenu>> AI_PATTERN_FORGE_MENU =
            MENUS.register("ai_pattern_forge", () -> IMenuTypeExtension.create(AiPatternForgeMenu::new));

    public AIPatternForgeMod(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(this::registerCapabilities);
        modBus.addListener(this::addCreativeTabItems);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                AI_PATTERN_FORGE_BE.get(),
                (blockEntity, context) -> blockEntity
        );
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(AI_PATTERN_FORGE_ITEM.get());
        }
    }
}
