package vn.duckymomo.core.paper;

import java.util.UUID;

/**
 * Hai người có phải phe mình không (cùng thị trấn, cùng quốc gia, hoặc quốc gia đồng minh). Lớp riêng có Towny: chỉ gọi khi Towny bật
 * (SkillEnchants "Hộ giá"), sảnh / Practice không bao giờ nạp lớp này.
 */
final class TownyAllies {

    private TownyAllies() {
    }

    static boolean allies(UUID a, UUID b) {
        var api = com.palmergames.bukkit.towny.TownyAPI.getInstance();
        com.palmergames.bukkit.towny.object.Resident ra = api.getResident(a), rb = api.getResident(b);
        if (ra == null || rb == null || !ra.hasTown() || !rb.hasTown()) {
            return false;
        }
        if (ra.getTownOrNull() == rb.getTownOrNull()) {
            return true;
        }
        com.palmergames.bukkit.towny.object.Nation na = ra.getNationOrNull(), nb = rb.getNationOrNull();
        return na != null && nb != null && (na == nb || na.hasAlly(nb));
    }
}
