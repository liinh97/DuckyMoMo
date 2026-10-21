package vn.duckymomo.core.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kỹ năng chuột phải: Lướt (Dash), Hồi máu (Heal), Cầu lửa (Fire). Hai cách gắn lên đồ:
 *  - phù phép riêng của server (Thư viện đồ › Phù phép, mã web "dmm:luot|hoi_mau|cau_lua", lưu dmm-enchants trong thuvien-do.yml):
 *    dấu PDC duckymomocore:phuphep_<mã> = cấp + dòng "Lướt II" đầu mô tả; cấp quyết định mạnh yếu (LEVEL_VALUE);
 *  - kỹ năng Xưởng (feature luot / hoi_mau / cau_lua, value tự đặt) — Workshop gọi cast().
 * Hồi chiêu theo người, dùng chung cho cả hai cách: Lướt 4 giây, Hồi máu 20 giây, Cầu lửa 3 giây. Cầu lửa không đốt khối,
 * sát thương đi qua EntityDamageByEntityEvent nên Towny / PvP / vùng an toàn vẫn chặn như đòn thường.
 */
final class SkillEnchants implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    /** mã -> [tên, tên tiếng Anh]; thứ tự = thứ tự dòng trên đồ. Khớp DMM_ENCH trong panel/thuvien.html và LIB_DMM_ENCH trong app.py. */
    static final Map<String, String[]> NAMES = new LinkedHashMap<>();
    static final int MAX_LEVEL = 3;
    static final NamespacedKey FIRE = new NamespacedKey("duckymomocore", "caulua_satthuong");
    private static final Map<String, Long> COOLDOWN_MS = Map.of("luot", 4000L, "hoi_mau", 20000L, "cau_lua", 3000L);
    private static final Map<String, Long> NEXT = new ConcurrentHashMap<>();   // "<uuid>:<mã>" -> lúc dùng lại được
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};

    static {
        NAMES.put("luot", new String[]{"Lướt", "Dash"});
        NAMES.put("hoi_mau", new String[]{"Hồi máu", "Heal"});
        NAMES.put("cau_lua", new String[]{"Cầu lửa", "Fire"});
    }

    static NamespacedKey key(String id) {
        return new NamespacedKey("duckymomocore", "phuphep_" + id);
    }

    /** Giá trị kỹ năng theo cấp phù phép: Lướt = lực 1 / 1,5 / 2; Hồi máu = 2 máu (1 tim) mỗi cấp; Cầu lửa = 3 + 2 × cấp sát thương. */
    static double levelValue(String id, int lv) {
        return switch (id) {
            case "luot" -> 0.5 + 0.5 * lv;
            case "hoi_mau" -> 2.0 * lv;
            default -> 3 + 2.0 * lv;
        };
    }

    /** Ghi phù phép riêng lên đồ (Thư viện đồ). Trả các dòng hiện ở đầu mô tả. */
    static List<Component> apply(ItemMeta meta, Map<String, Integer> levels) {
        List<Component> lines = new ArrayList<>();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        for (String id : NAMES.keySet()) {
            Integer lv = levels.get(id);
            if (lv == null || lv < 1) {
                continue;
            }
            pdc.set(key(id), PersistentDataType.INTEGER, lv);
            lines.add(Component.text(NAMES.get(id)[0] + " " + (lv < ROMAN.length ? ROMAN[lv] : String.valueOf(lv)), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (!lines.isEmpty() && !meta.hasEnchants() && !meta.hasEnchantmentGlintOverride()) {
            meta.setEnchantmentGlintOverride(true);   // lấp lánh như đồ có phù phép
        }
        return lines;
    }

    /** Dùng kỹ năng (đã kiểm điều kiện dùng đồ ở chỗ gọi). true = đã dùng. */
    static boolean cast(Player p, String id, double value) {
        long now = System.currentTimeMillis();
        String k = p.getUniqueId() + ":" + id;
        String name = NAMES.getOrDefault(id, new String[]{id})[0];
        long next = NEXT.getOrDefault(k, 0L);
        if (next > now) {
            p.sendActionBar(LEGACY.deserialize("&7" + name + " hồi trong " + ((next - now) / 1000 + 1) + " giây"));
            return false;
        }
        switch (id) {
            case "luot" -> {
                double force = Math.max(1, Math.min(3, value <= 0 ? 1.5 : value));
                p.setVelocity(p.getLocation().getDirection().setY(0).normalize().multiply(0.9 + 0.6 * force).setY(0.25));
                p.setFallDistance(0);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 0.8f, 1.3f);
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.2, 0), 12, 0.3, 0.1, 0.3, 0.02);
            }
            case "hoi_mau" -> {
                var attr = p.getAttribute(Attribute.MAX_HEALTH);
                double max = attr == null ? 20 : attr.getValue();
                if (p.getHealth() >= max) {
                    p.sendActionBar(LEGACY.deserialize("&7Máu đang đầy"));
                    return false;
                }
                p.setHealth(Math.min(max, p.getHealth() + (value <= 0 ? 6 : value)));
                p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 6, 0.4, 0.3, 0.4, 0);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.8f);
            }
            case "cau_lua" -> {
                SmallFireball f = p.launchProjectile(SmallFireball.class, p.getLocation().getDirection().multiply(1.4));
                f.setIsIncendiary(false);   // không đốt khối (đất người khác, rừng)
                f.getPersistentDataContainer().set(FIRE, PersistentDataType.DOUBLE, value <= 0 ? 5 : value);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.8f, 1f);
            }
            default -> {
                return false;
            }
        }
        NEXT.put(k, now + COOLDOWN_MS.getOrDefault(id, 3000L));
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Block b = e.getClickedBlock();
        if (b != null && b.getType().isInteractable() && !e.getPlayer().isSneaking()) {
            return;   // bấm rương, cửa… vẫn mở như thường (ngồi xuống + bấm thì dùng kỹ năng)
        }
        ItemStack it = e.getPlayer().getInventory().getItemInMainHand();
        if (!it.hasItemMeta()) {
            return;
        }
        PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();
        boolean used = false;
        for (String id : NAMES.keySet()) {
            Integer lv = pdc.get(key(id), PersistentDataType.INTEGER);
            if (lv != null && lv > 0 && cast(e.getPlayer(), id, levelValue(id, lv)) && !id.equals("luot")) {
                used = true;
            }
        }
        if (used) {
            e.setCancelled(true);   // không ăn / đặt khối khi vừa hồi máu, bắn cầu lửa
        }
    }

    /** Cầu lửa: sát thương theo cấp / value. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof SmallFireball fb) {
            Double dmg = fb.getPersistentDataContainer().get(FIRE, PersistentDataType.DOUBLE);
            if (dmg != null) {
                e.setDamage(dmg);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        String u = e.getPlayer().getUniqueId() + ":";
        NEXT.keySet().removeIf(k -> k.startsWith(u));
    }
}
