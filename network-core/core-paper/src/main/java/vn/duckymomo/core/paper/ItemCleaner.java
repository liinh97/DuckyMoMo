package vn.duckymomo.core.paper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Trident;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Dọn rác định kỳ: báo trước trong chat (đếm ngược), rồi xoá vật phẩm rơi trên đất và mũi tên cắm đất.
 * Không xoá: vật phẩm vừa rơi (< min-age-seconds, ví dụ đồ rơi khi chết), vật phẩm có tên (đổi tên bằng đe), đinh ba.
 * Cấu hình: item-cleaner trong plugins/DuckyMoMoCore/config.yml.
 */
final class ItemCleaner {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final JavaPlugin plugin;
    private final int intervalSeconds;
    private final Set<Integer> warnAt;
    private final int minAgeTicks;
    private final boolean arrows;
    private final String warnMsg;
    private final String doneMsg;
    private int left;

    ItemCleaner(JavaPlugin plugin, ConfigurationSection c) {
        this.plugin = plugin;
        this.intervalSeconds = Math.max(60, c.getInt("interval-minutes", 10) * 60);
        this.warnAt = new HashSet<>(c.getIntegerList("warn-seconds").isEmpty()
                ? List.of(60, 30, 10, 5, 3, 2, 1) : c.getIntegerList("warn-seconds"));
        this.minAgeTicks = c.getInt("min-age-seconds", 30) * 20;
        this.arrows = c.getBoolean("arrows", true);
        this.warnMsg = c.getString("warn-message", "&c[⚠] &7Dọn vật phẩm dưới đất sau &e{s}s&7.");
        this.doneMsg = c.getString("done-message", "&c[⚠] &7Đã dọn sạch &e{n} &7vật phẩm!");
        this.left = intervalSeconds;
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        plugin.getLogger().info("Dọn rác: mỗi " + intervalSeconds / 60 + " phút, báo trước " + warnAt);
    }

    private void tick() {
        left--;
        if (warnAt.contains(left)) {
            Bukkit.broadcast(LEGACY.deserialize(warnMsg.replace("{s}", Integer.toString(left))));
        }
        if (left <= 0) {
            int n = clean();
            Bukkit.broadcast(LEGACY.deserialize(doneMsg.replace("{n}", Integer.toString(n))));
            left = intervalSeconds;
        }
    }

    /** Xoá trên mọi world (chỉ chunk đang nạp); trả về số thực thể đã xoá. */
    int clean() {
        int n = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e instanceof Item item) {
                    if (item.getTicksLived() < minAgeTicks || (item.getItemStack().hasItemMeta() && item.getItemStack().getItemMeta().hasDisplayName())) {
                        continue;
                    }
                } else if (!(arrows && e instanceof AbstractArrow a && !(a instanceof Trident) && a.isInBlock())) {
                    continue;
                }
                e.remove();
                n++;
            }
        }
        return n;
    }

    /** Lệnh /dmm clean: dọn ngay (admin). */
    Component cleanNow() {
        return LEGACY.deserialize(doneMsg.replace("{n}", Integer.toString(clean())));
    }
}
