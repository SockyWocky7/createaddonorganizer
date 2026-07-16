package com.sockywocky.createaddonorganizer.client;

import java.util.List;

import net.mcexpanded.fancytabsections.FTSInternal;
import net.mcexpanded.fancytabsections.FancyTabSections;
import net.mcexpanded.fancytabsections.Section.Section;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;

import com.sockywocky.createaddonorganizer.Config;
import com.sockywocky.createaddonorganizer.mixin.CreativeModeInventoryScreenAccessor;

public final class CollapseSync {
    private CollapseSync() {}

    public static void tick() {
        if (!Config.showCollapseToggle()) {
            return;
        }
        CreativeModeTab tab = CreativeModeInventoryScreenAccessor.getSelectedTab();
        ResourceLocation tabId = tab == null ? null : BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
        if (tabId == null) {
            return;
        }
        List<Section<?>> sections = FancyTabSections.REGISTERED_TABS.get(tabId);
        if (sections == null) {
            return;
        }
        for (Section<?> section : sections) {
            boolean collapsed = FTSInternal.isCollapsed(section);
            if (collapsed != Config.isSectionCollapsed(section.id())) {
                Config.setSectionCollapsed(section.id(), collapsed);
            }
        }
    }
}
