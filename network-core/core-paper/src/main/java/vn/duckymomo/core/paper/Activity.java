package vn.duckymomo.core.paper;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Giờ chơi thật (không tính AFK): một phút được tính khi trong phút đó người chơi xoay góc nhìn, đào/đặt block, bấm đồ,
 * đánh, chat hoặc gõ lệnh. Chỉ bị đẩy đi (nước, xe) thì không tính.
 * Lưu tổng giờ trong mùa + số phút từng ngày (giữ 14 ngày) ở plugins/DuckyMoMoCore/hoatdong.yml.
 * Dùng cho: dân hoạt động (đất 8 ô/dân), lập thị trấn, quân hàm, bỏ phiếu. /choilai xoá về 0.
 */
final class Activity implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final int KEEP_DAYS = 14;

    private final DuckyMoMoCore plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, Long> lastActive = new ConcurrentHashMap<>();
    private final double activeHoursPerWeek;
    private final int newMemberDays;
    private boolean dirty;

    Activity(DuckyMoMoCore plugin, ConfigurationSection c) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "hoatdong.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        this.activeHoursPerWeek = c.getDouble("active-hours-per-week", 3);
        this.newMemberDays = c.getInt("new-member-days", 3);
    }

    void start() {
        // Mỗi phút: ai có thao tác trong 60 giây qua được cộng 1 phút
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickMinute, 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::saveIfDirty, 6000L, 6000L);
        plugin.getLogger().info("Giờ chơi thật: bật (dân hoạt động >= " + activeHoursPerWeek + " giờ/7 ngày)");
    }

    double activeHoursPerWeek() {
        return activeHoursPerWeek;
    }

    int newMemberDays() {
        return newMemberDays;
    }

    // ------------------------------------------------------------------ ghi nhận thao tác

    private void touch(Player p) {
        lastActive.put(p.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getYaw() != e.getTo().getYaw() || e.getFrom().getPitch() != e.getTo().getPitch()) {
            touch(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        touch(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        touch(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        touch(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            touch(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            touch(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent e) {
        touch(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        touch(e.getPlayer());
    }

    private void tickMinute() {
        long now = System.currentTimeMillis();
        String today = LocalDate.now().toString();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Long last = lastActive.get(p.getUniqueId());
            if (last == null || now - last > 60_000L) {
                continue;
            }
            String k = p.getUniqueId().toString();
            data.set(k + ".ten", p.getName());
            data.set(k + ".tong", data.getInt(k + ".tong") + 1);
            data.set(k + ".ngay." + today, data.getInt(k + ".ngay." + today) + 1);
            dirty = true;
        }
    }

    // ------------------------------------------------------------------ đọc

    /** Tổng số giờ chơi thật trong mùa (từ lần /choilai gần nhất). */
    double totalHours(UUID id) {
        return data.getInt(id + ".tong") / 60.0;
    }

    /** Số giờ chơi thật trong 7 ngày gần nhất (tính cả hôm nay). */
    double weekHours(UUID id) {
        ConfigurationSection days = data.getConfigurationSection(id + ".ngay");
        if (days == null) {
            return 0;
        }
        LocalDate from = LocalDate.now().minusDays(6);
        int sum = 0;
        for (String d : days.getKeys(false)) {
            try {
                if (!LocalDate.parse(d).isBefore(from)) {
                    sum += days.getInt(d);
                }
            } catch (RuntimeException ignored) {
                // ngày viết sai thì bỏ qua
            }
        }
        return sum / 60.0;
    }

    /** UUID (chuỗi) mọi người chơi từng có giờ chơi thật. */
    java.util.Set<String> knownPlayers() {
        return data.getKeys(false);
    }

    boolean isActive(UUID id) {
        return weekHours(id) >= activeHoursPerWeek;
    }

    /** /choilai: xoá giờ chơi thật về 0. */
    void reset(UUID id) {
        String name = data.getString(id + ".ten");
        data.set(id.toString(), null);
        if (name != null) {
            data.set(id + ".ten", name);
        }
        dirty = true;
        saveIfDirty();
    }

    // ------------------------------------------------------------------ lưu

    private void saveIfDirty() {
        if (!dirty) {
            return;
        }
        // Bỏ số liệu ngày quá cũ cho file gọn
        LocalDate cut = LocalDate.now().minusDays(KEEP_DAYS);
        for (String k : data.getKeys(false)) {
            ConfigurationSection days = data.getConfigurationSection(k + ".ngay");
            if (days == null) {
                continue;
            }
            for (String d : days.getKeys(false)) {
                try {
                    if (LocalDate.parse(d).isBefore(cut)) {
                        days.set(d, null);
                    }
                } catch (RuntimeException e) {
                    days.set(d, null);
                }
            }
        }
        try {
            data.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được hoatdong.yml: " + e.getMessage());
        }
    }

    void shutdown() {
        saveIfDirty();
    }

    /** /hoatdong [tên]: xem giờ chơi thật của mình (hoặc người khác nếu là admin). */
    boolean command(CommandSender s, String[] args) {
        OfflinePlayer target;
        if (args.length >= 1 && s.hasPermission("duckymomo.admin")) {
            target = Bukkit.getOfflinePlayerIfCached(args[0]);
            if (target == null) {
                s.sendMessage(LEGACY.deserialize("&cKhông tìm thấy " + args[0]));
                return true;
            }
        } else if (s instanceof Player p) {
            target = p;
        } else {
            s.sendMessage("/hoatdong <tên>");
            return true;
        }
        UUID id = target.getUniqueId();
        double week = weekHours(id);
        s.sendMessage(LEGACY.deserialize(String.format(Locale.ROOT,
                "&6Giờ chơi thật của &f%s&6: &f%.1f giờ &7trong mùa, &f%.1f giờ &7trong 7 ngày qua %s",
                target.getName(), totalHours(id), week,
                week >= activeHoursPerWeek ? "&a(đang là dân hoạt động)"
                        : String.format(Locale.ROOT, "&c(cần %.0f giờ/7 ngày để là dân hoạt động)", activeHoursPerWeek))));
        s.sendMessage(LEGACY.deserialize("&8Đứng yên, bị nước/xe đẩy đi không được tính giờ."));
        return true;
    }
}
