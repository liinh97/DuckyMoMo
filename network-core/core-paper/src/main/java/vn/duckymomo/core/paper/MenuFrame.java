package vn.duckymomo.core.paper;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Khung menu thống nhất (30/10, chủ server: "các menu con chưa sửa lại cho đẹp"): viền kính màu theo nhóm (như /menu), ô trống bên
 * trong kính xám sáng. Chỉ tô ô còn TRỐNG nên không đè nút / món của menu; kính ẩn tooltip, bấm vào không làm gì (các menu tra
 * slot / khoá PDC nên bỏ qua kính).
 * Màu nhóm: Kinh tế YELLOW, Hằng ngày ORANGE, Máy chủ PINK, Người chơi LIME, chiến đấu RED, sảnh LIGHT_BLUE.
 */
final class MenuFrame {
    private MenuFrame() {
    }

    static ItemStack pane(Material m) {
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> meta.setHideTooltip(true));
        return it;
    }

    /** Khoá PDC của nút "Về menu" (PlayerMenu bắt khi bấm ở bất kỳ menu nào); null = server không có /menu (sảnh…). */
    static org.bukkit.NamespacedKey BACK_KEY;

    /** Kính nền cũ của menu (kính xám, không tên / ẩn tooltip): coi như ô trống để tô lại khung. */
    private static boolean filler(ItemStack it) {
        if (it == null || it.getType().isAir()) {
            return true;
        }
        if (it.getType() != Material.GRAY_STAINED_GLASS_PANE && it.getType() != Material.LIGHT_GRAY_STAINED_GLASS_PANE
                && it.getType() != Material.BLACK_STAINED_GLASS_PANE) {
            return false;
        }
        var m = it.getItemMeta();
        return m == null || m.isHideTooltip() || !m.hasDisplayName()
                || net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(m.displayName()).isBlank();
    }

    static ItemStack backItem() {
        ItemStack it = Tip.of("&e⬅ &fVề menu").desc("Quay lại menu chính /menu").click("Bấm để quay lại").apply(new ItemStack(Material.NETHER_STAR));
        it.editMeta(m -> m.getPersistentDataContainer().set(BACK_KEY, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1));
        return it;
    }

    /** Tô viền (hàng đầu, hàng cuối, cột 0 và 8) bằng border, bên trong GRAY, chỉ ô trống hoặc kính nền cũ. Góc dưới trái trống
     *  thì đặt nút Về menu (30/10 chủ server: "vào gacha không có nút quay lại menu cấp trên"). */
    static void paint(Inventory inv, Material border) {
        int size = inv.getSize(), rows = size / 9;
        ItemStack edge = pane(border), in = pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < size; i++) {
            ItemStack cur = inv.getItem(i);
            if (!filler(cur)) {
                continue;
            }
            int r = i / 9, c = i % 9;
            boolean isEdge = rows <= 1 || r == 0 || r == rows - 1 || c == 0 || c == 8;
            inv.setItem(i, isEdge ? edge : in);
        }
        int corner = (rows - 1) * 9;
        if (BACK_KEY != null && rows >= 3 && inv.getItem(corner) != null && inv.getItem(corner).getType() == border) {
            inv.setItem(corner, backItem());
        }
    }

    /** Cột 1..7 cho k món một hàng: ít món thì cách 1 ô, căn giữa (giống InfoMenu). */
    static int[] cols(int k) {
        int[] c = new int[k];
        if (k <= 4) {
            int start = 1 + (7 - (2 * k - 1)) / 2;
            for (int i = 0; i < k; i++) {
                c[i] = start + 2 * i;
            }
        } else {
            int start = 1 + (7 - Math.min(k, 7)) / 2;
            for (int i = 0; i < k; i++) {
                c[i] = start + i;
            }
        }
        return c;
    }
}
