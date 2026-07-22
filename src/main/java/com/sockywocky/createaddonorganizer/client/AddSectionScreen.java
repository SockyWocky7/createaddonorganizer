package com.sockywocky.createaddonorganizer.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.mcexpanded.fancytabsections.FancyTabSections;
import net.mcexpanded.fancytabsections.Section.Section;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import com.sockywocky.createaddonorganizer.AbsorbedTabs;
import com.sockywocky.createaddonorganizer.AddonDetection;
import com.sockywocky.createaddonorganizer.Config;
import com.sockywocky.createaddonorganizer.SectionCatalog;
import com.sockywocky.createaddonorganizer.client.simulated.SimulatedHub;
import com.sockywocky.createaddonorganizer.client.simulated.SimulatedSupport;
import com.sockywocky.createaddonorganizer.createaddonorganizer;
import com.sockywocky.createaddonorganizer.createaddonorganizerClient;

public class AddSectionScreen extends Screen {
    private enum Mode { SUB, MAIN }

    private final Screen returnTo;
    private ResourceLocation currentHub;
    private Mode mode = Mode.SUB;
    private String searchQuery = "";
    private EditBox searchBox;
    private CandidateList list;

    public AddSectionScreen(Screen returnTo) {
        super(Component.translatable("createaddonorganizer.addsection.title"));
        this.returnTo = returnTo;
        this.currentHub = firstHub();
    }

    private static ResourceLocation firstHub() {
        return SectionCatalog.knownHubs().stream()
                .min(Comparator.comparing(id -> nameOf(id).getString(), String.CASE_INSENSITIVE_ORDER))
                .orElse(createaddonorganizer.CREATE_BASE);
    }

    private boolean showHubPicker() {
        return mode == Mode.SUB;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(modeLabel(), b -> {
                    mode = mode == Mode.SUB ? Mode.MAIN : Mode.SUB;
                    rebuildWidgets();
                })
                .bounds(this.width / 2 - 100, 30, 200, 20).build());

        searchBox = new EditBox(this.font, this.width / 2 - 100, 54, 200, 20,
                Component.translatable("createaddonorganizer.addsection.search"));
        searchBox.setHint(Component.translatable("createaddonorganizer.addsection.search"));
        searchBox.setValue(searchQuery);
        searchBox.setResponder(s -> {
            searchQuery = s;
            refreshList(0);
        });
        addRenderableWidget(searchBox);

        int listTop = 82;
        if (showHubPicker()) {
            addRenderableWidget(Button.builder(hubLabel(), b -> {
                        currentHub = nextHub(currentHub);
                        rebuildWidgets();
                    })
                    .bounds(this.width / 2 - 100, 80, 200, 20).build());
            listTop = 108;
        }

        int listBottom = this.height - 40;
        list = new CandidateList(this.minecraft, this.width, listBottom - listTop, listTop, 20);
        for (ResourceLocation id : candidates()) {
            list.add(id);
        }
        addRenderableWidget(list);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }

    private void refreshList(double scrollAmount) {
        list.setEntries(candidates());
        list.setScrollAmount(scrollAmount);
    }

    private Component modeLabel() {
        String key = mode == Mode.SUB ? "createaddonorganizer.addsection.mode.sub" : "createaddonorganizer.addsection.mode.main";
        return Component.translatable("createaddonorganizer.addsection.mode").append(": ").append(Component.translatable(key));
    }

    private Component hubLabel() {
        return Component.translatable("createaddonorganizer.addsection.hub").append(": ").append(nameOf(currentHub));
    }

    private static ResourceLocation nextHub(ResourceLocation current) {
        List<ResourceLocation> hubs = SectionCatalog.knownHubs().stream()
                .sorted(Comparator.comparing(id -> nameOf(id).getString(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (hubs.isEmpty()) {
            return current;
        }
        int index = hubs.indexOf(current);
        return hubs.get((index + 1) % hubs.size());
    }

    private List<ResourceLocation> candidates() {
        String query = searchQuery.trim().toLowerCase(Locale.ROOT);
        Set<ResourceLocation> knownHubs = SectionCatalog.knownHubs();
        Map<ResourceLocation, Component> names = new HashMap<>();
        List<ResourceLocation> out = new ArrayList<>();
        for (var entry : BuiltInRegistries.CREATIVE_MODE_TAB.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            boolean eligible = mode == Mode.SUB
                    ? AddonDetection.isSubSectionCandidate(id, knownHubs)
                    : AddonDetection.isHubPromotionCandidate(id, knownHubs);
            if (!eligible) {
                continue;
            }
            Component name = nameOf(id);
            if (query.isEmpty() || name.getString().toLowerCase(Locale.ROOT).contains(query)) {
                names.put(id, name);
                out.add(id);
            }
        }
        out.sort((a, b) -> names.get(a).getString().compareToIgnoreCase(names.get(b).getString()));
        return out;
    }

    private static Component nameOf(ResourceLocation id) {
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(id);
        return tab != null ? tab.getDisplayName() : Component.literal(id.toString());
    }

    private void pick(ResourceLocation id) {
        double scroll = list.getScrollAmount();
        if (mode == Mode.SUB) {
            addSubSection(id);
        } else {
            addMainSection(id);
        }
        refreshList(scroll);
    }

    private void addSubSection(ResourceLocation id) {
        Config.addForceInclude(id);
        Config.removeForceExclude(id);
        if (createaddonorganizer.CREATE_BASE.equals(currentHub)) {
            Config.clearRoute(id);
        } else {
            Config.setRoute(id, currentHub);
        }
        AbsorbedTabs.IDS.add(id);
        if (this.minecraft.level != null) {
            LiveColors.remove(id);
            if (SimulatedSupport.isMainTab(currentHub)) {
                CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(id);
                SimulatedHub.inject(id, tab.getDisplayName());
                SimulatedHub.foldItems(id, tab.getDisplayItems());
            } else {
                Section<?> section = createaddonorganizer.sectionFromLiveTab(id);
                if (section != null) {
                    FancyTabSections.addSection(currentHub, section);
                }
            }
            createaddonorganizer.refreshTabLayout(createaddonorganizerClient.currentDisplayParams(this.minecraft));
        }
    }

    private void addMainSection(ResourceLocation id) {
        Config.addExtraMainSection(id);
        Config.removeForceExclude(id);
        createaddonorganizer.MANAGED_PARENTS.add(id);
        if (this.minecraft.level != null) {
            LiveColors.remove(id);
            if (!FancyTabSections.REGISTERED_TABS.containsKey(id)) {
                Section<?> section = createaddonorganizer.sectionFromLiveTab(id);
                if (section != null) {
                    FancyTabSections.addSection(id, section);
                }
            }
            createaddonorganizer.refreshTabLayout(createaddonorganizerClient.currentDisplayParams(this.minecraft));
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(returnTo);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
    }

    private class CandidateList extends ContainerObjectSelectionList<CandidateList.Row> {
        CandidateList(Minecraft mc, int width, int height, int top, int itemHeight) {
            super(mc, width, height, top, itemHeight);
        }

        void add(ResourceLocation id) {
            addEntry(new Row(id));
        }

        void setEntries(List<ResourceLocation> ids) {
            replaceEntries(ids.stream().map(Row::new).toList());
        }

        @Override
        public int getRowWidth() {
            return 280;
        }

        private class Row extends ContainerObjectSelectionList.Entry<Row> {
            private final ResourceLocation id;
            private final Component name;
            private final boolean placed;

            Row(ResourceLocation id) {
                this.id = id;
                this.name = nameOf(id);
                this.placed = AddonDetection.isPlaced(id);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of();
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of();
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button == 0) {
                    pick(id);
                    return true;
                }
                return false;
            }

            @Override
            public void render(GuiGraphics g, int index, int top, int left, int rowWidth, int rowHeight,
                    int mouseX, int mouseY, boolean hovered, float partialTick) {
                if (hovered) {
                    g.fill(left, top, left + rowWidth, top + rowHeight, 0x40FFFFFF);
                }
                CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(id);
                ItemStack icon = SafeIcon.of(tab);
                SafeIcon.render(g, icon, left + 4, top + (rowHeight - 16) / 2);
                g.drawString(font, name, left + 26, top + (rowHeight - 8) / 2, 0xFFFFFFFF);
                if (placed) {
                    Component tag = Component.translatable("createaddonorganizer.addsection.placed");
                    g.drawString(font, tag, left + rowWidth - font.width(tag) - 4, top + (rowHeight - 8) / 2, 0xFFAAAAAA);
                }
            }
        }
    }
}
