package vn.duckymomo.core.paper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Kho lương công thành (/kholuong). Mỗi thị trấn Towny có một kho lương (điểm). Dân nộp nông sản, đồ ăn, cá câu (EvenMoreFish,
 * theo độ hiếm), món ăn (/nauan) vào kho. Lúc ĐANG có phiên đánh, mỗi người trong vùng vây ăn từ kho của THỊ TRẤN MÌNH:
 * kho còn thì được nuôi no (thanh đói đầy -> tự hồi máu như thường), kho cạn thì bị Đói. Config siege-supply:
 *  - points.<VẬT_LIỆU>: điểm mỗi cái; fish.<độ hiếm>: điểm mỗi con cá câu; dish-points: điểm mỗi món ăn.
 *  - max-per-town: kho tối đa; cost-per-player-minute: mỗi người đang đánh ăn bao nhiêu điểm/phút.
 * Sự kiện mùa (events.<id>.supply-multiplier, vd Hạn hán) nhân điểm NÔNG SẢN khi nộp (lương khan thì quý).
 * spoil-percent-per-day: kho hao hụt % mỗi ngày (lương để lâu hư), tính dần mỗi 10 phút theo thời gian thật; 0 = không hao.
 * Lưu: kholuong.yml (theo UUID thị trấn); ghi kholuong.json cho web.
 */
final class SiegeSupply {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final DuckyMoMoCore plugin;
    private final Map<Material, Double> points = new EnumMap<>(Material.class);
    private final Map<String, Double> fish = new HashMap<>();
    private final double dishPoints;
    private final double max;
    private final double costPerMinute;
    private final double spoil;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, Boolean> fedState = new HashMap<>();
    private boolean dirty;

    SiegeSupply(DuckyMoMoCore plugin, ConfigurationSection c) {
        this.plugin = plugin;
        ConfigurationSection p = c.getConfigurationSection("points");
        if (p != null) {
            for (String k : p.getKeys(false)) {
                Material m = Material.matchMaterial(k);
                if (m != null) {
                    points.put(m, p.getDouble(k));
                }
            }
        }
        ConfigurationSection f = c.getConfigurationSection("fish");
        if (f != null) {
            for (String k : f.getKeys(false)) {
                fish.put(k.toLowerCase(Locale.ROOT), f.getDouble(k));
            }
        }
        this.dishPoints = c.getDouble("dish-points", 15);
        this.max = Math.max(1, c.getDouble("max-per-town", 5000));
        this.costPerMinute = Math.max(0, c.getDouble("cost-per-player-minute", 2));
        this.spoil = Math.max(0, Math.min(100, c.getDouble("spoil-percent-per-day", 0)));
        this.file = new File(plugin.getDataFolder(), "kholuong.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    String describe() {
        return points.size() + " loại nông sản/đồ ăn, tối đa " + (long) max + " điểm/thị trấn, " + fmt(costPerMinute) + " điểm/người/phút khi đánh";
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::feedTick, 300L, 300L);   // 15 giây
        Bukkit.getScheduler().runTaskTimer(plugin, this::flush, 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::spoilTick, 600L, 12000L);   // 10 phút
        writeJson();
    }

    void stop() {
        flush();
    }

    private static Town townOf(Player p) {
        Town merc = Mercenary.supplyTown(p);   // lính đánh thuê: ăn kho lương thủ đô nước thuê
        if (merc != null) {
            return merc;
        }
        Resident r = TownyAPI.getInstance().getResident(p);
        return r == null ? null : r.getTownOrNull();
    }

    double stock(Town t) {
        return data.getDouble("kho." + t.getUUID() + ".diem", 0);
    }

    private void setStock(Town t, double v) {
        data.set("kho." + t.getUUID() + ".diem", Math.max(0, Math.min(max, v)));
        data.set("kho." + t.getUUID() + ".ten", t.getName());
        dirty = true;
    }

    /** Điểm của một món (0 = không nộp được). */
    double value(ItemStack it) {
        if (it == null || it.getType().isAir()) {
            return 0;
        }
        if (Dishes.isDish(it)) {
            return dishPoints;
        }
        String r = PetFish.rarity(it);
        if (r != null) {
            return fish.getOrDefault(r.toLowerCase(Locale.ROOT), 0.0);
        }
        if (it.hasItemMeta() && it.getItemMeta().hasCustomModelDataComponent()) {
            return 0;   // đồ đặc biệt của plugin khác: không nộp
        }
        double v = points.getOrDefault(it.getType(), 0.0);
        ConfigurationSection ev = Seasons.eventNow();
        return v * (ev == null ? 1.0 : ev.getDouble("supply-multiplier", 1.0));
    }

    /** Hao hụt theo thời gian thật kể từ lần tính trước (lưu hao-hut-luc, qua khởi động lại vẫn đúng). */
    private void spoilTick() {
        long now = System.currentTimeMillis();
        long last = data.getLong("hao-hut-luc", now);
        data.set("hao-hut-luc", now);
        dirty = true;
        if (spoil <= 0 || now <= last) {
            return;
        }
        double factor = Math.pow(1 - spoil / 100.0, (now - last) / 86_400_000.0);
        ConfigurationSection kho = data.getConfigurationSection("kho");
        if (kho != null) {
            for (String id : kho.getKeys(false)) {
                kho.set(id + ".diem", kho.getDouble(id + ".diem", 0) * factor);
            }
        }
    }

    private void feedTick() {
        if (!SiegeWarHook.battleActive()) {
            fedState.clear();
            return;
        }
        double cost = costPerMinute / 4.0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() != GameMode.SURVIVAL || !SiegeWarHook.inActiveSiegeZone(p.getLocation())) {
                fedState.remove(p.getUniqueId());
                continue;
            }
            Town t = townOf(p);
            boolean fed = t != null && stock(t) >= cost;
            if (fed) {
                setStock(t, stock(t) - cost);
                p.setFoodLevel(Math.min(20, p.getFoodLevel() + 3));
                p.setSaturation(Math.min(p.getFoodLevel(), p.getSaturation() + 2));
            } else {
                p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 320, 0, true, false, true));
            }
            Boolean was = fedState.put(p.getUniqueId(), fed);
            if (was == null || was != fed) {
                p.sendMessage(c(fed ? "&a🍚 Kho lương " + t.getName() + " nuôi quân: bạn được ăn no trong trận (kho còn " + (long) stock(t) + " điểm)."
                        : t == null ? "&c🍚 Bạn không thuộc thị trấn nào: không có kho lương, sẽ mau đói trong trận."
                                : "&c🍚 Kho lương " + t.getName() + " đã cạn: quân mau đói. Nộp thêm: &f/kholuong nop"));
            }
        }
    }

    boolean command(CommandSender s, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("top")) {
            List<Object[]> rows = new ArrayList<>();
            for (Town t : TownyAPI.getInstance().getTowns()) {
                double v = stock(t);
                if (v > 0) {
                    rows.add(new Object[] {t.getName(), v});
                }
            }
            rows.sort((a, b) -> Double.compare((Double) b[1], (Double) a[1]));
            s.sendMessage(c("&6&l🍚 Kho lương các thị trấn"));
            for (int i = 0; i < Math.min(10, rows.size()); i++) {
                s.sendMessage(c("&6" + (i + 1) + ". &f" + rows.get(i)[0] + " &7— &e" + (long) (double) (Double) rows.get(i)[1] + " điểm"));
            }
            if (rows.isEmpty()) {
                s.sendMessage(c("&7Chưa thị trấn nào có lương."));
            }
            return true;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage(c("&7Dùng trong game. /kholuong top xem các kho."));
            return true;
        }
        Town t = townOf(p);
        if (t == null) {
            p.sendMessage(c("&cBạn chưa thuộc thị trấn nào nên chưa có kho lương."));
            return true;
        }
        if (sub.equals("nop") || sub.equals("noptat")) {
            boolean all = sub.equals("noptat");
            ItemStack[] inv = p.getInventory().getStorageContents();
            double gained = 0;
            int items = 0;
            double room = max - stock(t);
            for (int i = 0; i < inv.length && room > 0; i++) {
                if (!all && i != p.getInventory().getHeldItemSlot()) {
                    continue;
                }
                ItemStack it = inv[i];
                if (all && (Dishes.isDish(it) || PetFish.rarity(it) != null)) {
                    continue;   // cá câu, món ăn là đồ quý: chỉ nộp khi cầm trên tay (/kholuong nop)
                }
                double v = value(it);
                if (v <= 0) {
                    continue;
                }
                int n = (int) Math.min(it.getAmount(), Math.floor(room / v));
                if (n <= 0) {
                    break;
                }
                it.setAmount(it.getAmount() - n);
                gained += n * v;
                room -= n * v;
                items += n;
            }
            p.getInventory().setStorageContents(inv);
            if (items == 0) {
                p.sendMessage(c(room <= 0 ? "&eKho lương " + t.getName() + " đã đầy (" + (long) max + " điểm)."
                        : all ? "&cTrong túi không có nông sản/đồ ăn nộp được. Cá câu, món ăn: cầm trên tay rồi /kholuong nop."
                                : "&cCầm nông sản, đồ ăn, cá câu hoặc món ăn trên tay rồi gõ lại (hoặc /kholuong noptat)."));
                return true;
            }
            setStock(t, stock(t) + gained);
            Achievements.add(p, "kholuong", gained);
            p.playSound(p.getLocation(), Sound.BLOCK_BARREL_CLOSE, 0.8f, 1f);
            ConfigurationSection ev = Seasons.eventNow();
            p.sendMessage(c("&a🍚 Đã nộp " + items + " món vào kho lương " + t.getName() + ": &f+" + fmt(gained) + " điểm &7(kho: "
                    + (long) stock(t) + "/" + (long) max + ")"
                    + (ev != null && ev.getDouble("supply-multiplier", 1.0) != 1.0 ? " &d" + ev.getString("name", "") + "&7: nông sản x"
                    + fmt(ev.getDouble("supply-multiplier")) : "")));
            return true;
        }
        double v = stock(t);
        p.sendMessage(c("&6&l🍚 Kho lương " + t.getName() + ": &e" + (long) v + "&7/" + (long) max + " điểm"));
        double perHour = costPerMinute * 60;
        p.sendMessage(c("&7Mỗi người đánh trong vùng vây ăn &f" + fmt(costPerMinute) + " điểm/phút &7(≈ " + (long) perHour
                + "/giờ). Kho này nuôi được khoảng &f" + (costPerMinute > 0 ? (long) (v / costPerMinute) : 0) + " phút-người&7."));
        if (spoil > 0) {
            p.sendMessage(c("&7Lương để lâu hư: kho hao &f" + fmt(spoil) + "%/ngày&7, nên nộp gần ngày công thành."));
        }
        p.sendMessage(c("&7Nộp: cầm trên tay &f/kholuong nop &7· cả túi (trừ cá câu, món ăn) &f/kholuong noptat &7· các kho &f/kholuong top"));
        p.sendMessage(c("&7Giá trị: cá câu và món ăn (/nauan) đáng nhiều điểm nhất; bánh mì, khoai nướng, thịt chín hơn đồ sống."));
        return true;
    }

    List<String> tab(String[] a) {
        if (a.length == 1) {
            return List.of("nop", "noptat", "top").stream().filter(x -> x.startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }

    private void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được kholuong.yml: " + e.getMessage());
        }
        writeJson();
    }

    private void writeJson() {
        JsonObject root = new JsonObject();
        root.addProperty("updated", System.currentTimeMillis() / 1000);
        root.addProperty("max", max);
        root.addProperty("cost_per_minute", costPerMinute);
        JsonArray arr = new JsonArray();
        ConfigurationSection kho = data.getConfigurationSection("kho");
        if (kho != null) {
            for (String id : kho.getKeys(false)) {
                JsonObject o = new JsonObject();
                o.addProperty("uuid", id);
                o.addProperty("ten", kho.getString(id + ".ten", "?"));
                o.addProperty("diem", Math.round(kho.getDouble(id + ".diem", 0)));
                arr.add(o);
            }
        }
        root.add("kho", arr);
        try {
            Files.writeString(new File(plugin.getDataFolder(), "kholuong.json").toPath(), root.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Không ghi được kholuong.json: " + e.getMessage());
        }
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
}
