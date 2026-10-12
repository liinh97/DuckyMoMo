package vn.duckymomo.core.paper;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Chat dạng "[Admin] [VIP] Linh: nội dung", giống format EssentialsX Chat ở banghoi/practice
 * ('{PREFIX}{DISPLAYNAME}&7: &f{MESSAGE}'). Prefix lấy từ LuckPerms, đã ghép role + rank donate theo
 * meta-formatting trong LuckPerms/config.yml.
 */
final class ChatFormat implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        User user = LuckPermsProvider.get().getUserManager().getUser(e.getPlayer().getUniqueId());
        String prefix = user == null ? null : user.getCachedData().getMetaData().getPrefix();
        Component pre = prefix == null ? Component.empty() : LEGACY.deserialize(prefix);
        e.renderer((source, displayName, message, viewer) -> Component.empty()
                .append(pre)
                .append(displayName)
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(message.colorIfAbsent(NamedTextColor.WHITE)));
    }
}
