package vn.duckymomo.core.paper;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Một chế độ chơi, đọc từ modes/<id>/mode.yml (cùng file mà network-core sẽ đồng bộ vào bảng network.modes). */
record GameMode(String id, String displayName, String description, String icon, String status,
                List<String> clients, String server, String betaPermission) {

    boolean hidden() {
        return status.equals("closed");
    }

    boolean joinable() {
        return status.equals("open") || status.equals("beta");
    }

    boolean supportsBedrock() {
        return clients.contains("bedrock");
    }

    /** Đọc mọi modes/*\/mode.yml (bỏ qua thư mục khuôn mẫu bắt đầu bằng "_"). Đọc lại mỗi lần mở menu. */
    static List<GameMode> loadAll(File modesDir, Logger log) {
        List<GameMode> modes = new ArrayList<>();
        File[] dirs = modesDir.listFiles(File::isDirectory);
        if (dirs == null) {
            log.warning("Không đọc được thư mục chế độ: " + modesDir);
            return modes;
        }
        for (File dir : dirs) {
            File file = new File(dir, "mode.yml");
            if (dir.getName().startsWith("_") || !file.isFile()) {
                continue;
            }
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            String server = null;
            List<?> servers = y.getList("servers");
            if (servers != null && !servers.isEmpty() && servers.get(0) instanceof java.util.Map<?, ?> first) {
                Object name = first.get("name");
                server = name == null ? null : name.toString();
            }
            ConfigurationSection vis = y.getConfigurationSection("visibility");
            modes.add(new GameMode(
                y.getString("id", dir.getName()),
                y.getString("display_name", dir.getName()),
                y.getString("description", ""),
                y.getString("icon", "minecraft:grass_block"),
                y.getString("status", "closed").toLowerCase(Locale.ROOT),
                y.getStringList("clients"),
                server,
                vis == null ? "network.beta" : vis.getString("beta_permission", "network.beta")));
        }
        modes.sort(Comparator.comparing(GameMode::id));
        return modes;
    }
}
