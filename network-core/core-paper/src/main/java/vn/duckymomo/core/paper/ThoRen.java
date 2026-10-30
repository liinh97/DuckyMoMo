package vn.duckymomo.core.paper;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Thợ Rèn (/thoren, 23/10): set đồ trao đổi. Mỗi set có các món (mũ, áo, quần, giày, kiếm), mỗi món cấp +0..+max, mỗi cấp là một
 * món trong Thư viện đồ (config tho-ren.sets.<set>.pieces.<món>.ids, vị trí = cấp).
 *  - Rèn +0: chỉ cần nguyên liệu. Nâng +N: cần món CÙNG SET cấp +N-1 (bị lấy đi) + nguyên liệu cấp N (cost[N]). Không thất bại.
 *    Nguyên liệu: "xu" (Vault), mã thư viện (Xu Quái, Lõi Quái, Tinh Luyện…) hoặc vật liệu game (chỉ tính món thường, không phải đồ thư viện).
 *  - Hiệu ứng bộ (bonus): theo số món giáp đang mặc có cấp >= from; lấy mức cao nhất đạt được của mỗi set, cộng các set lại.
 *    mau (% máu tối đa), day-lui (% kháng đẩy lùi), giam-st (% giảm sát thương nhận), thu-hoach (% cơ hội gấp đôi nông sản khi phá cây
 *    chín, set Nông Gia), dem-st (% sát thương thêm lên quái thù địch ban đêm ở thế giới thường, set Thợ Săn).
 *  - Set mùa / sự kiện (season): chỉ rèn được khi mùa đang mở (ngày from..to MM-dd, hoặc open: on/off cho sự kiện bật tay); đúng mùa thì
 *    hiệu ứng bộ x boost, vũ khí của set +damage % sát thương, quái thù địch (không từ lồng sinh quái) có drop-chance % rơi nguyên liệu mùa.
 *  - Độ mòn: wear[cấp] = % lần trừ độ bền được tính (cấp cao mòn chậm).
 */
final class ThoRen implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    /** an = mã mảnh ấn riêng của món (vd mũ, giày Chiến Binh: cb_an_khoi); khoá "an" trong cost được thay bằng mã này. */
    private record Piece(String key, String name, List<String> ids, String an) { }
    private record Bonus(int from, int pieces, double mau, double dayLui, double giamSt, double thuHoach, double demSt, double kn) {
        Bonus times(double k) {
            return new Bonus(from, pieces, mau * k, dayLui * k, giamSt * k, thuHoach * k, demSt * k, kn * k);
        }
    }
    private record Season(String name, String from, String to, String open, double boost, double damage, String drop, double dropChance) { }
    /** requires = mã set trước: rèn +0 cần món cùng loại của set đó ở cấp cao nhất (vd Tinh Anh +0 cần Tân Thủ +9). */
    private record SetDef(String id, String name, Material icon, String desc, String requires, List<Piece> pieces, List<Map<String, Object>> costs,
                          List<Integer> wear, List<Bonus> bonus, Season season) {
        int max() {
            return pieces.stream().mapToInt(p -> p.ids().size()).min().orElse(0) - 1;
        }
    }
    private record Ref(SetDef set, Piece piece, int level) { }
    /** Quầy đổi nguyên liệu (NPC ở các quầy Kinh Đô): đổi MỘT CHIỀU cost -> out. open = "thoren": menu mọi set; "set:<mã>": mở thẳng một set. */
    private record Recipe(String out, int amount, Map<String, Object> cost) { }
    /**
     * 30/10: open = "trungbay": tủ trưng bày, chỉ XEM đồ cấp cao nhất của các set (sets: thứ tự hiện, trống = mọi set), không đổi được;
     * open = "chiduong": người chỉ đường, menu các điểm đến (di: [{name, warp, icon, desc}]) bấm = /warp. kieu: danlang = NPC dân làng
     * (giống ông lái buôn ở shop) dù đang dùng Citizens.
     */
    private record Stall(String id, String name, String sub, String profession, String open, String skin, List<Recipe> recipes,
                         List<String> showSets, List<Map<?, ?>> go, boolean villager, boolean nameOnly) { }
    /** Hiệu ứng đang áp cho một người (tổng các set) + mô tả để báo khi đổi. */
    private record Active(double mau, double dayLui, double giamSt, double thuHoach, double demSt, double kn, String text) { }

    private final DuckyMoMoCore plugin;
    private final ItemLibrary lib;
    private final Map<String, SetDef> sets = new LinkedHashMap<>();
    private final Map<String, Ref> byId = new HashMap<>();
    private final Map<UUID, Active> active = new HashMap<>();
    private final NamespacedKey keyMau;
    /** Dấu phiên bản mô tả của món set (băm mục Thư viện đồ): khác thì dựng lại món theo Thư viện, giữ độ mòn + số lượng. */
    private final NamespacedKey keyVer;
    private final NamespacedKey keyDayLui;
    private final Map<String, Stall> stalls = new LinkedHashMap<>();
    private final java.util.Set<String> guarded = new java.util.HashSet<>();   // mảnh / tinh thể: cấm đặt, chế, nung, đốt
    private final NamespacedKey npcKey;
    /** NPC dân làng của quầy (để xoay đầu nhìn người chơi; quét lúc bật + khi chunk nạp). */
    private final java.util.Set<UUID> npcs = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** NPC hình người (Citizens, tho-ren.npc-type: citizens): quầy -> ID NPC, lưu ở thoren-npc.yml. Không có Citizens thì dùng dân làng. */
    private final boolean citizens;
    private final java.io.File npcFile;
    private final org.bukkit.configuration.file.YamlConfiguration npcIds;
    /** Rơi mảnh trao đổi trong dungeon: mã dungeon -> [item, % quái thường, % phó trùm, % trùm]. Không con nào rơi chắc chắn. */
    private record Drop(List<String> items, double mob, double mini, double boss, List<String> rare, double rareFactor) { }
    private final Map<String, Drop> dungeonDrops = new HashMap<>();
    private final String fishItem;
    /** Đồ EliteMobs (dùng tạm) yếu hơn đồ set: hệ số sát thương vũ khí, % nhận thêm mỗi món giáp. */
    private final double emWeapon;
    private final double emArmor;      // nguyên liệu rơi khi câu cá (Vảy Cá Hiếm, set Ngư Phủ)
    /**
     * Đồ set đánh quái EliteMobs: cấp hiệu dụng của set [ở +0, ở +max] (nội suy theo cấp). EliteMobs chấm cấp đồ theo phù phép (trần Sắc bén 5…) nên
     * mọi set chỉ ngang đồ cấp ~10, tầng cao gần như không đánh nổi (đo 23/10: Cao Cấp +5 vs quái cấp 55 = 1%/nhát). Thay bằng: quái thường cùng cấp
     * chết sau nhat nhát (x hệ số máu của quái), mình chết sau don đòn; mỗi cấp chênh nhân / chia heSo. Lấy mức có lợi hơn cho người chơi.
     */
    private final Map<String, double[]> vsLevel = new HashMap<>();
    private double vsHits = 3, vsTaken = 8, vsStep = 1.08;
    private static java.lang.reflect.Method emEntity;
    private static boolean emTried;
    private final double fishChance;

    static volatile ThoRen INSTANCE;

    /** Set giáp đang mặc nhiều món nhất (28/10, hiện dưới tên khi ẩn giáp): "Tân Thủ +7", chưa đủ bộ thì "Tân Thủ +7 (3/4)".
     *  Cấp = món thấp nhất trong số đang mặc của set đó. null nếu không mặc món giáp nào của set. */
    static String wornSet(Player p) {
        ThoRen t = INSTANCE;
        if (t == null || p == null) {
            return null;
        }
        // 30/10 chủ server: món mạnh nhất phải thắng (1 giày Thần Thánh > 3 món Tân Thủ). Xếp theo bậc set (tho-ren.sets.<id>.bac,
        // 1 = yếu nhất), cùng bậc thì nhiều món hơn, rồi cấp cao hơn.
        Map<SetDef, int[]> seen = new LinkedHashMap<>();   // set -> {số món, cấp thấp nhất}
        for (ItemStack it : p.getInventory().getArmorContents()) {
            Ref r = it == null ? null : t.byId.get(ItemLibrary.idOf(it));
            if (r == null) {
                continue;
            }
            int[] v = seen.computeIfAbsent(r.set(), k -> new int[] {0, Integer.MAX_VALUE});
            v[0]++;
            v[1] = Math.min(v[1], r.level());
        }
        SetDef best = null;
        int[] bv = null;
        for (Map.Entry<SetDef, int[]> e : seen.entrySet()) {
            int[] v = e.getValue();
            int rk = t.setRank.getOrDefault(e.getKey().id(), 0), brk = best == null ? -1 : t.setRank.getOrDefault(best.id(), 0);
            if (bv == null || rk > brk || (rk == brk && (v[0] > bv[0] || (v[0] == bv[0] && v[1] > bv[1])))) {
                best = e.getKey();
                bv = v;
            }
        }
        if (best == null) {
            return null;
        }
        return best.name() + " +" + bv[1] + (bv[0] < 4 ? " (" + bv[0] + "/4)" : "");
    }

    /** Bậc sức mạnh của set (tho-ren.sets.<id>.bac, 1 = yếu nhất), dùng chọn set hiện trên đầu khi ẩn giáp. */
    private final Map<String, Integer> setRank = new java.util.HashMap<>();

    ThoRen(DuckyMoMoCore plugin, ItemLibrary lib, ConfigurationSection c) {
        INSTANCE = this;
        this.plugin = plugin;
        this.lib = lib;
        this.keyMau = new NamespacedKey(plugin, "thoren_mau");
        this.keyVer = new NamespacedKey(plugin, "thoren_ver");
        this.keyDayLui = new NamespacedKey(plugin, "thoren_daylui");
        this.npcKey = new NamespacedKey(plugin, "thoren_npc");
        this.citizens = !"villager".equalsIgnoreCase(c.getString("npc-type", "citizens")) && Bukkit.getPluginManager().isPluginEnabled("Citizens");
        this.npcFile = new java.io.File(plugin.getDataFolder(), "thoren-npc.yml");
        this.npcIds = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(npcFile);
        ConfigurationSection qs = c.getConfigurationSection("quay");
        if (qs != null) {
            for (String id : qs.getKeys(false)) {
                ConfigurationSection q = qs.getConfigurationSection(id);
                List<Recipe> rs = new ArrayList<>();
                for (Map<?, ?> m : q.getMapList("recipes")) {
                    Map<String, Object> cost = new LinkedHashMap<>();
                    if (m.get("cost") instanceof Map<?, ?> cm) {
                        cm.forEach((a, b) -> cost.put(String.valueOf(a), b));
                    }
                    String out = String.valueOf(m.get("out"));
                    rs.add(new Recipe(out, Math.max(1, num(m.get("amount")).intValue()), cost));
                    guarded.add(out);
                }
                stalls.put(id, new Stall(id, q.getString("name", id), q.getString("sub", ""), q.getString("profession", "MASON"),
                        q.getString("open", ""), q.getString("skin", ""), rs, q.getStringList("sets"), q.getMapList("di"),
                        "danlang".equalsIgnoreCase(q.getString("kieu", "")), q.getBoolean("chi-ten", false)));
            }
        }
        guarded.addAll(c.getStringList("guard"));
        ConfigurationSection dd = c.getConfigurationSection("dungeon-drops");
        if (dd != null) {
            double mob = dd.getDouble("mob-chance", 3), mini = dd.getDouble("miniboss-chance", 15), boss = dd.getDouble("boss-chance", 30);
            ConfigurationSection list = dd.getConfigurationSection("dungeons");
            for (String id : list == null ? java.util.Set.<String>of() : list.getKeys(false)) {
                ConfigurationSection x = list.getConfigurationSection(id);
                if (x == null) {
                    continue;
                }
                double mul = x.getDouble("multiplier", 1);
                // rare: mảnh hiếm (Huyền Ấn) tung riêng, tỉ lệ = tỉ lệ thường × rare-chance (vd 0.3 = bằng 30%)
                dungeonDrops.put(id, new Drop(x.getStringList("items"), Math.min(100, mob * mul), Math.min(100, mini * mul), Math.min(100, boss * mul),
                        x.getStringList("rare"), x.getDouble("rare-chance", 0.3)));
            }
        }
        this.fishItem = c.getString("fish-drop.item", "");
        this.emWeapon = c.getDouble("elitemobs-gear.weapon-damage", 1);
        this.emArmor = c.getDouble("elitemobs-gear.armor-penalty", 0);
        ConfigurationSection ve = c.getConfigurationSection("elitemobs-gear.set-vs-elite");
        if (ve != null) {
            vsHits = ve.getDouble("nhat-ngang-cap", 3);
            vsTaken = ve.getDouble("don-ngang-cap", 8);
            vsStep = ve.getDouble("he-so-cap", 1.08);
            ConfigurationSection lv = ve.getConfigurationSection("cap");
            for (String id : lv == null ? java.util.Set.<String>of() : lv.getKeys(false)) {
                List<Double> d = lv.getDoubleList(id);
                if (!d.isEmpty()) {
                    vsLevel.put(id, new double[]{d.get(0), d.get(d.size() - 1)});
                }
            }
        }
        this.fishChance = c.getDouble("fish-drop.chance", 0);
        ConfigurationSection ss = c.getConfigurationSection("sets");
        if (ss == null) {
            return;
        }
        for (String id : ss.getKeys(false)) {
            ConfigurationSection s = ss.getConfigurationSection(id);
            ConfigurationSection ps = s == null ? null : s.getConfigurationSection("pieces");
            if (ps == null) {
                continue;
            }
            List<Piece> pieces = new ArrayList<>();
            for (String k : ps.getKeys(false)) {
                pieces.add(new Piece(k, ps.getString(k + ".name", k), ps.getStringList(k + ".ids"), ps.getString(k + ".an", "")));
            }
            List<Map<String, Object>> costs = new ArrayList<>();
            for (Map<?, ?> m : s.getMapList("cost")) {
                Map<String, Object> cm = new LinkedHashMap<>();
                m.forEach((a, b) -> cm.put(String.valueOf(a), b));
                costs.add(cm);
            }
            List<Bonus> bonus = new ArrayList<>();
            for (Map<?, ?> m : s.getMapList("bonus")) {
                bonus.add(new Bonus(num(m.get("from")).intValue(), num(m.get("pieces")).intValue(), num(m.get("mau")).doubleValue(),
                        num(m.get("day-lui")).doubleValue(), num(m.get("giam-st")).doubleValue(), num(m.get("thu-hoach")).doubleValue(),
                        num(m.get("dem-st")).doubleValue(), num(m.get("kn")).doubleValue()));
            }
            ConfigurationSection se = s.getConfigurationSection("season");
            Season season = se == null ? null : new Season(se.getString("name", ""), se.getString("from", ""), se.getString("to", ""),
                    se.getString("open", "auto"), se.getDouble("boost", 1), se.getDouble("damage", 0), se.getString("drop.item", ""),
                    se.getDouble("drop.chance", 0));
            Material icon = Material.matchMaterial(s.getString("icon", "IRON_CHESTPLATE"));
            setRank.put(id, s.getInt("bac", 0));
            SetDef def = new SetDef(id, s.getString("name", id), icon == null ? Material.IRON_CHESTPLATE : icon, s.getString("desc", ""),
                    s.getString("requires", ""), pieces, costs, s.getIntegerList("wear"), bonus, season);
            sets.put(id, def);
            for (Piece p : pieces) {
                for (int i = 0; i < p.ids().size(); i++) {
                    byId.put(p.ids().get(i), new Ref(def, p, i));
                }
            }
        }
        // 30/10: thứ tự set kéo thả ở Cài đặt › Thợ Rèn (tho-ren.set-order, yếu -> mạnh) đè bac từng set
        List<String> order = c.getStringList("set-order");
        for (int oi = 0; oi < order.size(); oi++) {
            setRank.put(order.get(oi), oi + 1);
        }
    }

    private static Number num(Object o) {
        return o instanceof Number n ? n : 0;
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::refresh), 100L, 40L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> Bukkit.getWorlds().forEach(w -> w.getEntities().forEach(this::track)), 100L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::lookAtPlayers, 120L, 5L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::updateItems), 200L, 1200L);
        if (citizens) {   // skin trong config (tho-ren.quay.<quầy>.skin) áp lại cho NPC đã đặt
            Bukkit.getScheduler().runTaskLater(plugin, () -> stalls.values().forEach(st -> {
                int id = npcIds.getInt(st.id(), -1);
                if (id >= 0 && CitizensBridge.exists(id)) {
                    CitizensBridge.setSkin(id, st.skin());
                }
            }), 160L);
        }
    }

    /** Ghi nhận NPC quầy; NPC đặt trước 23/10 có gắn tên trên dân làng (hiện chồng lên dòng chữ nổi) -> bỏ tên. */
    private void track(org.bukkit.entity.Entity e) {
        if (citizens && e instanceof org.bukkit.entity.Villager v) {
            String q = v.getPersistentDataContainer().get(npcKey, org.bukkit.persistence.PersistentDataType.STRING);
            Stall st = q == null ? null : stalls.get(q);
            if (st != null && !st.villager() && npcIds.getInt(q, -1) < 0) {   // kieu: danlang (30/10) giữ dân làng
                // NPC dân làng đặt trước 23/10 (chủ server: "nhìn như zombie dân làng") -> NPC hình người đúng chỗ đó, giữ dòng chữ nổi
                org.bukkit.Location loc = v.getLocation();
                v.remove();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    npcIds.set(q, CitizensBridge.createNoName(org.bukkit.ChatColor.stripColor(st.name().replace('&', '§')), loc, st.skin()));
                    saveNpcIds();
                    raiseLabels(loc, q);
                });
                return;
            }
        }
        if (e instanceof org.bukkit.entity.Villager v && v.getPersistentDataContainer().has(npcKey, org.bukkit.persistence.PersistentDataType.STRING)) {
            npcs.add(v.getUniqueId());
            if (v.customName() != null) {
                v.customName(null);
            }
            if (v.getVillagerType() != org.bukkit.entity.Villager.Type.PLAINS) {
                v.setVillagerType(org.bukkit.entity.Villager.Type.PLAINS);   // loại theo biome (đầm lầy, rừng rậm…) nhìn tối như zombie dân làng
            }
        }
    }

    private void saveNpcIds() {
        try {
            npcIds.save(npcFile);
        } catch (java.io.IOException ex) {
            plugin.getLogger().warning("Thợ Rèn: không lưu được thoren-npc.yml: " + ex.getMessage());
        }
    }

    /** NPC người thấp hơn dân làng: nâng dòng chữ nổi của quầy cho khỏi chạm đầu (chỉ chạy một lần, lúc đổi). */
    private void raiseLabels(org.bukkit.Location at, String q) {
        for (org.bukkit.entity.Entity e : at.getWorld().getNearbyEntities(at, 0.6, 3, 0.6)) {
            if (e instanceof org.bukkit.entity.ArmorStand a && q.equals(a.getPersistentDataContainer().get(npcKey, org.bukkit.persistence.PersistentDataType.STRING))) {
                a.teleport(a.getLocation().add(0, 0.25, 0));
            }
        }
    }

    @EventHandler
    public void onEntitiesLoad(org.bukkit.event.world.EntitiesLoadEvent e) {
        e.getEntities().forEach(this::track);
    }

    /** NPC tắt AI nên không tự nhìn ai: mỗi 1/4 giây xoay mặt về người chơi gần nhất trong 8 khối. */
    private void lookAtPlayers() {
        for (UUID id : npcs) {
            org.bukkit.entity.Entity e = Bukkit.getEntity(id);
            if (!(e instanceof org.bukkit.entity.Villager v) || !v.isValid()) {
                if (e == null || !e.isValid()) {
                    continue;
                }
                npcs.remove(id);
                continue;
            }
            Player best = null;
            double bd = 64;
            for (Player pl : v.getWorld().getPlayers()) {
                double d = pl.getLocation().distanceSquared(v.getLocation());
                if (d < bd && pl.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                    bd = d;
                    best = pl;
                }
            }
            if (best == null) {
                continue;
            }
            org.bukkit.util.Vector dir = best.getEyeLocation().toVector().subtract(v.getEyeLocation().toVector());
            float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
            float pitch = (float) Math.toDegrees(-Math.atan2(dir.getY(), Math.hypot(dir.getX(), dir.getZ())));
            v.setRotation(yaw, pitch);
            v.setBodyYaw(yaw);
        }
    }

    /** NPC + dòng chữ của quầy: không ai đánh được (cờ bất tử của game vẫn để người chơi sáng tạo đánh chết). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onNpcDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(npcKey, org.bukkit.persistence.PersistentDataType.STRING)) {
            e.setCancelled(true);
        }
    }

    String summary() {
        return sets.size() + " set, " + byId.size() + " món";
    }

    // ---------------------------------------------------------------- rèn / nâng cấp

    boolean command(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Chỉ người chơi dùng được /thoren.");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("kham") && Kham.INSTANCE != null) {
            Kham.INSTANCE.open(p);
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("set") && sets.containsKey(args[1].toLowerCase(java.util.Locale.ROOT))) {
            setMenu(p, sets.get(args[1].toLowerCase(java.util.Locale.ROOT)), false);   // /thoren set <mã>: mở thẳng một set (vd nút ở /diemdanh)
            return true;
        }
        menu(p);
        return true;
    }

    // ---------------------------------------------------------------- quầy đổi nguyên liệu + NPC

    private void stallMenu(Player p, Stall st) {
        if ("thoren".equals(st.open())) {
            menu(p);
            return;
        }
        if ("trungbay".equals(st.open())) {
            showcase(p, st);
            return;
        }
        if ("chiduong".equals(st.open())) {
            guide(p, st);
            return;
        }
        if (st.open().startsWith("set:") && sets.containsKey(st.open().substring(4))) {
            setMenu(p, sets.get(st.open().substring(4)), false);   // NPC riêng của một set: mở thẳng set đó
            return;
        }
        InfoMenu m = InfoMenu.of("&6" + st.name()).accent(Material.ORANGE_STAINED_GLASS_PANE);
        m.top(Tip.of("&6" + st.name()).accent("&6").desc(st.sub().isBlank() ? "Đổi nguyên liệu." : st.sub(),
                "Đổi một chiều, không đổi ngược lại. Mỗi lần bấm đổi 1 lượt.").apply(new ItemStack(Material.EMERALD)));
        for (Recipe r : st.recipes()) {
            m.add(recipeItem(p, r), pl -> {
                exchange(pl, r);
                stallMenu(pl, st);
            });
        }
        m.open(p);
    }

    /** Ô quầy đổi: chính món thật (mô tả màu gốc) + cuối mô tả "✦ DÙNG ĐỂ" (ghép / rèn ra gì) và "⇄ ĐỔI" (nguyên liệu ✔/✘). */
    private ItemStack recipeItem(Player p, Recipe r) {
        ItemStack it = lib.build(r.out(), r.amount());
        if (it == null) {
            return Tip.of("&c" + r.out()).desc("Chưa có trong Thư viện đồ, báo admin.").apply(new ItemStack(Material.BARRIER));
        }
        List<String> add = new ArrayList<>();
        List<String> use = usage(r.out());
        if (!use.isEmpty()) {
            add.add("");
            add.add("&6&l✦ DÙNG ĐỂ");
            use.forEach(u -> add.add("&e▸ &7" + u));
        }
        add.add("");
        add.add("&6&l⇄ ĐỔI" + (r.amount() > 1 ? " &e×" + r.amount() : ""));
        boolean ok = true;
        for (Map.Entry<String, Object> e : r.cost().entrySet()) {
            int need = num(e.getValue()).intValue();
            long have = have(p, e.getKey());
            ok &= have >= need;
            add.add((have >= need ? "&a✔ &7" : "&c✘ &7") + label(e.getKey(), need) + " &8(" + fmt(have) + ")");
        }
        add.add(ok ? "&e➜ Bấm để đổi" : "&c✘ Chưa đủ");
        it.editMeta(meta -> {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            add.forEach(l -> lore.add(c(l)));
            meta.lore(lore);
        });
        return it;
    }

    /** Món này dùng vào đâu (23/10, chủ server: "nhìn không biết item này trade ra cái gì"): ghép ra tinh thể nào, rèn set nào cấp mấy. */
    private List<String> usage(String id) {
        List<String> out = new ArrayList<>();
        for (Stall st : stalls.values()) {
            for (Recipe r : st.recipes()) {
                if (r.cost().containsKey(id)) {
                    out.add("Ghép " + lib.name(r.out()) + "&7 ở " + st.name());
                }
            }
        }
        for (SetDef set : sets.values()) {
            java.util.Set<String> pieces = new java.util.LinkedHashSet<>();
            int lo = Integer.MAX_VALUE, hi = -1;
            for (int lv = 0; lv < set.costs().size(); lv++) {
                Map<String, Object> raw = set.costs().get(lv);
                boolean direct = raw.containsKey(id);
                for (Piece pc : set.pieces()) {
                    if (direct || raw.containsKey("an") && id.equals(pc.an())) {
                        pieces.add(pc.name());
                        lo = Math.min(lo, lv);
                        hi = Math.max(hi, lv);
                    }
                }
            }
            if (hi >= 0) {
                boolean all = pieces.size() == set.pieces().size();
                out.add("Rèn " + (all ? "" : String.join(", ", pieces) + " ") + set.name() + " &e+" + lo + (hi > lo ? "→+" + hi : ""));
            }
        }
        return out;
    }

    private void exchange(Player p, Recipe r) {
        for (Map.Entry<String, Object> e : r.cost().entrySet()) {
            if (have(p, e.getKey()) < num(e.getValue()).intValue()) {
                p.sendMessage(c("&cChưa đủ " + label(e.getKey(), num(e.getValue()).intValue()) + "."));
                return;
            }
        }
        ItemStack out = lib.build(r.out(), r.amount());
        if (out == null) {
            p.sendMessage(c("&cMón " + r.out() + " chưa có trong Thư viện đồ, báo admin."));
            return;
        }
        r.cost().forEach((k, v) -> take(p, k, num(v).intValue()));
        Rewards.hand(p, out);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8f, 1.1f);
        p.sendMessage(c("&a✔ Đã đổi được " + (r.amount() > 1 ? r.amount() + " " : "") + lib.name(r.out()) + "&a."));
    }

    /** /dmm thorennpc <quầy|remove>: đặt NPC quầy ở chỗ đang đứng (mỗi quầy một NPC, đặt lại thì NPC cũ của quầy đó bị xoá). */
    void npcCommand(CommandSender s, String[] args) {
        // Console/RCON: /dmm thorennpc <quầy> <x> <y> <z> <yaw> (world chính) — 25/10 xếp NPC vào sạp khu chợ bằng toạ độ tính sẵn
        org.bukkit.Location at = null;
        if (!(s instanceof Player) && args.length >= 6) {
            at = new org.bukkit.Location(Bukkit.getWorlds().get(0), Integer.parseInt(args[2]) + 0.5, Integer.parseInt(args[3]),
                    Integer.parseInt(args[4]) + 0.5, Float.parseFloat(args[5]), 0);
        }
        if (!(s instanceof Player) && at == null && !(args.length >= 2 && (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("xoa")))) {
            s.sendMessage("Vào game, đứng trong quầy rồi gõ /dmm thorennpc <quầy> (console: /dmm thorennpc <quầy> <x> <y> <z> <yaw>)");
            return;
        }
        org.bukkit.World world = s instanceof Player pl ? pl.getWorld() : Bukkit.getWorlds().get(0);
        String id = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (id.equals("remove") || id.equals("xoa")) {
            int n = removeNpc(world, args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : null);
            s.sendMessage(c("&7Đã xoá " + n + " thực thể NPC quầy."));
            return;
        }
        Stall st = stalls.get(id);
        if (st == null) {
            s.sendMessage(c("&e/dmm thorennpc <quầy> &7đứng trong quầy rồi gõ. Quầy: &f" + String.join(", ", stalls.keySet())
                    + " &7· gỡ: &f/dmm thorennpc remove [quầy]"));
            return;
        }
        removeNpc(world, id);
        org.bukkit.Location loc;
        if (at != null) {
            loc = at;
        } else {
            Player p = (Player) s;
            loc = p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
            loc.setYaw(p.getLocation().getYaw());
            loc.setPitch(0);
        }
        if (citizens && !st.villager()) {
            npcIds.set(st.id(), CitizensBridge.createNoName(org.bukkit.ChatColor.stripColor(st.name().replace('&', '§')), loc, st.skin()));
            saveNpcIds();
            labels(world, loc, st, 1.85);
            s.sendMessage(c("&a✔ Đã đặt NPC " + st.name() + "&a." + (st.skin().isBlank() ? " &7(chưa đặt skin: tho-ren.quay." + st.id() + ".skin)" : "")));
            return;
        }
        org.bukkit.entity.Villager v = world.spawn(loc, org.bukkit.entity.Villager.class, e -> {
            e.setAI(false);   // không gắn tên lên dân làng: nhìn vào sẽ hiện tên chồng lên dòng chữ nổi
            e.setVillagerType(org.bukkit.entity.Villager.Type.PLAINS);   // dân làng đồng bằng (da sáng), không theo biome chỗ đặt
            e.setSilent(true);
            e.setInvulnerable(true);
            e.setCollidable(false);
            e.setPersistent(true);
            e.setRemoveWhenFarAway(false);
            org.bukkit.entity.Villager.Profession prof = org.bukkit.Registry.VILLAGER_PROFESSION.get(
                    NamespacedKey.minecraft(st.profession().toLowerCase(Locale.ROOT)));
            e.setProfession(prof == null ? org.bukkit.entity.Villager.Profession.MASON : prof);
            e.setVillagerLevel(5);
            e.getPersistentDataContainer().set(npcKey, org.bukkit.persistence.PersistentDataType.STRING, st.id());
        });
        if (!v.isValid()) {
            s.sendMessage(c("&cKhông đặt được NPC ở đây (có thể WorldGuard chặn sinh mob)."));
            return;
        }
        npcs.add(v.getUniqueId());
        labels(world, loc, st, 1.6);
        s.sendMessage(c("&a✔ Đã đặt NPC " + st.name() + "&a."));
    }

    /** Chữ nổi trên đầu NPC quầy (dòng thấp nhất ở độ cao base): tên + mô tả + "(bấm vào để mở)", hoặc chỉ tên nếu chi-ten: true. */
    private void labels(org.bukkit.World w, org.bukkit.Location loc, Stall st, double base) {
        String[] lines = st.nameOnly() ? new String[] {st.name()}
                : new String[] {st.name(), st.sub().isBlank() ? "&7Đổi nguyên liệu" : "&7" + st.sub(), "&8(bấm vào để mở)"};
        double y = base + (lines.length - 1) * 0.28;
        for (String line : lines) {
            w.spawn(loc.clone().add(0, y, 0), org.bukkit.entity.ArmorStand.class, a -> {
                a.setMarker(true);
                a.setInvisible(true);
                a.setGravity(false);
                a.setInvulnerable(true);
                a.setPersistent(true);
                a.customName(c(line));
                a.setCustomNameVisible(true);
                a.getPersistentDataContainer().set(npcKey, org.bukkit.persistence.PersistentDataType.STRING, st.id());
            });
            y -= 0.28;
        }
    }

    private int removeNpc(org.bukkit.World w, String id) {
        int n = 0;
        if (citizens) {
            for (String q : List.copyOf(npcIds.getKeys(false))) {
                if (id == null || id.equals(q)) {
                    CitizensBridge.remove(npcIds.getInt(q, -1));
                    npcIds.set(q, null);
                    n++;
                }
            }
            saveNpcIds();
        }
        for (org.bukkit.entity.Entity e : w.getEntities()) {
            String v = e.getPersistentDataContainer().get(npcKey, org.bukkit.persistence.PersistentDataType.STRING);
            if (v != null && (id == null || id.equals(v))) {
                e.remove();
                n++;
            }
        }
        return n;
    }

    List<String> stallIds() {
        return List.copyOf(stalls.keySet());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onNpc(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        String id = e.getRightClicked().getPersistentDataContainer().get(npcKey, org.bukkit.persistence.PersistentDataType.STRING);
        if (id == null && citizens && e.getRightClicked().hasMetadata("NPC")) {
            int nid = CitizensBridge.idOf(e.getRightClicked());
            for (String q : npcIds.getKeys(false)) {
                if (nid >= 0 && npcIds.getInt(q, -1) == nid) {
                    id = q;
                }
            }
        }
        if (id == null) {
            return;
        }
        e.setCancelled(true);
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        Stall st = stalls.get(id);
        if (st != null && !DailyQuests.talkAt(e.getPlayer(), "quay:" + id)) {   // việc dân làng nhờ (28/10)
            stallMenu(e.getPlayer(), st);
        }
    }

    // ---------------------------------------------------------------- chống dùng sai mảnh / tinh thể (dấu thuvien_id, không làm giả được)

    private boolean isGuarded(ItemStack it) {
        String id = ItemLibrary.idOf(it);
        return id != null && guarded.contains(id);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(org.bukkit.event.block.BlockPlaceEvent e) {
        if (isGuarded(e.getItemInHand())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onCraft(org.bukkit.event.inventory.PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (isGuarded(it)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onSmith(org.bukkit.event.inventory.PrepareSmithingEvent e) {
        for (ItemStack it : e.getInventory().getContents()) {
            if (isGuarded(it)) {
                e.setResult(null);
                return;
            }
        }
    }

    /** Lệ Ender (mắt ender) ném được, Hỏa Dược Đen (cầu lửa) châm lửa được: chặn mọi lần dùng chuột phải. */
    @EventHandler(priority = EventPriority.LOW)
    public void onUse(org.bukkit.event.player.PlayerInteractEvent e) {
        if (e.getAction().isRightClick() && isGuarded(e.getItem())) {
            e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.ALLOW);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(org.bukkit.event.inventory.FurnaceBurnEvent e) {
        if (isGuarded(e.getFuel())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSmelt(org.bukkit.event.inventory.FurnaceSmeltEvent e) {
        if (isGuarded(e.getSource())) {
            e.setCancelled(true);
        }
    }

    /** Cấp cao nhất của món này người chơi đang có (túi + đang mặc + tay phụ), -1 nếu chưa có. */
    private int owned(Player p, Piece piece) {
        int best = -1;
        for (ItemStack it : p.getInventory().getContents()) {
            Ref r = byId.get(ItemLibrary.idOf(it));
            if (r != null && r.piece() == piece && r.level() > best) {
                best = r.level();
            }
        }
        return best;
    }

    private void menu(Player p) {
        if (sets.size() == 1) {
            setMenu(p, sets.values().iterator().next(), false);
            return;
        }
        InfoMenu m = InfoMenu.of("&6⚒ Thợ Rèn").accent(Material.ORANGE_STAINED_GLASS_PANE);
        m.top(intro());
        for (SetDef set : sets.values()) {
            Tip t = Tip.of("&f" + set.name()).accent("&e");
            if (!set.desc().isBlank()) {
                t.desc(set.desc());
            }
            if (!set.requires().isBlank() && sets.containsKey(set.requires())) {
                SetDef req = sets.get(set.requires());
                t.kv("Nâng từ", req.name() + " +" + req.max());
            }
            t.kv("Cấp", "+0 → +" + set.max()).kv("Món", String.join(", ", set.pieces().stream().map(Piece::name).toList()));
            if (set.season() != null) {
                t.kv("Mùa", seasonText(set.season()));
            }
            Bonus b = best(p, set);
            if (b != null) {
                t.kv("Đang có", "&d" + bonusText(b));
            }
            m.add(t.click("Bấm để xem").apply(new ItemStack(set.icon())), pl -> setMenu(pl, set, true));
        }
        if (Kham.INSTANCE != null) {
            m.bar(Tip.of("&5◈ &fKhảm Tinh Luyện").accent("&d").desc("Cầm đồ set trên tay chính, khảm Tinh Luyện vào ô để thêm chỉ số.")
                    .kv("Lệnh", "/thoren kham").click("Bấm để mở").apply(new ItemStack(Material.AMETHYST_CLUSTER)), pl -> Kham.INSTANCE.open(pl));
        }
        m.open(p);
    }

    /** Tủ trưng bày (30/10): mỗi set một ô, bấm xem các món cấp cao nhất. Chỉ xem, không có nút rèn / đổi. */
    private void showcase(Player p, Stall st) {
        InfoMenu m = InfoMenu.of(st.name()).accent(Material.MAGENTA_STAINED_GLASS_PANE);
        m.top(Tip.of(st.name()).accent("&d").desc(st.sub().isBlank() ? "Đồ xịn nhất server, chỉ để xem." : st.sub(),
                        "Rê chuột vào từng món để xem chỉ số. Muốn có thì cày nguyên liệu rồi rèn ở Thợ Rèn.")
                .kv("Thợ Rèn", "/warp trade").apply(new ItemStack(Material.NETHER_STAR)));
        List<SetDef> list = new ArrayList<>();
        for (String id : st.showSets()) {
            if (sets.containsKey(id)) {
                list.add(sets.get(id));
            }
        }
        if (list.isEmpty()) {
            list.addAll(sets.values());
        }
        for (SetDef set : list) {
            Tip t = Tip.of("&f" + set.name() + " &e+" + set.max()).accent("&d");
            if (!set.desc().isBlank()) {
                t.desc(set.desc());
            }
            t.kv("Món", String.join(", ", set.pieces().stream().map(Piece::name).toList()));
            if (!set.bonus().isEmpty()) {
                t.kv("Hiệu ứng bộ", "&d" + bonusText(set.bonus().get(set.bonus().size() - 1)));
            }
            m.add(t.click("Bấm để xem đồ").apply(new ItemStack(set.icon())), pl -> showcaseSet(pl, st, set));
        }
        m.open(p);
    }

    private void showcaseSet(Player p, Stall st, SetDef set) {
        int lv = set.max();
        InfoMenu m = InfoMenu.of("&d✦ " + set.name() + " &e+" + lv).accent(Material.MAGENTA_STAINED_GLASS_PANE);
        Tip top = Tip.of("&d✦ &f" + set.name() + " &e+" + lv).accent("&d")
                .desc(set.desc().isBlank() ? "Đồ cấp cao nhất của set, chỉ để xem." : set.desc(), "Chỉ để xem. Rèn ở Thợ Rèn: /warp trade.");
        if (!set.bonus().isEmpty()) {
            top.kv("Hiệu ứng bộ", "&d" + bonusText(set.bonus().get(set.bonus().size() - 1)));
        }
        m.top(top.apply(new ItemStack(set.icon())));
        List<Piece> all = set.pieces();
        List<List<Piece>> blocks = all.size() <= 7 ? List.of(all)
                : List.of(all.subList(0, Math.min(4, all.size())), all.subList(Math.min(4, all.size()), all.size()));
        for (List<Piece> block : blocks) {
            for (Piece piece : block) {
                ItemStack it = lib.build(piece.ids().get(lv), 1);
                if (it != null) {
                    Kham.blank(it);
                }
                m.add(it != null ? it : Tip.of("&c" + piece.ids().get(lv)).desc("Chưa có trong Thư viện đồ.").apply(new ItemStack(Material.BARRIER)));
            }
            m.row();
        }
        m.back(pl -> showcase(pl, st));
        m.open(p);
    }

    /** Người chỉ đường (30/10): menu điểm đến, bấm = /warp tới đó. */
    private void guide(Player p, Stall st) {
        InfoMenu m = InfoMenu.of(st.name()).accent(Material.LIME_STAINED_GLASS_PANE);
        m.top(Tip.of(st.name()).accent("&a").desc(st.sub().isBlank() ? "Muốn đi đâu, tôi chỉ cho." : st.sub()).apply(new ItemStack(Material.COMPASS)));
        for (Map<?, ?> d : st.go()) {
            String warp = String.valueOf(d.get("warp"));
            Material icon = Material.matchMaterial(String.valueOf(d.containsKey("icon") ? d.get("icon") : "ENDER_PEARL"));
            Tip t = Tip.of("&f" + (d.containsKey("name") ? d.get("name") : warp)).accent("&a");
            if (d.get("desc") != null) {
                t.desc(String.valueOf(d.get("desc")));
            }
            m.add(t.kv("Lệnh", "/warp " + warp).click("Bấm để đi").apply(new ItemStack(icon == null ? Material.ENDER_PEARL : icon)),
                    InfoMenu.run("warp " + warp));
        }
        m.open(p);
    }

    private ItemStack intro() {
        return Tip.of("&6⚒ &fThợ Rèn").accent("&6").desc("Đổi nguyên liệu lấy đồ set, nâng cấp từng bậc.",
                        "Nâng +N cần món cùng set cấp +N-1 (bị lấy đi). Không bao giờ thất bại.")
                .line().item("Cấp càng cao càng mòn chậm").item("Mặc nhiều món cùng set cấp cao: có hiệu ứng bộ")
                .apply(new ItemStack(Material.ANVIL));
    }

    private void setMenu(Player p, SetDef set, boolean back) {
        int lv = set.max();   // mở ở trang cấp thấp nhất người này còn phải rèn
        for (Piece piece : set.pieces()) {
            lv = Math.min(lv, Math.min(set.max(), owned(p, piece) + 1));
        }
        setMenu(p, set, back, lv);
    }

    /**
     * Menu một set, MỖI CẤP MỘT TRANG (chủ server 23/10): hiện chính các món thật của set ở cấp đó (đủ tên, phù phép, mô tả) để người chơi
     * so với đồ đang có, cuối mô tả ghi điều kiện + nguyên liệu; ◀ ▶ chuyển cấp. Bấm = rèn đúng món cấp đó.
     */
    private void setMenu(Player p, SetDef set, boolean back, int level) {
        int lv = Math.max(0, Math.min(set.max(), level));
        InfoMenu m = InfoMenu.of("&6⚒ Rèn " + set.name() + " &8· &e+" + lv + " &8/ +" + set.max()).accent(Material.ORANGE_STAINED_GLASS_PANE);
        m.top(Tip.of("&6⚒ &fRèn " + set.name() + " &e+" + lv).accent("&6")
                .desc(set.desc().isBlank() ? "Rê chuột vào từng món để xem đồ và nguyên liệu." : set.desc(),
                        "Nâng +N cần món cùng loại +N-1 (bị lấy đi). Không bao giờ thất bại.")
                .apply(new ItemStack(Material.ANVIL)));
        // hàng trên: món nguyên bản (mô tả màu gốc); hàng dưới: nút Rèn ngay dưới từng món (chủ server 23/10 chốt 2 hàng).
        // Set có hơn 7 món (giáp + nhiều loại vũ khí, 23/10): hai khối, giáp (4 món + nút) rồi vũ khí (+ nút), mỗi hàng tối đa 7 ô.
        List<Piece> all = set.pieces();
        List<List<Piece>> blocks = all.size() <= 7 ? List.of(all)
                : List.of(all.subList(0, Math.min(4, all.size())), all.subList(Math.min(4, all.size()), all.size()));
        for (List<Piece> block : blocks) {
            for (Piece piece : block) {
                ItemStack it = lib.build(piece.ids().get(lv), 1);
                Kham.blank(it);   // món xem trước hiện số ô khảm
                m.add(it != null ? it : Tip.of("&c" + piece.ids().get(lv)).desc("Chưa có trong Thư viện đồ, báo admin.").apply(new ItemStack(Material.BARRIER)));
            }
            m.row();
            for (Piece piece : block) {
                m.add(forgeButton(p, set, piece, lv), pl -> {
                    upgrade(pl, set, piece, lv);
                    setMenu(pl, set, back, lv);
                });
            }
            m.row();
        }
        if (lv > 0) {
            m.bar(Tip.of("&e◀ &fCấp +" + (lv - 1)).click("Bấm để xem").apply(new ItemStack(Material.ARROW)), pl -> setMenu(pl, set, back, lv - 1));
        }
        if (lv < set.max()) {
            m.bar(Tip.of("&fCấp +" + (lv + 1) + " &e▶").click("Bấm để xem").apply(new ItemStack(Material.SPECTRAL_ARROW)), pl -> setMenu(pl, set, back, lv + 1));
        }
        if (back) {
            m.back(this::menu);
        }
        m.open(p);
    }

    /** Món phải nộp để lên cấp next (null = không cần): cấp dưới của chính set, hoặc +max của set trước khi rèn +0. */
    private String prevId(SetDef set, Piece piece, int next) {
        if (next > 0) {
            return piece.ids().get(next - 1);
        }
        SetDef req = set.requires().isBlank() ? null : sets.get(set.requires());
        if (req == null) {
            return null;
        }
        return req.pieces().stream().filter(x -> x.key().equals(piece.key())).findFirst()
                .map(x -> x.ids().get(req.max())).orElse(null);
    }

    private boolean hasItem(Player p, String id) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (id.equals(ItemLibrary.idOf(it))) {
                return true;
            }
        }
        return false;
    }

    /** Nút Rèn dưới mỗi món: món phải nộp + nguyên liệu (✔/✘). Đủ: bàn rèn lấp lánh; thiếu: giấy; đã có: thuốc nhuộm xanh. */
    private ItemStack forgeButton(Player p, SetDef set, Piece piece, int lv) {
        int cur = owned(p, piece);
        Tip t = Tip.of("&6⚒ &fRèn " + piece.name() + " " + set.name() + " &e+" + lv).accent("&6");
        if (cur >= lv) {
            return t.check(true, "Bạn đã có " + (cur == lv ? "món này" : "cấp cao hơn (+" + cur + ")")).apply(new ItemStack(Material.LIME_DYE));
        }
        t.line();
        boolean ok = true;
        if (set.season() != null) {
            ok = open(set.season());
            t.check(ok, "Mùa " + seasonText(set.season()));
        }
        String prev = prevId(set, piece, lv);
        if (prev != null) {
            boolean has = hasItem(p, prev);
            ok = has;
            t.check(has, label(prev) + " &8(bị lấy đi)");
        }
        for (Map.Entry<String, Object> e : cost(set, piece, lv).entrySet()) {
            int need = num(e.getValue()).intValue();
            long have = have(p, e.getKey());
            ok &= have >= need;
            t.check(have >= need, label(e.getKey(), need) + " &8(" + fmt(have) + ")");
        }
        if (ok) {
            t.click("Bấm để rèn");
        } else {
            t.deny("Chưa đủ điều kiện");
        }
        ItemStack it = t.apply(new ItemStack(ok ? Material.SMITHING_TABLE : Material.PAPER));
        if (ok) {
            it.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return it;
    }


    private Map<String, Object> cost(SetDef set, Piece piece, int level) {
        Map<String, Object> raw = level < set.costs().size() ? set.costs().get(level) : Map.of();
        if (!raw.containsKey("an") || piece.an().isBlank()) {
            return raw;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        raw.forEach((k, v) -> out.put(k.equals("an") ? piece.an() : k, v));
        return out;
    }

    /** Có bao nhiêu: xu (số dư), mã thư viện (đếm món), vật liệu game (đếm món thường trong túi). */
    private long have(Player p, String key) {
        if (key.equals("xu")) {
            return VaultEconomy.available() ? (long) VaultEconomy.balance(p) : 0;
        }
        Material mat = lib.section(key) == null ? Material.matchMaterial(key) : null;
        long n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it == null) {
                continue;
            }
            String id = ItemLibrary.idOf(it);
            if (mat == null ? key.equals(id) : (id == null && it.getType() == mat)) {
                n += it.getAmount();
            }
        }
        return n;
    }

    private void take(Player p, String key, int need) {
        if (key.equals("xu")) {
            VaultEconomy.withdraw(p, need);
            return;
        }
        Material mat = lib.section(key) == null ? Material.matchMaterial(key) : null;
        ItemStack[] inv = p.getInventory().getStorageContents();
        for (int pass = 0; pass < 2; pass++)   // lượt 1 bỏ qua ngọc đã tháo (mang phẩm Khảm)
        for (int i = 0; i < inv.length && need > 0; i++) {
            ItemStack it = inv[i];
            if (it == null || pass == 0 && Kham.isLooseGem(it)) {
                continue;
            }
            String id = ItemLibrary.idOf(it);
            if (mat == null ? key.equals(id) : (id == null && it.getType() == mat)) {
                int k = Math.min(need, it.getAmount());
                it.setAmount(it.getAmount() - k);
                need -= k;
            }
        }
        p.getInventory().setStorageContents(inv);
    }

    private void upgrade(Player p, SetDef set, Piece piece, int next) {
        int cur = owned(p, piece);
        if (next > set.max()) {
            return;
        }
        if (cur >= next) {
            p.sendMessage(c("&7Bạn đã có " + piece.name() + " " + set.name() + " +" + cur + "."));
            return;
        }
        if (set.season() != null && !open(set.season())) {
            p.sendMessage(c("&c" + set.name() + " chỉ rèn được trong mùa " + seasonText(set.season()) + "&c."));
            return;
        }
        Map<String, Object> cost = cost(set, piece, next);
        for (Map.Entry<String, Object> e : cost.entrySet()) {
            if (have(p, e.getKey()) < num(e.getValue()).intValue()) {
                p.sendMessage(c("&cChưa đủ " + label(e.getKey(), num(e.getValue()).intValue()) + "."));
                return;
            }
        }
        ItemStack made = lib.build(piece.ids().get(next), 1);
        Kham.blank(made);   // khối "◇ Ô trống"; carry() bên dưới ghi đè nếu món cũ có ngọc
        if (made != null) {
            String ver = version(piece.ids().get(next));
            made.editMeta(meta -> meta.getPersistentDataContainer().set(keyVer, org.bukkit.persistence.PersistentDataType.STRING, ver));
        }
        if (made == null) {
            p.sendMessage(c("&cMón " + piece.ids().get(next) + " chưa có trong Thư viện đồ, báo admin."));
            return;
        }
        // lấy món cấp trước (hoặc +max của set trước): ưu tiên trong túi, rồi đồ đang mặc / tay phụ (đồ mới thế vào đúng chỗ đó)
        int slot = -1;
        String prevId = prevId(set, piece, next);
        if (prevId != null) {
            PlayerInventory inv = p.getInventory();
            ItemStack[] all = inv.getContents();
            for (int i = 0; i < all.length && slot < 0; i++) {   // 0–35 túi trước, rồi 36–39 giáp, 40 tay phụ
                if (prevId.equals(ItemLibrary.idOf(all[i]))) {
                    slot = i;
                }
            }
            if (slot < 0) {
                p.sendMessage(c("&cCần " + label(prevId) + "&c trong túi."));
                return;
            }
        }
        cost.forEach((k, v) -> take(p, k, num(v).intValue()));
        if (slot >= 0) {
            ItemStack old = p.getInventory().getItem(slot);
            Kham.carry(old, made, p);   // ngọc khảm theo sang món mới
            if (old != null && old.getAmount() > 1) {
                old.setAmount(old.getAmount() - 1);
                Rewards.hand(p, made);
            } else {
                p.getInventory().setItem(slot, made);
            }
        } else {
            Rewards.hand(p, made);
        }
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
        p.sendMessage(c("&6⚒ " + (prevId == null ? "Đã rèn" : "Đã nâng cấp") + " &f" + piece.name() + " " + set.name() + " &e+" + next + "&6."));
        plugin.getLogger().info("Thợ Rèn: " + p.getName() + " " + piece.ids().get(next) + " (+" + next + ")");
        DailyQuests.feature(p, "thoren");
        refresh(p);
    }

    /** "Mũ Tân Thủ +9" cho món set (tên trong Thư viện là khung trang trí, không thấy cấp). */
    /** {mã set, mã món, cấp} của đồ set (Kham), null nếu không phải. */
    String[] setInfo(String id) {
        Ref r = id == null ? null : byId.get(id);
        return r == null ? null : new String[] {r.set().id(), r.piece().key(), String.valueOf(r.level())};
    }

    String labelOf(String id) {
        return label(id);
    }

    String setName(String set) {
        SetDef s = sets.get(set);
        return s == null ? set : s.name();
    }

    private String label(String id) {
        Ref r = byId.get(id);
        return r == null ? lib.name(id) : r.piece().name() + " " + r.set().name() + " +" + r.level();
    }

    private String label(String key, int n) {
        if (key.equals("xu")) {
            return Rewards.xu(n);
        }
        if (lib.section(key) != null) {
            return n + " " + lib.name(key) + "&7";
        }
        Material m = Material.matchMaterial(key);
        return n + " " + (m == null ? key : Rewards.nameOf(new ItemStack(m)));
    }

    private static String fmt(long n) {
        return String.format(Locale.ROOT, "%,d", n).replace(',', '.');
    }

    // ---------------------------------------------------------------- hiệu ứng bộ

    private Bonus best(Player p, SetDef set) {
        List<Integer> lv = new ArrayList<>();
        for (ItemStack it : p.getInventory().getArmorContents()) {
            Ref r = byId.get(ItemLibrary.idOf(it));
            if (r != null && r.set() == set) {
                lv.add(r.level());
            }
        }
        Bonus best = null;
        for (Bonus b : set.bonus()) {
            long n = lv.stream().filter(l -> l >= b.from()).count();
            if (n >= b.pieces() && (best == null || b.from() > best.from() || b.from() == best.from() && b.pieces() > best.pieces())) {
                best = b;
            }
        }
        return best;
    }

    private static String bonusText(Bonus b) {
        List<String> s = new ArrayList<>();
        if (b.mau() != 0) {
            s.add("+" + trim(b.mau()) + "% máu");
        }
        if (b.dayLui() != 0) {
            s.add("+" + trim(b.dayLui()) + "% kháng đẩy lùi");
        }
        if (b.giamSt() != 0) {
            s.add("-" + trim(b.giamSt()) + "% sát thương nhận");
        }
        if (b.thuHoach() != 0) {
            s.add(trim(b.thuHoach()) + "% gấp đôi nông sản");
        }
        if (b.demSt() != 0) {
            s.add("+" + trim(b.demSt()) + "% sát thương quái ban đêm");
        }
        if (b.kn() != 0) {
            s.add("+" + trim(b.kn()) + "% kinh nghiệm");
        }
        return String.join(", ", s);
    }

    private static String trim(double d) {
        double r = Math.round(d * 10) / 10.0;
        return r == Math.rint(r) ? String.valueOf((long) r) : String.valueOf(r);
    }

    private void refresh(Player p) {
        double mau = 0, day = 0, giam = 0, thu = 0, dem = 0, kn = 0;
        List<String> txt = new ArrayList<>();
        for (SetDef set : sets.values()) {
            Bonus b = best(p, set);
            if (b != null) {
                boolean boosted = set.season() != null && set.season().boost() != 1 && open(set.season());
                if (boosted) {
                    b = b.times(set.season().boost());
                }
                mau += b.mau();
                day += b.dayLui();
                giam += b.giamSt();
                thu += b.thuHoach();
                dem += b.demSt();
                kn += b.kn();
                txt.add(set.name() + (boosted ? " (đúng mùa)" : "") + ": " + bonusText(b));
            }
        }
        String text = String.join(" · ", txt);
        Active old = active.get(p.getUniqueId());
        if (old != null && old.text().equals(text)) {
            return;
        }
        apply(p, Attribute.MAX_HEALTH, keyMau, mau / 100.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        apply(p, Attribute.KNOCKBACK_RESISTANCE, keyDayLui, day / 100.0, AttributeModifier.Operation.ADD_NUMBER);
        if (text.isEmpty()) {
            active.remove(p.getUniqueId());
        } else {
            active.put(p.getUniqueId(), new Active(mau, day, giam, thu, dem, kn, text));
        }
        if (old != null || !text.isEmpty()) {
            p.sendActionBar(c(text.isEmpty() ? "&7Hiệu ứng bộ: không còn" : "&d✦ Hiệu ứng bộ &f" + text));
        }
    }

    private static void apply(Player p, Attribute attr, NamespacedKey key, double amount, AttributeModifier.Operation op) {
        AttributeInstance a = p.getAttribute(attr);
        if (a == null) {
            return;
        }
        a.removeModifier(key);
        if (amount != 0) {
            a.addTransientModifier(new AttributeModifier(key, amount, op, EquipmentSlotGroup.ANY));
        }
        if (attr == Attribute.MAX_HEALTH && p.getHealth() > a.getValue()) {
            p.setHealth(a.getValue());
        }
    }

    @EventHandler
    public void onArmor(PlayerArmorChangeEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> refresh(e.getPlayer()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            updateItems(e.getPlayer());
            refresh(e.getPlayer());
        }, 20L);
    }

    private String version(String id) {
        ConfigurationSection s = lib.section(id);
        return s == null ? "" : Integer.toHexString(s.getValues(true).toString().hashCode());
    }

    /**
     * Món set người chơi đang giữ (túi + đang mặc + tay phụ) tự đổi theo Thư viện đồ khi mục trong thư viện đổi (23/10: bật gộp phù phép
     * cho mô tả thấp, món đã phát vẫn hiện kiểu cũ). Giữ độ mòn và số lượng; món đúng phiên bản thì bỏ qua.
     */
    private void updateItems(Player p) {
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        ItemStack[] all = inv.getContents();
        boolean changed = false;
        for (int i = 0; i < all.length; i++) {
            ItemStack it = all[i];
            String id = ItemLibrary.idOf(it);
            if (id == null || !byId.containsKey(id)) {
                continue;
            }
            String ver = version(id);
            String has = it.getItemMeta().getPersistentDataContainer().get(keyVer, org.bukkit.persistence.PersistentDataType.STRING);
            if (ver.equals(has)) {
                changed |= ItemLibrary.tidyLore(it);   // gom phù phép 3 cái một dòng, tên tiếng Việt (đồ cũ)
                changed |= Kham.migrate(it);           // khối khảm cũ nhiều dòng -> 1 dòng
                changed |= Kham.blank(it);             // đồ cũ đúng phiên bản nhưng chưa có dòng ô khảm
                continue;
            }
            ItemStack fresh = lib.build(id, it.getAmount());
            if (fresh == null) {
                continue;
            }
            int dmg = it.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable d ? d.getDamage() : 0;
            fresh.editMeta(meta -> {
                if (meta instanceof org.bukkit.inventory.meta.Damageable d) {
                    d.setDamage(dmg);
                }
                meta.getPersistentDataContainer().set(keyVer, org.bukkit.persistence.PersistentDataType.STRING, ver);
            });
            Kham.carry(it, fresh, p);
            Kham.blank(fresh);
            all[i] = fresh;
            changed = true;
        }
        if (changed) {
            inv.setContents(all);
            plugin.getLogger().info("Thợ Rèn: cập nhật đồ set theo Thư viện cho " + p.getName());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        active.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) {
            Active a = active.get(p.getUniqueId());
            if (a != null && a.giamSt() > 0) {
                e.setDamage(e.getDamage() * Math.max(0, 1 - a.giamSt() / 100.0));
            }
        }
    }

    // ---------------------------------------------------------------- mảnh trao đổi rơi trong dungeon

    /**
     * Quái trong dungeon (người hạ đang ở dungeon có trong dungeon-drops): MỌI con chỉ có TỈ LỆ rơi 1 mảnh (chủ server 23/10: không phải giết trùm
     * là chắc rơi): tên EliteMobs "『N』" = trùm (boss %), "〖N〗" = phó trùm (mini %), còn lại quái thường (mob %). Mảnh chọn ngẫu nhiên trong items.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDungeonKill(org.bukkit.event.entity.EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        DungeonGate dg = Shared.dungeonGate;
        if (k == null || dg == null || dungeonDrops.isEmpty()) {
            return;
        }
        String did = dg.dungeonOf(k.getUniqueId());
        Drop d = did == null ? null : dungeonDrops.get(did);
        if (d == null || d.items().isEmpty() && d.rare().isEmpty()) {
            return;
        }
        String name = e.getEntity().customName() == null ? ""
                : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(e.getEntity().customName());
        double pct = name.startsWith("『") ? d.boss() : name.startsWith("〖") ? d.mini() : d.mob();
        List<String> got = new ArrayList<>();
        if (!d.items().isEmpty() && ThreadLocalRandom.current().nextDouble(100) < pct) {
            got.add(d.items().get(ThreadLocalRandom.current().nextInt(d.items().size())));
        }
        if (!d.rare().isEmpty() && ThreadLocalRandom.current().nextDouble(100) < pct * d.rareFactor()) {
            got.add(d.rare().get(ThreadLocalRandom.current().nextInt(d.rare().size())));
        }
        for (String id : got) {
            ItemStack it = lib.build(id, 1);
            if (it != null) {
                Rewards.hand(k, it);
                k.sendMessage(c("&6✦ Nhặt được " + lib.name(id) + "&6!"));
            }
        }
    }

    // ---------------------------------------------------------------- đồ EliteMobs yếu hơn đồ set

    /** Món của EliteMobs (có dấu PDC namespace "elitemobs"). */
    private static boolean eliteGear(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().getKeys().stream().anyMatch(k -> k.getNamespace().equals("elitemobs"));
    }

    /**
     * 23/10 chủ server: đồ EliteMobs rơi trong dungeon (rơi dễ, gắn chủ) không được mạnh bằng đồ trao đổi. Đánh bằng vũ khí EliteMobs: sát thương
     * × weapon-damage; mỗi món giáp EliteMobs đang mặc: nhận thêm armor-penalty. Chạy HIGHEST (sau EliteMobs tính sát thương).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEliteGear(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (emWeapon != 1 && e.getDamager() instanceof Player p && eliteGear(p.getInventory().getItemInMainHand())) {
            e.setDamage(e.getDamage() * emWeapon);
        }
        if (emArmor > 0 && e.getEntity() instanceof Player v) {
            int n = 0;
            for (ItemStack a : v.getInventory().getArmorContents()) {
                if (eliteGear(a)) {
                    n++;
                }
            }
            if (n > 0) {
                e.setDamage(e.getDamage() * (1 + emArmor * n));
            }
        }
        // 23/10 chủ server: "đồ tân thủ vào dungeon lv1 là bất khả thi" -> đồ set tính theo cấp hiệu dụng khi đánh / chịu đòn quái EliteMobs (chỉ PvE)
        if (!vsLevel.isEmpty()) {
            boolean shot = e.getDamager() instanceof org.bukkit.entity.Projectile;
            Player atk = e.getDamager() instanceof Player p ? p
                    : e.getDamager() instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Player p ? p : null;
            Object mob = atk == null ? null : elite(e.getEntity());
            if (mob != null && e.getEntity() instanceof org.bukkit.entity.LivingEntity le) {
                Ref r = byId.get(ItemLibrary.idOf(atk.getInventory().getItemInMainHand()));
                double[] lv = r == null ? null : vsLevel.get(r.set().id());
                if (lv != null) {
                    double hits = vsHits * emNum(mob, "getHealthMultiplier", 1) * Math.pow(vsStep, emNum(mob, "getLevel", 0) - lerp(lv[0], lv[1], r));
                    double max = le.getAttribute(Attribute.MAX_HEALTH) == null ? le.getHealth() : le.getAttribute(Attribute.MAX_HEALTH).getValue();
                    double dmg = max / Math.max(0.34, hits);
                    if (!shot) {
                        float cd = atk.getAttackCooldown();   // bấm liên tục không chờ hồi đòn: giảm như game thường
                        dmg *= 0.2 + 0.8 * cd * cd;
                    }
                    if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
                        dmg *= 0.5;
                    }
                    e.setDamage(Math.max(e.getDamage(), dmg));
                }
            }
            org.bukkit.entity.Entity src = e.getDamager() instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof org.bukkit.entity.Entity sh
                    ? sh : e.getDamager();
            Object from = e.getEntity() instanceof Player && !(src instanceof Player) ? elite(src) : null;
            if (from != null && e.getEntity() instanceof Player v) {
                double sum = 0;
                boolean any = false;
                for (ItemStack a : v.getInventory().getArmorContents()) {   // món không phải đồ set: cấp 0
                    Ref r = byId.get(ItemLibrary.idOf(a));
                    double[] lv = r == null ? null : vsLevel.get(r.set().id());
                    if (lv != null) {
                        sum += lerp(lv[0], lv[1], r);
                        any = true;
                    }
                }
                if (any) {
                    double dieAfter = vsTaken / Math.max(0.1, emNum(from, "getDamageMultiplier", 1)) * Math.pow(vsStep, sum / 4 - emNum(from, "getLevel", 0));
                    double max = v.getAttribute(Attribute.MAX_HEALTH) == null ? 20 : v.getAttribute(Attribute.MAX_HEALTH).getValue();
                    e.setDamage(Math.min(e.getDamage(), max / Math.max(1, dieAfter)));
                }
            }
        }
    }

    // ---------------------------------------------------------------- set mùa, Nông Gia, Thợ Săn

    private static final java.time.ZoneId VN = java.time.ZoneId.of("Asia/Ho_Chi_Minh");

    /** Mùa đang mở: open on/off (sự kiện bật tay, /dmm reload), auto = theo ngày from..to (MM-dd, to < from là qua năm mới). */
    private static boolean open(Season s) {
        if (s.open().equalsIgnoreCase("on") || s.open().equalsIgnoreCase("true")) {   // YAML đọc on/off thành true/false
            return true;
        }
        if (s.open().equalsIgnoreCase("off") || s.open().equalsIgnoreCase("false") || s.from().isBlank() || s.to().isBlank()) {
            return false;
        }
        String today = java.time.LocalDate.now(VN).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd"));
        return s.from().compareTo(s.to()) <= 0 ? today.compareTo(s.from()) >= 0 && today.compareTo(s.to()) <= 0
                : today.compareTo(s.from()) >= 0 || today.compareTo(s.to()) <= 0;
    }

    private static String seasonText(Season s) {
        String when = s.open().equalsIgnoreCase("auto") && !s.from().isBlank()
                ? " " + s.from().substring(3) + "/" + s.from().substring(0, 2) + " → " + s.to().substring(3) + "/" + s.to().substring(0, 2) : "";
        return s.name() + when + (open(s) ? " &a(đang mở)" : " &c(chưa tới mùa)");
    }

    /** Vũ khí set mùa đúng mùa: +damage %. Hiệu ứng Thợ Săn: +dem-st % lên quái thù địch ban đêm (thế giới thường). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSetDamage(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) {
            return;
        }
        Ref r = byId.get(ItemLibrary.idOf(p.getInventory().getItemInMainHand()));
        Season s = r == null ? null : r.set().season();
        if (s != null && s.damage() != 0 && open(s)) {
            e.setDamage(e.getDamage() * (1 + s.damage() / 100.0));
        }
        Active a = active.get(p.getUniqueId());
        if (a != null && a.demSt() > 0 && e.getEntity() instanceof org.bukkit.entity.Enemy
                && p.getWorld().getEnvironment() == org.bukkit.World.Environment.NORMAL) {
            long t = p.getWorld().getTime();
            if (t >= 13000 && t <= 23000) {
                e.setDamage(e.getDamage() * (1 + a.demSt() / 100.0));
            }
        }
    }

    /** Chuyên Cần (29/10): hiệu ứng bộ kn = % kinh nghiệm (orb) nhận thêm. */
    @EventHandler(ignoreCancelled = true)
    public void onExp(org.bukkit.event.player.PlayerExpChangeEvent e) {
        Active a = active.get(e.getPlayer().getUniqueId());
        if (a != null && a.kn() > 0 && e.getAmount() > 0) {
            double v = e.getAmount() * (1 + a.kn() / 100.0);
            int base = (int) Math.floor(v);
            e.setAmount(base + (ThreadLocalRandom.current().nextDouble() < v - base ? 1 : 0));
        }
    }

    /** Nông Gia: phá cây đã chín, có thu-hoach % cơ hội rơi thêm một phần nông sản y hệt. */
    @EventHandler(ignoreCancelled = true)
    public void onHarvest(org.bukkit.event.block.BlockDropItemEvent e) {
        Active a = active.get(e.getPlayer().getUniqueId());
        if (a == null || a.thuHoach() <= 0 || !(e.getBlockState().getBlockData() instanceof org.bukkit.block.data.Ageable ag)
                || ag.getAge() < ag.getMaximumAge() || ThreadLocalRandom.current().nextDouble(100) >= a.thuHoach()) {
            return;
        }
        for (org.bukkit.entity.Item i : e.getItems()) {
            e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation().add(0.5, 0.5, 0.5), i.getItemStack().clone());
        }
    }

    /** Nguyên liệu mùa: quái thù địch (không từ lồng sinh quái) do người chơi hạ, đúng mùa, có drop-chance % rơi. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSeasonKill(org.bukkit.event.entity.EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k == null || !(e.getEntity() instanceof org.bukkit.entity.Enemy) || e.getEntity().fromMobSpawner()) {
            return;
        }
        for (SetDef set : sets.values()) {
            Season s = set.season();
            if (s == null || s.drop().isBlank() || !open(s) || ThreadLocalRandom.current().nextDouble(100) >= s.dropChance()) {
                continue;
            }
            ItemStack it = lib.build(s.drop(), 1);
            if (it != null) {
                Rewards.hand(k, it);
                k.sendMessage(c("&6✦ " + s.name() + ": nhặt được " + lib.name(s.drop()) + "&6!"));
            }
        }
    }

    private static double lerp(double a, double b, Ref r) {
        int max = r.set().max();
        return max <= 0 ? b : a + (b - a) * r.level() / max;
    }

    /** EliteEntity của EliteMobs (EntityTracker.getEliteMobEntity qua phản chiếu), null nếu không phải quái EliteMobs / không có EliteMobs. */
    private static Object elite(org.bukkit.entity.Entity en) {
        if (!emTried) {
            emTried = true;
            try {
                var em = Bukkit.getPluginManager().getPlugin("EliteMobs");
                if (em != null) {
                    emEntity = Class.forName("com.magmaguy.elitemobs.entitytracker.EntityTracker", true, em.getClass().getClassLoader())
                            .getMethod("getEliteMobEntity", org.bukkit.entity.Entity.class);
                }
            } catch (ReflectiveOperationException ex) {
                emEntity = null;
            }
        }
        try {
            return emEntity == null || en == null ? null : emEntity.invoke(null, en);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private static double emNum(Object eliteEntity, String getter, double def) {
        try {
            return ((Number) eliteEntity.getClass().getMethod(getter).invoke(eliteEntity)).doubleValue();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return def;
        }
    }

    // ---------------------------------------------------------------- Vảy Cá Hiếm (câu cá)

    @EventHandler(ignoreCancelled = true)
    public void onFish(org.bukkit.event.player.PlayerFishEvent e) {
        if (e.getState() != org.bukkit.event.player.PlayerFishEvent.State.CAUGHT_FISH || fishItem.isBlank()
                || ThreadLocalRandom.current().nextDouble(100) >= fishChance) {
            return;
        }
        ItemStack it = lib.build(fishItem, 1);
        if (it != null) {
            Rewards.hand(e.getPlayer(), it);
            e.getPlayer().sendMessage(c("&b≈ Bạn vớt được " + lib.name(fishItem) + "&b!"));
        }
    }

    // ---------------------------------------------------------------- độ mòn theo cấp

    @EventHandler(ignoreCancelled = true)
    public void onWear(PlayerItemDamageEvent e) {
        Ref r = byId.get(ItemLibrary.idOf(e.getItem()));
        if (r == null || r.level() >= r.set().wear().size()) {
            return;
        }
        int pct = r.set().wear().get(r.level());
        int keep = 0;
        for (int i = 0; i < e.getDamage(); i++) {
            if (ThreadLocalRandom.current().nextInt(100) < pct) {
                keep++;
            }
        }
        e.setDamage(keep);
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
}
