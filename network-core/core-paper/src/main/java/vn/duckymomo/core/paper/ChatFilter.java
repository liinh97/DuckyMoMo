package vn.duckymomo.core.paper;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Lọc chat: che từ tục tiếng Việt (thay bằng ***) và chống spam (chat quá nhanh, lặp lại, viết hoa toàn bộ).
 * Hai danh sách từ (config chat-filter):
 *   words-accented: so khớp nguyên dạng có dấu (từ mà bỏ dấu sẽ trùng từ thường: lồn/lớn, cặc/các, buồi/buổi, đéo/đeo);
 *   words-plain: so khớp sau khi bỏ dấu, đ -> d (viết tắt rõ ràng: dm, vcl, dcm…), bắt được cả kiểu gõ không dấu.
 * Mỗi từ khớp theo ranh giới từ. Quyền duckymomo.chat.bypass: không bị chống spam (vẫn bị che từ tục).
 */
final class ChatFilter implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final Pattern accented;
    private final Pattern plain;
    private final long minIntervalMs;
    private final long repeatMs;
    private final double capsRatio;
    private final String tooFast;
    private final String repeated;
    private final Map<UUID, long[]> lastTime = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastText = new ConcurrentHashMap<>();

    ChatFilter(ConfigurationSection c) {
        this.accented = build(c.getStringList("words-accented"), false);
        this.plain = build(c.getStringList("words-plain"), true);
        this.minIntervalMs = (long) (c.getDouble("min-interval-seconds", 1.5) * 1000);
        this.repeatMs = c.getLong("repeat-seconds", 30) * 1000;
        this.capsRatio = c.getDouble("caps-ratio", 0.7);
        this.tooFast = c.getString("too-fast-message", "&cChat chậm lại một chút!");
        this.repeated = c.getString("repeat-message", "&cĐừng gửi lặp lại cùng một câu!");
    }

    /** Bỏ dấu tiếng Việt, đ -> d, chữ thường. Giữ nguyên độ dài chuỗi để vị trí khớp dùng lại được trên chuỗi gốc. */
    static String fold(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = Character.toLowerCase(s.charAt(i));
            if (ch == 'đ') {
                b.append('d');
                continue;
            }
            String d = Normalizer.normalize(String.valueOf(ch), Normalizer.Form.NFD);
            b.append(d.isEmpty() ? ch : d.charAt(0));
        }
        return b.toString();
    }

    private static Pattern build(List<String> words, boolean folded) {
        if (words.isEmpty()) {
            return null;
        }
        StringBuilder r = new StringBuilder("(?<![\\p{L}\\p{N}])(?:");
        for (int i = 0; i < words.size(); i++) {
            String w = folded ? fold(words.get(i)) : words.get(i).toLowerCase();
            r.append(i == 0 ? "" : "|").append(Pattern.quote(w).replace(" ", "\\E\\s+\\Q"));
        }
        r.append(")(?![\\p{L}\\p{N}])");
        return Pattern.compile(r.toString(), Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE);
    }

    /** Che các đoạn khớp; trả về null nếu không có gì để che. */
    String censor(String text) {
        char[] out = text.toCharArray();
        boolean hit = false;
        String nfc = Normalizer.normalize(text, Normalizer.Form.NFC);
        if (nfc.length() == text.length()) {
            hit |= mask(accented, nfc.toLowerCase(), out);
            hit |= mask(plain, fold(nfc), out);
        }
        return hit ? new String(out) : null;
    }

    private static boolean mask(Pattern p, String s, char[] out) {
        if (p == null || s.length() != out.length) {
            return false;
        }
        boolean hit = false;
        Matcher m = p.matcher(s);
        while (m.find()) {
            for (int i = m.start(); i < m.end(); i++) {
                if (!Character.isWhitespace(out[i])) {
                    out[i] = '*';
                }
            }
            hit = true;
        }
        return hit;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        String text = PLAIN.serialize(e.message());
        if (!p.hasPermission("duckymomo.chat.bypass")) {
            long now = System.currentTimeMillis();
            long[] last = lastTime.computeIfAbsent(p.getUniqueId(), k -> new long[] {0});
            if (now - last[0] < minIntervalMs) {
                e.setCancelled(true);
                p.sendMessage(LEGACY.deserialize(tooFast));
                return;
            }
            String norm = fold(text).replaceAll("\\s+", " ").trim();
            if (norm.equals(lastText.get(p.getUniqueId())) && now - last[0] < repeatMs) {
                e.setCancelled(true);
                p.sendMessage(LEGACY.deserialize(repeated));
                return;
            }
            last[0] = now;
            lastText.put(p.getUniqueId(), norm);
            long letters = text.chars().filter(Character::isLetter).count();
            long upper = text.chars().filter(Character::isUpperCase).count();
            if (letters >= 8 && upper >= letters * capsRatio) {
                text = text.toLowerCase();
                e.message(Component.text(text));
            }
        }
        String clean = censor(text);
        if (clean != null) {
            e.message(Component.text(clean));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastTime.remove(e.getPlayer().getUniqueId());
        lastText.remove(e.getPlayer().getUniqueId());
    }
}
