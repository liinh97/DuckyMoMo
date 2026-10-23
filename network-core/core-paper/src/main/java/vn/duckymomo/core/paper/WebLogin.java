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
        menu(p, code);
        return true;
    }

    /** /web: menu (23/10, trước in chat). Mã ghi to ở giữa; bấm ô thì gửi mã (bấm sao chép) / link (bấm mở) vào chat vì menu không mở link được. */
    private void menu(Player p, String code) {
        InfoMenu m = InfoMenu.of("&6✉ Đăng nhập trang web").accent(org.bukkit.Material.ORANGE_STAINED_GLASS_PANE);
        m.top(Tip.of("&6✉ &fĐăng nhập trang web DuckyMoMo").accent("&6").desc("Xem tài khoản, rank, lệnh của bạn.", "Staff mở trang quản trị theo quyền.")
                .apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.WRITABLE_BOOK)));
        m.add(Tip.of("&fMã của bạn: &e" + code).accent("&e").desc("Dùng 1 lần, hết hạn sau " + minutes + " phút.").line()
                .deny("Không đưa mã cho ai. Admin không bao giờ hỏi mã này.").click("Bấm để gửi mã vào chat (bấm vào mã là sao chép)")
                .apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.NAME_TAG)), pl -> {
                    pl.closeInventory();
                    pl.sendMessage(c("&7Mã đăng nhập web: ").append(c("&e" + code).clickEvent(ClickEvent.copyToClipboard(code))
                            .hoverEvent(HoverEvent.showText(c("&7Bấm để sao chép")))).append(c(" &8(hết hạn sau " + minutes + " phút)")));
                });
        m.add(Tip.of("&fCách đăng nhập").accent("&b").line()
                .item("Mở &f" + url).item("Bấm &fĐăng nhập &7→ &fMã từ game").item("Nhập tên &f" + p.getName() + " &7và mã")
                .click("Bấm để gửi link vào chat (bấm vào link là mở)")
                .apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.COMPASS)), pl -> {
                    pl.closeInventory();
                    pl.sendMessage(c("&7Trang web: ").append(c("&f&n" + url).clickEvent(ClickEvent.openUrl(url))
                            .hoverEvent(HoverEvent.showText(c("&7Bấm để mở")))).append(c(" &7→ &fĐăng nhập &7→ &fMã từ game")));
                });
        m.add(Tip.of("&fLấy mã mới").accent("&a").desc("Mã cũ hết hiệu lực.").click("Bấm để lấy mã khác")
                .apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.CLOCK)), InfoMenu.run("web"));
        m.open(p);
    }

    /**
     * /dmm webma <tên> <mã> (chỉ console/RCON): kiểm tra và huỷ mã. Trả "WEBMA OK <tên thật>" hoặc lý do để web báo rõ:
     * WEBMA KHAC (mã đúng nhưng của tài khoản khác, mã đó KHÔNG bị huỷ), HET (hết hạn), SAI (sai số), KHONG (tên này chưa lấy mã).
     */
    String check(String name, String code) {
        String key = name.toLowerCase(Locale.ROOT);
        String given = code.trim();
        long now = System.currentTimeMillis();
        codes.values().removeIf(e -> now > e.until + 600_000L); // dọn mã quá hạn lâu
        Entry e = codes.get(key);
        if (e == null || !same(e.code, given)) {
            boolean other = codes.entrySet().stream()
                    .anyMatch(x -> !x.getKey().equals(key) && now <= x.getValue().until && same(x.getValue().code, given));
            if (other) {
                return "WEBMA KHAC";
            }
        }
        if (e == null) {
            return "WEBMA KHONG";
        }
        if (now > e.until) {
            codes.remove(key);
            return "WEBMA HET";
        }
        if (!same(e.code, given)) {
            if (++e.tries >= MAX_TRIES) {
                codes.remove(key);
            }
            return "WEBMA SAI";
        }
        codes.remove(key);
        return "WEBMA OK " + e.name;
    }

    private static boolean same(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
