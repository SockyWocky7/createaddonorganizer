package com.sockywocky.createaddonorganizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.sockywocky.createaddonorganizer.client.ColorSpec;
import com.sockywocky.createaddonorganizer.client.ColorUtil;
import com.sockywocky.createaddonorganizer.client.simulated.SimulatedSupport;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final Set<String> BUILTIN_INCLUDE = Set.of(
            "bits_n_bobs:bnb_based",
            "bits_n_bobs:bnb_palettes",
            "bits_n_bobs:bnb_deco",
            "create_more_automation:create_more_automation");

    private static final Map<String, String> BUILTIN_ROUTES = Map.of(
            "bits_n_bobs:bnb_palettes", "create:palettes",
            "bits_n_bobs:bnb_deco", "create:palettes",
            "railways:palettes", "create:palettes");

    private static final Set<String> BUILTIN_EXCLUDE = Set.of();

    public static final ModConfigSpec.BooleanValue CLASSIC_ORGANIZER_LAYOUT = BUILDER
            .comment("Use the classic (pre-1.3) organizer menu: centered column, per-row Edit button, no search or sidebar.")
            .define("classicOrganizerLayout", true);

    public static final ModConfigSpec.BooleanValue SHOW_COLLAPSE_TOGGLE = BUILDER
            .comment("Show Fancy Tab Sections' collapse/expand button on each banner. Off by default.")
            .define("showCollapseToggle", false);

    public static final ModConfigSpec.BooleanValue STICKY_SECTION_BANNERS = BUILDER
            .comment("Keep each section's banner pinned to the top while scrolling its items, instead of scrolling away.")
            .define("stickySectionBanners", false);

    static {
        BUILDER.comment("Which addon tabs get absorbed into Create, and where.")
                .push("absorption");
    }

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FORCE_INCLUDE = BUILDER
            .comment("Tab IDs to always absorb under Create, even without a Create dependency.")
            .defineListAllowEmpty("forceInclude", List.of(), () -> "somemod:main", Config::isValidTabId);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FORCE_EXCLUDE = BUILDER
            .comment("Tab IDs to never absorb; they keep their own standalone tab.")
            .defineListAllowEmpty("forceExclude", List.of(), () -> "somemod:main", Config::isValidTabId);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> ROUTES = BUILDER
            .comment("Fold a tab into a chosen Create parent tab. Format: \"<addonTabId> > <parentTabId>\",",
                    "e.g. \"somemod:deco > create:palettes\".")
            .defineListAllowEmpty("routes", List.of(), () -> "somemod:deco > create:palettes", Config::isValidRoute);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXTRA_MAIN_SECTIONS = BUILDER
            .comment("Tabs promoted to hub status (can have others folded into them) even without routes",
                    "pointing at them yet. Managed via shift+\"+\" in the creative menu.")
            .defineListAllowEmpty("extraMainSections", List.of(), () -> "somemod:main", Config::isValidTabId);

    static {
        BUILDER.pop();
        BUILDER.comment("Banner, box, and title-text styling for sections.")
                .push("appearance");
    }

    public static final ModConfigSpec.IntValue DEFAULT_BANNER_COLOR = BUILDER
            .comment("Default section banner colour (ARGB int).")
            .defineInRange("defaultBannerColor", 0xFF262626, Integer.MIN_VALUE, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> DEFAULT_BANNER_GRADIENT = BUILDER
            .comment("Optional gradient for the default banner, as \"<secondHex>|<DIRECTION>|<STYLE>\"",
                    "(DIRECTION: VERTICAL/HORIZONTAL/DIAGONAL_UP/DIAGONAL_DOWN, STYLE: SMOOTH/DITHER_2X2/",
                    "DITHER_4X4/DITHER_TRICOLOR/DITHER_QUADCOLOR). Empty (default) means a flat colour.")
            .define("defaultBannerGradient", "");

    public static final ModConfigSpec.ConfigValue<List<? extends String>> SECTION_COLORS = BUILDER
            .comment("Per-section banner colours, keyed by tab ID. Format: \"<tabId> = <hex>\", optionally a",
                    "gradient: \"<tabId> = <hex1>|<hex2>|<DIRECTION>|<STYLE>\". Accepts #RRGGBB, #AARRGGBB, or",
                    "0x-prefixed hex.")
            .defineListAllowEmpty("sectionColors", List.of(), () -> "somemod:main = #4A4A4A", Config::isValidBannerColorSpecEntry);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> BANNERS = BUILDER
            .comment("Per-section banner IMAGES, keyed by tab ID; overrides the colour. Format:",
                    "\"<tabId> = <ref>\" (\"res:ns:path\", \"file:name.png\", or \"remote:name.png\").",
                    "Managed by the in-game banner editor; banners are 160x17.")
            .defineListAllowEmpty("banners", List.of(),
                    () -> "somemod:main = res:createaddonorganizer:textures/banner/create1.png", Config::isValidBanner);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> ANIMATED_BANNERS = BUILDER
            .comment("Marks a banner texture as animated (vertical strip of 17px frames), keyed by texture id.",
                    "Format: \"<textureId> = <frametime>\" (ticks). Bundled textures auto-detect via .mcmeta.",
                    "Managed by the in-game banner editor.")
            .defineListAllowEmpty("animatedBanners", List.of(),
                    () -> "createaddonorganizer:custom_banner/example = 2", Config::isValidAnimatedBanner);

    public static final ModConfigSpec.BooleanValue SHOW_ALL_BANNERS = BUILDER
            .comment("Ignore curated banner pools and always show the full gallery.")
            .define("showAllBanners", false);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXTRA_BANNER_POOL = BUILDER
            .comment("Your own uploads added to a curated tab's banner pool, keyed by tab ID. Format:",
                    "\"<tabId> = <ref>\". Managed by the in-game banner editor.")
            .defineListAllowEmpty("extraBannerPool", List.of(),
                    () -> "somemod:main = file:example.png", Config::isValidBanner);

    public static final ModConfigSpec.BooleanValue FETCH_ONLINE_BANNERS = BUILDER
            .comment("Check GitHub once per launch for new/updated community banners and credits. Disable",
                    "for offline use.")
            .define("fetchOnlineBanners", true);

    public static final ModConfigSpec.ConfigValue<String> BANNER_MANIFEST_URL = BUILDER
            .comment("URL of the remote banner manifest (JSON). Used only when fetchOnlineBanners is true.")
            .define("bannerManifestUrl",
                    "https://cdn.jsdelivr.net/gh/SockyWocky7/createaddonorganizer@master/banners/index.json",
                    Config::isValidUrl);

    public static final ModConfigSpec.ConfigValue<String> BANNER_POOLS_MANIFEST_URL = BUILDER
            .comment("URL of the remote banner-pool manifest (JSON) -- curated banners per tab. Used only",
                    "when fetchOnlineBanners is true; edit the file at this URL to update pools without a",
                    "mod update.")
            .define("bannerPoolsManifestUrl",
                    "https://cdn.jsdelivr.net/gh/SockyWocky7/createaddonorganizer@master/banners/pools.json",
                    Config::isValidUrl);

    public static final ModConfigSpec.BooleanValue FETCH_ONLINE_BOX_TEXTURES = BUILDER
            .comment("Check GitHub once per launch for new/updated community text-banner textures. Disable",
                    "for offline use.")
            .define("fetchOnlineBoxTextures", true);

    public static final ModConfigSpec.ConfigValue<String> BOX_MANIFEST_URL = BUILDER
            .comment("URL of the remote text-banner manifest (JSON). Used only when fetchOnlineBoxTextures",
                    "is true.")
            .define("boxManifestUrl",
                    "https://cdn.jsdelivr.net/gh/SockyWocky7/createaddonorganizer@master/text_banners/index.json",
                    Config::isValidUrl);

    public static final ModConfigSpec.BooleanValue TINTED_TEXT_BOX = BUILDER
            .comment("Draw a semi-transparent box behind section title text for contrast.")
            .define("tintedTextBox", true);

    public static final ModConfigSpec.IntValue DEFAULT_BOX_COLOR = BUILDER
            .comment("Default tinted-box colour (ARGB int).")
            .defineInRange("defaultBoxColor", 0x64000000, Integer.MIN_VALUE, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> BOX_COLORS = BUILDER
            .comment("Per-section tinted-box colours, keyed by tab ID. Same format as sectionColors; alpha",
                    "controls opacity.")
            .defineListAllowEmpty("boxColors", List.of(), () -> "somemod:main = #64000000", Config::isValidSectionColor);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> BOX_TEXTURES = BUILDER
            .comment("Per-section contrast-box IMAGES, keyed by tab ID (3-sliced horizontally so any width",
                    "fits). Format: \"<tabId> = <ref>\" (\"res:ns:path\" or \"file:name.png\"). Fixed 14px height.",
                    "Managed by the in-game box editor.")
            .defineListAllowEmpty("boxTextures", List.of(),
                    () -> "somemod:main = res:createaddonorganizer:textures/box/example.png", Config::isValidBanner);

    public static final ModConfigSpec.DoubleValue DEFAULT_BOX_DARKEN = BUILDER
            .comment("How much to darken a per-section contrast-box IMAGE when rendered (0 = no darkening,",
                    "1 = fully black). Only applies while a box texture is set.")
            .defineInRange("defaultBoxDarken", 0.0, 0.0, 1.0);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> BOX_DARKENS = BUILDER
            .comment("Per-section contrast-box image darken overrides, keyed by tab ID.")
            .defineListAllowEmpty("boxDarkens", List.of(), () -> "somemod:main = 0.0", Config::isValidSectionFraction);

    public static final ModConfigSpec.DoubleValue DEFAULT_BOX_OPACITY = BUILDER
            .comment("Opacity of a per-section contrast-box IMAGE when rendered (0 = fully transparent,",
                    "1 = fully opaque). Only applies while a box texture is set.")
            .defineInRange("defaultBoxOpacity", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> BOX_OPACITIES = BUILDER
            .comment("Per-section contrast-box image opacity overrides, keyed by tab ID.")
            .defineListAllowEmpty("boxOpacities", List.of(), () -> "somemod:main = 1.0", Config::isValidSectionFraction);

    public static final ModConfigSpec.IntValue DEFAULT_TEXT_COLOR = BUILDER
            .comment("Default section title text colour (ARGB int).")
            .defineInRange("defaultTextColor", 0xFFFFFFFF, Integer.MIN_VALUE, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> DEFAULT_TEXT_GRADIENT = BUILDER
            .comment("Optional gradient for the default text colour, as \"<secondHex>|<DIRECTION>\".",
                    "Text gradients always render smooth.")
            .define("defaultTextGradient", "");

    public static final ModConfigSpec.ConfigValue<List<? extends String>> TEXT_COLORS = BUILDER
            .comment("Per-section title text colours, keyed by tab ID. Same format as sectionColors, minus",
                    "the STYLE token.")
            .defineListAllowEmpty("textColors", List.of(), () -> "somemod:main = #FFFFFFFF", Config::isValidTextColorSpecEntry);

    public static final ModConfigSpec.BooleanValue TWO_TONE_TEXT = BUILDER
            .comment("Shade title text two-tone: primary colour on top, secondary on bottom of each glyph.")
            .define("twoToneText", true);

    public static final ModConfigSpec.IntValue DEFAULT_TEXT_SECONDARY_COLOR = BUILDER
            .comment("Default secondary text colour (ARGB int).")
            .defineInRange("defaultTextSecondaryColor", 0xFFCCCCCC, Integer.MIN_VALUE, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> DEFAULT_TEXT_SECONDARY_GRADIENT = BUILDER
            .comment("Optional gradient for the default secondary text colour, as \"<secondHex>|<DIRECTION>\".")
            .define("defaultTextSecondaryGradient", "");

    public static final ModConfigSpec.ConfigValue<List<? extends String>> TEXT_SECONDARY_COLORS = BUILDER
            .comment("Per-section secondary text colour overrides, keyed by tab ID. Only used while",
                    "twoToneText is on.")
            .defineListAllowEmpty("textSecondaryColors", List.of(), () -> "somemod:main = #FFCEA05A", Config::isValidTextColorSpecEntry);

    public static final ModConfigSpec.DoubleValue DEFAULT_TWO_TONE_SPLIT = BUILDER
            .comment("Default vertical split of two-tone text, as a fraction of glyph height from the top",
                    "(0 = secondary, 1 = primary).")
            .defineInRange("defaultTwoToneSplit", 0.55, 0.0, 1.0);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> TEXT_SPLITS = BUILDER
            .comment("Per-section two-tone split overrides, keyed by tab ID. Format: \"<tabId> = <fraction>\".",
                    "Only used while twoToneText is on.")
            .defineListAllowEmpty("textSplits", List.of(), () -> "somemod:main = 0.56", Config::isValidSectionFraction);

    public static final ModConfigSpec.DoubleValue DEFAULT_SCROLL_CUTOFF = BUILDER
            .comment("Fraction of the banner's title width text must exceed before scrolling. 1.0 (default)",
                    "scrolls only on true overflow; lower values make shorter titles scroll too.")
            .defineInRange("defaultScrollCutoff", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> SCROLL_CUTOFFS = BUILDER
            .comment("Per-section scroll cutoff overrides, keyed by tab ID. Managed from the banner editor's",
                    "Primary text panel.")
            .defineListAllowEmpty("scrollCutoffs", List.of(), () -> "somemod:main = 1.0", Config::isValidSectionFraction);

    public static final ModConfigSpec.BooleanValue TITLE_TEXT_SHADOW = BUILDER
            .comment("Draw title text with the vanilla drop shadow by default. Per-section overrides take",
                    "precedence.")
            .define("titleTextShadow", true);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> TITLE_TEXT_SHADOW_SECTIONS = BUILDER
            .comment("Per-section drop-shadow on/off overrides, keyed by tab ID. Format: \"<tabId> = true\"",
                    "or \"false\". Managed from the banner editor's Shadow panel.")
            .defineListAllowEmpty("titleTextShadowSections", List.of(), () -> "somemod:main = true", Config::isValidSectionBoolean);

    public static final ModConfigSpec.IntValue DEFAULT_TEXT_OUTLINE_COLOR = BUILDER
            .comment("Default title text outline colour (ARGB int). Starting colour in the banner editor's",
                    "Outline panel.")
            .defineInRange("defaultTextOutlineColor", 0xFF000000, Integer.MIN_VALUE, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> DEFAULT_TEXT_OUTLINE_GRADIENT = BUILDER
            .comment("Optional gradient for the default outline colour, as \"<secondHex>|<DIRECTION>\".")
            .define("defaultTextOutlineGradient", "");

    public static final ModConfigSpec.ConfigValue<List<? extends String>> TEXT_OUTLINE_COLORS = BUILDER
            .comment("Per-section text outline colours, keyed by tab ID. An entry's presence enables the",
                    "outline for that section. Managed from the banner editor's Outline panel.")
            .defineListAllowEmpty("textOutlineColors", List.of(), () -> "somemod:main = #FF000000", Config::isValidTextColorSpecEntry);

    public static final ModConfigSpec.IntValue DEFAULT_TEXT_SHADOW_COLOR = BUILDER
            .comment("Default custom drop-shadow colour (ARGB int), used once a section's shadow is",
                    "unlinked from its text colour.")
            .defineInRange("defaultTextShadowColor", 0xFF000000, Integer.MIN_VALUE, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> TEXT_SHADOW_COLORS = BUILDER
            .comment("Per-section custom drop-shadow colours, keyed by tab ID. An entry unlinks that shadow",
                    "from the primary text colour. Only drawn while titleTextShadow is on.")
            .defineListAllowEmpty("textShadowColors", List.of(), () -> "somemod:main = #FF000000", Config::isValidSectionColor);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> HIGHLIGHT_COLORS = BUILDER
            .comment("Accent colour for MAIN tabs only, keyed by tab ID. Config-screen only -- tints that",
                    "tab's row in the section list, no effect in-game.")
            .defineListAllowEmpty("highlightColors", List.of(), () -> "create:base = #4A90D9", Config::isValidSectionColor);

    public static final ModConfigSpec.BooleanValue RAINBOW_MODE = BUILDER
            .comment("Compute banner/text colours live as a red-to-violet gradient by tab position, instead",
                    "of using the manual colour lists. Enabled by the Rainbow preset.")
            .define("rainbowMode", false);

    static {
        BUILDER.pop();
        BUILDER.comment("Custom section order and names.")
                .push("organization");
    }

    public static final ModConfigSpec.ConfigValue<List<? extends String>> SECTION_ORDER = BUILDER
            .comment("Manual drag order of sections within each parent tab. Unlisted sections are appended",
                    "alphabetically.")
            .defineListAllowEmpty("sectionOrder", List.of(), () -> "somemod:main", Config::isValidTabId);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> SECTION_NAMES = BUILDER
            .comment("Custom display names, keyed by tab ID. Format: \"<tabId> = <name>\". Managed by",
                    "ctrl+click-to-rename in the section list.")
            .defineListAllowEmpty("sectionNames", List.of(), () -> "somemod:main = My Custom Name", Config::isValidSectionName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> COLLAPSED_SECTIONS = BUILDER
            .comment("Sections currently collapsed via the collapse toggle. Only relevant while",
                    "showCollapseToggle is on.")
            .defineListAllowEmpty("collapsedSections", List.of(), () -> "somemod:main", Config::isValidTabId);

    static {
        BUILDER.pop();
        BUILDER.comment("The section-index jump list shown on any tab with organized sections",
                        "(both Fancy Tab Sections tabs and Simulated-family addon tabs).")
                .push("interface");
    }

    public enum IndexPanelStyle { VANILLA, DARK, REFURBISHED, BACKPORT }

    public static final ModConfigSpec.EnumValue<IndexPanelStyle> INDEX_PANEL_STYLE = BUILDER
            .comment("Visual style of the section-index panel: VANILLA (light",
                    "raised panel, default), DARK (flat dark panel), REFURBISHED (beveled side tabs),",
                    "BACKPORT (compact textured panel).")
            .defineEnum("indexPanelStyle", IndexPanelStyle.VANILLA);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec.BooleanValue EDITOR_HINT_SEEN = BUILDER
            .comment("Set once the banner editor's preview hint is dismissed. Turn off to show it again.")
            .define("editorHintSeen", false);

    public static final ModConfigSpec.BooleanValue BANNER_EDITOR_PREVIEW_TOP = BUILDER
            .comment("Where the banner preview sits in the editor: true = under the title, false = above",
                    "the OK/Cancel buttons.")
            .define("bannerEditorPreviewTop", false);

    public static final ModConfigSpec.IntValue GRADIENT_CELL_SIZE = BUILDER
            .comment("Pixel chunkiness of the hue/saturation/value gradients in the banner editor. 1 =",
                    "smooth, higher = blockier.")
            .defineInRange("gradientCellSize", 5, 1, 20);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static void resetAllToDefault() {
        applyAppearance(DEFAULT_BANNER_COLOR.getDefault(), DEFAULT_BANNER_GRADIENT.getDefault(), SECTION_COLORS.getDefault(),
                BANNERS.getDefault(), ANIMATED_BANNERS.getDefault(), TINTED_TEXT_BOX.getDefault(), DEFAULT_BOX_COLOR.getDefault(),
                BOX_COLORS.getDefault(), BOX_TEXTURES.getDefault(), DEFAULT_TEXT_COLOR.getDefault(), DEFAULT_TEXT_GRADIENT.getDefault(),
                TEXT_COLORS.getDefault(), TWO_TONE_TEXT.getDefault(), DEFAULT_TEXT_SECONDARY_COLOR.getDefault(),
                DEFAULT_TEXT_SECONDARY_GRADIENT.getDefault(), TEXT_SECONDARY_COLORS.getDefault(),
                HIGHLIGHT_COLORS.getDefault(), SHOW_ALL_BANNERS.getDefault(), EXTRA_BANNER_POOL.getDefault());
        applyOrganization(SECTION_ORDER.getDefault(), SECTION_NAMES.getDefault());
        applyAbsorption(FORCE_INCLUDE.getDefault(), FORCE_EXCLUDE.getDefault(), ROUTES.getDefault(),
                EXTRA_MAIN_SECTIONS.getDefault());
        setRainbowMode(RAINBOW_MODE.getDefault());
        COLLAPSED_SECTIONS.set(COLLAPSED_SECTIONS.getDefault());
        DEFAULT_TEXT_OUTLINE_COLOR.set(DEFAULT_TEXT_OUTLINE_COLOR.getDefault());
        DEFAULT_TEXT_OUTLINE_GRADIENT.set(DEFAULT_TEXT_OUTLINE_GRADIENT.getDefault());
        TEXT_OUTLINE_COLORS.set(TEXT_OUTLINE_COLORS.getDefault());
        applyAppearanceExtras(TITLE_TEXT_SHADOW.getDefault(), TITLE_TEXT_SHADOW_SECTIONS.getDefault(),
                DEFAULT_TEXT_SHADOW_COLOR.getDefault(), TEXT_SHADOW_COLORS.getDefault(),
                DEFAULT_SCROLL_CUTOFF.getDefault(), SCROLL_CUTOFFS.getDefault(),
                DEFAULT_TWO_TONE_SPLIT.getDefault(), TEXT_SPLITS.getDefault());
        DEFAULT_BOX_DARKEN.set(DEFAULT_BOX_DARKEN.getDefault());
        BOX_DARKENS.set(BOX_DARKENS.getDefault());
        DEFAULT_BOX_OPACITY.set(DEFAULT_BOX_OPACITY.getDefault());
        BOX_OPACITIES.set(BOX_OPACITIES.getDefault());
        SPEC.save();
    }

    public static void applyOrganization(List<? extends String> sectionOrder, List<? extends String> sectionNames) {
        SECTION_ORDER.set(sectionOrder);
        SECTION_NAMES.set(sectionNames);
        SPEC.save();
    }

    public static void applyAbsorption(List<? extends String> forceInclude, List<? extends String> forceExclude,
            List<? extends String> routes, List<? extends String> extraMainSections) {
        FORCE_INCLUDE.set(forceInclude);
        FORCE_EXCLUDE.set(forceExclude);
        ROUTES.set(routes);
        EXTRA_MAIN_SECTIONS.set(extraMainSections);
        SPEC.save();
    }

    public static void applyAppearance(int bannerColor, String bannerGradient, List<? extends String> sectionColors,
            List<? extends String> banners, List<? extends String> animatedBanners, boolean tintedBox, int boxColor,
            List<? extends String> boxColors, List<? extends String> boxTextures, int textColor, String textGradient,
            List<? extends String> textColors, boolean twoTone, int textSecondaryColor, String textSecondaryGradient,
            List<? extends String> textSecondaryColors, List<? extends String> highlightColors,
            boolean showAllBanners, List<? extends String> extraBannerPool) {
        DEFAULT_BANNER_COLOR.set(bannerColor);
        DEFAULT_BANNER_GRADIENT.set(bannerGradient);
        SECTION_COLORS.set(sectionColors);
        BANNERS.set(banners);
        ANIMATED_BANNERS.set(animatedBanners);
        TINTED_TEXT_BOX.set(tintedBox);
        DEFAULT_BOX_COLOR.set(boxColor);
        BOX_COLORS.set(boxColors);
        BOX_TEXTURES.set(boxTextures);
        DEFAULT_TEXT_COLOR.set(textColor);
        DEFAULT_TEXT_GRADIENT.set(textGradient);
        TEXT_COLORS.set(textColors);
        TWO_TONE_TEXT.set(twoTone);
        DEFAULT_TEXT_SECONDARY_COLOR.set(textSecondaryColor);
        DEFAULT_TEXT_SECONDARY_GRADIENT.set(textSecondaryGradient);
        TEXT_SECONDARY_COLORS.set(textSecondaryColors);
        HIGHLIGHT_COLORS.set(highlightColors);
        SHOW_ALL_BANNERS.set(showAllBanners);
        EXTRA_BANNER_POOL.set(extraBannerPool);
        SPEC.save();
    }

    public static void applyTextOutlineDefaults(int textOutlineColor, String textOutlineGradient,
            List<? extends String> textOutlineColors) {
        DEFAULT_TEXT_OUTLINE_COLOR.set(textOutlineColor);
        DEFAULT_TEXT_OUTLINE_GRADIENT.set(textOutlineGradient);
        TEXT_OUTLINE_COLORS.set(textOutlineColors);
        SPEC.save();
    }

    public static void applyAppearanceExtras(boolean titleTextShadow, List<? extends String> titleTextShadowSections,
            int textShadowColor, List<? extends String> textShadowColors,
            double scrollCutoff, List<? extends String> scrollCutoffs,
            double twoToneSplit, List<? extends String> textSplits) {
        TITLE_TEXT_SHADOW.set(titleTextShadow);
        TITLE_TEXT_SHADOW_SECTIONS.set(titleTextShadowSections);
        DEFAULT_TEXT_SHADOW_COLOR.set(textShadowColor);
        TEXT_SHADOW_COLORS.set(textShadowColors);
        DEFAULT_SCROLL_CUTOFF.set(scrollCutoff);
        SCROLL_CUTOFFS.set(scrollCutoffs);
        DEFAULT_TWO_TONE_SPLIT.set(twoToneSplit);
        TEXT_SPLITS.set(textSplits);
        SPEC.save();
    }

    public static String sectionNameOverride(ResourceLocation id) {
        String key = id.toString();
        for (String entry : SECTION_NAMES.get()) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2 && key.equals(parts[0].trim())) {
                return parts[1].trim();
            }
        }
        return null;
    }

    public static void setSectionName(ResourceLocation id, String name) {
        List<String> updated = withoutEntry(SECTION_NAMES.get(), id);
        updated.add(id + " = " + name);
        SECTION_NAMES.set(updated);
        SPEC.save();
    }

    public static void clearSectionName(ResourceLocation id) {
        if (sectionNameOverride(id) == null) {
            return;
        }
        SECTION_NAMES.set(withoutEntry(SECTION_NAMES.get(), id));
        SPEC.save();
    }

    private static boolean isValidSectionName(final Object obj) {
        if (!(obj instanceof String s)) {
            return false;
        }
        String[] parts = s.split("=", 2);
        return parts.length == 2 && ResourceLocation.tryParse(parts[0].trim()) != null && !parts[1].trim().isEmpty();
    }

    private static boolean isValidTabId(final Object obj) {
        return obj instanceof String s && ResourceLocation.tryParse(s) != null;
    }

    private static boolean isValidRoute(final Object obj) {
        if (!(obj instanceof String s)) {
            return false;
        }
        String[] parts = s.split(">", 2);
        return parts.length == 2
                && ResourceLocation.tryParse(parts[0].trim()) != null
                && ResourceLocation.tryParse(parts[1].trim()) != null;
    }

    public static boolean isForceIncluded(ResourceLocation id) {
        return BUILTIN_INCLUDE.contains(id.toString()) || contains(FORCE_INCLUDE.get(), id);
    }

    public static boolean isForceExcluded(ResourceLocation id) {
        return isBuiltinExcluded(id) || contains(FORCE_EXCLUDE.get(), id);
    }

    public static boolean isBuiltinExcluded(ResourceLocation id) {
        return BUILTIN_EXCLUDE.contains(id.toString());
    }

    public static boolean isBuiltinHub(ResourceLocation id) {
        return createaddonorganizer.CREATE_BASE.equals(id) || BUILTIN_ROUTES.containsValue(id.toString())
                || SimulatedSupport.isMainTab(id);
    }

    public static ResourceLocation parentFor(ResourceLocation id) {
        ResourceLocation userRoute = lookupRoute(ROUTES.get(), id);
        if (userRoute != null && !isForceExcluded(userRoute)) {
            return userRoute;
        }
        ResourceLocation groupHub = AddonGroups.hubFor(id);
        if (groupHub != null && !isForceExcluded(groupHub)) {
            return groupHub;
        }
        String builtin = BUILTIN_ROUTES.get(id.toString());
        if (builtin != null) {
            ResourceLocation builtinParent = ResourceLocation.parse(builtin);
            if (!isForceExcluded(builtinParent)) {
                return builtinParent;
            }
        }
        if (SimulatedSupport.isLoaded() && !isForceExcluded(SimulatedSupport.MAIN_TAB)
                && AddonDetection.dependsOn(id, SimulatedSupport.MOD_ID)
                && !AddonDetection.dependsOn(id, AddonDetection.CREATE)) {
            return SimulatedSupport.MAIN_TAB;
        }
        return isForceExcluded(createaddonorganizer.CREATE_BASE) ? null : createaddonorganizer.CREATE_BASE;
    }

    public static Set<ResourceLocation> allRouteTargets() {
        Set<ResourceLocation> targets = new HashSet<>();
        for (String entry : ROUTES.get()) {
            String[] parts = entry.split(">", 2);
            if (parts.length == 2) {
                ResourceLocation parent = ResourceLocation.tryParse(parts[1].trim());
                if (parent != null && !isForceExcluded(parent)) {
                    targets.add(parent);
                }
            }
        }
        for (String parent : BUILTIN_ROUTES.values()) {
            ResourceLocation p = ResourceLocation.parse(parent);
            if (!isForceExcluded(p)) {
                targets.add(p);
            }
        }
        return targets;
    }

    public static void addForceInclude(ResourceLocation id) {
        if (contains(FORCE_INCLUDE.get(), id)) {
            return;
        }
        List<String> updated = new ArrayList<>(FORCE_INCLUDE.get());
        updated.add(id.toString());
        FORCE_INCLUDE.set(updated);
        SPEC.save();
    }

    public static void removeForceInclude(ResourceLocation id) {
        if (!contains(FORCE_INCLUDE.get(), id)) {
            return;
        }
        FORCE_INCLUDE.set(withoutValue(FORCE_INCLUDE.get(), id));
        SPEC.save();
    }

    public static void addForceExclude(ResourceLocation id) {
        if (contains(FORCE_EXCLUDE.get(), id)) {
            return;
        }
        List<String> updated = new ArrayList<>(FORCE_EXCLUDE.get());
        updated.add(id.toString());
        FORCE_EXCLUDE.set(updated);
        SPEC.save();
    }

    public static void removeForceExclude(ResourceLocation id) {
        if (!contains(FORCE_EXCLUDE.get(), id)) {
            return;
        }
        FORCE_EXCLUDE.set(withoutValue(FORCE_EXCLUDE.get(), id));
        SPEC.save();
    }

    public static void setRoute(ResourceLocation id, ResourceLocation newParent) {
        List<String> updated = withoutRoute(ROUTES.get(), id);
        updated.add(id + " > " + newParent);
        ROUTES.set(updated);
        SPEC.save();
    }

    public static void clearRoute(ResourceLocation id) {
        if (lookupRoute(ROUTES.get(), id) == null) {
            return;
        }
        ROUTES.set(withoutRoute(ROUTES.get(), id));
        SPEC.save();
    }

    public static List<ResourceLocation> subSectionsRoutedTo(ResourceLocation parent) {
        String target = parent.toString();
        List<ResourceLocation> out = new ArrayList<>();
        for (String entry : ROUTES.get()) {
            String[] parts = entry.split(">", 2);
            if (parts.length == 2 && target.equals(parts[1].trim())) {
                ResourceLocation id = ResourceLocation.tryParse(parts[0].trim());
                if (id != null) {
                    out.add(id);
                }
            }
        }
        return out;
    }

    public static void clearRoutesTo(ResourceLocation parent) {
        String target = parent.toString();
        List<String> updated = new ArrayList<>();
        boolean changed = false;
        for (String entry : ROUTES.get()) {
            String[] parts = entry.split(">", 2);
            if (parts.length == 2 && target.equals(parts[1].trim())) {
                changed = true;
                continue;
            }
            updated.add(entry);
        }
        if (changed) {
            ROUTES.set(updated);
            SPEC.save();
        }
    }

    public static void addExtraMainSection(ResourceLocation id) {
        if (contains(EXTRA_MAIN_SECTIONS.get(), id)) {
            return;
        }
        List<String> updated = new ArrayList<>(EXTRA_MAIN_SECTIONS.get());
        updated.add(id.toString());
        EXTRA_MAIN_SECTIONS.set(updated);
        SPEC.save();
    }

    public static void removeExtraMainSection(ResourceLocation id) {
        if (!contains(EXTRA_MAIN_SECTIONS.get(), id)) {
            return;
        }
        EXTRA_MAIN_SECTIONS.set(withoutValue(EXTRA_MAIN_SECTIONS.get(), id));
        SPEC.save();
    }

    public static Set<ResourceLocation> extraMainSections() {
        Set<ResourceLocation> out = new HashSet<>();
        for (String entry : EXTRA_MAIN_SECTIONS.get()) {
            ResourceLocation id = ResourceLocation.tryParse(entry);
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }

    public static void setSectionOrder(List<ResourceLocation> ids) {
        List<String> updated = new ArrayList<>();
        for (ResourceLocation id : ids) {
            updated.add(id.toString());
        }
        SECTION_ORDER.set(updated);
        SPEC.save();
    }

    public static List<ResourceLocation> applyOrder(List<ResourceLocation> ids, Function<ResourceLocation, String> nameOf) {
        List<? extends String> order = SECTION_ORDER.get();
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            index.put(order.get(i), i);
        }
        List<ResourceLocation> out = new ArrayList<>(ids);
        out.sort(Comparator.<ResourceLocation>comparingInt(id -> index.getOrDefault(id.toString(), Integer.MAX_VALUE))
                .thenComparing(nameOf, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public static boolean rainbowMode() {
        return RAINBOW_MODE.get();
    }

    public static void setRainbowMode(boolean value) {
        RAINBOW_MODE.set(value);
        SPEC.save();
    }

    public static ColorSpec bannerColorFor(ResourceLocation id) {
        if (rainbowMode()) {
            List<ResourceLocation> ordered = rainbowOrder();
            return ColorSpec.solid(rainbowBannerColor(ordered.indexOf(id), ordered.size()));
        }
        ColorSpec override = lookupColorSpec(SECTION_COLORS.get(), id, true);
        return override != null ? override : defaultBannerSpec();
    }

    public static ColorSpec defaultBannerSpec() {
        return composeDefaultSpec(DEFAULT_BANNER_COLOR.get(), DEFAULT_BANNER_GRADIENT.get(), true);
    }

    public static boolean hasColorOverride(ResourceLocation id) {
        return lookupColorSpec(SECTION_COLORS.get(), id, true) != null;
    }

    public static void setSectionColor(ResourceLocation id, ColorSpec spec) {
        List<String> updated = withoutEntry(SECTION_COLORS.get(), id);
        updated.add(id + " = " + formatColorSpec(spec, true));
        SECTION_COLORS.set(updated);
        SPEC.save();
    }

    public static String formatHex(int argb) {
        return String.format(Locale.ROOT, "#%08X", argb);
    }

    public static String bannerRefFor(ResourceLocation id) {
        String key = id.toString();
        for (String entry : BANNERS.get()) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2 && key.equals(parts[0].trim())) {
                return parts[1].trim();
            }
        }
        return null;
    }

    public static boolean hasBanner(ResourceLocation id) {
        return bannerRefFor(id) != null;
    }

    public static boolean tintedTextBox() {
        return TINTED_TEXT_BOX.get();
    }

    public static boolean showCollapseToggle() {
        return SHOW_COLLAPSE_TOGGLE.get();
    }

    public static boolean stickySectionBanners() {
        return STICKY_SECTION_BANNERS.get();
    }

    public static boolean isSectionCollapsed(ResourceLocation id) {
        return contains(COLLAPSED_SECTIONS.get(), id);
    }

    public static void setSectionCollapsed(ResourceLocation id, boolean collapsed) {
        if (collapsed == isSectionCollapsed(id)) {
            return;
        }
        List<String> updated;
        if (collapsed) {
            updated = new ArrayList<>(COLLAPSED_SECTIONS.get());
            updated.add(id.toString());
        } else {
            updated = withoutValue(COLLAPSED_SECTIONS.get(), id);
        }
        COLLAPSED_SECTIONS.set(updated);
        SPEC.save();
    }

    public static boolean classicOrganizerLayout() {
        return CLASSIC_ORGANIZER_LAYOUT.get();
    }

    public static void setTintedTextBox(boolean value) {
        TINTED_TEXT_BOX.set(value);
        SPEC.save();
    }

    public static int boxColorFor(ResourceLocation id) {
        Integer override = lookupColor(BOX_COLORS.get(), id);
        return override != null ? override : DEFAULT_BOX_COLOR.get();
    }

    public static void setBoxColor(ResourceLocation id, int argb) {
        List<String> updated = withoutEntry(BOX_COLORS.get(), id);
        updated.add(id + " = " + formatHex(argb));
        BOX_COLORS.set(updated);
        SPEC.save();
    }

    public static String boxTextureRefFor(ResourceLocation id) {
        String key = id.toString();
        for (String entry : BOX_TEXTURES.get()) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2 && key.equals(parts[0].trim())) {
                return parts[1].trim();
            }
        }
        return null;
    }

    public static boolean hasBoxTexture(ResourceLocation id) {
        return boxTextureRefFor(id) != null;
    }

    public static void setSectionBoxTexture(ResourceLocation id, String ref) {
        List<String> updated = withoutEntry(BOX_TEXTURES.get(), id);
        updated.add(id + " = " + ref);
        BOX_TEXTURES.set(updated);
        SPEC.save();
    }

    public static void clearSectionBoxTexture(ResourceLocation id) {
        if (boxTextureRefFor(id) == null) {
            return;
        }
        BOX_TEXTURES.set(withoutEntry(BOX_TEXTURES.get(), id));
        SPEC.save();
    }

    public static ColorSpec textColorFor(ResourceLocation id) {
        if (rainbowMode()) {
            List<ResourceLocation> ordered = rainbowOrder();
            return ColorSpec.solid(rainbowTextColor(ordered.indexOf(id), ordered.size()));
        }
        ColorSpec override = lookupColorSpec(TEXT_COLORS.get(), id, false);
        return override != null ? override : defaultTextSpec();
    }

    public static ColorSpec defaultTextSpec() {
        return composeDefaultSpec(DEFAULT_TEXT_COLOR.get(), DEFAULT_TEXT_GRADIENT.get(), false);
    }

    public static void setTextColor(ResourceLocation id, ColorSpec spec) {
        List<String> updated = withoutEntry(TEXT_COLORS.get(), id);
        updated.add(id + " = " + formatColorSpec(spec, false));
        TEXT_COLORS.set(updated);
        SPEC.save();
    }

    public static IndexPanelStyle indexPanelStyle() {
        return INDEX_PANEL_STYLE.get();
    }

    public static void setIndexPanelStyle(IndexPanelStyle style) {
        INDEX_PANEL_STYLE.set(style);
        SPEC.save();
    }

    public static boolean editorHintSeen() {
        return EDITOR_HINT_SEEN.get();
    }

    public static void setEditorHintSeen(boolean value) {
        EDITOR_HINT_SEEN.set(value);
        SPEC.save();
    }

    public static boolean bannerEditorPreviewTop() {
        return BANNER_EDITOR_PREVIEW_TOP.get();
    }

    public static int gradientCellSize() {
        return GRADIENT_CELL_SIZE.get();
    }

    public static ColorSpec textSecondaryColorFor(ResourceLocation id) {
        if (!TWO_TONE_TEXT.get()) {
            return null;
        }
        if (rainbowMode()) {
            List<ResourceLocation> ordered = rainbowOrder();
            return ColorSpec.solid(rainbowTextSecondaryColor(ordered.indexOf(id), ordered.size()));
        }
        ColorSpec override = lookupColorSpec(TEXT_SECONDARY_COLORS.get(), id, false);
        return override != null ? override : defaultTextSecondarySpec();
    }

    public static ColorSpec defaultTextSecondarySpec() {
        return composeDefaultSpec(DEFAULT_TEXT_SECONDARY_COLOR.get(), DEFAULT_TEXT_SECONDARY_GRADIENT.get(), false);
    }

    public static int rainbowBannerColor(int index, int total) {
        return 0xFF000000 | ColorUtil.hsvToRgb(rainbowHue(index, total), 0.65f, 0.55f);
    }

    public static int rainbowTextColor(int index, int total) {
        return 0xFF000000 | ColorUtil.hsvToRgb(rainbowHue(index, total), 0.25f, 1.0f);
    }

    public static int rainbowTextSecondaryColor(int index, int total) {
        return 0xFF000000 | ColorUtil.hsvToRgb(rainbowHue(index, total), 0.75f, 0.75f);
    }

    private static float rainbowHue(int index, int total) {
        if (index < 0 || total <= 1) {
            return 0f;
        }
        return (float) index / total;
    }

    private static final long RAINBOW_ORDER_CACHE_TTL_MS = 250;
    private static List<ResourceLocation> rainbowOrderCache = List.of();
    private static long rainbowOrderCacheAt = -RAINBOW_ORDER_CACHE_TTL_MS;

    private static List<ResourceLocation> rainbowOrder() {
        long now = System.currentTimeMillis();
        if (now - rainbowOrderCacheAt < RAINBOW_ORDER_CACHE_TTL_MS) {
            return rainbowOrderCache;
        }
        List<ResourceLocation> ordered = new ArrayList<>();
        for (SectionCatalog.Entry entry : SectionCatalog.colorables()) {
            if (!entry.readOnly()) {
                ordered.add(entry.id());
            }
        }
        rainbowOrderCache = ordered;
        rainbowOrderCacheAt = now;
        return ordered;
    }

    public static void setTextSecondaryColor(ResourceLocation id, ColorSpec spec) {
        List<String> updated = withoutEntry(TEXT_SECONDARY_COLORS.get(), id);
        updated.add(id + " = " + formatColorSpec(spec, false));
        TEXT_SECONDARY_COLORS.set(updated);
        SPEC.save();
    }

    public static void clearTextSecondaryColor(ResourceLocation id) {
        if (textSecondaryColorFor(id) == null) {
            return;
        }
        TEXT_SECONDARY_COLORS.set(withoutEntry(TEXT_SECONDARY_COLORS.get(), id));
        SPEC.save();
    }

    public static boolean titleTextShadow(ResourceLocation id) {
        Boolean override = lookupBoolean(TITLE_TEXT_SHADOW_SECTIONS.get(), id);
        return override != null ? override : TITLE_TEXT_SHADOW.get();
    }

    public static void setTitleTextShadow(ResourceLocation id, boolean shadow) {
        List<String> updated = withoutEntry(TITLE_TEXT_SHADOW_SECTIONS.get(), id);
        updated.add(id + " = " + shadow);
        TITLE_TEXT_SHADOW_SECTIONS.set(updated);
        SPEC.save();
    }

    public static ColorSpec textOutlineColorFor(ResourceLocation id) {
        return lookupColorSpec(TEXT_OUTLINE_COLORS.get(), id, false);
    }

    public static ColorSpec defaultTextOutlineSpec() {
        return composeDefaultSpec(DEFAULT_TEXT_OUTLINE_COLOR.get(), DEFAULT_TEXT_OUTLINE_GRADIENT.get(), false);
    }

    public static void setTextOutlineColor(ResourceLocation id, ColorSpec spec) {
        List<String> updated = withoutEntry(TEXT_OUTLINE_COLORS.get(), id);
        updated.add(id + " = " + formatColorSpec(spec, false));
        TEXT_OUTLINE_COLORS.set(updated);
        SPEC.save();
    }

    public static void clearTextOutlineColor(ResourceLocation id) {
        if (textOutlineColorFor(id) == null) {
            return;
        }
        TEXT_OUTLINE_COLORS.set(withoutEntry(TEXT_OUTLINE_COLORS.get(), id));
        SPEC.save();
    }

    public static Integer textShadowColorFor(ResourceLocation id) {
        return lookupColor(TEXT_SHADOW_COLORS.get(), id);
    }

    public static void setTextShadowColor(ResourceLocation id, int argb) {
        List<String> updated = withoutEntry(TEXT_SHADOW_COLORS.get(), id);
        updated.add(id + " = " + formatHex(argb));
        TEXT_SHADOW_COLORS.set(updated);
        SPEC.save();
    }

    public static void clearTextShadowColor(ResourceLocation id) {
        if (textShadowColorFor(id) == null) {
            return;
        }
        TEXT_SHADOW_COLORS.set(withoutEntry(TEXT_SHADOW_COLORS.get(), id));
        SPEC.save();
    }

    public static float twoToneSplitFor(ResourceLocation id) {
        Float override = lookupFraction(TEXT_SPLITS.get(), id);
        return override != null ? override : DEFAULT_TWO_TONE_SPLIT.get().floatValue();
    }

    public static void setTwoToneSplit(ResourceLocation id, float fraction) {
        List<String> updated = withoutEntry(TEXT_SPLITS.get(), id);
        updated.add(id + " = " + fraction);
        TEXT_SPLITS.set(updated);
        SPEC.save();
    }

    public static void clearTwoToneSplit(ResourceLocation id) {
        if (lookupFraction(TEXT_SPLITS.get(), id) == null) {
            return;
        }
        TEXT_SPLITS.set(withoutEntry(TEXT_SPLITS.get(), id));
        SPEC.save();
    }

    public static float scrollCutoffFor(ResourceLocation id) {
        Float override = lookupFraction(SCROLL_CUTOFFS.get(), id);
        return override != null ? override : DEFAULT_SCROLL_CUTOFF.get().floatValue();
    }

    public static void setScrollCutoff(ResourceLocation id, float fraction) {
        List<String> updated = withoutEntry(SCROLL_CUTOFFS.get(), id);
        updated.add(id + " = " + fraction);
        SCROLL_CUTOFFS.set(updated);
        SPEC.save();
    }

    public static float boxDarkenFor(ResourceLocation id) {
        Float override = lookupFraction(BOX_DARKENS.get(), id);
        return override != null ? override : DEFAULT_BOX_DARKEN.get().floatValue();
    }

    public static void setBoxDarken(ResourceLocation id, float fraction) {
        List<String> updated = withoutEntry(BOX_DARKENS.get(), id);
        updated.add(id + " = " + fraction);
        BOX_DARKENS.set(updated);
        SPEC.save();
    }

    public static float boxOpacityFor(ResourceLocation id) {
        Float override = lookupFraction(BOX_OPACITIES.get(), id);
        return override != null ? override : DEFAULT_BOX_OPACITY.get().floatValue();
    }

    public static void setBoxOpacity(ResourceLocation id, float fraction) {
        List<String> updated = withoutEntry(BOX_OPACITIES.get(), id);
        updated.add(id + " = " + fraction);
        BOX_OPACITIES.set(updated);
        SPEC.save();
    }

    public static Integer highlightColorFor(ResourceLocation id) {
        return lookupColor(HIGHLIGHT_COLORS.get(), id);
    }

    public static void setHighlightColor(ResourceLocation id, int argb) {
        List<String> updated = withoutEntry(HIGHLIGHT_COLORS.get(), id);
        updated.add(id + " = " + formatHex(argb));
        HIGHLIGHT_COLORS.set(updated);
        SPEC.save();
    }

    public static void clearHighlightColor(ResourceLocation id) {
        if (highlightColorFor(id) == null) {
            return;
        }
        HIGHLIGHT_COLORS.set(withoutEntry(HIGHLIGHT_COLORS.get(), id));
        SPEC.save();
    }

    public static boolean showAllBanners() {
        return SHOW_ALL_BANNERS.get();
    }

    public static void setShowAllBanners(boolean value) {
        SHOW_ALL_BANNERS.set(value);
        SPEC.save();
    }

    public static boolean fetchOnlineBanners() {
        return FETCH_ONLINE_BANNERS.get();
    }

    public static String bannerManifestUrl() {
        return BANNER_MANIFEST_URL.get();
    }

    public static String bannerPoolsManifestUrl() {
        return BANNER_POOLS_MANIFEST_URL.get();
    }

    public static boolean fetchOnlineBoxTextures() {
        return FETCH_ONLINE_BOX_TEXTURES.get();
    }

    public static String boxManifestUrl() {
        return BOX_MANIFEST_URL.get();
    }

    public static List<String> extraPoolFor(ResourceLocation id) {
        String key = id.toString();
        List<String> out = new ArrayList<>();
        for (String entry : EXTRA_BANNER_POOL.get()) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2 && key.equals(parts[0].trim())) {
                out.add(parts[1].trim());
            }
        }
        return out;
    }

    public static void addExtraPoolEntry(ResourceLocation id, String ref) {
        if (extraPoolFor(id).contains(ref)) {
            return;
        }
        List<String> updated = new ArrayList<>(EXTRA_BANNER_POOL.get());
        updated.add(id + " = " + ref);
        EXTRA_BANNER_POOL.set(updated);
        SPEC.save();
    }

    public static void removeExtraPoolEntriesForRef(String ref) {
        List<String> updated = new ArrayList<>();
        boolean changed = false;
        for (String entry : EXTRA_BANNER_POOL.get()) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2 && ref.equals(parts[1].trim())) {
                changed = true;
                continue;
            }
            updated.add(entry);
        }
        if (changed) {
            EXTRA_BANNER_POOL.set(updated);
            SPEC.save();
        }
    }

    public static void setSectionBanner(ResourceLocation id, String ref) {
        List<String> updated = withoutEntry(BANNERS.get(), id);
        updated.add(id + " = " + ref);
        BANNERS.set(updated);
        SPEC.save();
    }

    public static void setSectionBanners(Map<ResourceLocation, String> refs) {
        List<String> updated = new ArrayList<>(BANNERS.get());
        for (Map.Entry<ResourceLocation, String> e : refs.entrySet()) {
            updated = withoutEntry(updated, e.getKey());
            updated.add(e.getKey() + " = " + e.getValue());
        }
        BANNERS.set(updated);
        SPEC.save();
    }

    public static void clearSectionBanner(ResourceLocation id) {
        if (bannerRefFor(id) == null) {
            return;
        }
        BANNERS.set(withoutEntry(BANNERS.get(), id));
        SPEC.save();
    }

    public static Integer animatedFrameTicks(ResourceLocation texture) {
        String key = texture.toString();
        for (String entry : ANIMATED_BANNERS.get()) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2 && key.equals(parts[0].trim())) {
                try {
                    return Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    public static void setAnimatedBanner(ResourceLocation texture, int frameTicks) {
        List<String> updated = withoutEntry(ANIMATED_BANNERS.get(), texture);
        updated.add(texture + " = " + frameTicks);
        ANIMATED_BANNERS.set(updated);
        SPEC.save();
    }

    public static void clearAnimatedBanner(ResourceLocation texture) {
        if (animatedFrameTicks(texture) == null) {
            return;
        }
        ANIMATED_BANNERS.set(withoutEntry(ANIMATED_BANNERS.get(), texture));
        SPEC.save();
    }

    private static boolean isValidBanner(final Object obj) {
        if (!(obj instanceof String s)) {
            return false;
        }
        String[] parts = s.split("=", 2);
        return parts.length == 2
                && ResourceLocation.tryParse(parts[0].trim()) != null
                && !parts[1].trim().isEmpty();
    }

    private static boolean isValidUrl(final Object obj) {
        return obj instanceof String s && (s.startsWith("http://") || s.startsWith("https://"));
    }

    private static boolean isValidAnimatedBanner(final Object obj) {
        if (!(obj instanceof String s)) {
            return false;
        }
        String[] parts = s.split("=", 2);
        if (parts.length != 2 || ResourceLocation.tryParse(parts[0].trim()) == null) {
            return false;
        }
        try {
            return Integer.parseInt(parts[1].trim()) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static List<String> withoutEntry(List<? extends String> list, ResourceLocation id) {
        String key = id.toString();
        List<String> out = new ArrayList<>();
        for (String entry : list) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2 && key.equals(parts[0].trim())) {
                continue;
            }
            out.add(entry);
        }
        return out;
    }

    private static List<String> withoutValue(List<? extends String> list, ResourceLocation id) {
        String target = id.toString();
        List<String> out = new ArrayList<>();
        for (String entry : list) {
            if (!target.equals(entry)) {
                out.add(entry);
            }
        }
        return out;
    }

    private static List<String> withoutRoute(List<? extends String> list, ResourceLocation id) {
        String target = id.toString();
        List<String> out = new ArrayList<>();
        for (String entry : list) {
            String[] parts = entry.split(">", 2);
            if (parts.length == 2 && target.equals(parts[0].trim())) {
                continue;
            }
            out.add(entry);
        }
        return out;
    }

    private static boolean isValidSectionColor(final Object obj) {
        if (!(obj instanceof String s)) {
            return false;
        }
        String[] parts = s.split("=", 2);
        return parts.length == 2
                && ResourceLocation.tryParse(parts[0].trim()) != null
                && parseColor(parts[1]) != null;
    }

    private static boolean isValidBannerColorSpecEntry(final Object obj) {
        return isValidColorSpecEntry(obj, true);
    }

    private static boolean isValidTextColorSpecEntry(final Object obj) {
        return isValidColorSpecEntry(obj, false);
    }

    private static boolean isValidColorSpecEntry(final Object obj, boolean supportsStyle) {
        if (!(obj instanceof String s)) {
            return false;
        }
        String[] parts = s.split("=", 2);
        return parts.length == 2
                && ResourceLocation.tryParse(parts[0].trim()) != null
                && parseColorSpecEntry(parts[1].trim(), supportsStyle) != null;
    }

    private static boolean isValidSectionBoolean(final Object obj) {
        if (!(obj instanceof String s)) {
            return false;
        }
        String[] parts = s.split("=", 2);
        return parts.length == 2
                && ResourceLocation.tryParse(parts[0].trim()) != null
                && ("true".equalsIgnoreCase(parts[1].trim()) || "false".equalsIgnoreCase(parts[1].trim()));
    }

    private static boolean isValidSectionFraction(final Object obj) {
        if (!(obj instanceof String s)) {
            return false;
        }
        String[] parts = s.split("=", 2);
        if (parts.length != 2 || ResourceLocation.tryParse(parts[0].trim()) == null) {
            return false;
        }
        try {
            float f = Float.parseFloat(parts[1].trim());
            return f >= 0f && f <= 1f;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static Integer lookupColor(List<? extends String> list, ResourceLocation id) {
        String target = id.toString();
        for (String entry : list) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2 && target.equals(parts[0].trim())) {
                return parseColor(parts[1]);
            }
        }
        return null;
    }

    private static ColorSpec lookupColorSpec(List<? extends String> list, ResourceLocation id, boolean supportsStyle) {
        String target = id.toString();
        for (String entry : list) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2 && target.equals(parts[0].trim())) {
                return parseColorSpecEntry(parts[1].trim(), supportsStyle);
            }
        }
        return null;
    }

    private static ColorSpec composeDefaultSpec(int color1, String gradientSuffix, boolean supportsStyle) {
        if (gradientSuffix == null || gradientSuffix.isEmpty()) {
            return ColorSpec.solid(color1);
        }
        ColorSpec parsed = parseColorSpecEntry(formatHex(color1) + "|" + gradientSuffix, supportsStyle);
        return parsed != null ? parsed : ColorSpec.solid(color1);
    }

    public static String formatColorSpec(ColorSpec spec, boolean includeStyle) {
        if (!spec.isGradient()) {
            return formatHex(spec.color1());
        }
        String out = formatHex(spec.color1()) + "|" + formatHex(spec.color2()) + "|" + spec.direction().name();
        if (includeStyle) {
            out += "|" + spec.style().name();
        }
        return out;
    }

    public static ColorSpec parseColorSpecEntry(String raw, boolean supportsStyle) {
        String[] parts = raw.split("\\|");
        if (parts.length == 1) {
            Integer color1 = parseColor(parts[0]);
            return color1 != null ? ColorSpec.solid(color1) : null;
        }
        if (parts.length != 3 && parts.length != 4) {
            return null;
        }
        if (parts.length == 4 && !supportsStyle) {
            return null;
        }
        Integer color1 = parseColor(parts[0]);
        Integer color2 = parseColor(parts[1]);
        if (color1 == null || color2 == null) {
            return null;
        }
        ColorSpec.Direction direction;
        try {
            direction = ColorSpec.Direction.valueOf(parts[2].trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        ColorSpec.Style style = ColorSpec.Style.SMOOTH;
        if (parts.length == 4) {
            try {
                style = ColorSpec.Style.valueOf(parts[3].trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return new ColorSpec(color1, color2, direction, style);
    }

    private static Boolean lookupBoolean(List<? extends String> list, ResourceLocation id) {
        String target = id.toString();
        for (String entry : list) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2 && target.equals(parts[0].trim())) {
                return Boolean.parseBoolean(parts[1].trim());
            }
        }
        return null;
    }

    private static Float lookupFraction(List<? extends String> list, ResourceLocation id) {
        String target = id.toString();
        for (String entry : list) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2 && target.equals(parts[0].trim())) {
                try {
                    return Float.parseFloat(parts[1].trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    public static Integer parseColor(String raw) {
        String s = raw.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        } else if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
        }
        if (s.isEmpty() || s.length() > 8) {
            return null;
        }
        try {
            long value = Long.parseLong(s, 16);
            if (s.length() <= 6) {
                value |= 0xFF000000L;
            }
            return (int) value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static ResourceLocation lookupRoute(List<? extends String> routes, ResourceLocation id) {
        String target = id.toString();
        for (String entry : routes) {
            String[] parts = entry.split(">", 2);
            if (parts.length == 2 && target.equals(parts[0].trim())) {
                return ResourceLocation.tryParse(parts[1].trim());
            }
        }
        return null;
    }

    private static boolean contains(List<? extends String> list, ResourceLocation id) {
        String target = id.toString();
        for (String entry : list) {
            if (target.equals(entry)) {
                return true;
            }
        }
        return false;
    }
}
