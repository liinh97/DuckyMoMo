package vn.duckymomo.core.paper;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Bot của botfarm (trang quản trị › Bot) chỉ giữ một vùng nhỏ quanh mình (27/10, chủ server: 22 bot mà server sắp quá tải).
 * Mỗi bot đứng một khu riêng, mỗi khu server phải nạp view-distance (8 -> 17x17 chunk) và chạy simulation-distance (6 -> 13x13 chunk:
 * quái sinh, cây mọc, dơi…): 22 bot = ~5.600 chunk, ~4.700 thực thể, MSPT ~45. Bot đứng yên nên đặt riêng cho bot simulation / view
 * distance nhỏ (mặc định 2); người thật đi tới gần bot vẫn chạy vùng của chính họ như thường.
 * Nhận ra bot bằng IP giả 10.30.x.x mà botfarm gửi qua Velocity forwarding (người thật luôn vào qua proxy bằng IP thật).
 * Config: bot-view.enabled (mặc định bật), bot-view.simulation-distance, bot-view.view-distance.
 */
final class BotView implements Listener {

    private final int sim;
    private final int view;
    private final Logger log;

    BotView(int sim, int view, Logger log) {
        this.sim = Math.max(2, Math.min(32, sim));
        this.view = Math.max(2, Math.min(32, view));
        this.log = log;
        for (Player p : Bukkit.getOnlinePlayers()) {
            apply(p);   // nạp lại plugin khi bot đang online
        }
    }

    /**
     * Dọn quái "đông cứng" quanh bot (29/10, spark: ~5.000 quái, TPS 12–15). Bot giữ vòng chunk ngoài được nạp nhưng không chạy
     * (ngoài simulation-distance): quái lang thang ra đó đứng im mãi, không bao giờ tự biến mất, vẫn tốn công đếm / theo dõi.
     * Mỗi phút (khi có bot online) gỡ quái thù địch + dơi ở world / nether / end nếu: không có người THẬT trong 128 khối (như luật biến mất của game),
     * quái thường (removeWhenFarAway = không bị giữ lại, không tên; Entity#isPersistent của Bukkit là "lưu cùng chunk", đừng dùng). EliteMobs / pet / NPC có tên hoặc persistent nên không bị đụng.
     */
    /**
     * Chặn quái sinh tự nhiên ở chỗ không có người THẬT trong 128 khối (29/10: setAffectsSpawning không chặn được, bot vẫn làm
     * sinh ~1.250 quái / phút). Game gốc cũng xoá ngay quái cách mọi người chơi > 128 khối, nên người thật không thấy khác.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpawn(org.bukkit.event.entity.CreatureSpawnEvent e) {
        if (e.getSpawnReason() != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL
                || !CLEAN_WORLDS.contains(e.getLocation().getWorld().getName())) {
            return;
        }
        org.bukkit.Location l = e.getLocation();
        for (Player p : l.getWorld().getPlayers()) {
            if (!isBot(p) && p.getLocation().distanceSquared(l) < 128 * 128) {
                return;
            }
        }
        e.setCancelled(true);
    }

    private static final Set<String> CLEAN_WORLDS = Set.of("world", "world_nether", "world_the_end");

    void startCleanup(org.bukkit.plugin.Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanup, 200L, 1200L);
    }

    // ---------------------------------------------------------------- phòng ẩn (29/10)
    private String hideWorld;
    private double hideX;
    private double hideY;
    private double hideZ;
    private double hideR;

    /** Bot trong phòng ẩn (bot-hide {world, x, y, z, radius}) thì không ai thấy (BotHidePackets). Cần packetevents. */
    void startHide(org.bukkit.plugin.Plugin plugin, org.bukkit.configuration.ConfigurationSection c) {
        if (c == null || !c.getBoolean("enabled") || !Bukkit.getPluginManager().isPluginEnabled("packetevents")) {
            return;
        }
        hideWorld = c.getString("world", "world");
        hideX = c.getDouble("x");
        hideY = c.getDouble("y");
        hideZ = c.getDouble("z");
        hideR = c.getDouble("radius", 8);
        BotHidePackets.register();
        BotHidePackets.HIDDEN.clear();
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                hideCheck(p, p.getLocation());
            }
        }, 20L, 20L);
        log.info("Phòng ẩn bot: " + hideWorld + " " + (int) hideX + " " + (int) hideY + " " + (int) hideZ + " (bán kính " + (int) hideR + ")");
    }

    private boolean inRoom(org.bukkit.Location l) {
        return hideWorld != null && l.getWorld() != null && l.getWorld().getName().equals(hideWorld)
                && Math.abs(l.getY() - hideY) <= hideR && (l.getX() - hideX) * (l.getX() - hideX) + (l.getZ() - hideZ) * (l.getZ() - hideZ) <= hideR * hideR;
    }

    private void hideCheck(Player p, org.bukkit.Location at) {
        if (hideWorld == null) {
            return;
        }
        if (isBot(p) && inRoom(at)) {
            BotHidePackets.HIDDEN.add(p.getEntityId());
        } else {
            BotHidePackets.HIDDEN.remove(p.getEntityId());
        }
    }

    /** Dịch chuyển: cập nhật ngay theo chỗ tới (trước khi server gửi gói hiện bot cho người ở chỗ mới). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent e) {
        hideCheck(e.getPlayer(), e.getTo());
    }

    private void cleanup() {
        List<Player> real = new ArrayList<>();
        boolean anyBot = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isBot(p)) {
                anyBot = true;
            } else {
                real.add(p);
            }
        }
        if (!anyBot) {
            return;   // không có bot: game tự lo như thường
        }
        // 29/10: rà cả thế giới (không chỉ quanh bot): quái dưới hang sâu, ở chunk do bot / thị trấn giữ nạp cũng đông cứng
        Set<org.bukkit.entity.Entity> gone = new HashSet<>();
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            if (!CLEAN_WORLDS.contains(w.getName())) {
                continue;
            }
            for (org.bukkit.entity.LivingEntity le : w.getLivingEntities()) {
                if (!(le instanceof org.bukkit.entity.Mob m)) {
                    continue;
                }
                if (!(m instanceof org.bukkit.entity.Enemy) && !(m instanceof org.bukkit.entity.Bat)) {
                    continue;
                }
                if (!m.getRemoveWhenFarAway() || m.customName() != null || m.isInsideVehicle() || !m.getPassengers().isEmpty()) {
                    continue;
                }
                boolean near = false;
                for (Player r : real) {
                    if (r.getWorld().equals(w) && r.getLocation().distanceSquared(m.getLocation()) < 128 * 128) {
                        near = true;
                        break;
                    }
                }
                if (!near) {
                    gone.add(m);
                }
            }
        }
        for (org.bukkit.entity.Entity e : gone) {
            e.remove();
        }
        if (gone.size() >= 50) {
            log.info("Dọn " + gone.size() + " quái đông cứng quanh bot (không có người thật gần)");
        }
    }

    static boolean isBot(Player p) {
        InetSocketAddress a = p.getAddress();
        return a != null && a.getAddress() != null && a.getAddress().getHostAddress().startsWith("10.30.");
    }

    void apply(Player p) {
        if (!isBot(p)) {
            return;
        }
        try {
            p.setSimulationDistance(sim);
            p.setViewDistance(view);
            p.setSendViewDistance(view);
            // 29/10: bot đứng yên cả ngày làm quái sinh dồn quanh mình (spark: ~5.000 quái, TPS 14). Không tính bot vào sinh quái
            // (Paper: người không affects-spawning cũng bị bỏ qua khi xét quái biến mất) → quái quanh bot tự biến mất, quanh người thật vẫn sinh
            p.setAffectsSpawning(false);
        } catch (RuntimeException e) {
            log.warning("Không đặt được tầm nhìn cho bot " + p.getName() + ": " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        apply(e.getPlayer());
        remember(e.getPlayer());
    }

    // ---------------------------------------------------------------- bot đã từng vào (31/10: bot mua /ah của người OFFLINE phải
    // biết người bán offline có phải bot không; isBot chỉ nhận ra bot đang online qua IP 10.30.x.x). Lưu plugins/DuckyMoMoCore/bot-uuid.txt

    private static final java.util.Set<java.util.UUID> KNOWN = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static volatile java.io.File knownFile;

    static void initKnown(org.bukkit.plugin.java.JavaPlugin plugin) {
        knownFile = new java.io.File(plugin.getDataFolder(), "bot-uuid.txt");
        try {
            if (knownFile.exists()) {
                for (String l : java.nio.file.Files.readAllLines(knownFile.toPath())) {
                    try {
                        KNOWN.add(java.util.UUID.fromString(l.trim()));
                    } catch (IllegalArgumentException ignored) {
                        // dòng hỏng
                    }
                }
            }
        } catch (java.io.IOException ex) {
            plugin.getLogger().warning("Không đọc được bot-uuid.txt: " + ex.getMessage());
        }
        org.bukkit.Bukkit.getOnlinePlayers().forEach(BotView::remember);
    }

    private static void remember(Player p) {
        if (isBot(p) && KNOWN.add(p.getUniqueId()) && knownFile != null) {
            try {
                java.nio.file.Files.writeString(knownFile.toPath(), p.getUniqueId() + "\n", java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
            } catch (java.io.IOException ignored) {
                // lần sau ghi lại
            }
        }
    }

    /** Bot (đang online qua IP giả, hoặc đã từng vào server này dưới dạng bot). */
    static boolean knownBot(java.util.UUID id) {
        if (KNOWN.contains(id)) {
            return true;
        }
        Player p = org.bukkit.Bukkit.getPlayer(id);
        return p != null && isBot(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent e) {
        apply(e.getPlayer());
    }
}
