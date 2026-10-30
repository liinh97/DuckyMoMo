package vn.duckymomo.core.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Khảm Tinh Luyện vào đồ set (/thoren kham, 27/10, chủ server: Tinh Luyện là đồ cày thật giá trị, khảm vào đồ).
 *  - Số ô theo set + cấp (kham.slots.<set>: một số cho mọi cấp hoặc danh sách theo cấp +0, +1…). Set không có trong slots = 0 ô.
 *  - Khảm: đưa 1 món Tinh Luyện (Refining, có dấu thật) vào ô trống của món đang cầm, luôn thành công, món mất.
 *    Tháo: tốn thao-xu xu, thao-vo % vỡ ngọc (mất), còn lại trả món về túi.
 *  - Mỗi loại ngọc một chỉ số, khác nhau giữa giáp (mũ/áo/quần/giày) và vũ khí (món khác); chỉ số giáp tính khi mặc, vũ khí khi cầm tay chính.
 *    Cộng cả người, mỗi chỉ số có trần (kham.cap). cham-mon tính riêng từng món (trần cap.cham-mon).
 *    mau (% máu tối đa) · day-lui (% kháng đẩy lùi) · giam-st (% giảm sát thương nhận) · lua (% giảm sát thương lửa) · st (% sát thương gây ra)
 *    toc-danh (% tốc đánh) · toc-chay (% tốc chạy) · kinh-nghiem (% kinh nghiệm nhặt) · hoi-mau (% hồi máu) · hut-mau (% sát thương gây ra
 *    thành máu) · dot (% cơ hội đốt mục tiêu 3 giây) · st-chay (% sát thương lên mục tiêu đang cháy; 27/10 Than đổi từ dot sang
 *    st-chay vì mọi vũ khí set có ô đều sẵn Lửa) · cham-mon (% cơ hội không mòn).
 *  - Hào quang: màu theo loại ngọc (gems.<mã>.rgb), KIỂU hạt theo phẩm (pham[].hat), thưa / nhỏ dần theo năng lượng.
 *  - Phẩm + năng lượng (27/10, chủ server): khảm ra phẩm ngẫu nhiên (kham.pham: Thường / Tốt / Hoàn Mỹ, % hiệu năng trong khoảng min..max,
 *    nhân vào chỉ số gốc). Ngọc có năng lượng (kham.nang-luong): giáp mất 1 mỗi lần người mặc trúng đòn từ quái / người (không tính ngã, cháy,
 *    độc…), vũ khí mất 1 mỗi con quái / người hạ được (trùm: vukhi-trum); không đánh nhau thì không hao;
 *    còn canh-bao % thì nhắc, cạn thì ngọc vỡ (ô trống lại). Nạp đầy: 1 Tinh Luyện cùng loại + nap-xu (đáng khi ngọc phẩm cao).
 *    Tháo ra giữ phẩm + năng lượng trên món Tinh Luyện trả về (PDC dmm_kham_ngoc), khảm lại dùng đúng các số đó.
 *  - Lưu trên món: PDC dmm_kham = từng ô "mã:phẩm:%:năng lượng" cách dấu phẩy ("" = trống; ngọc khảm trước 27/10 chỉ có "mã" = Thường 100%,
 *    đầy năng lượng), dmm_kham_n = số dòng lore khảm đã thêm ở cuối. Thợ Rèn dựng lại món (nâng cấp, cập nhật theo Thư viện) gọi carry().
 */
final class Kham implements Listener {

    static volatile Kham INSTANCE;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final List<String> ARMOR = List.of("mu", "ao", "quan", "giay");

    private record Gem(String id, String name, String color, org.bukkit.Color rgb, Map<String, Double> armor, Map<String, Double> weapon) { }

    /**
     * hat: kiểu hạt hào quang của phẩm, ghép bằng "+": cham (chấm màu), chuyen-mau (chấm đổi từ màu ngọc sang trắng), vet (vệt sáng có đuôi),
     * xoay (hạt xoáy màu kiểu hiệu ứng thuốc), xoan-oc (vệt xoắn ốc từ chân lên đầu), vong-dau (vòng sáng xoay trên đỉnh đầu).
     * density (pham[].mat-do): hệ số số hạt của phẩm.
     */
    private record Tier(String name, String color, int chance, int min, int max, String hat, double density) { }

    /** Một ô: mã ngọc ("" = trống), phẩm (chỉ số trong pham), % hiệu năng, năng lượng còn. */
    private record Sock(String code, int tier, int factor, int energy) {
        boolean empty() {
            return code.isEmpty();
        }

        String enc() {
            return code.isEmpty() ? "" : code + ":" + tier + ":" + factor + ":" + energy;
        }
    }

    private static final Sock EMPTY = new Sock("", 0, 100, 0);

    private final DuckyMoMoCore plugin;
    private final ThoRen thoRen;
    private final Map<String, List<Integer>> slots = new HashMap<>();
    private final Map<String, String> icons = new HashMap<>();
    private final Map<String, Gem> gems = new LinkedHashMap<>();
    private final Map<String, Double> cap = new HashMap<>();
    private final double unsocketCost;
    private final int breakChance;
    private final List<Tier> tiers = new ArrayList<>();
    private final int maxEnergy;
    private final double rechargeCost;
    private final int warnPct;
    private final boolean debug;
    private final int bossDrain;
    private final boolean aura;
    private final int auraRange;
    private final double auraCount;
    private final double auraSize;
    private long auraTick;
    private final NamespacedKey keyGems;
    private final NamespacedKey keyLoose;
    private final NamespacedKey keyLines;
    private final NamespacedKey keyMau;
    private final NamespacedKey keyDayLui;
    private final NamespacedKey keyTocDanh;
    private final NamespacedKey keyTocChay;
    private final Map<UUID, Map<String, Double>> totals = new HashMap<>();
    /** Năng lượng giáp chờ trừ (đòn đã trúng chưa ghi vào món): ghi mỗi lần trúng đòn thì món đổi dữ liệu -> game phát tiếng mặc giáp liên tục. */
    private final Map<UUID, int[]> pendingArmor = new HashMap<>();
    private static final org.bukkit.inventory.EquipmentSlot[] ARMOR_SLOTS = {org.bukkit.inventory.EquipmentSlot.HEAD,
            org.bukkit.inventory.EquipmentSlot.CHEST, org.bukkit.inventory.EquipmentSlot.LEGS, org.bukkit.inventory.EquipmentSlot.FEET};

    static final Map<String, String> STAT_VI = Map.ofEntries(Map.entry("mau", "% máu tối đa"), Map.entry("day-lui", "% kháng đẩy lùi"),
            Map.entry("giam-st", "% giảm sát thương nhận"), Map.entry("lua", "% giảm sát thương lửa"), Map.entry("st", "% sát thương"),
            Map.entry("toc-danh", "% tốc đánh"), Map.entry("toc-chay", "% tốc chạy"), Map.entry("kinh-nghiem", "% kinh nghiệm"),
            Map.entry("hoi-mau", "% hồi máu"), Map.entry("hut-mau", "% hút máu"), Map.entry("dot", "% cơ hội đốt mục tiêu"),
            Map.entry("st-chay", "% sát thương lên mục tiêu đang cháy"),
            Map.entry("cham-mon", "% chậm mòn"));

    Kham(DuckyMoMoCore plugin, ThoRen thoRen, ConfigurationSection c) {
        INSTANCE = this;
        this.plugin = plugin;
        this.thoRen = thoRen;
        this.unsocketCost = c.getDouble("thao-xu", 2000);
        this.breakChance = c.getInt("thao-vo", 50);
        this.maxEnergy = Math.max(1, c.getInt("nang-luong", 3000));
        this.rechargeCost = c.getDouble("nap-xu", 1000);
        this.warnPct = c.getInt("canh-bao", 10);
        this.debug = c.getBoolean("debug", false);
        this.bossDrain = Math.max(1, c.getInt("vukhi-trum", 10));
        this.aura = c.getBoolean("hao-quang.enabled", true);
        this.auraRange = c.getInt("hao-quang.ban-kinh", 32);
        this.auraCount = c.getDouble("hao-quang.so-hat", 2);
        this.auraSize = c.getDouble("hao-quang.co", 0.85);
        for (Map<?, ?> m : c.getMapList("pham")) {
            tiers.add(new Tier(String.valueOf(m.get("name")), m.get("color") == null ? "&7" : String.valueOf(m.get("color")),
                    m.get("ti-le") instanceof Number n ? n.intValue() : 1, m.get("min") instanceof Number a ? a.intValue() : 100,
                    m.get("max") instanceof Number b ? b.intValue() : 100, m.get("hat") == null ? "cham" : String.valueOf(m.get("hat")),
                    m.get("mat-do") instanceof Number d ? d.doubleValue() : 1.0));
        }
        if (tiers.isEmpty()) {
            tiers.add(new Tier("Thường", "&7", 1, 100, 100, "cham", 1.0));
        }
        ConfigurationSection s = c.getConfigurationSection("slots");
        if (s != null) {
            for (String set : s.getKeys(false)) {
                slots.put(set, s.isList(set) ? s.getIntegerList(set) : List.of(s.getInt(set)));
            }
        }
        ConfigurationSection ic = c.getConfigurationSection("icons");
        if (ic != null) {
            for (String set : ic.getKeys(false)) {
                icons.put(set, ic.getString(set, "◆◇"));
            }
        }
        ConfigurationSection g = c.getConfigurationSection("gems");
        if (g != null) {
            for (String id : g.getKeys(false)) {
                ConfigurationSection x = g.getConfigurationSection(id);
                if (x != null) {
                    gems.put(id, new Gem(id, x.getString("name", id), x.getString("color", "&f"), rgb(x.getString("rgb", "#FFFFFF")),
                            stats(x.getConfigurationSection("giap")), stats(x.getConfigurationSection("vukhi"))));
                }
            }
        }
        ConfigurationSection cp = c.getConfigurationSection("cap");
        if (cp != null) {
            for (String k : cp.getKeys(false)) {
                cap.put(k, cp.getDouble(k));
            }
        }
        keyGems = new NamespacedKey(plugin, "dmm_kham");
        keyLoose = new NamespacedKey(plugin, "dmm_kham_ngoc");
        keyLines = new NamespacedKey(plugin, "dmm_kham_n");
        keyMau = new NamespacedKey(plugin, "kham_mau");
        keyDayLui = new NamespacedKey(plugin, "kham_daylui");
        keyTocDanh = new NamespacedKey(plugin, "kham_tocdanh");
        keyTocChay = new NamespacedKey(plugin, "kham_tocchay");
        // vũ khí đổi tay, kéo đồ trong túi: tính lại mỗi giây (rẻ: chỉ đọc PDC 5 món mỗi người)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::refresh), 40L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::flushArmor), 600L, 600L);
        if (aura) {
            int every = Math.max(2, c.getInt("hao-quang.chu-ky", 4));
            Bukkit.getScheduler().runTaskTimer(plugin, this::auraTick, 40L, every);
        }
    }

    // ---------------------------------------------------------------- hào quang (người khác cũng thấy; tắt được bằng /caidat "Hiệu ứng hạt người khác")

    private static final double[] AURA_Y = {1.85, 1.3, 0.85, 0.2};   // mũ, áo, quần, giày

    /** Người xem hạt của owner: chính owner + người gần đó không tắt "hat" trong /caidat. */
    private List<Player> viewers(Player owner) {
        List<Player> out = new ArrayList<>();
        double r2 = (double) auraRange * auraRange;
        for (Player v : owner.getWorld().getPlayers()) {
            if (v == owner || v.getLocation().distanceSquared(owner.getLocation()) <= r2 && PlayerSettings.on(v, "hat")) {
                out.add(v);
            }
        }
        return out;
    }

    private void dust(List<Player> vs, org.bukkit.Location l, org.bukkit.Color col, float size, int n, double spread) {
        org.bukkit.Particle.DustOptions o = new org.bukkit.Particle.DustOptions(col, size);
        for (Player v : vs) {
            v.spawnParticle(org.bukkit.Particle.DUST, l, n, spread, spread, spread, 0, o);
        }
    }

    /**
     * Mỗi ngọc một hạt màu riêng (theo loại) xoay quanh người ở tầng của món (vũ khí cạnh tay phải). Phẩm đổi KIỂU hạt (pham[].hat:
     * cham / chuyen-mau / vet), số hạt như nhau; năng lượng cạn dần thì thưa, nhỏ dần.
     */
    private void auraTick() {
        auraTick++;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.isInvisible() || p.hasPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY)) {
                continue;
            }
            List<Object[]> spots = new ArrayList<>();   // {Sock, chỗ: 0..3 giáp, 4 vũ khí}
            for (int i = 0; i < ARMOR_SLOTS.length; i++) {
                ItemStack it = p.getInventory().getItem(ARMOR_SLOTS[i]);
                if (hasGems(it) && isArmor(info(it))) {
                    for (Sock sk : gemsOf(it)) {
                        if (!sk.empty()) {
                            spots.add(new Object[] {sk, i});
                        }
                    }
                }
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hasGems(hand) && info(hand) != null && !isArmor(info(hand))) {
                for (Sock sk : gemsOf(hand)) {
                    if (!sk.empty()) {
                        spots.add(new Object[] {sk, 4});
                    }
                }
            }
            if (spots.isEmpty()) {
                continue;
            }
            List<Player> vs = viewers(p);
            org.bukkit.Location base = p.getLocation();
            double yaw = Math.toRadians(base.getYaw());
            for (int k = 0; k < spots.size(); k++) {
                Sock sk = (Sock) spots.get(k)[0];
                int where = (int) spots.get(k)[1];
                Gem gem = gems.get(sk.code());
                if (gem == null) {
                    continue;
                }
                double frac = Math.max(0, Math.min(1, (double) sk.energy() / maxEnergy));
                double want = auraCount * tier(sk).density() * (0.25 + 0.75 * frac);   // số hạt kỳ vọng mỗi nhịp (mat-do của phẩm)
                int n = (int) want + (rnd.nextDouble() < want - (int) want ? 1 : 0);
                if (n <= 0) {
                    continue;
                }
                float size = (float) (auraSize * (0.45 + 0.55 * frac));
                double a = auraTick * 0.35 + k * 2.1;
                org.bukkit.Location l, next;
                if (where < 4) {
                    l = base.clone().add(Math.cos(a) * 0.55, AURA_Y[where], Math.sin(a) * 0.55);
                    next = base.clone().add(Math.cos(a + 1.2) * 0.55, AURA_Y[where] + 0.15, Math.sin(a + 1.2) * 0.55);
                } else {   // vũ khí: cạnh tay phải
                    double rx = -Math.cos(yaw), rz = -Math.sin(yaw), fx = -Math.sin(yaw), fz = Math.cos(yaw);
                    l = base.clone().add(rx * 0.42 + fx * 0.35, 1.0 + rnd.nextDouble() * 0.25, rz * 0.42 + fz * 0.35);
                    next = l.clone().add(0, 0.45, 0);
                }
                double spread = where < 4 ? 0.04 : 0.08;
                for (String hat : tier(sk).hat().split("\\+")) {
                    switch (hat.trim()) {
                        case "chuyen-mau" -> {
                            org.bukkit.Particle.DustTransition o = new org.bukkit.Particle.DustTransition(gem.rgb(), org.bukkit.Color.WHITE, size);
                            for (Player v : vs) {
                                v.spawnParticle(org.bukkit.Particle.DUST_COLOR_TRANSITION, l, n, spread, spread, spread, 0, o);
                            }
                        }
                        case "xoay" -> {
                            for (Player v : vs) {
                                v.spawnParticle(org.bukkit.Particle.ENTITY_EFFECT, l, n, spread * 2, spread * 2, spread * 2, 0, gem.rgb());
                            }
                        }
                        case "vet" -> {
                            for (int j = 0; j < n; j++) {
                                org.bukkit.Location from = l.clone().add(rnd.nextGaussian() * spread, rnd.nextGaussian() * spread, rnd.nextGaussian() * spread);
                                org.bukkit.Particle.Trail o = new org.bukkit.Particle.Trail(next, gem.rgb(), 12 + (int) (8 * frac));
                                for (Player v : vs) {
                                    v.spawnParticle(org.bukkit.Particle.TRAIL, from, 1, 0, 0, 0, 0, o);
                                }
                            }
                        }
                        case "xoan-oc" -> {   // cả người: mỗi ngọc một sợi xoắn đi lên, lệch pha nhau
                            for (int j = 0; j < n; j++) {
                                double ph = (auraTick * 0.06 + k * 0.137 + j * 0.5) % 1.0;
                                double ang = ph * Math.PI * 4 + k * 2.1;
                                org.bukkit.Location from = base.clone().add(Math.cos(ang) * 0.65, ph * 2.0, Math.sin(ang) * 0.65);
                                org.bukkit.Location to = base.clone().add(Math.cos(ang + 0.9) * 0.65, ph * 2.0 + 0.3, Math.sin(ang + 0.9) * 0.65);
                                org.bukkit.Particle.Trail o = new org.bukkit.Particle.Trail(to, gem.rgb(), 14 + (int) (8 * frac));
                                for (Player v : vs) {
                                    v.spawnParticle(org.bukkit.Particle.TRAIL, from, 1, 0, 0, 0, 0, o);
                                }
                            }
                        }
                        case "vong-dau" -> {   // vòng sáng trên đỉnh đầu: các ngọc chia nhau điểm trên vòng
                            for (int j = 0; j < n; j++) {
                                double ang = auraTick * 0.25 + (k * 2 + j) * (Math.PI * 2 / Math.max(1, spots.size() * 2));
                                org.bukkit.Location h = base.clone().add(Math.cos(ang) * 0.38, 2.25, Math.sin(ang) * 0.38);
                                dust(vs, h, gem.rgb(), (float) (size * 0.8), 1, 0);
                            }
                        }
                        default -> dust(vs, l, gem.rgb(), size, n, spread);
                    }
                }
            }
        }
    }

    /** Ngọc vỡ: bùng hạt màu ngọc + tiếng thạch anh vỡ, người quanh đó cũng thấy / nghe. */
    private void breakFx(Player p, Gem gem) {
        org.bukkit.Location l = p.getLocation().add(0, 1.1, 0);
        List<Player> vs = viewers(p);
        if (gem != null) {
            dust(vs, l, gem.rgb(), 1.4f, 30, 0.45);
        }
        for (Player v : vs) {
            v.spawnParticle(org.bukkit.Particle.CRIT, l, 18, 0.4, 0.4, 0.4, 0.25);
        }
        p.getWorld().playSound(l, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.2f, 0.8f);
        p.getWorld().playSound(l, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.2f);
    }

    private static org.bukkit.Color rgb(String hex) {
        try {
            return org.bukkit.Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16));
        } catch (RuntimeException e) {
            return org.bukkit.Color.WHITE;
        }
    }

    private static Map<String, Double> stats(ConfigurationSection s) {
        Map<String, Double> m = new LinkedHashMap<>();
        if (s != null) {
            for (String k : s.getKeys(false)) {
                m.put(k, s.getDouble(k));
            }
        }
        return m;
    }

    private static Component c(String s) {
        return Tip.ns(LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false));
    }

    private static String trim(double d) {
        double r = Math.round(d * 10) / 10.0;
        return r == Math.rint(r) ? String.valueOf((long) r) : String.valueOf(r).replace('.', ',');
    }

    // ---------------------------------------------------------------- dữ liệu trên món

    /** {set, món, cấp} của đồ set (null nếu không phải). */
    private String[] info(ItemStack it) {
        String id = ItemLibrary.idOf(it);
        return id == null ? null : thoRen.setInfo(id);
    }

    int slotsOf(ItemStack it) {
        String[] in = info(it);
        if (in == null) {
            return 0;
        }
        List<Integer> l = slots.get(in[0]);
        if (l == null || l.isEmpty()) {
            return 0;
        }
        int lv = Integer.parseInt(in[2]);
        return Math.max(0, l.get(Math.min(lv, l.size() - 1)));
    }

    private static boolean isArmor(String[] in) {
        return in != null && ARMOR.contains(in[1]);
    }

    private Sock parse(String s) {
        if (s == null || s.isEmpty()) {
            return EMPTY;
        }
        String[] a = s.split(":");
        if (a.length < 4) {
            return new Sock(a[0], 0, 100, maxEnergy);   // ngọc khảm trước 27/10: Thường 100%, đầy năng lượng
        }
        try {
            return new Sock(a[0], Math.min(Integer.parseInt(a[1]), tiers.size() - 1), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
        } catch (NumberFormatException e) {
            return new Sock(a[0], 0, 100, maxEnergy);
        }
    }

    /** Ngọc từng ô (đủ số ô của món; ô trống = EMPTY). */
    private List<Sock> gemsOf(ItemStack it) {
        int n = slotsOf(it);
        List<Sock> out = new ArrayList<>();
        if (it != null && it.hasItemMeta()) {
            String raw = it.getItemMeta().getPersistentDataContainer().get(keyGems, PersistentDataType.STRING);
            if (raw != null && !raw.isEmpty()) {
                for (String x : raw.split(",", -1)) {
                    out.add(parse(x));
                }
            }
        }
        while (out.size() < n) {
            out.add(EMPTY);
        }
        return out;
    }

    private Tier tier(Sock s) {
        return tiers.get(Math.max(0, Math.min(s.tier(), tiers.size() - 1)));
    }

    private String bar(int energy) {
        int full = (int) Math.ceil(10.0 * Math.max(0, energy) / maxEnergy);
        String col = energy * 100 <= warnPct * maxEnergy ? "&c" : energy * 2 <= maxEnergy ? "&e" : "&a";
        return col + "▮".repeat(full) + "&8" + "▮".repeat(10 - full);
    }

    private int pct(int energy) {
        return (int) Math.ceil(100.0 * Math.max(0, energy) / maxEnergy);
    }

    /** Tung phẩm theo tỉ lệ, % hiệu năng ngẫu nhiên trong khoảng của phẩm. */
    private Sock roll(String code) {
        int total = tiers.stream().mapToInt(Tier::chance).sum();
        int r = ThreadLocalRandom.current().nextInt(Math.max(1, total));
        int i = 0;
        for (; i < tiers.size() - 1; i++) {
            r -= tiers.get(i).chance();
            if (r < 0) {
                break;
            }
        }
        Tier t = tiers.get(i);
        return new Sock(code, i, ThreadLocalRandom.current().nextInt(t.min(), t.max() + 1), maxEnergy);
    }

    private static final String LINE_PREFIX = "◈ Khảm ";
    private static final String TITLE = "KHẢM NGỌC";
    /** Ký hiệu ô khảm theo set (đầy, trống) — cùng cặp với dòng cấp nâng của set (Thánh quang ☀☼, Huyền lực ✦✧…); config kham.icons.<set>. */
    private static final Map<String, String> ICONS = Map.of("tinhanh", "★☆", "caocap", "▰▱", "huyenthoai", "✦✧", "thanthoai", "◆◇", "thanthanh", "●○");
    private static final java.util.regex.Pattern HEADER = java.util.regex.Pattern.compile("^((?:&[0-9a-fk-or]|&#[0-9a-fA-F]{6})*)(\\S+) ((?:&[0-9a-fk-or]|&#[0-9a-fA-F]{6})*)\\S.*$");

    /**
     * Ghi ngọc + dòng khảm. 28/10 (chủ server: khối khảm ở cuối món, mô tả dài thì không thấy): MỘT dòng
     * "◈ Khảm 1/4: ◆Sắt Tốt ◇ ◇ ◇ · ⚡95%" đặt sau dòng trống đầu tiên (ngay trên khối "THIÊN UY…"); chỉ số chi tiết xem /thoren kham.
     * Khối cũ ở cuối (dmm_kham_n dòng) gỡ đi khi ghi lại.
     */
    private void write(ItemStack it, List<Sock> list) {
        int n = slotsOf(it);
        ItemMeta meta = it.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        Integer old = meta.getPersistentDataContainer().get(keyLines, PersistentDataType.INTEGER);
        if (old != null && old > 0 && old <= lore.size()) {
            lore = new ArrayList<>(lore.subList(0, lore.size() - old));
        }
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        int at = -1;
        for (int i = 0; i < lore.size(); i++) {
            String pl = plain.serialize(lore.get(i));
            if (pl.startsWith(LINE_PREFIX) || pl.contains(" " + TITLE)) {
                lore.remove(i);
                if (i < lore.size() && plain.serialize(lore.get(i)).isBlank() && pl.contains(" " + TITLE)) {
                    lore.remove(i);   // dòng trống đặt kèm dưới dòng khảm
                }
                at = firstSection(lore) >= 0 ? -1 : i;   // có tiêu đề mục thì tính lại chỗ (sửa đồ lỡ đặt sai), không thì giữ chỗ cũ
                break;
            }
        }
        if (n > 0) {
            if (at < 0) {   // ngay trên tiêu đề mục đầu tiên ("▸ THIÊN UY"…); đồ kiểu cũ: sau dòng trống đầu tiên
                at = firstSection(lore);
                for (int i = 0; i < lore.size() && at < 0; i++) {
                    if (plain.serialize(lore.get(i)).isBlank()) {
                        at = i + 1;
                    }
                }
            }
            if (at < 0 || at > lore.size()) {
                at = lore.size();
            }
            long filled = list.stream().filter(x -> !x.empty()).count();
            // tiêu đề cùng kiểu mục của set (28/10, chủ server: dòng khảm lệch kiểu, xấu): lấy ký hiệu + màu dòng tiêu đề ngay dưới
            String sym = "◈", c1 = "&d&l", c2 = "";
            if (at < lore.size()) {
                java.util.regex.Matcher m = HEADER.matcher(LEGACY.serialize(lore.get(at)));
                if (m.matches() && m.group(2).length() <= 2) {
                    sym = m.group(2);
                    c1 = m.group(1).isEmpty() ? c1 : m.group(1);
                    c2 = m.group(3);
                }
            }
            String tc = "&a&l";   // 28/10 tối (chủ server: màu mè như Tân Thủ): mục khảm một màu riêng, xanh lá
            c1 = "&a&l";
            sym = "▸";
            String[] in = info(it);
            String ic = in == null ? "◆◇" : icons.getOrDefault(in[0], ICONS.getOrDefault(in[0], "◆◇"));
            int[] cp = ic.codePoints().toArray();
            String full = "◆", empty = "◇";   // 28/10 tối (chủ server): ô khảm nhỏ, hình viên ngọc, mọi set như nhau
            StringBuilder b = new StringBuilder(c1 + sym + " " + tc + TITLE + "&r");
            int minE = Integer.MAX_VALUE;
            List<String> names = new ArrayList<>();
            for (int i = 0; i < n; i++) {   // ô dạng [●][○][○] như thanh Loading của Tân Thủ, tô theo màu ngọc
                Sock sk = i < list.size() ? list.get(i) : EMPTY;
                Gem gem = gems.get(sk.code());
                if (gem == null) {
                    b.append(" &7").append(empty);
                } else {
                    b.append(" ").append(gem.color()).append(full);
                    names.add(gem.color() + gem.name());
                    minE = Math.min(minE, sk.energy());
                }
            }
            if (!names.isEmpty()) {
                b.append(" ").append(String.join("&7, ", names));
            }
            // 28/10 chiều (chủ server): không đếm "1/4", "⚡95%" (dài, xấu) — dãy ô sáng/tối như thanh "Loading" của Tân Thủ;
            // phẩm, chỉ số, năng lượng xem ở /thoren kham; chỉ nhắc khi năng lượng sắp cạn
            if (filled > 0 && minE * 100 <= warnPct * maxEnergy) {
                b.append(" &c⚡ sắp cạn");
            }
            lore.add(at, c(b.toString()));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(keyGems, PersistentDataType.STRING, String.join(",", list.stream().map(Sock::enc).toList()));
        meta.getPersistentDataContainer().set(keyLines, PersistentDataType.INTEGER, 0);
        it.setItemMeta(meta);
    }

    /** Dòng tiêu đề mục đầu tiên ("▸ …") trong mô tả; -1 nếu không có. */
    private static int firstSection(List<Component> lore) {
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        for (int i = 0; i < lore.size(); i++) {
            if (plain.serialize(lore.get(i)).startsWith("▸ ")) {
                return i;
            }
        }
        return -1;
    }

    /** Đồ còn dòng khảm kiểu cũ (khối nhiều dòng ở cuối, hoặc "◈ Khảm 0/4"): ghi lại theo kiểu mục của set. true = đã đổi món. */
    static boolean migrate(ItemStack it) {
        Kham k = INSTANCE;
        if (k == null || it == null || !it.hasItemMeta()) {
            return false;
        }
        Integer old = it.getItemMeta().getPersistentDataContainer().get(k.keyLines, PersistentDataType.INTEGER);
        List<Component> lore = it.getItemMeta().lore();
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        boolean oneLineOld = lore != null && lore.stream().anyMatch(l -> plain.serialize(l).startsWith(LINE_PREFIX));   // "◈ Khảm 0/4" sáng 28/10
        boolean misplaced = false;   // 28/10 chiều: dòng khảm lỡ đặt trên tiêu đề set (sau dòng trống mới thêm dưới khối phù phép)
        if (lore != null) {
            int sec = firstSection(lore);
            for (int i = 0; i < lore.size(); i++) {
                if (plain.serialize(lore.get(i)).contains(" " + TITLE)) {
                    int nx = i + 1;
                    while (nx < lore.size() && plain.serialize(lore.get(nx)).isBlank()) {
                        nx++;
                    }
                    misplaced = sec >= 0 && nx != sec;
                }
            }
        }
        if (((old == null || old <= 0) && !oneLineOld && !misplaced) || k.slotsOf(it) <= 0) {
            return false;
        }
        k.write(it, k.gemsOf(it));
        return true;
    }

    private String effect(Gem gem, boolean armor) {
        return effect(gem, armor, 100);
    }

    private String effect(Gem gem, boolean armor, int factor) {
        List<String> s = new ArrayList<>();
        (armor ? gem.armor() : gem.weapon()).forEach((k, v) -> s.add("&a+" + trim(v * factor / 100.0) + "&7" + STAT_VI.getOrDefault(k, " " + k)));
        return s.isEmpty() ? "&8(không có tác dụng ở loại đồ này)" : String.join("&7, ", s);
    }

    /** Món có ô mà chưa có khối lore khảm (mới rèn, món xem trước trong menu, đồ cũ): vẽ khối "◇ Ô trống" để biết món có mấy ô
     *  (28/10: trước chỉ hiện sau lần khảm đầu). true = đã đổi món. */
    static boolean blank(ItemStack it) {
        Kham k = INSTANCE;
        if (k == null || it == null || !it.hasItemMeta() || it.getItemMeta().getPersistentDataContainer().has(k.keyGems, PersistentDataType.STRING)) {
            return false;
        }
        int n = k.slotsOf(it);
        if (n <= 0) {
            return false;
        }
        k.write(it, new ArrayList<>(java.util.Collections.nCopies(n, EMPTY)));
        return true;
    }

    /** Thợ Rèn dựng lại món: chép ngọc từ món cũ sang món mới (ô thừa nếu món mới ít ô hơn: trả ngọc về túi người chơi). */
    static void carry(ItemStack from, ItemStack to, Player owner) {
        Kham k = INSTANCE;
        if (k == null || from == null || to == null || !from.hasItemMeta()) {
            return;
        }
        String raw = from.getItemMeta().getPersistentDataContainer().get(k.keyGems, PersistentDataType.STRING);
        if (raw == null || raw.replace(",", "").isEmpty()) {
            return;
        }
        int n = k.slotsOf(to);
        List<Sock> keep = new ArrayList<>();
        for (String x : raw.split(",", -1)) {
            Sock g = k.parse(x);
            if (g.empty()) {
                continue;
            }
            if (keep.size() < n) {
                keep.add(g);
            } else if (owner != null) {
                k.giveGem(owner, g);
            }
        }
        while (keep.size() < n) {
            keep.add(EMPTY);
        }
        if (n > 0) {
            k.write(to, keep);
        }
    }

    /** Trả ngọc về túi: món Tinh Luyện mang theo phẩm + năng lượng (khảm lại dùng đúng các số này). */
    private void giveGem(Player p, Sock g) {
        Refining r = Shared.refining;
        ItemStack it = r == null ? null : r.build(g.code(), 1);
        if (it == null) {
            return;
        }
        Tier t = tier(g);
        it.editMeta(meta -> {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(c("&5◈ Ngọc đã khảm: " + t.color() + t.name() + " " + g.factor() + "%"));
            lore.add(c("&7   năng lượng " + bar(g.energy()) + " &7" + pct(g.energy()) + "%"));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(keyLoose, PersistentDataType.STRING, g.tier() + ":" + g.factor() + ":" + g.energy());
        });
        Rewards.hand(p, it);
    }

    /** Món Tinh Luyện là ngọc đã tháo (mang phẩm + năng lượng): Thợ Rèn, đơn nhà buôn lấy món trơn trước. */
    static boolean isLooseGem(ItemStack it) {
        Kham k = INSTANCE;
        return k != null && it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(k.keyLoose, PersistentDataType.STRING);
    }

    private String looseTag(ItemStack it) {
        return it == null || !it.hasItemMeta() ? "" : it.getItemMeta().getPersistentDataContainer().getOrDefault(keyLoose, PersistentDataType.STRING, "");
    }

    // ---------------------------------------------------------------- menu (/thoren kham): làm trên món đang cầm tay chính

    void open(Player p) {
        flushArmor(p);
        ItemStack hand = p.getInventory().getItemInMainHand();
        int n = slotsOf(hand);
        if (n <= 0) {
            InfoMenu.of("&5◈ Khảm Tinh Luyện").accent(Material.PURPLE_STAINED_GLASS_PANE).top(guide(p))
                    .add(Tip.of("&cCầm đồ set có ô khảm trên tay").accent("&c").desc("Đang cầm: " + (info(hand) == null ? "không phải đồ set" : "đồ set chưa có ô khảm") + ".")
                            .line().items(slotTable()).apply(new ItemStack(Material.BARRIER)))
                    .open(p);
            return;
        }
        String[] in = info(hand);
        String handId = ItemLibrary.idOf(hand);
        List<Sock> list = gemsOf(hand);
        InfoMenu m = InfoMenu.of("&5◈ Khảm: " + thoRen.labelOf(handId)).accent(Material.PURPLE_STAINED_GLASS_PANE).top(guide(p));
        for (int i = 0; i < n; i++) {
            int slot = i;
            Sock sk = list.get(i);
            Gem gem = gems.get(sk.code());
            if (gem == null) {
                m.add(Tip.of("&7◇ Ô trống " + (i + 1)).accent("&d").desc("Bấm để chọn Tinh Luyện khảm vào ô này.")
                        .click("Chọn ngọc").apply(new ItemStack(Material.GRAY_STAINED_GLASS_PANE)), pl -> pick(pl, handId, slot));
            } else {
                ItemStack icon = Shared.refining != null ? Shared.refining.build(gem.id(), 1) : null;
                Tier t = tier(sk);
                m.add(Tip.of("&d◆ " + gem.color() + gem.name() + " " + t.color() + t.name() + " " + sk.factor() + "%").accent("&d")
                        .desc(effect(gem, isArmor(in), sk.factor()))
                        .line().kv("Năng lượng", bar(sk.energy()) + " &f" + pct(sk.energy()) + "%")
                        .click("Bấm để nạp năng lượng / tháo").apply(icon != null ? icon : new ItemStack(Material.AMETHYST_SHARD)), pl -> slotMenu(pl, handId, slot));
            }
        }
        m.open(p);
    }

    private ItemStack guide(Player p) {
        Tip t = Tip.of("&5◈ &fKhảm Tinh Luyện").accent("&d")
                .desc("Cầm đồ set trên tay chính rồi mở menu này. Mỗi ô khảm một món Tinh Luyện (món bị lấy đi), ra phẩm ngẫu nhiên.",
                        "Giáp tính khi mặc, vũ khí tính khi cầm tay chính. Nâng cấp đồ: ngọc tự chuyển sang món mới.",
                        "Ngọc hao năng lượng khi đánh nhau (giáp: trúng đòn quái / người; vũ khí: mỗi con hạ được, trùm hao nhiều hơn), cạn thì vỡ. Không dùng thì không hao.")
                .line().kv("Nạp đầy", "1 Tinh Luyện cùng loại + " + money(rechargeCost))
                .kv("Tháo ngọc", money(unsocketCost) + ", " + breakChance + "% vỡ (giữ phẩm, năng lượng)").line();
        StringBuilder ph = new StringBuilder();
        int total = tiers.stream().mapToInt(Tier::chance).sum();
        for (Tier tr : tiers) {
            ph.append(ph.length() > 0 ? "&7 · " : "").append(tr.color()).append(tr.name()).append(" &7").append(Math.round(100.0 * tr.chance() / Math.max(1, total)))
                    .append("% (").append(tr.min()).append("–").append(tr.max()).append("%)");
        }
        t.kv("Phẩm", ph.toString()).line();
        Map<String, Double> now = compute(p);
        t.head("Đang có (cả người)");
        if (now.isEmpty()) {
            t.item("&7chưa có");
        }
        now.forEach((k, v) -> t.item("&a+" + trim(v) + "&7" + STAT_VI.getOrDefault(k, " " + k)
                + (cap.containsKey(k) && v >= cap.get(k) ? " &c(chạm trần)" : "")));
        t.line();
        for (Gem g : gems.values()) {
            t.item(g.color() + g.name() + "&7: giáp " + effect(g, true) + "&7 · vũ khí " + effect(g, false));
        }
        return t.apply(new ItemStack(Material.AMETHYST_CLUSTER));
    }

    private List<String> slotTable() {
        List<String> out = new ArrayList<>();
        slots.forEach((set, l) -> out.add(thoRen.setName(set) + "&7: " + (l.size() == 1 ? l.get(0) + " ô" : l.toString().replace(", ", "/") + " ô theo cấp")));
        return out;
    }

    private boolean sameHand(Player p, String handId) {
        ItemStack h = p.getInventory().getItemInMainHand();
        if (!handId.equals(ItemLibrary.idOf(h))) {
            p.sendMessage(c("&cĐồ trên tay đã đổi, mở lại /thoren kham."));
            p.closeInventory();
            return false;
        }
        return true;
    }

    private void pick(Player p, String handId, int slot) {
        if (!sameHand(p, handId)) {
            return;
        }
        String[] in = info(p.getInventory().getItemInMainHand());
        Map<String, Integer> have = new LinkedHashMap<>();   // "mã|thẻ" (thẻ = phẩm:%:năng lượng của ngọc đã tháo, "" = món mới)
        Refining r = Shared.refining;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            String g = r == null ? null : r.idOf(it);
            if (g != null && gems.containsKey(g)) {
                have.merge(g + "|" + looseTag(it), it.getAmount(), Integer::sum);
            }
        }
        InfoMenu m = InfoMenu.of("&5◈ Chọn ngọc cho ô " + (slot + 1)).accent(Material.PURPLE_STAINED_GLASS_PANE).back(this::open);
        if (have.isEmpty()) {
            m.add(Tip.of("&cTrong túi chưa có Tinh Luyện nào khảm được").accent("&c")
                    .desc("Làm ở /tinhluyen: đổi khối khoáng sản lấy món Tinh Luyện.").apply(new ItemStack(Material.BARRIER)));
        }
        have.forEach((key, n) -> {
            String g = key.substring(0, key.indexOf('|')), tag = key.substring(key.indexOf('|') + 1);
            Gem gem = gems.get(g);
            ItemStack icon = r.build(g, 1);
            Tip tp = Tip.of(gem.color() + gem.name()).accent("&d");
            if (tag.isEmpty()) {
                tp.desc("Gốc: " + effect(gem, isArmor(in))).kv("Phẩm", "ngẫu nhiên khi khảm");
            } else {
                Sock sk = parse(g + ":" + tag);
                tp.desc(effect(gem, isArmor(in), sk.factor())).kv("Phẩm", tier(sk).color() + tier(sk).name() + " " + sk.factor() + "% &8(ngọc đã tháo)")
                        .kv("Năng lượng", bar(sk.energy()) + " &f" + pct(sk.energy()) + "%");
            }
            m.add(tp.kv("Đang có", String.valueOf(n)).click("Bấm để khảm").apply(icon != null ? icon : new ItemStack(Material.AMETHYST_SHARD)),
                    pl -> socket(pl, handId, slot, g, tag));
        });
        m.open(p);
    }

    private void socket(Player p, String handId, int slot, String g, String tag) {
        if (!sameHand(p, handId)) {
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getAmount() > 1) {   // chồng nhiều món (đồ admin phát): khảm 1 ngọc thành nhiều món có ngọc
            p.sendMessage(c("&cTách riêng từng món rồi mới khảm."));
            p.closeInventory();
            return;
        }
        List<Sock> list = gemsOf(hand);
        if (slot >= list.size() || !list.get(slot).empty()) {
            open(p);
            return;
        }
        Refining r = Shared.refining;
        ItemStack[] inv = p.getInventory().getStorageContents();
        boolean took = false;
        for (int i = 0; i < inv.length && !took; i++) {
            if (r != null && g.equals(r.idOf(inv[i])) && tag.equals(looseTag(inv[i]))) {
                inv[i].setAmount(inv[i].getAmount() - 1);
                took = true;
            }
        }
        if (!took) {
            p.sendMessage(c("&cKhông còn món Tinh Luyện này trong túi."));
            open(p);
            return;
        }
        p.getInventory().setStorageContents(inv);
        Sock sk = tag.isEmpty() ? roll(g) : parse(g + ":" + tag);
        list.set(slot, sk);
        write(hand, list);
        p.getInventory().setItemInMainHand(hand);
        Tier t = tier(sk);
        boolean top = sk.tier() == tiers.size() - 1 && tag.isEmpty();
        p.playSound(p.getLocation(), top ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.2f);
        dust(viewers(p), p.getLocation().add(0, 1.1, 0), gems.get(g).rgb(), 1.0f + 0.3f * sk.tier(), 16, 0.35);
        p.sendMessage(c("&d◈ Đã khảm " + gems.get(g).color() + gems.get(g).name() + " &dvào ô " + (slot + 1) + ": " + t.color() + t.name() + " " + sk.factor() + "%&d."));
        plugin.getLogger().info("Khảm: " + p.getName() + " " + sk.enc() + " -> " + handId + " ô " + (slot + 1));
        DailyQuests.feature(p, "kham");
        refresh(p);
        open(p);
    }

    private void slotMenu(Player p, String handId, int slot) {
        if (!sameHand(p, handId)) {
            return;
        }
        Sock sk = gemsOf(p.getInventory().getItemInMainHand()).get(slot);
        Gem gem = gems.get(sk.code());
        if (gem == null) {
            return;
        }
        Refining r = Shared.refining;
        int have = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (r != null && sk.code().equals(r.idOf(it)) && !isLooseGem(it)) {
                have += it.getAmount();
            }
        }
        Tier t = tier(sk);
        Tip nap = Tip.of("&a⚡ Nạp đầy năng lượng").accent("&a").desc(gem.name() + " " + t.color() + t.name() + " " + sk.factor() + "%&7: năng lượng "
                        + bar(sk.energy()) + " &f" + pct(sk.energy()) + "%")
                .kv("Cần", "1 " + gem.color() + "Tinh Luyện " + gem.name() + " &8(có " + have + ")&7 + " + money(rechargeCost));
        if (sk.energy() >= maxEnergy) {
            nap.deny("Đang đầy");
        } else if (have <= 0) {
            nap.deny("Thiếu Tinh Luyện " + gem.name());
        } else {
            nap.click("Bấm để nạp");
        }
        InfoMenu.of("&5◈ " + gem.name() + " ô " + (slot + 1)).accent(Material.PURPLE_STAINED_GLASS_PANE).back(this::open)
                .add(nap.apply(new ItemStack(Material.GLOWSTONE_DUST)), pl -> recharge(pl, handId, slot))
                .add(Tip.of("&cTháo ngọc").accent("&c").desc("Tốn " + money(unsocketCost) + ". &c" + breakChance + "% ngọc vỡ mất hẳn&7, còn lại trả về túi (giữ phẩm, năng lượng).")
                        .click("Bấm để tháo").apply(new ItemStack(Material.SHEARS)), pl -> remove(pl, handId, slot))
                .open(p);
    }

    private void recharge(Player p, String handId, int slot) {
        if (!sameHand(p, handId)) {
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        List<Sock> list = gemsOf(hand);
        Sock sk = slot < list.size() ? list.get(slot) : EMPTY;
        if (sk.empty() || sk.energy() >= maxEnergy) {
            slotMenu(p, handId, slot);
            return;
        }
        Refining r = Shared.refining;
        ItemStack[] inv = p.getInventory().getStorageContents();
        int idx = -1;
        for (int i = 0; i < inv.length && idx < 0; i++) {
            if (r != null && sk.code().equals(r.idOf(inv[i])) && !isLooseGem(inv[i])) {
                idx = i;
            }
        }
        if (idx < 0) {
            p.sendMessage(c("&cCần 1 Tinh Luyện " + gems.get(sk.code()).name() + " (món mới, không phải ngọc đã tháo) trong túi."));
            return;
        }
        if (rechargeCost > 0 && (!VaultEconomy.available() || !VaultEconomy.withdraw(p, rechargeCost))) {
            p.sendMessage(c("&cKhông đủ " + money(rechargeCost) + " để nạp."));
            return;
        }
        inv[idx].setAmount(inv[idx].getAmount() - 1);
        p.getInventory().setStorageContents(inv);
        list.set(slot, new Sock(sk.code(), sk.tier(), sk.factor(), maxEnergy));
        write(hand, list);
        p.getInventory().setItemInMainHand(hand);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.4f);
        p.sendMessage(c("&a⚡ Đã nạp đầy năng lượng cho " + gems.get(sk.code()).name() + " ô " + (slot + 1) + "."));
        plugin.getLogger().info("Khảm: " + p.getName() + " nạp " + sk.enc() + " trên " + handId);
        slotMenu(p, handId, slot);
    }

    private void remove(Player p, String handId, int slot) {
        if (!sameHand(p, handId)) {
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        List<Sock> list = gemsOf(hand);
        Sock g = slot < list.size() ? list.get(slot) : EMPTY;
        if (g.empty()) {
            open(p);
            return;
        }
        if (unsocketCost > 0 && (!VaultEconomy.available() || !VaultEconomy.withdraw(p, unsocketCost))) {
            p.sendMessage(c("&cKhông đủ " + money(unsocketCost) + " để tháo."));
            return;
        }
        list.set(slot, EMPTY);
        write(hand, list);
        p.getInventory().setItemInMainHand(hand);
        boolean broke = ThreadLocalRandom.current().nextInt(100) < breakChance;
        Gem gem = gems.get(g.code());
        String nm = gem == null ? g.code() : gem.name();
        if (broke) {
            breakFx(p, gem);
            p.sendMessage(c("&c✖ " + nm + " vỡ khi tháo."));
        } else {
            giveGem(p, g);
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
            p.sendMessage(c("&a✔ Tháo được " + nm + " nguyên vẹn (giữ phẩm, năng lượng)."));
        }
        plugin.getLogger().info("Khảm: " + p.getName() + " tháo " + g.enc() + " khỏi " + handId + (broke ? " (vỡ)" : ""));
        refresh(p);
        open(p);
    }

    private static String money(double v) {
        return VaultEconomy.format(v);
    }

    // ---------------------------------------------------------------- chỉ số

    private Map<String, Double> compute(Player p) {
        Map<String, Double> t = new HashMap<>();
        List<ItemStack> worn = new ArrayList<>(Arrays.asList(p.getInventory().getArmorContents()));
        ItemStack hand = p.getInventory().getItemInMainHand();
        for (ItemStack it : worn) {
            add(t, it, true);
        }
        String[] hi = info(hand);
        if (hi != null && !isArmor(hi)) {
            add(t, hand, false);
        }
        t.replaceAll((k, v) -> cap.containsKey(k) ? Math.min(v, cap.get(k)) : v);
        return t;
    }

    private void add(Map<String, Double> t, ItemStack it, boolean armorSlot) {
        String[] in = info(it);
        if (in == null || isArmor(in) != armorSlot) {
            return;
        }
        for (Sock sk : gemsOf(it)) {
            Gem gem = gems.get(sk.code());
            if (gem != null) {
                (armorSlot ? gem.armor() : gem.weapon()).forEach((k, v) -> {
                    if (!k.equals("cham-mon")) {
                        t.merge(k, v * sk.factor() / 100.0, Double::sum);
                    }
                });
            }
        }
    }

    private double stat(Player p, String k) {
        Map<String, Double> t = totals.get(p.getUniqueId());
        return t == null ? 0 : t.getOrDefault(k, 0.0);
    }

    void refresh(Player p) {
        Map<String, Double> t = compute(p);
        Map<String, Double> old = totals.get(p.getUniqueId());
        if (t.equals(old == null ? Map.of() : old)) {
            return;
        }
        if (t.isEmpty()) {
            totals.remove(p.getUniqueId());
        } else {
            totals.put(p.getUniqueId(), t);
        }
        apply(p, Attribute.MAX_HEALTH, keyMau, t.getOrDefault("mau", 0.0) / 100.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        apply(p, Attribute.KNOCKBACK_RESISTANCE, keyDayLui, t.getOrDefault("day-lui", 0.0) / 100.0, AttributeModifier.Operation.ADD_NUMBER);
        apply(p, Attribute.ATTACK_SPEED, keyTocDanh, t.getOrDefault("toc-danh", 0.0) / 100.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        apply(p, Attribute.MOVEMENT_SPEED, keyTocChay, t.getOrDefault("toc-chay", 0.0) / 100.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    }

    private static void apply(Player p, Attribute attr, NamespacedKey key, double amount, AttributeModifier.Operation op) {
        AttributeInstance a = p.getAttribute(attr);
        if (a == null) {
            return;
        }
        a.removeModifier(key);
        if (amount != 0) {
            a.addTransientModifier(new AttributeModifier(key, amount, op, EquipmentSlotGroup.ANY));
        }
        if (attr == Attribute.MAX_HEALTH && p.getHealth() > a.getValue()) {
            p.setHealth(a.getValue());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        flushArmor(e.getPlayer());
        totals.remove(e.getPlayer().getUniqueId());
        pendingArmor.remove(e.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- năng lượng

    private boolean hasGems(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return false;
        }
        String raw = it.getItemMeta().getPersistentDataContainer().get(keyGems, PersistentDataType.STRING);
        return raw != null && !raw.replace(",", "").isEmpty();
    }

    /** Trừ n năng lượng mọi ngọc trên món; nhắc khi qua ngưỡng canh-bao, vỡ khi cạn. true nếu món có ngọc (đã ghi). */
    private boolean drain(Player p, ItemStack it, int n) {
        if (n <= 0 || !hasGems(it)) {
            return false;
        }
        List<Sock> list = gemsOf(it);
        boolean any = false, broke = false;
        String item = thoRen.labelOf(ItemLibrary.idOf(it));
        for (int i = 0; i < list.size(); i++) {
            Sock sk = list.get(i);
            if (sk.empty()) {
                continue;
            }
            any = true;
            int left = sk.energy() - n;
            Gem gem = gems.get(sk.code());
            String nm = gem == null ? sk.code() : gem.color() + gem.name();
            if (left <= 0) {
                list.set(i, EMPTY);
                broke = true;
                breakFx(p, gem);
                p.sendMessage(c("&c✖ Ngọc " + nm + " &ctrên &f" + item + " &cđã cạn năng lượng và vỡ. &7Khảm ngọc mới: /thoren kham"));
                plugin.getLogger().info("Khảm: " + p.getName() + " vỡ ngọc " + sk.enc() + " (cạn năng lượng) trên " + ItemLibrary.idOf(it));
            } else {
                if ((long) sk.energy() * 100 > (long) warnPct * maxEnergy && (long) left * 100 <= (long) warnPct * maxEnergy) {
                    p.sendActionBar(c("&e⚡ Ngọc " + nm + " &etrên &f" + item + " &esắp cạn năng lượng (" + pct(left) + "%). &7Nạp ở /thoren kham"));
                }
                list.set(i, new Sock(sk.code(), sk.tier(), sk.factor(), left));
            }
        }
        if (!any) {
            return false;
        }
        write(it, list);
        if (broke) {
            Bukkit.getScheduler().runTask(plugin, () -> refresh(p));
        }
        return true;
    }

    /** Ghi năng lượng giáp đã dồn vào từng món đang mặc. */
    void flushArmor(Player p) {
        int[] pend = pendingArmor.remove(p.getUniqueId());
        if (pend == null) {
            return;
        }
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            if (pend[i] > 0) {
                ItemStack it = p.getInventory().getItem(ARMOR_SLOTS[i]);
                boolean ok = drain(p, it, pend[i]);
                if (ok) {
                    p.getInventory().setItem(ARMOR_SLOTS[i], it);
                }
                if (debug) {
                    plugin.getLogger().info("Khảm debug: ghi " + pend[i] + " vào " + ARMOR_SLOTS[i] + " của " + p.getName() + " -> " + ok + " "
                            + (it == null || !it.hasItemMeta() ? "" : it.getItemMeta().getPersistentDataContainer().get(keyGems, PersistentDataType.STRING)));
                }
            }
        }
    }

    /**
     * Người mặc trúng đòn từ quái / người (cận chiến, tên, nổ do thực thể): mỗi món giáp có ngọc -1 năng lượng (dồn lại, ghi định kỳ).
     * Ngã, cháy, độc, đói, chết đuối không tính (27/10, chủ server: không đánh nhau thì không hao).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player p) || e.getFinalDamage() <= 0 || e.getDamager() == p) {
            return;
        }
        int[] pend = null;
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            if (hasGems(p.getInventory().getItem(ARMOR_SLOTS[i]))) {
                if (pend == null) {
                    pend = pendingArmor.computeIfAbsent(p.getUniqueId(), k -> new int[ARMOR_SLOTS.length]);
                }
                pend[i]++;
            }
        }
        if (debug) {
            plugin.getLogger().info("Khảm debug: " + p.getName() + " trúng đòn " + e.getCause() + " " + e.getFinalDamage() + " -> dồn "
                    + (pend == null ? "không có giáp khảm" : Arrays.toString(pend)));
        }
    }

    private boolean sameGear(ItemStack a, ItemStack b) {
        if (a == null || b == null || !a.hasItemMeta() || !b.hasItemMeta()) {
            return false;
        }
        return java.util.Objects.equals(ItemLibrary.idOf(a), ItemLibrary.idOf(b))
                && java.util.Objects.equals(a.getItemMeta().getPersistentDataContainer().get(keyGems, PersistentDataType.STRING),
                        b.getItemMeta().getPersistentDataContainer().get(keyGems, PersistentDataType.STRING));
    }

    /**
     * Thay / tháo giáp: phần năng lượng dồn của chỗ đó thuộc món cũ -> trừ vào chính món cũ (tìm trên con trỏ chuột, trong túi; tháo giáp
     * liên tục không né được hao). Món đã rơi / đổi chủ ngay thì bỏ phần dồn đó (tối đa 30 giây).
     */
    @EventHandler
    public void onArmorSwap(com.destroystokyo.paper.event.player.PlayerArmorChangeEvent e) {
        Player p = e.getPlayer();
        int[] pend = pendingArmor.get(p.getUniqueId());
        if (pend == null || e.getOldItem() == null || sameGear(e.getOldItem(), e.getNewItem())) {
            return;   // cùng món (chỉ đổi độ bền / lore): mòn giáp cũng phát sự kiện này
        }
        int i = java.util.Arrays.asList(ARMOR_SLOTS).indexOf(e.getSlot());
        if (i < 0 || pend[i] <= 0) {
            return;
        }
        int n = pend[i];
        pend[i] = 0;
        ItemStack old = e.getOldItem();
        Bukkit.getScheduler().runTask(plugin, () -> {   // món cũ tới chỗ mới (con trỏ / túi) sau sự kiện
            if (!p.isOnline()) {
                return;
            }
            ItemStack cur = p.getItemOnCursor();
            if (sameGear(old, cur)) {
                drain(p, cur, n);
                p.setItemOnCursor(cur);
                return;
            }
            ItemStack[] inv = p.getInventory().getStorageContents();
            for (int k = 0; k < inv.length; k++) {
                if (sameGear(old, inv[k])) {
                    drain(p, inv[k], n);
                    p.getInventory().setItem(k, inv[k]);
                    return;
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) {
            return;
        }
        double red = stat(p, "giam-st");
        EntityDamageEvent.DamageCause cause = e.getCause();
        if (cause == EntityDamageEvent.DamageCause.FIRE || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA || cause == EntityDamageEvent.DamageCause.HOT_FLOOR) {
            red += stat(p, "lua");
        }
        if (red > 0) {
            e.setDamage(e.getDamage() * Math.max(0, 1 - red / 100.0));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Player p = e.getDamager() instanceof Player pl ? pl
                : e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player pl2 ? pl2 : null;
        if (p == null || !(e.getEntity() instanceof LivingEntity target)) {
            return;
        }
        double st = stat(p, "st") + (target.getFireTicks() > 0 ? stat(p, "st-chay") : 0);
        if (st > 0) {
            e.setDamage(e.getDamage() * (1 + st / 100.0));
        }
        double dot = stat(p, "dot");
        if (dot > 0 && ThreadLocalRandom.current().nextDouble(100) < dot) {
            target.setFireTicks(Math.max(target.getFireTicks(), 60));
        }
        double hut = stat(p, "hut-mau");
        if (hut > 0) {
            double heal = e.getFinalDamage() * hut / 100.0;
            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            if (max != null && heal > 0) {
                p.setHealth(Math.min(max.getValue(), p.getHealth() + heal));
            }
        }
    }

    /**
     * Vũ khí hao theo số con hạ được (27/10): quái / người -1, trùm (EliteMobs tên 『N』) -bossDrain. Vũ khí cầm tay chính lúc hạ
     * (cung bắn hạ cũng tính cung đang cầm). Đánh trượt, chém nhiều nhát một con không hao thêm.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(org.bukkit.event.entity.EntityDeathEvent e) {
        Player p = e.getEntity().getKiller();
        if (p == null || !(e.getEntity() instanceof org.bukkit.entity.Enemy || e.getEntity() instanceof Player)) {
            return;
        }
        ItemStack w = p.getInventory().getItemInMainHand();
        String[] in = info(w);
        if (in == null || isArmor(in)) {
            return;
        }
        Component name = e.getEntity().customName();
        boolean boss = name != null && net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name).contains("『");
        if (drain(p, w, boss ? bossDrain : 1)) {
            p.getInventory().setItemInMainHand(w);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent e) {
        if (e.getEntity() instanceof Player p && stat(p, "hoi-mau") > 0) {
            e.setAmount(e.getAmount() * (1 + stat(p, "hoi-mau") / 100.0));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExp(PlayerExpChangeEvent e) {
        double k = stat(e.getPlayer(), "kinh-nghiem");
        if (k > 0 && e.getAmount() > 0) {
            double v = e.getAmount() * (1 + k / 100.0);
            int whole = (int) v;
            e.setAmount(whole + (ThreadLocalRandom.current().nextDouble() < v - whole ? 1 : 0));
        }
    }

    /** Chậm mòn: tính riêng từng món (ngọc Đồng trên chính món đó), chạy sau độ mòn theo cấp của Thợ Rèn. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWear(PlayerItemDamageEvent e) {
        String[] in = info(e.getItem());
        if (in == null) {
            return;
        }
        double pct = 0;
        for (Sock sk : gemsOf(e.getItem())) {
            Gem gem = gems.get(sk.code());
            if (gem != null) {
                pct += (isArmor(in) ? gem.armor() : gem.weapon()).getOrDefault("cham-mon", 0.0) * sk.factor() / 100.0;
            }
        }
        pct = Math.min(pct, cap.getOrDefault("cham-mon", 50.0));
        if (pct <= 0) {
            return;
        }
        int keep = 0;
        for (int i = 0; i < e.getDamage(); i++) {
            if (ThreadLocalRandom.current().nextDouble(100) >= pct) {
                keep++;
            }
        }
        e.setDamage(keep);
    }

    String describe() {
        return gems.size() + " loại ngọc, " + slots.size() + " set có ô, tháo " + money(unsocketCost) + " / vỡ " + breakChance + "%";
    }
}
