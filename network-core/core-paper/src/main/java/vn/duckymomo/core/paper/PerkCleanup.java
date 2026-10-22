package vn.duckymomo.core.paper;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dọn phần còn sót khi quyền mua bằng Vàng HẾT HẠN (22/10, test bot tools/bottest/vang-hethan.js: LuckPerms gỡ quyền đúng giờ nhưng nick,
 * món đội bằng /hat, hạt PlayerParticles vẫn còn). Chạy lúc vào game + mỗi 30 giây với người đang online:
 *  - Nick: còn nick mà không còn essentials.nick → "nick <tên> off"; còn quyền nick nhưng mất essentials.nick.color mà nick có màu → bỏ màu.
 *  - /hat: không còn essentials.hat mà trên đầu là món KHÔNG đội được theo cách thường (khối, bí ngô thường…) → cất vào túi, đầy thì thả.
 *    Mũ giáp, bí ngô khắc, đầu mob, món có ô đội đầu riêng vẫn giữ.
 *  - Hạt: vừa mất quyền dùng hiệu ứng (playerparticles.effect.flame làm mẫu) trong lúc online → "ppo <tên> reset". Hết hạn lúc offline:
 *    PlayerParticles check-permissions-on-login: true tự gỡ.
 * Nick đọc qua Essentials (reflection, không cần thư viện lúc build).
 */
final class PerkCleanup implements Listener {

    private final JavaPlugin plugin;
    private final Plugin ess;
    /** Người đang có quyền hiệu ứng hạt ở lần kiểm trước (chỉ reset khi vừa MẤT, không chạy lệnh mỗi 30 giây). */
    private final Set<UUID> hadParticles = ConcurrentHashMap.newKeySet();

    PerkCleanup(JavaPlugin plugin) {
        this.plugin = plugin;
        this.ess = Bukkit.getPluginManager().getPlugin("Essentials");
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::check), 600L, 600L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                check(p);
            }
        }, 60L);   // chờ LuckPerms nạp quyền xong
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        hadParticles.remove(e.getPlayer().getUniqueId());
    }

    private void check(Player p) {
        nick(p);
        hat(p);
        if (Bukkit.getPluginManager().isPluginEnabled("PlayerParticles")) {   // hỏi mỗi lần: PlayerParticles bật SAU DuckyMoMoCore (test bot 22/10)
            boolean has = p.hasPermission("playerparticles.effect.flame");
            if (has) {
                hadParticles.add(p.getUniqueId());
            } else if (hadParticles.remove(p.getUniqueId())) {
                console("ppo " + p.getName() + " reset");
                p.sendMessage("§7Hiệu ứng hạt đã hết hạn. Mua lại ở §e/vang§7.");
            }
        }
    }

    private void nick(Player p) {
        if (ess == null || !ess.isEnabled()) {
            return;
        }
        String nick;
        try {
            Object user = ess.getClass().getMethod("getUser", Player.class).invoke(ess, p);
            nick = user == null ? null : (String) user.getClass().getMethod("getNickname").invoke(user);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return;
        }
        if (nick == null || nick.isBlank()) {
            return;
        }
        if (!p.hasPermission("essentials.nick")) {
            console("essentials:nick " + p.getName() + " off");
            p.sendMessage("§7Quyền đổi biệt danh đã hết hạn, tên trở lại như cũ. Mua lại ở §e/vang§7.");
        } else if (!p.hasPermission("essentials.nick.color") && nick.matches(".*[&§][0-9a-fk-orA-FK-OR#].*")) {
            String plain = nick.replaceAll("[&§]#[0-9a-fA-F]{6}|[&§][0-9a-fk-orA-FK-OR]", "");
            if (!plain.isBlank()) {
                console("essentials:nick " + p.getName() + " " + plain);
                p.sendMessage("§7Quyền biệt danh có màu đã hết hạn, biệt danh trở về chữ thường.");
            }
        }
    }

    private void hat(Player p) {
        if (ess == null || p.hasPermission("essentials.hat")) {
            return;
        }
        ItemStack head = p.getInventory().getHelmet();
        if (head == null || head.getType().isAir() || wearable(head)) {
            return;
        }
        p.getInventory().setHelmet(null);
        for (ItemStack left : p.getInventory().addItem(head).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
        p.sendMessage("§7Quyền đội mũ /hat đã hết hạn, đã cất món trên đầu vào túi. Mua lại ở §e/vang§7.");
    }

    /** Món tự đội được không cần /hat: mũ giáp, bí ngô khắc, đầu mob, món có ô đội đầu riêng (component equippable). */
    private static boolean wearable(ItemStack it) {
        Material m = it.getType();
        if (m.getEquipmentSlot() == EquipmentSlot.HEAD) {
            return true;
        }
        return it.hasItemMeta() && it.getItemMeta().hasEquippable() && it.getItemMeta().getEquippable().getSlot() == EquipmentSlot.HEAD;
    }

    private static void console(String cmd) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }
}
