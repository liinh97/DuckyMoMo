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
        return LEGACY.deserialize(s);
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
            out.add("Bạn còn " + ah + " món trên chợ đấu giá (/ah): rút về hoặc nhận lại món hết hạn/đã mua trước.");
        }
        return out;
    }

    // ------------------------------------------------------------------ chơi lại

    private void doReset(Player p) {
        UUID id = p.getUniqueId();
        String name = p.getName();
        boolean gift = getsGift(id);
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
        for (String job : Integrations.ujobsJobs()) {
            console("ujobs setlevel " + name + " " + job + " 0");
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
        File dir = new File(backupDir, p.getName() + "-" + p.getUniqueId() + "-" + LocalDateTime.now().format(STAMP));
        if (!dir.mkdirs()) {
            plugin.getLogger().warning("Không tạo được thư mục sao lưu " + dir);
        }
        File world = Bukkit.getWorlds().get(0).getWorldFolder();
        File ess = new File(plugin.getDataFolder().getParentFile(), "Essentials/userdata/" + p.getUniqueId() + ".yml");
        for (File f : new File[] {new File(world, "playerdata/" + p.getUniqueId() + ".dat"), ess}) {
            if (f.isFile()) {
                try {
                    Files.copy(f.toPath(), new File(dir, f.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
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
