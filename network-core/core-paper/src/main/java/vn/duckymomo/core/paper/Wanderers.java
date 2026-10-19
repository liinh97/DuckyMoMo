package vn.duckymomo.core.paper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Lữ khách (/lukhach, Bang Hội Chiến). Config wander-npcs:
 *  - Mỗi lữ khách gắn với một nơi phía sau Cổng Hoang (npcs.<mã>.dungeon = mã dungeon trong wild-gates.dungeons) và kể chuyện về nơi đó.
 *  - Là NPC Citizens (tên = npcs.<mã>.name), đứng ở đất hoang ngẫu nhiên (cùng luật chọn chỗ của Cổng Hoang), mỗi move-hours giờ dời đi
 *    chỗ khác và báo tin đồn cả server (vùng + hướng, không toạ độ). Lời thoại + nhiệm vụ: BetonQuest, gói cotruyen/lukhach/.
 *  - Cho BetonQuest biết cổng: placeholder %dmmlk_<mã>_cong|tim|tindon|vung|huong|chu|timboi|npcvung|npchuong% (WanderersPapi).
 *    Chưa mở mùa thì chưa có cổng (cong = khong): lữ khách chỉ kể truyền thuyết.
 *  - Ai đi qua một Cổng Hoang: gắn tag BetonQuest cotruyen>lk_<mã>_qua (lệnh console bq tag) để về kể cho lữ khách nhận thưởng.
 * Lưu: lukhach.yml (ID NPC, chỗ, lúc dời); ghi lukhach.json cho web (không có toạ độ).
 */
final class Wanderers {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    record Npc(String code, String dungeon, String name, String skin, String rumor) { }

    private final DuckyMoMoCore plugin;
    private final long moveMs;
    private final boolean announce;
    private final Map<String, Npc> npcs = new LinkedHashMap<>();
    private final File file;
    private final YamlConfiguration data;
    private int task = -1;

    Wanderers(DuckyMoMoCore plugin, ConfigurationSection c) {
        this.plugin = plugin;
        this.moveMs = Math.max(1, c.getLong("move-hours", 72)) * 3_600_000L;
        this.announce = c.getBoolean("announce", true);
        ConfigurationSection ns = c.getConfigurationSection("npcs");
        if (ns != null) {
            for (String k : ns.getKeys(false)) {
                String code = k.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                if (code.isEmpty() || ns.getString(k + ".name", "").isBlank()) {
                    continue;
                }
                npcs.put(code, new Npc(code, ns.getString(k + ".dungeon", ""), ns.getString(k + ".name"), ns.getString(k + ".skin", ""),
                        ns.getString(k + ".rumor", "có một người lạ mặt đang ngồi nghỉ chân")));
            }
        }
        this.file = new File(plugin.getDataFolder(), "lukhach.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    boolean ready() {
        return !npcs.isEmpty();
    }

    String describe() {
        return npcs.size() + " lữ khách, dời chỗ mỗi " + moveMs / 3_600_000L + " giờ";
    }

    void start() {
        Shared.wanderers = this;
        // đợi Citizens nạp xong NPC đã lưu (Citizens nạp sau khi server bật xong) rồi mới kiểm tra / tạo
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 600L, 6000L).getTaskId();
    }

    void stop() {
        Shared.wanderers = null;
        if (task != -1) {
            Bukkit.getScheduler().cancelTask(task);
        }
        save();
    }

    Map<String, Npc> npcs() {
        return npcs;
    }

    private String n(String code) {
        return "npc." + code + ".";
    }

    // ---------------------------------------------------------------- xuất hiện + dời chỗ

    private void tick() {
        long now = System.currentTimeMillis();
        for (Npc v : npcs.values()) {
            int id = data.getInt(n(v.code()) + "id", -1);
            if (!CitizensBridge.exists(id)) {
                spawn(v);
            } else if (now - data.getLong(n(v.code()) + "luc", 0) >= moveMs) {
                relocate(v, id);
            }
        }
    }

    private Location spot() {
        WildGates wg = Shared.wildGates;
        return wg == null ? null : wg.randomSpot(false);
    }

    private boolean spawn(Npc v) {
        Location l = spot();
        if (l == null) {
            return false;
        }
        int id = CitizensBridge.create(v.name(), l, v.skin());
        data.set(n(v.code()) + "id", id);
        placed(v, l);
        return true;
    }

    private boolean relocate(Npc v, int id) {
        Location l = spot();
        if (l == null) {
            return false;
        }
        CitizensBridge.move(id, l);
        placed(v, l);
        return true;
    }

    private void placed(Npc v, Location l) {
        String p = n(v.code());
        data.set(p + "world", l.getWorld().getName());
        data.set(p + "x", l.getBlockX());
        data.set(p + "y", l.getBlockY());
        data.set(p + "z", l.getBlockZ());
        data.set(p + "luc", System.currentTimeMillis());
        WildGates wg = Shared.wildGates;
        String[] z = wg == null ? new String[] {"?", ""} : wg.zoneLabel(l);
        data.set(p + "vung", z[0]);
        data.set(p + "huong", z[1]);
        save();
        writeJson();
        plugin.getLogger().info("Lữ khách " + v.name() + " -> " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + " (" + z[0] + ")");
        if (announce) {
            PlayerSettings.broadcast("sukien", c("&6📜 TIN ĐỒN: &eỞ " + z[0] + " (" + z[1] + ") " + v.rumor() + ". &7Người ta gọi là &f"
                    + v.name() + "&7. (/lukhach)"));
        }
    }

    // ---------------------------------------------------------------- cho BetonQuest

    /** Có người đi qua Cổng Hoang dẫn tới dungeon: gắn tag BetonQuest cho lữ khách kể về nơi đó. */
    void onGatePass(Player p, String dungeon) {
        for (Npc v : npcs.values()) {
            if (!v.dungeon().equals(dungeon)) {
                continue;
            }
            if (Bukkit.getPluginManager().isPluginEnabled("BetonQuest")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "bq tag " + p.getName() + " add cotruyen>lk_" + v.code() + "_qua");
            }
            p.sendMessage(c("&6📜 &e" + v.name() + " &7hẳn sẽ muốn nghe chuyện về nơi này. Tìm lữ khách đó (&f/lukhach&7) mà kể."));
        }
    }

    /** Giá trị cho placeholder %dmmlk_<mã>_<trường>% (null = không có). */
    String value(String code, String field) {
        Npc v = npcs.get(code);
        if (v == null) {
            return null;
        }
        switch (field) {
            case "ten" -> {
                return v.name();
            }
            case "npcvung" -> {
                return data.getString(n(code) + "vung", "");
            }
            case "npchuong" -> {
                return data.getString(n(code) + "huong", "");
            }
            default -> { }
        }
        WildGates wg = Shared.wildGates;
        Map<String, String> g = wg == null ? null : wg.gateInfo(v.dungeon());
        if (field.equals("cong")) {
            return g == null ? "khong" : "co";
        }
        if (g == null) {
            return field.equals("tim") ? "khong" : "";
        }
        return g.getOrDefault(field, null);
    }

    // ---------------------------------------------------------------- lệnh

    boolean command(CommandSender s, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        boolean admin = s.hasPermission("duckymomo.admin");
        if (admin && sub.equals("ds")) {
            for (Npc v : npcs.values()) {
                int id = data.getInt(n(v.code()) + "id", -1);
                Location l = CitizensBridge.exists(id) ? CitizensBridge.where(id) : null;
                String xyz = l == null ? "?" : l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
                Component line = c("&6" + v.code() + " &8— &f" + v.name() + " &7· NPC #" + id + " · " + xyz + " · " + data.getString(n(v.code()) + "vung", "?")
                        + " · cổng: " + value(v.code(), "cong"));
                if (l != null) {
                    line = line.append(c(" &a[Tới]").clickEvent(ClickEvent.runCommand("/minecraft:tp @s " + xyz))
                            .hoverEvent(HoverEvent.showText(c("&7Dịch chuyển tới " + xyz))));
                }
                s.sendMessage(line);
            }
            return true;
        }
        if (admin && sub.equals("doi")) {
            String which = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
            List<Npc> list = which.equals("all") ? new ArrayList<>(npcs.values()) : npcs.containsKey(which) ? List.of(npcs.get(which)) : List.of();
            if (list.isEmpty()) {
                s.sendMessage("/lukhach doi <mã|all>  (mã: " + String.join(", ", npcs.keySet()) + ")");
                return true;
            }
            for (Npc v : list) {
                int id = data.getInt(n(v.code()) + "id", -1);
                boolean ok = CitizensBridge.exists(id) ? relocate(v, id) : spawn(v);
                s.sendMessage((ok ? "Đã dời " : "Không tìm được chỗ cho ") + v.name());
            }
            return true;
        }
        if (admin && sub.equals("xoa")) {
            for (Npc v : npcs.values()) {
                CitizensBridge.remove(data.getInt(n(v.code()) + "id", -1));
            }
            data.set("npc", null);
            save();
            writeJson();
            s.sendMessage("Đã xoá mọi NPC lữ khách (5 phút sau tự tạo lại nếu module còn bật; tắt hẳn: wander-npcs.enabled false).");
            return true;
        }
        s.sendMessage(c("&6&l━━ 📜 LỮ KHÁCH ━━"));
        s.sendMessage(c("&7Những người lạ lang thang ngoài đất hoang, mỗi người biết chuyện về một nơi phía sau Cổng Hoang. Tìm họ, chuột phải để nói"
                + " chuyện. Cứ " + moveMs / 3_600_000L + " giờ họ lại dời đi chỗ khác."));
        for (Npc v : npcs.values()) {
            String vung = data.getString(n(v.code()) + "vung", "");
            s.sendMessage(c("  &f" + v.name() + " &8— " + (vung.isEmpty() ? "&8chưa xuất hiện" : "&7đang ở &e" + vung + " &7(" + data.getString(n(v.code()) + "huong", "") + ")")));
        }
        if (admin) {
            s.sendMessage(c("&8Admin: /lukhach ds · /lukhach doi <mã|all> · /lukhach xoa"));
        }
        return true;
    }

    // ---------------------------------------------------------------- lưu + web

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được lukhach.yml: " + e.getMessage());
        }
    }

    private void writeJson() {
        JsonObject root = new JsonObject();
        root.addProperty("gio_doi", moveMs / 3_600_000L);
        JsonArray arr = new JsonArray();
        DungeonGate dg = Shared.dungeonGate;
        for (Npc v : npcs.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("ma", v.code());
            o.addProperty("ten", v.name());
            o.addProperty("noi", dg == null ? v.dungeon() : dg.displayName(v.dungeon()));
            o.addProperty("vung", data.getString(n(v.code()) + "vung", ""));
            o.addProperty("huong", data.getString(n(v.code()) + "huong", ""));
            o.addProperty("luc", data.getLong(n(v.code()) + "luc", 0));
            o.addProperty("cong", "co".equals(value(v.code(), "cong")));
            arr.add(o);
        }
        root.add("lu_khach", arr);
        try {
            Files.writeString(new File(plugin.getDataFolder(), "lukhach.json").toPath(), root.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Không ghi được lukhach.json: " + e.getMessage());
        }
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
}
