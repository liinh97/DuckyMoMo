package vn.duckymomo.core.paper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Thăng Cấp (/capdo, Bang Hội Chiến; ý từ "Prestige" của UniMC, tự làm lại). Config level-up:
 *  - max-level cấp. Lên cấp N cần ĐỦ: xu (trả khi lên, rút khỏi kinh tế) = money-base × N^money-exponent; giờ chơi thật của mùa
 *    (Activity) >= hours-per-level × N; điểm hạ gục tích luỹ >= kill-points-per-level × N.
 *  - Điểm hạ gục: hạ quái +mob-points, hạ người +player-points (quái từ lồng không tính; cùng một người chỉ tính 1 lần / player-cooldown-minutes).
 *  - Lên cấp: +points-per-level điểm; mỗi milestone-every cấp: milestone-rewards (quà Rewards).
 *  - Cửa hàng (shop.<mã>: name, icon, cost (điểm), perms [quyền LuckPerms cấp vĩnh viễn], rewards [quà], description): mua 1 lần.
 *    CHỈ tiện ích / trang trí (không sát thương, máu…): công thành công bằng; điểm chỉ kiếm bằng chơi (luật Mojang).
 *  - keep-on-new-season: false (mặc định) = khi leaderboard.season-start đổi (mùa mới) thì cấp, điểm, đặc quyền đã mua làm lại từ đầu.
 * Hiện %dmmcap_cap% (TAB). Lưu capdo.yml; ghi capdo.json (top) cho web.
 */
final class LevelUp implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    static volatile LevelUp INSTANCE;

    record Perk(String id, String name, Material icon, int cost, List<String> perms, List<String> rewards, List<String> desc) { }

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        String view = "main";
        final Map<Integer, String> slots = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final JavaPlugin plugin;
    private final Activity activity;
    private final Rewards rewards;
    private final int maxLevel;
    private final double moneyBase;
    private final double moneyExp;
    private final double hoursPer;
    private final int killPer;
    private final int mobPoints;
    private final int playerPoints;
    private final long playerCdMs;
    private final int pointsPerLevel;
    private final int milestoneEvery;
    private final List<String> milestoneRewards;
    private final boolean keep;
    private final Map<String, Perk> shop = new LinkedHashMap<>();
    private final File file;
    private final YamlConfiguration data;
    private final Map<String, Long> lastVictim = new HashMap<>();
    private boolean dirty;

    LevelUp(JavaPlugin plugin, Activity activity, Rewards rewards, ConfigurationSection c) {
        this.plugin = plugin;
        this.activity = activity;
        this.rewards = rewards;
        this.maxLevel = Math.max(1, c.getInt("max-level", 100));
        this.moneyBase = Math.max(0, c.getDouble("money-base", 1000));
        this.moneyExp = Math.max(0, c.getDouble("money-exponent", 1.3));
        this.hoursPer = Math.max(0, c.getDouble("hours-per-level", 1));
        this.killPer = Math.max(0, c.getInt("kill-points-per-level", 20));
        this.mobPoints = Math.max(0, c.getInt("mob-points", 1));
        this.playerPoints = Math.max(0, c.getInt("player-points", 10));
        this.playerCdMs = Math.max(0, c.getLong("player-cooldown-minutes", 10)) * 60_000L;
        this.pointsPerLevel = Math.max(0, c.getInt("points-per-level", 1));
        this.milestoneEvery = Math.max(0, c.getInt("milestone-every", 10));
        this.milestoneRewards = c.getStringList("milestone-rewards");
        this.keep = c.getBoolean("keep-on-new-season", false);
        ConfigurationSection ss = c.getConfigurationSection("shop");
        if (ss != null) {
            for (String id : ss.getKeys(false)) {
                ConfigurationSection p = ss.getConfigurationSection(id);
                if (p == null || !p.getBoolean("enabled", true)) {
                    continue;
                }
                Material m = Material.matchMaterial(p.getString("icon", "PAPER"));
                shop.put(id, new Perk(id, p.getString("name", id), m == null ? Material.PAPER : m, Math.max(0, p.getInt("cost", 1)),
                        p.getStringList("perms"), p.getStringList("rewards"), p.getStringList("description")));
            }
        }
        this.file = new File(plugin.getDataFolder(), "capdo.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        Shared.FORGET.add(id -> Shared.forget(data, file, id));   // admin reset "như người mới"
    }

    String describe() {
        return maxLevel + " cấp, " + shop.size() + " đặc quyền, quái +" + mobPoints + " / người +" + playerPoints + (keep ? ", giữ qua mùa" : ", làm lại mỗi mùa");
    }

    void start() {
        INSTANCE = this;
        seasonCheck();
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) {
                save();
                writeJson();
            }
        }, 1200L, 1200L);
        writeJson();
    }

    void stop() {
        INSTANCE = null;
        save();
    }

    // ---------------------------------------------------------------- số liệu

    private String k(UUID u) {
        return "nguoi." + u + ".";
    }

    int level(UUID u) {
        return data.getInt(k(u) + "cap", 0);
    }

    static int levelOf(UUID u) {
        LevelUp l = INSTANCE;
        return l == null ? 0 : l.level(u);
    }

    private int points(UUID u) {
        return data.getInt(k(u) + "diem", 0);
    }

    private int killPts(UUID u) {
        return data.getInt(k(u) + "ha", 0);
    }

    double moneyFor(int lv) {
        return Math.round(moneyBase * Math.pow(lv, moneyExp));
    }

    private double hoursFor(int lv) {
        return hoursPer * lv;
    }

    private int killsFor(int lv) {
        return killPer * lv;
    }

    private double hours(UUID u) {
        return activity == null ? 0 : activity.totalHours(u);
    }

    /** Mùa mới (leaderboard.season-start đổi) và không giữ cấp: làm lại cấp, điểm, gỡ đặc quyền đã mua. */
    private void seasonCheck() {
        String season = plugin.getConfig().getString("leaderboard.season-start", "");
        String seen = data.getString("mua", null);
        if (seen == null) {
            data.set("mua", season);
            dirty = true;
            return;
        }
        if (seen.equals(season) || keep) {
            if (!seen.equals(season)) {
                data.set("mua", season);
                dirty = true;
            }
            return;
        }
        ConfigurationSection ps = data.getConfigurationSection("nguoi");
        int n = 0;
        if (ps != null) {
            for (String id : ps.getKeys(false)) {
                List<String> perms = data.getStringList("nguoi." + id + ".quyen");
                if (!perms.isEmpty()) {
                    UUID u = UUID.fromString(id);
                    LuckPermsProvider.get().getUserManager().modifyUser(u, user -> perms.forEach(pn -> user.data().remove(PermissionNode.builder(pn).build())));
                }
                n++;
            }
        }
        data.set("nguoi", null);
        data.set("mua", season);
        dirty = true;
        save();
        plugin.getLogger().info("Thăng Cấp: mùa mới (" + season + ") → làm lại cấp của " + n + " người.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        LivingEntity v = e.getEntity();
        if (killer == null || v == killer) {
            return;
        }
        int add;
        if (v instanceof Player vp) {
            String key = killer.getUniqueId() + ">" + vp.getUniqueId();
            long now = System.currentTimeMillis();
            if (now - lastVictim.getOrDefault(key, 0L) < playerCdMs) {
                return;   // giết đi giết lại một người: không tính
            }
            lastVictim.put(key, now);
            add = playerPoints;
        } else {
            if (v.getEntitySpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER || v.getEntitySpawnReason() == CreatureSpawnEvent.SpawnReason.TRIAL_SPAWNER) {
                return;
            }
            add = mobPoints;
        }
        if (add <= 0) {
            return;
        }
        UUID u = killer.getUniqueId();
        data.set(k(u) + "ha", killPts(u) + add);
        data.set(k(u) + "ten", killer.getName());
        dirty = true;
    }

    // ---------------------------------------------------------------- thăng cấp, cửa hàng

    private boolean ready(Player p, int next) {
        return next <= maxLevel && VaultEconomy.available() && VaultEconomy.has(p, moneyFor(next))
                && hours(p.getUniqueId()) >= hoursFor(next) && killPts(p.getUniqueId()) >= killsFor(next);
    }

    private void levelUp(Player p) {
        UUID u = p.getUniqueId();
        int next = level(u) + 1;
        if (next > maxLevel) {
            p.sendMessage(c("&6Bạn đã đạt cấp tối đa (" + maxLevel + ")."));
            return;
        }
        if (!ready(p, next)) {
            p.sendMessage(c("&cChưa đủ điều kiện lên cấp " + next + ". Xem trong &f/capdo&c."));
            return;
        }
        if (!VaultEconomy.withdraw(p, moneyFor(next))) {
            p.sendMessage(c("&cKhông trừ được tiền."));
            return;
        }
        data.set(k(u) + "cap", next);
        data.set(k(u) + "diem", points(u) + pointsPerLevel);
        data.set(k(u) + "ten", p.getName());
        dirty = true;
        save();
        writeJson();
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.1f);
        p.sendMessage(c("&a⬆ Lên cấp " + next + "! &7+" + pointsPerLevel + " điểm thăng cấp (đang có " + points(u) + "). Đổi đặc quyền: &f/capdo"));
        if (milestoneEvery > 0 && next % milestoneEvery == 0) {
            List<String> got = rewards == null ? List.of() : rewards.give(p, milestoneRewards);
            PlayerSettings.broadcast("sukien", c("&6⬆ " + p.getName() + " &eđã đạt &6Cấp " + next + "&e!" + (got.isEmpty() ? "" : " &7Quà mốc: &f" + String.join(", ", got))));
        }
    }

    private void buy(Player p, Perk perk) {
        UUID u = p.getUniqueId();
        List<String> owned = new ArrayList<>(data.getStringList(k(u) + "mua"));
        if (owned.contains(perk.id())) {
            p.sendMessage(c("&7Bạn đã có &f" + perk.name() + "&7."));
            return;
        }
        if (points(u) < perk.cost()) {
            p.sendMessage(c("&cCần " + perk.cost() + " điểm thăng cấp (đang có " + points(u) + ")."));
            return;
        }
        data.set(k(u) + "diem", points(u) - perk.cost());
        owned.add(perk.id());
        data.set(k(u) + "mua", owned);
        if (!perk.perms().isEmpty()) {
            List<String> granted = new ArrayList<>(data.getStringList(k(u) + "quyen"));
            granted.addAll(perk.perms());
            data.set(k(u) + "quyen", granted);
            LuckPermsProvider.get().getUserManager().modifyUser(u, user -> perk.perms().forEach(pn -> user.data().add(PermissionNode.builder(pn).value(true).build())));
        }
        if (!perk.rewards().isEmpty() && rewards != null) {
            rewards.give(p, perk.rewards());
        }
        dirty = true;
        save();
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
        p.sendMessage(c("&a✔ Đã đổi &f" + perk.name() + "&a (−" + perk.cost() + " điểm)."));
    }

    // ---------------------------------------------------------------- menu

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    private static String money(double v) {
        return VaultEconomy.available() ? VaultEconomy.format(v) : String.format(Locale.ROOT, "%,.0f xu", v);
    }

    private String mark(boolean ok) {
        return ok ? "&a" : "&c";
    }

    void open(Player p) {
        UUID u = p.getUniqueId();
        int lv = level(u), next = lv + 1;
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 45, c("&0⬆ Thăng Cấp &8· cấp " + lv + "/" + maxLevel));
        h.inv.setItem(4, Tip.of("&6⬆ &fCấp " + lv).sub("tối đa " + maxLevel).desc("Tiến độ của bạn trong mùa này").line()
                .kv("Điểm thăng cấp", "&e" + points(u))
                .kv("Điểm hạ gục", killPts(u) + " &8(quái +" + mobPoints + ", người +" + playerPoints + ")")
                .kv("Giờ chơi", String.format(Locale.ROOT, "%.1f", hours(u)) + " giờ")
                .apply(new ItemStack(Material.NETHER_STAR)));
        if (next <= maxLevel) {
            boolean mOk = VaultEconomy.available() && VaultEconomy.has(p, moneyFor(next));
            boolean hOk = hours(u) >= hoursFor(next), kOk = killPts(u) >= killsFor(next);
            boolean all = mOk && hOk && kOk;
            Tip t = Tip.of((all ? "&a⬆ " : "&e⬆ ") + "&fLên cấp " + next).accent(all ? "&a" : "&e").head("Điều kiện")
                    .item(mark(mOk) + money(VaultEconomy.available() ? VaultEconomy.balance(p) : 0) + "&7/" + money(moneyFor(next)) + " &8(trả khi lên)")
                    .item(mark(kOk) + killPts(u) + "&7/" + killsFor(next) + " điểm hạ gục")
                    .item(mark(hOk) + String.format(Locale.ROOT, "%.1f", hours(u)) + "&7/" + fmtH(hoursFor(next)) + " giờ chơi")
                    .gap().head("Thưởng").item("&e+" + pointsPerLevel + " điểm thăng cấp")
                    ;
            if (milestoneEvery > 0 && next % milestoneEvery == 0) {
                t.item("&6Quà mốc cấp " + next);
            }
            t.line();
            if (all) {
                t.click("Bấm để lên cấp");
            } else {
                t.deny("Chưa đủ điều kiện");
            }
            h.inv.setItem(22, t.apply(new ItemStack(all ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE)));
            h.slots.put(22, "len");
        } else {
            h.inv.setItem(22, Tip.of("&6✦ &fĐã đạt cấp tối đa").desc("Mùa sau làm lại từ đầu").apply(new ItemStack(Material.GOLD_BLOCK)));
        }
        h.inv.setItem(38, Tip.of("&e⛃ &fCửa hàng thăng cấp").sub(points(u) + " điểm").desc("Dùng điểm thăng cấp đổi tiện ích, trang trí")
                .line().click("Bấm để mở").apply(new ItemStack(Material.CHEST)));
        h.slots.put(38, "shop");
        h.inv.setItem(42, Tip.of("&b♛ &fXếp hạng").desc("Những người cấp cao nhất server").line().click("Bấm để xem").apply(new ItemStack(Material.GOLD_INGOT)));
        h.slots.put(42, "top");
        p.openInventory(h.inv);
    }

    private static String fmtH(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private void openShop(Player p) {
        UUID u = p.getUniqueId();
        Holder h = new Holder();
        h.view = "shop";
        h.inv = Bukkit.createInventory(h, 54, c("&0⛃ Cửa hàng thăng cấp &8· " + points(u) + " điểm"));
        List<String> owned = data.getStringList(k(u) + "mua");
        int slot = 10;
        for (Perk pk : shop.values()) {
            if (slot > 43) {
                break;
            }
            if (slot % 9 == 8) {
                slot += 2;
            }
            boolean has = owned.contains(pk.id());
            Tip t = Tip.of((has ? "&a✔ " : "&e✦ ") + "&f" + pk.name()).desc(pk.desc().toArray(String[]::new)).line();
            if (has) {
                t.kv("Trạng thái", "&aĐã có");
            } else {
                boolean can = points(u) >= pk.cost();
                t.kv("Giá", (can ? "&e" : "&c") + pk.cost() + " điểm");
                if (can) {
                    t.click("Bấm để đổi");
                } else {
                    t.deny("Chưa đủ điểm");
                }
            }
            h.inv.setItem(slot, t.apply(new ItemStack(pk.icon())));
            h.slots.put(slot, "mua:" + pk.id());
            slot++;
        }
        h.inv.setItem(49, Tip.of("&7← &fQuay lại").apply(new ItemStack(Material.ARROW)));
        h.slots.put(49, "back");
        p.openInventory(h.inv);
    }

    private List<Map.Entry<String, int[]>> ranking() {
        List<Map.Entry<String, int[]>> out = new ArrayList<>();
        ConfigurationSection ps = data.getConfigurationSection("nguoi");
        if (ps != null) {
            for (String id : ps.getKeys(false)) {
                int lv = data.getInt("nguoi." + id + ".cap", 0);
                if (lv > 0) {
                    out.add(Map.entry(id, new int[]{lv, data.getInt("nguoi." + id + ".diem", 0)}));
                }
            }
        }
        out.sort((a, b) -> b.getValue()[0] != a.getValue()[0] ? b.getValue()[0] - a.getValue()[0] : b.getValue()[1] - a.getValue()[1]);
        return out;
    }

    private void openTop(Player p) {
        Holder h = new Holder();
        h.view = "top";
        h.inv = Bukkit.createInventory(h, 45, c("&0♛ Xếp hạng Thăng Cấp"));
        int[] slots = {4, 12, 14, 20, 21, 22, 23, 24, 30, 32};
        List<Map.Entry<String, int[]>> top = ranking();
        for (int i = 0; i < Math.min(slots.length, top.size()); i++) {
            var e = top.get(i);
            OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(e.getKey()));
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setOwningPlayer(op);
            sm.displayName(c("&e[#" + (i + 1) + "] &f" + data.getString("nguoi." + e.getKey() + ".ten", op.getName())));
            sm.lore(Tip.of("").kv("Cấp", "&b" + e.getValue()[0] + "/" + maxLevel).kv("Điểm thăng cấp", "&e" + e.getValue()[1]).lore());
            head.setItemMeta(sm);
            h.inv.setItem(slots[i], head);
        }
        h.inv.setItem(40, Tip.of("&7← &fQuay lại").apply(new ItemStack(Material.ARROW)));
        h.slots.put(40, "back");
        p.openInventory(h.inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        String a = h.slots.get(e.getRawSlot());
        if (a == null || !(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        switch (a) {
            case "len" -> {
                levelUp(p);
                open(p);
            }
            case "shop" -> openShop(p);
            case "top" -> openTop(p);
            case "back" -> open(p);
            default -> {
                if (a.startsWith("mua:")) {
                    Perk pk = shop.get(a.substring(4));
                    if (pk != null) {
                        buy(p, pk);
                        openShop(p);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    boolean command(CommandSender s, String[] args) {
        if (args.length >= 3 && args[0].equalsIgnoreCase("dat") && s.hasPermission("duckymomo.admin")) {
            OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
            try {
                data.set(k(t.getUniqueId()) + "cap", Math.max(0, Math.min(maxLevel, Integer.parseInt(args[2]))));
                data.set(k(t.getUniqueId()) + "ten", t.getName());
                dirty = true;
                save();
                writeJson();
                s.sendMessage("Đã đặt cấp " + args[2] + " cho " + t.getName());
            } catch (NumberFormatException ex) {
                s.sendMessage("/capdo dat <tên> <cấp>");
            }
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("top")) {
            s.sendMessage(c("&6Xếp hạng Thăng Cấp"));
            List<Map.Entry<String, int[]>> top = ranking();
            for (int i = 0; i < Math.min(10, top.size()); i++) {
                s.sendMessage(c("&e#" + (i + 1) + " &f" + data.getString("nguoi." + top.get(i).getKey() + ".ten", "?") + " &7— cấp &b" + top.get(i).getValue()[0]));
            }
            return true;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("/capdo top | /capdo dat <tên> <cấp>");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("len")) {
            levelUp(p);
            return true;
        }
        open(p);
        return true;
    }

    // ---------------------------------------------------------------- lưu + web

    private void save() {
        try {
            data.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được capdo.yml: " + e.getMessage());
        }
    }

    private void writeJson() {
        JsonObject root = new JsonObject();
        root.addProperty("toi_da", maxLevel);
        JsonArray arr = new JsonArray();
        List<Map.Entry<String, int[]>> top = ranking();
        for (int i = 0; i < Math.min(10, top.size()); i++) {
            JsonObject o = new JsonObject();
            o.addProperty("ten", data.getString("nguoi." + top.get(i).getKey() + ".ten", "?"));
            o.addProperty("cap", top.get(i).getValue()[0]);
            arr.add(o);
        }
        root.add("top", arr);
        JsonObject nguoi = new JsonObject();
        ConfigurationSection ps = data.getConfigurationSection("nguoi");
        if (ps != null) {
            for (String id : ps.getKeys(false)) {
                JsonObject o = new JsonObject();
                o.addProperty("cap", data.getInt("nguoi." + id + ".cap", 0));
                o.addProperty("diem", data.getInt("nguoi." + id + ".diem", 0));
                o.addProperty("ha", data.getInt("nguoi." + id + ".ha", 0));
                nguoi.add(id, o);
            }
        }
        root.add("nguoi", nguoi);
        try {
            Files.writeString(new File(plugin.getDataFolder(), "capdo.json").toPath(), root.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Không ghi được capdo.json: " + e.getMessage());
        }
    }
}
