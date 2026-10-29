package vn.duckymomo.core.paper;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bot đứng trong phòng ẩn thì không ai thấy (29/10, chủ server: "đưa hết bot về … member không nhìn thấy được"): chặn gói hiện thực thể
 * (SPAWN_ENTITY) của bot đang ẩn. Tên vẫn ở danh sách TAB (gói PLAYER_INFO không đụng), chat vẫn như thường.
 * Phòng ẩn đặt xa mọi warp / spawn hơn tầm theo dõi người chơi (128 khối) nên bot dịch chuyển ra / vào phòng thì client tự tạo / xoá như
 * người dịch chuyển bình thường. Chỉ nạp khi có packetevents.
 */
final class BotHidePackets extends PacketListenerAbstract {
    /** entity id của bot đang ẩn (BotView cập nhật mỗi giây + ngay khi bot dịch chuyển) */
    static final Set<Integer> HIDDEN = ConcurrentHashMap.newKeySet();
    private static boolean registered;

    private BotHidePackets() {
        super(PacketListenerPriority.HIGH);
    }

    static synchronized void register() {
        if (!registered) {
            PacketEvents.getAPI().getEventManager().registerListener(new BotHidePackets());
            registered = true;
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent e) {
        if (e.getPacketType() != PacketType.Play.Server.SPAWN_ENTITY || HIDDEN.isEmpty()) {
            return;
        }
        if (HIDDEN.contains(new WrapperPlayServerSpawnEntity(e).getEntityId())) {
            e.setCancelled(true);
        }
    }
}
