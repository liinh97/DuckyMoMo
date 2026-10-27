package vn.duckymomo.core.paper;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import org.bukkit.entity.Player;

/** Chạm Towny riêng ở đây (chỉ gọi khi Towny bật): lớp khác tham chiếu kiểu Towny mà server không có Towny là lỗi nạp lớp. */
final class TownyCheck {

    private TownyCheck() {
    }

    static boolean hasTown(Player p) {
        Resident r = TownyAPI.getInstance().getResident(p);
        return r != null && r.hasTown();
    }
}
