package vn.duckymomo.core.paper;

import java.util.Locale;
import java.util.Set;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Chặn /pay cho chính mình (21/10: admin gõ /pay Linh 10000 bằng tài khoản Linh, Essentials trừ rồi cộng lại, số dư không đổi -> tưởng lỗi).
 * Báo rõ và gợi ý lệnh admin cộng tiền.
 */
final class PaySelfGuard implements Listener {

    private static final Set<String> PAY = Set.of("/pay", "/epay", "/essentials:pay", "/essentials:epay");

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String[] a = e.getMessage().trim().split("\\s+");
        if (a.length < 2 || !PAY.contains(a[0].toLowerCase(Locale.ROOT))) {
            return;
        }
        Player p = e.getPlayer();
        String target = a[1].startsWith(".") ? a[1].substring(1) : a[1];
        String me = p.getName().startsWith(".") ? p.getName().substring(1) : p.getName();
        if (target.equalsIgnoreCase(me)) {
            e.setCancelled(true);
            p.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(
                    "&cKhông chuyển tiền cho chính mình được (tiền trừ rồi cộng lại, số dư không đổi)."
                            + (p.hasPermission("essentials.eco") ? " &7Admin cộng tiền: &f/eco give " + p.getName() + " <số>" : "")));
        }
    }
}
