package vn.duckymomo.skyvang;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * /vang cho Skyblock (31/10): ví Vàng + cửa hàng món Skyblock. Server này không chạy DuckyMoMoCore nên plugin chỉ làm đúng việc này:
 * hỏi panel /api/web/vang?may=skyblock-1 (chỉ món có server = skyblock-1), bấm 2 lần để mua (panel trừ Vàng rồi chạy lệnh cấp, lỗi thì hoàn).
 * Mọi lần gọi panel chạy luồng phụ, không có tác vụ lặp: không gọi gì khi không ai dùng /vang. Nạp Vàng: /vang nap ra link trang tài khoản.
 */
public final class SkyVang extends JavaPlugin implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final String SERVER = "skyblock-1";
    private static final int[] SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final String panelUrl = System.getenv().getOrDefault("PANEL_URL", "");
    private final String panelKey = System.getenv().getOrDefault("PANEL_SITE_KEY", "");
    private final String web = System.getenv().getOrDefault("VANG_WEB", "https://app.goc6.io.vn/mc/web/taikhoan.html");
    private final Map<UUID, String> confirm = new HashMap<>();
    private final Set<UUID> busy = new HashSet<>();

    private static final class Holder implements InventoryHolder {
        final Map<Integer, JsonObject> items = new HashMap<>();
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info(panelUrl.isBlank() || panelKey.isBlank() ? "Thiếu PANEL_URL/PANEL_SITE_KEY: /vang chưa dùng được" : "/vang sẵn sàng");
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Lệnh này phải gõ trong game.");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("nap")) {
            nap(p);
            return true;
        }
        open(p);
        return true;
    }

    private void nap(Player p) {
        p.sendMessage(c("&6✦ &fNạp Vàng ở trang tài khoản, quét QR ngân hàng xong Vàng về ví:"));
        p.sendMessage(c("&e&n" + web).clickEvent(ClickEvent.openUrl(web)));
    }

    /** Luồng phụ: gọi panel; xong về luồng chính mở menu. */
    private void open(Player p) {
        String name = p.getName();
        async(p, () -> call("GET", "/api/web/vang?may=" + SERVER + "&ten=" + URLEncoder.encode(name, StandardCharsets.UTF_8), null), r -> show(p, r));
    }

    private interface Job {
        JsonObject run() throws Exception;
    }

    private interface Done {
        void run(JsonObject r);
    }

    private void async(Player p, Job job, Done done) {
        if (!busy.add(p.getUniqueId())) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            JsonObject r = null;
            String err = null;
            try {
                r = job.run();
            } catch (Exception e) {
                err = e.getMessage();
            }
            JsonObject res = r;
            String msg = err;
            Bukkit.getScheduler().runTask(this, () -> {
                busy.remove(p.getUniqueId());
                if (!p.isOnline()) {
                    return;
                }
                if (res == null) {
                    p.sendMessage(c("&c" + (msg == null ? "Không kết nối được, thử lại sau." : msg)));
                } else {
                    done.run(res);
                }
            });
        });
    }

    private void show(Player p, JsonObject w) {
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 54, c("&6&lVàng · Skyblock"));
        h.inv = inv;
        ItemStack pane = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, pane);
        }
        long du = w.has("so_du") ? w.get("so_du").getAsLong() : 0;
        inv.setItem(4, item(Material.GOLD_BLOCK, "&6✦ Ví Vàng: &e" + du, List.of("&7Vàng dùng chung tài khoản,", "&7mua gì thì cấp ngay ở server này.")));
        JsonArray mon = w.has("mon") ? w.getAsJsonArray("mon") : new JsonArray();
        int i = 0;
        for (JsonElement e : mon) {
            if (i >= SLOTS.length) {
                break;
            }
            JsonObject m = e.getAsJsonObject();
            String ma = str(m, "ma");
            int gia = m.get("gia").getAsInt();
            List<String> lore = new ArrayList<>();
            lore.add("&7" + str(m, "mo_ta"));
            lore.add("");
            lore.add("&eGiá: &6" + gia + " Vàng");
            lore.add(du >= gia ? "&aBấm 2 lần để mua" : "&cChưa đủ Vàng, /vang nap");
            inv.setItem(SLOTS[i], item(ma.contains("xu") ? Material.GOLD_INGOT : Material.NETHER_STAR, "&f" + str(m, "ten"), lore));
            h.items.put(SLOTS[i], m);
            i++;
        }
        if (i == 0) {
            inv.setItem(22, item(Material.BARRIER, "&7Chưa có món nào", List.of()));
        }
        inv.setItem(49, item(Material.EMERALD, "&aNạp Vàng", List.of("&7Lấy link trang tài khoản")));
        confirm.remove(p.getUniqueId());
        p.openInventory(inv);
    }

    private static ItemStack item(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> {
            meta.displayName(c(name));
            meta.lore(lore.stream().map(SkyVang::c).toList());
        });
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h) || !(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        e.setCancelled(true);
        if (e.getRawSlot() == 49) {
            p.closeInventory();
            nap(p);
            return;
        }
        JsonObject m = h.items.get(e.getRawSlot());
        if (m == null) {
            return;
        }
        String ma = str(m, "ma");
        if (!ma.equals(confirm.get(p.getUniqueId()))) {
            confirm.put(p.getUniqueId(), ma);
            p.sendMessage(c("&eBấm lần nữa để mua &f" + str(m, "ten") + " &e(" + m.get("gia").getAsInt() + " Vàng)."));
            return;
        }
        confirm.remove(p.getUniqueId());
        p.closeInventory();
        JsonObject body = new JsonObject();
        body.addProperty("viec", "mua");
        body.addProperty("ten", p.getName());
        body.addProperty("ma", ma);
        async(p, () -> call("POST", "/api/web/vang", body.toString()), r -> p.sendMessage(c("&a✔ " + str(r, "message"))));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

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
}
