package vn.duckymomo.core.paper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Bảng truy nã quái (/truyna, Bang Hội Chiến). Config wanted:
 *  - Mỗi interval-minutes phút, nếu chưa đủ max-active lệnh truy nã thì dán một lệnh mới: chọn ngẫu nhiên một mục trong targets
 *    {name, type, health (máu), damage (nhân sát thương), armor (none|iron|diamond|netherite), rewards} và một điểm đất hoang trong vùng /tpr đã mở.
 *  - Quái chỉ xuất hiện khi có người chơi tới gần (spawn-distance khối), phát sáng, không tự biến mất. Hết expire-minutes phút chưa ai hạ thì trốn mất.
 *  - Ai gây nhiều sát thương nhất (kể cả qua pet, tên…: Culprit) nhận rewards; báo cả server. Đếm vào Sổ thành tích (truyna).
 */
final class WantedBoard implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private record Target(String name, EntityType type, double health, double damage, String armor, List<String> rewards) { }
    private record Zone(String name, String requires, int x, int z, int r) { }

    private static final class Post {
        final Target target;
        final Location loc;
        final String zone;
        final long until;
        UUID entity;
        final Map<UUID, Double> damage = new HashMap<>();

        Post(Target target, Location loc, String zone, long until) {
            this.target = target;
            this.loc = loc;
            this.zone = zone;
            this.until = until;
        }
    }

    private final DuckyMoMoCore plugin;
    private final Rewards rewards;
    private final String world;
    private final long intervalMs;
    private final long expireMs;
    private final int maxActive;
    private final int spawnDistance;
    private final List<Target> targets = new ArrayList<>();
    private final List<Zone> zones = new ArrayList<>();
    private final List<Post> posts = new ArrayList<>();
    private final NamespacedKey key;
    private long lastPost;

    WantedBoard(DuckyMoMoCore plugin, Rewards rewards, ConfigurationSection c) {
        this.plugin = plugin;
        this.rewards = rewards;
        this.world = plugin.getConfig().getString("rtp.world", "world");
        this.intervalMs = Math.max(1, c.getLong("interval-minutes", 45)) * 60_000L;
        this.expireMs = Math.max(5, c.getLong("expire-minutes", 60)) * 60_000L;
        this.maxActive = Math.max(1, c.getInt("max-active", 2));
        this.spawnDistance = Math.max(16, c.getInt("spawn-distance", 64));
        for (Map<?, ?> m : c.getMapList("targets")) {
            try {
                List<String> rw = new ArrayList<>();
                if (m.get("rewards") instanceof List<?> l) {
                    l.forEach(x -> rw.add(String.valueOf(x)));
                }
                targets.add(new Target(String.valueOf(m.get("name")), EntityType.valueOf(String.valueOf(m.get("type")).toUpperCase(Locale.ROOT)),
                        num(m.get("health"), 100), num(m.get("damage"), 1.5), String.valueOf(m.containsKey("armor") ? m.get("armor") : "iron"), rw));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Truy nã: mục sai " + m + ": " + e.getMessage());
            }
        }
        for (Map<?, ?> z : plugin.getConfig().getMapList("rtp.zones")) {
            if (z.get("radius") instanceof Number r && r.intValue() > 0) {
                zones.add(new Zone(String.valueOf(z.get("name")), z.get("requires") == null ? "" : String.valueOf(z.get("requires")),
                        ((Number) z.get("center-x")).intValue(), ((Number) z.get("center-z")).intValue(), r.intValue()));
            }
        }
        this.key = new NamespacedKey(plugin, "dmm_truyna");
        this.lastPost = System.currentTimeMillis() - intervalMs + 120_000L;   // lệnh đầu tiên khoảng 2 phút sau khi bật
    }

    private static double num(Object o, double d) {
        return o instanceof Number n ? n.doubleValue() : d;
    }

    boolean ready() {
        return !targets.isEmpty() && !zones.isEmpty();
    }

    String describe() {
        return targets.size() + " loại quái, " + intervalMs / 60_000 + " phút/lệnh, tối đa " + maxActive + " lệnh cùng lúc";
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 100L);
    }

    void stop() {
        for (Post p : posts) {
            Entity e = p.entity == null ? null : Bukkit.getEntity(p.entity);
            if (e != null) {
                e.remove();
            }
        }
        posts.clear();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Post p : new ArrayList<>(posts)) {
            Entity e = p.entity == null ? null : Bukkit.getEntity(p.entity);
            if (now > p.until) {
                if (e != null) {
                    e.remove();
                }
                posts.remove(p);
                Bukkit.broadcast(c("&8☠ " + p.target.name() + " &7đã trốn khỏi " + p.zone + "&7. Lệnh truy nã hết hạn."));
                continue;
            }
            if (p.entity == null && p.loc.isChunkLoaded() && nearby(p.loc)) {
                spawn(p);
            }
        }
        if (posts.size() < maxActive && now - lastPost >= intervalMs) {
            lastPost = now;
            post();
        }
    }

    private boolean nearby(Location l) {
        for (Player pl : l.getWorld().getPlayers()) {
            if (pl.getLocation().distanceSquared(l) <= (double) spawnDistance * spawnDistance) {
                return true;
            }
        }
        return false;
    }

    private void post() {
        World w = Bukkit.getWorld(world);
        List<Zone> open = zones.stream().filter(z -> Shared.unlocked(z.requires())).toList();
        if (w == null || open.isEmpty() || targets.isEmpty()) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 20; i++) {
            Zone z = open.get(r.nextInt(open.size()));
            double a = r.nextDouble(Math.PI * 2), d = Math.sqrt(r.nextDouble()) * z.r() * 0.85;
            int x = z.x() + (int) (Math.cos(a) * d), zz = z.z() + (int) (Math.sin(a) * d);
            Block top = w.getHighestBlockAt(x, zz);
            if (top.isLiquid() || top.getY() < w.getSeaLevel() - 2) {
                continue;
            }
            Location l = top.getLocation().add(0.5, 1, 0.5);
            if (Bukkit.getPluginManager().isPluginEnabled("Towny") && !com.palmergames.bukkit.towny.TownyAPI.getInstance().isWilderness(l)) {
                continue;
            }
            Target t = targets.get(r.nextInt(targets.size()));
            Post p = new Post(t, l, z.name(), System.currentTimeMillis() + expireMs);
            posts.add(p);
            List<String> d2 = rewards == null ? t.rewards() : rewards.describe(t.rewards());
            Bukkit.broadcast(c("&4☠ TRUY NÃ: &c" + t.name() + " &7ở &f" + z.name() + " &7quanh &f~" + Math.round(l.getX() / 50) * 50 + ", ~"
                    + Math.round(l.getZ() / 50) * 50 + " &7— thưởng: &e" + String.join(", ", d2) + " &7(" + expireMs / 60_000 + " phút). &f/truyna"));
            return;
        }
    }

    private void spawn(Post p) {
        LivingEntity e = (LivingEntity) p.loc.getWorld().spawnEntity(p.loc, p.target.type());
        e.customName(c("&4☠ &c" + p.target.name()));
        e.setCustomNameVisible(true);
        e.setGlowing(true);
        e.setRemoveWhenFarAway(false);
        e.getPersistentDataContainer().set(key, PersistentDataType.STRING, "1");
        AttributeInstance hp = e.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(p.target.health());
            e.setHealth(p.target.health());
        }
        AttributeInstance dmg = e.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmg != null) {
            dmg.setBaseValue(dmg.getBaseValue() * p.target.damage());
        }
        EntityEquipment eq = e.getEquipment();
        String a = p.target.armor().toLowerCase(Locale.ROOT);
        if (eq != null && !a.equals("none")) {
            String m = a.equals("netherite") ? "NETHERITE" : a.equals("diamond") ? "DIAMOND" : "IRON";
            eq.setHelmet(new ItemStack(Material.valueOf(m + "_HELMET")));
            eq.setChestplate(new ItemStack(Material.valueOf(m + "_CHESTPLATE")));
            eq.setLeggings(new ItemStack(Material.valueOf(m + "_LEGGINGS")));
            eq.setBoots(new ItemStack(Material.valueOf(m + "_BOOTS")));
            eq.setHelmetDropChance(0);
            eq.setChestplateDropChance(0);
            eq.setLeggingsDropChance(0);
            eq.setBootsDropChance(0);
        }
        p.entity = e.getUniqueId();
        for (Player pl : p.loc.getWorld().getPlayers()) {
            if (pl.getLocation().distanceSquared(p.loc) < (double) spawnDistance * spawnDistance) {
                pl.sendMessage(c("&4☠ " + p.target.name() + " &cđã xuất hiện gần bạn!"));
            }
        }
    }

    private Post postOf(Entity e) {
        for (Post p : posts) {
            if (e.getUniqueId().equals(p.entity)) {
                return p;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Post p = postOf(e.getEntity());
        Player by = p == null ? null : Culprit.of(e.getDamager());
        if (by != null) {
            p.damage.merge(by.getUniqueId(), e.getFinalDamage(), Double::sum);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        Post p = postOf(e.getEntity());
        if (p == null) {
            return;
        }
        posts.remove(p);
        e.getDrops().clear();
        UUID best = p.damage.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        Player w = best == null ? null : Bukkit.getPlayer(best);
        if (w == null) {
            Bukkit.broadcast(c("&8☠ " + p.target.name() + " &7đã bị hạ nhưng không ai nhận thưởng."));
            return;
        }
        List<String> got = rewards == null ? List.of() : rewards.give(w, p.target.rewards());
        Achievements.add(w, "truyna", 1);
        Bukkit.broadcast(c("&6☠ &f" + w.getName() + " &6đã hạ &c" + p.target.name() + " &6ở " + p.zone + "&6! " + (got.isEmpty() ? "" : "&7(" + String.join(", ", got) + "&7)")));
    }

    /** Quái truy nã còn sót (lệnh đã hết / server khởi động lại) khi vùng nạp lại: xoá. */
    @EventHandler
    public void onLoad(org.bukkit.event.world.EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            if (en.getPersistentDataContainer().has(key) && postOf(en) == null) {
                en.remove();
            }
        }
    }

    boolean command(CommandSender s, String[] args) {
        s.sendMessage(c("&4&l☠ Bảng truy nã &7(lệnh mới mỗi " + intervalMs / 60_000 + " phút, quái xuất hiện khi có người tới gần)"));
        if (posts.isEmpty()) {
            s.sendMessage(c("&7Hiện chưa có lệnh truy nã nào."));
        }
        long now = System.currentTimeMillis();
        for (Post p : posts) {
            List<String> d = rewards == null ? p.target.rewards() : rewards.describe(p.target.rewards());
            s.sendMessage(c("&c" + p.target.name() + " &7ở &f" + p.zone + " &7quanh &f~" + Math.round(p.loc.getX() / 50) * 50 + ", ~" + Math.round(p.loc.getZ() / 50) * 50
                    + " &7· còn " + (p.until - now) / 60_000 + " phút · thưởng &e" + String.join(", ", d)));
        }
        s.sendMessage(c("&7Người gây nhiều sát thương nhất (kể cả qua pet) nhận thưởng. Tới vùng bằng &f/tpr&7."));
        return true;
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
}
