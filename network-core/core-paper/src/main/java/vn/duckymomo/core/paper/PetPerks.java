package vn.duckymomo.core.paper;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

/**
 * Đặc quyền Thăng Cấp cho pet MyPet (quyền LuckPerms do /capdo cấp):
 *  - duckymomo.pet.kinhnghiem: pet nhận thêm 15% kinh nghiệm (MyPetExpEvent, mọi nguồn: hạ quái, ăn cá, Đấu Trường).
 *  - duckymomo.pet.hoisinh: pet chết hồi sinh nhanh gấp đôi (MyPet đặt thời gian chờ lúc pet chết, 1 tick sau giảm một nửa).
 * Gắn sự kiện MyPet qua phản chiếu (không cần jar MyPet khi build).
 */
final class PetPerks implements Listener {

    static final String EXP_PERM = "duckymomo.pet.kinhnghiem";
    static final String RESPAWN_PERM = "duckymomo.pet.hoisinh";
    private static final double EXP_BONUS = 0.15;
    private static final double RESPAWN_FACTOR = 0.5;

    private final JavaPlugin plugin;

    PetPerks(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("unchecked")
    void start() {
        Plugin mp = Bukkit.getPluginManager().getPlugin("MyPet");
        if (mp == null || !mp.isEnabled()) {
            return;
        }
        try {
            Class<? extends Event> cls = (Class<? extends Event>) Class.forName("de.Keyle.MyPet.api.event.MyPetExpEvent", true, mp.getClass().getClassLoader());
            Method player = cls.getMethod("getPlayer");
            Method get = cls.getMethod("getExp");
            Method set = cls.getMethod("setExp", double.class);
            Bukkit.getPluginManager().registerEvent(cls, this, EventPriority.HIGH, (l, e) -> {
                if (!cls.isInstance(e)) {
                    return;
                }
                try {
                    if (player.invoke(e) instanceof Player p && p.hasPermission(EXP_PERM)) {
                        double v = ((Number) get.invoke(e)).doubleValue();
                        if (v > 0) {
                            set.invoke(e, v * (1 + EXP_BONUS));
                        }
                    }
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    plugin.getLogger().warning("Đặc quyền kinh nghiệm pet lỗi: " + ex);
                }
            }, plugin, true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Không gắn được đặc quyền kinh nghiệm pet (MyPetExpEvent): " + e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPetDeath(EntityDeathEvent e) {
        Object pet = MyPetBridge.of(e.getEntity());
        if (pet == null) {
            return;
        }
        Player owner = owner(pet);
        if (owner == null || !owner.hasPermission(RESPAWN_PERM)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                Method get = pet.getClass().getMethod("getRespawnTime");
                Method set = pet.getClass().getMethod("setRespawnTime", int.class);
                get.setAccessible(true);
                set.setAccessible(true);
                int t = ((Number) get.invoke(pet)).intValue();
                if (t <= 1) {
                    return;
                }
                int now = Math.max(1, (int) Math.ceil(t * RESPAWN_FACTOR));
                set.invoke(pet, now);
                if (owner.isOnline()) {
                    owner.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(
                            "&b✦ Đặc quyền Thăng Cấp: &fpet hồi sinh sau &e" + now + " giây &7(thay vì " + t + " giây)."));
                }
            } catch (ReflectiveOperationException | RuntimeException ex) {
                plugin.getLogger().warning("Đặc quyền hồi sinh pet lỗi: " + ex);
            }
        }, 2L);
    }

    private static Player owner(Object pet) {
        try {
            Method m = pet.getClass().getMethod("getOwner");
            m.setAccessible(true);
            Object o = m.invoke(pet);
            Method pm = o.getClass().getMethod("getPlayer");
            pm.setAccessible(true);
            return pm.invoke(o) instanceof Player p ? p : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
