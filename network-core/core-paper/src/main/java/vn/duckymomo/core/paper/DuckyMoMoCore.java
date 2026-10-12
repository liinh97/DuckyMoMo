package vn.duckymomo.core.paper;

import java.io.File;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class DuckyMoMoCore extends JavaPlugin {

    private MapDecorator decorator;
    private StructurePlacer structures;
    private TownyMapLayer townyMap;
    private ItemCleaner cleaner;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setup(false);
    }

    /**
     * /dmm reload: đọc lại config.yml rồi bật lại mọi phần (trang Cài đặt gọi lệnh này sau khi ghi config).
     * Trang trí map đang chạy dở thì bị dừng (lưu tiến độ), chạy tiếp bằng /dmm decorate start.
     */
    private void reload() {
        teardown();
        HandlerList.unregisterAll(this);
        getServer().getScheduler().cancelTasks(this);
        reloadConfig();
        setup(true);
    }

    private void teardown() {
        if (townyMap != null) {
            townyMap.stop();
            townyMap = null;
        }
        if (decorator != null) {
            decorator.shutdown();
            structures.shutdown();
        }
    }

    private void setup(boolean reloading) {
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

        // Lọc chat: che từ tục tiếng Việt, chống spam (chat-filter.enabled)
        if (getConfig().getBoolean("chat-filter.enabled")) {
            getServer().getPluginManager().registerEvents(new ChatFilter(getConfig().getConfigurationSection("chat-filter")), this);
            getLogger().info("Lọc chat: bật");
        }

        // Rank donate (tước vị) + thông báo vào/ra: donate-ranks.enabled
        PluginCommand rankCmd = getCommand("rank");
        if (getConfig().getBoolean("donate-ranks.enabled") && getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            DonateRanks ranks = new DonateRanks(this, getConfig().getConfigurationSection("donate-ranks"));
            getServer().getPluginManager().registerEvents(ranks, this);
            if (rankCmd != null) {
                rankCmd.setExecutor((sender, cmd, label, args) -> ranks.command(sender, args));
            }
            getLogger().info("Rank donate + thông báo vào/ra: bật");
        } else if (rankCmd != null) {
            rankCmd.setExecutor((sender, cmd, label, args) -> {
                sender.sendMessage("Rank donate chưa bật trên server này.");
                return true;
            });
        }

        // Cẩm nang trong game: /huongdan (nội dung plugins/DuckyMoMoCore/huongdan.yml)
        PluginCommand guideCmd = getCommand("huongdan");
        if (guideCmd != null) {
            Guide guide = new Guide(this, Guide.defaultChapter(getConfig().getConfigurationSection("guide")),
                    getConfig().getString("guide.join-hint", ""));
            getServer().getPluginManager().registerEvents(guide, this);
            guideCmd.setExecutor((sender, cmd, label, args) -> guide.command(sender, args));
        }

        // Chat có prefix role + rank donate (lobby không có EssentialsX Chat): chat-format.enabled
        if (getConfig().getBoolean("chat-format.enabled") && getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            getServer().getPluginManager().registerEvents(new ChatFormat(), this);
            getLogger().info("Chat có prefix LuckPerms: bật");
        }

        // Đảo bay (lobby): rơi xuống dưới void-teleport.below-y thì về spawn (không đặt = tắt)
        if (getConfig().isInt("void-teleport.below-y")) {
            int belowY = getConfig().getInt("void-teleport.below-y");
            getServer().getPluginManager().registerEvents(new VoidTeleport(belowY, getConfig().getBoolean("void-teleport.spawn-on-join")), this);
            getLogger().info("Rơi xuống hư không về spawn: bật (y < " + belowY + ")");
        }

        // Chống đặt rồi phá block để lấy tiền nghề (uJobs): job-guard.enabled
        if (getConfig().getBoolean("job-guard.enabled")) {
            getServer().getPluginManager().registerEvents(
                    new PlacedBlockGuard(this, getConfig().getStringList("job-guard.materials")), this);
            getLogger().info("Chống đặt-phá lấy tiền nghề: bật");
        }

        // Ranh giới thị trấn Towny trên bản đồ web squaremap (towny-map.enabled)
        if (getConfig().getBoolean("towny-map.enabled") && getServer().getPluginManager().isPluginEnabled("Towny")
                && getServer().getPluginManager().isPluginEnabled("squaremap")) {
            townyMap = new TownyMapLayer(this, getConfig().getString("towny-map.world", "world"),
                    getConfig().getInt("towny-map.interval-seconds", 60), getConfig().getIntegerList("towny-map.border"));
            townyMap.start();
        }

        // Dọn rác định kỳ (banghoi): item-cleaner.enabled
        cleaner = getConfig().getBoolean("item-cleaner.enabled")
                ? new ItemCleaner(this, getConfig().getConfigurationSection("item-cleaner")) : null;
        if (cleaner != null) {
            cleaner.start();
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
                } else if (args.length >= 1 && args[0].equalsIgnoreCase("clean")) {
                    if (cleaner == null) {
                        sender.sendMessage("Dọn rác chưa bật trên server này (config: item-cleaner.enabled).");
                    } else {
                        sender.sendMessage(cleaner.cleanNow());
                    }
                } else if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
                    reload();
                    sender.sendMessage("DuckyMoMoCore: đã nạp lại config.yml");
                } else {
                    sender.sendMessage("/dmm decorate|structures start | pause | status | reset   /dmm clean   /dmm reload");
                }
                return true;
            });
        }
        if (!reloading && decorator != null && getConfig().getBoolean("decorate.auto-resume")) {
            // chạy tiếp sau khi server khởi động lại (nếu lần trước đang chạy dở)
            getServer().getScheduler().runTaskLater(this, () -> {
                decorator.resumeIfUnfinished();
                structures.resumeIfUnfinished();
            }, 200L);
        }
    }

    @Override
    public void onDisable() {
        teardown();
    }
}
