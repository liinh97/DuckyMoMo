package vn.duckymomo.core.paper;

import com.palmergames.bukkit.towny.object.Resident;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Treo thưởng (/treothuong, ý từ UniMC "Bounty"). Khác "truy nã" của Hội Bàn Tròn (đó là án phạt).
 *  - Người chơi bỏ tiền treo thưởng lên đầu người khác (phí fee-percent % không hoàn). Ai hạ được người đó thì nhận hết tiền treo.
 *  - Hệ thống tự treo (streak): ai giết liền kills mạng mà chưa chết thì bị treo reward xu, mỗi mạng thêm +per-kill.
 *  - Chống gian: không nhận thưởng khi giết tài khoản phụ (WarRules), cùng IP; không treo lên dân Liên minh Hoà Bình (allow-on-peace);
 *    phần tiền chính người hạ đã treo thì trả lại cho họ (không tự treo rồi tự ăn).
 *  - Hết days ngày không ai hạ: trả tiền lại cho người treo (không trả phí), phần hệ thống biến mất.
 * Lưu: plugins/DuckyMoMoCore/treothuong.yml (muc.<uuid>.{ten, het, he-thong, nguoi.<uuid>.{ten, xu}}, chuoi.<uuid>).
 */
final class Bounty implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private static final class Holder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final JavaPlugin plugin;
    private final ConfigurationSection cfg;
    private final WarRules war;
    private final PeaceAlliance peace;
    private final File file;
    private final YamlConfiguration data;

    Bounty(JavaPlugin plugin, ConfigurationSection cfg, WarRules war, PeaceAlliance peace) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.war = war;
        this.peace = peace;
        this.file = new File(plugin.getDataFolder(), "treothuong.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::expire, 400L, 12000L);
    }

    private static Component c(String s) {
        return Tip.ns(LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false));
    }

    private static String money(double v) {
        return VaultEconomy.format(v);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được treothuong.yml: " + e.getMessage());
        }
    }

    private String base(UUID id) {
        return "muc." + id;
    }

    double total(UUID id) {
        String b = base(id);
        double t = data.getDouble(b + ".he-thong");
        ConfigurationSection ps = data.getConfigurationSection(b + ".nguoi");
        if (ps != null) {
            for (String k : ps.getKeys(false)) {
                t += ps.getDouble(k + ".xu");
            }
        }
        return t;
    }

    private void extend(UUID id, String name) {
        data.set(base(id) + ".ten", name);
        data.set(base(id) + ".het", System.currentTimeMillis() + cfg.getInt("days", 3) * 86_400_000L);
    }

    private boolean peaceful(UUID id) {
        if (peace == null) {
            return false;
        }
        Resident r = Members.resident(id);
        return r != null && peace.isMember(r.getTownOrNull());
    }

    private static String ip(Player p) {
        InetSocketAddress a = p.getAddress();
        return a == null || a.getAddress() == null ? "" : a.getAddress().getHostAddress();
    }

    // ------------------------------------------------------------ treo

    private void place(Player p, String[] args) {
        if (args.length < 3) {
            p.sendMessage(c("&e/treothuong dat <người> <xu>"));
            return;
        }
        Player t = Bukkit.getPlayerExact(args[1]);
        OfflinePlayer target = t != null ? t : Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null || target.getName() == null) {
            p.sendMessage(c("&cKhông thấy người chơi " + args[1] + "."));
            return;
        }
        if (target.getUniqueId().equals(p.getUniqueId())) {
            p.sendMessage(c("&cKhông tự treo thưởng cho mình."));
            return;
        }
        if (!cfg.getBoolean("allow-on-peace", false) && peaceful(target.getUniqueId())) {
            p.sendMessage(c("&c" + target.getName() + " là dân Liên minh Hoà Bình, không treo thưởng được."));
            return;
        }
        if (war != null && war.isAlt(target.getName())) {
            p.sendMessage(c("&cKhông treo thưởng lên tài khoản phụ."));
            return;
        }
        double amount;
        try {
            amount = Math.floor(Double.parseDouble(args[2]));
        } catch (NumberFormatException e) {
            p.sendMessage(c("&cSố xu không hợp lệ."));
            return;
        }
        double min = cfg.getDouble("min", 100);
        if (amount < min) {
            p.sendMessage(c("&cTreo ít nhất " + money(min) + "."));
            return;
        }
        if (total(target.getUniqueId()) + amount > cfg.getDouble("max-total", 100000)) {
            p.sendMessage(c("&cTổng tiền treo lên một người tối đa " + money(cfg.getDouble("max-total", 100000)) + "."));
            return;
        }
        double fee = Math.ceil(amount * cfg.getDouble("fee-percent", 5)) / 100.0;
        if (!VaultEconomy.withdraw(p, amount + fee)) {
            p.sendMessage(c("&cKhông đủ tiền: cần " + money(amount + fee) + " (phí " + money(fee) + ")."));
            return;
        }
        String k = base(target.getUniqueId()) + ".nguoi." + p.getUniqueId();
        data.set(k + ".ten", p.getName());
        data.set(k + ".xu", data.getDouble(k + ".xu") + amount);
        extend(target.getUniqueId(), target.getName());
        save();
        double sum = total(target.getUniqueId());
        Bukkit.broadcast(c("&6☠ [Treo thưởng] &f" + p.getName() + " &7treo &e" + money(amount) + " &7lên đầu &c" + target.getName()
                + " &7(tổng &e" + money(sum) + "&7). Hạ được là nhận!"));
        plugin.getLogger().info("Treo thưởng: " + p.getName() + " treo " + amount + " lên " + target.getName());
        if (target.getPlayer() != null) {
            target.getPlayer().playSound(target.getPlayer().getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.4f, 1.6f);
        }
    }

    // ------------------------------------------------------------ giết

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = Culprit.killer(victim);   // pet / mũi tên / thú thuần hoá tính về chủ
        UUID vid = victim.getUniqueId();
        data.set("chuoi." + vid, null);   // chết: mất chuỗi
        if (killer == null || killer.equals(victim)) {
            save();
            return;
        }
        boolean fair = (war == null || !war.isAlt(victim.getName()) && !war.isAlt(killer.getName()))
                && !ip(killer).equals(ip(victim)) && !peaceful(vid);
        if (fair) {
            claim(victim, killer);
            streak(killer);
        }
        save();
    }

    private void claim(Player victim, Player killer) {
        String b = base(victim.getUniqueId());
        if (!data.isConfigurationSection(b)) {
            return;
        }
        double pay = data.getDouble(b + ".he-thong");
        double own = 0;
        ConfigurationSection ps = data.getConfigurationSection(b + ".nguoi");
        if (ps != null) {
            for (String k : ps.getKeys(false)) {
                double x = ps.getDouble(k + ".xu");
                if (k.equals(killer.getUniqueId().toString())) {
                    own += x;   // tiền chính mình treo: trả lại, không tính là thưởng
                } else {
                    pay += x;
                }
            }
        }
        data.set(b, null);
        if (own > 0) {
            VaultEconomy.deposit(killer, own);
        }
        if (pay <= 0) {
            return;
        }
        VaultEconomy.deposit(killer, pay);
        killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 0.8f);
        Bukkit.broadcast(c("&6☠ [Treo thưởng] &a" + killer.getName() + " &7đã hạ &c" + victim.getName() + " &7và nhận &e" + money(pay) + "&7!"));
        plugin.getLogger().info("Treo thưởng: " + killer.getName() + " hạ " + victim.getName() + " nhận " + pay);
    }

    private void streak(Player killer) {
        ConfigurationSection st = cfg.getConfigurationSection("streak");
        if (st == null || !st.getBoolean("enabled", true) || peaceful(killer.getUniqueId())) {
            return;
        }
        int n = data.getInt("chuoi." + killer.getUniqueId()) + 1;
        data.set("chuoi." + killer.getUniqueId(), n);
        int need = st.getInt("kills", 5);
        if (n < need) {
            return;
        }
        double add = n == need ? st.getDouble("reward", 500) : st.getDouble("per-kill", 100);
        if (add <= 0) {
            return;
        }
        String b = base(killer.getUniqueId());
        data.set(b + ".he-thong", data.getDouble(b + ".he-thong") + add);
        extend(killer.getUniqueId(), killer.getName());
        Bukkit.broadcast(c("&4☠ [Hệ thống] &c" + killer.getName() + " &7đang có chuỗi &f" + n + " &7mạng. Treo thưởng thêm &e" + money(add)
                + " &7(tổng &e" + money(total(killer.getUniqueId())) + "&7). &f/treothuong"));
    }

    // ------------------------------------------------------------ hết hạn

    private void expire() {
        ConfigurationSection m = data.getConfigurationSection("muc");
        if (m == null) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (String id : new ArrayList<>(m.getKeys(false))) {
            if (m.getLong(id + ".het") > now) {
                continue;
            }
            ConfigurationSection ps = m.getConfigurationSection(id + ".nguoi");
            if (ps != null) {
                for (String k : ps.getKeys(false)) {
                    try {
                        VaultEconomy.deposit(Bukkit.getOfflinePlayer(UUID.fromString(k)), ps.getDouble(k + ".xu"));
                    } catch (IllegalArgumentException ignored) {
                        // khoá hỏng: bỏ
                    }
                }
            }
            plugin.getLogger().info("Treo thưởng hết hạn: " + m.getString(id + ".ten") + " (trả lại tiền người treo)");
            m.set(id, null);
            changed = true;
        }
        if (changed) {
            save();
        }
    }

    // ------------------------------------------------------------ menu, lệnh

    private record Entry(UUID id, String name, double total, long until, String by) { }

    private List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        ConfigurationSection m = data.getConfigurationSection("muc");
        if (m != null) {
            for (String id : m.getKeys(false)) {
                List<String> by = new ArrayList<>();
                if (m.getDouble(id + ".he-thong") > 0) {
                    by.add("Hệ thống");
                }
                ConfigurationSection ps = m.getConfigurationSection(id + ".nguoi");
                if (ps != null) {
                    for (String k : ps.getKeys(false)) {
                        by.add(ps.getString(k + ".ten", "?"));
                    }
                }
                try {
                    UUID u = UUID.fromString(id);
                    out.add(new Entry(u, m.getString(id + ".ten", "?"), total(u), m.getLong(id + ".het"), String.join(", ", by)));
                } catch (IllegalArgumentException ignored) {
                    // bỏ
                }
            }
        }
        out.sort(Comparator.comparingDouble(Entry::total).reversed());
        return out;
    }

    private void open(Player p) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 54, c("&0☠ Treo thưởng"));
        List<Entry> list = entries();
        for (int i = 0; i < list.size() && i < 45; i++) {
            Entry en = list.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(en.id()));
            long h1 = Math.max(0, en.until() - System.currentTimeMillis()) / 3_600_000L;
            Tip t = Tip.of("&c☠ &f" + en.name()).accent("&c").desc("Hạ người này để nhận tiền thưởng").line()
                    .kv("Tiền thưởng", "&e" + money(en.total())).kv("Người treo", en.by())
                    .kv("Trạng thái", Bukkit.getPlayer(en.id()) != null ? "&aĐang trực tuyến" : "&7Ngoại tuyến")
                    .kv("Hết hạn sau", h1 >= 24 ? h1 / 24 + " ngày" : h1 + " giờ");
            sm.displayName(t.name());
            sm.lore(t.lore());
            head.setItemMeta(sm);
            h.inv.setItem(i, head);
        }
        ItemStack info = Tip.of("&e❖ &fTreo thưởng là gì?").desc("Bỏ xu ra treo thưởng lên đầu một người chơi", "Ai hạ được người đó thì nhận tiền")
                .line().kv("Lệnh", "&e/treothuong dat <người> <xu>").kv("Phí", cfg.getDouble("fee-percent", 5) + "% &8(không hoàn)")
                .kv("Hết hạn", cfg.getInt("days", 3) + " ngày &8(trả lại tiền)").deny("Không treo lên dân Liên minh Hoà Bình")
                .apply(new ItemStack(Material.BOOK));
        h.inv.setItem(49, info);
        if (list.isEmpty()) {
            p.sendMessage(c("&7Chưa có ai bị treo thưởng."));
        }
        MenuFrame.paint(h.inv, Material.RED_STAINED_GLASS_PANE);
        p.openInventory(h.inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    boolean command(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Chỉ dùng trong game.");
            return true;
        }
        if (!VaultEconomy.available()) {
            p.sendMessage(c("&cChưa có hệ thống tiền."));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("dat")) {
            place(p, args);
        } else if (args.length >= 1 && args[0].equalsIgnoreCase("giup")) {
            p.sendMessage(c("&4━━ TREO THƯỞNG ━━"));
            p.sendMessage(c("&e/treothuong &7xem ai đang bị treo · &e/treothuong dat <người> <xu> &7treo thưởng (phí "
                    + cfg.getDouble("fee-percent", 5) + "%)"));
            p.sendMessage(c("&7Ai hạ được người bị treo thì nhận hết. Giết liền " + cfg.getInt("streak.kills", 5)
                    + " mạng không chết: hệ thống tự treo thưởng lên bạn. Hết " + cfg.getInt("days", 3) + " ngày: trả lại tiền."));
        } else {
            open(p);
        }
        return true;
    }

    List<String> tab(String[] a) {
        if (a.length == 1) {
            return List.of("dat", "giup").stream().filter(x -> x.startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (a.length == 2 && a[0].equalsIgnoreCase("dat")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
