package vn.duckymomo.core.paper;

import java.io.File;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Cẩm nang trong game: /huongdan [chương] (alias hd, guide, camnang).
 * - Java: mở sách; trang đầu là mục lục, bấm tên chương để nhảy tới trang của chương.
 * - Bedrock (Floodgate): sách không mở được -> form nút bấm, mỗi chương một nút, bấm thì hiện nội dung chương.
 * Nội dung: plugins/DuckyMoMoCore/huongdan.yml (đọc lại mỗi lần mở, sửa không cần reload).
 *   title, author, chapters: [{id, name, pages: ["&6Tiêu đề\n...", ...]}]. Mỗi trang sách ~14 dòng x ~19 ký tự.
 * Mặc định mở chương guide.default-chapter trong config.yml (vd banghoi ở Bang Hội Chiến) khi không ghi chương.
 */
final class Guide implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    record Chapter(String id, String name, List<String> pages) {
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
        String want = args.length > 0 ? args[0] : null;
        if (BedrockMenu.available() && BedrockMenu.isBedrock(p.getUniqueId())) {
            openForm(p, y, chs, want);
        } else {
            openBook(p, y, chs, want == null ? defaultChapter : want);
        }
        return true;
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
        Component toc = LEGACY.deserialize(y.getString("toc-title", "&6&lCẨM NANG\n&8DuckyMoMo Network\n\n"));
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
        BedrockMenu.open(p.getUniqueId(), c.name(), body, List.of(new BedrockMenu.Entry("« Các chương", "back")),
                x -> plugin.getServer().getScheduler().runTask(plugin, () -> openForm(p, y, chs, null)));
    }

    private static String strip(String s) {
        return s.replaceAll("&[0-9a-fk-or]", "");
    }

    static String defaultChapter(ConfigurationSection c) {
        return c == null ? null : c.getString("default-chapter");
    }
}
