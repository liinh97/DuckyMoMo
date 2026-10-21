package vn.duckymomo.core.paper;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tốc đánh của món Thư viện đồ (attributes.attack_speed, đòn/giây; dấu PDC duckymomocore:thuvien_tocdanh). Server đánh kiểu 1.8
 * (OldCombatMechanics bỏ hồi đòn) nên thuộc tính tốc đánh của game vô tác dụng; ở đây tự tính giống game gốc: đòn tới sớm hơn
 * 1/tốc đánh giây thì sát thương × (0,2 + 0,8 × tiến độ²). Chỉ món có dấu này mới bị tính; món khác vẫn đánh kiểu 1.8.
 * Đổi món trên tay thì tính lại từ đầu (như game).
 */
final class AttackSpeed implements Listener {

    static final NamespacedKey KEY = new NamespacedKey("duckymomocore", "thuvien_tocdanh");
    private final Map<UUID, Long> last = new ConcurrentHashMap<>();

    static double of(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return 0;
        }
        Double v = it.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.DOUBLE);
        return v == null ? 0 : v;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        double aps = of(p.getInventory().getItemInMainHand());
        if (aps <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        Long prev = last.put(p.getUniqueId(), now);
        if (prev == null) {
            return;   // đòn đầu: đủ sát thương
        }
        double progress = Math.min(1, (now - prev) / (1000.0 / aps));
        if (progress < 1) {
            e.setDamage(e.getDamage() * (0.2 + 0.8 * progress * progress));
        }
    }

    @EventHandler
    public void onSwap(PlayerItemHeldEvent e) {
        last.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());   // đổi vũ khí: hồi lại từ đầu
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        last.remove(e.getPlayer().getUniqueId());
    }
}
