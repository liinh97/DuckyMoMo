package vn.duckymomo.core.paper;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Ngủ bỏ qua đêm (30/10, chủ server gửi ảnh "Đang ngủ để bỏ qua đêm [1/4] người…"). Trước đó gamerule players_sleeping_percentage = 100
 * và ~20 bot không bao giờ ngủ nên KHÔNG ai qua đêm được. Giờ:
 *  - bot (BotView.isBot) được bỏ khỏi phép đếm (setSleepingIgnored, đặt lại mỗi 5 giây vì Essentials đổi cờ này khi hết AFK);
 *  - đặt gamerule players_sleeping_percentage = sleep.percent cho mọi world;
 *  - ai lên giường: báo cả world "Đang ngủ để bỏ qua đêm [x/y] người…", đủ người thì "Trời sắp sáng!" + chữ giữa màn hình.
 * Config: sleep {enabled, percent}
 */
final class SleepVote implements Listener {
    private final JavaPlugin plugin;
    private final int percent;
    private BukkitTask task;

    SleepVote(JavaPlugin plugin, ConfigurationSection cfg) {
        this.plugin = plugin;
        this.percent = Math.max(1, Math.min(100, cfg.getInt("percent", 50)));
    }

    void start() {
        for (World w : Bukkit.getWorlds()) {
            w.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, percent);
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (BotView.isBot(p) && !p.isSleepingIgnored()) {
                    p.setSleepingIgnored(true);
                }
            }
        }, 40L, 100L);
    }

    void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (BotView.isBot(e.getPlayer())) {
            e.getPlayer().setSleepingIgnored(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBed(PlayerBedEnterEvent e) {
        if (e.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) {
            return;
        }
        World w = e.getPlayer().getWorld();
        Bukkit.getScheduler().runTask(plugin, () -> {
            int eligible = 0, sleeping = 0;
            for (Player p : w.getPlayers()) {
                if (p.isSleepingIgnored() || p.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                eligible++;
                if (p.isSleeping()) {
                    sleeping++;
                }
            }
            int need = Math.max(1, (int) Math.ceil(eligible * percent / 100.0));
            boolean enough = sleeping >= need;
            for (Player p : w.getPlayers()) {
                if (BotView.isBot(p)) {
                    continue;
                }
                p.sendMessage(Tip.c(enough ? "&e☀ Đủ người ngủ &7[&f" + sleeping + "&7/&f" + need + "&7]&e, trời sắp sáng!"
                        : "&e☾ Đang ngủ để bỏ qua đêm &7[&f" + sleeping + "&7/&f" + need + "&7] &engười..."));
                if (enough) {
                    ScreenNotify.show(p, "&e☀ Trời sắp sáng!", "&7Đủ người ngủ qua đêm");
                }
            }
        });
    }
}
