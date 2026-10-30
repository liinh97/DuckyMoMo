package vn.duckymomo.core.paper;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Vinh danh mùa (Bang Hội Chiến): chụp người/nước thắng ra file JSON dùng chung (hall-of-fame.dir, mặc định /shared/vinhdanh) để
 * sảnh dựng Quảng trường Danh vọng (HallOfFameDisplay) và web hiện Quảng trường Danh vọng. Mỗi mùa một file S<mùa>.json:
 *  - tạm tính: tự ghi mỗi 30 phút trong mùa ("trang_thai": "tam");
 *  - chốt: /vinhdanh chot (admin, sau /bxh traothuong) -> "trang_thai": "chot", không ghi đè nữa.
 * Nội dung: Công thành chiến (Bá Chủ: nước, vua, từng thị trấn với thị trưởng và thành viên kèm chức vụ; Á Chủ hạng 2–3), Chiến Thần, Đại Phú Hộ,
 * Kiến Trúc Sư (thị trấn + thành viên), Đấu Trường (điểm), Pet mạnh nhất.
 */
final class HallOfFameSnapshot {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    static final Map<String, String> ROLE_VI = Map.ofEntries(
            Map.entry("assistant", "Phó thị trưởng"), Map.entry("helper", "Trợ lý"), Map.entry("sheriff", "Cảnh sát trưởng"),
            Map.entry("guard", "Vệ binh"), Map.entry("private", "Binh nhì"), Map.entry("sergeant", "Trung sĩ"), Map.entry("lieutenant", "Trung uý"),
            Map.entry("captain", "Đại uý"), Map.entry("major", "Thiếu tá"), Map.entry("colonel", "Đại tá"), Map.entry("general", "Tướng"),
            Map.entry("co-king", "Phó vương"), Map.entry("diplomat", "Ngoại giao"), Map.entry("recruiter", "Tuyển quân"));

    private final DuckyMoMoCore plugin;
    private final Leaderboard board;
    private final File dir;
    private final String season;

    HallOfFameSnapshot(DuckyMoMoCore plugin, Leaderboard board, ConfigurationSection c, String season) {
        this.plugin = plugin;
        this.board = board;
        this.dir = new File(c.getString("dir", "/shared/vinhdanh"));
        this.season = season;
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> write(false, null), 1200L, 36_000L);   // 30 phút
    }

    private File file() {
        return new File(dir, "S" + season + ".json");
    }

    private boolean locked() {
        try {
            File f = file();
            return f.exists() && Files.readString(f.toPath(), StandardCharsets.UTF_8).contains("\"trang_thai\": \"chot\"");
        } catch (IOException e) {
            return false;
        }
    }

    boolean command(CommandSender s, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("chot") || sub.equals("tam")) {
            if (!s.hasPermission("duckymomo.vinhdanh.admin")) {
                s.sendMessage(c("&cChỉ admin chốt vinh danh."));
                return true;
            }
            boolean force = args.length > 1 && args[1].equalsIgnoreCase("ghide");
            if (locked() && !force) {
                s.sendMessage(c("&eMùa " + season + " đã chốt. Ghi đè: /vinhdanh " + sub + " ghide"));
                return true;
            }
            write(sub.equals("chot"), s);
            return true;
        }
        if (s instanceof org.bukkit.entity.Player pm && !s.hasPermission("duckymomo.vinhdanh.admin")) {   // menu (22/10, trước in chat)
            InfoMenu m = InfoMenu.of("&6⛫ Vinh danh mùa " + season).accent(org.bukkit.Material.YELLOW_STAINED_GLASS_PANE);
            m.top(Tip.of("&6⛫ &fVinh danh mùa " + season).accent("&6").desc(locked() ? "Đã chốt" : "Tạm tính, cập nhật 30 phút/lần").line()
                    .note("Xem tượng và bảng vàng ở Quảng trường Danh vọng tại sảnh, hoặc trang web mục Quảng trường Danh vọng.")
                    .apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.BEACON)));
            if (board != null) {
                var top = board.topNations(3);
                for (int i = 0; i < top.size(); i++) {
                    org.bukkit.inventory.ItemStack it = Tip.of(i == 0 ? "&6♛ &fBá Chủ" : "&e♛ &fÁ Chủ").accent("&6").kv("Quốc gia", top.get(i).getKey())
                            .kv("Điểm", String.valueOf(top.get(i).getValue())).apply(new org.bukkit.inventory.ItemStack(i == 0 ? org.bukkit.Material.NETHER_STAR
                                    : org.bukkit.Material.GOLD_INGOT));
                    m.add(it);
                }
                var war = board.topWarriors(1);
                if (!war.isEmpty()) {
                    m.add(Tip.of("&c⚔ &fChiến Thần").accent("&c").kv("Người", war.get(0).getKey()).kv("Điểm", String.valueOf(war.get(0).getValue()))
                            .apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_SWORD)));
                }
                if (top.isEmpty() && war.isEmpty()) {
                    m.add(Tip.of("&7Chưa có ai trên bảng").apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.GRAY_DYE)));
                }
            }
            m.open(pm);
            return true;
        }
        s.sendMessage(c("&6⛫ Vinh danh mùa " + season + (locked() ? " &a(đã chốt)" : " &7(tạm tính, cập nhật 30 phút/lần)")));
        if (board != null) {
            var top = board.topNations(3);
            for (int i = 0; i < top.size(); i++) {
                s.sendMessage(c((i == 0 ? "&6Bá Chủ: &f" : "&eÁ Chủ: &f") + top.get(i).getKey() + " &7(" + top.get(i).getValue() + " điểm)"));
            }
            var war = board.topWarriors(1);
            if (!war.isEmpty()) {
                s.sendMessage(c("&cChiến Thần: &f" + war.get(0).getKey() + " &7(" + war.get(0).getValue() + " điểm)"));
            }
        }
        s.sendMessage(c("&7Xem tượng và bảng vàng ở &fQuảng trường Danh vọng &7tại sảnh, hoặc trang web mục Quảng trường Danh vọng."
                + (s.hasPermission("duckymomo.vinhdanh.admin") ? " &7Admin: /vinhdanh chot | tam" : "")));
        return true;
    }

    private void write(boolean lock, CommandSender who) {
        if (locked() && !lock && who == null) {
            return;   // đã chốt: không tự ghi đè
        }
        JsonObject root = new JsonObject();
        root.addProperty("mua", season);
        root.addProperty("trang_thai", lock ? "chot" : "tam");
        root.addProperty("thoi_gian", System.currentTimeMillis() / 1000);
        if (board != null) {
            JsonArray nations = new JsonArray();
            var top = board.topNations(3);
            for (int i = 0; i < top.size(); i++) {
                nations.add(nation(top.get(i).getKey(), top.get(i).getValue(), i + 1, i == 0));
            }
            root.add("ba_chu", nations);
            JsonArray war = new JsonArray();
            board.topWarriors(3).forEach(e -> war.add(person(e.getKey(), e.getValue())));
            root.add("chien_than", war);
            JsonArray rich = new JsonArray();
            board.topRich(3).forEach(e -> rich.add(person(e.getKey(), Math.round(e.getValue()))));
            root.add("phu_ho", rich);
            Map<String, Integer> votes = board.architectVotes();
            String best = votes.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
            if (best != null) {
                Town t = TownyAPI.getInstance().getTown(best);
                JsonObject k = t != null ? town(t, true) : new JsonObject();
                k.addProperty("ten", t != null ? t.getName() : best);
                k.addProperty("phieu", votes.get(best));
                root.add("kien_truc_su", k);
            }
        }
        root.add("dau_truong", arenaTop());
        root.add("pet", petTop());
        try {
            Files.createDirectories(dir.toPath());
            Files.writeString(file().toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
            if (who != null) {
                who.sendMessage(c(lock ? "&a⛫ Đã chốt vinh danh mùa " + season + ". Sảnh và web cập nhật trong 1 phút." : "&aĐã ghi bản tạm tính mùa " + season + "."));
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Không ghi được vinh danh (" + dir + "): " + e.getMessage());
            if (who != null) {
                who.sendMessage(c("&cKhông ghi được file vinh danh: " + e.getMessage()));
            }
        }
    }

    private static JsonObject person(String name, long points) {
        JsonObject o = new JsonObject();
        o.addProperty("ten", name);
        OfflinePlayer p = Bukkit.getOfflinePlayerIfCached(name);
        if (p != null) {
            o.addProperty("uuid", p.getUniqueId().toString());
        }
        o.addProperty("diem", points);
        return o;
    }

    private static List<String> roles(Resident r, Town t, Nation n) {
        List<String> out = new ArrayList<>();
        if (n != null && n.getKing() == r) {
            out.add("Vua");
        }
        if (t.getMayor() == r) {
            out.add("Thị trưởng");
        }
        for (String rank : r.getNationRanks()) {
            out.add(ROLE_VI.getOrDefault(rank.toLowerCase(Locale.ROOT), rank));
        }
        for (String rank : r.getTownRanks()) {
            out.add(ROLE_VI.getOrDefault(rank.toLowerCase(Locale.ROOT), rank));
        }
        return out;
    }

    /** Thị trấn: tên, thị trưởng, thành viên (chức vụ trước, rồi theo tên). full = kèm danh sách thành viên. */
    private static JsonObject town(Town t, boolean full) {
        JsonObject o = new JsonObject();
        o.addProperty("ten", t.getName());
        o.addProperty("thi_truong", t.getMayor() == null ? "" : t.getMayor().getName());
        o.addProperty("so_dan", t.getResidents().size());
        if (full) {
            Nation n = t.getNationOrNull();
            List<Resident> rs = new ArrayList<>(t.getResidents());
            rs.sort(Comparator.comparingInt((Resident r) -> -roles(r, t, n).size()).thenComparing(Resident::getName, String.CASE_INSENSITIVE_ORDER));
            JsonArray arr = new JsonArray();
            for (Resident r : rs) {
                JsonObject m = new JsonObject();
                m.addProperty("ten", r.getName());
                UUID u = r.getUUID();
                if (u != null) {
                    m.addProperty("uuid", u.toString());
                }
                JsonArray rl = new JsonArray();
                roles(r, t, n).forEach(rl::add);
                m.add("chuc_vu", rl);
                arr.add(m);
            }
            o.add("thanh_vien", arr);
        }
        return o;
    }

    private static JsonObject nation(String name, int points, int rank, boolean full) {
        JsonObject o = new JsonObject();
        o.addProperty("hang", rank);
        o.addProperty("ten", name);
        o.addProperty("diem", points);
        Nation n = TownyAPI.getInstance().getNation(name);
        if (n != null) {
            Resident k = n.getKing();
            if (k != null) {
                o.addProperty("vua", k.getName());
                if (k.getUUID() != null) {
                    o.addProperty("vua_uuid", k.getUUID().toString());
                }
            }
            JsonArray towns = new JsonArray();
            List<Town> ts = new ArrayList<>(n.getTowns());
            ts.sort(Comparator.comparingInt((Town t) -> -t.getResidents().size()));
            ts.forEach(t -> towns.add(town(t, full)));
            o.add("thi_tran", towns);
        }
        return o;
    }

    private JsonArray arenaTop() {
        JsonArray out = new JsonArray();
        ConfigurationSection st = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "dautruong.yml")).getConfigurationSection("stats");
        if (st == null) {
            return out;
        }
        List<String> ids = new ArrayList<>(st.getKeys(false));
        ids.removeIf(k -> st.getInt(k + ".thang", 0) + st.getInt(k + ".thua", 0) == 0);
        ids.sort(Comparator.comparingInt((String k) -> st.getInt(k + ".elo", 1000)).reversed());
        for (String k : ids.subList(0, Math.min(3, ids.size()))) {
            JsonObject o = new JsonObject();
            o.addProperty("ten", st.getString(k + ".ten", "?"));
            o.addProperty("uuid", k);
            o.addProperty("diem", st.getInt(k + ".elo", 1000));
            o.addProperty("thang", st.getInt(k + ".thang", 0));
            out.add(o);
        }
        return out;
    }

    private JsonArray petTop() {
        JsonArray out = new JsonArray();
        File db = new File(plugin.getDataFolder().getParentFile(), "MyPet/pets.db");
        if (!db.exists()) {
            return out;
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
            // để DriverManager tự tìm
        }
        try (Connection con = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath() + "?open_mode=1");
             Statement st = con.createStatement();
             ResultSet r = st.executeQuery("SELECT p.name, p.type, p.exp, p.skilltree, pl.name, pl.mojang_uuid FROM pets p LEFT JOIN players pl "
                     + "ON pl.internal_uuid = p.owner_uuid ORDER BY p.exp DESC LIMIT 3")) {
            while (r.next()) {
                JsonObject o = new JsonObject();
                o.addProperty("ten", String.valueOf(r.getString(1)).replaceAll("<[^>]+>|[&§][0-9a-fk-or]", ""));
                o.addProperty("loai", r.getString(2));
                o.addProperty("cap", PetTop.level(r.getDouble(3)));
                o.addProperty("he", String.valueOf(r.getString(4)).split("_")[0]);
                o.addProperty("chu", r.getString(5));
                o.addProperty("uuid", r.getString(6));
                out.add(o);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Vinh danh: không đọc được pets.db: " + e.getMessage());
        }
        return out;
    }

    private static Component c(String s) {
        return Tip.ns(LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false));
    }
}
