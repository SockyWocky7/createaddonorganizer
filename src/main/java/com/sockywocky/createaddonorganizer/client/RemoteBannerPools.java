package com.sockywocky.createaddonorganizer.client;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.sockywocky.createaddonorganizer.Config;
import com.sockywocky.createaddonorganizer.createaddonorganizer;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

public final class RemoteBannerPools {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, List<String>>>() {}.getType();
    private static final Path REMOTE_DIR = FMLPaths.CONFIGDIR.get().resolve("createaddonorganizer/remote_banner_pools");
    private static final Path MANIFEST_CACHE = REMOTE_DIR.resolve("pools.json");
    private static final Path ETAG_FILE = REMOTE_DIR.resolve("pools.etag");
    private static final String RAW_FALLBACK_URL =
            "https://raw.githubusercontent.com/SockyWocky7/createaddonorganizer/master/banners/pools.json";

    private static final AtomicBoolean SYNC_STARTED = new AtomicBoolean(false);

    private static volatile Map<String, List<String>> pools = Map.of();
    private static volatile boolean everCached = false;

    private record FetchResult(boolean notModified, byte[] body, String etag) {}

    private RemoteBannerPools() {}

    public static void loadCacheFromDisk() {
        try {
            if (!Files.exists(MANIFEST_CACHE)) {
                everCached = false;
                return;
            }
            Map<String, List<String>> parsed = parseManifest(Files.readAllBytes(MANIFEST_CACHE));
            if (parsed == null) {
                pools = Map.of();
                everCached = false;
                return;
            }
            pools = parsed;
            everCached = true;
        } catch (IOException | RuntimeException e) {
            createaddonorganizer.LOGGER.warn("[CAO] failed to load cached remote banner-pool manifest", e);
            pools = Map.of();
            everCached = false;
        }
    }

    public static void syncAsync() {
        if (!SYNC_STARTED.compareAndSet(false, true)) {
            return;
        }
        Thread thread = new Thread(RemoteBannerPools::sync, "createaddonorganizer-banner-pools-sync");
        thread.setDaemon(true);
        thread.start();
    }

    public static boolean hasEverCached() {
        return everCached;
    }

    public static List<String> poolFor(ResourceLocation tabId) {
        List<String> refs = pools.get(tabId.toString());
        return refs != null ? refs : List.of();
    }

    public static Map<String, List<String>> poolsSnapshot() {
        return pools;
    }

    public static void refreshLocal() {
        Path dir = localDir();
        if (dir == null) {
            createaddonorganizer.LOGGER.warn("[CAO] local testing: could not resolve project root (not running from source)");
            return;
        }
        Path manifest = dir.resolve("pools.json");
        if (!Files.exists(manifest)) {
            createaddonorganizer.LOGGER.warn("[CAO] local testing: no pools.json found at {}", manifest);
            pools = Map.of();
            everCached = true;
            return;
        }
        try {
            Map<String, List<String>> parsed = parseManifest(Files.readAllBytes(manifest));
            if (parsed == null) {
                return;
            }
            pools = parsed;
            everCached = true;
        } catch (IOException e) {
            createaddonorganizer.LOGGER.warn("[CAO] local testing: failed to read {}", manifest, e);
        }
    }

    private static Path localDir() {
        Path cwd = Path.of("").toAbsolutePath();
        Path projectRoot = cwd.getParent();
        return projectRoot == null ? null : projectRoot.resolve("banners");
    }

    private static void sync() {
        try {
            if (!Config.fetchOnlineBanners()) {
                return;
            }
            if (RemoteBanners.isLocalTesting()) {
                refreshLocal();
                return;
            }
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            String etag = readEtag();

            FetchResult result = fetchManifest(client, Config.bannerPoolsManifestUrl(), etag);
            if (result == null) {
                result = fetchManifest(client, RAW_FALLBACK_URL, etag);
            }
            if (result == null) {
                createaddonorganizer.LOGGER.warn("[CAO] remote banner-pool manifest fetch failed (primary and fallback)");
                return;
            }
            if (result.notModified()) {
                return;
            }

            Map<String, List<String>> parsed = parseManifest(result.body());
            if (parsed == null) {
                return;
            }

            writeAtomic(MANIFEST_CACHE, result.body());
            if (result.etag() != null) {
                writeAtomic(ETAG_FILE, result.etag().getBytes(StandardCharsets.UTF_8));
            }

            pools = parsed;
            everCached = true;
        } catch (Exception e) {
            createaddonorganizer.LOGGER.warn("[CAO] remote banner-pool sync failed", e);
        }
    }

    private static FetchResult fetchManifest(HttpClient client, String url, String etag) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .GET();
            if (etag != null && !etag.isBlank()) {
                builder.header("If-None-Match", etag);
            }
            HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 304) {
                return new FetchResult(true, null, etag);
            }
            if (response.statusCode() != 200) {
                return null;
            }
            String newEtag = response.headers().firstValue("ETag").orElse(null);
            return new FetchResult(false, response.body(), newEtag);
        } catch (IOException | InterruptedException e) {
            createaddonorganizer.LOGGER.warn("[CAO] failed to fetch remote banner-pool manifest from {}", url, e);
            return null;
        }
    }

    private static Map<String, List<String>> parseManifest(byte[] bytes) {
        String json = new String(bytes, StandardCharsets.UTF_8);
        Map<String, List<String>> parsed;
        try {
            parsed = GSON.fromJson(json, MAP_TYPE);
        } catch (JsonSyntaxException e) {
            createaddonorganizer.LOGGER.warn("[CAO] remote banner-pool manifest is not valid JSON; ignoring it", e);
            return null;
        }
        if (parsed == null) {
            return Map.of();
        }
        Map<String, List<String>> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : parsed.entrySet()) {
            if (entry.getKey() == null || ResourceLocation.tryParse(entry.getKey()) == null || entry.getValue() == null) {
                createaddonorganizer.LOGGER.warn("[CAO] skipping malformed entry in banner-pool manifest: {}", entry.getKey());
                continue;
            }
            sanitized.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(sanitized);
    }

    private static String readEtag() {
        try {
            if (Files.exists(ETAG_FILE)) {
                return Files.readString(ETAG_FILE, StandardCharsets.UTF_8).trim();
            }
        } catch (IOException e) {
            createaddonorganizer.LOGGER.warn("[CAO] failed to read cached banner-pool manifest etag", e);
        }
        return null;
    }

    private static void writeAtomic(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, bytes);
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
