package com.sockywocky.createaddonorganizer.client;

import java.util.List;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CreditsScreen extends Screen {
    private static final int BAND_ALPHA = 0x2A;
    private static final int DIVIDER_ALPHA = 0x60;
    private static final int LINE_ALPHA = 0x50;
    private static final int SUB_LINE_X = 6;
    private static final int SUB_LINE_FADE = 10;

    private static final String DISCORD_URL = "https://discord.gg/gfVqfFQ3KB";
    private static final int CONTRIBUTE_Y = 46;

    private final Screen parent;
    private boolean empty;
    private Component hoverBannerTooltip;

    public CreditsScreen(Screen parent) {
        super(Component.translatable("createaddonorganizer.colors.credits.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int listTop = 60;
        int listBottom = this.height - 40;
        CreditsList list = new CreditsList(this.minecraft, this.width, listBottom - listTop, listTop, 24);
        List<CreditsCatalog.Entry> entries = CreditsCatalog.rows();
        empty = entries.isEmpty();
        for (CreditsCatalog.Entry entry : entries) {
            list.add(entry);
        }
        addRenderableWidget(list);

        if (DevMode.isUnlocked()) {
            addRenderableWidget(Checkbox.builder(Component.translatable("createaddonorganizer.colors.credits.localTesting"), this.font)
                    .pos(6, 6)
                    .selected(RemoteBanners.isLocalTesting())
                    .onValueChange((cb, checked) -> {
                        RemoteBanners.setLocalTesting(checked);
                        if (checked) {
                            RemoteBannerPools.refreshLocal();
                        } else {
                            RemoteBannerPools.loadCacheFromDisk();
                        }
                        BannerTextures.invalidateRemoteCache();
                        rebuildWidgets();
                    })
                    .build());
            Button refreshButton = addRenderableWidget(Button.builder(
                            Component.translatable("createaddonorganizer.colors.credits.refresh"),
                            b -> {
                                RemoteBanners.refreshLocal();
                                RemoteBannerPools.refreshLocal();
                                BannerTextures.invalidateRemoteCache();
                                rebuildWidgets();
                            })
                    .bounds(6, 28, 100, 20).build());
            refreshButton.active = RemoteBanners.isLocalTesting();
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 - 89, this.height - 30, 178, 20).build());
        addRenderableWidget(Button.builder(Component.literal("?"), b -> {})
                .bounds(this.width / 2 + 93, this.height - 30, 18, 20)
                .tooltip(Tooltip.create(Component.translatable("createaddonorganizer.colors.credits.onlineHint")))
                .build());
        addRenderableWidget(Button.builder(Component.literal("1"), b -> this.minecraft.setScreen(new TextBannerCreditsScreen(this.parent)))
                .bounds(this.width / 2 - 111, this.height - 30, 18, 20)
                .tooltip(Tooltip.create(Component.translatable("createaddonorganizer.colors.credits.viewTextBanner")))
                .build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hoverBannerTooltip = null;
        super.render(g, mouseX, mouseY, partialTick);

        float scale = 1.6f;
        g.pose().pushPose();
        g.pose().scale(scale, scale, scale);
        g.drawCenteredString(this.font, this.title, Math.round(this.width / 2 / scale), Math.round(16 / scale), 0xFFFFFFFF);
        g.pose().popPose();

        g.drawCenteredString(this.font, Component.translatable("createaddonorganizer.colors.credits.description"),
                this.width / 2, 34, 0xAAAAAAAA);

        Component prefix = Component.translatable("createaddonorganizer.colors.credits.contribute");
        Component link = Component.translatable("createaddonorganizer.colors.credits.discord");
        int startX = this.width / 2 - (this.font.width(prefix) + this.font.width(link)) / 2;
        g.drawString(this.font, prefix, startX, CONTRIBUTE_Y, 0xAAAAAAAA);
        g.drawString(this.font, link, startX + this.font.width(prefix), CONTRIBUTE_Y, 0xFF5555FF);

        if (empty) {
            g.drawCenteredString(this.font, Component.translatable("createaddonorganizer.colors.credits.empty"),
                    this.width / 2, this.height / 2, 0xFFAAAAAA);
        }

        if (hoverBannerTooltip != null) {
            g.renderTooltip(this.font, hoverBannerTooltip, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            Component prefix = Component.translatable("createaddonorganizer.colors.credits.contribute");
            Component link = Component.translatable("createaddonorganizer.colors.credits.discord");
            int prefixWidth = this.font.width(prefix);
            int linkWidth = this.font.width(link);
            int linkX1 = this.width / 2 - (prefixWidth + linkWidth) / 2 + prefixWidth;
            int linkX2 = linkX1 + linkWidth;
            if (mouseX >= linkX1 && mouseX < linkX2 && mouseY >= CONTRIBUTE_Y && mouseY < CONTRIBUTE_Y + 9) {
                this.minecraft.setScreen(new ConfirmLinkScreen(confirmed -> {
                    if (confirmed) {
                        Util.getPlatform().openUri(DISCORD_URL);
                    }
                    this.minecraft.setScreen(this);
                }, DISCORD_URL, true));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private class CreditsList extends ContainerObjectSelectionList<CreditsList.Row> {
        private final int rowHeight;

        CreditsList(Minecraft mc, int width, int height, int top, int itemHeight) {
            super(mc, width, height, top, itemHeight);
            this.rowHeight = itemHeight;
        }

        void add(CreditsCatalog.Entry entry) {
            addEntry(new Row(entry));
        }

        @Override
        public int getRowWidth() {
            return 320;
        }

        private boolean isLastInGroup(int index) {
            List<Row> all = children();
            return index + 1 >= all.size() || all.get(index + 1).data.header();
        }

        private class Row extends ContainerObjectSelectionList.Entry<Row> {
            private final CreditsCatalog.Entry data;

            Row(CreditsCatalog.Entry data) {
                this.data = data;
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
            public void render(GuiGraphics g, int index, int top, int left, int rowWidth, int rowHeight,
                    int mouseX, int mouseY, boolean hovered, float partialTick) {
                if (data.header()) {
                    int band = (BAND_ALPHA << 24) | (data.nameColor() & 0x00FFFFFF);
                    int divider = (DIVIDER_ALPHA << 24) | (data.nameColor() & 0x00FFFFFF);
                    g.fill(left, top, left + rowWidth, top + rowHeight, band);
                    if (index != 0) {
                        g.fill(left, top, left + rowWidth, top + 1, divider);
                    }
                    Component name = Component.literal(data.label()).withStyle(ChatFormatting.BOLD);
                    g.drawString(font, name, left + 6, top + (rowHeight - 8) / 2, data.nameColor());
                    return;
                }

                int lineColor = (LINE_ALPHA << 24) | (data.nameColor() & 0x00FFFFFF);
                int lineX = left + SUB_LINE_X;
                int lineBottom = top + rowHeight;
                int gapToNext = CreditsList.this.rowHeight - rowHeight;
                if (CreditsList.this.isLastInGroup(index)) {
                    int fadeLen = Math.min(SUB_LINE_FADE, rowHeight);
                    int fadeStart = lineBottom - fadeLen;
                    if (fadeStart > top) {
                        g.fill(lineX, top, lineX + 2, fadeStart, lineColor);
                    }
                    g.fillGradient(lineX, fadeStart, lineX + 2, lineBottom, lineColor, lineColor & 0x00FFFFFF);
                } else {
                    g.fill(lineX, top, lineX + 2, lineBottom + gapToNext, lineColor);
                }

                int bx = left + (rowWidth - BannerTextures.WIDTH) / 2;
                int by = top + (rowHeight - BannerTextures.HEIGHT) / 2;
                g.fill(bx - 1, by - 1, bx + BannerTextures.WIDTH + 1, by + BannerTextures.HEIGHT + 1, 0xFF000000);
                Optional<BannerAnimation.AnimInfo> anim = BannerAnimation.get(data.texture());
                int frameCount = anim.map(BannerAnimation.AnimInfo::frameCount).orElse(1);
                boolean bannerHovered = BannerAnimation.isHovering(bx, by, BannerTextures.WIDTH, BannerTextures.HEIGHT, mouseX, mouseY);
                int frame = anim.map(info -> BannerAnimation.currentFrame(data.texture(), info, bannerHovered)).orElse(0);
                g.blit(data.texture(), bx, by, 0.0F, frame * BannerTextures.HEIGHT, BannerTextures.WIDTH, BannerTextures.HEIGHT,
                        BannerTextures.WIDTH, frameCount * BannerTextures.HEIGHT);
                if (bannerHovered && data.filename() != null) {
                    CreditsScreen.this.hoverBannerTooltip = Component.literal(data.filename());
                }
            }
        }
    }
}
