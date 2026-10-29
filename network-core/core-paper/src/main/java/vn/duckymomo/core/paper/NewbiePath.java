package vn.duckymomo.core.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Con đường tân thủ /tanthu (29/10, chủ server: người mới vào mò lệnh vài phút rồi thoát). Chuỗi bước ngắn làm theo thứ tự, mỗi bước
 * thưởng ngay, thanh tiến độ (boss bar) luôn hiện bước đang làm + cách làm; xong hết có quà cuối và chỉ sang Việc dân làng / nhiệm vụ.
 * Ai được: người vào server LẦN ĐẦU sau khi bật (hasPlayedBefore = false), không tính bot; admin: /tanthu batdau <tên>.
 * Loại bước (newbie-path.steps): feature (mã DailyQuests.feature hoặc mã riêng: gialang = hỏi Già Làng mở Việc dân làng,
 * rtp = /rtp tới nơi, sethome = gõ /sethome), break (targets vật liệu, *_LOG kiểu đuôi), craft (vật liệu chế ra), kill (@hostile).
 * Lưu plugins/DuckyMoMoCore/tanthu.yml: players.<uuid>: {b: bước đang làm (0..), dem: tiến độ, an: ẩn thanh, xong: true}.
 */
final class NewbiePath implements Listener {
    static volatile NewbiePath INSTANCE;

    private record Step(String type, List<String> targets, int amount, String name, String how, String cmd, List<String> rewards) { }

    private final JavaPlugin plugin;
    private final Rewards rewards;
    private final List<Step> steps = new ArrayList<>();
    private final List<String> finalRewards;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private boolean dirty;

    NewbiePath(JavaPlugin plugin, ConfigurationSection cfg, Rewards rewards) {
        INSTANCE = this;
        this.plugin = plugin;
        this.rewards = rewards;
        for (Map<?, ?> m : cfg.getMapList("steps")) {
            Object t = m.get("target");
            List<String> targets = new ArrayList<>();
            if (t instanceof List<?> l) {
                l.forEach(x -> targets.add(String.valueOf(x).toUpperCase(Locale.ROOT)));
            } else if (t != null) {
                targets.add(String.valueOf(t).toUpperCase(Locale.ROOT));
            }
            List<String> rw = new ArrayList<>();
            if (m.get("rewards") instanceof List<?> l) {
                l.forEach(x -> rw.add(String.valueOf(x)));
            }
            Object amt = m.get("amount");
            steps.add(new Step(String.valueOf(m.get("type")).toLowerCase(Locale.ROOT), targets,
                    amt instanceof Number n ? Math.max(1, n.intValue()) : 1, String.valueOf(m.get("name")),
                    m.get("how") == null ? "" : String.valueOf(m.get("how")), m.get("cmd") == null ? null : String.valueOf(m.get("cmd")), rw));
        }
        this.finalRewards = cfg.getStringList("final-rewards");
        this.file = new File(plugin.getDataFolder(), "tanthu.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {   // ghi file mỗi 30 giây nếu có đổi; dmm reload tạo bản mới thì bản cũ dừng
            if (INSTANCE != this) {
                task.cancel();
                return;
            }
            if (dirty) {
                save();
            }
        }, 600L, 600L);
        for (Player p : Bukkit.getOnlinePlayers()) {
            refresh(p);
        }
    }

    void shutdown() {
        bars.values().forEach(BossBar::removeAll);
        bars.clear();
        save();
        if (INSTANCE == this) {
            INSTANCE = null;
        }
    }

    private void save() {
        dirty = false;
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được tanthu.yml: " + e.getMessage());
        }
    }

    private static String key(UUID u) {
        return "players." + u;
    }

    /** Đang trong chuỗi (chưa xong). */
    private boolean active(UUID u) {
        String k = key(u);
        return data.contains(k) && !data.getBoolean(k + ".xong");
    }

    private void start(Player p) {
        String k = key(p.getUniqueId());
        data.set(k + ".name", p.getName());
        data.set(k + ".b", 0);
        data.set(k + ".dem", 0);
        data.set(k + ".xong", null);
        data.set(k + ".an", null);
        dirty = true;
        refresh(p);
    }

    // ------------------------------------------------------------------ thanh tiến độ + chat

    private static String col(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private void refresh(Player p) {
        UUID u = p.getUniqueId();
        String k = key(u);
        if (!active(u) || data.getBoolean(k + ".an") || steps.isEmpty()) {
            BossBar b = bars.remove(u);
            if (b != null) {
                b.removeAll();
            }
            return;
        }
        int bi = Math.min(data.getInt(k + ".b"), steps.size() - 1);
        Step s = steps.get(bi);
        int dem = data.getInt(k + ".dem");
        String title = "&eTân thủ " + (bi + 1) + "/" + steps.size() + " &8· &f" + s.name()
                + (s.amount() > 1 ? " &7(" + Math.min(dem, s.amount()) + "/" + s.amount() + ")" : "")
                + (s.rewards().isEmpty() ? "" : " &8· &6+" + String.join(", ", rewards.describe(s.rewards())));
        BossBar bar = bars.computeIfAbsent(u, x -> {
            BossBar nb = Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SEGMENTED_10);
            nb.addPlayer(p);
            return nb;
        });
        bar.setTitle(col(title));
        bar.setProgress(Math.max(0, Math.min(1, (bi + Math.min(1.0, dem / (double) s.amount())) / steps.size())));
    }

    private void tellStep(Player p, int bi) {
        if (bi >= steps.size()) {
            return;
        }
        Step s = steps.get(bi);
        Component line = Tip.c("&e➜ Bước " + (bi + 1) + "/" + steps.size() + ": &f" + s.name() + (s.how().isEmpty() ? "" : " &7- " + s.how()));
        if (s.cmd() != null) {
            line = line.append(Tip.c(" &a&n[" + s.cmd() + "]").clickEvent(ClickEvent.runCommand(s.cmd()))
                    .hoverEvent(HoverEvent.showText(Tip.c("&7Bấm để gõ &f" + s.cmd()))));
        }
        p.sendMessage(line);
    }

    // ------------------------------------------------------------------ tiến độ

    private void progress(Player p, String type, String what, int n) {
        UUID u = p.getUniqueId();
        if (!active(u) || BotView.isBot(p)) {
            return;
        }
        String k = key(u);
        int bi = data.getInt(k + ".b");
        if (bi >= steps.size()) {
            return;
        }
        Step s = steps.get(bi);
        if (!s.type().equals(type) || !matches(s.targets(), what)) {
            return;
        }
        int dem = data.getInt(k + ".dem") + n;
        dirty = true;
        if (dem < s.amount()) {
            data.set(k + ".dem", dem);
            refresh(p);
            return;
        }
        advance(p, bi, true);
    }

    private static boolean matches(List<String> targets, String what) {
        if (targets.isEmpty() || targets.contains("*")) {
            return true;
        }
        if (what.contains("|")) {
            for (String w : what.split("\\|")) {
                if (matches(targets, w)) {
                    return true;
                }
            }
            return false;
        }
        for (String t : targets) {
            if (t.equals(what) || (t.startsWith("*") && what.endsWith(t.substring(1))) || (t.endsWith("*") && what.startsWith(t.substring(0, t.length() - 1)))) {
                return true;
            }
        }
        return false;
    }

    /** Xong bước bi (paid = có thưởng; bỏ qua thì không). */
    private void advance(Player p, int bi, boolean paid) {
        String k = key(p.getUniqueId());
        Step s = steps.get(bi);
        List<String> got = paid ? rewards.give(p, s.rewards()) : List.of();
        data.set(k + ".b", bi + 1);
        data.set(k + ".dem", 0);
        dirty = true;
        if (paid) {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
            p.sendMessage(Tip.c("&a✔ Xong: &f" + s.name() + (got.isEmpty() ? "" : " &7· nhận &e" + String.join("&7, &e", got))));
        }
        if (bi + 1 >= steps.size()) {
            data.set(k + ".xong", true);
            List<String> fin = rewards.give(p, finalRewards);
            p.showTitle(net.kyori.adventure.title.Title.title(Tip.c("&6Xong đường tân thủ!"), Tip.c("&7Giờ bạn đã quen làng rồi")));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
            p.sendMessage(Tip.c("&6★ Hoàn thành đường tân thủ!" + (fin.isEmpty() ? "" : " &7Quà: &e" + String.join("&7, &e", fin))));
            p.sendMessage(Tip.c("&7Tiếp theo: giúp dân làng (&f/nhiemvu&7), lập hoặc vào thị trấn (&f/t new <tên>&7, &f/t list&7), điểm danh mỗi ngày đổi set &6Chuyên Cần&7."));
        } else {
            p.showTitle(net.kyori.adventure.title.Title.title(Tip.c("&a✔ " + s.name()), Tip.c("&7Tiếp: &f" + steps.get(bi + 1).name())));
            tellStep(p, bi + 1);
        }
        refresh(p);
    }

    /** Module khác báo người chơi vừa dùng một tính năng (gọi kèm trong DailyQuests.feature). */
    static void feature(Player p, String id) {
        NewbiePath n = INSTANCE;
        if (n != null) {
            n.progress(p, "feature", id.toUpperCase(Locale.ROOT), 1);
        }
    }

    // ------------------------------------------------------------------ sự kiện

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (BotView.isBot(p)) {
            return;
        }
        boolean fresh = !p.hasPlayedBefore();   // lần đầu vào (kể cả sau /choilai reset như người mới): làm lại từ đầu
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            if (fresh) {
                start(p);
                p.sendMessage(Tip.c("&6✦ Chào mừng tới Đại Lục Sương Mai! &7Làm theo &eđường tân thủ&7 trên đầu màn hình, mỗi bước đều có thưởng. &8(/tanthu)"));
                tellStep(p, 0);
            } else if (active(p.getUniqueId())) {
                refresh(p);
                tellStep(p, data.getInt(key(p.getUniqueId()) + ".b"));
            }
        }, 60L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        BossBar b = bars.remove(e.getPlayer().getUniqueId());
        if (b != null) {
            b.removeAll();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        progress(e.getPlayer(), "break", e.getBlock().getType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            progress(p, "craft", e.getRecipe().getResult().getType().name(), 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k != null) {
            // quái thù địch khớp cả "@HOSTILE" lẫn tên loài
            progress(k, "kill", (e.getEntity() instanceof Monster ? "@HOSTILE|" : "") + e.getEntityType().name(), 1);
        }
    }

    private static final Set<String> SETHOME = Set.of("sethome", "esethome", "createhome", "ecreatehome");

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCmd(PlayerCommandPreprocessEvent e) {
        String root = e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        root = root.substring(root.indexOf(':') + 1);
        if (SETHOME.contains(root)) {
            Player p = e.getPlayer();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) {
                    feature(p, "sethome");
                }
            }, 2L);
        }
    }

    // ------------------------------------------------------------------ /tanthu

    boolean command(CommandSender s, String[] a) {
        if (a.length >= 2 && a[0].equalsIgnoreCase("batdau") && s.hasPermission("duckymomo.admin")) {
            Player t = Bukkit.getPlayerExact(a[1]);
            if (t == null) {
                s.sendMessage("Người chơi phải đang online.");
                return true;
            }
            start(t);
            tellStep(t, 0);
            s.sendMessage("Đã bắt đầu đường tân thủ cho " + t.getName());
            return true;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("/tanthu batdau <tên>: cho người chơi làm lại đường tân thủ");
            return true;
        }
        UUID u = p.getUniqueId();
        String k = key(u);
        if (!data.contains(k)) {
            p.sendMessage(Tip.c("&7Đường tân thủ dành cho người mới vào lần đầu. Bạn có thể xem &f/huongdan&7 và &f/nhiemvu&7."));
            return true;
        }
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        if (data.getBoolean(k + ".xong")) {
            p.sendMessage(Tip.c("&a✔ Bạn đã xong đường tân thủ. &7Tiếp: &f/nhiemvu&7, &f/t list&7, &f/diemdanh&7."));
            return true;
        }
        switch (sub) {
            case "an" -> {
                data.set(k + ".an", true);
                dirty = true;
                refresh(p);
                p.sendMessage(Tip.c("&7Đã ẩn thanh tân thủ. Hiện lại: &f/tanthu hien"));
            }
            case "hien" -> {
                data.set(k + ".an", null);
                dirty = true;
                refresh(p);
                p.sendMessage(Tip.c("&7Đã hiện thanh tân thủ."));
            }
            case "boqua" -> {
                int bi = data.getInt(k + ".b");
                if (bi < steps.size()) {
                    p.sendMessage(Tip.c("&7Đã bỏ qua: &f" + steps.get(bi).name() + " &8(không có thưởng bước này)"));
                    advance(p, bi, false);
                }
            }
            default -> {
                int bi = data.getInt(k + ".b");
                p.sendMessage(Tip.c("&6✦ Đường tân thủ &7(" + bi + "/" + steps.size() + " bước)"));
                for (int i = 0; i < steps.size(); i++) {
                    Step st = steps.get(i);
                    String mark = i < bi ? "&a✔ " : i == bi ? "&e➜ " : "&8• ";
                    p.sendMessage(Tip.c(mark + (i <= bi ? "&f" : "&7") + st.name() + (st.rewards().isEmpty() ? "" : " &8(" + String.join(", ", rewards.describe(st.rewards())) + ")")));
                }
                tellStep(p, bi);
                p.sendMessage(Tip.c("&8Kẹt bước nào: &7/tanthu boqua &8· ẩn thanh: &7/tanthu an"));
            }
        }
        return true;
    }

}
