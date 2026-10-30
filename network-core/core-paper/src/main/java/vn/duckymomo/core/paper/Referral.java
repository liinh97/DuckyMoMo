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
 * Chống tài khoản phụ: không tự giới thiệu, không bot; 31/10: không từng dùng chung IP nào (nhớ 10 IP gần nhất mỗi người), không cùng chủ
 * theo /taikhoanphu; mỗi mạng chỉ 1 người cho mỗi người rủ (các người được rủ không được chung IP với nhau); kiểm lại lúc trả quà (dính thì huỷ thưởng, ghi ref.<…>.chan); "đủ giờ" là giờ chơi thật (Activity, không tính treo máy).
 * Mỗi người mới chỉ nhập 1 lần.
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

    /** 31/10: giờ chơi THẬT (Activity, không tính treo máy) - trước tính cả AFK nên nick phụ treo 2 giờ là người rủ nhận quà. Tính theo tick. */
    private static long played(Player p) {
        Activity a = Activity.INSTANCE;
        return a != null ? (long) (a.totalHours(p.getUniqueId()) * 72000) : p.getStatistic(Statistic.PLAY_ONE_MINUTE);
    }

    /** Lịch sử IP (tối đa 10 gần nhất) + IP cũ một ô. */
    private java.util.Set<String> ips(String uuid) {
        java.util.Set<String> out = new java.util.HashSet<>(data.getStringList("ips." + uuid));
        String one = data.getString("ip." + uuid, "");
        if (!one.isEmpty()) {
            out.add(one);
        }
        return out;
    }

    /** 31/10 chống nick phụ: từng dùng chung IP nào (không chỉ lần vào gần nhất), hoặc cùng chủ theo /taikhoanphu. null = được. */
    private String altReason(String newUuid, String newName, String refUuid, String refName) {
        java.util.Set<String> a = ips(newUuid);
        a.retainAll(ips(refUuid));
        a.remove("");
        if (!a.isEmpty()) {
            return "từng dùng chung mạng";
        }
        String o1 = WarRules.ownerOf(newName), o2 = WarRules.ownerOf(refName);
        if (o1 != null && o2 != null && o1.equalsIgnoreCase(o2)) {
            return "cùng chủ (tài khoản phụ đã khai)";
        }
        // 31/10 chủ server: mỗi mạng chỉ tính 1 người cho mỗi người rủ (2 nick cùng mạng khác của người rủ không được tính 2 lần)
        ConfigurationSection refs = data.getConfigurationSection("ref");
        if (refs != null) {
            java.util.Set<String> mine = ips(newUuid);
            mine.remove("");
            long myT = refs.getLong(newUuid + ".t", Long.MAX_VALUE);   // chưa nhập mã = mới nhất
            for (String other : refs.getKeys(false)) {
                if (other.equals(newUuid) || !refUuid.equals(refs.getString(other + ".by")) || refs.contains(other + ".chan")
                        || refs.getLong(other + ".t", 0) >= myT) {
                    continue;   // chỉ so với người nhập mã TRƯỚC (người đầu tiên của mạng được tính)
                }
                java.util.Set<String> o = ips(other);
                o.retainAll(mine);
                if (!o.isEmpty()) {
                    return "cùng mạng với " + refs.getString(other + ".name", "một người") + " (người này cũng do " + refName + " rủ; mỗi mạng tính 1 người)";
                }
            }
        }
        return null;
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
        List<String> hist = new ArrayList<>(data.getStringList("ips." + u));   // 31/10: nhớ 10 IP gần nhất (đổi mạng vẫn bắt được nick phụ)
        hist.remove(ip(p));
        hist.add(0, ip(p));
        data.set("ips." + u, hist.subList(0, Math.min(10, hist.size())));
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
            // kiểm lại lúc trả quà: hai bên có thể đã dùng chung mạng / khai tài khoản phụ sau khi nhập mã
            String why = by == null ? null : altReason(p.getUniqueId().toString(), p.getName(), by, data.getString(k + ".byName", ""));
            if (why != null) {
                data.set(k + ".chan", why);
                plugin.getLogger().info("Giới thiệu: huỷ thưởng " + data.getString(k + ".byName") + " <- " + p.getName() + " (" + why + ")");
                continue;
            }
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
        String why = altReason(p.getUniqueId().toString(), p.getName(), r.getUniqueId().toString(), r.getName());
        if (why != null) {
            p.sendMessage(Tip.c("&cBạn và &f" + r.getName() + "&c " + why + ": không tính giới thiệu (chống tài khoản phụ)."));
            plugin.getLogger().info("Giới thiệu: chặn " + p.getName() + " <- " + r.getName() + " (" + why + ")");
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
