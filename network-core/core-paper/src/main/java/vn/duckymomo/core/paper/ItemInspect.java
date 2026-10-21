package vn.duckymomo.core.paper;

import io.papermc.paper.inventory.tooltip.TooltipContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * /xemdo: in NGUYÊN tooltip của món đang cầm ra khung chat (21/10: đồ nhiều phù phép + lore dài bị đẩy ra ngoài màn hình, game không
 * cho cuộn hay thu nhỏ tooltip). Dùng ItemStack#computeTooltipLines của Paper nên giống hệt game vẽ (kể cả khối "Khi được mặc" do phù phép,
 * chữ dịch theo ngôn ngữ máy người chơi). Khung chat cuộn được bằng chuột nên dài bao nhiêu cũng xem hết. Không đổi gì trên món.
 */
final class ItemInspect {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private ItemInspect() {
    }

    static boolean command(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Chỉ dùng trong game: cầm món trên tay rồi gõ /xemdo");
            return true;
        }
        ItemStack it = p.getInventory().getItemInMainHand();
        if (it.getType().isAir()) {
            p.sendMessage(LEGACY.deserialize("&eCầm món muốn xem trên tay chính rồi gõ &f/xemdo&e."));
            return true;
        }
        List<Component> lines = it.computeTooltipLines(TooltipContext.create(false, false), p);
        p.sendMessage(LEGACY.deserialize("&8━━━━━━━━ &7Xem đồ &8(" + lines.size() + " dòng) ━━━━━━━━"));
        for (Component line : lines) {
            p.sendMessage(line);
        }
        p.sendMessage(LEGACY.deserialize("&8━━━━━━━━ &7Mở chat (&fT&7), lăn chuột để xem hết &8━━━━━━━━"));
        return true;
    }
}
