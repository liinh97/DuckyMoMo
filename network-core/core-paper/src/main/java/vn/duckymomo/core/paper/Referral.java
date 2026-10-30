package vn.duckymomo.core.paper;

import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Giới thiệu bạn bè /gioithieu (29/10, chủ server: "bổ sung code để user giới thiệu nhau"). Mã giới thiệu = tên người chơi.
 * Người mới (vào lần đầu chưa quá window-days ngày) gõ /gioithieu <tên người giới thiệu>: nhận new-rewards ngay. Người giới thiệu nhận
 * referrer-rewards khi người mới chơi đủ need-hours giờ (thống kê thời gian chơi), mốc tổng số người (milestones) thưởng thêm.
 * Chống tài khoản phụ: không tự giới thiệu, không cùng IP với người giới thiệu (IP lần vào gần nhất), mỗi người mới chỉ nhập 1 lần.
 * Người giới thiệu offline lúc đủ giờ: thưởng giữ lại, phát khi vào game. Lưu plugins/DuckyMoMoCore/gioithieu.yml:
 * ref.<người mới>: {by, byName, name, t, ok}; ip.<uuid>; count.<uuid> (đã xác nhận); owed.<uuid> (chờ phát); name.<uuid>.
 */
final class Referral implements Listener {
    static volatile Referral INSTANCE;

    private final JavaPlugin plugin;
    private final Rewards rewards;
    private final ConfigurationSection cfg;
    private final File file;
    private final YamlConfiguration data;

    Referral(JavaPlugin plugin, ConfigurationSection cfg, Rewards rewards) {
        INSTANCE = this;
        this.plugin = plugin;
        this.cfg = cfg;
        this.rewards = rewards;
        this.file = new File(plugin.getDataFolder(), "gioithieu.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {   // mỗi phút: người mới nào đủ giờ chơi thì xác nhận
            if (INSTANCE != this) {
                task.cancel();
                return;
            }
            confirmTick();
        }, 1200L, 1200L);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được gioithieu.yml: " + e.getMessage());
        }
    }

    private static String ip(Player p) {
        return p.getAddress() == null ? "" : p.getAddress().getAddress().getHostAddress();
    }

    private long needTicks() {
        return (long) (cfg.getDouble("need-hours", 2) * 72000);
    }

    private static long played(Player p) {
        return p.getStatistic(Statistic.PLAY_ONE_MINUTE);   // tính theo tick
    }

    // ------------------------------------------------------------------ vào game

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (BotView.isBot(p)) {
            return;
        }
        String u = p.getUniqueId().toString();
        data.set("ip." + u, ip(p));
        data.set("name." + u, p.getName());
        boolean first = !p.hasPlayedBefore();
        int owed = data.getInt("owed." + u);
        if (owed > 0) {
            data.set("owed." + u, null);
        }
        save();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            for (int i = 0; i < owed; i++) {
                pay(p);
            }
            if (first) {
                p.sendMessage(Tip.c("&b✉ Được bạn rủ vào chơi? &7Gõ &f/gioithieu <tên người rủ>&7 trong " + cfg.getInt("window-days", 3)
                        + " ngày đầu để cả hai cùng nhận quà."));
            }
        }, first ? 160L : 60L);
    }

    // ------------------------------------------------------------------ xác nhận + thưởng

    private void confirmTick() {
        long need = needTicks();
        boolean dirty = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            String k = "ref." + p.getUniqueId();
            if (!data.contains(k) || data.getBoolean(k + ".ok") || played(p) < need) {
                continue;
            }
            data.set(k + ".ok", true);
            dirty = true;
            String by = data.getString(k + ".by");
            Player r = by == null ? null : Bukkit.getPlayer(UUID.fromString(by));
            if (r != null) {
                pay(r);
            } else if (by != null) {
                data.set("owed." + by, data.getInt("owed." + by) + 1);
            }
            p.sendMessage(Tip.c("&b✉ Bạn đã chơi đủ " + trim(cfg.getDouble("need-hours", 2)) + " giờ: người rủ bạn ("
                    + data.getString(k + ".byName", "?") + ") đã nhận quà giới thiệu."));
        }
        if (dirty) {
            save();
        }
    }

    /** Phát thưởng cho người giới thiệu: một người mới vừa được xác nhận. */
    private void pay(Player r) {
        String u = r.getUniqueId().toString();
        int n = data.getInt("count." + u) + 1;
        data.set("count." + u, n);
        save();
        List<String> got = new ArrayList<>(rewards.give(r, cfg.getStringList("referrer-rewards")));
        r.playSound(r.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        r.sendMessage(Tip.c("&b✉ Một người bạn rủ đã chơi đủ giờ! &7Nhận: &e" + String.join("&7, &e", got) + " &7(đã rủ " + n + " người)"));
        List<String> mile = cfg.getStringList("milestones." + n);
        if (!mile.isEmpty()) {
            List<String> extra = rewards.give(r, mile);
            r.sendMessage(Tip.c("&6★ Mốc " + n + " người được rủ! &7Thêm: &e" + String.join("&7, &e", extra)));
            Bukkit.broadcast(Tip.c("&6★ &f" + r.getName() + " &6đã rủ được &f" + n + " người&6 vào làng! &7(/gioithieu)"));
        }
    }

    private static String trim(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    // ------------------------------------------------------------------ /gioithieu

    boolean command(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Chỉ người chơi.");
            return true;
        }
        if (a.length == 0) {
            info(p);
            return true;
        }
        if (a[0].equalsIgnoreCase("top")) {
            top(p);
            return true;
        }
        enter(p, a[0]);
        return true;
    }

    private void info(Player p) {
        String u = p.getUniqueId().toString();
        int ok = data.getInt("count." + u);
        int wait = 0;
        ConfigurationSection refs = data.getConfigurationSection("ref");
        if (refs != null) {
            for (String k : refs.getKeys(false)) {
                if (u.equals(refs.getString(k + ".by")) && !refs.getBoolean(k + ".ok")) {
                    wait++;
                }
            }
        }
        p.sendMessage(Tip.c("&b✉ Giới thiệu bạn bè"));
        p.sendMessage(Tip.c("&7Mã của bạn: &f" + p.getName() + " &7· bạn mới vào gõ &f/gioithieu " + p.getName())
                .clickEvent(ClickEvent.copyToClipboard("/gioithieu " + p.getName()))
                .hoverEvent(HoverEvent.showText(Tip.c("&7Bấm để chép lời mời"))));
        p.sendMessage(Tip.c("&7Bạn mới nhận ngay: &e" + String.join("&7, &e", rewards.describe(cfg.getStringList("new-rewards")))));
        p.sendMessage(Tip.c("&7Bạn nhận khi họ chơi đủ " + trim(cfg.getDouble("need-hours", 2)) + " giờ: &e"
                + String.join("&7, &e", rewards.describe(cfg.getStringList("referrer-rewards")))));
        ConfigurationSection m = cfg.getConfigurationSection("milestones");
        if (m != null) {
            TreeMap<Integer, String> ms = new TreeMap<>();
            for (String k : m.getKeys(false)) {
                ms.put(Integer.parseInt(k), String.join(", ", rewards.describe(m.getStringList(k))));
            }
            ms.forEach((n, t) -> p.sendMessage(Tip.c("&7• Mốc &f" + n + " người&7: &e" + t)));
        }
        p.sendMessage(Tip.c("&7Đã rủ: &a" + ok + " người" + (wait > 0 ? " &7· đang chờ đủ giờ: &f" + wait : "") + " &7(/gioithieu top)"));
        String k = "ref." + u;
        if (data.contains(k)) {
            p.sendMessage(Tip.c("&7Bạn được " + data.getString(k + ".byName") + " giới thiệu."));
        } else if (canEnter(p)) {
            p.sendMessage(Tip.c("&7Bạn mới vào? Gõ &7/gioithieu <tên người rủ>&7 để nhận quà."));
        }
    }

    private boolean canEnter(Player p) {
        long days = (System.currentTimeMillis() - p.getFirstPlayed()) / 86_400_000L;
        return days < cfg.getInt("window-days", 3) && !data.contains("ref." + p.getUniqueId());
    }

    private void enter(Player p, String name) {
        String k = "ref." + p.getUniqueId();
        if (data.contains(k)) {
            p.sendMessage(Tip.c("&cBạn đã nhập mã của &f" + data.getString(k + ".byName") + "&c rồi (mỗi người một lần)."));
            return;
        }
        if (!canEnter(p)) {
            p.sendMessage(Tip.c("&cChỉ người mới trong " + cfg.getInt("window-days", 3) + " ngày đầu mới nhập mã giới thiệu được. &7Mã của bạn: &f/gioithieu"));
            return;
        }
        OfflinePlayer r = Bukkit.getOfflinePlayerIfCached(name);
        if (r == null || !r.hasPlayedBefore() && !r.isOnline()) {
            p.sendMessage(Tip.c("&cKhông có người chơi tên &f" + name + "&c. &7Nhập đúng tên trong game của người rủ bạn."));
            return;
        }
        if (r.getUniqueId().equals(p.getUniqueId())) {
            p.sendMessage(Tip.c("&cKhông tự giới thiệu mình được."));
            return;
        }
        if (r.isOnline() && r.getPlayer() != null && BotView.isBot(r.getPlayer())) {
            p.sendMessage(Tip.c("&cNgười này không nhận giới thiệu."));
            return;
        }
        String rip = data.getString("ip." + r.getUniqueId(), "");
        if (!rip.isEmpty() && rip.equals(ip(p))) {
            p.sendMessage(Tip.c("&cBạn và &f" + r.getName() + "&c dùng chung mạng: không tính giới thiệu (chống tài khoản phụ)."));
            return;
        }
        data.set(k + ".by", r.getUniqueId().toString());
        data.set(k + ".byName", r.getName());
        data.set(k + ".name", p.getName());
        data.set(k + ".t", System.currentTimeMillis());
        save();
        List<String> got = rewards.give(p, cfg.getStringList("new-rewards"));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        p.sendMessage(Tip.c("&a✔ Đã ghi nhận &f" + r.getName() + "&a rủ bạn vào! &7Nhận: &e" + String.join("&7, &e", got)));
        p.sendMessage(Tip.c("&7Chơi đủ " + trim(cfg.getDouble("need-hours", 2)) + " giờ thì " + r.getName() + " cũng nhận quà."));
        Player ro = r.getPlayer();
        if (ro != null) {
            ro.sendMessage(Tip.c("&b✉ &f" + p.getName() + "&7 vừa nhập mã giới thiệu của bạn. Họ chơi đủ "
                    + trim(cfg.getDouble("need-hours", 2)) + " giờ là bạn nhận quà."));
        }
    }

    private void top(Player p) {
        ConfigurationSection c = data.getConfigurationSection("count");
        List<Map.Entry<String, Integer>> l = new ArrayList<>();
        if (c != null) {
            for (String k : c.getKeys(false)) {
                l.add(Map.entry(k, c.getInt(k)));
            }
        }
        l.sort((x, y) -> y.getValue() - x.getValue());
        p.sendMessage(Tip.c("&b✉ Rủ được nhiều bạn nhất"));
        if (l.isEmpty()) {
            p.sendMessage(Tip.c("&7Chưa có ai. Rủ bạn vào chơi: &f/gioithieu"));
        }
        for (int i = 0; i < Math.min(10, l.size()); i++) {
            p.sendMessage(Tip.c("&e" + (i + 1) + ". &f" + data.getString("name." + l.get(i).getKey(), "?") + " &7· " + l.get(i).getValue() + " người"));
        }
    }
}
