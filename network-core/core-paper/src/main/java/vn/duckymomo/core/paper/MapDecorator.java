package vn.duckymomo.core.paper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

/**
 * Trang trí map Trái Đất (WorldPainter xuất ra không có cây, cỏ, công trình vì Minecraft 1.18+ coi các chunk đó
 * là đã hoàn chỉnh). Duyệt lần lượt mọi chunk trong viền thế giới, trồng cây/cỏ/hoa theo biome. Chạy rải đều qua
 * nhiều tick, lưu tiến độ để chạy tiếp. Công trình vanilla do {@link StructurePlacer} đặt riêng.
 *
 * <p>Lệnh: /dmm decorate start | pause | status | reset | area x1 z1 x2 z2. Cấu hình: plugins/DuckyMoMoCore/config.yml mục "decorate".
 * "area" trang trí bù một vùng (ví dụ vùng loại trừ cũ quanh spawn) mà không đụng tiến độ của lượt chính.
 */
final class MapDecorator {

    private final DuckyMoMoCore plugin;
    private final File progressFile;
    private final Random random = new Random();

    // cấu hình
    private String worldName;
    private int minCx, maxCx, minCz, maxCz;
    private double treeDensity;
    /** Lượt 2 (chỉ thêm cây): tắt trồng cỏ/hoa. */
    private boolean plantsEnabled;
    /** Nhân mật độ cây theo nhiễu mượt để cây mọc thành mảng rừng + trảng trống, thay vì rải đều từng chunk. */
    private boolean patchNoise;
    private int excludeX, excludeZ, excludeRadius;
    private long tickBudgetNanos;
    private int maxInFlight;

    // trạng thái
    private int cursorX, cursorZ;
    private long chunksDone, treesPlaced, plantsPlaced;
    private boolean running;
    /** Admin muốn tác vụ chạy (lưu vào file, để tự chạy tiếp sau khi server khởi động lại). */
    private boolean wantRunning;
    /** Đang chạy "area": biên tạm, không lưu tiến độ; xong thì nạp lại biên và tiến độ của lượt chính. */
    private boolean areaMode;
    private BukkitTask task;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final Deque<Chunk> ready = new ArrayDeque<>();
    private long startedAt;
    private long chunksAtStart;

    MapDecorator(DuckyMoMoCore plugin) {
        this.plugin = plugin;
        this.progressFile = new File(plugin.getDataFolder(), "decorate-progress.yml");
        loadConfig();
        loadProgress();
    }

    private void loadConfig() {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("decorate");
        worldName = c.getString("world", "world");
        minCx = Math.floorDiv(c.getInt("min-x"), 16);
        maxCx = Math.floorDiv(c.getInt("max-x") - 1, 16);
        minCz = Math.floorDiv(c.getInt("min-z"), 16);
        maxCz = Math.floorDiv(c.getInt("max-z") - 1, 16);
        treeDensity = c.getDouble("tree-density", 1.0);
        plantsEnabled = c.getBoolean("plants", true);
        patchNoise = c.getBoolean("patch-noise", false);
        excludeX = c.getInt("exclude.x");
        excludeZ = c.getInt("exclude.z");
        excludeRadius = c.getInt("exclude.radius", 300);
        tickBudgetNanos = c.getLong("tick-budget-ms", 15) * 1_000_000L;
        maxInFlight = Math.max(1, c.getInt("max-chunks-loading", 8));
    }

    // ------------------------------------------------------------------ tiến độ

    private void loadProgress() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(progressFile);
        cursorX = y.getInt("cursor-x", minCx);
        cursorZ = y.getInt("cursor-z", minCz);
        chunksDone = y.getLong("chunks-done");
        treesPlaced = y.getLong("trees");
        plantsPlaced = y.getLong("plants");
        wantRunning = y.getBoolean("want-running");
    }

    private void saveProgress() {
        if (areaMode) {
            return;
        }
        YamlConfiguration y = new YamlConfiguration();
        y.set("cursor-x", cursorX);
        y.set("cursor-z", cursorZ);
        y.set("chunks-done", chunksDone);
        y.set("trees", treesPlaced);
        y.set("plants", plantsPlaced);
        y.set("want-running", wantRunning);
        try {
            y.save(progressFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được tiến độ trang trí: " + e.getMessage());
        }
    }

    private long totalChunks() {
        return (long) (maxCx - minCx + 1) * (maxCz - minCz + 1);
    }

    private boolean finished() {
        return cursorZ > maxCz;
    }

    // ------------------------------------------------------------------ lệnh

    void command(CommandSender sender, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "status";
        switch (sub) {
            case "start", "resume" -> {
                if (running) {
                    sender.sendMessage("Đang chạy rồi. /dmm decorate status để xem tiến độ.");
                } else if (finished()) {
                    sender.sendMessage("Đã trang trí xong toàn bộ map. /dmm decorate reset nếu muốn chạy lại từ đầu.");
                } else {
                    start();
                    sender.sendMessage("Bắt đầu trang trí map (" + worldName + "). /dmm decorate status để xem tiến độ.");
                }
            }
            case "pause", "stop" -> {
                wantRunning = false;
                stop();
                if (areaMode) {
                    endArea();
                    sender.sendMessage("Đã dừng trang trí vùng.");
                } else {
                    sender.sendMessage("Đã tạm dừng. Chạy tiếp: /dmm decorate start");
                }
            }
            case "area" -> {
                if (running) {
                    sender.sendMessage("Đang chạy. Tạm dừng trước (/dmm decorate pause).");
                    return;
                }
                if (args.length < 6) {
                    sender.sendMessage("/dmm decorate area <x1> <z1> <x2> <z2>  (toạ độ block)");
                    return;
                }
                int x1, z1, x2, z2;
                try {
                    x1 = Integer.parseInt(args[2]);
                    z1 = Integer.parseInt(args[3]);
                    x2 = Integer.parseInt(args[4]);
                    z2 = Integer.parseInt(args[5]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("Toạ độ phải là số nguyên.");
                    return;
                }
                areaMode = true;
                minCx = Math.floorDiv(Math.min(x1, x2), 16);
                maxCx = Math.floorDiv(Math.max(x1, x2), 16);
                minCz = Math.floorDiv(Math.min(z1, z2), 16);
                maxCz = Math.floorDiv(Math.max(z1, z2), 16);
                cursorX = minCx;
                cursorZ = minCz;
                chunksDone = treesPlaced = plantsPlaced = 0;
                start();
                sender.sendMessage("Trang trí vùng " + totalChunks() + " chunk (bỏ qua vùng loại trừ quanh spawn). /dmm decorate status");
            }
            case "reset" -> {
                if (running) {
                    sender.sendMessage("Tạm dừng trước (/dmm decorate pause) rồi mới reset.");
                    return;
                }
                cursorX = minCx;
                cursorZ = minCz;
                chunksDone = treesPlaced = plantsPlaced = 0;
                saveProgress();
                sender.sendMessage("Đã đặt lại tiến độ về đầu.");
            }
            default -> sender.sendMessage(status());
        }
    }

    String status() {
        long total = totalChunks();
        long processed = (long) (cursorZ - minCz) * (maxCx - minCx + 1) + (cursorX - minCx);
        double pct = Math.min(100.0, processed * 100.0 / total);
        String eta = "";
        if (running && chunksDone > chunksAtStart) {
            double perSec = (chunksDone - chunksAtStart) / Math.max(1.0, (System.currentTimeMillis() - startedAt) / 1000.0);
            long remain = (long) ((total - processed) / Math.max(0.1, perSec));
            eta = String.format(" | %.0f chunk/s, còn khoảng %dh%02dm", perSec, remain / 3600, (remain % 3600) / 60);
        }
        return String.format("Trang trí map: %s %.2f%% (%d/%d chunk) | cây %d, cỏ/hoa %d%s",
            running ? "ĐANG CHẠY" : (finished() ? "XONG" : "TẠM DỪNG"),
            pct, processed, total, treesPlaced, plantsPlaced, eta);
    }

    void start() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Không có world " + worldName);
            return;
        }
        running = true;
        wantRunning = true;
        startedAt = System.currentTimeMillis();
        chunksAtStart = chunksDone;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(world), 1L, 1L);
        plugin.getLogger().info("Bắt đầu trang trí map. " + status());
    }

    /** Gọi khi server khởi động: nếu lần trước đang chạy dở (đã có tiến độ, chưa xong) thì chạy tiếp. */
    void resumeIfUnfinished() {
        if (!running && wantRunning && !finished()) {
            start();
        }
    }

    /** Server tắt: lưu tiến độ, giữ nguyên ý muốn chạy tiếp. */
    void shutdown() {
        if (task != null) {
            task.cancel();
        }
        if (areaMode) {
            endArea();  // vùng làm dở thì thôi, giữ tiến độ lượt chính
        }
        saveProgress();
    }

    /** Hết chế độ "area": nạp lại biên map và tiến độ của lượt chính. */
    private void endArea() {
        areaMode = false;
        loadConfig();
        loadProgress();
    }

    void stop() {
        running = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
        ready.clear();
        saveProgress();
    }

    // ------------------------------------------------------------------ vòng xử lý

    private long lastSave = System.currentTimeMillis();

    private void tick(World world) {
        long deadline = System.nanoTime() + tickBudgetNanos;

        // 1) xếp hàng nạp chunk bất đồng bộ (không tạo chunk mới)
        while (inFlight.get() < maxInFlight && !finished()) {
            int cx = cursorX, cz = cursorZ;
            advanceCursor();
            inFlight.incrementAndGet();
            world.getChunkAtAsync(cx, cz, false).whenComplete((chunk, err) -> {
                inFlight.decrementAndGet();
                if (chunk != null) {
                    ready.add(chunk);
                }
            });
        }

        // 2) trang trí các chunk đã nạp, trong giới hạn thời gian của tick
        while (!ready.isEmpty() && System.nanoTime() < deadline) {
            decorateChunk(world, ready.poll());
            chunksDone++;
        }

        if (System.currentTimeMillis() - lastSave > 30_000) {
            saveProgress();
            lastSave = System.currentTimeMillis();
        }
        if (finished() && inFlight.get() == 0 && ready.isEmpty()) {
            if (areaMode) {
                running = false;
                task.cancel();
                task = null;
                plugin.getLogger().info("Trang trí vùng XONG: " + chunksDone + " chunk, cây " + treesPlaced + ", cỏ/hoa " + plantsPlaced);
                endArea();
                return;
            }
            wantRunning = false;
            stop();
            plugin.getLogger().info("Trang trí map XONG. " + status());
        }
    }

    private void advanceCursor() {
        cursorX++;
        if (cursorX > maxCx) {
            cursorX = minCx;
            cursorZ++;
        }
    }

    private boolean excluded(int x, int z) {
        long dx = x - excludeX, dz = z - excludeZ;
        return dx * dx + dz * dz < (long) excludeRadius * excludeRadius;
    }

    // ------------------------------------------------------------------ cây, cỏ, hoa

    /** Cách trang trí theo biome: loại cây (kèm tỉ lệ), số cây trung bình mỗi chunk, cây bụi/hoa. */
    private record Veg(List<TreeType> trees, double treesPerChunk, List<Material> plants, int plantsPerChunk,
                       Material ground) {
    }

    private static final List<Material> FLOWERS = List.of(Material.SHORT_GRASS, Material.SHORT_GRASS, Material.SHORT_GRASS,
        Material.DANDELION, Material.POPPY, Material.OXEYE_DAISY, Material.CORNFLOWER, Material.AZURE_BLUET);
    private static final List<Material> GRASS = List.of(Material.SHORT_GRASS, Material.SHORT_GRASS, Material.FERN);

    private static final Map<String, Veg> VEG = Map.ofEntries(
        // Mật độ (cây/chunk) gần với rừng vanilla; patch-noise nhân thêm 0,25–3,0 theo mảng.
        Map.entry("jungle", new Veg(List.of(TreeType.JUNGLE, TreeType.JUNGLE, TreeType.SMALL_JUNGLE, TreeType.SMALL_JUNGLE, TreeType.COCOA_TREE, TreeType.JUNGLE_BUSH), 30, GRASS, 10, null)),
        Map.entry("bamboo_jungle", new Veg(List.of(TreeType.SMALL_JUNGLE, TreeType.JUNGLE, TreeType.JUNGLE_BUSH), 16, GRASS, 10, null)),
        Map.entry("sparse_jungle", new Veg(List.of(TreeType.SMALL_JUNGLE, TreeType.SMALL_JUNGLE, TreeType.JUNGLE_BUSH, TreeType.TREE), 14, GRASS, 8, null)),
        Map.entry("forest", new Veg(List.of(TreeType.TREE, TreeType.TREE, TreeType.BIRCH, TreeType.BIG_TREE), 14, FLOWERS, 6, null)),
        Map.entry("flower_forest", new Veg(List.of(TreeType.TREE, TreeType.BIRCH), 10, FLOWERS, 14, null)),
        Map.entry("birch_forest", new Veg(List.of(TreeType.BIRCH, TreeType.BIRCH, TreeType.TALL_BIRCH), 14, FLOWERS, 6, null)),
        Map.entry("old_growth_birch_forest", new Veg(List.of(TreeType.TALL_BIRCH), 14, FLOWERS, 6, null)),
        Map.entry("dark_forest", new Veg(List.of(TreeType.DARK_OAK, TreeType.DARK_OAK, TreeType.DARK_OAK, TreeType.TREE, TreeType.RED_MUSHROOM, TreeType.BROWN_MUSHROOM), 20, GRASS, 4, null)),
        Map.entry("taiga", new Veg(List.of(TreeType.REDWOOD, TreeType.TALL_REDWOOD), 14, GRASS, 6, null)),
        Map.entry("snowy_taiga", new Veg(List.of(TreeType.REDWOOD, TreeType.TALL_REDWOOD), 10, List.of(), 0, null)),
        Map.entry("old_growth_pine_taiga", new Veg(List.of(TreeType.MEGA_REDWOOD, TreeType.REDWOOD, TreeType.TALL_REDWOOD), 14, GRASS, 6, null)),
        Map.entry("old_growth_spruce_taiga", new Veg(List.of(TreeType.MEGA_REDWOOD, TreeType.TALL_REDWOOD, TreeType.REDWOOD), 14, GRASS, 6, null)),
        Map.entry("grove", new Veg(List.of(TreeType.REDWOOD, TreeType.TALL_REDWOOD), 6, List.of(), 0, null)),
        Map.entry("windswept_forest", new Veg(List.of(TreeType.REDWOOD, TreeType.TREE), 8, GRASS, 4, null)),
        Map.entry("windswept_hills", new Veg(List.of(TreeType.REDWOOD, TreeType.TREE), 1, GRASS, 4, null)),
        Map.entry("plains", new Veg(List.of(TreeType.TREE, TreeType.BIG_TREE, TreeType.BIRCH), 0.6, FLOWERS, 10, null)),
        Map.entry("sunflower_plains", new Veg(List.of(TreeType.TREE), 0.5, FLOWERS, 12, null)),
        Map.entry("meadow", new Veg(List.of(TreeType.TREE, TreeType.BIRCH), 0.4, FLOWERS, 14, null)),
        Map.entry("cherry_grove", new Veg(List.of(TreeType.CHERRY), 8, FLOWERS, 6, null)),
        Map.entry("savanna", new Veg(List.of(TreeType.ACACIA, TreeType.ACACIA, TreeType.TREE), 2, GRASS, 10, null)),
        Map.entry("savanna_plateau", new Veg(List.of(TreeType.ACACIA, TreeType.TREE), 2.5, GRASS, 10, null)),
        Map.entry("windswept_savanna", new Veg(List.of(TreeType.ACACIA), 1.5, GRASS, 6, null)),
        Map.entry("swamp", new Veg(List.of(TreeType.SWAMP), 6, GRASS, 6, null)),
        Map.entry("mangrove_swamp", new Veg(List.of(TreeType.MANGROVE, TreeType.TALL_MANGROVE), 12, List.of(), 0, null)),
        Map.entry("wooded_badlands", new Veg(List.of(TreeType.TREE), 3, List.of(Material.DEAD_BUSH), 2, null)),
        Map.entry("badlands", new Veg(List.of(), 0, List.of(Material.DEAD_BUSH, Material.CACTUS), 3, null)),
        Map.entry("eroded_badlands", new Veg(List.of(), 0, List.of(Material.DEAD_BUSH, Material.CACTUS), 3, null)),
        Map.entry("desert", new Veg(List.of(), 0, List.of(Material.DEAD_BUSH, Material.CACTUS, Material.CACTUS), 3, null)),
        Map.entry("snowy_plains", new Veg(List.of(TreeType.REDWOOD), 0.15, List.of(), 0, null)),
        Map.entry("mushroom_fields", new Veg(List.of(TreeType.RED_MUSHROOM, TreeType.BROWN_MUSHROOM), 1, List.of(), 0, null)));

    private void decorateChunk(World world, Chunk chunk) {
        int baseX = chunk.getX() << 4, baseZ = chunk.getZ() << 4;
        if (excluded(baseX + 8, baseZ + 8)) {
            return;
        }
        String biome = biomeKey(world.getBiome(baseX + 8, world.getHighestBlockYAt(baseX + 8, baseZ + 8), baseZ + 8));

        Veg veg = VEG.get(biome);
        if (veg == null) {
            return; // biển, sông, núi đá, băng…: không trồng
        }
        double want = veg.treesPerChunk() * treeDensity * (patchNoise ? patch(chunk.getX(), chunk.getZ()) : 1.0);
        int trees = (int) want;
        if (random.nextDouble() < want - trees) {
            trees++;
        }
        for (int i = 0; i < trees && !veg.trees().isEmpty(); i++) {
            Block ground = surface(world, baseX + random.nextInt(16), baseZ + random.nextInt(16));
            if (ground == null || !isSoil(ground.getType())) {
                continue;
            }
            TreeType type = veg.trees().get(random.nextInt(veg.trees().size()));
            Location at = ground.getLocation().add(0.5, 1, 0.5);
            if (world.generateTree(at, random, type)) {
                treesPlaced++;
            }
        }
        if ("bamboo_jungle".equals(biome)) {
            plantBamboo(world, baseX, baseZ);
        }
        for (int i = 0; plantsEnabled && i < veg.plantsPerChunk(); i++) {
            Block ground = surface(world, baseX + random.nextInt(16), baseZ + random.nextInt(16));
            if (ground == null) {
                continue;
            }
            Material plant = veg.plants().get(random.nextInt(veg.plants().size()));
            Block above = ground.getRelative(0, 1, 0);
            if (!above.getType().isAir()) {
                continue;
            }
            boolean ok = switch (plant) {
                case CACTUS -> ground.getType() == Material.SAND || ground.getType() == Material.RED_SAND;
                case DEAD_BUSH -> ground.getType() == Material.SAND || ground.getType() == Material.RED_SAND
                    || ground.getType() == Material.TERRACOTTA || ground.getType() == Material.COARSE_DIRT;
                default -> ground.getType() == Material.GRASS_BLOCK || ground.getType() == Material.PODZOL;
            };
            if (!ok) {
                continue;
            }
            above.setType(plant, false);
            if (plant == Material.CACTUS && random.nextBoolean()) {
                Block top = above.getRelative(0, 1, 0);
                if (top.getType().isAir()) {
                    top.setType(Material.CACTUS, false);
                }
            }
            plantsPlaced++;
        }
    }

    /**
     * Hệ số mật độ theo vị trí: nhiễu giá trị hai tầng (ô 8 và 3 chunk, nội suy mượt) -> 0,25 (trảng trống) đến
     * 3,0 (rừng rậm). Cố định theo toạ độ nên chạy lại vẫn ra cùng mảng rừng.
     */
    private static double patch(int cx, int cz) {
        double n = 0.65 * valueNoise(cx / 8.0, cz / 8.0, 1) + 0.35 * valueNoise(cx / 3.0, cz / 3.0, 2);
        double m = Math.max(0, Math.min(1, (n - 0.35) / 0.4));
        return 0.25 + 2.75 * m;
    }

    private static double valueNoise(double x, double z, int seed) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double fx = x - x0, fz = z - z0;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash01(x0, z0, seed), b = hash01(x0 + 1, z0, seed), c = hash01(x0, z0 + 1, seed), d = hash01(x0 + 1, z0 + 1, seed);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fz;
    }

    private static double hash01(int x, int z, int seed) {
        long h = x * 374761393L + z * 668265263L + seed * 2147483647L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }

    /** Rừng tre: vài bụi tre thật (cao 6–12) mỗi chunk, mỗi bụi 4–9 cây sát nhau. */
    private void plantBamboo(World world, int baseX, int baseZ) {
        int clumps = 1 + random.nextInt(3);
        for (int c = 0; c < clumps; c++) {
            int cx = baseX + 2 + random.nextInt(12), cz = baseZ + 2 + random.nextInt(12);
            int stalks = 4 + random.nextInt(6);
            for (int i = 0; i < stalks; i++) {
                Block ground = surface(world, cx + random.nextInt(5) - 2, cz + random.nextInt(5) - 2);
                if (ground == null || !isSoil(ground.getType())) {
                    continue;
                }
                int h = 6 + random.nextInt(7);
                Block b = ground.getRelative(0, 1, 0);
                boolean free = true;
                for (int k = 0; k < h; k++) {
                    if (!b.getRelative(0, k, 0).getType().isAir()) {
                        free = false;
                        break;
                    }
                }
                if (!free) {
                    continue;
                }
                for (int k = 0; k < h; k++) {
                    b.getRelative(0, k, 0).setType(Material.BAMBOO, false);
                }
                plantsPlaced++;
            }
        }
    }

    /** Khối mặt đất cao nhất (bỏ qua lá cây), null nếu là nước/dung nham. */
    private static Block surface(World world, int x, int z) {
        Block b = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        Material t = b.getType();
        if (t == Material.WATER || t == Material.LAVA || t == Material.ICE || t == Material.BLUE_ICE || t == Material.PACKED_ICE) {
            return null;
        }
        return b;
    }

    private static boolean isSoil(Material m) {
        return m == Material.GRASS_BLOCK || m == Material.DIRT || m == Material.PODZOL || m == Material.COARSE_DIRT
            || m == Material.MOSS_BLOCK || m == Material.MUD || m == Material.ROOTED_DIRT || m == Material.MYCELIUM
            || m == Material.SNOW_BLOCK;
    }

    @SuppressWarnings("deprecation")
    private static String biomeKey(org.bukkit.block.Biome biome) {
        return biome.getKey().getKey();
    }
}
