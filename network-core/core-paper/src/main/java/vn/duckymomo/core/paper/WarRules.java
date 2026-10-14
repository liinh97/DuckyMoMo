package vn.duckymomo.core.paper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Luật chiến tranh Bang Hội Chiến (mục pvp-rules trong config.yml):
 *  - wild-pvp: false = ngoài đất thị trấn chỉ được đánh người trong vùng vây khi đang có phiên đánh (giai đoạn hoà bình);
 *    true = PvP đất hoang như thường. Trong thị trấn Towny tự quyết như cũ.
 *  - Quân hàm (guard, private…general): chỉ phong được cho người đã chơi đủ min-rank-hours ở server này, không phải tài khoản phụ.
 *  - Tài khoản phụ (admin ghi bằng /dmm phu): không nhận quân hàm, không đánh người trong vùng vây.
 */
final class WarRules implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final Set<String> TOWN_CMDS = Set.of("t", "town", "towny:t", "towny:town");
    private static final Set<String> NATION_CMDS = Set.of("n", "nation", "towny:n", "towny:nation");

    private final boolean wildPvp;
    private final double minRankHours;
    private final Set<String> militaryRanks;
    private final File altsFile;
    private final YamlConfiguration alts;

    WarRules(DuckyMoMoCore plugin, ConfigurationSection c) {
        this.wildPvp = c.getBoolean("wild-pvp", true);
        this.minRankHours = Math.max(0, c.getDouble("min-rank-hours", 5));
        this.militaryRanks = c.getStringList("military-ranks").stream()
                .map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        this.altsFile = new File(plugin.getDataFolder(), "taikhoanphu.yml");
        this.alts = YamlConfiguration.loadConfiguration(altsFile);
    }

    String describe() {
        return "PvP đất hoang " + (wildPvp ? "bật" : "TẮT (chỉ đánh trong vùng vây lúc có phiên)")
                + ", phong quân hàm cần " + minRankHours + " giờ chơi, " + alts.getKeys(false).size() + " tài khoản phụ";
    }

    // ------------------------------------------------------------------ PvP

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        Location at = victim.getLocation();
        boolean siegeFight = SiegeWarHook.battleActive() && SiegeWarHook.inActiveSiegeZone(at);
        if (siegeFight && isAlt(attacker.getName())) {
            event.setCancelled(true);
            attacker.sendActionBar(c("&cTài khoản phụ không được đánh trận."));
            return;
        }
        if (!wildPvp && !siegeFight && wilderness(at)) {
            event.setCancelled(true);
            attacker.sendActionBar(c("&eĐang thời kỳ hoà bình: chỉ đánh nhau trong vùng công thành."));
        }
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        ProjectileSource src = null;
        if (damager instanceof Projectile pr) {
            src = pr.getShooter();
        } else if (damager instanceof AreaEffectCloud cloud) {
            src = cloud.getSource();
        } else if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) {
            return p;
        } else if (damager instanceof Tameable pet && pet.getOwner() instanceof Player p) {
            return p;
        }
        return src instanceof Player p ? p : null;
    }

    private static boolean wilderness(Location loc) {
        return !Bukkit.getPluginManager().isPluginEnabled("Towny") || TownyCheck.wilderness(loc);
    }

    // ------------------------------------------------------------------ quân hàm

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String[] a = event.getMessage().substring(1).trim().split("\\s+");
        if (a.length < 5 || !a[1].equalsIgnoreCase("rank") || !a[2].equalsIgnoreCase("add")) {
            return;
        }
        String label = a[0].toLowerCase(Locale.ROOT);
        if (!TOWN_CMDS.contains(label) && !NATION_CMDS.contains(label)) {
            return;
        }
        boolean military = false;
        for (int i = 4; i < a.length; i++) {
            military |= militaryRanks.contains(a[i].toLowerCase(Locale.ROOT));
        }
        if (!military || event.getPlayer().hasPermission("duckymomo.war.bypass")) {
            return;
        }
        String target = a[3];
        Player sender = event.getPlayer();
        if (isAlt(target)) {
            event.setCancelled(true);
            sender.sendMessage(c("&c" + target + " là tài khoản phụ (" + alts.getString(key(target) + ".chinh") + "), không được nhận quân hàm."));
            return;
        }
        double hours = playHours(target);
        if (hours < 0) {
            return; // chưa từng vào server: để Towny tự báo lỗi
        }
        if (hours < minRankHours) {
            event.setCancelled(true);
            sender.sendMessage(c(String.format(Locale.ROOT,
                    "&c%s mới chơi %.1f giờ ở server này, cần đủ %.0f giờ mới được phong quân hàm.", target, hours, minRankHours)));
        }
    }

    /** Số giờ đã chơi ở server này (thống kê Minecraft), -1 nếu không tìm thấy người chơi. */
    private static double playHours(String name) {
        OfflinePlayer p = Bukkit.getPlayerExact(name);
        if (p == null) {
            p = Bukkit.getOfflinePlayerIfCached(name);
        }
        if (p == null || !p.hasPlayedBefore() && !p.isOnline()) {
            return -1;
        }
        return p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20.0 / 3600.0;
    }

    // ------------------------------------------------------------------ tài khoản phụ

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    boolean isAlt(String name) {
        return alts.isConfigurationSection(key(name));
    }

    /** /dmm phu add <phụ> <chính> | remove <phụ> | list */
    void altCommand(CommandSender s, String[] args) {
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "list";
        if (sub.equals("add") && args.length >= 4) {
            alts.set(key(args[2]) + ".ten", args[2]);
            alts.set(key(args[2]) + ".chinh", args[3]);
            alts.set(key(args[2]) + ".ghi-boi", s.getName());
            alts.set(key(args[2]) + ".ngay", java.time.LocalDate.now().toString());
            save(s);
            s.sendMessage(c("&aĐã ghi &f" + args[2] + " &alà tài khoản phụ của &f" + args[3]
                    + "&a. Nhớ gỡ quân hàm nếu đang có: /n rank remove " + args[2] + " <quân hàm>"));
        } else if (sub.equals("remove") && args.length >= 3) {
            alts.set(key(args[2]), null);
            save(s);
            s.sendMessage(c("&aĐã bỏ &f" + args[2] + " &akhỏi danh sách tài khoản phụ."));
        } else if (sub.equals("list")) {
            Map<String, List<String>> byMain = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (String k : alts.getKeys(false)) {
                byMain.computeIfAbsent(alts.getString(k + ".chinh", "?"), x -> new ArrayList<>()).add(alts.getString(k + ".ten", k));
            }
            s.sendMessage(c("&6Tài khoản phụ (" + alts.getKeys(false).size() + "):"));
            byMain.forEach((main, list) -> s.sendMessage(c("&f" + main + " &7→ &e" + String.join("&7, &e", list))));
        } else {
            s.sendMessage("/dmm phu add <tài khoản phụ> <tài khoản chính> | remove <tài khoản phụ> | list");
        }
    }

    private void save(CommandSender s) {
        try {
            alts.save(altsFile);
        } catch (IOException e) {
            s.sendMessage("Không lưu được taikhoanphu.yml: " + e.getMessage());
        }
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s);
    }

    /** Lớp riêng: server không có Towny không nạp tới lớp này. */
    private static final class TownyCheck {
        static boolean wilderness(Location loc) {
            return com.palmergames.bukkit.towny.TownyAPI.getInstance().isWilderness(loc);
        }
    }
}
