package vn.duckymomo.core.paper;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * NPC chọn chế độ ở sảnh (30/10, chủ server gửi ảnh: hàng NPC có skin, trên đầu tên chế độ, phiên bản, gói tài nguyên, hỗ trợ BE,
 * số online, hạt dưới chân; bấm là vào server). NPC = thực thể Mannequin (hình người có skin, không cần Citizens), chữ = TextDisplay,
 * số online hỏi proxy (BungeeCord PlayerCount) mỗi 5 giây. Chế độ đọc từ modes/*\/mode.yml (GameMode): đóng -> "Sắp mở" (bấm không
 * vào), beta -> chỉ người có quyền thử. Skin lấy theo skinId trong plugins/SkinsRestorer/recommendations.json (đã ký sẵn).
 * Đặt: đứng chỗ muốn rồi /dmm npcsanh <mã chế độ> (NPC quay mặt theo hướng nhìn), gỡ: /dmm npcsanh xoa <mã>. Lưu: npc-sanh.yml.
 * Config lobby-npcs: {enabled, particle, modes.<id>{skin, version, pack, color}}
 */
final class LobbyNpcs implements Listener, PluginMessageListener {
    private final JavaPlugin plugin;
    private final ConfigurationSection cfg;
    private final File modesDir;
    private final NamespacedKey tag;
    private final File file;
    private final YamlConfiguration data;
    private final Map<String, Integer> counts = new HashMap<>();
    /** Mã thực thể hiện tại (NPC lưu vào thế giới; vùng nhả rồi nạp lại thì là đối tượng mới nên tra theo UUID). */
    private final Map<String, UUID> npcs = new HashMap<>();
    private final Map<String, UUID> labels = new HashMap<>();
    private BukkitTask tick;
    private BukkitTask fx;

    LobbyNpcs(JavaPlugin plugin, ConfigurationSection cfg, File modesDir) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.modesDir = modesDir;
        this.tag = new NamespacedKey(plugin, "npc_sanh");
        this.file = new File(plugin.getDataFolder(), "npc-sanh.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    void start() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, "BungeeCord", this);
        Bukkit.getScheduler().runTaskLater(plugin, this::spawnAll, 40L);
        tick = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 60L, 100L);
        Particle part = Particle.SOUL_FIRE_FLAME;
        try {
            part = Particle.valueOf(cfg.getString("particle", "SOUL_FIRE_FLAME").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            // giữ mặc định
        }
        Particle p = part;
        fx = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (UUID u : npcs.values()) {
                Entity m = Bukkit.getEntity(u);
                if (m != null && m.isValid() && !m.getWorld().getPlayers().isEmpty()) {
                    m.getWorld().spawnParticle(p, m.getLocation().add(0, 0.1, 0), 6, 0.45, 0.05, 0.45, 0.01);
                }
            }
        }, 40L, 8L);
    }

    void stop() {
        if (tick != null) {
            tick.cancel();
        }
        if (fx != null) {
            fx.cancel();
        }
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, "BungeeCord", this);
        npcs.clear();
        labels.clear();
    }

    private Map<String, GameMode> modes() {
        Map<String, GameMode> out = new HashMap<>();
        for (GameMode g : GameMode.loadAll(modesDir, plugin.getLogger())) {
            out.put(g.id(), g);
        }
        return out;
    }

    /** Skin đã ký theo skinId (SkinsRestorer recommendations.json); null nếu không có. */
    private ProfileProperty skin(String id) {
        File f = new File(plugin.getDataFolder().getParentFile(), "SkinsRestorer/recommendations.json");
        try {
            JsonArray arr = JsonParser.parseString(Files.readString(f.toPath(), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("skins");
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                if (o.get("skinId").getAsString().equalsIgnoreCase(id)) {
                    return new ProfileProperty("textures", o.get("value").getAsString(), o.get("signature").getAsString());
                }
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("NPC sảnh: không đọc được skin " + id + " (" + e.getMessage() + ")");
        }
        return null;
    }

    private void spawnAll() {
        Map<String, GameMode> ms = modes();
        for (String id : data.getKeys(false)) {
            Location loc = data.getLocation(id + ".loc");
            if (loc != null && loc.getWorld() != null) {
                spawn(id, loc, ms.get(id));
            }
        }
        refresh();
    }

    private void spawn(String id, Location loc, GameMode gm) {
        // dọn bản cũ (restart, đặt lại); bản nằm ở vùng chưa nạp sẽ bị onEntitiesLoad xoá khi vùng nạp
        loc.getChunk().load();
        for (Entity e : loc.getWorld().getNearbyEntities(loc, 3, 4, 3)) {
            if (id.equals(e.getPersistentDataContainer().get(tag, PersistentDataType.STRING))) {
                e.remove();
            }
        }
        ConfigurationSection mc = cfg.getConfigurationSection("modes." + id);
        ProfileProperty prop = skin(mc == null ? "" : mc.getString("skin", ""));
        Mannequin m = loc.getWorld().spawn(loc, Mannequin.class, e -> {
            e.setImmovable(true);
            e.setInvulnerable(true);
            e.setGravity(false);
            e.setSilent(true);
            e.setPersistent(true);
            e.setRemoveWhenFarAway(false);
            e.setDescription(Component.empty());
            if (prop != null) {
                e.setProfile(ResolvableProfile.resolvableProfile().name("DMM" + Math.abs(id.hashCode() % 1000))
                        .uuid(UUID.nameUUIDFromBytes(("duckymomo:npc:" + id).getBytes(StandardCharsets.UTF_8))).addProperty(prop).build());
            }
            e.getPersistentDataContainer().set(tag, PersistentDataType.STRING, id);
        });
        TextDisplay t = loc.getWorld().spawn(loc.clone().add(0, 2.35, 0), TextDisplay.class, e -> {
            e.setBillboard(Display.Billboard.CENTER);
            e.setPersistent(true);
            e.setShadowed(false);
            e.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
            e.getPersistentDataContainer().set(tag, PersistentDataType.STRING, id);
        });
        npcs.put(id, m.getUniqueId());
        labels.put(id, t.getUniqueId());
    }

    /** Hỏi proxy số người mỗi server (cần 1 người đang online ở sảnh để gửi tin) rồi vẽ lại chữ. */
    private void refresh() {
        Map<String, GameMode> ms = modes();
        Player any = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (any != null) {
            for (GameMode g : ms.values()) {
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("PlayerCount");
                out.writeUTF(g.server());
                any.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
            }
        }
        for (Map.Entry<String, UUID> e : labels.entrySet()) {
            GameMode g = ms.get(e.getKey());
            Entity t = Bukkit.getEntity(e.getValue());
            if (g == null || !(t instanceof TextDisplay td) || !td.isValid()) {
                continue;
            }
            td.text(Tip.c(text(e.getKey(), g)));
        }
    }

    private String text(String id, GameMode g) {
        ConfigurationSection mc = cfg.getConfigurationSection("modes." + id);
        String color = mc == null ? "&e" : mc.getString("color", "&e");
        String ver = mc == null ? "" : mc.getString("version", "");
        boolean pack = mc != null && mc.getBoolean("pack", false);
        String state;
        if (g.status().equals("closed") || g.status().equals("maintenance")) {
            state = g.status().equals("maintenance") ? "&6● Đang bảo trì" : "&c● Sắp mở";
        } else {
            int n = counts.getOrDefault(g.server(), 0);
            state = (g.status().equals("beta") ? "&e● Thử nghiệm &7· " : "&a● ") + "&f" + n + " &aonline";
        }
        return color + g.displayName().toUpperCase(Locale.forLanguageTag("vi")) + "\n"
                + (ver.isBlank() ? "" : "&f" + ver + "\n")
                + "&7&m              &r\n"
                + "&fGói tài nguyên: " + (pack ? "&a✔" : "&c✘") + "\n"
                + "&fHỗ trợ Bedrock: " + (g.supportsBedrock() ? "&a✔" : "&c✘") + "\n"
                + state;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("BungeeCord")) {
            return;
        }
        try {
            ByteArrayDataInput in = ByteStreams.newDataInput(message);
            if (in.readUTF().equals("PlayerCount")) {
                counts.put(in.readUTF(), in.readInt());
            }
        } catch (RuntimeException ignored) {
            // tin khác của kênh BungeeCord
        }
    }

    private String idOf(Entity e) {
        return e.getPersistentDataContainer().get(tag, PersistentDataType.STRING);
    }

    /** Vùng nạp lại: thực thể gắn nhãn NPC sảnh mà không phải bản hiện tại (bản cũ từ lần chạy trước) thì xoá. */
    @EventHandler
    public void onEntitiesLoad(org.bukkit.event.world.EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            String id = idOf(en);
            if (id != null && !en.getUniqueId().equals(npcs.get(id)) && !en.getUniqueId().equals(labels.get(id))) {
                en.remove();
            }
        }
    }

    @EventHandler
    public void onRightClick(PlayerInteractEntityEvent e) {
        String id = idOf(e.getRightClicked());
        if (id != null) {
            e.setCancelled(true);
            join(e.getPlayer(), id);
        }
    }

    @EventHandler
    public void onLeftClick(EntityDamageByEntityEvent e) {
        String id = idOf(e.getEntity());
        if (id != null) {
            e.setCancelled(true);
            if (e.getDamager() instanceof Player p) {
                join(p, id);
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (idOf(e.getEntity()) != null) {
            e.setCancelled(true);
        }
    }

    private void join(Player p, String id) {
        GameMode g = modes().get(id);
        if (g == null) {
            return;
        }
        if (g.status().equals("closed") || g.status().equals("maintenance")) {
            p.sendMessage(Tip.c("&e" + g.displayName() + " &7" + (g.status().equals("maintenance") ? "đang bảo trì, quay lại sau nhé." : "sắp mở, chờ thông báo trên Discord nhé!")));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            return;
        }
        if (g.status().equals("beta") && !g.betaPermission().isBlank() && !p.hasPermission(g.betaPermission())) {
            p.sendMessage(Tip.c("&e" + g.displayName() + " &7đang thử nghiệm, chưa mở cho mọi người."));
            return;
        }
        p.sendMessage(Tip.c("&a➜ &fĐang chuyển tới &e" + g.displayName() + "&f…"));
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(g.server());
        p.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
    }

    List<String> ids() {
        return modes().keySet().stream().sorted().toList();
    }

    /** /dmm npcsanh <mã> | xoa <mã> */
    void command(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Vào game, đứng chỗ muốn đặt rồi /dmm npcsanh <mã chế độ>");
            return;
        }
        Map<String, GameMode> ms = modes();
        if (a.length >= 3 && a[1].equalsIgnoreCase("xoa")) {
            String id = a[2].toLowerCase(Locale.ROOT);
            data.set(id, null);
            save();
            for (Map<String, UUID> m : List.of(npcs, labels)) {
                UUID u = m.remove(id);
                Entity e = u == null ? null : Bukkit.getEntity(u);
                if (e != null) {
                    e.remove();
                }
            }
            p.sendMessage(Tip.c("&7Đã gỡ NPC " + id + "."));
            return;
        }
        String id = a.length >= 2 ? a[1].toLowerCase(Locale.ROOT) : "";
        if (!ms.containsKey(id)) {
            p.sendMessage(Tip.c("&e/dmm npcsanh <" + String.join("|", ids()) + "> &7đứng chỗ muốn đặt · &e/dmm npcsanh xoa <mã>"));
            return;
        }
        Location loc = p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
        loc.setYaw(p.getLocation().getYaw());
        loc.setPitch(0);
        data.set(id + ".loc", loc);
        save();
        spawn(id, loc, ms.get(id));
        refresh();
        p.sendMessage(Tip.c("&a✔ Đã đặt NPC &e" + ms.get(id).displayName() + "&a. Gỡ: /dmm npcsanh xoa " + id));
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("NPC sảnh: không lưu được npc-sanh.yml: " + e);
        }
    }
}
