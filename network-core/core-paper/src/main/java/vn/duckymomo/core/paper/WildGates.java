package vn.duckymomo.core.paper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.event.PreNewTownEvent;
import com.palmergames.bukkit.towny.event.TownPreClaimEvent;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

/**
 * Cổng Hoang (/conghoang, Bang Hội Chiến). Config wild-gates:
 *  - Mỗi interval-days ngày server đặt 1 cổng (tối đa max-gates cổng/mùa) ở đất hoang của một vùng /tpr đã mở, cách thị trấn gần nhất
 *    >= min-town-distance khối, cách cổng khác >= min-gate-distance. Báo cả server "tin đồn" (chỉ tên vùng + hướng, không toạ độ).
 *  - Cổng ngụy trang theo biome (vòng đá rêu, giếng sa thạch, cột băng, gốc cây rêu, rễ đước), không hiện trên bản đồ web.
 *    Dấu hiệu: hạt tím trong particle-range khối; đứng gần thì thanh hành động nhắc "luồng khí lạ". Đứng lên ô giữa + Shift để vào.
 *  - Người tìm ra đầu tiên được grace-minutes phút ưu tiên: người khác không lập/mở rộng thị trấn trong grace-radius khối quanh cổng.
 *  - Thị trấn có ô đất chứa cổng là chủ cổng: dân thị trấn, dân cùng nước, dân nước đang chiếm (SiegeWar) vào miễn phí; người ngoài trả
 *    phí do người quản lý đặt (/conghoang phi), tiền vào ngân khố thị trấn, hoặc bị chặn (/conghoang dong). Người quản lý: vua nước chiếm
 *    nếu thị trấn bị chiếm, không thì thị trưởng. Chiếm được thị trấn là sở hữu cổng.
 *  - Cổng dẫn vào một dungeon trong danh sách dungeons (qua Cổng Dungeon, bỏ qua điều kiện của dungeon đó).
 *  - upkeep-per-day (xu/ngày từ ngân khố thị trấn giữ cổng) và revolt-hours-less (bớt giờ chờ khởi nghĩa khi thị trấn giữ cổng bị chiếm): mặc định 0.
 * Lưu: conghoang.yml (gồm khối đã đặt để gỡ lại); ghi conghoang.json cho web (không có toạ độ cổng chưa ai tìm ra).
 */
final class WildGates implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private record Zone(String name, String requires, int x, int z, int r) { }

    private final DuckyMoMoCore plugin;
    private final String world;
    private final int maxGates;
    private final long intervalMs;
    private final int minTownDist;
    private final int minGateDist;
    private final long graceMs;
    private final int graceRadius;
    private final int particleRange;
    private final double maxToll;
    private final double upkeep;
    private final double revoltLess;
    private final List<String> pool;
    private final List<Zone> zones = new ArrayList<>();
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private Method getOccupier;

    WildGates(DuckyMoMoCore plugin, ConfigurationSection c) {
        this.plugin = plugin;
        this.world = plugin.getConfig().getString("rtp.world", "world");
        this.maxGates = Math.max(0, c.getInt("max-gates", 6));
        this.intervalMs = Math.max(1, c.getLong("interval-days", 7)) * 86_400_000L;
        this.minTownDist = Math.max(0, c.getInt("min-town-distance", 300));
        this.minGateDist = Math.max(0, c.getInt("min-gate-distance", 800));
        this.graceMs = Math.max(0, c.getLong("grace-minutes", 30)) * 60_000L;
        this.graceRadius = Math.max(16, c.getInt("grace-radius", 64));
        this.particleRange = Math.max(4, c.getInt("particle-range", 16));
        this.maxToll = Math.max(0, c.getDouble("max-toll", 10000));
        this.upkeep = Math.max(0, c.getDouble("upkeep-per-day", 0));
        this.revoltLess = Math.max(0, c.getDouble("revolt-hours-less", 0));
        this.pool = c.getStringList("dungeons");
        for (Map<?, ?> z : plugin.getConfig().getMapList("rtp.zones")) {
            if (z.get("radius") instanceof Number r && r.intValue() > 0) {
                zones.add(new Zone(String.valueOf(z.get("name")), z.get("requires") == null ? "" : String.valueOf(z.get("requires")),
                        ((Number) z.get("center-x")).intValue(), ((Number) z.get("center-z")).intValue(), r.intValue()));
            }
        }
        this.file = new File(plugin.getDataFolder(), "conghoang.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    boolean ready() {
        return !zones.isEmpty() && !pool.isEmpty();
    }

    String describe() {
        return gateIds().size() + "/" + maxGates + " cổng, " + intervalMs / 86_400_000L + " ngày thêm 1, dẫn tới " + pool.size() + " dungeon";
    }

    void start() {
        Shared.wildGates = this;
        if (Bukkit.getPluginManager().isPluginEnabled("SiegeWar")) {
            try {
                getOccupier = Class.forName("com.gmail.goosius.siegewar.TownOccupationController", true,
                        Bukkit.getPluginManager().getPlugin("SiegeWar").getClass().getClassLoader()).getMethod("getTownOccupier", Town.class);
            } catch (ReflectiveOperationException e) {
                plugin.getLogger().warning("Cổng Hoang: không đọc được nước chiếm (SiegeWar): " + e);
            }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 200L, 40L);
        writeJson();
    }

    void stop() {
        Shared.wildGates = null;
        save();
    }

    // ---------------------------------------------------------------- dữ liệu cổng

    private List<String> gateIds() {
        ConfigurationSection s = data.getConfigurationSection("cong");
        return s == null ? List.of() : new ArrayList<>(s.getKeys(false));
    }

    private String g(String id) {
        return "cong." + id + ".";
    }

    private Location center(String id) {
        World w = Bukkit.getWorld(data.getString(g(id) + "world", world));
        return w == null ? null : new Location(w, data.getInt(g(id) + "x") + 0.5, data.getInt(g(id) + "y"), data.getInt(g(id) + "z") + 0.5);
    }

    private boolean found(String id) {
        return data.contains(g(id) + "tim-boi");
    }

    private String dungeonName(String id) {
        DungeonGate dg = Shared.dungeonGate;
        String d = data.getString(g(id) + "dungeon", "");
        return dg == null ? d : dg.displayName(d);
    }

    /** Thị trấn có ô đất chứa cổng (null = đất hoang). */
    private Town owner(String id) {
        Location l = center(id);
        if (l == null || !Bukkit.getPluginManager().isPluginEnabled("Towny")) {
            return null;
        }
        TownBlock tb = TownyAPI.getInstance().getTownBlock(l);
        return tb == null ? null : tb.getTownOrNull();
    }

    private Nation occupier(Town t) {
        if (t == null || getOccupier == null) {
            return null;
        }
        try {
            return (Nation) getOccupier.invoke(null, t);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Thị trấn đang giữ ít nhất một cổng (cho WarBalance bớt giờ khởi nghĩa). */
    double revoltLess(Town t) {
        if (revoltLess <= 0 || t == null) {
            return 0;
        }
        return gateIds().stream().anyMatch(id -> t.equals(owner(id))) ? revoltLess : 0;
    }

    private boolean isManager(Player p, Town t) {
        if (t == null) {
            return false;
        }
        Resident r = TownyAPI.getInstance().getResident(p);
        if (r == null) {
            return false;
        }
        Nation occ = occupier(t);
        if (occ != null) {
            return occ.getKing() != null && occ.getKing().equals(r);
        }
        return t.getMayor() != null && t.getMayor().equals(r);
    }

    private boolean freeFor(Player p, Town t) {
        Resident r = TownyAPI.getInstance().getResident(p);
        if (r == null) {
            return false;
        }
        Town mine = r.getTownOrNull();
        if (t.equals(mine)) {
            return true;
        }
        Nation n = r.getNationOrNull();
        if (n == null) {
            return false;
        }
        return n.equals(t.getNationOrNull()) || n.equals(occupier(t));
    }

    // ---------------------------------------------------------------- xuất hiện

    private Zone zoneAt(double x, double z) {
        Zone best = null;
        double bd = Double.MAX_VALUE;
        for (Zone zn : zones) {
            double d = Math.hypot(zn.x() - x, zn.z() - z);
            if (d < bd) {
                bd = d;
                best = zn;
            }
        }
        return best;
    }

    private static String direction(Zone z, double x, double zz) {
        double dx = x - z.x(), dz = zz - z.z();
        if (Math.hypot(dx, dz) < z.r() * 0.25) {
            return "gần trung tâm";
        }
        String ns = dz < -Math.abs(dx) * 0.4 ? "bắc" : dz > Math.abs(dx) * 0.4 ? "nam" : "";
        String ew = dx > Math.abs(dz) * 0.4 ? "đông" : dx < -Math.abs(dz) * 0.4 ? "tây" : "";
        return "phía " + (ns + (ns.isEmpty() || ew.isEmpty() ? "" : " ") + ew).trim();
    }

    private Location pickSpot() {
        World w = Bukkit.getWorld(world);
        List<Zone> open = zones.stream().filter(z -> Shared.unlocked(z.requires())).toList();
        if (w == null || open.isEmpty()) {
            return null;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        outer:
        for (int i = 0; i < 40; i++) {
            Zone z = open.get(r.nextInt(open.size()));
            double a = r.nextDouble(Math.PI * 2), d = Math.sqrt(r.nextDouble()) * z.r() * 0.85;
            int x = z.x() + (int) (Math.cos(a) * d), zz = z.z() + (int) (Math.sin(a) * d);
            Block top = w.getHighestBlockAt(x, zz);
            if (top.isLiquid() || top.getType() == Material.ICE || top.getY() < w.getSeaLevel() - 2 || !top.getType().isSolid()) {
                continue;
            }
            Location l = top.getLocation();
            for (Town t : TownyAPI.getInstance().getTowns()) {
                Location h = t.getSpawnOrNull();
                if (h != null && h.getWorld() == w && h.distance(l) < minTownDist) {
                    continue outer;
                }
            }
            if (!TownyAPI.getInstance().isWilderness(l)) {
                continue;
            }
            for (String id : gateIds()) {
                Location c = center(id);
                if (c != null && c.getWorld() == w && c.distance(l) < minGateDist) {
                    continue outer;
                }
            }
            return l;
        }
        return null;
    }

    /** Đặt một cổng ở l (khối mặt đất), trả mã cổng. */
    private String place(Location ground) {
        int n = data.getInt("dem", 0) + 1;
        String id = "c" + n;
        data.set("dem", n);
        String dungeon = pickDungeon();
        String style = style(ground.getBlock());
        List<String> placed = new ArrayList<>();
        build(ground, style, placed);
        String p = g(id);
        data.set(p + "world", ground.getWorld().getName());
        data.set(p + "x", ground.getBlockX());
        data.set(p + "y", ground.getBlockY() + 1);
        data.set(p + "z", ground.getBlockZ());
        data.set(p + "kieu", style);
        data.set(p + "dungeon", dungeon);
        data.set(p + "luc", System.currentTimeMillis());
        Zone z = zoneAt(ground.getX(), ground.getZ());
        data.set(p + "vung", z == null ? "?" : z.name());
        data.set(p + "huong", z == null ? "" : direction(z, ground.getX(), ground.getZ()));
        data.set(p + "khoi", placed);
        data.set("lan-cuoi", System.currentTimeMillis());
        save();
        writeJson();
        return id;
    }

    private String pickDungeon() {
        DungeonGate dg = Shared.dungeonGate;
        List<String> ok = dg == null ? pool : pool.stream().filter(dg::isInstalled).toList();
        List<String> from = ok.isEmpty() ? pool : ok;
        return from.get(ThreadLocalRandom.current().nextInt(from.size()));
    }

    private static String style(Block ground) {
        String b = ground.getBiome().getKey().getKey();
        if (b.contains("desert") || b.contains("badlands") || b.contains("savanna")) {
            return "giengcat";
        }
        if (b.contains("snow") || b.contains("frozen") || b.contains("ice") || b.contains("peaks") || b.contains("grove")) {
            return "cotbang";
        }
        if (b.contains("swamp") || b.contains("mangrove")) {
            return "reduoc";
        }
        if (b.contains("forest") || b.contains("jungle") || b.contains("taiga") || b.contains("birch")) {
            return "gocrieu";
        }
        return "vongda";
    }

    private static void set(Block b, Material m, List<String> placed) {
        placed.add(b.getX() + "," + b.getY() + "," + b.getZ() + "," + b.getBlockData().getAsString());
        b.setType(m, false);
    }

    /** Dựng cổng nhỏ, trông như tự nhiên. Ô giữa (mặt đất) là chỗ đứng + Shift. */
    private static void build(Location ground, String style, List<String> placed) {
        Block c = ground.getBlock();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int[][] ring = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, -2}, {2, -2}, {-2, 2}};
        switch (style) {
            case "giengcat" -> {
                set(c, Material.CHISELED_SANDSTONE, placed);
                for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}}) {
                    if (r.nextInt(4) != 0) {
                        set(c.getRelative(o[0], 1, o[1]), r.nextBoolean() ? Material.SANDSTONE_SLAB : Material.CUT_SANDSTONE_SLAB, placed);
                    }
                }
                set(c.getRelative(2, 1, 0), Material.SANDSTONE_WALL, placed);
                set(c.getRelative(-2, 1, 0), Material.SANDSTONE_WALL, placed);
            }
            case "cotbang" -> {
                set(c, Material.PACKED_ICE, placed);
                for (int i = 0; i < 4; i++) {
                    int[] o = ring[r.nextInt(ring.length)];
                    int h = 1 + r.nextInt(3);
                    for (int y = 1; y <= h; y++) {
                        set(c.getRelative(o[0], y, o[1]), y == h && r.nextBoolean() ? Material.BLUE_ICE : Material.PACKED_ICE, placed);
                    }
                }
            }
            case "reduoc" -> {
                set(c, Material.MUDDY_MANGROVE_ROOTS, placed);
                for (int i = 0; i < 4; i++) {
                    int[] o = ring[r.nextInt(ring.length)];
                    for (int y = 1; y <= 1 + r.nextInt(2); y++) {
                        set(c.getRelative(o[0], y, o[1]), Material.MANGROVE_ROOTS, placed);
                    }
                }
                set(c.getRelative(1, 1, 1), Material.MOSS_CARPET, placed);
            }
            case "gocrieu" -> {
                set(c, Material.MOSS_BLOCK, placed);
                for (int i = 0; i < 3; i++) {
                    int[] o = ring[r.nextInt(ring.length)];
                    int h = 1 + r.nextInt(2);
                    for (int y = 1; y <= h; y++) {
                        set(c.getRelative(o[0], y, o[1]), Material.OAK_LOG, placed);
                    }
                    set(c.getRelative(o[0], h + 1, o[1]), Material.MOSS_CARPET, placed);
                }
                for (int x = -1; x <= 1; x++) {
                    set(c.getRelative(x, 1, 2), Material.STRIPPED_OAK_LOG, placed);
                }
            }
            default -> {
                set(c, Material.CHISELED_STONE_BRICKS, placed);
                for (int[] o : ring) {
                    if (r.nextInt(5) != 0) {
                        int h = 1 + r.nextInt(2);
                        for (int y = 1; y <= h; y++) {
                            set(c.getRelative(o[0], y, o[1]), r.nextBoolean() ? Material.MOSSY_COBBLESTONE : Material.MOSSY_STONE_BRICKS, placed);
                        }
                    }
                }
            }
        }
    }

    private void unbuild(String id) {
        World w = Bukkit.getWorld(data.getString(g(id) + "world", world));
        if (w == null) {
            return;
        }
        List<String> l = new ArrayList<>(data.getStringList(g(id) + "khoi"));
        java.util.Collections.reverse(l);
        for (String s : l) {
            String[] a = s.split(",", 4);
            try {
                w.getBlockAt(Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2])).setBlockData(Bukkit.createBlockData(a[3]), false);
            } catch (IllegalArgumentException ignored) {
                // khối lạ: bỏ qua
            }
        }
    }

    private String spawnNow() {
        Location l = pickSpot();
        if (l == null) {
            return null;
        }
        String id = place(l);
        Bukkit.broadcast(c("&5&l🌀 TIN ĐỒN: &dCó luồng khí lạ ở vùng &f" + data.getString(g(id) + "vung") + "&d, "
                + data.getString(g(id) + "huong") + ". &7Ai đó nói thấy một nơi trông rất cũ kỹ… (Cổng Hoang: /conghoang)"));
        return id;
    }

    // ---------------------------------------------------------------- vòng lặp

    private void tick() {
        long now = System.currentTimeMillis();
        int count = data.getInt("dem", 0);
        boolean seasonOpen = !plugin.getConfig().getString("leaderboard.season-start", "").isBlank();   // chưa mở mùa: không tự đặt cổng
        if (seasonOpen && count < maxGates && now - data.getLong("lan-cuoi", 0) >= intervalMs) {
            if (spawnNow() == null) {
                data.set("lan-cuoi", now - intervalMs + 3_600_000L);   // không tìm được chỗ: thử lại sau 1 giờ
            }
        }
        // phí duy trì mỗi ngày
        String today = LocalDate.now(VN).toString();
        if (upkeep > 0 && !today.equals(data.getString("ngay-phi"))) {
            data.set("ngay-phi", today);
            for (String id : gateIds()) {
                Town t = owner(id);
                if (t != null && !t.getAccount().withdraw(upkeep, "Phí duy trì Cổng Hoang")) {
                    plugin.getLogger().info("Cổng Hoang " + id + ": " + t.getName() + " không đủ tiền phí duy trì.");
                }
            }
            save();
        }
        // dấu hiệu
        for (String id : gateIds()) {
            Location c = center(id);
            if (c == null || !c.isChunkLoaded()) {
                continue;
            }
            for (Player p : c.getWorld().getPlayers()) {
                double d = p.getLocation().distanceSquared(c);
                if (d < particleRange * particleRange) {
                    p.spawnParticle(Particle.PORTAL, c.clone().add(0, 0.6, 0), 12, 0.3, 0.4, 0.3, 0.3);
                }
                if (d < 36) {
                    p.sendActionBar(c(d < 3 ? "&5🌀 Luồng khí lạ ngay dưới chân… &dngồi xuống (Shift) thử xem" : "&5🌀 Bạn cảm thấy một luồng khí lạ quanh đây…"));
                }
            }
        }
    }

    // ---------------------------------------------------------------- vào cổng

    private String gateAt(Location l) {
        for (String id : gateIds()) {
            Location c = center(id);
            if (c != null && c.getWorld() == l.getWorld() && Math.abs(c.getY() - l.getY()) <= 2.5
                    && Math.hypot(c.getX() - l.getX(), c.getZ() - l.getZ()) <= 1.6) {
                return id;
            }
        }
        return null;
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) {
            return;
        }
        Player p = e.getPlayer();
        String id = gateAt(p.getLocation());
        if (id == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (cooldown.getOrDefault(p.getUniqueId(), 0L) > now) {
            return;
        }
        cooldown.put(p.getUniqueId(), now + 3000);
        use(p, id, false);
    }

    private void use(Player p, String id, boolean paid) {
        String pfx = g(id);
        long now = System.currentTimeMillis();
        if (!found(id)) {
            data.set(pfx + "tim-boi", p.getName());
            data.set(pfx + "tim-uuid", p.getUniqueId().toString());
            data.set(pfx + "tim-luc", now);
            data.set(pfx + "uu-tien", now + graceMs);
            save();
            writeJson();
            p.playSound(p.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.8f, 1.1f);
            p.sendMessage(c("&5&l🌀 Bạn đã tìm ra một Cổng Hoang! &7Dẫn vào: &d" + dungeonName(id)));
            if (graceMs > 0) {
                p.sendMessage(c("&7Trong &f" + graceMs / 60_000 + " phút &7tới, chỉ bạn (hoặc thị trấn của bạn) được lập/mở rộng thị trấn trong "
                        + graceRadius + " khối quanh đây. Thị trấn giữ cổng: dân mình vào miễn phí, người ngoài trả phí vào ngân khố."));
            }
            Bukkit.broadcast(c("&5🌀 &f" + p.getName() + " &dđã tìm ra một Cổng Hoang ở vùng &f" + data.getString(pfx + "vung") + "&d!"));
            Achievements.add(p, "conghoang", 1);
        }
        Town t = owner(id);
        if (t == null) {
            if (data.getLong(pfx + "uu-tien", 0) > now && !p.getUniqueId().toString().equals(data.getString(pfx + "tim-uuid"))
                    && !sameTownAsFinder(p, id)) {
                p.sendMessage(c("&7Cổng vừa được &f" + data.getString(pfx + "tim-boi") + " &7tìm ra, đang trong thời gian ưu tiên. Quay lại sau."));
                return;
            }
        } else if (!freeFor(p, t) && !isManager(p, t)) {
            if (data.getBoolean(pfx + "dong", false)) {
                p.sendMessage(c("&cCổng thuộc thị trấn &f" + t.getName() + " &cđang đóng với người ngoài."));
                return;
            }
            double toll = data.getDouble(pfx + "phi", 0);
            if (toll > 0 && !paid) {
                p.sendMessage(c("&7Cổng thuộc thị trấn &f" + t.getName() + "&7. Phí qua cổng: &e" + money(toll) + "&7.")
                        .append(c(" &a[Trả và vào]").clickEvent(ClickEvent.runCommand("/conghoang vao " + id))
                                .hoverEvent(HoverEvent.showText(c("&7Trả " + money(toll) + " vào ngân khố " + t.getName())))));
                return;
            }
            if (toll > 0) {
                if (!VaultEconomy.available() || !VaultEconomy.withdraw(p, toll)) {
                    p.sendMessage(c("&cKhông đủ tiền trả phí qua cổng."));
                    return;
                }
                t.getAccount().deposit(toll, "Phí Cổng Hoang: " + p.getName());
                data.set(pfx + "thu", data.getDouble(pfx + "thu", 0) + toll);
            }
        }
        DungeonGate dg = Shared.dungeonGate;
        String dungeon = data.getString(pfx + "dungeon", "");
        if (dg == null || !dg.passThrough(p, dungeon)) {
            if (paid && t != null && data.getDouble(pfx + "phi", 0) > 0) {
                VaultEconomy.deposit(p, data.getDouble(pfx + "phi", 0));   // hoàn phí
                t.getAccount().withdraw(data.getDouble(pfx + "phi", 0), "Hoàn phí Cổng Hoang");
            }
            return;
        }
        data.set(pfx + "luot", data.getInt(pfx + "luot", 0) + 1);
        save();
    }

    private boolean sameTownAsFinder(Player p, String id) {
        String u = data.getString(g(id) + "tim-uuid");
        if (u == null) {
            return false;
        }
        Resident a = TownyAPI.getInstance().getResident(p), b = TownyAPI.getInstance().getResident(UUID.fromString(u));
        return a != null && b != null && a.getTownOrNull() != null && a.getTownOrNull().equals(b.getTownOrNull());
    }

    // ---------------------------------------------------------------- ưu tiên + bảo vệ

    private String graceGateNear(Location l) {
        long now = System.currentTimeMillis();
        for (String id : gateIds()) {
            Location c = center(id);
            if (data.getLong(g(id) + "uu-tien", 0) > now && c != null && c.getWorld() == l.getWorld()
                    && Math.hypot(c.getX() - l.getX(), c.getZ() - l.getZ()) <= graceRadius) {
                return id;
            }
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClaim(TownPreClaimEvent e) {
        Location l = e.getTownBlock().getWorldCoord().getLowerMostCornerLocation().add(8, 0, 8);
        String id = graceGateNear(l);
        if (id == null) {
            return;
        }
        String u = data.getString(g(id) + "tim-uuid");
        Resident finder = u == null ? null : TownyAPI.getInstance().getResident(UUID.fromString(u));
        if (finder == null || !e.getTown().equals(finder.getTownOrNull())) {
            e.setCancelled(true);
            e.setCancelMessage("Gần đây có Cổng Hoang vừa được " + data.getString(g(id) + "tim-boi") + " tìm ra: đang trong thời gian ưu tiên, chưa mở rộng tới được.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onNewTown(PreNewTownEvent e) {
        String id = graceGateNear(e.getTownLocation());
        if (id != null && !e.getPlayer().getUniqueId().toString().equals(data.getString(g(id) + "tim-uuid"))) {
            e.setCancelled(true);
            e.setCancelMessage("Gần đây có Cổng Hoang vừa được " + data.getString(g(id) + "tim-boi") + " tìm ra: đang trong thời gian ưu tiên.");
        }
    }

    private boolean isGateBlock(Block b) {
        String key = b.getX() + "," + b.getY() + "," + b.getZ() + ",";
        for (String id : gateIds()) {
            if (b.getWorld().getName().equals(data.getString(g(id) + "world", world))
                    && data.getStringList(g(id) + "khoi").stream().anyMatch(s -> s.startsWith(key))) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (isGateBlock(e.getBlock()) && !e.getPlayer().hasPermission("duckymomo.admin")) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(c("&5Khối này không phá được."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(this::isGateBlock);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(this::isGateBlock);
    }

    /** Kho báu huyền thoại: chỉ đường tới một Cổng Hoang chưa ai tìm ra (true nếu có). */
    boolean hint(Player p) {
        List<String> l = gateIds().stream().filter(id -> !found(id)).toList();
        if (l.isEmpty()) {
            return false;
        }
        String id = l.get(ThreadLocalRandom.current().nextInt(l.size()));
        Location s = center(id);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        p.playSound(p.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 0.6f);
        p.sendMessage(c("&5&l🗺 Dưới đáy rương có mảnh giấy cũ: &d\"Cổng Hoang ở quanh X " + (s.getBlockX() + r.nextInt(-40, 41)) + " Z "
                + (s.getBlockZ() + r.nextInt(-40, 41)) + "\"&5. &7(trong khoảng 40 khối, tìm hạt tím)"));
        return true;
    }

    // ---------------------------------------------------------------- lệnh

    boolean command(CommandSender s, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        String id = args.length > 1 ? args[1] : "";
        boolean admin = s.hasPermission("duckymomo.admin");
        switch (sub) {
            case "vao" -> {
                if (s instanceof Player p && data.contains("cong." + id) && id.equals(gateAt(p.getLocation()))) {
                    use(p, id, true);
                } else {
                    s.sendMessage(c("&7Phải đứng tại Cổng Hoang mới vào được."));
                }
                return true;
            }
            case "phi", "dong", "mo" -> {
                if (!(s instanceof Player p) || !data.contains("cong." + id)) {
                    s.sendMessage(c("&e/conghoang phi <mã> <xu> &7| &e/conghoang dong|mo <mã> &7(mã xem trong /conghoang)"));
                    return true;
                }
                Town t = owner(id);
                if (!isManager(p, t) && !admin) {
                    p.sendMessage(c("&cChỉ thị trưởng thị trấn giữ cổng (hoặc vua nước đang chiếm thị trấn đó) mới chỉnh được."));
                    return true;
                }
                if (sub.equals("phi")) {
                    double v;
                    try {
                        v = Math.max(0, Math.min(maxToll, Double.parseDouble(args.length > 2 ? args[2] : "-1")));
                    } catch (NumberFormatException ex) {
                        v = -1;
                    }
                    if (args.length < 3 || v < 0) {
                        p.sendMessage(c("&e/conghoang phi " + id + " <xu> &7(0 = miễn phí, tối đa " + money(maxToll) + ")"));
                        return true;
                    }
                    data.set(g(id) + "phi", v);
                    p.sendMessage(c("&a✔ Phí qua cổng " + id + ": &f" + (v == 0 ? "miễn phí" : money(v))));
                } else {
                    data.set(g(id) + "dong", sub.equals("dong"));
                    p.sendMessage(c(sub.equals("dong") ? "&a✔ Đã đóng cổng " + id + " với người ngoài." : "&a✔ Đã mở cổng " + id + " cho người ngoài (trả phí nếu có)."));
                }
                save();
                writeJson();
                return true;
            }
            case "tao" -> {
                if (!admin) {
                    break;
                }
                if (s instanceof Player p && args.length > 1 && args[1].equalsIgnoreCase("here")) {
                    String nid = place(p.getLocation().getBlock().getRelative(0, -1, 0).getLocation());
                    s.sendMessage("Đã đặt cổng " + nid + " tại chỗ đứng, dẫn tới " + data.getString(g(nid) + "dungeon"));
                } else {
                    String nid = spawnNow();
                    s.sendMessage(nid == null ? "Không tìm được chỗ (vùng /tpr đã mở, cách thị trấn " + minTownDist + " khối)." : "Đã đặt cổng " + nid);
                }
                return true;
            }
            case "xoa" -> {
                if (!admin) {
                    break;
                }
                if (!data.contains("cong." + id)) {
                    s.sendMessage("/conghoang xoa <mã>");
                    return true;
                }
                unbuild(id);
                data.set("cong." + id, null);
                save();
                writeJson();
                s.sendMessage("Đã gỡ cổng " + id + " (vẫn tính vào số cổng của mùa; /conghoang datlai để đếm lại từ 0).");
                return true;
            }
            case "datlai" -> {
                if (!admin) {
                    break;
                }
                gateIds().forEach(this::unbuild);
                data.set("cong", null);
                data.set("dem", 0);
                data.set("lan-cuoi", System.currentTimeMillis());
                save();
                writeJson();
                s.sendMessage("Đã gỡ mọi Cổng Hoang và đếm lại từ 0 (cổng đầu tiên sau " + intervalMs / 86_400_000L + " ngày, hoặc /conghoang tao).");
                return true;
            }
            case "ds" -> {
                if (!admin) {
                    break;
                }
                for (String gid : gateIds()) {
                    Location c = center(gid);
                    Town t = owner(gid);
                    s.sendMessage(gid + ": " + (c == null ? "?" : c.getBlockX() + " " + c.getBlockY() + " " + c.getBlockZ()) + " · " + data.getString(g(gid) + "kieu")
                            + " · " + data.getString(g(gid) + "dungeon") + " · " + (found(gid) ? "tìm bởi " + data.getString(g(gid) + "tim-boi") : "chưa ai tìm")
                            + (t == null ? "" : " · chủ " + t.getName()));
                }
                return true;
            }
            default -> { }
        }
        // danh sách công khai
        s.sendMessage(c("&5&l━━ 🌀 CỔNG HOANG ━━"));
        s.sendMessage(c("&7Cổng dungeon ẩn trên bản đồ, ngụy trang như cảnh vật (vòng đá rêu, giếng cát, cột băng, gốc cây rêu…). Gần cổng có hạt tím,"
                + " đứng lên ô giữa rồi &fngồi xuống (Shift) &7để vào. Mỗi " + intervalMs / 86_400_000L + " ngày thêm 1 cổng, tối đa " + maxGates + " cổng/mùa."));
        s.sendMessage(c("&7Thị trấn có ô đất chứa cổng là chủ: dân mình vào miễn phí, người ngoài trả phí vào ngân khố. Chiếm được thị trấn là có cổng."));
        for (String gid : gateIds()) {
            Town t = owner(gid);
            String state = !found(gid) ? "&8chưa ai tìm ra · tin đồn: vùng " + data.getString(g(gid) + "vung") + ", " + data.getString(g(gid) + "huong")
                    : "&7tìm bởi &f" + data.getString(g(gid) + "tim-boi") + " &7· " + (t == null ? "&ađất hoang, vào tự do"
                    : "&fthị trấn " + t.getName() + (data.getBoolean(g(gid) + "dong") ? " &c(đóng)" : " &7· phí " + money(data.getDouble(g(gid) + "phi", 0))));
            s.sendMessage(c("  &d" + gid + " &8— &7" + (found(gid) ? dungeonName(gid) + " &8· " : "") + state));
        }
        if (gateIds().isEmpty()) {
            s.sendMessage(c("  &8Chưa có cổng nào xuất hiện."));
        }
        s.sendMessage(c("&8Chủ cổng: /conghoang phi <mã> <xu> · /conghoang dong|mo <mã>"));
        return true;
    }

    private static String money(double v) {
        return VaultEconomy.available() ? VaultEconomy.format(v) : String.format(Locale.ROOT, "%,.0f xu", v);
    }

    // ---------------------------------------------------------------- lưu + web

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được conghoang.yml: " + e.getMessage());
        }
    }

    private void writeJson() {
        JsonObject root = new JsonObject();
        root.addProperty("toi_da", maxGates);
        root.addProperty("da_xuat_hien", data.getInt("dem", 0));
        root.addProperty("so_ngay", intervalMs / 86_400_000L);
        JsonArray arr = new JsonArray();
        for (String id : gateIds()) {
            JsonObject o = new JsonObject();
            o.addProperty("ma", id);
            o.addProperty("vung", data.getString(g(id) + "vung", "?"));
            o.addProperty("tim", found(id));
            if (found(id)) {
                o.addProperty("tim_boi", data.getString(g(id) + "tim-boi"));
                o.addProperty("dungeon", dungeonName(id));
                Town t = Bukkit.getPluginManager().isPluginEnabled("Towny") ? owner(id) : null;
                o.addProperty("chu", t == null ? "" : t.getName());
                o.addProperty("phi", data.getDouble(g(id) + "phi", 0));
                o.addProperty("dong", data.getBoolean(g(id) + "dong", false));
                o.addProperty("luot", data.getInt(g(id) + "luot", 0));
            } else {
                o.addProperty("huong", data.getString(g(id) + "huong", ""));
            }
            arr.add(o);
        }
        root.add("cong", arr);
        try {
            Files.writeString(new File(plugin.getDataFolder(), "conghoang.json").toPath(), root.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Không ghi được conghoang.json: " + e.getMessage());
        }
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
}
