package vn.duckymomo.core.paper;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Luật chết (30/10, chủ server chốt). Theo thứ tự:
 *  1. Chỗ đã có luật riêng (Đấu Trường, Hầm Ngục, dungeon… đặt keepInventory) -> để nguyên.
 *  2. Chơi chưa đủ newbie-hours giờ (mọi rank) -> giữ hết.
 *  3. Trong vùng công thành đang diễn ra -> giữ hết (đánh nhau căng, người mới tham chiến dễ chết).
 *  4. Trong thị trấn của mình hoặc Kinh Đô (capital-town) -> giữ hết.
 *  5. Còn lại (quái, môi trường, bị người giết ngoài công thành): rơi hết túi đồ; giáp đang mặc, tay phụ và món đang cầm
 *     mỗi món gear-drop-percent % rơi. Món có Ngọc Hộ Thân (Kham.useKeep) không rơi, trừ một lượt của ngọc.
 * Balo là kho riêng, không bao giờ rơi. Kinh nghiệm: như game gốc.
 * Config death-rules: {enabled, newbie-hours, capital-town, gear-drop-percent}
 */
final class DeathRules implements Listener {
    private static final int HELMET = 39, OFFHAND = 40, ARMOR_FROM = 36;

    private final JavaPlugin plugin;
    private final ConfigurationSection cfg;

    DeathRules(JavaPlugin plugin, ConfigurationSection cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
    }

    private double hours(Player p) {
        return p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20.0 / 3600.0;
    }

    private boolean safeTown(Player p, Location l) {
        try {
            var api = com.palmergames.bukkit.towny.TownyAPI.getInstance();
            var here = api.getTown(l);
            if (here == null) {
                return false;
            }
            if (here.getName().equalsIgnoreCase(cfg.getString("capital-town", "KinhDo"))) {
                return true;
            }
            var mine = api.getResident(p.getUniqueId());
            return mine != null && mine.hasTown() && here.equals(mine.getTownOrNull());
        } catch (Throwable t) {
            return false;
        }
    }

    private static void keepAll(PlayerDeathEvent e) {
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST)   // sau Đấu Trường / Hầm Ngục / EliteMobs: chỗ nào đã giữ đồ thì để nguyên
    public void onDeath(PlayerDeathEvent e) {
        if (e.getKeepInventory()) {
            return;   // Đấu Trường / Hầm Ngục / dungeon đã xử lý
        }
        Player p = e.getEntity();
        Location l = p.getLocation();
        double newbieHours = cfg.getDouble("newbie-hours", 30);
        String why;
        if (hours(p) < newbieHours) {
            keepAll(e);
            why = "&a✔ Người mới: &fchết không mất đồ khi chưa chơi đủ " + trim(newbieHours) + " giờ (đã chơi " + (int) hours(p) + " giờ).";
        } else if (SiegeWarHook.battleActive() && SiegeWarHook.inActiveSiegeZone(l)) {
            keepAll(e);
            why = "&a⚔ Chết trong trận công thành: &fkhông rơi đồ.";
        } else if (safeTown(p, l)) {
            keepAll(e);
            why = "&a✔ Chết trong thị trấn của bạn / Kinh Đô: &fgiữ đồ.";
        } else {
            why = dropRule(e, p);
        }
        String msg = why;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                p.sendMessage(Tip.c(msg));
            }
        }, 30L);
    }

    /** Rơi túi đồ + gear-drop-percent % mỗi món đang mặc/cầm; Ngọc Hộ Thân giữ món. Tự rơi tay để kiểm từng ô. */
    private String dropRule(PlayerDeathEvent e, Player p) {
        int gearPct = Math.max(0, Math.min(100, cfg.getInt("gear-drop-percent", 10)));
        e.setKeepInventory(true);   // giữ túi rồi tự rơi từng món (để biết món nào rơi)
        e.getDrops().clear();
        e.setKeepLevel(false);      // kinh nghiệm như game gốc (mất cấp, rơi một ít: số game đã tính sẵn trong event)
        DonateQr.restoreAll(p);     // đang cầm mã QR nạp tiền: món thật giấu trong QR
        PlayerInventory inv = p.getInventory();
        int held = inv.getHeldItemSlot();
        Location at = p.getLocation();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int bag = 0, gear = 0, saved = 0;
        for (int i = 0; i <= OFFHAND; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) {
                continue;
            }
            boolean worn = i == held || i >= ARMOR_FROM && i <= HELMET || i == OFFHAND;
            if (worn && rnd.nextInt(100) >= gearPct) {
                continue;   // may mắn: món đang mặc/cầm không rơi
            }
            if (Kham.useKeep(p, it)) {
                inv.setItem(i, it);   // ghi lại năng lượng ngọc đã trừ
                saved++;
                continue;
            }
            inv.setItem(i, null);
            at.getWorld().dropItemNaturally(at, it);
            if (worn) {
                gear++;
            } else {
                bag++;
            }
        }
        return "&c☠ Chết ngoài vùng an toàn: &frơi " + bag + " ô túi đồ"
                + (gear > 0 ? ", &c" + gear + " món đang mặc/cầm&f" : ", giữ được giáp và vũ khí")
                + (saved > 0 ? ", &6Ngọc Hộ Thân giữ " + saved + " món&f" : "")
                + ". &7Balo không bao giờ rơi; khảm Ngọc Hộ Thân để giữ đồ.";
    }

    private static String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
    }
}
