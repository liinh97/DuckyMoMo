package vn.duckymomo.core.paper;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Cửa hàng Kinh Đô (14/10, làm lại 20/10 theo kiểu "chợ server"): trang chính chia DANH MỤC (decor-shop.categories: id, name, icon, slot,
 * desc, sell [mẫu vật liệu]); trong danh mục mỗi món ghi giá mua (server bán) và giá bán lại (Essentials worth.yml, /sell), số đang có.
 * Chuột trái: mua 1 lô · Chuột phải: bán 1 lô · Shift + phải: bán hết loại đó (bán qua lệnh /sell của Essentials nên giữ thưởng kinh đô,
 * quỹ khai hoang…). Món chỉ thu mua (không bán) lấy tự động từ worth.yml theo mẫu "sell" của danh mục (vd "*_LOG", "DIAMOND").
 * Món server bán nằm TRONG danh mục: categories[].items [{material | library, amount, price}] (20/10, Cài đặt sửa theo từng danh mục);
 * materials / items cũ (có "cat:") vẫn đọc được. titles = danh mục danhhieu. Trả bằng xu, không bán gì tăng sức mạnh.
 *  - Mọi người: bấm NPC cửa hàng ở khu shop tại spawn (admin đặt bằng /dmm shopnpc).
 *  - Rank ủng hộ (quyền rank-permission): gõ /cuahang, nhưng CHỈ khi đang đứng trong kinh đô (không mua từ xa được).
 *  - Vật liệu: chỉ server bán, không mua lại; món nào giá mỗi cái <= giá thu mua /sell (Essentials worth.yml) thì tự bỏ (chống lời vô hạn).
 * Danh sách trong config decor-shop: materials, items (trang trí), titles (mã, chữ, giá). Danh hiệu dùng qua /danhhieu.
 */
final class DecorShop implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    /** Danh mục: sell = mẫu vật liệu chỉ thu mua (lấy giá từ worth.yml), "*" ở đầu/cuối = khớp đuôi/đầu tên. */
    record Cat(String id, String name, Material icon, int slot, String desc, List<String> sell) { }

    private final Map<String, Cat> cats = new LinkedHashMap<>();

    /** Đồ ĐẶC THÙ không bao giờ bán theo mẫu (*): quặng, kim loại, đá quý, đồ hiếm, đồ admin, vũ khí/giáp/công cụ… (thêm: decor-shop.khong-ban). */
    static final List<String> DENY = List.of("*_ORE", "RAW_*", "*_INGOT", "*_NUGGET", "*DIAMOND*", "*EMERALD*", "*NETHERITE*", "*GOLD*", "*IRON*",
            "*COPPER*", "*LAPIS*", "*REDSTONE*", "*QUARTZ*", "*AMETHYST*", "ANCIENT_DEBRIS", "*_SPAWN_EGG", "SPAWNER", "TRIAL_SPAWNER", "VAULT",
            "*COMMAND*", "BARRIER", "STRUCTURE_*", "JIGSAW", "LIGHT", "DEBUG_STICK", "BEDROCK", "END_PORTAL_FRAME", "REINFORCED_DEEPSLATE",
            "ELYTRA", "TOTEM_OF_UNDYING", "BEACON", "NETHER_STAR", "DRAGON_*", "*SHULKER*", "ENCHANTED_*", "*_SMITHING_TEMPLATE", "HEAVY_CORE",
            "*TRIAL_KEY", "OMINOUS_*", "CONDUIT", "HEART_OF_THE_SEA", "*_HEAD", "*_SKULL", "PETRIFIED_OAK_SLAB", "KNOWLEDGE_BOOK", "INFESTED_*",
            "*SCULK*", "CALIBRATED_*", "*_SWORD", "*_AXE", "*_PICKAXE", "*_SHOVEL", "*_HOE", "*_HELMET", "*_CHESTPLATE", "*_LEGGINGS", "*_BOOTS",
            "BOW", "CROSSBOW", "TRIDENT", "MACE", "SHIELD", "*POTION*", "*_BUCKET", "TNT*", "END_CRYSTAL", "RESPAWN_ANCHOR", "LODESTONE",
            "CRYING_OBSIDIAN", "CHORUS_*", "FROGSPAWN", "*_POTTERY_SHERD", "WITHER_*", "ECHO_SHARD", "RECOVERY_COMPASS", "*_BANNER_PATTERN", "MUSIC_DISC_*",
            "DISC_FRAGMENT_*", "GOAT_HORN", "SPYGLASS", "CLOCK", "COMPASS", "*_MINECART", "*RAIL", "POWERED_RAIL", "BUDDING_AMETHYST");

    /** lib: mã trong Thư viện đồ (bán đồ admin thiết kế), null = đồ thường theo icon. */
    record Offer(String key, String cat, Material icon, int amount, double price, String title, String titleText, String lib) {
    }

    private final DuckyMoMoCore plugin;
    private final Leaderboard board;
    private final Predicate<Location> inCapital;
    private final String rankPermission;
    private final String npcName;
    private final List<Offer> offers = new ArrayList<>();
    private final NamespacedKey offerKey;
    private final NamespacedKey catKey;
    private final NamespacedKey npcKey;
    private final NamespacedKey sellKey;
    private final ItemLibrary library;

    DecorShop(DuckyMoMoCore plugin, Leaderboard board, Predicate<Location> inCapital, ConfigurationSection c, ItemLibrary library) {
        this.plugin = plugin;
        this.library = library;
        this.board = board;
        this.inCapital = inCapital;
        this.rankPermission = c.getString("rank-permission", "duckymomo.cuahang");
        this.npcName = c.getString("npc-name", "&6Cửa hàng Kinh Đô");
        this.offerKey = new NamespacedKey(plugin, "cuahang");
        this.catKey = new NamespacedKey(plugin, "cuahang_muc");
        this.npcKey = new NamespacedKey(plugin, "shop_npc");
        this.sellKey = new NamespacedKey(plugin, "cuahang_ban");
        for (Map<?, ?> m : c.getMapList("categories")) {
            String id = String.valueOf(m.get("id"));
            Material ic = Material.matchMaterial(String.valueOf(m.get("icon")));
            List<String> sell = new ArrayList<>();
            if (m.get("sell") instanceof List<?> l) {
                l.forEach(x -> sell.add(String.valueOf(x).toUpperCase(Locale.ROOT)));
            }
            cats.put(id, new Cat(id, String.valueOf(m.get("name") == null ? id : m.get("name")), ic == null ? Material.CHEST : ic,
                    m.get("slot") instanceof Number n ? n.intValue() : -1, m.get("desc") == null ? "" : String.valueOf(m.get("desc")), sell));
        }
        if (cats.isEmpty()) {   // cấu hình cũ chưa có danh mục
            cats.put("vatlieu", new Cat("vatlieu", "Vật liệu cơ bản", Material.BRICKS, 11, "", List.of()));
            cats.put("trangtri", new Cat("trangtri", "Trang trí", Material.LANTERN, 13, "", List.of()));
            cats.put("danhhieu", new Cat("danhhieu", "Danh hiệu", Material.NAME_TAG, 15, "", List.of()));
        }
        YamlConfiguration worth = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder().getParentFile(), "Essentials/worth.yml"));
        int skipped = 0;
        List<Map<?, ?>> buyList = new ArrayList<>();
        for (Map<?, ?> cm : c.getMapList("categories")) {
            if (cm.get("items") instanceof List<?> l) {
                for (Object x : l) {
                    if (x instanceof Map<?, ?> xm) {
                        Map<Object, Object> copy = new LinkedHashMap<>(xm);
                        copy.put("cat", String.valueOf(cm.get("id")));
                        buyList.add(copy);
                    }
                }
            }
        }
        buyList.addAll(c.getMapList("materials"));
        List<String> deny = new ArrayList<>(DENY);
        c.getStringList("khong-ban").forEach(x -> deny.add(x.toUpperCase(Locale.ROOT)));
        java.util.Set<Material> listed = new java.util.HashSet<>();
        List<Offer> expanded = new ArrayList<>();
        for (Map<?, ?> m : buyList) {
            String mat = String.valueOf(m.get("material")).toUpperCase(Locale.ROOT);
            if (m.get("library") == null && mat.contains("*")) {
                // mẫu (vd *_WOOL): mọi vật phẩm khớp, trừ đồ đặc thù (DENY + khong-ban) và món đã có dòng riêng
                for (Material x : Material.values()) {
                    if (x.isItem() && !x.isAir() && !x.isLegacy() && matches(mat, x) && deny.stream().noneMatch(d -> matches(d, x)) && !listed.contains(x)) {
                        Map<Object, Object> one = new LinkedHashMap<>(m);
                        one.put("material", x.name());
                        Offer o = item(one, "vatlieu");
                        if (o != null) {
                            expanded.add(o);
                            listed.add(x);
                        }
                    }
                }
                continue;
            }
            Offer single = item(m, "vatlieu");
            if (single != null) {
                if (single.lib() == null) {
                    listed.add(single.icon());
                }
                expanded.add(single);
            }
        }
        for (Offer o : expanded) {
            double sell = o.lib() != null ? 0 : worth.getDouble("worth." + o.icon().name().toLowerCase(Locale.ROOT), 0);
            if (sell > 0 && o.price() / o.amount() <= sell) {
                plugin.getLogger().warning("Cửa hàng: bỏ " + o.icon() + " vì giá bán (" + o.price() / o.amount() + "/cái) <= giá thu mua /sell (" + sell + ")");
                skipped++;
                continue;
            }
            offers.add(o);
        }
        for (Map<?, ?> m : c.getMapList("items")) {   // cấu hình cũ: đồ trang trí không kiểm giá /sell
            Offer o = item(m, "trangtri");
            if (o != null) {
                offers.add(o);
            }
        }
        for (Map<?, ?> m : c.getMapList("titles")) {
            offers.add(new Offer("t" + offers.size(), m.get("cat") == null ? "danhhieu" : String.valueOf(m.get("cat")), Material.NAME_TAG, 1,
                    m.get("price") instanceof Number n ? n.doubleValue() : 0, String.valueOf(m.get("id")), String.valueOf(m.get("text")), null));
        }
        plugin.getLogger().info("Cửa hàng Kinh Đô: " + offers.size() + " món" + (skipped > 0 ? " (bỏ " + skipped + " món rẻ hơn giá /sell)" : ""));
    }

    private Offer item(Map<?, ?> m, String defCat) {
        String cat = m.get("cat") == null ? defCat : String.valueOf(m.get("cat"));
        int amount = m.get("amount") instanceof Number n ? Math.max(1, n.intValue()) : 1;
        double price = m.get("price") instanceof Number n ? n.doubleValue() : 0;
        String key = (defCat.equals("vatlieu") ? "v" : "i") + offers.size();
        if (m.get("library") != null) {
            String id = String.valueOf(m.get("library"));
            ItemStack sample = library == null ? null : library.build(id, 1);
            if (sample == null) {
                plugin.getLogger().warning("Cửa hàng: bỏ món thư viện \"" + id + "\" (không có trong Thư viện đồ)");
                return null;
            }
            return new Offer(key, cat, sample.getType(), amount, price, null, null, id);
        }
        Material mat = Material.matchMaterial(String.valueOf(m.get("material")));
        if (mat == null || !mat.isItem()) {
            return null;
        }
        return new Offer(key, cat, mat, amount, price, null, null, null);
    }

    private static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    private String money(double v) {
        return VaultEconomy.available() ? VaultEconomy.format(v) : String.format(Locale.ROOT, "%.0f xu", v);
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inv;
        private String cat;   // null = trang chính
        private int page;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    // ------------------------------------------------------------------ mở cửa hàng

    /** /cuahang: rank ủng hộ, phải đứng trong kinh đô. */
    boolean command(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            return true;
        }
        if (!p.hasPermission(rankPermission)) {
            p.sendMessage(c("&eCửa hàng Kinh Đô: tới &fNPC cửa hàng ở khu shop tại spawn&e để mua. Rank ủng hộ mở được bằng &f/cuahang &ekhi đứng trong kinh đô."));
            return true;
        }
        if (!inCapital.test(p.getLocation())) {
            p.sendMessage(c("&eVề kinh đô (spawn) mới mở được cửa hàng."));
            return true;
        }
        openMenu(p);
        return true;
    }

    private static String catOf(Offer o, Map<String, Cat> cats) {
        if (cats.containsKey(o.cat())) {
            return o.cat();
        }
        return cats.keySet().iterator().next();
    }

    /** Giá thu mua /sell (Essentials worth.yml), đọc lại mỗi lần mở để Cài đặt sửa giá là thấy ngay. */
    private Map<Material, Double> worth() {
        Map<Material, Double> out = new LinkedHashMap<>();
        YamlConfiguration w = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder().getParentFile(), "Essentials/worth.yml"));
        ConfigurationSection sec = w.getConfigurationSection("worth");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                Material m = Material.matchMaterial(k);
                if (m != null && sec.getDouble(k) > 0) {
                    out.put(m, sec.getDouble(k));
                }
            }
        }
        return out;
    }

    private static boolean matches(String pattern, Material m) {
        String n = m.name();
        if (pattern.length() > 1 && pattern.startsWith("*") && pattern.endsWith("*")) {
            return n.contains(pattern.substring(1, pattern.length() - 1));
        }
        if (pattern.startsWith("*")) {
            return n.endsWith(pattern.substring(1));
        }
        if (pattern.endsWith("*")) {
            return n.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return n.equals(pattern);
    }

    /** Một ô trong danh mục: món server bán (offer) và/hoặc vật liệu server thu mua (sellMat). */
    private record Entry(Offer offer, Material sellMat) { }

    private List<Entry> entries(String cat, Map<Material, Double> worth) {
        List<Entry> out = new ArrayList<>();
        java.util.Set<Material> seen = new java.util.HashSet<>();
        for (Offer o : offers) {
            if (catOf(o, cats).equals(cat)) {
                Material sm = o.lib() == null && o.title() == null && worth.containsKey(o.icon()) ? o.icon() : null;
                out.add(new Entry(o, sm));
                if (sm != null) {
                    seen.add(sm);
                }
            }
        }
        Cat c = cats.get(cat);
        if (c != null) {
            for (Material m : worth.keySet()) {
                if (!seen.contains(m) && c.sell().stream().anyMatch(pt -> matches(pt, m))) {
                    out.add(new Entry(null, m));
                    seen.add(m);
                }
            }
        }
        return out;
    }

    private static int count(Player p, Material m) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && it.getType() == m && !it.hasItemMeta()) {
                n += it.getAmount();
            }
        }
        return n;
    }

    private void openMenu(Player p) {
        if (BedrockMenu.available() && BedrockMenu.isBedrock(p.getUniqueId())) {
            List<BedrockMenu.Entry> es = new ArrayList<>();
            cats.forEach((k, v) -> es.add(new BedrockMenu.Entry(v.name(), k)));
            BedrockMenu.open(p.getUniqueId(), "Cửa hàng Kinh Đô", "Trả bằng xu trong game. Chọn mục:", es,
                    k -> plugin.getServer().getScheduler().runTask(plugin, () -> openCategory(p, k, 0)));
            return;
        }
        Holder h = new Holder();
        Inventory inv = plugin.getServer().createInventory(h, 54, c("&0⛃ Cửa hàng Kinh Đô"));
        h.inv = inv;
        Map<Material, Double> worth = worth();
        inv.setItem(4, Tip.of("&6⛃ &fCửa hàng Kinh Đô").desc("Mua vật liệu, đồ trang trí, danh hiệu", "Bán nông sản, khoáng sản, đồ từ quái")
                .line().kv("Số dư", "&a" + money(VaultEconomy.available() ? VaultEconomy.balance(p) : 0))
                .note("Trả bằng xu trong game, không bán gì tăng sức mạnh").apply(new ItemStack(Material.GOLD_INGOT)));
        int[] auto = {11, 13, 15, 20, 22, 24, 29, 31, 33, 38, 40, 42};
        int ai = 0;
        for (Cat ct : cats.values()) {
            List<Entry> es = entries(ct.id(), worth);
            long buyN = es.stream().filter(x -> x.offer() != null).count(), sellN = es.stream().filter(x -> x.sellMat() != null).count();
            Tip t = Tip.of("&e" + ct.name());
            if (!ct.desc().isBlank()) {
                t.raw("&8↳ &7" + ct.desc());
            }
            t.line();
            if (buyN > 0) {
                t.kv("Server bán", buyN + " món");
            }
            if (sellN > 0) {
                t.kv("Server thu mua", sellN + " món");
            }
            t.click("Bấm để xem");
            ItemStack it = t.apply(new ItemStack(ct.icon()));
            ItemMeta m = it.getItemMeta();
            m.getPersistentDataContainer().set(catKey, PersistentDataType.STRING, ct.id());
            it.setItemMeta(m);
            int slot = ct.slot() >= 0 && ct.slot() < 45 && inv.getItem(ct.slot()) == null ? ct.slot() : -1;
            while (slot < 0 && ai < auto.length) {
                if (inv.getItem(auto[ai]) == null) {
                    slot = auto[ai];
                }
                ai++;
            }
            if (slot >= 0) {
                inv.setItem(slot, it);
            }
        }
        ItemStack close = Tip.of("&c✘ &fĐóng").apply(new ItemStack(Material.BARRIER));
        ItemMeta cm = close.getItemMeta();
        cm.getPersistentDataContainer().set(catKey, PersistentDataType.STRING, "close");
        close.setItemMeta(cm);
        inv.setItem(49, close);
        p.openInventory(inv);
    }

    private void openCategory(Player p, String cat, int page) {
        Cat ct = cats.get(cat);
        if (ct == null) {
            return;
        }
        Map<Material, Double> worth = worth();
        List<Entry> list = entries(cat, worth);
        if (BedrockMenu.available() && BedrockMenu.isBedrock(p.getUniqueId())) {
            List<BedrockMenu.Entry> es = new ArrayList<>();
            for (Entry en : list) {
                if (en.offer() != null) {
                    es.add(new BedrockMenu.Entry(label(en.offer()).replaceAll("&.", "") + "\n" + money(en.offer().price()), en.offer().key()));
                }
            }
            BedrockMenu.open(p.getUniqueId(), ct.name(), es.isEmpty() ? "Mục này chỉ thu mua: cầm đồ rồi gõ /sell hand." : "Bấm để mua:", es,
                    k -> plugin.getServer().getScheduler().runTask(plugin, () -> buy(p, k)));
            return;
        }
        int pages = Math.max(1, (list.size() + 44) / 45);
        page = Math.max(0, Math.min(page, pages - 1));
        Holder h = new Holder();
        h.cat = cat;
        h.page = page;
        Inventory inv = plugin.getServer().createInventory(h, 54, c("&0" + ct.name() + (pages > 1 ? " &8(" + (page + 1) + "/" + pages + ")" : "")));
        h.inv = inv;
        for (int i = 0; i < 45 && page * 45 + i < list.size(); i++) {
            inv.setItem(i, icon(p, list.get(page * 45 + i), worth));
        }
        if (page > 0) {
            inv.setItem(45, nav(Material.ARROW, "&e← &fTrang trước", "trang:" + (page - 1)));
        }
        inv.setItem(48, Tip.of("&6⛃ &fSố dư").kv("Xu", "&a" + money(VaultEconomy.available() ? VaultEconomy.balance(p) : 0)).apply(new ItemStack(Material.GOLD_NUGGET)));
        inv.setItem(49, nav(Material.PAPER, "&7← &fVề danh mục", "menu"));
        if (page < pages - 1) {
            inv.setItem(53, nav(Material.ARROW, "&fTrang sau &e→", "trang:" + (page + 1)));
        }
        p.openInventory(inv);
    }

    private ItemStack nav(Material m, String name, String action) {
        ItemStack it = Tip.of(name).apply(new ItemStack(m));
        ItemMeta im = it.getItemMeta();
        im.getPersistentDataContainer().set(catKey, PersistentDataType.STRING, action);
        it.setItemMeta(im);
        return it;
    }

    private ItemStack icon(Player p, Entry en, Map<Material, Double> worth) {
        Offer o = en.offer();
        ItemStack it = o != null && o.lib() != null ? library.build(o.lib(), 1) : null;
        if (it == null) {
            it = new ItemStack(o != null ? o.icon() : en.sellMat());
        }
        it.setAmount(Math.max(1, Math.min(o != null ? o.amount() : 1, it.getMaxStackSize())));
        Tip t = Tip.of(o != null ? label(o) : "&f" + Rewards.vi(en.sellMat().name())).accent("&6");
        if (o != null && o.title() != null) {
            t.desc("Danh hiệu hiện cạnh tên", "Đổi danh hiệu: /danhhieu");
        }
        t.line();
        if (o != null) {
            t.kv("Giá mua", "&a" + money(o.price()) + (o.title() == null ? " &8/ " + o.amount() + " món" : ""));
        }
        Material sm = en.sellMat();
        int have = sm == null ? 0 : count(p, sm);
        if (sm != null) {
            t.kv("Giá bán lại", "&e" + money(worth.get(sm)) + " &8/ món");
            t.kv("Bạn đang có", have > 0 ? "&f" + have : "&80");
        } else if (o != null && o.title() == null) {
            t.kv("Giá bán lại", "&8server không thu mua");
        }
        t.line();
        if (o != null) {
            t.click("Chuột trái", o.title() != null ? "mua danh hiệu" : "mua " + o.amount() + " món");
        }
        if (sm != null) {
            int lot = Math.min(have, o != null ? o.amount() : 64);
            if (have > 0) {
                t.click("Chuột phải", "bán " + lot + " món");
                t.click("Shift + phải", "bán hết " + have + " món");
            } else {
                t.note("Có món này trong túi thì bán được ở đây");
            }
        }
        if (o != null && o.title() == null && o.lib() == null) {
            t.note("Chỉ trang trí / vật liệu, không thêm sức mạnh");
        }
        if (o != null && o.lib() != null) {
            t.append(it);
        } else {
            t.apply(it);
        }
        ItemMeta m = it.getItemMeta();
        m.displayName(t.name());
        if (o != null) {
            m.getPersistentDataContainer().set(offerKey, PersistentDataType.STRING, o.key());
        }
        if (sm != null) {
            m.getPersistentDataContainer().set(sellKey, PersistentDataType.STRING, sm.name() + ":" + (o != null ? o.amount() : 64));
        }
        it.setItemMeta(m);
        return it;
    }

    /** Bán qua lệnh /sell của Essentials (giữ thưởng kinh đô, quỹ khai hoang, chống bán đồ có tên/phù phép). */
    private void sell(Player p, Material m, int lot, boolean all, Holder h) {
        int have = count(p, m);
        if (have <= 0) {
            p.sendMessage(c("&cTrong túi không có " + Rewards.vi(m.name()) + " (đồ thường) để bán."));
            return;
        }
        p.performCommand("sell " + m.name().toLowerCase(Locale.ROOT) + (all ? "" : " " + Math.min(lot, have)));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() == h) {
                openCategory(p, h.cat, h.page);
            }
        }, 2L);
    }

    private String label(Offer o) {
        if (o.title() != null) {
            return "Danh hiệu " + o.titleText();
        }
        if (o.lib() != null) {
            ItemStack s = library.build(o.lib(), 1);
            return "&f" + o.amount() + " × " + (s == null ? o.lib() : Rewards.nameOf(s));
        }
        return "&f" + o.amount() + " × " + Rewards.vi(o.icon().name());
    }

    private void buy(Player p, String key) {
        Offer o = offers.stream().filter(x -> x.key().equals(key)).findFirst().orElse(null);
        if (o == null) {
            return;
        }
        if (o.title() != null && board != null && !board.grantable(p.getUniqueId(), o.title())) {
            p.sendMessage(c("&eBạn đã có danh hiệu này."));
            return;
        }
        if (!VaultEconomy.available() || !VaultEconomy.withdraw(p, o.price())) {
            p.sendMessage(c("&cKhông đủ tiền (" + money(o.price()) + ")."));
            return;
        }
        if (o.title() != null) {
            board.grant(p.getUniqueId(), p.getName(), o.title());
        } else {
            int left = o.amount();
            while (left > 0) {
                ItemStack give = o.lib() != null ? library.build(o.lib(), 1) : new ItemStack(o.icon());
                if (give == null) {
                    VaultEconomy.deposit(p, o.price() * left / o.amount()); // món thư viện vừa bị xoá: hoàn tiền phần chưa giao
                    p.sendMessage(c("&cMón này không còn trong kho, đã hoàn tiền."));
                    break;
                }
                int n = Math.min(left, give.getMaxStackSize());
                give.setAmount(n);
                for (ItemStack rest : p.getInventory().addItem(give).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), rest);
                }
                left -= n;
            }
            p.sendMessage(c("&aĐã mua " + label(o) + " &avới " + money(o.price())));
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()
                || e.getClickedInventory() != e.getInventory()) {
            return;
        }
        var pdc = e.getCurrentItem().getItemMeta().getPersistentDataContainer();
        Holder h = (Holder) e.getInventory().getHolder();
        String cat = pdc.get(catKey, PersistentDataType.STRING);
        if (cat != null) {
            if (cat.equals("close")) {
                p.closeInventory();
            } else if (cat.equals("menu")) {
                openMenu(p);
            } else if (cat.startsWith("trang:")) {
                openCategory(p, h.cat, Integer.parseInt(cat.substring(6)));
            } else {
                openCategory(p, cat, 0);
            }
            return;
        }
        String key = pdc.get(offerKey, PersistentDataType.STRING);
        String sellTag = pdc.get(sellKey, PersistentDataType.STRING);
        org.bukkit.event.inventory.ClickType ct = e.getClick();
        if (sellTag != null && ct.isRightClick()) {
            String[] a = sellTag.split(":");
            Material m = Material.matchMaterial(a[0]);
            if (m != null) {
                sell(p, m, Integer.parseInt(a[1]), ct.isShiftClick(), h);
            }
            return;
        }
        if (key != null && ct.isLeftClick()) {
            buy(p, key);
            if (h.cat != null) {
                Holder hh = h;
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() == hh) {
                        openCategory(p, hh.cat, hh.page);
                    }
                }, 1L);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ NPC cửa hàng ở spawn

    /** /dmm shopnpc [remove]: đặt (hoặc xoá) NPC cửa hàng tại chỗ admin đứng. Đặt lại thì NPC cũ bị xoá. */
    void npcCommand(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Vào game, đứng ở khu shop tại spawn rồi gõ /dmm shopnpc");
            return;
        }
        int removed = 0;
        for (Entity e : p.getWorld().getEntities()) {
            if (e.getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE)) {
                e.remove();
                removed++;
            }
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("remove")) {
            p.sendMessage(c("&7Đã xoá " + removed + " thực thể NPC cửa hàng."));
            return;
        }
        Location loc = p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
        loc.setYaw(p.getLocation().getYaw());
        loc.setPitch(0);
        Villager v = p.getWorld().spawn(loc, Villager.class, e -> {
            e.customName(c(npcName));
            e.setCustomNameVisible(false);
            e.setAI(false);
            e.setSilent(true);
            e.setInvulnerable(true);
            e.setCollidable(false);
            e.setPersistent(true);
            e.setRemoveWhenFarAway(false);
            e.setProfession(Villager.Profession.MASON);
            e.setVillagerLevel(5);
            e.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte) 1);
        });
        if (!v.isValid()) {
            p.sendMessage(c("&cKhông đặt được dân làng ở đây (có thể WorldGuard chặn sinh mob)."));
            return;
        }
        String[] lines = {npcName, "&7Mua vật liệu · Bán nông sản, khoáng sản", "&8(bấm vào để mở)"};
        double y = 1.6 + (lines.length - 1) * 0.28;
        for (String line : lines) {
            p.getWorld().spawn(loc.clone().add(0, y, 0), ArmorStand.class, a -> {
                a.setMarker(true);
                a.setInvisible(true);
                a.setGravity(false);
                a.setInvulnerable(true);
                a.setPersistent(true);
                a.customName(c(line));
                a.setCustomNameVisible(true);
                a.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte) 2);
            });
            y -= 0.28;
        }
        p.sendMessage(c("&aĐã đặt NPC Cửa hàng Kinh Đô tại &f" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ() + "&a. Xoá: &f/dmm shopnpc remove"));
    }

    private boolean isNpc(Entity e) {
        return e.getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onNpcClick(PlayerInteractEntityEvent e) {
        if (!isNpc(e.getRightClicked())) {
            return;
        }
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) {
            openMenu(e.getPlayer());
        }
    }

    @EventHandler
    public void onNpcClickAt(PlayerInteractAtEntityEvent e) {
        if (isNpc(e.getRightClicked())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onNpcDamage(EntityDamageEvent e) {
        if (isNpc(e.getEntity())) {
            e.setCancelled(true);
        }
    }
}
