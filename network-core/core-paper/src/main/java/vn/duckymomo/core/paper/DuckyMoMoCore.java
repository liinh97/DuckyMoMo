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
    private Activity activity;
    private PeaceAlliance peace;
    private DecorShop decorShop;
    private ItemLibrary itemLibrary;
    private Rewards rewards;
    private DailyQuests quests;
    private LuckySpin spin;
    private me.clip.placeholderapi.expansion.PlaceholderExpansion spinPapi;
    private me.clip.placeholderapi.expansion.PlaceholderExpansion seasonPapi;
    private me.clip.placeholderapi.expansion.PlaceholderExpansion statsPapi;
    private me.clip.placeholderapi.expansion.PlaceholderExpansion frontierPapi;
    private Frontier frontier;

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
        if (quests != null) {
            quests.flush();
            quests = null;
        }
        if (spin != null) {
            spin.flush();
            spin = null;
        }
        if (spinPapi != null) {
            spinPapi.unregister();
            spinPapi = null;
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
        // Người chơi Bedrock (đã xác thực qua Xbox + Floodgate) không phải nhập mật khẩu AuthMe.
        // Chỉ bật trên server có AuthMe (hiện tại là lobby).
        if (getServer().getPluginManager().isPluginEnabled("AuthMe")) {
            getServer().getPluginManager().registerEvents(new BedrockAuthListener(this), this);
            getLogger().info("Bedrock auto-login: bật (AuthMe có trên server này)");
            if (getConfig().getBoolean("register-limit.enabled", true)) {
                boolean ok = new RegisterLimit(this, getConfig().getConfigurationSection("register-limit")).hook();
                getLogger().info("Giới hạn đăng ký theo IP mỗi 24 giờ: " + ok);
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

        // Rank donate (tước vị) + thông báo vào/ra: donate-ranks.enabled
        PluginCommand rankCmd = getCommand("rank");
        if (getConfig().getBoolean("donate-ranks.enabled") && getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            DonateRanks ranks = new DonateRanks(this, getConfig().getConfigurationSection("donate-ranks"));
            getServer().getPluginManager().registerEvents(ranks, this);
            // Ủng hộ bằng QR trong game: /rank mua (donate-qr.enabled, cần PANEL_URL + PANEL_SITE_KEY trong môi trường)
            if (getConfig().getBoolean("donate-qr.enabled")) {
                DonateQr qr = new DonateQr(this, getConfig().getConfigurationSection("donate-qr"), ranks.tiers());
                getServer().getPluginManager().registerEvents(qr, this);
                qr.start();
                ranks.setQr(qr);
                getLogger().info("Ủng hộ bằng QR trong game: " + (qr.ready() ? "bật" : "thiếu PANEL_URL/PANEL_SITE_KEY, chỉ hiện link web"));
            }
            if (rankCmd != null) {
                rankCmd.setExecutor((sender, cmd, label, args) -> ranks.command(sender, args));
            }
            getLogger().info("Rank donate + thông báo vào/ra: bật");
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
        java.util.function.Predicate<org.bukkit.Location> inCapital = loc -> {
            if (pa != null && pa.nation() != null) {
                return pa.inCapital(loc);
            }
            if (sp.size() < 2 || !loc.getWorld().getName().equals(getConfig().getString("town-rules.world", "world"))) {
                return false;
            }
            double dx = loc.getX() - sp.get(0);
            double dz = loc.getZ() - sp.get(1);
            return dx * dx + dz * dz <= (double) ring * ring;
        };
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

        // Xưởng chế tạo /xuong: đồ có lượt dùng + hạn (Bang Hội Chiến: workshop.enabled)
        Workshop workshop = getConfig().getBoolean("workshop.enabled") && getConfig().isConfigurationSection("workshop")
                ? new Workshop(this, getConfig().getConfigurationSection("workshop")) : null;
        if (workshop != null) {
            getServer().getPluginManager().registerEvents(workshop, this);
            workshop.start();
            getLogger().info("Xưởng chế tạo: " + workshop.size() + " món");
        }
        bind("xuong", workshop == null ? null : workshop::command, "Xưởng chế tạo");

        // Sàn thu mua /donhang (Bang Hội Chiến: orders.enabled, cần Vault)
        Orders orders = getConfig().getBoolean("orders.enabled") && getConfig().isConfigurationSection("orders")
                ? new Orders(this, getConfig().getConfigurationSection("orders")) : null;
        if (orders != null) {
            getServer().getPluginManager().registerEvents(orders, this);
            orders.start();
        }
        bind("donhang", orders == null ? null : orders::command, "Sàn thu mua");
        PluginCommand dhCmd = getCommand("donhang");
        if (dhCmd != null) {
            Orders oo = orders;
            dhCmd.setTabCompleter(oo == null ? null : (sender, cmd, label, args) -> oo.tab(args));
        }
        // Đăng nhập trang web bằng tài khoản game: /web cấp mã, trang web kiểm tra qua RCON (/dmm webma)
        WebLogin webLogin = new WebLogin(getConfig().getConfigurationSection("web-login"));
        bind("web", webLogin::command, "Đăng nhập web");
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

        // Mảnh sự kiện + cửa hàng đổi mảnh (Bang Hội Chiến: event-fragments.enabled); phần thưởng "manh" dùng module này
        FragmentShop fragments = getConfig().getBoolean("event-fragments.enabled")
                ? new FragmentShop(this, getConfig().getConfigurationSection("event-fragments"), rewards) : null;
        if (fragments != null) {
            getServer().getPluginManager().registerEvents(fragments, this);
            rewards.setFragments(fragments);
        }
        bind("doimanh", fragments == null ? null : fragments::command, "Đổi mảnh sự kiện");

        // Điểm danh hằng ngày (Bang Hội Chiến: daily-checkin.enabled)
        DailyCheckin checkin = getConfig().getBoolean("daily-checkin.enabled")
                ? new DailyCheckin(this, getConfig().getConfigurationSection("daily-checkin"), rewards) : null;
        if (checkin != null) {
            getServer().getPluginManager().registerEvents(checkin, this);
        }
        bind("diemdanh", checkin == null ? null : checkin::command, "Điểm danh");

        // Nhiệm vụ hằng ngày (Bang Hội Chiến: daily-quests.enabled)
        quests = getConfig().getBoolean("daily-quests.enabled")
                ? new DailyQuests(this, getConfig().getConfigurationSection("daily-quests"), rewards) : null;
        if (quests != null) {
            getServer().getPluginManager().registerEvents(quests, this);
        }
        bind("nhiemvu", quests == null ? null : quests::command, "Nhiệm vụ hằng ngày");

        // Vòng quay miễn phí: vé theo giờ chơi thật (Bang Hội Chiến: lucky-spin.enabled)
        spin = getConfig().getBoolean("lucky-spin.enabled")
                ? new LuckySpin(this, getConfig().getConfigurationSection("lucky-spin"), rewards, activity, itemLibrary) : null;
        if (spin != null) {
            getServer().getPluginManager().registerEvents(spin, this);
            if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                spinPapi = spin.placeholders();
                spinPapi.register();
            }
        }
        bind("vongquay", spin == null ? null : spin::command, "Vòng quay");

        // Mùa mưa / mùa khô: cây lớn nhanh/chậm, được mùa, mưa theo mùa, placeholder cho cá theo mùa (Bang Hội Chiến: seasons.enabled)
        Seasons seasons = getConfig().getBoolean("seasons.enabled") && getConfig().isConfigurationSection("seasons")
                ? new Seasons(this, getConfig().getConfigurationSection("seasons")) : null;
        if (seasons != null && !seasons.ready()) {
            getLogger().warning("Mùa: seasons.order trống hoặc sai, tắt");
            seasons = null;
        }
        if (seasons != null) {
            getServer().getPluginManager().registerEvents(seasons, this);
            seasons.start();
            if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                seasonPapi = seasons.placeholders();
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
            "§6§l/dmm §7(DuckyMoMo, gõ dài: /duckymomo) §8— bấm Tab để gợi ý",
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
