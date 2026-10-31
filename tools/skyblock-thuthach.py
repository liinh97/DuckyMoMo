"""Skyblock (31/10): dịch bộ thử thách mặc định của Challenges (default.json) sang tiếng Việt -> vietnam.json, nạp bằng /bsbadmin challenges › Import Database."""
import json

R = "/home/developer/works/DuckyMoMo/data/skyblock-1/plugins/BentoBox/addons/Challenges/"
LV = {
    "novice": ("Tập Sự", "Tập Sự"), "competent": ("Lành Nghề", "Lành Nghề"), "expert": ("Chuyên Gia", "Chuyên Gia"),
    "advanced": ("Bậc Thầy", "Bậc Thầy"), "elite": ("Huyền Thoại", "Huyền Thoại"),
}
# mã: (tên, [mô tả], phần thưởng, phần thưởng khi làm lại)
T = {
    "cobblecollector": ("Đổi Đá Cuội", ["Đổi đá cuội lấy", "vật liệu hữu ích"], "Một ít sỏi luôn có ích"),
    "seedcollector": ("Đổi Hạt Giống", ["Đổi hạt giống lấy", "vật liệu hữu ích"], "Một ít đất luôn có ích"),
    "cactuscollector": ("Đổi Xương Rồng", ["Đổi xương rồng lấy", "vật liệu hữu ích"], "Một ít cát luôn có ích"),
    "treecollector": ("Đổi Gỗ", ["Đốn cây, gom gỗ"], "Đổi gỗ lấy đất sét"),
    "cobblegenerator": ("Máy Tạo Đá Cuội", ["Mọi thứ bắt đầu từ", "một máy tạo đá cuội"], "Dụng cụ giúp bạn cày nhanh hơn"),
    "cactusfarm": ("Trại Xương Rồng", ["Dựng một trại", "xương rồng đơn giản"], "Thêm cát luôn có ích"),
    "melonfarm": ("Trại Dưa Hấu", ["Dựng một trại", "dưa hấu đơn giản"], "Thêm đất luôn có ích"),
    "mushroomfarm": ("Trại Nấm", ["Dựng một trại", "nấm đơn giản"], "Đất nấm giúp nấm mọc nhanh hơn"),
    "pumpkinfarm": ("Trại Bí Ngô", ["Dựng một trại", "bí ngô đơn giản"], "Thêm đất luôn có ích"),
    "sugarcanefarm": ("Trại Mía", ["Dựng một trại", "mía đơn giản"], "Thêm cát để mở rộng trại"),
    "treefarm": ("Trại Cây", ["Dựng một trại", "cây đơn giản"], "Thêm đất luôn có ích"),
    "applecollector": ("Hái Táo", ["Tìm và hái táo", "từ cây"], "Cây non để trồng thêm"),
    "melonmaker": ("Người Hái Dưa", ["Trồng, hái và", "chế dưa hấu"], "Dưa đổi thành khối đất sét"),
    "papermaker": ("Thợ Làm Giấy", ["Gom mía và chế giấy"], "Giấy đổi thành cát đỏ"),
    "pumpkinmaker": ("Người Hái Bí", ["Trồng và hái bí ngô"], "Bí đổi thành khối đất sét"),
    "chiseledmaker": ("Thợ Gạch Chạm", ["Nung đá, chế", "gạch đá chạm khắc"], "Gạch đổi thành andesit"),
    "crackedmaker": ("Thợ Gạch Nứt", ["Nung gạch đá", "thành gạch nứt"], "Gạch đổi thành diorit"),
    "stonesmelter": ("Thợ Gạch Đá", ["Nung đá, chế gạch đá"], "Gạch đổi thành granit"),
    "beetrootfarm": ("Trại Củ Dền", ["Dựng một trại", "củ dền đơn giản"], "Thêm đất luôn có ích"),
    "carrotfarm": ("Trại Cà Rốt", ["Dựng một trại", "cà rốt đơn giản"], "Thêm đất luôn có ích"),
    "potatofarm": ("Trại Khoai Tây", ["Dựng một trại", "khoai tây đơn giản"], "Thêm đất luôn có ích"),
    "animalfarm": ("Trại Thú", ["Làm một bãi cỏ", "chờ thú xuất hiện"], "Sắt chế đồ và vài chú mèo"),
    "mobfarm": ("Trại Quái", ["Làm chỗ tối để", "quái thù địch sinh ra"], "Sắt chế đồ và vài chú chó"),
    "visitnether": ("Xuống Địa Ngục", ["Địa Ngục đáng sợ nhưng", "có nhiều vật liệu quý"], "Sắt và lưu ly cho công sức của bạn"),
    "angler": ("Người Câu Hồi", ["Câu cá hồi"], "Tinh thể lăng trụ và tảo bẹ"),
    "fisherman": ("Ngư Dân", ["Câu cá tuyết"], "Mảnh lăng trụ và dưa biển"),
    "grinder": ("Thợ Cày Quái", ["Dùng trại quái", "gom đồ quái rơi"], "Đổi đồ rơi lấy vật liệu hữu ích"),
    "competent": ("Đảo Lành Nghề", ["Đạt cấp đảo 20"], "Mở được cổng Địa Ngục"),
    "irongolemfarm": ("Trại Sắt", ["Dùng dân làng tạo", "golem sắt miễn phí"], "Sách Tu sửa và quặng đá đỏ"),
    "slimefarm": ("Trại Slime", ["Làm trại slime", "lấy khối slime"], "Quặng thạch anh và sách Tu sửa"),
    "snowgolemfarm": ("Trại Tuyết", ["Tạo một golem tuyết"], "Sách Tu sửa và quặng lưu ly"),
    "villagerbreeder": ("Nhân Giống Dân Làng", ["Bắt đầu gây dựng", "đội quân dân làng"], "Sắt chế dụng cụ và vài chú mèo"),
    "eggcollector": ("Nhặt Trứng", ["Làm trại gà", "và nhặt trứng"], "Đổi trứng lấy sắt"),
    "flowercollector": ("Hái Hoa", ["Gom đủ các loại hoa nhỏ"], "Hoa lớn luôn đẹp hơn"),
    "milkcollector": ("Vắt Sữa Bò", ["Làm xô và vắt sữa bò"], "Đổi sữa lấy bò nấm"),
    "woolcollector": ("Gom Len", ["Gom đủ mọi màu len"], "Vật dụng hữu ích đổi lấy len"),
    "slimeballcollector": ("Gom Bóng Slime", ["Hạ slime, gom bóng slime"], "Đá đỏ luôn có ích"),
    "ironage": ("Thời Đại Sắt", ["Chế dụng cụ và giáp sắt"], "Đổi đồ sắt lấy kim cương"),
    "trader": ("Nhà Buôn", ["Giao dịch với dân làng", "để có ngọc lục bảo"], "Đổi ngọc lục bảo lấy kim cương"),
    "expert": ("Đảo Chuyên Gia", ["Đạt cấp đảo 100"], "Thêm kim cương"),
    "alchemist": ("Nhà Giả Kim", ["Pha vài loại thuốc"], "Kim cương không bao giờ thừa"),
    "dj": ("Tay Chơi Đĩa", ["Chế máy hát và", "gom đủ mọi đĩa nhạc"], "Kim cương và ngọc lục bảo cho bộ đĩa"),
    "icefarmer": ("Thợ Băng", ["Gom băng"], "Thêm kim cương và hộp shulker"),
    "goldage": ("Thời Đại Vàng", ["Chế dụng cụ và giáp vàng"], "Đổi đồ vàng lấy kim cương"),
    "witherkiller": ("Diệt Wither", ["Hạ Wither để", "chứng tỏ bản lĩnh"], "Thêm kim cương và hộp shulker"),
    "chef": ("Đầu Bếp", ["Chứng tỏ tay nghề", "nấu đủ mọi món"], "Thêm kim cương và hộp shulker"),
    "zoomaker": ("Sở Thú", ["Làm một sở thú", "toàn thú hiền"], "Thêm kim cương và hộp shulker"),
    "advanced": ("Đảo Bậc Thầy", ["Đạt cấp đảo 500"], "Thêm kim cương và vỏ shulker"),
    "visitend": ("Tới The End", ["Sau tận cùng", "liệu còn gì nữa?"], "Kim cương và cánh elytra"),
    "diamondage": ("Thời Đại Kim Cương", ["Chế dụng cụ và", "giáp kim cương"], "Đổi đồ kim cương lấy hộp shulker"),
    "glassmaker": ("Thợ Kính", ["Chế và gom đủ", "mọi màu kính"], "Đổi kính lấy hộp shulker"),
    "terracottamaker": ("Thợ Đất Nung", ["Nung đất sét", "thành đất nung"], "Đổi đất nung lấy hộp shulker"),
    "beaconator": ("Hải Đăng", ["Dựng hải đăng", "trên nền sắt"], "Thêm kim cương và hộp shulker"),
    "enderkiller": ("Sở Thú Quái Vật", ["Làm sở thú quái thù địch"], "Thêm kim cương và hộp shulker"),
    "engineer": ("Kỹ Sư", ["Làm vài cỗ máy đá đỏ"], "Thêm kim cương và hộp shulker"),
    "waterzoo": ("Thuỷ Cung", ["Làm một thuỷ cung"], "Thêm kim cương và hộp shulker"),
    "elite": ("Đảo Huyền Thoại", ["Đạt cấp đảo 10.000"], "Thêm kim cương và hộp shulker"),
    "allhandsondeck": ("Đủ Mặt Anh Em", ["Thử thách nhóm:", "cả đảo online cùng lúc"], "Phần thưởng cho cả đội"),
    "pooledtribute": ("Góp Kim Cương", ["Thử thách nhóm:", "cùng nộp 100 kim cương,", "ai online cũng góp được"], "Một khối kim cương cho đội"),
    "synchronizedbuild": ("Cùng Xây Đảo", ["Thử thách nhóm: 5 khối", "kim cương gần bạn, nửa", "đội online"], "Một hải đăng cho công trình"),
    "combinedeffort": ("Đồng Lòng", ["Thử thách nhóm:", "cùng hạ 1.000 quái"], "Một vật tổ cho đội"),
    "rollcallfeast": ("Bữa Tiệc Điểm Danh", ["Thử thách nhóm: 20 miếng", "bít tết chia đều, cả đội", "phải online"], "Mỗi người 2 táo vàng"),
}

d = json.load(open(R + "default.json", encoding="utf-8"))
assert {c["uniqueId"] for c in d["challengeList"]} == set(T), {c["uniqueId"] for c in d["challengeList"]} ^ set(T)
for c in d["challengeList"]:
    name, desc, reward = T[c["uniqueId"]]
    c["friendlyName"] = "&e&l " + name
    c["description"] = ["&7 " + x for x in desc]
    c["rewardText"] = "&7 " + reward
    if c.get("repeatRewardText"):
        c["repeatRewardText"] = "&7 " + reward
for l in d["challengeLevelList"]:
    name = LV[l["uniqueId"]][0]
    l["friendlyName"] = "&e&l " + name
    l["rewardText"] = f"&7 Thưởng khi xong mọi\n&7 thử thách {name}"
    if l.get("unlockMessage"):
        l["unlockMessage"] = f"&7 Chúc mừng! Đã mở cấp &d{name}&7!"
json.dump(d, open(R + "vietnam.json", "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print("ok", len(d["challengeList"]), "thử thách,", len(d["challengeLevelList"]), "cấp")
