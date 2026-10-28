package vn.duckymomo.core.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phù phép riêng của server, chuột phải (Thư viện đồ › Phù phép, mã web "dmm:<mã>", lưu dmm-enchants trong thuvien-do.yml):
 * dấu PDC duckymomocore:phuphep_<mã> = cấp + dòng "Lướt II" đầu mô tả; cấp quyết định mạnh yếu (levelValue).
 *   luot        Lướt về phía trước (lực 1 / 1,5 / 2), hồi 4 giây
 *   hoi_mau     Hồi máu 1 tim mỗi cấp, hồi 20 giây (đầy máu không dùng)
 *   cau_lua     Cầu lửa: sát thương 3 + 2 × cấp, cháy người trúng, không đốt khối, hồi 3 giây
 *   bay         Bay lên: bay lơ lửng 1 + cấp giây rồi rơi chậm, hồi 15 giây
 *   dich_chuyen Dịch chuyển tức thì 2 + 2 × cấp khối về trước theo hướng ngang, bám địa hình, dừng trước vách (không xuyên), hồi 8 giây
 *   dam_dat     Dậm đất: sát thương 2 + cấp, bán kính 2 + cấp, hất tung, hồi 12 giây
 *   cuong_no    Huyết chiến: mất 3 tim, +10 + 10 × cấp % sát thương trong 8 giây, hồi 30 giây (máu ≤ 7 thì không dùng)
 *   phong_bay   Phóng bay: gắn lên cánh elytra; đang mặc, chuột phải vào không khí là phóng lên và bay, hồi 10 giây
 *   khong_lo    Khổng lồ: to gấp 1,5 / 1,75 / 2 lần trong 10 giây (chỉ số scale có sẵn của game), hồi 40 giây; phải đủ chỗ trống phía trên
 *   thu_nho     Thu nhỏ: còn 0,75 / 0,6 / 0,5 lần trong 10 giây, hồi 40 giây
 * Nhóm lấy ý từ chiêu tướng LMHT (22/10, docs/y-tuong-phu-phep-lmht.md; tên riêng, không dùng tên Riot). Nhắm = tia từ mắt, trúng sinh vật
 * đầu tiên, không xuyên tường (aim). Chiêu tác động lên người khác phải qua được "đòn thử" (strike: sát thương thật qua sự kiện, Towny /
 * PvP / vùng an toàn / Đấu Trường chặn thì chiêu không có tác dụng):
 *   cam_lang    Câm lặng: nhắm 12 khối, mục tiêu không dùng được phù phép riêng + kỹ năng Xưởng 2 / 3 / 4 giây, hồi 25 giây
 *   troi        Trói: nhắm 15 khối, đứng im (tốc chạy + sức nhảy về 0, modifier tạm) 1 / 1,5 / 2 giây, hồi 14 giây
 *   moc_keo     Móc kéo: nhắm 14 khối, kéo mục tiêu về phía mình, hồi 16 giây
 *   tua_thoi_gian Tua thời gian: bấm lần 1 đặt mốc; trong 4 giây bấm lại (hoặc tự động khi hết 4 giây) quay về mốc, hồi lại 50 / 75 / 100 %
 *               máu đã mất từ lúc đặt mốc; mốc bị lấp kín / khác thế giới / đã chết thì huỷ; hồi 20 giây (tính từ lúc quay về)
 *   khien_phep  Khiên phép: tim vàng 2 / 4 / 6 tim trong 6 giây cho người chơi đang nhìn (10 khối), không nhìn ai thì cho mình, hồi 20 giây
 *   hoi_mau_dong_doi Hồi máu đồng đội: nhìn vào người chơi khác (15 khối) hồi 2 / 3 / 4 tim, hồi 12 giây
 *   chan_dan    Chắn đạn: 3 / 4 / 5 giây chặn mọi mũi tên / đạn bay tới từ phía trước mặt, hồi 20 giây
 *   soi_sang    Soi sáng: người chơi trong 20 / 25 / 30 khối phát sáng (Glowing, thấy cả người tàng hình) 5 giây, hồi 30 giây
 * Đợt 2 (22/10, người dùng "loại tính năng trùng": bỏ ý trùng phù phép riêng / Xưởng / CrazyEnchantments, còn 18):
 *   hoang_so    Hoảng sợ: nhắm 12 khối; quái bỏ chạy 2 / 3 / 4 giây, người bị đẩy lùi + chậm + tối mắt, hồi 18 giây
 *   hoa_cuu     Hoá cừu: nhắm 12 khối, mục tiêu không gây được sát thương 1 / 1,5 / 2 giây + chậm, hồi 20 giây
 *   hoa_da      Ánh mắt hoá đá: hình nón 8 khối trước mặt; ai đang NHÌN về phía mình bị trói 1 / 1,5 / 2 giây, quay lưng thì chỉ chậm, hồi 24 giây
 *   khieu_khich Khiêu khích: quái thù địch trong 6 / 8 / 10 khối quay sang đánh mình (không tác dụng lên người), hồi 15 giây
 *   danh_dau    Đánh dấu tử thần: nhắm 12 khối; 3 giây sau dấu nổ, gây thêm 25 / 35 / 45 % sát thương mình đã gây cho mục tiêu trong 3 giây đó, hồi 28 giây
 *   ho_den      Hố đen: chỗ nhắm (tối đa 15 khối) hút sinh vật trong 4 / 5 / 6 khối về giữa trong 2 giây, hồi 30 giây
 *   day_vang    Đẩy văng: nhắm 10 khối, đẩy mục tiêu bật xa; đập vào tường thì trói 1 giây + 2 sát thương, hồi 15 giây
 *   ho_gia      Hộ giá: nhìn vào người cùng thị trấn / quốc gia / quốc gia đồng minh (40 khối), 1,5 giây sau dịch chuyển tới, cả hai được
 *               2 / 4 / 6 tim vàng 6 giây, hồi 60 giây (không có Towny: ai cũng được)
 *   dong_bang   Đóng băng thời gian: tự đóng băng 2 / 2,5 / 3 giây: đứng im, không nhận sát thương, không đánh được, hết giờ hồi 1 tim, hồi 60 giây
 *   bat_tu      Bất tử: 3 / 4 / 5 giây máu không xuống dưới nửa tim, hồi 120 giây
 *   la_chan     Lá chắn phép: 4 / 5 / 6 giây, chặn 1 phù phép riêng của người khác nhắm vào mình (trói, kéo, câm lặng…) và hồi 1 tim, hồi 22 giây
 *   giai_khong_che Giải khống chế: gỡ Trói, Câm lặng, Hoá cừu và hiệu ứng xấu (chậm, độc, mù…), kháng khống chế 1 / 2 / 3 giây; dùng được cả khi
 *               đang bị Câm lặng, hồi 30 giây
 *   loc_xoay    Lốc xoáy: xoay chém 3 giây, mỗi 0,5 giây gây 1,5 / 2 / 2,5 sát thương quanh 3 khối, hồi 18 giây
 *   tia_sang    Tia sáng: tụ 0,5 giây rồi bắn tia thẳng 20 khối (dừng ở khối), trúng mọi sinh vật trên đường 4 / 6 / 8 sát thương, hồi 25 giây
 *   phao_kich   Pháo kích: chỉ vào mặt đất trong 30 khối, 1,5 giây sau nổ bán kính 3 khối, 4 / 6 / 8 sát thương, không phá khối, hồi 22 giây
 *   set_nay     Sét nảy: nhắm 15 khối, 3 / 4 / 5 sát thương rồi nảy sang 2 / 3 / 4 sinh vật gần nhau (mỗi lần nảy bớt 20 %), hồi 16 giây
 *   lien_hoan   Liên hoàn đao: chém lần lượt 2 / 3 / 4 sinh vật gần nhất trong 8 khối (4 / 5 / 6 sát thương), không bị đánh trong lúc chém, xong
 *               về chỗ cũ, hồi 22 giây
 *   rut_bai     Rút bài: bấm lần 1 xoay bài Đỏ / Xanh / Vàng (4 giây), bấm lần 2 chốt; đòn cận chiến kế tiếp trong 6 giây: Đỏ = nổ quanh mục tiêu
 *               + chậm, Xanh = mình hồi máu, Vàng = trói mục tiêu; mạnh theo cấp, hồi 14 giây (tính từ lúc chốt)
 *   tang_toc    Tăng tốc (thụ động, 22/10): cầm trên tay chính / tay phụ thì tốc chạy +10% / 20% / 30% (startPassive)
 * Đợt 3 (28/10, chủ server: "tàng hình ngắn như Q Shaco, nhảy cao, súng cao su như E Zac, phản công như Jax"):
 *   tang_hinh   Tàng hình: lướt 3 / 4 / 5 khối về trước (không xuyên tường) rồi vô hình hẳn với người khác (cả giáp) 1,5 / 2 / 2,5 giây,
 *               quái đang nhắm thì mất dấu; đòn đầu tiên trong lúc tàng hình +30 / 45 / 60 % sát thương và lộ diện, hồi 14 giây
 *   nhay_cao    Nhảy cao: bật thẳng lên ~5 / 9 / 14 khối (hơi nhích theo hướng nhìn), không mất máu khi rơi, phải đứng trên đất, hồi 8 giây
 *   sung_cao_su Súng cao su: bấm lần 1 kéo dây (đứng gần như im, tối đa 1,2 giây đầy), bấm lần 2 (hoặc tự bật sau 2,2 giây) bắn mình bay
 *               theo hướng nhìn, xa tối đa 8 / 11 / 14 khối theo độ căng; đáp đất hất tung + 3 / 4 / 5 sát thương quanh 3 khối, không mất
 *               máu khi rơi, hồi 16 giây (tính từ lúc kéo)
 *   phan_cong   Phản công: 2 giây né mọi đòn đánh (cận chiến + tên / đạn của sinh vật), hết giờ (hoặc bấm lại sau 0,5 giây) choáng 1 giây +
 *               2 / 3 / 4 sát thương quanh 3 khối, mỗi đòn đã né +20 % sát thương (tối đa +100 %), hồi 18 giây
 * Kích thước là modifier tạm (addTransientModifier: không lưu vào dữ liệu người chơi, thoát game / sập server là mất), mỗi lần dùng chỉ
 * 1 lệnh hẹn giờ trả lại nên không gây lag. Hết giờ mà đang ở chỗ chật thì chờ ra chỗ rộng (tối đa 10 giây) rồi mới về cỡ thường.
 * Tắt ở đâu do Sách cấm quyết định (BanBook; trang Công thành mặc định tắt Bay lên, Dịch chuyển, Phóng bay, Thu nhỏ).
 * Sát thương đi qua EntityDamageByEntityEvent nên Towny / PvP / vùng an toàn vẫn chặn như đòn thường. Hồi chiêu theo người.
 */
final class SkillEnchants implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    /** mã -> [tên, tên tiếng Anh]; thứ tự = thứ tự dòng trên đồ. Khớp DMM_ENCH trong panel/thuvien.html và LIB_DMM_ENCH trong app.py. */
    static final Map<String, String[]> NAMES = new LinkedHashMap<>();
    static final int MAX_LEVEL = 3;
    static final NamespacedKey FIRE = new NamespacedKey("duckymomocore", "caulua_satthuong");
    private static final Map<String, Long> COOLDOWN_MS = Map.ofEntries(Map.entry("luot", 4000L), Map.entry("hoi_mau", 20000L),
            Map.entry("cau_lua", 3000L), Map.entry("bay", 15000L), Map.entry("dich_chuyen", 8000L), Map.entry("dam_dat", 12000L),
            Map.entry("cuong_no", 30000L), Map.entry("phong_bay", 10000L), Map.entry("khong_lo", 40000L), Map.entry("thu_nho", 40000L),
            Map.entry("cam_lang", 25000L), Map.entry("troi", 14000L), Map.entry("moc_keo", 16000L), Map.entry("tua_thoi_gian", 20000L),
            Map.entry("khien_phep", 20000L), Map.entry("hoi_mau_dong_doi", 12000L), Map.entry("chan_dan", 20000L), Map.entry("soi_sang", 30000L),
            Map.entry("hoang_so", 18000L), Map.entry("hoa_cuu", 20000L), Map.entry("hoa_da", 24000L), Map.entry("khieu_khich", 15000L),
            Map.entry("danh_dau", 28000L), Map.entry("ho_den", 30000L), Map.entry("day_vang", 15000L), Map.entry("ho_gia", 60000L),
            Map.entry("dong_bang", 60000L), Map.entry("bat_tu", 120000L), Map.entry("la_chan", 22000L), Map.entry("giai_khong_che", 30000L),
            Map.entry("loc_xoay", 18000L), Map.entry("tia_sang", 25000L), Map.entry("phao_kich", 22000L), Map.entry("set_nay", 16000L),
            Map.entry("lien_hoan", 22000L), Map.entry("rut_bai", 14000L), Map.entry("tang_hinh", 14000L), Map.entry("nhay_cao", 8000L),
            Map.entry("sung_cao_su", 16000L), Map.entry("phan_cong", 18000L));
    /** Tàng hình: người -> [hết lúc (ms), % sát thương đòn đầu]. */
    private static final Map<UUID, double[]> STEALTH = new ConcurrentHashMap<>();
    /** Nhảy cao / Súng cao su: không mất máu khi rơi tới lúc. */
    private static final Map<UUID, Long> NOFALL = new ConcurrentHashMap<>();
    /** Súng cao su đang kéo: người -> [bắt đầu (ms), xa tối đa, sát thương]. */
    private static final Map<UUID, double[]> SLING = new ConcurrentHashMap<>();
    /** Phản công: người -> [hết lúc (ms), sát thương gốc, số đòn đã né, bắt đầu (ms)]. */
    private static final Map<UUID, double[]> COUNTER = new ConcurrentHashMap<>();
    /** Hoá cừu / Đóng băng: sinh vật -> hết lúc không gây được sát thương. */
    private static final Map<UUID, Long> DISARM = new ConcurrentHashMap<>();
    /** Đóng băng / Liên hoàn đao: không nhận sát thương tới lúc. */
    private static final Map<UUID, Long> INVULN = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> UNDYING = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> SPELL_SHIELD = new ConcurrentHashMap<>();
    /** Giải khống chế: kháng khống chế tới lúc. */
    private static final Map<UUID, Long> CC_IMMUNE = new ConcurrentHashMap<>();
    /** Đánh dấu tử thần: mục tiêu -> dấu (người đánh dấu, tổng sát thương, hết lúc, % nổ). */
    private static final class DeathMark {
        final UUID caster;
        final long until;
        final double pct;
        double sum;

        DeathMark(UUID caster, long until, double pct) {
            this.caster = caster;
            this.until = until;
            this.pct = pct;
        }
    }
    private static final Map<UUID, DeathMark> DEATH = new ConcurrentHashMap<>();
    /** Rút bài: lá đang xoay / đã chốt. card -1 = đang xoay. */
    private static final class Card {
        long start;
        int idx;
        int card = -1;
        long until;
        double value;
    }
    private static final Map<UUID, Card> CARDS = new ConcurrentHashMap<>();
    private static final String[] CARD_NAMES = {"&cĐỎ", "&9XANH", "&6VÀNG"};
    /** Câm lặng: người -> hết lúc (ms). */
    private static final Map<UUID, Long> SILENCE = new ConcurrentHashMap<>();
    /** Trói: modifier tạm tốc chạy + sức nhảy; sinh vật -> lượt. */
    static final NamespacedKey ROOT = new NamespacedKey("duckymomocore", "phuphep_troi");
    private static final Map<UUID, Long> ROOT_TOKEN = new ConcurrentHashMap<>();
    /** Tua thời gian: người -> mốc. */
    private record Mark(org.bukkit.Location at, double health, long token, double value) {
    }
    private static final Map<UUID, Mark> MARKS = new ConcurrentHashMap<>();
    /** Chắn đạn: người -> hết lúc (ms). */
    private static final Map<UUID, Long> WALL = new ConcurrentHashMap<>();
    /** Đòn thử của strike(): sự kiện sát thương có tới MONITOR (không bị huỷ) không. */
    private static org.bukkit.entity.Entity probeFrom;
    private static org.bukkit.entity.Entity probeTo;
    private static boolean probeOk;
    /** Cuồng nộ đang bật: người -> [hết lúc (ms), hệ số sát thương]. */
    private static final Map<UUID, double[]> RAGE = new ConcurrentHashMap<>();
    static final NamespacedKey SIZE = new NamespacedKey("duckymomocore", "phuphep_kichthuoc");
    private static final long SIZE_MS = 10000L;
    /** Khổng lồ / Thu nhỏ đang bật: người -> lượt (lần dùng sau thay lần trước, hẹn giờ cũ bỏ qua). */
    private static final Map<UUID, Long> SIZE_TOKEN = new ConcurrentHashMap<>();
    private static final Map<String, Long> NEXT = new ConcurrentHashMap<>();   // "<uuid>:<mã>" -> lúc dùng lại được
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};

    static {
        NAMES.put("luot", new String[]{"Lướt", "Dash"});
        NAMES.put("hoi_mau", new String[]{"Hồi máu", "Heal"});
        NAMES.put("cau_lua", new String[]{"Cầu lửa", "Fire"});
        NAMES.put("bay", new String[]{"Bay lên", "Ascend"});
        NAMES.put("dich_chuyen", new String[]{"Dịch chuyển", "Transmission"});
        NAMES.put("dam_dat", new String[]{"Dậm đất", "Rumble"});
        NAMES.put("cuong_no", new String[]{"Huyết chiến", "Soul Storm"});   // "Cuồng nộ" trùng tên phù phép Rage của CE
        NAMES.put("phong_bay", new String[]{"Phóng bay", "Slingshot"});
        NAMES.put("khong_lo", new String[]{"Khổng lồ", "Titan"});
        NAMES.put("thu_nho", new String[]{"Thu nhỏ", "Shrink"});
        NAMES.put("cam_lang", new String[]{"Câm lặng", "Silence"});
        NAMES.put("troi", new String[]{"Trói", "Bind"});
        NAMES.put("moc_keo", new String[]{"Móc kéo", "Hook"});
        NAMES.put("tua_thoi_gian", new String[]{"Tua thời gian", "Rewind"});
        NAMES.put("khien_phep", new String[]{"Khiên phép", "Barrier"});
        NAMES.put("hoi_mau_dong_doi", new String[]{"Hồi máu đồng đội", "Mend"});
        NAMES.put("chan_dan", new String[]{"Chắn đạn", "Wind Wall"});
        NAMES.put("soi_sang", new String[]{"Soi sáng", "Reveal"});
        NAMES.put("hoang_so", new String[]{"Hoảng sợ", "Terrify"});
        NAMES.put("hoa_cuu", new String[]{"Hoá cừu", "Polymorph"});
        NAMES.put("hoa_da", new String[]{"Ánh mắt hoá đá", "Petrify"});
        NAMES.put("khieu_khich", new String[]{"Khiêu khích", "Taunt"});
        NAMES.put("danh_dau", new String[]{"Đánh dấu tử thần", "Death Mark"});
        NAMES.put("ho_den", new String[]{"Hố đen", "Black Hole"});
        NAMES.put("day_vang", new String[]{"Đẩy văng", "Condemn"});
        NAMES.put("ho_gia", new String[]{"Hộ giá", "Guardian"});
        NAMES.put("dong_bang", new String[]{"Đóng băng thời gian", "Stasis"});
        NAMES.put("bat_tu", new String[]{"Bất tử", "Undying"});
        NAMES.put("la_chan", new String[]{"Lá chắn phép", "Spell Shield"});
        NAMES.put("giai_khong_che", new String[]{"Giải khống chế", "Cleanse"});
        NAMES.put("loc_xoay", new String[]{"Lốc xoáy", "Whirlwind"});
        NAMES.put("tia_sang", new String[]{"Tia sáng", "Light Beam"});
        NAMES.put("phao_kich", new String[]{"Pháo kích", "Artillery"});
        NAMES.put("set_nay", new String[]{"Sét nảy", "Chain Lightning"});
        NAMES.put("lien_hoan", new String[]{"Liên hoàn đao", "Blade Flurry"});
        NAMES.put("rut_bai", new String[]{"Rút bài", "Pick a Card"});
        NAMES.put("tang_toc", new String[]{"Tăng tốc", "Swiftness"});   // thụ động: cầm là chạy nhanh hơn (startPassive)
        NAMES.put("tang_hinh", new String[]{"Tàng hình", "Vanish"});
        NAMES.put("nhay_cao", new String[]{"Nhảy cao", "Leap"});
        NAMES.put("sung_cao_su", new String[]{"Súng cao su", "Catapult"});
        NAMES.put("phan_cong", new String[]{"Phản công", "Counter"});
    }

    static NamespacedKey key(String id) {
        return new NamespacedKey("duckymomocore", "phuphep_" + id);
    }

    /** Phù phép thụ động (không bấm chuột phải). */
    static final java.util.Set<String> PASSIVE = java.util.Set.of("tang_toc");
    private static final NamespacedKey SPEED_MOD = new NamespacedKey("duckymomocore", "phuphep_tang_toc");
    /** Tăng tốc đang áp: người -> cấp (chỉ đổi modifier khi cấp đổi). */
    private static final Map<UUID, Integer> SPEED_LV = new ConcurrentHashMap<>();

    /**
     * Tăng tốc (22/10, người dùng: "cầm item có skill này thì tăng tốc"): món có phù phép trên tay chính hoặc tay phụ thì tốc chạy
     * +10% / 20% / 30% (chỉ số % của game, modifier tạm không lưu). Hai tay cùng có thì lấy cấp cao hơn. Sách cấm tắt ở đâu thì ở đó
     * không có tác dụng. Kiểm mỗi 0,5 giây.
     */
    static void startPassive(org.bukkit.plugin.Plugin plugin) {
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                int lv = 0;
                for (ItemStack it : new ItemStack[]{p.getInventory().getItemInMainHand(), p.getInventory().getItemInOffHand()}) {
                    if (it == null || it.isEmpty() || !it.hasItemMeta()) {
                        continue;
                    }
                    Integer l = it.getItemMeta().getPersistentDataContainer().get(key("tang_toc"), PersistentDataType.INTEGER);
                    if (l != null && l > lv && BanBook.enchantBlockedBy(p, it, "tang_toc") == null) {
                        lv = l;
                    }
                }
                Integer had = SPEED_LV.get(p.getUniqueId());
                if ((had == null ? 0 : had) == lv) {
                    continue;
                }
                var attr = p.getAttribute(Attribute.MOVEMENT_SPEED);
                if (attr == null) {
                    continue;
                }
                attr.removeModifier(SPEED_MOD);
                if (lv > 0) {
                    attr.addTransientModifier(new org.bukkit.attribute.AttributeModifier(SPEED_MOD, levelValue("tang_toc", lv) / 100.0,
                            org.bukkit.attribute.AttributeModifier.Operation.ADD_SCALAR));
                    SPEED_LV.put(p.getUniqueId(), lv);
                } else {
                    SPEED_LV.remove(p.getUniqueId());
                }
            }
            SPEED_LV.keySet().removeIf(u -> org.bukkit.Bukkit.getPlayer(u) == null);
        }, 20L, 10L);
    }

    /** Giá trị kỹ năng theo cấp phù phép: Lướt = lực 1 / 1,5 / 2; Hồi máu = 2 máu (1 tim) mỗi cấp; Cầu lửa = 3 + 2 × cấp sát thương. */
    static double levelValue(String id, int lv) {
        return switch (id) {
            case "luot" -> 0.5 + 0.5 * lv;
            case "tang_toc" -> 10.0 * lv;              // % tốc chạy: 10 / 20 / 30
            case "hoi_mau" -> 2.0 * lv;
            case "bay" -> 1 + lv;              // giây
            case "dich_chuyen" -> 2 + 2.0 * lv; // khối
            case "dam_dat" -> 2 + lv;           // sát thương, bán kính cũng = 2 + cấp
            case "cuong_no" -> 10 + 10.0 * lv;  // %
            case "phong_bay" -> lv;
            case "khong_lo" -> 1.25 + 0.25 * lv;       // lần to: 1,5 / 1,75 / 2
            case "thu_nho" -> lv <= 1 ? 0.75 : lv == 2 ? 0.6 : 0.5;   // lần nhỏ: 0,75 / 0,6 / 0,5 (trước 22/10 trưa công thức ra 0,45 ở cấp III)
            case "cam_lang" -> 1 + lv;                 // giây
            case "troi" -> 0.5 + 0.5 * lv;             // giây
            case "moc_keo" -> lv;                      // lực
            case "tua_thoi_gian" -> 0.25 + 0.25 * lv;  // phần máu đã mất được hồi lại
            case "khien_phep" -> 2.0 * lv;             // tim vàng
            case "hoi_mau_dong_doi" -> 2 + 2.0 * lv;   // máu (2 = 1 tim)
            case "chan_dan" -> 2 + lv;                 // giây
            case "soi_sang" -> 15 + 5.0 * lv;          // khối
            case "hoang_so", "khieu_khich" -> lv;      // hoảng sợ: giây = 1 + cấp; khiêu khích: bán kính = 4 + 2 × cấp
            case "hoa_cuu", "hoa_da" -> 0.5 + 0.5 * lv;   // giây
            case "danh_dau" -> 0.15 + 0.1 * lv;        // phần sát thương nổ lại
            case "ho_den" -> 3 + lv;                   // bán kính
            case "day_vang" -> 1.2 + 0.2 * lv;         // lực đẩy (22/10: III 2,1 → 1,8)
            case "ho_gia" -> 2.0 * lv;                 // tim vàng
            case "dong_bang" -> 1.5 + 0.5 * lv;        // giây
            case "bat_tu" -> 2 + lv;                   // giây
            case "la_chan" -> 3 + lv;                  // giây
            case "giai_khong_che" -> lv;               // giây kháng khống chế
            case "loc_xoay" -> 1 + 0.5 * lv;           // sát thương mỗi nhịp
            case "tia_sang", "phao_kich" -> 2 + 2.0 * lv;   // sát thương
            case "set_nay" -> 2 + lv;                  // sát thương; số lần nảy = 1 + cấp
            case "lien_hoan" -> 3 + lv;                // sát thương; số mục tiêu = cấp + 1
            case "rut_bai" -> lv;
            case "tang_hinh" -> 1 + 0.5 * lv;          // giây tàng hình; lướt 2 + cấp khối; đòn đầu +15 + 15 × cấp %
            case "nhay_cao" -> 0.6 + 0.3 * lv;         // lực bật lên
            case "sung_cao_su" -> 5 + 3.0 * lv;        // xa tối đa (khối); sát thương đáp đất = 2 + cấp
            case "phan_cong" -> 1 + lv;                // sát thương gốc
            default -> 3 + 2.0 * lv;            // cau_lua
        };
    }

    /** Ghi phù phép riêng lên đồ (Thư viện đồ). Trả các dòng hiện ở đầu mô tả. */
    static List<Component> apply(ItemMeta meta, Map<String, Integer> levels) {
        List<Component> lines = new ArrayList<>();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        for (String id : NAMES.keySet()) {
            Integer lv = levels.get(id);
            if (lv == null || lv < 1) {
                continue;
            }
            pdc.set(key(id), PersistentDataType.INTEGER, lv);
            lines.add(Component.text(NAMES.get(id)[0] + " " + (lv < ROMAN.length ? ROMAN[lv] : String.valueOf(lv)), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (!lines.isEmpty() && !meta.hasEnchants() && !meta.hasEnchantmentGlintOverride()) {
            meta.setEnchantmentGlintOverride(true);   // lấp lánh như đồ có phù phép
        }
        return lines;
    }

    /** Dùng kỹ năng (đã kiểm điều kiện dùng đồ ở chỗ gọi). true = đã dùng. */
    static boolean cast(Player p, ItemStack src, String id, double value) {
        long now = System.currentTimeMillis();
        String k = p.getUniqueId() + ":" + id;
        String name = NAMES.getOrDefault(id, new String[]{id})[0];
        if (silenced(p) && !id.equals("giai_khong_che")) {
            p.sendActionBar(LEGACY.deserialize("&5Đang bị Câm lặng, chưa dùng được " + name));
            return false;
        }
        Card cd = id.equals("rut_bai") ? CARDS.get(p.getUniqueId()) : null;
        if (cd != null && cd.card < 0) {
            lockCard(p, cd);   // bấm lần 2: chốt lá đang hiện
            return true;
        }
        Mark mk = id.equals("tua_thoi_gian") ? MARKS.get(p.getUniqueId()) : null;
        if (mk != null) {
            rewind(p, mk.token());   // bấm lần 2: quay về mốc ngay
            return true;
        }
        if (id.equals("sung_cao_su") && SLING.containsKey(p.getUniqueId())) {
            releaseSling(p);   // bấm lần 2: bắn
            return true;
        }
        double[] ctr = id.equals("phan_cong") ? COUNTER.get(p.getUniqueId()) : null;
        if (ctr != null) {
            if (now - ctr[3] >= 500) {
                counterBurst(p);   // bấm lại sau 0,5 giây: nổ sớm
            }
            return true;
        }
        long next = NEXT.getOrDefault(k, 0L);
        if (next > now) {
            p.sendActionBar(LEGACY.deserialize("&7" + name + " hồi trong " + ((next - now) / 1000 + 1) + " giây"));
            return false;
        }
        String ban = BanBook.enchantBlockedBy(p, src, id);
        if (ban != null) {
            p.sendActionBar(LEGACY.deserialize("&c⛔ " + name + " bị Sách cấm tắt ở " + ban + " &7(/sachcam)"));
            return false;
        }
        switch (id) {
            case "bay" -> {
                int ticks = (int) Math.round((value <= 0 ? 2 : value) * 20);
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION, ticks, 0, false, false, true));
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING, ticks + 120, 0, false, false, true));
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.8f, 1.2f);
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 16, 0.4, 0.1, 0.4, 0.02);
            }
            case "dich_chuyen" -> {
                org.bukkit.Location to = blinkTarget(p, value <= 0 ? 4 : value);
                if (to == null) {
                    p.sendActionBar(LEGACY.deserialize("&7Phía trước bị chặn, không dịch chuyển được"));
                    return false;
                }
                p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.3);
                p.teleport(to, io.papermc.paper.entity.TeleportFlag.Relative.VELOCITY_ROTATION);
                p.getWorld().playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
            }
            case "dam_dat" -> {
                double r = value <= 0 ? 3 : value;
                p.getWorld().spawnParticle(Particle.EXPLOSION, p.getLocation(), 3, r / 2, 0.2, r / 2, 0);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
                for (org.bukkit.entity.Entity en : p.getNearbyEntities(r, 2, r)) {
                    if (hiddenAdmin(p, en)) {
                        continue;   // admin đang ẩn (/v): chiêu không trúng, không lộ
                    }
                    if (en instanceof org.bukkit.entity.LivingEntity le && en != p && !PetCombat.isPet(en)) {
                        le.damage(value <= 0 ? 3 : value, p);   // qua sự kiện: Towny / PvP vẫn kiểm
                        if (!le.isDead()) {
                            le.setVelocity(le.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0).normalize().multiply(0.6).setY(0.5));
                        }
                    }
                }
            }
            case "cuong_no" -> {
                if (p.getHealth() <= 7) {
                    p.sendActionBar(LEGACY.deserialize("&cMáu quá thấp để Huyết chiến (cần hơn 3,5 tim)"));
                    return false;
                }
                p.setHealth(p.getHealth() - 6);
                RAGE.put(p.getUniqueId(), new double[]{now + 8000, 1 + (value <= 0 ? 20 : value) / 100.0});
                p.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, p.getLocation().add(0, 2, 0), 6, 0.4, 0.3, 0.4, 0);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.6f, 1.2f);
                p.sendActionBar(LEGACY.deserialize("&cHuyết chiến: &f+" + Math.round(value <= 0 ? 20 : value) + "% sát thương trong 8 giây"));
            }
            case "phong_bay" -> {
                ItemStack chest = p.getInventory().getChestplate();
                if (chest == null || chest.getType() != org.bukkit.Material.ELYTRA) {
                    return false;
                }
                double force = Math.max(1, value);
                p.setVelocity(p.getLocation().getDirection().multiply(0.8 + 0.4 * force).setY(0.9 + 0.2 * force));
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
                p.getWorld().spawnParticle(Particle.FIREWORK, p.getLocation(), 20, 0.3, 0.1, 0.3, 0.05);
                org.bukkit.Bukkit.getScheduler().runTaskLater(org.bukkit.Bukkit.getPluginManager().getPlugin("DuckyMoMoCore"), () -> {
                    if (p.isOnline() && !p.isOnGround()) {
                        p.setGliding(true);
                    }
                }, 6L);
            }
            case "cam_lang", "troi", "moc_keo" -> {
                double range = id.equals("troi") ? 15 : id.equals("moc_keo") ? 14 : 12;
                org.bukkit.entity.LivingEntity t = aim(p, range, false);
                if (t == null) {
                    p.sendActionBar(LEGACY.deserialize("&7Không trúng ai (nhắm vào sinh vật trong " + (int) range + " khối)"));
                    return false;
                }
                if (!strike(p, t, id.equals("moc_keo") ? 2 : 1)) {
                    p.sendActionBar(LEGACY.deserialize("&7" + name + " không có tác dụng ở đây (khu an toàn / không được đánh)"));
                    return false;
                }
                beam(p, t, id.equals("cam_lang") ? Particle.WITCH : id.equals("troi") ? Particle.SCULK_SOUL : Particle.CRIT);
                switch (id) {
                    case "cam_lang" -> {
                        if (t instanceof Player tp && !ccImmune(tp)) {
                            SILENCE.put(tp.getUniqueId(), now + Math.round(value * 1000));
                            tp.sendActionBar(LEGACY.deserialize("&5Bị Câm lặng " + fmtSec(value) + " giây: không dùng được phù phép riêng, kỹ năng Xưởng"));
                        }
                        t.getWorld().playSound(t.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.8f, 1.4f);
                    }
                    case "troi" -> {
                        root(t, (int) Math.round(value * 20));
                        t.getWorld().playSound(t.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1f, 0.8f);
                    }
                    default -> {   // moc_keo
                        org.bukkit.util.Vector d = p.getLocation().toVector().subtract(t.getLocation().toVector());
                        double dist = d.length();
                        if (dist > 1.5) {
                            t.setVelocity(d.normalize().multiply(Math.min(2.6, 0.45 + dist * 0.17 * (0.8 + 0.1 * value))).setY(0.35));
                        }
                        t.getWorld().playSound(t.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 0.8f);
                    }
                }
            }
            case "tua_thoi_gian" -> {
                long token = now;
                MARKS.put(p.getUniqueId(), new Mark(p.getLocation(), p.getHealth(), token, value));
                p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.02);
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.8f);
                p.sendActionBar(LEGACY.deserialize("&bĐã đặt mốc thời gian: bấm lại trong 4 giây (hoặc chờ) để quay về"));
                org.bukkit.Bukkit.getScheduler().runTaskLater(pl(), () -> rewind(p, token), 80L);
                return true;   // hồi chiêu tính từ lúc quay về
            }
            case "khien_phep" -> {
                org.bukkit.entity.LivingEntity t = aim(p, 10, true);
                Player who = t instanceof Player tp ? tp : p;
                int amp = Math.max(0, (int) Math.round(value / 2) - 1);   // mỗi cấp absorption = 2 tim
                who.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.ABSORPTION, 120, amp, false, false, true));
                who.getWorld().spawnParticle(Particle.END_ROD, who.getLocation().add(0, 1, 0), 24, 0.4, 0.6, 0.4, 0.02);
                who.getWorld().playSound(who.getLocation(), Sound.ITEM_ARMOR_EQUIP_DIAMOND, 1f, 1.4f);
                if (who != p) {
                    beam(p, who, Particle.END_ROD);
                    who.sendActionBar(LEGACY.deserialize("&e" + p.getName() + " &fcho bạn &e" + fmtSec(value) + " tim vàng &f(6 giây)"));
                }
            }
            case "hoi_mau_dong_doi" -> {
                org.bukkit.entity.LivingEntity t = aim(p, 15, true);
                if (!(t instanceof Player tp)) {
                    p.sendActionBar(LEGACY.deserialize("&7Nhìn vào người cần hồi máu (trong 15 khối)"));
                    return false;
                }
                var attr = tp.getAttribute(Attribute.MAX_HEALTH);
                double max = attr == null ? 20 : attr.getValue();
                if (tp.getHealth() >= max) {
                    p.sendActionBar(LEGACY.deserialize("&7" + tp.getName() + " đang đầy máu"));
                    return false;
                }
                tp.setHealth(Math.min(max, tp.getHealth() + value));
                beam(p, tp, Particle.HAPPY_VILLAGER);
                tp.getWorld().spawnParticle(Particle.HEART, tp.getLocation().add(0, 2, 0), 5, 0.4, 0.3, 0.4, 0);
                tp.getWorld().playSound(tp.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
                tp.sendActionBar(LEGACY.deserialize("&a" + p.getName() + " &fhồi cho bạn &a" + fmtSec(value / 2) + " tim"));
            }
            case "chan_dan" -> {
                long until = now + Math.round(value * 1000);
                WALL.put(p.getUniqueId(), until);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 0.8f, 1.2f);
                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!p.isOnline() || System.currentTimeMillis() > until || !Long.valueOf(until).equals(WALL.get(p.getUniqueId()))) {
                            cancel();
                            return;
                        }
                        org.bukkit.Location c = p.getEyeLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(1.2));
                        p.getWorld().spawnParticle(Particle.CLOUD, c, 6, 0.6, 0.5, 0.6, 0);
                    }
                }.runTaskTimer(pl(), 0L, 5L);
            }
            case "soi_sang" -> {
                int n = 0;
                for (Player o : p.getWorld().getPlayers()) {
                    if (o != p && o.getLocation().distanceSquared(p.getLocation()) <= value * value
                            && o.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                        if (STEALTH.containsKey(o.getUniqueId())) {
                            reveal(o);   // Soi sáng phá Tàng hình (28/10)
                            o.sendActionBar(LEGACY.deserialize("&eBị Soi sáng của " + p.getName() + " làm lộ diện"));
                        } else if (!p.canSee(o)) {
                            continue;    // admin đang ẩn (/v): không làm sáng, không đếm (không lộ có người)
                        }
                        o.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.GLOWING, 100, 0, false, false, true));
                        n++;
                    }
                }
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.8f, 1.4f);
                p.getWorld().spawnParticle(Particle.END_ROD, p.getEyeLocation(), 40, 1.5, 1, 1.5, 0.05);
                p.sendActionBar(LEGACY.deserialize("&eSoi sáng: &f" + n + " người trong " + (int) value + " khối"));
            }
            case "hoang_so", "hoa_cuu", "danh_dau", "day_vang" -> {
                double range = id.equals("day_vang") ? 10 : 12;
                org.bukkit.entity.LivingEntity t = aim(p, range, false);
                if (t == null) {
                    p.sendActionBar(LEGACY.deserialize("&7Không trúng ai (nhắm vào sinh vật trong " + (int) range + " khối)"));
                    return false;
                }
                if (!strike(p, t, 1)) {
                    p.sendActionBar(LEGACY.deserialize("&7" + name + " không có tác dụng ở đây (khu an toàn / không được đánh / bị chặn)"));
                    return false;
                }
                switch (id) {
                    case "hoang_so" -> {
                        beam(p, t, Particle.SMOKE);
                        fear(p, t, (int) Math.round((1 + value) * 20));
                    }
                    case "hoa_cuu" -> {
                        beam(p, t, Particle.WITCH);
                        if (!ccImmune(t)) {
                            DISARM.put(t.getUniqueId(), now + Math.round(value * 1000));
                            t.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, (int) Math.round(value * 20), 1, false, false, true));
                            t.getWorld().spawnParticle(Particle.CLOUD, t.getLocation().add(0, 1, 0), 20, 0.4, 0.5, 0.4, 0.02);
                            t.getWorld().playSound(t.getLocation(), Sound.ENTITY_SHEEP_AMBIENT, 1f, 1.2f);
                            if (t instanceof Player tp) {
                                tp.sendActionBar(LEGACY.deserialize("&dBị Hoá cừu " + fmtSec(value) + " giây: không gây được sát thương"));
                            }
                        }
                    }
                    case "danh_dau" -> {
                        beam(p, t, Particle.DAMAGE_INDICATOR);
                        DEATH.put(t.getUniqueId(), new DeathMark(p.getUniqueId(), now + 3000, value));
                        t.getWorld().playSound(t.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.5f, 1.6f);
                        UUID tid = t.getUniqueId();
                        new org.bukkit.scheduler.BukkitRunnable() {
                            int n;

                            @Override
                            public void run() {
                                DeathMark d = DEATH.get(tid);
                                if (d == null || !t.isValid()) {
                                    DEATH.remove(tid);
                                    cancel();
                                    return;
                                }
                                if (++n < 6) {
                                    t.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, t.getLocation().add(0, t.getHeight() + 0.3, 0), 3, 0.2, 0.1, 0.2, 0);
                                    return;
                                }
                                DEATH.remove(tid);
                                cancel();
                                double dmg = d.sum * d.pct;
                                if (dmg >= 0.5 && !t.isDead()) {
                                    t.setNoDamageTicks(0);
                                    if (p.isOnline()) {
                                        t.damage(dmg, p);
                                    } else {
                                        t.damage(dmg);
                                    }
                                    t.getWorld().spawnParticle(Particle.EXPLOSION, t.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0);
                                    t.getWorld().playSound(t.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);
                                }
                            }
                        }.runTaskTimer(pl(), 10L, 10L);
                    }
                    default -> {   // day_vang
                        org.bukkit.util.Vector dir = p.getLocation().getDirection().setY(0);
                        if (dir.lengthSquared() < 1e-4) {
                            dir = t.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                        }
                        org.bukkit.util.Vector fdir = dir.normalize();
                        t.setVelocity(fdir.clone().multiply(value).setY(0.3));
                        t.getWorld().playSound(t.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.7f);
                        new org.bukkit.scheduler.BukkitRunnable() {
                            int n;

                            @Override
                            public void run() {
                                if (++n > 14 || !t.isValid()) {
                                    cancel();
                                    return;
                                }
                                org.bukkit.block.Block ahead = t.getLocation().add(fdir.clone().multiply(0.8)).add(0, 1, 0).getBlock();
                                if (n > 1 && !ahead.isPassable()) {
                                    cancel();
                                    root(t, 20);
                                    t.setNoDamageTicks(0);
                                    t.damage(2, p);
                                    t.getWorld().playSound(t.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1f, 0.8f);
                                    t.getWorld().spawnParticle(Particle.CRIT, t.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.1);
                                }
                            }
                        }.runTaskTimer(pl(), 1L, 1L);
                    }
                }
            }
            case "hoa_da" -> {
                org.bukkit.util.Vector face = p.getLocation().getDirection().setY(0);
                if (face.lengthSquared() < 1e-4) {
                    return false;
                }
                face.normalize();
                int hit = 0;
                for (org.bukkit.entity.Entity en : p.getNearbyEntities(8, 4, 8)) {
                    if (hiddenAdmin(p, en)) {
                        continue;   // admin đang ẩn (/v): chiêu không trúng, không lộ
                    }
                    if (!(en instanceof org.bukkit.entity.LivingEntity t) || en instanceof org.bukkit.entity.ArmorStand) {
                        continue;
                    }
                    org.bukkit.util.Vector to = t.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                    if (to.lengthSquared() < 1e-4 || face.dot(to.clone().normalize()) < 0.5 || !strike(p, t, 0.5)) {
                        continue;
                    }
                    hit++;
                    boolean looking = !(t instanceof Player) || t.getLocation().getDirection().setY(0).normalize().dot(to.clone().normalize().multiply(-1)) > 0.5;
                    if (looking) {
                        root(t, (int) Math.round(value * 20));
                        t.getWorld().spawnParticle(Particle.BLOCK, t.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, org.bukkit.Material.STONE.createBlockData());
                    } else {
                        t.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 40, 1, false, false, true));
                    }
                }
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 1.6f);
                p.getWorld().spawnParticle(Particle.WITCH, p.getEyeLocation().add(face.clone().multiply(2)), 20, 1.2, 0.4, 1.2, 0);
                p.sendActionBar(LEGACY.deserialize("&7Ánh mắt hoá đá trúng &f" + hit));
            }
            case "khieu_khich" -> {
                double r = 4 + 2 * value;
                int n = 0;
                for (org.bukkit.entity.Entity en : p.getNearbyEntities(r, r / 2, r)) {
                    if (hiddenAdmin(p, en)) {
                        continue;   // admin đang ẩn (/v): chiêu không trúng, không lộ
                    }
                    if (en instanceof org.bukkit.entity.Mob m && en instanceof org.bukkit.entity.Enemy && !PetCombat.isPet(en)) {
                        m.setTarget(p);
                        n++;
                    }
                }
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.7f, 1.3f);
                p.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, p.getLocation().add(0, 2.2, 0), 5, 0.4, 0.2, 0.4, 0);
                p.sendActionBar(LEGACY.deserialize("&6Khiêu khích: &f" + n + " quái quay sang đánh bạn"));
            }
            case "ho_den" -> {
                org.bukkit.Location center = aimPoint(p, 15);
                blackHole(p, center, value);
            }
            case "ho_gia" -> {
                org.bukkit.entity.LivingEntity t = aim(p, 40, true);
                if (!(t instanceof Player tp)) {
                    p.sendActionBar(LEGACY.deserialize("&7Nhìn vào đồng đội cần bảo vệ (trong 40 khối)"));
                    return false;
                }
                if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("Towny") && !TownyAllies.allies(p.getUniqueId(), tp.getUniqueId())) {
                    p.sendActionBar(LEGACY.deserialize("&7Chỉ hộ giá người cùng thị trấn, quốc gia hoặc quốc gia đồng minh"));
                    return false;
                }
                int amp = Math.max(0, (int) Math.round(value / 2) - 1);
                tp.sendActionBar(LEGACY.deserialize("&e" + p.getName() + " &fđang tới hộ giá bạn"));
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.4f);
                new org.bukkit.scheduler.BukkitRunnable() {
                    int n;

                    @Override
                    public void run() {
                        if (!p.isOnline() || p.isDead() || !tp.isOnline() || tp.isDead() || tp.getWorld() != p.getWorld()) {
                            cancel();
                            return;
                        }
                        if (++n < 6) {
                            p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 6, 0.3, 0.6, 0.3, 0.02);
                            tp.getWorld().spawnParticle(Particle.END_ROD, tp.getLocation().add(0, 1, 0), 6, 0.3, 0.6, 0.3, 0.02);
                            return;
                        }
                        cancel();
                        org.bukkit.Location to = tp.getLocation();
                        to.setYaw(p.getLocation().getYaw());
                        to.setPitch(p.getLocation().getPitch());
                        p.teleport(to);
                        for (Player x : List.of(p, tp)) {
                            x.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.ABSORPTION, 120, amp, false, false, true));
                        }
                        p.getWorld().playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1f);
                    }
                }.runTaskTimer(pl(), 5L, 5L);
            }
            case "dong_bang" -> {
                long until = now + Math.round(value * 1000);
                INVULN.put(p.getUniqueId(), until);
                DISARM.put(p.getUniqueId(), until);
                rootSelf(p, (int) Math.round(value * 20));
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_GLASS_PLACE, 1f, 0.6f);
                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!p.isOnline() || System.currentTimeMillis() > until) {
                            cancel();
                            if (p.isOnline() && !p.isDead()) {
                                var attr = p.getAttribute(Attribute.MAX_HEALTH);
                                p.setHealth(Math.min(attr == null ? 20 : attr.getValue(), p.getHealth() + 2));
                                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1.2f);
                            }
                            return;
                        }
                        p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1, 0), 12, 0.4, 0.8, 0.4, 0);
                    }
                }.runTaskTimer(pl(), 0L, 5L);
            }
            case "bat_tu" -> {
                UNDYING.put(p.getUniqueId(), now + Math.round(value * 1000));
                p.getWorld().playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.6f, 1.4f);
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.2);
                p.sendActionBar(LEGACY.deserialize("&cBất tử &f" + fmtSec(value) + " giây"));
            }
            case "la_chan" -> {
                SPELL_SHIELD.put(p.getUniqueId(), now + Math.round(value * 1000));
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.4f);
                p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.5);
                p.sendActionBar(LEGACY.deserialize("&bLá chắn phép &f" + fmtSec(value) + " giây"));
            }
            case "giai_khong_che" -> {
                SILENCE.remove(p.getUniqueId());
                DISARM.remove(p.getUniqueId());
                unroot(p);
                for (org.bukkit.potion.PotionEffectType t : List.of(org.bukkit.potion.PotionEffectType.SLOWNESS, org.bukkit.potion.PotionEffectType.WEAKNESS,
                        org.bukkit.potion.PotionEffectType.POISON, org.bukkit.potion.PotionEffectType.WITHER, org.bukkit.potion.PotionEffectType.BLINDNESS,
                        org.bukkit.potion.PotionEffectType.NAUSEA, org.bukkit.potion.PotionEffectType.MINING_FATIGUE, org.bukkit.potion.PotionEffectType.DARKNESS,
                        org.bukkit.potion.PotionEffectType.HUNGER)) {
                    p.removePotionEffect(t);
                }
                CC_IMMUNE.put(p.getUniqueId(), now + Math.round(value * 1000));
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1f, 1.4f);
                p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 20, 0.4, 0.8, 0.4, 0.05);
                p.sendActionBar(LEGACY.deserialize("&aĐã giải khống chế &7(kháng khống chế " + fmtSec(value) + " giây)"));
            }
            case "loc_xoay" -> {
                p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1.2f);
                new org.bukkit.scheduler.BukkitRunnable() {
                    int n;

                    @Override
                    public void run() {
                        if (!p.isOnline() || p.isDead() || ++n > 6) {
                            cancel();
                            return;
                        }
                        p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p.getLocation().add(0, 1, 0), 6, 1.5, 0.2, 1.5, 0);
                        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1f);
                        for (org.bukkit.entity.Entity en : p.getNearbyEntities(3, 1.5, 3)) {
                            if (hiddenAdmin(p, en)) {
                                continue;   // admin đang ẩn (/v): chiêu không trúng, không lộ
                            }
                            if (en instanceof org.bukkit.entity.LivingEntity le && !PetCombat.isPet(en) && !(en instanceof org.bukkit.entity.ArmorStand)) {
                                org.bukkit.util.Vector v = le.getVelocity();
                                le.setNoDamageTicks(0);
                                le.damage(value, p);
                                le.setVelocity(v);   // không đánh bật ra khỏi vòng xoay (test bot: bật ra sau 2 nhịp)
                            }
                        }
                    }
                }.runTaskTimer(pl(), 0L, 10L);
            }
            case "tia_sang" -> {
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
                p.getWorld().spawnParticle(Particle.END_ROD, p.getEyeLocation(), 15, 0.3, 0.3, 0.3, 0.02);
                org.bukkit.Bukkit.getScheduler().runTaskLater(pl(), () -> beamDamage(p, 20, value), 10L);
            }
            case "phao_kich" -> {
                org.bukkit.Location at = groundPoint(p, 30);
                if (at == null) {
                    p.sendActionBar(LEGACY.deserialize("&7Chỉ vào mặt đất trong 30 khối"));
                    return false;
                }
                artillery(p, at, value);
            }
            case "set_nay" -> {
                org.bukkit.entity.LivingEntity t = aim(p, 15, false);
                if (t == null) {
                    p.sendActionBar(LEGACY.deserialize("&7Không trúng ai (nhắm vào sinh vật trong 15 khối)"));
                    return false;
                }
                chain(p, t, value, (int) Math.round(value) - 1);
            }
            case "lien_hoan" -> {
                List<org.bukkit.entity.LivingEntity> targets = new ArrayList<>();
                for (org.bukkit.entity.Entity en : p.getNearbyEntities(8, 4, 8)) {
                    if (hiddenAdmin(p, en)) {
                        continue;   // admin đang ẩn (/v): chiêu không trúng, không lộ
                    }
                    if (en instanceof org.bukkit.entity.LivingEntity le && !PetCombat.isPet(en) && !(en instanceof org.bukkit.entity.ArmorStand)
                            && !(en instanceof Player o && o.getGameMode() == org.bukkit.GameMode.SPECTATOR)) {
                        targets.add(le);
                    }
                }
                if (targets.isEmpty()) {
                    p.sendActionBar(LEGACY.deserialize("&7Không có ai trong 8 khối"));
                    return false;
                }
                targets.sort(java.util.Comparator.comparingDouble(t -> t.getLocation().distanceSquared(p.getLocation())));
                flurry(p, targets.subList(0, Math.min(targets.size(), (int) Math.round(value) - 2)), value);
            }
            case "rut_bai" -> {
                Card c = new Card();
                c.start = now;
                c.value = value;
                CARDS.put(p.getUniqueId(), c);
                p.getWorld().playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.2f);
                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        if (CARDS.get(p.getUniqueId()) != c || c.card >= 0 || !p.isOnline()) {
                            cancel();
                            return;
                        }
                        if (System.currentTimeMillis() - c.start > 4000) {
                            cancel();
                            lockCard(p, c);   // hết 4 giây: tự chốt lá đang hiện
                            return;
                        }
                        c.idx = (c.idx + 1) % 3;
                        StringBuilder sb = new StringBuilder("&7Rút bài: ");
                        for (int i = 0; i < 3; i++) {
                            sb.append(i == c.idx ? "&f[" + CARD_NAMES[i] + "&f] " : "&8" + CARD_NAMES[i].replaceAll("&.", "") + " ");
                        }
                        p.sendActionBar(LEGACY.deserialize(sb.append("&7· bấm lại để chốt").toString()));
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.6f);
                    }
                }.runTaskTimer(pl(), 0L, 8L);
                return true;   // hồi chiêu tính từ lúc chốt
            }
            case "khong_lo", "thu_nho" -> {
                double scale = id.equals("khong_lo") ? Math.max(1.1, Math.min(3, value)) : Math.max(0.4, Math.min(0.9, value));
                if (!roomFor(p, scale)) {
                    p.sendActionBar(LEGACY.deserialize("&7Không đủ chỗ trống phía trên để hoá Khổng lồ"));
                    return false;
                }
                setScale(p, scale);
                p.getWorld().playSound(p.getLocation(), id.equals("khong_lo") ? Sound.ENTITY_IRON_GOLEM_REPAIR : Sound.ENTITY_PUFFER_FISH_BLOW_OUT, 0.8f, 1f);
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 20, 0.4 * scale, 0.6 * scale, 0.4 * scale, 0.02);
                long token = now;
                SIZE_TOKEN.put(p.getUniqueId(), token);
                org.bukkit.Bukkit.getScheduler().runTaskLater(org.bukkit.Bukkit.getPluginManager().getPlugin("DuckyMoMoCore"),
                        () -> endScale(p, token, 0), SIZE_MS / 50);
            }
            case "tang_hinh" -> {
                int lv = (int) Math.round((value - 1) * 2);
                p.getWorld().spawnParticle(Particle.LARGE_SMOKE, p.getLocation().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.02);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 1.2f);
                org.bukkit.Location to = blinkTarget(p, 2 + lv);
                if (to != null) {
                    p.teleport(to, io.papermc.paper.entity.TeleportFlag.Relative.VELOCITY_ROTATION);
                }
                long dur = Math.round(value * 1000);
                STEALTH.put(p.getUniqueId(), new double[]{now + dur, 15 + 15 * lv});
                for (Player o : org.bukkit.Bukkit.getOnlinePlayers()) {
                    if (o != p) {
                        o.hideEntity(pl(), p);   // vô hình hẳn (cả giáp, tên), khác thuốc tàng hình
                    }
                }
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY, (int) (dur / 50) + 2, 0, false, false, true));
                for (org.bukkit.entity.Entity en : p.getNearbyEntities(32, 16, 32)) {
                    if (hiddenAdmin(p, en)) {
                        continue;   // admin đang ẩn (/v): chiêu không trúng, không lộ
                    }
                    if (en instanceof org.bukkit.entity.Mob m && p.equals(m.getTarget())) {
                        m.setTarget(null);
                    }
                }
                org.bukkit.Bukkit.getScheduler().runTaskLater(pl(), () -> {
                    double[] st = STEALTH.get(p.getUniqueId());
                    if (st != null && st[0] <= System.currentTimeMillis() + 60) {
                        reveal(p);
                    }
                }, dur / 50 + 1);
                p.sendActionBar(LEGACY.deserialize("&8Tàng hình &7" + fmtSec(value) + " giây · đòn đầu tiên &e+" + (15 + 15 * lv) + "% sát thương"));
            }
            case "nhay_cao" -> {
                if (!p.isOnGround()) {
                    p.sendActionBar(LEGACY.deserialize("&7Phải đứng trên mặt đất mới Nhảy cao được"));
                    return false;
                }
                org.bukkit.util.Vector dir = p.getLocation().getDirection().setY(0);
                dir = dir.lengthSquared() > 0 ? dir.normalize().multiply(0.25) : dir;
                p.setVelocity(dir.setY(value <= 0 ? 1.2 : value));
                NOFALL.put(p.getUniqueId(), now + 8000);
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 20, 0.4, 0.1, 0.4, 0.05);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1f, 0.8f);
            }
            case "sung_cao_su" -> {
                if (!p.isOnGround()) {
                    p.sendActionBar(LEGACY.deserialize("&7Phải đứng trên mặt đất mới kéo Súng cao su được"));
                    return false;
                }
                int lv = (int) Math.round((value - 5) / 3);
                UUID u = p.getUniqueId();
                double[] sl = {now, value, 2 + lv};
                SLING.put(u, sl);
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 50, 5, false, false, false));
                p.getWorld().playSound(p.getLocation(), Sound.ITEM_CROSSBOW_LOADING_START, 1f, 0.8f);
                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        if (SLING.get(u) != sl || !p.isOnline() || p.isDead()) {
                            if (SLING.get(u) == sl) {
                                SLING.remove(u);
                            }
                            cancel();
                            return;
                        }
                        long held = System.currentTimeMillis() - (long) sl[0];
                        double frac = Math.min(1, held / 1200.0);
                        int bars = (int) Math.round(frac * 10);
                        p.sendActionBar(LEGACY.deserialize("&aSúng cao su &8[" + (frac >= 1 ? "&e" : "&a") + "▮".repeat(bars) + "&7" + "▮".repeat(10 - bars)
                                + "&8] &7bấm lần nữa để bật"));
                        if (held >= 2200) {
                            releaseSling(p);   // giữ quá lâu: tự bật
                            cancel();
                        }
                    }
                }.runTaskTimer(pl(), 2L, 2L);
            }
            case "phan_cong" -> {
                UUID u = p.getUniqueId();
                double[] c = {now + 2000, value <= 0 ? 3 : value, 0, now};
                COUNTER.put(u, c);
                p.getWorld().spawnParticle(Particle.ENCHANTED_HIT, p.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.2);
                p.getWorld().playSound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.7f);
                p.sendActionBar(LEGACY.deserialize("&6Phản công: &fné mọi đòn 2 giây, rồi choáng kẻ địch quanh mình &7(bấm lại để nổ sớm)"));
                org.bukkit.Bukkit.getScheduler().runTaskLater(pl(), () -> {
                    if (COUNTER.get(u) == c) {
                        counterBurst(p);
                    }
                }, 40L);
            }
            case "luot" -> {
                double force = Math.max(1, Math.min(3, value <= 0 ? 1.5 : value));
                p.setVelocity(p.getLocation().getDirection().setY(0).normalize().multiply(0.9 + 0.6 * force).setY(0.25));
                p.setFallDistance(0);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 0.8f, 1.3f);
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.2, 0), 12, 0.3, 0.1, 0.3, 0.02);
            }
            case "hoi_mau" -> {
                var attr = p.getAttribute(Attribute.MAX_HEALTH);
                double max = attr == null ? 20 : attr.getValue();
                if (p.getHealth() >= max) {
                    p.sendActionBar(LEGACY.deserialize("&7Máu đang đầy"));
                    return false;
                }
                p.setHealth(Math.min(max, p.getHealth() + (value <= 0 ? 6 : value)));
                p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 6, 0.4, 0.3, 0.4, 0);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.8f);
            }
            case "cau_lua" -> {
                SmallFireball f = p.launchProjectile(SmallFireball.class, p.getLocation().getDirection().multiply(1.4));
                f.setIsIncendiary(false);   // không đốt khối (đất người khác, rừng)
                f.getPersistentDataContainer().set(FIRE, PersistentDataType.DOUBLE, value <= 0 ? 5 : value);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.8f, 1f);
            }
            default -> {
                return false;
            }
        }
        NEXT.put(k, now + COOLDOWN_MS.getOrDefault(id, 3000L));
        return true;
    }

    /** Tàng hình hết giờ / ra đòn: hiện lại với mọi người. */
    private static void reveal(Player p) {
        if (STEALTH.remove(p.getUniqueId()) == null) {
            return;
        }
        for (Player o : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (o != p) {
                o.showEntity(pl(), p);
            }
        }
        var inv = p.getPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
        if (inv != null && inv.getDuration() <= 60) {
            p.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);   // chỉ gỡ hiệu ứng của chiêu, không gỡ thuốc uống
        }
        p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation().add(0, 1, 0), 15, 0.3, 0.6, 0.3, 0.02);
    }

    /** Súng cao su: bắn mình theo hướng nhìn, xa theo độ căng; đáp đất hất tung quanh 3 khối. */
    private static void releaseSling(Player p) {
        double[] sl = SLING.remove(p.getUniqueId());
        if (sl == null) {
            return;
        }
        double frac = Math.min(1, (System.currentTimeMillis() - (long) sl[0]) / 1200.0);
        double dist = Math.max(3, sl[1] * frac);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
        org.bukkit.util.Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() == 0) {
            dir = new org.bukkit.util.Vector(0, 0, 1);
        }
        p.setVelocity(dir.normalize().multiply(dist / 8.5).setY(0.6 + 0.2 * frac));
        NOFALL.put(p.getUniqueId(), System.currentTimeMillis() + 6000);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 0.7f);
        p.getWorld().spawnParticle(Particle.ITEM_SLIME, p.getLocation(), 20, 0.4, 0.2, 0.4, 0.05);
        double dmg = sl[2];
        new org.bukkit.scheduler.BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                if ((t > 3 && p.isOnGround()) || t > 60) {
                    cancel();
                    org.bukkit.Location at = p.getLocation();
                    at.getWorld().spawnParticle(Particle.EXPLOSION, at, 2, 1, 0.2, 1, 0);
                    at.getWorld().spawnParticle(Particle.ITEM_SLIME, at, 40, 1.5, 0.3, 1.5, 0.1);
                    at.getWorld().playSound(at, Sound.ENTITY_SLIME_SQUISH, 1.2f, 0.6f);
                    for (org.bukkit.entity.Entity en : p.getNearbyEntities(3, 2, 3)) {
                        if (hiddenAdmin(p, en)) {
                            continue;   // admin đang ẩn (/v): chiêu không trúng, không lộ
                        }
                        if (en instanceof org.bukkit.entity.LivingEntity le && !(en instanceof org.bukkit.entity.ArmorStand) && !PetCombat.isPet(en)
                                && strike(p, le, dmg) && !le.isDead()) {
                            le.setVelocity(le.getLocation().toVector().subtract(at.toVector()).setY(0).normalize().multiply(0.4).setY(0.6));
                        }
                    }
                }
            }
        }.runTaskTimer(pl(), 1L, 1L);
    }

    /** Phản công: choáng 1 giây + sát thương quanh 3 khối, mạnh thêm theo số đòn đã né. */
    private static void counterBurst(Player p) {
        double[] c = COUNTER.remove(p.getUniqueId());
        if (c == null || !p.isOnline() || p.isDead()) {
            return;
        }
        int dodged = (int) c[2];
        double dmg = c[1] * (1 + 0.2 * Math.min(5, dodged));
        org.bukkit.Location at = p.getLocation();
        at.getWorld().spawnParticle(Particle.SWEEP_ATTACK, at.clone().add(0, 1, 0), 12, 1.5, 0.3, 1.5, 0);
        at.getWorld().spawnParticle(Particle.CRIT, at.clone().add(0, 1, 0), 30, 1.5, 0.5, 1.5, 0.2);
        at.getWorld().playSound(at, Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.8f);
        int hit = 0;
        for (org.bukkit.entity.Entity en : p.getNearbyEntities(3, 2, 3)) {
            if (hiddenAdmin(p, en)) {
                continue;   // admin đang ẩn (/v): chiêu không trúng, không lộ
            }
            if (en instanceof org.bukkit.entity.LivingEntity le && !(en instanceof org.bukkit.entity.ArmorStand) && !PetCombat.isPet(en) && strike(p, le, dmg)) {
                if (!le.isDead()) {
                    root(le, 20);
                }
                hit++;
            }
        }
        p.sendActionBar(LEGACY.deserialize("&6Phản công: &fné " + dodged + " đòn · " + hit + " kẻ bị choáng"
                + (dodged > 0 ? " &7(+" + Math.min(5, dodged) * 20 + "% sát thương)" : "")));
    }

    private static org.bukkit.plugin.Plugin pl() {
        return org.bukkit.Bukkit.getPluginManager().getPlugin("DuckyMoMoCore");
    }

    private static String fmtSec(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v).replace('.', ',');
    }

    /** Đang bị Câm lặng (Workshop cũng hỏi để tắt kỹ năng Xưởng). */
    static boolean silenced(Player p) {
        Long until = SILENCE.get(p.getUniqueId());
        if (until == null) {
            return false;
        }
        if (until < System.currentTimeMillis()) {
            SILENCE.remove(p.getUniqueId());
            return false;
        }
        return true;
    }

    /** Sinh vật đầu tiên trên tia nhìn (không xuyên khối). onlyPlayers: chỉ người chơi. */
    private static org.bukkit.entity.LivingEntity aim(Player p, double range, boolean onlyPlayers) {
        org.bukkit.Location eye = p.getEyeLocation();
        var hit = p.getWorld().rayTrace(eye, eye.getDirection(), range, org.bukkit.FluidCollisionMode.NEVER, true, 0.35,
                e -> e != p && e instanceof org.bukkit.entity.LivingEntity && !(e instanceof org.bukkit.entity.ArmorStand)
                        && (!onlyPlayers || e instanceof Player) && !(e instanceof Player o && o.getGameMode() == org.bukkit.GameMode.SPECTATOR)
                        && !(e instanceof Player o && !p.canSee(o)));   // admin /v, người đang Tàng hình: không nhắm trúng
        return hit != null && hit.getHitEntity() instanceof org.bukkit.entity.LivingEntity le ? le : null;
    }

    /** Đòn thử: gây dmg sát thương thật (qua EntityDamageByEntityEvent). false = bị Towny / PvP / vùng an toàn / Đấu Trường chặn. */
    /** Admin đang ẩn (/v của Essentials ẩn bằng hidePlayer): mọi chiêu bỏ qua. Người đang Tàng hình (chiêu) không tính: vẫn dính chiêu vùng. */
    private static boolean hiddenAdmin(Player p, org.bukkit.entity.Entity e) {
        return e instanceof Player o && o != p && !p.canSee(o) && !STEALTH.containsKey(o.getUniqueId());
    }

    private static boolean strike(Player p, org.bukkit.entity.LivingEntity t, double dmg) {
        if (hiddenAdmin(p, t)) {
            return false;
        }
        if (t instanceof Player tp && active(SPELL_SHIELD, tp)) {
            SPELL_SHIELD.remove(tp.getUniqueId());   // Lá chắn phép: chặn chiêu này, hồi 1 tim
            var attr = tp.getAttribute(Attribute.MAX_HEALTH);
            tp.setHealth(Math.min(attr == null ? 20 : attr.getValue(), tp.getHealth() + 2));
            tp.getWorld().spawnParticle(Particle.ENCHANT, tp.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.5);
            tp.getWorld().playSound(tp.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1.4f);
            tp.sendActionBar(LEGACY.deserialize("&bLá chắn phép đã chặn chiêu của " + p.getName()));
            p.sendActionBar(LEGACY.deserialize("&7Bị Lá chắn phép của " + tp.getName() + " chặn"));
            return false;
        }
        t.setNoDamageTicks(0);   // đang trong khoảng miễn sát thương thì game bỏ qua cả sự kiện
        probeFrom = p;
        probeTo = t;
        probeOk = false;
        try {
            t.damage(dmg, p);
        } finally {
            probeFrom = null;
            probeTo = null;
        }
        return probeOk;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProbe(EntityDamageByEntityEvent e) {
        if (probeTo != null && e.getEntity() == probeTo && e.getDamager() == probeFrom) {
            probeOk = true;
        }
    }

    /** Vệt hạt từ mắt người dùng tới mục tiêu. */
    private static void beam(Player p, org.bukkit.entity.Entity t, Particle particle) {
        org.bukkit.Location a = p.getEyeLocation();
        org.bukkit.util.Vector d = t.getLocation().add(0, t.getHeight() / 2, 0).toVector().subtract(a.toVector());
        double len = d.length();
        d.normalize();
        for (double i = 0.8; i < len; i += 0.5) {
            p.getWorld().spawnParticle(particle, a.clone().add(d.clone().multiply(i)), 1, 0, 0, 0, 0);
        }
    }

    /** Trói: tốc chạy + sức nhảy nhân 0 trong ticks (modifier tạm, lần trói sau thay lần trước). */
    private static void root(org.bukkit.entity.LivingEntity t, int ticks) {
        if (ccImmune(t)) {
            return;
        }
        long token = System.nanoTime();
        ROOT_TOKEN.put(t.getUniqueId(), token);
        for (Attribute a : List.of(Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH)) {
            var ai = t.getAttribute(a);
            if (ai != null) {
                ai.removeModifier(ROOT);
                ai.addTransientModifier(new org.bukkit.attribute.AttributeModifier(ROOT, -1,
                        org.bukkit.attribute.AttributeModifier.Operation.MULTIPLY_SCALAR_1, org.bukkit.inventory.EquipmentSlotGroup.ANY));
            }
        }
        t.setVelocity(new org.bukkit.util.Vector(0, Math.min(0, t.getVelocity().getY()), 0));
        if (t instanceof Player tp) {
            tp.sendActionBar(LEGACY.deserialize("&7Bị Trói " + fmtSec(ticks / 20.0) + " giây"));
        }
        org.bukkit.Bukkit.getScheduler().runTaskLater(pl(), () -> {
            if (!Long.valueOf(token).equals(ROOT_TOKEN.get(t.getUniqueId()))) {
                return;
            }
            ROOT_TOKEN.remove(t.getUniqueId());
            for (Attribute a : List.of(Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH)) {
                var ai = t.getAttribute(a);
                if (ai != null) {
                    ai.removeModifier(ROOT);
                }
            }
        }, ticks);
    }

    private static boolean ccImmune(org.bukkit.entity.Entity t) {
        Long u = CC_IMMUNE.get(t.getUniqueId());
        return u != null && u > System.currentTimeMillis();
    }

    private static boolean active(Map<UUID, Long> m, org.bukkit.entity.Entity e) {
        Long u = m.get(e.getUniqueId());
        if (u == null) {
            return false;
        }
        if (u < System.currentTimeMillis()) {
            m.remove(e.getUniqueId());
            return false;
        }
        return true;
    }

    /** Gỡ Trói (Giải khống chế). */
    private static void unroot(org.bukkit.entity.LivingEntity t) {
        ROOT_TOKEN.remove(t.getUniqueId());
        for (Attribute a : List.of(Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH)) {
            var ai = t.getAttribute(a);
            if (ai != null) {
                ai.removeModifier(ROOT);
            }
        }
    }

    /** Tự đứng im (Đóng băng): như Trói nhưng không bị kháng khống chế chặn. */
    private static void rootSelf(Player p, int ticks) {
        Long imm = CC_IMMUNE.remove(p.getUniqueId());
        root(p, ticks);
        if (imm != null) {
            CC_IMMUNE.put(p.getUniqueId(), imm);
        }
    }

    /** Hoảng sợ: quái chạy ra xa người dùng; người bị đẩy lùi + chậm + tối mắt. */
    private static void fear(Player p, org.bukkit.entity.LivingEntity t, int ticks) {
        if (ccImmune(t)) {
            return;
        }
        t.getWorld().playSound(t.getLocation(), Sound.ENTITY_GHAST_SCREAM, 0.6f, 1.4f);
        if (t instanceof Player tp) {
            org.bukkit.util.Vector away = tp.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
            if (away.lengthSquared() > 1e-4) {
                tp.setVelocity(away.normalize().multiply(1.1).setY(0.35));
            }
            tp.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, ticks, 0, false, false, true));
            tp.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS, ticks, 0, false, false, true));
            tp.sendActionBar(LEGACY.deserialize("&7Bị Hoảng sợ"));
            return;
        }
        if (!(t instanceof org.bukkit.entity.Mob m)) {
            return;
        }
        new org.bukkit.scheduler.BukkitRunnable() {
            int n;

            @Override
            public void run() {
                if (!m.isValid() || (n += 10) > ticks) {
                    cancel();
                    return;
                }
                m.setTarget(null);
                org.bukkit.util.Vector away = m.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                if (away.lengthSquared() < 1e-4) {
                    away = new org.bukkit.util.Vector(1, 0, 0);
                }
                m.getPathfinder().moveTo(m.getLocation().add(away.normalize().multiply(8)), 1.4);
                m.getWorld().spawnParticle(Particle.SMOKE, m.getLocation().add(0, m.getHeight(), 0), 3, 0.2, 0.1, 0.2, 0);
            }
        }.runTaskTimer(pl(), 0L, 10L);
    }

    /** Điểm nhắm: khối trúng tia nhìn (tối đa range), không trúng thì cách range khối trước mặt. */
    private static org.bukkit.Location aimPoint(Player p, double range) {
        org.bukkit.Location eye = p.getEyeLocation();
        var hit = p.getWorld().rayTraceBlocks(eye, eye.getDirection(), range, org.bukkit.FluidCollisionMode.NEVER, true);
        return hit != null ? hit.getHitPosition().toLocation(p.getWorld()).add(0, 0.5, 0) : eye.add(eye.getDirection().multiply(range));
    }

    /** Mặt đất trúng tia nhìn (null nếu không trúng khối nào trong range). */
    private static org.bukkit.Location groundPoint(Player p, double range) {
        org.bukkit.Location eye = p.getEyeLocation();
        var hit = p.getWorld().rayTraceBlocks(eye, eye.getDirection(), range, org.bukkit.FluidCollisionMode.NEVER, true);
        return hit == null ? null : hit.getHitPosition().toLocation(p.getWorld()).add(0, 0.2, 0);
    }

    /** Hố đen: 2 giây hút sinh vật trong bán kính r về tâm (mỗi sinh vật phải qua đòn thử 1 lần). */
    private static void blackHole(Player p, org.bukkit.Location center, double r) {
        Map<UUID, Boolean> ok = new HashMap<>();
        center.getWorld().playSound(center, Sound.BLOCK_PORTAL_TRIGGER, 0.6f, 1.6f);
        new org.bukkit.scheduler.BukkitRunnable() {
            int n;

            @Override
            public void run() {
                if (!p.isOnline() || (n += 4) > 40) {
                    cancel();
                    return;
                }
                center.getWorld().spawnParticle(Particle.REVERSE_PORTAL, center, 30, r / 2, 0.6, r / 2, 0.05);
                center.getWorld().spawnParticle(Particle.SQUID_INK, center, 4, 0.2, 0.2, 0.2, 0);
                for (org.bukkit.entity.Entity en : center.getWorld().getNearbyEntities(center, r, r / 2 + 1, r)) {
                    if (hiddenAdmin(p, en)) {
                        continue;   // admin đang ẩn (/v): chiêu không trúng, không lộ
                    }
                    if (!(en instanceof org.bukkit.entity.LivingEntity le) || en == p || PetCombat.isPet(en) || en instanceof org.bukkit.entity.ArmorStand) {
                        continue;
                    }
                    if (!ok.computeIfAbsent(en.getUniqueId(), k -> strike(p, le, 0.5))) {
                        continue;
                    }
                    org.bukkit.util.Vector v = center.toVector().subtract(le.getLocation().toVector());
                    if (v.length() > 0.8) {
                        // đứng trên đất thì nhấc nhẹ: lực ngang trên mặt đất bị ma sát ăn hết (test bot 22/10: chỉ dời 0,2 khối)
                        double y = le.isOnGround() ? 0.2 : Math.max(-0.3, Math.min(0.2, v.getY() * 0.15));
                        le.setVelocity(v.normalize().multiply(0.45).setY(y));
                    }
                }
            }
        }.runTaskTimer(pl(), 0L, 4L);
    }

    /** Tia sáng: tia thẳng từ mắt, dừng ở khối, gây dmg cho mọi sinh vật cách tia dưới 1 khối. */
    private static void beamDamage(Player p, double range, double dmg) {
        if (!p.isOnline() || p.isDead()) {
            return;
        }
        org.bukkit.Location eye = p.getEyeLocation();
        org.bukkit.util.Vector dir = eye.getDirection().normalize();
        var block = p.getWorld().rayTraceBlocks(eye, dir, range, org.bukkit.FluidCollisionMode.NEVER, true);
        double len = block == null ? range : block.getHitPosition().distance(eye.toVector());
        for (double i = 0.5; i < len; i += 0.4) {
            p.getWorld().spawnParticle(Particle.END_ROD, eye.clone().add(dir.clone().multiply(i)), 1, 0.02, 0.02, 0.02, 0);
        }
        org.bukkit.util.Vector a = eye.toVector();
        org.bukkit.util.BoundingBox box = org.bukkit.util.BoundingBox.of(a, a.clone().add(dir.clone().multiply(len))).expand(1.2);
        for (org.bukkit.entity.Entity en : p.getWorld().getNearbyEntities(box)) {
            if (hiddenAdmin(p, en)) {
                continue;   // admin đang ẩn (/v): chiêu không trúng, không lộ
            }
            if (!(en instanceof org.bukkit.entity.LivingEntity le) || en == p || PetCombat.isPet(en) || en instanceof org.bukkit.entity.ArmorStand) {
                continue;
            }
            org.bukkit.util.Vector c = le.getLocation().add(0, le.getHeight() / 2, 0).toVector().subtract(a);
            double along = c.dot(dir);
            if (along < 0 || along > len || c.clone().subtract(dir.clone().multiply(along)).length() > 1.0 + le.getWidth() / 2) {
                continue;
            }
            le.setNoDamageTicks(0);
            le.damage(dmg, p);
        }
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GUARDIAN_ATTACK, 1f, 1.6f);
    }

    /** Pháo kích: báo trước 1,5 giây rồi nổ (không phá khối). */
    private static void artillery(Player p, org.bukkit.Location at, double dmg) {
        at.getWorld().playSound(at, Sound.ENTITY_TNT_PRIMED, 1f, 1f);
        new org.bukkit.scheduler.BukkitRunnable() {
            int n;

            @Override
            public void run() {
                if ((n += 5) < 30) {
                    for (int i = 0; i < 16; i++) {
                        double ang = Math.PI * 2 * i / 16;
                        at.getWorld().spawnParticle(Particle.FLAME, at.clone().add(Math.cos(ang) * 3, 0.1, Math.sin(ang) * 3), 1, 0, 0, 0, 0);
                    }
                    return;
                }
                cancel();
                at.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, at, 1, 0, 0, 0, 0);
                at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.9f);
                for (org.bukkit.entity.Entity en : at.getWorld().getNearbyEntities(at, 3, 3, 3)) {
                    if (hiddenAdmin(p, en)) {
                        continue;   // admin đang ẩn (/v): chiêu không trúng, không lộ
                    }
                    if (en instanceof org.bukkit.entity.LivingEntity le && en != p && !PetCombat.isPet(en) && !(en instanceof org.bukkit.entity.ArmorStand)
                            && le.getLocation().distanceSquared(at) <= 9) {
                        le.setNoDamageTicks(0);
                        if (p.isOnline()) {
                            le.damage(dmg, p);
                        } else {
                            le.damage(dmg);
                        }
                        if (!le.isDead()) {
                            le.setVelocity(le.getVelocity().add(new org.bukkit.util.Vector(0, 0.45, 0)));
                        }
                    }
                }
            }
        }.runTaskTimer(pl(), 0L, 5L);
    }

    /** Sét nảy: đánh mục tiêu đầu rồi nảy sang sinh vật gần nhất chưa trúng (6 khối), mỗi lần bớt 20 %. */
    private static void chain(Player p, org.bukkit.entity.LivingEntity first, double dmg, int bounces) {
        java.util.Set<UUID> hit = new java.util.HashSet<>();
        org.bukkit.entity.LivingEntity cur = first;
        org.bukkit.entity.Entity from = p;
        double d = dmg;
        for (int i = 0; i <= bounces && cur != null; i++) {
            hit.add(cur.getUniqueId());
            if (strike(p, cur, d)) {
                beam(from instanceof Player fp ? fp : p, cur, Particle.ELECTRIC_SPARK);
                cur.getWorld().playSound(cur.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.6f, 1.6f);
            }
            from = cur;
            d *= 0.8;
            org.bukkit.entity.LivingEntity next = null;
            double best = 36;
            for (org.bukkit.entity.Entity en : cur.getNearbyEntities(6, 3, 6)) {
                if (hiddenAdmin(p, en)) {
                    continue;   // admin đang ẩn (/v): chiêu không trúng, không lộ
                }
                if (en instanceof org.bukkit.entity.LivingEntity le && en != p && !hit.contains(en.getUniqueId()) && !PetCombat.isPet(en)
                        && !(en instanceof org.bukkit.entity.ArmorStand)) {
                    double ds = en.getLocation().distanceSquared(cur.getLocation());
                    if (ds < best) {
                        best = ds;
                        next = le;
                    }
                }
            }
            if (next != null) {
                sparkLine(cur, next);
            }
            cur = next;
        }
    }

    private static void sparkLine(org.bukkit.entity.Entity a, org.bukkit.entity.Entity b) {
        org.bukkit.Location s = a.getLocation().add(0, a.getHeight() / 2, 0);
        org.bukkit.util.Vector d = b.getLocation().add(0, b.getHeight() / 2, 0).toVector().subtract(s.toVector());
        double len = d.length();
        d.normalize();
        for (double i = 0; i < len; i += 0.4) {
            a.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, s.clone().add(d.clone().multiply(i)), 1, 0, 0, 0, 0);
        }
    }

    /** Liên hoàn đao: lần lượt dịch chuyển ra sau từng mục tiêu và chém, không nhận sát thương trong lúc đó, xong về chỗ cũ. */
    private static void flurry(Player p, List<org.bukkit.entity.LivingEntity> targets, double dmg) {
        org.bukkit.Location start = p.getLocation();
        INVULN.put(p.getUniqueId(), System.currentTimeMillis() + targets.size() * 250L + 500);
        new org.bukkit.scheduler.BukkitRunnable() {
            int i;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                if (i >= targets.size()) {
                    cancel();
                    org.bukkit.block.Block f = start.getBlock();
                    if (start.getWorld() == p.getWorld() && f.isPassable() && f.getRelative(org.bukkit.block.BlockFace.UP).isPassable()) {
                        p.teleport(start, io.papermc.paper.entity.TeleportFlag.Relative.VELOCITY_ROTATION);
                    }
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.6f);
                    return;
                }
                org.bukkit.entity.LivingEntity t = targets.get(i++);
                if (!t.isValid() || t.isDead() || t.getWorld() != p.getWorld() || t.getLocation().distanceSquared(start) > 144 || !strike(p, t, dmg)) {
                    return;
                }
                org.bukkit.Location behind = t.getLocation().subtract(t.getLocation().getDirection().setY(0).normalize().multiply(1.2));
                org.bukkit.block.Block f = behind.getBlock();
                org.bukkit.Location to = f.isPassable() && f.getRelative(org.bukkit.block.BlockFace.UP).isPassable() ? behind : t.getLocation();
                to.setDirection(t.getLocation().toVector().subtract(to.toVector()));
                p.teleport(to);
                p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, t.getLocation().add(0, 1, 0), 2, 0.3, 0.3, 0.3, 0);
                p.getWorld().playSound(t.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.3f);
            }
        }.runTaskTimer(pl(), 0L, 5L);
    }

    /** Rút bài: chốt lá đang hiện, đòn cận chiến kế tiếp trong 6 giây sẽ kích hoạt. */
    private static void lockCard(Player p, Card c) {
        c.card = c.idx;
        c.until = System.currentTimeMillis() + 6000;
        NEXT.put(p.getUniqueId() + ":rut_bai", System.currentTimeMillis() + COOLDOWN_MS.get("rut_bai"));
        p.sendActionBar(LEGACY.deserialize("&7Đã chốt lá " + CARD_NAMES[c.card] + " &7· đánh trúng trong 6 giây"));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
    }

    /** Tua thời gian: quay về mốc (nếu còn đúng lượt token). */
    private static void rewind(Player p, long token) {
        Mark m = MARKS.get(p.getUniqueId());
        if (m == null || m.token() != token) {
            return;
        }
        MARKS.remove(p.getUniqueId());
        NEXT.put(p.getUniqueId() + ":tua_thoi_gian", System.currentTimeMillis() + COOLDOWN_MS.get("tua_thoi_gian"));
        org.bukkit.Location to = m.at();
        if (!p.isOnline() || p.isDead() || to.getWorld() != p.getWorld()) {
            return;
        }
        org.bukkit.block.Block feet = to.getBlock();
        if (!feet.isPassable() || !feet.getRelative(org.bukkit.block.BlockFace.UP).isPassable()) {
            p.sendActionBar(LEGACY.deserialize("&7Mốc thời gian đã bị lấp kín, không quay về được"));
            return;
        }
        p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.05);
        p.teleport(to, io.papermc.paper.entity.TeleportFlag.Relative.VELOCITY_ROTATION);
        p.setFallDistance(0);
        double lost = m.health() - p.getHealth();
        if (lost > 0) {
            var attr = p.getAttribute(Attribute.MAX_HEALTH);
            double max = attr == null ? 20 : attr.getValue();
            p.setHealth(Math.min(max, p.getHealth() + lost * Math.max(0, Math.min(1, m.value()))));
        }
        p.getWorld().playSound(to, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 0.9f, 0.8f);
        p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, to.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.05);
    }

    /** Rút bài: Đỏ = nổ quanh mục tiêu + chậm; Xanh = mình hồi máu; Vàng = trói mục tiêu. */
    private static void playCard(Player p, org.bukkit.entity.LivingEntity victim, Card c) {
        double lv = c.value;
        switch (c.card) {
            case 0 -> {
                victim.getWorld().spawnParticle(Particle.EXPLOSION, victim.getLocation().add(0, 1, 0), 2, 0.5, 0.3, 0.5, 0);
                victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.5f);
                for (org.bukkit.entity.Entity en : victim.getNearbyEntities(3, 2, 3)) {
                    if (hiddenAdmin(p, en)) {
                        continue;   // admin đang ẩn (/v): chiêu không trúng, không lộ
                    }
                    if (en instanceof org.bukkit.entity.LivingEntity le && en != p && !PetCombat.isPet(en) && !(en instanceof org.bukkit.entity.ArmorStand)) {
                        org.bukkit.Bukkit.getScheduler().runTask(pl(), () -> {
                            le.setNoDamageTicks(0);
                            le.damage(1 + lv, p);
                            le.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 40, 0, false, false, true));
                        });
                    }
                }
                org.bukkit.Bukkit.getScheduler().runTask(pl(), () -> {
                    victim.setNoDamageTicks(0);
                    victim.damage(1 + lv, p);
                    victim.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 40, 0, false, false, true));
                });
            }
            case 1 -> {
                var attr = p.getAttribute(Attribute.MAX_HEALTH);
                p.setHealth(Math.min(attr == null ? 20 : attr.getValue(), p.getHealth() + 2 + lv * 2));
                p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 4, 0.3, 0.2, 0.3, 0);
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
            }
            default -> {
                org.bukkit.Bukkit.getScheduler().runTask(pl(), () -> root(victim, (int) Math.round((0.5 + 0.5 * lv) * 20)));
                victim.getWorld().spawnParticle(Particle.WAX_ON, victim.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0);
                victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1f, 0.8f);
            }
        }
    }

    /** Người đang bật Chắn đạn và viên đạn bay tới từ phía trước mặt (theo vị trí người bắn / viên đạn, không theo vận tốc lúc chạm). */
    private static boolean fromFront(Player p, org.bukkit.entity.Projectile pr) {
        Long until = WALL.get(p.getUniqueId());
        if (until == null || until < System.currentTimeMillis()) {
            return false;
        }
        org.bukkit.util.Vector face = p.getLocation().getDirection().setY(0);
        org.bukkit.Location from = pr.getShooter() instanceof org.bukkit.entity.Entity sh ? sh.getLocation() : pr.getOrigin() != null ? pr.getOrigin() : pr.getLocation();
        org.bukkit.util.Vector to = from.toVector().subtract(p.getLocation().toVector()).setY(0);
        if (face.lengthSquared() < 1e-6 || to.lengthSquared() < 1e-6) {
            return false;
        }
        return face.normalize().dot(to.normalize()) > 0.1;   // người bắn ở nửa trước mặt
    }

    /** Chắn đạn: đạn bay tới người đang bật Chắn đạn từ phía trước mặt thì bị chặn. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectile(org.bukkit.event.entity.ProjectileHitEvent e) {
        if (!(e.getHitEntity() instanceof Player p)) {
            return;
        }
        if (!fromFront(p, e.getEntity())) {
            return;
        }
        e.setCancelled(true);
        e.getEntity().remove();
        p.getWorld().spawnParticle(Particle.CLOUD, e.getEntity().getLocation(), 6, 0.1, 0.1, 0.1, 0.02);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_DEFLECT, 0.8f, 1.2f);
    }

    /** Đặt cỡ người (1 = thường). Modifier tạm nên không ghi vào dữ liệu người chơi. */
    private static void setScale(Player p, double scale) {
        var attr = p.getAttribute(Attribute.SCALE);
        if (attr == null) {
            return;
        }
        attr.removeModifier(SIZE);
        if (scale != 1) {
            attr.addTransientModifier(new org.bukkit.attribute.AttributeModifier(SIZE, scale - 1,
                    org.bukkit.attribute.AttributeModifier.Operation.ADD_SCALAR, org.bukkit.inventory.EquipmentSlotGroup.ANY));
        }
    }

    /** Có đủ khoảng trống phía trên cho người cao 1,8 × scale khối không (chỉ cần kiểm khi to ra). */
    private static boolean roomFor(Player p, double scale) {
        org.bukkit.Location l = p.getLocation();
        int top = (int) Math.ceil(l.getY() + 1.8 * scale);
        for (int y = l.getBlockY(); y < top; y++) {
            if (!l.getWorld().getBlockAt(l.getBlockX(), y, l.getBlockZ()).isPassable()) {
                return false;
            }
        }
        return true;
    }

    /** Hết giờ Khổng lồ / Thu nhỏ: đang Thu nhỏ ở chỗ chật thì chờ thêm từng giây (tối đa 10 lần) cho khỏi kẹt đầu vào khối. */
    private static void endScale(Player p, long token, int tries) {
        if (!p.isOnline() || !Long.valueOf(token).equals(SIZE_TOKEN.get(p.getUniqueId()))) {
            return;
        }
        var attr = p.getAttribute(Attribute.SCALE);
        boolean small = attr != null && attr.getValue() < 1;
        if (small && tries < 10 && !roomFor(p, 1)) {
            p.sendActionBar(LEGACY.deserialize("&7Ra chỗ rộng để trở lại cỡ thường"));
            org.bukkit.Bukkit.getScheduler().runTaskLater(org.bukkit.Bukkit.getPluginManager().getPlugin("DuckyMoMoCore"),
                    () -> endScale(p, token, tries + 1), 20L);
            return;
        }
        SIZE_TOKEN.remove(p.getUniqueId());
        setScale(p, 1);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.6f, 1.2f);
    }

    /** Điểm đến Dịch chuyển: đi theo hướng ngang từng 0,5 khối, bám địa hình (mỗi bước lên tối đa 1 khối, xuống tối đa 2) như đi bộ;
     *  gặp vách không có chỗ đứng đủ 2 khối cao thì dừng (không xuyên tường). Trả chỗ xa nhất đứng được, null nếu không đi được. */
    private static org.bukkit.Location blinkTarget(Player p, double dist) {
        org.bukkit.Location start = p.getLocation();
        // chỉ theo hướng ngang (nhìn chúc xuống không cắm xuống đất, nhìn lên không bay lên trời)
        org.bukkit.util.Vector dir = start.getDirection().setY(0);
        if (dir.lengthSquared() < 1e-4) {
            return null;
        }
        dir.normalize();
        int y = start.getBlockY();
        org.bukkit.Location best = null;
        for (double d = 0.5; d <= dist; d += 0.5) {
            org.bukkit.Location col = start.clone().add(dir.clone().multiply(d));
            Integer ny = null;
            for (int dy = 1; dy >= -2; dy--) {   // ưu tiên chỗ cao (leo dốc), rồi mới xuống
                org.bukkit.block.Block feet = col.getWorld().getBlockAt(col.getBlockX(), y + dy, col.getBlockZ());
                if (feet.isPassable() && feet.getRelative(org.bukkit.block.BlockFace.UP).isPassable()
                        && !feet.getRelative(org.bukkit.block.BlockFace.DOWN).isPassable()) {
                    ny = y + dy;
                    break;
                }
            }
            if (ny == null) {
                break;   // vách / tường: dừng, không xuyên
            }
            y = ny;
            best = new org.bukkit.Location(col.getWorld(), col.getX(), y, col.getZ(), start.getYaw(), start.getPitch());
        }
        return best == null || best.distanceSquared(start) < 1 ? null : best;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Block b = e.getClickedBlock();
        if (b != null && b.getType().isInteractable() && !e.getPlayer().isSneaking()) {
            return;   // bấm rương, cửa… vẫn mở như thường (ngồi xuống + bấm thì dùng kỹ năng)
        }
        ItemStack chest = e.getPlayer().getInventory().getChestplate();
        if (e.getAction() == Action.RIGHT_CLICK_AIR && chest != null && chest.hasItemMeta()) {
            Integer lv = chest.getItemMeta().getPersistentDataContainer().get(key("phong_bay"), PersistentDataType.INTEGER);
            if (lv != null && lv > 0) {
                cast(e.getPlayer(), chest, "phong_bay", levelValue("phong_bay", lv));
            }
        }
        ItemStack it = e.getPlayer().getInventory().getItemInMainHand();
        if (!it.hasItemMeta()) {
            return;
        }
        PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();
        boolean used = false;
        for (String id : NAMES.keySet()) {
            if (id.equals("phong_bay") || PASSIVE.contains(id)) {
                continue;   // phóng bay: ở trên cánh, xử lý phía trên; phù phép thụ động không bấm
            }
            Integer lv = pdc.get(key(id), PersistentDataType.INTEGER);
            if (lv != null && lv > 0 && cast(e.getPlayer(), it, id, levelValue(id, lv)) && !id.equals("luot")) {
                used = true;
            }
        }
        if (used) {
            e.setCancelled(true);   // không ăn / đặt khối khi vừa hồi máu, bắn cầu lửa
        }
    }

    /** Cầu lửa: sát thương theo cấp / value. Cuồng nộ: nhân sát thương đòn đánh / bắn của người đang Cuồng nộ. */
    /** Đóng băng / Liên hoàn đao: không nhận sát thương. Bất tử: không chết. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnyDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (active(INVULN, e.getEntity())) {
            e.setCancelled(true);
        }
        if (e.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL && active(NOFALL, e.getEntity())) {
            e.setCancelled(true);   // Nhảy cao / Súng cao su: không mất máu khi rơi
            NOFALL.remove(e.getEntity().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethal(org.bukkit.event.entity.EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && active(UNDYING, p) && p.getHealth() - e.getFinalDamage() < 1) {
            e.setCancelled(true);
            p.setHealth(Math.min(p.getHealth(), 1));
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 8, 0.3, 0.6, 0.3, 0.1);
        }
    }

    /** Đánh dấu tử thần: cộng sát thương người đánh dấu gây cho mục tiêu trong 3 giây. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMarked(EntityDamageByEntityEvent e) {
        DeathMark d = DEATH.get(e.getEntity().getUniqueId());
        if (d == null || probeTo != null) {
            return;
        }
        org.bukkit.entity.Entity src = e.getDamager() instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof org.bukkit.entity.Entity sh ? sh : e.getDamager();
        if (src.getUniqueId().equals(d.caster) && System.currentTimeMillis() < d.until) {
            d.sum += e.getFinalDamage();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        org.bukkit.entity.Entity src = e.getDamager() instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof org.bukkit.entity.Entity sh ? sh : e.getDamager();
        if (active(DISARM, src)) {
            e.setCancelled(true);   // Hoá cừu / Đóng băng: không gây được sát thương
            return;
        }
        double[] ctr = probeTo == null && e.getEntity() instanceof Player vp ? COUNTER.get(vp.getUniqueId()) : null;
        if (ctr != null && System.currentTimeMillis() <= ctr[0] && src instanceof org.bukkit.entity.LivingEntity) {
            e.setCancelled(true);   // Phản công: né mọi đòn đánh (cận chiến + tên / đạn của sinh vật)
            ctr[2]++;
            e.getEntity().getWorld().playSound(e.getEntity().getLocation(), Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1f, 1.4f);
            e.getEntity().getWorld().spawnParticle(Particle.ENCHANTED_HIT, e.getEntity().getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.1);
            if (e.getDamager() instanceof org.bukkit.entity.Projectile pr) {
                pr.remove();
            }
            return;
        }
        if (e.getDamager() instanceof org.bukkit.entity.Projectile pr && e.getEntity() instanceof Player victim && fromFront(victim, pr)) {
            e.setCancelled(true);   // Chắn đạn (test bot 22/10: chỉ huỷ ProjectileHitEvent thì tên của bộ xương vẫn gây sát thương)
            pr.remove();
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_BREEZE_DEFLECT, 0.8f, 1.2f);
            return;
        }
        if (e.getDamager() instanceof Player cp && probeTo == null && e.getEntity() instanceof org.bukkit.entity.LivingEntity victim) {
            Card c = CARDS.get(cp.getUniqueId());
            if (c != null && c.card >= 0) {
                CARDS.remove(cp.getUniqueId());
                if (System.currentTimeMillis() <= c.until) {
                    playCard(cp, victim, c);
                }
            }
        }
        Player atk = e.getDamager() instanceof Player pl ? pl
                : e.getDamager() instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Player pl ? pl : null;
        if (atk != null && probeTo == null) {
            double[] st = STEALTH.get(atk.getUniqueId());
            if (st != null) {
                if (st[0] >= System.currentTimeMillis()) {
                    e.setDamage(e.getDamage() * (1 + st[1] / 100.0));   // Tàng hình: đòn đầu tiên mạnh hơn rồi lộ diện
                    e.getEntity().getWorld().spawnParticle(Particle.CRIT, e.getEntity().getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.2);
                    e.getEntity().getWorld().playSound(e.getEntity().getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.8f);
                }
                reveal(atk);
            }
        }
        if (atk != null) {
            double[] r = RAGE.get(atk.getUniqueId());
            if (r != null) {
                if (r[0] < System.currentTimeMillis()) {
                    RAGE.remove(atk.getUniqueId());
                } else {
                    e.setDamage(e.getDamage() * r[1]);
                }
            }
        }
        if (e.getDamager() instanceof SmallFireball fb) {
            Double dmg = fb.getPersistentDataContainer().get(FIRE, PersistentDataType.DOUBLE);
            if (dmg != null) {
                e.setDamage(dmg);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent e) {
        if (e.getTarget() instanceof Player t) {
            double[] st = STEALTH.get(t.getUniqueId());
            if (st != null && st[0] >= System.currentTimeMillis()) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        reveal(e.getPlayer());   // ẩn với người khác là theo phiên của họ: phải hiện lại, không thì vào lại vẫn bị ẩn
        for (Map<UUID, ?> m : List.of(NOFALL, SLING, COUNTER)) {
            m.remove(e.getPlayer().getUniqueId());
        }
        String u = e.getPlayer().getUniqueId() + ":";
        NEXT.keySet().removeIf(k -> k.startsWith(u));
        RAGE.remove(e.getPlayer().getUniqueId());
        UUID q = e.getPlayer().getUniqueId();
        for (Map<UUID, ?> m : List.of(SILENCE, MARKS, WALL, DISARM, INVULN, UNDYING, SPELL_SHIELD, CC_IMMUNE, CARDS, DEATH)) {
            m.remove(q);
        }
        if (SIZE_TOKEN.remove(e.getPlayer().getUniqueId()) != null) {
            setScale(e.getPlayer(), 1);
        }
    }
}
