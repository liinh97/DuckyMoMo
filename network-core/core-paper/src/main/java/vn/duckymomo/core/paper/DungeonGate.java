package vn.duckymomo.core.paper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
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
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.persistence.PersistentDataType;

/**
 * Cổng Dungeon (/dungeon, Bang Hội Chiến). Đặt quyền riêng "duckymomo.dungeon.<mã>" cho từng gói EliteMobs (trường permission của
 * content package, đặt bằng reflection mỗi phút nên /em reload hay cập nhật gói cũng không mất) rồi chỉ cấp quyền cho người qua được cổng.
 * Mọi lối vào của EliteMobs (menu /em, NPC dịch chuyển, nhóm) đều hỏi quyền này. Config dungeon-gate.dungeons.<mã gói (tên file bỏ .yml)>:
 *  - chain:  chuỗi cấp. requires: <mã> = trùm cuối dungeon đó rơi CHÌA KHOÁ thật (TRIAL_KEY, PDC dmm_chiatang) cho từng người; dùng chìa
 *            (/dungeon vao hoặc bấm chuột phải) thì mất chìa, mở VĨNH VIỄN tầng đó cho người dùng (mo.<uuid>). key-tradeable: false = chìa
 *            ghi tên người nhận, người khác không dùng được. Không requires = mở sẵn.
 *  - hidden: dungeon ẩn. Lối vào giấu trên bản đồ: chưa đặt thì server TỰ chọn chỗ đất hoang ngẫu nhiên (luật chọn chỗ của Cổng Hoang);
 *            admin đổi: /dungeon datan <mã> (chỗ đang đứng) hoặc /dungeon datan <mã> ngaunhien. Tới gần (hidden-radius khối) là tìm ra, giữ cả mùa.
 *            night-only: true = chỉ lộ ra ban đêm. hint: gợi ý hiện trong /dungeon.
 *  - luck:   may mắn. Vé Vận Mệnh rơi hiếm khi hạ quái / câu cá (luck.*); dùng vé có roll-chance % cổng mở (trượt thì mất vé).
 *  - ticket: vé cá nhân mua bằng xu (price/lượt, tối đa max-tickets vé trong túi).
 *  - server: cả server góp quỹ (goal xu); đủ thì mở cho mọi người open-days ngày (0 = tới hết mùa), hết hạn thì góp lại.
 *  - gate:   chỉ vào được qua Cổng Hoang (WildGates.passThrough); /dungeon vao báo đi tìm cổng.
 *  Chung: name, level (nhãn cấp), requires-frontier (mốc khai hoang phải mở), first-rewards (quà lần đầu vượt, mỗi người một lần).
 *  Dungeon ẩn có fragments: N = tìm ra lối vào rồi còn phải ghép N Mảnh Chìa (rơi khi hạ quái / câu cá, chỉ cho người đã tìm ra).
 *  Điều kiện phụ (tuỳ dungeon, kiểm cho TỪNG người trong nhóm /em party; có điều kiện thì mỗi lần vào đều phải qua /dungeon vao):
 *   min-pet-level (pet đang dẫn), party: "2-4" (số người trong nhóm EliteMobs), days: [t2..t7, cn], hours: "19-23" (giờ VN),
 *   seasons: [mua, kho], hegemon-only (nước Bá chủ), min-achievements (số mốc Sổ thành tích).
 * Dungeon của tuần (weekly): mỗi tuần một dungeon, vượt được thêm weekly.rewards (tối đa weekly.max-per-week lần/người/tuần).
 * Kỷ lục tốc độ: thời gian từ lúc vào tới lúc vượt, top 5 mỗi dungeon (dungeon.json). Kho báu bậc có reveal-hidden: chỉ đường tới lối vào ẩn.
 * Vé (ticket/luck) chỉ trừ khi thật sự vào: /dungeon vao mở cửa window-seconds giây, không vào kịp thì hoàn vé.
 * Lưu: dungeon.yml; ghi dungeon.json cho web.
 */
final class DungeonGate implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    static final String PERM = "duckymomo.dungeon.";
    static final Map<String, String> TYPE_VI = Map.of("chain", "Chuỗi chìa khoá", "hidden", "Dungeon ẩn", "luck", "May mắn",
            "ticket", "Vé cá nhân", "server", "Mở chung cả server", "gate", "Chỉ qua Cổng Hoang");

    private record Dg(String id, String name, String type, String level, String requires, String frontier, String hint, boolean nightOnly,
                      double rollChance, double price, int maxTickets, double goal, int openDays, List<String> firstRewards,
                      int fragments, int minPet, int partyMin, int partyMax, List<String> days, int hourFrom, int hourTo, List<String> seasons,
                      boolean hegemonOnly, int minAch) {
        boolean hasCond() {
            return minPet > 0 || partyMin > 1 || partyMax > 0 || !days.isEmpty() || hourFrom >= 0 || !seasons.isEmpty() || hegemonOnly || minAch > 0;
        }
    }

    /** gate = mở qua Cổng Hoang (vào tới nơi thì báo Lữ khách; 22/10: trước đây tính ngay lúc mở menu, chưa cần vào). */
    private record Pass(String id, long until, boolean ticket, boolean gate) { }

    private static final List<String> DAY_CODES = List.of("t2", "t3", "t4", "t5", "t6", "t7", "cn");

    private final DuckyMoMoCore plugin;
    private final Rewards rewards;
    private final Map<String, Dg> dungeons = new LinkedHashMap<>();
    private final long windowMs;
    private final int hiddenRadius;
    private final double luckMob;
    private final double luckFish;
    private final int luckPerDay;
    private final NamespacedKey ticketKey;
    private final NamespacedKey fragKey;
    private final NamespacedKey keyKey;
    private final NamespacedKey keyOwner;
    private final boolean keyTradeable;
    /** 23/10: vào là đánh luôn: tự bấm [SẴN SÀNG] hộ cả nhóm, tự /em start khi vào instance. */
    private final boolean autoReady;
    private final boolean autoStart;
    private Map<?, ?> readyChecks;          // PartyDungeonReadyCheckManager.checksByParty
    private Method readyCall;               // PartyDungeonReadyCheckManager.ready(Player, String)
    private Method playerInstance;          // MatchInstance.getPlayerInstance(Player)
    private final Set<Object> started = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private final double fragMob;
    private final double fragFish;
    private final int fragPerDay;
    private final boolean weeklyOn;
    private final List<String> weeklyRewards;
    private final int weeklyMax;
    private final List<String> weeklyTypes;
    private final Map<UUID, Long> enteredAt = new HashMap<>();
    private Method partyIds;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, PermissionAttachment> attach = new HashMap<>();
    private final Map<UUID, Pass> pending = new HashMap<>();
    private final Map<UUID, String> inside = new HashMap<>();
    private final Map<Object, Object> originalPerm = new HashMap<>();   // ContentPackagesConfigFields → quyền gốc (trả lại khi tắt)
    private Method getEmPackages;
    private Method getFields;
    private Method isInstalled;
    private boolean hooked;
    private int tickCount;

    DungeonGate(DuckyMoMoCore plugin, Rewards rewards, ConfigurationSection c) {
        this.plugin = plugin;
        this.rewards = rewards;
        this.windowMs = Math.max(30, c.getLong("window-seconds", 120)) * 1000L;
        this.hiddenRadius = Math.max(2, c.getInt("hidden-radius", 4));
        this.luckMob = c.getDouble("luck.mob-chance", 0.05);
        this.luckFish = c.getDouble("luck.fish-chance", 0.3);
        this.luckPerDay = c.getInt("luck.max-per-day", 1);
        this.fragMob = c.getDouble("fragment.mob-chance", 1);
        this.fragFish = c.getDouble("fragment.fish-chance", 3);
        this.fragPerDay = c.getInt("fragment.max-per-day", 3);
        this.weeklyOn = c.getBoolean("weekly.enabled", true);
        this.weeklyRewards = c.getStringList("weekly.rewards");
        this.weeklyMax = Math.max(1, c.getInt("weekly.max-per-week", 3));
        this.weeklyTypes = c.isList("weekly.types") ? c.getStringList("weekly.types") : List.of("chain", "ticket", "server");
        ConfigurationSection ds = c.getConfigurationSection("dungeons");
        if (ds != null) {
            for (String id : ds.getKeys(false)) {
                ConfigurationSection d = ds.getConfigurationSection(id);
                if (d == null) {
                    continue;
                }
                String type = d.getString("type", "chain").toLowerCase(Locale.ROOT);
                if (!TYPE_VI.containsKey(type)) {
                    plugin.getLogger().warning("Cổng Dungeon: kiểu lạ '" + type + "' ở " + id + " (chain|hidden|luck|ticket|server|gate)");
                    continue;
                }
                Material ic = Material.matchMaterial(d.getString("icon", ""));
                if (ic != null) {
                    dgIcon.put(id, ic);
                }
                dungeons.put(id, new Dg(id, d.getString("name", id), type, d.getString("level", ""), d.getString("requires", ""),
                        d.getString("requires-frontier", ""), d.getString("hint", ""), d.getBoolean("night-only", false),
                        Math.max(1, Math.min(100, d.getDouble("roll-chance", 100))), Math.max(0, d.getDouble("price", 0)),
                        Math.max(1, d.getInt("max-tickets", 5)), Math.max(1, d.getDouble("goal", 100000)), Math.max(0, d.getInt("open-days", 7)),
                        d.getStringList("first-rewards"), Math.max(0, d.getInt("fragments", 0)), Math.max(0, d.getInt("min-pet-level", 0)),
                        range(d.getString("party", ""), 0, 1), range(d.getString("party", ""), 1, 0),
                        d.getStringList("days").stream().map(x -> x.toLowerCase(Locale.ROOT)).toList(),
                        range(d.getString("hours", ""), 0, -1), range(d.getString("hours", ""), 1, -1),
                        d.getStringList("seasons"), d.getBoolean("hegemon-only", false), Math.max(0, d.getInt("min-achievements", 0))));
            }
        }
        this.ticketKey = new NamespacedKey(plugin, "dmm_vevanmenh");
        this.fragKey = new NamespacedKey(plugin, "dmm_manhchia");
        this.keyKey = new NamespacedKey(plugin, "dmm_chiatang");
        this.keyOwner = new NamespacedKey(plugin, "dmm_chiatang_chu");
        this.keyTradeable = c.getBoolean("key-tradeable", false);
        this.autoReady = c.getBoolean("auto-ready", true);
        this.autoStart = c.getBoolean("auto-start", true);
        this.file = new File(plugin.getDataFolder(), "dungeon.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    /** "2-4" → phần tử i (một số "3" = cả hai đầu); trống/sai → def. */
    private static int range(String v, int i, int def) {
        if (v == null || v.isBlank()) {
            return def;
        }
        String[] a = v.trim().split("\\s*-\\s*");
        try {
            return Integer.parseInt(a[Math.min(i, a.length - 1)]);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    boolean ready() {
        return !dungeons.isEmpty();
    }

    String describe() {
        Map<String, Integer> n = new LinkedHashMap<>();
        dungeons.values().forEach(d -> n.merge(TYPE_VI.get(d.type()), 1, Integer::sum));
        return dungeons.size() + " dungeon " + n;
    }

    void start() {
        // EliteMobs nạp sau DuckyMoMoCore → đợi 5 giây mới gắn
        Shared.dungeonGate = this;
        Bukkit.getScheduler().runTaskLater(plugin, this::hook, 100L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 120L, 100L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::autoReadyTick, 200L, 10L);
    }

    void stop() {
        Shared.dungeonGate = null;
        HandlerList.unregisterAll(this);
        // trả quyền gốc cho các gói (không để gói bị khoá khi tắt module)
        for (Map.Entry<Object, Object> e : originalPerm.entrySet()) {
            setPermField(e.getKey(), (String) e.getValue());
        }
        originalPerm.clear();
        attach.values().forEach(a -> {
            try {
                a.remove();
            } catch (IllegalArgumentException ignored) {
                // người chơi đã thoát
            }
        });
        attach.clear();
        // vé đang chờ: hoàn lại
        for (Map.Entry<UUID, Pass> e : pending.entrySet()) {
            if (e.getValue().ticket()) {
                refund(e.getKey(), e.getValue().id());
            }
        }
        pending.clear();
        save();
    }

    // ---------------------------------------------------------------- gắn EliteMobs

    @SuppressWarnings("unchecked")
    private void hook() {
        var em = Bukkit.getPluginManager().getPlugin("EliteMobs");
        if (em == null || !em.isEnabled()) {
            plugin.getLogger().warning("Cổng Dungeon: chưa có EliteMobs, /dungeon chỉ hiện danh sách.");
            return;
        }
        try {
            ClassLoader cl = em.getClass().getClassLoader();
            Class<?> pkg = Class.forName("com.magmaguy.elitemobs.dungeons.EMPackage", true, cl);
            getEmPackages = pkg.getMethod("getEmPackages");
            getFields = pkg.getMethod("getContentPackagesConfigFields");
            isInstalled = pkg.getMethod("isInstalled");
            try {
                partyIds = Class.forName("com.magmaguy.elitemobs.parties.PartyManager", true, cl).getMethod("getDungeonEntryMemberIds", Player.class);
            } catch (ReflectiveOperationException e) {
                plugin.getLogger().warning("Cổng Dungeon: không đọc được nhóm EliteMobs, điều kiện nhóm coi như chỉ có 1 người.");
            }
            try {
                Class<?> rc = Class.forName("com.magmaguy.elitemobs.parties.PartyDungeonReadyCheckManager", true, cl);
                java.lang.reflect.Field f = rc.getDeclaredField("checksByParty");
                f.setAccessible(true);
                readyChecks = (Map<?, ?>) f.get(null);
                readyCall = rc.getMethod("ready", Player.class, String.class);
            } catch (ReflectiveOperationException | RuntimeException e) {
                plugin.getLogger().warning("Cổng Dungeon: không tự xác nhận sẵn sàng cho nhóm được (" + e + "), người chơi tự bấm [SẴN SÀNG].");
            }
            try {
                playerInstance = Class.forName("com.magmaguy.elitemobs.instanced.MatchInstance", true, cl).getMethod("getPlayerInstance", Player.class);
            } catch (ReflectiveOperationException e) {
                plugin.getLogger().warning("Cổng Dungeon: không tự bắt đầu trận được (" + e + "), người chơi gõ /em start.");
            }
            Class<? extends Event> join = (Class<? extends Event>) Class.forName("com.magmaguy.elitemobs.api.PlayerJoinDungeonEvent", true, cl);
            Class<? extends Event> leave = (Class<? extends Event>) Class.forName("com.magmaguy.elitemobs.api.PlayerLeaveDungeonEvent", true, cl);
            Class<? extends Event> done = (Class<? extends Event>) Class.forName("com.magmaguy.elitemobs.api.DungeonCompleteEvent", true, cl);
            Method joinPlayer = join.getMethod("getPlayer"), joinInst = join.getMethod("getDungeonInstance");
            Method leavePlayer = leave.getMethod("getPlayer"), leaveInst = leave.getMethod("getDungeonInstance");
            Method doneInst = done.getMethod("getDungeonInstance");
            Bukkit.getPluginManager().registerEvent(join, this, EventPriority.MONITOR, (l, ev) -> {
                if (join.isInstance(ev)) {
                    onEnter(players(ev, joinPlayer, joinInst), instanceId(ev, joinInst));
                }
            }, plugin, true);
            Bukkit.getPluginManager().registerEvent(leave, this, EventPriority.MONITOR, (l, ev) -> {
                if (leave.isInstance(ev)) {
                    for (Player p : players(ev, leavePlayer, leaveInst)) {
                        inside.remove(p.getUniqueId());
                        enteredAt.remove(p.getUniqueId());
                        refresh(p);
                    }
                }
            }, plugin, true);
            Bukkit.getPluginManager().registerEvent(done, this, EventPriority.MONITOR, (l, ev) -> {
                if (done.isInstance(ev)) {
                    onComplete(players(ev, null, doneInst), instanceId(ev, doneInst));
                }
            }, plugin, true);
            hooked = true;
            Bukkit.getScheduler().runTaskLater(plugin, this::autoHide, 200L);   // sau khi Cổng Hoang bật
            applyPermissions();
            Bukkit.getOnlinePlayers().forEach(this::refresh);
            writeJson();
            plugin.getLogger().info("Cổng Dungeon: đã gắn EliteMobs (" + installedCount() + "/" + dungeons.size() + " gói đã cài).");
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Cổng Dungeon: không gắn được EliteMobs: " + e);
        }
    }

    /** Mã dungeon người này đang ở trong (null nếu không ở dungeon nào); Thợ Rèn dùng để rơi mảnh theo dungeon. */
    String dungeonOf(UUID u) {
        return inside.get(u);
    }

    private Object pkg(String id) {
        if (getEmPackages == null) {
            return null;
        }
        try {
            Map<?, ?> m = (Map<?, ?>) getEmPackages.invoke(null);
            Object o = m.get(id + ".yml");
            return o != null ? o : m.get(id);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private boolean installed(String id) {
        Object p = pkg(id);
        try {
            return p != null && (boolean) isInstalled.invoke(p);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private long installedCount() {
        return dungeons.keySet().stream().filter(this::installed).count();
    }

    private static Field permField(Object fields) {
        for (Class<?> k = fields.getClass(); k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField("permission");
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                // lên lớp cha
            }
        }
        return null;
    }

    private void setPermField(Object fields, String v) {
        try {
            Field f = permField(fields);
            if (f != null) {
                f.set(fields, v);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Cổng Dungeon: không đặt được quyền gói: " + e);
        }
    }

    /** Đặt quyền riêng cho từng gói có trong cấu hình (gọi lại mỗi phút: /em reload tạo lại đối tượng cấu hình). */
    private void applyPermissions() {
        for (Dg d : dungeons.values()) {
            Object p = pkg(d.id());
            if (p == null) {
                continue;
            }
            try {
                Object fields = getFields.invoke(p);
                Field f = permField(fields);
                if (f == null) {
                    continue;
                }
                Object cur = f.get(fields);
                if (!(PERM + d.id()).equals(cur)) {
                    originalPerm.putIfAbsent(fields, (String) cur);
                    f.set(fields, PERM + d.id());
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                plugin.getLogger().warning("Cổng Dungeon: lỗi đặt quyền " + d.id() + ": " + e);
            }
        }
    }

    private static Collection<Player> players(Event ev, Method player, Method inst) {
        try {
            if (player != null && player.invoke(ev) instanceof Player p) {
                return List.of(p);
            }
            Object i = inst.invoke(ev);
            Object ps = i.getClass().getMethod("getPlayers").invoke(i);
            return ps instanceof Collection<?> col ? col.stream().filter(Player.class::isInstance).map(Player.class::cast).toList() : List.of();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return List.of();
        }
    }

    private static String instanceId(Event ev, Method inst) {
        try {
            Object i = inst.invoke(ev);
            Object f = i.getClass().getMethod("getContentPackagesConfigFields").invoke(i);
            String fn = String.valueOf(f.getClass().getMethod("getFilename").invoke(f));
            return fn.endsWith(".yml") ? fn.substring(0, fn.length() - 4) : fn;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "";
        }
    }

    // ---------------------------------------------------------------- quyền vào

    private String uk(UUID u) {
        return u.toString();
    }

    private boolean cleared(UUID u, String id) {
        return data.getStringList("qua." + uk(u)).contains(id);
    }

    private boolean found(UUID u, String id) {
        return data.getStringList("tim." + uk(u)).contains(id);
    }

    private int tickets(UUID u, String id) {
        return data.getInt("ve." + uk(u) + "." + id, 0);
    }

    private void addTickets(UUID u, String id, int n) {
        int v = Math.max(0, tickets(u, id) + n);
        data.set("ve." + uk(u) + "." + id, v == 0 ? null : v);
    }

    private boolean serverOpen(String id) {
        long until = data.getLong("quy." + id + ".mo", 0);
        return until == -1 || until > System.currentTimeMillis();
    }

    /** Điều kiện chung (mốc khai hoang). */
    private boolean baseOk(Dg d) {
        return Shared.unlocked(d.frontier());
    }

    /** Có quyền vào ngay (không cần vé)? */
    private boolean freeAccess(UUID u, Dg d) {
        if (!baseOk(d)) {
            return false;
        }
        return switch (d.type()) {
            case "chain" -> d.requires().isBlank() || unlocked(u, d.id());
            case "hidden" -> found(u, d.id()) && (d.fragments() == 0 || assembled(u, d.id()));
            case "server" -> serverOpen(d.id());
            default -> false;
        };
    }

    private boolean assembled(UUID u, String id) {
        return data.getStringList("ghep." + uk(u)).contains(id);
    }

    private void refresh(Player p) {
        if (!p.isOnline()) {
            return;   // EliteMobs có thể báo rời dungeon SAU khi người chơi đã thoát (dọn instance): đừng tạo quyền cho đối tượng cũ
        }
        UUID u = p.getUniqueId();
        Set<String> allow = new HashSet<>();
        for (Dg d : dungeons.values()) {
            if (!d.hasCond() && freeAccess(u, d)) {
                allow.add(d.id());
            }
        }
        Pass ps = pending.get(u);
        if (ps != null) {
            allow.add(ps.id());
        }
        String in = inside.get(u);
        if (in != null) {
            allow.add(in);
        }
        PermissionAttachment a = attach.get(u);
        if (a != null && a.getPermissible() != p) {
            attach.remove(u);   // quyền tạm của lượt chơi trước (23/10: lần vào sau bị "không có quyền vào hầm ngục này")
            a = null;
        }
        if (a == null) {
            a = p.addAttachment(plugin);
            if (a == null) {   // 27/10: đối tượng người chơi của lượt cũ (thoát rồi vào lại trong 1 giây) không gắn quyền được
                return;
            }
            attach.put(u, a);
        }
        for (Dg d : dungeons.values()) {
            boolean want = allow.contains(d.id());
            Boolean has = a.getPermissions().get(PERM + d.id());
            if (want && has == null) {
                a.setPermission(PERM + d.id(), true);
            } else if (!want && has != null) {
                a.unsetPermission(PERM + d.id());
            }
        }
    }

    private void refund(UUID u, String id) {
        addTickets(u, id, 1);
    }

    private void tick() {
        tickCount++;
        if (hooked && tickCount % 12 == 0) {
            applyPermissions();
        }
        if (hooked && tickCount % 720 == 0) {
            autoHide();   // mỗi giờ thử lại nếu còn dungeon ẩn chưa có chỗ (vd chưa có vùng nào mở)
        }
        long now = System.currentTimeMillis();
        boolean dirty = false;
        // hết giờ chờ mà chưa vào: hoàn vé
        for (var it = pending.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (e.getValue().until() < now) {
                it.remove();
                if (e.getValue().ticket()) {
                    refund(e.getKey(), e.getValue().id());
                    dirty = true;
                }
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null) {
                    p.sendMessage(c("&7⌛ Cổng &f" + name(e.getValue().id()) + " &7đã đóng (chưa vào)." + (e.getValue().ticket() ? " Vé đã được hoàn lại." : "")));
                    refresh(p);
                }
            }
        }
        // quỹ chung hết hạn mở
        for (Dg d : dungeons.values()) {
            long until = data.getLong("quy." + d.id() + ".mo", 0);
            if (d.type().equals("server") && until > 0 && until < now) {
                data.set("quy." + d.id() + ".mo", null);
                dirty = true;
                PlayerSettings.broadcast("sukien", c("&5⛩ Cổng &d" + d.name() + " &5đã đóng lại. Góp quỹ để mở lần sau: &f/dungeon gop " + d.id() + " <xu>"));
                Bukkit.getOnlinePlayers().forEach(this::refresh);
            }
        }
        // dungeon ẩn: lộ diện khi tới gần + hạt sáng gợi ý
        for (Dg d : dungeons.values()) {
            if (!d.type().equals("hidden")) {
                continue;
            }
            Location spot = spot(d.id());
            if (spot == null || (d.nightOnly() && !night(spot.getWorld()))) {
                continue;
            }
            for (Player p : spot.getWorld().getPlayers()) {
                double dist = p.getLocation().distanceSquared(spot);
                if (dist < 24 * 24) {
                    p.spawnParticle(Particle.PORTAL, spot.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.2);
                }
                if (dist <= hiddenRadius * hiddenRadius && !found(p.getUniqueId(), d.id()) && baseOk(d)) {
                    List<String> l = new ArrayList<>(data.getStringList("tim." + uk(p.getUniqueId())));
                    l.add(d.id());
                    data.set("tim." + uk(p.getUniqueId()), l);
                    dirty = true;
                    p.playSound(p.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.6f, 1.4f);
                    p.sendMessage(c("&5⛩ Bạn đã tìm ra dungeon ẩn: &d" + d.name() + "&5! &7Vào bất cứ lúc nào trong mùa này: "));
                    p.sendMessage(button("&d[Vào ngay]", "/dungeon vao " + d.id(), "Mở cổng " + d.name()));
                    Achievements.add(p, "dungeonan", 1);
                    refresh(p);
                }
            }
        }
        if (dirty) {
            save();
        }
    }

    /** Dungeon ẩn chưa có chỗ giấu: server tự chọn chỗ đất hoang ngẫu nhiên (không báo ai, admin xem bằng /dungeon datan… hoặc log). */
    private void autoHide() {
        for (Dg d : dungeons.values()) {
            if (d.type().equals("hidden") && !data.contains("an." + d.id())) {
                String r = hideRandom(d);
                if (r != null) {
                    plugin.getLogger().info("Cổng Dungeon: tự giấu lối vào " + d.name() + " tại " + r);
                }
            }
        }
    }

    private String hideRandom(Dg d) {
        WildGates wg = Shared.wildGates;
        Location l = wg == null ? null : wg.randomSpot(false);
        if (l == null) {
            return null;
        }
        data.set("an." + d.id(), l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ());
        save();
        return l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
    }

    private static boolean night(World w) {
        long t = w.getTime();
        return t >= 13000 && t <= 23000;
    }

    private Location spot(String id) {
        String v = data.getString("an." + id);
        if (v == null) {
            return null;
        }
        String[] a = v.split(";");
        World w = Bukkit.getWorld(a[0]);
        return w == null ? null : new Location(w, Double.parseDouble(a[1]) + 0.5, Double.parseDouble(a[2]), Double.parseDouble(a[3]) + 0.5);
    }

    private String name(String id) {
        Dg d = dungeons.get(id);
        return d == null ? id : d.name();
    }

    // ---------------------------------------------------------------- sự kiện EliteMobs

    private void onEnter(Collection<Player> ps, String id) {
        if (!dungeons.containsKey(id)) {
            return;
        }
        for (Player p : ps) {
            inside.put(p.getUniqueId(), id);
            enteredAt.putIfAbsent(p.getUniqueId(), System.currentTimeMillis());
            Pass pass = pending.get(p.getUniqueId());
            if (pass != null && pass.id().equals(id)) {
                pending.remove(p.getUniqueId());   // vé đã dùng (đã trừ lúc mở cổng)
                if (pass.gate() && Shared.wanderers != null) {
                    Shared.wanderers.onGatePass(p, id);
                }
            }
            refresh(p);
            // 26/10: member không biết cách ra giữa chừng
            p.sendMessage(c("&7⛩ Muốn thoát dungeon giữa chừng: gõ &f/em quit"));
            DailyQuests.feature(p, "dungeon");
        }
        if (autoStart && playerInstance != null && !ps.isEmpty()) {
            Player first = ps.iterator().next();
            // chờ cả nhóm dịch chuyển vào rồi bắt đầu trận một lần cho mỗi instance (vào instance chỉ là phòng chờ)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    Object inst = first.isOnline() ? playerInstance.invoke(null, first) : null;
                    if (inst != null && started.add(inst)) {
                        first.performCommand("em start");
                    }
                } catch (ReflectiveOperationException | RuntimeException e) {
                    plugin.getLogger().warning("Cổng Dungeon: tự bắt đầu trận lỗi: " + e);
                }
            }, 40L);
        }
    }

    /** Lượt kiểm tra sẵn sàng của nhóm EliteMobs đang chờ: bấm [SẴN SÀNG] hộ mọi thành viên đang online (mã lượt lấy từ ReadyCheck.token). */
    private void autoReadyTick() {
        if (!autoReady || readyChecks == null || readyChecks.isEmpty()) {
            return;
        }
        for (Object rc : List.copyOf(readyChecks.values())) {
            try {
                java.lang.reflect.Field tf = rc.getClass().getDeclaredField("token");
                java.lang.reflect.Field mf = rc.getClass().getDeclaredField("memberIds");
                java.lang.reflect.Field rf = rc.getClass().getDeclaredField("readyPlayerIds");
                tf.setAccessible(true);
                mf.setAccessible(true);
                rf.setAccessible(true);
                String token = String.valueOf(tf.get(rc));
                Set<?> ready = (Set<?>) rf.get(rc);
                for (Object id : List.copyOf((List<?>) mf.get(rc))) {
                    Player m = id instanceof UUID u ? Bukkit.getPlayer(u) : null;
                    if (m != null && !ready.contains(id)) {
                        readyCall.invoke(null, m, token);
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                plugin.getLogger().warning("Cổng Dungeon: tự xác nhận sẵn sàng lỗi: " + e);
                readyChecks = null;   // tắt hẳn, khỏi lỗi lặp lại mỗi nửa giây
                return;
            }
        }
    }

    private void onComplete(Collection<Player> ps, String id) {
        Dg d = dungeons.get(id);
        if (d == null) {
            return;
        }
        if (!data.contains("dau." + id) && !ps.isEmpty()) {
            List<String> names = ps.stream().map(Player::getName).toList();
            data.set("dau." + id + ".ten", names);
            data.set("dau." + id + ".luc", System.currentTimeMillis());
            PlayerSettings.broadcast("sukien", c("&6♛ ĐẦU TIÊN! &e" + String.join(", ", names) + " &6là người đầu tiên của server vượt &f" + d.name() + "&6!"));
        }
        data.set("dem." + id, data.getInt("dem." + id, 0) + 1);
        long start = ps.stream().map(p -> enteredAt.get(p.getUniqueId())).filter(java.util.Objects::nonNull).min(Long::compare).orElse(0L);
        if (start > 0) {
            record(d, ps, (System.currentTimeMillis() - start) / 1000);
        }
        boolean weekly = id.equals(weeklyId());
        for (Player p : ps) {
            UUID u = p.getUniqueId();
            enteredAt.remove(u);
            Achievements.add(p, "dungeon", 1);
            if (weekly && !weeklyRewards.isEmpty() && rewards != null) {
                String wk = "tuanthuong." + weekKey() + "." + uk(u);
                if (data.getInt(wk, 0) < weeklyMax) {
                    data.set(wk, data.getInt(wk, 0) + 1);
                    List<String> got = rewards.give(p, weeklyRewards);
                    if (!got.isEmpty()) {
                        p.sendMessage(c("&6★ Dungeon của tuần: thưởng thêm &e" + String.join(", ", got) + " &7(" + data.getInt(wk, 0) + "/" + weeklyMax + " tuần này)"));
                    }
                }
            }
            giveKeys(p, id);
            if (cleared(u, id)) {
                continue;
            }
            List<String> l = new ArrayList<>(data.getStringList("qua." + uk(u)));
            l.add(id);
            data.set("qua." + uk(u), l);
            p.sendMessage(c("&a✔ Lần đầu vượt &f" + d.name() + "&a!"));
            if (!d.firstRewards().isEmpty() && rewards != null) {
                List<String> got = rewards.give(p, d.firstRewards());
                if (!got.isEmpty()) {
                    p.sendMessage(c("&7Quà lần đầu: &e" + String.join(", ", got)));
                }
            }
            refresh(p);
        }
        save();
        writeJson();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(id);   // lấy người chơi hiện tại: thoát rồi vào lại thì đối tượng cũ vẫn báo online theo UUID
            if (p != null && p.isValid()) {
                refresh(p);
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        attach.remove(e.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- Vé Vận Mệnh (may mắn)

    private List<Dg> luckDungeons() {
        return dungeons.values().stream().filter(d -> d.type().equals("luck") && baseOk(d)).toList();
    }

    private ItemStack luckTicket(Dg d) {
        ItemStack it = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta m = it.getItemMeta();
        Tip t = Tip.of("&d✦ &fVé Vận Mệnh").sub(d.name()).accent("&d").desc("Mở cổng dungeon may mắn " + d.name()).line()
                .kv("Dùng", "&e/dungeon vao " + d.id())
                .kv("Mở cổng", d.rollChance() < 100 ? "&c" + Math.round(d.rollChance()) + "% &8(trượt thì vé tan biến)" : "&achắc chắn");
        m.displayName(t.name());
        m.lore(t.lore());
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(ticketKey, PersistentDataType.STRING, d.id());
        it.setItemMeta(m);
        return it;
    }

    private void maybeLuck(Player p) {
        List<Dg> l = luckDungeons();
        if (l.isEmpty()) {
            return;
        }
        String k = "vanmenh." + uk(p.getUniqueId()) + "." + LocalDate.now(VN);
        if (data.getInt(k, 0) >= luckPerDay) {
            return;
        }
        data.set(k, data.getInt(k, 0) + 1);
        save();
        Dg d = l.get(ThreadLocalRandom.current().nextInt(l.size()));
        Rewards.hand(p, luckTicket(d));
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        PlayerSettings.broadcast("sukien", c("&d✦ " + p.getName() + " &5vừa nhặt được &dVé Vận Mệnh &5(" + d.name() + ")&5! Cực hiếm!"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k != null && e.getEntity() instanceof Monster && inside.get(k.getUniqueId()) == null) {
            if (ThreadLocalRandom.current().nextDouble(100) < luckMob) {
                maybeLuck(k);
            }
            if (ThreadLocalRandom.current().nextDouble(100) < fragMob) {
                maybeFragment(k);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble(100) < luckFish) {
            maybeLuck(e.getPlayer());
        }
        if (ThreadLocalRandom.current().nextDouble(100) < fragFish) {
            maybeFragment(e.getPlayer());
        }
    }

    /** Lấy một Vé Vận Mệnh của dungeon id trong túi (true nếu có và đã trừ). */
    private boolean takeLuckTicket(Player p, String id) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && it.hasItemMeta() && id.equals(it.getItemMeta().getPersistentDataContainer().get(ticketKey, PersistentDataType.STRING))) {
                it.setAmount(it.getAmount() - 1);
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- Chìa khoá tầng (chuỗi chain)

    private boolean unlocked(UUID u, String id) {
        return data.getStringList("mo." + uk(u)).contains(id);
    }

    private ItemStack keyItem(Dg d, Player owner) {
        ItemStack it = new ItemStack(Material.TRIAL_KEY);
        ItemMeta m = it.getItemMeta();
        Tip t = Tip.of("&6⚿ &fChìa khoá " + d.name()).accent("&6").desc("Mở vĩnh viễn tầng " + d.name()).line();
        if (!d.level().isBlank()) {
            t.kv("Cấp", d.level());
        }
        t.kv("Dùng", "&e/dungeon vao " + d.id()).note("Chìa mất khi dùng");
        if (!keyTradeable) {
            t.deny("Chỉ " + owner.getName() + " dùng được");
            m.getPersistentDataContainer().set(keyOwner, PersistentDataType.STRING, owner.getUniqueId().toString());
        }
        m.displayName(t.name());
        m.lore(t.lore());
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(keyKey, PersistentDataType.STRING, d.id());
        it.setItemMeta(m);
        return it;
    }

    /** Ô chứa chìa của tầng id mà người này dùng được (null nếu không có). */
    private ItemStack keySlot(Player p, String id) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null || !it.hasItemMeta()) {
                continue;
            }
            var pdc = it.getItemMeta().getPersistentDataContainer();
            String owner = pdc.get(keyOwner, PersistentDataType.STRING);
            if (id.equals(pdc.get(keyKey, PersistentDataType.STRING)) && (keyTradeable || owner == null || owner.equals(p.getUniqueId().toString()))) {
                return it;
            }
        }
        return null;
    }

    /** Có chìa thì dùng: mất chìa, mở vĩnh viễn tầng cho người này. */
    private boolean useKey(Player p, Dg d) {
        ItemStack k = keySlot(p, d.id());
        if (k == null) {
            return false;
        }
        k.setAmount(k.getAmount() - 1);
        List<String> l = new ArrayList<>(data.getStringList("mo." + uk(p.getUniqueId())));
        l.add(d.id());
        data.set("mo." + uk(p.getUniqueId()), l);
        save();
        p.playSound(p.getLocation(), Sound.BLOCK_VAULT_OPEN_SHUTTER, 1f, 1f);
        p.sendMessage(c("&6⚿ Đã dùng Chìa khoá: tầng &e" + d.name() + " &6mở vĩnh viễn cho bạn!"));
        refresh(p);
        return true;
    }

    /** Vượt tầng id: rơi chìa tầng sau (nếu chưa mở và chưa có chìa trong túi). */
    private void giveKeys(Player p, String id) {
        for (Dg next : dungeons.values()) {
            if (!next.type().equals("chain") || !id.equals(next.requires()) || unlocked(p.getUniqueId(), next.id()) || keySlot(p, next.id()) != null) {
                continue;
            }
            Rewards.hand(p, keyItem(next, p));
            p.sendMessage(c("&6⚿ Trùm cuối rơi &eChìa khoá " + next.name() + "&6! Dùng để mở tầng sau: ")
                    .append(button(" &e[Mở " + next.name() + "]", "/dungeon vao " + next.id(), "Dùng chìa, mở vĩnh viễn tầng " + next.name())));
        }
    }

    @EventHandler
    public void onKeyUse(org.bukkit.event.player.PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || !e.getAction().isRightClick()) {
            return;
        }
        ItemStack it = e.getItem();
        String id = it == null || !it.hasItemMeta() ? null : it.getItemMeta().getPersistentDataContainer().get(keyKey, PersistentDataType.STRING);
        Dg d = id == null ? null : dungeons.get(id);
        if (d == null) {
            return;
        }
        e.setCancelled(true);   // chìa là TRIAL_KEY: không cho mở kho thử thách bằng nó
        Player p = e.getPlayer();
        if (unlocked(p.getUniqueId(), id)) {
            p.sendMessage(c("&7Tầng " + d.name() + " đã mở cho bạn rồi. Giữ chìa (hoặc cho người khác nếu được trao đổi)."));
        } else if (!useKey(p, d)) {
            p.sendMessage(c("&cChìa này không phải của bạn."));
        }
    }

    // ---------------------------------------------------------------- Mảnh Chìa (dungeon ẩn có fragments)

    private ItemStack fragment(Dg d) {
        ItemStack it = new ItemStack(Material.ECHO_SHARD);
        ItemMeta m = it.getItemMeta();
        Tip t = Tip.of("&5✧ &fMảnh Chìa").sub(d.name()).accent("&5").desc("Ghép đủ mảnh để mở lối vào " + d.name()).line()
                .kv("Cần", d.fragments() + " mảnh").kv("Ghép", "&e/dungeon vao " + d.id());
        m.displayName(t.name());
        m.lore(t.lore());
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(fragKey, PersistentDataType.STRING, d.id());
        it.setItemMeta(m);
        return it;
    }

    private int countFragments(Player p, String id) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && it.hasItemMeta() && id.equals(it.getItemMeta().getPersistentDataContainer().get(fragKey, PersistentDataType.STRING))) {
                n += it.getAmount();
            }
        }
        return n;
    }

    private void takeFragments(Player p, String id, int n) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (n > 0 && it != null && it.hasItemMeta() && id.equals(it.getItemMeta().getPersistentDataContainer().get(fragKey, PersistentDataType.STRING))) {
                int t = Math.min(n, it.getAmount());
                it.setAmount(it.getAmount() - t);
                n -= t;
            }
        }
    }

    /** Rơi Mảnh Chìa cho người đã tìm ra dungeon ẩn mà chưa ghép đủ. */
    private void maybeFragment(Player p) {
        UUID u = p.getUniqueId();
        List<Dg> l = dungeons.values().stream().filter(d -> d.type().equals("hidden") && d.fragments() > 0 && found(u, d.id())
                && !assembled(u, d.id()) && countFragments(p, d.id()) < d.fragments()).toList();
        if (l.isEmpty()) {
            return;
        }
        String k = "manhngay." + uk(u) + "." + LocalDate.now(VN);
        if (data.getInt(k, 0) >= fragPerDay) {
            return;
        }
        data.set(k, data.getInt(k, 0) + 1);
        save();
        Dg d = l.get(ThreadLocalRandom.current().nextInt(l.size()));
        Rewards.hand(p, fragment(d));
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 0.8f);
        p.sendMessage(c("&5✧ Nhặt được &dMảnh Chìa &5(" + d.name() + "&5): &f" + countFragments(p, d.id()) + "/" + d.fragments()));
    }

    // ---------------------------------------------------------------- Dungeon của tuần + kỷ lục

    private static String weekKey() {
        LocalDate d = LocalDate.now(VN);
        return d.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR) + "w" + d.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    /** Dungeon của tuần (chốt lần đầu hỏi trong tuần, lưu lại để không đổi giữa tuần). */
    String weeklyId() {
        if (!weeklyOn) {
            return null;
        }
        String k = "tuan." + weekKey();
        String v = data.getString(k);
        if (v != null && dungeons.containsKey(v)) {
            return v;
        }
        if (!hooked) {
            return null;
        }
        List<Dg> l = dungeons.values().stream().filter(d -> weeklyTypes.contains(d.type()) && installed(d.id())).toList();
        if (l.isEmpty()) {
            return null;
        }
        String last = data.getString("tuan-truoc", "");
        List<Dg> pick = l.size() > 1 ? l.stream().filter(d -> !d.id().equals(last)).toList() : l;
        String id = pick.get(ThreadLocalRandom.current().nextInt(pick.size())).id();
        data.set("tuan", null);
        data.set(k, id);
        data.set("tuan-truoc", id);
        save();
        PlayerSettings.broadcast("sukien", c("&6★ Dungeon của tuần: &e" + name(id) + "&6! Vượt được thêm quà. &f/dungeon"));
        return id;
    }

    private void record(Dg d, Collection<Player> ps, long secs) {
        if (secs < 10) {
            return;
        }
        String k = "kyluc." + d.id();
        List<String> l = new ArrayList<>(data.getStringList(k));
        String names = String.join(", ", ps.stream().map(Player::getName).sorted().toList());
        boolean best = l.isEmpty() || secs < Long.parseLong(l.get(0).split(";", 2)[0]);
        l.add(secs + ";" + names);
        l.sort(java.util.Comparator.comparingLong(x -> Long.parseLong(x.split(";", 2)[0])));
        data.set(k, l.subList(0, Math.min(5, l.size())));
        String t = clock(secs);
        if (best) {
            PlayerSettings.broadcast("sukien", c("&b⏱ KỶ LỤC MỚI! &f" + names + " &bvượt &f" + d.name() + " &btrong &e" + t + "&b!"));
        } else {
            ps.forEach(p -> p.sendMessage(c("&7⏱ Thời gian vượt: &f" + t)));
        }
    }

    private static String clock(long secs) {
        return secs >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", secs / 3600, secs / 60 % 60, secs % 60)
                : String.format(Locale.ROOT, "%d:%02d", secs / 60, secs % 60);
    }

    /** Kho báu bậc hiếm: chỉ đường tới một lối vào ẩn người này chưa tìm ra (true nếu có). */
    boolean revealHidden(Player p) {
        List<Dg> l = dungeons.values().stream().filter(d -> d.type().equals("hidden") && baseOk(d) && !found(p.getUniqueId(), d.id())
                && spot(d.id()) != null).toList();
        if (l.isEmpty()) {
            return false;
        }
        Dg d = l.get(ThreadLocalRandom.current().nextInt(l.size()));
        Location s = spot(d.id());
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int x = s.getBlockX() + r.nextInt(-40, 41), z = s.getBlockZ() + r.nextInt(-40, 41);
        p.playSound(p.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 0.6f);
        p.sendMessage(c("&5⌖ Dưới đáy rương có mảnh giấy cũ: &d\"Lối vào " + d.name() + " ở quanh X " + x + " Z " + z
                + (d.nightOnly() ? ", chỉ hiện lúc đêm" : "") + "\"&5. &7(trong khoảng 40 khối, tìm hạt tím)"));
        return true;
    }

    // ---------------------------------------------------------------- lệnh

    boolean command(CommandSender s, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        Dg d = args.length > 1 ? dungeons.get(args[1]) : null;
        switch (sub) {
            case "vao" -> {
                if (!(s instanceof Player p)) {
                    return true;
                }
                if (d == null) {
                    p.sendMessage(c("&e/dungeon vao <mã> &7(xem mã trong /dungeon)"));
                    return true;
                }
                enter(p, d);
                return true;
            }
            case "mua" -> {
                if (!(s instanceof Player p) || d == null || !d.type().equals("ticket")) {
                    s.sendMessage(c("&e/dungeon mua <mã dungeon vé cá nhân> [số vé]"));
                    return true;
                }
                if (!baseOk(d)) {
                    p.sendMessage(c("&cVùng của dungeon này chưa khai hoang."));
                    return true;
                }
                int n = 1;
                try {
                    n = Math.max(1, Integer.parseInt(args.length > 2 ? args[2] : "1"));
                } catch (NumberFormatException ignored) {
                    // giữ 1
                }
                n = Math.min(n, d.maxTickets() - tickets(p.getUniqueId(), d.id()));
                if (n <= 0) {
                    p.sendMessage(c("&7Bạn đã giữ tối đa &f" + d.maxTickets() + " &7vé &f" + d.name() + "&7."));
                    return true;
                }
                if (!VaultEconomy.available() || !VaultEconomy.withdraw(p, d.price() * n)) {
                    p.sendMessage(c("&cKhông đủ tiền (cần " + money(d.price() * n) + ")."));
                    return true;
                }
                addTickets(p.getUniqueId(), d.id(), n);
                save();
                writeJson();
                p.sendMessage(c("&a✔ Đã mua &f" + n + " &avé &f" + d.name() + " &a(" + money(d.price() * n) + "). Đang có: &f"
                        + tickets(p.getUniqueId(), d.id()) + " &avé."));
                p.sendMessage(button("&a[Vào ngay]", "/dungeon vao " + d.id(), "Mở cổng (trừ 1 vé khi vào)"));
                return true;
            }
            case "gop" -> {
                if (!(s instanceof Player p) || d == null || !d.type().equals("server")) {
                    s.sendMessage(c("&e/dungeon gop <mã dungeon mở chung> <xu>"));
                    return true;
                }
                double amount;
                try {
                    amount = Math.floor(Double.parseDouble(args.length > 2 ? args[2] : "0"));
                } catch (NumberFormatException e) {
                    amount = 0;
                }
                if (amount < 1) {
                    p.sendMessage(c("&e/dungeon gop " + d.id() + " <xu>"));
                    return true;
                }
                if (!baseOk(d)) {
                    p.sendMessage(c("&cVùng của dungeon này chưa khai hoang."));
                    return true;
                }
                if (serverOpen(d.id())) {
                    p.sendMessage(c("&7Cổng &f" + d.name() + " &7đang mở, chưa cần góp."));
                    return true;
                }
                double need = d.goal() - data.getDouble("quy." + d.id() + ".xu", 0);
                amount = Math.min(amount, Math.ceil(need));
                if (!VaultEconomy.available() || !VaultEconomy.withdraw(p, amount)) {
                    p.sendMessage(c("&cKhông đủ tiền."));
                    return true;
                }
                addFund(p.getName(), d, amount);
                p.sendMessage(c("&a✔ Đã góp &f" + money(amount) + " &avào quỹ mở cổng &f" + d.name() + "&a. Cảm ơn!"));
                return true;
            }
            case "datan" -> {
                if (s.hasPermission("duckymomo.admin") && d != null && d.type().equals("hidden") && args.length > 2 && args[2].equalsIgnoreCase("ngaunhien")) {
                    data.set("an." + d.id(), null);
                    String r = hideRandom(d);
                    s.sendMessage(r == null ? "Không tìm được chỗ (cần Cổng Hoang bật và vùng /tpr đã mở)." : "Đã giấu ngẫu nhiên " + d.name() + " tại " + r);
                    return true;
                }
                if (!s.hasPermission("duckymomo.admin") || !(s instanceof Player p) || d == null || !d.type().equals("hidden")) {
                    s.sendMessage("/dungeon datan <mã dungeon ẩn> [ngaunhien]  (đứng tại chỗ muốn giấu lối vào, hoặc để server chọn ngẫu nhiên)");
                    return true;
                }
                Location l = p.getLocation();
                data.set("an." + d.id(), l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ());
                save();
                p.sendMessage(c("&a✔ Đã giấu lối vào &f" + d.name() + " &atại " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()
                        + " &7(người chơi tới trong " + hiddenRadius + " khối là tìm ra" + (d.nightOnly() ? ", chỉ ban đêm" : "") + ")"));
                return true;
            }
            case "ds" -> {
                if (!s.hasPermission("duckymomo.admin")) {
                    list(s);
                    return true;
                }
                s.sendMessage(c("&5━━ Chỗ giấu dungeon ẩn (chỉ admin thấy) ━━"));
                for (Dg h : dungeons.values()) {
                    if (!h.type().equals("hidden")) {
                        continue;
                    }
                    Location l = spot(h.id());
                    if (l == null) {
                        s.sendMessage(c("  &d" + h.name() + " &8— &cchưa có chỗ &7(/dungeon datan " + h.id() + " ngaunhien)"));
                        continue;
                    }
                    String xyz = l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
                    int n = 0;
                    ConfigurationSection tim = data.getConfigurationSection("tim");
                    if (tim != null) {
                        for (String k : tim.getKeys(false)) {
                            n += data.getStringList("tim." + k).contains(h.id()) ? 1 : 0;
                        }
                    }
                    s.sendMessage(c("  &d" + h.name() + " &8— &f" + l.getWorld().getName() + " " + xyz + " &7· " + n + " người đã tìm ra" + (h.nightOnly() ? " · chỉ lộ ban đêm" : ""))
                            .append(c(" &a[Tới]").clickEvent(ClickEvent.runCommand("/minecraft:tp @s " + xyz))
                                    .hoverEvent(HoverEvent.showText(c("&7Dịch chuyển tới " + xyz)))));
                }
                return true;
            }
            case "cho" -> {
                if (!s.hasPermission("duckymomo.admin") || args.length < 3) {
                    s.sendMessage("/dungeon cho <mã> <người> [số]  (vé cá nhân, Vé Vận Mệnh, Mảnh Chìa, Chìa khoá tầng)");
                    return true;
                }
                Player t = Bukkit.getPlayerExact(args[2]);
                if (d == null || t == null) {
                    s.sendMessage("Sai mã dungeon hoặc người chơi offline.");
                    return true;
                }
                int n = args.length > 3 ? Math.max(1, Integer.parseInt(args[3])) : 1;
                if (d.type().equals("luck") || d.type().equals("hidden") || d.type().equals("chain")) {
                    for (int i = 0; i < n; i++) {
                        Rewards.hand(t, d.type().equals("luck") ? luckTicket(d) : d.type().equals("chain") ? keyItem(d, t) : fragment(d));
                    }
                } else {
                    addTickets(t.getUniqueId(), d.id(), n);
                    save();
                }
                s.sendMessage("Đã cho " + t.getName() + " " + n + " vé " + d.name());
                return true;
            }
            case "mo" -> {
                if (!s.hasPermission("duckymomo.admin") || d == null || !d.type().equals("server")) {
                    s.sendMessage("/dungeon mo <mã dungeon mở chung>  (admin mở ngay, không cần quỹ)");
                    return true;
                }
                addFund(null, d, d.goal());
                return true;
            }
            default -> {
                list(s);
                return true;
            }
        }
    }

    private void addFund(String who, Dg d, double amount) {
        String k = "quy." + d.id();
        double v = data.getDouble(k + ".xu", 0) + amount;
        if (who != null) {
            data.set(k + ".gop." + who, data.getDouble(k + ".gop." + who, 0) + amount);
        }
        if (v >= d.goal()) {
            data.set(k + ".xu", 0);
            data.set(k + ".gop", null);
            data.set(k + ".mo", d.openDays() == 0 ? -1 : System.currentTimeMillis() + d.openDays() * 86_400_000L);
            PlayerSettings.broadcast("sukien", c("&5⛩ CỔNG ĐÃ MỞ! &dQuỹ chung đã đủ, &f" + d.name() + " &dmở cho cả server "
                    + (d.openDays() == 0 ? "tới hết mùa" : "trong " + d.openDays() + " ngày") + ". &f/dungeon vao " + d.id()));
            Bukkit.getOnlinePlayers().forEach(this::refresh);
        } else {
            data.set(k + ".xu", v);
        }
        save();
        writeJson();
    }

    private void enter(Player p, Dg d) {
        UUID u = p.getUniqueId();
        if (!hooked) {
            p.sendMessage(c("&cHệ thống dungeon (EliteMobs) chưa sẵn sàng."));
            return;
        }
        if (!installed(d.id())) {
            p.sendMessage(c("&c" + d.name() + " chưa được cài trên server."));
            return;
        }
        if (!baseOk(d)) {
            p.sendMessage(c("&cVùng của dungeon này chưa khai hoang (cần mốc khai hoang mở trước)."));
            return;
        }
        Pass ps = pending.get(u);
        if (ps != null && ps.id().equals(d.id())) {
            open(p, d);   // cổng vẫn đang mở: mở lại menu
            return;
        }
        if (d.type().equals("gate")) {
            p.sendMessage(c("&5◎ " + d.name() + " chỉ vào được qua &dCổng Hoang&5: tìm cổng ngụy trang trên bản đồ (/conghoang xem tin đồn)."));
            return;
        }
        String tp = timeProblem(d);
        if (tp != null) {
            p.sendMessage(c("&c" + d.name() + " chỉ mở " + tp + "."));
            return;
        }
        List<Player> roster = roster(p);
        if (d.partyMin() > 1 && roster.size() < d.partyMin() || d.partyMax() > 0 && roster.size() > d.partyMax()) {
            p.sendMessage(c("&c" + d.name() + " cần nhóm &f" + partyText(d) + " &cngười (nhóm của bạn: " + roster.size()
                    + "). Lập nhóm: &f/em party create&c, mời: &f/em party invite <tên>"));
            return;
        }
        List<String> problems = new ArrayList<>();
        for (Player m : roster) {
            String pr = memberProblem(m, d);
            if (pr != null) {
                problems.add((m == p ? "Bạn" : m.getName()) + ": " + pr);
            }
        }
        if (!problems.isEmpty()) {
            p.sendMessage(c("&cChưa vào được &f" + d.name() + "&c:"));
            problems.forEach(x -> p.sendMessage(c("  &8• &7" + x)));
            return;
        }
        boolean ticketType = d.type().equals("ticket") || d.type().equals("luck");
        boolean rolled = false;
        for (Player m : roster) {
            if (ticketType) {
                if (tickets(m.getUniqueId(), d.id()) > 0) {
                    addTickets(m.getUniqueId(), d.id(), -1);
                } else if (d.type().equals("luck") && takeLuckTicket(m, d.id())) {
                    rolled = true;   // vừa dùng Vé Vận Mệnh trong túi → tung xúc xắc
                }
            }
        }
        if (rolled && ThreadLocalRandom.current().nextDouble(100) >= d.rollChance()) {
            save();
            roster.forEach(m -> {
                m.playSound(m.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.6f);
                m.sendMessage(c("&5✦ Vận mệnh không mỉm cười… Vé tan biến &7(" + Math.round(d.rollChance()) + "% cổng mở)."));
            });
            return;
        }
        long until = System.currentTimeMillis() + windowMs;
        for (Player m : roster) {
            if (ticketType || d.hasCond() || !freeAccess(m.getUniqueId(), d)) {
                Pass old = pending.get(m.getUniqueId());
                if (old != null && old.ticket() && !old.id().equals(d.id())) {
                    refund(m.getUniqueId(), old.id());
                }
                pending.put(m.getUniqueId(), new Pass(d.id(), until, ticketType, false));
                refresh(m);
            }
            if (m != p) {
                m.sendMessage(c("&a⛩ " + p.getName() + " đã mở cổng &f" + d.name() + " &acho cả nhóm" + (ticketType ? " (trừ 1 vé của bạn)" : "")
                        + ". Chờ trưởng nhóm chọn độ khó."));
            }
        }
        save();
        if (ticketType) {
            p.sendMessage(c("&a⛩ Cổng &f" + d.name() + " &ađã mở trong " + windowMs / 1000 + " giây (đã dùng vé; không vào kịp thì được hoàn vé)."));
        }
        open(p, d);
    }

    /** Người trong nhóm EliteMobs đang online (không có nhóm = chỉ mình). */
    private List<Player> roster(Player p) {
        if (partyIds != null) {
            try {
                List<Player> l = new ArrayList<>();
                for (Object o : (Collection<?>) partyIds.invoke(null, p)) {
                    Player m = o instanceof UUID id ? Bukkit.getPlayer(id) : null;
                    if (m != null && !l.contains(m)) {
                        l.add(m);
                    }
                }
                if (!l.contains(p)) {
                    l.add(0, p);
                }
                return l;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // coi như đi một mình
            }
        }
        return List.of(p);
    }

    /** Lý do một người chưa vào được (null = được). Dungeon ẩn đủ Mảnh Chìa thì ghép luôn ở đây. */
    private String memberProblem(Player m, Dg d) {
        UUID u = m.getUniqueId();
        if (inside.containsKey(u)) {
            return "đang ở trong một dungeon";
        }
        switch (d.type()) {
            case "chain" -> {
                if (!freeAccess(u, d)) {
                    if (!useKey(m, d)) {
                        return "cần Chìa khoá " + d.name() + " (trùm cuối " + name(d.requires()) + " rơi ra)";
                    }
                }
            }
            case "hidden" -> {
                if (!found(u, d.id())) {
                    return "chưa tìm ra lối vào" + (d.hint().isBlank() ? "" : " (gợi ý: " + d.hint() + ")");
                }
                if (d.fragments() > 0 && !assembled(u, d.id())) {
                    int have = countFragments(m, d.id());
                    if (have < d.fragments()) {
                        return "cần ghép " + d.fragments() + " Mảnh Chìa (đang có " + have + "; rơi khi hạ quái, câu cá)";
                    }
                    takeFragments(m, d.id(), d.fragments());
                    List<String> l = new ArrayList<>(data.getStringList("ghep." + uk(u)));
                    l.add(d.id());
                    data.set("ghep." + uk(u), l);
                    save();
                    m.playSound(m.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.7f, 1.2f);
                    m.sendMessage(c("&5✧ Đã ghép đủ Mảnh Chìa: lối vào &d" + d.name() + " &5mở cho bạn cả mùa!"));
                }
            }
            case "server" -> {
                if (!serverOpen(d.id())) {
                    return "cổng đang đóng, cả server góp quỹ: /dungeon gop " + d.id() + " <xu> (còn thiếu "
                            + money(d.goal() - data.getDouble("quy." + d.id() + ".xu", 0)) + ")";
                }
            }
            case "ticket" -> {
                if (tickets(u, d.id()) <= 0) {
                    return "cần vé: /dungeon mua " + d.id() + " (" + money(d.price()) + "/vé)";
                }
            }
            case "luck" -> {
                if (tickets(u, d.id()) <= 0 && !hasLuckTicket(m, d.id())) {
                    return "cần Vé Vận Mệnh (" + d.name() + "): rơi rất hiếm khi hạ quái hoặc câu cá";
                }
            }
            default -> { }
        }
        if (d.minPet() > 0) {
            Object pet = MyPetBridge.active(m);
            int lv = pet == null ? 0 : MyPetBridge.level(pet);
            if (lv < d.minPet()) {
                return "cần dẫn theo pet cấp " + d.minPet() + " trở lên" + (pet == null ? " (chưa gọi pet)" : " (pet đang cấp " + lv + ")");
            }
        }
        if (d.minAch() > 0) {
            int n = Achievements.doneCount(u);
            if (n >= 0 && n < d.minAch()) {
                return "cần " + d.minAch() + " mốc Sổ thành tích (đang có " + n + ", /thanhtich)";
            }
        }
        if (d.hegemonOnly()) {
            WarBalance wb = WarBalance.INSTANCE;
            com.palmergames.bukkit.towny.object.Resident r = com.palmergames.bukkit.towny.TownyAPI.getInstance().getResident(m);
            com.palmergames.bukkit.towny.object.Nation n = r == null ? null : r.getNationOrNull();
            if (wb == null || n == null || !wb.isHegemon(n)) {
                return "chỉ dân của nước Bá chủ (/canbang)";
            }
        }
        return null;
    }

    private boolean hasLuckTicket(Player p, String id) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && it.hasItemMeta() && id.equals(it.getItemMeta().getPersistentDataContainer().get(ticketKey, PersistentDataType.STRING))) {
                return true;
            }
        }
        return false;
    }

    /** Điều kiện theo thời gian chưa đạt → mô tả lúc mở; đạt → null. */
    private String timeProblem(Dg d) {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(VN);
        boolean ok = true;
        if (!d.days().isEmpty() && !d.days().contains(DAY_CODES.get(now.getDayOfWeek().getValue() - 1))) {
            ok = false;
        }
        if (d.hourFrom() >= 0) {
            int h = now.getHour(), a = d.hourFrom(), b = d.hourTo() < 0 ? a : d.hourTo();
            if (a <= b ? h < a || h > b : h < a && h > b) {
                ok = false;
            }
        }
        if (!d.seasons().isEmpty() && !d.seasons().contains(Seasons.now())) {
            ok = false;
        }
        return ok ? null : String.join(", ", timeText(d));
    }

    private static List<String> timeText(Dg d) {
        List<String> l = new ArrayList<>();
        if (!d.days().isEmpty()) {
            l.add(d.days().stream().map(x -> x.equals("cn") ? "Chủ nhật" : "Thứ " + x.substring(1)).collect(java.util.stream.Collectors.joining(", ")));
        }
        if (d.hourFrom() >= 0) {
            l.add("từ " + d.hourFrom() + "h tới " + ((d.hourTo() < 0 ? d.hourFrom() : d.hourTo()) + 1) + "h");
        }
        if (!d.seasons().isEmpty()) {
            l.add("mùa " + String.join("/", d.seasons().stream().map(x -> x.equals("mua") ? "mưa" : x.equals("kho") ? "khô" : x).toList()));
        }
        return l;
    }

    private static String partyText(Dg d) {
        int a = Math.max(1, d.partyMin()), b = d.partyMax();
        return b <= 0 ? "từ " + a : a == b ? String.valueOf(a) : a + "–" + b;
    }

    /** Các điều kiện phụ, dạng chữ cho /dungeon và web. */
    private List<String> condText(Dg d) {
        List<String> l = new ArrayList<>(timeText(d));
        if (d.partyMin() > 1 || d.partyMax() > 0) {
            l.add("nhóm " + partyText(d) + " người");
        }
        if (d.minPet() > 0) {
            l.add("pet cấp " + d.minPet() + "+");
        }
        if (d.minAch() > 0) {
            l.add(d.minAch() + " thành tích");
        }
        if (d.hegemonOnly()) {
            l.add("chỉ nước Bá chủ");
        }
        if (d.type().equals("hidden") && d.fragments() > 0) {
            l.add("ghép " + d.fragments() + " Mảnh Chìa");
        }
        return l;
    }

    String displayName(String id) {
        return name(id);
    }

    boolean isInstalled(String id) {
        return hooked && installed(id);
    }

    /** Vào thẳng dungeon id (Cổng Hoang): bỏ qua kiểu cổng và điều kiện của dungeon đó. */
    boolean passThrough(Player p, String id) {
        Dg d = dungeons.get(id);
        if (d == null || !hooked || !installed(id)) {
            p.sendMessage(c("&5◎ Cổng còn đang ngủ (dungeon " + (d == null ? id : d.name()) + " chưa sẵn sàng). Quay lại sau."));
            return false;
        }
        if (inside.containsKey(p.getUniqueId())) {
            p.sendMessage(c("&cBạn đang ở trong một dungeon."));
            return false;
        }
        pending.put(p.getUniqueId(), new Pass(id, System.currentTimeMillis() + windowMs, false, true));
        open(p, d);
        return true;
    }

    /** Cấp quyền rồi mở menu chọn độ khó của EliteMobs. */
    private void open(Player p, Dg d) {
        refresh(p);
        p.playSound(p.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 0.8f);
        p.performCommand("em dungeontp " + d.id() + ".yml");
    }

    /** Cách vào từng loại (dòng mô tả trong menu). */
    private static final Map<String, String> TYPE_HOW = Map.of(
            "chain", "Đi lần lượt: trùm cuối mỗi tầng rơi Chìa khoá, dùng chìa là mở vĩnh viễn tầng sau.",
            "hidden", "Không có lối vào trong menu: tự tìm cổng giấu trên bản đồ, để ý hạt tím lấp lánh.",
            "luck", "Cần Vé Vận Mệnh rơi hiếm từ quái.",
            "ticket", "Mua vé bằng xu, mỗi vé một lượt; chỉ trừ vé khi thật sự vào.",
            "server", "Cả server góp xu vào quỹ; đủ mục tiêu thì cổng mở cho mọi người.",
            "gate", "Chỉ vào được qua Cổng Hoang ngụy trang trên bản đồ (/conghoang).");
    /** 30/10: icon riêng từng dungeon (dungeons.<id>.icon), không có thì theo loại (TYPE_ICON). */
    private final Map<String, Material> dgIcon = new java.util.HashMap<>();
    private static final Map<String, Material> TYPE_ICON = Map.of("chain", Material.TRIPWIRE_HOOK, "hidden", Material.ENDER_EYE,
            "luck", Material.RABBIT_FOOT, "ticket", Material.PAPER, "server", Material.BELL, "gate", Material.END_PORTAL_FRAME);

    /** /dungeon cho người chơi: menu theo loại (22/10, trước in ~25 dòng chat). Trang chính = các loại + dungeon của tuần;
     *  bấm loại mở trang con. Bấm dungeon = như bấm tên trong chat (vào / mua vé / góp quỹ). */
    /** Cấp chiến đấu EliteMobs 10 (trung bình kỹ năng vũ khí + giáp, luyện bằng cách hạ quái elite; đồ kim cương KHÔNG tăng cấp).
     *  -1 nếu không đọc được (không có PlaceholderAPI). 28/10: người mới không biết vì sao vào dungeon bị đánh như không mặc giáp. */
    static int combatLevel(Player p) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return -1;
        }
        try {
            return Integer.parseInt(PapiText.apply(p, "%elitemobs_player_combat_level%").trim());
        } catch (RuntimeException | LinkageError e) {
            return -1;
        }
    }

    private static int levelOf(Dg d) {
        try {
            return Integer.parseInt(d.level().replaceAll("[^0-9].*$", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** "12 / cần ~10 (vừa sức)" có màu. */
    private static String fit(int mine, int need) {
        String c = mine >= need ? "&a" : mine >= need - 5 ? "&e" : "&c";
        String w = mine >= need ? "vừa sức" : mine >= need - 5 ? "hơi yếu" : "quá yếu";
        return c + mine + " &7/ cần ~" + need + " " + c + "(" + w + ")";
    }

    private void menu(Player p) {
        UUID u = p.getUniqueId();
        String weekly = weeklyId();
        InfoMenu m = InfoMenu.of("&5⛩ Cổng Dungeon").accent(Material.PURPLE_STAINED_GLASS_PANE);
        Tip top = Tip.of("&5⛩ &fCổng Dungeon").accent("&d")
                .desc("Đồ hiếm, quái mạnh chỉ có trong dungeon.", "Chọn loại để xem các cổng và cách vào.");
        if (weekly != null) {
            top.line().kv("★ Dungeon của tuần", "&e" + name(weekly));
            if (!weeklyRewards.isEmpty() && rewards != null) {
                top.kv("Thưởng thêm", String.join(", ", rewards.describe(weeklyRewards)) + " &7(tối đa " + weeklyMax + " lần/tuần)");
            }
        }
        int cl = combatLevel(p);
        if (cl >= 0) {
            top.line().kv("Cấp chiến đấu của bạn", "&f" + cl)
                    .note("Trong dungeon, sức mạnh tính theo CẤP CHIẾN ĐẤU (kỹ năng vũ khí + giáp của EliteMobs), không theo đồ kim cương hay netherite.")
                    .note("Luyện cấp: hạ quái elite ngoài đất hoang (quái có hiệu ứng phát sáng, cấp tự theo cấp của bạn), rồi vào dungeon vừa sức. Xem kỹ năng: /em");
        }
        m.top(top.apply(new ItemStack(Material.ENDER_EYE)));
        if (Shared.hamNguc != null && Shared.hamNguc.allowed(p)) {   // 29/10: hầm ngục tự dựng, rơi thẳng đồ set (đang thử: chỉ người trong chi-cho)
            m.add(Tip.of("&6⚔ Hầm Ngục DuckyMoMo").sub("mới").accent("&6")
                    .desc("Hầm ngục của server: quái rơi thẳng món set +0", "(Tân Thủ → Chiến Binh → Tinh Anh → Cao Cấp).")
                    .note("Không cần cấp chiến đấu, sức mạnh theo đồ bạn mặc.").line().click("Bấm để mở")
                    .apply(new ItemStack(Material.IRON_BARS)), InfoMenu.run("hamnguc"));
            m.row();
        }
        java.util.Map<String, List<Dg>> byType = new java.util.LinkedHashMap<>();
        for (Dg d : dungeons.values()) {
            byType.computeIfAbsent(d.type(), k -> new java.util.ArrayList<>()).add(d);
        }
        for (var e : byType.entrySet()) {
            String type = e.getKey();
            List<Dg> list = e.getValue();
            long done = list.stream().filter(d -> doneFor(u, d)).count();
            Tip t = Tip.of("&d" + TYPE_VI.get(type)).sub(list.size() + " cổng").accent("&d").desc(TYPE_HOW.getOrDefault(type, "")).line();
            for (Dg d : list) {
                boolean hid = type.equals("hidden") && !found(u, d.id());
                t.raw((doneFor(u, d) ? "&a✔ " : "&8○ ") + (hid ? "&7???" : "&f" + d.name()) + (d.level().isBlank() ? "" : " &7cấp " + d.level())
                        + (d.id().equals(weekly) ? " &6★" : ""));
            }
            t.line().click("Bấm để xem từng cổng");
            ItemStack icon = t.apply(new ItemStack(TYPE_ICON.getOrDefault(type, Material.COMPASS), (int) Math.max(1, Math.min(64, list.size()))));
            if (done > 0) {
                icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            }
            m.add(icon, pl -> typeMenu(pl, type));
        }
        if (weekly != null && dungeons.containsKey(weekly)) {
            m.row();
            Dg w = dungeons.get(weekly);
            var en = entry(p, w, weekly);
            m.add(en.getKey(), en.getValue());
        }
        m.open(p);
    }

    private boolean doneFor(UUID u, Dg d) {
        return switch (d.type()) {
            case "chain" -> cleared(u, d.id());
            case "hidden" -> found(u, d.id());
            case "server" -> serverOpen(d.id());
            default -> false;
        };
    }

    /** Trang con: các dungeon của một loại. */
    private void typeMenu(Player p, String type) {
        String weekly = weeklyId();
        InfoMenu m = InfoMenu.of("&5⛩ " + TYPE_VI.get(type)).accent(Material.PURPLE_STAINED_GLASS_PANE).back(this::menu);
        m.top(Tip.of("&d" + TYPE_VI.get(type)).accent("&d").desc(TYPE_HOW.getOrDefault(type, ""))
                .apply(new ItemStack(TYPE_ICON.getOrDefault(type, Material.COMPASS))));
        for (Dg d : dungeons.values()) {
            if (d.type().equals(type)) {
                var en = entry(p, d, weekly);
                m.add(en.getKey(), en.getValue());
            }
        }
        m.open(p);
    }

    /** Ô của một dungeon (tooltip + việc khi bấm). */
    private java.util.Map.Entry<ItemStack, java.util.function.Consumer<Player>> entry(Player p, Dg d, String weekly) {
        UUID u = p.getUniqueId();
        boolean inst = !hooked || installed(d.id());
        boolean hiddenUnknown = d.type().equals("hidden") && !found(u, d.id());
        boolean done = switch (d.type()) {
            case "chain" -> cleared(u, d.id()) || freeAccess(u, d);
            case "hidden" -> found(u, d.id());
            case "server" -> serverOpen(d.id());
            case "ticket" -> tickets(u, d.id()) > 0;
            default -> false;
        };
        String state;
        if (!inst) {
            state = "&7chưa cài";
        } else if (!baseOk(d)) {
            state = "&7chờ khai hoang";
        } else {
            state = switch (d.type()) {
                case "chain" -> cleared(u, d.id()) ? "&a✔ đã vượt" : freeAccess(u, d) ? "&e⚿ đã mở"
                    : keySlot(p, d.id()) != null ? "&6⚿ có chìa trong túi" : "&c✖ cần Chìa khoá của trùm " + name(d.requires());
                case "hidden" -> found(u, d.id()) ? "&a✔ đã tìm ra" : "&7chưa tìm ra";
                case "luck" -> "&7Vé Vận Mệnh" + (d.rollChance() < 100 ? ", " + Math.round(d.rollChance()) + "% mở" : "");
                case "ticket" -> "&f" + tickets(u, d.id()) + " &7vé · " + money(d.price()) + "/vé";
                case "gate" -> "&5◎ tìm Cổng Hoang để vào";
                case "server" -> serverOpen(d.id()) ? "&a⛩ đang mở" + until(d.id())
                    : "&7quỹ &f" + money(data.getDouble("quy." + d.id() + ".xu", 0)) + " &7/ " + money(d.goal());
                default -> "";
            };
        }
        Tip t = Tip.of((done ? "&a" : "&f") + (hiddenUnknown ? "???" : d.name()) + (d.id().equals(weekly) ? " &6★" : ""))
                .sub(d.level().isBlank() ? TYPE_VI.get(d.type()) : "cấp " + d.level()).accent("&d")
                .desc(TYPE_VI.get(d.type()), TYPE_HOW.getOrDefault(d.type(), "")).line()
                .kv("Trạng thái", state);
        int mine = combatLevel(p), need = levelOf(d);
        if (mine >= 0 && need > 0 && !hiddenUnknown) {
            t.kv("Cấp chiến đấu", fit(mine, need));
        }
        if (hiddenUnknown && !d.hint().isBlank()) {
            t.kv("Gợi ý", "&7" + d.hint());
        }
        List<String> dk = condText(d);
        if (!dk.isEmpty()) {
            t.gap().head("Điều kiện").items(dk);
        }
        List<String> rec = data.getStringList("kyluc." + d.id());
        if (!rec.isEmpty()) {
            String[] r0 = rec.get(0).split(";", 2);
            t.kv("⏱ Kỷ lục", clock(Long.parseLong(r0[0])) + " &7(" + (r0.length > 1 ? r0[1] : "") + ")");
        }
        if (d.id().equals(weekly)) {
            t.kv("★", "&eDungeon của tuần, vượt được thưởng thêm");
        }
        java.util.function.Consumer<Player> act = null;
        boolean locked = d.type().equals("chain") && !cleared(u, d.id()) && !freeAccess(u, d) && keySlot(p, d.id()) == null;
        if (locked) {
            t.line().deny("Chưa mở: cần Chìa khoá của trùm " + name(d.requires()));
        } else if (inst && baseOk(d) && !d.type().equals("gate") && !hiddenUnknown) {
            t.line();
            if (d.type().equals("ticket") && tickets(u, d.id()) == 0) {
                t.click("Bấm để mua vé (" + money(d.price()) + ")");
                act = pl -> { pl.closeInventory(); pl.performCommand("dungeon mua " + d.id()); };
            } else if (d.type().equals("server") && !serverOpen(d.id())) {
                t.click("Bấm để góp quỹ (gõ số xu)");
                act = pl -> {
                pl.closeInventory();
                pl.sendMessage(c("&5⛩ &7Góp quỹ " + d.name() + ": ").append(c("&e[bấm rồi gõ số xu]")
                        .clickEvent(ClickEvent.suggestCommand("/dungeon gop " + d.id() + " "))));
                };
            } else {
                t.click("Bấm để vào");
                act = pl -> { pl.closeInventory(); pl.performCommand("dungeon vao " + d.id()); };
            }
        }
        ItemStack icon = t.apply(new ItemStack(hiddenUnknown ? Material.GRAY_DYE : dgIcon.getOrDefault(d.id(), TYPE_ICON.getOrDefault(d.type(), Material.COMPASS))));
        if (done || d.id().equals(weekly)) {
            icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return java.util.Map.entry(icon, act == null ? pl -> { } : act);
    }

    private void list(CommandSender s) {
        if (s instanceof Player pl) {
            menu(pl);
            return;
        }
        UUID u = s instanceof Player p ? p.getUniqueId() : null;
        s.sendMessage(c("&5━━ ⛩ CỔNG DUNGEON ━━"));
        s.sendMessage(c("&7Bấm vào tên để vào. Đồ trong dungeon chỉ có ở đây; mỗi boss có thời gian chờ nhận lại đồ."));
        String weekly = weeklyId();
        if (weekly != null) {
            s.sendMessage(c("&6★ Dungeon của tuần: &e" + name(weekly) + (weeklyRewards.isEmpty() || rewards == null ? ""
                    : " &7(vượt được thêm " + String.join(", ", rewards.describe(weeklyRewards)) + ", tối đa " + weeklyMax + " lần/tuần)")));
        }
        String lastType = "";
        int hiddenLeft = 0;
        for (Dg d : dungeons.values()) {
            if (!d.type().equals(lastType)) {
                lastType = d.type();
                s.sendMessage(c("&d" + TYPE_VI.get(d.type()) + ":"));
            }
            boolean inst = !hooked || installed(d.id());
            String lv = d.level().isBlank() ? "" : " &8[cấp " + d.level() + "]";
            String state;
            if (!inst) {
                state = "&7chưa cài";
            } else if (!baseOk(d)) {
                state = "&7chờ khai hoang";
            } else if (u == null) {
                state = "";
            } else {
                state = switch (d.type()) {
                    case "chain" -> cleared(u, d.id()) ? "&a✔ đã vượt" : freeAccess(u, d) ? "&e⚿ đã mở"
                            : s instanceof Player pl && keySlot(pl, d.id()) != null ? "&6⚿ có chìa trong túi, bấm để mở" : "&c✖ cần Chìa khoá (từ trùm " + name(d.requires()) + ")";
                    case "hidden" -> found(u, d.id()) ? "&a✔ đã tìm ra" : "&7??? " + (d.hint().isBlank() ? "" : d.hint());
                    case "luck" -> "&7Vé Vận Mệnh rơi hiếm" + (d.rollChance() < 100 ? ", " + Math.round(d.rollChance()) + "% mở" : "");
                    case "ticket" -> "&7" + money(d.price()) + "/vé · đang có &f" + tickets(u, d.id());
                    case "gate" -> "&5◎ tìm Cổng Hoang để vào";
                    case "server" -> serverOpen(d.id()) ? "&a⛩ đang mở" + until(d.id())
                            : "&7quỹ " + money(data.getDouble("quy." + d.id() + ".xu", 0)) + "/" + money(d.goal());
                    default -> "";
                };
            }
            if (d.type().equals("hidden") && u != null && !found(u, d.id())) {
                hiddenLeft++;
                s.sendMessage(c("  &8• &7??? " + lv + " &8— " + state));
                continue;
            }
            List<String> dk = condText(d);
            String star = d.id().equals(weekly) ? " &6★ tuần này" : "";
            List<String> rec = data.getStringList("kyluc." + d.id());
            String hover = (dk.isEmpty() ? "" : "\n&eĐiều kiện: &f" + String.join(", ", dk))
                    + (rec.isEmpty() ? "" : "\n&b⏱ Kỷ lục: &f" + clock(Long.parseLong(rec.get(0).split(";", 2)[0])) + " &7(" + rec.get(0).split(";", 2)[1] + ")");
            Component line = c("  &8• &f" + d.name() + lv + star + " &8— " + state + (dk.isEmpty() ? "" : " &e⚠"));
            if (inst && !d.type().equals("gate")) {
                String cmd = d.type().equals("ticket") && u != null && tickets(u, d.id()) == 0 ? "/dungeon mua " + d.id()
                        : d.type().equals("server") && !serverOpen(d.id()) ? "/dungeon gop " + d.id() + " " : "/dungeon vao " + d.id();
                line = line.clickEvent(cmd.endsWith(" ") ? ClickEvent.suggestCommand(cmd) : ClickEvent.runCommand(cmd))
                        .hoverEvent(HoverEvent.showText(c("&7" + cmd + hover + "\n&7mã: " + d.id())));
            }
            s.sendMessage(line);
        }
        if (hiddenLeft > 0) {
            s.sendMessage(c("&7Còn &d" + hiddenLeft + " &7dungeon ẩn chưa tìm ra: lối vào giấu đâu đó trên bản đồ, để ý hạt tím lấp lánh."));
        }
        s.sendMessage(c("&7Rê chuột lên tên: điều kiện (&e⚠&8) và kỷ lục tốc độ. Lệnh: /dungeon vao|mua|gop <mã>"));
    }

    private String until(String id) {
        long until = data.getLong("quy." + id + ".mo", 0);
        if (until == -1) {
            return " tới hết mùa";
        }
        long h = Math.max(0, (until - System.currentTimeMillis()) / 3_600_000L);
        return " còn " + (h >= 24 ? h / 24 + " ngày" : h + " giờ");
    }

    private static Component button(String text, String cmd, String hover) {
        return c(text).clickEvent(ClickEvent.runCommand(cmd)).hoverEvent(HoverEvent.showText(c("&7" + hover)));
    }

    private static String money(double v) {
        return VaultEconomy.available() ? VaultEconomy.format(v) : String.format(Locale.ROOT, "%,.0f xu", v);
    }

    // ---------------------------------------------------------------- lưu + web

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được dungeon.yml: " + e.getMessage());
        }
    }

    private void writeJson() {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (Dg d : dungeons.values()) {
            JsonObject o = new JsonObject();
            boolean hidden = d.type().equals("hidden");
            o.addProperty("ma", hidden ? "" : d.id());
            o.addProperty("ten", hidden ? "???" : d.name());
            o.addProperty("kieu", d.type());
            o.addProperty("cap", d.level());
            if (d.type().equals("chain") && !d.requires().isBlank()) {
                o.addProperty("can", name(d.requires()));
            }
            if (d.type().equals("ticket")) {
                o.addProperty("gia", d.price());
            }
            if (d.type().equals("luck")) {
                o.addProperty("ti_le", d.rollChance());
            }
            if (d.type().equals("server")) {
                o.addProperty("quy", data.getDouble("quy." + d.id() + ".xu", 0));
                o.addProperty("muc_tieu", d.goal());
                o.addProperty("mo_den", data.getLong("quy." + d.id() + ".mo", 0));
                o.addProperty("so_ngay", d.openDays());
            }
            if (!hidden && data.contains("dau." + d.id())) {
                o.addProperty("dau_tien", String.join(", ", data.getStringList("dau." + d.id() + ".ten")));
            }
            o.addProperty("luot", data.getInt("dem." + d.id(), 0));
            JsonArray dk = new JsonArray();
            condText(d).forEach(dk::add);
            o.add("dk", dk);
            o.addProperty("tuan", d.id().equals(data.getString("tuan." + weekKey())));
            JsonArray rec = new JsonArray();
            if (!hidden) {
                for (String r : data.getStringList("kyluc." + d.id())) {
                    String[] a = r.split(";", 2);
                    JsonObject x = new JsonObject();
                    x.addProperty("giay", Long.parseLong(a[0]));
                    x.addProperty("ten", a.length > 1 ? a[1] : "");
                    rec.add(x);
                }
            }
            o.add("ky_luc", rec);
            arr.add(o);
        }
        root.add("dungeons", arr);
        JsonObject players = new JsonObject();
        Set<String> ids = new HashSet<>();
        for (String sec : List.of("qua", "tim", "ve")) {
            ConfigurationSection cs = data.getConfigurationSection(sec);
            if (cs != null) {
                ids.addAll(cs.getKeys(false));
            }
        }
        for (String id : ids) {
            JsonObject o = new JsonObject();
            JsonArray q = new JsonArray();
            data.getStringList("qua." + id).forEach(x -> q.add(name(x)));
            o.add("da_vuot", q);
            o.addProperty("tim_an", data.getStringList("tim." + id).size());
            JsonObject ve = new JsonObject();
            ConfigurationSection v = data.getConfigurationSection("ve." + id);
            if (v != null) {
                v.getKeys(false).forEach(k -> ve.addProperty(name(k), v.getInt(k)));
            }
            o.add("ve", ve);
            players.add(id, o);
        }
        root.add("nguoi", players);
        try {
            Files.writeString(new File(plugin.getDataFolder(), "dungeon.json").toPath(), root.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Không ghi được dungeon.json: " + e.getMessage());
        }
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
}
