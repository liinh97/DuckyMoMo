package vn.duckymomo.core.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Khung mô tả (tooltip) dùng chung cho MỌI menu DuckyMoMoCore (20/10, người dùng: "mô tả và sắp xếp cho đẹp, đừng đơn điệu").
 * Bố cục cố định để người chơi quen mắt:
 *   [ký hiệu] Tên (phụ đề xám)
 *   ▍ mô tả ngắn (dòng đầu sáng, dòng sau xám)
 *   ───────────────            (vạch ngăn)
 *   • Nhãn: giá trị            (thông tin, mỗi dòng một ý)
 *   • Nhóm:                    (tiêu đề nhóm) +   ▸ mục con
 *   ───────────────
 *   ➜ Bấm để …                 (việc khi bấm, xanh; không làm được thì đỏ ✘)
 * Hiển thị (lore()): dòng trống sau tiêu đề, vạch ngăn hiện thành dòng trống cho thoáng (22/10).
 * KHÔNG dùng chữ in đậm (&l): font tiếng Việt vẽ đậm bị nhòe. Dòng dài tự xuống dòng ở ~38 ký tự để khung không quá rộng.
 */
final class Tip {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('&').hexColors().build();   // 30/10: tiền tố rank ủng hộ màu hex &#RRGGBB
    static final String LINE = "&7&m                              ";
    private static final int WRAP = 38;

    private Component name;
    private final List<String> lines = new ArrayList<>();
    private String accent = "&6";
    private boolean pendingGap;

    private Tip() {
    }

    /** title: tên có mã màu; nên có ký hiệu đầu (vd "&e⛃ &fChợ đen"). */
    static Tip of(String title) {
        Tip t = new Tip();
        t.name = c(title);
        return t;
    }

    /** Màu nhấn (thanh mô tả, tiêu đề nhóm), mặc định &6. */
    Tip accent(String color) {
        this.accent = color;
        return this;
    }

    /** Phụ đề xám sau tên: "Tên (phụ đề)". */
    Tip sub(String text) {
        name = name.append(c(" &7(" + text + "&7)"));
        return this;
    }

    /** Mô tả: dòng đầu có thanh màu nhấn, các dòng sau xám. */
    Tip desc(String... text) {
        boolean first = true;
        for (String d : text) {
            if (d == null || d.isBlank()) {
                continue;
            }
            List<String> w = wrap(d, WRAP - 2);
            for (int i = 0; i < w.size(); i++) {
                add((first && i == 0 ? accent + "▍ &f" : "&7▍ &7") + w.get(i));
            }
            first = false;
        }
        return this;
    }

    /** Vạch ngăn. */
    Tip line() {
        pendingGap = false;
        if (!lines.isEmpty() && !lines.get(lines.size() - 1).equals(LINE)) {
            lines.add(LINE);
        }
        return this;
    }

    /** Dòng trống (chỉ thêm khi có dòng tiếp theo). */
    Tip gap() {
        pendingGap = !lines.isEmpty();
        return this;
    }

    /** Thông tin: "• Nhãn: giá trị" (giá trị có mã màu riêng thì giữ, không có thì trắng). */
    Tip kv(String label, String value) {
        String v = value == null ? "" : value;
        add("&7• &7" + label + ": " + (v.startsWith("&") ? v : "&f" + v));
        return this;
    }

    /** Tiêu đề nhóm: "• Nhóm:" màu nhấn. */
    Tip head(String label) {
        add("&7• " + accent + label + ":");
        return this;
    }

    /** Mục con dưới nhóm: "  ▸ mục". */
    Tip item(String text) {
        List<String> w = wrap(text, WRAP - 4);
        for (int i = 0; i < w.size(); i++) {
            add((i == 0 ? "  &7▸ &f" : "    &f") + w.get(i));
        }
        return this;
    }

    Tip items(List<String> texts) {
        texts.forEach(this::item);
        return this;
    }

    /** Dòng tự do (giữ mã màu). */
    Tip raw(String text) {
        add(text);
        return this;
    }

    /** Ghi chú nhỏ xám đậm. */
    Tip note(String text) {
        for (String w : wrap(text, WRAP)) {
            add("&7" + w);
        }
        return this;
    }

    /** Việc khi bấm (xanh). */
    Tip click(String action) {
        add("&a➜ " + action);
        return this;
    }

    /** Việc khi Shift + bấm / chuột phải… */
    Tip click(String how, String action) {
        add("&e" + how + "&7: &f" + action);
        return this;
    }

    /** Không làm được (đỏ). */
    Tip deny(String why) {
        add("&c✘ " + why);
        return this;
    }

    /** Điều kiện đạt / chưa đạt. */
    Tip check(boolean ok, String text) {
        add((ok ? "&a✔ &7" : "&c✘ &7") + text);
        return this;
    }

    private void add(String s) {
        if (pendingGap) {
            lines.add("");
            pendingGap = false;
        }
        lines.add(s);
    }

    /** Mô tả thoáng (22/10, người dùng: "tooltip nhìn phải thoáng"): dòng trống sau tiêu đề, vạch ngăn thành dòng trống,
     *  không hai dòng trống liền nhau, không dòng trống ở cuối. */
    List<Component> lore() {
        List<String> out = new ArrayList<>();
        out.add("");
        for (String s : lines) {
            String v = s.equals(LINE) ? "" : s;
            if (v.isEmpty() && out.get(out.size() - 1).isEmpty()) {
                continue;
            }
            out.add(v);
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
            out.remove(out.size() - 1);
        }
        List<Component> lore = new ArrayList<>();
        for (String s : out) {
            lore.add(s.isEmpty() ? Component.empty() : c(s));
        }
        return lore;
    }

    Component name() {
        return name;
    }

    /** Ghi tên + mô tả lên đồ (ẩn dòng thuộc tính / phù phép mặc định của vật phẩm cho gọn). */
    ItemStack apply(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        if (m == null) {
            return it;
        }
        m.displayName(name);
        m.lore(lore());
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(m);
        return it;
    }

    /** Thêm khung mô tả vào SAU lore sẵn có (đồ thật, vd món thư viện hiện trong menu). Tên giữ nguyên. */
    ItemStack append(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        if (m == null) {
            return it;
        }
        List<Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
        lore.addAll(lore());   // lore() mở đầu bằng dòng trống: ngăn với lore sẵn có
        m.lore(lore);
        it.setItemMeta(m);
        return it;
    }

    /** 30/10 chủ server: chữ có bóng đổ nhìn nhoè với dấu tiếng Việt -> mọi chữ của plugin tắt bóng (shadow_color trong suốt, MC 1.21.4+). */
    static Component ns(Component c) {
        return c.shadowColor(net.kyori.adventure.text.format.ShadowColor.none());
    }

    static Component c(String s) {
        return ns(LEGACY.deserialize(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
    }

    /** Xuống dòng theo số ký tự nhìn thấy (bỏ qua mã màu), giữ màu đang dùng sang dòng sau. */
    static List<String> wrap(String s, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int vis = 0;
        String color = "";
        String lastColor = "";
        for (String word : s.split(" ")) {
            int wv = word.replaceAll("&[0-9a-fk-orA-FK-OR]|&#[0-9a-fA-F]{6}", "").length();
            if (vis > 0 && vis + 1 + wv > width) {
                out.add(cur.toString());
                cur = new StringBuilder(lastColor);
                vis = 0;
            }
            if (vis > 0) {
                cur.append(' ');
                vis++;
            }
            cur.append(word);
            vis += wv;
            java.util.regex.Matcher mm = java.util.regex.Pattern.compile("&#[0-9a-fA-F]{6}|&[0-9a-fA-F]").matcher(word);
            while (mm.find()) {
                color = mm.group();
            }
            lastColor = color;
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }
}
