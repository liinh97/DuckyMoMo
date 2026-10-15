package vn.duckymomo.core.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mảnh sự kiện + cửa hàng đổi mảnh (/doimanh).
 * Mảnh là ĐỒ VẬT (trao đổi, bán được) gắn dấu PDC "dmm_manh" = loại (không làm giả bằng đổi tên). Mỗi loại: name, material, lore,
 * until (hạn đổi yyyy-MM-dd, trống = không hạn). Nhận qua phần thưởng "manh <loại> <số>" bị giới hạn daily-cap mảnh/người/ngày
 * (chống tài khoản phụ cày); admin/boss phát bằng /doimanh give (không tính giới hạn).
 * Cửa hàng: shop [{label, cost {loại: số}, rewards [quà], limit (lần/người, 0 = không giới hạn)}]. Lưu: doimanh.yml
 */
final class FragmentShop implements Listener, Rewards.FragmentGiver {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private record Type(String id, String name, Material material, List<String> lore, String until) {
        boolean open() {
            return until.isBlank() || !LocalDate.now(VN).isAfter(LocalDate.parse(until));
        }
    }

    private record Offer(String label, Map<String, Integer> cost, List<String> rewards, int limit, int index) { }

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        final Map<Integer, Offer> slots = new LinkedHashMap<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final JavaPlugin plugin;
    private final ConfigurationSection cfg;
    private final Rewards rewards;
    private final NamespacedKey key;
    private final Map<String, Type> types = new LinkedHashMap<>();
    private final List<Offer> offers = new ArrayList<>();
    private final File file;
    private final YamlConfiguration data;

    FragmentShop(JavaPlugin plugin, ConfigurationSection cfg, Rewards rewards) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.rewards = rewards;
        this.key = new NamespacedKey(plugin, "manh");
        this.file = new File(plugin.getDataFolder(), "doimanh.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection ts = cfg.getConfigurationSection("types");
        if (ts != null) {
            for (String id : ts.getKeys(false)) {
                ConfigurationSection t = ts.getConfigurationSection(id);
                if (t == null) {
                    continue;
                }
                Material m = Material.matchMaterial(t.getString("material", "AMETHYST_SHARD"));
                String until = t.getString("until", "");
                try {
                    if (!until.isBlank()) {
                        LocalDate.parse(until);
                    }
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Mảnh " + id + ": hạn đổi sai (yyyy-MM-dd), coi như không hạn");
                    until = "";
                }
                types.put(id, new Type(id, t.getString("name", id), m == null ? Material.AMETHYST_SHARD : m, t.getStringList("lore"), until));
            }
        }
        int i = 0;
        for (Map<?, ?> m : cfg.getMapList("shop")) {
            Map<String, Integer> cost = new LinkedHashMap<>();
            if (m.get("cost") instanceof Map<?, ?> cm) {
                for (Map.Entry<?, ?> e : cm.entrySet()) {
                    if (e.getValue() instanceof Number n && n.intValue() > 0) {
                        cost.put(String.valueOf(e.getKey()), n.intValue());
                    }
                }
            }
            List<String> rw = new ArrayList<>();
            if (m.get("rewards") instanceof List<?> l) {
                for (Object o : l) {
                    rw.add(String.valueOf(o));
                }
            }
            if (!cost.isEmpty() && !rw.isEmpty()) {
                offers.add(new Offer(String.valueOf(m.get("label") == null ? rw.get(0) : m.get("label")), cost, rw,
                        m.get("limit") instanceof Number n ? n.intValue() : 0, i));
            }
            i++;
        }
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private ItemStack fragment(Type t, int amount) {
        ItemStack it = new ItemStack(t.material(), amount);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(t.name()));
        List<Component> lore = new ArrayList<>();
        for (String l : t.lore()) {
            lore.add(c(l));
        }
        if (!t.until().isBlank()) {
            lore.add(c("&8Đổi được đến hết ngày " + t.until()));
        }
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, t.id());
        it.setItemMeta(meta);
        return it;
    }

    private String typeOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        return it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    /** Phát mảnh (kể cả vượt giới hạn ngày nếu bypass). Trả mô tả hoặc null. */
    private String grant(Player p, String typeId, int amount, boolean bypass) {
        Type t = types.get(typeId);
        if (t == null || amount <= 0) {
            plugin.getLogger().warning("Mảnh sự kiện: không có loại \"" + typeId + "\"");
            return null;
        }
        String day = LocalDate.now(VN).toString();
        String k = "nhan." + p.getUniqueId();
        if (!day.equals(data.getString(k + ".ngay"))) {
            data.set(k + ".ngay", day);
            data.set(k + ".so", 0);
        }
        int got = data.getInt(k + ".so");
        int cap = cfg.getInt("daily-cap", 20);
        if (!bypass && cap > 0) {
            amount = Math.min(amount, cap - got);
            if (amount <= 0) {
                p.sendMessage(c("&7(Đã nhận đủ " + cap + " mảnh sự kiện hôm nay, mai nhận tiếp.)"));
                return null;
            }
        }
        if (!bypass) {
            data.set(k + ".so", got + amount);
            save();
        }
        int left = amount;
        while (left > 0) {
            int n = Math.min(left, t.material().getMaxStackSize());
            Rewards.hand(p, fragment(t, n));
            left -= n;
        }
        return amount + " " + t.name() + "&r";
    }

    @Override
    public String give(Player p, String type, int amount) {
        return grant(p, type, amount, false);
    }

    private int count(Player p, String typeId) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (typeId.equals(typeOf(it))) {
                n += it.getAmount();
            }
        }
        return n;
    }

    private void take(Player p, String typeId, int amount) {
        ItemStack[] inv = p.getInventory().getStorageContents();
        for (int i = 0; i < inv.length && amount > 0; i++) {
            if (typeId.equals(typeOf(inv[i]))) {
                int n = Math.min(amount, inv[i].getAmount());
                inv[i].setAmount(inv[i].getAmount() - n);
                amount -= n;
            }
        }
        p.getInventory().setStorageContents(inv);
    }

    private boolean available(Offer o) {
        for (String t : o.cost().keySet()) {
            Type ty = types.get(t);
            if (ty == null || !ty.open()) {
                return false;
            }
        }
        return true;
    }

    private int used(Player p, Offer o) {
        return data.getInt("doi." + p.getUniqueId() + "." + o.index(), 0);
    }

    private ItemStack offerIcon(Player p, Offer o) {
        String first = o.rewards().get(0).trim();
        String[] a = first.split("\\s+");
        ItemStack it = null;
        if (a[0].equalsIgnoreCase("vat") && a.length > 1) {
            Material m = Material.matchMaterial(a[1]);
            it = m == null ? null : new ItemStack(m);
        }
        if (it == null) {
            it = new ItemStack(a[0].equalsIgnoreCase("xu") ? Material.GOLD_INGOT : Material.CHEST);
        }
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c("&e" + o.label()));
        List<Component> lore = new ArrayList<>();
        for (String d : rewards.describe(o.rewards())) {
            lore.add(c("&8• &7" + d));
        }
        lore.add(c("&7Giá:"));
        boolean enough = true;
        for (Map.Entry<String, Integer> e : o.cost().entrySet()) {
            Type t = types.get(e.getKey());
            int have = count(p, e.getKey());
            enough &= have >= e.getValue();
            lore.add(c("  " + (have >= e.getValue() ? "&a" : "&c") + e.getValue() + " " + (t == null ? e.getKey() : t.name()) + " &8(có " + have + ")"));
        }
        if (o.limit() > 0) {
            lore.add(c("&7Đã đổi &f" + used(p, o) + "/" + o.limit() + " &7lần"));
        }
        lore.add(c(enough ? "&a» Bấm để đổi" : "&c» Chưa đủ mảnh"));
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    void open(Player p) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 54, c("&5&lĐỔI MẢNH SỰ KIỆN"));
        int slot = 10;
        for (Offer o : offers) {
            if (!available(o) || slot >= 44) {
                continue;
            }
            h.inv.setItem(slot, offerIcon(p, o));
            h.slots.put(slot, o);
            slot += (slot % 9 == 7) ? 3 : 1;
        }
        if (h.slots.isEmpty()) {
            p.sendMessage(c("&7Hiện chưa có sự kiện nào mở đổi mảnh."));
            return;
        }
        p.openInventory(h.inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        Offer o = h.slots.get(e.getRawSlot());
        if (o == null || !(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (!available(o)) {
            p.sendMessage(c("&cSự kiện đã hết hạn đổi."));
            return;
        }
        if (o.limit() > 0 && used(p, o) >= o.limit()) {
            p.sendMessage(c("&cBạn đã đổi món này tối đa " + o.limit() + " lần."));
            return;
        }
        for (Map.Entry<String, Integer> cost : o.cost().entrySet()) {
            if (count(p, cost.getKey()) < cost.getValue()) {
                p.sendMessage(c("&cChưa đủ mảnh."));
                return;
            }
        }
        for (Map.Entry<String, Integer> cost : o.cost().entrySet()) {
            take(p, cost.getKey(), cost.getValue());
        }
        data.set("doi." + p.getUniqueId() + "." + o.index(), used(p, o) + 1);
        save();
        List<String> got = rewards.give(p, o.rewards());
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
        p.sendMessage(c("&a✔ Đã đổi &f" + o.label() + (got.isEmpty() ? "" : " &7(" + String.join("&7, ", got) + "&7)")));
        plugin.getLogger().info("Đổi mảnh: " + p.getName() + " đổi " + o.label());
        h.inv.setItem(e.getRawSlot(), offerIcon(p, o));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    boolean command(CommandSender s, String[] args) {
        // Boss EliteMobs: onDeathCommands "doimanh chia <loại> <tổng> $players" -> chia đều cho người tham gia đang online
        if (args.length >= 4 && args[0].equalsIgnoreCase("chia") && s.hasPermission("duckymomo.admin")) {
            int total;
            try {
                total = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                s.sendMessage("Tổng số mảnh không hợp lệ.");
                return true;
            }
            List<Player> got = new ArrayList<>();
            for (String name : String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)).split("[,\\s]+")) {
                Player t = Bukkit.getPlayerExact(name.trim());
                if (t != null && !got.contains(t)) {
                    got.add(t);
                }
            }
            if (got.isEmpty() || total <= 0) {
                return true;
            }
            int each = Math.max(1, total / got.size());
            for (Player t : got) {
                String d = grant(t, args[1].toLowerCase(Locale.ROOT), each, true);
                if (d != null) {
                    t.sendMessage(c("&d✦ Phần của bạn: &f" + d));
                }
            }
            plugin.getLogger().info("Chia " + each + " mảnh " + args[1] + " cho " + got.size() + " người");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("give")) {
            if (!s.hasPermission("duckymomo.admin")) {
                s.sendMessage("Chỉ admin.");
                return true;
            }
            if (args.length < 4) {
                s.sendMessage("/doimanh give <người> <loại> <số>   loại: " + String.join(", ", types.keySet()));
                return true;
            }
            Player t = Bukkit.getPlayerExact(args[1]);
            if (t == null) {
                s.sendMessage("Người chơi không online trên server này.");
                return true;
            }
            int n;
            try {
                n = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                s.sendMessage("Số không hợp lệ.");
                return true;
            }
            String d = grant(t, args[2].toLowerCase(Locale.ROOT), n, true);
            s.sendMessage(d == null ? "Không có loại mảnh này." : "Đã phát " + n + " mảnh " + args[2] + " cho " + t.getName());
            if (d != null) {
                t.sendMessage(c("&d✦ Bạn nhận &f" + d));
            }
            return true;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("/doimanh give <người> <loại> <số>");
            return true;
        }
        open(p);
        return true;
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được doimanh.yml: " + e.getMessage());
        }
    }
}
