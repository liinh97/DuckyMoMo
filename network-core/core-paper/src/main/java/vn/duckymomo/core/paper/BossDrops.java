package vn.duckymomo.core.paper;

import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Đồ rơi boss chọn từ THƯ VIỆN ĐỒ (20/10). Boss (máu, sát thương, kỹ năng) vẫn là file EliteMobs; đồ rơi ghi riêng ở
 * plugins/DuckyMoMoCore/boss-roi.yml (trang quản trị › Boss › Đồ rơi), đọc lại khi file đổi nên lưu trên web là có hiệu lực ngay:
 *   &lt;tên file boss&gt;.yml: [{do: &lt;mã thư viện&gt;, ti-le: 0.1–100 (%), so: số lượng, cho: top1 | top3 | tat-ca | ngau-nhien}]
 *   top1 / ngau-nhien: tung 1 lần, trúng thì người đánh nhiều nhất / một người đã đánh (ngẫu nhiên) nhận.
 *   top3 / tat-ca: mỗi người trong 3 người đánh nhiều nhất / mọi người đã đánh tung riêng.
 * Đồ phát thẳng vào túi (đầy thì rơi dưới chân), đủ công dụng của thư viện (kỹ năng Xưởng, hiệu ứng món ăn…).
 * Gắn EliteMobs bằng reflection (EliteMobDeathEvent), không cần EliteMobs lúc biên dịch.
 */
final class BossDrops implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration cache;
    private long stamp = -1;

    BossDrops(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "boss-roi.yml");
    }

    void start() {
        // EliteMobs nạp sau DuckyMoMoCore → đợi 5 giây mới gắn
        Bukkit.getScheduler().runTaskLater(plugin, this::hook, 100L);
    }

    void stop() {
        HandlerList.unregisterAll(this);
    }

    private YamlConfiguration load() {
        long s = file.lastModified() ^ file.length();
        if (cache == null || s != stamp) {
            cache = YamlConfiguration.loadConfiguration(file);
            stamp = s;
        }
        return cache;
    }

    @SuppressWarnings("unchecked")
    private void hook() {
        var em = Bukkit.getPluginManager().getPlugin("EliteMobs");
        if (em == null || !em.isEnabled()) {
            plugin.getLogger().warning("Đồ rơi boss: chưa có EliteMobs, tắt.");
            return;
        }
        try {
            ClassLoader cl = em.getClass().getClassLoader();
            Class<? extends Event> death = (Class<? extends Event>) Class.forName("com.magmaguy.elitemobs.api.EliteMobDeathEvent", true, cl);
            Class<?> boss = Class.forName("com.magmaguy.elitemobs.mobconstructor.custombosses.CustomBossEntity", true, cl);
            Method elite = death.getMethod("getEliteEntity");
            Method damagers = Class.forName("com.magmaguy.elitemobs.mobconstructor.EliteEntity", true, cl).getMethod("getDamagers");
            Method fields = boss.getMethod("getCustomBossesConfigFields");
            Bukkit.getPluginManager().registerEvent(death, this, EventPriority.MONITOR, (l, ev) -> {
                if (!death.isInstance(ev)) {
                    return;
                }
                try {
                    Object ent = elite.invoke(ev);
                    if (!boss.isInstance(ent)) {
                        return;
                    }
                    Object f = fields.invoke(ent);
                    String name = f == null ? null : String.valueOf(f.getClass().getMethod("getFilename").invoke(f));
                    onDeath(name, (Map<Player, Double>) damagers.invoke(ent));
                } catch (ReflectiveOperationException | RuntimeException e) {
                    plugin.getLogger().warning("Đồ rơi boss: lỗi đọc boss vừa chết: " + e);
                }
            }, plugin, true);
            plugin.getLogger().info("Đồ rơi boss: đã gắn EliteMobs (" + load().getKeys(false).size() + " boss có đồ rơi từ thư viện).");
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Đồ rơi boss: không gắn được EliteMobs: " + e);
        }
    }

    private void onDeath(String bossFile, Map<Player, Double> hits) {
        if (bossFile == null || hits == null || hits.isEmpty()) {
            return;
        }
        List<Map<?, ?>> drops = load().getMapList(bossFile);
        if (drops.isEmpty()) {
            return;
        }
        List<Player> ranked = new ArrayList<>();
        hits.entrySet().stream().sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey).filter(p -> p != null && p.isOnline()).forEach(ranked::add);
        if (ranked.isEmpty()) {
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (Map<?, ?> d : drops) {
            String id = String.valueOf(d.get("do"));
            double chance = d.get("ti-le") instanceof Number n ? n.doubleValue() : 100;
            int amount = d.get("so") instanceof Number n ? Math.max(1, n.intValue()) : 1;
            String who = String.valueOf(d.get("cho") == null ? "top1" : d.get("cho")).toLowerCase(Locale.ROOT);
            List<Player> rolls = switch (who) {
                case "tat-ca" -> ranked;
                case "top3" -> ranked.subList(0, Math.min(3, ranked.size()));
                case "ngau-nhien" -> List.of(ranked.get(rnd.nextInt(ranked.size())));
                default -> List.of(ranked.get(0));
            };
            for (Player p : rolls) {
                if (rnd.nextDouble(100) >= chance) {
                    continue;
                }
                give(p, id, amount, bossFile);
            }
        }
    }

    private void give(Player p, String id, int amount, String bossFile) {
        ItemLibrary lib = Shared.library;
        int left = amount;
        String name = lib == null ? id : lib.name(id);
        while (left > 0) {
            ItemStack it = lib == null ? null : lib.build(id, left);
            if (it == null) {
                plugin.getLogger().warning("Đồ rơi boss (" + bossFile + "): thư viện đồ không có \"" + id + "\"");
                return;
            }
            left -= it.getAmount();
            Rewards.hand(p, it);
        }
        p.sendMessage(LEGACY.deserialize("&6✦ Đồ rơi từ boss: &f").append(LEGACY.deserialize(name))
                .append(LEGACY.deserialize(amount > 1 ? " &7x" + amount : "")).decoration(TextDecoration.ITALIC, false));
        plugin.getLogger().info("Đồ rơi boss: " + p.getName() + " nhận " + id + " x" + amount + " (" + bossFile + ")");
    }
}
