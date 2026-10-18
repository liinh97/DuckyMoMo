package vn.duckymomo.core.paper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Gom các dòng nhắc lúc vào game (hướng dẫn, mùa, công thành, điểm danh, đơn hàng…) thành MỘT khung, gửi delay-ticks sau khi vào
 * (để không chen giữa thông báo của plugin khác). Module gọi JoinNotes.add(p, thứ tự, dòng) ngay trong PlayerJoinEvent.
 * Không phụ thuộc plugin nào: dùng được ở mọi server.
 */
final class JoinNotes {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final long DELAY = 100L;   // 5 giây
    private static JavaPlugin plugin;
    private static String title = "DuckyMoMo";
    private static final Map<UUID, List<Object[]>> pending = new HashMap<>();

    private JoinNotes() {
    }

    static void init(JavaPlugin p, String serverTitle) {
        plugin = p;
        title = serverTitle == null || serverTitle.isBlank() ? "DuckyMoMo" : serverTitle;
        pending.clear();
    }

    /** Thêm một dòng (order nhỏ đứng trước). Gửi gộp sau DELAY tick kể từ dòng đầu tiên. */
    static void add(Player p, int order, Component line) {
        if (plugin == null) {
            p.sendMessage(line);
            return;
        }
        List<Object[]> list = pending.get(p.getUniqueId());
        if (list == null) {
            list = new ArrayList<>();
            pending.put(p.getUniqueId(), list);
            UUID id = p.getUniqueId();
            Bukkit.getScheduler().runTaskLater(plugin, () -> flush(id), DELAY);
        }
        list.add(new Object[] {order, line});
    }

    private static void flush(UUID id) {
        List<Object[]> list = pending.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (list == null || list.isEmpty() || p == null || !p.isOnline()) {
            return;
        }
        list.sort((a, b) -> Integer.compare((Integer) a[0], (Integer) b[0]));
        p.sendMessage(c("&6&m          &r &6&l" + title + " &6&m          "));
        for (Object[] o : list) {
            p.sendMessage(c("&6▏ ").append((Component) o[1]));
        }
        p.sendMessage(c("&6&m                                        "));
    }

    static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
}
