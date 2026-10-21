package vn.duckymomo.core.paper;

import java.util.Locale;
import java.util.Set;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Tên pet không mang mã màu (21/10: MyPet bỏ mã § TRƯỚC rồi mới đổi & thành màu, nên quyền MyPet.command.name.color không chặn được
 * /petname &k&lHack -> tên nhấp nháy đậm). Bỏ mọi mã &x / &#rrggbb khỏi lệnh trước khi MyPet nhận; staff (duckymomo.admin) giữ màu.
 */
final class PetNameGuard implements Listener {

    private static final Set<String> CMDS = Set.of("/petname", "/mypet:petname");

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage();
        int sp = msg.indexOf(' ');
        if (sp < 0 || !CMDS.contains(msg.substring(0, sp).toLowerCase(Locale.ROOT)) || e.getPlayer().hasPermission("duckymomo.admin")) {
            return;
        }
        String name = msg.substring(sp + 1).replaceAll("(?i)&#[0-9a-f]{6}|&[0-9a-fk-orx]", "").trim();
        if (name.isEmpty()) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(net.kyori.adventure.text.Component.text("Tên pet không được chỉ toàn mã màu.",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }
        e.setMessage(msg.substring(0, sp + 1) + name);
    }
}
