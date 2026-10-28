package vn.duckymomo.core.paper;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Thân nhiệt (/nhietdo, 28/10, chủ server: "thời tiết khắc nghiệt … mang theo vật giữ ấm … đá giữ nhiệt … ô cho mùa mưa").
 * Giữ 2 mùa mưa/khô; thêm đợt Rét đậm (mùa mưa) và Nắng nóng (mùa khô) trong seasons.events (khoá temperature).
 * Mỗi giây: nhiệt độ môi trường = nhiệt của mùa/đợt + quần xã (tuyết lạnh, sa mạc nóng) + đêm/nắng + ướt + nguồn nhiệt/băng gần,
 * dưới mái che kéo về dễ chịu; thân nhiệt đuổi dần theo. Ngoài vùng dễ chịu: lạnh (màn hình đóng băng, chậm, đói nhanh),
 * nóng (yếu sức, đói nhanh); quá mức thì mất máu nhưng dừng ở min-health (chủ server: không chết vì nhiệt).
 * Đỡ: Đá Ôn Linh (để cạnh lửa thì nóng lên, cạnh băng/tuyết thì lạnh đi, nguội dần; mang trong túi), ô lá (Lá Nhỏ Giọt cầm tay:
 * không ướt khi mưa, che nắng), giáp da, đồ ăn nóng (súp) / mát (dưa hấu, nước). Miễn: bot botfarm, người mới, creative/spectator,
 * thế giới ngoài body-temp.worlds. Config: body-temp.
 */
final class BodyTemp implements Listener {
    private final JavaPlugin plugin;
    private final ConfigurationSection cfg;
    private final NamespacedKey stoneKey;
    private final NamespacedKey bodyKey;
    private final NamespacedKey wetKey;
    private final Map<UUID, Double> body = new HashMap<>();
    private final Map<UUID, Double> wet = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Long> warned = new HashMap<>();
    private final Map<UUID, Double> lastEnv = new HashMap<>();
    /** nhiệt chính xác của từng viên đá theo ô túi đồ (trên món đồ chỉ ghi khi đổi 1 độ, đỡ gửi lại túi đồ mỗi giây) */
    private final Map<UUID, Map<Integer, Double>> precise = new HashMap<>();
    private final Set<String> worlds = new HashSet<>();
    private long ticks;

    BodyTemp(JavaPlugin plugin, ConfigurationSection cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.stoneKey = new NamespacedKey(plugin, "daonlinh");
        this.bodyKey = new NamespacedKey(plugin, "thannhiet");
        this.wetKey = new NamespacedKey(plugin, "uot");
        worlds.addAll(cfg.getStringList("worlds"));
        if (worlds.isEmpty()) {
            worlds.add("world");
        }
        recipes();
        for (Player p : Bukkit.getOnlinePlayers()) {
            load(p);
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    // ------------------------------------------------------------------ đồ
    private void recipes() {
        NamespacedKey k = new NamespacedKey(plugin, "daonlinh");
        Bukkit.removeRecipe(k);
        // 28/10: không theo hình: đủ 4 đá (Đá hoặc Đá trơn) + 4 đá lửa + 1 sắt, ô nào cũng được
        org.bukkit.inventory.ShapelessRecipe r = new org.bukkit.inventory.ShapelessRecipe(k, stone(cfg.getDouble("comfort-mid", 22)));
        RecipeChoice da = new RecipeChoice.MaterialChoice(Material.STONE, Material.SMOOTH_STONE);   // chủ server đặt Đá thường: cả hai đều được
        for (int i = 0; i < 4; i++) {
            r.addIngredient(da);
        }
        r.addIngredient(4, Material.FLINT);
        r.addIngredient(1, Material.IRON_INGOT);
        Bukkit.addRecipe(r);
        // Ô lá: Minecraft không có cái ô; Lá Nhỏ Giọt cầm tay trông như ô lá. Ngoài hang Lush còn chế được: 3 lá cây + 2 que
        NamespacedKey u = new NamespacedKey(plugin, "ola");
        Bukkit.removeRecipe(u);
        ShapedRecipe o = new ShapedRecipe(u, new ItemStack(Material.BIG_DRIPLEAF));
        o.shape("LLL", " S ", " S ");
        o.setIngredient('L', new RecipeChoice.MaterialChoice(Tag.LEAVES));
        o.setIngredient('S', Material.STICK);
        Bukkit.addRecipe(o);
    }

    ItemStack stone(double t) {
        ItemStack it = new ItemStack(Material.FLINT);
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(stoneKey, PersistentDataType.DOUBLE, t);
        m.setMaxStackSize(1);
        decorate(m, t);
        it.setItemMeta(m);
        return it;
    }

    private void decorate(ItemMeta m, double t) {
        String state = t >= 40 ? "&cđang nóng" : t <= 8 ? "&bđang lạnh" : "&7nguội";
        m.displayName(Tip.c("&6Đá Ôn Linh &8(" + state + "&8)").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String s : List.of(
                "&7Nhiệt độ đá: &f" + Math.round(t) + "°",
                "&8▍&7Để cạnh lửa / lửa trại / dung nham: đá nóng lên,",
                "&8▍&7mang theo sưởi ấm khi trời lạnh.",
                "&8▍&7Để cạnh băng / tuyết: đá lạnh đi, mang theo khi nóng.",
                "&8▍&7Đá nguội dần về nhiệt độ xung quanh.",
                "&8• &7Chỉ cần nằm trong túi đồ")) {
            lore.add(Tip.c(s).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        }
        m.lore(lore);
        String model = t >= 40 ? "magma_block" : t <= 8 ? "packed_ice" : "smooth_stone";
        m.setItemModel(NamespacedKey.minecraft(model));
    }

    private boolean isStone(ItemStack it) {
        return it != null && it.getType() == Material.FLINT && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(stoneKey, PersistentDataType.DOUBLE);
    }

    /** Đá Ôn Linh gốc là đá lửa: không cho dùng làm đá lửa trong công thức khác. */
    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        if (e.getRecipe() == null) {
            return;
        }
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (isStone(it)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ trạng thái
    private void load(Player p) {
        var pdc = p.getPersistentDataContainer();
        body.put(p.getUniqueId(), pdc.getOrDefault(bodyKey, PersistentDataType.DOUBLE, cfg.getDouble("comfort-mid", 22)));
        wet.put(p.getUniqueId(), pdc.getOrDefault(wetKey, PersistentDataType.DOUBLE, 0.0));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        load(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID u = p.getUniqueId();
        Double b = body.remove(u);
        Double w = wet.remove(u);
        if (b != null) {
            p.getPersistentDataContainer().set(bodyKey, PersistentDataType.DOUBLE, b);
            p.getPersistentDataContainer().set(wetKey, PersistentDataType.DOUBLE, w == null ? 0 : w);
        }
        BossBar bar = bars.remove(u);
        if (bar != null) {
            bar.removeAll();
        }
        warned.remove(u);
        lastEnv.remove(u);
        precise.remove(u);
    }

    /** Có bị tính thân nhiệt không. */
    private boolean affected(Player p) {
        if (BotView.isBot(p) || !worlds.contains(p.getWorld().getName())) {
            return false;
        }
        GameMode g = p.getGameMode();
        if (g != GameMode.SURVIVAL && g != GameMode.ADVENTURE) {
            return false;
        }
        double newbie = cfg.getDouble("newbie-hours", 2);
        return p.getStatistic(Statistic.PLAY_ONE_MINUTE) >= newbie * 72000;
    }

    private static boolean umbrella(Player p) {
        return p.getInventory().getItemInMainHand().getType() == Material.BIG_DRIPLEAF
                || p.getInventory().getItemInOffHand().getType() == Material.BIG_DRIPLEAF;
    }

    private static boolean covered(Player p) {
        Location eye = p.getEyeLocation();
        return p.getWorld().getHighestBlockYAt(eye) > eye.getBlockY();
    }

    /** Nguồn nhiệt (+) và nguồn lạnh (−) quanh chỗ l trong bán kính r. */
    private double[] sources(Location l, int r) {
        double heat = 0;
        double cold = 0;
        World w = l.getWorld();
        int bx = l.getBlockX();
        int by = l.getBlockY();
        int bz = l.getBlockZ();
        for (int x = -r; x <= r; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -r; z <= r; z++) {
                    Block b = w.getBlockAt(bx + x, by + y, bz + z);
                    Material m = b.getType();
                    switch (m) {
                        case FIRE, SOUL_FIRE, LAVA -> heat = Math.max(heat, 1);
                        case CAMPFIRE, SOUL_CAMPFIRE, FURNACE, BLAST_FURNACE, SMOKER -> {
                            if (b.getBlockData() instanceof Lightable lt && lt.isLit()) {
                                heat = Math.max(heat, 1);
                            }
                        }
                        case MAGMA_BLOCK -> heat = Math.max(heat, 0.6);
                        case ICE, PACKED_ICE, BLUE_ICE, SNOW_BLOCK, POWDER_SNOW -> cold = Math.max(cold, m == Material.BLUE_ICE ? 1 : 0.7);
                        default -> {
                        }
                    }
                }
            }
        }
        return new double[]{heat, cold};
    }

    private static boolean night(World w) {
        long t = w.getTime() % 24000;
        return t >= 13000 && t < 23000;
    }

    /** Nhiệt độ môi trường quanh p (chưa tính Đá Ôn Linh). parts nhận từng thành phần để /nhietdo giải thích (có thể null). */
    private double environment(Player p, double heat, double cold, List<String> parts) {
        double t = Seasons.temperature();
        add(parts, "Mùa / đợt", t, true);
        Location l = p.getLocation();
        double bt = l.getBlock().getTemperature();
        double biome = (bt - 0.8) * cfg.getDouble("biome-scale", 15);
        t += biome;
        add(parts, "Vùng đất", biome, false);
        boolean cov = covered(p);
        boolean umb = umbrella(p);
        World w = p.getWorld();
        if (night(w)) {
            double n = -cfg.getDouble("night", 6);
            t += n;
            add(parts, "Ban đêm", n, false);
        } else if (!cov && !w.hasStorm()) {
            double s = umb ? cfg.getDouble("sun", 4) / 3 : cfg.getDouble("sun", 4);
            t += s;
            add(parts, umb ? "Nắng (có ô che)" : "Nắng", s, false);
        }
        if (p.isInWater()) {
            double v = -cfg.getDouble("water", 8);
            t += v;
            add(parts, "Dưới nước", v, false);
        }
        double wv = -Math.min(10, wet.getOrDefault(p.getUniqueId(), 0.0) / 10);
        if (wv < -0.5) {
            t += wv;
            add(parts, "Người ướt", wv, false);
        }
        int leather = 0;
        for (ItemStack a : p.getInventory().getArmorContents()) {
            if (a != null && a.getType().name().startsWith("LEATHER_")) {
                leather++;
            }
        }
        if (leather > 0) {
            double v = leather * cfg.getDouble("leather-per-piece", 3);
            t += v;
            add(parts, "Giáp da (" + leather + " món)", v, false);
        }
        if (cov) {
            double mid = cfg.getDouble("comfort-mid", 22);
            double v = (mid - t) * cfg.getDouble("shelter", 0.5);
            t += v;
            add(parts, "Có mái che", v, false);
        }
        if (heat > 0) {
            double v = heat * cfg.getDouble("heat-source", 18);
            t += v;
            add(parts, "Gần lửa", v, false);
        }
        if (cold > 0) {
            double v = -cold * cfg.getDouble("cold-source", 10);
            t += v;
            add(parts, "Gần băng / tuyết", v, false);
        }
        return t;
    }

    private static void add(List<String> parts, String what, double v, boolean base) {
        if (parts != null && (base || Math.abs(v) >= 0.5)) {
            parts.add("&8• &7" + what + ": " + (base ? "&f" + Math.round(v) + "°" : (v > 0 ? "&c+" : "&b") + Math.round(v) + "°"));
        }
    }

    private void tick() {
        ticks++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!affected(p)) {
                hideBar(p);
                continue;
            }
            try {
                step(p);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Thân nhiệt " + p.getName() + ": " + ex);
            }
        }
    }

    private void step(Player p) {
        UUID u = p.getUniqueId();
        double[] src = sources(p.getLocation(), cfg.getInt("source-radius", 3));
        // ướt: mưa không có mái / ô, xuống nước; khô dần (nhanh hơn cạnh lửa)
        double w = wet.getOrDefault(u, 0.0);
        boolean raining = p.getWorld().hasStorm() && !covered(p) && !umbrella(p)
                && p.getLocation().getBlock().getTemperature() < 1.5;   // sa mạc / đất cằn không mưa
        if (p.isInWater()) {
            w += 25;
        } else if (raining) {
            w += cfg.getDouble("rain-wet", 4);
        } else {
            w -= src[0] > 0 ? 6 : 1.5;
        }
        w = Math.max(0, Math.min(100, w));
        wet.put(u, w);

        double env = environment(p, src[0], src[1], null);
        lastEnv.put(u, env);
        double mid = cfg.getDouble("comfort-mid", 22);
        // Đá Ôn Linh: cập nhật nhiệt từng viên, lấy viên giúp nhiều nhất
        double best = env;
        ItemStack[] inv = p.getInventory().getContents();
        for (int i = 0; i < inv.length; i++) {
            ItemStack it = inv[i];
            if (!isStone(it)) {
                continue;
            }
            ItemMeta m = it.getItemMeta();
            double stored = m.getPersistentDataContainer().getOrDefault(stoneKey, PersistentDataType.DOUBLE, mid);
            Map<Integer, Double> pm = precise.computeIfAbsent(u, k -> new HashMap<>());
            Double prev = pm.get(i);
            double st = prev == null || Math.round(prev) != Math.round(stored) ? stored : prev;   // viên mới / vừa dời ô: lấy theo món đồ
            double target;
            double rate;
            if (src[0] > 0) {
                target = cfg.getDouble("stone-hot", 70);
                rate = 0.06;
            } else if (src[1] > 0) {
                target = cfg.getDouble("stone-cold", -10);
                rate = 0.06;
            } else {
                target = env;
                rate = 1.0 / Math.max(10, cfg.getDouble("stone-hold-seconds", 300));
            }
            double ns = st + (target - st) * rate;
            pm.put(i, ns);
            if (Math.round(ns) != Math.round(stored)) {
                m.getPersistentDataContainer().set(stoneKey, PersistentDataType.DOUBLE, (double) Math.round(ns));
                decorate(m, ns);
                it.setItemMeta(m);
                p.getInventory().setItem(i, it);
                pm.put(i, ns);
            }
            double eff = env + (ns - env) * cfg.getDouble("stone-strength", 0.6);
            if (env < mid && ns > env) {
                eff = Math.min(eff, mid + 2);
            } else if (env > mid && ns < env) {
                eff = Math.max(eff, mid - 2);
            }
            if (Math.abs(eff - mid) < Math.abs(best - mid)) {
                best = eff;
            }
        }

        double b = body.getOrDefault(u, mid);
        b += (best - b) * cfg.getDouble("body-rate", 0.025);
        body.put(u, b);
        effects(p, b, w);
    }

    private void effects(Player p, double b, double w) {
        double cold1 = cfg.getDouble("cold", 10);
        double cold2 = cfg.getDouble("freezing", 2);
        double hot1 = cfg.getDouble("hot", 35);
        double hot2 = cfg.getDouble("scorching", 43);
        int lvl = b < cold2 ? -2 : b < cold1 ? -1 : b > hot2 ? 2 : b > hot1 ? 1 : 0;
        if (lvl < 0) {
            int want = lvl == -2 ? 130 : 90;                       // < 140: chỉ hiện băng, không để game tự gây sát thương đóng băng
            if (p.getFreezeTicks() < want) {
                p.setFreezeTicks(want);
            }
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, lvl == -2 ? 1 : 0, true, false, true));
            p.setExhaustion(p.getExhaustion() + (lvl == -2 ? 0.25f : 0.1f));
        } else if (lvl > 0) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 50, 0, true, false, true));
            if (lvl == 2) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 0, true, false, true));
            }
            p.setExhaustion(p.getExhaustion() + (lvl == 2 ? 0.3f : 0.12f));
        }
        if (Math.abs(lvl) == 2 && ticks % Math.max(1, cfg.getInt("damage-every", 4)) == 0) {
            double min = cfg.getDouble("min-health", 2);
            if (p.getHealth() > min && p.getNoDamageTicks() <= 0) {
                p.setHealth(Math.max(min, p.getHealth() - cfg.getDouble("damage", 1)));
                p.playHurtAnimation(0);
                p.playSound(p.getLocation(), lvl < 0 ? Sound.ENTITY_PLAYER_HURT_FREEZE : Sound.ENTITY_PLAYER_HURT_ON_FIRE, 0.7f, 1f);
            }
        }
        if (lvl != 0) {
            long now = System.currentTimeMillis();
            if (now - warned.getOrDefault(p.getUniqueId(), 0L) > cfg.getLong("tip-minutes", 5) * 60_000L) {
                warned.put(p.getUniqueId(), now);
                p.sendMessage(Tip.c(lvl < 0
                        ? "&b❄ Bạn đang lạnh. &7Vào nhà, lại gần lửa trại, mặc giáp da hoặc mang &6Đá Ôn Linh &7đã hơ nóng. &8(/nhietdo)"
                        : "&c☀ Bạn đang nóng. &7Vào bóng râm, cầm &aô lá&7, ăn dưa hấu / uống nước, mang &6Đá Ôn Linh &7đã ướp lạnh. &8(/nhietdo)"));
            }
        }
        showBar(p, b, w, lvl);
    }

    private void showBar(Player p, double b, double w, int lvl) {
        boolean show = lvl != 0 || b < cfg.getDouble("cold", 10) + 5 || b > cfg.getDouble("hot", 35) - 5 || w >= 30;
        if (!show) {
            hideBar(p);
            return;
        }
        BossBar bar = bars.computeIfAbsent(p.getUniqueId(), k -> {
            BossBar nb = Bukkit.createBossBar("", BarColor.WHITE, BarStyle.SEGMENTED_10);
            nb.addPlayer(p);
            return nb;
        });
        String state = lvl == -2 ? "§bRét cóng" : lvl == -1 ? "§bLạnh" : lvl == 2 ? "§cNóng bỏng" : lvl == 1 ? "§6Nóng" : "§fHơi " + (b < 22 ? "lạnh" : "nóng");
        String title = (b < 22 ? "§b❄ " : "§6☀ ") + "§fThân nhiệt " + Math.round(b) + "° §8· " + state + (w >= 10 ? " §8· §3ướt " + Math.round(w) + "%" : "");
        bar.setTitle(title);
        bar.setColor(lvl < 0 ? BarColor.BLUE : lvl > 0 ? BarColor.RED : b < 22 ? BarColor.WHITE : BarColor.YELLOW);
        bar.setProgress(Math.max(0, Math.min(1, (b + 10) / 60.0)));
        if (!bar.getPlayers().contains(p)) {
            bar.addPlayer(p);
        }
    }

    private void hideBar(Player p) {
        BossBar bar = bars.remove(p.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }

    // ------------------------------------------------------------------ ăn uống
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (!affected(p)) {
            return;
        }
        Material m = e.getItem().getType();
        double d = switch (m) {
            case MUSHROOM_STEW, RABBIT_STEW, BEETROOT_SOUP, SUSPICIOUS_STEW -> cfg.getDouble("food-warm", 8);
            case MELON_SLICE, SWEET_BERRIES, GLOW_BERRIES -> -cfg.getDouble("food-cool", 5);
            case POTION, HONEY_BOTTLE -> -cfg.getDouble("food-cool", 5) / 2;
            default -> 0;
        };
        if (d != 0) {
            UUID u = p.getUniqueId();
            double mid = cfg.getDouble("comfort-mid", 22);
            double b = body.getOrDefault(u, mid);
            body.put(u, d > 0 ? Math.max(b, Math.min(b + d, mid + 4)) : Math.min(b, Math.max(b + d, mid - 4)));
            p.sendActionBar(Tip.c(d > 0 ? "&6☕ Ấm người lên" : "&b❄ Mát người"));
        }
    }

    // ------------------------------------------------------------------ lệnh
    boolean command(CommandSender sender, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("cho") && sender.hasPermission("duckymomo.admin")) {
            Player t = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player sp ? sp : null;
            if (t == null) {
                sender.sendMessage(Tip.c("&cKhông thấy người đó."));
                return true;
            }
            double temp = args.length >= 3 ? Double.parseDouble(args[2]) : cfg.getDouble("comfort-mid", 22);
            t.getInventory().addItem(stone(temp));
            sender.sendMessage(Tip.c("&aĐã cho " + t.getName() + " một Đá Ôn Linh (" + Math.round(temp) + "°)."));
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("dat") && sender.hasPermission("duckymomo.admin")) {
            Player t = Bukkit.getPlayerExact(args[1]);
            if (t == null || args.length < 3) {
                sender.sendMessage(Tip.c("&c/nhietdo dat <người> <độ>"));
                return true;
            }
            body.put(t.getUniqueId(), Double.parseDouble(args[2]));
            sender.sendMessage(Tip.c("&aĐã đặt thân nhiệt " + t.getName() + " = " + args[2] + "°."));
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Chỉ người chơi.");
            return true;
        }
        double mid = cfg.getDouble("comfort-mid", 22);
        double b = body.getOrDefault(p.getUniqueId(), mid);
        double w = wet.getOrDefault(p.getUniqueId(), 0.0);
        List<String> parts = new ArrayList<>();
        double[] src = sources(p.getLocation(), cfg.getInt("source-radius", 3));
        double env = environment(p, src[0], src[1], parts);
        ConfigurationSection ev = Seasons.eventNow();
        p.sendMessage(Tip.c(Tip.LINE));
        p.sendMessage(Tip.c("&6Thân nhiệt &8· &f" + Math.round(b) + "° &7(dễ chịu " + Math.round(cfg.getDouble("cold", 10)) + "–"
                + Math.round(cfg.getDouble("hot", 35)) + "°)" + (w >= 1 ? " &8· &3ướt " + Math.round(w) + "%" : "")));
        if (!affected(p)) {
            p.sendMessage(Tip.c("&7Bạn đang được miễn (người mới, chế độ sáng tạo, hoặc không ở thế giới chính)."));
        }
        p.sendMessage(Tip.c("&7Môi trường quanh bạn: &f" + Math.round(env) + "°"
                + (ev != null && ev.contains("temperature") ? " &8(" + Seasons.plain(ev.getString("name", "")) + ")" : "")));
        for (String s : parts) {
            p.sendMessage(Tip.c("  " + s));
        }
        p.sendMessage(Tip.c("&8▸ &6Đá Ôn Linh&7: chế 4 Đá (thường hoặc trơn) + 4 đá lửa + 1 sắt, xếp ô nào cũng được. Hơ cạnh lửa / ướp cạnh băng rồi mang theo."));
        p.sendMessage(Tip.c("&8▸ &aÔ lá&7: cầm Lá Nhỏ Giọt (hang Lush, hoặc chế 3 lá cây + 2 que) để không ướt khi mưa, đỡ nắng."));
        p.sendMessage(Tip.c("&8▸ &7Ấm: súp, lửa trại, giáp da, vào nhà. Mát: dưa hấu, nước, bóng râm, xuống nước."));
        p.sendMessage(Tip.c(Tip.LINE));
        return true;
    }

    List<String> tab(CommandSender sender, String[] a) {
        if (a.length == 1 && sender.hasPermission("duckymomo.admin")) {
            List<String> l = new ArrayList<>();
            for (String s : List.of("cho", "dat")) {
                if (s.startsWith(a[0].toLowerCase(Locale.ROOT))) {
                    l.add(s);
                }
            }
            return l;
        }
        return List.of();
    }

    void shutdown() {
        for (BossBar b : bars.values()) {
            b.removeAll();
        }
        bars.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Double b = body.get(p.getUniqueId());
            if (b != null) {
                p.getPersistentDataContainer().set(bodyKey, PersistentDataType.DOUBLE, b);
                p.getPersistentDataContainer().set(wetKey, PersistentDataType.DOUBLE, wet.getOrDefault(p.getUniqueId(), 0.0));
            }
        }
    }
}
