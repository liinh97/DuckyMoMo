package vn.duckymomo.core.paper;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    /**
     * Dọn quái "đông cứng" quanh bot (29/10, spark: ~5.000 quái, TPS 12–15). Bot giữ vòng chunk ngoài được nạp nhưng không chạy
     * (ngoài simulation-distance): quái lang thang ra đó đứng im mãi, không bao giờ tự biến mất, vẫn tốn công đếm / theo dõi.
     * Mỗi phút gỡ quái thù địch + dơi trong 64 khối quanh bot nếu: không có người THẬT trong 128 khối (như luật biến mất của game),
     * quái thường (removeWhenFarAway, không tên, không persistent). EliteMobs / pet / NPC có tên hoặc persistent nên không bị đụng.
     */
    void startCleanup(org.bukkit.plugin.Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanup, 200L, 1200L);
    }

    private void cleanup() {
        List<Player> real = new ArrayList<>();
        List<Player> bots = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            (isBot(p) ? bots : real).add(p);
        }
        Set<org.bukkit.entity.Entity> gone = new HashSet<>();
        for (Player b : bots) {
            for (org.bukkit.entity.Entity e : b.getNearbyEntities(64, 64, 64)) {
                if (gone.contains(e) || !(e instanceof org.bukkit.entity.Mob m)) {
                    continue;
                }
                if (!(m instanceof org.bukkit.entity.Enemy) && !(m instanceof org.bukkit.entity.Bat)) {
                    continue;
                }
                if (!m.getRemoveWhenFarAway() || m.customName() != null || m.isPersistent() || m.isInsideVehicle() || !m.getPassengers().isEmpty()) {
                    continue;
                }
                boolean near = false;
                for (Player r : real) {
                    if (r.getWorld().equals(m.getWorld()) && r.getLocation().distanceSquared(m.getLocation()) < 128 * 128) {
                        near = true;
                        break;
                    }
                }
                if (!near) {
                    gone.add(m);
                }
            }
        }
        for (org.bukkit.entity.Entity e : gone) {
            e.remove();
        }
        if (gone.size() >= 50) {
            log.info("Dọn " + gone.size() + " quái đông cứng quanh bot (không có người thật gần)");
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
            // 29/10: bot đứng yên cả ngày làm quái sinh dồn quanh mình (spark: ~5.000 quái, TPS 14). Không tính bot vào sinh quái
            // (Paper: người không affects-spawning cũng bị bỏ qua khi xét quái biến mất) → quái quanh bot tự biến mất, quanh người thật vẫn sinh
            p.setAffectsSpawning(false);
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
