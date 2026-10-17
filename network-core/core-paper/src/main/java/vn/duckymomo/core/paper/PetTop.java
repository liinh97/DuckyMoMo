package vn.duckymomo.core.paper;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

/**
 * /bxhpet: bảng xếp hạng pet chiến đấu MyPet theo kinh nghiệm (đọc thẳng plugins/MyPet/pets.db, chỉ đọc, chạy nền).
 *   /bxhpet            top 10 mọi pet
 *   /bxhpet <hệ>       top 10 một hệ: chienbinh, haucan, dausi, thucuoi, nongtrai
 *   /bxhpet tyle       xác suất bắt từng nhóm loài
 * Cấp tính theo công thức mặc định của MyPet (giống panel/taikhoan.py pet_level).
 */
final class PetTop {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    static final Map<String, String[]> HE = Map.of(   // gõ -> (ID cây gốc, tên)
            "chienbinh", new String[] {"Combat", "Chiến Binh"}, "haucan", new String[] {"Utility", "Hậu Cần"},
            "dausi", new String[] {"PvP", "Đấu Sĩ"}, "thucuoi", new String[] {"Ride", "Thú Cưỡi"},
            "nongtrai", new String[] {"Farm", "Nông Trại"});

    private final DuckyMoMoCore plugin;
    private final PetCapture capture;

    PetTop(DuckyMoMoCore plugin, PetCapture capture) {
        this.plugin = plugin;
        this.capture = capture;
    }

    boolean command(CommandSender s, String[] args) {
        String a = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (a.equals("tyle") || a.equals("xacsuat")) {
            if (capture == null) {
                s.sendMessage(c("&7Bắt pet đang không dùng xác suất: đánh yếu máu rồi quăng dây xích là bắt được."));
                return true;
            }
            s.sendMessage(c("&6&l🐾 Xác suất bắt pet &7(mỗi lần quăng dây xích vào con yếu máu)"));
            String season = Seasons.nowName();
            for (Object[] g : capture.table().values()) {
                int bonus = (Integer) g[2];
                s.sendMessage(c("&e" + g[0] + "&7: &f" + Math.min(100, (Integer) g[1] + bonus) + "%"
                        + (bonus != 0 ? " &a(" + (bonus > 0 ? "+" : "") + bonus + "% nhờ " + season + "&a)" : "")));
            }
            s.sendMessage(c("&7Loài khác: &f" + capture.defaultChance() + "%&7. Trượt nhiều lần con vật sẽ hoảng, phải tìm con khác."));
            return true;
        }
        String[] he = HE.get(a);
        if (!a.isEmpty() && he == null) {
            s.sendMessage(c("&eDùng: &f/bxhpet &7[chienbinh|haucan|dausi|thucuoi|nongtrai|tyle]"));
            return true;
        }
        File db = new File(plugin.getDataFolder().getParentFile(), "MyPet/pets.db");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String> lines = new ArrayList<>();
            String sql = "SELECT p.name, p.type, p.exp, p.skilltree, pl.name FROM pets p LEFT JOIN players pl ON pl.internal_uuid = p.owner_uuid"
                    + (he != null ? " WHERE p.skilltree = ? OR p.skilltree LIKE ? ESCAPE '\\'" : "") + " ORDER BY p.exp DESC LIMIT 10";
            try {
                Class.forName("org.sqlite.JDBC");   // Paper có sẵn sqlite-jdbc
            } catch (ClassNotFoundException ignored) {
                // để DriverManager tự tìm
            }
            try (Connection con = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath() + "?open_mode=1");
                 PreparedStatement st = con.prepareStatement(sql)) {
                if (he != null) {
                    st.setString(1, he[0]);
                    st.setString(2, he[0] + "\\_%");
                }
                try (ResultSet r = st.executeQuery()) {
                    int i = 1;
                    while (r.next()) {
                        String name = clean(r.getString(1));
                        lines.add("&6" + i++ + ". &f" + (name.isEmpty() ? r.getString(2) : name) + " &7(" + r.getString(2) + ") &ecấp "
                                + level(r.getDouble(3)) + " &7· chủ &f" + (r.getString(5) == null ? "?" : r.getString(5))
                                + (he == null ? " &8" + treeName(r.getString(4)) : ""));
                    }
                }
            } catch (SQLException e) {
                lines.add("&cChưa đọc được dữ liệu pet: " + e.getMessage());
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                s.sendMessage(c("&6&l🐾 Bảng xếp hạng pet" + (he != null ? " · " + he[1] : "")));
                if (lines.isEmpty()) {
                    s.sendMessage(c("&7Chưa có pet nào."));
                }
                lines.forEach(l -> s.sendMessage(c(l)));
                s.sendMessage(c("&7Theo hệ: &f/bxhpet chienbinh|haucan|dausi|thucuoi|nongtrai &7· xác suất bắt: &f/bxhpet tyle"));
            });
        });
        return true;
    }

    private static String treeName(String id) {
        if (id == null) {
            return "chưa chọn hệ";
        }
        String base = id.split("_", 2)[0];
        for (String[] v : HE.values()) {
            if (v[0].equals(base)) {
                return v[1];
            }
        }
        return id;
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("<[^>]+>|[&§][0-9a-fk-or]", "");
    }

    /** Cấp MyPet từ kinh nghiệm: 17/cấp tới 17, rồi +3, từ 32 +7 (công thức mặc định). */
    static int level(double exp) {
        int lv = 1;
        while (lv < 1000 && need(lv + 1) <= exp) {
            lv++;
        }
        return lv;
    }

    private static double need(int lv) {
        if (lv <= 1) {
            return 0;
        }
        if (lv <= 17) {
            return (lv - 1) * 17.0;
        }
        if (lv <= 31) {
            int n = lv - 17;
            return 272 + n * (3.0 * lv - 14) / 2;
        }
        int n = lv - 32;
        return 887 + n * (7.0 * lv - 93) / 2;
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
}
