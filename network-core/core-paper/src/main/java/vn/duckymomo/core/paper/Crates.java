package vn.duckymomo.core.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gacha (/gacha, một lệnh duy nhất; 21/10): nhiều máy, mỗi máy một VÉ là món Thư viện đồ (công dụng chia-khoa). Mỗi lần quay mất 1 vé trong túi.
 * Phần thưởng dạng Rewards (vat = đồ thường, do = thư viện, xu…), tỉ lệ = weight / tổng, menu ghi rõ tỉ lệ.
 * Chìa lấy từ: quà "do <chìa> 1" ở điểm danh / nhiệm vụ… và chìa chơi online (online-key: mỗi N phút CHƠI THẬT, Activity, được 1 chìa).
 * Chìa KHÔNG bán bằng tiền thật / Vàng (luật Mojang về quay thưởng). Cầm chìa bấm chuột phải cũng mở đúng hòm.
 * Config: crates.{enabled, online-key{item, minutes}, list[{id, name, icon, key, source, prizes[{label, weight, rare, icon, rewards}]}]}
 * 21/10: thay hẳn /vongquay. Chìa online: mỗi online-key.minutes phút (rank nhanh hơn: quyền duckymomo.vongquay.nhanh.<5..50>, giữ tên
 * quyền cũ để Danh phận khỏi đổi), giữ tối đa online-key.max chìa trong túi (đầy thì ngừng đếm). Vé vòng quay còn tồn (vongquay.yml)
 * tự đổi thành chìa online lúc vào game. Placeholder: %dmmhom_chia% (chìa online đang có), %dmmhom_con% (phút tới chìa tiếp theo / "đầy").
 * Lưu phút chơi: plugins/DuckyMoMoCore/hom.yml
 */
final class Crates implements Listener {
    private static final int SPIN_SLOT = 49, BACK_SLOT = 48, INFO_SLOT = 45;

    private record Prize(String label, List<String> rewards, int weight, boolean rare, Material icon) { }

    private record Crate(String id, String name, Material icon, String key, String source, List<Prize> prizes, int total) { }

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        Crate crate;          // null = trang danh sách hòm
        boolean spinning;
        final Map<Integer, Crate> slots = new LinkedHashMap<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final JavaPlugin plugin;
    private final ConfigurationSection cfg;
    private final Rewards rewards;
    private final Activity activity;
    private final ItemLibrary library;
    private final Map<String, Crate> crates = new LinkedHashMap<>();
    private final String onlineKey;
    private final int onlineMinutes;
    private final int onlineMax;
    private final File file;
    private final YamlConfiguration data;
    private boolean dirty;

    Crates(JavaPlugin plugin, ConfigurationSection cfg, Rewards rewards, Activity activity, ItemLibrary library) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.rewards = rewards;
        this.activity = activity;
        this.library = library;
        this.file = new File(plugin.getDataFolder(), "hom.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        Shared.FORGET.add(id -> Shared.forget(data, file, id));
        this.onlineKey = cfg.getString("online-key.item", "").trim();
        this.onlineMinutes = Math.max(5, cfg.getInt("online-key.minutes", 60));
        this.onlineMax = Math.max(1, cfg.getInt("online-key.max", 5));
        for (Map<?, ?> m : cfg.getMapList("list")) {
            String id = str(m.get("id")).toLowerCase(Locale.ROOT);
            String key = str(m.get("key"));
            if (id.isEmpty() || key.isEmpty()) {
                continue;
            }
            List<Prize> ps = new ArrayList<>();
            int sum = 0;
            if (m.get("prizes") instanceof List<?> pl) {
                for (Object o : pl) {
                    if (!(o instanceof Map<?, ?> pm)) {
                        continue;
                    }
                    int w = pm.get("weight") instanceof Number n ? n.intValue() : 0;
                    List<String> rw = new ArrayList<>();
                    if (pm.get("rewards") instanceof List<?> l) {
                        l.forEach(x -> rw.add(String.valueOf(x)));
                    }
                    if (w <= 0 || rw.isEmpty()) {
                        continue;
                    }
                    Material ic = pm.get("icon") == null ? null : Material.matchMaterial(str(pm.get("icon")));
                    String label = str(pm.get("label"));
                    ps.add(new Prize(label.isEmpty() ? rw.get(0) : label, rw, w, Boolean.TRUE.equals(pm.get("rare")), ic));
                    sum += w;
                }
            }
            Material icon = Material.matchMaterial(str(m.get("icon")).isEmpty() ? "CHEST" : str(m.get("icon")));
            String name = str(m.get("name"));
            crates.put(id, new Crate(id, name.isEmpty() ? id : name, icon == null ? Material.CHEST : icon, key,
                    str(m.get("source")), ps, sum));
        }
        if (!onlineKey.isEmpty()) {
            Bukkit.getScheduler().runTaskTimer(plugin, this::tickMinute, 1200L, 1200L);
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static Component c(String s) {
        return Tip.c(s);
    }

    // ---- chìa chơi online

    /** Rank nhận chìa nhanh hơn: quyền duckymomo.vongquay.nhanh.<5..50> (% bớt thời gian, lấy mức cao nhất; Danh phận cấp). */
    int minutesPerKey(OfflinePlayer o) {
        Player p = o == null ? null : o.getPlayer();
        if (p != null) {
            for (int pct = 50; pct >= 5; pct -= 5) {
                if (p.hasPermission("duckymomo.vongquay.nhanh." + pct)) {
                    return Math.max(1, (int) Math.round(onlineMinutes * (100 - pct) / 100.0));
                }
            }
        }
        return onlineMinutes;
    }

    int minutesToNext(OfflinePlayer p) {
        return Math.max(0, minutesPerKey(p) - data.getInt(p.getUniqueId() + ".phut", 0));
    }

    private boolean full(Player p) {
        return count(p, onlineKey) >= onlineMax;
    }

    private void tickMinute() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (activity != null && !activity.activeNow(p.getUniqueId())) {
                continue;   // treo máy không tính
            }
            if (full(p)) {
                continue;   // đủ chìa chưa mở: ngừng đếm, mở bớt mới tích tiếp
            }
            String k = p.getUniqueId().toString();
            int phut = data.getInt(k + ".phut", 0) + 1;
            if (phut >= minutesPerKey(p)) {
                phut = 0;
                rewards.give(p, List.of("do " + onlineKey + " 1"));   // túi đầy thì rơi dưới chân
                data.set(k + ".da-nhan", data.getInt(k + ".da-nhan") + 1);
                p.sendMessage(c("&b▤ Bạn nhận 1 &f" + keyName(onlineKey) + " &b(chơi đủ " + minutesPerKey(p) + " phút). Gõ &f/gacha &bđể quay!"));
                ScreenNotify.show(p, "&b+1 vé gacha", "&7Gõ &f/gacha &7để quay");
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
            }
            data.set(k + ".phut", phut);
            data.set(k + ".ten", p.getName());
            dirty = true;
        }
        flush();
    }

    void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được hom.yml: " + e.getMessage());
        }
    }

    // ---- chìa trong túi

    private String keyName(String key) {
        ConfigurationSection s = library.section(key);
        String n = s == null ? "" : s.getString("ten-goi", "");
        return n.isEmpty() ? "vé " + key : n;
    }

    private static int count(Player p, String key) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && key.equals(ItemLibrary.idOf(it))) {
                n += it.getAmount();
            }
        }
        return n;
    }

    private static boolean take(Player p, String key) {
        ItemStack[] inv = p.getInventory().getStorageContents();
        for (int i = 0; i < inv.length; i++) {
            ItemStack it = inv[i];
            if (it != null && key.equals(ItemLibrary.idOf(it))) {
                if (it.getAmount() > 1) {
                    it.setAmount(it.getAmount() - 1);
                } else {
                    p.getInventory().setItem(i, null);
                }
                return true;
            }
        }
        return false;
    }

    /** Chìa lấy từ đâu: tự dò điểm danh, nhiệm vụ, chìa online + dòng ghi thêm (source) của hòm. */
    private List<String> sources(Crate cr) {
        List<String> out = new ArrayList<>();
        ConfigurationSection root = plugin.getConfig();
        String want = "do " + cr.key();
        java.util.function.Predicate<List<String>> has = l -> l.stream().anyMatch(s -> (s.trim() + " ").startsWith(want + " "));
        if (has.test(root.getStringList("daily-checkin.daily-rewards"))) {
            out.add("Điểm danh mỗi ngày &7(/diemdanh)");
        }
        ConfigurationSection ms = root.getConfigurationSection("daily-checkin.milestones");
        if (ms != null) {
            for (String d : ms.getKeys(false)) {
                if (has.test(ms.getStringList(d))) {
                    out.add("Mốc điểm danh ngày thứ " + d);
                }
            }
        }
        if (has.test(root.getStringList("daily-quests.complete-all-rewards"))) {
            out.add("Xong hết nhiệm vụ ngày &7(/nhiemvu)");
        }
        if (cr.key().equals(onlineKey)) {
            out.add("Chơi thật " + onlineMinutes + " phút &7(treo máy không tính; giữ tối đa " + onlineMax + " vé)");
        }
        if (!cr.source().isEmpty()) {
            out.add(cr.source());
        }
        return out;
    }

    // ---- menu

    private ItemStack prizeIcon(Crate cr, Prize pz) {
        ItemStack it = null;
        String[] a = pz.rewards().get(0).trim().split("\\s+");
        if (pz.icon() != null) {
            it = new ItemStack(pz.icon());
        } else if (a[0].equalsIgnoreCase("do") && a.length > 1) {
            it = library.build(a[1], 1);
        } else if (a[0].equalsIgnoreCase("vat") && a.length > 1) {
            Material m = Material.matchMaterial(a[1]);
            int n = 1;
            try {
                n = a.length > 2 ? Math.max(1, Integer.parseInt(a[2])) : 1;
            } catch (NumberFormatException ignored) {
                // giữ 1
            }
            it = m == null || !m.isItem() ? null : new ItemStack(m, Math.min(64, n));
        } else if (a[0].equalsIgnoreCase("xu")) {
            it = new ItemStack(Material.GOLD_INGOT);
        } else if (a[0].equalsIgnoreCase("manh")) {
            it = new ItemStack(Material.AMETHYST_SHARD);
        }
        if (it == null) {
            it = new ItemStack(Material.PAPER);
        }
        Tip t = Tip.of((pz.rare() ? "&6✦ &6" : "&e◇ &f") + pz.label()).accent(pz.rare() ? "&6" : "&e");
        if (pz.rare()) {
            t.sub("hiếm");
        }
        return t.kv("Tỉ lệ trúng", String.format(Locale.ROOT, "&f%.1f%%", 100.0 * pz.weight() / cr.total()))
                .line().head("Phần thưởng").items(rewards.describe(pz.rewards())).apply(it);
    }

    private ItemStack crateIcon(Player p, Crate cr) {
        int have = count(p, cr.key());
        Tip t = Tip.of("&6❖ &f" + cr.name()).sub(cr.prizes().size() + " phần thưởng")
                .desc("Mỗi lần quay mất 1 " + keyName(cr.key()))
                .line().kv("Vé bạn đang có", (have > 0 ? "&a" : "&c") + have);
        List<String> src = sources(cr);
        if (!src.isEmpty()) {
            t.head("Vé lấy từ").items(src);
        }
        t.line().click("Bấm để xem phần thưởng và quay");
        return t.apply(new ItemStack(cr.icon()));
    }

    private ItemStack spinButton(Player p, Crate cr) {
        int have = count(p, cr.key());
        Tip t = Tip.of(have > 0 ? "&a✦ &fQuay ngay" : "&7✦ &fChưa có vé").sub(have + " vé").accent(have > 0 ? "&a" : "&7")
                .desc("Mỗi lần quay mất 1 " + keyName(cr.key())).line();
        if (have > 0) {
            t.click("Bấm để quay");
        } else {
            t.deny("Cần " + keyName(cr.key()) + " trong túi");
        }
        return t.apply(new ItemStack(have > 0 ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE));
    }

    private static ItemStack pane(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(" "));
        meta.setHideTooltip(true);
        it.setItemMeta(meta);
        return it;
    }

    void openList(Player p) {
        Holder h = new Holder();
        int rows = Math.min(6, Math.max(3, (crates.size() + 8) / 9 + 2));
        h.inv = Bukkit.createInventory(h, rows * 9, c("&0❖ Gacha · quay thưởng"));
        ItemStack bg = pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int s = 0; s < rows * 9; s++) {
            h.inv.setItem(s, bg);
        }
        List<Crate> list = new ArrayList<>(crates.values());
        // xếp giữa hàng 2 (ít hòm) hoặc lấp từ ô 10
        int start = list.size() <= 7 ? 9 + (9 - list.size()) / 2 : 9;
        for (int i = 0; i < list.size() && start + i < rows * 9 - 9; i++) {
            h.inv.setItem(start + i, crateIcon(p, list.get(i)));
            h.slots.put(start + i, list.get(i));
        }
        Tip info = Tip.of("&e❖ &fGacha là gì?").accent("&e").desc("Mỗi máy gacha cần vé riêng, quay một lần mất một vé",
                        "Menu ghi rõ tỉ lệ từng phần thưởng")
                .line().note("Vé miễn phí: điểm danh, chơi online… KHÔNG bán bằng tiền thật");
        if (!onlineKey.isEmpty()) {
            info.kv("Vé online tiếp theo", full(p) ? "&cđủ " + onlineMax + " vé, quay bớt đã" : "sau " + minutesToNext(p) + " phút");
        }
        info.line().click("Cầm vé bấm chuột phải", "mở đúng máy gacha");
        h.inv.setItem(rows * 9 - 5, info.apply(new ItemStack(Material.BOOK)));
        MenuFrame.paint(h.inv, Material.ORANGE_STAINED_GLASS_PANE);
        p.openInventory(h.inv);
    }

    void open(Player p, Crate cr) {
        refreshTickets(p);
        Holder h = new Holder();
        h.crate = cr;
        h.inv = Bukkit.createInventory(h, 54, c("&0❖ " + org.bukkit.ChatColor.stripColor(
                org.bukkit.ChatColor.translateAlternateColorCodes('&', cr.name()))));
        ItemStack empty = pane(Material.LIGHT_GRAY_STAINED_GLASS_PANE), bar = pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int s = 0; s < 54; s++) {
            h.inv.setItem(s, s < 36 ? empty : bar);
        }
        for (int i = 0; i < cr.prizes().size() && i < 36; i++) {
            h.inv.setItem(i, prizeIcon(cr, cr.prizes().get(i)));
        }
        Tip info = Tip.of("&e❖ &fVé lấy từ đâu?").accent("&e").desc(keyName(cr.key()), "Vé miễn phí, không bán bằng tiền thật").line();
        List<String> src = sources(cr);
        if (src.isEmpty()) {
            info.note("Chưa đặt nguồn vé cho máy này");
        } else {
            info.items(src);
        }
        if (cr.key().equals(onlineKey)) {
            info.kv("Vé online tiếp theo", full(p) ? "&cđủ " + onlineMax + " vé, quay bớt đã" : "sau " + minutesToNext(p) + " phút");
        }
        h.inv.setItem(INFO_SLOT, info.apply(new ItemStack(Material.BOOK)));
        h.inv.setItem(BACK_SLOT, Tip.of("&c◂ &fCác máy gacha khác").accent("&c").click("Bấm để quay lại").apply(new ItemStack(Material.ARROW)));
        h.inv.setItem(SPIN_SLOT, spinButton(p, cr));
        MenuFrame.paint(h.inv, Material.ORANGE_STAINED_GLASS_PANE);   // 30/10: ô quà trống + thanh dưới thành khung cam
        p.openInventory(h.inv);
    }

    private static Prize pick(Crate cr) {
        int r = ThreadLocalRandom.current().nextInt(cr.total());
        for (Prize pz : cr.prizes()) {
            r -= pz.weight();
            if (r < 0) {
                return pz;
            }
        }
        return cr.prizes().get(cr.prizes().size() - 1);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory() || h.spinning) {
            return;
        }
        int slot = e.getRawSlot();
        if (h.crate == null) {
            Crate cr = h.slots.get(slot);
            if (cr != null) {
                open(p, cr);
            }
            return;
        }
        if (slot == BACK_SLOT) {
            openList(p);
            return;
        }
        if (slot != SPIN_SLOT) {
            return;
        }
        Crate cr = h.crate;
        if (cr.total() <= 0) {
            p.sendMessage(c("&cMáy gacha này chưa có phần thưởng."));
            return;
        }
        if (!take(p, cr.key())) {
            p.sendMessage(c("&cBạn không có &f" + keyName(cr.key()) + "&c trong túi."));
            return;
        }
        Prize win = pick(cr);
        List<String> got = rewards.give(p, win.rewards());   // phát ngay; hiệu ứng chỉ để xem
        data.set(p.getUniqueId() + ".da-quay." + cr.id(), data.getInt(p.getUniqueId() + ".da-quay." + cr.id()) + 1);
        dirty = true;
        flush();
        plugin.getLogger().info("Gacha " + cr.id() + ": " + p.getName() + " trúng " + win.label());
        DailyQuests.feature(p, "gacha");
        h.spinning = true;
        final int[] step = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            step[0]++;
            if (step[0] < 16) {
                h.inv.setItem(SPIN_SLOT, prizeIcon(cr, cr.prizes().get(ThreadLocalRandom.current().nextInt(cr.prizes().size()))));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.0f + step[0] * 0.05f);
                return;
            }
            task.cancel();
            h.spinning = false;
            h.inv.setItem(SPIN_SLOT, prizeIcon(cr, win));
            p.playSound(p.getLocation(), win.rare() ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1f);
            p.sendMessage(c("&a✦ " + cr.name() + "&a: bạn trúng &f" + win.label() + (got.isEmpty() ? "" : " &7(" + String.join("&7, ", got) + "&7)")));
            if (win.rare()) {
                ScreenNotify.invite("&6✦ &e" + p.getName() + " &fquay " + cr.name() + " &ftrúng &e" + win.label() + "&f! Thử vận may:", "/gacha");
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.getOpenInventory().getTopInventory().getHolder() == h) {
                    h.inv.setItem(SPIN_SLOT, spinButton(p, cr));
                }
            }, 40L);
        }, 2L, 2L);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    /** Cầm chìa bấm chuột phải: mở đúng hòm của chìa đó. */
    @EventHandler(ignoreCancelled = false)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        String id = ItemLibrary.idOf(e.getItem());
        if (id == null) {
            return;
        }
        for (Crate cr : crates.values()) {
            if (cr.key().equals(id)) {
                e.setCancelled(true);
                open(e.getPlayer(), cr);
                return;
            }
        }
    }

    /** Vé /vongquay cũ còn tồn -> chìa online (mỗi vé 1 chìa), một lần. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        migrateSpinTickets(e.getPlayer());
        refreshTickets(e.getPlayer());
    }

    /**
     * Vé đã phát giữ tên/mô tả lúc phát (27/10: vé cũ còn ghi "/hom" sau khi đổi sang /gacha). Vé trong túi + rương Ender khác Thư viện đồ
     * (tên, mô tả) thì dựng lại theo Thư viện, giữ số lượng. Gọi khi vào game và khi mở gacha.
     */
    void refreshTickets(Player p) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        crates.values().forEach(cr -> keys.add(cr.key()));
        for (org.bukkit.inventory.Inventory inv : List.of(p.getInventory(), p.getEnderChest())) {
            ItemStack[] all = inv.getContents();
            boolean changed = false;
            for (int i = 0; i < all.length; i++) {
                String id = ItemLibrary.idOf(all[i]);
                if (id == null || !keys.contains(id)) {
                    continue;
                }
                ItemStack fresh = library.build(id, all[i].getAmount());
                if (fresh == null || !fresh.hasItemMeta()) {
                    continue;
                }
                var a = all[i].getItemMeta();
                var b = fresh.getItemMeta();
                if (java.util.Objects.equals(a.lore(), b.lore()) && java.util.Objects.equals(a.displayName(), b.displayName())) {
                    continue;
                }
                all[i] = fresh;
                changed = true;
            }
            if (changed) {
                inv.setContents(all);
            }
        }
    }

    void migrateSpinTickets(Player p) {
        if (onlineKey.isEmpty()) {
            return;
        }
        File vf = new File(plugin.getDataFolder(), "vongquay.yml");
        if (!vf.isFile()) {
            return;
        }
        YamlConfiguration vq = YamlConfiguration.loadConfiguration(vf);
        String k = p.getUniqueId() + ".ve";
        int ve = vq.getInt(k, 0);
        if (ve <= 0) {
            return;
        }
        vq.set(k, 0);
        try {
            vq.save(vf);
        } catch (IOException ex) {
            plugin.getLogger().warning("Không lưu được vongquay.yml: " + ex.getMessage());
            return;
        }
        rewards.give(p, List.of("do " + onlineKey + " " + Math.min(64, ve)));
        p.sendMessage(c("&b▤ Vòng quay đã gộp vào Gacha: " + ve + " vé cũ của bạn đổi thành &f" + ve + " " + keyName(onlineKey) + "&b. Gõ &f/gacha&b."));
        plugin.getLogger().info("Gacha: đổi " + ve + " vé vòng quay của " + p.getName() + " thành vé online");
    }

    /** %dmmhom_chia%, %dmmhom_con% (gọi chỉ khi có PlaceholderAPI). */
    me.clip.placeholderapi.expansion.PlaceholderExpansion placeholders() {
        return new me.clip.placeholderapi.expansion.PlaceholderExpansion() {
            @Override
            public String getIdentifier() {
                return "dmmhom";
            }

            @Override
            public String getAuthor() {
                return "DuckyMoMo";
            }

            @Override
            public String getVersion() {
                return "1";
            }

            @Override
            public boolean persist() {
                return true;
            }

            @Override
            public String onRequest(OfflinePlayer player, String params) {
                Player p = player == null ? null : player.getPlayer();
                if (p == null || onlineKey.isEmpty()) {
                    return "";
                }
                return switch (params) {
                    case "chia" -> String.valueOf(count(p, onlineKey));
                    case "con" -> full(p) ? "đầy" : minutesToNext(p) + "p";
                    default -> null;
                };
            }
        };
    }

    boolean command(CommandSender s, String[] args) {
        if (args.length >= 3 && args[0].equalsIgnoreCase("give") && s.hasPermission("duckymomo.admin")) {
            Player t = Bukkit.getPlayerExact(args[1]);
            Crate cr = crates.get(args[2].toLowerCase(Locale.ROOT));
            if (t == null || cr == null) {
                s.sendMessage("Cần người đang online và mã máy gacha (" + String.join(", ", crates.keySet()) + ").");
                return true;
            }
            int n = 1;
            try {
                n = args.length > 3 ? Math.max(1, Math.min(64, Integer.parseInt(args[3]))) : 1;
            } catch (NumberFormatException ignored) {
                // giữ 1
            }
            rewards.give(t, List.of("do " + cr.key() + " " + n));
            s.sendMessage("Đã tặng " + n + " " + keyName(cr.key()) + " cho " + t.getName() + ".");
            return true;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("/gacha give <người> <mã máy> [số vé]");
            return true;
        }
        if (crates.isEmpty()) {
            p.sendMessage(c("&cChưa có máy gacha nào."));
            return true;
        }
        Crate cr = args.length > 0 ? crates.get(args[0].toLowerCase(Locale.ROOT)) : null;
        if (cr != null) {
            open(p, cr);
        } else {
            openList(p);
        }
        return true;
    }
}
