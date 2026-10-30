package vn.duckymomo.core.paper;

import java.io.File;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Cẩm nang trong game: /huongdan [sach | chương] (alias hd, guide, camnang).
 * - /hd (21/10): MENU RƯƠNG các nhóm (huongdan.yml › menu). Rê chuột lên nhóm thấy ngay lệnh + mô tả ngắn (kiểu menu lệnh của CherryMC);
 *   bấm thì mở sách chỉ gồm chương của nhóm đó. Ô sách (menu.book-slot) mở cả cuốn. Không có menu thì /hd mở sách như trước.
 * - /hd sach: cả cuốn sách (trang đầu là mục lục, bấm tên chương để nhảy). /hd <mã chương>: sách của riêng chương đó.
 * - Bedrock (Floodgate): sách không mở được -> form nút bấm: các nhóm, bấm nhóm thấy lệnh, nút "Đọc chi tiết" hiện cả chương.
 * Nội dung: plugins/DuckyMoMoCore/huongdan.yml (đọc lại mỗi lần mở, sửa không cần reload).
 *   title, author, menu{title, rows, book-slot, items[{slot, icon, ten, mo, lenh["/lệnh|mô tả"], ghi, chuong}]},
 *   chapters: [{id, name, pages: ["&6Tiêu đề\n...", ...]}]. Mỗi trang sách ~14 dòng x ~19 ký tự.
 */
final class Guide implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final Locale VI = Locale.forLanguageTag("vi");
    private static final String FULL_BOOK = "\u0000sach";

    record Chapter(String id, String name, List<String> pages) {
    }

    record Item(int slot, Material icon, String name, String desc, String chapter, List<String> lines, String note) {
    }

    /** Rương menu /hd: ô -> mã chương (FULL_BOOK = cả cuốn). */
    static final class Holder implements InventoryHolder {
        final Map<Integer, String> target = new HashMap<>();
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final JavaPlugin plugin;
    private final String defaultChapter;
    private final String joinHint;

    Guide(JavaPlugin plugin, String defaultChapter, String joinHint) {
        this.plugin = plugin;
        this.defaultChapter = defaultChapter;
        this.joinHint = joinHint;
    }

    /** Nhắc riêng người vừa vào (guide.join-hint, trống = tắt), bấm vào dòng nhắc là mở cẩm nang. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (joinHint == null || joinHint.isBlank()) {
            return;
        }
        JoinNotes.add(e.getPlayer(), 0, LEGACY.deserialize(joinHint).clickEvent(ClickEvent.runCommand("/huongdan"))
                .hoverEvent(HoverEvent.showText(Component.text("Bấm để mở cẩm nang"))));
    }

    /** 30/10: gõ lệnh không có thì chỉ về /huongdan (bấm được) thay cho câu lỗi tiếng Anh của Minecraft. */
    @EventHandler
    public void onUnknown(org.bukkit.event.command.UnknownCommandEvent e) {
        if (!(e.getSender() instanceof org.bukkit.entity.Player)) {
            return;
        }
        String cmd = e.getCommandLine().split(" ")[0];
        e.message(LEGACY.deserialize("&c✘ Không có lệnh &f/" + cmd + "&c. Xem các lệnh: ")
                .append(LEGACY.deserialize("&e[/huongdan]").clickEvent(ClickEvent.runCommand("/huongdan"))
                        .hoverEvent(HoverEvent.showText(Component.text("Bấm để mở cẩm nang")))));
    }

    private YamlConfiguration load() {
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "huongdan.yml"));
    }

    private static List<Chapter> chapters(YamlConfiguration y) {
        List<Chapter> out = new ArrayList<>();
        for (Map<?, ?> m : y.getMapList("chapters")) {
            List<String> pages = new ArrayList<>();
            if (m.get("pages") instanceof List<?> l) {
                for (Object o : l) {
                    pages.add(String.valueOf(o));
                }
            }
            out.add(new Chapter(String.valueOf(m.get("id")), String.valueOf(m.get("name")), pages));
        }
        return out;
    }

    private static List<Item> items(YamlConfiguration y) {
        List<Item> out = new ArrayList<>();
        for (Map<?, ?> m : y.getMapList("menu.items")) {
            Material icon = Material.matchMaterial(String.valueOf(m.get("icon")));
            List<String> lines = new ArrayList<>();
            if (m.get("lenh") instanceof List<?> l) {
                l.forEach(o -> lines.add(String.valueOf(o)));
            }
            out.add(new Item(m.get("slot") instanceof Number n ? n.intValue() : -1, icon == null || !icon.isItem() ? Material.BOOK : icon,
                    str(m.get("ten")), str(m.get("mo")), str(m.get("chuong")), lines, str(m.get("ghi"))));
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /** Gợi ý Tab: sach + mã chương. */
    List<String> tab(String prefix) {
        List<String> out = new ArrayList<>(List.of("sach"));
        chapters(load()).forEach(c -> out.add(c.id()));
        out.removeIf(x -> !x.startsWith(prefix.toLowerCase(Locale.ROOT)));
        return out;
    }

    boolean command(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Chỉ người chơi mở được cẩm nang.");
            return true;
        }
        YamlConfiguration y = load();
        List<Chapter> chs = chapters(y);
        if (chs.isEmpty()) {
            p.sendMessage(Component.text("Chưa có cẩm nang (thiếu plugins/DuckyMoMoCore/huongdan.yml).", NamedTextColor.RED));
            return true;
        }
        boolean bedrock = BedrockMenu.available() && BedrockMenu.isBedrock(p.getUniqueId());
        String want = args.length > 0 ? args[0] : null;
        List<Item> menu = items(y);
        if (want == null && !menu.isEmpty()) {
            if (bedrock) {
                menuForm(p, y, chs, menu);
            } else {
                openMenu(p, y, menu);
            }
            return true;
        }
        if (want != null && !want.equalsIgnoreCase("sach")) {
            for (Chapter c : chs) {
                if (c.id().equalsIgnoreCase(want)) {
                    if (bedrock) {
                        showChapter(p, y, chs, c);
                    } else {
                        openChapter(p, c);
                    }
                    return true;
                }
            }
        }
        if (bedrock) {
            openForm(p, y, chs, null);
        } else {
            openBook(p, y, chs, want == null ? defaultChapter : null);
        }
        return true;
    }

    // ---------------------------------------------------------------- Java: menu rương

    private void openMenu(Player p, YamlConfiguration y, List<Item> menu) {
        int rows = Math.max(1, Math.min(6, y.getInt("menu.rows", 5)));
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, rows * 9, Tip.c(y.getString("menu.title", "&0Cẩm nang &8(/hd)")));
        for (Item it : menu) {
            if (it.slot() < 0 || it.slot() >= rows * 9) {
                continue;
            }
            h.inv.setItem(it.slot(), tooltip(it).apply(new ItemStack(it.icon())));
            if (!it.chapter().isBlank()) {
                h.target.put(it.slot(), it.chapter());
            }
        }
        int book = y.getInt("menu.book-slot", -1);
        if (book >= 0 && book < rows * 9) {
            h.inv.setItem(book, Tip.of("&6✎ &fCả cuốn cẩm nang").desc("Mọi chương trong một quyển sách", "Mục lục ở trang đầu, bấm tên chương để nhảy tới.")
                    .line().click("Bấm để mở sách").apply(new ItemStack(Material.WRITABLE_BOOK)));
            h.target.put(book, FULL_BOOK);
        }
        p.openInventory(h.inv);
    }

    /** Khung kiểu menu lệnh: TÊN NHÓM, mô tả, rồi mỗi dòng "/lệnh ➜ việc", lưu ý, bấm để đọc chi tiết. */
    private static Tip tooltip(Item it) {
        Tip t = Tip.of("&e✦ " + it.name().toUpperCase(VI)).desc(it.desc()).line();
        for (String l : it.lines()) {
            int bar = l.indexOf('|');
            if (bar > 0) {
                t.raw("&e" + l.substring(0, bar).trim() + " &8➜ &f" + l.substring(bar + 1).trim());
            } else {
                t.raw("&8• &f" + l.trim());
            }
        }
        if (!it.note().isBlank()) {
            t.gap();
            List<String> w = Tip.wrap(it.note(), 40);
            for (int i = 0; i < w.size(); i++) {
                t.raw((i == 0 ? "&6⚠ &7" : "  &7") + w.get(i));
            }
        }
        return it.chapter().isBlank() ? t : t.line().click("Bấm để đọc chi tiết");
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getRawSlot() < 0 || e.getRawSlot() >= h.inv.getSize()) {
            return;
        }
        String target = h.target.get(e.getRawSlot());
        if (target == null) {
            return;
        }
        p.closeInventory();
        // mở sách ở tick sau (đóng rương xong mới mở sách được)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            YamlConfiguration y = load();
            List<Chapter> chs = chapters(y);
            if (FULL_BOOK.equals(target)) {
                openBook(p, y, chs, null);
                return;
            }
            chs.stream().filter(c -> c.id().equalsIgnoreCase(target)).findFirst().ifPresent(c -> openChapter(p, c));
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    /** Sách chỉ một chương; dòng cuối mỗi trang "« Về cẩm nang" mở lại menu. */
    private void openChapter(Player p, Chapter c) {
        List<Component> pages = new ArrayList<>();
        for (String src : c.pages().isEmpty() ? List.of("&8(chưa có nội dung)") : c.pages()) {
            for (String text : paginate(src, 13)) {
                pages.add(LEGACY.deserialize(text).append(Component.text("\n« Về cẩm nang", NamedTextColor.GRAY)
                        .decoration(TextDecoration.UNDERLINED, true).clickEvent(ClickEvent.runCommand("/huongdan"))));
            }
        }
        p.openBook(Book.book(Component.text(c.name()), Component.text("DuckyMoMo"), pages));
    }

    // ---------------------------------------------------------------- Java: sách

    private void openBook(Player p, YamlConfiguration y, List<Chapter> chs, String jumpTo) {
        List<Component> pages = new ArrayList<>();
        // mục lục: trang 1; chương bắt đầu từ trang 2
        // Ngắt dòng + chia trang theo bề rộng font thật (trang sách 114 px, 14 dòng; chừa 1 dòng cho "« Mục lục")
        List<List<String>> laid = new ArrayList<>();
        for (Chapter c : chs) {
            List<String> ps = new ArrayList<>();
            for (String src : c.pages().isEmpty() ? List.of("&8(chưa có nội dung)") : c.pages()) {
                ps.addAll(paginate(src, 13));
            }
            laid.add(ps);
        }
        int[] start = new int[chs.size()];
        int next = 2;
        for (int i = 0; i < chs.size(); i++) {
            start[i] = next;
            next += laid.get(i).size();
        }
        Component toc = LEGACY.deserialize(y.getString("toc-title", "&6CẨM NANG\n&8DuckyMoMo Network\n\n"));
        for (int i = 0; i < chs.size(); i++) {
            Chapter c = chs.get(i);
            toc = toc.append(Component.text("» " + c.name() + "\n", NamedTextColor.DARK_BLUE)
                    .clickEvent(ClickEvent.changePage(start[i]))
                    .hoverEvent(HoverEvent.showText(Component.text("Trang " + start[i]))));
        }
        toc = toc.append(LEGACY.deserialize(y.getString("toc-footer", "\n&8Bấm tên chương để mở")));
        pages.add(toc);
        for (List<String> ps : laid) {
            for (String text : ps) {
                Component page = LEGACY.deserialize(text);
                // dòng cuối mỗi trang: về mục lục
                page = page.append(Component.text("\n« Mục lục", NamedTextColor.GRAY)
                        .decoration(TextDecoration.UNDERLINED, true).clickEvent(ClickEvent.changePage(1)));
                pages.add(page);
            }
        }
        p.openBook(Book.book(LEGACY.deserialize(y.getString("title", "Cẩm nang")),
                Component.text(y.getString("author", "DuckyMoMo")), pages));
        // mở thẳng chương (Book API không chọn được trang mở đầu): nhắc người chơi bấm mục lục
        if (jumpTo != null) {
            for (int i = 0; i < chs.size(); i++) {
                if (chs.get(i).id().equalsIgnoreCase(jumpTo)) {
                    p.sendMessage(Component.text("Cẩm nang: chương \"" + chs.get(i).name() + "\" ở trang " + start[i]
                            + " (bấm tên chương ở mục lục).", NamedTextColor.GRAY));
                }
            }
        }
    }

    // ---------------------------------------------------------------- ngắt dòng theo font Minecraft

    private static final int LINE_PX = 114;

    /** Bề rộng (px, kể cả 1 px cách chữ) theo font mặc định; chữ có dấu tính theo chữ gốc. */
    static int width(char ch, boolean bold) {
        char b = Normalizer.normalize(String.valueOf(ch), Normalizer.Form.NFD).charAt(0);
        int w = switch (b) {
            case 'i', '!', '.', ',', ':', ';', '|', '\'' -> 2;
            case 'l', '`' -> 3;
            case 't', 'I', ' ', '[', ']' -> 4;
            case 'f', 'k', '(', ')', '{', '}', '<', '>', '*', '"' -> 5;
            case '@', '~', '«', '»' -> 7;
            default -> 6;
        };
        return w + (bold && b != ' ' ? 1 : 0);
    }

    /**
     * Ngắt một trang nguồn thành các dòng vừa 114 px, rồi gom thành trang sách tối đa maxLines dòng.
     * Mã màu &x được giữ: dòng/trang mới mở bằng định dạng đang dùng.
     */
    static List<String> paginate(String src, int maxLines) {
        List<String> lines = new ArrayList<>();
        List<String> lineFmt = new ArrayList<>();
        String color = "";
        String format = "";
        for (String raw : src.split("\n", -1)) {
            StringBuilder cur = new StringBuilder();
            String curStartFmt = color + format;
            int px = 0;
            for (String word : raw.split(" ", -1)) {
                // đo từ (bỏ mã màu), cập nhật định dạng theo mã trong từ
                int wpx = 0;
                boolean bold = format.contains("&l");
                String c2 = color;
                String f2 = format;
                for (int i = 0; i < word.length(); i++) {
                    char ch = word.charAt(i);
                    if (ch == '&' && i + 1 < word.length() && "0123456789abcdefklmnor".indexOf(Character.toLowerCase(word.charAt(i + 1))) >= 0) {
                        char code = Character.toLowerCase(word.charAt(++i));
                        if ("0123456789abcdef".indexOf(code) >= 0) {
                            c2 = "&" + code;
                            f2 = "";
                        } else if (code == 'r') {
                            c2 = "";
                            f2 = "";
                        } else {
                            f2 += "&" + code;
                        }
                        bold = f2.contains("&l");
                        continue;
                    }
                    wpx += width(ch, bold);
                }
                int space = cur.isEmpty() ? 0 : width(' ', format.contains("&l"));
                if (!cur.isEmpty() && px + space + wpx > LINE_PX) {
                    lines.add(cur.toString());
                    lineFmt.add(curStartFmt);
                    cur = new StringBuilder();
                    curStartFmt = color + format;
                    px = 0;
                    space = 0;
                }
                if (space > 0) {
                    cur.append(' ');
                }
                cur.append(word);
                px += space + wpx;
                color = c2;
                format = f2;
            }
            lines.add(cur.toString());
            lineFmt.add(curStartFmt);
        }
        // bỏ dòng trống cuối
        while (lines.size() > 1 && lines.get(lines.size() - 1).isBlank()) {
            lines.remove(lines.size() - 1);
            lineFmt.remove(lineFmt.size() - 1);
        }
        List<String> pages = new ArrayList<>();
        for (int i = 0; i < lines.size(); i += maxLines) {
            StringBuilder pg = new StringBuilder(lineFmt.get(i));
            for (int j = i; j < Math.min(lines.size(), i + maxLines); j++) {
                if (j > i) {
                    pg.append('\n').append(j > i && !lines.get(j).startsWith("&") ? lineFmt.get(j) : "");
                }
                pg.append(lines.get(j));
            }
            pages.add(pg.toString());
        }
        return pages;
    }

    // ---------------------------------------------------------------- Bedrock: form

    /** Bedrock: danh sách nhóm; bấm nhóm thấy lệnh, nút "Đọc chi tiết" hiện cả chương. */
    private void menuForm(Player p, YamlConfiguration y, List<Chapter> chs, List<Item> menu) {
        List<BedrockMenu.Entry> entries = new ArrayList<>();
        for (int i = 0; i < menu.size(); i++) {
            entries.add(new BedrockMenu.Entry(menu.get(i).name(), String.valueOf(i)));
        }
        entries.add(new BedrockMenu.Entry("Cả cuốn cẩm nang", "sach"));
        BedrockMenu.open(p.getUniqueId(), strip(y.getString("menu.title", "Cẩm nang")), "Chọn mục muốn xem:", entries,
                pick -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (pick.equals("sach")) {
                        openForm(p, y, chs, null);
                    } else {
                        itemForm(p, y, chs, menu, menu.get(Integer.parseInt(pick)));
                    }
                }));
    }

    private void itemForm(Player p, YamlConfiguration y, List<Chapter> chs, List<Item> menu, Item it) {
        StringBuilder body = new StringBuilder("§7" + it.desc() + "\n");
        for (String l : it.lines()) {
            int bar = l.indexOf('|');
            body.append('\n').append(bar > 0 ? "§e" + l.substring(0, bar).trim() + " §8- §f" + l.substring(bar + 1).trim() : "§f• " + l.trim());
        }
        if (!it.note().isBlank()) {
            body.append("\n\n§6Lưu ý: §7").append(it.note());
        }
        List<BedrockMenu.Entry> entries = new ArrayList<>();
        if (!it.chapter().isBlank()) {
            entries.add(new BedrockMenu.Entry("Đọc chi tiết", "ct"));
        }
        entries.add(new BedrockMenu.Entry("« Quay lại", "back"));
        BedrockMenu.open(p.getUniqueId(), it.name(), body.toString(), entries,
                pick -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (pick.equals("ct")) {
                        chs.stream().filter(c -> c.id().equalsIgnoreCase(it.chapter())).findFirst()
                                .ifPresent(c -> showChapter(p, y, chs, c));
                    } else {
                        menuForm(p, y, chs, menu);
                    }
                }));
    }

    private void openForm(Player p, YamlConfiguration y, List<Chapter> chs, String want) {
        for (Chapter c : chs) {
            if (want != null && c.id().equalsIgnoreCase(want)) {
                showChapter(p, y, chs, c);
                return;
            }
        }
        List<BedrockMenu.Entry> entries = new ArrayList<>();
        for (Chapter c : chs) {
            entries.add(new BedrockMenu.Entry(c.name(), c.id()));
        }
        BedrockMenu.open(p.getUniqueId(), strip(y.getString("title", "Cẩm nang")), "Chọn chương muốn đọc:", entries,
                id -> chs.stream().filter(c -> c.id().equals(id)).findFirst()
                        .ifPresent(c -> plugin.getServer().getScheduler().runTask(plugin, () -> showChapter(p, y, chs, c))));
    }

    private void showChapter(Player p, YamlConfiguration y, List<Chapter> chs, Chapter c) {
        // Bedrock hiểu mã màu §: đổi & -> §; nội dung cả chương trong một form, nút Quay lại về danh sách chương
        String body = String.join("\n\n", c.pages()).replace('&', '§');
        BedrockMenu.open(p.getUniqueId(), c.name(), body, List.of(new BedrockMenu.Entry("« Cẩm nang", "back")),
                x -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    List<Item> menu = items(y);
                    if (menu.isEmpty()) {
                        openForm(p, y, chs, null);
                    } else {
                        menuForm(p, y, chs, menu);
                    }
                }));
    }

    private static String strip(String s) {
        return s.replaceAll("&[0-9a-fk-or]", "");
    }

    static String defaultChapter(ConfigurationSection c) {
        return c == null ? null : c.getString("default-chapter");
    }
}
