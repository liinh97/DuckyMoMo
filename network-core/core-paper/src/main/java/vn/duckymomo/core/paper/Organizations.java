package vn.duckymomo.core.paper;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.event.PlotPreChangeTypeEvent;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownBlockType;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

/**
 * Tổ chức và giấy phép của dân hoà bình (chốt 14/10, mục 10.2, 11A-12): >= min-members dân hoà bình lập tổ chức, xin giấy phép,
 * Hội đồng Hoà Bình duyệt (quá bán), phí weekly-fee/tuần trừ vào quỹ tổ chức (hết tiền thì treo), hết mùa hết hạn, Hội Bàn Tròn
 * thu hồi được (2/3). Thành viên có giấy phép được vào nhóm LuckPerms giayphep-<loại> (server=banghoi-1):
 *  - thuonghoi: thêm shop rương (QuickShop limits), thêm món đấu giá (zauctionhouse.max.15), /quangcao 1 lần mỗi giờ;
 *  - sukien: /sukien tao|den|thuong|dautruong|ket (thông báo, chữ nổi, điểm dịch chuyển 24 giờ, thưởng bằng quỹ, đấu trường không mất đồ).
 * Dữ liệu: plugins/DuckyMoMoCore/tochuc.yml.
 */
final class Organizations implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final long DAY = 86_400_000L;
    static final List<String> TYPES = List.of("thuonghoi", "sukien");

    private final DuckyMoMoCore plugin;
    private final PeaceAlliance peace;
    private final PeaceCouncil council;
    private final int minMembers;
    private final double weeklyFee;
    private final Map<String, Integer> slots = new HashMap<>();
    private final String season;
    private final int adCooldownMinutes;
    private final int eventHours;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, String[]> invites = new HashMap<>();
    private final Map<UUID, Long> lastAd = new HashMap<>();
    private final NamespacedKey holoKey;
    private boolean arenaBypass;

    Organizations(DuckyMoMoCore plugin, PeaceAlliance peace, PeaceCouncil council, ConfigurationSection c, String season) {
        this.plugin = plugin;
        this.peace = peace;
        this.council = council;
        this.minMembers = c.getInt("min-members", 3);
        this.weeklyFee = c.getDouble("weekly-fee", 100);
        for (String t : TYPES) {
            slots.put(t, c.getInt("slots." + t, t.equals("thuonghoi") ? 3 : 2));
        }
        this.season = season;
        this.adCooldownMinutes = c.getInt("ad-cooldown-minutes", 60);
        this.eventHours = c.getInt("event-teleport-hours", 24);
        this.file = new File(plugin.getDataFolder(), "tochuc.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        this.holoKey = new NamespacedKey(plugin, "sukien_holo");
        council.onLicenseResult(r -> licenseDecided(r[0], Boolean.parseBoolean(r[1])));
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::hourly, 1200L, 72_000L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::eventTick, 1200L, 1200L);
        setupGroups();
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s);
    }

    private static void console(String cmd) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    private static String typeName(String t) {
        return t.equals("thuonghoi") ? "Thương Hội" : "Tổ chức Sự kiện";
    }

    /** Tạo nhóm LuckPerms một lần (đánh dấu trong tochuc.yml). */
    private void setupGroups() {
        if (data.getBoolean("da-tao-nhom")) {
            return;
        }
        console("lp creategroup giayphep-thuonghoi");
        console("lp creategroup giayphep-sukien");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            console("lp group giayphep-thuonghoi permission set duckymomo.giayphep.thuonghoi true server=banghoi-1");
            console("lp group giayphep-thuonghoi permission set zauctionhouse.max.15 true server=banghoi-1");
            console("lp group giayphep-sukien permission set duckymomo.giayphep.sukien true server=banghoi-1");
            data.set("da-tao-nhom", true);
            save();
        }, 60L);
    }

    // ------------------------------------------------------------------ tra cứu

    private String orgOf(String name) {
        ConfigurationSection os = data.getConfigurationSection("tc");
        if (os == null) {
            return null;
        }
        for (String id : os.getKeys(false)) {
            for (String m : os.getStringList(id + ".thanhvien")) {
                if (m.equalsIgnoreCase(name)) {
                    return id;
                }
            }
        }
        return null;
    }

    private String findOrg(String nameOrId) {
        ConfigurationSection os = data.getConfigurationSection("tc");
        if (os == null) {
            return null;
        }
        for (String id : os.getKeys(false)) {
            if (id.equalsIgnoreCase(nameOrId) || os.getString(id + ".ten", "").equalsIgnoreCase(nameOrId)) {
                return id;
            }
        }
        return null;
    }

    private boolean isPeaceResident(String name) {
        Resident r = Members.resident(name);
        return r != null && peace.isMember(r.getTownOrNull());
    }

    private boolean licensed(String id) {
        return "CO".equals(data.getString("tc." + id + ".giayphep")) && season.equals(data.getString("tc." + id + ".mua"));
    }

    private long countLicensed(String type) {
        ConfigurationSection os = data.getConfigurationSection("tc");
        return os == null ? 0 : os.getKeys(false).stream().filter(id -> type.equals(os.getString(id + ".loai")) && licensed(id)).count();
    }

    /** Đồng bộ nhóm LuckPerms: thành viên là dân hoà bình của tổ chức có giấy phép thì vào nhóm, còn lại thì ra. */
    private void sync(String id) {
        String group = "giayphep-" + data.getString("tc." + id + ".loai");
        List<String> want = new ArrayList<>();
        if (licensed(id)) {
            for (String m : data.getStringList("tc." + id + ".thanhvien")) {
                if (isPeaceResident(m)) {
                    want.add(m.toLowerCase(Locale.ROOT));
                }
            }
        }
        List<String> have = data.getStringList("tc." + id + ".da-cap");
        for (String m : have) {
            if (!want.contains(m)) {
                console("lp user " + lpTarget(m) + " parent remove " + group + " server=banghoi-1");
            }
        }
        for (String m : want) {
            if (!have.contains(m)) {
                console("lp user " + lpTarget(m) + " parent add " + group + " server=banghoi-1");
            }
        }
        data.set("tc." + id + ".da-cap", want);
        save();
    }

    /** Hội Bàn Tròn thu hồi giấy phép. */
    boolean revoke(String nameOrId) {
        String id = findOrg(nameOrId);
        if (id == null) {
            return false;
        }
        data.set("tc." + id + ".giayphep", "THUHOI");
        save();
        sync(id);
        return true;
    }

    // ------------------------------------------------------------------ giấy phép, phí

    private void licenseDecided(String id, boolean pass) {
        if (!data.isSet("tc." + id)) {
            return;
        }
        String type = data.getString("tc." + id + ".loai");
        if (pass && countLicensed(type) >= slots.get(type)) {
            pass = false;
            Bukkit.broadcast(c("&e" + typeName(type) + " đã hết suất (" + slots.get(type) + "), giấy phép của " + data.getString("tc." + id + ".ten") + " không được cấp."));
        }
        if (pass) {
            data.set("tc." + id + ".giayphep", "CO");
            data.set("tc." + id + ".mua", season);
            data.set("tc." + id + ".phi-den", System.currentTimeMillis());
            save();
            payFee(id);
        } else {
            data.set("tc." + id + ".giayphep", "KHONG");
            save();
        }
        sync(id);
    }

    private void payFee(String id) {
        double fund = data.getDouble("tc." + id + ".quy");
        if (fund >= weeklyFee) {
            data.set("tc." + id + ".quy", fund - weeklyFee);
            data.set("tc." + id + ".phi-den", data.getLong("tc." + id + ".phi-den") + 7 * DAY);
            data.set("tc." + id + ".treo", false);
        } else if (!data.getBoolean("tc." + id + ".treo")) {
            data.set("tc." + id + ".treo", true);
            data.set("tc." + id + ".giayphep", "TREO");
            Bukkit.broadcast(c("&e" + data.getString("tc." + id + ".ten") + ": quỹ không đủ " + weeklyFee + " xu phí tuần, giấy phép bị treo."));
        }
        save();
    }

    private void hourly() {
        ConfigurationSection os = data.getConfigurationSection("tc");
        if (os == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (String id : os.getKeys(false)) {
            String st = os.getString(id + ".giayphep", "");
            if (st.equals("CO") && !season.equals(os.getString(id + ".mua"))) {
                os.set(id + ".giayphep", "HETHAN");
            } else if (st.equals("CO") && now >= os.getLong(id + ".phi-den")) {
                payFee(id);
            }
            sync(id);
        }
        save();
    }

    // ------------------------------------------------------------------ /tochuc

    boolean command(CommandSender s, String[] args) {
        String sub = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("danhsach") || !(s instanceof Player)) {
            list(s);
            return true;
        }
        Player p = (Player) s;
        String mine = orgOf(p.getName());
        switch (sub) {
            case "lap" -> {
                if (args.length < 3 || !TYPES.contains(args[1].toLowerCase(Locale.ROOT))) {
                    p.sendMessage(c("&c/tochuc lap thuonghoi|sukien <tên tổ chức>"));
                } else if (mine != null) {
                    p.sendMessage(c("&cBạn đã ở trong một tổ chức."));
                } else if (!isPeaceResident(p.getName())) {
                    p.sendMessage(c("&cChỉ dân Liên minh Hoà Bình mới lập tổ chức được."));
                } else {
                    String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                    if (findOrg(name) != null) {
                        p.sendMessage(c("&cĐã có tổ chức tên này."));
                        return true;
                    }
                    int next = data.getInt("so") + 1;
                    data.set("so", next);
                    String id = "T" + next;
                    data.set("tc." + id + ".ten", name);
                    data.set("tc." + id + ".loai", args[1].toLowerCase(Locale.ROOT));
                    data.set("tc." + id + ".truong", p.getName());
                    data.set("tc." + id + ".thanhvien", new ArrayList<>(List.of(p.getName())));
                    data.set("tc." + id + ".quy", 0.0);
                    data.set("tc." + id + ".giayphep", "CHUA");
                    save();
                    p.sendMessage(c("&aĐã lập " + typeName(args[1].toLowerCase(Locale.ROOT)) + " &f" + name + "&a. Mời thêm: &f/tochuc moi <tên>&a, đủ "
                            + minMembers + " người thì &f/tochuc xinphep"));
                }
            }
            case "moi" -> {
                if (mine == null || !p.getName().equalsIgnoreCase(data.getString("tc." + mine + ".truong")) || args.length < 2) {
                    p.sendMessage(c("&cChỉ người đứng đầu tổ chức mời được: /tochuc moi <tên>"));
                    return true;
                }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null || !isPeaceResident(t.getName()) || orgOf(t.getName()) != null) {
                    p.sendMessage(c("&c" + args[1] + " phải đang online, là dân hoà bình và chưa ở tổ chức nào."));
                    return true;
                }
                invites.put(t.getUniqueId(), new String[] {mine, Long.toString(System.currentTimeMillis())});
                t.sendMessage(c("&a" + p.getName() + " mời bạn vào &f" + data.getString("tc." + mine + ".ten") + "&a. Gõ &f/tochuc vao &atrong 10 phút."));
                p.sendMessage(c("&aĐã mời " + t.getName()));
            }
            case "vao" -> {
                String[] inv = invites.remove(p.getUniqueId());
                if (inv == null || System.currentTimeMillis() - Long.parseLong(inv[1]) > 600_000L || !data.isSet("tc." + inv[0])) {
                    p.sendMessage(c("&cKhông có lời mời nào (hoặc đã quá 10 phút)."));
                    return true;
                }
                List<String> m = new ArrayList<>(data.getStringList("tc." + inv[0] + ".thanhvien"));
                m.add(p.getName());
                data.set("tc." + inv[0] + ".thanhvien", m);
                save();
                sync(inv[0]);
                p.sendMessage(c("&aĐã vào " + data.getString("tc." + inv[0] + ".ten")));
            }
            case "roi", "duoi" -> {
                String who = sub.equals("roi") ? p.getName() : args.length >= 2 ? args[1] : "";
                if (mine == null || sub.equals("duoi") && !p.getName().equalsIgnoreCase(data.getString("tc." + mine + ".truong"))) {
                    p.sendMessage(c("&cKhông làm được."));
                    return true;
                }
                List<String> m = new ArrayList<>(data.getStringList("tc." + mine + ".thanhvien"));
                m.removeIf(x -> x.equalsIgnoreCase(who));
                if (m.isEmpty()) {
                    data.set("tc." + mine + ".thanhvien", m);
                    data.set("tc." + mine + ".giayphep", "GIAITAN");
                } else {
                    data.set("tc." + mine + ".thanhvien", m);
                    if (who.equalsIgnoreCase(data.getString("tc." + mine + ".truong"))) {
                        data.set("tc." + mine + ".truong", m.get(0));
                    }
                }
                save();
                sync(mine);
                p.sendMessage(c("&aĐã cập nhật thành viên."));
            }
            case "gop" -> {
                double amount;
                try {
                    amount = Double.parseDouble(args.length >= 2 ? args[1] : "x");
                } catch (NumberFormatException e) {
                    amount = -1;
                }
                if (mine == null || amount <= 0) {
                    p.sendMessage(c("&c/tochuc gop <số tiền> (phải ở trong tổ chức)"));
                    return true;
                }
                if (!VaultEconomy.withdraw(p, amount)) {
                    p.sendMessage(c("&cKhông đủ tiền."));
                    return true;
                }
                data.set("tc." + mine + ".quy", data.getDouble("tc." + mine + ".quy") + amount);
                save();
                if (data.getBoolean("tc." + mine + ".treo") && data.getDouble("tc." + mine + ".quy") >= weeklyFee) {
                    data.set("tc." + mine + ".giayphep", "CO");
                    data.set("tc." + mine + ".phi-den", System.currentTimeMillis());
                    payFee(mine);
                    sync(mine);
                }
                p.sendMessage(c(String.format(Locale.ROOT, "&aĐã góp %.0f xu. Quỹ: %.0f xu.", amount, data.getDouble("tc." + mine + ".quy"))));
            }
            case "xinphep" -> {
                if (mine == null || !p.getName().equalsIgnoreCase(data.getString("tc." + mine + ".truong"))) {
                    p.sendMessage(c("&cChỉ người đứng đầu tổ chức xin phép được."));
                    return true;
                }
                String type = data.getString("tc." + mine + ".loai");
                long members = data.getStringList("tc." + mine + ".thanhvien").stream().filter(this::isPeaceResident).count();
                if (members < minMembers) {
                    p.sendMessage(c("&cCần ít nhất " + minMembers + " thành viên là dân hoà bình (đang có " + members + ")."));
                } else if (countLicensed(type) >= slots.get(type)) {
                    p.sendMessage(c("&c" + typeName(type) + " đã hết suất (" + slots.get(type) + ")."));
                } else if (licensed(mine)) {
                    p.sendMessage(c("&aTổ chức đã có giấy phép."));
                } else {
                    data.set("tc." + mine + ".giayphep", "CHO");
                    save();
                    council.propose("GIAYPHEP", mine, p.getName(), "", 0.5, null);
                    p.sendMessage(c("&aĐã gửi Hội đồng Hoà Bình. Phí giấy phép " + weeklyFee + " xu/tuần trừ vào quỹ tổ chức (/tochuc gop)."));
                }
            }
            default -> show(p, mine);
        }
        return true;
    }

    private void show(CommandSender s, String id) {
        if (id == null) {
            s.sendMessage(c("&7Bạn chưa ở tổ chức nào. &f/tochuc lap thuonghoi|sukien <tên> &7· &f/tochuc danhsach"));
            s.sendMessage(c("&7Giấy phép: Thương Hội (thêm shop rương, thêm món đấu giá, /quangcao), Tổ chức Sự kiện (/sukien). Phí "
                    + weeklyFee + " xu/tuần, Hội đồng Hoà Bình duyệt."));
            return;
        }
        s.sendMessage(c(String.format(Locale.ROOT, "&a%s &7(%s, %s) - quỹ %.0f xu, giấy phép: &f%s", data.getString("tc." + id + ".ten"),
                typeName(data.getString("tc." + id + ".loai")), id, data.getDouble("tc." + id + ".quy"), data.getString("tc." + id + ".giayphep"))));
        s.sendMessage(c("&7Đứng đầu: &f" + data.getString("tc." + id + ".truong") + " &7· Thành viên: &f"
                + String.join(", ", data.getStringList("tc." + id + ".thanhvien"))));
        s.sendMessage(c("&7Lệnh: &f/tochuc moi <tên> · vao · roi · duoi <tên> · gop <tiền> · xinphep"));
    }

    private void list(CommandSender s) {
        ConfigurationSection os = data.getConfigurationSection("tc");
        s.sendMessage(c("&aTổ chức hoà bình"));
        if (os == null) {
            return;
        }
        for (String id : os.getKeys(false)) {
            String st = os.getString(id + ".giayphep", "");
            if (!st.equals("GIAITAN")) {
                s.sendMessage(c("&f" + os.getString(id + ".ten") + " &7(" + typeName(os.getString(id + ".loai")) + ", " + id + "): " + st
                        + ", " + os.getStringList(id + ".thanhvien").size() + " người"));
            }
        }
    }

    // ------------------------------------------------------------------ /quangcao

    boolean adCommand(CommandSender s, String[] args) {
        if (!(s instanceof Player p) || !p.hasPermission("duckymomo.giayphep.thuonghoi") || args.length == 0) {
            s.sendMessage(c("&cChỉ thành viên Thương Hội có giấy phép: /quangcao <nội dung>"));
            return true;
        }
        Long last = lastAd.get(p.getUniqueId());
        long wait = last == null ? 0 : last + adCooldownMinutes * 60_000L - System.currentTimeMillis();
        if (wait > 0) {
            p.sendMessage(c("&cMỗi " + adCooldownMinutes + " phút rao một lần, còn " + (wait / 60_000 + 1) + " phút."));
            return true;
        }
        lastAd.put(p.getUniqueId(), System.currentTimeMillis());
        String org = orgOf(p.getName());
        String msg = String.join(" ", args).replaceAll("&[k-or]", "");
        Bukkit.broadcast(c("&6[" + (org == null ? "Thương Hội" : data.getString("tc." + org + ".ten")) + "] &f" + p.getName() + "&7: &e" + msg));
        return true;
    }

    // ------------------------------------------------------------------ /sukien

    boolean eventCommand(CommandSender s, String[] args) {
        String sub = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("den") || sub.isEmpty()) {
            return eventGo(s, args);
        }
        if (!(s instanceof Player p) || !p.hasPermission("duckymomo.giayphep.sukien")) {
            s.sendMessage(c("&cChỉ thành viên Tổ chức Sự kiện có giấy phép. Người chơi: &f/sukien &7(xem) &f/sukien den <mã>"));
            return true;
        }
        String org = orgOf(p.getName());
        switch (sub) {
            case "tao" -> {
                if (args.length < 3 || !peace.inPeaceLand(p.getLocation())) {
                    p.sendMessage(c("&c/sukien tao <sau bao nhiêu phút> <tên sự kiện> (đứng trong đất hoà bình)"));
                    return true;
                }
                int minutes;
                try {
                    minutes = Math.max(1, Integer.parseInt(args[1]));
                } catch (NumberFormatException e) {
                    p.sendMessage(c("&cSố phút không đúng."));
                    return true;
                }
                String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).replaceAll("&[k-or]", "");
                int next = data.getInt("so-sk") + 1;
                data.set("so-sk", next);
                String id = "S" + next;
                Location l = p.getLocation();
                long start = System.currentTimeMillis() + minutes * 60_000L;
                data.set("sk." + id + ".ten", name);
                data.set("sk." + id + ".tochuc", org);
                data.set("sk." + id + ".bd", start);
                data.set("sk." + id + ".het", start + eventHours * 3_600_000L);
                data.set("sk." + id + ".noi", l.getWorld().getName() + " " + l.getX() + " " + l.getY() + " " + l.getZ());
                save();
                spawnHolo(id, l, name, org);
                Bukkit.broadcast(c("&d✦ Sự kiện &f" + name + " &dcủa " + data.getString("tc." + org + ".ten") + " bắt đầu sau " + minutes
                        + " phút. Tới nơi: &f/sukien den " + id));
            }
            case "thuong" -> {
                Player t = args.length >= 3 ? Bukkit.getPlayerExact(args[1]) : null;
                double amount;
                try {
                    amount = args.length >= 3 ? Double.parseDouble(args[2]) : -1;
                } catch (NumberFormatException e) {
                    amount = -1;
                }
                if (t == null || amount <= 0 || org == null || data.getDouble("tc." + org + ".quy") < amount) {
                    p.sendMessage(c("&c/sukien thuong <người online> <tiền> (lấy từ quỹ tổ chức, quỹ phải đủ)"));
                    return true;
                }
                data.set("tc." + org + ".quy", data.getDouble("tc." + org + ".quy") - amount);
                save();
                VaultEconomy.deposit(t, amount);
                Bukkit.broadcast(c(String.format(Locale.ROOT, "&d✦ %s trao thưởng &f%.0f xu &dcho &f%s", data.getString("tc." + org + ".ten"), amount, t.getName())));
            }
            case "dautruong" -> {
                TownBlock tb = TownyAPI.getInstance().getTownBlock(p.getLocation());
                Resident r = TownyAPI.getInstance().getResident(p);
                Town t = tb == null ? null : tb.getTownOrNull();
                if (t == null || r == null || r.getTownOrNull() != t || !peace.isMember(t)) {
                    p.sendMessage(c("&cĐứng trong một ô của thị trấn hoà bình của bạn."));
                    return true;
                }
                boolean toArena = tb.getType() != TownBlockType.ARENA;
                arenaBypass = true;
                tb.setType(toArena ? TownBlockType.ARENA : TownBlockType.RESIDENTIAL);
                arenaBypass = false;
                tb.save();
                p.sendMessage(c(toArena ? "&aÔ này thành đấu trường: đánh nhau không mất đồ." : "&aÔ này thôi làm đấu trường."));
            }
            case "ket" -> {
                ConfigurationSection sk = data.getConfigurationSection("sk");
                if (sk != null) {
                    for (String id : sk.getKeys(false)) {
                        if (org != null && org.equals(sk.getString(id + ".tochuc"))) {
                            endEvent(id);
                        }
                    }
                }
                p.sendMessage(c("&aĐã kết thúc sự kiện của tổ chức."));
            }
            default -> p.sendMessage(c("&7/sukien tao <phút> <tên> · thuong <người> <tiền> · dautruong · ket · den <mã>"));
        }
        return true;
    }

    private boolean eventGo(CommandSender s, String[] args) {
        ConfigurationSection sk = data.getConfigurationSection("sk");
        if (args.length < 2 || !(s instanceof Player p)) {
            s.sendMessage(c("&dSự kiện đang mở"));
            if (sk != null) {
                for (String id : sk.getKeys(false)) {
                    s.sendMessage(c("&f" + id + "&7: " + sk.getString(id + ".ten") + " &7- /sukien den " + id));
                }
            }
            return true;
        }
        String id = args[1].toUpperCase(Locale.ROOT);
        if (sk == null || !sk.isSet(id)) {
            p.sendMessage(c("&cKhông có sự kiện " + id));
            return true;
        }
        String[] n = sk.getString(id + ".noi", "").split(" ");
        Location l = new Location(Bukkit.getWorld(n[0]), Double.parseDouble(n[1]), Double.parseDouble(n[2]), Double.parseDouble(n[3]));
        p.teleportAsync(l);
        return true;
    }

    private void spawnHolo(String id, Location l, String name, String org) {
        String[] lines = {"&d✦ " + name + " ✦", "&7" + data.getString("tc." + org + ".ten") + " · /sukien den " + id};
        double y = 2.3;
        for (String line : lines) {
            l.getWorld().spawn(l.clone().add(0, y, 0), ArmorStand.class, a -> {
                a.setMarker(true);
                a.setInvisible(true);
                a.setGravity(false);
                a.setInvulnerable(true);
                a.setPersistent(true);
                a.customName(c(line));
                a.setCustomNameVisible(true);
                a.getPersistentDataContainer().set(holoKey, PersistentDataType.STRING, id);
            });
            y -= 0.28;
        }
    }

    private void endEvent(String id) {
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntitiesByClass(ArmorStand.class)) {
                if (id.equals(e.getPersistentDataContainer().get(holoKey, PersistentDataType.STRING))) {
                    e.remove();
                }
            }
        }
        data.set("sk." + id, null);
        save();
    }

    private void eventTick() {
        ConfigurationSection sk = data.getConfigurationSection("sk");
        if (sk == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Set<String> done = new HashSet<>();
        for (String id : sk.getKeys(false)) {
            long start = sk.getLong(id + ".bd");
            long left = (start - now) / 60_000L;
            if (now < start && (left == 10 || left == 5 || left == 1)) {
                Bukkit.broadcast(c("&d✦ Sự kiện &f" + sk.getString(id + ".ten") + " &dbắt đầu sau " + left + " phút: &f/sukien den " + id));
            }
            if (now >= sk.getLong(id + ".het")) {
                done.add(id);
            }
        }
        done.forEach(this::endEvent);
    }

    /** Chỉ /sukien dautruong mới đổi ô thành đấu trường. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlotType(PlotPreChangeTypeEvent e) {
        if (!arenaBypass && e.getNewType() == TownBlockType.ARENA) {
            e.setCancelled(true);
            e.setCancelMessage("Đấu trường chỉ do Tổ chức Sự kiện có giấy phép mở (/sukien dautruong).");
        }
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được tochuc.yml: " + e.getMessage());
        }
    }

    /** LuckPerms từ chối tên có dấu chấm (Bedrock: .ten) trong "lp user": dùng UUID nếu server đã biết người chơi. */
    private static String lpTarget(String name) {
        if (name.matches("[A-Za-z0-9_]{1,16}")) {
            return name;
        }
        org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayerIfCached(name);
        return op != null ? op.getUniqueId().toString() : name;
    }
}
