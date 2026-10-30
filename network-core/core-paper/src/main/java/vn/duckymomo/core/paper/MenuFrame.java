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

    /** Tô viền (hàng đầu, hàng cuối, cột 0 và 8) bằng border, bên trong GRAY, chỉ những ô đang trống. */
    static void paint(Inventory inv, Material border) {
        int size = inv.getSize(), rows = size / 9;
        ItemStack edge = pane(border), in = pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < size; i++) {
            ItemStack cur = inv.getItem(i);
            if (cur != null && !cur.getType().isAir()) {
                continue;
            }
            int r = i / 9, c = i % 9;
            boolean isEdge = rows <= 1 || r == 0 || r == rows - 1 || c == 0 || c == 8;
            inv.setItem(i, isEdge ? edge : in);
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
