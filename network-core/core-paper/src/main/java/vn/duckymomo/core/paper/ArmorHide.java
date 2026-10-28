package vn.duckymomo.core.paper;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ẩn giáp (/angiap, 28/10, chủ server: "có skin thì mặc giáp vào bị xấu"). Bật thì NGƯỜI KHÁC nhìn thấy skin, không thấy giáp
 * (giáp vẫn bảo vệ đủ; chỉ gửi gói trang bị giả "không có gì" tới người xem bằng Player#sendEquipmentChange). Công bằng PvP:
 * CHỈ ẩn ở chỗ an toàn (vòng kinh đô / đất hoà bình / thị trấn của chính mình) và KHÔNG đang giao tranh (combat-seconds sau
 * khi đánh / bị người đánh); ra đất hoang, vùng vây hay vừa đánh nhau thì người khác thấy giáp thật ngay.
 * Config: armor-hide {enabled, combat-seconds}. Lưu người đang bật: plugins/DuckyMoMoCore/angiap.yml
 */
final class ArmorHide implements Listener {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final ItemStack AIR = new ItemStack(Material.AIR);

    private final JavaPlugin plugin;
    private final PeaceAlliance peace;
    private final long combatMillis;
    private final File file;
    private final Set<UUID> on = new HashSet<>();
    private final Set<UUID> hiddenNow = new HashSet<>();      // đang gửi giáp giả (để biết khi nào phải trả giáp thật)
    private final Map<UUID, Long> lastFight = new HashMap<>();

    ArmorHide(JavaPlugin plugin, ConfigurationSection cfg, PeaceAlliance peace) {
        this.plugin = plugin;
        this.peace = peace;
        this.combatMillis = Math.max(0, cfg.getInt("combat-seconds", 15)) * 1000L;
        this.file = new File(plugin.getDataFolder(), "angiap.yml");
        for (String s : YamlConfiguration.loadConfiguration(file).getStringList("bat")) {
            try {
                on.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
                // bỏ dòng hỏng
            }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> l = new ArrayList<>();
        on.forEach(u -> l.add(u.toString()));
        y.set("bat", l);
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được angiap.yml: " + e.getMessage());
        }
    }

    private static Component c(String s) {
        return Tip.c(s);
    }

    /** Chỗ an toàn: vòng kinh đô, đất hoà bình, hoặc ô đất thuộc thị trấn của chính người đó. */
    private boolean safeSpot(Player p) {
        Location l = p.getLocation();
        if (peace != null && (peace.inPeaceLand(l) || (peace.spawnRing != null && peace.spawnRing.test(l)))) {
            return true;
        }
        try {
            Town here = TownyAPI.getInstance().getTown(l);
            Resident r = TownyAPI.getInstance().getResident(p);
            return here != null && r != null && r.hasTown() && here.equals(r.getTownOrNull());
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    private boolean inCombat(Player p) {
        return System.currentTimeMillis() - lastFight.getOrDefault(p.getUniqueId(), 0L) < combatMillis;
    }

    /** Bật hay chưa: công tắc "angiap" ở /caidat (PlayerSettings, lưu meta LuckPerms); angiap.yml cũ vẫn tính. */
    private boolean enabled(Player p) {
        return PlayerSettings.INSTANCE != null ? PlayerSettings.on(p, "angiap") : on.contains(p.getUniqueId());
    }

    private boolean shouldHide(Player p) {
        return enabled(p) && !inCombat(p) && safeSpot(p);
    }

    /** Gửi giáp (giả hoặc thật) của p tới mọi người đang thấy p. */
    private void push(Player p, boolean hide) {
        for (Player v : p.getTrackedBy()) {
            send(v, p, hide);
        }
    }

    private static void send(Player viewer, Player p, boolean hide) {
        for (EquipmentSlot s : ARMOR) {
            ItemStack real = p.getInventory().getItem(s);
            viewer.sendEquipmentChange(p, s, hide ? AIR : (real == null ? AIR : real));
        }
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean hide = shouldHide(p);
            boolean was = hiddenNow.contains(p.getUniqueId());
            if (hide) {
                hiddenNow.add(p.getUniqueId());
                push(p, true);                                    // gửi lại mỗi giây: server có thể gửi giáp thật khi giáp đổi / mòn
            } else if (was) {
                hiddenNow.remove(p.getUniqueId());
                push(p, false);                                   // vừa ra chỗ không an toàn / vừa đánh nhau: hiện giáp thật ngay
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFight(EntityDamageByEntityEvent e) {
        Entity d = e.getDamager();
        if (d instanceof Projectile pr && pr.getShooter() instanceof Entity sh) {
            d = sh;
        }
        if (d instanceof Player a && e.getEntity() instanceof Player b && !a.equals(b)) {
            long now = System.currentTimeMillis();
            lastFight.put(a.getUniqueId(), now);
            lastFight.put(b.getUniqueId(), now);
            for (Player x : new Player[] {a, b}) {
                if (hiddenNow.remove(x.getUniqueId())) {
                    push(x, false);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmor(PlayerArmorChangeEvent e) {
        Player p = e.getPlayer();
        if (hiddenNow.contains(p.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> push(p, true));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrack(PlayerTrackEntityEvent e) {
        if (e.getEntity() instanceof Player p && hiddenNow.contains(p.getUniqueId())) {
            Player v = e.getPlayer();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (v.isOnline() && p.isOnline() && hiddenNow.contains(p.getUniqueId())) {
                    send(v, p, true);
                }
            }, 2L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        hiddenNow.remove(e.getPlayer().getUniqueId());
        lastFight.remove(e.getPlayer().getUniqueId());
    }

    boolean command(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Chỉ dùng trong game.");
            return true;
        }
        UUID u = p.getUniqueId();
        boolean was = enabled(p);
        on.remove(u);
        save();
        if (PlayerSettings.INSTANCE != null) {
            PlayerSettings.INSTANCE.set(p, "angiap", !was);              // cùng công tắc với /caidat
        } else if (!was) {
            on.add(u);
            save();
        }
        if (was) {
            if (hiddenNow.remove(u)) {
                push(p, false);
            }
            p.sendMessage(c("&7Đã tắt ẩn giáp: người khác thấy giáp của bạn như thường."));
        } else {
            p.sendMessage(c("&aĐã bật ẩn giáp: &fngười khác thấy skin, không thấy giáp. &7Bạn vẫn thấy giáp của mình (góc nhìn thứ 3, túi đồ): giáp vẫn đang mặc, vẫn bảo vệ đủ."));
            p.sendMessage(c("&7Chỉ ẩn ở kinh đô, đất hoà bình, thị trấn của bạn; ra đất hoang, vùng vây hoặc đang đánh nhau thì giáp hiện lại. Gõ &f/angiap &7để tắt."));
        }
        return true;
    }
}
