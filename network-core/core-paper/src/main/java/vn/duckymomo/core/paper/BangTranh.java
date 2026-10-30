package vn.duckymomo.core.paper;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bảng tranh (30/10, chủ server: bảng hướng dẫn kiểu ảnh treo tường ở spawn): ảnh PNG cắt ô 128x128, mỗi ô một bản đồ trong khung
 * phát sáng (cố định, bất tử, khung ẩn). Ảnh: plugins/DuckyMoMoCore/bangtranh/<tên>.png, cạnh là bội số 128 (vẽ bằng
 * tools/bangtranh/ve-bang.py). Lưu mã bản đồ -> ô ảnh ở bangtranh.yml, bật plugin thì gắn lại hình (restart không mất).
 *   /dmm bangtranh <tên>        nhìn vào khối tường: khối đó là góc DƯỚI-TRÁI của bảng, bảng mọc sang phải + lên trên
 *   /dmm bangtranh xoa <tên>    gỡ mọi khung của bảng đó trong bán kính 16 khối
 *   /dmm bangtranh capnhat      vẽ lại hình từ file (sửa ảnh xong không cần đặt lại)
 */
final class BangTranh {
    private final JavaPlugin plugin;
    private final File dir;
    private final File file;
    private final YamlConfiguration data;
    private final Map<String, BufferedImage> cache = new HashMap<>();

    BangTranh(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "bangtranh");
        this.file = new File(plugin.getDataFolder(), "bangtranh.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        dir.mkdirs();
    }

    List<String> names() {
        List<String> out = new ArrayList<>();
        String[] f = dir.list((d, n) -> n.toLowerCase().endsWith(".png"));
        if (f != null) {
            for (String n : f) {
                out.add(n.substring(0, n.length() - 4));
            }
        }
        return out;
    }

    private BufferedImage image(String name) {
        return cache.computeIfAbsent(name, n -> {
            try {
                BufferedImage img = ImageIO.read(new File(dir, n + ".png"));
                return img == null || img.getWidth() % 128 != 0 || img.getHeight() % 128 != 0 ? null : img;
            } catch (IOException e) {
                return null;
            }
        });
    }

    /** Gắn hình cho mọi bản đồ đã lưu (lúc bật plugin / capnhat). */
    int restore() {
        int n = 0;
        var maps = data.getConfigurationSection("maps");
        if (maps == null) {
            return 0;
        }
        for (String id : maps.getKeys(false)) {
            String[] v = maps.getString(id, "").split(":");
            MapView view;
            try {
                view = Bukkit.getMap(Integer.parseInt(id));
            } catch (NumberFormatException e) {
                continue;
            }
            BufferedImage img = v.length == 3 ? image(v[0]) : null;
            if (view == null || img == null) {
                continue;
            }
            attach(view, img, Integer.parseInt(v[1]), Integer.parseInt(v[2]));
            n++;
        }
        return n;
    }

    private static void attach(MapView view, BufferedImage img, int cx, int cy) {
        BufferedImage tile = img.getSubimage(cx * 128, cy * 128, 128, 128);
        view.getRenderers().forEach(view::removeRenderer);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.addRenderer(new MapRenderer(false) {
            private boolean done;

            @Override
            public void render(MapView v, MapCanvas canvas, Player p) {
                if (!done) {
                    canvas.drawImage(0, 0, tile);
                    done = true;
                }
            }
        });
    }

    void command(CommandSender s, String[] args) {
        String sub = args.length >= 2 ? args[1].toLowerCase() : "";
        if (sub.equals("capnhat")) {
            cache.clear();
            s.sendMessage(Tip.c("&aĐã vẽ lại " + restore() + " ô bảng tranh (người chơi ra xa rồi vào lại mới thấy hình mới)."));
            return;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("Vào game, nhìn vào tường rồi gõ /dmm bangtranh <tên>");
            return;
        }
        if (sub.equals("xoa") && args.length >= 3) {
            int n = remove(p, args[2]);
            s.sendMessage(Tip.c("&7Đã gỡ " + n + " khung của bảng &f" + args[2] + "&7."));
            return;
        }
        BufferedImage img = sub.isEmpty() ? null : image(args[1]);
        if (img == null) {
            s.sendMessage(Tip.c("&e/dmm bangtranh <tên> &7nhìn vào tường (khối nhìn = góc dưới-trái) · &e/dmm bangtranh xoa <tên> &7· &e/dmm bangtranh capnhat"
                    + "\n&7Ảnh: plugins/DuckyMoMoCore/bangtranh/<tên>.png, cạnh bội số 128. Có: &f" + String.join(", ", names())));
            return;
        }
        Block base = p.getTargetBlockExact(8);
        var face = p.getTargetBlockFace(8);
        if (base == null || face == null || face == BlockFace.UP || face == BlockFace.DOWN) {
            s.sendMessage(Tip.c("&cNhìn thẳng vào mặt bên của một khối tường (trong 8 khối)."));
            return;
        }
        // người nhìn theo hướng ngược mặt khối; bên phải người nhìn = xoay hướng nhìn 90° theo chiều kim đồng hồ
        BlockFace look = face.getOppositeFace();
        BlockFace right = switch (look) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            default -> BlockFace.NORTH;
        };
        int w = img.getWidth() / 128, h = img.getHeight() / 128;
        // kiểm tra trước: tường đủ khối đặc, chỗ treo trống
        for (int cx = 0; cx < w; cx++) {
            for (int cy = 0; cy < h; cy++) {
                Block wall = base.getRelative(right, cx).getRelative(BlockFace.UP, h - 1 - cy);
                if (!wall.getType().isSolid() || !wall.getRelative(face).getType().isAir()) {
                    s.sendMessage(Tip.c("&cTường không đủ chỗ cho bảng " + w + "x" + h + " (cần tường đặc, phía trước trống) ở "
                            + wall.getX() + " " + wall.getY() + " " + wall.getZ()));
                    return;
                }
            }
        }
        String name = args[1];
        for (int cx = 0; cx < w; cx++) {
            for (int cy = 0; cy < h; cy++) {
                Block wall = base.getRelative(right, cx).getRelative(BlockFace.UP, h - 1 - cy);
                MapView view = Bukkit.createMap(p.getWorld());
                attach(view, img, cx, cy);
                data.set("maps." + view.getId(), name + ":" + cx + ":" + cy);
                ItemStack map = new ItemStack(Material.FILLED_MAP);
                MapMeta mm = (MapMeta) map.getItemMeta();
                mm.setMapView(view);
                map.setItemMeta(mm);
                p.getWorld().spawn(wall.getRelative(face).getLocation(), GlowItemFrame.class, fr -> {
                    fr.setFacingDirection(face, true);
                    fr.setItem(map, false);
                    fr.setFixed(true);
                    fr.setInvulnerable(true);
                    fr.setVisible(false);
                    fr.setPersistent(true);
                });
            }
        }
        save();
        s.sendMessage(Tip.c("&a✔ Đã treo bảng &f" + name + " &a(" + w + "x" + h + " khung). Gỡ: /dmm bangtranh xoa " + name));
    }

    private int remove(Player p, String name) {
        int n = 0;
        for (ItemFrame fr : p.getWorld().getNearbyEntitiesByType(ItemFrame.class, p.getLocation(), 16)) {
            ItemStack it = fr.getItem();
            if (it.getType() != Material.FILLED_MAP || !(it.getItemMeta() instanceof MapMeta mm) || mm.getMapView() == null) {
                continue;
            }
            String key = "maps." + mm.getMapView().getId();
            if (data.getString(key, "").startsWith(name + ":")) {
                data.set(key, null);
                fr.remove();
                n++;
            }
        }
        save();
        return n;
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Bảng tranh: không lưu được bangtranh.yml: " + e);
        }
    }
}
