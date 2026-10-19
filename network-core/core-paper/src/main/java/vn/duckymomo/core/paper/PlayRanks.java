package vn.duckymomo.core.paper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.WeightNode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Danh phận (/danhphan, Bang Hội Chiến, 19/10): rank CHƠI miễn phí, kiếm bằng giờ chơi thật + Thăng Cấp + cốt truyện, BẤM NHẬN trong menu.
 * Mặc định 4 bậc: Dân Tị Nạn (nhóm default, mới vào) → Dân (6 giờ + xong chương 1) → Lương Dân (24 giờ + cấp 10) → Hào Kiệt (72 giờ + cấp 25).
 * Quyền lợi mỗi bậc: số nhà /sethome (Essentials sethome-multiple dmm<N>), số balo /balo (duckymomo.balo.<N>), số món treo chợ /ah
 * (zauctionhouse.max.<N>) — quyền đặt theo ngữ cảnh server (server: banghoi-1). Config play-ranks.tiers.<mã> (Cài đặt › Người mới › Danh phận):
 * group, name, prefix, priority, hours, level, perm (+perm-text), homes, balo, ah, icon. Lúc bật / dmm reload plugin TỰ ĐỒNG BỘ nhóm LuckPerms
 * (tạo nhóm, tiền tố, weight, các quyền quyền lợi; bỏ quyền quyền lợi cũ) nên sửa trên Cài đặt là đủ, không gõ lệnh lp.
 * Người chơi luôn giữ nhóm default; lên bậc = thêm nhóm bậc mới, bỏ nhóm bậc cũ (tiền tố bậc cao hơn hiện thay). Giữ khi /choilai.
 */
final class PlayRanks implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final List<String> MANAGED = List.of("essentials.sethome.multiple.dmm", "zauctionhouse.max.", "duckymomo.balo.");

    record Tier(String id, String group, String name, String prefix, int priority, double hours, int level, String perm, String permText,
                int homes, int balo, int ah, Material icon) { }

    private final DuckyMoMoCore plugin;
    private final Activity activity;
    private final List<Tier> tiers = new ArrayList<>();
    private final String server;
    private final boolean announce;

    PlayRanks(DuckyMoMoCore plugin, Activity activity, ConfigurationSection c) {
        this.plugin = plugin;
        this.activity = activity;
        this.server = c.getString("server", "banghoi-1");
        this.announce = c.getBoolean("announce", true);
        ConfigurationSection ts = c.getConfigurationSection("tiers");
        if (ts != null) {
            int i = 0;
            for (String id : ts.getKeys(false)) {
                ConfigurationSection t = ts.getConfigurationSection(id);
                if (t == null) {
                    continue;
                }
                Material icon = Material.matchMaterial(t.getString("icon", "PAPER"));
                tiers.add(new Tier(id, t.getString("group", i == 0 ? "default" : id).toLowerCase(Locale.ROOT), t.getString("name", id),
                        t.getString("prefix", ""), t.getInt("priority", i), t.getDouble("hours", 0), t.getInt("level", 0),
                        t.getString("perm", ""), t.getString("perm-text", "Mở khoá"), Math.max(1, t.getInt("homes", 1)),
                        Math.max(0, t.getInt("balo", 0)), Math.max(1, t.getInt("ah", 1)), icon == null ? Material.PAPER : icon));
                i++;
            }
        }
    }

    String describe() {
        StringBuilder s = new StringBuilder();
        tiers.forEach(t -> s.append(s.length() > 0 ? " → " : "").append(t.name()));
        return s.toString();
    }

    // ---------------------------------------------------------------- đồng bộ LuckPerms

    /** Tạo/sửa nhóm của từng bậc theo config: tiền tố, weight, quyền nhà / balo / chợ (ngữ cảnh server). */
    void sync() {
        LuckPerms lp;
        try {
            lp = LuckPermsProvider.get();
        } catch (IllegalStateException e) {
            plugin.getLogger().warning("Danh phận: chưa có LuckPerms, không đồng bộ nhóm");
            return;
        }
        ImmutableContextSet ctx = ImmutableContextSet.of("server", server);
        for (Tier t : tiers) {
            lp.getGroupManager().createAndLoadGroup(t.group()).thenAccept(g -> {
                g.data().clear(n -> managed(n));
                if (!t.prefix().isBlank()) {
                    g.data().add(PrefixNode.builder(t.prefix(), t.priority()).build());
                }
                if (!t.group().equals("default")) {
                    g.data().add(WeightNode.builder(t.priority()).build());
                }
                g.data().add(PermissionNode.builder("essentials.sethome.multiple.dmm" + t.homes()).context(ctx).build());
                g.data().add(PermissionNode.builder("zauctionhouse.max." + t.ah()).context(ctx).build());
                if (t.balo() > 0) {
                    g.data().add(PermissionNode.builder("duckymomo.balo." + t.balo()).context(ctx).build());
                }
                lp.getGroupManager().saveGroup(g);
            });
        }
        plugin.getLogger().info("Danh phận: đã đồng bộ " + tiers.size() + " nhóm LuckPerms (" + describe() + ")");
    }

    private static boolean managed(Node n) {
        if (n.getType() == NodeType.PREFIX || n.getType() == NodeType.WEIGHT) {
            return true;
        }
        return n.getType() == NodeType.PERMISSION && MANAGED.stream().anyMatch(m -> n.getKey().startsWith(m));
    }

    // ---------------------------------------------------------------- bậc của người chơi

    /** Chỉ số bậc hiện tại (0 = bậc đầu, nhóm default). */
    int current(Player p) {
        for (int i = tiers.size() - 1; i > 0; i--) {
            if (p.hasPermission("group." + tiers.get(i).group())) {
                return i;
            }
        }
        return 0;
    }

    String tierName(Player p) {
        return tiers.isEmpty() ? "" : tiers.get(current(p)).name();
    }

    private double hours(Player p) {
        return activity == null ? 0 : activity.totalHours(p.getUniqueId());
    }

    private static int level(Player p) {
        try {
            return LevelUp.levelOf(p.getUniqueId());
        } catch (RuntimeException | NoClassDefFoundError e) {
            return 0;
        }
    }

    private boolean meets(Player p, Tier t) {
        return hours(p) >= t.hours() && level(p) >= t.level() && (t.perm().isBlank() || p.hasPermission(t.perm()));
    }

    private void promote(Player p, Tier to) {
        LuckPerms lp = LuckPermsProvider.get();
        UUID u = p.getUniqueId();
        lp.getUserManager().modifyUser(u, user -> {
            for (Tier t : tiers) {
                if (!t.group().equals("default")) {
                    user.data().remove(InheritanceNode.builder(t.group()).build());
                }
            }
            if (!to.group().equals("default")) {
                user.data().add(InheritanceNode.builder(to.group()).build());
            }
        });
    }

    // ---------------------------------------------------------------- menu

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        final Map<Integer, Integer> slots = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    void open(Player p) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 27, c("&6&lDANH PHẬN &8(rank chơi, miễn phí)"));
        ItemStack bg = pane();
        for (int s = 0; s < 27; s++) {
            h.inv.setItem(s, bg);
        }
        int cur = current(p);
        int[] at = tiers.size() <= 4 ? new int[] {10, 12, 14, 16} : new int[] {9, 10, 11, 12, 13, 14, 15, 16, 17};
        for (int i = 0; i < tiers.size() && i < at.length; i++) {
            h.inv.setItem(at[i], icon(p, i, cur));
            h.slots.put(at[i], i);
        }
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta m = info.getItemMeta();
        m.displayName(c("&6Bạn đang là: &f" + tiers.get(cur).name()));
        m.lore(List.of(c("&7Giờ chơi thật: &f" + String.format(Locale.ROOT, "%.1f", hours(p)) + " giờ &8(/hoatdong)"),
                c("&7Thăng Cấp: &f" + level(p) + " &8(/capdo)"), c("&8Rank chơi kiếm bằng cách chơi, không bán.")));
        info.setItemMeta(m);
        h.inv.setItem(4, info);
        p.openInventory(h.inv);
    }

    private ItemStack icon(Player p, int i, int cur) {
        Tier t = tiers.get(i);
        ItemStack it = new ItemStack(t.icon());
        ItemMeta m = it.getItemMeta();
        m.displayName(c("&f&lRANK " + t.name().toUpperCase(Locale.ROOT)));
        List<Component> lore = new ArrayList<>();
        lore.add(c(t.prefix().isBlank() ? "&8(không tiền tố)" : "&7Tiền tố: " + t.prefix()));
        lore.add(Component.empty());
        lore.add(c("&e&lQUYỀN LỢI:"));
        lore.add(c("&7• Đặt &a" + t.homes() + "&7 nhà &d(/sethome)"));
        lore.add(c("&7• " + (t.balo() > 0 ? "Có &a" + t.balo() + "&7 balo cá nhân &d(/balo)" : "Chưa có balo &d(/balo)")));
        lore.add(c("&7• &a" + t.ah() + "&7 món treo chợ &d(/ah)"));
        lore.add(Component.empty());
        if (i > 0) {
            lore.add(c("&7Giá nâng cấp: &aMiễn phí"));
            lore.add(c("&e&lYÊU CẦU:"));
            if (t.hours() > 0) {
                boolean ok = hours(p) >= t.hours();
                lore.add(c((ok ? "&a✔" : "&c✘") + " &7Chơi thật " + fmt(t.hours()) + " giờ &8(" + String.format(Locale.ROOT, "%.1f", hours(p)) + ")"));
            }
            if (t.level() > 0) {
                boolean ok = level(p) >= t.level();
                lore.add(c((ok ? "&a✔" : "&c✘") + " &7Thăng Cấp " + t.level() + " &8(đang " + level(p) + ")"));
            }
            if (!t.perm().isBlank()) {
                lore.add(c((p.hasPermission(t.perm()) ? "&a✔ &7" : "&c✘ &7") + t.permText()));
            }
            lore.add(Component.empty());
        }
        if (i == cur) {
            lore.add(c("&a★ Đây là danh phận hiện tại của bạn"));
            m.setEnchantmentGlintOverride(true);
        } else if (i < cur) {
            lore.add(c("&8Đã vượt qua"));
        } else if (i == cur + 1) {
            lore.add(meets(p, t) ? c("&e▶ Bấm để nhận rank!") : c("&c✘ Chưa đủ yêu cầu"));
        } else {
            lore.add(c("&8Cần nhận bậc trước"));
        }
        m.lore(lore);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(m);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) {
            return;
        }
        Integer i = h.slots.get(e.getRawSlot());
        if (i == null) {
            return;
        }
        int cur = current(p);
        Tier t = tiers.get(i);
        if (i != cur + 1) {
            p.sendMessage(c(i <= cur ? "&7Bạn đã có danh phận này." : "&cNhận bậc trước đã."));
            return;
        }
        if (!meets(p, t)) {
            p.sendMessage(c("&cChưa đủ yêu cầu để nhận &f" + t.name() + "&c. Xem trong menu."));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            return;
        }
        promote(p, t);
        p.closeInventory();
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1f);
        p.sendMessage(c("&a&l✔ Bạn đã trở thành &f" + t.name() + "&a&l! &7Nhà: " + t.homes() + " · Balo: " + t.balo() + " · Chợ: " + t.ah() + " món."));
        if (announce) {
            Bukkit.broadcast(c("&6✦ &f" + p.getName() + " &7vừa trở thành ").append(c(t.prefix().isBlank() ? "&f" + t.name() : t.prefix().trim())).append(c("&7!")));
        }
        plugin.getLogger().info("Danh phận: " + p.getName() + " → " + t.id());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------- lệnh

    boolean command(CommandSender s, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("dongbo") && s.hasPermission("duckymomo.admin")) {
            sync();
            s.sendMessage("Đã đồng bộ nhóm LuckPerms của danh phận.");
            return true;
        }
        if (sub.equals("dat") && s.hasPermission("duckymomo.admin")) {
            Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
            Tier to = args.length > 2 ? tiers.stream().filter(x -> x.id().equalsIgnoreCase(args[2])).findFirst().orElse(null) : null;
            if (t == null || to == null) {
                List<String> ids = tiers.stream().map(Tier::id).toList();
                s.sendMessage("/danhphan dat <người đang online> <" + String.join("|", ids) + ">");
                return true;
            }
            promote(t, to);
            s.sendMessage("Đã đặt " + t.getName() + " → " + to.name());
            return true;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("/danhphan dongbo · /danhphan dat <người> <mã bậc>");
            return true;
        }
        if (tiers.isEmpty()) {
            p.sendMessage(c("&cChưa cấu hình danh phận."));
            return true;
        }
        open(p);
        return true;
    }

    List<String> tab(CommandSender s, String[] args) {
        if (!s.hasPermission("duckymomo.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("dat", "dongbo");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("dat")) {
            return tiers.stream().map(Tier::id).toList();
        }
        return null;
    }

    private static ItemStack pane() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(" "));
        m.setHideTooltip(true);
        it.setItemMeta(m);
        return it;
    }

    private static String fmt(double h) {
        return h == Math.floor(h) ? String.valueOf((long) h) : String.valueOf(h);
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
}
