package vn.duckymomo.core.paper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Chương trình Media (/media, Bang Hội Chiến, 19/10): kiểm soát rank Media (người livestream về server) bằng SỐ LIỆU, admin không phải tự đếm.
 *  - /media dangky <link kênh TikTok/YouTube> → đơn chờ duyệt (trang quản trị › Media, hoặc /media duyet <tên>): rank media CÓ HẠN
 *    (trial-days, LuckPerms parent tạm), gia hạn /media giahan <tên> <ngày>, gỡ /media go <tên>.
 *  - /media live [link buổi live] → báo cả server (link bấm được), mở "buổi live". Mỗi check-minutes phút server tự kiểm: người đó còn ONLINE
 *    THẬT trong server (Activity, không tính AFK) và kênh CÓ ĐANG LIVE không (TikTok: trang api-live không chính thức, status 2 = đang live;
 *    YouTube: Data API nếu có youtube-key). Chỉ phút thoả cả hai mới tính "đã xác minh"; kiểm không được (bị chặn/lỗi) thì tính "chưa xác minh"
 *    để admin xem link. Buổi ≥ min-session-minutes mới tính buổi.
 *  - Chỉ tiêu 7 ngày: ≥ min-sessions buổi VÀ ≥ min-hours giờ. Mỗi tuần (thứ 2) chấm tuần trước; thiếu 2 tuần liền → báo admin (không tự gỡ).
 *  - /media baocao <tên> <lý do>: người chơi tố cáo (link giả, nội dung xấu).
 * Lưu media.yml; ghi media.json cho trang quản trị (panel đọc, nút duyệt/từ chối/gia hạn/gỡ chạy lệnh /media qua RCON).
 */
final class MediaProgram implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("HH:mm dd/MM").withZone(VN);
    private static final long MIN = 60_000L;
    private static final long DAY = 86_400_000L;
    private static final Pattern TIKTOK = Pattern.compile("(?i)^(?:https?://)?(?:www\\.|m\\.|vt\\.)?tiktok\\.com/@([A-Za-z0-9._]{2,24})");
    private static final Pattern YOUTUBE = Pattern.compile("(?i)^(?:https?://)?(?:www\\.|m\\.)?youtube\\.com/(@[A-Za-z0-9._-]{3,30}|channel/UC[A-Za-z0-9_-]{22})");
    private static final Pattern LIVE_LINK = Pattern.compile("(?i)^https?://(?:www\\.|m\\.|vt\\.|vm\\.)?(?:tiktok\\.com|youtube\\.com|youtu\\.be)/\\S{1,200}$");

    private final DuckyMoMoCore plugin;
    private final Activity activity;
    private final String group;
    private final int trialDays;
    private final int extendDays;
    private final int minSessions;
    private final int minSessionMinutes;
    private final double minHours;
    private final int checkMinutes;
    private final boolean tiktokCheck;
    private final String youtubeKey;
    private final int announceCooldown;
    private final int failWeeks;
    private final File file;
    private final YamlConfiguration data;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Map<String, String> ytIds = new HashMap<>();

    MediaProgram(DuckyMoMoCore plugin, Activity activity, ConfigurationSection c) {
        this.plugin = plugin;
        this.activity = activity;
        this.group = c.getString("group", "media").toLowerCase(Locale.ROOT);
        this.trialDays = Math.max(1, c.getInt("trial-days", 14));
        this.extendDays = Math.max(1, c.getInt("extend-days", 30));
        this.minSessions = Math.max(0, c.getInt("min-sessions", 4));
        this.minSessionMinutes = Math.max(5, c.getInt("min-session-minutes", 30));
        this.minHours = Math.max(0, c.getDouble("min-hours", 6));
        this.checkMinutes = Math.max(1, c.getInt("check-minutes", 5));
        this.tiktokCheck = c.getBoolean("tiktok-check", true);
        String yk = c.getString("youtube-key", "");
        this.youtubeKey = yk == null || yk.startsWith("${") ? "" : yk.trim();   // ${CFG_YOUTUBE_KEY} chưa được điền = chưa có khoá
        this.announceCooldown = Math.max(0, c.getInt("announce-cooldown-minutes", 60));
        this.failWeeks = Math.max(1, c.getInt("fail-weeks-alert", 2));
        this.file = new File(plugin.getDataFolder(), "media.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    String describe() {
        return "chỉ tiêu " + minSessions + " buổi ≥" + minSessionMinutes + " phút + " + minHours + " giờ / 7 ngày, kiểm mỗi " + checkMinutes + " phút"
                + (tiktokCheck ? ", TikTok" : "") + (youtubeKey == null || youtubeKey.isBlank() ? "" : ", YouTube");
    }

    void start() {
        long period = checkMinutes * 1200L;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period);
        Bukkit.getScheduler().runTaskTimer(plugin, this::weekly, 200L, 12_000L);
        writeJson();
    }

    void stop() {
        // buổi đang mở vẫn giữ (khởi động lại server không mất buổi); lưu lần cuối
        save();
    }

    // ---------------------------------------------------------------- dữ liệu

    private String k(UUID u) {
        return "nguoi." + u;
    }

    private boolean registered(UUID u) {
        return data.isConfigurationSection(k(u));
    }

    /** Trạng thái đơn; DUYET mà rank đã quá hạn (LuckPerms tự gỡ nhóm) thì là HET (22/10: trước vẫn hiện "đang là Sứ Giả", vẫn bị chấm chỉ tiêu). */
    private String status(UUID u) {
        String st = data.getString(k(u) + ".trangthai", "");
        long het = data.getLong(k(u) + ".het");
        return "DUYET".equals(st) && het > 0 && het <= System.currentTimeMillis() ? "HET" : st;
    }

    private UUID byName(String name) {
        ConfigurationSection n = data.getConfigurationSection("nguoi");
        if (n != null) {
            for (String id : n.getKeys(false)) {
                if (name.equalsIgnoreCase(n.getString(id + ".ten"))) {
                    return UUID.fromString(id);
                }
            }
        }
        return null;
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được media.yml: " + e.getMessage());
        }
    }

    /** Các buổi trong khoảng [from, to): {soBuoi, phutXacMinh, phutChuaXacMinh}. Buổi tính khi tổng phút ≥ min-session-minutes. */
    private long[] stats(UUID u, long from, long to) {
        long sessions = 0, ok = 0, unk = 0;
        ConfigurationSection b = data.getConfigurationSection(k(u) + ".buoi");
        if (b != null) {
            for (String id : b.getKeys(false)) {
                long bd = b.getLong(id + ".bd");
                if (bd < from || bd >= to) {
                    continue;
                }
                long xm = b.getLong(id + ".xm"), cx = b.getLong(id + ".cxm");
                if (xm + cx >= minSessionMinutes) {
                    sessions++;
                }
                ok += xm;
                unk += cx;
            }
        }
        return new long[] {sessions, ok, unk};
    }

    private boolean meets(long[] s) {
        return s[0] >= minSessions && (s[1] + s[2]) / 60.0 >= minHours;
    }

    // ---------------------------------------------------------------- kiểm định kỳ

    private void tick() {
        ConfigurationSection n = data.getConfigurationSection("nguoi");
        if (n == null) {
            return;
        }
        List<Object[]> todo = new ArrayList<>();
        for (String id : n.getKeys(false)) {
            String cur = n.getString(id + ".dang-live");
            if (cur == null) {
                continue;
            }
            UUID u = UUID.fromString(id);
            Player p = Bukkit.getPlayer(u);
            boolean online = p != null && (activity == null || activity.activeNow(u));
            long bd = n.getLong(id + ".buoi." + cur + ".bd");
            if (System.currentTimeMillis() - bd > 8 * 3_600_000L) {
                endSession(u, "quá 8 giờ");
                continue;
            }
            todo.add(new Object[] {u, cur, online, n.getString(id + ".kenh", "")});
        }
        if (todo.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Object[]> results = new ArrayList<>();
            for (Object[] t : todo) {
                results.add(new Object[] {t[0], t[1], t[2], (Boolean) t[2] ? liveNow((String) t[3]) : Boolean.FALSE});
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Object[] r : results) {
                    UUID u = (UUID) r[0];
                    String path = k(u) + ".buoi." + r[1];
                    if (!data.isConfigurationSection(path) || !(Boolean) r[2]) {
                        continue;
                    }
                    Boolean live = (Boolean) r[3];
                    if (live == null) {
                        data.set(path + ".cxm", data.getLong(path + ".cxm") + checkMinutes);
                    } else if (live) {
                        data.set(path + ".xm", data.getLong(path + ".xm") + checkMinutes);
                    } else {
                        data.set(path + ".tat", data.getInt(path + ".tat") + 1);
                        Player p = Bukkit.getPlayer(u);
                        if (p != null && data.getInt(path + ".tat") == 1) {
                            p.sendMessage(c("&e[Sứ Giả] Kênh của bạn đang KHÔNG live (theo " + platform(data.getString(k(u) + ".kenh", ""))
                                    + "): thời gian này không được tính. Live xong gõ &f/sugia tat&e."));
                        }
                        if (data.getInt(path + ".tat") >= Math.max(3, 30 / checkMinutes)) {
                            endSession(u, "kênh không live");
                        }
                    }
                }
                save();
                writeJson();
            });
        });
    }

    /** true = đang live, false = không live, null = không kiểm được (TikTok chặn, lỗi mạng, YouTube chưa có khoá). */
    private Boolean liveNow(String channel) {
        try {
            Matcher m = TIKTOK.matcher(channel);
            if (m.find()) {
                if (!tiktokCheck) {
                    return null;
                }
                String url = "https://www.tiktok.com/api-live/user/room/?aid=1988&app_name=tiktok_web&device_platform=web_pc&sourceType=54&uniqueId="
                        + URLEncoder.encode(m.group(1), StandardCharsets.UTF_8);
                HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36")
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
                JsonObject d = JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonObject("data");
                if (d == null) {
                    return null;
                }
                JsonObject room = d.getAsJsonObject("liveRoom");
                JsonObject user = d.getAsJsonObject("user");
                int st = room != null && room.has("status") ? room.get("status").getAsInt() : user != null && user.has("status") ? user.get("status").getAsInt() : -1;
                return st == 2 ? Boolean.TRUE : st < 0 ? null : Boolean.FALSE;
            }
            m = YOUTUBE.matcher(channel);
            if (m.find()) {
                if (youtubeKey == null || youtubeKey.isBlank()) {
                    return null;
                }
                String id = m.group(1).startsWith("channel/") ? m.group(1).substring(8) : ytIds.get(m.group(1));
                if (id == null) {
                    JsonObject j = getJson("https://www.googleapis.com/youtube/v3/channels?part=id&forHandle="
                            + URLEncoder.encode(m.group(1), StandardCharsets.UTF_8) + "&key=" + youtubeKey);
                    JsonArray items = j.getAsJsonArray("items");
                    if (items == null || items.isEmpty()) {
                        return null;
                    }
                    id = items.get(0).getAsJsonObject().get("id").getAsString();
                    ytIds.put(m.group(1), id);
                }
                JsonObject j = getJson("https://www.googleapis.com/youtube/v3/search?part=id&type=video&eventType=live&channelId=" + id + "&key=" + youtubeKey);
                JsonArray items = j.getAsJsonArray("items");
                return items != null && !items.isEmpty();
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private JsonObject getJson(String url) throws IOException, InterruptedException {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return JsonParser.parseString(r.body()).getAsJsonObject();
    }

    private static String platform(String channel) {
        return TIKTOK.matcher(channel).find() ? "TikTok" : YOUTUBE.matcher(channel).find() ? "YouTube" : "?";
    }

    private void endSession(UUID u, String why) {
        String cur = data.getString(k(u) + ".dang-live");
        if (cur == null) {
            return;
        }
        String path = k(u) + ".buoi." + cur;
        data.set(path + ".kt", System.currentTimeMillis());
        data.set(path + ".ly-do-ket", why);
        data.set(k(u) + ".dang-live", null);
        save();
        Player p = Bukkit.getPlayer(u);
        if (p != null) {
            long xm = data.getLong(path + ".xm"), cx = data.getLong(path + ".cxm");
            p.sendMessage(c("&d[Sứ Giả] Kết thúc buổi live (" + why + "): tính &f" + (xm + cx) + " phút &7(xác minh " + xm + ", chưa xác minh " + cx + ")"
                    + ((xm + cx) >= minSessionMinutes ? " &a✔ đủ 1 buổi" : " &c✘ chưa đủ " + minSessionMinutes + " phút")));
        }
        writeJson();
    }

    /** Thứ 2 mỗi tuần: chấm tuần trước (Thứ 2–Chủ nhật), thiếu fail-weeks-alert tuần liền thì báo admin. */
    private void weekly() {
        LocalDate today = LocalDate.now(VN);
        String week = today.get(IsoFields.WEEK_BASED_YEAR) + "-" + today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        if (week.equals(data.getString("tuan-da-cham"))) {
            return;
        }
        LocalDate monday = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        long to = monday.atStartOfDay(VN).toInstant().toEpochMilli(), from = to - 7 * DAY;
        ConfigurationSection n = data.getConfigurationSection("nguoi");
        if (n != null && data.isSet("tuan-da-cham")) {
            for (String id : n.getKeys(false)) {
                if (!"DUYET".equals(status(UUID.fromString(id)))) {
                    continue;
                }
                long[] s = stats(UUID.fromString(id), from, to);
                boolean ok = meets(s);
                int streak = ok ? 0 : n.getInt(id + ".thieu-lien") + 1;
                n.set(id + ".thieu-lien", streak);
                n.set(id + ".tuan-truoc", (ok ? "ĐẠT" : "THIẾU") + " " + s[0] + " buổi, " + String.format(Locale.ROOT, "%.1f", (s[1] + s[2]) / 60.0) + " giờ");
                if (streak >= failWeeks) {
                    String msg = "&c[Sứ Giả] " + n.getString(id + ".ten") + " thiếu chỉ tiêu " + streak + " tuần liền (tuần trước: " + s[0] + " buổi, "
                            + String.format(Locale.ROOT, "%.1f", (s[1] + s[2]) / 60.0) + " giờ). Xem trang quản trị › Sứ Giả.";
                    Bukkit.getOnlinePlayers().stream().filter(p -> p.hasPermission("duckymomo.admin")).forEach(p -> p.sendMessage(c(msg)));
                    plugin.getLogger().info(LEGACY.serialize(c(msg)).replaceAll("§.", ""));
                }
            }
        }
        data.set("tuan-da-cham", week);
        save();
        writeJson();
    }

    // ---------------------------------------------------------------- rank (LuckPerms, có hạn)

    private void grant(UUID u, long untilMs) {
        LuckPermsProvider.get().getUserManager().modifyUser(u, user -> {
            user.data().clear(nd -> nd instanceof InheritanceNode in && in.getGroupName().equalsIgnoreCase(group));
            if (untilMs > System.currentTimeMillis()) {
                user.data().add(InheritanceNode.builder(group).expiry(Instant.ofEpochMilli(untilMs)).build());
            }
        });
        data.set(k(u) + ".het", untilMs);
    }

    // ---------------------------------------------------------------- sự kiện

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (data.getString(k(e.getPlayer().getUniqueId()) + ".dang-live") != null) {
            endSession(e.getPlayer().getUniqueId(), "thoát game");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID u = p.getUniqueId();
        if (!"DUYET".equals(status(u))) {
            return;
        }
        long now = System.currentTimeMillis();
        long[] s = stats(u, now - 7 * DAY, now);
        long het = data.getLong(k(u) + ".het");
        Bukkit.getScheduler().runTaskLater(plugin, () -> p.sendMessage(c("&d[Sứ Giả] 7 ngày qua: &f" + s[0] + "/" + minSessions + " buổi&7, &f"
                + String.format(Locale.ROOT, "%.1f", (s[1] + s[2]) / 60.0) + "/" + fmt(minHours) + " giờ " + (meets(s) ? "&a✔ đạt" : "&e(chưa đạt)")
                + (het > 0 ? " &7· rank hết hạn " + WHEN.format(Instant.ofEpochMilli(het)) : "") + " &7— live: /sugia live")), 60L);
    }

    // ---------------------------------------------------------------- lệnh

    boolean command(CommandSender s, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        boolean admin = s.hasPermission("duckymomo.admin");
        if (admin && List.of("duyet", "tuchoi", "giahan", "go", "xem", "ds").contains(sub)) {
            return adminCommand(s, sub, args);
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("/sugia ds · duyet|tuchoi|go <tên> · giahan <tên> <ngày> · xem <tên>");
            return true;
        }
        UUID u = p.getUniqueId();
        switch (sub) {
            case "dangky" -> {
                if (args.length < 2 || !(TIKTOK.matcher(args[1]).find() || YOUTUBE.matcher(args[1]).find())) {
                    p.sendMessage(c("&cDùng: /sugia dangky <link kênh> &7(vd https://www.tiktok.com/@tenkenh hoặc https://www.youtube.com/@tenkenh)"));
                    return true;
                }
                if ("DUYET".equals(status(u))) {
                    p.sendMessage(c("&eBạn đã là Sứ Giả. Đổi kênh: nhắn admin."));
                    return true;
                }
                data.set(k(u) + ".ten", p.getName());
                data.set(k(u) + ".kenh", args[1]);
                data.set(k(u) + ".trangthai", "CHO");
                data.set(k(u) + ".dangky-luc", System.currentTimeMillis());
                save();
                p.sendMessage(c("&a[Sứ Giả] Đã gửi đơn. Trong lúc chờ duyệt, mỗi lần live gõ &f/sugia live &a để server ghi buổi live làm bằng chứng."));
                Bukkit.getOnlinePlayers().stream().filter(a -> a.hasPermission("duckymomo.admin"))
                        .forEach(a -> a.sendMessage(c("&d[Sứ Giả] " + p.getName() + " đăng ký Sứ Giả: &f" + args[1] + " &7(trang quản trị › Sứ Giả)")));
                writeJson();
            }
            case "live" -> {
                if (!registered(u) || "TUCHOI".equals(status(u))) {
                    p.sendMessage(c("&cChỉ Sứ Giả hoặc người đã đăng ký (/sugia dangky <link kênh>) mới báo live được."));
                    return true;
                }
                if (data.getString(k(u) + ".dang-live") != null) {
                    p.sendMessage(c("&eĐang có buổi live mở. Kết thúc: &f/sugia tat"));
                    return true;
                }
                String link = args.length > 1 ? args[1] : data.getString(k(u) + ".kenh", "");
                if (args.length > 1 && !LIVE_LINK.matcher(link).matches()) {
                    p.sendMessage(c("&cLink phải là TikTok hoặc YouTube."));
                    return true;
                }
                long now = System.currentTimeMillis();
                String id = String.valueOf(now);
                data.set(k(u) + ".buoi." + id + ".bd", now);
                data.set(k(u) + ".buoi." + id + ".link", link);
                data.set(k(u) + ".dang-live", id);
                save();
                long last = data.getLong(k(u) + ".bao-luc");
                if (now - last >= announceCooldown * MIN) {
                    data.set(k(u) + ".bao-luc", now);
                    Component msg = c("&c● LIVE &f" + p.getName() + " &7đang livestream server! ")
                            .append(c("&b&n[Bấm để xem]").clickEvent(ClickEvent.openUrl(link)).hoverEvent(HoverEvent.showText(c("&7" + link))));
                    Bukkit.broadcast(msg);
                }
                p.sendMessage(c("&d[Sứ Giả] Đã mở buổi live. Server kiểm mỗi " + checkMinutes + " phút (bạn online thật + kênh đang live). Xong gõ &f/sugia tat&d."));
                writeJson();
            }
            case "tat", "ket" -> {
                if (data.getString(k(u) + ".dang-live") == null) {
                    p.sendMessage(c("&7Không có buổi live nào đang mở."));
                } else {
                    endSession(u, "tự kết thúc");
                }
            }
            case "baocao" -> {
                if (args.length < 3) {
                    p.sendMessage(c("&cDùng: /sugia baocao <tên> <lý do>"));
                    return true;
                }
                List<String> list = new ArrayList<>(data.getStringList("baocao"));
                list.add(WHEN.format(Instant.now()) + " | " + p.getName() + " → " + args[1] + ": " + String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)));
                data.set("baocao", list.subList(Math.max(0, list.size() - 100), list.size()));
                save();
                p.sendMessage(c("&aĐã gửi tố cáo cho admin. Cảm ơn bạn!"));
                Bukkit.getOnlinePlayers().stream().filter(a -> a.hasPermission("duckymomo.admin"))
                        .forEach(a -> a.sendMessage(c("&c[Sứ Giả] Tố cáo " + args[1] + " từ " + p.getName())));
                writeJson();
            }
            default -> info(p);
        }
        return true;
    }

    /** /sugia: menu (22/10, trước in chat). */
    private void info(Player p) {
        UUID u = p.getUniqueId();
        InfoMenu m = InfoMenu.of("&c◆ Sứ Giả").accent(org.bukkit.Material.RED_STAINED_GLASS_PANE);
        Tip top = Tip.of("&c◆ &fSứ Giả").sub("rank livestream").accent("&c")
                .desc("Rank miễn phí cho người livestream server, admin duyệt.").line()
                .head("Chỉ tiêu 7 ngày")
                .item(minSessions + " buổi live (mỗi buổi ≥ " + minSessionMinutes + " phút)")
                .item(fmt(minHours) + " giờ live")
                .note("Tính khi bạn ONLINE trong server và kênh ĐANG LIVE (server tự kiểm).");
        if (registered(u)) {
            long now = System.currentTimeMillis();
            long[] st = stats(u, now - 7 * DAY, now);
            String sv = switch (status(u)) {
                case "DUYET" -> "&ađã duyệt";
                case "CHO" -> "&echờ duyệt";
                case "TUCHOI" -> "&cbị từ chối";
                default -> "&7hết hạn";
            };
            top.line().head("Đơn của bạn").kv("Trạng thái", sv).kv("Kênh", data.getString(k(u) + ".kenh", ""))
                    .kv("7 ngày qua", st[0] + " buổi, " + String.format(Locale.ROOT, "%.1f", (st[1] + st[2]) / 60.0) + " giờ")
                    .check(meets(st), meets(st) ? "đạt chỉ tiêu" : "chưa đạt chỉ tiêu");
        }
        m.top(top.apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.RED_BANNER)));
        m.add(Tip.of("&a✎ &fĐăng ký").accent("&a").desc("Gửi link kênh TikTok / YouTube để admin duyệt").line()
                .kv("Lệnh", "/sugia dangky <link kênh>").click("Bấm để điền lệnh").apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.WRITABLE_BOOK)),
                InfoMenu.suggest("/sugia dangky", "Dán link kênh"));
        m.add(Tip.of("&c● &fĐang live").accent("&c").desc("Báo server bạn bắt đầu live (để tính chỉ tiêu)").line()
                .kv("Lệnh", "/sugia live [link]").click("Bấm để báo đang live").apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.REDSTONE_TORCH)),
                pl -> { pl.closeInventory(); pl.performCommand("sugia live"); });
        m.add(Tip.of("&7■ &fKết thúc live").desc("Báo server bạn đã tắt live").line()
                .kv("Lệnh", "/sugia tat").click("Bấm để báo đã tắt").apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.LEVER)),
                pl -> { pl.closeInventory(); pl.performCommand("sugia tat"); });
        m.add(Tip.of("&e⚑ &fBáo cáo").accent("&e").desc("Báo admin một Sứ Giả làm sai (live nội dung xấu, không live…)").line()
                .kv("Lệnh", "/sugia baocao <tên> <lý do>").click("Bấm để điền lệnh").apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.BELL)),
                InfoMenu.suggest("/sugia baocao", "Gõ tên và lý do"));
        m.open(p);
    }

    private boolean adminCommand(CommandSender s, String sub, String[] args) {
        if (sub.equals("ds")) {
            ConfigurationSection n = data.getConfigurationSection("nguoi");
            long now = System.currentTimeMillis();
            s.sendMessage(LEGACY.serialize(c("&cSứ Giả &7(" + (n == null ? 0 : n.getKeys(false).size()) + " người)")));
            if (n != null) {
                for (String id : n.getKeys(false)) {
                    long[] st = stats(UUID.fromString(id), now - 7 * DAY, now);
                    s.sendMessage(LEGACY.serialize(c("&f" + n.getString(id + ".ten") + " &7[" + status(UUID.fromString(id)) + "] " + st[0] + " buổi, "
                            + String.format(Locale.ROOT, "%.1f", (st[1] + st[2]) / 60.0) + " giờ (chưa xác minh " + st[2] + "p)"
                            + (meets(st) ? " &a✔" : " &c✘") + " &7" + n.getString(id + ".kenh"))));
                }
            }
            return true;
        }
        if (args.length < 2) {
            s.sendMessage("/sugia " + sub + " <tên>" + (sub.equals("giahan") ? " <ngày>" : ""));
            return true;
        }
        UUID u = byName(args[1]);
        if (u == null) {
            OfflinePlayer o = Bukkit.getOfflinePlayerIfCached(args[1]);
            u = o == null ? null : o.getUniqueId();
            if (u != null && (sub.equals("duyet") || sub.equals("giahan"))) {
                data.set(k(u) + ".ten", o.getName());
                data.set(k(u) + ".kenh", data.getString(k(u) + ".kenh", ""));
            }
        }
        if (u == null) {
            s.sendMessage("Không thấy " + args[1]);
            return true;
        }
        long now = System.currentTimeMillis();
        String name = data.getString(k(u) + ".ten", args[1]);
        switch (sub) {
            case "duyet" -> {
                int days = args.length > 2 ? parse(args[2], trialDays) : trialDays;
                data.set(k(u) + ".trangthai", "DUYET");
                data.set(k(u) + ".thieu-lien", 0);
                grant(u, now + days * DAY);
                notify(u, "&a[Sứ Giả] Đơn Sứ Giả của bạn đã được DUYỆT (thử " + days + " ngày). Nhớ /sugia live mỗi lần livestream!");
                s.sendMessage("Đã duyệt " + name + " (" + days + " ngày).");
            }
            case "giahan" -> {
                int days = args.length > 2 ? parse(args[2], extendDays) : extendDays;
                long base = Math.max(now, data.getLong(k(u) + ".het"));
                data.set(k(u) + ".trangthai", "DUYET");
                grant(u, base + days * DAY);
                notify(u, "&a[Sứ Giả] Rank Sứ Giả được gia hạn thêm " + days + " ngày.");
                s.sendMessage("Đã gia hạn " + name + " thêm " + days + " ngày.");
            }
            case "tuchoi", "go" -> {
                data.set(k(u) + ".trangthai", sub.equals("go") ? "HET" : "TUCHOI");
                grant(u, 0);
                notify(u, sub.equals("go") ? "&e[Sứ Giả] Rank Sứ Giả của bạn đã bị gỡ." : "&e[Sứ Giả] Đơn Sứ Giả chưa được duyệt lần này.");
                s.sendMessage((sub.equals("go") ? "Đã gỡ Sứ Giả của " : "Đã từ chối ") + name + ".");
            }
            case "xem" -> {
                ConfigurationSection b = data.getConfigurationSection(k(u) + ".buoi");
                s.sendMessage(name + " · " + status(u) + " · " + data.getString(k(u) + ".kenh"));
                if (b != null) {
                    List<String> ids = new ArrayList<>(b.getKeys(false));
                    for (String id : ids.subList(Math.max(0, ids.size() - 10), ids.size())) {
                        s.sendMessage("  " + WHEN.format(Instant.ofEpochMilli(b.getLong(id + ".bd"))) + " · " + b.getLong(id + ".xm") + "p xác minh, "
                                + b.getLong(id + ".cxm") + "p chưa · " + b.getString(id + ".link"));
                    }
                }
                return true;
            }
            default -> {
                return true;
            }
        }
        save();
        writeJson();
        return true;
    }

    private void notify(UUID u, String msg) {
        Player p = Bukkit.getPlayer(u);
        if (p != null) {
            p.sendMessage(c(msg));
        }
    }

    List<String> tab(CommandSender s, String[] args) {
        if (args.length == 1) {
            List<String> l = new ArrayList<>(List.of("dangky", "live", "tat", "baocao"));
            if (s.hasPermission("duckymomo.admin")) {
                l.addAll(List.of("ds", "duyet", "tuchoi", "giahan", "go", "xem"));
            }
            return l;
        }
        return null;
    }

    // ---------------------------------------------------------------- trang quản trị

    private void writeJson() {
        JsonObject root = new JsonObject();
        JsonObject cfg = new JsonObject();
        cfg.addProperty("buoi", minSessions);
        cfg.addProperty("phut_buoi", minSessionMinutes);
        cfg.addProperty("gio", minHours);
        cfg.addProperty("kiem_phut", checkMinutes);
        cfg.addProperty("thu_viec", trialDays);
        cfg.addProperty("gia_han", extendDays);
        root.add("chi_tieu", cfg);
        JsonArray people = new JsonArray();
        ConfigurationSection n = data.getConfigurationSection("nguoi");
        long now = System.currentTimeMillis();
        if (n != null) {
            for (String id : n.getKeys(false)) {
                UUID u = UUID.fromString(id);
                long[] st = stats(u, now - 7 * DAY, now);
                JsonObject o = new JsonObject();
                o.addProperty("ten", n.getString(id + ".ten"));
                o.addProperty("kenh", n.getString(id + ".kenh", ""));
                o.addProperty("nen_tang", platform(n.getString(id + ".kenh", "")));
                o.addProperty("trang_thai", status(u));
                o.addProperty("dang_ky", n.getLong(id + ".dangky-luc"));
                o.addProperty("het", n.getLong(id + ".het"));
                o.addProperty("buoi_7", st[0]);
                o.addProperty("phut_xm_7", st[1]);
                o.addProperty("phut_cxm_7", st[2]);
                o.addProperty("dat", meets(st));
                o.addProperty("thieu_lien", n.getInt(id + ".thieu-lien"));
                o.addProperty("tuan_truoc", n.getString(id + ".tuan-truoc", ""));
                o.addProperty("dang_live", n.getString(id + ".dang-live") != null);
                o.addProperty("gio_choi", activity == null ? 0 : Math.round(activity.totalHours(u) * 10) / 10.0);
                JsonArray bs = new JsonArray();
                ConfigurationSection b = n.getConfigurationSection(id + ".buoi");
                if (b != null) {
                    List<String> ids = new ArrayList<>(b.getKeys(false));
                    for (String bid : ids.subList(Math.max(0, ids.size() - 15), ids.size())) {
                        JsonObject x = new JsonObject();
                        x.addProperty("bd", b.getLong(bid + ".bd"));
                        x.addProperty("kt", b.getLong(bid + ".kt"));
                        x.addProperty("xm", b.getLong(bid + ".xm"));
                        x.addProperty("cxm", b.getLong(bid + ".cxm"));
                        x.addProperty("link", b.getString(bid + ".link", ""));
                        bs.add(x);
                    }
                }
                o.add("buoi", bs);
                people.add(o);
            }
        }
        root.add("nguoi", people);
        JsonArray rep = new JsonArray();
        data.getStringList("baocao").forEach(rep::add);
        root.add("bao_cao", rep);
        root.addProperty("cap_nhat", now);
        try {
            Files.writeString(new File(plugin.getDataFolder(), "media.json").toPath(), root.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Không ghi được media.json: " + e.getMessage());
        }
    }

    private static int parse(String s, int d) {
        try {
            return Math.max(1, Math.min(3650, Integer.parseInt(s)));
        } catch (NumberFormatException e) {
            return d;
        }
    }

    private static String fmt(double h) {
        return h == Math.floor(h) ? String.valueOf((long) h) : String.valueOf(h);
    }

    private static Component c(String s) {
        return Tip.ns(LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false));
    }
}
