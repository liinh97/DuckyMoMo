package vn.duckymomo.core.paper;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

/** Placeholder %dmmvang_so_du% (số Vàng, cho bảng bên phải TAB). Lớp riêng để server không có PlaceholderAPI vẫn nạp được DonateQr. */
final class VangPapi {

    private VangPapi() {
    }

    static Object register() {
        PlaceholderExpansion e = new PlaceholderExpansion() {
            @Override
            public String getIdentifier() {
                return "dmmvang";
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
                if (player == null || player.getName() == null || !params.equals("so_du")) {
                    return null;
                }
                Long v = DonateQr.BAL.get(player.getName().toLowerCase(Locale.ROOT));
                return v == null ? "…" : String.format("%,d", v).replace(',', '.');
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
