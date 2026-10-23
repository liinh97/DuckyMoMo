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
 *    mau (% máu tối đa), day-lui (% kháng đẩy lùi), giam-st (% giảm sát thương nhận).
 *  - Độ mòn: wear[cấp] = % lần trừ độ bền được tính (cấp cao mòn chậm).
 */
final class ThoRen implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    /** an = mã mảnh ấn riêng của món (vd mũ, giày Chiến Binh: cb_an_khoi); khoá "an" trong cost được thay bằng mã này. */
    private record Piece(String key, String name, List<String> ids, String an) { }
    private record Bonus(int from, int pieces, double mau, double dayLui, double giamSt) { }
    /** requires = mã set trước: rèn +0 cần món cùng loại của set đó ở cấp cao nhất (vd Tinh Anh +0 cần Tân Thủ +9). */
    private record SetDef(String id, String name, Material icon, String desc, String requires, List<Piece> pieces, List<Map<String, Object>> costs,
                          List<Integer> wear, List<Bonus> bonus) {
        int max() {
            return pieces.stream().mapToInt(p -> p.ids().size()).min().orElse(0) - 1;
        }
    }
    private record Ref(SetDef set, Piece piece, int level) { }
    /** Quầy đổi nguyên liệu (NPC ở các quầy Kinh Đô): đổi MỘT CHIỀU cost -> out. open = "thoren": menu mọi set; "set:<mã>": mở thẳng một set. */
    private record Recipe(String out, int amount, Map<String, Object> cost) { }
    private record Stall(String id, String name, String sub, String profession, String open, List<Recipe> recipes) { }
    /** Hiệu ứng đang áp cho một người (tổng các set) + mô tả để báo khi đổi. */
    private record Active(double mau, double dayLui, double giamSt, String text) { }

    private final DuckyMoMoCore plugin;
    private final ItemLibrary lib;
    private final Map<String, SetDef> sets = new LinkedHashMap<>();
    private final Map<String, Ref> byId = new HashMap<>();
    private final Map<UUID, Active> active = new HashMap<>();
    private final NamespacedKey keyMau;
    private final NamespacedKey keyDayLui;
    private final Map<String, Stall> stalls = new LinkedHashMap<>();
    private final java.util.Set<String> guarded = new java.util.HashSet<>();   // mảnh / tinh thể: cấm đặt, chế, nung, đốt
    private final NamespacedKey npcKey;
    /** NPC dân làng của quầy (để xoay đầu nhìn người chơi; quét lúc bật + khi chunk nạp). */
    private final java.util.Set<UUID> npcs = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Rơi mảnh trao đổi trong dungeon: mã dungeon -> [item, % quái thường, % phó trùm, % trùm]. Không con nào rơi chắc chắn. */
    private record Drop(List<String> items, double mob, double mini, double boss) { }
    private final Map<String, Drop> dungeonDrops = new HashMap<>();
    private final String fishItem;      // nguyên liệu rơi khi câu cá (Vảy Cá Hiếm, set Ngư Phủ)
    private final double fishChance;

    ThoRen(DuckyMoMoCore plugin, ItemLibrary lib, ConfigurationSection c) {
        this.plugin = plugin;
        this.lib = lib;
        this.keyMau = new NamespacedKey(plugin, "thoren_mau");
        this.keyDayLui = new NamespacedKey(plugin, "thoren_daylui");
        this.npcKey = new NamespacedKey(plugin, "thoren_npc");
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
                        q.getString("open", ""), rs));
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
                dungeonDrops.put(id, new Drop(x.getStringList("items"), Math.min(100, mob * mul), Math.min(100, mini * mul), Math.min(100, boss * mul)));
            }
        }
        this.fishItem = c.getString("fish-drop.item", "");
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
                        num(m.get("day-lui")).doubleValue(), num(m.get("giam-st")).doubleValue()));
            }
            Material icon = Material.matchMaterial(s.getString("icon", "IRON_CHESTPLATE"));
            SetDef def = new SetDef(id, s.getString("name", id), icon == null ? Material.IRON_CHESTPLATE : icon, s.getString("desc", ""),
                    s.getString("requires", ""), pieces, costs, s.getIntegerList("wear"), bonus);
            sets.put(id, def);
            for (Piece p : pieces) {
                for (int i = 0; i < p.ids().size(); i++) {
                    byId.put(p.ids().get(i), new Ref(def, p, i));
                }
            }
        }
    }

    private static Number num(Object o) {
        return o instanceof Number n ? n : 0;
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::refresh), 100L, 40L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> Bukkit.getWorlds().forEach(w -> w.getEntities().forEach(this::track)), 100L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::lookAtPlayers, 120L, 5L);
    }

    /** Ghi nhận NPC quầy; NPC đặt trước 23/10 có gắn tên trên dân làng (hiện chồng lên dòng chữ nổi) -> bỏ tên. */
    private void track(org.bukkit.entity.Entity e) {
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
        menu(p);
        return true;
    }

    // ---------------------------------------------------------------- quầy đổi nguyên liệu + NPC

    private void stallMenu(Player p, Stall st) {
        if ("thoren".equals(st.open())) {
            menu(p);
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

    private ItemStack recipeItem(Player p, Recipe r) {
        ItemStack out = lib.build(r.out(), r.amount());
        Tip t = Tip.of("&f" + (r.amount() > 1 ? r.amount() + " " : "") + lib.name(r.out())).accent("&e");
        ConfigurationSection sec = lib.section(r.out());
        List<String> desc = sec == null ? List.of() : sec.getStringList("lore");
        if (!desc.isEmpty()) {
            t.desc(desc.get(0).replaceAll("&[0-9a-fk-or]", ""));
        }
        t.line();
        boolean ok = true;
        for (Map.Entry<String, Object> e : r.cost().entrySet()) {
            int need = num(e.getValue()).intValue();
            long have = have(p, e.getKey());
            ok &= have >= need;
            t.check(have >= need, label(e.getKey(), need) + " &8(" + fmt(have) + ")");
        }
        if (ok) {
            t.click("Bấm để đổi");
        } else {
            t.deny("Chưa đủ");
        }
        ItemStack it = t.apply(new ItemStack(out == null ? Material.BARRIER : out.getType()));
        it.setAmount(Math.max(1, Math.min(64, r.amount())));
        if (ok) {
            it.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return it;
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
        if (!(s instanceof Player p)) {
            s.sendMessage("Vào game, đứng trong quầy rồi gõ /dmm thorennpc <quầy>");
            return;
        }
        String id = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (id.equals("remove") || id.equals("xoa")) {
            int n = removeNpc(p.getWorld(), args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : null);
            p.sendMessage(c("&7Đã xoá " + n + " thực thể NPC quầy."));
            return;
        }
        Stall st = stalls.get(id);
        if (st == null) {
            p.sendMessage(c("&e/dmm thorennpc <quầy> &7đứng trong quầy rồi gõ. Quầy: &f" + String.join(", ", stalls.keySet())
                    + " &7· gỡ: &f/dmm thorennpc remove [quầy]"));
            return;
        }
        removeNpc(p.getWorld(), id);
        org.bukkit.Location loc = p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
        loc.setYaw(p.getLocation().getYaw());
        loc.setPitch(0);
        org.bukkit.entity.Villager v = p.getWorld().spawn(loc, org.bukkit.entity.Villager.class, e -> {
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
            p.sendMessage(c("&cKhông đặt được NPC ở đây (có thể WorldGuard chặn sinh mob)."));
            return;
        }
        npcs.add(v.getUniqueId());
        String[] lines = {st.name(), st.sub().isBlank() ? "&7Đổi nguyên liệu" : "&7" + st.sub(), "&8(bấm vào để mở)"};
        double y = 1.6 + (lines.length - 1) * 0.28;
        for (String line : lines) {
            p.getWorld().spawn(loc.clone().add(0, y, 0), org.bukkit.entity.ArmorStand.class, a -> {
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
        p.sendMessage(c("&a✔ Đã đặt NPC " + st.name() + "&a."));
    }

    private int removeNpc(org.bukkit.World w, String id) {
        int n = 0;
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
        if (id == null) {
            return;
        }
        e.setCancelled(true);
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        Stall st = stalls.get(id);
        if (st != null) {
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
            Bonus b = best(p, set);
            if (b != null) {
                t.kv("Đang có", "&d" + bonusText(b));
            }
            m.add(t.click("Bấm để xem").apply(new ItemStack(set.icon())), pl -> setMenu(pl, set, true));
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
        for (Piece piece : set.pieces()) {
            m.add(pieceItem(p, set, piece, lv), pl -> {
                upgrade(pl, set, piece, lv);
                setMenu(pl, set, back, lv);
            });
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

    private ItemStack pieceItem(Player p, SetDef set, Piece piece, int lv) {
        ItemStack it = lib.build(piece.ids().get(lv), 1);
        if (it == null) {
            return Tip.of("&c" + piece.ids().get(lv)).desc("Chưa có trong Thư viện đồ, báo admin.").apply(new ItemStack(Material.BARRIER));
        }
        int cur = owned(p, piece);
        List<String> add = new ArrayList<>();
        add.add("");
        add.add("&6&l⚒ RÈN &e" + piece.name() + " " + set.name() + " +" + lv);
        if (cur >= lv) {
            add.add("&a✔ Bạn đã có " + (cur == lv ? "món này" : "cấp cao hơn (+" + cur + ")"));
        } else {
            boolean ok = true;
            String prev = prevId(set, piece, lv);
            if (prev != null) {
                boolean has = hasItem(p, prev);
                ok = has;
                add.add((has ? "&a✔ &7" : "&c✘ &7") + label(prev) + " &8(bị lấy đi)");
            }
            for (Map.Entry<String, Object> e : cost(set, piece, lv).entrySet()) {
                int need = num(e.getValue()).intValue();
                long have = have(p, e.getKey());
                ok &= have >= need;
                add.add((have >= need ? "&a✔ &7" : "&c✘ &7") + label(e.getKey(), need) + " &8(" + fmt(have) + ")");
            }
            add.add(ok ? "&e➜ Bấm để rèn" : "&c✘ Chưa đủ điều kiện");
        }
        it.editMeta(meta -> {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            add.forEach(l -> lore.add(c(l)));
            meta.lore(lore);
        });
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
        for (int i = 0; i < inv.length && need > 0; i++) {
            ItemStack it = inv[i];
            if (it == null) {
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
        Map<String, Object> cost = cost(set, piece, next);
        for (Map.Entry<String, Object> e : cost.entrySet()) {
            if (have(p, e.getKey()) < num(e.getValue()).intValue()) {
                p.sendMessage(c("&cChưa đủ " + label(e.getKey(), num(e.getValue()).intValue()) + "."));
                return;
            }
        }
        ItemStack made = lib.build(piece.ids().get(next), 1);
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
        refresh(p);
    }

    /** "Mũ Tân Thủ +9" cho món set (tên trong Thư viện là khung trang trí, không thấy cấp). */
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
        return String.join(", ", s);
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private void refresh(Player p) {
        double mau = 0, day = 0, giam = 0;
        List<String> txt = new ArrayList<>();
        for (SetDef set : sets.values()) {
            Bonus b = best(p, set);
            if (b != null) {
                mau += b.mau();
                day += b.dayLui();
                giam += b.giamSt();
                txt.add(set.name() + ": " + bonusText(b));
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
            active.put(p.getUniqueId(), new Active(mau, day, giam, text));
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
        Bukkit.getScheduler().runTaskLater(plugin, () -> refresh(e.getPlayer()), 20L);
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
        if (d == null || d.items().isEmpty()) {
            return;
        }
        String name = e.getEntity().customName() == null ? ""
                : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(e.getEntity().customName());
        double pct = name.startsWith("『") ? d.boss() : name.startsWith("〖") ? d.mini() : d.mob();
        int n = ThreadLocalRandom.current().nextDouble(100) < pct ? 1 : 0;
        for (int i = 0; i < n; i++) {
            String id = d.items().get(ThreadLocalRandom.current().nextInt(d.items().size()));
            ItemStack it = lib.build(id, 1);
            if (it != null) {
                Rewards.hand(k, it);
                k.sendMessage(c("&6✦ Nhặt được " + lib.name(id) + "&6!"));
            }
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
