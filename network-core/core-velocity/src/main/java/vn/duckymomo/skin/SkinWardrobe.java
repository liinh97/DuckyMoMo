package vn.duckymomo.skin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.skinsrestorer.api.PropertyUtils;
import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.property.InputDataResult;
import net.skinsrestorer.api.property.SkinIdentifier;
import net.skinsrestorer.api.property.SkinProperty;
import net.skinsrestorer.api.property.SkinType;
import net.skinsrestorer.api.property.SkinVariant;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Tủ skin /tuskin (chạy trên proxy vì SkinsRestorer chạy ở proxy). Mỗi người lưu tối đa N skin rồi đổi qua lại;
 * N = quyền duckymomo.tuskin.&lt;N&gt; cao nhất (Danh phận / rank khác cấp theo trang Cài đặt, quyền KHÔNG kèm context server),
 * duckymomo.tuskin.admin = 50, không có quyền nào = 1.
 * Skin lưu bằng mã của SkinsRestorer (tên tài khoản bản quyền / link ảnh qua MineSkin), mặc lại không cần tải lại,
 * và không tạo "custom skin" nên không lọt vào menu /skins chung của server.
 * Cầu nối web: HTTP nội bộ (cổng DMM_SKIN_PORT, khoá DMM_SKIN_KEY), chỉ panel gọi: GET /tuskin?ten=, POST /tuskin {ten, viec, nguon, so, nhan}.
 * Dữ liệu: plugins/duckymomoskin/tuskin.json (khoá = tên thường).
 */
public final class SkinWardrobe {
    static final int MAX = 50;
    private static final LegacyComponentSerializer L = LegacyComponentSerializer.legacyAmpersand();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Pattern NAME = Pattern.compile("^\\.?[A-Za-z0-9_]{1,17}$");
    private static final Pattern PREMIUM = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final Pattern URL = Pattern.compile("^https?://[^\\s\"'<>]{4,300}$");
    private static final String P = "&6[Tủ skin] ";

    private final ProxyServer proxy;
    private final Logger log;
    private final Path file;
    private final Map<String, List<Entry>> data = new ConcurrentHashMap<>();
    private final Map<String, Long> busy = new ConcurrentHashMap<>();
    private HttpServer http;
    /** Menu ở server game nhờ proxy chạy lệnh của proxy (vd /tuskin) cho người chơi: kênh duckymomo:cmd, CHỈ các lệnh dưới đây. */
    private static final MinecraftChannelIdentifier CMD = MinecraftChannelIdentifier.from("duckymomo:cmd");
    private static final java.util.Set<String> PROXY_CMDS = java.util.Set.of("tuskin", "wardrobe", "skin", "skins");

    /** Một skin trong tủ: tên gợi nhớ, mã SkinsRestorer (id + kiểu + dáng), mã ảnh texture để web vẽ đầu. */
    static final class Entry {
        String ten;
        String id;
        String variant;
        String type;
        String tex;
        long luc;
    }

    record Res(boolean ok, String msg) {
        static Res ok(String m) {
            return new Res(true, m);
        }

        static Res fail(String m) {
            return new Res(false, m);
        }
    }

    @Inject
    public SkinWardrobe(ProxyServer proxy, Logger log, @DataDirectory Path dir) {
        this.proxy = proxy;
        this.log = log;
        this.file = dir.resolve("tuskin.json");
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent e) {
        load();
        CommandManager cm = proxy.getCommandManager();
        cm.register(cm.metaBuilder("tuskin").aliases("wardrobe").plugin(this).build(), new Cmd());
        proxy.getChannelRegistrar().register(CMD);
        startHttp();
        log.info("Tủ skin: bật, {} người có tủ", data.size());
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent e) {
        if (!e.getIdentifier().equals(CMD)) {
            return;
        }
        e.setResult(PluginMessageEvent.ForwardResult.handled());   // không chuyển tiếp cho client
        if (!(e.getSource() instanceof ServerConnection sc)) {
            return;   // chỉ nhận từ server game, không nhận từ người chơi
        }
        String cmd;
        try {
            cmd = new java.io.DataInputStream(new java.io.ByteArrayInputStream(e.getData())).readUTF().trim();
        } catch (IOException ex) {
            return;
        }
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        String root = cmd.split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (!PROXY_CMDS.contains(root)) {
            log.warn("Lệnh từ menu không được phép chạy ở proxy: {}", cmd);
            return;
        }
        proxy.getCommandManager().executeAsync(sc.getPlayer(), cmd);
    }

    @Subscribe
    public void onStop(ProxyShutdownEvent e) {
        if (http != null) {
            http.stop(0);
        }
    }

    static Component c(String s) {
        return L.deserialize(s);
    }

    // ---------------------------------------------------------------- lưu trữ

    private void load() {
        try {
            if (Files.exists(file)) {
                Map<String, List<Entry>> m = GSON.fromJson(Files.readString(file), new TypeToken<Map<String, List<Entry>>>() { }.getType());
                if (m != null) {
                    m.forEach((k, v) -> data.put(k, Collections.synchronizedList(new ArrayList<>(v))));
                }
            }
        } catch (Exception ex) {
            log.warn("Tủ skin: không đọc được {}: {}", file, ex.getMessage());
        }
    }

    private synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("tuskin.json.tmp");
            Files.writeString(tmp, GSON.toJson(data));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            log.warn("Tủ skin: không ghi được {}: {}", file, ex.getMessage());
        }
    }

    private List<Entry> list(String name) {
        return data.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> Collections.synchronizedList(new ArrayList<>()));
    }

    // ---------------------------------------------------------------- giới hạn, người chơi

    int limit(Player p) {
        if (p.hasPermission("duckymomo.tuskin.admin")) {
            return MAX;
        }
        for (int n = MAX; n >= 1; n--) {
            if (p.hasPermission("duckymomo.tuskin." + n)) {
                return n;
            }
        }
        return 1;
    }

    int limit(UUID uuid) {
        Optional<Player> online = proxy.getPlayer(uuid);
        if (online.isPresent()) {
            return limit(online.get());
        }
        try {
            CachedPermissionData d = LuckPermsProvider.get().getUserManager().loadUser(uuid).get(5, TimeUnit.SECONDS)
                    .getCachedData().getPermissionData();
            if (d.checkPermission("duckymomo.tuskin.admin").asBoolean()) {
                return MAX;
            }
            for (int n = MAX; n >= 1; n--) {
                if (d.checkPermission("duckymomo.tuskin." + n).asBoolean()) {
                    return n;
                }
            }
        } catch (Exception ex) {
            log.warn("Tủ skin: không đọc được quyền của {}: {}", uuid, ex.getMessage());
        }
        return 1;
    }

    /** Người chơi theo tên: đang online thì lấy thật; offline thì UUID kiểu không bản quyền (proxy online-mode=false). Bedrock offline: null. */
    UUID uuidOf(String name) {
        Optional<Player> p = proxy.getPlayer(name);
        if (p.isPresent()) {
            return p.get().getUniqueId();
        }
        if (name.startsWith(".")) {
            return null;
        }
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static String label(String s, String fallback) {
        String t = s == null ? "" : s.replaceAll("[&§<>\"]", "").trim();
        if (t.length() > 24) {
            t = t.substring(0, 24);
        }
        return t.isEmpty() ? fallback : t;
    }

    // ---------------------------------------------------------------- việc chính

    private static SkinIdentifier idOf(Entry en) {
        return SkinIdentifier.of(en.id, en.variant == null ? null : SkinVariant.valueOf(en.variant), SkinType.valueOf(en.type));
    }

    private static Entry entry(SkinIdentifier id, SkinProperty prop, String ten) {
        Entry en = new Entry();
        en.ten = ten;
        en.id = id.getIdentifier();
        en.variant = id.getSkinVariant() == null ? null : id.getSkinVariant().name();
        en.type = id.getSkinType().name();
        try {
            en.tex = prop == null ? null : PropertyUtils.getSkinTextureHash(prop);
        } catch (RuntimeException ex) {
            en.tex = null;
        }
        en.luc = System.currentTimeMillis();
        return en;
    }

    private static int indexOf(List<Entry> l, Entry en) {
        synchronized (l) {
            for (int i = 0; i < l.size(); i++) {
                Entry x = l.get(i);
                if (x.id.equals(en.id) && x.type.equals(en.type)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void wear(UUID uuid, Entry en) throws Exception {
        SkinsRestorer sr = SkinsRestorerProvider.get();
        sr.getPlayerStorage().setSkinIdOfPlayer(uuid, idOf(en));
        Optional<Player> p = proxy.getPlayer(uuid);
        if (p.isPresent()) {
            sr.getSkinApplier(Player.class).applySkin(p.get());
        }
    }

    private boolean tooFast(String name) {
        long now = System.currentTimeMillis();
        Long last = busy.get(name.toLowerCase(Locale.ROOT));
        if (last != null && now - last < 5000) {
            return true;
        }
        busy.put(name.toLowerCase(Locale.ROOT), now);
        return false;
    }

    /** Thêm skin từ link ảnh hoặc tên tài khoản bản quyền, rồi mặc luôn. */
    Res add(String name, UUID uuid, String input, String nhan) {
        String in = input == null ? "" : input.trim();
        if (!URL.matcher(in).matches() && !PREMIUM.matcher(in).matches()) {
            return Res.fail("Nhập link ảnh skin (bắt đầu bằng https://) hoặc tên một tài khoản Minecraft bản quyền.");
        }
        if (tooFast(name)) {
            return Res.fail("Chờ vài giây rồi thử lại.");
        }
        List<Entry> l = list(name);
        try {
            Optional<InputDataResult> r = SkinsRestorerProvider.get().getSkinStorage().findOrCreateSkinData(in);
            if (r.isEmpty()) {
                return Res.fail("Không tìm thấy skin. Kiểm lại tên tài khoản bản quyền, hoặc link phải trỏ thẳng tới ảnh PNG 64x64.");
            }
            Entry en = entry(r.get().getIdentifier(), r.get().getProperty(),
                    label(nhan, PREMIUM.matcher(in).matches() ? in : "Skin " + (l.size() + 1)));
            int at = indexOf(l, en);
            if (at >= 0) {
                wear(uuid, l.get(at));
                return Res.ok("Skin này đã có ở ô " + (at + 1) + ", đã mặc.");
            }
            int lim = limit(uuid);
            if (l.size() >= lim) {
                return Res.fail("Tủ đã đầy (" + l.size() + "/" + lim + " ô). Xoá bớt một skin, hoặc lên bậc danh phận để có thêm ô.");
            }
            l.add(en);
            save();
            wear(uuid, en);
            return Res.ok("Đã thêm \"" + en.ten + "\" vào ô " + l.size() + " và mặc.");
        } catch (Exception ex) {
            log.warn("Tủ skin: thêm '{}' cho {} lỗi: {}", in, name, ex.getMessage());
            return Res.fail("Không tạo được skin: " + (ex.getMessage() == null ? "lỗi không rõ" : ex.getMessage()));
        }
    }

    /** Lưu skin đang mặc (đã đổi bằng /skin hoặc tủ) vào tủ. */
    Res saveCurrent(Player p, String nhan) {
        SkinsRestorer sr = SkinsRestorerProvider.get();
        Optional<SkinIdentifier> cur = sr.getPlayerStorage().getSkinIdOfPlayer(p.getUniqueId());
        if (cur.isEmpty()) {
            return Res.fail("Bạn đang mặc skin gốc, chưa có gì để lưu. Đổi skin bằng &f/skin <tên>&c hoặc &f/tuskin them <link|tên>&c.");
        }
        List<Entry> l = list(p.getUsername());
        Entry en = entry(cur.get(), sr.getSkinStorage().getSkinDataByIdentifier(cur.get()).orElse(null),
                label(nhan, "Skin " + (l.size() + 1)));
        int at = indexOf(l, en);
        if (at >= 0) {
            return Res.ok("Skin đang mặc đã có ở ô " + (at + 1) + ".");
        }
        int lim = limit(p);
        if (l.size() >= lim) {
            return Res.fail("Tủ đã đầy (" + l.size() + "/" + lim + " ô). Xoá bớt một skin, hoặc lên bậc danh phận để có thêm ô.");
        }
        l.add(en);
        save();
        return Res.ok("Đã lưu skin đang mặc vào ô " + l.size() + " (\"" + en.ten + "\").");
    }

    Res wearSlot(String name, UUID uuid, int so) {
        List<Entry> l = list(name);
        Entry en;
        synchronized (l) {
            if (so < 1 || so > l.size()) {
                return Res.fail("Không có ô " + so + " trong tủ.");
            }
            en = l.get(so - 1);
        }
        // tủ lớn hơn giới hạn hiện tại (bị hạ bậc): chỉ mặc được các ô trong giới hạn
        int lim = limit(uuid);
        if (so > lim) {
            return Res.fail("Ô " + so + " vượt giới hạn hiện tại (" + lim + " ô). Lên bậc để dùng lại, hoặc xoá bớt.");
        }
        try {
            wear(uuid, en);
            return Res.ok("Đã mặc \"" + en.ten + "\"" + (proxy.getPlayer(uuid).isPresent() ? "." : " (áp dụng khi bạn vào game)."));
        } catch (Exception ex) {
            log.warn("Tủ skin: mặc ô {} cho {} lỗi: {}", so, name, ex.getMessage());
            return Res.fail("Không mặc được skin này: " + ex.getMessage());
        }
    }

    Res remove(String name, int so) {
        List<Entry> l = list(name);
        Entry en;
        synchronized (l) {
            if (so < 1 || so > l.size()) {
                return Res.fail("Không có ô " + so + " trong tủ.");
            }
            en = l.remove(so - 1);
        }
        save();
        return Res.ok("Đã xoá \"" + en.ten + "\" khỏi tủ (skin đang mặc giữ nguyên).");
    }

    // ---------------------------------------------------------------- lệnh /tuskin

    private void async(Runnable r) {
        proxy.getScheduler().buildTask(this, r).schedule();
    }

    private void reply(Player p, Res r) {
        p.sendMessage(c(P + (r.ok() ? "&a" : "&c") + r.msg()));
    }

    private void show(Player p) {
        List<Entry> l = list(p.getUsername());
        int lim = limit(p);
        String cur = SkinsRestorerProvider.get().getPlayerStorage().getSkinIdOfPlayer(p.getUniqueId()).map(SkinIdentifier::getIdentifier).orElse("");
        p.sendMessage(c("&6&l👕 Tủ skin &7(" + l.size() + "/" + lim + " ô)"));
        List<Entry> copy;
        synchronized (l) {
            copy = new ArrayList<>(l);
        }
        if (copy.isEmpty()) {
            p.sendMessage(c("&7Tủ đang trống."));
        }
        for (int i = 0; i < copy.size(); i++) {
            Entry en = copy.get(i);
            int so = i + 1;
            Component line = c((so > lim ? "&8" : "&7") + so + ". &f" + en.ten + (en.id.equals(cur) ? " &e(đang mặc)" : "") + " ")
                    .append(c("&a[Mặc]").clickEvent(ClickEvent.runCommand("/tuskin mac " + so)).hoverEvent(HoverEvent.showText(c("&7Bấm để mặc skin này"))))
                    .append(c(" "))
                    .append(c("&c[Xoá]").clickEvent(ClickEvent.suggestCommand("/tuskin xoa " + so)).hoverEvent(HoverEvent.showText(c("&7Bấm rồi Enter để xoá"))));
            p.sendMessage(line);
        }
        p.sendMessage(c("&7Thêm: &f/tuskin them <link ảnh|tên bản quyền> [tên gợi nhớ] &7· Lưu skin đang mặc: &f/tuskin luu [tên]"));
        p.sendMessage(c("&7Hoặc đổi skin trên trang tài khoản web (tải ảnh lên được). Lên bậc danh phận để có thêm ô."));
    }

    private static String join(String[] a, int from) {
        return from >= a.length ? "" : String.join(" ", java.util.Arrays.copyOfRange(a, from, a.length));
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (RuntimeException ex) {
            return -1;
        }
    }

    final class Cmd implements SimpleCommand {
        @Override
        public void execute(Invocation inv) {
            if (!(inv.source() instanceof Player p)) {
                inv.source().sendMessage(c("&cChỉ người chơi dùng được."));
                return;
            }
            String[] a = inv.arguments();
            String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "", "ds", "xem" -> async(() -> show(p));
                case "luu" -> async(() -> reply(p, saveCurrent(p, join(a, 1))));
                case "them" -> {
                    if (a.length < 2) {
                        p.sendMessage(c(P + "&7Cách dùng: &f/tuskin them <link ảnh|tên bản quyền> [tên gợi nhớ]"));
                        return;
                    }
                    p.sendMessage(c(P + "&7Đang tạo skin, chờ vài giây…"));
                    async(() -> reply(p, add(p.getUsername(), p.getUniqueId(), a[1], join(a, 2))));
                }
                case "mac" -> async(() -> reply(p, a.length < 2 ? Res.fail("Cách dùng: /tuskin mac <số ô>") : wearSlot(p.getUsername(), p.getUniqueId(), num(a[1]))));
                case "xoa" -> async(() -> reply(p, a.length < 2 ? Res.fail("Cách dùng: /tuskin xoa <số ô>") : remove(p.getUsername(), num(a[1]))));
                default -> p.sendMessage(c(P + "&7/tuskin · /tuskin them <link|tên> [tên] · /tuskin luu [tên] · /tuskin mac <số> · /tuskin xoa <số>"));
            }
        }

        @Override
        public List<String> suggest(Invocation inv) {
            return inv.arguments().length <= 1 ? List.of("them", "luu", "mac", "xoa") : List.of();
        }
    }

    // ---------------------------------------------------------------- cầu nối web (chỉ panel gọi, trong mạng Docker)

    private void startHttp() {
        String key = System.getenv().getOrDefault("DMM_SKIN_KEY", "");
        if (key.isBlank()) {
            log.warn("Tủ skin: chưa đặt DMM_SKIN_KEY, tắt cầu nối web (lệnh /tuskin vẫn chạy)");
            return;
        }
        int port = Integer.parseInt(System.getenv().getOrDefault("DMM_SKIN_PORT", "8095"));
        try {
            http = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException ex) {
            log.warn("Tủ skin: không mở được cổng {}: {}", port, ex.getMessage());
            return;
        }
        http.createContext("/tuskin", ex -> {
            try {
                handle(ex, key);
            } catch (Exception err) {
                log.warn("Tủ skin web: {}", err.toString());
                send(ex, 500, Map.of("ok", false, "message", "Lỗi máy chủ skin"));
            } finally {
                ex.close();
            }
        });
        http.setExecutor(Executors.newFixedThreadPool(2));
        http.start();
        log.info("Tủ skin: cầu nối web ở cổng {}", port);
    }

    private static void send(HttpExchange ex, int code, Object body) {
        try {
            byte[] b = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(code, b.length);
            ex.getResponseBody().write(b);
        } catch (IOException ignored) {
            // người gọi đã ngắt
        }
    }

    private Map<String, Object> state(String name) {
        UUID uuid = uuidOf(name);
        List<Entry> l = list(name);
        List<Map<String, Object>> ds = new ArrayList<>();
        String cur = uuid == null ? "" : SkinsRestorerProvider.get().getPlayerStorage().getSkinIdOfPlayer(uuid).map(SkinIdentifier::getIdentifier).orElse("");
        synchronized (l) {
            for (int i = 0; i < l.size(); i++) {
                Entry en = l.get(i);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("so", i + 1);
                m.put("ten", en.ten);
                m.put("tex", en.tex);
                m.put("dang_mac", en.id.equals(cur));
                ds.add(m);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("online", proxy.getPlayer(name).isPresent());
        out.put("gioi_han", uuid == null ? Math.max(1, l.size()) : limit(uuid));
        out.put("ds", ds);
        return out;
    }

    private void handle(HttpExchange ex, String key) throws IOException {
        String given = ex.getRequestHeaders().getFirst("X-DMM-Key");
        if (given == null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8))) {
            send(ex, 403, Map.of("ok", false, "message", "Sai khoá"));
            return;
        }
        if ("GET".equals(ex.getRequestMethod())) {
            String name = "";
            String q = ex.getRequestURI().getRawQuery();
            if (q != null) {
                for (String part : q.split("&")) {
                    if (part.startsWith("ten=")) {
                        name = URLDecoder.decode(part.substring(4), StandardCharsets.UTF_8);
                    }
                }
            }
            if (!NAME.matcher(name).matches()) {
                send(ex, 400, Map.of("ok", false, "message", "Tên không hợp lệ"));
                return;
            }
            send(ex, 200, state(name));
            return;
        }
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, Map.of("ok", false, "message", "Chỉ GET/POST"));
            return;
        }
        byte[] raw;
        try (InputStream in = ex.getRequestBody()) {
            raw = in.readNBytes(8192);
        }
        JsonObject b = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
        String name = b.has("ten") ? b.get("ten").getAsString() : "";
        if (!NAME.matcher(name).matches()) {
            send(ex, 400, Map.of("ok", false, "message", "Tên không hợp lệ"));
            return;
        }
        UUID uuid = uuidOf(name);
        String viec = b.has("viec") ? b.get("viec").getAsString() : "";
        Res r;
        if (uuid == null && !viec.equals("xoa")) {
            r = Res.fail("Tài khoản điện thoại (tên có dấu chấm) cần đang ở trong server mới đổi skin trên web được.");
        } else {
            int so = b.has("so") ? b.get("so").getAsInt() : -1;
            r = switch (viec) {
                case "them" -> add(name, uuid, b.has("nguon") ? b.get("nguon").getAsString() : "", b.has("nhan") ? b.get("nhan").getAsString() : "");
                case "mac" -> wearSlot(name, uuid, so);
                case "xoa" -> remove(name, so);
                default -> Res.fail("Việc không hợp lệ");
            };
        }
        Map<String, Object> out = state(name);
        out.put("ok", r.ok());
        out.put("message", r.msg().replaceAll("&[0-9a-fk-or]", ""));
        send(ex, r.ok() ? 200 : 400, out);
        // báo trong game nếu đang online
        proxy.getPlayer(name).ifPresent(p -> {
            if (r.ok()) {
                p.sendMessage(c(P + "&a" + r.msg() + " &7(từ trang web)"));
            }
        });
    }
}
