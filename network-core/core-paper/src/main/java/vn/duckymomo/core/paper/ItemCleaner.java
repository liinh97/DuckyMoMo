package vn.duckymomo.core.paper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Dọn rác định kỳ: báo trước trong chat (đếm ngược), rồi xoá vật phẩm rơi trên đất và mũi tên cắm đất.
 * Không xoá: vật phẩm vừa rơi (< min-age-seconds), vật phẩm có tên (đổi tên bằng đe), đinh ba,
 * đồ quanh chỗ người chơi chết (death-protect-*), đồ trong exclude-areas hoặc đất thị trấn (skip-towns).
 * Trong phiên đánh công thành (SiegeWar) thì bỏ qua lượt dọn.
 * Cấu hình: item-cleaner trong plugins/DuckyMoMoCore/config.yml.
 */
final class ItemCleaner implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private record Death(World world, double x, double y, double z, long at) {
    }

    private record Area(int minX, int minZ, int maxX, int maxZ) {
        boolean contains(Location l) {
            return l.getBlockX() >= minX && l.getBlockX() <= maxX && l.getBlockZ() >= minZ && l.getBlockZ() <= maxZ;
        }
    }

    private final JavaPlugin plugin;
    private final int intervalSeconds;
    private final Set<Integer> warnAt;
    private final int minAgeTicks;
    private final boolean arrows;
    private final String warnMsg;
    private final String doneMsg;
    private final String skipMsg;
    private final long deathProtectMs;
    private final double deathRadiusSq;
    private final boolean pauseDuringBattle;
    private final boolean skipTowns;
    private final List<Area> excludes = new ArrayList<>();
    private final Deque<Death> deaths = new ArrayDeque<>();
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
        this.skipMsg = c.getString("battle-skip-message", "");
        this.deathProtectMs = Math.max(0, c.getLong("death-protect-minutes", 10)) * 60_000L;
        double r = Math.max(0, c.getDouble("death-protect-radius", 8));
        this.deathRadiusSq = r * r;
        this.pauseDuringBattle = c.getBoolean("pause-during-battle", true);
        this.skipTowns = c.getBoolean("skip-towns", false);
        // Mỗi dòng: x1 z1 x2 z2 (dấu cách hoặc dấu phẩy), áp dụng cho mọi world
        for (String line : c.getStringList("exclude-areas")) {
            String[] p = line.trim().split("[\\s,;]+");
            if (p.length < 4) {
                continue;
            }
            try {
                int x1 = Integer.parseInt(p[0]);
                int z1 = Integer.parseInt(p[1]);
                int x2 = Integer.parseInt(p[2]);
                int z2 = Integer.parseInt(p[3]);
                excludes.add(new Area(Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2)));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("item-cleaner.exclude-areas: bỏ qua dòng sai \"" + line + "\" (cần 4 số: x1 z1 x2 z2)");
            }
        }
        this.left = intervalSeconds;
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        plugin.getLogger().info("Dọn rác: mỗi " + intervalSeconds / 60 + " phút, báo trước " + warnAt
                + ", giữ đồ khi chết " + deathProtectMs / 60_000 + " phút, " + excludes.size() + " vùng không dọn"
                + (skipTowns ? ", không dọn đất thị trấn" : "") + (pauseDuringBattle ? ", nghỉ trong giờ công thành" : ""));
    }

    private void tick() {
        left--;
        if (warnAt.contains(left) && !inBattle()) {
            Bukkit.broadcast(LEGACY.deserialize(warnMsg.replace("{s}", Integer.toString(left))));
        }
        if (left <= 0) {
            left = intervalSeconds;
            if (inBattle()) {
                if (!skipMsg.isBlank()) {
                    Bukkit.broadcast(LEGACY.deserialize(skipMsg));
                }
                return;
            }
            int n = clean();
            if (n > 0) {   // không dọn được gì thì im lặng, đỡ rác chat
                Bukkit.broadcast(LEGACY.deserialize(doneMsg.replace("{n}", Integer.toString(n))));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (deathProtectMs > 0) {
            Location l = event.getPlayer().getLocation();
            deaths.addLast(new Death(l.getWorld(), l.getX(), l.getY(), l.getZ(), System.currentTimeMillis()));
        }
    }

    /** Xoá trên mọi world (chỉ chunk đang nạp); trả về số thực thể đã xoá. */
    int clean() {
        long now = System.currentTimeMillis();
        while (!deaths.isEmpty() && now - deaths.peekFirst().at() > deathProtectMs) {
            deaths.removeFirst();
        }
        boolean towns = skipTowns && Bukkit.getPluginManager().isPluginEnabled("Towny");
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
                Location l = e.getLocation();
                if (nearDeath(l) || inExcluded(l) || (towns && TownyCheck.inTown(l))) {
                    continue;
                }
                e.remove();
                n++;
            }
        }
        return n;
    }

    private boolean nearDeath(Location l) {
        for (Death d : deaths) {
            if (d.world() == l.getWorld()) {
                double dx = d.x() - l.getX();
                double dy = d.y() - l.getY();
                double dz = d.z() - l.getZ();
                if (dx * dx + dy * dy + dz * dz <= deathRadiusSq) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean inExcluded(Location l) {
        for (Area a : excludes) {
            if (a.contains(l)) {
                return true;
            }
        }
        return false;
    }

    /** Đang trong phiên đánh công thành (hỏi SiegeWar; phiên tự huỷ khi không có cuộc vây nào). */
    boolean inBattle() {
        return pauseDuringBattle && SiegeWarHook.battleActive();
    }

    /** Lệnh /dmm clean: dọn ngay (admin), không xét giờ công thành. */
    Component cleanNow() {
        return LEGACY.deserialize(doneMsg.replace("{n}", Integer.toString(clean())));
    }

    /** Lớp riêng: server không có Towny không nạp tới lớp này. */
    private static final class TownyCheck {
        static boolean inTown(Location l) {
            return !com.palmergames.bukkit.towny.TownyAPI.getInstance().isWilderness(l);
        }
    }
}
