package vn.duckymomo.core.paper;

import java.net.InetSocketAddress;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Bot của botfarm (trang quản trị › Bot) chỉ giữ một vùng nhỏ quanh mình (27/10, chủ server: 22 bot mà server sắp quá tải).
 * Mỗi bot đứng một khu riêng, mỗi khu server phải nạp view-distance (8 -> 17x17 chunk) và chạy simulation-distance (6 -> 13x13 chunk:
 * quái sinh, cây mọc, dơi…): 22 bot = ~5.600 chunk, ~4.700 thực thể, MSPT ~45. Bot đứng yên nên đặt riêng cho bot simulation / view
 * distance nhỏ (mặc định 2); người thật đi tới gần bot vẫn chạy vùng của chính họ như thường.
 * Nhận ra bot bằng IP giả 10.30.x.x mà botfarm gửi qua Velocity forwarding (người thật luôn vào qua proxy bằng IP thật).
 * Config: bot-view.enabled (mặc định bật), bot-view.simulation-distance, bot-view.view-distance.
 */
final class BotView implements Listener {

    private final int sim;
    private final int view;
    private final Logger log;

    BotView(int sim, int view, Logger log) {
        this.sim = Math.max(2, Math.min(32, sim));
        this.view = Math.max(2, Math.min(32, view));
        this.log = log;
        for (Player p : Bukkit.getOnlinePlayers()) {
            apply(p);   // nạp lại plugin khi bot đang online
        }
    }

    static boolean isBot(Player p) {
        InetSocketAddress a = p.getAddress();
        return a != null && a.getAddress() != null && a.getAddress().getHostAddress().startsWith("10.30.");
    }

    void apply(Player p) {
        if (!isBot(p)) {
            return;
        }
        try {
            p.setSimulationDistance(sim);
            p.setViewDistance(view);
            p.setSendViewDistance(view);
        } catch (RuntimeException e) {
            log.warning("Không đặt được tầm nhìn cho bot " + p.getName() + ": " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        apply(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent e) {
        apply(e.getPlayer());
    }
}
