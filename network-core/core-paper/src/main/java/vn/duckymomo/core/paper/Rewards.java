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
                    return n + " " + pretty(m.name());
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
        return pretty(item.getType().name());
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
