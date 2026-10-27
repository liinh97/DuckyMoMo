package vn.duckymomo.core.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Nhiệm vụ hằng ngày (/nhiemvu). Mỗi người mỗi ngày nhận per-day nhiệm vụ ngẫu nhiên (cố định trong ngày) từ pool.
 * Loại: break (phá khối), place (đặt khối), kill (giết mob), fish (câu cá), harvest (thu hoạch cây đã chín).
 * targets: tên vật liệu/mob, dạng *_ORE, OAK_*, * (tất cả), @hostile (mọi quái thù địch). Khối do người chơi đặt không tính
 * (PlacedBlockGuard huỷ sự kiện phá; ở đây ignoreCancelled). Làm xong một nhiệm vụ: quà của nó; xong hết: complete-all-rewards.
 * Lưu: plugins/DuckyMoMoCore/nhiemvu.yml (ghi mỗi phút + khi thoát).
 * Làm quen tính năng (intro, 26/10): mỗi người làm MỘT lần mỗi việc, làm song song, không theo thứ tự; module khác báo bằng
 * DailyQuests.feature(p, mã) khi người chơi làm thật: mua (Cửa hàng Kinh Đô), ban (/sell có tiền về), choden (treo món zAuctionHouse),
 * trade (TradePro xong), thoren, tinhluyen, dungeon (vào tới), pet (bắt pet), thitran (có thị trấn Towny, kiểm mỗi phút).
 * Xong hết: intro-all-rewards. Lưu intro.<uuid>: [mã đã xong] (không xoá khi sang ngày).
 */
final class DailyQuests implements Listener {
    static volatile DailyQuests INSTANCE;

    /** Số nhiệm vụ hôm nay chưa xong (PlayerSettings nhắc). */
    int remaining(UUID id) {
        String key = "players." + id;
        int n = 0;
        for (String qid : todays(id)) {
            Quest q = pool.get(qid);
            if (q != null && data.getInt(key + ".progress." + qid, 0) < need(q)) {
                n++;
            }
        }
        return n;
    }

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private record Quest(String id, String name, String type, List<String> targets, int amount, List<String> rewards) { }

    private record Intro(String id, String name, String how, List<String> rewards) { }

    private final JavaPlugin plugin;
    private final ConfigurationSection cfg;
    private final Rewards rewards;
    private final File file;
    private final YamlConfiguration data;
    private final Map<String, Quest> pool = new LinkedHashMap<>();
    private final Map<String, Intro> intros = new LinkedHashMap<>();
    private boolean dirty;

    DailyQuests(JavaPlugin plugin, ConfigurationSection cfg, Rewards rewards) {
        INSTANCE = this;
        this.plugin = plugin;
        this.cfg = cfg;
        this.rewards = rewards;
        this.file = new File(plugin.getDataFolder(), "nhiemvu.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        Shared.FORGET.add(id -> Shared.forget(data, file, id));   // admin reset "như người mới"
        ConfigurationSection p = cfg.getConfigurationSection("pool");
        if (p != null) {
            for (String id : p.getKeys(false)) {
                ConfigurationSection q = p.getConfigurationSection(id);
                if (q == null) {
                    continue;
                }
                List<String> t = q.getStringList("targets").stream().map(x -> x.toUpperCase(Locale.ROOT)).toList();
                pool.put(id, new Quest(id, q.getString("name", id), q.getString("type", "break").toLowerCase(Locale.ROOT),
                        t.isEmpty() ? List.of("*") : t, Math.max(1, q.getInt("amount", 1)), q.getStringList("rewards")));
            }
        }
        ConfigurationSection in = cfg.getConfigurationSection("intro");
        if (in != null) {
            for (String id : in.getKeys(false)) {
                ConfigurationSection q = in.getConfigurationSection(id);
                if (q != null) {
                    intros.put(id, new Intro(id, q.getString("name", id), q.getString("how", ""), q.getStringList("rewards")));
                }
            }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::flush, 1200L, 1200L);
        if (intros.containsKey("thitran") && Bukkit.getPluginManager().isPluginEnabled("Towny")) {
            Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                for (Player pl : Bukkit.getOnlinePlayers()) {
                    if (!introDone(pl.getUniqueId(), "thitran") && TownyCheck.hasTown(pl)) {
                        intro(pl, "thitran");
                    }
                }
            }, 1200L, 1200L);
        }
        hookEvent("fr.maxlego08.zauctionhouse.api.event.events.stats.AuctionItemListedEvent", "getSeller", "choden");
        hookEvent("com.jaketheman.tradepro.events.TradeCompleteEvent", "getPlayerOne", "trade");
        hookEvent("com.jaketheman.tradepro.events.TradeCompleteEvent", "getPlayerTwo", "trade");
    }

    /** Sự kiện của plugin khác (không phụ thuộc lúc biên dịch): người chơi lấy bằng getter, có thì tính việc làm quen. */
    @SuppressWarnings("unchecked")
    private void hookEvent(String cls, String getter, String intro) {
        if (!intros.containsKey(intro)) {
            return;
        }
        try {
            Class<? extends org.bukkit.event.Event> ev = (Class<? extends org.bukkit.event.Event>) Class.forName(cls);
            java.lang.reflect.Method g = ev.getMethod(getter);
            Bukkit.getPluginManager().registerEvent(ev, this, EventPriority.MONITOR, (l, e) -> {
                if (ev.isInstance(e)) {
                    try {
                        if (g.invoke(e) instanceof Player p) {
                            intro(p, intro);
                        }
                    } catch (ReflectiveOperationException ignored) {
                        // bỏ qua
                    }
                }
            }, plugin, true);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            // plugin đó chưa cài
        }
    }

    /** Module khác báo người chơi vừa dùng một tính năng (mã trong daily-quests.intro). */
    static void feature(Player p, String id) {
        DailyQuests d = INSTANCE;
        if (d != null) {
            d.intro(p, id);
        }
    }

    /** /sell (lệnh hoặc khung shop): chờ 2 tick, có tiền về mới tính việc "ban" (bán hụt không tính). */
    static void watchSell(Player p) {
        DailyQuests d = INSTANCE;
        if (d == null || !d.intros.containsKey("ban") || d.introDone(p.getUniqueId(), "ban") || !VaultEconomy.available()) {
            return;
        }
        double before = VaultEconomy.balance(p);
        Bukkit.getScheduler().runTaskLater(d.plugin, () -> {
            if (p.isOnline() && VaultEconomy.balance(p) > before) {
                d.intro(p, "ban");
            }
        }, 2L);
    }

    private boolean introDone(UUID id, String intro) {
        return data.getStringList("intro." + id).contains(intro);
    }

    private void intro(Player p, String id) {
        Intro in = intros.get(id);
        if (in == null || introDone(p.getUniqueId(), id)) {
            return;
        }
        List<String> done = new ArrayList<>(data.getStringList("intro." + p.getUniqueId()));
        done.add(id);
        data.set("intro." + p.getUniqueId(), done);
        dirty = true;
        List<String> got = rewards.give(p, in.rewards());
        long left = intros.keySet().stream().filter(k -> !done.contains(k)).count();
        p.sendMessage(c("&a✔ Làm quen: &f" + in.name() + (got.isEmpty() ? "" : "&a. Nhận: &f" + String.join("&7, &f", got))
                + (left > 0 ? " &7(còn " + left + " việc làm quen, xem /nhiemvu)" : "")));
        if (left == 0) {
            List<String> bonus = rewards.give(p, cfg.getStringList("intro-all-rewards"));
            p.sendMessage(c("&6★ Đã làm quen hết các tính năng!" + (bonus.isEmpty() ? "" : " Thưởng thêm: &f" + String.join("&7, &f", bonus))));
        }
        flush();
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s);
    }

    private static String today() {
        return LocalDate.now(VN).toString();
    }

    void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được nhiemvu.yml: " + e.getMessage());
        }
    }

    /** Nhiệm vụ hôm nay của người chơi (tạo mới khi sang ngày). */
    private List<String> todays(UUID id) {
        String key = "players." + id;
        if (!today().equals(data.getString(key + ".date"))) {
            List<String> ids = new ArrayList<>(pool.keySet());
            Collections.shuffle(ids, new Random((id.toString() + today()).hashCode()));
            List<String> pick = ids.subList(0, Math.min(cfg.getInt("per-day", 3), ids.size()));
            data.set(key, null);
            data.set(key + ".date", today());
            data.set(key + ".quests", new ArrayList<>(pick));
            dirty = true;
        }
        return data.getStringList(key + ".quests");
    }

    /** Chỉ tiêu thật: server vắng (LowPop) thì giảm. */
    private static int need(Quest q) {
        return Math.max(1, (int) Math.round(q.amount() * LowPop.questScale()));
    }

    /** Tên hiện cho người chơi: số trong tên ("Câu 10 con cá") đổi theo chỉ tiêu thật khi server vắng giảm việc. */
    private static String title(Quest q) {
        int n = need(q);
        if (n == q.amount()) {
            return q.name();
        }
        String t = q.name().replaceFirst("\\b" + q.amount() + "\\b", String.valueOf(n));
        return t.equals(q.name()) ? q.name() + " (" + n + ")" : t;
    }

    private static boolean matches(List<String> targets, String name) {
        for (String t : targets) {
            if (t.equals("*") || t.equals(name)
                    || (t.startsWith("*") && name.endsWith(t.substring(1)))
                    || (t.endsWith("*") && name.startsWith(t.substring(0, t.length() - 1)))) {
                return true;
            }
        }
        return false;
    }

    private void progress(Player p, String type, String target, boolean hostile, int n) {
        String key = "players." + p.getUniqueId();
        for (String qid : todays(p.getUniqueId())) {
            Quest q = pool.get(qid);
            if (q == null || !q.type().equals(type)) {
                continue;
            }
            if (!(matches(q.targets(), target) || (hostile && q.targets().contains("@HOSTILE")))) {
                continue;
            }
            int done = data.getInt(key + ".progress." + qid, 0);
            if (done >= need(q)) {
                continue;
            }
            done = Math.min(need(q), done + n);
            data.set(key + ".progress." + qid, done);
            dirty = true;
            p.sendActionBar(c("&e" + title(q) + " &f" + done + "&7/&f" + need(q)));
            if (done >= need(q)) {
                complete(p, q);
            }
        }
    }

    private void complete(Player p, Quest q) {
        List<String> got = rewards.give(p, q.rewards());
        p.sendMessage(c("&a✔ Xong nhiệm vụ: &f" + title(q) + (got.isEmpty() ? "" : "&a. Nhận: &f" + String.join("&7, &f", got))));
        String key = "players." + p.getUniqueId();
        boolean all = true;
        for (String qid : todays(p.getUniqueId())) {
            Quest x = pool.get(qid);
            if (x != null && data.getInt(key + ".progress." + qid, 0) < need(x)) {
                all = false;
            }
        }
        if (all && !data.getBoolean(key + ".bonus")) {
            data.set(key + ".bonus", true);
            List<String> bonus = rewards.give(p, cfg.getStringList("complete-all-rewards"));
            p.sendMessage(c("&6★ Xong hết nhiệm vụ hôm nay!" + (bonus.isEmpty() ? "" : " Thưởng thêm: &f" + String.join("&7, &f", bonus))));
        }
        flush();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Material m = b.getType();
        if (b.getBlockData() instanceof Ageable age && age.getAge() >= age.getMaximumAge()) {
            progress(e.getPlayer(), "harvest", m.name(), false, 1);
        }
        progress(e.getPlayer(), "break", m.name(), false, 1);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent e) {
        progress(e.getPlayer(), "place", e.getBlockPlaced().getType().name(), false, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k != null && !(e.getEntity() instanceof Player)) {
            progress(k, "kill", e.getEntityType().name(), e.getEntity() instanceof Monster, 1);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            progress(e.getPlayer(), "fish", "*", false, 1);
            intro(e.getPlayer(), "cauca");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSellCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent e) {
        String c = e.getMessage().toLowerCase(Locale.ROOT);
        if (c.startsWith("/sell") || c.startsWith("/esell") || c.startsWith("/essentials:sell")) {
            watchSell(e.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        flush();
    }

    boolean command(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Chỉ người chơi xem được nhiệm vụ.");
            return true;
        }
        String key = "players." + p.getUniqueId();
        List<String> qs = todays(p.getUniqueId());
        p.sendMessage(c("&6Nhiệm vụ hôm nay &7(đổi lúc 0 giờ)"
                + (LowPop.questScale() < 1 ? " &8· &bserver đang vắng nên nhẹ hơn" : "")));
        for (String qid : qs) {
            Quest q = pool.get(qid);
            if (q == null) {
                continue;
            }
            int done = data.getInt(key + ".progress." + qid, 0);
            int bars = 10 * Math.min(done, need(q)) / need(q);
            String bar = "&a" + "▮".repeat(bars) + "&8" + "▮".repeat(10 - bars);
            p.sendMessage(c((done >= need(q) ? "&a✔ " : "&7• ") + "&f" + title(q) + " " + bar + " &7" + done + "/" + need(q)
                    + (q.rewards().isEmpty() ? "" : " &8→ &7" + String.join("&7, ", rewards.describe(q.rewards())))));
        }
        List<String> bonus = rewards.describe(cfg.getStringList("complete-all-rewards"));
        if (!bonus.isEmpty()) {
            p.sendMessage(c("&7Xong hết: thưởng thêm &f" + String.join("&7, &f", bonus) + (data.getBoolean(key + ".bonus") ? " &a(đã nhận)" : "")));
        }
        if (!intros.isEmpty()) {
            List<String> done = data.getStringList("intro." + p.getUniqueId());
            long left = intros.keySet().stream().filter(k -> !done.contains(k)).count();
            if (left == 0) {
                p.sendMessage(c("&a✔ Đã làm quen hết " + intros.size() + " tính năng."));
            } else {
                p.sendMessage(c("&6Làm quen tính năng &7(mỗi việc nhận quà 1 lần, làm việc nào trước cũng được)"));
                for (Intro in : intros.values()) {
                    boolean ok = done.contains(in.id());
                    p.sendMessage(c((ok ? "&a✔ &7" : "&7• &f") + in.name() + (ok || in.rewards().isEmpty() ? ""
                            : " &8→ &7" + String.join("&7, ", rewards.describe(in.rewards())))
                            + (ok || in.how().isEmpty() ? "" : "\n   &8" + in.how())));
                }
                List<String> ib = rewards.describe(cfg.getStringList("intro-all-rewards"));
                if (!ib.isEmpty()) {
                    p.sendMessage(c("&7Làm quen hết: thưởng thêm &f" + String.join("&7, &f", ib)));
                }
            }
        }
        return true;
    }
}
