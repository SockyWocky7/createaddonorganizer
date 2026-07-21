package com.sockywocky.createaddonorganizer.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import com.sockywocky.createaddonorganizer.Config;
import com.sockywocky.createaddonorganizer.client.simulated.SimulatedHub;
import com.sockywocky.createaddonorganizer.client.simulated.SimulatedSupport;
import com.sockywocky.createaddonorganizer.mixin.CreativeModeInventoryScreenAccessor;

public final class SectionIndexPanel {
    private SectionIndexPanel() {}

    private static final long ANIM_MS = 250;

    private static final ResourceLocation TOGGLE_TEXTURE_COLLAPSED =
            ResourceLocation.fromNamespaceAndPath("createaddonorganizer", "textures/gui/sidebar_toggle_collapsed.png");
    private static final ResourceLocation TOGGLE_TEXTURE_EXPANDED =
            ResourceLocation.fromNamespaceAndPath("createaddonorganizer", "textures/gui/sidebar_toggle_expanded.png");
    private static final int TOGGLE_LEFT = 6;
    private static final int TOGGLE_SIZE = 8;
    private static final int TOGGLE_HIT_PAD = 3;

    private static final int TOGGLE_TEXT_GAP = 3;
    private static final int TOGGLE_HOVER = 0x50FFFFFF;

    private static boolean expanded = false;

    private static float panelScroll = 0f;
    private static Config.IndexPanelStyle lastStyle = null;

    private static long animStart = -1;
    private static float animFrom;
    private static float animTo;

    private static ResourceLocation lastTabId = null;
    private static boolean simulatedTab = false;
    private static int lastSimVersion = -1;

    private static List<ResourceLocation> ids = null;
    private static List<Component> titles = List.of();
    private static List<ItemStack> icons = List.of();

    private static IndexPanelSkin skin() {
        Config.IndexPanelStyle style = Config.indexPanelStyle();
        if (style != lastStyle) {
            lastStyle = style;
            panelScroll = 0f;
        }
        return switch (style) {
            case VANILLA -> ClassicPanelSkin.VANILLA;
            case DARK -> ClassicPanelSkin.DARK;
            case REFURBISHED -> RefurbishedPanelSkin.INSTANCE;
            case BACKPORT -> BackportPanelSkin.INSTANCE;
        };
    }

    private static IndexPanelSkin.View view(CreativeModeInventoryScreen screen, boolean withSelection) {
        return new IndexPanelSkin.View(ids == null ? 0 : ids.size(), icons, expanded, panelScroll,
                withSelection ? selectedIndex(screen) : -1);
    }

    private static IndexPanelSkin.Rect toggleRect(CreativeModeInventoryScreen screen) {
        int y = screen.getGuiTop() + 6 + (8 - TOGGLE_SIZE) / 2;
        return new IndexPanelSkin.Rect(screen.getGuiLeft() + TOGGLE_LEFT, y, TOGGLE_SIZE, TOGGLE_SIZE);
    }

    private static IndexPanelSkin.Rect toggleHitRect(CreativeModeInventoryScreen screen) {
        IndexPanelSkin.Rect r = toggleRect(screen);
        return new IndexPanelSkin.Rect(r.x() - TOGGLE_HIT_PAD, r.y() - TOGGLE_HIT_PAD,
                r.w() + 2 * TOGGLE_HIT_PAD, r.h() + 2 * TOGGLE_HIT_PAD);
    }

    public static void render(CreativeModeInventoryScreen screen, GuiGraphics gg, int mouseX, int mouseY) {
        refresh();
        if (ids == null) {
            return;
        }
        tickAnimation(screen);
        IndexPanelSkin skin = skin();
        panelScroll = Mth.clamp(panelScroll, 0f, skin.maxScroll(screen, ids.size()));

        IndexPanelSkin.View view = view(screen, true);
        skin.render(screen, gg, view, mouseX, mouseY);

        boolean toggleHovered = toggleHitRect(screen).contains(mouseX, mouseY);
        IndexPanelSkin.Rect toggle = toggleRect(screen);
        ResourceLocation toggleTexture = expanded ? TOGGLE_TEXTURE_EXPANDED : TOGGLE_TEXTURE_COLLAPSED;
        gg.blit(toggleTexture, toggle.x(), toggle.y(), 0f, 0f, toggle.w(), toggle.h(), toggle.w(), toggle.h());
        if (toggleHovered) {
            gg.fill(toggle.x(), toggle.y(), toggle.x() + toggle.w(), toggle.y() + toggle.h(), TOGGLE_HOVER);
        }

        var font = Minecraft.getInstance().font;
        if (toggleHovered) {
            gg.renderTooltip(font, Component.translatable("createaddonorganizer.index.toggle"), mouseX, mouseY);
            return;
        }
        IndexPanelSkin.Hit hit = skin.hitTest(screen, view, mouseX, mouseY);
        if (hit instanceof IndexPanelSkin.Hit.Entry entry) {
            gg.renderTooltip(font, titles.get(entry.sectionIndex()), mouseX, mouseY);
        }
    }

    public static boolean mouseClicked(CreativeModeInventoryScreen screen, double mouseX, double mouseY, int button) {
        refresh();
        if (ids == null) {
            return false;
        }
        if (toggleHitRect(screen).contains(mouseX, mouseY)) {
            if (button == 0) {
                expanded = !expanded;
                playClick();
            }
            return true;
        }
        IndexPanelSkin skin = skin();
        switch (skin.hitTest(screen, view(screen, false), mouseX, mouseY)) {
            case IndexPanelSkin.Hit.Entry entry -> {
                if (button == 0) {
                    jumpTo(screen, ids.get(entry.sectionIndex()));
                    playClick();
                }
            }
            case IndexPanelSkin.Hit.ScrollUp ignored -> {
                if (button == 0) {
                    panelScroll = Mth.clamp(panelScroll - 1f, 0f, skin.maxScroll(screen, ids.size()));
                    playClick();
                }
            }
            case IndexPanelSkin.Hit.ScrollDown ignored -> {
                if (button == 0) {
                    panelScroll = Mth.clamp(panelScroll + 1f, 0f, skin.maxScroll(screen, ids.size()));
                    playClick();
                }
            }
            case IndexPanelSkin.Hit.PanelBody ignored -> {

            }
            case IndexPanelSkin.Hit.None ignored -> {
                cancelAnimation();
                return false;
            }
        }
        return true;
    }

    public static boolean mouseReleased(CreativeModeInventoryScreen screen, double mouseX, double mouseY) {
        refresh();
        if (ids == null) {
            return false;
        }
        if (toggleHitRect(screen).contains(mouseX, mouseY)) {
            return true;
        }
        IndexPanelSkin skin = skin();
        return !(skin.hitTest(screen, view(screen, false), mouseX, mouseY) instanceof IndexPanelSkin.Hit.None);
    }

    public static boolean mouseScrolled(CreativeModeInventoryScreen screen, double mouseX, double mouseY, double scrollY) {
        refresh();
        if (ids == null || !expanded) {
            return false;
        }
        IndexPanelSkin skin = skin();
        if (skin.wheelOver(screen, view(screen, false), mouseX, mouseY)) {
            panelScroll = skin.snap(Mth.clamp(panelScroll - (float) (scrollY * skin.wheelStep()),
                    0f, skin.maxScroll(screen, ids.size())));
            return true;
        }
        cancelAnimation();
        return false;
    }

    private static Integer rowOf(ResourceLocation id) {
        return simulatedTab ? SimulatedHub.rowOf(id) : null;
    }

    private static int selectedIndex(CreativeModeInventoryScreen screen) {
        int rows = Mth.positiveCeilDiv(screen.getMenu().items.size(), 9) - 5;
        int topRow = rows <= 0 ? 0
                : Math.round(((CreativeModeInventoryScreenAccessor) screen).getScrollOffs() * rows);
        int sel = -1;
        for (int i = 0; i < ids.size(); i++) {
            Integer row = rowOf(ids.get(i));
            if (row == null) {
                return -1;
            }
            if (row <= topRow) {
                sel = i;
            } else {
                break;
            }
        }
        return sel;
    }

    private static void jumpTo(CreativeModeInventoryScreen screen, ResourceLocation id) {
        Integer row = rowOf(id);
        if (row == null) {
            return;
        }

        int rows = Mth.positiveCeilDiv(screen.getMenu().items.size(), 9) - 5;
        if (rows <= 0) {
            return;
        }
        animFrom = ((CreativeModeInventoryScreenAccessor) screen).getScrollOffs();
        animTo = Mth.clamp(row / (float) rows, 0f, 1f);
        animStart = System.currentTimeMillis();
    }

    private static void tickAnimation(CreativeModeInventoryScreen screen) {
        if (animStart < 0) {
            return;
        }
        float t = Mth.clamp((System.currentTimeMillis() - animStart) / (float) ANIM_MS, 0f, 1f);
        float inv = 1f - t;
        float eased = 1f - inv * inv * inv;
        float s = Mth.lerp(eased, animFrom, animTo);
        ((CreativeModeInventoryScreenAccessor) screen).setScrollOffs(s);
        screen.getMenu().scrollTo(s);
        if (t >= 1f) {
            animStart = -1;
        }
    }

    private static void cancelAnimation() {
        animStart = -1;
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    public static boolean active() {
        // FTS 6.0 renders its own section-index panel for tabs it registers; this panel now
        // only covers Simulated-hub tabs, which aren't FTS sections.
        ResourceLocation tabId = selectedTabId();
        if (tabId == null) {
            return false;
        }
        return SimulatedSupport.isLoaded() && SimulatedSupport.isMainTab(tabId);
    }

    public static int titleX() {
        return TOGGLE_LEFT + TOGGLE_SIZE + TOGGLE_TEXT_GAP;
    }

    private static ResourceLocation selectedTabId() {
        CreativeModeTab tab = CreativeModeInventoryScreenAccessor.getSelectedTab();
        return tab == null ? null : BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
    }

    private static void refresh() {
        ResourceLocation tabId = selectedTabId();
        boolean simTab = tabId != null && SimulatedSupport.isLoaded() && SimulatedSupport.isMainTab(tabId);

        if (!simTab) {
            // FTS 6.0 owns the index panel for its own registered tabs now; nothing for us to show.
            simulatedTab = false;
            lastSimVersion = -1;
            if (!Objects.equals(tabId, lastTabId)) {
                lastTabId = tabId;
                cancelAnimation();
                panelScroll = 0f;
            }
            ids = null;
            titles = List.of();
            icons = List.of();
            return;
        }

        int version = SimulatedHub.stateVersion();
        boolean tabChanged = !Objects.equals(tabId, lastTabId);
        if (!tabChanged && simulatedTab && version == lastSimVersion) {
            return;
        }
        simulatedTab = true;
        lastTabId = tabId;
        lastSimVersion = version;
        if (tabChanged) {
            cancelAnimation();
            panelScroll = 0f;
        }
        List<SimulatedHub.IndexEntry> entries = SimulatedHub.allSectionsInOrder();
        List<ResourceLocation> newIds = new ArrayList<>(entries.size());
        List<Component> newTitles = new ArrayList<>(entries.size());
        List<ItemStack> newIcons = new ArrayList<>(entries.size());
        for (SimulatedHub.IndexEntry entry : entries) {
            newIds.add(entry.id());
            newTitles.add(entry.title());
            newIcons.add(SimulatedHub.iconFor(entry.id(), entry.owned()));
        }
        ids = newIds;
        titles = newTitles;
        icons = newIcons;
    }
}
