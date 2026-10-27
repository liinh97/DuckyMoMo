package vn.duckymomo.core.paper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thăng Cấp (/capdo, Bang Hội Chiến; làm lại 21/10 theo hướng cày cuốc). Config level-up:
 *  - max-level cấp. Lên cấp N cần ĐỦ: xu (trả khi lên, rút khỏi kinh tế) = money-base × N^money-exponent; TỔNG giờ chơi thật của mùa
 *    (Activity) >= hours-base × N^hours-exponent; TỔNG điểm hạ gục >= kill-base × N^kill-exponent.
 *  - Điểm hạ gục: hạ quái +mob-points, hạ người +player-points (quái từ lồng không tính; cùng một người chỉ tính 1 lần / player-cooldown-minutes).
 *  - Điểm thuộc tính: points-per-level mỗi cấp + milestone-points mỗi milestone-every cấp (kèm quà milestone-rewards).
 *    4 thuộc tính (attributes.*.per-tier), mỗi cái max-tier bậc, bậc k tốn k điểm: cấp 100 có 110 điểm = max đúng 2 thuộc tính.
 *    Máu (MAX_HEALTH, 1 = nửa tim), Sức mạnh (% sát thương gây ra), Bền bỉ (% sát thương nhận giảm), Nhanh nhẹn (% tốc chạy).
 *    Tắt ở attribute-off-worlds (+ Đấu Trường). Tẩy điểm: respec-cost xu (0 = miễn phí, -1 = không cho).
 *  - Đặc quyền (shop.<mã>: name, icon, description, tiers [{level, cost (xu), perms [quyền LuckPerms vĩnh viễn], rewards}]): mua lần lượt
 *    từng bậc, cần đủ cấp + trả xu. Không trùng rank ủng hộ / danh phận.
 *  - keep-on-new-season: false (mặc định) = khi leaderboard.season-start đổi (mùa mới) thì cấp, thuộc tính, đặc quyền đã mua làm lại từ đầu.
 *  - level-colors "cấp:mã màu, …" (27/10, chủ server): màu của cấp đổi theo mốc (mặc định mỗi 10 cấp), hiện ở TAB, thanh chỉ số,
 *    bảng bên phải, menu (%dmmcap_mau%, %dmmcap_mau_mm%); lên mốc đổi màu thì báo người đó.
 *  - Sức mạnh / Bền bỉ cộng ngay lúc trúng đòn nên không nhìn thấy: %dmmcap_hud_mm% hiện vài giây trên thanh chỉ số sát thương đánh ra
 *    và phần được cộng / được đỡ; menu ghi ví dụ thực tế (đòn 10 -> …, máu tối đa hiện tại…).
 * Hiện %dmmcap_cap% (TAB). Lưu capdo.yml; ghi capdo.json (top) cho web.
 */
final class LevelUp implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final int DATA_VERSION = 2;
    static volatile LevelUp INSTANCE;

    record Tier(int level, double cost, List<String> perms, List<String> rewards) { }

    record Perk(String id, String name, Material icon, List<String> desc, List<Tier> tiers) { }

    /** Thuộc tính: mã lưu, tên, biểu tượng, đơn vị hiện ("%" hoặc tim). */
    enum Attr {
        MAU("mau", "Máu", Material.RED_DYE, "&c"),
        SUC("sucmanh", "Sức mạnh", Material.IRON_SWORD, "&6"),
        BEN("benbi", "Bền bỉ", Material.IRON_CHESTPLATE, "&9"),
        NHANH("nhanhnhen", "Nhanh nhẹn", Material.FEATHER, "&b");

        final String id;
        final String name;
        final Material icon;
        final String color;

        Attr(String id, String name, Material icon, String color) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.color = color;
        }
    }

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        String view = "main";
        final Map<Integer, String> slots = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final JavaPlugin plugin;
    private final Activity activity;
    private final Rewards rewards;
    private final int maxLevel;
    private final double moneyBase;
    private final double moneyExp;
    private final double hoursBase;
    private final double hoursExp;
    private final double killBase;
    private final double killExp;
    private final int mobPoints;
    private final int playerPoints;
    private final long playerCdMs;
    private final int pointsPerLevel;
    private final int milestoneEvery;
    private final int milestonePoints;
    private final List<String> milestoneRewards;
    private final int maxTier;
    private final Map<Attr, Double> perTier = new HashMap<>();
    private final Set<String> offWorlds = new HashSet<>();
    private final double respecCost;
    private final boolean keep;
    private final Map<String, Perk> shop = new LinkedHashMap<>();
    private final NamespacedKey hpKey;
    private final NamespacedKey speedKey;
    private final File file;
    private final YamlConfiguration data;
    private final Map<String, Long> lastVictim = new HashMap<>();
    private boolean dirty;
    static final String DEFAULT_COLORS = "0:&f, 10:&a, 20:&b, 30:&9, 40:&d, 50:&6, 60:&c, 70:&5, 80:&4, 90:&e, 100:&e&l";
    private final TreeMap<Integer, String> colors = new TreeMap<>();
    private static final long HUD_MS = 3000;
    /** Đòn gần nhất có Sức mạnh (đánh) / Bền bỉ (bị đánh): {thời điểm ms, sát thương, phần thuộc tính cộng / đỡ}. */
    private final Map<UUID, double[]> lastHit = new ConcurrentHashMap<>();
    private final Map<UUID, double[]> lastHurt = new ConcurrentHashMap<>();
    private static final Map<Character, String> MM = Map.ofEntries(Map.entry('0', "black"), Map.entry('1', "dark_blue"),
            Map.entry('2', "dark_green"), Map.entry('3', "dark_aqua"), Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"), Map.entry('8', "dark_gray"), Map.entry('9', "blue"), Map.entry('a', "green"),
            Map.entry('b', "aqua"), Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"), Map.entry('f', "white"),
            Map.entry('l', "bold"), Map.entry('o', "italic"), Map.entry('n', "underlined"), Map.entry('m', "strikethrough"));

    LevelUp(JavaPlugin plugin, Activity activity, Rewards rewards, ConfigurationSection c) {
        this.plugin = plugin;
        this.activity = activity;
        this.rewards = rewards;
        this.maxLevel = Math.max(1, c.getInt("max-level", 100));
        this.moneyBase = Math.max(0, c.getDouble("money-base", 500));
        this.moneyExp = Math.max(0, c.getDouble("money-exponent", 0.6));
        this.hoursBase = Math.max(0, c.getDouble("hours-base", 0.04));
        this.hoursExp = Math.max(0, c.getDouble("hours-exponent", 2));
        this.killBase = Math.max(0, c.getDouble("kill-base", 2));
        this.killExp = Math.max(0, c.getDouble("kill-exponent", 2));
        this.mobPoints = Math.max(0, c.getInt("mob-points", 1));
        this.playerPoints = Math.max(0, c.getInt("player-points", 10));
        this.playerCdMs = Math.max(0, c.getLong("player-cooldown-minutes", 10)) * 60_000L;
        this.pointsPerLevel = Math.max(0, c.getInt("points-per-level", 1));
        this.milestoneEvery = Math.max(0, c.getInt("milestone-every", 10));
        this.milestonePoints = Math.max(0, c.getInt("milestone-points", 1));
        this.milestoneRewards = c.getStringList("milestone-rewards");
        this.maxTier = Math.max(1, c.getInt("attribute-max-tier", 10));
        for (Attr a : Attr.values()) {
            perTier.put(a, Math.max(0, c.getDouble("attributes." + a.id, 1)));
        }
        offWorlds.addAll(c.getStringList("attribute-off-worlds"));
        this.respecCost = c.getDouble("respec-cost", 20000);
        this.keep = c.getBoolean("keep-on-new-season", false);
        for (String part : c.getString("level-colors", DEFAULT_COLORS).split(",")) {
            String[] kv = part.trim().split(":", 2);
            try {
                if (kv.length == 2 && !kv[1].isBlank()) {
                    colors.put(Integer.parseInt(kv[0].trim()), kv[1].trim());
                }
            } catch (NumberFormatException ignored) {
                // bỏ mục sai, giữ phần còn lại
            }
        }
        ConfigurationSection ss = c.getConfigurationSection("shop");
        if (ss != null) {
            for (String id : ss.getKeys(false)) {
                ConfigurationSection p = ss.getConfigurationSection(id);
                if (p == null || !p.getBoolean("enabled", true)) {
                    continue;
                }
                List<Tier> tiers = new ArrayList<>();
                for (Map<?, ?> m : p.getMapList("tiers")) {
                    tiers.add(new Tier(num(m.get("level"), 0).intValue(), num(m.get("cost"), 0).doubleValue(), strs(m.get("perms")), strs(m.get("rewards"))));
                }
                if (tiers.isEmpty()) {
                    continue;
                }
                Material m = Material.matchMaterial(p.getString("icon", "PAPER"));
                shop.put(id, new Perk(id, p.getString("name", id), m == null ? Material.PAPER : m, p.getStringList("description"), tiers));
            }
        }
        this.hpKey = new NamespacedKey(plugin, "capdo_mau");
        this.speedKey = new NamespacedKey(plugin, "capdo_nhanh");
        this.file = new File(plugin.getDataFolder(), "capdo.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        Shared.FORGET.add(id -> {   // admin reset "như người mới": xoá cả khỏi bảng xếp hạng trên web
            // 22/10: thu hồi luôn quyền của đặc quyền đã mua (giảm giá Xưởng, bán thêm…; trước đó còn sót sau reset)
            revoke(id, shop.values().stream().flatMap(pk -> pk.tiers().stream()).flatMap(t -> t.perms().stream()).distinct().toList());
            Shared.forget(data, file, id);
            writeJson();
        });
    }

    private static Number num(Object o, Number def) {
        if (o instanceof Number n) {
            return n;
        }
        try {
            return o == null ? def : Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static List<String> strs(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            l.forEach(x -> out.add(String.valueOf(x)));
        } else if (o != null) {
            out.add(o.toString());
        }
        return out;
    }

    String describe() {
        return maxLevel + " cấp, " + totalPoints(maxLevel) + " điểm thuộc tính ở cấp tối đa, " + shop.size() + " đặc quyền, quái +" + mobPoints
                + " / người +" + playerPoints + (keep ? ", giữ qua mùa" : ", làm lại mỗi mùa");
    }

    void start() {
        INSTANCE = this;
        migrate();
        seasonCheck();
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) {
                save();
                writeJson();
            }
        }, 1200L, 1200L);
        Bukkit.getOnlinePlayers().forEach(this::applyStats);   // dmm reload
        writeJson();
    }

    void stop() {
        INSTANCE = null;
        Bukkit.getOnlinePlayers().forEach(this::clearStats);
        save();
    }

    // ---------------------------------------------------------------- số liệu

    private String k(UUID u) {
        return "nguoi." + u + ".";
    }

    int level(UUID u) {
        return data.getInt(k(u) + "cap", 0);
    }

    static int levelOf(UUID u) {
        LevelUp l = INSTANCE;
        return l == null ? 0 : l.level(u);
    }

    /** Mã màu & của cấp lv (mốc gần nhất ≤ lv trong level-colors). */
    static String colorOf(int lv) {
        LevelUp l = INSTANCE;
        Map.Entry<Integer, String> e = l == null ? null : l.colors.floorEntry(lv);
        return e == null ? "&f" : e.getValue();
    }

    /** "&a&l" -> "<green><bold>" (thanh chỉ số AuraSkills đọc MiniMessage). */
    static String miniMessage(String legacy) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i + 1 < legacy.length(); i++) {
            char ch = legacy.charAt(i);
            if (ch != '&' && ch != '§') {
                continue;
            }
            String t = MM.get(Character.toLowerCase(legacy.charAt(i + 1)));
            if (t != null) {
                b.append('<').append(t).append('>');
            }
            i++;
        }
        return b.toString();
    }

    /** Vài giây sau đòn có Sức mạnh / Bền bỉ: " | ⚔ 7,7 (+0,7)", " | đỡ −0,5" (mã §, gắn vào thanh chỉ số: AuraSkills chèn
     *  placeholder như chữ thường SAU khi đọc MiniMessage nên thẻ <gold> / mã & hiện nguyên chữ, còn § thì game vẫn tô màu; 28/10). */
    static String hud(UUID u) {
        LevelUp l = INSTANCE;
        if (l == null) {
            return "";
        }
        long now = System.currentTimeMillis();
        StringBuilder b = new StringBuilder();
        double[] h = l.lastHit.get(u);
        if (h != null && now - (long) h[0] < HUD_MS) {
            b.append(" §8| §6⚔ ").append(dmg(h[1])).append(" §7(+").append(dmg(h[2])).append(" Sức mạnh)");
        }
        double[] d = l.lastHurt.get(u);
        if (d != null && now - (long) d[0] < HUD_MS) {
            b.append(" §8| §9Bền bỉ đỡ −").append(dmg(d[2]));
        }
        return b.toString();
    }

    private static String dmg(double v) {
        return String.format(Locale.ROOT, v < 1 ? "%.2f" : "%.1f", v).replace('.', ',');
    }

    /** Tổng điểm thuộc tính có được ở cấp lv. */
    int totalPoints(int lv) {
        return lv * pointsPerLevel + (milestoneEvery > 0 ? lv / milestoneEvery * milestonePoints : 0);
    }

    int tier(UUID u, Attr a) {
        return data.getInt(k(u) + "thuoctinh." + a.id, 0);
    }

    /** Điểm đã dùng: bậc t tốn 1 + 2 + … + t. */
    private int spent(UUID u) {
        int s = 0;
        for (Attr a : Attr.values()) {
            int t = tier(u, a);
            s += t * (t + 1) / 2;
        }
        return s;
    }

    int freePoints(UUID u) {
        return Math.max(0, totalPoints(level(u)) - spent(u));
    }

    private int killPts(UUID u) {
        return data.getInt(k(u) + "ha", 0);
    }

    double moneyFor(int lv) {
        return Math.round(moneyBase * Math.pow(lv, moneyExp));
    }

    private double hoursFor(int lv) {
        return hoursBase * Math.pow(lv, hoursExp);
    }

    private int killsFor(int lv) {
        return (int) Math.round(killBase * Math.pow(lv, killExp));
    }

    private double hours(UUID u) {
        return activity == null ? 0 : activity.totalHours(u);
    }

    /** Số bậc đặc quyền đã mua. */
    private int owned(UUID u, String perk) {
        return data.getInt(k(u) + "dacquyen." + perk, 0);
    }

    /** Bản cũ (cửa hàng đổi điểm /craft /anvil…): gỡ quyền đã cấp, xoá điểm/đồ đã mua; cấp và điểm hạ gục giữ. */
    private void migrate() {
        if (data.getInt("phienban", 1) >= DATA_VERSION) {
            return;
        }
        ConfigurationSection ps = data.getConfigurationSection("nguoi");
        if (ps != null) {
            for (String id : ps.getKeys(false)) {
                revoke(UUID.fromString(id), data.getStringList("nguoi." + id + ".quyen"));
                data.set("nguoi." + id + ".quyen", null);
                data.set("nguoi." + id + ".mua", null);
                data.set("nguoi." + id + ".diem", null);
            }
        }
        data.set("phienban", DATA_VERSION);
        dirty = true;
        save();
        plugin.getLogger().info("Thăng Cấp: đã chuyển sang bản thuộc tính + đặc quyền xu (gỡ cửa hàng điểm cũ).");
    }

    private void revoke(UUID u, List<String> perms) {
        if (!perms.isEmpty()) {
            LuckPermsProvider.get().getUserManager().modifyUser(u, user -> perms.forEach(pn -> user.data().remove(PermissionNode.builder(pn).build())));
        }
    }

    /** Mùa mới (leaderboard.season-start đổi) và không giữ cấp: làm lại cấp, thuộc tính, gỡ đặc quyền đã mua. */
    private void seasonCheck() {
        String season = plugin.getConfig().getString("leaderboard.season-start", "");
        String seen = data.getString("mua", null);
        if (seen == null) {
            data.set("mua", season);
            dirty = true;
            return;
        }
        if (seen.equals(season) || keep) {
            if (!seen.equals(season)) {
                data.set("mua", season);
                dirty = true;
            }
            return;
        }
        ConfigurationSection ps = data.getConfigurationSection("nguoi");
        int n = 0;
        if (ps != null) {
            for (String id : ps.getKeys(false)) {
                revoke(UUID.fromString(id), data.getStringList("nguoi." + id + ".quyen"));
                n++;
            }
        }
        data.set("nguoi", null);
        data.set("mua", season);
        dirty = true;
        save();
        plugin.getLogger().info("Thăng Cấp: mùa mới (" + season + ") → làm lại cấp của " + n + " người.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        LivingEntity v = e.getEntity();
        if (killer == null || v == killer) {
            return;
        }
        int add;
        if (v instanceof Player vp) {
            String key = killer.getUniqueId() + ">" + vp.getUniqueId();
            long now = System.currentTimeMillis();
            if (now - lastVictim.getOrDefault(key, 0L) < playerCdMs) {
                return;   // giết đi giết lại một người: không tính
            }
            lastVictim.put(key, now);
            add = playerPoints;
        } else {
            if (v.getEntitySpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER || v.getEntitySpawnReason() == CreatureSpawnEvent.SpawnReason.TRIAL_SPAWNER) {
                return;
            }
            add = mobPoints;
        }
        if (add <= 0) {
            return;
        }
        UUID u = killer.getUniqueId();
        data.set(k(u) + "ha", killPts(u) + add);
        data.set(k(u) + "ten", killer.getName());
        dirty = true;
    }

    // ---------------------------------------------------------------- thuộc tính trong game

    private boolean statsOff(Player p) {
        String w = p.getWorld().getName();
        return w.equals(Arena.WORLD) || offWorlds.contains(w);
    }

    private double bonus(Player p, Attr a) {
        return statsOff(p) ? 0 : tier(p.getUniqueId(), a) * perTier.get(a);
    }

    /** Gắn máu + tốc chạy (modifier tạm, không lưu vào file nhân vật: tắt plugin là mất). */
    void applyStats(Player p) {
        clearStats(p);
        double hp = bonus(p, Attr.MAU);
        double sp = bonus(p, Attr.NHANH) / 100.0;
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        if (max != null && hp > 0) {
            max.addTransientModifier(new AttributeModifier(hpKey, hp, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        }
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null && sp > 0) {
            speed.addTransientModifier(new AttributeModifier(speedKey, sp, AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.ANY));
        }
    }

    private void clearStats(Player p) {
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            max.removeModifier(hpKey);
            if (p.getHealth() > max.getValue()) {
                p.setHealth(max.getValue());
            }
        }
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(speedKey);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                applyStats(p);
            }
        }, 5L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                applyStats(p);
            }
        }, 1L);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        applyStats(e.getPlayer());
    }

    /** Sức mạnh: +% sát thương gây ra (cận chiến + bắn). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Player p = e.getDamager() instanceof Player pl ? pl
                : e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player pl ? pl : null;
        if (p == null || e.getEntity() == p) {
            return;
        }
        double b = bonus(p, Attr.SUC);
        if (b > 0) {
            double base = e.getDamage(), d = base * (1 + b / 100.0);
            e.setDamage(d);
            lastHit.put(p.getUniqueId(), new double[] {System.currentTimeMillis(), d, d - base});
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastHit.remove(e.getPlayer().getUniqueId());
        lastHurt.remove(e.getPlayer().getUniqueId());
    }

    /** Bền bỉ: −% sát thương nhận (trừ rơi khỏi thế giới, /kill, viền thế giới). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) {
            return;
        }
        switch (e.getCause()) {
            case VOID, KILL, WORLD_BORDER -> {
                return;
            }
            default -> { }
        }
        double b = Math.min(90, bonus(p, Attr.BEN));
        if (b > 0) {
            double base = e.getDamage(), d = base * (1 - b / 100.0);
            e.setDamage(d);
            lastHurt.put(p.getUniqueId(), new double[] {System.currentTimeMillis(), d, base - d});
        }
    }

    // ---------------------------------------------------------------- thăng cấp, thuộc tính, đặc quyền

    private boolean ready(Player p, int next) {
        return next <= maxLevel && VaultEconomy.available() && VaultEconomy.has(p, moneyFor(next))
                && hours(p.getUniqueId()) >= hoursFor(next) && killPts(p.getUniqueId()) >= killsFor(next);
    }

    private void levelUp(Player p) {
        UUID u = p.getUniqueId();
        int next = level(u) + 1;
        if (next > maxLevel) {
            p.sendMessage(c("&6Bạn đã đạt cấp tối đa (" + maxLevel + ")."));
            return;
        }
        if (!ready(p, next)) {
            p.sendMessage(c("&cChưa đủ điều kiện lên cấp " + next + ". Xem trong &f/capdo&c."));
            return;
        }
        if (!VaultEconomy.withdraw(p, moneyFor(next))) {
            p.sendMessage(c("&cKhông trừ được tiền."));
            return;
        }
        int gain = totalPoints(next) - totalPoints(next - 1);
        data.set(k(u) + "cap", next);
        data.set(k(u) + "ten", p.getName());
        dirty = true;
        save();
        writeJson();
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.1f);
        DailyQuests.feature(p, "capdo");
        p.sendMessage(c("&a⬆ Lên cấp " + next + "! &7+" + gain + " điểm thuộc tính (đang có " + freePoints(u) + "). Cộng điểm: &f/capdo"));
        if (!colorOf(next).equals(colorOf(next - 1))) {
            p.sendMessage(c("&e✦ Cấp của bạn đổi màu: " + colorOf(next) + "Cấp " + next + " &7(ở Tab, thanh chỉ số, bảng bên phải)"));
        }
        if (milestoneEvery > 0 && next % milestoneEvery == 0) {
            List<String> got = rewards == null ? List.of() : rewards.give(p, milestoneRewards);
            PlayerSettings.broadcast("sukien", c("&6⬆ " + p.getName() + " &eđã đạt &6Cấp " + next + "&e!" + (got.isEmpty() ? "" : " &7Quà mốc: &f" + String.join(", ", got))));
        }
    }

    private void raise(Player p, Attr a) {
        UUID u = p.getUniqueId();
        int t = tier(u, a);
        if (t >= maxTier) {
            p.sendMessage(c("&7" + a.name + " đã tối đa (bậc " + maxTier + ")."));
            return;
        }
        int cost = t + 1;
        if (freePoints(u) < cost) {
            p.sendMessage(c("&cBậc " + cost + " cần " + cost + " điểm thuộc tính (đang có " + freePoints(u) + "). Lên cấp để có thêm."));
            return;
        }
        data.set(k(u) + "thuoctinh." + a.id, t + 1);
        data.set(k(u) + "ten", p.getName());
        dirty = true;
        save();
        writeJson();   // trang tài khoản đọc "điểm chưa cộng" ở capdo.json (22/10: trước giữ số cũ tới lần lên cấp sau)
        applyStats(p);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        p.sendMessage(c("&a✔ " + a.color + a.name + " &abậc " + (t + 1) + ": &f" + effect(a, t + 1) + (statsOff(p) ? " &8(tắt ở thế giới này)" : "")));
    }

    private void respec(Player p) {
        UUID u = p.getUniqueId();
        if (respecCost < 0) {
            return;
        }
        if (spent(u) == 0) {
            p.sendMessage(c("&7Bạn chưa cộng điểm nào."));
            return;
        }
        if (respecCost > 0 && (!VaultEconomy.available() || !VaultEconomy.withdraw(p, respecCost))) {
            p.sendMessage(c("&cTẩy điểm cần " + money(respecCost) + "."));
            return;
        }
        data.set(k(u) + "thuoctinh", null);
        dirty = true;
        save();
        writeJson();
        applyStats(p);
        p.sendMessage(c("&a✔ Đã tẩy điểm: nhận lại " + freePoints(u) + " điểm thuộc tính" + (respecCost > 0 ? " &7(−" + money(respecCost) + ")" : "") + "."));
    }

    private void buy(Player p, Perk perk) {
        UUID u = p.getUniqueId();
        int have = owned(u, perk.id());
        if (have >= perk.tiers().size()) {
            p.sendMessage(c("&7Bạn đã có đủ &f" + perk.name() + "&7."));
            return;
        }
        Tier t = perk.tiers().get(have);
        if (level(u) < t.level()) {
            p.sendMessage(c("&cCần Thăng Cấp " + t.level() + " (bạn đang cấp " + level(u) + ")."));
            return;
        }
        if (t.cost() > 0 && (!VaultEconomy.available() || !VaultEconomy.withdraw(p, t.cost()))) {
            p.sendMessage(c("&cCần " + money(t.cost()) + "."));
            return;
        }
        data.set(k(u) + "dacquyen." + perk.id(), have + 1);
        if (!t.perms().isEmpty()) {
            List<String> granted = new ArrayList<>(data.getStringList(k(u) + "quyen"));
            granted.addAll(t.perms());
            data.set(k(u) + "quyen", granted);
            LuckPermsProvider.get().getUserManager().modifyUser(u, user -> t.perms().forEach(pn -> user.data().add(PermissionNode.builder(pn).value(true).build())));
        }
        if (!t.rewards().isEmpty() && rewards != null) {
            rewards.give(p, t.rewards());
        }
        dirty = true;
        save();
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
        p.sendMessage(c("&a✔ Đã mua &f" + perk.name() + (perk.tiers().size() > 1 ? " &7bậc " + (have + 1) : "") + "&a (−" + money(t.cost()) + ")."));
    }

    // ---------------------------------------------------------------- menu

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    private static String money(double v) {
        return VaultEconomy.available() ? VaultEconomy.format(v) : String.format(Locale.ROOT, "%,.0f xu", v);
    }

    private String mark(boolean ok) {
        return ok ? "&a" : "&c";
    }

    private static String num(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v).replace('.', ',');
    }

    /** 12.500 (dấu chấm ngăn nghìn). */
    private static String count(long v) {
        return String.format(Locale.ROOT, "%,d", v).replace(',', '.');
    }

    /** Giờ chơi: dưới 1 giờ ghi phút ("3 phút"), còn lại "4,5 giờ". need = mức yêu cầu (làm tròn lên), không thì làm tròn xuống. */
    private static String time(double h, boolean need) {
        if (h < 1) {
            return (long) (need ? Math.ceil(h * 60) : Math.floor(h * 60)) + " phút";
        }
        return num((need ? Math.ceil(h * 10) : Math.floor(h * 10)) / 10) + " giờ";
    }

    /** Hiệu lực của thuộc tính ở bậc t, vd "+3 tim", "+4% sát thương". */
    private String effect(Attr a, int t) {
        double v = t * perTier.get(a);
        return switch (a) {
            case MAU -> "+" + num(v / 2) + " tim";
            case SUC -> "+" + num(v) + "% sát thương gây ra";
            case BEN -> "−" + num(v) + "% sát thương nhận";
            case NHANH -> "+" + num(v) + "% tốc chạy";
        };
    }

    /** Hiệu lực nhìn được: máu tối đa / tốc chạy đọc từ chỉ số thật của nhân vật, sức mạnh / bền bỉ quy ra một đòn 10 sát thương. */
    private String real(Player p, Attr a, int t) {
        double v = t * perTier.get(a);
        return switch (a) {
            case MAU -> {
                AttributeInstance m = p.getAttribute(Attribute.MAX_HEALTH);
                yield "&fmáu tối đa " + num((m == null ? 20 : m.getValue()) / 2) + " tim";
            }
            case SUC -> "&fđòn 10 sát thương thành " + num(Math.round(10 * (1 + v / 100.0) * 10) / 10.0);
            case BEN -> "&fbị đánh 10 chỉ mất " + num(Math.round(10 * (1 - Math.min(90, v) / 100.0) * 10) / 10.0);
            case NHANH -> {
                AttributeInstance s = p.getAttribute(Attribute.MOVEMENT_SPEED);
                yield "&ftốc chạy " + (s == null || s.getBaseValue() <= 0 ? "+" + num(v) : num(Math.round(s.getValue() / s.getBaseValue() * 100))) + "%";
            }
        };
    }

    private static String bar(int t, int max, String color) {
        StringBuilder b = new StringBuilder(color);
        for (int i = 0; i < max; i++) {
            if (i == t) {
                b.append("&8");
            }
            b.append('▮');
        }
        return b.toString();
    }

    void open(Player p) {
        UUID u = p.getUniqueId();
        int lv = level(u), next = lv + 1;
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 45, c("&0⬆ Thăng Cấp &8· cấp " + lv + "/" + maxLevel));
        Integer nextColor = colors.higherKey(lv);
        h.inv.setItem(4, Tip.of("&6⬆ " + colorOf(lv) + "Cấp " + lv).sub("tối đa " + maxLevel).desc("Tiến độ của bạn trong mùa này").line()
                .kv("Màu cấp", colorOf(lv) + "■■■ &8" + (nextColor == null ? "(màu cao nhất)"
                        : "· cấp " + nextColor + " đổi sang " + colors.get(nextColor) + "■■■"))
                .kv("Điểm thuộc tính", "&e" + freePoints(u) + " &7chưa dùng &8(" + totalPoints(lv) + " tổng)")
                .kv("Điểm hạ gục", count(killPts(u)) + " &8(quái +" + mobPoints + ", người +" + playerPoints + ")")
                .kv("Giờ chơi", time(hours(u), false))
                .gap().note("Mỗi cấp +" + pointsPerLevel + " điểm, mỗi " + milestoneEvery + " cấp thưởng thêm " + milestonePoints
                        + ". Cấp " + maxLevel + " có " + totalPoints(maxLevel) + " điểm: đủ max " + totalPoints(maxLevel) / (maxTier * (maxTier + 1) / 2) + " thuộc tính.")
                .apply(new ItemStack(Material.NETHER_STAR)));
        if (next <= maxLevel) {
            boolean mOk = VaultEconomy.available() && VaultEconomy.has(p, moneyFor(next));
            boolean hOk = hours(u) >= hoursFor(next), kOk = killPts(u) >= killsFor(next);
            boolean all = mOk && hOk && kOk;
            Tip t = Tip.of((all ? "&a⬆ " : "&e⬆ ") + "&fLên cấp " + next).accent(all ? "&a" : "&e").head("Điều kiện")
                    .item(mark(mOk) + money(VaultEconomy.available() ? VaultEconomy.balance(p) : 0) + "&7/" + money(moneyFor(next)) + " &8(trả khi lên)")
                    .item(mark(kOk) + count(killPts(u)) + "&7/" + count(killsFor(next)) + " điểm hạ gục &8(tổng)")
                    .item(mark(hOk) + time(hours(u), false) + "&7/" + time(hoursFor(next), true) + " chơi &8(tổng trong mùa)")
                    .gap().head("Thưởng").item("&e+" + (totalPoints(next) - totalPoints(lv)) + " điểm thuộc tính");
            if (milestoneEvery > 0 && next % milestoneEvery == 0) {
                t.item("&6Quà mốc cấp " + next);
            }
            t.line();
            if (all) {
                t.click("Bấm để lên cấp");
            } else {
                t.deny("Chưa đủ điều kiện");
            }
            h.inv.setItem(13, t.apply(new ItemStack(all ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE)));
            h.slots.put(13, "len");
        } else {
            h.inv.setItem(13, Tip.of("&6✦ &fĐã đạt cấp tối đa").desc("Mùa sau làm lại từ đầu").apply(new ItemStack(Material.GOLD_BLOCK)));
        }
        int[] aslots = {19, 21, 23, 25};
        Attr[] attrs = Attr.values();
        boolean off = statsOff(p);
        for (int i = 0; i < attrs.length; i++) {
            Attr a = attrs[i];
            int t = tier(u, a);
            Tip tip = Tip.of(a.color + "✦ &f" + a.name).sub("bậc " + t + "/" + maxTier).accent(a.color)
                    .desc(switch (a) {
                        case MAU -> "Thêm máu tối đa";
                        case SUC -> "Đánh mạnh hơn (cận chiến và bắn)";
                        case BEN -> "Nhận ít sát thương hơn";
                        case NHANH -> "Chạy nhanh hơn";
                    }).raw(bar(t, maxTier, a.color)).line()
                    .kv("Đang có", t == 0 ? "&7chưa có" : "&f" + effect(a, t));
            if (t > 0 && !off) {
                tip.kv("Thực tế", real(p, a, t));
                if (a == Attr.SUC || a == Attr.BEN) {
                    tip.note("Đánh / bị đánh: thanh chỉ số dưới màn hình hiện phần " + a.name + " vài giây");
                }
            }
            if (t < maxTier) {
                boolean can = freePoints(u) >= t + 1;
                tip.kv("Bậc " + (t + 1), "&f" + effect(a, t + 1) + " &8· " + (can ? "&e" : "&c") + (t + 1) + " điểm").line();
                if (can) {
                    tip.click("Bấm để nâng");
                } else {
                    tip.deny("Chưa đủ điểm thuộc tính");
                }
            } else {
                tip.kv("Trạng thái", "&aĐã tối đa");
            }
            if (off) {
                tip.note("Đang tắt ở thế giới này (Đấu Trường công bằng)");
            }
            h.inv.setItem(aslots[i], tip.apply(new ItemStack(a.icon)));
            h.slots.put(aslots[i], "tt:" + a.name());
        }
        if (respecCost >= 0) {
            h.inv.setItem(38, Tip.of("&c↺ &fTẩy điểm").desc("Trả lại toàn bộ điểm thuộc tính để cộng lại từ đầu").line()
                    .kv("Giá", respecCost > 0 ? "&e" + money(respecCost) : "&aMiễn phí").line()
                    .click("Shift + bấm", "tẩy điểm").apply(new ItemStack(Material.WATER_BUCKET)));
            h.slots.put(38, "tay");
        }
        h.inv.setItem(40, Tip.of("&e⛃ &fĐặc quyền").desc("Mua bằng xu, cần đủ cấp: giảm giá Xưởng, bán cho server được nhiều hơn, cộng kinh nghiệm…")
                .line().click("Bấm để mở").apply(new ItemStack(Material.CHEST)));
        h.slots.put(40, "shop");
        h.inv.setItem(42, Tip.of("&b♛ &fXếp hạng").desc("Những người cấp cao nhất server").line().click("Bấm để xem").apply(new ItemStack(Material.GOLD_INGOT)));
        h.slots.put(42, "top");
        p.openInventory(h.inv);
    }

    private void openShop(Player p) {
        UUID u = p.getUniqueId();
        Holder h = new Holder();
        h.view = "shop";
        h.inv = Bukkit.createInventory(h, 45, c("&0⛃ Đặc quyền Thăng Cấp &8· cấp " + level(u)));
        int slot = 10;
        for (Perk pk : shop.values()) {
            if (slot > 34) {
                break;
            }
            if (slot % 9 == 8) {
                slot += 2;
            }
            int have = owned(u, pk.id());
            int n = pk.tiers().size();
            boolean full = have >= n;
            Tip t = Tip.of((full ? "&a✔ " : "&e✦ ") + "&f" + pk.name()).desc(pk.desc().toArray(String[]::new));
            if (n > 1) {
                t.raw(bar(have, n, "&a"));
            }
            t.line();
            if (n > 1) {
                t.head("Các bậc");
                for (int i = 0; i < n; i++) {
                    Tier ti = pk.tiers().get(i);
                    t.item((i < have ? "&a✔ " : "&7") + "Bậc " + (i + 1) + ": cấp " + ti.level() + ", " + money(ti.cost()));
                }
                t.gap();
            }
            if (full) {
                t.kv("Trạng thái", "&aĐã có" + (n > 1 ? " đủ " + n + " bậc" : ""));
            } else {
                Tier ti = pk.tiers().get(have);
                boolean lvOk = level(u) >= ti.level();
                boolean mOk = VaultEconomy.available() && VaultEconomy.has(p, ti.cost());
                t.head(n > 1 ? "Mua bậc " + (have + 1) : "Điều kiện")
                        .item(mark(lvOk) + "Thăng Cấp " + ti.level() + " &8(đang " + level(u) + ")")
                        .item(mark(mOk) + money(ti.cost())).line();
                if (lvOk && mOk) {
                    t.click("Bấm để mua");
                } else {
                    t.deny(lvOk ? "Chưa đủ xu" : "Chưa đủ cấp");
                }
            }
            h.inv.setItem(slot, t.apply(new ItemStack(pk.icon())));
            h.slots.put(slot, "mua:" + pk.id());
            slot++;
        }
        h.inv.setItem(40, Tip.of("&7← &fQuay lại").apply(new ItemStack(Material.ARROW)));
        h.slots.put(40, "back");
        p.openInventory(h.inv);
    }

    private List<Map.Entry<String, Integer>> ranking() {
        List<Map.Entry<String, Integer>> out = new ArrayList<>();
        ConfigurationSection ps = data.getConfigurationSection("nguoi");
        if (ps != null) {
            for (String id : ps.getKeys(false)) {
                int lv = data.getInt("nguoi." + id + ".cap", 0);
                if (lv > 0) {
                    out.add(Map.entry(id, lv));
                }
            }
        }
        out.sort((a, b) -> b.getValue() != a.getValue().intValue() ? b.getValue() - a.getValue()
                : data.getInt("nguoi." + b.getKey() + ".ha", 0) - data.getInt("nguoi." + a.getKey() + ".ha", 0));
        return out;
    }

    private void openTop(Player p) {
        Holder h = new Holder();
        h.view = "top";
        h.inv = Bukkit.createInventory(h, 45, c("&0♛ Xếp hạng Thăng Cấp"));
        int[] slots = {4, 12, 14, 20, 21, 22, 23, 24, 30, 32};
        List<Map.Entry<String, Integer>> top = ranking();
        for (int i = 0; i < Math.min(slots.length, top.size()); i++) {
            var e = top.get(i);
            UUID id = UUID.fromString(e.getKey());
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setOwningPlayer(op);
            sm.displayName(c("&e[#" + (i + 1) + "] &f" + data.getString("nguoi." + e.getKey() + ".ten", op.getName())));
            Tip t = Tip.of("").kv("Cấp", "&b" + e.getValue() + "/" + maxLevel).kv("Điểm hạ gục", killPts(id) + "");
            for (Attr a : Attr.values()) {
                int ti = tier(id, a);
                if (ti > 0) {
                    t.kv(a.name, a.color + "bậc " + ti);
                }
            }
            sm.lore(t.lore());
            head.setItemMeta(sm);
            h.inv.setItem(slots[i], head);
        }
        h.inv.setItem(40, Tip.of("&7← &fQuay lại").apply(new ItemStack(Material.ARROW)));
        h.slots.put(40, "back");
        p.openInventory(h.inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        String a = h.slots.get(e.getRawSlot());
        if (a == null || !(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        switch (a) {
            case "len" -> {
                levelUp(p);
                open(p);
            }
            case "tay" -> {
                if (!e.isShiftClick()) {
                    p.sendMessage(c("&7Giữ &fShift &7rồi bấm để tẩy điểm (tránh bấm nhầm)."));
                    return;
                }
                respec(p);
                open(p);
            }
            case "shop" -> openShop(p);
            case "top" -> openTop(p);
            case "back" -> open(p);
            default -> {
                if (a.startsWith("tt:")) {
                    raise(p, Attr.valueOf(a.substring(3)));
                    open(p);
                } else if (a.startsWith("mua:")) {
                    Perk pk = shop.get(a.substring(4));
                    if (pk != null) {
                        buy(p, pk);
                        openShop(p);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    boolean command(CommandSender s, String[] args) {
        if (args.length >= 3 && args[0].equalsIgnoreCase("dat") && s.hasPermission("duckymomo.admin")) {
            OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
            try {
                UUID u = t.getUniqueId();
                data.set(k(u) + "cap", Math.max(0, Math.min(maxLevel, Integer.parseInt(args[2]))));
                data.set(k(u) + "ten", t.getName());
                if (spent(u) > totalPoints(level(u))) {
                    data.set(k(u) + "thuoctinh", null);   // hạ cấp: điểm đã cộng vượt quá thì trả lại hết
                }
                dirty = true;
                save();
                writeJson();
                if (t.getPlayer() != null) {
                    applyStats(t.getPlayer());
                }
                s.sendMessage("Đã đặt cấp " + args[2] + " cho " + t.getName() + " (" + freePoints(u) + " điểm thuộc tính chưa dùng)");
            } catch (NumberFormatException ex) {
                s.sendMessage("/capdo dat <tên> <cấp>");
            }
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("top")) {
            s.sendMessage(c("&6Xếp hạng Thăng Cấp"));
            List<Map.Entry<String, Integer>> top = ranking();
            for (int i = 0; i < Math.min(10, top.size()); i++) {
                s.sendMessage(c("&e#" + (i + 1) + " &f" + data.getString("nguoi." + top.get(i).getKey() + ".ten", "?") + " &7— cấp &b" + top.get(i).getValue()));
            }
            return true;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("/capdo top | /capdo dat <tên> <cấp>");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("len")) {
            levelUp(p);
            return true;
        }
        open(p);
        return true;
    }

    // ---------------------------------------------------------------- lưu + web

    private void save() {
        try {
            data.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được capdo.yml: " + e.getMessage());
        }
    }

    private void writeJson() {
        JsonObject root = new JsonObject();
        root.addProperty("toi_da", maxLevel);
        JsonArray arr = new JsonArray();
        List<Map.Entry<String, Integer>> top = ranking();
        for (int i = 0; i < Math.min(10, top.size()); i++) {
            JsonObject o = new JsonObject();
            o.addProperty("ten", data.getString("nguoi." + top.get(i).getKey() + ".ten", "?"));
            o.addProperty("cap", top.get(i).getValue());
            arr.add(o);
        }
        root.add("top", arr);
        JsonObject nguoi = new JsonObject();
        ConfigurationSection ps = data.getConfigurationSection("nguoi");
        if (ps != null) {
            for (String id : ps.getKeys(false)) {
                UUID u = UUID.fromString(id);
                JsonObject o = new JsonObject();
                o.addProperty("cap", level(u));
                o.addProperty("diem", freePoints(u));
                o.addProperty("ha", killPts(u));
                JsonObject tt = new JsonObject();
                for (Attr a : Attr.values()) {
                    tt.addProperty(a.id, tier(u, a));
                }
                o.add("thuoctinh", tt);
                nguoi.add(id, o);
            }
        }
        root.add("nguoi", nguoi);
        try {
            Files.writeString(new File(plugin.getDataFolder(), "capdo.json").toPath(), root.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Không ghi được capdo.json: " + e.getMessage());
        }
    }
}
