package vn.duckymomo.core.paper;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
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
 * Admin thử: /mua dat <id> (ghi đè, lưu ở mua.yml) · /mua dat tudong (trả về theo lịch).
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

    private static Component c(String s) {
        return LEGACY.deserialize(s);
    }

    private static String plain(String s) {
        return s.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
    }

    private int daysPer() {
        return Math.max(1, cfg.getInt("days-per-season", 7));
    }

    private long daysSinceStart() {
        return ChronoUnit.DAYS.between(start, LocalDate.now(VN));
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

    private String next() {
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
        return s == null ? 1.0 : s.getDouble("growth." + m.name(), 1.0);
    }

    private void checkChange() {
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
        for (String wn : cfg.getStringList("worlds")) {
            World w = Bukkit.getWorld(wn);
            if (w == null) {
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
        if (!inWorld(e.getBlock().getWorld())) {
            return;
        }
        BlockData nd = e.getNewState().getBlockData();
        Material m = nd.getMaterial();
        double g = growth(m);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (g < 1.0) {
            if (r.nextDouble() >= g) {
                e.setCancelled(true);
            }
            return;
        }
        if (g > 1.0 && nd instanceof Ageable && r.nextDouble() < g - 1.0) {
            Block b = e.getBlock();
            // lên thêm 1 bậc ở tick sau (sau khi bậc của sự kiện này đã được đặt)
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (b.getType() == m && b.getBlockData() instanceof Ageable a && a.getAge() < a.getMaximumAge()) {
                    a.setAge(a.getAge() + 1);
                    b.setBlockData(a);
                }
            });
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
        double pct = s == null ? 0 : s.getDouble("harvest-bonus." + b.getType().name(), 0);
        if (pct > 0 && ThreadLocalRandom.current().nextDouble(100) < pct) {
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), new ItemStack(produce, 1));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!cfg.getBoolean("join-message", true)) {
            return;
        }
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                p.sendMessage(c("&6[Mùa] &fĐang là " + name(current()) + (forced() ? "" : " &7(còn " + daysLeft() + " ngày)")
                        + "&f. Gõ &e/mua &fđể xem chi tiết."));
            }
        }, 60L);
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
        sender.sendMessage(c("&6&l━━ " + name(id) + " &6&l━━"));
        sender.sendMessage(c(forced() ? "&7Admin đang ghi đè mùa (thử nghiệm)."
                : "&7Còn &f" + daysLeft() + " ngày&7, sau đó sang " + name(next()) + "&7. Mỗi mùa " + daysPer() + " ngày."));
        if (s != null) {
            List<String> up = new ArrayList<>();
            List<String> down = new ArrayList<>();
            ConfigurationSection gr = s.getConfigurationSection("growth");
            if (gr != null) {
                for (String k : gr.getKeys(false)) {
                    double v = gr.getDouble(k, 1.0);
                    if (v > 1.0) {
                        up.add(cropName(k) + " " + pct(v));
                    } else if (v < 1.0) {
                        down.add(cropName(k) + " " + pct(v));
                    }
                }
            }
            if (!up.isEmpty()) {
                sender.sendMessage(c("&a▲ Lớn nhanh: &f" + String.join(", ", up)));
            }
            if (!down.isEmpty()) {
                sender.sendMessage(c("&c▼ Lớn chậm: &f" + String.join(", ", down)));
            }
            ConfigurationSection hb = s.getConfigurationSection("harvest-bonus");
            if (hb != null) {
                List<String> bonus = new ArrayList<>();
                for (String k : hb.getKeys(false)) {
                    if (hb.getDouble(k, 0) > 0) {
                        bonus.add(cropName(k) + " " + Math.round(hb.getDouble(k)) + "%");
                    }
                }
                if (!bonus.isEmpty()) {
                    sender.sendMessage(c("&e✦ Được mùa (rơi thêm 1 khi gặt cây chín): &f" + String.join(", ", bonus)));
                }
            }
            for (String line : s.getStringList("hints")) {
                sender.sendMessage(c(line));
            }
        }
        return true;
    }

    List<String> tab(CommandSender sender, String[] a) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("duckymomo.mua.admin")) {
            return out;
        }
        if (a.length == 1) {
            out.add("dat");
        } else if (a.length == 2 && a[0].equalsIgnoreCase("dat")) {
            out.addAll(order);
            out.add("tudong");
        }
        String typed = a[a.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(o -> o.startsWith(typed)).toList();
    }

    PlaceholderExpansion placeholders() {
        return new PlaceholderExpansion() {
            @Override
            public String getIdentifier() {
                return "dmmmua";
            }

            @Override
            public String getAuthor() {
                return "DuckyMoMo";
            }

            @Override
            public String getVersion() {
                return "1";
            }

            @Override
            public boolean persist() {
                return true;
            }

            @Override
            public String onRequest(OfflinePlayer player, String params) {
                return switch (params) {
                    case "id" -> current();
                    case "ten" -> LegacyComponentSerializer.legacySection().serialize(c(name(current())));
                    case "ten_tron" -> plain(name(current()));
                    case "con" -> String.valueOf(daysLeft());
                    case "sau" -> plain(name(next()));
                    default -> null;
                };
            }
        };
    }
}
