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
 */
final class PlayerMenu implements Listener {
    static final String PROXY_CHANNEL = "duckymomo:cmd";
    private static final LegacyComponentSerializer L = LegacyComponentSerializer.legacyAmpersand();

    record Button(int slot, Material icon, String name, String sub, List<String> lore, List<String> gom, String bam,
                  String cmd, String suggest, String proxy, String server) { }

    private final JavaPlugin plugin;
    private final String title;
    private final int rows;
    private final boolean swapHand;
    private final Material filler;
    private final Map<Integer, Button> buttons = new HashMap<>();

    static final class Holder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    PlayerMenu(JavaPlugin plugin, ConfigurationSection cfg) {
        this.plugin = plugin;
        this.title = cfg.getString("title", "&0Menu");
        this.rows = Math.max(1, Math.min(6, cfg.getInt("rows", 5)));
        this.swapHand = cfg.getBoolean("shift-f", true);
        Material f = Material.matchMaterial(cfg.getString("filler", "GRAY_STAINED_GLASS_PANE"));
        this.filler = f == null ? Material.AIR : f;
        for (Map<?, ?> m : cfg.getMapList("buttons")) {
            try {
                int slot = ((Number) m.get("slot")).intValue();
                Material icon = Material.matchMaterial(String.valueOf(m.get("icon")));
                if (icon == null || slot < 0 || slot >= rows * 9) {
                    plugin.getLogger().warning("Menu: bỏ nút sai (ô " + slot + ", " + m.get("icon") + ")");
                    continue;
                }
                buttons.put(slot, new Button(slot, icon, String.valueOf(m.get("name")), str(m.get("sub")), list(m.get("lore")), list(m.get("gom")),
                        str(m.get("bam")), str(m.get("cmd")), str(m.get("suggest")), str(m.get("proxy")), str(m.get("server"))));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Menu: bỏ nút sai " + m + ": " + e.getMessage());
            }
        }
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, PROXY_CHANNEL);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
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
        return L.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    int size() {
        return buttons.size();
    }

    void open(Player p) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, rows * 9, c(title));
        if (filler != Material.AIR) {
            ItemStack glass = new ItemStack(filler);
            ItemMeta gm = glass.getItemMeta();
            gm.displayName(Component.text(" "));
            glass.setItemMeta(gm);
            for (int i = 0; i < rows * 9; i++) {
                h.inv.setItem(i, glass);
            }
        }
        for (Button b : buttons.values()) {
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
            im.addItemFlags(org.bukkit.inventory.ItemFlag.values());
            it.setItemMeta(im);
            h.inv.setItem(b.slot(), it);
        }
        p.openInventory(h.inv);
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
        Button b = buttons.get(e.getRawSlot());
        if (b == null) {
            return;
        }
        p.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> run(p, b));
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
        if (!b.server().isEmpty()) {
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
            p.performCommand(b.cmd().startsWith("/") ? b.cmd().substring(1) : b.cmd());
        }
    }
}
