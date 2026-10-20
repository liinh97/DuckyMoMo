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
        if (!cfg.getBoolean("remind-on-join", true)) {
            return;
        }
        if (!today().equals(data.getString("players." + p.getUniqueId() + ".last"))) {
            int mins = cfg.getInt("min-online-minutes", 5);
            JoinNotes.add(p, 30, c("&e📅 Hôm nay bạn chưa điểm danh. ").append(c("&a&n[Bấm để điểm danh]")
                    .clickEvent(ClickEvent.runCommand("/diemdanh"))
                    .hoverEvent(HoverEvent.showText(c("&7/diemdanh" + (mins > 0 ? " (cần online đủ " + mins + " phút)" : ""))))));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        joined.remove(e.getPlayer().getUniqueId());
    }

    boolean command(CommandSender s, String[] args) {
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
        int need = cfg.getInt("min-online-minutes", 5);
        long online = (System.currentTimeMillis() - joined.getOrDefault(p.getUniqueId(), System.currentTimeMillis())) / 60000;
        if (online < need) {
            p.sendMessage(c("&eCần online thêm &f" + (need - online) + " phút&e nữa mới điểm danh được (chống tài khoản phụ)."));
            return true;
        }
        count++;
        data.set(key + ".name", p.getName());
        data.set(key + ".count", count);
        data.set(key + ".last", today());
        save();
        List<String> got = new ArrayList<>(rewards.give(p, cfg.getStringList("daily-rewards")));
        int day = cycleDay(count);
        List<String> mile = milestones().get(day);
        p.sendMessage(c("&a✔ Điểm danh ngày thứ &f" + count + "&a!" + (got.isEmpty() ? "" : " Nhận: &f" + String.join("&7, &f", got))));
        if (mile != null && !mile.isEmpty()) {
            List<String> extra = rewards.give(p, mile);
            p.sendMessage(c("&6★ Chạm mốc " + day + " ngày! Thưởng thêm: &f" + String.join("&7, &f", extra)));
            if (cfg.getBoolean("broadcast-milestone", true)) {
                Bukkit.broadcast(c("&6★ &f" + p.getName() + " &6đã điểm danh đủ mốc &f" + day + " ngày&6! Gõ &f/diemdanh &6để nhận quà mỗi ngày."));
            }
        }
        status(p, count);
        return true;
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
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được diemdanh.yml: " + e.getMessage());
        }
    }
}
