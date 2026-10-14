package vn.duckymomo.core.paper;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.Location;

/**
 * Hỏi SiegeWar (gọi qua reflection, không cần thư viện lúc build): có đang trong phiên đánh không,
 * một vị trí có nằm trong vùng vây đang diễn ra không. Không có SiegeWar thì trả về false.
 * Hàm dùng: com.gmail.goosius.siegewar.SiegeWarAPI (SiegeWar 3.7.0).
 */
final class SiegeWarHook {

    private static Method battleActive;
    private static Method inZone;
    private static boolean tried;

    private SiegeWarHook() {
    }

    private static boolean load() {
        if (!Bukkit.getPluginManager().isPluginEnabled("SiegeWar")) {
            return false;
        }
        if (!tried) {
            tried = true;
            try {
                Class<?> api = Class.forName("com.gmail.goosius.siegewar.SiegeWarAPI", true,
                        Bukkit.getPluginManager().getPlugin("SiegeWar").getClass().getClassLoader());
                battleActive = api.getMethod("isBattleSessionActive");
                inZone = api.getMethod("isLocationInActiveSiegeZone", Location.class);
            } catch (ReflectiveOperationException | RuntimeException e) {
                Bukkit.getLogger().warning("[DuckyMoMoCore] Không gọi được SiegeWarAPI: " + e);
            }
        }
        return battleActive != null && inZone != null;
    }

    static boolean available() {
        return load();
    }

    /** Đang trong phiên đánh (SiegeWar tự huỷ phiên khi không có cuộc vây nào). */
    static boolean battleActive() {
        try {
            return load() && (Boolean) battleActive.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Vị trí nằm trong vùng vây (bán kính quanh cờ) của một cuộc vây đang diễn ra, kể cả ngoài giờ đánh. */
    static boolean inActiveSiegeZone(Location loc) {
        try {
            return load() && (Boolean) inZone.invoke(null, loc);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
