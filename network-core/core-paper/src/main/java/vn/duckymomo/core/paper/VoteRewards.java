package vn.duckymomo.core.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Thưởng vote server trên các trang xếp hạng (Votifier/AzuVotifier, sự kiện gọi bằng reflection).
 * Vote → xu (Vault), vote đủ N ngày liên tiếp → thưởng thêm, mỗi M lượt toàn server → vote party cho người đang online.
 * /vote: link các trang + thành tích; /vote top: bảng vote tháng. Lưu: plugins/DuckyMoMoCore/vote.yml (trang web đọc file này).
 * Vote đến từ VPS: relay (socat 8192) → banghoi-1:8192. Không bán gì, chỉ thưởng xu trong game.
 */
final class VoteRewards implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final JavaPlugin plugin;
    private final ConfigurationSection cfg;
    private final File file;
    private final YamlConfiguration data;

    VoteRewards(JavaPlugin plugin, ConfigurationSection cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.file = new File(plugin.getDataFolder(), "vote.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s);
    }

    /** Gắn vào sự kiện vote của Votifier. Trả false nếu chưa cài Votifier. */
    @SuppressWarnings("unchecked")
    boolean hook() {
        Plugin v = Bukkit.getPluginManager().getPlugin("Votifier");
        if (v == null || !v.isEnabled()) {
            return false;
        }
        try {
            ClassLoader cl = v.getClass().getClassLoader();
            Class<? extends Event> cls = (Class<? extends Event>) Class.forName("com.vexsoftware.votifier.model.VotifierEvent", true, cl);
            Method getVote = cls.getMethod("getVote");
            Class<?> voteCls = Class.forName("com.vexsoftware.votifier.model.Vote", true, cl);
            Method user = voteCls.getMethod("getUsername");
            Method service = voteCls.getMethod("getServiceName");
            Bukkit.getPluginManager().registerEvent(cls, this, EventPriority.NORMAL, (l, ev) -> {
                if (!cls.isInstance(ev)) {
                    return;
                }
                try {
                    Object vote = getVote.invoke(ev);
                    String name = String.valueOf(user.invoke(vote)).trim();
                    String site = String.valueOf(service.invoke(vote));
                    if (Bukkit.isPrimaryThread()) {
                        onVote(name, site);
                    } else {
                        Bukkit.getScheduler().runTask(plugin, () -> onVote(name, site));
                    }
                } catch (ReflectiveOperationException e) {
                    plugin.getLogger().warning("Vote: " + e);
                }
            }, plugin);
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            plugin.getLogger().warning("Không gắn được vào Votifier: " + e);
            return false;
        }
    }

    /** Tìm người chơi theo tên đã từng vào server (tên Bedrock có dấu chấm đầu, trang vote có thể bỏ dấu chấm). */
    private static OfflinePlayer find(String name) {
        if (name.isEmpty()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer p = Bukkit.getOfflinePlayerIfCached(name);
        if (p == null && !name.startsWith(".")) {
            p = Bukkit.getOfflinePlayerIfCached("." + name);
        }
        return p != null && (p.isOnline() || p.hasPlayedBefore()) ? p : null;
    }

    void onVote(String name, String site) {
        OfflinePlayer p = find(name);
        plugin.getLogger().info("Vote từ " + site + " cho \"" + name + "\"" + (p == null ? " (không tìm thấy người chơi, bỏ qua)" : ""));
        if (p == null) {
            return;
        }
        String key = "players." + p.getUniqueId();
        LocalDate today = LocalDate.now(VN);
        String month = today.toString().substring(0, 7);

        // Chuỗi ngày: hôm qua có vote thì +1, hôm nay đã tính rồi thì giữ, cách quãng thì về 1
        String last = data.getString(key + ".last-day", "");
        int streak = data.getInt(key + ".streak", 0);
        boolean newDay = !last.equals(today.toString());
        if (newDay) {
            streak = last.equals(today.minusDays(1).toString()) ? streak + 1 : 1;
        }
        if (!month.equals(data.getString(key + ".month", ""))) {
            data.set(key + ".month", month);
            data.set(key + ".month-votes", 0);
        }
        data.set(key + ".name", p.getName() == null ? name : p.getName());
        data.set(key + ".last-day", today.toString());
        data.set(key + ".streak", streak);
        data.set(key + ".total", data.getInt(key + ".total", 0) + 1);
        data.set(key + ".month-votes", data.getInt(key + ".month-votes", 0) + 1);

        double money = cfg.getDouble("money-per-vote", 100);
        int streakDays = cfg.getInt("streak-days", 7);
        double bonus = newDay && streakDays > 0 && streak % streakDays == 0 ? cfg.getDouble("streak-bonus", 500) : 0;
        if (VaultEconomy.available()) {
            VaultEconomy.deposit(p, money + bonus);
        }
        String who = p.getName() == null ? name : p.getName();
        if (cfg.getBoolean("broadcast", true)) {
            Bukkit.broadcast(c("&a✔ &f" + who + " &avừa vote cho server (" + site + ") và nhận &e" + VaultEconomy.format(money)
                    + "&a. Gõ &f/vote &ađể nhận thưởng như vậy!"));
        }
        if (bonus > 0) {
            Bukkit.broadcast(c("&6★ " + who + " vote " + streak + " ngày liên tiếp: thưởng thêm &e" + VaultEconomy.format(bonus) + "&6!"));
        }

        int every = cfg.getInt("party-every", 30);
        int count = data.getInt("party.count", 0) + 1;
        if (every > 0 && count >= every) {
            count = 0;
            double each = cfg.getDouble("party-money", 200);
            for (Player o : Bukkit.getOnlinePlayers()) {
                VaultEconomy.deposit(o, each);
            }
            Bukkit.broadcast(c("&d&l🎉 VOTE PARTY! &r&dServer đạt " + every + " lượt vote: mọi người đang online nhận &e"
                    + VaultEconomy.format(each) + "&d. Cảm ơn cả nhà!"));
        }
        data.set("party.count", count);
        save();
    }

    boolean command(CommandSender s, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("top")) {
            String month = LocalDate.now(VN).toString().substring(0, 7);
            List<Map.Entry<String, Integer>> rows = monthTop(month);
            s.sendMessage(c("&6&l🏆 Bảng vote tháng " + month.substring(5) + "/" + month.substring(0, 4)));
            if (rows.isEmpty()) {
                s.sendMessage(c("&7Chưa ai vote tháng này. Gõ &f/vote &7để là người đầu tiên!"));
            }
            for (int i = 0; i < Math.min(10, rows.size()); i++) {
                s.sendMessage(c("&e" + (i + 1) + ". &f" + rows.get(i).getKey() + " &7- &a" + rows.get(i).getValue() + " lượt"));
            }
            return true;
        }
        s.sendMessage(c("&a&lVote cho DuckyMoMo &7(mỗi trang 1 lần/ngày, nhập đúng tên trong game)"));
        s.sendMessage(c("&7Mỗi lượt: &e" + VaultEconomy.format(cfg.getDouble("money-per-vote", 100))
                + "&7 · vote " + cfg.getInt("streak-days", 7) + " ngày liền: thêm &e" + VaultEconomy.format(cfg.getDouble("streak-bonus", 500))
                + "&7 · mỗi " + cfg.getInt("party-every", 30) + " lượt cả server: vote party"));
        ConfigurationSection sites = cfg.getConfigurationSection("sites");
        int shown = 0;
        if (sites != null) {
            for (String k : sites.getKeys(false)) {
                String url = sites.getString(k + ".url", "");
                if (url.isBlank()) {
                    continue;
                }
                String label = sites.getString(k + ".name", k);
                s.sendMessage(c("&b➜ &f&n" + label).clickEvent(ClickEvent.openUrl(url))
                        .hoverEvent(HoverEvent.showText(c("&7Bấm để mở " + url))));
                shown++;
            }
        }
        if (shown == 0) {
            s.sendMessage(c("&7Server đang đăng ký lên các trang vote, sắp có link."));
        }
        if (s instanceof Player p) {
            String key = "players." + p.getUniqueId();
            s.sendMessage(c("&7Bạn: &f" + data.getInt(key + ".total", 0) + "&7 lượt · chuỗi &f"
                    + data.getInt(key + ".streak", 0) + "&7 ngày · &f/vote top &7xem bảng tháng"));
        }
        return true;
    }

    private List<Map.Entry<String, Integer>> monthTop(String month) {
        List<Map.Entry<String, Integer>> rows = new ArrayList<>();
        ConfigurationSection ps = data.getConfigurationSection("players");
        if (ps != null) {
            for (String id : ps.getKeys(false)) {
                if (month.equals(ps.getString(id + ".month")) && ps.getInt(id + ".month-votes") > 0) {
                    rows.add(Map.entry(ps.getString(id + ".name", "?"), ps.getInt(id + ".month-votes")));
                }
            }
        }
        rows.sort((a, b) -> b.getValue() - a.getValue());
        return rows;
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được vote.yml: " + e.getMessage());
        }
    }
}
