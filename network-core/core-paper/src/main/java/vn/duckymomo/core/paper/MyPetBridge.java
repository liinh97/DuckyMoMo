package vn.duckymomo.core.paper;

import java.lang.reflect.Method;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Gọi MyPet qua phản chiếu (không cần jar MyPet khi build). Mọi hàm trả null/false khi MyPet tắt hoặc API đổi.
 * MyPet API: MyPetApi.getMyPetManager().hasActiveMyPet(Player) / getMyPet(Player) -> MyPet (có khi Optional);
 * MyPet: getPetType() (enum, vd "Wolf"), getEntity() -> Optional<MyPetBukkitEntity>, getExperience().addExp(double),
 * getSaturation()/setSaturation(double) (0..100).
 */
final class MyPetBridge {

    private MyPetBridge() {
    }

    private static Object manager() throws ReflectiveOperationException {
        Plugin mp = Bukkit.getPluginManager().getPlugin("MyPet");
        if (mp == null || !mp.isEnabled()) {
            return null;
        }
        return Class.forName("de.Keyle.MyPet.MyPetApi", true, mp.getClass().getClassLoader()).getMethod("getMyPetManager").invoke(null);
    }

    /** Pet đang dẫn của người chơi (đối tượng MyPet), hoặc null. */
    static Object active(Player p) {
        try {
            Object mgr = manager();
            if (mgr == null || !(Boolean) acc(mgr.getClass().getMethod("hasActiveMyPet", Player.class)).invoke(mgr, p)) {
                return null;
            }
            Object pet = acc(mgr.getClass().getMethod("getMyPet", Player.class)).invoke(mgr, p);
            return pet instanceof Optional<?> o ? o.orElse(null) : pet;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Đối tượng MyPet của một thực thể pet (MyPetBukkitEntity.getMyPet()), hoặc null. */
    static Object of(Entity petEntity) {
        if (!PetCombat.isPet(petEntity)) {
            return null;
        }
        try {
            return call(petEntity, "getMyPet");
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Loài MyPet (vd "Wolf", "PolarBear"), hoặc null. */
    static String type(Object myPet) {
        try {
            Object t = call(myPet, "getPetType");
            return t == null ? null : ((Enum<?>) t).name();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Thực thể đang hiện trong thế giới của pet, hoặc null (pet chết / chưa gọi ra). */
    static LivingEntity entity(Object myPet) {
        try {
            Object e = call(myPet, "getEntity");
            if (e instanceof Optional<?> o) {
                e = o.orElse(null);
            }
            return e instanceof LivingEntity le && le.isValid() ? le : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    static boolean addExp(Object myPet, double exp) {
        try {
            Object xp = call(myPet, "getExperience");
            acc(xp.getClass().getMethod("addExp", double.class)).invoke(xp, exp);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Cấp hiện tại của pet (0 = không đọc được). */
    static int level(Object myPet) {
        try {
            Object xp = call(myPet, "getExperience");
            return ((Number) call(xp, "getLevel")).intValue();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }

    static double saturation(Object myPet) {
        try {
            return ((Number) call(myPet, "getSaturation")).doubleValue();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    static boolean setSaturation(Object myPet, double v) {
        try {
            acc(myPet.getClass().getMethod("setSaturation", double.class)).invoke(myPet, Math.max(0, Math.min(100, v)));
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static Object call(Object o, String name) throws ReflectiveOperationException {
        return acc(o.getClass().getMethod(name)).invoke(o);
    }

    /** Lớp cài đặt của MyPet có thể không public: mở quyền gọi. */
    private static Method acc(Method m) {
        m.setAccessible(true);
        return m;
    }
}
