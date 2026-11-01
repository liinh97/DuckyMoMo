package vn.duckymomo.skin;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Màn hình bị đá chung một kiểu (1/11, chủ server: khung tên network, dòng đỏ, lý do, link liên hệ). Mọi lần server con đá người chơi ra hẳn
 * (AuthMe, whitelist, server tắt / khởi động lại, Grim, /kick ở sảnh) đều qua KickedFromServerEvent nên bọc một chỗ, khỏi sửa từng plugin.
 * Chỉ bọc DisconnectPlayer; được chuyển về sảnh (RedirectPlayer) hay ở lại server cũ (Notify) thì lý do hiện trong chat, giữ nguyên một dòng.
 * Màn hình do chính proxy sinh ra (Velocity lang, Sonar) không qua đây: sửa trong lang/messages.properties và plugins/sonar/messages.yml.
 */
final class KickScreen {

    private static final Component HEAD = MiniMessage.miniMessage().deserialize(
            "<gradient:#f7b733:#fc4a1a>DUCKYMOMO NETWORK</gradient><newline><dark_gray>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬</dark_gray><newline><newline>"
                    + "<red>Bạn đã bị ngắt kết nối</red><newline>");
    private static final Component FOOT = MiniMessage.miniMessage().deserialize(
            "<newline><newline><gray>Cần hỗ trợ: </gray><aqua>" + System.getenv().getOrDefault("KICK_LINK", "discord.gg/Hg9cDWApF") + "</aqua>");

    @Subscribe(order = PostOrder.LAST)
    public void onKick(KickedFromServerEvent e) {
        if (e.getResult() instanceof KickedFromServerEvent.DisconnectPlayer d) {
            Component reason = Component.text().color(NamedTextColor.WHITE).append(d.getReasonComponent()).build();
            e.setResult(KickedFromServerEvent.DisconnectPlayer.create(HEAD.append(reason).append(FOOT)));
        }
    }
}
