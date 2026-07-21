package com.sockywocky.createaddonorganizer.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.IntSupplier;

import com.mojang.blaze3d.platform.NativeImage;
import com.sockywocky.createaddonorganizer.Config;
import com.sockywocky.createaddonorganizer.client.simulated.SimulatedSupport;
import com.sockywocky.createaddonorganizer.createaddonorganizer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

public class ColorPickerScreen extends Screen {
    private enum Mode { COLOR, IMAGE }

    private enum EditTarget { BANNER, BOX, TEXT, HIGHLIGHT }

    private enum TextTarget { PRIMARY, SECONDARY, OUTLINE, SHADOW }

    private final Screen parent;
    private final ResourceLocation id;
    private final Component sectionName;

    private Mode mode;
    private EditTarget target = EditTarget.BANNER;
    private TextTarget textEditTarget = TextTarget.PRIMARY;

    private final Hsva bannerHsva;
    private final Hsva bannerHsva2;
    private boolean bannerGradientEnabled;
    private ColorSpec.Direction bannerDirection = ColorSpec.Direction.VERTICAL;
    private ColorSpec.Style bannerStyle = ColorSpec.Style.SMOOTH;
    private final Hsva boxHsva;
    private final Hsva textHsva;
    private final Hsva textHsva2;
    private boolean textGradientEnabled;
    private Hsva text2Hsva;
    private final Hsva text2Hsva2;
    private boolean text2GradientEnabled;
    private boolean twoTone;
    private float twoToneSplit;
    private final Hsva outlineHsva;
    private final Hsva outlineHsva2;
    private boolean outlineGradientEnabled;
    private boolean outlineEnabled;
    private boolean editingStop2;
    private boolean shadowEnabled;
    private boolean shadowUnlinked;
    private final Hsva shadowHsva;
    private float scrollCutoff;

    private final boolean isMainTab;
    private final boolean highlightOnly;
    private boolean hasHighlight;
    private final Hsva highlightHsva;

    private ResourceLocation selectedTexture;
    private String selectedRef;
    private boolean bannerAnimated;
    private int bannerFrameTicks = 2;

    private Mode boxMode;
    private ResourceLocation selectedBoxTexture;
    private String selectedBoxRef;

    private int previewY;
    private int panelTop;
    private static final int ROW_H = 20;
    private static final int FULL_ROW_GAP = 6;
    private static final int FULL_SQUARE_H = 100;
    private static final int FULL_BAR_H = 16;
    private static final int REFERENCE_CONTENT_HEIGHT = 236;
    private static final float MIN_LAYOUT_SCALE = 0.5f;
    private int rowGap = FULL_ROW_GAP;
    private int squareHeight = FULL_SQUARE_H;
    private int barHeight = FULL_BAR_H;
    private Component hoverBannerTooltip;
    private GalleryList galleryList;
    private BoxGalleryList boxGalleryList;

    private final GradientTexture svSquareTexture = new GradientTexture("sv_square");
    private final GradientTexture hueBarTexture = new GradientTexture("hue_bar");

    public ColorPickerScreen(Screen parent, ResourceLocation id, Component sectionName, boolean isMainTab) {
        super(Component.translatable("createaddonorganizer.colors.pick"));
        this.parent = parent;
        this.id = id;
        this.sectionName = sectionName;

        ColorSpec bannerSpec = Config.bannerColorFor(id);
        this.bannerHsva = Hsva.fromArgb(bannerSpec.color1());
        this.bannerGradientEnabled = bannerSpec.isGradient();
        this.bannerHsva2 = Hsva.fromArgb(bannerSpec.isGradient() ? bannerSpec.color2() : bannerSpec.color1());
        this.bannerDirection = bannerSpec.direction();
        this.bannerStyle = bannerSpec.style();

        this.boxHsva = Hsva.fromArgb(Config.boxColorFor(id));

        ColorSpec textSpec = Config.textColorFor(id);
        this.textHsva = Hsva.fromArgb(textSpec.color1());
        this.textGradientEnabled = textSpec.isGradient();
        this.textHsva2 = Hsva.fromArgb(textSpec.isGradient() ? textSpec.color2() : textSpec.color1());

        ColorSpec secondary = Config.textSecondaryColorFor(id);
        this.twoTone = secondary != null;
        ColorSpec secondarySpec = secondary != null ? secondary : ColorSpec.solid(0xFFCEA05A);
        this.text2Hsva = Hsva.fromArgb(secondarySpec.color1());
        this.text2GradientEnabled = secondarySpec.isGradient();
        this.text2Hsva2 = Hsva.fromArgb(secondarySpec.isGradient() ? secondarySpec.color2() : secondarySpec.color1());
        this.twoToneSplit = Config.twoToneSplitFor(id);

        ColorSpec outline = Config.textOutlineColorFor(id);
        this.outlineEnabled = outline != null;
        ColorSpec outlineSpec = outline != null ? outline : Config.defaultTextOutlineSpec();
        this.outlineHsva = Hsva.fromArgb(outlineSpec.color1());
        this.outlineGradientEnabled = outlineSpec.isGradient();
        this.outlineHsva2 = Hsva.fromArgb(outlineSpec.isGradient() ? outlineSpec.color2() : outlineSpec.color1());

        this.shadowEnabled = Config.titleTextShadow(id);
        Integer shadowColor = Config.textShadowColorFor(id);
        this.shadowUnlinked = shadowColor != null;
        this.shadowHsva = Hsva.fromArgb(shadowColor != null ? shadowColor : Config.DEFAULT_TEXT_SHADOW_COLOR.get());
        this.scrollCutoff = Config.scrollCutoffFor(id);

        this.selectedRef = Config.bannerRefFor(id);
        if (selectedRef != null) {
            this.selectedTexture = BannerTextures.resolve(selectedRef);
            this.mode = Mode.IMAGE;
            syncAnimationFields();
        } else {
            this.mode = Mode.COLOR;
        }

        this.selectedBoxRef = Config.boxTextureRefFor(id);
        if (selectedBoxRef != null) {
            this.selectedBoxTexture = BoxTextures.resolve(selectedBoxRef);
            this.boxMode = Mode.IMAGE;
        } else {
            this.boxMode = Mode.COLOR;
        }

        this.isMainTab = isMainTab;
        this.highlightOnly = SimulatedSupport.isMainTab(id);
        if (highlightOnly) {
            this.target = EditTarget.HIGHLIGHT;
        }
        Integer highlightOverride = Config.highlightColorFor(id);
        this.hasHighlight = highlightOverride != null;
        this.highlightHsva = Hsva.fromArgb(highlightOverride != null ? highlightOverride : 0xFF4A90D9);
    }

    private void syncAnimationFields() {
        Integer declared = selectedTexture != null ? Config.animatedFrameTicks(selectedTexture) : null;
        this.bannerAnimated = selectedTexture != null && BannerAnimation.isAnimatable(selectedTexture);
        this.bannerFrameTicks = declared != null ? declared : 2;
    }

    @Override
    protected void init() {
        int buttonsY = this.height - 28;
        this.panelTop = 34;

        if (highlightOnly) {
            initHighlightPanel();
            addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.ok"), b -> confirm())
                    .bounds(this.width / 2 - 102, buttonsY, 100, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.cancel"), b -> onClose())
                    .bounds(this.width / 2 + 2, buttonsY, 78, 20).build());
            addRenderableWidget(Button.builder(Component.literal("?"), b -> {})
                    .bounds(this.width / 2 + 84, buttonsY, 18, 20)
                    .tooltip(Tooltip.create(Component.translatable("createaddonorganizer.colors.simulatedUneditable")))
                    .build());
            return;
        }

        boolean previewTop = Config.bannerEditorPreviewTop();
        this.previewY = previewTop ? 32 : this.height - 52;
        this.panelTop = previewTop ? 58 : 34;
        int contentBottom = previewTop ? buttonsY : Math.min(buttonsY, this.previewY - 8);
        computeLayoutScale(contentBottom - this.panelTop);

        switch (target) {
            case BANNER -> initBannerPanel();
            case BOX -> initBoxPanel();
            case TEXT -> initTextPanel();
            case HIGHLIGHT -> initHighlightPanel();
        }

        if (DevMode.isUnlocked()) {
            Button screenshotButton = addRenderableWidget(Button.builder(
                            Component.translatable("createaddonorganizer.banner.screenshot"),
                            b -> captureScreenshot())
                    .bounds(6, 6, 100, 20).build());
            screenshotButton.active = hasBanner();

            addRenderableWidget(Button.builder(
                            Component.translatable("createaddonorganizer.banner.screenshot.openFolder"),
                            b -> BannerScreenshot.openFolder())
                    .bounds(6, 28, 130, 20).build());
        }

        if (isMainTab) {
            addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.highlightButton"), b -> {
                        target = EditTarget.HIGHLIGHT;
                        editingStop2 = false;
                        rebuildWidgets();
                    })
                    .bounds(this.width - 106, 6, 100, 20).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.ok"), b -> confirm())
                .bounds(this.width / 2 - 102, buttonsY, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.cancel"), b -> onClose())
                .bounds(this.width / 2 + 2, buttonsY, 100, 20).build());
    }

    private void initHighlightPanel() {
        int x = this.width / 2 - 100;
        addRenderableWidget(Checkbox.builder(Component.translatable("createaddonorganizer.colors.highlight.enabled"), this.font)
                .pos(x, panelTop)
                .selected(hasHighlight)
                .onValueChange((cb, checked) -> {
                    hasHighlight = checked;
                    rebuildWidgets();
                })
                .build());
        if (hasHighlight) {
            addColorControls(x, panelTop + 30, highlightHsva);
        }
    }

    private void computeLayoutScale(int available) {
        float scale = available >= REFERENCE_CONTENT_HEIGHT
                ? 1.0f
                : Math.max(MIN_LAYOUT_SCALE, available / (float) REFERENCE_CONTENT_HEIGHT);
        rowGap = Math.max(2, Math.round(FULL_ROW_GAP * scale));
        squareHeight = Math.max(50, Math.round(FULL_SQUARE_H * scale));
        barHeight = Math.max(8, Math.round(FULL_BAR_H * scale));
    }

    private int initTopRow(int x, int y, Component toggleLabel, Runnable onToggle, Runnable onToggleBackward, Runnable onReset) {
        final int rowW = 200, toggleW = 150, resetW = 46, gap = 4;
        if (toggleLabel != null) {
            addRenderableWidget(new CycleActionButton(x, y, toggleW, ROW_H, toggleLabel, onToggle, onToggleBackward));
            addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.resetShort"),
                            b -> onReset.run())
                    .bounds(x + toggleW + gap, y, resetW, ROW_H)
                    .tooltip(Tooltip.create(Component.translatable("createaddonorganizer.colors.reset")))
                    .build());
        } else {
            addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.reset"),
                            b -> onReset.run())
                    .bounds(x, y, rowW, ROW_H).build());
        }
        return y + ROW_H + rowGap;
    }

    private void initBannerPanel() {
        int x = this.width / 2 - 100;
        int contentY = initTopRow(x, panelTop, modeLabel(mode), this::toggleBannerMode, this::toggleBannerMode, this::resetBanner);

        if (mode == Mode.COLOR) {
            addGradientControls(x, contentY, bannerGradientEnabled, () -> bannerGradientEnabled = !bannerGradientEnabled,
                    bannerHsva, bannerHsva2,
                    new BannerGradientExtras(bannerDirection, d -> bannerDirection = d, bannerStyle, s -> bannerStyle = s));
        } else {
            boolean isUpload = selectedRef != null && selectedRef.startsWith("file:");
            addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.banner.upload"),
                    b -> upload()).bounds(x, contentY, isUpload ? 130 : 200, 20).build());
            if (isUpload) {
                addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.banner.delete"),
                        b -> confirmDelete()).bounds(x + 134, contentY, 66, 20).build());
            }

            boolean canAnimate = selectedTexture != null && BannerAnimation.isAnimatable(selectedTexture);
            if (canAnimate) {
                addRenderableWidget(Checkbox.builder(Component.translatable("createaddonorganizer.banner.animated"), this.font)
                        .pos(x, contentY + 25)
                        .selected(bannerAnimated)
                        .onValueChange((cb, checked) -> {
                            bannerAnimated = checked;
                            rebuildWidgets();
                        })
                        .build());
                if (bannerAnimated) {
                    addRenderableWidget(new ChannelSlider(x + 90, contentY + 24, 110, 20,
                            Component.translatable("createaddonorganizer.banner.speed"),
                            (bannerFrameTicks - 1) / 9.0,
                            v -> bannerFrameTicks = 1 + (int) Math.round(v * 9),
                            v -> (1 + (int) Math.round(v * 9)) + "t"));
                }
            }

            List<String> pool = BannerPools.poolFor(id);
            boolean hasPool = !pool.isEmpty();
            int checkboxY = canAnimate ? contentY + 48 : contentY + 24;
            int listTop;
            if (hasPool) {
                addRenderableWidget(Checkbox.builder(Component.translatable("createaddonorganizer.banner.showAll"), this.font)
                        .pos(x, checkboxY)
                        .selected(Config.showAllBanners())
                        .onValueChange((cb, checked) -> {
                            Config.setShowAllBanners(checked);
                            rebuildWidgets();
                        })
                        .build());
                listTop = checkboxY + 24;
            } else {
                listTop = checkboxY;
            }

            int listBottom = Config.bannerEditorPreviewTop() ? this.height - 34 : previewY - 8;
            double restoreScroll = galleryList != null ? galleryList.getScrollAmount() : 0;
            galleryList = new GalleryList(this.minecraft, this.width, listBottom - listTop, listTop, BannerTextures.HEIGHT + 6);
            boolean restrict = !DevMode.isUnlocked() && !Config.showAllBanners();
            List<String> refs;
            if (!hasPool) {
                refs = BannerTextures.gallery();
            } else if (restrict) {
                refs = new ArrayList<>(pool);
                for (String extra : Config.extraPoolFor(id)) {
                    if (!refs.contains(extra)) {
                        refs.add(extra);
                    }
                }
            } else {
                refs = BannerTextures.gallery();
            }
            // A curated pool (unlike the full gallery, which only ever lists remote banners already
            // confirmed downloaded) can reference a remote banner whose background sync hasn't finished
            // yet; showing it anyway renders as a blank, invisible row instead of just being absent.
            for (String ref : refs) {
                if (BannerTextures.resolve(ref) != null) {
                    galleryList.add(ref);
                }
            }
            addRenderableWidget(galleryList);
            galleryList.setScrollAmount(restoreScroll);
        }
    }

    private void toggleBannerMode() {
        mode = (mode == Mode.COLOR) ? Mode.IMAGE : Mode.COLOR;
        rebuildWidgets();
    }

    private void toggleBoxMode() {
        boxMode = (boxMode == Mode.COLOR) ? Mode.IMAGE : Mode.COLOR;
        rebuildWidgets();
    }

    private void initBoxPanel() {
        int x = this.width / 2 - 100;
        int contentY = initTopRow(x, panelTop, modeLabel(boxMode), this::toggleBoxMode, this::toggleBoxMode, this::resetBox);

        if (boxMode == Mode.COLOR) {
            int y = addColorControls(x, contentY, boxHsva);
            addTintedBoxCheckbox(x, y + 4);
            addRenderableWidget(new ChannelSlider(x + 90, y + 3, 110, 20, Component.translatable("createaddonorganizer.colors.alpha"),
                    boxHsva.a, v -> boxHsva.a = (float) v));
        } else {
            addTintedBoxCheckbox(x, contentY);
            int rowY = contentY + 24;

            boolean isUpload = selectedBoxRef != null && selectedBoxRef.startsWith("file:");
            addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.banner.upload"),
                    b -> uploadBox()).bounds(x, rowY, isUpload ? 130 : 200, 20).build());
            if (isUpload) {
                addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.banner.delete"),
                        b -> confirmDeleteBox()).bounds(x + 134, rowY, 66, 20).build());
            }

            int listTop = rowY + 25;
            int listBottom = Config.bannerEditorPreviewTop() ? this.height - 34 : previewY - 8;
            double restoreScroll = boxGalleryList != null ? boxGalleryList.getScrollAmount() : 0;
            boxGalleryList = new BoxGalleryList(this.minecraft, this.width, listBottom - listTop, listTop, BoxTextures.HEIGHT + 10);
            for (String ref : BoxTextures.gallery()) {
                boxGalleryList.add(ref);
            }
            addRenderableWidget(boxGalleryList);
            boxGalleryList.setScrollAmount(restoreScroll);
        }
    }

    private void addTintedBoxCheckbox(int x, int y) {
        addRenderableWidget(Checkbox.builder(Component.translatable("createaddonorganizer.colors.tintedBox"), this.font)
                .pos(x, y)
                .selected(Config.tintedTextBox())
                .onValueChange((cb, checked) -> Config.setTintedTextBox(checked))
                .build());
    }

    private void initTextPanel() {
        int x = this.width / 2 - 100;
        int contentY = initTopRow(x, panelTop, textTargetLabel(), this::cycleTextTarget, this::cycleTextTargetBackward, this::resetActiveTextTarget);

        switch (textEditTarget) {
            case PRIMARY -> {
                int y = addGradientControls(x, contentY, textGradientEnabled, () -> textGradientEnabled = !textGradientEnabled,
                        textHsva, textHsva2, null);
                addRenderableWidget(new ChannelSlider(x, y + 4, 200, 20, Component.translatable("createaddonorganizer.colors.scrollCutoff"),
                        scrollCutoff, v -> scrollCutoff = (float) v));
            }
            case SECONDARY -> {
                int y = addGradientControls(x, contentY, text2GradientEnabled, () -> text2GradientEnabled = !text2GradientEnabled,
                        text2Hsva, text2Hsva2, null);
                addRenderableWidget(Checkbox.builder(Component.translatable("createaddonorganizer.colors.twoTone"), this.font)
                        .pos(x, y + 4)
                        .selected(twoTone)
                        .onValueChange((cb, checked) -> twoTone = checked)
                        .build());
                addRenderableWidget(new ChannelSlider(x + 90, y + 3, 110, 20, Component.translatable("createaddonorganizer.colors.twoToneSplit"),
                        twoToneSplit, v -> twoToneSplit = (float) v));
            }
            case OUTLINE -> {
                int y = addGradientControls(x, contentY, outlineGradientEnabled, () -> outlineGradientEnabled = !outlineGradientEnabled,
                        outlineHsva, outlineHsva2, null);
                addRenderableWidget(Checkbox.builder(Component.translatable("createaddonorganizer.colors.outline"), this.font)
                        .pos(x, y + 4)
                        .selected(outlineEnabled)
                        .onValueChange((cb, checked) -> outlineEnabled = checked)
                        .build());
            }
            case SHADOW -> {
                addRenderableWidget(Checkbox.builder(Component.translatable("createaddonorganizer.colors.shadow"), this.font)
                        .pos(x, contentY)
                        .selected(shadowEnabled)
                        .onValueChange((cb, checked) -> shadowEnabled = checked)
                        .build());
                addRenderableWidget(Checkbox.builder(Component.translatable("createaddonorganizer.colors.shadowUnlinked"), this.font)
                        .pos(x, contentY + 24)
                        .selected(shadowUnlinked)
                        .onValueChange((cb, checked) -> {
                            shadowUnlinked = checked;
                            rebuildWidgets();
                        })
                        .build());
                if (shadowUnlinked) {
                    addColorControls(x, contentY + 50, shadowHsva);
                }
            }
        }
    }

    private void cycleTextTarget() {
        textEditTarget = switch (textEditTarget) {
            case PRIMARY -> TextTarget.SECONDARY;
            case SECONDARY -> TextTarget.OUTLINE;
            case OUTLINE -> TextTarget.SHADOW;
            case SHADOW -> TextTarget.PRIMARY;
        };
        editingStop2 = false;
        rebuildWidgets();
    }

    private void cycleTextTargetBackward() {
        textEditTarget = switch (textEditTarget) {
            case PRIMARY -> TextTarget.SHADOW;
            case SECONDARY -> TextTarget.PRIMARY;
            case OUTLINE -> TextTarget.SECONDARY;
            case SHADOW -> TextTarget.OUTLINE;
        };
        editingStop2 = false;
        rebuildWidgets();
    }

    private void resetActiveTextTarget() {
        switch (textEditTarget) {
            case PRIMARY -> {
                resetGradientTarget(Config.defaultTextSpec(), textHsva, textHsva2, v -> textGradientEnabled = v);
                scrollCutoff = Config.DEFAULT_SCROLL_CUTOFF.get().floatValue();
            }
            case SECONDARY -> resetGradientTarget(Config.defaultTextSecondarySpec(), text2Hsva, text2Hsva2,
                    v -> text2GradientEnabled = v);
            case OUTLINE -> resetGradientTarget(Config.defaultTextOutlineSpec(), outlineHsva, outlineHsva2,
                    v -> outlineGradientEnabled = v);
            case SHADOW -> {
                shadowEnabled = Config.TITLE_TEXT_SHADOW.get();
                shadowUnlinked = false;
                resetHsva(shadowHsva, Config.DEFAULT_TEXT_SHADOW_COLOR.get());
            }
        }
        editingStop2 = false;
        rebuildWidgets();
    }

    private static void resetGradientTarget(ColorSpec def, Hsva stop1, Hsva stop2, Consumer<Boolean> setGradientEnabled) {
        resetHsva(stop1, def.color1());
        resetHsva(stop2, def.isGradient() ? def.color2() : def.color1());
        setGradientEnabled.accept(def.isGradient());
    }

    private static void resetHsva(Hsva target, int argb) {
        Hsva def = Hsva.fromArgb(argb);
        target.h = def.h;
        target.s = def.s;
        target.v = def.v;
    }

    private Component textTargetLabel() {
        String key = switch (textEditTarget) {
            case PRIMARY -> "createaddonorganizer.colors.text.primary";
            case SECONDARY -> "createaddonorganizer.colors.text.secondary";
            case OUTLINE -> "createaddonorganizer.colors.text.outline";
            case SHADOW -> "createaddonorganizer.colors.text.shadow";
        };
        return Component.translatable("createaddonorganizer.colors.text.editing").copy().append(": ").append(Component.translatable(key));
    }

    private record BannerGradientExtras(ColorSpec.Direction direction, Consumer<ColorSpec.Direction> setDirection,
            ColorSpec.Style style, Consumer<ColorSpec.Style> setStyle) {}

    private int addGradientControls(int x, int y, boolean gradientEnabled, Runnable toggleGradient,
            Hsva stop1, Hsva stop2, BannerGradientExtras extras) {
        if (!gradientEnabled) {
            addRenderableWidget(new CycleActionButton(x, y, 200, ROW_H, gradientToggleLabel(false),
                    () -> { toggleGradient.run(); rebuildWidgets(); },
                    () -> { toggleGradient.run(); rebuildWidgets(); }));
            y += ROW_H + rowGap;
            return addColorControls(x, y, stop1);
        }

        addRenderableWidget(new CycleActionButton(x, y, 96, ROW_H, gradientToggleLabel(true),
                () -> { toggleGradient.run(); rebuildWidgets(); },
                () -> { toggleGradient.run(); rebuildWidgets(); }));
        addRenderableWidget(new CycleActionButton(x + 104, y, 96, ROW_H, stopLabel(),
                () -> { editingStop2 = !editingStop2; rebuildWidgets(); },
                () -> { editingStop2 = !editingStop2; rebuildWidgets(); }));
        y += ROW_H + rowGap;

        if (extras != null) {
            ColorSpec.Direction direction = extras.direction();
            addRenderableWidget(new CycleActionButton(x, y, 96, ROW_H, directionLabel(direction),
                    () -> { extras.setDirection().accept(nextDirection(direction)); rebuildWidgets(); },
                    () -> { extras.setDirection().accept(prevDirection(direction)); rebuildWidgets(); }));
            ColorSpec.Style style = extras.style();
            addRenderableWidget(new CycleActionButton(x + 104, y, 96, ROW_H, styleLabel(style),
                    () -> { extras.setStyle().accept(nextStyle(style)); rebuildWidgets(); },
                    () -> { extras.setStyle().accept(prevStyle(style)); rebuildWidgets(); }));
            y += ROW_H + rowGap;
        }

        return addColorControls(x, y, editingStop2 ? stop2 : stop1);
    }

    private static ColorSpec.Direction nextDirection(ColorSpec.Direction dir) {
        return switch (dir) {
            case VERTICAL -> ColorSpec.Direction.HORIZONTAL;
            case HORIZONTAL -> ColorSpec.Direction.DIAGONAL_UP;
            case DIAGONAL_UP -> ColorSpec.Direction.DIAGONAL_DOWN;
            case DIAGONAL_DOWN -> ColorSpec.Direction.VERTICAL;
        };
    }

    private static ColorSpec.Direction prevDirection(ColorSpec.Direction dir) {
        return switch (dir) {
            case VERTICAL -> ColorSpec.Direction.DIAGONAL_DOWN;
            case HORIZONTAL -> ColorSpec.Direction.VERTICAL;
            case DIAGONAL_UP -> ColorSpec.Direction.HORIZONTAL;
            case DIAGONAL_DOWN -> ColorSpec.Direction.DIAGONAL_UP;
        };
    }

    private static ColorSpec.Style nextStyle(ColorSpec.Style style) {
        return switch (style) {
            case SMOOTH -> ColorSpec.Style.DITHER_2X2;
            case DITHER_2X2 -> ColorSpec.Style.DITHER_4X4;
            case DITHER_4X4 -> ColorSpec.Style.DITHER_8X8;
            case DITHER_8X8 -> ColorSpec.Style.DITHER_TRICOLOR;
            case DITHER_TRICOLOR -> ColorSpec.Style.SMOOTH;
        };
    }

    private static ColorSpec.Style prevStyle(ColorSpec.Style style) {
        return switch (style) {
            case SMOOTH -> ColorSpec.Style.DITHER_TRICOLOR;
            case DITHER_2X2 -> ColorSpec.Style.SMOOTH;
            case DITHER_4X4 -> ColorSpec.Style.DITHER_2X2;
            case DITHER_8X8 -> ColorSpec.Style.DITHER_4X4;
            case DITHER_TRICOLOR -> ColorSpec.Style.DITHER_8X8;
        };
    }

    private Component gradientToggleLabel(boolean enabled) {
        String key = enabled ? "createaddonorganizer.colors.gradient.gradient" : "createaddonorganizer.colors.gradient.solid";
        return Component.translatable("createaddonorganizer.colors.gradient.mode").copy().append(": ").append(Component.translatable(key));
    }

    private Component stopLabel() {
        String key = editingStop2 ? "createaddonorganizer.colors.gradient.stop2" : "createaddonorganizer.colors.gradient.stop1";
        return Component.translatable("createaddonorganizer.colors.gradient.editing").copy().append(": ").append(Component.translatable(key));
    }

    private Component directionLabel(ColorSpec.Direction dir) {
        String key = switch (dir) {
            case VERTICAL -> "createaddonorganizer.colors.gradient.direction.vertical";
            case HORIZONTAL -> "createaddonorganizer.colors.gradient.direction.horizontal";
            case DIAGONAL_UP -> "createaddonorganizer.colors.gradient.direction.diagonalUp";
            case DIAGONAL_DOWN -> "createaddonorganizer.colors.gradient.direction.diagonalDown";
        };
        return Component.translatable("createaddonorganizer.colors.gradient.direction").copy().append(": ").append(Component.translatable(key));
    }

    private Component styleLabel(ColorSpec.Style style) {
        String key = switch (style) {
            case SMOOTH -> "createaddonorganizer.colors.gradient.style.smooth";
            case DITHER_2X2 -> "createaddonorganizer.colors.gradient.style.dither2x2";
            case DITHER_4X4 -> "createaddonorganizer.colors.gradient.style.dither4x4";
            case DITHER_8X8 -> "createaddonorganizer.colors.gradient.style.dither8x8";
            case DITHER_TRICOLOR -> "createaddonorganizer.colors.gradient.style.dithertricolor";
        };
        return Component.translatable("createaddonorganizer.colors.gradient.style").copy().append(": ").append(Component.translatable(key));
    }

    private int addColorControls(int x, int y, Hsva target) {
        int width = 200;

        int barY = y + squareHeight + rowGap;
        int hexY = barY + barHeight + rowGap;

        EditBox hexBox = new EditBox(this.font, x, hexY, width - 50, 20, Component.empty());
        hexBox.setMaxLength(7);
        hexBox.setValue(hex6(target));

        boolean[] refreshing = {false};
        hexBox.setResponder(text -> {
            if (!refreshing[0]) {
                applyHex(text, target, null);
            }
        });
        Runnable refreshHexText = () -> {
            refreshing[0] = true;
            hexBox.setValue(hex6(target));
            refreshing[0] = false;
        };

        IntSupplier cellSize = Config::gradientCellSize;
        addRenderableWidget(new SvSquare(x, y, width, squareHeight, target, refreshHexText, cellSize, svSquareTexture));
        addRenderableWidget(new HueBar(x, barY, width, barHeight, target, refreshHexText, cellSize, hueBarTexture));
        addRenderableWidget(hexBox);
        addRenderableWidget(Button.builder(Component.translatable("createaddonorganizer.colors.copy"),
                        b -> this.minecraft.keyboardHandler.setClipboard(hexBox.getValue()))
                .bounds(x + width - 44, hexY, 44, 20).build());

        return hexY + 20;
    }

    private static String hex6(Hsva hsva) {
        return String.format(Locale.ROOT, "#%06X", ColorUtil.hsvToRgb(hsva.h, hsva.s, hsva.v));
    }

    private static void applyHex(String raw, Hsva target, Runnable afterChange) {
        String s = raw.startsWith("#") ? raw.substring(1) : raw;
        if (s.length() != 6) {
            return;
        }
        try {
            int rgb = Integer.parseInt(s, 16);
            float[] hsv = ColorUtil.rgbToHsv(0xFF000000 | rgb);
            target.h = hsv[0];
            target.s = hsv[1];
            target.v = hsv[2];
            if (afterChange != null) {
                afterChange.run();
            }
        } catch (NumberFormatException ignored) {

        }
    }

    private void resetBanner() {
        ColorSpec def = Config.defaultBannerSpec();
        Hsva stop1 = Hsva.fromArgb(def.color1());
        bannerHsva.h = stop1.h;
        bannerHsva.s = stop1.s;
        bannerHsva.v = stop1.v;
        bannerHsva.a = stop1.a;
        resetHsva(bannerHsva2, def.isGradient() ? def.color2() : def.color1());
        bannerGradientEnabled = def.isGradient();
        bannerDirection = def.direction();
        bannerStyle = def.style();
        editingStop2 = false;
        mode = Mode.COLOR;
        selectedRef = null;
        selectedTexture = null;
        syncAnimationFields();
        rebuildWidgets();
    }

    private void resetBox() {
        Hsva def = Hsva.fromArgb(Config.DEFAULT_BOX_COLOR.get());
        boxHsva.h = def.h;
        boxHsva.s = def.s;
        boxHsva.v = def.v;
        boxHsva.a = def.a;
        boxMode = Mode.COLOR;
        selectedBoxRef = null;
        selectedBoxTexture = null;
        rebuildWidgets();
    }

    private Component modeLabel(Mode m) {
        String key = m == Mode.COLOR
                ? "createaddonorganizer.banner.mode.color"
                : "createaddonorganizer.banner.mode.image";
        return Component.translatable("createaddonorganizer.banner.mode").copy().append(": ").append(Component.translatable(key));
    }

    private void uploadBox() {
        Optional<Path> chosen = BoxTextures.chooseFile();
        if (chosen.isEmpty()) {
            return;
        }
        try {
            String ref = BoxTextures.importFile(chosen.get());
            ResourceLocation tex = BoxTextures.resolve(ref);
            if (tex != null) {
                this.selectedBoxRef = ref;
                this.selectedBoxTexture = tex;
                rebuildWidgets();
            }
        } catch (IOException e) {
            createaddonorganizer.LOGGER.warn("[CAO] failed to import box texture image", e);
        }
    }

    private void confirmDeleteBox() {
        String ref = this.selectedBoxRef;
        this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                BoxTextures.deleteFile(ref);
                this.selectedBoxRef = null;
                this.selectedBoxTexture = null;
            }
            this.minecraft.setScreen(this);
        }, Component.translatable("createaddonorganizer.box.delete.title"),
                Component.translatable("createaddonorganizer.box.delete.message", ref)));
    }

    private Component currentTitle() {
        String key = switch (target) {
            case BANNER -> "createaddonorganizer.colors.target.banner";
            case BOX -> "createaddonorganizer.colors.target.box";
            case TEXT -> "createaddonorganizer.colors.target.text";
            case HIGHLIGHT -> "createaddonorganizer.colors.target.highlight";
        };
        return this.title.copy().append(" — ").append(Component.translatable(key));
    }

    private void upload() {
        Optional<Path> chosen = BannerTextures.chooseFile();
        if (chosen.isEmpty()) {
            return;
        }
        try {
            String ref = BannerTextures.importFile(chosen.get());
            ResourceLocation tex = BannerTextures.resolve(ref);
            if (tex != null) {
                this.selectedRef = ref;
                this.selectedTexture = tex;
                Config.addExtraPoolEntry(id, ref);
                syncAnimationFields();
                rebuildWidgets();
            }
        } catch (IOException e) {
            createaddonorganizer.LOGGER.warn("[CAO] failed to import banner image", e);
        }
    }

    private void confirmDelete() {
        String ref = this.selectedRef;
        ResourceLocation texture = this.selectedTexture;
        this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                BannerTextures.deleteFile(ref);
                if (texture != null) {
                    Config.clearAnimatedBanner(texture);
                    BannerAnimation.invalidate(texture);
                }
                Config.removeExtraPoolEntriesForRef(ref);
                this.selectedRef = null;
                this.selectedTexture = null;
                syncAnimationFields();
            }
            this.minecraft.setScreen(this);
        }, Component.translatable("createaddonorganizer.banner.delete.title"),
                Component.translatable("createaddonorganizer.banner.delete.message", ref)));
    }

    private static ColorSpec buildSpec(Hsva stop1, boolean gradientEnabled, Hsva stop2, ColorSpec.Direction direction,
            ColorSpec.Style style) {
        if (!gradientEnabled) {
            return ColorSpec.solid(stop1.toArgb());
        }
        return new ColorSpec(stop1.toArgb(), stop2.toArgb(), direction, style);
    }

    private void confirm() {
        if (highlightOnly) {
            if (hasHighlight) {
                Config.setHighlightColor(id, highlightHsva.toArgb());
            } else {
                Config.clearHighlightColor(id);
            }
            onClose();
            return;
        }

        if (mode == Mode.COLOR) {
            ColorSpec bannerSpec = buildSpec(bannerHsva, bannerGradientEnabled, bannerHsva2, bannerDirection, bannerStyle);
            Config.clearSectionBanner(id);
            Config.setSectionColor(id, bannerSpec);
            LiveColors.apply(id, bannerSpec);
        } else if (selectedRef != null && selectedTexture != null) {
            Config.setSectionBanner(id, selectedRef);
            LiveColors.applyTexture(id, selectedTexture);
            if (BannerAnimation.isAnimatable(selectedTexture)) {
                if (bannerAnimated) {
                    Config.setAnimatedBanner(selectedTexture, bannerFrameTicks);
                } else {
                    Config.clearAnimatedBanner(selectedTexture);
                }
                BannerAnimation.invalidate(selectedTexture);
            }
        }

        ColorSpec textSpec = buildSpec(textHsva, textGradientEnabled, textHsva2, ColorSpec.Direction.HORIZONTAL, ColorSpec.Style.SMOOTH);
        Config.setTextColor(id, textSpec);
        LiveColors.applyTextColor(id, textSpec);
        Config.setScrollCutoff(id, scrollCutoff);

        if (boxMode == Mode.COLOR) {
            Config.clearSectionBoxTexture(id);
            Config.setBoxColor(id, boxHsva.toArgb());
        } else if (selectedBoxRef != null && selectedBoxTexture != null) {
            Config.setSectionBoxTexture(id, selectedBoxRef);
        }

        if (twoTone) {
            ColorSpec secondarySpec = buildSpec(text2Hsva, text2GradientEnabled, text2Hsva2, ColorSpec.Direction.HORIZONTAL, ColorSpec.Style.SMOOTH);
            Config.setTextSecondaryColor(id, secondarySpec);
            Config.setTwoToneSplit(id, twoToneSplit);
        } else {
            Config.clearTextSecondaryColor(id);
            Config.clearTwoToneSplit(id);
        }

        if (outlineEnabled) {
            ColorSpec outlineSpec = buildSpec(outlineHsva, outlineGradientEnabled, outlineHsva2, ColorSpec.Direction.HORIZONTAL, ColorSpec.Style.SMOOTH);
            Config.setTextOutlineColor(id, outlineSpec);
        } else {
            Config.clearTextOutlineColor(id);
        }
        Config.setTitleTextShadow(id, shadowEnabled);
        if (shadowUnlinked) {
            Config.setTextShadowColor(id, shadowHsva.toArgb());
        } else {
            Config.clearTextShadowColor(id);
        }

        if (isMainTab) {
            if (hasHighlight) {
                Config.setHighlightColor(id, highlightHsva.toArgb());
            } else {
                Config.clearHighlightColor(id);
            }
        }

        onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (!highlightOnly && button == 0) {
            EditTarget hit = hitTest((int) mouseX, (int) mouseY);
            if (hit != null && !Config.editorHintSeen()) {

                Config.setEditorHintSeen(true);
            }
            if (hit != null && hit != target) {
                target = hit;
                editingStop2 = false;
                rebuildWidgets();
                return true;
            }
        }
        return false;
    }

    private EditTarget hitTest(int mx, int my) {
        int bx = this.width / 2 - BannerTextures.WIDTH / 2;
        int by = previewY;
        boolean hasBanner = mode == Mode.COLOR || selectedTexture != null;
        if (hasBanner) {
            int textX = bx + 5;
            int textY = by + 4;
            int w = this.font.width(this.sectionName);
            if (within(mx, my, textX, textY, textX + w, textY + 9)) {
                return EditTarget.TEXT;
            }
            if (within(mx, my, textX - 4, textY - 3, textX + w + 3, textY + 9 + 2)) {
                return EditTarget.BOX;
            }
        }
        if (within(mx, my, bx, by, bx + BannerTextures.WIDTH, by + CaoSection.CONTENT_H)) {
            return EditTarget.BANNER;
        }
        return null;
    }

    private static boolean within(int x, int y, int x1, int y1, int x2, int y2) {
        return x >= x1 && x < x2 && y >= y1 && y < y2;
    }

    private static String displayFilename(String ref) {
        if (ref.startsWith("file:") || ref.startsWith("remote:")) {
            return ref.substring(ref.indexOf(':') + 1);
        }
        int slash = ref.lastIndexOf('/');
        return slash >= 0 ? ref.substring(slash + 1) : ref;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hoverBannerTooltip = null;
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, currentTitle(), this.width / 2, 16, 0xFFFFFFFF);

        if (hoverBannerTooltip != null) {
            g.renderTooltip(this.font, hoverBannerTooltip, mouseX, mouseY);
        }

        if (highlightOnly) {
            return;
        }

        int bx = this.width / 2 - BannerTextures.WIDTH / 2;
        int by = previewY;
        g.fill(bx - 2, by - 2, bx + BannerTextures.WIDTH + 2, by + CaoSection.CONTENT_H + 2, 0xD0202020);

        boolean hasBanner = hasBanner();
        drawBannerContent(g, bx, by, mouseX, mouseY, false);

        EditTarget hovered = hitTest(mouseX, mouseY);

        boolean pulsing = !Config.editorHintSeen();

        if (hasBanner) {
            int textX = bx + 5;
            int textY = by + 4;
            int w = this.font.width(this.sectionName);
            if (pulsing) {
                outline(g, textX - 4, textY - 3, textX + w + 3, textY + 9 + 2, pulseColor(0.12f));
                outline(g, textX, textY, textX + w, textY + 9, pulseColor(0.24f));
            }
            if (hovered == EditTarget.TEXT) {
                outline(g, textX, textY, textX + w, textY + 9);
            } else if (hovered == EditTarget.BOX) {
                outline(g, textX - 4, textY - 3, textX + w + 3, textY + 9 + 2);
            }
        }
        if (pulsing) {
            outline(g, bx - 2, by - 2, bx + BannerTextures.WIDTH + 2, by + CaoSection.CONTENT_H + 2, pulseColor(0f));
        }
        if (hovered == EditTarget.BANNER) {
            outline(g, bx - 2, by - 2, bx + BannerTextures.WIDTH + 2, by + CaoSection.CONTENT_H + 2);
        }
    }

    private static final long PULSE_MS = 1700;

    private static int pulseColor(float phaseOffset) {
        float phase = (System.currentTimeMillis() % PULSE_MS) / (float) PULSE_MS + phaseOffset;
        float wave = (Mth.sin(phase * Mth.TWO_PI) + 1f) / 2f;
        int a = 0x30 + Math.round((0xB0 - 0x30) * wave);
        return (a << 24) | 0x00FFFFFF;
    }

    private static void outline(GuiGraphics g, int x1, int y1, int x2, int y2) {
        outline(g, x1, y1, x2, y2, 0xFFFFFFFF);
    }

    private static void outline(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y1 + 1, color);
        g.fill(x1, y2 - 1, x2, y2, color);
        g.fill(x1, y1, x1 + 1, y2, color);
        g.fill(x2 - 1, y1, x2, y2, color);
    }

    private static void drawMarker(GuiGraphics g, int cx, int cy) {
        outline(g, cx - 5, cy - 5, cx + 5, cy + 5, 0xFF000000);
        outline(g, cx - 4, cy - 4, cx + 4, cy + 4, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        svSquareTexture.release();
        hueBarTexture.release();
        this.minecraft.setScreen(parent);
    }

    private boolean hasBanner() {
        return mode == Mode.COLOR || selectedTexture != null;
    }

    private void captureScreenshot() {
        BannerScreenshot.capture(BannerTextures.WIDTH, BannerTextures.HEIGHT, sectionName.getString(),
                g -> drawBannerContent(g, 0, 0, -1, -1, true));
    }

    private void drawBannerContent(GuiGraphics g, int bx, int by, int mouseX, int mouseY, boolean staticFrame) {
        int renderHeight = staticFrame ? BannerTextures.HEIGHT : CaoSection.CONTENT_H;
        if (mode == Mode.COLOR) {
            ColorSpec bannerSpec = buildSpec(bannerHsva, bannerGradientEnabled, bannerHsva2, bannerDirection, bannerStyle);
            BannerFill.draw(g, bx, by, bx + BannerTextures.WIDTH, by + renderHeight, bannerSpec);
        } else if (selectedTexture != null) {
            float v = 0.0F;
            int texHeight = BannerTextures.HEIGHT;
            var anim = BannerAnimation.preview(selectedTexture, bannerAnimated, bannerFrameTicks);
            if (anim.isPresent()) {
                boolean hovered = !staticFrame
                        && BannerAnimation.isHovering(bx, by, BannerTextures.WIDTH, CaoSection.CONTENT_H, mouseX, mouseY);
                int frame = staticFrame ? 0 : BannerAnimation.currentFrame(selectedTexture, anim.get(), hovered);
                v = frame * BannerTextures.HEIGHT;
                texHeight = anim.get().frameCount() * BannerTextures.HEIGHT;
            }

            g.blit(selectedTexture, bx, by, BannerTextures.WIDTH, renderHeight, 0.0F, v,
                    BannerTextures.WIDTH, BannerTextures.HEIGHT, BannerTextures.WIDTH, texHeight);
        } else {
            g.drawCenteredString(this.font, Component.translatable("createaddonorganizer.banner.none"),
                    bx + BannerTextures.WIDTH / 2, by + (renderHeight - 8) / 2, 0xFF888888);
        }

        if (hasBanner()) {
            int textX = bx + 5;
            int textY = by + 4;
            int maxX = bx + BannerTextures.WIDTH - 3;
            int available = maxX - textX;
            int viewMaxX = textX + Math.round(available * scrollCutoff);
            int viewAvailable = viewMaxX - textX;
            boolean scrolling = !staticFrame && this.font.width(this.sectionName) > viewAvailable;
            if (Config.tintedTextBox()) {
                int w = scrolling ? viewAvailable : this.font.width(this.sectionName);
                ResourceLocation boxTex = boxMode == Mode.IMAGE ? selectedBoxTexture : null;
                BoxTextures.draw(g, boxTex, textX - 4, textY - 3, textX + w + 3, textY + 9 + 2, boxHsva.toArgb());
            }
            boolean vanillaShadow = shadowEnabled && !shadowUnlinked;
            int manualShadowArgb = shadowEnabled && shadowUnlinked ? shadowHsva.toArgb() : 0;
            ColorSpec textSpec = buildSpec(textHsva, textGradientEnabled, textHsva2, ColorSpec.Direction.HORIZONTAL, ColorSpec.Style.SMOOTH);
            ColorSpec secondarySpec = twoTone
                    ? buildSpec(text2Hsva, text2GradientEnabled, text2Hsva2, ColorSpec.Direction.HORIZONTAL, ColorSpec.Style.SMOOTH)
                    : null;
            ColorSpec outlineSpec = outlineEnabled
                    ? buildSpec(outlineHsva, outlineGradientEnabled, outlineHsva2, ColorSpec.Direction.HORIZONTAL, ColorSpec.Style.SMOOTH)
                    : null;
            TwoToneText.draw(g, this.font, this.sectionName, textX, textY, viewMaxX, textSpec,
                    secondarySpec, twoToneSplit, vanillaShadow, manualShadowArgb, outlineSpec);

            if (!staticFrame && target == EditTarget.TEXT && textEditTarget == TextTarget.PRIMARY) {
                g.fill(viewMaxX, by, viewMaxX + 1, by + renderHeight, 0xFFFFFF55);
            }
        }
    }

    private static final class Hsva {
        float h;
        float s;
        float v;
        float a = 1f;

        int toArgb() {
            return (Math.round(a * 255) << 24) | ColorUtil.hsvToRgb(h, s, v);
        }

        static Hsva fromArgb(int argb) {
            Hsva hsva = new Hsva();
            float[] hsv = ColorUtil.rgbToHsv(argb);
            hsva.h = hsv[0];
            hsva.s = hsv[1];
            hsva.v = hsv[2];
            hsva.a = ((argb >>> 24) & 0xFF) / 255f;
            return hsva;
        }
    }

    private static class ChannelSlider extends AbstractSliderButton {
        private final Component label;
        private final DoubleConsumer onChange;
        private final DoubleFunction<String> formatter;

        ChannelSlider(int x, int y, int w, int h, Component label, double initial, DoubleConsumer onChange) {
            this(x, y, w, h, label, initial, onChange, null);
        }

        ChannelSlider(int x, int y, int w, int h, Component label, double initial, DoubleConsumer onChange,
                DoubleFunction<String> formatter) {
            super(x, y, w, h, Component.empty(), initial);
            this.label = label;
            this.onChange = onChange;
            this.formatter = formatter;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            String suffix = formatter != null ? formatter.apply(this.value) : Math.round(this.value * 100) + "%";
            setMessage(label.copy().append(": " + suffix));
        }

        @Override
        protected void applyValue() {
            onChange.accept(this.value);
        }
    }

    @FunctionalInterface
    private interface CellColor {
        int argb(float fracX, float fracY);
    }

    private static final class GradientTexture {
        private final ResourceLocation id;
        private DynamicTexture texture;
        private float keyA = Float.NaN;
        private int keyCell = -1;

        GradientTexture(String name) {
            this.id = ResourceLocation.fromNamespaceAndPath(createaddonorganizer.MODID, "gradient_cache/" + name);
        }

        ResourceLocation ensure(int w, int h, float keyA, int cellSize, CellColor fn) {
            boolean sizeMatches = texture != null
                    && texture.getPixels() != null
                    && texture.getPixels().getWidth() == w
                    && texture.getPixels().getHeight() == h;
            if (sizeMatches && this.keyA == keyA && this.keyCell == cellSize) {
                return id;
            }
            NativeImage image = sizeMatches ? texture.getPixels() : new NativeImage(w, h, false);
            for (int cy = 0; cy < h; cy += cellSize) {
                int ch = Math.min(cellSize, h - cy);
                float fracY = (cy + ch / 2f) / h;
                for (int cx = 0; cx < w; cx += cellSize) {
                    int cw = Math.min(cellSize, w - cx);
                    float fracX = (cx + cw / 2f) / w;
                    int abgr = FastColor.ABGR32.fromArgb32(0xFF000000 | fn.argb(fracX, fracY));
                    for (int py = cy; py < cy + ch; py++) {
                        for (int px = cx; px < cx + cw; px++) {
                            image.setPixelRGBA(px, py, abgr);
                        }
                    }
                }
            }
            if (!sizeMatches) {
                if (texture != null) {
                    Minecraft.getInstance().getTextureManager().release(id);
                    texture.close();
                }
                texture = new DynamicTexture(image);
                Minecraft.getInstance().getTextureManager().register(id, texture);
            } else {
                texture.upload();
            }
            this.keyA = keyA;
            this.keyCell = cellSize;
            return id;
        }

        void release() {
            if (texture != null) {
                Minecraft.getInstance().getTextureManager().release(id);
                texture = null;
                keyA = Float.NaN;
                keyCell = -1;
            }
        }
    }

    private static class SvSquare extends AbstractWidget {
        private final Hsva target;
        private final Runnable afterChange;
        private final IntSupplier cellSize;
        private final GradientTexture gradient;

        SvSquare(int x, int y, int w, int h, Hsva target, Runnable afterChange, IntSupplier cellSize, GradientTexture gradient) {
            super(x, y, w, h, Component.empty());
            this.target = target;
            this.afterChange = afterChange;
            this.cellSize = cellSize;
            this.gradient = gradient;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            update(mouseX, mouseY);
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            update(mouseX, mouseY);
        }

        private void update(double mouseX, double mouseY) {
            target.s = Mth.clamp((float) ((mouseX - getX()) / getWidth()), 0f, 1f);
            target.v = Mth.clamp((float) (1 - (mouseY - getY()) / getHeight()), 0f, 1f);
            afterChange.run();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x0 = getX();
            int y0 = getY();
            int w = getWidth();
            int h = getHeight();
            ResourceLocation tex = gradient.ensure(w, h, target.h, cellSize.getAsInt(),
                    (fracX, fracY) -> ColorUtil.hsvToRgb(target.h, fracX, 1f - fracY));
            g.blit(tex, x0, y0, 0.0F, 0.0F, w, h, w, h);

            int mx = x0 + Math.round(target.s * w);
            int my = y0 + Math.round((1 - target.v) * h);
            drawMarker(g, mx, my);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {}
    }

    private static class HueBar extends ChannelSlider {
        private final Hsva target;
        private final IntSupplier cellSize;
        private final GradientTexture gradient;

        HueBar(int x, int y, int w, int h, Hsva target, Runnable afterChange, IntSupplier cellSize, GradientTexture gradient) {
            super(x, y, w, h, Component.translatable("createaddonorganizer.colors.hue"), target.h,
                    v -> {
                        target.h = (float) v;
                        afterChange.run();
                    });
            this.target = target;
            this.cellSize = cellSize;
            this.gradient = gradient;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x0 = getX();
            int y0 = getY();
            int w = getWidth();
            int h = getHeight();
            ResourceLocation tex = gradient.ensure(w, h, 0f, cellSize.getAsInt(),
                    (fracX, fracY) -> ColorUtil.hsvToRgb(fracX, 1f, 1f));
            g.blit(tex, x0, y0, 0.0F, 0.0F, w, h, w, h);

            int mx = x0 + Math.round(target.h * w);
            drawMarker(g, mx, y0 + h / 2);
        }
    }

    private class GalleryList extends ContainerObjectSelectionList<GalleryList.Row> {
        GalleryList(Minecraft mc, int width, int height, int top, int itemHeight) {
            super(mc, width, height, top, itemHeight);
        }

        void add(String ref) {
            addEntry(new Row(ref));
        }

        @Override
        public int getRowWidth() {
            return BannerTextures.WIDTH + 12;
        }

        private class Row extends ContainerObjectSelectionList.Entry<Row> {
            private final String ref;

            Row(String ref) {
                this.ref = ref;
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
            public boolean mouseClicked(double mx, double my, int button) {
                ResourceLocation texture = BannerTextures.resolve(ref);
                if (button == 0 && texture != null) {
                    selectedTexture = texture;
                    selectedRef = ref;
                    syncAnimationFields();
                    rebuildWidgets();
                    return true;
                }
                return false;
            }

            @Override
            public void render(GuiGraphics g, int index, int top, int left, int rowWidth, int rowHeight,
                    int mouseX, int mouseY, boolean hovered, float partialTick) {
                int bx = left + (rowWidth - BannerTextures.WIDTH) / 2;
                int by = top + (rowHeight - BannerTextures.HEIGHT) / 2;
                boolean selected = ref.equals(selectedRef);
                if (selected) {
                    outline(g, bx - 1, by - 1, bx + BannerTextures.WIDTH + 1, by + BannerTextures.HEIGHT + 1, 0xFFFFFFFF);
                } else if (hovered) {
                    outline(g, bx - 1, by - 1, bx + BannerTextures.WIDTH + 1, by + BannerTextures.HEIGHT + 1, 0x80FFFFFF);
                }
                ResourceLocation texture = BannerTextures.resolve(ref);
                boolean bannerHovered = BannerAnimation.isHovering(bx, by, BannerTextures.WIDTH, BannerTextures.HEIGHT, mouseX, mouseY);
                if (texture != null) {
                    Optional<BannerAnimation.AnimInfo> anim = BannerAnimation.get(texture);
                    int frameCount = anim.map(BannerAnimation.AnimInfo::frameCount).orElse(1);
                    int frame = anim.map(info -> BannerAnimation.currentFrame(texture, info, bannerHovered)).orElse(0);
                    g.blit(texture, bx, by, 0.0F, frame * BannerTextures.HEIGHT, BannerTextures.WIDTH, BannerTextures.HEIGHT,
                            BannerTextures.WIDTH, frameCount * BannerTextures.HEIGHT);
                }
                if (bannerHovered) {
                    ColorPickerScreen.this.hoverBannerTooltip = Component.literal(displayFilename(ref));
                }
            }
        }
    }

    private class BoxGalleryList extends ContainerObjectSelectionList<BoxGalleryList.Row> {
        BoxGalleryList(Minecraft mc, int width, int height, int top, int itemHeight) {
            super(mc, width, height, top, itemHeight);
        }

        void add(String ref) {
            addEntry(new Row(ref));
        }

        @Override
        public int getRowWidth() {
            return BannerTextures.WIDTH + 12;
        }

        private class Row extends ContainerObjectSelectionList.Entry<Row> {
            private final String ref;

            Row(String ref) {
                this.ref = ref;
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
            public boolean mouseClicked(double mx, double my, int button) {
                ResourceLocation texture = BoxTextures.resolve(ref);
                if (button == 0 && texture != null) {
                    selectedBoxTexture = texture;
                    selectedBoxRef = ref;
                    rebuildWidgets();
                    return true;
                }
                return false;
            }

            @Override
            public void render(GuiGraphics g, int index, int top, int left, int rowWidth, int rowHeight,
                    int mouseX, int mouseY, boolean hovered, float partialTick) {
                int previewW = BannerTextures.WIDTH;
                int previewH = BannerTextures.HEIGHT;
                int bx = left + (rowWidth - previewW) / 2;
                int by = top + (rowHeight - previewH) / 2;
                boolean selected = ref.equals(selectedBoxRef);
                if (selected) {
                    outline(g, bx - 1, by - 1, bx + previewW + 1, by + previewH + 1, 0xFFFFFFFF);
                } else if (hovered) {
                    outline(g, bx - 1, by - 1, bx + previewW + 1, by + previewH + 1, 0x80FFFFFF);
                }
                ResourceLocation texture = BoxTextures.resolve(ref);
                OptionalInt nativeW = texture != null ? BoxTextures.nativeWidth(texture) : OptionalInt.empty();
                if (texture != null && nativeW.isPresent()) {
                    BoxTextures.blit3Slice(g, texture, bx, by, previewW, previewH, nativeW.getAsInt(), BoxTextures.HEIGHT);
                }
            }
        }
    }
}
