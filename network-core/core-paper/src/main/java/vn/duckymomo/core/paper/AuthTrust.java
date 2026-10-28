package vn.duckymomo.core.paper;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Giữ đăng nhập lâu hơn cho tài khoản TIN CẬY (28/10, chủ server: "chỉ trong whitelist thì mới được thế thôi; còn mặc định vẫn login
 * lại sau 60 phút"). AuthMe chỉ có MỘT thời gian phiên chung (sessions.timeout = 60 phút) nên không đổi nó. Người có tên trong
 * auth-trust.players vào sảnh mà AuthMe đòi mật khẩu: nếu CÙNG IP với lần đăng nhập trước và lần đó chưa quá auth-trust.hours giờ
 * thì đăng nhập hộ (AuthMeApi.forceLogin). Khác IP / quá hạn / không có tên: nhập mật khẩu như thường.
 * Gọi AuthMe bằng reflection (server không có AuthMe vẫn bật được). Config: auth-trust {enabled, hours, players[]}.
 */
final class AuthTrust implements Listener {
    private final JavaPlugin plugin;
    private final Set<String> names = new HashSet<>();
    private final long hours;

    AuthTrust(JavaPlugin plugin, ConfigurationSection cfg) {
        this.plugin = plugin;
        this.hours = Math.max(1, cfg.getLong("hours", 24));
        for (String n : cfg.getStringList("players")) {
            names.add(n.trim().toLowerCase(Locale.ROOT));
        }
    }

    boolean ready() {
        return !names.isEmpty() && Bukkit.getPluginManager().isPluginEnabled("AuthMe");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!names.contains(p.getName().toLowerCase(Locale.ROOT))) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> check(p), 20L);   // đợi AuthMe xử lý vào game xong
    }

    private void check(Player p) {
        if (!p.isOnline()) {
            return;
        }
        try {
            Class<?> c = Class.forName("fr.xephi.authme.api.v3.AuthMeApi");
            Object api = c.getMethod("getInstance").invoke(null);
            if ((boolean) c.getMethod("isAuthenticated", Player.class).invoke(api, p)) {
                return;
            }
            String lastIp = (String) c.getMethod("getLastIp", String.class).invoke(api, p.getName());
            Instant last = (Instant) c.getMethod("getLastLoginTime", String.class).invoke(api, p.getName());
            InetSocketAddress addr = p.getAddress();
            String ip = addr == null || addr.getAddress() == null ? null : addr.getAddress().getHostAddress();
            if (ip == null || lastIp == null || last == null || !ip.equals(lastIp)) {
                return;
            }
            if (Duration.between(last, Instant.now()).toHours() >= hours) {
                return;
            }
            Method force = c.getMethod("forceLogin", Player.class);
            force.invoke(api, p);
            plugin.getLogger().info("Tài khoản tin cậy " + p.getName() + ": tự đăng nhập (cùng IP, lần trước chưa quá " + hours + " giờ)");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("Tài khoản tin cậy: không gọi được AuthMe: " + ex);
        }
    }
}
