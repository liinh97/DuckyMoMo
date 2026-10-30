package vn.duckymomo.core.paper;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.data.TemporaryNodeMergeStrategy;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Rank donate (tước vị) + thông báo vào/ra server.
 * - Bậc (thấp -> cao) và giá lấy từ config donate-ranks; nhóm LuckPerms tạo bằng tools/donate-ranks.cmds
 *   (bậc cao kế thừa bậc thấp, track "donate"). CHỈ quyền trang trí (luật Mojang: không bán lợi thế khi chơi).
 * - /rank: người chơi xem các bậc, quyền lợi, giá, rank của mình và ngày hết hạn.
 * - /rank give <tên> <bậc> <tháng>: admin cấp rank có thời hạn (1 tháng = 30 ngày), gia hạn thì cộng dồn;
 *   đổi sang bậc khác thì bỏ bậc cũ (báo admin số ngày còn lại của bậc cũ để tự bù). /rank remove, /rank check.
 * - Thông báo khi vào/ra: mọi người đều có dòng vào/ra kèm prefix; có rank (duckymomo.rank.join) thì thêm dòng
 *   chào; bậc cao nhất (duckymomo.rank.join.special) thêm tiếng chuông cho cả server.
 */
final class DonateRanks implements Listener {

    record Tier(String group, String name, String color, List<String> perks, List<String> prices) {
    }

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('&').hexColors().build();   // 30/10: tiền tố rank ủng hộ màu hex &#RRGGBB
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final JavaPlugin plugin;
    private final List<Tier> tiers = new ArrayList<>();
    private DonateQr qr;

    List<Tier> tiers() {
        return tiers;
    }

    /** Ủng hộ bằng QR trong game (/rank mua). */
    void setQr(DonateQr qr) {
        this.qr = qr;
    }

    private final String howToBuy;
    private final String joinMsg;
    private final String quitMsg;
    private final String rankJoinMsg;
    /** Skin độc quyền theo bậc (tên custom skin của SkinsRestorer, tạo bằng /sr createcustom <tên> <link>). */
    private final java.util.Map<String, List<String>> skins = new java.util.LinkedHashMap<>();
    private final boolean skinSync;

    DonateRanks(JavaPlugin plugin, ConfigurationSection c) {
        this.plugin = plugin;
        for (var m : c.getMapList("tiers")) {
            tiers.add(new Tier(String.valueOf(m.get("group")), String.valueOf(m.get("name")), String.valueOf(m.get("color")),
                    asList(m.get("perks")), asList(m.get("prices"))));
            skins.put(String.valueOf(m.get("group")), asList(m.get("skins")).stream()
                    .map(x -> x.trim().toLowerCase(java.util.Locale.ROOT)).filter(x -> !x.isEmpty()).toList());
        }
        this.skinSync = c.getBoolean("skin-sync", false);
        this.howToBuy = c.getString("how-to-buy", "&7Liên hệ admin để mua rank.");
        this.joinMsg = c.getString("join-message", "&7[&a+&7] {prefix}&f{player}");
        this.quitMsg = c.getString("quit-message", "&7[&c-&7] {prefix}&f{player}");
        this.rankJoinMsg = c.getString("rank-join-message", "&e✦ {tiercolor}{tier} &f{player} &evừa giá lâm!");
    }

    private static List<String> asList(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object x : l) {
                out.add(String.valueOf(x));
            }
        }
        return out;
    }

    private static Component c(String s) {
        return Tip.ns(LEGACY.deserialize(s));
    }

    /**
     * Đồng bộ quyền skin độc quyền vào LuckPerms (chỉ chạy ở server có donate-ranks.skin-sync: true, thường là sảnh; LuckPerms
     * dùng chung MariaDB nên proxy, nơi SkinsRestorer kiểm quyền, cũng thấy). default: skinsrestorer.skin.* + ownskin = mọi skin
     * thường vẫn miễn phí; mỗi skin độc quyền: default false, bậc được gán true (bậc cao kế thừa bậc thấp). Skin bỏ khỏi danh sách
     * thì gỡ quyền (nhớ ở rank-skins.yml).
     */
    void syncSkins(java.io.File dataFolder) {
        if (!skinSync) {
            return;
        }
        var gm = lp().getGroupManager();
        java.io.File f = new java.io.File(dataFolder, "rank-skins.yml");
        var old = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
        java.util.Set<String> all = new java.util.LinkedHashSet<>();
        skins.values().forEach(all::addAll);
        java.util.function.BiConsumer<String, java.util.function.Consumer<net.luckperms.api.model.group.Group>> edit = (g, fn) ->
                gm.loadGroup(g).thenAccept(o -> o.ifPresent(grp -> {
                    fn.accept(grp);
                    gm.saveGroup(grp);
                }));
        java.util.function.BiConsumer<net.luckperms.api.model.group.Group, String> unset = (grp, perm) -> {
            grp.data().remove(net.luckperms.api.node.types.PermissionNode.builder(perm).value(true).build());
            grp.data().remove(net.luckperms.api.node.types.PermissionNode.builder(perm).value(false).build());
        };
        edit.accept("default", grp -> {
            grp.data().add(net.luckperms.api.node.types.PermissionNode.builder("skinsrestorer.skin.*").value(true).build());
            grp.data().add(net.luckperms.api.node.types.PermissionNode.builder("skinsrestorer.ownskin").value(true).build());
            for (String sk : old.getStringList("tat-ca")) {
                if (!all.contains(sk)) {
                    unset.accept(grp, "skinsrestorer.skin." + sk);
                }
            }
            for (String sk : all) {
                unset.accept(grp, "skinsrestorer.skin." + sk);
                grp.data().add(net.luckperms.api.node.types.PermissionNode.builder("skinsrestorer.skin." + sk).value(false).build());
            }
        });
        for (Tier t : tiers) {
            List<String> want = skins.getOrDefault(t.group(), List.of());
            List<String> had = old.getStringList("bac." + t.group());
            edit.accept(t.group(), grp -> {
                for (String sk : had) {
                    if (!want.contains(sk)) {
                        unset.accept(grp, "skinsrestorer.skin." + sk);
                    }
                }
                for (String sk : want) {
                    grp.data().add(net.luckperms.api.node.types.PermissionNode.builder("skinsrestorer.skin." + sk).value(true).build());
                }
            });
            old.set("bac." + t.group(), want);
        }
        old.set("tat-ca", new ArrayList<>(all));
        try {
            old.save(f);
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Không lưu được rank-skins.yml: " + e.getMessage());
        }
        plugin.getLogger().info("Skin theo rank: " + all.size() + " skin độc quyền đã đồng bộ vào LuckPerms");
    }

    /** Skin độc quyền của bậc, đọc thẳng từ LuckPerms (nguồn thật; thêm bằng /rank skin them hoặc Cài đặt). */
    private List<String> tierSkins(String group) {
        var g = lp().getGroupManager().getGroup(group);
        if (g == null) {
            return skins.getOrDefault(group, List.of());
        }
        return g.getNodes(net.luckperms.api.node.NodeType.PERMISSION).stream()
                .filter(n -> n.getValue() && n.getPermission().startsWith("skinsrestorer.skin.") && !n.getPermission().endsWith(".*"))
                .map(n -> n.getPermission().substring("skinsrestorer.skin.".length())).sorted().toList();
    }

    private Tier findTier(String q) {
        String k = java.text.Normalizer.normalize(q, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace("đ", "d").replace("Đ", "d")
                .replace(" ", "").toLowerCase(java.util.Locale.ROOT);
        for (Tier t : tiers) {
            String n = java.text.Normalizer.normalize(t.name(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace("đ", "d").replace("Đ", "d")
                    .replace(" ", "").toLowerCase(java.util.Locale.ROOT);
            if (t.group().equalsIgnoreCase(k) || n.equals(k)) {
                return t;
            }
        }
        return null;
    }

    private static net.luckperms.api.node.types.PermissionNode perm(String p, boolean v) {
        return net.luckperms.api.node.types.PermissionNode.builder(p).value(v).build();
    }

    /**
     * /rank skin                          xem skin độc quyền từng bậc
     * /rank skin them <bậc> <tên> <link>  khoá skin cho bậc (LuckPerms ngay, không cần khởi động lại) + nút tạo skin (/sr createcustom chạy ở proxy)
     * /rank skin xoa <tên>                mở khoá lại cho mọi người (skin vẫn còn trong /skins)
     */
    private void skinCommand(CommandSender s, String[] a, boolean admin) {
        String sub = a.length > 1 ? a[1].toLowerCase(java.util.Locale.ROOT) : "";
        if (sub.isEmpty() || !admin) {
            s.sendMessage(c("&6Skin độc quyền theo rank &7(menu /skins; bậc cao có luôn skin bậc dưới)"));
            for (Tier t : tiers) {
                List<String> sk = tierSkins(t.group());
                s.sendMessage(c("  " + t.color() + t.name() + "&7: " + (sk.isEmpty() ? "&7chưa có" : "&f" + String.join(", ", sk))));
            }
            if (admin) {
                s.sendMessage(c("&7Thêm: &f/rank skin them <bậc> <tên> <link ảnh .png> &7· Bỏ khoá: &f/rank skin xoa <tên>"));
            }
            return;
        }
        var gm = lp().getGroupManager();
        if (sub.equals("them")) {
            if (a.length < 5) {
                s.sendMessage(c("&eDùng: /rank skin them <bậc> <tên> <link ảnh .png>  &7(vd /rank skin them congtuoc tuongquan https://…/skin.png)"));
                return;
            }
            Tier t = findTier(a[2]);
            int i = 3;
            if (t == null && a.length >= 6) {   // tên bậc 2 từ: "Công Tước"
                t = findTier(a[2] + a[3]);
                i = 4;
            }
            String name = a[i].toLowerCase(java.util.Locale.ROOT);
            String url = a[i + 1];
            if (t == null) {
                s.sendMessage(c("&cKhông có bậc \"" + a[2] + "\". Bậc: " + String.join(", ", tiers.stream().map(Tier::group).toList())));
                return;
            }
            if (!name.matches("[a-z0-9_-]{2,32}")) {
                s.sendMessage(c("&cTên skin chỉ gồm chữ thường không dấu, số, _ hoặc - (2–32 ký tự)."));
                return;
            }
            if (!url.matches("https?://\\S+")) {
                s.sendMessage(c("&cLink ảnh phải bắt đầu bằng http:// hoặc https://"));
                return;
            }
            String node = "skinsrestorer.skin." + name;
            final Tier tt = t;
            for (Tier o : tiers) {   // một skin chỉ thuộc một bậc
                gm.modifyGroup(o.group(), g -> {
                    g.data().remove(perm(node, true));
                    g.data().remove(perm(node, false));
                    if (o == tt) {
                        g.data().add(perm(node, true));
                    }
                });
            }
            gm.modifyGroup("default", g -> {
                g.data().remove(perm(node, true));
                g.data().add(perm(node, false));
            });
            s.sendMessage(c("&a✔ Đã khoá skin &f" + name + " &acho " + t.color() + t.name() + " &a(và bậc cao hơn)."));
            s.sendMessage(c("&eBước cuối: ").append(c("&a[Bấm để tạo skin]")
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/sr createcustom " + name + " " + url))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(c("&7Chạy /sr createcustom " + name + " " + url
                            + "\n&7(lệnh của proxy nên phải bấm từ trong game)")))));
            if (!(s instanceof Player)) {
                s.sendMessage(c("&7(Đang gõ từ console: vào game gõ &f/sr createcustom " + name + " " + url + "&7)"));
            }
            return;
        }
        if (sub.equals("xoa")) {
            if (a.length < 3) {
                s.sendMessage(c("&eDùng: /rank skin xoa <tên>"));
                return;
            }
            String node = "skinsrestorer.skin." + a[2].toLowerCase(java.util.Locale.ROOT);
            for (Tier o : tiers) {
                gm.modifyGroup(o.group(), g -> {
                    g.data().remove(perm(node, true));
                    g.data().remove(perm(node, false));
                });
            }
            gm.modifyGroup("default", g -> {
                g.data().remove(perm(node, true));
                g.data().remove(perm(node, false));
            });
            s.sendMessage(c("&a✔ Đã bỏ khoá skin &f" + a[2] + "&a: ai cũng dùng được (skin vẫn nằm trong /skins)."));
            return;
        }
        s.sendMessage(c("&e/rank skin &7· &e/rank skin them <bậc> <tên> <link> &7· &e/rank skin xoa <tên>"));
    }

    private static LuckPerms lp() {
        return LuckPermsProvider.get();
    }

    private Tier tierOf(User u) {
        Tier best = null;
        for (Tier t : tiers) {
            if (u.getInheritedGroups(u.getQueryOptions()).stream().anyMatch(g -> g.getName().equalsIgnoreCase(t.group()))) {
                best = t;
            }
        }
        return best;
    }

    private String prefixOf(Player p) {
        User u = lp().getUserManager().getUser(p.getUniqueId());
        String pre = u == null ? null : u.getCachedData().getMetaData().getPrefix();
        return pre == null ? "" : pre;
    }

    // ---------------------------------------------------------------- vào / ra

    /** 27/10: vào / ra không báo (bot trang quản trị › Bot: botfarm cấp quyền này, khỏi cả loạt dòng [+] [-] khi dịch vụ khởi động lại). */
    static final String SILENT = "duckymomo.vaora.an";

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission(SILENT)) {
            e.joinMessage(null);
            return;
        }
        e.joinMessage(c(joinMsg.replace("{prefix}", prefixOf(p)).replace("{player}", p.getName())));
        if (!p.hasPermission("duckymomo.rank.join")) {
            return;
        }
        User u = lp().getUserManager().getUser(p.getUniqueId());
        Tier t = u == null ? null : tierOf(u);
        if (t == null) {
            return;
        }
        PlayerSettings.broadcast("vaora", c(rankJoinMsg.replace("{tiercolor}", t.color()).replace("{tier}", t.name()).replace("{player}", p.getName())));
        if (p.hasPermission("duckymomo.rank.join.special")) {
            for (Player o : Bukkit.getOnlinePlayers()) {
                o.playSound(o.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent e) {
        if (e.getPlayer().hasPermission(SILENT)) {
            e.quitMessage(null);
            return;
        }
        e.quitMessage(c(quitMsg.replace("{prefix}", prefixOf(e.getPlayer())).replace("{player}", e.getPlayer().getName())));
    }

    // ---------------------------------------------------------------- lệnh

    boolean command(CommandSender s, String[] a) {
        boolean admin = s.hasPermission("duckymomo.rank.admin");
        if (a.length == 0) {
            showInfo(s);
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "mua", "qr", "nap" -> {
                if (qr != null && s instanceof Player p) {
                    qr.open(p);
                } else {
                    showInfo(s);
                }
            }
            case "dachuyen" -> {
                if (qr != null && s instanceof Player p) {
                    qr.reported(p, a.length > 1 ? a[1] : null);
                }
            }
            case "huyqr" -> {
                if (qr != null && s instanceof Player p) {
                    qr.cancel(p);
                }
            }
            case "skin" -> skinCommand(s, a, admin);
            case "give" -> {
                if (!admin || a.length < 4) {
                    s.sendMessage(c(admin ? "&cDùng: /rank give <tên> <bậc> <số tháng: 1|3|6|12>" : "&cBạn không có quyền."));
                    return true;
                }
                give(s, a[1], a[2], a[3]);
            }
            case "remove" -> {
                if (!admin || a.length < 2) {
                    s.sendMessage(c(admin ? "&cDùng: /rank remove <tên>" : "&cBạn không có quyền."));
                    return true;
                }
                withUser(s, a[1], u -> {
                    int n = clearTiers(u);
                    s.sendMessage(c("&aĐã gỡ rank donate của &f" + u.getUsername() + " &7(" + n + " mục)."));
                });
            }
            case "check" -> {
                if (!admin || a.length < 2) {
                    s.sendMessage(c(admin ? "&cDùng: /rank check <tên>" : "&cBạn không có quyền."));
                    return true;
                }
                withUser(s, a[1], u -> s.sendMessage(c("&f" + u.getUsername() + "&7: " + describe(u))));
            }
            default -> showInfo(s);
        }
        return true;
    }

    /** Biểu tượng từng bậc (thấp -> cao). */
    private static final org.bukkit.Material[] TIER_ICON = {org.bukkit.Material.COPPER_INGOT, org.bukkit.Material.IRON_INGOT,
            org.bukkit.Material.GOLD_INGOT, org.bukkit.Material.DIAMOND, org.bukkit.Material.NETHERITE_INGOT};

    /** /rank cho người chơi: menu (22/10, trước in ~15 dòng chat khó đọc). Console vẫn in chữ. */
    private void showMenu(Player p) {
        User u = lp().getUserManager().getUser(p.getUniqueId());
        Tier mine = u == null ? null : tierOf(u);
        boolean shop = qr != null && qr.ready();
        InfoMenu m = InfoMenu.of("&6✦ Rank ủng hộ").accent(org.bukkit.Material.ORANGE_STAINED_GLASS_PANE);
        Tip top = Tip.of("&6✦ &fRank ủng hộ").sub("tước vị").accent("&6")
                .desc("Ủng hộ server để nhận tước vị và quyền lợi.", "Bậc cao có đủ quyền lợi của các bậc thấp hơn.").line()
                .kv("Rank của bạn", u == null ? "&7chưa có" : describe(u));
        if (shop) {
            top.kv("Cách mua", "&6Vàng").note("Nạp tiền ủng hộ được Vàng, rồi chọn rank trong cửa hàng Vàng.");
        } else {
            top.raw(howToBuy);
        }
        m.top(top.apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.NETHER_STAR)));
        for (int i = 0; i < tiers.size() && i < 7; i++) {
            Tier t = tiers.get(i);
            boolean have = mine != null && mine.group().equalsIgnoreCase(t.group());
            Tip tip = Tip.of(t.color() + "♛ " + t.name()).sub("bậc " + (i + 1)).accent(t.color())
                    .desc(i == 0 ? "Bậc khởi đầu" : "Có đủ quyền lợi của " + tiers.get(i - 1).name() + " và:").gap()
                    .head("Quyền lợi").items(t.perks());
            List<String> sk = tierSkins(t.group());
            if (!sk.isEmpty()) {
                tip.gap().head("Skin độc quyền (/skins)").items(sk);
            }
            if (!t.prices().isEmpty()) {
                tip.gap().head("Giá (quy ra Vàng)");
                t.prices().forEach(pr -> tip.raw("  &7▸ &e" + pr));
            }
            tip.line();
            if (have) {
                tip.check(true, "Bạn đang có: " + describe(u).replaceAll("^&.[^&]*", "").trim());
            }
            if (shop) {
                tip.click(have ? "Bấm để gia hạn bằng Vàng" : "Bấm để mua bằng Vàng");
            }
            org.bukkit.inventory.ItemStack icon = tip.apply(new org.bukkit.inventory.ItemStack(TIER_ICON[Math.min(i, TIER_ICON.length - 1)]));
            if (have) {
                icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            }
            m.add(icon, shop ? pl -> { pl.closeInventory(); pl.performCommand("vang"); } : null);
        }
        if (shop) {
            m.bar(Tip.of("&6⛃ &fCửa hàng Vàng").desc("Mua rank, đồ trang trí, đổi tên màu bằng Vàng").line()
                    .click("Bấm để mở").apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.GOLD_BLOCK)),
                    pl -> { pl.closeInventory(); pl.performCommand("vang"); });
            m.bar(Tip.of("&a✚ &fNạp Vàng").sub("mã QR").desc("Chọn số tiền, mã QR chuyển khoản hiện ngay trên tay").line()
                    .note("Admin duyệt xong là Vàng vào ví").click("Bấm để chọn số tiền").apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.MAP)),
                    pl -> { pl.closeInventory(); pl.performCommand("rank mua"); });
        }
        m.open(p);
    }

    private void showInfo(CommandSender s) {
        if (s instanceof Player p) {
            showMenu(p);
            return;
        }
        s.sendMessage(c("&6✦ RANK DONATE (tước vị) ✦"));
        for (Tier t : tiers) {
            s.sendMessage(c(t.color() + t.name() + "&7: " + String.join("&7, ", t.perks().stream().map(x -> "&f" + x).toList())));
            List<String> sk = tierSkins(t.group());
            if (!sk.isEmpty()) {
                s.sendMessage(c("   &7Skin độc quyền (/skins): &f" + String.join(", ", sk) + " &7(+ skin của bậc dưới)"));
            }
            if (!t.prices().isEmpty()) {
                s.sendMessage(c("   &7Giá (quy ra Vàng): &e" + String.join("&7 | &e", t.prices())));
            }
        }
        s.sendMessage(c("&7Bậc cao có đủ quyền của các bậc thấp hơn."));
        if (qr != null && qr.ready() && s instanceof Player) {
            s.sendMessage(c("&7Rank mua bằng &6Vàng&7: nạp tiền ủng hộ được Vàng, rồi tự chọn rank trong cửa hàng."));
            s.sendMessage(c("&6[Mở cửa hàng Vàng]").clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/vang"))
                    .append(c("  ")).append(c("&a[Nạp Vàng bằng mã QR]").clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/rank mua"))));
        } else {
            s.sendMessage(c(howToBuy));
        }
        if (s instanceof Player p) {
            User u = lp().getUserManager().getUser(p.getUniqueId());
            if (u != null) {
                s.sendMessage(c("&7Rank của bạn: " + describe(u)));
            }
        }
    }

    /** "Bá Tước (hết hạn 12/11/2026)" hoặc "chưa có". */
    private String describe(User u) {
        Tier t = tierOf(u);
        if (t == null) {
            return "&7chưa có";
        }
        for (InheritanceNode n : u.getNodes(net.luckperms.api.node.NodeType.INHERITANCE)) {
            if (n.getGroupName().equalsIgnoreCase(t.group())) {
                return t.color() + t.name() + (n.hasExpiry()
                        ? " &7(hết hạn &f" + DATE.format(n.getExpiry()) + "&7, còn &f" + daysLeft(n.getExpiry()) + " &7ngày)"
                        : " &7(vĩnh viễn)");
            }
        }
        return t.color() + t.name();
    }

    private static long daysLeft(Instant exp) {
        return Math.max(0, Duration.between(Instant.now(), exp).toDays());
    }

    private void give(CommandSender s, String name, String tierArg, String monthsArg) {
        Tier tier = tiers.stream().filter(t -> t.group().equalsIgnoreCase(tierArg)
                || fold(t.name()).replace(" ", "").equalsIgnoreCase(fold(tierArg).replace(" ", ""))).findFirst().orElse(null);
        if (tier == null) {
            s.sendMessage(c("&cKhông có bậc '" + tierArg + "'. Có: " + String.join(", ", tiers.stream().map(Tier::group).toList())));
            return;
        }
        int months;
        try {
            months = Integer.parseInt(monthsArg);
        } catch (NumberFormatException ex) {
            months = 0;
        }
        if (months < 1 || months > 24) {
            s.sendMessage(c("&cSố tháng phải từ 1 đến 24 (gói thường dùng: 1, 3, 6, 12)."));
            return;
        }
        int m = months;
        withUser(s, name, u -> {
            // bỏ các bậc khác (giữ bậc đang cấp để cộng dồn thời gian)
            StringBuilder lost = new StringBuilder();
            for (InheritanceNode n : new ArrayList<>(u.getNodes(net.luckperms.api.node.NodeType.INHERITANCE))) {
                boolean donate = tiers.stream().anyMatch(t -> t.group().equalsIgnoreCase(n.getGroupName()));
                if (donate && !n.getGroupName().equalsIgnoreCase(tier.group())) {
                    lost.append(n.getGroupName()).append(n.hasExpiry() ? " còn " + daysLeft(n.getExpiry()) + " ngày" : " vĩnh viễn").append("; ");
                    u.data().remove(n);
                }
            }
            InheritanceNode node = InheritanceNode.builder(tier.group()).expiry(30L * m, TimeUnit.DAYS).build();
            u.data().add(node, TemporaryNodeMergeStrategy.ADD_NEW_DURATION_TO_EXISTING);
            lp().getUserManager().saveUser(u).thenRun(() -> {
                lp().getMessagingService().ifPresent(ms -> ms.pushUserUpdate(u));
                Bukkit.getScheduler().runTask(plugin, () -> {
                    s.sendMessage(c("&aĐã cấp " + tier.color() + tier.name() + " &a" + m + " tháng cho &f" + u.getUsername()
                            + "&a. Hiện: " + describe(u)));
                    if (!lost.isEmpty()) {
                        s.sendMessage(c("&eĐã bỏ bậc cũ: &f" + lost + "&e(bù thời gian nếu cần)."));
                    }
                    plugin.getLogger().info("Rank donate: " + s.getName() + " cấp " + tier.group() + " " + m + " tháng cho "
                            + u.getUsername() + (lost.isEmpty() ? "" : " (bỏ: " + lost + ")"));
                    Player p = Bukkit.getPlayer(u.getUniqueId());
                    if (p != null) {
                        p.sendMessage(c("&6✦ Cảm ơn bạn đã ủng hộ server! Bạn đã nhận " + tier.color() + tier.name() + " &6(" + m + " tháng)."));
                    }
                });
            });
        });
    }

    private int clearTiers(User u) {
        int n = 0;
        for (InheritanceNode node : new ArrayList<>(u.getNodes(net.luckperms.api.node.NodeType.INHERITANCE))) {
            if (tiers.stream().anyMatch(t -> t.group().equalsIgnoreCase(node.getGroupName()))) {
                u.data().remove(node);
                n++;
            }
        }
        lp().getUserManager().saveUser(u).thenRun(() -> lp().getMessagingService().ifPresent(ms -> ms.pushUserUpdate(u)));
        return n;
    }

    /** Tải user (kể cả offline) theo tên rồi chạy việc trên luồng chính. */
    private void withUser(CommandSender s, String name, java.util.function.Consumer<User> job) {
        lp().getUserManager().lookupUniqueId(name).thenAccept((UUID id) -> {
            if (id == null) {
                Bukkit.getScheduler().runTask(plugin, () -> s.sendMessage(c("&cKhông thấy người chơi '" + name + "' (chưa từng vào server?).")));
                return;
            }
            lp().getUserManager().loadUser(id).thenAccept(u -> Bukkit.getScheduler().runTask(plugin, () -> job.accept(u)));
        });
    }

    private static String fold(String s) {
        return ChatFilter.fold(s);
    }
}
