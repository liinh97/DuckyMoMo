package vn.duckymomo.core.paper;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Quảng trường Danh vọng ở SẢNH (giữ qua mọi mùa). Đọc S<mùa>.json do Bang Hội Chiến ghi (HallOfFameSnapshot) trong hall-of-fame.dir
 * (thư mục dùng chung data/shared/vinhdanh). Admin đứng đúng chỗ rồi /vinhdanh dat <mùa|moinhat> <hạng mục>: dựng bảng chữ nổi (TextDisplay)
 * + tượng đầu người chơi (ItemDisplay đầu người) quay theo hướng admin đang nhìn. Hạng mục: tieude, bachu, chienthan, phuho, kientrucsu,
 * dautruong, pet. Điểm đặt lưu vinhdanh.yml; tự làm mới mỗi phút khi file đổi. Thực thể không lưu vào world (tạo lại khi bật),
 * chunk chứa điểm đặt được giữ nạp. /vinhdanh tp: tới quảng trường (điểm đặt "tieude" đầu tiên).
 */
final class HallOfFameDisplay {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    static final List<String> CATS = List.of("tieude", "bachu", "chienthan", "phuho", "kientrucsu", "dautruong", "pet");
    private static final String[] MEDAL = {"&6①", "&f②", "&c③"};
    private static final Map<String, String> HE_VI = Map.of("Combat", "Chiến Binh", "Utility", "Hậu Cần", "PvP", "Đấu Sĩ", "Ride", "Thú Cưỡi", "Farm", "Nông Trại");

    private record Anchor(String season, String cat, Location loc) { }

    private final DuckyMoMoCore plugin;
    private final File dir;
    private final File file;
    private final YamlConfiguration data;
    private final List<Anchor> anchors = new ArrayList<>();
    private final List<Entity> spawned = new ArrayList<>();
    private long lastStamp = -1;

    HallOfFameDisplay(DuckyMoMoCore plugin, ConfigurationSection c) {
        this.plugin = plugin;
        this.dir = new File(c.getString("dir", "/shared/vinhdanh"));
        this.file = new File(plugin.getDataFolder(), "vinhdanh.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> m : data.getMapList("diem")) {
            World w = Bukkit.getWorld(String.valueOf(m.get("world")));
            if (w != null) {
                anchors.add(new Anchor(String.valueOf(m.get("mua")), String.valueOf(m.get("muc")),
                        new Location(w, num(m.get("x")), num(m.get("y")), num(m.get("z")), (float) num(m.get("yaw")), 0)));
            }
        }
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshIfChanged, 100L, 1200L);
    }

    void stop() {
        clear();
    }

    // ---------------------------------------------------------------- dữ liệu mùa

    /** mùa -> JSON, theo số mùa tăng dần. */
    private TreeMap<Integer, JsonObject> seasons() {
        TreeMap<Integer, JsonObject> out = new TreeMap<>();
        File[] fs = dir.listFiles((d, n) -> n.matches("S\\d+\\.json"));
        if (fs == null) {
            return out;
        }
        for (File f : fs) {
            try {
                out.put(Integer.parseInt(f.getName().replaceAll("\\D", "")),
                        JsonParser.parseString(Files.readString(f.toPath(), StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (IOException | RuntimeException ignored) {
                // file hỏng: bỏ qua
            }
        }
        return out;
    }

    private long stamp() {
        long s = 0;
        File[] fs = dir.listFiles();
        if (fs != null) {
            for (File f : fs) {
                s += f.lastModified() + f.length();
            }
        }
        return s;
    }

    private void refreshIfChanged() {
        long st = stamp();
        if (st != lastStamp) {
            lastStamp = st;
            render();
        }
    }

    // ---------------------------------------------------------------- dựng

    private void clear() {
        spawned.forEach(Entity::remove);
        spawned.clear();
    }

    void render() {
        clear();
        TreeMap<Integer, JsonObject> all = seasons();
        for (Anchor a : anchors) {
            JsonObject s = a.season().equals("moinhat") ? (all.isEmpty() ? null : all.lastEntry().getValue())
                    : all.get(parseInt(a.season()));
            a.loc().getChunk().setForceLoaded(true);
            build(a, s);
        }
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String str(JsonObject o, String k) {
        return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static JsonArray arr(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray();
    }

    private void build(Anchor a, JsonObject s) {
        String season = s == null ? a.season() : str(s, "mua");
        boolean tam = s != null && "tam".equals(str(s, "trang_thai"));
        String tag = " &8· Mùa " + season + (tam ? " (đang diễn ra, tạm tính)" : "");
        List<String> lines = new ArrayList<>();
        List<String[]> heads = new ArrayList<>();   // {tên, uuid}
        switch (a.cat()) {
            case "tieude" -> {
                lines.add("&6⛫ QUẢNG TRƯỜNG DANH VỌNG");
                lines.add("&eMùa " + season + (tam ? " &7— đang diễn ra" : " &7— đã khép lại"));
                lines.add("&7Danh hiệu, tượng và tên khắc ở đây giữ mãi qua mọi mùa.");
            }
            case "bachu" -> {
                lines.add("&6⚔ CÔNG THÀNH CHIẾN" + tag);
                JsonArray ns = arr(s, "ba_chu");
                if (ns.isEmpty()) {
                    lines.add("&7Chưa có quốc gia nào ghi điểm.");
                }
                for (int i = 0; i < ns.size(); i++) {
                    JsonObject n = ns.get(i).getAsJsonObject();
                    if (i == 0) {
                        lines.add("&eBá Chủ: &f" + str(n, "ten") + " &7(" + str(n, "diem") + " điểm)");
                        lines.add("&7Vua &f" + str(n, "vua"));
                        heads.add(new String[] {str(n, "vua"), str(n, "vua_uuid")});
                        for (JsonElement te : arr(n, "thi_tran")) {
                            JsonObject t = te.getAsJsonObject();
                            lines.add("");
                            lines.add("&6" + str(t, "ten") + " &7— thị trưởng &f" + str(t, "thi_truong") + " &7(" + str(t, "so_dan") + " dân)");
                            List<String> mem = new ArrayList<>();
                            JsonArray ms = arr(t, "thanh_vien");
                            for (int k = 0; k < Math.min(30, ms.size()); k++) {
                                JsonObject m = ms.get(k).getAsJsonObject();
                                List<String> rl = new ArrayList<>();
                                arr(m, "chuc_vu").forEach(x -> rl.add(x.getAsString()));
                                mem.add("&f" + str(m, "ten") + (rl.isEmpty() ? "" : " &8(" + String.join(", ", rl) + ")"));
                            }
                            if (ms.size() > 30) {
                                mem.add("&7và " + (ms.size() - 30) + " người nữa");
                            }
                            lines.addAll(wrap(mem, 4));
                        }
                    } else {
                        lines.add("");
                        lines.add("&eÁ Chủ hạng " + (i + 1) + ": &f" + str(n, "ten") + " &7(" + str(n, "diem") + " điểm, vua " + str(n, "vua") + ")");
                    }
                }
            }
            case "chienthan", "phuho", "dautruong" -> {
                String title = switch (a.cat()) {
                    case "chienthan" -> "&c⚔ CHIẾN THẦN";
                    case "phuho" -> "&e⛃ ĐẠI PHÚ HỘ";
                    default -> "&b⚔ ĐẤU TRƯỜNG";
                };
                String unit = a.cat().equals("phuho") ? " xu" : " điểm";
                lines.add(title + tag);
                JsonArray ps = arr(s, a.cat().equals("chienthan") ? "chien_than" : a.cat().equals("phuho") ? "phu_ho" : "dau_truong");
                if (ps.isEmpty()) {
                    lines.add("&7Chưa có ai.");
                }
                for (int i = 0; i < ps.size(); i++) {
                    JsonObject p = ps.get(i).getAsJsonObject();
                    lines.add(MEDAL[Math.min(i, 2)] + " &f" + str(p, "ten") + " &7— " + str(p, "diem") + unit);
                    heads.add(new String[] {str(p, "ten"), str(p, "uuid")});
                }
            }
            case "kientrucsu" -> {
                lines.add("&b⛫ KIẾN TRÚC SƯ" + tag);
                JsonObject k = s != null && s.has("kien_truc_su") ? s.getAsJsonObject("kien_truc_su") : null;
                if (k == null) {
                    lines.add("&7Chưa bình chọn.");
                } else {
                    lines.add("&f" + str(k, "ten") + " &7(" + str(k, "phieu") + " phiếu) — thị trưởng &f" + str(k, "thi_truong"));
                    List<String> mem = new ArrayList<>();
                    arr(k, "thanh_vien").forEach(x -> mem.add("&f" + str(x.getAsJsonObject(), "ten")));
                    lines.addAll(wrap(mem, 6));
                    heads.add(new String[] {str(k, "thi_truong"), ""});
                }
            }
            case "pet" -> {
                lines.add("&d❖ PET MẠNH NHẤT" + tag);
                JsonArray ps = arr(s, "pet");
                if (ps.isEmpty()) {
                    lines.add("&7Chưa có pet nào.");
                }
                for (int i = 0; i < ps.size(); i++) {
                    JsonObject p = ps.get(i).getAsJsonObject();
                    lines.add(MEDAL[Math.min(i, 2)] + " &f" + str(p, "ten") + " &7(" + str(p, "loai") + ", " + HE_VI.getOrDefault(str(p, "he"), str(p, "he"))
                            + ", cấp " + str(p, "cap") + ") — chủ &f" + str(p, "chu"));
                    heads.add(new String[] {str(p, "chu"), str(p, "uuid")});
                }
            }
            default -> lines.add("&7(hạng mục lạ: " + a.cat() + ")");
        }
        spawnText(a.loc(), lines);
        double top = 0.3 + lines.size() * 0.27;
        for (int i = 0; i < heads.size(); i++) {
            // hạng 1 ở giữa và cao hơn; hạng 2 bên trái, hạng 3 bên phải (theo hướng nhìn của bảng)
            double side = i == 0 ? 0 : (i == 1 ? -0.9 : 0.9) * (heads.size() > 3 ? 1 : 1);
            double up = i == 0 ? 0.45 : 0;
            spawnHead(a.loc(), side, top + up, i == 0 ? 1.1f : 0.8f, heads.get(i)[0], heads.get(i)[1]);
            if (i >= 2) {
                break;
            }
        }
    }

    /** Gộp danh sách thành dòng, mỗi dòng tối đa perLine mục. */
    private static List<String> wrap(List<String> items, int perLine) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i += perLine) {
            out.add(String.join("&7, ", items.subList(i, Math.min(items.size(), i + perLine))));
        }
        return out;
    }

    private void spawnText(Location base, List<String> lines) {
        Location l = base.clone().add(0, 0.3, 0);
        l.setPitch(0);
        TextDisplay td = base.getWorld().spawn(l, TextDisplay.class, t -> {
            t.text(c(String.join("\n", lines)));
            t.setBillboard(Display.Billboard.FIXED);
            t.setLineWidth(420);
            t.setBackgroundColor(Color.fromARGB(150, 10, 12, 20));
            t.setShadowed(true);
            t.setPersistent(false);
            t.setViewRange(1.5f);
        });
        spawned.add(td);
    }

    private void spawnHead(Location base, double side, double up, float scale, String name, String uuid) {
        if (name == null || name.isEmpty()) {
            return;
        }
        double yaw = Math.toRadians(base.getYaw());
        Location l = base.clone().add(-Math.cos(yaw) * side, up, -Math.sin(yaw) * side);
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemDisplay id = base.getWorld().spawn(l, ItemDisplay.class, d -> {
            d.setItemStack(head);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
            d.setPersistent(false);
            d.setViewRange(1.5f);
        });
        spawned.add(id);
        UUID u = null;
        try {
            u = uuid == null || uuid.isEmpty() ? null : UUID.fromString(uuid);
        } catch (IllegalArgumentException ignored) {
            // không có uuid: tra theo tên
        }
        PlayerProfile profile = Bukkit.createProfile(u, name);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                profile.complete(true);   // lấy da (skin) từ Mojang nếu tên có thật; không được thì đầu mặc định
            } catch (RuntimeException ignored) {
                // offline / tên lạ
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!id.isValid()) {
                    return;
                }
                ItemStack h = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta m = (SkullMeta) h.getItemMeta();
                m.setPlayerProfile(profile);
                h.setItemMeta(m);
                id.setItemStack(h);
            });
        });
    }

    // ---------------------------------------------------------------- lệnh

    boolean command(CommandSender s, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        boolean admin = s.hasPermission("duckymomo.vinhdanh.admin");
        if (sub.equals("tp") && s instanceof Player p) {
            Anchor t = anchors.stream().filter(a -> a.cat().equals("tieude")).findFirst().orElse(anchors.isEmpty() ? null : anchors.get(0));
            if (t == null) {
                p.sendMessage(c("&7Quảng trường Danh vọng chưa dựng."));
            } else {
                Location l = t.loc().clone().add(Math.cos(Math.toRadians(t.loc().getYaw() + 90)) * 5, 0, Math.sin(Math.toRadians(t.loc().getYaw() + 90)) * 5);
                l.setYaw(t.loc().getYaw() + 180);
                p.teleport(l);
            }
            return true;
        }
        if (admin && sub.equals("dat") && s instanceof Player p && args.length >= 3) {
            String cat = args[2].toLowerCase(Locale.ROOT);
            if (!CATS.contains(cat)) {
                p.sendMessage(c("&cHạng mục: " + String.join(", ", CATS)));
                return true;
            }
            Location l = p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
            l.setYaw(Math.round(p.getLocation().getYaw() / 90f) * 90f + 180f);   // bảng quay mặt về phía người đặt
            anchors.add(new Anchor(args[1].toLowerCase(Locale.ROOT), cat, l));
            save();
            render();
            p.sendMessage(c("&aĐã đặt " + cat + " (mùa " + args[1] + ") ở đây. Bỏ: đứng gần rồi /vinhdanh xoa"));
            return true;
        }
        if (admin && sub.equals("xoa") && s instanceof Player p) {
            Anchor best = null;
            for (Anchor a : anchors) {
                if (a.loc().getWorld() == p.getWorld() && a.loc().distanceSquared(p.getLocation()) < 36 && (best == null
                        || a.loc().distanceSquared(p.getLocation()) < best.loc().distanceSquared(p.getLocation()))) {
                    best = a;
                }
            }
            if (best == null) {
                p.sendMessage(c("&eKhông có điểm đặt nào trong 6 khối."));
                return true;
            }
            anchors.remove(best);
            save();
            render();
            p.sendMessage(c("&aĐã bỏ " + best.cat() + " (mùa " + best.season() + ")."));
            return true;
        }
        if (admin && sub.equals("lammoi")) {
            render();
            s.sendMessage(c("&aĐã dựng lại " + anchors.size() + " điểm."));
            return true;
        }
        TreeMap<Integer, JsonObject> all = seasons();
        if (s instanceof Player pm && !admin) {   // menu (22/10, trước in chat)
            InfoMenu m = InfoMenu.of("&6⛫ Quảng trường Danh vọng").accent(org.bukkit.Material.YELLOW_STAINED_GLASS_PANE);
            m.top(Tip.of("&6⛫ &fQuảng trường Danh vọng").accent("&6").desc("Tượng và bảng vinh danh người đứng đầu mỗi mùa.").line()
                    .kv("Số mùa", all.isEmpty() ? "chưa có" : String.valueOf(all.size())).apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.BEACON)));
            String[][] cats = {{"ba_chu", "Bá Chủ"}, {"chien_than", "Chiến Thần"}, {"phu_ho", "Đại Phú Hộ"}, {"dau_truong", "Đấu Trường"}, {"pet", "Pet"}};
            for (Map.Entry<Integer, JsonObject> e : all.descendingMap().entrySet()) {
                boolean now = "tam".equals(str(e.getValue(), "trang_thai"));
                Tip t = Tip.of("&eMùa " + e.getKey()).sub(now ? "đang diễn ra" : "đã xong").accent("&e").line();
                for (String[] ct : cats) {
                    JsonArray ns = arr(e.getValue(), ct[0]);
                    t.kv(ct[1], ns.isEmpty() ? "&7chưa có" : str(ns.get(0).getAsJsonObject(), "ten"));
                }
                m.add(t.apply(new org.bukkit.inventory.ItemStack(now ? org.bukkit.Material.CLOCK : org.bukkit.Material.GOLD_BLOCK)));
            }
            if (all.isEmpty()) {
                m.add(Tip.of("&7Chưa có mùa nào").apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.GRAY_DYE)));
            }
            m.bar(Tip.of("&a✈ &fTới quảng trường").accent("&a").line().kv("Lệnh", "/danhvong tp").click("Bấm để tới")
                    .apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.ENDER_PEARL)), InfoMenu.run("danhvong tp"));
            m.open(pm);
            return true;
        }
        s.sendMessage(c("&6⛫ Quảng trường Danh vọng &7— " + (all.isEmpty() ? "chưa có mùa nào" : all.size() + " mùa")));
        for (Map.Entry<Integer, JsonObject> e : all.descendingMap().entrySet()) {
            JsonArray ns = arr(e.getValue(), "ba_chu");
            s.sendMessage(c("&eMùa " + e.getKey() + ("tam".equals(str(e.getValue(), "trang_thai")) ? " &7(đang diễn ra)" : "") + "&7: Bá Chủ &f"
                    + (ns.isEmpty() ? "chưa có" : str(ns.get(0).getAsJsonObject(), "ten"))));
        }
        s.sendMessage(c("&7Tới quảng trường: &f/danhvong tp" + (admin ? " &8· Admin: /vinhdanh dat <mùa|moinhat> <" + String.join("|", CATS)
                + "> · xoa · lammoi" : "")));
        return true;
    }

    List<String> tab(CommandSender s, String[] a) {
        List<String> out = new ArrayList<>();
        boolean admin = s.hasPermission("duckymomo.vinhdanh.admin");
        if (a.length == 1) {
            out.add("tp");
            if (admin) {
                out.addAll(List.of("dat", "xoa", "lammoi"));
            }
        } else if (a.length == 2 && admin && a[0].equalsIgnoreCase("dat")) {
            out.add("moinhat");
            seasons().keySet().forEach(k -> out.add(String.valueOf(k)));
        } else if (a.length == 3 && admin && a[0].equalsIgnoreCase("dat")) {
            out.addAll(CATS);
        }
        String typed = a[a.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(x -> x.startsWith(typed)).toList();
    }

    private void save() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Anchor a : anchors) {
            out.add(Map.of("mua", a.season(), "muc", a.cat(), "world", a.loc().getWorld().getName(), "x", a.loc().getX(), "y", a.loc().getY(),
                    "z", a.loc().getZ(), "yaw", (double) a.loc().getYaw()));
        }
        data.set("diem", out);
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được vinhdanh.yml: " + e.getMessage());
        }
    }

    private static Component c(String s) {
        return Tip.ns(LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false));
    }
}
