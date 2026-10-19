package vn.duckymomo.core.paper;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * Placeholder %dmmlk_<mã>_<trường>% của Lữ khách cho lời thoại BetonQuest (%ph.dmmlk_cong_tindon%):
 * cong (co|khong), tim (co|khong), tindon, vung, huong, chu (thị trấn giữ cổng), timboi, ten, npcvung, npchuong.
 * Lớp riêng để server không có PlaceholderAPI vẫn nạp được Wanderers.
 */
final class WanderersPapi {

    private WanderersPapi() {
    }

    static Object register() {
        PlaceholderExpansion e = new PlaceholderExpansion() {
            @Override
            public String getIdentifier() {
                return "dmmlk";
            }

            @Override
            public String getAuthor() {
                return "DuckyMoMo";
            }

            @Override
            public String getVersion() {
                return "1";
            }

            @Override
            public boolean persist() {
                return true;
            }

            @Override
            public String onRequest(OfflinePlayer player, String params) {
                Wanderers w = Shared.wanderers;
                int i = params.indexOf('_');
                if (w == null || i <= 0) {
                    return null;
                }
                return w.value(params.substring(0, i), params.substring(i + 1));
            }
        };
        e.register();
        return e;
    }

    static void unregister(Object e) {
        if (e instanceof PlaceholderExpansion pe) {
            pe.unregister();
        }
    }
}
