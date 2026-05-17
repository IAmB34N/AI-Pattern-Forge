package com.example.aipatternforge;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AECableType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class AiPatternForgeBlockEntity extends BlockEntity implements IInWorldGridNodeHost, MenuProvider {
    private static final ResourceLocation BLANK_PATTERN_ID = ResourceLocation.fromNamespaceAndPath("ae2", "blank_pattern");
    private final IManagedGridNode mainNode = GridHelper
            .createManagedNode(this, new NodeListener())
            .setTagName("main")
            .setFlags(GridFlags.REQUIRE_CHANNEL)
            .setIdlePowerUsage(0.0)
            .setInWorldNode(true)
            .setExposedOnSides(Set.of(Direction.values()));

    private ItemStack selectedBlock = ItemStack.EMPTY;

    private static ServerLevel cachedServerLevel = null;
    private static int cachedServerRecipeCount = -1;
    private static final List<EncodedRecipeChoice> cachedServerChoices = new ArrayList<>();

    public AiPatternForgeBlockEntity(BlockPos pos, BlockState state) {
        super(AIPatternForgeMod.AI_PATTERN_FORGE_BE.get(), pos, state);
        mainNode.setVisualRepresentation(AIPatternForgeMod.AI_PATTERN_FORGE_ITEM.get());
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        GridHelper.onFirstTick(this, AiPatternForgeBlockEntity::onFirstTick);
    }

    private void onFirstTick() {
        if (level != null && !level.isClientSide) {
            mainNode.create(level, worldPosition);
            updateOnlineState();
        }
    }

    @Override
    public void setRemoved() {
        mainNode.destroy();
        super.setRemoved();
    }

    public ItemStack getSelectedBlock() {
        return selectedBlock.copy();
    }

    public boolean isOnline() {
        return mainNode.isOnline();
    }

    public void setSelectedBlock(ItemStack stack) {
        selectedBlock = stack.copyWithCount(1);
        syncToClient();
    }

    public void clearSelectedBlock() {
        selectedBlock = ItemStack.EMPTY;
        syncToClient();
    }

    public void tryEncodeRecipeIndex(ServerPlayer player, int recipeIndex) {
        tryEncodeGlobalRecipeIndex(player, recipeIndex);
    }

    public void tryEncodeGlobalRecipeIndex(ServerPlayer player, int recipeIndex) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (!mainNode.isActive()) {
            player.displayClientMessage(Component.literal("AI Pattern Forge needs one available AE2 channel and a connected ME network."), true);
            updateOnlineState();
            return;
        }

        if (mainNode.getGrid() == null) {
            player.displayClientMessage(Component.literal("AI Pattern Forge is not connected to an AE2 grid."), true);
            return;
        }

        if (!player.getAbilities().instabuild && findBlankPatternSlot(player) < 0) {
            player.displayClientMessage(Component.literal("Put an AE2 blank pattern in your inventory first."), true);
            return;
        }

        List<EncodedRecipeChoice> recipes = findAllEncodableRecipes(serverLevel);
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) {
            player.displayClientMessage(Component.literal("That recipe is no longer available. Try reopening the forge."), true);
            return;
        }

        EncodedRecipeChoice choice = recipes.get(recipeIndex);
        Optional<ItemStack> encodedPattern = choice.encode();
        if (encodedPattern.isEmpty()) {
            player.displayClientMessage(Component.literal("AE2 rejected that pattern. Try a different recipe."), true);
            return;
        }

        if (!player.getAbilities().instabuild) {
            int slot = findBlankPatternSlot(player);
            if (slot < 0) {
                player.displayClientMessage(Component.literal("Put an AE2 blank pattern in your inventory first."), true);
                return;
            }
            player.getInventory().getItem(slot).shrink(1);
        }

        ItemStack encoded = encodedPattern.get();
        encoded.set(DataComponents.CUSTOM_NAME, Component.literal("AI Pattern: ").append(choice.result().getHoverName()));

        if (!player.getInventory().add(encoded)) {
            player.drop(encoded, false);
        }

        player.displayClientMessage(Component.literal("Encoded ").append(choice.type()).append(" pattern for ").append(choice.result().getHoverName()), true);
    }

    /** Legacy right-click support: encode the first matching recipe for the selected block. */
    public void tryEncodePattern(ServerPlayer player, ItemStack heldBlankPattern) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (selectedBlock.isEmpty()) {
            player.displayClientMessage(Component.literal("Select a block first by right-clicking the forge with a block item, or open the GUI to pick any block."), true);
            return;
        }
        List<EncodedRecipeChoice> recipes = findAllEncodableRecipes(serverLevel);
        for (int i = 0; i < recipes.size(); i++) {
            if (ItemStack.isSameItemSameComponents(recipes.get(i).result(), selectedBlock)) {
                tryEncodeGlobalRecipeIndex(player, i);
                return;
            }
        }
        player.displayClientMessage(Component.literal("No encodable recipe found for ").append(selectedBlock.getHoverName()), true);
    }

    public static List<EncodedRecipeChoice> findEncodableRecipes(ServerLevel serverLevel, ItemStack target) {
        List<EncodedRecipeChoice> matches = new ArrayList<>();
        for (EncodedRecipeChoice choice : findAllEncodableRecipes(serverLevel)) {
            if (ItemStack.isSameItemSameComponents(choice.result(), target)) {
                matches.add(choice);
            }
        }
        return matches;
    }

    public static List<EncodedRecipeChoice> findAllEncodableRecipes(ServerLevel serverLevel) {
        int recipeCount = countRecipeEntries(serverLevel);
        if (cachedServerLevel == serverLevel && cachedServerRecipeCount == recipeCount && !cachedServerChoices.isEmpty()) {
            return cachedServerChoices;
        }

        List<EncodedRecipeChoice> choices = new ArrayList<>();
        HolderLookup.Provider registries = serverLevel.registryAccess();

        for (RecipeHolder<CraftingRecipe> holder : serverLevel.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            CraftingRecipe recipe = holder.value();
            ItemStack result = recipe.getResultItem(registries);
            if (result.isEmpty()) {
                continue;
            }
            ItemStack[] inputGrid = buildCraftingInputGrid(recipe);
            if (!isEmptyGrid(inputGrid)) {
                choices.add(EncodedRecipeChoice.crafting(holder, inputGrid, result.copy()));
            }
        }

        for (RecipeHolder<StonecutterRecipe> holder : serverLevel.getRecipeManager().getAllRecipesFor(RecipeType.STONECUTTING)) {
            StonecutterRecipe recipe = holder.value();
            ItemStack result = recipe.getResultItem(registries);
            Ingredient input = recipe.getIngredients().isEmpty() ? Ingredient.EMPTY : recipe.getIngredients().getFirst();
            ItemStack firstInput = firstMatchingStack(input);
            if (!result.isEmpty() && !firstInput.isEmpty()) {
                choices.add(EncodedRecipeChoice.stonecutting(holder, firstInput, result.copy()));
            }
        }

        addCookingRecipes(choices, "Smelting", serverLevel, RecipeType.SMELTING, registries);
        addCookingRecipes(choices, "Blasting", serverLevel, RecipeType.BLASTING, registries);
        addCookingRecipes(choices, "Smoking", serverLevel, RecipeType.SMOKING, registries);
        addCookingRecipes(choices, "Campfire", serverLevel, RecipeType.CAMPFIRE_COOKING, registries);

        cachedServerLevel = serverLevel;
        cachedServerRecipeCount = recipeCount;
        cachedServerChoices.clear();
        cachedServerChoices.addAll(choices);
        return cachedServerChoices;
    }

    private static int countRecipeEntries(ServerLevel serverLevel) {
        return serverLevel.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).size()
                + serverLevel.getRecipeManager().getAllRecipesFor(RecipeType.STONECUTTING).size()
                + serverLevel.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING).size()
                + serverLevel.getRecipeManager().getAllRecipesFor(RecipeType.BLASTING).size()
                + serverLevel.getRecipeManager().getAllRecipesFor(RecipeType.SMOKING).size()
                + serverLevel.getRecipeManager().getAllRecipesFor(RecipeType.CAMPFIRE_COOKING).size();
    }

    private static <T extends AbstractCookingRecipe> void addCookingRecipes(List<EncodedRecipeChoice> choices, String type,
                                                                            ServerLevel serverLevel, RecipeType<T> recipeType,
                                                                            HolderLookup.Provider registries) {
        for (RecipeHolder<T> holder : serverLevel.getRecipeManager().getAllRecipesFor(recipeType)) {
            T recipe = holder.value();
            ItemStack result = recipe.getResultItem(registries);
            Ingredient input = recipe.getIngredients().isEmpty() ? Ingredient.EMPTY : recipe.getIngredients().getFirst();
            ItemStack firstInput = firstMatchingStack(input);
            if (!result.isEmpty() && !firstInput.isEmpty()) {
                choices.add(EncodedRecipeChoice.processing(type, holder.id(), firstInput, result.copy()));
            }
        }
    }

    private static ItemStack[] buildCraftingInputGrid(CraftingRecipe recipe) {
        ItemStack[] grid = new ItemStack[9];
        for (int i = 0; i < grid.length; i++) {
            grid[i] = ItemStack.EMPTY;
        }

        NonNullList<Ingredient> ingredients = recipe.getIngredients();

        if (recipe instanceof ShapedRecipe shaped) {
            int width = shaped.getWidth();
            int height = shaped.getHeight();
            for (int y = 0; y < height && y < 3; y++) {
                for (int x = 0; x < width && x < 3; x++) {
                    int ingredientIndex = y * width + x;
                    int gridIndex = y * 3 + x;
                    if (ingredientIndex < ingredients.size()) {
                        grid[gridIndex] = firstMatchingStack(ingredients.get(ingredientIndex));
                    }
                }
            }
            return grid;
        }

        int gridSlot = 0;
        for (Ingredient ingredient : ingredients) {
            if (gridSlot >= grid.length) {
                break;
            }
            grid[gridSlot++] = firstMatchingStack(ingredient);
        }
        return grid;
    }

    private static ItemStack firstMatchingStack(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack[] matches = ingredient.getItems();
        if (matches.length == 0) {
            return ItemStack.EMPTY;
        }
        return matches[0].copyWithCount(1);
    }

    private static boolean isEmptyGrid(ItemStack[] grid) {
        for (ItemStack stack : grid) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static boolean isBlankPattern(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(BLANK_PATTERN_ID);
    }

    private static int findBlankPatternSlot(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isBlankPattern(player.getInventory().getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    private void updateOnlineState() {
        if (level instanceof ServerLevel serverLevel) {
            boolean online = mainNode.isOnline();
            BlockState state = getBlockState();
            if (state.hasProperty(AiPatternForgeBlock.ONLINE) && state.getValue(AiPatternForgeBlock.ONLINE) != online) {
                serverLevel.setBlock(worldPosition, state.setValue(AiPatternForgeBlock.ONLINE, online), 3);
            }
        }
    }

    public void setOnline(boolean online) {
        if (level == null || level.isClientSide) {
            return;
        }

        BlockState state = getBlockState();

        if (state.hasProperty(AiPatternForgeBlock.ONLINE)
                && state.getValue(AiPatternForgeBlock.ONLINE) != online) {
            level.setBlock(worldPosition, state.setValue(AiPatternForgeBlock.ONLINE, online), 3);
        }
    }

    private void syncToClient() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public @Nullable IGridNode getGridNode(Direction dir) {
        return mainNode.getNode();
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.aipatternforge.ai_pattern_forge");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new AiPatternForgeMenu(containerId, playerInventory, this);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveSelectedBlock(tag, registries);
        return tag;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveSelectedBlock(tag, registries);
    }

    private void saveSelectedBlock(CompoundTag tag, HolderLookup.Provider registries) {
        if (!selectedBlock.isEmpty()) {
            tag.put("SelectedBlock", selectedBlock.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("SelectedBlock")) {
            selectedBlock = ItemStack.parseOptional(registries, tag.getCompound("SelectedBlock"));
        } else {
            selectedBlock = ItemStack.EMPTY;
        }
    }

    public record EncodedRecipeChoice(String type, ResourceLocation id, RecipeHolder<?> holder, ItemStack[] craftingGrid,
                                      ItemStack input, ItemStack result) {
        static EncodedRecipeChoice crafting(RecipeHolder<CraftingRecipe> holder, ItemStack[] craftingGrid, ItemStack result) {
            return new EncodedRecipeChoice("Crafting", holder.id(), holder, craftingGrid, ItemStack.EMPTY, result);
        }

        static EncodedRecipeChoice stonecutting(RecipeHolder<StonecutterRecipe> holder, ItemStack input, ItemStack result) {
            return new EncodedRecipeChoice("Stonecutting", holder.id(), holder, new ItemStack[0], input.copyWithCount(1), result);
        }

        static EncodedRecipeChoice processing(String type, ResourceLocation id, ItemStack input, ItemStack result) {
            return new EncodedRecipeChoice(type, id, null, new ItemStack[0], input.copyWithCount(1), result);
        }

        Optional<ItemStack> encode() {
            try {
                if ("Crafting".equals(type) && holder != null && holder.value() instanceof CraftingRecipe) {
                    @SuppressWarnings("unchecked")
                    RecipeHolder<CraftingRecipe> craftingHolder = (RecipeHolder<CraftingRecipe>) holder;
                    return Optional.of(PatternDetailsHelper.encodeCraftingPattern(craftingHolder, craftingGrid, result.copy(), true, true));
                }
                if ("Stonecutting".equals(type) && holder != null && holder.value() instanceof StonecutterRecipe) {
                    @SuppressWarnings("unchecked")
                    RecipeHolder<StonecutterRecipe> stoneHolder = (RecipeHolder<StonecutterRecipe>) holder;
                    return Optional.of(PatternDetailsHelper.encodeStonecuttingPattern(stoneHolder, AEItemKey.of(input.copyWithCount(1)), AEItemKey.of(result.copy()), true));
                }
                GenericStack in = GenericStack.fromItemStack(input.copyWithCount(Math.max(1, input.getCount())));
                GenericStack out = GenericStack.fromItemStack(result.copyWithCount(Math.max(1, result.getCount())));
                if (in != null && out != null) {
                    return Optional.of(PatternDetailsHelper.encodeProcessingPattern(List.of(in), List.of(out)));
                }
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
            return Optional.empty();
        }
    }

    private class NodeListener implements IGridNodeListener<AiPatternForgeBlockEntity> {
        @Override
        public void onSaveChanges(AiPatternForgeBlockEntity nodeOwner, IGridNode node) {
            nodeOwner.setChanged();
        }

        @Override
        public void onStateChanged(AiPatternForgeBlockEntity nodeOwner, IGridNode node, State state) {
            nodeOwner.updateOnlineState();
        }
    }
}
