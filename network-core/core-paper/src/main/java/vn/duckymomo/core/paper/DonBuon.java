package vn.duckymomo.core.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Đơn hàng nhà buôn mỗi tuần (27/10): NPC Nhà Buôn ở khu shop (/warp shop, đặt bằng /dmm donbuonnpc), bấm vào mở đơn. Mỗi thứ Hai (giờ VN) chọn per-week đơn từ pool. Mỗi đơn cần vài món Tinh Luyện,
 * trả gia % giá bán Tinh Luyện (mặc định 120%, lời hơn bán thẳng cho server) + quà thêm (qua). Mỗi người giao mỗi đơn 1 lần/tuần;
 * mỗi đơn tối đa luot lượt cả server (hết lượt = đóng, ai nhanh người đó được). Món Tinh Luyện nhận diện bằng dấu thật (Refining.idOf).
 * Lưu: donbuon.yml (tuan, don [chỉ số pool], giao.<uuid> [chỉ số đơn], dem.<chỉ số đơn>, tong.<uuid> = tổng xu nhận trong tuần).
 */
final class DonBuon implements org.bukkit.event.Listener {

    static volatile DonBuon INSTANCE;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private record Order(Map<String, Integer> need, List<String> extra) { }

    private final DuckyMoMoCore plugin;
    private final Rewards rewards;
    private final int perWeek;
    private final double pct;
    private final int maxDeliveries;
    private final boolean announce;
    private final List<Order> pool = new ArrayList<>();
    private final File file;
    private final YamlConfiguration data;
    private final org.bukkit.NamespacedKey npcKey;

    DonBuon(DuckyMoMoCore plugin, Rewards rewards, ConfigurationSection c) {
        INSTANCE = this;
        this.plugin = plugin;
        this.rewards = rewards;
        this.perWeek = Math.max(1, c.getInt("per-week", 3));
        this.pct = c.getDouble("gia", 120);
        this.maxDeliveries = c.getInt("luot", 10);
        this.announce = c.getBoolean("announce", true);
        for (Map<?, ?> m : c.getMapList("pool")) {
            Map<String, Integer> need = new LinkedHashMap<>();
            if (m.get("can") instanceof Map<?, ?> cm) {
                cm.forEach((k, v) -> need.put(String.valueOf(k), v instanceof Number n ? n.intValue() : 1));
            }
            List<String> extra = new ArrayList<>();
            if (m.get("qua") instanceof List<?> l) {
                l.forEach(x -> extra.add(String.valueOf(x)));
            }
            if (!need.isEmpty()) {
                pool.add(new Order(need, extra));
            }
        }
        this.file = new File(plugin.getDataFolder(), "donbuon.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        Shared.FORGET.add(id -> Shared.forget(data, file, id));
        this.npcKey = new org.bukkit.NamespacedKey(plugin, "donbuon_npc");
        Bukkit.getScheduler().runTaskTimer(plugin, this::roll, 100L, 6000L);   // 5 phút kiểm sang tuần mới
        Bukkit.getScheduler().runTaskTimer(plugin, this::lookAtPlayers, 40L, 10L);
    }

    // ---------------------------------------------------------------- NPC Nhà Buôn (dân làng tắt AI, cùng kiểu NPC Cửa hàng)

    private boolean isNpc(org.bukkit.entity.Entity e) {
        return e.getPersistentDataContainer().has(npcKey, org.bukkit.persistence.PersistentDataType.BYTE);
    }

    /** /dmm donbuonnpc [remove] (đứng tại chỗ) · console: /dmm donbuonnpc <x> <y> <z> <yaw> (world chính). Đặt lại thì NPC cũ bị xoá. */
    void npcCommand(org.bukkit.command.CommandSender s, String[] args) {
        boolean console = !(s instanceof Player);
        boolean remove = args.length >= 2 && args[1].equalsIgnoreCase("remove");
        if (console && args.length < 5 && !remove) {
            s.sendMessage("Vào game đứng chỗ muốn đặt rồi gõ /dmm donbuonnpc (console: /dmm donbuonnpc <x> <y> <z> <yaw>)");
            return;
        }
        org.bukkit.World world = s instanceof Player pl ? pl.getWorld() : Bukkit.getWorlds().get(0);
        int removed = 0;
        for (org.bukkit.entity.Entity e : world.getEntities()) {
            if (isNpc(e)) {
                e.remove();
                removed++;
            }
        }
        if (remove) {
            s.sendMessage(c("&7Đã xoá " + removed + " thực thể NPC Nhà Buôn."));
            return;
        }
        org.bukkit.Location loc;
        if (console) {
            loc = new org.bukkit.Location(world, Integer.parseInt(args[1]) + 0.5, Integer.parseInt(args[2]), Integer.parseInt(args[3]) + 0.5, Float.parseFloat(args[4]), 0);
        } else {
            Player p = (Player) s;
            loc = p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
            loc.setYaw(p.getLocation().getYaw());
        }
        org.bukkit.entity.Villager v = world.spawn(loc, org.bukkit.entity.Villager.class, e -> {
            e.setAI(false);
            e.setSilent(true);
            e.setInvulnerable(true);
            e.setCollidable(false);
            e.setPersistent(true);
            e.setRemoveWhenFarAway(false);
            e.setProfession(org.bukkit.entity.Villager.Profession.CARTOGRAPHER);
            e.setVillagerType(org.bukkit.entity.Villager.Type.DESERT);
            e.setVillagerLevel(5);
            e.getPersistentDataContainer().set(npcKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        });
        if (!v.isValid()) {
            s.sendMessage(c("&cKhông đặt được dân làng ở đây (có thể WorldGuard chặn sinh mob)."));
            return;
        }
        String[] lines = {"&6✉ Nhà Buôn", "&7Đơn hàng tuần: cần Tinh Luyện, trả hậu", "&8(bấm vào để xem đơn)"};
        double y = 1.6 + (lines.length - 1) * 0.28;
        for (String line : lines) {
            world.spawn(loc.clone().add(0, y, 0), org.bukkit.entity.ArmorStand.class, a -> {
                a.setMarker(true);
                a.setInvisible(true);
                a.setGravity(false);
                a.setInvulnerable(true);
                a.setPersistent(true);
                a.customName(c(line));
                a.setCustomNameVisible(true);
                a.getPersistentDataContainer().set(npcKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 2);
            });
            y -= 0.28;
        }
        s.sendMessage(c("&aĐã đặt NPC Nhà Buôn tại &f" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ() + "&a. Xoá: &f/dmm donbuonnpc remove"));
    }

    private void lookAtPlayers() {
        for (Player pl : Bukkit.getOnlinePlayers()) {
            for (org.bukkit.entity.Entity e : pl.getNearbyEntities(8, 4, 8)) {
                if (e instanceof org.bukkit.entity.Villager v && isNpc(v)) {
                    org.bukkit.util.Vector dir = pl.getEyeLocation().toVector().subtract(v.getEyeLocation().toVector());
                    v.setRotation((float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ())), 0);
                }
            }
        }
    }

    private final Map<java.util.UUID, Long> lastClick = new java.util.HashMap<>();

    /** Máy thật gửi cả interact_at lẫn interact (2 sự kiện), có client chỉ gửi một: mở theo cái đến trước, bỏ cái thứ hai trong 300 ms. */
    private void clicked(Player p, org.bukkit.inventory.EquipmentSlot hand) {
        if (hand != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastClick.put(p.getUniqueId(), now);
        if (last == null || now - last > 300) {
            open(p);
        }
    }

    @org.bukkit.event.EventHandler
    public void onNpcClick(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        if (isNpc(e.getRightClicked())) {
            e.setCancelled(true);
            clicked(e.getPlayer(), e.getHand());
        }
    }

    @org.bukkit.event.EventHandler
    public void onNpcClickAt(org.bukkit.event.player.PlayerInteractAtEntityEvent e) {
        if (isNpc(e.getRightClicked())) {
            e.setCancelled(true);
            clicked(e.getPlayer(), e.getHand());
        }
    }

    @org.bukkit.event.EventHandler
    public void onNpcDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (isNpc(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    private static String week() {
        LocalDate d = LocalDate.now(VN);
        return d.get(IsoFields.WEEK_BASED_YEAR) + "-W" + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    /** Sang tuần mới: chọn đơn mới, xoá lượt giao tuần cũ. */
    private void roll() {
        String w = week();
        if (w.equals(data.getString("tuan")) || pool.isEmpty()) {
            return;
        }
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < pool.size(); i++) {
            idx.add(i);
        }
        Collections.shuffle(idx, new Random(w.hashCode()));
        for (String k : new ArrayList<>(data.getKeys(false))) {
            data.set(k, null);
        }
        data.set("tuan", w);
        data.set("don", new ArrayList<>(idx.subList(0, Math.min(perWeek, idx.size()))));
        save();
        if (announce && data.getStringList("don").size() > 0) {
            PlayerSettings.broadcast("sukien", c("&6✉ Nhà Buôn có đơn hàng tuần mới: cần Tinh Luyện, trả " + trim(pct) + "% giá. Gặp Nhà Buôn ở &f/warp shop&6."));
        }
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được donbuon.yml: " + e.getMessage());
        }
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private double pay(Order o) {
        Refining r = Shared.refining;
        double v = 0;
        for (Map.Entry<String, Integer> e : o.need().entrySet()) {
            v += (r == null ? 0 : r.sellOf(e.getKey())) * e.getValue();
        }
        return Math.round(v * pct / 100.0);
    }

    void open(Player p) {
        roll();
        Refining r = Shared.refining;
        List<Integer> ids = data.getIntegerList("don");
        List<Integer> mine = data.getIntegerList("giao." + p.getUniqueId());
        InfoMenu m = InfoMenu.of("&6✉ Đơn nhà buôn tuần này").accent(Material.ORANGE_STAINED_GLASS_PANE)
                .top(Tip.of("&6✉ &fĐơn nhà buôn").accent("&6")
                        .desc("Mỗi thứ Hai nhà buôn ra đơn mới, cần món Tinh Luyện. Giao đủ là nhận xu (" + trim(pct) + "% giá bán cho server) và quà thêm.",
                                "Mỗi người giao mỗi đơn 1 lần/tuần" + (maxDeliveries > 0 ? "; mỗi đơn chỉ " + maxDeliveries + " lượt cả server, ai nhanh người đó được." : "."))
                        .line().kv("Tuần này bạn đã nhận", money(data.getDouble("tong." + p.getUniqueId(), 0)))
                        .apply(new ItemStack(Material.WRITABLE_BOOK)));
        if (ids.isEmpty()) {
            m.add(Tip.of("&7Chưa có đơn nào").accent("&7").apply(new ItemStack(Material.BARRIER)));
        }
        for (int k = 0; k < ids.size(); k++) {
            int slot = k;
            Order o = ids.get(k) < pool.size() ? pool.get(ids.get(k)) : null;
            if (o == null) {
                continue;
            }
            int used = data.getInt("dem." + k, 0);
            boolean done = mine.contains(k), full = maxDeliveries > 0 && used >= maxDeliveries;
            boolean enough = true;
            Tip t = Tip.of("&6Đơn " + (k + 1)).accent("&e").head("Cần");
            for (Map.Entry<String, Integer> e : o.need().entrySet()) {
                int have = r == null ? 0 : r.count(p, e.getKey());
                enough &= have >= e.getValue();
                t.item((have >= e.getValue() ? "&a" : "&c") + e.getValue() + "× &f" + (r == null ? e.getKey() : LEGACY.serialize(r.nameOf(e.getKey()))) + " &8(có " + have + ")");
            }
            t.line().kv("Trả", "&6" + money(pay(o)));
            List<String> ex = rewards.describe(o.extra());
            if (!ex.isEmpty()) {
                t.kv("Quà thêm", String.join(", ", ex));
            }
            if (maxDeliveries > 0) {
                t.kv("Lượt còn", (maxDeliveries - used) + "/" + maxDeliveries);
            }
            ItemStack icon = new ItemStack(done ? Material.LIME_DYE : full ? Material.GRAY_DYE : enough ? Material.EMERALD : Material.PAPER);
            if (done) {
                t.deny("Bạn đã giao đơn này tuần này");
            } else if (full) {
                t.deny("Đơn đã đủ lượt");
            } else if (enough) {
                t.click("Bấm để giao hàng");
            } else {
                t.deny("Chưa đủ món");
            }
            m.add(t.apply(icon), pl -> deliver(pl, slot));
        }
        m.open(p);
    }

    private void deliver(Player p, int k) {
        roll();
        List<Integer> ids = data.getIntegerList("don");
        Refining r = Shared.refining;
        if (k >= ids.size() || r == null || ids.get(k) >= pool.size()) {
            return;
        }
        Order o = pool.get(ids.get(k));
        List<Integer> mine = new ArrayList<>(data.getIntegerList("giao." + p.getUniqueId()));
        if (mine.contains(k) || maxDeliveries > 0 && data.getInt("dem." + k, 0) >= maxDeliveries) {
            open(p);
            return;
        }
        for (Map.Entry<String, Integer> e : o.need().entrySet()) {
            if (r.count(p, e.getKey()) < e.getValue()) {
                p.sendMessage(c("&cChưa đủ món cho đơn này."));
                open(p);
                return;
            }
        }
        o.need().forEach((id, n) -> r.take(p, id, n));
        double xu = pay(o);
        if (xu > 0 && VaultEconomy.available()) {
            VaultEconomy.deposit(p, xu);
        }
        List<String> got = rewards.give(p, o.extra());
        mine.add(k);
        data.set("giao." + p.getUniqueId(), mine);
        data.set("dem." + k, data.getInt("dem." + k, 0) + 1);
        data.set("tong." + p.getUniqueId(), data.getDouble("tong." + p.getUniqueId(), 0) + xu);
        save();
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
        p.sendMessage(c("&6✉ Đã giao đơn " + (k + 1) + ": nhận &e" + money(xu) + (got.isEmpty() ? "" : "&6, &f" + String.join("&7, &f", got)) + "&6."));
        plugin.getLogger().info("Đơn nhà buôn: " + p.getName() + " giao đơn " + (k + 1) + " (" + o.need() + ") nhận " + xu);
        DailyQuests.feature(p, "donbuon");
        open(p);
    }

    private static String money(double v) {
        return VaultEconomy.format(v);
    }

    String describe() {
        return pool.size() + " mẫu đơn, " + perWeek + " đơn/tuần, trả " + trim(pct) + "%, " + (maxDeliveries > 0 ? maxDeliveries + " lượt/đơn" : "không giới hạn lượt");
    }
}
