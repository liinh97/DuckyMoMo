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
 *  - chain:  chuỗi cấp. requires: <mã> = phải vượt dungeon đó trước (nhận "chìa khoá"); không requires = mở sẵn.
 *  - hidden: dungeon ẩn. Lối vào giấu trên bản đồ (admin /dungeon datan <mã>); đi tới gần (hidden-radius khối) là tìm ra, giữ cả mùa.
 *            night-only: true = chỉ lộ ra ban đêm. hint: gợi ý hiện trong /dungeon.
 *  - luck:   may mắn. Vé Vận Mệnh rơi hiếm khi hạ quái / câu cá (luck.*); dùng vé có roll-chance % cổng mở (trượt thì mất vé).
 *  - ticket: vé cá nhân mua bằng xu (price/lượt, tối đa max-tickets vé trong túi).
 *  - server: cả server góp quỹ (goal xu); đủ thì mở cho mọi người open-days ngày (0 = tới hết mùa), hết hạn thì góp lại.
 *  Chung: name, level (nhãn cấp), requires-frontier (mốc khai hoang phải mở), first-rewards (quà lần đầu vượt, mỗi người một lần).
 * Vé (ticket/luck) chỉ trừ khi thật sự vào: /dungeon vao mở cửa window-seconds giây, không vào kịp thì hoàn vé.
 * Lưu: dungeon.yml; ghi dungeon.json cho web.
 */
final class DungeonGate implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    static final String PERM = "duckymomo.dungeon.";
    static final Map<String, String> TYPE_VI = Map.of("chain", "Chuỗi chìa khoá", "hidden", "Dungeon ẩn", "luck", "May mắn",
            "ticket", "Vé cá nhân", "server", "Mở chung cả server");

    private record Dg(String id, String name, String type, String level, String requires, String frontier, String hint, boolean nightOnly,
                      double rollChance, double price, int maxTickets, double goal, int openDays, List<String> firstRewards) { }

    private record Pass(String id, long until) { }

    private final DuckyMoMoCore plugin;
    private final Rewards rewards;
    private final Map<String, Dg> dungeons = new LinkedHashMap<>();
    private final long windowMs;
    private final int hiddenRadius;
    private final double luckMob;
    private final double luckFish;
    private final int luckPerDay;
    private final NamespacedKey ticketKey;
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
        ConfigurationSection ds = c.getConfigurationSection("dungeons");
        if (ds != null) {
            for (String id : ds.getKeys(false)) {
                ConfigurationSection d = ds.getConfigurationSection(id);
                if (d == null) {
                    continue;
                }
                String type = d.getString("type", "chain").toLowerCase(Locale.ROOT);
                if (!TYPE_VI.containsKey(type)) {
                    plugin.getLogger().warning("Cổng Dungeon: kiểu lạ '" + type + "' ở " + id + " (chain|hidden|luck|ticket|server)");
                    continue;
                }
                dungeons.put(id, new Dg(id, d.getString("name", id), type, d.getString("level", ""), d.getString("requires", ""),
                        d.getString("requires-frontier", ""), d.getString("hint", ""), d.getBoolean("night-only", false),
                        Math.max(1, Math.min(100, d.getDouble("roll-chance", 100))), Math.max(0, d.getDouble("price", 0)),
                        Math.max(1, d.getInt("max-tickets", 5)), Math.max(1, d.getDouble("goal", 100000)), Math.max(0, d.getInt("open-days", 7)),
                        d.getStringList("first-rewards")));
            }
        }
        this.ticketKey = new NamespacedKey(plugin, "dmm_vevanmenh");
        this.file = new File(plugin.getDataFolder(), "dungeon.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
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
        Bukkit.getScheduler().runTaskLater(plugin, this::hook, 100L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 120L, 100L);
    }

    void stop() {
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
            refund(e.getKey(), e.getValue().id());
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
            applyPermissions();
            Bukkit.getOnlinePlayers().forEach(this::refresh);
            writeJson();
            plugin.getLogger().info("Cổng Dungeon: đã gắn EliteMobs (" + installedCount() + "/" + dungeons.size() + " gói đã cài).");
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Cổng Dungeon: không gắn được EliteMobs: " + e);
        }
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
            case "chain" -> d.requires().isBlank() || cleared(u, d.requires());
            case "hidden" -> found(u, d.id());
            case "server" -> serverOpen(d.id());
            default -> false;
        };
    }

    private void refresh(Player p) {
        UUID u = p.getUniqueId();
        Set<String> allow = new HashSet<>();
        for (Dg d : dungeons.values()) {
            if (freeAccess(u, d)) {
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
        if (a == null) {
            a = p.addAttachment(plugin);
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
        long now = System.currentTimeMillis();
        boolean dirty = false;
        // hết giờ chờ mà chưa vào: hoàn vé
        for (var it = pending.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (e.getValue().until() < now) {
                it.remove();
                refund(e.getKey(), e.getValue().id());
                dirty = true;
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null) {
                    p.sendMessage(c("&7⌛ Cổng &f" + name(e.getValue().id()) + " &7đã đóng (chưa vào). Vé đã được hoàn lại."));
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
                Bukkit.broadcast(c("&5⛩ Cổng &d" + d.name() + " &5đã đóng lại. Góp quỹ để mở lần sau: &f/dungeon gop " + d.id() + " <xu>"));
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
                    p.sendMessage(c("&5&l⛩ Bạn đã tìm ra dungeon ẩn: &d" + d.name() + "&5! &7Vào bất cứ lúc nào trong mùa này: "));
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
            Pass pass = pending.get(p.getUniqueId());
            if (pass != null && pass.id().equals(id)) {
                pending.remove(p.getUniqueId());   // vé đã dùng (đã trừ lúc mở cổng)
            }
            refresh(p);
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
            Bukkit.broadcast(c("&6&l🏆 ĐẦU TIÊN! &e" + String.join(", ", names) + " &6là người đầu tiên của server vượt &f" + d.name() + "&6!"));
        }
        data.set("dem." + id, data.getInt("dem." + id, 0) + 1);
        for (Player p : ps) {
            UUID u = p.getUniqueId();
            Achievements.add(p, "dungeon", 1);
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
            for (Dg next : dungeons.values()) {
                if (next.type().equals("chain") && id.equals(next.requires())) {
                    p.sendMessage(c("&6🗝 Bạn nhận được &eChìa khoá " + next.name() + "&6. Tầng tiếp theo đã mở: "));
                    p.sendMessage(button("&e[Vào " + next.name() + "]", "/dungeon vao " + next.id(), "Mở cổng " + next.name()));
                }
            }
            refresh(p);
        }
        save();
        writeJson();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (e.getPlayer().isOnline()) {
                refresh(e.getPlayer());
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
        m.displayName(c("&d&l✦ Vé Vận Mệnh &7(" + d.name() + "&7)"));
        m.lore(List.of(c("&7Mở cổng dungeon may mắn &f" + d.name()), c("&7Dùng: &f/dungeon vao " + d.id()),
                c(d.rollChance() < 100 ? "&c" + Math.round(d.rollChance()) + "% cổng mở, trượt thì vé tan biến" : "&aChắc chắn mở cổng")));
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
        Bukkit.broadcast(c("&d✦ " + p.getName() + " &5vừa nhặt được &dVé Vận Mệnh &5(" + d.name() + ")&5! Cực hiếm!"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k != null && e.getEntity() instanceof Monster && inside.get(k.getUniqueId()) == null
                && ThreadLocalRandom.current().nextDouble(100) < luckMob) {
            maybeLuck(k);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH && ThreadLocalRandom.current().nextDouble(100) < luckFish) {
            maybeLuck(e.getPlayer());
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
                if (!s.hasPermission("duckymomo.admin") || !(s instanceof Player p) || d == null || !d.type().equals("hidden")) {
                    s.sendMessage("/dungeon datan <mã dungeon ẩn> (đứng tại chỗ muốn giấu lối vào)");
                    return true;
                }
                Location l = p.getLocation();
                data.set("an." + d.id(), l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ());
                save();
                p.sendMessage(c("&a✔ Đã giấu lối vào &f" + d.name() + " &atại " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()
                        + " &7(người chơi tới trong " + hiddenRadius + " khối là tìm ra" + (d.nightOnly() ? ", chỉ ban đêm" : "") + ")"));
                return true;
            }
            case "cho" -> {
                if (!s.hasPermission("duckymomo.admin") || args.length < 3) {
                    s.sendMessage("/dungeon cho <mã> <người> [số]  (vé cá nhân hoặc Vé Vận Mệnh)");
                    return true;
                }
                Player t = Bukkit.getPlayerExact(args[2]);
                if (d == null || t == null) {
                    s.sendMessage("Sai mã dungeon hoặc người chơi offline.");
                    return true;
                }
                int n = args.length > 3 ? Math.max(1, Integer.parseInt(args[3])) : 1;
                if (d.type().equals("luck")) {
                    for (int i = 0; i < n; i++) {
                        Rewards.hand(t, luckTicket(d));
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
            Bukkit.broadcast(c("&5&l⛩ CỔNG ĐÃ MỞ! &dQuỹ chung đã đủ, &f" + d.name() + " &dmở cho cả server "
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
        if (inside.containsKey(u)) {
            p.sendMessage(c("&cBạn đang ở trong một dungeon."));
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
        if (freeAccess(u, d)) {
            open(p, d);
            return;
        }
        switch (d.type()) {
            case "chain" -> p.sendMessage(c("&cCần &eChìa khoá " + d.name() + "&c: vượt &f" + name(d.requires()) + " &ctrước."));
            case "hidden" -> p.sendMessage(c("&cBạn chưa tìm ra lối vào dungeon ẩn này." + (d.hint().isBlank() ? "" : " &7Gợi ý: &f" + d.hint())));
            case "server" -> p.sendMessage(c("&cCổng đang đóng. Cả server góp quỹ để mở: &f/dungeon gop " + d.id() + " <xu> &7(còn thiếu "
                    + money(d.goal() - data.getDouble("quy." + d.id() + ".xu", 0)) + ")"));
            default -> {
                if (tickets(u, d.id()) <= 0 && !(d.type().equals("luck") && takeLuckTicket(p, d.id()))) {
                    p.sendMessage(c(d.type().equals("luck") ? "&cCần &dVé Vận Mệnh &c(" + d.name() + "&c): rơi rất hiếm khi hạ quái hoặc câu cá."
                            : "&cCần vé. Mua: &f/dungeon mua " + d.id() + " &7(" + money(d.price()) + "/vé)"));
                    return;
                }
                if (tickets(u, d.id()) > 0) {
                    addTickets(u, d.id(), -1);
                } else if (ThreadLocalRandom.current().nextDouble(100) >= d.rollChance()) {
                    // vé vận mệnh vừa lấy từ túi: tung xúc xắc
                    p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.6f);
                    p.sendMessage(c("&5✦ Vận mệnh không mỉm cười… Vé tan biến &7(" + Math.round(d.rollChance()) + "% cổng mở)."));
                    return;
                }
                save();
                pending.put(u, new Pass(d.id(), System.currentTimeMillis() + windowMs));
                p.sendMessage(c("&a⛩ Cổng &f" + d.name() + " &ađã mở cho bạn trong " + windowMs / 1000
                        + " giây (đã dùng 1 vé; không vào kịp thì được hoàn vé)."));
                open(p, d);
            }
        }
    }

    /** Cấp quyền rồi mở menu chọn độ khó của EliteMobs. */
    private void open(Player p, Dg d) {
        refresh(p);
        p.playSound(p.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 0.8f);
        p.performCommand("em dungeontp " + d.id() + ".yml");
    }

    private void list(CommandSender s) {
        UUID u = s instanceof Player p ? p.getUniqueId() : null;
        s.sendMessage(c("&5&l━━ ⛩ CỔNG DUNGEON ━━"));
        s.sendMessage(c("&7Bấm vào tên để vào. Đồ trong dungeon chỉ có ở đây; mỗi boss có thời gian chờ nhận lại đồ."));
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
                state = "&8chưa cài";
            } else if (!baseOk(d)) {
                state = "&8chờ khai hoang";
            } else if (u == null) {
                state = "";
            } else {
                state = switch (d.type()) {
                    case "chain" -> cleared(u, d.id()) ? "&a✔ đã vượt" : freeAccess(u, d) ? "&e🗝 đã có chìa" : "&c🔒 cần vượt " + name(d.requires());
                    case "hidden" -> found(u, d.id()) ? "&a✔ đã tìm ra" : "&7??? " + (d.hint().isBlank() ? "" : d.hint());
                    case "luck" -> "&7Vé Vận Mệnh rơi hiếm" + (d.rollChance() < 100 ? ", " + Math.round(d.rollChance()) + "% mở" : "");
                    case "ticket" -> "&7" + money(d.price()) + "/vé · đang có &f" + tickets(u, d.id());
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
            Component line = c("  &8• &f" + d.name() + lv + " &8— " + state);
            if (inst) {
                String cmd = d.type().equals("ticket") && u != null && tickets(u, d.id()) == 0 ? "/dungeon mua " + d.id()
                        : d.type().equals("server") && !serverOpen(d.id()) ? "/dungeon gop " + d.id() + " " : "/dungeon vao " + d.id();
                line = line.clickEvent(cmd.endsWith(" ") ? ClickEvent.suggestCommand(cmd) : ClickEvent.runCommand(cmd))
                        .hoverEvent(HoverEvent.showText(c("&7" + cmd + "\n&8mã: " + d.id())));
            }
            s.sendMessage(line);
        }
        if (hiddenLeft > 0) {
            s.sendMessage(c("&7Còn &d" + hiddenLeft + " &7dungeon ẩn chưa tìm ra: lối vào giấu đâu đó trên bản đồ, để ý hạt tím lấp lánh."));
        }
        s.sendMessage(c("&8Lệnh: /dungeon vao|mua|gop <mã>"));
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
