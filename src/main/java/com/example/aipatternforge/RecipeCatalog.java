package com.example.aipatternforge;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Client-side catalog of every recipe + uncraftable block the terminal can show.
 *
 * The catalog is built incrementally on the client tick so it is ready by the time the player
 * opens the terminal. Cache validity is checked against the bound Level plus recipe/item counts;
 * if recipes reload (e.g. /reload), {@link #invalidate()} clears the catalog and the next tick
 * rebuilds it.
 */
public final class RecipeCatalog {
    public record Entry(int globalIndex, String type, ResourceLocation id, ItemStack result, String haystack, String mod) {}

    // Number of recipes/items to process per client tick while the catalog builds.
    // Higher = catalog ready sooner, slightly bigger per-tick spike. 1024 keeps each tick under
    // a few ms on modern hardware while finishing a 20k-item modpack in ~1 second.
    private static final int TICK_BUDGET = 1024;

    private static Level boundLevel;
    private static int boundRecipeCount = -1;
    private static int boundItemCount = -1;
    private static final List<Entry> entries = new ArrayList<>();

    private static boolean indexing = false;
    private static int stage = 0;
    private static int pos = 0;
    private static int globalIndex = 0;
    private static HolderLookup.Provider registries;
    private static List<RecipeHolder<CraftingRecipe>> pendingCrafting = List.of();
    private static List<RecipeHolder<StonecutterRecipe>> pendingStonecutting = List.of();
    private static List<RecipeHolder<SmeltingRecipe>> pendingSmelting = List.of();
    private static List<RecipeHolder<BlastingRecipe>> pendingBlasting = List.of();
    private static List<RecipeHolder<SmokingRecipe>> pendingSmoking = List.of();
    private static List<RecipeHolder<CampfireCookingRecipe>> pendingCampfire = List.of();
    private static List<Item> pendingBlockItems = List.of();
    private static final Set<ResourceLocation> recipeResultItems = new HashSet<>();

    private RecipeCatalog() {}

    /** Drop the catalog. Next {@link #tick()} will rebuild it. */
    public static void invalidate() {
        boundLevel = null;
        boundRecipeCount = -1;
        boundItemCount = -1;
        entries.clear();
        recipeResultItems.clear();
        indexing = false;
        pendingCrafting = List.of();
        pendingStonecutting = List.of();
        pendingSmelting = List.of();
        pendingBlasting = List.of();
        pendingSmoking = List.of();
        pendingCampfire = List.of();
        pendingBlockItems = List.of();
    }

    /** True when the catalog has finished building for the current level. */
    public static boolean isReady() {
        Level level = Minecraft.getInstance().level;
        return level != null && level == boundLevel && !indexing && countRecipes(level) == boundRecipeCount
                && BuiltInRegistries.ITEM.size() == boundItemCount;
    }

    public static boolean isIndexing() {
        return indexing;
    }

    public static List<Entry> entries() {
        return entries;
    }

    /** Called every client tick. Starts a rebuild if the catalog is stale, then advances the budget. */
    public static void tick() {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            // Left the world: keep the catalog around in case we rejoin the same world,
            // but stop ticking. invalidate() is the caller's responsibility on logout if needed.
            return;
        }
        if (!indexing && (boundLevel != level
                || boundRecipeCount != countRecipes(level)
                || boundItemCount != BuiltInRegistries.ITEM.size())) {
            startBuild(level);
        }
        if (indexing) {
            advance(TICK_BUDGET);
        }
    }

    private static void startBuild(Level level) {
        entries.clear();
        recipeResultItems.clear();
        boundLevel = level;
        boundRecipeCount = countRecipes(level);
        boundItemCount = BuiltInRegistries.ITEM.size();
        registries = level.registryAccess();
        pendingCrafting = List.copyOf(level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING));
        pendingStonecutting = List.copyOf(level.getRecipeManager().getAllRecipesFor(RecipeType.STONECUTTING));
        pendingSmelting = List.copyOf(level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING));
        pendingBlasting = List.copyOf(level.getRecipeManager().getAllRecipesFor(RecipeType.BLASTING));
        pendingSmoking = List.copyOf(level.getRecipeManager().getAllRecipesFor(RecipeType.SMOKING));
        pendingCampfire = List.copyOf(level.getRecipeManager().getAllRecipesFor(RecipeType.CAMPFIRE_COOKING));
        List<Item> blocks = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof BlockItem) {
                blocks.add(item);
            }
        }
        pendingBlockItems = blocks;
        stage = 0;
        pos = 0;
        globalIndex = 0;
        indexing = true;
    }

    private static void advance(int budget) {
        while (budget-- > 0 && indexing) {
            switch (stage) {
                case 0 -> {
                    if (pos < pendingCrafting.size()) addCrafting(pendingCrafting.get(pos++));
                    else nextStage();
                }
                case 1 -> {
                    if (pos < pendingStonecutting.size()) addStonecutting(pendingStonecutting.get(pos++));
                    else nextStage();
                }
                case 2 -> {
                    if (pos < pendingSmelting.size()) addCooking(pendingSmelting.get(pos++), "Smelting");
                    else nextStage();
                }
                case 3 -> {
                    if (pos < pendingBlasting.size()) addCooking(pendingBlasting.get(pos++), "Blasting");
                    else nextStage();
                }
                case 4 -> {
                    if (pos < pendingSmoking.size()) addCooking(pendingSmoking.get(pos++), "Smoking");
                    else nextStage();
                }
                case 5 -> {
                    if (pos < pendingCampfire.size()) addCooking(pendingCampfire.get(pos++), "Campfire");
                    else nextStage();
                }
                case 6 -> {
                    if (pos < pendingBlockItems.size()) addUncraftableBlock(pendingBlockItems.get(pos++));
                    else finish();
                }
                default -> finish();
            }
        }
    }

    private static void nextStage() { stage++; pos = 0; }

    private static void finish() {
        indexing = false;
        pendingCrafting = List.of();
        pendingStonecutting = List.of();
        pendingSmelting = List.of();
        pendingBlasting = List.of();
        pendingSmoking = List.of();
        pendingCampfire = List.of();
        pendingBlockItems = List.of();
        registries = null;
    }

    private static void addCrafting(RecipeHolder<CraftingRecipe> holder) {
        ItemStack result = holder.value().getResultItem(registries);
        if (!result.isEmpty() && hasAnyIngredient(holder.value().getIngredients())) {
            rememberResult(result);
            entries.add(new Entry(globalIndex++, "Crafting", holder.id(), result.copy(),
                    makeHaystack(holder.id(), "Crafting", result), modOf(result)));
        }
    }

    private static void addStonecutting(RecipeHolder<StonecutterRecipe> holder) {
        ItemStack result = holder.value().getResultItem(registries);
        if (!result.isEmpty() && hasAnyIngredient(holder.value().getIngredients())) {
            rememberResult(result);
            entries.add(new Entry(globalIndex++, "Stonecutting", holder.id(), result.copy(),
                    makeHaystack(holder.id(), "Stonecutting", result), modOf(result)));
        }
    }

    private static <T extends AbstractCookingRecipe> void addCooking(RecipeHolder<T> holder, String type) {
        ItemStack result = holder.value().getResultItem(registries);
        if (!result.isEmpty() && hasAnyIngredient(holder.value().getIngredients())) {
            rememberResult(result);
            entries.add(new Entry(globalIndex++, type, holder.id(), result.copy(),
                    makeHaystack(holder.id(), type, result), modOf(result)));
        }
    }

    private static void addUncraftableBlock(Item item) {
        ItemStack stack = new ItemStack(item);
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id != null && !recipeResultItems.contains(id)) {
            entries.add(new Entry(-1, "No Pattern", id, stack,
                    makeHaystack(id, "No Pattern", stack),
                    modOf(stack)));
        }
    }

    private static void rememberResult(ItemStack result) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(result.getItem());
        if (id != null) recipeResultItems.add(id);
    }

    private static int countRecipes(Level level) {
        return level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).size()
                + level.getRecipeManager().getAllRecipesFor(RecipeType.STONECUTTING).size()
                + level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING).size()
                + level.getRecipeManager().getAllRecipesFor(RecipeType.BLASTING).size()
                + level.getRecipeManager().getAllRecipesFor(RecipeType.SMOKING).size()
                + level.getRecipeManager().getAllRecipesFor(RecipeType.CAMPFIRE_COOKING).size();
    }

    private static boolean hasAnyIngredient(List<Ingredient> ingredients) {
        for (Ingredient ingredient : ingredients) {
            if (ingredient != null && !ingredient.isEmpty() && ingredient.getItems().length > 0) return true;
        }
        return false;
    }

    /**
     * Builds the lowercase haystack used by the screen's search. Format includes:
     *   <name> <id> <type> @<mod> #<full-tag-id> #<tag-path> ...
     * so plain text, @mod, and #tag queries all reduce to a substring match on the haystack.
     */
    private static String makeHaystack(ResourceLocation id, String type, ItemStack result) {
        StringBuilder sb = new StringBuilder(64);
        sb.append(result.getHoverName().getString().toLowerCase(Locale.ROOT)).append(' ');
        sb.append(id).append(' ');
        sb.append(type.toLowerCase(Locale.ROOT)).append(' ');
        sb.append('@').append(modOf(result).toLowerCase(Locale.ROOT));
        try {
            result.getItem().builtInRegistryHolder().tags().forEach(tag -> {
                ResourceLocation loc = tag.location();
                sb.append(" #").append(loc).append(" #").append(loc.getPath());
            });
        } catch (Throwable ignored) {
            // Tags may not be populated yet on the client during early world join — fall back to no tags.
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static String modOf(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "minecraft" : id.getNamespace();
    }
}
