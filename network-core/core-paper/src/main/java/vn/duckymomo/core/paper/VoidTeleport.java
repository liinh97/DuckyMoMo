package vn.duckymomo.core.paper;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * World đảo bay (lobby): rơi thấp hơn belowY thì đưa về spawn của world, không chết vì hư không.
 * spawnOnJoin: mỗi lần vào đều đứng đúng giữa ô spawn (không vào lại chỗ thoát lần trước).
 */
final class VoidTeleport implements Listener {

    private final int belowY;
    private final boolean spawnOnJoin;

    VoidTeleport(int belowY, boolean spawnOnJoin) {
        this.belowY = belowY;
        this.spawnOnJoin = spawnOnJoin;
    }

    /** Giữa ô spawn của world, giữ hướng nhìn đã đặt bằng /setworldspawn. */
    static Location spawn(World w) {
        Location s = w.getSpawnLocation();
        return new Location(w, s.getBlockX() + 0.5, s.getBlockY(), s.getBlockZ() + 0.5, s.getYaw(), s.getPitch());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (spawnOnJoin) {
            Player p = e.getPlayer();
            p.teleport(spawn(p.getWorld()));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo().getY() < belowY && e.getFrom().getBlockY() != e.getTo().getBlockY()) {
            Player p = e.getPlayer();
            p.setFallDistance(0);
            p.teleport(spawn(p.getWorld()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onVoidDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            e.setCancelled(true);
            p.setFallDistance(0);
            p.teleport(spawn(p.getWorld()));
        }
    }
}
