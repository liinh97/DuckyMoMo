package vn.duckymomo.core.paper;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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
import org.bukkit.event.player.PlayerGameModeChangeEvent;
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
 * (giáp vẫn bảo vệ đủ; chỉ gửi gói trang bị giả "không có gì" tới người xem bằng Player#sendEquipmentChange). Chủ server chốt:
 * bật là lúc nào cũng ẩn (cả PvP, đất hoang); CHỈ khi công thành (giờ đánh + trong vùng vây) giáp hiện. Dưới tên có món giáp
 * mạnh nhất (%dmmgiap_tren%) để người khác vẫn biết sức đối thủ.
 * Config: armor-hide {enabled, combat-seconds}. Lưu người đang bật: plugins/DuckyMoMoCore/angiap.yml
 */
final class ArmorHide implements Listener {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final ItemStack AIR = new ItemStack(Material.AIR);
    private static final java.util.regex.Pattern LEGACY_STRIP = java.util.regex.Pattern.compile("(?i)&[0-9a-fk-or]|&#[0-9a-f]{6}");

    private final JavaPlugin plugin;
    private final PeaceAlliance peace;
    private final long combatMillis;
    private final File file;
    private final Set<UUID> on = new HashSet<>();
    private final Set<UUID> hiddenNow = new HashSet<>();      // đang gửi giáp giả (để biết khi nào phải trả giáp thật)
    private final Map<UUID, Long> lastFight = new HashMap<>();
    private final boolean packets;                            // có PacketEvents: ẩn bằng sửa gói tin (không chớp)

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
        boolean pk = false;
        if (Bukkit.getPluginManager().isPluginEnabled("packetevents")) {
            try {
                ArmorHidePackets.register();
                pk = true;
            } catch (RuntimeException | LinkageError e) {
                plugin.getLogger().warning("Ẩn giáp: không dùng được PacketEvents, quay về cách gửi đè mỗi giây: " + e);
            }
        }
        this.packets = pk;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    /** Ẩn với cả chính mình chỉ khi sinh tồn / phiêu lưu: creative túi đồ do máy người chơi quyết, ô trông trống mà bấm là mất giáp thật. */
    private static boolean selfOk(Player p) {
        return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
    }

    /** Cập nhật danh sách sửa gói tin của p rồi gửi lại cho đúng (người khác: gói trang bị; mình: cả túi đồ). */
    private void apply(Player p, boolean hide) {
        int id = p.getEntityId();
        boolean self = hide && selfOk(p);
        boolean selfWas = ArmorHidePackets.SELF.contains(id);
        if (hide) {
            ArmorHidePackets.HIDDEN.add(id);
        } else {
            ArmorHidePackets.HIDDEN.remove(id);
        }
        if (self) {
            ArmorHidePackets.SELF.add(id);
        } else {
            ArmorHidePackets.SELF.remove(id);
        }
        for (Player v : p.getTrackedBy()) {
            send(v, p, hide);                                     // qua bộ lọc gói tin nên giáp giả/thật đều đúng
        }
        if (self != selfWas) {
            p.updateInventory();                                  // gửi lại cả túi đồ (bộ lọc làm trống ô giáp nếu đang ẩn)
            send(p, p, self);                                     // hình nhân vật của mình ở góc nhìn thứ 3
        }
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

    /** 28/10 chủ server: bật là LÚC NÀO cũng ẩn (kể cả PvP, đất hoang); CHỈ khi công thành (giờ đánh + trong vùng vây) giáp hiện.
     *  Dưới tên luôn có món giáp mạnh nhất (%dmmgiap_tren%) nên người khác vẫn biết sức đối thủ. */
    private boolean shouldHide(Player p) {
        return enabled(p) && !(SiegeWarHook.battleActive() && SiegeWarHook.inActiveSiegeZone(p.getLocation()));
    }

    /** Gửi giáp (giả hoặc thật) của p tới mọi người đang thấy p. */
    private void push(Player p, boolean hide) {
        for (Player v : p.getTrackedBy()) {
            send(v, p, hide);
        }
        send(p, p, hide && selfOk(p));   // 28/10 chủ server: ẩn cả với chính mình (góc nhìn thứ 3; ô giáp trong túi đồ trông trống nhưng vẫn mặc)
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
            if (packets) {
                if (hide) {
                    hiddenNow.add(p.getUniqueId());
                } else {
                    hiddenNow.remove(p.getUniqueId());
                }
                boolean selfNow = hide && selfOk(p);
                if (hide != was || selfNow != ArmorHidePackets.SELF.contains(p.getEntityId())) {
                    apply(p, hide);                               // chỉ gửi khi đổi trạng thái; còn lại bộ lọc gói tin lo
                }
                continue;
            }
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
            // 28/10: đánh nhau không còn làm hiện giáp (chỉ công thành mới hiện)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmor(PlayerArmorChangeEvent e) {
        Player p = e.getPlayer();
        if (!packets && hiddenNow.contains(p.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> push(p, true));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrack(PlayerTrackEntityEvent e) {
        if (!packets && e.getEntity() instanceof Player p && hiddenNow.contains(p.getUniqueId())) {
            Player v = e.getPlayer();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (v.isOnline() && p.isOnline() && hiddenNow.contains(p.getUniqueId())) {
                    send(v, p, true);
                }
            }, 2L);
        }
    }

    /** Sang creative / spectator: thôi ẩn với chính mình NGAY (không đợi tick), tránh bấm ô trông trống mà mất giáp. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMode(PlayerGameModeChangeEvent e) {
        Player p = e.getPlayer();
        if (!hiddenNow.contains(p.getUniqueId())) {
            return;
        }
        if (packets) {
            ArmorHidePackets.SELF.remove(p.getEntityId());
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) {
                if (packets) {
                    apply(p, hiddenNow.contains(p.getUniqueId()));
                } else {
                    send(p, p, false);
                    p.updateInventory();
                }
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (packets) {
            ArmorHidePackets.HIDDEN.remove(e.getPlayer().getEntityId());
            ArmorHidePackets.SELF.remove(e.getPlayer().getEntityId());
        }
        hiddenNow.remove(e.getPlayer().getUniqueId());
        lastFight.remove(e.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- món giáp mạnh nhất (hiện dưới tên khi đang ẩn giáp, 28/10)
    private static int tier(Material m) {
        String n = m.name();
        if (n.startsWith("NETHERITE_")) {
            return 7;
        }
        if (n.startsWith("DIAMOND_")) {
            return 6;
        }
        if (n.startsWith("IRON_") || n.equals("TURTLE_HELMET")) {
            return 5;
        }
        if (n.startsWith("CHAINMAIL_") || n.startsWith("COPPER_")) {
            return 4;
        }
        if (n.startsWith("GOLDEN_")) {
            return 3;
        }
        return n.startsWith("LEATHER_") ? 2 : 1;
    }

    private static String pieceName(Material m) {
        String n = m.name();
        if (n.equals("TURTLE_HELMET")) {
            return "Mai Rùa";
        }
        String part = n.endsWith("_HELMET") ? "Mũ" : n.endsWith("_CHESTPLATE") ? "Áo Giáp" : n.endsWith("_LEGGINGS") ? "Quần"
                : n.endsWith("_BOOTS") ? "Giày" : null;
        if (part == null) {
            return null;
        }
        String mat = n.startsWith("NETHERITE_") ? "Netherite" : n.startsWith("DIAMOND_") ? "Kim Cương" : n.startsWith("IRON_") ? "Sắt"
                : n.startsWith("CHAINMAIL_") ? "Xích" : n.startsWith("COPPER_") ? "Đồng" : n.startsWith("GOLDEN_") ? "Vàng"
                : n.startsWith("LEATHER_") ? "Da" : "";
        return (part + " " + mat).trim();
    }

    /** "Quần Kim Cương +4" (món mạnh nhất theo chất liệu, rồi cấp Bảo vệ); null nếu không mặc giáp. */
    static String bestPiece(Player p) {
        String best = null;
        int bestScore = -1;
        for (EquipmentSlot s : ARMOR) {
            ItemStack it = p.getInventory().getItem(s);
            if (it == null || it.getType().isAir()) {
                continue;
            }
            String name = pieceName(it.getType());
            if (name == null) {
                continue;
            }
            int prot = it.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.PROTECTION);
            int score = tier(it.getType()) * 100 + prot;
            if (score > bestScore) {
                bestScore = score;
                best = name + (prot > 0 ? " +" + prot : "");
            }
        }
        return best;
    }

    /** Placeholder %dmmgiap_tren%: " · ⛨ <món mạnh nhất>" khi người đó đang ẩn giáp, rỗng khi không (TAB dòng dưới tên). */
    me.clip.placeholderapi.expansion.PlaceholderExpansion placeholders() {
        return new me.clip.placeholderapi.expansion.PlaceholderExpansion() {
            @Override
            public String getIdentifier() {
                return "dmmgiap";
            }

            @Override
            public String getAuthor() {
                return "DuckyMoMo";
            }

            @Override
            public String getVersion() {
                return "1";
            }

            @Override
            public boolean persist() {
                return true;
            }

            @Override
            public String onRequest(org.bukkit.OfflinePlayer player, String params) {
                Player p = player == null ? null : player.getPlayer();
                if (p == null || !params.equals("tren") || !hiddenNow.contains(p.getUniqueId())) {
                    return "";
                }
                String b = ThoRen.wornSet(p);   // 28/10 chủ server: hiện TÊN SET (ai cũng có đồ kim cương, tên món không nói lên gì)
                return b == null ? "" : " &8· &b⛨ " + LEGACY_STRIP.matcher(b).replaceAll("");
            }
        };
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
            p.sendMessage(c("&aĐã bật ẩn giáp: &fai cũng thấy skin, không thấy giáp, kể cả bạn. &eÔ giáp trong túi đồ trông TRỐNG nhưng giáp vẫn đang mặc, vẫn bảo vệ đủ &7(đừng tưởng mất đồ)."));
            p.sendMessage(c("&7Ẩn ở mọi nơi, kể cả đất hoang và lúc đánh nhau; CHỈ khi công thành (giờ đánh, trong vùng vây) giáp mới hiện. Dưới tên bạn hiện set đồ đang mặc. Gõ &f/angiap &7để tắt."));
        }
        return true;
    }
}
