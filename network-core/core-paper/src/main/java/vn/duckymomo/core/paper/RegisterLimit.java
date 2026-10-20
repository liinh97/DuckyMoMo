package vn.duckymomo.core.paper;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Giới hạn số tài khoản đăng ký mỗi IP trong 24 giờ (luật mùa 1, mục 37: tối đa 2).
 * AuthMe chỉ có maxRegPerIp tính TỔNG vĩnh viễn: ở Việt Nam 4G và quán net dùng chung IP, chặn vĩnh viễn sẽ khoá cả quán.
 * IP loopback (playit: 127.x) và mạng nội bộ không tính: không phải IP thật của người chơi.
 * Sự kiện AuthMe gọi bằng reflection (không cần AuthMe lúc build). Lưu: plugins/DuckyMoMoCore/dangky-ip.yml
 */
final class RegisterLimit implements Listener {
    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final long MONTH = 30 * DAY;

    private final JavaPlugin plugin;
    private final int perDay;
    /** Tối đa N tài khoản mỗi IP trong 30 ngày (register-limit.per-ip-per-30-days, 0 = tắt). */
    private final int perMonth;
    /** TỔNG tối đa N tài khoản mỗi IP, mãi mãi (register-limit.per-ip-total, 0 = tắt; chủ server 20/10: "tối đa chỉ 2 tài khoản").
     *  Đếm = tài khoản AuthMe đang gắn IP đó (AuthMeApi.getNamesByIp, có cả tài khoản tạo trước khi có tính năng này)
     *  + mọi lần đăng ký từ IP đó server đã ghi (dangky-ip.yml, không xoá theo thời gian). */
    private final int perTotal;
    /** IP miễn giới hạn (register-limit.whitelist): đúng IP, hoặc tiền tố kết thúc bằng "." / ":" (vd "171.255.251." = cả dải). */
    private final java.util.List<String> whitelist;
    private final File file;
    private final YamlConfiguration data;

    RegisterLimit(JavaPlugin plugin, ConfigurationSection cfg) {
        this.plugin = plugin;
        this.perDay = cfg == null ? 2 : cfg.getInt("per-ip-per-day", 2);
        this.perMonth = cfg == null ? 0 : cfg.getInt("per-ip-per-30-days", 0);
        this.perTotal = cfg == null ? 2 : cfg.getInt("per-ip-total", 2);
        this.whitelist = cfg == null ? java.util.List.of()
                : cfg.getStringList("whitelist").stream().map(String::trim).filter(x -> !x.isEmpty()).toList();
        this.file = new File(plugin.getDataFolder(), "dangky-ip.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    boolean whitelisted(String ip) {
        for (String w : whitelist) {
            if (w.endsWith(".") || w.endsWith(":") ? ip.startsWith(w) : ip.equals(w)) {
                return true;
            }
        }
        return false;
    }

    /** Trả false nếu không có AuthMe trên server này. */
    @SuppressWarnings("unchecked")
    boolean hook() {
        Plugin am = Bukkit.getPluginManager().getPlugin("AuthMe");
        if (am == null || !am.isEnabled() || perDay <= 0 && perMonth <= 0 && perTotal <= 0) {
            return false;
        }
        try {
            ClassLoader cl = am.getClass().getClassLoader();
            Class<? extends Event> pre = (Class<? extends Event>) Class.forName("fr.xephi.authme.events.AuthMeAsyncPreRegisterEvent", true, cl);
            Class<? extends Event> done = (Class<? extends Event>) Class.forName("fr.xephi.authme.events.RegisterEvent", true, cl);
            Method prePlayer = pre.getMethod("getPlayer");
            Method setCan = pre.getMethod("setCanRegister", boolean.class);
            Method donePlayer = done.getMethod("getPlayer");
            Class<?> apiClass = Class.forName("fr.xephi.authme.api.v3.AuthMeApi", true, cl);
            Method apiInstance = apiClass.getMethod("getInstance");
            Method namesByIp = apiClass.getMethod("getNamesByIp", String.class);
            Bukkit.getPluginManager().registerEvent(pre, this, EventPriority.HIGH, (l, ev) -> {
                if (!pre.isInstance(ev)) {
                    return;
                }
                try {
                    Player p = (Player) prePlayer.invoke(ev);
                    String ip = ipOf(p);
                    if (ip == null || whitelisted(ip)) {
                        return;
                    }
                    int used = recent(ip, DAY).size();
                    int usedMonth = recent(ip, MONTH).size();
                    java.util.Set<String> all = new java.util.HashSet<>();
                    for (String s : recent(ip, Long.MAX_VALUE)) {
                        all.add(s.substring(s.indexOf(' ') + 1).toLowerCase(java.util.Locale.ROOT));
                    }
                    try {
                        Object api = apiInstance.invoke(null);
                        if (api != null) {
                            for (Object n : (List<?>) namesByIp.invoke(api, ip)) {
                                all.add(String.valueOf(n).toLowerCase(java.util.Locale.ROOT));
                            }
                        }
                    } catch (ReflectiveOperationException e) {
                        plugin.getLogger().warning("Giới hạn đăng ký: không hỏi được AuthMe theo IP: " + e);
                    }
                    all.remove(p.getName().toLowerCase(java.util.Locale.ROOT));
                    if (perTotal > 0 && all.size() >= perTotal) {
                        setCan.invoke(ev, false);
                        p.sendMessage("§cIP của bạn đã có " + all.size() + " tài khoản (mỗi mạng tối đa " + perTotal
                                + "). Hãy đăng nhập tài khoản cũ, hoặc nhờ admin nếu bạn chơi ở quán net/mạng chung.");
                        plugin.getLogger().info("Chặn đăng ký: " + p.getName() + " từ " + ip + " (đã có " + all.size() + "/" + perTotal + " tài khoản: " + all + ")");
                    } else if (perDay > 0 && used >= perDay) {
                        setCan.invoke(ev, false);
                        p.sendMessage("§cIP của bạn đã đăng ký " + used + " tài khoản trong 24 giờ qua (tối đa " + perDay
                                + "). Thử lại sau, hoặc nhờ admin nếu bạn chơi ở quán net/mạng chung.");
                        plugin.getLogger().info("Chặn đăng ký: " + p.getName() + " từ " + ip + " (" + used + "/" + perDay + " trong 24 giờ)");
                    } else if (perMonth > 0 && usedMonth >= perMonth) {
                        setCan.invoke(ev, false);
                        p.sendMessage("§cIP của bạn đã đăng ký " + usedMonth + " tài khoản trong 30 ngày qua (tối đa " + perMonth
                                + "). Nhờ admin nếu bạn chơi ở quán net/mạng chung.");
                        plugin.getLogger().info("Chặn đăng ký: " + p.getName() + " từ " + ip + " (" + usedMonth + "/" + perMonth + " trong 30 ngày)");
                    }
                } catch (ReflectiveOperationException e) {
                    plugin.getLogger().warning("Giới hạn đăng ký: " + e);
                }
            }, plugin);
            Bukkit.getPluginManager().registerEvent(done, this, EventPriority.MONITOR, (l, ev) -> {
                if (!done.isInstance(ev)) {
                    return;
                }
                try {
                    Player p = (Player) donePlayer.invoke(ev);
                    String ip = ipOf(p);
                    if (ip != null) {
                        record(ip, p.getName());
                    }
                } catch (ReflectiveOperationException e) {
                    plugin.getLogger().warning("Giới hạn đăng ký: " + e);
                }
            }, plugin);
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            plugin.getLogger().warning("Không gắn được giới hạn đăng ký vào AuthMe: " + e);
            return false;
        }
    }

    /** IP thật của người chơi, null nếu là loopback/nội bộ (không tính). */
    private static String ipOf(Player p) {
        if (p.getAddress() == null) {
            return null;
        }
        InetAddress a = p.getAddress().getAddress();
        if (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress()) {
            return null;
        }
        return a.getHostAddress();
    }

    private static String key(String ip) {
        return "ip." + ip.replace('.', '_').replace(':', '-');
    }

    private synchronized List<String> recent(String ip, long window) {
        long now = System.currentTimeMillis();
        List<String> keep = new ArrayList<>();
        for (String s : data.getStringList(key(ip))) {
            int i = s.indexOf(' ');
            try {
                if (i > 0 && (window == Long.MAX_VALUE || now - Long.parseLong(s.substring(0, i)) < window)) {
                    keep.add(s);
                }
            } catch (NumberFormatException ignored) {
                // dòng hỏng: bỏ
            }
        }
        return keep;
    }

    private synchronized void record(String ip, String name) {
        List<String> list = recent(ip, Long.MAX_VALUE);   // giữ mãi để đếm tổng số tài khoản mỗi IP
        list.add(System.currentTimeMillis() + " " + name);
        data.set(key(ip), list);
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được dangky-ip.yml: " + e.getMessage());
        }
    }
}
