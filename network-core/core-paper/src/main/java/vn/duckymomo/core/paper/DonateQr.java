package vn.duckymomo.core.paper;

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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ủng hộ bằng QR ngay trong game (/rank mua, ý từ UniMC): chọn bậc + gói trong menu -> panel tạo yêu cầu (chung hàng chờ với trang web,
 * /api/web/donate) và trả link VietQR -> vẽ QR lên một TẤM BẢN ĐỒ đưa vào tay người chơi, hết hạn sau minutes phút.
 * Người chơi bấm [Tôi đã chuyển khoản] trong chat -> panel báo admin; admin duyệt ở trang quản trị > Donate (như cũ).
 * Cần biến môi trường PANEL_URL + PANEL_SITE_KEY trong container server (compose) và thông tin ngân hàng trong site/config/site.yml.
 * Tấm bản đồ QR có dấu PDC: không vứt ra, không cất vào rương, chết không rơi, hết hạn tự biến mất.
 */
final class DonateQr implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        final Map<Integer, int[]> slots = new HashMap<>();   // ô -> {bậc, gói}

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final JavaPlugin plugin;
    private final ConfigurationSection cfg;
    private final List<DonateRanks.Tier> tiers;
    private final NamespacedKey key;
    private final String panelUrl = System.getenv().getOrDefault("PANEL_URL", "");
    private final String panelKey = System.getenv().getOrDefault("PANEL_SITE_KEY", "");
    private final Map<UUID, String> lastOrder = new HashMap<>();

    DonateQr(JavaPlugin plugin, ConfigurationSection cfg, List<DonateRanks.Tier> tiers) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.tiers = tiers;
        this.key = new NamespacedKey(plugin, "qr_het");
    }

    boolean ready() {
        return !panelUrl.isBlank() && !panelKey.isBlank();
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::removeExpired), 200L, 400L);
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    private String web() {
        return cfg.getString("web", "");
    }

    // ------------------------------------------------------------ menu chọn gói

    void open(Player p) {
        if (!ready()) {
            p.sendMessage(c("&eỦng hộ trên web: &f" + web() + " &7(đăng nhập bằng /web)"));
            return;
        }
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 54, c("&6ỦNG HỘ SERVER &8(chỉ đồ trang trí)"));
        int row = 0;
        for (int ti = 0; ti < tiers.size() && row < 6; ti++) {
            DonateRanks.Tier t = tiers.get(ti);
            ItemStack head = new ItemStack(Material.NAME_TAG);
            ItemMeta hm = head.getItemMeta();
            hm.displayName(c(t.color() + t.name()));
            List<Component> lore = new ArrayList<>();
            for (String perk : t.perks()) {
                lore.add(c("&7• &f" + perk));
            }
            hm.lore(lore);
            head.setItemMeta(hm);
            h.inv.setItem(row * 9, head);
            for (int gi = 0; gi < t.prices().size() && gi < 7; gi++) {
                ItemStack it = new ItemStack(Material.GOLD_NUGGET, Math.min(64, gi + 1));
                ItemMeta m = it.getItemMeta();
                m.displayName(c(t.color() + t.name() + " &7· &e" + t.prices().get(gi)));
                m.lore(List.of(c("&a» Bấm để tạo mã QR chuyển khoản"), c("&7Mã có hiệu lực " + cfg.getInt("minutes", 10) + " phút")));
                it.setItemMeta(m);
                int slot = row * 9 + 2 + gi;
                h.inv.setItem(slot, it);
                h.slots.put(slot, new int[] {ti, gi});
            }
            row++;
        }
        p.openInventory(h.inv);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof Holder h) {
            e.setCancelled(true);
            int[] pick = h.slots.get(e.getRawSlot());
            if (pick != null && e.getWhoClicked() instanceof Player p) {
                p.closeInventory();
                create(p, pick[0], pick[1]);
            }
            return;
        }
        // bản đồ QR: không cho cất vào rương/hòm khác
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

    // ------------------------------------------------------------ gọi panel

    private JsonObject post(String body) throws Exception {
        HttpURLConnection con = (HttpURLConnection) URI.create(panelUrl + "/api/web/donate").toURL().openConnection();
        con.setConnectTimeout(5000);
        con.setReadTimeout(10000);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("X-DMM-Key", panelKey);
        con.setRequestProperty("X-DMM-Ip", "game");
        try (OutputStream os = con.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = con.getResponseCode();
        try (InputStream in = code < 400 ? con.getInputStream() : con.getErrorStream()) {
            String txt = in == null ? "{}" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject o = JsonParser.parseString(txt).getAsJsonObject();
            if (code >= 400) {
                throw new IllegalStateException(o.has("message") ? o.get("message").getAsString() : "Lỗi " + code);
            }
            return o;
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private void create(Player p, int ti, int gi) {
        p.sendMessage(c("&6[Ủng hộ] &7Đang tạo mã VietQR…"));
        UUID id = p.getUniqueId();
        String name = p.getName();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("action", "tao");
                body.addProperty("ten", name);
                body.addProperty("bac", ti);
                body.addProperty("goi", gi);
                JsonObject res = post(body.toString());
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

    /** Tải ảnh QR, đổi về đen trắng 128x128 (điểm ảnh gần nhất) cho rõ trên bản đồ. */
    private static BufferedImage fetch(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) URI.create(url).toURL().openConnection();
        con.setConnectTimeout(5000);
        con.setReadTimeout(10000);
        BufferedImage src;
        try (InputStream in = con.getInputStream()) {
            src = ImageIO.read(in);
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
        String money = String.format("%,d", don.has("tien") ? don.get("tien").getAsLong() : 0).replace(',', '.') + "đ";
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
        meta.displayName(c("&6QR ủng hộ &7(" + minutes + " phút)"));
        List<Component> lore = new ArrayList<>();
        lore.add(c("&7Gói: &f" + str(don, "bac") + " · " + str(don, "goi")));
        lore.add(c("&7Số tiền: &e" + money));
        lore.add(c("&7Nội dung CK: &f" + content));
        JsonObject bank = res.has("bank") && res.get("bank").isJsonObject() ? res.getAsJsonObject("bank") : new JsonObject();
        if (!str(bank, "ngan-hang").isBlank()) {
            lore.add(c("&7" + str(bank, "ngan-hang") + " · " + str(bank, "so-tai-khoan") + " · " + str(bank, "chu-tai-khoan")));
        }
        lore.add(c("&8Cầm trên tay, mở app ngân hàng quét mã"));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(key, PersistentDataType.LONG, System.currentTimeMillis() + minutes * 60_000L);
        map.setItemMeta(meta);
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            p.getInventory().setItemInMainHand(map);
        } else if (!p.getInventory().addItem(map).isEmpty()) {
            p.sendMessage(c("&c[Ủng hộ] Túi đầy, dọn 1 ô rồi bấm lại. Nội dung CK: &f" + content));
            return;
        }
        p.sendMessage(c("&6[Ủng hộ] &fMã QR đã ở trong túi (tấm bản đồ), hiệu lực &e" + minutes + " phút&f. Số tiền &e" + money
                + "&f, nội dung &f" + content + "&f."));
        p.sendMessage(c("&a[Tôi đã chuyển khoản]").clickEvent(ClickEvent.runCommand("/rank dachuyen " + rid))
                .hoverEvent(HoverEvent.showText(c("Báo admin kiểm tra và cấp rank")))
                .append(c("  ")).append(c("&c[Huỷ]").clickEvent(ClickEvent.runCommand("/rank huyqr"))));
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
        String name = p.getName();
        UUID uid = p.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String msg;
            try {
                JsonObject body = new JsonObject();
                body.addProperty("action", "bao");
                body.addProperty("ten", name);
                body.addProperty("id", id);
                JsonObject res = post(body.toString());
                msg = "&a[Ủng hộ] " + (res.has("message") ? res.get("message").getAsString() : "Đã báo admin.");
            } catch (Exception ex) {
                msg = "&c[Ủng hộ] " + ex.getMessage();
            }
            String m = msg;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player pl = Bukkit.getPlayer(uid);
                if (pl != null) {
                    pl.sendMessage(c(m));
                    removeAll(pl);
                }
            });
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

    private void removeAll(Player p) {
        ItemStack[] inv = p.getInventory().getContents();
        for (int i = 0; i < inv.length; i++) {
            if (isQr(inv[i])) {
                p.getInventory().setItem(i, null);
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
                    p.getInventory().setItem(i, null);
                    p.sendMessage(c("&7[Ủng hộ] Mã QR đã hết hạn. Cần mã mới: &e/rank mua"));
                }
            }
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (isQr(e.getItemDrop().getItemStack())) {
            e.getItemDrop().remove();
            e.getPlayer().sendMessage(c("&7[Ủng hộ] Đã cất mã QR."));
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(this::isQr);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        removeExpired(e.getPlayer());
    }
}
