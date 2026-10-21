package vn.duckymomo.core.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Giới hạn tiền bán đồ cho server mỗi người mỗi ngày (21/10, chống lạm phát: farm mía / máy sắt / máy vàng chạy AFK in tiền).
 * Tính cả 3 đường bán cho server: /sell của Essentials (cả nút bán ở /cuahang), Rìu Bán của Xưởng, bán món Tinh Luyện.
 * Bán cho người chơi (/choden, shop rương, /trade) KHÔNG tính.
 *
 * Cấu hình sell-limit: enabled, daily (xu/người/ngày; ngày theo giờ Việt Nam, mới lại lúc 0 giờ). Quyền bỏ qua: duckymomo.sell.khonggioihan.
 * Đặc quyền Thăng Cấp: duckymomo.sell.them.<n> (n = 1…9) cộng thêm n × extra-per-perk (mặc định 1.000) xu/ngày, lấy n lớn nhất.
 * Đếm: số xu thật nhận được (UserBalanceUpdateEvent COMMAND_SELL ở MONITOR, đã gồm thưởng bán ở kinh đô) + record() từ Xưởng / Tinh Luyện.
 * Chặn TRƯỚC khi bán: ước lượng giá trị lần bán theo worth.yml (+ giá món Tinh Luyện); vượt phần còn lại thì không bán gì và báo còn bao nhiêu
 * (không bao giờ lấy đồ mà không trả tiền). Lưu: banchoserver.yml (chỉ giữ ngày hôm nay).
 */
final class SellLimit implements Listener {

    static final String BYPASS = "duckymomo.sell.khonggioihan";
    private static final List<String> SELL_CMDS = List.of("sell", "esell", "essentials:sell", "essentials:esell");
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final JavaPlugin plugin;
    private final double daily;
    private final double extraPerPerk;
    private final File file;
    private final File worthFile;
    private final Map<UUID, Double> sold = new HashMap<>();
    private final Map<Material, Double> worth = new HashMap<>();
    private long worthStamp = -1;
    private String day;
    private boolean dirty;

    SellLimit(JavaPlugin plugin, ConfigurationSection c) {
        this.plugin = plugin;
        this.daily = Math.max(0, c.getDouble("daily", 8000));
        this.extraPerPerk = Math.max(0, c.getDouble("extra-per-perk", 1000));
        this.file = new File(plugin.getDataFolder(), "banchoserver.yml");
        this.worthFile = new File(plugin.getDataFolder().getParentFile(), "Essentials/worth.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        this.day = y.getString("ngay", today());
        ConfigurationSection s = y.getConfigurationSection("ban");
        if (s != null && day.equals(today())) {
            for (String k : s.getKeys(false)) {
                try {
                    sold.put(UUID.fromString(k), s.getDouble(k));
                } catch (IllegalArgumentException ignored) {
                    // khoá hỏng: bỏ
                }
            }
        }
    }

    void start() {
        hookEssentials();
        Bukkit.getScheduler().runTaskTimer(plugin, this::flush, 600L, 600L);
        Shared.sellLimit = this;
        plugin.getLogger().info("Giới hạn bán cho server: " + Rewards.xu(daily) + "/người/ngày");
    }

    double daily() {
        return daily;
    }

    /** Giới hạn của riêng người này: daily + bậc đặc quyền Thăng Cấp. */
    double limit(Player p) {
        for (int n = 9; n >= 1; n--) {
            if (p.hasPermission("duckymomo.sell.them." + n)) {
                return daily + n * extraPerPerk;
            }
        }
        return daily;
    }

    /** Số xu còn được bán cho server hôm nay (người có quyền bỏ qua: vô hạn). */
    double remaining(Player p) {
        if (p.hasPermission(BYPASS)) {
            return Double.MAX_VALUE;
        }
        rollDay();
        return Math.max(0, limit(p) - sold.getOrDefault(p.getUniqueId(), 0.0));
    }

    double soldToday(Player p) {
        rollDay();
        return sold.getOrDefault(p.getUniqueId(), 0.0);
    }

    /** Hỏi trước khi bán: estimate = số xu lần bán này dự kiến nhận. false = không được bán (đã báo người chơi). */
    boolean allow(Player p, double estimate) {
        double left = remaining(p);
        if (left == Double.MAX_VALUE) {
            return true;
        }
        if (left <= 0.0001) {
            p.sendMessage(c("&cHôm nay bạn đã bán cho server đủ &e" + Rewards.xu(limit(p)) + "&c (giới hạn mỗi ngày, mới lại lúc 0 giờ)."));
            p.sendMessage(c("&7Vẫn bán cho người chơi được: &f/choden&7, shop rương, &f/trade&7."));
            return false;
        }
        if (estimate > left + 0.0001) {
            p.sendMessage(c("&cLần bán này khoảng &e" + Rewards.xu(estimate) + "&c, hôm nay chỉ còn bán được &e" + Rewards.xu(left)
                    + "&c cho server (giới hạn " + Rewards.xu(limit(p)) + "/ngày)."));
            p.sendMessage(c("&7Bán bớt số lượng (vd &f/sell <tên món> 16&7), hoặc bán cho người chơi ở &f/choden&7."));
            return false;
        }
        return true;
    }

    /** Ghi số xu vừa nhận từ việc bán cho server (Xưởng, Tinh Luyện; /sell tự ghi qua sự kiện Essentials). */
    void record(Player p, double amount) {
        if (amount <= 0 || p.hasPermission(BYPASS)) {
            return;
        }
        rollDay();
        double before = sold.getOrDefault(p.getUniqueId(), 0.0);
        double after = before + amount;
        sold.put(p.getUniqueId(), after);
        dirty = true;
        double lim = limit(p);
        if (before < lim * 0.8 && after >= lim * 0.8) {
            p.sendMessage(c("&eHôm nay bạn đã bán cho server " + Rewards.xu(after) + " / " + Rewards.xu(lim)
                    + "&e. Đủ mức thì phải chờ sang ngày mới bán tiếp cho server."));
        }
    }

    // ---------------------------------------------------------------- /sell của Essentials

    /** Chặn trước: ước lượng giá trị lần /sell. Chạy trước Tinh Luyện (LOW) để chặn cả phần món tinh luyện. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSellCommand(PlayerCommandPreprocessEvent e) {
        String[] a = e.getMessage().substring(1).trim().split("\\s+");
        if (a.length < 2 || !SELL_CMDS.contains(a[0].toLowerCase(Locale.ROOT))) {
            return;
        }
        Player p = e.getPlayer();
        if (remaining(p) == Double.MAX_VALUE) {
            return;
        }
        if (!allow(p, estimate(p, a))) {
            e.setCancelled(true);
        }
    }

    /** Giá trị dự kiến của "/sell <hand|all|inventory|invent|blocks|tên vật phẩm> [số]". Không đoán được thì 0 (bán xong vẫn được đếm). */
    double estimate(Player p, String[] a) {
        String what = a[1].toLowerCase(Locale.ROOT);
        Integer amount = null;
        if (a.length > 2) {
            try {
                amount = Integer.parseInt(a[2]);
            } catch (NumberFormatException ignored) {
                // Essentials sẽ báo sai cú pháp
            }
        }
        ItemStack[] inv = p.getInventory().getStorageContents();
        switch (what) {
            case "hand" -> {
                ItemStack h = p.getInventory().getItemInMainHand();
                if (h.getType().isAir()) {
                    return 0;
                }
                // Essentials: "hand" bán MỌI món giống món đang cầm trong túi (không ghi số = hết)
                return unitPrice(h) * pick(countLike(inv, h), amount);
            }
            case "all", "inventory", "invent", "blocks" -> {
                double total = 0;
                for (ItemStack s : inv) {
                    if (s != null && !s.getType().isAir() && (!what.equals("blocks") || s.getType().isBlock())) {
                        total += unitPrice(s) * s.getAmount();
                    }
                }
                return total;
            }
            default -> {
                Material m = Material.matchMaterial(what);
                if (m == null) {
                    return 0;
                }
                ItemStack plain = new ItemStack(m);
                return unitPrice(plain) * pick(countLike(inv, plain), amount);
            }
        }
    }

    /** Giá bán 1 cái: món Tinh Luyện theo giá riêng, đồ có tên / phù phép = 0 (Essentials không bán), còn lại theo worth.yml. */
    double unitPrice(ItemStack s) {
        Refining r = Shared.refining;
        if (r != null) {
            double v = r.priceOf(s);
            if (v >= 0) {
                return v;
            }
        }
        if (s.hasItemMeta() && !s.isSimilar(new ItemStack(s.getType()))) {
            return 0;
        }
        loadWorth();
        return worth.getOrDefault(s.getType(), 0.0);
    }

    private static int countLike(ItemStack[] inv, ItemStack like) {
        int n = 0;
        for (ItemStack s : inv) {
            if (s != null && s.isSimilar(like)) {
                n += s.getAmount();
            }
        }
        return n;
    }

    /** Số lượng Essentials sẽ bán: không ghi số = hết; số dương = tối đa ngần ấy; số âm = giữ lại ngần ấy. */
    private static int pick(int have, Integer amount) {
        if (amount == null) {
            return have;
        }
        return amount >= 0 ? Math.min(have, amount) : Math.max(0, have + amount);
    }

    private void loadWorth() {
        long stamp = worthFile.lastModified();
        if (stamp == worthStamp) {
            return;
        }
        worthStamp = stamp;
        worth.clear();
        ConfigurationSection w = YamlConfiguration.loadConfiguration(worthFile).getConfigurationSection("worth");
        if (w == null) {
            return;
        }
        for (String k : w.getKeys(false)) {
            Material m = Material.matchMaterial(k);
            if (m != null && !w.isConfigurationSection(k)) {
                worth.put(m, w.getDouble(k));
            }
        }
    }

    /** Đếm tiền /sell thật nhận được (sau thưởng kinh đô) qua UserBalanceUpdateEvent của Essentials (reflection như PeaceAlliance). */
    @SuppressWarnings("unchecked")
    private void hookEssentials() {
        Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
        if (ess == null || !ess.isEnabled()) {
            plugin.getLogger().warning("Giới hạn bán: không có Essentials, chỉ tính Xưởng và Tinh Luyện");
            return;
        }
        try {
            Class<? extends Event> cls = (Class<? extends Event>) Class.forName("net.ess3.api.events.UserBalanceUpdateEvent", true, ess.getClass().getClassLoader());
            Method cause = cls.getMethod("getCause");
            Method player = cls.getMethod("getPlayer");
            Method oldB = cls.getMethod("getOldBalance");
            Method newB = cls.getMethod("getNewBalance");
            Bukkit.getPluginManager().registerEvent(cls, this, EventPriority.MONITOR, (l, ev) -> {
                if (!cls.isInstance(ev)) {
                    return;
                }
                try {
                    if (!"COMMAND_SELL".equals(String.valueOf(cause.invoke(ev)))) {
                        return;
                    }
                    Player p = (Player) player.invoke(ev);
                    BigDecimal gain = ((BigDecimal) newB.invoke(ev)).subtract((BigDecimal) oldB.invoke(ev));
                    if (p != null && gain.signum() > 0) {
                        record(p, gain.doubleValue());
                    }
                } catch (ReflectiveOperationException ex) {
                    plugin.getLogger().warning("Giới hạn bán: đếm tiền /sell lỗi: " + ex);
                }
            }, plugin, true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Giới hạn bán: không gắn được /sell của Essentials: " + e);
        }
    }

    // ---------------------------------------------------------------- lưu

    private static String today() {
        return LocalDate.now(VN).toString();
    }

    private void rollDay() {
        String t = today();
        if (!t.equals(day)) {
            day = t;
            sold.clear();
            dirty = true;
        }
    }

    void flush() {
        rollDay();
        if (!dirty) {
            return;
        }
        YamlConfiguration y = new YamlConfiguration();
        y.set("ngay", day);
        sold.forEach((id, v) -> y.set("ban." + id, Math.round(v * 100) / 100.0));
        try {
            y.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("Giới hạn bán: không lưu được " + file.getName() + ": " + e.getMessage());
        }
    }

    private static Component c(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }
}
