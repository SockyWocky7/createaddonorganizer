package com.sockywocky.createaddonorganizer.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.Window;

import com.sockywocky.createaddonorganizer.Config;

import net.mcexpanded.fancytabsections.Section.Section;
import net.mcexpanded.fancytabsections.Section.SectionAnimatedTextured;
import net.mcexpanded.fancytabsections.Section.SectionColored;
import net.mcexpanded.fancytabsections.Section.SectionTextured;
import net.mcexpanded.fancytabsections.Section.StickySection;
import net.mcexpanded.fancytabsections.creativetab.ConglomerateOfItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public record CaoSection(ResourceLocation id, Component title, ColorSpec bannerColor, ResourceLocation texture,
        ColorSpec textColor, ConglomerateOfItems items) implements Section<CaoSection>, StickySection {

    private static final int CONTENT_W = 160;
    static final int CONTENT_H = 16;
    private static final int ROW_H = 18;
    private static final int BEVEL_DARK = 0xFF373737;
    private static final int BEVEL_WHITE = 0xFFFFFFFF;

    public CaoSection(ResourceLocation id, Component title, ColorSpec bannerColor, ColorSpec textColor, ConglomerateOfItems items) {
        this(id, title, bannerColor, null, textColor, items);
    }

    public CaoSection withBanner(ColorSpec spec) {
        return new CaoSection(id, title, spec, null, textColor, items);
    }

    public CaoSection withTexture(ResourceLocation newTexture) {
        return new CaoSection(id, title, bannerColor, newTexture, textColor, items);
    }

    public CaoSection withTextColor(ColorSpec spec) {
        return new CaoSection(id, title, bannerColor, texture, spec, items);
    }

    public CaoSection withTitle(Component newTitle) {
        return new CaoSection(id, newTitle, bannerColor, texture, textColor, items);
    }

    public static Component titleOf(Section<?> section) {
        if (section instanceof CaoSection s) {
            return s.title();
        }
        if (section instanceof SectionColored s) {
            return s.title;
        }
        if (section instanceof SectionTextured s) {
            return s.title;
        }
        if (section instanceof SectionAnimatedTextured s) {
            return s.title;
        }
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(section.id());
        return tab != null ? tab.getDisplayName() : Component.literal(section.id().toString());
    }

    @Override
    public boolean collapsible() {
        return Config.showCollapseToggle();
    }

    @Override
    public boolean isSticky() {
        return Config.stickySectionBanners();
    }

    @Override
    public ItemStack icon() {
        ItemStack tabIcon = SafeIcon.of(BuiltInRegistries.CREATIVE_MODE_TAB.get(id));
        if (!tabIcon.isEmpty()) {
            return tabIcon;
        }
        List<ItemStack> stacks = items.getStacks();
        return stacks.isEmpty() ? ItemStack.EMPTY : stacks.get(0);
    }

    @Override
    public void render(GuiGraphics g, Font font, int topLeftX, int topLeftY) {
        int x1 = topLeftX + 1;
        int x2 = x1 + CONTENT_W;
        int contentTop = topLeftY + 1;
        int contentBottom = contentTop + CONTENT_H;

        g.fill(x1 - 1, topLeftY, x2, topLeftY + 1, BEVEL_DARK);
        g.fill(x1, topLeftY + ROW_H - 1, x2 + 1, topLeftY + ROW_H, BEVEL_WHITE);

        if (texture != null) {
            var anim = BannerAnimation.get(texture);
            float v = 0f;
            int texHeight = BannerTextures.HEIGHT;
            if (anim.isPresent()) {
                boolean hovered = isHoveredNow(x1, contentTop);
                int frame = BannerAnimation.currentFrame(texture, anim.get(), hovered);
                v = frame * BannerTextures.HEIGHT;
                texHeight = anim.get().frameCount() * BannerTextures.HEIGHT;
            }
            g.blit(texture, x1, contentTop, CONTENT_W, CONTENT_H, 0f, v,
                    BannerTextures.WIDTH, BannerTextures.HEIGHT, BannerTextures.WIDTH, texHeight);
        } else {
            BannerFill.draw(g, x1, contentTop, x2, contentBottom, bannerColor);
        }

        int textX = topLeftX + 6;
        int textY = topLeftY + 5;
        int maxX = x2 - 3;
        int available = maxX - textX;
        float cutoff = Config.scrollCutoffFor(id);
        int viewMaxX = textX + Math.round(available * cutoff);
        int viewAvailable = viewMaxX - textX;
        boolean screenshot = TwoToneText.renderTargetActive();
        Component shown = screenshot ? clampedTitle(font, title, viewAvailable) : title;
        boolean scrolling = !screenshot && font.width(shown) > viewAvailable;

        if (Config.tintedTextBox()) {
            int w = scrolling ? viewAvailable : font.width(shown);
            ResourceLocation boxTexture = BoxTextures.resolve(Config.boxTextureRefFor(id));
            BoxTextures.draw(g, boxTexture, textX - 4, textY - 3, textX + w + 3, textY + 9 + 2, Config.boxColorFor(id),
                    Config.boxDarkenFor(id), Config.boxOpacityFor(id));
        }
        boolean shadowOn = Config.titleTextShadow(id);
        Integer shadowOverride = shadowOn ? Config.textShadowColorFor(id) : null;
        boolean vanillaShadow = shadowOn && shadowOverride == null;
        ColorSpec outline = Config.textOutlineColorFor(id);
        TwoToneText.draw(g, font, shown, textX, textY, viewMaxX, textColor, Config.textSecondaryColorFor(id),
                Config.twoToneSplitFor(id), vanillaShadow, shadowOverride != null ? shadowOverride : 0, outline);
    }

    private static final Map<String, Component> CLAMPED_TITLES = new HashMap<>();

    private static Component clampedTitle(Font font, Component title, int maxW) {
        if (font.width(title) <= maxW) {
            return title;
        }
        String key = maxW + " " + title.getString();
        Component cached = CLAMPED_TITLES.get(key);
        if (cached != null) {
            return cached;
        }
        FormattedText cut = font.substrByWidth(title, Math.max(0, maxW - font.width("…")));
        Component shown = Component.literal(cut.getString().stripTrailing() + "…");
        CLAMPED_TITLES.put(key, shown);
        return shown;
    }

    private static boolean isHoveredNow(int x, int y) {
        Window window = Minecraft.getInstance().getWindow();
        double mouseX = Minecraft.getInstance().mouseHandler.xpos() / window.getGuiScale();
        double mouseY = Minecraft.getInstance().mouseHandler.ypos() / window.getGuiScale();
        return BannerAnimation.isHovering(x, y, CONTENT_W, CONTENT_H, mouseX, mouseY);
    }
}
