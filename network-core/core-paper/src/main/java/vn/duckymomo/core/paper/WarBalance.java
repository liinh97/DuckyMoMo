package vn.duckymomo.core.paper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Government;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * Cân bằng chiến tranh (/canbang), chống "nước to nuốt hết" (Bang Hội Chiến, cần Towny + SiegeWar). Config war-balance:
 *  1. Càng to càng tốn: war-chest-extra-percent-per-town = quỹ chiến tranh tăng % theo mỗi thị trấn (trừ thị trấn đầu) nước đi vây đang có;
 *     colony-upkeep-per-day = mỗi thuộc địa tốn ngần ấy xu/ngày từ ngân khố nước chiếm (trả thiếu thì ghi nợ, còn nợ thì không cắm cờ vây được).
 *  2. Kẻ yếu: underdog-ratio (dân bên tấn công >= ngần ấy lần dân bên phòng thủ) -> bên phòng thủ +underdog-points-percent% điểm giữ cờ.
 *  3. Thuộc địa: max-colonies mỗi nước (chặn chiếm thêm); revolt-hours-base giờ được khởi nghĩa, bớt revolt-hours-less-per-colony giờ cho mỗi
 *     thuộc địa khác của nước chiếm, tối thiểu revolt-hours-min.
 *  4. Bá chủ: nước có >= hegemon-percent% số thị trấn (và >= hegemon-min-towns): đánh vào Bá chủ được giảm hegemon-warchest-discount% quỹ
 *     chiến tranh (hoàn lại cho bên tấn công) và +hegemon-points-percent% điểm giữ cờ; Bá chủ chỉ hưởng đặc sản tối đa hegemon-max-regions vùng.
 * SiegeWar gọi qua phản chiếu (không cần jar khi build). Lưu nợ: canbang.yml; ghi canbang.json cho web.
 */
final class WarBalance implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    static volatile WarBalance INSTANCE;

    private final DuckyMoMoCore plugin;
    private final double chestExtraPct;
    private final double colonyUpkeep;
    private final double underdogRatio;
    private final double underdogPct;
    private final int maxColonies;
    private final double revoltBase;
    private final double revoltLess;
    private final double revoltMin;
    private final double hegemonPct;
    private final int hegemonMinTowns;
    private final double hegemonDiscount;
    private final double hegemonPointsPct;
    final int hegemonMaxRegions;
    private final File file;
    private final YamlConfiguration data;
    private final Set<String> hegemons = new HashSet<>();
    private final String peaceNation;   // Liên minh Hoà Bình: không tính Bá chủ, không phí thuộc địa
    private ClassLoader swLoader;
    private Method occupiedForeignTowns;
    private Method setRevoltEnd;

    WarBalance(DuckyMoMoCore plugin, ConfigurationSection c) {
        this.plugin = plugin;
        this.chestExtraPct = Math.max(0, c.getDouble("war-chest-extra-percent-per-town", 10));
        this.colonyUpkeep = Math.max(0, c.getDouble("colony-upkeep-per-day", 50));
        this.underdogRatio = Math.max(1, c.getDouble("underdog-ratio", 2));
        this.underdogPct = Math.max(0, c.getDouble("underdog-points-percent", 15));
        this.maxColonies = Math.max(0, c.getInt("max-colonies", 3));
        this.revoltBase = Math.max(1, c.getDouble("revolt-hours-base", 72));
        this.revoltLess = Math.max(0, c.getDouble("revolt-hours-less-per-colony", 12));
        this.revoltMin = Math.max(1, c.getDouble("revolt-hours-min", 24));
        this.hegemonPct = Math.max(1, c.getDouble("hegemon-percent", 30));
        this.hegemonMinTowns = Math.max(1, c.getInt("hegemon-min-towns", 5));
        this.hegemonDiscount = Math.max(0, Math.min(100, c.getDouble("hegemon-warchest-discount", 30)));
        this.hegemonPointsPct = Math.max(0, c.getDouble("hegemon-points-percent", 15));
        this.hegemonMaxRegions = Math.max(0, c.getInt("hegemon-max-regions", 2));
        this.peaceNation = plugin.getConfig().getBoolean("peace-alliance.enabled") ? plugin.getConfig().getString("peace-alliance.nation", "") : "";
        this.file = new File(plugin.getDataFolder(), "canbang.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    String describe() {
        return "quỹ +" + fmt(chestExtraPct) + "%/thị trấn, thuộc địa " + fmt(colonyUpkeep) + " xu/ngày (tối đa " + maxColonies + "), kẻ yếu +"
                + fmt(underdogPct) + "%, Bá chủ >= " + fmt(hegemonPct) + "% thị trấn";
    }

    boolean start() {
        Plugin sw = Bukkit.getPluginManager().getPlugin("SiegeWar");
        if (sw == null || !sw.isEnabled()) {
            return false;
        }
        swLoader = sw.getClass().getClassLoader();
        try {
            Class<?> occ = Class.forName("com.gmail.goosius.siegewar.TownOccupationController", true, swLoader);
            occupiedForeignTowns = occ.getMethod("getOccupiedForeignTowns", Nation.class);
            setRevoltEnd = Class.forName("com.gmail.goosius.siegewar.metadata.TownMetaDataController", true, swLoader)
                    .getMethod("setRevoltImmunityEndTime", Town.class, long.class);
            on("PreSiegeWarStartEvent", EventPriority.HIGH, this::onPreSiege);
            on("SiegeWarStartEvent", EventPriority.MONITOR, this::onSiegeStart);
            on("PreInvadeEvent", EventPriority.HIGH, this::onPreInvade);
            on("BannerControlPointsEvent", EventPriority.MONITOR, this::onBannerPoints);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Cân bằng chiến tranh: không gắn được SiegeWar (" + e + "), tắt.");
            return false;
        }
        INSTANCE = this;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 200L, 12000L);   // 10 phút
        return true;
    }

    void stop() {
        INSTANCE = null;
        save();
    }

    @SuppressWarnings("unchecked")
    private void on(String event, EventPriority prio, Consumer<Event> handler) throws ClassNotFoundException {
        Class<? extends Event> cls = (Class<? extends Event>) Class.forName("com.gmail.goosius.siegewar.events." + event, true, swLoader);
        Bukkit.getPluginManager().registerEvent(cls, this, prio, (l, ev) -> {
            if (cls.isInstance(ev)) {
                try {
                    handler.accept(ev);
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Cân bằng chiến tranh (" + event + "): " + ex);
                }
            }
        }, plugin, false);
    }

    private static Object get(Object o, String m) {
        try {
            Method mm = o.getClass().getMethod(m);
            mm.setAccessible(true);
            return mm.invoke(o);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(m + ": " + e, e);
        }
    }

    private static void call(Object o, String m, Class<?> t, Object v) {
        try {
            Method mm = o.getClass().getMethod(m, t);
            mm.setAccessible(true);
            mm.invoke(o, v);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(m + ": " + e, e);
        }
    }

    // ---------------------------------------------------------------- số liệu

    /** Số thuộc địa = thị trấn bị chiếm (Towny conquered) trong nước n. 21/10: trước dùng SiegeWar getOccupiedForeignTowns, hàm đó bỏ sót
     *  thị trấn trước khi bị chiếm không thuộc nước nào -> không thu phí thuộc địa, không tính giới hạn max-colonies. */
    int colonies(Nation n) {
        if (n == null) {
            return 0;
        }
        int k = (int) n.getTowns().stream().filter(Town::isConquered).count();
        try {
            return Math.max(k, ((Collection<?>) occupiedForeignTowns.invoke(null, n)).size());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return k;
        }
    }

    boolean isHegemon(Nation n) {
        return n != null && hegemons.contains(n.getName().toLowerCase(Locale.ROOT));
    }

    static boolean hegemon(Nation n) {
        WarBalance w = INSTANCE;
        return w != null && w.isHegemon(n);
    }

    private double debt(Nation n) {
        return data.getDouble("no." + n.getUUID(), 0);
    }

    private double warchestPerPlot() {
        YamlConfiguration sw = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder().getParentFile(), "SiegeWar/config.yml"));
        return Double.parseDouble(String.valueOf(sw.get("war.siege.money.warchest_cost_per_plot", "20")));
    }

    /** % quỹ chiến tranh thêm (dương) / bớt (âm) khi nước n vây thị trấn target. */
    private double chestAdjustPct(Nation n, Town target) {
        double pct = chestExtraPct * Math.max(0, n.getTowns().size() - 1);
        if (isHegemon(target.getNationOrNull())) {
            pct -= hegemonDiscount;
        }
        return pct;
    }

    private static int residents(Object gov) {
        return gov instanceof Government g ? g.getResidents().size() : 0;
    }

    // ---------------------------------------------------------------- sự kiện SiegeWar

    private void onPreSiege(Event ev) {
        if ((Boolean) get(ev, "isCancelled")) {
            return;
        }
        Nation n = (Nation) get(ev, "getNation");
        Town target = (Town) get(ev, "getTargetTown");
        if (n == null || target == null) {
            return;
        }
        if (debt(n) > 0) {
            call(ev, "setCancelled", boolean.class, true);
            call(ev, "setCancellationMsg", String.class, "Nước " + n.getName() + " còn nợ phí duy trì thuộc địa " + fmt(debt(n))
                    + " xu: nộp tiền vào ngân khố nước, hệ thống tự trừ nợ rồi mới cắm cờ vây được (/canbang).");
            return;
        }
        double pct = chestAdjustPct(n, target);
        if (pct > 0) {
            double base = target.getTownBlocks().size() * warchestPerPlot();
            double total = base * (1 + pct / 100.0);
            if (!n.getAccount().canPayFromHoldings(total)) {
                call(ev, "setCancelled", boolean.class, true);
                call(ev, "setCancellationMsg", String.class, "Nước " + n.getName() + " có " + n.getTowns().size() + " thị trấn nên quỹ chiến tranh tăng "
                        + fmt(pct) + "%: cần " + fmt(total) + " xu trong ngân khố nước (/canbang).");
            }
        }
    }

    private void onSiegeStart(Event ev) {
        Object siege = get(ev, "getSiege");
        Nation n = (Nation) get(ev, "getNation");
        Town target = (Town) get(ev, "getTargetTown");
        if (siege == null || n == null || target == null) {
            return;
        }
        double pct = chestAdjustPct(n, target);
        if (pct == 0) {
            return;
        }
        double chest = (Double) get(siege, "getWarChestAmount");
        double adj = Math.round(chest * pct) / 100.0;
        if (adj > 0) {
            if (!n.getAccount().withdraw(adj, "Quỹ chiến tranh thêm (nước lớn)")) {
                return;
            }
        } else {
            n.getAccount().deposit(-adj, "Giảm quỹ chiến tranh khi đánh Bá chủ");
        }
        call(siege, "setWarChestAmount", double.class, chest + adj);
        String msg = adj > 0 ? "&6⚖ Nước &f" + n.getName() + " &6có " + n.getTowns().size() + " thị trấn: quỹ chiến tranh thêm &f" + fmt(adj) + " xu"
                : "&6⚖ Đánh vào Bá chủ: nước &f" + n.getName() + " &6được hoàn &f" + fmt(-adj) + " xu &6quỹ chiến tranh";
        Bukkit.broadcast(c(msg + " &7(cuộc vây " + target.getName() + ")."));
    }

    private void onPreInvade(Event ev) {
        if ((Boolean) get(ev, "isCancelled")) {
            return;
        }
        Nation n = (Nation) get(ev, "getInvadingNation");
        Town town = (Town) get(ev, "getInvadedTown");
        if (n == null || town == null) {
            return;
        }
        int have = colonies(n);
        if (maxColonies > 0 && have >= maxColonies) {
            call(ev, "setCancelled", boolean.class, true);
            call(ev, "setCancellationMsg", String.class, "Nước " + n.getName() + " đã có " + have + "/" + maxColonies
                    + " thuộc địa: không chiếm thêm được (vẫn cướp được). /canbang");
            return;
        }
        // sau khi SiegeWar chiếm xong: đặt lại hạn khởi nghĩa theo số thuộc địa của nước chiếm
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            WildGates wg = Shared.wildGates;
            double h = Math.max(revoltMin, revoltHours(colonies(n)) - (wg == null ? 0 : wg.revoltLess(town)));
            try {
                setRevoltEnd.invoke(null, town, System.currentTimeMillis() + (long) (h * 3_600_000L));
                Bukkit.broadcast(c("&6⚖ " + town.getName() + " thành thuộc địa của " + n.getName() + ": được khởi nghĩa sau &f" + fmt(h) + " giờ&6."));
            } catch (ReflectiveOperationException | RuntimeException e) {
                plugin.getLogger().warning("Không đặt được hạn khởi nghĩa: " + e);
            }
        }, 40L);
    }

    double revoltHours(int colonies) {
        return Math.max(revoltMin, revoltBase - revoltLess * Math.max(0, colonies - 1));
    }

    private void onBannerPoints(Event ev) {
        Object siege = get(ev, "getSiege");
        String side = String.valueOf(get(ev, "getAwardingSide"));
        int pts = (Integer) get(ev, "getBattlePoints");
        if (siege == null || pts <= 0) {
            return;
        }
        boolean defending = side.toUpperCase(Locale.ROOT).startsWith("DEFEND");
        Object att = get(siege, "getAttacker");
        Object def = get(siege, "getDefender");
        double pct = 0;
        // so cả phe: thị trấn bị vây thuộc nước nào thì tính dân cả nước đó (không so nước với một thị trấn lẻ)
        Nation an = govNation(att);
        Nation dn = govNation(def);
        int a = an != null ? residents(an) : residents(att);
        int d = dn != null ? residents(dn) : residents(def);
        if (defending && d > 0 && a >= underdogRatio * d) {
            pct += underdogPct;   // bên phòng thủ nhỏ hơn hẳn
        }
        Nation otherNation = govNation(defending ? att : def);
        if (isHegemon(otherNation)) {
            pct += hegemonPointsPct;   // phe đang giành điểm đánh vào Bá chủ
        }
        if (!defending) {
            // server vắng: phe phòng thủ không ai online thì bên tấn công chỉ ăn một phần điểm (không mất trắng vì vắng nhà)
            double keep = LowPop.absentDefenderAttackPct();
            if (keep < 100 && onlineOf(dn != null ? dn : def) == 0) {
                pct -= 100 - keep;
            }
        }
        int bonus = (int) Math.round(pts * pct / 100.0);
        if (bonus == 0) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> call(siege, defending ? "adjustDefenderBattlePoints" : "adjustAttackerBattlePoints", int.class, bonus));
    }

    private static int onlineOf(Object gov) {
        if (!(gov instanceof Government g)) {
            return 0;
        }
        int n = 0;
        for (var r : g.getResidents()) {
            if (r.isOnline()) {
                n++;
            }
        }
        return n;
    }

    private static Nation govNation(Object gov) {
        if (gov instanceof Nation n) {
            return n;
        }
        return gov instanceof Town t ? t.getNationOrNull() : null;
    }

    // ---------------------------------------------------------------- định kỳ: Bá chủ, phí thuộc địa

    /** Thị trấn/nước thuộc phe chiến tranh (không phải Liên minh Hoà Bình, không trung lập). */
    private boolean atWar(Town t) {
        Nation n = t.getNationOrNull();
        return !PeaceStatus.peaceful(t) && !(n != null && n.getName().equalsIgnoreCase(peaceNation));
    }

    private boolean isPeace(Nation n) {
        return n != null && n.getName().equalsIgnoreCase(peaceNation);
    }

    private void tick() {
        List<Town> towns = new ArrayList<>(TownyAPI.getInstance().getTowns());
        towns.removeIf(t -> !atWar(t));   // mẫu số Bá chủ: chỉ thị trấn tham gia chiến tranh
        Set<String> now = new HashSet<>();
        if (!towns.isEmpty()) {
            for (Nation n : TownyAPI.getInstance().getNations()) {
                if (isPeace(n)) {
                    continue;
                }
                int k = n.getTowns().size();
                if (k >= hegemonMinTowns && k * 100.0 / towns.size() >= hegemonPct) {
                    now.add(n.getName().toLowerCase(Locale.ROOT));
                }
            }
        }
        for (String h : now) {
            if (!hegemons.contains(h)) {
                Bukkit.broadcast(c("&4⚔ &cNước &f" + h + " &cđã thành BÁ CHỦ&c (nắm từ " + fmt(hegemonPct) + "% thị trấn): đánh vào Bá chủ được giảm quỹ chiến tranh "
                        + fmt(hegemonDiscount) + "% và cộng " + fmt(hegemonPointsPct) + "% điểm. &7/canbang"));
            }
        }
        for (String h : hegemons) {
            if (!now.contains(h)) {
                Bukkit.broadcast(c("&6⚖ Nước &f" + h + " &6không còn là Bá chủ."));
            }
        }
        hegemons.clear();
        hegemons.addAll(now);
        String today = LocalDate.now(VN).toString();
        if (!today.equals(data.getString("phi-ngay", "")) && colonyUpkeep > 0) {
            data.set("phi-ngay", today);
            for (Nation n : TownyAPI.getInstance().getNations()) {
                if (isPeace(n)) {
                    continue;
                }
                double owe = debt(n) + colonyUpkeep * colonies(n);
                if (owe <= 0) {
                    continue;
                }
                double have = Math.max(0, n.getAccount().getHoldingBalance());
                double pay = Math.min(have, owe);
                if (pay > 0) {
                    n.getAccount().withdraw(pay, "Phí duy trì thuộc địa");
                }
                data.set("no." + n.getUUID(), owe - pay > 0.009 ? owe - pay : null);
            }
        } else {
            // trả nợ dần khi ngân khố có tiền
            for (Nation n : TownyAPI.getInstance().getNations()) {
                double owe = debt(n);
                if (owe > 0) {
                    double pay = Math.min(Math.max(0, n.getAccount().getHoldingBalance()), owe);
                    if (pay > 0 && n.getAccount().withdraw(pay, "Trả nợ phí thuộc địa")) {
                        data.set("no." + n.getUUID(), owe - pay > 0.009 ? owe - pay : null);
                    }
                }
            }
        }
        save();
        writeJson(towns.size());
    }

    // ---------------------------------------------------------------- lệnh, lưu

    /** /canbang cho người chơi: menu (22/10, trước in 8 dòng chat). */
    private void menu(Player p) {
        InfoMenu m = InfoMenu.of("&6⚖ Cân bằng chiến tranh").accent(org.bukkit.Material.ORANGE_STAINED_GLASS_PANE);
        Tip top = Tip.of("&6⚖ &fCân bằng chiến tranh").accent("&6").desc("Luật giúp nước nhỏ có cơ hội, nước lớn phải trả giá.").line()
                .kv("Bá chủ hiện tại", hegemons.isEmpty() ? "&7chưa có" : String.join(", ", hegemons));
        var res = TownyAPI.getInstance().getResident(p);
        Town t = res == null ? null : res.getTownOrNull();
        Nation n = t == null ? null : t.getNationOrNull();
        if (n != null) {
            top.gap().head("Nước bạn: " + n.getName()).kv("Thị trấn", String.valueOf(n.getTowns().size()))
                    .kv("Thuộc địa", colonies(n) + "/" + maxColonies)
                    .kv("Đi vây tốn thêm", fmt(chestExtraPct * Math.max(0, n.getTowns().size() - 1)) + "%");
            if (debt(n) > 0) {
                top.kv("Nợ", "&c" + fmt(debt(n)) + " xu &7(không cắm cờ vây được)");
            }
        }
        m.top(top.apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.GOLDEN_HELMET)));
        m.add(Tip.of("&e⛃ &fQuỹ chiến tranh").accent("&e").desc("Nước càng nhiều thị trấn, đi vây càng tốn")
                .kv("Tăng", fmt(chestExtraPct) + "% cho mỗi thị trấn (trừ thị trấn đầu)").apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.CHEST)));
        m.add(Tip.of("&6⚑ &fThuộc địa").accent("&6").kv("Phí", fmt(colonyUpkeep) + " xu/ngày từ ngân khố nước").kv("Tối đa", maxColonies + " thuộc địa/nước")
                .note("Còn nợ phí thì không cắm cờ vây được.").apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.YELLOW_BANNER)));
        m.add(Tip.of("&c✊ &fKhởi nghĩa").accent("&c").desc("Thuộc địa được nổi dậy giành lại tự do").kv("Sau", fmt(revoltBase) + " giờ")
                .kv("Bớt", fmt(revoltLess) + " giờ cho mỗi thuộc địa khác của nước chủ").kv("Ít nhất", fmt(revoltMin) + " giờ")
                .apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.TNT)));
        m.add(Tip.of("&a⛨ &fBên yếu được lợi").accent("&a").desc("Bên phòng thủ ít dân hơn hẳn")
                .kv("Khi", "bên kia đông gấp " + fmt(underdogRatio) + " lần").kv("Được", "+" + fmt(underdogPct) + "% điểm giữ cờ")
                .apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.SHIELD)));
        m.add(Tip.of("&d♛ &fBá chủ").accent("&d").desc("Nước nắm từ " + fmt(hegemonPct) + "% thị trấn (ít nhất " + hegemonMinTowns + ")")
                .head("Bị bất lợi").item("Đánh vào Bá chủ được giảm " + fmt(hegemonDiscount) + "% quỹ").item("+" + fmt(hegemonPointsPct) + "% điểm khi đánh Bá chủ")
                .item("Chỉ hưởng đặc sản tối đa " + hegemonMaxRegions + " vùng").apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.NETHER_STAR)));
        m.open(p);
    }

    boolean command(CommandSender s, String[] args) {
        if (s instanceof Player pm) {
            menu(pm);
            return true;
        }
        s.sendMessage(c("&6⚖ Cân bằng chiến tranh"));
        s.sendMessage(c("&7• Quỹ chiến tranh tăng &f" + fmt(chestExtraPct) + "% &7cho mỗi thị trấn (trừ thị trấn đầu) nước đi vây đang có."));
        s.sendMessage(c("&7• Mỗi thuộc địa tốn &f" + fmt(colonyUpkeep) + " xu/ngày &7từ ngân khố nước; còn nợ thì không cắm cờ vây được. Tối đa &f"
                + maxColonies + " &7thuộc địa/nước."));
        s.sendMessage(c("&7• Thuộc địa được khởi nghĩa sau &f" + fmt(revoltBase) + " giờ&7, bớt &f" + fmt(revoltLess) + " giờ &7cho mỗi thuộc địa khác của nước chiếm (ít nhất "
                + fmt(revoltMin) + " giờ)."));
        s.sendMessage(c("&7• Bên phòng thủ ít dân hơn hẳn (bên kia gấp &f" + fmt(underdogRatio) + " lần&7): +&f" + fmt(underdogPct) + "% &7điểm giữ cờ."));
        s.sendMessage(c("&7• Bá chủ (nắm từ &f" + fmt(hegemonPct) + "% &7thị trấn, ít nhất " + hegemonMinTowns + "): đánh vào được giảm &f" + fmt(hegemonDiscount)
                + "% &7quỹ, +&f" + fmt(hegemonPointsPct) + "% &7điểm; Bá chủ chỉ hưởng đặc sản tối đa " + hegemonMaxRegions + " vùng."));
        s.sendMessage(c("&eBá chủ hiện tại: &f" + (hegemons.isEmpty() ? "chưa có" : String.join(", ", hegemons))));
        if (s instanceof Player p) {
            var res = TownyAPI.getInstance().getResident(p);
            Town t = res == null ? null : res.getTownOrNull();
            Nation n = t == null ? null : t.getNationOrNull();
            if (n != null) {
                s.sendMessage(c("&eNước bạn (&f" + n.getName() + "&e): " + n.getTowns().size() + " thị trấn, " + colonies(n) + "/" + maxColonies + " thuộc địa"
                        + (debt(n) > 0 ? ", &cnợ " + fmt(debt(n)) + " xu" : "") + "&e; đi vây tốn thêm &f" + fmt(chestExtraPct * Math.max(0, n.getTowns().size() - 1)) + "%&e."));
            }
        }
        return true;
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được canbang.yml: " + e.getMessage());
        }
    }

    private void writeJson(int totalTowns) {
        JsonObject root = new JsonObject();
        root.addProperty("updated", System.currentTimeMillis() / 1000);
        root.addProperty("tong_thi_tran", totalTowns);
        JsonArray arr = new JsonArray();
        for (Nation n : TownyAPI.getInstance().getNations()) {
            JsonObject o = new JsonObject();
            o.addProperty("ten", n.getName());
            o.addProperty("thi_tran", n.getTowns().size());
            o.addProperty("thuoc_dia", colonies(n));
            o.addProperty("ba_chu", isHegemon(n));
            o.addProperty("no", Math.round(debt(n)));
            arr.add(o);
        }
        root.add("nuoc", arr);
        try {
            Files.writeString(new File(plugin.getDataFolder(), "canbang.json").toPath(), root.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Không ghi được canbang.json: " + e.getMessage());
        }
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
}
