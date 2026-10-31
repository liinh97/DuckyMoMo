#!/usr/bin/env python3
"""Việt hoá menu SimplePets (pet trang trí /pet): tên nút (Items/*.json), tiêu đề menu (Inventories/*.json),
tên loài + các lựa chọn dữ liệu trong Pets/*.json (Half Scale: true -> Nửa cỡ: bật...).

Dùng: python3 tools/vi/simplepets-menu.py <thư mục SimplePets> [...]
  Chạy cho bản mẫu trước (modes/banghoi/server/plugins/SimplePets, lobby/plugins/SimplePets) rồi mới tới data/.
Chạy lại nhiều lần không sao (chuỗi đã dịch thì bỏ qua). Tên loài lấy từ display_name đã Việt hoá ("&d✿ &fMèo &7(...)").
"""
import json, os, re, sys

COLOR = re.compile(r'(&#[0-9A-Fa-f]{6}|&[0-9a-fk-orA-FK-OR])')

FIXED = {   # cả chuỗi (đã bỏ mã màu ở hai đầu)
    'Toggle Pet as Hat': 'Đội pet lên đầu', 'Install Addons': 'Cài tiện ích', 'Name Pet': 'Đặt tên pet',
    'Click Here to go': 'Bấm để sang', 'the next page': 'trang sau', 'the previous page': 'trang trước',
    'Click Here to remove this pet': 'Bấm để cất pet này',
    '... or all pets if you have the selector screen open.': '... hoặc cất hết pet nếu đang mở màn chọn.',
    'Shift+Click to select a pet': 'Shift + bấm để chọn một pet', 'Remove Pets': 'Cất pet',
    'Ride Your Pet': 'Cưỡi pet', 'Click here to save your current': 'Bấm để lưu pet đang gọi',
    'pet for you to spawn later': 'để gọi lại sau', 'Save Pet': 'Lưu pet',
    'View the pets you have saved': 'Xem các pet đã lưu', 'Pet Saves': 'Pet đã lưu',
    'SimplePets Addon Modules': 'Tiện ích SimplePets', 'Pet Data Changer': 'Chỉnh pet', 'Pets': 'Thú cưng',
}
LORE = {'Previous: ': 'Trước: ', 'Current: ': 'Đang chọn: ', 'Next: ': 'Sau: '}
FIELD = {
    'Half Scale': 'Nửa cỡ', 'Visible': 'Hiện hình', 'Frozen': 'Đứng yên', 'Silent': 'Im lặng', 'Burning': 'Bốc cháy',
    'Baby': 'Con non', 'Saddled': 'Có yên', 'Shaking': 'Run rẩy', 'Sitting': 'Ngồi', 'Eating': 'Đang ăn',
    'Raised Arms': 'Giơ tay', 'Variant': 'Kiểu', 'Chest': 'Có rương', 'Size': 'Cỡ', 'Tamed': 'Đã thuần',
    'Sleep': 'Ngủ', 'Armor': 'Giáp', 'Rainbow': 'Cầu vồng', 'Profession': 'Nghề', 'Biome': 'Vùng', 'Level': 'Bậc',
    'Angry': 'Giận', 'Type': 'Loại', 'Dancing': 'Nhảy múa', 'Powered': 'Tích điện', 'Screaming': 'Gào thét',
    'Spell': 'Phép', 'Sheared': 'Đã xén lông', 'Skin': 'Màu lông', 'Anger-level': 'Mức giận', 'Vibration': 'Rung',
    'Tilted': 'Nghiêng', 'Playing Dead': 'Giả chết', 'Croaking': 'Kêu', 'Tongue': 'Thè lưỡi', 'Charging': 'Lao tới',
    'Pattern': 'Hoa văn', 'Standing': 'Đứng dậy', 'State': 'Trạng thái', 'Left-horn': 'Sừng trái',
    'Right-horn': 'Sừng phải', 'Nectar': 'Có mật', 'Stinger': 'Có ngòi', 'Flipped': 'Lộn ngược', 'Glowing': 'Phát sáng',
    'Sneeze': 'Hắt hơi', 'Color': 'Màu', 'Style': 'Đốm', 'Johnny': 'Johnny', 'Closed': 'Đóng vỏ',
    'Oxidation': 'Gỉ đồng', 'Pumpkin': 'Đội bí ngô', 'Shielded': 'Có khiên', 'Small': 'Nhỏ', 'Head Up': 'Ngẩng đầu',
    'Potion': 'Cầm thuốc', 'Interested': 'Tò mò', 'Crouching': 'Rình', 'Chomping': 'Đớp', 'Hanging': 'Treo ngược',
    'Phase': 'Tư thế',
}
VALUE = {
    'true': 'bật', 'false': 'tắt', 'None': 'không', 'NONE': 'không',
    'TEMPERATE': 'ôn đới', 'WARM': 'nóng', 'COLD': 'lạnh', 'SMALL': 'nhỏ', 'MEDIUM': 'vừa', 'LARGE': 'lớn',
    'ARMORER': 'thợ giáp', 'BUTCHER': 'đồ tể', 'CARTOGRAPHER': 'người vẽ bản đồ', 'CLERIC': 'thầy tu',
    'FARMER': 'nông dân', 'FISHERMAN': 'ngư dân', 'FLETCHER': 'thợ cung', 'LEATHERWORKER': 'thợ da',
    'LIBRARIAN': 'thủ thư', 'MASON': 'thợ đá', 'NITWIT': 'kẻ ngốc', 'SHEPHERD': 'người chăn cừu',
    'TOOLSMITH': 'thợ rèn dụng cụ', 'WEAPONSMITH': 'thợ rèn vũ khí',
    'DESERT': 'sa mạc', 'JUNGLE': 'rừng rậm', 'PLAINS': 'đồng bằng', 'SAVANNA': 'thảo nguyên', 'SNOW': 'tuyết',
    'SWAMP': 'đầm lầy', 'TAIGA': 'rừng lá kim',
    'NOVICE': 'tập sự', 'APPRENTICE': 'học việc', 'JOURNEYMAN': 'thành thạo', 'EXPERT': 'chuyên gia', 'MASTER': 'bậc thầy',
    'GOLD': 'vàng', 'CYAN': 'lam ngọc', 'BLUE': 'xanh dương', 'RED': 'đỏ', 'GREEN': 'xanh lá', 'GRAY': 'xám',
    'BLACK': 'đen', 'WHITE': 'trắng', 'BROWN': 'nâu', 'CREAMY': 'kem', 'CHESTNUT': 'hạt dẻ', 'DARKBROWN': 'nâu sẫm',
    'BLACK_AND_WHITE': 'đen trắng', 'SALT_AND_PEPPER': 'muối tiêu', 'THE_KILLER_BUNNY': 'thỏ sát thủ',
    'Summon Vex': 'gọi Vex', 'Fangs': 'nanh', 'Wololo': 'Wololo', 'Disappear': 'tàng hình', 'Blindness': 'làm mù',
    'CALM': 'bình tĩnh', 'AGITATED': 'bực', 'ANGRY': 'giận dữ',
    'PALE': 'nhạt', 'SPOTTED': 'đốm', 'SNOWY': 'tuyết', 'ASHEN': 'tro', 'RUSTY': 'gỉ', 'WOODS': 'rừng', 'STRIPED': 'vằn',
    'LUCY': 'hồng', 'WILD': 'hoang',
    'IDLING': 'nghỉ', 'FEELING_HAPPY': 'vui', 'SCENTING': 'đánh hơi', 'SNIFFING': 'hít ngửi', 'DIGGING': 'đào bới',
    'NORMAL': 'thường', 'LAZY': 'lười', 'WORRIED': 'lo lắng', 'PLAYFUL': 'nghịch', 'WEAK': 'yếu', 'AGGRESSIVE': 'hung dữ',
    'LEATHER': 'da', 'COPPER': 'đồng', 'IRON': 'sắt', 'DIAMOND': 'kim cương', 'NETHERITE': 'netherit',
    'WHITEFIELD': 'mảng trắng', 'WHITE_DOTS': 'chấm trắng', 'BLACK_DOTS': 'chấm đen',
    'UNAFFECTED': 'mới', 'EXPOSED': 'hơi gỉ', 'WEATHERED': 'gỉ nhiều', 'OXIDIZED': 'gỉ hết',
    'TABBY': 'mướp', 'SIAMESE': 'Xiêm', 'BRITISH_SHORTHAIR': 'lông ngắn', 'CALICO': 'tam thể', 'PERSIAN': 'Ba Tư',
    'RAGDOLL': 'Ragdoll', 'JELLIE': 'Jellie', 'ALL_BLACK': 'mun',
    'Red Fox': 'cáo đỏ', 'Snow Fox': 'cáo tuyết', 'STANDING': 'đứng', 'ROLLED_UP': 'cuộn tròn',
}
VALUE.update({k: k.lower() for k in ('KOB', 'SUNSTREAK', 'SNOOPER', 'DASHER', 'BRINELY', 'SPOTTY', 'FLOPPER', 'STRIPEY',
                                     'GLITTER', 'BLOCKFISH', 'BETTY', 'CLAYFISH')})

missing = set()


def tr_text(t, species=None):
    """Dịch phần chữ giữa các mã màu."""
    s = t.strip()
    if not s:
        return t
    if s in FIXED:
        return t.replace(s, FIXED[s])
    for en, vi in LORE.items():
        if t.startswith(en):
            return vi + t[len(en):]
    m = re.fullmatch(r'([A-Za-z -]+): (.*)', t)
    if m and m.group(1) in FIELD:
        return f'{FIELD[m.group(1)]}: {tr_text(m.group(2)) if m.group(2) else ""}'
    if m and m.group(1) in FIELD:
        return t
    if s in VALUE:
        return t.replace(s, VALUE[s])
    if species and re.fullmatch(r'[A-Za-z ]+', s):
        return t.replace(s, species)
    if re.search(r'[A-Za-z]{3}', s) and not re.search(r'[À-ỹ]', s) and '{' not in s and s not in TEN.values():
        missing.add(s)
    return t


def tr(raw, species=None):
    parts = COLOR.split(raw)
    out, i = [], 0
    while i < len(parts):
        p = parts[i]
        if COLOR.fullmatch(p) or not p:
            out.append(p)
        else:
            # "Half Scale: " + "&a" + "true": ghép trường với giá trị sau mã màu
            m = re.fullmatch(r'([A-Za-z -]+): ', p)
            if m and m.group(1) in FIELD:
                out.append(f'{FIELD[m.group(1)]}: ')
            else:
                out.append(tr_text(p, species))
        i += 1
    return ''.join(out)


def walk(x, key='', species=None):
    if isinstance(x, dict):
        return {k: walk(v, k, species) for k, v in x.items()}
    if isinstance(x, list):
        return [walk(v, key, species) for v in x]
    if isinstance(x, str) and key in ('minecraft:custom_name', 'minecraft:lore', 'title'):
        return tr(x, species)
    return x


def _ten_loai():
    """Tên Việt các loài: lấy bảng TEN trong tools/mypet/sinh-cay-ky-nang.py (một nguồn chung), thêm vài loài chỉ SimplePets có."""
    src = os.path.join(os.path.dirname(__file__), '..', 'mypet', 'sinh-cay-ky-nang.py')
    with open(src, encoding='utf-8') as f:
        m = re.search(r'^TEN = (\{.*?\})\n', f.read(), re.S | re.M)
    ten = eval(m.group(1), {}) if m else {}
    ten.update({'ArmorStand': 'Giá giáp', 'CamelHusk': 'Lạc đà xác khô', 'Nautilus': 'Ốc anh vũ',
                'ZombieNautilus': 'Ốc anh vũ zombie', 'Parched': 'Bộ xương khô', 'SulfurCube': 'Khối lưu huỳnh',
                'HappyGhast': 'Ghast vui vẻ', 'Polarbear': 'Gấu Bắc Cực', 'Shulker': 'Shulker'})
    return ten


TEN = _ten_loai()


def species_of(d):
    dn = d.get('display_name', '') or ''
    m = re.search(r'&f(.+?) &7\(', dn)
    if m:
        return m.group(1)
    m = re.search(r"%player%'s (.+?) Pet", dn)                   # bản gốc tiếng Anh: đổi luôn display_name
    if m and m.group(1).replace(' ', '') in TEN:
        vi = TEN[m.group(1).replace(' ', '')]
        d['display_name'] = f'&d✿ &f{vi} &7(%player%)'
        return vi
    return None


def run(root):
    changed = 0
    for sub in ('Pets', 'Items', 'Inventories'):
        folder = os.path.join(root, sub)
        if not os.path.isdir(folder):
            continue
        for name in sorted(os.listdir(folder)):
            p = os.path.join(folder, name)
            if not name.endswith('.json') or not os.path.isfile(p):
                continue
            with open(p, encoding='utf-8') as f:          # đọc xong mới mở ghi
                text = f.read()
            d = json.loads(text)
            orig = json.loads(text)
            sp = species_of(d) if sub == 'Pets' else None
            if sub == 'Pets' and isinstance(d.get('item'), dict):
                # chỉ tên nút loài lấy tên Việt; các lựa chọn bên dưới dịch theo bảng
                item = d['item'].get('components', {})
                if 'minecraft:custom_name' in item and sp:
                    raw = item['minecraft:custom_name']
                    lead = ''.join(c for c in COLOR.findall(raw)[:1]) if raw.startswith('&') else ''
                    item['minecraft:custom_name'] = lead + sp
            nd = walk(d)
            if nd != orig:
                new = json.dumps(nd, ensure_ascii=False, indent=2)
                with open(p, 'w', encoding='utf-8') as f:
                    f.write(new)
                changed += 1
    return changed


if __name__ == '__main__':
    for r in sys.argv[1:]:
        print(r, 'sửa', run(r), 'file')
    if missing:
        print('CHƯA DỊCH:', sorted(missing))
