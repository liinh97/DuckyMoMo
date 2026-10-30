package vn.duckymomo.core.paper;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.MetaNode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cài đặt cá nhân (/caidat, /settings): mỗi người tự bật/tắt. Lưu ở LuckPerms (meta "dmmcaidat.<mã>" = bat|tat của người chơi) nên
 * dùng chung mọi server + proxy và trang web đọc được (DB luckperms). Không có meta = theo mặc định.
 * Nơi khác hỏi: PlayerSettings.on(player, mã); gửi tin có thể tắt: PlayerSettings.broadcast(mã, tin).
 */
final class PlayerSettings implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    static final String META = "dmmcaidat.";
    static volatile PlayerSettings INSTANCE;

    record Opt(String id, String name, Material icon, boolean def, String desc, String where) { }

    /** Danh sách công tắc (trang web dùng cùng danh sách: panel/taikhoan.py CAIDAT). */
    static final Map<String, Opt> OPTS = new LinkedHashMap<>();

    private static void opt(String id, String name, Material icon, boolean def, String desc, String where) {
        OPTS.put(id, new Opt(id, name, icon, def, desc, where));
    }

    static {
        opt("chat", "Kênh chat chung", Material.PAPER, true, "Tắt: không thấy chat của người khác (vẫn thấy tin nhắn riêng, thông báo)", "");
        opt("msg", "Tin nhắn riêng (/msg)", Material.WRITABLE_BOOK, true, "Tắt: không ai nhắn riêng cho bạn được", "");
        opt("goiten", "Kêu khi bị gọi tên", Material.BELL, true, "Có tiếng \"ting\" khi ai đó nhắc tên bạn trong chat", "");
        opt("loctuc", "Lọc từ tục", Material.SPONGE, true, "Tắt: bạn thấy chat nguyên văn (người khác vẫn thấy bản đã lọc)", "");
        opt("vaora", "Thông báo vào / rời", Material.OAK_DOOR, true, "Dòng \"… đã vào / đã rời server\"", "");
        opt("tuvong", "Thông báo tử vong", Material.SKELETON_SKULL, true, "Dòng \"… đã chết\" của người khác (của mình vẫn thấy)", "");
        opt("donrac", "Thông báo dọn rác", Material.HOPPER, true, "Đếm ngược dọn đồ rơi trên đất", "Bang Hội Chiến");
        opt("sukien", "Thông báo sự kiện", Material.GOAT_HORN, true, "Lệnh săn, kho báu, tin đồn Cổng Hoang, kỷ lục dungeon…", "Bang Hội Chiến");
        opt("chugiua", "Chữ giữa màn hình", Material.NAME_TAG, true, "Chữ to ngắn khi dịch chuyển, điểm danh, lên cấp, xong nhiệm vụ…", "");
        opt("khungnhac", "Khung nhắc lúc vào game", Material.BOOK, true, "Khung gom hướng dẫn, mùa, công thành, điểm danh… lúc vào", "");
        opt("bangphai", "Bảng bên phải", Material.CLOCK, true, "Bảng thông tin bên phải màn hình (như /sb)", "");
        opt("bossbar", "Thanh mẹo trên cùng", Material.DRAGON_BREATH, true, "Thanh chữ mẹo xoay vòng ở đầu màn hình", "");
        opt("hat", "Hiệu ứng hạt người khác", Material.BLAZE_POWDER, true, "Tắt: không thấy hạt trang trí của người khác (máy yếu đỡ giật)", "");
        opt("maumob", "Máu quái trên đầu", Material.RED_DYE, true, "Tên, máu, sát thương bay lên khi đánh quái", "Bang Hội Chiến");
        opt("tpa", "Nhận yêu cầu TPA", Material.ENDER_PEARL, true, "Người khác xin dịch chuyển TỚI bạn (/tpa)", "Bang Hội Chiến, Practice");
        opt("tpahere", "Nhận yêu cầu TPAHere", Material.ENDER_EYE, true, "Người khác xin kéo bạn tới chỗ họ (/tpahere)", "Bang Hội Chiến, Practice");
        opt("loimoi", "Lời mời đấu / nhóm", Material.IRON_SWORD, true, "Lời mời đấu tay đôi (/duel) và vào nhóm (/party invite)", "Practice");
        opt("nhacvay", "Nhắc giờ công thành", Material.RED_BANNER, true, "Báo trước 15 phút khi thị trấn / nước bạn có trận vây", "Bang Hội Chiến");
        opt("nhacngay", "Nhắc điểm danh, nhiệm vụ", Material.WRITTEN_BOOK, true, "Mỗi giờ nhắc nếu chưa điểm danh / còn nhiệm vụ hằng ngày", "Bang Hội Chiến");
        // 30/10 chủ server: mặc định font pixel gốc Minecraft (gói items đã sửa dấu ngã); bật ô này = chữ mềm Be Vietnam Pro
        opt("fontviet", "Font chữ mềm", Material.NAME_TAG, false, "Bật: chữ mềm Be Vietnam Pro (gói tài nguyên). Tắt (mặc định): font pixel gốc Minecraft, đã sửa dấu ngã, chữ căn giữa chuẩn hơn; hình vật phẩm riêng vẫn giữ", "");
        opt("goipet", "Tự gọi pet khi vào", Material.BONE, false, "Vào Bang Hội Chiến là pet chiến đấu (MyPet) tự ra đi theo", "Bang Hội Chiến");
        opt("angiap", "Ẩn giáp", Material.LEATHER_CHESTPLATE, false, "Ai cũng thấy skin, không thấy giáp, kể cả bạn (dưới tên hiện set đồ đang mặc). LƯU Ý: ô giáp trong túi đồ trông TRỐNG nhưng giáp vẫn đang mặc, vẫn bảo vệ đủ. Ẩn ở mọi nơi; chỉ khi công thành giáp mới hiện", "Bang Hội Chiến");   // 28/10, cùng /angiap (ArmorHide)
    }

    private static final Set<String> MSG_CMDS = Set.of("msg", "tell", "w", "m", "t", "pm", "whisper", "emsg", "etell", "ew", "ewhisper", "epm", "dm");
    private static final Set<String> TPA_CMDS = Set.of("tpa", "tpask", "call", "etpa", "etpask", "ecall");
    private static final Set<String> TPAHERE_CMDS = Set.of("tpahere", "etpahere");

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        final Map<Integer, String> slots = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final JavaPlugin plugin;
    private final String server;
    /** Giá trị vừa đổi (LuckPerms lưu bất đồng bộ): dùng ngay trong lúc chờ. */
    private final Map<UUID, Map<String, Boolean>> recent = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDaily = new HashMap<>();
    private Long remindedSession;
    private net.luckperms.api.event.EventSubscription<?> sub;
    /** Trạng thái đã áp vào TAB / PlayerParticles / HoloMobHealth (chỉ áp lại khi đổi, vd đổi trên web). */
    private final Map<UUID, Map<String, Boolean>> applied = new ConcurrentHashMap<>();

    PlayerSettings(JavaPlugin plugin, String server) {
        this.plugin = plugin;
        this.server = server;
    }

    void start() {
        INSTANCE = this;
        Bukkit.getScheduler().runTaskTimer(plugin, this::remindTick, 1200L, 1200L);
        Bukkit.getOnlinePlayers().forEach(p -> Bukkit.getScheduler().runTaskLater(plugin, () -> applyAll(p), 20L));
        // đổi trên trang web (lp user … meta set) -> LuckPerms tính lại dữ liệu người chơi -> áp lại các mục cần
        sub = lp().getEventBus().subscribe(plugin, net.luckperms.api.event.user.UserDataRecalculateEvent.class, ev -> {
            Player p = Bukkit.getPlayer(ev.getUser().getUniqueId());
            if (p != null && INSTANCE == this) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    recent.remove(p.getUniqueId());
                    applyAll(p);
                });
            }
        });
    }

    void stop() {
        INSTANCE = null;
        if (sub != null) {
            sub.close();   // /dmm reload: bỏ đăng ký cũ
        }
    }

    private static LuckPerms lp() {
        return LuckPermsProvider.get();
    }

    // ---------------------------------------------------------------- đọc / ghi

    static boolean on(Player p, String id) {
        PlayerSettings s = INSTANCE;
        Opt o = OPTS.get(id);
        boolean def = o == null || o.def();
        if (s == null || p == null) {
            return def;
        }
        Map<String, Boolean> r = s.recent.get(p.getUniqueId());
        if (r != null && r.containsKey(id)) {
            return r.get(id);
        }
        try {
            User u = lp().getUserManager().getUser(p.getUniqueId());
            String v = u == null ? null : u.getCachedData().getMetaData().getMetaValue(META + id);
            return v == null ? def : v.equals("bat");
        } catch (RuntimeException | LinkageError e) {
            return def;
        }
    }

    void set(Player p, String id, boolean value) {
        recent.computeIfAbsent(p.getUniqueId(), k -> new ConcurrentHashMap<>()).put(id, value);
        String key = META + id;
        boolean def = OPTS.get(id).def();
        lp().getUserManager().modifyUser(p.getUniqueId(), u -> {
            u.data().clear(NodeType.META.predicate(n -> n.getMetaKey().equals(key)));
            if (value != def) {
                u.data().add(MetaNode.builder(key, value ? "bat" : "tat").build());
            }
        });
        apply(p, id);
    }

    /** Gửi tin cho mọi người đang bật mục id (+ console). Không có module thì gửi tất cả. */
    static void broadcast(String id, Component msg) {
        if (INSTANCE == null) {
            Bukkit.broadcast(msg);
            return;
        }
        Bukkit.getConsoleSender().sendMessage(msg);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (on(p, id)) {
                p.sendMessage(msg);
            }
        }
    }

    // ---------------------------------------------------------------- áp dụng vào plugin khác

    private void applyAll(Player p) {
        if (!p.isOnline()) {
            return;
        }
        for (String id : List.of("bangphai", "bossbar", "hat", "maumob", "fontviet")) {
            apply(p, id);
        }
    }

    private void apply(Player p, String id) {
        boolean v = on(p, id);
        Map<String, Boolean> done = applied.computeIfAbsent(p.getUniqueId(), k -> new ConcurrentHashMap<>());
        if (Boolean.valueOf(v).equals(done.get(id))) {
            return;
        }
        done.put(id, v);
        switch (id) {
            case "fontviet" -> sendPack(p, v);
            case "bangphai" -> {
                if (Bukkit.getPluginManager().isPluginEnabled("TAB")) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tab scoreboard " + (v ? "on " : "off ") + p.getName() + " -s");
                }
            }
            case "bossbar" -> {
                if (Bukkit.getPluginManager().isPluginEnabled("TAB")) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tab bossbar " + (v ? "on " : "off ") + p.getName() + " -s");
                }
            }
            case "hat" -> {
                var pp = Bukkit.getPluginManager().getPlugin("PlayerParticles");
                if (pp != null && pp.isEnabled()) {
                    try {
                        Class<?> api = Class.forName("dev.esophose.playerparticles.api.PlayerParticlesAPI", true, pp.getClass().getClassLoader());
                        Object inst = api.getMethod("getInstance").invoke(null);
                        api.getMethod("togglePlayerParticleVisibility", Player.class, boolean.class).invoke(inst, p, !v);
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        plugin.getLogger().fine("Cài đặt: PlayerParticles: " + e);
                    }
                }
            }
            case "maumob" -> {
                var hmh = Bukkit.getPluginManager().getPlugin("HoloMobHealth");
                if (hmh != null && hmh.isEnabled()) {
                    try {
                        ClassLoader cl = hmh.getClass().getClassLoader();
                        @SuppressWarnings("unchecked")
                        Set<Player> enabled = (Set<Player>) Class.forName("com.loohp.holomobhealth.HoloMobHealth", true, cl).getField("playersEnabled").get(null);
                        if (enabled.contains(p) != v) {
                            boolean now = (boolean) Class.forName("com.loohp.holomobhealth.database.Database", true, cl).getMethod("toggle", Player.class).invoke(null, p);
                            if (now) {
                                enabled.add(p);
                            } else {
                                enabled.remove(p);
                            }
                        }
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        plugin.getLogger().fine("Cài đặt: HoloMobHealth: " + e);
                    }
                }
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- chat

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player sender = e.getPlayer();
        e.viewers().removeIf(a -> a instanceof Player v && v != sender && !on(v, "chat"));
        Component original = ChatFilter.ORIGINAL.remove(e);
        if (original != null) {
            ChatRenderer old = e.renderer();
            e.renderer((source, displayName, message, viewer) ->
                    old.render(source, displayName, viewer instanceof Player v && !on(v, "loctuc") ? original : message, viewer));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChatMention(AsyncChatEvent e) {
        String text = " " + PLAIN.serialize(e.message()).toLowerCase(Locale.ROOT) + " ";
        List<Player> hit = new ArrayList<>();
        for (Audience a : e.viewers()) {
            if (a instanceof Player v && v != e.getPlayer() && text.matches("(?s).*[^a-z0-9_]" + java.util.regex.Pattern.quote(v.getName().toLowerCase(Locale.ROOT)) + "[^a-z0-9_].*")
                    && on(v, "goiten")) {
                hit.add(v);
            }
        }
        if (!hit.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> hit.forEach(v -> v.playSound(v.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f)));
        }
    }

    // ---------------------------------------------------------------- gói tài nguyên theo lựa chọn từng người

    /**
     * Gửi gói tài nguyên theo ô "Font tiếng Việt" (config resource-pack, URL/SHA1 do ops/build-font-pack.sh ghi vào .env -> CFG_*):
     * bật = gói đầy đủ (font Be Vietnam Pro + hình vật phẩm), tắt = gói chỉ có hình vật phẩm (font gốc Minecraft).
     * Server.properties KHÔNG tự gửi gói nữa (RESOURCE_PACK trống trong compose). Không bắt buộc: từ chối vẫn chơi được.
     */
    private void sendPack(Player p, boolean font) {
        var c = plugin.getConfig().getConfigurationSection("resource-pack");
        if (c == null || !p.isOnline()) {
            return;
        }
        String url = c.getString(font ? "font-url" : "item-url", "").trim();
        String sha = c.getString(font ? "font-sha1" : "item-sha1", "").trim();
        if (url.isEmpty() || url.startsWith("${") || !sha.matches("[0-9a-fA-F]{40}")) {
            return;
        }
        String prompt = c.getString(font ? "font-prompt" : "item-prompt", "");
        // 30/10: mỗi gói một mã cố định, gửi bằng "thêm" (không "gỡ hết rồi gắn lại" như setResourcePack): chuyển server mà máy đã có
        // đúng gói này (cùng mã + SHA1) thì không nạp lại tài nguyên. Đổi ô Font chữ mềm thì gỡ gói kia.
        p.removeResourcePacks(font ? PACK_ITEMS : PACK_FONT);
        var req = net.kyori.adventure.resource.ResourcePackRequest.resourcePackRequest()
                .packs(net.kyori.adventure.resource.ResourcePackInfo.resourcePackInfo(font ? PACK_FONT : PACK_ITEMS, java.net.URI.create(url), sha.toLowerCase()))
                .replace(false).required(false);
        if (!prompt.isEmpty()) {
            req = req.prompt(LegacyComponentSerializer.legacyAmpersand().deserialize(prompt));
        }
        p.sendResourcePacks(req.build());
    }

    private static final java.util.UUID PACK_FONT = java.util.UUID.nameUUIDFromBytes("duckymomo:pack-font".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final java.util.UUID PACK_ITEMS = java.util.UUID.nameUUIDFromBytes("duckymomo:pack-items".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    // ---------------------------------------------------------------- vào / rời / tử vong

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        Component m = e.joinMessage();
        if (m != null) {
            e.joinMessage(null);
            broadcast("vaora", m);
        }
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyAll(p), 40L);
        if (on(p, "goipet") && Bukkit.getPluginManager().isPluginEnabled("MyPet")) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && MyPetBridge.active(p) == null) {
                    p.performCommand("petcall");
                }
            }, 100L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent e) {
        Component m = e.quitMessage();
        if (m != null) {
            e.quitMessage(null);
            UUID self = e.getPlayer().getUniqueId();
            Bukkit.getConsoleSender().sendMessage(m);
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (!o.getUniqueId().equals(self) && on(o, "vaora")) {
                    o.sendMessage(m);
                }
            }
        }
        recent.remove(e.getPlayer().getUniqueId());
        applied.remove(e.getPlayer().getUniqueId());
        lastDaily.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Component m = e.deathMessage();
        if (m == null) {
            return;
        }
        e.deathMessage(null);
        Bukkit.getConsoleSender().sendMessage(m);
        Player dead = e.getPlayer();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o == dead || on(o, "tuvong")) {
                o.sendMessage(m);
            }
        }
    }

    // ---------------------------------------------------------------- tin nhắn riêng, TPA, lời mời

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player s = e.getPlayer();
        boolean bypass = s.hasPermission("duckymomo.caidat.bypass");
        String[] a = e.getMessage().substring(1).trim().split("\\s+");
        if (a.length < 2) {
            return;
        }
        String cmd = a[0].toLowerCase(Locale.ROOT);
        int colon = cmd.indexOf(':');
        if (colon >= 0) {
            cmd = cmd.substring(colon + 1);
        }
        String id = null;
        String target = a[1];
        if (MSG_CMDS.contains(cmd)) {
            id = "msg";
        } else if (TPA_CMDS.contains(cmd)) {
            id = "tpa";
        } else if (TPAHERE_CMDS.contains(cmd)) {
            id = "tpahere";
        } else if (cmd.equals("duel") || cmd.equals("duels")) {
            id = "loimoi";
        } else if (cmd.equals("party") && a.length >= 3 && a[1].equalsIgnoreCase("invite")) {
            id = "loimoi";
            target = a[2];
        }
        if (id == null) {
            return;
        }
        // TPA: Essentials nhận cả một phần tên (vd /tpa lin) nên tìm như vậy; tin nhắn/lệnh khác giữ đúng tên (tránh /t claim khớp nhầm)
        Player t = id.startsWith("tpa") ? Bukkit.getPlayer(target) : Bukkit.getPlayerExact(target);
        if (t == null || t == s || on(t, id)) {
            return;
        }
        if (bypass) {
            // staff vẫn gửi được, nhưng báo rõ để không tưởng công tắc hỏng
            s.sendMessage(c("&7(" + t.getName() + " đang tắt " + OPTS.get(id).name() + "; bạn vẫn gửi được vì có quyền staff.)"));
            return;
        }
        e.setCancelled(true);
        s.sendMessage(c("&c" + t.getName() + " &cđang tắt &f" + OPTS.get(id).name() + "&c."));
    }

    // ---------------------------------------------------------------- nhắc nhở

    private void remindTick() {
        long now = System.currentTimeMillis();
        // nhắc giờ công thành: còn ≤ 15 phút tới phiên đánh, báo 1 lần mỗi phiên cho người có thị trấn / nước dính trận vây
        if (SiegeWarHook.available()) {
            Long next = SiegeWarHook.nextBattleSessionStart();
            if (next != null && next - now <= 15 * 60_000L && next > now && !next.equals(remindedSession)) {
                remindedSession = next;
                List<SiegeWarHook.SiegeInfo> sieges = SiegeWarHook.activeSieges();
                long mins = Math.max(1, (next - now) / 60_000L);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!on(p, "nhacvay")) {
                        continue;
                    }
                    String[] tn = townAndNation(p);
                    for (SiegeWarHook.SiegeInfo si : sieges) {
                        boolean def = tn[0] != null && si.town().equalsIgnoreCase(tn[0]);
                        boolean att = tn[1] != null && si.attacker().toLowerCase(Locale.ROOT).contains(tn[1].toLowerCase(Locale.ROOT));
                        if (def || att) {
                            p.sendMessage(c("&c⚔ Còn " + mins + " phút tới giờ công thành! &f" + (def ? "Thị trấn bạn (" + si.town() + ") đang bị vây."
                                    : "Nước bạn đang vây " + si.town() + ".")));
                            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.7f, 1f);
                            break;
                        }
                    }
                }
            }
        }
        // nhắc điểm danh / nhiệm vụ: mỗi giờ
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!on(p, "nhacngay") || now - lastDaily.getOrDefault(p.getUniqueId(), now - 50 * 60_000L) < 60 * 60_000L) {
                lastDaily.putIfAbsent(p.getUniqueId(), now - 50 * 60_000L);
                continue;
            }
            lastDaily.put(p.getUniqueId(), now);
            DailyCheckin dc = DailyCheckin.INSTANCE;
            DailyQuests dq = DailyQuests.INSTANCE;
            boolean noCheck = dc != null && !dc.checkedToday(p.getUniqueId());
            int left = dq == null ? 0 : dq.remaining(p.getUniqueId());
            if (noCheck || left > 0) {
                p.sendMessage(c("&e♫ " + (noCheck ? "Hôm nay bạn chưa điểm danh. " : "") + (left > 0 ? "Còn &f" + left + " &enhiệm vụ hằng ngày. " : ""))
                        .append(c(noCheck ? "&a&n[Điểm danh]" : "&a&n[Xem nhiệm vụ]").clickEvent(ClickEvent.runCommand(noCheck ? "/diemdanh" : "/nhiemvu")))
                        .append(c(" &8(tắt nhắc: /caidat)")));
            }
        }
    }

    private static String[] townAndNation(Player p) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Towny")) {
            return new String[2];
        }
        try {
            var r = com.palmergames.bukkit.towny.TownyAPI.getInstance().getResident(p);
            var t = r == null ? null : r.getTownOrNull();
            var n = r == null ? null : r.getNationOrNull();
            return new String[]{t == null ? null : t.getName(), n == null ? null : n.getName()};
        } catch (RuntimeException | LinkageError e) {
            return new String[2];
        }
    }

    // ---------------------------------------------------------------- menu /caidat

    private static Component c(String s) {
        return Tip.ns(LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false));
    }

    private ItemStack icon(Player p, Opt o) {
        boolean v = on(p, o.id());
        Tip t = Tip.of((v ? "&a● " : "&c○ ") + "&f" + o.name()).accent(v ? "&a" : "&c").desc(o.desc());
        if (!o.where().isBlank()) {
            t.note("Có ở: " + o.where());
        }
        t.line().kv("Trạng thái", v ? "&aĐang bật" : "&cĐang tắt").click(v ? "Bấm để tắt" : "Bấm để bật");
        ItemStack it = t.apply(new ItemStack(o.icon()));
        ItemMeta m = it.getItemMeta();
        if (v) {
            m.setEnchantmentGlintOverride(true);
        }
        it.setItemMeta(m);
        return it;
    }

    void open(Player p) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 45, c("&0⚙ Cài đặt cá nhân"));
        int i = 0;
        for (Opt o : OPTS.values()) {
            if (i >= SLOTS.length) {
                break;
            }
            h.inv.setItem(SLOTS[i], icon(p, o));
            h.slots.put(SLOTS[i], o.id());
            i++;
        }
        MenuFrame.paint(h.inv, Material.LIME_STAINED_GLASS_PANE);
        p.openInventory(h.inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        String id = h.slots.get(e.getRawSlot());
        if (id == null || !(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        boolean v = !on(p, id);
        set(p, id, v);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, v ? 1.4f : 0.9f);
        h.inv.setItem(e.getRawSlot(), icon(p, OPTS.get(id)));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    boolean command(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Chỉ dùng trong game.");
            return true;
        }
        if (args.length >= 2 && OPTS.containsKey(args[0].toLowerCase(Locale.ROOT))) {
            String id = args[0].toLowerCase(Locale.ROOT);
            boolean v = args[1].equalsIgnoreCase("bat") || args[1].equalsIgnoreCase("on");
            set(p, id, v);
            p.sendMessage(c("&a✔ " + OPTS.get(id).name() + ": " + (v ? "&aBật" : "&cTắt")));
            return true;
        }
        open(p);
        return true;
    }
}
