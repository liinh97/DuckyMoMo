package vn.duckymomo.core.paper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Kỹ năng nghề AuraSkills làm lại mỗi mùa (config aura-skills.reset-each-season, Bang Hội Chiến). Lệnh "skills skill reset <người>" của AuraSkills
 * chỉ chạy với người đang online, nên: khi leaderboard.season-start đổi (mùa mới) thì ghi nhận, rồi ai vào game lần đầu trong mùa mới (hoặc đang
 * online lúc đổi) thì xoá cấp kỹ năng của người đó (chỉ số + khả năng tự gỡ theo cấp). Lần đầu bật module chỉ ghi mùa hiện tại, không xoá ai.
 * Lưu: kynang-mua.yml (mua = mùa đã thấy, can = đang có đợt làm lại, xong = UUID đã làm lại trong đợt này).
 */
final class SkillSeason implements Listener {

    private final DuckyMoMoCore plugin;
    private final File file;
    private final YamlConfiguration data;

    SkillSeason(DuckyMoMoCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "kynang-mua.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    void start() {
        String season = plugin.getConfig().getString("leaderboard.season-start", "");
        String seen = data.getString("mua", null);
        if (seen == null) {
            data.set("mua", season);
            save();
            return;
        }
        if (!seen.equals(season)) {
            data.set("mua", season);
            data.set("can", true);
            data.set("xong", new ArrayList<String>());
            save();
            plugin.getLogger().info("Kỹ năng nghề: mùa mới (" + season + ") → làm lại cấp AuraSkills khi từng người vào game.");
            Bukkit.getOnlinePlayers().forEach(p -> Bukkit.getScheduler().runTaskLater(plugin, () -> reset(p), 40L));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!data.getBoolean("can", false)) {
            return;
        }
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> reset(p), 100L);   // đợi AuraSkills nạp dữ liệu người chơi
    }

    private void reset(Player p) {
        if (!p.isOnline() || !Bukkit.getPluginManager().isPluginEnabled("AuraSkills")) {
            return;
        }
        List<String> done = data.getStringList("xong");
        String id = p.getUniqueId().toString();
        if (done.contains(id)) {
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "skills skill reset " + p.getName());
        done.add(id);
        data.set("xong", done);
        save();
        p.sendMessage(c("&6⚒ Mùa mới: kỹ năng nghề (/skills) đã làm lại từ cấp 0. Chúc may mắn!"));
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Không lưu được kynang-mua.yml: " + ex.getMessage());
        }
    }

    private static Component c(String s) {
        return Tip.ns(LegacyComponentSerializer.legacyAmpersand().deserialize(s).decoration(TextDecoration.ITALIC, false));
    }
}
