package vn.duckymomo.core.paper;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownBlockType;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

/**
 * Án giết dân hoà bình + Hội Bàn Tròn (chốt 14/10, docs/banghoi-luat-mua1.md mục 10.3 + 11B).
 *  - Dân Liên minh Hoà Bình bị giết trực tiếp trong đất hoà bình hoặc dải đệm buffer-blocks → mở vụ án, trừ: tự vệ (nạn nhân đánh trước
 *    trong self-defense-seconds giây), trong vùng vây lúc có phiên, ô đấu trường.
 *  - Mức sàn tự động: bồi thường floor-fine (×repeat-multiplier mỗi lần tái phạm trong mùa) + cấm vào đất hoà bình + đình chỉ công thành
 *    (cấm vào vùng vây) cho tới khi xử xong.
 *  - Hội Bàn Tròn: thủ lĩnh seats quốc gia đứng đầu Bá Chủ + đại diện hoà bình; họp trực tiếp tại "bàn tròn" trong một thị trấn hoà bình
 *    (đại diện chọn, quá hạn thì bốc ngẫu nhiên), phải đứng trong table-radius block mới bỏ phiếu; cần min-seats ghế; xử hết vụ thì tan;
 *    không có việc thì không họp.
 *    Mức phạt thêm: phạt nặng, truy nã, tù, lưu đày, cấm công thành 1-3 tuần; không được tha mức sàn. Kiến nghị: trừ điểm Bá Chủ,
 *    thu hồi giấy phép (2/3), mở lại vụ (kháng án, 1 lần).
 *  - Dân hoà bình giết người không phải tự vệ: bị đuổi khỏi thị trấn (thị trưởng: Hội đồng Hoà Bình bỏ phiếu đuổi thị trấn).
 * Dữ liệu: plugins/DuckyMoMoCore/an.yml (công khai: /bantron lichsu).
 */
final class Justice implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm dd/MM").withZone(VN);
    private static final long MIN = 60_000L;
    /** Mức phạt, từ nhẹ tới nặng (hoà phiếu chọn mức nhẹ hơn). */
    static final List<String> OPTIONS = List.of("giu", "phatnang", "truyna", "tu", "luuday", "camct1", "camct2", "camct3");

    private final DuckyMoMoCore plugin;
    private final PeaceAlliance peace;
    private final PeaceCouncil council;
    private final Organizations orgs;
    private final Leaderboard board;
    private final WarRules war;
    private final ConfigurationSection cfg;
    private final ConfigurationSection rt;
    private final File file;
    private final YamlConfiguration data;
    private final Map<String, Long> hits = new HashMap<>();
    private final Map<UUID, Long> lastWarn = new HashMap<>();
    private Set<UUID> openKillers = new HashSet<>();

    Justice(DuckyMoMoCore plugin, PeaceAlliance peace, PeaceCouncil council, Organizations orgs, Leaderboard board, WarRules war,
            ConfigurationSection c) {
        this.plugin = plugin;
        this.peace = peace;
        this.council = council;
        this.orgs = orgs;
        this.board = board;
        this.war = war;
        this.cfg = c;
        this.rt = c.getConfigurationSection("roundtable") != null ? c.getConfigurationSection("roundtable") : c.createSection("roundtable");
        this.file = new File(plugin.getDataFolder(), "an.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        rebuildOpen();
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 400L, 400L);
        hookSiegeStart();
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s);
    }

    private static void console(String cmd) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    /** "21:00 thứ 4 15/10" */
    private static String when(long ms) {
        Instant i = Instant.ofEpochMilli(ms);
        DayOfWeek d = i.atZone(VN).getDayOfWeek();
        String day = d == DayOfWeek.SUNDAY ? "Chủ nhật" : "thứ " + (d.getValue() + 1);
        String[] hm = HM.format(i).split(" ");
        return hm[0] + " " + day + " " + hm[1];
    }

    // ------------------------------------------------------------------ lệnh cấm

    private void rebuildOpen() {
        Set<UUID> s = new HashSet<>();
        ConfigurationSection vs = data.getConfigurationSection("vu");
        if (vs != null) {
            for (String id : vs.getKeys(false)) {
                if ("MO".equals(vs.getString(id + ".trangthai"))) {
                    s.add(UUID.fromString(vs.getString(id + ".ke-uuid")));
                }
            }
        }
        openKillers = s;
    }

    boolean bannedPeace(UUID id) {
        return openKillers.contains(id) || data.getLong("cam." + id + ".hoabinh") > System.currentTimeMillis();
    }

    boolean bannedSiege(UUID id) {
        return openKillers.contains(id) || data.getLong("cam." + id + ".congthanh") > System.currentTimeMillis();
    }

    private boolean ownTownLand(Player p, Location l) {
        Town at = TownyAPI.getInstance().getTown(l);
        Resident r = TownyAPI.getInstance().getResident(p);
        return at != null && r != null && r.getTownOrNull() == at;
    }

    /** Chỗ đưa người bị cấm ra: exile-spawn trong config, không có thì ngoài vòng kinh đô về phía đông. */
    private Location exile(World w) {
        String s = cfg.getString("exile-spawn", "");
        String[] p = s.trim().split("[\\s,]+");
        if (p.length >= 3) {
            try {
                return new Location(w, Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
            } catch (NumberFormatException ignored) {
                // dùng mặc định
            }
        }
        List<Integer> sp = plugin.getConfig().getIntegerList("town-rules.spawn");
        int x = (sp.size() >= 2 ? sp.get(0) : 0) + plugin.getConfig().getInt("town-rules.reserved-spawn-radius", 300) + 40;
        int z = sp.size() >= 2 ? sp.get(1) : 0;
        return w.getHighestBlockAt(x, z).getLocation().add(0.5, 1, 0.5);
    }

    private void warn(Player p, String msg) {
        Long last = lastWarn.get(p.getUniqueId());
        if (last == null || System.currentTimeMillis() - last > 3000) {
            lastWarn.put(p.getUniqueId(), System.currentTimeMillis());
            p.sendActionBar(c(msg));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        Location from = e.getFrom();
        if (to.getBlockX() == from.getBlockX() && to.getBlockZ() == from.getBlockZ()) {
            return;
        }
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (!openKillers.contains(id) && data.getConfigurationSection("cam." + id) == null) {
            return;
        }
        if (bannedPeace(id) && peace.inPeaceLand(to)) {
            if (peace.inPeaceLand(from)) {
                p.teleportAsync(exile(p.getWorld()));
            } else {
                e.setCancelled(true);
            }
            warn(p, "&cBạn đang bị cấm vào đất Liên minh Hoà Bình (/bantron toi).");
            return;
        }
        if (bannedSiege(id) && SiegeWarHook.inActiveSiegeZone(to) && !ownTownLand(p, to)) {
            if (SiegeWarHook.inActiveSiegeZone(from) && !ownTownLand(p, from)) {
                p.teleportAsync(exile(p.getWorld()));
            } else {
                e.setCancelled(true);
            }
            warn(p, "&cBạn đang bị cấm công thành: không được vào vùng vây (/bantron toi).");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        Location to = e.getTo();
        if (bannedPeace(p.getUniqueId()) && peace.inPeaceLand(to)) {
            e.setCancelled(true);
            p.sendMessage(c("&cBạn đang bị cấm vào đất Liên minh Hoà Bình."));
            return;
        }
        if (bannedSiege(p.getUniqueId()) && SiegeWarHook.inActiveSiegeZone(to) && !ownTownLand(p, to)) {
            e.setCancelled(true);
            p.sendMessage(c("&cBạn đang bị cấm công thành: không được vào vùng vây."));
            return;
        }
        // Gần giờ họp Hội Bàn Tròn: không dịch chuyển thẳng tới gần bàn tròn, phải đi đường
        PlayerTeleportEvent.TeleportCause cause = e.getCause();
        if ((cause == PlayerTeleportEvent.TeleportCause.COMMAND || cause == PlayerTeleportEvent.TeleportCause.PLUGIN) && noTeleportActive()) {
            Location table = table();
            int r = rt.getInt("no-teleport-radius", 150);
            if (table != null && to.getWorld() == table.getWorld() && to.distanceSquared(table) <= (double) r * r
                    && (p.getWorld() != table.getWorld() || p.getLocation().distanceSquared(table) > (double) r * r)
                    && !p.hasPermission("duckymomo.admin")) {
                e.setCancelled(true);
                p.sendMessage(c("&eSắp họp Hội Bàn Tròn: không dịch chuyển tới gần nơi họp (" + r + " block), hãy đi đường."));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        if (bannedPeace(e.getPlayer().getUniqueId()) && peace.inPeaceLand(e.getRespawnLocation())) {
            e.setRespawnLocation(exile(e.getRespawnLocation().getWorld()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        int jail = data.getInt("tu." + p.getUniqueId());
        if (jail > 0) {
            data.set("tu." + p.getUniqueId(), null);
            save();
            Bukkit.getScheduler().runTaskLater(plugin, () -> console("jail " + p.getName() + " " + cfg.getString("jail-name", "nhatu") + " " + jail + "m"), 40L);
        }
        if (bannedPeace(p.getUniqueId()) && peace.inPeaceLand(p.getLocation())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> p.teleportAsync(exile(p.getWorld())), 20L);
        }
    }

    // ------------------------------------------------------------------ giết người

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Player a = e.getDamager() instanceof Player p ? p
                : e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p2 ? p2 : null;
        if (a != null && e.getEntity() instanceof Player v) {
            hits.put(a.getUniqueId() + "|" + v.getUniqueId(), System.currentTimeMillis());
        }
    }

    /** a đã đánh b trong self-defense-seconds giây qua. */
    private boolean hitRecently(UUID a, UUID b) {
        Long t = hits.get(a + "|" + b);
        return t != null && System.currentTimeMillis() - t <= cfg.getInt("self-defense-seconds", 30) * 1000L;
    }

    private boolean nearPeaceLand(Location l) {
        if (peace.inPeaceLand(l)) {
            return true;
        }
        Nation n = peace.nation();
        int buf = cfg.getInt("buffer-blocks", 100);
        if (n == null) {
            return false;
        }
        for (Town t : n.getTowns()) {
            for (TownBlock tb : t.getTownBlocks()) {
                if (tb.getWorld() == null || !tb.getWorld().getName().equals(l.getWorld().getName())) {
                    continue;
                }
                double dx = Math.max(Math.abs(l.getX() - (tb.getX() * 16 + 8)) - 8, 0);
                double dz = Math.max(Math.abs(l.getZ() - (tb.getZ() * 16 + 8)) - 8, 0);
                if (dx * dx + dz * dz <= (double) buf * buf) {
                    return true;
                }
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        if (!(victim.getLastDamageCause() instanceof EntityDamageByEntityEvent last)) {
            return;
        }
        Player killer = last.getDamager() instanceof Player p ? p
                : last.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p2 ? p2 : null;
        if (killer == null || killer.equals(victim)) {
            return;
        }
        Location loc = victim.getLocation();
        // Truy nã: ai hạ kẻ bị truy nã thì nhận tiền thưởng
        payBounty(victim, killer);
        TownBlock tb = TownyAPI.getInstance().getTownBlock(loc);
        if (tb != null && tb.getType() == TownBlockType.ARENA) {
            return; // đấu trường
        }
        if (SiegeWarHook.battleActive() && SiegeWarHook.inActiveSiegeZone(loc)) {
            return; // đã báo di tản, không mở án
        }
        boolean selfDefense = hitRecently(victim.getUniqueId(), killer.getUniqueId());
        Resident rv = Members.resident(victim.getUniqueId());
        Resident rk = Members.resident(killer.getUniqueId());
        boolean victimPeace = rv != null && peace.isMember(rv.getTownOrNull());
        boolean killerPeace = rk != null && peace.isMember(rk.getTownOrNull());
        if (victimPeace && !selfDefense && nearPeaceLand(loc)) {
            openCase(killer, victim, loc, last.getDamager().getType().name());
        }
        if (killerPeace && !selfDefense) {
            peaceKiller(killer, rk, victim);
        }
    }

    private void peaceKiller(Player killer, Resident rk, Player victim) {
        Town t = rk.getTownOrNull();
        if (t == null) {
            return;
        }
        if (rk.isMayor()) {
            council.propose("DUOI", t.getName(), "Hệ thống", "thị trưởng " + killer.getName() + " giết " + victim.getName() + " không phải tự vệ", 0.5,
                    t.getUUID().toString());
        } else {
            rk.removeTown();
            rk.save();
            Bukkit.broadcast(c("&c" + killer.getName() + " (dân hoà bình) giết " + victim.getName() + " không phải tự vệ: bị đuổi khỏi " + t.getName() + "."));
        }
    }

    private void openCase(Player killer, Player victim, Location l, String weapon) {
        int n = data.getInt("so-vu") + 1;
        data.set("so-vu", n);
        String id = "V" + n;
        int prior = data.getInt("lan." + killer.getUniqueId());
        data.set("lan." + killer.getUniqueId(), prior + 1);
        double floor = cfg.getDouble("floor-fine", 100) * Math.pow(cfg.getDouble("repeat-multiplier", 2), prior);
        String k = "vu." + id;
        data.set(k + ".ke", killer.getName());
        data.set(k + ".ke-uuid", killer.getUniqueId().toString());
        data.set(k + ".nan", victim.getName());
        data.set(k + ".nan-uuid", victim.getUniqueId().toString());
        data.set(k + ".luc", System.currentTimeMillis());
        data.set(k + ".noi", l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ());
        data.set(k + ".vukhi", weapon);
        data.set(k + ".trangthai", "MO");
        data.set(k + ".san", floor);
        data.set(k + ".tai-pham", prior);
        double paid = transfer(killer, victim, floor);
        data.set(k + ".da-boi-thuong", paid);
        save();
        rebuildOpen();
        Bukkit.broadcast(c(String.format(Locale.ROOT,
                "&c⚖ Vụ án %s: &f%s &cgiết dân hoà bình &f%s&c. Mức sàn: bồi thường %.0f xu%s, cấm vào đất hoà bình và cấm công thành tới khi Hội Bàn Tròn xử (%s).",
                id, killer.getName(), victim.getName(), floor, prior > 0 ? " (tái phạm lần " + (prior + 1) + ")" : "", when(meetingTime()))));
        if (bannedSiege(killer.getUniqueId()) && SiegeWarHook.inActiveSiegeZone(killer.getLocation()) && !ownTownLand(killer, killer.getLocation())) {
            killer.teleportAsync(exile(killer.getWorld()));
        }
    }

    /** Chuyển tiền (lấy được bao nhiêu thì lấy) từ a sang b; trả về số đã chuyển. */
    private double transfer(OfflinePlayer from, OfflinePlayer to, double amount) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault") || !VaultEconomy.available() || amount <= 0) {
            return 0;
        }
        double take = Math.min(amount, Math.max(0, VaultEconomy.balance(from)));
        if (take > 0 && VaultEconomy.withdraw(from, take)) {
            if (to != null) {
                VaultEconomy.deposit(to, take);
            }
            return take;
        }
        return 0;
    }

    private void payBounty(Player victim, Player killer) {
        String k = "truyna." + victim.getUniqueId();
        if (data.getLong(k + ".den") <= System.currentTimeMillis()) {
            return;
        }
        if (war != null && war.isAlt(killer.getName())) {
            return;
        }
        double reward = data.getDouble(k + ".tien");
        if (reward > 0) {
            VaultEconomy.deposit(killer, reward);
        }
        data.set(k, null);
        save();
        console("lp user " + victim.getName() + " meta removetempsuffix 70");
        Bukkit.broadcast(c(String.format(Locale.ROOT, "&6⚖ %s đã hạ kẻ bị truy nã %s, nhận %.0f xu tiền thưởng.", killer.getName(), victim.getName(), reward)));
    }

    // ------------------------------------------------------------------ ghế Hội Bàn Tròn

    /** Ghế: "qg:<quốc gia>" cho top quốc gia, "hb" cho đại diện hoà bình. */
    LinkedHashMap<String, String> seats() {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        int n = rt.getInt("seats", 5);
        for (Map.Entry<String, Integer> e : board.topNations(n)) {
            out.put("qg:" + e.getKey(), e.getKey());
        }
        if (out.size() < n) {
            // Đầu mùa chưa có điểm: lấy quốc gia nhiều thị trấn nhất
            TownyAPI.getInstance().getNations().stream()
                    .filter(x -> !x.getName().equalsIgnoreCase(peace.nationName()) && !out.containsKey("qg:" + x.getName()))
                    .sorted((a, b) -> b.getTowns().size() != a.getTowns().size() ? b.getTowns().size() - a.getTowns().size()
                            : Integer.compare(b.getTowns().stream().mapToInt(Town::getNumTownBlocks).sum(), a.getTowns().stream().mapToInt(Town::getNumTownBlocks).sum()))
                    .limit(n - out.size()).forEach(x -> out.put("qg:" + x.getName(), x.getName()));
        }
        out.put("hb", "Liên minh Hoà Bình");
        return out;
    }

    /** Ghế mà người này đang ngồi (vua, sứ thần còn hạn, hoặc đại diện hoà bình); null nếu không có. */
    private String seatOf(Player p) {
        if (council.isRepresentative(p)) {
            return "hb";
        }
        Resident r = TownyAPI.getInstance().getResident(p);
        for (String seat : seats().keySet()) {
            if (!seat.startsWith("qg:")) {
                continue;
            }
            Nation n = TownyAPI.getInstance().getNation(seat.substring(3));
            if (n == null) {
                continue;
            }
            if (r != null && r.isKing() && r.getNationOrNull() == n) {
                return seat;
            }
            String env = data.getString("suthan." + n.getUUID() + ".ten");
            if (env != null && env.equalsIgnoreCase(p.getName()) && data.getLong("suthan." + n.getUUID() + ".den") > System.currentTimeMillis()) {
                return seat;
            }
        }
        return null;
    }

    /** Ghế bị tránh với một vụ án: ghế của quốc gia kẻ giết hoặc nạn nhân. */
    private boolean recused(String seat, String itemKey) {
        if (itemKey.startsWith("vu.")) {
            for (String who : List.of(data.getString(itemKey + ".ke-uuid", ""), data.getString(itemKey + ".nan-uuid", ""))) {
                Resident r = who.isEmpty() ? null : Members.resident(UUID.fromString(who));
                Nation n = r == null ? null : r.getNationOrNull();
                if (n == null) {
                    continue;
                }
                boolean peaceVictim = n.getName().equalsIgnoreCase(peace.nationName());
                if (seat.equals("qg:" + n.getName()) || seat.equals("hb") && peaceVictim && !rt.getBoolean("peace-seat-votes-on-peace-victims", true)) {
                    return true;
                }
            }
            return false;
        }
        return seat.equals(data.getString(itemKey + ".tranh"));
    }

    // ------------------------------------------------------------------ lịch họp

    private long meetingTime() {
        long luc = data.getLong("hop.luc");
        if (luc > 0) {
            return luc;
        }
        long emerg = data.getLong("hop.batthuong");
        if (emerg > System.currentTimeMillis()) {
            return emerg;
        }
        DayOfWeek day;
        LocalTime at;
        try {
            day = DayOfWeek.valueOf(rt.getString("day", "WEDNESDAY").toUpperCase(Locale.ROOT));
            at = LocalTime.parse(rt.getString("time", "21:00"));
        } catch (RuntimeException e) {
            day = DayOfWeek.WEDNESDAY;
            at = LocalTime.of(21, 0);
        }
        LocalDateTime now = LocalDateTime.now(VN);
        LocalDateTime next = now.with(TemporalAdjusters.nextOrSame(day)).with(at);
        if (!next.isAfter(now)) {
            next = next.plusWeeks(1);
        }
        return next.atZone(VN).toInstant().toEpochMilli();
    }

    private Location table() {
        return parseLoc(data.getString("hop.ban"));
    }

    private static Location parseLoc(String s) {
        if (s == null) {
            return null;
        }
        String[] p = s.split(" ");
        World w = p.length >= 4 ? Bukkit.getWorld(p[0]) : null;
        return w == null ? null : new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
    }

    private static String locStr(Location l) {
        return l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
    }

    private boolean noTeleportActive() {
        String st = data.getString("hop.trangthai", "CHO");
        long t = data.getLong("hop.luc");
        return st.equals("MO") || t > 0 && System.currentTimeMillis() >= t - rt.getInt("no-teleport-minutes", 30) * MIN;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        expireBounties(now);
        if (data.getLong("hop.luc") == 0) {
            data.set("hop.luc", meetingTime());
            data.set("hop.trangthai", "CHO");
            save();
        }
        long t = data.getLong("hop.luc");
        String st = data.getString("hop.trangthai", "CHO");
        if (st.equals("CHO")) {
            boolean work = !agenda().isEmpty();
            if (!work && now >= t) {
                endMeeting(); // không có việc thì không họp, hẹn tuần sau
                return;
            }
            if (work && table() == null && now >= t - rt.getInt("choose-deadline-hours", 24) * 3_600_000L) {
                pickRandomTable();
            }
            announceOnce("t60", work && now >= t - 60 * MIN, "&6⚖ Hội Bàn Tròn họp sau 60 phút");
            announceOnce("t10", work && now >= t - 10 * MIN, "&6⚖ Hội Bàn Tròn họp sau 10 phút");
            if (now >= t) {
                data.set("hop.trangthai", "MO");
                data.set("hop.mo-luc", now);
                data.set("hop.du", false);
                save();
                Bukkit.broadcast(c("&6⚖ Hội Bàn Tròn khai mạc tại " + tableDesc() + ". Các ghế: &f" + String.join(", ", seats().values())
                        + "&6. Cần " + rt.getInt("min-seats", 3) + " ghế có mặt."));
            }
            return;
        }
        // Đang họp
        Location table = table();
        long opened = data.getLong("hop.mo-luc");
        if (!data.getBoolean("hop.du")) {
            if (presentSeats(table).size() >= rt.getInt("min-seats", 3)) {
                data.set("hop.du", true);
                save();
                Bukkit.broadcast(c("&6⚖ Đủ ghế, Hội Bàn Tròn bắt đầu xử: &f" + agenda().size() + " việc. &7Ghế có mặt bỏ phiếu: /bantron"));
            } else if (now >= opened + rt.getInt("quorum-wait-minutes", 15) * MIN) {
                Bukkit.broadcast(c("&e⚖ Hội Bàn Tròn không đủ ghế: các vụ án dừng ở mức sàn, kiến nghị không thông qua."));
                for (String item : agenda()) {
                    finalizeItem(item, true);
                }
                endMeeting();
            }
            return;
        }
        boolean timeout = now >= opened + rt.getInt("max-hours", 3) * 3_600_000L;
        Set<String> present = presentSeats(table);
        for (String item : agenda()) {
            long first = data.getLong(item + ".bo-tu");
            boolean allVoted = present.stream().filter(s -> !recused(s, item)).allMatch(s -> data.isSet(item + ".phieu." + seatKey(s)));
            if (timeout || allVoted && first > 0 || first > 0 && now >= first + rt.getInt("item-minutes", 10) * MIN) {
                finalizeItem(item, false);
            }
        }
        if (agenda().isEmpty() || timeout) {
            Bukkit.broadcast(c("&6⚖ Hội Bàn Tròn đã xử xong, tan họp. Hồ sơ: /bantron lichsu"));
            endMeeting();
        }
    }

    private static String seatKey(String seat) {
        return seat.replace(':', '_').replace('.', '_');
    }

    private void announceOnce(String key, boolean cond, String msg) {
        String k = "hop.bao." + key;
        if (cond && !data.getBoolean(k)) {
            data.set(k, true);
            save();
            Bukkit.broadcast(c(msg + " tại " + tableDesc() + "&6. Đi đường tới (không dịch chuyển trong " + rt.getInt("no-teleport-radius", 150) + " block)."));
        }
    }

    private String tableDesc() {
        Location l = table();
        return l == null ? "&f(chưa chọn nơi)" : "&f" + data.getString("hop.ban-thitran", "?") + " (" + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + ")";
    }

    private void pickRandomTable() {
        ConfigurationSection bs = data.getConfigurationSection("ban");
        List<String> towns = new ArrayList<>();
        if (bs != null) {
            for (String id : bs.getKeys(false)) {
                Town t = TownyAPI.getInstance().getTown(UUID.fromString(id));
                if (t != null && peace.isMember(t) && parseLoc(bs.getString(id)) != null && !SiegeWarHook.inActiveSiegeZone(parseLoc(bs.getString(id)))) {
                    towns.add(id);
                }
            }
        }
        if (towns.isEmpty()) {
            Town cap = TownyAPI.getInstance().getTown(peace.capitalName());
            Location l = cap == null ? null : cap.getSpawnOrNull();
            if (l == null) {
                return;
            }
            data.set("hop.ban", locStr(l));
            data.set("hop.ban-thitran", cap.getName());
        } else {
            String id = towns.get(ThreadLocalRandom.current().nextInt(towns.size()));
            data.set("hop.ban", bs.getString(id));
            data.set("hop.ban-thitran", TownyAPI.getInstance().getTown(UUID.fromString(id)).getName());
        }
        save();
        Bukkit.broadcast(c("&6⚖ Nơi họp Hội Bàn Tròn (" + when(data.getLong("hop.luc")) + "): " + tableDesc()));
    }

    private Set<String> presentSeats(Location table) {
        Set<String> out = new HashSet<>();
        if (table == null) {
            return out;
        }
        double r = rt.getInt("table-radius", 16);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld() == table.getWorld() && p.getLocation().distanceSquared(table) <= r * r) {
                String s = seatOf(p);
                if (s != null) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private void endMeeting() {
        data.set("hop", null);
        data.set("hop.luc", 0);
        save();
    }

    /** Việc cần xử: vụ án đang mở + kiến nghị đang mở (khoá đầy đủ "vu.V1", "kien.N1"). */
    private List<String> agenda() {
        List<String> out = new ArrayList<>();
        for (String sec : List.of("vu", "kien")) {
            ConfigurationSection cs = data.getConfigurationSection(sec);
            if (cs != null) {
                for (String id : cs.getKeys(false)) {
                    if ("MO".equals(cs.getString(id + ".trangthai"))) {
                        out.add(sec + "." + id);
                    }
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ bỏ phiếu, phán quyết

    private void vote(Player p, String itemId, String choice) {
        String item = itemId.startsWith("V") ? "vu." + itemId : "kien." + itemId;
        if (!"MO".equals(data.getString("hop.trangthai")) || !data.getBoolean("hop.du")) {
            p.sendMessage(c("&cChỉ bỏ phiếu trong lúc Hội Bàn Tròn đang họp (đủ ghế)."));
            return;
        }
        if (!"MO".equals(data.getString(item + ".trangthai"))) {
            p.sendMessage(c("&cKhông có việc đang mở mã " + itemId));
            return;
        }
        String seat = seatOf(p);
        Location table = table();
        if (seat == null) {
            p.sendMessage(c("&cBạn không giữ ghế nào trong Hội Bàn Tròn."));
            return;
        }
        if (table == null || p.getWorld() != table.getWorld() || p.getLocation().distance(table) > rt.getInt("table-radius", 16)) {
            p.sendMessage(c("&cPhải có mặt tại bàn tròn (trong " + rt.getInt("table-radius", 16) + " block) mới bỏ phiếu được."));
            return;
        }
        if (recused(seat, item)) {
            p.sendMessage(c("&cGhế của bạn có liên quan tới việc này, không được bỏ phiếu."));
            return;
        }
        boolean isCase = item.startsWith("vu.");
        String ch = choice.toLowerCase(Locale.ROOT);
        if (isCase ? !OPTIONS.contains(ch) : !(ch.equals("co") || ch.equals("khong"))) {
            p.sendMessage(c("&cLựa chọn: " + (isCase ? String.join(", ", OPTIONS) : "co, khong")));
            return;
        }
        data.set(item + ".phieu." + seatKey(seat), ch + ":" + p.getName());
        if (data.getLong(item + ".bo-tu") == 0) {
            data.set(item + ".bo-tu", System.currentTimeMillis());
        }
        save();
        p.sendMessage(c("&aĐã bỏ phiếu &f" + ch + " &acho " + itemId + " (công khai)."));
    }

    private void finalizeItem(String item, boolean floorOnly) {
        Map<String, Integer> tally = new HashMap<>();
        ConfigurationSection ph = data.getConfigurationSection(item + ".phieu");
        if (ph != null && !floorOnly) {
            for (String k : ph.getKeys(false)) {
                tally.merge(ph.getString(k, "").split(":")[0], 1, Integer::sum);
            }
        }
        data.set(item + ".trangthai", "XONG");
        data.set(item + ".xu-luc", System.currentTimeMillis());
        if (item.startsWith("vu.")) {
            String verdict = "giu";
            int best = 0;
            for (String o : OPTIONS) {
                int v = tally.getOrDefault(o, 0);
                if (v > best) {
                    best = v;
                    verdict = o;
                }
            }
            data.set(item + ".ketqua", verdict);
            save();
            applyVerdict(item, verdict);
        } else {
            int yes = tally.getOrDefault("co", 0);
            int votes = yes + tally.getOrDefault("khong", 0);
            String loai = data.getString(item + ".loai", "");
            boolean pass = votes > 0 && (loai.equals("THUHOI") ? yes * 3 >= votes * 2 : yes * 2 > votes);
            data.set(item + ".ketqua", pass);
            save();
            applyMotion(item, pass);
        }
        rebuildOpen();
    }

    private void applyVerdict(String item, String verdict) {
        String id = item.substring(3);
        OfflinePlayer killer = Bukkit.getOfflinePlayer(UUID.fromString(data.getString(item + ".ke-uuid")));
        OfflinePlayer victim = Bukkit.getOfflinePlayer(UUID.fromString(data.getString(item + ".nan-uuid")));
        double floor = data.getDouble(item + ".san");
        long now = System.currentTimeMillis();
        String text;
        switch (verdict) {
            case "phatnang" -> {
                double paid = transfer(killer, victim, floor * (cfg.getDouble("heavy-multiplier", 3) - 1));
                text = String.format(Locale.ROOT, "phạt nặng (bồi thường thêm %.0f xu)", paid);
            }
            case "truyna" -> {
                double escrow = transfer(killer, null, floor);
                int days = cfg.getInt("bounty-days", 3);
                data.set("truyna." + killer.getUniqueId() + ".den", now + days * 86_400_000L);
                data.set("truyna." + killer.getUniqueId() + ".tien", escrow);
                data.set("truyna." + killer.getUniqueId() + ".nan", victim.getUniqueId().toString());
                data.set("truyna." + killer.getUniqueId() + ".ten", killer.getName());
                console("lp user " + killer.getName() + " meta addtempsuffix 70 \" &c[Truy nã]\" " + days + "d server=banghoi-1");
                text = String.format(Locale.ROOT, "truy nã %d ngày (ai hạ được nhận %.0f xu)", days, escrow);
            }
            case "tu" -> {
                int m = cfg.getInt("jail-minutes", 45);
                Player online = killer.getPlayer();
                if (online != null) {
                    console("jail " + online.getName() + " " + cfg.getString("jail-name", "nhatu") + " " + m + "m");
                } else {
                    data.set("tu." + killer.getUniqueId(), m);
                }
                text = "tù " + m + " phút";
            }
            case "luuday" -> {
                int d = cfg.getInt("exile-days", 14);
                data.set("cam." + killer.getUniqueId() + ".hoabinh", Math.max(data.getLong("cam." + killer.getUniqueId() + ".hoabinh"), now + d * 86_400_000L));
                text = "lưu đày (cấm vào đất hoà bình " + d + " ngày)";
            }
            case "camct1", "camct2", "camct3" -> {
                int w = verdict.charAt(5) - '0';
                data.set("cam." + killer.getUniqueId() + ".congthanh", Math.max(data.getLong("cam." + killer.getUniqueId() + ".congthanh"), now + w * 7 * 86_400_000L));
                text = "cấm công thành " + w + " tuần";
            }
            default -> text = "giữ mức sàn";
        }
        data.set(item + ".ketqua-chu", text);
        save();
        Bukkit.broadcast(c("&6⚖ Phán quyết vụ " + id + " (" + killer.getName() + " giết " + victim.getName() + "): &f" + text + votesText(item)));
    }

    private String votesText(String item) {
        ConfigurationSection ph = data.getConfigurationSection(item + ".phieu");
        if (ph == null) {
            return " &7(không ai bỏ phiếu)";
        }
        List<String> v = new ArrayList<>();
        for (String k : ph.getKeys(false)) {
            String[] x = ph.getString(k, "").split(":");
            v.add((x.length > 1 ? x[1] : k) + "=" + x[0]);
        }
        return " &7(" + String.join(", ", v) + ")";
    }

    private void applyMotion(String item, boolean pass) {
        String loai = data.getString(item + ".loai", "");
        String muc = data.getString(item + ".muc", "");
        String id = item.substring(5);
        if (pass) {
            switch (loai) {
                case "TRUDIEM" -> board.deduct(muc, data.getInt(item + ".giatri"), data.getString(item + ".ly", ""));
                case "THUHOI" -> {
                    if (orgs != null) {
                        orgs.revoke(muc);
                    }
                }
                case "MOLAI" -> reopen("vu." + muc);
                default -> {
                }
            }
        }
        Bukkit.broadcast(c("&6⚖ Kiến nghị " + id + " (" + describeMotion(item) + "): " + (pass ? "&aTHÔNG QUA" : "&cKHÔNG THÔNG QUA") + votesText(item)));
    }

    /** Kháng án được chấp nhận: huỷ phạt có thời hạn của phán quyết cũ (tiền đã trả không hoàn), xử lại. */
    private void reopen(String item) {
        if (!data.isSet(item)) {
            return;
        }
        UUID k = UUID.fromString(data.getString(item + ".ke-uuid"));
        String old = data.getString(item + ".ketqua", "");
        if (old.equals("truyna")) {
            OfflinePlayer kp = Bukkit.getOfflinePlayer(k);
            VaultEconomy.deposit(kp, data.getDouble("truyna." + k + ".tien"));
            data.set("truyna." + k, null);
            console("lp user " + kp.getName() + " meta removetempsuffix 70");
        } else if (old.equals("luuday")) {
            data.set("cam." + k + ".hoabinh", null);
        } else if (old.startsWith("camct")) {
            data.set("cam." + k + ".congthanh", null);
        }
        data.set(item + ".trangthai", "MO");
        data.set(item + ".mo-lai", true);
        data.set(item + ".phieu", null);
        data.set(item + ".bo-tu", null);
        data.set(item + ".ketqua", null);
        save();
        rebuildOpen();
    }

    private String describeMotion(String item) {
        String loai = data.getString(item + ".loai", "");
        String muc = data.getString(item + ".muc", "");
        return switch (loai) {
            case "TRUDIEM" -> "trừ " + data.getInt(item + ".giatri") + " điểm Bá Chủ của " + muc + ": " + data.getString(item + ".ly", "");
            case "THUHOI" -> "thu hồi giấy phép của " + muc + ": " + data.getString(item + ".ly", "");
            case "MOLAI" -> "mở lại vụ " + muc + ": " + data.getString(item + ".ly", "");
            default -> loai + " " + muc;
        };
    }

    private void expireBounties(long now) {
        ConfigurationSection ts = data.getConfigurationSection("truyna");
        if (ts == null) {
            return;
        }
        for (String k : ts.getKeys(false)) {
            if (ts.getLong(k + ".den") <= now) {
                String victim = ts.getString(k + ".nan");
                if (victim != null && ts.getDouble(k + ".tien") > 0) {
                    VaultEconomy.deposit(Bukkit.getOfflinePlayer(UUID.fromString(victim)), ts.getDouble(k + ".tien"));
                }
                ts.set(k, null);
                save();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void hookSiegeStart() {
        Plugin sw = Bukkit.getPluginManager().getPlugin("SiegeWar");
        if (sw == null || !sw.isEnabled()) {
            return;
        }
        try {
            Class<? extends Event> cls = (Class<? extends Event>) Class.forName("com.gmail.goosius.siegewar.events.PreSiegeWarStartEvent", true, sw.getClass().getClassLoader());
            Method starter = cls.getMethod("getSiegeStarter");
            Method msg = cls.getMethod("setCancellationMsg", String.class);
            Bukkit.getPluginManager().registerEvent(cls, this, EventPriority.HIGH, (l, ev) -> {
                if (!cls.isInstance(ev)) {
                    return;
                }
                try {
                    Player p = (Player) starter.invoke(ev);
                    if (p != null && bannedSiege(p.getUniqueId())) {
                        ((org.bukkit.event.Cancellable) ev).setCancelled(true);
                        msg.invoke(ev, "Bạn đang bị cấm công thành (vụ án chưa xử hoặc phán quyết của Hội Bàn Tròn).");
                    }
                } catch (ReflectiveOperationException ex) {
                    plugin.getLogger().warning("Kiểm tra cấm công thành lỗi: " + ex);
                }
            }, plugin, true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Không gắn được cấm công thành (SiegeWar): " + e);
        }
    }

    // ------------------------------------------------------------------ /bantron

    boolean command(CommandSender s, String[] args) {
        String sub = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "lichsu" -> history(s);
            case "vu" -> {
                if (args.length >= 2) {
                    showItem(s, args[1].toUpperCase(Locale.ROOT));
                }
            }
            case "toi" -> me(s);
            default -> {
                if (!(s instanceof Player p)) {
                    overview(s);
                    return true;
                }
                handlePlayer(p, sub, args);
            }
        }
        return true;
    }

    private void handlePlayer(Player p, String sub, String[] args) {
        switch (sub) {
            case "xu" -> {
                if (args.length < 3) {
                    p.sendMessage(c("&c/bantron xu <mã> <" + String.join("|", OPTIONS) + "> (vụ án) hoặc co|khong (kiến nghị)"));
                } else {
                    vote(p, args[1].toUpperCase(Locale.ROOT), args[2]);
                }
            }
            case "datban" -> {
                Resident r = TownyAPI.getInstance().getResident(p);
                Town t = r == null ? null : r.getTownOrNull();
                if (t == null || !r.isMayor() || !peace.isMember(t) || TownyAPI.getInstance().getTown(p.getLocation()) != t) {
                    p.sendMessage(c("&cThị trưởng thị trấn hoà bình đứng trong thị trấn mình rồi gõ /bantron datban."));
                    return;
                }
                data.set("ban." + t.getUUID(), locStr(p.getLocation()));
                save();
                p.sendMessage(c("&aĐã đặt bàn tròn của " + t.getName() + " tại đây."));
            }
            case "chon" -> {
                if (!council.isRepresentative(p) || args.length < 2) {
                    p.sendMessage(c("&cChỉ đại diện hoà bình chọn nơi họp: /bantron chon <thị trấn có bàn tròn>"));
                    return;
                }
                Town t = TownyAPI.getInstance().getTown(args[1]);
                String l = t == null ? null : data.getString("ban." + t.getUUID());
                if (l == null || !peace.isMember(t)) {
                    p.sendMessage(c("&cThị trấn này chưa đặt bàn tròn (/bantron datban)."));
                    return;
                }
                if (System.currentTimeMillis() > meetingTime() - rt.getInt("choose-deadline-hours", 24) * 3_600_000L) {
                    p.sendMessage(c("&cĐã quá hạn chọn (" + rt.getInt("choose-deadline-hours", 24) + " giờ trước giờ họp)."));
                    return;
                }
                data.set("hop.ban", l);
                data.set("hop.ban-thitran", t.getName());
                save();
                Bukkit.broadcast(c("&6⚖ Đại diện hoà bình chọn nơi họp Hội Bàn Tròn (" + when(meetingTime()) + "): " + tableDesc()));
            }
            case "hopbatthuong" -> {
                if (!council.isRepresentative(p) || args.length < 3) {
                    p.sendMessage(c("&cChỉ đại diện hoà bình: /bantron hopbatthuong <yyyy-MM-dd> <HH:mm>"));
                    return;
                }
                try {
                    long at = LocalDateTime.parse(args[1] + "T" + args[2]).atZone(VN).toInstant().toEpochMilli();
                    if (at < System.currentTimeMillis() + rt.getInt("emergency-notice-hours", 24) * 3_600_000L) {
                        p.sendMessage(c("&cHọp bất thường phải báo trước ít nhất " + rt.getInt("emergency-notice-hours", 24) + " giờ."));
                        return;
                    }
                    if ("MO".equals(data.getString("hop.trangthai"))) {
                        p.sendMessage(c("&cĐang họp, chờ tan họp."));
                        return;
                    }
                    data.set("hop", null);
                    data.set("hop.batthuong", at);
                    data.set("hop.luc", at);
                    data.set("hop.trangthai", "CHO");
                    save();
                    Bukkit.broadcast(c("&6⚖ Họp Hội Bàn Tròn bất thường lúc " + when(at) + ". Đại diện hoà bình sẽ chọn nơi họp."));
                } catch (RuntimeException e) {
                    p.sendMessage(c("&cSai định dạng ngày giờ, ví dụ: /bantron hopbatthuong 2026-10-20 21:00"));
                }
            }
            case "suthan" -> {
                Resident r = TownyAPI.getInstance().getResident(p);
                Resident env = args.length >= 2 ? Members.resident(args[1]) : null;
                if (r == null || !r.isKing() || env == null || env.getNationOrNull() != r.getNationOrNull()) {
                    p.sendMessage(c("&cVua cử một người cùng quốc gia: /bantron suthan <tên>"));
                    return;
                }
                Nation n = r.getNationOrNull();
                data.set("suthan." + n.getUUID() + ".ten", env.getName());
                data.set("suthan." + n.getUUID() + ".den", meetingTime() + rt.getInt("max-hours", 3) * 3_600_000L);
                save();
                p.sendMessage(c("&a" + env.getName() + " là sứ thần của " + n.getName() + " ở phiên họp tới."));
            }
            case "khangan" -> {
                String item = args.length >= 3 ? "vu." + args[1].toUpperCase(Locale.ROOT) : null;
                if (item == null || !p.getUniqueId().toString().equals(data.getString(item + ".ke-uuid"))
                        || !"XONG".equals(data.getString(item + ".trangthai")) || data.getBoolean(item + ".da-khang")) {
                    p.sendMessage(c("&cChỉ người bị xử kháng án được, 1 lần mỗi vụ, phải có bằng chứng mới: /bantron khangan <mã vụ> <bằng chứng>"));
                    return;
                }
                data.set(item + ".da-khang", true);
                newMotion("MOLAI", args[1].toUpperCase(Locale.ROOT), 0, String.join(" ", Arrays.copyOfRange(args, 2, args.length)), p.getName(), null);
            }
            case "kiennghi" -> fileMotion(p, args);
            default -> {
                overview(p);
                bedrockVote(p);
            }
        }
    }

    private void fileMotion(Player p, String[] args) {
        Resident r = TownyAPI.getInstance().getResident(p);
        boolean allowed = council.isRepresentative(p) || r != null && (r.isKing() || r.isMayor());
        if (!allowed || args.length < 4) {
            p.sendMessage(c("&cVua, thị trưởng hoặc đại diện hoà bình: /bantron kiennghi trudiem <quốc gia> <điểm> <lý do> | thuhoi <tổ chức> <lý do>"));
            return;
        }
        String type = args[1].toLowerCase(Locale.ROOT);
        if (type.equals("trudiem") && args.length >= 5) {
            int pts;
            try {
                pts = Math.abs(Integer.parseInt(args[3]));
            } catch (NumberFormatException e) {
                p.sendMessage(c("&cSố điểm không đúng."));
                return;
            }
            newMotion("TRUDIEM", args[2], pts, String.join(" ", Arrays.copyOfRange(args, 4, args.length)), p.getName(), "qg:" + args[2]);
        } else if (type.equals("thuhoi")) {
            newMotion("THUHOI", args[2], 0, String.join(" ", Arrays.copyOfRange(args, 3, args.length)), p.getName(), null);
        } else {
            p.sendMessage(c("&c/bantron kiennghi trudiem <quốc gia> <điểm> <lý do> | thuhoi <tổ chức> <lý do>"));
        }
    }

    private void newMotion(String loai, String muc, int value, String reason, String by, String recusedSeat) {
        int n = data.getInt("so-kien") + 1;
        data.set("so-kien", n);
        String k = "kien.N" + n;
        data.set(k + ".loai", loai);
        data.set(k + ".muc", muc);
        data.set(k + ".giatri", value);
        data.set(k + ".ly", reason);
        data.set(k + ".nguoi", by);
        data.set(k + ".luc", System.currentTimeMillis());
        data.set(k + ".trangthai", "MO");
        if (recusedSeat != null) {
            data.set(k + ".tranh", recusedSeat);
        }
        save();
        Bukkit.broadcast(c("&6⚖ Kiến nghị N" + n + " gửi Hội Bàn Tròn: " + describeMotion(k) + " &7(xử ở phiên họp " + when(meetingTime()) + ")"));
    }

    private void bedrockVote(Player p) {
        if (!BedrockMenu.available() || !BedrockMenu.isBedrock(p.getUniqueId()) || seatOf(p) == null || !data.getBoolean("hop.du")) {
            return;
        }
        String seat = seatOf(p);
        for (String item : agenda()) {
            if (recused(seat, item) || data.isSet(item + ".phieu." + seatKey(seat))) {
                continue;
            }
            String id = item.substring(item.indexOf('.') + 1);
            List<String> opts = item.startsWith("vu.") ? OPTIONS : List.of("co", "khong");
            List<BedrockMenu.Entry> es = new ArrayList<>();
            for (String o : opts) {
                es.add(new BedrockMenu.Entry(o, o));
            }
            String title = item.startsWith("vu.") ? "Vụ " + id + ": " + data.getString(item + ".ke") + " giết " + data.getString(item + ".nan")
                    : "Kiến nghị " + id + ": " + describeMotion(item);
            BedrockMenu.open(p.getUniqueId(), "Hội Bàn Tròn", title, es,
                    o -> plugin.getServer().getScheduler().runTask(plugin, () -> vote(p, id, o)));
            return;
        }
    }

    private void overview(CommandSender s) {
        long t = meetingTime();
        s.sendMessage(c("&6&l⚖ HỘI BÀN TRÒN &7- họp " + when(t) + (agenda().isEmpty() ? " (chưa có việc: không họp)" : "") + " tại " + tableDesc()
                + ("MO".equals(data.getString("hop.trangthai")) ? " &a(đang họp)" : "")));
        List<String> seatText = new ArrayList<>();
        for (Map.Entry<String, String> e : seats().entrySet()) {
            Nation n = e.getKey().startsWith("qg:") ? TownyAPI.getInstance().getNation(e.getValue()) : null;
            String holder = e.getKey().equals("hb") ? (council.representative() == null ? "chưa có đại diện" : council.representative())
                    : n == null || n.getKing() == null ? "?" : n.getKing().getName();
            seatText.add(e.getValue() + " (" + holder + ")");
        }
        s.sendMessage(c("&7Ghế: &f" + String.join("&7, &f", seatText)));
        for (String item : agenda()) {
            String id = item.substring(item.indexOf('.') + 1);
            s.sendMessage(c(item.startsWith("vu.") ? "&c" + id + "&7: " + data.getString(item + ".ke") + " giết " + data.getString(item + ".nan")
                    + " lúc " + when(data.getLong(item + ".luc")) : "&e" + id + "&7: " + describeMotion(item)));
        }
        s.sendMessage(c("&7Lệnh: &f/bantron xu <mã> <mức> · lichsu · vu <mã> · toi · datban · chon <thị trấn> · suthan <tên> · khangan <mã> <bằng chứng> · kiennghi ..."));
        s.sendMessage(c("&7Mức: giu, phatnang, truyna, tu, luuday, camct1, camct2, camct3"));
    }

    private void showItem(CommandSender s, String id) {
        String item = id.startsWith("V") ? "vu." + id : "kien." + id;
        if (!data.isSet(item)) {
            s.sendMessage(c("&cKhông có " + id));
            return;
        }
        if (item.startsWith("vu.")) {
            s.sendMessage(c("&6Vụ " + id + ": &f" + data.getString(item + ".ke") + " giết " + data.getString(item + ".nan") + " &7lúc "
                    + when(data.getLong(item + ".luc")) + " tại " + data.getString(item + ".noi") + " (" + data.getString(item + ".vukhi") + ")"));
            s.sendMessage(c(String.format(Locale.ROOT, "&7Mức sàn %.0f xu (đã bồi thường %.0f), trạng thái %s, phán quyết: %s%s",
                    data.getDouble(item + ".san"), data.getDouble(item + ".da-boi-thuong"), data.getString(item + ".trangthai"),
                    data.getString(item + ".ketqua-chu", "chưa xử"), votesText(item))));
        } else {
            s.sendMessage(c("&6Kiến nghị " + id + ": &f" + describeMotion(item) + " &7(" + data.getString(item + ".nguoi") + ", " + data.getString(item + ".trangthai")
                    + ", kết quả " + data.getString(item + ".ketqua", "chưa xử") + ")" + votesText(item)));
        }
    }

    private void history(CommandSender s) {
        s.sendMessage(c("&6&lHồ sơ Hội Bàn Tròn (mới nhất)"));
        List<String> items = new ArrayList<>();
        for (String sec : List.of("vu", "kien")) {
            ConfigurationSection cs = data.getConfigurationSection(sec);
            if (cs != null) {
                for (String id : cs.getKeys(false)) {
                    items.add(sec + "." + id);
                }
            }
        }
        items.sort((a, b) -> Long.compare(data.getLong(b + ".luc"), data.getLong(a + ".luc")));
        for (String item : items.subList(0, Math.min(10, items.size()))) {
            showItem(s, item.substring(item.indexOf('.') + 1));
        }
    }

    private void me(CommandSender s) {
        if (!(s instanceof Player p)) {
            return;
        }
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        s.sendMessage(c("&6Tình trạng của bạn:"));
        s.sendMessage(c(openKillers.contains(id) ? "&c- Có vụ án chưa xử: cấm vào đất hoà bình và cấm công thành tới phiên họp " + when(meetingTime()) : "&a- Không có vụ án đang chờ xử"));
        if (data.getLong("cam." + id + ".hoabinh") > now) {
            s.sendMessage(c("&c- Lưu đày tới " + when(data.getLong("cam." + id + ".hoabinh"))));
        }
        if (data.getLong("cam." + id + ".congthanh") > now) {
            s.sendMessage(c("&c- Cấm công thành tới " + when(data.getLong("cam." + id + ".congthanh"))));
        }
        if (data.getLong("truyna." + id + ".den") > now) {
            s.sendMessage(c("&c- Đang bị truy nã tới " + when(data.getLong("truyna." + id + ".den"))));
        }
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được an.yml: " + e.getMessage());
        }
    }
}
