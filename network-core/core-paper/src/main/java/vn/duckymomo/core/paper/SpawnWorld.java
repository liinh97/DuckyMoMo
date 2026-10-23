package vn.duckymomo.core.paper;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import java.io.File;
import java.io.FileInputStream;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;

/**
 * World spawn riêng (24/10, chủ server: spawn nhỏ ở world phẳng cho nhẹ, các khu dịch vụ ở đó, có cổng sang world sinh tồn).
 * World trống (một lớp không khí, khí hậu rừng anh đào), không sinh quái, trời luôn sáng, không mưa; plugin tự nạp lúc bật.
 * Công trình: schematic trong plugins/WorldEdit/schematics (spawn-world.schematic), dán bằng /dmm spawnmoi dan tại spawn-world.paste.
 * Config: spawn-world {enabled, name, schematic, paste {x,y,z}}.
 */
final class SpawnWorld {

    private final DuckyMoMoCore plugin;

    SpawnWorld(DuckyMoMoCore plugin) {
        this.plugin = plugin;
    }

    private ConfigurationSection cfg() {
        return plugin.getConfig().getConfigurationSection("spawn-world");
    }

    /** Nạp (tạo nếu chưa có) world spawn. Gọi lúc bật plugin khi spawn-world.enabled. */
    World load() {
        ConfigurationSection c = cfg();
        if (c == null) {
            return null;
        }
        String name = c.getString("name", "spawn");
        World w = Bukkit.getWorld(name);
        if (w == null) {
            w = new WorldCreator(name).type(WorldType.FLAT).generateStructures(false)
                    .generatorSettings("{\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}],\"biome\":\"minecraft:cherry_grove\"}")
                    .createWorld();
        }
        if (w != null) {
            w.setGameRule(GameRules.SPAWN_MOBS, false);
            w.setGameRule(GameRules.ADVANCE_TIME, false);
            w.setGameRule(GameRules.ADVANCE_WEATHER, false);
            w.setTime(6000);
            w.setStorm(false);
        }
        return w;
    }

    void command(CommandSender s, String[] a) {
        String sub = a.length >= 2 ? a[1].toLowerCase(java.util.Locale.ROOT) : "";
        ConfigurationSection c = cfg();
        if (c == null) {
            s.sendMessage("Chưa có spawn-world trong config.");
            return;
        }
        World w = load();
        if (w == null) {
            s.sendMessage("Không tạo được world spawn.");
            return;
        }
        switch (sub) {
            case "dan" -> {
                File f = new File(new File(plugin.getDataFolder().getParentFile(), "WorldEdit/schematics"), c.getString("schematic", ""));
                ClipboardFormat fmt = ClipboardFormats.findByFile(f);
                if (!f.isFile() || fmt == null) {
                    s.sendMessage("Không đọc được schematic " + f);
                    return;
                }
                int x = c.getInt("paste.x", 0), y = c.getInt("paste.y", 100), z = c.getInt("paste.z", 0);
                try (ClipboardReader r = fmt.getReader(new FileInputStream(f))) {
                    Clipboard clip = r.read();
                    try (EditSession es = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(w))) {
                        Operations.complete(new ClipboardHolder(clip).createPaste(es).to(BlockVector3.at(x, y, z)).ignoreAirBlocks(true).build());
                        s.sendMessage("Đã dán " + f.getName() + " vào world " + w.getName() + " tại " + x + " " + y + " " + z + " ("
                                + es.getBlockChangeCount() + " khối). Vùng: " + clip.getRegion().getMinimumPoint().subtract(clip.getOrigin()).add(x, y, z)
                                + " → " + clip.getRegion().getMaximumPoint().subtract(clip.getOrigin()).add(x, y, z));
                    }
                } catch (Exception ex) {
                    s.sendMessage("Lỗi dán schematic: " + ex.getMessage());
                    plugin.getLogger().warning("Spawn mới: lỗi dán " + ex);
                }
            }
            case "diemxuathien" -> {
                Location l = new Location(w, c.getDouble("spawn.x", 0.5), c.getDouble("spawn.y", 100), c.getDouble("spawn.z", 0.5),
                        (float) c.getDouble("spawn.yaw", 0), 0);
                w.setSpawnLocation(l);
                s.sendMessage("Điểm xuất hiện world " + w.getName() + ": " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ());
            }
            case "dattaiday" -> {
                if (!(s instanceof org.bukkit.entity.Player p) || p.getWorld() != w) {
                    s.sendMessage("Vào world " + w.getName() + " (lệnh: /execute in minecraft:" + w.getName() + " run tp @s 5 81 -1), đứng chỗ muốn làm điểm xuất hiện rồi gõ lại.");
                    return;
                }
                Location l = p.getLocation();
                Location at = new Location(w, Math.floor(l.getX()) + 0.5, Math.floor(l.getY()), Math.floor(l.getZ()) + 0.5, Math.round(l.getYaw() / 45f) * 45f, 0);
                // lưu file riêng (KHÔNG saveConfig: ghi đè config.yml sẽ mất hết chú thích)
                org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
                y.set("x", at.getX());
                y.set("y", at.getY());
                y.set("z", at.getZ());
                y.set("yaw", (double) at.getYaw());
                try {
                    y.save(new File(plugin.getDataFolder(), "spawn-diem.yml"));
                } catch (java.io.IOException ex) {
                    s.sendMessage("Không lưu được spawn-diem.yml: " + ex.getMessage());
                }
                w.setSpawnLocation(at);
                s.sendMessage("Đã đặt điểm xuất hiện world " + w.getName() + " tại " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()
                        + " (hướng nhìn đang đứng), lưu ở spawn-diem.yml.");
            }
            default -> s.sendMessage("/dmm spawnmoi dan | diemxuathien | dattaiday  (world: " + w.getName() + ")");
        }
    }
}
