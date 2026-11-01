# Set THẦN NÔNG (1/11, chủ server): set nghề trồng trọt bậc cao, nâng từ Nông Gia +4, +0..+5, ~1 tháng cày cả bộ +5.
# Sức mạnh: +0 ~ giữa Chiến Binh +5 và Tinh Anh +0, +5 ~ dưới Tinh Anh +5 một chút (bù bằng nghề nông: Lưỡi hái gặt 5x5,
# nông sản gấp 2-3, cây quanh người lớn nhanh — ThoRen.java). Nguyên liệu mới: 7 mảnh Lão Nông, Quả Linh Tinh, Ngũ Cốc Tinh,
# Thần Mộc Châu, Hạt Giống Thần (rơi khi tự gặt cây chín, tho-ren.harvest-drop).
# Chạy trong container panel:  docker exec -i duckymomo-panel-1 python3 - < tools/thoren/gen_thannong.py
#   rồi  docker exec duckymomo-banghoi-1-1 rcon-cli "dmm thuvien nap"
# Chạy lại không sao (ghi đè đúng các mã của set này).
from ruamel.yaml import YAML
P = '/data/banghoi-1/plugins/DuckyMoMoCore/thuvien-do.yml'
y = YAML(); y.preserve_quotes = True; y.width = 4096
with open(P, encoding='utf-8') as f:
    doc = y.load(f)
items = doc['items']


def nl(iid, mat, name, desc, truyen, nguon, kind, src, dung='Dùng ở Thợ Kim Hoàn, Thợ Rèn (/thoren)'):
    items[iid] = {'nguon': 'web', 'nhom': 'nguyen-lieu', 'ten-goi': name.split(' ', 1)[1].replace('&l', '').lstrip('&0123456789abcdefklmnor'),
                  'material': mat, 'amount': 1, 'name': name,
                  'lore': ['&7' + desc, '&6✧ Truyền thuyết: &7&o' + truyen, '&a⚑ Nguồn: &f' + nguon, '', kind + src, '&8' + dung],
                  'glint': True, 'bo': ['Nguyên liệu Thợ Rèn', 'Thần Nông'], 'nguoi-luu': 'Claude (Thợ Rèn)'}


# ---- mảnh Lão Nông (đổi nông sản thô)
MANH = [
    ('cu_den', 'BEETROOT', '&8✦ &cCủ Dền Đỏ', 'Củ dền đỏ thẫm, cắt ra nhuộm đỏ cả tay.', 'Dân làng tin củ dền đỏ nhất mang may mắn cho cả vụ sau.', 'Trồng củ dền; đem 64 củ tới Lão Nông.', 'Lão Nông: 64 Củ dền'),
    ('bi_vang', 'PUMPKIN_PIE', '&8✦ &6Bí Vàng', 'Quả bí to nhất ruộng, vỏ vàng như nắng.', 'Bí vàng được giữ lại làm giống cho mùa sau.', 'Trồng bí ngô; đem 32 quả tới Lão Nông.', 'Lão Nông: 32 Bí ngô'),
    ('dua_ngot', 'GLISTERING_MELON_SLICE', '&8✦ &aDưa Ngọt', 'Miếng dưa ngọt lịm, vỏ lấp lánh như phủ vàng.', 'Người ta nói dưa trồng dưới trăng rằm ngọt hơn mật.', 'Trồng dưa hấu; đem 32 khối dưa tới Lão Nông.', 'Lão Nông: 32 Khối dưa hấu'),
    ('duong_mia', 'SUGAR', '&8✦ &fĐường Mía', 'Đường thắng từ mía bờ sông, trắng như tuyết.', 'Mía mọc bên nước mới ngọt; mía trên cạn chỉ toàn xơ.', 'Trồng mía; đem 64 cây tới Lão Nông.', 'Lão Nông: 64 Mía'),
    ('ca_cao', 'COCOA_BEANS', '&8✦ &6Hạt Ca Cao', 'Hạt ca cao rừng rậm, rang lên thơm nức.', 'Ca cao chỉ chịu mọc trên thân cây rừng rậm.', 'Trồng ca cao; đem 64 hạt tới Lão Nông.', 'Lão Nông: 64 Hạt ca cao'),
    ('qua_mong', 'SWEET_BERRIES', '&8✦ &cQuả Mọng Rừng', 'Chùm quả mọng đỏ, gai đâm mới hái được.', 'Quả mọng rừng lá kim nuôi sống người đi rừng qua mùa đông.', 'Trồng dâu ngọt; đem 64 quả tới Lão Nông.', 'Lão Nông: 64 Dâu ngọt'),
    ('nam_dia_nguc', 'NETHER_WART', '&8✦ &4Nấm Địa Ngục', 'Nấm đỏ mọc trên cát linh hồn, ấm nóng trong tay.', 'Chỉ đất Địa Ngục mới nuôi nổi thứ nấm này.', 'Trồng nấm địa ngục (Nether); đem 64 cây tới Lão Nông.', 'Lão Nông: 64 Nấm địa ngục'),
    ('qua_sang', 'GLOW_BERRIES', '&8✦ &eQuả Sáng', 'Quả mọng phát sáng dưới hang sâu.', 'Thợ mỏ lạc đường nhờ quả sáng mà tìm được lối ra.', 'Hái quả mọng phát sáng (hang động); đem 48 quả tới Lão Nông.', 'Lão Nông: 48 Quả mọng phát sáng'),
    ('tao_bien', 'DRIED_KELP', '&8✦ &2Tảo Biển', 'Tảo phơi khô gió biển, dai và mặn.', 'Ngư dân ven biển trồng tảo dưới nước như trồng lúa.', 'Trồng tảo bẹ dưới nước; đem 128 tảo tới Lão Nông.', 'Lão Nông: 128 Tảo bẹ'),
    ('qua_chorus', 'POPPED_CHORUS_FRUIT', '&8✦ &dQuả Chorus Nổ', 'Quả của cây mọc giữa hư không, nướng lên nở bung.', 'Ăn sống thì bị dịch chuyển bậy, nướng chín mới dùng được.', 'Hái quả chorus ở The End; đem 32 quả tới Lão Nông.', 'Lão Nông: 32 Quả chorus'),
]
for iid, mat, name, desc, tr, ng, src in MANH:
    nl(iid, mat, name, desc, tr, ng, '&8Mảnh · ', src)

# ---- tinh thể Thợ Kim Hoàn
nl('ngu_coc', 'HAY_BLOCK', '&6❖ &e&lNgũ Cốc Tinh', 'Lúa, củ, dền ép lại thành một khối vàng óng.', 'Một khối Ngũ Cốc Tinh nuôi được cả làng qua mùa giáp hạt.',
   'Ghép ở Thợ Kim Hoàn: Bông Lúa + Củ Quả + Củ Dền.', '&6Tinh thể · &8', 'Thợ Kim Hoàn: 4 Bông Lúa + 4 Củ Quả + 2 Củ Dền + 400 xu', 'Dùng ở Thợ Kim Hoàn, Thợ Rèn (set Thần Nông)')
nl('qua_linh', 'GOLDEN_APPLE', '&6❖ &a&lQuả Linh Tinh', 'Tinh hoa của bốn loại quả, thơm suốt ba ngày.', 'Quả Linh Tinh chỉ kết khi đủ bí, dưa, quả mọng và quả sáng.',
   'Ghép ở Thợ Kim Hoàn: Bí Vàng + Dưa Ngọt + Quả Mọng + Quả Sáng.', '&6Tinh thể · &8', 'Thợ Kim Hoàn: 2 Bí Vàng + 2 Dưa Ngọt + 2 Quả Mọng + 2 Quả Sáng + 400 xu', 'Dùng ở Thợ Kim Hoàn, Thợ Rèn (set Thần Nông)')
nl('than_moc', 'MANGROVE_PROPAGULE', '&6❖ &2&lThần Mộc Châu', 'Mầm cây thần, ôm cả đất trời bốn mùa.', 'Truyền rằng Thần Nông gieo hạt này một lần, ruộng lúa xanh trăm năm.',
   'Ghép ở Thợ Kim Hoàn: Ngũ Cốc Tinh + Quả Linh Tinh + Nấm Địa Ngục + Địa Linh + Hạt Giống Thần + Sáp Mật.', '&6Tinh thể · &8',
   'Thợ Kim Hoàn: 1 Ngũ Cốc Tinh + 1 Quả Linh Tinh + 2 Nấm Địa Ngục + 1 Địa Linh + 1 Hạt Giống Thần + 2 Sáp Mật + 1.000 xu', 'Dùng ở Thợ Rèn (set Thần Nông)')
# ---- Hạt Giống Thần: chỉ rơi khi tự tay gặt cây chín
items['hat_than'] = {'nguon': 'web', 'nhom': 'nguyen-lieu', 'ten-goi': 'Hạt Giống Thần', 'material': 'TORCHFLOWER_SEEDS', 'amount': 1,
                     'name': '&6✿ &a&lHạt Giống Thần',
                     'lore': ['&7Hạt giống lấp lánh, nảy mầm trong lòng bàn tay.',
                              '&6✧ Truyền thuyết: &7&oNghìn cây lúa mới có một hạt được thần đất ban phước.',
                              '&a⚑ Nguồn: &fTự tay gặt cây đã chín (lúa, cà rốt, khoai, củ dền, nấm địa ngục, ca cao…), hiếm; mùa thuận cho cây đó rơi nhiều hơn.',
                              '&8Máy farm, dân làng gặt không rơi.', '', '&8Dùng ở Thợ Kim Hoàn, Thợ Rèn (set Thần Nông)'],
                     'glint': True, 'bo': ['Nguyên liệu Thợ Rèn', 'Thần Nông'], 'nguoi-luu': 'Claude (Thợ Rèn)'}

# ---- 30 món: mũ / giáp / quần / giày / lưỡi hái (khoá "cuoc" như Nông Gia để nâng từ Nông Gia +4)
SLOT = {'mu': 'HELMET', 'ao': 'CHESTPLATE', 'quan': 'LEGGINGS', 'giay': 'BOOTS', 'cuoc': 'HOE'}
NOUN = {'mu': 'Mũ', 'ao': 'Giáp', 'quan': 'Quần', 'giay': 'Giày', 'cuoc': 'Lưỡi hái'}
RAR = [('&5', 'Epic'), ('&5', 'Epic'), ('&5', 'Epic'), ('&6', 'Legendary'), ('&6', 'Legendary'), ('&6', 'Legendary')]
PROT = [4, 5, 5, 5, 6, 6]
UNB = [4, 4, 4, 5, 5, 5]
FLAV = {
    'mu': ['Nón lá thần ban…', 'Che nắng cho cả cánh đồng…', 'Nhìn mây biết mưa…', 'Gió bốn mùa nghe lời…', 'Đội trời đạp đất…', 'Vương miện của mùa màng…'],
    'ao': ['Áo dệt từ rơm vàng…', 'Lúa chín theo bước chân…', 'Đất mẹ che chở…', 'Mùa nào cũng được mùa…', 'Trăm ruộng một lòng…', 'Thần Nông giáng thế…'],
    'quan': ['Lội ruộng không lấm bùn…', 'Bước đến đâu mạ xanh đến đó…', 'Đi suốt đồng không mỏi…', 'Đất nhường đường…', 'Chân in thành luống…', 'Đi qua là mùa vàng…'],
    'giay': ['Dép cỏ thơm…', 'Bước nhẹ trên ruộng…', 'Chạy kịp trước cơn mưa…', 'Chẳng bao giờ trượt bờ…', 'Gót chân nảy mầm…', 'Đạp mây về làng…'],
    'cuoc': ['Lưỡi hái trăng non…', 'Một nhát một sào…', 'Gặt cả đồi lúa…', 'Lúa ngả theo lưỡi hái…', 'Hái cả mùa thu…', 'Lưỡi hái của Thần Nông…'],
}


def roman(n):
    return ['', 'I', 'II', 'III', 'IV', 'V'][n]


for lv in range(6):
    col, rname = RAR[lv]
    common_skills = {}
    for pc in SLOT:
        noun = NOUN[pc]
        ench, attr, ce, skills = {}, {}, {}, []
        if pc == 'mu':
            ench = {'protection': PROT[lv], 'unbreaking': UNB[lv], 'projectile_protection': [2, 3, 3, 3, 4, 4][lv], 'respiration': 3}
            attr = {'pct_mau': [6, 7, 8, 9, 10, 11][lv]}
            ce = {'Implants': 1 if lv < 3 else 2, **({'Glowing': 1} if lv >= 2 else {})}
            skills = ['Làm đồng lâu &ekhông đói'] + (['Nhìn rõ trong đêm'] if lv >= 2 else [])
        elif pc == 'ao':
            ench = {'protection': PROT[lv], 'unbreaking': UNB[lv], 'projectile_protection': [2, 3, 3, 3, 4, 4][lv],
                    'blast_protection': [2, 3, 3, 3, 4, 4][lv], 'fire_protection': [2, 3, 3, 3, 4, 4][lv]}
            attr = {'pct_mau': [16, 18, 20, 22, 24, 26][lv]}
            ce = {'Nursery': 1 if lv < 3 else 2, 'Crouch': 1 if lv < 4 else 2}
            skills = ['Có cơ hội &ahồi máu&7 khi đi bộ', 'Ngồi (shift) lúc chiến đấu: nhận ít sát thương hơn']
        elif pc == 'quan':
            ench = {'protection': PROT[lv], 'unbreaking': UNB[lv], 'blast_protection': [2, 3, 3, 3, 4, 4][lv], 'swift_sneak': 2 if lv < 3 else 3}
            attr = {'pct_mau': [6, 7, 8, 9, 10, 11][lv]}
            skills = ['Ngồi (shift) vẫn đi nhanh']
        elif pc == 'giay':
            ench = {'protection': PROT[lv], 'unbreaking': UNB[lv], 'feather_falling': 4, 'depth_strider': 2 if lv < 3 else 3}
            attr = {'pct_day_lui': [10, 12, 13, 14, 16, 17][lv]}
            ce = {'Gears': 1, **({'Springs': 1} if lv >= 3 else {})}
            skills = ['Chạy nhanh hơn &e20%'] + (['Nhảy cao hơn'] if lv >= 3 else [])
        else:   # lưỡi hái: cuốc 3x3 + gặt 5x5 (ThoRen) + đánh được
            ench = {'efficiency': 5, 'fortune': 3, 'unbreaking': UNB[lv], 'sharpness': [5, 5, 6, 6, 7, 7][lv], 'looting': [1, 2, 2, 2, 3, 3][lv]}
            attr = {'attack_damage': 8, 'pct_sat_thuong': [10, 12, 14, 16, 18, 20][lv]}
            ce = {'Tiller': 1, 'Planter': 1, 'GreenThumb': 3}
            skills = ['Cuốc đất là cuốc luôn &e3×3&7, tự gieo hạt', 'Phá một cây chín: &egặt cả vùng 5×5&7 cây chín, tự gieo lại',
                      'Chuột phải vào cây non: có cơ hội &alớn ngay', 'Sắc như kiếm: &cđánh quái, đánh người được']
        name = f'&2✿ &6&l{noun} Thần Nông &2✿ &e+{lv}'
        lore = ['&2❀━━━━ &6&l☘ THẦN NÔNG ☘ &2━━━━❀',
                f'&a▸ &7Trang bị: &f{noun} &8· &7Netherite',
                f'&a▸ &7Phẩm chất: {col}{rname}',
                '&a▸ &7Mùa vàng: ' + '&e' + '✿' * lv + '&8' + '✿' * (5 - lv) + f' &8({lv}/5)', '']
        st = []
        if attr.get('pct_mau'): st.append(f"&7  ☘ +{attr['pct_mau']}% sinh lực tối đa")
        if attr.get('pct_day_lui'): st.append(f"&7  ☘ +{attr['pct_day_lui']}% kháng đẩy lùi")
        if attr.get('pct_sat_thuong'): st.append(f"&7  ☘ +{attr['pct_sat_thuong']}% sát thương")
        if st:
            lore += ['&6&l✦ SỨC ĐẤT'] + st
        lore += ['&2&l✦ PHÉP NHÀ NÔNG'] + ['&7  ☘ ' + x for x in skills]
        lore += ['&2❀━━━━━━━━━━━━━━━━━━━❀', f'&6&o"{FLAV[pc][lv]}"']
        items[f'tn_{pc}_{lv}'] = {'nguon': 'web', 'nhom': 'cong-cu' if pc == 'cuoc' else 'giap', 'ten-goi': f'{noun} Thần Nông +{lv}',
                                  'material': 'NETHERITE_' + SLOT[pc], 'amount': 1, 'name': name, 'lore': lore,
                                  'enchants': ench, 'attributes': attr, **({'ce-enchants': ce} if ce else {}),
                                  'compact-enchants': True, 'hide-stats': True, 'bo': ['Thần Nông', 'Đồ set'], 'nguoi-luu': 'Claude (Thợ Rèn)'}

with open(P, encoding='utf-8') as f:
    old = f.read()
import io
buf = io.StringIO(); y.dump(doc, buf)
new = buf.getvalue()
assert len(new) > len(old) * 0.9, 'file mới ngắn bất thường, không ghi'
with open(P, 'w', encoding='utf-8') as f:
    f.write(new)
print('đã ghi', len(MANH), 'mảnh, 3 tinh thể, Hạt Giống Thần, 30 món Thần Nông')
