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
            .comment("Use the classic (pre-1.3) organizer menu: centered column, an Edit button on every row,",
                    "no search box or sidebar.")
            .define("classicOrganizerLayout", true);

    public static final ModConfigSpec.BooleanValue SHOW_COLLAPSE_TOGGLE = BUILDER
            .comment("Show Fancy Tab Sections' built-in collapse/expand button on the right side of each",
                    "section banner. Off by default since this mod's own banners aren't designed for it.")
            .define("showCollapseToggle", false);

    static {
        BUILDER.comment("Which addon tabs get absorbed, and which Create tab each one folds into.")
                .push("absorption");
    }

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FORCE_INCLUDE = BUILDER
            .comment("Tab IDs (e.g. \"somemod:main\") to ALWAYS absorb under the Create tab,",
                    "even if the owning mod doesn't declare a dependency on Create.")
            .defineListAllowEmpty("forceInclude", List.of(), () -> "somemod:main", Config::isValidTabId);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FORCE_EXCLUDE = BUILDER
            .comment("Tab IDs (e.g. \"somemod:main\") to NEVER absorb; they keep their own standalone tab.")
            .defineListAllowEmpty("forceExclude", List.of(), () -> "somemod:main", Config::isValidTabId);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> ROUTES = BUILDER
            .comment("Fold specific tabs into a chosen Create PARENT tab instead of the main \"create:base\".",
                    "Format: \"<addonTabId> > <parentTabId>\", e.g. \"somemod:deco > create:palettes\".",
                    "Create's parent tabs are \"create:base\" (Create) and \"create:palettes\" (Create: Palettes).")
            .defineListAllowEmpty("routes", List.of(), () -> "somemod:deco > create:palettes", Config::isValidRoute);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXTRA_MAIN_SECTIONS = BUILDER
            .comment("Tabs explicitly promoted to \"hub\" status (can have other tabs folded into them as",
                    "sub-sections) even though nothing routes into them yet, e.g. \"somemod:main\". A hub keeps",
                    "its own standalone tab button and items, same as create:base/create:palettes today.",
                    "Managed live via shift+\"+\" in the creative menu.")
            .defineListAllowEmpty("extraMainSections", List.of(), () -> "somemod:main", Config::isValidTabId);

    static {
        BUILDER.pop();
        BUILDER.comment("Banner, contrast-box, and title-text styling for the sections.")
                .push("appearance");
    }

    public static final ModConfigSpec.IntValue DEFAULT_BANNER_COLOR = BUILDER
            .comment("Default section banner colour as an ARGB integer (default opaque dark grey, HSV value 15%).")
            .defineInRange("defaultBannerColor", 0xFF262626, Integer.MIN_VALUE, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> SECTION_COLORS = BUILDER
            .comment("Per-section banner colours, keyed by tab ID (a stop-gap until artwork exists).",
                    "Format: \"<tabId> = <hex>\", e.g. \"create:palettes = #6A8F3C\" or \"somemod:main = 0xFF335577\".",
                    "Accepts #RRGGBB, #AARRGGBB, or a 0x-prefixed hex value; 6-digit values are treated as opaque.",
                    "Changes apply the next time you join a world.")
            .defineListAllowEmpty("sectionColors", List.of(), () -> "somemod:main = #4A4A4A", Config::isValidSectionColor);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> BANNERS = BUILDER
            .comment("Per-section banner IMAGES, keyed by tab ID. An image overrides the colour for that section.",
                    "Format: \"<tabId> = <ref>\" where <ref> is \"res:<namespace>:<path>\" for a bundled/gallery",
                    "texture, \"file:<name>.png\" for a PNG in config/createaddonorganizer/banners/, or",
                    "\"remote:<name>.png\" for a banner fetched from the online gallery.",
                    "Managed by the in-game banner editor; banners are 160x17.")
            .defineListAllowEmpty("banners", List.of(),
                    () -> "somemod:main = res:createaddonorganizer:textures/banner/create1.png", Config::isValidBanner);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> ANIMATED_BANNERS = BUILDER
            .comment("Declares a banner texture as animated (a vertical strip of 17px frames), keyed by the",
                    "resolved texture id -- not the section id, so a shared/uploaded texture animates",
                    "everywhere it's used. Format: \"<textureId> = <frametime>\" (frametime in game ticks).",
                    "Bundled (res:) textures don't need this; they're auto-detected from a standard .mcmeta.",
                    "Managed by the in-game banner editor.")
            .defineListAllowEmpty("animatedBanners", List.of(),
                    () -> "createaddonorganizer:custom_banner/example = 2", Config::isValidAnimatedBanner);

    public static final ModConfigSpec.BooleanValue SHOW_ALL_BANNERS = BUILDER
            .comment("Some mods have a curated set of banners assigned to them; by default their picker only",
                    "offers those. Turn this on to ignore that curation and see the full banner gallery",
                    "everywhere, same as an unassigned mod.")
            .define("showAllBanners", false);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXTRA_BANNER_POOL = BUILDER
            .comment("Banners YOU'VE uploaded for a curated (pool-restricted) mod's tab, keyed by tab ID.",
                    "Added to that tab's curated pool automatically so your own upload shows up in its",
                    "gallery even while the curated pool is restricting everything else. Format:",
                    "\"<tabId> = <ref>\"; a tab can have several rows. Managed by the in-game banner editor.")
            .defineListAllowEmpty("extraBannerPool", List.of(),
                    () -> "somemod:main = file:example.png", Config::isValidBanner);

    public static final ModConfigSpec.BooleanValue FETCH_ONLINE_BANNERS = BUILDER
            .comment("If true, checks GitHub once per game launch (on a background thread) for new or",
                    "updated community banners and credits. Never blocks loading, never retried mid-session.",
                    "Disable for fully offline/airgapped use.")
            .define("fetchOnlineBanners", true);

    public static final ModConfigSpec.ConfigValue<String> BANNER_MANIFEST_URL = BUILDER
            .comment("URL of the remote banner manifest (JSON). Only used when fetchOnlineBanners is true.",
                    "Override to test against a local server or a fork's repository.")
            .define("bannerManifestUrl",
                    "https://cdn.jsdelivr.net/gh/SockyWocky7/createaddonorganizer@master/banners/index.json",
                    Config::isValidUrl);

    public static final ModConfigSpec.BooleanValue FETCH_ONLINE_BOX_TEXTURES = BUILDER
            .comment("If true, checks GitHub once per game launch (on a background thread) for new or",
                    "updated community text-banner (contrast box) textures. Never blocks loading, never",
                    "retried mid-session. Disable for fully offline/airgapped use.")
            .define("fetchOnlineBoxTextures", true);

    public static final ModConfigSpec.ConfigValue<String> BOX_MANIFEST_URL = BUILDER
            .comment("URL of the remote text-banner manifest (JSON). Only used when fetchOnlineBoxTextures is",
                    "true. Override to test against a local server or a fork's repository.")
            .define("boxManifestUrl",
                    "https://cdn.jsdelivr.net/gh/SockyWocky7/createaddonorganizer@master/text_banners/index.json",
                    Config::isValidUrl);

    public static final ModConfigSpec.BooleanValue TINTED_TEXT_BOX = BUILDER
            .comment("Draw a semi-transparent box behind section title text for contrast.")
            .define("tintedTextBox", true);

    public static final ModConfigSpec.IntValue DEFAULT_BOX_COLOR = BUILDER
            .comment("Default tinted-box colour as an ARGB integer (default translucent black).")
            .defineInRange("defaultBoxColor", 0x64000000, Integer.MIN_VALUE, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> BOX_COLORS = BUILDER
            .comment("Per-section tinted-box colours, keyed by tab ID. Same format as sectionColors;",
                    "the alpha channel controls opacity, e.g. \"create:palettes = #AA39231C\".")
            .defineListAllowEmpty("boxColors", List.of(), () -> "somemod:main = #64000000", Config::isValidSectionColor);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> BOX_TEXTURES = BUILDER
            .comment("Per-section contrast-box IMAGES, keyed by tab ID (drawn behind the title text,",
                    "3-sliced horizontally -- fixed-width end caps plus a tiled middle -- so any width fits).",
                    "An image overrides the box colour for that section. Format: \"<tabId> = <ref>\" where",
                    "<ref> is \"res:<namespace>:<path>\" for a bundled texture or \"file:<name>.png\" for a PNG",
                    "in config/createaddonorganizer/text_banners/. No remote gallery or animation support",
                    "for box textures (unlike banners). Height is fixed at 14px; any width.",
                    "Managed by the in-game box editor.")
            .defineListAllowEmpty("boxTextures", List.of(),
                    () -> "somemod:main = res:createaddonorganizer:textures/box/example.png", Config::isValidBanner);

    public static final ModConfigSpec.IntValue DEFAULT_TEXT_COLOR = BUILDER
            .comment("Default section title text colour as an ARGB integer (default opaque white).")
            .defineInRange("defaultTextColor", 0xFFFFFFFF, Integer.MIN_VALUE, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> TEXT_COLORS = BUILDER
            .comment("Per-section title text colours, keyed by tab ID. Same format as sectionColors.")
            .defineListAllowEmpty("textColors", List.of(), () -> "somemod:main = #FFFFFFFF", Config::isValidSectionColor);

    public static final ModConfigSpec.BooleanValue TWO_TONE_TEXT = BUILDER
            .comment("Shade section title text two-tone (primary colour on top, secondary on the bottom",
                    "of each glyph) instead of a single flat colour.")
            .define("twoToneText", true);

    public static final ModConfigSpec.IntValue DEFAULT_TEXT_SECONDARY_COLOR = BUILDER
            .comment("Default secondary text colour as an ARGB integer (default opaque light grey,",
                    "HSV 0/0/80%).")
            .defineInRange("defaultTextSecondaryColor", 0xFFCCCCCC, Integer.MIN_VALUE, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> TEXT_SECONDARY_COLORS = BUILDER
            .comment("Per-section SECONDARY title text colour overrides, keyed by tab ID. Only used while",
                    "twoToneText is on; falls back to defaultTextSecondaryColor when unset for a section.")
            .defineListAllowEmpty("textSecondaryColors", List.of(), () -> "somemod:main = #FFCEA05A", Config::isValidSectionColor);

    public static final ModConfigSpec.DoubleValue DEFAULT_TWO_TONE_SPLIT = BUILDER
            .comment("Default vertical split point of two-tone title text, as a fraction of the glyph height",
                    "measured from the top (0.0 = fully secondary colour, 1.0 = fully primary colour).",
                    "Default 0.56 matches the original fixed split.")
            .defineInRange("defaultTwoToneSplit", 5.0 / 9.0, 0.0, 1.0);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> TEXT_SPLITS = BUILDER
            .comment("Per-section two-tone text split overrides, keyed by tab ID. Only used while twoToneText",
                    "is on; falls back to defaultTwoToneSplit when unset for a section. Format:",
                    "\"<tabId> = <fraction>\".")
            .defineListAllowEmpty("textSplits", List.of(), () -> "somemod:main = 0.56", Config::isValidSectionFraction);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> HIGHLIGHT_COLORS = BUILDER
            .comment("Optional accent colour for MAIN tabs only (create:base, create:palettes, and any promoted",
                    "hub), keyed by tab ID. Purely a config-screen convenience: tints that tab's row band and",
                    "its sub-section group line in the section list, so you can tell addon groups apart at a",
                    "glance. Has no effect on the actual in-game banner. Format: \"<tabId> = <hex>\".")
            .defineListAllowEmpty("highlightColors", List.of(), () -> "create:base = #4A90D9", Config::isValidSectionColor);

    public static final ModConfigSpec.BooleanValue RAINBOW_MODE = BUILDER
            .comment("When on, banner/text/secondary-text colours are computed live from each section's",
                    "position in the current tab order (a smooth red-to-violet gradient top to bottom)",
                    "instead of read from sectionColors/textColors/textSecondaryColors. Recomputes on the fly",
                    "as tabs are reordered or added, so it never goes stale. Turned on by the \"Rainbow\" preset.")
            .define("rainbowMode", false);

    static {
        BUILDER.pop();
        BUILDER.comment("Custom section ordering and renames.")
                .push("organization");
    }

    public static final ModConfigSpec.ConfigValue<List<? extends String>> SECTION_ORDER = BUILDER
            .comment("Manual drag order of sections within each parent tab (tab IDs). Unlisted sections",
                    "(e.g. newly installed addons) are appended alphabetically by name.")
            .defineListAllowEmpty("sectionOrder", List.of(), () -> "somemod:main", Config::isValidTabId);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> SECTION_NAMES = BUILDER
            .comment("Custom display names for sections, keyed by tab ID. Overrides the addon's own name.",
                    "Format: \"<tabId> = <custom name>\". Managed by ctrl+click-to-rename in the section list.")
            .defineListAllowEmpty("sectionNames", List.of(), () -> "somemod:main = My Custom Name", Config::isValidSectionName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> COLLAPSED_SECTIONS = BUILDER
            .comment("Section tab IDs currently collapsed via Fancy Tab Sections' collapse toggle (only",
                    "relevant while showCollapseToggle is on). Remembered across tab rebuilds and restarts.")
            .defineListAllowEmpty("collapsedSections", List.of(), () -> "somemod:main", Config::isValidTabId);

    static {
        BUILDER.pop();
        BUILDER.comment("The left-side section-index jump list on the creative screen.")
                .push("interface");
    }

    public enum IndexPanelStyle { VANILLA, DARK, REFURBISHED, BACKPORT }

    public static final ModConfigSpec.EnumValue<IndexPanelStyle> INDEX_PANEL_STYLE = BUILDER
            .comment("Visual style of the section-index panel:",
                    "VANILLA - light-grey raised panel matching the vanilla inventory (default).",
                    "DARK - the original flat dark panel.",
                    "REFURBISHED - beveled side tabs in the style of MrCrayfish's Refurbished Furniture.",
                    "BACKPORT - compact textured side panel in the style of Vanilla Backport.")
            .defineEnum("indexPanelStyle", IndexPanelStyle.VANILLA);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec.BooleanValue EDITOR_HINT_SEEN = BUILDER
            .comment("Set automatically once the banner editor's click-the-preview hint has been acknowledged",
                    "(by clicking any part of the preview). Turn off to show the pulsing hint again.")
            .define("editorHintSeen", false);

    public static final ModConfigSpec.BooleanValue BANNER_EDITOR_PREVIEW_TOP = BUILDER
            .comment("Where the clickable banner preview sits in the banner editor:",
                    "true - directly under the title, with the edit controls below it.",
                    "false - at the bottom of the screen, just above the OK/Cancel buttons.")
            .define("bannerEditorPreviewTop", false);

    public static final ModConfigSpec.IntValue GRADIENT_CELL_SIZE = BUILDER
            .comment("Chunkiness (in GUI pixels) of the pixelated hue/saturation/value picker gradients in the",
                    "banner editor. 1 is a smooth gradient; higher values look blockier/more pixel-art.")
            .defineInRange("gradientCellSize", 5, 1, 20);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static void resetAllToDefault() {
        applyAppearance(DEFAULT_BANNER_COLOR.getDefault(), SECTION_COLORS.getDefault(), BANNERS.getDefault(),
                ANIMATED_BANNERS.getDefault(), TINTED_TEXT_BOX.getDefault(), DEFAULT_BOX_COLOR.getDefault(),
                BOX_COLORS.getDefault(), BOX_TEXTURES.getDefault(), DEFAULT_TEXT_COLOR.getDefault(), TEXT_COLORS.getDefault(),
                TWO_TONE_TEXT.getDefault(), DEFAULT_TEXT_SECONDARY_COLOR.getDefault(), TEXT_SECONDARY_COLORS.getDefault(),
                HIGHLIGHT_COLORS.getDefault(), SHOW_ALL_BANNERS.getDefault(), EXTRA_BANNER_POOL.getDefault());
        applyOrganization(SECTION_ORDER.getDefault(), SECTION_NAMES.getDefault());
        applyAbsorption(FORCE_INCLUDE.getDefault(), FORCE_EXCLUDE.getDefault(), ROUTES.getDefault(),
                EXTRA_MAIN_SECTIONS.getDefault());
        setRainbowMode(RAINBOW_MODE.getDefault());
        COLLAPSED_SECTIONS.set(COLLAPSED_SECTIONS.getDefault());
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

    public static void applyAppearance(int bannerColor, List<? extends String> sectionColors, List<? extends String> banners,
            List<? extends String> animatedBanners, boolean tintedBox, int boxColor, List<? extends String> boxColors,
            List<? extends String> boxTextures, int textColor, List<? extends String> textColors, boolean twoTone,
            int textSecondaryColor, List<? extends String> textSecondaryColors, List<? extends String> highlightColors,
            boolean showAllBanners, List<? extends String> extraBannerPool) {
        DEFAULT_BANNER_COLOR.set(bannerColor);
        SECTION_COLORS.set(sectionColors);
        BANNERS.set(banners);
        ANIMATED_BANNERS.set(animatedBanners);
        TINTED_TEXT_BOX.set(tintedBox);
        DEFAULT_BOX_COLOR.set(boxColor);
        BOX_COLORS.set(boxColors);
        BOX_TEXTURES.set(boxTextures);
        DEFAULT_TEXT_COLOR.set(textColor);
        TEXT_COLORS.set(textColors);
        TWO_TONE_TEXT.set(twoTone);
        DEFAULT_TEXT_SECONDARY_COLOR.set(textSecondaryColor);
        TEXT_SECONDARY_COLORS.set(textSecondaryColors);
        HIGHLIGHT_COLORS.set(highlightColors);
        SHOW_ALL_BANNERS.set(showAllBanners);
        EXTRA_BANNER_POOL.set(extraBannerPool);
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

    public static int bannerColorFor(ResourceLocation id) {
        if (rainbowMode()) {
            List<ResourceLocation> ordered = rainbowOrder();
            return rainbowBannerColor(ordered.indexOf(id), ordered.size());
        }
        Integer override = lookupColor(SECTION_COLORS.get(), id);
        return override != null ? override : DEFAULT_BANNER_COLOR.get();
    }

    public static boolean hasColorOverride(ResourceLocation id) {
        return lookupColor(SECTION_COLORS.get(), id) != null;
    }

    public static void setSectionColor(ResourceLocation id, int argb) {
        List<String> updated = withoutEntry(SECTION_COLORS.get(), id);
        updated.add(id + " = " + formatHex(argb));
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

    public static int textColorFor(ResourceLocation id) {
        if (rainbowMode()) {
            List<ResourceLocation> ordered = rainbowOrder();
            return rainbowTextColor(ordered.indexOf(id), ordered.size());
        }
        Integer override = lookupColor(TEXT_COLORS.get(), id);
        return override != null ? override : DEFAULT_TEXT_COLOR.get();
    }

    public static void setTextColor(ResourceLocation id, int argb) {
        List<String> updated = withoutEntry(TEXT_COLORS.get(), id);
        updated.add(id + " = " + formatHex(argb));
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

    public static Integer textSecondaryColorFor(ResourceLocation id) {
        if (!TWO_TONE_TEXT.get()) {
            return null;
        }
        if (rainbowMode()) {
            List<ResourceLocation> ordered = rainbowOrder();
            return rainbowTextSecondaryColor(ordered.indexOf(id), ordered.size());
        }
        Integer override = lookupColor(TEXT_SECONDARY_COLORS.get(), id);
        return override != null ? override : DEFAULT_TEXT_SECONDARY_COLOR.get();
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

    public static void setTextSecondaryColor(ResourceLocation id, int argb) {
        List<String> updated = withoutEntry(TEXT_SECONDARY_COLORS.get(), id);
        updated.add(id + " = " + formatHex(argb));
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
