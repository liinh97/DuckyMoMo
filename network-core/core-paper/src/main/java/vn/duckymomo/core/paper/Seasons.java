package vn.duckymomo.core.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mùa mưa / mùa khô (/mua). Mùa đổi theo NGÀY THẬT (giờ Việt Nam): start-date là ngày bắt đầu mùa đầu tiên trong order,
 * mỗi mùa days-per-season ngày, lặp vòng. Không cần lưu gì: mọi server tính ra cùng một mùa.
 * Mỗi mùa:
 *   growth        hệ số lớn của cây (tính theo khối MỚI mọc ra: WHEAT, CARROTS, SUGAR_CANE, MELON, PUMPKIN, MELON_STEM…).
 *                 < 1: chậm lại (bỏ bớt lần lớn), > 1: nhanh hơn (cây có tuổi được lên thêm 1 bậc). Không ghi = 1.
 *   harvest-bonus % rơi thêm 1 nông sản khi phá cây đã chín (WHEAT, CARROTS, POTATOES, BEETROOTS, COCOA).
 *   rain-start-chance / rain-stop-chance  % mỗi 5 phút: trời đang tạnh thì đổ mưa / đang mưa thì tạnh (cộng thêm thời tiết gốc).
 * Placeholder (cho EvenMoreFish cá theo mùa, TAB…): %dmmmua_id%, %dmmmua_ten%, %dmmmua_con% (số ngày còn lại), %dmmmua_sau% (mùa tới).
 * Sự kiện trong mùa (events.<id>): season, from-day..to-day (ngày thứ mấy của mùa, 1 = ngày đầu, số âm = đếm từ cuối: -1 = ngày cuối),
 *   name, subtitle, rain (on/off/không ghi), growth (nhân thêm), harvest-bonus (cộng thêm %), capture-bonus.<nhóm> (% bắt pet),
 *   supply-multiplier (nhân điểm nông sản khi nộp kho lương). Vd Lũ cuối mùa mưa, Hạn hán giữa mùa khô.
 * Đặc sản vùng (RegionSpecialty) cộng thêm % được mùa qua Seasons.extraHarvest.
 * Admin thử: /mua dat <id> (ghi đè, lưu ở mua.yml) · /mua dat tudong (trả về theo lịch) · /mua sukien <id|tudong>.
 */
final class Seasons implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Map<Material, Material> PRODUCE = new EnumMap<>(Material.class);
    private static final Map<Material, String> NAMES = new EnumMap<>(Material.class);

    static {
        PRODUCE.put(Material.WHEAT, Material.WHEAT);
        PRODUCE.put(Material.CARROTS, Material.CARROT);
        PRODUCE.put(Material.POTATOES, Material.POTATO);
        PRODUCE.put(Material.BEETROOTS, Material.BEETROOT);
        PRODUCE.put(Material.COCOA, Material.COCOA_BEANS);
        NAMES.put(Material.WHEAT, "Lúa mì");
        NAMES.put(Material.CARROTS, "Cà rốt");
        NAMES.put(Material.POTATOES, "Khoai tây");
        NAMES.put(Material.BEETROOTS, "Củ dền");
        NAMES.put(Material.COCOA, "Ca cao");
        NAMES.put(Material.SUGAR_CANE, "Mía");
        NAMES.put(Material.BAMBOO, "Tre");
        NAMES.put(Material.CACTUS, "Xương rồng");
        NAMES.put(Material.MELON, "Dưa hấu");
        NAMES.put(Material.MELON_STEM, "Dây dưa hấu");
        NAMES.put(Material.PUMPKIN, "Bí ngô");
        NAMES.put(Material.PUMPKIN_STEM, "Dây bí ngô");
        NAMES.put(Material.SWEET_BERRY_BUSH, "Dâu rừng");
        NAMES.put(Material.KELP, "Tảo bẹ");
    }

    /** Module đang chạy (null nếu tắt): module khác hỏi mùa qua Seasons.now() / nowName(). */
    static volatile Seasons INSTANCE;

    private final JavaPlugin plugin;
    private final ConfigurationSection cfg;
    private final List<String> order = new ArrayList<>();
    private final File file;
    private final YamlConfiguration data;
    private LocalDate start;
    private String last;

    Seasons(JavaPlugin plugin, ConfigurationSection cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
        ConfigurationSection list = cfg.getConfigurationSection("list");
        for (String id : cfg.getStringList("order")) {
            if (list != null && list.isConfigurationSection(id)) {
                order.add(id);
            } else {
                plugin.getLogger().warning("Mùa: '" + id + "' có trong order nhưng không có trong seasons.list, bỏ qua");
            }
        }
        // YAML có thể đọc 2026-10-12 (không ngoặc kép) thành kiểu ngày thay vì chuỗi
        Object sd = cfg.get("start-date", "2026-10-12");
        try {
            start = sd instanceof java.util.Date d ? d.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    : LocalDate.parse(String.valueOf(sd).trim());
        } catch (DateTimeParseException e) {
            plugin.getLogger().warning("Mùa: start-date sai dạng yyyy-MM-dd, dùng 2026-10-12");
            start = LocalDate.of(2026, 10, 12);
        }
        this.file = new File(plugin.getDataFolder(), "mua.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    boolean ready() {
        return !order.isEmpty();
    }

    void start() {
        last = current();
        // đổi mùa: kiểm tra mỗi phút (mùa đổi lúc 0 giờ)
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkChange, 1200L, 1200L);
        // mưa theo mùa: mỗi 5 phút
        Bukkit.getScheduler().runTaskTimer(plugin, this::weatherTick, 600L, 6000L);
        plugin.getLogger().info("Mùa: bật, hiện là " + plain(name(last)) + " (còn " + daysLeft() + " ngày)");
    }

    static Component c(String s) {
        return Tip.ns(LEGACY.deserialize(s));
    }

    static String plain(String s) {
        return s.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
    }

    private int daysPer() {
        return Math.max(1, cfg.getInt("days-per-season", 7));
    }

    private long daysSinceStart() {
        return ChronoUnit.DAYS.between(start, LocalDate.now(VN));
    }

    /** Mùa hiện tại (id, vd "mua"/"kho") hoặc "" khi module mùa tắt. */
    static String now() {
        Seasons s = INSTANCE;
        return s == null ? "" : s.current();
    }

    /** Sự kiện mùa đang diễn ra (cấu hình events.<id>) hoặc null. */
    static ConfigurationSection eventNow() {
        Seasons s = INSTANCE;
        return s == null ? null : s.event();
    }

    /** Nhiệt độ nền (°) cho Thân nhiệt: đợt đang diễn ra có khoá temperature thì lấy, không thì của mùa (list.<mùa>.temperature), mặc định 25. */
    static double temperature() {
        Seasons s = INSTANCE;
        if (s == null) {
            return 25;
        }
        ConfigurationSection ev = s.event();
        if (ev != null && ev.contains("temperature")) {
            return ev.getDouble("temperature");
        }
        ConfigurationSection se = s.season(s.current());
        return se == null ? 25 : se.getDouble("temperature", 25);
    }

    /** Thêm % được mùa từ nơi khác (đặc sản vùng): (người gặt, cây) -> %. */
    static volatile java.util.function.BiFunction<Player, Material, Double> extraHarvest;

    /** Ngày thứ mấy của mùa (1..daysPer). */
    long dayOfSeason() {
        return Math.floorMod(daysSinceStart(), (long) daysPer()) + 1;
    }

    ConfigurationSection event() {
        ConfigurationSection ev = cfg.getConfigurationSection("events");
        if (ev == null) {
            return null;
        }
        String forcedEv = data.getString("ghi-de-sukien", "");
        if (!forcedEv.isEmpty()) {
            return ev.getConfigurationSection(forcedEv);
        }
        String cur = current();
        long day = dayOfSeason();
        int per = daysPer();
        for (String id : ev.getKeys(false)) {
            ConfigurationSection e = ev.getConfigurationSection(id);
            if (e == null || !cur.equals(e.getString("season"))) {
                continue;
            }
            // 30/10: đợt chỉ để đổi nhiệt (Rét đậm, Nắng nóng: có khoá temperature) không diễn ra khi Thân nhiệt đang tắt
            if (e.contains("temperature") && !plugin.getConfig().getBoolean("body-temp.enabled")) {
                continue;
            }
            int from = e.getInt("from-day", 1);
            int to = e.getInt("to-day", from);
            from = from < 0 ? per + from + 1 : from;
            to = to < 0 ? per + to + 1 : to;
            if (day >= from && day <= to) {
                return e;
            }
        }
        return null;
    }

    static String nowName() {
        Seasons s = INSTANCE;
        return s == null ? "" : s.name(s.current());
    }

    /** Mùa hiện tại (id), tính ghi đè của admin nếu có. */
    String current() {
        String forced = data.getString("ghi-de", "");
        if (!forced.isEmpty() && order.contains(forced)) {
            return forced;
        }
        if (order.isEmpty()) {
            return "";
        }
        int i = (int) Math.floorMod(Math.floorDiv(daysSinceStart(), daysPer()), (long) order.size());
        return order.get(i);
    }

    String next() {
        int i = order.indexOf(current());
        return order.get((i + 1) % order.size());
    }

    /** Số ngày còn lại của mùa (kể cả hôm nay). Đang ghi đè thì không tính. */
    long daysLeft() {
        return daysPer() - Math.floorMod(daysSinceStart(), (long) daysPer());
    }

    private boolean forced() {
        return order.contains(data.getString("ghi-de", ""));
    }

    private ConfigurationSection season(String id) {
        return cfg.getConfigurationSection("list." + id);
    }

    String name(String id) {
        ConfigurationSection s = season(id);
        return s == null ? id : s.getString("name", id);
    }

    private double growth(Material m) {
        ConfigurationSection s = season(current());
        ConfigurationSection e = event();
        return (s == null ? 1.0 : s.getDouble("growth." + m.name(), 1.0)) * (e == null ? 1.0 : e.getDouble("growth." + m.name(), 1.0));
    }

    private String lastEvent = null;

    /** Báo khi sự kiện mùa bắt đầu / kết thúc. */
    private void checkEvent() {
        ConfigurationSection e = event();
        String id = e == null ? "" : e.getName();
        if (lastEvent == null) {
            lastEvent = id;
            return;
        }
        if (id.equals(lastEvent)) {
            return;
        }
        lastEvent = id;
        if (e == null) {
            Bukkit.broadcast(c("&6[Mùa] &fSự kiện mùa đã qua, trời đất trở lại bình thường."));
            return;
        }
        Title t = Title.title(c(e.getString("name", id)), c(e.getString("subtitle", "")),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofSeconds(1)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(t);
        }
        Bukkit.broadcast(c("&6[Mùa] &fBắt đầu " + e.getString("name", id) + "&f: " + e.getString("subtitle", "") + " &7(/mua xem chi tiết)"));
    }

    private void checkChange() {
        checkEvent();
        String now = current();
        if (now.equals(last)) {
            return;
        }
        last = now;
        ConfigurationSection s = season(now);
        String n = name(now);
        Title t = Title.title(c(n), c(s == null ? "" : s.getString("subtitle", "")),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofSeconds(1)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(t);
        }
        Bukkit.broadcast(c("&6[Mùa] &fĐã sang " + n + "&f. Gõ &e/mua &fxem cây nào lớn nhanh, cá nào xuất hiện."));
    }

    private void weatherTick() {
        ConfigurationSection s = season(current());
        if (s == null) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        ConfigurationSection ev = event();
        String rain = ev == null ? "" : ev.getString("rain", "");
        for (String wn : cfg.getStringList("worlds")) {
            World w = Bukkit.getWorld(wn);
            if (w == null) {
                continue;
            }
            if (rain.equalsIgnoreCase("on")) {        // sự kiện ép mưa (lũ)
                if (!w.hasStorm()) {
                    w.setStorm(true);
                }
                w.setWeatherDuration(12000);
                continue;
            }
            if (rain.equalsIgnoreCase("off")) {       // sự kiện ép nắng (hạn)
                w.setStorm(false);
                w.setThundering(false);
                w.setClearWeatherDuration(12000);
                continue;
            }
            if (!w.hasStorm()) {
                if (r.nextDouble(100) < s.getDouble("rain-start-chance", 0)) {
                    w.setStorm(true);
                    w.setWeatherDuration(r.nextInt(6000, 18000));
                }
            } else if (r.nextDouble(100) < s.getDouble("rain-stop-chance", 0)) {
                w.setStorm(false);
                w.setThundering(false);
            }
        }
    }

    private boolean inWorld(World w) {
        List<String> worlds = cfg.getStringList("worlds");
        return worlds.isEmpty() || worlds.contains(w.getName());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent e) {
        grow(e);
    }

    /** Tre, tảo bẹ mọc lên bằng BlockSpreadEvent (danh sách listener riêng, không vào onGrow). */
    @EventHandler(ignoreCancelled = true)
    public void onSpread(org.bukkit.event.block.BlockSpreadEvent e) {
        Material m = e.getNewState().getType();
        if (m == Material.BAMBOO || m == Material.KELP) {
            grow(e);
        }
    }

    private void grow(BlockGrowEvent e) {
        if (!inWorld(e.getBlock().getWorld())) {
            return;
        }
        BlockData nd = e.getNewState().getBlockData();
        Material m = nd.getMaterial();
        double g = growth(m);
        if (m == Material.MELON_STEM || m == Material.PUMPKIN_STEM) {
            // hệ số "quả" (MELON/PUMPKIN) > 1 cũng làm dây lớn nhanh; quả thì thêm sản lượng lúc gặt (onFruit)
            g = Math.max(g, growth(m == Material.MELON_STEM ? Material.MELON : Material.PUMPKIN));
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (g < 1.0) {
            if (r.nextDouble() >= g) {
                e.setCancelled(true);
                if (m == Material.CACTUS || m == Material.SUGAR_CANE) {
                    // khối dưới (đang đếm tuổi) vẫn ở tuổi 15 thì lần random tick sau thử mọc lại ngay -> chậm gần như không có.
                    // Đặt lại tuổi 0 = phải đếm lại cả chu kỳ: chặn với xác suất 1 - g thì trung bình chậm đúng g lần.
                    Block below = e.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (below.getType() == m && below.getBlockData() instanceof Ageable a && a.getAge() > 0) {
                            a.setAge(0);
                            below.setBlockData(a, false);
                        }
                    });
                }
            }
            return;
        }
        if (g <= 1.0) {
            return;
        }
        Block b = e.getBlock();
        switch (m) {
            case CACTUS, SUGAR_CANE -> {
                // gốc: khối trên cùng đếm tuổi 0..15 rồi mới mọc khối mới -> cho khối mới bắt đầu ở tuổi 16 - 16/g = nhanh g lần
                double start = 16 - 16 / g;
                int age = (int) start + (r.nextDouble() < start - (int) start ? 1 : 0);
                if (age > 0) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (b.getType() == m && b.getBlockData() instanceof Ageable a) {
                            a.setAge(Math.min(age, a.getMaximumAge()));
                            b.setBlockData(a, false);
                        }
                    });
                }
            }
            case BAMBOO, KELP -> {
                // tuổi của tre là độ to thân, của tảo bẹ là giới hạn chiều cao: không cộng tuổi mà mọc thêm 1 khối (xác suất g - 1)
                if (r.nextDouble() < g - 1.0) {
                    Bukkit.getScheduler().runTask(plugin, () -> growColumn(b, m));
                }
            }
            default -> {
                if (nd instanceof Ageable && r.nextDouble() < g - 1.0) {
                    // lên thêm 1 bậc ở tick sau (sau khi bậc của sự kiện này đã được đặt)
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (b.getType() == m && b.getBlockData() instanceof Ageable a && a.getAge() < a.getMaximumAge()) {
                            a.setAge(a.getAge() + 1);
                            b.setBlockData(a);
                        }
                    });
                }
            }
        }
    }

    /** Tre / tảo bẹ mọc thêm 1 khối trên ngọn vừa mọc (giữ giới hạn chiều cao như gốc). */
    private static void growColumn(Block top, Material m) {
        if (top.getType() != m) {
            return;
        }
        Block up = top.getRelative(org.bukkit.block.BlockFace.UP);
        if (m == Material.BAMBOO) {
            int h = 1;
            for (Block d = top.getRelative(org.bukkit.block.BlockFace.DOWN); d.getType() == Material.BAMBOO && h < 16; d = d.getRelative(org.bukkit.block.BlockFace.DOWN)) {
                h++;
            }
            if (h < 16 && up.getType() == Material.AIR) {
                up.setBlockData(top.getBlockData(), false);
            }
            return;
        }
        if (!(top.getBlockData() instanceof Ageable a) || a.getAge() >= a.getMaximumAge()
                || up.getType() != Material.WATER || !(up.getBlockData() instanceof org.bukkit.block.data.Levelled l) || l.getLevel() != 0) {
            return;
        }
        Ageable head = (Ageable) Material.KELP.createBlockData();
        head.setAge(a.getAge() + 1);
        up.setBlockData(head, false);
        top.setType(Material.KELP_PLANT, false);
    }

    /** Dưa hấu / bí ngô mọc từ dây (không phải khối người đặt): hệ số mùa > 1 = thêm sản lượng khi gặt (xác suất g - 1 rơi gấp đôi). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFruit(BlockBreakEvent e) {
        Block b = e.getBlock();
        Material m = b.getType();
        if ((m != Material.MELON && m != Material.PUMPKIN) || e.getPlayer().getGameMode() != GameMode.SURVIVAL
                || !inWorld(b.getWorld()) || !e.isDropItems()) {
            return;
        }
        double g = growth(m);
        if (g <= 1.0 || ThreadLocalRandom.current().nextDouble() >= g - 1.0) {
            return;
        }
        Material stem = m == Material.MELON ? Material.ATTACHED_MELON_STEM : Material.ATTACHED_PUMPKIN_STEM;
        boolean grown = false;
        for (org.bukkit.block.BlockFace f : new org.bukkit.block.BlockFace[]{org.bukkit.block.BlockFace.NORTH,
                org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST}) {
            Block n = b.getRelative(f);
            if (n.getType() == stem && n.getBlockData() instanceof org.bukkit.block.data.Directional d && d.getFacing() == f.getOppositeFace()) {
                grown = true;
                break;
            }
        }
        if (grown) {
            ItemStack bonus = m == Material.MELON ? new ItemStack(Material.MELON_SLICE, 3 + ThreadLocalRandom.current().nextInt(5)) : new ItemStack(Material.PUMPKIN, 1);
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), bonus);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(BlockBreakEvent e) {
        Block b = e.getBlock();
        Material produce = PRODUCE.get(b.getType());
        if (produce == null || e.getPlayer().getGameMode() != GameMode.SURVIVAL || !inWorld(b.getWorld())
                || !(b.getBlockData() instanceof Ageable a) || a.getAge() < a.getMaximumAge() || !e.isDropItems()) {
            return;
        }
        ConfigurationSection s = season(current());
        ConfigurationSection ev = event();
        double pct = (s == null ? 0 : s.getDouble("harvest-bonus." + b.getType().name(), 0))
                + (ev == null ? 0 : ev.getDouble("harvest-bonus." + b.getType().name(), 0));
        java.util.function.BiFunction<Player, Material, Double> extra = extraHarvest;
        if (extra != null) {
            Double x = extra.apply(e.getPlayer(), b.getType());
            pct += x == null ? 0 : x;
        }
        if (pct > 0 && ThreadLocalRandom.current().nextDouble(100) < pct) {
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), new ItemStack(produce, 1));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!cfg.getBoolean("join-message", true)) {
            return;
        }
        ConfigurationSection ev = event();
        // 30/10 rút gọn khung chào: một ý, kèm nút lệnh
        JoinNotes.add(e.getPlayer(), 10, c(name(current()) + (forced() ? "" : " &7(còn " + daysLeft() + " ngày)")
                + (ev == null ? "" : " &7· &f" + ev.getString("name", ev.getName())) + " ").append(c("&e[/mua]")
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/mua"))));
    }

    /** Tên Việt của cây/nông sản (vd WHEAT -> lúa). */
    static String cropLabel(Material m) {
        return NAMES.getOrDefault(m, Rewards.vi(m.name()));
    }

    private static String cropName(String key) {
        try {
            Material m = Material.valueOf(key.toUpperCase(Locale.ROOT));
            return NAMES.getOrDefault(m, key.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return key;
        }
    }

    private static String pct(double v) {
        long p = Math.round((v - 1.0) * 100);
        return (p > 0 ? "+" : "") + p + "%";
    }

    boolean command(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("sukien")) {
            if (!sender.hasPermission("duckymomo.mua.admin")) {
                sender.sendMessage(c("&cBạn không có quyền."));
                return true;
            }
            ConfigurationSection evs = cfg.getConfigurationSection("events");
            boolean auto = args[1].equalsIgnoreCase("tudong");
            if (!auto && (evs == null || !evs.isConfigurationSection(args[1]))) {
                sender.sendMessage(c("&e/mua sukien <" + (evs == null ? "" : String.join("|", evs.getKeys(false))) + "|tudong>"));
                return true;
            }
            data.set("ghi-de-sukien", auto ? null : args[1]);
            try {
                data.save(file);
            } catch (IOException ex) {
                plugin.getLogger().warning("Không lưu được mua.yml: " + ex.getMessage());
            }
            checkChange();
            sender.sendMessage(c(auto ? "&aSự kiện mùa: theo lịch." : "&aĐã bật sự kiện " + args[1] + " &7(/mua sukien tudong để theo lịch)"));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("dat")) {
            if (!sender.hasPermission("duckymomo.mua.admin")) {
                sender.sendMessage(c("&cBạn không có quyền đổi mùa."));
                return true;
            }
            if (args.length < 2 || (!order.contains(args[1]) && !args[1].equalsIgnoreCase("tudong"))) {
                sender.sendMessage(c("&e/mua dat <" + String.join("|", order) + "|tudong>"));
                return true;
            }
            data.set("ghi-de", args[1].equalsIgnoreCase("tudong") ? null : args[1]);
            try {
                data.save(file);
            } catch (IOException ex) {
                plugin.getLogger().warning("Không lưu được mua.yml: " + ex.getMessage());
            }
            checkChange();
            sender.sendMessage(c("&aĐã đặt: " + name(current()) + (forced() ? " &7(ghi đè, /mua dat tudong để theo lịch)" : " &7(theo lịch)")));
            return true;
        }
        String id = current();
        ConfigurationSection s = season(id);
        List<String> upM = List.of(), downM = List.of(), bonusM = List.of(), hintsM = List.of();
        if (!(sender instanceof Player)) {
            sender.sendMessage(c("&6━━ " + name(id) + " &6━━"));
        }
        ConfigurationSection evNow = event();
        if (!(sender instanceof Player)) {
            if (evNow != null) {
                sender.sendMessage(c("&d⚡ Sự kiện: " + evNow.getString("name", evNow.getName()) + " &7— " + evNow.getString("subtitle", "")));
            }
            sender.sendMessage(c(forced() ? "&7Admin đang ghi đè mùa (thử nghiệm)."
                    : "&7Còn &f" + daysLeft() + " ngày&7, sau đó sang " + name(next()) + "&7. Mỗi mùa " + daysPer() + " ngày."));
        }
        if (s != null) {
            List<String> up = new ArrayList<>();
            List<String> down = new ArrayList<>();
            // gộp tốc độ lớn của mùa và sự kiện mùa đang diễn ra
            java.util.Set<String> keys = new java.util.LinkedHashSet<>();
            ConfigurationSection gr = s.getConfigurationSection("growth");
            if (gr != null) {
                keys.addAll(gr.getKeys(false));
            }
            ConfigurationSection evg = evNow == null ? null : evNow.getConfigurationSection("growth");
            if (evg != null) {
                keys.addAll(evg.getKeys(false));
            }
            if (!keys.isEmpty()) {
                for (String k : keys) {
                    Material gm = Material.matchMaterial(k);
                    double v = gm == null ? 1.0 : growth(gm);
                    if (v > 1.0) {
                        up.add(cropName(k) + " " + pct(v));
                    } else if (v < 1.0) {
                        down.add(cropName(k) + " " + pct(v));
                    }
                }
            }
            if (sender instanceof Player) {
                upM = up;
                downM = down;
            } else {
                if (!up.isEmpty()) {
                    sender.sendMessage(c("&a▲ Lớn nhanh: &f" + String.join(", ", up)));
                }
                if (!down.isEmpty()) {
                    sender.sendMessage(c("&c▼ Lớn chậm: &f" + String.join(", ", down)));
                }
            }
            // được mùa: mùa + sự kiện cộng lại (giống onHarvest)
            java.util.Map<String, Double> hbm = new java.util.LinkedHashMap<>();
            for (ConfigurationSection hb : new ConfigurationSection[]{s.getConfigurationSection("harvest-bonus"),
                    evNow == null ? null : evNow.getConfigurationSection("harvest-bonus")}) {
                if (hb != null) {
                    for (String k : hb.getKeys(false)) {
                        hbm.merge(k.toUpperCase(Locale.ROOT), hb.getDouble(k, 0), Double::sum);
                    }
                }
            }
            {
                List<String> bonus = new ArrayList<>();
                hbm.forEach((k, v) -> {
                    if (v > 0) {
                        bonus.add(cropName(k) + " " + Math.round(v) + "%");
                    }
                });
                bonusM = bonus;
                if (!bonus.isEmpty() && !(sender instanceof Player)) {
                    sender.sendMessage(c("&e✦ Được mùa (rơi thêm 1 khi gặt cây chín): &f" + String.join(", ", bonus)));
                }
            }
            hintsM = s.getStringList("hints");
            if (!(sender instanceof Player)) {
                for (String line : hintsM) {
                    sender.sendMessage(c(line));
                }
            }
        }
        if (sender instanceof Player pm) {
            menu(pm, id, evNow, upM, downM, bonusM, hintsM);
        }
        return true;
    }

    /** /mua cho người chơi: menu (22/10, trước in chat). */
    private void menu(Player p, String id, ConfigurationSection ev, List<String> up, List<String> down, List<String> bonus, List<String> hints) {
        InfoMenu m = InfoMenu.of("&6" + name(id)).accent(org.bukkit.Material.YELLOW_STAINED_GLASS_PANE);
        Tip top = Tip.of(name(id)).accent("&e").desc("Mùa đổi theo ngày thật, cây lớn và cá cắn câu khác nhau theo mùa.").line();
        if (forced()) {
            top.kv("Lịch", "&dadmin đang ghi đè mùa (thử nghiệm)");
        } else {
            top.kv("Còn", daysLeft() + " ngày").kv("Mùa sau", name(next())).kv("Mỗi mùa", daysPer() + " ngày");
        }
        if (ev != null) {
            top.gap().kv("⚡ Sự kiện", "&d" + ev.getString("name", ev.getName())).note(ev.getString("subtitle", ""));
        }
        m.top(top.apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.SUNFLOWER)));
        Tip a = Tip.of("&a▲ &fCây lớn nhanh").accent("&a");
        if (up.isEmpty()) {
            a.desc("Không có cây nào lớn nhanh hơn thường");
        } else {
            a.items(up);
        }
        m.add(a.apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.WHEAT_SEEDS)));
        Tip b = Tip.of("&c▼ &fCây lớn chậm").accent("&c");
        if (down.isEmpty()) {
            b.desc("Không có cây nào lớn chậm hơn thường");
        } else {
            b.items(down);
        }
        m.add(b.apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.DEAD_BUSH)));
        Tip d = Tip.of("&e✦ &fĐược mùa").accent("&e").desc("Gặt cây chín có tỉ lệ rơi thêm 1");
        if (bonus.isEmpty()) {
            d.line().note("Mùa này không có cây được mùa");
        } else {
            d.line().items(bonus);
        }
        m.add(d.apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.HAY_BLOCK)));
        Tip h = Tip.of("&b✎ &fMẹo mùa này").accent("&b");
        if (hints.isEmpty()) {
            h.desc("Không có mẹo riêng");
        } else {
            hints.forEach(x -> h.item(x.replaceAll("^(&[0-9a-fk-or])+", "")));
        }
        m.add(h.apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.BOOK)));
        m.add(Tip.of("&3≈ &fCá theo mùa").desc("Mỗi mùa có loài cá dễ câu hơn").line().kv("Lệnh", "/emf journal").click("Bấm để xem sổ tay cá")
                .apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.COD)), pl -> { pl.closeInventory(); pl.performCommand("emf journal"); });
        m.open(p);
    }

    List<String> tab(CommandSender sender, String[] a) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("duckymomo.mua.admin")) {
            return out;
        }
        if (a.length == 1) {
            out.add("dat");
            out.add("sukien");
        } else if (a.length == 2 && a[0].equalsIgnoreCase("dat")) {
            out.addAll(order);
            out.add("tudong");
        } else if (a.length == 2 && a[0].equalsIgnoreCase("sukien")) {
            ConfigurationSection evs = cfg.getConfigurationSection("events");
            if (evs != null) {
                out.addAll(evs.getKeys(false));
            }
            out.add("tudong");
        }
        String typed = a[a.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(o -> o.startsWith(typed)).toList();
    }
}
