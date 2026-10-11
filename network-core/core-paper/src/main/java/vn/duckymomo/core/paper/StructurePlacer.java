package vn.duckymomo.core.paper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

/**
 * Đặt công trình vanilla (làng, đền, hầm mỏ, stronghold…) lên map Trái Đất theo lưới thưa. Tách khỏi phần trồng cây
 * vì lệnh /place structure cần MỌI chunk trong vùng công trình được nạp sẵn ("That position is not loaded"):
 * mỗi điểm lưới nạp các chunk xung quanh, giữ bằng plugin chunk ticket, đặt, rồi thả ra. Từng điểm một.
 *
 * <p>Lệnh: /dmm structures start | pause | status | reset. Kết quả từng công trình ghi vào structures-placed.csv.
 * Chọn công trình dùng Random theo toạ độ, nên chạy lại (reset) ra cùng kết quả.
 */
final class StructurePlacer {

    private final DuckyMoMoCore plugin;
    private final File progressFile;
    private final File logFile;

    private String worldName;
    private int minCx, maxCx, minCz, maxCz;
    private int spacing;
    private double chance;
    private int excludeX, excludeZ, excludeRadius;

    private enum Kind { SURFACE, MINESHAFT, TRIAL, STRONGHOLD }

    private record Candidate(int cx, int cz, Kind kind) {
    }

    private final List<Candidate> candidates = new ArrayList<>();

    // trạng thái
    private int index;
    private long placed, failed, skipped;
    private boolean running, wantRunning;
    private BukkitTask task;

    /** Điểm lưới đang xử lý (null = rảnh, lấy điểm tiếp theo). */
    private Candidate current;
    private String currentName;
    private int radius;
    private int pendingLoads;
    private final List<Chunk> held = new ArrayList<>();

    StructurePlacer(DuckyMoMoCore plugin) {
        this.plugin = plugin;
        this.progressFile = new File(plugin.getDataFolder(), "structures-progress.yml");
        this.logFile = new File(plugin.getDataFolder(), "structures-placed.csv");
        loadConfig();
        buildCandidates();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(progressFile);
        index = y.getInt("index");
        placed = y.getLong("placed");
        failed = y.getLong("failed");
        skipped = y.getLong("skipped");
        wantRunning = y.getBoolean("want-running");
    }

    private void loadConfig() {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("decorate");
        worldName = c.getString("world", "world");
        minCx = Math.floorDiv(c.getInt("min-x"), 16);
        maxCx = Math.floorDiv(c.getInt("max-x") - 1, 16);
        minCz = Math.floorDiv(c.getInt("min-z"), 16);
        maxCz = Math.floorDiv(c.getInt("max-z") - 1, 16);
        spacing = Math.max(8, c.getInt("structure-spacing-chunks", 64));
        chance = c.getDouble("structure-chance", 0.6);
        excludeX = c.getInt("exclude.x");
        excludeZ = c.getInt("exclude.z");
        excludeRadius = c.getInt("exclude.radius", 300);
    }

    /** Cùng lưới với bản cũ trong MapDecorator: mặt đất mỗi ô spacing, hầm mỏ dày gấp đôi, phòng thử thách, stronghold thưa hơn. */
    private void buildCandidates() {
        int under = Math.max(8, spacing / 2);
        for (int cz = minCz; cz <= maxCz; cz++) {
            for (int cx = minCx; cx <= maxCx; cx++) {
                if (Math.floorMod(cx, spacing) == spacing / 2 && Math.floorMod(cz, spacing) == spacing / 2) {
                    candidates.add(new Candidate(cx, cz, Kind.SURFACE));
                }
                if (Math.floorMod(cx, under) == 3 && Math.floorMod(cz, under) == 3) {
                    candidates.add(new Candidate(cx, cz, Kind.MINESHAFT));
                }
                if (Math.floorMod(cx, spacing * 2) == 11 && Math.floorMod(cz, spacing * 2) == 11) {
                    candidates.add(new Candidate(cx, cz, Kind.TRIAL));
                }
                if (Math.floorMod(cx, spacing * 4) == 23 && Math.floorMod(cz, spacing * 4) == 23) {
                    candidates.add(new Candidate(cx, cz, Kind.STRONGHOLD));
                }
            }
        }
    }

    private void saveProgress() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("index", index);
        y.set("placed", placed);
        y.set("failed", failed);
        y.set("skipped", skipped);
        y.set("want-running", wantRunning);
        try {
            y.save(progressFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được tiến độ đặt công trình: " + e.getMessage());
        }
    }

    private boolean finished() {
        return index >= candidates.size();
    }

    // ------------------------------------------------------------------ lệnh

    void command(CommandSender sender, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "status";
        switch (sub) {
            case "start", "resume" -> {
                if (running) {
                    sender.sendMessage("Đang chạy rồi. /dmm structures status để xem tiến độ.");
                } else if (finished()) {
                    sender.sendMessage("Đã xong mọi điểm lưới. /dmm structures reset nếu muốn chạy lại.");
                } else {
                    start();
                    sender.sendMessage("Bắt đầu đặt công trình. /dmm structures status để xem tiến độ.");
                }
            }
            case "pause", "stop" -> {
                wantRunning = false;
                stop();
                sender.sendMessage("Đã tạm dừng. Chạy tiếp: /dmm structures start");
            }
            case "reset" -> {
                if (running) {
                    sender.sendMessage("Tạm dừng trước (/dmm structures pause) rồi mới reset.");
                    return;
                }
                index = 0;
                placed = failed = skipped = 0;
                saveProgress();
                sender.sendMessage("Đã đặt lại tiến độ về đầu (file structures-placed.csv giữ nguyên).");
            }
            default -> sender.sendMessage(status());
        }
    }

    String status() {
        return String.format("Đặt công trình: %s %d/%d điểm lưới | đã đặt %d, lỗi %d, bỏ qua %d",
            running ? "ĐANG CHẠY" : (finished() ? "XONG" : "TẠM DỪNG"),
            index, candidates.size(), placed, failed, skipped);
    }

    void start() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Không có world " + worldName);
            return;
        }
        running = true;
        wantRunning = true;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(world), 1L, 1L);
        plugin.getLogger().info("Bắt đầu đặt công trình. " + status());
    }

    void resumeIfUnfinished() {
        if (!running && wantRunning && !finished()) {
            start();
        }
    }

    void shutdown() {
        if (task != null) {
            task.cancel();
        }
        release();
        saveProgress();
    }

    void stop() {
        running = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
        // điểm đang làm dở sẽ được làm lại từ đầu lần sau (index chưa tăng)
        release();
        current = null;
        saveProgress();
    }

    // ------------------------------------------------------------------ vòng xử lý

    private void tick(World world) {
        if (current == null) {
            if (finished()) {
                wantRunning = false;
                stop();
                plugin.getLogger().info("Đặt công trình XONG. " + status());
                return;
            }
            begin(world, candidates.get(index));
            return;
        }
        if (currentName != null && pendingLoads == 0) {
            place(world);
            release();
            current = null;
            index++;
            saveProgress();
        }
    }

    /** Bước 1: nạp chunk giữa để xem biome, chọn công trình. */
    private void begin(World world, Candidate c) {
        current = c;
        currentName = null;
        pendingLoads = 1;
        world.getChunkAtAsync(c.cx(), c.cz(), false).whenComplete((chunk, err) -> {
            if (current != c) {
                return; // đã tạm dừng
            }
            pendingLoads = 0;
            String name = chunk == null ? null : choose(world, c);
            if (name == null) {
                skipped++;
                current = null;
                index++;
                return;
            }
            currentName = name;
            radius = RADIUS.getOrDefault(name, 3);
            if (excluded((c.cx() << 4) + 8, (c.cz() << 4) + 8, radius * 16)) {
                skipped++;
                current = null;
                index++;
                return;
            }
            loadAround(world, c);
        });
    }

    /** Bước 2: nạp mọi chunk trong bán kính, giữ lại bằng ticket cho tới khi đặt xong. */
    private void loadAround(World world, Candidate c) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = c.cx() + dx, z = c.cz() + dz;
                if (x < minCx || x > maxCx || z < minCz || z > maxCz) {
                    continue;
                }
                pendingLoads++;
                world.getChunkAtAsync(x, z, false).whenComplete((chunk, err) -> {
                    if (current != c) {
                        return; // đã tạm dừng
                    }
                    pendingLoads--;
                    if (chunk != null) {
                        chunk.addPluginChunkTicket(plugin);
                        held.add(chunk);
                    }
                });
            }
        }
    }

    private void release() {
        for (Chunk chunk : held) {
            chunk.removePluginChunkTicket(plugin);
        }
        held.clear();
    }

    /** Bước 3: chạy lệnh vanilla, đọc thông báo trả về để biết thành công hay lỗi. */
    private void place(World world) {
        Candidate c = current;
        int x = (c.cx() << 4) + 8, z = (c.cz() << 4) + 8;
        String cmd = String.format("execute in minecraft:%s positioned %d 0 %d positioned over motion_blocking_no_leaves run place structure minecraft:%s",
            world.getKey().getKey(), x, z, currentName);
        List<String> feedback = new ArrayList<>();
        boolean[] ok = {false};
        CommandSender capture = Bukkit.createCommandSender(msg -> {
            String key = translationKey(msg);
            if (key != null && key.startsWith("commands.place.structure.success")) {
                ok[0] = true;
            }
            feedback.add(key != null ? key : PlainTextComponentSerializer.plainText().serialize(msg));
        });
        try {
            Bukkit.dispatchCommand(capture, cmd);
        } catch (RuntimeException e) {
            feedback.add("exception: " + e.getMessage());
        }
        String result = String.join(" / ", feedback);
        if (ok[0]) {
            placed++;
            plugin.getLogger().info("Đã đặt " + currentName + " ở " + x + "," + z);
        } else {
            failed++;
            plugin.getLogger().warning("Không đặt được " + currentName + " ở " + x + "," + z + ": " + result);
        }
        try (PrintWriter w = new PrintWriter(new FileWriter(logFile, true))) {
            w.printf("%s,%d,%d,%s,%s%n", currentName, x, z, ok[0] ? "ok" : "fail", result.replace(',', ';'));
        } catch (IOException e) {
            plugin.getLogger().warning("Không ghi được " + logFile.getName() + ": " + e.getMessage());
        }
    }

    private static String translationKey(Component msg) {
        if (msg instanceof TranslatableComponent t) {
            return t.key();
        }
        for (Component child : msg.children()) {
            String key = translationKey(child);
            if (key != null) {
                return key;
            }
        }
        return null;
    }

    private boolean excluded(int x, int z, int margin) {
        long dx = x - excludeX, dz = z - excludeZ, r = excludeRadius + margin;
        return dx * dx + dz * dz < r * r;
    }

    // ------------------------------------------------------------------ chọn công trình

    /** Random theo toạ độ + loại điểm lưới: chạy lại cho cùng kết quả. */
    private String choose(World world, Candidate c) {
        Random r = new Random(((long) c.cx() * 341873128712L) ^ ((long) c.cz() * 132897987541L) ^ c.kind().ordinal());
        int bx = (c.cx() << 4) + 8, bz = (c.cz() << 4) + 8;
        String biome = biomeKey(world.getBiome(bx, world.getHighestBlockYAt(bx, bz), bz));
        boolean ocean = biome.contains("ocean");
        return switch (c.kind()) {
            case SURFACE -> {
                List<String> options = SURFACE_STRUCTURES.get(biome);
                yield options != null && r.nextDouble() < chance ? options.get(r.nextInt(options.size())) : null;
            }
            case MINESHAFT -> !ocean && r.nextDouble() < 0.5 ? "mineshaft" : null;
            case TRIAL -> ocean ? null : "trial_chambers";
            case STRONGHOLD -> ocean ? null : "stronghold";
        };
    }

    @SuppressWarnings("deprecation")
    private static String biomeKey(org.bukkit.block.Biome biome) {
        return biome.getKey().getKey();
    }

    /** Bán kính (chunk) cần nạp quanh điểm đặt; công trình ghép mảnh (làng, phòng thử thách) trải rộng tới ~128 block. */
    private static final Map<String, Integer> RADIUS = Map.ofEntries(
        Map.entry("village_plains", 8), Map.entry("village_taiga", 8), Map.entry("village_snowy", 8),
        Map.entry("village_savanna", 8), Map.entry("village_desert", 8),
        Map.entry("pillager_outpost", 6), Map.entry("trail_ruins", 6), Map.entry("trial_chambers", 8),
        Map.entry("mansion", 6), Map.entry("stronghold", 8), Map.entry("mineshaft", 7), Map.entry("mineshaft_mesa", 7),
        Map.entry("monument", 4));

    /** Công trình mặt đất theo biome (chọn ngẫu nhiên một cái trong danh sách). */
    private static final Map<String, List<String>> SURFACE_STRUCTURES = Map.ofEntries(
        Map.entry("plains", List.of("village_plains", "village_plains", "pillager_outpost", "ruined_portal")),
        Map.entry("sunflower_plains", List.of("village_plains", "pillager_outpost")),
        Map.entry("meadow", List.of("village_plains", "pillager_outpost")),
        Map.entry("forest", List.of("ruined_portal", "trail_ruins")),
        Map.entry("flower_forest", List.of("ruined_portal", "trail_ruins")),
        Map.entry("birch_forest", List.of("trail_ruins", "ruined_portal")),
        Map.entry("dark_forest", List.of("mansion", "ruined_portal")),
        Map.entry("taiga", List.of("village_taiga", "village_taiga", "pillager_outpost", "trail_ruins")),
        Map.entry("old_growth_spruce_taiga", List.of("trail_ruins", "village_taiga")),
        Map.entry("old_growth_pine_taiga", List.of("trail_ruins", "village_taiga")),
        Map.entry("snowy_taiga", List.of("village_snowy", "igloo")),
        Map.entry("snowy_plains", List.of("village_snowy", "igloo", "igloo")),
        Map.entry("savanna", List.of("village_savanna", "village_savanna", "pillager_outpost")),
        Map.entry("savanna_plateau", List.of("village_savanna", "pillager_outpost")),
        Map.entry("desert", List.of("village_desert", "desert_pyramid", "desert_pyramid", "ruined_portal_desert")),
        Map.entry("jungle", List.of("jungle_pyramid", "ruined_portal_jungle")),
        Map.entry("bamboo_jungle", List.of("jungle_pyramid")),
        Map.entry("sparse_jungle", List.of("jungle_pyramid", "ruined_portal_jungle")),
        Map.entry("swamp", List.of("swamp_hut", "ruined_portal_swamp")),
        Map.entry("mangrove_swamp", List.of("swamp_hut")),
        Map.entry("badlands", List.of("mineshaft_mesa", "ruined_portal_desert")),
        Map.entry("wooded_badlands", List.of("mineshaft_mesa")),
        Map.entry("windswept_hills", List.of("ruined_portal_mountain")),
        Map.entry("stony_peaks", List.of("ruined_portal_mountain")),
        Map.entry("warm_ocean", List.of("ocean_ruin_warm", "shipwreck")),
        Map.entry("lukewarm_ocean", List.of("ocean_ruin_warm", "shipwreck")),
        Map.entry("deep_lukewarm_ocean", List.of("ocean_ruin_warm", "shipwreck", "monument")),
        Map.entry("ocean", List.of("shipwreck", "ocean_ruin_cold", "ruined_portal_ocean")),
        Map.entry("deep_ocean", List.of("monument", "shipwreck", "ocean_ruin_cold")),
        Map.entry("cold_ocean", List.of("ocean_ruin_cold", "shipwreck")),
        Map.entry("deep_cold_ocean", List.of("monument", "ocean_ruin_cold")),
        Map.entry("frozen_ocean", List.of("ocean_ruin_cold")),
        Map.entry("beach", List.of("shipwreck_beached", "buried_treasure")));
}
