package vn.duckymomo.core.paper;

import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;

/**
 * Số liệu PvP của chính người chơi cho bảng bên phải (TAB), lấy từ thống kê gốc của Minecraft trên server này:
 * %dmmtoi_giet% (số người đã giết), %dmmtoi_chet% (số lần chết), %dmmtoi_kda% (giết / chết, 1 chữ số thập phân).
 */
final class PlayerStatsPlaceholders extends PlaceholderExpansion {

    /**
     * Tạo qua hàm tĩnh (kiểu trả về là PlaceholderExpansion): DuckyMoMoCore không nhắc thẳng tới lớp con này, nên server KHÔNG có
     * PlaceholderAPI (sảnh) vẫn nạp được plugin. Gán thẳng "new PlayerStatsPlaceholders()" vào biến PlaceholderExpansion làm Java phải
     * nạp PlaceholderAPI ngay lúc kiểm tra lớp DuckyMoMoCore -> NoClassDefFoundError, cả plugin không chạy (lỗi 16/10 ở sảnh).
     */
    static PlaceholderExpansion create() {
        return new PlayerStatsPlaceholders();
    }

    private static long newDaySec = -1;

    /** 30/10: thời gian tới "ngày mới" của Towny (thu thuế / phí duy trì), dạng ngắn "5g12p". Đọc new_day_time (vd 12h) của Towny. */
    private static String untilNewDay() {
        if (newDaySec < 0) {
            newDaySec = 12 * 3600;
            var t = org.bukkit.Bukkit.getPluginManager().getPlugin("Towny");
            if (t != null) {
                var y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        new java.io.File(t.getDataFolder(), "settings/config.yml"));
                String v = y.getString("plugin.day_timer.new_day_time", y.getString("new_day_time", "12h")).trim().toLowerCase(Locale.ROOT);
                try {
                    long n = Long.parseLong(v.replaceAll("[^0-9]", ""));
                    newDaySec = v.endsWith("h") ? n * 3600 : v.endsWith("m") ? n * 60 : n;
                } catch (NumberFormatException ignored) {
                    // giữ 12h
                }
            }
        }
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime next = now.toLocalDate().atStartOfDay().plusSeconds(newDaySec);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        long m = java.time.Duration.between(now, next).toMinutes();
        return m >= 60 ? (m / 60) + "g" + (m % 60) + "p" : m + "p";
    }

    @Override
    public String getIdentifier() {
        return "dmmtoi";
    }

    @Override
    public String getAuthor() {
        return "DuckyMoMo";
    }

    @Override
    public String getVersion() {
        return "1";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if ("thue_con".equals(params)) {
            return untilNewDay();
        }
        if (player == null) {
            return "";
        }
        int kills;
        int deaths;
        try {
            kills = player.getStatistic(Statistic.PLAYER_KILLS);
            deaths = player.getStatistic(Statistic.DEATHS);
        } catch (RuntimeException e) {
            return "0";
        }
        return switch (params) {
            case "giet" -> String.valueOf(kills);
            case "chet" -> String.valueOf(deaths);
            case "kda" -> String.format(Locale.ROOT, "%.1f", kills / (double) Math.max(1, deaths));
            default -> null;
        };
    }
}
