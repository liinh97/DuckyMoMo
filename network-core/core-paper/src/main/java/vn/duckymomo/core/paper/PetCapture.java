package vn.duckymomo.core.paper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Bắt pet MyPet theo XÁC SUẤT (Bang Hội Chiến, config pet-capture). MyPet tự bắt khi đánh con vật yếu máu bằng dây xích; ở đây
 * mỗi loài trong pet-config.yml có thêm yêu cầu "Permission:duckymomo.pet.batduoc". Mỗi lần đánh đủ điều kiện (cầm dây xích,
 * có quyền bắt loài đó, không đang dẫn pet, con vật còn ≤ 10% máu sau cú đánh) là MỘT LẦN THỬ: quay xác suất, trúng thì cấp
 * quyền tạm đúng lúc MyPet xử lý cú đánh này (MyPet nghe ở MONITOR, mình ở HIGHEST) rồi gỡ ngay tick sau.
 *  - chance-percent: xác suất mặc định; group-chance.<nhóm loài>: riêng từng nhóm (nhóm lấy từ cây kỹ năng, khoá DmmNhom).
 *  - attempts-per-mob: mỗi con chỉ thử được ngần ấy lần, hết thì con vật "hoảng" không bắt được nữa (lưu trên con vật).
 *  - cooldown-seconds: chờ giữa 2 lần thử của một người (chống bấm liên tục).
 *  - season-bonus.<mùa>.<nhóm>: cộng thêm % trong mùa đó (vd mùa mưa Thủy tộc +15), mùa lấy từ module Mùa (Seasons.now()).
 *  - charm-bonus: Bùa bắt pet (/buabatpet, dấu PDC dmm_bua) cầm TAY TRÁI lúc thử bắt: +% và mất 1 bùa mỗi lần thử.
 *    Có từ giải thi câu cá (EvenMoreFish COMMAND:buabatpet give {player} N) hoặc admin phát.
 * Loài bắt bằng "Tamed" (sói, mèo, ngựa, lạc đà...: phải tự thuần hoá trước) không quay xác suất: con của mình thì cấp quyền luôn.
 * Loài không có LowHp/Tamed (golem, Creaking...) để MyPet tự xét, không cấp quyền.
 */
final class PetCapture implements Listener {

    static final String PERM = "duckymomo.pet.batduoc";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final DuckyMoMoCore plugin;
    private final int chance;
    private final int attempts;
    private final long cooldownMs;
    private final Map<String, Integer> groupChance = new HashMap<>();
    private final Map<String, Map<String, Integer>> seasonBonus = new HashMap<>();   // mùa -> nhóm -> % cộng thêm
    private final Map<String, String> typeGroup = new HashMap<>();      // loài MyPet (vd PolarBear) -> mã nhóm (thulon)
    private final Map<String, String> groupName = new HashMap<>();      // mã nhóm -> tên Việt
    private final Map<UUID, Long> lastTry = new HashMap<>();
    private final java.util.Set<String> lowHp = new java.util.HashSet<>();   // loài bắt kiểu "đánh yếu máu" (pet-config.yml)
    private final java.util.Set<String> tamed = new java.util.HashSet<>();   // loài bắt kiểu "đã thuần hoá"
    private final NamespacedKey triesKey;
    private final NamespacedKey charmKey;
    private final int charmBonus;
    private Method byEntityTypeName;
    private Method hasActive;
    private Object petManager;

    PetCapture(DuckyMoMoCore plugin, ConfigurationSection c) {
        this.plugin = plugin;
        this.chance = clamp(c.getInt("chance-percent", 30));
        this.attempts = Math.max(1, c.getInt("attempts-per-mob", 3));
        this.cooldownMs = Math.max(0, (long) (c.getDouble("cooldown-seconds", 2) * 1000));
        ConfigurationSection g = c.getConfigurationSection("group-chance");
        if (g != null) {
            for (String k : g.getKeys(false)) {
                groupChance.put(k.toLowerCase(Locale.ROOT), clamp(g.getInt(k)));
            }
        }
        ConfigurationSection sb = c.getConfigurationSection("season-bonus");
        if (sb != null) {
            for (String season : sb.getKeys(false)) {
                ConfigurationSection one = sb.getConfigurationSection(season);
                if (one == null) {
                    continue;
                }
                Map<String, Integer> m = new HashMap<>();
                for (String g2 : one.getKeys(false)) {
                    m.put(g2.toLowerCase(Locale.ROOT), one.getInt(g2));
                }
                seasonBonus.put(season, m);
            }
        }
        this.triesKey = new NamespacedKey(plugin, "batpet_lanthu");
        this.charmKey = new NamespacedKey(plugin, "dmm_bua");
        this.charmBonus = Math.max(0, c.getInt("charm-bonus", 25));
        loadGroups();
        loadRequirements();
        Plugin mp = Bukkit.getPluginManager().getPlugin("MyPet");
        try {
            ClassLoader cl = mp.getClass().getClassLoader();
            byEntityTypeName = Class.forName("de.Keyle.MyPet.api.entity.MyPetType", true, cl).getMethod("byEntityTypeName", String.class);
            petManager = Class.forName("de.Keyle.MyPet.MyPetApi", true, cl).getMethod("getMyPetManager").invoke(null);
            hasActive = petManager.getClass().getMethod("hasActiveMyPet", Player.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Bắt pet theo xác suất: không đọc được MyPet (" + e + "), tắt.");
            byEntityTypeName = null;
        }
    }

    boolean ready() {
        return byEntityTypeName != null;
    }

    String describe() {
        return chance + "% mặc định, " + groupChance.size() + " nhóm riêng, " + attempts + " lần thử mỗi con, chờ " + cooldownMs / 1000.0 + " giây";
    }

    /** Nhóm loài đọc từ cây kỹ năng MyPet do tools/mypet/sinh-cay-ky-nang.py sinh (MobTypes + DmmNhom). */
    private void loadGroups() {
        File dir = new File(plugin.getDataFolder().getParentFile(), "MyPet/skilltrees");
        File[] files = dir.listFiles((d, n) -> n.endsWith(".st.json"));
        if (files == null) {
            return;
        }
        for (File f : files) {
            try {
                JsonObject o = JsonParser.parseString(Files.readString(f.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
                if (!o.has("DmmNhom") || !o.has("MobTypes")) {
                    continue;
                }
                JsonObject g = o.getAsJsonObject("DmmNhom");
                String ma = g.get("ma").getAsString();
                groupName.put(ma, g.get("ten").getAsString());
                JsonArray types = o.getAsJsonArray("MobTypes");
                for (JsonElement t : types) {
                    typeGroup.put(t.getAsString().toLowerCase(Locale.ROOT), ma);
                }
            } catch (IOException | RuntimeException ignored) {
                // file hỏng: bỏ qua, loài đó dùng xác suất mặc định
            }
        }
    }

    private void loadRequirements() {
        File f = new File(plugin.getDataFolder().getParentFile(), "MyPet/pet-config.yml");
        ConfigurationSection pets = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f).getConfigurationSection("MyPet.Pets");
        if (pets == null) {
            return;
        }
        for (String t : pets.getKeys(false)) {
            for (String r : pets.getStringList(t + ".LeashRequirements")) {
                String n = r.split(":", 2)[0].trim();
                if (n.equalsIgnoreCase("LowHp")) {
                    lowHp.add(t.toLowerCase(Locale.ROOT));
                } else if (n.equalsIgnoreCase("Tamed")) {
                    tamed.add(t.toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    /** Xác suất bắt (%) của một loài MyPet. */
    int chanceFor(String petType) {
        String g = typeGroup.get(petType.toLowerCase(Locale.ROOT));
        int base = g != null && groupChance.containsKey(g) ? groupChance.get(g) : chance;
        return clamp(base + bonusNow(g));
    }

    /** % cộng thêm của nhóm trong mùa hiện tại. */
    int bonusNow(String group) {
        if (group == null) {
            return 0;
        }
        Map<String, Integer> m = seasonBonus.get(Seasons.now());
        ConfigurationSection ev = Seasons.eventNow();   // sự kiện mùa (Lũ…) cộng thêm
        return (m == null ? 0 : m.getOrDefault(group, 0)) + (ev == null ? 0 : ev.getInt("capture-bonus." + group, 0));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target) || target instanceof Player || PetCombat.isPet(target) || target.isDead()) {
            return;
        }
        Player p;
        if (event.getDamager() instanceof Player pl) {
            if (pl.getInventory().getItemInMainHand().getType() != Material.LEAD) {
                return;
            }
            p = pl;
        } else if (event.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player pl && pr.hasMetadata("MyPetLeashItem")) {
            p = pl;   // bắt từ xa (dây xích gắn vào tên), nếu MyPet bật
        } else {
            return;
        }
        String type = petType(target);
        if (type == null || !p.hasPermission("MyPet.leash." + type) || isActive(p)) {
            return;
        }
        String key = type.toLowerCase(Locale.ROOT);
        if (tamed.contains(key)) {
            if (target instanceof org.bukkit.entity.Tameable tm && tm.isTamed() && tm.getOwner() != null
                    && tm.getOwner().getUniqueId().equals(p.getUniqueId())) {
                grant(p);   // con mình tự thuần hoá: bắt luôn như trước
            }
            return;
        }
        if (!lowHp.contains(key)) {
            return;
        }
        double max = target.getAttribute(Attribute.MAX_HEALTH) != null ? target.getAttribute(Attribute.MAX_HEALTH).getValue() : target.getHealth();
        if ((target.getHealth() - event.getDamage()) / max > 0.1) {
            return;   // chưa đủ yếu: MyPet cũng không bắt, không tính lần thử
        }
        int used = target.getPersistentDataContainer().getOrDefault(triesKey, PersistentDataType.INTEGER, 0);
        if (used >= attempts) {
            p.sendActionBar(c("&cCon này đã hoảng sợ, không bắt được nữa. Tìm con khác."));
            return;
        }
        long now = System.currentTimeMillis();
        long wait = lastTry.getOrDefault(p.getUniqueId(), 0L) + cooldownMs - now;
        if (wait > 0) {
            p.sendActionBar(c("&eChờ &f" + String.format(Locale.ROOT, "%.1f", wait / 1000.0) + " giây &erồi quăng dây xích tiếp."));
            return;
        }
        lastTry.put(p.getUniqueId(), now);
        target.getPersistentDataContainer().set(triesKey, PersistentDataType.INTEGER, used + 1);
        int ch = chanceFor(type);
        ItemStack off = p.getInventory().getItemInOffHand();
        if (isCharm(off)) {
            off.setAmount(off.getAmount() - 1);
            ch = clamp(ch + charmBonus);
            p.sendMessage(c("&b✦ Bùa bắt pet: &f+" + charmBonus + "% &7lần này (còn " + off.getAmount() + " bùa)."));
        }
        if (ThreadLocalRandom.current().nextInt(100) < ch) {
            grant(p);
            return;
        }
        int left = attempts - used - 1;
        target.getWorld().spawnParticle(Particle.SMOKE, target.getLocation().add(0, target.getHeight() / 2, 0), 12, 0.3, 0.3, 0.3, 0.02);
        p.playSound(target.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.7f, 1.4f);
        p.sendActionBar(c("&c✘ Trượt! &7Con vật vùng ra (xác suất &f" + ch + "%&7). "
                + (left > 0 ? "Còn &f" + left + " &7lần thử với con này." : "&cCon này đã hoảng, hết lần thử.")));
    }

    boolean isCharm(ItemStack it) {
        return it != null && it.hasItemMeta() && "batpet".equals(it.getItemMeta().getPersistentDataContainer().get(charmKey, PersistentDataType.STRING));
    }

    ItemStack charm(int amount) {
        ItemStack it = new ItemStack(Material.NAUTILUS_SHELL, amount);
        org.bukkit.inventory.meta.ItemMeta m = it.getItemMeta();
        m.displayName(c("&b&l✦ Bùa bắt pet"));
        m.lore(java.util.List.of(c("&7Cầm ở &ftay trái &7khi quăng dây xích bắt pet:"), c("&a+" + charmBonus + "% &7xác suất, mất 1 bùa mỗi lần thử."),
                c("&8Phần thưởng thi câu cá")));
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(charmKey, PersistentDataType.STRING, "batpet");
        it.setItemMeta(m);
        return it;
    }

    /** /buabatpet: xem cách dùng; admin: /buabatpet give <người> [số]. */
    boolean charmCommand(org.bukkit.command.CommandSender s, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("give")) {
            if (!s.hasPermission("duckymomo.pet.admin")) {
                s.sendMessage(c("&cBạn không có quyền phát bùa."));
                return true;
            }
            Player t = Bukkit.getPlayerExact(args[1]);
            int n = 1;
            try {
                n = args.length > 2 ? Math.max(1, Math.min(64, Integer.parseInt(args[2]))) : 1;
            } catch (NumberFormatException ignored) {
                // số sai: phát 1
            }
            if (t == null) {
                s.sendMessage(c("&cKhông thấy " + args[1] + " online."));
                return true;
            }
            Rewards.hand(t, charm(n));
            t.sendMessage(c("&b✦ Bạn nhận " + n + " Bùa bắt pet. &7Cầm tay trái khi bắt pet: +" + charmBonus + "%."));
            s.sendMessage(c("&aĐã phát " + n + " bùa cho " + t.getName() + "."));
            return true;
        }
        s.sendMessage(c("&b&l✦ Bùa bắt pet: &7cầm ở &ftay trái &7khi quăng dây xích bắt pet: &a+" + charmBonus
                + "% &7xác suất, mất 1 bùa mỗi lần thử. Có từ giải thi câu cá cuối tuần."));
        return true;
    }

    /** Quyền tạm cho đúng cú đánh này: MyPet xét ở MONITOR cùng sự kiện, tick sau gỡ. */
    private void grant(Player p) {
        PermissionAttachment a = p.addAttachment(plugin, PERM, true);
        Bukkit.getScheduler().runTask(plugin, () -> p.removeAttachment(a));
    }

    private String petType(LivingEntity e) {
        try {
            Object t = byEntityTypeName.invoke(null, e.getType().name());
            return t == null ? null : ((Enum<?>) t).name();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    private boolean isActive(Player p) {
        try {
            return (Boolean) hasActive.invoke(petManager, p);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    /** Bảng xác suất cho /bxhpet tyle và web: mã nhóm -> (tên, %). */
    /** mã nhóm -> (tên, % gốc, % cộng thêm mùa này). */
    Map<String, Object[]> table() {
        Map<String, Object[]> out = new java.util.TreeMap<>();
        for (Map.Entry<String, String> e : groupName.entrySet()) {
            out.put(e.getKey(), new Object[] {e.getValue(), groupChance.getOrDefault(e.getKey(), chance), bonusNow(e.getKey())});
        }
        return out;
    }

    int defaultChance() {
        return chance;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
}
