package vn.duckymomo.core.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Xưởng chế tạo (/xuong, ý từ "Server Tools" của UniMC): đồ đặc biệt có LƯỢT DÙNG và THỜI HẠN (mặc định 7 ngày), chế bằng xu + nguyên liệu.
 * Hết lượt hoặc hết hạn thì món đồ biến mất -> tiền và tài nguyên liên tục rời khỏi nền kinh tế, không ai giữ đồ mạnh mãi.
 * Tính năng (feature):
 *   tui_hu      bấm chuột phải mở rương ender ở bất cứ đâu (trừ vùng vây đang đánh)      1 lượt / lần mở
 *   nuoc        xô nước không bao giờ cạn                                                   1 lượt / lần đổ
 *   hut_do      để trong túi: đồ từ khối mình đào và quái mình giết bay thẳng vào túi        1 lượt / lần
 *               (KHÔNG hút đồ rơi của người chơi khác, không ăn cắp chiến lợi phẩm PvP)
 *   hut_mau     cung: mỗi phát trúng hồi value máu (2 = 1 tim) cho người bắn               1 lượt / phát bắn
 *   (trống)     chỉ là đồ có phù phép gốc (enchants), vd Búa tạ = chuỳ Wind Burst + Density; độ bền gốc của Minecraft
 * pvp: true -> món này không gây sát thương / không hồi máu trong vùng vây đang có phiên đánh (công thành công bằng).
 * Dấu trên đồ (PDC): xuong_id, xuong_luot (lượt còn, -1 = không đếm), xuong_het (mốc hết hạn ms).
 */
final class Workshop implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("HH:mm dd/MM").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    record Recipe(String id, String name, Material material, String feature, double value, int uses, int days, double cost,
                  Map<Material, Integer> ingredients, Map<String, Integer> enchants, boolean pvp, List<String> desc) { }

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        final Map<Integer, Recipe> slots = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final JavaPlugin plugin;
    private final Map<String, Recipe> recipes = new LinkedHashMap<>();
    private final NamespacedKey kId;
    private final NamespacedKey kUses;
    private final NamespacedKey kUntil;
    private final NamespacedKey kArrow;

    Workshop(JavaPlugin plugin, ConfigurationSection cfg) {
        this.plugin = plugin;
        this.kId = new NamespacedKey(plugin, "xuong_id");
        this.kUses = new NamespacedKey(plugin, "xuong_luot");
        this.kUntil = new NamespacedKey(plugin, "xuong_het");
        this.kArrow = new NamespacedKey(plugin, "xuong_hutmau");
        ConfigurationSection items = cfg.getConfigurationSection("items");
        if (items == null) {
            return;
        }
        for (String id : items.getKeys(false)) {
            ConfigurationSection r = items.getConfigurationSection(id);
            if (r == null || !r.getBoolean("enabled", true)) {
                continue;
            }
            Material m = Material.matchMaterial(r.getString("material", "STONE"));
            if (m == null || !m.isItem()) {
                plugin.getLogger().warning("Xưởng: " + id + " có material sai, bỏ qua");
                continue;
            }
            Map<Material, Integer> ing = new LinkedHashMap<>();
            ConfigurationSection is = r.getConfigurationSection("ingredients");
            if (is != null) {
                for (String k : is.getKeys(false)) {
                    Material im = Material.matchMaterial(k);
                    if (im != null && is.getInt(k) > 0) {
                        ing.put(im, is.getInt(k));
                    }
                }
            }
            Map<String, Integer> ench = new LinkedHashMap<>();
            ConfigurationSection es = r.getConfigurationSection("enchants");
            if (es != null) {
                for (String k : es.getKeys(false)) {
                    ench.put(k.toLowerCase(Locale.ROOT), es.getInt(k));
                }
            }
            recipes.put(id, new Recipe(id, r.getString("name", id), m, r.getString("feature", ""), r.getDouble("value", 0),
                    r.getInt("uses", -1), r.getInt("days", 7), r.getDouble("cost", 0), ing, ench, r.getBoolean("pvp", false),
                    r.getStringList("description")));
        }
    }

    int size() {
        return recipes.size();
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::sweep), 600L, 1200L);
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    private static String money(double v) {
        return VaultEconomy.available() ? VaultEconomy.format(v) : (long) v + " xu";
    }

    private static String featureText(Recipe r) {
        return switch (r.feature()) {
            case "tui_hu" -> "Rương ender mở ở mọi nơi";
            case "nuoc" -> "Nguồn nước vô tận";
            case "hut_do" -> "Đồ đào/săn bay thẳng vào túi";
            case "hut_mau" -> "Hút máu (+" + (r.value() / 2) + " tim/phát trúng)";
            default -> "Phù phép đặc biệt";
        };
    }

    // ------------------------------------------------------------ dựng đồ, lượt, hạn

    private Recipe recipeOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        String id = it.getItemMeta().getPersistentDataContainer().get(kId, PersistentDataType.STRING);
        return id == null ? null : recipes.get(id);
    }

    private boolean expired(ItemStack it) {
        Long until = it.getItemMeta().getPersistentDataContainer().get(kUntil, PersistentDataType.LONG);
        return until != null && System.currentTimeMillis() > until;
    }

    private ItemStack build(Recipe r) {
        ItemStack it = new ItemStack(r.material());
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(r.name()));
        for (Map.Entry<String, Integer> e : r.enchants().entrySet()) {
            Enchantment en = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(e.getKey()));
            if (en != null && e.getValue() > 0) {
                meta.addEnchant(en, e.getValue(), true);
            }
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(kId, PersistentDataType.STRING, r.id());
        pdc.set(kUses, PersistentDataType.INTEGER, r.uses());
        pdc.set(kUntil, PersistentDataType.LONG, System.currentTimeMillis() + r.days() * 86_400_000L);
        it.setItemMeta(meta);
        relore(it, r);
        return it;
    }

    private void relore(ItemStack it, Recipe r) {
        ItemMeta meta = it.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int uses = pdc.getOrDefault(kUses, PersistentDataType.INTEGER, -1);
        long until = pdc.getOrDefault(kUntil, PersistentDataType.LONG, 0L);
        List<Component> lore = new ArrayList<>();
        lore.add(c("&7— Tính năng: &b" + featureText(r)));
        if (uses >= 0) {
            lore.add(c("&7— Độ bền: &6" + String.format(Locale.ROOT, "%,d", uses).replace(',', '.') + " lượt"));
        }
        lore.add(c("&7— Hết hạn: &b" + WHEN.format(Instant.ofEpochMilli(until))));
        for (String d : r.desc()) {
            lore.add(c("&8" + d));
        }
        if (r.pvp()) {
            lore.add(c("&c✖ Không dùng được trong vùng vây khi đang đánh"));
        }
        meta.lore(lore);
        it.setItemMeta(meta);
    }

    /** Trừ 1 lượt. Trả về đồ sau khi trừ, hoặc null nếu hết lượt (đồ biến mất). */
    private ItemStack useOnce(ItemStack it, Recipe r) {
        ItemMeta meta = it.getItemMeta();
        int uses = meta.getPersistentDataContainer().getOrDefault(kUses, PersistentDataType.INTEGER, -1);
        if (uses < 0) {
            return it;
        }
        if (uses <= 1) {
            return null;
        }
        meta.getPersistentDataContainer().set(kUses, PersistentDataType.INTEGER, uses - 1);
        it.setItemMeta(meta);
        relore(it, r);
        return it;
    }

    /** Xoá đồ xưởng hết hạn trong túi. */
    private void sweep(Player p) {
        ItemStack[] inv = p.getInventory().getContents();
        for (int i = 0; i < inv.length; i++) {
            Recipe r = recipeOf(inv[i]);
            if (r != null && expired(inv[i])) {
                p.getInventory().setItem(i, null);
                p.sendMessage(c("&7[Xưởng] " + r.name() + " &7đã hết hạn và biến mất. Chế lại: &e/xuong"));
            }
        }
    }

    private boolean blockedHere(Player p, Recipe r) {
        return r.pvp() && SiegeWarHook.battleActive() && SiegeWarHook.inActiveSiegeZone(p.getLocation());
    }

    /** Kiểm tra trước khi dùng: hết hạn thì xoá và báo. */
    private boolean alive(Player p, ItemStack it, Recipe r, EquipmentSlot slot) {
        if (!expired(it)) {
            return true;
        }
        p.getInventory().setItem(slot, null);
        p.sendMessage(c("&7[Xưởng] " + r.name() + " &7đã hết hạn và biến mất."));
        return false;
    }

    // ------------------------------------------------------------ tính năng

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() == null) {
            return;
        }
        ItemStack it = e.getItem();
        Recipe r = recipeOf(it);
        if (r == null || !r.feature().equals("tui_hu")) {
            return;
        }
        e.setCancelled(true);   // không đặt rương ender xuống
        Player p = e.getPlayer();
        if (!alive(p, it, r, e.getHand())) {
            return;
        }
        if (SiegeWarHook.battleActive() && SiegeWarHook.inActiveSiegeZone(p.getLocation())) {
            p.sendMessage(c("&c[Xưởng] Không mở Túi Hư trong vùng vây đang đánh."));
            return;
        }
        p.getInventory().setItem(e.getHand(), useOnce(it, r));
        p.openInventory(p.getEnderChest());
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6f, 1f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        Player p = e.getPlayer();
        ItemStack it = p.getInventory().getItem(e.getHand());
        Recipe r = recipeOf(it);
        if (r == null || !r.feature().equals("nuoc")) {
            return;
        }
        if (expired(it)) {
            e.setItemStack(new ItemStack(Material.BUCKET));
            p.sendMessage(c("&7[Xưởng] " + r.name() + " &7đã hết hạn."));
            return;
        }
        ItemStack after = useOnce(it.clone(), r);
        e.setItemStack(after == null ? new ItemStack(Material.BUCKET) : after);
    }

    /** Ô trong túi có món mang tính năng feature còn hạn, -1 nếu không có. */
    private int findInBag(Player p, String feature) {
        ItemStack[] inv = p.getInventory().getContents();
        for (int i = 0; i < inv.length; i++) {
            Recipe r = recipeOf(inv[i]);
            if (r != null && r.feature().equals(feature) && !expired(inv[i])) {
                return i;
            }
        }
        return -1;
    }

    private void spendInBag(Player p, int slot) {
        ItemStack it = p.getInventory().getItem(slot);
        Recipe r = recipeOf(it);
        if (r != null) {
            p.getInventory().setItem(slot, useOnce(it, r));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDrops(BlockDropItemEvent e) {
        Player p = e.getPlayer();
        int magnet = findInBag(p, "hut_do");
        if (magnet < 0 || e.getItems().isEmpty()) {
            return;
        }
        boolean took = false;
        for (Iterator<Item> iter = e.getItems().iterator(); iter.hasNext(); ) {
            Item drop = iter.next();
            Map<Integer, ItemStack> rest = p.getInventory().addItem(drop.getItemStack());
            if (rest.isEmpty()) {
                iter.remove();
                took = true;
            } else {
                drop.setItemStack(rest.values().iterator().next());
            }
        }
        if (took) {
            spendInBag(p, magnet);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobDrops(EntityDeathEvent e) {
        Player p = e.getEntity().getKiller();
        if (p == null || e.getEntity() instanceof Player || e.getDrops().isEmpty()) {
            return;
        }
        int magnet = findInBag(p, "hut_do");
        if (magnet < 0) {
            return;
        }
        List<ItemStack> keep = new ArrayList<>();
        for (ItemStack d : e.getDrops()) {
            keep.addAll(p.getInventory().addItem(d).values());
        }
        e.getDrops().clear();
        e.getDrops().addAll(keep);
        if (e.getDroppedExp() > 0) {
            p.giveExp(e.getDroppedExp());
            e.setDroppedExp(0);
        }
        spendInBag(p, magnet);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p)) {
            return;
        }
        ItemStack bow = e.getBow();
        Recipe r = recipeOf(bow);
        if (r == null || !r.feature().equals("hut_mau")) {
            return;
        }
        if (!alive(p, bow, r, e.getHand())) {
            e.setCancelled(true);
            return;
        }
        e.getProjectile().getPersistentDataContainer().set(kArrow, PersistentDataType.DOUBLE, r.value());
        p.getInventory().setItem(e.getHand(), useOnce(bow, r));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        // cung hút máu
        if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player shooter) {
            Double heal = pr.getPersistentDataContainer().get(kArrow, PersistentDataType.DOUBLE);
            if (heal != null && heal > 0 && e.getEntity() instanceof org.bukkit.entity.LivingEntity) {
                if (SiegeWarHook.battleActive() && SiegeWarHook.inActiveSiegeZone(shooter.getLocation())) {
                    return;   // món PvP: không hút máu trong vùng vây đang đánh
                }
                var attr = shooter.getAttribute(Attribute.MAX_HEALTH);
                double max = attr == null ? 20 : attr.getValue();
                shooter.setHealth(Math.min(max, shooter.getHealth() + heal));
            }
            return;
        }
        // đồ PvP cầm tay (vd Búa tạ): không gây sát thương trong vùng vây đang đánh
        if (e.getDamager() instanceof Player p && e.getEntity() instanceof Player) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            Recipe r = recipeOf(hand);
            if (r != null && blockedHere(p, r)) {
                e.setCancelled(true);
                p.sendActionBar(c("&c" + r.name() + " &ckhông dùng được trong vùng vây khi đang đánh"));
            } else if (r != null && expired(hand)) {
                e.setCancelled(true);
                p.getInventory().setItemInMainHand(null);
                p.sendMessage(c("&7[Xưởng] " + r.name() + " &7đã hết hạn và biến mất."));
            }
        }
    }

    // ------------------------------------------------------------ menu chế tạo

    private static int countPlain(Player p, Material m) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && it.getType() == m && it.isSimilar(new ItemStack(m))) {
                n += it.getAmount();
            }
        }
        return n;
    }

    private static void takePlain(Player p, Material m, int amount) {
        ItemStack[] inv = p.getInventory().getStorageContents();
        for (int i = 0; i < inv.length && amount > 0; i++) {
            if (inv[i] != null && inv[i].getType() == m && inv[i].isSimilar(new ItemStack(m))) {
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

    private ItemStack icon(Player p, Recipe r) {
        ItemStack it = new ItemStack(r.material());
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(r.name()));
        List<Component> lore = new ArrayList<>();
        lore.add(c("&7— Tính năng: &b" + featureText(r)));
        if (r.uses() >= 0) {
            lore.add(c("&7— Độ bền: &6" + String.format(Locale.ROOT, "%,d", r.uses()).replace(',', '.') + " lượt"));
        }
        lore.add(c("&7— Thời hạn: &b" + r.days() + " ngày"));
        for (String d : r.desc()) {
            lore.add(c("&8" + d));
        }
        if (r.pvp()) {
            lore.add(c("&c✖ Không dùng được trong vùng vây khi đang đánh"));
        }
        lore.add(Component.empty());
        lore.add(c("&7Giá chế:"));
        if (r.cost() > 0) {
            boolean ok = VaultEconomy.available() && VaultEconomy.has(p, r.cost());
            lore.add(c("  " + (ok ? "&a" : "&c") + money(r.cost())));
        }
        for (Map.Entry<Material, Integer> e : r.ingredients().entrySet()) {
            int have = countPlain(p, e.getKey());
            lore.add(c("  " + (have >= e.getValue() ? "&a" : "&c") + e.getValue() + "x ").append(Component.translatable(e.getKey().translationKey()))
                    .append(c(" &8(có " + have + ")")));
        }
        lore.add(c("&a→ Nhấn để chế tạo."));
        meta.lore(lore);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS, org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        if (!r.enchants().isEmpty()) {
            meta.setEnchantmentGlintOverride(true);
        }
        it.setItemMeta(meta);
        return it;
    }

    void open(Player p) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 27, c("&8&lXƯỞNG CHẾ TẠO &7(đồ có hạn dùng)"));
        int slot = 10;
        for (Recipe r : recipes.values()) {
            if (slot > 16) {
                break;
            }
            h.inv.setItem(slot, icon(p, r));
            h.slots.put(slot, r);
            slot++;
        }
        p.openInventory(h.inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        Recipe r = h.slots.get(e.getRawSlot());
        if (r == null || !(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        for (Map.Entry<Material, Integer> in : r.ingredients().entrySet()) {
            if (countPlain(p, in.getKey()) < in.getValue()) {
                p.sendMessage(c("&cThiếu nguyên liệu: cần " + in.getValue() + "x ").append(Component.translatable(in.getKey().translationKey())));
                return;
            }
        }
        if (p.getInventory().firstEmpty() < 0) {
            p.sendMessage(c("&cTúi đầy, dọn 1 ô rồi chế."));
            return;
        }
        if (r.cost() > 0 && (!VaultEconomy.available() || !VaultEconomy.withdraw(p, r.cost()))) {
            p.sendMessage(c("&cKhông đủ " + money(r.cost()) + "."));
            return;
        }
        for (Map.Entry<Material, Integer> in : r.ingredients().entrySet()) {
            takePlain(p, in.getKey(), in.getValue());
        }
        p.getInventory().addItem(build(r));
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.2f);
        p.sendMessage(c("&a✔ Đã chế " + r.name() + "&a. Dùng được " + r.days() + " ngày."));
        plugin.getLogger().info("Xưởng: " + p.getName() + " chế " + r.id());
        h.inv.setItem(e.getRawSlot(), icon(p, r));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    boolean command(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Chỉ dùng trong game.");
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("tang") && s.hasPermission("duckymomo.admin")) {
            Recipe r = recipes.get(args[1]);
            Player t = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : p;
            if (r == null || t == null) {
                s.sendMessage("/xuong tang <" + String.join("|", recipes.keySet()) + "> [người]");
            } else {
                t.getInventory().addItem(build(r));
                s.sendMessage("Đã tặng " + r.id() + " cho " + t.getName());
            }
            return true;
        }
        open(p);
        return true;
    }
}
