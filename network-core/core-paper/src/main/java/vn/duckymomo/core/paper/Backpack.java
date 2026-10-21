package vn.duckymomo.core.paper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Balo cá nhân (/balo [số], Bang Hội Chiến, 19/10): túi mang theo người. Số balo theo quyền duckymomo.balo.<N>, số ô mỗi balo theo
 * duckymomo.balo.o.<9|18|27|36|45|54> (cao nhất; không có thì backpack.default-slots) — Danh phận cấp cho các bậc và rank khác (other-ranks).
 * Rank giảm số ô: đồ ở ô bị khoá vẫn giữ trong file, báo người chơi, lên rank nhiều ô hơn là thấy lại.
 * Lưu plugins/DuckyMoMoCore/balo/<uuid>.yml (mỗi ô = ItemStack.serializeAsBytes base64), ghi ngay sau mỗi thao tác (tick sau) để
 * server sập giữa chừng không nhân đồ; mỗi balo chỉ có MỘT Inventory dùng chung (admin xem cùng lúc người chơi cũng chung một bản).
 * Không mở được khi vừa đánh nhau với người (combat-seconds) hoặc trong vùng vây đang có phiên đánh. /choilai xoá balo (wipe).
 * Admin: /balo xem <tên> [số].
 */
final class Backpack implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final int[] SIZES = {54, 45, 36, 27, 18, 9};
    private final int defaultSlots;

    private final DuckyMoMoCore plugin;
    private final File dir;
    private final long combatMillis;
    private final int maxBags;
    private final Map<String, Inventory> open = new HashMap<>();   // "uuid#số" -> balo đang mở
    private final Map<UUID, Long> lastFight = new HashMap<>();

    Backpack(DuckyMoMoCore plugin, ConfigurationSection c) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "balo");
        this.combatMillis = Math.max(0, c == null ? 15 : c.getInt("combat-seconds", 15)) * 1000L;
        this.maxBags = Math.max(1, c == null ? 9 : c.getInt("max", 9));
        this.defaultSlots = PlayRanks.slots(c == null ? 27 : c.getInt("default-slots", 27));
        dir.mkdirs();
    }

    static final class Holder implements InventoryHolder {   // ItemInspect: balo tính là kho đồ
        UUID owner;
        int index;
        int hidden;   // số món nằm ở ô bị khoá (rank giảm số ô): vẫn giữ trong file
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    /** Số balo được dùng: quyền duckymomo.balo.<N> cao nhất. */
    int allowed(Player p) {
        for (int n = maxBags; n >= 1; n--) {
            if (p.hasPermission("duckymomo.balo." + n)) {
                return n;
            }
        }
        return 0;
    }

    /** Số ô mỗi balo: quyền duckymomo.balo.o.<9..54> cao nhất (danh phận / rank khác), không có thì default-slots. */
    int slotsOf(Player p) {
        for (int n : SIZES) {
            if (p.hasPermission("duckymomo.balo.o." + n)) {
                return n;
            }
        }
        return defaultSlots;
    }

    private File file(UUID u) {
        return new File(dir, u + ".yml");
    }

    private Inventory get(UUID owner, int index, String ownerName, int size) {
        String key = owner + "#" + index;
        Inventory inv = open.get(key);
        if (inv != null) {
            return inv;
        }
        Holder h = new Holder();
        h.owner = owner;
        h.index = index;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file(owner));
        List<String> slots = y.getStringList("balo" + index);
        if (size <= 0) {   // admin xem balo người offline: đủ ô để thấy hết đồ đã lưu
            int last = 0;
            for (int i = 0; i < slots.size(); i++) {
                if (!slots.get(i).isEmpty()) {
                    last = i + 1;
                }
            }
            size = PlayRanks.slots(Math.max(defaultSlots, last));
        }
        h.inv = Bukkit.createInventory(h, size, c("&0▣ Balo " + index + " &8· " + ownerName + " · " + size + " ô"));
        int hidden = 0;
        for (int i = size; i < slots.size(); i++) {
            hidden += slots.get(i).isEmpty() ? 0 : 1;
        }
        h.hidden = hidden;
        for (int i = 0; i < Math.min(size, slots.size()); i++) {
            String b64 = slots.get(i);
            if (b64 != null && !b64.isEmpty()) {
                try {
                    h.inv.setItem(i, ItemStack.deserializeBytes(Base64.getDecoder().decode(b64)));
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Balo " + owner + "#" + index + " ô " + i + " hỏng: " + e.getMessage());
                }
            }
        }
        open.put(key, h.inv);
        return h.inv;
    }

    private void save(Holder h) {
        File f = file(h.owner);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        List<String> old = y.getStringList("balo" + h.index);
        List<String> slots = new ArrayList<>();
        for (ItemStack it : h.inv.getContents()) {
            slots.add(it == null || it.getType().isAir() ? "" : Base64.getEncoder().encodeToString(it.serializeAsBytes()));
        }
        // ô vượt số ô hiện tại (rank giảm): giữ nguyên đồ cũ, không xoá
        for (int i = slots.size(); i < old.size(); i++) {
            slots.add(old.get(i));
        }
        y.set("balo" + h.index, slots);
        try {
            y.save(f);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được balo " + f.getName() + ": " + e.getMessage());
        }
    }

    /** /choilai: xoá mọi balo của người chơi (đóng balo đang mở, không lưu lại). */
    void wipe(UUID u) {
        for (String k : new ArrayList<>(open.keySet())) {
            if (k.startsWith(u + "#")) {
                Inventory inv = open.remove(k);
                inv.clear();
                new ArrayList<>(inv.getViewers()).forEach(v -> v.closeInventory());
            }
        }
        if (file(u).exists() && !file(u).delete()) {
            plugin.getLogger().warning("Không xoá được balo của " + u);
        }
    }

    /** Tắt plugin / dmm reload: lưu và đóng mọi balo đang mở (listener cũ sắp bị gỡ, không đóng thì thao tác sau đó không được lưu). */
    void saveAll() {
        for (Inventory inv : new ArrayList<>(open.values())) {
            if (inv.getHolder() instanceof Holder h) {
                save(h);
                new ArrayList<>(inv.getViewers()).forEach(v -> v.closeInventory());
            }
        }
        open.clear();
    }

    private String blocked(Player p) {
        if (System.currentTimeMillis() - lastFight.getOrDefault(p.getUniqueId(), 0L) < combatMillis) {
            return "Đang giao tranh, chờ " + combatMillis / 1000 + " giây sau trận mới mở balo được.";
        }
        try {
            if (SiegeWarHook.battleActive() && SiegeWarHook.inActiveSiegeZone(p.getLocation())) {
                return "Không mở balo trong vùng vây khi đang có phiên đánh.";
            }
        } catch (RuntimeException | NoClassDefFoundError ignored) {
            // không có SiegeWar
        }
        return null;
    }

    // ---------------------------------------------------------------- sự kiện

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFight(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = Culprit.of(e.getDamager());
        if (attacker == null || attacker == victim) {
            return;
        }
        long now = System.currentTimeMillis();
        lastFight.put(victim.getUniqueId(), now);
        lastFight.put(attacker.getUniqueId(), now);
        for (Player p : List.of(victim, attacker)) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Holder) {
                p.closeInventory();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof Holder h) {
            Bukkit.getScheduler().runTask(plugin, () -> save(h));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder h) {
            Bukkit.getScheduler().runTask(plugin, () -> save(h));
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        save(h);
        // người xem cuối cùng đóng → bỏ khỏi bộ nhớ (lần mở sau đọc lại file)
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (h.inv.getViewers().isEmpty()) {
                open.remove(h.owner + "#" + h.index);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastFight.remove(e.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- lệnh

    boolean command(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Dùng trong game.");
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("xem") && p.hasPermission("duckymomo.admin")) {
            OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(args[1]);
            if (t == null) {
                p.sendMessage(c("&cKhông thấy người chơi " + args[1]));
                return true;
            }
            int n = args.length > 2 ? parse(args[2]) : 1;
            if (n < 1 || n > maxBags) {
                p.sendMessage(c("&cSố balo 1–" + maxBags));
                return true;
            }
            Player on = t.getPlayer();
            p.openInventory(get(t.getUniqueId(), n, t.getName(), on == null ? 0 : slotsOf(on)));
            return true;
        }
        int have = allowed(p);
        if (have <= 0) {
            p.sendMessage(c("&cBạn chưa có balo. Lên danh phận &fDân &cđể có balo đầu tiên: &f/danhphan"));
            return true;
        }
        int n = args.length > 0 ? parse(args[0]) : 1;
        if (n < 1 || n > have) {
            p.sendMessage(c("&eBạn có " + have + " balo: &f/balo 1" + (have > 1 ? " … /balo " + have : "")));
            return true;
        }
        String why = blocked(p);
        if (why != null) {
            p.sendMessage(c("&c" + why));
            return true;
        }
        Inventory inv = get(p.getUniqueId(), n, p.getName(), slotsOf(p));
        p.openInventory(inv);
        if (inv.getHolder() instanceof Holder h && h.hidden > 0) {
            p.sendMessage(c("&eCòn " + h.hidden + " món ở các ô balo đã khoá (rank hiện chỉ " + inv.getSize() + " ô). Đồ vẫn giữ, lên rank nhiều ô hơn để lấy."));
        }
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.6f, 1.1f);
        return true;
    }

    List<String> tab(CommandSender s, String[] args) {
        if (args.length == 1 && s instanceof Player p) {
            List<String> out = new ArrayList<>();
            for (int i = 1; i <= allowed(p); i++) {
                out.add(String.valueOf(i));
            }
            if (p.hasPermission("duckymomo.admin")) {
                out.add("xem");
            }
            return out;
        }
        return args.length == 2 && args[0].equalsIgnoreCase("xem") ? null : List.of();
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim().toLowerCase(Locale.ROOT));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
}
