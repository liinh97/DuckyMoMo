package vn.duckymomo.core.paper;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.types.PermissionNode;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Lính đánh thuê (/linhdanhthue, Bang Hội Chiến): cầu nối người chơi một mình với chiến tranh của các nước. Config mercenary:
 *  - Người KHÔNG thuộc nước nào (và không ở Liên minh Hoà Bình, không phải tài khoản phụ, chơi >= min-hours giờ) đăng ký kèm giá.
 *  - Vua hoặc người có chức nước trong hire-ranks thuê: /linhdanhthue thue <tên>; lính đồng ý /linhdanhthue nhan -> ngân khố nước trả giá,
 *    server giữ fee-percent %, lính nhận phần còn lại. Hợp đồng contract-hours giờ, mỗi lính 1 hợp đồng một lúc.
 *  - Trong hợp đồng: miễn "ốm" vùng vây của SiegeWar (quyền siegewar.immune.to.war.nausea, node LuckPerms có hạn = hạn hợp đồng;
 *    22/10: trước dùng PermissionAttachment, test bot thấy SiegeWar không nhận -> lính vẫn ốm), ăn kho lương THỦ ĐÔ nước thuê,
 *    không đánh được người của nước thuê và ngược lại. Không giữ cờ được (SiegeWar chỉ tính quân hàm), không có điểm Chiến Thần.
 * Lưu: linhdanhthue.yml (dangky.<uuid>{ten,gia,xong}, hopdong.<uuid>{nuoc,het}).
 */
final class Mercenary implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    static final String IMMUNE = "siegewar.immune.to.war.nausea";
    static volatile Mercenary INSTANCE;

    private record Offer(UUID nation, String nationName, String by, long at) { }

    private final DuckyMoMoCore plugin;
    private final Activity activity;
    private final WarRules war;
    private final double minHours;
    private final double contractHours;
    private final double feePct;
    private final double minPrice;
    private final double maxPrice;
    private final List<String> hireRanks;
    private final String peaceNation;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, Offer> offers = new HashMap<>();

    Mercenary(DuckyMoMoCore plugin, Activity activity, WarRules war, ConfigurationSection c) {
        this.plugin = plugin;
        this.activity = activity;
        this.war = war;
        this.minHours = Math.max(0, c.getDouble("min-hours", 5));
        this.contractHours = Math.max(1, c.getDouble("contract-hours", 48));
        this.feePct = Math.max(0, Math.min(90, c.getDouble("fee-percent", 10)));
        this.minPrice = Math.max(0, c.getDouble("min-price", 100));
        this.maxPrice = Math.max(minPrice, c.getDouble("max-price", 100000));
        this.hireRanks = c.getStringList("hire-ranks").stream().map(x -> x.toLowerCase(Locale.ROOT)).toList();
        this.peaceNation = plugin.getConfig().getBoolean("peace-alliance.enabled") ? plugin.getConfig().getString("peace-alliance.nation", "") : "";
        this.file = new File(plugin.getDataFolder(), "linhdanhthue.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    String describe() {
        return "hợp đồng " + fmt(contractHours) + " giờ, phí " + fmt(feePct) + "%, giá " + fmt(minPrice) + "–" + fmt(maxPrice) + " xu";
    }

    void start() {
        INSTANCE = this;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 200L, 1200L);
        ConfigurationSection hd = data.getConfigurationSection("hopdong");   // hợp đồng làm trước 22/10 chưa có node
        if (hd != null) {
            hd.getKeys(false).forEach(id -> grantImmune(UUID.fromString(id), hd.getLong(id + ".het", 0)));
        }
    }

    void stop() {
        INSTANCE = null;
        save();
    }

    // ---------------------------------------------------------------- hợp đồng

    /** Nước đang thuê người này (null = không có hợp đồng còn hạn). */
    Nation employer(UUID u) {
        long het = data.getLong("hopdong." + u + ".het", 0);
        if (het <= System.currentTimeMillis()) {
            return null;
        }
        String id = data.getString("hopdong." + u + ".nuoc");
        try {
            return id == null ? null : TownyAPI.getInstance().getNation(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Thị trấn mà lính đánh thuê ăn kho lương: thủ đô nước thuê. */
    static Town supplyTown(Player p) {
        Mercenary m = INSTANCE;
        Nation n = m == null ? null : m.employer(p.getUniqueId());
        return n == null ? null : n.getCapital();
    }

    /** Miễn ốm vùng vây tới hết hợp đồng: node LuckPerms có hạn (tự hết, không cần gỡ). */
    private static void grantImmune(UUID u, long het) {
        if (het <= System.currentTimeMillis()) {
            return;
        }
        try {
            LuckPermsProvider.get().getUserManager().modifyUser(u, user ->
                    user.data().add(PermissionNode.builder(IMMUNE).expiry(het / 1000).build()));
        } catch (IllegalStateException | NoClassDefFoundError e) {
            Bukkit.getLogger().warning("[DuckyMoMoCore] Lính đánh thuê: không cấp được quyền miễn ốm (" + e + ")");
        }
    }

    private void tick() {
        ConfigurationSection hd = data.getConfigurationSection("hopdong");
        if (hd != null) {
            for (String id : new ArrayList<>(hd.getKeys(false))) {
                if (hd.getLong(id + ".het", 0) <= System.currentTimeMillis()) {
                    String nation = hd.getString(id + ".ten-nuoc", "?");
                    data.set("hopdong." + id, null);
                    data.set("dangky." + id + ".xong", data.getInt("dangky." + id + ".xong", 0) + 1);
                    Player p = Bukkit.getPlayer(UUID.fromString(id));
                    if (p != null) {
                        p.sendMessage(c("&6⚔ Hợp đồng lính đánh thuê với nước &f" + nation + " &6đã hết hạn. Bạn lại có thể nhận việc mới."));
                    }
                }
            }
        }
        offers.values().removeIf(o -> System.currentTimeMillis() - o.at() > 600_000L);
        save();
    }

    /** Lính và người của nước thuê không đánh nhau được (kể cả tên, pet, TNT…). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) {
            return;
        }
        Player att = Culprit.of(e.getDamager());
        if (att == null || att == victim) {
            return;
        }
        Nation ea = employer(att.getUniqueId());
        Nation ev = employer(victim.getUniqueId());
        if (ea == null && ev == null) {
            return;
        }
        Nation na = ea != null ? ea : nationOf(att);
        Nation nv = ev != null ? ev : nationOf(victim);
        if (na != null && na == nv) {
            e.setCancelled(true);
        }
    }

    private static Nation nationOf(Player p) {
        Resident r = TownyAPI.getInstance().getResident(p);
        Town t = r == null ? null : r.getTownOrNull();
        return t == null ? null : t.getNationOrNull();
    }

    // ---------------------------------------------------------------- lệnh

    boolean command(CommandSender s, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "dangky" -> {
                if (!(s instanceof Player p)) {
                    return true;
                }
                String why = cannotRegister(p);
                if (why != null) {
                    p.sendMessage(c("&c" + why));
                    return true;
                }
                double price;
                try {
                    price = Double.parseDouble(args.length > 1 ? args[1] : "");
                } catch (NumberFormatException ex) {
                    p.sendMessage(c("&eDùng: &f/linhdanhthue dangky <giá xu mỗi hợp đồng " + fmt(contractHours) + " giờ>"));
                    return true;
                }
                if (price < minPrice || price > maxPrice) {
                    p.sendMessage(c("&cGiá phải từ " + fmt(minPrice) + " tới " + fmt(maxPrice) + " xu."));
                    return true;
                }
                data.set("dangky." + p.getUniqueId() + ".ten", p.getName());
                data.set("dangky." + p.getUniqueId() + ".gia", price);
                save();
                p.sendMessage(c("&a⚔ Đã đăng ký lính đánh thuê, giá &f" + fmt(price) + " xu&a/hợp đồng. Các nước thấy bạn ở &f/linhdanhthue&a. Rút tên: &f/linhdanhthue huy"));
            }
            case "huy" -> {
                if (s instanceof Player p) {
                    data.set("dangky." + p.getUniqueId(), null);
                    save();
                    p.sendMessage(c("&eĐã rút tên khỏi danh sách lính đánh thuê" + (employer(p.getUniqueId()) != null ? " (hợp đồng đang chạy vẫn giữ tới hết hạn)." : ".")));
                }
            }
            case "thue" -> hire(s, args);
            case "nhan" -> accept(s, true);
            case "tuchoi" -> accept(s, false);
            default -> list(s);
        }
        return true;
    }

    private String cannotRegister(Player p) {
        Resident r = TownyAPI.getInstance().getResident(p);
        Town t = r == null ? null : r.getTownOrNull();
        if (t != null && t.hasNation()) {
            Nation n = t.getNationOrNull();
            return n != null && n.getName().equalsIgnoreCase(peaceNation) ? "Dân Liên minh Hoà Bình không đi đánh thuê."
                    : "Bạn đang thuộc nước " + (n == null ? "?" : n.getName()) + ": lính đánh thuê phải là người không thuộc nước nào.";
        }
        if (war != null && war.isAlt(p.getName())) {
            return "Tài khoản phụ không đi đánh thuê được.";
        }
        if (activity != null && activity.totalHours(p.getUniqueId()) < minHours) {
            return "Cần chơi ít nhất " + fmt(minHours) + " giờ ở Bang Hội Chiến mới đăng ký đánh thuê được.";
        }
        return null;
    }

    private boolean canHire(Player p, Nation n) {
        Resident r = TownyAPI.getInstance().getResident(p);
        if (r == null || n == null) {
            return false;
        }
        if (n.getKing() == r) {
            return true;
        }
        return r.getNationRanks().stream().anyMatch(x -> hireRanks.contains(x.toLowerCase(Locale.ROOT)));
    }

    private void hire(CommandSender s, String[] args) {
        if (!(s instanceof Player p) || args.length < 2) {
            s.sendMessage(c("&eDùng: &f/linhdanhthue thue <tên lính>"));
            return;
        }
        Nation n = nationOf(p);
        if (n == null || n.getName().equalsIgnoreCase(peaceNation)) {
            p.sendMessage(c("&cChỉ nước tham chiến mới thuê lính được."));
            return;
        }
        if (!canHire(p, n)) {
            p.sendMessage(c("&cChỉ vua hoặc chức " + String.join(", ", hireRanks) + " mới được thuê lính."));
            return;
        }
        OfflinePlayer m = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (m == null || !data.isConfigurationSection("dangky." + m.getUniqueId())) {
            p.sendMessage(c("&c" + args[1] + " không có trong danh sách lính đánh thuê (/linhdanhthue)."));
            return;
        }
        if (employer(m.getUniqueId()) != null) {
            p.sendMessage(c("&c" + m.getName() + " đang có hợp đồng với nước khác."));
            return;
        }
        double price = data.getDouble("dangky." + m.getUniqueId() + ".gia");
        if (!n.getAccount().canPayFromHoldings(price)) {
            p.sendMessage(c("&cNgân khố nước không đủ " + fmt(price) + " xu."));
            return;
        }
        Player mp = m.getPlayer();
        if (mp == null) {
            p.sendMessage(c("&c" + m.getName() + " đang offline: lính phải online để nhận lời thuê."));
            return;
        }
        offers.put(mp.getUniqueId(), new Offer(n.getUUID(), n.getName(), p.getName(), System.currentTimeMillis()));
        p.sendMessage(c("&aĐã gửi lời thuê tới " + mp.getName() + " (" + fmt(price) + " xu từ ngân khố nước, " + fmt(contractHours) + " giờ). Chờ trả lời trong 10 phút."));
        mp.sendMessage(c("&6⚔ Nước &f" + n.getName() + " &6(" + p.getName() + ") muốn thuê bạn &f" + fmt(contractHours) + " giờ &6với giá &f" + fmt(price)
                + " xu &6(bạn nhận " + fmt(price * (100 - feePct) / 100) + " sau phí). &a/linhdanhthue nhan &6hoặc &c/linhdanhthue tuchoi"));
    }

    private void accept(CommandSender s, boolean yes) {
        if (!(s instanceof Player p)) {
            return;
        }
        Offer o = offers.remove(p.getUniqueId());
        if (o == null) {
            p.sendMessage(c("&7Không có lời thuê nào đang chờ."));
            return;
        }
        Player by = Bukkit.getPlayerExact(o.by());
        if (!yes) {
            p.sendMessage(c("&eĐã từ chối lời thuê của nước " + o.nationName() + "."));
            if (by != null) {
                by.sendMessage(c("&c" + p.getName() + " từ chối lời thuê."));
            }
            return;
        }
        Nation n = TownyAPI.getInstance().getNation(o.nation());
        String why = cannotRegister(p);
        if (n == null || why != null || employer(p.getUniqueId()) != null) {
            p.sendMessage(c("&cKhông nhận được: " + (why != null ? why : n == null ? "nước không còn." : "bạn đang có hợp đồng khác.")));
            return;
        }
        double price = data.getDouble("dangky." + p.getUniqueId() + ".gia");
        if (!n.getAccount().withdraw(price, "Thuê lính đánh thuê " + p.getName())) {
            p.sendMessage(c("&cNgân khố nước " + n.getName() + " không còn đủ tiền."));
            return;
        }
        double net = price * (100 - feePct) / 100;
        if (VaultEconomy.available()) {
            VaultEconomy.deposit(p, net);
        }
        long het = System.currentTimeMillis() + (long) (contractHours * 3_600_000L);
        data.set("hopdong." + p.getUniqueId() + ".nuoc", n.getUUID().toString());
        data.set("hopdong." + p.getUniqueId() + ".ten-nuoc", n.getName());
        data.set("hopdong." + p.getUniqueId() + ".het", het);
        save();
        grantImmune(p.getUniqueId(), het);
        p.sendMessage(c("&a⚔ Đã nhận hợp đồng với nước &f" + n.getName() + "&a: +" + fmt(net) + " xu. Trong " + fmt(contractHours)
                + " giờ: vào vùng vây không bị ốm, ăn kho lương thủ đô của họ, không đánh được người của họ."));
        for (Resident r : n.getResidents()) {
            Player q = r.getPlayer();
            if (q != null) {
                q.sendMessage(c("&6⚔ Lính đánh thuê &f" + p.getName() + " &6đã nhận hợp đồng với nước ta (" + fmt(contractHours) + " giờ)."));
            }
        }
    }

    /** /linhdanhthue cho người chơi: menu (22/10, trước in chat). */
    private void menu(Player p) {
        InfoMenu m = InfoMenu.of("&6⚔ Lính đánh thuê").accent(org.bukkit.Material.ORANGE_STAINED_GLASS_PANE);
        m.top(Tip.of("&6⚔ &fLính đánh thuê").accent("&6").desc("Người không thuộc nước nào, nhận tiền đánh thuê cho một nước.").line()
                .kv("Hợp đồng", fmt(contractHours) + " giờ").kv("Trả bằng", "ngân khố nước thuê")
                .gap().note("Vua / tướng bấm vào một lính để thuê.").apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_HELMET)));
        ConfigurationSection dk = data.getConfigurationSection("dangky");
        if (dk != null) {
            for (String id : dk.getKeys(false)) {
                Nation e = employer(UUID.fromString(id));
                OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(id));
                String name = dk.getString(id + ".ten", "?");
                Tip t = Tip.of("&f" + name).accent("&6").line().kv("Giá", "&e" + fmt(dk.getDouble(id + ".gia")) + " xu")
                        .kv("Đã làm", dk.getInt(id + ".xong", 0) + " hợp đồng")
                        .kv("Trạng thái", e != null ? "&cđang làm cho " + e.getName() : op.isOnline() ? "&arảnh, đang online" : "&7offline");
                if (e == null) {
                    t.line().click("Bấm để thuê (vua / tướng)");
                }
                org.bukkit.inventory.ItemStack head = new org.bukkit.inventory.ItemStack(org.bukkit.Material.PLAYER_HEAD);
                head.editMeta(org.bukkit.inventory.meta.SkullMeta.class, sm -> sm.setOwningPlayer(op));
                m.add(t.apply(head), e == null ? InfoMenu.run("linhdanhthue thue " + name) : null);
            }
        }
        if (dk == null || dk.getKeys(false).isEmpty()) {
            m.add(Tip.of("&7Chưa ai đăng ký").apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.GRAY_DYE)));
        }
        m.bar(Tip.of("&a✚ &fĐăng ký làm lính").accent("&a").desc("Chỉ người không thuộc nước nào").line().kv("Lệnh", "/linhdanhthue dangky <giá>")
                .click("Bấm để điền lệnh").apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.WRITABLE_BOOK)),
                InfoMenu.suggest("/linhdanhthue dangky", "Gõ giá thuê (xu)"));
        m.bar(Tip.of("&c✖ &fRút tên").accent("&c").line().kv("Lệnh", "/linhdanhthue huy").click("Bấm để rút")
                .apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.RED_DYE)), InfoMenu.run("linhdanhthue huy"));
        m.open(p);
    }

    private void list(CommandSender s) {
        if (s instanceof Player pm) {
            menu(pm);
            return;
        }
        s.sendMessage(c("&6⚔ Lính đánh thuê &7(người không thuộc nước nào, thuê bằng ngân khố nước, " + fmt(contractHours) + " giờ/hợp đồng)"));
        ConfigurationSection dk = data.getConfigurationSection("dangky");
        int shown = 0;
        if (dk != null) {
            for (String id : dk.getKeys(false)) {
                Nation e = employer(UUID.fromString(id));
                OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(id));
                s.sendMessage(c("&f" + dk.getString(id + ".ten") + " &7— &e" + fmt(dk.getDouble(id + ".gia")) + " xu &7· " + dk.getInt(id + ".xong", 0)
                        + " hợp đồng đã làm · " + (e != null ? "&cđang làm cho " + e.getName() : op.isOnline() ? "&arảnh, đang online" : "&7offline")));
                shown++;
            }
        }
        if (shown == 0) {
            s.sendMessage(c("&7Chưa ai đăng ký."));
        }
        s.sendMessage(c("&7Đăng ký: &f/linhdanhthue dangky <giá> &7· rút: &f/linhdanhthue huy &7· vua/tướng thuê: &f/linhdanhthue thue <tên>"));
    }

    List<String> tab(String[] a) {
        if (a.length == 1) {
            return List.of("dangky", "huy", "thue", "nhan", "tuchoi").stream().filter(x -> x.startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (a.length == 2 && a[0].equalsIgnoreCase("thue")) {
            ConfigurationSection dk = data.getConfigurationSection("dangky");
            return dk == null ? List.of() : dk.getKeys(false).stream().map(id -> dk.getString(id + ".ten", "")).filter(n -> n.toLowerCase(Locale.ROOT)
                    .startsWith(a[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được linhdanhthue.yml: " + e.getMessage());
        }
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static Component c(String s) {
        return Tip.ns(LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false));
    }
}
