package vn.duckymomo.core.paper;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tab gọn (30/10, chủ server: "cứ hiện như ban đầu nhưng tầm 20 người thôi là gọn"). Mỗi người chỉ thấy tối đa max người trong
 * danh sách Tab: chính mình + người chơi thật luôn hiện, bot lấp chỗ còn lại (thứ tự tên, ổn định). Người bị ẩn khỏi Tab vẫn hiện
 * bình thường trong thế giới (Paper unlistPlayer chỉ bỏ khỏi danh sách). Số online ở chân Tab vẫn là số thật.
 * Config tab-limit: {enabled, max}
 */
final class TabLimit implements Listener {
    private final JavaPlugin plugin;
    private final int max;
    private BukkitTask task;

    TabLimit(JavaPlugin plugin, int max) {
        this.plugin = plugin;
        this.max = Math.max(5, max);
    }

    void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::apply, 40L, 100L);
    }

    void stop() {
        if (task != null) {
            task.cancel();
        }
        for (Player v : Bukkit.getOnlinePlayers()) {
            for (Player t : Bukkit.getOnlinePlayers()) {
                if (!v.isListed(t)) {
                    v.listPlayer(t);
                }
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, this::apply, 5L);
    }

    private void apply() {
        List<Player> all = new ArrayList<>(Bukkit.getOnlinePlayers());
        // người thật trước, rồi bot; trong mỗi nhóm theo tên cho ổn định
        all.sort(Comparator.comparing((Player p) -> BotView.isBot(p)).thenComparing(p -> p.getName().toLowerCase()));
        for (Player viewer : all) {
            if (BotView.isBot(viewer)) {
                continue;   // bot không nhìn Tab
            }
            Set<Player> show = new HashSet<>();
            show.add(viewer);
            for (Player t : all) {
                if (show.size() >= max) {
                    break;
                }
                show.add(t);
            }
            for (Player t : all) {
                if (t == viewer) {
                    continue;
                }
                boolean want = show.contains(t) || !BotView.isBot(t);   // người thật luôn hiện, kể cả khi quá max
                if (want && !viewer.isListed(t)) {
                    viewer.listPlayer(t);
                } else if (!want && viewer.isListed(t)) {
                    viewer.unlistPlayer(t);
                }
            }
        }
    }
}
