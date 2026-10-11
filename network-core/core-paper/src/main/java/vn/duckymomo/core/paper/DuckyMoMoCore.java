package vn.duckymomo.core.paper;

import java.io.File;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class DuckyMoMoCore extends JavaPlugin {

    private MapDecorator decorator;
    private StructurePlacer structures;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Người chơi Bedrock (đã xác thực qua Xbox + Floodgate) không phải nhập mật khẩu AuthMe.
        // Chỉ bật trên server có AuthMe (hiện tại là lobby).
        if (getServer().getPluginManager().isPluginEnabled("AuthMe")) {
            getServer().getPluginManager().registerEvents(new BedrockAuthListener(this), this);
            getLogger().info("Bedrock auto-login: bật (AuthMe có trên server này)");
        }

        // Menu chọn chế độ: chỉ bật ở lobby (plugins/DuckyMoMoCore/config.yml: lobby-menu.enabled)
        PluginCommand menuCommand = getCommand("menu");
        if (getConfig().getBoolean("lobby-menu.enabled")) {
            File modesDir = new File(getConfig().getString("lobby-menu.modes-dir", "/modes"));
            LobbyMenu menu = new LobbyMenu(this, modesDir, getConfig().getInt("lobby-menu.item-slot", 4));
            getServer().getPluginManager().registerEvents(menu, this);
            if (menuCommand != null) {
                menuCommand.setExecutor((sender, cmd, label, args) -> {
                    if (sender instanceof Player p) {
                        menu.open(p);
                    }
                    return true;
                });
            }
            getLogger().info("Menu chọn chế độ: bật, đọc chế độ từ " + modesDir);
        } else if (menuCommand != null) {
            menuCommand.setExecutor((sender, cmd, label, args) -> {
                sender.sendMessage("Menu chọn chế độ chỉ có ở lobby. Gõ /server lobby để về sảnh.");
                return true;
            });
        }

        // Lệnh admin /dmm. Trang trí map (cây, cỏ, công trình) chỉ bật ở server có decorate.enabled (banghoi-1)
        decorator = getConfig().getBoolean("decorate.enabled") ? new MapDecorator(this) : null;
        structures = decorator != null ? new StructurePlacer(this) : null;
        PluginCommand dmm = getCommand("dmm");
        if (dmm != null) {
            dmm.setExecutor((sender, cmd, label, args) -> {
                if (args.length >= 1 && args[0].equalsIgnoreCase("decorate")) {
                    if (decorator == null) {
                        sender.sendMessage("Trang trí map chưa bật trên server này (config: decorate.enabled).");
                    } else {
                        decorator.command(sender, args);
                    }
                } else if (args.length >= 1 && args[0].equalsIgnoreCase("structures")) {
                    if (structures == null) {
                        sender.sendMessage("Đặt công trình chưa bật trên server này (config: decorate.enabled).");
                    } else {
                        structures.command(sender, args);
                    }
                } else {
                    sender.sendMessage("/dmm decorate|structures start | pause | status | reset");
                }
                return true;
            });
        }
        if (decorator != null && getConfig().getBoolean("decorate.auto-resume")) {
            // chạy tiếp sau khi server khởi động lại (nếu lần trước đang chạy dở)
            getServer().getScheduler().runTaskLater(this, () -> {
                decorator.resumeIfUnfinished();
                structures.resumeIfUnfinished();
            }, 200L);
        }
    }

    @Override
    public void onDisable() {
        if (decorator != null) {
            decorator.shutdown();
            structures.shutdown();
        }
    }
}
