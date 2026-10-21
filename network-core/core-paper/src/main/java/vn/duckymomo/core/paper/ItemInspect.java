package vn.duckymomo.core.paper;

import io.papermc.paper.inventory.tooltip.TooltipContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.TradeSelectEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Xem ĐỦ mô tả (tooltip) của món ra khung chat (21/10: đồ nhiều phù phép + lore dài bị đẩy ra ngoài màn hình, game không cho cuộn hay
 * thu nhỏ tooltip). Dùng ItemStack#computeTooltipLines của Paper nên giống hệt game vẽ (kể cả khối "Khi được mặc" do phù phép, chữ dịch
 * theo ngôn ngữ máy người chơi). Chat cuộn được bằng chuột. Không đổi gì trên món.
 *  - /xemdo: món đang cầm · /xemdo <tên>: món người đó đang cầm · /xemdo trade: món vừa chọn ở màn trade dân làng.
 *  - Phím F (đổi tay) khi rê chuột lên món: trong menu plugin (Chợ đen, /kit, Gacha, Xưởng…) và ô kết quả trade luôn xem;
 *    trong túi đồ / rương / rương Ender / balo (/balo) chỉ xem khi mô tả dài hơn LONG dòng (món ngắn vẫn đổi tay như thường).
 *  - Chọn món trade có mô tả dài: Java nhận nút [Xem đủ] trong chat, Bedrock (không có phím F, không bấm được chat) được in luôn.
 */
final class ItemInspect implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    /** Màn hình 1080p, cỡ giao diện 3 hiện khoảng 20 dòng tooltip. */
    private static final int LONG = 20;
    private static final Map<UUID, ItemStack> LAST_TRADE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_SHOW = new ConcurrentHashMap<>();

    private static List<Component> lines(ItemStack it, Player p) {
        return it.computeTooltipLines(TooltipContext.create(false, false), p);
    }

    /** In tooltip ra chat. inGui: đang mở menu (chat nằm sau, nhắc đóng menu). */
    private static void show(Player p, ItemStack it, String from, boolean inGui) {
        long now = System.currentTimeMillis();
        if (now - LAST_SHOW.getOrDefault(p.getUniqueId(), 0L) < 800) {
            return;   // giữ phím F: không in lặp
        }
        LAST_SHOW.put(p.getUniqueId(), now);
        List<Component> ls = lines(it, p);
        p.sendMessage(LEGACY.deserialize("&8━━━━━━━━ &7Xem đồ" + (from == null ? "" : " &8· &7" + from) + " &8(" + ls.size() + " dòng) ━━━━━━━━"));
        ls.forEach(p::sendMessage);
        p.sendMessage(LEGACY.deserialize(inGui ? "&8━━━━━━━━ &7Đóng menu, mở chat (&fT&7), lăn chuột để xem hết &8━━━━━━━━"
                : "&8━━━━━━━━ &7Mở chat (&fT&7), lăn chuột để xem hết &8━━━━━━━━"));
    }

    static boolean command(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Chỉ dùng trong game: /xemdo | /xemdo <tên> | /xemdo trade");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("trade")) {
            ItemStack it = LAST_TRADE.get(p.getUniqueId());
            if (it == null) {
                p.sendMessage(LEGACY.deserialize("&eMở màn trade dân làng, bấm chọn một món rồi gõ &f/xemdo trade&e."));
            } else {
                show(p, it, "món trade", false);
            }
            return true;
        }
        if (args.length >= 1) {
            Player t = Bukkit.getPlayerExact(args[0]);
            if (t == null) {
                p.sendMessage(LEGACY.deserialize("&cKhông thấy người chơi &f" + args[0] + "&c đang online ở server này."));
            } else if (t.getInventory().getItemInMainHand().getType().isAir()) {
                p.sendMessage(LEGACY.deserialize("&e" + t.getName() + " không cầm gì trên tay."));
            } else {
                show(p, t.getInventory().getItemInMainHand(), "đồ " + t.getName() + " đang cầm", false);
            }
            return true;
        }
        ItemStack it = p.getInventory().getItemInMainHand();
        if (it.getType().isAir()) {
            p.sendMessage(LEGACY.deserialize("&eCầm món muốn xem trên tay chính rồi gõ &f/xemdo&e. Trong túi đồ / menu: rê chuột lên món, bấm &fF&e."));
            return true;
        }
        show(p, it, null, false);
        return true;
    }

    /** Kho thật (rương, lò, balo dạng block/entity…) hay túi đồ: F giữ chức năng đổi tay cho món mô tả ngắn. */
    private static boolean realStorage(Inventory inv) {
        if (inv == null) {
            return true;
        }
        if (inv.getType() == InventoryType.PLAYER || inv.getType() == InventoryType.CRAFTING || inv.getType() == InventoryType.ENDER_CHEST) {
            return true;
        }
        InventoryHolder h = inv.getHolder(false);
        return h instanceof Container || h instanceof DoubleChest || h instanceof Entity || h instanceof Backpack.Holder;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        if (e.getClick() != ClickType.SWAP_OFFHAND || !(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        ItemStack it = e.getCurrentItem();
        if (it == null || it.getType().isAir()) {
            return;
        }
        Inventory clicked = e.getClickedInventory();
        boolean merchant = clicked != null && clicked.getType() == InventoryType.MERCHANT;
        if (!merchant && realStorage(clicked) && lines(it, p).size() <= LONG) {
            return;   // món ngắn trong túi / rương: F đổi tay như thường
        }
        e.setCancelled(true);
        show(p, it.clone(), merchant ? "món trade" : null, true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrade(TradeSelectEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        MerchantRecipe r;
        try {
            r = e.getMerchant().getRecipe(e.getIndex());
        } catch (RuntimeException ex) {
            return;
        }
        ItemStack it = r.getResult().clone();
        LAST_TRADE.put(p.getUniqueId(), it);
        if (lines(it, p).size() <= LONG) {
            return;
        }
        if (BedrockMenu.isBedrock(p.getUniqueId())) {
            show(p, it, "món trade", true);
            return;
        }
        // ô kết quả chỉ hiện món khi đủ đồ trả, nên nút [Xem đủ] lên trước
        p.sendMessage(LEGACY.deserialize("&7Mô tả món này dài quá màn hình: bấm ")
                .append(LEGACY.deserialize("&a[Xem đủ]").clickEvent(ClickEvent.runCommand("/xemdo trade"))
                        .hoverEvent(HoverEvent.showText(LEGACY.deserialize("&7In đủ mô tả ra khung chat"))))
                .append(LEGACY.deserialize("&7 (hoặc khi đủ đồ trả: rê chuột vào ô kết quả, bấm &fF&7)")));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        LAST_TRADE.remove(e.getPlayer().getUniqueId());
        LAST_SHOW.remove(e.getPlayer().getUniqueId());
    }
}
