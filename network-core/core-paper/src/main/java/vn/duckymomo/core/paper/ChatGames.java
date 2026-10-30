package vn.duckymomo.core.paper;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Đố vui trong chat (30/10, chủ server: "câu đố có quà nhỏ, toán, tiếng Việt… đơn giản"). Cứ interval-minutes phút (có người thật
 * online) ra một câu, ai gõ đúng đầu tiên trong timeout giây nhận xu. Trả lời không dấu / hoa thường đều được.
 * Kiểu (weights): xaotron (xáo chữ trong từng tiếng), dienchu (điền chữ bị che), daonguoc (đọc ngược), toan (phép tính),
 * dayso (số tiếp theo của dãy), gonhanh (gõ lại đúng câu), caudo (câu hỏi + đáp án trong config). Admin: /dmm minigame [kiểu].
 * Kho từ: chat-games.words + (use-item-names) ~1.800 tên vật phẩm tiếng Việt của Minecraft, lọc tên ngắn. Nhớ recent-memory câu
 * gần nhất để không lặp lại sớm.
 * Config chat-games: {enabled, interval-minutes, timeout, min-players, reward-min, reward-max, weights{}, words[], phrases[],
 *   riddles[{q, a[]}]}
 */
final class ChatGames implements Listener {
    private static final String LINE = "&d&m                                                     ";

    private final JavaPlugin plugin;
    private final ConfigurationSection cfg;
    private final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    private volatile List<String> answers;   // đáp án đã chuẩn hoá; null = không có câu đang chạy
    private String shown;                     // đáp án hiện khi công bố
    private String kind;
    private BukkitTask timeout;
    private BukkitTask loop;
    private static final List<String> KINDS = List.of("xaotron", "dienchu", "daonguoc", "toan", "dayso", "gonhanh", "caudo");
    private final List<String> pool = new ArrayList<>();
    private final java.util.ArrayDeque<String> recent = new java.util.ArrayDeque<>();

    ChatGames(JavaPlugin plugin, ConfigurationSection cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
        java.util.Set<String> seen = new java.util.LinkedHashSet<>(cfg.getStringList("words"));
        if (cfg.getBoolean("use-item-names", true)) {
            for (String n : Rewards.vanillaNames()) {
                String t = n.toLowerCase(Locale.forLanguageTag("vi")).trim();
                int words = t.split(" ").length;
                if (words <= 3 && t.length() >= 4 && t.length() <= 20 && !t.matches(".*[0-9()].*")) {
                    seen.add(t);
                }
            }
        }
        pool.addAll(seen);
    }

    /** Lấy ngẫu nhiên, tránh các mục ra gần đây. */
    private String fresh(List<String> list) {
        String pick = list.get(rnd.nextInt(list.size()));
        for (int i = 0; i < 30 && recent.contains(pick); i++) {
            pick = list.get(rnd.nextInt(list.size()));
        }
        recent.addLast(pick);
        while (recent.size() > Math.max(10, cfg.getInt("recent-memory", 300))) {
            recent.removeFirst();
        }
        return pick;
    }

    void start() {
        long every = Math.max(2, cfg.getInt("interval-minutes", 12)) * 60L * 20L;
        loop = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long real = Bukkit.getOnlinePlayers().stream().filter(p -> !BotView.isBot(p)).count();
            if (answers == null && real >= Math.max(1, cfg.getInt("min-players", 1))) {
                ask(null);
            }
        }, every, every);
    }

    void stop() {
        if (loop != null) {
            loop.cancel();
        }
        if (timeout != null) {
            timeout.cancel();
        }
    }

    static String norm(String s) {
        String t = Normalizer.normalize(s.toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return t.replaceAll("[^a-z0-9 -]", "").replaceAll("\\s+", " ").trim();
    }

    private String pickKind() {
        ConfigurationSection w = cfg.getConfigurationSection("weights");
        List<String> kinds = KINDS;
        int total = 0;
        for (String k : kinds) {
            total += w == null ? 1 : Math.max(0, w.getInt(k, 1));
        }
        int r = rnd.nextInt(Math.max(1, total));
        for (String k : kinds) {
            r -= w == null ? 1 : Math.max(0, w.getInt(k, 1));
            if (r < 0) {
                return k;
            }
        }
        return "toan";
    }

    /** Xáo chữ trong từng tiếng (giữ thứ tự tiếng), đảm bảo khác bản gốc nếu được. */
    private String scramble(String phrase) {
        StringBuilder out = new StringBuilder();
        for (String word : phrase.split(" ")) {
            List<Integer> cps = new ArrayList<>(word.codePoints().boxed().toList());
            String orig = word;
            String mixed = word;
            for (int i = 0; i < 8 && mixed.equals(orig) && cps.size() > 1; i++) {
                Collections.shuffle(cps, rnd);
                StringBuilder b = new StringBuilder();
                cps.forEach(b::appendCodePoint);
                mixed = b.toString();
            }
            out.append(out.isEmpty() ? "" : " ").append(mixed);
        }
        return out.toString();
    }

    /** Ra một câu (kind null = ngẫu nhiên theo weights). Trả false nếu đang có câu hoặc thiếu dữ liệu. */
    boolean ask(String forced) {
        if (answers != null) {
            return false;
        }
        String k = forced != null ? forced : pickKind();
        String head;
        String body;
        List<String> ans = new ArrayList<>();
        switch (k) {
            case "xaotron" -> {
                if (pool.isEmpty()) {
                    return false;
                }
                shown = fresh(pool);
                head = "Xếp lại chữ cho đúng!";
                body = "&6Từ khoá: &f" + scramble(shown);
                ans.add(shown);
            }
            case "dienchu" -> {
                if (pool.isEmpty()) {
                    return false;
                }
                shown = fresh(pool);
                StringBuilder b = new StringBuilder();
                int[] cps = shown.codePoints().toArray();
                for (int i = 0; i < cps.length; i++) {
                    // che ~45% chữ cái, giữ chữ đầu mỗi chữ; 30/10: cách từng ký tự, chỗ trống vàng cho dễ nhìn
                    boolean first = i == 0 || cps[i - 1] == ' ';
                    if (cps[i] == ' ') {
                        b.append("   ");
                        continue;
                    }
                    b.append(!first && rnd.nextInt(100) < 45 ? "&e_" : "&f" + new String(Character.toChars(cps[i]))).append(' ');
                }
                head = "Điền chữ còn thiếu!";
                body = "&6Gợi ý: " + b.toString().trim() + " &b(" + shown.split(" ").length + " chữ)";
                ans.add(shown);
            }
            case "daonguoc" -> {
                if (pool.isEmpty()) {
                    return false;
                }
                shown = fresh(pool);
                head = "Đọc ngược lại là gì?";
                body = "&6Chữ ngược: &f" + new StringBuilder(shown).reverse();
                ans.add(shown);
            }
            case "dayso" -> {
                int start = rnd.nextInt(1, 30), step = rnd.nextInt(2, 12), form = rnd.nextInt(3);
                List<Integer> seq = new ArrayList<>();
                int v = start;
                for (int i = 0; i < 5; i++) {
                    seq.add(v);
                    v = form == 0 ? v + step : form == 1 ? v * 2 : v + step + i;   // cộng đều / nhân đôi / cộng tăng dần
                }
                head = "Số tiếp theo là gì?";
                body = "&6Dãy: &f" + seq.subList(0, 4).toString().replaceAll("[\\[\\]]", "") + ", ?";
                shown = String.valueOf(seq.get(4));
                ans.add(shown);
            }
            case "gonhanh" -> {
                List<String> ph = cfg.getStringList("phrases");
                if (ph.isEmpty()) {
                    return false;
                }
                shown = fresh(ph);
                head = "Ai gõ nhanh nhất?";
                body = "&6Gõ lại: &f" + shown;
                ans.add(shown);
            }
            case "caudo" -> {
                List<Map<?, ?>> rs = cfg.getMapList("riddles");
                if (rs.isEmpty()) {
                    return false;
                }
                List<String> qs = rs.stream().map(x -> String.valueOf(x.get("q"))).toList();
                String q0 = fresh(qs);
                Map<?, ?> r = rs.get(qs.indexOf(q0));
                head = "Câu đố vui!";
                body = "&f" + r.get("q");
                if (r.get("a") instanceof List<?> l) {
                    l.forEach(x -> ans.add(String.valueOf(x)));
                } else {
                    ans.add(String.valueOf(r.get("a")));
                }
                shown = ans.get(0);
            }
            default -> {
                k = "toan";
                int a = rnd.nextInt(5, 99), b = rnd.nextInt(2, 60), c = rnd.nextInt(2, 10), op = rnd.nextInt(5);
                int x = a % 12 + 2, y = b % 9 + 2;
                int res = switch (op) {
                    case 0 -> a + b;
                    case 1 -> a - b;
                    case 2 -> x * y;
                    case 3 -> a + x * c;
                    default -> x * y * c / c;   // chia hết: (x*y) : y
                };
                String q = switch (op) {
                    case 0 -> a + " + " + b;
                    case 1 -> a + " - " + b;
                    case 2 -> x + " × " + y;
                    case 3 -> a + " + " + x + " × " + c;
                    default -> (x * y) + " : " + y;
                };
                if (op == 4) {
                    res = x;
                }
                head = "Tính nhanh!";
                body = "&6Phép tính: &f" + q + " = ?";
                shown = String.valueOf(res);
                ans.add(shown);
            }
        }
        kind = k;
        answers = ans.stream().map(ChatGames::norm).toList();
        int secs = Math.max(15, cfg.getInt("timeout", 60));
        broadcast(LINE, "&d✦ DuckyMoMo - Đố vui &e(" + label(k) + ")", "&e" + head, body,
                "&fGõ đáp án vào chat &7· &e" + secs + " giây &7· &fthưởng &a" + VaultEconomy.format(cfg.getDouble("reward-min", 50))
                        + "&f - &a" + VaultEconomy.format(cfg.getDouble("reward-max", 150)), LINE);
        timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (answers != null) {
                answers = null;
                broadcast(LINE, "&d✦ Đố vui &7- hết giờ, chưa ai trả lời đúng.", "&fĐáp án: &d" + shown, LINE);
            }
        }, secs * 20L);
        return true;
    }

    private static String label(String k) {
        return switch (k) {
            case "xaotron" -> "xáo trộn";
            case "gonhanh" -> "gõ nhanh";
            case "caudo" -> "câu đố";
            case "dienchu" -> "điền chữ";
            case "daonguoc" -> "đọc ngược";
            case "dayso" -> "dãy số";
            default -> "toán";
        };
    }

    private static void broadcast(String... lines) {
        for (String l : lines) {
            Bukkit.broadcast(Tip.c(l));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        List<String> a = answers;
        if (a == null || BotView.isBot(e.getPlayer())) {
            return;
        }
        String said = norm(PlainTextComponentSerializer.plainText().serialize(e.message()));
        if (!a.contains(said)) {
            return;
        }
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> win(p));
    }

    private void win(Player p) {
        if (answers == null || !p.isOnline()) {
            return;   // người khác đã thắng trước (2 người gõ cùng lúc)
        }
        answers = null;
        if (timeout != null) {
            timeout.cancel();
        }
        double lo = cfg.getDouble("reward-min", 50), hi = Math.max(lo, cfg.getDouble("reward-max", 150));
        double prize = Math.round(lo + rnd.nextDouble() * (hi - lo));
        VaultEconomy.deposit(p, prize);
        ScreenNotify.show(p, "&a+" + VaultEconomy.format(prize), "&7Trả lời đúng đầu tiên!");
        broadcast(LINE, "&d✦ DuckyMoMo - Đố vui &e(" + label(kind) + ")",
                "&a" + p.getName() + " &dtrả lời đúng đầu tiên, nhận &a" + VaultEconomy.format(prize) + "&d!", "&fĐáp án: &d" + shown, LINE);
    }

    void command(CommandSender s, String[] args) {
        String k = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : null;
        if (k != null && !KINDS.contains(k)) {
            s.sendMessage(Tip.c("&e/dmm minigame [" + String.join("|", KINDS) + "] &7ra câu đố ngay · kho từ: " + pool.size()));
            return;
        }
        s.sendMessage(Tip.c(ask(k) ? "&aĐã ra câu đố." : "&cĐang có câu đố chạy, hoặc kiểu này chưa có dữ liệu trong config."));
    }
}
