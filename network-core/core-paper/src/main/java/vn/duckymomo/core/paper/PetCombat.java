package vn.duckymomo.core.paper;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Pet chiến đấu (MyPet) đánh người chơi, Bang Hội Chiến. Config pet-combat:
 *  - player-damage-reduce-percent: pet đánh người chơi bị giảm bao nhiêu % sát thương (mọi nơi).
 *  - siege-pet-chance: % phiên công thành CHO pet tham chiến. Mỗi phiên bốc thăm cố định theo giờ bắt đầu (seed + giờ),
 *    nên biết trước để báo cùng lịch. Phiên không cho: pet không gây sát thương cho người chơi trong vùng vây lúc đang đánh.
 *  - announce-minutes: báo cả server trước phiên bao nhiêu phút (kèm có/không pet); /lichcongthanh xem bất cứ lúc nào.
 * Lúc có phiên đánh, trong vùng vây: không bay bằng pet (Thú Cưỡi cấp 10+). Pet cưỡi cao hơn 3 khối so với mặt đất thì người cưỡi
 * bị đẩy xuống, kèm Rơi chậm 6 giây (không mất máu). Cưỡi chạy dưới đất và nhảy vẫn được.
 * Nhận pet MyPet qua interface de.Keyle.MyPet.api.entity.MyPetBukkitEntity (so tên lớp, không cần jar MyPet khi build).
 */
final class PetCombat implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("HH:mm EEEE dd/MM", new java.util.Locale("vi"))
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final String MYPET_ENTITY = "de.Keyle.MyPet.api.entity.MyPetBukkitEntity";

    private final DuckyMoMoCore plugin;
    private final double reduce;
    private final int chance;
    private final String seed;
    private final List<Integer> announce;
    private final Set<String> announced = new HashSet<>();
    private Long upcoming;
    private boolean wasActive;
    private boolean currentAllowed = true;
    private final Map<UUID, Long> flyWarned = new HashMap<>();

    PetCombat(DuckyMoMoCore plugin, ConfigurationSection c) {
        this.plugin = plugin;
        this.reduce = Math.max(0, Math.min(100, c.getDouble("player-damage-reduce-percent", 50)));
        this.chance = Math.max(0, Math.min(100, c.getInt("siege-pet-chance", 30)));
        this.seed = c.getString("seed", "mua1");
        this.announce = c.getIntegerList("announce-minutes");
    }

    String describe() {
        return "giảm " + fmt(reduce) + "% sát thương lên người chơi, " + chance + "% phiên công thành cho pet";
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 200L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::noSiegeFlight, 100L, 5L);
    }

    /** Đang có phiên đánh: cưỡi pet bay trong vùng vây -> đẩy người xuống (Rơi chậm), pet vẫn ở lại. */
    private void noSiegeFlight() {
        if (!SiegeWarHook.battleActive()) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            Entity v = p.getVehicle();
            Entity pet = isPet(v) ? v : v != null && isPet(v.getVehicle()) ? v.getVehicle() : null;   // MyPet có khi cho ngồi qua "ghế"
            if (pet == null || pet.isOnGround() || pet.isInWater() || !highAboveGround(pet) || !SiegeWarHook.inActiveSiegeZone(pet.getLocation())) {
                continue;
            }
            p.leaveVehicle();
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 120, 0, false, false, true));
            long now = System.currentTimeMillis();
            if (now - flyWarned.getOrDefault(p.getUniqueId(), 0L) > 5000L) {
                flyWarned.put(p.getUniqueId(), now);
                p.sendMessage(c("&c⚔ Đang công thành: không được bay bằng pet trong vùng vây."));
            }
        }
    }

    /** 3 khối ngay dưới pet đều trống (nhảy thường chỉ cao khoảng 2 khối nên không bị tính). */
    private static boolean highAboveGround(Entity e) {
        org.bukkit.block.Block b = e.getLocation().getBlock();
        for (int i = 1; i <= 3; i++) {
            if (!b.getRelative(0, -i, 0).isPassable()) {
                return false;
            }
        }
        return true;
    }

    /** Phiên bắt đầu lúc startMillis có cho pet tham chiến không (cố định, tính trước được). */
    boolean allowedFor(long startMillis) {
        if (chance >= 100) {
            return true;
        }
        if (chance <= 0) {
            return false;
        }
        return Math.floorMod(Objects.hash(seed, startMillis / 60000L), 100) < chance;
    }

    private void tick() {
        boolean active = SiegeWarHook.battleActive();
        Long next = SiegeWarHook.nextBattleSessionStart();
        long now = System.currentTimeMillis();
        if (!active && next != null && next > now) {
            upcoming = next;
            long mins = (next - now + 59_999L) / 60_000L;
            for (int m : announce) {
                String key = next + ":" + m;
                if (mins <= m && mins > m - 1 && announced.add(key)) {
                    broadcast("&6⚔ Còn " + m + " phút nữa là phiên công thành (" + WHEN.format(Instant.ofEpochMilli(next)) + "): "
                            + petText(allowedFor(next)) + "&6. Gõ &f/lichcongthanh &6xem lịch.");
                }
            }
        }
        if (active && !wasActive) {
            currentAllowed = upcoming == null || allowedFor(upcoming);
            broadcast("&c⚔ Phiên công thành bắt đầu: " + petText(currentAllowed) + "&c.");
        }
        wasActive = active;
    }

    private static String petText(boolean allowed) {
        return allowed ? "&aCÓ cho pet tham chiến" : "&cKHÔNG cho pet tham chiến";
    }

    private void broadcast(String msg) {
        Component m = c(msg);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(m);
        }
        plugin.getLogger().info("Pet công thành: " + LEGACY.serialize(m));
    }

    static boolean isPet(Entity e) {
        if (e == null) {
            return false;
        }
        for (Class<?> k = e.getClass(); k != null; k = k.getSuperclass()) {
            for (Class<?> i : k.getInterfaces()) {
                if (i.getName().equals(MYPET_ENTITY)) {
                    return true;
                }
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || victim.getWorld().getName().equals(Arena.WORLD)) {
            return;   // trong Đấu Trường: module Arena tự tính (mức giảm riêng)
        }
        Entity d = event.getDamager();
        if (d instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) {
            d = shooter;
        }
        if (!isPet(d)) {
            return;
        }
        if (SiegeWarHook.battleActive() && !currentAllowed && SiegeWarHook.inActiveSiegeZone(victim.getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (reduce > 0) {
            event.setDamage(event.getDamage() * (100 - reduce) / 100.0);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Long next = upcoming;
        if (next != null && next > System.currentTimeMillis()) {
            Player p = event.getPlayer();
            Bukkit.getScheduler().runTaskLater(plugin, () -> p.sendMessage(c("&6⚔ Phiên công thành tới (" + WHEN.format(Instant.ofEpochMilli(next))
                    + "): " + petText(allowedFor(next)) + "&6.")), 80L);
        }
    }

    /** /lichcongthanh: phiên tới, có cho pet không, luật pet đánh người. */
    boolean command(CommandSender s, String[] args) {
        s.sendMessage(c("&6&l⚔ Lịch công thành"));
        if (SiegeWarHook.battleActive()) {
            s.sendMessage(c("&cĐang có phiên đánh: " + petText(currentAllowed) + "&c."));
        }
        Long next = upcoming != null ? upcoming : SiegeWarHook.nextBattleSessionStart();
        if (next == null || next <= System.currentTimeMillis()) {
            s.sendMessage(c("&7Chưa có phiên công thành sắp tới (chưa ai cắm cờ vây)."));
        } else {
            s.sendMessage(c("&ePhiên tới: &f" + WHEN.format(Instant.ofEpochMilli(next)) + " &7— " + petText(allowedFor(next))));
        }
        s.sendMessage(c("&7Pet chiến đấu đánh người chơi bị giảm &f" + fmt(reduce) + "% &7sát thương. Khoảng &f" + chance
                + "% &7phiên công thành cho pet tham chiến (bốc thăm, báo trước)."));
        return true;
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
}
