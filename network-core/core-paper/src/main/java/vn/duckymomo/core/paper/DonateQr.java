package vn.duckymomo.core.paper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ủng hộ bằng Vàng (20/10). /rank mua (/vang nap): chọn SỐ TIỀN nạp -> panel tạo yêu cầu (chung hàng chờ với web, /api/web/donate)
 * và trả ảnh QR VietQR (panel tự vẽ, data URI) -> vẽ lên TẤM BẢN ĐỒ đặt THẲNG VÀO TAY: món đang cầm được giấu trong tấm bản đồ
 * (PDC, nên server sập vẫn không mất) và trả lại đúng ô khi cất QR. Bấm [Tôi đã chuyển khoản] -> panel báo admin; admin duyệt = cộng Vàng.
 * /vang: xem ví, mở cửa hàng Vàng (rank ủng hộ, trang trí, tên), bấm 2 lần để mua (panel /api/web/vang trừ Vàng rồi cấp).
 * Cần PANEL_URL + PANEL_SITE_KEY trong môi trường và tài khoản ngân hàng trong site/config/site.yml.
 */
final class DonateQr implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final long[] AMOUNTS = {20_000, 50_000, 100_000, 200_000, 500_000, 1_000_000};

    /** Menu nạp: ô -> số tiền. Menu cửa hàng: ô -> mã món. */
    private static final class Holder implements InventoryHolder {
        Inventory inv;
        final Map<Integer, Long> amounts = new HashMap<>();
        final Map<Integer, JsonObject> items = new HashMap<>();
        int shopSlot = -1;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final JavaPlugin plugin;
    private final ConfigurationSection cfg;
    private final NamespacedKey key;
    private final NamespacedKey stashKey;
    private final String panelUrl = System.getenv().getOrDefault("PANEL_URL", "");
    private final String panelKey = System.getenv().getOrDefault("PANEL_SITE_KEY", "");
    private final Map<UUID, String> lastOrder = new HashMap<>();
    /** Bấm mua lần 1 ghi lại; bấm lại cùng món trong 6 giây mới mua (chống bấm nhầm). */
    private final Map<UUID, String> confirm = new HashMap<>();
    private final Map<UUID, Long> confirmAt = new HashMap<>();
    /** Số dư Vàng của người đang online (tên thường -> Vàng) cho placeholder %dmmvang_so_du% (bảng bên phải). Làm mới mỗi phút. */
    static final Map<String, Long> BAL = new java.util.concurrent.ConcurrentHashMap<>();

    DonateQr(JavaPlugin plugin, ConfigurationSection cfg, List<DonateRanks.Tier> tiers) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.key = new NamespacedKey(plugin, "qr_het");
        this.stashKey = new NamespacedKey(plugin, "qr_giu_do");
    }

    boolean ready() {
        return !panelUrl.isBlank() && !panelKey.isBlank();
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::removeExpired), 200L, 400L);
        if (ready()) {
            Bukkit.getScheduler().runTaskTimer(plugin, this::refreshBalances, 100L, 1200L);
        }
    }

    /** Hỏi panel số Vàng của mọi người đang online trong MỘT lần gọi (luồng phụ). */
    void refreshBalances() {
        List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (names.isEmpty() || !ready()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JsonObject r = call("GET", "/api/web/vang-ds?ten=" + URLEncoder.encode(String.join(",", names), StandardCharsets.UTF_8), null);
                if (r.has("so_du") && r.get("so_du").isJsonObject()) {
                    r.getAsJsonObject("so_du").entrySet().forEach(en -> BAL.put(en.getKey(), en.getValue().getAsLong()));
                }
            } catch (Exception ex) {
                plugin.getLogger().fine("Vàng: không làm mới số dư: " + ex.getMessage());
            }
        });
    }

    private static Component c(String s) {
        return Tip.ns(LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false));
    }

    private String web() {
        return cfg.getString("web", "");
    }

    private static String vnd(long v) {
        return String.format("%,d", v).replace(',', '.') + "đ";
    }

    // ------------------------------------------------------------ gọi panel

    private JsonObject call(String method, String path, String body) throws Exception {
        HttpURLConnection con = (HttpURLConnection) URI.create(panelUrl + path).toURL().openConnection();
        con.setConnectTimeout(5000);
        con.setReadTimeout(30000);
        con.setRequestMethod(method);
        con.setRequestProperty("X-DMM-Key", panelKey);
        con.setRequestProperty("X-DMM-Ip", "game");
        if (body != null) {
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = con.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = con.getResponseCode();
        try (InputStream in = code < 400 ? con.getInputStream() : con.getErrorStream()) {
            String txt = in == null ? "{}" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject o = JsonParser.parseString(txt).getAsJsonObject();
            if (code >= 400 || o.has("ok") && !o.get("ok").getAsBoolean()) {
                throw new IllegalStateException(o.has("message") ? o.get("message").getAsString() : "Lỗi " + code);
            }
            return o;
        }
    }

    private JsonObject wallet(String name) throws Exception {
        JsonObject w = call("GET", "/api/web/vang?ten=" + URLEncoder.encode(name, StandardCharsets.UTF_8), null);
        if (w.has("so_du")) {
            BAL.put(name.toLowerCase(java.util.Locale.ROOT), w.get("so_du").getAsLong());
        }
        return w;
    }

    private static String str(JsonObject o, String k) {
        return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static long num(JsonObject o, String k) {
        return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsLong() : 0;
    }

    /** Chạy việc mạng ở luồng phụ rồi quay về luồng chính; lỗi thì báo người chơi. */
    private void async(Player p, java.util.concurrent.Callable<JsonObject> work, java.util.function.BiConsumer<Player, JsonObject> done) {
        UUID id = p.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JsonObject r = work.call();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player pl = Bukkit.getPlayer(id);
                    if (pl != null) {
                        done.accept(pl, r);
                    }
                });
            } catch (Exception ex) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player pl = Bukkit.getPlayer(id);
                    if (pl != null) {
                        pl.sendMessage(c("&c[Vàng] " + ex.getMessage()));
                    }
                });
            }
        });
    }

    private static long vangFor(long tien, JsonObject w) {
        long rate = Math.max(1, num(w, "ty_gia"));
        long base = tien / rate;
        long pct = 0;
        if (w.has("thuong") && w.get("thuong").isJsonArray()) {
            for (JsonElement e : w.getAsJsonArray("thuong")) {
                if (tien >= num(e.getAsJsonObject(), "tu")) {
                    pct = num(e.getAsJsonObject(), "pct");
                }
            }
        }
        return base + base * pct / 100;
    }

    // ------------------------------------------------------------ menu nạp (/rank mua, /vang nap)

    void open(Player p) {
        if (!ready()) {
            p.sendMessage(c("&eNạp Vàng trên web: &f" + web() + " &7(đăng nhập bằng /web)"));
            return;
        }
        async(p, () -> wallet(p.getName()), (pl, w) -> {
            Holder h = new Holder();
            h.inv = Bukkit.createInventory(h, 27, c("&0◆ Nạp Vàng &8· có " + num(w, "so_du") + " Vàng"));
            int slot = 10;
            for (long t : AMOUNTS) {
                ItemStack it = Tip.of("&6◆ &f" + vnd(t)).sub("nhận " + vangFor(t, w) + " Vàng").accent("&6")
                        .desc("Chuyển khoản ngân hàng bằng mã QR").line()
                        .kv("Nhận được", "&6" + vangFor(t, w) + " Vàng").kv("Mã QR", "hiện trên tay, " + cfg.getInt("minutes", 10) + " phút")
                        .note("Số tiền khác: nạp trên web " + web()).line().click("Bấm để lấy mã QR")
                        .apply(new ItemStack(Material.GOLD_NUGGET, (int) Math.min(64, Math.max(1, t / 100_000))));
                h.inv.setItem(slot, it);
                h.amounts.put(slot, t);
                slot++;
            }
            ItemStack shop = Tip.of("&6⛃ &fCửa hàng Vàng").sub(num(w, "so_du") + " Vàng").accent("&6")
                    .desc("Tiêu Vàng bạn đang có").gap().head("Gồm").item("Rank ủng hộ").item("Đồ trang trí, hiệu ứng").item("Đổi tên màu")
                    .line().click("Bấm để mở").apply(new ItemStack(Material.GOLD_INGOT));
            h.inv.setItem(17, shop);
            h.shopSlot = 17;
            pl.openInventory(h.inv);
        });
    }

    // ------------------------------------------------------------ /vang: ví + cửa hàng

    boolean vangCommand(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Chỉ người chơi dùng được.");
            return true;
        }
        if (!ready()) {
            p.sendMessage(c("&eVàng xem và mua trên web: &f" + web()));
            return true;
        }
        if (a.length > 0 && a[0].equalsIgnoreCase("nap")) {
            open(p);
            return true;
        }
        openShop(p);
        return true;
    }

    private void openShop(Player p) {
        async(p, () -> wallet(p.getName()), (pl, w) -> {
            JsonArray mon = w.has("mon") ? w.getAsJsonArray("mon") : new JsonArray();
            int rows = Math.min(6, Math.max(2, (mon.size() + 9) / 9 + 1));
            Holder h = new Holder();
            long bal = num(w, "so_du");
            h.inv = Bukkit.createInventory(h, rows * 9, c("&0⛃ Cửa hàng Vàng &8· có " + bal + " Vàng"));
            int slot = 0;
            for (JsonElement e : mon) {
                if (slot >= rows * 9 - 9) {
                    break;
                }
                JsonObject m = e.getAsJsonObject();
                long gia = num(m, "gia");
                String loai = str(m, "loai");
                boolean can = bal >= gia;
                Tip t = Tip.of((can ? "&6◆ &f" : "&7◆ &7") + str(m, "ten")).accent("&6");
                if (!str(m, "nhom").isBlank()) {
                    t.sub(str(m, "nhom"));
                }
                t.desc(str(m, "mo_ta")).line().kv("Giá", "&6" + gia + " Vàng");
                long ngay = num(m, "ngay");
                long gio = num(m, "gio");   // 29/10: tiện ích bán theo giờ
                t.kv("Thời hạn", gio > 0 ? gio + " giờ &8(mua thêm cộng dồn)" : ngay > 0 ? ngay + " ngày &8(mua thêm cộng dồn)" : "vĩnh viễn");
                t.line();
                if (can) {
                    t.click("Bấm 2 lần để mua");
                } else {
                    t.deny("Thiếu " + (gia - bal) + " Vàng");
                }
                ItemStack it = t.apply(new ItemStack(loai.equals("rank") ? Material.NAME_TAG : can ? Material.GOLD_INGOT : Material.IRON_INGOT));
                h.inv.setItem(slot, it);
                h.items.put(slot, m);
                slot++;
            }
            ItemStack nap = Tip.of("&e✚ &fNạp thêm Vàng").desc("Lấy mã QR chuyển khoản").line()
                    .kv("Tỉ giá", vnd(num(w, "ty_gia")) + " = 1 Vàng").click("Bấm để chọn số tiền")
                    .apply(new ItemStack(Material.GOLD_NUGGET));
            h.inv.setItem(rows * 9 - 5, nap);
            h.amounts.put(rows * 9 - 5, -1L);
            pl.openInventory(h.inv);
        });
    }

    private void buy(Player p, JsonObject m) {
        String ma = str(m, "ma");
        long now = System.currentTimeMillis();
        if (!ma.equals(confirm.get(p.getUniqueId())) || now - confirmAt.getOrDefault(p.getUniqueId(), 0L) > 6000) {
            confirm.put(p.getUniqueId(), ma);
            confirmAt.put(p.getUniqueId(), now);
            p.sendMessage(c("&6[Vàng] &fBấm lại &e" + str(m, "ten") + " &ftrong 6 giây để mua với &6" + num(m, "gia") + " Vàng&f."));
            return;
        }
        confirm.remove(p.getUniqueId());
        p.closeInventory();
        JsonObject body = new JsonObject();
        body.addProperty("ten", p.getName());
        body.addProperty("viec", "mua");
        body.addProperty("ma", ma);
        async(p, () -> call("POST", "/api/web/vang", body.toString()), (pl, r) -> {
            if (r.has("so_du")) {
                BAL.put(pl.getName().toLowerCase(java.util.Locale.ROOT), r.get("so_du").getAsLong());
            }
            pl.sendMessage(c("&a[Vàng] " + str(r, "message")));
        });
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof Holder h) {
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) {
                return;
            }
            int slot = e.getRawSlot();
            if (slot == h.shopSlot) {
                p.closeInventory();
                openShop(p);
            } else if (h.amounts.containsKey(slot)) {
                long t = h.amounts.get(slot);
                p.closeInventory();
                if (t < 0) {
                    open(p);
                } else {
                    create(p, t);
                }
            } else if (h.items.containsKey(slot)) {
                buy(p, h.items.get(slot));
            }
            return;
        }
        // bản đồ QR: không cho cất vào rương/hòm khác, không đổi chỗ bằng phím số
        if (e.getView().getTopInventory().getType() != InventoryType.CRAFTING
                && (isQr(e.getCurrentItem()) || isQr(e.getCursor()) || e.getHotbarButton() >= 0
                && isQr(e.getWhoClicked().getInventory().getItem(e.getHotbarButton())))) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder || isQr(e.getOldCursor())) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ tạo mã nạp

    private void create(Player p, long tien) {
        p.sendMessage(c("&6[Ủng hộ] &7Đang tạo mã QR…"));
        JsonObject body = new JsonObject();
        body.addProperty("action", "tao");
        body.addProperty("ten", p.getName());
        body.addProperty("tien", tien);
        UUID id = p.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JsonObject res = call("POST", "/api/web/donate", body.toString());
                JsonObject don = res.getAsJsonObject("don");
                String qr = str(res, "qr");
                BufferedImage img = qr.isBlank() ? null : fetch(qr);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player pl = Bukkit.getPlayer(id);
                    if (pl != null) {
                        deliver(pl, don, img, res);
                    }
                });
            } catch (Exception ex) {
                plugin.getLogger().warning("Ủng hộ QR: " + ex);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player pl = Bukkit.getPlayer(id);
                    if (pl != null) {
                        pl.sendMessage(c("&c[Ủng hộ] Không tạo được mã: " + ex.getMessage() + ". Thử trên web: &f" + web()));
                    }
                });
            }
        });
    }

    /** Ảnh QR: data URI do panel tự vẽ (hoặc link ảnh), đổi về đen trắng 128x128 cho rõ trên bản đồ. */
    private static BufferedImage fetch(String url) throws Exception {
        BufferedImage src;
        if (url.startsWith("data:image/")) {
            src = ImageIO.read(new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(url.substring(url.indexOf(',') + 1))));
        } else {
            HttpURLConnection con = (HttpURLConnection) URI.create(url).toURL().openConnection();
            con.setConnectTimeout(5000);
            con.setReadTimeout(10000);
            con.setInstanceFollowRedirects(true);
            try (InputStream in = con.getInputStream()) {
                src = ImageIO.read(in);
            }
        }
        if (src == null) {
            throw new IllegalStateException("ảnh QR hỏng");
        }
        BufferedImage out = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                int rgb = src.getRGB(x * src.getWidth() / 128, y * src.getHeight() / 128);
                int lum = ((rgb >> 16 & 0xff) * 3 + (rgb >> 8 & 0xff) * 6 + (rgb & 0xff)) / 10;
                out.setRGB(x, y, lum < 128 ? 0x000000 : 0xFFFFFF);
            }
        }
        return out;
    }

    private void deliver(Player p, JsonObject don, BufferedImage img, JsonObject res) {
        String rid = str(don, "id");
        String content = str(don, "noi_dung");
        String money = vnd(num(don, "tien"));
        lastOrder.put(p.getUniqueId(), rid);
        if (img == null) {
            p.sendMessage(c("&e[Ủng hộ] Server chưa điền tài khoản ngân hàng. Nhắn admin, nội dung chuyển khoản: &f" + content));
            return;
        }
        removeAll(p);
        MapView view = Bukkit.createMap(p.getWorld());
        view.getRenderers().forEach(view::removeRenderer);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.setLocked(true);
        view.addRenderer(new MapRenderer() {
            private boolean drawn;

            @Override
            public void render(MapView map, MapCanvas canvas, Player player) {
                if (!drawn) {
                    canvas.drawImage(0, 0, img);
                    drawn = true;
                }
            }
        });
        int minutes = cfg.getInt("minutes", 10);
        ItemStack map = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) map.getItemMeta();
        meta.setMapView(view);
        Tip qt = Tip.of("&6◆ &fQR nạp Vàng").sub(minutes + " phút").accent("&6").desc("Mở app ngân hàng, quét mã này").line()
                .kv(str(don, "bac"), "&6" + str(don, "goi")).kv("Số tiền", "&e" + money).kv("Nội dung", content);
        JsonObject bank = res.has("bank") && res.get("bank").isJsonObject() ? res.getAsJsonObject("bank") : new JsonObject();
        if (!str(bank, "ngan-hang").isBlank()) {
            qt.kv("Ngân hàng", str(bank, "ngan-hang")).kv("Số TK", str(bank, "so-tai-khoan")).kv("Chủ TK", str(bank, "chu-tai-khoan"));
        }
        qt.line().click("Xong thì bấm [Tôi đã chuyển khoản] trong chat").click("Q / cất đi", "huỷ, trả lại món đang cầm");
        meta.displayName(qt.name());
        meta.lore(qt.lore());
        meta.getPersistentDataContainer().set(key, PersistentDataType.LONG, System.currentTimeMillis() + minutes * 60_000L);
        // Đặt THẲNG vào tay, không tốn ô: món đang cầm giấu trong tấm bản đồ, cất QR thì trả lại đúng chỗ
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!hand.getType().isAir()) {
            meta.getPersistentDataContainer().set(stashKey, PersistentDataType.BYTE_ARRAY, hand.serializeAsBytes());
        }
        map.setItemMeta(meta);
        p.getInventory().setItemInMainHand(map);
        p.sendMessage(c("&6[Ủng hộ] &fMã QR đang trên tay bạn, hiệu lực &e" + minutes + " phút&f. Số tiền &e" + money
                + "&f → &6" + str(don, "goi") + "&f. Nội dung &f" + content + "&f."));
        p.sendMessage(c("&a[Tôi đã chuyển khoản]").clickEvent(ClickEvent.runCommand("/rank dachuyen " + rid))
                .hoverEvent(HoverEvent.showText(c("Báo admin kiểm tra và cộng Vàng")))
                .append(c("  ")).append(c("&c[Huỷ]").clickEvent(ClickEvent.runCommand("/rank huyqr")))
                .append(c("  &7(món đang cầm sẽ trả lại khi cất mã)")));
    }

    // ------------------------------------------------------------ báo đã chuyển, huỷ

    void reported(Player p, String rid) {
        if (!ready()) {
            return;
        }
        String id = rid == null || rid.isBlank() ? lastOrder.get(p.getUniqueId()) : rid;
        if (id == null) {
            p.sendMessage(c("&7Chưa có mã nào. Gõ &e/rank mua"));
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("action", "bao");
        body.addProperty("ten", p.getName());
        body.addProperty("id", id);
        async(p, () -> call("POST", "/api/web/donate", body.toString()), (pl, r) -> {
            pl.sendMessage(c("&a[Ủng hộ] " + (str(r, "message").isBlank() ? "Đã báo admin." : str(r, "message"))));
            removeAll(pl);
        });
    }

    void cancel(Player p) {
        removeAll(p);
        p.sendMessage(c("&7[Ủng hộ] Đã cất mã QR. Chưa chuyển thì không cần làm gì thêm."));
    }

    // ------------------------------------------------------------ bản đồ QR

    private boolean isQr(ItemStack it) {
        return it != null && it.getType() == Material.FILLED_MAP && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.LONG);
    }

    /** Món đang cầm lúc nhận QR (giấu trong tấm bản đồ), null nếu tay không. */
    private ItemStack stashed(ItemStack qr) {
        byte[] b = qr.getItemMeta().getPersistentDataContainer().get(stashKey, PersistentDataType.BYTE_ARRAY);
        try {
            return b == null ? null : ItemStack.deserializeBytes(b);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("QR: không đọc được món đồ giữ trong bản đồ: " + ex);
            return null;
        }
    }

    /** Thay tấm bản đồ QR ở ô i bằng món đồ đã giấu (hoặc để trống). */
    private void restore(Player p, int i, ItemStack qr) {
        p.getInventory().setItem(i, stashed(qr));
    }

    private void removeAll(Player p) {
        ItemStack[] inv = p.getInventory().getContents();
        for (int i = 0; i < inv.length; i++) {
            if (isQr(inv[i])) {
                restore(p, i, inv[i]);
            }
        }
    }

    private void removeExpired(Player p) {
        long now = System.currentTimeMillis();
        ItemStack[] inv = p.getInventory().getContents();
        for (int i = 0; i < inv.length; i++) {
            if (isQr(inv[i])) {
                Long until = inv[i].getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.LONG);
                if (until == null || now > until) {
                    restore(p, i, inv[i]);
                    p.sendMessage(c("&7[Ủng hộ] Mã QR đã hết hạn. Cần mã mới: &e/rank mua"));
                }
            }
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (isQr(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);   // bấm Q: cất mã và trả món cũ về tay
            Bukkit.getScheduler().runTask(plugin, () -> cancel(e.getPlayer()));
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (isQr(e.getMainHandItem()) || isQr(e.getOffHandItem())) {
            e.setCancelled(true);   // không đưa QR sang tay trái (lẫn món đồ giấu bên trong)
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        List<ItemStack> back = new ArrayList<>();
        e.getDrops().removeIf(it -> {
            if (isQr(it)) {
                ItemStack s = stashed(it);
                if (s != null) {
                    back.add(s);   // món đồ giấu trong QR rơi ra như đồ thường
                }
                return true;
            }
            return false;
        });
        e.getDrops().addAll(back);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        removeAll(e.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        removeAll(e.getPlayer());   // server sập lúc đang cầm QR: trả món đồ giấu bên trong
        Bukkit.getScheduler().runTaskLater(plugin, this::refreshBalances, 60L);
    }
}
