package vn.duckymomo.core.paper;

import java.util.Arrays;
import java.util.List;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Chống "đặt rồi phá" để lấy tiền nghề (uJobs trả tiền mỗi lần phá gỗ/đá/quặng, không phân biệt block tự nhiên).
 * Block thuộc danh sách theo dõi do người chơi đặt được đánh dấu trong dữ liệu chunk. Khi phá lại block đã đánh dấu:
 * huỷ sự kiện rồi tự phá (rơi đồ như thường) → uJobs (MONITOR, bỏ qua sự kiện đã huỷ) không trả tiền.
 * CoreProtect cũng bỏ qua sự kiện đã huỷ nên ghi lại lượt phá qua API của nó.
 * Cấu hình: job-guard trong plugins/DuckyMoMoCore/config.yml (materials: tên đúng hoặc dạng *_LOG, STONE*).
 */
final class PlacedBlockGuard implements Listener {

    private final NamespacedKey key;
    private final List<String> patterns;

    PlacedBlockGuard(JavaPlugin plugin, List<String> patterns) {
        this.key = new NamespacedKey(plugin, "placed");
        this.patterns = patterns.stream().map(String::toUpperCase).toList();
    }

    private boolean tracked(Material m) {
        String n = m.name();
        for (String p : patterns) {
            if (p.startsWith("*") ? n.endsWith(p.substring(1)) : p.endsWith("*") ? n.startsWith(p.substring(0, p.length() - 1)) : n.equals(p)) {
                return true;
            }
        }
        return false;
    }

    /** Vị trí trong chunk gói vào một int: x 4 bit, z 4 bit, y (+2048) 12 bit. */
    private static int pack(Block b) {
        return (b.getX() & 15) | (b.getZ() & 15) << 4 | (b.getY() + 2048) << 8;
    }

    private int[] get(PersistentDataContainer pdc) {
        int[] a = pdc.get(key, PersistentDataType.INTEGER_ARRAY);
        return a == null ? new int[0] : a;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        if (!tracked(b.getType())) {
            return;
        }
        PersistentDataContainer pdc = b.getChunk().getPersistentDataContainer();
        int[] a = get(pdc);
        int p = pack(b);
        for (int v : a) {
            if (v == p) {
                return;
            }
        }
        int[] n = Arrays.copyOf(a, a.length + 1);
        n[a.length] = p;
        pdc.set(key, PersistentDataType.INTEGER_ARRAY, n);
    }

    /** Sau các plugin bảo vệ đất (Towny, WorldGuard…), trước uJobs/CoreProtect (MONITOR). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Chunk c = b.getChunk();
        PersistentDataContainer pdc = c.getPersistentDataContainer();
        int[] a = get(pdc);
        if (a.length == 0) {
            return;
        }
        int p = pack(b);
        int i = 0;
        while (i < a.length && a[i] != p) {
            i++;
        }
        if (i == a.length) {
            return;
        }
        int[] n = new int[a.length - 1];
        System.arraycopy(a, 0, n, 0, i);
        System.arraycopy(a, i + 1, n, i, a.length - i - 1);
        if (n.length == 0) {
            pdc.remove(key);
        } else {
            pdc.set(key, PersistentDataType.INTEGER_ARRAY, n);
        }
        if (!tracked(b.getType())) {
            return; // vị trí cũ đã đổi sang block khác (piston, nổ…): chỉ xoá dấu
        }
        e.setCancelled(true);
        logRemoval(e.getPlayer().getName(), b);
        if (e.getPlayer().getGameMode() == GameMode.CREATIVE) {
            b.setType(Material.AIR);
        } else {
            b.breakNaturally(e.getPlayer().getInventory().getItemInMainHand(), true, true);
        }
    }

    private static void logRemoval(String user, Block b) {
        if (!Bukkit.getPluginManager().isPluginEnabled("CoreProtect")) {
            return;
        }
        CoreProtectAPI api = CoreProtect.getInstance().getAPI();
        if (api.isEnabled()) {
            api.logRemoval(user, b.getLocation(), b.getType(), b.getBlockData());
        }
    }
}
