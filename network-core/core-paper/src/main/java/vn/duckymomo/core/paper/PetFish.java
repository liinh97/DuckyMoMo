package vn.duckymomo.core.paper;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Cho pet MyPet ăn CÁ CÂU ĐƯỢC (EvenMoreFish): cầm cá, chuột phải vào pet của mình. Config pet-fish:
 *  - rarity.<Common|Rare|Epic|Legendary>: food (điểm no, tối đa 100), exp (kinh nghiệm), heal (% máu tối đa).
 *  - daily-exp-cap: kinh nghiệm tối đa mỗi người/ngày từ cá (chống máy câu cá tự động cày cấp pet); quá thì vẫn no + hồi máu.
 * Nhận cá qua dấu PDC của EvenMoreFish (evenmorefish:emf-fish-rarity), cá thường của Minecraft không tính.
 * Lưu số kinh nghiệm đã nhận hôm nay: plugins/DuckyMoMoCore/petfish.yml.
 */
final class PetFish implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    static final NamespacedKey RARITY = new NamespacedKey("evenmorefish", "emf-fish-rarity");
    static final NamespacedKey NAME = new NamespacedKey("evenmorefish", "emf-fish-name");

    private record Food(double food, double exp, double heal) { }

    private final DuckyMoMoCore plugin;
    private final Map<String, Food> foods = new HashMap<>();
    private final double cap;
    private final File file;
    private final YamlConfiguration data;

    PetFish(DuckyMoMoCore plugin, ConfigurationSection c) {
        this.plugin = plugin;
        this.cap = Math.max(0, c.getDouble("daily-exp-cap", 300));
        ConfigurationSection r = c.getConfigurationSection("rarity");
        if (r != null) {
            for (String k : r.getKeys(false)) {
                foods.put(k.toLowerCase(Locale.ROOT), new Food(r.getDouble(k + ".food", 10), r.getDouble(k + ".exp", 0), r.getDouble(k + ".heal", 0)));
            }
        }
        this.file = new File(plugin.getDataFolder(), "petfish.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    String describe() {
        return foods.size() + " độ hiếm, tối đa " + (long) cap + " kinh nghiệm/người/ngày";
    }

    /** Độ hiếm của cá EvenMoreFish (vd "Legendary"), hoặc null nếu không phải cá câu. */
    static String rarity(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        return it.getItemMeta().getPersistentDataContainer().get(RARITY, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFeed(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        String rar = rarity(hand);
        if (rar == null || !PetCombat.isPet(event.getRightClicked())) {
            return;
        }
        Object pet = MyPetBridge.of(event.getRightClicked());
        Player owner = Arena.petOwner(event.getRightClicked());
        if (pet == null || owner == null || !owner.getUniqueId().equals(p.getUniqueId())) {
            return;
        }
        Food f = foods.get(rar.toLowerCase(Locale.ROOT));
        if (f == null) {
            return;
        }
        event.setCancelled(true);   // không để MyPet xử lý cú bấm này (cưỡi, điều khiển…)
        double sat = MyPetBridge.saturation(pet);
        LivingEntity e = event.getRightClicked() instanceof LivingEntity le ? le : null;
        double max = e == null || e.getAttribute(Attribute.MAX_HEALTH) == null ? 0 : e.getAttribute(Attribute.MAX_HEALTH).getValue();
        boolean hungry = sat >= 0 && sat < 100;
        boolean hurt = e != null && e.getHealth() < max;
        String today = LocalDate.now(VN).toString();
        String key = "ngay." + today + "." + p.getUniqueId();
        double got = data.getDouble(key, 0);
        double exp = Math.max(0, Math.min(f.exp(), cap - got));
        if (!hungry && !hurt && exp <= 0) {
            p.sendActionBar(c("&7Pet đang no, đủ máu và hôm nay đã nhận đủ kinh nghiệm từ cá (" + (long) cap + ")."));
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
        if (hungry) {
            MyPetBridge.setSaturation(pet, sat + f.food());
        }
        if (hurt && f.heal() > 0) {
            e.setHealth(Math.min(max, e.getHealth() + max * f.heal() / 100.0));
        }
        if (exp > 0 && MyPetBridge.addExp(pet, exp)) {
            data.set(key, got + exp);
            dropOldDays(today);
            try {
                data.save(file);
            } catch (IOException ex) {
                plugin.getLogger().warning("Không lưu được petfish.yml: " + ex.getMessage());
            }
        }
        if (e != null) {
            e.getWorld().spawnParticle(Particle.HEART, e.getLocation().add(0, e.getHeight() + 0.3, 0), 4, 0.3, 0.2, 0.3, 0);
        }
        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.8f, 1.2f);
        p.sendActionBar(c("&a≈ Pet ăn cá: &fno +" + (long) f.food() + (f.heal() > 0 && hurt ? " &7· &fhồi " + (long) f.heal() + "% máu" : "")
                + (exp > 0 ? " &7· &e+" + fmt(exp) + " kinh nghiệm &7(hôm nay " + fmt(got + exp) + "/" + (long) cap + ")"
                        : f.exp() > 0 ? " &7· hôm nay đã đủ kinh nghiệm từ cá" : "")));
    }

    /** Chỉ giữ dữ liệu hôm nay (file không phình). */
    private void dropOldDays(String today) {
        ConfigurationSection all = data.getConfigurationSection("ngay");
        if (all != null) {
            for (String d : all.getKeys(false)) {
                if (!d.equals(today)) {
                    data.set("ngay." + d, null);
                }
            }
        }
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static Component c(String s) {
        return Tip.ns(LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false));
    }
}
