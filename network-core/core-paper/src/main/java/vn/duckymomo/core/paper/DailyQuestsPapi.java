package vn.duckymomo.core.paper;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * Placeholder việc NPC (28/10). Lớp riêng để server không có PlaceholderAPI vẫn nạp được DailyQuests.
 *  %dmmviec_ketqua% lời NPC vừa nói khi giao / trả việc (hội thoại BetonQuest hiện ngay trong bảng, không phải đợi chat).
 */
final class DailyQuestsPapi {

    private DailyQuestsPapi() {
    }

    static Object register() {
        PlaceholderExpansion e = new PlaceholderExpansion() {
            @Override
            public String getIdentifier() {
                return "dmmviec";
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
                if (player == null) {
                    return "";
                }
                return "ketqua".equals(params) ? DailyQuests.summary(player.getUniqueId()) : null;
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
