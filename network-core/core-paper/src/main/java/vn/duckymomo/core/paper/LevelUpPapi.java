package vn.duckymomo.core.paper;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * Placeholder Thăng Cấp. Lớp riêng để sảnh không có PlaceholderAPI vẫn nạp được LevelUp.
 *  %dmmcap_cap% cấp · %dmmcap_mau% mã màu & của cấp (đổi theo mốc level-colors, cho TAB / bảng bên phải) · %dmmcap_mau_mm% màu đó
 *  dạng MiniMessage (thanh chỉ số AuraSkills) · %dmmcap_capmau% "&a12" · %dmmcap_hud_mm% vài giây sau khi đánh / bị đánh:
 *  " | ⚔ 7,7 (+0,7)" phần sát thương Sức mạnh cộng thêm, " | 🛡 −0,5" phần Bền bỉ đỡ (27/10: trước chỉ có dòng chữ báo, không thấy hiệu lực).
 */
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
                int lv = LevelUp.levelOf(player.getUniqueId());
                return switch (params) {
                    case "cap" -> String.valueOf(lv);
                    case "mau" -> LevelUp.colorOf(lv);
                    case "mau_mm" -> LevelUp.miniMessage(LevelUp.colorOf(lv));
                    case "capmau" -> LevelUp.colorOf(lv) + lv;
                    case "hud_mm" -> LevelUp.hud(player.getUniqueId());
                    default -> null;
                };
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
