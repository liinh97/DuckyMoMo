"""Tiếng Việt cho EvenMoreFish 2.5.1: thêm câu còn thiếu vào messages.yml (bản vn có sẵn thiếu ~80 câu) và dịch chữ trong menu gui/*.yml.
Giữ nguyên chú thích, thứ tự (ruamel). Chạy trong image panel (có ruamel):
  docker run --rm -v "$PWD":/w -w /w --entrypoint python3 duckymomo-panel tools/vi/evenmorefish.py <thư mục plugins/EvenMoreFish>
Sửa tại chỗ messages.yml và gui/; sau đó chép sang repo nếu cần giữ."""
import re
import sys
from pathlib import Path

from ruamel.yaml import YAML

BASE = Path(sys.argv[1])
y = YAML()
y.preserve_quotes = True
y.width = 10000

MSG = {
    "fish-hunted": "<white><b>{player}</b> vừa săn được <b>{rarity}</b> {fish} dài {length}cm!",
    "lengthless-fish-hunted": "<white><b>{player}</b> vừa săn được <b>{rarity}</b> {fish}!",
    "competition-types.shortest": "con cá ngắn nhất",
    "competition-types.shortest-total": "tổng chiều dài cá ngắn nhất",
    "fisherman-lore": "<white>Người câu: {player}",
    "length-lore": "<white>Dài {length}cm.",
    "leaderboard.most-fish": "<white>#{position} | {player} <white>({amount} <white>con)",
    "leaderboard.single-winner": "<white>{player} thắng cuộc thi {type}. Chúc mừng!",
    "leaderboard.total-players": "<white>Có tổng cộng {amount} người trên bảng xếp hạng.",
    "leaderboard.no-winners": "<white>Không ai có thành tích câu cá.",
    "leaderboard.no-record": "<white>Bạn chưa câu được con nào.",
    "no-sellable-fish": "<white>Bạn không có gì để bán!",
    "help-format": "[noPrefix]<aqua>{command} <yellow>- <white>{description}",
    "help-general.top": "[noPrefix]Xem bảng xếp hạng cuộc thi đang diễn ra.",
    "help-general.help": "[noPrefix]Xem trang này.",
    "help-general.shop": "[noPrefix]Mở cửa hàng bán cá.",
    "help-general.toggle": "[noPrefix]Bật/tắt các tính năng của plugin.",
    "help-general.gui": "[noPrefix]Mở menu chính.",
    "help-general.admin": "[noPrefix]Trợ giúp lệnh admin.",
    "help-general.sellall": "[noPrefix]Bán hết cá trong túi.",
    "help-general.next": "[noPrefix]Xem khi nào có cuộc thi tiếp theo.",
    "help-general.applybaits": "[noPrefix]Lắp mồi vào cần câu.",
    "help-general.journal": "[noPrefix]Xem nhật ký các loại cá đã câu.",
    "help-list.fish": "[noPrefix]Liệt kê cá của một độ hiếm.",
    "help-list.rarities": "[noPrefix]Liệt kê các độ hiếm.",
    "help-competition.start": "[noPrefix]Bắt đầu một cuộc thi đã cài.",
    "help-competition.end": "[noPrefix]Kết thúc cuộc thi hiện tại.",
    "help-competition.test": "[noPrefix]Thử một cuộc thi theo kiểu đã chọn.",
    "help-competition.extend": "[noPrefix]Kéo dài cuộc thi đang diễn ra.",
    "help-admin.bait": "[noPrefix]Đưa mồi cho người chơi.",
    "help-admin.competition": "[noPrefix]Bắt đầu hoặc dừng cuộc thi",
    "help-admin.clearbaits": "[noPrefix]Gỡ hết mồi đã lắp khỏi cần câu.",
    "help-admin.fish": "[noPrefix]Đưa cá cho người chơi.",
    "help-admin.nbt-rod": "[noPrefix]Đưa cần câu NBT riêng (cần để câu cá EMF) cho người chơi.",
    "help-admin.custom-rod": "[noPrefix]Đưa cần câu riêng cho người chơi.",
    "help-admin.reload": "[noPrefix]Nạp lại cấu hình plugin.",
    "help-admin.migrate": "[noPrefix]Chuyển cơ sở dữ liệu từ bản cũ (V2) sang V3",
    "help-admin.addons": "[noPrefix]Liệt kê addon.",
    "help-admin.version": "[noPrefix]Xem thông tin plugin.",
    "help-admin.rawitem": "[noPrefix]Xem NBT gốc của vật phẩm trên tay.",
    "help-admin.database": "[noPrefix]Quản lý cơ sở dữ liệu của plugin.",
    "no-fish-in-place": "<white>Câu cá để giành vị trí này",
    "no-size-in-place": "<white>Câu cá để giành vị trí này",
    "no-competition-running-fish": "<white>Hiện không có cuộc thi nào.",
    "no-competition-running-size": "<white>Hiện không có cuộc thi nào.",
    "no-competitions-scheduled": "<white>Chưa có lịch cuộc thi nào.",
    "competition-time-extended": "<white>Cuộc thi được kéo dài thêm {duration}!",
    "custom-fishing-enabled": "<green>Bật",
    "custom-fishing-disabled": "<red>Tắt",
    "emf-time-remaining.inactive": "<white>Còn {days} ngày {hours} giờ {minutes} phút tới cuộc thi tiếp theo.",
    "emf-time-remaining.active": "<white>Cuộc thi còn: {time-left}",
    "bait-rod-protection": "<white>Đã bảo vệ cần câu có mồi. Muốn sửa cần thì đặt nó vào ô đầu tiên.",
    "bait-invalid-rod": "<white>Không lắp mồi vào cần câu này được!",
    "bait-purchased": "<white>Bạn đã mua {amount}x {bait} với giá {price}.",
    "bait-confirm-purchase": "<white>Bấm lại trong 5 giây để xác nhận mua mồi.",
    "bait-cannot-afford": "<white>Không đủ tiền mua mồi này. Cần {price}.",
    "bait-not-for-sale": "<white>Mồi này không bán.",
    "toggle.fishing.no-permission": "<white>Bạn không bật/tắt được cá đặc biệt.",
    "toggle.fishing.False": "<white>Bạn sẽ không câu ra cá đặc biệt nữa.",
    "toggle.fishing.True": "<white>Bạn sẽ câu ra cá đặc biệt.",
    "toggle.bossbar.no-permission": "<white>Bạn không bật/tắt được thanh cuộc thi.",
    "toggle.bossbar.False": "<white>Bạn sẽ không thấy thanh cuộc thi nữa.",
    "toggle.bossbar.True": "<white>Bạn sẽ thấy thanh cuộc thi.",
    "toggle.catch-message.no-permission": "<white>Bạn không bật/tắt được tin câu cá.",
    "toggle.catch-message.False": "<white>Bạn sẽ không thấy tin câu cá nữa.",
    "toggle.catch-message.True": "<white>Bạn sẽ thấy tin câu cá.",
    "admin.competition-id-invalid": "<white>Mã cuộc thi không hợp lệ.",
    "admin.custom-rod-given": "<white>Đã đưa cần câu riêng cho {player}.",
    "admin.list-addons": "<white>{addon-type} đã đăng ký: ",
    "rarity-invalid": "<white>Độ hiếm không hợp lệ!",
    "journal-disabled": "<white>Nhật ký câu cá đang tắt. Hãy bật cơ sở dữ liệu của plugin.",
    "reward.catch-up": "<white>Bạn có phần thưởng nhận lúc offline, đang trao ngay.",
    "bait.rod-lore": "<white>\n<gray>Ô mồi: <yellow>({current_baits}/{max_baits})\n<white>\n{baits}\n<white>",
    "bait.bait-lore": "<white>\nTăng tỉ lệ câu được:\n{boosts}\n{lore}\n<white>\n<#dadada>Thả lên cần câu để lắp,\n<#dadada>hoặc giữ <u>SHIFT</u> để lắp hết.\n<white>",
    "bait.boosts-rarity": "► <white>1 độ hiếm",
    "bait.boosts-rarities": "► <white>{amount} độ hiếm",
    "bait.boosts-fish": "► <white>{amount} loại cá",
    "bait.unused-slot": "<gray>► ? <i>Ô trống",
    "exploits.afk-fishing.detected": "<white>Chỗ này hết cá rồi. Thử thả câu xa ra ít nhất {range} khối.",
    "exploits.afk-fishing.blocked": "<white>Chỗ này hết cá rồi. Thử thả câu xa ra ít nhất {range} khối.",
}
GUI = {
    "<#006F86>EvenMoreFish - Apply Baits": "<#006F86>Câu cá - Lắp mồi", "<red>Exit": "<red>Thoát", "<yellow>Exit to the Menu": "<yellow>Về menu",
    "<#006F86>EvenMoreFish - Baits": "<#006F86>Câu cá - Mồi", "<aqua>Next Page": "<aqua>Trang sau", "<yellow>Go to the next page": "<yellow>Sang trang sau",
    "<yellow>You are on the last page": "<yellow>Đang ở trang cuối", "<aqua>Last Page": "<aqua>Trang cuối", "<yellow>Go to the last page": "<yellow>Tới trang cuối",
    "<aqua>Previous Page": "<aqua>Trang trước", "<yellow>Go to the previous page": "<yellow>Về trang trước",
    "<yellow>You are on the first page": "<yellow>Đang ở trang đầu", "<aqua>First Page": "<aqua>Trang đầu", "<yellow>Go to the first page": "<yellow>Về trang đầu",
    "<#006F86>EvenMoreFish - Fish Journal": "<#006F86>Câu cá - Nhật ký", "<dark_gray>Undiscovered Fish": "<dark_gray>Cá chưa câu được",
    "<white>Catch this fish": "<white>Câu được loại cá này", "<white>to unlock it here.": "<white>để mở khoá ở đây.",
    "<white>Caught: {times-caught}x": "<white>Đã câu: {times-caught} lần", "<white>Largest Size: {largest-size}cm": "<white>Dài nhất: {largest-size}cm",
    "<white>Smallest Size: {smallest-size}cm": "<white>Ngắn nhất: {smallest-size}cm", "<white>Found: {discover-date}": "<white>Câu lần đầu: {discover-date}",
    "<white>Discovered by: {discoverer}": "<white>Người câu đầu tiên: {discoverer}", "<white>Server Largest: {server-largest}cm": "<white>Dài nhất server: {server-largest}cm",
    "<white>Server Smallest: {server-smallest}cm": "<white>Ngắn nhất server: {server-smallest}cm", "<white>Server Caught: {server-caught}x": "<white>Cả server đã câu: {server-caught} lần",
    "<red>Information": "<red>Thông tin", "<yellow>This page shows all the {rarity} fish you have caught.": "<yellow>Trang này hiện mọi cá {rarity} bạn đã câu.",
    "<dark_gray>Undiscovered Rarity": "<dark_gray>Độ hiếm chưa mở", "<white>Catch a fish from this": "<white>Câu một con cá thuộc",
    "<white>rarity to unlock it here.": "<white>độ hiếm này để mở khoá.", "<aqua>→ Click to view caught fish": "<aqua>→ Bấm để xem cá đã câu",
    "<#006F86>EvenMoreFish - Main Menu": "<#006F86>Câu cá - Menu chính", "<gold>» Toggle Custom Fish": "<gold>» Bật/tắt cá đặc biệt",
    "<gray>Click this button to": "<gray>Bấm nút này để", "<gray>toggle custom fish.": "<gray>bật/tắt cá đặc biệt.",
    "<gold>» Open Fish Shop": "<gold>» Mở cửa hàng cá", "<gray>Sell the fish you catch to": "<gray>Bán cá câu được",
    "<gray>earn a bit of extra money.": "<gray>để kiếm thêm chút tiền.", "<gold>» Command Help": "<gold>» Trợ giúp lệnh",
    "<gray>Shows a list of commands": "<gray>Xem danh sách lệnh", "<gray>you can use for this plugin.": "<gray>dùng được của câu cá.",
    "<gold>» View Baits": "<gold>» Xem mồi", "<gray>Shows all loaded baits": "<gray>Xem mọi loại mồi", "<gold>» Fish Journal": "<gold>» Nhật ký câu cá",
    "<gray>Keep track of all the": "<gray>Theo dõi mọi loại", "<gray>fish you have caught.": "<gray>cá bạn đã câu.",
    "<#006F86>EvenMoreFish - Sell Fish (Confirm)": "<#006F86>Câu cá - Bán cá (xác nhận)", "<bold><gold>Confirm Sell": "<bold><gold>Xác nhận bán",
    "<dark_gray>Fish Shop": "<dark_gray>Cửa hàng cá", "<gray>Sell your fish here to make": "<gray>Bán cá ở đây để", "<gray>some extra money.": "<gray>kiếm thêm tiền.",
    "<yellow>» (Left-click) sell the fish.": "<yellow>» (Chuột trái) bán cá.", "<gray>» (Right-click) cancel.": "<gray>» (Chuột phải) huỷ.",
    "<bold><gold>Confirm Sell All": "<bold><gold>Xác nhận bán hết", "<dark_gray>Inventory": "<dark_gray>Túi đồ",
    "<gray>Click this button to sell": "<gray>Bấm nút này để bán", "<gray>the fish in your inventory to": "<gray>cá trong túi đồ để",
    "<gray>make some extra money.": "<gray>kiếm thêm tiền.", "<#006F86>EvenMoreFish - Sell Fish": "<#006F86>Câu cá - Bán cá", "<bold><gold>Sell": "<bold><gold>Bán",
    "<gray>Click this button to sell all": "<gray>Bấm nút này để bán hết", "<gray>the fish inside the shop menu.": "<gray>cá trong menu cửa hàng.",
    "<bold><gold>Sell All": "<bold><gold>Bán hết", "<gray>the fish in your inventory.": "<gray>cá trong túi đồ.",
}


def ph(s):
    return sorted(re.findall(r"\{[^}]+\}", s))


# 1) messages.yml: thêm câu còn thiếu (chỉ khi chưa có), giữ nguyên câu đã có
p = BASE / "messages.yml"
doc = y.load(p.read_text(encoding="utf-8"))
added = 0
for key, val in MSG.items():
    node, parts = doc, key.split(".")
    for part in parts[:-1]:
        if part not in node or not hasattr(node[part], "get"):
            node[part] = {}
        node = node[part]
    if parts[-1] not in node:
        node[parts[-1]] = val
        added += 1
with p.open("w", encoding="utf-8") as f:
    y.dump(doc, f)

# 2) gui/*.yml: thay chữ khớp nguyên câu
changed = 0
for g in sorted((BASE / "gui").rglob("*.yml")):
    d = y.load(g.read_text(encoding="utf-8"))

    def walk(n):
        global changed
        if hasattr(n, "items"):
            for k in list(n.keys()):
                v = n[k]
                if isinstance(v, str) and v in GUI:
                    n[k] = GUI[v]
                    changed += 1
                else:
                    walk(v)
        elif isinstance(n, list):
            for i, v in enumerate(n):
                if isinstance(v, str) and v in GUI:
                    n[i] = GUI[v]
                    changed += 1
                else:
                    walk(v)
    walk(d)
    with g.open("w", encoding="utf-8") as f:
        y.dump(d, f)
bad = [k for k, v in GUI.items() if ph(k) != ph(v)]
print(f"messages.yml: thêm {added} câu; menu: đổi {changed} chỗ; lệch tham số: {bad}")
