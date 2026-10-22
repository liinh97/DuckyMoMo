package vn.duckymomo.core.paper;

import io.papermc.paper.event.entity.EntityKnockbackEvent;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Chỉnh theo % của món Thư viện đồ (22/10, người dùng: "% máu, sát thương, tốc chạy, tốc đánh, kháng đẩy lùi, làm cả kiểu âm").
 * Máu % và tốc chạy % dùng chỉ số % có sẵn của game (ItemLibrary). Ba cái dưới server tự tính, dấu PDC duckymomocore:thuvien_pct_<mã>:
 *  - sat_thuong: sát thương gây ra × (1 + tổng%/100) (cận chiến + bắn; tự tính vì server đánh kiểu 1.8, OldCombatMechanics).
 *  - toc_danh: tốc đánh của món (AttackSpeed) × (1 + tổng%/100). Món không đặt tốc đánh (bấm nhanh bao nhiêu cũng đủ đòn): % dương
 *    không đổi gì, % âm thì lấy tốc đánh gốc của game làm mốc (kiếm 1,6 đòn/giây…).
 *  - day_lui: lực bị đẩy lùi × (1 − tổng%/100), tối thiểu 0: +100% = không bị đẩy, âm = bị đẩy xa hơn (kháng đẩy lùi của game không âm được).
 * Món chỉ tính khi ở đúng ô của nó (vũ khí trên tay chính, giáp đang mặc), như chỉ số của game. Cộng dồn nhiều món.
 */
final class ItemPercent implements Listener {

    static final NamespacedKey DAMAGE = new NamespacedKey("duckymomocore", "thuvien_pct_sat_thuong");
    static final NamespacedKey ATTACK_SPEED = new NamespacedKey("duckymomocore", "thuvien_pct_toc_danh");
    static final NamespacedKey KNOCKBACK = new NamespacedKey("duckymomocore", "thuvien_pct_day_lui");
    private static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    /** Tổng % của các món đang ở đúng ô. */
    static double sum(LivingEntity e, NamespacedKey key) {
        EntityEquipment eq = e.getEquipment();
        if (eq == null) {
            return 0;
        }
        double s = 0;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack it = eq.getItem(slot);
            if (it == null || it.isEmpty() || !it.hasItemMeta() || it.getType().getEquipmentSlot() != slot) {
                continue;
            }
            Double v = it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
            if (v != null) {
                s += v;
            }
        }
        return s;
    }

    /** Tốc đánh gốc của game cho món (đòn/giây), dùng làm mốc khi % tốc đánh âm mà món không đặt tốc đánh. */
    @SuppressWarnings("deprecation")
    static double vanillaSpeed(Material m) {
        double v = 4;
        if (m != null && !m.isAir()) {
            for (var mod : m.getDefaultAttributeModifiers(EquipmentSlot.HAND).get(org.bukkit.attribute.Attribute.ATTACK_SPEED)) {
                v += mod.getAmount();
            }
        }
        return Math.max(0.1, v);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        LivingEntity atk = e.getDamager() instanceof LivingEntity l ? l
                : e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof LivingEntity s ? s : null;
        if (atk == null) {
            return;
        }
        double pct = sum(atk, DAMAGE);
        if (pct != 0) {
            e.setDamage(e.getDamage() * Math.max(0, 1 + pct / 100.0));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        double pct = sum(victim, KNOCKBACK);
        if (pct != 0) {
            e.setKnockback(e.getKnockback().multiply(Math.max(0, 1 - pct / 100.0)));
        }
    }
}
