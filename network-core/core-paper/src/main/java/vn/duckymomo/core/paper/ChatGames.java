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
 * Kiểu (weights): xaotron (xáo chữ trong từng tiếng của một cụm từ), toan (cộng trừ nhân), gonhanh (gõ lại đúng câu),
 * cauddo (câu hỏi + đáp án trong config). Admin: /dmm minigame [kiểu] ra câu ngay.
 * Config chat-games: {enabled, interval-minutes, timeout, min-players, reward-min, reward-max, weights{}, words[], phrases[],
 *   riddles[{q, a[]}]}
 */
final class ChatGames implements Listener {
    private static final String LINE = "&8&m                                                     ";

    private final JavaPlugin plugin;
    private final ConfigurationSection cfg;
    private final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    private volatile List<String> answers;   // đáp án đã chuẩn hoá; null = không có câu đang chạy
    private String shown;                     // đáp án hiện khi công bố
    private String kind;
    private BukkitTask timeout;
    private BukkitTask loop;

    ChatGames(JavaPlugin plugin, ConfigurationSection cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
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
        List<String> kinds = List.of("xaotron", "toan", "gonhanh", "caudo");
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
                List<String> words = cfg.getStringList("words");
                if (words.isEmpty()) {
                    return false;
                }
                shown = words.get(rnd.nextInt(words.size()));
                head = "Xếp lại chữ cho đúng!";
                body = "&6Từ khoá: &f" + scramble(shown);
                ans.add(shown);
            }
            case "gonhanh" -> {
                List<String> ph = cfg.getStringList("phrases");
                if (ph.isEmpty()) {
                    return false;
                }
                shown = ph.get(rnd.nextInt(ph.size()));
                head = "Ai gõ nhanh nhất?";
                body = "&6Gõ lại: &f" + shown;
                ans.add(shown);
            }
            case "caudo" -> {
                List<Map<?, ?>> rs = cfg.getMapList("riddles");
                if (rs.isEmpty()) {
                    return false;
                }
                Map<?, ?> r = rs.get(rnd.nextInt(rs.size()));
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
                int a = rnd.nextInt(5, 60), b = rnd.nextInt(2, 40), op = rnd.nextInt(3);
                int res = op == 0 ? a + b : op == 1 ? a - b : (a % 12 + 2) * (b % 9 + 2);
                String q = op == 0 ? a + " + " + b : op == 1 ? a + " - " + b : (a % 12 + 2) + " × " + (b % 9 + 2);
                head = "Tính nhanh!";
                body = "&6Phép tính: &f" + q + " = ?";
                shown = String.valueOf(res);
                ans.add(shown);
            }
        }
        kind = k;
        answers = ans.stream().map(ChatGames::norm).toList();
        int secs = Math.max(15, cfg.getInt("timeout", 60));
        broadcast(LINE, "&d✦ DuckyMoMo - Đố vui &8(" + label(k) + ")", "&e" + head, body,
                "&7Gõ đáp án vào chat · " + secs + " giây · thưởng &a" + VaultEconomy.format(cfg.getDouble("reward-min", 50))
                        + "&7 - &a" + VaultEconomy.format(cfg.getDouble("reward-max", 150)), LINE);
        timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (answers != null) {
                answers = null;
                broadcast(LINE, "&d✦ Đố vui &7- hết giờ, chưa ai trả lời đúng.", "&7Đáp án: &d" + shown, LINE);
            }
        }, secs * 20L);
        return true;
    }

    private static String label(String k) {
        return switch (k) {
            case "xaotron" -> "xáo trộn";
            case "gonhanh" -> "gõ nhanh";
            case "caudo" -> "câu đố";
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
        broadcast(LINE, "&d✦ DuckyMoMo - Đố vui &8(" + label(kind) + ")",
                "&a" + p.getName() + " &dtrả lời đúng đầu tiên, nhận &a" + VaultEconomy.format(prize) + "&d!", "&7Đáp án: &d" + shown, LINE);
    }

    void command(CommandSender s, String[] args) {
        String k = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : null;
        if (k != null && !List.of("xaotron", "toan", "gonhanh", "caudo").contains(k)) {
            s.sendMessage(Tip.c("&e/dmm minigame [xaotron|toan|gonhanh|caudo] &7ra câu đố ngay"));
            return;
        }
        s.sendMessage(Tip.c(ask(k) ? "&aĐã ra câu đố." : "&cĐang có câu đố chạy, hoặc kiểu này chưa có dữ liệu trong config."));
    }
}
