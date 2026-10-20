package vn.duckymomo.core.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gói quà (/kit, /kits, /goiqua): thay kit của Essentials. Server không bật module thì DuckyMoMoCore trả tên /kit cho plugin khác
 * lúc khởi động (Practice dùng /kit edit của Duels). Mỗi gói: tên, biểu tượng, thời gian chờ nhận lại (cho, giây; -1 = chỉ một lần,
 * 0 = không chờ), rank được nhận (nhóm LuckPerms; trống = mọi người) và quà dạng Rewards (vat / do / xu / lenh / manh…).
 * Gói "lan-dau" tự phát khi người chơi vào server LẦN ĐẦU (không cần rank, tính là đã nhận).
 * Ai nhận: so với NHÓM THẬT trong LuckPerms (không dùng hasPermission, vì admin có quyền * sẽ thành nhận mọi gói):
 *   nhóm Danh phận (default = Tị Nạn, dan, luongdan, haokiet): chỉ đúng bậc HIỆN TẠI (Hào Kiệt không nhận gói Dân);
 *   nhóm khác (media, rank ủng hộ, staff): người được gán trực tiếp nhóm đó.
 * Ô "gói quà" của từng rank trong Danh phận (play-ranks … kit) cũng cho rank đó nhận gói có mã tương ứng.
 * Admin: /kit <mã> <người> phát thẳng (bỏ qua rank, thời gian chờ), /kit lamlai <người> [mã] xoá lượt đã nhận.
 * Lưu: plugins/DuckyMoMoCore/goiqua.yml (players.<uuid>.<mã> = lúc nhận, mili giây).
 * Cài đặt: Người mới › Gói quà (config goi-qua).
 */
final class Kits implements Listener {

    record Kit(String id, String name, Material icon, long cooldown, List<String> ranks, List<String> rewards) { }

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    /** Tên hiện của nhóm LuckPerms (ghi "Cần rank …"); nhóm lạ thì hiện mã nhóm. */
    private static final Map<String, String> RANK_NAME = Map.ofEntries(Map.entry("default", "Dân Tị Nạn"), Map.entry("dan", "Dân"),
            Map.entry("luongdan", "Lương Dân"), Map.entry("haokiet", "Hào Kiệt"), Map.entry("media", "Sứ Giả"), Map.entry("namtuoc", "Nam Tước"),
            Map.entry("tutuoc", "Tử Tước"), Map.entry("batuoc", "Bá Tước"), Map.entry("hautuoc", "Hầu Tước"), Map.entry("congtuoc", "Công Tước"),
            Map.entry("builder", "Builder"), Map.entry("helper", "Helper"), Map.entry("mod", "Mod"), Map.entry("quanly", "Quản lý"),
            Map.entry("admin", "Admin"));

    private final JavaPlugin plugin;
    private final Rewards rewards;
    private final String firstJoin;
    private final Map<String, Kit> kits = new LinkedHashMap<>();
    private final File file;
    private final YamlConfiguration data;
    /** Nhóm của các bậc Danh phận theo thứ tự (đầu = default / Tị Nạn). */
    private final List<String> tiers = new ArrayList<>();
    /** mã gói -> nhóm được nhận do ô "gói quà" của rank (play-ranks tiers / other-ranks … kit). */
    private final Map<String, List<String>> rankKits = new java.util.HashMap<>();

    Kits(JavaPlugin plugin, ConfigurationSection cfg, Rewards rewards) {
        this.plugin = plugin;
        this.rewards = rewards;
        this.firstJoin = cfg.getString("lan-dau", "").trim().toLowerCase(Locale.ROOT);
        ConfigurationSection gs = cfg.getConfigurationSection("goi");
        if (gs != null) {
            for (String id : gs.getKeys(false)) {
                ConfigurationSection g = gs.getConfigurationSection(id);
                if (g == null) {
                    continue;
                }
                Material icon = Material.matchMaterial(g.getString("icon", "CHEST"));
                List<String> ranks = new ArrayList<>();
                for (String r : g.getStringList("rank")) {
                    if (!r.isBlank()) {
                        ranks.add(r.trim().toLowerCase(Locale.ROOT));
                    }
                }
                String key = id.toLowerCase(Locale.ROOT);
                kits.put(key, new Kit(key, g.getString("ten", id), icon == null || !icon.isItem() ? Material.CHEST : icon,
                        g.getLong("cho", -1), ranks, g.getStringList("qua")));
            }
        }
        ConfigurationSection pr = plugin.getConfig().getConfigurationSection("play-ranks");
        if (pr != null) {
            ConfigurationSection ts = pr.getConfigurationSection("tiers");
            if (ts != null) {
                int i = 0;
                for (String id : ts.getKeys(false)) {
                    String g = ts.getString(id + ".group", i == 0 ? "default" : id).toLowerCase(Locale.ROOT);
                    tiers.add(g);
                    linkKit(ts.getString(id + ".kit", ""), g);
                    i++;
                }
            }
            ConfigurationSection os = pr.getConfigurationSection("other-ranks");
            if (os != null) {
                for (String g : os.getKeys(false)) {
                    linkKit(os.getString(g + ".kit", ""), g.toLowerCase(Locale.ROOT));
                }
            }
        }
        if (tiers.isEmpty()) {
            tiers.add("default");
        }
        this.file = new File(plugin.getDataFolder(), "goiqua.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        Shared.FORGET.add(id -> Shared.forget(data, file, id));   // admin reset "như người mới"
    }

    String describe() {
        return kits.size() + " gói" + (firstJoin.isEmpty() ? "" : ", lần đầu: " + firstJoin);
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    // ---------------------------------------------------------------- điều kiện

    private void linkKit(String kit, String group) {
        String k = kit == null ? "" : kit.trim().toLowerCase(Locale.ROOT);
        if (!k.isEmpty()) {
            rankKits.computeIfAbsent(k, x -> new ArrayList<>()).add(group);
        }
    }

    /** Nhóm gán trực tiếp + mọi nhóm kế thừa của người chơi (LuckPerms). */
    private static java.util.Set<String>[] groups(Player p) {
        java.util.Set<String> direct = new java.util.HashSet<>(), all = new java.util.HashSet<>();
        try {
            net.luckperms.api.model.user.User u = net.luckperms.api.LuckPermsProvider.get().getUserManager().getUser(p.getUniqueId());
            if (u != null) {
                for (net.luckperms.api.node.types.InheritanceNode n : u.getNodes(net.luckperms.api.node.NodeType.INHERITANCE)) {
                    direct.add(n.getGroupName().toLowerCase(Locale.ROOT));
                }
                for (net.luckperms.api.model.group.Group g : u.getInheritedGroups(u.getQueryOptions())) {
                    all.add(g.getName().toLowerCase(Locale.ROOT));
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // không có LuckPerms: chỉ gói không giới hạn rank
        }
        all.addAll(direct);
        all.add("default");
        @SuppressWarnings("unchecked")
        java.util.Set<String>[] r = new java.util.Set[]{direct, all};
        return r;
    }

    /** Bậc Danh phận hiện tại: nhóm bậc cao nhất người chơi đang có (không có = bậc đầu, Tị Nạn). */
    private String tier(java.util.Set<String> all) {
        for (int i = tiers.size() - 1; i > 0; i--) {
            if (all.contains(tiers.get(i))) {
                return tiers.get(i);
            }
        }
        return tiers.get(0);
    }

    /** Rank được nhận của gói: chọn trên trang Cài đặt + rank có ô "gói quà" trỏ tới gói này. */
    private List<String> ranksOf(Kit k) {
        List<String> r = new ArrayList<>(k.ranks());
        for (String g : rankKits.getOrDefault(k.id(), List.of())) {
            if (!r.contains(g)) {
                r.add(g);
            }
        }
        return r;
    }

    /** Gói không giới hạn rank: ai cũng nhận; gói theo rank: đúng bậc Danh phận hiện tại hoặc được gán thẳng nhóm đó. */
    boolean allowed(Player p, Kit k) {
        List<String> ranks = ranksOf(k);
        if (ranks.isEmpty()) {
            return true;
        }
        java.util.Set<String>[] g = groups(p);
        String t = tier(g[1]);
        for (String r : ranks) {
            if (tiers.contains(r) ? r.equals(t) : g[0].contains(r)) {
                return true;
            }
        }
        return false;
    }

    private long last(Player p, Kit k) {
        return data.getLong("players." + p.getUniqueId() + "." + k.id(), 0);
    }

    /** Mili giây còn phải chờ: 0 = nhận được, -1 = gói một lần đã nhận. */
    long waitLeft(Player p, Kit k) {
        long t = last(p, k);
        if (t == 0) {
            return 0;
        }
        if (k.cooldown() < 0) {
            return -1;
        }
        return Math.max(0, t + k.cooldown() * 1000L - System.currentTimeMillis());
    }

    static String duration(long ms) {
        long m = Math.max(1, (ms + 59_999) / 60_000);
        long d = m / 1440, h = m % 1440 / 60, mm = m % 60;
        StringBuilder s = new StringBuilder();
        if (d > 0) {
            s.append(d).append(" ngày ");
        }
        if (h > 0) {
            s.append(h).append(" giờ ");
        }
        if (d == 0 && mm > 0) {
            s.append(mm).append(" phút");
        }
        return s.toString().trim();
    }

    private static String cooldownText(Kit k) {
        return k.cooldown() < 0 ? "Nhận một lần duy nhất" : k.cooldown() == 0 ? "Nhận bất cứ lúc nào" : "Nhận lại sau " + duration(k.cooldown() * 1000L);
    }

    private String rankText(Kit k) {
        List<String> n = new ArrayList<>();
        for (String r : ranksOf(k)) {
            n.add(RANK_NAME.getOrDefault(r, r));
        }
        return String.join(", ", n);
    }

    // ---------------------------------------------------------------- phát

    /** Phát gói, ghi lượt nhận. force: bỏ qua rank và thời gian chờ (admin, lần đầu vào). Trả thông báo lỗi hoặc null nếu phát được. */
    String give(Player p, Kit k, boolean force) {
        if (!force) {
            if (!allowed(p, k)) {
                return "&cGói &f" + k.name() + "&c chỉ dành cho: &f" + rankText(k) + "&c.";
            }
            long w = waitLeft(p, k);
            if (w < 0) {
                return "&7Bạn đã nhận &f" + k.name() + "&7 rồi (gói chỉ nhận một lần).";
            }
            if (w > 0) {
                return "&7Còn &f" + duration(w) + "&7 nữa mới nhận lại được &f" + k.name() + "&7.";
            }
        }
        data.set("players." + p.getUniqueId() + "." + k.id(), System.currentTimeMillis());
        data.set("players." + p.getUniqueId() + ".name", p.getName());
        save();
        List<String> got = rewards.give(p, k.rewards());
        p.sendMessage(c("&a🎁 Đã nhận &f" + k.name() + (got.isEmpty() ? "&a." : "&a: &f" + String.join("&7, &f", got))));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        plugin.getLogger().info("Gói quà: " + p.getName() + " nhận " + k.id() + (force ? " (admin/lần đầu)" : ""));
        return null;
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được goiqua.yml: " + e.getMessage());
        }
    }

    /** Lần đầu vào server: phát gói lan-dau (2 giây sau để túi đồ và các plugin khác xong việc). */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Kit k = kits.get(firstJoin);
        if (k == null || p.hasPlayedBefore() || last(p, k) != 0) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && last(p, k) == 0) {
                give(p, k, true);
                p.sendMessage(c("&7Gõ &e/kit&7 để xem các gói quà khác."));
            }
        }, 40L);
    }

    // ---------------------------------------------------------------- lệnh

    boolean command(CommandSender s, String[] a) {
        boolean admin = s.hasPermission("duckymomo.kit.admin");
        if (a.length >= 2 && admin && a[0].equalsIgnoreCase("lamlai")) {
            Player t = Bukkit.getPlayer(a[1]);
            if (t == null) {
                s.sendMessage("Người chơi không online.");
                return true;
            }
            if (a.length >= 3) {
                data.set("players." + t.getUniqueId() + "." + a[2].toLowerCase(Locale.ROOT), null);
            } else {
                data.set("players." + t.getUniqueId(), null);
            }
            save();
            s.sendMessage("Đã xoá lượt nhận gói quà của " + t.getName() + (a.length >= 3 ? " (" + a[2] + ")" : " (mọi gói)") + ".");
            return true;
        }
        if (a.length >= 2 && admin) {
            Kit k = kits.get(a[0].toLowerCase(Locale.ROOT));
            Player t = Bukkit.getPlayer(a[1]);
            if (k == null || t == null) {
                s.sendMessage(k == null ? "Không có gói \"" + a[0] + "\". Có: " + String.join(", ", kits.keySet()) : "Người chơi không online.");
                return true;
            }
            give(t, k, true);
            s.sendMessage("Đã phát " + k.id() + " cho " + t.getName() + ".");
            return true;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("/kit <mã> <người> phát gói · /kit lamlai <người> [mã] · gói: " + String.join(", ", kits.keySet()));
            return true;
        }
        if (a.length >= 1) {
            Kit k = kits.get(a[0].toLowerCase(Locale.ROOT));
            if (k == null) {
                p.sendMessage(c("&cKhông có gói \"" + a[0] + "\". Gõ &e/kit&c để xem các gói."));
                return true;
            }
            String err = give(p, k, false);
            if (err != null) {
                p.sendMessage(c(err));
            }
            return true;
        }
        open(p);
        return true;
    }

    List<String> tab(CommandSender s, String[] a) {
        List<String> o = new ArrayList<>();
        if (a.length == 1) {
            o.addAll(kits.keySet());
            if (s.hasPermission("duckymomo.kit.admin")) {
                o.add("lamlai");
            }
        } else if (a.length == 2 && s.hasPermission("duckymomo.kit.admin")) {
            Bukkit.getOnlinePlayers().forEach(p -> o.add(p.getName()));
        } else if (a.length == 3 && a[0].equalsIgnoreCase("lamlai")) {
            o.addAll(kits.keySet());
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        return o.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }

    // ---------------------------------------------------------------- menu

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        final List<String> slots = new ArrayList<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    void open(Player p) {
        Holder h = new Holder();
        int size = Math.max(9, Math.min(54, (kits.size() + 8) / 9 * 9));
        h.inv = Bukkit.createInventory(h, size, c("&0Gói quà &8(bấm để nhận)"));
        // gói mình nhận được lên trước, gói của rank khác xếp sau (xám, ghi rank cần có)
        List<Kit> order = new ArrayList<>(kits.values());
        order.sort(java.util.Comparator.comparing(k -> !allowed(p, k)));
        for (Kit k : order) {
            if (h.slots.size() >= size) {
                break;
            }
            ItemStack it = new ItemStack(k.icon());
            ItemMeta m = it.getItemMeta();
            boolean ok = allowed(p, k);
            long w = waitLeft(p, k);
            m.displayName(c((ok && w == 0 ? "&a" : "&7") + k.name()));
            List<Component> lore = new ArrayList<>();
            lore.add(c("&8" + cooldownText(k)));
            if (!ranksOf(k).isEmpty()) {
                lore.add(c("&8Dành cho: " + rankText(k)));
            }
            lore.add(Component.empty());
            for (String d : rewards.describe(k.rewards())) {
                lore.add(c("&7• &f" + d));
            }
            lore.add(Component.empty());
            if (!ok) {
                lore.add(c("&cCần rank: " + rankText(k)));
            } else if (w < 0) {
                lore.add(c("&7Đã nhận"));
            } else if (w > 0) {
                lore.add(c("&eNhận lại sau " + duration(w)));
            } else {
                lore.add(c("&a▶ Bấm để nhận"));
            }
            m.lore(lore);
            m.addItemFlags(ItemFlag.values());
            if (ok && w == 0) {
                m.setEnchantmentGlintOverride(true);
            }
            it.setItemMeta(m);
            h.inv.setItem(h.slots.size(), it);
            h.slots.add(k.id());
        }
        p.openInventory(h.inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h) || !(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= h.slots.size()) {
            return;
        }
        Kit k = kits.get(h.slots.get(slot));
        if (k == null) {
            return;
        }
        String err = give(p, k, false);
        if (err != null) {
            p.sendMessage(c(err));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
        }
        open(p);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }
}
