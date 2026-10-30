package vn.duckymomo.core.paper;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

/**
 * Thời gian chờ dịch chuyển theo rank (22/10, chủ server: "mặc định dân là 5s, donate là 3s"). Mỗi bậc danh phận / rank khác có tp-delay
 * (play-ranks.tiers.<mã>.tp-delay, play-ranks.other-ranks.<mã>.tp-delay; Cài đặt › Người mới); PlayRanks đồng bộ thành quyền
 * duckymomo.tpdelay.<giây>. Người nhiều rank lấy SỐ NHỎ NHẤT. Không có quyền nào thì giữ thời gian chờ gốc.
 * Áp dụng: Essentials /home /spawn /warp /back /tpa /tpahere (đổi delay của TeleportWarmupEvent, gọi qua reflection: không cần thư viện
 * Essentials lúc build) và /tpr (RandomTeleport). Essentials chỉ bắn sự kiện khi teleport-delay gốc > 0 và người chơi không có
 * essentials.teleport.timer.bypass, nên teleport-delay gốc phải > 0 (Cài đặt › "Đứng yên bao lâu trước khi đi").
 */
final class TeleportDelay {

    private TeleportDelay() {
    }

    /** Số giây chờ của người chơi: nhỏ nhất trong các quyền duckymomo.tpdelay.<N>, không có thì fallback. */
    static int seconds(Player p, int fallback) {
        for (int n = 0; n <= 30; n++) {
            if (p.isPermissionSet("duckymomo.tpdelay." + n) && p.hasPermission("duckymomo.tpdelay." + n)) {
                return n;
            }
        }
        return fallback;
    }

    /** Gắn vào sự kiện đếm ngược của Essentials. */
    @SuppressWarnings("unchecked")
    static void hookEssentials(JavaPlugin plugin) {
        Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
        if (ess == null || !ess.isEnabled()) {
            return;
        }
        try {
            Class<? extends Event> ev = (Class<? extends Event>) Class.forName("net.ess3.api.events.teleport.TeleportWarmupEvent", true,
                    ess.getClass().getClassLoader());
            Method teleportee = ev.getMethod("getTeleportee");
            Method getDelay = ev.getMethod("getDelay");
            Method setDelay = ev.getMethod("setDelay", double.class);
            Method getTarget = ev.getMethod("getTarget");
            Method targetLoc = Class.forName("com.earth2me.essentials.ITarget", true, ess.getClass().getClassLoader()).getMethod("getLocation");
            Bukkit.getPluginManager().registerEvent(ev, new Listener() { }, EventPriority.NORMAL, (l, e) -> {
                if (!ev.isInstance(e)) {
                    return;
                }
                try {
                    Object user = teleportee.invoke(e);
                    if (user != null && user.getClass().getMethod("getBase").invoke(user) instanceof Player p) {
                        double cur = (double) getDelay.invoke(e);
                        int secs = seconds(p, (int) Math.round(cur));
                        // 31/10: dân hoà bình về kinh đô (vòng spawn / đất KinhDo) chờ ít hơn (peace-alliance.capital-tp-delay)
                        if (Bukkit.getPluginManager().isPluginEnabled("Towny")) {
                            Object tg = getTarget.invoke(e);
                            Object loc = tg == null ? null : targetLoc.invoke(tg);   // qua interface: lớp thật có thể không public
                            int fast = loc instanceof org.bukkit.Location dest ? PeaceAlliance.capitalDelay(p, dest) : -1;
                            if (fast >= 0 && fast < secs) {
                                secs = fast;
                            }
                        }
                        setDelay.invoke(e, (double) secs);
                        ScreenNotify.countdown(p, secs);   // 30/10: đếm ngược giữa màn hình
                    }
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    plugin.getLogger().warning("Thời gian chờ dịch chuyển theo rank: lỗi đọc sự kiện Essentials (" + ex + ")");
                }
            }, plugin, true);
            plugin.getLogger().info("Thời gian chờ dịch chuyển theo rank: bật (Essentials)");
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().warning("Thời gian chờ dịch chuyển theo rank: Essentials không có TeleportWarmupEvent (" + ex + ")");
        }
    }
}
