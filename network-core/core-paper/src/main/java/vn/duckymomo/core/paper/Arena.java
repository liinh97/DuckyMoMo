package vn.duckymomo.core.paper;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.scheduler.BukkitTask;

/**
 * Đấu Trường /dautruong (Bang Hội Chiến): người + pet MyPet đấu 1v1 hoặc 2v2 trong thế giới riêng (mặc định "dautruong", trống).
 *  - Sân: admin đứng trong sân gõ /dautruong admin dat <sân> a1|a2|b1|b2|xem (map tải về dán bằng WorldEdit).
 *  - Vào: hàng chờ theo kiểu + mức cược (chỉ ghép người cùng mức), hoặc thách đấu 1v1.
 *  - Đồ của mình, chết trong sân không rơi đồ (huỷ đòn chí mạng, chuyển sang xem). Hết giờ: đội còn nhiều % máu hơn thắng.
 *  - Cược: trừ khi bắt đầu, server thu fee-percent, đội thắng chia phần còn lại; hoà/huỷ trả lại. Cùng IP = giao hữu (không cược, không điểm).
 *  - Thưởng thắng win-reward xu, tối đa daily-reward-cap trận/ngày. Điểm ELO + /dautruong top.
 *  - Mọi sát thương trong thế giới đấu trường do module này quyết định (ghi đè Towny/PvPManager/WorldGuard ở HIGHEST).
 * Dữ liệu: dautruong.yml (sân, thống kê, người chờ khôi phục khi thoát giữa trận).
 */
final class Arena implements Listener {

    static volatile String WORLD = "dautruong";
    static volatile double PET_REDUCE = 50;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<String> SLOTS = List.of("a1", "a2", "b1", "b2", "xem");
    private static final Set<String> ALLOWED_CMDS = Set.of("dautruong", "dt", "arena", "petinfo", "petskill", "msg", "r", "tell", "w", "reply");

    enum State { COUNTDOWN, FIGHT, END }

    private final class Match {
        final String id;
        final String sanId;
        final int size;
        final double stake;
        final boolean friendly;
        final List<UUID> a;
        final List<UUID> b;
        final Set<UUID> out = new HashSet<>();
        State state = State.COUNTDOWN;
        long fightEnd;
        BukkitTask task;

        Match(String sanId, List<UUID> a, List<UUID> b, double stake, boolean friendly) {
            this.id = Long.toString(System.nanoTime(), 36);
            this.sanId = sanId;
            this.size = a.size();
            this.a = a;
            this.b = b;
            this.stake = stake;
            this.friendly = friendly;
        }

        List<UUID> all() {
            List<UUID> l = new ArrayList<>(a);
            l.addAll(b);
            return l;
        }

        boolean teamA(UUID u) {
            return a.contains(u);
        }
    }

    private record Saved(Location loc, GameMode mode) {
    }

    private record Invite(UUID from, double stake, long until) {
    }

    private final DuckyMoMoCore plugin;
    private final File file;
    private final YamlConfiguration data;
    private final int countdown;
    private final int fightSeconds;
    private final double feePercent;
    private final double maxStake;
    private final double winReward;
    private final int dailyCap;
    private final int eloK;
    private final Map<String, List<UUID>> queues = new LinkedHashMap<>();
    private final Map<UUID, String> queuedIn = new HashMap<>();
    private final Map<UUID, Match> inMatch = new HashMap<>();
    private final Map<String, Match> sanBusy = new HashMap<>();
    private final Map<UUID, Saved> saved = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();
    private final Set<UUID> watching = new HashSet<>();
    /** người đang xem -> sân. */
    private final Map<UUID, String> watchSan = new HashMap<>();
    private final Map<UUID, Long> gateCooldown = new HashMap<>();
    /** người đang đứng CHỜ trong sân (bước vào cổng) -> sân. */
    private final Map<UUID, String> waitSan = new HashMap<>();

    Arena(DuckyMoMoCore plugin, ConfigurationSection c) {
        this.plugin = plugin;
        WORLD = c.getString("world", "dautruong");
        PET_REDUCE = Math.max(0, Math.min(100, c.getDouble("pet-damage-reduce-percent", 50)));
        this.countdown = Math.max(1, c.getInt("countdown-seconds", 5));
        this.fightSeconds = Math.max(30, c.getInt("fight-seconds", 180));
        this.feePercent = Math.max(0, Math.min(50, c.getDouble("fee-percent", 5)));
        this.maxStake = Math.max(0, c.getDouble("max-stake", 10000));
        this.winReward = Math.max(0, c.getDouble("win-reward", 50));
        this.dailyCap = Math.max(0, c.getInt("daily-reward-cap", 5));
        this.eloK = Math.max(1, c.getInt("elo-k", 32));
        this.file = new File(plugin.getDataFolder(), "dautruong.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        ensureWorld();
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, String> e : waitSan.entrySet()) {
                Player p = Bukkit.getPlayer(e.getKey());
                if (p == null || inMatch.containsKey(e.getKey())) {
                    continue;
                }
                String k = queuedIn.getOrDefault(e.getKey(), "1:0:" + e.getValue());
                String[] kp = k.split(":");
                p.sendActionBar(c("&e⏳ Chờ đối thủ &f" + kp[0] + "v" + kp[0] + (kp[1].equals("0") ? "" : " &ecược &f" + kp[1])
                        + " &7· Thoát: &f/dautruong roi"));
            }
        }, 40L, 40L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::writeLive, 60L, 100L);
    }

    String describe() {
        World w = Bukkit.getWorld(WORLD);
        return "thế giới " + WORLD + (w == null ? "" : " (uid " + w.getUID() + ")") + ", " + sanIds().size() + " sân, " + fightSeconds + " giây/trận, phí cược " + fmt(feePercent) + "%";
    }

    // ------------------------------------------------------------------ thế giới, sân

    private World ensureWorld() {
        World w = Bukkit.getWorld(WORLD);
        if (w == null) {
            w = new WorldCreator(WORLD).generator(new ChunkGenerator() {
            }).generateStructures(false).createWorld();
            if (w != null) {
                w.setSpawnLocation(0, 101, 0);
                w.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
                w.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
                w.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
                w.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, true);
                w.setTime(6000);
            }
        }
        return w;
    }

    private List<String> sanIds() {
        ConfigurationSection s = data.getConfigurationSection("san");
        return s == null ? List.of() : new ArrayList<>(s.getKeys(false));
    }

    private Location point(String san, String slot) {
        String v = data.getString("san." + san + "." + slot);
        if (v == null) {
            return null;
        }
        String[] p = v.split(",");
        World w = Bukkit.getWorld(p[0]);
        if (w == null || p.length < 6) {
            return null;
        }
        return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                Float.parseFloat(p[4]), Float.parseFloat(p[5]));
    }

    private boolean sanReady(String san, int size) {
        if (point(san, "a1") == null || point(san, "b1") == null) {
            return false;
        }
        return size < 2 || (point(san, "a2") != null && point(san, "b2") != null);
    }

    private String freeSan(int size) {
        for (String s : sanIds()) {
            if (!sanBusy.containsKey(s) && sanReady(s, size)) {
                return s;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ lệnh

    boolean command(CommandSender s, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) {
            return admin(s, args);
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("Lệnh này phải gõ trong game.");
            return true;
        }
        switch (sub) {
            case "vao", "join" -> join(p, args);
            case "roi", "leave" -> leave(p);
            case "doi" -> requeue(p, args.length >= 2 ? args[1] : "", null);
            case "cuoc" -> requeue(p, null, args.length >= 2 ? args[1] : "0");
            case "thachdau", "challenge" -> challenge(p, args);
            case "dongy", "accept" -> accept(p);
            case "tuchoi", "deny" -> {
                invites.remove(p.getUniqueId());
                p.sendMessage(c("&7Đã từ chối lời thách đấu."));
            }
            case "xem", "spectate" -> spectate(p, args);
            case "xemsan" -> {
                Match m = args.length >= 2 ? sanBusy.get(args[1].toLowerCase(Locale.ROOT)) : null;
                if (m == null) {
                    p.sendMessage(c("&7Sân này đang trống, chưa có trận để xem."));
                } else {
                    spectate(p, new String[] {"xem", Bukkit.getOfflinePlayer(m.a.get(0)).getName()});
                }
            }
            case "top" -> top(p);
            case "toi", "me" -> me(p);
            default -> help(p);
        }
        return true;
    }

    private void help(Player p) {
        p.sendMessage(c("&6&l⚔ ĐẤU TRƯỜNG &7(người + pet, đồ của mình, chết không rơi đồ)"));
        p.sendMessage(c("&e/dautruong vao <1v1|2v2> [cược] [sân] &7vào hàng chờ (ghép người cùng mức cược); &e/warp pvp &7tới sảnh có 3 cổng"));
        p.sendMessage(c("&e/dautruong thachdau <người> [cược] &7thách đấu 1v1; người kia &e/dautruong dongy"));
        p.sendMessage(c("&e/dautruong roi &7rời hàng chờ / thôi xem"));
        p.sendMessage(c("&e/dautruong xem [người] &7xem trận đang đấu"));
        p.sendMessage(c("&e/dautruong top &7bảng xếp hạng · &e/dautruong toi &7thành tích của bạn"));
        p.sendMessage(c("&7Cược tối đa " + money(maxStake) + ", server thu " + fmt(feePercent) + "%. Trận " + fightSeconds
                + " giây, hết giờ đội còn nhiều máu hơn thắng. Thắng thưởng " + money(winReward) + " (tối đa " + dailyCap + " trận/ngày)."));
    }

    private boolean busy(Player p) {
        if (waitSan.containsKey(p.getUniqueId())) {
            p.sendMessage(c("&cBạn đang chờ trong sân: &e/dautruong roi &ctrước."));
            return true;
        }
        if (inMatch.containsKey(p.getUniqueId())) {
            p.sendMessage(c("&cBạn đang trong trận."));
            return true;
        }
        if (watching.contains(p.getUniqueId())) {
            p.sendMessage(c("&cBạn đang xem trận: &e/dautruong roi &ctrước."));
            return true;
        }
        if (SiegeWarHook.battleActive() && SiegeWarHook.inActiveSiegeZone(p.getLocation())) {
            p.sendMessage(c("&cĐang ở vùng công thành trong giờ đánh, không vào đấu trường được."));
            return true;
        }
        return false;
    }

    private double parseStake(Player p, String v) {
        if (v == null) {
            return 0;
        }
        try {
            double d = Math.floor(Double.parseDouble(v));
            if (d < 0 || d > maxStake) {
                p.sendMessage(c("&cCược từ 0 đến " + money(maxStake) + "."));
                return -1;
            }
            if (d > 0 && (!VaultEconomy.available() || !VaultEconomy.has(p, d))) {
                p.sendMessage(c("&cKhông đủ tiền để cược " + money(d) + "."));
                return -1;
            }
            return d;
        } catch (NumberFormatException e) {
            p.sendMessage(c("&cMức cược phải là số, vd 100."));
            return -1;
        }
    }

    private void join(Player p, String[] args) {
        if (busy(p)) {
            return;
        }
        String mode = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "1v1";
        int size = mode.equals("2v2") ? 2 : mode.equals("1v1") ? 1 : 0;
        if (size == 0) {
            p.sendMessage(c("&e/dautruong vao <1v1|2v2> [cược]"));
            return;
        }
        double stake = parseStake(p, args.length >= 3 ? args[2] : null);
        if (stake < 0) {
            return;
        }
        String san = args.length >= 4 ? args[3].toLowerCase(Locale.ROOT) : "*";
        if (!san.equals("*") && !sanIds().contains(san)) {
            p.sendMessage(c("&cKhông có sân " + san + "."));
            return;
        }
        if (!san.equals("*") && !sanReady(san, size)) {
            p.sendMessage(c("&cSân " + sanName(san) + " &ckhông có kiểu " + mode + "."));
            return;
        }
        leaveQueue(p.getUniqueId());
        String key = size + ":" + (long) stake + ":" + san;
        queues.computeIfAbsent(key, k -> new ArrayList<>()).add(p.getUniqueId());
        queuedIn.put(p.getUniqueId(), key);
        p.sendMessage(c("&aĐã vào hàng chờ &f" + mode + (san.equals("*") ? "" : " &asân &f" + sanName(san)) + (stake > 0 ? " &acược &f" + money(stake) : "")
                + "&a. Rời: &e/dautruong roi"));
        tryMatch(key);
    }

    private void leaveQueue(UUID u) {
        String k = queuedIn.remove(u);
        if (k != null && queues.containsKey(k)) {
            queues.get(k).remove(u);
        }
    }

    private void leave(Player p) {
        UUID u = p.getUniqueId();
        if (waitSan.remove(u) != null) {
            leaveQueue(u);
            restore(p);
            p.sendMessage(c("&7Đã rời sân, về sảnh."));
            return;
        }
        watchSan.remove(u);
        if (watching.remove(u)) {
            restore(p);
            p.sendMessage(c("&7Đã thôi xem trận."));
            return;
        }
        if (queuedIn.containsKey(u)) {
            leaveQueue(u);
            p.sendMessage(c("&7Đã rời hàng chờ."));
            return;
        }
        if (inMatch.containsKey(u)) {
            p.sendMessage(c("&cĐang trong trận thì không rời được (thoát game = thua)."));
            return;
        }
        p.sendMessage(c("&7Bạn không ở hàng chờ."));
    }

    private void tryMatch(String key) {
        List<UUID> q = queues.get(key);
        if (q == null) {
            return;
        }
        q.removeIf(u -> Bukkit.getPlayer(u) == null);
        String[] kp = key.split(":");
        int size = Integer.parseInt(kp[0]);
        double stake = Double.parseDouble(kp[1]);
        String want = kp.length > 2 ? kp[2] : "*";
        while (q.size() >= size * 2) {
            String san = want.equals("*") ? freeSan(size) : (!sanBusy.containsKey(want) && sanReady(want, size) ? want : null);
            if (san == null) {
                for (UUID u : q) {
                    Player p = Bukkit.getPlayer(u);
                    if (p != null) {
                        p.sendActionBar(c("&eĐủ người, đang chờ sân trống…"));
                    }
                }
                return;
            }
            List<UUID> picked = new ArrayList<>(q.subList(0, size * 2));
            q.removeAll(picked);
            picked.forEach(queuedIn::remove);
            java.util.Collections.shuffle(picked);
            start(san, new ArrayList<>(picked.subList(0, size)), new ArrayList<>(picked.subList(size, size * 2)), stake);
        }
    }

    private void challenge(Player p, String[] args) {
        if (busy(p)) {
            return;
        }
        if (args.length < 2) {
            p.sendMessage(c("&e/dautruong thachdau <người> [cược]"));
            return;
        }
        Player t = Bukkit.getPlayerExact(args[1]);
        if (t == null || t.equals(p)) {
            p.sendMessage(c("&cKhông thấy người chơi này online ở Bang Hội Chiến."));
            return;
        }
        double stake = parseStake(p, args.length >= 3 ? args[2] : null);
        if (stake < 0) {
            return;
        }
        invites.put(t.getUniqueId(), new Invite(p.getUniqueId(), stake, System.currentTimeMillis() + 60_000L));
        p.sendMessage(c("&aĐã thách đấu &f" + t.getName() + (stake > 0 ? " &a(cược " + money(stake) + ")" : "") + "&a. Chờ 60 giây."));
        t.sendMessage(c("&6⚔ &f" + p.getName() + " &6thách đấu bạn 1v1 (người + pet)" + (stake > 0 ? ", cược &f" + money(stake) : "")
                + "&6. Gõ &e/dautruong dongy &6hoặc &e/dautruong tuchoi&6."));
    }

    private void accept(Player p) {
        Invite inv = invites.remove(p.getUniqueId());
        if (inv == null || inv.until() < System.currentTimeMillis()) {
            p.sendMessage(c("&7Không có lời thách đấu nào (hoặc đã hết hạn)."));
            return;
        }
        Player from = Bukkit.getPlayer(inv.from());
        if (from == null || busy(p) || inMatch.containsKey(inv.from())) {
            p.sendMessage(c("&cNgười thách đấu không còn sẵn sàng."));
            return;
        }
        if (inv.stake() > 0 && (!VaultEconomy.has(p, inv.stake()) || !VaultEconomy.has(from, inv.stake()))) {
            p.sendMessage(c("&cMột trong hai người không đủ tiền cược."));
            return;
        }
        String san = freeSan(1);
        if (san == null) {
            p.sendMessage(c("&cChưa có sân trống, thử lại sau ít phút."));
            return;
        }
        leaveQueue(p.getUniqueId());
        leaveQueue(from.getUniqueId());
        start(san, new ArrayList<>(List.of(from.getUniqueId())), new ArrayList<>(List.of(p.getUniqueId())), inv.stake());
    }

    // ------------------------------------------------------------------ trận đấu

    private void start(String san, List<UUID> a, List<UUID> b, double stake) {
        List<Player> ps = new ArrayList<>();
        for (UUID u : a) {
            ps.add(Bukkit.getPlayer(u));
        }
        for (UUID u : b) {
            ps.add(Bukkit.getPlayer(u));
        }
        if (ps.contains(null)) {
            for (Player p : ps) {
                if (p != null) {
                    p.sendMessage(c("&cCó người vừa thoát, trận bị huỷ. Vào hàng chờ lại nhé."));
                }
            }
            return;
        }
        // giao hữu: hai đối thủ cùng IP -> không cược, không điểm, không thưởng (chống chuyển tiền / cày điểm bằng tài khoản phụ)
        boolean friendly = false;
        for (UUID x : a) {
            for (UUID y : b) {
                if (sameIp(Bukkit.getPlayer(x), Bukkit.getPlayer(y))) {
                    friendly = true;
                }
            }
        }
        double realStake = friendly ? 0 : stake;
        if (realStake > 0) {
            List<Player> paid = new ArrayList<>();
            for (Player p : ps) {
                if (!VaultEconomy.withdraw(p, realStake)) {
                    paid.forEach(x -> VaultEconomy.deposit(x, realStake));
                    ps.forEach(x -> x.sendMessage(c("&c" + p.getName() + " không đủ tiền cược, trận bị huỷ.")));
                    return;
                }
                paid.add(p);
            }
        }
        Match m = new Match(san, a, b, realStake, friendly);
        sanBusy.put(san, m);
        Location view = point(san, "xem");
        for (Map.Entry<UUID, String> e : waitSan.entrySet()) {
            Player w = Bukkit.getPlayer(e.getKey());
            if (w != null && e.getValue().equals(san) && !a.contains(e.getKey()) && !b.contains(e.getKey())) {
                w.setGameMode(GameMode.SPECTATOR);
                if (view != null) {
                    w.teleport(view);
                }
                w.sendMessage(c("&7Sân bắt đầu một trận khác; bạn xem trong lúc chờ, xong trận sẽ quay lại chờ."));
            }
        }
        String[] slotA = {"a1", "a2"};
        String[] slotB = {"b1", "b2"};
        for (int i = 0; i < a.size(); i++) {
            enter(Bukkit.getPlayer(a.get(i)), m, point(san, slotA[i]));
        }
        for (int i = 0; i < b.size(); i++) {
            enter(Bukkit.getPlayer(b.get(i)), m, point(san, slotB[i]));
        }
        String names = names(a) + " &7vs &f" + names(b);
        broadcastMatch(m, "&6⚔ Trận " + m.size + "v" + m.size + " ở sân &f" + san + "&6: &f" + names
                + (realStake > 0 ? " &6(cược " + money(realStake) + "/người)" : "") + (friendly ? " &7(giao hữu: cùng IP, không tính)" : ""));
        int[] left = {countdown};
        m.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (m.state == State.COUNTDOWN) {
                if (left[0] > 0) {
                    title(m, "&e" + left[0], "&7Chuẩn bị");
                    left[0]--;
                } else {
                    m.state = State.FIGHT;
                    m.fightEnd = System.currentTimeMillis() + fightSeconds * 1000L;
                    title(m, "&c&lĐÁNH!", "&7" + fightSeconds + " giây");
                }
            } else if (m.state == State.FIGHT) {
                long rem = (m.fightEnd - System.currentTimeMillis()) / 1000L;
                for (UUID u : m.all()) {
                    Player p = Bukkit.getPlayer(u);
                    if (p != null) {
                        p.sendActionBar(c("&6⚔ Còn &f" + Math.max(0, rem) + "s &7· Đội A: &f" + alive(m, m.a) + " &7· Đội B: &f" + alive(m, m.b)));
                    }
                }
                if (rem <= 0) {
                    timeUp(m);
                }
            }
        }, 0L, 20L);
    }

    private void enter(Player p, Match m, Location at) {
        UUID u = p.getUniqueId();
        waitSan.remove(u);
        if (!saved.containsKey(u)) {
            saved.put(u, new Saved(p.getLocation().clone(), p.getGameMode()));
            persistRestore(p);
        }
        inMatch.put(u, m);
        p.teleport(at);
        p.setGameMode(GameMode.SURVIVAL);
        heal(p);
        // gọi pet ra (nếu có) sau khi tới sân
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && inMatch.get(u) == m) {
                p.performCommand("petcall");
            }
        }, 10L);
    }

    private void heal(Player p) {
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max == null ? 20 : max.getValue());
        p.setFoodLevel(20);
        p.setSaturation(10);
        p.setFireTicks(0);
        p.setFallDistance(0);
    }

    private int alive(Match m, List<UUID> team) {
        int n = 0;
        for (UUID u : team) {
            if (!m.out.contains(u)) {
                n++;
            }
        }
        return n;
    }

    private void eliminate(Player p, Match m, String why) {
        UUID u = p.getUniqueId();
        if (!m.out.add(u)) {
            return;
        }
        heal(p);
        p.setGameMode(GameMode.SPECTATOR);
        broadcastMatch(m, "&c✖ &f" + p.getName() + " &cbị loại" + (why == null ? "" : " (" + why + ")") + ".");
        checkEnd(m);
    }

    private void checkEnd(Match m) {
        if (m.state == State.END) {
            return;
        }
        boolean aDead = alive(m, m.a) == 0;
        boolean bDead = alive(m, m.b) == 0;
        if (aDead || bDead) {
            finish(m, aDead && bDead ? null : aDead ? m.b : m.a);
        }
    }

    private void timeUp(Match m) {
        double ha = hpPercent(m, m.a);
        double hb = hpPercent(m, m.b);
        broadcastMatch(m, "&eHết giờ: máu còn lại đội A " + Math.round(ha) + "%, đội B " + Math.round(hb) + "%.");
        finish(m, Math.abs(ha - hb) < 0.5 ? null : ha > hb ? m.a : m.b);
    }

    private double hpPercent(Match m, List<UUID> team) {
        double sum = 0;
        for (UUID u : team) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && !m.out.contains(u)) {
                var max = p.getAttribute(Attribute.MAX_HEALTH);
                sum += p.getHealth() / (max == null ? 20 : max.getValue()) * 100;
            }
        }
        return sum / team.size();
    }

    /** winners null = hoà. */
    private void finish(Match m, List<UUID> winners) {
        m.state = State.END;
        if (m.task != null) {
            m.task.cancel();
        }
        List<UUID> losers = winners == null ? List.of() : winners == m.a ? m.b : m.a;
        if (winners == null) {
            broadcastMatch(m, "&eHoà! Trả lại tiền cược.");
            refund(m);
        } else {
            double pot = m.stake * m.size * 2;
            double share = m.stake > 0 ? Math.floor(pot * (100 - feePercent) / 100.0 / winners.size()) : 0;
            String today = LocalDate.now(VN).toString();
            for (UUID u : winners) {
                Player p = Bukkit.getPlayer(u);
                double reward = 0;
                if (!m.friendly) {
                    int n = data.getInt("stats." + u + ".ngay." + today, 0);
                    if (n < dailyCap) {
                        reward = winReward;
                        data.set("stats." + u + ".ngay." + today, n + 1);
                    }
                }
                if (p != null && share + reward > 0 && VaultEconomy.available()) {
                    VaultEconomy.deposit(p, share + reward);
                }
                if (p != null) {
                    p.sendMessage(c("&a&lTHẮNG! " + (share > 0 ? "&aNhận cược &f" + money(share) + " " : "")
                            + (reward > 0 ? "&a+ thưởng &f" + money(reward) : m.friendly ? "&7(giao hữu, không thưởng)" : "&7(đã đủ " + dailyCap + " trận thưởng hôm nay)")));
                }
            }
            broadcastMatch(m, "&6🏆 Đội &f" + names(winners) + " &6thắng!");
            if (!m.friendly) {
                rate(winners, losers);
            }
        }
        save();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (UUID u : m.all()) {
                inMatch.remove(u);
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    restore(p);
                }
            }
            sanBusy.remove(m.sanId);
            int i = 0;
            for (Map.Entry<UUID, String> e : waitSan.entrySet()) {
                Player w = Bukkit.getPlayer(e.getKey());
                if (w != null && e.getValue().equals(m.sanId)) {
                    w.setGameMode(GameMode.SURVIVAL);
                    Location at = waitPoint(m.sanId, i++);
                    if (at != null) {
                        w.teleport(at);
                    }
                }
            }
            for (String k : new ArrayList<>(queues.keySet())) {
                tryMatch(k);
            }
        }, 60L);
    }

    private void refund(Match m) {
        if (m.stake <= 0) {
            return;
        }
        for (UUID u : m.all()) {
            VaultEconomy.deposit(Bukkit.getOfflinePlayer(u), m.stake);
        }
    }

    private void rate(List<UUID> win, List<UUID> lose) {
        double rw = win.stream().mapToDouble(this::elo).average().orElse(1000);
        double rl = lose.stream().mapToDouble(this::elo).average().orElse(1000);
        double expW = 1 / (1 + Math.pow(10, (rl - rw) / 400));
        int delta = (int) Math.round(eloK * (1 - expW));
        for (UUID u : win) {
            stat(u, delta, true);
        }
        for (UUID u : lose) {
            stat(u, -delta, false);
        }
    }

    private int elo(UUID u) {
        return data.getInt("stats." + u + ".elo", 1000);
    }

    private void stat(UUID u, int delta, boolean won) {
        String b = "stats." + u;
        data.set(b + ".ten", Bukkit.getOfflinePlayer(u).getName());
        data.set(b + ".elo", Math.max(0, elo(u) + delta));
        data.set(b + (won ? ".thang" : ".thua"), data.getInt(b + (won ? ".thang" : ".thua"), 0) + 1);
        Player p = Bukkit.getPlayer(u);
        if (p != null) {
            p.sendMessage(c("&7Điểm Đấu Trường: &f" + elo(u) + " &7(" + (delta >= 0 ? "+" : "") + delta + ")"));
        }
    }

    private void restore(Player p) {
        Saved s = saved.remove(p.getUniqueId());
        data.set("khoiphuc." + p.getUniqueId(), null);
        save();
        heal(p);
        if (s != null) {
            p.setGameMode(s.mode() == GameMode.SPECTATOR ? GameMode.SURVIVAL : s.mode());
            p.teleport(s.loc());
        } else if (p.getWorld().getName().equals(WORLD)) {
            p.setGameMode(GameMode.SURVIVAL);
            p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
    }

    private void persistRestore(Player p) {
        Saved s = saved.get(p.getUniqueId());
        if (s == null) {
            return;
        }
        Location l = s.loc();
        data.set("khoiphuc." + p.getUniqueId(), l.getWorld().getName() + "," + l.getX() + "," + l.getY() + "," + l.getZ() + ","
                + l.getYaw() + "," + l.getPitch() + "," + s.mode().name());
        save();
    }

    // ------------------------------------------------------------------ xem trận

    private void spectate(Player p, String[] args) {
        if (sanBusy.isEmpty()) {
            p.sendMessage(c("&7Không có trận nào đang đấu."));
            return;
        }
        if (args.length < 2) {
            p.sendMessage(c("&6Trận đang đấu:"));
            for (Match m : sanBusy.values()) {
                p.sendMessage(c("&7- sân &f" + m.sanId + "&7: &f" + names(m.a) + " &7vs &f" + names(m.b) + " &7(&e/dautruong xem "
                        + Bukkit.getOfflinePlayer(m.a.get(0)).getName() + "&7)"));
            }
            return;
        }
        if (busy(p) || queuedIn.containsKey(p.getUniqueId())) {
            if (queuedIn.containsKey(p.getUniqueId())) {
                p.sendMessage(c("&cĐang ở hàng chờ thì không xem được: &e/dautruong roi &ctrước."));
            }
            return;
        }
        Player t = Bukkit.getPlayerExact(args[1]);
        Match m = t == null ? null : inMatch.get(t.getUniqueId());
        if (m == null) {
            p.sendMessage(c("&cNgười này không ở trận nào."));
            return;
        }
        Location at = point(m.sanId, "xem");
        if (at == null) {
            at = point(m.sanId, "a1");
        }
        saved.put(p.getUniqueId(), new Saved(p.getLocation().clone(), p.getGameMode()));
        persistRestore(p);
        watching.add(p.getUniqueId());
        watchSan.put(p.getUniqueId(), m.sanId);
        p.teleport(at);
        p.setGameMode(GameMode.SPECTATOR);
        p.sendMessage(c("&7Đang xem trận. Thôi xem: &e/dautruong roi"));
    }

    // ------------------------------------------------------------------ xếp hạng

    private void top(Player p) {
        ConfigurationSection st = data.getConfigurationSection("stats");
        p.sendMessage(c("&6&l🏆 TOP ĐẤU TRƯỜNG"));
        if (st == null) {
            p.sendMessage(c("&7Chưa có trận nào."));
            return;
        }
        List<String> ids = new ArrayList<>(st.getKeys(false));
        ids.removeIf(k -> st.getInt(k + ".thang", 0) + st.getInt(k + ".thua", 0) == 0);
        ids.sort(Comparator.comparingInt((String k) -> st.getInt(k + ".elo", 1000)).reversed());
        for (int i = 0; i < Math.min(10, ids.size()); i++) {
            String k = ids.get(i);
            p.sendMessage(c("&e#" + (i + 1) + " &f" + st.getString(k + ".ten", "?") + " &7— &f" + st.getInt(k + ".elo", 1000)
                    + " &7điểm (" + st.getInt(k + ".thang", 0) + " thắng, " + st.getInt(k + ".thua", 0) + " thua)"));
        }
    }

    private void me(Player p) {
        String b = "stats." + p.getUniqueId();
        p.sendMessage(c("&6Đấu Trường của bạn: &f" + elo(p.getUniqueId()) + " &7điểm, &a" + data.getInt(b + ".thang", 0)
                + " &7thắng, &c" + data.getInt(b + ".thua", 0) + " &7thua. Hôm nay đã nhận thưởng "
                + data.getInt(b + ".ngay." + LocalDate.now(VN), 0) + "/" + dailyCap + " trận."));
    }

    // ------------------------------------------------------------------ admin

    private boolean admin(CommandSender s, String[] args) {
        if (!s.hasPermission("duckymomo.admin")) {
            s.sendMessage("Cần quyền duckymomo.admin.");
            return true;
        }
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "den" -> {
                if (s instanceof Player p) {
                    World w = ensureWorld();
                    Location l = new Location(w, 0.5, 101, 0.5);
                    if (w.getBlockAt(0, 100, 0).getType().isAir()) {
                        for (int x = -3; x <= 3; x++) {
                            for (int z = -3; z <= 3; z++) {
                                w.getBlockAt(x, 100, z).setType(Material.GLASS);
                            }
                        }
                    }
                    p.teleport(l);
                    p.sendMessage(c("&aTới thế giới đấu trường. Dán map: &f//schem load <tên> &arồi &f//paste&a; đặt điểm: &f/dautruong admin dat <sân> a1|a2|b1|b2|xem"));
                }
            }
            case "dat" -> {
                if (!(s instanceof Player p) || args.length < 4 || !SLOTS.contains(args[3].toLowerCase(Locale.ROOT))
                        || !args[2].matches("[a-z0-9_]{1,24}")) {
                    s.sendMessage("/dautruong admin dat <sân> a1|a2|b1|b2|xem  (đứng tại chỗ, nhìn về phía đối thủ; tên sân: chữ thường, số, _)");
                    return true;
                }
                Location l = p.getLocation();
                if (!l.getWorld().getName().equals(WORLD)) {
                    s.sendMessage("Phải đứng trong thế giới " + WORLD + " (/dautruong admin den).");
                    return true;
                }
                data.set("san." + args[2] + "." + args[3].toLowerCase(Locale.ROOT), l.getWorld().getName() + "," + l.getX() + "," + l.getY() + ","
                        + l.getZ() + "," + l.getYaw() + "," + l.getPitch());
                save();
                s.sendMessage("Đã đặt " + args[3] + " của sân " + args[2] + ". Sân dùng được 1v1 khi có a1+b1, 2v2 khi có thêm a2+b2.");
            }
            case "xoa" -> {
                if (args.length < 3) {
                    s.sendMessage("/dautruong admin xoa <sân>");
                    return true;
                }
                data.set("san." + args[2], null);
                save();
                s.sendMessage("Đã xoá sân " + args[2] + ".");
            }
            case "ds", "list" -> {
                for (String id : sanIds()) {
                    StringBuilder b = new StringBuilder(id + ": ");
                    for (String slot : SLOTS) {
                        b.append(slot).append(point(id, slot) == null ? "✗ " : "✓ ");
                    }
                    b.append(sanBusy.containsKey(id) ? "(đang đấu)" : "");
                    s.sendMessage(b.toString());
                }
                if (sanIds().isEmpty()) {
                    s.sendMessage("Chưa có sân. /dautruong admin den rồi dán map, đặt điểm.");
                }
            }
            case "huy" -> {
                for (Match m : new ArrayList<>(sanBusy.values())) {
                    broadcastMatch(m, "&cAdmin huỷ trận, trả lại tiền cược.");
                    m.state = State.END;
                    if (m.task != null) {
                        m.task.cancel();
                    }
                    refund(m);
                    for (UUID u : m.all()) {
                        inMatch.remove(u);
                        Player p = Bukkit.getPlayer(u);
                        if (p != null) {
                            restore(p);
                        }
                    }
                    sanBusy.remove(m.sanId);
                }
                s.sendMessage("Đã huỷ mọi trận.");
            }
            case "sanh" -> {
                if (s instanceof Player p && p.getWorld().getName().equals(WORLD)) {
                    data.set("sanh", locStr(p.getLocation()));
                    save();
                    s.sendMessage("Đã đặt sảnh PvP tại chỗ đứng.");
                } else {
                    s.sendMessage("Đứng trong thế giới " + WORLD + " rồi gõ lại.");
                }
            }
            case "cong" -> {
                // /dautruong admin cong <sân> x1 y1 z1 x2 y2 z2 : ô bước vào là cổng của sân (gõ được ở console)
                if (args.length < 9) {
                    s.sendMessage("/dautruong admin cong <sân> x1 y1 z1 x2 y2 z2");
                    return true;
                }
                data.set("cong." + args[2], String.join(",", java.util.Arrays.copyOfRange(args, 3, 9)));
                save();
                s.sendMessage("Đã đặt cổng của sân " + args[2] + ".");
            }
            case "npc" -> npc(s, args);
            case "ten" -> {
                if (args.length < 4) {
                    s.sendMessage("/dautruong admin ten <sân> <tên hiển thị, có mã màu &>");
                    return true;
                }
                data.set("ten." + args[2], String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)));
                save();
                s.sendMessage("Đã đặt tên sân " + args[2] + ".");
            }
            default -> s.sendMessage("/dautruong admin npc [x y z hướng] | npc xoa | den | sanh | dat <sân> <a1|a2|b1|b2|xem> | cong <sân> x1 y1 z1 x2 y2 z2 | ten <sân> <tên> | xoa <sân> | ds | huy");
        }
        return true;
    }

    // ------------------------------------------------------------------ sự kiện

    private static Player attacker(Entity d) {
        if (d instanceof Player p) {
            return p;
        }
        if (d instanceof Projectile pr && pr.getShooter() instanceof Entity sh) {
            d = sh;
            if (d instanceof Player p) {
                return p;
            }
        }
        return PetCombat.isPet(d) ? petOwner(d) : null;
    }

    static Player petOwner(Entity pet) {
        try {
            Object owner = pet.getClass().getMethod("getOwner").invoke(pet);
            Object player = owner == null ? null : owner.getClass().getMethod("getPlayer").invoke(owner);
            return player instanceof Player p ? p : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageBy(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (!victim.getWorld().getName().equals(WORLD)) {
            return;
        }
        Player att = attacker(event.getDamager());
        Player vp = victim instanceof Player p ? p : PetCombat.isPet(victim) ? petOwner(victim) : null;
        Match m = vp == null ? null : inMatch.get(vp.getUniqueId());
        boolean ok = m != null && m.state == State.FIGHT && att != null && inMatch.get(att.getUniqueId()) == m
                && m.teamA(att.getUniqueId()) != m.teamA(vp.getUniqueId()) && !m.out.contains(att.getUniqueId())
                && !m.out.contains(vp.getUniqueId());
        if (!ok) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(false);   // ghi đè Towny / PvPManager (bảo vệ người mới) / WorldGuard trong trận
        if (PetCombat.isPet(event.getDamager()) || (event.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity sh
                && PetCombat.isPet(sh))) {
            event.setDamage(event.getDamage() * (100 - PET_REDUCE) / 100.0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p) || !p.getWorld().getName().equals(WORLD)) {
            return;
        }
        Match m = inMatch.get(p.getUniqueId());
        if (m == null || m.state != State.FIGHT || m.out.contains(p.getUniqueId())) {
            return;
        }
        if (p.getHealth() - event.getFinalDamage() <= 0) {
            event.setCancelled(true);
            eliminate(p, m, null);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAnyDamage(EntityDamageEvent event) {
        // ngoài trận (đếm ngược, xem, người lạc vào) trong thế giới đấu trường: không mất máu
        if (event instanceof EntityDamageByEntityEvent || !(event.getEntity() instanceof Player p) || !p.getWorld().getName().equals(WORLD)) {
            return;
        }
        Match m = inMatch.get(p.getUniqueId());
        if (m == null || m.state != State.FIGHT || m.out.contains(p.getUniqueId())) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID && m == null && !watching.contains(p.getUniqueId())) {
                Location l = lobby();
                p.setFallDistance(0);
                p.teleport(l != null ? l : p.getWorld().getSpawnLocation());
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        Match m = inMatch.get(p.getUniqueId());
        if (m != null || p.getWorld().getName().equals(WORLD)) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            if (m != null) {
                eliminate(p, m, null);
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Match m = inMatch.get(event.getPlayer().getUniqueId());
        if (m != null && m.state == State.COUNTDOWN && event.hasChangedPosition()) {
            Location to = event.getFrom().clone();
            to.setYaw(event.getTo().getYaw());
            to.setPitch(event.getTo().getPitch());
            event.setTo(to);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        UUID u = event.getPlayer().getUniqueId();
        if (!inMatch.containsKey(u) && !watching.contains(u) && !waitSan.containsKey(u)) {
            return;
        }
        String cmd = event.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        cmd = cmd.substring(cmd.indexOf(':') + 1);
        if (!ALLOWED_CMDS.contains(cmd) && !event.getPlayer().hasPermission("duckymomo.admin")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(c("&cĐang ở đấu trường: chỉ dùng được /dautruong, /petinfo, /msg. Thoát: &f/dautruong roi"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        UUID u = p.getUniqueId();
        leaveQueue(u);
        invites.remove(u);
        if (waitSan.remove(u) != null) {
            restore(p);
            return;
        }
        watchSan.remove(u);
        if (watching.remove(u)) {
            restore(p);
            return;
        }
        Match m = inMatch.get(u);
        if (m != null) {
            if (m.state == State.END) {
                inMatch.remove(u);
                restore(p);
                return;
            }
            // thoát giữa trận = bị loại; về chỗ cũ ngay để lần vào sau không kẹt trong sân
            Saved s = saved.get(u);
            m.out.add(u);
            inMatch.remove(u);
            if (s != null) {
                p.setGameMode(s.mode() == GameMode.SPECTATOR ? GameMode.SURVIVAL : s.mode());
                p.teleport(s.loc());
                saved.remove(u);
                data.set("khoiphuc." + u, null);
                save();
            }
            broadcastMatch(m, "&c✖ &f" + p.getName() + " &cthoát game, bị loại.");
            checkEnd(m);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        String v = data.getString("khoiphuc." + p.getUniqueId());
        if (v == null && !p.getWorld().getName().equals(WORLD)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            data.set("khoiphuc." + p.getUniqueId(), null);
            save();
            String[] a = v == null ? null : v.split(",");
            World w = a == null ? null : Bukkit.getWorld(a[0]);
            p.setGameMode(a != null && a.length >= 7 && !a[6].equals("SPECTATOR") ? GameMode.valueOf(a[6]) : GameMode.SURVIVAL);
            p.teleport(w != null ? new Location(w, Double.parseDouble(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3]),
                    Float.parseFloat(a[4]), Float.parseFloat(a[5])) : Bukkit.getWorlds().get(0).getSpawnLocation());
            p.sendMessage(c("&7Đã đưa bạn về chỗ cũ sau trận đấu trường."));
        }, 20L);
    }

    /** Tắt plugin / reload: huỷ mọi trận, trả cược, đưa người về. */
    void shutdown() {
        for (Match m : new ArrayList<>(sanBusy.values())) {
            if (m.task != null) {
                m.task.cancel();
            }
            if (m.state != State.END) {
                refund(m);
            }
            for (UUID u : m.all()) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    p.sendMessage(c("&cServer nạp lại, trận bị huỷ và trả tiền cược."));
                    restore(p);
                }
            }
        }
        for (UUID u : new ArrayList<>(waitSan.keySet())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                restore(p);
            }
        }
        waitSan.clear();
        for (UUID u : new ArrayList<>(watching)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                restore(p);
            }
        }
        sanBusy.clear();
        inMatch.clear();
        watching.clear();
        // KHÔNG save() ở đây: mọi thay đổi đã lưu ngay khi đổi; lưu lúc tắt sẽ ghi đè file admin vừa sửa tay rồi /dmm reload
    }

    // ------------------------------------------------------------------ NPC ở spawn

    private static final List<String> NPC_LINES = List.of("&6&l⚔ ĐẤU TRƯỜNG ⚔", "&fNgười + pet · 1v1 · 2v2", "&7Chết không rơi đồ · có cược", "&e▶ Bấm để tới sảnh PvP");

    private org.bukkit.NamespacedKey npcKey() {
        return new org.bukkit.NamespacedKey(plugin, "dautruong_npc");
    }

    private boolean isNpc(Entity e) {
        return e.getPersistentDataContainer().has(npcKey(), org.bukkit.persistence.PersistentDataType.BYTE);
    }

    /** /dautruong admin npc [x y z hướng] (console: phải có toạ độ, ở thế giới chính) | npc xoa */
    private void npc(CommandSender s, String[] args) {
        World main = Bukkit.getWorlds().get(0);
        int removed = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (isNpc(e)) {
                    e.remove();
                    removed++;
                }
            }
        }
        if (args.length >= 3 && args[2].equalsIgnoreCase("xoa")) {
            s.sendMessage("Đã xoá " + removed + " thực thể NPC Đấu Trường (chunk đang nạp).");
            return;
        }
        Location loc;
        if (args.length >= 6) {
            loc = new Location(main, Double.parseDouble(args[2]), Double.parseDouble(args[3]), Double.parseDouble(args[4]),
                    args.length >= 6 ? Float.parseFloat(args[5]) : 0f, 0f);
        } else if (s instanceof Player p) {
            loc = p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
            loc.setYaw(p.getLocation().getYaw());
            loc.setPitch(0);
        } else {
            s.sendMessage("/dautruong admin npc <x> <y> <z> <hướng> (console) hoặc đứng tại chỗ trong game");
            return;
        }
        loc.getChunk().load();
        org.bukkit.entity.Villager v = loc.getWorld().spawn(loc, org.bukkit.entity.Villager.class, e -> {
            e.setAI(false);
            e.setSilent(true);
            e.setInvulnerable(true);
            e.setCollidable(false);
            e.setPersistent(true);
            e.setRemoveWhenFarAway(false);
            e.setProfession(org.bukkit.entity.Villager.Profession.WEAPONSMITH);
            e.setVillagerLevel(5);
            e.getPersistentDataContainer().set(npcKey(), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        });
        if (!v.isValid()) {
            s.sendMessage("Không đặt được dân làng ở đây (WorldGuard chặn sinh mob?).");
            return;
        }
        double y = 1.6 + (NPC_LINES.size() - 1) * 0.28;
        for (String line : NPC_LINES) {
            loc.getWorld().spawn(loc.clone().add(0, y, 0), org.bukkit.entity.ArmorStand.class, st -> {
                st.setMarker(true);
                st.setInvisible(true);
                st.setGravity(false);
                st.setInvulnerable(true);
                st.setPersistent(true);
                st.customName(c(line));
                st.setCustomNameVisible(true);
                st.getPersistentDataContainer().set(npcKey(), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 2);
            });
            y -= 0.28;
        }
        s.sendMessage("Đã đặt NPC Đấu Trường tại " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()
                + (removed > 0 ? " (xoá " + removed + " thực thể cũ)" : "") + ". Xoá: /dautruong admin npc xoa");
    }

    @EventHandler
    public void onNpcClick(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (!isNpc(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true); // không mở bảng mua bán của dân làng
        if (event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) {
            Player p = event.getPlayer();
            if (inMatch.containsKey(p.getUniqueId())) {
                return;
            }
            p.sendMessage(c("&6⚔ Tới sảnh Đấu Trường… &7(hoặc gõ &f/warp pvp&7)"));
            p.performCommand("warp pvp");   // qua Essentials: có chờ 3 giây như mọi lệnh dịch chuyển
        }
    }

    @EventHandler
    public void onNpcClickAt(org.bukkit.event.player.PlayerInteractAtEntityEvent event) {
        if (isNpc(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onNpcHurt(EntityDamageEvent event) {
        if (isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onNpcTransform(org.bukkit.event.entity.EntityTransformEvent event) {
        if (isNpc(event.getEntity())) {
            event.setCancelled(true); // sét đánh không biến thành phù thuỷ
        }
    }

    // ------------------------------------------------------------------ sảnh PvP + cổng

    private Location lobby() {
        String v = data.getString("sanh");
        if (v == null) {
            return null;
        }
        String[] p = v.split(",");
        World w = Bukkit.getWorld(p[0]);
        return w == null ? null : new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                Float.parseFloat(p[4]), Float.parseFloat(p[5]));
    }

    private static String locStr(Location l) {
        return l.getWorld().getName() + "," + l.getX() + "," + l.getY() + "," + l.getZ() + "," + l.getYaw() + "," + l.getPitch();
    }

    String sanName(String san) {
        return data.getString("ten." + san, "&f" + san);
    }

    private String gateAt(Location l) {
        ConfigurationSection g = data.getConfigurationSection("cong");
        if (g == null) {
            return null;
        }
        for (String san : g.getKeys(false)) {
            String[] v = g.getString(san, "").split(",");
            if (v.length < 6) {
                continue;
            }
            int[] b = new int[6];
            for (int i = 0; i < 6; i++) {
                b[i] = Integer.parseInt(v[i].trim());
            }
            int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
            if (x >= Math.min(b[0], b[3]) && x <= Math.max(b[0], b[3]) && y >= Math.min(b[1], b[4]) && y <= Math.max(b[1], b[4])
                    && z >= Math.min(b[2], b[5]) && z <= Math.max(b[2], b[5])) {
                return san;
            }
        }
        return null;
    }

    /** Thế giới đấu trường: không ai (trừ admin) phá/đặt khối, kể cả sảnh và sân. */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(org.bukkit.event.block.BlockBreakEvent event) {
        if (event.getBlock().getWorld().getName().equals(WORLD) && !event.getPlayer().hasPermission("duckymomo.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        if (event.getBlock().getWorld().getName().equals(WORLD) && !event.getPlayer().hasPermission("duckymomo.admin")) {
            event.setCancelled(true);
        }
    }

    /** Thế giới đấu trường: không cưỡi pet (Thú Cưỡi cấp 10+ bay được, bay ra khỏi sân / lơ lửng ngoài tầm đánh). */
    @EventHandler(ignoreCancelled = true)
    public void onMount(org.bukkit.event.entity.EntityMountEvent event) {
        if (event.getEntity() instanceof Player p && PetCombat.isPet(event.getMount())
                && event.getMount().getWorld().getName().equals(WORLD)) {
            event.setCancelled(true);
            p.sendMessage(c("&cĐấu trường không cho cưỡi pet."));
        }
    }

    @EventHandler
    public void onGate(PlayerMoveEvent event) {
        if (!event.hasChangedBlock() || !event.getTo().getWorld().getName().equals(WORLD)) {
            return;
        }
        Player p = event.getPlayer();
        UUID u = p.getUniqueId();
        if (inMatch.containsKey(u) || watching.contains(u)) {
            return;
        }
        String san = gateAt(event.getTo());
        if (san == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - gateCooldown.getOrDefault(u, 0L) < 3000L) {
            return;
        }
        gateCooldown.put(u, now);
        // đẩy nhẹ người chơi ra khỏi cổng (về phía sảnh) để không bật menu liên tục
        Location home = lobby();
        if (home != null) {
            org.bukkit.util.Vector v = home.toVector().subtract(p.getLocation().toVector()).setY(0);
            if (v.lengthSquared() > 0.01) {
                p.setVelocity(v.normalize().multiply(0.6).setY(0.25));
            }
        }
        gateEnter(p, san);
    }

    private Location waitPoint(String san, int i) {
        String[] order = {"a1", "b1", "a2", "b2"};
        Location l = point(san, order[i % 4]);
        return l != null ? l : point(san, "a1");
    }

    /** Bước vào cổng: sân trống -> vào thẳng sân chờ đối thủ (1v1, không cược); sân đang đấu -> vào xem. */
    private void gateEnter(Player p, String san) {
        UUID u = p.getUniqueId();
        if (busy(p)) {
            return;
        }
        if (sanBusy.containsKey(san)) {
            Match m = sanBusy.get(san);
            spectate(p, new String[] {"xem", Bukkit.getOfflinePlayer(m.a.get(0)).getName()});
            if (watching.contains(u)) {
                p.sendMessage(Component.text().append(c("&7Sân đang có trận, bạn đang xem. ")).append(button("&c[⬅ Thoát về sảnh]", "/dautruong roi", false, "Về sảnh PvP")).build());
            }
            return;
        }
        if (!sanReady(san, 1)) {
            p.sendMessage(c("&cSân này chưa sẵn sàng (admin chưa đặt điểm xuất phát)."));
            return;
        }
        leaveQueue(u);
        int n = 0;
        for (String v : waitSan.values()) {
            if (v.equals(san)) {
                n++;
            }
        }
        Location home = lobby();
        saved.put(u, new Saved(home != null ? home : p.getLocation().clone(), p.getGameMode()));
        persistRestore(p);
        waitSan.put(u, san);
        p.teleport(waitPoint(san, n));
        p.setGameMode(GameMode.SURVIVAL);
        heal(p);
        String key = "1:0:" + san;
        queues.computeIfAbsent(key, k -> new ArrayList<>()).add(u);
        queuedIn.put(u, key);
        waitMenu(p, san);
        tryMatch(key);
    }

    private void waitMenu(Player p, String san) {
        String k = queuedIn.getOrDefault(p.getUniqueId(), "1:0:" + san);
        String[] kp = k.split(":");
        int size = Integer.parseInt(kp[0]);
        p.sendMessage(c("&6&l⚔ " + sanName(san) + " &7— đang chờ đối thủ &f" + size + "v" + size + (kp[1].equals("0") ? " &7(không cược)" : " &ecược &f" + kp[1])));
        net.kyori.adventure.text.TextComponent.Builder row = Component.text();
        int other = size == 1 ? 2 : 1;
        if (sanReady(san, other)) {
            row.append(button("&a[Đổi sang " + other + "v" + other + "]", "/dautruong doi " + other + "v" + other, false, "Chờ trận " + other + "v" + other + " thay vì " + size + "v" + size));
            row.append(Component.text("  "));
        }
        row.append(button("&e[Cược…]", "/dautruong cuoc 100", true, "Gõ số xu thay cho 100 rồi Enter (0 = không cược). Chỉ ghép với người cùng mức."));
        row.append(Component.text("  "));
        row.append(button("&c[⬅ Thoát về sảnh]", "/dautruong roi", false, "Rời sân, về sảnh PvP"));
        p.sendMessage(row.build());
        p.sendMessage(c("&7Bedrock: gõ &f/dautruong doi 2v2 &7· &f/dautruong cuoc <xu> &7· &f/dautruong roi"));
    }

    /** Người đang chờ trong sân đổi kiểu (1v1/2v2) hoặc mức cược. */
    private void requeue(Player p, String mode, String stakeArg) {
        UUID u = p.getUniqueId();
        String san = waitSan.get(u);
        if (san == null) {
            p.sendMessage(c("&7Lệnh này dùng khi đang đứng chờ trong sân (bước vào một cổng ở &f/warp pvp&7)."));
            return;
        }
        String[] kp = queuedIn.getOrDefault(u, "1:0:" + san).split(":");
        int size = Integer.parseInt(kp[0]);
        double stake = Double.parseDouble(kp[1]);
        if (mode != null) {
            size = mode.equalsIgnoreCase("2v2") ? 2 : mode.equalsIgnoreCase("1v1") ? 1 : 0;
            if (size == 0 || !sanReady(san, size)) {
                p.sendMessage(c("&cSân này không có kiểu đó."));
                return;
            }
        }
        if (stakeArg != null) {
            stake = parseStake(p, stakeArg);
            if (stake < 0) {
                return;
            }
        }
        leaveQueue(u);
        String key = size + ":" + (long) stake + ":" + san;
        queues.computeIfAbsent(key, k -> new ArrayList<>()).add(u);
        queuedIn.put(u, key);
        waitMenu(p, san);
        tryMatch(key);
    }

    private Component button(String label, String cmd, boolean suggest, String hover) {
        return c(label).clickEvent(suggest ? net.kyori.adventure.text.event.ClickEvent.suggestCommand(cmd)
                : net.kyori.adventure.text.event.ClickEvent.runCommand(cmd)).hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(c("&7" + hover)));
    }

    /** Trạng thái sân cho bảng chữ nổi / menu cổng (mã màu &). */
    String status(String san) {
        Match m = sanBusy.get(san);
        if (m == null) {
            return "&aTrống &7— bước vào cổng để đấu";
        }
        String vs = "&f" + names(m.a) + " &7vs &f" + names(m.b);
        if (m.state == State.COUNTDOWN) {
            return "&eChuẩn bị: " + vs;
        }
        if (m.state == State.END) {
            return "&7Vừa xong: " + vs;
        }
        long rem = Math.max(0, (m.fightEnd - System.currentTimeMillis()) / 1000L);
        return "&cĐang đấu " + m.size + "v" + m.size + ": " + vs + " &7· còn &f" + rem + "s";
    }

    private int queued(String san) {
        int n = 0;
        for (Map.Entry<String, List<UUID>> e : queues.entrySet()) {
            String[] kp = e.getKey().split(":");
            if (kp.length > 2 && kp[2].equals(san)) {
                n += e.getValue().size();
            }
        }
        return n;
    }

    private int watchers(String san) {
        int n = 0;
        for (String v : watchSan.values()) {
            if (v.equals(san)) {
                n++;
            }
        }
        return n;
    }

    /** PlaceholderAPI %dmmdt_<sân>_trangthai|cho|xem|ten% cho bảng chữ nổi trên cổng (gọi chỉ khi có PlaceholderAPI). */
    me.clip.placeholderapi.expansion.PlaceholderExpansion placeholders() {
        return new me.clip.placeholderapi.expansion.PlaceholderExpansion() {
            @Override
            public String getIdentifier() {
                return "dmmdt";
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
            public String onRequest(org.bukkit.OfflinePlayer player, String params) {
                int i = params.lastIndexOf('_');
                if (i <= 0) {
                    return null;
                }
                String san = params.substring(0, i);
                String what = params.substring(i + 1);
                String out = switch (what) {
                    case "trangthai" -> status(san);
                    case "cho" -> "&7Hàng chờ: &f" + queued(san) + " &7người";
                    case "xem" -> "&7Đang xem: &f" + watchers(san) + " &7người";
                    case "ten" -> sanName(san);
                    default -> null;
                };
                return out == null ? null : LegacyComponentSerializer.legacySection().serialize(c(out));
            }
        };
    }

    // ------------------------------------------------------------------ xuất cho trang web

    /** plugins/DuckyMoMoCore/dautruong-live.json (mỗi 5 giây): trạng thái từng sân + top, trang web công khai đọc. */
    private void writeLive() {
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.addProperty("updated", System.currentTimeMillis() / 1000L);
        com.google.gson.JsonArray sans = new com.google.gson.JsonArray();
        for (String id : sanIds()) {
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("id", id);
            o.addProperty("ten", plainText(sanName(id)));
            o.addProperty("trangthai", plainText(status(id)));
            Match m = sanBusy.get(id);
            o.addProperty("dangdau", m != null);
            if (m != null) {
                o.addProperty("kieu", m.size + "v" + m.size);
                o.addProperty("a", names(m.a));
                o.addProperty("b", names(m.b));
                o.addProperty("conlai", m.state == State.FIGHT ? Math.max(0, (m.fightEnd - System.currentTimeMillis()) / 1000L) : -1);
                o.addProperty("cuoc", m.stake);
            }
            o.addProperty("cho", queued(id));
            o.addProperty("xem", watchers(id));
            sans.add(o);
        }
        root.add("san", sans);
        com.google.gson.JsonArray top = new com.google.gson.JsonArray();
        ConfigurationSection st = data.getConfigurationSection("stats");
        if (st != null) {
            List<String> ids = new ArrayList<>(st.getKeys(false));
            ids.removeIf(k -> st.getInt(k + ".thang", 0) + st.getInt(k + ".thua", 0) == 0);
            ids.sort(Comparator.comparingInt((String k) -> st.getInt(k + ".elo", 1000)).reversed());
            for (String k : ids.subList(0, Math.min(10, ids.size()))) {
                com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                o.addProperty("ten", st.getString(k + ".ten", "?"));
                o.addProperty("elo", st.getInt(k + ".elo", 1000));
                o.addProperty("thang", st.getInt(k + ".thang", 0));
                o.addProperty("thua", st.getInt(k + ".thua", 0));
                top.add(o);
            }
        }
        root.add("top", top);
        try {
            java.nio.file.Files.writeString(new File(plugin.getDataFolder(), "dautruong-live.json").toPath(), root.toString(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Không ghi được dautruong-live.json: " + e.getMessage());
        }
    }

    private static String plainText(String legacy) {
        return legacy.replaceAll("&[0-9a-fk-orA-FK-OR]", "");
    }

    // ------------------------------------------------------------------ tiện ích

    private static boolean sameIp(Player x, Player y) {
        return x != null && y != null && x.getAddress() != null && y.getAddress() != null
                && Objects.equals(x.getAddress().getAddress(), y.getAddress().getAddress())
                && !x.getAddress().getAddress().isLoopbackAddress();
    }

    private String names(List<UUID> team) {
        List<String> n = new ArrayList<>();
        for (UUID u : team) {
            n.add(Objects.requireNonNullElse(Bukkit.getOfflinePlayer(u).getName(), "?"));
        }
        return String.join(" + ", n);
    }

    private void broadcastMatch(Match m, String msg) {
        Component c = c(msg);
        Set<UUID> to = new HashSet<>(m.all());
        to.addAll(watching);
        for (UUID u : to) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.sendMessage(c);
            }
        }
    }

    private void title(Match m, String big, String small) {
        Title t = Title.title(c(big), c(small), Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofMillis(900), java.time.Duration.ofMillis(200)));
        for (UUID u : m.all()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.showTitle(t);
            }
        }
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được dautruong.yml: " + e.getMessage());
        }
    }

    private static String money(double v) {
        return VaultEconomy.available() ? VaultEconomy.format(v) : fmt(v) + " xu";
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
}
