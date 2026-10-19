"""Bản tiếng Việt đầy đủ cho EssentialsX: bản vi có sẵn trong jar + dịch phần còn thiếu / còn tiếng Anh.
Chạy: python3 tools/vi/essentials.py <messages.properties gốc (en)> <messages_vi.properties trong jar> <file ra>
File ra đặt ở plugins/Essentials/messages/messages_vi.properties (banghoi + practice), config.yml: locale: vi.
Nâng EssentialsX: chạy lại, câu mới (nếu có) in ra để dịch thêm vào T."""
import re
import sys

EN, VI, OUT = sys.argv[1], sys.argv[2], sys.argv[3]

# Tên tham số trong cú pháp lệnh (<player>, [reason]…). Chữ phải gõ đúng (set, clear, on|off…) giữ nguyên.
ARG = {"player": "người chơi", "playername": "người chơi", "otherplayer": "người khác", "amount": "số lượng", "world": "thế giới",
       "worldname": "thế giới", "reason": "lý do", "message": "lời nhắn", "line number": "số dòng", "mob": "loài", "speed": "tốc độ",
       "item": "vật phẩm", "itemname": "vật phẩm", "duration": "thời lượng", "text": "chữ", "page": "trang", "pagenumber": "trang",
       "type": "loại", "title": "tiêu đề", "time": "thời gian", "radius": "bán kính", "name": "tên", "mount": "vật cưỡi", "level": "cấp",
       "expire time": "hạn", "delay": "độ trễ", "warp": "điểm warp", "owner": "chủ", "nickname": "biệt danh", "meta param": "thuộc tính",
       "itemmeta": "thuộc tính", "jailname": "tên nhà tù", "id": "mã", "enchantmentname": "phù phép", "enchantment name": "phù phép",
       "args": "tham số", "address": "địa chỉ IP", "datediff": "thời gian", "author": "tác giả", "player/message": "người chơi/lời nhắn"}

T = {
    "banJoin": "Bạn bị cấm vào server. Lý do\\: {0}",
    "discordErrorInvalidPerms": "Bot Discord chưa được cấp đủ quyền\\! Thiếu quyền\\: {0}.",
    "mutedUserSpeaks": "{0} định nói nhưng đang bị cấm chat\\: {1}",
    "chatTypeSpy": "[Theo dõi]",
    "expCommandUsage3Description": "Xem lượng kinh nghiệm của người chơi",
    "expCommandUsage4Description": "Đưa kinh nghiệm của người chơi về 0",
    "pingCommandDescription": "Pong\\!",
    "pong": "Pong\\!",
    "shoutDisabled": "<primary>Chế độ hét <secondary>đã tắt<primary>.",
    "shoutDisabledFor": "<primary>Chế độ hét <secondary>đã tắt <primary>cho <secondary>{0}<primary>.",
    "shoutEnabled": "<primary>Chế độ hét <secondary>đã bật<primary>.",
    "shoutEnabledFor": "<primary>Chế độ hét <secondary>đã bật <primary>cho <secondary>{0}<primary>.",
    "shoutFormat": "<primary>[Hét]<reset> {0}",
    "editsignCommandClear": "<primary>Đã xoá chữ trên biển.",
    "editsignCommandClearLine": "<primary>Đã xoá dòng<secondary> {0}<primary>.",
    "showkitCommandDescription": "Xem đồ trong một kit.",
    "showkitCommandUsage1Description": "Xem tóm tắt đồ trong kit",
    "editsignCommandDescription": "Sửa chữ trên biển.",
    "editsignCommandLimit": "<dark_red>Chữ quá dài, không vừa biển.",
    "editsignCommandNoLine": "<dark_red>Số dòng phải từ <secondary>1-4<dark_red>.",
    "editsignCommandSetSuccess": "<primary>Đã đặt dòng<secondary> {0}<primary> thành \"<secondary>{1}<primary>\".",
    "editsignCommandTarget": "<dark_red>Phải nhìn vào biển mới sửa được chữ.",
    "editsignCopy": "<primary>Đã chép biển\\! Dán bằng <secondary>/{0} paste<primary>.",
    "editsignCopyLine": "<primary>Đã chép dòng <secondary>{0} <primary>của biển\\! Dán bằng <secondary>/{1} paste {0}<primary>.",
    "editsignPaste": "<primary>Đã dán biển\\!",
    "editsignPasteLine": "<primary>Đã dán dòng <secondary>{0} <primary>của biển\\!",
    "editsignCommandUsage1Description": "Đặt chữ cho một dòng của biển đang nhìn",
    "editsignCommandUsage2Description": "Xoá một dòng của biển đang nhìn",
    "editsignCommandUsage3Description": "Chép cả biển (hoặc một dòng) đang nhìn",
    "editsignCommandUsage4Description": "Dán chữ đã chép lên cả biển (hoặc một dòng) đang nhìn",
    "skullCommandDescription": "Đặt chủ cho đầu người chơi",
    "skullCommandUsage1Description": "Lấy đầu của chính mình",
    "skullCommandUsage2Description": "Lấy đầu của người chơi",
    "skullCommandUsage3Description": "Lấy đầu theo texture (mã hash trong link texture hoặc chuỗi Base64)",
    "skullCommandUsage4Description": "Đưa đầu của một người cho một người chơi",
    "skullCommandUsage5Description": "Đưa đầu theo texture (mã hash hoặc chuỗi Base64) cho một người chơi",
    "skullInvalidBase64": "<dark_red>Mã texture không hợp lệ.",
    "smithingtableCommandDescription": "Mở bàn rèn.",
    "socialspyCommandDescription": "Bật/tắt xem lệnh msg/mail của người khác trong chat.",
    "socialspyCommandUsage1Description": "Bật/tắt theo dõi tin nhắn cho mình hoặc người khác",
    "spawnerCommandDescription": "Đổi loài quái của lồng quái.",
    "spawnerCommandUsage1Description": "Đổi loài quái (và độ trễ nếu có) của lồng quái đang nhìn",
    "spawnmobCommandDescription": "Gọi quái.",
    "spawnmobCommandUsage1Description": "Gọi một (hoặc số lượng ghi) con quái tại chỗ bạn (hoặc chỗ người khác)",
    "spawnmobCommandUsage2Description": "Gọi một (hoặc số lượng ghi) con quái cưỡi con khác tại chỗ bạn (hoặc chỗ người khác)",
    "speedCommandDescription": "Đổi tốc độ di chuyển.",
    "speedCommandUsage1Description": "Đặt tốc độ bay hoặc đi bộ",
    "speedCommandUsage2Description": "Đặt một loại tốc độ cho bạn hoặc người khác",
    "stonecutterCommandDescription": "Mở máy cắt đá.",
    "sudoCommandDescription": "Bắt người khác chạy một lệnh.",
    "sudoCommandUsage1Description": "Bắt người chơi chạy lệnh đã ghi",
    "suicideCommandDescription": "Tự sát.",
    "teleportBottom": "<primary>Đang dịch chuyển xuống đáy.",
    "teleportNoAcceptPermission": "<secondary>{0} <dark_red>không có quyền nhận yêu cầu dịch chuyển.",
    "teleportOfflineUnknown": "<primary>Không tìm thấy vị trí cuối cùng của <secondary>{0}<primary>.",
    "tempbanJoin": "Bạn bị cấm vào server trong {0}. Lý do\\: {1}",
    "tempbanCommandDescription": "Cấm tạm thời một người.",
    "tempbanCommandUsage1Description": "Cấm người chơi trong một thời gian, có thể kèm lý do",
    "tempbanipCommandDescription": "Cấm tạm thời một địa chỉ IP.",
    "tempbanipCommandUsage1Description": "Cấm địa chỉ IP trong một thời gian, có thể kèm lý do",
    "thunderCommandDescription": "Bật/tắt sấm sét.",
    "thunderCommandUsage1Description": "Bật/tắt sấm sét (có thể kèm thời lượng)",
    "timeCommandDescription": "Xem/đổi giờ trong thế giới (mặc định thế giới đang đứng).",
    "timeCommandUsage1Description": "Xem giờ ở mọi thế giới",
    "timeCommandUsage2Description": "Đặt giờ cho thế giới hiện tại (hoặc thế giới ghi)",
    "timeCommandUsage3Description": "Cộng thêm giờ cho thế giới hiện tại (hoặc thế giới ghi)",
    "timeWorldAdd": "<primary>Đã chỉnh giờ nhanh thêm<secondary> {0} <primary>ở\\: <secondary>{1}<primary>.",
    "togglejailCommandDescription": "Bỏ tù / thả một người, đưa tới nhà tù đã ghi.",
    "toggleshoutCommandDescription": "Bật/tắt chế độ hét (nói cho cả server)",
    "toggleshoutCommandUsage1Description": "Bật/tắt chế độ hét cho mình hoặc người khác",
    "topCommandDescription": "Dịch chuyển lên khối cao nhất tại chỗ đứng.",
    "tpCommandDescription": "Dịch chuyển tới người chơi.",
    "tpCommandUsage1Description": "Dịch chuyển bạn tới người chơi",
    "tpCommandUsage2Description": "Dịch chuyển người thứ nhất tới người thứ hai",
    "tpaCommandDescription": "Xin dịch chuyển tới người chơi.",
    "tpaCommandUsage1Description": "Xin dịch chuyển tới người chơi",
    "tpaallCommandDescription": "Mời mọi người đang online dịch chuyển tới chỗ bạn.",
    "tpaallCommandUsage1Description": "Mời mọi người dịch chuyển tới chỗ bạn",
    "tpacancelCommandDescription": "Huỷ mọi yêu cầu dịch chuyển đang chờ. Ghi [người chơi] để chỉ huỷ với người đó.",
    "tpacancelCommandUsage1Description": "Huỷ mọi yêu cầu dịch chuyển đang chờ của bạn",
    "tpacancelCommandUsage2Description": "Huỷ yêu cầu dịch chuyển đang chờ với người chơi",
    "tpacceptCommandDescription": "Chấp nhận yêu cầu dịch chuyển.",
    "tpacceptCommandUsage1Description": "Chấp nhận yêu cầu dịch chuyển mới nhất",
    "tpacceptCommandUsage2Description": "Chấp nhận yêu cầu dịch chuyển của người chơi",
    "tpacceptCommandUsage3Description": "Chấp nhận mọi yêu cầu dịch chuyển",
    "tpahereCommandDescription": "Mời người chơi dịch chuyển tới chỗ bạn.",
    "tpahereCommandUsage1Description": "Mời người chơi dịch chuyển tới chỗ bạn",
    "tpallCommandDescription": "Dịch chuyển mọi người đang online tới một người.",
    "tpallCommandUsage1Description": "Dịch chuyển mọi người tới chỗ bạn (hoặc người khác)",
    "tpautoCommandDescription": "Tự chấp nhận yêu cầu dịch chuyển.",
    "tpautoCommandUsage1Description": "Bật/tắt tự chấp nhận tpa cho mình hoặc người khác",
    "tpdenyCommandDescription": "Từ chối yêu cầu dịch chuyển.",
    "tpdenyCommandUsage1Description": "Từ chối yêu cầu dịch chuyển mới nhất",
    "tpdenyCommandUsage2Description": "Từ chối yêu cầu dịch chuyển của người chơi",
    "tpdenyCommandUsage3Description": "Từ chối mọi yêu cầu dịch chuyển",
    "tphereCommandDescription": "Dịch chuyển người chơi tới chỗ bạn.",
    "tphereCommandUsage1Description": "Dịch chuyển người chơi tới chỗ bạn",
    "tpoCommandDescription": "Dịch chuyển bỏ qua cài đặt chặn dịch chuyển.",
    "tpoCommandUsage1Description": "Dịch chuyển người chơi tới chỗ bạn, bỏ qua cài đặt của họ",
    "tpoCommandUsage2Description": "Dịch chuyển người thứ nhất tới người thứ hai, bỏ qua cài đặt của họ",
    "tpofflineCommandDescription": "Dịch chuyển tới chỗ người chơi thoát game lần cuối",
    "tpofflineCommandUsage1Description": "Dịch chuyển bạn tới chỗ người chơi thoát game",
    "tpohereCommandDescription": "Kéo người chơi tới chỗ bạn, bỏ qua cài đặt chặn dịch chuyển.",
    "tpohereCommandUsage1Description": "Dịch chuyển người chơi tới chỗ bạn, bỏ qua cài đặt của họ",
    "tpposCommandDescription": "Dịch chuyển tới toạ độ.",
    "tpposCommandUsage1Description": "Dịch chuyển bạn tới toạ độ (có thể kèm hướng nhìn, thế giới)",
    "tprCommandDescription": "Dịch chuyển ngẫu nhiên.",
    "tprCommandUsage1Description": "Dịch chuyển bạn tới chỗ ngẫu nhiên",
    "tprCommandUsage2Description": "Dịch chuyển bạn tới chỗ ngẫu nhiên trong thế giới đã ghi",
    "tprCommandUsage3Description": "Dịch chuyển người chơi tới chỗ ngẫu nhiên trong thế giới đã ghi",
    "tprOtherUser": "<primary>Đang dịch chuyển<secondary> {0}<primary> tới chỗ ngẫu nhiên.",
    "tprSuccess": "<primary>Đang dịch chuyển tới chỗ ngẫu nhiên...",
    "tprSuccessDone": "<primary>Bạn đã được dịch chuyển tới chỗ ngẫu nhiên.",
    "tprNoPermission": "<dark_red>Bạn không có quyền dùng điểm đó.",
    "tprNotExist": "<dark_red>Điểm dịch chuyển ngẫu nhiên đó không tồn tại.",
    "tptoggleCommandDescription": "Chặn mọi kiểu dịch chuyển tới bạn.",
    "tptoggleCommandUsageDescription": "Bật/tắt nhận dịch chuyển cho mình hoặc người khác",
    "tradeSignFull": "<dark_red>Biển này đã đầy\\!",
    "tradeSignSameType": "<dark_red>Không đổi được cùng một loại vật phẩm.",
    "treeCommandDescription": "Mọc cây ở chỗ đang nhìn.",
    "treeCommandUsage1Description": "Mọc một cây loại đã ghi ở chỗ đang nhìn",
    "unbanCommandDescription": "Bỏ cấm người chơi.",
    "unbanCommandUsage1Description": "Bỏ cấm người chơi",
    "unbanipCommandDescription": "Bỏ cấm địa chỉ IP.",
    "unbanipCommandUsage1Description": "Bỏ cấm địa chỉ IP",
    "unlimitedCommandDescription": "Cho đặt vật phẩm không giới hạn.",
    "unlimitedCommandUsage1Description": "Xem danh sách vật phẩm không giới hạn của mình hoặc người khác",
    "unlimitedCommandUsage2Description": "Bật/tắt không giới hạn cho vật phẩm (mình hoặc người khác)",
    "unlimitedCommandUsage3Description": "Xoá mọi vật phẩm không giới hạn của mình hoặc người khác",
    "unlinkCommandDescription": "Huỷ liên kết tài khoản Minecraft với Discord.",
    "unlinkCommandUsage1Description": "Huỷ liên kết tài khoản Minecraft với Discord.",
    "unmuteNotify": "<secondary>{0} <primary>đã bỏ cấm chat<secondary> {1}<primary>.",
    "unsafeTeleportDestination": "<dark_red>Điểm đến không an toàn.",
    "playerNotMuted": "<secondary>{0} <primary>không bị cấm chat.",
    "unsupportedBrand": "<dark_red>Nền tảng server hiện tại không hỗ trợ tính năng này.",
    "usermapEntry": "<secondary>{0} <primary>ứng với <secondary>{1}<primary>.",
    "usermapKnown": "<primary>Bộ nhớ tạm có <secondary>{0} <primary>người dùng với <secondary>{1} <primary>cặp tên - UUID.",
    "usermapPurge": "<primary>Đang tìm file userdata không khớp, kết quả ghi vào console. Chế độ xoá\\: {0}",
    "usermapSize": "<primary>Người dùng trong bộ nhớ tạm: <secondary>{0}<primary>/<secondary>{1}<primary>/<secondary>{2}<primary>.",
    "vanishCommandDescription": "Ẩn mình khỏi người chơi khác.",
    "vanishCommandUsage1Description": "Bật/tắt ẩn mình cho mình hoặc người khác",
    "versionCheckDisabled": "<primary>Đã tắt kiểm tra cập nhật trong config.",
    "versionCustom": "<primary>Không kiểm tra được phiên bản\\! Tự build? Thông tin build\\: <secondary>{0}<primary>.",
    "versionDevBehind": "<dark_red>Bản EssentialsX dev đang cũ <secondary>{0} <dark_red>bản\\!",
    "versionDevDiverged": "<primary>Đang chạy bản thử nghiệm của EssentialsX, cũ hơn bản dev mới nhất <secondary>{0} <primary>bản\\!",
    "versionDevDivergedBranch": "<primary>Nhánh tính năng\\: <secondary>{0}<primary>.",
    "versionDevDivergedLatest": "<primary>Đang chạy bản thử nghiệm mới nhất của EssentialsX\\!",
    "versionDevLatest": "<primary>Đang chạy bản dev mới nhất của EssentialsX\\!",
    "versionError": "<dark_red>Lỗi khi lấy thông tin phiên bản EssentialsX\\! Thông tin build\\: <secondary>{0}<primary>.",
    "versionErrorPlayer": "<primary>Lỗi khi kiểm tra phiên bản EssentialsX\\!",
    "versionFetching": "<primary>Đang lấy thông tin phiên bản...",
    "versionOutputEconLayer": "<primary>Lớp kinh tế\\: <reset>{0}",
    "versionReleaseLatest": "<primary>Đang chạy bản ổn định mới nhất của EssentialsX\\!",
    "versionReleaseNew": "<dark_red>Có bản EssentialsX mới\\: <secondary>{0}<dark_red>.",
    "versionReleaseNewLink": "<dark_red>Tải tại\\:<secondary> {0}",
    "voiceSilencedTime": "<primary>Bạn bị cấm nói trong {0}\\!",
    "voiceSilencedReasonTime": "<primary>Bạn bị cấm nói trong {0}\\! Lý do\\: <secondary>{1}",
    "warpCommandDescription": "Xem danh sách warp hoặc dịch chuyển tới một warp.",
    "warpCommandUsage1Description": "Xem danh sách warp (trang đầu hoặc trang ghi)",
    "warpCommandUsage2Description": "Dịch chuyển bạn (hoặc người khác) tới warp",
    "warpInfo": "<primary>Thông tin warp<secondary> {0}<primary>\\:",
    "warpinfoCommandDescription": "Xem vị trí của một warp.",
    "warpinfoCommandUsage1Description": "Xem thông tin warp",
    "weatherCommandDescription": "Đặt thời tiết.",
    "weatherCommandUsage1Description": "Đặt thời tiết (có thể kèm thời lượng)",
    "whoisCommandDescription": "Xem thông tin cơ bản của người chơi.",
    "whoisCommandUsage1Description": "Xem thông tin cơ bản của người chơi",
    "whoisTop": "<primary> \\=\\=\\=\\=\\=\\= Thông tin\\:<secondary> {0} <primary>\\=\\=\\=\\=\\=\\=",
    "whoisUuid": "<primary> - UUID\\:<reset> {0}",
    "whoisFirstLogin": "<primary> - Vào lần đầu:<reset> {0}",
    "whoisWhitelist": "<primary> - Danh sách trắng\\:<reset> {0}",
    "workbenchCommandDescription": "Mở bàn chế tạo.",
    "worldCommandDescription": "Chuyển thế giới.",
    "worldCommandUsage1Description": "Dịch chuyển tới chỗ tương ứng ở Nether hoặc thế giới thường",
    "worldCommandUsage2Description": "Dịch chuyển tới chỗ của bạn trong thế giới đã ghi",
    "worthCommandDescription": "Xem giá bán của đồ trên tay hoặc đồ đã ghi.",
    "worthCommandUsage1Description": "Xem giá bán toàn bộ (hoặc số lượng ghi) vật phẩm đó trong túi",
    "worthCommandUsage2Description": "Xem giá bán toàn bộ (hoặc số lượng ghi) đồ đang cầm",
    "worthCommandUsage3Description": "Xem giá bán mọi thứ trong túi",
    "worthCommandUsage4Description": "Xem giá bán toàn bộ (hoặc số lượng ghi) khối trong túi",
}


def read(path):
    keys, vals = [], {}
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        if not line.strip() or line.lstrip().startswith(("#", "!")) or "=" not in line:
            continue
        k, v = line.split("=", 1)
        keys.append(k.strip())
        vals[k.strip()] = v
    return keys, vals


def usage(v):
    def rep(m):
        inner = m.group(2)
        if inner == "command" or "|" in inner:
            return m.group(0)
        return m.group(1) + ARG.get(inner, inner) + m.group(3)
    return re.sub(r"([<\[])([a-z][a-z /]*?)([>\]])", rep, v).replace("[player\\:]", "[người chơi\\:]")


def wordy(s):
    return re.search(r"[A-Za-z]{3,}", re.sub(r"<[^>]*>|\{[^}]*\}|\[[^\]]*\]|/\S+", "", s)) is not None


en_keys, en = read(EN)
_, vi = read(VI)
out, left = {}, []
for k in en_keys:
    v = vi.get(k, en[k])
    if k in T:
        v = T[k]
    elif re.match(r"^[A-Za-z0-9]+CommandUsage\d*$", k):
        v = usage(v)
    elif v == en[k] and wordy(v):
        left.append(k)
    out[k] = v
with open(OUT, "w", encoding="utf-8") as f:
    f.write("# DuckyMoMo: tiếng Việt đầy đủ cho EssentialsX (bản vi có sẵn + dịch thêm, sinh bằng tools/vi/essentials.py)\n")
    for k in en_keys:
        f.write(f"{k}={out[k]}\n")
print(f"{len(en_keys)} khoá, dịch thêm {len(T)}, cú pháp lệnh đã dịch tham số; còn tiếng Anh: {len(left)}", left[:20])
