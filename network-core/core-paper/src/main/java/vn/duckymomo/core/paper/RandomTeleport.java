package vn.duckymomo.core.paper;

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
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Dịch chuyển ngẫu nhiên (/tpr): chọn khu trong vùng mùa, tìm chỗ an toàn (đất liền, đất hoang Towny, ngoài khu spawn và vùng vây)
 * rồi đưa người chơi tới. Menu: rương cho Java, form cho Bedrock. NPC dân làng ở spawn (/dmm rtpnpc) bấm vào mở menu.
 * Cấu hình: plugins/DuckyMoMoCore/config.yml mục rtp (thời gian chờ, phí sửa được trên trang Cài đặt).
 */
final class RandomTeleport implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final Set<Material> UNSAFE = Set.of(Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE, Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH, Material.POINTED_DRIPSTONE,
            Material.FIRE, Material.SOUL_FIRE, Material.LAVA, Material.WATER, Material.BEDROCK);
    // Giữ qua /dmm reload (đối tượng mới được tạo lại khi nạp config)
    private static final Map<UUID, Long> LAST_USE = new HashMap<>();

    record Zone(String id, String name, Material icon, String description, int centerX, int centerZ, int radius, String requires) {
        /** Vùng gắn mốc khai hoang (requires) chỉ hiện khi mốc đã mở. */
        boolean open() {
            return Shared.unlocked(requires);
        }

        boolean whole() {
            return radius <= 0;
        }
    }

    record Rect(int minX, int minZ, int maxX, int maxZ) {
        boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        static Rect of(List<Integer> v) {
            return v.size() < 4 ? null
                    : new Rect(Math.min(v.get(0), v.get(2)), Math.min(v.get(1), v.get(3)),
                            Math.max(v.get(0), v.get(2)), Math.max(v.get(1), v.get(3)));
        }
    }

    private final DuckyMoMoCore plugin;
    private final String worldName;
    private final long cooldownMs;
    private final double cost;
    private final int maxAttempts;
    private final int townBuffer;
    private final Rect border;
    private final List<Rect> excludes = new ArrayList<>();
    private final List<String> badBiomes;
    private final List<Zone> zones = new ArrayList<>();
    private final String npcName;
    private final List<String> npcLines;
    private final NamespacedKey npcKey;
    private final NamespacedKey zoneKey;
    private final Set<UUID> busy = new HashSet<>();
    private final int warmupSeconds;
    /** Đang đếm ngược trước khi dịch chuyển (đứng yên N giây, giống teleport-delay của Essentials). */
    private final Map<UUID, org.bukkit.scheduler.BukkitTask> warming = new HashMap<>();

    RandomTeleport(DuckyMoMoCore plugin, ConfigurationSection cfg) {
        this.plugin = plugin;
        this.worldName = cfg.getString("world", "world");
        this.cooldownMs = Math.max(0, cfg.getLong("cooldown-seconds", 300)) * 1000L;
        this.cost = Math.max(0, cfg.getDouble("cost", 0));
        this.warmupSeconds = Math.max(0, cfg.getInt("warmup-seconds", 3));
        this.maxAttempts = Math.max(1, cfg.getInt("max-attempts", 15));
        this.townBuffer = Math.max(0, cfg.getInt("town-buffer", 32));
        this.border = Rect.of(cfg.getIntegerList("border"));
        for (Object o : cfg.getList("exclude", List.of())) {
            if (o instanceof List<?> l) {
                Rect r = Rect.of(l.stream().filter(Number.class::isInstance).map(n -> ((Number) n).intValue()).toList());
                if (r != null) {
                    excludes.add(r);
                }
            }
        }
        this.badBiomes = cfg.getStringList("blocked-biomes");
        for (Map<?, ?> m : cfg.getMapList("zones")) {
            String id = String.valueOf(m.get("id"));
            Material icon = Material.matchMaterial(String.valueOf(m.containsKey("icon") ? m.get("icon") : "GRASS_BLOCK"));
            zones.add(new Zone(id.toLowerCase(Locale.ROOT), String.valueOf(m.containsKey("name") ? m.get("name") : id),
                    icon == null || !icon.isItem() ? Material.GRASS_BLOCK : icon,
                    m.containsKey("description") ? String.valueOf(m.get("description")) : "",
                    num(m.get("center-x")), num(m.get("center-z")), num(m.get("radius")),
                    m.containsKey("requires") ? String.valueOf(m.get("requires")) : ""));
        }
        this.npcName = cfg.getString("npc.name", "&6Dịch chuyển ngẫu nhiên");
        this.npcLines = cfg.getStringList("npc.lines");
        this.npcKey = new NamespacedKey(plugin, "rtp_npc");
        this.zoneKey = new NamespacedKey(plugin, "rtp_zone");
    }

    private static int num(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    /** /choilai: xoá thời gian chờ /tpr. */
    static void clearCooldown(UUID id) {
        LAST_USE.remove(id);
    }

    int zoneCount() {
        return zones.size();
    }

    // ------------------------------------------------------------------ lệnh /tpr [khu]

    boolean command(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Chỉ người chơi dùng được /tpr.");
            return true;
        }
        if (args.length == 0) {
            openMenu(p);
            return true;
        }
        Zone z = find(args[0]);
        if (z == null) {
            p.sendMessage(c("&cKhông có khu &f" + args[0] + "&c. Các khu: &f"
                    + String.join("&7, &f", zones.stream().filter(Zone::open).map(Zone::id).toList())));
            return true;
        }
        start(p, z);
        return true;
    }

    List<String> tab(String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String pre = args[0].toLowerCase(Locale.ROOT);
        return zones.stream().filter(Zone::open).map(Zone::id).filter(id -> id.startsWith(pre)).toList();
    }

    private Zone find(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        return zones.stream().filter(z -> z.id().equals(key) && z.open()).findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ menu

    private static final class MenuHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    void openMenu(Player p) {
        String info = waitInfo(p);
        if (BedrockMenu.available() && BedrockMenu.isBedrock(p.getUniqueId())) {
            List<BedrockMenu.Entry> entries = new ArrayList<>();
            for (Zone z : zones) {
                if (!z.open()) {
                    continue;
                }
                entries.add(new BedrockMenu.Entry(plain(z.name()) + (z.description().isBlank() ? "" : "\n" + plain(z.description())), z.id()));
            }
            BedrockMenu.open(p.getUniqueId(), "Dịch chuyển ngẫu nhiên",
                    "Chọn khu muốn tới, bạn sẽ được đưa tới một chỗ đất hoang ngẫu nhiên trong khu đó." + plain(info),
                    entries, id -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        Zone z = find(id);
                        if (z != null && p.isOnline()) {
                            start(p, z);
                        }
                    }));
            return;
        }
        int rows = Math.max(3, Math.min(6, (zones.size() + 6) / 7 + 2));
        MenuHolder holder = new MenuHolder();
        Inventory inv = plugin.getServer().createInventory(holder, rows * 9, c("&0➹ Dịch chuyển tới vùng"));
        holder.inventory = inv;
        int slot = 10;
        for (Zone z : zones) {
            if (!z.open()) {
                continue;
            }
            if (slot % 9 == 8) {
                slot += 2;
            }
            if (slot >= inv.getSize() - 9) {
                break;
            }
            inv.setItem(slot++, zoneButton(z, info));
        }
        p.openInventory(inv);
    }

    private ItemStack zoneButton(Zone z, String info) {
        Tip t = Tip.of("&b➹ &f" + z.name()).accent("&b");
        if (!z.description().isBlank()) {
            t.desc(z.description());
        }
        t.line();
        if (!info.isBlank()) {
            t.raw(info.strip());
        }
        t.click("Bấm để dịch chuyển").note("Đứng yên vài giây sau khi bấm");
        ItemStack item = t.apply(new ItemStack(z.icon()));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(zoneKey, PersistentDataType.STRING, z.id());
        item.setItemMeta(meta);
        return item;
    }

    /** Dòng phụ: phí và thời gian chờ còn lại (trống nếu không có gì). */
    private String waitInfo(Player p) {
        StringBuilder sb = new StringBuilder();
        long left = cooldownLeft(p);
        if (left > 0) {
            sb.append("\n&cCòn chờ ").append(duration(left)).append(" mới dùng lại được.");
        }
        if (cost > 0) {
            sb.append("\n&ePhí mỗi lần: ").append(money(cost));
        }
        return sb.toString();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p) || event.getClickedInventory() != event.getInventory()) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }
        Zone z = find(String.valueOf(clicked.getItemMeta().getPersistentDataContainer().get(zoneKey, PersistentDataType.STRING)));
        if (z != null) {
            p.closeInventory();
            start(p, z);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ dịch chuyển

    private long cooldownLeft(Player p) {
        if (p.hasPermission("duckymomo.rtp.bypass")) {
            return 0;
        }
        Long last = LAST_USE.get(p.getUniqueId());
        return last == null ? 0 : Math.max(0, last + cooldownMs - System.currentTimeMillis());
    }

    void start(Player p, Zone z) {
        World w = plugin.getServer().getWorld(worldName);
        if (w == null) {
            p.sendMessage(c("&cChưa có thế giới " + worldName + ", báo admin kiểm tra cấu hình rtp.world."));
            return;
        }
        long left = cooldownLeft(p);
        if (left > 0) {
            p.sendMessage(c("&cBạn vừa dịch chuyển rồi, chờ thêm &f" + duration(left) + " &cnhé."));
            return;
        }
        if (cost > 0 && !p.hasPermission("duckymomo.rtp.free")) {
            if (!vault()) {
                p.sendMessage(c("&cChưa có hệ thống tiền, không thu phí được. Báo admin nhé."));
                return;
            }
            if (!VaultEconomy.has(p, cost)) {
                p.sendMessage(c("&cKhông đủ tiền: cần &f" + money(cost) + "&c."));
                return;
            }
        }
        if (!busy.add(p.getUniqueId())) {
            p.sendMessage(c("&7Đang tìm chỗ cho bạn, chờ chút…"));
            return;
        }
        // chờ đứng yên trước khi đi (staff có quyền bỏ qua thời gian chờ thì đi ngay)
        int warm = TeleportDelay.seconds(p, warmupSeconds);   // theo rank (Danh phận / rank khác: tp-delay), không có thì rtp.warmup-seconds
        if (warm <= 0 || p.hasPermission("essentials.teleport.timer.bypass") || p.hasPermission("duckymomo.rtp.bypass")) {
            p.sendMessage(c("&7Đang tìm chỗ đất hoang ở " + z.name() + "&7…"));
            attempt(p, z, w, 1);
            return;
        }
        UUID id = p.getUniqueId();
        Location from = p.getLocation().clone();
        int[] count = {warm};
        p.sendMessage(c("&7Đứng yên &f" + warm + " giây &7để dịch chuyển (di chuyển hoặc bị đánh là huỷ)."));
        warming.put(id, plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline()) {
                stopWarmup(id, null);
            } else if (moved(from, p.getLocation())) {
                stopWarmup(id, "&cBạn đã di chuyển, huỷ dịch chuyển.");
            } else if (count[0] <= 0) {
                org.bukkit.scheduler.BukkitTask t = warming.remove(id);
                if (t != null) {
                    t.cancel();
                }
                p.sendMessage(c("&7Đang tìm chỗ đất hoang ở " + z.name() + "&7…"));
                attempt(p, z, w, 1);
            } else {
                p.sendActionBar(c("&eDịch chuyển sau &f" + count[0] + " &egiây… đứng yên"));
                count[0]--;
            }
        }, 0L, 20L));
    }

    private static boolean moved(Location a, Location b) {
        return a.getWorld() != b.getWorld() || a.getBlockX() != b.getBlockX() || a.getBlockZ() != b.getBlockZ()
                || Math.abs(a.getY() - b.getY()) > 1.0;
    }

    private void stopWarmup(UUID id, String msg) {
        org.bukkit.scheduler.BukkitTask t = warming.remove(id);
        if (t != null) {
            t.cancel();
        }
        busy.remove(id);
        Player p = plugin.getServer().getPlayer(id);
        if (msg != null && p != null) {
            p.sendMessage(c(msg));
            p.sendActionBar(c(msg));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onWarmupDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && warming.containsKey(p.getUniqueId())) {
            stopWarmup(p.getUniqueId(), "&cBạn bị tấn công, huỷ dịch chuyển.");
        }
    }

    private void attempt(Player p, Zone z, World w, int n) {
        if (!p.isOnline()) {
            busy.remove(p.getUniqueId());
            return;
        }
        if (n > maxAttempts) {
            busy.remove(p.getUniqueId());
            p.sendMessage(c("&cKhông tìm được chỗ an toàn ở " + z.name() + "&c, thử lại hoặc chọn khu khác nhé."));
            return;
        }
        int[] xz = pick(z);
        if (xz == null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> attempt(p, z, w, n + 1));
            return;
        }
        int x = xz[0];
        int zz = xz[1];
        // Chunk đã tạo sẵn (Chunky) nên không cho tạo mới: chunk chưa có thì thử chỗ khác
        w.getChunkAtAsync(x >> 4, zz >> 4, false).whenComplete((chunk, err) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Location dest = chunk == null || err != null ? null : safeSpot(w, x, zz);
                    if (dest == null) {
                        attempt(p, z, w, n + 1);
                    } else {
                        go(p, z, dest);
                    }
                }));
    }

    /** Điểm ngẫu nhiên trong khu (khu tròn, hoặc cả vùng mùa nếu bán kính 0); null nếu rơi vào vùng cấm. */
    private int[] pick(Zone z) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int x;
        int zz;
        if (z.whole()) {
            if (border == null) {
                return null;
            }
            x = r.nextInt(border.minX() + 16, border.maxX() - 15);
            zz = r.nextInt(border.minZ() + 16, border.maxZ() - 15);
        } else {
            double a = r.nextDouble(Math.PI * 2);
            double d = z.radius() * Math.sqrt(r.nextDouble());
            x = (int) Math.floor(z.centerX() + Math.cos(a) * d);
            zz = (int) Math.floor(z.centerZ() + Math.sin(a) * d);
        }
        if (border != null && !new Rect(border.minX() + 16, border.minZ() + 16, border.maxX() - 16, border.maxZ() - 16).contains(x, zz)) {
            return null;
        }
        for (Rect ex : excludes) {
            if (ex.contains(x, zz)) {
                return null;
            }
        }
        return new int[] {x, zz};
    }

    private Location safeSpot(World w, int x, int z) {
        Block ground = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (ground.getY() <= w.getMinHeight() + 1 || ground.isLiquid() || !ground.getType().isSolid()
                || UNSAFE.contains(ground.getType())) {
            return null;
        }
        // 21/10: không thả dưới tán cây / trên ngọn thân cây (người chơi bị kẹt trong lá, phải phá ra thì dính luật đất hoang)
        if (w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING) != ground.getY()
                || org.bukkit.Tag.LOGS.isTagged(ground.getType()) || org.bukkit.Tag.LEAVES.isTagged(ground.getType())) {
            return null;
        }
        Block feet = ground.getRelative(0, 1, 0);
        Block head = ground.getRelative(0, 2, 0);
        if (!feet.isPassable() || !head.isPassable() || feet.isLiquid() || head.isLiquid()
                || UNSAFE.contains(feet.getType()) || UNSAFE.contains(head.getType())) {
            return null;
        }
        String biome = biomeKey(ground.getBiome());
        for (String bad : badBiomes) {
            if (biome.contains(bad.toLowerCase(Locale.ROOT))) {
                return null;
            }
        }
        Location loc = new Location(w, x + 0.5, ground.getY() + 1, z + 0.5);
        if (SiegeWarHook.inActiveSiegeZone(loc)) {
            return null; // không thả người vào vùng đang bị vây
        }
        if (plugin.getServer().getPluginManager().isPluginEnabled("Towny") && !TownyCheck.wilderness(loc, townBuffer)) {
            return null;
        }
        return loc;
    }

    @SuppressWarnings("deprecation")
    private static String biomeKey(org.bukkit.block.Biome biome) {
        return biome.getKey().getKey();
    }

    private void go(Player p, Zone z, Location dest) {
        boolean pay = cost > 0 && !p.hasPermission("duckymomo.rtp.free");
        if (pay && !VaultEconomy.withdraw(p, cost)) {
            busy.remove(p.getUniqueId());
            p.sendMessage(c("&cKhông đủ tiền: cần &f" + money(cost) + "&c."));
            return;
        }
        dest.setYaw(p.getLocation().getYaw());
        dest.setPitch(p.getLocation().getPitch());
        p.teleportAsync(dest).whenComplete((ok, err) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            busy.remove(p.getUniqueId());
            if (Boolean.TRUE.equals(ok)) {
                LAST_USE.put(p.getUniqueId(), System.currentTimeMillis());
                NewbiePath.feature(p, "rtp");
                p.sendMessage(c("&aĐã tới " + z.name() + "&a: &f" + dest.getBlockX() + " " + dest.getBlockY() + " " + dest.getBlockZ()
                        + (pay ? " &7(phí " + money(cost) + ")" : "") + "&a. Muốn lập thị trấn ở đây: &f/t new <tên>"));
            } else {
                if (pay) {
                    VaultEconomy.deposit(p, cost);
                }
                p.sendMessage(c("&cDịch chuyển không thành, thử lại nhé."));
            }
        }));
    }

    // ------------------------------------------------------------------ NPC ở spawn

    /** /dmm rtpnpc [remove]: đặt (hoặc xoá) dân làng + chữ nổi tại chỗ admin đứng. Đặt lại thì NPC cũ bị xoá. */
    void npcCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Vào game, đứng ở chỗ muốn đặt NPC rồi gõ /dmm rtpnpc");
            return;
        }
        int removed = removeNpcs(p.getWorld());
        if (args.length >= 2 && args[1].equalsIgnoreCase("remove")) {
            p.sendMessage(c("&7Đã xoá " + removed + " thực thể NPC dịch chuyển (chỉ tính chunk đang nạp)."));
            return;
        }
        Location loc = p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
        loc.setYaw(p.getLocation().getYaw());
        loc.setPitch(0);
        Villager v = p.getWorld().spawn(loc, Villager.class, e -> {
            e.customName(c(npcName));
            e.setCustomNameVisible(npcLines.isEmpty());
            e.setAI(false);
            e.setSilent(true);
            e.setInvulnerable(true);
            e.setCollidable(false);
            e.setPersistent(true);
            e.setRemoveWhenFarAway(false);
            e.setProfession(Villager.Profession.CARTOGRAPHER);
            e.setVillagerLevel(5);
            e.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte) 1);
        });
        if (!v.isValid()) {
            p.sendMessage(c("&cKhông đặt được dân làng ở đây (có thể WorldGuard chặn sinh mob). Thử chỗ khác."));
            return;
        }
        // Chữ nổi: dòng đầu là tên, các dòng sau là npc.lines; armor stand ẩn (Bedrock qua Geyser hiển thị được)
        List<String> lines = new ArrayList<>();
        if (!npcLines.isEmpty()) {
            lines.add(npcName);
            lines.addAll(npcLines);
        }
        double y = 1.6 + (lines.size() - 1) * 0.28;
        for (String line : lines) {
            Location at = loc.clone().add(0, y, 0);
            p.getWorld().spawn(at, ArmorStand.class, s -> {
                s.setMarker(true);
                s.setInvisible(true);
                s.setGravity(false);
                s.setInvulnerable(true);
                s.setPersistent(true);
                s.customName(c(line));
                s.setCustomNameVisible(true);
                s.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte) 2);
            });
            y -= 0.28;
        }
        p.sendMessage(c("&aĐã đặt NPC dịch chuyển ngẫu nhiên tại &f" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()
                + (removed > 0 ? " &7(xoá " + removed + " thực thể NPC cũ)" : "") + "&a. Xoá: &f/dmm rtpnpc remove"));
    }

    private int removeNpcs(World w) {
        int n = 0;
        for (Entity e : w.getEntities()) {
            if (isNpc(e)) {
                e.remove();
                n++;
            }
        }
        return n;
    }

    private boolean isNpc(Entity e) {
        return e.getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onNpcClick(PlayerInteractEntityEvent event) {
        if (!isNpc(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true); // không mở bảng mua bán của dân làng
        if (event.getHand() == EquipmentSlot.HAND) {
            openMenu(event.getPlayer());
        }
    }

    @EventHandler
    public void onNpcClickAt(PlayerInteractAtEntityEvent event) {
        if (isNpc(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onNpcDamage(EntityDamageEvent event) {
        if (isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onNpcTransform(EntityTransformEvent event) {
        if (isNpc(event.getEntity())) {
            event.setCancelled(true); // sét đánh không biến thành phù thuỷ
        }
    }

    // ------------------------------------------------------------------ tiện ích

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    private static String plain(String s) {
        return s.replaceAll("&[0-9a-fk-orA-FK-OR]", "");
    }

    private static String duration(long ms) {
        long s = (ms + 999) / 1000;
        return s >= 60 ? (s / 60) + " phút " + (s % 60) + " giây" : s + " giây";
    }

    /** Kiểm tra plugin trước rồi mới chạm tới lớp VaultEconomy (server không có Vault không nạp lớp đó). */
    private static boolean vault() {
        return org.bukkit.Bukkit.getPluginManager().isPluginEnabled("Vault") && VaultEconomy.available();
    }

    private static String money(double v) {
        return vault() ? VaultEconomy.format(v) : String.format(Locale.ROOT, "%.0f", v);
    }

    /** Kiểm tra đất hoang Towny (lớp riêng: server không có Towny không nạp tới lớp này). */
    private static final class TownyCheck {
        static boolean wilderness(Location loc, int buffer) {
            com.palmergames.bukkit.towny.TownyAPI api = com.palmergames.bukkit.towny.TownyAPI.getInstance();
            if (!api.isWilderness(loc)) {
                return false;
            }
            if (buffer <= 0) {
                return true;
            }
            // Không thả sát nách thị trấn người khác: 8 điểm quanh cách buffer block cũng phải là đất hoang
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if ((dx != 0 || dz != 0) && !api.isWilderness(loc.clone().add(dx * buffer, 0, dz * buffer))) {
                        return false;
                    }
                }
            }
            return true;
        }
    }
}
