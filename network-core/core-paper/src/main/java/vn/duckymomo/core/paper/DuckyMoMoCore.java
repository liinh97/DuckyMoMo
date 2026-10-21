package vn.duckymomo.core.paper;

import java.util.List;
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
    private OverEnchant overEnchant;
    private CommandDump commandDump;
    // Lệnh có đăng ký (plugin.yml) nhưng module tắt trên server này: chỉ trả lời "chỉ có ở…". commands.json đánh dấu để web không liệt kê.
    private final java.util.Set<String> offCommands = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private RandomTeleport rtp;
    private WarRules warRules;
    private Arena arena;
    private SiegeSupply supply;
    private RegionSpecialty specialty;
    private WarBalance warBalance;
    private HallOfFameDisplay hallDisplay;
    private Mercenary mercenary;
    private Achievements achievements;
    private TreasureHunt treasure;
    private WantedBoard wanted;
    private ThaiAp thaiAp;
    private DungeonGate dungeonGate;
    private BossDrops bossDrops;
    private PlayerSettings playerSettings;
    private LevelUp levelUp;
    private Object levelPapi;
    private Object vangPapi;
    private WildGates wildGates;
    private Wanderers wanderers;
    private Object wanderPapi;
    private Activity activity;
    private PeaceAlliance peace;
    private DecorShop decorShop;
    private ItemLibrary itemLibrary;
    private Rewards rewards;
    private DailyQuests quests;
    private Crates crates;
    private me.clip.placeholderapi.expansion.PlaceholderExpansion gachaPapi;
    private me.clip.placeholderapi.expansion.PlaceholderExpansion seasonPapi;
    private me.clip.placeholderapi.expansion.PlaceholderExpansion statsPapi;
    private me.clip.placeholderapi.expansion.PlaceholderExpansion frontierPapi;
    private me.clip.placeholderapi.expansion.PlaceholderExpansion arenaPapi;
    private Frontier frontier;

    /**
     * Sửa lỗi Citizens 2.0.44 b4265 trên 26.2: lưu kiểu NPC là "minecraft:player" nhưng lúc nạp chỉ hiểu "PLAYER" (SimpleNPCDataStore
     * matchEntityType) → mọi NPC biến mất sau mỗi lần khởi động lại. onLoad chạy trước khi Citizens đọc saves.yml, nên đổi lại ở đây
     * (chỉ dòng kiểu NPC "type: minecraft:<loài>" dưới traits, thụt 4–8 khoảng trắng: định dạng danh sách cũ và định dạng theo ID). Server không có Citizens: không có file, bỏ qua.
     */
    @Override
    public void onLoad() {
        File saves = new File(getDataFolder().getParentFile(), "Citizens/saves.yml");
        if (!saves.isFile()) {
            return;
        }
        try {
            String text = java.nio.file.Files.readString(saves.toPath());
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^( {4,8}type: )minecraft:([a-z_]+)\\s*$").matcher(text);
            StringBuilder out = new StringBuilder();
            int n = 0;
            while (m.find()) {
                m.appendReplacement(out, m.group(1) + m.group(2).toUpperCase(java.util.Locale.ROOT));
                n++;
            }
            m.appendTail(out);
            if (n > 0) {
                java.nio.file.Files.writeString(saves.toPath(), out.toString());
                getLogger().info("Citizens saves.yml: sửa kiểu NPC của " + n + " NPC (minecraft:player -> PLAYER) để Citizens nạp lại được.");
            }
        } catch (java.io.IOException e) {
            getLogger().warning("Không sửa được Citizens/saves.yml: " + e.getMessage());
        }
    }

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
        SellLimit sl = Shared.sellLimit;
        if (sl != null) {
            sl.flush();
        }
        Backpack bp = Shared.backpack;
        if (bp != null) {
            bp.saveAll();
        }
        MediaProgram mp = Shared.media;
        if (mp != null) {
            mp.stop();
        }
        Shared.clear();
        DailyCheckin.INSTANCE = null;
        DailyQuests.INSTANCE = null;
        if (playerSettings != null) {
            playerSettings.stop();
            playerSettings = null;
        }
        if (supply != null) {
            supply.stop();
            supply = null;
        }
        if (specialty != null) {
            specialty.stop();
            specialty = null;
        }
        if (warBalance != null) {
            warBalance.stop();
            warBalance = null;
        }
        if (hallDisplay != null) {
            hallDisplay.stop();
            hallDisplay = null;
        }
        if (mercenary != null) {
            mercenary.stop();
            mercenary = null;
        }
        if (achievements != null) {
            achievements.stop();
            achievements = null;
        }
        if (treasure != null) {
            treasure.stop();
            treasure = null;
        }
        if (wanted != null) {
            wanted.stop();
            wanted = null;
        }
        if (wanderPapi != null) {
            WanderersPapi.unregister(wanderPapi);
            wanderPapi = null;
        }
        if (wanderers != null) {
            wanderers.stop();
            wanderers = null;
        }
        if (wildGates != null) {
            wildGates.stop();
            wildGates = null;
        }
        if (dungeonGate != null) {
            dungeonGate.stop();
            dungeonGate = null;
        }
        if (bossDrops != null) {
            bossDrops.stop();
            bossDrops = null;
        }
        if (thaiAp != null) {
            thaiAp.stop();
            thaiAp = null;
        }
        if (arenaPapi != null) {
            arenaPapi.unregister();
            arenaPapi = null;
        }
        if (arena != null) {
            arena.shutdown();
            arena = null;
        }
        if (quests != null) {
            quests.flush();
            quests = null;
        }
        if (crates != null) {
            crates.flush();
        }
        if (gachaPapi != null) {
            gachaPapi.unregister();
            gachaPapi = null;
        }
        if (levelPapi != null) {
            LevelUpPapi.unregister(levelPapi);
            levelPapi = null;
        }
        if (vangPapi != null) {
            VangPapi.unregister(vangPapi);
            vangPapi = null;
        }
        if (levelUp != null) {
            levelUp.stop();
            levelUp = null;
        }
        if (seasonPapi != null) {
            seasonPapi.unregister();
            seasonPapi = null;
        }
        if (statsPapi != null) {
            statsPapi.unregister();
            statsPapi = null;
        }
        if (frontierPapi != null) {
            frontierPapi.unregister();
            frontierPapi = null;
        }
        if (frontier != null) {
            frontier.flush();
            frontier = null;
        }
        if (activity != null) {
            activity.shutdown();
            activity = null;
        }
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
        offCommands.clear();
        // Thư viện đồ (mọi server): dựng trước để cửa hàng, phần thưởng dùng; /dmm luudo|laydo|thuvien
        itemLibrary = new ItemLibrary(this);
        Shared.library = itemLibrary;
        // Người chơi Bedrock (đã xác thực qua Xbox + Floodgate) không phải nhập mật khẩu AuthMe.
        // Chỉ bật trên server có AuthMe (hiện tại là lobby).
        if (getServer().getPluginManager().isPluginEnabled("AuthMe")) {
            getServer().getPluginManager().registerEvents(new BedrockAuthListener(this), this);
            getLogger().info("Bedrock auto-login: bật (AuthMe có trên server này)");
            if (getConfig().getBoolean("register-limit.enabled", true)) {
                boolean ok = new RegisterLimit(this, getConfig().getConfigurationSection("register-limit")).hook();
                getLogger().info("Giới hạn đăng ký theo IP (tổng mỗi IP, có danh sách miễn): " + ok);
            }
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
        } else if (menuCommand != null && getConfig().getBoolean("player-menu.enabled")) {
            // Server chế độ: /menu (và Shift+F) mở menu chính: điểm danh, chợ, xưởng, balo, tủ skin, rank… (player-menu)
            PlayerMenu pm = new PlayerMenu(this, getConfig().getConfigurationSection("player-menu"));
            getServer().getPluginManager().registerEvents(pm, this);
            menuCommand.setExecutor((sender, cmd, label, args) -> {
                if (sender instanceof Player p) {
                    pm.open(p);
                }
                return true;
            });
            getLogger().info("Menu chính (/menu, Shift+F): bật, " + pm.size() + " nút");
        } else if (menuCommand != null) {
            offCommands.add("menu");
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

        // Cài đặt cá nhân /caidat (mọi server, lưu ở LuckPerms meta): player-settings.enabled (mặc định bật)
        PluginCommand caidatCmd = getCommand("caidat");
        if (getConfig().getBoolean("player-settings.enabled", true) && getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            playerSettings = new PlayerSettings(this, getConfig().getString("join-notes-title", ""));
            getServer().getPluginManager().registerEvents(playerSettings, this);
            playerSettings.start();
            if (caidatCmd != null) {
                PlayerSettings ps = playerSettings;
                caidatCmd.setExecutor((sender, cmd, label, args) -> ps.command(sender, args));
            }
            getLogger().info("Cài đặt cá nhân: " + PlayerSettings.OPTS.size() + " công tắc");
        }
        // Rank donate (tước vị) + thông báo vào/ra: donate-ranks.enabled
        PluginCommand rankCmd = getCommand("rank");
        DonateQr vangQr = null;
        if (getConfig().getBoolean("donate-ranks.enabled") && getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            DonateRanks ranks = new DonateRanks(this, getConfig().getConfigurationSection("donate-ranks"));
            getServer().getPluginManager().registerEvents(ranks, this);
            ranks.syncSkins(getDataFolder());
            // Ủng hộ bằng QR trong game: /rank mua (donate-qr.enabled, cần PANEL_URL + PANEL_SITE_KEY trong môi trường)
            if (getConfig().getBoolean("donate-qr.enabled")) {
                DonateQr qr = new DonateQr(this, getConfig().getConfigurationSection("donate-qr"), ranks.tiers());
                getServer().getPluginManager().registerEvents(qr, this);
                qr.start();
                ranks.setQr(qr);
                vangQr = qr;
                if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                    vangPapi = VangPapi.register();   // %dmmvang_so_du% cho bảng bên phải
                }
                getLogger().info("Ủng hộ bằng QR trong game: " + (qr.ready() ? "bật" : "thiếu PANEL_URL/PANEL_SITE_KEY, chỉ hiện link web"));
            }
            if (rankCmd != null) {
                rankCmd.setExecutor((sender, cmd, label, args) -> ranks.command(sender, args));
            }
            getLogger().info("Rank donate + thông báo vào/ra: bật");
            DonateQr vq = vangQr;
            bind("vang", vq == null ? null : vq::vangCommand, "Vàng");
        } else if (rankCmd != null) {
            offCommands.add("rank");
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
            guideCmd.setTabCompleter((sender, cmd, label, args) -> args.length == 1 ? guide.tab(args[0]) : java.util.List.of());
        }

        // Chat có prefix LuckPerms (lobby không có EssentialsX Chat): chat-format.enabled
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

        // Chặn /pay cho chính mình (Essentials trừ rồi cộng lại, người chơi tưởng lỗi)
        if (getServer().getPluginManager().isPluginEnabled("Essentials")) {
            getServer().getPluginManager().registerEvents(new PaySelfGuard(), this);
            getServer().getPluginManager().registerEvents(new PetNameGuard(), this);
        }

        // Nghề Jobs Reborn (21/10): gõ /jobs (không thêm gì) là mở menu chọn nghề thay vì danh sách lệnh
        if (Integrations.jobsReborn()) {
            getServer().getPluginManager().registerEvents(new JobsMenu(), this);
        }

        // Chống đặt rồi phá block để lấy tiền nghề (Jobs): job-guard.enabled
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
            getServer().getPluginManager().registerEvents(cleaner, this);
            cleaner.start();
        }

        // Đồ/sách phù phép vượt cấp (chỉ admin tạo; đe giữ cấp của sách): over-enchant.enabled
        overEnchant = getConfig().getBoolean("over-enchant.enabled")
                ? new OverEnchant(getConfig().getInt("over-enchant.max-level", 50)) : null;
        if (overEnchant != null) {
            getServer().getPluginManager().registerEvents(overEnchant, this);
            getLogger().info("Đồ phù phép vượt cấp: bật (tối đa cấp " + getConfig().getInt("over-enchant.max-level", 50) + ")");
        }

        // Dịch chuyển ngẫu nhiên /tpr + NPC ở spawn (banghoi): rtp.enabled
        PluginCommand tprCmd = getCommand("tpr");
        rtp = getConfig().getBoolean("rtp.enabled") ? new RandomTeleport(this, getConfig().getConfigurationSection("rtp")) : null;
        if (rtp != null) {
            getServer().getPluginManager().registerEvents(rtp, this);
            if (tprCmd != null) {
                tprCmd.setExecutor((sender, cmd, label, args) -> rtp.command(sender, args));
                tprCmd.setTabCompleter((sender, cmd, label, args) -> rtp.tab(args));
            }
            getLogger().info("Dịch chuyển ngẫu nhiên /tpr: bật (" + rtp.zoneCount() + " khu)");
        } else if (tprCmd != null) {
            offCommands.add("tpr");
            tprCmd.setExecutor((sender, cmd, label, args) -> {
                sender.sendMessage("Dịch chuyển ngẫu nhiên chưa bật trên server này.");
                return true;
            });
        }

        // Giờ chơi thật (không tính AFK) cho dân hoạt động, quân hàm, lập thị trấn (banghoi): active-play.enabled
        activity = getConfig().getBoolean("active-play.enabled") ? new Activity(this, getConfig().getConfigurationSection("active-play")) : null;
        LowPop.init(activity, getConfig().getConfigurationSection("low-population"));
        JoinNotes.init(this, getConfig().getString("join-notes-title", "DuckyMoMo"));
        if (activity != null) {
            getServer().getPluginManager().registerEvents(activity, this);
            activity.start();
        }
        PluginCommand hdCmd = getCommand("hoatdong");
        if (activity == null) {
            offCommands.add("hoatdong");
        }
        if (hdCmd != null) {
            hdCmd.setExecutor((sender, cmd, label, args) -> {
                if (activity == null) {
                    sender.sendMessage("Giờ chơi thật chưa bật trên server này.");
                    return true;
                }
                return activity.command(sender, args);
            });
        }

        // Luật chiến tranh: PvP đất hoang bật/tắt, điều kiện phong quân hàm, tài khoản phụ (banghoi): pvp-rules.enabled (cần active-play)
        warRules = activity != null && getConfig().getBoolean("pvp-rules.enabled")
                ? new WarRules(this, activity, getConfig().getConfigurationSection("pvp-rules")) : null;
        if (warRules != null) {
            getServer().getPluginManager().registerEvents(warRules, this);
            getLogger().info("Luật chiến tranh: " + warRules.describe());
        }
        PluginCommand altCmd = getCommand("taikhoanphu");
        if (warRules == null) {
            offCommands.add("taikhoanphu");
        }
        if (altCmd != null) {
            altCmd.setExecutor((sender, cmd, label, args) -> {
                if (warRules == null) {
                    sender.sendMessage("Chỉ dùng ở Bang Hội Chiến.");
                    return true;
                }
                return warRules.selfCommand(sender, args);
            });
        }

        // Vũ khí nổ lên người chơi: crystal, respawn anchor, giường, xe goòng TNT (Bang Hội Chiến: explosive-pvp.enabled)
        if (getConfig().getBoolean("explosive-pvp.enabled")) {
            ExplosionRules boom = new ExplosionRules(getConfig().getConfigurationSection("explosive-pvp"));
            getServer().getPluginManager().registerEvents(boom, this);
            getLogger().info("Vũ khí nổ lên người chơi: " + boom.describe());
        }

        // Luật đất đai Towny (lập thị trấn/quốc gia, vòng spawn, đổi thị trấn, đất theo dân hoạt động, cắm cờ): town-rules.enabled
        // Liên minh Hoà Bình (banghoi): peace-alliance.enabled
        peace = getConfig().getBoolean("peace-alliance.enabled") && getServer().getPluginManager().isPluginEnabled("Towny")
                ? new PeaceAlliance(this, getConfig().getConfigurationSection("peace-alliance")) : null;
        if (peace != null) {
            getServer().getPluginManager().registerEvents(peace, this);
            peace.start();
        }
        PluginCommand peaceCmd = getCommand("hoabinh");
        if (peace == null) {
            offCommands.add("hoabinh");
        }
        if (peaceCmd != null) {
            peaceCmd.setExecutor((sender, cmd, label, args) -> {
                if (peace == null) {
                    sender.sendMessage("Liên minh Hoà Bình chỉ có ở Bang Hội Chiến.");
                    return true;
                }
                return peace.command(sender, args);
            });
        }

        if (activity != null && getConfig().getBoolean("town-rules.enabled") && getServer().getPluginManager().isPluginEnabled("Towny")) {
            TownRules townRules = new TownRules(this, activity, peace, getConfig().getConfigurationSection("town-rules"));
            getServer().getPluginManager().registerEvents(townRules, this);
            townRules.start();
        }

        // Chơi lại từ đầu /choilai (banghoi): player-reset.enabled
        PlayerReset reset = activity != null && getConfig().getBoolean("player-reset.enabled")
                ? new PlayerReset(this, activity, getConfig().getConfigurationSection("player-reset")) : null;
        if (reset != null) {
            getServer().getPluginManager().registerEvents(reset, this);
            getLogger().info("Chơi lại từ đầu /choilai: bật");
        }
        // Đợt 3-4: Hội đồng Hoà Bình, tổ chức, bảng xếp hạng, án + Hội Bàn Tròn, cửa hàng trang trí (banghoi)
        PeaceCouncil council = peace != null && activity != null && getConfig().getBoolean("council.enabled")
                ? new PeaceCouncil(this, peace, activity, getConfig().getConfigurationSection("council")) : null;
        if (council != null) {
            council.start();
        }
        Organizations orgs = council != null && getConfig().getBoolean("organizations.enabled")
                ? new Organizations(this, peace, council, getConfig().getConfigurationSection("organizations"),
                        getConfig().getString("leaderboard.season", "1")) : null;
        if (orgs != null) {
            getServer().getPluginManager().registerEvents(orgs, this);
            orgs.start();
        }
        java.util.Set<String> ranks = new java.util.HashSet<>();
        getConfig().getStringList("pvp-rules.military-ranks").forEach(x -> ranks.add(x.toLowerCase(java.util.Locale.ROOT)));
        Leaderboard board = activity != null && getConfig().getBoolean("leaderboard.enabled") && getServer().getPluginManager().isPluginEnabled("Towny")
                ? new Leaderboard(this, activity, warRules, peace == null ? "" : peace.nationName(), getConfig().getConfigurationSection("leaderboard"), ranks) : null;
        if (board != null) {
            getServer().getPluginManager().registerEvents(board, this);
            board.start();
        }
        // Vinh danh mùa: Bang Hội Chiến chụp người thắng ra thư mục dùng chung (hall-of-fame.snapshot), sảnh dựng Quảng trường Danh vọng (hall-of-fame.display)
        org.bukkit.configuration.ConfigurationSection hof = getConfig().getConfigurationSection("hall-of-fame");
        HallOfFameSnapshot hofSnap = hof != null && hof.getBoolean("snapshot") && board != null
                ? new HallOfFameSnapshot(this, board, hof, getConfig().getString("leaderboard.season", "1")) : null;
        if (hofSnap != null) {
            hofSnap.start();
        }
        hallDisplay = hof != null && hof.getBoolean("display") ? new HallOfFameDisplay(this, hof) : null;
        if (hallDisplay != null) {
            hallDisplay.start();
        }
        HallOfFameDisplay hd = hallDisplay;
        bind("vinhdanh", hd != null ? hd::command : hofSnap != null ? hofSnap::command : null, "Quảng trường Danh vọng");
        org.bukkit.command.PluginCommand vdCmd = getCommand("vinhdanh");
        if (vdCmd != null) {
            vdCmd.setTabCompleter(hd == null ? null : (sender, cmd, label, a) -> hd.tab(sender, a));
        }
        Justice justice = council != null && board != null && getConfig().getBoolean("justice.enabled")
                ? new Justice(this, peace, council, orgs, board, warRules, getConfig().getConfigurationSection("justice")) : null;
        if (justice != null) {
            getServer().getPluginManager().registerEvents(justice, this);
            justice.start();
        }
        // Kinh đô: đất thị trấn thủ đô của Liên minh Hoà Bình; chưa lập liên minh thì tạm tính vòng quanh spawn (town-rules)
        java.util.List<Integer> sp = getConfig().getIntegerList("town-rules.spawn");
        int ring = getConfig().getInt("town-rules.reserved-spawn-radius", 300);
        PeaceAlliance pa = peace;
        java.util.function.Predicate<org.bukkit.Location> ringAt = loc -> {
            if (sp.size() < 2 || !loc.getWorld().getName().equals(getConfig().getString("town-rules.world", "world"))) {
                return false;
            }
            double dx = loc.getX() - sp.get(0);
            double dz = loc.getZ() - sp.get(1);
            return dx * dx + dz * dz <= (double) ring * ring;
        };
        java.util.function.Predicate<org.bukkit.Location> inCapital = loc -> pa != null && pa.nation() != null ? pa.inCapital(loc) : ringAt.test(loc);
        if (pa != null) {
            pa.spawnRing = ringAt;
        }
        DecorShop shop = board != null && getConfig().getBoolean("decor-shop.enabled")
                ? new DecorShop(this, board, inCapital, getConfig().getConfigurationSection("decor-shop"), itemLibrary) : null;
        if (shop != null) {
            getServer().getPluginManager().registerEvents(shop, this);
        }
        decorShop = shop;
        bind("hoidong", council == null ? null : council::command, "Hội đồng Hoà Bình");
        bind("tochuc", orgs == null ? null : orgs::command, "Tổ chức hoà bình");
        bind("quangcao", orgs == null ? null : orgs::adCommand, "Quảng cáo Thương Hội");
        bind("sukien", orgs == null ? null : orgs::eventCommand, "Sự kiện");
        bind("bxh", board == null ? null : board::command, "Bảng xếp hạng");
        bind("binhchon", board == null ? null : board::voteCommand, "Bình chọn Kiến Trúc Sư");
        bind("danhhieu", board == null ? null : board::titleCommand, "Danh hiệu");
        bind("bantron", justice == null ? null : justice::command, "Hội Bàn Tròn");
        // Treo thưởng /treothuong: người chơi treo + hệ thống tự treo chuỗi giết (Bang Hội Chiến: bounty.enabled, cần Vault)
        Bounty bounty = getConfig().getBoolean("bounty.enabled") && getConfig().isConfigurationSection("bounty")
                ? new Bounty(this, getConfig().getConfigurationSection("bounty"), warRules, peace) : null;
        if (bounty != null) {
            getServer().getPluginManager().registerEvents(bounty, this);
            bounty.start();
        }
        bind("treothuong", bounty == null ? null : bounty::command, "Treo thưởng");
        PluginCommand ttCmd = getCommand("treothuong");
        if (ttCmd != null) {
            Bounty bb = bounty;
            ttCmd.setTabCompleter(bb == null ? null : (sender, cmd, label, args) -> bb.tab(args));
        }
        bind("cuahang", shop == null ? null : shop::command, "Cửa hàng trang trí");

        // Pet chiến đấu MyPet: giảm sát thương lên người chơi, bốc thăm phiên công thành cho pet (Bang Hội Chiến: pet-combat.enabled)
        PetCombat petCombat = getConfig().getBoolean("pet-combat.enabled") && getConfig().isConfigurationSection("pet-combat")
                && getServer().getPluginManager().isPluginEnabled("MyPet")
                ? new PetCombat(this, getConfig().getConfigurationSection("pet-combat")) : null;
        if (petCombat != null) {
            getServer().getPluginManager().registerEvents(petCombat, this);
            petCombat.start();
            getLogger().info("Pet chiến đấu: " + petCombat.describe());
        }
        bind("lichcongthanh", petCombat == null ? null : petCombat::command, "Lịch công thành");

        // Bắt pet theo xác suất (pet-capture.enabled) + /bxhpet bảng xếp hạng pet (cần MyPet)
        boolean myPetOn = getServer().getPluginManager().isPluginEnabled("MyPet");
        PetCapture petCapture = myPetOn && getConfig().getBoolean("pet-capture.enabled") && getConfig().isConfigurationSection("pet-capture")
                ? new PetCapture(this, getConfig().getConfigurationSection("pet-capture")) : null;
        if (petCapture != null && petCapture.ready()) {
            getServer().getPluginManager().registerEvents(petCapture, this);
            getLogger().info("Bắt pet theo xác suất: " + petCapture.describe());
        } else {
            petCapture = null;
        }
        // Cho pet ăn cá câu (EvenMoreFish): no, hồi máu, kinh nghiệm (pet-fish.enabled)
        if (myPetOn && getConfig().getBoolean("pet-fish.enabled") && getConfig().isConfigurationSection("pet-fish")) {
            PetFish petFish = new PetFish(this, getConfig().getConfigurationSection("pet-fish"));
            getServer().getPluginManager().registerEvents(petFish, this);
            getLogger().info("Pet ăn cá: " + petFish.describe());
        }
        // Đặc quyền Thăng Cấp cho pet: +15% kinh nghiệm, hồi sinh nhanh gấp đôi (quyền do /capdo cấp)
        if (myPetOn) {
            PetPerks petPerks = new PetPerks(this);
            getServer().getPluginManager().registerEvents(petPerks, this);
            petPerks.start();
        }
        PetCapture charmOwner = petCapture;
        bind("buabatpet", charmOwner == null ? null : charmOwner::charmCommand, "Bùa bắt pet");
        PetTop petTop = myPetOn ? new PetTop(this, petCapture) : null;
        bind("bxhpet", petTop == null ? null : petTop::command, "Bảng xếp hạng pet");

        // Đấu Trường /dautruong: người + pet MyPet 1v1, 2v2 trong thế giới riêng (Bang Hội Chiến: arena.enabled)
        arena = getConfig().getBoolean("arena.enabled") && getConfig().isConfigurationSection("arena")
                ? new Arena(this, getConfig().getConfigurationSection("arena")) : null;
        if (arena != null) {
            getServer().getPluginManager().registerEvents(arena, this);
            getLogger().info("Đấu Trường: " + arena.describe());
            if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                arenaPapi = arena.placeholders();
                arenaPapi.register();
            }
        }
        Arena ar = arena;
        bind("dautruong", ar == null ? null : ar::command, "Đấu Trường");

        // Thưởng vote server (Votifier ở banghoi-1; vote từ VPS qua relay socat 8192)
        VoteRewards vote = getConfig().getBoolean("vote.enabled") ? new VoteRewards(this, getConfig().getConfigurationSection("vote")) : null;
        if (vote != null && !vote.hook()) {
            getLogger().info("Thưởng vote: chưa có Votifier, /vote vẫn hiện link");
        }
        bind("vote", vote == null ? null : vote::command, "Vote server");
        // Khai hoang vùng mới /khaihoang: quỹ chung từ /sell + góp, đủ mốc thì mở vùng (Bang Hội Chiến: frontier.enabled)
        frontier = getConfig().getBoolean("frontier.enabled") && getConfig().isConfigurationSection("frontier")
                ? new Frontier(this, getConfig().getConfigurationSection("frontier")) : null;
        if (frontier != null) {
            frontier.start();
            if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                frontierPapi = frontier.placeholders();
                frontierPapi.register();
            }
        }
        Frontier fr = frontier;
        bind("khaihoang", fr == null ? null : fr::command, "Khai hoang vùng mới");

        // Giới hạn tiền bán cho server mỗi ngày (21/10, chống lạm phát): /sell, Rìu Bán của Xưởng, món Tinh Luyện (sell-limit.enabled)
        SellLimit sellLimit = getConfig().getBoolean("sell-limit.enabled") && getConfig().isConfigurationSection("sell-limit")
                ? new SellLimit(this, getConfig().getConfigurationSection("sell-limit")) : null;
        if (sellLimit != null) {
            getServer().getPluginManager().registerEvents(sellLimit, this);
            sellLimit.start();
        }

        // Xưởng chế tạo /xuong: đồ có lượt dùng + hạn (Bang Hội Chiến: workshop.enabled)
        Workshop workshop = getConfig().getBoolean("workshop.enabled") && getConfig().isConfigurationSection("workshop")
                ? new Workshop(this, getConfig().getConfigurationSection("workshop")) : null;
        if (workshop != null) {
            getServer().getPluginManager().registerEvents(workshop, this);
            workshop.start();
            getLogger().info("Xưởng chế tạo: " + workshop.size() + " món");
        }
        bind("xuong", workshop == null ? null : workshop::command, "Xưởng chế tạo");

        // Chợ đen /choden (thu mua + chợ người chơi /ah) (Bang Hội Chiến: orders.enabled, cần Vault)
        Orders orders = getConfig().getBoolean("orders.enabled") && getConfig().isConfigurationSection("orders")
                ? new Orders(this, getConfig().getConfigurationSection("orders")) : null;
        if (orders != null) {
            getServer().getPluginManager().registerEvents(orders, this);
            orders.start();
        }
        bind("choden", orders == null ? null : orders::hubCommand, "Chợ đen");   // 21/10: một lệnh cho cả chợ (bỏ /donhang)
        PluginCommand chCmd = getCommand("choden");
        if (chCmd != null) {
            Orders oo = orders;
            chCmd.setTabCompleter(oo == null ? null : (sender, cmd, label, args) -> oo.hubTab(args));
        }
        // Đăng nhập trang web bằng tài khoản game: /web cấp mã, trang web kiểm tra qua RCON (/dmm webma)
        WebLogin webLogin = new WebLogin(getConfig().getConfigurationSection("web-login"));
        bind("web", webLogin::command, "Đăng nhập web");
        getServer().getPluginManager().registerEvents(new ItemInspect(), this);   // phím F trong túi/menu, chọn món trade
        getServer().getPluginManager().registerEvents(new AttackSpeed(), this);   // tốc đánh món Thư viện đồ (OCM bỏ hồi đòn)
        getServer().getPluginManager().registerEvents(new SkillEnchants(), this);   // phù phép riêng Lướt / Hồi máu / Cầu lửa
        bind("xemdo", ItemInspect::command, "Xem đồ");   // in đủ tooltip món ra chat (tooltip dài quá màn hình)
        if (council != null) {
            getLogger().info("Hội đồng Hoà Bình, tổ chức, bảng xếp hạng, Hội Bàn Tròn, cửa hàng: "
                    + (orgs != null) + "/" + (board != null) + "/" + (justice != null) + "/" + (shop != null));
        }

        PluginCommand resetCmd = getCommand("choilai");
        if (reset == null) {
            offCommands.add("choilai");
        }
        if (resetCmd != null) {
            resetCmd.setExecutor((sender, cmd, label, args) -> {
                if (reset == null) {
                    sender.sendMessage("Chơi lại từ đầu chỉ có ở Bang Hội Chiến.");
                    return true;
                }
                return reset.command(sender, args);
            });
        }

        // Xuất mọi lệnh của server cho trang Cẩm nang (plugins/DuckyMoMoCore/commands.json), 30 giây sau khi khởi động
        commandDump = new CommandDump(this, offCommands);
        if (!reloading) {
            commandDump.schedule();
        }

        // Lệnh admin /dmm. Trang trí map (cây, cỏ, công trình) chỉ bật ở server có decorate.enabled (banghoi-1)
        decorator = getConfig().getBoolean("decorate.enabled") ? new MapDecorator(this) : null;
        structures = decorator != null ? new StructurePlacer(this) : null;
        // Thư viện đồ (mọi server): /dmm luudo|laydo|thuvien; trang web /thuvien đọc thuvien-meta.json
        if (!reloading) {
            getServer().getScheduler().runTaskLater(this, itemLibrary::exportMeta, 40L);
        }
        rewards = new Rewards(this, itemLibrary);
        if (frontier != null) {
            frontier.setRewards(rewards);
        }
        // Sổ thành tích (achievements): mục tiêu chơi một mình, tự đếm, đạt là nhận quà
        achievements = getConfig().getBoolean("achievements.enabled") && getConfig().isConfigurationSection("achievements")
                ? new Achievements(this, activity, rewards, getConfig().getConfigurationSection("achievements")) : null;
        if (achievements != null) {
            getServer().getPluginManager().registerEvents(achievements, this);
            achievements.start();
            getLogger().info("Sổ thành tích: " + achievements.describe());
        }
        Achievements ach = achievements;
        bind("thanhtich", ach == null ? null : ach::command, "Sổ thành tích");
        // Thăng Cấp (level-up, Bang Hội Chiến): cấp riêng của server, xu + giờ chơi + hạ gục, điểm đổi đặc quyền
        levelUp = getConfig().getBoolean("level-up.enabled") && getConfig().isConfigurationSection("level-up")
                ? new LevelUp(this, activity, rewards, getConfig().getConfigurationSection("level-up")) : null;
        if (levelUp != null) {
            getServer().getPluginManager().registerEvents(levelUp, this);
            levelUp.start();
            if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                levelPapi = LevelUpPapi.register();
            }
            getLogger().info("Thăng Cấp: " + levelUp.describe());
        }
        LevelUp lvu = levelUp;
        bind("capdo", lvu == null ? null : lvu::command, "Thăng Cấp");
        // Chơi một mình: bản đồ kho báu (treasure), lệnh săn quái (wanted), Thái Ấp đất riêng (thai-ap) — Bang Hội Chiến
        treasure = getConfig().getBoolean("treasure.enabled") && getConfig().isConfigurationSection("treasure")
                ? new TreasureHunt(this, rewards, getConfig().getConfigurationSection("treasure")) : null;
        if (treasure != null && treasure.ready()) {
            getServer().getPluginManager().registerEvents(treasure, this);
            treasure.start();
            getLogger().info("Kho báu: " + treasure.describe());
        } else {
            treasure = null;
        }
        TreasureHunt th = treasure;
        bind("khobau", th == null ? null : th::command, "Bản đồ kho báu");
        wanted = getConfig().getBoolean("wanted.enabled") && getConfig().isConfigurationSection("wanted")
                ? new WantedBoard(this, rewards, getConfig().getConfigurationSection("wanted")) : null;
        if (wanted != null && wanted.ready()) {
            getServer().getPluginManager().registerEvents(wanted, this);
            wanted.start();
            getLogger().info("Lệnh săn: " + wanted.describe());
        } else {
            wanted = null;
        }
        WantedBoard wbd = wanted;
        bind("lenhsan", wbd == null ? null : wbd::command, "Lệnh săn");
        thaiAp = getConfig().getBoolean("thai-ap.enabled") && getConfig().isConfigurationSection("thai-ap")
                ? new ThaiAp(this, getConfig().getConfigurationSection("thai-ap")) : null;
        if (thaiAp != null) {
            getServer().getPluginManager().registerEvents(thaiAp, this);
            thaiAp.start();
            getLogger().info("Thái Ấp: " + thaiAp.describe());
        }
        ThaiAp ta = thaiAp;
        bind("thaiap", ta == null ? null : ta::command, "Thái Ấp");
        // Tinh Luyện (tinh-luyen, Bang Hội Chiến): đổi một chiều khối thường → món tinh luyện (trade, Xưởng, bán); Xu Quái rơi từ quái
        Refining refining = getConfig().getBoolean("tinh-luyen.enabled") && getConfig().isConfigurationSection("tinh-luyen")
                ? new Refining(this, getConfig().getConfigurationSection("tinh-luyen")) : null;
        Shared.refining = refining;
        if (refining != null) {
            getServer().getPluginManager().registerEvents(refining, this);
            getLogger().info("Tinh luyện: " + refining.describe());
        }
        bind("tinhluyen", refining == null ? null : refining::command, "Tinh Luyện");
        // Danh phận (play-ranks): rank chơi miễn phí Dân Tị Nạn → Dân → Lương Dân → Hào Kiệt, bấm nhận; tự đồng bộ nhóm LuckPerms
        PlayRanks playRanks = getConfig().getBoolean("play-ranks.enabled") && getConfig().isConfigurationSection("play-ranks")
                ? new PlayRanks(this, activity, getConfig().getConfigurationSection("play-ranks")) : null;
        if (playRanks != null) {
            getServer().getPluginManager().registerEvents(playRanks, this);
            getServer().getScheduler().runTaskLater(this, playRanks::sync, 60L);
            getLogger().info("Danh phận: " + playRanks.describe());
        }
        bind("danhphan", playRanks == null ? null : playRanks::command, "Danh phận");
        PluginCommand dpCmd = getCommand("danhphan");
        if (dpCmd != null) {
            dpCmd.setTabCompleter(playRanks == null ? null : (sender, cmd, label, args) -> playRanks.tab(sender, args));
        }
        // Chương trình Media (/media): đăng ký, báo live, server tự kiểm (online thật + kênh đang live), chỉ tiêu tuần, trang quản trị › Media
        MediaProgram media = getConfig().getBoolean("media.enabled") && getConfig().isConfigurationSection("media")
                ? new MediaProgram(this, activity, getConfig().getConfigurationSection("media")) : null;
        if (media != null) {
            getServer().getPluginManager().registerEvents(media, this);
            media.start();
            Shared.media = media;
            getLogger().info("Sứ Giả: " + media.describe());
        }
        bind("media", media == null ? null : media::command, "Sứ Giả");
        PluginCommand mediaCmd = getCommand("media");
        if (mediaCmd != null) {
            mediaCmd.setTabCompleter(media == null ? null : (sender, cmd, label, args) -> media.tab(sender, args));
        }
        // Balo cá nhân (/balo): số balo theo quyền duckymomo.balo.<N> (danh phận cấp)
        Backpack backpack = getConfig().getBoolean("backpack.enabled")
                ? new Backpack(this, getConfig().getConfigurationSection("backpack")) : null;
        Shared.backpack = backpack;
        if (backpack != null) {
            getServer().getPluginManager().registerEvents(backpack, this);
        }
        bind("balo", backpack == null ? null : backpack::command, "Balo");
        PluginCommand baloCmd = getCommand("balo");
        if (baloCmd != null) {
            baloCmd.setTabCompleter(backpack == null ? null : (sender, cmd, label, args) -> backpack.tab(sender, args));
        }
        PluginCommand tlCmd = getCommand("tinhluyen");
        if (tlCmd != null) {
            tlCmd.setTabCompleter(refining == null ? null : (sender, cmd, label, args) -> refining.tab(sender, args));
        }
        // Cổng Dungeon (dungeon-gate): khoá từng gói EliteMobs bằng quyền riêng, mở theo chuỗi chìa khoá / ẩn / may mắn / vé / quỹ chung
        dungeonGate = getConfig().getBoolean("dungeon-gate.enabled") && getConfig().isConfigurationSection("dungeon-gate")
                ? new DungeonGate(this, rewards, getConfig().getConfigurationSection("dungeon-gate")) : null;
        if (dungeonGate != null && dungeonGate.ready()) {
            getServer().getPluginManager().registerEvents(dungeonGate, this);
            dungeonGate.start();
            getLogger().info("Cổng Dungeon: " + dungeonGate.describe());
        } else {
            dungeonGate = null;
        }
        // Đồ rơi boss EliteMobs chọn từ thư viện đồ (boss-roi.yml, trang Boss › Đồ rơi)
        if (getServer().getPluginManager().getPlugin("EliteMobs") != null) {
            bossDrops = new BossDrops(this);
            bossDrops.start();
        }
        DungeonGate dgt = dungeonGate;
        bind("dungeon", dgt == null ? null : dgt::command, "Cổng Dungeon");
        // Cổng Hoang (wild-gates): cổng dungeon ngụy trang xuất hiện dần trên bản đồ, thị trấn giữ cổng thu phí
        wildGates = dungeonGate != null && getConfig().getBoolean("wild-gates.enabled") && getConfig().isConfigurationSection("wild-gates")
                && getServer().getPluginManager().isPluginEnabled("Towny")
                ? new WildGates(this, getConfig().getConfigurationSection("wild-gates")) : null;
        if (wildGates != null && wildGates.ready()) {
            getServer().getPluginManager().registerEvents(wildGates, this);
            wildGates.start();
            getLogger().info("Cổng Hoang: " + wildGates.describe());
        } else {
            wildGates = null;
        }
        WildGates wgs = wildGates;
        bind("conghoang", wgs == null ? null : wgs::command, "Cổng Hoang");
        // Lữ khách (wander-npcs): NPC Citizens lang thang ngoài đất hoang, kể chuyện về nơi phía sau Cổng Hoang (lời thoại: BetonQuest)
        wanderers = wildGates != null && getConfig().getBoolean("wander-npcs.enabled") && getConfig().isConfigurationSection("wander-npcs")
                && getServer().getPluginManager().isPluginEnabled("Citizens")
                ? new Wanderers(this, getConfig().getConfigurationSection("wander-npcs")) : null;
        if (wanderers != null && wanderers.ready()) {
            wanderers.start();
            if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                wanderPapi = WanderersPapi.register();
            }
            getLogger().info("Lữ khách: " + wanderers.describe());
        } else {
            wanderers = null;
        }
        Wanderers wds = wanderers;
        bind("lukhach", wds == null ? null : wds::command, "Lữ khách");
        // Kỹ năng nghề AuraSkills làm lại mỗi mùa (aura-skills.reset-each-season): xoá cấp khi từng người vào game trong mùa mới
        if (getConfig().getBoolean("aura-skills.reset-each-season", false) && getServer().getPluginManager().getPlugin("AuraSkills") != null) {
            SkillSeason skillSeason = new SkillSeason(this);
            getServer().getPluginManager().registerEvents(skillSeason, this);
            skillSeason.start();
            getLogger().info("Kỹ năng nghề: làm lại mỗi mùa");
        }

        // Mảnh sự kiện + cửa hàng đổi mảnh (Bang Hội Chiến: event-fragments.enabled); phần thưởng "manh" dùng module này
        FragmentShop fragments = getConfig().getBoolean("event-fragments.enabled")
                ? new FragmentShop(this, getConfig().getConfigurationSection("event-fragments"), rewards) : null;
        if (fragments != null) {
            getServer().getPluginManager().registerEvents(fragments, this);
            rewards.setFragments(fragments);
        }
        bind("doimanh", fragments == null ? null : fragments::command, "Đổi mảnh sự kiện");   // 21/10: đã bỏ khỏi plugin.yml (tắt)

        // Điểm danh hằng ngày (Bang Hội Chiến: daily-checkin.enabled)
        DailyCheckin checkin = getConfig().getBoolean("daily-checkin.enabled")
                ? new DailyCheckin(this, getConfig().getConfigurationSection("daily-checkin"), rewards) : null;
        if (checkin != null) {
            getServer().getPluginManager().registerEvents(checkin, this);
        }
        bind("diemdanh", checkin == null ? null : checkin::command, "Điểm danh");

        // Gói quà /kit (Bang Hội Chiến: goi-qua.enabled): thay kit Essentials, quà theo rank + gói lần đầu vào
        Kits kits = getConfig().getBoolean("goi-qua.enabled")
                ? new Kits(this, getConfig().getConfigurationSection("goi-qua"), rewards) : null;
        if (kits != null) {
            getServer().getPluginManager().registerEvents(kits, this);
            getLogger().info("Gói quà: " + kits.describe());
        }
        bind("goiqua", kits == null ? null : kits::command, "Gói quà");
        PluginCommand kitCmd = getCommand("goiqua");
        if (kitCmd != null) {
            kitCmd.setTabCompleter(kits == null ? null : (sender, cmd, label, args) -> kits.tab(sender, args));
        }
        if (kits == null) {
            // server không có Gói quà (Practice dùng /kit edit của Duels, sảnh…): trả tên /kit, /kits cho plugin khác.
            // Lệnh trong plugin.yml được đăng ký TRƯỚC onEnable, plugin bật sau (Duels, Essentials) sẽ nhận lại tên này.
            java.util.Map<String, org.bukkit.command.Command> known = getServer().getCommandMap().getKnownCommands();
            for (String l : List.of("kit", "kits", "duckymomocore:kit", "duckymomocore:kits")) {
                if (known.get(l) instanceof PluginCommand pc && pc.getPlugin() == this) {
                    known.remove(l);
                }
            }
        }

        // Nhiệm vụ hằng ngày (Bang Hội Chiến: daily-quests.enabled)
        quests = getConfig().getBoolean("daily-quests.enabled")
                ? new DailyQuests(this, getConfig().getConfigurationSection("daily-quests"), rewards) : null;
        if (quests != null) {
            getServer().getPluginManager().registerEvents(quests, this);
        }
        bind("nhiemvu", quests == null ? null : quests::command, "Nhiệm vụ hằng ngày");

        // Gacha (quay thưởng; 21/10 thay hẳn /vongquay): nhiều máy, mỗi máy một loại vé là món Thư viện đồ (điểm danh, chơi online…)
        // (Bang Hội Chiến: crates.enabled)
        crates = getConfig().getBoolean("crates.enabled")
                ? new Crates(this, getConfig().getConfigurationSection("crates"), rewards, activity, itemLibrary) : null;
        if (crates != null) {
            getServer().getPluginManager().registerEvents(crates, this);
            if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                gachaPapi = crates.placeholders();
                gachaPapi.register();
            }
            for (Player online : getServer().getOnlinePlayers()) {
                crates.migrateSpinTickets(online);   // dmm reload khi người chơi đang online
            }
        }
        bind("gacha", crates == null ? null : crates::command, "Gacha");

        // Mùa mưa / mùa khô: cây lớn nhanh/chậm, được mùa, mưa theo mùa, placeholder cho cá theo mùa (Bang Hội Chiến: seasons.enabled)
        Seasons seasons = getConfig().getBoolean("seasons.enabled") && getConfig().isConfigurationSection("seasons")
                ? new Seasons(this, getConfig().getConfigurationSection("seasons")) : null;
        if (seasons != null && !seasons.ready()) {
            getLogger().warning("Mùa: seasons.order trống hoặc sai, tắt");
            seasons = null;
        }
        // Kho lương công thành (siege-supply), món ăn ra trận (dishes), đặc sản vùng (region-specialty): Bang Hội Chiến, cần Towny
        boolean towny = getServer().getPluginManager().isPluginEnabled("Towny");
        Dishes dishes = getConfig().getBoolean("dishes.enabled") && getConfig().isConfigurationSection("dishes")
                ? new Dishes(this, getConfig().getConfigurationSection("dishes")) : null;
        if (dishes != null && dishes.ready()) {
            getServer().getPluginManager().registerEvents(dishes, this);
            getLogger().info("Món ăn ra trận: " + dishes.describe());
        } else {
            dishes = null;
        }
        bind("nauan", dishes == null ? null : dishes::command, "Nấu món ăn ra trận");
        supply = towny && getConfig().getBoolean("siege-supply.enabled") && getConfig().isConfigurationSection("siege-supply")
                ? new SiegeSupply(this, getConfig().getConfigurationSection("siege-supply")) : null;
        if (supply != null) {
            supply.start();
            getLogger().info("Kho lương công thành: " + supply.describe());
        }
        SiegeSupply klSupply = supply;
        bind("kholuong", klSupply == null ? null : klSupply::command, "Kho lương công thành");
        org.bukkit.command.PluginCommand klCmd = getCommand("kholuong");
        if (klCmd != null) {
            klCmd.setTabCompleter(klSupply == null ? null : (sender, cmd, label, a) -> klSupply.tab(a));
        }
        specialty = towny && getConfig().getBoolean("region-specialty.enabled") && getConfig().isConfigurationSection("region-specialty")
                ? new RegionSpecialty(this, getConfig().getConfigurationSection("region-specialty"), getConfig().getMapList("rtp.zones")) : null;
        if (specialty != null && specialty.ready()) {
            specialty.start();
            getLogger().info("Đặc sản vùng: " + specialty.describe());
        } else {
            specialty = null;
        }
        RegionSpecialty rsp = specialty;
        bind("dacsan", rsp == null ? null : rsp::command, "Đặc sản vùng");
        // Cân bằng chiến tranh (war-balance): nước lớn tốn hơn, kẻ yếu được cộng điểm, giới hạn thuộc địa, Bá chủ (cần Towny + SiegeWar)
        warBalance = towny && getServer().getPluginManager().isPluginEnabled("SiegeWar") && getConfig().getBoolean("war-balance.enabled")
                && getConfig().isConfigurationSection("war-balance") ? new WarBalance(this, getConfig().getConfigurationSection("war-balance")) : null;
        if (warBalance != null && warBalance.start()) {
            getLogger().info("Cân bằng chiến tranh: " + warBalance.describe());
        } else {
            warBalance = null;
        }
        WarBalance wbl = warBalance;
        bind("canbang", wbl == null ? null : wbl::command, "Cân bằng chiến tranh");
        // Lính đánh thuê (mercenary): người không thuộc nước nào được các nước thuê ra trận (cần Towny)
        mercenary = towny && getConfig().getBoolean("mercenary.enabled") && getConfig().isConfigurationSection("mercenary")
                ? new Mercenary(this, activity, warRules, getConfig().getConfigurationSection("mercenary")) : null;
        if (mercenary != null) {
            getServer().getPluginManager().registerEvents(mercenary, this);
            mercenary.start();
            getLogger().info("Lính đánh thuê: " + mercenary.describe());
        }
        Mercenary mc = mercenary;
        bind("linhdanhthue", mc == null ? null : mc::command, "Lính đánh thuê");
        org.bukkit.command.PluginCommand ldtCmd = getCommand("linhdanhthue");
        if (ldtCmd != null) {
            ldtCmd.setTabCompleter(mc == null ? null : (sender, cmd, label, a) -> mc.tab(a));
        }

        Seasons.INSTANCE = seasons;
        if (seasons != null) {
            getServer().getPluginManager().registerEvents(seasons, this);
            seasons.start();
            if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                seasonPapi = SeasonsPapi.create(seasons);
                seasonPapi.register();
            }
        }
        bind("mua", seasons == null ? null : seasons::command, "Mùa mưa / mùa khô");
        // Giết / chết / KDA của người chơi cho bảng bên phải: %dmmtoi_giet%, %dmmtoi_chet%, %dmmtoi_kda%
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            statsPapi = PlayerStatsPlaceholders.create();
            statsPapi.register();
        }
        PluginCommand muaCmd = getCommand("mua");
        if (muaCmd != null) {
            Seasons ss = seasons;
            muaCmd.setTabCompleter(ss == null ? null : (sender, cmd, label, args) -> ss.tab(sender, args));
        }
        PluginCommand dmm = getCommand("dmm");
        if (dmm != null) {
            dmm.setExecutor((sender, cmd, label, args) -> {
                if (args.length >= 1 && (args[0].equalsIgnoreCase("luudo") || args[0].equalsIgnoreCase("laydo")
                        || args[0].equalsIgnoreCase("thuvien"))) {
                    itemLibrary.command(sender, args);
                } else if (args.length >= 1 && args[0].equalsIgnoreCase("decorate")) {
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
                } else if (args.length >= 1 && (args[0].equalsIgnoreCase("enchant") || args[0].equalsIgnoreCase("sach"))) {
                    if (overEnchant == null) {
                        sender.sendMessage("Đồ vượt cấp chưa bật trên server này (config: over-enchant.enabled).");
                    } else {
                        overEnchant.command(sender, args);
                    }
                } else if (args.length >= 1 && args[0].equalsIgnoreCase("rtpnpc")) {
                    if (rtp == null) {
                        sender.sendMessage("Dịch chuyển ngẫu nhiên chưa bật trên server này (config: rtp.enabled).");
                    } else {
                        rtp.npcCommand(sender, args);
                    }
                } else if (args.length >= 1 && args[0].equalsIgnoreCase("shopnpc")) {
                    if (decorShop == null) {
                        sender.sendMessage("Cửa hàng chưa bật trên server này (config: decor-shop.enabled).");
                    } else {
                        decorShop.npcCommand(sender, args);
                    }
                } else if (args.length >= 1 && args[0].equalsIgnoreCase("hoabinh")) {
                    if (peace == null) {
                        sender.sendMessage("Liên minh Hoà Bình chưa bật trên server này (config: peace-alliance.enabled).");
                    } else {
                        peace.adminCommand(sender, args);
                    }
                } else if (args.length >= 1 && args[0].equalsIgnoreCase("phu")) {
                    if (warRules == null) {
                        sender.sendMessage("Luật chiến tranh chưa bật trên server này (config: pvp-rules.enabled).");
                    } else {
                        warRules.altCommand(sender, args);
                    }
                } else if (args.length >= 1 && args[0].equalsIgnoreCase("webma")) {
                    // chỉ console/RCON (trang web gọi): người chơi có quyền admin cũng không dùng để dò mã
                    if (sender instanceof org.bukkit.entity.Player || args.length < 3) {
                        sender.sendMessage("WEBMA SAI");
                    } else {
                        sender.sendMessage(webLogin.check(args[1], args[2]));
                    }
                } else if (args.length >= 1 && args[0].equalsIgnoreCase("choilaiweb")) {
                    // chỉ console/RCON: nút "Chơi lại từ đầu" trên trang web, mở bước xác nhận trong game
                    if (sender instanceof org.bukkit.entity.Player || args.length < 2) {
                        sender.sendMessage("CHOILAI SAI");
                    } else {
                        sender.sendMessage(reset == null ? "CHOILAI TAT" : reset.webRequest(args[1]));
                    }
                } else if (args.length >= 1 && args[0].equalsIgnoreCase("lenh")) {
                    sender.sendMessage("DuckyMoMoCore: đã xuất " + commandDump.dump() + " lệnh ra commands.json");
                } else if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
                    reload();
                    sender.sendMessage("DuckyMoMoCore: đã nạp lại config.yml");
                } else {
                    for (String line : DMM_HELP) {
                        sender.sendMessage(line);
                    }
                }
                return true;
            });
            dmm.setTabCompleter((sender, cmd, label, args) -> dmmTab(args));
        }
        if (!reloading && decorator != null && getConfig().getBoolean("decorate.auto-resume")) {
            // chạy tiếp sau khi server khởi động lại (nếu lần trước đang chạy dở)
            getServer().getScheduler().runTaskLater(this, () -> {
                decorator.resumeIfUnfinished();
                structures.resumeIfUnfinished();
            }, 200L);
        }
    }

    private static final List<String> DMM_HELP = List.of(
            "§6/dmm §7(DuckyMoMo, gõ dài: /duckymomo) §8— bấm Tab để gợi ý",
            "§e/dmm enchant <phù phép> <cấp> §7phù phép vượt cấp món đang cầm (0 = gỡ)",
            "§e/dmm sach <phù phép> <cấp> [người] §7tặng sách phù phép",
            "§e/dmm laydo <mã> [người] [số] §7lấy đồ trong Thư viện đồ",
            "§e/dmm luudo <mã> [tên gợi nhớ] §7lưu món đang cầm vào thư viện",
            "§e/dmm thuvien [nap] §7xem mã / kiểm tra thư viện",
            "§e/dmm clean §7dọn rác ngay   §e/dmm reload §7nạp lại config   §e/dmm lenh §7xuất danh sách lệnh",
            "§e/dmm rtpnpc|shopnpc [remove] §7đặt/gỡ NPC   §e/dmm phu add|remove|list §7tài khoản phụ",
            "§e/dmm hoabinh setup|claim <r> §7Liên minh Hoà Bình   §e/dmm decorate|structures start|pause|status|reset");
    private static final List<String> DMM_SUBS = List.of("enchant", "sach", "laydo", "luudo", "thuvien", "clean", "reload", "lenh",
            "rtpnpc", "shopnpc", "phu", "hoabinh", "decorate", "structures");

    /** Gợi ý Tab cho /dmm: lệnh con, tên phù phép, mã thư viện đồ, tên người chơi. */
    private List<String> dmmTab(String[] a) {
        List<String> opts = new java.util.ArrayList<>();
        String sub = a[0].toLowerCase(java.util.Locale.ROOT);
        java.util.function.Supplier<List<String>> players = () -> getServer().getOnlinePlayers().stream().map(org.bukkit.entity.Player::getName).toList();
        if (a.length == 1) {
            opts.addAll(DMM_SUBS);
        } else {
            switch (sub) {
                case "enchant", "sach" -> {
                    if (a.length == 2) {
                        for (org.bukkit.enchantments.Enchantment e : io.papermc.paper.registry.RegistryAccess.registryAccess()
                                .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT)) {
                            org.bukkit.NamespacedKey k = e.getKey();
                            opts.add("minecraft".equals(k.getNamespace()) ? k.getKey() : k.asString());
                        }
                    } else if (a.length == 3) {
                        opts.addAll(sub.equals("enchant") ? List.of("0", "5", "10", "20", "30", "50") : List.of("5", "10", "20", "30", "50"));
                    } else if (a.length == 4 && sub.equals("sach")) {
                        opts.addAll(players.get());
                    }
                }
                case "laydo" -> {
                    if (a.length == 2) {
                        opts.addAll(itemLibrary.ids());
                    } else if (a.length == 3) {
                        opts.addAll(players.get());
                    } else if (a.length == 4) {
                        opts.addAll(List.of("1", "16", "64"));
                    }
                }
                case "luudo" -> {
                    if (a.length == 2) {
                        opts.addAll(itemLibrary.ids());
                    }
                }
                case "thuvien" -> {
                    if (a.length == 2) {
                        opts.add("nap");
                    }
                }
                case "decorate", "structures" -> {
                    if (a.length == 2) {
                        opts.addAll(List.of("start", "pause", "status", "reset"));
                    }
                }
                case "rtpnpc", "shopnpc" -> {
                    if (a.length == 2) {
                        opts.add("remove");
                    }
                }
                case "phu" -> {
                    if (a.length == 2) {
                        opts.addAll(List.of("add", "remove", "list"));
                    } else if (a.length == 3) {
                        opts.addAll(players.get());
                    }
                }
                case "hoabinh" -> {
                    if (a.length == 2) {
                        opts.addAll(List.of("setup", "claim"));
                    }
                }
                default -> { }
            }
        }
        String last = a[a.length - 1].toLowerCase(java.util.Locale.ROOT);
        return opts.stream().filter(o -> o.toLowerCase(java.util.Locale.ROOT).startsWith(last)).sorted().toList();
    }

    /** Gắn lệnh: có module thì chạy, không thì báo chỉ có ở Bang Hội Chiến. */
    private void bind(String name, java.util.function.BiFunction<org.bukkit.command.CommandSender, String[], Boolean> run, String what) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            return;
        }
        if (run == null) {
            offCommands.add(name);
        }
        cmd.setExecutor((sender, c, label, args) -> {
            if (run == null) {
                sender.sendMessage(what + " chỉ có ở Bang Hội Chiến.");
                return true;
            }
            return run.apply(sender, args);
        });
    }

    @Override
    public void onDisable() {
        teardown();
    }
}
