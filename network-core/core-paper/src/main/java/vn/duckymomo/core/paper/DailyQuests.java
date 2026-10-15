package vn.duckymomo.core.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Nhiệm vụ hằng ngày (/nhiemvu). Mỗi người mỗi ngày nhận per-day nhiệm vụ ngẫu nhiên (cố định trong ngày) từ pool.
 * Loại: break (phá khối), place (đặt khối), kill (giết mob), fish (câu cá), harvest (thu hoạch cây đã chín).
 * targets: tên vật liệu/mob, dạng *_ORE, OAK_*, * (tất cả), @hostile (mọi quái thù địch). Khối do người chơi đặt không tính
 * (PlacedBlockGuard huỷ sự kiện phá; ở đây ignoreCancelled). Làm xong một nhiệm vụ: quà của nó; xong hết: complete-all-rewards.
 * Lưu: plugins/DuckyMoMoCore/nhiemvu.yml (ghi mỗi phút + khi thoát).
 */
final class DailyQuests implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private record Quest(String id, String name, String type, List<String> targets, int amount, List<String> rewards) { }

    private final JavaPlugin plugin;
    private final ConfigurationSection cfg;
    private final Rewards rewards;
    private final File file;
    private final YamlConfiguration data;
    private final Map<String, Quest> pool = new LinkedHashMap<>();
    private boolean dirty;

    DailyQuests(JavaPlugin plugin, ConfigurationSection cfg, Rewards rewards) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.rewards = rewards;
        this.file = new File(plugin.getDataFolder(), "nhiemvu.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection p = cfg.getConfigurationSection("pool");
        if (p != null) {
            for (String id : p.getKeys(false)) {
                ConfigurationSection q = p.getConfigurationSection(id);
                if (q == null) {
                    continue;
                }
                List<String> t = q.getStringList("targets").stream().map(x -> x.toUpperCase(Locale.ROOT)).toList();
                pool.put(id, new Quest(id, q.getString("name", id), q.getString("type", "break").toLowerCase(Locale.ROOT),
                        t.isEmpty() ? List.of("*") : t, Math.max(1, q.getInt("amount", 1)), q.getStringList("rewards")));
            }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::flush, 1200L, 1200L);
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s);
    }

    private static String today() {
        return LocalDate.now(VN).toString();
    }

    void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được nhiemvu.yml: " + e.getMessage());
        }
    }

    /** Nhiệm vụ hôm nay của người chơi (tạo mới khi sang ngày). */
    private List<String> todays(UUID id) {
        String key = "players." + id;
        if (!today().equals(data.getString(key + ".date"))) {
            List<String> ids = new ArrayList<>(pool.keySet());
            Collections.shuffle(ids, new Random((id.toString() + today()).hashCode()));
            List<String> pick = ids.subList(0, Math.min(cfg.getInt("per-day", 3), ids.size()));
            data.set(key, null);
            data.set(key + ".date", today());
            data.set(key + ".quests", new ArrayList<>(pick));
            dirty = true;
        }
        return data.getStringList(key + ".quests");
    }

    private static boolean matches(List<String> targets, String name) {
        for (String t : targets) {
            if (t.equals("*") || t.equals(name)
                    || (t.startsWith("*") && name.endsWith(t.substring(1)))
                    || (t.endsWith("*") && name.startsWith(t.substring(0, t.length() - 1)))) {
                return true;
            }
        }
        return false;
    }

    private void progress(Player p, String type, String target, boolean hostile, int n) {
        String key = "players." + p.getUniqueId();
        for (String qid : todays(p.getUniqueId())) {
            Quest q = pool.get(qid);
            if (q == null || !q.type().equals(type)) {
                continue;
            }
            if (!(matches(q.targets(), target) || (hostile && q.targets().contains("@HOSTILE")))) {
                continue;
            }
            int done = data.getInt(key + ".progress." + qid, 0);
            if (done >= q.amount()) {
                continue;
            }
            done = Math.min(q.amount(), done + n);
            data.set(key + ".progress." + qid, done);
            dirty = true;
            p.sendActionBar(c("&e" + q.name() + " &f" + done + "&7/&f" + q.amount()));
            if (done >= q.amount()) {
                complete(p, q);
            }
        }
    }

    private void complete(Player p, Quest q) {
        List<String> got = rewards.give(p, q.rewards());
        p.sendMessage(c("&a✔ Xong nhiệm vụ: &f" + q.name() + (got.isEmpty() ? "" : "&a. Nhận: &f" + String.join("&7, &f", got))));
        String key = "players." + p.getUniqueId();
        boolean all = true;
        for (String qid : todays(p.getUniqueId())) {
            Quest x = pool.get(qid);
            if (x != null && data.getInt(key + ".progress." + qid, 0) < x.amount()) {
                all = false;
            }
        }
        if (all && !data.getBoolean(key + ".bonus")) {
            data.set(key + ".bonus", true);
            List<String> bonus = rewards.give(p, cfg.getStringList("complete-all-rewards"));
            p.sendMessage(c("&6★ Xong hết nhiệm vụ hôm nay!" + (bonus.isEmpty() ? "" : " Thưởng thêm: &f" + String.join("&7, &f", bonus))));
        }
        flush();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Material m = b.getType();
        if (b.getBlockData() instanceof Ageable age && age.getAge() >= age.getMaximumAge()) {
            progress(e.getPlayer(), "harvest", m.name(), false, 1);
        }
        progress(e.getPlayer(), "break", m.name(), false, 1);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent e) {
        progress(e.getPlayer(), "place", e.getBlockPlaced().getType().name(), false, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k != null && !(e.getEntity() instanceof Player)) {
            progress(k, "kill", e.getEntityType().name(), e.getEntity() instanceof Monster, 1);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            progress(e.getPlayer(), "fish", "*", false, 1);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        flush();
    }

    boolean command(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Chỉ người chơi xem được nhiệm vụ.");
            return true;
        }
        String key = "players." + p.getUniqueId();
        List<String> qs = todays(p.getUniqueId());
        p.sendMessage(c("&6&lNhiệm vụ hôm nay &7(đổi lúc 0 giờ)"));
        for (String qid : qs) {
            Quest q = pool.get(qid);
            if (q == null) {
                continue;
            }
            int done = data.getInt(key + ".progress." + qid, 0);
            int bars = 10 * Math.min(done, q.amount()) / q.amount();
            String bar = "&a" + "▮".repeat(bars) + "&8" + "▮".repeat(10 - bars);
            p.sendMessage(c((done >= q.amount() ? "&a✔ " : "&7• ") + "&f" + q.name() + " " + bar + " &7" + done + "/" + q.amount()
                    + (q.rewards().isEmpty() ? "" : " &8→ &7" + String.join("&7, ", rewards.describe(q.rewards())))));
        }
        List<String> bonus = rewards.describe(cfg.getStringList("complete-all-rewards"));
        if (!bonus.isEmpty()) {
            p.sendMessage(c("&7Xong hết: thưởng thêm &f" + String.join("&7, &f", bonus) + (data.getBoolean(key + ".bonus") ? " &a(đã nhận)" : "")));
        }
        return true;
    }
}
