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
 * <p>Lệnh: /dmm decorate start | pause | status | reset. Cấu hình: plugins/DuckyMoMoCore/config.yml mục "decorate".
 */
final class MapDecorator {

    private final DuckyMoMoCore plugin;
    private final File progressFile;
    private final Random random = new Random();

    // cấu hình
    private String worldName;
    private int minCx, maxCx, minCz, maxCz;
    private double treeDensity;
    private int excludeX, excludeZ, excludeRadius;
    private long tickBudgetNanos;
    private int maxInFlight;

    // trạng thái
    private int cursorX, cursorZ;
    private long chunksDone, treesPlaced, plantsPlaced;
    private boolean running;
    /** Admin muốn tác vụ chạy (lưu vào file, để tự chạy tiếp sau khi server khởi động lại). */
    private boolean wantRunning;
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
                sender.sendMessage("Đã tạm dừng. Chạy tiếp: /dmm decorate start");
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
        saveProgress();
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
        Map.entry("jungle", new Veg(List.of(TreeType.JUNGLE, TreeType.SMALL_JUNGLE, TreeType.SMALL_JUNGLE, TreeType.COCOA_TREE, TreeType.JUNGLE_BUSH, TreeType.JUNGLE_BUSH), 9, GRASS, 10, null)),
        Map.entry("bamboo_jungle", new Veg(List.of(TreeType.SMALL_JUNGLE, TreeType.JUNGLE_BUSH, TreeType.JUNGLE_BUSH), 5, GRASS, 10, null)),
        Map.entry("sparse_jungle", new Veg(List.of(TreeType.SMALL_JUNGLE, TreeType.JUNGLE_BUSH, TreeType.TREE), 3, GRASS, 8, null)),
        Map.entry("forest", new Veg(List.of(TreeType.TREE, TreeType.TREE, TreeType.BIRCH, TreeType.BIG_TREE), 6, FLOWERS, 6, null)),
        Map.entry("flower_forest", new Veg(List.of(TreeType.TREE, TreeType.BIRCH), 4, FLOWERS, 14, null)),
        Map.entry("birch_forest", new Veg(List.of(TreeType.BIRCH, TreeType.BIRCH, TreeType.TALL_BIRCH), 6, FLOWERS, 6, null)),
        Map.entry("old_growth_birch_forest", new Veg(List.of(TreeType.TALL_BIRCH), 6, FLOWERS, 6, null)),
        Map.entry("dark_forest", new Veg(List.of(TreeType.DARK_OAK, TreeType.DARK_OAK, TreeType.TREE, TreeType.RED_MUSHROOM, TreeType.BROWN_MUSHROOM), 8, GRASS, 4, null)),
        Map.entry("taiga", new Veg(List.of(TreeType.REDWOOD, TreeType.TALL_REDWOOD), 6, GRASS, 6, null)),
        Map.entry("snowy_taiga", new Veg(List.of(TreeType.REDWOOD, TreeType.TALL_REDWOOD), 5, List.of(), 0, null)),
        Map.entry("old_growth_pine_taiga", new Veg(List.of(TreeType.MEGA_REDWOOD, TreeType.REDWOOD), 6, GRASS, 6, null)),
        Map.entry("old_growth_spruce_taiga", new Veg(List.of(TreeType.MEGA_REDWOOD, TreeType.TALL_REDWOOD), 6, GRASS, 6, null)),
        Map.entry("grove", new Veg(List.of(TreeType.REDWOOD, TreeType.TALL_REDWOOD), 3, List.of(), 0, null)),
        Map.entry("windswept_forest", new Veg(List.of(TreeType.REDWOOD, TreeType.TREE), 3, GRASS, 4, null)),
        Map.entry("windswept_hills", new Veg(List.of(TreeType.REDWOOD, TreeType.TREE), 0.6, GRASS, 4, null)),
        Map.entry("plains", new Veg(List.of(TreeType.TREE, TreeType.BIG_TREE), 0.3, FLOWERS, 10, null)),
        Map.entry("sunflower_plains", new Veg(List.of(TreeType.TREE), 0.3, FLOWERS, 12, null)),
        Map.entry("meadow", new Veg(List.of(TreeType.TREE, TreeType.BIRCH), 0.2, FLOWERS, 14, null)),
        Map.entry("cherry_grove", new Veg(List.of(TreeType.CHERRY), 3, FLOWERS, 6, null)),
        Map.entry("savanna", new Veg(List.of(TreeType.ACACIA, TreeType.ACACIA, TreeType.TREE), 1.2, GRASS, 10, null)),
        Map.entry("savanna_plateau", new Veg(List.of(TreeType.ACACIA, TreeType.TREE), 1.5, GRASS, 10, null)),
        Map.entry("windswept_savanna", new Veg(List.of(TreeType.ACACIA), 1, GRASS, 6, null)),
        Map.entry("swamp", new Veg(List.of(TreeType.SWAMP), 2, GRASS, 6, null)),
        Map.entry("mangrove_swamp", new Veg(List.of(TreeType.MANGROVE, TreeType.TALL_MANGROVE), 5, List.of(), 0, null)),
        Map.entry("wooded_badlands", new Veg(List.of(TreeType.TREE), 1.5, List.of(Material.DEAD_BUSH), 2, null)),
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
        int trees = (int) (veg.treesPerChunk() * treeDensity);
        if (random.nextDouble() < veg.treesPerChunk() * treeDensity - trees) {
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
        for (int i = 0; i < veg.plantsPerChunk(); i++) {
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
