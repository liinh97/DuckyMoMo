package vn.duckymomo.core.paper;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phần thưởng dùng chung (điểm danh, nhiệm vụ, vòng quay, đổi mảnh…). Mỗi dòng một quà, trang Cài đặt có ô chọn:
 *   xu <số>               cộng xu (Vault)
 *   do <mã> [số]          đồ trong thư viện đồ (ItemLibrary)
 *   vat <VẬT_LIỆU> [số]   đồ thường
 *   lenh <lệnh>           lệnh console, {player} = tên người chơi
 *   manh <loại> <số>      mảnh sự kiện (FragmentShop, khi có)
 *   danhhieu <mã>         trao danh hiệu (Leaderboard, mã trong leaderboard.titles), giữ qua các mùa
 *   petexp <số>           kinh nghiệm cho pet MyPet đang dẫn (không dẫn pet thì mất: nơi phát nên kiểm tra trước)
 * Túi đầy thì đồ rơi xuống chân người chơi.
 */
final class Rewards {
    /** Gắn khi có module mảnh sự kiện: (người chơi, loại, số) -> mô tả, null nếu không phát được. */
    interface FragmentGiver {
        String give(Player p, String type, int amount);
    }

    private final JavaPlugin plugin;
    private final ItemLibrary library;
    private FragmentGiver fragments;

    Rewards(JavaPlugin plugin, ItemLibrary library) {
        this.plugin = plugin;
        this.library = library;
    }

    void setFragments(FragmentGiver giver) {
        this.fragments = giver;
    }

    /** Phát danh sách quà, trả mô tả từng món đã phát (để báo cho người chơi). */
    List<String> give(Player p, List<String> specs) {
        List<String> out = new ArrayList<>();
        for (String spec : specs) {
            String d = giveOne(p, spec.trim());
            if (d != null) {
                out.add(d);
            }
        }
        return out;
    }

    private String giveOne(Player p, String spec) {
        String[] a = spec.split("\\s+", 2);
        String kind = a[0].toLowerCase(Locale.ROOT);
        String rest = a.length > 1 ? a[1].trim() : "";
        try {
            switch (kind) {
                case "xu" -> {
                    double amount = Double.parseDouble(rest);
                    if (!VaultEconomy.available() || amount <= 0) {
                        return null;
                    }
                    VaultEconomy.deposit(p, amount);
                    return VaultEconomy.format(amount);
                }
                case "do" -> {
                    String[] b = rest.split("\\s+");
                    ItemStack item = library.build(b[0], b.length > 1 ? Integer.parseInt(b[1]) : 0);
                    if (item == null) {
                        plugin.getLogger().warning("Phần thưởng: không có \"" + b[0] + "\" trong thư viện đồ");
                        return null;
                    }
                    String name = nameOf(item);
                    hand(p, item);
                    return name + " x" + item.getAmount();
                }
                case "vat" -> {
                    String[] b = rest.split("\\s+");
                    Material m = Material.matchMaterial(b[0]);
                    int n = b.length > 1 ? Integer.parseInt(b[1]) : 1;
                    if (m == null || !m.isItem() || n < 1) {
                        plugin.getLogger().warning("Phần thưởng: vật liệu sai \"" + rest + "\"");
                        return null;
                    }
                    int left = n;
                    while (left > 0) {
                        int take = Math.min(left, m.getMaxStackSize());
                        hand(p, new ItemStack(m, take));
                        left -= take;
                    }
                    return n + " " + vi(m.name());
                }
                case "lenh" -> {
                    if (rest.isEmpty()) {
                        return null;
                    }
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rest.replace("{player}", p.getName()));
                    return null;
                }
                case "manh" -> {
                    String[] b = rest.split("\\s+");
                    if (fragments == null || b.length < 2) {
                        plugin.getLogger().warning("Phần thưởng: chưa bật mảnh sự kiện, bỏ qua \"" + spec + "\"");
                        return null;
                    }
                    return fragments.give(p, b[0], Integer.parseInt(b[1]));
                }
                case "danhhieu" -> {
                    Leaderboard lb = Shared.leaderboard;
                    String t = rest.trim().toLowerCase(Locale.ROOT);
                    if (lb == null || t.isEmpty()) {
                        return null;
                    }
                    lb.grant(p.getUniqueId(), p.getName(), t);
                    return "danh hiệu " + lb.titleText(t);
                }
                case "petexp" -> {
                    double n = Double.parseDouble(rest.trim());
                    return Arena.addPetExp(p, n) ? (long) n + " kinh nghiệm pet" : null;
                }
                default -> {
                    plugin.getLogger().warning("Phần thưởng không hiểu: \"" + spec + "\"");
                    return null;
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Phần thưởng lỗi \"" + spec + "\": " + e.getMessage());
            return null;
        }
    }

    /** Mô tả quà cho người chơi đọc (không phát): "100 xu", "Kiếm Rồng x1", "3 Diamond", "2 mảnh lamnguy". Lệnh: bỏ qua. */
    List<String> describe(List<String> specs) {
        List<String> out = new ArrayList<>();
        for (String spec : specs) {
            String[] a = spec.trim().split("\\s+");
            try {
                switch (a[0].toLowerCase(Locale.ROOT)) {
                    case "xu" -> out.add(xu(Double.parseDouble(a[1])));
                    case "do" -> {
                        ItemStack item = library.build(a[1], a.length > 2 ? Integer.parseInt(a[2]) : 0);
                        out.add(item == null ? a[1] : nameOf(item) + " x" + item.getAmount());
                    }
                    case "vat" -> out.add((a.length > 2 ? a[2] : "1") + " " + vi(a[1]));
                    case "manh" -> out.add(a[2] + " mảnh " + a[1]);
                    case "petexp" -> out.add(a[1] + " kinh nghiệm cho pet đang dẫn");
                    case "danhhieu" -> out.add("danh hiệu " + (Shared.leaderboard == null ? a[1] : Shared.leaderboard.titleText(a[1])));
                    default -> { }
                }
            } catch (RuntimeException ignored) {
                // quà sai định dạng: không mô tả
            }
        }
        return out;
    }

    static void hand(Player p, ItemStack item) {
        for (ItemStack left : p.getInventory().addItem(item).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
    }

    static String nameOf(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().serialize(meta.displayName()) + "&r";
        }
        String n = vi(item.getType().name());   // tên món đứng riêng: viết hoa chữ đầu
        return n.isEmpty() ? n : n.substring(0, 1).toUpperCase(Locale.forLanguageTag("vi")) + n.substring(1);
    }

    /** Tiền kiểu Việt: 4.000 xu (Essentials đang để ký hiệu $, chỉ được 1 ký tự nên không ghi "xu" được ở đó). */
    static String xu(double v) {
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
        nf.setMaximumFractionDigits(2);   // phí lẻ như 0,5 xu/ô vẫn hiện đúng
        return nf.format(v) + " xu";
    }

    private static final java.util.Map<String, String> VI = java.util.Map.ofEntries(
            java.util.Map.entry("DIAMOND", "kim cương"), java.util.Map.entry("DIAMOND_BLOCK", "khối kim cương"),
            java.util.Map.entry("IRON_INGOT", "thỏi sắt"), java.util.Map.entry("GOLD_INGOT", "thỏi vàng"),
            java.util.Map.entry("EMERALD", "ngọc lục bảo"), java.util.Map.entry("NETHERITE_INGOT", "thỏi netherite"),
            java.util.Map.entry("GOLDEN_APPLE", "táo vàng"), java.util.Map.entry("ENCHANTED_GOLDEN_APPLE", "táo vàng phù phép"),
            java.util.Map.entry("EXPERIENCE_BOTTLE", "bình kinh nghiệm"), java.util.Map.entry("BREAD", "bánh mì"),
            java.util.Map.entry("ARROW", "mũi tên"), java.util.Map.entry("MUSIC_DISC_PIGSTEP", "đĩa nhạc Pigstep"),
            java.util.Map.entry("MUSIC_DISC_CAT", "đĩa nhạc Cat"), java.util.Map.entry("TOTEM_OF_UNDYING", "vật tổ bất tử"),
            java.util.Map.entry("ENDER_PEARL", "ngọc ender"), java.util.Map.entry("NAME_TAG", "bảng tên"));

    /** Tên tiếng Việt chính thức của Minecraft cho mọi vật phẩm/khối (ten-vat-pham-vi.properties, tạo bằng tools/ten-vat-pham-vi.py). */
    private static final java.util.Map<String, String> VANILLA_VI = loadVanilla();

    private static java.util.Map<String, String> loadVanilla() {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        try (java.io.InputStream in = Rewards.class.getResourceAsStream("/ten-vat-pham-vi.properties")) {
            if (in != null) {
                java.util.Properties p = new java.util.Properties();
                p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                p.forEach((k, v) -> m.put(String.valueOf(k), String.valueOf(v)));
            }
        } catch (java.io.IOException ignored) {
            // không có bảng: dùng tên gốc
        }
        return m;
    }

    /** Tên Việt của vật phẩm: tên gọi riêng (VI), rồi tên tiếng Việt chính thức của Minecraft, cuối cùng mới tới tên gốc. */
    static String vi(String material) {
        String k = material.toUpperCase(Locale.ROOT);
        String v = VI.get(k);
        if (v == null) {
            v = VANILLA_VI.get(k);
            // ghép sau số lượng ("1 mũ da") nên viết thường chữ đầu, như các tên trong VI
            if (v != null && !v.isEmpty()) {
                v = v.substring(0, 1).toLowerCase(Locale.forLanguageTag("vi")) + v.substring(1);
            }
        }
        return v != null ? v : pretty(k);
    }

    static String pretty(String material) {
        StringBuilder sb = new StringBuilder();
        for (String w : material.toLowerCase(Locale.ROOT).split("_")) {
            if (!w.isEmpty()) {
                sb.append(sb.length() > 0 ? " " : "").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
        }
        return sb.toString();
    }
}
