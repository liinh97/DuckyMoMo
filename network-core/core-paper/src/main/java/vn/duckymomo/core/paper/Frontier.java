package vn.duckymomo.core.paper;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Khai hoang vùng mới (/khaihoang, ý từ "Khai mở thứ nguyên" của UniMC): cả server cùng góp vào một quỹ chung, đủ mốc thì mở vùng đất mới
 * (lệnh console của mốc, vd nới viền ChunkyBorder).
 * Quỹ tăng khi: bán đồ cho server bằng /sell (sell-percent % số tiền bán, người bán KHÔNG mất gì) và góp thẳng /khaihoang gop <xu>
 * (tiền bị rút = rút bớt tiền khỏi kinh tế). Mốc theo thứ tự trong goals: {id, name, need, commands, message}.
 * Lưu: khaihoang.yml (quy, gop.<uuid>.{ten,xu}, dat: [id đã mở]). Placeholder %dmmkh_*% cho hologram ở spawn.
 * Vùng mới: rtp.zones có "requires: <id mốc>" chỉ hiện ở /tpr và tính đặc sản khi mốc đã mở (Frontier.isUnlocked).
 * Người Tiên Phong (pioneer): pioneer.count người ĐẦU TIÊN đặt chân vào vùng mới (bán kính zone) nhận pioneer.rewards (quà chung Rewards,
 * vd "xu 2000", "danhhieu tienphong") — lưu tienphong.<mốc>: [uuid].
 */
final class Frontier implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    record Goal(String id, String name, double need, List<String> commands, String message) { }

    /** Mốc khai hoang đã mở chưa (trống = không cần mốc). Gọi qua Shared.unlocked để server không có khai hoang khỏi nạp lớp này. */
    static boolean isUnlocked(String goalId) {
        return Shared.unlocked(goalId);
    }

    boolean isReached(String goalId) {
        return reached().contains(goalId);
    }

    private record Area(String goal, String name, int x, int z, int r) { }

    private final List<Area> areas = new ArrayList<>();
    private Rewards rewards;

    private final JavaPlugin plugin;
    private final ConfigurationSection cfg;
    private final List<Goal> goals = new ArrayList<>();
    private final File file;
    private final YamlConfiguration data;
    private boolean dirty;

    Frontier(JavaPlugin plugin, ConfigurationSection cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
        for (Map<?, ?> m : cfg.getMapList("goals")) {
            Object id = m.get("id");
            Object need = m.get("need");
            if (id == null || !(need instanceof Number n) || n.doubleValue() <= 0) {
                continue;
            }
            List<String> cmds = new ArrayList<>();
            if (m.get("commands") instanceof List<?> l) {
                for (Object o : l) {
                    cmds.add(String.valueOf(o));
                }
            }
            goals.add(new Goal(String.valueOf(id), String.valueOf(m.get("name") == null ? id : m.get("name")), n.doubleValue(), cmds,
                    m.get("message") == null ? "" : String.valueOf(m.get("message"))));
        }
        this.file = new File(plugin.getDataFolder(), "khaihoang.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> z : plugin.getConfig().getMapList("rtp.zones")) {
            if (z.get("requires") != null && z.get("radius") instanceof Number r && r.intValue() > 0) {
                areas.add(new Area(String.valueOf(z.get("requires")), String.valueOf(z.get("name")), ((Number) z.get("center-x")).intValue(),
                        ((Number) z.get("center-z")).intValue(), r.intValue()));
            }
        }
    }

    void setRewards(Rewards r) {
        this.rewards = r;
    }

    /** Mỗi 5 giây: ai đặt chân vào vùng mới đã mở, còn suất Tiên Phong thì nhận quà. */
    private void pioneerTick() {
        int count = cfg.getInt("pioneer.count", 10);
        List<String> gifts = cfg.getStringList("pioneer.rewards");
        if (count <= 0 || areas.isEmpty()) {
            return;
        }
        for (Area a : areas) {
            if (!isUnlocked(a.goal())) {
                continue;
            }
            List<String> got = new ArrayList<>(data.getStringList("tienphong." + a.goal()));
            if (got.size() >= count) {
                continue;
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getGameMode() != org.bukkit.GameMode.SURVIVAL || got.contains(p.getUniqueId().toString())) {
                    continue;
                }
                double dx = p.getLocation().getX() - a.x(), dz = p.getLocation().getZ() - a.z();
                if (dx * dx + dz * dz > (double) a.r() * a.r()) {
                    continue;
                }
                got.add(p.getUniqueId().toString());
                data.set("tienphong." + a.goal(), got);
                dirty = true;
                List<String> desc = rewards == null ? List.of() : rewards.give(p, gifts);
                Bukkit.broadcast(c("&a⚑ &f" + p.getName() + " &alà Người Tiên Phong thứ " + got.size() + "/" + count + " đặt chân tới " + a.name()
                        + (desc.isEmpty() ? "" : " &7(" + String.join(", ", desc) + ")")));
                if (got.size() >= count) {
                    break;
                }
            }
        }
    }

    void start() {
        hookSell();
        // lưu gộp mỗi 30 giây (bán /sell rất nhiều lần)
        Bukkit.getScheduler().runTaskTimer(plugin, this::flush, 600L, 600L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::pioneerTick, 200L, 100L);
        Shared.frontier = this;
    }

    void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được khaihoang.yml: " + e.getMessage());
        }
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s);
    }

    private static String money(double v) {
        return VaultEconomy.available() ? VaultEconomy.format(v) : String.format(Locale.ROOT, "%,.0f xu", v);
    }

    double fund() {
        return data.getDouble("quy");
    }

    private List<String> reached() {
        return data.getStringList("dat");
    }

    /** Mốc tiếp theo chưa mở (null = đã mở hết). */
    Goal next() {
        List<String> done = reached();
        for (Goal g : goals) {
            if (!done.contains(g.id())) {
                return g;
            }
        }
        return null;
    }

    private void add(OfflinePlayer p, double amount) {
        if (amount <= 0) {
            return;
        }
        data.set("quy", fund() + amount);
        if (p != null) {
            String k = "gop." + p.getUniqueId();
            data.set(k + ".ten", p.getName());
            data.set(k + ".xu", data.getDouble(k + ".xu") + amount);
        }
        dirty = true;
        check();
    }

    private void check() {
        Goal g = next();
        while (g != null && fund() >= g.need()) {
            unlock(g);
            g = next();
        }
    }

    private void unlock(Goal g) {
        List<String> done = new ArrayList<>(reached());
        done.add(g.id());
        data.set("dat", done);
        dirty = true;
        flush();
        plugin.getLogger().info("Khai hoang: đạt mốc " + g.id() + " (quỹ " + fund() + ")");
        for (String cmd : g.commands()) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Khai hoang: lệnh lỗi \"" + cmd + "\": " + e.getMessage());
            }
        }
        Title t = Title.title(c("&a&lKHAI HOANG THÀNH CÔNG"), c(g.name()),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(5), Duration.ofSeconds(1)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(t);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        }
        Bukkit.broadcast(c("&6[Khai hoang] &fCả server đã góp đủ &a" + money(g.need()) + "&f: " + g.name()
                + (g.message().isBlank() ? "" : "&f. " + g.message())));
        for (Area a : areas) {
            if (a.goal().equals(g.id())) {
                Bukkit.broadcast(c("&a⚑ Vùng mới " + a.name() + " &ađã có ở &f/tpr&a. " + cfg.getInt("pioneer.count", 10)
                        + " người đặt chân tới đầu tiên là &lNgười Tiên Phong&r&a, có quà!"));
            }
        }
    }

    /** /sell của Essentials: cộng sell-percent % số tiền bán vào quỹ (không trừ người bán). Gọi qua reflection như PeaceAlliance. */
    @SuppressWarnings("unchecked")
    private void hookSell() {
        double pct = cfg.getDouble("sell-percent", 100);
        Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
        if (ess == null || !ess.isEnabled() || pct <= 0) {
            return;
        }
        try {
            Class<? extends Event> cls = (Class<? extends Event>) Class.forName("net.ess3.api.events.UserBalanceUpdateEvent", true, ess.getClass().getClassLoader());
            Method cause = cls.getMethod("getCause");
            Method player = cls.getMethod("getPlayer");
            Method oldB = cls.getMethod("getOldBalance");
            Method newB = cls.getMethod("getNewBalance");
            Bukkit.getPluginManager().registerEvent(cls, this, EventPriority.MONITOR, (l, ev) -> {
                if (!cls.isInstance(ev)) {
                    return;
                }
                try {
                    if (!"COMMAND_SELL".equals(String.valueOf(cause.invoke(ev)))) {
                        return;
                    }
                    Player p = (Player) player.invoke(ev);
                    BigDecimal gain = ((BigDecimal) newB.invoke(ev)).subtract((BigDecimal) oldB.invoke(ev));
                    if (p != null && gain.signum() > 0) {
                        add(p, gain.doubleValue() * pct / 100.0);
                    }
                } catch (ReflectiveOperationException ex) {
                    plugin.getLogger().warning("Khai hoang: đếm tiền bán lỗi: " + ex);
                }
            }, plugin, true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Khai hoang: không gắn được /sell của Essentials: " + e);
        }
    }

    private static String bar(double pct, int cells) {
        int on = (int) Math.max(0, Math.min(cells, Math.floor(pct / 100.0 * cells)));
        return "&a" + "■".repeat(on) + "&8" + "■".repeat(cells - on);
    }

    private List<Map.Entry<String, Double>> top(int n) {
        ConfigurationSection s = data.getConfigurationSection("gop");
        List<Map.Entry<String, Double>> out = new ArrayList<>();
        if (s != null) {
            for (String k : s.getKeys(false)) {
                out.add(Map.entry(s.getString(k + ".ten", "?"), s.getDouble(k + ".xu")));
            }
        }
        out.sort(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()));
        return out.subList(0, Math.min(n, out.size()));
    }

    boolean command(CommandSender s, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("gop")) {
            if (!(s instanceof Player p) || !VaultEconomy.available()) {
                s.sendMessage("Chỉ người chơi góp được (cần hệ thống tiền).");
                return true;
            }
            double amount;
            try {
                amount = Math.floor(Double.parseDouble(args.length > 1 ? args[1] : "0"));
            } catch (NumberFormatException e) {
                amount = 0;
            }
            double min = cfg.getDouble("min-donate", 10);
            if (amount < min) {
                p.sendMessage(c("&e/khaihoang gop <xu> &7(ít nhất " + money(min) + ")"));
                return true;
            }
            if (next() == null) {
                p.sendMessage(c("&7Đã mở hết các vùng của mùa này, cảm ơn bạn!"));
                return true;
            }
            if (!VaultEconomy.withdraw(p, amount)) {
                p.sendMessage(c("&cKhông đủ tiền."));
                return true;
            }
            p.sendMessage(c("&a✔ Đã góp &f" + money(amount) + " &avào quỹ khai hoang. Cảm ơn!"));
            add(p, amount);
            return true;
        }
        if (sub.equals("them") && s.hasPermission("duckymomo.admin")) {
            try {
                add(null, Double.parseDouble(args[1]));
                s.sendMessage("Quỹ khai hoang: " + fund());
            } catch (RuntimeException e) {
                s.sendMessage("/khaihoang them <xu>");
            }
            return true;
        }
        Goal g = next();
        s.sendMessage(c("&6&l━━ KHAI HOANG VÙNG MỚI ━━"));
        s.sendMessage(c("&7Cả server góp chung: bán đồ bằng &f/sell &7(được tính " + Math.round(cfg.getDouble("sell-percent", 100))
                + "% tiền bán, bạn không mất gì) hoặc &f/khaihoang gop <xu>&7."));
        s.sendMessage(c("&7Quỹ hiện có: &a" + money(fund())));
        if (!areas.isEmpty()) {
            s.sendMessage(c("&7Mỗi mốc mở: vùng mới ở &f/tpr&7, đặc sản riêng (&f/dacsan&7), &a" + cfg.getInt("pioneer.count", 10)
                    + " người đặt chân tới đầu tiên &7là &aNgười Tiên Phong &7(có quà, danh hiệu)."));
        }
        List<String> done = reached();
        for (Goal x : goals) {
            boolean ok = done.contains(x.id());
            double pct = Math.min(100, fund() * 100 / x.need());
            s.sendMessage(c((ok ? "&a✔ " : x == g ? "&e➤ " : "&8○ ") + "&f" + x.name() + " &7" + (ok ? "(đã mở)"
                    : money(x.need()) + " " + bar(pct, 10) + " &f" + String.format(Locale.ROOT, "%.1f", pct) + "%")));
        }
        List<Map.Entry<String, Double>> t = top(5);
        if (!t.isEmpty()) {
            StringBuilder sb = new StringBuilder("&7Góp nhiều nhất: ");
            for (int i = 0; i < t.size(); i++) {
                sb.append(i == 0 ? "" : "&7, ").append("&f").append(t.get(i).getKey()).append(" &8(").append(money(t.get(i).getValue())).append(")");
            }
            s.sendMessage(c(sb.toString()));
        }
        return true;
    }

    PlaceholderExpansion placeholders() {
        return new PlaceholderExpansion() {
            @Override
            public String getIdentifier() {
                return "dmmkh";
            }

            @Override
            public String getAuthor() {
                return "DuckyMoMo";
            }

            @Override
            public String getVersion() {
                return "1";
            }

            @Override
            public boolean persist() {
                return true;
            }

            @Override
            public String onRequest(OfflinePlayer player, String params) {
                Goal g = next();
                double pct = g == null ? 100 : Math.min(100, fund() * 100 / g.need());
                return switch (params) {
                    case "quy" -> money(fund());
                    case "ten" -> g == null ? "Đã mở hết" : LegacyComponentSerializer.legacySection().serialize(c(g.name()));
                    case "can" -> g == null ? "" : money(g.need());
                    case "pct" -> String.format(Locale.ROOT, "%.1f", pct);
                    case "thanh" -> LegacyComponentSerializer.legacySection().serialize(c(bar(pct, 12)));
                    case "toi" -> {
                        if (player == null) {
                            yield "0";
                        }
                        yield money(data.getDouble("gop." + player.getUniqueId() + ".xu"));
                    }
                    default -> null;
                };
            }
        };
    }
}
