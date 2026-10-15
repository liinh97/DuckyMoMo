package vn.duckymomo.core.paper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Đăng nhập trang web bằng tài khoản game (/web): cấp mã 6 số dùng một lần, hết hạn sau vài phút (dùng được cho cả
 * Bedrock, vốn không biết mật khẩu AuthMe). Trang web hỏi lại qua RCON: /dmm webma <tên> <mã> → "WEBMA OK <tên thật>"
 * hoặc "WEBMA SAI". Nhập sai 5 lần thì mã bị huỷ. Mã chỉ giữ trong bộ nhớ (khởi động lại server là mất, gõ /web lại).
 */
final class WebLogin {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final int MAX_TRIES = 5;

    private static final class Entry {
        final String name;
        final String code;
        final long until;
        int tries;

        Entry(String name, String code, long until) {
            this.name = name;
            this.code = code;
            this.until = until;
        }
    }

    private final Map<String, Entry> codes = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final String url;
    private final int minutes;

    WebLogin(ConfigurationSection c) {
        this.url = c == null ? "https://app.goc6.io.vn/mc/web/" : c.getString("url", "https://app.goc6.io.vn/mc/web/");
        this.minutes = Math.max(1, c == null ? 5 : c.getInt("code-minutes", 5));
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s);
    }

    /** /web: cấp mã mới (mã cũ của người này hết hiệu lực). */
    boolean command(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Chỉ người chơi dùng được /web.");
            return true;
        }
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        codes.put(p.getName().toLowerCase(Locale.ROOT), new Entry(p.getName(), code, System.currentTimeMillis() + minutes * 60_000L));
        p.sendMessage(c("&6&lĐăng nhập trang web DuckyMoMo"));
        p.sendMessage(c("&7Mã của bạn: ").append(c("&e&l" + code)
                .clickEvent(ClickEvent.copyToClipboard(code)).hoverEvent(HoverEvent.showText(c("&7Bấm để sao chép"))))
                .append(c(" &8(dùng 1 lần, hết hạn sau " + minutes + " phút)")));
        p.sendMessage(c("&7Mở ").append(c("&f&n" + url).clickEvent(ClickEvent.openUrl(url)))
                .append(c(" &7→ &fĐăng nhập &7→ &fMã từ game&7, nhập tên &f" + p.getName() + " &7và mã.")));
        p.sendMessage(c("&8Không đưa mã cho ai. Admin không bao giờ hỏi mã này."));
        return true;
    }

    /** /dmm webma <tên> <mã> (chỉ console/RCON): kiểm tra và huỷ mã. */
    String check(String name, String code) {
        String key = name.toLowerCase(Locale.ROOT);
        Entry e = codes.get(key);
        if (e == null) {
            return "WEBMA SAI";
        }
        if (System.currentTimeMillis() > e.until) {
            codes.remove(key);
            return "WEBMA SAI";
        }
        boolean ok = MessageDigest.isEqual(e.code.getBytes(StandardCharsets.UTF_8), code.trim().getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            if (++e.tries >= MAX_TRIES) {
                codes.remove(key);
            }
            return "WEBMA SAI";
        }
        codes.remove(key);
        return "WEBMA OK " + e.name;
    }
}
