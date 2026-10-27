package vn.duckymomo.core.paper;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.util.BoundingBox;

/**
 * Giữ khối dưới chân NPC Citizens (26/10, chủ server: lữ khách ngoài đất hoang bị phá khối dưới chân là rơi xuống).
 * Không phá được (trừ admin), nổ không mất. NPC nhận ra bằng metadata "NPC" Citizens gắn lên thực thể, nên lớp này không chạm
 * kiểu của Citizens. Dân làng shop/thợ rèn/rtp (setAI false) vốn không rơi nên không cần.
 */
final class NpcGround implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    /** Có NPC đứng trên khối b (chân NPC trong ô ngay trên b, kể cả đứng trên phiến/thảm). */
    static boolean underNpc(Block b) {
        BoundingBox box = new BoundingBox(b.getX(), b.getY() + 0.5, b.getZ(), b.getX() + 1, b.getY() + 2, b.getZ() + 1);
        return b.getWorld().getNearbyEntities(box, e -> e.hasMetadata("NPC")).stream()
                .anyMatch(e -> e.getLocation().getBlockY() - b.getY() <= 1);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!e.getPlayer().hasPermission("duckymomo.admin") && underNpc(e.getBlock())) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(LEGACY.deserialize("&eKhối dưới chân NPC không phá được."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(NpcGround::underNpc);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(NpcGround::underNpc);
    }
}
