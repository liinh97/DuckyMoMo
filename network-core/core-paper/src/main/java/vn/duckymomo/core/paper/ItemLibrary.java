package vn.duckymomo.core.paper;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Thư viện đồ: NƠI DUY NHẤT định nghĩa vật phẩm đặc biệt (20/10). Chỗ khác chỉ chọn mã thư viện và giữ thông số của riêng mình:
 * Xưởng (giá, bàn chế), món ăn /nauan (nguyên liệu), Tinh Luyện (công thức, giá bán), mảnh sự kiện (hạn đổi), bùa bắt pet (% cộng),
 * boss (tỉ lệ rơi, cho ai: boss-roi.yml), phần thưởng "do <mã>". Mã của Xưởng / món ăn / Tinh Luyện / mảnh = mã thư viện.
 * Hai loại trong plugins/DuckyMoMoCore/thuvien-do.yml:
 *   nguon: web  – tạo/sửa trên trang Thư viện đồ (/thuvien): material, name, lore, enchants (cho phép vượt cấp), unbreakable, model,
 *               attributes {attack_damage, attack_speed, armor, …}: chỉ số gốc ghi đè (số như dòng "+8 Sát thương" trong game; thiếu = giữ gốc).
 *   nguon: game – /dmm luudo <mã> khi cầm đồ: giữ nguyên vẹn (base64 của ItemStack), kèm "preview" để web hiển thị.
 * Chung cho cả hai: nhom (đầu mục trên web), cong-dung (ky-nang | mon-an | bua-bat-pet | tinh-luyen | manh; trống = đồ thường),
 *   ky-nang {feature, value, text, uses, days, pvp} (Workshop), hieu-ung ["HIỆU_ỨNG cấp giây"] (Dishes).
 * Mọi món dựng ra mang dấu PDC thuvien_id = mã; module có công dụng đóng thêm dấu riêng (STAMPERS) nên đồ thư viện phát ở đâu cũng dùng được.
 * Đồ mẫu (thuvien-mau.yml trong jar, có "phien"): lần bật đầu sau khi tăng phiên thì thêm món mẫu CHƯA có (không đè món admin đã sửa).
 * Lúc bật, ghi thuvien-meta.json (danh sách phù phép + vật liệu của server, kể cả phù phép plugin như Enchantio) cho trang web.
 */
final class ItemLibrary {
    static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    /** Công dụng -> module đóng dấu riêng lên đồ (Workshop, Dishes, PetCapture, Refining, FragmentShop đăng ký khi bật). */
    interface Stamper {
        ItemStack stamp(ItemStack item, String id, ConfigurationSection s);
    }

    /** Chỉ số gốc chỉnh được (attributes.<mã>). base: số người chơi có sẵn mà dòng "+8 Sát thương" cộng vào (sát thương 1),
     *  percent: ghi theo % (tốc chạy). Không có tốc đánh: OldCombatMechanics bỏ thời gian hồi đòn nên vô tác dụng. */
    record AttrDef(String id, Attribute attr, double base, boolean percent) { }

    static final List<AttrDef> ATTRS = List.of(
            new AttrDef("attack_damage", Attribute.ATTACK_DAMAGE, 1, false),
            new AttrDef("armor", Attribute.ARMOR, 0, false),
            new AttrDef("armor_toughness", Attribute.ARMOR_TOUGHNESS, 0, false),
            new AttrDef("knockback_resistance", Attribute.KNOCKBACK_RESISTANCE, 0, false),
            new AttrDef("max_health", Attribute.MAX_HEALTH, 0, false),
            new AttrDef("movement_speed", Attribute.MOVEMENT_SPEED, 0, true));

    static final Map<String, Stamper> STAMPERS = new java.util.concurrent.ConcurrentHashMap<>();
    /** Đầu mục (nhom) -> tên hiện; khớp NHOM trong panel/thuvien.html. */
    static final Map<String, String> NHOM = new LinkedHashMap<>();

    static {
        NHOM.put("vu-khi", "Vũ khí");
        NHOM.put("cong-cu", "Công cụ");
        NHOM.put("giap", "Giáp & khiên");
        NHOM.put("do-an", "Món ăn");
        NHOM.put("bua", "Bùa & tiêu hao");
        NHOM.put("nguyen-lieu", "Nguyên liệu");
        NHOM.put("tien", "Tiền vật phẩm");
        NHOM.put("manh", "Mảnh sự kiện");
        NHOM.put("chia-khoa", "Vé gacha");
        NHOM.put("khac", "Khác");
    }

    private static NamespacedKey idKey;
    private final JavaPlugin plugin;
    private final File file;
    private final CeBridge ce;
    private YamlConfiguration cache;
    private long cacheStamp = -1;
    private int version;

    /** Bản đang chạy (BanBook dùng đồng bộ hình riêng của đồ trong túi khi thư viện đổi). */
    static ItemLibrary INSTANCE;

    ItemLibrary(JavaPlugin plugin) {
        INSTANCE = this;
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "thuvien-do.yml");
        this.ce = new CeBridge(plugin.getLogger());
        idKey = new NamespacedKey(plugin, "thuvien_id");
        importTemplate();
    }

    /** Đọc file (giữ bản đã đọc tới khi file đổi: web lưu là thấy ngay, không đọc lại mỗi lần phát đồ). */
    private synchronized YamlConfiguration load() {
        long stamp = file.lastModified() ^ file.length();
        if (cache == null || stamp != cacheStamp) {
            cache = YamlConfiguration.loadConfiguration(file);
            cacheStamp = stamp;
            version++;
        }
        return cache;
    }

    /** Tăng mỗi lần file thư viện đổi: module giữ bản tính sẵn (Workshop) so số này để tính lại. */
    int version() {
        load();
        return version;
    }

    /** Ghi ra file tạm rồi đổi tên (23/10: panel và plugin ghi chồng nhau từng để lại 88 KB ký tự rỗng giữa thuvien-do.yml). */
    private static void saveAtomic(org.bukkit.configuration.file.YamlConfiguration y, File file) throws IOException {
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        y.save(tmp);
        java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }

    /** Mục của một món (null nếu không có). Đừng sửa: dùng chung bản đã đọc. */
    /** 30/10: hình riêng (model) của món theo thư viện; null = không đặt. */
    static NamespacedKey modelOf(String id) {
        ItemLibrary l = INSTANCE;
        ConfigurationSection s = l == null || id == null ? null : l.section(id);
        String m = s == null ? "" : s.getString("model", "");
        return m == null || m.isBlank() ? null : NamespacedKey.fromString(m.toLowerCase(Locale.ROOT));
    }

    ConfigurationSection section(String id) {
        return id == null ? null : load().getConfigurationSection("items." + id);
    }

    /** Mục khác của file thư viện (vd "sach-cam" của BanBook), null nếu không có. Đừng sửa. */
    ConfigurationSection raw(String path) {
        return load().getConfigurationSection(path);
    }

    /** Tên hiện của món (mã màu &), trống nếu chưa đặt tên. */
    String name(String id) {
        ConfigurationSection s = section(id);
        if (s == null) {
            return id;
        }
        String n = "game".equals(s.getString("nguon")) ? s.getString("preview.name", "") : s.getString("name", "");
        return n == null || n.isBlank() ? s.getString("ten-goi", id) : n;
    }

    /** Mã thư viện trên đồ (null nếu không phải đồ thư viện). */
    static String idOf(ItemStack it) {
        if (idKey == null || it == null || !it.hasItemMeta()) {
            return null;
        }
        return it.getItemMeta().getPersistentDataContainer().get(idKey, org.bukkit.persistence.PersistentDataType.STRING);
    }

    /** Các mã có công dụng này (vd "ky-nang"). */
    List<String> idsWith(String use) {
        List<String> out = new ArrayList<>();
        ConfigurationSection s = load().getConfigurationSection("items");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                if (use.equals(s.getString(k + ".cong-dung", ""))) {
                    out.add(k);
                }
            }
        }
        return out;
    }

    /** Thêm món mẫu (thuvien-mau.yml trong jar) còn thiếu khi phiên mẫu mới hơn phiên đã nạp. */
    private void importTemplate() {
        if (!plugin.getConfig().isConfigurationSection("workshop")) {
            return;   // đồ mẫu là đồ Bang Hội Chiến (Xưởng, Tinh Luyện…): sảnh / practice / sáng tạo không cần
        }
        try (var in = plugin.getResource("thuvien-mau.yml")) {
            if (in == null) {
                return;
            }
            YamlConfiguration tpl = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            int have = y.getInt("phien-mau", 0), want = tpl.getInt("phien", 0);
            ConfigurationSection items = tpl.getConfigurationSection("items");
            if (want <= have || items == null) {
                return;
            }
            int added = 0;
            for (String k : items.getKeys(false)) {
                if (!y.isConfigurationSection("items." + k)) {
                    y.set("items." + k, items.getConfigurationSection(k));
                    added++;
                }
            }
            y.set("phien-mau", want);
            plugin.getDataFolder().mkdirs();
            saveAtomic(y, file);
            plugin.getLogger().info("Thư viện đồ: nạp mẫu phiên " + want + ", thêm " + added + " món.");
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("Thư viện đồ: không nạp được đồ mẫu: " + e);
        }
    }

    List<String> ids() {
        ConfigurationSection s = load().getConfigurationSection("items");
        return s == null ? List.of() : new ArrayList<>(s.getKeys(false));
    }

    /** Dựng đồ theo mã, ĐỦ công dụng (kỹ năng Xưởng, hiệu ứng món ăn, dấu Tinh Luyện…); null nếu không có hoặc hỏng.
     *  amount <= 0: lấy số lượng ghi trong thư viện. */
    ItemStack build(String id, int amount) {
        ItemStack item = base(id, amount);
        if (item == null) {
            return null;
        }
        ConfigurationSection s = section(id);
        Stamper st = STAMPERS.get(s.getString("cong-dung", ""));
        if (st != null) {
            try {
                ItemStack done = st.stamp(item, id, s);
                if (done != null) {
                    item = done;
                }
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Thư viện đồ: gắn công dụng cho \"" + id + "\" lỗi: " + e);
            }
        }
        return item;
    }

    /** Chỉ hình dạng (tên, mô tả, phù phép, hình riêng) + dấu thuvien_id, chưa gắn công dụng. */
    ItemStack base(String id, int amount) {
        ConfigurationSection s = id == null ? null : section(id);
        if (s == null) {
            return null;
        }
        ItemStack item;
        try {
            if ("game".equals(s.getString("nguon"))) {
                item = ItemStack.deserializeBytes(Base64.getDecoder().decode(s.getString("data", "")));
            } else {
                item = fromFields(s);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Thư viện đồ: không dựng được \"" + id + "\": " + e.getMessage());
            return null;
        }
        if (item == null) {
            return null;
        }
        if (amount > 0) {
            item.setAmount(Math.min(amount, item.getMaxStackSize()));
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(idKey, org.bukkit.persistence.PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Phù phép mà game tự vẽ thêm khối "Khi được mặc" (chỉ số theo phù phép, 1.21+). Khớp ENCH_STAT trong thuvien.html. */
    private static final java.util.Set<String> ENCH_STAT = java.util.Set.of("blast_protection", "fire_protection", "swift_sneak", "depth_strider",
            "respiration", "aqua_affinity", "soul_speed", "feather_falling");

    /** Ước số dòng tooltip như game vẽ (tên, phù phép, phù phép CE, lore, "Không thể phá huỷ", khối chỉ số, khối "Khi được mặc"). */
    @SuppressWarnings("deprecation")
    private static int tooltipLines(ItemMeta meta, Material mat, ConfigurationSection s, int loreLines) {
        int n = 1 + loreLines;
        if (!s.getBoolean("hide-enchants")) {
            n += meta.getEnchants().size();
        }
        ConfigurationSection cee = s.getConfigurationSection("ce-enchants");
        n += cee == null ? 0 : cee.getKeys(false).size();
        if (meta.isUnbreakable()) {
            n++;
        }
        var mods = meta.getAttributeModifiers();
        int attrs = mods != null && !mods.isEmpty() ? mods.keySet().size() : mat.getDefaultAttributeModifiers(mat.getEquipmentSlot()).keySet().size();
        n += attrs > 0 ? attrs + 2 : 0;
        int es = (int) meta.getEnchants().keySet().stream().filter(e -> ENCH_STAT.contains(e.getKey().getKey())).count();
        n += es > 0 ? es + 2 : 0;
        return n;
    }

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    /** Tên phù phép của game bằng chữ tiếng Việt cố định (bản dịch vi_vn của Minecraft). 28/10: trước dùng chữ "máy tự dịch", nhưng
     *  CrazyEnchantments ghi lại mô tả bằng chuỗi nên đổi thành chữ TIẾNG ANH cố định (kiếm hiện "Fire Aspect · Sharpness"). */
    private static final Map<String, String> VI_ENCH = Map.ofEntries(
            Map.entry("aqua_affinity", "Đào nhanh dưới nước"),
            Map.entry("bane_of_arthropods", "Diệt chân đốt"),
            Map.entry("binding_curse", "Lời nguyền ràng buộc"),
            Map.entry("blast_protection", "Bảo vệ khỏi vụ nổ"),
            Map.entry("breach", "Phá giáp"),
            Map.entry("channeling", "Chớp điện"),
            Map.entry("density", "Tỉ trọng"),
            Map.entry("depth_strider", "Sải bước dưới nước"),
            Map.entry("efficiency", "Hiệu suất"),
            Map.entry("feather_falling", "Bảo vệ khỏi sát thương rơi"),
            Map.entry("fire_aspect", "Gây cháy"),
            Map.entry("fire_protection", "Bảo vệ khỏi lửa"),
            Map.entry("flame", "Lửa"),
            Map.entry("fortune", "Gia tài"),
            Map.entry("frost_walker", "Chân lạnh"),
            Map.entry("impaling", "Xiên vào da thịt"),
            Map.entry("infinity", "Vô hạn"),
            Map.entry("knockback", "Đánh bật lùi"),
            Map.entry("looting", "Nhặt"),
            Map.entry("loyalty", "Trung thành"),
            Map.entry("luck_of_the_sea", "May mắn của biển cả"),
            Map.entry("lunge", "Lao tới"),
            Map.entry("lure", "Nhử"),
            Map.entry("mending", "Tu sửa"),
            Map.entry("multishot", "Mưa tên"),
            Map.entry("piercing", "Xuyên thủng"),
            Map.entry("power", "Sức mạnh"),
            Map.entry("projectile_protection", "Bảo vệ khỏi vật bắn ra"),
            Map.entry("protection", "Bảo vệ"),
            Map.entry("punch", "Xung kích"),
            Map.entry("quick_charge", "Nạp nhanh"),
            Map.entry("respiration", "Hô hấp"),
            Map.entry("riptide", "Sóng dữ"),
            Map.entry("sharpness", "Sắc bén"),
            Map.entry("silk_touch", "Độ mềm mại"),
            Map.entry("smite", "Diệt xác sống"),
            Map.entry("soul_speed", "Tốc độ linh hồn"),
            Map.entry("sweeping", "Quét cạnh"),
            Map.entry("sweeping_edge", "Quét cạnh"),
            Map.entry("swift_sneak", "Rón rén nhanh"),
            Map.entry("thorns", "Gai"),
            Map.entry("unbreaking", "Chậm hỏng"),
            Map.entry("vanishing_curse", "Lời nguyền biến mất"),
            Map.entry("wind_burst", "Phong bộc"));

    /** Tên chỉ số bằng chữ tiếng Việt cố định (cùng lý do VI_ENCH: plugin khác ghi lại mô tả thì chữ "máy tự dịch" thành tiếng Anh). */
    private static final Map<String, String> VI_ATTR = Map.ofEntries(
            Map.entry("air_drag_modifier", "Hệ số lực cản không khí"),
            Map.entry("armor", "Giáp"),
            Map.entry("armor_toughness", "Độ cứng giáp"),
            Map.entry("attack_damage", "Sát thương"),
            Map.entry("attack_knockback", "Sức bật lùi"),
            Map.entry("attack_speed", "Tốc độ tấn công"),
            Map.entry("below_name_distance", "Khoảng cách hiển thị điểm số trên nhãn ghi tên"),
            Map.entry("block_break_speed", "Tốc độ phá khối"),
            Map.entry("block_interaction_range", "Phạm vi tương tác khối"),
            Map.entry("bounciness", "Độ nảy"),
            Map.entry("burning_time", "Thời gian cháy"),
            Map.entry("camera_distance", "Khoảng cách máy quay"),
            Map.entry("entity_interaction_range", "Phạm vi tương tác thực thể"),
            Map.entry("explosion_knockback_resistance", "Kháng đẩy lùi từ vụ nổ"),
            Map.entry("fall_damage_multiplier", "Hệ số sát thương rơi"),
            Map.entry("flying_speed", "Tốc độ bay"),
            Map.entry("follow_range", "Phạm vi đi theo của sinh vật"),
            Map.entry("friction_modifier", "Hệ số ma sát"),
            Map.entry("gravity", "Trọng lực"),
            Map.entry("jump_strength", "Sức nhảy"),
            Map.entry("knockback_resistance", "Kháng đẩy lùi"),
            Map.entry("luck", "May mắn"),
            Map.entry("max_absorption", "Hấp thụ tối đa"),
            Map.entry("max_health", "Máu tối đa"),
            Map.entry("mining_efficiency", "Hiệu suất đào"),
            Map.entry("movement_efficiency", "Hiệu quả di chuyển"),
            Map.entry("movement_speed", "Tốc chạy"),
            Map.entry("nameplate_distance", "Khoảng cách hiển thị nhãn ghi tên"),
            Map.entry("oxygen_bonus", "Phần oxi thêm"),
            Map.entry("safe_fall_distance", "Độ cao rơi xuống an toàn"),
            Map.entry("scale", "Quy mô"),
            Map.entry("sneaking_speed", "Tốc độ đi rón rén"),
            Map.entry("spawn_reinforcements", "Lực lượng thây ma tăng cường"),
            Map.entry("step_height", "Chiều cao bước"),
            Map.entry("submerged_mining_speed", "Tốc độ đào dưới nước"),
            Map.entry("sweeping_damage_ratio", "Tỉ lệ sát thương quét cạnh"),
            Map.entry("tempt_range", "Phạm vi thu hút sinh vật"),
            Map.entry("water_movement_efficiency", "Hiệu quả di chuyển dưới nước"),
            Map.entry("waypoint_receive_range", "Phạm vi nhận điểm mốc"),
            Map.entry("waypoint_transmit_range", "Phạm vi truyền điểm mốc"));

    private static Component attrName(Attribute a) {
        String vi = VI_ATTR.get(a.getKey().getKey());
        return vi != null ? Component.text(vi) : Component.translatable(a.translationKey());
    }

    private static Component enchName(Enchantment e) {
        String vi = "minecraft".equals(e.getKey().getNamespace()) ? VI_ENCH.get(e.getKey().getKey()) : null;
        return vi != null ? Component.text(vi) : e.description();
    }

    /** Phù phép của game, mỗi phù phép một mục (gộp 3 mục một dòng ở tidyLore / compactEnchants). */
    private static List<Component> enchEntries(Map<Enchantment, Integer> ench) {
        List<Component> out = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> e : ench.entrySet()) {
            int lv = e.getValue();
            Component c = enchName(e.getKey()).color(e.getKey().isCursed() ? NamedTextColor.RED : NamedTextColor.GRAY);
            if (!(lv == 1 && e.getKey().getMaxLevel() == 1)) {
                c = c.append(Component.text(" " + (lv < ROMAN.length ? ROMAN[lv] : String.valueOf(lv))));
            }
            out.add(c);
        }
        return out;
    }

    private static List<Component> pack(List<Component> entries, int per) {
        List<Component> out = new ArrayList<>();
        Component line = null;
        int n = 0;
        for (Component c : entries) {
            line = line == null ? c : line.append(Component.text(" · ", NamedTextColor.DARK_GRAY)).append(c);
            if (++n % per == 0) {
                out.add(line.decoration(TextDecoration.ITALIC, false));
                line = null;
            }
        }
        if (line != null) {
            out.add(line.decoration(TextDecoration.ITALIC, false));
        }
        return out;
    }

    /** Phù phép gộp 3 cái một dòng, tên tiếng Việt. */
    private static List<Component> compactEnchants(Map<Enchantment, Integer> ench) {
        return pack(enchEntries(ench), 3);
    }

    private static final java.util.regex.Pattern ENCH_ENTRY =
            java.util.regex.Pattern.compile("^[\\p{L}][\\p{L} '’-]{0,30} ([IVXLC]+|\\d+)$");   // tên + cấp bắt buộc: không gộp nhầm dòng mô tả
    private static final org.bukkit.NamespacedKey TIDY_KEY = new org.bukkit.NamespacedKey("duckymomocore", "lore_gon");

    /**
     * Gom khối phù phép đầu mô tả (CrazyEnchantments mỗi phù phép một dòng, phù phép riêng của server, dòng phù phép game đã gộp) thành
     * 3 mục một dòng; dòng phù phép game viết lại bằng tên tiếng Việt (sửa đồ cũ bị CE đổi sang tiếng Anh). 28/10, chủ server.
     * Chạy lúc dựng món (sau khi CE ghi mô tả) và lúc vào game cho đồ đang giữ. true = đã đổi món.
     */
    static boolean tidyLore(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = it.getItemMeta();
        byte ver = meta.getPersistentDataContainer().getOrDefault(TIDY_KEY, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 0);
        List<Component> lore = meta.lore();
        if (ver >= 2 || lore == null || lore.isEmpty()) {
            return false;
        }
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        List<Component> out = new ArrayList<>(lore);
        if (ver < 1) {   // v1: gom phù phép đầu mô tả
            List<Component> entries = new ArrayList<>();
            boolean vanillaDone = false;
            int k = 0;
            for (; k < lore.size(); k++) {
                String t = plain.serialize(lore.get(k)).trim();
                if (t.isEmpty()) {
                    break;
                }
                if (t.contains(" · ")) {   // dòng phù phép game đã gộp (có thể đã thành tiếng Anh): dựng lại từ dữ liệu phù phép
                    boolean all = java.util.Arrays.stream(t.split(" · ")).allMatch(x -> ENCH_ENTRY.matcher(x.trim()).matches() || x.trim().matches("^[\\p{L}][\\p{L} '’-]{0,30}$"));
                    if (!all || !meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS)) {
                        break;
                    }
                    if (!vanillaDone) {
                        entries.addAll(enchEntries(meta.getEnchants()));
                        vanillaDone = true;
                    }
                    continue;
                }
                if (!ENCH_ENTRY.matcher(t).matches()) {
                    break;
                }
                entries.add(lore.get(k));
            }
            if (!entries.isEmpty()) {
                out = new ArrayList<>(pack(entries, 3));
                if (k < lore.size() && !plain.serialize(lore.get(k)).isBlank()) {
                    out.add(Component.empty());   // khoảng trống giữa khối phù phép và tiêu đề set
                }
                out.addAll(lore.subList(k, lore.size()));
            }
        }
        // v2 (28/10): dòng chỉ số tiếng Việt, gỡ dòng "Sát thương: 9" của OldCombatMechanics (trùng "Khi cầm"), gỡ dòng "+72% Sát thương"
        // trùng với khối nội dung của set ("✧ +72% sát thương")
        List<String> low = out.stream().map(c -> plain.serialize(c).toLowerCase(Locale.ROOT)).toList();
        List<Component> res = new ArrayList<>();
        for (int i = 0; i < out.size(); i++) {
            String t = plain.serialize(out.get(i)).trim();
            if (t.matches("^Sát thương: [0-9.,]+$")) {
                if (!res.isEmpty() && plain.serialize(res.get(res.size() - 1)).isBlank()) {
                    res.remove(res.size() - 1);
                }
                continue;
            }
            if (t.matches("^[+-][0-9.,]+% .+$")) {
                String want = t.toLowerCase(Locale.ROOT);
                int self = i;
                boolean dup = java.util.stream.IntStream.range(0, low.size()).anyMatch(j -> j != self && low.get(j).contains(want));
                if (dup) {
                    continue;
                }
            }
            if (t.startsWith("Khi cầm: ") || t.startsWith("Khi mặc: ") || t.startsWith("Khi đội: ")) {
                Component line = statsLine(meta, it.getType());
                if (line != null) {
                    res.add(line);
                    continue;
                }
            }
            res.add(out.get(i));
        }
        meta.lore(res);
        meta.getPersistentDataContainer().set(TIDY_KEY, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 2);
        it.setItemMeta(meta);
        return true;
    }

    /** "Khi mặc: +6 Giáp · +2 Độ cứng giáp" thay cho khối "Khi ở ngực: …" của game. Đồ chưa có chỉ số riêng thì chép chỉ số gốc vào
     *  để cờ ẩn có tác dụng. Dòng "Khi được mặc" do phù phép (vd Bảo vệ khỏi vụ nổ) cũng bị ẩn theo. */
    @SuppressWarnings("deprecation")
    private static Component statsLine(ItemMeta meta, Material mat) {
        EquipmentSlot slot = mat.getEquipmentSlot();
        if (meta.getAttributeModifiers() == null || meta.getAttributeModifiers().isEmpty()) {
            mat.getDefaultAttributeModifiers(slot).forEach(meta::addAttributeModifier);
        }
        var mods = meta.getAttributeModifiers();
        if (mods == null || mods.isEmpty()) {
            return null;
        }
        Component line = Component.text(slot == EquipmentSlot.HAND ? "Khi cầm: " : slot == EquipmentSlot.HEAD ? "Khi đội: " : "Khi mặc: ", NamedTextColor.GRAY);
        boolean first = true;
        for (Attribute a : mods.keySet()) {
            if (a == Attribute.ATTACK_SPEED) {
                continue;   // đánh kiểu 1.8 (OCM): không có hồi đòn
            }
            double add = 0, per = 0;   // số cộng thẳng và số % (vd Máu tối đa +4 và +10%) hiện riêng
            for (AttributeModifier m : mods.get(a)) {
                if (m.getOperation() == AttributeModifier.Operation.ADD_NUMBER) {
                    add += m.getAmount();
                } else {
                    per += m.getAmount() * 100;
                }
            }
            if (a == Attribute.ATTACK_DAMAGE) {
                add += 1;
            }
            for (int k = 0; k < 2; k++) {
                boolean pct = k == 1;
                double sum = pct ? per : add;
                if (sum == 0) {
                    continue;
                }
                String num = (sum > 0 && a != Attribute.ATTACK_DAMAGE ? "+" : "") + (sum == Math.floor(sum) ? String.valueOf((long) sum)
                        : String.format(Locale.ROOT, "%.2f", sum).replaceAll("0+$", "").replace('.', ',')) + (pct ? "%" : "");
                line = line.append(Component.text(first ? "" : " · ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(num + " ", sum < 0 ? NamedTextColor.RED : NamedTextColor.BLUE))
                        .append(attrName(a).color(sum < 0 ? NamedTextColor.RED : NamedTextColor.BLUE));
                first = false;
            }
        }
        return first ? null : line.decoration(TextDecoration.ITALIC, false);
    }

    /** Gắn chỉ số riêng là Minecraft bỏ HẾT chỉ số gốc của loại đồ (kiếm mất sát thương nếu chỉ sửa giáp): chép chỉ số gốc trước, rồi ghi đè. */
    @SuppressWarnings("deprecation")
    private void applyAttributes(ItemMeta meta, Material mat, ConfigurationSection at) {
        EquipmentSlot slot = mat.getEquipmentSlot();
        EquipmentSlotGroup group = slot.getGroup();
        mat.getDefaultAttributeModifiers(slot).forEach(meta::addAttributeModifier);
        Double ocm = ocmDamage(mat);
        for (AttrDef d : ATTRS) {
            // sát thương không sửa mà có bảng OldCombatMechanics: ghi đúng số trong bảng (đồ có chỉ số riêng thì OCM dùng số trên đồ)
            boolean keepOcm = !at.isSet(d.id()) && d.attr() == Attribute.ATTACK_DAMAGE && ocm != null;
            if (!at.isSet(d.id()) && !keepOcm) {
                continue;
            }
            double v = keepOcm ? ocm : at.getDouble(d.id());
            meta.removeAttributeModifier(d.attr());
            double amount = d.percent() ? v / 100.0 : v - d.base();
            if (amount != 0) {
                meta.addAttributeModifier(d.attr(), new AttributeModifier(new NamespacedKey(plugin, "thuvien_" + d.id()), amount,
                        d.percent() ? AttributeModifier.Operation.ADD_SCALAR : AttributeModifier.Operation.ADD_NUMBER, group));
            }
        }
        double mau = at.getDouble("pct_mau", 0);   // Máu % (22/10): +10 = máu tối đa gốc ×1,1; âm = giảm
        if (mau != 0) {
            meta.addAttributeModifier(Attribute.MAX_HEALTH, new AttributeModifier(new NamespacedKey(plugin, "thuvien_pct_mau"), mau / 100.0,
                    AttributeModifier.Operation.ADD_SCALAR, group));
        }
    }

    /** Chỉnh theo % server tự tính (ItemPercent): mã trong attributes -> (dấu PDC, tên hiện trên đồ). */
    private static final List<Map.Entry<String, Map.Entry<NamespacedKey, String>>> PCTS = List.of(
            Map.entry("pct_sat_thuong", Map.entry(ItemPercent.DAMAGE, "Sát thương")),
            Map.entry("pct_toc_danh", Map.entry(ItemPercent.ATTACK_SPEED, "Tốc đánh")),
            Map.entry("pct_day_lui", Map.entry(ItemPercent.KNOCKBACK, "Kháng đẩy lùi")));

    private static Map<String, Double> ocmTable;

    /** Sát thương "gốc" theo bảng OldCombatMechanics old-tool-damage.damages (tên cũ GOLD_/WOOD_/SPADE), null nếu không có. */
    private static Double ocmDamage(Material m) {
        if (ocmTable == null) {
            Map<String, Double> t = new java.util.HashMap<>();
            File f = new File(Bukkit.getPluginsFolder(), "OldCombatMechanics/config.yml");
            if (f.isFile() && Bukkit.getPluginManager().isPluginEnabled("OldCombatMechanics")) {
                ConfigurationSection d = YamlConfiguration.loadConfiguration(f).getConfigurationSection("old-tool-damage.damages");
                if (d != null) {
                    for (String k : d.getKeys(false)) {
                        t.put(k.toUpperCase(Locale.ROOT).replace("GOLD_", "GOLDEN_").replace("WOOD_", "WOODEN_").replace("_SPADE", "_SHOVEL"), d.getDouble(k));
                    }
                }
            }
            ocmTable = t;
        }
        return ocmTable.get(m.name());
    }

    /** Chỉ số gốc của loại đồ theo số hiện trong game ({"slot":"mainhand","attack_damage":8,…}); null nếu không có chỉ số nào. */
    @SuppressWarnings("deprecation")
    private static String defaultAttrs(Material m) {
        EquipmentSlot slot = m.getEquipmentSlot();
        var def = m.getDefaultAttributeModifiers(slot);
        StringBuilder b = new StringBuilder("{\"slot\":\"").append(slot.getGroup().toString().toLowerCase(Locale.ROOT)).append('"');
        boolean any = false;
        for (AttrDef d : ATTRS) {
            double sum = 0;
            boolean has = false;
            for (AttributeModifier mod : def.get(d.attr())) {
                sum += mod.getAmount() * (d.percent() ? 100 : 1);
                has = true;
            }
            Double ocm = d.attr() == Attribute.ATTACK_DAMAGE ? ocmDamage(m) : null;
            if (has || ocm != null) {
                b.append(",\"").append(d.id()).append("\":").append(ocm != null ? ocm : Math.round((sum + d.base()) * 1000) / 1000.0);
                any = true;
            }
        }
        return any || slot != EquipmentSlot.HAND ? b.append('}').toString() : null;
    }

    private static Enchantment enchant(String name) {
        String n = name.toLowerCase(Locale.ROOT).trim();
        NamespacedKey key = n.contains(":") ? NamespacedKey.fromString(n) : NamespacedKey.minecraft(n);
        return key == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
    }

    private ItemStack fromFields(ConfigurationSection s) {
        Material mat = Material.matchMaterial(s.getString("material", ""));
        if (mat == null || !mat.isItem() || mat.isAir()) {
            throw new IllegalArgumentException("vật liệu không hợp lệ: " + s.getString("material"));
        }
        ItemStack item = new ItemStack(mat, Math.max(1, s.getInt("amount", 1)));
        ItemMeta meta = item.getItemMeta();
        String name = s.getString("name", "");
        if (!name.isBlank()) {
            meta.displayName(LEGACY.deserialize(name).colorIfAbsent(NamedTextColor.WHITE)
                    .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        List<Component> lore = new ArrayList<>();
        for (String line : s.getStringList("lore")) {
            lore.add(LEGACY.deserialize(line).colorIfAbsent(NamedTextColor.GRAY)
                    .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        ConfigurationSection ench = s.getConfigurationSection("enchants");
        if (ench != null) {
            for (String k : ench.getKeys(false)) {
                Enchantment e = enchant(k);
                int lvl = ench.getInt(k);
                if (e == null || lvl < 1) {
                    plugin.getLogger().warning("Thư viện đồ: bỏ qua phù phép " + k);
                    continue;
                }
                if (meta instanceof EnchantmentStorageMeta book) {
                    book.addStoredEnchant(e, lvl, true);
                } else {
                    meta.addEnchant(e, lvl, true);
                }
            }
        }
        if (s.getBoolean("unbreakable")) {
            meta.setUnbreakable(true);
        }
        if (s.getBoolean("hide-enchants")) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_STORED_ENCHANTS);
        }
        if (s.isBoolean("glint")) {
            meta.setEnchantmentGlintOverride(s.getBoolean("glint"));
        }
        ConfigurationSection at = s.getConfigurationSection("attributes");
        if (at != null && !at.getKeys(false).isEmpty()) {
            applyAttributes(meta, mat, at);
            // tốc đánh: OldCombatMechanics bỏ hồi đòn nên không dùng thuộc tính của game; AttackSpeed tự tính theo dấu này (đòn/giây)
            double aps = at.getDouble("attack_speed", 0);
            if (aps > 0) {
                meta.getPersistentDataContainer().set(AttackSpeed.KEY, org.bukkit.persistence.PersistentDataType.DOUBLE, aps);
                lore.add(LEGACY.deserialize("&7Tốc đánh: &f" + (aps == Math.floor(aps) ? String.valueOf((long) aps)
                        : String.format(Locale.ROOT, "%.2f", aps).replaceAll("0+$", "").replace('.', ',')) + " &7đòn/giây")
                        .decoration(TextDecoration.ITALIC, false));
            }
            for (var pe : PCTS) {
                double v = at.getDouble(pe.getKey(), 0);
                if (v == 0) {
                    continue;
                }
                meta.getPersistentDataContainer().set(pe.getValue().getKey(), org.bukkit.persistence.PersistentDataType.DOUBLE, v);
                String num = (v > 0 ? "+" : "") + (v == Math.floor(v) ? String.valueOf((long) v)
                        : String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replace('.', ','));
                lore.add(LEGACY.deserialize((v > 0 ? "&9" : "&c") + num + "% " + pe.getValue().getValue()).decoration(TextDecoration.ITALIC, false));
            }
        }
        // phù phép riêng của server (Lướt, Hồi máu, Cầu lửa): dòng "Lướt II" đầu mô tả, ngay dưới phù phép của game
        ConfigurationSection dmm = s.getConfigurationSection("dmm-enchants");
        if (dmm != null && !dmm.getKeys(false).isEmpty()) {
            Map<String, Integer> lv = new LinkedHashMap<>();
            dmm.getKeys(false).forEach(k -> lv.put(k, dmm.getInt(k)));
            lore.addAll(0, SkillEnchants.apply(meta, lv));
        }
        // tooltip gọn (21/10: đồ nhiều phù phép bị đẩy ra ngoài màn hình): 3 phù phép một dòng, chỉ số game gộp 1 dòng cuối mô tả.
        // Bật theo món (compact-enchants, hide-stats) hoặc TỰ BẬT khi tooltip ước tính dài hơn item-library.tooltip-max-lines (0 = tắt).
        // Chỉ đổi cách hiện phần game tự vẽ; tên, lore, phù phép CE giữ nguyên.
        int maxLines = plugin.getConfig().getInt("item-library.tooltip-max-lines", 20);
        boolean auto = maxLines > 0 && tooltipLines(meta, mat, s, lore.size()) > maxLines;
        if ((s.getBoolean("compact-enchants") || auto) && !(meta instanceof EnchantmentStorageMeta) && meta.getEnchants().size() > 1) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            lore.addAll(0, compactEnchants(meta.getEnchants()));
        }
        if (s.getBoolean("hide-stats") || auto) {
            Component line = statsLine(meta, mat);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            if (line != null) {
                lore.add(line);
            }
        }
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        String model = s.getString("model", "");
        if (model != null && !model.isBlank()) {
            NamespacedKey mk = NamespacedKey.fromString(model.toLowerCase(Locale.ROOT));
            if (mk != null) {
                meta.setItemModel(mk);   // hình riêng trong gói tài nguyên DuckyMoMo (không tải gói: thấy vật phẩm gốc)
            }
        }
        item.setItemMeta(meta);
        // phù phép CrazyEnchantments (Boom, Lifesteal…): CE tự ghi dòng mô tả và dữ liệu lên đồ
        ConfigurationSection cee = s.getConfigurationSection("ce-enchants");
        if (cee != null) {
            for (String k : cee.getKeys(false)) {
                int lvl = cee.getInt(k);
                if (lvl < 1 || !ce.add(item, k, lvl)) {
                    plugin.getLogger().warning("Thư viện đồ: bỏ qua phù phép CE " + k + " (không có CrazyEnchantments hoặc không có phù phép này)");
                }
            }
        }
        tidyLore(item);   // gom phù phép 3 cái một dòng (sau khi CE ghi mô tả)
        return item;
    }

    /** Lưu đồ đang cầm, giữ nguyên vẹn. Trả thông báo. */
    private String saveHeld(Player p, String id, String label) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            return "§cCầm món đồ cần lưu trên tay chính.";
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        boolean existed = y.isConfigurationSection("items." + id);
        String base = "items." + id;
        // ghi đè hình dạng nhưng GIỮ đầu mục + công dụng đã đặt trên web
        Map<String, Object> keep = new LinkedHashMap<>();
        for (String k : List.of("nhom", "cong-dung", "ky-nang", "hieu-ung")) {
            if (y.contains(base + "." + k)) {
                keep.put(k, y.get(base + "." + k));
            }
        }
        y.set(base, null);
        keep.forEach((k, v) -> y.set(base + "." + k, v));
        y.set(base + ".nguon", "game");
        y.set(base + ".ten-goi", label.isBlank() ? id : label);
        y.set(base + ".data", Base64.getEncoder().encodeToString(hand.serializeAsBytes()));
        y.set(base + ".nguoi-luu", p.getName());
        ItemMeta meta = hand.getItemMeta();
        y.set(base + ".preview.material", hand.getType().name());
        y.set(base + ".preview.amount", hand.getAmount());
        if (meta != null) {
            if (meta.hasDisplayName()) {
                y.set(base + ".preview.name", LEGACY.serialize(meta.displayName()));
            }
            if (meta.hasLore() && meta.lore() != null) {
                List<String> lore = new ArrayList<>();
                for (Component c : meta.lore()) {
                    lore.add(LEGACY.serialize(c));
                }
                y.set(base + ".preview.lore", lore);
            }
            Map<Enchantment, Integer> all = new LinkedHashMap<>(meta.getEnchants());
            if (meta instanceof EnchantmentStorageMeta book) {
                all.putAll(book.getStoredEnchants());
            }
            for (Map.Entry<Enchantment, Integer> e : all.entrySet()) {
                y.set(base + ".preview.enchants." + shortKey(e.getKey().getKey()), e.getValue());
            }
            if (meta.isUnbreakable()) {
                y.set(base + ".preview.unbreakable", true);
            }
        }
        try {
            saveAtomic(y, file);
        } catch (IOException e) {
            return "§cKhông lưu được thư viện: " + e.getMessage();
        }
        return "§aĐã " + (existed ? "ghi đè" : "lưu") + " \"" + id + "\" vào thư viện đồ (giữ nguyên vẹn). Xem/chọn trên trang Cài đặt > Thư viện đồ.";
    }

    private static String shortKey(NamespacedKey k) {
        return "minecraft".equals(k.getNamespace()) ? k.getKey() : k.asString();
    }

    /** /dmm luudo | laydo | thuvien */
    void command(CommandSender s, String[] args) {
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("luudo")) {
            if (!(s instanceof Player p)) {
                s.sendMessage("Lệnh này phải gõ trong game (cầm đồ trên tay).");
            } else if (args.length < 2 || !ID.matcher(args[1]).matches()) {
                s.sendMessage("§e/dmm luudo <mã> [tên gợi nhớ]  §7mã: chữ thường, số, _ (tối đa 32), vd kiem_rong");
            } else {
                s.sendMessage(saveHeld(p, args[1], String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length))));
            }
            return;
        }
        if (sub.equals("laydo")) {
            if (args.length < 2) {
                s.sendMessage("§e/dmm laydo <mã> [số lượng] [người chơi]");
                return;
            }
            // 23/10: số trước, người sau (thường lấy cho mình: /dmm laydo kiem 5). Ô nào là số thì là số lượng, nên thứ tự cũ vẫn chạy.
            int amount = 0;
            String who = null;
            for (int i = 2; i < Math.min(args.length, 4); i++) {
                if (args[i].matches("\\d+") && amount == 0) {
                    amount = Integer.parseInt(args[i]);
                } else if (who == null) {
                    who = args[i];
                } else {
                    s.sendMessage("§cSố lượng không hợp lệ.");
                    return;
                }
            }
            Player target = who != null ? Bukkit.getPlayerExact(who) : (s instanceof Player p ? p : null);
            if (target == null) {
                s.sendMessage(who != null ? "§c" + who + " không online trên server này." : "§e/dmm laydo <mã> [số lượng] <người chơi>");
                return;
            }
            ItemStack item = build(args[1], amount);
            if (item == null) {
                s.sendMessage("§cKhông có \"" + args[1] + "\" trong thư viện (hoặc đồ hỏng, xem log).");
                return;
            }
            for (ItemStack left : target.getInventory().addItem(item).values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), left);
            }
            s.sendMessage("§aĐã đưa \"" + args[1] + "\" x" + item.getAmount() + " cho " + target.getName() + ".");
            plugin.getLogger().info("Thư viện đồ: " + s.getName() + " đưa " + args[1] + " x" + item.getAmount() + " cho " + target.getName());
            return;
        }
        // thuvien [nap]
        if (args.length >= 2 && args[1].equalsIgnoreCase("nap")) {
            int ok = 0;
            List<String> bad = new ArrayList<>();
            for (String id : ids()) {
                if (build(id, 0) != null) {
                    ok++;
                } else {
                    bad.add(id);
                }
            }
            s.sendMessage("Thư viện đồ: " + ok + " món dựng được" + (bad.isEmpty() ? "" : ", lỗi: " + String.join(", ", bad)));
            return;
        }
        List<String> all = ids();
        s.sendMessage("§eThư viện đồ (" + all.size() + ")" + (all.isEmpty() ? ": §ftrống" : ""));
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String id : all) {
            String g = section(id).getString("nhom", "khac");
            groups.computeIfAbsent(NHOM.containsKey(g) ? g : "khac", k -> new ArrayList<>()).add(id);
        }
        for (String g : NHOM.keySet()) {
            if (groups.containsKey(g)) {
                s.sendMessage("§6" + NHOM.get(g) + ": §f" + String.join(", ", groups.get(g)));
            }
        }
        s.sendMessage("§7/dmm luudo <mã> · /dmm laydo <mã> [số] [người] · /dmm thuvien nap");
    }

    private static String js(String v) {
        StringBuilder b = new StringBuilder("\"");
        for (char ch : String.valueOf(v).toCharArray()) {
            switch (ch) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> { }
                default -> {
                    if (ch < 0x20) {
                        b.append(String.format("\\u%04x", (int) ch));
                    } else {
                        b.append(ch);
                    }
                }
            }
        }
        return b.append('"').toString();
    }

    /** Danh sách phù phép + vật liệu cho trang web (đọc registry thật, gồm cả phù phép plugin). */
    void exportMeta() {
        ocmTable = null;   // đọc lại bảng sát thương OldCombatMechanics (dmm reload)
        StringBuilder sb = new StringBuilder("{\"enchantments\":[");
        boolean first = true;
        List<Material> items = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m.isItem() && !m.isAir() && !m.isLegacy()) {
                items.add(m);
            }
        }
        for (Enchantment e : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)) {
            // "items": vật liệu phù phép này dùng được (theo luật của server, gồm cả phù phép plugin) — trang web lọc theo loại đồ
            StringBuilder ok = new StringBuilder();
            for (Material m : items) {
                try {
                    if (e.canEnchantItem(new ItemStack(m))) {
                        ok.append(ok.length() > 0 ? "," : "").append('"').append(m.name()).append('"');
                    }
                } catch (RuntimeException ignored) {
                    // vật liệu lạ: bỏ
                }
            }
            sb.append(first ? "" : ",").append("{\"key\":\"").append(e.getKey().asString()).append("\",\"max\":").append(e.getMaxLevel())
                    .append(",\"items\":[").append(ok).append("]}");
            first = false;
        }
        // phù phép CrazyEnchantments: tên, tên hiện, cấp tối đa, đang bật (Enabled trong Enchantments.yml), loại đồ
        sb.append("],\"ce\":[");
        first = true;
        for (CeBridge.Info i : ce.list()) {
            sb.append(first ? "" : ",").append("{\"name\":").append(js(i.name())).append(",\"display\":").append(js(i.display()))
                    .append(",\"max\":").append(i.max()).append(",\"active\":").append(i.active()).append(",\"type\":").append(js(i.type()))
                    .append(",\"items\":[");
            for (int j = 0; j < i.items().size(); j++) {
                sb.append(j > 0 ? "," : "").append(js(i.items().get(j)));
            }
            sb.append("],\"desc\":[");
            for (int j = 0; j < i.desc().size(); j++) {
                sb.append(j > 0 ? "," : "").append(js(i.desc().get(j)));
            }
            sb.append("]}");
            first = false;
        }
        sb.append("],\"tooltip_max\":").append(plugin.getConfig().getInt("item-library.tooltip-max-lines", 20));
        sb.append(",\"attr_def\":{");   // chỉ số gốc từng loại đồ (trang web điền sẵn ô Chỉ số)
        first = true;
        for (Material m : Material.values()) {
            if (m.isItem() && !m.isAir() && !m.isLegacy()) {
                String d = defaultAttrs(m);
                if (d != null) {
                    sb.append(first ? "" : ",").append('"').append(m.name()).append("\":").append(d);
                    first = false;
                }
            }
        }
        sb.append("},\"materials\":[");
        first = true;
        for (Material m : Material.values()) {
            if (m.isItem() && !m.isAir() && !m.isLegacy()) {
                sb.append(first ? "" : ",").append('"').append(m.name()).append('"');
                first = false;
            }
        }
        sb.append("]}");
        try {
            Files.writeString(new File(plugin.getDataFolder(), "thuvien-meta.json").toPath(), sb, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Không ghi được thuvien-meta.json: " + e.getMessage());
        }
    }
}
