package vn.duckymomo.core.paper;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Hội đồng Hoà Bình (chốt 14/10, mục 10.1, 11A-13): thị trưởng các thị trấn hoà bình (thị trấn có >= min-active dân hoạt động
 * mới có phiếu, kinh đô của server không có phiếu).
 *  - Đại diện: nhiệm kỳ term-days ngày, bầu trong election-days ngày cuối nhiệm kỳ, tối đa max-terms nhiệm kỳ liền,
 *    bãi nhiệm khi 2/3 số phiếu đồng ý; không ai ứng cử thì thị trưởng thị trấn hoà bình lớn nhất tạm giữ.
 *  - Kiến nghị (bỏ phiếu từ xa bằng /hoidong phieu): duyệt giấy phép tổ chức (quá bán), đuổi thị trấn làm tay sai (quá bán),
 *    bãi nhiệm đại diện (2/3). Hết hạn motion-days ngày mà chưa đủ phiếu thì không thông qua.
 * Dữ liệu: plugins/DuckyMoMoCore/hoidong.yml. Phiếu công khai (ai bỏ gì đều xem được).
 */
final class PeaceCouncil {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("HH:mm dd/MM").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final long DAY = 86_400_000L;

    private final DuckyMoMoCore plugin;
    private final PeaceAlliance peace;
    private final Activity activity;
    private final int minActive;
    private final int termDays;
    private final int electionDays;
    private final int maxTerms;
    private final int motionDays;
    private final File file;
    private final YamlConfiguration data;
    private Consumer<String[]> licenseResult;

    PeaceCouncil(DuckyMoMoCore plugin, PeaceAlliance peace, Activity activity, ConfigurationSection c) {
        this.plugin = plugin;
        this.peace = peace;
        this.activity = activity;
        this.minActive = c.getInt("min-active-residents", 3);
        this.termDays = c.getInt("term-days", 30);
        this.electionDays = c.getInt("election-days", 2);
        this.maxTerms = c.getInt("max-terms", 2);
        this.motionDays = c.getInt("motion-days", 3);
        this.file = new File(plugin.getDataFolder(), "hoidong.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 600L, 1200L);
    }

    /** Organizations đăng ký nhận kết quả duyệt giấy phép: args = {mã tổ chức, "true"/"false"}. */
    void onLicenseResult(Consumer<String[]> cb) {
        this.licenseResult = cb;
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s);
    }

    // ------------------------------------------------------------------ thành viên, phiếu

    /** Các thị trấn có phiếu: trong liên minh, không phải kinh đô, có >= minActive dân hoạt động. */
    List<Town> votingTowns() {
        List<Town> out = new ArrayList<>();
        Nation n = peace.nation();
        if (n == null) {
            return out;
        }
        for (Town t : n.getTowns()) {
            if (!t.getName().equalsIgnoreCase(peace.capitalName()) && Members.activeCount(activity, t) >= minActive) {
                out.add(t);
            }
        }
        return out;
    }

    private Town voterTown(Player p) {
        Resident r = TownyAPI.getInstance().getResident(p);
        Town t = r == null ? null : r.getTownOrNull();
        if (t == null || !r.isMayor() || !votingTowns().contains(t)) {
            return null;
        }
        return t;
    }

    /** Tên đại diện hoà bình hiện tại (null nếu chưa có). */
    String representative() {
        return data.getString("daidien.ten");
    }

    boolean isRepresentative(CommandSender s) {
        String rep = representative();
        return rep != null && rep.equalsIgnoreCase(s.getName());
    }

    // ------------------------------------------------------------------ mỗi phút

    private void tick() {
        long now = System.currentTimeMillis();
        if (peace.nation() == null) {
            return;
        }
        long termEnd = data.getLong("daidien.den");
        // Mở bầu cử trong electionDays ngày cuối nhiệm kỳ (hoặc ngay nếu chưa có đại diện)
        if (!data.getBoolean("bau.mo") && (representative() == null || now >= termEnd - electionDays * DAY)) {
            openElection(now, representative() == null ? now + electionDays * DAY : Math.max(termEnd, now + DAY));
        }
        if (data.getBoolean("bau.mo") && now >= data.getLong("bau.dong")) {
            closeElection(now);
        }
        if (representative() == null) {
            interim(now);
        }
        // Kiến nghị hết hạn
        ConfigurationSection ks = data.getConfigurationSection("kien");
        if (ks != null) {
            for (String id : ks.getKeys(false)) {
                if (!ks.isSet(id + ".ketqua") && now >= ks.getLong(id + ".den")) {
                    finish(id, false);
                }
            }
        }
    }

    private void openElection(long now, long close) {
        data.set("bau.mo", true);
        data.set("bau.tu", now);
        data.set("bau.dong", close);
        data.set("bau.ungvien", new ArrayList<String>());
        data.set("bau.phieu", null);
        save();
        Bukkit.broadcast(c("&a[Liên minh Hoà Bình] Mở bầu đại diện tới " + WHEN.format(Instant.ofEpochMilli(close))
                + ". Dân hoà bình ứng cử: &f/hoidong ungcu&a; thị trưởng bỏ phiếu: &f/hoidong bau <tên>"));
    }

    private void closeElection(long now) {
        Map<String, Integer> count = new HashMap<>();
        ConfigurationSection ph = data.getConfigurationSection("bau.phieu");
        if (ph != null) {
            for (String town : ph.getKeys(false)) {
                count.merge(ph.getString(town).toLowerCase(Locale.ROOT), 1, Integer::sum);
            }
        }
        String winner = null;
        int best = 0;
        for (String cand : data.getStringList("bau.ungvien")) {
            int v = count.getOrDefault(cand.toLowerCase(Locale.ROOT), 0);
            if (v > best || v == best && winner != null && townSize(cand) > townSize(winner)) {
                best = v;
                winner = cand;
            }
        }
        data.set("bau.mo", false);
        if (winner != null && best > 0) {
            String prev = representative();
            int terms = prev != null && prev.equalsIgnoreCase(winner) ? data.getInt("daidien.lien") + 1 : 1;
            setRep(winner, now, terms, false);
            Bukkit.broadcast(c("&a[Liên minh Hoà Bình] &f" + winner + " &ađắc cử đại diện (" + best + " phiếu), nhiệm kỳ " + termDays + " ngày."));
        } else {
            data.set("daidien", null);
            interim(now);
        }
        save();
    }

    private int townSize(String residentName) {
        Resident r = Members.resident(residentName);
        Town t = r == null ? null : r.getTownOrNull();
        return t == null ? 0 : t.getNumTownBlocks();
    }

    /** Không ai đắc cử: thị trưởng thị trấn hoà bình lớn nhất tạm giữ tới lần bầu sau. */
    private void interim(long now) {
        Town biggest = votingTowns().stream().max(Comparator.comparingInt(Town::getNumTownBlocks)).orElse(null);
        if (biggest == null || biggest.getMayor() == null) {
            return;
        }
        setRep(biggest.getMayor().getName(), now, data.getInt("daidien.lien"), true);
        Bukkit.broadcast(c("&a[Liên minh Hoà Bình] Không có đại diện đắc cử: &f" + biggest.getMayor().getName()
                + " &a(thị trưởng " + biggest.getName() + ", thị trấn lớn nhất) tạm giữ chức."));
    }

    private void setRep(String name, long now, int terms, boolean interim) {
        data.set("daidien.ten", name);
        data.set("daidien.tu", now);
        data.set("daidien.den", now + termDays * DAY);
        data.set("daidien.lien", terms);
        data.set("daidien.tam", interim);
        save();
    }

    // ------------------------------------------------------------------ kiến nghị

    /** Tạo kiến nghị. loai: GIAYPHEP | DUOI | BAIMIEN. Trả về mã. */
    String propose(String loai, String target, String proposer, String note, double threshold, String recusedTownUuid) {
        int next = data.getInt("so-kien") + 1;
        data.set("so-kien", next);
        String id = "K" + next;
        long now = System.currentTimeMillis();
        data.set("kien." + id + ".loai", loai);
        data.set("kien." + id + ".muc", target);
        data.set("kien." + id + ".nguoi", proposer);
        data.set("kien." + id + ".ghichu", note);
        data.set("kien." + id + ".nguong", threshold);
        data.set("kien." + id + ".tu", now);
        data.set("kien." + id + ".den", now + motionDays * DAY);
        if (recusedTownUuid != null) {
            data.set("kien." + id + ".tranh", recusedTownUuid);
        }
        save();
        Bukkit.broadcast(c("&a[Hội đồng Hoà Bình] Kiến nghị &f" + id + "&a: " + describe(id) + ". Thị trưởng hoà bình bỏ phiếu: &f/hoidong phieu "
                + id + " co|khong &7(hạn " + motionDays + " ngày)"));
        return id;
    }

    private String describe(String id) {
        String loai = data.getString("kien." + id + ".loai", "");
        String muc = data.getString("kien." + id + ".muc", "");
        return switch (loai) {
            case "GIAYPHEP" -> "cấp giấy phép cho tổ chức " + muc;
            case "DUOI" -> "đuổi thị trấn " + muc + " khỏi liên minh (" + data.getString("kien." + id + ".ghichu", "") + ")";
            case "BAIMIEN" -> "bãi nhiệm đại diện " + muc;
            default -> loai + " " + muc;
        };
    }

    private void vote(Player p, String id, boolean yes) {
        if (!data.isSet("kien." + id + ".loai") || data.isSet("kien." + id + ".ketqua")) {
            p.sendMessage(c("&cKhông có kiến nghị đang mở mã " + id));
            return;
        }
        Town t = voterTown(p);
        if (t == null) {
            p.sendMessage(c("&cChỉ thị trưởng thị trấn hoà bình có ít nhất " + minActive + " dân hoạt động mới có phiếu."));
            return;
        }
        if (t.getUUID().toString().equals(data.getString("kien." + id + ".tranh"))) {
            p.sendMessage(c("&cThị trấn của bạn là đối tượng của kiến nghị này, không được bỏ phiếu."));
            return;
        }
        data.set("kien." + id + ".phieu." + t.getUUID(), (yes ? "co:" : "khong:") + p.getName());
        save();
        p.sendMessage(c("&aĐã bỏ phiếu " + (yes ? "ĐỒNG Ý" : "KHÔNG") + " cho " + id + " (phiếu công khai)."));
        check(id);
    }

    private void check(String id) {
        int eligible = votingTowns().size() - (data.isSet("kien." + id + ".tranh") ? 1 : 0);
        if (eligible <= 0) {
            return;
        }
        int needed = (int) Math.floor(data.getDouble("kien." + id + ".nguong") * eligible) + 1;
        if (data.getDouble("kien." + id + ".nguong") > 0.6) {
            needed = (int) Math.ceil(data.getDouble("kien." + id + ".nguong") * eligible);
        }
        int yes = 0;
        int no = 0;
        ConfigurationSection ph = data.getConfigurationSection("kien." + id + ".phieu");
        if (ph != null) {
            for (String k : ph.getKeys(false)) {
                if (ph.getString(k, "").startsWith("co:")) {
                    yes++;
                } else {
                    no++;
                }
            }
        }
        if (yes >= needed) {
            finish(id, true);
        } else if (no > eligible - needed) {
            finish(id, false);
        }
    }

    private void finish(String id, boolean pass) {
        data.set("kien." + id + ".ketqua", pass);
        save();
        String loai = data.getString("kien." + id + ".loai", "");
        String muc = data.getString("kien." + id + ".muc", "");
        Bukkit.broadcast(c("&a[Hội đồng Hoà Bình] Kiến nghị " + id + " (" + describe(id) + "): " + (pass ? "&aTHÔNG QUA" : "&cKHÔNG THÔNG QUA")));
        if (loai.equals("GIAYPHEP") && licenseResult != null) {
            licenseResult.accept(new String[] {muc, Boolean.toString(pass)});
        } else if (pass && loai.equals("DUOI")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ta nation " + peace.nationName() + " kick " + muc);
        } else if (pass && loai.equals("BAIMIEN")) {
            data.set("daidien", null);
            data.set("bau.mo", false);
            save();
            tick(); // mở bầu cử mới ngay, có người tạm giữ
        }
    }

    // ------------------------------------------------------------------ /hoidong

    boolean command(CommandSender s, String[] args) {
        String sub = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (!(s instanceof Player p)) {
            info(s);
            return true;
        }
        switch (sub) {
            case "ungcu" -> {
                if (!data.getBoolean("bau.mo")) {
                    p.sendMessage(c("&cChưa tới kỳ bầu cử."));
                    return true;
                }
                Resident r = TownyAPI.getInstance().getResident(p);
                if (r == null || !peace.isMember(r.getTownOrNull()) || activity.totalHours(p.getUniqueId()) < 5) {
                    p.sendMessage(c("&cChỉ dân hoà bình đã chơi thật ít nhất 5 giờ mới ứng cử được."));
                    return true;
                }
                if (p.getName().equalsIgnoreCase(representative()) && !data.getBoolean("daidien.tam") && data.getInt("daidien.lien") >= maxTerms) {
                    p.sendMessage(c("&cĐã làm đại diện " + maxTerms + " nhiệm kỳ liền, lần này không được ứng cử."));
                    return true;
                }
                List<String> list = new ArrayList<>(data.getStringList("bau.ungvien"));
                if (list.stream().noneMatch(x -> x.equalsIgnoreCase(p.getName()))) {
                    list.add(p.getName());
                    data.set("bau.ungvien", list);
                    save();
                    Bukkit.broadcast(c("&a[Liên minh Hoà Bình] &f" + p.getName() + " &aứng cử đại diện."));
                }
            }
            case "bau" -> {
                if (!data.getBoolean("bau.mo") || args.length < 2) {
                    p.sendMessage(c("&cChưa tới kỳ bầu cử, hoặc thiếu tên: /hoidong bau <tên ứng viên>"));
                    return true;
                }
                Town t = voterTown(p);
                if (t == null) {
                    p.sendMessage(c("&cChỉ thị trưởng thị trấn hoà bình có ít nhất " + minActive + " dân hoạt động mới có phiếu."));
                    return true;
                }
                String cand = data.getStringList("bau.ungvien").stream().filter(x -> x.equalsIgnoreCase(args[1])).findFirst().orElse(null);
                if (cand == null) {
                    p.sendMessage(c("&c" + args[1] + " không ứng cử. Ứng viên: &f" + String.join(", ", data.getStringList("bau.ungvien"))));
                    return true;
                }
                data.set("bau.phieu." + t.getUUID(), cand);
                save();
                p.sendMessage(c("&aThị trấn " + t.getName() + " bầu cho &f" + cand + " &a(công khai, đổi được tới lúc đóng bầu)."));
            }
            case "baimien" -> {
                String rep = representative();
                if (rep == null || voterTown(p) == null) {
                    p.sendMessage(c("&cChỉ thị trưởng có phiếu mới đề nghị bãi nhiệm, và phải đang có đại diện."));
                    return true;
                }
                propose("BAIMIEN", rep, p.getName(), "", 2.0 / 3, null);
            }
            case "deuoi" -> {
                if (args.length < 3 || voterTown(p) == null) {
                    p.sendMessage(c("&c/hoidong deuoi <thị trấn> <lý do> (thị trưởng có phiếu)"));
                    return true;
                }
                Town t = TownyAPI.getInstance().getTown(args[1]);
                if (t == null || !peace.isMember(t) || t.getName().equalsIgnoreCase(peace.capitalName())) {
                    p.sendMessage(c("&cKhông có thị trấn hoà bình tên " + args[1]));
                    return true;
                }
                propose("DUOI", t.getName(), p.getName(), String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)), 0.5,
                        t.getUUID().toString());
            }
            case "phieu" -> {
                if (args.length < 3) {
                    p.sendMessage(c("&c/hoidong phieu <mã> co|khong"));
                    return true;
                }
                vote(p, args[1].toUpperCase(Locale.ROOT), args[2].equalsIgnoreCase("co") || args[2].equalsIgnoreCase("có"));
            }
            default -> info(p);
        }
        return true;
    }

    private void info(CommandSender s) {
        String rep = representative();
        s.sendMessage(c("&a&lHội đồng Hoà Bình &7- " + votingTowns().size() + " thị trấn có phiếu"));
        s.sendMessage(c("&7Đại diện: &f" + (rep == null ? "chưa có" : rep + (data.getBoolean("daidien.tam") ? " (tạm giữ)" : "")
                + " &7tới " + WHEN.format(Instant.ofEpochMilli(data.getLong("daidien.den"))))));
        if (data.getBoolean("bau.mo")) {
            s.sendMessage(c("&eĐang bầu đại diện tới " + WHEN.format(Instant.ofEpochMilli(data.getLong("bau.dong")))
                    + ". Ứng viên: &f" + String.join(", ", data.getStringList("bau.ungvien"))));
        }
        ConfigurationSection ks = data.getConfigurationSection("kien");
        if (ks != null) {
            for (String id : ks.getKeys(false)) {
                if (!ks.isSet(id + ".ketqua")) {
                    s.sendMessage(c("&f" + id + "&7: " + describe(id) + " &7(hạn " + WHEN.format(Instant.ofEpochMilli(ks.getLong(id + ".den"))) + ")"));
                }
            }
        }
        s.sendMessage(c("&7Lệnh: &f/hoidong ungcu · bau <tên> · phieu <mã> co|khong · baimien · deuoi <thị trấn> <lý do>"));
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được hoidong.yml: " + e.getMessage());
        }
    }
}
