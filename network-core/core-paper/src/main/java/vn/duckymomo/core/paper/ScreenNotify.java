package vn.duckymomo.core.paper;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Chữ giữa màn hình (30/10, chủ server: "thông báo cả ở chat lẫn giữa màn hình, giữa màn hình ngắn gọn xúc tích").
 * Chat giữ câu đầy đủ, giữa màn hình chỉ 1 chữ to (2-4 từ) + 1 dòng phụ. Người chơi tắt ở /caidat "Chữ giữa màn hình" (chugiua).
 * Gọi: ScreenNotify.show(p, "&6Tới nơi!", "&7x=1 y=2 z=3"). Tự hiện khi dịch chuyển xa (lệnh / plugin) tới world chính hoặc spawn.
 */
final class ScreenNotify implements Listener {
    private static final Title.Times TIMES = Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1800), Duration.ofMillis(450));
    private final JavaPlugin plugin;
    private final Map<UUID, Long> joined = new HashMap<>();
    private final Map<UUID, Long> lastTp = new HashMap<>();

    private static JavaPlugin PLUGIN;
    private static final Map<UUID, org.bukkit.scheduler.BukkitTask> COUNTDOWN = new HashMap<>();

    ScreenNotify(JavaPlugin plugin) {
        this.plugin = plugin;
        PLUGIN = plugin;
    }

    /** 30/10: đếm ngược dịch chuyển giữa màn hình "Đứng yên / còn N giây". Dừng khi người chơi di chuyển (Essentials / tpr tự huỷ). */
    static void countdown(Player p, int secs) {
        if (PLUGIN == null || secs <= 0 || p == null || BotView.isBot(p) || !PlayerSettings.on(p, "chugiua")) {
            return;
        }
        var old = COUNTDOWN.remove(p.getUniqueId());
        if (old != null) {
            old.cancel();
        }
        Location start = p.getLocation();
        int[] left = {secs};
        COUNTDOWN.put(p.getUniqueId(), Bukkit.getScheduler().runTaskTimer(PLUGIN, () -> {
            boolean moved = !p.isOnline() || p.getWorld() != start.getWorld() || p.getLocation().distanceSquared(start) > 0.6 * 0.6;
            if (moved || left[0] <= 0) {
                var t = COUNTDOWN.remove(p.getUniqueId());
                if (t != null) {
                    t.cancel();
                }
                return;
            }
            p.showTitle(Title.title(Tip.c("&eĐứng yên"), Tip.c("&fDịch chuyển sau &a" + left[0] + " &fgiây"),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(200))));
            left[0]--;
        }, 0L, 20L));
    }

    static void show(Player p, String big, String small) {
        if (p == null || !p.isOnline() || BotView.isBot(p) || !PlayerSettings.on(p, "chugiua")) {
            return;
        }
        p.showTitle(Title.title(Tip.c(big), Tip.c(small == null ? "" : small), TIMES));
    }

    /** Báo cả server một việc người chơi vừa làm để người khác làm theo, kèm nút lệnh bấm được (30/10). Ai tắt "Thông báo sự kiện"
     *  (/caidat sukien) thì không thấy. msg dùng mã màu &, cmd dạng "/vote". */
    static void invite(String msg, String cmd) {
        PlayerSettings.broadcast("sukien", Tip.c("&d[DuckyMoMo] &r" + msg + " ").append(Tip.c("&b[" + cmd + "]")
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(cmd))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Tip.c("&7Bấm để gõ &f" + cmd)))));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        joined.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    /** Dịch chuyển xa bằng lệnh / plugin (home, warp, tpa, tpr, spawn…) tới world chính hoặc world spawn: "Tới nơi!" + toạ độ. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        var cause = e.getCause();
        if (cause != PlayerTeleportEvent.TeleportCause.COMMAND && cause != PlayerTeleportEvent.TeleportCause.PLUGIN) {
            return;
        }
        Player p = e.getPlayer();
        Location from = e.getFrom(), to = e.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }
        String w = to.getWorld().getName();
        if (!(to.getWorld().equals(Bukkit.getWorlds().get(0)) || w.equals("spawn"))) {
            return;   // dungeon, đấu trường… có chữ riêng của chúng
        }
        boolean far = from.getWorld() != to.getWorld() || from.distanceSquared(to) >= 32 * 32;
        long now = System.currentTimeMillis();
        if (!far || now - joined.getOrDefault(p.getUniqueId(), 0L) < 8000 || now - lastTp.getOrDefault(p.getUniqueId(), 0L) < 3000) {
            return;
        }
        lastTp.put(p.getUniqueId(), now);
        String where = w.equals("spawn") ? "&fKhu spawn" : "&fx=" + to.getBlockX() + " y=" + to.getBlockY() + " z=" + to.getBlockZ();
        Bukkit.getScheduler().runTaskLater(plugin, () -> show(p, "&6Tới nơi!", where), 2L);
    }
}
