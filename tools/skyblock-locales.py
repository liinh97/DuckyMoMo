"""Skyblock (31/10): tạo vi.yml cho addon BentoBox thiếu tiếng Việt (Challenges, Upgrades, ControlPanel) và bù khoá thiếu ở Level, MagicCobblestoneGenerator.
Dịch theo cụm trên bản en-US; phần chỉ admin thấy (khung sửa thử thách, sửa nâng cấp) để nguyên tiếng Anh. Chạy lúc server tắt."""
import re
import yaml

L = "/home/developer/works/DuckyMoMo/data/skyblock-1/plugins/BentoBox/locales/"

COMMON = {
    # nút, gợi ý
    "Right Click": "Chuột phải", "Left Click": "Chuột trái", "Shift Click": "Shift + bấm", "Click": "Bấm", "to select.": "để chọn.", "to choose a completion count.": "để chọn số lần.",
    "to choose.": "để chọn.", "to complete all.": "để hoàn thành hết.", "to complete.": "để hoàn thành.", "to return.": "để quay lại.", "to quit.": "để thoát.", "to open.": "để mở.",
    "to view previous page.": "để xem trang trước.", "to view next page.": "để xem trang sau.", "to reduce.": "để giảm.", "to increase.": "để tăng.", "to cancel.": "để huỷ.",
    "Challenges Menu": "Thử Thách", "Select Game Mode": "Chọn chế độ", "How many times?": "Mấy lần?", "Free Challenges": "Thử thách tự do", "Displays a list of": "Danh sách",
    "free challenges": "thử thách tự do", "Return to the previous menu": "Về menu trước", "or exit the GUI": "hoặc đóng", "Return": "Quay lại", "Previous Page": "Trang trước",
    "Next Page": "Trang sau", "Switch to page": "Sang trang", "Complete challenge": "Hoàn thành thử thách", "Complete": "Hoàn thành", "time(s)": "lần", "Quit": "Thoát", "Exit the GUI.": "Đóng menu.",
    "Show Title": "Hiện tiêu đề", "Displays a title when a challenge": "Hiện tiêu đề khi xong thử thách", "or level is completed.": "hoặc cấp.", "Enabled": "Bật", "Disabled": "Tắt",
    # mô tả thử thách
    "Team required to complete": "Cần có đội mới làm được", "Team:": "Đội:", "required)": "cần)", "online (": "online (",
    "out of": "trên", "Completed all": "Đã xong đủ", "Completed": "Đã xong", "times": "lần", "Cool down:": "Hồi:", "Available after:": "Làm lại sau:", "Limited to:": "Chỉ ở:", "Limited to": "Chỉ ở",
    "Requires permissions:": "Cần quyền:", "Requires": "Cần quyền", "permission.": "", "Required Biome(s):": "Cần quần xã:", "Required Blocks:": "Cần khối:", "Required Entities:": "Cần sinh vật:",
    "No further than": "Trong vòng", "meters": "khối", "Blocks will be": "Khối sẽ bị", "Entities will be": "Sinh vật sẽ bị", "Item(s) will be": "Đồ sẽ bị", "removed": "lấy đi",
    "Required Items:": "Cần đồ:", "Required Experience:": "Cần kinh nghiệm:", "Experience will be": "Kinh nghiệm sẽ bị", "Required money:": "Cần xu:", "Money will be": "Xu sẽ bị",
    "Required island level:": "Cần cấp đảo:", "Statistic data will be": "Chỉ số sẽ bị", "reduced": "giảm", "Rewards:": "Phần thưởng:", "Items:": "Đồ:", "Experience:": "Kinh nghiệm:", "Money:": "Xu:", "Commands:": "Lệnh:",
    "Locked": "Chưa mở", "challenges.": "thử thách.", "more challenges need to be": "thử thách nữa cần", "completed to unlock this level.": "hoàn thành để mở cấp này.",
    "challenge(s) can be": "thử thách được", "skipped to unlock the next level.": "bỏ qua để mở cấp sau.",
    "Success!": "Hoàn thành!", "Any Hanging Sign": "Bảng treo bất kỳ", "Any Sign": "Bảng bất kỳ", "Any ": "Bất kỳ ",
    # tin nhắn
    "This challenge has already been completed!": "Thử thách này đã xong rồi!", "This challenge has not been completed yet!": "Thử thách này chưa xong!",
    "You completed the [value] <reset><dark_green>challenge!": "Bạn đã xong thử thách [value]<reset><dark_green>!",
    "You repeated the [value] <reset><dark_green>challenge!": "Bạn làm lại thử thách [value]<reset><dark_green>!",
    "You repeated the [value] <reset><dark_green>challenge [count] times!": "Bạn làm lại thử thách [value]<reset><dark_green> [count] lần!",
    "Luck was not on your side — no reward this time!": "Lần này không may, không có thưởng!", "You completed the [value] <reset><dark_green>level!": "Bạn đã xong cấp [value]<reset><dark_green>!",
    "[name] has completed the [value] <reset><dark_purple>challenge!": "[name] đã xong thử thách [value]<reset><dark_purple>!",
    "[name] has completed the [value] <reset><dark_purple>level!": "[name] đã xong cấp [value]<reset><dark_purple>!",
    "Missing challenge name": "Thiếu tên thử thách", "Unknown challenge": "Không có thử thách này", "The challenge is not deployed!": "Thử thách chưa mở!",
    "You must be on your island to do that!": "Bạn phải đứng trên đảo của mình!", "You have not unlocked the required level to complete this challenge.": "Bạn chưa mở cấp cần cho thử thách này.",
    "This challenge is not repeatable!": "Thử thách này không làm lại được!", "You do not have enough [items] to complete this challenge!": "Bạn chưa đủ [items] cho thử thách này!",
    "You must be standing within [number] blocks of all required items.": "Bạn phải đứng trong vòng [number] khối quanh các món cần.", "You still need [amount] x [item]": "Còn thiếu [amount] x [item]",
    "Cannot complete the challenge: the requirements are not met.": "Chưa đủ điều kiện hoàn thành thử thách.", "You must have [value] in your account to complete the challenge.": "Bạn cần có [value] để hoàn thành thử thách.",
    "You must have [value] EXP to complete this challenge.": "Bạn cần [value] kinh nghiệm cho thử thách này.", "Your island must be at least level [number] to complete this challenge!": "Đảo phải đạt cấp [number] mới làm được thử thách này!",
    "You do not have a high enough rank to do that.": "Vai trò của bạn trên đảo chưa đủ để làm việc này.", "Some items cannot be removed from your inventory!": "Có món không lấy khỏi túi được!",
    "Challenges are not implemented in this world yet!": "Thế giới này chưa có thử thách!", "You do not have permission to complete this challenge multiple times at once.": "Bạn không có quyền làm nhiều lần cùng lúc.",
    "This is a team challenge. You must have a team to complete it!": "Đây là thử thách nhóm, bạn cần có đội!", "This team challenge needs [number] team members online. Only [online] are online.": "Thử thách nhóm cần [number] người online, hiện có [online].",
    "[name] does not have their share ([amount] x [item]) for this team challenge.": "[name] chưa có phần của mình ([amount] x [item]).",
    "This challenge requires a wait time of [timeout] between completions. You must wait [wait-time] before completing it again.": "Thử thách này phải chờ [timeout] giữa 2 lần, còn [wait-time] nữa.",
    "Opens the Challenges GUI.": "Mở bảng thử thách.", "Completes a challenge.": "Hoàn thành một thử thách.",
    "Challenges protection": "Quyền làm thử thách", "Toggle who can": "Chọn ai được", "complete challenges": "làm thử thách", "No challenges outside the island": "Chỉ làm thử thách trên đảo",
    # Upgrades
    "Open the upgrade shop interface": "Mở cửa hàng nâng cấp đảo", "You need to be on your island to do that": "Bạn phải đứng trên đảo của mình", "You dont have any item in your main hand": "Bạn chưa cầm món nào",
    "Couldn't withdraw money. You should talk about this to an administrator": "Không trừ được xu, báo admin giúp nhé", "Before buying this upgrade, you need to place/break at least one block": "Hãy đặt hoặc phá ít nhất 1 khối trước khi mua nâng cấp",
    "An error happened. You should look at the consol for error": "Có lỗi xảy ra, báo admin giúp nhé",
    "Island Min Level:": "Cần cấp đảo:", "limits upgrade of": "tăng giới hạn thêm", "Your island [block] limits was upgraded by [level]": "Giới hạn [block] của đảo tăng thêm [level]", "Max level reached": "Đã đạt tối đa", "Money cost:": "Giá:",
    "limits upgrade": "tăng giới hạn", "Range upgrade of": "Mở rộng đảo thêm", "Your island range was upgraded by [rangelevel] blocks": "Đảo đã rộng thêm [rangelevel] khối", "Range upgrade": "Mở rộng đảo",
    "Island upgrade shop": "Nâng Cấp Đảo", "Don't forget to update your island level with the level command": "Nhớ gõ /is level để cập nhật cấp đảo", "Previous page": "Trang trước", "Next page": "Trang sau",
    "Go to previous page": "Về trang trước", "Go to next page": "Sang trang sau", "Click to go to previous page": "Bấm về trang trước", "Click to go to next page": "Bấm sang trang sau", "Click to purchase upgrade": "Bấm để mua nâng cấp",
    "Level:": "Cấp:", "Exit": "Thoát", "Yes": "Có", "No upgrades yet — click 'Add upgrade' to create one": "Chưa có nâng cấp nào", "Island level": "Cấp đảo", "Min. island level:": "Cấp đảo tối thiểu:",
    "Costs [amount] money": "Giá [amount] xu", "Costs [amount]x [item]": "Giá [amount]x [item]", "Requires permission [permission]": "Cần quyền [permission]", "Increase border by [rangelevel] blocks": "Mở rộng viền thêm [rangelevel] khối",
    "Limits upgrade": "Tăng giới hạn", "Increases [type] limit for [target] by [amount]": "Tăng giới hạn [target] thêm [amount]", "Runs commands on upgrade": "Chạy lệnh khi nâng cấp", "Spawner Boost": "Tăng lồng quái",
    "+[amount] entities per spawner trigger": "+[amount] sinh vật mỗi lần lồng sinh", "Crop Growth Boost": "Tăng tốc cây trồng", "+[amount] extra growth trigger per crop tick": "+[amount] lần lớn thêm mỗi nhịp", "Switch who can upgrade": "Chọn ai được nâng cấp", "Upgrade Rank": "Quyền nâng cấp",
    "Money": "Xu", "Item": "Đồ",
    # ControlPanel
    "opens control panel": "mở bảng điều khiển đảo", "Could not find a valid gamemode.": "Không tìm thấy chế độ chơi.", "Could not find any valid control panel.": "Chưa có bảng điều khiển.",
    # Level
    "donate blocks to permanently raise island level": "góp khối để tăng cấp đảo vĩnh viễn", "You must be on your island to donate blocks.": "Bạn phải đứng trên đảo mới góp khối được.", "You do not have permission to donate blocks on this island.": "Bạn không có quyền góp khối trên đảo này.",
    "That block has no level value.": "Khối này không có giá trị cấp đảo.", "Invalid amount. Use a positive number.": "Số lượng không hợp lệ.", "There are no valid blocks to donate.": "Không có khối nào góp được.", "Donation cancelled. Items returned.": "Đã huỷ góp, trả lại đồ.",
    "Donated [number] blocks for": "Đã góp [number] khối, được", "points! These points are permanent.": "điểm vĩnh viễn!", "Confirm - Items will be DESTROYED!": "Xác nhận - đồ sẽ BỊ HUỶ!", "Donate blocks to your island": "Góp khối cho đảo", "Currently donated:": "Đã góp:", "points": "điểm",
    "Points to add:": "Điểm được thêm:", "These items will be destroyed!": "Các món này sẽ bị huỷ!", "Donated [number] x [material] for": "Đã góp [number] x [material], được", "permanent points!": "điểm vĩnh viễn!", "You must be holding a placeable block to donate.": "Hãy cầm khối đặt được để góp.",
    "About to DESTROY": "Sắp HUỶ", "permanent points.": "điểm vĩnh viễn.", "Toggle who can donate": "Chọn ai được góp", "blocks to raise island level.": "khối tăng cấp đảo.", "Block donation not allowed": "Không được góp khối", "Display blocks permanently": "Xem khối đã góp", "donated to island level.": "vĩnh viễn cho cấp đảo.",
    # MagicCobblestoneGenerator
    "Select Generators": "Chọn máy tạo đá", "Required Generators:": "Cần máy:", "Required Generators": "Máy cần có", "Activate On Unlock": "Tự bật khi mở", "Required Phase:": "Cần giai đoạn:", "Required Blocks Broken:": "Cần phá số khối:",
    "Please confirm that you want to buy": "Xác nhận mua", "for [number].": "giá [number].", "Accept the generators": "Chọn các máy này", "Selected Generators:": "Máy đã chọn:", "Accepts selected generators": "Nhận các máy đã chọn", "and returns.": "và quay lại.",
}
ORDER = sorted(COMMON, key=len, reverse=True)
ADMIN = re.compile(r"(^|\.)(admin\w*|editupgradepanel|edittierpanel|listadmin\w*|islandlevelpricepanel|chatinput|titles)(\.|$)|admindescription|rulequestion|invalidrule|setformula|formula|paneltitle|message\.|\.error\.(load|upgrade|rangeovermax|increase)|materials\.")


def tr(s):
    # che thẻ <...> và [..]? chỉ thay chữ, thẻ không trùng cụm nào
    for k in ORDER:
        s = re.sub(r"(?<![\w<])" + re.escape(k), COMMON[k].replace("\\", r"\\"), s)
    return s


def walk(o, path=""):
    if isinstance(o, dict):
        return {k: walk(v, f"{path}.{k}" if path else str(k)) for k, v in o.items()}
    if isinstance(o, list):
        return [walk(x, path) for x in o]
    if isinstance(o, str) and not (ADMIN.search(path) and "player-gui" not in path and "gamemode-gui" not in path and "multiple-gui" not in path and not path.endswith("titles.challenge-title") and not path.endswith("titles.level-title")):
        return tr(o)
    return o


def load(p):
    return yaml.safe_load(open(p, encoding="utf-8"))


def dump(o, p):
    yaml.safe_dump(o, open(p, "w", encoding="utf-8"), allow_unicode=True, sort_keys=False, width=1000)


for a in ("Challenges", "Upgrades", "ControlPanel"):
    en = load(L + a + "/en-US.yml")
    vi = walk(en)
    vi.setdefault("meta", {})["authors"] = ["DuckyMoMo"]
    dump(vi, L + a + "/vi.yml")
    print("ok", a)


def merge(en, vi, path=""):
    """Bù khoá thiếu, dịch lại chuỗi còn y tiếng Anh."""
    for k, v in en.items():
        p = f"{path}.{k}" if path else str(k)
        if isinstance(v, dict):
            merge(v, vi.setdefault(k, {}), p)
        elif k not in vi or vi[k] == v:
            vi[k] = walk(v, p)


for a in ("Level", "MagicCobblestoneGenerator"):
    en = load(L + a + "/en-US.yml")
    vi = load(L + a + "/vi.yml")
    merge(en, vi)
    dump(vi, L + a + "/vi.yml")
    print("ok", a)

# kiểm: chuỗi người chơi thấy còn tiếng Anh ở Challenges
v = load(L + "Challenges/vi.yml")["challenges"]
for k in ("titles", "buttons"):
    print({x: str(y)[:40] for x, y in v["gui"]["titles"].items() if x in ("player-gui", "gamemode-gui", "multiple-gui")})
    break
print(v["messages"]["you-completed-challenge"], "|", v["gui"]["tips"]["click-to-complete"], "|", v["gui"]["descriptions"]["challenge"]["status"]["completed-times-of"])
print(v["errors"]["not-enough-items"], "|", v["gui"]["descriptions"]["challenge"]["requirements"]["island"]["warning-block"], "|", v["titles"]["challenge-title"])
print(load(L + "Upgrades/vi.yml")["upgrades"]["ui"]["upgradepanel"]["title"], "|", load(L + "Upgrades/vi.yml")["upgrades"]["prices"]["money"]["description"])
