package vn.duckymomo.core.paper;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/** Thay %placeholder% bằng PlaceholderAPI. Lớp riêng: chỉ nạp khi server CÓ PlaceholderAPI (sảnh/practice không có, xem HANDOFF bài học 4). */
final class PapiText {
    private PapiText() {
    }

    static String apply(Player p, String s) {
        return PlaceholderAPI.setPlaceholders(p, s);
    }
}
