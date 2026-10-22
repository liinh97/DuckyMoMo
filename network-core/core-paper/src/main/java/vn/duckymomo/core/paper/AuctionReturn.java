package vn.duckymomo.core.paper;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chợ đen: món treo bán HẾT HẠN tự về túi người bán (22/10, người dùng: "sao hết hạn không tự đẩy về kho đồ"). Trước: món nằm ở
 * /choden hethan, quá thời gian giữ (Cài đặt › Chợ đen, 7 ngày) mà chưa rút thì zAuctionHouse xoá vĩnh viễn.
 * Lúc vào game + mỗi 60 giây với người đang online: mỗi món hết hạn cần 1 ô trống, gọi đúng hàm nút "lấy lại" của zAuctionHouse
 * (AuctionManager#removeExpiredItem: xoá khỏi kho chợ + đưa vào túi, tự báo "Bạn đã lấy lại…"). Túi đầy thì giữ lại và nhắc (mỗi 10 phút một lần).
 * Plugin đọc qua reflection (không cần thư viện lúc build), như Integrations.auctionCount.
 */
final class AuctionReturn implements Listener {

    private final JavaPlugin plugin;
    /** Đang chờ zAuctionHouse trả món (hàm bất đồng bộ): không gọi chồng. */
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
    private final java.util.Map<UUID, Long> warned = new ConcurrentHashMap<>();

    AuctionReturn(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::check), 1200L, 1200L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                check(p);
            }
        }, 200L);   // chờ zAuctionHouse nạp dữ liệu người chơi
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        busy.remove(e.getPlayer().getUniqueId());
        warned.remove(e.getPlayer().getUniqueId());
    }

    private void check(Player p) {
        Plugin zah = Bukkit.getPluginManager().getPlugin("zAuctionHouse");
        if (zah == null || !zah.isEnabled() || busy.contains(p.getUniqueId())) {
            return;
        }
        List<?> items;
        Object mgr;
        Method remove;
        try {
            ClassLoader cl = zah.getClass().getClassLoader();
            Class<?> ap = Class.forName("fr.maxlego08.zauctionhouse.api.AuctionPlugin", true, cl);
            Class<?> am = Class.forName("fr.maxlego08.zauctionhouse.api.AuctionManager", true, cl);
            Class<?> itemCls = Class.forName("fr.maxlego08.zauctionhouse.api.item.Item", true, cl);
            mgr = ap.getMethod("getAuctionManager").invoke(zah);
            items = (List<?>) am.getMethod("getExpiredItems", UUID.class).invoke(mgr, p.getUniqueId());
            remove = am.getMethod("removeExpiredItem", Player.class, itemCls);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("Chợ đen: không đọc được món hết hạn của " + p.getName() + " (" + ex + ")");
            return;
        }
        if (items == null || items.isEmpty()) {
            return;
        }
        busy.add(p.getUniqueId());
        next(p, mgr, remove, List.copyOf(items), 0);
    }

    /** Trả lần lượt từng món (đợi món trước xong mới tới món sau, để đếm ô trống đúng). */
    private void next(Player p, Object mgr, Method remove, List<?> items, int i) {
        if (!p.isOnline()) {
            busy.remove(p.getUniqueId());
            return;
        }
        if (i >= items.size() || p.getInventory().firstEmpty() < 0) {
            busy.remove(p.getUniqueId());
            int left = items.size() - i;
            long now = System.currentTimeMillis();
            if (left > 0 && now - warned.getOrDefault(p.getUniqueId(), 0L) > 600_000L) {
                warned.put(p.getUniqueId(), now);
                p.sendMessage("§6[Chợ đen] §cTúi đầy: §f" + left + " §7món hết hạn còn chờ ở §e/choden hethan§7. Dọn túi là món tự về.");
            }
            return;
        }
        CompletableFuture<?> f;
        try {
            f = (CompletableFuture<?>) remove.invoke(mgr, p, items.get(i));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            busy.remove(p.getUniqueId());
            plugin.getLogger().warning("Chợ đen: không trả được món hết hạn cho " + p.getName() + " (" + ex + ")");
            return;
        }
        f.whenComplete((r, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (ex != null) {
                busy.remove(p.getUniqueId());
                plugin.getLogger().warning("Chợ đen: trả món hết hạn cho " + p.getName() + " lỗi: " + ex);
                return;
            }
            next(p, mgr, remove, items, i + 1);
        }));
    }
}
