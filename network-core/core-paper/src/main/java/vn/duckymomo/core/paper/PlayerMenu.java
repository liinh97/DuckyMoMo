package vn.duckymomo.core.paper;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu chính cho người chơi (/menu, Shift+F) ở server chế độ (Bang Hội Chiến…). Ở sảnh /menu vẫn là menu chọn chế độ.
 * Nút cấu hình trong player-menu.buttons: {slot, icon, name, sub, lore, gom, bam, cmd | suggest | proxy | server}
 *   name: tên (nên có ký hiệu đầu), sub: phụ đề xám, lore: mô tả, gom: danh sách "Gồm" (mục con), bam: chữ dòng bấm.
 *   Chữ dùng được %placeholder% của PlaceholderAPI (vd %dmmvang_so_du%) nếu server có PlaceholderAPI. Khung mô tả: Tip.
 *   cmd: chạy lệnh thay người chơi trên server này (không dấu /), suggest: điền sẵn vào ô chat (vd "/trade "),
 *   proxy: nhờ proxy chạy lệnh (lệnh của proxy như tuskin; kênh duckymomo:cmd, proxy chỉ nhận lệnh trong danh sách cho phép),
 *   server: chuyển sang server khác (kênh BungeeCord Connect).
 * 30/10 (chủ server: "sửa menu cho gọn"): player-menu.groups = các NHÓM. Có groups thì menu chính chỉ gồm buttons (ô hồ sơ…) + nút
 *   nhóm; bấm nhóm mở menu con 6 hàng: viền màu (border, hàng 2 border2), hàng trên là tab các nhóm (nhóm đang xem ghi "Đang xem"),
 *   nút của nhóm tự xếp giữa hàng 3-5, dưới cùng Quay lại + Đóng.
 *   Nhóm: {id, slot (ô ở menu chính), icon, name, sub, lore, gom, border, border2, title, buttons: [nút như trên, không cần slot]}
 */
final class PlayerMenu implements Listener {
    static final String PROXY_CHANNEL = "duckymomo:cmd";
    private static final LegacyComponentSerializer L = LegacyComponentSerializer.legacyAmpersand();

    record Group(String id, int slot, Material icon, String name, String sub, List<String> lore, List<String> gom, Material border,
                 Material border2, String title, boolean hidden, List<Button> buttons) { }

    private static final int BACK_SLOT = 48, CLOSE_SLOT = 50;

    record Button(int slot, Material icon, String name, String sub, List<String> lore, List<String> gom, String bam,
                  String cmd, String suggest, String proxy, String server, String open) { }

    private final JavaPlugin plugin;
    private final String title;
    private final int rows;
    private final boolean swapHand;
    private final Material filler;
    private final Map<Integer, Button> buttons = new HashMap<>();
    private final List<Group> groups = new ArrayList<>();

    static final class Holder implements InventoryHolder {
        Inventory inv;
        Group group;                                        // null = menu chính
        final Map<Integer, Object> slots = new HashMap<>(); // ô -> Button | Group | "back" | "close"

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    /** Nhóm /menu người chơi vừa bấm nút trong đó (nút "Về menu" ở menu tính năng quay lại đúng nhóm này). */
    private static final Map<java.util.UUID, String> LAST_GROUP = new java.util.concurrent.ConcurrentHashMap<>();

    PlayerMenu(JavaPlugin plugin, ConfigurationSection cfg) {
        this.plugin = plugin;
        MenuFrame.BACK_KEY = new org.bukkit.NamespacedKey(plugin, "ve_menu");
        this.title = cfg.getString("title", "&0Menu");
        this.rows = Math.max(1, Math.min(6, cfg.getInt("rows", 5)));
        this.swapHand = cfg.getBoolean("shift-f", true);
        Material f = Material.matchMaterial(cfg.getString("filler", "GRAY_STAINED_GLASS_PANE"));
        this.filler = f == null ? Material.AIR : f;
        for (Map<?, ?> m : cfg.getMapList("buttons")) {
            Button b = button(m, true);
            if (b != null) {
                buttons.put(b.slot(), b);
            }
        }
        for (Map<?, ?> m : cfg.getMapList("groups")) {
            try {
                Material icon = Material.matchMaterial(String.valueOf(m.get("icon")));
                boolean hidden = Boolean.TRUE.equals(m.get("hidden"));   // nhóm ẩn: chỉ mở bằng nút có open: <id>
                int slot = m.get("slot") instanceof Number n ? n.intValue() : -1;
                if (icon == null || (!hidden && (slot < 0 || slot >= rows * 9))) {
                    plugin.getLogger().warning("Menu: bỏ nhóm sai " + m.get("id"));
                    continue;
                }
                List<Button> bs = new ArrayList<>();
                if (m.get("buttons") instanceof List<?> l) {
                    for (Object o : l) {
                        if (o instanceof Map<?, ?> bm) {
                            Button b = button(bm, false);
                            if (b != null) {
                                bs.add(b);
                            }
                        }
                    }
                }
                Material b1 = Material.matchMaterial(str(m.get("border")));
                Material b2 = Material.matchMaterial(str(m.get("border2")));
                groups.add(new Group(str(m.get("id")), slot, icon, String.valueOf(m.get("name")), str(m.get("sub")), list(m.get("lore")),
                        list(m.get("gom")), b1 == null ? Material.YELLOW_STAINED_GLASS_PANE : b1, b2, str(m.get("title")), hidden, bs));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Menu: bỏ nhóm sai " + m.get("id") + ": " + e.getMessage());
            }
        }
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, PROXY_CHANNEL);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
    }

    private Button button(Map<?, ?> m, boolean needSlot) {
        try {
            int slot = m.get("slot") instanceof Number n ? n.intValue() : -1;
            Material icon = Material.matchMaterial(String.valueOf(m.get("icon")));
            if (icon == null || (needSlot && (slot < 0 || slot >= rows * 9))) {
                plugin.getLogger().warning("Menu: bỏ nút sai (ô " + slot + ", " + m.get("icon") + ")");
                return null;
            }
            return new Button(slot, icon, String.valueOf(m.get("name")), str(m.get("sub")), list(m.get("lore")), list(m.get("gom")),
                    str(m.get("bam")), str(m.get("cmd")), str(m.get("suggest")), str(m.get("proxy")), str(m.get("server")), str(m.get("open")));
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Menu: bỏ nút sai " + m + ": " + e.getMessage());
            return null;
        }
    }

    private static List<String> list(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> ll) {
            ll.forEach(x -> out.add(String.valueOf(x)));
        } else if (o != null) {
            out.add(String.valueOf(o));
        }
        return out;
    }

    /** Thay %placeholder% (PlaceholderAPI) nếu server có; không có thì giữ nguyên chữ. */
    private static String ph(Player p, String s) {
        if (s.indexOf('%') < 0 || !Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return s;
        }
        try {
            return PapiText.apply(p, s);
        } catch (Throwable t) {
            return s;
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static Component c(String s) {
        return Tip.ns(L.deserialize(s).decoration(TextDecoration.ITALIC, false));
    }

    int size() {
        return buttons.size() + groups.stream().mapToInt(g -> g.buttons().size()).sum();
    }

    void open(Player p) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, rows * 9, c(title));
        fill(h.inv, 0, rows * 9, filler);
        for (Button b : buttons.values()) {
            put(h, b.slot(), icon(p, b), b);
        }
        for (Group g : groups) {
            if (g.hidden()) {
                continue;
            }
            Tip t = Tip.of(ph(p, g.name()));
            if (!g.sub().isEmpty()) {
                t.sub(ph(p, g.sub()));
            }
            t.desc(g.lore().stream().map(x -> ph(p, x)).toArray(String[]::new));
            if (!g.gom().isEmpty()) {
                t.gap().head("Bao gồm");
                g.gom().forEach(x -> t.item(ph(p, x)));
            }
            t.line().click("Bấm để mở " + plain(g.name()));
            put(h, g.slot(), flags(t.apply(new ItemStack(g.icon()))), g);
        }
        if (!groups.isEmpty() && !h.slots.containsKey(rows * 9 - 5)) {
            put(h, rows * 9 - 5, flags(Tip.of("&c✘ &fĐóng").apply(new ItemStack(Material.BARRIER))), "close");
        }
        p.openInventory(h.inv);
    }

    /** Menu con của một nhóm: viền màu, tab các nhóm ở hàng trên, nút xếp giữa, Quay lại + Đóng ở hàng dưới. */
    void openGroup(Player p, Group g) {
        Holder h = new Holder();
        h.group = g;
        h.inv = Bukkit.createInventory(h, 54, c(g.title().isEmpty() ? title + " &8- &0" + plain(g.name()) : g.title()));
        fill(h.inv, 0, 54, filler);
        for (int i = 0; i < 54; i++) {
            int r = i / 9, col = i % 9;
            if (r == 0 || r == 5 || col == 0 || col == 8) {
                fill(h.inv, i, i + 1, g.border());
            } else if (r == 1 && g.border2() != null) {
                fill(h.inv, i, i + 1, g.border2());
            }
        }
        List<Group> tabs = groups.stream().filter(x -> !x.hidden() || x == g).toList();
        int start = Math.max(0, (9 - tabs.size()) / 2);
        for (int i = 0; i < tabs.size() && start + i < 9; i++) {
            Group o = tabs.get(i);
            Tip t = Tip.of(ph(p, o.name()));
            if (o == g) {
                t.kv("Trạng thái", "&aĐang xem");
            } else {
                t.click("Bấm để chuyển sang " + plain(o.name()));
            }
            ItemStack it = flags(t.apply(new ItemStack(o.icon())));
            if (o == g) {
                ItemMeta im = it.getItemMeta();
                im.setEnchantmentGlintOverride(true);
                it.setItemMeta(im);
            }
            put(h, start + i, it, o);
        }
        // nút: hàng 3-5 (ô giữa cột 1..7), mỗi hàng tối đa 7 (ít nút thì 5 cho thoáng), căn giữa
        List<Button> bs = g.buttons();
        int per = bs.size() > 10 ? 7 : 5;
        for (int i = 0; i < bs.size() && i < 21; i++) {
            int row = 2 + i / per;
            int inRow = Math.min(per, bs.size() - (i / per) * per);
            int col = (9 - inRow) / 2 + i % per;
            if (row > 4) {
                break;
            }
            put(h, row * 9 + col, icon(p, bs.get(i)), bs.get(i));
        }
        put(h, BACK_SLOT, flags(Tip.of("&e⬅ &fQuay lại").click("Bấm để về menu chính").apply(new ItemStack(Material.NETHER_STAR))), "back");
        put(h, CLOSE_SLOT, flags(Tip.of("&c✘ &fĐóng").apply(new ItemStack(Material.BARRIER))), "close");
        p.openInventory(h.inv);
    }

    private static void put(Holder h, int slot, ItemStack it, Object what) {
        h.inv.setItem(slot, it);
        h.slots.put(slot, what);
    }

    private static void fill(Inventory inv, int from, int to, Material m) {
        if (m == null || m == Material.AIR) {
            return;
        }
        ItemStack glass = new ItemStack(m);
        ItemMeta gm = glass.getItemMeta();
        gm.displayName(Component.text(" "));
        gm.setHideTooltip(true);
        glass.setItemMeta(gm);
        for (int i = from; i < to; i++) {
            inv.setItem(i, glass);
        }
    }

    private static String plain(String s) {
        return org.bukkit.ChatColor.stripColor(org.bukkit.ChatColor.translateAlternateColorCodes('&', s)).replaceAll("^[^\\p{L}]+", "").trim();
    }

    private static ItemStack flags(ItemStack it) {
        ItemMeta im = it.getItemMeta();
        im.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        it.setItemMeta(im);
        return it;
    }

    private ItemStack icon(Player p, Button b) {
        Tip t = Tip.of(ph(p, b.name()));
        if (!b.sub().isEmpty()) {
            t.sub(ph(p, b.sub()));
        }
        t.desc(b.lore().stream().map(x -> ph(p, x)).toArray(String[]::new));
        if (!b.gom().isEmpty()) {
            t.gap().head("Gồm");
            b.gom().forEach(x -> t.item(ph(p, x)));
        }
        t.line().click(b.bam().isEmpty() ? (b.suggest().isEmpty() ? "Bấm để mở" : "Bấm rồi gõ tiếp trong khung chat") : ph(p, b.bam()));
        ItemStack it = t.apply(new ItemStack(b.icon()));
        ItemMeta im = it.getItemMeta();
        if (im instanceof org.bukkit.inventory.meta.SkullMeta sm) {
            sm.setOwningPlayer(p);   // đầu người chơi = ô hồ sơ của chính mình
        }
        it.setItemMeta(im);
        return flags(it);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) {
            return;
        }
        Holder h = (Holder) e.getView().getTopInventory().getHolder();
        Object what = h.slots.get(e.getRawSlot());
        if (what instanceof Group g) {
            if (g != h.group) {
                Bukkit.getScheduler().runTask(plugin, () -> openGroup(p, g));
            }
        } else if ("back".equals(what)) {
            Bukkit.getScheduler().runTask(plugin, () -> open(p));
        } else if ("close".equals(what)) {
            p.closeInventory();
        } else if (what instanceof Button b) {
            if (h.group != null && !h.group.hidden()) {
                LAST_GROUP.put(p.getUniqueId(), h.group.id());
            } else if (h.group == null) {
                LAST_GROUP.remove(p.getUniqueId());
            }
            if (b.open().isEmpty()) {
                p.closeInventory();
            }
            Bukkit.getScheduler().runTask(plugin, () -> run(p, b));
        }
    }

    /** Nút "⬅ Về menu" (MenuFrame / InfoMenu) ở bất kỳ menu nào: mở lại nhóm /menu vừa vào, không có thì menu chính. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBack(InventoryClickEvent e) {
        if (MenuFrame.BACK_KEY == null || !(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) {
            return;
        }
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta() || !it.getItemMeta().getPersistentDataContainer().has(MenuFrame.BACK_KEY)) {
            return;
        }
        e.setCancelled(true);
        String gid = LAST_GROUP.get(p.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            Group g = gid == null ? null : groups.stream().filter(x -> x.id().equals(gid)).findFirst().orElse(null);
            if (g != null) {
                openGroup(p, g);
            } else {
                open(p);
            }
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    /** Shift + F (đổi tay khi đang ngồi) mở menu: không cần vật phẩm trên tay, điện thoại dùng /menu. */
    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (swapHand && e.getPlayer().isSneaking()) {
            e.setCancelled(true);
            open(e.getPlayer());
        }
    }

    private void run(Player p, Button b) {
        if (!b.open().isEmpty()) {
            groups.stream().filter(g -> g.id().equals(b.open())).findFirst().ifPresent(g -> openGroup(p, g));
        } else if (!b.server().isEmpty()) {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(b.server());
            p.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
        } else if (!b.proxy().isEmpty()) {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF(b.proxy());
            p.sendPluginMessage(plugin, PROXY_CHANNEL, out.toByteArray());
        } else if (!b.suggest().isEmpty()) {
            p.sendMessage(c("&6» &fBấm vào đây để gõ: &e" + b.suggest() + "&7 (điền tiếp rồi Enter)")
                    .clickEvent(ClickEvent.suggestCommand(b.suggest())));
        } else if (!b.cmd().isEmpty()) {
            String cmd = b.cmd().startsWith("/") ? b.cmd().substring(1) : b.cmd();
            java.util.function.BiPredicate<Player, String> gate = Shared.economyGate;   // nút menu không qua PlayerCommandPreprocessEvent
            if (gate != null && gate.test(p, cmd)) {
                return;
            }
            p.performCommand(cmd);
        }
    }
}
