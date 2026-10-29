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
    static volatile HamNguc hamNguc;
    static volatile WildGates wildGates;
    static volatile Wanderers wanderers;
    static volatile Refining refining;
    static volatile Backpack backpack;
    static volatile MediaProgram media;
    /** Lệnh kinh tế chỉ dùng trong đất hoà bình (PeaceAlliance): test(người, "lệnh không có /") = true nghĩa là BỊ CHẶN (đã báo người chơi).
     *  Kiểu BiPredicate để lớp chạy ở sảnh (không có Towny) gọi được mà không nạp PeaceAlliance. */
    static volatile java.util.function.BiPredicate<org.bukkit.entity.Player, String> economyGate;
    /** Giới hạn tiền bán cho server mỗi ngày (null = tắt). */
    static volatile SellLimit sellLimit;
    /** Thư viện đồ: mọi module lấy vật phẩm đặc biệt từ đây (Xưởng, món ăn, Tinh Luyện, mảnh, bùa, boss). */
    static volatile ItemLibrary library;

    /** Admin reset "như người mới": mỗi module có dữ liệu theo người chơi đăng ký một hàm xoá dữ liệu của UUID đó. */
    /** Đang cho vào/rời nghề hộ (chơi lại, admin): luật "chỉ vào/rời nghề trong đất hoà bình" bỏ qua các UUID này. */
    static final java.util.Set<java.util.UUID> JOBS_FREE = java.util.concurrent.ConcurrentHashMap.newKeySet();

    static final java.util.List<java.util.function.Consumer<java.util.UUID>> FORGET = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Xoá mọi mục có khoá = UUID (ở mọi tầng: "players.<uuid>", "dem.<uuid>"…) trong file dữ liệu của module rồi lưu. */
    static void forget(org.bukkit.configuration.file.YamlConfiguration data, java.io.File file, java.util.UUID id) {
        if (removeKey(data, id.toString())) {
            try {
                data.save(file);
            } catch (java.io.IOException ignored) {
                // lưu lại ở lần ghi sau của module
            }
        }
    }

    private static boolean removeKey(org.bukkit.configuration.ConfigurationSection s, String key) {
        boolean hit = false;
        for (String k : new java.util.ArrayList<>(s.getKeys(false))) {
            if (k.equals(key)) {
                s.set(k, null);
                hit = true;
            } else if (s.isConfigurationSection(k)) {
                hit |= removeKey(s.getConfigurationSection(k), key);
            }
        }
        return hit;
    }

    private Shared() {
    }

    /** Mốc khai hoang đã mở chưa (trống = không cần mốc). Chỉ nạp lớp Frontier khi có module khai hoang. */
    static boolean unlocked(String goalId) {
        if (goalId == null || goalId.isBlank()) {
            return true;
        }
        Frontier f = frontier;
        // 27/10: khai hoang tắt (frontier.enabled false) = đã mở hết map -> mọi mốc coi như đã mở (vùng /tpr, dungeon gắn mốc)
        return f == null || f.isReached(goalId);
    }

    static void clear() {
        frontier = null;
        leaderboard = null;
        dungeonGate = null;
        wildGates = null;
        wanderers = null;
        refining = null;
        backpack = null;
        media = null;
        sellLimit = null;
        economyGate = null;
        library = null;
        ItemLibrary.STAMPERS.clear();
        FORGET.clear();
    }
}
