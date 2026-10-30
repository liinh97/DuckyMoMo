package vn.duckymomo.core.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Hoạt động giả của bot botfarm (30/10, chủ server).
 *  - Hệ số giả theo giờ chơi người THẬT: người mới thấy bot hoạt động đủ, chơi lâu thì bớt dần (bot cứ lượn quanh người chơi lâu năm
 *    là bị nhận ra), lâu hẳn thì thôi. bot-gia.muc: [{gio, he-so}…] tăng dần theo gio: giờ chơi < gio thì lấy he-so, quá mức cuối = 0.
 *    Cả server (chat, chết giả, trả lời câu đố) lấy hệ số LỚN NHẤT trong số người thật đang online (người mới nhất quyết định);
 *    không có người thật nào = 1 (giữ như cũ). Việc nhắm một người (tpa giả, đi dạo) lấy hệ số của chính người đó.
 *  - /dmm botgia (RCON, botfarm hỏi): "BOTGIA all=<hệ số>;<tên>=<giờ>:<hệ số>;…"
 *  - /dmm chetgia <bot> [loai]: tin tử vong giả của bot (bot bất tử nên không chết thật), gửi như tin tử vong thật (người tắt
 *    "tuvong" ở /caidat không thấy). Không làm nếu có người thật trong 96 khối quanh bot (thấy bot vẫn đứng đó là lộ).
 *    In "CHETGIA OK <bot> <loai>" hoặc "CHETGIA KHONG <lý do>".
 */
final class BotFake {
    private BotFake() { }

    static volatile ConfigurationSection cfg;

    /** loại -> {khoá tin tử vong, thực thể (null = không có)} */
    private static final Map<String, String[]> KINDS = Map.ofEntries(
            Map.entry("lava", new String[] {"death.attack.lava", null}),
            Map.entry("roi", new String[] {"death.attack.fall", null}),
            Map.entry("duoi", new String[] {"death.attack.drown", null}),
            Map.entry("chay", new String[] {"death.attack.onFire", null}),
            Map.entry("nghet", new String[] {"death.attack.inWall", null}),
            Map.entry("zombie", new String[] {"death.attack.mob", "entity.minecraft.zombie"}),
            Map.entry("xuong", new String[] {"death.attack.arrow", "entity.minecraft.skeleton"}),
            Map.entry("creeper", new String[] {"death.attack.explosion.player", "entity.minecraft.creeper"}),
            Map.entry("nhen", new String[] {"death.attack.mob", "entity.minecraft.spider"}),
            Map.entry("enderman", new String[] {"death.attack.mob", "entity.minecraft.enderman"}));
    /** Bốc ngẫu nhiên khi không ghi loại: hay gặp lặp lại nhiều lần. */
    private static final List<String> RANDOM = List.of("lava", "lava", "lava", "roi", "roi", "zombie", "zombie", "xuong", "xuong", "creeper",
            "creeper", "nhen", "duoi", "chay", "nghet", "enderman");

    static double hours(org.bukkit.OfflinePlayer p) {
        return p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20.0 / 3600.0;
    }

    static double factor(org.bukkit.OfflinePlayer p) {
        double h = hours(p);
        ConfigurationSection c = cfg;
        List<Map<?, ?>> muc = c == null ? List.of() : c.getMapList("muc");
        if (muc.isEmpty()) {
            return h < 1 ? 1 : h < 4 ? 0.5 : h < 12 ? 0.2 : 0;
        }
        for (Map<?, ?> m : muc) {
            if (m.get("gio") instanceof Number g && h < g.doubleValue()) {
                return m.get("he-so") instanceof Number f ? Math.max(0, Math.min(1, f.doubleValue())) : 1;
            }
        }
        return 0;
    }

    /** Hệ số cả server: lớn nhất trong số người thật đang online; không có ai = 1. */
    static double factorAll() {
        double best = -1;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!BotView.isBot(p)) {
                best = Math.max(best, factor(p));
            }
        }
        return best < 0 ? 1 : best;
    }

    private static String num(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    static void command(CommandSender sender, String[] args) {
        if (args[0].equalsIgnoreCase("botgia")) {
            StringBuilder sb = new StringBuilder("BOTGIA all=").append(num(factorAll()));
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!BotView.isBot(p)) {
                    sb.append(';').append(p.getName()).append('=').append(num(hours(p))).append(':').append(num(factor(p)));
                }
            }
            sender.sendMessage(sb.toString());
            return;
        }
        // chetgia <bot> [loai]
        if (args.length < 2) {
            sender.sendMessage("CHETGIA KHONG cách dùng: /dmm chetgia <bot> [" + String.join("|", KINDS.keySet()) + "]");
            return;
        }
        Player bot = Bukkit.getPlayerExact(args[1]);
        if (bot == null || !BotView.isBot(bot)) {
            sender.sendMessage("CHETGIA KHONG " + args[1] + " không phải bot đang online");
            return;
        }
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!BotView.isBot(o) && o.getWorld().equals(bot.getWorld()) && o.getLocation().distanceSquared(bot.getLocation()) < 96 * 96) {
                sender.sendMessage("CHETGIA KHONG có người thật gần " + bot.getName());
                return;
            }
        }
        String kind = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : RANDOM.get(ThreadLocalRandom.current().nextInt(RANDOM.size()));
        String[] k = KINDS.get(kind);
        if (k == null) {
            sender.sendMessage("CHETGIA KHONG không có loại " + kind);
            return;
        }
        List<Component> with = new ArrayList<>();
        with.add(bot.displayName());
        if (k[1] != null) {
            with.add(Component.translatable(k[1]));
        }
        Component msg = Component.translatable(k[0], with);
        Bukkit.getConsoleSender().sendMessage(msg);
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o != bot && PlayerSettings.on(o, "tuvong")) {
                o.sendMessage(msg);
            }
        }
        sender.sendMessage("CHETGIA OK " + bot.getName() + " " + kind);
    }
}
