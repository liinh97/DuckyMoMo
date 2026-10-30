package vn.duckymomo.core.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Menu thông tin dùng chung (22/10, người dùng: "nhiều chỗ hướng dẫn qua chat rất khó nhìn -> chỉnh thành menu như ảnh, đẹp bắt mắt";
 * "tooltip nhìn phải thoáng, gom tính năng theo danh mục, nhiều thì sang trang, không để trống nhiều ô").
 * Thay cho các lệnh in cả chục dòng ra chat (/rank, /dungeon, /sachcam…): mỗi ý là một món, rê chuột đọc tooltip kiểu Tip.
 *  - Bố cục tự co: hàng trên (ô giữa = món đầu trang, tóm tắt) + các hàng nội dung + hàng dưới (Đóng giữa, Quay lại trái,
 *    nút phụ đối xứng hai bên). Số hàng theo số món (3..6), không để thừa hàng trống.
 *  - Nhóm: row() bắt đầu hàng mới = một danh mục; mỗi hàng căn giữa, ít món thì giãn cách 1 ô.
 *  - Nhiều hơn 4 hàng nội dung thì sang trang (◀ ▶ ở hàng dưới).
 *  - Tooltip thoáng: tự thêm dòng trống sau tiêu đề, vạch ngăn của Tip đổi thành dòng trống.
 *  - Bấm món: chạy việc gắn với món (mở menu con, chạy lệnh như người chơi gõ, điền sẵn lệnh). Không lấy / bỏ đồ được.
 *
 *   InfoMenu.of("&6Rank ủng hộ").accent(Material.ORANGE_STAINED_GLASS_PANE).top(tipItem)
 *       .add(tip1, p -> …).row().add(tip2, "vang").bar(nút, việc).back(p -> …).open(p);
 */
final class InfoMenu {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('&').hexColors().build();   // 30/10: tiền tố rank ủng hộ màu hex &#RRGGBB
    private static final int PER_ROW = 7;
    private static final int MAX_ROWS = 4;   // hàng nội dung mỗi trang

    private record Entry(ItemStack item, Consumer<Player> action) { }

    private final String titleText;
    private Material frame = Material.CYAN_STAINED_GLASS_PANE;   // 30/10: viền màu như /menu (accent() đổi theo nhóm)
    private ItemStack top;
    /** Các hàng (danh mục), mỗi hàng tối đa 7 món; hàng dài hơn tự tách. */
    private final List<List<Entry>> groups = new ArrayList<>();
    private final List<Entry> bar = new ArrayList<>();
    private Consumer<Player> back;

    private InfoMenu(String title) {
        this.titleText = title;
        groups.add(new ArrayList<>());
    }

    static InfoMenu of(String title) {
        return new InfoMenu(title);
    }

    /** Màu khung (kính màu). */
    InfoMenu accent(Material pane) {
        this.frame = pane;
        return this;
    }

    /** Món đầu trang (ô giữa hàng trên): tóm tắt / người chơi đang ở đâu. */
    InfoMenu top(ItemStack it) {
        this.top = airy(it);
        return this;
    }

    /** Thêm món vào hàng (danh mục) hiện tại (null = không bấm được). */
    InfoMenu add(ItemStack it, Consumer<Player> onClick) {
        groups.get(groups.size() - 1).add(new Entry(airy(it), onClick));
        return this;
    }

    /** Thêm món chỉ để xem (không bấm). */
    InfoMenu add(ItemStack it) {
        return add(it, (Consumer<Player>) null);
    }

    /** Thêm món, bấm = chạy lệnh như người chơi gõ (không dấu /). */
    InfoMenu add(ItemStack it, String command) {
        return add(it, command == null ? null : run(command));
    }

    /** Bắt đầu hàng mới (danh mục mới). */
    InfoMenu row() {
        if (!groups.get(groups.size() - 1).isEmpty()) {
            groups.add(new ArrayList<>());
        }
        return this;
    }

    /** Nút phụ ở hàng dưới (tối đa 4, xếp đối xứng quanh nút Đóng). */
    InfoMenu bar(ItemStack it, Consumer<Player> onClick) {
        if (bar.size() < 4) {
            bar.add(new Entry(airy(it), onClick));
        }
        return this;
    }

    /** Nút Quay lại (góc trái hàng dưới). */
    InfoMenu back(Consumer<Player> onBack) {
        this.back = onBack;
        return this;
    }

    /** Việc khi bấm: đóng menu, chạy lệnh như người chơi gõ. */
    static Consumer<Player> run(String command) {
        return p -> {
            p.closeInventory();
            p.performCommand(command);
        };
    }

    /** Việc khi bấm cho lệnh cần gõ thêm: đóng menu, hiện dòng [bấm để điền lệnh] trong chat. */
    static Consumer<Player> suggest(String command, String hint) {
        return p -> {
            p.closeInventory();
            p.sendMessage(LEGACY.deserialize("&7" + hint + ": ").append(LEGACY.deserialize("&e[" + command + "…]")
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(command + " "))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(LEGACY.deserialize("&7Bấm để điền sẵn lệnh, gõ tiếp rồi Enter")))));
        };
    }

    void open(Player p) {
        open(p, 0);
    }

    /** Các hàng thật (hàng dài hơn 7 món tách ra), bỏ hàng rỗng. */
    private List<List<Entry>> rows() {
        List<List<Entry>> out = new ArrayList<>();
        for (List<Entry> g : groups) {
            for (int i = 0; i < g.size(); i += PER_ROW) {
                out.add(g.subList(i, Math.min(g.size(), i + PER_ROW)));
            }
        }
        return out;
    }

    private void open(Player p, int page) {
        List<List<Entry>> all = rows();
        int pages = Math.max(1, (all.size() + MAX_ROWS - 1) / MAX_ROWS);
        int pg = Math.max(0, Math.min(page, pages - 1));
        List<List<Entry>> mine = all.subList(Math.min(all.size(), pg * MAX_ROWS), Math.min(all.size(), (pg + 1) * MAX_ROWS));
        int rows = Math.max(3, mine.size() + 2);
        Holder h = new Holder();
        Component title = LEGACY.deserialize(MenuFrame.darkTitle(titleText) + (pages > 1 ? " &8(" + (pg + 1) + "/" + pages + ")" : "")).decoration(TextDecoration.ITALIC, false);
        Inventory inv = Bukkit.createInventory(h, rows * 9, title);
        h.inv = inv;
        ItemStack pane = hidden(new ItemStack(frame));
        ItemStack dark = hidden(new ItemStack(Material.GRAY_STAINED_GLASS_PANE));   // 30/10: nền xám sáng thay đen
        for (int i = 0; i < rows * 9; i++) {
            boolean edge = i < 9 || i >= (rows - 1) * 9 || i % 9 == 0 || i % 9 == 8;
            inv.setItem(i, edge ? pane : dark);
        }
        if (top != null) {
            inv.setItem(4, top);
        }
        for (int r = 0; r < mine.size(); r++) {
            List<Entry> row = mine.get(r);
            int[] cols = cols(row.size());
            for (int k = 0; k < row.size(); k++) {
                int slot = (r + 1) * 9 + cols[k];
                inv.setItem(slot, row.get(k).item());
                if (row.get(k).action() != null) {
                    h.actions.put(slot, row.get(k).action());
                }
            }
        }
        int base = (rows - 1) * 9;
        inv.setItem(base + 4, Tip.of("&c✕ &fĐóng").apply(new ItemStack(Material.BARRIER)));
        h.actions.put(base + 4, Player::closeInventory);
        if (back != null) {
            inv.setItem(base, Tip.of("&7← &fQuay lại").apply(new ItemStack(Material.ARROW)));
            h.actions.put(base, back);
        } else if (MenuFrame.BACK_KEY != null) {
            inv.setItem(base, MenuFrame.backItem());   // 30/10: về /menu (PlayerMenu bắt khoá ve_menu)
        }
        if (pg > 0) {
            inv.setItem(base + 3, Tip.of("&e◀ &fTrang trước").sub((pg) + "/" + pages).apply(new ItemStack(Material.SPECTRAL_ARROW)));
            h.actions.put(base + 3, pl -> open(pl, pg - 1));
        }
        if (pg < pages - 1) {
            inv.setItem(base + 5, Tip.of("&e▶ &fTrang sau").sub((pg + 2) + "/" + pages).apply(new ItemStack(Material.SPECTRAL_ARROW)));
            h.actions.put(base + 5, pl -> open(pl, pg + 1));
        }
        int[] barSlots = switch (bar.size()) {
            case 1 -> new int[]{6};
            case 2 -> new int[]{2, 6};
            case 3 -> new int[]{2, 6, 7};
            default -> new int[]{1, 2, 6, 7};
        };
        for (int i = 0; i < bar.size(); i++) {
            inv.setItem(base + barSlots[i], bar.get(i).item());
            if (bar.get(i).action() != null) {
                h.actions.put(base + barSlots[i], bar.get(i).action());
            }
        }
        p.openInventory(inv);
    }

    /** Cột (1..7) cho k món trong một hàng: ít món thì giãn cách 1 ô, căn giữa. */
    private static int[] cols(int k) {
        int[] c = new int[k];
        if (k <= 4) {
            int start = 1 + (7 - (2 * k - 1)) / 2;
            for (int i = 0; i < k; i++) {
                c[i] = start + 2 * i;
            }
        } else {
            int start = 1 + (7 - k) / 2;
            for (int i = 0; i < k; i++) {
                c[i] = start + i;
            }
        }
        return c;
    }

    private static ItemStack hidden(ItemStack it) {
        it.editMeta(m -> m.setHideTooltip(true));
        return it;
    }

    /** Tooltip thoáng: thêm dòng trống sau tiêu đề, vạch ngăn (gạch ngang toàn khoảng trắng) đổi thành dòng trống, bỏ dòng trống đôi. */
    private static ItemStack airy(ItemStack it) {
        if (it == null || !it.hasItemMeta() || it.getItemMeta().lore() == null) {
            return it;
        }
        List<Component> out = new ArrayList<>();
        out.add(Component.empty());
        for (Component l : it.getItemMeta().lore()) {
            boolean sep = PlainTextComponentSerializer.plainText().serialize(l).isBlank();
            if (sep) {
                if (!out.isEmpty() && !PlainTextComponentSerializer.plainText().serialize(out.get(out.size() - 1)).isEmpty()) {
                    out.add(Component.empty());
                }
            } else {
                out.add(l);
            }
        }
        while (out.size() > 1 && PlainTextComponentSerializer.plainText().serialize(out.get(out.size() - 1)).isBlank()) {
            out.remove(out.size() - 1);
        }
        it.editMeta(m -> m.lore(out));
        return it;
    }

    static final class Holder implements InventoryHolder {
        private Inventory inv;
        private final Map<Integer, Consumer<Player>> actions = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    /** Một listener cho mọi InfoMenu (đăng ký 1 lần ở DuckyMoMoCore). */
    static final class Events implements Listener {
        private final JavaPlugin plugin;

        Events(JavaPlugin plugin) {
            this.plugin = plugin;
        }

        @EventHandler
        public void onClick(InventoryClickEvent e) {
            if (!(e.getInventory().getHolder(false) instanceof Holder h)) {
                return;
            }
            e.setCancelled(true);
            if (e.getClickedInventory() != e.getInventory() || e.getClick() == ClickType.DOUBLE_CLICK || !(e.getWhoClicked() instanceof Player p)) {
                return;
            }
            Consumer<Player> a = h.actions.get(e.getRawSlot());
            if (a != null) {
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                Bukkit.getScheduler().runTask(plugin, () -> a.accept(p));   // mở menu khác ngay trong sự kiện bấm dễ lỗi
            }
        }

        @EventHandler
        public void onDrag(InventoryDragEvent e) {
            if (e.getInventory().getHolder(false) instanceof Holder) {
                e.setCancelled(true);
            }
        }
    }
}
