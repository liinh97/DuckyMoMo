# Mảnh (tầng 2) + tinh thể (tầng 3) cho Thợ Rèn / quầy đổi ở Kinh Đô. Lấp lánh (glint) + dấu thuvien_id (không làm giả được).
from ruamel.yaml import YAML
P = '/data/banghoi-1/plugins/DuckyMoMoCore/thuvien-do.yml'
y = YAML(); y.preserve_quotes = True; y.width = 4096
doc = y.load(open(P, encoding='utf-8'))
items = doc['items']

# mã: (vật phẩm nền, tên, câu mô tả, nguồn)
MANH = {
    # --- Lão Khoáng (khoáng sản thô)
    'manh_than': ('FIREWORK_STAR', '&8✦ &cMảnh Than Hồng', 'Than nung đỏ ba ngày ba đêm mới kết thành mảnh.', 'Lão Khoáng: 64 Than'),
    'manh_dong': ('COPPER_NUGGET', '&8✦ &6Mảnh Đồng Cổ', 'Đồng rỉ xanh từ những mạch quặng ngàn năm.', 'Lão Khoáng: 48 Đồng thô'),
    'manh_sat': ('IRON_NUGGET', '&8✦ &fMảnh Sắt Nguội', 'Sắt tôi qua nước giếng sâu, cứng như đá.', 'Lão Khoáng: 32 Sắt thô'),
    'manh_vang': ('GLOWSTONE_DUST', '&8✦ &eBụi Vàng Ròng', 'Vàng đãi mười lần, chỉ còn lại bụi sáng.', 'Lão Khoáng: 24 Vàng thô'),
    'manh_lam': ('PRISMARINE_SHARD', '&8✦ &9Mảnh Lam Ngọc', 'Lam ngọc mài từ đá lưu ly dưới lòng núi.', 'Lão Khoáng: 48 Lưu ly'),
    'manh_hong': ('RESIN_BRICK', '&8✦ &cMảnh Hồng Ngọc', 'Bụi đỏ nén chặt, vẫn còn rung lên khe khẽ.', 'Lão Khoáng: 64 Đá đỏ'),
    'vun_kc': ('PRISMARINE_CRYSTALS', '&8✦ &bVụn Kim Cương', 'Kim cương vỡ vụn, mỗi hạt vẫn lấp lánh.', 'Lão Khoáng: 4 Kim cương'),
    'manh_hac': ('DISC_FRAGMENT_5', '&8✦ &5Mảnh Hắc Thiết', 'Mảnh vỡ từ lò rèn ở tận cùng Địa Ngục.', 'Lão Khoáng: 2 Phế liệu netherite'),
    # --- Lão Săn Quái (chiến lợi phẩm quái)
    'xuong_oan': ('SKELETON_SKULL', '&8✦ &7Xương Oán Linh', 'Xương của bộ xương chết không nhắm mắt.', 'Lão Săn Quái: 48 Xương'),
    'to_bac': ('COBWEB', '&8✦ &fTơ Nhện Bạc', 'Tơ nhện hang dệt dưới ánh trăng, dai như thép.', 'Lão Săn Quái: 48 Tơ'),
    'hoa_duoc': ('FIRE_CHARGE', '&8✦ &6Hỏa Dược Đen', 'Thuốc súng creeper, chỉ chờ một tia lửa.', 'Lão Săn Quái: 48 Thuốc súng'),
    'le_ender': ('ENDER_EYE', '&8✦ &2Lệ Ender', 'Giọt nước mắt của kẻ lang thang giữa hai cõi.', 'Lão Săn Quái: 12 Ngọc ender'),
    # --- Ngư Ông
    'vay_trang': ('TURTLE_SCUTE', '&8✦ &3Vảy Ánh Trăng', 'Vảy cá phơi dưới trăng rằm, ánh bạc không phai.', 'Ngư Ông: 24 Cá tuyết + 24 Cá hồi'),
}
TINH = {
    # --- Thợ Kim Hoàn (ghép mảnh -> tinh thể)
    'hop_kim': ('HEAVY_CORE', '&6❖ &e&lPhôi Hợp Kim', 'Sắt, than, đồng nung chảy làm một.', 'Thợ Kim Hoàn: 4 Sắt Nguội + 3 Than Hồng + 2 Đồng Cổ'),
    'lam_chau': ('HEART_OF_THE_SEA', '&6❖ &9&lLam Linh Châu', 'Hạt châu xanh thẳm, giữ cả bầu trời bên trong.', 'Thợ Kim Hoàn: 4 Lam Ngọc + 2 Bụi Vàng + 1 Vụn Kim Cương'),
    'huyet_tinh': ('RESIN_CLUMP', '&6❖ &c&lHuyết Tinh Thạch', 'Đá đỏ thấm máu quái, ấm như vừa thở.', 'Thợ Kim Hoàn: 4 Hồng Ngọc + 2 Xương Oán Linh + 2 Lõi Quái'),
    'hon_quai': ('NETHER_STAR', '&6❖ &d&lHồn Quái Kết Tinh', 'Linh hồn trăm con quái tụ lại thành sao.', 'Thợ Kim Hoàn: 4 Lõi Quái + 2 Lệ Ender + 2 Tơ Nhện Bạc + 2 Hỏa Dược Đen'),
    'hac_dieu': ('CRYING_OBSIDIAN', '&6❖ &5&lHắc Diệu Thạch', 'Đá đen khóc ra ánh tím, rèn được netherite.', 'Thợ Kim Hoàn: 2 Hắc Thiết + 2 Phôi Hợp Kim + 1 Lam Linh Châu'),
    'ngoc_trieu': ('CONDUIT', '&6❖ &b&lNgọc Thủy Triều', 'Nghe được tiếng sóng khi áp vào tai.', 'Thợ Kim Hoàn: 4 Vảy Cá Hiếm + 4 Vảy Ánh Trăng + 1 Lam Linh Châu'),
}
for iid, (mat, name, desc, src) in MANH.items():
    items[iid] = {'nguon': 'web', 'nhom': 'nguyen-lieu', 'ten-goi': name.split(' ', 1)[1].replace('&', '').lstrip('0123456789abcdefklmnor'),
                  'material': mat, 'amount': 1, 'name': name,
                  'lore': ['&7' + desc, '', '&8Mảnh · ' + src, '&8Dùng ở Thợ Kim Hoàn, Thợ Rèn (/thoren)'], 'glint': True,
                  'bo': ['Nguyên liệu Thợ Rèn'], 'nguoi-luu': 'Claude (Thợ Rèn)'}
for iid, (mat, name, desc, src) in TINH.items():
    items[iid] = {'nguon': 'web', 'nhom': 'nguyen-lieu', 'ten-goi': name.split('&l', 1)[1],
                  'material': mat, 'amount': 1, 'name': name,
                  'lore': ['&7' + desc, '', '&6Tinh thể · &8' + src, '&8Dùng ở Thợ Rèn (/thoren)'], 'glint': True,
                  'bo': ['Nguyên liệu Thợ Rèn'], 'nguoi-luu': 'Claude (Thợ Rèn)'}
y.dump(doc, open(P, 'w', encoding='utf-8'))
print('đã ghi', len(MANH), 'mảnh +', len(TINH), 'tinh thể')
print({k: items[k]['ten-goi'] for k in list(MANH)[:3] + list(TINH)[:2]})
