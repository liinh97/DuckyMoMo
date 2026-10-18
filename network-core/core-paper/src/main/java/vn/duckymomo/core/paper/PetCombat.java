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
import org.bukkit.entity.LivingEntity;
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
 *  - siege-no-fly / siege-no-fly-height: lúc có phiên đánh, trong vùng vây không bay bằng pet (loài bay, hệ Thú Cưỡi). Pet cưỡi cao hơn
 *    ngần ấy khối so với mặt đất thì người cưỡi bị đẩy xuống, kèm Rơi chậm 6 giây (không mất máu). Chạy dưới đất và nhảy vẫn được.
 *  - siege-weather: lúc có phiên đánh, trong vùng vây, trời đất ảnh hưởng trận và cho pet "đất diễn":
 *      đêm: ai không có pet mắt đêm (night-eyes) ở gần thì bị Bóng tối; có thì được Nhìn đêm, pet mắt đêm +night-pet-bonus% sát thương.
 *      mưa: người đi bộ bị Chậm (cưỡi pet thì không), pet nước (water-pets) +rain-water-bonus%, pet lửa (fire-pets) -rain-fire-malus%.
 *      nắng gắt (mùa heat-season ban ngày, không mưa): ai không dẫn pet lửa thì bị Đói, pet lửa +heat-bonus%.
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
    private final boolean noFly;
    private final int noFlyHeight;
    private final Set<String> announced = new HashSet<>();
    private Long upcoming;
    private boolean wasActive;
    private boolean currentAllowed = true;
    private final Map<UUID, Long> flyWarned = new HashMap<>();
    private final Map<UUID, String> weatherShown = new HashMap<>();
    private final boolean weather;
    private final boolean nightDark;
    private final boolean rainSlow;
    private final boolean heatHunger;
    private final Set<String> eyes = new HashSet<>();
    private final Set<String> waterPets = new HashSet<>();
    private final Set<String> firePets = new HashSet<>();
    private final double eyesRange;
    private final double nightBonus;
    private final double waterBonus;
    private final double fireMalus;
    private final double heatBonus;
    private final String heatSeason;

    PetCombat(DuckyMoMoCore plugin, ConfigurationSection c) {
        this.plugin = plugin;
        this.reduce = Math.max(0, Math.min(100, c.getDouble("player-damage-reduce-percent", 50)));
        this.chance = Math.max(0, Math.min(100, c.getInt("siege-pet-chance", 30)));
        this.seed = c.getString("seed", "mua1");
        this.announce = c.getIntegerList("announce-minutes");
        this.noFly = c.getBoolean("siege-no-fly", true);
        this.noFlyHeight = Math.max(2, Math.min(20, c.getInt("siege-no-fly-height", 3)));
        ConfigurationSection w = c.getConfigurationSection("siege-weather");
        this.weather = w != null && w.getBoolean("enabled", true);
        this.nightDark = w != null && w.getBoolean("night-darkness", true);
        this.rainSlow = w != null && w.getBoolean("rain-slowness", true);
        this.heatHunger = w != null && w.getBoolean("heat-hunger", true);
        this.eyesRange = w == null ? 24 : w.getDouble("pet-range", 24);
        this.nightBonus = w == null ? 0 : w.getDouble("night-pet-bonus", 20);
        this.waterBonus = w == null ? 0 : w.getDouble("rain-water-bonus", 20);
        this.fireMalus = w == null ? 0 : w.getDouble("rain-fire-malus", 25);
        this.heatBonus = w == null ? 0 : w.getDouble("heat-bonus", 15);
        this.heatSeason = w == null ? "kho" : w.getString("heat-season", "kho");
        if (w != null) {
            w.getStringList("night-eyes").forEach(t -> eyes.add(t.toLowerCase(java.util.Locale.ROOT)));
            w.getStringList("water-pets").forEach(t -> waterPets.add(t.toLowerCase(java.util.Locale.ROOT)));
            w.getStringList("fire-pets").forEach(t -> firePets.add(t.toLowerCase(java.util.Locale.ROOT)));
        }
    }

    String describe() {
        return "giảm " + fmt(reduce) + "% sát thương lên người chơi, " + chance + "% phiên công thành cho pet"
                + (noFly ? ", cấm bay bằng pet trong vùng vây (cao hơn " + noFlyHeight + " khối)" : "")
                + (weather ? ", thời tiết công thành (" + eyes.size() + " loài mắt đêm, " + waterPets.size() + " loài nước, " + firePets.size() + " loài lửa)" : "");
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 200L);
        if (noFly) {
            Bukkit.getScheduler().runTaskTimer(plugin, this::noSiegeFlight, 100L, 5L);
        }
        if (weather) {
            Bukkit.getScheduler().runTaskTimer(plugin, this::siegeWeather, 100L, 20L);
        }
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

    /** Trời lúc này ở chỗ người chơi (đang có phiên đánh, trong vùng vây): "dem", "mua", "nang" (có thể nhiều). */
    private Set<String> conditions(org.bukkit.Location l) {
        Set<String> out = new HashSet<>();
        long t = l.getWorld().getTime();
        if (t >= 13000 && t < 23000) {
            out.add("dem");
        }
        if (l.getWorld().hasStorm()) {
            out.add("mua");
        } else if (t < 12000 && heatSeason.equals(Seasons.now())) {
            out.add("nang");
        }
        return out;
    }

    /** Pet đang dẫn ở gần người chơi: loài (chữ thường) hoặc null. */
    private String petNear(Player p) {
        Object pet = MyPetBridge.active(p);
        LivingEntity e = pet == null ? null : MyPetBridge.entity(pet);
        if (e == null || e.getWorld() != p.getWorld() || e.getLocation().distanceSquared(p.getLocation()) > eyesRange * eyesRange) {
            return null;
        }
        String t = MyPetBridge.type(pet);
        return t == null ? null : t.toLowerCase(java.util.Locale.ROOT);
    }

    /** Mỗi giây lúc có phiên đánh: hiệu ứng thời tiết cho người trong vùng vây. */
    private void siegeWeather() {
        if (!SiegeWarHook.battleActive()) {
            weatherShown.clear();
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.getGameMode() == org.bukkit.GameMode.CREATIVE
                    || !SiegeWarHook.inActiveSiegeZone(p.getLocation())) {
                weatherShown.remove(p.getUniqueId());
                continue;
            }
            Set<String> cond = conditions(p.getLocation());
            String pet = cond.isEmpty() ? null : petNear(p);
            StringBuilder msg = new StringBuilder();
            if (cond.contains("dem") && nightDark) {
                if (pet != null && eyes.contains(pet)) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 260, 0, true, false, true));
                    msg.append("&b🌙 Đêm: pet mắt đêm soi đường cho bạn. ");
                } else {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, true, false, true));
                    msg.append("&8🌙 Đêm: tầm nhìn giảm. &7Pet mắt đêm (dơi, mèo, cáo, sói, nhện…) soi đường được. ");
                }
            }
            if (cond.contains("mua") && rainSlow) {
                if (p.getVehicle() == null) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, true, false, true));
                    msg.append("&9☔ Mưa: đường lầy, đi bộ chậm (cưỡi pet thì không). ");
                } else {
                    msg.append("&9☔ Mưa: cưỡi pet không bị lầy. ");
                }
            }
            if (cond.contains("nang") && heatHunger) {
                if (pet != null && firePets.contains(pet)) {
                    msg.append("&6☀ Nắng gắt: pet lửa giúp bạn chịu nóng. ");
                } else {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 40, 0, true, false, true));
                    msg.append("&6☀ Nắng gắt mùa khô: mau đói. ");
                }
            }
            String m = msg.toString().trim();
            if (!m.equals(weatherShown.getOrDefault(p.getUniqueId(), ""))) {
                weatherShown.put(p.getUniqueId(), m);
                if (!m.isEmpty()) {
                    p.sendMessage(c(m));
                }
            }
        }
    }

    /** Sát thương của pet trong vùng vây lúc có phiên đánh, theo thời tiết + loài. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPetWeather(EntityDamageByEntityEvent event) {
        if (!weather || !SiegeWarHook.battleActive()) {
            return;
        }
        Entity d = event.getDamager();
        if (d instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) {
            d = shooter;
        }
        if (!isPet(d) || !SiegeWarHook.inActiveSiegeZone(d.getLocation())) {
            return;
        }
        Object pet = MyPetBridge.of(d);
        String t = pet == null ? null : MyPetBridge.type(pet);
        if (t == null) {
            return;
        }
        t = t.toLowerCase(java.util.Locale.ROOT);
        Set<String> cond = conditions(d.getLocation());
        double pct = 0;
        if (cond.contains("dem") && eyes.contains(t)) {
            pct += nightBonus;
        }
        if (cond.contains("mua")) {
            pct += waterPets.contains(t) ? waterBonus : 0;
            pct -= firePets.contains(t) ? fireMalus : 0;
        }
        if (cond.contains("nang") && firePets.contains(t)) {
            pct += heatBonus;
        }
        if (pct != 0) {
            event.setDamage(Math.max(0, event.getDamage() * (100 + pct) / 100.0));
        }
    }

    /** noFlyHeight khối ngay dưới pet đều trống (nhảy thường chỉ cao khoảng 2 khối nên không bị tính). */
    private boolean highAboveGround(Entity e) {
        org.bukkit.block.Block b = e.getLocation().getBlock();
        for (int i = 1; i <= noFlyHeight; i++) {
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
            JoinNotes.add(event.getPlayer(), 20, c("&6⚔ Phiên công thành tới (" + WHEN.format(Instant.ofEpochMilli(next))
                    + "): " + petText(allowedFor(next)) + "&6."));
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
