package vn.duckymomo.core.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Nhiệm vụ hằng ngày (/nhiemvu). Mỗi người mỗi ngày nhận per-day nhiệm vụ ngẫu nhiên (cố định trong ngày) từ pool.
 * Loại: break (phá khối), place (đặt khối), kill (giết mob), fish (câu cá), harvest (thu hoạch cây đã chín).
 * targets: tên vật liệu/mob, dạng *_ORE, OAK_*, * (tất cả), @hostile (mọi quái thù địch). Khối do người chơi đặt không tính
 * (PlacedBlockGuard huỷ sự kiện phá; ở đây ignoreCancelled). Làm xong một nhiệm vụ: quà của nó; xong hết: complete-all-rewards.
 * Lưu: plugins/DuckyMoMoCore/nhiemvu.yml (ghi mỗi phút + khi thoát).
 * Làm quen tính năng (intro, 26/10): mỗi người làm MỘT lần mỗi việc, làm song song, không theo thứ tự; module khác báo bằng
 * DailyQuests.feature(p, mã) khi người chơi làm thật: mua (Cửa hàng Kinh Đô), ban (/sell có tiền về), choden (treo món zAuctionHouse),
 * trade (TradePro xong), thoren, tinhluyen, dungeon (vào tới), pet (bắt pet), thitran (có thị trấn Towny, kiểm mỗi phút).
 * Xong hết: intro-all-rewards. Lưu intro.<uuid>: [mã đã xong] (không xoá khi sang ngày).
 * Việc dân làng nhờ (28/10, thay danh sách Làm quen trong /nhiemvu): mỗi việc intro có npc = người giao (daily-quests.givers).
 * Nhánh mở khi hỏi người giao ĐẦU TIÊN (Già Làng, hội thoại BetonQuest gọi /dmm viecnpc <người> gialang); sau đó bấm từng NPC thì
 * nhận hết việc của người đó, làm xong (feature) chưa có quà mà phải quay lại NPC đó mới nhận. Việc chưa nhận thì làm cũng không tính.
 * NPC: "bq" (hội thoại BetonQuest gọi lệnh), "quay:<quầy Thợ Rèn>" (ThoRen.onNpc), "donbuon" (Nhà Buôn): có việc để giao / trả thì
 * NPC nói trong chat và lần bấm đó không mở menu. Lưu lang.<uuid>: mo (đã mở nhánh), nhan (đang làm), xong (chờ nhận thưởng).
 * NPC hội thoại (bq): xong việc thì gắn tag BetonQuest cotruyen>vl_<mã>_tra -> câu mở đầu "xong rồi à?" gọi trả thưởng ngay khi bấm NPC;
 * chữ trong chat đợi hội thoại ĐÓNG mới gửi (PlayerConversationEndEvent; bảng dialog che chat, đóng muộn thì chữ đã mờ; 28/10).
 * Chuỗi việc lữ khách (28/10, daily-quests.chains.<người giao>): các bước nối nhau, khó dần; nhận bước -> làm -> về kể -> trả thưởng
 * và giao luôn bước sau. Loại bước: kill / break / place / fish / harvest (đếm như nhiệm vụ ngày), feature (mã DailyQuests.feature,
 * vd dautruong, vuotdungeon, xuong), nop (mang đồ thường tới nộp, items: {VẬT_LIỆU: số}). Không cần mở nhánh Già Làng.
 * Lưu lang.<uuid>.chuoi.<mã>: b (số bước đã xong), nhan (đang làm bước b), dem (tiến độ).
 */
final class DailyQuests implements Listener {
    static volatile DailyQuests INSTANCE;

    /** Số nhiệm vụ hôm nay chưa xong (PlayerSettings nhắc). */
    int remaining(UUID id) {
        String key = "players." + id;
        int n = 0;
        for (String qid : todays(id)) {
            Quest q = pool.get(qid);
            if (q != null && data.getInt(key + ".progress." + qid, 0) < need(q)) {
                n++;
            }
        }
        return n;
    }

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private record Quest(String id, String name, String type, List<String> targets, int amount, List<String> rewards) { }

    private record Intro(String id, String name, String how, List<String> rewards, String giver) { }

    /** Một bước trong chuỗi việc lữ khách (noi = câu NPC nói lúc giao bước này). */
    private record Step(String name, String type, List<String> targets, int amount, Map<Material, Integer> items, String how,
                        String noi, List<String> rewards) { }

    /** Người giao việc dân làng: npc = "bq" | "quay:<quầy>" | "donbuon"; mo / nhan / tra / het = câu nói lúc mở nhánh / giao / trả / hết việc. */
    private record Giver(String id, String name, String npc, String where, String mo, String nhan, String tra, String het) { }

    private final JavaPlugin plugin;
    private final ConfigurationSection cfg;
    private final Rewards rewards;
    private final File file;
    private final YamlConfiguration data;
    private final Map<String, Quest> pool = new LinkedHashMap<>();
    private final Map<String, Intro> intros = new LinkedHashMap<>();
    private final Map<String, Giver> givers = new LinkedHashMap<>();
    private final Map<String, List<Step>> chains = new LinkedHashMap<>();
    /** Hội thoại BetonQuest đã gọi /dmm viecnpc: lời NPC gom lại, gửi chat khi hội thoại đóng (bảng che chat). */
    private final Map<UUID, List<Component>> pending = new java.util.HashMap<>();
    /** Đang gom lời NPC (trong lúc chạy talk từ /dmm viecnpc). */
    private final Map<UUID, List<Component>> capture = new java.util.HashMap<>();
    /** Lời NPC lần gần nhất dạng chữ thường: %dmmviec_ketqua% hiện ngay trong bảng hội thoại (28/10: bấm Ok không thấy gì). */
    private static final Map<UUID, String> SUMMARY = new java.util.concurrent.ConcurrentHashMap<>();
    private boolean convEndHook;
    private boolean dirty;

    DailyQuests(JavaPlugin plugin, ConfigurationSection cfg, Rewards rewards) {
        INSTANCE = this;
        this.plugin = plugin;
        this.cfg = cfg;
        this.rewards = rewards;
        this.file = new File(plugin.getDataFolder(), "nhiemvu.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        Shared.FORGET.add(id -> Shared.forget(data, file, id));   // admin reset "như người mới"
        ConfigurationSection p = cfg.getConfigurationSection("pool");
        if (p != null) {
            for (String id : p.getKeys(false)) {
                ConfigurationSection q = p.getConfigurationSection(id);
                if (q == null) {
                    continue;
                }
                List<String> t = q.getStringList("targets").stream().map(x -> x.toUpperCase(Locale.ROOT)).toList();
                pool.put(id, new Quest(id, q.getString("name", id), q.getString("type", "break").toLowerCase(Locale.ROOT),
                        t.isEmpty() ? List.of("*") : t, Math.max(1, q.getInt("amount", 1)), q.getStringList("rewards")));
            }
        }
        ConfigurationSection gs = cfg.getConfigurationSection("givers");
        if (gs != null) {
            for (String id : gs.getKeys(false)) {
                ConfigurationSection g = gs.getConfigurationSection(id);
                if (g != null) {
                    String ten = "&e" + g.getString("name", id).replaceAll("(?i)[&§][0-9a-fk-or]", "");   // một màu vàng cho mọi NPC, dễ đọc
                    givers.put(id, new Giver(id, ten, g.getString("npc", "bq").toLowerCase(Locale.ROOT), g.getString("where", ""),
                            g.getString("mo", ""), g.getString("nhan", ""), g.getString("tra", ""), g.getString("het", "")));
                }
            }
        }
        ConfigurationSection cs = cfg.getConfigurationSection("chains");
        if (cs != null) {
            for (String gid : cs.getKeys(false)) {
                if (!givers.containsKey(gid)) {
                    plugin.getLogger().warning("daily-quests.chains." + gid + ": chưa có người giao trong daily-quests.givers");
                    continue;
                }
                List<Step> steps = new ArrayList<>();
                for (Map<?, ?> m : cs.getMapList(gid)) {
                    Map<Material, Integer> items = new LinkedHashMap<>();
                    if (m.get("items") instanceof Map<?, ?> im) {
                        im.forEach((k, v) -> {
                            Material mat = Material.matchMaterial(String.valueOf(k));
                            if (mat == null) {
                                plugin.getLogger().warning("daily-quests.chains." + gid + ": không có vật liệu " + k);
                            } else {
                                items.put(mat, Integer.parseInt(String.valueOf(v)));
                            }
                        });
                    }
                    List<String> t = new ArrayList<>();
                    if (m.get("targets") instanceof List<?> tl) {
                        tl.forEach(x -> t.add(String.valueOf(x).toUpperCase(Locale.ROOT)));
                    }
                    List<String> rw = new ArrayList<>();
                    if (m.get("rewards") instanceof List<?> rl) {
                        rl.forEach(x -> rw.add(String.valueOf(x)));
                    }
                    Object amt = m.get("amount");
                    steps.add(new Step(String.valueOf(m.get("name")), String.valueOf(m.get("type")).toLowerCase(Locale.ROOT),
                            t.isEmpty() ? List.of("*") : t, amt == null ? 1 : Math.max(1, Integer.parseInt(String.valueOf(amt))), items,
                            m.get("how") == null ? "" : String.valueOf(m.get("how")), m.get("noi") == null ? "" : String.valueOf(m.get("noi")), rw));
                }
                if (!steps.isEmpty()) {
                    chains.put(gid, steps);
                }
            }
        }
        ConfigurationSection in = cfg.getConfigurationSection("intro");
        if (in != null) {
            for (String id : in.getKeys(false)) {
                ConfigurationSection q = in.getConfigurationSection(id);
                if (q != null) {
                    String g = q.getString("npc");
                    intros.put(id, new Intro(id, q.getString("name", id), q.getString("how", ""), q.getStringList("rewards"),
                            g != null && givers.containsKey(g) ? g : null));
                }
            }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::flush, 1200L, 1200L);
        if (intros.containsKey("thitran") && Bukkit.getPluginManager().isPluginEnabled("Towny")) {
            Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                for (Player pl : Bukkit.getOnlinePlayers()) {
                    if (!introDone(pl.getUniqueId(), "thitran") && TownyCheck.hasTown(pl)) {
                        intro(pl, "thitran");
                    }
                }
            }, 1200L, 1200L);
        }
        hookConversationEnd();
        Bukkit.getScheduler().runTaskLater(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::syncTags), 100L);
        hookEvent("fr.maxlego08.zauctionhouse.api.event.events.stats.AuctionItemListedEvent", "getSeller", "choden");
        hookEvent("com.jaketheman.tradepro.events.TradeCompleteEvent", "getPlayerOne", "trade");
        hookEvent("com.jaketheman.tradepro.events.TradeCompleteEvent", "getPlayerTwo", "trade");
    }

    /** Sự kiện của plugin khác (không phụ thuộc lúc biên dịch): người chơi lấy bằng getter, có thì tính việc làm quen. */
    @SuppressWarnings("unchecked")
    private void hookEvent(String cls, String getter, String intro) {
        if (!intros.containsKey(intro)) {
            return;
        }
        try {
            Class<? extends org.bukkit.event.Event> ev = (Class<? extends org.bukkit.event.Event>) Class.forName(cls);
            java.lang.reflect.Method g = ev.getMethod(getter);
            Bukkit.getPluginManager().registerEvent(ev, this, EventPriority.MONITOR, (l, e) -> {
                if (ev.isInstance(e)) {
                    try {
                        if (g.invoke(e) instanceof Player p) {
                            intro(p, intro);
                        }
                    } catch (ReflectiveOperationException ignored) {
                        // bỏ qua
                    }
                }
            }, plugin, true);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            // plugin đó chưa cài
        }
    }

    /** BetonQuest PlayerConversationEndEvent (không phụ thuộc lúc biên dịch): hội thoại đóng thì NPC nói phần đang chờ. */
    @SuppressWarnings("unchecked")
    private void hookConversationEnd() {
        try {
            Class<? extends org.bukkit.event.Event> ev = (Class<? extends org.bukkit.event.Event>)
                    Class.forName("org.betonquest.betonquest.api.bukkit.event.PlayerConversationEndEvent");
            java.lang.reflect.Method prof = ev.getMethod("getProfile");
            Bukkit.getPluginManager().registerEvent(ev, this, EventPriority.MONITOR, (l, e) -> {
                if (!ev.isInstance(e)) {
                    return;
                }
                try {
                    Object pr = prof.invoke(e);
                    if (pr.getClass().getMethod("getPlayer").invoke(pr) instanceof org.bukkit.OfflinePlayer op) {
                        UUID u = op.getUniqueId();
                        Bukkit.getScheduler().runTaskLater(plugin, () -> sayPending(u), 5L);
                    }
                } catch (ReflectiveOperationException ignored) {
                    // bỏ qua
                }
            }, plugin);
            convEndHook = true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            // BetonQuest chưa cài
        }
    }

    private void sayPending(UUID u) {
        List<Component> msgs = pending.remove(u);
        Player p = Bukkit.getPlayer(u);
        if (msgs != null && p != null) {
            msgs.forEach(p::sendMessage);
        }
    }

    private void send(Player p, Component m) {
        List<Component> cap = capture.get(p.getUniqueId());
        if (cap != null) {
            cap.add(m);
        } else {
            p.sendMessage(m);
        }
    }

    /** %dmmviec_ketqua%: lời NPC vừa nói (trả thưởng / giao việc) cho bảng hội thoại. */
    static String summary(UUID u) {
        return SUMMARY.getOrDefault(u, "Ta chưa có gì để nói thêm.");
    }

    private static String plainSummary(List<Component> msgs) {
        List<String> out = new ArrayList<>();
        for (Component m : msgs) {
            String t = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(m).trim();
            java.util.regex.Matcher mm = java.util.regex.Pattern.compile("^[^:“]{1,40}: “(.*)”$", java.util.regex.Pattern.DOTALL).matcher(t);
            if (mm.matches()) {
                t = mm.group(1);   // lời NPC: bỏ tên + ngoặc, bảng đã có tên NPC
            }
            if (!t.isEmpty()) {
                out.add(t.replaceAll("\\n\\s+", "\n   "));
            }
        }
        return out.isEmpty() ? "Ta chưa có gì để nói thêm." : String.join("\n", out);
    }

    /** NPC hội thoại: có việc xong chờ trả thì gắn tag cotruyen>vl_<mã>_tra (câu mở đầu "xong rồi à?" trong hội thoại), hết thì gỡ. */
    private void syncTag(Player p, Giver g) {
        if (!"bq".equals(g.npc()) || !Bukkit.getPluginManager().isPluginEnabled("BetonQuest")) {
            return;
        }
        boolean has = claimable(p.getUniqueId(), g);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "bq tag " + p.getName() + (has ? " add" : " del") + " cotruyen>vl_" + g.id() + "_tra");
    }

    private boolean claimable(UUID u, Giver g) {
        if (list(u, "xong").stream().anyMatch(id -> intros.containsKey(id) && g.id().equals(intros.get(id).giver()))) {
            return true;
        }
        List<Step> steps = chains.get(g.id());
        int b = data.getInt(ck(u, g.id()) + ".b", 0);
        return steps != null && b < steps.size() && data.getBoolean(ck(u, g.id()) + ".nhan")
                && !steps.get(b).type().equals("nop") && data.getInt(ck(u, g.id()) + ".dem", 0) >= steps.get(b).amount();
    }

    // ---------------------------------------------------------------- chuỗi việc lữ khách

    private static String ck(UUID u, String g) {
        return lk(u) + ".chuoi." + g;
    }

    private void chainProgress(Player p, String type, String target, boolean hostile, int n) {
        UUID u = p.getUniqueId();
        for (Map.Entry<String, List<Step>> e : chains.entrySet()) {
            String k = ck(u, e.getKey());
            int b = data.getInt(k + ".b", 0);
            if (b >= e.getValue().size() || !data.getBoolean(k + ".nhan")) {
                continue;
            }
            Step st = e.getValue().get(b);
            if (!st.type().equals(type) || !(matches(st.targets(), target) || (hostile && st.targets().contains("@HOSTILE")))) {
                continue;
            }
            int dem = data.getInt(k + ".dem", 0);
            if (dem >= st.amount()) {
                continue;
            }
            dem = Math.min(st.amount(), dem + n);
            data.set(k + ".dem", dem);
            dirty = true;
            Giver g = givers.get(e.getKey());
            p.sendActionBar(c("&e" + st.name() + " &f" + dem + "&7/&f" + st.amount()));
            if (dem >= st.amount()) {
                p.sendMessage(c("&a✔ Xong việc: &f" + st.name() + "&a. Về kể cho " + g.name() + (g.where().isEmpty() ? "" : " &7(" + g.where() + ")")
                        + " &anhận thưởng."));
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
                syncTag(p, g);
                flush();
            }
        }
    }

    /** Đồ thường (không tên riêng, không dấu plugin) trong túi: đếm / lấy đi cho bước "nop". */
    private static boolean plain(org.bukkit.inventory.ItemStack it) {
        if (it == null || it.getType().isAir()) {
            return false;
        }
        if (!it.hasItemMeta()) {
            return true;
        }
        org.bukkit.inventory.meta.ItemMeta m = it.getItemMeta();
        return !m.hasDisplayName() && m.getPersistentDataContainer().isEmpty();
    }

    private static int count(Player p, Material mat) {
        int n = 0;
        for (org.bukkit.inventory.ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && it.getType() == mat && plain(it)) {
                n += it.getAmount();
            }
        }
        return n;
    }

    private static void take(Player p, Material mat, int n) {
        org.bukkit.inventory.ItemStack[] inv = p.getInventory().getStorageContents();
        for (int i = 0; i < inv.length && n > 0; i++) {
            org.bukkit.inventory.ItemStack it = inv[i];
            if (it != null && it.getType() == mat && plain(it)) {
                int t = Math.min(n, it.getAmount());
                it.setAmount(it.getAmount() - t);
                n -= t;
            }
        }
        p.getInventory().setStorageContents(inv);
    }

    private String stepLine(Step st, int dem) {
        String prog = st.type().equals("nop") ? "" : " &7" + Math.min(dem, st.amount()) + "/" + st.amount();
        return "&f" + st.name() + prog + (st.rewards().isEmpty() ? "" : " &7→ &a" + String.join("&7, &a", rewards.describe(st.rewards())));
    }

    /** NPC lữ khách: trả bước đang làm nếu xong (bước "nop": có đủ đồ thì lấy luôn), rồi giao bước sau. */
    private boolean talkChain(Player p, Giver g, List<Step> steps, boolean asked) {
        UUID u = p.getUniqueId();
        String k = ck(u, g.id());
        int b = data.getInt(k + ".b", 0);
        boolean said = false;
        boolean paid = false;
        if (b < steps.size() && data.getBoolean(k + ".nhan")) {
            Step st = steps.get(b);
            int dem = data.getInt(k + ".dem", 0);
            boolean done = st.type().equals("nop") ? st.items().entrySet().stream().allMatch(x -> count(p, x.getKey()) >= x.getValue())
                    : dem >= st.amount();
            if (done) {
                st.items().forEach((mat, n) -> take(p, mat, n));
                say(p, g, g.tra());
                List<String> got = rewards.give(p, st.rewards());
                send(p, c("&a✔ Bước " + (b + 1) + "/" + steps.size() + ": &f" + st.name()
                        + (got.isEmpty() ? "" : "&a. Nhận: &f" + String.join("&7, &f", got))));
                b++;
                data.set(k + ".b", b);
                data.set(k + ".nhan", false);
                data.set(k + ".dem", 0);
                dirty = true;
                said = true;
                paid = true;
            } else if (asked) {
                say(p, g, "Việc ta nhờ vẫn chưa xong đâu:");
                send(p, c("&7• " + stepLine(st, dem) + (st.how().isEmpty() ? "" : "\n   &7" + st.how())));
                if (st.type().equals("nop")) {
                    st.items().forEach((mat, n) -> send(p, c("   &7Trong túi: &f" + count(p, mat) + "&7/" + n
                            + " &7(đồ thường, không đổi tên)")));
                }
                said = true;
            }
        }
        if (b < steps.size() && !data.getBoolean(k + ".nhan") && (asked || paid)) {
            Step st = steps.get(b);
            say(p, g, !st.noi().isEmpty() ? st.noi() : g.nhan());
            send(p, c("&6Bước " + (b + 1) + "/" + steps.size() + ": " + stepLine(st, 0) + (st.how().isEmpty() ? "" : "\n   &7" + st.how())));
            data.set(k + ".nhan", true);
            data.set(k + ".dem", 0);
            dirty = true;
            said = true;
        } else if (b >= steps.size() && (asked || paid)) {
            say(p, g, g.het());
            said = true;
        }
        if (said) {
            flush();
        }
        return said;
    }

    /** Phần lữ khách của /nhiemvu: bước đang làm của từng người. */
    private void wandererQuests(Player p) {
        if (chains.isEmpty()) {
            return;
        }
        UUID u = p.getUniqueId();
        List<String> lines = new ArrayList<>();
        int doneAll = 0;
        for (Map.Entry<String, List<Step>> e : chains.entrySet()) {
            String k = ck(u, e.getKey());
            int b = data.getInt(k + ".b", 0);
            if (b >= e.getValue().size()) {
                doneAll++;
                continue;
            }
            if (!data.getBoolean(k + ".nhan")) {
                continue;
            }
            Step st = e.getValue().get(b);
            int dem = data.getInt(k + ".dem", 0);
            boolean ok = !st.type().equals("nop") && dem >= st.amount();
            lines.add(givers.get(e.getKey()).name() + " &7(bước " + (b + 1) + "/" + e.getValue().size() + ")");
            lines.add(ok ? " &a✔ " + st.name() + " &f— xong, về kể cho lữ khách nhận thưởng"
                    : " &f• " + stepLine(st, dem) + (st.how().isEmpty() ? "" : "\n    &7" + st.how()));
        }
        if (lines.isEmpty()) {
            if (doneAll < chains.size()) {
                p.sendMessage(c("&6Lữ khách nhờ việc: &7gặp lữ khách ngoài đất hoang (&f/lukhach &7xem họ ở đâu), hỏi &f“Ông có việc gì cần giúp không?”"
                        + " &7(" + chains.size() + " người, mỗi người " + chains.values().iterator().next().size() + " việc khó dần)"));
            }
            return;
        }
        p.sendMessage(c("&6Lữ khách nhờ việc &7(/lukhach xem họ đang ở đâu)"));
        lines.forEach(l -> p.sendMessage(c(l)));
    }

    /** Lúc vào server: gắn lại tag cho NPC hội thoại còn việc xong chưa trả (chỉ gắn, không gỡ, đỡ lệnh thừa). */
    private void syncTags(Player p) {
        for (Giver g : givers.values()) {
            if ("bq".equals(g.npc()) && claimable(p.getUniqueId(), g)) {
                syncTag(p, g);
            }
        }
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                syncTags(p);
            }
        }, 40L);
        // 30/10: nhắc nhiệm vụ hôm nay còn dở (bấm = /nhiemvu)
        List<String> qs = todays(p.getUniqueId());
        int left = 0;
        for (String qid : qs) {
            Quest q = pool.get(qid);
            if (q != null && data.getInt("players." + p.getUniqueId() + ".progress." + qid, 0) < need(q)) {
                left++;
            }
        }
        if (left > 0) {
            JoinNotes.add(p, 35, c("&b☰ &fCòn &e" + left + "&f/" + qs.size() + " nhiệm vụ hôm nay chưa xong. ")
                    .append(c("&a[/nhiemvu]").clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/nhiemvu"))));
        }
    }

    /** Module khác báo người chơi vừa dùng một tính năng (mã trong daily-quests.intro). */
    static void feature(Player p, String id) {
        NewbiePath.feature(p, id);   // đường tân thủ dùng chung mã
        DailyQuests d = INSTANCE;
        if (d != null) {
            d.intro(p, id);
            d.chainProgress(p, "feature", id.toUpperCase(Locale.ROOT), false, 1);
        }
    }

    /** /sell (lệnh hoặc khung shop): chờ 2 tick, có tiền về mới tính việc "ban" (bán hụt không tính). */
    static void watchSell(Player p) {
        DailyQuests d = INSTANCE;
        if (d == null || !d.intros.containsKey("ban") || d.introDone(p.getUniqueId(), "ban") || !VaultEconomy.available()) {
            return;
        }
        double before = VaultEconomy.balance(p);
        Bukkit.getScheduler().runTaskLater(d.plugin, () -> {
            if (p.isOnline() && VaultEconomy.balance(p) > before) {
                d.intro(p, "ban");
            }
        }, 2L);
    }

    private boolean introDone(UUID id, String intro) {
        return data.getStringList("intro." + id).contains(intro);
    }

    private void intro(Player p, String id) {
        Intro in = intros.get(id);
        UUID u = p.getUniqueId();
        if (in == null || introDone(u, id)) {
            return;
        }
        if (in.giver() == null) {
            claim(p, List.of(id));
            return;
        }
        List<String> nhan = list(u, "nhan");
        if (!nhan.remove(id)) {
            return;   // chưa nhận việc từ NPC: chưa tính
        }
        List<String> xong = list(u, "xong");
        xong.add(id);
        data.set(lk(u) + ".nhan", nhan);
        data.set(lk(u) + ".xong", xong);
        dirty = true;
        Giver g = givers.get(in.giver());
        p.sendMessage(c("&a✔ Xong việc: &f" + in.name() + "&a. Về gặp " + g.name() + (g.where().isEmpty() ? "" : " &7(" + g.where() + ")")
                + " &anhận thưởng."));
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        syncTag(p, g);
        flush();
    }

    /** Trả quà các việc (ghi vào intro.<uuid>); việc cuối cùng xong thì thưởng thêm intro-all-rewards. */
    private void claim(Player p, List<String> ids) {
        UUID u = p.getUniqueId();
        List<String> done = new ArrayList<>(data.getStringList("intro." + u));
        boolean added = false;
        for (String id : ids) {
            Intro in = intros.get(id);
            if (in == null || done.contains(id)) {
                continue;
            }
            done.add(id);
            added = true;
            List<String> got = rewards.give(p, in.rewards());
            send(p, c("&a✔ " + in.name() + (got.isEmpty() ? "" : "&a. Nhận: &f" + String.join("&7, &f", got))));
        }
        if (!added) {
            return;
        }
        data.set("intro." + u, done);
        dirty = true;
        if (intros.keySet().stream().allMatch(done::contains)) {
            List<String> bonus = rewards.give(p, cfg.getStringList("intro-all-rewards"));
            send(p, c("&6★ Đã giúp hết dân làng!" + (bonus.isEmpty() ? "" : " Thưởng thêm: &f" + String.join("&7, &f", bonus))));
        }
        flush();
    }

    // ---------------------------------------------------------------- việc dân làng nhờ: NPC giao / trả

    private static String lk(UUID id) {
        return "lang." + id;
    }

    private boolean opened(UUID id) {
        return data.getBoolean(lk(id) + ".mo");
    }

    private List<String> list(UUID id, String k) {
        return new ArrayList<>(data.getStringList(lk(id) + "." + k));
    }

    private Giver head() {
        return givers.values().iterator().next();
    }

    private String rewardText(Intro in) {
        return in.rewards().isEmpty() ? "" : " &7→ &a" + String.join("&7, &a", rewards.describe(in.rewards()));
    }

    private void say(Player p, Giver g, String line) {
        if (!line.isBlank()) {
            send(p, c(g.name() + "&7: &f“" + line + "&f”"));
        }
    }

    /** NPC quầy ("quay:<mã>") / Nhà Buôn ("donbuon") được bấm: true = NPC đã nói (giao / trả việc), bên gọi không mở menu lần này. */
    static boolean talkAt(Player p, String npc) {
        DailyQuests d = INSTANCE;
        if (d == null) {
            return false;
        }
        for (Giver g : d.givers.values()) {
            if (g.npc().equals(npc) && d.talk(p, g, false)) {
                p.sendMessage(c("&7(bấm lần nữa để mở quầy)"));
                return true;
            }
        }
        return false;
    }

    /** /dmm viecnpc <người> <mã người giao>: hội thoại BetonQuest gọi (console). Trả / giao việc ngay, lời NPC hiện trong câu sau của
     *  hội thoại (%dmmviec_ketqua%) và chép vào chat khi hội thoại đóng (bảng che chat). */
    void talkCommand(CommandSender s, String[] args) {
        Player p = args.length >= 3 ? Bukkit.getPlayerExact(args[1]) : null;
        Giver g = args.length >= 3 ? givers.get(args[2].toLowerCase(Locale.ROOT)) : null;
        if (p == null || g == null) {
            s.sendMessage("/dmm viecnpc <người đang online> <" + String.join("|", givers.keySet()) + ">: NPC giao / trả việc dân làng");
            return;
        }
        UUID u = p.getUniqueId();
        NewbiePath.feature(p, g.id());   // đường tân thủ: bước "hỏi Già Làng" (gialang)
        List<Component> got = new ArrayList<>();
        capture.put(u, got);
        try {
            talk(p, g, true);   // trả thưởng / giao việc NGAY (bảng hội thoại hiện kết quả qua %dmmviec_ketqua% ở câu sau)
        } finally {
            capture.remove(u);
        }
        syncTag(p, g);
        SUMMARY.put(u, plainSummary(got));
        pending.put(u, got);    // chép vào chat khi hội thoại đóng (dự phòng 60 giây)
        Bukkit.getScheduler().runTaskLater(plugin, () -> sayPending(u), convEndHook ? 1200L : 20L);
    }

    /** asked = người chơi chủ động hỏi (hội thoại): không có gì mới cũng trả lời (việc còn dở / hết việc). */
    private boolean talk(Player p, Giver g, boolean asked) {
        List<Step> chain = chains.get(g.id());
        if (chain != null) {
            return talkChain(p, g, chain, asked);
        }
        UUID u = p.getUniqueId();
        boolean said = false;
        if (!opened(u)) {
            Giver h = head();
            if (h != g) {
                if (asked) {
                    say(p, g, "Cháu mới tới làng hả? Gặp " + h.name() + "&f trước đã" + (h.where().isEmpty() ? "" : " (" + h.where() + ")")
                            + ", cụ ấy sẽ chỉ việc cho.");
                }
                return asked;
            }
            data.set(lk(u) + ".mo", true);
            dirty = true;
            say(p, g, g.mo());
            send(p, c("&6Việc dân làng nhờ &7(gặp từng người để nhận việc, làm xong quay lại người đó nhận thưởng):"));
            for (Giver x : givers.values()) {
                long n = intros.values().stream().filter(i -> x.id().equals(i.giver()) && !introDone(u, i.id())).count();
                if (n > 0) {
                    send(p, c("&7• " + x.name() + (x.where().isEmpty() ? "" : " &7· " + x.where()) + " &f(" + n + " việc)"));
                }
            }
            said = true;
        }
        List<String> nhan = list(u, "nhan");
        List<String> xong = list(u, "xong");
        List<String> tra = xong.stream().filter(id -> intros.containsKey(id) && g.id().equals(intros.get(id).giver())).toList();
        if (!tra.isEmpty()) {
            say(p, g, g.tra());
            xong.removeAll(tra);
            data.set(lk(u) + ".xong", xong);
            dirty = true;
            claim(p, tra);
            said = true;
        }
        List<Intro> moi = intros.values().stream().filter(i -> g.id().equals(i.giver()) && !introDone(u, i.id())
                && !nhan.contains(i.id()) && !xong.contains(i.id())).toList();
        if (!moi.isEmpty()) {
            say(p, g, g.nhan());
            for (Intro i : moi) {
                send(p, c("&7• &f" + i.name() + rewardText(i) + (i.how().isEmpty() ? "" : "\n   &7" + i.how())));
                nhan.add(i.id());
            }
            data.set(lk(u) + ".nhan", nhan);
            dirty = true;
            send(p, c("&a✔ Đã nhận " + moi.size() + " việc. &7Xong thì quay lại gặp " + g.name() + "&7 nhận thưởng (xem lại: &e/nhiemvu&7)."));
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_YES, 0.8f, 1f);
            said = true;
        }
        boolean allMine = intros.values().stream().filter(i -> g.id().equals(i.giver())).allMatch(i -> introDone(u, i.id()));
        if (allMine && (!tra.isEmpty() || asked)) {
            say(p, g, g.het());
            givers.values().stream().filter(x -> intros.values().stream().anyMatch(i -> x.id().equals(i.giver()) && !introDone(u, i.id())
                    && !nhan.contains(i.id()) && !xong.contains(i.id()))).findFirst()
                    .ifPresent(x -> send(p, c("&7➜ Gợi ý: gặp " + x.name() + (x.where().isEmpty() ? "" : " &7ở " + x.where()))));
            said = true;
        } else if (!said && asked) {
            say(p, g, "Việc ta nhờ cháu vẫn chưa xong đâu:");
            intros.values().stream().filter(i -> g.id().equals(i.giver()) && nhan.contains(i.id()))
                    .forEach(i -> send(p, c("&7• &f" + i.name() + (i.how().isEmpty() ? "" : "\n   &7" + i.how()))));
            said = true;
        }
        if (said) {
            flush();
        }
        return said;
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s);
    }

    private static String today() {
        return LocalDate.now(VN).toString();
    }

    void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được nhiemvu.yml: " + e.getMessage());
        }
    }

    /** Nhiệm vụ hôm nay của người chơi (tạo mới khi sang ngày). */
    private List<String> todays(UUID id) {
        String key = "players." + id;
        if (!today().equals(data.getString(key + ".date"))) {
            List<String> ids = new ArrayList<>(pool.keySet());
            Collections.shuffle(ids, new Random((id.toString() + today()).hashCode()));
            List<String> pick = ids.subList(0, Math.min(cfg.getInt("per-day", 3), ids.size()));
            data.set(key, null);
            data.set(key + ".date", today());
            data.set(key + ".quests", new ArrayList<>(pick));
            dirty = true;
        }
        return data.getStringList(key + ".quests");
    }

    /** Chỉ tiêu thật: server vắng (LowPop) thì giảm. */
    private static int need(Quest q) {
        return Math.max(1, (int) Math.round(q.amount() * LowPop.questScale()));
    }

    /** Tên hiện cho người chơi: số trong tên ("Câu 10 con cá") đổi theo chỉ tiêu thật khi server vắng giảm việc. */
    private static String title(Quest q) {
        int n = need(q);
        if (n == q.amount()) {
            return q.name();
        }
        String t = q.name().replaceFirst("\\b" + q.amount() + "\\b", String.valueOf(n));
        return t.equals(q.name()) ? q.name() + " (" + n + ")" : t;
    }

    private static boolean matches(List<String> targets, String name) {
        for (String t : targets) {
            if (t.equals("*") || t.equals(name)
                    || (t.startsWith("*") && name.endsWith(t.substring(1)))
                    || (t.endsWith("*") && name.startsWith(t.substring(0, t.length() - 1)))) {
                return true;
            }
        }
        return false;
    }

    private void progress(Player p, String type, String target, boolean hostile, int n) {
        chainProgress(p, type, target, hostile, n);
        String key = "players." + p.getUniqueId();
        for (String qid : todays(p.getUniqueId())) {
            Quest q = pool.get(qid);
            if (q == null || !q.type().equals(type)) {
                continue;
            }
            if (!(matches(q.targets(), target) || (hostile && q.targets().contains("@HOSTILE")))) {
                continue;
            }
            int done = data.getInt(key + ".progress." + qid, 0);
            if (done >= need(q)) {
                continue;
            }
            done = Math.min(need(q), done + n);
            data.set(key + ".progress." + qid, done);
            dirty = true;
            p.sendActionBar(c("&e" + title(q) + " &f" + done + "&7/&f" + need(q)));
            if (done >= need(q)) {
                complete(p, q);
            }
        }
    }

    private void complete(Player p, Quest q) {
        List<String> got = rewards.give(p, q.rewards());
        p.sendMessage(c("&a✔ Xong nhiệm vụ: &f" + title(q) + (got.isEmpty() ? "" : "&a. Nhận: &f" + String.join("&7, &f", got))));
        ScreenNotify.show(p, "&a✔ Xong nhiệm vụ", "&f" + title(q));
        String key = "players." + p.getUniqueId();
        boolean all = true;
        for (String qid : todays(p.getUniqueId())) {
            Quest x = pool.get(qid);
            if (x != null && data.getInt(key + ".progress." + qid, 0) < need(x)) {
                all = false;
            }
        }
        if (all && !data.getBoolean(key + ".bonus")) {
            data.set(key + ".bonus", true);
            List<String> bonus = rewards.give(p, cfg.getStringList("complete-all-rewards"));
            ScreenNotify.show(p, "&6★ Xong hết nhiệm vụ!", "&7Hẹn bạn ngày mai");
            p.sendMessage(c("&6★ Xong hết nhiệm vụ hôm nay!" + (bonus.isEmpty() ? "" : " Thưởng thêm: &f" + String.join("&7, &f", bonus))));
        }
        flush();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Material m = b.getType();
        if (b.getBlockData() instanceof Ageable age && age.getAge() >= age.getMaximumAge()) {
            progress(e.getPlayer(), "harvest", m.name(), false, 1);
        }
        progress(e.getPlayer(), "break", m.name(), false, 1);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent e) {
        progress(e.getPlayer(), "place", e.getBlockPlaced().getType().name(), false, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k != null && !(e.getEntity() instanceof Player)) {
            progress(k, "kill", e.getEntityType().name(), e.getEntity() instanceof Monster, 1);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            progress(e.getPlayer(), "fish", "*", false, 1);
            intro(e.getPlayer(), "cauca");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSellCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent e) {
        String c = e.getMessage().toLowerCase(Locale.ROOT);
        if (c.startsWith("/sell") || c.startsWith("/esell") || c.startsWith("/essentials:sell")) {
            watchSell(e.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        flush();
    }

    /** Phần "Việc dân làng nhờ" của /nhiemvu: chỉ việc đang nhận / chờ nhận thưởng, việc chưa nhận chỉ ghi tên người giao. */
    private void villageQuests(Player p) {
        UUID u = p.getUniqueId();
        long left = intros.keySet().stream().filter(k -> !introDone(u, k)).count();
        if (left == 0) {
            p.sendMessage(c("&a✔ Đã giúp hết dân làng (" + intros.size() + " việc)."));
            return;
        }
        if (!givers.isEmpty() && !opened(u)) {
            Giver h = head();
            p.sendMessage(c("&6Việc dân làng nhờ: &7gặp " + h.name() + (h.where().isEmpty() ? "" : " &7ở " + h.where())
                    + "&7, hỏi &f“Trong làng có ai cần giúp không ạ?” &7(" + left + " việc có quà)"));
            return;
        }
        List<String> nhan = list(u, "nhan");
        List<String> xong = list(u, "xong");
        List<Intro> tudo = intros.values().stream().filter(i -> i.giver() == null && !introDone(u, i.id())).toList();
        p.sendMessage(c("&6Việc dân làng nhờ &7(" + (nhan.size() + xong.size() + tudo.size()) + " việc đang nhận)"));
        for (Giver g : givers.values()) {   // gom theo người giao: tên một lần, việc thụt vào dưới
            List<Intro> done = xong.stream().map(intros::get).filter(i -> i != null && g.id().equals(i.giver())).toList();
            List<Intro> doing = nhan.stream().map(intros::get).filter(i -> i != null && g.id().equals(i.giver())).toList();
            if (done.isEmpty() && doing.isEmpty()) {
                continue;
            }
            p.sendMessage(c(g.name() + (g.where().isEmpty() ? "" : " &7(" + shortWhere(g) + ")")));
            for (Intro in : done) {
                p.sendMessage(c(" &a✔ " + in.name() + " &f— xong, về gặp nhận" + rewardText(in)));
            }
            for (Intro in : doing) {
                p.sendMessage(c(" &f• " + in.name() + rewardText(in) + (in.how().isEmpty() ? "" : "\n    &7" + in.how())));
            }
        }
        for (Intro in : tudo) {
            p.sendMessage(c(" &f• " + in.name() + rewardText(in) + (in.how().isEmpty() ? "" : "\n    &7" + in.how())));
        }
        Map<String, List<String>> chua = new LinkedHashMap<>();   // nơi ở -> tên người còn việc chưa nhận
        for (Giver x : givers.values()) {
            if (intros.values().stream().anyMatch(i -> x.id().equals(i.giver()) && !introDone(u, i.id()) && !nhan.contains(i.id()) && !xong.contains(i.id()))) {
                chua.computeIfAbsent(shortWhere(x), k -> new ArrayList<>()).add(x.name());
            }
        }
        if (!chua.isEmpty()) {
            List<String> parts = new ArrayList<>();
            chua.forEach((w, names) -> parts.add(String.join("&7, ", names) + (w.isEmpty() ? "" : " &7(" + w + ")")));
            p.sendMessage(c("&7Còn việc chưa nhận, gặp: " + String.join("&7; ", parts)));
        }
        List<String> ib = rewards.describe(cfg.getStringList("intro-all-rewards"));
        if (!ib.isEmpty()) {
            p.sendMessage(c("&7Giúp hết dân làng: thưởng thêm &f" + String.join("&7, &f", ib)));
        }
    }

    /** Nơi ở ngắn gọn: "/warp trade" lấy từ where ("chợ /warp trade"); không có /warp thì giữ nguyên. */
    private static String shortWhere(Giver g) {
        int i = g.where().indexOf("/warp ");
        return i < 0 ? g.where() : g.where().substring(i);
    }

    boolean command(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Chỉ người chơi xem được nhiệm vụ.");
            return true;
        }
        String key = "players." + p.getUniqueId();
        List<String> qs = todays(p.getUniqueId());
        p.sendMessage(c("&6Nhiệm vụ hôm nay &7(đổi lúc 0 giờ)"
                + (LowPop.questScale() < 1 ? " &7· &bserver đang vắng nên nhẹ hơn" : "")));
        for (String qid : qs) {
            Quest q = pool.get(qid);
            if (q == null) {
                continue;
            }
            int done = data.getInt(key + ".progress." + qid, 0);
            int bars = 10 * Math.min(done, need(q)) / need(q);
            String bar = "&a" + "▮".repeat(bars) + "&8" + "▮".repeat(10 - bars);
            p.sendMessage(c((done >= need(q) ? "&a✔ " : "&7• ") + "&f" + title(q) + " " + bar + " &7" + done + "/" + need(q)
                    + (q.rewards().isEmpty() ? "" : " &7→ &a" + String.join("&7, &a", rewards.describe(q.rewards())))));
        }
        List<String> bonus = rewards.describe(cfg.getStringList("complete-all-rewards"));
        if (!bonus.isEmpty()) {
            p.sendMessage(c("&7Xong hết: thưởng thêm &f" + String.join("&7, &f", bonus) + (data.getBoolean(key + ".bonus") ? " &a(đã nhận)" : "")));
        }
        if (!intros.isEmpty()) {
            villageQuests(p);
        }
        wandererQuests(p);
        return true;
    }
}
