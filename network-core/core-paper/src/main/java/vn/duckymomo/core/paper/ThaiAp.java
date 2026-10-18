package vn.duckymomo.core.paper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

/**
 * Thái Ấp (/thaiap, Bang Hội Chiến): đất riêng mua bằng xu trong một thế giới phẳng riêng (world, mặc định "thaiap"), cho người thích
 * xây, làm nông, nuôi pet một mình. An toàn tuyệt đối (không PvP, không bị vây, không quái), nhưng KHÔNG có quặng/tài nguyên hiếm
 * (đất phẳng, không hang) để không ai trốn vào đây cày thay cho thế giới chính. Config thai-ap:
 *  - sizes [50, 70, 90]: cạnh mảnh đất theo cấp; prices [5000, 8000, 15000]: giá mua (cấp 1) và mở rộng lên từng cấp sau.
 *  - cell: khoảng cách giữa tâm các mảnh (còn lại là đường đi chung); kitchen-bonus: % kéo dài hiệu ứng món ăn nấu (/nauan) trong Thái Ấp của mình.
 *  - max-trusted: số người được mời cùng xây.
 * Lệnh: mua, morong, moi/duoi <người>, ghe <người>, thich, top, ten <tên>, datspawn, (không tham số) = về Thái Ấp của mình.
 * Lưu: thaiap.yml (dat.<uuid>{so,cap,ten,tin[],thich[],spawn}); ghi thaiap.json cho web (top thích).
 */
final class ThaiAp implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final int ROW = 20;   // số mảnh mỗi hàng
    private static final int GROUND = 64;

    private final DuckyMoMoCore plugin;
    private final String worldName;
    private final List<Integer> sizes;
    private final List<Double> prices;
    private final int cell;
    private final int maxTrusted;
    private final File file;
    private final YamlConfiguration data;
    private World world;

    ThaiAp(DuckyMoMoCore plugin, ConfigurationSection c) {
        this.plugin = plugin;
        this.worldName = c.getString("world", "thaiap");
        List<Integer> sz = c.getIntegerList("sizes");
        this.sizes = sz.isEmpty() ? List.of(50, 70, 90) : sz;
        List<Double> pr = c.getDoubleList("prices");
        this.prices = pr.isEmpty() ? List.of(5000.0, 8000.0, 15000.0) : pr;
        this.cell = Math.max(sizes.get(sizes.size() - 1) + 10, c.getInt("cell", 110));
        this.maxTrusted = Math.max(0, c.getInt("max-trusted", 5));
        this.file = new File(plugin.getDataFolder(), "thaiap.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        Dishes.kitchenBonus = Math.max(0, c.getDouble("kitchen-bonus", 50));
    }

    String describe() {
        return "thế giới " + worldName + ", cạnh " + sizes + ", giá " + prices + " xu";
    }

    void start() {
        world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = new WorldCreator(worldName).generator(new FlatGen()).biomeProvider(new PlainsOnly()).generateStructures(false).createWorld();
        }
        if (world == null) {
            plugin.getLogger().warning("Thái Ấp: không tạo được thế giới " + worldName);
            return;
        }
        world.setSpawnLocation(0, GROUND + 1, 0);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.DO_INSOMNIA, false);
        world.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        world.setPVP(false);
        disableTowny();
        Dishes.kitchenHere = this::inOwnPlot;
        writeJson();
    }

    void stop() {
        Dishes.kitchenHere = null;
        save();
    }

    /** Towny không quản thế giới này (không lập thị trấn, không chặn xây đất hoang): module tự bảo vệ từng mảnh. */
    private void disableTowny() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Towny")) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                var tw = com.palmergames.bukkit.towny.TownyAPI.getInstance().getTownyWorld(world);
                if (tw != null && tw.isUsingTowny()) {
                    tw.setUsingTowny(false);
                    tw.setClaimable(false);
                    tw.setPVP(false);
                    tw.save();
                    plugin.getLogger().info("Thái Ấp: đã tắt Towny ở thế giới " + worldName);
                }
            } catch (RuntimeException | NoSuchMethodError e) {
                plugin.getLogger().warning("Thái Ấp: không tắt được Towny ở " + worldName + " (" + e + "): tắt tay bằng /tw toggle usingtowny ở thế giới đó.");
            }
        }, 100L);
    }

    // ---------------------------------------------------------------- thế giới phẳng

    private static final class FlatGen extends ChunkGenerator {
        @Override
        public void generateNoise(WorldInfo info, Random random, int cx, int cz, ChunkData d) {
            int min = info.getMinHeight();
            d.setRegion(0, min, 0, 16, min + 1, 16, Material.BEDROCK);
            d.setRegion(0, min + 1, 0, 16, GROUND - 3, 16, Material.STONE);
            d.setRegion(0, GROUND - 3, 0, 16, GROUND, 16, Material.DIRT);
            d.setRegion(0, GROUND, 0, 16, GROUND + 1, 16, Material.GRASS_BLOCK);
        }

        @Override
        public boolean shouldGenerateCaves() {
            return false;
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return false;
        }

        @Override
        public boolean shouldGenerateMobs() {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures() {
            return false;
        }
    }

    private static final class PlainsOnly extends BiomeProvider {
        @Override
        public Biome getBiome(WorldInfo info, int x, int y, int z) {
            return Biome.PLAINS;
        }

        @Override
        public List<Biome> getBiomes(WorldInfo info) {
            return List.of(Biome.PLAINS);
        }
    }

    // ---------------------------------------------------------------- mảnh đất

    private int centerX(int n) {
        return ((n % ROW) + 1) * cell;
    }

    private int centerZ(int n) {
        return ((n / ROW) + 1) * cell;
    }

    private String key(UUID u) {
        return "dat." + u;
    }

    private boolean has(UUID u) {
        return data.isConfigurationSection(key(u));
    }

    private int size(UUID u) {
        return sizes.get(Math.min(sizes.size() - 1, data.getInt(key(u) + ".cap", 0)));
    }

    /** Chủ mảnh đất chứa vị trí này (null = đường đi / chưa ai mua). */
    private UUID ownerAt(Location l) {
        if (l.getWorld() != world) {
            return null;
        }
        int ix = (int) Math.round(l.getX() / cell) - 1, iz = (int) Math.round(l.getZ() / cell) - 1;
        if (ix < 0 || ix >= ROW || iz < 0) {
            return null;
        }
        int n = iz * ROW + ix;
        String id = data.getString("so." + n);
        if (id == null) {
            return null;
        }
        UUID u = UUID.fromString(id);
        int half = size(u) / 2;
        return Math.abs(l.getX() - centerX(n)) <= half && Math.abs(l.getZ() - centerZ(n)) <= half ? u : null;
    }

    private boolean canBuild(Player p, Location l) {
        if (p.hasPermission("duckymomo.admin")) {
            return true;
        }
        UUID o = ownerAt(l);
        return o != null && (o.equals(p.getUniqueId()) || data.getStringList(key(o) + ".tin").contains(p.getUniqueId().toString()));
    }

    /** Đang đứng trong Thái Ấp của chính mình (bếp riêng). */
    boolean inOwnPlot(Player p) {
        UUID o = ownerAt(p.getLocation());
        return o != null && o.equals(p.getUniqueId());
    }

    private Location home(UUID u) {
        String sp = data.getString(key(u) + ".spawn");
        int n = data.getInt(key(u) + ".so");
        if (sp != null) {
            String[] a = sp.split(",");
            return new Location(world, Double.parseDouble(a[0]), Double.parseDouble(a[1]), Double.parseDouble(a[2]), Float.parseFloat(a[3]), 0);
        }
        return new Location(world, centerX(n) + 0.5, GROUND + 1, centerZ(n) - size(u) / 2.0 + 2.5, 0, 0);
    }

    /** Viền mảnh đất bằng lối mòn ngay ngoài phần xây được. */
    private void drawBorder(int n, int size) {
        int half = size / 2 + 1, cx = centerX(n), cz = centerZ(n);
        for (int i = -half; i <= half; i++) {
            for (int[] xz : new int[][] {{cx + i, cz - half}, {cx + i, cz + half}, {cx - half, cz + i}, {cx + half, cz + i}}) {
                Block b = world.getBlockAt(xz[0], GROUND, xz[1]);
                if (b.getType() == Material.GRASS_BLOCK || b.getType() == Material.DIRT_PATH) {
                    b.setType(Material.DIRT_PATH, false);
                }
            }
        }
    }

    // ---------------------------------------------------------------- bảo vệ

    private boolean deny(Player p, Location l) {
        if (l.getWorld() != world || canBuild(p, l)) {
            return false;
        }
        p.sendActionBar(c("&cĐây không phải Thái Ấp của bạn."));
        return true;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (deny(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (deny(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEmpty(PlayerBucketEmptyEvent e) {
        if (deny(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFill(PlayerBucketFillEvent e) {
        if (deny(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() != null && deny(e.getPlayer(), e.getClickedBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Player) && !PetCombat.isPet(e.getRightClicked()) && deny(e.getPlayer(), e.getRightClicked().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onStand(PlayerArmorStandManipulateEvent e) {
        if (deny(e.getPlayer(), e.getRightClicked().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHanging(HangingBreakByEntityEvent e) {
        Player p = e.getRemover() == null ? null : Culprit.of(e.getRemover());
        if (e.getEntity().getWorld() == world && (p == null || deny(p, e.getEntity().getLocation()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getEntity().getWorld() != world) {
            return;
        }
        Player p = Culprit.of(e.getDamager());
        if (p == null) {
            return;
        }
        if (e.getEntity() instanceof Player) {
            e.setCancelled(true);   // Thái Ấp không có PvP
        } else if (!PetCombat.isPet(e.getEntity()) && deny(p, e.getEntity().getLocation())) {
            e.setCancelled(true);   // thú nuôi, khung tranh, giá giáp của người khác
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (e.getLocation().getWorld() == world) {
            e.blockList().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (e.getBlock().getWorld() == world) {
            e.blockList().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (e.getBlock().getWorld() == world && !sameOwner(e.getBlock().getLocation(), e.getToBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonOut(BlockPistonExtendEvent e) {
        if (e.getBlock().getWorld() == world && e.getBlocks().stream().anyMatch(b -> !sameOwner(e.getBlock().getLocation(),
                b.getRelative(e.getDirection()).getLocation()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonIn(BlockPistonRetractEvent e) {
        if (e.getBlock().getWorld() == world && e.getBlocks().stream().anyMatch(b -> !sameOwner(e.getBlock().getLocation(), b.getLocation()))) {
            e.setCancelled(true);
        }
    }

    private boolean sameOwner(Location a, Location b) {
        UUID oa = ownerAt(a), ob = ownerAt(b);
        return oa != null && oa.equals(ob);
    }

    // ---------------------------------------------------------------- lệnh

    boolean command(CommandSender s, String[] args) {
        if (world == null) {
            s.sendMessage(c("&cThái Ấp chưa sẵn sàng."));
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("top")) {
            top(s);
            return true;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("Dùng trong game.");
            return true;
        }
        UUID u = p.getUniqueId();
        switch (sub) {
            case "mua" -> buy(p);
            case "morong" -> expand(p);
            case "moi", "duoi" -> trust(p, args, sub.equals("moi"));
            case "ghe" -> visit(p, args);
            case "thich" -> like(p);
            case "ten" -> {
                if (!has(u) || args.length < 2) {
                    p.sendMessage(c(has(u) ? "&eDùng: /thaiap ten <tên>" : "&cBạn chưa có Thái Ấp."));
                    return true;
                }
                String name = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).replaceAll("&[k-or]", "");
                data.set(key(u) + ".ten", name.length() > 32 ? name.substring(0, 32) : name);
                save();
                p.sendMessage(c("&aĐã đặt tên Thái Ấp: &f" + data.getString(key(u) + ".ten")));
            }
            case "datspawn" -> {
                if (!inOwnPlot(p)) {
                    p.sendMessage(c("&cĐứng trong Thái Ấp của bạn rồi gõ lại."));
                    return true;
                }
                Location l = p.getLocation();
                data.set(key(u) + ".spawn", String.format(Locale.ROOT, "%.1f,%.1f,%.1f,%.0f", l.getX(), l.getY(), l.getZ(), l.getYaw()));
                save();
                p.sendMessage(c("&aĐã đặt chỗ xuất hiện khi về / khách ghé Thái Ấp."));
            }
            case "" -> {
                if (!has(u)) {
                    help(p);
                } else {
                    p.teleport(home(u));
                    p.sendMessage(c("&aVề Thái Ấp " + data.getString(key(u) + ".ten", "của bạn") + "&a."));
                }
            }
            default -> help(p);
        }
        return true;
    }

    private void help(Player p) {
        p.sendMessage(c("&6&l🏡 Thái Ấp &7— đất riêng an toàn: không PvP, không bị vây, không quái. Không có quặng: chỉ để xây, làm nông, nuôi pet."));
        p.sendMessage(c("&7Mua: &f/thaiap mua &7(" + money(prices.get(0)) + ", cạnh " + sizes.get(0) + " khối) · mở rộng: &f/thaiap morong"));
        p.sendMessage(c("&7Về: &f/thaiap &7· mời bạn xây cùng: &f/thaiap moi <tên> &7(bỏ: duoi) · đặt tên: &f/thaiap ten <tên> &7· chỗ xuất hiện: &f/thaiap datspawn"));
        p.sendMessage(c("&7Tham quan: &f/thaiap ghe <tên> &7· thích Thái Ấp đang đứng: &f/thaiap thich &7· đẹp nhất: &f/thaiap top"));
        p.sendMessage(c("&7Bếp riêng: món &f/nauan &7nấu trong Thái Ấp của mình kéo dài hiệu ứng thêm &f" + (long) Dishes.kitchenBonus + "%&7."));
    }

    private static String money(double v) {
        return VaultEconomy.available() ? VaultEconomy.format(v) : (long) v + " xu";
    }

    private void buy(Player p) {
        UUID u = p.getUniqueId();
        if (has(u)) {
            p.sendMessage(c("&eBạn đã có Thái Ấp: &f/thaiap &eđể về, &f/thaiap morong &eđể mở rộng."));
            return;
        }
        double price = prices.get(0);
        if (!VaultEconomy.available() || !VaultEconomy.withdraw(p, price)) {
            p.sendMessage(c("&cCần " + money(price) + " để mua Thái Ấp."));
            return;
        }
        int n = data.getInt("tiep", 0);
        while (data.isString("so." + n)) {
            n++;
        }
        data.set("tiep", n + 1);
        data.set("so." + n, u.toString());
        data.set(key(u) + ".so", n);
        data.set(key(u) + ".cap", 0);
        data.set(key(u) + ".ten", p.getName());
        save();
        drawBorder(n, sizes.get(0));
        p.teleport(home(u));
        p.sendMessage(c("&a&l🏡 Đã mua Thái Ấp " + sizes.get(0) + "×" + sizes.get(0) + "! &7Viền lối mòn là ranh giới. Gõ &f/thaiap &7để xem lệnh."));
        writeJson();
    }

    private void expand(Player p) {
        UUID u = p.getUniqueId();
        if (!has(u)) {
            p.sendMessage(c("&cBạn chưa có Thái Ấp: /thaiap mua"));
            return;
        }
        int lv = data.getInt(key(u) + ".cap", 0);
        if (lv + 1 >= sizes.size() || lv + 1 >= prices.size()) {
            p.sendMessage(c("&eThái Ấp đã to nhất (" + size(u) + "×" + size(u) + ")."));
            return;
        }
        double price = prices.get(lv + 1);
        if (!VaultEconomy.withdraw(p, price)) {
            p.sendMessage(c("&cCần " + money(price) + " để mở rộng lên " + sizes.get(lv + 1) + "×" + sizes.get(lv + 1) + "."));
            return;
        }
        data.set(key(u) + ".cap", lv + 1);
        save();
        drawBorder(data.getInt(key(u) + ".so"), sizes.get(lv + 1));
        p.sendMessage(c("&aĐã mở rộng Thái Ấp lên " + sizes.get(lv + 1) + "×" + sizes.get(lv + 1) + "."));
        writeJson();
    }

    private void trust(Player p, String[] args, boolean add) {
        UUID u = p.getUniqueId();
        if (!has(u) || args.length < 2) {
            p.sendMessage(c(has(u) ? "&eDùng: /thaiap " + (add ? "moi" : "duoi") + " <tên>" : "&cBạn chưa có Thái Ấp."));
            return;
        }
        OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (t == null) {
            p.sendMessage(c("&cKhông thấy người chơi " + args[1] + "."));
            return;
        }
        List<String> tin = new ArrayList<>(data.getStringList(key(u) + ".tin"));
        if (add) {
            if (tin.size() >= maxTrusted) {
                p.sendMessage(c("&cTối đa " + maxTrusted + " người được mời."));
                return;
            }
            if (!tin.contains(t.getUniqueId().toString())) {
                tin.add(t.getUniqueId().toString());
            }
        } else {
            tin.remove(t.getUniqueId().toString());
        }
        data.set(key(u) + ".tin", tin);
        save();
        p.sendMessage(c(add ? "&a" + t.getName() + " được xây cùng trong Thái Ấp của bạn." : "&e" + t.getName() + " không còn được xây trong Thái Ấp của bạn."));
    }

    private void visit(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage(c("&eDùng: /thaiap ghe <tên>"));
            return;
        }
        OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (t == null || !has(t.getUniqueId())) {
            p.sendMessage(c("&c" + args[1] + " chưa có Thái Ấp."));
            return;
        }
        p.teleport(home(t.getUniqueId()));
        p.sendMessage(c("&aĐang ghé Thái Ấp &f" + data.getString(key(t.getUniqueId()) + ".ten", t.getName()) + "&a. Thích thì &f/thaiap thich&a."));
    }

    private void like(Player p) {
        UUID o = ownerAt(p.getLocation());
        if (o == null) {
            p.sendMessage(c("&eĐứng trong Thái Ấp của người khác rồi gõ /thaiap thich."));
            return;
        }
        if (o.equals(p.getUniqueId())) {
            p.sendMessage(c("&eKhông tự thích Thái Ấp của mình được."));
            return;
        }
        List<String> likes = new ArrayList<>(data.getStringList(key(o) + ".thich"));
        if (likes.contains(p.getUniqueId().toString())) {
            p.sendMessage(c("&7Bạn đã thích Thái Ấp này rồi."));
            return;
        }
        likes.add(p.getUniqueId().toString());
        data.set(key(o) + ".thich", likes);
        save();
        p.sendMessage(c("&a❤ Đã thích Thái Ấp " + data.getString(key(o) + ".ten") + " &7(" + likes.size() + " lượt)."));
        Player owner = Bukkit.getPlayer(o);
        if (owner != null) {
            owner.sendMessage(c("&d❤ " + p.getName() + " vừa thích Thái Ấp của bạn (" + likes.size() + " lượt)."));
            Achievements.add(owner, "thaiap", 1);
        }
        writeJson();
    }

    private List<String> ranked() {
        ConfigurationSection d = data.getConfigurationSection("dat");
        List<String> ids = d == null ? new ArrayList<>() : new ArrayList<>(d.getKeys(false));
        ids.sort(Comparator.comparingInt((String id) -> data.getStringList("dat." + id + ".thich").size()).reversed());
        return ids;
    }

    private void top(CommandSender s) {
        s.sendMessage(c("&6&l🏡 Thái Ấp được thích nhiều nhất"));
        List<String> ids = ranked();
        for (int i = 0; i < Math.min(10, ids.size()); i++) {
            String id = ids.get(i);
            OfflinePlayer o = Bukkit.getOfflinePlayer(UUID.fromString(id));
            s.sendMessage(c("&6" + (i + 1) + ". &f" + data.getString("dat." + id + ".ten", "?") + " &7của " + o.getName() + " — &d❤ "
                    + data.getStringList("dat." + id + ".thich").size()));
        }
        if (ids.isEmpty()) {
            s.sendMessage(c("&7Chưa ai mua Thái Ấp."));
        }
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được thaiap.yml: " + e.getMessage());
        }
    }

    private void writeJson() {
        JsonObject root = new JsonObject();
        root.addProperty("so_luong", ranked().size());
        JsonArray arr = new JsonArray();
        for (String id : ranked().subList(0, Math.min(10, ranked().size()))) {
            JsonObject o = new JsonObject();
            o.addProperty("ten", data.getString("dat." + id + ".ten", "?"));
            o.addProperty("chu", Bukkit.getOfflinePlayer(UUID.fromString(id)).getName());
            o.addProperty("thich", data.getStringList("dat." + id + ".thich").size());
            o.addProperty("canh", sizes.get(Math.min(sizes.size() - 1, data.getInt("dat." + id + ".cap", 0))));
            arr.add(o);
        }
        root.add("top", arr);
        try {
            Files.writeString(new File(plugin.getDataFolder(), "thaiap.json").toPath(), root.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Không ghi được thaiap.json: " + e.getMessage());
        }
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
}
