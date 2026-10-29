package vn.duckymomo.core.paper;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Đặc quyền riêng Công Tước (29/10, chủ server chọn):
 * 1. /bay — bay trong đất thị trấn của MÌNH (quyền duckymomo.bay). Chỉ khi: đứng trong ô đất thị trấn mình, không vừa đánh người
 *    (combat-seconds), không trong vùng vây đang có phiên đánh, chế độ sinh tồn / phiêu lưu. Hết điều kiện thì tự tắt bay và lần rơi đó
 *    không mất máu. Chỉ tắt bay mà module này bật (staff /fly của Essentials không bị đụng).
 * 2. /fix (Essentials repair, quyền essentials.repair, hồi chiêu ở Essentials command-cooldowns): KHÔNG sửa đồ có mã Thư viện đồ
 *    (set Thợ Rèn, đồ đặc biệt: độ bền là một phần thiết kế), không cho /fix all nếu thiếu essentials.repair.all.
 * Config: town-fly {enabled, combat-seconds}.
 */
final class TownFly implements Listener {
    static final String PERM = "duckymomo.bay";
    private static final Set<String> FIX_CMDS = Set.of("fix", "repair", "efix", "erepair");

    private final long combatMillis;
    private final Set<UUID> wants = new HashSet<>();      // đã bật /bay
    private final Set<UUID> managed = new HashSet<>();    // module này đang cho bay
    private final Set<UUID> safeFall = new HashSet<>();   // vừa bị tắt bay: lần rơi tới không mất máu
    private final Map<UUID, Long> lastFight = new HashMap<>();

    TownFly(JavaPlugin plugin, org.bukkit.configuration.ConfigurationSection cfg) {
        this.combatMillis = Math.max(0, cfg == null ? 15 : cfg.getInt("combat-seconds", 15)) * 1000L;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    private static boolean ownTown(Player p) {
        try {
            Town here = TownyAPI.getInstance().getTown(p.getLocation());
            Resident r = TownyAPI.getInstance().getResident(p);
            return here != null && r != null && r.hasTown() && here.equals(r.getTownOrNull());
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** null = được bay; khác null = lý do không được. */
    private String why(Player p) {
        if (!p.hasPermission(PERM)) {
            return "Bay trong thị trấn là đặc quyền của Công Tước.";
        }
        GameMode g = p.getGameMode();
        if (g != GameMode.SURVIVAL && g != GameMode.ADVENTURE) {
            return "Chỉ dùng ở chế độ sinh tồn.";
        }
        if (System.currentTimeMillis() - lastFight.getOrDefault(p.getUniqueId(), 0L) < combatMillis) {
            return "Đang giao tranh, chờ " + combatMillis / 1000 + " giây sau trận.";
        }
        try {
            if (SiegeWarHook.battleActive() && SiegeWarHook.inActiveSiegeZone(p.getLocation())) {
                return "Không bay trong vùng vây khi đang có phiên đánh.";
            }
        } catch (RuntimeException | NoClassDefFoundError ignored) {
            // không có SiegeWar
        }
        if (!ownTown(p)) {
            return "Chỉ bay được trong đất thị trấn của bạn.";
        }
        return null;
    }

    private void stop(Player p, String reason) {
        UUID u = p.getUniqueId();
        if (!managed.remove(u)) {
            return;
        }
        if (p.isFlying()) {
            safeFall.add(u);
        }
        p.setFlying(false);
        p.setAllowFlight(false);
        if (reason != null) {
            p.sendActionBar(Tip.c("&e✈ Hết bay: &7" + reason));
        }
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID u = p.getUniqueId();
            if (safeFall.contains(u) && p.isOnGround()) {
                safeFall.remove(u);
            }
            if (!wants.contains(u)) {
                continue;
            }
            String w = why(p);
            if (w == null) {
                if (!managed.contains(u)) {
                    managed.add(u);
                    p.setAllowFlight(true);
                    p.sendActionBar(Tip.c("&a✈ Được bay trong thị trấn &7(nhấn nhảy 2 lần)"));
                }
            } else {
                stop(p, w);
            }
        }
    }

    boolean command(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Chỉ người chơi.");
            return true;
        }
        if (!p.hasPermission(PERM)) {
            p.sendMessage(Tip.c("&cBay trong thị trấn là đặc quyền của &6Công Tước&c. &7Xem /rank."));
            return true;
        }
        UUID u = p.getUniqueId();
        if (wants.remove(u)) {
            stop(p, null);
            p.sendMessage(Tip.c("&7Đã tắt bay trong thị trấn."));
            return true;
        }
        wants.add(u);
        String w = why(p);
        p.sendMessage(Tip.c("&aĐã bật bay trong thị trấn. " + (w == null ? "&7Nhấn nhảy 2 lần để bay." : "&e" + w + " &7(tự bay khi đủ điều kiện)")));
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFight(EntityDamageByEntityEvent e) {
        Entity d = e.getDamager();
        if (d instanceof Projectile pr && pr.getShooter() instanceof Entity sh) {
            d = sh;
        }
        if (d instanceof Player a && e.getEntity() instanceof Player b && !a.equals(b)) {
            long now = System.currentTimeMillis();
            lastFight.put(a.getUniqueId(), now);
            lastFight.put(b.getUniqueId(), now);
            stop(a, "đang giao tranh");
            stop(b, "đang giao tranh");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && e.getEntity() instanceof Player p && safeFall.remove(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        stop(p, null);
        wants.remove(p.getUniqueId());
        safeFall.remove(p.getUniqueId());
        lastFight.remove(p.getUniqueId());
    }

    /** /fix: không sửa đồ có mã Thư viện đồ (set Thợ Rèn, đồ đặc biệt), /fix all cần essentials.repair.all. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFix(PlayerCommandPreprocessEvent e) {
        String[] parts = e.getMessage().substring(1).split(" ");
        String root = parts[0].toLowerCase(Locale.ROOT);
        root = root.substring(root.indexOf(':') + 1);
        if (!FIX_CMDS.contains(root)) {
            return;
        }
        Player p = e.getPlayer();
        if (parts.length > 1 && parts[1].equalsIgnoreCase("all") && !p.hasPermission("essentials.repair.all")) {
            e.setCancelled(true);
            p.sendMessage(Tip.c("&c/fix chỉ sửa món đang cầm: &f/fix"));
            return;
        }
        ItemStack it = p.getInventory().getItemInMainHand();
        if (!p.hasPermission("essentials.repair.all") && ItemLibrary.idOf(it) != null) {
            e.setCancelled(true);
            p.sendMessage(Tip.c("&cĐồ đặc biệt (set Thợ Rèn, đồ Thư viện) không sửa bằng /fix được. &7Ra Thợ Rèn hoặc dùng đe."));
        }
    }
}
