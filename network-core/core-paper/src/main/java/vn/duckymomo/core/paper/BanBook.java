package vn.duckymomo.core.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sách cấm (22/10): luật cấm đồ theo hoạt động, sửa ở trang Thư viện đồ › Sách cấm (thuvien-do.yml, mục sach-cam):
 *   sach-cam.<hoạt động>: {mo-ta, do {<mã thư viện>: tat-hieu-ung | vo-hieu | chan-vao}, phu-phep [<mã phù phép riêng>]}
 * Hoạt động (ACTIVITIES): cong-thanh = vùng vây lúc đang có phiên đánh; dau-truong = thế giới Đấu Trường (arena.world);
 *   dungeon = thế giới EliteMobs em_* (cả lối vào qua Cổng Hoang), trừ sảnh Hội Phiêu Lưu em_adventurers_guild.
 * Cách xử lý từng món (người dùng 22/10: "không cho dùng item đó luôn, kiểu tối đi và không cầm được"):
 *   vo-hieu       món TỐI ĐI (lớp phủ hồi chiêu của game: món mang nhóm hồi chiêu riêng duckymomocore:sachcam_<hoạt động…>, mỗi giây
 *                 đặt hồi chiêu nhóm đó cho người đang ở hoạt động) và KHÔNG CẦM ĐƯỢC: không chọn lên tay (huỷ đổi ô), không đưa
 *                 sang tay phụ, đang cầm / mặc thì tự cất vào túi (túi đầy thì đổi chỗ với món khác, không được thì nhắc).
 *                 Chặn thêm cho chắc: đánh 0 sát thương, không bắn, không ăn, không chuột phải, vật tổ không cứu.
 *   chan-vao      mang trong túi thì không vào được (bước vào vùng vây, /dautruong, dịch chuyển vào dungeon); lỡ ở trong rồi
 *                 (phiên đánh bắt đầu, nhặt được bên trong) thì như vo-hieu.
 *   (tat-hieu-ung: tên cũ trong file, đọc thành vo-hieu.)
 *   phu-phep: phù phép riêng bị tắt ở hoạt động đó trên mọi món (vd Thu nhỏ trong công thành).
 * Luật đọc theo mã thư viện trên đồ (dấu thuvien_id) nên đổi luật là áp ngay cho đồ đã phát. Dòng "⛔" cuối mô tả đồ tự cập nhật
 * (vào game, đổi ô cầm, nhặt đồ, đóng túi, mỗi lần thư viện đổi); dòng đã ghi giữ ở PDC sachcam_lore để thay đúng chỗ.
 * Chưa có mục sach-cam (thư viện cũ): tự suy ra trang Công thành từ luật cũ (ky-nang.pvp + Bay lên / Dịch chuyển / Phóng bay /
 * Thu nhỏ); web lưu lần đầu thì ghi hẳn vào file (panel/app.py lib_default_book, khớp default() ở đây).
 * Người chơi xem luật: /sachcam (cầm món thì xem riêng món đó).
 */
final class BanBook implements Listener {

    enum Mode {
        DISABLE("vo-hieu", "không dùng được"), BLOCK("chan-vao", "không cho mang vào");

        final String key;
        final String text;

        Mode(String key, String text) {
            this.key = key;
            this.text = text;
        }

        static Mode of(String k) {
            if ("tat-hieu-ung".equals(k)) {
                return DISABLE;
            }
            for (Mode m : values()) {
                if (m.key.equals(k)) {
                    return m;
                }
            }
            return null;
        }
    }

    /** mã hoạt động -> tên hiện. Khớp LIB_ACTIVITIES trong panel/app.py. */
    static final Map<String, String> ACTIVITIES = new LinkedHashMap<>();
    /** Luật cũ trước Sách cấm: phù phép riêng tắt trong vùng vây đang đánh. */
    static final List<String> OLD_SIEGE_ENCHANTS = List.of("bay", "dich_chuyen", "phong_bay", "thu_nho");

    static {
        ACTIVITIES.put("cong-thanh", "Công thành");
        ACTIVITIES.put("dau-truong", "Đấu trường");
        ACTIVITIES.put("dungeon", "Hầm ngục");
    }

    record Page(String id, String desc, Map<String, Mode> items, Set<String> enchants) {
    }

    /** Nhóm hồi chiêu của món theo các hoạt động cấm nó: duckymomocore:sachcam_<hđ>_<hđ>… (tối đa 7 nhóm với 3 hoạt động). */
    private static NamespacedKey groupKey(Set<String> acts) {
        return new NamespacedKey("duckymomocore", "sachcam_" + String.join("_", acts).replace('-', '_'));
    }

    private static boolean ourGroup(NamespacedKey k) {
        return k != null && k.getNamespace().equals("duckymomocore") && k.getKey().startsWith("sachcam_");
    }

    /** Mọi nhóm có chứa hoạt động act (để làm tối món khi người chơi đang ở act). */
    private static List<NamespacedKey> groupsWith(String act) {
        List<String> all = new ArrayList<>(ACTIVITIES.keySet());
        List<NamespacedKey> out = new ArrayList<>();
        for (int mask = 1; mask < (1 << all.size()); mask++) {
            Set<String> acts = new LinkedHashSet<>();
            for (int i = 0; i < all.size(); i++) {
                if ((mask & (1 << i)) != 0) {
                    acts.add(all.get(i));
                }
            }
            if (acts.contains(act)) {
                out.add(groupKey(acts));
            }
        }
        return out;
    }

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static volatile Map<String, Page> pages = Map.of();
    private static int seenVersion = -1;
    private static NamespacedKey loreKey;
    /** Hoạt động mà người chơi đang mang đồ "chan-vao" (tính lại khi túi đổi + mỗi giây). */
    private static final Map<UUID, Set<String>> CARRY_BLOCK = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_WARN = new ConcurrentHashMap<>();

    /** Thế giới Đấu Trường (config arena.world). Không đọc Arena.WORLD: sảnh / Practice không nạp lớp Arena. */
    private static volatile String arenaWorld = "dautruong";
    private final JavaPlugin plugin;

    BanBook(JavaPlugin plugin) {
        this.plugin = plugin;
        arenaWorld = plugin.getConfig().getString("arena.world", "dautruong");
        loreKey = new NamespacedKey(plugin, "sachcam_lore");
        seenVersion = -1;   // /dmm reload dựng thư viện mới (đếm lại từ đầu)
    }

    void start() {
        reload();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    // ------------------------------------------------------------------ đọc luật

    /** Đọc lại khi file thư viện đổi. Trả true nếu vừa đọc lại. */
    private static boolean reload() {
        ItemLibrary lib = Shared.library;
        if (lib == null) {
            return false;
        }
        int v = lib.version();
        if (v == seenVersion) {
            return false;
        }
        seenVersion = v;
        ConfigurationSection book = lib.raw("sach-cam");
        Map<String, Page> out = new LinkedHashMap<>();
        if (book == null) {
            out.putAll(defaults(lib));
        } else {
            for (String act : ACTIVITIES.keySet()) {
                ConfigurationSection s = book.getConfigurationSection(act);
                if (s == null) {
                    continue;
                }
                Map<String, Mode> items = new LinkedHashMap<>();
                ConfigurationSection d = s.getConfigurationSection("do");
                if (d != null) {
                    for (String id : d.getKeys(false)) {
                        Mode m = Mode.of(d.getString(id, ""));
                        if (m != null) {
                            items.put(id, m);
                        }
                    }
                }
                out.put(act, new Page(act, s.getString("mo-ta", ""), items, new LinkedHashSet<>(s.getStringList("phu-phep"))));
            }
        }
        pages = out;
        return true;
    }

    /** Thư viện chưa có Sách cấm: trang Công thành theo luật cũ (khớp lib_default_book trong panel/app.py). */
    private static Map<String, Page> defaults(ItemLibrary lib) {
        Map<String, Mode> items = new LinkedHashMap<>();
        for (String id : lib.idsWith("ky-nang")) {
            ConfigurationSection s = lib.section(id);
            if (s != null && s.getBoolean("ky-nang.pvp")) {
                items.put(id, Mode.DISABLE);
            }
        }
        return Map.of("cong-thanh", new Page("cong-thanh", "Trong vùng vây lúc đang có phiên đánh", items, new LinkedHashSet<>(OLD_SIEGE_ENCHANTS)));
    }

    // ------------------------------------------------------------------ hỏi luật (module khác gọi)

    /** Các hoạt động người chơi đang ở trong. */
    static List<String> activeAt(Player p) {
        List<String> out = new ArrayList<>(2);
        Location l = p.getLocation();
        World w = l.getWorld();
        String wn = w == null ? "" : w.getName().toLowerCase();
        if (wn.contains(":")) {
            wn = wn.substring(wn.indexOf(':') + 1);
        }
        if (wn.equalsIgnoreCase(arenaWorld)) {
            out.add("dau-truong");
        } else if (wn.startsWith("em_") && !wn.equals("em_adventurers_guild")) {
            out.add("dungeon");
        } else if (SiegeWarHook.battleActive() && SiegeWarHook.inActiveSiegeZone(l)) {
            out.add("cong-thanh");
        }
        return out;
    }

    /** Luật của món ở một hoạt động (null = không cấm). */
    private static Mode modeOf(String act, ItemStack it) {
        Page pg = pages.get(act);
        String id = ItemLibrary.idOf(it);
        return pg == null || id == null ? null : pg.items().get(id);
    }

    /** Tên hoạt động đang cấm món này ở chỗ người chơi đứng (kỹ năng Xưởng, phù phép riêng không chạy), null = được dùng. */
    static String effectsBlockedBy(Player p, ItemStack it) {
        if (it == null || pages.isEmpty()) {
            return null;
        }
        for (String act : activeAt(p)) {
            if (modeOf(act, it) != null) {
                return ACTIVITIES.get(act);
            }
        }
        return null;
    }

    /** Như effectsBlockedBy, cộng thêm phù phép riêng bị tắt ở hoạt động (mọi món). */
    static String enchantBlockedBy(Player p, ItemStack it, String enchant) {
        if (pages.isEmpty()) {
            return null;
        }
        for (String act : activeAt(p)) {
            Page pg = pages.get(act);
            if (pg != null && (pg.enchants().contains(enchant) || (it != null && modeOf(act, it) != null))) {
                return ACTIVITIES.get(act);
            }
        }
        return null;
    }

    /** Món không dùng được ở chỗ này (vo-hieu, hoặc chan-vao mà đã lỡ ở trong). */
    private static String disabledBy(Player p, ItemStack it) {
        if (it == null || it.getType().isAir() || pages.isEmpty() || ItemLibrary.idOf(it) == null) {
            return null;
        }
        for (String act : activeAt(p)) {
            Mode m = modeOf(act, it);
            if (m == Mode.DISABLE || m == Mode.BLOCK) {
                return ACTIVITIES.get(act);
            }
        }
        return null;
    }

    /** Có được vào hoạt động không (đồ chan-vao trong túi). false = bị chặn, đã báo người chơi. Arena gọi trước khi cho vào hàng chờ. */
    static boolean mayEnter(Player p, String act) {
        Page pg = pages.get(act);
        if (pg == null) {
            return true;
        }
        List<String> names = new ArrayList<>();
        for (ItemStack it : p.getInventory().getContents()) {
            if (modeOf(act, it) == Mode.BLOCK) {
                String n = name(ItemLibrary.idOf(it));
                if (!names.contains(n)) {
                    names.add(n);
                }
            }
        }
        if (names.isEmpty()) {
            return true;
        }
        p.sendMessage(c("&c⛔ Sách cấm: không mang vào " + ACTIVITIES.get(act) + ": &f" + String.join("&7, &f", names)
                + "&c. Cất vào rương rồi vào lại. Xem luật: &e/sachcam"));
        return false;
    }

    private static String name(String id) {
        ItemLibrary lib = Shared.library;
        return lib == null ? id : lib.name(id);
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    private static void warn(Player p, String msg) {
        long now = System.currentTimeMillis();
        if (LAST_WARN.getOrDefault(p.getUniqueId(), 0L) < now - 2000) {
            LAST_WARN.put(p.getUniqueId(), now);
            p.sendActionBar(c(msg));
        }
    }

    // ------------------------------------------------------------------ dòng luật trên đồ (tự cập nhật)

    /** Dòng "⛔" của món theo luật hiện tại. */
    static List<String> loreLines(ItemStack it) {
        List<String> out = new ArrayList<>();
        if (it == null || !it.hasItemMeta() || pages.isEmpty()) {
            return out;
        }
        String id = ItemLibrary.idOf(it);
        var pdc = it.getItemMeta().getPersistentDataContainer();
        for (Page pg : pages.values()) {
            Mode m = id == null ? null : pg.items().get(id);
            if (m != null) {
                out.add("&c⛔ " + ACTIVITIES.get(pg.id()) + ": &7" + m.text);
                continue;
            }
            List<String> off = new ArrayList<>();
            for (String e : pg.enchants()) {
                if (SkillEnchants.NAMES.containsKey(e) && pdc.has(SkillEnchants.key(e), PersistentDataType.INTEGER)) {
                    off.add(SkillEnchants.NAMES.get(e)[0]);
                }
            }
            if (!off.isEmpty()) {
                out.add("&c⛔ " + ACTIVITIES.get(pg.id()) + ": &7tắt " + String.join(", ", off));
            }
        }
        return out;
    }

    /** Thay dòng luật cũ ở cuối mô tả bằng dòng theo luật hiện tại + gắn / gỡ nhóm hồi chiêu (để tối món). Trả true nếu đồ đổi. */
    static boolean refresh(ItemStack it) {
        if (loreKey == null || it == null || !it.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = it.getItemMeta();
        boolean changed = relore(meta, loreLines(it));
        // nhóm hồi chiêu theo các hoạt động cấm món này
        String id = ItemLibrary.idOf(it);
        Set<String> acts = new LinkedHashSet<>();
        for (Page pg : pages.values()) {
            if (id != null && pg.items().containsKey(id)) {
                acts.add(pg.id());
            }
        }
        NamespacedKey want = acts.isEmpty() ? null : groupKey(acts);
        NamespacedKey cur = meta.hasUseCooldown() ? meta.getUseCooldown().getCooldownGroup() : null;
        if (want == null && ourGroup(cur)) {
            meta.setUseCooldown(null);
            changed = true;
        } else if (want != null && !want.equals(cur) && (cur == null || ourGroup(cur))) {
            var uc = meta.getUseCooldown();
            uc.setCooldownSeconds(0.05f);   // dùng món (ăn…) chỉ hồi 1 tick
            uc.setCooldownGroup(want);
            meta.setUseCooldown(uc);
            changed = true;
        }
        if (changed) {
            it.setItemMeta(meta);
        }
        return changed;
    }

    /** Dòng ⛔ cuối mô tả: bỏ dòng đã ghi lần trước (nếu còn nguyên), thêm dòng mới. */
    private static boolean relore(ItemMeta meta, List<String> now) {
        String old = meta.getPersistentDataContainer().get(loreKey, PersistentDataType.STRING);
        String joined = String.join("\n", now);
        if (old == null && now.isEmpty()) {
            return false;
        }
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        int n = old == null || old.isEmpty() ? 0 : old.split("\n", -1).length;
        boolean present = n > 0 && lore.size() >= n;
        if (present) {
            String[] want = old.split("\n", -1);
            for (int i = 0; i < n; i++) {
                if (!LEGACY.serialize(lore.get(lore.size() - n + i)).equals(LEGACY.serialize(c(want[i])))) {
                    present = false;   // mô tả đã bị module khác dựng lại (Xưởng): dòng cũ không còn
                    break;
                }
            }
        }
        if (present && joined.equals(old)) {
            return false;
        }
        if (present) {
            lore.subList(lore.size() - n, lore.size()).clear();
        }
        for (String l : now) {
            lore.add(c(l));
        }
        meta.lore(lore.isEmpty() ? null : lore);
        if (now.isEmpty()) {
            meta.getPersistentDataContainer().remove(loreKey);
        } else {
            meta.getPersistentDataContainer().set(loreKey, PersistentDataType.STRING, joined);
        }
        return true;
    }

    /** Cập nhật dòng luật mọi món trong túi + tính lại đồ chan-vao đang mang. */
    private static void refreshInventory(Player p) {
        PlayerInventory inv = p.getInventory();
        Set<String> carry = new LinkedHashSet<>();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || !it.hasItemMeta()) {
                continue;
            }
            if (refresh(it)) {
                inv.setItem(i, it);
            }
            for (Page pg : pages.values()) {
                if (modeOf(pg.id(), it) == Mode.BLOCK) {
                    carry.add(pg.id());
                }
            }
        }
        if (carry.isEmpty()) {
            CARRY_BLOCK.remove(p.getUniqueId());
        } else {
            CARRY_BLOCK.put(p.getUniqueId(), carry);
        }
    }

    /** Hoạt động mà người chơi đang mang đồ chan-vao (tính ngay từ túi đồ, không sửa mô tả). */
    private static Set<String> carried(Player p) {
        Set<String> carry = new LinkedHashSet<>();
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null || !it.hasItemMeta()) {
                continue;
            }
            for (Page pg : pages.values()) {
                if (modeOf(pg.id(), it) == Mode.BLOCK) {
                    carry.add(pg.id());
                }
            }
        }
        return carry;
    }

    private void later(Player p) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) {
                refreshInventory(p);
            }
        });
    }

    /** Mỗi giây: thư viện đổi thì cập nhật đồ mọi người; ở hoạt động thì làm tối món bị cấm, món đang cầm / mặc thì cất vào túi. */
    private void tick() {
        boolean changed = reload();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (changed) {
                refreshInventory(p);
            } else {
                Set<String> carry = carried(p);   // đồ phát bằng lệnh / gói quà không có sự kiện nhặt: tính lại mỗi giây cho onMove
                if (carry.isEmpty()) {
                    CARRY_BLOCK.remove(p.getUniqueId());
                } else {
                    CARRY_BLOCK.put(p.getUniqueId(), carry);
                }
                ItemStack hand = p.getInventory().getItemInMainHand();   // đồ phát bằng lệnh / đổi chỗ trong túi không có sự kiện nhặt
                if (refresh(hand)) {
                    p.getInventory().setItemInMainHand(hand);
                }
            }
            List<String> here = pages.isEmpty() ? List.of() : activeAt(p);
            if (here.isEmpty()) {
                continue;
            }
            for (String act : here) {
                if (pages.containsKey(act)) {
                    for (NamespacedKey k : groupsWith(act)) {
                        p.setCooldown(k, 40);   // món mang nhóm này tối đi (hết 2 giây sau khi rời hoạt động)
                    }
                }
            }
            PlayerInventory inv = p.getInventory();
            String inHand = disabledBy(p, inv.getItemInMainHand());
            if (inHand != null) {
                stash(p, inv.getHeldItemSlot(), inHand);
            }
            for (EquipmentSlot slot : EnumSet.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFF_HAND)) {
                ItemStack it = inv.getItem(slot);
                String why = disabledBy(p, it);
                if (why == null) {
                    continue;
                }
                inv.setItem(slot, null);
                Map<Integer, ItemStack> left = inv.addItem(it);
                if (!left.isEmpty()) {
                    inv.setItem(slot, it);   // túi đầy: trả lại chỗ cũ, chỉ nhắc
                    warn(p, "&c⛔ " + name(ItemLibrary.idOf(it)) + " &ckhông dùng được ở " + why + ": túi đầy, chưa tháo ra được");
                } else {
                    warn(p, "&c⛔ Đã tháo " + name(ItemLibrary.idOf(it)) + " &cvào túi: không dùng được ở " + why);
                }
            }
        }
    }

    /** Cất món ở ô tay chính vào túi trong (ô 9–35): ô trống, không thì đổi chỗ với món dùng được. */
    private static void stash(Player p, int slot, String why) {
        PlayerInventory inv = p.getInventory();
        ItemStack it = inv.getItem(slot);
        int to = -1;
        for (int i = 9; i < 36 && to < 0; i++) {
            ItemStack o = inv.getItem(i);
            if (o == null || o.getType().isAir()) {
                to = i;
            }
        }
        for (int i = 9; i < 36 && to < 0; i++) {
            if (disabledBy(p, inv.getItem(i)) == null) {
                to = i;
            }
        }
        if (to < 0) {
            warn(p, "&c⛔ Món này không dùng được ở " + why + " &7(/sachcam)");
            return;
        }
        inv.setItem(slot, inv.getItem(to));
        inv.setItem(to, it);
        warn(p, "&c⛔ Đã cất " + name(ItemLibrary.idOf(it)) + " &cvào túi: không dùng được ở " + why + " &7(/sachcam)");
    }

    // ------------------------------------------------------------------ sự kiện: cập nhật dòng luật

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        later(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        CARRY_BLOCK.remove(e.getPlayer().getUniqueId());
        LAST_WARN.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent e) {
        ItemStack it = e.getPlayer().getInventory().getItem(e.getNewSlot());
        String why = disabledBy(e.getPlayer(), it);
        if (why != null) {
            e.setCancelled(true);   // không cầm được
            warn(e.getPlayer(), "&c⛔ Món này không dùng được ở " + why + " &7(/sachcam)");
            return;
        }
        Player p = e.getPlayer();
        int slot = e.getNewSlot();
        Bukkit.getScheduler().runTask(plugin, () -> {   // chờ 1 tick: CrazyEnchantments dựng lại món lúc đổi ô cầm (test bot 22/10: mất dòng ⛔)
            ItemStack cur = p.getInventory().getItem(slot);
            if (p.isOnline() && refresh(cur)) {
                p.getInventory().setItem(slot, cur);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p) {
            later(p);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) {
            later(p);
        }
    }

    // ------------------------------------------------------------------ sự kiện: vô hiệu hoá

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            String why = disabledBy(p, p.getInventory().getItemInMainHand());
            if (why != null) {
                e.setDamage(0);   // Đấu Trường bỏ huỷ ở HIGHEST: để 0 cho chắc
                e.setCancelled(true);
                warn(p, "&c⛔ Món này không dùng được ở " + why + " &7(/sachcam)");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(org.bukkit.event.player.PlayerSwapHandItemsEvent e) {
        String why = disabledBy(e.getPlayer(), e.getMainHandItem());
        if (why == null) {
            why = disabledBy(e.getPlayer(), e.getOffHandItem());
        }
        if (why != null) {
            e.setCancelled(true);
            warn(e.getPlayer(), "&c⛔ Món này không dùng được ở " + why + " &7(/sachcam)");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (e.getEntity() instanceof Player p) {
            String why = disabledBy(p, e.getBow());
            if (why != null) {
                e.setCancelled(true);
                warn(p, "&c⛔ Món này không dùng được ở " + why + " &7(/sachcam)");
            }
        }
    }

    /** Đinh ba, cầu tuyết, ngọc ender… phóng từ tay chính. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        Projectile pr = e.getEntity();
        if (pr.getShooter() instanceof Player p && !(pr instanceof org.bukkit.entity.AbstractArrow && !(pr instanceof org.bukkit.entity.Trident))) {
            String why = disabledBy(p, p.getInventory().getItemInMainHand());
            if (why != null) {
                e.setCancelled(true);
                warn(p, "&c⛔ Món này không dùng được ở " + why + " &7(/sachcam)");
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getItem() == null) {
            return;
        }
        String why = disabledBy(e.getPlayer(), e.getItem());
        if (why != null) {
            e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            warn(e.getPlayer(), "&c⛔ Món này không dùng được ở " + why + " &7(/sachcam)");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        String why = disabledBy(e.getPlayer(), e.getItem());
        if (why != null) {
            e.setCancelled(true);
            warn(e.getPlayer(), "&c⛔ Món này không dùng được ở " + why + " &7(/sachcam)");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTotem(EntityResurrectEvent e) {
        if (e.getEntity() instanceof Player p && e.getHand() != null) {
            String why = disabledBy(p, p.getInventory().getItem(e.getHand()));
            if (why != null) {
                e.setCancelled(true);
            }
        }
    }

    // ------------------------------------------------------------------ sự kiện: không cho mang vào

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo(), from = e.getFrom();
        if (to.getBlockX() == from.getBlockX() && to.getBlockZ() == from.getBlockZ()) {
            return;
        }
        Set<String> carry = CARRY_BLOCK.get(e.getPlayer().getUniqueId());
        if (carry == null || !carry.contains("cong-thanh") || !SiegeWarHook.battleActive()) {
            return;
        }
        if (SiegeWarHook.inActiveSiegeZone(to) && !SiegeWarHook.inActiveSiegeZone(from)) {
            e.setCancelled(true);
            warn(e.getPlayer(), "&c⛔ Đang mang đồ Sách cấm không cho vào vùng vây. Cất đồ rồi vào lại &7(/sachcam)");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (pages.isEmpty() || e.getTo().getWorld() == null) {
            return;
        }
        // kiểm thẳng túi đồ (22/10, test bot: đồ nhận bằng lệnh / gói quà không qua sự kiện nhặt nên CARRY_BLOCK cũ -> vào dungeon không bị chặn)
        Set<String> carry = carried(e.getPlayer());
        Player p = e.getPlayer();
        String wn = e.getTo().getWorld().getName().toLowerCase();
        if (wn.contains(":")) {
            wn = wn.substring(wn.indexOf(':') + 1);
        }
        boolean dungeon = carry.contains("dungeon") && wn.startsWith("em_") && !wn.equals("em_adventurers_guild")
                && e.getFrom().getWorld() != e.getTo().getWorld();
        boolean siege = carry.contains("cong-thanh") && SiegeWarHook.battleActive() && SiegeWarHook.inActiveSiegeZone(e.getTo())
                && !SiegeWarHook.inActiveSiegeZone(e.getFrom());
        if ((dungeon && !mayEnter(p, "dungeon")) || (siege && !mayEnter(p, "cong-thanh"))) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ /sachcam

    private static final Map<String, org.bukkit.Material> ACT_ICON = Map.of("cong-thanh", org.bukkit.Material.RED_BANNER,
            "dau-truong", org.bukkit.Material.IRON_SWORD, "dungeon", org.bukkit.Material.SPAWNER);

    /** /sachcam cho người chơi: menu (22/10, trước in chat). Mỗi hoạt động một ô; ô đầu trang = món đang cầm bị cấm ở đâu. */
    private void menu(Player p) {
        InfoMenu m = InfoMenu.of("&c⛔ Sách cấm").accent(org.bukkit.Material.RED_STAINED_GLASS_PANE);
        ItemStack hand = p.getInventory().getItemInMainHand();
        List<String> mine = loreLines(hand);
        Tip top = Tip.of("&c⛔ &fSách cấm").accent("&c").desc("Luật cấm đồ theo hoạt động.", "Đồ bị cấm có dòng ⛔ cuối mô tả.").line()
                .head("Món đang cầm");
        if (hand.getType().isAir()) {
            top.item("&7(tay trống)");
        } else if (mine.isEmpty()) {
            top.check(true, "không bị cấm ở đâu");
        } else {
            mine.forEach(l -> top.raw("  " + l));
        }
        top.gap().note("Không dùng được = món tối đi, không cầm / mặc được. Không cho mang vào = cất vào rương rồi mới vào được.");
        m.top(top.apply(new ItemStack(org.bukkit.Material.WRITTEN_BOOK)));
        for (String act : ACTIVITIES.keySet()) {
            Page pg = pages.get(act);
            Tip t = Tip.of("&c⛔ &f" + ACTIVITIES.get(act)).accent("&c");
            if (pg != null && !pg.desc().isBlank()) {
                t.desc(pg.desc());
            }
            boolean any = false;
            if (pg != null) {
                Map<Mode, List<String>> by = new HashMap<>();
                pg.items().forEach((id, mode) -> by.computeIfAbsent(mode, k -> new ArrayList<>()).add(name(id)));
                for (Mode mode : Mode.values()) {
                    if (by.containsKey(mode)) {
                        t.gap().head(Character.toUpperCase(mode.text.charAt(0)) + mode.text.substring(1)).items(by.get(mode));
                        any = true;
                    }
                }
                if (!pg.enchants().isEmpty()) {
                    List<String> n = new ArrayList<>();
                    pg.enchants().forEach(e -> n.add(SkillEnchants.NAMES.containsKey(e) ? SkillEnchants.NAMES.get(e)[0] : e));
                    t.gap().head("Phù phép riêng bị tắt").items(n);
                    any = true;
                }
            }
            if (!any) {
                t.line().check(true, "Không cấm món nào");
            }
            m.add(t.apply(new ItemStack(ACT_ICON.getOrDefault(act, org.bukkit.Material.BOOK))));
        }
        m.open(p);
    }

    boolean command(CommandSender s, String[] args) {
        reload();
        if (s instanceof Player pl) {
            menu(pl);
            return true;
        }
        s.sendMessage(c("&c⛔ SÁCH CẤM &7· luật cấm đồ theo hoạt động"));
        if (s instanceof Player p) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            List<String> mine = loreLines(hand);
            if (!hand.getType().isAir()) {
                s.sendMessage(c("&7Món đang cầm: " + (mine.isEmpty() ? "&akhông bị cấm ở đâu" : "")));
                mine.forEach(l -> s.sendMessage(c("  " + l)));
            }
        }
        boolean any = false;
        for (Page pg : pages.values()) {
            if (pg.items().isEmpty() && pg.enchants().isEmpty()) {
                continue;
            }
            any = true;
            s.sendMessage(c("&6⛔ " + ACTIVITIES.get(pg.id()) + (pg.desc().isBlank() ? "" : " &7— " + pg.desc())));
            Map<Mode, List<String>> by = new HashMap<>();
            pg.items().forEach((id, m) -> by.computeIfAbsent(m, k -> new ArrayList<>()).add(name(id)));
            for (Mode m : Mode.values()) {
                if (by.containsKey(m)) {
                    s.sendMessage(c("  &7" + Character.toUpperCase(m.text.charAt(0)) + m.text.substring(1) + ": &f" + String.join("&7, &f", by.get(m))));
                }
            }
            if (!pg.enchants().isEmpty()) {
                List<String> n = new ArrayList<>();
                pg.enchants().forEach(e -> n.add(SkillEnchants.NAMES.containsKey(e) ? SkillEnchants.NAMES.get(e)[0] : e));
                s.sendMessage(c("  &7Phù phép riêng bị tắt (mọi món): &f" + String.join("&7, &f", n)));
            }
        }
        if (!any) {
            s.sendMessage(c("&aHiện không cấm món nào."));
        }
        s.sendMessage(c("&7Không dùng được = món tối đi, không cầm / mặc được · Không cho mang vào = phải cất vào rương mới vào được"));
        return true;
    }
}
