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
            remember(event, name);
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

    /**
     * Đăng nhập lâu (23/10, chủ server): người có tên trong dang-nhap-lau.nguoi, vào lại từ ĐÚNG IP của lần đăng nhập trước và lần đó
     * chưa quá dang-nhap-lau.ngay ngày thì tự đăng nhập (người khác vẫn theo phiên AuthMe, 60 phút). Chỉ cho người tin cậy: cùng mạng
     * (quán net, 4G chung IP) người khác gõ đúng tên là vào được.
     */
    private void remember(AsyncPlayerConnectionConfigureEvent event, String name) {
        var c = plugin.getConfig().getConfigurationSection("dang-nhap-lau");
        if (c == null || name == null) {
            return;
        }
        boolean listed = c.getStringList("nguoi").stream().anyMatch(n -> n.trim().equalsIgnoreCase(name));
        if (!listed) {
            return;
        }
        AuthMeApi api = AuthMeApi.getInstance();
        java.net.InetSocketAddress addr = event.getConnection().getClientAddress();
        String ip = addr == null || addr.getAddress() == null ? null : addr.getAddress().getHostAddress();
        java.time.Instant last = api.isRegistered(name) ? api.getLastLoginTime(name) : null;
        String lastIp = api.isRegistered(name) ? api.getLastIp(name) : null;
        long days = Math.max(1, c.getLong("ngay", 30));
        if (ip == null || last == null || lastIp == null || !ip.equals(lastIp)
                || last.isBefore(java.time.Instant.now().minus(java.time.Duration.ofDays(days)))) {
            return;
        }
        api.forceLoginFromProxy(name);
        plugin.getLogger().info("Đăng nhập lâu: tự đăng nhập " + name + " (cùng IP, lần trước " + last + ")");
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
