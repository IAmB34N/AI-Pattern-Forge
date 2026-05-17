package com.example.aipatternforge;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AiPatternForgeScreen extends AbstractContainerScreen<AiPatternForgeMenu> {
    private static final int PANEL = 0xFFC8CEDF;
    private static final int PANEL_LIGHT = 0xFFE3E7F3;
    private static final int PANEL_MID = 0xFFB7BED2;
    private static final int PANEL_DARK = 0xFF737B92;
    private static final int SLOT = 0xFFB4BBD0;
    private static final int SLOT_SELECTED = 0xFFEAF0FF;
    private static final int OUTLINE = 0xFF555B72;
    private static final int TEXT = 0xFF2A2D3A;
    private static final int MUTED = 0xFF596073;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREEN = 0xFF69B96A;
    private static final int RED = 0xFFC86868;

    // Uses the bundled AE2 Pattern Encoding Terminal texture/layout assets.
    // See NOTICE.md for LGPL-3.0 attribution/compliance notes.
    private static final ResourceLocation AE2_PATTERN_GUI = ResourceLocation.fromNamespaceAndPath(AIPatternForgeMod.MOD_ID, "textures/guis/pattern.png");
    private static final ResourceLocation AE2_PATTERN_MODES = ResourceLocation.fromNamespaceAndPath(AIPatternForgeMod.MOD_ID, "textures/guis/pattern_modes.png");
    private static final ResourceLocation AE2_STATES = ResourceLocation.fromNamespaceAndPath(AIPatternForgeMod.MOD_ID, "textures/guis/states.png");

    private EditBox searchBox;
    private Button guideButton;
    private Button sortByButton;
    private Button sortOrderButton;
    private Button visibleTypesButton;
    private Button settingsButton;
    private Button styleButton;
    private Button craftableButton;
    private Button uncraftableButton;
    private Button allButton;
    private Button encodeButton;

    private static Level cachedLevel;
    private static int cachedRecipeCount = -1;
    private static int cachedItemCount = -1;
    private static final List<RecipeDisplay> cachedAllMatches = new ArrayList<>();

    private boolean indexing = false;
    private int indexingStage = 0;
    private int indexingPos = 0;
    private int globalRecipeIndex = 0;
    private int indexingRecipeCount = 0;
    private int indexingItemCount = 0;
    private Level indexingLevel = null;
    private HolderLookup.Provider indexingRegistries = null;
    private List<RecipeHolder<CraftingRecipe>> pendingCrafting = List.of();
    private List<RecipeHolder<StonecutterRecipe>> pendingStonecutting = List.of();
    private List<RecipeHolder<SmeltingRecipe>> pendingSmelting = List.of();
    private List<RecipeHolder<BlastingRecipe>> pendingBlasting = List.of();
    private List<RecipeHolder<SmokingRecipe>> pendingSmoking = List.of();
    private List<RecipeHolder<CampfireCookingRecipe>> pendingCampfire = List.of();
    private List<Item> pendingBlockItems = List.of();
    private final Set<ResourceLocation> recipeResultItems = new HashSet<>();
    private int searchDebounceTicks = 0;

    private TerminalStyle style = TerminalStyle.TALL;
    private SortBy sortBy = SortBy.NAME;
    private boolean ascending = true;
    private CraftabilityFilter craftabilityFilter = CraftabilityFilter.ALL;
    private EncodingMode encodingMode = EncodingMode.CRAFTING;
    private boolean visibleTypesOpen = false;
    private boolean settingsOpen = false;
    private boolean showItems = true;
    private boolean showFluids = true;
    private boolean showEnergy = true;
    private boolean showChemicals = ModList.get().isLoaded("mekanism");

    private final List<RecipeDisplay> allMatches = new ArrayList<>();
    private final List<RecipeDisplay> visibleMatches = new ArrayList<>();
    private final Set<Integer> selectedGlobalIndices = new LinkedHashSet<>();
    private int selectedVisibleIndex = -1;
    private int scrollRow = 0;

    public AiPatternForgeScreen(AiPatternForgeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        applyStyle(TerminalStyle.TALL);
        this.inventoryLabelY = this.imageHeight + 1000;
        this.titleLabelY = this.imageHeight + 1000;
    }

    private void applyStyle(TerminalStyle newStyle) {
        this.style = newStyle;
        // AE2's 1.21.1 Pattern Encoding Terminal texture is a 195x251 screen.
        // The style button is kept for compatibility/user preference, but this rewrite
        // intentionally keeps the exact AE2 terminal proportions instead of stretching
        // the UI and breaking the texture alignment.
        this.imageWidth = 195;
        this.imageHeight = 251;
    }

    @Override
    protected void init() {
        applyStyle(style);
        super.init();

        // Do not use vanilla Button widgets here. AE2 renders its terminal controls using
        // textured icon buttons, and vanilla widgets caused the wrong gray buttons and
        // overlapping text. We draw/click the terminal controls manually below.

        // AE2 search position from assets/ae2/screens/terminals/base_terminal.json
        this.searchBox = new EditBox(this.font, leftPos + 80, topPos + 4, 89, 12, Component.literal("Search"));
        this.searchBox.setHint(Component.literal("Search..."));
        this.searchBox.setResponder(value -> searchDebounceTicks = 5);
        addRenderableWidget(searchBox);

        beginIndexingOrUseCache();
        rebuildVisibleList();
        updateButtonText();
    }

    private void setCraftability(CraftabilityFilter filter) {
        this.craftabilityFilter = filter;
        rebuildVisibleList();
        updateButtonText();
    }

    private void updateButtonText() {
        if (sortByButton != null) sortByButton.setMessage(Component.literal(sortBy.shortLabel()));
        if (sortOrderButton != null) sortOrderButton.setMessage(Component.literal(ascending ? "Asc." : "Desc."));
        if (styleButton != null) styleButton.setMessage(Component.literal(style.shortLabel()));
        if (craftableButton != null) craftableButton.active = craftabilityFilter != CraftabilityFilter.CRAFTABLE;
        if (uncraftableButton != null) uncraftableButton.active = craftabilityFilter != CraftabilityFilter.UNCRAFTABLE;
        if (allButton != null) allButton.active = craftabilityFilter != CraftabilityFilter.ALL;
        if (encodeButton != null) {
            int count = selectedGlobalIndices.isEmpty() ? selectedSingleRecipeCount() : selectedGlobalIndices.size();
            encodeButton.setMessage(Component.literal(count > 1 ? "Encode " + count : "Encode"));
        }
    }

    private int selectedSingleRecipeCount() {
        if (selectedVisibleIndex >= 0 && selectedVisibleIndex < visibleMatches.size() && visibleMatches.get(selectedVisibleIndex).globalIndex() >= 0) {
            return 1;
        }
        return 0;
    }

    private void openGuide() {
        try {
            Util.getPlatform().openUri(new URI("https://guide.appliedenergistics.org/1.21.1/"));
        } catch (Exception ignored) {
        }
    }

    private void encodeSelected() {
        if (minecraft == null || minecraft.gameMode == null) return;

        if (!selectedGlobalIndices.isEmpty()) {
            for (int globalIndex : selectedGlobalIndices) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, globalIndex);
            }
            return;
        }

        if (selectedVisibleIndex >= 0 && selectedVisibleIndex < visibleMatches.size()) {
            RecipeDisplay display = visibleMatches.get(selectedVisibleIndex);
            if (display.globalIndex() >= 0) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, display.globalIndex());
            }
        }
    }

    private void beginIndexingOrUseCache() {
        allMatches.clear();
        visibleMatches.clear();
        recipeResultItems.clear();
        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        int recipeCount = countRecipeEntries(level);
        int itemCount = BuiltInRegistries.ITEM.size();
        if (cachedLevel == level && cachedRecipeCount == recipeCount && cachedItemCount == itemCount && !cachedAllMatches.isEmpty()) {
            allMatches.addAll(cachedAllMatches);
            indexing = false;
            return;
        }

        // Snapshot only the lists on open. The expensive recipe display/haystack work is spread
        // across later client ticks so opening the terminal doesn't freeze large modpacks.
        indexing = true;
        indexingStage = 0;
        indexingPos = 0;
        globalRecipeIndex = 0;
        indexingLevel = level;
        indexingRecipeCount = recipeCount;
        indexingItemCount = itemCount;
        indexingRegistries = level.registryAccess();
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
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (indexing) {
            processIndexingBudget(8);
        }
        if (searchDebounceTicks > 0) {
            searchDebounceTicks--;
            if (searchDebounceTicks == 0) {
                rebuildVisibleList();
                updateButtonText();
            }
        }
    }

    private void processIndexingBudget(int budget) {
        if (indexingRegistries == null) {
            indexing = false;
            return;
        }

        boolean changed = false;
        while (budget-- > 0 && indexing) {
            switch (indexingStage) {
                case 0 -> {
                    if (indexingPos < pendingCrafting.size()) {
                        changed |= addCraftingDisplay(pendingCrafting.get(indexingPos++), indexingRegistries);
                    } else nextIndexingStage();
                }
                case 1 -> {
                    if (indexingPos < pendingStonecutting.size()) {
                        changed |= addStonecuttingDisplay(pendingStonecutting.get(indexingPos++), indexingRegistries);
                    } else nextIndexingStage();
                }
                case 2 -> {
                    if (indexingPos < pendingSmelting.size()) {
                        changed |= addCookingDisplay(pendingSmelting.get(indexingPos++), indexingRegistries, "Smelting");
                    } else nextIndexingStage();
                }
                case 3 -> {
                    if (indexingPos < pendingBlasting.size()) {
                        changed |= addCookingDisplay(pendingBlasting.get(indexingPos++), indexingRegistries, "Blasting");
                    } else nextIndexingStage();
                }
                case 4 -> {
                    if (indexingPos < pendingSmoking.size()) {
                        changed |= addCookingDisplay(pendingSmoking.get(indexingPos++), indexingRegistries, "Smoking");
                    } else nextIndexingStage();
                }
                case 5 -> {
                    if (indexingPos < pendingCampfire.size()) {
                        changed |= addCookingDisplay(pendingCampfire.get(indexingPos++), indexingRegistries, "Campfire");
                    } else nextIndexingStage();
                }
                case 6 -> {
                    if (indexingPos < pendingBlockItems.size()) {
                        changed |= addUncraftableBlockDisplay(pendingBlockItems.get(indexingPos++));
                    } else finishIndexing();
                }
                default -> finishIndexing();
            }
        }

        if (changed || !indexing) {
            rebuildVisibleList();
            updateButtonText();
        }
    }

    private void nextIndexingStage() {
        indexingStage++;
        indexingPos = 0;
    }

    private void finishIndexing() {
        indexing = false;
        cachedLevel = indexingLevel;
        cachedRecipeCount = indexingRecipeCount;
        cachedItemCount = indexingItemCount;
        cachedAllMatches.clear();
        cachedAllMatches.addAll(allMatches);
        pendingCrafting = List.of();
        pendingStonecutting = List.of();
        pendingSmelting = List.of();
        pendingBlasting = List.of();
        pendingSmoking = List.of();
        pendingCampfire = List.of();
        pendingBlockItems = List.of();
    }

    private boolean addCraftingDisplay(RecipeHolder<CraftingRecipe> holder, HolderLookup.Provider registries) {
        ItemStack result = holder.value().getResultItem(registries);
        boolean encodable = !result.isEmpty() && hasAnyIngredient(holder.value().getIngredients());
        if (encodable) {
            rememberRecipeResult(result);
            allMatches.add(new RecipeDisplay(globalRecipeIndex++, "Crafting", holder.id(), result.copy(), makeHaystack(holder.id(), "Crafting", result, holder.value().getIngredients()), getModName(result)));
            return true;
        }
        return false;
    }

    private boolean addStonecuttingDisplay(RecipeHolder<StonecutterRecipe> holder, HolderLookup.Provider registries) {
        ItemStack result = holder.value().getResultItem(registries);
        boolean encodable = !result.isEmpty() && hasAnyIngredient(holder.value().getIngredients());
        if (encodable) {
            rememberRecipeResult(result);
            allMatches.add(new RecipeDisplay(globalRecipeIndex++, "Stonecutting", holder.id(), result.copy(), makeHaystack(holder.id(), "Stonecutting", result, holder.value().getIngredients()), getModName(result)));
            return true;
        }
        return false;
    }

    private <T extends AbstractCookingRecipe> boolean addCookingDisplay(RecipeHolder<T> holder, HolderLookup.Provider registries, String type) {
        ItemStack result = holder.value().getResultItem(registries);
        boolean encodable = !result.isEmpty() && hasAnyIngredient(holder.value().getIngredients());
        if (encodable) {
            rememberRecipeResult(result);
            allMatches.add(new RecipeDisplay(globalRecipeIndex++, type, holder.id(), result.copy(), makeHaystack(holder.id(), type, result, holder.value().getIngredients()), getModName(result)));
            return true;
        }
        return false;
    }

    private boolean addUncraftableBlockDisplay(Item item) {
        ItemStack stack = new ItemStack(item);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId != null && !recipeResultItems.contains(itemId)) {
            allMatches.add(new RecipeDisplay(-1, "No Pattern", itemId, stack, (stack.getHoverName().getString() + " " + itemId + " " + getModName(stack)).toLowerCase(Locale.ROOT), getModName(stack)));
            return true;
        }
        return false;
    }

    private void rememberRecipeResult(ItemStack result) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(result.getItem());
        if (id != null) {
            recipeResultItems.add(id);
        }
    }

    private static int countRecipeEntries(Level level) {
        return level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).size()
                + level.getRecipeManager().getAllRecipesFor(RecipeType.STONECUTTING).size()
                + level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING).size()
                + level.getRecipeManager().getAllRecipesFor(RecipeType.BLASTING).size()
                + level.getRecipeManager().getAllRecipesFor(RecipeType.SMOKING).size()
                + level.getRecipeManager().getAllRecipesFor(RecipeType.CAMPFIRE_COOKING).size();
    }

    private <T extends AbstractCookingRecipe> int addCookingDisplays(int startIndex, Level level, HolderLookup.Provider registries, String type, RecipeType<T> recipeType) {
        int index = startIndex;
        for (RecipeHolder<T> holder : level.getRecipeManager().getAllRecipesFor(recipeType)) {
            ItemStack result = holder.value().getResultItem(registries);
            boolean encodable = !result.isEmpty() && hasAnyIngredient(holder.value().getIngredients());
            if (encodable) {
                allMatches.add(new RecipeDisplay(index, type, holder.id(), result.copy(), makeHaystack(holder.id(), type, result, holder.value().getIngredients()), getModName(result)));
                index++;
            }
        }
        return index;
    }

    private static boolean isBlockResult(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem;
    }

    private static boolean hasAnyIngredient(List<Ingredient> ingredients) {
        for (Ingredient ingredient : ingredients) {
            if (ingredient != null && !ingredient.isEmpty() && ingredient.getItems().length > 0) return true;
        }
        return false;
    }

    private static String makeHaystack(ResourceLocation id, String type, ItemStack result, List<Ingredient> ingredients) {
        // Keep this intentionally small. Older versions added every possible ingredient
        // display name to the search text, which created a lot of short-lived strings in
        // large modpacks and made opening/searching the terminal feel laggy.
        return (id + " "
                + type.toLowerCase(Locale.ROOT) + " "
                + result.getHoverName().getString().toLowerCase(Locale.ROOT) + " "
                + getModName(result).toLowerCase(Locale.ROOT));
    }

    private static String getModName(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "minecraft" : id.getNamespace();
    }

    private void rebuildVisibleList() {
        visibleMatches.clear();
        String q = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        for (RecipeDisplay display : allMatches) {
            if (!showItems) continue;
            boolean hasRecipe = display.globalIndex() >= 0;
            if (craftabilityFilter == CraftabilityFilter.CRAFTABLE && !hasRecipe) continue;
            if (craftabilityFilter == CraftabilityFilter.UNCRAFTABLE && hasRecipe) continue;
            if (q.isEmpty() || display.haystack().contains(q)) visibleMatches.add(display);
        }
        Comparator<RecipeDisplay> comparator = switch (sortBy) {
            case NAME -> Comparator.comparing(display -> display.result().getHoverName().getString().toLowerCase(Locale.ROOT));
            case COUNT -> Comparator.comparingInt(display -> display.result().getCount());
            case MOD -> Comparator.comparing(RecipeDisplay::mod).thenComparing(display -> display.result().getHoverName().getString().toLowerCase(Locale.ROOT));
        };
        if (!ascending) comparator = comparator.reversed();
        visibleMatches.sort(comparator);

        int maxIndex = visibleMatches.size() - 1;
        if (selectedVisibleIndex > maxIndex) selectedVisibleIndex = maxIndex;
        if (selectedVisibleIndex < 0 && !visibleMatches.isEmpty()) selectedVisibleIndex = 0;
        scrollRow = Mth.clamp(scrollRow, 0, maxScrollRow());
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        drawTerminalBackground(guiGraphics);
        renderBlockGrid(guiGraphics, mouseX, mouseY);
        renderPatternArea(guiGraphics);
        renderTerminalControls(guiGraphics);
        renderPopoutPanels(guiGraphics);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderHoveredTooltip(guiGraphics, mouseX, mouseY);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Label positions copied from AE2 19.2.17 screen style JSON.
        guiGraphics.drawString(font, Component.literal("Terminal"), 8, 6, TEXT, false);
        guiGraphics.drawString(font, Component.literal("Pattern Encoding"), 8, imageHeight - 177, TEXT, false);
        guiGraphics.drawString(font, Component.literal("Inventory"), 8, imageHeight - 95, TEXT, false);
    }

    private void drawTerminalBackground(GuiGraphics g) {
        // Directly draw AE2's Pattern Encoding Terminal texture, so the base GUI proportions,
        // slot backgrounds, and panel styling match AE2's 1.21.1 terminal.
        g.blit(AE2_PATTERN_GUI, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // AE2 big scrollbar position from base_terminal.json: left 175, top 18.
        int sx = scrollbarX();
        int sy = gridY();
        int sh = gridH();
        int max = maxScrollRow();
        if (max > 0) {
            int thumbH = Math.max(14, sh / 2);
            int thumbY = sy + (sh - thumbH) * scrollRow / max;
            g.fill(sx + 2, thumbY, sx + 8, thumbY + thumbH, 0xAA404858);
        }
    }

    private void drawRightColumnLabels(GuiGraphics g) {
        // No right-side custom labels in the AE2-layout rewrite. AE2 uses toolbar buttons
        // and mode tabs around the terminal body instead of the old custom right column.
    }

    private void drawGridPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, WHITE);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, PANEL_MID);
    }

    private void renderBlockGrid(GuiGraphics g, int mouseX, int mouseY) {
        int cols = gridCols();
        int rows = gridRows();
        int start = scrollRow * cols;

        if (visibleMatches.isEmpty()) {
            g.drawString(font, Component.literal(indexing ? "Loading recipes..." : "No blocks match that search."), gridX() + 6, gridY() + 8, MUTED, false);
            if (encodeButton != null) encodeButton.active = false;
            return;
        }

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int visibleIndex = start + row * cols + col;
                int x = gridX() + col * 18;
                int y = gridY() + row * 18;
                boolean selected = visibleIndex == selectedVisibleIndex || (visibleIndex < visibleMatches.size() && selectedGlobalIndices.contains(visibleMatches.get(visibleIndex).globalIndex()));
                if (visibleIndex < visibleMatches.size()) {
                    if (selected) {
                        g.fill(x, y, x + 18, y + 18, 0x66FFFFFF);
                        g.fill(x, y, x + 18, y + 1, 0xFFBFE7FF);
                        g.fill(x, y + 17, x + 18, y + 18, 0xFF5E93B8);
                    }
                    ItemStack stack = visibleMatches.get(visibleIndex).result();
                    g.renderItem(stack, x + 1, y + 1);
                    if (visibleMatches.get(visibleIndex).globalIndex() < 0) {
                        g.fill(x + 12, y + 12, x + 17, y + 17, 0xAA9B5555);
                    }
                }
            }
        }

        if (selectedVisibleIndex >= 0 && selectedVisibleIndex < visibleMatches.size()) {
            RecipeDisplay selected = visibleMatches.get(selectedVisibleIndex);
            if (encodeButton != null) encodeButton.active = !selectedGlobalIndices.isEmpty() || selected.globalIndex() >= 0;
        } else if (encodeButton != null) {
            encodeButton.active = !selectedGlobalIndices.isEmpty();
        }

    }

    private void renderPatternArea(GuiGraphics g) {
        // Exact AE2 pattern-mode panel locations from AE2 19.2.17.
        int panelX = leftPos + 8;
        int panelY = topPos + imageHeight - 165;
        switch (encodingMode) {
            case CRAFTING -> g.blit(AE2_PATTERN_MODES, panelX, panelY, 0, 0, 124, 66);
            case PROCESSING -> g.blit(AE2_PATTERN_MODES, panelX, panelY, 0, 70, 124, 66);
            case STONECUTTING -> g.blit(AE2_PATTERN_MODES, panelX, panelY, 0, 140, 124, 66);
            case SMITHING -> g.blit(AE2_PATTERN_MODES, panelX, panelY, 128, 70, 124, 66);
        }

        if (selectedVisibleIndex < 0 || selectedVisibleIndex >= visibleMatches.size()) {
            return;
        }

        RecipeDisplay selected = visibleMatches.get(selectedVisibleIndex);
        if (selected.globalIndex() < 0) {
            return;
        }

        ItemStack result = selected.result();
        if (encodingMode == EncodingMode.CRAFTING) {
            g.renderItem(result, leftPos + 106, topPos + imageHeight - 140);
        } else if (encodingMode == EncodingMode.PROCESSING) {
            g.renderItem(result, leftPos + 109, topPos + imageHeight - 158);
        } else if (encodingMode == EncodingMode.STONECUTTING) {
            g.renderItem(result, leftPos + 109, topPos + imageHeight - 140);
        } else if (encodingMode == EncodingMode.SMITHING) {
            g.renderItem(result, leftPos + 109, topPos + imageHeight - 140);
        }

        // AE2 blank pattern + encoded pattern slots from pattern_encoding_terminal.json.
        int blankX = leftPos + 147;
        int blankY = topPos + imageHeight - 165;
        int encodedX = leftPos + 147;
        int encodedY = topPos + imageHeight - 118;
        drawIcon(g, blankX + 1, blankY + 1, 240, 128);
        if (!selectedGlobalIndices.isEmpty()) {
            g.drawString(font, Component.literal(String.valueOf(selectedGlobalIndices.size())), encodedX + 6, encodedY + 5, TEXT, false);
        } else {
            g.renderItem(result, encodedX + 1, encodedY + 1);
        }
    }

    private void renderFakeInventory(GuiGraphics g) {
        // Intentionally unused. The player inventory is now made of real Slot objects
        // registered in AiPatternForgeMenu, so there are no fake inventory slots.
    }

    private void drawSlot(GuiGraphics g, int x, int y, boolean selected) {
        int fill = selected ? SLOT_SELECTED : SLOT;
        g.fill(x, y, x + 18, y + 18, OUTLINE);
        g.fill(x + 1, y + 1, x + 17, y + 17, WHITE);
        g.fill(x + 2, y + 2, x + 16, y + 16, fill);
    }

    private void renderTerminalControls(GuiGraphics g) {
        // AE2-style left toolbar using actual icons from states.png.
        int toolX = toolbarX();
        int toolY = toolbarY();
        drawToolbarButton(g, toolX, toolY, 176, 0); // help
        drawToolbarButton(g, toolX, toolY + 22, sortBy == SortBy.NAME ? 0 : sortBy == SortBy.COUNT ? 16 : 96, 64); // sort
        drawToolbarButton(g, toolX, toolY + 44, craftabilityFilter == CraftabilityFilter.ALL ? 32 : craftabilityFilter == CraftabilityFilter.CRAFTABLE ? 48 : 0, craftabilityFilter == CraftabilityFilter.UNCRAFTABLE ? 128 : 16); // view/craftability
        drawToolbarButton(g, toolX, toolY + 66, 160, 16); // visible types
        drawToolbarButton(g, toolX, toolY + 88, ascending ? 0 : 16, 48); // sort direction
        drawToolbarButton(g, toolX, toolY + 110, 32, 64); // settings
        drawToolbarButton(g, toolX, toolY + 132, 112, 80); // terminal style

        // Right mode tabs from pattern_encoding_terminal.json.
        drawModeTab(g, leftPos + 173, topPos + imageHeight - 174, 0, 32, encodingMode == EncodingMode.CRAFTING);
        drawModeTab(g, leftPos + 173, topPos + imageHeight - 153, 16, 32, encodingMode == EncodingMode.PROCESSING);
        drawModeTab(g, leftPos + 173, topPos + imageHeight - 132, 48, 32, encodingMode == EncodingMode.STONECUTTING);
        drawModeTab(g, leftPos + 173, topPos + imageHeight - 111, 32, 32, encodingMode == EncodingMode.SMITHING);

        // Encode action button from pattern_encoding_terminal.json.
        int ex = leftPos + 147;
        int ey = topPos + imageHeight - 145;
        drawActionButton(g, ex, ey, 128, 0);
    }

    private void drawToolbarButton(GuiGraphics g, int x, int y, int iconU, int iconV) {
        // Icon.TOOLBAR_BUTTON_BACKGROUND in AE2 states.png: 176,128 size 18x20.
        g.blit(AE2_STATES, x, y, 176, 128, 18, 20);
        drawIcon(g, x + 1, y + 2, iconU, iconV);
    }

    private void drawModeTab(GuiGraphics g, int x, int y, int iconU, int iconV, boolean selected) {
        // Icon.HORIZONTAL_TAB / HORIZONTAL_TAB_SELECTED in AE2 states.png.
        g.blit(AE2_STATES, x, y, selected ? 128 : 128, selected ? 150 : 128, 22, 22);
        drawIcon(g, x + 3, y + 3, iconU, iconV);
    }

    private void drawActionButton(GuiGraphics g, int x, int y, int iconU, int iconV) {
        g.blit(AE2_STATES, x + 1, y, 176, 128, 18, 20);
        drawIcon(g, x + 2, y + 2, iconU, iconV);
    }

    private void drawIcon(GuiGraphics g, int x, int y, int u, int v) {
        g.blit(AE2_STATES, x, y, u, v, 16, 16);
    }

    private boolean inRect(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void renderPopoutPanels(GuiGraphics g) {
        if (visibleTypesOpen) {
            int x = leftPos - 136;
            int y = topPos + 72;
            drawGridPanel(g, x, y, 112, 96);
            g.drawString(font, Component.literal("Visible Types"), x + 8, y + 8, TEXT, false);
            g.drawString(font, Component.literal(toggleText("Items", showItems)), x + 10, y + 25, TEXT, false);
            g.drawString(font, Component.literal(toggleText("Fluids", showFluids)), x + 10, y + 39, TEXT, false);
            g.drawString(font, Component.literal(toggleText("Energy", showEnergy)), x + 10, y + 53, TEXT, false);
            g.drawString(font, Component.literal(toggleText("Chemicals", showChemicals)), x + 10, y + 67, TEXT, false);
            g.drawString(font, Component.literal("Recipe output filter"), x + 10, y + 82, MUTED, false);
        }
        if (settingsOpen) {
            int x = leftPos - 146;
            int y = topPos + 118;
            drawGridPanel(g, x, y, 122, 88);
            g.drawString(font, Component.literal("Terminal Settings"), x + 8, y + 8, TEXT, false);
            g.drawString(font, Component.literal("Sort: " + sortBy.label()), x + 10, y + 25, TEXT, false);
            g.drawString(font, Component.literal("Order: " + (ascending ? "Ascending" : "Descending")), x + 10, y + 39, TEXT, false);
            g.drawString(font, Component.literal("Filter: " + craftabilityFilter.longLabel()), x + 10, y + 53, TEXT, false);
            g.drawString(font, Component.literal("Style: " + style.label()), x + 10, y + 67, TEXT, false);
        }
    }

    private void renderHoveredTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int idx = indexAt(mouseX, mouseY);
        if (idx >= 0 && idx < visibleMatches.size()) {
            RecipeDisplay display = visibleMatches.get(idx);
            List<Component> lines = new ArrayList<>();
            lines.add(display.result().getHoverName());
            lines.add(Component.literal(display.globalIndex() >= 0 ? display.type() + " pattern available" : "No encodable recipe found"));
            if (display.globalIndex() >= 0) {
                lines.add(Component.literal("Shift-click to add/remove"));
            }
            lines.add(Component.literal("Mod: " + display.mod()));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    @Override
    protected void slotClicked(@Nullable Slot slot, int slotId, int mouseButton, ClickType type) {
        // Only intercept shift-clicks from the player's real inventory. Normal clicks remain vanilla
        // so the inventory never feels fake or broken.
        if (slot != null && slot.hasItem() && type == ClickType.QUICK_MOVE) {
            if (encodeFirstRecipeForStack(slot.getItem())) {
                return;
            }
        }
        super.slotClicked(slot, slotId, mouseButton, type);
    }

    private boolean encodeFirstRecipeForStack(ItemStack stack) {
        if (stack.isEmpty()) return false;

        int globalIndex = -1;
        for (RecipeDisplay display : allMatches) {
            if (display.globalIndex() >= 0 && ItemStack.isSameItemSameComponents(display.result(), stack)) {
                globalIndex = display.globalIndex();
                break;
            }
        }

        if (globalIndex < 0) {
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal("No encodable recipe found for ").append(stack.getHoverName()), true);
            }
            return true;
        }

        selectedGlobalIndices.clear();
        selectedGlobalIndices.add(globalIndex);
        craftabilityFilter = CraftabilityFilter.CRAFTABLE;
        if (searchBox != null) searchBox.setValue("");
        rebuildVisibleList();

        for (int i = 0; i < visibleMatches.size(); i++) {
            if (visibleMatches.get(i).globalIndex() == globalIndex) {
                selectedVisibleIndex = i;
                scrollRow = Mth.clamp(i / gridCols(), 0, maxScrollRow());
                break;
            }
        }
        updateButtonText();

        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, globalIndex);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int toolX = toolbarX();
            int toolY = toolbarY();
            if (inRect(mouseX, mouseY, toolX, toolY, 18, 20)) { openGuide(); return true; }
            if (inRect(mouseX, mouseY, toolX, toolY + 22, 18, 20)) { sortBy = sortBy.next(); rebuildVisibleList(); updateButtonText(); return true; }
            if (inRect(mouseX, mouseY, toolX, toolY + 44, 18, 20)) { setCraftability(craftabilityFilter.next()); return true; }
            if (inRect(mouseX, mouseY, toolX, toolY + 66, 18, 20)) { visibleTypesOpen = !visibleTypesOpen; settingsOpen = false; return true; }
            if (inRect(mouseX, mouseY, toolX, toolY + 88, 18, 20)) { ascending = !ascending; rebuildVisibleList(); updateButtonText(); return true; }
            if (inRect(mouseX, mouseY, toolX, toolY + 110, 18, 20)) { settingsOpen = !settingsOpen; visibleTypesOpen = false; return true; }
            if (inRect(mouseX, mouseY, toolX, toolY + 132, 18, 20)) { applyStyle(style.next()); init(minecraft, width, height); return true; }

            if (inRect(mouseX, mouseY, leftPos + 173, topPos + imageHeight - 174, 22, 22)) { encodingMode = EncodingMode.CRAFTING; return true; }
            if (inRect(mouseX, mouseY, leftPos + 173, topPos + imageHeight - 153, 22, 22)) { encodingMode = EncodingMode.PROCESSING; return true; }
            if (inRect(mouseX, mouseY, leftPos + 173, topPos + imageHeight - 132, 22, 22)) { encodingMode = EncodingMode.STONECUTTING; return true; }
            if (inRect(mouseX, mouseY, leftPos + 173, topPos + imageHeight - 111, 22, 22)) { encodingMode = EncodingMode.SMITHING; return true; }
            if (inRect(mouseX, mouseY, leftPos + 147, topPos + imageHeight - 145, 22, 22)) { encodeSelected(); return true; }

            if (visibleTypesOpen) {
                int x = leftPos - 136;
                int y = topPos + 72;
                if (mouseX >= x + 8 && mouseX <= x + 108) {
                    if (mouseY >= y + 23 && mouseY <= y + 36) { showItems = !showItems; rebuildVisibleList(); return true; }
                    if (mouseY >= y + 37 && mouseY <= y + 50) { showFluids = !showFluids; return true; }
                    if (mouseY >= y + 51 && mouseY <= y + 64) { showEnergy = !showEnergy; return true; }
                    if (mouseY >= y + 65 && mouseY <= y + 78) { showChemicals = !showChemicals; return true; }
                }
            }
            int idx = indexAt(mouseX, mouseY);
            if (idx >= 0 && idx < visibleMatches.size()) {
                selectedVisibleIndex = idx;
                RecipeDisplay display = visibleMatches.get(idx);
                if (display.globalIndex() >= 0) {
                    if (Screen.hasShiftDown()) {
                        toggleSelectedGlobal(display.globalIndex());
                    } else {
                        selectedGlobalIndices.clear();
                        selectedGlobalIndices.add(display.globalIndex());
                    }
                    updateButtonText();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void toggleSelectedGlobal(int globalIndex) {
        if (globalIndex < 0) return;
        if (!selectedGlobalIndices.add(globalIndex)) {
            selectedGlobalIndices.remove(globalIndex);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Screen.hasControlDown() && keyCode == 65) {
            selectedGlobalIndices.clear();
            for (RecipeDisplay display : visibleMatches) {
                if (display.globalIndex() >= 0) {
                    selectedGlobalIndices.add(display.globalIndex());
                }
            }
            updateButtonText();
            return true;
        }
        if (keyCode == 256 && !selectedGlobalIndices.isEmpty()) {
            selectedGlobalIndices.clear();
            updateButtonText();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= gridX() && mouseX <= gridX() + gridW() && mouseY >= gridY() && mouseY <= gridY() + gridH()) {
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScrollRow());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int indexAt(double mouseX, double mouseY) {
        if (mouseX < gridX() || mouseX >= gridX() + gridCols() * 18 || mouseY < gridY() || mouseY >= gridY() + gridRows() * 18) return -1;
        int col = ((int) mouseX - gridX()) / 18;
        int row = ((int) mouseY - gridY()) / 18;
        return (scrollRow + row) * gridCols() + col;
    }

    private static String toggleText(String label, boolean enabled) {
        return (enabled ? "[✓] " : "[ ] ") + label;
    }

    private int toolbarX() { return leftPos - 23; }
    private int toolbarY() { return topPos + 4; }
    private int gridX() { return leftPos + 8; }
    private int gridY() { return topPos + 18; }
    private int rightPanelX() { return leftPos + imageWidth; }
    private int gridW() { return 9 * 18; }
    private int gridH() { return 3 * 18; }
    private int gridCols() { return 9; }
    private int gridRows() { return 3; }
    private int scrollbarX() { return leftPos + 175; }
    private int patternX() { return leftPos + 8; }
    private int patternY() { return topPos + imageHeight - 166; }
    private int patternW() { return 159; }
    private int inventoryX() { return leftPos + 8; }
    private int inventoryY() { return topPos + imageHeight - 84; }
    private int inventoryW() { return 9 * 18; }
    private int maxScrollRow() { return Math.max(0, (visibleMatches.size() + gridCols() - 1) / gridCols() - gridRows()); }

    private enum EncodingMode {
        CRAFTING, PROCESSING, STONECUTTING, SMITHING
    }

    private enum SortBy {
        NAME("Item Name A-Z", "Name"), COUNT("Number of Items", "Count"), MOD("Mod", "Mod");
        private final String label;
        private final String shortLabel;
        SortBy(String label, String shortLabel) { this.label = label; this.shortLabel = shortLabel; }
        String label() { return label; }
        String shortLabel() { return shortLabel; }
        SortBy next() { return values()[(ordinal() + 1) % values().length]; }
    }

    private enum CraftabilityFilter {
        ALL("All"), CRAFTABLE("Craftable"), UNCRAFTABLE("Uncraftable");
        private final String longLabel;
        CraftabilityFilter(String longLabel) { this.longLabel = longLabel; }
        String longLabel() { return longLabel; }
        CraftabilityFilter next() { return values()[(ordinal() + 1) % values().length]; }
    }

    private enum TerminalStyle {
        SMALL("Small Centered", "Small"), MEDIUM("Medium Centered", "Medium"), TALL("Tall Centered", "Tall"), FULL_HEIGHT("Full-Height", "Full");
        private final String label;
        private final String shortLabel;
        TerminalStyle(String label, String shortLabel) { this.label = label; this.shortLabel = shortLabel; }
        String label() { return label; }
        String shortLabel() { return shortLabel; }
        TerminalStyle next() { return values()[(ordinal() + 1) % values().length]; }
    }

    private record RecipeDisplay(int globalIndex, String type, ResourceLocation id, ItemStack result, String haystack, String mod) {
    }
}
