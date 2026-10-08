package vn.duckymomo.core.paper;

import org.bukkit.plugin.java.JavaPlugin;

public final class DuckyMoMoCore extends JavaPlugin {

    @Override
    public void onEnable() {
        // Người chơi Bedrock (đã xác thực qua Xbox + Floodgate) không phải nhập mật khẩu AuthMe.
        // Chỉ bật trên server có AuthMe (hiện tại là lobby).
        if (getServer().getPluginManager().isPluginEnabled("AuthMe")) {
            getServer().getPluginManager().registerEvents(new BedrockAuthListener(this), this);
            getLogger().info("Bedrock auto-login: bật (AuthMe có trên server này)");
        }
    }
}
