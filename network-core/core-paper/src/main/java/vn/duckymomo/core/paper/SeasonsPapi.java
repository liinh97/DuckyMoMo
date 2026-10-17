package vn.duckymomo.core.paper;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Placeholder %dmmmua_*% cho Seasons. Tách khỏi Seasons để server KHÔNG có PlaceholderAPI (sảnh) vẫn nạp được lớp Seasons
 * (lớp có tham chiếu PlaceholderExpansion thì Java kiểm tra lúc nạp và lỗi ClassNotFoundException -> tắt cả plugin).
 */
final class SeasonsPapi {

    private SeasonsPapi() {
    }

    static PlaceholderExpansion create(Seasons s) {
        return new PlaceholderExpansion() {
            @Override
            public String getIdentifier() {
                return "dmmmua";
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
                return switch (params) {
                    case "id" -> s.current();
                    case "ten" -> LegacyComponentSerializer.legacySection().serialize(Seasons.c(s.name(s.current())));
                    case "ten_tron" -> Seasons.plain(s.name(s.current()));
                    case "con" -> String.valueOf(s.daysLeft());
                    case "sau" -> Seasons.plain(s.name(s.next()));
                    case "sukien" -> {
                        ConfigurationSection ev = s.event();
                        yield ev == null ? "" : Seasons.plain(ev.getString("name", ev.getName()));
                    }
                    default -> null;
                };
            }
        };
    }
}
