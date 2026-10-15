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
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
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
 * Thư viện đồ: đồ đặt sẵn để làm phần thưởng (điểm danh, nhiệm vụ, vòng quay, đổi mảnh, cửa hàng) hoặc admin phát.
 * Hai loại trong plugins/DuckyMoMoCore/thuvien-do.yml:
 *   nguon: web  – tạo/sửa trên trang Cài đặt (/thuvien): material, name, lore, enchants (cho phép vượt cấp), unbreakable…
 *   nguon: game – /dmm luudo <mã> khi cầm đồ: giữ nguyên vẹn (base64 của ItemStack), kèm "preview" để web hiển thị.
 * Lúc bật, ghi thuvien-meta.json (danh sách phù phép + vật liệu của server, kể cả phù phép plugin như Enchantio) cho trang web.
 */
final class ItemLibrary {
    static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final JavaPlugin plugin;
    private final File file;

    ItemLibrary(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "thuvien-do.yml");
    }

    private YamlConfiguration load() {
        return YamlConfiguration.loadConfiguration(file);
    }

    List<String> ids() {
        ConfigurationSection s = load().getConfigurationSection("items");
        return s == null ? List.of() : new ArrayList<>(s.getKeys(false));
    }

    /** Dựng đồ theo mã; null nếu không có hoặc hỏng. amount <= 0: lấy số lượng ghi trong thư viện. */
    ItemStack build(String id, int amount) {
        ConfigurationSection s = load().getConfigurationSection("items." + id);
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
        return item;
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
        if (!lore.isEmpty()) {
            meta.lore(lore);
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
        item.setItemMeta(meta);
        return item;
    }

    /** Lưu đồ đang cầm, giữ nguyên vẹn. Trả thông báo. */
    private String saveHeld(Player p, String id, String label) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            return "§cCầm món đồ cần lưu trên tay chính.";
        }
        YamlConfiguration y = load();
        boolean existed = y.isConfigurationSection("items." + id);
        String base = "items." + id;
        y.set(base, null);
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
            y.save(file);
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
                s.sendMessage("§e/dmm laydo <mã> [người chơi] [số lượng]");
                return;
            }
            Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : (s instanceof Player p ? p : null);
            if (target == null) {
                s.sendMessage("§cNgười chơi không online trên server này.");
                return;
            }
            int amount = 0;
            if (args.length >= 4) {
                try {
                    amount = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    s.sendMessage("§cSố lượng không hợp lệ.");
                    return;
                }
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
        s.sendMessage("§eThư viện đồ (" + all.size() + "): §f" + (all.isEmpty() ? "trống" : String.join(", ", all)));
        s.sendMessage("§7/dmm luudo <mã> · /dmm laydo <mã> [người] [số] · /dmm thuvien nap");
    }

    /** Danh sách phù phép + vật liệu cho trang web (đọc registry thật, gồm cả phù phép plugin). */
    void exportMeta() {
        StringBuilder sb = new StringBuilder("{\"enchantments\":[");
        boolean first = true;
        for (Enchantment e : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)) {
            sb.append(first ? "" : ",").append("{\"key\":\"").append(e.getKey().asString()).append("\",\"max\":").append(e.getMaxLevel()).append('}');
            first = false;
        }
        sb.append("],\"materials\":[");
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
