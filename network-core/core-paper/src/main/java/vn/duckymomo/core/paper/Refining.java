package vn.duckymomo.core.paper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Tinh Luyện (/tinhluyen, Bang Hội Chiến, 19/10): đổi MỘT CHIỀU lượng lớn khối thường thành "món tinh luyện" — đồ thật gắn dấu PDC
 * "dmm_tinhluyen" = mã món (đổi tên bằng đe không làm giả được), dùng làm hàng trao đổi ở shop dân làng (Shopkeepers), nguyên liệu Xưởng
 * (workshop.items.<mã>.tinh-luyen: {sat: 1}) và bán cho server (/sell hand|all hoặc /tinhluyen ban). Mục đích kinh tế: rút khoáng sản khỏi
 * nền kinh tế (không đổi ngược), giá bán ~70% giá trị nguyên liệu theo bảng /sell nên tinh luyện rồi bán không bao giờ lời hơn bán thẳng.
 * Xu Quái: quái thù địch người chơi giết rơi theo tỉ lệ (quái từ lồng không rơi), quái EliteMobs rơi nhiều hơn.
 * Món nền là mảnh gốm / hạt vàng: không đặt xuống được, không có giá trong worth.yml (Essentials không bán nhầm theo vật liệu gốc);
 * dùng để chế tạo / rèn / sửa đồ bị chặn. Config tinh-luyen: items.<mã thư viện>{recipe {VẬT_LIỆU|mã món: số}, sell},
 * xu-quai{item, chance, elite-chance, elite-max}. Hình dạng (tên, vật phẩm nền, mô tả, model) ở THƯ VIỆN ĐỒ, công dụng "tinh-luyen" (20/10).
 */
final class Refining implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final List<String> SELL_CMDS = List.of("sell", "esell", "essentials:sell", "essentials:esell");

    record Item(String id, Map<String, Integer> recipe, double sell) {
        String name() {
            ItemLibrary lib = Shared.library;
            return lib == null ? id : lib.name(id);
        }
    }

    /** Tên tiếng Việt của nguyên liệu (client tiếng Anh hiện "Block of Diamond" nếu dùng tên gốc). */
    private static final Map<String, String> VI = Map.ofEntries(Map.entry("COBBLESTONE", "Đá cuội"), Map.entry("COBBLED_DEEPSLATE", "Đá sâu cuội"),
            Map.entry("COAL_BLOCK", "Khối than"), Map.entry("RAW_COPPER_BLOCK", "Khối đồng thô"), Map.entry("RAW_IRON_BLOCK", "Khối sắt thô"),
            Map.entry("REDSTONE_BLOCK", "Khối đá đỏ"), Map.entry("LAPIS_BLOCK", "Khối lapis"), Map.entry("RAW_GOLD_BLOCK", "Khối vàng thô"),
            Map.entry("DIAMOND_BLOCK", "Khối kim cương"), Map.entry("EMERALD_BLOCK", "Khối lục bảo"), Map.entry("NETHERITE_BLOCK", "Khối netherite"),
            Map.entry("HAY_BLOCK", "Kiện rơm"), Map.entry("IRON_BLOCK", "Khối sắt"), Map.entry("GOLD_BLOCK", "Khối vàng"),
            Map.entry("COPPER_BLOCK", "Khối đồng"), Map.entry("QUARTZ_BLOCK", "Khối thạch anh"), Map.entry("BONE_BLOCK", "Khối xương"));

    private final DuckyMoMoCore plugin;
    private final NamespacedKey key;
    private final Map<String, Item> items = new LinkedHashMap<>();
    private final String coinId;
    private final double chance;
    private final double eliteChance;
    private final int eliteMax;
    private final String coreId;       // Lõi Quái (23/10): chỉ quái EliteMobs rơi, nguyên liệu Thợ Rèn
    private final double coreChance;

    Refining(DuckyMoMoCore plugin, ConfigurationSection c) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "dmm_tinhluyen");
        ConfigurationSection is = c.getConfigurationSection("items");
        if (is != null) {
            for (String id : is.getKeys(false)) {
                ConfigurationSection i = is.getConfigurationSection(id);
                if (i == null) {
                    continue;
                }
                Map<String, Integer> rec = new LinkedHashMap<>();
                ConfigurationSection rs = i.getConfigurationSection("recipe");
                if (rs != null) {
                    for (String k : rs.getKeys(false)) {
                        if (rs.getInt(k) > 0) {
                            rec.put(k, rs.getInt(k));
                        }
                    }
                }
                items.put(id.toLowerCase(Locale.ROOT), new Item(id.toLowerCase(Locale.ROOT), rec, Math.max(0, i.getDouble("sell", 0))));
            }
        }
        // nguyên liệu phải là vật liệu thật hoặc mã món khác
        for (Item it : items.values()) {
            for (String k : it.recipe().keySet()) {
                if (!items.containsKey(k.toLowerCase(Locale.ROOT)) && Material.matchMaterial(k) == null) {
                    plugin.getLogger().warning("Tinh luyện: " + it.id() + " cần '" + k + "' không phải vật liệu hay món nào");
                }
            }
        }
        ItemLibrary.STAMPERS.put("tinh-luyen", (it, id, sec) -> items.containsKey(id) ? stamp(it, id) : it);
        ConfigurationSection x = c.getConfigurationSection("xu-quai");
        this.coinId = x == null ? "xuquai" : x.getString("item", "xuquai").toLowerCase(Locale.ROOT);
        this.chance = x == null ? 5 : x.getDouble("chance", 5);
        this.eliteChance = x == null ? 40 : x.getDouble("elite-chance", 40);
        this.eliteMax = x == null ? 3 : Math.max(1, x.getInt("elite-max", 3));
        ConfigurationSection lq = c.getConfigurationSection("loi-quai");
        this.coreId = lq == null ? "" : lq.getString("item", "loiquai");
        this.coreChance = lq == null ? 0 : lq.getDouble("elite-chance", 15);
    }

    String describe() {
        return items.size() + " món, Xu Quái " + chance + "% (tinh nhuệ " + eliteChance + "%)";
    }

    // ---------------------------------------------------------------- món đồ

    /** Món từ thư viện đồ (null nếu thư viện không có món này hoặc món không mang công dụng Tinh Luyện). */
    ItemStack build(String id, int amount) {
        ItemLibrary lib = Shared.library;
        ItemStack it = lib == null ? null : lib.build(id, Math.max(1, amount));
        return it != null && id.equals(idOf(it)) ? it : null;
    }

    /** Đóng dấu Tinh Luyện lên đồ thư viện (ItemLibrary.STAMPERS "tinh-luyen"). */
    private ItemStack stamp(ItemStack it, String id) {
        ItemMeta meta = it.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(c("&7Nguyên liệu tinh luyện · /tinhluyen"));
        meta.lore(lore);
        if (!meta.hasItemModel() && !meta.hasEnchantmentGlintOverride()) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        return it;
    }

    /** Mã món tinh luyện của vật phẩm (null = đồ thường). */
    String idOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        String id = it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return id != null && items.containsKey(id) ? id : null;
    }

    boolean exists(String id) {
        return items.containsKey(id.toLowerCase(Locale.ROOT));
    }

    String name(String id) {
        Item d = items.get(id.toLowerCase(Locale.ROOT));
        return d == null ? id : d.name();
    }

    /** Số lượng trong túi: mã món tinh luyện, hoặc vật liệu thường (đồ trơn, không tên/phù phép). */
    int count(Player p, String ingredient) {
        String low = ingredient.toLowerCase(Locale.ROOT);
        int n = 0;
        if (items.containsKey(low)) {
            for (ItemStack it : p.getInventory().getStorageContents()) {
                if (low.equals(idOf(it))) {
                    n += it.getAmount();
                }
            }
            return n;
        }
        Material m = Material.matchMaterial(ingredient);
        if (m == null) {
            return 0;
        }
        ItemStack plain = new ItemStack(m);
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && it.getType() == m && it.isSimilar(plain)) {
                n += it.getAmount();
            }
        }
        return n;
    }

    void take(Player p, String ingredient, int amount) {
        String low = ingredient.toLowerCase(Locale.ROOT);
        Material m = items.containsKey(low) ? null : Material.matchMaterial(ingredient);
        ItemStack plain = m == null ? null : new ItemStack(m);
        ItemStack[] inv = p.getInventory().getStorageContents();
        for (int pass = 0; pass < 2; pass++)   // lượt 1 bỏ qua ngọc đã tháo (mang phẩm Khảm), hết món trơn mới lấy tới
        for (int i = 0; i < inv.length && amount > 0; i++) {
            ItemStack it = inv[i];
            boolean match = plain != null ? it != null && it.getType() == m && it.isSimilar(plain) : low.equals(idOf(it));
            if (match && pass == 0 && Kham.isLooseGem(it)) {
                continue;
            }
            if (match) {
                int t = Math.min(amount, it.getAmount());
                it.setAmount(it.getAmount() - t);
                amount -= t;
                if (it.getAmount() <= 0) {
                    inv[i] = null;
                }
            }
        }
        p.getInventory().setStorageContents(inv);
    }

    /** Giá bán cho server của một món tinh luyện (0 nếu không có). */
    double sellOf(String id) {
        Item d = items.get(id);
        return d == null ? 0 : d.sell();
    }

    Component nameOf(String id) {
        return ingredientName(id);
    }

    private Component ingredientName(String k) {
        String low = k.toLowerCase(Locale.ROOT);
        if (items.containsKey(low)) {
            return c(items.get(low).name());
        }
        Material m = Material.matchMaterial(k);
        if (m != null && VI.containsKey(m.name())) {
            return Component.text(VI.get(m.name()));
        }
        return m == null ? Component.text(k) : Component.translatable(m.translationKey());
    }

    // ---------------------------------------------------------------- menu

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        final Map<Integer, Item> slots = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    void open(Player p) {
        Holder h = new Holder();
        int rows = Math.min(6, Math.max(3, (int) Math.ceil(items.size() / 7.0) + 2));
        h.inv = Bukkit.createInventory(h, rows * 9, c("&0♨ Tinh Luyện"));
        ItemStack edge = pane(Material.LIGHT_BLUE_STAINED_GLASS_PANE), inner = pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int s = 0; s < rows * 9; s++) {
            int r = s / 9, col = s % 9;
            h.inv.setItem(s, r == 0 || r == rows - 1 || col == 0 || col == 8 ? edge : inner);
        }
        ItemStack info = Tip.of("&d♨ &fTinh Luyện").sub("đổi một chiều").accent("&d")
                .desc("Đổi lượng lớn khối thường thành món quý")
                .gap().head("Dùng để").item("Khảm vào đồ set: /thoren kham").item("Thợ Rèn: lên set bậc cao (Cao Cấp trở lên)")
                .item("Giao đơn Nhà Buôn mỗi tuần (lời 20%, NPC ở /warp shop)").item("Trao đổi giữa người chơi").item("Bán cho server: /tinhluyen ban")
                .line().raw("&c✘ Không đổi ngược lại được").note("/tinhluyen ds: xem mọi công thức")
                .apply(new ItemStack(Material.NETHER_STAR));
        h.inv.setItem(4, info);
        int i = 0;
        for (Item d : items.values()) {
            if (d.recipe().isEmpty()) {
                continue;   // món nền (Xu Quái): không chế được
            }
            int row = 1 + i / 7, col = 1 + i % 7;
            if (row >= rows - 1) {
                break;
            }
            int slot = row * 9 + col;
            ItemStack ic = icon(p, d);
            if (ic == null) {
                continue;
            }
            h.inv.setItem(slot, ic);
            h.slots.put(slot, d);
            i++;
        }
        p.openInventory(h.inv);
    }

    private static ItemStack pane(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(" "));
        meta.setHideTooltip(true);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack icon(Player p, Item d) {
        ItemStack it = build(d.id(), 1);
        if (it == null) {
            return null;
        }
        ItemMeta meta = it.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Tip.c(Tip.LINE));
        lore.add(Tip.c("&8• &dNguyên liệu cần:"));
        boolean ok = true;
        for (Map.Entry<String, Integer> in : d.recipe().entrySet()) {
            int have = count(p, in.getKey());
            boolean enough = have >= in.getValue();
            ok &= enough;
            lore.add(Tip.c("  &8▸ " + (enough ? "&a" : "&c") + in.getValue() + " &f").append(ingredientName(in.getKey()).color(null).decoration(TextDecoration.ITALIC, false))
                    .append(Tip.c(" &8(có " + have + ")")));
        }
        if (d.sell() > 0) {
            lore.add(Tip.c("&8• &7Bán cho server: &6" + money(d.sell())));
        }
        lore.add(Tip.c(Tip.LINE));
        if (ok) {
            lore.add(Tip.c("&a➜ Bấm để tinh luyện 1 lần"));
            lore.add(Tip.c("&eShift + bấm&7: &ftinh luyện hết số có thể"));
        } else {
            lore.add(Tip.c("&c✘ Còn thiếu nguyên liệu"));
        }
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) {
            return;
        }
        Item d = h.slots.get(e.getRawSlot());
        if (d == null) {
            return;
        }
        int times = 0;
        int max = e.isShiftClick() ? 64 : 1;
        while (times < max && canCraft(p, d)) {
            for (Map.Entry<String, Integer> in : d.recipe().entrySet()) {
                take(p, in.getKey(), in.getValue());
            }
            times++;
        }
        if (times == 0) {
            p.sendMessage(c("&cBạn còn thiếu nguyên liệu cho ").append(c(d.name())).append(c("&c.")));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            return;
        }
        give(p, d.id(), times);
        p.playSound(p.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.7f, 1.1f);
        p.sendMessage(c("&a✔ Đã tinh luyện " + times + " ").append(c(d.name())).append(c("&a.")));
        plugin.getLogger().info("Tinh luyện: " + p.getName() + " → " + times + " " + d.id());
        DailyQuests.feature(p, "tinhluyen");
        open(p);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    private boolean canCraft(Player p, Item d) {
        for (Map.Entry<String, Integer> in : d.recipe().entrySet()) {
            if (count(p, in.getKey()) < in.getValue()) {
                return false;
            }
        }
        return true;
    }

    private void give(Player p, String id, int amount) {
        while (amount > 0) {
            ItemStack it = build(id, amount);
            if (it == null) {
                plugin.getLogger().warning("Tinh luyện: thư viện đồ không có món " + id);
                return;
            }
            amount -= it.getAmount();
            p.getInventory().addItem(it).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        }
    }

    // ---------------------------------------------------------------- bán

    /** Bán món tinh luyện: hand = chỉ món đang cầm, ngược lại cả túi. Trả số món đã bán. */
    private int sell(Player p, boolean handOnly) {
        double total = 0;
        int count = 0;
        ItemStack[] inv = p.getInventory().getStorageContents();
        int held = p.getInventory().getHeldItemSlot();
        for (int i = 0; i < inv.length; i++) {
            if (handOnly && i != held) {
                continue;
            }
            String id = idOf(inv[i]);
            if (id == null || items.get(id).sell() <= 0) {
                continue;
            }
            total += items.get(id).sell() * inv[i].getAmount();
            count += inv[i].getAmount();
            inv[i] = null;
        }
        if (count == 0 || !VaultEconomy.available()) {
            return 0;
        }
        SellLimit limit = Shared.sellLimit;
        if (limit != null && !limit.allow(p, total)) {
            return 0;   // vượt giới hạn bán mỗi ngày: chưa lấy món nào (inv là bản sao)
        }
        p.getInventory().setStorageContents(inv);
        VaultEconomy.deposit(p, total);
        if (limit != null) {
            limit.record(p, total);
        }
        Frontier f = Shared.frontier;
        if (f != null) {
            f.addSale(p, total);   // như /sell: góp % vào quỹ khai hoang
        }
        p.sendMessage(c("&a[Tinh luyện] Đã bán " + count + " món: &e+" + money(total)));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        return count;
    }

    /** Giá bán cho server của 1 món tinh luyện; -1 = không phải món tinh luyện (cho SellLimit ước lượng /sell). */
    double priceOf(ItemStack it) {
        String id = idOf(it);
        return id == null ? -1 : Math.max(0, items.get(id).sell());
    }

    /** /sell hand|all của Essentials: món tinh luyện bán theo giá riêng (vật liệu nền không có giá trong worth.yml). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSellCommand(PlayerCommandPreprocessEvent e) {
        String[] a = e.getMessage().substring(1).trim().split("\\s+");
        if (a.length < 2 || !SELL_CMDS.contains(a[0].toLowerCase(Locale.ROOT))) {
            return;
        }
        String what = a[1].toLowerCase(Locale.ROOT);
        Player p = e.getPlayer();
        if (what.equals("hand") && idOf(p.getInventory().getItemInMainHand()) != null) {
            sell(p, true);
            e.setCancelled(true);
        } else if (what.equals("all") || what.equals("inventory") || what.equals("invent")) {
            sell(p, false);   // phần còn lại để Essentials bán như thường
        }
    }

    // ---------------------------------------------------------------- Xu Quái

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity en = e.getEntity();
        if (!(en instanceof Enemy) || en.getKiller() == null || !items.containsKey(coinId)
                || en.getEntitySpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            return;
        }
        boolean elite = en.getPersistentDataContainer().getKeys().stream().anyMatch(k -> k.getNamespace().equals("elitemobs"));
        if (elite && coreChance > 0 && ThreadLocalRandom.current().nextDouble(100) < coreChance) {
            ItemStack core = build(coreId, 1);
            if (core != null) {
                e.getDrops().add(core);
            }
        }
        double roll = ThreadLocalRandom.current().nextDouble(100);
        if (roll >= (elite ? eliteChance : chance)) {
            return;
        }
        int n = elite ? 1 + ThreadLocalRandom.current().nextInt(eliteMax) : 1;
        ItemStack coin = build(coinId, n);
        if (coin != null) {
            e.getDrops().add(coin);
        }
    }

    // ---------------------------------------------------------------- chặn dùng sai

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (idOf(e.getItemInHand()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (idOf(it) != null) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onSmith(PrepareSmithingEvent e) {
        for (ItemStack it : e.getInventory().getContents()) {
            if (idOf(it) != null) {
                e.setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        for (ItemStack it : e.getInventory().getContents()) {
            if (idOf(it) != null) {
                e.setResult(null);
                return;
            }
        }
    }

    // ---------------------------------------------------------------- lệnh

    boolean command(CommandSender s, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("give") && s.hasPermission("duckymomo.admin")) {
            String id = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
            int n = 1;
            try {
                n = args.length > 2 ? Math.max(1, Math.min(2304, Integer.parseInt(args[2]))) : 1;
            } catch (NumberFormatException ignored) {
                // mặc định 1
            }
            Player t = args.length > 3 ? Bukkit.getPlayerExact(args[3]) : s instanceof Player sp ? sp : null;
            if (!items.containsKey(id) || t == null) {
                s.sendMessage("/tinhluyen give <" + String.join("|", items.keySet()) + "> [số] [người]");
                return true;
            }
            give(t, id, n);
            s.sendMessage(LEGACY.serialize(c("&aĐã đưa " + n + " ").append(c(items.get(id).name()))) + " cho " + t.getName());
            return true;
        }
        if (sub.equals("ds")) {
            s.sendMessage(c("&5Tinh luyện &7— mã món (cho admin, Shopkeepers, Xưởng):"));
            for (Item d : items.values()) {
                StringBuilder r = new StringBuilder();
                d.recipe().forEach((k, v) -> r.append(r.length() > 0 ? " + " : "").append(v).append(' ').append(k));
                s.sendMessage(c("&f" + d.id() + " &7= ").append(c(d.name())).append(c("&7 ← " + (r.length() == 0 ? "quái rơi" : r)
                        + (d.sell() > 0 ? " · bán " + money(d.sell()) : ""))));
            }
            return true;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("/tinhluyen give <mã> [số] <người> · /tinhluyen ds");
            return true;
        }
        if (sub.equals("ban")) {
            boolean all = args.length > 1 && (args[1].equalsIgnoreCase("tatca") || args[1].equalsIgnoreCase("all"));
            if (sell(p, !all) == 0) {
                p.sendMessage(c("&7" + (all ? "Túi" : "Tay") + " không có món tinh luyện nào bán được. &f/tinhluyen ban tatca &7để bán cả túi."));
            }
            return true;
        }
        open(p);
        return true;
    }

    List<String> tab(CommandSender s, String[] args) {
        if (args.length == 1) {
            return s.hasPermission("duckymomo.admin") ? List.of("ban", "ds", "give") : List.of("ban", "ds");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return new ArrayList<>(items.keySet());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("ban")) {
            return List.of("tatca");
        }
        return List.of();
    }

    private static String money(double v) {
        return VaultEconomy.available() ? VaultEconomy.format(v) : (long) v + " xu";
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
}
