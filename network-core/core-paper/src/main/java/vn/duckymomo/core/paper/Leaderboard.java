package vn.duckymomo.core.paper;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;

/**
 * Bảng xếp hạng mùa (chốt 14/10, docs/banghoi-luat-mua1.md mục 8 + 11C):
 *  - Bá Chủ (quốc gia, không tính Liên minh Hoà Bình): mỗi tuần (snapshot-day/time) +town-points mỗi thị trấn có >= 3 dân hoạt động,
 *    +1 mỗi plots-per-point ô; tuần double-week ×2. Trận (SiegeEndEvent): tấn công thắng +30, phòng thủ thắng +20, khởi nghĩa thắng +20;
 *    không tính nếu thị trấn bị vây < 3 dân hoặc lập < 3 ngày. Hội Bàn Tròn trừ điểm được (deduct).
 *  - Chiến Thần: hạ người có quân hàm phe kia trong vùng vây lúc có phiên +3 (cùng một người tối đa 3 lần/phiên), chiếm cờ thành công +5.
 *  - Đại Phú Hộ: tiền trong ví (bỏ tài khoản phụ). Kiến Trúc Sư: thị trấn đăng ký tuần register-week, người chơi (>= 5 giờ) bỏ 1 phiếu
 *    tuần vote-week cho thị trấn không phải của mình.
 *  - Đóng mùa: /bxh dongmua (khoá điểm, 3 ngày khiếu nại ở Hội Bàn Tròn) rồi /bxh traothuong (trao danh hiệu). Danh hiệu: /danhhieu.
 * Dữ liệu: plugins/DuckyMoMoCore/bxh.yml (danh hiệu giữ qua các mùa).
 */
final class Leaderboard implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final DuckyMoMoCore plugin;
    private final Activity activity;
    private final WarRules war;
    private final String peaceNation;
    private final ConfigurationSection cfg;
    private final Set<String> militaryRanks;
    private final File file;
    private final YamlConfiguration data;
    private final Map<String, Set<UUID>> bannerRewarded = new LinkedHashMap<>();

    Leaderboard(DuckyMoMoCore plugin, Activity activity, WarRules war, String peaceNation, ConfigurationSection c, Set<String> militaryRanks) {
        this.plugin = plugin;
        this.activity = activity;
        this.war = war;
        this.peaceNation = peaceNation == null ? "" : peaceNation;
        this.cfg = c;
        this.militaryRanks = militaryRanks;
        this.file = new File(plugin.getDataFolder(), "bxh.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        String season = c.getString("season", "1");
        if (!season.equals(data.getString("mua"))) {
            // Mùa mới: xoá điểm, giữ danh hiệu
            data.set("bachu", null);
            data.set("chienthan", null);
            data.set("phien", null);
            data.set("kts", null);
            data.set("chot", null);
            data.set("chup", null);
            data.set("ketqua", null);
            data.set("mua", season);
            save();
        }
    }

    void start() {
        Shared.leaderboard = this;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1200L, 1200L);
        hookSiegeEnd();
        hookBanner();
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                new BxhPlaceholders(this).register();
            } catch (RuntimeException | LinkageError e) {
                plugin.getLogger().warning("Không đăng ký được placeholder bảng xếp hạng: " + e);
            }
        }
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s);
    }

    private int pts(String key, int def) {
        return cfg.getInt("points." + key, def);
    }

    private boolean locked() {
        return data.getBoolean("chot");
    }

    // ------------------------------------------------------------------ tuần của mùa

    /** Tuần thứ mấy của mùa (1 = tuần đầu), 0 nếu chưa đặt ngày bắt đầu mùa. */
    int week() {
        String start = cfg.getString("season-start", "");
        if (start.isBlank()) {
            return 0;
        }
        try {
            long days = ChronoUnit.DAYS.between(LocalDate.parse(start), LocalDate.now(VN));
            return days < 0 ? 0 : (int) (days / 7) + 1;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private long weekStartMillis(int w) {
        try {
            return LocalDate.parse(cfg.getString("season-start", "")).plusDays(7L * (w - 1)).atStartOfDay(VN).toInstant().toEpochMilli();
        } catch (RuntimeException e) {
            return Long.MAX_VALUE;
        }
    }

    // ------------------------------------------------------------------ Bá Chủ

    private boolean isPeace(Nation n) {
        return n != null && n.getName().equalsIgnoreCase(peaceNation);
    }

    private void addNation(Nation n, int points, String why) {
        if (n == null || isPeace(n) || locked()) {
            return;
        }
        String k = "bachu." + n.getUUID();
        data.set(k + ".ten", n.getName());
        data.set(k + ".diem", data.getInt(k + ".diem") + points);
        List<String> log = new ArrayList<>(data.getStringList(k + ".lichsu"));
        log.add(LocalDate.now(VN) + " " + (points >= 0 ? "+" : "") + points + " " + why);
        data.set(k + ".lichsu", log);
    }

    /** Hội Bàn Tròn trừ điểm (vây giả, khiếu nại). */
    boolean deduct(String nationName, int points, String why) {
        Nation n = TownyAPI.getInstance().getNation(nationName);
        if (n == null) {
            return false;
        }
        boolean wasLocked = locked();
        data.set("chot", false);
        addNation(n, -Math.abs(points), "Hội Bàn Tròn: " + why);
        data.set("chot", wasLocked);
        save();
        return true;
    }

    private void snapshot() {
        int w = week();
        int mult = w > 0 && w == cfg.getInt("double-week", 10) ? 2 : 1;
        int minActive = cfg.getInt("town-active-residents", 3);
        int per = Math.max(1, cfg.getInt("plots-per-point", 10));
        List<String> summary = new ArrayList<>();
        for (Nation n : TownyAPI.getInstance().getNations()) {
            if (isPeace(n)) {
                continue;
            }
            int towns = (int) n.getTowns().stream().filter(t -> Members.activeCount(activity, t) >= minActive).count();
            int plots = n.getTowns().stream().mapToInt(Town::getNumTownBlocks).sum();
            int p = (towns * pts("town", 10) + plots / per) * mult;
            if (p > 0) {
                addNation(n, p, "lãnh thổ tuần " + (w > 0 ? w : "?") + ": " + towns + " thị trấn, " + plots + " ô" + (mult > 1 ? " (x2)" : ""));
                data.set("bachu." + n.getUUID() + ".o", plots);
            }
        }
        save();
        List<Map.Entry<String, Integer>> top = topNations(3);
        for (int i = 0; i < top.size(); i++) {
            summary.add((i + 1) + ". " + top.get(i).getKey() + " (" + top.get(i).getValue() + ")");
        }
        Bukkit.broadcast(c("&6⚑ Tổng kết tuần" + (w > 0 ? " " + w : "") + " - Bá Chủ: &f" + (summary.isEmpty() ? "chưa có" : String.join(" &7| &f", summary))
                + " &7(/bxh)"));
    }

    /** Top quốc gia theo điểm Bá Chủ: tên -> điểm. Hoà điểm: nhiều trận thắng hơn, rồi nhiều ô hơn. */
    List<Map.Entry<String, Integer>> topNations(int n) {
        ConfigurationSection bs = data.getConfigurationSection("bachu");
        if (bs == null) {
            return List.of();
        }
        return bs.getKeys(false).stream()
                .sorted(Comparator.comparingInt((String k) -> bs.getInt(k + ".diem")).thenComparingInt(k -> bs.getInt(k + ".tran"))
                        .thenComparingInt(k -> bs.getInt(k + ".o")).reversed())
                .limit(n).map(k -> Map.entry(bs.getString(k + ".ten", k), bs.getInt(k + ".diem"))).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private void hookSiegeEnd() {
        Plugin sw = Bukkit.getPluginManager().getPlugin("SiegeWar");
        if (sw == null || !sw.isEnabled()) {
            return;
        }
        try {
            Class<? extends Event> cls = (Class<? extends Event>) Class.forName("com.gmail.goosius.siegewar.events.SiegeEndEvent", true, sw.getClass().getClassLoader());
            Method getSiege = cls.getMethod("getSiege");
            Bukkit.getPluginManager().registerEvent(cls, this, EventPriority.MONITOR, (l, ev) -> {
                if (!cls.isInstance(ev)) {
                    return;
                }
                try {
                    Object siege = getSiege.invoke(ev);
                    Class<?> sc = siege.getClass();
                    Object status = sc.getMethod("getStatus").invoke(siege);
                    boolean attackersWon = (Boolean) status.getClass().getMethod("attackersWon").invoke(status);
                    boolean defendersWon = (Boolean) status.getClass().getMethod("defendersWon").invoke(status);
                    String type = String.valueOf(sc.getMethod("getSiegeType").invoke(siege));
                    Town town = (Town) sc.getMethod("getTown").invoke(siege);
                    Object attacker = sc.getMethod("getAttackingNationIfPossibleElseTown").invoke(siege);
                    Object defender = sc.getMethod("getDefendingNationIfPossibleElseTown").invoke(siege);
                    siegeEnded(type, town, attacker, defender, attackersWon, defendersWon);
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    plugin.getLogger().warning("Tính điểm trận lỗi: " + ex);
                }
            }, plugin, true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Không gắn được tính điểm trận (SiegeWar): " + e);
        }
    }

    private void siegeEnded(String type, Town town, Object attacker, Object defender, boolean attackersWon, boolean defendersWon) {
        if (town == null) {
            return;
        }
        long age = System.currentTimeMillis() - town.getRegistered();
        if (town.getResidents().size() < cfg.getInt("min-defender-residents", 3) || age < cfg.getInt("min-town-age-days", 3) * 86_400_000L) {
            Bukkit.broadcast(c("&7(Trận ở " + town.getName() + " không tính điểm Bá Chủ: thị trấn quá nhỏ hoặc mới lập.)"));
            return;
        }
        boolean revolt = type.toUpperCase(Locale.ROOT).contains("REVOLT");
        if (attackersWon && attacker instanceof Nation an) {
            addNation(an, revolt ? pts("revolt-win", 20) : pts("attack-win", 30), (revolt ? "khởi nghĩa thắng ở " : "tấn công thắng ở ") + town.getName());
            data.set("bachu." + an.getUUID() + ".tran", data.getInt("bachu." + an.getUUID() + ".tran") + 1);
        } else if (attackersWon && revolt && town.getNationOrNull() != null) {
            addNation(town.getNationOrNull(), pts("revolt-win", 20), "khởi nghĩa thắng ở " + town.getName());
        } else if (defendersWon && defender instanceof Nation dn) {
            addNation(dn, pts("defend-win", 20), "phòng thủ thắng ở " + town.getName());
            data.set("bachu." + dn.getUUID() + ".tran", data.getInt("bachu." + dn.getUUID() + ".tran") + 1);
        }
        save();
    }

    // ------------------------------------------------------------------ Chiến Thần

    private String sessionKey() {
        return LocalDateTime.now(VN).truncatedTo(ChronoUnit.HOURS).toString().replace(':', '-');
    }

    private void addWarrior(OfflinePlayer p, int points) {
        if (locked() || p == null) {
            return;
        }
        String k = "chienthan." + p.getUniqueId();
        data.set(k + ".ten", p.getName());
        data.set(k + ".diem", data.getInt(k + ".diem") + points);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        if (!(victim.getLastDamageCause() instanceof EntityDamageByEntityEvent last)) {
            return;
        }
        Player killer = Culprit.of(last.getDamager());   // pet / mũi tên tính về chủ
        if (killer == null || killer.equals(victim) || !SiegeWarHook.battleActive() || !SiegeWarHook.inActiveSiegeZone(victim.getLocation())) {
            return;
        }
        Resident rk = Members.resident(killer.getUniqueId());
        Resident rv = Members.resident(victim.getUniqueId());
        if (!Members.military(rk, militaryRanks) || !Members.military(rv, militaryRanks) || Members.sameSide(rk, rv)) {
            return;
        }
        if (war != null && (war.isAlt(killer.getName()) || war.isAlt(victim.getName()))) {
            return;
        }
        String k = "phien." + sessionKey() + "." + killer.getUniqueId() + "." + victim.getUniqueId();
        int n = data.getInt(k);
        if (n >= cfg.getInt("kill-cap", 3)) {
            return;
        }
        data.set(k, n + 1);
        addWarrior(killer, pts("kill", 3));
        save();
    }

    @SuppressWarnings("unchecked")
    private void hookBanner() {
        Plugin sw = Bukkit.getPluginManager().getPlugin("SiegeWar");
        if (sw == null || !sw.isEnabled()) {
            return;
        }
        try {
            Class<? extends Event> cls = (Class<? extends Event>) Class.forName("com.gmail.goosius.siegewar.events.BannerControlSessionEndedEvent", true, sw.getClass().getClassLoader());
            Method getSiege = cls.getMethod("getSiege");
            Bukkit.getPluginManager().registerEvent(cls, this, EventPriority.MONITOR, (l, ev) -> {
                if (!cls.isInstance(ev)) {
                    return;
                }
                try {
                    Object siege = getSiege.invoke(ev);
                    String id = String.valueOf(siege.getClass().getMethod("getUUID").invoke(siege));
                    List<Resident> ctrl = (List<Resident>) siege.getClass().getMethod("getBannerControllingResidents").invoke(siege);
                    Set<UUID> done = bannerRewarded.computeIfAbsent(id, x -> new HashSet<>());
                    Set<UUID> now = ctrl.stream().map(Resident::getUUID).collect(Collectors.toSet());
                    done.retainAll(now); // mất cờ rồi chiếm lại thì được tính lại
                    for (Resident r : ctrl) {
                        if (done.add(r.getUUID()) && (war == null || !war.isAlt(r.getName()))) {
                            addWarrior(Bukkit.getOfflinePlayer(r.getUUID()), pts("banner", 5));
                        }
                    }
                    save();
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    plugin.getLogger().warning("Tính điểm chiếm cờ lỗi: " + ex);
                }
            }, plugin, true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Không gắn được tính điểm chiếm cờ (SiegeWar): " + e);
        }
    }

    List<Map.Entry<String, Integer>> topWarriors(int n) {
        ConfigurationSection cs = data.getConfigurationSection("chienthan");
        if (cs == null) {
            return List.of();
        }
        return cs.getKeys(false).stream().sorted(Comparator.comparingInt((String k) -> cs.getInt(k + ".diem")).reversed())
                .limit(n).map(k -> Map.entry(cs.getString(k + ".ten", k), cs.getInt(k + ".diem"))).collect(Collectors.toList());
    }

    // ------------------------------------------------------------------ Đại Phú Hộ

    List<Map.Entry<String, Double>> topRich(int n) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault") || !VaultEconomy.available()) {
            return List.of();
        }
        List<Map.Entry<String, Double>> all = new ArrayList<>();
        for (String id : activity.knownPlayers()) {
            OfflinePlayer p = Bukkit.getOfflinePlayer(UUID.fromString(id));
            if (p.getName() == null || war != null && war.isAlt(p.getName())) {
                continue;
            }
            all.add(Map.entry(p.getName(), VaultEconomy.balance(p)));
        }
        all.sort(Map.Entry.<String, Double>comparingByValue().reversed());
        return all.subList(0, Math.min(n, all.size()));
    }

    // ------------------------------------------------------------------ Kiến Trúc Sư

    boolean voteCommand(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            return true;
        }
        int w = week();
        String sub = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "";
        List<String> reg = new ArrayList<>(data.getStringList("kts.dangky"));
        if (sub.equals("dangky")) {
            Resident r = Members.resident(p.getUniqueId());
            Town t = r == null ? null : r.getTownOrNull();
            if (t == null || !r.isMayor()) {
                p.sendMessage(c("&cChỉ thị trưởng đăng ký được."));
            } else if (w < cfg.getInt("register-week", 9) || locked()) {
                p.sendMessage(c("&cĐăng ký Kiến Trúc Sư mở từ tuần " + cfg.getInt("register-week", 9) + " của mùa (đang tuần " + w + ")."));
            } else if (!reg.contains(t.getUUID().toString())) {
                reg.add(t.getUUID().toString());
                data.set("kts.dangky", reg);
                save();
                Bukkit.broadcast(c("&b✎ " + t.getName() + " tham gia giải Kiến Trúc Sư. Tham quan: &f/binhchon xem " + t.getName()));
            }
            return true;
        }
        if (sub.equals("xem") && args.length >= 2) {
            Town t = TownyAPI.getInstance().getTown(args[1]);
            if (t == null || !reg.contains(t.getUUID().toString()) || t.getSpawnOrNull() == null) {
                p.sendMessage(c("&cThị trấn chưa đăng ký hoặc chưa đặt điểm hồi sinh."));
            } else {
                p.teleportAsync(t.getSpawnOrNull());
            }
            return true;
        }
        if (args.length >= 1 && !sub.equals("danhsach")) {
            Town t = TownyAPI.getInstance().getTown(args[0]);
            Resident r = Members.resident(p.getUniqueId());
            if (w != cfg.getInt("vote-week", 10) || locked()) {
                p.sendMessage(c("&cBình chọn chỉ mở trong tuần " + cfg.getInt("vote-week", 10) + " của mùa."));
            } else if (t == null || !reg.contains(t.getUUID().toString())) {
                p.sendMessage(c("&cThị trấn chưa đăng ký."));
            } else if (r != null && r.getTownOrNull() == t) {
                p.sendMessage(c("&cKhông bỏ phiếu cho thị trấn của mình."));
            } else if (activity.totalHours(p.getUniqueId()) < 5 || war != null && war.isAlt(p.getName())) {
                p.sendMessage(c("&cCần chơi thật ít nhất 5 giờ và không phải tài khoản phụ."));
            } else if (data.isSet("kts.phieu." + p.getUniqueId())) {
                p.sendMessage(c("&cBạn đã bỏ phiếu rồi."));
            } else {
                data.set("kts.phieu." + p.getUniqueId(), t.getUUID().toString());
                save();
                p.sendMessage(c("&aĐã bình chọn cho " + t.getName() + "."));
            }
            return true;
        }
        s.sendMessage(c("&bKiến Trúc Sư &7(tuần " + w + "): đăng ký tuần " + cfg.getInt("register-week", 9) + ", bình chọn tuần " + cfg.getInt("vote-week", 10)));
        for (Map.Entry<String, Integer> e : architectVotes().entrySet()) {
            s.sendMessage(c("&f" + e.getKey() + " &7- " + e.getValue() + " phiếu"));
        }
        s.sendMessage(c("&7/binhchon dangky (thị trưởng) · xem <thị trấn> · <thị trấn> (bỏ phiếu)"));
        return true;
    }

    Map<String, Integer> architectVotes() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String id : data.getStringList("kts.dangky")) {
            Town t = TownyAPI.getInstance().getTown(UUID.fromString(id));
            if (t != null) {
                out.put(t.getName(), 0);
            }
        }
        ConfigurationSection ph = data.getConfigurationSection("kts.phieu");
        if (ph != null) {
            for (String voter : ph.getKeys(false)) {
                Town t = TownyAPI.getInstance().getTown(UUID.fromString(ph.getString(voter)));
                if (t != null) {
                    out.merge(t.getName(), 1, Integer::sum);
                }
            }
        }
        return out.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    // ------------------------------------------------------------------ mỗi phút

    private void tick() {
        if (locked()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(VN);
        DayOfWeek day;
        LocalTime at;
        try {
            day = DayOfWeek.valueOf(cfg.getString("snapshot-day", "SUNDAY").toUpperCase(Locale.ROOT));
            at = LocalTime.parse(cfg.getString("snapshot-time", "23:00"));
        } catch (RuntimeException e) {
            return;
        }
        String key = now.toLocalDate().toString();
        if (now.getDayOfWeek() == day && !now.toLocalTime().isBefore(at) && !key.equals(data.getString("chup"))) {
            data.set("chup", key);
            snapshot();
        }
    }

    // ------------------------------------------------------------------ /bxh

    boolean command(CommandSender s, String[] args) {
        String sub = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (s.hasPermission("duckymomo.admin") && (sub.equals("dongmua") || sub.equals("traothuong") || sub.equals("hologram"))) {
            admin(s, sub);
            return true;
        }
        if (sub.isEmpty() && s instanceof Player p && BedrockMenu.available() && BedrockMenu.isBedrock(p.getUniqueId())) {
            BedrockMenu.open(p.getUniqueId(), "Bảng xếp hạng mùa", "Chọn bảng muốn xem:",
                    List.of(new BedrockMenu.Entry("Bá Chủ (quốc gia)", "bachu"), new BedrockMenu.Entry("Chiến Thần", "chienthan"),
                            new BedrockMenu.Entry("Đại Phú Hộ", "phuho"), new BedrockMenu.Entry("Kiến Trúc Sư", "kientruc")),
                    x -> plugin.getServer().getScheduler().runTask(plugin, () -> p.performCommand("bxh " + x)));
            return true;
        }
        int w = week();
        String head = "&6BẢNG XẾP HẠNG MÙA " + data.getString("mua") + (w > 0 ? " &7(tuần " + w + ")" : "") + (locked() ? " &c(đã khoá)" : "");
        switch (sub) {
            case "chienthan" -> {
                s.sendMessage(c(head + " &f- Chiến Thần"));
                int i = 1;
                for (Map.Entry<String, Integer> e : topWarriors(10)) {
                    s.sendMessage(c("&e" + i++ + ". &f" + e.getKey() + " &7- " + e.getValue() + " điểm"));
                }
            }
            case "phuho" -> {
                s.sendMessage(c(head + " &f- Đại Phú Hộ"));
                int i = 1;
                for (Map.Entry<String, Double> e : topRich(10)) {
                    s.sendMessage(c(String.format(Locale.ROOT, "&e%d. &f%s &7- %.0f xu", i++, e.getKey(), e.getValue())));
                }
            }
            case "kientruc" -> voteCommand(s, new String[] {"danhsach"});
            case "quocgia" -> {
                ConfigurationSection bs = data.getConfigurationSection("bachu");
                String name = args.length >= 2 ? args[1] : "";
                if (bs != null) {
                    for (String k : bs.getKeys(false)) {
                        if (bs.getString(k + ".ten", "").equalsIgnoreCase(name)) {
                            s.sendMessage(c("&6" + bs.getString(k + ".ten") + ": &f" + bs.getInt(k + ".diem") + " điểm"));
                            List<String> log = bs.getStringList(k + ".lichsu");
                            log.subList(Math.max(0, log.size() - 10), log.size()).forEach(x -> s.sendMessage(c("&7" + x)));
                        }
                    }
                }
            }
            default -> {
                s.sendMessage(c(head + " &f- Bá Chủ"));
                int i = 1;
                for (Map.Entry<String, Integer> e : topNations(10)) {
                    s.sendMessage(c("&e" + i++ + ". &f" + e.getKey() + " &7- " + e.getValue() + " điểm"));
                }
                s.sendMessage(c("&7/bxh chienthan · phuho · kientruc · quocgia <tên> (xem điểm đến từ đâu) · /danhhieu"));
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ đóng mùa, danh hiệu

    private void admin(CommandSender s, String sub) {
        switch (sub) {
            case "dongmua" -> {
                data.set("chot", true);
                data.set("chot-luc", System.currentTimeMillis());
                save();
                Bukkit.broadcast(c("&6⚑ Mùa " + data.getString("mua") + " đã đóng! Điểm đã khoá. Khiếu nại bảng xếp hạng gửi Hội Bàn Tròn trong 3 ngày (/bantron kiennghi)."));
            }
            case "traothuong" -> {
                if (!locked()) {
                    s.sendMessage("Chưa đóng mùa: /bxh dongmua trước.");
                    return;
                }
                if (System.currentTimeMillis() - data.getLong("chot-luc") < 3 * 86_400_000L) {
                    s.sendMessage("Chưa đủ 3 ngày khiếu nại kể từ lúc đóng mùa.");
                    return;
                }
                award(s);
            }
            case "hologram" -> s.sendMessage(BxhPlaceholders.hologramHelp());
            default -> {
            }
        }
    }

    /** Ai được danh hiệu cả quốc gia/thị trấn: thành viên từ trước tuần title-member-before-week và chơi thật >= title-min-hours. */
    private boolean eligible(Resident r, Town t) {
        if (r == null || activity.totalHours(r.getUUID()) < cfg.getDouble("title-min-hours", 10) || war != null && war.isAlt(r.getName())) {
            return false;
        }
        long cut = weekStartMillis(cfg.getInt("title-member-before-week", 9));
        boolean joinedTown = r.getJoinedTownAt() <= 0 || r.getJoinedTownAt() < cut;
        boolean townInNation = t == null || t.getJoinedNationAt() <= 0 || t.getJoinedNationAt() < cut;
        return joinedTown && townInNation;
    }

    private void award(CommandSender s) {
        String season = data.getString("mua");
        List<Map.Entry<String, Integer>> nations = topNations(3);
        int given = 0;
        for (int i = 0; i < nations.size(); i++) {
            Nation n = TownyAPI.getInstance().getNation(nations.get(i).getKey());
            if (n == null) {
                continue;
            }
            String title = i == 0 ? "bachu" : "achu";
            for (Town t : n.getTowns()) {
                for (Resident r : t.getResidents()) {
                    if (eligible(r, t)) {
                        given += grant(r.getUUID(), r.getName(), title + season) ? 1 : 0;
                    }
                }
            }
        }
        List<Map.Entry<String, Integer>> warriors = topWarriors(3);
        for (int i = 0; i < warriors.size(); i++) {
            OfflinePlayer p = Bukkit.getOfflinePlayerIfCached(warriors.get(i).getKey());
            if (p != null) {
                given += grant(p.getUniqueId(), p.getName(), (i == 0 ? "chienthan" : "dungtuong") + season) ? 1 : 0;
            }
        }
        List<Map.Entry<String, Double>> rich = topRich(1);
        if (!rich.isEmpty()) {
            OfflinePlayer p = Bukkit.getOfflinePlayerIfCached(rich.get(0).getKey());
            if (p != null) {
                given += grant(p.getUniqueId(), p.getName(), "phuho" + season) ? 1 : 0;
            }
        }
        Map<String, Integer> kts = architectVotes();
        if (!kts.isEmpty()) {
            Town t = TownyAPI.getInstance().getTown(kts.keySet().iterator().next());
            if (t != null) {
                for (Resident r : t.getResidents()) {
                    if (eligible(r, t)) {
                        given += grant(r.getUUID(), r.getName(), "kientrucsu" + season) ? 1 : 0;
                    }
                }
            }
        }
        for (String id : activity.knownPlayers()) {
            UUID u = UUID.fromString(id);
            if (activity.totalHours(u) >= cfg.getDouble("title-min-hours", 10)) {
                OfflinePlayer p = Bukkit.getOfflinePlayer(u);
                given += grant(u, p.getName(), "mua" + season) ? 1 : 0;
            }
        }
        save();
        Bukkit.broadcast(c("&6⚑ Đã trao danh hiệu mùa " + season + " (" + given + " lượt). Xem và đổi danh hiệu: &f/danhhieu"));
        s.sendMessage("Đã trao " + given + " danh hiệu.");
    }

    /** Người chơi chưa có danh hiệu này (mua được). */
    boolean grantable(UUID id, String title) {
        return !data.getStringList("danhhieu." + id + ".co").contains(title);
    }

    /** Thêm danh hiệu (mã) cho người chơi; true nếu mới. */
    boolean grant(UUID id, String name, String title) {
        List<String> have = new ArrayList<>(data.getStringList("danhhieu." + id + ".co"));
        if (have.contains(title)) {
            return false;
        }
        have.add(title);
        data.set("danhhieu." + id + ".co", have);
        data.set("danhhieu." + id + ".ten", name);
        save();
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            p.sendMessage(c("&6Bạn nhận danh hiệu " + titleText(title) + "&6! Dùng: &f/danhhieu chon " + title));
        }
        return true;
    }

    /** Chữ hiển thị của danh hiệu: titles.<mã> trong config, mã có số mùa ở cuối thì thay {mua}. */
    String titleText(String title) {
        String base = title.replaceAll("\\d+$", "");
        String season = title.substring(base.length());
        String t = cfg.getString("titles." + title, cfg.getString("titles." + base, "&7[" + title + "]"));
        return t.replace("{mua}", season);
    }

    boolean titleCommand(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            return true;
        }
        String sub = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "";
        List<String> have = data.getStringList("danhhieu." + p.getUniqueId() + ".co");
        switch (sub) {
            case "chon" -> {
                if (args.length < 2 || !have.contains(args[1])) {
                    p.sendMessage(c("&cBạn không có danh hiệu này."));
                    return true;
                }
                data.set("danhhieu." + p.getUniqueId() + ".dung", args[1]);
                save();
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + p.getUniqueId() + " meta removesuffix 60");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + p.getUniqueId() + " meta addsuffix 60 \" " + titleText(args[1]) + "\"");
                p.sendMessage(c("&aĐang dùng danh hiệu " + titleText(args[1])));
            }
            case "tat" -> {
                data.set("danhhieu." + p.getUniqueId() + ".dung", null);
                save();
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + p.getUniqueId() + " meta removesuffix 60");
                p.sendMessage(c("&aĐã ẩn danh hiệu."));
            }
            default -> {
                p.sendMessage(c("&6Danh hiệu của bạn &7(giữ qua các mùa, chỉ giành được trong game)"));
                if (have.isEmpty()) {
                    p.sendMessage(c("&7Chưa có. Giành bằng bảng xếp hạng mùa (/bxh) hoặc mua bằng xu ở /cuahang."));
                }
                for (String t : have) {
                    p.sendMessage(c("&f" + t + " &7→ " + titleText(t)));
                }
                p.sendMessage(c("&7/danhhieu chon <mã> · /danhhieu tat"));
            }
        }
        return true;
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được bxh.yml: " + e.getMessage());
        }
    }
}
