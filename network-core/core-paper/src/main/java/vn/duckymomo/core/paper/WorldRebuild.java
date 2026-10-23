package vn.duckymomo.core.paper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;

/**
 * Đổi map Trái Đất (WorldPainter, không hang, ít quặng) sang world Minecraft thường "giả làm Trái Đất" (23/10, chủ server).
 * Giữ nguyên toạ độ (khu /tpr, warp, Kinh Đô, dungeon… không phải sửa): thế giới mới tạo tạm tên dmm_moi (cùng thư mục world,
 * dimension riêng), tạo trước vùng mùa bằng Chunky, chép khu spawn từ world sang, rồi TẮT server đổi thư mục overworld (ops, ngoài plugin).
 * /dmm thegioi seed <n>: thử n seed, chấm điểm quanh spawn (đất liền, phẳng, độ cao gần spawn cũ) | tao <seed> | chepspawn | xem
 */
final class WorldRebuild {

    static final String NEW = "dmm_moi";
    private static final int SPAWN_X = 12699, SPAWN_Z = -2465, HALF = 200;   // khu spawn (decorate.exclude 190 + lề)
    private static final int COPY_FROM_Y = 40;                               // dưới mức này giữ lòng đất world mới (hang, quặng)
    private static final long BUDGET_NS = 30_000_000L;

    private final DuckyMoMoCore plugin;
    private BukkitTask task;
    private String state = "nghỉ";

    WorldRebuild(DuckyMoMoCore plugin) {
        this.plugin = plugin;
    }

    void command(CommandSender s, String[] a) {
        String sub = a.length >= 2 ? a[1].toLowerCase(java.util.Locale.ROOT) : "xem";
        switch (sub) {
            case "seed" -> seeds(s, a.length >= 3 ? Integer.parseInt(a[2]) : 8);
            case "tao" -> {
                if (a.length < 3) {
                    s.sendMessage("/dmm thegioi tao <seed>");
                    return;
                }
                World w = create(NEW, Long.parseLong(a[2]));
                World main = Bukkit.getWorlds().get(0);
                w.getWorldBorder().setCenter(main.getWorldBorder().getCenter());
                w.getWorldBorder().setSize(main.getWorldBorder().getSize());
                var c = plugin.getConfig();
                int x0 = c.getInt("decorate.min-x", 11200), x1 = c.getInt("decorate.max-x", 13800);
                int z0 = c.getInt("decorate.min-z", -3200), z1 = c.getInt("decorate.max-z", -800);
                var con = Bukkit.getConsoleSender();
                for (String cmd : List.of("chunky world " + NEW, "chunky shape square",
                        "chunky center " + (x0 + x1) / 2 + " " + (z0 + z1) / 2, "chunky radius " + (x1 - x0) / 2 + " " + (z1 - z0) / 2, "chunky start")) {
                    Bukkit.dispatchCommand(con, cmd);
                }
                s.sendMessage("Đã tạo " + NEW + " (seed " + a[2] + "), Chunky đang tạo trước vùng mùa. Xem: /chunky progress");
            }
            case "chepspawn" -> copySpawn(s);
            case "sua" -> fixHeights(s);
            default -> s.sendMessage("Đổi world: " + state);
        }
    }

    private static World create(String name, long seed) {
        World w = Bukkit.getWorld(name);
        if (w != null) {
            return w;
        }
        w = new WorldCreator(name).environment(World.Environment.NORMAL).type(WorldType.NORMAL).seed(seed).createWorld();
        w.setAutoSave(true);
        return w;
    }

    /** Thử n seed: mẫu lưới 11×11 (bước 40) quanh spawn, điểm cao = nhiều đất liền, ít chênh độ cao, gần độ cao spawn cũ. */
    private void seeds(CommandSender s, int n) {
        World main = Bukkit.getWorlds().get(0);
        int ref = main.getHighestBlockYAt(SPAWN_X, SPAWN_Z, org.bukkit.HeightMap.OCEAN_FLOOR);
        record R(long seed, double score, int land, double avgDiff, double std, String biome) { }
        List<R> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long seed = ThreadLocalRandom.current().nextLong();
            String name = "dmm_thuseed";
            World w = create(name, seed);
            int land = 0, cnt = 0;
            double sum = 0, sum2 = 0, diff = 0;
            for (int dx = -HALF; dx <= HALF; dx += 40) {
                for (int dz = -HALF; dz <= HALF; dz += 40) {
                    int x = SPAWN_X + dx, z = SPAWN_Z + dz;
                    String b = w.getBiome(x, 64, z).getKey().getKey();
                    int y = w.getHighestBlockYAt(x, z, org.bukkit.HeightMap.OCEAN_FLOOR);
                    boolean wet = b.contains("ocean") || b.contains("river") || b.contains("beach") || b.contains("swamp");
                    if (!wet && y >= 62) {
                        land++;
                    }
                    cnt++;
                    sum += y;
                    sum2 += (double) y * y;
                    diff += Math.abs(y - ref);
                }
            }
            double mean = sum / cnt, std = Math.sqrt(Math.max(0, sum2 / cnt - mean * mean));
            String mid = w.getBiome(SPAWN_X, 64, SPAWN_Z).getKey().getKey();
            double score = 100.0 * land / cnt - std * 2 - diff / cnt * 1.5;
            out.add(new R(seed, score, 100 * land / cnt, diff / cnt, std, mid));
            Path folder = w.getWorldFolder().toPath().toAbsolutePath().normalize();
            Bukkit.unloadWorld(w, false);
            Path mainFolder = main.getWorldFolder().toPath().toAbsolutePath().normalize();
            // CHỐT AN TOÀN: chỉ xoá thư mục đúng tên thế giới thử, không bao giờ xoá thư mục world chính
            if (folder.getFileName().toString().equals(name) && !folder.equals(mainFolder) && !mainFolder.startsWith(folder)) {
                delete(folder);
            } else {
                plugin.getLogger().warning("Đổi world: KHÔNG xoá " + folder + " (không phải thư mục thế giới thử)");
            }
        }
        out.sort(Comparator.comparingDouble(R::score).reversed());
        s.sendMessage("Độ cao spawn cũ: " + ref + ". Seed tốt nhất trước:");
        for (R r : out) {
            s.sendMessage(String.format("seed %d | điểm %.0f | đất liền %d%% | chênh với spawn cũ %.1f | gồ ghề %.1f | giữa: %s",
                    r.seed(), r.score(), r.land(), r.avgDiff(), r.std(), r.biome()));
            plugin.getLogger().info(String.format("Thử seed %d điểm %.0f đất %d%% chênh %.1f gồ ghề %.1f %s", r.seed(), r.score(), r.land(), r.avgDiff(), r.std(), r.biome()));
        }
    }

    private static void delete(Path p) {
        try (var st = Files.walk(p)) {
            st.sorted(Comparator.reverseOrder()).forEach(x -> x.toFile().delete());
        } catch (IOException ignored) {
        }
    }

    /** Chép khu spawn (khung vuông quanh spawn, từ y COPY_FROM_Y lên trời) từ world chính sang dmm_moi, rải qua nhiều tick. */
    private void copySpawn(CommandSender s) {
        World src = Bukkit.getWorlds().get(0), dst = Bukkit.getWorld(NEW);
        if (dst == null) {
            s.sendMessage("Chưa có " + NEW + ": /dmm thegioi tao <seed> trước.");
            return;
        }
        if (task != null) {
            s.sendMessage("Đang chép: " + state);
            return;
        }
        int x0 = SPAWN_X - HALF, z0 = SPAWN_Z - HALF, size = HALF * 2 + 1, maxY = src.getMaxHeight();
        int[] col = {0};
        long[] blocks = {0}, states = {0};
        int total = size * size;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long t0 = System.nanoTime();
            while (System.nanoTime() - t0 < BUDGET_NS) {
                if (col[0] >= total) {
                    task.cancel();
                    task = null;
                    int ents = copyEntities(src, dst, x0, z0, size);
                    state = "chép spawn XONG: " + blocks[0] + " khối, " + states[0] + " khối có dữ liệu (rương, bảng…), " + ents + " vật trang trí.";
                    s.sendMessage(state);
                    plugin.getLogger().info("Đổi world: " + state);
                    return;
                }
                int x = x0 + col[0] % size, z = z0 + col[0] / size;
                for (int y = COPY_FROM_Y; y < maxY; y++) {
                    Block a = src.getBlockAt(x, y, z), b = dst.getBlockAt(x, y, z);
                    var data = a.getBlockData();
                    if (!b.getBlockData().equals(data)) {
                        b.setBlockData(data, false);
                        blocks[0]++;
                    }
                    BlockState st = a.getState();
                    if (st instanceof org.bukkit.block.TileState) {
                        try {
                            st.copy(b.getLocation()).update(true, false);
                            states[0]++;
                        } catch (Throwable ignored) {
                        }
                    }
                }
                col[0]++;
            }
            state = String.format("đang chép spawn %d/%d cột (%.1f%%), %d khối", col[0], total, 100.0 * col[0] / total, blocks[0]);
        }, 1L, 1L);
        s.sendMessage("Bắt đầu chép spawn: " + total + " cột, y từ " + COPY_FROM_Y + ". Xem: /dmm thegioi xem");
    }

    private static boolean inSpawn(double x, double z) {
        return Math.abs(x - SPAWN_X) <= HALF && Math.abs(z - SPAWN_Z) <= HALF;
    }

    /** Mặt đất world mới: khối rắn cao nhất (bỏ lá) + 1. */
    private static double surface(World w, double x, double z) {
        return w.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z), org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
    }

    /**
     * Sau khi đổi world: warp, nhà (Essentials) và NPC Citizens NGOÀI khu spawn vẫn mang độ cao của map cũ -> đặt lên mặt đất mới.
     * Trong khu spawn (chép nguyên) giữ nguyên.
     */
    private void fixHeights(CommandSender s) {
        World w = Bukkit.getWorlds().get(0);
        int warps = 0, homes = 0, npcs = 0;
        java.io.File ess = new java.io.File(plugin.getDataFolder().getParentFile(), "Essentials");
        java.io.File[] wf = new java.io.File(ess, "warps").listFiles((d, n) -> n.endsWith(".yml"));
        for (java.io.File f : wf == null ? new java.io.File[0] : wf) {
            var y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
            if (w.getName().equals(y.getString("world-name")) && !inSpawn(y.getDouble("x"), y.getDouble("z"))) {
                y.set("y", surface(w, y.getDouble("x"), y.getDouble("z")));
                try {
                    y.save(f);
                    warps++;
                } catch (IOException ignored) {
                }
            }
        }
        java.io.File[] uf = new java.io.File(ess, "userdata").listFiles((d, n) -> n.endsWith(".yml"));
        for (java.io.File f : uf == null ? new java.io.File[0] : uf) {
            var y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
            var hs = y.getConfigurationSection("homes");
            boolean changed = false;
            for (String h : hs == null ? java.util.Set.<String>of() : hs.getKeys(false)) {
                var c = hs.getConfigurationSection(h);
                if (c != null && w.getName().equals(c.getString("world-name")) && !inSpawn(c.getDouble("x"), c.getDouble("z"))) {
                    c.set("y", surface(w, c.getDouble("x"), c.getDouble("z")));
                    changed = true;
                    homes++;
                }
            }
            if (changed) {
                try {
                    y.save(f);
                } catch (IOException ignored) {
                }
            }
        }
        if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            for (net.citizensnpcs.api.npc.NPC npc : net.citizensnpcs.api.CitizensAPI.getNPCRegistry()) {
                Location l = npc.getStoredLocation();
                if (l != null && l.getWorld() == w && !inSpawn(l.getX(), l.getZ())) {
                    Location to = l.clone();
                    to.setY(surface(w, l.getX(), l.getZ()));
                    npc.teleport(to, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                    npcs++;
                }
            }
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "essentials reload");
        String msg = "Đổi world: đặt lại độ cao " + warps + " warp, " + homes + " nhà, " + npcs + " NPC ngoài spawn lên mặt đất mới.";
        s.sendMessage(msg);
        plugin.getLogger().info(msg);
    }

    /** Vật trang trí (khung tranh, tranh, armor stand trang trí, khối hiển thị): không chép NPC Citizens, người, quái, đồ rơi. */
    private static int copyEntities(World src, World dst, int x0, int z0, int size) {
        int n = 0;
        for (int cx = x0 >> 4; cx <= (x0 + size) >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= (z0 + size) >> 4; cz++) {
                for (Entity e : src.getChunkAt(cx, cz).getEntities()) {
                    if (e.hasMetadata("NPC") || !(e instanceof org.bukkit.entity.Hanging || e instanceof org.bukkit.entity.ArmorStand
                            || e instanceof org.bukkit.entity.Display)) {
                        continue;
                    }
                    Location l = e.getLocation().clone();
                    l.setWorld(dst);
                    try {
                        e.copy(l);
                        n++;
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return n;
    }
}
