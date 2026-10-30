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
            why = "&a✔ Người mới: &fchết không mất đồ khi chưa chơi đủ " + trim(newbieHours) + " giờ (đã chơi " + (int) hours(p) + " giờ). &7/chet";
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
                + ". &7Xem chi tiết: &e/chet";
    }

    // ---------------------------------------------------------------- /chet

    /** /chet: giải thích luật chết + tình trạng của chính người chơi (giờ chơi, chỗ đang đứng, đồ có Ngọc Hộ Thân). */
    void open(Player p) {
        double newbieHours = cfg.getDouble("newbie-hours", 30);
        int gearPct = Math.max(0, Math.min(100, cfg.getInt("gear-drop-percent", 10)));
        double h = hours(p);
        boolean newbie = h < newbieHours;
        Location l = p.getLocation();
        boolean siege = SiegeWarHook.battleActive() && SiegeWarHook.inActiveSiegeZone(l);
        boolean town = safeTown(p, l);
        boolean safe = newbie || siege || town;
        int protMin = plugin.getConfig().getInt("item-cleaner.death-protect-minutes", 10);

        java.util.List<String> kept = new java.util.ArrayList<>();
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i <= OFFHAND; i++) {
            int left = Kham.keepLeft(inv.getItem(i));
            if (left > 0) {
                kept.add("&f" + Kham.label(inv.getItem(i)) + " &7(còn &6" + left + "&7 lượt)");
            }
        }

        InfoMenu m = InfoMenu.of("&4☠ Luật chết").accent(org.bukkit.Material.RED_STAINED_GLASS_PANE);
        Tip top = Tip.of("&4☠ &fNếu bạn chết lúc này").accent(safe ? "&a" : "&c")
                .desc(safe ? "&aBạn KHÔNG mất đồ." : "&cTúi đồ sẽ rơi, giáp và vũ khí mỗi món " + gearPct + "% rơi.").line()
                .kv("Giờ chơi", trim(Math.floor(h * 10) / 10) + " / " + trim(newbieHours) + " giờ"
                        + (newbie ? " &a(còn " + trim(Math.ceil((newbieHours - h) * 10) / 10) + " giờ bảo vệ)" : " &7(hết bảo vệ người mới)"))
                .kv("Chỗ đang đứng", siege ? "&atrận công thành" : town ? "&athị trấn của bạn / Kinh Đô" : "&cngoài vùng an toàn")
                .kv("Món có Ngọc Hộ Thân", kept.isEmpty() ? "&7chưa có" : "&6" + kept.size());
        kept.forEach(top::item);
        m.top(top.apply(new ItemStack(safe ? org.bukkit.Material.TOTEM_OF_UNDYING : org.bukkit.Material.SKELETON_SKULL)));

        // hàng 1: không mất gì
        m.add(Tip.of("&a✔ &fNgười mới").accent("&a").desc("Chơi chưa đủ " + trim(newbieHours) + " giờ: chết không mất gì, kể cả kinh nghiệm.")
                .line().kv("Áp dụng", "mọi rank, mọi chỗ").kv("Của bạn", newbie ? "&ađang được bảo vệ" : "&7đã hết")
                .note("Giờ chơi xem bằng /hoatdong.").apply(new ItemStack(org.bukkit.Material.OAK_SAPLING)), "hoatdong");
        m.add(Tip.of("&a⚔ &fTrận công thành").accent("&a").desc("Chết trong vùng công thành đang diễn ra: không rơi gì.")
                .line().note("Đánh nhau căng, người mới tham chiến không sợ mất đồ.").apply(new ItemStack(org.bukkit.Material.IRON_SWORD)));
        m.add(Tip.of("&a⌂ &fThị trấn của bạn / Kinh Đô").accent("&a").desc("Chết trong đất thị trấn bạn đang ở hoặc Kinh Đô: giữ hết đồ.")
                .line().note("Đất thị trấn khác, đất hoang, hang, Nether: không tính.").apply(new ItemStack(org.bukkit.Material.RED_BED)));
        m.add(Tip.of("&a▣ &fBalo").accent("&a").desc("Đồ cất trong balo không bao giờ rơi, chết ở đâu cũng vậy.")
                .line().kv("Lệnh", "/balo").click("Bấm để mở balo").apply(new ItemStack(org.bukkit.Material.CHEST)), "balo");
        m.row();
        // hàng 2: chỗ còn lại
        m.add(Tip.of("&c☠ &fChết ngoài vùng an toàn").accent("&c")
                .desc("Do quái, ngã, cháy, chết đuối, bị người khác giết… ở đất hoang, thị trấn khác, hang, Nether:")
                .line().item("&cRơi hết đồ trong túi &7(36 ô)").item("Giáp đang mặc: mỗi món &c" + gearPct + "%&f rơi")
                .item("Vũ khí đang cầm, tay trái: mỗi món &c" + gearPct + "%&f rơi").item("Kinh nghiệm: mất như game gốc")
                .apply(new ItemStack(org.bukkit.Material.SKELETON_SKULL)));
        m.add(Tip.of("&e⚠ &fThoát game khi đánh nhau").accent("&e").desc("Đang bị gắn cờ giao tranh (15 giây sau đòn cuối) mà thoát game = chết tại chỗ, rơi đồ như trên.")
                .line().kv("Xem trạng thái", "/pvpstatus").apply(new ItemStack(org.bukkit.Material.BARRIER)), "pvpstatus");
        m.add(Tip.of("&e⌛ &fNhặt lại đồ").accent("&e").desc("Đồ rơi quanh chỗ chết (8 khối) không bị dọn rác trong " + protMin + " phút.")
                .line().note("Người khác đi ngang vẫn nhặt được: quay lại nhanh, /back về chỗ vừa chết.")
                .click("Bấm để /back").apply(new ItemStack(org.bukkit.Material.CLOCK)), "back");
        m.row();
        // hàng 3: Ngọc Hộ Thân
        Tip ht = Tip.of("&6✦ &fNgọc Hộ Thân").accent("&6").desc("Khảm vào đồ set có ô (Tinh Anh trở lên): chết ở đâu món đó cũng KHÔNG rơi.")
                .line().kv("Số lần giữ", Kham.keepSummary())
                .kv("Khảm hỏng", "&c" + Kham.keepFailPct() + "% &7(ngọc vỡ, món nguyên)")
                .note("Mỗi lần món đó lẽ ra bị rơi mà được giữ thì mất 1 lượt; đánh nhau cũng hao chút ít (bằng "
                        + trim(Kham.keepCombatPct()) + "% ngọc thường). Hết năng lượng ngọc vỡ, không nạp lại.")
                .kv("Lệnh khảm", "/thoren kham &7(cầm đồ set trên tay)").click("Bấm để mở khảm");
        m.add(ht.apply(new ItemStack(org.bukkit.Material.HEART_OF_THE_SEA)), "thoren kham");
        m.add(Tip.of("&d♨ &fCách có Ngọc Hộ Thân").accent("&d").desc("Tinh Luyện Hộ Thân ở /tinhluyen:")
                .line().item("2 Lõi Quái &7(quái dungeon rơi)").item("4 Tinh Luyện Netherite").item("5.000 xu")
                .note("Mua bán, trao đổi với người khác được.").click("Bấm để mở Tinh Luyện").apply(new ItemStack(org.bukkit.Material.NETHER_STAR)), "tinhluyen");
        m.open(p);
    }

    private static String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
    }
}
