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
import org.bukkit.NamespacedKey;
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
import org.bukkit.persistence.PersistentDataType;

/**
 * Thái Ấp (/thaiap, Bang Hội Chiến): đất riêng mua bằng xu trong một thế giới phẳng riêng (world, mặc định "thaiap"), cho người thích
 * xây, làm nông, nuôi pet một mình. An toàn tuyệt đối (không PvP, không bị vây, không quái), nhưng KHÔNG có quặng/tài nguyên hiếm
 * (đất phẳng, không hang) để không ai trốn vào đây cày thay cho thế giới chính. Config thai-ap:
 *  - sizes [50, 70, 90]: cạnh mảnh đất theo cấp; prices [5000, 8000, 15000]: giá mua (cấp 1) và mở rộng lên từng cấp sau.
 *  - cell: khoảng cách giữa tâm các mảnh (còn lại là đường đi chung); kitchen-bonus: % kéo dài hiệu ứng món ăn nấu (/nauan) trong Thái Ấp của mình.
 *  - max-trusted: số người được mời cùng xây.
 * Lệnh: mua, morong, moi/duoi <người>, ghe <người>, thich, top, ten <tên>, datspawn, (không tham số) = về Thái Ấp của mình.
 * Lưu: thaiap.yml (dat.<uuid>{so,cap,ten,tin[],thich[],spawn}); ghi thaiap.json cho web (top thích).
 *
 * Thái Ấp Sáng Tạo (thai-ap.sang-tao, 19/10): server riêng sangtao-1 chế độ sáng tạo, CHỈ vào được qua cổng trong thế giới Thái Ấp.
 *  - Bang Hội Chiến: cổng (khung thạch anh tím, hạt tím, chữ nổi) ở chỗ xuất hiện của thế giới Thái Ấp (tự dựng; admin dời: /thaiap congsangtao).
 *    Bước vào: phải có Thái Ấp hoặc được mời xây cùng → ghi vé sangtao-ve.yml (uuid: hạn ms, ticket-seconds) rồi chuyển server (BungeeCord Connect).
 *    /thaiap sangtao: tới trước cổng.
 *  - sangtao-1 (sang-tao.mirror-file có giá trị): chạy cùng lớp này ở "chế độ bản sao": đọc thaiap.yml của Bang Hội Chiến (mount chỉ đọc)
 *    nên mỗi người có mảnh đất cùng số, cùng cỡ, cùng người được mời; không mua/mở rộng/mời/thích ở đây. Vào không có vé (gõ /server) → đưa về sảnh.
 *    Túi đồ, tiền là của server sangtao-1 (tách hẳn Bang Hội Chiến): không có đường mang đồ sáng tạo về. Chặn ném đồ, kích nổ, triệu hồi wither,
 *    giới hạn sinh vật quanh mỗi người (max-entities). /thaiap ve: về Bang Hội Chiến.
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
    // Thái Ấp Sáng Tạo
    private static final String CHANNEL = "BungeeCord";
    private final boolean creativeLink;      // Bang Hội Chiến: có cổng sang server sáng tạo
    private final boolean mirror;            // sangtao-1: bản sao chỉ đọc
    private final String creativeServer;
    private final String homeServer;
    private final File ticketFile;
    private final File mirrorFile;
    private final long ticketMillis;
    private final int maxEntities;
    private final NamespacedKey portalKey;
    private final java.util.Map<UUID, Long> portalCooldown = new java.util.HashMap<>();
    /** Bản sao: hạn của vé đã dùng theo người (file vé chỉ đọc, không xoá được) -> mỗi lần qua cổng chỉ vào được 1 lần. */
    private final java.util.Map<UUID, Long> usedTicket = new java.util.HashMap<>();

    ThaiAp(DuckyMoMoCore plugin, ConfigurationSection c) {
        this.plugin = plugin;
        this.worldName = c.getString("world", "thaiap");
        List<Integer> sz = c.getIntegerList("sizes");
        this.sizes = sz.isEmpty() ? List.of(50, 70, 90) : sz;
        List<Double> pr = c.getDoubleList("prices");
        this.prices = pr.isEmpty() ? List.of(5000.0, 8000.0, 15000.0) : pr;
        this.cell = Math.max(sizes.get(sizes.size() - 1) + 10, c.getInt("cell", 110));
        this.maxTrusted = Math.max(0, c.getInt("max-trusted", 5));
        ConfigurationSection st = c.getConfigurationSection("sang-tao");
        String mf = st == null ? "" : st.getString("mirror-file", "");
        this.mirror = mf != null && !mf.isBlank();
        this.creativeLink = !mirror && st != null && st.getBoolean("enabled", false);
        this.creativeServer = st == null ? "sangtao-1" : st.getString("server", "sangtao-1");
        this.homeServer = st == null ? "banghoi-1" : st.getString("home-server", "banghoi-1");
        this.ticketMillis = (st == null ? 60 : Math.max(10, st.getInt("ticket-seconds", 60))) * 1000L;
        this.maxEntities = st == null ? 40 : Math.max(5, st.getInt("max-entities", 40));
        this.mirrorFile = mirror ? new File(mf) : null;
        this.ticketFile = mirror ? new File(st.getString("ticket-file", "/banghoi-dmm/sangtao-ve.yml"))
                : new File(plugin.getDataFolder(), "sangtao-ve.yml");
        this.portalKey = new NamespacedKey(plugin, "cong_sangtao");
        this.file = mirror ? mirrorFile : new File(plugin.getDataFolder(), "thaiap.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        Dishes.kitchenBonus = Math.max(0, c.getDouble("kitchen-bonus", 50));
    }

    String describe() {
        return "thế giới " + worldName + ", cạnh " + sizes + (mirror ? ", BẢN SAO SÁNG TẠO đọc " + mirrorFile : ", giá " + prices + " xu")
                + (creativeLink ? ", cổng sang " + creativeServer : "");
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
        if (mirror) {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setTime(6000);
            world.setStorm(false);
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
            // dữ liệu chủ đất do Bang Hội Chiến ghi: đọc lại mỗi 30 giây
            Bukkit.getScheduler().runTaskTimer(plugin, this::reloadMirror, 600L, 600L);
            return;
        }
        Dishes.kitchenHere = this::inOwnPlot;
        writeJson();
        if (creativeLink) {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
            if (!data.isString("cong-sangtao")) {
                buildPortal(new Location(world, 0, GROUND + 1, 6));
            }
            Bukkit.getScheduler().runTaskTimer(plugin, this::portalParticles, 40L, 20L);
        }
    }

    void stop() {
        if (!mirror) {
            Dishes.kitchenHere = null;
            save();
        }
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
        if (mirror) {
            mirrorCommand(p, sub, args);
            return true;
        }
        switch (sub) {
            case "sangtao" -> {
                Location front = portalFront();
                if (!creativeLink || front == null) {
                    p.sendMessage(c("&cChưa có cổng Thái Ấp Sáng Tạo."));
                } else {
                    p.teleport(front);
                    p.sendMessage(c("&d✦ Cổng Thái Ấp Sáng Tạo ở trước mặt: bước vào để sang server sáng tạo (cần có Thái Ấp hoặc được mời xây cùng)."));
                }
            }
            case "congsangtao" -> {
                if (!p.hasPermission("duckymomo.admin") || p.getWorld() != world) {
                    p.sendMessage(c("&cAdmin đứng trong thế giới Thái Ấp, chỗ muốn đặt cổng (khung dựng về phía trước, dọc trục X)."));
                    return true;
                }
                buildPortal(p.getLocation().getBlock().getLocation());
                p.sendMessage(c("&aĐã dựng cổng Thái Ấp Sáng Tạo tại đây (cổng cũ đã gỡ)."));
            }
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

    /** /thaiap không tham số khi chưa có đất, /thaiap giup: menu (22/10, trước in 6 dòng chat). Hàng 1 = đất của tôi, hàng 2 = khám phá. */
    private void help(Player p) {
        java.util.UUID u = p.getUniqueId();
        boolean own = has(u);
        InfoMenu m = InfoMenu.of("&6⌂ Thái Ấp").accent(org.bukkit.Material.LIME_STAINED_GLASS_PANE);
        Tip top = Tip.of("&6⌂ &fThái Ấp").accent("&a")
                .desc("Đất riêng an toàn: không PvP, không bị vây, không quái.", "Không có quặng: chỉ để xây, làm nông, nuôi pet.").line();
        if (own) {
            int lv = data.getInt(key(u) + ".cap", 0);
            int sz = sizes.get(Math.min(sizes.size() - 1, lv));
            top.head("Thái Ấp của bạn").kv("Tên", data.getString(key(u) + ".ten", p.getName())).kv("Cỡ", sz + "×" + sz)
                    .kv("Lượt thích", String.valueOf(data.getStringList(key(u) + ".thich").size()))
                    .kv("Người xây cùng", String.valueOf(data.getStringList(key(u) + ".tin").size()));
        } else {
            top.kv("Giá mua", money(prices.get(0)) + " &7(cạnh " + sizes.get(0) + " khối)");
        }
        top.kv("Bếp riêng", "món /nauan nấu ở đây dài thêm " + (long) Dishes.kitchenBonus + "%");
        m.top(top.apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.OAK_SAPLING)));
        if (own) {
            m.add(Tip.of("&a⌂ &fVề Thái Ấp").accent("&a").line().kv("Lệnh", "/thaiap").click("Bấm để về")
                    .apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.OAK_DOOR)), InfoMenu.run("thaiap"));
            int lv = data.getInt(key(u) + ".cap", 0);
            Tip ex = Tip.of("&e⤢ &fMở rộng").accent("&e").desc("Đất to hơn: " + String.join(" → ", sizes.stream().map(x -> x + "×" + x).toList())).line();
            if (lv + 1 < sizes.size() && lv + 1 < prices.size()) {
                ex.kv("Lên " + sizes.get(lv + 1) + "×" + sizes.get(lv + 1), money(prices.get(lv + 1))).kv("Lệnh", "/thaiap morong").click("Bấm để mở rộng");
            } else {
                ex.check(true, "Đã to nhất");
            }
            m.add(ex.apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.SCAFFOLDING)), InfoMenu.run("thaiap morong"));
        } else {
            m.add(Tip.of("&a✚ &fMua Thái Ấp").accent("&a").desc("Mảnh đất " + sizes.get(0) + "×" + sizes.get(0) + ", viền lối mòn là ranh giới").line()
                    .kv("Giá", money(prices.get(0))).kv("Lệnh", "/thaiap mua").click("Bấm để mua")
                    .apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.GRASS_BLOCK)), InfoMenu.run("thaiap mua"));
        }
        m.add(Tip.of("&f✎ &fĐặt tên").line().kv("Lệnh", "/thaiap ten <tên>").click("Bấm để điền lệnh")
                .apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.NAME_TAG)), InfoMenu.suggest("/thaiap ten", "Gõ tên Thái Ấp"));
        m.add(Tip.of("&f⚑ &fĐặt chỗ xuất hiện").desc("Đứng ở chỗ muốn khách xuất hiện rồi bấm").line().kv("Lệnh", "/thaiap datspawn")
                .click("Bấm để đặt ở chỗ đang đứng").apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.RED_BED)), InfoMenu.run("thaiap datspawn"));
        m.add(Tip.of("&b☺ &fMời xây cùng").accent("&b").desc("Cho bạn bè xây trong đất của bạn").line()
                .kv("Lệnh", "/thaiap moi <tên>").click("Bấm để điền lệnh").apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.PLAYER_HEAD)),
                InfoMenu.suggest("/thaiap moi", "Gõ tên người"));
        m.add(Tip.of("&c✖ &fĐuổi người xây cùng").accent("&c").desc("Bỏ quyền xây của một người").line()
                .kv("Lệnh", "/thaiap duoi <tên>").click("Bấm để điền lệnh").apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_DOOR)),
                InfoMenu.suggest("/thaiap duoi", "Gõ tên người"));
        m.row();
        m.add(Tip.of("&d✈ &fTham quan").accent("&d").desc("Ghé Thái Ấp của người khác").line().kv("Lệnh", "/thaiap ghe <tên>").click("Bấm để điền lệnh")
                .apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.COMPASS)), InfoMenu.suggest("/thaiap ghe", "Gõ tên chủ đất"));
        m.add(Tip.of("&c❤ &fThích").accent("&c").desc("Thích Thái Ấp bạn đang đứng").line().kv("Lệnh", "/thaiap thich").click("Bấm để thích")
                .apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.POPPY)), InfoMenu.run("thaiap thich"));
        m.add(Tip.of("&6♛ &fĐẹp nhất").accent("&6").desc("Thái Ấp nhiều lượt thích nhất").line().kv("Lệnh", "/thaiap top").click("Bấm để xem")
                .apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.GOLD_INGOT)), InfoMenu.run("thaiap top"));
        if (creativeLink) {
            m.add(Tip.of("&d✦ &fThái Ấp Sáng Tạo").accent("&d").desc("Bản sao mảnh đất của bạn ở chế độ sáng tạo: xây thoả thích, đồ không mang về được").line()
                    .kv("Lệnh", "/thaiap sangtao").click("Bấm để tới cổng").apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.AMETHYST_CLUSTER)),
                    InfoMenu.run("thaiap sangtao"));
        }
        m.open(p);
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
        p.sendMessage(c("&a⌂ Đã mua Thái Ấp " + sizes.get(0) + "×" + sizes.get(0) + "! &7Viền lối mòn là ranh giới. Gõ &f/thaiap &7để xem lệnh."));
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

    // ---------------------------------------------------------------- Thái Ấp Sáng Tạo

    private void connect(Player p, String server) {
        com.google.common.io.ByteArrayDataOutput out = com.google.common.io.ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);
        p.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
    }

    /** Được mời xây cùng ở Thái Ấp của ai (null = không). */
    private UUID trustedBy(UUID u) {
        ConfigurationSection d = data.getConfigurationSection("dat");
        if (d == null) {
            return null;
        }
        for (String id : d.getKeys(false)) {
            if (data.getStringList("dat." + id + ".tin").contains(u.toString())) {
                return UUID.fromString(id);
            }
        }
        return null;
    }

    /** Ô dưới chân cổng (góc trái khung). */
    private int[] portalBase() {
        String s = data.getString("cong-sangtao");
        if (s == null) {
            return null;
        }
        String[] a = s.split(",");
        return new int[] {Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2])};
    }

    private Location portalFront() {
        int[] b = portalBase();
        return b == null || world == null ? null : new Location(world, b[0] + 1.0, b[1] + 1, b[2] - 2.5, 0, 0);
    }

    private boolean inPortal(Location l) {
        int[] b = portalBase();
        if (b == null || l.getWorld() != world) {
            return false;
        }
        int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
        return z == b[2] && x >= b[0] && x <= b[0] + 1 && y >= b[1] + 1 && y <= b[1] + 3;
    }

    /** Khung 4×5 thạch anh tím dọc trục X, lòng 2×3 trống; base = ô dưới chân lòng cổng bên trái. */
    private void buildPortal(Location base) {
        int[] old = portalBase();
        if (old != null) {
            for (int dx = -1; dx <= 2; dx++) {
                for (int dy = 0; dy <= 4; dy++) {
                    Block b = world.getBlockAt(old[0] + dx, old[1] + dy, old[2]);
                    if (b.getType() == Material.AMETHYST_BLOCK || b.getType() == Material.PURPUR_PILLAR) {
                        b.setType(dy == 0 ? Material.GRASS_BLOCK : Material.AIR, false);
                    }
                }
            }
        }
        world.getNearbyEntities(base, 64, 16, 64).stream()
                .filter(e -> e.getPersistentDataContainer().has(portalKey, PersistentDataType.BYTE)).forEach(org.bukkit.entity.Entity::remove);
        if (old != null) {
            world.getNearbyEntities(new Location(world, old[0], old[1], old[2]), 8, 8, 8).stream()
                    .filter(e -> e.getPersistentDataContainer().has(portalKey, PersistentDataType.BYTE)).forEach(org.bukkit.entity.Entity::remove);
        }
        int bx = base.getBlockX(), by = base.getBlockY() - 1, bz = base.getBlockZ();
        for (int dx = -1; dx <= 2; dx++) {
            for (int dy = 0; dy <= 4; dy++) {
                boolean frame = dx == -1 || dx == 2 || dy == 0 || dy == 4;
                Block b = world.getBlockAt(bx + dx, by + dy, bz);
                b.setType(frame ? ((dx == -1 || dx == 2) && dy > 0 && dy < 4 ? Material.PURPUR_PILLAR : Material.AMETHYST_BLOCK) : Material.AIR, false);
            }
        }
        data.set("cong-sangtao", bx + "," + by + "," + bz);
        save();
        world.spawn(new Location(world, bx + 1.0, by + 5.6, bz + 0.5), org.bukkit.entity.TextDisplay.class, t -> {
            t.text(c("&d✦ THÁI ẤP SÁNG TẠO ✦\n&fBước vào cổng để sang chế độ sáng tạo\n&7Cần có Thái Ấp hoặc được mời xây cùng"));
            t.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            t.setPersistent(true);
            t.getPersistentDataContainer().set(portalKey, PersistentDataType.BYTE, (byte) 1);
        });
    }

    private void portalParticles() {
        int[] b = portalBase();
        if (b == null || world == null || world.getPlayers().isEmpty()) {
            return;
        }
        Location mid = new Location(world, b[0] + 1.0, b[1] + 2.5, b[2] + 0.5);
        if (!mid.isChunkLoaded()) {
            return;
        }
        world.spawnParticle(org.bukkit.Particle.PORTAL, mid, 40, 0.5, 1.0, 0.1, 0.2);
    }

    /** Ghi vé (Bang Hội Chiến → sangtao-1 đọc qua mount chỉ đọc). */
    private void writeTicket(UUID u) {
        YamlConfiguration t = YamlConfiguration.loadConfiguration(ticketFile);
        long now = System.currentTimeMillis();
        for (String k : t.getKeys(false)) {
            if (t.getLong(k) < now) {
                t.set(k, null);
            }
        }
        t.set(u.toString(), now + ticketMillis);
        try {
            t.save(ticketFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Không ghi được sangtao-ve.yml: " + e.getMessage());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortalMove(org.bukkit.event.player.PlayerMoveEvent e) {
        if (!creativeLink || e.getTo().getWorld() != world || e.getFrom().getBlock().equals(e.getTo().getBlock()) || !inPortal(e.getTo())) {
            return;
        }
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        if (now < portalCooldown.getOrDefault(p.getUniqueId(), 0L)) {
            return;
        }
        portalCooldown.put(p.getUniqueId(), now + 5000);
        UUID u = p.getUniqueId();
        if (!has(u) && trustedBy(u) == null && !p.hasPermission("duckymomo.admin")) {
            p.sendMessage(c("&cCần có Thái Ấp (&f/thaiap mua&c) hoặc được chủ Thái Ấp mời xây cùng (&f/thaiap moi <tên>&c) mới sang Thái Ấp Sáng Tạo được."));
            Location f = portalFront();
            if (f != null) {
                Bukkit.getScheduler().runTask(plugin, () -> p.teleport(f));
            }
            return;
        }
        writeTicket(u);
        p.sendMessage(c("&d✦ Đang sang Thái Ấp Sáng Tạo… &7(túi đồ bên đó riêng, đồ không mang về được; về lại: &f/thaiap ve&7)"));
        connect(p, creativeServer);
    }

    /** Về lại từ server sáng tạo: đang đứng trong lòng cổng thì đẩy ra trước cổng (không bị chuyển ngược ngay). */
    @EventHandler
    public void onLinkJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        if (creativeLink && inPortal(e.getPlayer().getLocation())) {
            portalCooldown.put(e.getPlayer().getUniqueId(), System.currentTimeMillis() + 5000);
            Location f = portalFront();
            Bukkit.getScheduler().runTaskLater(plugin, () -> e.getPlayer().teleport(f), 2L);
        }
    }

    // ---- phía sangtao-1 (bản sao)

    private void reloadMirror() {
        try {
            data.load(mirrorFile);
        } catch (Exception ex) {
            plugin.getLogger().warning("Thái Ấp Sáng Tạo: không đọc được " + mirrorFile + " (" + ex.getMessage() + ")");
        }
    }

    /** Còn vé chưa dùng thì đánh dấu đã dùng (vé ghi hạn mới mỗi lần bước vào cổng). */
    private boolean useTicket(UUID u) {
        long exp = YamlConfiguration.loadConfiguration(ticketFile).getLong(u.toString());
        if (exp <= System.currentTimeMillis() || Long.valueOf(exp).equals(usedTicket.get(u))) {
            return false;
        }
        usedTicket.put(u, exp);
        return true;
    }

    /** Mảnh đất người này dùng ở bản sao: của mình, không có thì của người mời. */
    private UUID plotFor(UUID u) {
        return has(u) ? u : trustedBy(u);
    }

    @EventHandler
    public void onMirrorJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        if (!mirror) {
            return;
        }
        Player p = e.getPlayer();
        reloadMirror();
        UUID u = p.getUniqueId();
        UUID plot = plotFor(u);
        boolean admin = p.hasPermission("duckymomo.admin");
        if (!admin && (plot == null || !useTicket(u))) {
            p.setGameMode(org.bukkit.GameMode.ADVENTURE);
            p.sendMessage(c("&cThái Ấp Sáng Tạo chỉ vào được qua &dcổng trong Thái Ấp&c ở Bang Hội Chiến (&f/thaiap sangtao&c). Đang đưa về sảnh…"));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) {
                    connect(p, "lobby");
                }
            }, 40L);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            p.setGameMode(org.bukkit.GameMode.CREATIVE);
            p.teleport(plot != null ? home(plot) : world.getSpawnLocation());
            p.sendMessage(c("&d✦ THÁI ẤP SÁNG TẠO &7— bản sao mảnh đất " + (plot != null && plot.equals(u) ? "của bạn" : "bạn được mời")
                    + ", xây thoả thích ở chế độ sáng tạo."));
            p.sendMessage(c("&7Túi đồ ở đây riêng, đồ không mang về Bang Hội Chiến. Lệnh: &f/thaiap &7(về mảnh đất) · &f/thaiap ghe <tên> &7· &f/thaiap ve &7(về Bang Hội Chiến)"));
        }, 5L);
    }

    @EventHandler
    public void onMirrorRespawn(org.bukkit.event.player.PlayerRespawnEvent e) {
        if (mirror && world != null) {
            UUID plot = plotFor(e.getPlayer().getUniqueId());
            e.setRespawnLocation(plot != null ? home(plot) : world.getSpawnLocation());
        }
    }

    private void mirrorCommand(Player p, String sub, String[] args) {
        UUID u = p.getUniqueId();
        switch (sub) {
            case "ve", "banghoi" -> {
                p.sendMessage(c("&aĐang về Bang Hội Chiến…"));
                connect(p, homeServer);
            }
            case "ghe" -> visit(p, args);
            case "" -> {
                UUID plot = plotFor(u);
                if (plot == null) {
                    p.sendMessage(c("&cBạn chưa có Thái Ấp ở Bang Hội Chiến."));
                } else {
                    p.teleport(home(plot));
                }
            }
            case "mua", "morong", "moi", "duoi", "ten", "datspawn", "thich" ->
                    p.sendMessage(c("&eLàm ở Bang Hội Chiến (&f/thaiap " + sub + "&e): Thái Ấp Sáng Tạo tự cập nhật theo trong 30 giây."));
            default -> {
                p.sendMessage(c("&d✦ Thái Ấp Sáng Tạo &7— bản sao mảnh đất ở chế độ sáng tạo, túi đồ riêng."));
                p.sendMessage(c("&7&f/thaiap &7về mảnh đất · &f/thaiap ghe <tên> &7tham quan · &f/thaiap ve &7về Bang Hội Chiến"));
            }
        }
    }

    /**
     * Client sáng tạo tự gửi được món đồ với dữ liệu tuỳ ý (chế đồ "hack": thuộc tính +1000 sát thương, phù phép cấp 32767, trứng chứa
     * dữ liệu sinh vật lạ, rương lồng rương hàng trăm KB làm sập/lag server khi lưu). Chỉ giữ đồ bình thường; món lạ đổi thành bản trơn.
     */
    @EventHandler(ignoreCancelled = true)
    public void onMirrorCreative(org.bukkit.event.inventory.InventoryCreativeEvent e) {
        if (!mirror) {
            return;
        }
        org.bukkit.inventory.ItemStack it = e.getCursor();
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) {
            return;
        }
        String why = suspicious(it);
        if (why != null) {
            e.setCursor(new org.bukkit.inventory.ItemStack(it.getType(), Math.max(1, Math.min(it.getAmount(), it.getType().getMaxStackSize()))));
            if (e.getWhoClicked() instanceof Player p) {
                p.sendActionBar(c("&eĐã bỏ dữ liệu lạ khỏi món đồ (" + why + ")."));
                plugin.getLogger().info("Sáng tạo: " + p.getName() + " gửi món " + it.getType() + " có " + why + " → đổi thành bản trơn");
            }
        }
    }

    private static String suspicious(org.bukkit.inventory.ItemStack it) {
        byte[] raw;
        try {
            raw = it.serializeAsBytes();
        } catch (RuntimeException ex) {
            return "dữ liệu hỏng";
        }
        if (raw.length > 8192) {
            return "dữ liệu quá lớn " + raw.length / 1024 + " KB";
        }
        org.bukkit.inventory.meta.ItemMeta m = it.getItemMeta();
        if (m.hasAttributeModifiers()) {
            return "chỉ số tự chế";
        }
        for (var en : m.getEnchants().entrySet()) {
            if (en.getValue() > en.getKey().getMaxLevel()) {
                return "phù phép vượt cấp";
            }
        }
        if (m instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta es) {
            for (var en : es.getStoredEnchants().entrySet()) {
                if (en.getValue() > en.getKey().getMaxLevel()) {
                    return "sách phù phép vượt cấp";
                }
            }
        }
        if (m instanceof org.bukkit.inventory.meta.SpawnEggMeta egg && egg.getSpawnedEntity() != null) {
            return "trứng chứa dữ liệu sinh vật";
        }
        if (m instanceof org.bukkit.inventory.meta.PotionMeta pm && pm.hasCustomEffects()) {
            return "thuốc tự chế";
        }
        if (m.isUnbreakable()) {
            return "không bao giờ hỏng";
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMirrorDrop(org.bukkit.event.player.PlayerDropItemEvent e) {
        if (mirror) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMirrorPrime(org.bukkit.event.entity.ExplosionPrimeEvent e) {
        if (mirror) {
            e.setCancelled(true);
            e.getEntity().remove();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMirrorSpawn(org.bukkit.event.entity.CreatureSpawnEvent e) {
        if (!mirror) {
            return;
        }
        switch (e.getSpawnReason()) {
            case BUILD_WITHER, BUILD_IRONGOLEM, BUILD_SNOWMAN, NATURAL, RAID, PATROL, REINFORCEMENTS, VILLAGE_INVASION, JOCKEY, MOUNT -> {
                if (e.getSpawnReason() != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BUILD_IRONGOLEM
                        && e.getSpawnReason() != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BUILD_SNOWMAN) {
                    e.setCancelled(true);
                    return;
                }
            }
            default -> { }
        }
        if (e.getEntityType() == org.bukkit.entity.EntityType.WITHER || e.getEntityType() == org.bukkit.entity.EntityType.ENDER_DRAGON) {
            e.setCancelled(true);
            return;
        }
        long near = e.getLocation().getNearbyLivingEntities(48).stream().filter(x -> !(x instanceof Player)).count();
        if (near >= maxEntities) {
            e.setCancelled(true);
            e.getLocation().getNearbyPlayers(16).forEach(pl -> pl.sendActionBar(c("&cĐã đủ " + maxEntities + " sinh vật quanh đây.")));
        }
    }

    private List<String> ranked() {
        ConfigurationSection d = data.getConfigurationSection("dat");
        List<String> ids = d == null ? new ArrayList<>() : new ArrayList<>(d.getKeys(false));
        ids.sort(Comparator.comparingInt((String id) -> data.getStringList("dat." + id + ".thich").size()).reversed());
        return ids;
    }

    private void top(CommandSender s) {
        s.sendMessage(c("&6⌂ Thái Ấp được thích nhiều nhất"));
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
        if (mirror) {
            return;
        }
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được thaiap.yml: " + e.getMessage());
        }
    }

    private void writeJson() {
        if (mirror) {
            return;
        }
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
        return Tip.ns(LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false));
    }
}
