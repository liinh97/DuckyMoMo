package vn.duckymomo.core.paper;

import java.util.List;
import java.util.Map;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * Placeholder cho hologram bảng xếp hạng (PlaceholderAPI, chỉ nạp khi có plugin này):
 * %dmm_bachu_1_ten%, %dmm_bachu_1_diem%, %dmm_chienthan_1_ten%, %dmm_chienthan_1_diem% (1..10).
 * Riêng từng người xem (DecentHolograms hiện khác nhau cho mỗi người): %dmm_toi_chienthan% ("12 điểm (#3)"),
 * %dmm_toi_bachu% (quốc gia của mình: "Đại Việt · 40 điểm (#2)"). Ý từ bảng "Bạn đã giết: 0 (#53)" của UniMC.
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

    private long cachedAt;
    private List<Map.Entry<String, Integer>> nationsAll = List.of();
    private List<Map.Entry<String, Integer>> warriorsAll = List.of();

    private void refresh() {
        long now = System.currentTimeMillis();
        if (now - cachedAt > 5000) {
            nationsAll = board.topNations(Integer.MAX_VALUE);
            warriorsAll = board.topWarriors(Integer.MAX_VALUE);
            cachedAt = now;
        }
    }

    private static int rankOf(List<Map.Entry<String, Integer>> list, String name) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getKey() != null && list.get(i).getKey().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private String mine(OfflinePlayer player, String what) {
        if (player == null || player.getName() == null) {
            return "";
        }
        refresh();
        if (what.equals("chienthan")) {
            int i = rankOf(warriorsAll, player.getName());
            return i < 0 ? "chưa có điểm" : warriorsAll.get(i).getValue() + " điểm (#" + (i + 1) + ")";
        }
        String nation = null;
        try {
            com.palmergames.bukkit.towny.object.Resident r = com.palmergames.bukkit.towny.TownyAPI.getInstance().getResident(player.getUniqueId());
            if (r != null && r.getNationOrNull() != null) {
                nation = r.getNationOrNull().getName();
            }
        } catch (RuntimeException | NoClassDefFoundError e) {
            return "";
        }
        if (nation == null) {
            return "chưa có quốc gia";
        }
        int i = rankOf(nationsAll, nation);
        return nation + (i < 0 ? " · chưa có điểm" : " · " + nationsAll.get(i).getValue() + " điểm (#" + (i + 1) + ")");
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params.equals("toi_chienthan") || params.equals("toi_bachu")) {
            return mine(player, params.substring(4));
        }
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
        sb.append("/dh line add bxh_bachu 1 &r\n/dh line add bxh_bachu 1 &7Quốc gia bạn: &f%dmm_toi_bachu%\n");
        sb.append("/dh create bxh_chienthan &c&l⚔ CHIẾN THẦN ⚔\n");
        for (int i = 1; i <= 5; i++) {
            sb.append("/dh line add bxh_chienthan 1 &e").append(i).append(". &f%dmm_chienthan_").append(i).append("_ten% &8- &a%dmm_chienthan_")
                    .append(i).append("_diem%\n");
        }
        sb.append("/dh line add bxh_chienthan 1 &r\n/dh line add bxh_chienthan 1 &7Bạn: &f%dmm_toi_chienthan%\n");
        return sb.toString();
    }
}
