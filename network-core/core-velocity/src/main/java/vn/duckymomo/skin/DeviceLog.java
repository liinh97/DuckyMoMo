package vn.duckymomo.skin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.PlayerClientBrandEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Thiết bị của người chơi (27/10, trang quản trị ghi trước tên member): Bedrock hay máy tính (Java), hệ điều hành của máy Bedrock
 * (Floodgate, gọi bằng reflection cho khỏi phụ thuộc lúc biên dịch), phiên bản game, client Java (vanilla / fabric / lunar…).
 * Ghi thietbi.json trong thư mục plugin: {tên thường: {loai: be|pc, os, ban, client, luc}}; panel đọc (data/proxy/plugins/duckymomoskin/).
 */
final class DeviceLog {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger log;
    private final Path file;
    private final Map<String, Map<String, Object>> data;
    private volatile boolean dirty;

    DeviceLog(Object plugin, ProxyServer proxy, Logger log, Path dir) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.log = log;
        this.file = dir.resolve("thietbi.json");
        Map<String, Map<String, Object>> d = null;
        try {
            if (Files.exists(file)) {
                d = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<LinkedHashMap<String, Map<String, Object>>>() { }.getType());
            }
        } catch (Exception e) {
            log.warn("Thiết bị: không đọc được {}: {}", file, e.getMessage());
        }
        this.data = d == null ? new LinkedHashMap<>() : d;
        proxy.getScheduler().buildTask(plugin, this::save).repeat(10, TimeUnit.SECONDS).schedule();
    }

    /** Hệ điều hành Bedrock qua Floodgate (null = không phải Bedrock / không có Floodgate). */
    private static String bedrockOs(Player p) {
        try {
            Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object inst = api.getMethod("getInstance").invoke(null);
            if (!(boolean) api.getMethod("isFloodgatePlayer", java.util.UUID.class).invoke(inst, p.getUniqueId())) {
                return null;
            }
            Object fp = api.getMethod("getPlayer", java.util.UUID.class).invoke(inst, p.getUniqueId());
            Object os = fp == null ? null : fp.getClass().getMethod("getDeviceOs").invoke(fp);
            return os == null ? "" : os.toString();
        } catch (Throwable e) {
            return p.getUniqueId().toString().startsWith("00000000-0000-0000-") ? "" : null;
        }
    }

    private Map<String, Object> entry(Player p) {
        return data.computeIfAbsent(p.getUsername().toLowerCase(Locale.ROOT), k -> new LinkedHashMap<>());
    }

    @Subscribe
    public void onLogin(PostLoginEvent e) {
        Player p = e.getPlayer();
        String os = bedrockOs(p);
        Map<String, Object> m = entry(p);
        m.put("ten", p.getUsername());
        m.put("loai", os != null ? "be" : "pc");
        if (os != null && !os.isEmpty()) {
            m.put("os", os);
        } else {
            m.remove("os");
        }
        m.put("ban", p.getProtocolVersion().getMostRecentSupportedVersion());
        m.put("luc", System.currentTimeMillis());
        if (os != null) {
            m.remove("client");
        }
        dirty = true;
    }

    @Subscribe
    public void onBrand(PlayerClientBrandEvent e) {
        Map<String, Object> m = entry(e.getPlayer());
        if (!"be".equals(m.get("loai"))) {
            m.put("client", e.getBrand());
            dirty = true;
        }
    }

    private synchronized void save() {
        if (!dirty) {
            return;
        }
        dirty = false;
        try {
            Path tmp = file.resolveSibling("thietbi.json.tmp");
            Files.writeString(tmp, GSON.toJson(data), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ex) {
            log.warn("Thiết bị: không ghi được {}: {}", file, ex.getMessage());
        }
    }
}
