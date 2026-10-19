package vn.duckymomo.core.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Xưởng chế tạo (/xuong): đồ đặc biệt theo logic "HIỆU ỨNG + ĐỘ BỀN + THỜI HẠN" (ý từ Server Tools của UniMC, tự làm lại).
 * Hết độ bền hoặc hết hạn (mặc định 7 ngày) thì món biến mất -> tiền và tài nguyên liên tục rời nền kinh tế.
 * Chế bằng xu + nguyên liệu xếp trên lưới 3×3 (grid: 9 ô "VẬT_LIỆU số" hoặc ""), bấm thành phẩm để chế.
 * Cấu hình workshop.items.<mã>: name, material, feature, value, text (chữ "Tính năng"), uses (độ bền, -1 = độ bền gốc), days, cost,
 *   grid | ingredients, enchants {sharpness: 5, enchantio:telepathy: 1, ce:Blast: 1}, pvp (true = mất tác dụng trong vùng vây đang đánh),
 *   description [], enabled. Sửa trên trang Cài đặt › Kinh tế › Xưởng chế tạo.
 * Hiệu ứng (feature) và value:
 *   ""          công cụ / vũ khí thường (phù phép, kể cả CrazyEnchantments "ce:Tên" và Enchantio)
 *   dao_vung    đào theo vùng: value 1 = 3×3, 2 = 5×5 (cuốc chim, xẻng, cuốc đất)       -> 1 độ bền / khối
 *   chat_cay    chặt cả cây: value = số khối gỗ tối đa                                      -> 1 / khối
 *   tu_nung     quặng / quặng thô ra thỏi 100%                                              -> 1 / khối
 *   da_nang     3 trong 1: tự đổi cuốc chim / rìu / xẻng theo khối đang đào                 -> 1 / khối
 *   gat_lua     gặt cây chín 3×3 + gieo lại (lấy hạt trong túi)                             -> 1 / khối
 *   hut_do      để trong túi: đồ mình đào / săn bay thẳng vào túi                           -> 1 / lần
 *   nam_cham    để trong túi: hút đồ rơi trong bán kính value khối (không hút đồ người khác vứt, đồ quanh chỗ chết) -> 1 / món hút
 *   ban_ruong   rìu: đánh vào rương / thùng là bán hết theo giá /sell (Essentials worth.yml) -> 1 / lần
 *   tui_hu      chuột phải mở rương ender ở bất cứ đâu                                      -> 1 / lần
 *   nuoc        nước vô tận · dung_nham: dung nham vô tận (Towny vẫn chặn đổ ở đất người khác) -> 1 / lần đổ
 *   bat_thu     chuột phải thú hiền (bò, cừu…) thành trứng sinh vật (không bắt quái, thú có chủ, pet MyPet) -> 1 / con
 *   co_le       phá lồng quái (spawner) rơi ra nguyên lồng                                  -> 1 / lồng
 *   moc_bay     cần câu: móc vào khối thì kéo bay tới (value = lực)                          -> 1 / lần
 *   hut_mau     cung / nỏ: mỗi phát trúng hồi value máu (2 = 1 tim)                          -> 1 / phát
 *   khien_than  khiên (cầm tay chính hoặc phụ): giảm value% sát thương, phản lại 1/2 phần giảm -> 1 / đòn đỡ
 *   kiem_nhat   chuột phải: lướt tới, chém xuyên mọi sinh vật trên đường (value = sát thương, hồi 4 giây) -> 1 / lần lướt
 *   riu_chien   đánh: value% chí mạng (×1,5) + chảy máu 3 giây                               -> 1 / đòn
 * Vũ khí / cung (feature trống) mất 1 độ bền mỗi đòn / phát; công cụ mất 1 mỗi khối. Món có độ bền riêng thì không hỏng theo độ bền gốc.
 * Dấu trên đồ (PDC): xuong_id, xuong_luot (độ bền còn, -1 = không đếm), xuong_het (hạn ms), xuong_celo (số dòng mô tả CE ở đầu lore).
 */
final class Workshop implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("HH:mm dd/MM").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    /** Hiệu ứng: mã -> [tên hiển thị, nghĩa của value]. Trang Cài đặt dùng cùng danh sách (panel/app.py WORKSHOP_FEATURES). */
    static final Map<String, String[]> FEATURES = new LinkedHashMap<>();

    static {
        FEATURES.put("", new String[]{"Phù phép", ""});
        FEATURES.put("dao_vung", new String[]{"Đào vùng", "1 = 3×3, 2 = 5×5"});
        FEATURES.put("chat_cay", new String[]{"Chặt cả cây", "số khối gỗ tối đa"});
        FEATURES.put("tu_nung", new String[]{"Tự nung", ""});
        FEATURES.put("da_nang", new String[]{"3 trong 1", ""});
        FEATURES.put("gat_lua", new String[]{"Thu hoạch 3×3 + gieo lại", ""});
        FEATURES.put("hut_do", new String[]{"Đồ bay vào túi", ""});
        FEATURES.put("nam_cham", new String[]{"Hút đồ", "bán kính (khối)"});
        FEATURES.put("ban_ruong", new String[]{"Bán cả rương", ""});
        FEATURES.put("tui_hu", new String[]{"Rương ender từ xa", ""});
        FEATURES.put("nuoc", new String[]{"Nước vô tận", ""});
        FEATURES.put("dung_nham", new String[]{"Dung nham vô tận", ""});
        FEATURES.put("bat_thu", new String[]{"Bắt thú thành trứng", ""});
        FEATURES.put("co_le", new String[]{"Lấy lồng quái", ""});
        FEATURES.put("moc_bay", new String[]{"Móc câu bay", "lực kéo (1–3)"});
        FEATURES.put("hut_mau", new String[]{"Hút máu", "máu hồi mỗi phát (2 = 1 tim)"});
        FEATURES.put("khien_than", new String[]{"Giảm sát thương + phản đòn", "% giảm"});
        FEATURES.put("kiem_nhat", new String[]{"Lướt chém", "sát thương"});
        FEATURES.put("riu_chien", new String[]{"Chí mạng + chảy máu", "% chí mạng"});
    }

    private static final Set<String> BLOCK_COUNT = Set.of("", "dao_vung", "chat_cay", "tu_nung", "da_nang", "gat_lua", "co_le");
    private static final Map<Material, Material> SMELT = new HashMap<>();

    static {
        SMELT.put(Material.RAW_IRON, Material.IRON_INGOT);
        SMELT.put(Material.RAW_GOLD, Material.GOLD_INGOT);
        SMELT.put(Material.RAW_COPPER, Material.COPPER_INGOT);
        SMELT.put(Material.IRON_ORE, Material.IRON_INGOT);
        SMELT.put(Material.DEEPSLATE_IRON_ORE, Material.IRON_INGOT);
        SMELT.put(Material.GOLD_ORE, Material.GOLD_INGOT);
        SMELT.put(Material.DEEPSLATE_GOLD_ORE, Material.GOLD_INGOT);
        SMELT.put(Material.COPPER_ORE, Material.COPPER_INGOT);
        SMELT.put(Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT);
        SMELT.put(Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP);
    }

    record Recipe(String id, String name, Material material, String feature, double value, String text, int uses, int days, double cost,
                  List<String> grid, Map<Material, Integer> ingredients, Map<String, Integer> enchants, boolean pvp, List<String> desc,
                  Map<String, Integer> refined) { }

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        Recipe view;   // null = menu chính, khác null = bảng chế 3×3 của món đó
        final Map<Integer, Recipe> slots = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static final int[] MAIN_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private static final int[] GRID_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int ARROW_SLOT = 23;
    private static final int RESULT_SLOT = 25;
    private static final int BACK_SLOT = 45;

    private final JavaPlugin plugin;
    private final Map<String, Recipe> recipes = new LinkedHashMap<>();
    private final NamespacedKey kId;
    private final NamespacedKey kUses;
    private final NamespacedKey kUntil;
    private final NamespacedKey kArrow;
    private final NamespacedKey kCeLore;
    private final CeBridge ce;
    private final Map<Material, Double> worth = new HashMap<>();
    private final Set<UUID> busy = new HashSet<>();                 // đang phá khối thay người chơi (chặn đệ quy)
    private final Map<UUID, Long> dashCd = new HashMap<>();
    private final ArrayDeque<double[]> deaths = new ArrayDeque<>(); // {x, y, z, ms, worldHash} chỗ người chơi chết (nam châm không hút)

    Workshop(JavaPlugin plugin, ConfigurationSection cfg) {
        this.plugin = plugin;
        this.kId = new NamespacedKey(plugin, "xuong_id");
        this.kUses = new NamespacedKey(plugin, "xuong_luot");
        this.kUntil = new NamespacedKey(plugin, "xuong_het");
        this.kArrow = new NamespacedKey(plugin, "xuong_hutmau");
        this.kCeLore = new NamespacedKey(plugin, "xuong_celo");
        this.ce = new CeBridge(plugin.getLogger());
        ConfigurationSection items = cfg.getConfigurationSection("items");
        if (items != null) {
            for (String id : items.getKeys(false)) {
                ConfigurationSection r = items.getConfigurationSection(id);
                if (r == null || !r.getBoolean("enabled", true)) {
                    continue;
                }
                Material m = Material.matchMaterial(r.getString("material", "STONE"));
                if (m == null || !m.isItem()) {
                    plugin.getLogger().warning("Xưởng: " + id + " có material sai, bỏ qua");
                    continue;
                }
                String feature = r.getString("feature", "");
                if (!FEATURES.containsKey(feature)) {
                    plugin.getLogger().warning("Xưởng: " + id + " có hiệu ứng lạ '" + feature + "', coi như không hiệu ứng");
                    feature = "";
                }
                List<String> grid = new ArrayList<>(r.getStringList("grid"));
                Map<Material, Integer> ing = new LinkedHashMap<>();
                if (!grid.isEmpty()) {
                    while (grid.size() < 9) {
                        grid.add("");
                    }
                    for (String g : grid.subList(0, 9)) {
                        Object[] gm = cell(g);
                        if (gm != null) {
                            ing.merge((Material) gm[0], (Integer) gm[1], Integer::sum);
                        }
                    }
                } else {
                    ConfigurationSection is = r.getConfigurationSection("ingredients");
                    if (is != null) {
                        for (String k : is.getKeys(false)) {
                            Material im = Material.matchMaterial(k);
                            if (im != null && is.getInt(k) > 0) {
                                ing.put(im, is.getInt(k));
                                grid.add(im.name() + " " + is.getInt(k));
                            }
                        }
                    }
                    while (grid.size() < 9) {
                        grid.add("");
                    }
                }
                Map<String, Integer> ench = new LinkedHashMap<>();
                ConfigurationSection es = r.getConfigurationSection("enchants");
                if (es != null) {
                    for (String k : es.getKeys(false)) {
                        ench.put(k, es.getInt(k));
                    }
                }
                // món Tinh Luyện cần thêm (tinh-luyen: {sat: 1, kimcuong: 1}), kiểm/trừ qua Shared.refining
                Map<String, Integer> refined = new LinkedHashMap<>();
                ConfigurationSection rf = r.getConfigurationSection("tinh-luyen");
                if (rf != null) {
                    for (String k : rf.getKeys(false)) {
                        if (rf.getInt(k) > 0) {
                            refined.put(k.toLowerCase(java.util.Locale.ROOT), rf.getInt(k));
                        }
                    }
                }
                recipes.put(id, new Recipe(id, r.getString("name", id), m, feature, r.getDouble("value", 0), r.getString("text", ""),
                        r.getInt("uses", -1), r.getInt("days", 7), r.getDouble("cost", 0), grid.subList(0, 9), ing, ench,
                        r.getBoolean("pvp", false), r.getStringList("description"), refined));
            }
        }
        loadWorth();
    }

    /** "VẬT_LIỆU số" | "VẬT_LIỆU:số" | "VẬT_LIỆU" -> {Material, số}; ô trống / sai -> null. */
    private static Object[] cell(String g) {
        if (g == null || g.isBlank()) {
            return null;
        }
        String[] a = g.trim().split("[\\s:x×]+");
        Material im = Material.matchMaterial(a[0]);
        if (im == null || !im.isItem()) {
            return null;
        }
        int n = 1;
        try {
            n = a.length > 1 ? Math.max(1, Math.min(640, Integer.parseInt(a[1]))) : 1;
        } catch (NumberFormatException ignored) {
            // 1
        }
        return new Object[]{im, n};
    }

    private void loadWorth() {
        File f = new File(plugin.getDataFolder().getParentFile(), "Essentials/worth.yml");
        ConfigurationSection w = YamlConfiguration.loadConfiguration(f).getConfigurationSection("worth");
        if (w == null) {
            return;
        }
        for (String k : w.getKeys(false)) {
            Material m = Material.matchMaterial(k);
            if (m != null && w.isDouble(k) || m != null && w.isInt(k)) {
                worth.put(m, w.getDouble(k));
            }
        }
    }

    int size() {
        return recipes.size();
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::sweep), 600L, 1200L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::magnetTick, 40L, 10L);
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    private static String money(double v) {
        return VaultEconomy.available() ? VaultEconomy.format(v) : (long) v + " xu";
    }

    private static String fmt(int n) {
        return String.format(Locale.ROOT, "%,d", n).replace(',', '.');
    }

    private static String featureText(Recipe r) {
        if (!r.text().isBlank()) {
            return r.text();
        }
        String base = FEATURES.getOrDefault(r.feature(), new String[]{"Phù phép", ""})[0];
        return switch (r.feature()) {
            case "dao_vung" -> base + " &e[" + (2 * (int) Math.max(1, r.value()) + 1) + "×" + (2 * (int) Math.max(1, r.value()) + 1) + " khối]";
            case "nam_cham" -> base + " &e[bán kính " + (int) r.value() + " khối]";
            case "hut_mau" -> base + " &e[+" + fmtD(r.value() / 2) + " tim/phát]";
            case "khien_than" -> base + " &e[giảm " + (int) r.value() + "%]";
            case "riu_chien" -> base + " &e[" + (int) r.value() + "% chí mạng]";
            default -> base;
        };
    }

    private static String fmtD(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    // ------------------------------------------------------------ dựng đồ, độ bền, hạn

    private Recipe recipeOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        String id = it.getItemMeta().getPersistentDataContainer().get(kId, PersistentDataType.STRING);
        return id == null ? null : recipes.get(id);
    }

    private boolean expired(ItemStack it) {
        Long until = it.getItemMeta().getPersistentDataContainer().get(kUntil, PersistentDataType.LONG);
        return until != null && System.currentTimeMillis() > until;
    }

    private ItemStack build(Recipe r) {
        ItemStack it = new ItemStack(r.material());
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(r.name()));
        List<String[]> ceList = new ArrayList<>();
        for (Map.Entry<String, Integer> e : r.enchants().entrySet()) {
            String k = e.getKey();
            if (k.regionMatches(true, 0, "ce:", 0, 3)) {
                ceList.add(new String[]{k.substring(3), String.valueOf(e.getValue())});
                continue;
            }
            NamespacedKey nk = k.contains(":") ? NamespacedKey.fromString(k.toLowerCase(Locale.ROOT)) : NamespacedKey.minecraft(k.toLowerCase(Locale.ROOT));
            Enchantment en = nk == null ? null : Registry.ENCHANTMENT.get(nk);
            if (en != null && e.getValue() > 0) {
                meta.addEnchant(en, e.getValue(), true);
            } else {
                plugin.getLogger().warning("Xưởng: " + r.id() + " phù phép lạ " + k);
            }
        }
        if (r.uses() >= 0) {
            meta.setUnbreakable(true);   // độ bền tính riêng (xuong_luot), không hỏng theo độ bền gốc
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(kId, PersistentDataType.STRING, r.id());
        pdc.set(kUses, PersistentDataType.INTEGER, r.uses());
        pdc.set(kUntil, PersistentDataType.LONG, System.currentTimeMillis() + r.days() * 86_400_000L);
        it.setItemMeta(meta);
        for (String[] x : ceList) {
            if (!ce.add(it, x[0], Integer.parseInt(x[1]))) {
                plugin.getLogger().warning("Xưởng: " + r.id() + " không gắn được phù phép CrazyEnchantments " + x[0]);
            }
        }
        ItemMeta m2 = it.getItemMeta();
        List<Component> ceLore = m2.lore();
        m2.getPersistentDataContainer().set(kCeLore, PersistentDataType.INTEGER, ceLore == null ? 0 : ceLore.size());
        it.setItemMeta(m2);
        relore(it, r);
        return it;
    }

    private void relore(ItemStack it, Recipe r) {
        ItemMeta meta = it.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int uses = pdc.getOrDefault(kUses, PersistentDataType.INTEGER, -1);
        long until = pdc.getOrDefault(kUntil, PersistentDataType.LONG, 0L);
        int keep = pdc.getOrDefault(kCeLore, PersistentDataType.INTEGER, 0);
        List<Component> old = meta.lore();
        List<Component> lore = new ArrayList<>();
        if (old != null) {
            lore.addAll(old.subList(0, Math.min(keep, old.size())));   // dòng mô tả của CrazyEnchantments
        }
        lore.add(c("&7— Tính năng: &b" + featureText(r)));
        if (uses >= 0) {
            lore.add(c("&7— Độ bền: &6" + fmt(uses)));
        }
        lore.add(c("&7— Hết hạn: &b" + WHEN.format(Instant.ofEpochMilli(until))));
        for (String d : r.desc()) {
            lore.add(c("&8" + d));
        }
        if (r.pvp()) {
            lore.add(c("&c✖ Mất tác dụng trong vùng vây khi đang đánh"));
        }
        meta.lore(lore);
        it.setItemMeta(meta);
    }

    /** Trừ n độ bền. Trả về đồ sau khi trừ, hoặc null nếu hết (đồ biến mất). */
    private ItemStack use(ItemStack it, Recipe r, int n) {
        ItemMeta meta = it.getItemMeta();
        int uses = meta.getPersistentDataContainer().getOrDefault(kUses, PersistentDataType.INTEGER, -1);
        if (uses < 0) {
            return it;
        }
        if (uses <= n) {
            return null;
        }
        meta.getPersistentDataContainer().set(kUses, PersistentDataType.INTEGER, uses - n);
        it.setItemMeta(meta);
        relore(it, r);
        return it;
    }

    private ItemStack useOnce(ItemStack it, Recipe r) {
        return use(it, r, 1);
    }

    /** Trừ độ bền món đang cầm tay chính; hết thì báo. */
    private void spendHand(Player p, Recipe r, int n) {
        ItemStack after = use(p.getInventory().getItemInMainHand(), r, n);
        p.getInventory().setItemInMainHand(after);
        if (after == null) {
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            p.sendMessage(c("&7[Xưởng] " + r.name() + " &7đã mòn hết. Chế lại: &e/xuong"));
        }
    }

    /** Xoá đồ xưởng hết hạn trong túi. */
    private void sweep(Player p) {
        ItemStack[] inv = p.getInventory().getContents();
        for (int i = 0; i < inv.length; i++) {
            Recipe r = recipeOf(inv[i]);
            if (r != null && expired(inv[i])) {
                p.getInventory().setItem(i, null);
                p.sendMessage(c("&7[Xưởng] " + r.name() + " &7đã hết hạn và biến mất. Chế lại: &e/xuong"));
            }
        }
    }

    private boolean blockedHere(Player p, Recipe r) {
        return r.pvp() && SiegeWarHook.battleActive() && SiegeWarHook.inActiveSiegeZone(p.getLocation());
    }

    /** Món cầm tay còn dùng được không: hết hạn thì xoá; món pvp trong vùng vây đang đánh thì báo. */
    private boolean usable(Player p, ItemStack it, Recipe r, EquipmentSlot slot) {
        if (expired(it)) {
            p.getInventory().setItem(slot, null);
            p.sendMessage(c("&7[Xưởng] " + r.name() + " &7đã hết hạn và biến mất."));
            return false;
        }
        if (blockedHere(p, r)) {
            p.sendActionBar(c("&c" + r.name() + " &cmất tác dụng trong vùng vây khi đang đánh"));
            return false;
        }
        return true;
    }

    private static boolean isTool(Material m) {
        String n = m.name();
        return n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE") || m == Material.SHEARS;
    }

    // ------------------------------------------------------------ phá khối: đào vùng, chặt cây, gặt lúa, lấy lồng, đếm độ bền

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreakEffect(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (busy.contains(p.getUniqueId())) {
            return;   // khối phụ do chính hiệu ứng phá: không lan tiếp
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        Recipe r = recipeOf(hand);
        if (r == null || !usable(p, hand, r, EquipmentSlot.HAND)) {
            return;
        }
        Block b = e.getBlock();
        switch (r.feature()) {
            case "dao_vung" -> {
                int rad = (int) Math.max(1, Math.min(2, r.value()));
                List<Block> more = new ArrayList<>();
                float pitch = p.getLocation().getPitch();
                BlockFace face = Math.abs(pitch) > 50 ? BlockFace.UP : p.getFacing();
                for (int a = -rad; a <= rad; a++) {
                    for (int c2 = -rad; c2 <= rad; c2++) {
                        if (a == 0 && c2 == 0) {
                            continue;
                        }
                        Block o = face == BlockFace.UP ? b.getRelative(a, 0, c2)
                                : face == BlockFace.NORTH || face == BlockFace.SOUTH ? b.getRelative(a, c2, 0) : b.getRelative(0, c2, a);
                        if (o.getType().isAir() || o.isLiquid() || o.getType().getHardness() < 0 || o.getState() instanceof Container
                                || o.getType() == Material.SPAWNER || !o.isPreferredTool(hand)) {
                            continue;
                        }
                        more.add(o);
                    }
                }
                breakAll(p, more);
            }
            case "chat_cay" -> {
                if (!Tag.LOGS.isTagged(b.getType())) {
                    return;
                }
                int max = (int) Math.max(8, Math.min(256, r.value() <= 0 ? 150 : r.value()));
                List<Block> logs = new ArrayList<>();
                ArrayDeque<Block> q = new ArrayDeque<>(List.of(b));
                Set<Block> seen = new HashSet<>(List.of(b));
                while (!q.isEmpty() && logs.size() < max) {
                    Block cur = q.poll();
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = 0; dy <= 1; dy++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                Block n = cur.getRelative(dx, dy, dz);
                                if (seen.add(n) && Tag.LOGS.isTagged(n.getType())) {
                                    logs.add(n);
                                    q.add(n);
                                }
                            }
                        }
                    }
                }
                breakAll(p, logs);
            }
            case "gat_lua" -> {
                if (!(b.getBlockData() instanceof Ageable ag) || ag.getAge() < ag.getMaximumAge()) {
                    return;
                }
                List<Block> crops = new ArrayList<>();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block o = b.getRelative(dx, 0, dz);
                        if ((dx != 0 || dz != 0) && o.getBlockData() instanceof Ageable a2 && a2.getAge() >= a2.getMaximumAge() && o.getType() == b.getType()) {
                            crops.add(o);
                        }
                    }
                }
                List<Block> all = new ArrayList<>(crops);
                all.add(b);
                Material crop = b.getType();
                breakAll(p, crops);
                Bukkit.getScheduler().runTask(plugin, () -> replant(p, all, crop));
            }
            case "co_le" -> {
                if (b.getType() != Material.SPAWNER || !(b.getState() instanceof CreatureSpawner sp)) {
                    return;
                }
                ItemStack drop = new ItemStack(Material.SPAWNER);
                if (drop.getItemMeta() instanceof BlockStateMeta bsm) {
                    CreatureSpawner st = (CreatureSpawner) bsm.getBlockState();
                    st.setSpawnedType(sp.getSpawnedType());
                    bsm.setBlockState(st);
                    drop.setItemMeta(bsm);
                }
                e.setExpToDrop(0);
                e.setDropItems(false);
                b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), drop);
            }
            default -> { }
        }
    }

    private static Material seedOf(Material crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case NETHER_WART -> Material.NETHER_WART;
            default -> null;
        };
    }

    private void replant(Player p, List<Block> blocks, Material crop) {
        Material seed = seedOf(crop);
        if (seed == null) {
            return;
        }
        for (Block bl : blocks) {
            Material below = bl.getRelative(BlockFace.DOWN).getType();
            boolean soil = crop == Material.NETHER_WART ? below == Material.SOUL_SAND : below == Material.FARMLAND;
            if (!bl.getType().isAir() || !soil || !p.getInventory().containsAtLeast(new ItemStack(seed), 1)) {
                continue;
            }
            p.getInventory().removeItem(new ItemStack(seed, 1));
            bl.setType(crop);
        }
    }

    /** Phá các khối như chính người chơi phá (BlockBreakEvent: Towny, WorldGuard, CoreProtect đều thấy). */
    private void breakAll(Player p, List<Block> blocks) {
        if (blocks.isEmpty()) {
            return;
        }
        busy.add(p.getUniqueId());
        try {
            for (Block o : blocks) {
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (recipeOf(hand) == null) {
                    break;   // món vừa mòn hết
                }
                p.breakBlock(o);
            }
        } finally {
            busy.remove(p.getUniqueId());
        }
    }

    /** 1 độ bền / khối cho công cụ xưởng (kể cả khối phụ của đào vùng, chặt cây). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakCount(BlockBreakEvent e) {
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        Recipe r = recipeOf(hand);
        if (r != null && BLOCK_COUNT.contains(r.feature()) && isTool(hand.getType())) {
            spendHand(p, r, 1);
        }
    }

    // ------------------------------------------------------------ chuột trái / phải

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() == null) {
            return;
        }
        ItemStack it = e.getItem();
        Recipe r = recipeOf(it);
        if (r == null) {
            return;
        }
        Player p = e.getPlayer();
        boolean right = e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK;
        switch (r.feature()) {
            case "tui_hu" -> {
                if (!right) {
                    return;
                }
                e.setCancelled(true);   // không đặt rương ender xuống
                if (!usable(p, it, r, e.getHand())) {
                    return;
                }
                if (SiegeWarHook.battleActive() && SiegeWarHook.inActiveSiegeZone(p.getLocation())) {
                    p.sendMessage(c("&c[Xưởng] Không mở rương ender từ xa trong vùng vây đang đánh."));
                    return;
                }
                p.getInventory().setItem(e.getHand(), useOnce(it, r));
                p.openInventory(p.getEnderChest());
                p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6f, 1f);
            }
            case "da_nang" -> {
                if (e.getAction() != Action.LEFT_CLICK_BLOCK || e.getClickedBlock() == null || e.getHand() != EquipmentSlot.HAND) {
                    return;
                }
                Block b = e.getClickedBlock();
                String tier = it.getType().name().substring(0, it.getType().name().lastIndexOf('_'));
                String kind = Tag.MINEABLE_AXE.isTagged(b.getType()) ? "_AXE" : Tag.MINEABLE_SHOVEL.isTagged(b.getType()) ? "_SHOVEL" : "_PICKAXE";
                Material want = Material.matchMaterial(tier + kind);
                if (want != null && want != it.getType()) {
                    p.getInventory().setItemInMainHand(it.withType(want));
                }
            }
            case "ban_ruong" -> {
                if (e.getAction() != Action.LEFT_CLICK_BLOCK || e.getClickedBlock() == null || !(e.getClickedBlock().getState() instanceof Container box)
                        || e.getHand() != EquipmentSlot.HAND) {
                    return;
                }
                e.setCancelled(true);
                if (!usable(p, it, r, EquipmentSlot.HAND) || !canOpen(p, e.getClickedBlock())) {
                    if (!canOpen(p, e.getClickedBlock())) {
                        p.sendActionBar(c("&cBạn không có quyền mở rương này."));
                    }
                    return;
                }
                sellContainer(p, box, r);
            }
            case "kiem_nhat" -> {
                if (!right || e.getHand() != EquipmentSlot.HAND || !usable(p, it, r, EquipmentSlot.HAND)) {
                    return;
                }
                long now = System.currentTimeMillis();
                if (dashCd.getOrDefault(p.getUniqueId(), 0L) > now) {
                    p.sendActionBar(c("&7Lướt chém hồi trong " + (dashCd.get(p.getUniqueId()) - now) / 1000 + " giây"));
                    return;
                }
                dashCd.put(p.getUniqueId(), now + 4000);
                dash(p, r);
                spendHand(p, r, 1);
            }
            default -> { }
        }
    }

    /** Có quyền mở rương ở chỗ này không (Towny: quyền SWITCH; không có Towny thì được). */
    private static boolean canOpen(Player p, Block b) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Towny")) {
            return true;
        }
        try {
            return com.palmergames.bukkit.towny.utils.PlayerCacheUtil.getCachePermission(p, b.getLocation(), b.getType(),
                    com.palmergames.bukkit.towny.object.TownyPermission.ActionType.SWITCH);
        } catch (RuntimeException | LinkageError ex) {
            return false;
        }
    }

    private void sellContainer(Player p, Container box, Recipe r) {
        if (!VaultEconomy.available()) {
            return;
        }
        Inventory inv = box.getInventory();
        double total = 0;
        int count = 0;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.hasItemMeta() && !s.isSimilar(new ItemStack(s.getType()))) {
                continue;   // đồ có tên / phù phép / dữ liệu riêng: không bán (giống /sell)
            }
            Double price = worth.get(s.getType());
            if (price == null || price <= 0) {
                continue;
            }
            total += price * s.getAmount();
            count += s.getAmount();
            inv.setItem(i, null);
        }
        if (count == 0) {
            p.sendActionBar(c("&7Rương không có gì bán được (bảng giá /sell)."));
            return;
        }
        VaultEconomy.deposit(p, total);
        Frontier f = Shared.frontier;
        if (f != null) {
            f.addSale(p, total);   // như /sell: góp % vào quỹ khai hoang
        }
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        p.sendMessage(c("&a[Xưởng] Đã bán " + fmt(count) + " món trong rương: &e+" + money(total)));
        spendHand(p, r, 1);
    }

    private void dash(Player p, Recipe r) {
        Vector dir = p.getLocation().getDirection().setY(0).normalize().multiply(1.6).setY(0.2);
        p.setVelocity(dir);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.4f);
        double dmg = r.value() <= 0 ? 6 : r.value();
        Set<UUID> hit = new HashSet<>();
        for (int t = 1; t <= 8; t++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) {
                    return;
                }
                p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p.getLocation().add(0, 1, 0), 1);
                for (Entity en : p.getNearbyEntities(1.6, 1.2, 1.6)) {
                    if (en instanceof LivingEntity le && en != p && hit.add(en.getUniqueId())) {
                        le.damage(dmg, p);   // qua EntityDamageByEntityEvent: Towny/PvP vẫn kiểm
                    }
                }
            }, t);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityClick(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        ItemStack it = p.getInventory().getItem(e.getHand());
        Recipe r = recipeOf(it);
        if (r == null || !r.feature().equals("bat_thu") || !(e.getRightClicked() instanceof Animals a)) {
            return;
        }
        e.setCancelled(true);
        if (!usable(p, it, r, e.getHand())) {
            return;
        }
        if (a instanceof Tameable t && t.isTamed() || MyPetBridge.of(a) != null || a.customName() != null) {
            p.sendActionBar(c("&cKhông bắt thú có chủ, pet hoặc thú đã đặt tên."));
            return;
        }
        if (Bukkit.getPluginManager().isPluginEnabled("Towny") && !townyCanBuild(p, a.getLocation())) {
            p.sendActionBar(c("&cKhông bắt thú trong đất của người khác."));
            return;
        }
        Material egg = Material.matchMaterial(a.getType().name() + "_SPAWN_EGG");
        if (egg == null) {
            return;
        }
        a.remove();
        Rewards.hand(p, new ItemStack(egg));
        p.getWorld().spawnParticle(Particle.CLOUD, a.getLocation().add(0, 0.5, 0), 12, 0.3, 0.3, 0.3, 0.02);
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.8f);
        p.getInventory().setItem(e.getHand(), useOnce(p.getInventory().getItem(e.getHand()), r));
    }

    private static boolean townyCanBuild(Player p, Location l) {
        try {
            return com.palmergames.bukkit.towny.utils.PlayerCacheUtil.getCachePermission(p, l, Material.DIRT,
                    com.palmergames.bukkit.towny.object.TownyPermission.ActionType.BUILD);
        } catch (RuntimeException | LinkageError ex) {
            return false;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        Player p = e.getPlayer();
        ItemStack it = p.getInventory().getItem(e.getHand());
        Recipe r = recipeOf(it);
        if (r == null || !r.feature().equals("nuoc") && !r.feature().equals("dung_nham")) {
            return;
        }
        if (expired(it)) {
            e.setItemStack(new ItemStack(Material.BUCKET));
            p.sendMessage(c("&7[Xưởng] " + r.name() + " &7đã hết hạn."));
            return;
        }
        if (r.feature().equals("dung_nham") && SiegeWarHook.battleActive() && SiegeWarHook.inActiveSiegeZone(e.getBlock().getLocation())) {
            e.setCancelled(true);
            p.sendActionBar(c("&cKhông đổ dung nham trong vùng vây đang đánh."));
            return;
        }
        ItemStack after = useOnce(it.clone(), r);
        e.setItemStack(after == null ? new ItemStack(Material.BUCKET) : after);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        Player p = e.getPlayer();
        ItemStack it = p.getInventory().getItemInMainHand();
        Recipe r = recipeOf(it);
        if (r == null || !r.feature().equals("moc_bay")) {
            return;
        }
        if (e.getState() != PlayerFishEvent.State.IN_GROUND && e.getState() != PlayerFishEvent.State.REEL_IN) {
            return;
        }
        Location hook = e.getHook().getLocation();
        if (!e.getHook().isOnGround() && !hook.clone().add(0, -0.3, 0).getBlock().getType().isSolid()) {
            return;
        }
        if (!usable(p, it, r, EquipmentSlot.HAND)) {
            return;
        }
        double force = Math.max(0.5, Math.min(3, r.value() <= 0 ? 1.5 : r.value()));
        Vector v = hook.toVector().subtract(p.getLocation().toVector());
        double dist = v.length();
        v.normalize().multiply(Math.min(3.5, 0.5 + dist * 0.12) * force / 1.5).setY(Math.max(0.4, v.getY() * 0.9 + 0.35));
        p.setVelocity(v);
        p.setFallDistance(0);
        p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 0.7f);
        spendHand(p, r, 1);
    }

    // ------------------------------------------------------------ hút đồ, tự nung

    /** Ô trong túi có món mang tính năng feature còn hạn, -1 nếu không có. */
    private int findInBag(Player p, String feature) {
        ItemStack[] inv = p.getInventory().getContents();
        for (int i = 0; i < inv.length; i++) {
            Recipe r = recipeOf(inv[i]);
            if (r != null && r.feature().equals(feature) && !expired(inv[i])) {
                return i;
            }
        }
        return -1;
    }

    private void spendInBag(Player p, int slot, int n) {
        ItemStack it = p.getInventory().getItem(slot);
        Recipe r = recipeOf(it);
        if (r != null) {
            ItemStack after = use(it, r, n);
            p.getInventory().setItem(slot, after);
            if (after == null) {
                p.sendMessage(c("&7[Xưởng] " + r.name() + " &7đã mòn hết. Chế lại: &e/xuong"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDrops(BlockDropItemEvent e) {
        Player p = e.getPlayer();
        Recipe hand = recipeOf(p.getInventory().getItemInMainHand());
        if (hand != null && hand.feature().equals("tu_nung")) {
            for (Item drop : e.getItems()) {
                Material to = SMELT.get(drop.getItemStack().getType());
                if (to != null) {
                    drop.setItemStack(new ItemStack(to, drop.getItemStack().getAmount()));
                }
            }
        }
        int magnet = findInBag(p, "hut_do");
        if (magnet < 0 || e.getItems().isEmpty()) {
            return;
        }
        boolean took = false;
        for (Iterator<Item> iter = e.getItems().iterator(); iter.hasNext(); ) {
            Item drop = iter.next();
            Map<Integer, ItemStack> rest = p.getInventory().addItem(drop.getItemStack());
            if (rest.isEmpty()) {
                iter.remove();
                took = true;
            } else {
                drop.setItemStack(rest.values().iterator().next());
            }
        }
        if (took) {
            spendInBag(p, magnet, 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobDrops(EntityDeathEvent e) {
        Player p = e.getEntity().getKiller();
        if (p == null || e.getEntity() instanceof Player || e.getDrops().isEmpty()) {
            return;
        }
        int magnet = findInBag(p, "hut_do");
        if (magnet < 0) {
            return;
        }
        boolean took = false;
        for (Iterator<ItemStack> iter = e.getDrops().iterator(); iter.hasNext(); ) {
            ItemStack drop = iter.next();
            Map<Integer, ItemStack> rest = p.getInventory().addItem(drop);
            if (rest.isEmpty()) {
                iter.remove();
                took = true;
            }
        }
        if (took) {
            spendInBag(p, magnet, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent e) {
        Location l = e.getPlayer().getLocation();
        deaths.addLast(new double[]{l.getX(), l.getY(), l.getZ(), System.currentTimeMillis(), l.getWorld().getName().hashCode()});
    }

    private boolean nearDeath(Location l) {
        long now = System.currentTimeMillis();
        while (!deaths.isEmpty() && now - deaths.peekFirst()[3] > 300_000) {
            deaths.pollFirst();
        }
        for (double[] d : deaths) {
            if (d[4] == l.getWorld().getName().hashCode() && Math.abs(d[0] - l.getX()) < 10 && Math.abs(d[1] - l.getY()) < 10 && Math.abs(d[2] - l.getZ()) < 10) {
                return true;
            }
        }
        return false;
    }

    /** Nam châm: mỗi nửa giây kéo đồ rơi trong bán kính về phía người chơi. */
    private void magnetTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            int slot = findInBag(p, "nam_cham");
            if (slot < 0) {
                continue;
            }
            Recipe r = recipeOf(p.getInventory().getItem(slot));
            if (r == null || blockedHere(p, r)) {
                continue;
            }
            double rad = Math.max(2, Math.min(32, r.value() <= 0 ? 16 : r.value()));
            int pulled = 0;
            for (Entity en : p.getNearbyEntities(rad, rad, rad)) {
                if (!(en instanceof Item item) || item.getPickupDelay() > 0 && item.getTicksLived() < 20) {
                    continue;
                }
                UUID thrower = item.getThrower();
                if (thrower != null && !thrower.equals(p.getUniqueId()) || nearDeath(item.getLocation())) {
                    continue;   // không hút đồ người khác vứt, đồ quanh chỗ người chết
                }
                if (item.getLocation().distanceSquared(p.getLocation()) < 2.25) {
                    continue;
                }
                item.teleport(p.getLocation());
                pulled++;
            }
            if (pulled > 0) {
                spendInBag(p, slot, pulled);
            }
        }
    }

    // ------------------------------------------------------------ đánh nhau: cung, khiên, rìu chiến, vũ khí thường

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p)) {
            return;
        }
        ItemStack bow = e.getBow();
        Recipe r = recipeOf(bow);
        if (r == null) {
            return;
        }
        if (expired(bow)) {
            e.setCancelled(true);
            p.getInventory().setItem(e.getHand(), null);
            p.sendMessage(c("&7[Xưởng] " + r.name() + " &7đã hết hạn và biến mất."));
            return;
        }
        if (r.feature().equals("hut_mau")) {
            e.getProjectile().getPersistentDataContainer().set(kArrow, PersistentDataType.DOUBLE, r.value());
        }
        ItemStack after = useOnce(bow, r);
        p.getInventory().setItem(e.getHand(), after);
        if (after == null) {
            p.sendMessage(c("&7[Xưởng] " + r.name() + " &7đã mòn hết."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        // cung hút máu
        if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player shooter) {
            Double heal = pr.getPersistentDataContainer().get(kArrow, PersistentDataType.DOUBLE);
            if (heal != null && heal > 0 && e.getEntity() instanceof LivingEntity) {
                if (SiegeWarHook.battleActive() && SiegeWarHook.inActiveSiegeZone(shooter.getLocation())) {
                    return;
                }
                var attr = shooter.getAttribute(Attribute.MAX_HEALTH);
                double max = attr == null ? 20 : attr.getValue();
                shooter.setHealth(Math.min(max, shooter.getHealth() + heal));
            }
        }
        // khiên thần: người bị đánh cầm khiên xưởng
        if (e.getEntity() instanceof Player victim) {
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.OFF_HAND, EquipmentSlot.HAND}) {
                ItemStack sh = victim.getInventory().getItem(slot);
                Recipe r = recipeOf(sh);
                if (r == null || !r.feature().equals("khien_than") || expired(sh) || blockedHere(victim, r)) {
                    continue;
                }
                double pct = Math.max(0, Math.min(90, r.value() <= 0 ? 50 : r.value())) / 100.0;
                double cut = e.getDamage() * pct;
                e.setDamage(e.getDamage() - cut);
                Entity src = e.getDamager() instanceof Projectile pr2 && pr2.getShooter() instanceof Entity s2 ? s2 : e.getDamager();
                if (src instanceof LivingEntity att && att != victim && cut > 0) {
                    Bukkit.getScheduler().runTask(plugin, () -> att.damage(cut / 2, victim));
                }
                ItemStack after = useOnce(sh, r);
                victim.getInventory().setItem(slot, after);
                break;
            }
        }
        // vũ khí cầm tay
        if (!(e.getDamager() instanceof Player p)) {
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        Recipe r = recipeOf(hand);
        if (r == null) {
            return;
        }
        if (blockedHere(p, r) && e.getEntity() instanceof Player) {
            e.setCancelled(true);
            p.sendActionBar(c("&c" + r.name() + " &ckhông dùng được trong vùng vây khi đang đánh"));
            return;
        }
        if (expired(hand)) {
            p.getInventory().setItemInMainHand(null);
            p.sendMessage(c("&7[Xưởng] " + r.name() + " &7đã hết hạn và biến mất."));
            return;
        }
        if (r.feature().equals("riu_chien") && e.getEntity() instanceof LivingEntity le
                && ThreadLocalRandom.current().nextDouble(100) < Math.max(0, Math.min(100, r.value()))) {
            e.setDamage(e.getDamage() * 1.5);
            le.getWorld().spawnParticle(Particle.CRIT, le.getLocation().add(0, 1, 0), 15, 0.3, 0.4, 0.3, 0.1);
            for (int t = 1; t <= 3; t++) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!le.isDead()) {
                        le.damage(1.0, p);
                        le.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, le.getLocation().add(0, 1, 0), 3, 0.2, 0.3, 0.2, 0);
                    }
                }, t * 20L);
            }
        }
        boolean weapon = r.feature().equals("riu_chien") || r.feature().isEmpty() && !isTool(hand.getType()) || r.feature().isEmpty() && hand.getType().name().endsWith("_AXE");
        if (weapon) {
            spendHand(p, r, 1);
        }
    }

    // ------------------------------------------------------------ menu chế tạo + bảng 3×3

    private static int countPlain(Player p, Material m) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && it.getType() == m && it.isSimilar(new ItemStack(m))) {
                n += it.getAmount();
            }
        }
        return n;
    }

    private static void takePlain(Player p, Material m, int amount) {
        ItemStack[] inv = p.getInventory().getStorageContents();
        for (int i = 0; i < inv.length && amount > 0; i++) {
            if (inv[i] != null && inv[i].getType() == m && inv[i].isSimilar(new ItemStack(m))) {
                int t = Math.min(amount, inv[i].getAmount());
                inv[i].setAmount(inv[i].getAmount() - t);
                amount -= t;
                if (inv[i].getAmount() <= 0) {
                    inv[i] = null;
                }
            }
        }
        p.getInventory().setStorageContents(inv);
    }

    private ItemStack icon(Player p, Recipe r, boolean craftView) {
        ItemStack it = new ItemStack(r.material());
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(r.name()));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(c("&7— Tính năng: &b" + featureText(r)));
        lore.add(c("&7— Độ bền: &6" + (r.uses() >= 0 ? fmt(r.uses()) : "như đồ thường")));
        lore.add(c("&7— Thời hạn: &b" + String.format("%02d", r.days()) + " ngày"));
        for (String d : r.desc()) {
            lore.add(c("&8" + d));
        }
        if (r.pvp()) {
            lore.add(c("&c✖ Mất tác dụng trong vùng vây khi đang đánh"));
        }
        lore.add(Component.empty());
        if (craftView) {
            Refining rf = Shared.refining;
            for (Map.Entry<String, Integer> in : r.refined().entrySet()) {
                int have = rf == null ? 0 : rf.count(p, in.getKey());
                lore.add(c("&7Cần thêm: " + (have >= in.getValue() ? "&a" : "&c") + in.getValue() + "x ")
                        .append(c(rf == null ? in.getKey() : rf.name(in.getKey()))).append(c(" &8(có " + have + ")")));
            }
            if (r.cost() > 0) {
                boolean ok = VaultEconomy.available() && VaultEconomy.has(p, r.cost());
                lore.add(c("&7Giá: " + (ok ? "&a" : "&c") + money(r.cost())));
            }
            lore.add(c("&a→ Nhấn để chế tạo."));
        } else {
            lore.add(c("&a→ Nhấn để xem công thức."));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        if (!r.enchants().isEmpty()) {
            meta.setEnchantmentGlintOverride(true);
        }
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack pane(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(name));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }

    void open(Player p) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 54, c("&8&lXƯỞNG CHẾ TẠO &7(hiệu ứng · độ bền · thời hạn)"));
        int i = 0;
        for (Recipe r : recipes.values()) {
            if (i >= MAIN_SLOTS.length) {
                break;
            }
            h.inv.setItem(MAIN_SLOTS[i], icon(p, r, false));
            h.slots.put(MAIN_SLOTS[i], r);
            i++;
        }
        p.openInventory(h.inv);
    }

    private void openCraft(Player p, Recipe r) {
        Holder h = new Holder();
        h.view = r;
        h.inv = Bukkit.createInventory(h, 54, c("&8&lCHẾ TẠO: " + LEGACY.serialize(c(r.name())).replaceAll("&.", "")));
        ItemStack bg = pane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s = 0; s < 54; s++) {
            h.inv.setItem(s, bg);
        }
        for (int k = 0; k < 9; k++) {
            Object[] g = cell(r.grid().get(k));
            if (g == null) {
                h.inv.setItem(GRID_SLOTS[k], null);
                continue;
            }
            Material m = (Material) g[0];
            int n = (Integer) g[1];
            ItemStack it = new ItemStack(m, Math.max(1, Math.min(m.getMaxStackSize(), n)));
            ItemMeta meta = it.getItemMeta();
            int have = countPlain(p, m);
            int need = r.ingredients().getOrDefault(m, n);
            meta.lore(List.of(c("&7Cần: &f" + n + (need > n ? " &8(cả công thức " + need + ")" : "")), c((have >= need ? "&a" : "&c") + "Bạn có: " + have)));
            it.setItemMeta(meta);
            h.inv.setItem(GRID_SLOTS[k], it);
        }
        h.inv.setItem(ARROW_SLOT, pane(Material.ARROW, "&f→"));
        h.inv.setItem(RESULT_SLOT, icon(p, r, true));
        h.inv.setItem(BACK_SLOT, pane(Material.BOOK, "&e← Về danh sách"));
        p.openInventory(h.inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (h.view == null) {
            Recipe r = h.slots.get(e.getRawSlot());
            if (r != null) {
                openCraft(p, r);
            }
            return;
        }
        if (e.getRawSlot() == BACK_SLOT) {
            open(p);
            return;
        }
        if (e.getRawSlot() != RESULT_SLOT) {
            return;
        }
        Recipe r = h.view;
        for (Map.Entry<Material, Integer> in : r.ingredients().entrySet()) {
            if (countPlain(p, in.getKey()) < in.getValue()) {
                p.sendMessage(c("&cThiếu nguyên liệu: cần " + in.getValue() + "x ").append(Component.translatable(in.getKey().translationKey())));
                return;
            }
        }
        Refining rf = Shared.refining;
        for (Map.Entry<String, Integer> in : r.refined().entrySet()) {
            if (rf == null || rf.count(p, in.getKey()) < in.getValue()) {
                p.sendMessage(c("&cThiếu nguyên liệu: cần " + in.getValue() + "x ").append(c(rf == null ? in.getKey() : rf.name(in.getKey())))
                        .append(c(" &7(/tinhluyen)")));
                return;
            }
        }
        if (p.getInventory().firstEmpty() < 0) {
            p.sendMessage(c("&cTúi đầy, dọn 1 ô rồi chế."));
            return;
        }
        double cost = p.hasPermission("duckymomo.xuong.giam") ? Math.round(r.cost() * 0.95) : r.cost();   // đặc quyền Thăng Cấp: giảm 5%
        if (cost > 0 && (!VaultEconomy.available() || !VaultEconomy.withdraw(p, cost))) {
            p.sendMessage(c("&cKhông đủ " + money(cost) + "."));
            return;
        }
        for (Map.Entry<Material, Integer> in : r.ingredients().entrySet()) {
            takePlain(p, in.getKey(), in.getValue());
        }
        for (Map.Entry<String, Integer> in : r.refined().entrySet()) {
            rf.take(p, in.getKey(), in.getValue());
        }
        p.getInventory().addItem(build(r));
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.2f);
        p.sendMessage(c("&a✔ Đã chế " + r.name() + "&a. Dùng được " + r.days() + " ngày."));
        plugin.getLogger().info("Xưởng: " + p.getName() + " chế " + r.id());
        openCraft(p, r);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    boolean command(CommandSender s, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("tang") && s.hasPermission("duckymomo.admin")) {
            Recipe r = recipes.get(args[1]);
            Player t = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : s instanceof Player sp ? sp : null;
            if (r == null || t == null) {
                s.sendMessage("/xuong tang <" + String.join("|", recipes.keySet()) + "> [người]");
            } else {
                t.getInventory().addItem(build(r));
                s.sendMessage("Đã tặng " + r.id() + " cho " + t.getName());
            }
            return true;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("Chỉ dùng trong game.");
            return true;
        }
        open(p);
        return true;
    }
}
