package vn.duckymomo.core.paper;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Vòng quay miễn phí (/vongquay). Vé KHÔNG bán, không trao đổi được: mỗi minutes-per-key phút CHƠI THẬT (Activity, không tính treo
 * máy) được 1 vé, giữ tối đa max-keys. Menu hiện mọi phần thưởng kèm TỈ LỆ (weight / tổng). Quà theo dạng Rewards; rare: báo cả server.
 * Placeholder (PlaceholderAPI): %dmmvq_ve% số vé, %dmmvq_con% phút tới vé tiếp theo. Lưu: plugins/DuckyMoMoCore/vongquay.yml
 */
final class LuckySpin implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final int SPIN_SLOT = 49;

    private record Prize(String label, List<String> rewards, int weight, boolean rare, Material icon) { }

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        boolean spinning;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final JavaPlugin plugin;
    private final ConfigurationSection cfg;
    private final Rewards rewards;
    private final Activity activity;
    private final ItemLibrary library;
    private final List<Prize> prizes = new ArrayList<>();
    private final int total;
    private final File file;
    private final YamlConfiguration data;
    private boolean dirty;

    LuckySpin(JavaPlugin plugin, ConfigurationSection cfg, Rewards rewards, Activity activity, ItemLibrary library) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.rewards = rewards;
        this.activity = activity;
        this.library = library;
        this.file = new File(plugin.getDataFolder(), "vongquay.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        int sum = 0;
        for (Map<?, ?> m : cfg.getMapList("prizes")) {
            int w = m.get("weight") instanceof Number n ? n.intValue() : 0;
            Object r = m.get("rewards");
            List<String> rw = new ArrayList<>();
            if (r instanceof List<?> l) {
                for (Object o : l) {
                    rw.add(String.valueOf(o));
                }
            }
            if (w <= 0 || rw.isEmpty()) {
                continue;
            }
            Material icon = m.get("icon") == null ? null : Material.matchMaterial(String.valueOf(m.get("icon")));
            prizes.add(new Prize(String.valueOf(m.get("label") == null ? rw.get(0) : m.get("label")), rw, w,
                    Boolean.TRUE.equals(m.get("rare")), icon));
            sum += w;
        }
        this.total = sum;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickMinute, 1200L, 1200L);
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    int minutesPerKey() {
        return Math.max(1, cfg.getInt("minutes-per-key", 60));
    }

    /** Rank (vd Media) nhận vé nhanh hơn: quyền duckymomo.vongquay.nhanh.<5..50> (% bớt thời gian, cao nhất) — Danh phận cấp theo config. */
    int minutesPerKey(OfflinePlayer o) {
        Player p = o == null ? null : o.getPlayer();
        int base = minutesPerKey();
        if (p == null) {
            return base;
        }
        for (int pct = 50; pct >= 5; pct -= 5) {
            if (p.hasPermission("duckymomo.vongquay.nhanh." + pct)) {
                return Math.max(1, (int) Math.round(base * (100 - pct) / 100.0));
            }
        }
        return base;
    }

    int keys(OfflinePlayer p) {
        return data.getInt(p.getUniqueId() + ".ve", 0);
    }

    int minutesToNext(OfflinePlayer p) {
        return Math.max(0, minutesPerKey(p) - data.getInt(p.getUniqueId() + ".phut", 0));
    }

    private void tickMinute() {
        int max = cfg.getInt("max-keys", 5);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (activity != null && !activity.activeNow(p.getUniqueId())) {
                continue;
            }
            String k = p.getUniqueId().toString();
            int ve = data.getInt(k + ".ve", 0);
            if (ve >= max) {
                continue; // đầy vé: không tích thêm phút
            }
            int phut = data.getInt(k + ".phut", 0) + 1;
            if (phut >= minutesPerKey(p)) {
                phut = 0;
                data.set(k + ".ve", ve + 1);
                p.sendMessage(c("&6🔑 Bạn nhận 1 vé vòng quay (chơi đủ " + minutesPerKey(p) + " phút). Gõ &f/vongquay &6để quay!"));
            }
            data.set(k + ".phut", phut);
            data.set(k + ".ten", p.getName());
            dirty = true;
        }
        flush();
    }

    void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được vongquay.yml: " + e.getMessage());
        }
    }

    private ItemStack icon(Prize pz) {
        ItemStack it = null;
        String first = pz.rewards().get(0).trim();
        String[] a = first.split("\\s+");
        if (pz.icon() != null) {
            it = new ItemStack(pz.icon());
        } else if (a[0].equalsIgnoreCase("do") && a.length > 1) {
            it = library.build(a[1], 1);
        } else if (a[0].equalsIgnoreCase("vat") && a.length > 1) {
            Material m = Material.matchMaterial(a[1]);
            it = m == null ? null : new ItemStack(m, Math.min(64, a.length > 2 ? Math.max(1, Integer.parseInt(a[2])) : 1));
        } else if (a[0].equalsIgnoreCase("xu")) {
            it = new ItemStack(Material.GOLD_INGOT);
        } else if (a[0].equalsIgnoreCase("manh")) {
            it = new ItemStack(Material.AMETHYST_SHARD);
        }
        if (it == null) {
            it = new ItemStack(Material.PAPER);
        }
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c((pz.rare() ? "&6✦ " : "&e") + pz.label()));
        List<Component> lore = new ArrayList<>();
        lore.add(c(String.format(Locale.ROOT, "&7Tỉ lệ: &f%.1f%%", 100.0 * pz.weight() / total)));
        for (String d : rewards.describe(pz.rewards())) {
            lore.add(c("&8• &7" + d));
        }
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack spinButton(Player p) {
        ItemStack it = new ItemStack(keys(p) > 0 ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(keys(p) > 0 ? "&aQUAY (&f" + keys(p) + " vé&a)" : "&7Hết vé"));
        meta.lore(List.of(c("&7Chơi thật &f" + minutesPerKey(p) + " phút &7= 1 vé (tối đa " + cfg.getInt("max-keys", 5) + ")"),
                c("&7Vé tiếp theo sau &f" + minutesToNext(p) + " phút"), c("&8Vé miễn phí, không bán, không trao đổi")));
        it.setItemMeta(meta);
        return it;
    }

    void open(Player p) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 54, c("&6VÒNG QUAY MIỄN PHÍ"));
        int slot = 10;
        for (Prize pz : prizes) {
            if (slot >= 44) {
                break;
            }
            h.inv.setItem(slot, icon(pz));
            slot += (slot % 9 == 7) ? 3 : 1;
        }
        h.inv.setItem(SPIN_SLOT, spinButton(p));
        p.openInventory(h.inv);
    }

    private Prize pick() {
        int r = ThreadLocalRandom.current().nextInt(total);
        for (Prize pz : prizes) {
            r -= pz.weight();
            if (r < 0) {
                return pz;
            }
        }
        return prizes.get(prizes.size() - 1);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getRawSlot() != SPIN_SLOT || h.spinning) {
            return;
        }
        String k = p.getUniqueId().toString();
        int ve = data.getInt(k + ".ve", 0);
        if (ve <= 0 || total <= 0) {
            p.sendMessage(c("&cHết vé. Chơi thêm &f" + minutesToNext(p) + " phút &cđể có vé mới."));
            return;
        }
        data.set(k + ".ve", ve - 1);
        data.set(k + ".da-quay", data.getInt(k + ".da-quay") + 1);
        dirty = true;
        flush();
        Prize win = pick();
        List<String> got = rewards.give(p, win.rewards()); // phát ngay; hiệu ứng chỉ để xem
        plugin.getLogger().info("Vòng quay: " + p.getName() + " trúng " + win.label());
        h.spinning = true;
        final int[] step = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            step[0]++;
            if (step[0] < 16) {
                h.inv.setItem(SPIN_SLOT, icon(prizes.get(ThreadLocalRandom.current().nextInt(prizes.size()))));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.0f + step[0] * 0.05f);
                return;
            }
            task.cancel();
            h.spinning = false;
            h.inv.setItem(SPIN_SLOT, icon(win));
            p.playSound(p.getLocation(), win.rare() ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1f);
            p.sendMessage(c("&a🎉 Vòng quay: bạn trúng &f" + win.label() + (got.isEmpty() ? "" : " &7(" + String.join("&7, ", got) + "&7)")));
            if (win.rare()) {
                Bukkit.broadcast(c("&6✦ &f" + p.getName() + " &6quay trúng &f" + win.label() + "&6! Chơi đủ giờ để có vé miễn phí: &f/vongquay"));
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.getOpenInventory().getTopInventory().getHolder() == h) {
                    h.inv.setItem(SPIN_SLOT, spinButton(p));
                }
            }, 40L);
        }, 2L, 2L);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    boolean command(CommandSender s, String[] args) {
        if (args.length >= 3 && args[0].equalsIgnoreCase("give") && s.hasPermission("duckymomo.admin")) {
            OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(args[1]);
            if (t == null) {
                s.sendMessage("Không tìm thấy người chơi.");
                return true;
            }
            int n;
            try {
                n = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                s.sendMessage("Số vé không hợp lệ.");
                return true;
            }
            data.set(t.getUniqueId() + ".ve", Math.max(0, keys(t) + n));
            dirty = true;
            flush();
            s.sendMessage("Đã " + (n >= 0 ? "tặng " : "trừ ") + Math.abs(n) + " vé cho " + t.getName() + " (còn " + keys(t) + ").");
            return true;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("/vongquay give <người> <số vé>");
            return true;
        }
        if (total <= 0) {
            p.sendMessage(c("&cVòng quay chưa có phần thưởng."));
            return true;
        }
        open(p);
        return true;
    }

    /** %dmmvq_ve%, %dmmvq_con% */
    PlaceholderExpansion placeholders() {
        return new PlaceholderExpansion() {
            @Override
            public String getIdentifier() {
                return "dmmvq";
            }

            @Override
            public String getAuthor() {
                return "DuckyMoMo";
            }

            @Override
            public String getVersion() {
                return "1";
            }

            @Override
            public boolean persist() {
                return true;
            }

            @Override
            public String onRequest(OfflinePlayer player, String params) {
                if (player == null) {
                    return "";
                }
                return switch (params) {
                    case "ve" -> String.valueOf(keys(player));
                    case "con" -> keys(player) >= cfg.getInt("max-keys", 5) ? "đầy" : minutesToNext(player) + "p";
                    default -> null;
                };
            }
        };
    }
}
