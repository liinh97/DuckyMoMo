package vn.duckymomo.core.paper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Món ăn ra trận (/nauan): nấu cá câu (EvenMoreFish, theo độ hiếm) + nông sản thành món ăn cho hiệu ứng ngắn khi ăn.
 * Config dishes: cooldown-minutes (ăn món này xong phải chờ mới ăn được món khác), list.<mã thư viện>: {need {VẬT_LIỆU: số,
 * "ca:<độ hiếm>": số}}. Món (tên, vật phẩm ăn được, mô tả) và hiệu ứng khi ăn ["HIỆU_ỨNG cấp giây"] (cấp 0 = I) nằm ở THƯ VIỆN ĐỒ,
 * công dụng "mon-an", mục hieu-ung (20/10). Món là đồ vật có dấu PDC dmm_mon = mã (trao đổi, bán, nộp kho lương được); món thư viện
 * phát từ boss / phần thưởng cũng ăn được hiệu ứng. Không bán bằng tiền thật.
 */
final class Dishes implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    static NamespacedKey KEY;
    static NamespacedKey KITCHEN;
    /** Thái Ấp (ThaiAp): người đang ở bếp riêng thì món nấu ra được kéo dài hiệu ứng kitchenBonus %. */
    static volatile java.util.function.Predicate<Player> kitchenHere;
    static volatile double kitchenBonus;

    private record Dish(String id, Map<String, Integer> need) { }

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        final Map<Integer, Dish> slots = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final Map<String, Dish> dishes = new LinkedHashMap<>();
    private final long cooldownMs;
    private final Map<UUID, Long> lastEat = new HashMap<>();

    Dishes(DuckyMoMoCore plugin, ConfigurationSection c) {
        KEY = new NamespacedKey(plugin, "dmm_mon");
        KITCHEN = new NamespacedKey(plugin, "dmm_bep");
        this.cooldownMs = Math.max(0, c.getLong("cooldown-minutes", 20)) * 60_000L;
        ConfigurationSection l = c.getConfigurationSection("list");
        if (l != null) {
            for (String id : l.getKeys(false)) {
                ConfigurationSection d = l.getConfigurationSection(id);
                if (d == null) {
                    continue;
                }
                Map<String, Integer> need = new LinkedHashMap<>();
                ConfigurationSection n = d.getConfigurationSection("need");
                if (n != null) {
                    for (String k : n.getKeys(false)) {
                        need.put(k, n.getInt(k));
                    }
                }
                dishes.put(id, new Dish(id, need));
            }
        }
        ItemLibrary.STAMPERS.put("mon-an", Dishes::stamp);
    }

    /** Hiệu ứng khi ăn món thư viện này ([] nếu không phải món ăn). */
    private static List<String> effects(String id) {
        ItemLibrary lib = Shared.library;
        ConfigurationSection s = lib == null ? null : lib.section(id);
        return s == null || !"mon-an".equals(s.getString("cong-dung", "")) ? List.of() : s.getStringList("hieu-ung");
    }

    private static String dishName(String id) {
        ItemLibrary lib = Shared.library;
        return lib == null ? id : lib.name(id);
    }

    /** Đóng dấu món ăn lên đồ thư viện: dòng hiệu ứng + dấu dmm_mon. */
    private static ItemStack stamp(ItemStack it, String id, ConfigurationSection s) {
        if (KEY == null) {
            return it;
        }
        ItemMeta meta = it.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(c("&8Khi ăn:"));
        for (String e : s.getStringList("hieu-ung")) {
            lore.add(c("&a• " + effectText(e)));
        }
        lore.add(c("&8Món ăn ra trận · /nauan"));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        return it;
    }

    boolean ready() {
        return !dishes.isEmpty();
    }

    String describe() {
        return dishes.size() + " món, chờ " + cooldownMs / 60_000 + " phút giữa 2 món";
    }

    static boolean isDish(ItemStack it) {
        return KEY != null && it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(KEY, PersistentDataType.STRING);
    }

    private static String dishId(ItemStack it) {
        return isDish(it) ? it.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING) : null;
    }

    /** Món từ thư viện (null nếu thư viện không còn món này hoặc không phải món ăn). */
    private ItemStack make(Dish d, int amount) {
        ItemLibrary lib = Shared.library;
        ItemStack it = lib == null ? null : lib.build(d.id(), amount);
        return it != null && isDish(it) ? it : null;
    }

    private static final Map<String, String> EFFECT_VI = Map.ofEntries(
            Map.entry("REGENERATION", "Hồi máu"), Map.entry("RESISTANCE", "Kháng sát thương"), Map.entry("ABSORPTION", "Máu ảo"),
            Map.entry("STRENGTH", "Sức mạnh"), Map.entry("SPEED", "Nhanh nhẹn"), Map.entry("FIRE_RESISTANCE", "Kháng lửa"),
            Map.entry("NIGHT_VISION", "Nhìn đêm"), Map.entry("SATURATION", "No bụng"), Map.entry("HASTE", "Đào nhanh"),
            Map.entry("WATER_BREATHING", "Thở dưới nước"), Map.entry("JUMP_BOOST", "Nhảy cao"), Map.entry("HEALTH_BOOST", "Thêm máu tối đa"));

    private static String effectText(String spec) {
        String[] a = spec.trim().split("\\s+");
        int lv = a.length > 1 ? Integer.parseInt(a[1]) : 0;
        String sec = a.length > 2 ? a[2] : "30";
        return EFFECT_VI.getOrDefault(a[0].toUpperCase(Locale.ROOT), a[0]) + " " + roman(lv + 1) + " (" + sec + " giây)";
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(n);
        };
    }

    private static boolean isFishKey(String k) {
        return k.regionMatches(true, 0, "ca:", 0, 3);
    }

    private static final Map<String, String> FISH_VI = Map.of("common", "Thường", "rare", "Hiếm", "epic", "Quý", "legendary", "Huyền thoại");

    private static String needName(String k) {
        if (isFishKey(k)) {
            return "cá " + FISH_VI.getOrDefault(k.substring(3).toLowerCase(Locale.ROOT), k.substring(3));
        }
        Material m = Material.matchMaterial(k);
        return m == null ? k : Seasons.cropLabel(m);
    }

    private static boolean matches(String k, ItemStack it) {
        if (it == null || it.getType().isAir()) {
            return false;
        }
        if (isFishKey(k)) {
            String r = PetFish.rarity(it);
            return r != null && r.equalsIgnoreCase(k.substring(3));
        }
        Material m = Material.matchMaterial(k);
        return m != null && it.getType() == m && PetFish.rarity(it) == null && !isDish(it);
    }

    private static int count(Player p, String k) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (matches(k, it)) {
                n += it.getAmount();
            }
        }
        return n;
    }

    private static void take(Player p, String k, int amount) {
        ItemStack[] inv = p.getInventory().getStorageContents();
        for (int i = 0; i < inv.length && amount > 0; i++) {
            if (matches(k, inv[i])) {
                int n = Math.min(amount, inv[i].getAmount());
                inv[i].setAmount(inv[i].getAmount() - n);
                amount -= n;
            }
        }
        p.getInventory().setStorageContents(inv);
    }

    boolean command(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Dùng trong game.");
            return true;
        }
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 27, c("&0🍲 Bếp ra trận"));
        int slot = 10;
        for (Dish d : dishes.values()) {
            if (slot > 16) {
                break;
            }
            ItemStack ic = icon(p, d);
            if (ic == null) {
                continue;
            }
            h.inv.setItem(slot, ic);
            h.slots.put(slot, d);
            slot++;
        }
        p.openInventory(h.inv);
        return true;
    }

    private ItemStack icon(Player p, Dish d) {
        ItemStack it = make(d, 1);
        if (it == null) {
            return null;
        }
        ItemMeta meta = it.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(c("&7Cần:"));
        boolean ok = true;
        for (Map.Entry<String, Integer> e : d.need().entrySet()) {
            int have = count(p, e.getKey());
            ok &= have >= e.getValue();
            lore.add(c("  " + (have >= e.getValue() ? "&a" : "&c") + e.getValue() + " " + needName(e.getKey()) + " &8(có " + have + ")"));
        }
        lore.add(c(ok ? "&a» Bấm để nấu" : "&c» Chưa đủ nguyên liệu"));
        meta.lore(lore);
        meta.getPersistentDataContainer().remove(KEY);   // biểu tượng trong menu không phải món thật
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        Dish d = h.slots.get(e.getRawSlot());
        if (d == null || !(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        for (Map.Entry<String, Integer> n : d.need().entrySet()) {
            if (count(p, n.getKey()) < n.getValue()) {
                p.sendMessage(c("&cChưa đủ " + needName(n.getKey()) + " (cần " + n.getValue() + ")."));
                return;
            }
        }
        ItemStack dish = make(d, 1);
        if (dish == null) {
            p.sendMessage(c("&cMón này không còn trong thư viện đồ, báo admin."));
            return;
        }
        d.need().forEach((k, v) -> take(p, k, v));
        var kh = kitchenHere;
        boolean home = kh != null && kitchenBonus > 0 && kh.test(p);
        if (home) {
            ItemMeta dm = dish.getItemMeta();
            dm.getPersistentDataContainer().set(KITCHEN, PersistentDataType.BYTE, (byte) 1);
            List<Component> lore = dm.lore() == null ? new ArrayList<>() : new ArrayList<>(dm.lore());
            lore.add(c("&d🏡 Nấu ở bếp Thái Ấp: hiệu ứng +" + (long) kitchenBonus + "%"));
            dm.lore(lore);
            dish.setItemMeta(dm);
        }
        Rewards.hand(p, dish);
        Achievements.add(p, "nauan", 1);
        p.playSound(p.getLocation(), Sound.BLOCK_SMOKER_SMOKE, 0.8f, 1f);
        p.sendMessage(c("&a🍲 Đã nấu " + dishName(d.id()) + "&a."));
        h.inv.setItem(e.getRawSlot(), icon(p, d));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        String id = dishId(e.getItem());
        List<String> fx = id == null ? List.of() : effects(id);
        if (fx.isEmpty()) {
            return;
        }
        Player p = e.getPlayer();
        long wait = lastEat.getOrDefault(p.getUniqueId(), 0L) + cooldownMs - System.currentTimeMillis();
        if (wait > 0) {
            e.setCancelled(true);
            p.sendMessage(c("&eBạn vừa ăn món ra trận, chờ &f" + (wait / 60_000 + 1) + " phút &enữa mới ăn được món khác."));
            return;
        }
        lastEat.put(p.getUniqueId(), System.currentTimeMillis());
        for (String spec : fx) {
            String[] a = spec.trim().split("\\s+");
            PotionEffectType t = org.bukkit.Registry.EFFECT.get(org.bukkit.NamespacedKey.minecraft(a[0].toLowerCase(Locale.ROOT)));
            if (t == null) {
                continue;
            }
            int lv = a.length > 1 ? Integer.parseInt(a[1]) : 0;
            int sec = a.length > 2 ? Integer.parseInt(a[2]) : 30;
            if (e.getItem().getItemMeta().getPersistentDataContainer().has(KITCHEN)) {
                sec = (int) Math.round(sec * (1 + kitchenBonus / 100.0));   // món nấu ở bếp Thái Ấp
            }
            p.addPotionEffect(new PotionEffect(t, sec * 20, lv, false, true, true));
        }
        p.sendMessage(c("&a🍲 " + dishName(id) + "&a: " + String.join(", ", fx.stream().map(Dishes::effectText).toList())));
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
}
