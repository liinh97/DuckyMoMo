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

    private final JavaPlugin plugin;
    private final int perDay;
    /** IP miễn giới hạn (register-limit.whitelist): đúng IP, hoặc tiền tố kết thúc bằng "." / ":" (vd "171.255.251." = cả dải). */
    private final java.util.List<String> whitelist;
    private final File file;
    private final YamlConfiguration data;

    RegisterLimit(JavaPlugin plugin, ConfigurationSection cfg) {
        this.plugin = plugin;
        this.perDay = cfg == null ? 2 : cfg.getInt("per-ip-per-day", 2);
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
        if (am == null || !am.isEnabled() || perDay <= 0) {
            return false;
        }
        try {
            ClassLoader cl = am.getClass().getClassLoader();
            Class<? extends Event> pre = (Class<? extends Event>) Class.forName("fr.xephi.authme.events.AuthMeAsyncPreRegisterEvent", true, cl);
            Class<? extends Event> done = (Class<? extends Event>) Class.forName("fr.xephi.authme.events.RegisterEvent", true, cl);
            Method prePlayer = pre.getMethod("getPlayer");
            Method setCan = pre.getMethod("setCanRegister", boolean.class);
            Method donePlayer = done.getMethod("getPlayer");
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
                    int used = recent(ip).size();
                    if (used >= perDay) {
                        setCan.invoke(ev, false);
                        p.sendMessage("§cIP của bạn đã đăng ký " + used + " tài khoản trong 24 giờ qua (tối đa " + perDay
                                + "). Thử lại sau, hoặc nhờ admin nếu bạn chơi ở quán net/mạng chung.");
                        plugin.getLogger().info("Chặn đăng ký: " + p.getName() + " từ " + ip + " (" + used + "/" + perDay + " trong 24 giờ)");
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

    private synchronized List<String> recent(String ip) {
        long now = System.currentTimeMillis();
        List<String> keep = new ArrayList<>();
        for (String s : data.getStringList(key(ip))) {
            int i = s.indexOf(' ');
            try {
                if (i > 0 && now - Long.parseLong(s.substring(0, i)) < DAY) {
                    keep.add(s);
                }
            } catch (NumberFormatException ignored) {
                // dòng hỏng: bỏ
            }
        }
        return keep;
    }

    private synchronized void record(String ip, String name) {
        List<String> list = recent(ip);
        list.add(System.currentTimeMillis() + " " + name);
        data.set(key(ip), list);
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được dangky-ip.yml: " + e.getMessage());
        }
    }
}
