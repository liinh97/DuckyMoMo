package vn.duckymomo.core.paper;

import com.destroystokyo.paper.profile.PlayerProfile;
import fr.xephi.authme.api.v3.AuthMeApi;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Tự đăng ký + đăng nhập AuthMe cho người chơi Bedrock.
 *
 * <p>Người chơi Bedrock vào qua Geyser + Floodgate đã được Xbox Live xác thực, nên mật khẩu
 * AuthMe là thừa (và mỗi lần app điện thoại bị tạm dừng lại phải gõ lại). Nhận diện bằng
 * UUID của Floodgate ({@code new UUID(0, xuid)}, nửa đầu bằng 0) cộng tiền tố tên "." của Floodgate.
 * Người chơi Java qua Velocity offline mode luôn có UUID v3 (nửa đầu khác 0), nên không giả được.
 */
final class BedrockAuthListener implements Listener {

    private static final String FLOODGATE_PREFIX = ".";
    private static final long JOIN_FALLBACK_TICKS = 20L;

    private final DuckyMoMoCore plugin;
    private final SecureRandom random = new SecureRandom();

    BedrockAuthListener(DuckyMoMoCore plugin) {
        this.plugin = plugin;
    }

    static boolean isBedrock(UUID id, String name) {
        return id != null && name != null
            && id.getMostSignificantBits() == 0L
            && name.startsWith(FLOODGATE_PREFIX);
    }

    /**
     * Chạy trước handler của AuthMe (HIGHEST) ở pha configuration: đăng ký nếu chưa có, rồi xếp
     * lệnh force-login. AuthMe thấy lệnh này thì bỏ qua hộp thoại pre-join và đăng nhập khi vào.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
        PlayerProfile profile = event.getConnection().getProfile();
        UUID id = profile.getId();
        String name = profile.getName();
        if (!isBedrock(id, name)) {
            return;
        }
        AuthMeApi api = AuthMeApi.getInstance();
        if (!api.isRegistered(name)) {
            // Mật khẩu ngẫu nhiên, không ai biết: tài khoản Bedrock chỉ vào được qua Xbox + Floodgate
            if (api.registerPlayer(name, randomPassword())) {
                plugin.getLogger().info("Bedrock: đã tự đăng ký " + name);
            }
        }
        api.forceLoginFromProxy(name);
    }

    /** Dự phòng: nếu vì lý do nào đó vào rồi mà vẫn chưa đăng nhập (ví dụ tắt pre-join dialog). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!isBedrock(player.getUniqueId(), player.getName())) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            AuthMeApi api = AuthMeApi.getInstance();
            if (player.isOnline() && !api.isAuthenticated(player)) {
                api.forceLogin(player);
                plugin.getLogger().info("Bedrock: force-login dự phòng cho " + player.getName());
            }
        }, JOIN_FALLBACK_TICKS);
    }

    private String randomPassword() {
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
