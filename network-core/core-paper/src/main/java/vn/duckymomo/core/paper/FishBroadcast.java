package vn.duckymomo.core.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

/**
 * Khung báo cả server khi có người câu được cá hiếm (30/10, chủ server gửi ảnh "★ CON CÁ HIẾM! ★ … Tier | Cân nặng | Dài").
 * Nghe EMFFishEvent của EvenMoreFish qua reflection (không cần thư viện lúc build). Độ hiếm trong fish-broadcast.rarities (mã id của
 * EvenMoreFish, vd Epic, Legendary): tắt broadcast.enabled của độ hiếm đó bên EvenMoreFish để không báo 2 lần.
 * Ai tắt "Thông báo sự kiện" (/caidat sukien) thì không thấy.
 */
final class FishBroadcast implements Listener {
    private static final LegacyComponentSerializer L = LegacyComponentSerializer.legacyAmpersand();
    private final JavaPlugin plugin;
    private final List<String> rarities;

    FishBroadcast(JavaPlugin plugin, List<String> rarities) {
        this.plugin = plugin;
        this.rarities = rarities.stream().map(x -> x.toLowerCase(Locale.ROOT)).toList();
    }

    @SuppressWarnings("unchecked")
    boolean hook() {
        Plugin emf = Bukkit.getPluginManager().getPlugin("EvenMoreFish");
        if (emf == null || !emf.isEnabled()) {
            return false;
        }
        try {
            ClassLoader cl = emf.getClass().getClassLoader();
            Class<? extends Event> cls = (Class<? extends Event>) Class.forName("com.oheers.fish.api.EMFFishEvent", true, cl);
            Method getFish = cls.getMethod("getFish"), getPlayer = cls.getMethod("getPlayer");
            Bukkit.getPluginManager().registerEvent(cls, this, EventPriority.MONITOR, (l, ev) -> {
                if (!cls.isInstance(ev) || (ev instanceof org.bukkit.event.Cancellable c && c.isCancelled())) {
                    return;
                }
                try {
                    announce((Player) getPlayer.invoke(ev), getFish.invoke(ev));
                } catch (ReflectiveOperationException | RuntimeException e) {
                    plugin.getLogger().warning("Báo cá hiếm: " + e);
                }
            }, plugin, true);
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            plugin.getLogger().warning("Không gắn được vào EvenMoreFish: " + e);
            return false;
        }
    }

    private static Object call(Object o, String m) throws ReflectiveOperationException {
        return o.getClass().getMethod(m).invoke(o);
    }

    private void announce(Player p, Object fish) throws ReflectiveOperationException {
        Object rarity = call(fish, "getRarity");
        String id = String.valueOf(call(rarity, "getId"));
        if (!rarities.contains(id.toLowerCase(Locale.ROOT))) {
            return;
        }
        String rName = L.serialize((Component) call(rarity, "getDisplayName"));
        String fName = L.serialize((Component) call(fish, "getDisplayName"));
        float len = (float) call(fish, "getLength");
        double kg = (double) call(fish, "getWeight");
        boolean legend = id.equalsIgnoreCase("Legendary");
        String bar = legend ? "&6&m                                        " : "&d&m                                        ";
        String stats = "&fĐộ hiếm: " + rName + (kg > 0 ? " &7| &fNặng: &b" + String.format(Locale.ROOT, "%.1f", kg) + "kg" : "")
                + (len > 0 ? " &7| &fDài: &d" + String.format(Locale.ROOT, "%.1f", len) + "cm" : "");
        PlayerSettings.broadcast("sukien", Tip.c(bar));
        PlayerSettings.broadcast("sukien", Tip.c(legend ? "      &6✦ &eCÁ HUYỀN THOẠI! &6✦" : "      &d★ &fCON CÁ HIẾM! &d★"));
        PlayerSettings.broadcast("sukien", Tip.c("&fNgười chơi &e" + p.getName() + " &fvừa câu được &r" + fName + "&7!"));
        PlayerSettings.broadcast("sukien", Tip.c(stats));
        PlayerSettings.broadcast("sukien", Tip.c(bar));
        ScreenNotify.show(p, legend ? "&6✦ Cá Huyền thoại!" : "&d★ Cá hiếm!", "&f" + fName);
    }
}
