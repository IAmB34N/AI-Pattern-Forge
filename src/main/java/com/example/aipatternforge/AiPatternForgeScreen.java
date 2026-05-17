package com.example.aipatternforge;

import com.example.aipatternforge.compat.jei.JEIBridge;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * AI Pattern Forge terminal screen.
 *
 * Layout follows the bundled AE2 spec at assets/aipatternforge/screens/terminals/pattern_encoding_terminal.json.
 * The panel width is always 195. The panel height depends on the current terminal style:
 *   - SMALL  =  3 grid rows -> imageHeight 251
 *   - MEDIUM =  6 grid rows -> imageHeight 305
 *   - TALL   = 10 grid rows -> imageHeight 377
 *   - FULL   = 14 grid rows -> imageHeight 449
 * imageHeight is computed as `17 (header) + rows * 18 + 180 (bottom = pattern panel + player inv + hotbar)`.
 *
 * NOTE on AE2 position convention: `bottom: N` means the top-left of the widget is N pixels above the
 * bottom edge of the panel, i.e. y = imageHeight - N. `top: N` means y = N directly.
 */
public class AiPatternForgeScreen extends AbstractContainerScreen<AiPatternForgeMenu> {
    // === Panel width is always 195. Height is dynamic; see imageHeight. ===
    private static final int PANEL_W = 195;
    private static final int HEADER_H = 17;       // pattern.png srcRect [0,0,195,17]
    private static final int ROW_H = 18;          // pattern.png srcRect [0,17,195,18] / [0,35,195,18] / [0,53,195,18]
    private static final int BOTTOM_H = 180;      // pattern.png srcRect [0,71,195,180]
    // pattern.png is a 256x256 atlas; the panel art lives in the top-left 195x251 corner.
    // The 9-arg blit needs the ACTUAL texture dimensions or UV mapping is wrong.
    private static final int TEXTURE_W = 256;
    private static final int TEXTURE_H = 256;

    // === Block grid (top of the panel) ===
    private static final int GRID_X = 8;
    private static final int GRID_Y = 18;
    private static final int GRID_COLS = 9;
    private static final int SLOT_SIZE = 18;
    private static final int GRID_W = GRID_COLS * SLOT_SIZE;

    // === Search box (base_terminal.json: left=80 top=4 w=89 h=12) ===
    private static final int SEARCH_X = 80;
    private static final int SEARCH_Y = 4;
    private static final int SEARCH_W = 89;
    private static final int SEARCH_H = 12;

    // === Scrollbar (base_terminal.json: left=175 top=18) ===
    private static final int SCROLL_X = 175;

    // === Mode tab X (modeTabButtonN: left=173, 22x22) ===
    private static final int MODE_TAB_X = 173;
    private static final int MODE_TAB_W = 22;
    private static final int MODE_TAB_H = 22;
    // Mode tab "bottom" values per AE2 spec
    private static final int[] MODE_TAB_BOTTOM = { 174, 153, 132, 111 };

    // === Right column slots ===
    private static final int BLANK_PATTERN_X = 147;
    private static final int BLANK_PATTERN_BOTTOM = 165;
    private static final int ENCODED_PATTERN_X = 147;
    private static final int ENCODED_PATTERN_BOTTOM = 118;

    // === Encode action button ===
    private static final int ENCODE_BTN_X = 147;
    private static final int ENCODE_BTN_BOTTOM = 145;
    private static final int ENCODE_BTN_W = 18;
    private static final int ENCODE_BTN_H = 20;

    // === Mode panel (modePanel0: left=9 bottom=166, 124x66 sprite) ===
    private static final int MODE_PANEL_X = 9;
    private static final int MODE_PANEL_BOTTOM = 166;
    private static final int MODE_PANEL_W = 124;
    private static final int MODE_PANEL_H = 66;

    // === Per-mode result preview slot positions (AE2 encoding/*.json) ===
    private static final int CRAFTING_RESULT_X = 106;
    private static final int CRAFTING_RESULT_BOTTOM = 140;
    private static final int PROCESSING_OUTPUT_X = 109;
    private static final int PROCESSING_OUTPUT_BOTTOM = 158;
    private static final int STONECUTTING_RESULT_X = 109;
    private static final int STONECUTTING_RESULT_BOTTOM = 140;
    private static final int SMITHING_RESULT_X = 109;
    private static final int SMITHING_RESULT_BOTTOM = 140;

    // === Labels ===
    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 6;
    private static final int PATTERN_LABEL_X = 8;
    private static final int PATTERN_LABEL_BOTTOM = 177;
    private static final int INVENTORY_LABEL_X = 8;
    private static final int INVENTORY_LABEL_BOTTOM = 95;

    // === Left toolbar (attached to the LEFT edge of the panel, AE2 style) ===
    private static final int TOOLBAR_BTN_W = 18;
    private static final int TOOLBAR_BTN_H = 20;
    private static final int TOOLBAR_BTN_STRIDE = 22;
    private static final int TOOLBAR_COUNT = 7;
    private static final int TOOLBAR_X = -TOOLBAR_BTN_W - 4;
    private static final int TOOLBAR_Y = 4;
    private static final int TOOLBAR_BACKDROP_PAD = 3;

    // === Player inventory anchors (from common/player_inventory.json) ===
    private static final int PLAYER_INV_BOTTOM = 84; // top of first inv row = imageHeight - 84
    private static final int PLAYER_HOTBAR_BOTTOM = 26;

    // === Popout panels (open to the LEFT of the main panel) ===
    private static final int POPOUT_W = 178;
    private static final int POPOUT_HEADER_H = 18;
    private static final int POPOUT_ROW_H = 14;

    // === Colors ===
    private static final int TEXT = 0xFF2A2D3A;
    private static final int MUTED = 0xFF596073;
    private static final int PANEL_LIGHT = 0xFFE3E7F3;
    private static final int PANEL_MID = 0xFFB7BED2;
    private static final int PANEL_DARK = 0xFF737B92;
    private static final int OUTLINE = 0xFF555B72;
    private static final int TOGGLE_ON_TRACK = 0xFFB4DBFF;
    private static final int TOGGLE_OFF_TRACK = 0xFF9095A6;
    private static final int TOGGLE_KNOB = 0xFFFFFFFF;
    private static final int TOGGLE_KNOB_DARK = 0xFFC8CCD8;

    private static final ResourceLocation AE2_PATTERN_GUI =
            ResourceLocation.fromNamespaceAndPath(AIPatternForgeMod.MOD_ID, "textures/guis/pattern.png");
    private static final ResourceLocation AE2_PATTERN_MODES =
            ResourceLocation.fromNamespaceAndPath(AIPatternForgeMod.MOD_ID, "textures/guis/pattern_modes.png");
    private static final ResourceLocation AE2_STATES =
            ResourceLocation.fromNamespaceAndPath(AIPatternForgeMod.MOD_ID, "textures/guis/states.png");

    // === AE2 icon U/V coordinates in states.png (taken from AE2 19.2.x Icon enum) ===
    // 16x16 unless noted.
    private static final int ICON_HELP_U = 176, ICON_HELP_V = 0;
    private static final int ICON_VIEW_STORED_U = 0,  ICON_VIEW_STORED_V = 16;   // uncraftable filter
    private static final int ICON_VIEW_ALL_U = 32, ICON_VIEW_ALL_V = 16;         // all filter
    private static final int ICON_VIEW_CRAFTING_U = 48, ICON_VIEW_CRAFTING_V = 16; // craftable filter
    private static final int ICON_TYPE_FILTER_ALL_U = 160, ICON_TYPE_FILTER_ALL_V = 16;
    private static final int ICON_TAB_CRAFTING_U = 0,  ICON_TAB_CRAFTING_V = 32;
    private static final int ICON_TAB_PROCESSING_U = 16, ICON_TAB_PROCESSING_V = 32;
    private static final int ICON_TAB_SMITHING_U = 32, ICON_TAB_SMITHING_V = 32;
    private static final int ICON_TAB_STONECUTTING_U = 48, ICON_TAB_STONECUTTING_V = 32;
    private static final int ICON_ARROW_UP_U = 0,  ICON_ARROW_UP_V = 48;
    private static final int ICON_ARROW_DOWN_U = 16, ICON_ARROW_DOWN_V = 48;
    private static final int ICON_SORT_NAME_U = 0,  ICON_SORT_NAME_V = 64;
    private static final int ICON_SORT_AMOUNT_U = 16, ICON_SORT_AMOUNT_V = 64;
    private static final int ICON_COG_U = 32, ICON_COG_V = 64;                   // settings
    private static final int ICON_SORT_MOD_U = 96, ICON_SORT_MOD_V = 64;
    private static final int ICON_TOOLBAR_BG_U = 176, ICON_TOOLBAR_BG_V = 128;   // 18x20
    private static final int ICON_TAB_BG_U = 128, ICON_TAB_BG_V = 128;           // 22x22
    private static final int ICON_TAB_BG_SEL_U = 128, ICON_TAB_BG_SEL_V = 150;   // 22x22
    private static final int ICON_BLANK_PATTERN_U = 240, ICON_BLANK_PATTERN_V = 128;
    private static final int ICON_STYLE_SMALL_U = 0,  ICON_STYLE_SMALL_V = 208;
    private static final int ICON_STYLE_MEDIUM_U = 16, ICON_STYLE_MEDIUM_V = 208;
    private static final int ICON_STYLE_TALL_U = 32, ICON_STYLE_TALL_V = 208;
    private static final int ICON_STYLE_FULL_U = 48, ICON_STYLE_FULL_V = 208;

    // === Cross-screen-open state (so style + search + settings persist across reopens in a session) ===
    private static TerminalStyle persistedStyle = TerminalStyle.SMALL;
    private static String persistedSearch = "";
    private static SortBy persistedSortBy = SortBy.NAME;
    private static boolean persistedAscending = true;
    private static CraftabilityFilter persistedCraftability = CraftabilityFilter.ALL;
    // Visible types
    private static boolean persistedShowSource = true;
    private static boolean persistedShowFluids = true;
    private static boolean persistedShowEnergy = true;
    private static boolean persistedShowItems = true;
    private static boolean persistedShowChemicals = ModList.get().isLoaded("mekanism");
    // Settings
    private static boolean persistedPinAutoCrafted = true;
    private static boolean persistedNotifyFinished = true;
    private static boolean persistedClearOnClose = false;
    private static boolean persistedUseAESearch = true;   // mutually exclusive with persistedUseJEISearch
    private static boolean persistedUseJEISearch = false;
    private static boolean persistedRememberSearch = true;
    private static boolean persistedAutoFocus = false;
    private static boolean persistedSyncWithJEI = true;

    private EditBox searchBox;

    private TerminalStyle currentStyle = persistedStyle;
    private SortBy sortBy = persistedSortBy;
    private boolean ascending = persistedAscending;
    private CraftabilityFilter craftabilityFilter = persistedCraftability;
    private EncodingMode encodingMode = EncodingMode.CRAFTING;
    private boolean visibleTypesOpen = false;
    private boolean settingsOpen = false;

    private final List<RecipeCatalog.Entry> visibleMatches = new ArrayList<>();
    private final Set<Integer> selectedGlobalIndices = new LinkedHashSet<>();
    private int selectedVisibleIndex = -1;
    private int scrollRow = 0;
    private int searchDebounceTicks = 0;

    public AiPatternForgeScreen(AiPatternForgeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        applyStyleSize(currentStyle);
        // We render our own labels at AE2 positions; move the vanilla ones off-screen.
        this.inventoryLabelY = imageHeight + 1000;
        this.titleLabelY = imageHeight + 1000;
    }

    private void applyStyleSize(TerminalStyle style) {
        this.currentStyle = style;
        this.imageWidth = PANEL_W;
        this.imageHeight = HEADER_H + style.rows() * ROW_H + BOTTOM_H;
    }

    private void repositionInventorySlots() {
        // Slot.x and Slot.y are public mutable ints in 1.21.1 — safe to retarget client-side.
        // Server-side container slot bookkeeping is independent of these draw coordinates.
        int invY = imageHeight - PLAYER_INV_BOTTOM;
        int hotbarY = imageHeight - PLAYER_HOTBAR_BOTTOM;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (i < 27) {
                slot.y = invY + (i / 9) * SLOT_SIZE;
            } else {
                slot.y = hotbarY;
            }
        }
    }

    @Override
    protected void init() {
        applyStyleSize(currentStyle);
        super.init();
        repositionInventorySlots();
        this.searchBox = new EditBox(this.font, leftPos + SEARCH_X, topPos + SEARCH_Y,
                SEARCH_W, SEARCH_H, Component.literal("Search"));
        this.searchBox.setHint(Component.literal("Search..."));
        this.searchBox.setResponder(value -> {
            searchDebounceTicks = 5;
            if (persistedRememberSearch) persistedSearch = value;
        });
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(50);
        if (persistedRememberSearch && !persistedSearch.isEmpty()) {
            this.searchBox.setValue(persistedSearch);
        }
        addRenderableWidget(searchBox);
        if (persistedAutoFocus) {
            this.searchBox.setFocused(true);
            this.setFocused(this.searchBox);
        }
        rebuildVisibleList();
    }

    @Override
    public void removed() {
        super.removed();
        // Sync transient screen state back to the persisted slots.
        persistedStyle = currentStyle;
        persistedSortBy = sortBy;
        persistedAscending = ascending;
        persistedCraftability = craftabilityFilter;
        if (!persistedRememberSearch) persistedSearch = "";
        if (persistedClearOnClose) selectedGlobalIndices.clear();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (searchDebounceTicks > 0 && --searchDebounceTicks == 0) {
            rebuildVisibleList();
            maybePushSearchToJEI();
        }
    }

    private void maybePushSearchToJEI() {
        if (searchBox == null) return;
        if (persistedUseJEISearch || persistedSyncWithJEI) {
            JEIBridge.pushSearchToJEI(searchBox.getValue());
        }
    }

    // -----------------------------------------------------------------------------------------------
    // RENDER
    // -----------------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        renderHoveredTooltip(g, mouseX, mouseY);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        drawTiledBackground(g);
        drawScrollbarThumb(g);
        drawToolbarBackdrop(g);
        renderBlockGrid(g);
        renderPatternArea(g);
        renderToolbar(g);
        renderModeTabs(g);
        renderEncodeButton(g);
        renderPopouts(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, Component.literal("Terminal"), TITLE_X, TITLE_Y, TEXT, false);
        g.drawString(font, Component.literal("Pattern Encoding"), PATTERN_LABEL_X, imageHeight - PATTERN_LABEL_BOTTOM, TEXT, false);
        g.drawString(font, Component.literal("Inventory"), INVENTORY_LABEL_X, imageHeight - INVENTORY_LABEL_BOTTOM, TEXT, false);

        if (RecipeCatalog.isIndexing()) {
            String label = "Indexing...";
            int labelW = font.width(label);
            g.drawString(font, Component.literal(label), PANEL_W - labelW - 26, TITLE_Y + 1, MUTED, false);
        }
    }

    /**
     * Tile-render the pattern.png texture so the panel can be taller than the source 195x251.
     * Layout: HEADER -> firstRow -> (rows-2)*middleRow -> lastRow -> BOTTOM.
     */
    private void drawTiledBackground(GuiGraphics g) {
        int rows = currentStyle.rows();
        // Header
        g.blit(AE2_PATTERN_GUI, leftPos, topPos, 0, 0, PANEL_W, HEADER_H, TEXTURE_W, TEXTURE_H);
        // firstRow
        int y = topPos + HEADER_H;
        g.blit(AE2_PATTERN_GUI, leftPos, y, 0, HEADER_H, PANEL_W, ROW_H, TEXTURE_W, TEXTURE_H);
        // middle rows (use source y=35)
        for (int i = 0; i < rows - 2; i++) {
            y += ROW_H;
            g.blit(AE2_PATTERN_GUI, leftPos, y, 0, HEADER_H + ROW_H, PANEL_W, ROW_H, TEXTURE_W, TEXTURE_H);
        }
        // lastRow (use source y=53)
        y += ROW_H;
        g.blit(AE2_PATTERN_GUI, leftPos, y, 0, HEADER_H + ROW_H * 2, PANEL_W, ROW_H, TEXTURE_W, TEXTURE_H);
        // bottom (use source y=71, 180 tall)
        y += ROW_H;
        g.blit(AE2_PATTERN_GUI, leftPos, y, 0, HEADER_H + ROW_H * 3, PANEL_W, BOTTOM_H, TEXTURE_W, TEXTURE_H);
    }

    private void drawScrollbarThumb(GuiGraphics g) {
        int max = maxScrollRow();
        int gridH = gridRows() * SLOT_SIZE;
        if (max <= 0) return;
        int thumbH = Math.max(14, gridH / 3);
        int thumbY = topPos + GRID_Y + (gridH - thumbH) * scrollRow / max;
        int thumbX = leftPos + SCROLL_X;
        g.fill(thumbX + 2, thumbY, thumbX + 8, thumbY + thumbH, 0xAA404858);
    }

    private void drawToolbarBackdrop(GuiGraphics g) {
        int x = leftPos + TOOLBAR_X - TOOLBAR_BACKDROP_PAD;
        int y = topPos + TOOLBAR_Y - TOOLBAR_BACKDROP_PAD;
        int w = TOOLBAR_BTN_W + TOOLBAR_BACKDROP_PAD * 2;
        int h = TOOLBAR_BTN_STRIDE * TOOLBAR_COUNT - (TOOLBAR_BTN_STRIDE - TOOLBAR_BTN_H) + TOOLBAR_BACKDROP_PAD * 2;
        g.fill(x, y, x + w, y + h, OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL_LIGHT);
        g.fill(x + 1, y + 1, x + w - 2, y + h - 2, PANEL_MID);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, PANEL_DARK);
    }

    private void renderBlockGrid(GuiGraphics g) {
        List<RecipeCatalog.Entry> matches = visibleMatches;
        int start = scrollRow * GRID_COLS;
        int rows = gridRows();

        if (matches.isEmpty()) {
            String msg = RecipeCatalog.isIndexing() ? "Loading recipes..." : "No items match.";
            g.drawString(font, Component.literal(msg), leftPos + GRID_X + 6, topPos + GRID_Y + 8, MUTED, false);
            return;
        }

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int visibleIndex = start + row * GRID_COLS + col;
                if (visibleIndex >= matches.size()) continue;
                int x = leftPos + GRID_X + col * SLOT_SIZE;
                int y = topPos + GRID_Y + row * SLOT_SIZE;
                RecipeCatalog.Entry e = matches.get(visibleIndex);
                boolean selected = visibleIndex == selectedVisibleIndex
                        || (e.globalIndex() >= 0 && selectedGlobalIndices.contains(e.globalIndex()));
                if (selected) {
                    g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0x66FFFFFF);
                    g.fill(x, y, x + SLOT_SIZE, y + 1, 0xFFBFE7FF);
                    g.fill(x, y + SLOT_SIZE - 1, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF5E93B8);
                }
                g.renderItem(e.result(), x + 1, y + 1);
                if (e.globalIndex() < 0) {
                    g.fill(x + 12, y + 12, x + 17, y + 17, 0xAA9B5555);
                }
            }
        }
    }

    private void renderPatternArea(GuiGraphics g) {
        int panelX = leftPos + MODE_PANEL_X;
        int panelY = topPos + imageHeight - MODE_PANEL_BOTTOM;
        switch (encodingMode) {
            case CRAFTING -> g.blit(AE2_PATTERN_MODES, panelX, panelY, 0, 0, MODE_PANEL_W, MODE_PANEL_H);
            case PROCESSING -> g.blit(AE2_PATTERN_MODES, panelX, panelY, 0, 70, MODE_PANEL_W, MODE_PANEL_H);
            case STONECUTTING -> g.blit(AE2_PATTERN_MODES, panelX, panelY, 0, 140, MODE_PANEL_W, MODE_PANEL_H);
            case SMITHING -> g.blit(AE2_PATTERN_MODES, panelX, panelY, 128, 70, MODE_PANEL_W, MODE_PANEL_H);
        }

        if (selectedVisibleIndex < 0 || selectedVisibleIndex >= visibleMatches.size()) return;
        RecipeCatalog.Entry selected = visibleMatches.get(selectedVisibleIndex);
        if (selected.globalIndex() < 0) return;

        ItemStack result = selected.result();
        switch (encodingMode) {
            case CRAFTING -> g.renderItem(result, leftPos + CRAFTING_RESULT_X, topPos + imageHeight - CRAFTING_RESULT_BOTTOM);
            case PROCESSING -> g.renderItem(result, leftPos + PROCESSING_OUTPUT_X, topPos + imageHeight - PROCESSING_OUTPUT_BOTTOM);
            case STONECUTTING -> g.renderItem(result, leftPos + STONECUTTING_RESULT_X, topPos + imageHeight - STONECUTTING_RESULT_BOTTOM);
            case SMITHING -> g.renderItem(result, leftPos + SMITHING_RESULT_X, topPos + imageHeight - SMITHING_RESULT_BOTTOM);
        }

        // Blank pattern preview using AE2's BACKGROUND_BLANK_PATTERN sprite.
        int blankX = leftPos + BLANK_PATTERN_X;
        int blankY = topPos + imageHeight - BLANK_PATTERN_BOTTOM;
        g.blit(AE2_STATES, blankX + 1, blankY + 1, ICON_BLANK_PATTERN_U, ICON_BLANK_PATTERN_V, 16, 16);

        int encX = leftPos + ENCODED_PATTERN_X;
        int encY = topPos + imageHeight - ENCODED_PATTERN_BOTTOM;
        if (!selectedGlobalIndices.isEmpty()) {
            String count = String.valueOf(selectedGlobalIndices.size());
            int w = font.width(count);
            g.drawString(font, Component.literal(count), encX + 9 - w / 2, encY + 5, TEXT, false);
        } else {
            g.renderItem(result, encX + 1, encY + 1);
        }
    }

    private void renderToolbar(GuiGraphics g) {
        for (int i = 0; i < TOOLBAR_COUNT; i++) {
            int x = leftPos + TOOLBAR_X;
            int y = topPos + TOOLBAR_Y + i * TOOLBAR_BTN_STRIDE;
            drawToolbarButton(g, x, y);
            drawToolbarIcon(g, x, y, i);
        }
    }

    private void drawToolbarButton(GuiGraphics g, int x, int y) {
        g.blit(AE2_STATES, x, y, ICON_TOOLBAR_BG_U, ICON_TOOLBAR_BG_V, TOOLBAR_BTN_W, TOOLBAR_BTN_H);
    }

    /**
     * Draws the icon for the i-th toolbar button using AE2's states.png sprite atlas, so the buttons
     * match the icons from the AE2 reference terminal screenshots.
     */
    private void drawToolbarIcon(GuiGraphics g, int btnX, int btnY, int slot) {
        int u, v;
        switch (slot) {
            case 0 -> { u = ICON_HELP_U; v = ICON_HELP_V; }
            case 1 -> {
                switch (sortBy) {
                    case NAME -> { u = ICON_SORT_NAME_U;   v = ICON_SORT_NAME_V; }
                    case COUNT -> { u = ICON_SORT_AMOUNT_U; v = ICON_SORT_AMOUNT_V; }
                    default ->   { u = ICON_SORT_MOD_U;    v = ICON_SORT_MOD_V; }
                }
            }
            case 2 -> {
                switch (craftabilityFilter) {
                    case ALL -> { u = ICON_VIEW_ALL_U;       v = ICON_VIEW_ALL_V; }
                    case CRAFTABLE -> { u = ICON_VIEW_CRAFTING_U; v = ICON_VIEW_CRAFTING_V; }
                    default ->  { u = ICON_VIEW_STORED_U;    v = ICON_VIEW_STORED_V; }
                }
            }
            case 3 -> { u = ICON_TYPE_FILTER_ALL_U; v = ICON_TYPE_FILTER_ALL_V; }
            case 4 -> { u = ascending ? ICON_ARROW_UP_U : ICON_ARROW_DOWN_U;
                        v = ascending ? ICON_ARROW_UP_V : ICON_ARROW_DOWN_V; }
            case 5 -> { u = ICON_COG_U; v = ICON_COG_V; }
            case 6 -> {
                switch (currentStyle) {
                    case SMALL ->  { u = ICON_STYLE_SMALL_U;  v = ICON_STYLE_SMALL_V; }
                    case MEDIUM -> { u = ICON_STYLE_MEDIUM_U; v = ICON_STYLE_MEDIUM_V; }
                    case TALL ->   { u = ICON_STYLE_TALL_U;   v = ICON_STYLE_TALL_V; }
                    default ->     { u = ICON_STYLE_FULL_U;   v = ICON_STYLE_FULL_V; }
                }
            }
            default -> { u = ICON_HELP_U; v = ICON_HELP_V; }
        }
        g.blit(AE2_STATES, btnX + 1, btnY + 2, u, v, 16, 16);
    }

    private void renderModeTabs(GuiGraphics g) {
        int[][] iconUV = {
                {ICON_TAB_CRAFTING_U, ICON_TAB_CRAFTING_V},
                {ICON_TAB_PROCESSING_U, ICON_TAB_PROCESSING_V},
                {ICON_TAB_STONECUTTING_U, ICON_TAB_STONECUTTING_V},
                {ICON_TAB_SMITHING_U, ICON_TAB_SMITHING_V}
        };
        for (int i = 0; i < MODE_TAB_BOTTOM.length; i++) {
            int x = leftPos + MODE_TAB_X;
            int y = topPos + imageHeight - MODE_TAB_BOTTOM[i];
            boolean selected = encodingMode.ordinal() == i;
            int bgU = ICON_TAB_BG_U;
            int bgV = selected ? ICON_TAB_BG_SEL_V : ICON_TAB_BG_V;
            g.blit(AE2_STATES, x, y, bgU, bgV, MODE_TAB_W, MODE_TAB_H);
            g.blit(AE2_STATES, x + 3, y + 3, iconUV[i][0], iconUV[i][1], 16, 16);
        }
    }

    private void renderEncodeButton(GuiGraphics g) {
        int x = leftPos + ENCODE_BTN_X;
        int y = topPos + imageHeight - ENCODE_BTN_BOTTOM;
        boolean canEncode = encodableSelectionCount() > 0;
        g.blit(AE2_STATES, x, y, ICON_TOOLBAR_BG_U, ICON_TOOLBAR_BG_V, ENCODE_BTN_W, ENCODE_BTN_H);
        // Down arrow (no AE2 single-pattern encode icon; primitive draw keeps it crisp).
        drawDownArrow(g, x + 1, y + 2);
        if (!canEncode) {
            g.fill(x, y, x + ENCODE_BTN_W, y + ENCODE_BTN_H, 0x80808080);
        }
    }

    private void drawDownArrow(GuiGraphics g, int x, int y) {
        // Shaft
        g.fill(x + 7, y + 3, x + 9, y + 11, TEXT);
        // Arrowhead
        g.fill(x + 4, y + 9, x + 12, y + 11, TEXT);
        g.fill(x + 5, y + 11, x + 11, y + 12, TEXT);
        g.fill(x + 6, y + 12, x + 10, y + 13, TEXT);
        g.fill(x + 7, y + 13, x + 9, y + 14, TEXT);
    }

    // -----------------------------------------------------------------------------------------------
    // POPOUTS
    // -----------------------------------------------------------------------------------------------

    private int popoutVisibleTypesH() { return POPOUT_HEADER_H + POPOUT_ROW_H * 5 + 8; }
    private int popoutSettingsH() { return POPOUT_HEADER_H + POPOUT_ROW_H * 8 + 12; }

    private int popoutX() {
        return Math.max(4, leftPos - POPOUT_W - 10);
    }

    private int popoutY(int popoutHeight) {
        return Math.max(4, topPos + (imageHeight - popoutHeight) / 2);
    }

    private void renderPopouts(GuiGraphics g, int mouseX, int mouseY) {
        if (visibleTypesOpen) drawVisibleTypesPopout(g, mouseX, mouseY);
        if (settingsOpen) drawSettingsPopout(g, mouseX, mouseY);
    }

    private void drawVisibleTypesPopout(GuiGraphics g, int mouseX, int mouseY) {
        int h = popoutVisibleTypesH();
        int x = popoutX();
        int y = popoutY(h);
        drawPopoutFrame(g, x, y, POPOUT_W, h, "Configure Visible Types");
        int rowY = y + POPOUT_HEADER_H + 4;
        drawToggleRow(g, x + 10, rowY,                     "Source",    persistedShowSource);
        drawToggleRow(g, x + 10, rowY + POPOUT_ROW_H,      "Fluids",    persistedShowFluids);
        drawToggleRow(g, x + 10, rowY + POPOUT_ROW_H * 2,  "Energy",    persistedShowEnergy);
        drawToggleRow(g, x + 10, rowY + POPOUT_ROW_H * 3,  "Items",     persistedShowItems);
        drawToggleRow(g, x + 10, rowY + POPOUT_ROW_H * 4,  "Chemicals", persistedShowChemicals);
    }

    private void drawSettingsPopout(GuiGraphics g, int mouseX, int mouseY) {
        int h = popoutSettingsH();
        int x = popoutX();
        int y = popoutY(h);
        drawPopoutFrame(g, x, y, POPOUT_W, h, "Terminal Settings");
        int rowY = y + POPOUT_HEADER_H + 4;
        drawToggleRow(g, x + 10, rowY,                    "Pin auto-crafted items to first row", persistedPinAutoCrafted);
        drawToggleRow(g, x + 10, rowY + POPOUT_ROW_H,     "Notify when crafting jobs finish",    persistedNotifyFinished);
        drawToggleRow(g, x + 10, rowY + POPOUT_ROW_H * 2, "Clear terminal grid on close",        persistedClearOnClose);
        // Section header
        int sectionY = rowY + POPOUT_ROW_H * 3 + 2;
        g.drawString(font, Component.literal("Search Settings"), x + 10, sectionY, MUTED, false);
        rowY = sectionY + 10;
        drawToggleRow(g, x + 10, rowY,                    "Use AE search",                       persistedUseAESearch);
        drawToggleRow(g, x + 10, rowY + POPOUT_ROW_H,     "Use JEI search",                      persistedUseJEISearch);
        drawToggleRow(g, x + 10, rowY + POPOUT_ROW_H * 2, "Remember last search",                persistedRememberSearch);
        drawToggleRow(g, x + 10, rowY + POPOUT_ROW_H * 3, "Auto-Focus search on open",           persistedAutoFocus);
        drawToggleRow(g, x + 10, rowY + POPOUT_ROW_H * 4, "Sync with JEI search",                persistedSyncWithJEI);
    }

    private void drawPopoutFrame(GuiGraphics g, int x, int y, int w, int h, String title) {
        // Outer panel
        g.fill(x, y, x + w, y + h, OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL_LIGHT);
        g.fill(x + 1, y + 1, x + w - 2, y + h - 2, PANEL_MID);
        // Header row
        g.fill(x + 1, y + 1, x + w - 1, y + POPOUT_HEADER_H, PANEL_DARK);
        g.fill(x + 1, y + 1, x + w - 1, y + POPOUT_HEADER_H - 1, PANEL_MID);
        // ? icon on left and X close icon on right
        drawHeaderIcon(g, x + 4, y + 3, "?");
        drawHeaderIcon(g, x + w - 18, y + 3, "x");
        // Title
        g.drawString(font, Component.literal(title), x + 22, y + 5, TEXT, false);
    }

    private void drawHeaderIcon(GuiGraphics g, int x, int y, String glyph) {
        g.fill(x, y, x + 13, y + 13, OUTLINE);
        g.fill(x + 1, y + 1, x + 12, y + 12, PANEL_LIGHT);
        int gw = font.width(glyph);
        g.drawString(font, glyph, x + (13 - gw) / 2 + 1, y + 3, TEXT, false);
    }

    private void drawToggleRow(GuiGraphics g, int x, int y, String label, boolean on) {
        drawToggle(g, x, y + 2, on);
        g.drawString(font, label, x + 22, y + 3, TEXT, false);
    }

    private void drawToggle(GuiGraphics g, int x, int y, boolean on) {
        int trackColor = on ? TOGGLE_ON_TRACK : TOGGLE_OFF_TRACK;
        int knobColor = on ? TOGGLE_KNOB : TOGGLE_KNOB_DARK;
        g.fill(x, y, x + 17, y + 9, OUTLINE);
        g.fill(x + 1, y + 1, x + 16, y + 8, trackColor);
        int knobX = on ? x + 1 : x + 9;
        g.fill(knobX, y + 1, knobX + 7, y + 8, knobColor);
    }

    private void renderHoveredTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int idx = indexAt(mouseX, mouseY);
        if (idx < 0 || idx >= visibleMatches.size()) return;
        RecipeCatalog.Entry display = visibleMatches.get(idx);
        List<Component> lines = new ArrayList<>();
        lines.add(display.result().getHoverName());
        lines.add(Component.literal(display.globalIndex() >= 0
                ? display.type() + " pattern available"
                : "No encodable recipe found"));
        if (display.globalIndex() >= 0) lines.add(Component.literal("Shift-click to add/remove"));
        lines.add(Component.literal("Mod: " + display.mod()));
        g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    // -----------------------------------------------------------------------------------------------
    // INPUT
    // -----------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Popouts intercept clicks while open so the user can flip toggles without closing.
            if (visibleTypesOpen && handleVisibleTypesClick(mouseX, mouseY)) return true;
            if (settingsOpen && handleSettingsClick(mouseX, mouseY)) return true;

            // Toolbar
            for (int i = 0; i < TOOLBAR_COUNT; i++) {
                int x = leftPos + TOOLBAR_X;
                int y = topPos + TOOLBAR_Y + i * TOOLBAR_BTN_STRIDE;
                if (inRect(mouseX, mouseY, x, y, TOOLBAR_BTN_W, TOOLBAR_BTN_H)) {
                    onToolbarClick(i);
                    return true;
                }
            }
            // Mode tabs
            for (int i = 0; i < MODE_TAB_BOTTOM.length; i++) {
                int x = leftPos + MODE_TAB_X;
                int y = topPos + imageHeight - MODE_TAB_BOTTOM[i];
                if (inRect(mouseX, mouseY, x, y, MODE_TAB_W, MODE_TAB_H)) {
                    encodingMode = EncodingMode.values()[i];
                    return true;
                }
            }
            // Encode button
            int encX = leftPos + ENCODE_BTN_X;
            int encY = topPos + imageHeight - ENCODE_BTN_BOTTOM;
            if (inRect(mouseX, mouseY, encX, encY, ENCODE_BTN_W, ENCODE_BTN_H)) {
                encodeSelected();
                return true;
            }
            // Block grid
            int idx = indexAt(mouseX, mouseY);
            if (idx >= 0 && idx < visibleMatches.size()) {
                handleGridClick(idx);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void onToolbarClick(int slot) {
        switch (slot) {
            case 0 -> openGuide();
            case 1 -> { sortBy = sortBy.next(); rebuildVisibleList(); }
            case 2 -> { craftabilityFilter = craftabilityFilter.next(); rebuildVisibleList(); }
            case 3 -> { visibleTypesOpen = !visibleTypesOpen; settingsOpen = false; }
            case 4 -> { ascending = !ascending; rebuildVisibleList(); }
            case 5 -> { settingsOpen = !settingsOpen; visibleTypesOpen = false; }
            case 6 -> cycleTerminalStyle();
        }
    }

    private void cycleTerminalStyle() {
        TerminalStyle next = currentStyle.next();
        applyStyleSize(next);
        if (this.minecraft != null) {
            String preserved = searchBox != null ? searchBox.getValue() : "";
            init(minecraft, width, height);
            if (searchBox != null) searchBox.setValue(preserved);
        }
    }

    private boolean handleVisibleTypesClick(double mouseX, double mouseY) {
        int h = popoutVisibleTypesH();
        int px = popoutX();
        int py = popoutY(h);
        if (!inRect(mouseX, mouseY, px, py, POPOUT_W, h)) return false;
        // Close (X) icon
        if (inRect(mouseX, mouseY, px + POPOUT_W - 18, py + 3, 13, 13)) {
            visibleTypesOpen = false;
            return true;
        }
        int rowY = py + POPOUT_HEADER_H + 4;
        if (toggleRowHit(mouseX, mouseY, px + 10, rowY))                    { persistedShowSource    = !persistedShowSource;    rebuildVisibleList(); return true; }
        if (toggleRowHit(mouseX, mouseY, px + 10, rowY + POPOUT_ROW_H))     { persistedShowFluids    = !persistedShowFluids;    rebuildVisibleList(); return true; }
        if (toggleRowHit(mouseX, mouseY, px + 10, rowY + POPOUT_ROW_H * 2)) { persistedShowEnergy    = !persistedShowEnergy;    rebuildVisibleList(); return true; }
        if (toggleRowHit(mouseX, mouseY, px + 10, rowY + POPOUT_ROW_H * 3)) { persistedShowItems     = !persistedShowItems;     rebuildVisibleList(); return true; }
        if (toggleRowHit(mouseX, mouseY, px + 10, rowY + POPOUT_ROW_H * 4)) { persistedShowChemicals = !persistedShowChemicals; rebuildVisibleList(); return true; }
        return true; // swallow clicks inside the panel even if they don't hit a toggle
    }

    private boolean handleSettingsClick(double mouseX, double mouseY) {
        int h = popoutSettingsH();
        int px = popoutX();
        int py = popoutY(h);
        if (!inRect(mouseX, mouseY, px, py, POPOUT_W, h)) return false;
        if (inRect(mouseX, mouseY, px + POPOUT_W - 18, py + 3, 13, 13)) {
            settingsOpen = false;
            return true;
        }
        int rowY = py + POPOUT_HEADER_H + 4;
        if (toggleRowHit(mouseX, mouseY, px + 10, rowY))                    { persistedPinAutoCrafted = !persistedPinAutoCrafted; rebuildVisibleList(); return true; }
        if (toggleRowHit(mouseX, mouseY, px + 10, rowY + POPOUT_ROW_H))     { persistedNotifyFinished = !persistedNotifyFinished; return true; }
        if (toggleRowHit(mouseX, mouseY, px + 10, rowY + POPOUT_ROW_H * 2)) { persistedClearOnClose   = !persistedClearOnClose; return true; }
        int sectionY = rowY + POPOUT_ROW_H * 3 + 2;
        int srowY = sectionY + 10;
        if (toggleRowHit(mouseX, mouseY, px + 10, srowY))                    { persistedUseAESearch  = true;  persistedUseJEISearch = false; return true; }
        if (toggleRowHit(mouseX, mouseY, px + 10, srowY + POPOUT_ROW_H))     { persistedUseJEISearch = true;  persistedUseAESearch  = false; return true; }
        if (toggleRowHit(mouseX, mouseY, px + 10, srowY + POPOUT_ROW_H * 2)) { persistedRememberSearch = !persistedRememberSearch; if (!persistedRememberSearch) persistedSearch = ""; return true; }
        if (toggleRowHit(mouseX, mouseY, px + 10, srowY + POPOUT_ROW_H * 3)) {
            persistedAutoFocus = !persistedAutoFocus;
            if (persistedAutoFocus && searchBox != null) { searchBox.setFocused(true); this.setFocused(searchBox); }
            return true;
        }
        if (toggleRowHit(mouseX, mouseY, px + 10, srowY + POPOUT_ROW_H * 4)) { persistedSyncWithJEI  = !persistedSyncWithJEI; return true; }
        return true;
    }

    private boolean toggleRowHit(double mouseX, double mouseY, int rowX, int rowY) {
        // Whole row is clickable: toggle pill (17x9) + label area.
        return inRect(mouseX, mouseY, rowX, rowY, POPOUT_W - 24, POPOUT_ROW_H);
    }

    private void handleGridClick(int idx) {
        selectedVisibleIndex = idx;
        RecipeCatalog.Entry display = visibleMatches.get(idx);
        if (display.globalIndex() < 0) return;
        if (Screen.hasShiftDown()) {
            toggleSelected(display.globalIndex());
        } else {
            selectedGlobalIndices.clear();
            selectedGlobalIndices.add(display.globalIndex());
        }
    }

    private void toggleSelected(int globalIndex) {
        if (!selectedGlobalIndices.add(globalIndex)) selectedGlobalIndices.remove(globalIndex);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean searchFocused = searchBox != null && searchBox.isFocused();

        // Ctrl+A: select every visible craftable, but only when the search box doesn't have focus
        // (so Ctrl+A inside the search box still selects search text).
        if (Screen.hasControlDown() && keyCode == 65 && !searchFocused) {
            selectedGlobalIndices.clear();
            for (RecipeCatalog.Entry e : visibleMatches) {
                if (e.globalIndex() >= 0) selectedGlobalIndices.add(e.globalIndex());
            }
            return true;
        }

        if (keyCode == 256) { // Esc
            if (visibleTypesOpen) { visibleTypesOpen = false; return true; }
            if (settingsOpen) { settingsOpen = false; return true; }
            if (searchFocused) {
                searchBox.setFocused(false);
                setFocused(null);
                return true;
            }
            if (!selectedGlobalIndices.isEmpty()) {
                selectedGlobalIndices.clear();
                return true;
            }
        }

        // While the search box is focused, route keys to it directly and bypass
        // AbstractContainerScreen's inventory-keybind close (so pressing 'E' while typing inserts
        // the letter instead of closing the screen). Returning false here lets charTyped fire next.
        if (searchFocused) {
            return searchBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inRect(mouseX, mouseY, leftPos + GRID_X, topPos + GRID_Y, GRID_W, gridRows() * SLOT_SIZE)) {
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScrollRow());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void slotClicked(@Nullable Slot slot, int slotId, int mouseButton, ClickType type) {
        if (slot != null && slot.hasItem()) {
            if (type == ClickType.PICKUP && mouseButton == 0 && selectMatchingPattern(slot.getItem(), false)) return;
            if (type == ClickType.QUICK_MOVE && selectMatchingPattern(slot.getItem(), true)) return;
        }
        super.slotClicked(slot, slotId, mouseButton, type);
    }

    private boolean selectMatchingPattern(ItemStack stack, boolean toggleMultiSelect) {
        if (stack.isEmpty()) return false;
        int globalIndex = -1;
        for (RecipeCatalog.Entry e : RecipeCatalog.entries()) {
            if (e.globalIndex() >= 0 && ItemStack.isSameItemSameComponents(e.result(), stack)) {
                globalIndex = e.globalIndex();
                break;
            }
        }
        if (globalIndex < 0) {
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.literal("No encodable recipe found for ").append(stack.getHoverName()), true);
            }
            return true;
        }

        if (toggleMultiSelect) {
            toggleSelected(globalIndex);
        } else {
            selectedGlobalIndices.clear();
            selectedGlobalIndices.add(globalIndex);
        }

        craftabilityFilter = CraftabilityFilter.CRAFTABLE;
        if (searchBox != null) searchBox.setValue("");
        rebuildVisibleList();
        for (int i = 0; i < visibleMatches.size(); i++) {
            if (visibleMatches.get(i).globalIndex() == globalIndex) {
                selectedVisibleIndex = i;
                scrollRow = Mth.clamp(i / GRID_COLS, 0, maxScrollRow());
                break;
            }
        }
        return true;
    }

    // -----------------------------------------------------------------------------------------------
    // ACTIONS
    // -----------------------------------------------------------------------------------------------

    private void openGuide() {
        try { Util.getPlatform().openUri(new URI("https://guide.appliedenergistics.org/1.21.1/")); }
        catch (Exception ignored) {}
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
            RecipeCatalog.Entry display = visibleMatches.get(selectedVisibleIndex);
            if (display.globalIndex() >= 0) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, display.globalIndex());
            }
        }
    }

    private int encodableSelectionCount() {
        if (!selectedGlobalIndices.isEmpty()) return selectedGlobalIndices.size();
        if (selectedVisibleIndex >= 0 && selectedVisibleIndex < visibleMatches.size()
                && visibleMatches.get(selectedVisibleIndex).globalIndex() >= 0) return 1;
        return 0;
    }

    // -----------------------------------------------------------------------------------------------
    // FILTER / SORT
    // -----------------------------------------------------------------------------------------------

    private void rebuildVisibleList() {
        visibleMatches.clear();
        if (!persistedShowItems) return; // visible-types filter: no items
        String q = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        String[] tokens = q.isEmpty() ? EMPTY_TOKENS : q.split("\\s+");
        for (RecipeCatalog.Entry e : RecipeCatalog.entries()) {
            boolean hasRecipe = e.globalIndex() >= 0;
            if (craftabilityFilter == CraftabilityFilter.CRAFTABLE && !hasRecipe) continue;
            if (craftabilityFilter == CraftabilityFilter.UNCRAFTABLE && hasRecipe) continue;
            if (matchesAllTokens(e.haystack(), tokens)) visibleMatches.add(e);
        }
        Comparator<RecipeCatalog.Entry> cmp = switch (sortBy) {
            case NAME -> Comparator.comparing(e -> e.result().getHoverName().getString().toLowerCase(Locale.ROOT));
            case COUNT -> Comparator.comparingInt(e -> e.result().getCount());
            case MOD -> Comparator.<RecipeCatalog.Entry, String>comparing(RecipeCatalog.Entry::mod)
                    .thenComparing(e -> e.result().getHoverName().getString().toLowerCase(Locale.ROOT));
        };
        if (!ascending) cmp = cmp.reversed();
        // Pin-auto-crafted: lift selected entries to the front, then apply normal ordering for the rest.
        if (persistedPinAutoCrafted && !selectedGlobalIndices.isEmpty()) {
            Comparator<RecipeCatalog.Entry> base = cmp;
            cmp = Comparator.<RecipeCatalog.Entry, Integer>comparing(e ->
                    selectedGlobalIndices.contains(e.globalIndex()) ? 0 : 1).thenComparing(base);
        }
        visibleMatches.sort(cmp);

        int maxIdx = visibleMatches.size() - 1;
        if (selectedVisibleIndex > maxIdx) selectedVisibleIndex = maxIdx;
        if (selectedVisibleIndex < 0 && !visibleMatches.isEmpty()) selectedVisibleIndex = 0;
        scrollRow = Mth.clamp(scrollRow, 0, maxScrollRow());
    }

    private static final String[] EMPTY_TOKENS = new String[0];

    /**
     * Returns true if every token in {@code tokens} appears as a substring in {@code haystack}.
     * Tokens starting with @ or # are matched literally; the haystack already embeds the mod as
     * "@modid" and each tag as "#namespace:path" and "#path", so a query like
     * "@minecraft #wool red" matches red wool items from minecraft.
     */
    private static boolean matchesAllTokens(String haystack, String[] tokens) {
        for (String token : tokens) {
            if (!token.isEmpty() && !haystack.contains(token)) return false;
        }
        return true;
    }

    private int gridRows() { return currentStyle.rows(); }

    private int maxScrollRow() {
        return Math.max(0, (visibleMatches.size() + GRID_COLS - 1) / GRID_COLS - gridRows());
    }

    private int indexAt(double mouseX, double mouseY) {
        int gx = leftPos + GRID_X;
        int gy = topPos + GRID_Y;
        int gh = gridRows() * SLOT_SIZE;
        if (mouseX < gx || mouseX >= gx + GRID_W || mouseY < gy || mouseY >= gy + gh) return -1;
        int col = ((int) mouseX - gx) / SLOT_SIZE;
        int row = ((int) mouseY - gy) / SLOT_SIZE;
        return (scrollRow + row) * GRID_COLS + col;
    }

    private static boolean inRect(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    // -----------------------------------------------------------------------------------------------
    // ENUMS
    // -----------------------------------------------------------------------------------------------

    private enum EncodingMode { CRAFTING, PROCESSING, STONECUTTING, SMITHING }

    private enum SortBy {
        NAME("Item Name A-Z"), COUNT("Number of Items"), MOD("Mod");
        private final String label;
        SortBy(String label) { this.label = label; }
        String label() { return label; }
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
        SMALL(3, "S"), MEDIUM(6, "M"), TALL(10, "T"), FULL(14, "F");
        private final int rows;
        private final String shortLabel;
        TerminalStyle(int rows, String shortLabel) { this.rows = rows; this.shortLabel = shortLabel; }
        int rows() { return rows; }
        String shortLabel() { return shortLabel; }
        TerminalStyle next() { return values()[(ordinal() + 1) % values().length]; }
    }
}
