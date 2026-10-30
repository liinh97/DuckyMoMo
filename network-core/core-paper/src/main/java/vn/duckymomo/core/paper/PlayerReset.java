package vn.duckymomo.core.paper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;

/**
 * /choilai: người chơi tự chơi lại từ đầu ở Bang Hội Chiến (chốt 14/10, docs/banghoi-luat-mua1.md mục 11F).
 * Xoá: đồ, rương Ender, XP, hiệu ứng, tiền (về starting-money nếu được quà, không thì 0), nhà, giường, cấp nghề uJobs,
 * rời thị trấn, giờ chơi thật, chờ /tpr; về spawn, bật lại bảo vệ người mới. Quà tân thủ + tiền khởi đầu chỉ lần đầu trong mùa.
 * Giữ: rank donate, danh hiệu, án/lệnh cấm, tài khoản. Trước khi xoá lưu bản sao playerdata + Essentials userdata.
 * Admin (trang quản trị › Hệ thống, console): /choilai admin <tên> [moi] [giurank]
 *   không "moi": như /choilai nhưng không cần xác nhận, không tính lượt, luôn có quà tân thủ (người phải online ở server này);
 *   "moi" = như người mới hoàn toàn (để test): thêm xoá dữ liệu module (điểm danh, nhiệm vụ, gacha, thành tích, Thăng Cấp,
 *   gói quà, /choilai), /caidat, danh phận (giurank: giữ rank ủng hộ/staff/Sứ Giả), rồi đá ra và xoá nhân vật (playerdata, chỉ số,
 *   thành tựu, AuraSkills, Essentials) để lần vào sau là "lần đầu". Người offline thì xoá thẳng.
 */
final class PlayerReset implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final DuckyMoMoCore plugin;
    private final Activity activity;
    private final int cooldownDays;
    private final int maxPerSeason;
    private final String season;
    private final boolean giftFirstOnly;
    private final double startingMoney;
    private final String kit;
    private final int backupDays;
    private final File file;
    private final YamlConfiguration data;
    private final File backupDir;
    private final Map<UUID, Long> confirmUntil = new HashMap<>();
    private final Map<UUID, Long> lastCombat = new HashMap<>();
    /** Người vừa bị admin reset "như người mới": thoát game xong thì xoá file nhân vật. */
    private final java.util.Set<UUID> wipeOnQuit = java.util.concurrent.ConcurrentHashMap.newKeySet();

    PlayerReset(DuckyMoMoCore plugin, Activity activity, ConfigurationSection c) {
        this.plugin = plugin;
        this.activity = activity;
        this.cooldownDays = c.getInt("cooldown-days", 7);
        this.maxPerSeason = c.getInt("max-per-season", 3);
        this.season = c.getString("season", "1");
        this.giftFirstOnly = c.getBoolean("gift-first-only", true);
        this.startingMoney = c.getDouble("starting-money", 200);
        this.kit = c.getString("kit", "tanthu");
        this.backupDays = c.getInt("backup-days", 7);
        this.file = new File(plugin.getDataFolder(), "choilai.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        this.backupDir = new File(plugin.getDataFolder(), "choilai-backup");
        cleanOldBackups();
    }

    private static Component c(String s) {
        return Tip.ns(LEGACY.deserialize(s));
    }

    // ------------------------------------------------------------------ đang đánh nhau

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        Player attacker = e.getDamager() instanceof Player p ? p
                : e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p2 ? p2 : null;
        if (attacker != null && e.getEntity() instanceof Player victim) {
            long now = System.currentTimeMillis();
            lastCombat.put(attacker.getUniqueId(), now);
            lastCombat.put(victim.getUniqueId(), now);
        }
    }

    // ------------------------------------------------------------------ lệnh

    boolean command(CommandSender s, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("admin") && (!(s instanceof Player) || s.hasPermission("duckymomo.choilai.admin"))) {
            java.util.Set<String> f = new java.util.HashSet<>();
            for (int i = 2; i < args.length; i++) {
                f.add(args[i].toLowerCase(Locale.ROOT));
            }
            s.sendMessage(adminReset(args[1], f.contains("moi"), f.contains("giurank")));
            return true;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("Chỉ người chơi dùng được /choilai.");
            return true;
        }
        UUID id = p.getUniqueId();
        List<String> problems = problems(p);
        if (args.length >= 1 && args[0].equalsIgnoreCase("xacnhan")) {
            Long until = confirmUntil.remove(id);
            if (until == null || until < System.currentTimeMillis()) {
                p.sendMessage(c("&cHết hạn xác nhận. Gõ lại &f/choilai&c."));
            } else if (!problems.isEmpty()) {
                problems.forEach(x -> p.sendMessage(c("&c✖ " + x)));
            } else {
                doReset(p);
            }
            return true;
        }
        if (!(BedrockMenu.available() && BedrockMenu.isBedrock(id))) {   // Java: menu xác nhận (22/10, trước in chat + gõ lại lệnh)
            boolean gift = getsGift(id);
            InfoMenu m = InfoMenu.of("&6↺ Chơi lại từ đầu").accent(org.bukkit.Material.RED_STAINED_GLASS_PANE);
            Tip top = Tip.of("&6↺ &fChơi lại từ đầu").sub("Bang Hội Chiến").accent("&c").line()
                    .head("Mất hết").items(List.of("Đồ trên người, rương Ender, kinh nghiệm", "Tiền, nhà (/home), cấp nghề", "Thị trấn, quân hàm, giờ chơi thật"))
                    .note("Đồ cất trong rương ngoài thế giới vẫn còn ở đó.")
                    .gap().head("Giữ lại").items(List.of("Rank ủng hộ, danh hiệu", "Án và lệnh cấm (chơi lại không xoá tội)"))
                    .gap().raw(gift ? "&a✔ Lần đầu trong mùa: có quà tân thủ + " + money(startingMoney) : "&e⚠ Đã chơi lại trong mùa: KHÔNG có quà tân thủ và tiền khởi đầu");
            if (problems.isEmpty()) {
                top.kv("Còn", (maxPerSeason - countThisSeason(id) - 1) + " lần trong mùa, cách nhau " + cooldownDays + " ngày");
            }
            m.top(top.apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.RECOVERY_COMPASS)));
            if (!problems.isEmpty()) {
                Tip no = Tip.of("&c✖ &fChưa chơi lại được").accent("&c");
                problems.forEach(x -> no.raw("&c✖ &7" + x));
                m.add(no.apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.BARRIER)));
            } else {
                confirmUntil.put(id, System.currentTimeMillis() + 60_000L);
                m.add(Tip.of("&a✔ &fXác nhận chơi lại").accent("&a").desc("Bắt đầu lại ngay").line().deny("Không hoàn tác được")
                        .click("Bấm để chơi lại").apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.LIME_CONCRETE)), InfoMenu.run("choilai xacnhan"));
                m.add(Tip.of("&c✖ &fThôi, giữ nguyên").accent("&c").click("Bấm để đóng").apply(new org.bukkit.inventory.ItemStack(org.bukkit.Material.RED_CONCRETE)),
                        pl -> { confirmUntil.remove(pl.getUniqueId()); pl.closeInventory(); });
            }
            m.open(p);
            return true;
        }
        p.sendMessage(c("&6CHƠI LẠI TỪ ĐẦU &7(Bang Hội Chiến)"));
        if (!problems.isEmpty()) {
            p.sendMessage(c("&cChưa chơi lại được:"));
            problems.forEach(x -> p.sendMessage(c("&c✖ " + x)));
            return true;
        }
        boolean gift = getsGift(id);
        p.sendMessage(c("&7Sẽ &cmất hết&7: đồ trên người, rương Ender, kinh nghiệm, tiền, nhà (/home), cấp nghề, thị trấn và quân hàm,"
                + " giờ chơi thật. Đồ cất trong rương ngoài thế giới vẫn còn ở đó."));
        p.sendMessage(c("&7Giữ lại: rank donate, danh hiệu, án và lệnh cấm (chơi lại không xoá tội)."));
        p.sendMessage(c(gift ? "&aLần đầu chơi lại trong mùa: được quà tân thủ + " + money(startingMoney) + "."
                : "&eĐã chơi lại trong mùa này: lần này KHÔNG có quà tân thủ và tiền khởi đầu."));
        p.sendMessage(c(String.format(Locale.ROOT, "&7Còn %d lần trong mùa, mỗi lần cách nhau %d ngày. &cKhông hoàn tác được.",
                maxPerSeason - countThisSeason(id) - 1, cooldownDays)));
        confirmUntil.put(id, System.currentTimeMillis() + 30_000L);
        if (BedrockMenu.available() && BedrockMenu.isBedrock(id)) {
            BedrockMenu.open(id, "Chơi lại từ đầu",
                    "Mất hết đồ, tiền, nhà, cấp nghề, thị trấn. Giữ rank, danh hiệu, án. " + (gift ? "Có quà tân thủ." : "Không có quà tân thủ.")
                            + " Không hoàn tác được.",
                    List.of(new BedrockMenu.Entry("§cXác nhận chơi lại", "ok"), new BedrockMenu.Entry("Huỷ", null)),
                    x -> plugin.getServer().getScheduler().runTask(plugin, () -> p.performCommand("choilai xacnhan")));
        } else {
            p.sendMessage(c("&eGõ &f/choilai xacnhan &etrong 30 giây để bắt đầu lại."));
        }
        return true;
    }

    /**
     * Trang web (RCON: /dmm choilaiweb <tên>): người chơi phải đang online ở server này. Mở bước xác nhận y như gõ /choilai,
     * việc xoá chỉ xảy ra khi chính người đó xác nhận TRONG GAME (/choilai xacnhan hoặc nút trên Bedrock).
     */
    String webRequest(String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p == null) {
            return "CHOILAI OFFLINE";
        }
        List<String> problems = problems(p);
        p.sendMessage(c("&6[Web] &7Bạn vừa bấm &fChơi lại từ đầu &7trên trang web. Không phải bạn thì bỏ qua (không xác nhận là không mất gì)."));
        command(p, new String[0]);
        return problems.isEmpty() ? "CHOILAI OK" : "CHOILAI CHUA " + String.join(" | ", problems);
    }

    private int countThisSeason(UUID id) {
        return data.getInt(id + ".mua." + season + ".so-lan");
    }

    private boolean getsGift(UUID id) {
        return !giftFirstOnly || countThisSeason(id) == 0;
    }

    private String money(double v) {
        return Bukkit.getPluginManager().isPluginEnabled("Vault") && VaultEconomy.available() ? VaultEconomy.format(v)
                : String.format(Locale.ROOT, "%.0f xu", v);
    }

    /** Những lý do chưa cho chơi lại (rỗng = được). */
    private List<String> problems(Player p) {
        List<String> out = new ArrayList<>();
        UUID id = p.getUniqueId();
        long last = data.getLong(id + ".lan-cuoi");
        long wait = last + cooldownDays * 86_400_000L - System.currentTimeMillis();
        if (last > 0 && wait > 0) {
            out.add(String.format(Locale.ROOT, "Mỗi lần chơi lại cách nhau %d ngày, còn %.1f ngày.", cooldownDays, wait / 86_400_000.0));
        }
        if (countThisSeason(id) >= maxPerSeason) {
            out.add("Đã chơi lại " + maxPerSeason + " lần trong mùa này (tối đa).");
        }
        Long fight = lastCombat.get(id);
        if (fight != null && System.currentTimeMillis() - fight < 30_000L) {
            out.add("Đang đánh nhau, chờ 30 giây.");
        }
        if (Bukkit.getPluginManager().isPluginEnabled("Towny")) {
            String t = TownyPart.problem(id);
            if (t != null) {
                out.add(t);
            }
        }
        int shops = Integrations.quickShopCount(id);
        if (shops > 0) {
            out.add("Bạn còn " + shops + " shop rương: phá biển/rương shop để dỡ trước.");
        }
        int ah = Integrations.auctionCount(p);
        if (ah > 0) {
            out.add("Bạn còn " + ah + " món ở Chợ đen: rút món đang bán (/choden cuatoi), nhận món hết hạn (/choden hethan)"
                    + " và nhận đồ đã mua (/choden mua → nút ✔ Đồ đã mua) trước.");
        }
        return out;
    }

    // ------------------------------------------------------------------ chơi lại

    /** Reset do admin làm. Trả câu báo kết quả (trang quản trị hiện nguyên văn). */
    String adminReset(String name, boolean fresh, boolean keepRanks) {
        Player p = Bukkit.getPlayerExact(name);
        UUID id;
        if (p != null) {
            id = p.getUniqueId();
        } else {
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
            if (op == null || !fresh) {
                return fresh ? "RESET LOI: không thấy người chơi \"" + name + "\" ở server này (sai tên hoặc chưa từng vào)."
                        : "RESET LOI: " + name + " phải đang online ở server này (chơi lại cần dọn túi đồ). Chọn \"như người mới\" để reset khi offline.";
            }
            id = op.getUniqueId();
        }
        if (p != null) {
            doReset(p, true);
        } else {
            backupFiles(name, id);
            if (Bukkit.getPluginManager().isPluginEnabled("Towny")) {
                TownyPart.leaveTown(id);
            }
            for (String home : Integrations.essentialsHomes(id)) {
                console("delhome " + name + ":" + home);
            }
            activity.reset(id);
            RandomTeleport.clearCooldown(id);
        }
        if (!fresh) {
            plugin.getLogger().info("Admin reset (chơi lại): " + name);
            return "RESET OK: " + name + " đã chơi lại từ đầu (như /choilai, không tính lượt, có quà tân thủ).";
        }
        for (java.util.function.Consumer<UUID> c : Shared.FORGET) {
            try {
                c.accept(id);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Reset " + name + ": một module không xoá được dữ liệu: " + e);
            }
        }
        data.set(id.toString(), null);
        try {
            data.save(file);
        } catch (IOException ignored) {
            // lần sau
        }
        int lp = lpReset(id, keepRanks);
        if (p != null) {
            wipeOnQuit.add(id);
            p.kick(c("&eTài khoản của bạn vừa được admin reset như người mới.\n&7Vào lại để bắt đầu từ đầu."));
        } else {
            wipeFiles(id);
        }
        plugin.getLogger().info("Admin reset (như người mới): " + name + " " + id + (keepRanks ? " (giữ rank)" : ""));
        return "RESET OK: " + name + " đã reset như người mới" + (p != null ? " (đã đá ra, vào lại là lần đầu)" : "")
                + "; xoá " + lp + " quyền/nhóm LuckPerms" + (keepRanks ? " (giữ rank ủng hộ/staff/Sứ Giả)" : "") + ". Sao lưu: plugins/DuckyMoMoCore/choilai-backup.";
    }

    /** Bỏ /caidat (meta dmmcaidat.*) và danh phận (nhóm các bậc); không giữ rank thì bỏ mọi nhóm trừ default và mọi quyền lẻ. Trả số node đã bỏ.
     *  Quyền đặc quyền Thăng Cấp do LevelUp thu hồi (Shared.FORGET). */
    private int lpReset(UUID id, boolean keepRanks) {
        java.util.Set<String> tiers = new java.util.HashSet<>();
        ConfigurationSection ts = plugin.getConfig().getConfigurationSection("play-ranks.tiers");
        if (ts != null) {
            for (String k : ts.getKeys(false)) {
                tiers.add(ts.getString(k + ".group", k).toLowerCase(Locale.ROOT));
            }
        }
        tiers.remove("default");
        int[] n = {0};
        try {
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            lp.getUserManager().modifyUser(id, u -> {
                for (net.luckperms.api.node.Node node : new ArrayList<>(u.getNodes())) {
                    boolean meta = node instanceof net.luckperms.api.node.types.MetaNode m && m.getMetaKey().startsWith("dmmcaidat.");
                    boolean group = node instanceof net.luckperms.api.node.types.InheritanceNode in
                            && !in.getGroupName().equalsIgnoreCase("default")
                            && (!keepRanks || tiers.contains(in.getGroupName().toLowerCase(Locale.ROOT)));
                    // không giữ rank: bỏ cả quyền lẻ (món Vàng, quyền admin cấp tay…) — 22/10, trước đó còn sót sau reset
                    boolean perm = !keepRanks && node instanceof net.luckperms.api.node.types.PermissionNode;
                    if (meta || group || perm) {
                        u.data().remove(node);
                        n[0]++;
                    }
                }
            }).join();
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().warning("Reset: không sửa được LuckPerms: " + e);
        }
        return n[0];
    }

    /** File nhân vật (vị trí, túi đồ, chỉ số, thành tựu) + Essentials + AuraSkills của người (đã offline). */
    private List<File> playerFiles(UUID id) {
        File world = Bukkit.getWorlds().get(0).getWorldFolder();
        // 26.x: thư mục thế giới là world/dimensions/minecraft/overworld, file nhân vật ở world/players (22/10: trước không xoá được)
        for (File d = world; d != null; d = d.getParentFile()) {
            if (new File(d, "players").isDirectory() || new File(d, "playerdata").isDirectory()) {
                world = d;
                break;
            }
        }
        File plugins = plugin.getDataFolder().getParentFile();
        List<File> out = new ArrayList<>();
        for (String rel : List.of("players/data/%s.dat", "players/data/%s.dat_old", "players/stats/%s.json", "players/advancements/%s.json",
                "playerdata/%s.dat", "playerdata/%s.dat_old", "stats/%s.json", "advancements/%s.json")) {
            out.add(new File(world, rel.formatted(id)));
        }
        out.add(new File(plugins, "Essentials/userdata/" + id + ".yml"));
        out.add(new File(plugins, "AuraSkills/userdata/" + id + ".yml"));
        return out;
    }

    private void wipeFiles(UUID id) {
        int n = 0;
        for (File f : playerFiles(id)) {
            if (f.isFile() && f.delete()) {
                n++;
            }
        }
        plugin.getLogger().info("Reset: đã xoá " + n + " file nhân vật của " + id);
    }

    /** Thoát game xong (server đã ghi file nhân vật) mới xoá, 2 giây sau cho chắc. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (wipeOnQuit.remove(id)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (Bukkit.getPlayer(id) == null) {
                    wipeFiles(id);
                }
            }, 40L);
        }
    }

    private void doReset(Player p) {
        doReset(p, false);
    }

    /** admin: không kiểm điều kiện, không tính lượt chơi lại, luôn có quà tân thủ. */
    private void doReset(Player p, boolean admin) {
        UUID id = p.getUniqueId();
        String name = p.getName();
        boolean gift = admin || getsGift(id);
        backup(p);

        if (Bukkit.getPluginManager().isPluginEnabled("Towny")) {
            TownyPart.leaveTown(id);
        }
        p.closeInventory();
        p.getInventory().clear();
        p.getEnderChest().clear();
        Backpack bp = Shared.backpack;
        if (bp != null) {
            bp.wipe(id);   // balo cũng là đồ: chơi lại thì mất
        }
        p.setItemOnCursor(null);
        p.setLevel(0);
        p.setExp(0);
        p.setTotalExperience(0);
        for (PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
        var maxHp = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(maxHp == null ? 20 : maxHp.getValue());
        p.setFoodLevel(20);
        p.setSaturation(5);
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.setRespawnLocation(null);

        if (Bukkit.getPluginManager().isPluginEnabled("Vault") && VaultEconomy.available()) {
            VaultEconomy.setBalance(p, gift ? startingMoney : 0);
        }
        for (String home : Integrations.essentialsHomes(id)) {
            console("delhome " + name + ":" + home);
        }
        if (Integrations.jobsReborn()) {
            Shared.JOBS_FREE.add(id);   // chơi lại: rời nghề ở đâu cũng được
            try {
                console("jobs fireall " + name);   // rời nghề (Jobs giữ cấp cũ trong lưu trữ, vào lại nghề đó bị trừ 30% cấp)
            } finally {
                Shared.JOBS_FREE.remove(id);
            }
        }
        activity.reset(id);
        RandomTeleport.clearCooldown(id);
        console("spawn " + name);
        if (gift && !kit.isBlank()) {
            console("kit " + kit + " " + name);
        }
        if (Bukkit.getPluginManager().isPluginEnabled("PvPManager")) {
            console("newbie add " + name);
        }

        if (admin) {
            p.sendMessage(c("&eAdmin vừa cho bạn chơi lại từ đầu." + (gift ? " &aĐã nhận quà tân thủ." : "")));
            return;
        }
        data.set(id + ".ten", name);
        data.set(id + ".lan-cuoi", System.currentTimeMillis());
        data.set(id + ".mua." + season + ".so-lan", countThisSeason(id) + 1);
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được choilai.yml: " + e.getMessage());
        }
        plugin.getLogger().info("/choilai: " + name + " đã chơi lại từ đầu (lần " + countThisSeason(id) + " mùa " + season
                + (gift ? ", có quà" : ", không quà") + ")");
        p.sendMessage(c("&aĐã bắt đầu lại từ đầu!" + (gift ? " &aĐã nhận quà tân thủ." : "") + " &7Gõ &f/huongdan &7để xem cẩm nang."));
    }

    private void console(String cmd) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    private void backup(Player p) {
        p.saveData();
        backupFiles(p.getName(), p.getUniqueId());
    }

    /** Sao lưu file nhân vật (26.x: world/players/data, bản cũ: world/playerdata) + Essentials + AuraSkills. */
    private void backupFiles(String name, UUID id) {
        File dir = new File(backupDir, name + "-" + id + "-" + LocalDateTime.now().format(STAMP));
        if (!dir.mkdirs()) {
            plugin.getLogger().warning("Không tạo được thư mục sao lưu " + dir);
        }
        for (File f : playerFiles(id)) {
            if (f.isFile()) {
                try {
                    Files.copy(f.toPath(), new File(dir, f.getParentFile().getName() + "-" + f.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    plugin.getLogger().warning("Sao lưu " + f + " lỗi: " + e.getMessage());
                }
            }
        }
    }

    private void cleanOldBackups() {
        File[] dirs = backupDir.listFiles(File::isDirectory);
        if (dirs == null) {
            return;
        }
        long cut = System.currentTimeMillis() - backupDays * 86_400_000L;
        for (File d : dirs) {
            if (d.lastModified() < cut) {
                File[] fs = d.listFiles();
                if (fs != null) {
                    for (File f : fs) {
                        f.delete();
                    }
                }
                d.delete();
            }
        }
    }

    // ------------------------------------------------------------------ Towny (lớp riêng: không có Towny thì không nạp)

    private static final class TownyPart {
        static String problem(UUID id) {
            com.palmergames.bukkit.towny.object.Resident r = com.palmergames.bukkit.towny.TownyAPI.getInstance().getResident(id);
            if (r == null || !r.hasTown()) {
                return null;
            }
            if (r.isKing()) {
                return "Bạn đang là vua: nhường ngôi trước (/n set king <tên>).";
            }
            if (r.isMayor()) {
                return "Bạn đang là thị trưởng: nhường chức trước (/t set mayor <tên>).";
            }
            if (SiegeWarHook.hasActiveSiege(r.getTownOrNull())) {
                return "Thị trấn của bạn đang bị vây: chờ cuộc vây kết thúc.";
            }
            return null;
        }

        static void leaveTown(UUID id) {
            com.palmergames.bukkit.towny.object.Resident r = com.palmergames.bukkit.towny.TownyAPI.getInstance().getResident(id);
            if (r != null && r.hasTown() && !r.isMayor()) {
                r.removeTown();
                r.save();
            }
        }
    }
}
