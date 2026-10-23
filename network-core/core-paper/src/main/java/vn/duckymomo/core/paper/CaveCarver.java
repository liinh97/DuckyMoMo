package vn.duckymomo.core.paper;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

/**
 * Đục hang tự nhiên vào map Trái Đất (23/10, chủ server: "bổ sung cave tự nhiên với số lượng như bình thường").
 * Map dựng bằng WorldPainter không có hang (đo: 0–1,5% khoảng trống dưới đất, map thường 5–10%). Cách làm: thế giới NGUỒN
 * "hang_nguon" do Minecraft tạo bình thường (không công trình); mỗi chunk trong vùng mùa 1 (khung decorate.min/max-x/z), chỗ nào
 * thế giới nguồn là không khí (hang, hang khổng lồ, khe nứt) thì đục chỗ đó ở map thật, cùng toạ độ. Dưới y -54 nguồn là dung nham
 * thì đổ dung nham (như hang thường).
 * Chỉ đục khối TỰ NHIÊN (NATURAL); chừa vỏ: mặt đất trên cạn 6 khối, dưới nước 10 khối; không đục ô cạnh nước / dung nham (khỏi ngập);
 * bỏ khu spawn (decorate.exclude) và chunk đã có thị trấn (Towny). Tiến độ: hang-tiendo.yml (dừng rồi chạy tiếp được).
 * Lệnh (admin): /dmm hang thu <x> <z> [bán kính chunk] | chay | dung | xem.
 */
final class CaveCarver {

    private static final String SOURCE = "hang_nguon";
    private static final int MIN_Y = -58;           // trên lớp đá gốc
    private static final int LAVA_Y = -54;          // dưới mức này nguồn là dung nham thì đổ dung nham
    private static final int SHELL_LAND = 6, SHELL_WATER = 10;
    private static final long BUDGET_NS = 25_000_000L;   // tối đa ~25 ms mỗi tick cho việc đục
    private static final Set<Material> NATURAL = EnumSet.noneOf(Material.class);
    private static final Set<Material> GROUND = EnumSet.noneOf(Material.class);
    /** Đồ trang trí trong hang của thế giới nguồn (thạch nhũ, hang xanh, hốc thạch anh tím…): chép kèm cho hang tự nhiên, giữ hướng / dạng. */
    private static final Set<Material> DECOR = EnumSet.of(Material.POINTED_DRIPSTONE, Material.DRIPSTONE_BLOCK, Material.MOSS_BLOCK, Material.MOSS_CARPET,
            Material.GLOW_LICHEN, Material.CAVE_VINES, Material.CAVE_VINES_PLANT, Material.AZALEA, Material.FLOWERING_AZALEA, Material.SPORE_BLOSSOM,
            Material.BIG_DRIPLEAF, Material.BIG_DRIPLEAF_STEM, Material.SMALL_DRIPLEAF, Material.HANGING_ROOTS, Material.ROOTED_DIRT, Material.CLAY,
            Material.AMETHYST_BLOCK, Material.BUDDING_AMETHYST, Material.AMETHYST_CLUSTER, Material.LARGE_AMETHYST_BUD, Material.MEDIUM_AMETHYST_BUD,
            Material.SMALL_AMETHYST_BUD, Material.CALCITE, Material.SMOOTH_BASALT, Material.SCULK, Material.SCULK_VEIN);

    static {
        for (Material m : Material.values()) {
            String n = m.name();
            if (!m.isBlock() || m.isLegacy()) {
                continue;
            }
            if (n.endsWith("_ORE") || n.equals("STONE") || n.equals("DEEPSLATE") || n.equals("TUFF") || n.equals("GRANITE") || n.equals("DIORITE")
                    || n.equals("ANDESITE") || n.equals("CALCITE") || n.equals("DIRT") || n.equals("GRAVEL") || n.equals("CLAY")
                    || n.equals("COARSE_DIRT") || n.equals("SMOOTH_BASALT") || n.equals("DRIPSTONE_BLOCK") || n.equals("SANDSTONE")
                    || n.equals("RED_SANDSTONE") || n.equals("TERRACOTTA") || n.endsWith("_TERRACOTTA") && !n.contains("GLAZED")
                    || n.equals("RAW_IRON_BLOCK") || n.equals("RAW_COPPER_BLOCK")) {
                NATURAL.add(m);
            }
        }
        GROUND.addAll(NATURAL);
        GROUND.addAll(EnumSet.of(Material.GRASS_BLOCK, Material.PODZOL, Material.MYCELIUM, Material.SAND, Material.RED_SAND, Material.SNOW_BLOCK,
                Material.ROOTED_DIRT, Material.MUD, Material.MOSS_BLOCK, Material.PACKED_ICE, Material.ICE, Material.BLUE_ICE, Material.DIRT_PATH,
                Material.FARMLAND, Material.BEDROCK));
    }

    private final DuckyMoMoCore plugin;
    private final File progressFile;
    private BukkitTask task;
    private int minCx, maxCx, minCz, maxCz;      // vùng chạy (chunk)
    private int next;                            // chỉ số chunk tiếp theo (hàng theo z, cột theo x)
    private int total;
    private long carved, lava, skipped, decor;
    private boolean full;                        // chạy cả vùng (lưu tiến độ) hay chạy thử
    private CommandSender who;
    private java.util.concurrent.CompletableFuture<?> pending;
    private ChunkSnapshot srcSnap;
    private int srcCx, srcCz;

    CaveCarver(DuckyMoMoCore plugin) {
        this.plugin = plugin;
        this.progressFile = new File(plugin.getDataFolder(), "hang-tiendo.yml");
    }

    void command(CommandSender s, String[] a) {
        String sub = a.length >= 2 ? a[1].toLowerCase(java.util.Locale.ROOT) : "xem";
        switch (sub) {
            case "thu" -> {
                if (a.length < 4) {
                    s.sendMessage("/dmm hang thu <x> <z> [bán kính chunk, mặc định 2]");
                    return;
                }
                int x = Integer.parseInt(a[2]), z = Integer.parseInt(a[3]), r = a.length >= 5 ? Integer.parseInt(a[4]) : 2;
                start(s, (x >> 4) - r, (x >> 4) + r, (z >> 4) - r, (z >> 4) + r, 0, false);
            }
            case "chay" -> {
                var c = plugin.getConfig();
                YamlConfiguration p = YamlConfiguration.loadConfiguration(progressFile);
                start(s, c.getInt("decorate.min-x", 11200) >> 4, c.getInt("decorate.max-x", 13800) >> 4,
                        c.getInt("decorate.min-z", -3200) >> 4, c.getInt("decorate.max-z", -800) >> 4, p.getInt("next", 0), true);
                carved = p.getLong("carved", 0);
                lava = p.getLong("lava", 0);
                skipped = p.getLong("skipped", 0);
                decor = p.getLong("decor", 0);
            }
            case "dung" -> {
                stop();
                s.sendMessage("Đã dừng đục hang ở chunk " + next + "/" + total + ". Chạy tiếp: /dmm hang chay");
            }
            default -> s.sendMessage(task == null ? "Đục hang: đang nghỉ. " + savedText() : status());
        }
    }

    private String savedText() {
        YamlConfiguration p = YamlConfiguration.loadConfiguration(progressFile);
        return p.contains("next") ? "Lần trước tới chunk " + p.getInt("next") + "/" + p.getInt("total") + ", đã đục " + p.getLong("carved") + " khối." : "";
    }

    private String status() {
        return String.format("Đục hang: chunk %d/%d (%.1f%%), đã đục %d khối, trang trí hang %d, dung nham %d, bỏ qua %d chunk (spawn / thị trấn).",
                next, total, total == 0 ? 0 : 100.0 * next / total, carved, decor, lava, skipped);
    }

    private void start(CommandSender s, int x0, int x1, int z0, int z1, int from, boolean full) {
        if (task != null) {
            s.sendMessage("Đang chạy rồi: " + status());
            return;
        }
        World dst = Bukkit.getWorld(plugin.getConfig().getString("decorate.world", "world"));
        if (dst == null) {
            s.sendMessage("Không thấy thế giới decorate.world");
            return;
        }
        World src = Bukkit.getWorld(SOURCE);
        if (src == null) {
            s.sendMessage("Tạo thế giới nguồn " + SOURCE + " (Minecraft thường, không công trình)…");
            src = new WorldCreator(SOURCE).environment(World.Environment.NORMAL).type(WorldType.NORMAL).generateStructures(false)
                    .seed(20261023L).createWorld();
            if (src == null) {
                s.sendMessage("Không tạo được thế giới nguồn.");
                return;
            }
            src.setAutoSave(false);
        }
        this.minCx = x0;
        this.maxCx = x1;
        this.minCz = z0;
        this.maxCz = z1;
        this.total = (x1 - x0 + 1) * (z1 - z0 + 1);
        this.next = Math.max(0, Math.min(from, total));
        this.full = full;
        this.who = s;
        if (!full) {
            carved = lava = skipped = decor = 0;
        }
        World srcW = src;
        s.sendMessage("Bắt đầu đục hang: " + total + " chunk (" + (full ? "cả vùng mùa, từ chunk " + next : "chạy thử") + ").");
        plugin.getLogger().info("Đục hang: bắt đầu " + total + " chunk, từ " + next);
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(dst, srcW), 1L, 1L);
    }

    private void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (full) {
            save();
        }
    }

    private void save() {
        YamlConfiguration p = new YamlConfiguration();
        p.set("next", next);
        p.set("total", total);
        p.set("carved", carved);
        p.set("lava", lava);
        p.set("skipped", skipped);
        p.set("decor", decor);
        try {
            p.save(progressFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Đục hang: không lưu được tiến độ: " + ex.getMessage());
        }
    }

    /** Mỗi tick: chờ chunk nguồn tạo xong (bất đồng bộ), đục trong giới hạn thời gian, rồi xin chunk nguồn kế tiếp. */
    private void tick(World dst, World src) {
        long t0 = System.nanoTime();
        while (System.nanoTime() - t0 < BUDGET_NS) {
            if (next >= total) {
                stop();
                String done = "Đục hang XONG: " + status();
                plugin.getLogger().info(done);
                if (who != null) {
                    who.sendMessage(done);
                }
                return;
            }
            int w = maxCx - minCx + 1;
            int cx = minCx + next % w, cz = minCz + next / w;
            if (skip(dst, cx, cz)) {
                skipped++;
                next++;
                continue;
            }
            if (srcSnap == null || srcCx != cx || srcCz != cz) {
                if (pending == null) {
                    pending = src.getChunkAtAsync(cx, cz, true).thenAccept(ch -> {
                        srcSnap = ch.getChunkSnapshot(false, false, false);
                        srcCx = ch.getX();
                        srcCz = ch.getZ();
                        pending = null;
                    });
                }
                return;   // chờ tick sau
            }
            carve(dst, cx, cz);
            src.unloadChunkRequest(cx, cz);
            srcSnap = null;
            next++;
            if (full && next % 200 == 0) {
                save();
                plugin.getLogger().info(status());
            }
        }
    }

    private boolean skip(World dst, int cx, int cz) {
        var ex = plugin.getConfig().getConfigurationSection("decorate.exclude");
        int bx = (cx << 4) + 8, bz = (cz << 4) + 8;
        if (ex != null) {
            double dx = bx - ex.getInt("x"), dz = bz - ex.getInt("z"), r = ex.getInt("radius") + 16;
            if (dx * dx + dz * dz <= r * r) {
                return true;
            }
        }
        if (Bukkit.getPluginManager().isPluginEnabled("Towny")) {
            try {
                return com.palmergames.bukkit.towny.TownyAPI.getInstance().getTownBlock(new org.bukkit.Location(dst, bx, 64, bz)) != null;
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
    }

    private void carve(World dst, int cx, int cz) {
        var chunk = dst.getChunkAt(cx, cz);
        ChunkSnapshot d = chunk.getChunkSnapshot(false, false, false);
        int maxY = dst.getMaxHeight() - 1;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // mặt đất thật: khối GROUND cao nhất; có nước / dung nham phía trên thì chừa vỏ dày hơn
                int ground = MIN_Y;
                boolean wet = false;
                for (int y = maxY; y > MIN_Y; y--) {
                    Material m = d.getBlockType(x, y, z);
                    if (m == Material.WATER || m == Material.LAVA) {
                        wet = true;
                    } else if (GROUND.contains(m)) {
                        ground = y;
                        break;
                    }
                }
                // mặt đất của thế giới NGUỒN: chỉ chép phần nằm dưới nó (hang thật), không chép bầu trời của nguồn vào lòng núi Trái Đất
                int srcGround = MIN_Y;
                for (int y = maxY; y > MIN_Y; y--) {
                    if (GROUND.contains(srcSnap.getBlockType(x, y, z))) {
                        srcGround = y;
                        break;
                    }
                }
                int top = Math.min(ground - (wet ? SHELL_WATER : SHELL_LAND), srcGround - 3);
                for (int y = MIN_Y; y <= top; y++) {
                    Material s = srcSnap.getBlockType(x, y, z);
                    boolean air = s == Material.AIR || s == Material.CAVE_AIR;
                    boolean hot = s == Material.LAVA && y <= LAVA_Y;
                    boolean deco = DECOR.contains(s);
                    if (!air && !hot && !deco) {
                        continue;
                    }
                    Material m = d.getBlockType(x, y, z);
                    if (!NATURAL.contains(m) || nearFluid(dst, d, cx, cz, x, y, z)) {
                        continue;
                    }
                    Block b = chunk.getBlock(x, y, z);
                    if (deco) {
                        b.setBlockData(srcSnap.getBlockData(x, y, z), false);   // giữ hướng thạch nhũ, dây leo…
                        decor++;
                    } else {
                        b.setType(hot ? Material.LAVA : Material.CAVE_AIR, false);
                        if (hot) {
                            lava++;
                        } else {
                            carved++;
                        }
                    }
                }
            }
        }
    }

    /** Có nước / dung nham sát bên (6 hướng) ở map thật: không đục, khỏi tràn ngập hang. */
    private static boolean nearFluid(World w, ChunkSnapshot d, int cx, int cz, int x, int y, int z) {
        int[][] dir = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] v : dir) {
            int nx = x + v[0], ny = y + v[1], nz = z + v[2];
            Material m = nx >= 0 && nx < 16 && nz >= 0 && nz < 16 ? d.getBlockType(nx, ny, nz)
                    : w.getBlockAt((cx << 4) + nx, ny, (cz << 4) + nz).getType();
            if (m == Material.WATER || m == Material.LAVA) {
                return true;
            }
        }
        return false;
    }
}
