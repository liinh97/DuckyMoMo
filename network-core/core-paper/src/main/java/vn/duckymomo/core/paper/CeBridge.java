package vn.duckymomo.core.paper;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Cầu nối tới CrazyEnchantments (phù phép CE như Boom, Lifesteal… không nằm trong registry phù phép của game).
 * Gọi API của CE bằng reflection lúc chạy nên không cần CE khi build, và server không có CE vẫn chạy bình thường.
 * API dùng (CE 26.x): CrazyEnchantments.getStarter().getCrazyManager(): getRegisteredEnchantments(),
 * getEnchantmentFromName(String), addEnchantment(ItemStack, CEnchantment, int).
 */
final class CeBridge {

    /** Một phù phép CE cho trang web: tên trong Enchantments.yml, tên hiện (mã màu &), cấp tối đa, đang bật, loại đồ dùng được. */
    record Info(String name, String display, int max, boolean active, String type, List<String> items, List<String> desc) { }

    private final Logger log;
    private Object manager;
    private boolean tried;

    CeBridge(Logger log) {
        this.log = log;
    }

    /** Lấy CrazyManager (lần đầu dùng, vì CE có thể bật sau DuckyMoMoCore). null nếu không có CE. */
    private Object manager() {
        if (manager != null || tried) {
            return manager;
        }
        tried = true;
        Plugin ce = Bukkit.getPluginManager().getPlugin("CrazyEnchantments");
        if (ce == null || !ce.isEnabled()) {
            tried = ce == null; // CE có nhưng chưa bật: thử lại lần sau
            return null;
        }
        try {
            Object starter = ce.getClass().getMethod("getStarter").invoke(ce);
            manager = starter.getClass().getMethod("getCrazyManager").invoke(starter);
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.warning("Không nối được CrazyEnchantments (API đổi?): " + e);
        }
        return manager;
    }

    boolean available() {
        return manager() != null;
    }

    List<Info> list() {
        List<Info> out = new ArrayList<>();
        Object m = manager();
        if (m == null) {
            return out;
        }
        try {
            for (Object ce : (List<?>) m.getClass().getMethod("getRegisteredEnchantments").invoke(m)) {
                Class<?> c = ce.getClass();
                String name = String.valueOf(c.getMethod("getName").invoke(ce));
                String display = String.valueOf(c.getMethod("getCustomName").invoke(ce));
                int max = (int) c.getMethod("getMaxLevel").invoke(ce);
                boolean active = (boolean) c.getMethod("isActivated").invoke(ce);
                List<String> desc = new ArrayList<>();
                Object d = c.getMethod("getInfoDescription").invoke(ce);
                if (d instanceof List<?> l) {
                    for (Object x : l) {
                        desc.add(String.valueOf(x));
                    }
                }
                String type = "";
                List<String> items = new ArrayList<>();
                Object t = c.getMethod("getEnchantmentType").invoke(ce);
                if (t != null) {
                    type = String.valueOf(t.getClass().getMethod("getName").invoke(t));
                    Object mats = t.getClass().getMethod("getEnchantableMaterials").invoke(t);
                    if (mats instanceof List<?> l) {
                        for (Object x : l) {
                            if (x instanceof Material mat) {
                                items.add(mat.name());
                            }
                        }
                    }
                }
                out.add(new Info(name, display, max, active, type, items, desc));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.warning("Không đọc được danh sách phù phép CrazyEnchantments: " + e);
        }
        return out;
    }

    /** Gắn phù phép CE vào đồ (CE tự ghi dòng mô tả + dữ liệu). false nếu không có CE hoặc không có phù phép này. */
    boolean add(ItemStack item, String name, int level) {
        Object m = manager();
        if (m == null) {
            return false;
        }
        try {
            Object ce = m.getClass().getMethod("getEnchantmentFromName", String.class).invoke(m, name);
            if (ce == null) {
                return false;
            }
            Method add = null;
            for (Method x : m.getClass().getMethods()) {
                if (x.getName().equals("addEnchantment") && x.getParameterCount() == 3 && x.getParameterTypes()[0] == ItemStack.class) {
                    add = x;
                }
            }
            if (add == null) {
                return false;
            }
            add.invoke(m, item, ce, level);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.warning("Không gắn được phù phép CE " + name + ": " + e);
            return false;
        }
    }
}
