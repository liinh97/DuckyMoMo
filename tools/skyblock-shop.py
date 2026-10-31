"""Skyblock (31/10): chỉnh UltimateShop: tiếng Việt (languages/vi_VN.yml dịch từ en_US.yml), đơn vị xu, bỏ shop Premium, hạ giá đá cuội. Chạy lúc server tắt."""
import glob, os, re, yaml

P = "/home/developer/works/DuckyMoMo/data/skyblock-1/plugins/UltimateShop/"

# ---- bản dịch theo cụm, cụm dài thay trước
VI = {
    "Sell Items": "Bán đồ", "Confirm Sale": "Xác nhận bán", "Confirm Purchase": "Xác nhận mua", "Original price": "Giá gốc", "Final multiplier": "Hệ số cuối",
    "Price": "Giá", "Sellable items": "Món bán được", "Unsellable items": "Món không bán được", "Click to sell the items above.": "Bấm để bán các món trên.",
    "Search Filters": "Bộ lọc tìm kiếm", "Reset Search": "Xoá tìm kiếm", "Start a Search": "Bắt đầu tìm", "Search Overview": "Kết quả tìm", "No Matching Products": "Không có món phù hợp",
    "Matched products": "Số món khớp", "Name filter": "Lọc tên", "Shop:": "Shop:", "Product:": "Món:", "Click to trade this product.": "Bấm để mua bán món này.",
    "Sell All": "Bán tất cả", "Open the bulk sell menu for": "Mở menu bán hàng loạt", "your current inventory contents.": "đồ đang có trong túi.",
    "Server Market": "Chợ Skyblock", "Choose Amount": "Chọn số lượng", "Increase the amount by": "Tăng số lượng thêm", "Decrease the amount by": "Giảm số lượng đi",
    "Complete the trade with the": "Giao dịch với", "currently selected amount.": "số lượng đang chọn.", "Next Page >": "Trang sau >", "< Previous Page": "< Trang trước",
    "Browse categories, search products, or open your favourites.": "Chọn mục để mua bán.",
    "Farming": "Nông sản", "Seeds, crops and harvest items.": "Hạt giống, cây trồng, nông sản.", "A simple starting point for survival.": "Món khởi đầu cho đảo mới.",
    "Mob Drops": "Đồ quái rơi", "Bones, pearls, slime and more.": "Xương, ngọc ender, slime...", "Useful loot from hostile mobs.": "Chiến lợi phẩm từ quái.",
    "Concrete": "Bê tông", "All coloured concrete blocks": "Bê tông đủ màu", "for large decorative builds.": "để xây trang trí.",
    "Flowers": "Hoa", "Natural decoration and dye-ready": "Trang trí tự nhiên,", "plants for gardens and paths.": "làm thuốc nhuộm, làm vườn.",
    "Logs & Saplings": "Gỗ & Cây non", "Tree logs and matching saplings": "Gỗ và cây non", "for building and replanting.": "để xây và trồng lại.",
    "Minerals": "Khoáng sản", "Ores, gems and refined resources.": "Quặng, đá quý, thỏi kim loại.", "Higher-value goods for progression.": "Hàng giá trị cao.",
    "Blocks": "Khối xây", "Core building blocks and natural": "Khối xây cơ bản", "materials for everyday projects.": "và vật liệu tự nhiên.",
    "Terracotta": "Đất nung", "Full terracotta colour sets": "Đất nung đủ màu", "for warm and rustic builds.": "cho công trình mộc mạc.",
    "Glass, stained glass and": "Kính thường, kính màu", "window-ready decorative blocks.": "làm cửa sổ, trang trí.", "Glass": "Kính",
    "Wool": "Len", "Coloured wool for banners,": "Len màu làm cờ,", "pixel art and soft decoration.": "tranh khối, trang trí.",
    "Special": "Đặc biệt", "Rare or utility-focused items": "Món hiếm, món tiện ích", "that stand out from daily stock.": "khác hàng thường ngày.",
    "Redstone": "Đá đỏ", "Automation parts and logic": "Linh kiện tự động,", "components for technical builds.": "máy móc đá đỏ.",
    "Fish": "Cá", "Raw fish, cooked fish and": "Cá sống, cá nướng và", "Transports": "Di chuyển", "Transport": "Di chuyển",
    "Blocks Shop": "Khối xây", "Concrete Shop": "Bê tông", "Drops Shop": "Đồ quái rơi", "Farming Shop": "Nông sản", "Fish Shop": "Cá", "Flowers Shop": "Hoa", "Glass Shop": "Kính",
    "Logs Shop": "Gỗ & Cây non", "Minerals Shop": "Khoáng sản", "Redstone Shop": "Đá đỏ", "Special Shop": "Đặc biệt", "Terracotta Shop": "Đất nung", "Wool Shop": "Len",
    "Transport Shop": "Di chuyển", "Daily Shop (Premium)": "Chợ ngày",
    "No sell history": "Chưa bán lần nào", "Esc to confirm sell!": "Esc để xác nhận bán!", "Back": "Quay lại", "Close": "Đóng", "Cancel": "Huỷ", "Buy": "Mua", "Sell": "Bán",
    "Select Amount": "Chọn số lượng", "Select a product": "Chọn món", "Amount": "Số lượng", "Confirm": "Xác nhận", "View details": "Xem chi tiết", "Product Info": "Thông tin món",
    "Type amount here!": "Nhập số lượng!", "Search": "Tìm", "QUICK ACTIONS": "THAO TÁC NHANH", "No extra actions": "Không có thao tác thêm", "Toggle favourite": "Yêu thích",
    "CATEGORIES": "DANH MỤC", "PRODUCTS": "SẢN PHẨM", "Transaction completed": "Giao dịch xong", "Transaction failed": "Giao dịch lỗi", "Transaction cancelled": "Đã huỷ giao dịch",
    "Permission required": "Thiếu quyền", "Your limit was reached": "Đã hết hạn mức của bạn", "Server limit reached": "Đã hết hạn mức server",
    "Insufficient funds or items": "Không đủ xu hoặc đồ", "Requirements not met": "Chưa đủ điều kiện", "Inventory full": "Túi đầy",
    "Purchase:": "Mua:", "Sell:": "Bán:", "VIP Bonus:": "Thưởng VIP:", "Tax:": "Thuế:", "Player Buy Stock": "Bạn đã mua", "Server Buy Stock": "Server đã mua",
    "Player Sell Limit": "Bạn đã bán", "Server Sell Limit": "Server đã bán", "Can not buy more!": "Không mua thêm được!", "Can not sell more!": "Không bán thêm được!",
    "Refresh Time": "Làm mới lúc", "Sold Out!": "Hết hàng!",
    # tin nhắn
    "You can not finish this transaction because your inventory will full after that!": "Túi đầy, không giao dịch được!",
    "All your {amount}x sellable items have been sold, you earned {reward} for this!": "Đã bán {amount} món, nhận {reward}!",
    "Successfully purchased {item}&fx{amount}, cost {price}&f!": "Đã mua {item}&fx{amount}, hết {price}&f!",
    "Successfully sold {item}&fx{amount}, gain {price} &fwith multiplier &cx{multiplier}!": "Đã bán {item}&fx{amount}, nhận {price}&f!",
    "You didn't meet the condition that open this menu!": "Bạn chưa đủ điều kiện mở menu này!",
    "You didn't meet the condition that buy this product!": "Bạn chưa đủ điều kiện mua món này!",
    "You didn't meet the condition that sell this product!": "Bạn chưa đủ điều kiện bán món này!",
    "You do not have enough money to purchase {item}. Required price: {price}.": "Không đủ xu mua {item}. Cần {price}.",
    "You do not have enough {item} to sell.": "Không đủ {item} để bán.",
    "You have to use this command in game!": "Lệnh này phải gõ trong game!",
    "You don't have permission to use this command!": "Bạn không có quyền dùng lệnh này!",
}
ORDER = sorted(VI, key=len, reverse=True)


def tr(s):
    for k in ORDER:
        s = s.replace(k, VI[k])
    return s.replace("&#98FB98[UltimateShop] ", "&6[Chợ] ")


SKIP = {"editor", "help", "command-tab", "plugin", "placeholderapi"}
left = []


def walk(o, path=""):
    if isinstance(o, dict):
        return {k: (v if not path and k in SKIP else walk(v, path + "." + str(k))) for k, v in o.items()}
    if isinstance(o, list):
        return [walk(x, path) for x in o]
    if isinstance(o, str):
        if "keyword" in path:   # từ khoá gõ trong chat giữ nguyên
            return o
        t = tr(o)
        if re.search(r"(?<![{&#\w-])\b(the|you|your|and|with|this|from|for|items?|products?)\b(?![^{]*})", t, re.I):
            left.append(path + " = " + t[:90])
        return t
    return o


src = yaml.safe_load(open(P + "languages/en_US.yml", encoding="utf-8"))
out = walk(src)
yaml.safe_dump(out, open(P + "languages/vi_VN.yml", "w", encoding="utf-8"), allow_unicode=True, sort_keys=False, width=1000)
print("vi_VN.yml xong; còn tiếng Anh:", len(left))
print("\n".join(left[:26]))

# ---- config: ngôn ngữ cố định tiếng Việt
p = P + "config.yml"
s = open(p, encoding="utf-8").read()
s = re.sub(r"^(\s*)language: en_US", r"\1language: vi_VN", s, count=1, flags=re.M)
s = re.sub(r"^(\s*)per-player-language: true", r"\1per-player-language: false", s, count=1, flags=re.M)
open(p, "w", encoding="utf-8", newline="").write(s)

# ---- shop: bỏ shop Premium, đơn vị xu, đá cuội rẻ (máy tạo đá ra vô hạn)
os.makedirs(P + "shops-tat", exist_ok=True)
for n in ("example.yml", "daily.yml"):
    if os.path.exists(P + "shops/" + n):
        os.replace(P + "shops/" + n, P + "shops-tat/" + n)
for f in glob.glob(P + "shops/*.yml"):
    s = open(f, encoding="utf-8").read().replace("'{amount}$'", "'{amount} xu'")
    open(f, "w", encoding="utf-8", newline="").write(s)
p = P + "shops/blocks.yml"
s = open(p, encoding="utf-8").read()
for mat, sell in (("COBBLESTONE", "0.1"), ("STONE", "0.15")):
    s, n = re.subn(r"(material: " + mat + r"\n[\s\S]*?sell-prices:\n\s+1:\n\s+economy-plugin: Vault\n\s+amount: ')[0-9.]+(')", r"\g<1>" + sell + r"\g<2>", s, count=1)
    assert n == 1, mat
open(p, "w", encoding="utf-8", newline="").write(s)

# ---- menu chính: bỏ 2 nút Premium (tìm kiếm, yêu thích)
p = P + "menus/common_menus/main.yml"
s = open(p, encoding="utf-8").read()
assert s.count("'00Y0S0X00'") == 1
s = s.replace("'00Y0S0X00'", "'0000S0000'")
open(p, "w", encoding="utf-8", newline="").write(s)
print("ok shop")
