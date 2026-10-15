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
