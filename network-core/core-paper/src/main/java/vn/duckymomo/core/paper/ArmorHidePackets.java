package vn.duckymomo.core.paper;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ẩn giáp bằng cách sửa gói tin ngay lúc gửi (PacketEvents), 28/10: trước đó gửi đè giáp giả mỗi giây nên server gửi giáp thật
 * (mòn, đổi đồ, đồng bộ túi đồ) là giáp chớp hiện ("lúc ẩn lúc hiện"). Giờ mọi gói chứa giáp của người đang ẩn đều bị đổi thành trống:
 *  - người khác: gói trang bị (ENTITY_EQUIPMENT) của người đó;
 *  - chính mình: ô giáp 5..8 của túi đồ (SET_SLOT / WINDOW_ITEMS cửa sổ 0, SET_PLAYER_INVENTORY ô 36..39) và gói trang bị gửi cho mình.
 * Chỉ nạp lớp này khi plugin packetevents đang bật (không có thì ArmorHide dùng cách cũ).
 */
final class ArmorHidePackets extends PacketListenerAbstract {
    /** entity id của người đang ẩn với người khác */
    static final Set<Integer> HIDDEN = ConcurrentHashMap.newKeySet();
    /** entity id của người đang ẩn với cả chính mình (không tính creative/spectator: creative túi đồ do máy người chơi quyết, ô trông trống mà bấm là mất giáp thật) */
    static final Set<Integer> SELF = ConcurrentHashMap.newKeySet();
    /** 30/10 chủ server: món giáp sắp hỏng thì KHÔNG ẩn (để biết mà sửa). entity id -> bit ô giáp được hiện (1 mũ, 2 áo, 4 quần, 8 giày). */
    static final java.util.Map<Integer, Integer> SHOW = new ConcurrentHashMap<>();

    private static boolean shown(int id, int bit) {
        return (SHOW.getOrDefault(id, 0) & bit) != 0;
    }

    private static int bit(EquipmentSlot s) {
        return s == EquipmentSlot.HELMET ? 1 : s == EquipmentSlot.CHEST_PLATE ? 2 : s == EquipmentSlot.LEGGINGS ? 4 : 8;
    }

    private ArmorHidePackets() {
        super(PacketListenerPriority.HIGH);
    }

    private static boolean registered;

    /** Đăng ký một lần (dmm reload tạo lại ArmorHide nhưng PacketEvents giữ listener cũ: đăng ký lại là lọc trùng). */
    static synchronized void register() {
        if (!registered) {
            PacketEvents.getAPI().getEventManager().registerListener(new ArmorHidePackets());
            registered = true;
        }
    }

    private static boolean armor(EquipmentSlot s) {
        return s == EquipmentSlot.HELMET || s == EquipmentSlot.CHEST_PLATE || s == EquipmentSlot.LEGGINGS || s == EquipmentSlot.BOOTS;
    }

    @Override
    public void onPacketSend(PacketSendEvent e) {
        var type = e.getPacketType();
        if (type != PacketType.Play.Server.ENTITY_EQUIPMENT && type != PacketType.Play.Server.SET_SLOT
                && type != PacketType.Play.Server.WINDOW_ITEMS && type != PacketType.Play.Server.SET_PLAYER_INVENTORY) {
            return;
        }
        Object o = e.getPlayer();
        if (!(o instanceof Player viewer)) {
            return;
        }
        int me = viewer.getEntityId();
        if (type == PacketType.Play.Server.ENTITY_EQUIPMENT) {
            WrapperPlayServerEntityEquipment w = new WrapperPlayServerEntityEquipment(e);
            int id = w.getEntityId();
            if (id == me ? !SELF.contains(id) : !HIDDEN.contains(id)) {
                return;
            }
            List<Equipment> l = new ArrayList<>(w.getEquipment());
            boolean changed = false;
            for (int i = 0; i < l.size(); i++) {
                Equipment q = l.get(i);
                if (armor(q.getSlot()) && !q.getItem().isEmpty() && !shown(id, bit(q.getSlot()))) {
                    l.set(i, new Equipment(q.getSlot(), ItemStack.EMPTY));
                    changed = true;
                }
            }
            if (changed) {
                w.setEquipment(l);
                e.markForReEncode(true);
            }
            return;
        }
        if (!SELF.contains(me)) {
            return;
        }
        if (type == PacketType.Play.Server.SET_SLOT) {
            WrapperPlayServerSetSlot w = new WrapperPlayServerSetSlot(e);
            if (w.getWindowId() == 0 && w.getSlot() >= 5 && w.getSlot() <= 8 && !w.getItem().isEmpty() && !shown(me, 1 << (w.getSlot() - 5))) {
                w.setItem(ItemStack.EMPTY);
                e.markForReEncode(true);
            }
        } else if (type == PacketType.Play.Server.WINDOW_ITEMS) {
            WrapperPlayServerWindowItems w = new WrapperPlayServerWindowItems(e);
            List<ItemStack> items = w.getItems();
            if (w.getWindowId() == 0 && items.size() > 8) {
                List<ItemStack> l = new ArrayList<>(items);
                for (int i = 5; i <= 8; i++) {
                    if (!shown(me, 1 << (i - 5))) {   // ô 5 mũ, 6 áo, 7 quần, 8 giày
                        l.set(i, ItemStack.EMPTY);
                    }
                }
                w.setItems(l);
                e.markForReEncode(true);
            }
        } else {
            WrapperPlayServerSetPlayerInventory w = new WrapperPlayServerSetPlayerInventory(e);
            if (w.getSlot() >= 36 && w.getSlot() <= 39 && !w.getStack().isEmpty() && !shown(me, 1 << (39 - w.getSlot()))) {   // 36 giày … 39 mũ
                w.setStack(ItemStack.EMPTY);
                e.markForReEncode(true);
            }
        }
    }
}
