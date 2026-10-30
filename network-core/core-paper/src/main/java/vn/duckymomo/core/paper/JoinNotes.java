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
    /** 30/10: dòng chào ("%name%" = tên) + link cộng đồng (bấm mở), config join-welcome. Trống = khung cũ. */
    private static String welcome = "";
    private static final List<String[]> links = new ArrayList<>();
    private static final Map<UUID, List<Object[]>> pending = new HashMap<>();

    private JoinNotes() {
    }

    static void init(JavaPlugin p, String serverTitle, org.bukkit.configuration.ConfigurationSection w) {
        plugin = p;
        title = serverTitle == null || serverTitle.isBlank() ? "DuckyMoMo" : serverTitle;
        pending.clear();
        links.clear();
        welcome = w == null ? "" : w.getString("title", "");
        if (w != null) {
            for (Map<?, ?> m : w.getMapList("links")) {
                String label = String.valueOf(m.get("label")), text = String.valueOf(m.get("text"));
                Object url = m.get("url");
                if (!"null".equals(label) && !"null".equals(text)) {
                    links.add(new String[] {label, text, url == null ? "" : String.valueOf(url)});
                }
            }
        }
    }

    /** Thêm một dòng (order nhỏ đứng trước). Gửi gộp sau DELAY tick kể từ dòng đầu tiên. */
    static void add(Player p, int order, Component line) {
        if (!PlayerSettings.on(p, "khungnhac")) {
            return;   // người chơi tắt khung nhắc (/caidat)
        }
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
        if (welcome.isBlank()) {
            p.sendMessage(c("&6&m          &r &6" + title + " &6&m          "));
            for (Object[] o : list) {
                p.sendMessage(c("&6▏ ").append((Component) o[1]));
            }
            p.sendMessage(c("&6&m                                        "));
            return;
        }
        // Bố cục 30/10: vạch · dòng chào · link cộng đồng · các dòng nhắc · vạch
        p.sendMessage(c("&8&m                                                     "));
        p.sendMessage(c(welcome.replace("%name%", p.getName())));
        // 30/10 chủ server "thông báo hơi ríu rít": link gộp 1 dòng, các dòng nhắc liền nhau, không dòng trống
        if (!links.isEmpty()) {
            Component line = c(" ");
            boolean first = true;
            for (String[] l : links) {
                if (!first) {
                    line = line.append(c(" &8· "));
                }
                first = false;
                if (l[2].isBlank()) {
                    line = line.append(c("&7" + l[0].trim() + ": &f" + l[1]));
                } else {
                    line = line.append(c("&b[" + l[0].trim() + "]").clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(l[2]))
                            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(c("&f" + l[1] + "\n&7Bấm để mở"))));
                }
            }
            p.sendMessage(line);
        }
        for (Object[] o : list) {
            p.sendMessage(c(" ").append((Component) o[1]));
        }
        p.sendMessage(c("&8&m                                                     "));
    }

    static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
}
