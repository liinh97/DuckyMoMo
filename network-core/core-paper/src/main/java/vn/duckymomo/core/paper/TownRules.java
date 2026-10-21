package vn.duckymomo.core.paper;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.event.PreNewTownEvent;
import com.palmergames.bukkit.towny.event.TownPreAddResidentEvent;
import com.palmergames.bukkit.towny.event.TownPreClaimEvent;
import com.palmergames.bukkit.towny.event.TownRemoveResidentEvent;
import com.palmergames.bukkit.towny.event.nation.PreNewNationEvent;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Luật đất đai Bang Hội Chiến (town-rules trong config.yml, chốt 14/10):
 *  - lập thị trấn cần town-min-hours giờ chơi thật; lập quốc gia cần thị trấn có nation-min-active-residents dân hoạt động;
 *  - quốc gia cần siege-min-towns thị trấn mới cắm cờ vây chinh phục (sự kiện PreSiegeWarStartEvent của SiegeWar, gọi qua reflection);
 *  - vòng reserved-spawn-radius block quanh spawn chỉ dành cho quốc gia reserved-nation (Liên minh Hoà Bình);
 *  - rời/bị đuổi khỏi thị trấn thì join-cooldown-days ngày sau mới vào thị trấn khác;
 *  - đất: mỗi dân chỉ mang lại town_block_ratio ô nếu là dân hoạt động (bonus âm cho dân không hoạt động, chỉnh 10 phút/lần).
 * Dân hoạt động = chơi thật >= active-hours-per-week giờ trong 7 ngày VÀ đã ở thị trấn >= new-member-days ngày.
 */
final class TownRules implements Listener {

    private final DuckyMoMoCore plugin;
    private final Activity activity;
    private final PeaceAlliance peace;
    private final double townMinHours;
    private final int nationMinActive;
    private final int siegeMinTowns;
    private final int joinCooldownDays;
    private final boolean landByActive;
    private final int reservedRadius;
    private final String reservedNation;
    private final String world;
    private final int spawnX;
    private final int spawnZ;
    private final File file;
    private final YamlConfiguration data;

    TownRules(DuckyMoMoCore plugin, Activity activity, PeaceAlliance peace, ConfigurationSection c) {
        this.plugin = plugin;
        this.activity = activity;
        this.peace = peace;
        this.townMinHours = c.getDouble("town-min-hours", 3);
        this.nationMinActive = c.getInt("nation-min-active-residents", 3);
        this.siegeMinTowns = c.getInt("siege-min-towns", 2);
        this.joinCooldownDays = c.getInt("join-cooldown-days", 3);
        this.landByActive = c.getBoolean("land-per-active-resident", true);
        this.reservedRadius = c.getInt("reserved-spawn-radius", 300);
        this.reservedNation = peace != null ? peace.nationName() : c.getString("reserved-nation", "");
        this.world = c.getString("world", "world");
        List<Integer> sp = c.getIntegerList("spawn");
        this.spawnX = sp.size() >= 2 ? sp.get(0) : 0;
        this.spawnZ = sp.size() >= 2 ? sp.get(1) : 0;
        this.file = new File(plugin.getDataFolder(), "datdai.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::adjustLand, 600L, 12000L);
        hookSiegeStart();
        plugin.getLogger().info(String.format(Locale.ROOT,
                "Luật đất đai: lập thị trấn %.0f giờ, quốc gia %d dân hoạt động, cắm cờ cần %d thị trấn, vòng spawn %d block, đổi thị trấn chờ %d ngày, đất theo dân hoạt động: %s",
                townMinHours, nationMinActive, siegeMinTowns, reservedRadius, joinCooldownDays, landByActive));
    }

    private static boolean bypass(Player p) {
        return p != null && p.hasPermission("duckymomo.town.bypass");
    }

    boolean isActiveMember(Resident r) {
        return Members.active(activity, r);
    }

    private boolean inReserved(String worldName, int x, int z) {
        if (reservedRadius <= 0 || !world.equalsIgnoreCase(worldName)) {
            return false;
        }
        long dx = x - spawnX;
        long dz = z - spawnZ;
        return dx * dx + dz * dz <= (long) reservedRadius * reservedRadius;
    }

    private boolean isReservedNation(Town t) {
        Nation n = t == null ? null : t.getNationOrNull();
        return n != null && !reservedNation.isBlank() && n.getName().equalsIgnoreCase(reservedNation);
    }

    // ------------------------------------------------------------------ lập thị trấn, chiếm đất, lập quốc gia

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNewTown(PreNewTownEvent e) {
        Player p = e.getPlayer();
        if (bypass(p)) {
            return;
        }
        double h = activity.totalHours(p.getUniqueId());
        if (h < townMinHours) {
            e.setCancelled(true);
            e.setCancelMessage(String.format(Locale.ROOT,
                    "Cần chơi thật đủ %.0f giờ mới được lập thị trấn (bạn đang có %.1f giờ, xem /hoatdong).", townMinHours, h));
            return;
        }
        if (inReserved(e.getTownLocation().getWorld().getName(), e.getTownLocation().getBlockX(), e.getTownLocation().getBlockZ())) {
            // Trong vòng spawn chỉ lập thị trấn sát đất hoà bình, thị trấn tự vào Liên minh Hoà Bình
            String err = peace == null ? "Vùng " + reservedRadius + " block quanh kinh đô (spawn) dành cho Liên minh Hoà Bình."
                    : peace.newTownInRing(p, e.getTownName(), e.getTownLocation());
            if (err != null) {
                e.setCancelled(true);
                e.setCancelMessage(err);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClaim(TownPreClaimEvent e) {
        if (bypass(e.getPlayer()) || isReservedNation(e.getTown())) {
            return;
        }
        TownBlock tb = e.getTownBlock();
        int size = TownySettings.getTownBlockSize();
        if (inReserved(tb.getWorld().getName(), tb.getX() * size + size / 2, tb.getZ() * size + size / 2)) {
            e.setCancelled(true);
            e.setCancelMessage("Vùng " + reservedRadius + " block quanh kinh đô (spawn) dành cho Liên minh Hoà Bình.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNewNation(PreNewNationEvent e) {
        Town t = e.getTown();
        Player mayor = t.getMayor() == null ? null : t.getMayor().getPlayer();
        if (bypass(mayor)) {
            return;
        }
        // Kinh đô của server (thị trưởng NPC, không có dân) lập Liên minh Hoà Bình: không cần dân hoạt động (21/10: /dmm hoabinh setup bị chặn ở đây)
        if (peace != null && t.getName().equalsIgnoreCase(peace.capitalName()) && e.getNationName().equalsIgnoreCase(peace.nationName())) {
            return;
        }
        long active = t.getResidents().stream().filter(this::isActiveMember).count();
        if (active < nationMinActive) {
            e.setCancelled(true);
            e.setCancelMessage(String.format(Locale.ROOT,
                    "Thị trấn cần ít nhất %d dân hoạt động (chơi thật %.0f giờ/7 ngày, ở thị trấn đủ %d ngày) mới lập được quốc gia. Hiện có %d.",
                    nationMinActive, activity.activeHoursPerWeek(), activity.newMemberDays(), active));
        }
    }

    // ------------------------------------------------------------------ đổi thị trấn

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeave(TownRemoveResidentEvent e) {
        data.set("roi." + e.getResident().getUUID(), System.currentTimeMillis());
        save();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onJoin(TownPreAddResidentEvent e) {
        Resident r = e.getResident();
        if (bypass(r.getPlayer())) {
            return;
        }
        long left = data.getLong("roi." + r.getUUID());
        long wait = left + joinCooldownDays * 86_400_000L - System.currentTimeMillis();
        if (left > 0 && wait > 0) {
            e.setCancelled(true);
            e.setCancelMessage(String.format(Locale.ROOT,
                    "%s vừa rời thị trấn cũ, còn %.1f ngày nữa mới vào được thị trấn khác.", r.getName(), wait / 86_400_000.0));
        }
    }

    // ------------------------------------------------------------------ đất theo dân hoạt động

    /** Mỗi dân không hoạt động: trừ town_block_ratio ô khỏi giới hạn đất (qua bonus âm). Đất đã có không bị lấy lại. */
    private void adjustLand() {
        int ratio = TownySettings.getTownBlockRatio();
        for (Town t : TownyAPI.getInstance().getTowns()) {
            String key = "bonus." + t.getUUID();
            int applied = data.getInt(key);
            int desired = 0;
            if (landByActive && ratio > 0) {
                long inactive = t.getResidents().stream().filter(r -> !isActiveMember(r)).count();
                desired = (int) (-ratio * inactive);
            }
            if (desired != applied) {
                t.setBonusBlocks(t.getBonusBlocks() - applied + desired);
                t.save();
                data.set(key, desired == 0 ? null : desired);
            }
        }
        save();
    }

    // ------------------------------------------------------------------ cắm cờ vây

    @SuppressWarnings("unchecked")
    private void hookSiegeStart() {
        if (!Bukkit.getPluginManager().isPluginEnabled("SiegeWar")) {
            return;
        }
        try {
            ClassLoader cl = Bukkit.getPluginManager().getPlugin("SiegeWar").getClass().getClassLoader();
            Class<? extends Event> cls = (Class<? extends Event>) Class.forName("com.gmail.goosius.siegewar.events.PreSiegeWarStartEvent", true, cl);
            Method type = cls.getMethod("getSiegeType");
            Method nation = cls.getMethod("getNation");
            Method msg = cls.getMethod("setCancellationMsg", String.class);
            Bukkit.getPluginManager().registerEvent(cls, this, EventPriority.HIGH, (l, ev) -> {
                if (!cls.isInstance(ev)) {
                    return;
                }
                try {
                    if (!"CONQUEST".equals(String.valueOf(type.invoke(ev)))) {
                        return;
                    }
                    Nation n = (Nation) nation.invoke(ev);
                    if (n != null && n.getNumTowns() < siegeMinTowns) {
                        ((org.bukkit.event.Cancellable) ev).setCancelled(true);
                        msg.invoke(ev, "Quốc gia cần ít nhất " + siegeMinTowns + " thị trấn mới được cắm cờ vây thành (hiện có " + n.getNumTowns() + ").");
                    }
                } catch (ReflectiveOperationException ex) {
                    plugin.getLogger().warning("Kiểm tra cắm cờ vây lỗi: " + ex);
                }
            }, plugin, true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Không gắn được kiểm tra cắm cờ vây (SiegeWar): " + e);
        }
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được datdai.yml: " + e.getMessage());
        }
    }
}
