package vn.duckymomo.core.paper;

import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.metadata.CustomDataField;

/**
 * Thị trấn đang hoà bình theo SiegeWar (21/10): SiegeWar 3.7 lưu ở metadata "siegewar_peaceSetting" của thị trấn, KHÔNG phải cờ
 * neutral của Towny (lệnh swa town X setpeaceful true báo thành công nhưng neutral vẫn false -> trước đây tưởng chưa hoà bình mãi).
 * Chỉ gọi ở server có Towny.
 */
final class PeaceStatus {

    private PeaceStatus() {
    }

    static boolean peaceful(Town t) {
        if (t == null) {
            return false;
        }
        if (t.isNeutral()) {
            return true;
        }
        CustomDataField<?> f = t.getMetadata("siegewar_peaceSetting");
        return f != null && Boolean.TRUE.equals(f.getValue());
    }
}
