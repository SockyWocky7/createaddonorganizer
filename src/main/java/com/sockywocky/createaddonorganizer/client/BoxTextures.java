package com.sockywocky.createaddonorganizer.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.sockywocky.createaddonorganizer.createaddonorganizer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.neoforged.fml.loading.FMLPaths;

public final class BoxTextures {
    public static final int HEIGHT = 14;
    public static final int CAP_WIDTH = HEIGHT / 2;

    private static final Path BOX_DIR = FMLPaths.CONFIGDIR.get().resolve("createaddonorganizer/text_banners");

    private static final Map<String, ResourceLocation> FILE_REGISTERED = new HashMap<>();
    private static final Map<String, ResourceLocation> REMOTE_REGISTERED = new HashMap<>();
    private static final Map<ResourceLocation, Integer> WIDTH_CACHE = new HashMap<>();
    private static final Set<ResourceLocation> BUNDLED_REGISTERED = new HashSet<>();

    private BoxTextures() {}

    public static ResourceLocation resolve(String ref) {
        if (ref == null) {
            return null;
        }
        if (ref.startsWith("res:")) {
            ResourceLocation rl = ResourceLocation.tryParse(ref.substring(4));
            if (rl != null) {
                ensureBundledRegistered(rl);
            }
            return rl;
        }
        if (ref.startsWith("file:")) {
            String fileName = ref.substring(5);
            ResourceLocation cached = FILE_REGISTERED.get(fileName);
            return cached != null ? cached : loadFileAndCache(BOX_DIR.resolve(fileName), fileName);
        }
        if (ref.startsWith("remote:")) {
            String fileName = ref.substring(7);
            ResourceLocation cached = REMOTE_REGISTERED.get(fileName);
            if (cached != null) {
                return cached;
            }
            return RemoteBoxTextures.isCachedOnDisk(fileName)
                    ? loadRemoteAndCache(RemoteBoxTextures.fileFor(fileName), fileName)
                    : null;
        }
        return ResourceLocation.tryParse(ref);
    }

    public static List<String> gallery() {
        Map<ResourceLocation, Resource> found = Minecraft.getInstance().getResourceManager()
                .listResources("textures/text_banner", p -> p.getPath().endsWith(".png"));
        List<String> bundled = new ArrayList<>();
        List<String> bundledFileNames = new ArrayList<>();
        for (ResourceLocation tex : found.keySet()) {
            bundled.add(resRef(tex));
            bundledFileNames.add(tex.getPath().substring(tex.getPath().lastIndexOf('/') + 1));
        }
        bundled.sort(String.CASE_INSENSITIVE_ORDER);

        List<String> uploads = new ArrayList<>();
        if (Files.isDirectory(BOX_DIR)) {
            try (var files = Files.list(BOX_DIR)) {
                files.filter(p -> p.getFileName().toString().endsWith(".png"))
                        .forEach(p -> uploads.add("file:" + p.getFileName()));
            } catch (IOException e) {
                createaddonorganizer.LOGGER.warn("[CAO] failed to list uploaded box textures", e);
            }
        }
        uploads.sort(String.CASE_INSENSITIVE_ORDER);

        List<String> remotes = RemoteBoxTextures.availableFilenames().stream()
                .filter(f -> bundledFileNames.stream().noneMatch(b -> b.equalsIgnoreCase(f)))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(f -> "remote:" + f)
                .toList();

        List<String> out = new ArrayList<>(bundled.size() + uploads.size() + remotes.size());
        out.addAll(bundled);
        out.addAll(uploads);
        out.addAll(remotes);
        return out;
    }

    public static String resRef(ResourceLocation texture) {
        return "res:" + texture;
    }

    public static OptionalInt nativeWidth(ResourceLocation texture) {
        if (texture == null) {
            return OptionalInt.empty();
        }
        Integer cached = WIDTH_CACHE.get(texture);
        if (cached != null) {
            return OptionalInt.of(cached);
        }
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);
        if (resource.isEmpty()) {
            return OptionalInt.empty();
        }
        try (NativeImage image = NativeImage.read(resource.get().open())) {
            int width = image.getWidth();
            WIDTH_CACHE.put(texture, width);
            return OptionalInt.of(width);
        } catch (IOException e) {
            createaddonorganizer.LOGGER.warn("[CAO] failed to read box texture dimensions for {}", texture, e);
            return OptionalInt.empty();
        }
    }

    public static Optional<Path> chooseFile() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.png"));
            filters.flip();
            String result = TinyFileDialogs.tinyfd_openFileDialog(
                    "Choose a text banner PNG (any width; 14 tall, or it will be rescaled)", "", filters,
                    "PNG image (*.png)", false);
            if (result == null || result.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(Path.of(result));
        } catch (Throwable t) {
            createaddonorganizer.LOGGER.warn("[CAO] native file dialog unavailable", t);
            return Optional.empty();
        }
    }

    public static void invalidateRemoteCache() {
        for (ResourceLocation rl : REMOTE_REGISTERED.values()) {
            Minecraft.getInstance().getTextureManager().release(rl);
            WIDTH_CACHE.remove(rl);
        }
        REMOTE_REGISTERED.clear();
    }

    public static void deleteFile(String ref) {
        if (ref == null || !ref.startsWith("file:")) {
            return;
        }
        String fileName = ref.substring(5);
        ResourceLocation registered = FILE_REGISTERED.remove(fileName);
        if (registered != null) {
            Minecraft.getInstance().getTextureManager().release(registered);
            WIDTH_CACHE.remove(registered);
        }
        try {
            Files.deleteIfExists(BOX_DIR.resolve(fileName));
        } catch (IOException e) {
            createaddonorganizer.LOGGER.warn("[CAO] failed to delete box texture image {}", fileName, e);
        }
    }

    public static String importFile(Path source) throws IOException {
        Files.createDirectories(BOX_DIR);
        String fileName = dedupedName(sanitizeStem(source.getFileName().toString()));
        Path dst = BOX_DIR.resolve(fileName);
        Files.copy(source, dst, StandardCopyOption.REPLACE_EXISTING);
        loadFileAndCache(dst, fileName);
        return "file:" + fileName;
    }

    private static void ensureBundledRegistered(ResourceLocation rl) {
        if (BUNDLED_REGISTERED.contains(rl)) {
            return;
        }
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(rl);
        if (resource.isEmpty()) {
            return;
        }
        try (InputStream in = resource.get().open()) {
            NativeImage image = NativeImage.read(in);
            Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(image));
            BUNDLED_REGISTERED.add(rl);
        } catch (IOException e) {
            createaddonorganizer.LOGGER.warn("[CAO] failed to register bundled text-banner texture {}", rl, e);
        }
    }

    private static ResourceLocation loadFileAndCache(Path path, String fileName) {
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(createaddonorganizer.MODID,
                "custom_text_banner/" + sanitizeStem(fileName));
        try (InputStream in = Files.newInputStream(path)) {
            NativeImage resized = resizeForImport(NativeImage.read(in));
            WIDTH_CACHE.put(rl, resized.getWidth());
            Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(resized));
            FILE_REGISTERED.put(fileName, rl);
            return rl;
        } catch (IOException e) {
            createaddonorganizer.LOGGER.warn("[CAO] failed to load box texture image {}", path, e);
            return null;
        }
    }

    private static ResourceLocation loadRemoteAndCache(Path path, String fileName) {
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(createaddonorganizer.MODID,
                "custom_text_banner/remote/" + sanitizeStem(fileName));
        try (InputStream in = Files.newInputStream(path)) {
            NativeImage resized = resizeForImport(NativeImage.read(in));
            WIDTH_CACHE.put(rl, resized.getWidth());
            Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(resized));
            REMOTE_REGISTERED.put(fileName, rl);
            return rl;
        } catch (IOException e) {
            createaddonorganizer.LOGGER.warn("[CAO] failed to load remote box texture image {}", path, e);
            return null;
        }
    }

    private static NativeImage resizeForImport(NativeImage src) {
        if (src.getHeight() == HEIGHT) {
            return src;
        }
        NativeImage dst = new NativeImage(src.getWidth(), HEIGHT, false);
        for (int y = 0; y < HEIGHT; y++) {
            int sy = y * src.getHeight() / HEIGHT;
            for (int x = 0; x < src.getWidth(); x++) {
                dst.setPixelRGBA(x, y, src.getPixelRGBA(x, sy));
            }
        }
        src.close();
        return dst;
    }

    private static String sanitizeStem(String rawFileName) {
        String stem = rawFileName.replaceFirst("(?i)\\.png$", "");
        return stem.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    private static String dedupedName(String stem) {
        String name = stem + ".png";
        int suffix = 2;
        while (Files.exists(BOX_DIR.resolve(name))) {
            name = stem + "_" + suffix + ".png";
            suffix++;
        }
        return name;
    }

    public static void blit3Slice(GuiGraphics g, ResourceLocation tex, int x, int y, int destW, int destH, int texW, int texH) {
        int cap = CAP_WIDTH;
        int middleSrcW = texW - cap * 2;
        if (destW <= cap * 2 || middleSrcW <= 0) {
            g.blit(tex, x, y, destW, destH, 0f, 0f, texW, texH, texW, texH);
            return;
        }

        g.blit(tex, x, y, cap, destH, 0f, 0f, cap, texH, texW, texH);
        g.blit(tex, x + destW - cap, y, cap, destH, (float) (texW - cap), 0f, cap, texH, texW, texH);

        int middleDestW = destW - cap * 2;
        int drawn = 0;
        while (drawn < middleDestW) {
            int tileW = Math.min(middleSrcW, middleDestW - drawn);
            int dx = x + cap + drawn;
            if (tileW < middleSrcW) {
                TwoToneText.beginScissor(g, dx, y, dx + tileW, y + destH);
                g.blit(tex, dx, y, middleSrcW, destH, (float) cap, 0f, middleSrcW, texH, texW, texH);
                TwoToneText.endScissor(g);
            } else {
                g.blit(tex, dx, y, tileW, destH, (float) cap, 0f, tileW, texH, texW, texH);
            }
            drawn += tileW;
        }
    }

    public static void draw(GuiGraphics g, ResourceLocation texture, int x1, int y1, int x2, int y2, int fallbackArgb,
            float darken, float opacity) {
        OptionalInt texW = texture != null ? nativeWidth(texture) : OptionalInt.empty();
        if (texture != null && texW.isPresent()) {
            float mult = 1f - Mth.clamp(darken, 0f, 1f);
            float alpha = Mth.clamp(opacity, 0f, 1f);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(mult, mult, mult, alpha);
            blit3Slice(g, texture, x1, y1, x2 - x1, y2 - y1, texW.getAsInt(), HEIGHT);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        } else {
            g.fill(x1, y1, x2, y2, fallbackArgb);
        }
    }
}
