package vn.duckymomo.core.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Điểm danh hằng ngày (/diemdanh). Đếm TỔNG số ngày đã điểm danh (bỏ ngày không mất chuỗi, chỉ không tăng).
 * Mỗi lần: quà ngày (daily-rewards) + quà mốc nếu chạm mốc (milestones, lặp theo cycle-days). Quà theo dạng Rewards.
 * Phải online đủ min-online-minutes trong phiên mới điểm danh được (chống tài khoản phụ vào cày). Ngày tính theo giờ Việt Nam.
 * Lưu: plugins/DuckyMoMoCore/diemdanh.yml
 */
final class DailyCheckin implements Listener {
    static volatile DailyCheckin INSTANCE;

    /** Hôm nay đã điểm danh chưa (PlayerSettings nhắc). */
    boolean checkedToday(java.util.UUID id) {
        return today().equals(data.getString("players." + id + ".last"));
    }

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final JavaPlugin plugin;
    private final ConfigurationSection cfg;
    private final Rewards rewards;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, Long> joined = new HashMap<>();
    private final Map<UUID, Long> moved = new HashMap<>();
    private final java.util.Set<UUID> waiting = new java.util.HashSet<>();   // gõ /diemdanh sớm: đủ phút thì tự điểm danh (29/10)   // lần cuối cử động (phiếu online không tính lúc đứng im)

    DailyCheckin(JavaPlugin plugin, ConfigurationSection cfg, Rewards rewards) {
        INSTANCE = this;
        this.plugin = plugin;
        this.cfg = cfg;
        this.rewards = rewards;
        this.file = new File(plugin.getDataFolder(), "diemdanh.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        Shared.FORGET.add(id -> Shared.forget(data, file, id));   // admin reset "như người mới"
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            joined.put(p.getUniqueId(), now);
            moved.put(p.getUniqueId(), now);
        }
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {   // mỗi phút; dmm reload tạo bản mới thì bản cũ tự dừng
            if (INSTANCE != this) {
                task.cancel();
                return;
            }
            onlineTick();
        }, 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {   // 20 giây: ai đang chờ đủ phút online thì tự điểm danh
            if (INSTANCE != this) {
                task.cancel();
                return;
            }
            autoCheckin();
        }, 400L, 400L);
    }

    /**
     * Phiếu online (29/10, set Chuyên Cần): mỗi every-minutes phút ĐANG HOẠT ĐỘNG trong ngày (giờ VN) nhận 1 món item, tối đa max-per-day.
     * Phút đứng im quá idle-minutes không tính; bot không tính. Lưu players.<uuid>.online.{day,min,got}.
     */
    private void onlineTick() {
        ConfigurationSection o = cfg.getConfigurationSection("online-ticket");
        if (o == null || !o.getBoolean("enabled", true)) {
            return;
        }
        String item = o.getString("item", "phieu_dd");
        int every = Math.max(1, o.getInt("every-minutes", 60));
        int max = o.getInt("max-per-day", 2);
        long idle = o.getLong("idle-minutes", 5) * 60_000L;
        long now = System.currentTimeMillis();
        String day = today();
        boolean dirty = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (BotView.isBot(p) || now - moved.getOrDefault(p.getUniqueId(), 0L) > idle) {
                continue;
            }
            String k = "players." + p.getUniqueId() + ".online";
            if (!day.equals(data.getString(k + ".day"))) {
                data.set(k + ".day", day);
                data.set(k + ".min", 0);
                data.set(k + ".got", 0);
            }
            int got = data.getInt(k + ".got");
            if (got >= max) {
                continue;
            }
            int min = data.getInt(k + ".min") + 1;
            data.set(k + ".min", min);
            dirty = true;
            if (min >= every * (got + 1)) {
                got++;
                data.set(k + ".got", got);
                List<String> r = rewards.give(p, List.of("do " + item + " 1"));
                p.sendMessage(c("&e☀ Online " + min + " phút hôm nay: nhận &f" + String.join("&7, &f", r)
                        + (got < max ? "&7 (phiếu tiếp sau " + every + " phút nữa)" : "&7 (đủ " + max + " phiếu online hôm nay)")));
            }
        }
        if (dirty) {
            save();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(org.bukkit.event.player.PlayerMoveEvent e) {
        org.bukkit.Location f = e.getFrom();
        org.bukkit.Location t = e.getTo();
        if (f.getYaw() != t.getYaw() || f.getPitch() != t.getPitch() || f.getBlockX() != t.getBlockX() || f.getBlockZ() != t.getBlockZ()) {
            moved.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s);
    }

    private static String today() {
        return LocalDate.now(VN).toString();
    }

    private TreeMap<Integer, List<String>> milestones() {
        TreeMap<Integer, List<String>> m = new TreeMap<>();
        ConfigurationSection s = cfg.getConfigurationSection("milestones");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                try {
                    m.put(Integer.parseInt(k.trim()), s.getStringList(k));
                } catch (NumberFormatException ignored) {
                    // mốc không phải số: bỏ
                }
            }
        }
        return m;
    }

    /** Ngày thứ mấy trong chu kỳ mốc (1..cycle). */
    private int cycleDay(int count) {
        int cycle = cfg.getInt("cycle-days", 30);
        return cycle > 0 ? ((count - 1) % cycle) + 1 : count;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        joined.put(p.getUniqueId(), System.currentTimeMillis());
        moved.put(p.getUniqueId(), System.currentTimeMillis());
        if (!cfg.getBoolean("remind-on-join", true)) {
            return;
        }
        if (!today().equals(data.getString("players." + p.getUniqueId() + ".last"))) {
            int mins = cfg.getInt("min-online-minutes", 5);
            JoinNotes.add(p, 30, c("&e☷ Hôm nay bạn chưa điểm danh. ").append(c("&a&n[Bấm để điểm danh]")
                    .clickEvent(ClickEvent.runCommand("/diemdanh"))
                    .hoverEvent(HoverEvent.showText(c("&7/diemdanh" + (mins > 0 ? " (cần online đủ " + mins + " phút)" : ""))))));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        joined.remove(e.getPlayer().getUniqueId());
        moved.remove(e.getPlayer().getUniqueId());
        waiting.remove(e.getPlayer().getUniqueId());
    }

    boolean command(CommandSender s, String[] args) {
        // Admin (28/10, trang Bot › Hồ sơ): /diemdanh dat <tên> <số ngày> — đặt tổng số ngày, lần cuối = hôm nay (0 = xoá)
        if (args.length >= 3 && args[0].equalsIgnoreCase("dat") && s.hasPermission("duckymomo.admin")) {
            org.bukkit.OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(args[1]);
            int n;
            try {
                n = Math.max(0, Math.min(100000, Integer.parseInt(args[2])));
            } catch (NumberFormatException e) {
                n = -1;
            }
            if (t == null || n < 0) {
                s.sendMessage("/diemdanh dat <tên đã từng vào> <số ngày>");
                return true;
            }
            String k = "players." + t.getUniqueId();
            data.set(k + ".count", n);
            data.set(k + ".last", n > 0 ? today() : null);
            save();
            s.sendMessage("Đã đặt điểm danh của " + t.getName() + ": " + n + " ngày");
            return true;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("Chỉ người chơi điểm danh được.");
            return true;
        }
        String key = "players." + p.getUniqueId();
        int count = data.getInt(key + ".count", 0);
        if (args.length >= 1 && args[0].equalsIgnoreCase("xem")) {
            status(p, count);
            return true;
        }
        if (today().equals(data.getString(key + ".last"))) {
            p.sendMessage(c("&7Hôm nay bạn đã điểm danh rồi (tổng &f" + count + "&7 ngày). Quay lại vào ngày mai nhé!"));
            status(p, count);
            return true;
        }
        long left = minutesLeft(p);
        if (left > 0) {
            waiting.add(p.getUniqueId());
            p.sendMessage(c("&e⏳ Còn &f" + left + " phút&e nữa là điểm danh được. &7Cứ chơi tiếp, tới lúc đó server &atự điểm danh&7 cho bạn."));
            return true;
        }
        checkin(p);
        return true;
    }

    /** Số phút online còn thiếu trong phiên này (0 = đủ). */
    private long minutesLeft(Player p) {
        long ms = System.currentTimeMillis() - joined.getOrDefault(p.getUniqueId(), System.currentTimeMillis());
        long need = cfg.getInt("min-online-minutes", 5) * 60_000L;
        return ms >= need ? 0 : (need - ms + 59_999) / 60_000;
    }

    private void autoCheckin() {
        for (java.util.Iterator<UUID> it = waiting.iterator(); it.hasNext(); ) {
            Player p = Bukkit.getPlayer(it.next());
            if (p == null) {
                it.remove();
                continue;
            }
            if (today().equals(data.getString("players." + p.getUniqueId() + ".last"))) {
                it.remove();
                continue;
            }
            if (minutesLeft(p) == 0) {
                it.remove();
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                checkin(p);
            }
        }
    }

    /** Điểm danh thật (đã kiểm: hôm nay chưa điểm danh, đủ phút online). */
    private void checkin(Player p) {
        String key = "players." + p.getUniqueId();
        int count = data.getInt(key + ".count", 0);
        count++;
        data.set(key + ".name", p.getName());
        data.set(key + ".count", count);
        data.set(key + ".last", today());
        save();
        List<String> got = new ArrayList<>(rewards.give(p, cfg.getStringList("daily-rewards")));
        int day = cycleDay(count);
        List<String> mile = milestones().get(day);
        DailyQuests.feature(p, "diemdanh");
        p.sendMessage(c("&a✔ Điểm danh ngày thứ &f" + count + "&a!" + (got.isEmpty() ? "" : " Nhận: &f" + String.join("&7, &f", got))));
        ScreenNotify.show(p, "&e☀ Đã điểm danh", "&7Ngày thứ &f" + count);
        if (mile != null && !mile.isEmpty()) {
            List<String> extra = rewards.give(p, mile);
            p.sendMessage(c("&6★ Chạm mốc " + day + " ngày! Thưởng thêm: &f" + String.join("&7, &f", extra)));
            if (cfg.getBoolean("broadcast-milestone", true)) {
                Bukkit.broadcast(c("&6★ &f" + p.getName() + " &6đã điểm danh đủ mốc &f" + day + " ngày&6! Gõ &f/diemdanh &6để nhận quà mỗi ngày."));
            }
        }
        status(p, count);
    }

    private void status(Player p, int count) {
        TreeMap<Integer, List<String>> m = milestones();
        int day = count == 0 ? 0 : cycleDay(count);
        Map.Entry<Integer, List<String>> next = m.higherEntry(day);
        if (next == null && cfg.getInt("cycle-days", 30) > 0 && !m.isEmpty()) {
            int cycle = cfg.getInt("cycle-days", 30);
            next = Map.entry(m.firstKey() + cycle, m.firstEntry().getValue());
        }
        String nextText = next == null ? "" : " &7· mốc tiếp theo: &f" + (next.getKey() > cfg.getInt("cycle-days", 30) && cfg.getInt("cycle-days", 30) > 0
                ? (next.getKey() - cfg.getInt("cycle-days", 30)) : next.getKey()) + " ngày&7 (còn &f" + (next.getKey() - day) + "&7 lần điểm danh)";
        p.sendMessage(c("&7Đã điểm danh &f" + count + "&7 ngày" + nextText));
        ConfigurationSection o = cfg.getConfigurationSection("online-ticket");
        if (o != null) {   // nút đổi set luôn hiện; dòng phiếu online chỉ khi online-ticket bật (29/10 đã tắt: chỉ điểm danh ra phiếu)
            String set = o.getString("set", "chuyencan");
            String line = "&e☀ Đổi Phiếu Chuyên Cần lấy set ở Thợ Rèn ";
            if (o.getBoolean("enabled", true)) {
                String k = "players." + p.getUniqueId() + ".online";
                boolean td = today().equals(data.getString(k + ".day"));
                int got = td ? data.getInt(k + ".got") : 0;
                int min = td ? data.getInt(k + ".min") : 0;
                int max = o.getInt("max-per-day", 2);
                int every = Math.max(1, o.getInt("every-minutes", 60));
                line = "&e☀ Phiếu online hôm nay: &f" + got + "/" + max + (got < max ? " &7(phiếu tiếp sau &f"
                        + Math.max(1, every * (got + 1) - min) + " phút&7 hoạt động)" : "") + " ";
            }
            p.sendMessage(c(line)
                    .append(c("&6&n[Đổi set Chuyên Cần]").clickEvent(ClickEvent.runCommand("/thoren set " + set))
                            .hoverEvent(HoverEvent.showText(c("&7Đổi Phiếu Chuyên Cần lấy món set ở Thợ Rèn")))));
        }
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được diemdanh.yml: " + e.getMessage());
        }
    }
}
