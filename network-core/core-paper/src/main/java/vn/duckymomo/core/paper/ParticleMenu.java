package vn.duckymomo.core.paper;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

/**
 * Menu hiệu ứng hạt tiếng Việt (30/10, chủ server: "phân rõ như này: vết chân, vòng đầu, xoay quanh thân…"). Thay menu gốc tiếng Anh
 * của PlayerParticles khi gõ /pp (không tham số) hoặc /pp gui; các lệnh /pp khác giữ nguyên. Chọn NHÓM (vị trí trên người) ->
 * chọn KIỂU (style) -> chọn LOẠI HẠT (effect) = người chơi chạy /pp add <hạt> <kiểu>. Quyền, giới hạn số hạt do PlayerParticles
 * kiểm (rank Tử Tước trở lên, hoặc mua "1 hiệu ứng hạt" ở Cửa hàng Vàng).
 */
final class ParticleMenu implements Listener {

    private record Style(String id, String name, String desc, Material icon) { }

    private record Group(String name, String desc, Material icon, String color, List<Style> styles) { }

    private record Effect(String id, String name, Material icon) { }

    private static final List<Group> GROUPS = List.of(
            new Group("Vết chân", "Đi tới đâu để lại hạt tới đó", Material.LEATHER_BOOTS, "&a", List.of(
                    new Style("feet", "Dưới chân", "Hạt quanh bàn chân", Material.LEATHER_BOOTS),
                    new Style("move", "Khi di chuyển", "Chỉ hiện lúc đang đi", Material.GOLDEN_BOOTS),
                    new Style("trail", "Vệt kéo dài", "Để lại vệt phía sau", Material.LEAD))),
            new Group("Vòng đầu", "Hào quang, hạt trên đầu", Material.GOLDEN_HELMET, "&e", List.of(
                    new Style("halo", "Vòng hào quang", "Vòng tròn trên đỉnh đầu", Material.GOLDEN_HELMET),
                    new Style("overhead", "Trên đầu", "Hạt bay ngay trên đầu", Material.FEATHER))),
            new Group("Quanh thân", "Xoay, vòng, khối quanh người", Material.ENDER_EYE, "&b", List.of(
                    new Style("orbit", "Xoay quanh", "Hạt bay vòng quanh người", Material.ENDER_EYE),
                    new Style("rings", "Vòng tròn", "Nhiều vòng quanh thân", Material.HEART_OF_THE_SEA),
                    new Style("spin", "Xoay tròn", "Một vòng xoay quanh eo", Material.COMPASS),
                    new Style("spiral", "Xoắn ốc", "Xoắn từ chân lên đầu", Material.TWISTING_VINES),
                    new Style("quadhelix", "Xoắn bốn", "Bốn dải xoắn quanh người", Material.IRON_CHAIN),
                    new Style("vortex", "Lốc xoáy", "Phễu xoáy dưới chân", Material.WIND_CHARGE),
                    new Style("whirl", "Cuộn xoáy", "Cuộn quanh người", Material.BREEZE_ROD),
                    new Style("whirlwind", "Gió lốc", "Nhiều cuộn gió", Material.FEATHER),
                    new Style("sphere", "Quả cầu", "Cầu bao quanh người", Material.SNOWBALL),
                    new Style("icosphere", "Cầu đa giác", "Cầu nhiều mặt", Material.AMETHYST_CLUSTER),
                    new Style("cube", "Khối lập phương", "Hộp bao quanh người", Material.GLASS),
                    new Style("outline", "Viền thân", "Vẽ viền dáng người", Material.ARMOR_STAND),
                    new Style("pulse", "Nhịp đập", "Vòng lan ra theo nhịp", Material.SCULK_SENSOR),
                    new Style("twins", "Song sinh", "Hai đốm bay đối xứng", Material.ECHO_SHARD),
                    new Style("chains", "Xích", "Xích hạt quanh người", Material.IRON_CHAIN),
                    new Style("beam", "Cột sáng", "Cột hạt dưới chân", Material.END_ROD),
                    new Style("companion", "Bạn đồng hành", "Một đốm bay theo", Material.ALLAY_SPAWN_EGG),
                    new Style("invocation", "Triệu hồi", "Vòng phép dưới chân", Material.ENCHANTING_TABLE),
                    new Style("popper", "Nổ lách tách", "Hạt bật lên từng đợt", Material.FIREWORK_ROCKET),
                    new Style("normal", "Thường", "Hạt quanh người", Material.GLOWSTONE_DUST),
                    new Style("thick", "Dày đặc", "Nhiều hạt quanh người", Material.GLOWSTONE),
                    new Style("point", "Một điểm", "Một đốm trên đầu", Material.TORCH))),
            new Group("Cánh", "Đôi cánh sau lưng", Material.ELYTRA, "&d", List.of(
                    new Style("wings", "Đôi cánh", "Cánh hạt sau lưng", Material.ELYTRA),
                    new Style("batman", "Cánh dơi", "Biểu tượng cánh dơi", Material.PHANTOM_MEMBRANE))),
            new Group("Theo hành động", "Chỉ hiện khi làm việc gì đó", Material.IRON_SWORD, "&c", List.of(
                    new Style("swords", "Khi chém", "Hạt khi đánh quái / người", Material.IRON_SWORD),
                    new Style("arrows", "Khi bắn tên", "Hạt theo mũi tên", Material.BOW),
                    new Style("hurt", "Khi bị đánh", "Hạt khi mất máu", Material.REDSTONE),
                    new Style("blockbreak", "Khi đào", "Hạt chỗ khối vừa phá", Material.IRON_PICKAXE),
                    new Style("blockplace", "Khi đặt khối", "Hạt chỗ khối vừa đặt", Material.BRICKS),
                    new Style("fishing", "Khi câu cá", "Hạt ở phao câu", Material.FISHING_ROD),
                    new Style("teleport", "Khi dịch chuyển", "Hạt ở chỗ đi và chỗ đến", Material.ENDER_PEARL),
                    new Style("death", "Khi chết", "Hạt ở chỗ ngã xuống", Material.SKELETON_SKULL),
                    new Style("celebration", "Ăn mừng", "Pháo hạt quanh người", Material.CAKE))));

    private static final List<Effect> EFFECTS = List.of(
            new Effect("heart", "Trái tim", Material.POPPY),
            new Effect("flame", "Lửa", Material.BLAZE_POWDER),
            new Effect("soul_fire_flame", "Lửa linh hồn", Material.SOUL_TORCH),
            new Effect("cherry_leaves", "Hoa anh đào", Material.CHERRY_LEAVES),
            new Effect("happy_villager", "Lấp lánh xanh", Material.EMERALD),
            new Effect("enchant", "Ký tự phép", Material.ENCHANTED_BOOK),
            new Effect("note", "Nốt nhạc", Material.NOTE_BLOCK),
            new Effect("end_rod", "Ánh sáng", Material.END_ROD),
            new Effect("snowflake", "Bông tuyết", Material.SNOWBALL),
            new Effect("cloud", "Mây", Material.WHITE_WOOL),
            new Effect("totem_of_undying", "Vật tổ", Material.TOTEM_OF_UNDYING),
            new Effect("witch", "Phép tím", Material.AMETHYST_SHARD),
            new Effect("glow", "Phát sáng", Material.GLOW_INK_SAC),
            new Effect("electric_spark", "Tia điện", Material.LIGHTNING_ROD),
            new Effect("firefly", "Đom đóm", Material.GLOWSTONE_DUST),
            new Effect("dripping_honey", "Mật ong", Material.HONEY_BOTTLE),
            new Effect("spore_blossom_air", "Phấn hoa", Material.SPORE_BLOSSOM),
            new Effect("portal", "Cổng không gian", Material.OBSIDIAN),
            new Effect("crit", "Chí mạng", Material.GOLDEN_SWORD),
            new Effect("bubble_pop", "Bong bóng", Material.PRISMARINE_CRYSTALS),
            new Effect("ash", "Tro bụi", Material.GRAY_DYE));

    private final JavaPlugin plugin;

    ParticleMenu(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private static boolean has(Player p, String kind, String id) {
        return p.hasPermission("playerparticles." + kind + "." + id) || p.hasPermission("playerparticles." + kind + ".*");
    }

    /** /pp hoặc /pp gui -> menu tiếng Việt (lệnh /pp khác giữ nguyên cho PlayerParticles). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String m = e.getMessage().trim().toLowerCase(Locale.ROOT);
        if (m.equals("/pp") || m.equals("/pp gui") || m.equals("/playerparticles") || m.equals("/playerparticles gui")) {
            e.setCancelled(true);
            open(e.getPlayer());
        }
    }

    void open(Player p) {
        InfoMenu m = InfoMenu.of("✦ Hiệu ứng hạt").accent(Material.PURPLE_STAINED_GLASS_PANE);
        boolean any = p.hasPermission("playerparticles.style.*") || p.hasPermission("playerparticles.particles.max.1");
        m.top(Tip.of("&d✦ &fHiệu ứng hạt").desc("Hạt trang trí quanh người, chỉ để đẹp", "Chọn nhóm theo vị trí, rồi kiểu, rồi loại hạt")
                .line().kv("Được dùng", any ? "&acó" : "&ccần rank Tử Tước trở lên hoặc mua ở Cửa hàng Vàng")
                .apply(new ItemStack(Material.BLAZE_POWDER)));
        for (Group g : GROUPS) {
            Tip t = Tip.of(g.color() + "✦ &f" + g.name()).accent(g.color()).desc(g.desc()).line().head(g.styles().size() + " kiểu");
            g.styles().stream().limit(6).forEach(s -> t.item(s.name()));
            if (g.styles().size() > 6) {
                t.item("… và " + (g.styles().size() - 6) + " kiểu khác");
            }
            t.line().click("Bấm để chọn kiểu");
            m.add(t.apply(new ItemStack(g.icon())), pl -> openGroup(pl, g));
        }
        m.row();
        m.add(Tip.of("&c✘ &fGỡ hết hiệu ứng").desc("Tắt mọi hạt đang bật trên người bạn").click("Bấm để gỡ").apply(new ItemStack(Material.BARRIER)), pl -> {
            pl.closeInventory();
            pl.performCommand("pp reset");
        });
        m.open(p);
    }

    private void openGroup(Player p, Group g) {
        InfoMenu m = InfoMenu.of("✦ " + g.name()).accent(Material.PURPLE_STAINED_GLASS_PANE).back(this::open);
        m.top(Tip.of(g.color() + "✦ &f" + g.name()).desc(g.desc()).apply(new ItemStack(g.icon())));
        int i = 0;
        for (Style s : g.styles()) {
            boolean ok = has(p, "style", s.id());
            Tip t = Tip.of((ok ? g.color() : "&7") + s.name()).accent(g.color()).desc(s.desc()).line();
            if (ok) {
                t.click("Bấm để chọn loại hạt");
            } else {
                t.deny("Chưa mở kiểu này");
            }
            m.add(t.apply(new ItemStack(s.icon())), ok ? pl -> openEffects(pl, g, s) : null);
            if (++i % 7 == 0) {
                m.row();
            }
        }
        m.open(p);
    }

    private void openEffects(Player p, Group g, Style s) {
        InfoMenu m = InfoMenu.of("✦ " + s.name() + " · chọn hạt").accent(Material.PURPLE_STAINED_GLASS_PANE).back(pl -> openGroup(pl, g));
        m.top(Tip.of(g.color() + s.name()).desc(s.desc(), "Chọn loại hạt để bật").apply(new ItemStack(s.icon())));
        int i = 0;
        for (Effect ef : EFFECTS) {
            boolean ok = has(p, "effect", ef.id());
            Tip t = Tip.of((ok ? "&f" : "&7") + ef.name()).accent("&d").line();
            if (ok) {
                t.click("Bấm để bật");
            } else {
                t.deny("Chưa mở loại hạt này");
            }
            m.add(t.apply(new ItemStack(ef.icon())), ok ? pl -> {
                pl.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> pl.performCommand("pp add " + ef.id() + " " + s.id()));
            } : null);
            if (++i % 7 == 0) {
                m.row();
            }
        }
        m.open(p);
    }
}
