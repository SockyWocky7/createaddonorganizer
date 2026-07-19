package com.sockywocky.createaddonorganizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.sockywocky.createaddonorganizer.client.BannerTextures;
import com.sockywocky.createaddonorganizer.client.CaoSection;
import com.sockywocky.createaddonorganizer.client.LiveColors;
import com.sockywocky.createaddonorganizer.client.simulated.SimulatedHub;
import com.sockywocky.createaddonorganizer.client.simulated.SimulatedSupport;
import com.sockywocky.createaddonorganizer.mixin.CreativeModeInventoryScreenAccessor;
import com.sockywocky.createaddonorganizer.mixin.CreativeModeTabsAccessor;
import com.sockywocky.createaddonorganizer.mixin.ItemPickerMenuAccessor;

import net.mcexpanded.fancytabsections.FTSInternal;
import net.mcexpanded.fancytabsections.FancyTabSections;
import net.mcexpanded.fancytabsections.Section.Section;
import net.mcexpanded.fancytabsections.creativetab.ConglomerateOfItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(createaddonorganizer.MODID)
public class createaddonorganizer {
    public static final String MODID = "createaddonorganizer";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final ResourceLocation CREATE_BASE = ResourceLocation.fromNamespaceAndPath("create", "base");

    public static final Set<ResourceLocation> MANAGED_PARENTS = ConcurrentHashMap.newKeySet();
    static {
        MANAGED_PARENTS.add(CREATE_BASE);
    }

    private static volatile boolean collecting = false;
    private static final Map<ResourceLocation, List<Section<?>>> PENDING = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Section<?>> OWN_SECTIONS = new LinkedHashMap<>();

    private static final Map<String, List<ResourceLocation>> SKIP_EXAMPLES = new LinkedHashMap<>();
    private static final int SKIP_EXAMPLES_PER_REASON = 10;
    private static int candidatesSeen = 0;

    private static ResourceLocation lastReconciledTab = null;

    public createaddonorganizer(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        if (SimulatedSupport.isLoaded()) {
            MANAGED_PARENTS.add(SimulatedSupport.MAIN_TAB);
        }

        modEventBus.addListener(EventPriority.LOWEST, createaddonorganizer::onBuildTabContents);
    }

    private static int listenerInvocationsThisPass = 0;

    private static void onBuildTabContents(BuildCreativeModeTabContentsEvent event) {
        if (!collecting) {
            return;
        }
        listenerInvocationsThisPass++;
        ResourceLocation tabId = event.getTabKey().location();
        if (SimulatedSupport.isMainTab(tabId)) {
            return;
        }
        if (MANAGED_PARENTS.contains(tabId)) {
            OWN_SECTIONS.put(tabId, sectionOf(event, tabId));
            return;
        }
        candidatesSeen++;
        String skipReason = AddonDetection.skipReason(tabId);
        if (skipReason != null) {
            List<ResourceLocation> examples = SKIP_EXAMPLES.computeIfAbsent(skipReason, k -> new ArrayList<>());
            if (examples.size() < SKIP_EXAMPLES_PER_REASON) {
                examples.add(tabId);
            }
            return;
        }
        ResourceLocation parent = Config.parentFor(tabId);
        PENDING.computeIfAbsent(parent, k -> new ArrayList<>()).add(sectionOf(event, tabId));
        AbsorbedTabs.IDS.add(tabId);
    }

    public static boolean organize(CreativeModeTab.ItemDisplayParameters params) {
        MANAGED_PARENTS.clear();
        MANAGED_PARENTS.add(CREATE_BASE);
        MANAGED_PARENTS.addAll(Config.allRouteTargets());
        MANAGED_PARENTS.addAll(Config.extraMainSections());
        MANAGED_PARENTS.addAll(AddonGroups.hubs());
        if (SimulatedSupport.isLoaded()) {
            MANAGED_PARENTS.add(SimulatedSupport.MAIN_TAB);
        }
        MANAGED_PARENTS.removeIf(Config::isForceExcluded);

        PENDING.clear();
        OWN_SECTIONS.clear();
        SKIP_EXAMPLES.clear();
        AbsorbedTabs.IDS.clear();
        candidatesSeen = 0;
        listenerInvocationsThisPass = 0;
        int totalTabs = CreativeModeTabs.allTabs().size();
        LOGGER.info("[CAO] {} tab(s) registered total, {} managed parent(s): {}", totalTabs,
                MANAGED_PARENTS.size(), MANAGED_PARENTS);
        collecting = true;
        forceRebuild(params);
        collecting = false;
        LOGGER.info("[CAO] collection pass invoked our listener {} time(s)", listenerInvocationsThisPass);
        if (listenerInvocationsThisPass == 0) {
            LOGGER.warn("[CAO] collection pass captured nothing; will retry");
            return false;
        }

        for (ResourceLocation parent : MANAGED_PARENTS) {
            if (SimulatedSupport.isMainTab(parent)) {
                SimulatedHub.retractAll();
            } else {
                dropParentSections(parent);
            }
        }

        int addonCount = 0;
        for (ResourceLocation parent : MANAGED_PARENTS) {
            List<Section<?>> addons = PENDING.getOrDefault(parent, List.of());

            if (SimulatedSupport.isMainTab(parent)) {
                List<ResourceLocation> ids = new ArrayList<>();
                for (Section<?> addon : orderedById(addons)) {
                    CaoSection cao = (CaoSection) addon;
                    SimulatedHub.inject(cao.id(), cao.title());
                    SimulatedHub.foldItems(cao.id(), cao.items().getStacks());
                    ids.add(cao.id());
                }
                SimulatedHub.reorder(ids);
                addonCount += addons.size();
                continue;
            }

            if (addons.isEmpty() && !CREATE_BASE.equals(parent) && !Config.extraMainSections().contains(parent)) {
                continue;
            }
            Section<?> own = OWN_SECTIONS.get(parent);
            if (own != null) {
                FancyTabSections.addSection(parent, own);
            }
            for (Section<?> addon : orderedById(addons)) {
                FancyTabSections.addSection(parent, addon);
            }
            addonCount += addons.size();
        }
        LOGGER.info("[CAO] organized {} Create parent tab(s) with {} absorbed addon section(s): {}",
                MANAGED_PARENTS.size(), addonCount, AbsorbedTabs.IDS);
        logSkipDiagnostics();

        rebuildTabs(MANAGED_PARENTS, params);
        reconcilePrunedItems(params);
        refreshSearchTrees(params);
        resetCreativeScrollIfOpen();
        return true;
    }

    private static void reconcilePrunedItems(CreativeModeTab.ItemDisplayParameters params) {
        Set<ResourceLocation> changed = new HashSet<>();
        for (ResourceLocation parent : MANAGED_PARENTS) {
            if (SimulatedSupport.isMainTab(parent)) {
                continue;
            }
            CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(parent);
            List<Section<?>> sections = FancyTabSections.REGISTERED_TABS.get(parent);
            if (tab == null || sections == null) {
                continue;
            }
            Set<ItemStack> present = ItemStackLinkedSet.createTypeAndComponentsSet();
            present.addAll(tab.getSearchTabDisplayItems());
            List<Section<?>> kept = new ArrayList<>(sections.size());
            boolean pruned = false;
            for (Section<?> section : sections) {
                if (!(section instanceof CaoSection cao)) {
                    kept.add(section);
                    continue;
                }
                List<ItemStack> stacks = cao.items().getStacks();
                List<ItemStack> surviving = new ArrayList<>(stacks.size());
                for (ItemStack stack : stacks) {
                    if (present.contains(stack)) {
                        surviving.add(stack);
                    }
                }
                if (surviving.size() == stacks.size()) {
                    kept.add(section);
                    continue;
                }
                pruned = true;
                if (surviving.isEmpty()) {
                    continue;
                }
                ConglomerateOfItems conglomerate = ConglomerateOfItems.create();
                for (ItemStack stack : surviving) {
                    conglomerate.add(stack);
                }
                conglomerate.resolveStacks(Minecraft.getInstance().level.registryAccess());
                kept.add(new CaoSection(cao.id(), cao.title(), cao.bannerColor(), cao.texture(),
                        cao.textColor(), conglomerate));
            }
            if (!pruned) {
                continue;
            }
            dropParentSections(parent);
            for (Section<?> section : kept) {
                FancyTabSections.addSection(parent, section);
            }
            changed.add(parent);
        }
        if (changed.isEmpty()) {
            return;
        }
        LOGGER.info("[CAO] another mod pruned items from final tab contents; realigning section rows for {}", changed);
        rebuildTabs(changed, params);
    }

    /**
     * Some item-hiding mods (e.g. Item Obliterator, Reliable Remover) prune items from a tab's final
     * contents via a mixin on {@code CreativeModeTab.buildContents}, and FTS only re-assembles a
     * registered tab's merged contents (and re-runs that pipeline) when the tab is actually selected
     * in the creative screen -- not once at world-join. Re-running reconciliation here, gated on the
     * selected tab actually changing, catches whatever the currently viewed tab's real final contents
     * are instead of relying solely on the one-time organize() snapshot.
     *
     * @return true if reconciliation ran (caller should refresh the screen's visible slots)
     */
    public static boolean reconcileOnTabView(ResourceLocation selectedTabId) {
        if (selectedTabId == null || selectedTabId.equals(lastReconciledTab) || !MANAGED_PARENTS.contains(selectedTabId)) {
            return false;
        }
        lastReconciledTab = selectedTabId;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return false;
        }
        reconcilePrunedItems(createaddonorganizerClient.currentDisplayParams(mc));
        return true;
    }

    private static void resetCreativeScrollIfOpen() {
        if (!(Minecraft.getInstance().screen instanceof CreativeModeInventoryScreen screen)) {
            return;
        }
        ((CreativeModeInventoryScreenAccessor) screen).setScrollOffs(0f);
        if (screen.getMenu() instanceof ItemPickerMenuAccessor menu) {
            menu.invokeScrollTo(0f);
        }
    }

    private static void rebuildTabs(Collection<ResourceLocation> ids, CreativeModeTab.ItemDisplayParameters params) {
        for (ResourceLocation id : ids) {
            CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(id);
            if (tab == null) {
                continue;
            }
            try {
                ModernFixCompat.clearMemoizedParams(tab);
                tab.buildContents(params);
            } catch (Throwable t) {
                LOGGER.error("[CAO] creative tab {} threw while rebuilding its contents; leaving it as-is", id, t);
            }
        }
    }

    private static void logSkipDiagnostics() {
        if (SKIP_EXAMPLES.isEmpty()) {
            return;
        }
        int skipped = SKIP_EXAMPLES.values().stream().mapToInt(List::size).sum();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<ResourceLocation>> e : SKIP_EXAMPLES.entrySet()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append('"').append(e.getKey()).append("\": ").append(e.getValue());
            if (e.getValue().size() >= SKIP_EXAMPLES_PER_REASON) {
                sb.append(" (+more, capped at ").append(SKIP_EXAMPLES_PER_REASON).append(" examples)");
            }
        }
        LOGGER.info("[CAO] {} candidate tab(s) considered, at least {} skipped -- {}", candidatesSeen, skipped, sb);
    }

    private static void forceRebuild(CreativeModeTab.ItemDisplayParameters params) {
        List<CreativeModeTab> order = new ArrayList<>();
        List<CreativeModeTab> deferredParents = new ArrayList<>();
        List<CreativeModeTab> nonCategory = new ArrayList<>();
        for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
            if (tab.getType() != CreativeModeTab.Type.CATEGORY) {
                nonCategory.add(tab);
            } else if (MANAGED_PARENTS.contains(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab))) {
                deferredParents.add(tab);
            } else {
                order.add(tab);
            }
        }
        order.addAll(deferredParents);
        order.addAll(nonCategory);

        int completed = 0;
        for (CreativeModeTab tab : order) {
            try {
                ModernFixCompat.clearMemoizedParams(tab);
                tab.buildContents(params);
                completed++;
            } catch (Throwable t) {
                LOGGER.error("[CAO] creative tab {} threw while rebuilding its contents; leaving it as-is",
                        BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab), t);
            }
        }
        LOGGER.info("[CAO] forceRebuild: {}/{} tab(s) completed without throwing", completed, order.size());
        CreativeModeTabsAccessor.setCachedParameters(params);
        refreshSearchTrees(params);
    }

    private static void refreshSearchTrees(CreativeModeTab.ItemDisplayParameters params) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }
        List<ItemStack> searchItems = List.copyOf(CreativeModeTabs.searchTab().getDisplayItems());
        connection.searchTrees().updateCreativeTooltips(params.holders(), searchItems);
        connection.searchTrees().updateCreativeTags(searchItems);
    }

    public static void refreshTabLayout(CreativeModeTab.ItemDisplayParameters params) {
        rebuildTabs(MANAGED_PARENTS, params);
        reconcilePrunedItems(params);
    }

    public static void dropParentSections(ResourceLocation parent) {
        FancyTabSections.REGISTERED_TABS.remove(parent);
    }

    public static void reapplyAbsorption(CreativeModeTab.ItemDisplayParameters params) {
        Set<ResourceLocation> stillWanted = new HashSet<>();
        stillWanted.add(CREATE_BASE);
        stillWanted.addAll(Config.allRouteTargets());
        stillWanted.addAll(Config.extraMainSections());
        stillWanted.addAll(AddonGroups.hubs());
        if (SimulatedSupport.isLoaded()) {
            stillWanted.add(SimulatedSupport.MAIN_TAB);
        }
        stillWanted.removeIf(Config::isForceExcluded);

        Set<ResourceLocation> dropped = new HashSet<>();
        for (ResourceLocation parent : new ArrayList<>(MANAGED_PARENTS)) {
            if (!stillWanted.contains(parent)) {
                MANAGED_PARENTS.remove(parent);
                dropped.add(parent);
                if (SimulatedSupport.isMainTab(parent)) {
                    SimulatedHub.retractAll();
                } else {
                    dropParentSections(parent);
                }
            }
        }
        MANAGED_PARENTS.addAll(stillWanted);
        for (ResourceLocation parent : stillWanted) {
            if (SimulatedSupport.isMainTab(parent)) {
                continue;
            }
            if (!FancyTabSections.REGISTERED_TABS.containsKey(parent)) {
                Section<?> section = sectionFromLiveTab(parent);
                if (section != null) {
                    FancyTabSections.addSection(parent, section);
                }
            }
        }

        for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
            if (tab.getType() != CreativeModeTab.Type.CATEGORY) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (id == null || MANAGED_PARENTS.contains(id)) {
                continue;
            }
            ResourceLocation currentParent = LiveColors.findParent(id);
            if (!AddonDetection.isAbsorbTarget(id)) {
                if (currentParent != null) {
                    LiveColors.remove(id);
                }
                AbsorbedTabs.IDS.remove(id);
                continue;
            }
            ResourceLocation desiredParent = Config.parentFor(id);
            if (currentParent == null) {
                if (SimulatedSupport.isMainTab(desiredParent)) {
                    CreativeModeTab liveTab = BuiltInRegistries.CREATIVE_MODE_TAB.get(id);
                    SimulatedHub.inject(id, liveTab.getDisplayName());
                    SimulatedHub.foldItems(id, liveTab.getDisplayItems());
                } else {
                    Section<?> section = sectionFromLiveTab(id);
                    if (section != null) {
                        FancyTabSections.addSection(desiredParent, section);
                    }
                }
            } else if (!currentParent.equals(desiredParent)) {
                LiveColors.moveToParent(id, desiredParent);
            }
            AbsorbedTabs.IDS.add(id);
        }

        Set<ResourceLocation> toRebuild = new HashSet<>(stillWanted);
        toRebuild.addAll(dropped);
        rebuildTabs(toRebuild, params);
        reconcilePrunedItems(params);
        resetCreativeScrollIfOpen();
    }

    private static List<Section<?>> orderedById(List<Section<?>> addons) {
        if (addons.isEmpty()) {
            return addons;
        }
        Map<ResourceLocation, Section<?>> byId = new HashMap<>();
        for (Section<?> s : addons) {
            byId.put(s.id(), s);
        }
        List<ResourceLocation> ordered = Config.applyOrder(new ArrayList<>(byId.keySet()),
                id -> CaoSection.titleOf(byId.get(id)).getString());
        List<Section<?>> out = new ArrayList<>(ordered.size());
        for (ResourceLocation id : ordered) {
            out.add(byId.get(id));
        }
        return out;
    }

    private static Section<?> sectionOf(BuildCreativeModeTabContentsEvent event, ResourceLocation id) {
        return sectionFromItems(id, event.getParentEntries(), event.getTab().getDisplayName());
    }

    public static Section<?> sectionFromLiveTab(ResourceLocation id) {
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(id);
        if (tab == null) {
            LOGGER.warn("[CAO] config references unknown creative tab {}; skipping its section", id);
            return null;
        }
        return sectionFromItems(id, tab.getDisplayItems(), tab.getDisplayName());
    }

    private static Section<?> sectionFromItems(ResourceLocation id, Collection<ItemStack> items, Component tabDisplayName) {
        ConglomerateOfItems conglomerate = ConglomerateOfItems.create();
        for (ItemStack stack : items) {
            conglomerate.add(stack.copy());
        }
        conglomerate.resolveStacks(Minecraft.getInstance().level.registryAccess());
        String nameOverride = Config.sectionNameOverride(id);
        Component title = nameOverride != null ? Component.literal(nameOverride) : tabDisplayName;
        String bannerRef = Config.bannerRefFor(id);
        CaoSection section;
        if (bannerRef != null) {
            ResourceLocation texture = BannerTextures.resolve(bannerRef);
            section = texture != null
                    ? new CaoSection(id, title, Config.bannerColorFor(id), texture, Config.textColorFor(id), conglomerate)
                    : new CaoSection(id, title, Config.bannerColorFor(id), Config.textColorFor(id), conglomerate);
        } else {
            section = new CaoSection(id, title, Config.bannerColorFor(id), Config.textColorFor(id), conglomerate);
        }
        if (Config.showCollapseToggle() && Config.isSectionCollapsed(id)) {
            FTSInternal.collapse(section, false);
        }
        return section;
    }
}
