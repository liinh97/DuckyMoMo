"""Tiếng Việt cho Towny / SiegeWar ép cho MỌI người chơi (kể cả máy để tiếng Anh): ghi toàn bộ câu vi vào lang/override/global.yml
(file này đè mọi ngôn ngữ). Nguồn: bản vi-VN trong jar + dịch thêm (T). Bỏ qua câu nhật ký console (flatfile_*) và thông tin file.
Chạy: python3 tools/vi/towny.py towny <en-US.yml> <vi-VN.yml> <global.yml ra>
      python3 tools/vi/towny.py siegewar <en-US.yml> <vi-VN.yml> <global.yml ra>
(en-US/vi-VN lấy trong jar: unzip Towny-*.jar 'lang/en-US.yml' 'lang/vi-VN.yml')"""
import re
import sys

import yaml

KIND, EN, VI, OUT = sys.argv[1:5]
SKIP = re.compile(r"^(name|language|author|website|description|flatfile_.*|msg_ptw_warning_\d+)$")   # msg_ptw_warning_*: Towny cấm ghi đè

TOWNY = {
    "default_towny_prefix": "&6[Towny] &b",
    "help_4": "Trợ giúp lệnh cư dân.",
    "town_help_36": "Liệt kê thị trấn gần đây, xếp theo khoảng cách.",
    "town_help_37": "Liệt kê ô đất mà người chơi được tin cậy.",
    "town_help_38": "Liệt kê ô đất có người được tin cậy trong thị trấn.",
    "town_trust_help_1": "Bỏ một cư dân khỏi danh sách tin cậy của thị trấn.",
    "nation_help_19": "Dịch chuyển tới tiền đồn của các thị trấn trong quốc gia.",
    "res_8": "Chỉ là một nông dân hiền lành",
    "admin_panel_5": "Xem /ta plot ? để được trợ giúp",
    "admin_panel_7": "Xem /ta toggle ? để được trợ giúp",
    "townyadmin_town_toggle_help_0": "Bật/tắt PvP của thị trấn.",
    "ta_resident_help_5": "Đặt mốc lần online cuối hoặc ngày đăng ký của cư dân.",
    "ta_residentmeta_help_3": "Thêm hoặc xoá metadata của cư dân theo khoá.",
    "ta_nationmeta_help_1": "Xem metadata của quốc gia.",
    "ta_nationmeta_help_2": "Đặt khoá và giá trị metadata cho quốc gia.",
    "ta_nationmeta_help_3": "Thêm hoặc xoá metadata của quốc gia theo khoá.",
    "ta_reload_help_0": "Nạp lại cơ sở dữ liệu.",
    "ta_reload_help_1": "Nạp lại cấu hình.",
    "ta_reload_help_2": "Nạp lại file ngôn ngữ.",
    "plot_help_3": "Xem /plot set ? để được trợ giúp.",
    "plot_help_4": "Xem /plot toggle ? để được trợ giúp.",
    "plot_help_7": "Thêm quyền mặc định riêng cho một người chơi.",
    "plot_help_8": "Mở bảng chỉnh quyền.",
    "plot_group_help_0": "Ví dụ: /plot group new DatDatTien",
    "plot_group_help_4": "Ví dụ: /plot group set perm resident on.",
    "plot_group_help_5": "Ví dụ: /plot group toggle [pvp|fire|mobs]",
    "plot_group_help_6": "Ví dụ: /plot group forsale 50",
    "plot_group_help_7": "Ví dụ: /plot group notforsale",
    "plot_group_help_11": "Mở bảng quyền ô đất.",
    "msg_nation_cant_peaceful": "&bQuốc gia không đủ tiền để thành quốc gia hoà bình. Phải trả được %s mỗi ngày.",
    "msg_err_plot_preclaim_disable": "&cBạn chưa chiếm được ô đất %s lúc này.",
    "msg_err_plot_preunclaim_disable": "&cCư dân chưa bỏ được ô đất %s lúc này.",
    "msg_rank_can_only_be_assigned_x_times": "Không phong chức %s cho %s được: chức này chỉ phong tối đa %s lần, %s của bạn đã dùng %s lần.",
    "msg_you_lost_money_dying_with_town_loss": "Bạn mất %s, thị trấn của bạn mất %s.",
    "msg_you_lost_money_dying_with_nation_loss": "Bạn mất %s, quốc gia của bạn mất %s.",
    "msg_you_lost_money_dying_with_town_and_nation_loss": "Bạn mất %s, thị trấn mất %s và quốc gia mất %s.",
    "msg_your_town_lost_money_dying_and_nation_loss": "Thị trấn của bạn mất %s và quốc gia mất %s.",
    "nation_revoke_ally_successful": "&bĐã rút lại lời mời liên minh.",
    "nation_outpost_not_allowed_for_conquered_towns": "Thị trấn của bạn đang bị chiếm nên không dịch chuyển tới tiền đồn của quốc gia được.",
    "status_pvp": "PvP:",
    "status_title_pvp": "&4(PvP)",
    "msg_click_town_info": "<green>Bấm để xem thông tin thị trấn.",
    "msg_click_nation_info": "<green>Bấm để xem thông tin quốc gia.",
    "msg_click_spawn_brief": "<green>[Điểm hồi sinh]",
    "msg_ta_deposit_all_nations_success": "Đã nộp %s vào ngân khố mọi quốc gia.",
    "status_plot_clear_deletion": "Xoá khối khi dọn ô đất:",
    "towny_prices_overclaimed_based_on": "&7Phí duy trì khi chiếm vượt mức tính theo &a %s %s",
    "msg_comptype_forsale": "Đang rao bán",
    "msg_purge_complete_x_removed_from_towns": "&bĐã dọn xong, bỏ %s cư dân không hoạt động khỏi các thị trấn.",
    "msg_purge_reached_50_residents": "&bĐã dọn tới 50 người, các lần xoá sau không ghi lại nữa cho đỡ rối.",
    "help_ta_perms_townrankrename": "Đổi tên một chức thị trấn trong file townyperms",
    "help_ta_perms_nationrankrename": "Đổi tên một chức quốc gia trong file townyperms",
    "msg_err_must_be_less_than_or_equal_to": "Số lượng phải nhỏ hơn hoặc bằng %s",
    "msg_perm_hud_pvp": "PvP: ",
    "msg_nation_board_reset": "&bĐã xoá bảng tin quốc gia.",
    "msg_err_x_in_unjailable_world": "%s đang ở thế giới không bỏ tù được.",
    "msg_err_cannot_unclaim_outpost_with_adjacent_claims": "Không bỏ được tiền đồn đang có ô đất liền kề.",
    "msg_err_world_not_same_cant_teleport": "<red>Lệnh này không dịch chuyển sang thế giới khác được.",
    "msg_resident_registered_set": "Ngày đăng ký của cư dân '%s' đã đặt thành '%s'.",
    "msg_resident_lastonline_set": "Lần online cuối của cư dân '%s' đã đặt thành '%s'.",
    "titlemsg_pay_your_bail_subtitle": "/res jail paybail",
    "status_world_jailing": "Bỏ tù: ",
    "msg_version_build_info": "<dark_green>Thông tin bản build: <green>%s</green> nhánh <green>%s<green>.",
    "msg_version_build_info_failed": "<red>Không lấy được thông tin bản build.",
    "time-future": "trong tương lai",
    "time-just-now": "vừa xong",
    "time-a-minute-ago": "1 phút trước",
    "time-x-minutes-ago": "%d phút trước",
    "time-an-hour-ago": "1 giờ trước",
    "time-x-hours-ago": "%d giờ trước",
    "time-yesterday": "hôm qua",
    "time-x-days-ago": "%d ngày trước",
    "time-a-year-ago": "1 năm trước",
    "time-x-years-ago": "%d năm trước",
    "status_onlinesince": "Online từ:",
    "msg_a_player_spawned_to_your_town_earning_you_x": "%s vừa dịch chuyển tới thị trấn của bạn, bạn nhận %s.",
    "msg_you_have_been_refunded_your_town_cost": "Bạn được hoàn %s vì thị trấn mới lập chưa quá %s giờ và chưa chiếm quá %s ô đất.",
    "msg_nearby_towns": "Thị trấn gần đây",
    "msg_no_towns_nearby": "Không có thị trấn nào gần đây.",
    "msg_nearby_town_format": "<green>%s <white>(%s khối)",
    "msg_err_must_specify_days_to_be_conquered": "Phải ghi số ngày bị chiếm: 1 trở lên, hoặc -1 / unlimited để chiếm vô thời hạn.",
    "msg_conquered_status_granted": "Đã đặt %s ở trạng thái bị chiếm trong %s ngày.",
    "msg_conquered_status_granted_unlimited": "Đã đặt %s ở trạng thái bị chiếm vô thời hạn.",
    "msg_err_must_specify_on_or_off": "Phải ghi on hoặc off.",
    "msg_nation_level_overridden_with": "Cấp quốc gia của %s đã đặt thành %s.",
    "msg_nation_level_unset": "Đã bỏ cấp đặt tay của quốc gia %s. Cấp hiện tại: %s.",
    "msg_err_cannot_merge_x_seconds_remaining": "Đợi %s giây nữa mới mời %s sáp nhập lại được.",
    "msg_click_join_town": "<green>[Vào thị trấn]",
    "status_join_town": "Vào thị trấn",
    "msg_click_join_town_hover": "<green>Bấm để vào thị trấn <aqua>%s",
    "msg_err_resident_has_no_trusted_plots": "%s không được tin cậy ở ô đất nào trong thị trấn %s.",
    "msg_err_town_has_no_trusted_plots": "Thị trấn của bạn không có ô đất nào có người được tin cậy.",
    "msg_town_plots_trustlist_resident_title": "Ô đất tin cậy của %s (%s)",
    "msg_town_plots_trustlist_town_title": "Ô đất có người tin cậy của %s",
    "msg_list_plotgroups_trustlist_component": " (Nhóm %s ô đất)",
    "msg_list_trusted_hover": "Tin cậy: %s",
    "msg_nation_you_kicked": "&bBạn đã đuổi %s khỏi quốc gia.",
    "msg_successfully_renamed_rank_for_the_town_or_nation_rank": "Đã đổi tên chức %s thành %s trong %s.",
    "msg_err_your_town_cannot_afford_unclaim": "Thị trấn của bạn không đủ tiền để bỏ ô đất này. Cần %s.",
    "msg_err_no_ruined_town_here": "Phải đứng trong thị trấn đổ nát mới giành lại được (khi bạn không phải cư dân ở đó).",
}
SIEGEWAR = {
    "msg_war_siege_battle_session_ended_draw_result": "&b * Hoà @ %s (0)",
    "msg_siege_war_banner_control_reversal_bonus": ". Tốc độ ghi điểm X %s.",
    "status_town_siege_status_crushing_attacker_win": "Bên tấn công thắng áp đảo",
    "status_town_siege_status_crushing_defender_win": "Bên phòng thủ thắng áp đảo",
    "status_town_siege_status_decisive_attacker_win": "Bên tấn công thắng rõ ràng",
    "status_town_siege_status_decisive_defender_win": "Bên phòng thủ thắng rõ ràng",
    "msg_you_will_get_sickness": "&cCẢNH BÁO: Bạn đang ở đất hoang trong vùng vây nhưng không tham chiến. Hãy vào một thị trấn không bị vây hoặc rời vùng vây, nếu không sau %s giây sẽ bị Ốm Chiến Tranh.",
    "msg_you_received_war_sickness": "&cCẢNH BÁO: Bạn đang ở đất hoang trong vùng vây nhưng không tham chiến. Muốn hết Ốm Chiến Tranh thì vào một thị trấn không bị vây hoặc rời vùng vây.",
    "msg_err_capital_towns_cannot_go_peaceful": "&cThủ đô không được thành thị trấn hoà bình.",
    "msg_crushing": "ÁP ĐẢO",
    "status_town_hover_title_changing_peacefulness": "%s[%sĐang đổi trạng thái hoà bình%s]",
    "status_town_hover_title_revolt_immunity": "%s[%sMiễn khởi nghĩa%s]",
    "status_town_hover_title_siege_immunity": "%s[%sMiễn bị vây%s]",
    "status_nation_hover_title_sieges": "%s[%sCác cuộc vây%s]",
    "status_nation_hover_title_war_history": "%s[%sLịch sử chiến tranh%s]",
    "status_nation_hover_title_demoralization": "%s[%sMất tinh thần%s]",
    "siege_status_attacker_decisive_win": "Bên tấn công thắng rõ ràng",
    "siege_status_defender_decisive_win": "Bên phòng thủ thắng rõ ràng",
    "siege_status_attacker_crushing_win": "Bên tấn công thắng áp đảo",
    "siege_status_defender_crushing_win": "Bên phòng thủ thắng áp đảo",
    "siege_lore_format_date": "dd/MM/yyyy HH:mm",
    "msg_err_your_town_cannot_be_peaceful_while_a_capital_city": "&cThị trấn của bạn không đổi sang hoà bình được vì thủ đô không được hoà bình.",
    "msg_err_cannot_capture_capital_town": "&cKhông chiếm được thủ đô.",
    "msg_err_cannot_subvert_capital_town": "&cKhông thu phục được thủ đô.",
    "msg_err_capital_towns_cannot_cancel_non_peaceful_switch": "&cThủ đô không huỷ được việc chuyển sang không hoà bình.",
    "status_town_hover_title_occupied": "%s[%sBị chiếm%s]",
    "status_town_occupied_home_nation": "&2Quốc gia gốc: &a%s",
    "msg_err_cannot_merge_towns_without_same_occupier": "&cKhông sáp nhập được thị trấn bị chiếm, trừ khi hai thị trấn cùng bị một nước chiếm.",
    "sw_command_help_list_peaceful_towns": "Liệt kê thị trấn đang hoà bình.",
    "sw_peaceful_towns_title": "Thị trấn hoà bình",
    "msg_notifications_preference_set": "&bĐã đặt chế độ thông báo: %s.",
}

T = TOWNY if KIND == "towny" else SIEGEWAR


def flat(d, p=""):
    o = {}
    for k, v in (d or {}).items():
        key = f"{p}.{k}" if p else str(k)
        if isinstance(v, dict):
            o.update(flat(v, key))
        else:
            o[key] = v
    return o


def wordy(s):
    return re.search(r"[A-Za-z]{3,}", re.sub(r"<[^>]*>|%[sd]|&.|/\S+", "", str(s))) is not None


en, vi = flat(yaml.safe_load(open(EN, encoding="utf-8"))), flat(yaml.safe_load(open(VI, encoding="utf-8")))
out, left = {}, []
for k, v in en.items():
    if SKIP.match(k):
        continue
    if k in T:
        out[k] = T[k]
    elif k in vi and (vi[k] != v or not wordy(v)):
        out[k] = vi[k]
    elif wordy(v):
        left.append(k)
bad = [k for k in out if isinstance(en[k], str) and sorted(re.findall(r"%[sd]", en[k])) != sorted(re.findall(r"%[sd]", str(out[k])))]
with open(OUT, "w", encoding="utf-8") as f:
    f.write(f"# DuckyMoMo: tiếng Việt cho {KIND} ép cho mọi người chơi (global.yml đè mọi ngôn ngữ). Sinh bằng tools/vi/towny.py.\n")
    yaml.dump(out, f, allow_unicode=True, sort_keys=False, width=10000, default_style=None)
print(f"{KIND}: ghi {len(out)} câu; còn tiếng Anh {len(left)} {left[:15]}; lệch %s/%d: {bad[:10]}")
