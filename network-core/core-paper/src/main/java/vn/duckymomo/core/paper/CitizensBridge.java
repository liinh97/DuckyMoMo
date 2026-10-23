package vn.duckymomo.core.paper;

import java.lang.reflect.Method;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.Trait;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Mọi chỗ chạm vào API Citizens nằm ở đây (hàm tĩnh). Chỉ gọi khi Citizens đang bật (Bang Hội Chiến); sảnh/practice không có
 * Citizens nên không lớp nào khác được tham chiếu kiểu của Citizens (bài học PlaceholderAPI: NoClassDefFoundError tắt cả plugin).
 * LookClose và SkinTrait nằm trong jar chính của Citizens (không có trong citizensapi) nên gọi bằng reflection.
 */
final class CitizensBridge {

    private CitizensBridge() {
    }

    /** Tạo NPC hình người tên name ở l, quay mặt theo người chơi, skin theo tên người chơi (trống = mặc định). Trả ID. */
    static int create(String name, Location l, String skin) {
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, name);
        trait(npc, "net.citizensnpcs.trait.LookClose", "lookClose", boolean.class, true);
        if (skin != null && !skin.isBlank() && !skin.startsWith("http")) {
            trait(npc, "net.citizensnpcs.trait.SkinTrait", "setSkinName", String.class, skin);
        }
        npc.spawn(l);
        if (skin != null && skin.startsWith("http")) {
            urlSkin(npc.getId(), skin);
        }
        return npc.getId();
    }

    /** Như create nhưng ẩn bảng tên của Citizens (Thợ Rèn có dòng chữ nổi riêng). */
    static int createNoName(String name, Location l, String skin) {
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, name);
        npc.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, false);
        trait(npc, "net.citizensnpcs.trait.LookClose", "lookClose", boolean.class, true);
        if (skin != null && !skin.isBlank() && !skin.startsWith("http")) {
            trait(npc, "net.citizensnpcs.trait.SkinTrait", "setSkinName", String.class, skin);
        }
        npc.spawn(l);
        if (skin != null && skin.startsWith("http")) {
            urlSkin(npc.getId(), skin);
        }
        return npc.getId();
    }

    /**
     * Skin từ link ảnh PNG (23/10, vd https://cdn.minecraft.how/skin/<số>/image.png): Citizens gửi MineSkin ký rồi lưu luôn vào NPC, nên chỉ
     * gọi lúc tạo NPC (không gọi lại mỗi lần bật server, tránh MineSkin chặn vì gọi nhiều).
     */
    static void urlSkin(int id, String url) {
        org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), "npc skin --url " + url + " --id " + id);
    }

    /** Đổi skin NPC theo tên người chơi (trống = bỏ qua; link ảnh: bỏ qua vì đã lưu trong NPC lúc tạo). */
    static void setSkin(int id, String skin) {
        NPC npc = CitizensAPI.getNPCRegistry().getById(id);
        if (npc != null && skin != null && !skin.isBlank() && !skin.startsWith("http")) {
            trait(npc, "net.citizensnpcs.trait.SkinTrait", "setSkinName", String.class, skin);
        }
    }

    /** ID NPC Citizens của thực thể (-1 nếu không phải NPC). */
    static int idOf(org.bukkit.entity.Entity e) {
        NPC npc = CitizensAPI.getNPCRegistry().getNPC(e);
        return npc == null ? -1 : npc.getId();
    }

    static boolean exists(int id) {
        return id >= 0 && CitizensAPI.getNPCRegistry().getById(id) != null;
    }

    /** Dời NPC tới l (đang hiện thì dịch chuyển, đang ẩn vì chunk chưa nạp thì đặt lại chỗ). */
    static void move(int id, Location l) {
        NPC npc = CitizensAPI.getNPCRegistry().getById(id);
        if (npc == null) {
            return;
        }
        if (npc.isSpawned()) {
            npc.teleport(l, PlayerTeleportEvent.TeleportCause.PLUGIN);
        } else {
            npc.spawn(l);
        }
    }

    static Location where(int id) {
        NPC npc = CitizensAPI.getNPCRegistry().getById(id);
        return npc == null ? null : npc.getStoredLocation();
    }

    static void remove(int id) {
        NPC npc = CitizensAPI.getNPCRegistry().getById(id);
        if (npc != null) {
            npc.destroy();
        }
    }

    @SuppressWarnings("unchecked")
    private static void trait(NPC npc, String cls, String method, Class<?> arg, Object value) {
        try {
            Class<? extends Trait> c = (Class<? extends Trait>) Class.forName(cls, true, CitizensAPI.getPlugin().getClass().getClassLoader());
            Trait t = npc.getOrAddTrait(c);
            Method m = c.getMethod(method, arg);
            m.invoke(t, value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // bản Citizens khác tên lớp/hàm: bỏ qua, NPC vẫn dùng được
        }
    }
}
