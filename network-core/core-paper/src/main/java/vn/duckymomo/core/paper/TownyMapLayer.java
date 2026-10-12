package vn.duckymomo.core.paper;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.jpenilla.squaremap.api.BukkitAdapter;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.MapWorld;
import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.SimpleLayerProvider;
import xyz.jpenilla.squaremap.api.SquaremapProvider;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;

/**
 * Vẽ ranh giới thị trấn Towny lên bản đồ web squaremap (lớp "Thị trấn").
 * Mỗi thị trấn: các ô đất gộp thành hình chữ nhật (gộp theo hàng rồi theo cột), tô màu theo quốc gia (thị trấn tự do: xám).
 * Rê chuột: tên thị trấn, quốc gia, thị trưởng, số dân. Cập nhật mỗi interval-seconds (đọc Towny trên luồng chính).
 * Cấu hình: towny-map trong plugins/DuckyMoMoCore/config.yml.
 */
final class TownyMapLayer {

    private static final Key LAYER = Key.of("duckymomo_towny");

    private final JavaPlugin plugin;
    private final String worldName;
    private final int intervalSeconds;
    private SimpleLayerProvider provider;

    TownyMapLayer(JavaPlugin plugin, String worldName, int intervalSeconds) {
        this.plugin = plugin;
        this.worldName = worldName;
        this.intervalSeconds = Math.max(10, intervalSeconds);
    }

    void start() {
        // squaremap nạp world sau khi server khởi động xong: chờ 10 giây rồi mới đăng ký lớp
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            World w = Bukkit.getWorld(worldName);
            MapWorld map = w == null ? null
                    : SquaremapProvider.get().getWorldIfEnabled(BukkitAdapter.worldIdentifier(w)).orElse(null);
            if (map == null) {
                plugin.getLogger().warning("Bản đồ thị trấn: squaremap chưa bật cho world " + worldName);
                return;
            }
            provider = SimpleLayerProvider.builder("Thị trấn").showControls(true).defaultHidden(false)
                    .layerPriority(5).zIndex(5).build();
            map.layerRegistry().register(LAYER, provider);
            Bukkit.getScheduler().runTaskTimer(plugin, this::update, 20L, intervalSeconds * 20L);
            plugin.getLogger().info("Bản đồ thị trấn: bật (world " + worldName + ", mỗi " + intervalSeconds + " giây)");
        }, 200L);
    }

    void stop() {
        World w = Bukkit.getWorld(worldName);
        if (provider != null && w != null) {
            SquaremapProvider.get().getWorldIfEnabled(BukkitAdapter.worldIdentifier(w))
                    .ifPresent(m -> m.layerRegistry().unregister(LAYER));
        }
    }

    private void update() {
        // /squaremap reload tạo lại MapWorld: lớp đã đăng ký mất theo -> đăng ký lại
        World w = Bukkit.getWorld(worldName);
        if (w != null) {
            SquaremapProvider.get().getWorldIfEnabled(BukkitAdapter.worldIdentifier(w)).ifPresent(m -> {
                if (!m.layerRegistry().hasEntry(LAYER)) {
                    m.layerRegistry().register(LAYER, provider);
                }
            });
        }
        int size = TownySettings.getTownBlockSize();
        provider.clearMarkers();
        for (Town town : TownyAPI.getInstance().getTowns()) {
            Map<Integer, TreeSet<Integer>> rows = new TreeMap<>();
            for (TownBlock tb : town.getTownBlocks()) {
                if (tb.getWorld() != null && worldName.equals(tb.getWorld().getName())) {
                    rows.computeIfAbsent(tb.getZ(), k -> new TreeSet<>()).add(tb.getX());
                }
            }
            if (rows.isEmpty()) {
                continue;
            }
            Nation nation = town.getNationOrNull();
            Color color = nation == null ? new Color(0x9E9E9E) : colorOf(nation.getName());
            String tip = "<b>" + esc(town.getName()) + "</b>"
                    + "<br>Quốc gia: " + (nation == null ? "<i>tự do</i>" : esc(nation.getName()))
                    + "<br>Thị trưởng: " + (town.getMayor() == null ? "?" : esc(town.getMayor().getName()))
                    + "<br>Dân: " + town.getResidents().size()
                    + "<br>Ô đất: " + town.getTownBlocks().size();
            MarkerOptions opt = MarkerOptions.builder()
                    .fillColor(color).fillOpacity(0.35)
                    .strokeColor(color).strokeOpacity(0.9).strokeWeight(2)
                    .hoverTooltip(tip).build();
            int i = 0;
            for (int[] r : rects(rows)) {
                // r = {x1, z1, x2, z2} theo ô đất; Point theo block, góc dưới-phải là mép ngoài của ô cuối
                Marker m = Marker.rectangle(Point.of(r[0] * size, r[1] * size), Point.of((r[2] + 1) * size, (r[3] + 1) * size))
                        .markerOptions(opt);
                provider.addMarker(Key.of("town_" + town.getUUID() + "_" + i++), m);
            }
        }
    }

    /** Gộp ô đất: mỗi hàng z thành các đoạn x liền nhau, rồi nối đoạn giống hệt ở các hàng liền nhau. */
    static List<int[]> rects(Map<Integer, TreeSet<Integer>> rows) {
        List<int[]> out = new ArrayList<>();
        Map<Long, int[]> open = new HashMap<>();
        Integer prevZ = null;
        for (Map.Entry<Integer, TreeSet<Integer>> e : rows.entrySet()) {
            int z = e.getKey();
            Set<Long> runs = new TreeSet<>();
            Integer start = null;
            Integer last = null;
            for (int x : e.getValue()) {
                if (start == null) {
                    start = x;
                } else if (x != last + 1) {
                    runs.add(pack(start, last));
                    start = x;
                }
                last = x;
            }
            runs.add(pack(start, last));
            boolean contiguous = prevZ != null && prevZ == z - 1;
            Map<Long, int[]> next = new HashMap<>();
            for (Map.Entry<Long, int[]> o : open.entrySet()) {
                if (contiguous && runs.contains(o.getKey())) {
                    o.getValue()[3] = z;
                    next.put(o.getKey(), o.getValue());
                } else {
                    out.add(o.getValue());
                }
            }
            for (long k : runs) {
                if (!next.containsKey(k)) {
                    next.put(k, new int[] {(int) (k >> 32), z, (int) k, z});
                }
            }
            open = next;
            prevZ = z;
        }
        out.addAll(open.values());
        return out;
    }

    private static long pack(int a, int b) {
        return ((long) a << 32) | (b & 0xffffffffL);
    }

    private static Color colorOf(String name) {
        float hue = (name.hashCode() & 0xffff) / 65535f;
        return Color.getHSBColor(hue, 0.75f, 0.9f);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
