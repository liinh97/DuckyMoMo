package vn.duckymomo.core.paper;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

/**
 * Menu dạng form (nút bấm) cho người chơi Bedrock (điện thoại, console): giao diện rương khó bấm trên màn hình nhỏ.
 * Cần Floodgate trên server này (proxy bật send-floodgate-data). Tách lớp riêng để server không có Floodgate
 * không bị lỗi thiếu lớp: chỉ gọi khi available() trả về true.
 */
final class BedrockMenu {

    record Entry(String label, String server) {
    }

    private BedrockMenu() {
    }

    static boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("floodgate");
    }

    static boolean isBedrock(UUID uuid) {
        return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
    }

    /** Gửi form; bấm nút có server thì gọi onPick(server). Nút không có server (bảo trì, chưa hỗ trợ) chỉ để xem. */
    static void open(UUID uuid, String title, String content, List<Entry> entries, Consumer<String> onPick) {
        SimpleForm.Builder form = SimpleForm.builder().title(title).content(content);
        for (Entry e : entries) {
            form.button(e.label());
        }
        form.validResultHandler(res -> {
            Entry e = entries.get(res.clickedButtonId());
            if (e.server() != null) {
                onPick.accept(e.server());
            }
        });
        FloodgateApi.getInstance().sendForm(uuid, form.build());
    }
}
