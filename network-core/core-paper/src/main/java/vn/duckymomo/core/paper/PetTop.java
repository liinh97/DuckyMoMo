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
import org.bukkit.entity.Player;

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
        if ((a.equals("tyle") || a.equals("xacsuat")) && s instanceof Player pm0) {
            a = "";   // người chơi: menu, ô Xác suất bắt nằm trong đó
        }
        if (a.equals("tyle") || a.equals("xacsuat")) {
            if (capture == null) {
                s.sendMessage(c("&7Bắt pet đang không dùng xác suất: đánh yếu máu rồi quăng dây xích là bắt được."));
                return true;
            }
            s.sendMessage(c("&6❖ Xác suất bắt pet &7(mỗi lần quăng dây xích vào con yếu máu)"));
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
            List<String[]> rows = new ArrayList<>();   // tên, loài, cấp, chủ, hệ (cho menu)
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
                        rows.add(new String[] {name.isEmpty() ? r.getString(2) : name, r.getString(2), String.valueOf(level(r.getDouble(3))),
                                r.getString(5) == null ? "?" : r.getString(5), treeName(r.getString(4))});
                        lines.add("&6" + i++ + ". &f" + (name.isEmpty() ? r.getString(2) : name) + " &7(" + r.getString(2) + ") &ecấp "
                                + level(r.getDouble(3)) + " &7· chủ &f" + (r.getString(5) == null ? "?" : r.getString(5))
                                + (he == null ? " &8" + treeName(r.getString(4)) : ""));
                    }
                }
            } catch (SQLException e) {
                lines.add("&cChưa đọc được dữ liệu pet: " + e.getMessage());
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (s instanceof Player pm) {
                    if (pm.isOnline()) {
                        menu(pm, he, rows);
                    }
                    return;
                }
                s.sendMessage(c("&6❖ Bảng xếp hạng pet" + (he != null ? " · " + he[1] : "")));
                if (lines.isEmpty()) {
                    s.sendMessage(c("&7Chưa có pet nào."));
                }
                lines.forEach(l -> s.sendMessage(c(l)));
                s.sendMessage(c("&7Theo hệ: &f/bxhpet chienbinh|haucan|dausi|thucuoi|nongtrai &7· xác suất bắt: &f/bxhpet tyle"));
            });
        });
        return true;
    }

    /** /bxhpet cho người chơi: menu (22/10, trước in chat). Hàng 1 = top 10, hàng 2 = lọc theo hệ + xác suất bắt. */
    private void menu(Player p, String[] he, List<String[]> rows) {
        InfoMenu m = InfoMenu.of("&6❖ Bảng xếp hạng pet" + (he != null ? " &8· &f" + he[1] : "")).accent(org.bukkit.Material.ORANGE_STAINED_GLASS_PANE);
        m.top(Tip.of("&6❖ &fBảng xếp hạng pet").accent("&6").desc(he != null ? "Hệ " + he[1] : "Mọi hệ", "Xếp theo kinh nghiệm của pet.")
                .apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.BONE)));
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            org.bukkit.inventory.ItemStack it = Tip.of((i < 3 ? "&6" : "&f") + (i + 1) + ". " + r[0]).accent("&6").kv("Loài", r[1]).kv("Cấp", "&e" + r[2])
                    .kv("Chủ", r[3]).kv("Hệ", r[4]).apply(new org.bukkit.inventory.ItemStack(i == 0 ? org.bukkit.Material.GOLD_INGOT
                            : i == 1 ? org.bukkit.Material.IRON_INGOT : i == 2 ? org.bukkit.Material.COPPER_INGOT : org.bukkit.Material.NAME_TAG));
            if (i < 3) {
                it.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            }
            m.add(it);
        }
        if (rows.isEmpty()) {
            m.add(Tip.of("&7Chưa có pet nào").apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.GRAY_DYE)));
        }
        m.row();
        m.add(Tip.of((he == null ? "&a✔ " : "&f") + "Tất cả").line().click("Bấm để xem").apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.BOOK)),
                InfoMenu.run("bxhpet"));
        String[][] order = {{"chienbinh", "IRON_SWORD"}, {"haucan", "CHEST"}, {"dausi", "TARGET"}, {"thucuoi", "SADDLE"}, {"nongtrai", "WHEAT"}};
        for (String[] o : order) {
            String[] h = HE.get(o[0]);
            m.add(Tip.of((he != null && he[0].equals(h[0]) ? "&a✔ " : "&f") + h[1]).line().kv("Lệnh", "/bxhpet " + o[0]).click("Bấm để xem")
                    .apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.valueOf(o[1]))), InfoMenu.run("bxhpet " + o[0]));
        }
        Tip ty = Tip.of("&e% &fXác suất bắt").accent("&e");
        if (capture == null) {
            ty.desc("Đánh yếu máu rồi quăng dây xích là bắt được");
        } else {
            ty.desc("Mỗi lần quăng dây xích vào con yếu máu").line();
            String season = Seasons.nowName();
            for (Object[] g : capture.table().values()) {
                int bonus = (Integer) g[2];
                ty.raw("&e" + g[0] + "&7: &f" + Math.min(100, (Integer) g[1] + bonus) + "%" + (bonus != 0 ? " &a(" + (bonus > 0 ? "+" : "") + bonus + "% nhờ " + season + "&a)" : ""));
            }
            ty.kv("Loài khác", capture.defaultChance() + "%").note("Trượt nhiều lần con vật sẽ hoảng, phải tìm con khác.");
        }
        m.add(ty.apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.LEAD)));
        m.open(p);
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

    /** Cấp MyPet từ kinh nghiệm: CalculationMode Default (DefaultExperienceCalculator): lên cấp i+1 cần 7 + ⌊(i−1)·3,5⌋ (29/10: trước dùng
     *  công thức của exp.js, file chỉ dùng khi CalculationMode JS, nên cấp hiện cao hơn thật, vd 348 kinh nghiệm: 20 thay vì 13). */
    static int level(double exp) {
        int lv = 1;
        while (lv < 1000 && need(lv + 1) <= exp) {
            lv++;
        }
        return lv;
    }

    private static double need(int lv) {
        double t = 0;
        for (int i = 1; i < lv; i++) {
            t += 7 + Math.floor((i - 1) * 3.5);
        }
        return t;
    }

    private static Component c(String s) {
        return Tip.ns(LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false));
    }
}
