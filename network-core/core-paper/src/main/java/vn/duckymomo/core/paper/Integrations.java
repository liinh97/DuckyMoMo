package vn.duckymomo.core.paper;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Đọc dữ liệu của plugin khác không cần thư viện lúc build (reflection hoặc đọc file). Lỗi thì trả về 0/rỗng
 * (không chặn người chơi vì plugin khác đổi API), và ghi cảnh báo một lần.
 * QuickShop-Hikari 6.3, zAuctionHouse 4.0, Essentials userdata, uJobs jobs.yml.
 */
final class Integrations {

    private static final java.util.Set<String> WARNED = new java.util.HashSet<>();

    private Integrations() {
    }

    private static void warn(String what, Exception e) {
        if (WARNED.add(what)) {
            Bukkit.getLogger().warning("[DuckyMoMoCore] Không đọc được " + what + ": " + e);
        }
    }

    /** Số shop rương QuickShop của người chơi. */
    static int quickShopCount(UUID id) {
        Plugin qs = Bukkit.getPluginManager().getPlugin("QuickShop-Hikari");
        if (qs == null || !qs.isEnabled()) {
            return 0;
        }
        try {
            Class<?> api = Class.forName("com.ghostchu.quickshop.api.QuickShopAPI", true, qs.getClass().getClassLoader());
            Object inst = api.getMethod("getInstance").invoke(null);
            Object mgr = api.getMethod("getShopManager").invoke(inst);
            Class<?> sm = Class.forName("com.ghostchu.quickshop.api.shop.ShopManager", true, qs.getClass().getClassLoader());
            Method all = sm.getMethod("getAllShops", UUID.class);
            return ((Collection<?>) all.invoke(mgr, id)).size();
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn("QuickShop", e);
            return 0;
        }
    }

    /** Số món của người chơi trên chợ đấu giá: đang bán + hết hạn chưa rút + đã mua chưa nhận. */
    static int auctionCount(Player p) {
        Plugin zah = Bukkit.getPluginManager().getPlugin("zAuctionHouse");
        if (zah == null || !zah.isEnabled()) {
            return 0;
        }
        try {
            ClassLoader cl = zah.getClass().getClassLoader();
            Class<?> ap = Class.forName("fr.maxlego08.zauctionhouse.api.AuctionPlugin", true, cl);
            Class<?> am = Class.forName("fr.maxlego08.zauctionhouse.api.AuctionManager", true, cl);
            Object mgr = ap.getMethod("getAuctionManager").invoke(zah);
            int n = ((Collection<?>) am.getMethod("getItemsListedForSale", Player.class).invoke(mgr, p)).size();
            Class<?> st = Class.forName("fr.maxlego08.zauctionhouse.api.item.StorageType", true, cl);
            Method getItems = am.getMethod("getItems", st);
            Class<?> itemCls = Class.forName("fr.maxlego08.zauctionhouse.api.item.Item", true, cl);
            Method seller = itemCls.getMethod("getSellerUniqueId");
            Method buyer = itemCls.getMethod("getBuyerUniqueId");
            for (Object item : (List<?>) getItems.invoke(mgr, enumValue(st, "EXPIRED"))) {
                if (p.getUniqueId().equals(seller.invoke(item))) {
                    n++;
                }
            }
            for (Object item : (List<?>) getItems.invoke(mgr, enumValue(st, "PURCHASED"))) {
                if (p.getUniqueId().equals(buyer.invoke(item))) {
                    n++;
                }
            }
            return n;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn("zAuctionHouse", e);
            return 0;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> cls, String name) {
        return Enum.valueOf((Class) cls, name);
    }

    /** Tên các nhà (/home) của người chơi, đọc từ plugins/Essentials/userdata/<uuid>.yml. */
    static Set<String> essentialsHomes(UUID id) {
        File f = new File(Bukkit.getPluginsFolder(), "Essentials/userdata/" + id + ".yml");
        if (!f.isFile()) {
            return Set.of();
        }
        ConfigurationSection homes = YamlConfiguration.loadConfiguration(f).getConfigurationSection("homes");
        return homes == null ? Set.of() : homes.getKeys(false);
    }

    /** Mã các nghề uJobs (khoá dưới "jobs:" trong plugins/UJobs/jobs.yml). */
    static Set<String> ujobsJobs() {
        File f = new File(Bukkit.getPluginsFolder(), "UJobs/jobs.yml");
        if (!f.isFile() || !Bukkit.getPluginManager().isPluginEnabled("UJobs")) {
            return Set.of();
        }
        ConfigurationSection jobs = YamlConfiguration.loadConfiguration(f).getConfigurationSection("jobs");
        return jobs == null ? Set.of() : jobs.getKeys(false);
    }
}
