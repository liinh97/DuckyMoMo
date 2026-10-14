package vn.duckymomo.core.paper;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Cửa hàng Kinh Đô (14/10): 3 mục Vật liệu cơ bản / Trang trí / Danh hiệu, trả bằng xu trong game, không bán gì tăng sức mạnh.
 *  - Mọi người: bấm NPC cửa hàng ở khu shop tại spawn (admin đặt bằng /dmm shopnpc).
 *  - Rank ủng hộ (quyền rank-permission): gõ /cuahang, nhưng CHỈ khi đang đứng trong kinh đô (không mua từ xa được).
 *  - Vật liệu: chỉ server bán, không mua lại; món nào giá mỗi cái <= giá thu mua /sell (Essentials worth.yml) thì tự bỏ (chống lời vô hạn).
 * Danh sách trong config decor-shop: materials, items (trang trí), titles (mã, chữ, giá). Danh hiệu dùng qua /danhhieu.
 */
final class DecorShop implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final Map<String, String> CATS = new LinkedHashMap<>();

    static {
        CATS.put("vatlieu", "Vật liệu cơ bản");
        CATS.put("trangtri", "Trang trí");
        CATS.put("danhhieu", "Danh hiệu");
    }

    record Offer(String key, String cat, Material icon, int amount, double price, String title, String titleText) {
    }

    private final DuckyMoMoCore plugin;
    private final Leaderboard board;
    private final Predicate<Location> inCapital;
    private final String rankPermission;
    private final String npcName;
    private final List<Offer> offers = new ArrayList<>();
    private final NamespacedKey offerKey;
    private final NamespacedKey catKey;
    private final NamespacedKey npcKey;

    DecorShop(DuckyMoMoCore plugin, Leaderboard board, Predicate<Location> inCapital, ConfigurationSection c) {
        this.plugin = plugin;
        this.board = board;
        this.inCapital = inCapital;
        this.rankPermission = c.getString("rank-permission", "duckymomo.cuahang");
        this.npcName = c.getString("npc-name", "&6&lCửa hàng Kinh Đô");
        this.offerKey = new NamespacedKey(plugin, "cuahang");
        this.catKey = new NamespacedKey(plugin, "cuahang_muc");
        this.npcKey = new NamespacedKey(plugin, "shop_npc");
        YamlConfiguration worth = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder().getParentFile(), "Essentials/worth.yml"));
        int skipped = 0;
        for (Map<?, ?> m : c.getMapList("materials")) {
            Offer o = item(m, "vatlieu");
            if (o == null) {
                continue;
            }
            double sell = worth.getDouble("worth." + o.icon().name().toLowerCase(Locale.ROOT), 0);
            if (sell > 0 && o.price() / o.amount() <= sell) {
                plugin.getLogger().warning("Cửa hàng: bỏ " + o.icon() + " vì giá bán (" + o.price() / o.amount() + "/cái) <= giá thu mua /sell (" + sell + ")");
                skipped++;
                continue;
            }
            offers.add(o);
        }
        for (Map<?, ?> m : c.getMapList("items")) {
            Offer o = item(m, "trangtri");
            if (o != null) {
                offers.add(o);
            }
        }
        for (Map<?, ?> m : c.getMapList("titles")) {
            offers.add(new Offer("t" + offers.size(), "danhhieu", Material.NAME_TAG, 1,
                    m.get("price") instanceof Number n ? n.doubleValue() : 0, String.valueOf(m.get("id")), String.valueOf(m.get("text"))));
        }
        plugin.getLogger().info("Cửa hàng Kinh Đô: " + offers.size() + " món" + (skipped > 0 ? " (bỏ " + skipped + " món rẻ hơn giá /sell)" : ""));
    }

    private Offer item(Map<?, ?> m, String cat) {
        Material mat = Material.matchMaterial(String.valueOf(m.get("material")));
        if (mat == null || !mat.isItem()) {
            return null;
        }
        int amount = m.get("amount") instanceof Number n ? Math.max(1, n.intValue()) : 1;
        double price = m.get("price") instanceof Number n ? n.doubleValue() : 0;
        return new Offer((cat.equals("vatlieu") ? "v" : "i") + offers.size(), cat, mat, amount, price, null, null);
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    private String money(double v) {
        return VaultEconomy.available() ? VaultEconomy.format(v) : String.format(Locale.ROOT, "%.0f xu", v);
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    // ------------------------------------------------------------------ mở cửa hàng

    /** /cuahang: rank ủng hộ, phải đứng trong kinh đô. */
    boolean command(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            return true;
        }
        if (!p.hasPermission(rankPermission)) {
            p.sendMessage(c("&eCửa hàng Kinh Đô: tới &fNPC cửa hàng ở khu shop tại spawn&e để mua. Rank ủng hộ mở được bằng &f/cuahang &ekhi đứng trong kinh đô."));
            return true;
        }
        if (!inCapital.test(p.getLocation())) {
            p.sendMessage(c("&eVề kinh đô (spawn) mới mở được cửa hàng."));
            return true;
        }
        openMenu(p);
        return true;
    }

    private void openMenu(Player p) {
        if (BedrockMenu.available() && BedrockMenu.isBedrock(p.getUniqueId())) {
            List<BedrockMenu.Entry> es = new ArrayList<>();
            CATS.forEach((k, v) -> es.add(new BedrockMenu.Entry(v, k)));
            BedrockMenu.open(p.getUniqueId(), "Cửa hàng Kinh Đô", "Trả bằng xu trong game. Chọn mục:", es,
                    k -> plugin.getServer().getScheduler().runTask(plugin, () -> openCategory(p, k)));
            return;
        }
        Holder h = new Holder();
        Inventory inv = plugin.getServer().createInventory(h, 27, c("&8Cửa hàng Kinh Đô"));
        h.inv = inv;
        Material[] icons = {Material.BRICKS, Material.LANTERN, Material.NAME_TAG};
        int[] slots = {11, 13, 15};
        int i = 0;
        for (Map.Entry<String, String> e : CATS.entrySet()) {
            String cat = e.getKey();
            long n = offers.stream().filter(o -> o.cat().equals(cat)).count();
            ItemStack it = new ItemStack(icons[i]);
            ItemMeta m = it.getItemMeta();
            m.displayName(c("&6&l" + e.getValue()));
            m.lore(List.of(c("&7" + n + " món"), c("&b» Bấm để xem")));
            m.getPersistentDataContainer().set(catKey, PersistentDataType.STRING, cat);
            it.setItemMeta(m);
            inv.setItem(slots[i++], it);
        }
        p.openInventory(inv);
    }

    private void openCategory(Player p, String cat) {
        List<Offer> list = offers.stream().filter(o -> o.cat().equals(cat)).toList();
        if (BedrockMenu.available() && BedrockMenu.isBedrock(p.getUniqueId())) {
            List<BedrockMenu.Entry> es = new ArrayList<>();
            for (Offer o : list) {
                es.add(new BedrockMenu.Entry(label(o).replaceAll("&.", "") + "\n" + money(o.price()), o.key()));
            }
            BedrockMenu.open(p.getUniqueId(), CATS.get(cat), list.isEmpty() ? "Chưa có món nào." : "Bấm để mua:", es,
                    k -> plugin.getServer().getScheduler().runTask(plugin, () -> buy(p, k)));
            return;
        }
        int rows = Math.max(2, Math.min(6, (list.size() + 8) / 9 + 1));
        Holder h = new Holder();
        Inventory inv = plugin.getServer().createInventory(h, rows * 9, c("&8" + CATS.get(cat)));
        h.inv = inv;
        for (int i = 0; i < list.size() && i < inv.getSize() - 9; i++) {
            Offer o = list.get(i);
            ItemStack it = new ItemStack(o.icon(), Math.max(1, Math.min(o.amount(), o.icon().getMaxStackSize())));
            ItemMeta m = it.getItemMeta();
            m.displayName(c(label(o)));
            List<Component> lore = new ArrayList<>();
            lore.add(c("&7Giá: &e" + money(o.price())));
            if (o.title() != null) {
                lore.add(c("&7Danh hiệu, dùng bằng /danhhieu"));
            }
            lore.add(c("&b» Bấm để mua"));
            m.lore(lore);
            m.getPersistentDataContainer().set(offerKey, PersistentDataType.STRING, o.key());
            it.setItemMeta(m);
            inv.setItem(i, it);
        }
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.displayName(c("&7« Quay lại"));
        bm.getPersistentDataContainer().set(catKey, PersistentDataType.STRING, "menu");
        back.setItemMeta(bm);
        inv.setItem(inv.getSize() - 5, back);
        p.openInventory(inv);
    }

    private static String label(Offer o) {
        return o.title() != null ? "Danh hiệu " + o.titleText() : "&f" + o.amount() + " × " + o.icon().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private void buy(Player p, String key) {
        Offer o = offers.stream().filter(x -> x.key().equals(key)).findFirst().orElse(null);
        if (o == null) {
            return;
        }
        if (o.title() != null && board != null && !board.grantable(p.getUniqueId(), o.title())) {
            p.sendMessage(c("&eBạn đã có danh hiệu này."));
            return;
        }
        if (!VaultEconomy.available() || !VaultEconomy.withdraw(p, o.price())) {
            p.sendMessage(c("&cKhông đủ tiền (" + money(o.price()) + ")."));
            return;
        }
        if (o.title() != null) {
            board.grant(p.getUniqueId(), p.getName(), o.title());
        } else {
            int left = o.amount();
            while (left > 0) {
                int n = Math.min(left, o.icon().getMaxStackSize());
                for (ItemStack rest : p.getInventory().addItem(new ItemStack(o.icon(), n)).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), rest);
                }
                left -= n;
            }
            p.sendMessage(c("&aĐã mua " + label(o) + " &avới " + money(o.price())));
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()
                || e.getClickedInventory() != e.getInventory()) {
            return;
        }
        var pdc = e.getCurrentItem().getItemMeta().getPersistentDataContainer();
        String cat = pdc.get(catKey, PersistentDataType.STRING);
        if (cat != null) {
            if (cat.equals("menu")) {
                openMenu(p);
            } else {
                openCategory(p, cat);
            }
            return;
        }
        String key = pdc.get(offerKey, PersistentDataType.STRING);
        if (key != null) {
            buy(p, key);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ NPC cửa hàng ở spawn

    /** /dmm shopnpc [remove]: đặt (hoặc xoá) NPC cửa hàng tại chỗ admin đứng. Đặt lại thì NPC cũ bị xoá. */
    void npcCommand(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Vào game, đứng ở khu shop tại spawn rồi gõ /dmm shopnpc");
            return;
        }
        int removed = 0;
        for (Entity e : p.getWorld().getEntities()) {
            if (e.getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE)) {
                e.remove();
                removed++;
            }
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("remove")) {
            p.sendMessage(c("&7Đã xoá " + removed + " thực thể NPC cửa hàng."));
            return;
        }
        Location loc = p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
        loc.setYaw(p.getLocation().getYaw());
        loc.setPitch(0);
        Villager v = p.getWorld().spawn(loc, Villager.class, e -> {
            e.customName(c(npcName));
            e.setCustomNameVisible(false);
            e.setAI(false);
            e.setSilent(true);
            e.setInvulnerable(true);
            e.setCollidable(false);
            e.setPersistent(true);
            e.setRemoveWhenFarAway(false);
            e.setProfession(Villager.Profession.MASON);
            e.setVillagerLevel(5);
            e.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte) 1);
        });
        if (!v.isValid()) {
            p.sendMessage(c("&cKhông đặt được dân làng ở đây (có thể WorldGuard chặn sinh mob)."));
            return;
        }
        String[] lines = {npcName, "&7Vật liệu · Trang trí · Danh hiệu", "&8(bấm vào để mua)"};
        double y = 1.6 + (lines.length - 1) * 0.28;
        for (String line : lines) {
            p.getWorld().spawn(loc.clone().add(0, y, 0), ArmorStand.class, a -> {
                a.setMarker(true);
                a.setInvisible(true);
                a.setGravity(false);
                a.setInvulnerable(true);
                a.setPersistent(true);
                a.customName(c(line));
                a.setCustomNameVisible(true);
                a.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte) 2);
            });
            y -= 0.28;
        }
        p.sendMessage(c("&aĐã đặt NPC Cửa hàng Kinh Đô tại &f" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ() + "&a. Xoá: &f/dmm shopnpc remove"));
    }

    private boolean isNpc(Entity e) {
        return e.getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onNpcClick(PlayerInteractEntityEvent e) {
        if (!isNpc(e.getRightClicked())) {
            return;
        }
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) {
            openMenu(e.getPlayer());
        }
    }

    @EventHandler
    public void onNpcClickAt(PlayerInteractAtEntityEvent e) {
        if (isNpc(e.getRightClicked())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onNpcDamage(EntityDamageEvent e) {
        if (isNpc(e.getEntity())) {
            e.setCancelled(true);
        }
    }
}
