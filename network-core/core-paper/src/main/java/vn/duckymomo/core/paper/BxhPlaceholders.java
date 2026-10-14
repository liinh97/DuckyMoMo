package vn.duckymomo.core.paper;

import java.util.List;
import java.util.Map;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * Placeholder cho hologram bảng xếp hạng (PlaceholderAPI, chỉ nạp khi có plugin này):
 * %dmm_bachu_1_ten%, %dmm_bachu_1_diem%, %dmm_chienthan_1_ten%, %dmm_chienthan_1_diem% (1..10).
 */
final class BxhPlaceholders extends PlaceholderExpansion {

    private final Leaderboard board;

    BxhPlaceholders(Leaderboard board) {
        this.board = board;
    }

    @Override
    public String getIdentifier() {
        return "dmm";
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
        String[] p = params.split("_");
        if (p.length != 3) {
            return null;
        }
        int i;
        try {
            i = Integer.parseInt(p[1]) - 1;
        } catch (NumberFormatException e) {
            return null;
        }
        List<Map.Entry<String, Integer>> list = switch (p[0]) {
            case "bachu" -> board.topNations(10);
            case "chienthan" -> board.topWarriors(10);
            default -> null;
        };
        if (list == null) {
            return null;
        }
        if (i < 0 || i >= list.size()) {
            return p[2].equals("ten") ? "---" : "";
        }
        return p[2].equals("ten") ? list.get(i).getKey() : String.valueOf(list.get(i).getValue());
    }

    static String hologramHelp() {
        StringBuilder sb = new StringBuilder("Tạo hologram bảng xếp hạng (đứng tại chỗ muốn đặt, gõ lần lượt):\n");
        sb.append("/dh create bxh_bachu &6&l⚑ BÁ CHỦ MÙA ⚑\n");
        for (int i = 1; i <= 5; i++) {
            sb.append("/dh line add bxh_bachu 1 &e").append(i).append(". &f%dmm_bachu_").append(i).append("_ten% &8- &a%dmm_bachu_")
                    .append(i).append("_diem%\n");
        }
        sb.append("/dh create bxh_chienthan &c&l⚔ CHIẾN THẦN ⚔\n");
        for (int i = 1; i <= 5; i++) {
            sb.append("/dh line add bxh_chienthan 1 &e").append(i).append(". &f%dmm_chienthan_").append(i).append("_ten% &8- &a%dmm_chienthan_")
                    .append(i).append("_diem%\n");
        }
        return sb.toString();
    }
}
