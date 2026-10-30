package vn.duckymomo.core.paper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.WorldCoord;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Đặc sản vùng (/dacsan). Vùng = các vùng trong rtp.zones (tâm + bán kính). Nước (hoặc thị trấn chưa vào nước) có NHIỀU thị trấn
 * nhất trong vùng (đếm theo nhà chính / spawn của thị trấn; KHÔNG tính Liên minh Hoà Bình và thị trấn trung lập) là chủ vùng; dân của chủ vùng gặt đặc sản của vùng ở BẤT KỲ đâu thì
 * được mùa thêm % (cộng vào được mùa của /mua). Hoà nhau = vùng chưa có chủ. Chiếm thành trong vùng = giành đặc sản.
 * Config region-specialty: refresh-minutes, regions.<mã vùng>: {label, crops {CÂY: %}}. Ghi dacsan.json cho web mỗi lần tính lại.
 */
final class RegionSpecialty {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private record Region(String id, String name, String label, double x, double z, double r, Map<Material, Double> crops, String requires) {
        boolean open() {
            return Shared.unlocked(requires);   // vùng mở bằng khai hoang: chưa mở thì chưa có đặc sản
        }
    }

    private final DuckyMoMoCore plugin;
    private final List<Region> regions = new ArrayList<>();
    private final long refreshTicks;
    private final Map<String, String> owner = new HashMap<>();        // mã vùng -> khoá chủ ("n:<nước>" / "t:<thị trấn>")
    private final Map<String, String> ownerName = new HashMap<>();
    private final Map<String, Integer> ownerTowns = new HashMap<>();
    private final String peaceNation;   // Liên minh Hoà Bình + thị trấn trung lập không tranh vùng (không bị vây nên không ai giành lại được)

    RegionSpecialty(DuckyMoMoCore plugin, ConfigurationSection c, List<Map<?, ?>> zones) {
        this.plugin = plugin;
        this.refreshTicks = Math.max(1, c.getLong("refresh-minutes", 10)) * 1200L;
        this.peaceNation = plugin.getConfig().getBoolean("peace-alliance.enabled") ? plugin.getConfig().getString("peace-alliance.nation", "") : "";
        ConfigurationSection rs = c.getConfigurationSection("regions");
        for (Map<?, ?> z : zones) {
            String id = String.valueOf(z.get("id"));
            ConfigurationSection one = rs == null ? null : rs.getConfigurationSection(id);
            double r = num(z.get("radius"));
            if (one == null || r <= 0) {
                continue;
            }
            Map<Material, Double> crops = new LinkedHashMap<>();
            ConfigurationSection cr = one.getConfigurationSection("crops");
            if (cr != null) {
                for (String k : cr.getKeys(false)) {
                    Material m = Material.matchMaterial(k);
                    if (m != null) {
                        crops.put(m, cr.getDouble(k));
                    }
                }
            }
            regions.add(new Region(id, String.valueOf(z.get("name")), one.getString("label", ""), num(z.get("center-x")), num(z.get("center-z")), r, crops,
                    z.get("requires") == null ? "" : String.valueOf(z.get("requires"))));
        }
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    boolean ready() {
        return !regions.isEmpty();
    }

    String describe() {
        return regions.size() + " vùng có đặc sản";
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 200L, refreshTicks);
        Seasons.extraHarvest = this::bonus;
    }

    void stop() {
        Seasons.extraHarvest = null;
    }

    private Region regionAt(double x, double z) {
        for (Region r : regions) {
            if (!r.open()) {
                continue;
            }
            double dx = x - r.x(), dz = z - r.z();
            if (dx * dx + dz * dz <= r.r() * r.r()) {
                return r;
            }
        }
        return null;
    }

    private static String key(Town t) {
        Nation n = t.getNationOrNull();
        return n != null ? "n:" + n.getName() : "t:" + t.getName();
    }

    /** Đếm thị trấn từng phe trong mỗi vùng, chọn chủ vùng. */
    private void refresh() {
        Map<String, Map<String, Integer>> count = new HashMap<>();
        for (Town t : TownyAPI.getInstance().getTowns()) {
            Nation tn = t.getNationOrNull();
            if (PeaceStatus.peaceful(t) || (tn != null && tn.getName().equalsIgnoreCase(peaceNation))) {
                continue;
            }
            double x;
            double z;
            Location sp = t.getSpawnOrNull();
            if (sp != null) {
                x = sp.getX();
                z = sp.getZ();
            } else if (t.getHomeBlockOrNull() != null) {
                WorldCoord wc = t.getHomeBlockOrNull().getWorldCoord();
                x = wc.getX() * 16 + 8;
                z = wc.getZ() * 16 + 8;
            } else {
                continue;
            }
            Region r = regionAt(x, z);
            if (r != null) {
                count.computeIfAbsent(r.id(), k -> new HashMap<>()).merge(key(t), 1, Integer::sum);
            }
        }
        owner.clear();
        ownerName.clear();
        ownerTowns.clear();
        for (Region r : regions) {
            Map<String, Integer> m = count.getOrDefault(r.id(), Map.of());
            String best = null;
            int bestN = 0;
            boolean tie = false;
            for (Map.Entry<String, Integer> e : m.entrySet()) {
                if (e.getValue() > bestN) {
                    best = e.getKey();
                    bestN = e.getValue();
                    tie = false;
                } else if (e.getValue() == bestN) {
                    tie = true;
                }
            }
            if (best != null && !tie) {
                owner.put(r.id(), best);
                ownerName.put(r.id(), (best.startsWith("n:") ? "nước " : "thị trấn ") + best.substring(2));
                ownerTowns.put(r.id(), bestN);
            }
        }
        writeJson();
    }

    /** % được mùa thêm khi người chơi gặt cây (đặc sản các vùng mà phe họ làm chủ). */
    Double bonus(Player p, Material crop) {
        Resident res = TownyAPI.getInstance().getResident(p);
        Town t = res == null ? null : res.getTownOrNull();
        if (t == null) {
            return 0.0;
        }
        String k = key(t);
        List<Region> mine = new ArrayList<>();
        for (Region r : regions) {
            if (k.equals(owner.get(r.id()))) {
                mine.add(r);
            }
        }
        // Bá chủ (WarBalance) chỉ hưởng đặc sản ở vài vùng mạnh nhất của mình
        WarBalance wb = WarBalance.INSTANCE;
        if (wb != null && WarBalance.hegemon(t.getNationOrNull()) && mine.size() > wb.hegemonMaxRegions) {
            mine.sort((a, b) -> Integer.compare(ownerTowns.getOrDefault(b.id(), 0), ownerTowns.getOrDefault(a.id(), 0)));
            mine = mine.subList(0, wb.hegemonMaxRegions);
        }
        double sum = 0;
        for (Region r : mine) {
            sum += r.crops().getOrDefault(crop, 0.0);
        }
        return sum;
    }

    boolean command(CommandSender s, String[] args) {
        String mine = null;
        if (s instanceof Player p) {
            Resident res = TownyAPI.getInstance().getResident(p);
            Town t = res == null ? null : res.getTownOrNull();
            mine = t == null ? null : key(t);
        }
        s.sendMessage(c("&6⚘ Đặc sản vùng &7(phe có nhiều thị trấn nhất trong vùng được mùa thêm khi gặt đặc sản, gặt ở đâu cũng được)"));
        for (Region r : regions) {
            if (!r.open()) {
                s.sendMessage(c(r.name() + " &8(chưa khai hoang: góp quỹ /khaihoang để mở)"));
                continue;
            }
            String o = owner.get(r.id());
            List<String> crops = new ArrayList<>();
            r.crops().forEach((m, v) -> crops.add(Seasons.cropLabel(m) + " +" + Math.round(v) + "%"));
            s.sendMessage(c(r.name() + " &7(" + r.label() + "): &f" + String.join(", ", crops) + " &7· chủ: "
                    + (o == null ? "&7chưa có" : (o.equals(mine) ? "&a" : "&f") + ownerName.get(r.id()) + " &7(" + ownerTowns.get(r.id()) + " thị trấn)")));
        }
        s.sendMessage(c("&7Xây thêm thị trấn hoặc chiếm thành trong vùng để giành đặc sản. Tính lại mỗi " + refreshTicks / 1200 + " phút."));
        return true;
    }

    private void writeJson() {
        JsonObject root = new JsonObject();
        root.addProperty("updated", System.currentTimeMillis() / 1000);
        JsonArray arr = new JsonArray();
        for (Region r : regions) {
            JsonObject o = new JsonObject();
            o.addProperty("mo", r.open());
            o.addProperty("id", r.id());
            o.addProperty("ten", r.name().replaceAll("&[0-9a-fk-or]", ""));
            o.addProperty("dac_san", r.label());
            JsonArray cr = new JsonArray();
            r.crops().forEach((m, v) -> cr.add(Seasons.cropLabel(m) + " +" + Math.round(v) + "%"));
            o.add("cay", cr);
            o.addProperty("chu", ownerName.getOrDefault(r.id(), ""));
            o.addProperty("so_thi_tran", ownerTowns.getOrDefault(r.id(), 0));
            arr.add(o);
        }
        root.add("vung", arr);
        try {
            Files.writeString(new File(plugin.getDataFolder(), "dacsan.json").toPath(), root.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Không ghi được dacsan.json: " + e.getMessage());
        }
    }

    private static Component c(String s) {
        return Tip.ns(LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false));
    }
}
