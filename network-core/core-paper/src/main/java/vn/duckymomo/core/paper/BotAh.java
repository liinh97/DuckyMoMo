package vn.duckymomo.core.paper;

import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Bot mua đồ người chơi treo /ah (30/10, chủ server: "check xem giá hợp lý thì mua").
 * Cứ every-min..every-max phút (× hệ số giả BotFake của người bán: người chơi lâu rồi thì ít, lâu hẳn thì không) xét các món đang treo:
 *  - người bán là người THẬT (31/10: cả người offline, người online ưu tiên trước; offline-sellers: false để tắt), không phải bot
 *    (kể cả bot đang offline: BotView.knownBot), món đã treo ≥ min-age phút, tiền Vault;
 *  - định giá: món Tinh Luyện = giá bán Tinh Luyện; đồ thường TRƠN (không tên, không phù phép, không dữ liệu riêng) = giá /sell trong
 *    Essentials worth.yml; đồ khác (đồ set, đồ thư viện, đồ phù phép, hộp shulker…) không định giá được -> không mua;
 *  - giá hợp lý: min-ratio × giá trị ≤ giá ≤ max-ratio × giá trị (rẻ quá = có thể đặt nhầm, để người thật mua), giá trị ≥ min-value, giá ≤ max-price;
 *  - giới hạn chống in tiền (bán cho server có giới hạn 8.000 xu/ngày, đừng để /ah thành đường lách): mỗi người bán tối đa
 *    per-seller-items món và per-seller-xu xu mỗi ngày; cả server total-xu xu mỗi ngày. Ngày theo giờ VN, lưu botah.yml.
 * Bot mua bằng AuctionManager.purchaseItem (như người thật bấm mua: trừ tiền bot, trả tiền người bán, thuế, lịch sử). Bot thiếu tiền thì
 * được nạp đủ trước. Món đã mua nằm trong mục "đã mua" của bot (zAuctionHouse tự xoá sau 1 tuần).
 * /dmm botah: xem tình trạng; /dmm botah thu: xét và mua ngay một lần (bỏ qua hẹn giờ và hệ số).
 */
final class BotAh {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final JavaPlugin plugin;
    private final ConfigurationSection cfg;
    private final File dataFile;
    private final Map<String, Double> worth = new HashMap<>();
    private long worthAt;
    private String day = "";
    private double total;
    private final Map<UUID, double[]> sellers = new HashMap<>();   // {số món, số xu}
    private long next;
    private BukkitTask task;
    private String last = "chưa chạy";

    BotAh(JavaPlugin plugin, ConfigurationSection cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.dataFile = new File(plugin.getDataFolder(), "botah.yml");
        load();
    }

    static boolean available() {
        Plugin p = Bukkit.getPluginManager().getPlugin("zAuctionHouse");
        return p != null && p.isEnabled();
    }

    void start() {
        schedule();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (System.currentTimeMillis() >= next) {
                schedule();
                last = runOnce(false);
                if (!last.startsWith("bỏ lượt") && !last.startsWith("không")) {
                    plugin.getLogger().info("Bot mua /ah: " + last);
                }
            }
        }, 20 * 60L, 20 * 30L);
    }

    void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    private void schedule() {
        double lo = cfg.getDouble("every-min", 20), hi = Math.max(lo, cfg.getDouble("every-max", 60));
        next = System.currentTimeMillis() + (long) ((lo + ThreadLocalRandom.current().nextDouble() * (hi - lo)) * 60_000);
    }

    // ---------------------------------------------------------------- định giá

    private void loadWorth() {
        if (System.currentTimeMillis() - worthAt < 3_600_000 && !worth.isEmpty()) {
            return;
        }
        worthAt = System.currentTimeMillis();
        worth.clear();
        File f = new File(plugin.getDataFolder().getParentFile(), "Essentials/worth.yml");
        ConfigurationSection w = YamlConfiguration.loadConfiguration(f).getConfigurationSection("worth");
        if (w != null) {
            for (String k : w.getKeys(false)) {
                if (w.isDouble(k) || w.isInt(k)) {
                    worth.put(k.toLowerCase(Locale.ROOT).replace("_", ""), w.getDouble(k));
                }
            }
        }
    }

    /** Giá trị một chồng đồ (xu), -1 = không định giá được. */
    private double value(ItemStack it) {
        if (it == null || it.getType().isAir()) {
            return 0;
        }
        Refining r = Shared.refining;
        String tl = r == null ? null : r.idOf(it);
        if (tl != null) {
            double s = r.sellOf(tl);
            return s > 0 ? s * it.getAmount() : -1;
        }
        if (ItemLibrary.idOf(it) != null || !it.isSimilar(new ItemStack(it.getType()))) {
            return -1;   // đồ thư viện / có tên, phù phép, dữ liệu riêng
        }
        Double w = worth.get(it.getType().name().toLowerCase(Locale.ROOT).replace("_", ""));
        return w == null || w <= 0 ? -1 : w * it.getAmount();
    }

    private double value(Item item) {
        if (!(item instanceof AuctionItem ai)) {
            return -1;
        }
        double v = 0;
        for (ItemStack s : ai.getItemStacks()) {
            double x = value(s);
            if (x < 0) {
                return -1;
            }
            v += x;
        }
        return v;
    }

    // ---------------------------------------------------------------- mua

    String runOnce(boolean force) {
        if (!available()) {
            return "không có zAuctionHouse";
        }
        int h = java.time.LocalTime.now(VN).getHour();
        if (!force && h >= 1 && h < 7) {
            return "bỏ lượt: đêm";
        }
        rollDay();
        loadWorth();
        AuctionManager am = ((AuctionPlugin) Bukkit.getPluginManager().getPlugin("zAuctionHouse")).getAuctionManager();
        List<Player> bots = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (BotView.isBot(p)) {
                bots.add(p);
            }
        }
        if (bots.isEmpty()) {
            return "không có bot online";
        }
        double maxRatio = cfg.getDouble("max-ratio", 1.5), minRatio = cfg.getDouble("min-ratio", 0.5);
        double minValue = cfg.getDouble("min-value", 50), maxPrice = cfg.getDouble("max-price", 5000);
        double totalCap = cfg.getDouble("total-xu", 15000), sellerXu = cfg.getDouble("per-seller-xu", 3000);
        int sellerItems = cfg.getInt("per-seller-items", 2);
        long minAge = cfg.getLong("min-age-minutes", 15) * 60_000L;
        long now = System.currentTimeMillis();
        List<Object[]> ok = new ArrayList<>();   // {Item, Player seller, value}
        int seen = 0;
        for (Item it : am.getItems(StorageType.LISTED)) {
            if (it.isExpired() || !"vault".equalsIgnoreCase(it.getEconomyName())) {
                continue;
            }
            // 31/10 chủ server: mua cả của người OFFLINE (người online vẫn được ưu tiên); bỏ món của bot (kể cả bot đang offline)
            java.util.UUID sid = it.getSellerUniqueId();
            if (sid == null || BotView.knownBot(sid)) {
                continue;
            }
            org.bukkit.OfflinePlayer seller = Bukkit.getOfflinePlayer(sid);
            boolean online = seller.isOnline();
            if (!online && !cfg.getBoolean("offline-sellers", true)) {
                continue;
            }
            seen++;
            double price = it.getPrice().doubleValue();
            if (it.getCreatedAt() == null || now - it.getCreatedAt().getTime() < minAge || price > maxPrice || total + price > totalCap) {
                continue;
            }
            double[] s = sellers.getOrDefault(sid, new double[2]);
            if (s[0] >= sellerItems || s[1] + price > sellerXu) {
                continue;
            }
            double v = value(it);
            if (v < minValue || price > v * maxRatio || price < v * minRatio) {
                continue;
            }
            if (!force && ThreadLocalRandom.current().nextDouble() >= BotFake.factor(seller)) {
                continue;   // người bán chơi lâu rồi: ít mua của họ
            }
            ok.add(new Object[] {it, seller, v, online});
        }
        if (ok.isEmpty()) {
            return seen == 0 ? "không có món nào của người thật đang treo" : "bỏ lượt: " + seen + " món đang treo, không món nào giá hợp lý / còn hạn mức";
        }
        List<Object[]> on = ok.stream().filter(o -> (boolean) o[3]).toList();   // người online ưu tiên trước
        List<Object[]> from = on.isEmpty() ? ok : on;
        Object[] pick = from.get(ThreadLocalRandom.current().nextInt(from.size()));
        Item it = (Item) pick[0];
        org.bukkit.OfflinePlayer seller = (org.bukkit.OfflinePlayer) pick[1];
        Player bot = bots.get(ThreadLocalRandom.current().nextInt(bots.size()));
        double price = it.getPrice().doubleValue();
        if (VaultEconomy.available() && VaultEconomy.balance(bot) < price) {
            VaultEconomy.deposit(bot, price - VaultEconomy.balance(bot) + ThreadLocalRandom.current().nextInt(200, 2000));
        }
        double[] s = sellers.computeIfAbsent(sid(seller), k -> new double[2]);
        s[0]++;
        s[1] += price;
        total += price;
        save();
        String what = it.getAmount() + " " + it.getTranslationKey();
        am.purchaseItem(bot, it).whenComplete((v, ex) -> {
            if (ex != null) {
                plugin.getLogger().warning("Bot mua /ah lỗi (" + bot.getName() + " mua của " + seller.getName() + "): " + ex);
            }
        });
        return bot.getName() + " mua của " + seller.getName() + ": " + what + " giá " + Rewards.xu(price) + " (giá trị ~" + Rewards.xu((double) pick[2])
                + "; hôm nay cả server " + Rewards.xu(total) + ")";
    }

    private static java.util.UUID sid(org.bukkit.OfflinePlayer p) {
        return p.getUniqueId();
    }

    void command(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("thu")) {
            last = runOnce(true);
            sender.sendMessage("Bot mua /ah: " + last);
            return;
        }
        rollDay();
        sender.sendMessage("Bot mua /ah: hôm nay " + Rewards.xu(total) + " / " + Rewards.xu(cfg.getDouble("total-xu", 15000)) + ", " + sellers.size()
                + " người bán; lần xét kế ~" + Math.max(0, (next - System.currentTimeMillis()) / 60000) + " phút; lần trước: " + last);
    }

    // ---------------------------------------------------------------- lưu theo ngày

    private void rollDay() {
        String d = LocalDate.now(VN).toString();
        if (!d.equals(day)) {
            day = d;
            total = 0;
            sellers.clear();
            save();
        }
    }

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        day = y.getString("day", "");
        total = y.getDouble("total", 0);
        ConfigurationSection s = y.getConfigurationSection("sellers");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                try {
                    sellers.put(UUID.fromString(k), new double[] {s.getDouble(k + ".mon"), s.getDouble(k + ".xu")});
                } catch (IllegalArgumentException ignored) {
                    // khoá hỏng: bỏ
                }
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("day", day);
        y.set("total", total);
        sellers.forEach((u, v) -> {
            y.set("sellers." + u + ".mon", v[0]);
            y.set("sellers." + u + ".xu", v[1]);
        });
        try {
            y.save(dataFile);
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Không lưu được botah.yml: " + e.getMessage());
        }
    }
}
