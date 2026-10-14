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
    private static Method hasSiege;
    private static Method activeSieges;
    private static Method battleSession;
    private static boolean tried;

    private SiegeWarHook() {
    }

    private static boolean load() {
        if (!Bukkit.getPluginManager().isPluginEnabled("SiegeWar")) {
            return false;
        }
        if (!tried) {
            tried = true;
            ClassLoader cl = Bukkit.getPluginManager().getPlugin("SiegeWar").getClass().getClassLoader();
            Class<?> api = find("com.gmail.goosius.siegewar.SiegeWarAPI", cl);
            Class<?> town = find("com.palmergames.bukkit.towny.object.Town", cl);
            battleActive = method(api, "isBattleSessionActive");
            inZone = method(api, "isLocationInActiveSiegeZone", Location.class);
            activeSieges = method(api, "getActiveSieges");
            hasSiege = town == null ? null : method(api, "hasActiveSiege", town);
            // getBattleSession nằm ở lớp BattleSession (không phải SiegeWarAPI)
            battleSession = method(find("com.gmail.goosius.siegewar.objects.BattleSession", cl), "getBattleSession");
        }
        return true;
    }

    private static Class<?> find(String name, ClassLoader cl) {
        try {
            return Class.forName(name, true, cl);
        } catch (ReflectiveOperationException | LinkageError e) {
            Bukkit.getLogger().warning("[DuckyMoMoCore] SiegeWar thiếu lớp " + name);
            return null;
        }
    }

    /** Lấy từng hàm riêng: SiegeWar đổi một hàm thì chỉ phần đó tắt, phần khác vẫn chạy. */
    private static Method method(Class<?> c, String name, Class<?>... args) {
        if (c == null) {
            return null;
        }
        try {
            return c.getMethod(name, args);
        } catch (ReflectiveOperationException e) {
            Bukkit.getLogger().warning("[DuckyMoMoCore] SiegeWar thiếu hàm " + c.getSimpleName() + "." + name);
            return null;
        }
    }

    static boolean available() {
        return load();
    }

    /** Đang trong phiên đánh (SiegeWar tự huỷ phiên khi không có cuộc vây nào). */
    static boolean battleActive() {
        try {
            return load() && battleActive != null && (Boolean) battleActive.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Một cuộc vây đang diễn ra: mã, thị trấn bị vây, bên tấn công, vị trí cờ. */
    record SiegeInfo(String id, String town, String attacker, Location flag) {
    }

    static java.util.List<SiegeInfo> activeSieges() {
        java.util.List<SiegeInfo> out = new java.util.ArrayList<>();
        try {
            if (!load() || activeSieges == null) {
                return out;
            }
            for (Object s : (java.util.Collection<?>) activeSieges.invoke(null)) {
                Class<?> c = s.getClass();
                Object town = c.getMethod("getTown").invoke(s);
                String townName = town == null ? "?" : String.valueOf(town.getClass().getMethod("getName").invoke(town));
                out.add(new SiegeInfo(String.valueOf(c.getMethod("getUUID").invoke(s)), townName,
                        String.valueOf(c.getMethod("getAttackerNameForDisplay").invoke(s)), (Location) c.getMethod("getFlagLocation").invoke(s)));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // SiegeWar đổi API: không có thông báo vây, không chặn gì khác
        }
        return out;
    }

    /** Giờ bắt đầu phiên đánh tới (mili giây), null nếu không biết. */
    static Long nextBattleSessionStart() {
        try {
            if (!load() || battleSession == null) {
                return null;
            }
            Object bs = battleSession.invoke(null);
            Object v = bs == null ? null : bs.getClass().getMethod("getScheduledStartTime").invoke(bs);
            return v instanceof Long l && l > 0 ? l : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Thị trấn (com.palmergames…Town) đang bị vây. */
    static boolean hasActiveSiege(Object town) {
        try {
            return town != null && load() && hasSiege != null && (Boolean) hasSiege.invoke(null, town);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Vị trí nằm trong vùng vây (bán kính quanh cờ) của một cuộc vây đang diễn ra, kể cả ngoài giờ đánh. */
    static boolean inActiveSiegeZone(Location loc) {
        try {
            return load() && inZone != null && (Boolean) inZone.invoke(null, loc);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
