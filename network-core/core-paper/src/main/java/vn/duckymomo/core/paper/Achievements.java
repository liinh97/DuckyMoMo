package vn.duckymomo.core.paper;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Sổ thành tích (/thanhtich, Bang Hội Chiến): mục tiêu chơi một mình, tự đếm, đạt là tự nhận quà (Rewards: xu, danhhieu, petexp…).
 * Config achievements.list.<mã>: {name, desc, type, target, rewards[]}. Loại (type):
 *   khampha (số vùng /tpr đã đặt chân tới), cauca (số cá câu được), loaica (số loài cá khác nhau), quai (số quái giết),
 *   petcap (cấp pet cao nhất), batpet (số pet đã bắt), dautruong (số trận Đấu Trường thắng), nauan (số món đã nấu),
 *   kholuong (điểm đã nộp kho lương), giochoi (giờ chơi thật).
 * Module khác báo bằng Achievements.add(p, loại, số). Lưu: thanhtich.yml (dem.<uuid>.<loại>, loaica.<uuid>: [], vung.<uuid>: [], xong.<uuid>: []).
 */
final class Achievements implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    static volatile Achievements INSTANCE;
    static final Map<String, String> TYPE_VI = Map.ofEntries(Map.entry("khampha", "vùng đã tới"), Map.entry("cauca", "cá câu được"), Map.entry("loaica", "loài cá"), Map.entry("quai", "quái đã hạ"), Map.entry("petcap", "cấp pet"), Map.entry("batpet", "pet đã bắt"), Map.entry("dautruong", "trận thắng"), Map.entry("nauan", "món đã nấu"), Map.entry("kholuong", "điểm kho lương"), Map.entry("giochoi", "giờ chơi"), Map.entry("khobau", "kho báu đã mở"), Map.entry("truyna", "quái lệnh săn đã hạ"), Map.entry("thaiap", "lượt thích Thái Ấp"), Map.entry("dungeon", "lượt vượt dungeon"), Map.entry("dungeonan", "dungeon ẩn đã tìm"), Map.entry("conghoang", "Cổng Hoang đã tìm ra"));

    private record Goal(String id, String name, String desc, String type, double target, List<String> rewards) { }
    private record Zone(String id, String requires, int x, int z, int r) { }

    private final DuckyMoMoCore plugin;
    private final Activity activity;
    private final Rewards rewards;
    private final List<Goal> goals = new ArrayList<>();
    private final List<Zone> zones = new ArrayList<>();
    private final File file;
    private final YamlConfiguration data;
    private boolean dirty;

    Achievements(DuckyMoMoCore plugin, Activity activity, Rewards rewards, ConfigurationSection c) {
        this.plugin = plugin;
        this.activity = activity;
        this.rewards = rewards;
        ConfigurationSection l = c.getConfigurationSection("list");
        if (l != null) {
            for (String id : l.getKeys(false)) {
                ConfigurationSection g = l.getConfigurationSection(id);
                if (g != null && TYPE_VI.containsKey(g.getString("type", ""))) {
                    goals.add(new Goal(id, g.getString("name", id), g.getString("desc", ""), g.getString("type"), Math.max(1, g.getDouble("target", 1)),
                            g.getStringList("rewards")));
                }
            }
        }
        for (Map<?, ?> z : plugin.getConfig().getMapList("rtp.zones")) {
            if (z.get("radius") instanceof Number r && r.intValue() > 0) {
                zones.add(new Zone(String.valueOf(z.get("id")), z.get("requires") == null ? "" : String.valueOf(z.get("requires")),
                        ((Number) z.get("center-x")).intValue(), ((Number) z.get("center-z")).intValue(), r.intValue()));
            }
        }
        this.file = new File(plugin.getDataFolder(), "thanhtich.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        Shared.FORGET.add(id -> Shared.forget(data, file, id));   // admin reset "như người mới"
    }

    String describe() {
        return goals.size() + " thành tích";
    }

    void start() {
        INSTANCE = this;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 200L, 100L);           // 5 giây: vùng đã tới
        Bukkit.getScheduler().runTaskTimer(plugin, this::slowTick, 600L, 6000L);      // 5 phút: giờ chơi, pet, Đấu Trường
        Bukkit.getScheduler().runTaskTimer(plugin, this::flush, 1200L, 1200L);
    }

    void stop() {
        INSTANCE = null;
        flush();
    }

    // ---------------------------------------------------------------- đếm

    /** Cộng dồn một loại (module khác gọi). */
    /** Số mốc thành tích đã đạt (-1 nếu module tắt). */
    static int doneCount(UUID u) {
        Achievements a = INSTANCE;
        return a == null ? -1 : a.data.getStringList("xong." + u).size();
    }

    static void add(Player p, String type, double amount) {
        Achievements a = INSTANCE;
        if (a != null && p != null && amount > 0) {
            a.set(p, type, a.value(p.getUniqueId(), type) + amount);
        }
    }

    double value(UUID u, String type) {
        return data.getDouble("dem." + u + "." + type, 0);
    }

    /** Đặt giá trị (lấy max với giá trị cũ cho loại đo mức như cấp pet). */
    private void set(Player p, String type, double v) {
        if (v <= value(p.getUniqueId(), type) && !type.equals("giochoi")) {
            return;
        }
        data.set("dem." + p.getUniqueId() + "." + type, v);
        dirty = true;
        check(p, type);
    }

    private void check(Player p, String type) {
        List<String> done = new ArrayList<>(data.getStringList("xong." + p.getUniqueId()));
        for (Goal g : goals) {
            if (!g.type().equals(type) || done.contains(g.id()) || value(p.getUniqueId(), type) < g.target()) {
                continue;
            }
            done.add(g.id());
            data.set("xong." + p.getUniqueId(), done);
            dirty = true;
            List<String> got = rewards == null ? List.of() : rewards.give(p, g.rewards());
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.1f);
            p.sendMessage(c("&6🏅 Thành tích: &e" + g.name() + " &7— " + g.desc() + (got.isEmpty() ? "" : " &a(" + String.join(", ", got) + "&a)")));
            if (g.rewards().stream().anyMatch(r -> r.startsWith("danhhieu"))) {
                Bukkit.broadcast(c("&6🏅 &f" + p.getName() + " &6đạt thành tích &e" + g.name() + "&6!"));
            }
        }
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() != GameMode.SURVIVAL) {
                continue;
            }
            List<String> seen = data.getStringList("vung." + p.getUniqueId());
            for (Zone z : zones) {
                if (seen.contains(z.id()) || !Shared.unlocked(z.requires())) {
                    continue;
                }
                double dx = p.getLocation().getX() - z.x(), dz = p.getLocation().getZ() - z.z();
                if (dx * dx + dz * dz <= (double) z.r() * z.r()) {
                    List<String> n = new ArrayList<>(seen);
                    n.add(z.id());
                    data.set("vung." + p.getUniqueId(), n);
                    set(p, "khampha", n.size());
                    break;
                }
            }
        }
    }

    private void slowTick() {
        Map<String, Integer> caught = petCounts();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (activity != null) {
                set(p, "giochoi", Math.floor(activity.totalHours(p.getUniqueId())));
            }
            Object pet = MyPetBridge.active(p);
            int lv = pet == null ? 0 : MyPetBridge.level(pet);
            if (lv > 0) {
                set(p, "petcap", lv);
            }
            Integer n = caught.get(p.getUniqueId().toString());
            if (n != null) {
                set(p, "batpet", n);
            }
            int wins = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "dautruong.yml"))
                    .getInt("stats." + p.getUniqueId() + ".thang", 0);
            if (wins > 0) {
                set(p, "dautruong", wins);
            }
        }
    }

    /** Số pet mỗi người đang sở hữu (pets.db, theo UUID Mojang). */
    private Map<String, Integer> petCounts() {
        Map<String, Integer> out = new HashMap<>();
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
             ResultSet r = st.executeQuery("SELECT pl.mojang_uuid, COUNT(*) FROM pets p JOIN players pl ON pl.internal_uuid = p.owner_uuid GROUP BY pl.mojang_uuid")) {
            while (r.next()) {
                out.put(r.getString(1), r.getInt(2));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Thành tích: không đọc được pets.db: " + e.getMessage());
        }
        return out;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) {
        if (e.getEntity() instanceof Monster && e.getEntity().getKiller() != null) {
            add(e.getEntity().getKiller(), "quai", 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(e.getCaught() instanceof Item item)) {
            return;
        }
        Player p = e.getPlayer();
        add(p, "cauca", 1);
        // tên cá EvenMoreFish (đọc tick sau, khi EvenMoreFish đã gắn dấu lên con cá)
        Bukkit.getScheduler().runTask(plugin, () -> {
            var meta = item.getItemStack().getItemMeta();
            String name = meta == null ? null : meta.getPersistentDataContainer().get(PetFish.NAME, PersistentDataType.STRING);
            if (name == null) {
                return;
            }
            List<String> kinds = new ArrayList<>(data.getStringList("loaica." + p.getUniqueId()));
            if (!kinds.contains(name)) {
                kinds.add(name);
                data.set("loaica." + p.getUniqueId(), kinds);
                set(p, "loaica", kinds.size());
            }
        });
    }

    // ---------------------------------------------------------------- lệnh

    boolean command(CommandSender s, String[] args) {
        Player p = s instanceof Player pl ? pl : null;
        if (args.length > 0 && s.hasPermission("duckymomo.admin")) {
            Player t = Bukkit.getPlayerExact(args[0]);
            p = t != null ? t : p;
        }
        if (p == null) {
            s.sendMessage("Dùng trong game.");
            return true;
        }
        List<String> done = data.getStringList("xong." + p.getUniqueId());
        s.sendMessage(c("&6🏅 Sổ thành tích " + (p == s ? "" : "của " + p.getName() + " ") + "&7(" + done.size() + "/" + goals.size() + ")"));
        for (Goal g : goals) {
            double v = Math.min(g.target(), value(p.getUniqueId(), g.type()));
            boolean ok = done.contains(g.id());
            String bar = bar(v / g.target());
            s.sendMessage(c((ok ? "&a✔ " : "&7□ ") + (ok ? "&a" : "&f") + g.name() + " &8— &7" + g.desc() + " " + (ok ? "" : bar + " &f" + fmt(v) + "/" + fmt(g.target()) + " "
                    + TYPE_VI.getOrDefault(g.type(), g.type()))));
        }
        return true;
    }

    private static String bar(double f) {
        int n = (int) Math.round(Math.max(0, Math.min(1, f)) * 10);
        return "&a" + "▮".repeat(n) + "&8" + "▮".repeat(10 - n);
    }

    private void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được thanhtich.yml: " + e.getMessage());
        }
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
}
