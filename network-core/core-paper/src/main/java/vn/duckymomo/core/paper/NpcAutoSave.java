package vn.duckymomo.core.paper;

import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Lưu NPC Citizens ngay khi có thay đổi (25/10, chủ server: "nên để auto lưu"). Citizens chỉ ghi saves.yml định kỳ
 * (storage.save-task-frequency) và lúc tắt bình thường; máy tắt ngang/mất điện là mất NPC đặt sau lần ghi cuối.
 * Bắt lệnh /npc, /citizens, /dmm thorennpc (người chơi hoặc console) rồi ghi sau 10 giây (đợi skin từ mineskin về; nhiều
 * lệnh liền nhau chỉ ghi một lần). Chỉ đăng ký khi Citizens bật; kiểu Citizens chỉ chạm qua CitizensBridge.
 */
final class NpcAutoSave implements Listener {

    private final DuckyMoMoCore plugin;
    private BukkitTask pending;

    NpcAutoSave(DuckyMoMoCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayer(PlayerCommandPreprocessEvent e) {
        check(e.getMessage().substring(1));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsole(ServerCommandEvent e) {
        check(e.getCommand().startsWith("/") ? e.getCommand().substring(1) : e.getCommand());
    }

    /** RCON (panel, công cụ) có HandlerList riêng, không qua ServerCommandEvent. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRcon(org.bukkit.event.server.RemoteServerCommandEvent e) {
        onConsole(e);
    }

    private void check(String cmd) {
        String c = cmd.toLowerCase(Locale.ROOT);
        if (c.startsWith("citizens save")) {
            return;
        }
        if (c.startsWith("npc ") || c.startsWith("npc2 ") || c.startsWith("citizens:npc ") || c.startsWith("citizens ")
                || c.startsWith("dmm thorennpc") || c.startsWith("duckymomocore:dmm thorennpc")) {
            if (pending != null) {
                pending.cancel();
            }
            pending = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                pending = null;
                try {
                    CitizensBridge.save();
                    plugin.getLogger().info("Đã lưu NPC Citizens (sau lệnh: /" + cmd.split(" ")[0] + ").");
                } catch (Throwable t) {
                    plugin.getLogger().warning("Không lưu được NPC Citizens: " + t);
                }
            }, 200L);
        }
    }
}
