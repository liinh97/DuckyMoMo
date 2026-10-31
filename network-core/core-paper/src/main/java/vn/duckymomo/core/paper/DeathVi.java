package vn.duckymomo.core.paper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;

/**
 * Tin tử vong tiếng Việt dịch sẵn ở server (31/10, chủ server): tin tử vong vanilla là khoá dịch, máy người chơi để tiếng Anh thì
 * hiện "X fell out of the world". Ở đây thay các khoá death.* / entity.* / item.* / block.* bằng bản tiếng Việt CHÍNH THỨC của
 * Minecraft (vi_tuvong.json lấy từ vi_vn.json của Mojang, bản 26.2; tạo lại bằng tools/vi/tuvong-vi.py khi lên bản mới).
 * Khoá không có trong bảng thì giữ nguyên (máy người chơi tự dịch).
 */
final class DeathVi {
    private DeathVi() { }

    private static final Map<String, String> VI = load();

    private static Map<String, String> load() {
        Map<String, String> m = new HashMap<>();
        try (InputStream in = DeathVi.class.getResourceAsStream("/vi_tuvong.json")) {
            if (in != null) {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                o.entrySet().forEach(e -> m.put(e.getKey(), e.getValue().getAsString()));
            }
        } catch (Exception ignored) {
            // thiếu bảng thì để máy người chơi tự dịch như cũ
        }
        return m;
    }

    static Component vi(Component c) {
        if (c == null || VI.isEmpty()) {
            return c;
        }
        List<Component> kids = new ArrayList<>();
        for (Component k : c.children()) {
            kids.add(vi(k));
        }
        if (c instanceof TranslatableComponent t) {
            String fmt = VI.get(t.key());
            List<Component> args = new ArrayList<>();
            for (TranslationArgument a : t.arguments()) {
                args.add(vi(a.asComponent()));
            }
            if (fmt == null) {
                return t.arguments(args).children(kids);
            }
            TextComponent.Builder b = Component.text().style(t.style());
            StringBuilder lit = new StringBuilder();
            int next = 0;
            for (int i = 0; i < fmt.length(); i++) {
                char ch = fmt.charAt(i);
                if (ch != '%' || i + 1 >= fmt.length()) {
                    lit.append(ch);
                    continue;
                }
                char n = fmt.charAt(i + 1);
                if (n == '%') {
                    lit.append('%');
                    i++;
                    continue;
                }
                int idx = -1;
                int end = i + 1;
                if (n == 's') {
                    idx = next++;
                    end = i + 1;
                } else {
                    int j = i + 1;
                    while (j < fmt.length() && Character.isDigit(fmt.charAt(j))) {
                        j++;
                    }
                    if (j > i + 1 && j + 1 < fmt.length() && fmt.charAt(j) == '$' && fmt.charAt(j + 1) == 's') {
                        idx = Integer.parseInt(fmt.substring(i + 1, j)) - 1;
                        end = j + 1;
                    }
                }
                if (idx < 0) {
                    lit.append(ch);
                    continue;
                }
                if (lit.length() > 0) {
                    b.append(Component.text(lit.toString()));
                    lit.setLength(0);
                }
                if (idx < args.size()) {
                    b.append(args.get(idx));
                }
                i = end;
            }
            if (lit.length() > 0) {
                b.append(Component.text(lit.toString()));
            }
            return b.append(kids).build();
        }
        return c.children(kids);
    }
}
