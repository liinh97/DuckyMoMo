# Thêm dòng TRUYỀN THUYẾT + GỢI Ý NGUỒN vào mô tả mảnh / tinh thể / mảnh ấn (chủ server 23/10: người chơi phải biết mảnh kiếm ở đâu,
# kể theo hướng có cốt truyện). Chạy SAU gen_manh.py / gen_sets.py (trong container panel), chạy lại nhiều lần không bị nhân đôi:
#   docker exec -i duckymomo-panel-1 python3 - < tools/thoren/gen_truyen.py && docker exec duckymomo-banghoi-1-1 rcon-cli "dmm thuvien nap"
import sys
sys.path.insert(0, '/app')
import app

TRUYEN = {
    # mã: (truyền thuyết, nguồn)
    'manh_than': ('Lò rèn Kinh Đô chưa từng tắt lửa từ ngày lập quốc, nhờ than hồng của các mỏ phương bắc.',
                  'Đào than trong hang, mỏ; đem 64 than tới Lão Khoáng.'),
    'manh_dong': ('Đồng tiền đầu tiên của Kinh Đô được đúc từ chính loại đồng rỉ xanh này.',
                  'Đồng thô nhiều ở hang sâu, vách đá khô; đổi ở Lão Khoáng.'),
    'manh_sat': ('Mọi thanh kiếm Kinh Đô đều bắt đầu từ một mảnh sắt nguội trong giếng cổ.',
                 'Quặng sắt có ở mọi tầng đá; đổi ở Lão Khoáng.'),
    'manh_vang': ('Thợ kim hoàn chỉ nhận vàng đãi đủ mười lần ở các dòng sông phương nam.',
                  'Vàng thô ở hang sâu và vùng đất đỏ; đổi ở Lão Khoáng.'),
    'manh_lam': ('Người xưa tin lưu ly là đá của trời, giữ được cả giông bão.',
                 'Lưu ly nằm ở tầng đá sâu; đổi ở Lão Khoáng.'),
    'manh_hong': ('Đá đỏ rung lên mỗi khi có sấm, thợ rèn gọi nó là mạch máu của đất.',
                  'Đá đỏ ở tầng đá đen sâu nhất; đổi ở Lão Khoáng.'),
    'vun_kc': ('Kim cương vỡ từ trận Công Thành đầu tiên vẫn còn lưu lạc dưới lòng đất.',
               'Kim cương dưới tầng đá đen (gần đáy thế giới); đổi ở Lão Khoáng.'),
    'manh_hac': ('Hắc thiết chỉ sinh ra ở Địa Ngục, nơi đặt lò rèn của quỷ.',
                 'Đào mảnh vỡ cổ đại ở Nether, nung ra phế liệu netherite; đổi ở Lão Khoáng.'),
    'xuong_oan': ('Bộ xương nào bị hạ dưới trăng tròn đều để lại một mảnh oán khí.',
                  'Hạ bộ xương ban đêm, trong hang; đổi xương ở Lão Săn Quái.'),
    'to_bac': ('Nhện hang chỉ dệt tơ khi trăng lên, sợi tơ dai hơn cả dây cung.',
               'Hạ nhện, nhện hang ở mỏ bỏ hoang; đổi tơ ở Lão Săn Quái.'),
    'hoa_duoc': ('Creeper sinh ra từ cơn giận của đất, thuốc súng của chúng không bao giờ ẩm.',
                 'Hạ creeper trước khi nó kịp nổ; đổi ở Lão Săn Quái.'),
    'le_ender': ('Kẻ lang thang Ender rơi lệ mỗi khi bị nhìn thẳng vào mắt.',
                 'Hạ Enderman ban đêm, ở Nether hoặc End; đổi ở Lão Săn Quái.'),
    'vay_trang': ('Ngư Ông kể: chỉ cá phơi dưới trăng rằm mới giữ được ánh bạc trên vảy.',
                  'Câu cá tuyết và cá hồi; đổi ở Ngư Ông.'),
    'hop_kim': ('Phôi đầu tiên của mọi bộ giáp Kinh Đô, nung từ ba thứ kim loại.',
                'Ghép ở Thợ Kim Hoàn: Sắt Nguội + Than Hồng + Đồng Cổ.'),
    'lam_chau': ('Hạt châu giữ cả bầu trời bên trong, vua chúa xưa đính lên vương miện.',
                 'Ghép ở Thợ Kim Hoàn: Lam Ngọc + Bụi Vàng + Vụn Kim Cương.'),
    'huyet_tinh': ('Đá đỏ thấm máu quái tinh nhuệ, ấm như vừa còn thở.',
                   'Ghép ở Thợ Kim Hoàn: Hồng Ngọc + Xương Oán Linh + Lõi Quái.'),
    'hon_quai': ('Hồn trăm con quái tụ lại thành một ngôi sao, thợ rèn sợ chạm tay trần.',
                 'Ghép ở Thợ Kim Hoàn: Lõi Quái + Lệ Ender + Tơ Nhện Bạc + Hỏa Dược Đen.'),
    'hac_dieu': ('Đá đen khóc ra ánh tím, thứ duy nhất rèn được netherite cho người phàm.',
                 'Ghép ở Thợ Kim Hoàn: Hắc Thiết + Phôi Hợp Kim + Lam Linh Châu.'),
    'ngoc_trieu': ('Áp vào tai nghe được tiếng sóng của đại dương đầu tiên.',
                   'Ghép ở Thợ Kim Hoàn: Vảy Cá Hiếm + Vảy Ánh Trăng + Lam Linh Châu.'),
    'loiquai': ('Trái tim của quái tinh nhuệ, còn nóng hổi khi rơi ra.',
                'Hạ quái tinh nhuệ (quái có tên) ngoài tự nhiên hoặc trong dungeon.'),
    'vaycahiem': ('Vảy của loài cá chỉ cắn câu người kiên nhẫn nhất.',
                  'Câu cá ở bất cứ đâu, thỉnh thoảng vớt được.'),
    'xuquai': ('Đồng xu cũ quái vật nuốt vào từ thời Kinh Đô còn là làng nhỏ.',
               'Hạ quái thù địch (quái tinh nhuệ rơi nhiều hơn).'),
    # mảnh ấn dungeon: tầng 1–3 Chiến Ấn, 4–6 Linh Ấn, 7–10 Kim Ấn (tầng dễ mũ/giày, khó hơn giáp/quần, khó nhất vũ khí)
    'cb_an_khoi': ('Ấn của đội tiền quân đã ngã xuống trên Đỉnh Vách Đá.',
                   'Quái ở Đỉnh Vách Đá (tầng 1), Thánh Đường Bóng Tối.'),
    'cb_an_than': ('Giáp ấn của đội lính giữ Hang Thánh Địa, chưa từng lùi bước.',
                   'Quái ở Hang Thánh Địa (tầng 2), Thánh Đường Bóng Tối.'),
    'cb_an_binh': ('Binh ấn khắc trên vũ khí những người thợ mỏ chống quái ở Mỏ Hoang.',
                   'Quái ở Mỏ Hoang (tầng 3), Lễ Hội Pháo Hoa.'),
    'ta_an_khoi': ('Linh ấn của đội cận vệ canh Cây Cầu Cổ.',
                   'Quái ở Cây Cầu Cổ (tầng 4), Cống Ngầm.'),
    'ta_an_than': ('Linh ấn rơi ra khi Thành Phố Đổ Nát sụp xuống.',
                   'Quái ở Thành Phố Đổ Nát (tầng 5), Cống Ngầm, Đấu Trường Ma.'),
    'ta_an_binh': ('Linh ấn trên kiếm của hoàng gia trong Cung Điện bỏ hoang.',
                   'Quái ở Cung Điện (tầng 6), Đấu Trường Ma.'),
    'cc_an_khoi': ('Kim ấn của vua thợ mỏ, chôn dưới đáy Mỏ Đá.',
                   'Quái ở Mỏ Đá (tầng 7), Chuông Địa Ngục, Bắc Cực, Kẻ Trói Buộc, Xưởng Hơi Nước.'),
    'cc_an_than': ('Kim ấn của đạo quân lạc trong Mỏ Sâu, giáp vẫn còn sáng.',
                   'Quái ở Mỏ Sâu (tầng 8), Chuông Địa Ngục, Bắc Cực, Lâu Đài Hiệp Sĩ, Kẻ Trói Buộc, Xưởng Hơi Nước.'),
    'cc_an_binh': ('Kim ấn khắc trên vũ khí của tướng quỷ ở Hoang Mạc Địa Ngục.',
                   'Quái ở Hoang Mạc Địa Ngục (tầng 9), Chuông Địa Ngục, Lâu Đài Hiệp Sĩ, Kẻ Trói Buộc, Xưởng Hơi Nước.'),
    'ht_an_khoi': ('Ấn của các hiệp sĩ rồng, chỉ lộ ra khi kẻ canh giữ cuối cùng ngã xuống.',
                   'Rất hiếm: quái ở Chuông Địa Ngục (tầng 10), Bắc Cực, Kẻ Trói Buộc, Xưởng Hơi Nước.'),
    'ht_an_than': ('Giáp ấn khắc vảy rồng, truyền từ đời hiệp sĩ đầu tiên của Kinh Đô.',
                   'Rất hiếm: quái ở Lâu Đài Hiệp Sĩ, Bắc Cực, Kẻ Trói Buộc, Xưởng Hơi Nước.'),
    'ht_an_binh': ('Binh ấn trên thanh kiếm từng chém đứt sừng rồng.',
                   'Rất hiếm: quái ở Lâu Đài Hiệp Sĩ, Kẻ Trói Buộc, Xưởng Hơi Nước.'),
    'long_hon': ('Hồn con rồng cuối cùng bị nhốt trong viên châu, thợ kim hoàn phải đeo găng da rồng mới cầm được.',
                 'Ghép ở Thợ Kim Hoàn: Hồn Quái Kết Tinh + Hắc Diệu Thạch + Ngọc Thủy Triều + Huyết Tinh Thạch.'),
    # Thần Thoại / Thần Thánh (23/10): chủ server CHƯA chốt nơi rơi -> nguồn để "chưa ai tìm ra", mở sau thì sửa dòng nguồn ở đây
    'mt_an_khoi': ('Ấn của chư thần canh giữ cổng trời, mỗi trăm năm mới rơi xuống nhân gian một lần.',
                   'Chưa ai tìm ra. Lời đồn: nằm trong tay kẻ mạnh nhất thế giới này.'),
    'mt_an_than': ('Thần Ấn khắc tên các vị thần đã ngã xuống trong trận chiến đầu tiên.',
                   'Chưa ai tìm ra. Lời đồn: nằm trong tay kẻ mạnh nhất thế giới này.'),
    'mt_an_binh': ('Ấn trên chuôi thần binh, ai chạm vào sẽ nghe tiếng thần linh thì thầm.',
                   'Chưa ai tìm ra. Lời đồn: nằm trong tay kẻ mạnh nhất thế giới này.'),
    'dv_an_khoi': ('Thánh Ấn rơi từ vầng hào quang của đấng sáng thế.',
                   'Chưa ai tìm ra. Người xưa nói chỉ trời mới ban cho.'),
    'dv_an_than': ('Thánh Ấn sáng rực, đêm tối nhất cũng không che được.',
                   'Chưa ai tìm ra. Người xưa nói chỉ trời mới ban cho.'),
    'dv_an_binh': ('Thánh Ấn trên thanh kiếm phán xét, sét đánh theo mỗi nhát chém.',
                   'Chưa ai tìm ra. Người xưa nói chỉ trời mới ban cho.'),
    'nhat_nguyet': ('Ngày và đêm bị nhốt chung trong một viên châu, xoay mãi không ngừng.',
                    'Ghép ở Thợ Kim Hoàn: Long Hồn Châu + Ngọc Thủy Triều + Hắc Diệu Thạch.'),
    'thien_menh': ('Viên châu chọn chủ: người không đủ mệnh cầm vào sẽ bị bỏng tay.',
                   'Ghép ở Thợ Kim Hoàn: Nhật Nguyệt Châu + Hồn Quái Kết Tinh + Ngôi sao Nether.'),
    # set mùa U Linh (Lễ Hội Bí Ngô)
    'keo_hon': ('Mỗi đêm hội, ma quỷ trốn khỏi nghĩa địa đi xin kẹo; hạ chúng thì kẹo rơi ra.',
                'Chỉ trong mùa Lễ Hội Bí Ngô: hạ quái thù địch ngoài tự nhiên (không tính lồng sinh quái).'),
    # set nghề Nông Gia (quầy Lão Nông)
    'bong_lua': ('Lão Nông giữ lại bông lúa đẹp nhất mỗi vụ để dâng lên đình làng.',
                 'Trồng lúa mì; đem 64 lúa tới Lão Nông.'),
    'cu_qua': ('Củ to nhất mùa được rước đi khắp Kinh Đô trong ngày hội được mùa.',
               'Trồng cà rốt, khoai tây; đổi ở Lão Nông.'),
    'mat_ong': ('Ong rừng chỉ cho mật người biết đặt lửa hun khói, không phá tổ.',
                'Lấy mật ong ở tổ ong (đặt lửa trại bên dưới cho ong khỏi đốt); đổi ở Lão Nông.'),
}
MARK = '✧ Truyền thuyết'

with app.LIB_LOCK:
    y, doc = app.lib_load()
    items = doc['items']
    n = 0
    for iid, (legend, src) in TRUYEN.items():
        it = items.get(iid)
        if not it:
            print('thiếu', iid)
            continue
        lore = [l for l in (it.get('lore') or []) if MARK not in str(l) and '⚑ Nguồn' not in str(l)]
        add = ['&6' + MARK + ': &7&o' + legend, '&a⚑ Nguồn: &f' + src]
        it['lore'] = lore[:1] + add + lore[1:]   # sau dòng mô tả đầu
        n += 1
    app.lib_write(y, doc)
print('đã thêm truyền thuyết + nguồn cho', n, 'món')
