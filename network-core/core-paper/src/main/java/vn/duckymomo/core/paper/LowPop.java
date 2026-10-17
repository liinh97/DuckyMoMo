package vn.duckymomo.core.paper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Server vắng thì tự nhẹ đi (config low-population). Quy mô = số người có chơi trong 7 ngày gần nhất (Activity.weekHours > 0),
 * chốt 1 lần mỗi ngày (giờ VN) để chỉ tiêu không nhảy giữa ngày. Dưới threshold thì:
 *  - nhiệm vụ hằng ngày: chỉ tiêu × quest-scale;
 *  - bắt pet: +capture-bonus % xác suất;
 *  - công thành: phe phòng thủ KHÔNG ai online -> điểm giữ cờ của bên tấn công chỉ còn absent-defender-attack-percent %.
 */
final class LowPop {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static volatile Activity activity;
    private static volatile ConfigurationSection cfg;
    private static volatile String day = "";
    private static volatile int pop = Integer.MAX_VALUE;

    private LowPop() {
    }

    static void init(Activity a, ConfigurationSection c) {
        activity = a;
        cfg = c != null && c.getBoolean("enabled", true) ? c : null;
        day = "";
    }

    /** Số người chơi trong 7 ngày (chốt theo ngày). */
    static int population() {
        String today = LocalDate.now(VN).toString();
        Activity a = activity;
        if (a == null) {
            return Integer.MAX_VALUE;
        }
        if (!today.equals(day)) {
            int n = 0;
            for (String id : a.knownPlayers()) {
                try {
                    if (a.weekHours(UUID.fromString(id)) > 0) {
                        n++;
                    }
                } catch (IllegalArgumentException ignored) {
                    // khoá lạ trong hoatdong.yml
                }
            }
            pop = n;
            day = today;
        }
        return pop;
    }

    static boolean small() {
        ConfigurationSection c = cfg;
        return c != null && population() < c.getInt("threshold", 15);
    }

    static double questScale() {
        return small() ? Math.max(0.1, Math.min(1, cfg.getDouble("quest-scale", 0.6))) : 1.0;
    }

    static int captureBonus() {
        return small() ? Math.max(0, cfg.getInt("capture-bonus", 10)) : 0;
    }

    /** % điểm giữ cờ bên tấn công còn lại khi phe phòng thủ vắng (100 = không đổi). */
    static double absentDefenderAttackPct() {
        return small() ? Math.max(0, Math.min(100, cfg.getDouble("absent-defender-attack-percent", 50))) : 100;
    }
}
