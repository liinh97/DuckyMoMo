# Mũ / Quần / Giày / Kiếm Tân Thủ theo ĐÚNG format áo giáp của chủ server (khung tên, ✦ EQUIPMENT ✦, ◆ dòng, ▸ THUỘC TÍNH, ▸ KỸ NĂNG).
from ruamel.yaml import YAML
import copy
P = '/data/banghoi-1/plugins/DuckyMoMoCore/thuvien-do.yml'
y = YAML(); y.preserve_quotes = True; y.width = 4096
doc = y.load(open(P, encoding='utf-8'))
items = doc['items']
AO = ['giap', 'giap_tan_thu_1', 'giap_tan_thu_2', 'giap_tan_thu_3', 'giap_tan_thu', 'giap_tan_thu_5', 'giap_tan_thu_6',
      'giap_tan_thu_7', 'giap_tan_thu_8', 'giap_tan_thu_9']
ARMOR_MAT = ['LEATHER', 'CHAINMAIL', 'COPPER', 'IRON', 'IRON', 'IRON', 'DIAMOND', 'DIAMOND', 'DIAMOND', 'DIAMOND']
SWORD_MAT = ['STONE', 'STONE', 'COPPER', 'IRON', 'IRON', 'IRON', 'DIAMOND', 'DIAMOND', 'DIAMOND', 'DIAMOND']
RAR = ['&a✦ Common'] * 4 + ['&b✦ Uncommon'] * 3 + ['&9✦ Rare'] * 3
UNB = [3, 3, 3, 3, 4, 4, 4, 4, 4, 4]
PROT = [2, 2, 3, 3, 3, 3, 3, 3, 3, 4]
# câu cuối theo cấp, mỗi món một giọng (cấp 8, 9 giữ thanh Loading như áo)
FLAVOR = {
    'mu':   ['Chiếc mũ cũ của lính gác làng…', 'Vành mũ đã bớt lung lay…', 'Ánh đồng lấp lánh trên trán…', 'Sắt nguội, đầu tỉnh…',
             'Nghe được tiếng bước chân từ xa…', 'Mắt sáng như sao đêm…', 'Kim cương soi đường…', 'Hơi thở dài dưới đáy hồ…'],
    'quan': ['Đôi ống quần sờn gối…', 'Xích kêu leng keng mỗi bước…', 'Bước chân vững như đồng…', 'Đầu gối thép, không quỳ…',
             'Ngồi xổm cũng chẳng chậm…', 'Lướt êm như mèo rừng…', 'Kim cương che đôi chân…', 'Không gì quật ngã nổi…'],
    'giay': ['Đôi giày da đi mòn đường làng…', 'Đế xích bám chắc đá…', 'Gót đồng gõ nhịp hành quân…', 'Rơi từ vách cũng chẳng sao…',
             'Chạy suốt đêm không mỏi…', 'Lội nước như đi trên cạn…', 'Bước nào cũng lấp lánh…', 'Đứng vững giữa bão tố…'],
    'kiem': ['Lưỡi đá thô, ý chí sắc…', 'Đã biết mùi máu quái…', 'Ánh đồng lóe trong đêm…', 'Thép rèn qua lửa thử…',
             'Mỗi nhát chém một bài học…', 'Quái rơi đồ nhiều hơn hẳn…', 'Kim cương không biết cùn…', 'Một đường quét, ba con ngã…'],
}
LOAI = {'mu': 'Armor', 'quan': 'Armor', 'giay': 'Armor', 'kiem': 'Weapon'}
NOUN = {'mu': 'Mũ', 'quan': 'Quần', 'giay': 'Giày', 'kiem': 'Kiếm'}

# Mức gốc (chủ server 23/10: "tăng set sau chứ đừng giảm set trước" -> trả về như bản đầu; áo giữ thông số gốc của chủ server).
PROT = [2, 2, 3, 3, 3, 3, 3, 3, 3, 4]
UNB = [3, 3, 3, 3, 4, 4, 4, 4, 4, 4]

def spec(piece, lv):
    attr, skills = {}, []
    if piece == 'kiem':
        ench = {'sharpness': [1, 1, 2, 2, 3, 3, 3, 4, 4, 5][lv], 'unbreaking': UNB[lv]}
        if lv >= 5: ench['looting'] = 1 if lv < 8 else 2
        if lv >= 7: ench['sweeping_edge'] = 1 if lv < 9 else 2
        if lv >= 8: attr['pct_sat_thuong'] = 5 if lv == 8 else 10
        if lv >= 5: skills = {'luot': 1 if lv < 8 else 2}   # phù phép riêng (dmm-enchants): Lướt
        return ench, attr, skills
    ench = {'protection': PROT[lv], 'unbreaking': UNB[lv]}
    if piece == 'mu':
        ench['projectile_protection'] = [1, 1, 2, 2, 2, 3, 3, 3, 3, 3][lv]
        if lv >= 3: ench['respiration'] = 1 if lv < 6 else (2 if lv < 9 else 3)
        if lv >= 6: ench['aqua_affinity'] = 1
        if lv >= 8: attr['pct_mau'] = 5
    elif piece == 'quan':
        ench['blast_protection'] = [1, 1, 2, 2, 2, 3, 3, 3, 3, 3][lv]
        if lv >= 6: ench['swift_sneak'] = 1 if lv < 9 else 2
        if lv >= 7: attr['pct_mau'] = 5 if lv < 9 else 10
    elif piece == 'giay':
        ench['feather_falling'] = [1, 1, 2, 2, 2, 3, 3, 3, 4, 4][lv]
        if lv >= 6: ench['depth_strider'] = 1 if lv < 9 else 2
        if lv >= 8: attr['pct_day_lui'] = 5 if lv == 8 else 10
    return ench, attr, skills

def skill_lines(piece, lv, ench):
    s = []
    if piece == 'mu' and lv >= 3: s.append('&7• Mũ: thở dưới nước lâu hơn' + (', đào dưới nước nhanh' if lv >= 6 else ''))
    if piece == 'quan' and lv >= 6: s.append('&7• Quần: ngồi (shift) vẫn đi nhanh')
    if piece == 'giay': s.append('&7• Giày: giảm sát thương khi rơi' + (', đi nhanh dưới nước' if lv >= 6 else ''))
    if piece == 'kiem' and lv >= 5: s.append('&7• Chuột phải: &eLướt ' + ('I' if lv < 8 else 'II') + '&7 về phía trước (hồi 4 giây)')
    if piece == 'kiem' and lv >= 5: s.append('&7• Kiếm: quái rơi &enhiều đồ hơn')
    if piece == 'kiem' and lv >= 7: s.append('&7• Kiếm: chém &equét ngang&7 trúng nhiều quái')
    return s

def stat_lines(attr):
    s = []
    if attr.get('pct_mau'): s.append(f"&7• Tăng &a{attr['pct_mau']}%&7 sinh lực tối đa")
    if attr.get('pct_day_lui'): s.append(f"&7• Tăng &a{attr['pct_day_lui']}%&7 kháng đẩy lùi")
    if attr.get('pct_sat_thuong'): s.append(f"&7• Tăng &a{attr['pct_sat_thuong']}%&7 sát thương")
    return s

made = 0
for piece in ['mu', 'quan', 'giay', 'kiem']:
    for lv in range(10):
        ao = items[AO[lv]]
        name = str(ao['name']).replace('Giáp', NOUN[piece], 1)
        lore = []
        src = list(ao['lore'])
        i_attr = next(i for i, l in enumerate(src) if 'THUỘC TÍNH' in l)
        i_skill = next(i for i, l in enumerate(src) if 'KỸ NĂNG' in l)
        i_end = next(i for i in range(i_skill + 1, len(src)) if src[i].startswith('&8&m'))
        ench, attr, dmm = spec(piece, lv)
        for l in src[:i_attr]:
            if l.startswith('&f◆ &e'):   # dòng tên: "◆ Giáp tân thủ +N"
                l = '&f◆ &e' + NOUN[piece] + ' tân thủ &a+' + str(lv)
            elif 'Loại:' in l:
                l = '&f◆ &7Loại: &f' + LOAI[piece]
            elif 'Phẩm cấp:' in l:
                l = '&f◆ &7Phẩm cấp: ' + RAR[lv]
            lore.append(l)
        lore.append(src[i_attr])
        lore += stat_lines(attr)
        lore.append(src[i_skill])
        sk = skill_lines(piece, lv, ench)
        lore += sk if sk else ['&7  Chưa có kỹ năng']
        lore.append(src[i_end])
        tail = src[i_end + 1:]
        if lv < 8:   # cấp 8, 9 giữ thanh "Loading" như áo
            tail = ['&7&o' + FLAVOR[piece][lv]]
        lore += tail
        e = {'nguon': 'web', 'nhom': 'vu-khi' if piece == 'kiem' else 'giap', 'ten-goi': f'{NOUN[piece]} tân thủ +{lv}',
             'material': (SWORD_MAT if piece == 'kiem' else ARMOR_MAT)[lv] + '_' + {'mu': 'HELMET', 'quan': 'LEGGINGS', 'giay': 'BOOTS', 'kiem': 'SWORD'}[piece],
             'amount': 1, 'name': name, 'lore': lore, 'enchants': ench}
        if attr: e['attributes'] = attr
        if dmm: e['dmm-enchants'] = dmm
        if lv >= 6:
            e['compact-enchants'] = True
            e['hide-stats'] = True
        e['bo'] = ['Tân thủ', 'Đồ set']
        e['nguoi-luu'] = 'Claude (Thợ Rèn)'
        items[f'tt_{piece}_{lv}'] = e
        made += 1
y.dump(doc, open(P, 'w', encoding='utf-8'))
print('đã ghi', made)
for k in ['tt_mu_0', 'tt_kiem_5', 'tt_giay_9']:
    print(k, items[k]['name'], '|', ' / '.join(items[k]['lore']))
