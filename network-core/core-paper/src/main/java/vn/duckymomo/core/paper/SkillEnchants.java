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
 * Phù phép riêng của server, chuột phải (Thư viện đồ › Phù phép, mã web "dmm:<mã>", lưu dmm-enchants trong thuvien-do.yml):
 * dấu PDC duckymomocore:phuphep_<mã> = cấp + dòng "Lướt II" đầu mô tả; cấp quyết định mạnh yếu (levelValue).
 *   luot        Lướt về phía trước (lực 1 / 1,5 / 2), hồi 4 giây
 *   hoi_mau     Hồi máu 1 tim mỗi cấp, hồi 20 giây (đầy máu không dùng)
 *   cau_lua     Cầu lửa: sát thương 3 + 2 × cấp, cháy người trúng, không đốt khối, hồi 3 giây
 *   bay         Bay lên: bay lơ lửng 1 + cấp giây rồi rơi chậm, hồi 15 giây
 *   dich_chuyen Dịch chuyển tức thì 2 + 2 × cấp khối về trước, dừng trước tường (không xuyên), hồi 8 giây
 *   dam_dat     Dậm đất: sát thương 2 + cấp, bán kính 2 + cấp, hất tung, hồi 12 giây
 *   cuong_no    Huyết chiến: mất 3 tim, +10 + 10 × cấp % sát thương trong 8 giây, hồi 30 giây (máu ≤ 7 thì không dùng)
 *   phong_bay   Phóng bay: gắn lên cánh elytra; đang mặc, chuột phải vào không khí là phóng lên và bay, hồi 10 giây
 * Bay lên, Dịch chuyển, Phóng bay tắt trong vùng vây lúc có phiên đánh. Sát thương đi qua EntityDamageByEntityEvent nên Towny / PvP /
 * vùng an toàn vẫn chặn như đòn thường. Hồi chiêu theo người.
 */
final class SkillEnchants implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    /** mã -> [tên, tên tiếng Anh]; thứ tự = thứ tự dòng trên đồ. Khớp DMM_ENCH trong panel/thuvien.html và LIB_DMM_ENCH trong app.py. */
    static final Map<String, String[]> NAMES = new LinkedHashMap<>();
    static final int MAX_LEVEL = 3;
    static final NamespacedKey FIRE = new NamespacedKey("duckymomocore", "caulua_satthuong");
    private static final Map<String, Long> COOLDOWN_MS = Map.of("luot", 4000L, "hoi_mau", 20000L, "cau_lua", 3000L, "bay", 15000L,
            "dich_chuyen", 8000L, "dam_dat", 12000L, "cuong_no", 30000L, "phong_bay", 10000L);
    /** Cuồng nộ đang bật: người -> [hết lúc (ms), hệ số sát thương]. */
    private static final Map<UUID, double[]> RAGE = new ConcurrentHashMap<>();
    /** Kỹ năng di chuyển: tắt trong vùng vây lúc có phiên đánh. */
    private static final java.util.Set<String> MOBILITY = java.util.Set.of("bay", "dich_chuyen", "phong_bay");
    private static final Map<String, Long> NEXT = new ConcurrentHashMap<>();   // "<uuid>:<mã>" -> lúc dùng lại được
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};

    static {
        NAMES.put("luot", new String[]{"Lướt", "Dash"});
        NAMES.put("hoi_mau", new String[]{"Hồi máu", "Heal"});
        NAMES.put("cau_lua", new String[]{"Cầu lửa", "Fire"});
        NAMES.put("bay", new String[]{"Bay lên", "Ascend"});
        NAMES.put("dich_chuyen", new String[]{"Dịch chuyển", "Transmission"});
        NAMES.put("dam_dat", new String[]{"Dậm đất", "Rumble"});
        NAMES.put("cuong_no", new String[]{"Huyết chiến", "Soul Storm"});   // "Cuồng nộ" trùng tên phù phép Rage của CE
        NAMES.put("phong_bay", new String[]{"Phóng bay", "Slingshot"});
    }

    static NamespacedKey key(String id) {
        return new NamespacedKey("duckymomocore", "phuphep_" + id);
    }

    /** Giá trị kỹ năng theo cấp phù phép: Lướt = lực 1 / 1,5 / 2; Hồi máu = 2 máu (1 tim) mỗi cấp; Cầu lửa = 3 + 2 × cấp sát thương. */
    static double levelValue(String id, int lv) {
        return switch (id) {
            case "luot" -> 0.5 + 0.5 * lv;
            case "hoi_mau" -> 2.0 * lv;
            case "bay" -> 1 + lv;              // giây
            case "dich_chuyen" -> 2 + 2.0 * lv; // khối
            case "dam_dat" -> 2 + lv;           // sát thương, bán kính cũng = 2 + cấp
            case "cuong_no" -> 10 + 10.0 * lv;  // %
            case "phong_bay" -> lv;
            default -> 3 + 2.0 * lv;            // cau_lua
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
        if (MOBILITY.contains(id) && SiegeWarHook.battleActive() && SiegeWarHook.inActiveSiegeZone(p.getLocation())) {
            p.sendActionBar(LEGACY.deserialize("&c" + name + " không dùng được trong vùng vây đang đánh"));
            return false;
        }
        switch (id) {
            case "bay" -> {
                int ticks = (int) Math.round((value <= 0 ? 2 : value) * 20);
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION, ticks, 0, false, false, true));
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING, ticks + 120, 0, false, false, true));
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.8f, 1.2f);
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 16, 0.4, 0.1, 0.4, 0.02);
            }
            case "dich_chuyen" -> {
                org.bukkit.Location to = blinkTarget(p, value <= 0 ? 4 : value);
                if (to == null) {
                    p.sendActionBar(LEGACY.deserialize("&7Phía trước bị chặn, không dịch chuyển được"));
                    return false;
                }
                p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.3);
                p.teleport(to, io.papermc.paper.entity.TeleportFlag.Relative.VELOCITY_ROTATION);
                p.getWorld().playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
            }
            case "dam_dat" -> {
                double r = value <= 0 ? 3 : value;
                p.getWorld().spawnParticle(Particle.EXPLOSION, p.getLocation(), 3, r / 2, 0.2, r / 2, 0);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
                for (org.bukkit.entity.Entity en : p.getNearbyEntities(r, 2, r)) {
                    if (en instanceof org.bukkit.entity.LivingEntity le && en != p && !PetCombat.isPet(en)) {
                        le.damage(value <= 0 ? 3 : value, p);   // qua sự kiện: Towny / PvP vẫn kiểm
                        if (!le.isDead()) {
                            le.setVelocity(le.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0).normalize().multiply(0.6).setY(0.5));
                        }
                    }
                }
            }
            case "cuong_no" -> {
                if (p.getHealth() <= 7) {
                    p.sendActionBar(LEGACY.deserialize("&cMáu quá thấp để Huyết chiến (cần hơn 3,5 tim)"));
                    return false;
                }
                p.setHealth(p.getHealth() - 6);
                RAGE.put(p.getUniqueId(), new double[]{now + 8000, 1 + (value <= 0 ? 20 : value) / 100.0});
                p.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, p.getLocation().add(0, 2, 0), 6, 0.4, 0.3, 0.4, 0);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.6f, 1.2f);
                p.sendActionBar(LEGACY.deserialize("&cHuyết chiến: &f+" + Math.round(value <= 0 ? 20 : value) + "% sát thương trong 8 giây"));
            }
            case "phong_bay" -> {
                ItemStack chest = p.getInventory().getChestplate();
                if (chest == null || chest.getType() != org.bukkit.Material.ELYTRA) {
                    return false;
                }
                double force = Math.max(1, value);
                p.setVelocity(p.getLocation().getDirection().multiply(0.8 + 0.4 * force).setY(0.9 + 0.2 * force));
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
                p.getWorld().spawnParticle(Particle.FIREWORK, p.getLocation(), 20, 0.3, 0.1, 0.3, 0.05);
                org.bukkit.Bukkit.getScheduler().runTaskLater(org.bukkit.Bukkit.getPluginManager().getPlugin("DuckyMoMoCore"), () -> {
                    if (p.isOnline() && !p.isOnGround()) {
                        p.setGliding(true);
                    }
                }, 6L);
            }
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

    /** Điểm đến Dịch chuyển: đi theo hướng nhìn từng 0,5 khối, dừng ở chỗ trống xa nhất (đủ 2 khối cao) trước khi chạm khối đặc. */
    private static org.bukkit.Location blinkTarget(Player p, double dist) {
        org.bukkit.Location eye = p.getEyeLocation();
        org.bukkit.util.Vector dir = eye.getDirection().normalize();
        org.bukkit.Location best = null;
        for (double d = 0.5; d <= dist; d += 0.5) {
            org.bukkit.Location feet = p.getLocation().add(dir.clone().multiply(d));
            org.bukkit.block.Block a = feet.getBlock(), b2 = a.getRelative(org.bukkit.block.BlockFace.UP);
            if (!a.isPassable() || !b2.isPassable() || !eye.clone().add(dir.clone().multiply(d)).getBlock().isPassable()) {
                break;   // không xuyên tường
            }
            best = feet;
        }
        if (best == null || best.distanceSquared(p.getLocation()) < 1) {
            return null;
        }
        best.setYaw(p.getLocation().getYaw());
        best.setPitch(p.getLocation().getPitch());
        return best;
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
        ItemStack chest = e.getPlayer().getInventory().getChestplate();
        if (e.getAction() == Action.RIGHT_CLICK_AIR && chest != null && chest.hasItemMeta()) {
            Integer lv = chest.getItemMeta().getPersistentDataContainer().get(key("phong_bay"), PersistentDataType.INTEGER);
            if (lv != null && lv > 0) {
                cast(e.getPlayer(), "phong_bay", levelValue("phong_bay", lv));
            }
        }
        ItemStack it = e.getPlayer().getInventory().getItemInMainHand();
        if (!it.hasItemMeta()) {
            return;
        }
        PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();
        boolean used = false;
        for (String id : NAMES.keySet()) {
            if (id.equals("phong_bay")) {
                continue;   // ở trên cánh, xử lý phía trên
            }
            Integer lv = pdc.get(key(id), PersistentDataType.INTEGER);
            if (lv != null && lv > 0 && cast(e.getPlayer(), id, levelValue(id, lv)) && !id.equals("luot")) {
                used = true;
            }
        }
        if (used) {
            e.setCancelled(true);   // không ăn / đặt khối khi vừa hồi máu, bắn cầu lửa
        }
    }

    /** Cầu lửa: sát thương theo cấp / value. Cuồng nộ: nhân sát thương đòn đánh / bắn của người đang Cuồng nộ. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Player atk = e.getDamager() instanceof Player pl ? pl
                : e.getDamager() instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Player pl ? pl : null;
        if (atk != null) {
            double[] r = RAGE.get(atk.getUniqueId());
            if (r != null) {
                if (r[0] < System.currentTimeMillis()) {
                    RAGE.remove(atk.getUniqueId());
                } else {
                    e.setDamage(e.getDamage() * r[1]);
                }
            }
        }
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
        RAGE.remove(e.getPlayer().getUniqueId());
    }
}
