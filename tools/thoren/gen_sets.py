# Sinh các set Thợ Rèn (Tinh Anh, Thợ Mỏ, Ngư Phủ, Du Hiệp) vào Thư viện đồ theo ĐÚNG khung áo giáp Tân Thủ của chủ server.
# Chạy trong container panel (ruamel). Thang phù phép: memory set-do-trade (chừa đường cho set sau).
from ruamel.yaml import YAML
P = '/data/banghoi-1/plugins/DuckyMoMoCore/thuvien-do.yml'
y = YAML(); y.preserve_quotes = True; y.width = 4096
doc = y.load(open(P, encoding='utf-8'))
items = doc['items']
SEP = '&8&m━━━━━━━━━━━━━━━━'
SLOT = {'mu': 'HELMET', 'ao': 'CHESTPLATE', 'quan': 'LEGGINGS', 'giay': 'BOOTS', 'kiem': 'SWORD', 'cup': 'PICKAXE'}
NOUN = {'mu': 'Mũ', 'ao': 'Giáp', 'quan': 'Quần', 'giay': 'Giày', 'kiem': 'Kiếm', 'cup': 'Cúp', 'can': 'Cần câu', 'cung': 'Cung'}
LOAI = {'mu': 'Armor', 'ao': 'Armor', 'quan': 'Armor', 'giay': 'Armor', 'kiem': 'Weapon', 'cung': 'Weapon', 'cup': 'Tool', 'can': 'Tool'}


def material(piece, base):
    if piece == 'can':
        return 'FISHING_ROD'
    if piece == 'cung':
        return 'BOW'
    return base + '_' + SLOT[piece]


def roman(n):
    return ['', 'I', 'II', 'III', 'IV', 'V', 'VI'][n]


RAR_VI = {'&b✦ Uncommon': ('&b', 'Uncommon'), '&9✦ Rare': ('&9', 'Rare'), '&5✦ Epic': ('&5', 'Epic'), '&6✦ Epic': ('&6', 'Epic')}


def stat_lines(attr, bullet):
    out = []
    if attr.get('pct_mau'): out.append(f"{bullet}+{attr['pct_mau']}% sinh lực tối đa")
    if attr.get('pct_day_lui'): out.append(f"{bullet}+{attr['pct_day_lui']}% kháng đẩy lùi")
    if attr.get('pct_sat_thuong'): out.append(f"{bullet}+{attr['pct_sat_thuong']}% sát thương")
    return out


def bar(lv, maxn, on, off, c_on, c_off):
    return c_on + on * lv + c_off + off * (maxn - lv)


# Mỗi set một phong cách (chủ server 23/10: không bê nguyên khung Tân Thủ, phải mới mẻ, oách).
def style(set_id, piece, lv, maxn, rarity, attr, skills, flavor):
    noun = NOUN[piece]
    col, rname = RAR_VI.get(rarity, ('&5', 'Epic'))
    if set_id == 'ta':      # TINH ANH: hoàng gia tím – vàng, khung ┏┓, sao tinh luyện
        top = '&6' if lv == maxn else '&d'
        name = f'&5✧ {top}&l{noun} Tinh Anh &5✧ &e+{lv}'
        lore = ['&5┏━━━━━━━━━━━━━━━━━━┓', '&d     ❖ &5&lTINH ANH &d❖', '&5┗━━━━━━━━━━━━━━━━━━┛',
                f'&d✧ &7Trang bị: &f{noun} &8· &7Netherite',
                f'&d✧ &7Phẩm chất: {col}{rname}',
                '&d✧ &7Tinh luyện: ' + bar(lv, maxn, '★', '☆', '&e', '&8') + f' &8({lv}/{maxn})', '']
        st = stat_lines(attr, '&7  ▹ ')
        if st: lore += ['&d⚔ &5&lCHỈ SỐ'] + st
        lore += ['&d✦ &5&lTHIÊN PHÚ'] + (['&7  ▹ ' + x for x in skills] if skills else ['&8  ▹ Chưa thức tỉnh'])
        lore += ['', f'&5&o"{flavor}"']
    elif set_id == 'cb':    # CHIẾN BINH: đỏ đồng, khung ╔╗, thanh quân hàm ▮▯
        name = f'&4⚔ &c&l{noun} Chiến Binh &4⚔ &6+{lv}'
        lore = ['&4╔══════ &c&lCHIẾN BINH &4══════╗',
                f'&c⚔ &7Món: &f{noun}   &7Phẩm: {col}{rname}',
                '&c⚔ &7Quân hàm: ' + bar(lv, maxn, '▮', '▯', '&6', '&8') + f' &8+{lv}', '']
        st = stat_lines(attr, '&7  » ')
        if st: lore += ['&c&l▣ SỨC MẠNH'] + st
        lore += ['&6&l▣ CHIẾN KỸ'] + (['&7  » ' + x for x in skills] if skills else ['&8  » Chưa luyện thành'])
        lore += ['&4╚═══════════════════════╝', f'&c&o"{flavor}"']
    elif set_id == 'cc':    # CAO CẤP: vàng kim hoàng gia, ✦━━✦, thanh thăng hoa ✪
        name = f'&6♛ &e&l{noun} Cao Cấp &6♛ &f+{lv}'
        lore = ['&6✦━━━━━━ &e&lCAO CẤP &6━━━━━━✦',
                f'&e♛ &7Trang bị: &f{noun}',
                f'&e♛ &7Phẩm chất: {col}{rname}',
                '&e♛ &7Thăng hoa: ' + bar(lv, maxn, '✪', '✪', '&6', '&8') + f' &8({lv}/{maxn})', '']
        st = stat_lines(attr, '&7  ✧ ')
        if st: lore += ['&6&l◆ THẦN LỰC'] + st
        lore += ['&e&l◆ TUYỆT KỸ'] + (['&7  ✧ ' + x for x in skills] if skills else ['&8  ✧ Chưa khai mở'])
        lore += ['&6✦━━━━━━━━━━━━━━━━━━━━━✦', f'&e&o"{flavor}"']
    elif set_id == 'tm':    # THỢ MỎ: vàng đồng, dải ▬, cúp ⛏, thanh ■□
        name = f'&6⛏ &e&l{noun} Thợ Mỏ &6[+{lv}]'
        lore = ['&8▬▬▬▬ &6&l⛏ THỢ MỎ ⛏ &8▬▬▬▬',
                f'&7Món: &f{noun}   &7Hạng: {col}{rname}',
                '&7Tay nghề: ' + bar(lv, maxn, '■', '□', '&6', '&8') + f' &8+{lv}', '']
        st = stat_lines(attr, '&7  ▪ ')
        if st: lore += ['&6◈ &e&lThông số'] + st
        lore += ['&6◈ &e&lMẹo nghề'] + (['&7  ▪ ' + x for x in skills] if skills else ['&8  ▪ Chưa có'])
        lore += ['&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬', f'&6&o"{flavor}"']
    elif set_id == 'np':    # NGƯ PHỦ: sóng xanh biển, ∿ ≈, thanh ●○
        name = f'&3≈ &b&l{noun} Ngư Phủ &3≈ &f+{lv}'
        lore = ['&3∿∿∿∿∿ &b&lNGƯ PHỦ &3∿∿∿∿∿',
                f'&7Món: &f{noun}   &7Phẩm: {col}{rname}',
                '&7Sóng: ' + bar(lv, maxn, '●', '○', '&b', '&8') + f' &8+{lv}', '']
        st = stat_lines(attr, '&7  ≈ ')
        if st: lore += ['&b⚓ &3&lThuộc tính'] + st
        lore += ['&b⚓ &3&lÂn huệ của sông'] + (['&7  ≈ ' + x for x in skills] if skills else ['&8  ≈ Chưa có'])
        lore += ['&3∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿', f'&b&o"{flavor}"']
    else:                   # DU HIỆP: lục lâm, mũi tên ➹, thanh ▶▷
        name = f'&2➹ &a&l{noun} Du Hiệp &2➹ &f+{lv}'
        lore = ['&2━━━━ &a&l➹ DU HIỆP ➹ &2━━━━',
                f'&7Món: &f{noun}   &7Phẩm: {col}{rname}',
                '&7Hành trình: ' + bar(lv, maxn, '▶', '▷', '&a', '&8') + f' &8+{lv}', '']
        st = stat_lines(attr, '&7  ❯ ')
        if st: lore += ['&a❀ &2&lThân pháp'] + st
        lore += ['&a❀ &2&lBí kíp'] + (['&7  ❯ ' + x for x in skills] if skills else ['&8  ❯ Chưa có'])
        lore += ['&2━━━━━━━━━━━━━━━━━━', f'&a&o"{flavor}"']
    return name, lore


def build(set_id, tag, title, lv, piece, base, frame, rarity, maxn, ench, attr=None, ce=None, dmm=None, skills=(), flavor=''):
    attr = attr or {}
    skills = [x.replace('Giáp: ', '').replace('Mũ: ', '').replace('Quần: ', '').replace('Giày: ', '').replace('Cần câu: ', '')
              .replace('Cầm trên tay: ', 'Cầm tay: ') for x in skills]
    skills = [x[0].upper() + x[1:] if x and x[0].islower() else x for x in skills]
    name, lore = style(set_id, piece, lv, maxn, rarity, attr, skills, flavor)
    e = {'nguon': 'web', 'nhom': 'giap' if LOAI[piece] == 'Armor' else ('vu-khi' if LOAI[piece] == 'Weapon' else 'cong-cu'),
         'ten-goi': f'{NOUN[piece]} {title} +{lv}', 'material': material(piece, base), 'amount': 1,
         'name': name, 'lore': lore, 'enchants': {k: v for k, v in ench.items() if v}}
    if attr: e['attributes'] = attr
    if ce: e['ce-enchants'] = ce
    if dmm: e['dmm-enchants'] = dmm
    if lv >= 2 or len(e['enchants']) > 3:
        e['compact-enchants'] = True
        e['hide-stats'] = True
    e['bo'] = [tag, 'Đồ set']
    e['nguoi-luu'] = 'Claude (Thợ Rèn)'
    items[f'{set_id}_{piece}_{lv}'] = e


# ------------------------------------------------------------------ TINH ANH (+0..+5), nâng từ Chiến Binh +7
TA_FRAME = ['&5&l———= &d&l{N} tinh anh &l&5=———'] * 3 + ['&d&l———= &f&l{N} ti&d&lnh anh &l&d=———'] * 2 + ['&6&l———== &d&l{N} ti&e&lnh anh &l&6==———']
TA_RAR = ['&5✦ Epic'] * 6
TA_FLAVOR = {
    'mu': ['Ánh tím phủ đỉnh đầu…', 'Mắt nhìn xuyên màn đêm…', 'Vương miện của kẻ tinh nhuệ…', 'Tinh tú hội tụ trên trán…', 'Không gì che nổi tầm mắt…', 'Đỉnh cao của tinh anh…'],
    'ao': ['Netherite rèn trong lửa ngục…', 'Ngực thép, tim kiên định…', 'Đòn đánh trượt như nước…', 'Linh khí bao quanh thân…', 'Thành trì biết đi…', 'Bất khả xâm phạm…'],
    'quan': ['Bước chân của tinh anh…', 'Đầu gối không biết khuỵu…', 'Lướt êm như bóng đêm…', 'Vững như núi Tản…', 'Không ai đuổi kịp…', 'Đứng giữa nghìn quân…'],
    'giay': ['Đế netherite nặng trịch…', 'Mỗi bước rung đất…', 'Gió nâng gót chân…', 'Nhanh như sấm…', 'Đạp mây mà đi…', 'Dấu chân thành huyền thoại…'],
    'kiem': ['Lưỡi đen hút ánh sáng…', 'Mỗi nhát chém một giọt máu…', 'Kiếm khát chiến trận…', 'Máu sôi trong huyết quản…', 'Kiếm ý ngút trời…', 'Thiên hạ đệ nhất kiếm…'],
}
for lv in range(6):
    up = lv >= 3
    base = 'NETHERITE'
    common = dict(set_id='ta', tag='Tinh Anh', title='Tinh Anh', lv=lv, base=base, frame=TA_FRAME[lv], rarity=TA_RAR[lv], maxn=5)
    # Mạnh hơn HẲN Tân Thủ (chủ server 23/10: phải có mục tiêu trade up): Bảo vệ V→VII, Sắc bén VI→VIII, máu % gấp ~3 lần
    # Chủ server 23/10: không cần mạnh hơn hẳn; +0 có thể yếu hơn Tân Thủ +9, lên dần, +5 vượt vừa phải.
    # Bậc 3 (23/10, thang 7 bậc): nâng từ Chiến Binh +7; +0 ≈ hơi dưới Chiến Binh +7, +5 vượt vừa phải.
    prot = [5, 5, 5, 5, 6, 6][lv]
    unb = [4, 4, 4, 5, 5, 5][lv]
    sp = [3, 3, 4, 4, 4, 4][lv]   # chống tên / nổ / lửa
    build(piece='mu', ench={'protection': prot, 'unbreaking': unb, 'projectile_protection': sp, 'respiration': 3, 'aqua_affinity': 1},
          attr={'pct_mau': [8, 8, 9, 10, 11, 12][lv]}, ce={**({'Glowing': 1} if lv >= 2 else {}), **({'Implants': 1} if lv >= 4 else {})} or None,
          skills=(['Mũ: nhìn rõ trong đêm'] if lv >= 2 else []) + (['Đi đường dài không đói'] if lv >= 4 else []), flavor=TA_FLAVOR['mu'][lv], **common)
    build(piece='ao', ench={'protection': prot, 'unbreaking': unb, 'projectile_protection': sp, 'blast_protection': sp, 'fire_protection': sp},
          attr={'pct_mau': [20, 21, 22, 24, 26, 28][lv]}, ce={'Crouch': 2, 'Maneuver': 1 if lv < 3 else 2, **({'Enlightened': 1} if lv >= 5 else {})},
          skills=['Giáp: Khi ngồi &a(shift)&7 lúc chiến đấu, nhận ít sát thương hơn &e15%',
                  'Có tỉ lệ &e' + ('10' if lv < 3 else '15') + '% &7né hoàn toàn một đòn đánh.']
                 + (['Có cơ hội &ahồi máu&7 khi đang bị đánh'] if lv >= 5 else []), flavor=TA_FLAVOR['ao'][lv], **common)
    build(piece='quan', ench={'protection': prot, 'unbreaking': unb, 'blast_protection': sp, 'swift_sneak': 2 if lv < 3 else 3},
          attr={'pct_mau': [8, 8, 9, 10, 11, 12][lv]}, ce={'Nursery': 1 if lv < 5 else 2} if lv >= 3 else None,
          skills=['Quần: ngồi (shift) vẫn đi nhanh'] + (['Có cơ hội &ahồi máu&7 khi đi bộ (Dưỡng thương ' + ('I' if lv < 5 else 'II') + ')'] if lv >= 3 else []),
          flavor=TA_FLAVOR['quan'][lv], **common)
    build(piece='giay', ench={'protection': prot, 'unbreaking': unb, 'feather_falling': 4, 'depth_strider': 2 if lv < 3 else 3},
          attr={'pct_day_lui': [12, 12, 14, 15, 16, 18][lv]}, ce={**({'Gears': 1} if up else {}), **({'Springs': 1} if lv >= 5 else {})} or None,
          skills=(['Giày: chạy nhanh hơn &e20%'] if up else []) + (['Nhảy cao hơn'] if lv >= 5 else []), flavor=TA_FLAVOR['giay'][lv], **common)
    build(piece='kiem', ench={'sharpness': [6, 6, 6, 6, 7, 7][lv], 'unbreaking': unb, 'looting': 2 if lv < 3 else 3, 'sweeping_edge': 2 if lv < 3 else 3,
                              'fire_aspect': 1 if up else 0},
          attr={'pct_sat_thuong': [14, 15, 16, 18, 20, 22][lv]}, ce={'Vampire': 1 if lv < 3 else 2, **({'Rage': 1} if lv >= 4 else {})},
          dmm={'cuong_no': 1 if lv < 5 else 2} if up else None,
          skills=['Có cơ hội &ehút máu&7 khi đánh (Ma cà rồng ' + ('I' if lv < 3 else 'II') + ')']
                 + (['Chuột phải: &eHuyết chiến ' + ('I' if lv < 5 else 'II') + '&7, mất 3 tim đổi lấy sát thương (hồi 30 giây)'] if up else [])
                 + (['Đánh liên tiếp: mỗi đòn mạnh thêm &e10%'] if lv >= 4 else []),
          flavor=TA_FLAVOR['kiem'][lv], **common)

# ------------------------------------------------------------------ CHIẾN BINH (bậc 2, +0..+7, Rare), nâng từ Tân Thủ +9
CB_FLAVOR = {
    'mu': ['Mũ đồng của lính mới…', 'Vết chém đầu tiên trên vành…', 'Đội lên là quên sợ…', 'Mắt chỉ thấy quân thù…', 'Tiếng trống trận vang trong đầu…', 'Đã qua trăm trận…', 'Thủ lĩnh tiền quân…', 'Đầu không cúi trước ai…'],
    'ao': ['Giáp trận còn mùi khói…', 'Lưng chưa từng quay về địch…', 'Đỡ đòn thay đồng đội…', 'Máu khô thành hoa văn…', 'Tường thành di động…', 'Vạn tiễn không xuyên…', 'Kiên như thành đồng…', 'Khiên của cả đội quân…'],
    'quan': ['Bước hành quân đầu tiên…', 'Lội bùn qua chiến hào…', 'Không lùi nửa bước…', 'Gối thép quỳ không xong…', 'Xông pha đầu trận…', 'Chạy qua mưa tên…', 'Bước chân của tướng quân…', 'Đạp đổ cổng thành…'],
    'giay': ['Giày lính bạc màu…', 'Hành quân trăm dặm…', 'Rơi từ tường thành vẫn đứng…', 'Chân vững như cọc…', 'Gió lốc dưới gót…', 'Đến trước cả tin báo…', 'Không ai đẩy nổi…', 'Dấu chân thành đường hành quân…'],
    'kiem': ['Kiếm sắt của lính trơn…', 'Đã nếm máu quái…', 'Lưỡi kiếm thuộc lòng trận mạc…', 'Chém đâu đứt đó…', 'Kiếm lạnh như sương…', 'Một nhát hai mạng…', 'Kiếm hiệu của tiền quân…', 'Thanh kiếm của tướng…'],
}
for lv in range(8):
    common = dict(set_id='cb', tag='Chiến Binh', title='Chiến Binh', lv=lv, base='DIAMOND', frame='', rarity='&9✦ Rare', maxn=7)
    prot = [4, 4, 4, 4, 4, 4, 5, 5][lv]
    unb = [4] * 8
    sp = [3, 3, 3, 3, 3, 4, 4, 4][lv]
    build(piece='mu', ench={'protection': prot, 'unbreaking': unb[lv], 'projectile_protection': sp, 'respiration': 3, 'aqua_affinity': 1},
          attr={'pct_mau': [5, 5, 6, 6, 7, 7, 8, 8][lv]}, ce={'Glowing': 1} if lv >= 2 else None,
          skills=['Mũ: nhìn rõ trong đêm'] if lv >= 2 else [], flavor=CB_FLAVOR['mu'][lv], **common)
    build(piece='ao', ench={'protection': prot, 'unbreaking': unb[lv], 'projectile_protection': sp, 'blast_protection': sp, 'fire_protection': sp},
          attr={'pct_mau': [12, 13, 14, 15, 16, 17, 18, 20][lv]}, ce={'Crouch': 2, 'Maneuver': 1, **({'Savior': 1} if lv >= 5 else {})},
          skills=['Giáp: Khi ngồi &a(shift)&7 lúc chiến đấu, nhận ít sát thương hơn &e15%', 'Có tỉ lệ &e10% &7né hoàn toàn một đòn đánh.']
                 + (['Có cơ hội &egiảm nửa&7 sát thương nhận vào'] if lv >= 5 else []), flavor=CB_FLAVOR['ao'][lv], **common)
    build(piece='quan', ench={'protection': prot, 'unbreaking': unb[lv], 'blast_protection': sp, 'swift_sneak': 2},
          attr={'pct_mau': [5, 5, 6, 6, 7, 7, 8, 8][lv]}, ce={'Nursery': 1} if lv >= 4 else None,
          skills=['Quần: ngồi (shift) vẫn đi nhanh'] + (['Có cơ hội &ahồi máu&7 khi đi bộ'] if lv >= 4 else []), flavor=CB_FLAVOR['quan'][lv], **common)
    build(piece='giay', ench={'protection': prot, 'unbreaking': unb[lv], 'feather_falling': 4, 'depth_strider': 2},
          attr={'pct_day_lui': [8, 8, 10, 10, 12, 12, 14, 15][lv]}, ce={'Gears': 1} if lv >= 4 else None,
          skills=['Giày: chạy nhanh hơn &e20%'] if lv >= 4 else [], flavor=CB_FLAVOR['giay'][lv], **common)
    build(piece='kiem', ench={'sharpness': [5, 5, 5, 5, 6, 6, 6, 6][lv], 'unbreaking': unb[lv], 'looting': 2, 'sweeping_edge': 2},
          attr={'pct_sat_thuong': [8, 9, 10, 11, 12, 13, 14, 16][lv]}, ce={'SlowMo': 1 if lv < 6 else 2} if lv >= 3 else None, dmm={'luot': 2},
          skills=['Chuột phải: &eLướt II&7 về phía trước (hồi 4 giây)'] + (['Có cơ hội &blàm chậm&7 kẻ địch'] if lv >= 3 else []),
          flavor=CB_FLAVOR['kiem'][lv], **common)

# ------------------------------------------------------------------ CAO CẤP (bậc 4, +0..+5, Epic vàng), nâng từ Tinh Anh +5
CC_FLAVOR = {
    'mu': ['Vương miện chưa từng cúi…', 'Ánh kim soi nghìn dặm…', 'Trí tuệ của bậc đế vương…', 'Thần quang hộ đỉnh…', 'Nhật nguyệt trong mắt…', 'Đỉnh cao của phàm nhân…'],
    'ao': ['Kim giáp của hoàng tộc…', 'Thánh quang phủ thân…', 'Đao kiếm gãy khi chạm…', 'Long lân hộ thể…', 'Bất tử giữa sa trường…', 'Thân này là thành trì…'],
    'quan': ['Bước chân của quân vương…', 'Đất rung theo mỗi bước…', 'Đi giữa vạn quân không ai cản…', 'Kim cốt bất hoại…', 'Đạp lên mây vàng…', 'Thiên hạ dưới chân…'],
    'giay': ['Hài vàng của đế vương…', 'Gió theo sau gót…', 'Nhảy qua tường thành…', 'Nhanh hơn tin đồn…', 'Bóng vàng thoắt ẩn…', 'Bước qua cả bầu trời…'],
    'kiem': ['Kim kiếm khai thiên…', 'Mỗi nhát một tia sáng…', 'Kiếm khí xé gió…', 'Máu kẻ thù nhuộm vàng lưỡi…', 'Vạn kiếm quy tông…', 'Kiếm của bậc chí tôn…'],
}
for lv in range(6):
    common = dict(set_id='cc', tag='Cao Cấp', title='Cao Cấp', lv=lv, base='NETHERITE', frame='', rarity='&6✦ Epic', maxn=5)
    prot = [5, 5, 6, 6, 6, 7][lv]
    unb = 5
    sp = [4, 4, 4, 4, 5, 5][lv]
    build(piece='mu', ench={'protection': prot, 'unbreaking': unb, 'projectile_protection': sp, 'respiration': 3, 'aqua_affinity': 1},
          attr={'pct_mau': [11, 11, 12, 13, 14, 15][lv]}, ce={'Glowing': 1, 'Implants': 1},
          skills=['Mũ: nhìn rõ trong đêm', 'Đi đường dài không đói'], flavor=CC_FLAVOR['mu'][lv], **common)
    build(piece='ao', ench={'protection': prot, 'unbreaking': unb, 'projectile_protection': sp, 'blast_protection': sp, 'fire_protection': sp},
          attr={'pct_mau': [26, 27, 28, 30, 32, 35][lv]}, ce={'Crouch': 3, 'Maneuver': 2, 'Enlightened': 1 if lv < 3 else 2, **({'Savior': 1} if lv >= 3 else {})},
          skills=['Giáp: Khi ngồi &a(shift)&7 lúc chiến đấu, nhận ít sát thương hơn &e20%', 'Có tỉ lệ &e15% &7né hoàn toàn một đòn đánh.',
                  'Có cơ hội &ahồi máu&7 khi đang bị đánh'] + (['Có cơ hội &egiảm nửa&7 sát thương nhận vào'] if lv >= 3 else []),
          flavor=CC_FLAVOR['ao'][lv], **common)
    build(piece='quan', ench={'protection': prot, 'unbreaking': unb, 'blast_protection': sp, 'swift_sneak': 3},
          attr={'pct_mau': [11, 11, 12, 13, 14, 15][lv]}, ce={'Nursery': 2},
          skills=['Quần: ngồi (shift) vẫn đi nhanh', 'Có cơ hội &ahồi máu&7 khi đi bộ (Dưỡng thương II)'], flavor=CC_FLAVOR['quan'][lv], **common)
    build(piece='giay', ench={'protection': prot, 'unbreaking': unb, 'feather_falling': 5, 'depth_strider': 3},
          attr={'pct_day_lui': [15, 16, 18, 20, 22, 25][lv]}, ce={'Gears': 1 if lv < 3 else 2, 'Springs': 1},
          skills=['Giày: chạy nhanh hơn &e' + ('20' if lv < 3 else '40') + '%', 'Nhảy cao hơn'], flavor=CC_FLAVOR['giay'][lv], **common)
    build(piece='kiem', ench={'sharpness': [7, 7, 7, 8, 8, 8][lv], 'unbreaking': unb, 'looting': 3, 'sweeping_edge': 3, 'fire_aspect': 2},
          attr={'pct_sat_thuong': [20, 21, 22, 24, 27, 30][lv]}, ce={'Vampire': 2, 'Rage': 2, **({'DoubleDamage': 1} if lv >= 3 else {})},
          dmm={'cuong_no': 2},
          skills=['Có cơ hội &ehút máu&7 khi đánh (Ma cà rồng II)', 'Chuột phải: &eHuyết chiến II&7, mất 3 tim đổi lấy sát thương (hồi 30 giây)',
                  'Đánh liên tiếp: mỗi đòn mạnh thêm &e20%'] + (['Có cơ hội gây &egấp đôi sát thương'] if lv >= 3 else []),
          flavor=CC_FLAVOR['kiem'][lv], **common)

# ------------------------------------------------------------------ MẢNH TRAO ĐỔI rơi trong dungeon (3 set × 3 nhóm món)
SHERD = iter(['ARCHER', 'BREWER', 'EXPLORER', 'FLOW', 'GUSTER', 'HEARTBREAK', 'MOURNER', 'SCRAPE', 'SNORT'])
for sid, (c1, c2, sym, word, tag, where) in {
        'cb': ('&4', '&c', '⚔', 'Chiến Ấn', 'Chiến Binh', 'Đỉnh Vách Đá, Hang Thánh Địa, Mỏ Hoang…'),
        'ta': ('&5', '&d', '❖', 'Linh Ấn', 'Tinh Anh', 'Cây Cầu Cổ, Thành Phố Đổ Nát, Cung Điện…'),
        'cc': ('&6', '&e', '♛', 'Kim Ấn', 'Cao Cấp', 'Mỏ Đá, Mỏ Sâu, Hoang Mạc và Chuông Địa Ngục…')}.items():
    for grp, (label, pieces) in {'khoi': ('Khôi Giáp', 'Mũ, Giày'), 'than': ('Thân Giáp', 'Giáp, Quần'), 'binh': ('Binh Khí', 'Kiếm')}.items():
        items[f'{sid}_an_{grp}'] = {
            'nguon': 'web', 'nhom': 'nguyen-lieu', 'ten-goi': f'{word} · {label}', 'material': next(SHERD) + '_POTTERY_SHERD', 'amount': 1,
            'name': f'{c1}{sym} {c2}{word} &8· &f{label}',
            'lore': [f'&7Mảnh ấn rơi trong dungeon: {where}', '', f'{c2}▸ &7Dùng rèn &f{pieces} {tag}&7 ở Thợ Rèn', '&8Trao đổi được · lấp lánh = hàng thật'],
            'glint': True, 'bo': ['Nguyên liệu Thợ Rèn', tag], 'nguoi-luu': 'Claude (Thợ Rèn)'}

# ------------------------------------------------------------------ SET NGHỀ (+0..+4): đi ngang, giáp ≤ Bảo vệ III, kỹ năng tiện ích
NG_RAR = ['&b✦ Uncommon', '&b✦ Uncommon', '&9✦ Rare', '&9✦ Rare', '&5✦ Epic']
NG_BASE = ['IRON', 'IRON', 'IRON', 'DIAMOND', 'DIAMOND']
PROT = [2, 2, 3, 3, 4]      # +4 ≈ Tân Thủ +9 về giáp, cộng tiện ích
UNB = [2, 3, 3, 4, 4]


def frames(c1, c2, c3, word):
    a, b = word[:2], word[2:]
    return ([f'{c1}&l———= {c2}&l{{N}} {word} &l{c1}=———'] * 2 + [f'{c1}&l———= {c2}&l{{N}} {a}{c3}&l{b} &l{c1}=———'] * 2
            + [f'{c3}&l———== {c2}&l{{N}} {a}{c1}&l{b} &l{c3}==———'])


NGHE = {
    'tm': ('Thợ Mỏ', 'thợ mỏ', frames('&6', '&e', '&f', 'thợ mỏ'), 'cup', {
        'mu': ['Đèn mỏ chưa bao giờ tắt…', 'Bụi đá bám đầy vành…', 'Mắt quen bóng tối hầm sâu…', 'Thấy cả mạch quặng sau vách…', 'Vua của lòng đất…'],
        'ao': ['Áo bạt thấm mồ hôi…', 'Đá rơi cũng chẳng sợ…', 'Dung nham chỉ là nước ấm…', 'Lặn sâu tới đáy Nether…', 'Lửa ngục cũng phải nhường…'],
        'quan': ['Gối mòn vì quỳ đục đá…', 'Chui hầm như chuột chũi…', 'Bò nhanh qua khe hẹp…', 'Đường hầm là nhà…', 'Lòng đất không có ngõ cụt…'],
        'giay': ['Đế dày giẫm sỏi…', 'Nhảy xuống giếng mỏ vẫn đứng…', 'Rơi tầng hầm nhẹ tênh…', 'Không sợ vực sâu…', 'Bước xuống lõi trái đất…'],
        'cup': ['Cán gỗ, mũi sắt, tay chai…', 'Đá vỡ như đậu phụ…', 'Quặng tự chạy vào túi…', 'Một nhát một mạch…', 'Núi cũng phải cúi đầu…'],
    }),
    'np': ('Ngư Phủ', 'ngư phủ', frames('&3', '&b', '&f', 'ngư phủ'), 'can', {
        'mu': ['Nón lá che nắng sông…', 'Thở cùng nhịp sóng…', 'Lặn sâu chẳng cần ngoi…', 'Mắt thấy đáy biển…', 'Con của thuỷ thần…'],
        'ao': ['Áo tơi đẫm nước…', 'Gió sông không lạnh…', 'Sóng đánh không chao…', 'Bão biển cũng hiền…', 'Đại dương trong lồng ngực…'],
        'quan': ['Ống quần xắn tới gối…', 'Lội suối bắt cá…', 'Đứng vững giữa dòng…', 'Nước xiết chẳng cuốn nổi…', 'Đi giữa lòng biển…'],
        'giay': ['Dép rơm ướt sũng…', 'Lội nước nhanh thoăn thoắt…', 'Bơi như rái cá…', 'Đuổi kịp cả cá heo…', 'Bước trên sóng bạc…'],
        'can': ['Cần tre, dây cước…', 'Cá cắn câu nhanh hơn…', 'Lưỡi câu biết chọn cá…', 'Cá hiếm tự tìm tới…', 'Câu được cả trăng dưới nước…'],
    }),
    'dh': ('Du Hiệp', 'du hiệp', frames('&2', '&a', '&f', 'du hiệp'), 'cung', {
        'mu': ['Mũ lông chim phiêu bạt…', 'Tai nghe tiếng lá động…', 'Mắt diều hâu…', 'Thấy kẻ địch từ xa…', 'Kẻ lữ hành bất bại…'],
        'ao': ['Áo da gió bụi…', 'Tên bay qua vạt áo…', 'Mưa tên cũng tránh được…', 'Gió rừng che chở…', 'Hành hiệp khắp thiên hạ…'],
        'quan': ['Quần vải đi rừng…', 'Bước nhẹ không tiếng…', 'Băng rừng vượt suối…', 'Không vết chân nào để lại…', 'Bóng ma của rừng già…'],
        'giay': ['Giày cỏ đi muôn dặm…', 'Bật cao qua bờ rào…', 'Nhảy từ cây này sang cây khác…', 'Chân gió…', 'Thoắt ẩn thoắt hiện…'],
        'cung': ['Dây cung căng gió…', 'Bách phát bách trúng…', 'Mũi tên mang băng giá…', 'Tên xuyên mây…', 'Một mũi tên, một chớp mắt…'],
    }),
}
for sid, (title, low, fr, tool, flav) in NGHE.items():
    for lv in range(5):
        common = dict(set_id=sid, tag=title, title=title, lv=lv, base=NG_BASE[lv], frame=fr[lv], rarity=NG_RAR[lv], maxn=4)
        armor = {'protection': PROT[lv], 'unbreaking': UNB[lv]}
        if sid == 'tm':
            build(piece='mu', ench={**armor, 'projectile_protection': [0, 1, 1, 2, 2][lv]}, ce={'Glowing': 1} if lv >= 2 else None,
                  skills=['Mũ: nhìn rõ trong hầm tối'] if lv >= 2 else [], flavor=flav['mu'][lv], **common)
            build(piece='ao', ench={**armor, 'blast_protection': [1, 1, 2, 2, 3][lv], 'fire_protection': [1, 1, 2, 2, 3][lv]},
                  ce={'BurnShield': 1} if lv >= 3 else None, skills=['Giáp: miễn sát thương lửa, dung nham'] if lv >= 3 else [],
                  attr={'pct_mau': 5} if lv >= 4 else None, flavor=flav['ao'][lv], **common)
            build(piece='quan', ench={**armor, 'blast_protection': [1, 1, 2, 2, 2][lv], 'swift_sneak': 1 if lv >= 2 else 0},
                  skills=['Quần: ngồi (shift) vẫn đi nhanh'] if lv >= 2 else [], flavor=flav['quan'][lv], **common)
            build(piece='giay', ench={**armor, 'feather_falling': [2, 2, 3, 3, 4][lv]}, skills=['Giày: giảm mạnh sát thương khi rơi'],
                  flavor=flav['giay'][lv], **common)
            build(piece='cup', ench={'efficiency': [2, 3, 3, 4, 5][lv], 'fortune': [0, 1, 1, 2, 3][lv], 'unbreaking': UNB[lv]},   # chủ server: giảm cúp
                  ce={**({'Haste': 1 if lv < 3 else 2} if lv >= 1 else {}), **({'AutoSmelt': 1} if lv >= 3 else {}), **({'Telepathy': 1} if lv >= 4 else {})} or None,
                  skills=(['Cầm trên tay: đào nhanh hơn &e' + ('20' if lv < 3 else '40') + '%'] if lv >= 1 else [])
                         + (['Có cơ hội &etự nung&7 quặng thành thỏi'] if lv >= 3 else [])
                         + (['Khối đào được bay thẳng vào túi'] if lv >= 4 else []), flavor=flav['cup'][lv], **common)
        elif sid == 'np':
            build(piece='mu', ench={**armor, 'respiration': [1, 2, 2, 3, 3][lv], 'aqua_affinity': 1 if lv >= 1 else 0},
                  ce={'Mermaid': 1} if lv >= 3 else None, skills=['Mũ: thở dưới nước'] + (['Đào dưới nước nhanh'] if lv >= 1 else []),
                  flavor=flav['mu'][lv], **common)
            build(piece='ao', ench={**armor, 'projectile_protection': [1, 1, 2, 2, 3][lv]}, attr={'pct_mau': 5} if lv >= 4 else None,
                  flavor=flav['ao'][lv], **common)
            build(piece='quan', ench={**armor, 'blast_protection': [1, 1, 1, 2, 2][lv]}, flavor=flav['quan'][lv], **common)
            build(piece='giay', ench={**armor, 'feather_falling': [1, 1, 2, 2, 3][lv], 'depth_strider': [1, 1, 2, 2, 3][lv]},
                  skills=['Giày: đi nhanh dưới nước'], flavor=flav['giay'][lv], **common)
            build(piece='can', ench={'lure': [1, 2, 3, 3, 4][lv], 'luck_of_the_sea': [1, 2, 3, 3, 4][lv], 'unbreaking': UNB[lv]},
                  skills=['Cần câu: cá cắn nhanh hơn, dễ ra đồ quý', 'Câu được &bVảy Cá Hiếm'], flavor=flav['can'][lv], **common)
        else:
            build(piece='mu', ench={**armor, 'projectile_protection': [1, 1, 2, 2, 3][lv]}, flavor=flav['mu'][lv], **common)
            build(piece='ao', ench={**armor, 'projectile_protection': [1, 2, 2, 3, 3][lv]}, attr={'pct_mau': 5} if lv >= 4 else None,
                  flavor=flav['ao'][lv], **common)
            build(piece='quan', ench={**armor, 'swift_sneak': 1 if lv >= 3 else 0}, skills=['Quần: ngồi (shift) vẫn đi nhanh'] if lv >= 3 else [],
                  flavor=flav['quan'][lv], **common)
            build(piece='giay', ench={**armor, 'feather_falling': [1, 2, 2, 3, 3][lv]},
                  ce={**({'Springs': 1} if lv >= 2 else {}), **({'Gears': 1} if lv >= 4 else {})} or None,
                  skills=(['Giày: nhảy cao hơn'] if lv >= 2 else []) + (['Chạy nhanh hơn &e20%'] if lv >= 4 else []), flavor=flav['giay'][lv], **common)
            build(piece='cung', ench={'power': [2, 3, 4, 5, 6][lv], 'punch': 1 if lv < 3 else 2, 'unbreaking': UNB[lv], 'infinity': 1 if lv >= 4 else 0},
                  # cung KHÔNG gắn phù phép riêng chuột phải (chuột phải = kéo cung)
                  ce={**({'IceFreeze': 1 if lv < 4 else 2} if lv >= 2 else {}), **({'Piercing': 1} if lv >= 3 else {})} or None,
                  skills=(['Có cơ hội &blàm chậm&7 kẻ trúng tên'] if lv >= 2 else []) + (['Có cơ hội &egấp đôi sát thương&7 (Xuyên giáp I)'] if lv >= 3 else [])
                         + (['Không tốn tên'] if lv >= 4 else []), flavor=flav['cung'][lv], **common)

items['vaycahiem'] = {'nguon': 'web', 'nhom': 'nguyen-lieu', 'ten-goi': 'Vảy Cá Hiếm', 'material': 'ANGLER_POTTERY_SHERD', 'amount': 1,
                      'name': '&bVảy Cá Hiếm', 'lore': ['&7Thỉnh thoảng vớt được khi câu cá.', '&8Nguyên liệu Thợ Rèn (set Ngư Phủ): /thoren'],
                      'cong-dung': 'tinh-luyen'}
y.dump(doc, open(P, 'w', encoding='utf-8'))
n = sum(1 for k in items if k.split('_')[0] in ('ta', 'tm', 'np', 'dh', 'cb', 'cc'))
print('đã ghi', n, 'món set mới + vaycahiem')
for k in ['cb_ao_7', 'cc_kiem_5', 'cb_an_khoi']:
    print(k, items[k]['name'], '|', ' / '.join(items[k]['lore']), '|', dict(items[k]['enchants']), items[k].get('ce-enchants'), items[k].get('dmm-enchants'))
