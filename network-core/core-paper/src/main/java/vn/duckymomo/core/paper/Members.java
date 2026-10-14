package vn.duckymomo.core.paper;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Hàm dùng chung về người chơi trong Towny (chỉ gọi khi có Towny). */
final class Members {

    private Members() {
    }

    /** Dân hoạt động: chơi thật đủ giờ trong 7 ngày và đã ở thị trấn đủ new-member-days ngày. */
    static boolean active(Activity activity, Resident r) {
        if (r == null || activity == null || !activity.isActive(r.getUUID())) {
            return false;
        }
        long joined = r.getJoinedTownAt();
        return joined <= 0 || System.currentTimeMillis() - joined >= activity.newMemberDays() * 86_400_000L;
    }

    static long activeCount(Activity activity, Town t) {
        return t == null ? 0 : t.getResidents().stream().filter(r -> active(activity, r)).count();
    }

    /** Có quân hàm được tính điểm trận (guard của thị trấn, private…general của quốc gia). */
    static boolean military(Resident r, Set<String> ranks) {
        if (r == null) {
            return false;
        }
        for (String s : r.getTownRanks()) {
            if (ranks.contains(s.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        for (String s : r.getNationRanks()) {
            if (ranks.contains(s.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    static Resident resident(UUID id) {
        return TownyAPI.getInstance().getResident(id);
    }

    static Resident resident(String name) {
        return TownyAPI.getInstance().getResident(name);
    }

    static Nation nationOf(Resident r) {
        return r == null ? null : r.getNationOrNull();
    }

    static String nationName(Resident r) {
        Nation n = nationOf(r);
        return n == null ? null : n.getName();
    }

    /** Hai người cùng quốc gia (hoặc cùng thị trấn nếu không có quốc gia). */
    static boolean sameSide(Resident a, Resident b) {
        if (a == null || b == null) {
            return false;
        }
        Nation na = a.getNationOrNull();
        Nation nb = b.getNationOrNull();
        if (na != null && nb != null) {
            return na == nb;
        }
        Town ta = a.getTownOrNull();
        Town tb = b.getTownOrNull();
        return ta != null && ta == tb;
    }
}
