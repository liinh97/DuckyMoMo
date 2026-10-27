package vn.duckymomo.skin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Bot của trang quản trị › Bot (dịch vụ botfarm, 27/10) vào thẳng server con nên proxy không đếm: cộng số bot đang online vào số người
 * ở danh sách server (ProxyPingEvent), thêm tên bot vào danh sách rê chuột. Hỏi botfarm mỗi 15 giây (BOTFARM_URL, mặc định
 * http://botfarm:8090/bots); botfarm tắt thì cộng 0.
 */
final class BotCount {

    private final Logger log;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final URI url = URI.create(System.getenv().getOrDefault("BOTFARM_URL", "http://botfarm:8090") + "/bots");
    private volatile List<String> online = List.of();

    BotCount(Object plugin, ProxyServer proxy, Logger log) {
        this.log = log;
        proxy.getScheduler().buildTask(plugin, this::poll).repeat(15, TimeUnit.SECONDS).schedule();
    }

    private void poll() {
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(4)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            List<String> names = new ArrayList<>();
            JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
            for (JsonElement b : o.getAsJsonArray("bots")) {
                JsonObject x = b.getAsJsonObject();
                if (x.get("online").getAsBoolean()) {
                    names.add(x.get("name").getAsString());
                }
            }
            online = names;
        } catch (Exception e) {
            if (!online.isEmpty()) {
                log.info("Bot: không hỏi được botfarm ({}), tạm không cộng bot vào số người", e.getMessage());
            }
            online = List.of();
        }
    }

    @Subscribe(order = PostOrder.LATE)
    public void onPing(ProxyPingEvent e) {
        List<String> bots = online;
        if (bots.isEmpty()) {
            return;
        }
        ServerPing ping = e.getPing();
        ServerPing.Builder b = ping.asBuilder();
        int base = ping.getPlayers().map(ServerPing.Players::getOnline).orElse(0);
        b.onlinePlayers(base + bots.size());
        List<ServerPing.SamplePlayer> sample = new ArrayList<>(ping.getPlayers().map(ServerPing.Players::getSample).orElse(List.of()));
        for (String n : bots) {
            if (sample.size() >= 12) {
                break;
            }
            sample.add(new ServerPing.SamplePlayer(n, UUID.nameUUIDFromBytes(("OfflinePlayer:" + n).getBytes(StandardCharsets.UTF_8))));
        }
        b.clearSamplePlayers().samplePlayers(sample.toArray(new ServerPing.SamplePlayer[0]));
        e.setPing(b.build());
    }
}
