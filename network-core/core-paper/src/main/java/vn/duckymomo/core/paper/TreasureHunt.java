package vn.duckymomo.core.paper;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Bản đồ kho báu (/khobau, Bang Hội Chiến). Config treasure:
 *  - Nguồn: câu cá (fish-chance %), hạ quái thù địch (mob-chance %), tối đa max-per-day bản đồ/người/ngày.
 *  - tiers.<mã>: {name, weight, guards, guard-types, guard-health, rewards}. Bản đồ ghi sẵn vị trí: điểm ngẫu nhiên trên đất hoang (không trong
 *    thị trấn, không nước) trong một vùng /tpr đã mở.
 *  - Cầm bản đồ: thanh hành động báo lạnh / ấm / nóng theo khoảng cách. Tới nơi (<= dig-radius khối), ngồi xuống (Shift) để đào:
 *    lính canh xuất hiện; hạ hết trong guard-minutes phút thì mở rương (quà vào thẳng túi). 1/11: người hạ con cuối (đứng trong 40 khối)
 *    nhận, không nhất thiết là người đào; người đào chết / rời đi không huỷ. Hết giờ còn lính (hoặc không ai gần): trả bản đồ người đào.
 *  - Bậc có broadcast: true thì báo cả server vùng đang đào (người khác có thể tới tranh).
 *  - Bậc có reveal-hidden: <%> thì mở rương có thể kèm mảnh giấy chỉ đường tới lối vào một dungeon ẩn (Cổng Dungeon) chưa tìm ra.
 * Lưu: khobau.yml (ngay.<uuid>.<ngày> = số bản đồ đã nhận).
 */
final class TreasureHunt implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private record Tier(String id, String name, int weight, int guards, List<EntityType> types, double guardHealth, boolean broadcast, List<String> rewards, double revealHidden) { }
    private record Zone(String name, String requires, int x, int z, int r) { }

    private static final class Dig {
        final UUID player;
        final Location loc;
        final Tier tier;
        final ItemStack map;
        final Set<UUID> guards = new HashSet<>();
        final long until;
        UUID lastKiller;      // người hạ lính canh gần nhất (1/11: ai hạ con cuối mà đứng gần thì người đó mở rương)

        Dig(UUID player, Location loc, Tier tier, ItemStack map, long until) {
            this.player = player;
            this.loc = loc;
            this.tier = tier;
            this.map = map;
            this.until = until;
        }
    }

    private final DuckyMoMoCore plugin;
    private final Rewards rewards;
    private final String world;
    private final double fishChance;
    private final double mobChance;
    private final int maxPerDay;
    private final int digRadius;
    private final long guardMs;
    private final List<Tier> tiers = new ArrayList<>();
    private final List<Zone> zones = new ArrayList<>();
    private final NamespacedKey key;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, Dig> digs = new HashMap<>();

    TreasureHunt(DuckyMoMoCore plugin, Rewards rewards, ConfigurationSection c) {
        this.plugin = plugin;
        this.rewards = rewards;
        this.world = plugin.getConfig().getString("rtp.world", "world");
        this.fishChance = c.getDouble("fish-chance", 3);
        this.mobChance = c.getDouble("mob-chance", 0.5);
        this.maxPerDay = c.getInt("max-per-day", 5);
        this.digRadius = Math.max(2, c.getInt("dig-radius", 4));
        this.guardMs = Math.max(1, c.getLong("guard-minutes", 5)) * 60_000L;
        ConfigurationSection ts = c.getConfigurationSection("tiers");
        if (ts != null) {
            for (String id : ts.getKeys(false)) {
                ConfigurationSection t = ts.getConfigurationSection(id);
                List<EntityType> types = new ArrayList<>();
                for (String n : t.getStringList("guard-types")) {
                    try {
                        types.add(EntityType.valueOf(n.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException ignored) {
                        plugin.getLogger().warning("Kho báu: loài lính canh lạ " + n);
                    }
                }
                if (types.isEmpty()) {
                    types.add(EntityType.ZOMBIE);
                }
                tiers.add(new Tier(id, t.getString("name", id), Math.max(1, t.getInt("weight", 1)), Math.max(0, t.getInt("guards", 2)), types,
                        Math.max(1, t.getDouble("guard-health", 1)), t.getBoolean("broadcast", false), t.getStringList("rewards"),
                        Math.max(0, t.getDouble("reveal-hidden", 0))));
            }
        }
        for (Map<?, ?> z : plugin.getConfig().getMapList("rtp.zones")) {
            if (z.get("radius") instanceof Number r && r.intValue() > 0) {
                zones.add(new Zone(String.valueOf(z.get("name")), z.get("requires") == null ? "" : String.valueOf(z.get("requires")),
                        ((Number) z.get("center-x")).intValue(), ((Number) z.get("center-z")).intValue(), r.intValue()));
            }
        }
        this.key = new NamespacedKey(plugin, "dmm_khobau");
        this.file = new File(plugin.getDataFolder(), "khobau.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    boolean ready() {
        return !tiers.isEmpty() && !zones.isEmpty();
    }

    String describe() {
        return tiers.size() + " bậc, câu cá " + fishChance + "%, hạ quái " + mobChance + "%, tối đa " + maxPerDay + "/ngày";
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    void stop() {
        for (UUID id : new ArrayList<>(bars.keySet())) {
            hideBar(id);
        }
        for (Dig d : digs.values()) {
            d.guards.forEach(id -> {
                var e = Bukkit.getEntity(id);
                if (e != null) {
                    e.remove();
                }
            });
            Player p = Bukkit.getPlayer(d.player);
            if (p != null) {
                Rewards.hand(p, d.map);   // tắt plugin giữa chừng: trả bản đồ
            }
        }
        digs.clear();
    }

    // ---------------------------------------------------------------- bản đồ

    private Tier pickTier() {
        int sum = tiers.stream().mapToInt(Tier::weight).sum();
        int r = ThreadLocalRandom.current().nextInt(sum);
        for (Tier t : tiers) {
            r -= t.weight();
            if (r < 0) {
                return t;
            }
        }
        return tiers.get(0);
    }

    /** Điểm đất hoang ngẫu nhiên trong một vùng đã mở (null nếu không tìm được). */
    private Location pickSpot(Zone[] chosen) {
        World w = Bukkit.getWorld(world);
        List<Zone> open = zones.stream().filter(z -> Shared.unlocked(z.requires())).toList();
        if (w == null || open.isEmpty()) {
            return null;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 20; i++) {
            Zone z = open.get(r.nextInt(open.size()));
            double a = r.nextDouble(Math.PI * 2), d = Math.sqrt(r.nextDouble()) * z.r() * 0.85;
            int x = z.x() + (int) (Math.cos(a) * d), zz = z.z() + (int) (Math.sin(a) * d);
            Block top = w.getHighestBlockAt(x, zz);
            if (top.isLiquid() || top.getType() == Material.ICE || top.getY() < w.getSeaLevel() - 2) {
                continue;
            }
            Location l = top.getLocation().add(0.5, 1, 0.5);
            if (Bukkit.getPluginManager().isPluginEnabled("Towny") && !com.palmergames.bukkit.towny.TownyAPI.getInstance().isWilderness(l)) {
                continue;
            }
            chosen[0] = z;
            return l;
        }
        return null;
    }

    private ItemStack makeMap(Tier t, Location l, Zone z) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        int rx = (int) Math.round(l.getX() / 50.0) * 50, rz = (int) Math.round(l.getZ() / 50.0) * 50;
        Tip tip = Tip.of("&6⌖ &fBản đồ kho báu").sub(t.name()).desc("Cầm trên tay để dò: lạnh → ấm → nóng").line()
                .kv("Vùng", z.name()).kv("Quanh toạ độ", "X " + rx + " Z " + rz + " &7(±50)").line()
                .click("Tới nơi", "ngồi xuống (Shift) để đào").note("Lính canh sẽ xuất hiện!");
        m.displayName(tip.name());
        m.lore(tip.lore());
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(key, PersistentDataType.STRING,
                t.id() + ";" + l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ());
        it.setItemMeta(m);
        return it;
    }

    private String[] read(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        String v = it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return v == null ? null : v.split(";");
    }

    /** Cho người chơi một bản đồ (giới hạn mỗi ngày). Trả false nếu đã đủ hoặc không tìm được chỗ. */
    boolean give(Player p, boolean count) {
        String k = "ngay." + p.getUniqueId() + "." + LocalDate.now(VN);
        if (count && data.getInt(k, 0) >= maxPerDay) {
            return false;
        }
        Zone[] z = new Zone[1];
        Location l = pickSpot(z);
        if (l == null) {
            return false;
        }
        Tier t = pickTier();
        Rewards.hand(p, makeMap(t, l, z[0]));
        if (count) {
            data.set(k, data.getInt(k, 0) + 1);
            save();
        }
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.8f);
        p.sendMessage(c("&6⌖ Bạn nhặt được &eBản đồ kho báu &7(" + t.name() + "&7) &6ở vùng &f" + z[0].name() + "&6. Cầm trên tay để dò."));
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH && ThreadLocalRandom.current().nextDouble(100) < fishChance) {
            give(e.getPlayer(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k != null && "linhcanh".equals(e.getEntity().getPersistentDataContainer().get(key, PersistentDataType.STRING))) {
            for (Dig d : digs.values()) {
                if (d.guards.contains(e.getEntity().getUniqueId())) {
                    d.lastKiller = k.getUniqueId();
                }
            }
        }
        if (k != null && e.getEntity() instanceof Monster && !e.getEntity().getPersistentDataContainer().has(key)
                && ThreadLocalRandom.current().nextDouble(100) < mobChance) {
            give(k, true);
        }
    }

    // ---------------------------------------------------------------- dò + đào

    /** 1/11 chủ server: gợi ý nóng/lạnh ở thanh hành động bị tin vùng của Towny ("~ KinhDo (Hamlet) - (Không PVP)") đè qua đè lại
     *  -> chuyển lên THANH TRÊN ĐẦU (boss bar) riêng từng người, chỉ hiện khi đang cầm bản đồ. */
    private final Map<UUID, net.kyori.adventure.bossbar.BossBar> bars = new HashMap<>();

    private void bar(Player p, String text, float progress, net.kyori.adventure.bossbar.BossBar.Color color) {
        net.kyori.adventure.bossbar.BossBar b = bars.get(p.getUniqueId());
        if (b == null) {
            b = net.kyori.adventure.bossbar.BossBar.bossBar(c(text), progress, color, net.kyori.adventure.bossbar.BossBar.Overlay.PROGRESS);
            bars.put(p.getUniqueId(), b);
            p.showBossBar(b);
        } else {
            b.name(c(text)).progress(progress).color(color);
        }
    }

    private void hideBar(UUID id) {
        net.kyori.adventure.bossbar.BossBar b = bars.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (b != null && p != null) {
            p.hideBossBar(b);
        }
    }

    private void tick() {
        Set<UUID> holding = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            String[] v = read(p.getInventory().getItemInMainHand());
            if (v == null || digs.containsKey(p.getUniqueId())) {
                continue;
            }
            holding.add(p.getUniqueId());
            World w = Bukkit.getWorld(v[1]);
            if (w == null || p.getWorld() != w) {
                bar(p, "&7⌖ Kho báu ở thế giới khác.", 0f, net.kyori.adventure.bossbar.BossBar.Color.WHITE);
                continue;
            }
            double dx = p.getLocation().getX() - Integer.parseInt(v[2]), dz = p.getLocation().getZ() - Integer.parseInt(v[4]);
            double d = Math.sqrt(dx * dx + dz * dz);
            float prog = (float) Math.max(0, Math.min(1, 1 - d / 500.0));   // càng gần càng đầy (500 khối trở ra = rỗng)
            bar(p, d <= digRadius ? "&c✹✹ Ngay dưới chân! &eNgồi xuống (Shift) để đào" : d < 30 ? "&c✹ Nóng lắm rồi (" + (int) d + " khối)"
                    : d < 100 ? "&6☀ Ấm dần…" : d < 300 ? "&b❄ Lạnh" : "&9❄❄ Rất xa", d <= digRadius ? 1f : prog,
                    d < 30 ? net.kyori.adventure.bossbar.BossBar.Color.RED : d < 100 ? net.kyori.adventure.bossbar.BossBar.Color.YELLOW
                    : net.kyori.adventure.bossbar.BossBar.Color.BLUE);
        }
        for (UUID id : new ArrayList<>(bars.keySet())) {
            if (!holding.contains(id)) {
                hideBar(id);
            }
        }
        long now = System.currentTimeMillis();
        for (Dig d : new ArrayList<>(digs.values())) {
            Player owner = Bukkit.getPlayer(d.player);
            if (!d.loc.isChunkLoaded()) {          // không ai quanh đó: lính canh đang "ngủ" cùng chunk, đừng tính là đã chết
                if (now > d.until && owner != null) {
                    fail(d, owner);
                }
                continue;
            }
            d.guards.removeIf(id -> {
                var e = Bukkit.getEntity(id);
                return e == null || e.isDead();
            });
            // 1/11 chủ server: "tranh" có thật. Người đào chết / rời đi không huỷ lượt: lính canh đứng nguyên, đồng hồ vẫn chạy.
            // Hạ hết lính: người hạ con cuối (đứng trong 40 khối) mở rương; không có thì người đào nếu đứng gần; không thì người gần nhất.
            // Hết giờ còn lính: người đào online thì huỷ + trả bản đồ; người đào đã thoát thì lính cứ đứng đó tới khi bị hạ (ai hạ
            // con cuối thì lấy) hoặc bị dọn; người đào vào lại sau giờ thì huỷ + trả bản đồ.
            if (d.guards.isEmpty()) {
                Player win = winner(d);
                if (win != null) {
                    finish(d, win);
                } else if (now > d.until || owner == null) {
                    if (owner != null) {
                        fail(d, owner);
                    } else {
                        digs.remove(d.player);   // lính bị dọn khi người đào không online: bản đồ mất
                    }
                }
            } else if (now > d.until && owner != null) {
                fail(d, owner);
            }
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) {
            return;
        }
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        String[] v = read(hand);
        if (v == null || digs.containsKey(p.getUniqueId()) || p.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        World w = Bukkit.getWorld(v[1]);
        if (w == null || p.getWorld() != w) {
            return;
        }
        Location target = new Location(w, Integer.parseInt(v[2]) + 0.5, Integer.parseInt(v[3]), Integer.parseInt(v[4]) + 0.5);
        double dx = p.getLocation().getX() - target.getX(), dz = p.getLocation().getZ() - target.getZ();
        if (dx * dx + dz * dz > digRadius * digRadius) {
            return;
        }
        Tier t = tiers.stream().filter(x -> x.id().equals(v[0])).findFirst().orElse(tiers.get(0));
        ItemStack map = hand.clone();
        map.setAmount(1);
        hand.setAmount(hand.getAmount() - 1);
        Location ground = w.getHighestBlockAt(target).getLocation().add(0.5, 1, 0.5);
        Dig d = new Dig(p.getUniqueId(), ground, t, map, System.currentTimeMillis() + guardMs);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < t.guards(); i++) {
            Location at = ground.clone().add(r.nextDouble(-5, 5), 0, r.nextDouble(-5, 5));
            at = w.getHighestBlockAt(at).getLocation().add(0.5, 1, 0.5);
            LivingEntity g = (LivingEntity) w.spawnEntity(at, t.types().get(r.nextInt(t.types().size())));
            g.customName(c("&c☠ Lính canh kho báu"));
            g.setCustomNameVisible(true);
            g.setRemoveWhenFarAway(false);
            g.getPersistentDataContainer().set(key, PersistentDataType.STRING, "linhcanh");
            var hp = g.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null) {
                hp.setBaseValue(hp.getBaseValue() * t.guardHealth());
                g.setHealth(hp.getValue());
            }
            if (g instanceof org.bukkit.entity.Mob mob) {
                mob.setTarget(p);
            }
            d.guards.add(g.getUniqueId());
        }
        digs.put(p.getUniqueId(), d);
        w.playSound(ground, Sound.ITEM_SHOVEL_FLATTEN, 1f, 0.7f);
        w.spawnParticle(Particle.BLOCK, ground, 30, 0.6, 0.2, 0.6, Material.DIRT.createBlockData());
        p.sendMessage(c("&6⛏ Đang đào kho báu " + t.name() + "&6! " + (t.guards() > 0 ? "&cHạ hết " + t.guards() + " lính canh trong "
                + guardMs / 60_000 + " phút &6để mở rương. Chết hay rời đi thì lính vẫn đứng đó: ai hạ con cuối (đứng trong 40 khối) sẽ lấy rương." : "")));
        if (t.broadcast()) {
            PlayerSettings.broadcast("sukien", c("&6⌖ &f" + p.getName() + " &6đang đào &e" + t.name() + " &6quanh &fX " + ground.getBlockX() / 50 * 50 + " Z "
                    + ground.getBlockZ() / 50 * 50 + "&6! Ai hạ được lính canh cuối cùng sẽ lấy rương."));
        }
    }

    private void finish(Dig d, Player p) {
        digs.remove(d.player);
        List<String> got = rewards == null ? List.of() : rewards.give(p, d.tier.rewards());
        d.loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, d.loc, 40, 0.5, 0.8, 0.5, 0.2);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        p.sendMessage(c("&a✔ Mở rương kho báu " + d.tier.name() + "&a: " + (got.isEmpty() ? "" : String.join("&a, ", got))));
        Achievements.add(p, "khobau", 1);
        Player owner = Bukkit.getPlayer(d.player);
        if (owner != null && owner != p) {
            owner.sendMessage(c("&c✘ " + p.getName() + " đã hạ lính canh cuối và lấy mất rương " + d.tier.name() + "&c."));
        }
        DungeonGate dg = Shared.dungeonGate;
        if (d.tier.revealHidden() > 0 && ThreadLocalRandom.current().nextDouble(100) < d.tier.revealHidden()) {
            WildGates wg = Shared.wildGates;
            boolean gateFirst = ThreadLocalRandom.current().nextBoolean();   // chỉ Cổng Hoang hoặc dungeon ẩn, cái nào còn thì chỉ cái đó
            boolean done = gateFirst && wg != null && wg.hint(p);
            if (!done) {
                done = dg != null && dg.revealHidden(p);
            }
            if (!done && !gateFirst && wg != null) {
                wg.hint(p);
            }
        }
        if (d.tier.broadcast()) {
            PlayerSettings.broadcast("sukien", c("&6⌖ &f" + p.getName() + " &6đã mở được &e" + d.tier.name() + "&6!"));
        }
    }

    private static boolean near(Player p, Dig d) {
        return p != null && !p.isDead() && p.getWorld() == d.loc.getWorld() && p.getLocation().distanceSquared(d.loc) < 40 * 40;
    }

    private Player winner(Dig d) {
        Player k = d.lastKiller == null ? null : Bukkit.getPlayer(d.lastKiller);
        if (near(k, d)) {
            return k;
        }
        Player owner = Bukkit.getPlayer(d.player);
        if (near(owner, d)) {
            return owner;
        }
        return d.loc.getWorld().getPlayers().stream().filter(o -> near(o, d) && o.getGameMode() == GameMode.SURVIVAL)
                .min(java.util.Comparator.comparingDouble(o -> o.getLocation().distanceSquared(d.loc))).orElse(null);
    }

    private void fail(Dig d, Player p) {
        digs.remove(d.player);
        d.guards.forEach(id -> {
            var e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        });
        if (p != null) {
            Rewards.hand(p, d.map);
            p.sendMessage(c("&c✘ Chưa giữ được kho báu (hết giờ hoặc rời xa). &7Đã trả lại bản đồ, thử lại sau."));
        }
    }

    /** Lính canh còn sót (đã hết lượt đào / server khởi động lại) khi vùng nạp lại: xoá. */
    @EventHandler
    public void onLoad(org.bukkit.event.world.EntitiesLoadEvent e) {
        for (var en : e.getEntities()) {
            if ("linhcanh".equals(en.getPersistentDataContainer().get(key, PersistentDataType.STRING))
                    && digs.values().stream().noneMatch(d -> d.guards.contains(en.getUniqueId()))) {
                en.remove();
            }
        }
    }

    // ---------------------------------------------------------------- lệnh

    boolean command(CommandSender s, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("give") && s.hasPermission("duckymomo.admin")) {
            Player t = Bukkit.getPlayerExact(args[1]);
            if (t == null || !give(t, false)) {
                s.sendMessage(c("&cKhông phát được (người chơi offline hoặc chưa có vùng /tpr nào đã mở)."));
            }
            return true;
        }
        if (s instanceof Player pm) {   // menu (22/10, trước in chat)
            InfoMenu m = InfoMenu.of("&6⌖ Bản đồ kho báu").accent(org.bukkit.Material.YELLOW_STAINED_GLASS_PANE);
            m.top(Tip.of("&6⌖ &fBản đồ kho báu").accent("&6").desc("Bản đồ dẫn tới rương báu có lính canh.").line()
                    .head("Nhặt được khi").item("Câu cá: " + fishChance + "%").item("Hạ quái: " + mobChance + "%").kv("Tối đa", maxPerDay + " bản đồ/ngày")
                    .gap().head("Cách đào").item("Cầm bản đồ: thanh dưới màn hình báo lạnh / ấm / nóng").item("Tới nơi thì ngồi xuống (Shift) để đào")
                    .item("Hạ hết lính canh để mở rương").apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.FILLED_MAP)));
            org.bukkit.Material[] icons = {org.bukkit.Material.CHEST, org.bukkit.Material.TRAPPED_CHEST, org.bukkit.Material.ENDER_CHEST, org.bukkit.Material.SHULKER_BOX};
            int k = 0;
            for (Tier t : tiers) {
                List<String> d = rewards == null ? t.rewards() : rewards.describe(t.rewards());
                Tip tt = Tip.of("&e" + t.name()).accent("&e").kv("Lính canh", String.valueOf(t.guards())).gap().head("Phần thưởng").items(d);
                if (t.broadcast()) {
                    tt.gap().raw("&c⚠ Báo cả server khi đào");
                }
                m.add(tt.apply(new org.bukkit.inventory.ItemStack(icons[Math.min(k++, icons.length - 1)])));
            }
            m.open(pm);
            return true;
        }
        s.sendMessage(c("&6⌖ Bản đồ kho báu"));
        s.sendMessage(c("&7Nhặt được khi &fcâu cá &7(" + fishChance + "%) hoặc &fhạ quái &7(" + mobChance + "%), tối đa " + maxPerDay + " bản đồ/ngày."));
        s.sendMessage(c("&7Cầm bản đồ trên tay: thanh dưới màn hình báo lạnh/ấm/nóng. Tới nơi thì ngồi xuống (Shift) để đào, hạ hết lính canh để mở rương."));
        for (Tier t : tiers) {
            List<String> d = rewards == null ? t.rewards() : rewards.describe(t.rewards());
            s.sendMessage(c("&e" + t.name() + "&7: " + t.guards() + " lính canh · " + String.join(", ", d) + (t.broadcast() ? " &c(báo cả server)" : "")));
        }
        return true;
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được khobau.yml: " + e.getMessage());
        }
    }

    private static Component c(String s) {
        return Tip.ns(LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false));
    }
}
