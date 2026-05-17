package com.example.aipatternforge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class AiPatternForgeMenu extends AbstractContainerMenu {
    public static final int PLAYER_INV_X = 8;
    public static final int PLAYER_INV_Y = 167;

    private final ContainerLevelAccess access;
    private final BlockPos blockPos;
    @Nullable
    private final AiPatternForgeBlockEntity blockEntity;

    public AiPatternForgeMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData.readBlockPos(), ContainerLevelAccess.NULL, null);
    }

    public AiPatternForgeMenu(int containerId, Inventory playerInventory, AiPatternForgeBlockEntity blockEntity) {
        this(containerId, playerInventory, blockEntity.getBlockPos(), ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()), blockEntity);
    }

    private AiPatternForgeMenu(int containerId, Inventory playerInventory, BlockPos blockPos, ContainerLevelAccess access,
                               @Nullable AiPatternForgeBlockEntity blockEntity) {
        super(AIPatternForgeMod.AI_PATTERN_FORGE_MENU.get(), containerId);
        this.blockPos = blockPos;
        this.access = access;
        this.blockEntity = blockEntity;

        // Real player inventory slots. These are not fake drawn slots; Minecraft/NeoForge
        // handles normal slot rendering, tooltips, stack counts, and carried-stack behavior.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, PLAYER_INV_X + col * 18, PLAYER_INV_Y + 58));
        }
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    @Nullable
    public AiPatternForgeBlockEntity getServerBlockEntity() {
        return blockEntity;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (blockEntity != null) {
            blockEntity.tryEncodeRecipeIndex((net.minecraft.server.level.ServerPlayer) player, id);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(access, player, AIPatternForgeMod.AI_PATTERN_FORGE.get());
    }
}
