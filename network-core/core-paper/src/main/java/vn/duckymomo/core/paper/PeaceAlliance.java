package vn.duckymomo.core.paper;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.event.NationAddTownEvent;
import com.palmergames.bukkit.towny.event.NationRemoveTownEvent;
import com.palmergames.bukkit.towny.event.NewTownEvent;
import com.palmergames.bukkit.towny.event.TownBlockClaimCostCalculationEvent;
import com.palmergames.bukkit.towny.event.TownPreClaimEvent;
import com.palmergames.bukkit.towny.event.TownUpkeepCalculationEvent;
import com.palmergames.bukkit.towny.event.town.toggle.TownToggleNeutralEvent;
import com.palmergames.bukkit.towny.event.town.toggle.TownTogglePVPEvent;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.WorldCoord;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;

/**
 * Liên minh Hoà Bình (chốt 14/10, docs/banghoi-luat-mua1.md mục 10–11): quốc gia duy nhất được hoà bình, do server quản lý,
 * thủ đô là kinh đô ở spawn (thị trấn NPC, không tính vào giá đất). Đợt 2 gồm:
 *  - vào/rời bằng /hoabinh (thị trưởng); vào chờ join-wait-days mới hoà bình, rời mất ngay, rejoin-cooldown-days mới vào lại;
 *    không ai tự bật hoà bình (/t toggle peaceful bị chặn); plugin bật/tắt qua lệnh admin của SiegeWar;
 *  - đất lan liền kề (không tiền đồn), giá ô = plot-base-price + plot-price-per-plot × tổng ô của liên minh, phí bảo hộ theo ô/ngày;
 *    lập thị trấn trong vòng spawn phải sát đất hoà bình và tự vào liên minh; đất hoà bình luôn tắt PvP;
 *  - chợ: /ah chỉ mở trong đất hoà bình, /sell trong kinh đô +sell-bonus-percent%, shop rương trong đất hoà bình miễn thuế;
 *  - báo vây thành: toàn server + dân hoà bình có đất trong vùng vây (tin + thư), nhắc trước ngày công thành và trước mỗi phiên.
 */
final class PeaceAlliance implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final DuckyMoMoCore plugin;
    private final String nationName;
    private final String capitalName;
    private final int joinWaitDays;
    private final int rejoinCooldownDays;
    private final double plotBase;
    private final double plotPerPlot;
    private final double feePerPlot;
    private final double sellBonus;
    private final double shopTaxInside;
    private final double shopTaxOutside;
    private final boolean auctionOnlyInPeace;
    private final Set<String> auctionCommands = new HashSet<>();
    private final double warnDaysBefore;
    private final List<Integer> remindMinutes;
    private final File file;
    private final YamlConfiguration data;
    private final Set<String> autoJoin = new HashSet<>();
    private int siegeRadius = -1;

    PeaceAlliance(DuckyMoMoCore plugin, ConfigurationSection c) {
        this.plugin = plugin;
        this.nationName = c.getString("nation", "LienMinhHoaBinh");
        this.capitalName = c.getString("capital", "KinhDo");
        this.joinWaitDays = c.getInt("join-wait-days", 2);
        this.rejoinCooldownDays = c.getInt("rejoin-cooldown-days", 14);
        this.plotBase = c.getDouble("plot-base-price", 25);
        this.plotPerPlot = c.getDouble("plot-price-per-plot", 0.5);
        this.feePerPlot = c.getDouble("protection-fee-per-plot", 0.5);
        this.sellBonus = c.getDouble("sell-bonus-percent", 20);
        this.shopTaxInside = c.getDouble("shop-tax-inside", 0);
        this.shopTaxOutside = c.getDouble("shop-tax-outside", 0.05);
        this.auctionOnlyInPeace = c.getBoolean("auction-only-in-peace", true);
        for (String s : c.getStringList("auction-commands")) {
            auctionCommands.add(s.toLowerCase(Locale.ROOT));
        }
        this.warnDaysBefore = c.getDouble("siege-warn-days-before", 2);
        this.remindMinutes = c.getIntegerList("siege-remind-minutes");
        this.file = new File(plugin.getDataFolder(), "hoabinh.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    String nationName() {
        return nationName;
    }

    String capitalName() {
        return capitalName;
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 400L, 1200L);
        hookSellBonus();
        hookShopTax();
        plugin.getLogger().info("Liên minh Hoà Bình: bật (quốc gia " + nationName + ", kinh đô " + capitalName
                + (nation() == null ? ", CHƯA TẠO: đứng ở spawn gõ /dmm hoabinh setup" : "") + ")");
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s);
    }

    private static void console(String cmd) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    // ------------------------------------------------------------------ tra cứu

    Nation nation() {
        return TownyUniverse.getInstance().getNation(nationName);
    }

    boolean isMember(Town t) {
        Nation n = t == null ? null : t.getNationOrNull();
        return n != null && n.getName().equalsIgnoreCase(nationName);
    }

    private boolean isCapital(Town t) {
        return t != null && t.getName().equalsIgnoreCase(capitalName);
    }

    /** Đang đứng trong đất kinh đô (thị trấn thủ đô của liên minh). */
    boolean inCapital(Location l) {
        return isCapital(TownyAPI.getInstance().getTown(l));
    }

    boolean inPeaceLand(Location l) {
        return isMember(TownyAPI.getInstance().getTown(l));
    }

    private int totalPlots() {
        Nation n = nation();
        return n == null ? 0 : n.getTowns().stream().mapToInt(Town::getNumTownBlocks).sum();
    }

    /** Tổng ô của các thị trấn hoà bình (không tính kinh đô của server) - dùng tính giá mua đất. */
    private int memberPlots() {
        Nation n = nation();
        return n == null ? 0 : n.getTowns().stream().filter(t -> !isCapital(t)).mapToInt(Town::getNumTownBlocks).sum();
    }

    /** Ô (x, z) theo toạ độ Towny có chung cạnh với đất của liên minh (không tính chính thị trấn except). */
    boolean adjacentToPeace(String world, int x, int z, Town except) {
        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] o : d) {
            TownBlock tb = new WorldCoord(world, x + o[0], z + o[1]).getTownBlockOrNull();
            Town t = tb == null ? null : tb.getTownOrNull();
            if (t != null && t != except && isMember(t)) {
                return true;
            }
        }
        return false;
    }

    private boolean townTouchesPeace(Town t) {
        for (TownBlock tb : t.getTownBlocks()) {
            if (adjacentToPeace(tb.getWorld().getName(), tb.getX(), tb.getZ(), t)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ /hoabinh

    boolean command(CommandSender s, String[] args) {
        String sub = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "";
        Nation n = nation();
        if (n == null) {
            s.sendMessage(c("&cLiên minh Hoà Bình chưa được lập. Báo admin."));
            return true;
        }
        if (sub.equals("danhsach")) {
            s.sendMessage(c("&a&lLiên minh Hoà Bình &7(" + n.getTowns().size() + " thị trấn, " + totalPlots() + " ô):"));
            for (Town t : n.getTowns()) {
                s.sendMessage(c("&f" + t.getName() + " &7- " + t.getResidents().size() + " dân, " + t.getNumTownBlocks() + " ô"
                        + (t.isNeutral() ? " &a(đang hoà bình)" : " &e(chờ hiệu lực)")));
            }
            return true;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("/hoabinh danhsach");
            return true;
        }
        Resident r = TownyAPI.getInstance().getResident(p);
        Town t = r == null ? null : r.getTownOrNull();
        if (sub.equals("vao")) {
            String err = joinProblem(r, t);
            if (err != null) {
                p.sendMessage(c("&c" + err));
                return true;
            }
            data.set("vao." + t.getUUID(), System.currentTimeMillis());
            save();
            console("ta nation " + nationName + " add " + t.getName());
            p.sendMessage(c("&aĐã đưa &f" + t.getName() + " &avào Liên minh Hoà Bình. Hoà bình có hiệu lực sau &f" + joinWaitDays
                    + " ngày&a (trong lúc chờ vẫn có thể bị vây)."));
            return true;
        }
        if (sub.equals("roi")) {
            if (t == null || !r.isMayor() || !isMember(t) || isCapital(t)) {
                p.sendMessage(c("&cChỉ thị trưởng của thị trấn trong liên minh mới rời được."));
                return true;
            }
            console("ta nation " + nationName + " kick " + t.getName());
            p.sendMessage(c("&e" + t.getName() + " đã rời Liên minh Hoà Bình, mất hoà bình ngay. " + rejoinCooldownDays
                    + " ngày nữa mới vào lại được."));
            return true;
        }
        // thông tin
        p.sendMessage(c("&a&lLiên minh Hoà Bình &7- " + n.getTowns().size() + " thị trấn, " + totalPlots() + " ô"));
        p.sendMessage(c("&7Thị trấn trong liên minh: không bị vây, không bị chiếm, đất luôn tắt PvP. Đổi lại: dân không có quân hàm,"
                + " không đánh trận, không tính điểm Bá Chủ, chỉ được tự vệ, phí bảo hộ " + feePerPlot + " xu/ô/ngày."));
        p.sendMessage(c(String.format(Locale.ROOT, "&7Mua thêm ô: &f%.1f xu&7/ô lúc này (tăng theo tổng đất liên minh). Đất mới phải liền sát đất cũ.",
                plotBase + plotPerPlot * memberPlots())));
        p.sendMessage(c("&7Thị trưởng: &f/hoabinh vao &7(thị trấn phải chạm đất liên minh, không thuộc quốc gia nào) · &f/hoabinh roi &7· &f/hoabinh danhsach"));
        if (t != null && isMember(t)) {
            p.sendMessage(c(t.isNeutral() ? "&aThị trấn của bạn đang được hoà bình." : "&eThị trấn của bạn đang chờ hoà bình có hiệu lực."));
        }
        return true;
    }

    private String joinProblem(Resident r, Town t) {
        if (t == null || !r.isMayor()) {
            return "Chỉ thị trưởng mới đưa thị trấn vào liên minh được.";
        }
        if (isMember(t)) {
            return "Thị trấn đã ở trong liên minh.";
        }
        if (t.hasNation()) {
            return "Thị trấn đang thuộc quốc gia " + t.getNationOrNull().getName() + ": rời quốc gia trước (/n leave).";
        }
        if (SiegeWarHook.hasActiveSiege(t)) {
            return "Thị trấn đang bị vây, không vào liên minh được.";
        }
        long left = data.getLong("roi." + t.getUUID());
        long wait = left + rejoinCooldownDays * 86_400_000L - System.currentTimeMillis();
        if (left > 0 && wait > 0) {
            return String.format(Locale.ROOT, "Thị trấn vừa rời liên minh, còn %.1f ngày mới vào lại được.", wait / 86_400_000.0);
        }
        if (!townTouchesPeace(t)) {
            return "Đất thị trấn phải chạm (chung cạnh ô) với đất của liên minh. Đất hoà bình lan liền kề, không cách nhau.";
        }
        return null;
    }

    // ------------------------------------------------------------------ vào/rời, hoà bình

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdd(NationAddTownEvent e) {
        if (!e.getNation().getName().equalsIgnoreCase(nationName)) {
            return;
        }
        Town t = e.getTown();
        if (!data.contains("vao." + t.getUUID())) {
            data.set("vao." + t.getUUID(), System.currentTimeMillis());
        }
        t.setPVP(false);
        t.save();
        save();
        Bukkit.broadcast(c("&a✦ &f" + t.getName() + " &ađã gia nhập Liên minh Hoà Bình."));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(NationRemoveTownEvent e) {
        if (!e.getNation().getName().equalsIgnoreCase(nationName)) {
            return;
        }
        Town t = e.getTown();
        data.set("vao." + t.getUUID(), null);
        data.set("roi." + t.getUUID(), System.currentTimeMillis());
        save();
        if (t.isNeutral()) {
            console("swa town " + t.getName() + " setpeaceful false");
        }
        Bukkit.broadcast(c("&e✦ &f" + t.getName() + " &eđã rời Liên minh Hoà Bình."));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToggleNeutral(TownToggleNeutralEvent e) {
        CommandSender s = e.getSender();
        if (s instanceof ConsoleCommandSender || s.hasPermission("duckymomo.peace.admin")) {
            return;
        }
        e.setCancelled(true);
        e.setCancelMessage("Muốn hoà bình phải gia nhập Liên minh Hoà Bình: /hoabinh");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTogglePvp(TownTogglePVPEvent e) {
        if (e.getFutureState() && isMember(e.getTown())) {
            e.setCancelled(true);
            e.setCancelMessage("Đất của Liên minh Hoà Bình luôn tắt PvP.");
        }
    }

    // ------------------------------------------------------------------ đất

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClaim(TownPreClaimEvent e) {
        if (isMember(e.getTown()) && e.isOutpost()) {
            e.setCancelled(true);
            e.setCancelMessage("Thị trấn hoà bình không lập tiền đồn: đất phải liền sát đất cũ.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClaimCost(TownBlockClaimCostCalculationEvent e) {
        Town t = e.getTown();
        if (!isMember(t) || isCapital(t)) {
            return;
        }
        int total = memberPlots();
        double price = 0;
        for (int i = 0; i < e.getAmountOfRequestedTownBlocks(); i++) {
            price += plotBase + plotPerPlot * (total + i);
        }
        e.setPrice(price);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUpkeep(TownUpkeepCalculationEvent e) {
        Town t = e.getTown();
        if (isCapital(t)) {
            e.setUpkeep(0);
        } else if (isMember(t)) {
            e.setUpkeep(e.getUpkeep() + feePerPlot * t.getNumTownBlocks());
        }
    }

    /** Gọi từ TownRules: lập thị trấn trong vòng spawn. Trả về lý do từ chối (null = được, thị trấn sẽ tự vào liên minh). */
    String newTownInRing(Player p, String townName, Location at) {
        if (nation() == null) {
            return "Liên minh Hoà Bình chưa được lập, chưa lập thị trấn quanh kinh đô được.";
        }
        WorldCoord wc = WorldCoord.parseWorldCoord(at);
        if (!adjacentToPeace(wc.getWorldName(), wc.getX(), wc.getZ(), null)) {
            return "Quanh kinh đô chỉ lập thị trấn hoà bình, và phải đứng sát (chung cạnh ô) đất của Liên minh Hoà Bình.";
        }
        autoJoin.add(townName.toLowerCase(Locale.ROOT));
        p.sendMessage(c("&aThị trấn lập trong vùng kinh đô sẽ tự gia nhập Liên minh Hoà Bình."));
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNewTown(NewTownEvent e) {
        Town t = e.getTown();
        if (autoJoin.remove(t.getName().toLowerCase(Locale.ROOT)) && nation() != null) {
            data.set("vao." + t.getUUID(), System.currentTimeMillis());
            save();
            Bukkit.getScheduler().runTaskLater(plugin, () -> console("ta nation " + nationName + " add " + t.getName()), 20L);
        }
    }

    // ------------------------------------------------------------------ chợ

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!auctionOnlyInPeace) {
            return;
        }
        String label = e.getMessage().substring(1).split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        if (auctionCommands.contains(label) && !e.getPlayer().hasPermission("duckymomo.peace.admin")
                && !inPeaceLand(e.getPlayer().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(c("&eChợ đấu giá chỉ mở trong đất của Liên minh Hoà Bình (kinh đô và các thị trấn hoà bình)."));
        }
    }

    /** /sell trong đất kinh đô: thêm sell-bonus-percent% (sự kiện UserBalanceUpdateEvent của Essentials, gọi qua reflection). */
    @SuppressWarnings("unchecked")
    private void hookSellBonus() {
        Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
        if (ess == null || !ess.isEnabled() || sellBonus <= 0) {
            return;
        }
        try {
            Class<? extends Event> cls = (Class<? extends Event>) Class.forName("net.ess3.api.events.UserBalanceUpdateEvent", true, ess.getClass().getClassLoader());
            Method cause = cls.getMethod("getCause");
            Method player = cls.getMethod("getPlayer");
            Method oldB = cls.getMethod("getOldBalance");
            Method newB = cls.getMethod("getNewBalance");
            Method setB = cls.getMethod("setNewBalance", BigDecimal.class);
            Bukkit.getPluginManager().registerEvent(cls, this, EventPriority.HIGH, (l, ev) -> {
                if (!cls.isInstance(ev)) {
                    return;
                }
                try {
                    if (!"COMMAND_SELL".equals(String.valueOf(cause.invoke(ev)))) {
                        return;
                    }
                    Player p = (Player) player.invoke(ev);
                    if (p == null || !isCapital(TownyAPI.getInstance().getTown(p.getLocation()))) {
                        return;
                    }
                    BigDecimal o = (BigDecimal) oldB.invoke(ev);
                    BigDecimal n = (BigDecimal) newB.invoke(ev);
                    BigDecimal gain = n.subtract(o);
                    if (gain.signum() > 0) {
                        BigDecimal bonus = gain.multiply(BigDecimal.valueOf(sellBonus / 100.0));
                        setB.invoke(ev, n.add(bonus));
                        p.sendActionBar(c(String.format(Locale.ROOT, "&aChợ kinh đô: +%.0f%% (+%.2f xu)", sellBonus, bonus.doubleValue())));
                    }
                } catch (ReflectiveOperationException ex) {
                    plugin.getLogger().warning("Thưởng bán ở kinh đô lỗi: " + ex);
                }
            }, plugin, true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Không gắn được thưởng bán ở kinh đô (Essentials): " + e);
        }
    }

    /** Shop rương QuickShop: trong đất hoà bình thuế shop-tax-inside, ngoài shop-tax-outside (ShopTaxEvent). */
    @SuppressWarnings("unchecked")
    private void hookShopTax() {
        Plugin qs = Bukkit.getPluginManager().getPlugin("QuickShop-Hikari");
        if (qs == null || !qs.isEnabled()) {
            return;
        }
        try {
            ClassLoader cl = qs.getClass().getClassLoader();
            Class<? extends Event> cls = (Class<? extends Event>) Class.forName("com.ghostchu.quickshop.api.event.economy.ShopTaxEvent", true, cl);
            Class<?> shopCls = Class.forName("com.ghostchu.quickshop.api.shop.Shop", true, cl);
            Method getShop = cls.getMethod("getShop");
            Method setTax = cls.getMethod("setTax", double.class);
            Method getLoc = shopCls.getMethod("getLocation");
            Bukkit.getPluginManager().registerEvent(cls, this, EventPriority.HIGH, (l, ev) -> {
                if (!cls.isInstance(ev)) {
                    return;
                }
                try {
                    Location loc = (Location) getLoc.invoke(getShop.invoke(ev));
                    setTax.invoke(ev, loc != null && inPeaceLand(loc) ? shopTaxInside : shopTaxOutside);
                } catch (ReflectiveOperationException ex) {
                    plugin.getLogger().warning("Thuế shop rương lỗi: " + ex);
                }
            }, plugin, true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Không gắn được thuế shop rương (QuickShop): " + e);
        }
    }

    // ------------------------------------------------------------------ mỗi phút: bật hoà bình, báo vây

    private void tick() {
        Nation n = nation();
        if (n == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Town t : new ArrayList<>(n.getTowns())) {
            long joined = data.getLong("vao." + t.getUUID());
            boolean due = isCapital(t) || joined > 0 && now - joined >= joinWaitDays * 86_400_000L;
            // Thử bật hoà bình tối đa 10 phút một lần (lệnh SiegeWar có thể không thành, ví dụ kinh đô khi allow_peaceful_capitals tắt)
            if (due && !t.isNeutral() && now - data.getLong("thu." + t.getUUID()) > 600_000L) {
                data.set("thu." + t.getUUID(), now);
                save();
                console("swa town " + t.getName() + " setpeaceful true");
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (t.isNeutral()) {
                        broadcastTown(t, "&aHoà bình của " + t.getName() + " đã có hiệu lực.");
                    } else {
                        plugin.getLogger().warning("Không bật được hoà bình cho " + t.getName() + " (xem log SiegeWar)");
                    }
                }, 40L);
            }
            if (t.isPVP()) {
                t.setPVP(false);
                t.save();
            }
        }
        // Chỉ liên minh được hoà bình
        for (Town t : TownyAPI.getInstance().getTowns()) {
            if (t.isNeutral() && !isMember(t)) {
                console("swa town " + t.getName() + " setpeaceful false");
            }
        }
        siegeNotices(now);
    }

    private void broadcastTown(Town t, String msg) {
        for (Resident r : t.getResidents()) {
            Player p = r.getPlayer();
            if (p != null) {
                p.sendMessage(c(msg));
            }
        }
    }

    private int siegeRadius() {
        if (siegeRadius < 0) {
            File f = new File(plugin.getDataFolder().getParentFile(), "SiegeWar/config.yml");
            siegeRadius = YamlConfiguration.loadConfiguration(f).getInt("war.siege.distances.siegezone_radius_blocks", 200);
        }
        return siegeRadius;
    }

    /** Thị trấn hoà bình có ô đất nằm trong vùng vây (bán kính quanh cờ). */
    private List<Town> peaceTownsInZone(Location flag) {
        List<Town> out = new ArrayList<>();
        Nation n = nation();
        if (n == null || flag == null) {
            return out;
        }
        int r = siegeRadius();
        for (Town t : n.getTowns()) {
            for (TownBlock tb : t.getTownBlocks()) {
                if (!tb.getWorld().getName().equals(flag.getWorld().getName())) {
                    continue;
                }
                double dx = tb.getX() * 16 + 8 - flag.getX();
                double dz = tb.getZ() * 16 + 8 - flag.getZ();
                if (dx * dx + dz * dz <= (double) r * r) {
                    out.add(t);
                    break;
                }
            }
        }
        return out;
    }

    private void notifyPeace(List<Town> towns, String msg, boolean mail) {
        for (Town t : towns) {
            for (Resident r : t.getResidents()) {
                Player p = r.getPlayer();
                if (p != null) {
                    p.sendMessage(c(msg));
                } else if (mail) {
                    console("mail send " + r.getName() + " " + LEGACY.serialize(c(msg)).replaceAll("§.", ""));
                }
            }
        }
    }

    private void siegeNotices(long now) {
        Long next = SiegeWarHook.nextBattleSessionStart();
        for (SiegeWarHook.SiegeInfo s : SiegeWarHook.activeSieges()) {
            String key = "vay." + s.id();
            List<Town> peace = peaceTownsInZone(s.flag());
            String where = s.flag() == null ? "?" : s.flag().getBlockX() + " " + s.flag().getBlockZ();
            if (!data.getBoolean(key + ".bao")) {
                data.set(key + ".bao", true);
                save();
                Bukkit.broadcast(c("&c⚔ &f" + s.attacker() + " &cđang vây &f" + s.town() + " &ctại &f" + where
                        + "&c. Vùng vây bán kính &f" + siegeRadius() + " block&c." + (next == null ? "" : " Phiên đánh gần nhất: &f" + WHEN.format(Instant.ofEpochMilli(next)))));
                if (!peace.isEmpty()) {
                    notifyPeace(peace, "&c[Liên minh Hoà Bình] Thị trấn của bạn nằm trong vùng vây quanh " + s.town() + " (" + where
                            + "). Hãy di tản trước giờ công thành" + (next == null ? "" : " " + WHEN.format(Instant.ofEpochMilli(next))) + ".", true);
                }
            }
            if (next == null) {
                continue;
            }
            // Nhắc trước ngày công thành (nếu cuộc vây bắt đầu sớm hơn mốc này thì tin trên đã đủ)
            if (!data.getBoolean(key + ".nhac-ngay") && now >= next - (long) (warnDaysBefore * 86_400_000L)) {
                data.set(key + ".nhac-ngay", true);
                save();
                if (!peace.isEmpty()) {
                    notifyPeace(peace, "&c[Liên minh Hoà Bình] Còn chưa tới " + warnDaysBefore + " ngày nữa là công thành quanh " + s.town()
                            + ". Di tản khỏi vùng vây (" + where + ").", true);
                }
            }
            for (int m : remindMinutes) {
                long at = next - m * 60_000L;
                String rk = key + ".nhac." + next + "-" + m;
                if (now >= at && now < next && !data.getBoolean(rk)) {
                    data.set(rk, true);
                    save();
                    String msg = "&c⚔ Còn " + m + " phút nữa là phiên công thành quanh " + s.town() + " (" + where + ").";
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (SiegeWarHook.inActiveSiegeZone(p.getLocation())) {
                            p.sendMessage(c(msg + " &7Bạn đang đứng trong vùng vây."));
                        }
                    }
                    notifyPeace(peace, msg + " &7Dân hoà bình hãy rời vùng vây.", false);
                }
            }
        }
    }

    // ------------------------------------------------------------------ admin: /dmm hoabinh

    void adminCommand(CommandSender s, String[] args) {
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "setup" -> {
                if (!(s instanceof Player p)) {
                    s.sendMessage("Vào game, đứng giữa spawn rồi gõ /dmm hoabinh setup");
                    return;
                }
                // Kinh đô: thị trấn có thị trưởng NPC, rồi lập quốc gia, rồi bật hoà bình (SiegeWar cần allow_peaceful_capitals: true)
                p.performCommand("ta town new " + capitalName + " npc");
                Bukkit.getScheduler().runTaskLater(plugin, () -> p.performCommand("ta nation new " + nationName + " " + capitalName), 40L);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    console("swa town " + capitalName + " setpeaceful true");
                    s.sendMessage(c("&aXong. Tiếp theo: &f/dmm hoabinh claim <bán kính ô> &ađể kinh đô chiếm đất quanh spawn (1 ô = 16 block)."));
                }, 80L);
            }
            case "claim" -> {
                Town cap = TownyAPI.getInstance().getTown(capitalName);
                if (cap == null || cap.getHomeBlockOrNull() == null) {
                    s.sendMessage("Chưa có kinh đô " + capitalName + ": /dmm hoabinh setup trước.");
                    return;
                }
                int r;
                try {
                    r = Math.max(1, Math.min(40, Integer.parseInt(args.length >= 3 ? args[2] : "")));
                } catch (NumberFormatException e) {
                    s.sendMessage("/dmm hoabinh claim <bán kính ô, 1-40>");
                    return;
                }
                WorldCoord home = cap.getHomeBlockOrNull().getWorldCoord();
                int added = 0;
                int skipped = 0;
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        WorldCoord wc = new WorldCoord(home.getWorldName(), home.getX() + dx, home.getZ() + dz);
                        if (wc.hasTownBlock()) {
                            skipped++;
                            continue;
                        }
                        TownBlock tb = new TownBlock(wc);
                        tb.setTown(cap);
                        TownyUniverse.getInstance().addTownBlock(tb);
                        tb.save();
                        added++;
                    }
                }
                cap.save();
                s.sendMessage(c("&aKinh đô chiếm thêm &f" + added + " &aô (bỏ qua " + skipped + " ô đã có chủ). Tổng: " + cap.getNumTownBlocks() + " ô."));
            }
            default -> {
                Nation n = nation();
                s.sendMessage(c("&6Liên minh Hoà Bình: " + (n == null ? "&cchưa lập" : "&a" + n.getTowns().size() + " thị trấn, " + totalPlots() + " ô")
                        + " &7| /dmm hoabinh setup (đứng giữa spawn) | claim <bán kính ô>"));
            }
        }
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được hoabinh.yml: " + e.getMessage());
        }
    }
}
