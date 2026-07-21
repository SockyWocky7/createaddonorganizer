package com.sockywocky.createaddonorganizer.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.UnaryOperator;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sockywocky.createaddonorganizer.Config;
import com.sockywocky.createaddonorganizer.SectionCatalog;
import com.sockywocky.createaddonorganizer.createaddonorganizer;
import com.sockywocky.createaddonorganizer.createaddonorganizerClient;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

public class SectionColorsScreen extends Screen {

    private static final long FADE_MS = 300;
    private static final long TITLE_SLIDE_MS = 500;
    private static final long HINT_DELAY_MS = 300;

    private static final long[] BUTTON_DELAYS = {380, 500, 620, 740, 980, 1100, 1220, 1340, 1340, 1340, 1340, 1340};

    private static final int SIDEBAR_MIN_W = 150;
    private static final int MARGIN = 8;
    private static final long DOUBLE_CLICK_MS = 300;
    private static final long DIVIDER_DELAY_MS = 700;
    private static final long ROWS_DELAY_MS = 750;
    private static final long ROW_STAGGER_MS = 45;

    private static final int ROW_STAGGER_CAP = 12;
    private static final long ANIM_TOTAL_MS = 1500;

    private static final long GLINT_PERIOD_MS = 5000;
    private static final long GLINT_SWEEP_MS = 650;
    private static final int GLINT_COLOR = 0x00FFFFFF;
    private static final float GLINT_MAX_ALPHA = 0.9f;
    private static final float GLINT_HALO_ALPHA = 0.22f;

    private static final float GLINT_SLANT = 6f;

    private static final String UNDERTALE_LINE = "But Nobody Came.";
    private static final String KONAMI_LINE = "DONT put in the konami code";
    private static final String[] EMPTY_STATE_LINES = {
            "no tabs?",
            "You scared everything away :(",
            UNDERTALE_LINE,
            "cheeseburger",
            "nothing to see here...",
            KONAMI_LINE,
            "Add sections via \"Add Section...\" or \"Reset All\"",
    };

    private static final int[] KONAMI_SEQUENCE = {
            GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_DOWN,
            GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT,
    };
    private static final long KONAMI_CLOSE_DELAY_MS = 800;

    private final Screen parent;
    private final ModContainer container;

    // Stamped at the end of the FIRST init() rather than at construction: if building the row list
    // (SectionCatalog.colorables() etc.) is slow, capturing this before that work would let the entrance
    // animations' delay windows already be "elapsed" by the first render, skipping straight to the
    // finished state instead of animating. rebuildWidgets() re-invokes init() without re-arming this,
    // so a mid-session rebuild (e.g. from dragging a row) still doesn't replay the entrance animation.
    private long openedMillis = System.currentTimeMillis();
    private boolean animationArmed;
    private final String emptyStateText = EMPTY_STATE_LINES[new Random().nextInt(EMPTY_STATE_LINES.length)];

    private ColorList list;
    private Button saveButton;
    private final List<Button> fadingButtons = new ArrayList<>();
    private boolean orderDirty;
    private List<SectionCatalog.Entry> pendingOrder;
    private boolean noSections;
    private int listAreaTop;
    private int listAreaBottom;
    private Runnable lastUndo;
    private int konamiProgress;
    private long konamiTriggeredMillis;
    private ResourceLocation renamingId;
    private EditBox renameBox;
    private RenameIconButton renameConfirm;
    private RenameIconButton renameCancel;
    private Component hoverPreviewTooltip;

    private static double lastScroll;
    private static ResourceLocation lastSelectedId;
    private static String lastSearch = "";
    private boolean classic;
    private int listRowWidth = 320;
    private int listCenterX;
    private EditBox searchBox;
    private String searchQuery = "";
    private ResourceLocation selectedId;
    private SectionCatalog.Entry selectedEntry;
    private List<SectionCatalog.Entry> allEntries = List.of();
    private Component countLine = Component.empty();
    private Button panelEdit;
    private int sidebarX;
    private int sidebarW;
    private int selPreviewY;
    private int selContextY;
    private int sectionLabelY;
    private int resetLabelY;
    private ResourceLocation lastClickId;
    private long lastClickMillis;

    public SectionColorsScreen(Screen parent, ModContainer container) {
        super(Component.translatable("createaddonorganizer.colors.title"));
        this.parent = parent;
        this.container = container;
        this.selectedId = lastSelectedId;
        this.searchQuery = lastSearch;
    }

    @Override
    protected void init() {
        fadingButtons.clear();
        searchBox = null;
        panelEdit = null;
        classic = Config.classicOrganizerLayout();
        if (classic) {
            searchQuery = "";
        }
        double restoreScroll = list != null ? list.getScrollAmount() : lastScroll;

        int listTop;
        int listBottom;
        if (classic) {
            listRowWidth = 320;
            listCenterX = this.width / 2;
            if (DevMode.isUnlocked()) {
                fadingButtons.add(addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.addSection"),
                                b -> this.minecraft.setScreen(new AddSectionScreen(this)))
                        .bounds(this.width / 2 - 146, 64, 140, 20).build()));
                addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.assignBanners"),
                                b -> this.minecraft.setScreen(new BannerAssignmentScreen(this)))
                        .bounds(this.width / 2 + 6, 64, 140, 20).build());
            } else {
                fadingButtons.add(addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.addSection"),
                                b -> this.minecraft.setScreen(new AddSectionScreen(this)))
                        .bounds(this.width / 2 - 100, 64, 200, 20).build()));
            }
            fadingButtons.add(addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.resetOrder"),
                            b -> resetOrder())
                    .bounds(this.width / 2 - 154, 88, 92, 20).build()));
            fadingButtons.add(addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.resetAll"),
                            b -> confirmResetAll())
                    .bounds(this.width / 2 - 58, 88, 116, 20).build()));
            fadingButtons.add(addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.presets"),
                            b -> this.minecraft.setScreen(new PresetsScreen(this)))
                    .bounds(this.width / 2 + 62, 88, 92, 20).build()));
            listTop = 116;
            listBottom = this.height - 40;
        } else {
            sidebarW = Math.max(SIDEBAR_MIN_W, this.width / 3);
            sidebarX = this.width - MARGIN - sidebarW;
            listRowWidth = this.width - sidebarW - MARGIN * 3 - 24;
            listCenterX = MARGIN + listRowWidth / 2;

            buildSearchBox(MARGIN, 56, listRowWidth, 18);
            listTop = 90;
            listBottom = this.height - 12;

            initSidebar(listTop);
        }
        listAreaTop = listTop;
        listAreaBottom = listBottom;

        if (classic) {
            list = new ColorList(this.minecraft, this.width, listBottom - listTop, listTop, 24);
        } else {
            int listW = listRowWidth + 40;
            list = new ColorList(this.minecraft, listW, listBottom - listTop, listTop, 24);
            list.setX(MARGIN - 2 - (listW - listRowWidth) / 2);
        }

        List<SectionCatalog.Entry> source = orderDirty && pendingOrder != null ? pendingOrder : SectionCatalog.colorables();
        allEntries = new ArrayList<>(source);
        noSections = allEntries.isEmpty();
        for (SectionCatalog.Entry entry : filterEntries(allEntries, searchQuery)) {
            list.add(entry);
        }
        addRenderableWidget(list);
        list.setScrollAmount(restoreScroll);
        updateCountLine();

        selectedEntry = null;
        if (selectedId != null) {
            for (SectionCatalog.Entry entry : allEntries) {
                if (entry.id().equals(selectedId)) {
                    selectedEntry = entry;
                    break;
                }
            }
            if (selectedEntry == null) {
                selectedId = null;
                lastSelectedId = null;
            }
        }

        if (classic) {
            int footerY = this.height - 30;
            fadingButtons.add(addRenderableWidget(new HeartButton(this.width / 2 - 161, footerY, 20,
                    b -> this.minecraft.setScreen(new CreditsScreen(this)))));
            fadingButtons.add(addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.allSettings"),
                            b -> this.minecraft.setScreen(new ConfigurationScreen(container, this)))
                    .bounds(this.width / 2 - 137, footerY, 106, 20).build()));
            saveButton = addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.save"),
                            b -> saveOrder())
                    .bounds(this.width / 2 - 25, footerY, 90, 20).build());
            fadingButtons.add(saveButton);
            fadingButtons.add(addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                    .bounds(this.width / 2 + 71, footerY, 90, 20).build()));
        }

        if (!animationArmed) {
            animationArmed = true;
            openedMillis = System.currentTimeMillis();
        }
    }

    private void initSidebar(int listTop) {
        int pairW = sidebarW / 2 - 2;
        int pairRightX = sidebarX + sidebarW - pairW;
        int y = listTop;
        selPreviewY = y;
        y += BannerTextures.HEIGHT + 2;
        selContextY = y;
        y += 12;
        panelEdit = addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.panel.edit"),
                        b -> editSelected())
                .bounds(sidebarX, y, sidebarW, 20).build());
        fadingButtons.add(panelEdit);
        y += 24;

        sectionLabelY = y;
        y += 10;
        fadingButtons.add(addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.addSection"),
                        b -> this.minecraft.setScreen(new AddSectionScreen(this)))
                .bounds(sidebarX, y, pairW, 20).build()));
        fadingButtons.add(addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.presets"),
                        b -> this.minecraft.setScreen(new PresetsScreen(this)))
                .bounds(pairRightX, y, pairW, 20).build()));
        y += 24;
        if (DevMode.isUnlocked()) {
            addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.assignBanners"),
                            b -> this.minecraft.setScreen(new BannerAssignmentScreen(this)))
                    .bounds(sidebarX, y, sidebarW, 20).build());
            y += 24;
        }
        resetLabelY = y;
        y += 10;
        fadingButtons.add(addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.resetOrder"),
                        b -> resetOrder())
                .bounds(sidebarX, y, pairW, 20).build()));
        fadingButtons.add(addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.resetAll"),
                        b -> confirmResetAll())
                .bounds(pairRightX, y, pairW, 20).build()));

        int doneY = this.height - 28;
        int saveY = doneY - 24;
        int settingsY = saveY - 24;
        // All Settings must always be reachable -- it's the only way back to CLASSIC_ORGANIZER_LAYOUT and
        // every other option, so never hide it even if the sidebar runs out of vertical room.
        fadingButtons.add(addRenderableWidget(new HeartButton(sidebarX, settingsY, 20,
                b -> this.minecraft.setScreen(new CreditsScreen(this)))));
        fadingButtons.add(addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.allSettings"),
                        b -> this.minecraft.setScreen(new ConfigurationScreen(container, this)))
                .bounds(sidebarX + 24, settingsY, sidebarW - 24, 20).build()));
        saveButton = addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.save"),
                        b -> saveOrder())
                .bounds(sidebarX, saveY, sidebarW, 20).build());
        fadingButtons.add(saveButton);
        fadingButtons.add(addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(sidebarX, doneY, sidebarW, 20).build()));
    }

    private void buildSearchBox(int x, int y, int w, int h) {
        searchBox = new EditBox(this.font, x, y, w, h, Component.translatable("createaddonorganizer.colors.search.hint"));
        searchBox.setHint(Component.translatable("createaddonorganizer.colors.search.hint"));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchQuery);
        searchBox.setResponder(s -> {
            if (s.equals(searchQuery)) {
                return;
            }
            searchQuery = s;
            lastSearch = s;
            refreshListEntries(0);
        });
        addRenderableWidget(searchBox);
    }

    private void refreshListEntries(double scroll) {
        if (list == null) {
            return;
        }
        list.setEntries(filterEntries(allEntries, searchQuery));
        list.setScrollAmount(scroll);
        updateCountLine();
    }

    private static List<SectionCatalog.Entry> filterEntries(List<SectionCatalog.Entry> source, String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return source;
        }
        List<SectionCatalog.Entry> out = new ArrayList<>();
        int i = 0;
        while (i < source.size()) {
            SectionCatalog.Entry entry = source.get(i);
            if (!entry.parent()) {
                if (matchesQuery(entry, q)) {
                    out.add(entry);
                }
                i++;
                continue;
            }
            int end = i + 1;
            while (end < source.size() && !source.get(end).parent()) {
                end++;
            }
            boolean hubMatch = matchesQuery(entry, q);
            List<SectionCatalog.Entry> kids = new ArrayList<>();
            for (int k = i + 1; k < end; k++) {
                if (hubMatch || matchesQuery(source.get(k), q)) {
                    kids.add(source.get(k));
                }
            }
            if (hubMatch || !kids.isEmpty()) {
                out.add(entry);
                out.addAll(kids);
            }
            i = end;
        }
        return out;
    }

    private static boolean matchesQuery(SectionCatalog.Entry entry, String q) {
        return entry.name().getString().toLowerCase(Locale.ROOT).contains(q);
    }

    private void updateCountLine() {
        if (classic) {
            countLine = Component.empty();
            return;
        }
        if (searchQuery.trim().isEmpty()) {
            int hubs = 0;
            int sections = 0;
            for (SectionCatalog.Entry entry : allEntries) {
                if (entry.parent()) {
                    hubs++;
                } else {
                    sections++;
                }
            }
            countLine = Component.translatable("createaddonorganizer.colors.search.count", sections, hubs);
        } else {
            int matching = 0;
            for (ColorList.Row row : list.children()) {
                if (!row.data.parent()) {
                    matching++;
                }
            }
            countLine = Component.translatable("createaddonorganizer.colors.search.matching", matching);
        }
    }

    private void openEditor(SectionCatalog.Entry entry) {
        this.minecraft.setScreen(new ColorPickerScreen(this, entry.id(), entry.name(), entry.parent()));
    }

    private void editSelected() {
        if (selectedEntry != null && !selectedEntry.readOnly()) {
            openEditor(selectedEntry);
        }
    }

    private void clearSearch() {
        if (searchBox != null && !searchQuery.isEmpty()) {
            searchBox.setValue("");
        }
    }

    @Override
    public void removed() {
        if (list != null) {
            lastScroll = list.getScrollAmount();
        }
        lastSelectedId = selectedId;
        lastSearch = searchQuery;
        super.removed();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        saveButton.active = orderDirty;
        if (panelEdit != null) {
            panelEdit.active = selectedEntry != null && !selectedEntry.readOnly();
        }
        if (!animationDone()) {
            for (int i = 0; i < fadingButtons.size() && i < BUTTON_DELAYS.length; i++) {

                fadingButtons.get(i).setAlpha(Math.max(animProgress(BUTTON_DELAYS[i], FADE_MS), 0.04f));
            }
        }
        hoverPreviewTooltip = null;
        super.render(g, mouseX, mouseY, partialTick);
        if (hoverPreviewTooltip != null) {
            g.renderTooltip(this.font, hoverPreviewTooltip, mouseX, mouseY);
        }

        float slide = animProgress(0, TITLE_SLIDE_MS);
        int titleY = Math.round(-44 + (16 - -44) * slide);
        int titleAlpha = Math.round(255 * slide);

        if (titleAlpha >= 8) {
            Component modTitle = Component.literal(container.getModInfo().getDisplayName());
            float scale = 1.6f;
            g.pose().pushPose();
            g.pose().scale(scale, scale, scale);

            g.drawCenteredString(this.font, modTitle, Math.round(this.width / 2 / scale), Math.round(titleY / scale),
                    (titleAlpha << 24) | 0x00E4E4E4);
            renderTitleGlint(g, modTitle.getString(), scale, titleY, slide);
            g.pose().popPose();
        }

        int hintAlpha = Math.round(0xAA * animProgress(HINT_DELAY_MS, FADE_MS));
        if (hintAlpha >= 8) {
            String hintKey = classic ? "createaddonorganizer.colors.hint" : "createaddonorganizer.colors.hint2";
            g.drawCenteredString(this.font, Component.translatable(hintKey),
                    this.width / 2, classic ? 42 : 32, (hintAlpha << 24) | 0x00AAAAAA);
        }

        int dividerAlpha = Math.round(0x60 * animProgress(DIVIDER_DELAY_MS, FADE_MS));
        if (dividerAlpha >= 8) {
            if (classic) {
                g.fill(this.width / 2 - 160, 112, this.width / 2 + 160, 113, (dividerAlpha << 24) | 0x00FFFFFF);
            } else {
                g.fill(listCenterX - listRowWidth / 2, listAreaTop - 4, listCenterX + listRowWidth / 2,
                        listAreaTop - 3, (dividerAlpha << 24) | 0x00FFFFFF);
            }
        }

        if (!classic) {
            renderSidebarInfo(g);
        }

        if (noSections) {
            renderEmptyState(g);
        } else if (!classic && list.children().isEmpty()) {
            int y = listAreaTop + (listAreaBottom - listAreaTop) / 2 - 4;
            g.drawCenteredString(this.font, Component.translatable("createaddonorganizer.colors.search.none"),
                    listCenterX, y, 0xFFAAAAAA);
        }
    }

    private void renderSidebarInfo(GuiGraphics g) {
        float fade = animProgress(HINT_DELAY_MS, FADE_MS);
        if (Math.round(0xFF * fade) < 8) {
            return;
        }
        if (!noSections) {
            g.drawString(this.font, countLine, MARGIN, listAreaTop - 10,
                    (Math.round(0xAA * fade) << 24) | 0x00AAAAAA);
        }
        int labelColor = (Math.round(0x80 * fade) << 24) | 0x008F9AA5;
        if (sectionLabelY >= 0) {
            drawGroupLabel(g, Component.translatable("createaddonorganizer.colors.group.section"), sectionLabelY, labelColor);
        }
        if (resetLabelY >= 0) {
            drawGroupLabel(g, Component.translatable("createaddonorganizer.colors.group.reset"), resetLabelY, labelColor);
        }
        renderSelectionPanel(g, fade);
    }

    private void drawGroupLabel(GuiGraphics g, Component label, int y, int color) {
        String text = label.getString();
        int dashW = this.font.width("-");
        int available = Math.max(0, sidebarW - this.font.width(text) - 8);
        int dashCount = Math.max(1, available / 2 / dashW);
        String dashes = "-".repeat(dashCount);
        String full = dashes + " " + text + " " + dashes;
        g.drawCenteredString(this.font, full, sidebarX + sidebarW / 2, y, color);
    }

    private List<ResourceLocation> currentRainbowOrder() {
        List<SectionCatalog.Entry> source = orderDirty && pendingOrder != null ? pendingOrder : allEntries;
        List<ResourceLocation> ids = new ArrayList<>(source.size());
        for (SectionCatalog.Entry entry : source) {
            if (!entry.readOnly()) {
                ids.add(entry.id());
            }
        }
        return ids;
    }

    private ColorSpec previewBannerColor(ResourceLocation id) {
        if (!Config.rainbowMode()) {
            return Config.bannerColorFor(id);
        }
        List<ResourceLocation> order = currentRainbowOrder();
        return ColorSpec.solid(Config.rainbowBannerColor(order.indexOf(id), order.size()));
    }

    private ColorSpec previewTextColor(ResourceLocation id) {
        if (!Config.rainbowMode()) {
            return Config.textColorFor(id);
        }
        List<ResourceLocation> order = currentRainbowOrder();
        return ColorSpec.solid(Config.rainbowTextColor(order.indexOf(id), order.size()));
    }

    private ColorSpec previewTextSecondaryColor(ResourceLocation id) {
        ColorSpec normal = Config.textSecondaryColorFor(id);
        if (normal == null || !Config.rainbowMode()) {
            return normal;
        }
        List<ResourceLocation> order = currentRainbowOrder();
        return ColorSpec.solid(Config.rainbowTextSecondaryColor(order.indexOf(id), order.size()));
    }

    private static ColorSpec opaqueSpec(ColorSpec spec) {
        if (!spec.isGradient()) {
            return ColorSpec.solid(0xFF000000 | (spec.color1() & 0x00FFFFFF));
        }
        return new ColorSpec(0xFF000000 | (spec.color1() & 0x00FFFFFF), 0xFF000000 | (spec.color2() & 0x00FFFFFF),
                spec.direction(), spec.style());
    }

    private static ColorSpec mulAlphaSpec(ColorSpec spec, float factor) {
        if (!spec.isGradient()) {
            return ColorSpec.solid(mulAlpha(spec.color1(), factor));
        }
        return new ColorSpec(mulAlpha(spec.color1(), factor), mulAlpha(spec.color2(), factor), spec.direction(), spec.style());
    }

    private void renderSelectionPanel(GuiGraphics g, float fade) {
        if (selectedEntry == null) {
            g.drawString(this.font, Component.translatable("createaddonorganizer.colors.panel.none"),
                    sidebarX + 2, selPreviewY + 5, (Math.round(0xAA * fade) << 24) | 0x00AAAAAA);
            return;
        }
        int x = sidebarX;
        int y = selPreviewY;
        int w = sidebarW;
        int h = BannerTextures.HEIGHT;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, mulAlpha(0xFF000000, fade));

        ResourceLocation tex = null;
        String bannerRef = Config.bannerRefFor(selectedEntry.id());
        if (bannerRef != null) {
            tex = BannerTextures.resolve(bannerRef);
        }
        if (tex != null) {
            int texHeight = BannerAnimation.preview(tex, false, 1)
                    .map(BannerAnimation.AnimInfo::frameCount).orElse(1) * BannerTextures.HEIGHT;
            g.setColor(1f, 1f, 1f, fade);
            BannerTextures.blitCropped(g, tex, x, y, w, h, texHeight);
            g.setColor(1f, 1f, 1f, 1f);
        } else {
            ColorSpec bannerSpec = opaqueSpec(previewBannerColor(selectedEntry.id()));
            BannerFill.draw(g, x, y, x + w, y + h, mulAlphaSpec(bannerSpec, fade));
            g.fill(x, y, x + w, y + 1, mulAlpha(ColorUtil.brighten(bannerSpec.color1(), 0.4f), fade));
        }

        String full = selectedEntry.name().getString();
        String clipped = font.width(full) <= w - 8 ? full : font.plainSubstrByWidth(full, w - 8);
        int textY = y + (h - 8) / 2 + 1;
        ColorSpec primary = mulAlphaSpec(previewTextColor(selectedEntry.id()), fade);
        ColorSpec secondary = previewTextSecondaryColor(selectedEntry.id());
        boolean shadowOn = Config.titleTextShadow(selectedEntry.id());
        Integer shadowOverride = shadowOn ? Config.textShadowColorFor(selectedEntry.id()) : null;
        boolean vanillaShadow = shadowOn && shadowOverride == null;
        ColorSpec outline = Config.textOutlineColorFor(selectedEntry.id());
        TwoToneText.draw(g, font, Component.literal(clipped), x + 4, textY, x + w - 4, primary,
                secondary != null ? mulAlphaSpec(secondary, fade) : null,
                Config.twoToneSplitFor(selectedEntry.id()), vanillaShadow,
                shadowOverride != null ? mulAlpha(shadowOverride, fade) : 0,
                outline != null ? mulAlphaSpec(outline, fade) : null);

        Component context;
        if (selectedEntry.parent()) {
            context = Component.translatable("createaddonorganizer.colors.panel.hub");
        } else {
            ResourceLocation parentId = Config.parentFor(selectedEntry.id());
            Component parentName = Component.literal(parentId != null ? parentId.toString() : "?");
            if (parentId != null) {
                for (SectionCatalog.Entry entry : allEntries) {
                    if (entry.id().equals(parentId)) {
                        parentName = entry.name();
                        break;
                    }
                }
            }
            context = Component.translatable("createaddonorganizer.colors.panel.in", parentName);
        }
        if (selContextY >= 0) {
            String ctx = context.getString();
            String ctxClipped = font.width(ctx) <= w - 4 ? ctx : font.plainSubstrByWidth(ctx, w - 4);
            g.drawString(this.font, ctxClipped, sidebarX + 2, selContextY,
                    (Math.round(0xAA * fade) << 24) | 0x00AAAAAA);
        }
    }

    private void renderEmptyState(GuiGraphics g) {
        int y = listAreaTop + (listAreaBottom - listAreaTop) / 2 - 4;
        if (konamiTriggeredMillis != 0) {
            if (System.currentTimeMillis() - konamiTriggeredMillis >= KONAMI_CLOSE_DELAY_MS) {
                this.minecraft.setScreen(parent);
                return;
            }
            g.drawCenteredString(this.font, Component.literal("told you not to"), this.width / 2, y, 0xFFFF5555);
            return;
        }
        if (UNDERTALE_LINE.equals(emptyStateText)) {
            Component line = Component.literal(emptyStateText)
                    .setStyle(Style.EMPTY.withFont(ResourceLocation.fromNamespaceAndPath(createaddonorganizer.MODID, "undertale")));
            float scale = 1.5f;
            int width = this.font.width(line);
            g.pose().pushPose();
            g.pose().scale(scale, scale, scale);
            g.drawString(this.font, line, Math.round(this.width / 2 / scale - width / 2f), Math.round(y / scale),
                    0xFFAAAAAA, false);
            g.pose().popPose();
            return;
        }
        g.drawCenteredString(this.font, Component.literal(emptyStateText), this.width / 2, y, 0xFFAAAAAA);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (renamingId != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirmRename();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelRename();
                return true;
            }
            if (renameBox.keyPressed(keyCode, scanCode, modifiers) || renameBox.canConsumeInput()) {
                return true;
            }
        }
        if (noSections && KONAMI_LINE.equals(emptyStateText) && konamiTriggeredMillis == 0) {
            if (keyCode == KONAMI_SEQUENCE[konamiProgress]) {
                konamiProgress++;
                if (konamiProgress == KONAMI_SEQUENCE.length) {
                    konamiTriggeredMillis = System.currentTimeMillis();
                }
            } else {
                konamiProgress = keyCode == KONAMI_SEQUENCE[0] ? 1 : 0;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_Z && Screen.hasControlDown() && lastUndo != null
                && (searchBox == null || !searchBox.isFocused())) {
            Runnable undo = lastUndo;
            lastUndo = null;
            undo.run();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (renameBox != null && renameBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (renamingId != null) {
            if (renameConfirm.mouseClicked(mouseX, mouseY, button)
                    || renameCancel.mouseClicked(mouseX, mouseY, button)
                    || renameBox.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            cancelRename();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void startRename(SectionCatalog.Entry entry) {
        renamingId = entry.id();
        renameBox = new EditBox(this.font, 0, 0, 100, 20, Component.empty());
        renameBox.setMaxLength(64);
        renameBox.setValue(entry.name().getString());
        renameBox.setHighlightPos(0);
        renameBox.setFocused(true);
        renameBox.setTooltip(Tooltip.create(Component.translatable("createaddonorganizer.rename.hint")));
        renameConfirm = new RenameIconButton(true, Component.translatable("createaddonorganizer.colors.ok"),
                b -> confirmRename());
        renameCancel = new RenameIconButton(false, Component.translatable("createaddonorganizer.colors.cancel"),
                b -> cancelRename());
    }

    private void confirmRename() {
        if (renamingId == null) {
            return;
        }
        ResourceLocation id = renamingId;
        String name = renameBox.getValue().trim();
        Component title;
        if (name.isEmpty()) {
            Config.clearSectionName(id);
            CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(id);
            title = tab != null ? tab.getDisplayName() : Component.literal(id.toString());
        } else {
            Config.setSectionName(id, name);
            title = Component.literal(name);
        }
        LiveColors.applyTitle(id, title);
        list.updateName(id, title);
        UnaryOperator<SectionCatalog.Entry> retitle = e -> e.id().equals(id)
                ? new SectionCatalog.Entry(id, title, e.parent(), e.readOnly(), e.nativeTextColor(), e.nativeSecondaryTextColor())
                : e;
        allEntries.replaceAll(retitle);
        if (orderDirty && pendingOrder != null) {
            pendingOrder.replaceAll(retitle);
        }
        if (id.equals(selectedId) && selectedEntry != null) {
            selectedEntry = retitle.apply(selectedEntry);
        }
        cancelRename();
    }

    private void cancelRename() {
        renamingId = null;
        renameBox = null;
        renameConfirm = null;
        renameCancel = null;
    }

    private void renderTitleGlint(GuiGraphics g, String title, float scale, int titleY, float slide) {
        long since = System.currentTimeMillis() - openedMillis - ANIM_TOTAL_MS;
        if (since < 0) {
            return;
        }
        long inCycle = since % GLINT_PERIOD_MS;
        if (inCycle >= GLINT_SWEEP_MS) {
            return;
        }

        int textW = this.font.width(title);
        int startX = Math.round(this.width / 2 / scale) - textW / 2;
        int y = Math.round(titleY / scale);

        float t = inCycle / (float) GLINT_SWEEP_MS;
        float halfW = textW * 0.12f;
        float reach = halfW + GLINT_SLANT / 2;
        float waveX = -reach + (textW + 2 * reach) * t + startX;

        int screenTop = (int) Math.floor((y - 1) * scale);
        int screenBottom = (int) Math.ceil((y + this.font.lineHeight + 1) * scale);
        int strips = 7;

        g.pose().pushPose();

        g.pose().translate(0, 0, 1);
        for (int s = 0; s < strips; s++) {
            int sy0 = screenTop + (screenBottom - screenTop) * s / strips;
            int sy1 = screenTop + (screenBottom - screenTop) * (s + 1) / strips;
            float stripWaveX = waveX + GLINT_SLANT * (0.5f - (s + 0.5f) / strips);
            g.enableScissor(0, sy0, this.width, sy1);
            drawGlintLetters(g, title, startX, y, stripWaveX, halfW, slide);
            g.disableScissor();
        }
        g.pose().popPose();
    }

    private void drawGlintLetters(GuiGraphics g, String title, int startX, int y, float waveX, float halfW,
            float slide) {
        int x = startX;
        for (int i = 0; i < title.length(); i++) {
            String ch = title.substring(i, i + 1);
            int w = this.font.width(ch);
            float d = Math.abs((x + w / 2f) - waveX) / halfW;
            if (d < 1f) {
                float wave = (float) Math.pow(Math.cos(d * Math.PI / 2), 4);

                int haloA = Math.round(255 * GLINT_HALO_ALPHA * wave * slide);
                if (haloA >= 8) {
                    int halo = (haloA << 24) | GLINT_COLOR;
                    g.drawString(this.font, ch, x - 1, y, halo, false);
                    g.drawString(this.font, ch, x + 1, y, halo, false);
                    g.drawString(this.font, ch, x, y - 1, halo, false);
                    g.drawString(this.font, ch, x, y + 1, halo, false);
                }
                int a = Math.round(255 * GLINT_MAX_ALPHA * wave * slide);
                if (a >= 8) {

                    g.drawString(this.font, ch, x, y, (a << 24) | GLINT_COLOR, false);
                }
            }
            x += w;
        }
    }

    private boolean animationDone() {
        return System.currentTimeMillis() - openedMillis >= ANIM_TOTAL_MS;
    }

    private float animProgress(long delayMs, long durationMs) {
        float t = Mth.clamp((System.currentTimeMillis() - openedMillis - delayMs) / (float) durationMs, 0f, 1f);
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    private float rowAlpha(int rowIndex) {
        return animProgress(ROWS_DELAY_MS + Math.min(rowIndex, ROW_STAGGER_CAP) * ROW_STAGGER_MS, FADE_MS);
    }

    private static int mulAlpha(int argb, float factor) {
        int a = Math.round(((argb >>> 24) & 0xFF) * factor);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private void markOrderDirty() {
        pendingOrder = list.currentEntries();
        orderDirty = true;
    }

    private void resetOrder() {
        clearSearch();
        list.resetToAlphabetical();
    }

    private void saveOrder() {
        List<ResourceLocation> ids = new ArrayList<>();
        Map<ResourceLocation, List<ResourceLocation>> byParent = new LinkedHashMap<>();
        for (SectionCatalog.Entry entry : pendingOrder) {
            if (!entry.parent() && !entry.readOnly()) {
                ids.add(entry.id());
                byParent.computeIfAbsent(Config.parentFor(entry.id()), k -> new ArrayList<>()).add(entry.id());
            }
        }
        Config.setSectionOrder(ids);
        for (Map.Entry<ResourceLocation, List<ResourceLocation>> e : byParent.entrySet()) {
            LiveColors.applyOrder(e.getKey(), e.getValue());
        }
        refreshLiveTabLayout();
        orderDirty = false;
        pendingOrder = null;
        lastUndo = null;
        Notice.show(Component.translatable("createaddonorganizer.colors.saved"), Notice.GREEN);
    }

    private void refreshLiveTabLayout() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            createaddonorganizer.refreshTabLayout(createaddonorganizerClient.currentDisplayParams(mc));
        }
    }

    private void confirmResetAll() {
        this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                resetAllSettings();
            }
            this.minecraft.setScreen(this);
        }, Component.translatable("createaddonorganizer.colors.resetAll.title"),
                Component.translatable("createaddonorganizer.colors.resetAll.message")));
    }

    private void resetAllSettings() {
        Config.resetAllToDefault();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            createaddonorganizer.organize(createaddonorganizerClient.currentDisplayParams(mc));
        }
        List<SectionCatalog.Entry> entries = SectionCatalog.colorables();
        for (SectionCatalog.Entry entry : entries) {
            if (entry.readOnly()) {
                continue;
            }
            LiveColors.apply(entry.id(), ColorSpec.solid(Config.DEFAULT_BANNER_COLOR.get()));
            LiveColors.applyTextColor(entry.id(), ColorSpec.solid(Config.DEFAULT_TEXT_COLOR.get()));
            LiveColors.applyTitle(entry.id(), entry.name());
        }

        ResourceLocation currentParent = null;
        List<SectionCatalog.Entry> group = new ArrayList<>();
        for (SectionCatalog.Entry entry : entries) {
            if (entry.parent()) {
                applyAlphabeticalGroup(currentParent, group);
                currentParent = entry.id();
                group = new ArrayList<>();
            } else if (!entry.readOnly()) {
                group.add(entry);
            }
        }
        applyAlphabeticalGroup(currentParent, group);
        refreshLiveTabLayout();

        orderDirty = false;
        pendingOrder = null;
        rebuildWidgets();
        Notice.show(Component.translatable("createaddonorganizer.colors.resetAll.done"), Notice.GREEN);
    }

    private static void applyAlphabeticalGroup(ResourceLocation parent, List<SectionCatalog.Entry> group) {
        if (parent == null || group.isEmpty()) {
            return;
        }
        group.sort(Comparator.comparing(e -> e.name().getString(), String.CASE_INSENSITIVE_ORDER));
        List<ResourceLocation> ids = new ArrayList<>(group.size());
        for (SectionCatalog.Entry e : group) {
            ids.add(e.id());
        }
        LiveColors.applyOrder(parent, ids);
    }

    @Override
    public void onClose() {
        if (!orderDirty) {
            this.minecraft.setScreen(parent);
            return;
        }
        this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                saveOrder();
            } else {
                orderDirty = false;
                pendingOrder = null;
            }
            this.minecraft.setScreen(parent);
        }, Component.translatable("createaddonorganizer.colors.unsaved.title"),
                Component.translatable("createaddonorganizer.colors.unsaved.message"),
                Component.translatable("createaddonorganizer.colors.unsaved.save"),
                Component.translatable("createaddonorganizer.colors.unsaved.discard")) {
            @Override
            public boolean shouldCloseOnEsc() {
                return true;
            }

            @Override
            public void onClose() {
                SectionColorsScreen.this.orderDirty = false;
                SectionColorsScreen.this.pendingOrder = null;
                this.minecraft.setScreen(SectionColorsScreen.this.parent);
            }
        });
    }

    private class ColorList extends ContainerObjectSelectionList<ColorList.Row> {
        private final int rowHeight;
        private Row dragRow;
        private int dragFromIndex;
        private int dragTargetIndex;
        private ResourceLocation dragTargetParent;
        private int dragGrabOffsetY;
        private boolean dragActive;
        private boolean renderingGhost;
        private double dragStartMouseY;

        private static final long SLIDE_MS = 130;
        private static final int GHOST_BG = 0xB0101016;
        private static final int GHOST_BORDER = 0x60FFFFFF;
        private static final int EDGE_FADE_PX = 24;
        private static final int SUB_INDENT = 14;
        private static final int SUB_LINE_X = 6;
        private static final int SUB_LINE_FADE = 10;

        ColorList(Minecraft mc, int width, int height, int top, int itemHeight) {
            super(mc, width, height, top, itemHeight);
            this.rowHeight = itemHeight;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(g, mouseX, mouseY, partialTick);
            int x1 = getX();
            int x2 = getX() + getWidth();
            int top = getY();
            int bottom = getY() + getHeight();

            g.pose().pushPose();
            g.pose().translate(0, 0, 100);
            if (getScrollAmount() > 0) {
                g.fillGradient(x1, top, x2, top + EDGE_FADE_PX, 0x90000000, 0x00000000);
            }
            if (getScrollAmount() < getMaxScroll()) {
                g.fillGradient(x1, bottom - EDGE_FADE_PX, x2, bottom, 0x00000000, 0x90000000);
            }
            g.pose().popPose();

            if (dragRow != null && dragActive) {
                int left = getRowLeft();
                int w = getRowWidth();
                int gTop = ghostTop(mouseY);
                int entryH = rowHeight - 4;
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill(left - 3, gTop - 3, left + w + 3, gTop + entryH + 3, GHOST_BG);
                g.renderOutline(left - 3, gTop - 3, w + 6, entryH + 6, GHOST_BORDER);
                renderingGhost = true;
                dragRow.render(g, dragFromIndex, gTop, left, w, entryH, -1, -1, false, partialTick);
                renderingGhost = false;
                g.pose().popPose();
            }
        }

        void add(SectionCatalog.Entry entry) {
            addEntry(new Row(entry));
        }

        void setEntries(List<SectionCatalog.Entry> entries) {
            dragRow = null;
            dragActive = false;
            replaceEntries(entries.stream().map(Row::new).toList());
        }

        Row findRow(ResourceLocation id) {
            if (id == null) {
                return null;
            }
            for (Row row : children()) {
                if (row.data.id().equals(id)) {
                    return row;
                }
            }
            return null;
        }

        @Override
        public int getRowWidth() {
            return SectionColorsScreen.this.listRowWidth;
        }

        private ResourceLocation parentIdAt(int index) {
            for (int i = Math.min(index, children().size() - 1); i >= 0; i--) {
                if (children().get(i).data.parent()) {
                    return children().get(i).data.id();
                }
            }
            return null;
        }

        private boolean isLastInGroup(int index) {
            List<Row> all = children();
            return index + 1 >= all.size() || all.get(index + 1).data.parent();
        }

        private int minNonNativeInsertIndex(ResourceLocation parent) {
            List<Row> all = children();
            int start = 0;
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).data.parent() && all.get(i).data.id().equals(parent)) {
                    start = i + 1;
                    break;
                }
            }
            int i = start;
            while (i < all.size() && !all.get(i).data.parent() && all.get(i).data.readOnly()) {
                i++;
            }
            return i;
        }

        private void retarget(double mouseY) {
            List<Row> all = children();
            int slot = Math.round((ghostTop(mouseY) - getRowTop(0)) / (float) rowHeight);
            slot = Mth.clamp(slot, 0, all.size() - 1);
            int insertPos;
            if (all.get(slot).data.parent() || slot > dragFromIndex) {
                insertPos = slot + 1;
            } else {
                insertPos = slot;
            }
            dragTargetParent = parentIdAt(insertPos - 1);
            insertPos = Math.max(insertPos, minNonNativeInsertIndex(dragTargetParent));
            dragTargetIndex = insertPos;
            updateSlideTargets();
        }

        private void updateSlideTargets() {
            List<Row> all = children();
            for (int i = 0; i < all.size(); i++) {
                int target = 0;
                if (dragTargetIndex > dragFromIndex && i > dragFromIndex && i < dragTargetIndex) {
                    target = -rowHeight;
                } else if (dragTargetIndex <= dragFromIndex && i >= dragTargetIndex && i < dragFromIndex) {
                    target = rowHeight;
                }
                all.get(i).slideTo(target);
            }
        }

        private int ghostTop(double mouseY) {
            return Mth.clamp((int) mouseY - dragGrabOffsetY, getY(), getY() + getHeight() - rowHeight);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            boolean handled = super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            if (dragRow != null) {
                retarget(mouseY);
            }
            return handled;
        }

        void updateName(ResourceLocation id, Component title) {
            for (Row row : children()) {
                if (row.data.id().equals(id)) {
                    row.data = new SectionCatalog.Entry(id, title, row.data.parent(), row.data.readOnly(),
                            row.data.nativeTextColor(), row.data.nativeSecondaryTextColor());
                }
            }
        }

        List<SectionCatalog.Entry> currentEntries() {
            List<SectionCatalog.Entry> out = new ArrayList<>(children().size());
            for (Row row : children()) {
                out.add(row.data);
            }
            return out;
        }

        void resetToAlphabetical() {
            List<Row> all = children();
            int i = 0;
            while (i < all.size()) {
                int start = i + 1;
                int end = start;
                while (end < all.size() && !all.get(end).data.parent()) {
                    end++;
                }
                List<Integer> sortableIndices = new ArrayList<>();
                for (int k = start; k < end; k++) {
                    if (!all.get(k).data.readOnly()) {
                        sortableIndices.add(k);
                    }
                }
                List<Row> sortable = new ArrayList<>();
                for (int idx : sortableIndices) {
                    sortable.add(all.get(idx));
                }
                sortable.sort(Comparator.comparing(r -> r.data.name().getString(), String.CASE_INSENSITIVE_ORDER));
                for (int k = 0; k < sortableIndices.size(); k++) {
                    all.set(sortableIndices.get(k), sortable.get(k));
                }
                i = end;
            }
            SectionColorsScreen.this.markOrderDirty();
        }

        private class Row extends ContainerObjectSelectionList.Entry<Row> {
            private SectionCatalog.Entry data;
            private final Button edit;
            private float slideFrom;
            private int slideTarget;
            private long slideStart;

            private float slideOffset() {
                float t = Mth.clamp((System.currentTimeMillis() - slideStart) / (float) SLIDE_MS, 0f, 1f);
                float inv = 1f - t;
                return Mth.lerp(1f - inv * inv * inv, slideFrom, slideTarget);
            }

            private void slideTo(int target) {
                if (target == slideTarget) {
                    return;
                }
                slideFrom = slideOffset();
                slideTarget = target;
                slideStart = System.currentTimeMillis();
            }

            private void settleFrom(float from) {
                slideFrom = from;
                slideTarget = 0;
                slideStart = System.currentTimeMillis();
            }

            Row(SectionCatalog.Entry entry) {
                this.data = entry;
                this.edit = entry.readOnly() || !SectionColorsScreen.this.classic ? null
                        : Button.builder(Component.translatable("createaddonorganizer.colors.edit"),
                                        b -> SectionColorsScreen.this.openEditor(data))
                                .size(44, 20).build();
            }

            private void deleteViaShift() {
                ResourceLocation id = data.id();
                boolean wasForceIncluded = Config.isForceIncluded(id);
                ResourceLocation priorRoute = Config.parentFor(id);
                TabManager.deleteSectionConfig(id);
                ColorList.this.children().remove(this);
                SectionColorsScreen.this.allEntries.removeIf(e -> e.id().equals(id));
                if (SectionColorsScreen.this.orderDirty && SectionColorsScreen.this.pendingOrder != null) {
                    SectionColorsScreen.this.pendingOrder.removeIf(e -> e.id().equals(id));
                }
                if (id.equals(SectionColorsScreen.this.selectedId)) {
                    SectionColorsScreen.this.selectedId = null;
                    SectionColorsScreen.this.selectedEntry = null;
                    lastSelectedId = null;
                }
                SectionColorsScreen.this.updateCountLine();
                SectionColorsScreen.this.lastUndo = () -> {
                    TabManager.restoreSectionConfig(id, wasForceIncluded, priorRoute);
                    SectionColorsScreen.this.rebuildWidgets();
                };
            }

            private boolean isDeletableHub() {
                return data.parent();
            }

            private void confirmDeleteMainSection() {
                Component message = Config.isBuiltinHub(data.id())
                        ? Component.translatable("createaddonorganizer.colors.deleteMain.builtinMessage", data.name())
                        : Component.translatable("createaddonorganizer.colors.deleteMain.message", data.name());
                minecraft.setScreen(new ConfirmScreen(confirmed -> {
                    if (confirmed) {
                        deleteMainSection();
                    }
                    minecraft.setScreen(SectionColorsScreen.this);
                }, Component.translatable("createaddonorganizer.colors.deleteMain.title"), message));
            }

            private void deleteMainSection() {
                ResourceLocation id = data.id();
                boolean wasForceExcludedBefore = Config.isForceExcluded(id);
                List<ResourceLocation> routedHere = Config.subSectionsRoutedTo(id);

                Config.removeExtraMainSection(id);
                if (Config.isBuiltinHub(id)) {
                    Config.addForceExclude(id);
                }
                Config.clearRoutesTo(id);
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null && mc.player != null) {
                    createaddonorganizer.reapplyAbsorption(createaddonorganizerClient.currentDisplayParams(mc));
                }
                SectionColorsScreen.this.orderDirty = false;
                SectionColorsScreen.this.pendingOrder = null;

                SectionColorsScreen.this.lastUndo = () -> {
                    if (!wasForceExcludedBefore) {
                        Config.removeForceExclude(id);
                    }
                    Config.addExtraMainSection(id);
                    createaddonorganizer.MANAGED_PARENTS.add(id);
                    for (ResourceLocation subId : routedHere) {
                        Config.setRoute(subId, id);
                    }
                    Minecraft mc2 = Minecraft.getInstance();
                    if (mc2.level != null && mc2.player != null) {
                        createaddonorganizer.reapplyAbsorption(createaddonorganizerClient.currentDisplayParams(mc2));
                    }
                    SectionColorsScreen.this.rebuildWidgets();
                };

                SectionColorsScreen.this.rebuildWidgets();
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return edit != null ? List.of(edit) : List.of();
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return edit != null ? List.of(edit) : List.of();
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (super.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
                if (button == 0 && !SectionColorsScreen.this.classic && !Screen.hasShiftDown() && !Screen.hasControlDown()) {
                    boolean sameRow = data.id().equals(SectionColorsScreen.this.lastClickId)
                            && System.currentTimeMillis() - SectionColorsScreen.this.lastClickMillis < DOUBLE_CLICK_MS;
                    SectionColorsScreen.this.lastClickId = data.id();
                    SectionColorsScreen.this.lastClickMillis = System.currentTimeMillis();
                    SectionColorsScreen.this.selectedId = data.id();
                    SectionColorsScreen.this.selectedEntry = data;
                    lastSelectedId = data.id();
                    if (sameRow && !data.readOnly()) {
                        SectionColorsScreen.this.openEditor(data);
                        return true;
                    }
                }
                if (data.readOnly()) {
                    return false;
                }
                if (button == 0 && Screen.hasShiftDown() && !data.parent()) {
                    deleteViaShift();
                    return true;
                }
                if (button == 0 && Screen.hasShiftDown() && isDeletableHub()) {
                    confirmDeleteMainSection();
                    return true;
                }
                if (button == 0 && Screen.hasControlDown()) {
                    SectionColorsScreen.this.startRename(data);
                    return true;
                }
                if (button == 0 && !data.parent() && SectionColorsScreen.this.searchQuery.trim().isEmpty()) {
                    dragRow = this;
                    dragFromIndex = dragTargetIndex = ColorList.this.children().indexOf(this);
                    ColorList.this.dragTargetParent = ColorList.this.parentIdAt(dragFromIndex);
                    ColorList.this.dragGrabOffsetY = (int) (mouseY - ColorList.this.getRowTop(dragFromIndex));
                    ColorList.this.dragStartMouseY = mouseY;
                    ColorList.this.dragActive = false;
                    ColorList.this.updateSlideTargets();
                    return true;
                }
                return false;
            }

            @Override
            public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
                if (dragRow != this) {
                    return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
                }
                if (!ColorList.this.dragActive && Math.abs(mouseY - ColorList.this.dragStartMouseY) > 4) {
                    ColorList.this.dragActive = true;
                }
                ColorList.this.retarget(mouseY);
                return true;
            }

            @Override
            public boolean mouseReleased(double mouseX, double mouseY, int button) {
                if (dragRow != this) {
                    return super.mouseReleased(mouseX, mouseY, button);
                }
                List<Row> all = ColorList.this.children();
                Map<Row, Float> shownTops = new LinkedHashMap<>();
                if (ColorList.this.dragActive) {
                    for (int i = 0; i < all.size(); i++) {
                        Row r = all.get(i);
                        shownTops.put(r, r == dragRow
                                ? (float) ColorList.this.ghostTop(mouseY)
                                : ColorList.this.getRowTop(i) + r.slideOffset());
                    }
                }
                if (dragTargetIndex != dragFromIndex) {
                    ResourceLocation originParent = ColorList.this.parentIdAt(dragFromIndex);
                    ResourceLocation targetParent = ColorList.this.dragTargetParent;

                    int insertAt = dragTargetIndex > dragFromIndex ? dragTargetIndex - 1 : dragTargetIndex;
                    Row moved = ColorList.this.children().remove(dragFromIndex);
                    insertAt = Mth.clamp(insertAt, 0, ColorList.this.children().size());
                    ColorList.this.children().add(insertAt, moved);

                    if (targetParent != null && !targetParent.equals(originParent)) {
                        moveToHub(targetParent);
                    } else {
                        SectionColorsScreen.this.markOrderDirty();
                    }
                }
                if (ColorList.this.dragActive) {
                    for (int i = 0; i < all.size(); i++) {
                        Row r = all.get(i);
                        r.settleFrom(shownTops.get(r) - ColorList.this.getRowTop(i));
                    }
                }
                dragRow = null;
                ColorList.this.dragActive = false;
                return true;
            }

            private void moveToHub(ResourceLocation newParent) {
                if (createaddonorganizer.CREATE_BASE.equals(newParent)) {
                    Config.clearRoute(data.id());
                } else {
                    Config.setRoute(data.id(), newParent);
                }
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null && mc.player != null) {
                    LiveColors.moveToParent(data.id(), newParent);
                    createaddonorganizer.refreshTabLayout(createaddonorganizerClient.currentDisplayParams(mc));
                }
                SectionColorsScreen.this.markOrderDirty();
            }

            @Override
            public void render(GuiGraphics g, int index, int top, int left, int rowWidth, int rowHeight,
                    int mouseX, int mouseY, boolean hovered, float partialTick) {

                if (dragRow == this && ColorList.this.dragActive && !ColorList.this.renderingGhost) {
                    return;
                }
                if (!ColorList.this.renderingGhost) {
                    top += Math.round(slideOffset());
                }

                float alpha = animationDone() ? 1f : rowAlpha(index);

                if (!ColorList.this.renderingGhost) {
                    int listTop = ColorList.this.getY();
                    int listBottom = ColorList.this.getBottom();
                    float edgeFade = 1f;
                    if (ColorList.this.getScrollAmount() > 0 && top < listTop + EDGE_FADE_PX) {
                        edgeFade = Mth.clamp((top + rowHeight - listTop) / (float) EDGE_FADE_PX, 0f, 1f);
                    }
                    if (ColorList.this.getScrollAmount() < ColorList.this.getMaxScroll() && top + rowHeight > listBottom - EDGE_FADE_PX) {
                        edgeFade = Math.min(edgeFade, Mth.clamp((listBottom - top) / (float) EDGE_FADE_PX, 0f, 1f));
                    }
                    alpha *= edgeFade;
                }

                if (alpha <= 0.03f) {
                    return;
                }

                if (data.parent()) {
                    Integer highlight = Config.highlightColorFor(data.id());
                    int band = highlight != null ? (0x2A << 24) | (highlight & 0x00FFFFFF) : 0x2AFFFFFF;
                    int divider = highlight != null ? (0x60 << 24) | (highlight & 0x00FFFFFF) : 0x60FFFFFF;
                    g.fill(left, top, left + rowWidth, top + rowHeight, mulAlpha(band, alpha));
                    if (index != 0) {
                        g.fill(left, top, left + rowWidth, top + 1, mulAlpha(divider, alpha));
                    }
                } else if (!ColorList.this.renderingGhost) {
                    ResourceLocation groupParent = ColorList.this.parentIdAt(index);
                    Integer highlight = groupParent != null ? Config.highlightColorFor(groupParent) : null;
                    int lineColor = highlight != null ? (0x90 << 24) | (highlight & 0x00FFFFFF) : 0x50FFFFFF;
                    int lineX = left + SUB_LINE_X;
                    int lineBottom = top + rowHeight;
                    int gapToNext = ColorList.this.rowHeight - rowHeight;
                    if (ColorList.this.isLastInGroup(index)) {
                        int fadeLen = Math.min(SUB_LINE_FADE, rowHeight);
                        int fadeStart = lineBottom - fadeLen;
                        if (fadeStart > top) {
                            g.fill(lineX, top, lineX + 2, fadeStart, mulAlpha(lineColor, alpha));
                        }
                        g.fillGradient(lineX, fadeStart, lineX + 2, lineBottom,
                                mulAlpha(lineColor, alpha), mulAlpha(lineColor, 0f));
                    } else {
                        g.fill(lineX, top, lineX + 2, lineBottom + gapToNext, mulAlpha(lineColor, alpha));
                    }
                }


                int contentLeft = data.parent() ? left : left + SUB_INDENT;

                int textY = top + (rowHeight - 8) / 2;

                if (data.readOnly()) {
                    int previewW = 48;
                    int phantomEditX = left + rowWidth - 44;
                    String tag = Component.translatable("createaddonorganizer.colors.native").getString();
                    int tagX = phantomEditX - 10 - font.width(tag);
                    int nameX = contentLeft + previewW + 8;
                    int nameMaxWidth = tagX - 4 - nameX;
                    Component name = truncatedName(data, nameMaxWidth);
                    int primary = mulAlpha(data.nativeTextColor() != null ? data.nativeTextColor() : 0xFFAAAAAA, alpha);
                    int secondary = mulAlpha(data.nativeSecondaryTextColor() != null ? data.nativeSecondaryTextColor() : 0xFF777777, alpha);
                    TwoToneText.draw(g, font, name, nameX, textY, primary, secondary);
                    g.drawString(font, tag, tagX, textY, mulAlpha(0xFFAAAAAA, alpha));
                    return;
                }

                int previewW = 48;
                String previewTooltip;
                int previewX1;
                int previewY1;
                int previewX2;
                int previewY2;
                String bannerRef = Config.bannerRefFor(data.id());
                if (bannerRef != null) {
                    int th = BannerTextures.HEIGHT;
                    int ty = top + (rowHeight - th) / 2;
                    g.fill(contentLeft - 1, ty - 1, contentLeft + previewW + 1, ty + th + 1, mulAlpha(0xFF000000, alpha));
                    ResourceLocation tex = BannerTextures.resolve(bannerRef);
                    if (tex != null) {

                        int texHeight = BannerAnimation.preview(tex, false, 1)
                                .map(BannerAnimation.AnimInfo::frameCount).orElse(1) * BannerTextures.HEIGHT;
                        g.setColor(1f, 1f, 1f, alpha);
                        BannerTextures.blitCropped(g, tex, contentLeft, ty, previewW, th, texHeight);
                        g.setColor(1f, 1f, 1f, 1f);
                    }
                    previewTooltip = Component.translatable("createaddonorganizer.banner.mode.image").getString();
                    previewX1 = contentLeft - 1;
                    previewY1 = ty - 1;
                    previewX2 = contentLeft + previewW + 1;
                    previewY2 = ty + th + 1;
                } else {
                    int swatch = 16;
                    int sy = top + (rowHeight - swatch) / 2;
                    int sx = contentLeft + (previewW - swatch) / 2;
                    ColorSpec bannerSpec = opaqueSpec(previewBannerColor(data.id()));
                    g.fill(sx, sy, sx + swatch, sy + swatch, mulAlpha(0xFF000000, alpha));
                    BannerFill.draw(g, sx + 1, sy + 1, sx + swatch - 1, sy + swatch - 1, mulAlphaSpec(bannerSpec, alpha));
                    previewTooltip = Config.formatColorSpec(bannerSpec, true);
                    previewX1 = sx;
                    previewY1 = sy;
                    previewX2 = sx + swatch;
                    previewY2 = sy + swatch;
                }

                if (mouseX >= previewX1 && mouseX < previewX2 && mouseY >= previewY1 && mouseY < previewY2) {
                    SectionColorsScreen.this.hoverPreviewTooltip = Component.literal(previewTooltip);
                }

                int widgetY = top + (rowHeight - 20) / 2;
                int actionX = left + rowWidth;
                if (edit != null) {
                    edit.setX(left + rowWidth - edit.getWidth());
                    edit.setY(widgetY);
                    actionX = edit.getX();
                }
                int nameX = contentLeft + previewW + 8;

                if (data.id().equals(renamingId)) {
                    float widgetAlpha = Math.max(alpha, 0.04f);
                    renameCancel.setX(actionX - 22);
                    renameCancel.setY(widgetY);
                    renameConfirm.setX(renameCancel.getX() - 22);
                    renameConfirm.setY(widgetY);
                    renameBox.setX(nameX - 4);
                    renameBox.setY(widgetY);
                    renameBox.setWidth(renameConfirm.getX() - 4 - nameX + 4);
                    renameBox.render(g, mouseX, mouseY, partialTick);
                    renameConfirm.setAlpha(widgetAlpha);
                    renameCancel.setAlpha(widgetAlpha);
                    renameConfirm.render(g, mouseX, mouseY, partialTick);
                    renameCancel.render(g, mouseX, mouseY, partialTick);
                } else {
                    int nameMaxWidth = actionX - 10 - nameX;
                    Component name = truncatedName(data, nameMaxWidth);
                    ColorSpec primary = mulAlphaSpec(previewTextColor(data.id()), alpha);
                    ColorSpec secondary = previewTextSecondaryColor(data.id());
                    boolean shadowOn = Config.titleTextShadow(data.id());
                    Integer shadowOverride = shadowOn ? Config.textShadowColorFor(data.id()) : null;
                    boolean vanillaShadow = shadowOn && shadowOverride == null;
                    ColorSpec outline = Config.textOutlineColorFor(data.id());
                    TwoToneText.draw(g, font, name, nameX, textY, nameX + nameMaxWidth, primary,
                            secondary != null ? mulAlphaSpec(secondary, alpha) : null,
                            Config.twoToneSplitFor(data.id()), vanillaShadow,
                            shadowOverride != null ? mulAlpha(shadowOverride, alpha) : 0,
                            outline != null ? mulAlphaSpec(outline, alpha) : null);
                }

                if (edit != null) {
                    edit.setAlpha(Math.max(alpha, 0.04f));
                    edit.render(g, mouseX, mouseY, partialTick);
                }

                if (Screen.hasShiftDown() && hovered && (!data.parent() || isDeletableHub())) {
                    g.fill(left, top, left + rowWidth, top + rowHeight, mulAlpha(0x80AA2E24, alpha));
                }
            }

            private Component truncatedName(SectionCatalog.Entry entry, int maxWidth) {
                String full = entry.name().getString();
                Component base;
                if (font.width(full) <= maxWidth) {
                    base = entry.name();
                } else {
                    String ellipsis = "...";
                    int budget = Math.max(0, maxWidth - font.width(ellipsis));
                    base = Component.literal(font.plainSubstrByWidth(full, budget) + ellipsis);
                }
                return entry.parent() ? base.copy().withStyle(ChatFormatting.BOLD) : base;
            }
        }
    }

    private static class HeartButton extends Button {
        private static final ResourceLocation HEART_ICON = ResourceLocation.withDefaultNamespace("hud/heart/full");
        private static final int ICON_SIZE = 9;

        HeartButton(int x, int y, int size, OnPress onPress) {
            super(x, y, size, size, Component.empty(), onPress, DEFAULT_NARRATION);
            setTooltip(Tooltip.create(Component.translatable("createaddonorganizer.colors.credits")));
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(g, mouseX, mouseY, partialTick);
            int ix = getX() + (getWidth() - ICON_SIZE) / 2 + 1;
            int iy = getY() + (getHeight() - ICON_SIZE) / 2 + 1;

            RenderSystem.enableBlend();
            g.setColor(0f, 0f, 0f, this.alpha);
            g.blitSprite(HEART_ICON, ix - 1, iy, ICON_SIZE, ICON_SIZE);
            g.blitSprite(HEART_ICON, ix + 1, iy, ICON_SIZE, ICON_SIZE);
            g.blitSprite(HEART_ICON, ix, iy - 1, ICON_SIZE, ICON_SIZE);
            g.blitSprite(HEART_ICON, ix, iy + 1, ICON_SIZE, ICON_SIZE);

            g.setColor(1f, 1f, 1f, this.alpha);
            g.blitSprite(HEART_ICON, ix, iy, ICON_SIZE, ICON_SIZE);
            g.setColor(1f, 1f, 1f, 1f);
        }
    }
}
