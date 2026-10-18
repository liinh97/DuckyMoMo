package vn.duckymomo.core.paper;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/** Placeholder %dmmcap_cap% (cấp Thăng Cấp, cho TAB). Lớp riêng để sảnh không có PlaceholderAPI vẫn nạp được LevelUp. */
final class LevelUpPapi {

    private LevelUpPapi() {
    }

    static Object register() {
        PlaceholderExpansion e = new PlaceholderExpansion() {
            @Override
            public String getIdentifier() {
                return "dmmcap";
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
                return params.equals("cap") ? String.valueOf(LevelUp.levelOf(player.getUniqueId())) : null;
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
