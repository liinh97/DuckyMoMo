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

    /** Có thao tác trong phút vừa qua (đang chơi thật, không treo máy). */
    boolean activeNow(UUID id) {
        Long last = lastActive.get(id);
        return last != null && System.currentTimeMillis() - last <= 60_000L;
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

    /** /hoatdong [tên]: xem giờ chơi thật của mình (hoặc người khác nếu là admin). Admin: /hoatdong them <tên> <phút> (dựng tình huống test). */
    boolean command(CommandSender s, String[] args) {
        if (args.length >= 3 && args[0].equalsIgnoreCase("them") && s.hasPermission("duckymomo.admin")) {
            OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(args[1]);
            int min;
            try {
                min = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                min = 0;
            }
            if (t == null || min == 0) {
                s.sendMessage("/hoatdong them <tên> <phút> (số âm để trừ)");
                return true;
            }
            String k = t.getUniqueId().toString(), today = LocalDate.now().toString();
            data.set(k + ".ten", t.getName());
            data.set(k + ".tong", Math.max(0, data.getInt(k + ".tong") + min));
            data.set(k + ".ngay." + today, Math.max(0, data.getInt(k + ".ngay." + today) + min));
            dirty = true;
            s.sendMessage(String.format(Locale.ROOT, "Đã cộng %d phút giờ chơi cho %s (tổng %.1f giờ)", min, t.getName(), totalHours(t.getUniqueId())));
            return true;
        }
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
        if (s instanceof Player viewer) {
            menu(viewer, target);
            return true;
        }
        s.sendMessage(LEGACY.deserialize(String.format(Locale.ROOT,
                "&6Giờ chơi thật của &f%s&6: &f%.1f giờ &7trong mùa, &f%.1f giờ &7trong 7 ngày qua %s",
                target.getName(), totalHours(id), week,
                week >= activeHoursPerWeek ? "&a(đang là dân hoạt động)"
                        : String.format(Locale.ROOT, "&c(cần %.0f giờ/7 ngày để là dân hoạt động)", activeHoursPerWeek))));
        s.sendMessage(LEGACY.deserialize("&8Đứng yên, bị nước/xe đẩy đi không được tính giờ."));
        return true;
    }

    private static final String[] THU = {"Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật"};

    /** /hoatdong cho người chơi: menu (23/10, trước in chat). Hàng 1 = 7 ngày qua (cũ -> hôm nay), hàng 2 = giờ chơi dùng để làm gì. */
    private void menu(Player p, OfflinePlayer target) {
        UUID id = target.getUniqueId();
        double week = weekHours(id);
        boolean active = week >= activeHoursPerWeek;
        InfoMenu m = InfoMenu.of("&6⌚ Giờ chơi thật &8· &f" + target.getName()).accent(org.bukkit.Material.ORANGE_STAINED_GLASS_PANE);
        Tip top = Tip.of("&6⌚ &fGiờ chơi thật của " + target.getName()).accent("&6").desc("Không tính lúc đứng yên (AFK).")
                .kv("Trong mùa", String.format(Locale.ROOT, "&e%.1f giờ", totalHours(id)))
                .kv("7 ngày qua", String.format(Locale.ROOT, "&e%.1f giờ", week)).line()
                .check(active, active ? "Đang là dân hoạt động"
                        : String.format(Locale.ROOT, "Cần %.0f giờ / 7 ngày để là dân hoạt động (còn thiếu %.1f giờ)", activeHoursPerWeek, activeHoursPerWeek - week));
        m.top(top.apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.CLOCK)));
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            int min = data.getInt(id + ".ngay." + d);
            String name = (i == 0 ? "Hôm nay" : THU[d.getDayOfWeek().getValue() - 1]) + " &7" + String.format(Locale.ROOT, "%02d/%02d", d.getDayOfMonth(), d.getMonthValue());
            org.bukkit.inventory.ItemStack it = Tip.of((min > 0 ? "&e" : "&7") + name).accent("&e")
                    .kv("Giờ chơi", min > 0 ? String.format(Locale.ROOT, "&f%d giờ %02d phút", min / 60, min % 60) : "&8chưa chơi")
                    .apply(new org.bukkit.inventory.ItemStack(min >= 60 ? org.bukkit.Material.LIME_DYE : min > 0 ? org.bukkit.Material.YELLOW_DYE : org.bukkit.Material.GRAY_DYE));
            it.setAmount(Math.max(1, Math.min(64, min / 60)));
            m.add(it);
        }
        m.row();
        m.add(Tip.of("&b✦ &fGiờ chơi dùng để làm gì").accent("&b").desc("Tính trong mùa, /choilai xoá về 0.").line()
                .item("Dân hoạt động: thị trấn được thêm 8 ô đất mỗi người")
                .item("Lập thị trấn, phong quân hàm, bỏ phiếu")
                .note("Đứng yên, bị nước hay xe đẩy đi không được tính giờ.")
                .apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.BOOK)));
        m.open(p);
    }
}
