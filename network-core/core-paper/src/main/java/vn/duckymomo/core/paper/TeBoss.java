package vn.duckymomo.core.paper;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Đàn tế gọi boss (30/10, chủ server: "lập đàn hiến tế linh hồn để gọi boss… thêm vài kiểu cho đa dạng"). Mỗi đàn một kiểu:
 *   linhhon: giết quái thù địch trong bán kính radius quanh đàn = +1 linh hồn; đủ need thì boss hiện.
 *   levat:   cầm vật phẩm trong danh sách items, bấm chuột phải vào khối đàn để dâng (mỗi món N điểm); đủ need thì boss hiện.
 *   an:      quái thù địch (cả world) rơi "Mảnh Ấn Triệu Hồi" với tỉ lệ drop-chance; cầm đủ need mảnh bấm vào đàn = gọi boss ngay.
 *   lua:     4 lửa trại (đặt bằng /dmm dante lua <đàn> <1-4>); bấm thắp lửa, mỗi lần thắp quái canh hiện ra; thắp đủ 4 trong time giây
 *            thì boss hiện, quá giờ thì lửa tắt hết làm lại.
 * Boss: quái vanilla có máu / sát thương / cỡ (boss{type,name,hp,dmg,scale,speed}), thanh máu cho người trong bossbar-range, biến mất
 * sau despawn-minutes nếu chưa hạ. Thưởng khi hạ: xếp theo sát thương (top1 / top2 / top3) + ai góp (linh hồn, lễ vật, thắp lửa,
 * đánh) nhận all. Mỗi đàn nghỉ cooldown-minutes sau khi boss chết / biến mất. Bảng chữ nổi trên đàn hiện tiến độ.
 * Đặt đàn: nhìn vào khối làm đàn rồi /dmm dante dat <đàn>. ĐỪNG đặt trong thị trấn tắt quái (Towny xoá quái, cả boss) hay vùng
 * WorldGuard cấm sinh quái. Lưu vị trí + tiến độ: plugins/DuckyMoMoCore/teboss.yml.
 */
final class TeBoss implements Listener {

    private static final List<String> MODES = List.of("linhhon", "levat", "an", "lua");

    private record Boss(EntityType type, String name, double hp, double dmg, double scale, double speed) { }

    private static final class Dan {
        final String id;
        final String name;
        final String mode;
        final int need;
        final int radius;
        final int time;
        final int cooldownMin;
        final Boss boss;
        final Map<Material, Integer> items = new LinkedHashMap<>();
        final ConfigurationSection rewards;
        Location loc;
        final List<Location> fires = new ArrayList<>();
        int progress;
        long cooldownUntil;
        final Map<UUID, Integer> contrib = new HashMap<>();
        // boss đang sống
        UUID bossId;
        BossBar bar;
        long bossUntil;
        final Map<UUID, Double> damage = new HashMap<>();
        // kiểu lua
        long fireDeadline;
        TextDisplay label;

        Dan(String id, ConfigurationSection s) {
            this.id = id;
            this.name = s.getString("name", id);
            this.mode = s.getString("mode", "linhhon").toLowerCase(Locale.ROOT);
            this.need = Math.max(1, s.getInt("need", mode.equals("lua") ? 4 : 100));
            this.radius = Math.max(4, s.getInt("radius", 24));
            this.time = Math.max(15, s.getInt("time", 60));
            this.cooldownMin = Math.max(0, s.getInt("cooldown-minutes", 30));
            ConfigurationSection b = s.getConfigurationSection("boss");
            EntityType t = null;
            try {
                t = EntityType.valueOf(b == null ? "ZOMBIE" : b.getString("type", "ZOMBIE").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // giữ null, dưới đổi thành ZOMBIE
            }
            this.boss = new Boss(t == null ? EntityType.ZOMBIE : t, b == null ? "&cBoss" : b.getString("name", "&cBoss"),
                    b == null ? 500 : b.getDouble("hp", 500), b == null ? 10 : b.getDouble("dmg", 10),
                    b == null ? 1.5 : b.getDouble("scale", 1.5), b == null ? 0 : b.getDouble("speed", 0));
            ConfigurationSection it = s.getConfigurationSection("items");
            if (it != null) {
                for (String k : it.getKeys(false)) {
                    Material m = Material.matchMaterial(k);
                    if (m != null) {
                        items.put(m, Math.max(1, it.getInt(k, 1)));
                    }
                }
            }
            this.rewards = s.getConfigurationSection("rewards");
        }
    }

    private final JavaPlugin plugin;
    private final ConfigurationSection cfg;
    private final Rewards rewards;
    private final NamespacedKey tag;
    private final NamespacedKey shardKey;
    private final File file;
    private final YamlConfiguration data;
    private final Map<String, Dan> dans = new LinkedHashMap<>();
    private final double shardChance;
    private BukkitTask tick;

    TeBoss(JavaPlugin plugin, ConfigurationSection cfg, Rewards rewards) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.rewards = rewards;
        this.tag = new NamespacedKey(plugin, "teboss");
        this.shardKey = new NamespacedKey(plugin, "manh_an");
        this.file = new File(plugin.getDataFolder(), "teboss.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        this.shardChance = cfg.getDouble("an-drop-chance", 0.02);
        ConfigurationSection ds = cfg.getConfigurationSection("dan");
        if (ds != null) {
            for (String id : ds.getKeys(false)) {
                ConfigurationSection s = ds.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                Dan d = new Dan(id, s);
                if (!MODES.contains(d.mode)) {
                    plugin.getLogger().warning("Đàn tế " + id + ": kiểu lạ " + d.mode + " (" + String.join("|", MODES) + ")");
                    continue;
                }
                d.loc = data.getLocation("dan." + id + ".loc");
                for (Object o : data.getList("dan." + id + ".fires", List.of())) {
                    if (o instanceof Location l) {
                        d.fires.add(l);
                    }
                }
                d.progress = data.getInt("dan." + id + ".progress", 0);
                d.cooldownUntil = data.getLong("dan." + id + ".cooldown", 0);
                dans.put(id, d);
            }
        }
    }

    void start() {
        for (Dan d : dans.values()) {
            relabel(d);
        }
        tick = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    void stop() {
        if (tick != null) {
            tick.cancel();
        }
        for (Dan d : dans.values()) {
            despawnBoss(d, false);
            if (d.label != null) {
                d.label.remove();
            }
        }
        save();
    }

    private void save() {
        for (Dan d : dans.values()) {
            String k = "dan." + d.id;
            data.set(k + ".loc", d.loc);
            data.set(k + ".fires", d.fires);
            data.set(k + ".progress", d.progress);
            data.set(k + ".cooldown", d.cooldownUntil);
        }
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Đàn tế: không lưu được teboss.yml: " + e);
        }
    }

    // ---------------------------------------------------------------- bảng chữ trên đàn

    private String modeLine(Dan d) {
        return switch (d.mode) {
            case "levat" -> "&7[ Dâng lễ vật ]";
            case "an" -> "&7[ Ấn triệu hồi ]";
            case "lua" -> "&7[ Thắp 4 ngọn lửa ]";
            default -> "&7[ Lửa nghi lễ ]";
        };
    }

    private String labelText(Dan d) {
        StringBuilder sb = new StringBuilder();
        sb.append(d.name).append('\n').append(modeLine(d)).append('\n');
        long now = System.currentTimeMillis();
        if (d.bossId != null) {
            sb.append("&c⚔ Boss đang xuất hiện!\n&7Hạ trong &f").append(Math.max(0, (d.bossUntil - now) / 60000)).append(" phút");
        } else if (now < d.cooldownUntil) {
            sb.append("&7Đàn đang nghỉ, mở lại sau &f").append((d.cooldownUntil - now) / 60000 + 1).append(" phút");
        } else if (d.mode.equals("an")) {
            sb.append("&7Cầm &f").append(d.need).append(" Mảnh Ấn &7bấm vào đàn để gọi boss\n&7(mảnh rơi từ quái thù địch)");
        } else {
            int pct = Math.min(100, d.progress * 100 / d.need);
            int bars = pct / 10;
            String unit = switch (d.mode) {
                case "levat" -> "Lễ vật";
                case "lua" -> "Ngọn lửa";
                default -> "Linh hồn";
            };
            sb.append("&7").append(unit).append(": &f").append(d.progress).append("&7/&f").append(d.need).append(" &e").append(pct).append("%\n");
            sb.append("&6").append("■".repeat(bars)).append("&8").append("■".repeat(10 - bars)).append('\n');
            if (d.mode.equals("lua") && d.fireDeadline > 0) {
                sb.append("&cCòn &f").append(Math.max(0, (d.fireDeadline - now) / 1000)).append("s &cđể thắp hết!");
            } else {
                sb.append("&7Còn thiếu &f").append(Math.max(0, d.need - d.progress)).append(switch (d.mode) {
                    case "levat" -> " &7điểm lễ vật (bấm đàn khi cầm đồ)";
                    case "lua" -> " &7ngọn lửa (bấm lửa trại quanh đàn)";
                    default -> " &7linh hồn (hạ quái quanh đàn)";
                });
            }
        }
        return sb.toString();
    }

    private void relabel(Dan d) {
        if (d.loc == null || d.loc.getWorld() == null) {
            return;
        }
        if (d.label == null || !d.label.isValid()) {
            Location at = d.loc.clone().add(0.5, 2.6, 0.5);
            for (Entity e : at.getWorld().getNearbyEntities(at, 2, 3, 2)) {
                if (e instanceof TextDisplay && d.id.equals(e.getPersistentDataContainer().get(tag, PersistentDataType.STRING))) {
                    e.remove();
                }
            }
            d.label = at.getWorld().spawn(at, TextDisplay.class, t -> {
                t.setBillboard(Display.Billboard.CENTER);
                t.setPersistent(false);
                t.setSeeThrough(false);
                t.setShadowed(true);
                t.setBackgroundColor(org.bukkit.Color.fromARGB(110, 0, 0, 0));
                t.getPersistentDataContainer().set(tag, PersistentDataType.STRING, d.id);
            });
        }
        d.label.text(Tip.c(labelText(d)));
    }

    // ---------------------------------------------------------------- mỗi giây

    private void tick() {
        long now = System.currentTimeMillis();
        for (Dan d : dans.values()) {
            if (d.loc == null || !d.loc.isWorldLoaded() || !d.loc.getWorld().isChunkLoaded(d.loc.getBlockX() >> 4, d.loc.getBlockZ() >> 4)) {
                continue;
            }
            if (d.bossId != null) {
                Entity b = Bukkit.getEntity(d.bossId);
                if (!(b instanceof LivingEntity le) || !le.isValid()) {
                    // boss mất (dọn chunk, plugin khác xoá…): coi như biến mất
                    broadcast("&7" + strip(d.boss.name()) + " &7đã biến mất khỏi " + d.name + "&7.");
                    despawnBoss(d, true);
                } else if (now > d.bossUntil) {
                    broadcast("&7Hết giờ: " + d.boss.name() + " &7rút về cõi âm, " + d.name + " &7nghỉ một lúc.");
                    despawnBoss(d, true);
                } else {
                    AttributeInstance mh = le.getAttribute(Attribute.MAX_HEALTH);
                    d.bar.setProgress(Math.max(0, Math.min(1, le.getHealth() / (mh == null ? d.boss.hp() : mh.getValue()))));
                    int range = cfg.getInt("bossbar-range", 48);
                    for (Player p : b.getWorld().getPlayers()) {
                        boolean near = p.getLocation().distanceSquared(b.getLocation()) <= (double) range * range;
                        if (near && !d.bar.getPlayers().contains(p)) {
                            d.bar.addPlayer(p);
                        } else if (!near && d.bar.getPlayers().contains(p)) {
                            d.bar.removePlayer(p);
                        }
                    }
                    // boss chạy quá xa đàn thì kéo về
                    if (b.getLocation().distanceSquared(d.loc) > (double) (d.radius + 16) * (d.radius + 16)) {
                        b.teleport(d.loc.clone().add(0.5, 1, 0.5));
                    }
                }
            }
            if (d.mode.equals("lua") && d.fireDeadline > 0 && now > d.fireDeadline && d.bossId == null) {
                d.fireDeadline = 0;
                d.progress = 0;
                setFires(d, false);
                near(d, "&c✘ Hết giờ! Lửa ở " + d.name + " &cđã tắt, thắp lại từ đầu.");
            }
            relabel(d);
        }
    }

    // ---------------------------------------------------------------- góp tiến độ

    private void add(Dan d, Player p, int n) {
        if (d.bossId != null || System.currentTimeMillis() < d.cooldownUntil) {
            return;
        }
        d.progress = Math.min(d.need, d.progress + n);
        d.contrib.merge(p.getUniqueId(), n, Integer::sum);
        if (d.progress >= d.need) {
            summon(d, p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        // boss chết
        for (Dan d : dans.values()) {
            if (dead.getUniqueId().equals(d.bossId)) {
                e.getDrops().clear();
                e.setDroppedExp(Math.max(e.getDroppedExp(), 200));
                reward(d);
                despawnBoss(d, true);
                return;
            }
        }
        Player killer = dead.getKiller();
        if (killer == null || !(dead instanceof Monster) || BotView.isBot(killer)) {
            return;
        }
        // mảnh ấn rơi ở mọi nơi trong world có đàn kiểu "an"
        for (Dan d : dans.values()) {
            if (d.mode.equals("an") && d.loc != null && d.loc.getWorld() == dead.getWorld()
                    && ThreadLocalRandom.current().nextDouble() < shardChance) {
                e.getDrops().add(shard(1));
                break;
            }
        }
        for (Dan d : dans.values()) {
            if (d.mode.equals("linhhon") && d.loc != null && d.loc.getWorld() == dead.getWorld()
                    && dead.getLocation().distanceSquared(d.loc) <= (double) d.radius * d.radius) {
                if (d.bossId == null && System.currentTimeMillis() >= d.cooldownUntil) {
                    dead.getWorld().spawnParticle(Particle.SOUL, dead.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.02);
                    killer.sendActionBar(Tip.c("&b+1 linh hồn &7cho " + d.name + " &7(" + Math.min(d.need, d.progress + 1) + "/" + d.need + ")"));
                }
                add(d, killer, 1);
            }
        }
    }

    ItemStack shard(int n) {
        ItemStack it = new ItemStack(Material.ECHO_SHARD, n);
        ItemMeta m = it.getItemMeta();
        m.displayName(Tip.c("&dMảnh Ấn Triệu Hồi"));
        m.lore(List.of(Tip.c("&7Gom đủ rồi cầm trên tay, bấm vào"), Tip.c("&7đàn tế Ấn Triệu Hồi để gọi boss.")));
        m.getPersistentDataContainer().set(shardKey, PersistentDataType.BYTE, (byte) 1);
        m.setEnchantmentGlintOverride(true);
        it.setItemMeta(m);
        return it;
    }

    private boolean isShard(ItemStack it) {
        return it != null && it.getType() == Material.ECHO_SHARD && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(shardKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) {
            return;
        }
        Block b = e.getClickedBlock();
        Player p = e.getPlayer();
        for (Dan d : dans.values()) {
            if (d.loc == null) {
                continue;
            }
            if (d.mode.equals("lua")) {
                for (Location f : d.fires) {
                    if (f.getBlock().equals(b)) {
                        e.setCancelled(true);
                        light(d, p, b);
                        return;
                    }
                }
            }
            if (!d.loc.getBlock().equals(b)) {
                continue;
            }
            e.setCancelled(true);
            long now = System.currentTimeMillis();
            if (d.bossId != null) {
                p.sendMessage(Tip.c("&cBoss đang xuất hiện, hạ nó đi đã!"));
                return;
            }
            if (now < d.cooldownUntil) {
                p.sendMessage(Tip.c("&7" + d.name + " &7đang nghỉ, mở lại sau &f" + ((d.cooldownUntil - now) / 60000 + 1) + " phút&7."));
                return;
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            switch (d.mode) {
                case "levat" -> {
                    Integer pts = d.items.get(hand.getType());
                    if (pts == null || hand.hasItemMeta() && hand.getItemMeta().hasDisplayName()) {
                        List<String> names = new ArrayList<>();
                        d.items.forEach((m, v) -> names.add(Rewards.vi(m.name()) + " (" + v + ")"));
                        p.sendMessage(Tip.c("&6" + d.name + " &7nhận lễ vật: &f" + String.join("&7, &f", names)));
                        return;
                    }
                    int amount = hand.getAmount();
                    int room = (int) Math.ceil((d.need - d.progress) / (double) pts);
                    int use = Math.min(amount, Math.max(1, room));
                    hand.setAmount(amount - use);
                    p.playSound(b.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 1.2f);
                    b.getWorld().spawnParticle(Particle.FLAME, b.getLocation().add(0.5, 1.1, 0.5), 20, 0.3, 0.3, 0.3, 0.02);
                    p.sendActionBar(Tip.c("&6+" + use * pts + " điểm lễ vật &7(" + Math.min(d.need, d.progress + use * pts) + "/" + d.need + ")"));
                    add(d, p, use * pts);
                }
                case "an" -> {
                    if (!isShard(hand) || hand.getAmount() < d.need) {
                        p.sendMessage(Tip.c("&dCần cầm &f" + d.need + " Mảnh Ấn Triệu Hồi &dtrên tay (đang cầm "
                                + (isShard(hand) ? hand.getAmount() : 0) + "). &7Mảnh rơi ngẫu nhiên khi hạ quái thù địch."));
                        return;
                    }
                    hand.setAmount(hand.getAmount() - d.need);
                    d.contrib.merge(p.getUniqueId(), d.need, Integer::sum);
                    summon(d, p);
                }
                case "lua" -> p.sendMessage(Tip.c("&6Thắp đủ &f4 lửa trại &6quanh đàn trong &f" + d.time + " giây&6 (bấm chuột phải vào lửa trại)."));
                default -> p.sendMessage(Tip.c("&bHạ quái thù địch trong vòng &f" + d.radius + " khối &bquanh đàn để góp linh hồn."));
            }
            return;
        }
    }

    private void setFires(Dan d, boolean lit) {
        for (Location f : d.fires) {
            if (f.getBlock().getBlockData() instanceof Lightable l) {
                l.setLit(lit);
                f.getBlock().setBlockData(l);
            }
        }
    }

    private void light(Dan d, Player p, Block b) {
        long now = System.currentTimeMillis();
        if (d.bossId != null || now < d.cooldownUntil) {
            p.sendMessage(Tip.c("&7" + d.name + " &7chưa mở lại."));
            return;
        }
        if (!(b.getBlockData() instanceof Lightable l) || l.isLit()) {
            return;
        }
        l.setLit(true);
        b.setBlockData(l);
        if (d.fireDeadline == 0) {
            d.fireDeadline = now + d.time * 1000L;
            near(d, "&6♨ " + p.getName() + " &6đã thắp ngọn lửa đầu tiên ở " + d.name + "&6! Còn &f" + d.time + " giây &6để thắp hết.");
        }
        b.getWorld().playSound(b.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 0.8f);
        // quái canh lửa
        int guards = cfg.getInt("lua-guards", 2);
        for (int i = 0; i < guards; i++) {
            b.getWorld().spawnEntity(b.getLocation().add(ThreadLocalRandom.current().nextInt(-3, 4), 1, ThreadLocalRandom.current().nextInt(-3, 4)),
                    i % 2 == 0 ? EntityType.ZOMBIE : EntityType.SKELETON);
        }
        int lit = 0;
        for (Location f : d.fires) {
            if (f.getBlock().getBlockData() instanceof Lightable x && x.isLit()) {
                lit++;
            }
        }
        d.progress = lit;
        d.contrib.merge(p.getUniqueId(), 1, Integer::sum);
        if (lit >= Math.min(d.need, Math.max(1, d.fires.size()))) {
            d.fireDeadline = 0;
            summon(d, p);
        }
    }

    // ---------------------------------------------------------------- boss

    private void summon(Dan d, Player who) {
        Location at = d.loc.clone().add(0.5, 1, 0.5);
        Entity e = at.getWorld().spawnEntity(at, d.boss.type());
        if (!(e instanceof LivingEntity le)) {
            e.remove();
            return;
        }
        le.customName(Tip.c(d.boss.name()));
        le.setCustomNameVisible(true);
        le.setRemoveWhenFarAway(false);
        le.setPersistent(false);
        le.setGlowing(true);
        le.getPersistentDataContainer().set(tag, PersistentDataType.STRING, d.id);
        AttributeInstance mh = le.getAttribute(Attribute.MAX_HEALTH);
        if (mh != null) {
            mh.setBaseValue(d.boss.hp());
            le.setHealth(d.boss.hp());
        }
        AttributeInstance dmg = le.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmg != null) {
            dmg.setBaseValue(d.boss.dmg());
        }
        if (d.boss.scale() != 1.0 && le.getAttribute(Attribute.SCALE) != null) {
            le.getAttribute(Attribute.SCALE).setBaseValue(d.boss.scale());
        }
        if (d.boss.speed() > 0 && le.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
            le.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(d.boss.speed());
        }
        AttributeInstance kb = le.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (kb != null) {
            kb.setBaseValue(0.8);
        }
        d.bossId = le.getUniqueId();
        d.bossUntil = System.currentTimeMillis() + cfg.getInt("despawn-minutes", 10) * 60000L;
        d.damage.clear();
        d.bar = Bukkit.createBossBar(strip(d.boss.name()), BarColor.RED, BarStyle.SEGMENTED_10);
        d.progress = 0;
        at.getWorld().strikeLightningEffect(at);
        at.getWorld().playSound(at, Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.8f);
        String where = "x=" + d.loc.getBlockX() + " y=" + d.loc.getBlockY() + " z=" + d.loc.getBlockZ();
        broadcast("&c☠ " + d.boss.name() + " &cđã bị gọi lên ở " + d.name + " &7(" + where + ")&c bởi &e" + who.getName() + "&c! Tới hạ boss nhận thưởng!");
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(at) <= 64 * 64) {
                ScreenNotify.show(p, "&c☠ Boss xuất hiện!", d.boss.name());
            }
        }
        save();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        UUID id = e.getEntity().getUniqueId();
        for (Dan d : dans.values()) {
            if (!id.equals(d.bossId)) {
                continue;
            }
            Entity src = e.getDamager();
            if (src instanceof Projectile pr && pr.getShooter() instanceof Entity sh) {
                src = sh;
            }
            if (src instanceof org.bukkit.entity.Tameable t && t.getOwner() instanceof Player o) {
                src = o;   // pet đánh tính cho chủ
            }
            if (src instanceof Player p) {
                d.damage.merge(p.getUniqueId(), e.getFinalDamage(), Double::sum);
            }
            return;
        }
    }

    private void reward(Dan d) {
        List<Map.Entry<UUID, Double>> rank = new ArrayList<>(d.damage.entrySet());
        rank.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        StringBuilder top = new StringBuilder();
        java.util.Set<UUID> given = new java.util.HashSet<>();
        for (int i = 0; i < rank.size() && i < 3; i++) {
            Player p = Bukkit.getPlayer(rank.get(i).getKey());
            if (p == null) {
                continue;
            }
            List<String> got = rewards.give(p, d.rewards == null ? List.of() : d.rewards.getStringList("top" + (i + 1)));
            given.add(p.getUniqueId());
            top.append("\n&6#").append(i + 1).append(" &e").append(p.getName()).append(" &7(").append(Math.round(rank.get(i).getValue()))
                    .append(" sát thương)").append(got.isEmpty() ? "" : " &a" + String.join("&7, &a", got));
        }
        java.util.Set<UUID> all = new java.util.HashSet<>(d.damage.keySet());
        all.addAll(d.contrib.keySet());
        for (UUID u : all) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && !given.contains(u)) {
                rewards.give(p, d.rewards == null ? List.of() : d.rewards.getStringList("all"));
            }
        }
        broadcast("&a✔ " + d.boss.name() + " &ađã bị hạ ở " + d.name + "&a!" + top);
    }

    private void despawnBoss(Dan d, boolean cooldown) {
        if (d.bossId != null) {
            Entity b = Bukkit.getEntity(d.bossId);
            if (b != null) {
                b.remove();
            }
        }
        if (d.bar != null) {
            d.bar.removeAll();
        }
        boolean had = d.bossId != null;
        d.bossId = null;
        d.bar = null;
        d.contrib.clear();
        d.damage.clear();
        d.progress = 0;
        if (d.mode.equals("lua")) {
            setFires(d, false);
            d.fireDeadline = 0;
        }
        if (cooldown && had) {
            d.cooldownUntil = System.currentTimeMillis() + d.cooldownMin * 60000L;
        }
        save();
    }

    // ---------------------------------------------------------------- tiện ích

    private static String strip(String s) {
        return org.bukkit.ChatColor.stripColor(org.bukkit.ChatColor.translateAlternateColorCodes('&', s));
    }

    private static void broadcast(String msg) {
        for (String line : msg.split("\n")) {
            PlayerSettings.broadcast("sukien", Tip.c(line));
        }
    }

    private void near(Dan d, String msg) {
        for (Player p : d.loc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(d.loc) <= 64 * 64) {
                p.sendMessage(Tip.c(msg));
            }
        }
    }

    List<String> ids() {
        return new ArrayList<>(dans.keySet());
    }

    /** /dmm dante dat <đàn> | lua <đàn> <1-4> | goi <đàn> | huy <đàn> | xem | manh <người> <số> */
    void command(CommandSender s, String[] a) {
        String sub = a.length >= 2 ? a[1].toLowerCase(Locale.ROOT) : "";
        Dan d = a.length >= 3 ? dans.get(a[2].toLowerCase(Locale.ROOT)) : null;
        switch (sub) {
            case "dat" -> {
                if (!(s instanceof Player p) || d == null) {
                    s.sendMessage(Tip.c("&eVào game, nhìn vào khối làm đàn: /dmm dante dat <" + String.join("|", dans.keySet()) + ">"));
                    return;
                }
                Block b = p.getTargetBlockExact(6);
                if (b == null) {
                    s.sendMessage(Tip.c("&cNhìn vào một khối trong 6 khối."));
                    return;
                }
                if (d.label != null) {
                    d.label.remove();
                    d.label = null;
                }
                d.loc = b.getLocation();
                save();
                relabel(d);
                s.sendMessage(Tip.c("&a✔ Đã đặt " + d.name + " &a(" + d.mode + ") ở " + b.getX() + " " + b.getY() + " " + b.getZ()
                        + (d.mode.equals("lua") ? ". &eGiờ đặt 4 lửa trại quanh đàn rồi nhìn từng cái: /dmm dante lua " + d.id + " <1-4>" : "")
                        + " &7(nhớ: không đặt trong thị trấn tắt quái)"));
            }
            case "lua" -> {
                if (!(s instanceof Player p) || d == null || !d.mode.equals("lua") || a.length < 4) {
                    s.sendMessage(Tip.c("&e/dmm dante lua <đàn kiểu lua> <1-4> &7nhìn vào lửa trại"));
                    return;
                }
                Block b = p.getTargetBlockExact(6);
                if (b == null || !(b.getBlockData() instanceof Lightable)) {
                    s.sendMessage(Tip.c("&cNhìn vào một lửa trại (campfire)."));
                    return;
                }
                int i = Math.max(1, Math.min(4, Integer.parseInt(a[3]))) - 1;
                while (d.fires.size() <= i) {
                    d.fires.add(b.getLocation());
                }
                d.fires.set(i, b.getLocation());
                Lightable l = (Lightable) b.getBlockData();
                l.setLit(false);
                b.setBlockData(l);
                save();
                s.sendMessage(Tip.c("&a✔ Lửa " + (i + 1) + " của " + d.name + " &ađã đặt (" + d.fires.size() + "/4)."));
            }
            case "goi" -> {
                if (d == null || d.loc == null || !(s instanceof Player p)) {
                    s.sendMessage(Tip.c("&e/dmm dante goi <đàn đã đặt> &7(gọi boss ngay để thử)"));
                    return;
                }
                d.cooldownUntil = 0;
                summon(d, p);
            }
            case "huy" -> {
                if (d == null) {
                    s.sendMessage(Tip.c("&e/dmm dante huy <đàn> &7xoá boss + tiến độ + thời gian nghỉ"));
                    return;
                }
                despawnBoss(d, false);
                d.cooldownUntil = 0;
                save();
                s.sendMessage(Tip.c("&7Đã đặt lại " + d.name + "&7."));
            }
            case "manh" -> {
                Player t = a.length >= 3 ? Bukkit.getPlayerExact(a[2]) : null;
                if (t == null) {
                    s.sendMessage(Tip.c("&e/dmm dante manh <người online> [số] &7tặng Mảnh Ấn Triệu Hồi"));
                    return;
                }
                int n = a.length >= 4 ? Math.max(1, Integer.parseInt(a[3])) : 1;
                t.getInventory().addItem(shard(n)).values().forEach(x -> t.getWorld().dropItem(t.getLocation(), x));
                s.sendMessage(Tip.c("&aĐã tặng " + n + " Mảnh Ấn cho " + t.getName()));
            }
            default -> {
                s.sendMessage(Tip.c("&6Đàn tế: &e/dmm dante dat|lua|goi|huy|manh"));
                for (Dan x : dans.values()) {
                    s.sendMessage(Tip.c("&7• &f" + x.id + " &7(" + x.mode + ") " + x.name + " &7" + (x.loc == null ? "&cchưa đặt"
                            : x.loc.getBlockX() + " " + x.loc.getBlockY() + " " + x.loc.getBlockZ() + " &7tiến độ " + x.progress + "/" + x.need)
                            + (x.bossId != null ? " &cboss đang sống" : "")));
                }
            }
        }
    }
}
