package vn.duckymomo.core.paper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Xuất MỌI lệnh đang đăng ký trên server ra plugins/DuckyMoMoCore/commands.json cho trang Cẩm nang (panel /camnang,
 * mục "Tất cả lệnh"). Chạy 30 giây sau khi server khởi động (để plugin đăng ký lệnh muộn cũng có) và bằng /dmm lenh.
 * Mỗi lệnh: plugin (namespace), tên, tên gọi khác, mô tả, cách dùng, quyền, mặc định của quyền (true/op/false/not_op).
 */
final class CommandDump {

    private final JavaPlugin plugin;

    CommandDump(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void schedule() {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int n = dump();
            plugin.getLogger().info("Đã xuất " + n + " lệnh ra commands.json (cho trang Cẩm nang)");
        }, 600L);
    }

    int dump() {
        Map<String, Command> known = Bukkit.getCommandMap().getKnownCommands();
        // gom theo đối tượng lệnh: khoá "ns:tên" cho biết plugin; khoá không có ":" là tên gọi ngắn
        Map<Command, String> ns = new IdentityHashMap<>();
        for (Map.Entry<String, Command> e : known.entrySet()) {
            String key = e.getKey();
            int i = key.indexOf(':');
            if (i > 0) {
                ns.putIfAbsent(e.getValue(), key.substring(0, i));
            }
        }
        Map<String, String> rows = new TreeMap<>();
        for (Map.Entry<Command, String> e : ns.entrySet()) {
            Command c = e.getKey();
            String owner = c instanceof PluginIdentifiableCommand pic ? pic.getPlugin().getName() : e.getValue();
            String perm = c.getPermission();
            String def = "";
            if (perm != null && !perm.isBlank()) {
                Permission p = Bukkit.getPluginManager().getPermission(perm.split(";")[0]);
                def = p == null ? "" : p.getDefault().name().toLowerCase();
            }
            List<String> aliases = new ArrayList<>(c.getAliases());
            String json = "{\"plugin\":" + q(owner) + ",\"ns\":" + q(e.getValue()) + ",\"name\":" + q(c.getName())
                    + ",\"aliases\":[" + String.join(",", aliases.stream().map(CommandDump::q).toList()) + "]"
                    + ",\"description\":" + q(c.getDescription()) + ",\"usage\":" + q(c.getUsage())
                    + ",\"permission\":" + q(perm == null ? "" : perm) + ",\"default\":" + q(def) + "}";
            rows.put(owner.toLowerCase() + " " + c.getName() + " " + e.getValue(), json);
        }
        String out = "{\"server\":" + q(serverName()) + ",\"generated\":" + System.currentTimeMillis() / 1000
                + ",\"commands\":[\n" + String.join(",\n", rows.values()) + "\n]}\n";
        try {
            Files.writeString(new File(plugin.getDataFolder(), "commands.json").toPath(), out, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().warning("Không ghi được commands.json: " + ex.getMessage());
        }
        return rows.size();
    }

    private static String serverName() {
        String n = System.getenv("CFG_SERVER_NAME");
        return n == null ? "" : n;
    }

    private static String q(String s) {
        if (s == null) {
            return "\"\"";
        }
        StringBuilder b = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> { }
                case '\t' -> b.append(' ');
                default -> {
                    if (ch < 0x20) {
                        continue;
                    }
                    b.append(ch);
                }
            }
        }
        return b.append('"').toString();
    }
}
