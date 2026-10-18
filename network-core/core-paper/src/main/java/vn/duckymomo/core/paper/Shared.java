package vn.duckymomo.core.paper;

/**
 * Tham chiếu dùng chung giữa các module. Lớp này KHÔNG phụ thuộc plugin nào khác, nên đọc/ghi ở mọi server được.
 * Đừng đặt các tham chiếu này trong chính Frontier/Leaderboard: lớp đó có PlaceholderExpansion hoặc Towny, mà sảnh/practice
 * không có -> chạm vào (kể cả gán null lúc tắt plugin) là NoClassDefFoundError, phần dọn dẹp dừng giữa chừng.
 */
final class Shared {

    static volatile Frontier frontier;
    static volatile Leaderboard leaderboard;
    static volatile DungeonGate dungeonGate;

    private Shared() {
    }

    /** Mốc khai hoang đã mở chưa (trống = không cần mốc). Chỉ nạp lớp Frontier khi có module khai hoang. */
    static boolean unlocked(String goalId) {
        if (goalId == null || goalId.isBlank()) {
            return true;
        }
        Frontier f = frontier;
        return f != null && f.isReached(goalId);
    }

    static void clear() {
        frontier = null;
        leaderboard = null;
        dungeonGate = null;
    }
}
