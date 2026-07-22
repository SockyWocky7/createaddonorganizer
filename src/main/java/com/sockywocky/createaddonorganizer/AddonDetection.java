package com.sockywocky.createaddonorganizer;

import java.util.Set;

import net.mcexpanded.fancytabsections.FancyTabSections;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

import com.sockywocky.createaddonorganizer.client.simulated.SimulatedSupport;

public final class AddonDetection {
    static final String CREATE = "create";
    private static final String MINECRAFT = "minecraft";

    private AddonDetection() {}

    public static boolean isAbsorbTarget(ResourceLocation tabId) {
        return skipReason(tabId) == null;
    }

    public static String skipReason(ResourceLocation tabId) {
        String ns = tabId.getNamespace();
        if (CREATE.equals(ns)) {
            return "Create-owned tab";
        }
        if (SimulatedSupport.isLoaded() && SimulatedSupport.isMainTab(tabId)) {
            return "Simulated's own main tab, never an absorb target";
        }
        if (Config.isForceExcluded(tabId)) {
            return "force-excluded in config";
        }
        if (Config.parentFor(tabId) == null) {
            return "target hub is excluded";
        }
        if (Config.isForceIncluded(tabId) || AddonGroups.isMember(tabId)) {
            return null;
        }
        if (FancyTabSections.REGISTERED_TABS.containsKey(tabId)) {
            return "already organized with Fancy Tab Sections";
        }
        ModContainer container = ModList.get().getModContainerById(ns).orElse(null);
        if (container == null) {
            return "no loaded mod owns namespace '" + ns + "'";
        }
        boolean dependsOnSimulated = SimulatedSupport.isLoaded() && dependsOn(container, SimulatedSupport.MOD_ID);
        if (!dependsOn(container, CREATE) && !dependsOnSimulated) {
            return "mod '" + ns + "' declares no Create or Simulated dependency";
        }
        return null;
    }

    public static boolean dependsOn(ResourceLocation tabId, String modId) {
        ModContainer container = ModList.get().getModContainerById(tabId.getNamespace()).orElse(null);
        return container != null && dependsOn(container, modId);
    }

    public static boolean isSubSectionCandidate(ResourceLocation id) {
        return isSubSectionCandidate(id, SectionCatalog.knownHubs());
    }

    public static boolean isSubSectionCandidate(ResourceLocation id, Set<ResourceLocation> knownHubs) {
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(id);
        return tab != null && tab.getType() == CreativeModeTab.Type.CATEGORY
                && !CREATE.equals(id.getNamespace())
                && !MINECRAFT.equals(id.getNamespace())
                && !knownHubs.contains(id)
                && !Config.isBuiltinExcluded(id)
                && !SimulatedSupport.isMainTab(id);
    }

    public static boolean isHubPromotionCandidate(ResourceLocation id) {
        return isHubPromotionCandidate(id, SectionCatalog.knownHubs());
    }

    public static boolean isHubPromotionCandidate(ResourceLocation id, Set<ResourceLocation> knownHubs) {
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(id);
        return tab != null && tab.getType() == CreativeModeTab.Type.CATEGORY
                && !MINECRAFT.equals(id.getNamespace())
                && !knownHubs.contains(id)
                && !Config.isBuiltinExcluded(id);
    }

    public static boolean isPlaced(ResourceLocation id) {
        return AbsorbedTabs.IDS.contains(id) || isAbsorbTarget(id);
    }

    private static boolean dependsOn(ModContainer container, String modId) {
        for (IModInfo.ModVersion dependency : container.getModInfo().getDependencies()) {
            if (modId.equals(dependency.getModId())) {
                return true;
            }
        }
        return false;
    }
}
