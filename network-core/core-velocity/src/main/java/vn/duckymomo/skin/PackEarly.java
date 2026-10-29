package vn.duckymomo.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent;
import com.velocitypowered.api.event.player.configuration.PlayerFinishConfigurationEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPermsProvider;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Gửi gói tài nguyên SỚM (30/10, chủ server thử): ngay ở bước "đang cấu hình" của lần vào đầu tiên, proxy chờ máy người chơi nạp xong
 * (hoặc từ chối / lỗi / quá {@code cho-giay}) rồi mới cho vào thế giới. Màn nạp lẫn vào màn kết nối thay vì hiện lúc đã đứng trong sảnh.
 *
 * Công tắc: {@code plugins/duckymomoskin/goi-tai-nguyen.json} ("bat": true/false), đọc lại mỗi lần có người vào (không cần restart
 * proxy). Tắt = như cũ: chỉ server con (DuckyMoMoCore PlayerSettings) gửi gói sau khi vào. Server con VẪN gửi như thường; cùng mã gói
 * (UUID) + SHA1 nên máy đã có gói thì bỏ qua, không nạp lại. Mã gói phải khớp PlayerSettings.PACK_FONT / PACK_ITEMS.
 * URL/SHA1 do ops/build-font-pack.sh ghi vào file. Bedrock (Floodgate) bỏ qua: không dùng gói Java.
 * Ô /caidat "Font chữ mềm" = meta LuckPerms dmmcaidat.fontviet (bat/tat, mặc định tắt) -> gói font hay gói items.
 */
final class PackEarly {

    static final UUID PACK_FONT = UUID.nameUUIDFromBytes("duckymomo:pack-font".getBytes(StandardCharsets.UTF_8));
    static final UUID PACK_ITEMS = UUID.nameUUIDFromBytes("duckymomo:pack-items".getBytes(StandardCharsets.UTF_8));

    private final ProxyServer proxy;
    private final Logger log;
    private final Path file;
    /** Người đã được gửi gói trong lần kết nối này (chuyển server không gửi lại). */
    private final Set<UUID> sent = ConcurrentHashMap.newKeySet();
    /** Đang chờ máy người chơi trả lời: người -> (mã gói, future). */
    private final Map<UUID, Map.Entry<UUID, CompletableFuture<Void>>> waiting = new ConcurrentHashMap<>();

    PackEarly(ProxyServer proxy, Logger log, Path dataDir) {
        this.proxy = proxy;
        this.log = log;
        this.file = dataDir.resolve("goi-tai-nguyen.json");
    }

    private JsonObject cfg() {
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean bedrock(Player p) {
        try {
            Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object inst = api.getMethod("getInstance").invoke(null);
            return (boolean) api.getMethod("isFloodgatePlayer", UUID.class).invoke(inst, p.getUniqueId());
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean softFont(UUID uuid) {
        try {
            var u = LuckPermsProvider.get().getUserManager().loadUser(uuid).get(3, TimeUnit.SECONDS);
            return "bat".equals(u.getCachedData().getMetaData().getMetaValue("dmmcaidat.fontviet"));
        } catch (Exception e) {
            return false;
        }
    }

    @Subscribe
    public EventTask onFinishConfig(PlayerFinishConfigurationEvent e) {
        Player p = e.player();
        JsonObject c = cfg();
        if (c == null || !c.has("bat") || !c.get("bat").getAsBoolean() || sent.contains(p.getUniqueId())) {
            return null;
        }
        sent.add(p.getUniqueId());
        return EventTask.resumeWhenComplete(CompletableFuture.runAsync(() -> {}).thenCompose(v -> send(p, c)));
    }

    private CompletableFuture<Void> send(Player p, JsonObject c) {
        try {
            if (bedrock(p)) {
                return CompletableFuture.completedFuture(null);
            }
            boolean font = softFont(p.getUniqueId());
            JsonObject g = c.getAsJsonObject(font ? "font" : "items");
            String url = g.get("url").getAsString(), sha = g.get("sha1").getAsString();
            if (url.isBlank() || !sha.matches("[0-9a-fA-F]{40}")) {
                return CompletableFuture.completedFuture(null);
            }
            ResourcePackInfo.Builder b = proxy.createResourcePackBuilder(url).setId(font ? PACK_FONT : PACK_ITEMS)
                    .setHash(HexFormat.of().parseHex(sha)).setShouldForce(false);
            String prompt = g.has("prompt") ? g.get("prompt").getAsString() : "";
            if (!prompt.isEmpty()) {
                b.setPrompt(LegacyComponentSerializer.legacyAmpersand().deserialize(prompt));
            }
            int wait = c.has("cho-giay") ? c.get("cho-giay").getAsInt() : 30;
            CompletableFuture<Void> f = new CompletableFuture<>();
            waiting.put(p.getUniqueId(), Map.entry(font ? PACK_FONT : PACK_ITEMS, f));
            p.sendResourcePackOffer(b.build());
            // quá giờ (tải lỗi, máy không trả lời, chưa bấm hỏi): vẫn cho vào; server con sẽ gửi lại gói sau
            return f.completeOnTimeout(null, wait, TimeUnit.SECONDS).whenComplete((v, t) -> waiting.remove(p.getUniqueId()));
        } catch (Exception ex) {
            log.warn("Gói tài nguyên sớm: lỗi với {}: {}", p.getUsername(), ex.toString());
            return CompletableFuture.completedFuture(null);
        }
    }

    @Subscribe
    public void onStatus(PlayerResourcePackStatusEvent e) {
        var w = waiting.get(e.getPlayer().getUniqueId());
        if (w == null || (e.getPackId() != null && !w.getKey().equals(e.getPackId()))) {
            return;
        }
        switch (e.getStatus()) {
            case SUCCESSFUL, DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> w.getValue().complete(null);
            default -> { }
        }
    }

    @Subscribe
    public void onQuit(DisconnectEvent e) {
        sent.remove(e.getPlayer().getUniqueId());
        var w = waiting.remove(e.getPlayer().getUniqueId());
        if (w != null) {
            w.getValue().complete(null);
        }
    }
}
