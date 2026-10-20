package vn.duckymomo.core.paper;

import java.util.Locale;
import java.util.Set;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Jobs Reborn (21/10): /jobs không kèm gì chỉ in danh sách lệnh -> mở thẳng menu chọn nghề (/jobs browse).
 * Paper 26: đổi nội dung lệnh bằng setMessage không chạy với người chơi, nên huỷ rồi chạy lệnh mới.
 */
final class JobsMenu implements Listener {

    private static final Set<String> BARE = Set.of("/jobs", "/job", "/jobs:jobs", "/jobs:job");

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (BARE.contains(e.getMessage().trim().toLowerCase(Locale.ROOT))) {
            e.setCancelled(true);
            e.getPlayer().performCommand("jobs browse");
        }
    }
}
