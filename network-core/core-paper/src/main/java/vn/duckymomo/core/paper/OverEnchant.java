package vn.duckymomo.core.paper;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Đồ / sách phù phép vượt cấp tối đa (vd Sắc bén XXX), CHỈ admin tạo (phần thưởng sự kiện, hàng hiếm trong game;
 * KHÔNG bán lấy tiền thật: luật Mojang).
 *   /dmm enchant <phù phép> <cấp>        phù phép món đang cầm (cấp 0 = gỡ)
 *   /dmm sach <phù phép> <cấp> [người]   tạo sách
 * Phù phép: tên gốc (sharpness, efficiency...) hoặc có namespace (enchantio:telepathy). Cấp tối đa: over-enchant.max-level.
 * Đe: ghép SÁCH vượt cấp vào ĐỒ thì giữ nguyên cấp của sách (đe gốc có thể hạ về cấp tối đa); ghép hai sách với nhau
 * vẫn theo luật gốc nên người chơi không tự nâng cấp được.
 */
final class OverEnchant implements Listener {

    private final int maxLevel;

    OverEnchant(int maxLevel) {
        this.maxLevel = Math.max(1, Math.min(255, maxLevel));
    }

    private static Enchantment find(String name) {
        String n = name.toLowerCase();
        NamespacedKey key = n.contains(":") ? NamespacedKey.fromString(n) : NamespacedKey.minecraft(n);
        return key == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
    }

    boolean command(CommandSender s, String[] a) {
        // a[0] = enchant | sach
        if (a.length < 3) {
            s.sendMessage(Component.text("/dmm enchant <phù phép> <cấp>   ·   /dmm sach <phù phép> <cấp> [người chơi]", NamedTextColor.YELLOW));
            return true;
        }
        Enchantment e = find(a[1]);
        if (e == null) {
            s.sendMessage(Component.text("Không có phù phép '" + a[1] + "' (vd sharpness, efficiency, enchantio:telepathy)", NamedTextColor.RED));
            return true;
        }
        int level;
        try {
            level = Integer.parseInt(a[2]);
        } catch (NumberFormatException ex) {
            level = -1;
        }
        if (level < 0 || level > maxLevel) {
            s.sendMessage(Component.text("Cấp phải từ 0 đến " + maxLevel + " (over-enchant.max-level)", NamedTextColor.RED));
            return true;
        }
        if (a[0].equalsIgnoreCase("enchant")) {
            if (!(s instanceof Player p)) {
                s.sendMessage("Chỉ dùng trong game (phù phép món đang cầm). Từ console dùng /dmm sach.");
                return true;
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) {
                s.sendMessage(Component.text("Hãy cầm món đồ cần phù phép.", NamedTextColor.RED));
                return true;
            }
            if (level == 0) {
                hand.removeEnchantment(e);
            } else {
                hand.addUnsafeEnchantment(e, level);
            }
            s.sendMessage(Component.text("Đã đặt " + e.getKey().asString() + " cấp " + level + " cho món đang cầm.", NamedTextColor.GREEN));
            log(s, "enchant " + e.getKey() + " " + level + " cho " + hand.getType());
            return true;
        }
        // sach
        Player target = a.length > 3 ? Bukkit.getPlayerExact(a[3]) : (s instanceof Player p ? p : null);
        if (target == null) {
            s.sendMessage(Component.text("Không thấy người chơi (phải đang online).", NamedTextColor.RED));
            return true;
        }
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(e, Math.max(1, level), true);
        book.setItemMeta(meta);
        target.getInventory().addItem(book).values().forEach(left -> target.getWorld().dropItem(target.getLocation(), left));
        s.sendMessage(Component.text("Đã tặng sách " + e.getKey().asString() + " cấp " + level + " cho " + target.getName() + ".", NamedTextColor.GREEN));
        log(s, "sach " + e.getKey() + " " + level + " cho " + target.getName());
        return true;
    }

    private static void log(CommandSender s, String what) {
        Bukkit.getLogger().info("[DuckyMoMoCore] Đồ vượt cấp: " + s.getName() + " " + what);
    }

    /** Ghép sách vượt cấp vào đồ: giữ cấp của sách. Chạy sau tính toán gốc của đe. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent ev) {
        ItemStack left = ev.getInventory().getFirstItem();
        ItemStack right = ev.getInventory().getSecondItem();
        if (left == null || right == null || right.getType() != Material.ENCHANTED_BOOK || left.getType() == Material.ENCHANTED_BOOK) {
            return;
        }
        if (!(right.getItemMeta() instanceof EnchantmentStorageMeta book)) {
            return;
        }
        boolean over = false;
        for (Map.Entry<Enchantment, Integer> en : book.getStoredEnchants().entrySet()) {
            if (en.getValue() > en.getKey().getMaxLevel()) {
                over = true;
            }
        }
        if (!over) {
            return;
        }
        ItemStack result = ev.getResult() != null && ev.getResult().getType() != Material.AIR ? ev.getResult().clone() : left.clone();
        ItemMeta rm = result.getItemMeta();
        boolean applied = false;
        for (Map.Entry<Enchantment, Integer> en : book.getStoredEnchants().entrySet()) {
            Enchantment e = en.getKey();
            if (!e.canEnchantItem(left)) {
                continue;
            }
            int lv = Math.max(en.getValue(), left.getEnchantmentLevel(e));
            rm.addEnchant(e, lv, true);
            applied = true;
        }
        if (!applied) {
            return;
        }
        result.setItemMeta(rm);
        ev.setResult(result);
        // giá đe: 30 cấp kinh nghiệm (hiện "Too Expensive" nếu > 39 với người không phải creative)
        ev.getView().setRepairCost(Math.min(39, Math.max(ev.getView().getRepairCost(), 30)));
    }
}
