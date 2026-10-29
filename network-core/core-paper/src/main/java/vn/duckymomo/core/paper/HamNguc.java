package vn.duckymomo.core.paper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Hầm Ngục DuckyMoMo /hamnguc (29/10, chủ server: người mới vào dungeon EliteMobs thấy quái elite là thoát; muốn dungeon tự xây,
 * quái rơi thẳng món set thay vì nguyên liệu trao đổi). Chạy SONG SONG với dungeon EliteMobs (/dungeon vẫn còn).
 *  - Thế giới riêng (mặc định "hamnguc", trống, giữ đồ khi chết). Mỗi lượt vào dựng một bản riêng ở một ô (slot): chuỗi phòng nối bằng hành lang,
 *    phòng 0 an toàn, bước vào phòng là quái ra, hạ hết thì song sắt cửa ra mở; phòng giữa có phó trùm 〖〗, phòng cuối trùm 『』 (thanh máu).
 *  - Quái vanilla đặt tên tiếng Việt, máu / sát thương theo config (không dùng cấp chiến đấu EliteMobs); nhóm đông thì quái nhiều máu hơn.
 *  - Rơi đồ: chỉ món +0 (mã đầu tiên trong tho-ren.sets.<set>.pieces.<món>.ids) của set gắn với dungeon, vào túi người hạ; trùm: mỗi người
 *    trong lượt quay riêng. Nâng cấp tiếp ở Thợ Rèn như cũ.
 *  - Mở khoá theo chuỗi (requires), đi nhóm bằng /hamnguc moi <tên>, thoát /hamnguc thoat. Lưu: hamnguc.yml (lượt vượt, chỗ về khi thoát game).
 */
final class HamNguc implements Listener {

    private static final int ROOM = 17;          // ruột phòng 17 x 17
    private static final int CORRIDOR = 7;       // hành lang dài 7
    private static final int STEP = ROOM + 2 + CORRIDOR;
    private static final int FLOOR_Y = -60;      // thế giới phẳng: mặt cỏ ở -61, hầm đặt ngay trên
    private static final int SLOT_GAP = 512;
    private static final int SLOTS = 64;

    private final JavaPlugin plugin;
    private final ConfigurationSection cfg;
    private final String worldName;
    private final NamespacedKey kRun;
    private final NamespacedKey kRole;
    private final NamespacedKey kDmg;
    private final File file;
    private final YamlConfiguration data;
    private final Map<String, Dg> dungeons = new LinkedHashMap<>();
    private final Map<Integer, Run> runs = new HashMap<>();
    private final Map<UUID, Run> byPlayer = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> invites = new HashMap<>();   // người được mời -> (trưởng nhóm -> hạn)
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Set<UUID> allowTp = new HashSet<>();
    private boolean skillHit;
    private int nextId = 1;
    private BukkitTask ticker;

    private record MobDef(EntityType type, String name, double hp, double dmg, double scale) { }

    private record Dg(String id, String name, String theme, String set, Material icon, String requires, int rooms, String hint,
            List<MobDef> mobs, MobDef mini, MobDef boss, double dropMult, double reward, double firstReward) { }

    private static final class Run {
        final int id;
        final int slot;
        final Dg dg;
        final UUID leader;
        final Set<UUID> members = new HashSet<>();
        final Map<UUID, Location> back = new HashMap<>();
        final Set<UUID> everyone = new HashSet<>();   // ai từng vào lượt này (chia thưởng trùm, thưởng vượt)
        final boolean[] started;
        final boolean[] cleared;
        final List<List<UUID>> roomMobs = new ArrayList<>();
        final List<List<int[]>> cages = new ArrayList<>();                      // lồng quái mỗi phòng {x, z}
        final List<java.util.ArrayDeque<List<MobDef>>> waves = new ArrayList<>(); // đợt chưa ra
        final int[] spawning;                                                     // quái đang chui ra khỏi lồng
        final long startAt = System.currentTimeMillis();
        long emptySince;
        long doneAt;
        UUID boss;
        BossBar bar;
        int skillTick;
        boolean ready;

        Run(int id, int slot, Dg dg, UUID leader) {
            this.id = id;
            this.slot = slot;
            this.dg = dg;
            this.leader = leader;
            this.started = new boolean[dg.rooms()];
            this.cleared = new boolean[dg.rooms()];
            this.spawning = new int[dg.rooms()];
            for (int i = 0; i < dg.rooms(); i++) {
                roomMobs.add(new ArrayList<>());
                cages.add(new ArrayList<>());
                waves.add(new java.util.ArrayDeque<>());
            }
            started[0] = true;
            cleared[0] = true;
        }

        int baseX() {
            return slot * SLOT_GAP;
        }

        int roomX(int i) {   // x của tường trái phòng i
            return baseX() + i * STEP;
        }
    }

    HamNguc(JavaPlugin plugin, ConfigurationSection cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.worldName = cfg.getString("world", "hamnguc");
        this.kRun = new NamespacedKey(plugin, "hn_run");
        this.kRole = new NamespacedKey(plugin, "hn_vai");
        this.kDmg = new NamespacedKey(plugin, "hn_dmg");
        this.file = new File(plugin.getDataFolder(), "hamnguc.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection ds = cfg.getConfigurationSection("dungeons");
        if (ds != null) {
            for (String id : ds.getKeys(false)) {
                ConfigurationSection d = ds.getConfigurationSection(id);
                if (d == null) {
                    continue;
                }
                List<MobDef> mobs = new ArrayList<>();
                for (String m : d.getStringList("mobs")) {
                    MobDef md = parseMob(m, d.getDouble("hp", 30), d.getDouble("dmg", 4), 1.0);
                    if (md != null) {
                        mobs.add(md);
                    }
                }
                MobDef mini = mobOf(d.getConfigurationSection("mini"));
                MobDef boss = mobOf(d.getConfigurationSection("boss"));
                Material icon = Material.matchMaterial(d.getString("icon", "IRON_BARS"));
                if (mobs.isEmpty() || boss == null) {
                    plugin.getLogger().warning("Hầm Ngục: " + id + " thiếu mobs hoặc boss, bỏ qua");
                    continue;
                }
                dungeons.put(id, new Dg(id, d.getString("name", id), d.getString("theme", "hammo"), d.getString("set", "tanthu"),
                        icon == null ? Material.IRON_BARS : icon, d.getString("requires", ""), Math.max(3, Math.min(8, d.getInt("rooms", 5))),
                        d.getString("hint", ""), mobs, mini, boss, d.getDouble("drop-mult", 1.0), d.getDouble("reward", 0), d.getDouble("first-reward", 0)));
            }
        }
    }

    private static MobDef parseMob(String spec, double hp, double dmg, double scale) {
        String[] p = spec.split(":", 2);
        try {
            EntityType t = EntityType.valueOf(p[0].trim().toUpperCase(Locale.ROOT));
            return new MobDef(t, p.length > 1 ? p[1].trim() : t.name(), hp, dmg, scale);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static MobDef mobOf(ConfigurationSection s) {
        if (s == null) {
            return null;
        }
        MobDef m = parseMob(s.getString("type", "ZOMBIE") + ":" + s.getString("name", "Trùm"), s.getDouble("hp", 200), s.getDouble("dmg", 8),
                s.getDouble("scale", 1.0));
        return m;
    }

    String describe() {
        return dungeons.size() + " hầm ngục (" + String.join(", ", dungeons.keySet()) + "), thế giới " + worldName;
    }

    void start() {
        ensureWorld();
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    void shutdown() {
        if (ticker != null) {
            ticker.cancel();
        }
        for (Run r : new ArrayList<>(runs.values())) {
            end(r, "&7Server đang khởi động lại, đưa bạn ra khỏi hầm ngục.");
        }
        save();
    }

    // ------------------------------------------------------------------ thế giới

    private World ensureWorld() {
        World w = Bukkit.getWorld(worldName);
        if (w == null) {
            w = new WorldCreator(worldName).type(org.bukkit.WorldType.FLAT).generateStructures(false).createWorld();
            if (w != null) {
                w.setSpawnLocation(0, FLOOR_Y + 1, 0);
                w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                w.setGameRule(GameRule.KEEP_INVENTORY, true);
                w.setGameRule(GameRule.MOB_GRIEFING, false);
                w.setGameRule(GameRule.DO_FIRE_TICK, false);
                w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
                w.setTime(18000);
            }
        }
        return w;
    }

    private boolean inWorld(World w) {
        return w != null && w.getName().equals(worldName);
    }

    // ------------------------------------------------------------------ lệnh

    boolean command(CommandSender s, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) {
            if (!s.hasPermission("duckymomo.admin")) {
                s.sendMessage(Tip.c("&cChỉ dành cho Admin."));
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("xoa")) {
                int n = runs.size();
                for (Run r : new ArrayList<>(runs.values())) {
                    end(r, "&7Admin đã đóng các lượt hầm ngục.");
                }
                s.sendMessage(Tip.c("&aĐã đóng " + n + " lượt."));
            } else if (args.length >= 3 && args[1].equalsIgnoreCase("mo")) {   // admin mo <người> <dungeon>: ghi đã vượt (thử / bù)
                org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(args[2]);
                if (args.length >= 4 && dungeons.containsKey(args[3])) {
                    data.set("players." + op.getUniqueId() + "." + args[3], Math.max(1, clears(op.getUniqueId(), args[3])));
                    save();
                    s.sendMessage(Tip.c("&aĐã ghi " + args[2] + " vượt " + args[3]));
                }
            } else {
                s.sendMessage(Tip.c("&7Đang chạy: &f" + runs.size() + " lượt &7· " + describe()));
                for (Run r : runs.values()) {
                    s.sendMessage(Tip.c("&8- &f#" + r.id + " " + r.dg.name() + " &7slot " + r.slot + " · " + r.members.size() + " người"));
                }
            }
            return true;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("Lệnh này phải gõ trong game.");
            return true;
        }
        switch (sub) {
            case "thoat", "roi", "leave" -> leave(p, true);
            case "moi", "invite" -> invite(p, args.length >= 2 ? args[1] : "");
            case "vao", "join" -> joinOther(p, args.length >= 2 ? args[1] : "");
            case "di" -> {
                Dg d = args.length >= 2 ? dungeons.get(args[1].toLowerCase(Locale.ROOT)) : null;
                if (d != null) {
                    begin(p, d);
                } else {
                    menu(p);
                }
            }
            default -> menu(p);
        }
        return true;
    }

    private int clears(UUID u, String dg) {
        return data.getInt("players." + u + "." + dg, 0);
    }

    private boolean unlocked(UUID u, Dg d) {
        return d.requires().isBlank() || !dungeons.containsKey(d.requires()) || clears(u, d.requires()) > 0;
    }

    private String setName(String set) {
        return plugin.getConfig().getString("tho-ren.sets." + set + ".name", set);
    }

    void menu(Player p) {
        UUID u = p.getUniqueId();
        InfoMenu m = InfoMenu.of("&6⚔ Hầm Ngục DuckyMoMo").accent(Material.ORANGE_STAINED_GLASS_PANE);
        Tip top = Tip.of("&6⚔ &fHầm Ngục DuckyMoMo").accent("&6")
                .desc("Hầm ngục của server: đi qua từng phòng, hạ hết quái thì cửa mở.", "Phòng giữa có phó trùm, phòng cuối có trùm.")
                .line()
                .kv("Rơi đồ", "&fmón set &e+0 &7vào thẳng túi")
                .kv("Quái thường", "mũ, giày")
                .kv("Phó trùm", "thêm quần, giáp")
                .kv("Trùm", "thêm vũ khí, mỗi người quay riêng")
                .line()
                .note("Chết không mất đồ, sống lại ở phòng đầu. Nâng món +0 ở Thợ Rèn.")
                .note("Đi nhóm: vào trước rồi /hamnguc moi <tên> (tối đa " + cfg.getInt("max-party", 4) + " người). Thoát: /hamnguc thoat");
        m.top(top.apply(new ItemStack(Material.IRON_BARS)));
        for (Dg d : dungeons.values()) {
            boolean open = unlocked(u, d);
            int n = clears(u, d.id());
            Tip t = Tip.of((open ? "&6" : "&8") + d.name()).accent("&6").sub("Set " + setName(d.set()))
                    .kv("Rơi", "&f" + setName(d.set()) + " +0")
                    .kv("Số phòng", String.valueOf(d.rooms()))
                    .kv("Trùm", "&c" + strip(d.boss().name()));
            if (!d.hint().isBlank()) {
                t.kv("Nên có", "&f" + d.hint());
            }
            if (d.reward() > 0) {
                t.kv("Thưởng vượt", "&e" + VaultEconomy.format(d.reward()) + (n == 0 && d.firstReward() > 0 ? " &7(lần đầu thêm &e"
                        + VaultEconomy.format(d.firstReward()) + "&7)" : ""));
            }
            t.kv("Đã vượt", n + " lần").line();
            if (!open) {
                t.deny("Vượt " + dungeons.get(d.requires()).name() + " trước");
            } else {
                t.click("Bấm để vào");
            }
            ItemStack icon = t.apply(new ItemStack(open ? d.icon() : Material.GRAY_DYE));
            if (n > 0) {
                icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            }
            if (open) {
                m.add(icon, pl -> {
                    pl.closeInventory();
                    begin(pl, d);
                });
            } else {
                m.add(icon);
            }
        }
        m.open(p);
    }

    private static String strip(String s) {
        return s.replaceAll("&[0-9a-fk-or]", "");
    }

    // ------------------------------------------------------------------ vào, mời, thoát

    private void begin(Player p, Dg d) {
        UUID u = p.getUniqueId();
        if (byPlayer.containsKey(u)) {
            p.sendMessage(Tip.c("&cBạn đang ở trong hầm ngục. Gõ &f/hamnguc thoat &cđể ra trước."));
            return;
        }
        if (!unlocked(u, d)) {
            p.sendMessage(Tip.c("&cCần vượt &f" + dungeons.get(d.requires()).name() + " &ctrước."));
            return;
        }
        if (inWorld(p.getWorld())) {
            return;
        }
        long cd = cooldown.getOrDefault(u, 0L) - System.currentTimeMillis();
        if (cd > 0) {
            p.sendMessage(Tip.c("&cChờ thêm &f" + (cd / 1000 + 1) + " giây &cnữa mới vào lượt mới."));
            return;
        }
        Integer slot = freeSlot();
        World w = ensureWorld();
        if (slot == null || w == null) {
            p.sendMessage(Tip.c("&cHầm ngục đang đầy, thử lại sau ít phút."));
            return;
        }
        Run r = new Run(nextId++, slot, d, u);
        runs.put(slot, r);
        cooldown.put(u, System.currentTimeMillis() + cfg.getLong("cooldown-seconds", 120) * 1000L);
        p.sendMessage(Tip.c("&6⚔ &fĐang dựng &6" + d.name() + "&f…"));
        build(w, r, () -> {
            r.ready = true;
            if (p.isOnline() && !byPlayer.containsKey(u)) {
                enter(p, r);
                p.sendMessage(Tip.c("&6⚔ &f" + d.name() + ": &7đi vào phòng kế tiếp là quái xuất hiện. Rủ bạn: &f/hamnguc moi <tên>&7, thoát: &f/hamnguc thoat"));
            }
        });
    }

    private Integer freeSlot() {
        for (int i = 0; i < SLOTS; i++) {
            if (!runs.containsKey(i)) {
                return i;
            }
        }
        return null;
    }

    private Location entrance(Run r) {
        World w = Bukkit.getWorld(worldName);
        return new Location(w, r.roomX(0) + 5.5, FLOOR_Y + 1, 0.5, -90f, 0f);
    }

    private void enter(Player p, Run r) {
        r.back.put(p.getUniqueId(), p.getLocation());
        data.set("back." + p.getUniqueId(), serialize(p.getLocation()));
        save();
        r.members.add(p.getUniqueId());
        r.everyone.add(p.getUniqueId());
        byPlayer.put(p.getUniqueId(), r);
        allowTp.add(p.getUniqueId());
        p.teleport(entrance(r));
        allowTp.remove(p.getUniqueId());
        if (r.bar != null) {
            r.bar.addPlayer(p);
        }
        p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1f, 0.7f);
    }

    private void invite(Player p, String name) {
        Run r = byPlayer.get(p.getUniqueId());
        if (r == null) {
            p.sendMessage(Tip.c("&cVào hầm ngục trước (&f/hamnguc&c), rồi mới mời bạn."));
            return;
        }
        Player t = Bukkit.getPlayerExact(name);
        if (t == null || t.equals(p)) {
            p.sendMessage(Tip.c("&cKhông thấy người chơi &f" + name + " &cđang online."));
            return;
        }
        if (r.members.size() >= cfg.getInt("max-party", 4)) {
            p.sendMessage(Tip.c("&cNhóm đã đủ người."));
            return;
        }
        invites.computeIfAbsent(t.getUniqueId(), k -> new HashMap<>()).put(p.getUniqueId(), System.currentTimeMillis() + 120_000L);
        p.sendMessage(Tip.c("&aĐã mời &f" + t.getName() + "&a vào " + r.dg.name() + " (2 phút)."));
        t.sendMessage(Tip.c("&6⚔ &f" + p.getName() + " &7mời bạn vào &6" + r.dg.name() + "&7. ")
                .append(Tip.c("&a&l[VÀO CÙNG]").clickEvent(ClickEvent.runCommand("/hamnguc vao " + p.getName()))
                        .hoverEvent(HoverEvent.showText(Tip.c("&7Bấm để vào hầm ngục cùng " + p.getName())))));
    }

    private void joinOther(Player p, String leaderName) {
        Player l = Bukkit.getPlayerExact(leaderName);
        Map<UUID, Long> mine = invites.get(p.getUniqueId());
        Long until = l == null || mine == null ? null : mine.get(l.getUniqueId());
        if (until == null || until < System.currentTimeMillis()) {
            p.sendMessage(Tip.c("&cBạn không có lời mời còn hạn từ &f" + leaderName + "&c."));
            return;
        }
        Run r = byPlayer.get(l.getUniqueId());
        if (r == null || r.doneAt > 0) {
            p.sendMessage(Tip.c("&c" + leaderName + " không còn ở trong hầm ngục."));
            return;
        }
        if (byPlayer.containsKey(p.getUniqueId())) {
            p.sendMessage(Tip.c("&cBạn đang ở trong hầm ngục khác."));
            return;
        }
        if (r.members.size() >= cfg.getInt("max-party", 4)) {
            p.sendMessage(Tip.c("&cNhóm đã đủ người."));
            return;
        }
        mine.remove(l.getUniqueId());
        enter(p, r);
        for (UUID m : r.members) {
            Player o = Bukkit.getPlayer(m);
            if (o != null) {
                o.sendMessage(Tip.c("&a+ &f" + p.getName() + " &7đã vào " + r.dg.name() + "."));
            }
        }
    }

    /** Ra khỏi lượt: đưa về chỗ cũ (teleport=true) hoặc chỉ gỡ khỏi lượt (đã tự đi chỗ khác). */
    private void leave(Player p, boolean teleport) {
        Run r = byPlayer.remove(p.getUniqueId());
        if (r == null) {
            if (teleport) {
                p.sendMessage(Tip.c("&7Bạn không ở trong hầm ngục nào."));
            }
            return;
        }
        r.members.remove(p.getUniqueId());
        if (r.bar != null) {
            r.bar.removePlayer(p);
        }
        Location back = r.back.remove(p.getUniqueId());
        data.set("back." + p.getUniqueId(), null);
        save();
        if (teleport) {
            sendBack(p, back);
        }
        if (r.members.isEmpty() && r.emptySince == 0) {
            r.emptySince = System.currentTimeMillis();
        }
    }

    private void sendBack(Player p, Location back) {
        if (back != null && back.getWorld() != null && !inWorld(back.getWorld())) {
            p.teleport(back);
        } else {
            p.performCommand("spawn");
        }
    }

    private static String serialize(Location l) {
        return l.getWorld().getName() + "," + l.getX() + "," + l.getY() + "," + l.getZ() + "," + l.getYaw() + "," + l.getPitch();
    }

    private static Location parse(String v) {
        if (v == null) {
            return null;
        }
        String[] p = v.split(",");
        World w = p.length >= 6 ? Bukkit.getWorld(p[0]) : null;
        return w == null ? null : new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                Float.parseFloat(p[4]), Float.parseFloat(p[5]));
    }

    /** Đóng lượt: trả mọi người về, xoá quái, giải phóng ô. */
    private void end(Run r, String msg) {
        for (UUID m : new ArrayList<>(r.members)) {
            Player p = Bukkit.getPlayer(m);
            if (p != null) {
                p.sendMessage(Tip.c(msg));
                leave(p, true);
            }
        }
        if (r.bar != null) {
            r.bar.removeAll();
        }
        World w = Bukkit.getWorld(worldName);
        if (w != null) {
            for (Entity e : w.getEntities()) {
                Integer id = e.getPersistentDataContainer().get(kRun, PersistentDataType.INTEGER);
                if (id != null && id == r.id || e.getType() == EntityType.VEX && near(e, r) || e instanceof org.bukkit.entity.Item && near(e, r)) {
                    e.remove();
                }
            }
        }
        runs.remove(r.slot);
        waveTotal.keySet().removeIf(k -> k / 16 == r.id);
    }

    private boolean near(Entity e, Run r) {
        double x = e.getLocation().getX() - r.baseX();
        return x > -20 && x < r.dg.rooms() * STEP + 20;
    }

    // ------------------------------------------------------------------ dựng hầm ngục

    /**
     * Bộ khối theo chủ đề. floor: sàn trộn; accent: viền sàn, bệ; path: lối giữa; base: hàng chân tường; wall: thân tường trộn; trim: hàng đỉnh
     * tường, mũ cột, vòm; ceil: trần; pillar: cột, trụ ốp tường; lantern/torch/fire: đèn treo, đuốc tường (thiếu facing), lò lửa; deco: góc tường.
     */
    private record Theme(Material[] floor, Material accent, Material path, Material base, Material[] wall, Material trim, Material[] ceil,
            Material pillar, String lantern, String torch, String fire, Material[] deco, Material gate, String sign,
            String banner, String hang, Material[] rubble, Material slab) { }

    private static Theme theme(String id) {
        return switch (id) {
            case "mo" -> new Theme(
                    new Material[] {Material.COBBLED_DEEPSLATE, Material.TUFF, Material.DEEPSLATE, Material.PACKED_MUD},
                    Material.POLISHED_DEEPSLATE, Material.SPRUCE_PLANKS, Material.DEEPSLATE_BRICKS,
                    new Material[] {Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.TUFF, Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_IRON_ORE,
                            Material.DEEPSLATE_COPPER_ORE},
                    Material.STRIPPED_SPRUCE_LOG, new Material[] {Material.DEEPSLATE_TILES, Material.TUFF_BRICKS},
                    Material.SPRUCE_LOG, "minecraft:lantern[hanging=true]", "minecraft:wall_torch", "minecraft:campfire[lit=true]",
                    new Material[] {Material.COBWEB, Material.BARREL, Material.COBBLESTONE_WALL, Material.BROWN_MUSHROOM, Material.POINTED_DRIPSTONE},
                    Material.IRON_BARS, "minecraft:spruce_wall_sign", "minecraft:brown_wall_banner",
                    "minecraft:pointed_dripstone[vertical_direction=down,thickness=tip]",
                    new Material[] {Material.COBBLESTONE, Material.GRAVEL, Material.COAL_BLOCK, Material.TUFF_SLAB}, Material.COBBLED_DEEPSLATE_SLAB);
            case "thanhco" -> new Theme(
                    new Material[] {Material.NETHER_BRICKS, Material.RED_NETHER_BRICKS, Material.POLISHED_BLACKSTONE_BRICKS},
                    Material.GILDED_BLACKSTONE, Material.POLISHED_BLACKSTONE, Material.POLISHED_BLACKSTONE_BRICKS,
                    new Material[] {Material.NETHER_BRICKS, Material.CRACKED_NETHER_BRICKS, Material.RED_NETHER_BRICKS},
                    Material.CHISELED_NETHER_BRICKS, new Material[] {Material.BLACKSTONE, Material.NETHER_BRICKS},
                    Material.CHISELED_POLISHED_BLACKSTONE, "minecraft:soul_lantern[hanging=true]", "minecraft:soul_wall_torch",
                    "minecraft:soul_campfire[lit=true]",
                    new Material[] {Material.NETHER_BRICK_FENCE, Material.SOUL_SAND, Material.CRIMSON_ROOTS, Material.WITHER_SKELETON_SKULL, Material.MAGMA_BLOCK},
                    Material.IRON_BARS, "minecraft:crimson_wall_sign", "minecraft:red_wall_banner",
                    "minecraft:weeping_vines",
                    new Material[] {Material.BLACKSTONE, Material.NETHERRACK, Material.BLACKSTONE_SLAB, Material.BONE_BLOCK}, Material.NETHER_BRICK_SLAB);
            case "vuc" -> new Theme(
                    new Material[] {Material.END_STONE_BRICKS, Material.PURPUR_BLOCK, Material.OBSIDIAN},
                    Material.CRYING_OBSIDIAN, Material.PURPUR_BLOCK, Material.OBSIDIAN,
                    new Material[] {Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.PURPUR_BLOCK, Material.END_STONE_BRICKS},
                    Material.PURPUR_PILLAR, new Material[] {Material.OBSIDIAN, Material.PURPUR_BLOCK},
                    Material.PURPUR_PILLAR, "minecraft:end_rod[facing=down]", "minecraft:soul_wall_torch", "minecraft:soul_campfire[lit=true]",
                    new Material[] {Material.AMETHYST_CLUSTER, Material.CHORUS_PLANT, Material.SMALL_AMETHYST_BUD, Material.DRAGON_HEAD, Material.AMETHYST_BLOCK},
                    Material.IRON_BARS, "minecraft:warped_wall_sign", "minecraft:purple_wall_banner",
                    "minecraft:amethyst_cluster[facing=down]",
                    new Material[] {Material.END_STONE, Material.OBSIDIAN, Material.PURPUR_SLAB, Material.AMETHYST_BLOCK}, Material.PURPUR_SLAB);
            default -> new Theme(   // hammo
                    new Material[] {Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.MOSSY_COBBLESTONE},
                    Material.POLISHED_DEEPSLATE, Material.POLISHED_ANDESITE, Material.DEEPSLATE_BRICKS,
                    new Material[] {Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.MOSSY_STONE_BRICKS},
                    Material.CHISELED_STONE_BRICKS, new Material[] {Material.DEEPSLATE_TILES, Material.DEEPSLATE_BRICKS},
                    Material.POLISHED_DEEPSLATE, "minecraft:soul_lantern[hanging=true]", "minecraft:soul_wall_torch", "minecraft:soul_campfire[lit=true]",
                    new Material[] {Material.COBWEB, Material.SKELETON_SKULL, Material.BONE_BLOCK, Material.CANDLE, Material.COBWEB},
                    Material.IRON_BARS, "minecraft:dark_oak_wall_sign", "minecraft:gray_wall_banner",
                    "minecraft:hanging_roots",
                    new Material[] {Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.STONE_BRICK_SLAB, Material.BONE_BLOCK}, Material.STONE_BRICK_SLAB);
        };
    }

    private static Material pick(Material[] a, ThreadLocalRandom rnd) {
        return a[rnd.nextInt(a.length)];
    }

    private int roomHeight(Run r, int i) {
        return i == r.dg.rooms() - 1 ? 10 : 7;
    }

    /** Dựng từng phòng mỗi tick (xoá sạch hộp trước, ô có thể còn đồ dựng của lượt cũ), xong gọi done. */
    private void build(World w, Run r, Runnable done) {
        Theme t = theme(r.dg.theme());
        int[] i = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!runs.containsValue(r)) {
                task.cancel();
                return;
            }
            if (i[0] >= r.dg.rooms()) {
                task.cancel();
                done.run();
                return;
            }
            buildRoom(w, r, t, i[0]);
            i[0]++;
        }, 1L, 1L);
    }

    private void set(World w, int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }

    private void set(World w, int x, int y, int z, String data) {
        w.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(data), false);
    }

    private void buildRoom(World w, Run r, Theme t, int i) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int x0 = r.roomX(i);
        int x1 = x0 + ROOM + 1;
        int h = roomHeight(r, i);
        int half = ROOM / 2 + 1;   // tường ở z = ±9
        int last = r.dg.rooms() - 1;
        int mid = last / 2;
        int y0 = FLOOR_Y;
        // xoá hộp phòng + hành lang phía sau
        for (int x = x0 - 1; x <= x0 + STEP; x++) {
            for (int z = -half - 1; z <= half + 1; z++) {
                for (int y = y0; y <= y0 + 14; y++) {
                    if (w.getBlockAt(x, y, z).getType() != Material.AIR) {
                        set(w, x, y, z, Material.AIR);
                    }
                }
            }
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = -half; z <= half; z++) {
                int d = Math.min(Math.min(x - x0, x1 - x), Math.min(z + half, half - z));   // cách tường bao nhiêu
                boolean edge = d == 0;
                // sàn: viền, lối giữa, còn lại trộn
                Material fl = edge ? t.base() : d == 1 ? t.accent() : Math.abs(z) <= 1 ? t.path()
                        : (x + z) % 7 == 0 ? t.accent() : pick(t.floor(), rnd);
                set(w, x, y0, z, fl);
                if (edge) {   // tường: chân, thân (trụ ốp mỗi 4 khối), đỉnh
                    boolean pilaster = (x == x0 || x == x1) ? Math.floorMod(z, 4) == 0 : Math.floorMod(x - x0, 4) == 0;
                    for (int y = y0 + 1; y <= y0 + h + 1; y++) {
                        Material m = y == y0 + 1 ? t.base() : y == y0 + h + 1 ? t.trim() : pilaster ? t.pillar() : pick(t.wall(), rnd);
                        set(w, x, y, z, m);
                    }
                    set(w, x, y0 + h + 2, z, t.trim());
                } else {   // trần vòm: sát tường thấp, giữa cao hơn 1, vòng chuyển bằng trim
                    if (d == 1) {
                        set(w, x, y0 + h + 1, z, pick(t.ceil(), rnd));
                    } else {
                        if (d == 2) {
                            set(w, x, y0 + h + 1, z, t.trim());
                        }
                        set(w, x, y0 + h + 2, z, pick(t.ceil(), rnd));
                    }
                }
            }
        }
        // cột 4 góc trong (phòng trùm: 6 cột), mũ cột
        int[] colX = i == last ? new int[] {x0 + 3, x0 + 9, x0 + 15} : new int[] {x0 + 3, x0 + ROOM - 2};
        for (int cx : colX) {
            for (int cz : new int[] {-5, 5}) {
                for (int y = y0 + 1; y <= y0 + h + 1; y++) {
                    set(w, cx, y, cz, y == y0 + 1 || y == y0 + h + 1 ? t.trim() : t.pillar());
                }
            }
        }
        // đèn chùm: xích + đèn treo
        for (int x = x0 + 6; x <= x1 - 6; x += 6) {
            for (int z : new int[] {-3, 3}) {
                set(w, x, y0 + h + 1, z, "minecraft:chain[axis=y]");
                set(w, x, y0 + h, z, t.lantern());
            }
        }
        // đuốc tường mỗi 4 khối (giữa 2 trụ ốp), tránh lỗ cửa
        for (int x = x0 + 2; x < x1; x += 4) {
            set(w, x, y0 + 3, -half + 1, t.torch() + "[facing=south]");
            set(w, x, y0 + 3, half - 1, t.torch() + "[facing=north]");
        }
        for (int z = -half + 2; z < half; z += 4) {
            if (Math.abs(z) > 2) {
                if (i > 0) {
                    set(w, x0 + 1, y0 + 3, z, t.torch() + "[facing=east]");
                }
                set(w, x1 - 1, y0 + 3, z, t.torch() + "[facing=west]");
            }
        }
        // góc phòng + sát tường: đồ trang trí
        for (int[] c : new int[][] {{x0 + 1, -half + 1}, {x0 + 1, half - 1}, {x1 - 1, -half + 1}, {x1 - 1, half - 1}}) {
            set(w, c[0], y0 + h, c[1], Material.COBWEB);
        }
        for (int n = 0; n < 10; n++) {
            int x = x0 + 2 + rnd.nextInt(ROOM - 2);
            int z = rnd.nextBoolean() ? -half + 1 : half - 1;
            if (w.getBlockAt(x, y0 + 1, z).getType() == Material.AIR) {
                set(w, x, y0 + 1, z, pick(t.deco(), rnd));
            }
        }
        // cờ treo tường phía trên đuốc
        for (int x = x0 + 2; x < x1; x += 8) {
            set(w, x, y0 + h - 1, -half + 1, t.banner() + "[facing=south]");
            set(w, x, y0 + h - 1, half - 1, t.banner() + "[facing=north]");
        }
        // đồ treo trần + xích rủ (không trên lối giữa)
        for (int n = 0; n < 9; n++) {
            int x = x0 + 3 + rnd.nextInt(ROOM - 4);
            int z = (rnd.nextBoolean() ? 1 : -1) * (3 + rnd.nextInt(5));
            int top = y0 + h + 1 + (Math.min(Math.min(x - x0, x1 - x), Math.min(z + half, half - z)) >= 2 ? 1 : 0);
            if (w.getBlockAt(x, top - 1, z).getType() != Material.AIR) {
                continue;
            }
            if (n % 3 == 0) {
                int len = 1 + rnd.nextInt(3);
                for (int k = 1; k <= len; k++) {
                    set(w, x, top - k, z, "minecraft:chain[axis=y]");
                }
            } else {
                set(w, x, top - 1, z, t.hang());
            }
        }
        // gạch vụn, nến, cột gãy (phòng đánh)
        for (int n = 0; n < 8; n++) {
            int x = x0 + 2 + rnd.nextInt(ROOM - 2);
            int z = (rnd.nextBoolean() ? 1 : -1) * (3 + rnd.nextInt(5));
            if (w.getBlockAt(x, y0 + 1, z).getType() == Material.AIR) {
                set(w, x, y0 + 1, z, pick(t.rubble(), rnd));
            }
        }
        for (int n = 0; n < 5; n++) {
            int x = x0 + 1 + rnd.nextInt(ROOM);
            int z = rnd.nextBoolean() ? -half + 1 : half - 1;
            if (w.getBlockAt(x, y0 + 1, z).getType() == Material.AIR) {
                set(w, x, y0 + 1, z, "minecraft:candle[candles=" + (1 + rnd.nextInt(4)) + ",lit=true]");
            }
        }
        if (i > 0 && i < last) {
            for (int n = 0; n < 2; n++) {
                int x = x0 + 5 + rnd.nextInt(ROOM - 9);
                int z = (rnd.nextBoolean() ? 1 : -1) * (3 + rnd.nextInt(3));
                if (w.getBlockAt(x, y0 + 1, z).getType() != Material.AIR) {
                    continue;
                }
                int ph = 1 + rnd.nextInt(3);
                for (int y = y0 + 1; y < y0 + 1 + ph; y++) {
                    set(w, x, y, z, t.pillar());
                }
                set(w, x, y0 + 1 + ph, z, t.slab());
            }
        }
        // lồng quái: spawner trang trí (quái do plugin cho chui ra, lồng không tự đẻ)
        r.cages.get(i).clear();
        if (i > 0) {
            // quanh phòng, sát hai bên tường (29/10 chủ server: đặt quanh để quái ra từ mọi phía); phòng sau nhiều lồng hơn
            int[] xs = i >= 2 || i == last ? new int[] {x0 + 5, x0 + 9, x0 + 13} : new int[] {x0 + 6, x0 + 12};
            List<int[]> spots = new ArrayList<>();
            for (int cx : xs) {
                spots.add(new int[] {cx, -7});
                spots.add(new int[] {cx, 7});
            }
            for (int[] c : spots) {
                set(w, c[0], y0, c[1], t.accent());
                set(w, c[0], y0 + 1, c[1], Material.SPAWNER);
                if (w.getBlockAt(c[0], y0 + 1, c[1]).getState() instanceof org.bukkit.block.CreatureSpawner cs) {
                    cs.setSpawnedType(r.dg.mobs().get(rnd.nextInt(r.dg.mobs().size())).type());
                    cs.setMaxNearbyEntities(0);
                    cs.setDelay(Integer.MAX_VALUE / 2);
                    cs.update(true, false);
                }
                set(w, c[0], y0 + 2, c[1], "minecraft:chain[axis=y]");
                r.cages.get(i).add(c);
            }
        }
        // phòng đầu: biển tên + lò lửa hai bên + đồ của người canh hầm
        if (i == 0) {
            w.getBlockAt(x0 + 1, y0 + 3, 0).setBlockData(Bukkit.createBlockData(t.sign() + "[facing=east]"), false);
            if (w.getBlockAt(x0 + 1, y0 + 3, 0).getState() instanceof org.bukkit.block.Sign sg) {
                var side = sg.getSide(org.bukkit.block.sign.Side.FRONT);
                side.line(0, Tip.c("&6⚔"));
                side.line(1, Tip.c("&f" + r.dg.name()));
                side.line(2, Tip.c("&7Set " + setName(r.dg.set())));
                side.line(3, Tip.c("&8/hamnguc thoat"));
                side.setGlowingText(true);
                sg.update(true, false);
            }
            set(w, x0 + 2, y0 + 1, -2, t.fire());
            set(w, x0 + 2, y0 + 1, 2, t.fire());
            set(w, x0 + 1, y0 + 1, -half + 1, Material.ANVIL);
            set(w, x0 + 2, y0 + 1, -half + 1, Material.DECORATED_POT);
            set(w, x0 + 1, y0 + 1, half - 1, "minecraft:water_cauldron[level=3]");
            set(w, x0 + 2, y0 + 1, half - 1, Material.DECORATED_POT);
            for (int x = x0 + 4; x <= x0 + 7; x++) {
                set(w, x, y0 + 1, half - 1, Material.BOOKSHELF);
                set(w, x, y0 + 2, half - 1, x % 2 == 0 ? Material.CHISELED_BOOKSHELF : Material.BOOKSHELF);
            }
        }
        // phòng phó trùm: bệ thờ phía cuối phòng
        if (i == mid && i != last) {
            set(w, x1 - 2, y0 + 1, 0, t.accent());
            set(w, x1 - 2, y0 + 2, 0, t.trim());
            set(w, x1 - 2, y0 + 3, 0, t.fire());
        }
        // phòng trùm: bệ cao cuối phòng, ngai, lò lửa giữa các cột
        if (i == last) {
            for (int x = x1 - 4; x < x1; x++) {
                for (int z = -4; z <= 4; z++) {
                    set(w, x, y0 + 1, z, x == x1 - 4 || Math.abs(z) == 4 ? t.trim() : t.accent());
                }
            }
            set(w, x1 - 1, y0 + 2, 0, t.pillar());
            set(w, x1 - 1, y0 + 3, 0, t.trim());
            for (int x : new int[] {x0 + 6, x0 + 12}) {
                for (int z : new int[] {-6, 6}) {
                    set(w, x, y0 + 1, z, t.fire());
                }
            }
        }
        // cửa vào (tường trái) và cửa ra (tường phải): lỗ 3 x 4, khung trim; cửa ra đóng song sắt (trừ phòng 0)
        for (int z = -2; z <= 2; z++) {
            for (int y = y0 + 1; y <= y0 + 5; y++) {
                boolean hole = Math.abs(z) <= 1 && y <= y0 + 4;
                if (i > 0) {
                    set(w, x0, y, z, hole ? Material.AIR : t.trim());
                }
                if (i < last) {
                    set(w, x1, y, z, hole ? (i == 0 ? Material.AIR : t.gate()) : t.trim());
                }
            }
        }
        // hành lang tới phòng sau: vòm, đuốc
        if (i < last) {
            for (int x = x1 + 1; x < x0 + STEP; x++) {
                for (int z = -2; z <= 2; z++) {
                    set(w, x, y0, z, Math.abs(z) <= 1 ? t.path() : t.base());
                    set(w, x, y0 + 5, z, pick(t.ceil(), rnd));
                    for (int y = y0 + 1; y <= y0 + 4; y++) {
                        Material m = Math.abs(z) == 2 ? (y == y0 + 1 ? t.base() : (x - x1) % 3 == 0 ? t.pillar() : pick(t.wall(), rnd))
                                : Material.AIR;
                        set(w, x, y, z, m);
                    }
                    if (Math.abs(z) == 1) {
                        set(w, x, y0 + 4, z, t.trim());   // vòm
                    }
                }
            }
            int cx = x1 + 1 + CORRIDOR / 2;
            set(w, cx, y0 + 2, -1, t.torch() + "[facing=south]");
            set(w, cx, y0 + 2, 1, t.torch() + "[facing=north]");
            for (int sz : new int[] {-1, 1}) {   // hốc tường: đầu lâu + nến
                int nx = cx + (sz < 0 ? -2 : 2);
                set(w, nx, y0 + 2, 2 * sz, Material.AIR);
                set(w, nx, y0 + 2, 3 * sz, t.trim());
                set(w, nx, y0 + 1, 2 * sz, t.base());
                set(w, nx, y0 + 3, 2 * sz, t.trim());
                set(w, nx, y0 + 2, 2 * sz, rnd.nextBoolean() ? "minecraft:skeleton_skull[rotation=" + (sz < 0 ? 0 : 8) + "]"
                        : "minecraft:candle[candles=3,lit=true]");
            }
        }
    }

    private void openGate(World w, Run r, int i) {
        int x = r.roomX(i) + ROOM + 1;
        for (int z = -1; z <= 1; z++) {
            for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 4; y++) {
                set(w, x, y, z, Material.AIR);
            }
        }
        w.playSound(new Location(w, x, FLOOR_Y + 2, 0), Sound.BLOCK_IRON_DOOR_OPEN, 1.5f, 0.6f);
        w.spawnParticle(Particle.CLOUD, x + 0.5, FLOOR_Y + 2.5, 0.5, 20, 0.2, 1, 1, 0.02);
    }

    // ------------------------------------------------------------------ quái

    private double partyMult(Run r) {
        return 1 + cfg.getDouble("party-hp", 0.6) * Math.max(0, r.members.size() - 1);
    }

    private LivingEntity spawnMob(World w, Run r, MobDef d, String role, Location at) {
        Class<? extends Entity> cls = d.type().getEntityClass();
        if (cls == null || !LivingEntity.class.isAssignableFrom(cls)) {
            return null;
        }
        String color = role.equals("boss") ? "&c&l" : role.equals("mini") ? "&6" : "&7";
        double hp = Math.min(2000, d.hp() * partyMult(r));
        Entity e = w.spawn(at, cls, en -> {
            LivingEntity le = (LivingEntity) en;
            le.customName(Tip.c(color + d.name()));
            le.setCustomNameVisible(!role.equals("mob"));
            le.setRemoveWhenFarAway(false);
            le.setCanPickupItems(false);
            le.setPersistent(false);
            AttributeInstance mh = le.getAttribute(Attribute.MAX_HEALTH);
            if (mh != null) {
                mh.setBaseValue(hp);
                le.setHealth(hp);
            }
            if (d.scale() != 1.0 && le.getAttribute(Attribute.SCALE) != null) {
                le.getAttribute(Attribute.SCALE).setBaseValue(d.scale());
            }
            AttributeInstance kb = le.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            if (kb != null && !role.equals("mob")) {
                kb.setBaseValue(role.equals("boss") ? 0.8 : 0.5);
            }
            if (le instanceof org.bukkit.entity.PiglinAbstract pa) {
                pa.setImmuneToZombification(true);
            }
            if (le instanceof org.bukkit.entity.Ageable ag) {
                ag.setAdult();
            }
            EntityEquipment eq = le.getEquipment();
            if (eq != null) {
                eq.setHelmetDropChance(0);
                eq.setChestplateDropChance(0);
                eq.setLeggingsDropChance(0);
                eq.setBootsDropChance(0);
                eq.setItemInMainHandDropChance(0);
                eq.setItemInOffHandDropChance(0);
                if (eq.getHelmet() == null || eq.getHelmet().getType() == Material.AIR) {
                    eq.setHelmet(new ItemStack(Material.LEATHER_HELMET));   // không cháy nắng nếu lọt ra ngoài
                }
            }
            var pdc = le.getPersistentDataContainer();
            pdc.set(kRun, PersistentDataType.INTEGER, r.id);
            pdc.set(kRole, PersistentDataType.STRING, role);
            pdc.set(kDmg, PersistentDataType.DOUBLE, d.dmg());
        });
        if (e instanceof Mob mob) {
            Player tgt = nearestMember(r, at);
            if (tgt != null) {
                mob.setTarget(tgt);
            }
        }
        return (LivingEntity) e;
    }

    private Player nearestMember(Run r, Location at) {
        Player best = null;
        double bd = Double.MAX_VALUE;
        for (UUID m : r.members) {
            Player p = Bukkit.getPlayer(m);
            if (p != null && p.getWorld().equals(at.getWorld()) && !p.isDead()) {
                double d = p.getLocation().distanceSquared(at);
                if (d < bd) {
                    bd = d;
                    best = p;
                }
            }
        }
        return best;
    }

    private Location spot(World w, Run r, int i) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int x0 = r.roomX(i);
        for (int tries = 0; tries < 20; tries++) {
            int x = x0 + 8 + rnd.nextInt(ROOM - 9);
            int z = -6 + rnd.nextInt(13);
            if (w.getBlockAt(x, FLOOR_Y + 1, z).getType() == Material.AIR && w.getBlockAt(x, FLOOR_Y + 2, z).getType() == Material.AIR) {
                return new Location(w, x + 0.5, FLOOR_Y + 1, z + 0.5);
            }
        }
        return new Location(w, x0 + 12.5, FLOOR_Y + 1, 0.5);
    }

    private void startRoom(World w, Run r, int i) {
        r.started[i] = true;
        int last = r.dg.rooms() - 1;
        int mid = last / 2;
        int extra = Math.max(0, r.members.size() - 1);
        List<UUID> list = r.roomMobs.get(i);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String title;
        int count;
        if (i == last) {
            LivingEntity b = spawnMob(w, r, r.dg.boss(), "boss", new Location(w, r.roomX(i) + ROOM - 1.5, FLOOR_Y + 2, 0.5, 90f, 0f));
            if (b != null) {
                list.add(b.getUniqueId());
                r.boss = b.getUniqueId();
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, b.getLocation().add(0, 1, 0), 60, 0.6, 1.2, 0.6, 0.05);
                r.bar = Bukkit.createBossBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                        .serialize(Tip.c("&c&l" + strip(r.dg.boss().name()))), BarColor.RED, BarStyle.SEGMENTED_10);
                for (UUID m : r.members) {
                    Player p = Bukkit.getPlayer(m);
                    if (p != null) {
                        r.bar.addPlayer(p);
                    }
                }
            }
            count = 2 + extra;
            title = "&c&l" + strip(r.dg.boss().name());
        } else {
            count = Math.min(16, cfg.getInt("mobs-per-room", 3) + i + extra * 2);
            if (i == mid && r.dg.mini() != null) {
                LivingEntity m = spawnMob(w, r, r.dg.mini(), "mini", new Location(w, r.roomX(i) + 11.5, FLOOR_Y + 1, 0.5, 90f, 0f));
                if (m != null) {
                    list.add(m.getUniqueId());
                    w.spawnParticle(Particle.LARGE_SMOKE, m.getLocation().add(0, 1, 0), 40, 0.5, 1, 0.5, 0.02);
                }
                count = Math.max(2, count - 2);
            }
            title = i == mid && r.dg.mini() != null ? "&6" + strip(r.dg.mini().name()) : "&fPhòng " + i + "/" + (last - 1);
        }
        // chia đợt: mỗi đợt tối đa 2 con / lồng
        int per = Math.max(2, r.cages.get(i).size() * 2);
        List<MobDef> wave = new ArrayList<>();
        for (int n = 0; n < count; n++) {
            wave.add(r.dg.mobs().get(rnd.nextInt(r.dg.mobs().size())));
            if (wave.size() >= per) {
                r.waves.get(i).add(wave);
                wave = new ArrayList<>();
            }
        }
        if (!wave.isEmpty()) {
            r.waves.get(i).add(wave);
        }
        releaseWave(w, r, i);
        for (UUID m : r.members) {
            Player p = Bukkit.getPlayer(m);
            if (p != null) {
                p.showTitle(net.kyori.adventure.title.Title.title(Tip.c(title), Tip.c("&7Quái chui ra từ lồng, hạ hết để mở cửa")));
                p.playSound(p.getLocation(), i == last ? Sound.ENTITY_WITHER_SPAWN : Sound.ENTITY_EVOKER_PREPARE_SUMMON, 0.8f, 1f);
            }
        }
        // phòng trùm: đóng cửa vào sau lưng
        if (i == last) {
            int x = r.roomX(i);
            for (int z = -1; z <= 1; z++) {
                for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 4; y++) {
                    if (w.getBlockAt(x, y, z).getType() == Material.AIR) {
                        set(w, x, y, z, theme(r.dg.theme()).gate());
                    }
                }
            }
        }
    }

    /** Thả đợt kế tiếp: từng con chui ra khỏi lồng cách nhau 6 tick. */
    private void releaseWave(World w, Run r, int i) {
        List<MobDef> wave = r.waves.get(i).poll();
        if (wave == null) {
            return;
        }
        if (r.cleared[i - 1] && r.waves.get(i).size() + 1 < countWaves(r, i)) {
            tell(r, "&c⚠ Đợt quái mới!");
        }
        for (int n = 0; n < wave.size(); n++) {
            MobDef d = wave.get(n);
            r.spawning[i]++;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                r.spawning[i]--;
                if (!runs.containsValue(r) || r.cleared[i]) {
                    return;
                }
                Location l = cageExit(w, r, i);
                LivingEntity e = spawnMob(w, r, d, "mob", l);
                if (e != null) {
                    r.roomMobs.get(i).add(e.getUniqueId());
                    w.spawnParticle(Particle.FLAME, l.clone().add(0, 0.8, 0), 18, 0.3, 0.5, 0.3, 0.02);
                    w.spawnParticle(Particle.LARGE_SMOKE, l.clone().add(0, 1, 0), 10, 0.3, 0.4, 0.3, 0.01);
                    w.playSound(l, Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 0.8f);
                }
            }, 6L * n + 4L);
        }
    }

    private final Map<Integer, Integer> waveTotal = new HashMap<>();

    private int countWaves(Run r, int i) {
        return waveTotal.computeIfAbsent(r.id * 16 + i, k -> r.waves.get(i).size() + 1);
    }

    /** Chỗ quái ra: ô trống sát một lồng ngẫu nhiên của phòng (không có lồng thì chỗ ngẫu nhiên). */
    private Location cageExit(World w, Run r, int i) {
        List<int[]> cs = r.cages.get(i);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (!cs.isEmpty()) {
            int[] c = cs.get(rnd.nextInt(cs.size()));
            int[][] around = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, 1}, {-1, 1}, {1, -1}};
            for (int k = 0; k < around.length; k++) {
                int[] o = around[(k + rnd.nextInt(around.length)) % around.length];
                int x = c[0] + o[0];
                int z = c[1] + o[1];
                if (w.getBlockAt(x, FLOOR_Y + 1, z).isPassable() && w.getBlockAt(x, FLOOR_Y + 2, z).isPassable()) {
                    return new Location(w, x + 0.5, FLOOR_Y + 1, z + 0.5);
                }
            }
        }
        return spot(w, r, i);
    }

    private boolean alive(UUID id) {
        Entity e = Bukkit.getEntity(id);
        return e != null && e.isValid() && !e.isDead();
    }

    private int roomOf(Run r, Location l) {
        double x = l.getX() - r.baseX();
        int i = (int) Math.floor(x / STEP);
        double in = x - i * STEP;
        if (i < 0 || i >= r.dg.rooms() || in < 1 || in > ROOM + 1 || Math.abs(l.getZ()) > ROOM / 2.0) {
            return -1;
        }
        return i;
    }

    // ------------------------------------------------------------------ vòng lặp

    private void tick() {
        World w = Bukkit.getWorld(worldName);
        long now = System.currentTimeMillis();
        for (Run r : new ArrayList<>(runs.values())) {
            if (!r.ready) {
                continue;
            }
            if (r.members.isEmpty()) {
                if (r.emptySince > 0 && now - r.emptySince > 15_000) {
                    end(r, "");
                }
                continue;
            }
            r.emptySince = 0;
            if (r.doneAt > 0) {
                if (now - r.doneAt > cfg.getLong("exit-after-seconds", 30) * 1000L) {
                    end(r, "&6⚔ &fHết giờ ở lại, đưa bạn về.");
                }
                continue;
            }
            if (now - r.startAt > cfg.getLong("time-limit-minutes", 40) * 60_000L) {
                end(r, "&c⌛ Hết giờ hầm ngục.");
                continue;
            }
            if (w == null) {
                continue;
            }
            // vào phòng mới
            for (UUID m : r.members) {
                Player p = Bukkit.getPlayer(m);
                if (p == null || !inWorld(p.getWorld())) {
                    continue;
                }
                int i = roomOf(r, p.getLocation());
                if (i > 0 && !r.started[i] && r.cleared[i - 1]) {
                    startRoom(w, r, i);
                }
            }
            // phòng sạch quái
            for (int i = 1; i < r.dg.rooms(); i++) {
                if (r.started[i] && !r.cleared[i] && r.spawning[i] == 0 && !r.waves.get(i).isEmpty()
                        && r.roomMobs.get(i).stream().filter(this::alive).count() <= (i == r.dg.rooms() - 1 ? 1 : 1)
                        && (i != r.dg.rooms() - 1 || r.roomMobs.get(i).stream().filter(this::alive).allMatch(id -> id.equals(r.boss)))) {
                    releaseWave(w, r, i);
                }
                if (r.started[i] && !r.cleared[i] && r.spawning[i] == 0 && r.waves.get(i).isEmpty()
                        && r.roomMobs.get(i).stream().noneMatch(this::alive)) {
                    r.cleared[i] = true;
                    if (i == r.dg.rooms() - 1) {
                        complete(r);
                    } else {
                        openGate(w, r, i);
                        tell(r, "&a✔ Phòng đã sạch, cửa mở!");
                    }
                }
            }
            // thanh máu + kỹ năng trùm
            if (r.boss != null && r.bar != null) {
                Entity b = Bukkit.getEntity(r.boss);
                if (b instanceof LivingEntity le && le.isValid()) {
                    AttributeInstance mh = le.getAttribute(Attribute.MAX_HEALTH);
                    r.bar.setProgress(Math.max(0, Math.min(1, le.getHealth() / (mh == null ? 20 : mh.getValue()))));
                    if (r.skillTick % 16 == 8) {   // ~8 giây
                        bossSkill(w, r, le);
                    }
                }
            }
            for (int i = 1; i < r.dg.rooms(); i++) {   // phó trùm gọi thêm lính
                if (r.started[i] && !r.cleared[i] && r.skillTick % 24 == 0) {
                    for (UUID id : new ArrayList<>(r.roomMobs.get(i))) {
                        Entity e = Bukkit.getEntity(id);
                        if (e != null && e.isValid() && "mini".equals(e.getPersistentDataContainer().get(kRole, PersistentDataType.STRING))) {
                            summon(w, r, i, e.getLocation(), 1);
                        }
                    }
                }
            }
            r.skillTick++;
        }
    }

    private void summon(World w, Run r, int room, Location at, int n) {
        List<UUID> list = r.roomMobs.get(room);
        if (list.stream().filter(this::alive).count() > 10) {
            return;
        }
        for (int k = 0; k < n; k++) {
            Location l = r.cages.get(room).isEmpty() ? at : cageExit(w, r, room);
            LivingEntity e = spawnMob(w, r, r.dg.mobs().get(ThreadLocalRandom.current().nextInt(r.dg.mobs().size())), "mob", l);
            if (e != null) {
                list.add(e.getUniqueId());
                w.spawnParticle(Particle.SOUL, l.clone().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.02);
            }
        }
    }

    private void bossSkill(World w, Run r, LivingEntity boss) {
        int room = r.dg.rooms() - 1;
        double dmg = r.dg.boss().dmg();
        switch (ThreadLocalRandom.current().nextInt(3)) {
            case 0 -> {
                tell(r, "&c" + strip(r.dg.boss().name()) + " &7gọi thuộc hạ!");
                summon(w, r, room, boss.getLocation(), 2);
            }
            case 1 -> {   // dậm đất: báo trước 1 giây
                tell(r, "&c" + strip(r.dg.boss().name()) + " &7chuẩn bị dậm đất, &fchạy ra xa!");
                w.spawnParticle(Particle.ANGRY_VILLAGER, boss.getLocation().add(0, 1, 0), 12, 1.5, 0.3, 1.5);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!boss.isValid()) {
                        return;
                    }
                    Location c = boss.getLocation();
                    w.spawnParticle(Particle.EXPLOSION, c, 6, 2, 0.2, 2);
                    w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
                    for (UUID m : r.members) {
                        Player p = Bukkit.getPlayer(m);
                        if (p != null && p.getWorld().equals(w) && p.getLocation().distance(c) < 5) {
                            skillHit = true;
                            try {
                                p.damage(dmg * 1.2, boss);
                            } finally {
                                skillHit = false;
                            }
                            p.setVelocity(p.getLocation().toVector().subtract(c.toVector()).normalize().multiply(1.2).setY(0.6));
                        }
                    }
                }, 20L);
            }
            default -> {   // lao tới người xa nhất
                Player far = null;
                double fd = 0;
                for (UUID m : r.members) {
                    Player p = Bukkit.getPlayer(m);
                    if (p != null && p.getWorld().equals(w)) {
                        double d = p.getLocation().distanceSquared(boss.getLocation());
                        if (d > fd) {
                            fd = d;
                            far = p;
                        }
                    }
                }
                if (far != null && fd > 9) {
                    var v = far.getLocation().toVector().subtract(boss.getLocation().toVector()).normalize().multiply(1.4).setY(0.5);
                    boss.setVelocity(v);
                    if (boss instanceof Mob mob) {
                        mob.setTarget(far);
                    }
                    w.playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1f, 1f);
                }
            }
        }
    }

    private void tell(Run r, String msg) {
        for (UUID m : r.members) {
            Player p = Bukkit.getPlayer(m);
            if (p != null) {
                p.sendMessage(Tip.c(msg));
            }
        }
    }

    private void complete(Run r) {
        r.doneAt = System.currentTimeMillis();
        if (r.bar != null) {
            r.bar.removeAll();
        }
        for (UUID m : r.everyone) {
            Player p = Bukkit.getPlayer(m);
            int before = clears(m, r.dg.id());
            data.set("players." + m + "." + r.dg.id(), before + 1);
            double money = r.dg.reward() + (before == 0 ? r.dg.firstReward() : 0);
            if (money > 0) {
                VaultEconomy.deposit(Bukkit.getOfflinePlayer(m), money);
            }
            if (p != null) {
                p.showTitle(net.kyori.adventure.title.Title.title(Tip.c("&6&lVƯỢT HẦM NGỤC"), Tip.c("&f" + r.dg.name())));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                p.sendMessage(Tip.c("&6⚔ &fVượt &6" + r.dg.name() + (money > 0 ? "&f, nhận &e" + VaultEconomy.format(money) : "")
                        + (before == 0 ? " &7(lần đầu)" : "") + "&f. Tự về sau " + cfg.getLong("exit-after-seconds", 30) + " giây, hoặc &e/hamnguc thoat&f."));
                Dg next = dungeons.values().stream().filter(d -> r.dg.id().equals(d.requires())).findFirst().orElse(null);
                if (before == 0 && next != null) {
                    p.sendMessage(Tip.c("&a🔓 Đã mở &f" + next.name() + "&a."));
                }
            }
            if (before == 0 && cfg.getBoolean("broadcast-first", true) && p != null) {
                Bukkit.broadcast(Tip.c("&6⚔ &f" + p.getName() + " &7lần đầu vượt &6" + r.dg.name() + "&7! &f/hamnguc"));
            }
        }
        save();
    }

    // ------------------------------------------------------------------ rơi đồ, sát thương

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent e) {
        if (!inWorld(e.getEntity().getWorld()) || e.getEntity() instanceof Player) {
            return;
        }
        var pdc = e.getEntity().getPersistentDataContainer();
        Integer runId = pdc.get(kRun, PersistentDataType.INTEGER);
        e.getDrops().clear();
        if (runId == null) {
            e.setDroppedExp(0);
            return;
        }
        String role = pdc.getOrDefault(kRole, PersistentDataType.STRING, "mob");
        e.setDroppedExp(role.equals("boss") ? 60 : role.equals("mini") ? 20 : 3);
        Run r = runs.values().stream().filter(x -> x.id == runId).findFirst().orElse(null);
        if (r == null) {
            return;
        }
        Player k = e.getEntity().getKiller();
        double pct = cfg.getDouble("drop." + role, role.equals("boss") ? 40 : role.equals("mini") ? 15 : 2) * r.dg.dropMult();
        List<String> pool = cfg.getStringList("pieces." + role);
        if (pool.isEmpty()) {
            pool = role.equals("boss") ? List.of("mu", "giay", "quan", "ao", "kiem", "riu", "giao", "cung", "chuy")
                    : role.equals("mini") ? List.of("mu", "giay", "quan", "ao") : List.of("mu", "giay");
        }
        if (role.equals("boss")) {   // trùm: mỗi người trong lượt quay riêng
            for (UUID m : r.members) {
                Player p = Bukkit.getPlayer(m);
                if (p != null) {
                    roll(p, r.dg, pool, pct);
                }
            }
        } else if (k != null && r.members.contains(k.getUniqueId())) {
            roll(k, r.dg, pool, pct);
        }
    }

    private void roll(Player p, Dg d, List<String> pool, double pct) {
        if (ThreadLocalRandom.current().nextDouble(100) >= pct) {
            return;
        }
        String piece = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        List<String> ids = plugin.getConfig().getStringList("tho-ren.sets." + d.set() + ".pieces." + piece + ".ids");
        ItemLibrary lib = Shared.library;
        if (ids.isEmpty() || lib == null) {
            return;
        }
        ItemStack it = lib.build(ids.get(0), 1);
        if (it == null) {
            return;
        }
        Rewards.hand(p, it);
        p.sendMessage(Tip.c("&6✦ Nhặt được " + lib.name(ids.get(0)) + "&6!"));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!inWorld(e.getEntity().getWorld())) {
            return;
        }
        Entity src = e.getDamager();
        if (src instanceof Projectile pr && pr.getShooter() instanceof Entity sh) {
            src = sh;
        }
        if (e.getEntity() instanceof Player && src instanceof Player) {   // không đánh nhau trong hầm ngục
            e.setCancelled(true);
            return;
        }
        if (skillHit) {
            return;
        }
        Double dmg = src.getPersistentDataContainer().get(kDmg, PersistentDataType.DOUBLE);
        if (dmg != null && e.getEntity() instanceof Player) {
            e.setDamage(dmg);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent e) {   // quái hầm ngục không đánh nhau
        if (inWorld(e.getEntity().getWorld()) && e.getTarget() != null && !(e.getTarget() instanceof Player)
                && e.getTarget().getPersistentDataContainer().has(kRun, PersistentDataType.INTEGER)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawner(org.bukkit.event.entity.SpawnerSpawnEvent e) {
        if (inWorld(e.getLocation().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (inWorld(e.getEntity().getWorld())) {
            e.blockList().clear();
        }
    }

    // ------------------------------------------------------------------ bảo vệ thế giới, ra vào

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (inWorld(e.getBlock().getWorld()) && !e.getPlayer().hasPermission("duckymomo.admin")) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (inWorld(e.getBlock().getWorld()) && !e.getPlayer().hasPermission("duckymomo.admin")) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (inWorld(e.getBlock().getWorld()) && !e.getPlayer().hasPermission("duckymomo.admin")) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        boolean toDg = inWorld(e.getTo().getWorld());
        boolean fromDg = inWorld(e.getFrom().getWorld());
        if (toDg && !fromDg && !allowTp.contains(p.getUniqueId()) && !p.hasPermission("duckymomo.admin")) {
            e.setCancelled(true);   // /tpa, /tp tới người đang trong hầm ngục
            p.sendMessage(Tip.c("&cKhông dịch chuyển vào hầm ngục được. Muốn đi cùng: nhờ người trong đó &f/hamnguc moi " + p.getName()));
            return;
        }
        if (fromDg && !toDg && byPlayer.containsKey(p.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> leave(p, false));   // tự /spawn, /home…: rời lượt
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Run r = byPlayer.get(e.getPlayer().getUniqueId());
        if (r != null) {
            e.setRespawnLocation(entrance(r));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Run r = byPlayer.get(p.getUniqueId());
        if (r != null) {
            Location back = r.back.get(p.getUniqueId());
            leave(p, false);
            if (back != null) {   // vào lại thì về chỗ cũ
                data.set("back." + p.getUniqueId(), serialize(back));
                save();
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!inWorld(p.getWorld())) {
            return;
        }
        Location back = parse(data.getString("back." + p.getUniqueId()));
        data.set("back." + p.getUniqueId(), null);
        save();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && inWorld(p.getWorld())) {
                sendBack(p, back);
                p.sendMessage(Tip.c("&7Lượt hầm ngục đã đóng khi bạn thoát game, đưa bạn về."));
            }
        }, 20L);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Không lưu được hamnguc.yml: " + ex.getMessage());
        }
    }
}
