package vn.duckymomo.core.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sàn thu mua (/donhang, ý từ UniMC "Orders"). Người mua đặt "cần N món X, giá P/món": tiền N*P bị GIỮ trước (cộng phí đăng đơn,
 * phí không hoàn = rút tiền khỏi nền kinh tế). Người khác mở sàn, bấm đơn để giao hàng từ túi, nhận tiền NGAY; hàng nằm trong kho của đơn
 * chờ người mua nhận (/donhang cuatoi), kể cả khi người mua offline. Hết hạn hoặc huỷ: trả lại tiền của phần chưa giao.
 * Chỉ nhận đồ "trơn" (không tên riêng, không phù phép, không hỏng, không chứa đồ) để không ai giao đồ khác loại.
 * Lưu: plugins/DuckyMoMoCore/donhang.yml
 */
final class Orders implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final int PAGE = 45;

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        final boolean mine;
        final int page;
        final Map<Integer, Integer> slots = new HashMap<>();

        Holder(boolean mine, int page) {
            this.mine = mine;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final JavaPlugin plugin;
    private final ConfigurationSection cfg;
    private final File file;
    private final YamlConfiguration data;
    private final Set<Material> blocked = new HashSet<>();

    Orders(JavaPlugin plugin, ConfigurationSection cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.file = new File(plugin.getDataFolder(), "donhang.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        for (String s : cfg.getStringList("blacklist")) {
            Material m = Material.matchMaterial(s);
            if (m != null) {
                blocked.add(m);
            }
        }
    }

    void start() {
        // hết hạn: kiểm tra mỗi 10 phút
        Bukkit.getScheduler().runTaskTimer(plugin, this::expire, 200L, 12000L);
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    private static Component name(Material m) {
        return Component.translatable(m.translationKey());
    }

    private static String money(double v) {
        return VaultEconomy.format(v);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được donhang.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ dữ liệu đơn: don.<id>.{owner, ten, vat, can, gia, da, kho, tao, dong}

    private ConfigurationSection order(int id) {
        return data.getConfigurationSection("don." + id);
    }

    private List<Integer> ids() {
        ConfigurationSection s = data.getConfigurationSection("don");
        List<Integer> out = new ArrayList<>();
        if (s != null) {
            for (String k : s.getKeys(false)) {
                try {
                    out.add(Integer.parseInt(k));
                } catch (NumberFormatException ignored) {
                    // khoá lạ: bỏ
                }
            }
        }
        out.sort(Comparator.reverseOrder());
        return out;
    }

    private static boolean open(ConfigurationSection o) {
        return !o.getBoolean("dong") && o.getInt("da") < o.getInt("can");
    }

    private static Material mat(ConfigurationSection o) {
        Material m = Material.matchMaterial(o.getString("vat", "STONE"));
        return m == null ? Material.STONE : m;
    }

    private long expiresAt(ConfigurationSection o) {
        return o.getLong("tao") + Duration.ofDays(Math.max(1, cfg.getInt("days", 7))).toMillis();
    }

    private static String left(long ms) {
        long h = Math.max(0, ms) / 3_600_000L;
        return h >= 24 ? (h / 24) + " ngày " + (h % 24) + " giờ" : h + " giờ";
    }

    /** Đóng đơn, trả tiền phần chưa giao. Đơn còn hàng chưa nhận thì giữ lại tới khi nhận xong. */
    private void close(int id, String why) {
        ConfigurationSection o = order(id);
        if (o == null || o.getBoolean("dong")) {
            return;
        }
        int rest = o.getInt("can") - o.getInt("da");
        double refund = rest * o.getDouble("gia");
        OfflinePlayer owner = Bukkit.getOfflinePlayer(UUID.fromString(o.getString("owner")));
        if (refund > 0) {
            VaultEconomy.deposit(owner, refund);
        }
        o.set("dong", true);
        if (o.getInt("kho") <= 0) {
            data.set("don." + id, null);
        }
        save();
        plugin.getLogger().info("Đơn #" + id + " đóng (" + why + "), trả " + refund + " cho " + o.getString("ten"));
        if (owner.getPlayer() != null) {
            owner.getPlayer().sendMessage(c("&6[Đơn hàng] &fĐơn &e#" + id + " &fđã đóng (" + why + ")"
                    + (refund > 0 ? ", trả lại &a" + money(refund) : "") + "&f."));
        }
    }

    private void expire() {
        long now = System.currentTimeMillis();
        for (int id : ids()) {
            ConfigurationSection o = order(id);
            if (o != null && !o.getBoolean("dong") && now > expiresAt(o)) {
                close(id, "hết hạn");
            }
        }
    }

    // ------------------------------------------------------------ đồ trơn

    private static boolean plain(ItemStack it, Material m) {
        return it != null && it.getType() == m && it.isSimilar(new ItemStack(m));
    }

    private static int countPlain(Player p, Material m) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (plain(it, m)) {
                n += it.getAmount();
            }
        }
        return n;
    }

    private static void takePlain(Player p, Material m, int amount) {
        ItemStack[] inv = p.getInventory().getStorageContents();
        for (int i = 0; i < inv.length && amount > 0; i++) {
            if (plain(inv[i], m)) {
                int t = Math.min(amount, inv[i].getAmount());
                inv[i].setAmount(inv[i].getAmount() - t);
                amount -= t;
                if (inv[i].getAmount() <= 0) {
                    inv[i] = null;
                }
            }
        }
        p.getInventory().setStorageContents(inv);
    }

    // ------------------------------------------------------------ menu

    private ItemStack icon(int id, ConfigurationSection o, boolean mine) {
        Material m = mat(o);
        ItemStack it = new ItemStack(m.isItem() ? m : Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        int can = o.getInt("can");
        int da = o.getInt("da");
        double gia = o.getDouble("gia");
        meta.displayName(c("&e#" + id + " &f").append(name(m)));
        List<Component> lore = new ArrayList<>();
        lore.add(c("&7Người đặt: &f" + o.getString("ten")));
        lore.add(c("&7Cần mua: &a" + can + "x ").append(name(m)));
        lore.add(c("&7Tiến độ: &f" + da + "/" + can + " &8(" + (can == 0 ? 0 : da * 100 / can) + "%)"));
        lore.add(c("&7Đơn giá: &a" + money(gia)));
        lore.add(c("&7Còn trả: &a" + money((can - da) * gia)));
        lore.add(c(o.getBoolean("dong") ? "&8Đã đóng" : "&7Hết hạn sau: &f" + left(expiresAt(o) - System.currentTimeMillis())));
        lore.add(Component.empty());
        if (mine) {
            int kho = o.getInt("kho");
            lore.add(c(kho > 0 ? "&a» Chuột trái: nhận " + kho + " món đã giao" : "&8Chưa có hàng giao tới"));
            if (!o.getBoolean("dong")) {
                lore.add(c("&c» Chuột phải: huỷ đơn &8(/donhang huy " + id + ")"));
            }
        } else {
            lore.add(c("&a» Bấm để giao hàng &7(chỉ đồ thường, không tên/phù phép)"));
        }
        meta.lore(lore);
        it.setItemMeta(meta);
        it.setAmount(Math.max(1, Math.min(64, can - da)));
        return it;
    }

    private static ItemStack button(Material m, String text, String sub) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(text));
        if (sub != null) {
            meta.lore(List.of(c(sub)));
        }
        it.setItemMeta(meta);
        return it;
    }

    void openMarket(Player p, int page) {
        List<Integer> list = new ArrayList<>();
        for (int id : ids()) {
            ConfigurationSection o = order(id);
            if (o != null && open(o)) {
                list.add(id);
            }
        }
        render(p, list, false, page, "&2&lSÀN THU MUA &8(" + list.size() + " đơn)");
    }

    void openMine(Player p) {
        List<Integer> list = new ArrayList<>();
        for (int id : ids()) {
            ConfigurationSection o = order(id);
            if (o != null && p.getUniqueId().toString().equals(o.getString("owner"))) {
                list.add(id);
            }
        }
        if (list.isEmpty()) {
            p.sendMessage(c("&7Bạn chưa có đơn nào. Tạo: cầm món cần mua, gõ &e/donhang tao <số lượng> <đơn giá>"));
            return;
        }
        render(p, list, true, 0, "&6&lĐƠN CỦA TÔI");
    }

    private void render(Player p, List<Integer> list, boolean mine, int page, String title) {
        int pages = Math.max(1, (list.size() + PAGE - 1) / PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        Holder h = new Holder(mine, page);
        h.inv = Bukkit.createInventory(h, 54, c(title));
        for (int i = 0; i < PAGE && page * PAGE + i < list.size(); i++) {
            int id = list.get(page * PAGE + i);
            h.inv.setItem(i, icon(id, order(id), mine));
            h.slots.put(i, id);
        }
        if (page > 0) {
            h.inv.setItem(45, button(Material.ARROW, "&e« Trang trước", null));
        }
        h.inv.setItem(49, mine ? button(Material.EMERALD, "&aSàn thu mua", "&7Xem đơn của mọi người")
                : button(Material.CHEST, "&6Đơn của tôi", "&7Nhận hàng, huỷ đơn"));
        h.inv.setItem(48, button(Material.BOOK, "&eTạo đơn mới", "&7Cầm món cần mua, gõ &f/donhang tao <số> <giá>"));
        if (page < pages - 1) {
            h.inv.setItem(53, button(Material.ARROW, "&eTrang sau »", null));
        }
        if (list.isEmpty()) {
            h.inv.setItem(22, button(Material.BARRIER, "&7Chưa có đơn nào", "&7Hãy là người đặt đơn đầu tiên!"));
        }
        p.openInventory(h.inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getRawSlot() < 0 || e.getRawSlot() >= 54) {
            return;
        }
        int slot = e.getRawSlot();
        if (slot == 45 && h.page > 0) {
            openMarket(p, h.page - 1);
            return;
        }
        if (slot == 53) {
            openMarket(p, h.page + 1);
            return;
        }
        if (slot == 49) {
            if (h.mine) {
                openMarket(p, 0);
            } else {
                openMine(p);
            }
            return;
        }
        Integer id = h.slots.get(slot);
        if (id == null) {
            return;
        }
        if (h.mine) {
            if (e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT) {
                cancel(p, id);
            } else {
                collect(p, id);
            }
            openMine(p);
        } else {
            deliver(p, id);
            openMarket(p, h.page);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        String me = e.getPlayer().getUniqueId().toString();
        int waiting = 0;
        for (int id : ids()) {
            ConfigurationSection o = order(id);
            if (o != null && me.equals(o.getString("owner"))) {
                waiting += o.getInt("kho");
            }
        }
        if (waiting > 0) {
            int w = waiting;
            JoinNotes.add(e.getPlayer(), 40, c("&6[Đơn hàng] &fCó &a" + w + " &fmón đã giao chờ bạn nhận: &e/donhang cuatoi"));
        }
    }

    // ------------------------------------------------------------ hành động

    private void create(Player p, String[] args) {
        if (args.length < 3) {
            p.sendMessage(c("&eCầm món cần mua rồi gõ: &f/donhang tao <số lượng> <đơn giá>"));
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        Material m = hand.getType();
        if (m.isAir() || !plain(hand, m)) {
            p.sendMessage(c("&cCầm trên tay MỘT món đồ thường (không tên riêng, không phù phép, không hỏng) làm mẫu."));
            return;
        }
        if (blocked.contains(m)) {
            p.sendMessage(c("&cKhông được đặt mua món này."));
            return;
        }
        int amount;
        double price;
        try {
            amount = Integer.parseInt(args[1]);
            price = Math.round(Double.parseDouble(args[2].replace(",", ".")) * 100) / 100.0;
        } catch (NumberFormatException ex) {
            p.sendMessage(c("&cSố lượng và giá phải là số. Ví dụ: &f/donhang tao 1000 0.5"));
            return;
        }
        int maxAmount = cfg.getInt("max-amount", 6400);
        double minPrice = cfg.getDouble("min-price", 0.01);
        double maxPrice = cfg.getDouble("max-price", 100000);
        if (amount < 1 || amount > maxAmount) {
            p.sendMessage(c("&cSố lượng từ 1 tới " + maxAmount + "."));
            return;
        }
        if (price < minPrice || price > maxPrice) {
            p.sendMessage(c("&cĐơn giá từ " + money(minPrice) + " tới " + money(maxPrice) + "."));
            return;
        }
        int mineCount = 0;
        for (int id : ids()) {
            ConfigurationSection o = order(id);
            if (o != null && open(o) && p.getUniqueId().toString().equals(o.getString("owner"))) {
                mineCount++;
            }
        }
        int max = cfg.getInt("max-per-player", 5);
        for (int n = 50; n >= 1; n--) {   // rank (danh phận, Media…) cấp duckymomo.donhang.<N>: lấy số cao nhất (không thấp hơn mặc định)
            if (n > max && p.hasPermission("duckymomo.donhang." + n)) {
                max = n;
                break;
            }
        }
        if (mineCount >= max && !p.hasPermission("duckymomo.donhang.khonggioihan")) {
            p.sendMessage(c("&cBạn đang có " + mineCount + " đơn mở (tối đa " + max + "). Huỷ bớt ở &f/donhang cuatoi&c."));
            return;
        }
        double total = amount * price;
        double fee = Math.ceil(total * cfg.getDouble("fee-percent", 2)) / 100.0;
        if (!VaultEconomy.withdraw(p, total + fee)) {
            p.sendMessage(c("&cKhông đủ tiền: cần &f" + money(total + fee) + " &c(" + money(total) + " giữ cho người giao + phí "
                    + money(fee) + ")."));
            return;
        }
        int id = data.getInt("tiep", 1);
        data.set("tiep", id + 1);
        String b = "don." + id + ".";
        data.set(b + "owner", p.getUniqueId().toString());
        data.set(b + "ten", p.getName());
        data.set(b + "vat", m.name());
        data.set(b + "can", amount);
        data.set(b + "gia", price);
        data.set(b + "da", 0);
        data.set(b + "kho", 0);
        data.set(b + "tao", System.currentTimeMillis());
        save();
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        p.sendMessage(c("&a✔ Đã đăng đơn &e#" + id + "&a: cần " + amount + "x ").append(name(m))
                .append(c(" &agiá " + money(price) + "/món. Đã giữ " + money(total) + " (phí " + money(fee) + ")."
                        + " Hàng giao tới nhận ở &f/donhang cuatoi&a.")));
        plugin.getLogger().info("Đơn #" + id + ": " + p.getName() + " mua " + amount + " " + m + " giá " + price);
    }

    private void deliver(Player p, int id) {
        ConfigurationSection o = order(id);
        if (o == null || !open(o)) {
            p.sendMessage(c("&cĐơn này đã đóng."));
            return;
        }
        if (p.getUniqueId().toString().equals(o.getString("owner"))) {
            p.sendMessage(c("&cĐây là đơn của bạn."));
            return;
        }
        Material m = mat(o);
        int need = o.getInt("can") - o.getInt("da");
        int have = countPlain(p, m);
        if (have <= 0) {
            p.sendMessage(c("&cTúi bạn không có ").append(name(m)).append(c(" &cthường (không tên, không phù phép).")));
            return;
        }
        int n = Math.min(need, have);
        double pay = n * o.getDouble("gia");
        takePlain(p, m, n);
        VaultEconomy.deposit(p, pay);
        o.set("da", o.getInt("da") + n);
        o.set("kho", o.getInt("kho") + n);
        boolean done = o.getInt("da") >= o.getInt("can");
        if (done) {
            o.set("dong", true);
        }
        save();
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f);
        p.sendMessage(c("&a✔ Đã giao " + n + "x ").append(name(m)).append(c(" &acho " + o.getString("ten") + ", nhận &f" + money(pay) + "&a.")));
        plugin.getLogger().info("Đơn #" + id + ": " + p.getName() + " giao " + n + " " + m + " nhận " + pay);
        Player owner = Bukkit.getPlayer(UUID.fromString(o.getString("owner")));
        if (owner != null) {
            owner.sendMessage(c("&6[Đơn hàng] &f" + p.getName() + " vừa giao &a" + n + "x ").append(name(m))
                    .append(c(" &fcho đơn &e#" + id + (done ? " &a(đủ hàng)" : "") + "&f. Nhận: &e/donhang cuatoi")));
        }
    }

    private void collect(Player p, int id) {
        ConfigurationSection o = order(id);
        if (o == null || !p.getUniqueId().toString().equals(o.getString("owner"))) {
            return;
        }
        int kho = o.getInt("kho");
        if (kho <= 0) {
            p.sendMessage(c("&7Đơn &e#" + id + " &7chưa có hàng giao tới."));
            return;
        }
        Material m = mat(o);
        int given = 0;
        int max = m.getMaxStackSize();
        while (given < kho) {
            int n = Math.min(max, kho - given);
            Map<Integer, ItemStack> rest = p.getInventory().addItem(new ItemStack(m, n));
            int notFit = rest.values().stream().mapToInt(ItemStack::getAmount).sum();
            given += n - notFit;
            if (notFit > 0) {
                break;
            }
        }
        o.set("kho", kho - given);
        if (o.getBoolean("dong") && kho - given <= 0) {
            data.set("don." + id, null);
        }
        save();
        p.sendMessage(c("&a✔ Đã nhận " + given + "x ").append(name(m))
                .append(c(kho - given > 0 ? " &e(túi đầy, còn " + (kho - given) + " chờ nhận)" : "")));
    }

    private void cancel(Player p, int id) {
        ConfigurationSection o = order(id);
        if (o == null || (!p.getUniqueId().toString().equals(o.getString("owner")) && !p.hasPermission("duckymomo.admin"))) {
            p.sendMessage(c("&cKhông có đơn #" + id + " của bạn."));
            return;
        }
        if (o.getBoolean("dong")) {
            p.sendMessage(c("&7Đơn &e#" + id + " &7đã đóng."));
            return;
        }
        close(id, p.getUniqueId().toString().equals(o.getString("owner")) ? "bạn huỷ" : "admin huỷ");
    }

    boolean command(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Chỉ dùng trong game.");
            return true;
        }
        if (!VaultEconomy.available()) {
            p.sendMessage(c("&cChưa có hệ thống tiền, sàn thu mua tạm đóng."));
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "tao", "dat" -> create(p, args);
            case "cuatoi", "cua-toi", "toi" -> openMine(p);
            case "nhan" -> {
                for (int id : ids()) {
                    ConfigurationSection o = order(id);
                    if (o != null && p.getUniqueId().toString().equals(o.getString("owner")) && o.getInt("kho") > 0) {
                        collect(p, id);
                    }
                }
            }
            case "huy" -> {
                if (args.length < 2) {
                    p.sendMessage(c("&e/donhang huy <số đơn>"));
                } else {
                    try {
                        cancel(p, Integer.parseInt(args[1].replace("#", "")));
                    } catch (NumberFormatException e) {
                        p.sendMessage(c("&cSố đơn không hợp lệ."));
                    }
                }
            }
            case "giup", "help" -> {
                p.sendMessage(c("&6&l━━ SÀN THU MUA ━━"));
                p.sendMessage(c("&e/donhang &7mở sàn, bấm đơn để giao hàng nhận tiền ngay"));
                p.sendMessage(c("&e/donhang tao <số lượng> <đơn giá> &7(cầm món cần mua) đặt đơn; tiền được giữ trước, phí "
                        + cfg.getDouble("fee-percent", 2) + "%"));
                p.sendMessage(c("&e/donhang cuatoi &7nhận hàng (chuột trái), huỷ đơn (chuột phải)"));
                p.sendMessage(c("&e/donhang nhan &7nhận hết hàng · &e/donhang huy <số> &7huỷ đơn, trả lại tiền phần chưa giao"));
                p.sendMessage(c("&7Đơn tự đóng sau " + cfg.getInt("days", 7) + " ngày. Chỉ nhận đồ thường (không tên riêng, không phù phép)."));
            }
            default -> openMarket(p, 0);
        }
        return true;
    }

    List<String> tab(String[] a) {
        if (a.length == 1) {
            String t = a[0].toLowerCase(Locale.ROOT);
            return List.of("tao", "cuatoi", "nhan", "huy", "giup").stream().filter(x -> x.startsWith(t)).toList();
        }
        if (a.length == 2 && a[0].equalsIgnoreCase("tao")) {
            return List.of("64", "320", "1000");
        }
        return List.of();
    }
}
