package vn.duckymomo.core.paper;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chỉnh yếu phù phép Hành quyết (Execute) của CrazyEnchantments (22/10, người dùng: "chỉnh yếu đi"). Theo code CE 26.1.2
 * (SwordEnchantments.onPlayerDamage, HIGHEST): MỖI đòn kiếm trúng cho người đánh Sức mạnh IV (+520% kiểu 1.8 của OldCombatMechanics)
 * trong 3 + 20 × cấp tick, không tỉ lệ, không điều kiện; số này viết cứng trong plugin nên không sửa qua Enchantments.yml được.
 * Ở đây: lúc đòn đánh (HIGH, trước CE) ghi lại % máu kẻ địch; CE gắn Sức mạnh IV trong cùng tick đó thì huỷ và:
 *  - kẻ địch còn nhiều hơn ce-execute.max-hp-percent % máu: không cho gì (đúng mô tả "khi kẻ địch sắp hết máu");
 *  - còn lại: cho Sức mạnh cấp ce-execute.amplifier + 1 (0 = Sức mạnh I = +130%), giữ nguyên thời gian của CE.
 * Cài đặt › Phù phép › Hành quyết. ce-execute.enabled: false = để nguyên như CE.
 */
final class CeExecuteTamer implements Listener {

    private final JavaPlugin plugin;
    /** Người đánh -> [% máu kẻ địch trước đòn, tick]. */
    private final Map<UUID, double[]> lastHit = new ConcurrentHashMap<>();
    private boolean applying;

    CeExecuteTamer(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity v)) {
            return;
        }
        var max = v.getAttribute(Attribute.MAX_HEALTH);
        double pct = max == null || max.getValue() <= 0 ? 100 : v.getHealth() / max.getValue() * 100;
        lastHit.put(p.getUniqueId(), new double[]{pct, Bukkit.getCurrentTick()});
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent e) {
        if (applying || !plugin.getConfig().getBoolean("ce-execute.enabled", true) || !(e.getEntity() instanceof Player p)
                || e.getCause() != EntityPotionEffectEvent.Cause.PLUGIN) {
            return;
        }
        PotionEffect ef = e.getNewEffect();
        if (ef == null || !ef.getType().equals(PotionEffectType.STRENGTH) || ef.getAmplifier() != 3) {
            return;
        }
        double[] hit = lastHit.get(p.getUniqueId());
        if (hit == null || (int) hit[1] != Bukkit.getCurrentTick()) {
            return;   // không phải ngay lúc đánh: Sức mạnh IV từ nguồn khác, để nguyên
        }
        e.setCancelled(true);
        if (hit[0] > plugin.getConfig().getDouble("ce-execute.max-hp-percent", 30)) {
            return;
        }
        int amp = Math.max(0, Math.min(3, plugin.getConfig().getInt("ce-execute.amplifier", 0)));
        applying = true;
        try {
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, ef.getDuration(), amp, ef.isAmbient(), ef.hasParticles(), ef.hasIcon()));
        } finally {
            applying = false;
        }
    }
}
