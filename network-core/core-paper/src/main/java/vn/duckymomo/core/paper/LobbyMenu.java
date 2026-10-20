package vn.duckymomo.core.paper;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Menu chọn chế độ ở lobby: la bàn trên thanh công cụ (chuột phải / chạm để mở) và lệnh /menu.
 * Danh sách chế độ tự lấy từ modes/*\/mode.yml; bấm vào là chuyển server qua proxy (kênh BungeeCord của Velocity).
 * Người chơi điện thoại (Geyser) thấy menu dạng rương như Java.
 */
final class LobbyMenu implements Listener {

    private static final String CHANNEL = "BungeeCord";

    private final DuckyMoMoCore plugin;
    private final File modesDir;
    private final int itemSlot;
    private final NamespacedKey menuItemKey;
    private final NamespacedKey modeKey;

    LobbyMenu(DuckyMoMoCore plugin, File modesDir, int itemSlot) {
        this.plugin = plugin;
        this.modesDir = modesDir;
        this.itemSlot = itemSlot;
        this.menuItemKey = new NamespacedKey(plugin, "lobby_menu_item");
        this.modeKey = new NamespacedKey(plugin, "mode_server");
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    // ------------------------------------------------------------------ la bàn trên tay

    private ItemStack menuItem() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text("Chọn chế độ", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(text("Chuột phải (điện thoại: chạm giữ) để mở", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(menuItemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isMenuItem(ItemStack item) {
        return item != null && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(menuItemKey, PersistentDataType.BYTE);
    }

    private void giveMenuItem(Player player) {
        for (ItemStack it : player.getInventory().getContents()) {
            if (isMenuItem(it)) {
                return;
            }
        }
        player.getInventory().setItem(itemSlot, menuItem());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        giveMenuItem(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> giveMenuItem(event.getPlayer()));
    }

    // LOWEST + DENY: chạy trước WorldEdit. La bàn cũng là "gậy dịch chuyển" của WorldEdit (trái: nhảy tới, phải: xuyên tường),
    // admin/builder có quyền WorldEdit bấm la bàn bị dịch chuyển thay vì mở menu. WorldEdit bỏ qua sự kiện có useItemInHand = DENY.
    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL || !isMenuItem(event.getItem())) {
            return;
        }
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setCancelled(true);
        Player p = event.getPlayer();
        // mở ở tick sau: nếu plugin khác vẫn dịch chuyển/đóng giao diện trong tick này thì menu không bị đóng mất
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) {
                open(p);
            }
        });
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isMenuItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (isMenuItem(event.getMainHandItem()) || isMenuItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ menu

    /** Đánh dấu inventory là menu của plugin (để nhận ra khi bấm). */
    private static final class MenuHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    void open(Player player) {
        List<GameMode> visible = new ArrayList<>();
        for (GameMode mode : GameMode.loadAll(modesDir, plugin.getLogger())) {
            if (mode.hidden() || mode.server() == null) {
                continue;
            }
            if (mode.status().equals("beta") && !player.hasPermission(mode.betaPermission())) {
                continue;
            }
            visible.add(mode);
        }

        // Bedrock (Floodgate): form nút bấm thay cho giao diện rương
        if (BedrockMenu.available() && BedrockMenu.isBedrock(player.getUniqueId())) {
            List<BedrockMenu.Entry> entries = new ArrayList<>();
            for (GameMode mode : visible) {
                String state = switch (mode.status()) {
                    case "open" -> "Đang mở";
                    case "beta" -> "Thử nghiệm";
                    case "maintenance" -> "Bảo trì";
                    default -> mode.status();
                };
                boolean ok = mode.joinable() && mode.supportsBedrock();
                String label = mode.displayName() + "\n" + (mode.supportsBedrock() ? state : "Chưa hỗ trợ điện thoại");
                entries.add(new BedrockMenu.Entry(label, ok ? mode.server() : null));
            }
            BedrockMenu.open(player.getUniqueId(), "Chọn chế độ",
                    entries.isEmpty() ? "Chưa có chế độ nào mở, quay lại sau nhé!" : "Bấm vào chế độ muốn chơi:",
                    entries, server -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.sendMessage(text("Đang chuyển sang " + server + "…", NamedTextColor.GRAY));
                        connect(player, server);
                    }));
            return;
        }

        int rows = Math.max(3, Math.min(6, (visible.size() + 8) / 9 + 2));
        MenuHolder holder = new MenuHolder();
        Inventory inv = plugin.getServer().createInventory(holder, rows * 9, text("Chọn chế độ", NamedTextColor.DARK_GRAY));
        holder.inventory = inv;

        if (visible.isEmpty()) {
            inv.setItem(13, button(Material.BARRIER, text("Chưa có chế độ nào mở", NamedTextColor.RED),
                List.of(text("Quay lại sau nhé!", NamedTextColor.GRAY)), null));
        }
        boolean bedrock = BedrockAuthListener.isBedrock(player.getUniqueId(), player.getName());
        int slot = 10;
        for (GameMode mode : visible) {
            if (slot % 9 == 8) {
                slot += 2;
            }
            if (slot >= inv.getSize() - 9) {
                break;
            }
            inv.setItem(slot++, modeButton(mode, bedrock));
        }
        player.openInventory(inv);
    }

    private ItemStack modeButton(GameMode mode, boolean bedrock) {
        Material icon = Material.matchMaterial(mode.icon());
        if (icon == null || !icon.isItem()) {
            icon = Material.GRASS_BLOCK; // icon của mod (vd cobblemon:poke_ball) không có trên Paper
        }
        Tip t = Tip.of("&6✦ &f" + mode.displayName());
        if (!mode.description().isBlank()) {
            t.desc(mode.description());
        }
        String server = mode.server();
        t.line().kv("Trạng thái", switch (mode.status()) {
            case "open" -> "&a● Đang mở";
            case "beta" -> "&e● Thử nghiệm (beta)";
            case "maintenance" -> "&c● Đang bảo trì";
            default -> "&7● " + mode.status();
        });
        t.kv("Điện thoại", mode.supportsBedrock() ? "&achơi được" : "&cchưa hỗ trợ").line();
        if (bedrock && !mode.supportsBedrock()) {
            t.deny("Chế độ này chưa hỗ trợ điện thoại");
            server = null;
        } else if (!mode.joinable()) {
            t.deny("Chưa vào được lúc này");
            server = null;
        } else {
            t.click("Bấm để vào");
        }
        return button(icon, t.name(), t.lore(), server);
    }

    private ItemStack button(Material material, Component name, List<Component> lore, String server) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        if (server != null) {
            meta.getPersistentDataContainer().set(modeKey, PersistentDataType.STRING, server);
        }
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        // Không cho kéo la bàn ra khỏi chỗ
        if (isMenuItem(event.getCurrentItem()) || isMenuItem(event.getCursor())) {
            event.setCancelled(true);
        }
        if (!(event.getInventory().getHolder() instanceof MenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getInventory()) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }
        String server = clicked.getItemMeta().getPersistentDataContainer().get(modeKey, PersistentDataType.STRING);
        if (server == null) {
            return;
        }
        player.closeInventory();
        player.sendMessage(text("Đang chuyển sang ", NamedTextColor.GRAY)
            .append(clicked.getItemMeta().displayName() == null ? Component.text(server) : clicked.getItemMeta().displayName())
            .append(text("…", NamedTextColor.GRAY)));
        connect(player, server);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    private void connect(Player player, String server) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);
        player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
    }

    private static Component text(String s, NamedTextColor color) {
        return Component.text(s, color).decoration(TextDecoration.ITALIC, false);
    }
}
