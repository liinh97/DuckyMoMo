package vn.duckymomo.core.paper;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExplosiveMinecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Luật vũ khí nổ lên NGƯỜI CHƠI (explosive-pvp): crystal, respawn anchor, nổ giường (Nether/End), xe goòng TNT.
 * Mỗi loại một hệ số sát thương: 0 = không gây sát thương (huỷ luôn cú hất), 0.3 = còn 30%, 1 = như gốc.
 * Chỉ đụng tới sát thương lên người chơi: quái, rồng Ender, phá khối vẫn như gốc (phá khối do Towny/WorldGuard lo).
 * TNT thường (khối TNT) không đổi: SiegeWar và luật đất hoang đã quản.
 */
final class ExplosionRules implements Listener {
    private final double crystal;
    private final double anchor;
    private final double bed;
    private final double tntCart;

    ExplosionRules(ConfigurationSection cfg) {
        this.crystal = clamp(cfg.getDouble("end-crystal", 0));
        this.anchor = clamp(cfg.getDouble("respawn-anchor", 0));
        this.bed = clamp(cfg.getDouble("bed", 0));
        this.tntCart = clamp(cfg.getDouble("tnt-minecart", 0.3));
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    String describe() {
        return "crystal x" + crystal + ", anchor x" + anchor + ", giường x" + bed + ", xe goòng TNT x" + tntCart;
    }

    private static void apply(org.bukkit.event.entity.EntityDamageEvent e, double mult) {
        if (mult >= 1) {
            return;
        }
        if (mult <= 0) {
            e.setCancelled(true);
        } else {
            e.setDamage(e.getDamage() * mult);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityBlast(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) {
            return;
        }
        Entity d = e.getDamager();
        if (d instanceof EnderCrystal) {
            apply(e, crystal);
        } else if (d instanceof ExplosiveMinecart) {
            apply(e, tntCart);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBlast(EntityDamageByBlockEvent e) {
        if (!(e.getEntity() instanceof Player)) {
            return;
        }
        // Paper: trạng thái khối TRƯỚC khi nổ (lúc sự kiện chạy khối đã thành không khí)
        BlockState st = e.getDamagerBlockState();
        Material m = st == null ? null : st.getType();
        if (m == Material.RESPAWN_ANCHOR) {
            apply(e, anchor);
        } else if (m != null && m.name().endsWith("_BED")) {
            apply(e, bed);
        }
    }
}
