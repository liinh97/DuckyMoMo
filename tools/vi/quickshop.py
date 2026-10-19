"""Tiếng Việt cho QuickShop-Hikari: bản vi_vn của plugin (Crowdin) còn ~450 câu y tiếng Anh -> dịch phần người chơi gặp vào
plugins/QuickShop-Hikari/overrides/vi_vn/messages.yml (file ghi đè, chỉ chứa khoá đã dịch). config.yml: game-language: vi_vn,
enabled-languages: [vi_vn] (mọi người dùng tiếng Việt, kể cả máy để tiếng Anh). Bỏ qua addon.* (không cài addon), debug.*, updatenotify.*, database.*.
Chạy: python3 tools/vi/quickshop.py <lang/en-US/messages.yml trong jar> <file ra>"""
import re
import sys

import yaml

EN, OUT = sys.argv[1], sys.argv[2]

T = {
    "tabcomplete.amount": "[số lượng]", "tabcomplete.item": "[vật phẩm]", "tabcomplete.price": "[giá]", "tabcomplete.name": "[tên]",
    "tabcomplete.range": "[phạm vi]", "tabcomplete.page": "[trang]", "tabcomplete.shop-id": "[mã shop]", "tabcomplete.currency": "[loại tiền]",
    "tabcomplete.percentage": "[phần trăm%]", "tabcomplete.tag": "[nhãn]",
    "quick-fill.entry-own": " <yellow>- <red>[của bạn] <aqua>{0} <light_purple>{1}",
    "3rd-plugin-build-check-failed-admin": "<gray>(Chỉ admin) <light_purple>{0}<dark_gray> đã chặn kiểm tra quyền. Nếu không mong muốn, thử thêm <light_purple>{1} <gray>vào danh sách bỏ qua listener. Hướng dẫn:https://quickshop-community.github.io/QuickShop-Hikari-Documents/docs/modules/shops/protection-checker",
    "shop-owner-self-trade-denied": "<red>Không mua bán với shop của chính mình được.",
    "tags.commands.tag": "<yellow>Quản lý nhãn shop của bạn.",
    "tags.commands.favorite": "<yellow>Bật/tắt yêu thích shop đang nhìn.",
    "tags.commands.watch": "<yellow>Bật/tắt theo dõi shop đang nhìn.",
    "tags.commands.avoid": "<yellow>Bật/tắt né shop đang nhìn.",
    "tags.general.database-error": "<red>Có lỗi khi xử lý nhãn.",
    "tags.general.invalid": "<red>Nhãn không hợp lệ. Chỉ dùng chữ, gạch ngang (-) và gạch dưới (_). Dài tối đa: <yellow>{0}</yellow>",
    "tags.avoid.added": "<green>Đã thêm shop này vào danh sách né.", "tags.avoid.removed": "<green>Đã bỏ shop này khỏi danh sách né.",
    "tags.avoid.unable": "<red>Không đổi được trạng thái né của shop này.", "tags.avoid.none": "<red>Bạn chưa né shop nào.",
    "tags.avoid.label": "<yellow>Né",
    "tags.favorite.added": "<green>Đã thêm shop này vào danh sách yêu thích.", "tags.favorite.removed": "<green>Đã bỏ shop này khỏi danh sách yêu thích.",
    "tags.favorite.unable": "<red>Không đổi được trạng thái yêu thích của shop này.", "tags.favorite.none": "<red>Bạn chưa yêu thích shop nào.",
    "tags.favorite.label": "<yellow>Yêu thích",
    "tags.watch.added": "<green>Bạn đang theo dõi shop này.", "tags.watch.removed": "<green>Bạn đã thôi theo dõi shop này.",
    "tags.watch.unable": "<red>Không đổi được trạng thái theo dõi của shop này.", "tags.watch.none": "<red>Bạn không theo dõi shop nào.",
    "tags.watch.label": "<yellow>Đang theo dõi",
    "tags.tag.title-avoided-shops": "<green>Shop đang né</green> <gray>(Tổng <yellow>{0}</yellow>)</gray>",
    "tags.tag.title-favorited-shops": "<green>Shop yêu thích</green> <gray>(Tổng <yellow>{0}</yellow>)</gray>",
    "tags.tag.title-watched-shops": "<green>Shop đang theo dõi</green> <gray>(Tổng <yellow>{0}</yellow>)</gray>",
    "tags.tag.list-player-shops-title": "<green>Shop có nhãn của bạn</green> <gray>(<yellow>{0}</yellow> shop, <yellow>{1}</yellow> nhãn)</gray>",
    "tags.tag.list-player-shop-title": "<green>Nhãn của shop</green> <gray>(Tổng <yellow>{0}</yellow>)</gray>",
    "tags.tag.list-tag-title": "<green>Nhãn <aqua>{0}</aqua> đang dùng</green> <gray>(<yellow>{1}</yellow> shop)</gray>",
    "tags.tag.list-tag-shop-entry-tp": "<light_purple>[<click:run_command:\"{0}\"><hover:show_text:\"<gray>Dịch chuyển tới shop này\">Dịch chuyển</hover></click>]</light_purple>",
    "tags.tag.added": "<green>Đã gắn nhãn <aqua>{0}</aqua> cho shop này.",
    "tags.tag.duplicate": "<red>Shop này đã có nhãn <aqua>{0}</aqua>.",
    "tags.tag.failed-add": "<red>Có lỗi khi gắn nhãn <aqua>{0}</aqua> cho shop này.",
    "tags.tag.failed-remove": "<red>Có lỗi khi gỡ nhãn <aqua>{0}</aqua> khỏi shop này.",
    "tags.tag.removed": "<green>Đã gỡ nhãn <aqua>{0}</aqua> khỏi shop này.",
    "tags.tag.does-not-exist": "<red>Shop này không có nhãn <aqua>{0}</aqua>.",
    "tags.tag.cleared": "<green>Đã gỡ mọi nhãn của shop này.", "tags.tag.cleared-all": "<green>Đã gỡ mọi nhãn của mọi shop.",
    "tags.tag.cleared-tag": "<green>Đã gỡ nhãn <aqua>{0}</aqua> khỏi mọi shop có nhãn của bạn.",
    "tags.tag.no-tags-all": "<red>Chưa shop nào có nhãn.", "tags.tag.no-tags": "<red>Shop này chưa có nhãn.",
    "tags.tag.no-tagged-shops-player": "<red>Bạn chưa gắn nhãn nào cho shop này.", "tags.tag.no-tagged-shops": "<red>Bạn chưa có shop nào có nhãn.",
    "tags.tag.no-tagged-shops-tag": "<red>Bạn không có shop nào mang nhãn <aqua>{0}</aqua>.",
    "menu.this-shop-is-frozen": "<blue>ĐÓNG BĂNG</blue>", "menu.this-shop-is-active": "<aqua>ĐANG BÁN</aqua>",
    "menu.this-shop-is-state": "<green>Trạng thái hiện tại của shop: {0}<green>.",
    "shop-transaction-failed": "<red>Xin lỗi, có lỗi nội bộ khi xử lý giao dịch. Giao dịch đã huỷ và mọi thay đổi đã hoàn lại. Lý do: {0}",
    "metric.amount-stack-hover": "<yellow>{0}x chồng",
    "shop-type.frozen": "ĐÓNG BĂNG", "shop-state.frozen": "ĐÓNG BĂNG", "shop-state.unfrozen": "MỞ", "shop-state.freeze": "Đóng băng",
    "shop-state.unfreeze": "Mở lại", "shop-display.enabled": "Bật", "shop-display.disabled": "Tắt",
    "restricted-price-max": "<red>Giá của {0} bị giới hạn: tối đa {1}",
    "restricted-price-min": "<red>Giá của {0} bị giới hạn: tối thiểu {1}",
    "shop-type-change-price-restricted": "<red>Giá hiện tại không hợp lệ cho shop {0}. Khoảng cho phép: {1} - {2}. Đổi giá trước rồi mới đổi loại shop.",
    "signs.item-duration": "Thời lượng: {0}", "signs.item-level": "Cấp: {0}", "signs.splash-potion": "Ném", "signs.linger-potion": "Lưu cữu",
    "display-turn-on-owned": "<green>Đã bật vật phẩm trưng bày cho mọi shop của bạn.",
    "display-turn-off-owned": "<green>Đã tắt vật phẩm trưng bày cho mọi shop của bạn.",
    "display-turn-on-all": "<green>Đã bật vật phẩm trưng bày cho mọi shop trên server.",
    "display-turn-off-all": "<green>Đã tắt vật phẩm trưng bày cho mọi shop trên server.",
    "player-sold-to-your-store": "<green>{0} đã bán {1} {2} cho shop của bạn, được {3}.",
    "controlpanel.freeze": "<green>Đóng băng: <aqua>{0} <yellow>[<bold><light_purple>Đổi</light_purple></bold>]",
    "controlpanel.inventory": "<green>Kho: </green><yellow>[<bold><light_purple>Xem kho</light_purple></bold>]",
    "controlpanel.inventory-hover": "<yellow>Mở kho của shop.",
    "command.description.staffall": "<yellow>Quản lý nhân viên ở mọi shop của bạn.",
    "command.description.toggledisplayall": "<yellow>Bật/tắt vật phẩm trưng bày ở mọi shop của bạn (hoặc cả server).",
    "command.description.benefitall": "<yellow>Chia lợi nhuận giữa chủ shop và người khác cho mọi shop của bạn.",
    "command.description.browse": "<yellow>Xem các shop trên server.",
    "admin-shop": "Shop của server",
    "display-fallback": "<red>Do lỗi nội bộ, đang hiện thông báo dự phòng.\nGiá trị của vật phẩm này có thể chưa được dịch hoặc xử lý đúng.\nVui lòng báo admin.",
    "invalid-past-time": "<red>Không chọn được thời điểm trong quá khứ.",
    "transfer-single-request": "<yellow>Người chơi <aqua>{0}</aqua> muốn chuyển một shop cho bạn. Bạn có nhận không?",
    "transferall-ask": "<gold>Gõ <red>/quickshop transferall accept</red> để nhận hoặc <red>/quickshop transferall deny</red> để từ chối.\nYêu cầu hết hạn sau <red>{0}</red> giây.",
    "transfer-single-ask": "<gold>Gõ <red>/quickshop transferownership accept</red> để nhận hoặc <red>/quickshop transferownership deny</red> để từ chối.\nYêu cầu hết hạn sau <red>{0}</red> giây.",
    "transfer-no-self": "<red>Không chuyển shop cho chính mình được.",
    "transfer-no-buy": "<red>Không chuyển được shop thu mua.",
    "transfer-failed-some": "<red>Một số shop không chuyển được vì không đủ điều kiện.",
    "benefit-overflow": "<red>Tổng phần chia lợi nhuận phải nhỏ hơn 100%.",
    "benefit-exists": "<red>Người này đã có trong danh sách chia lợi nhuận của shop.",
    "benefit-removed": "<red>Đã bỏ người này khỏi danh sách chia lợi nhuận.",
    "benefit-added": "<green>Đã thêm <aqua>{0}</aqua> vào danh sách chia lợi nhuận!",
    "benefit-updated": "<green>Đã cập nhật phần chia lợi nhuận của <aqua>{0}</aqua>!",
    "benefit-query": "<green>Shop này có <yellow>{0}</yellow> người được chia lợi nhuận!",
    "batch-operations-based-on-tags-no-failure": "<green>Đã xử lý hàng loạt <yellow>{0}</yellow> shop.",
    "batch-operations-based-on-tags-have-failure-error-messages-may-appears-above": "<gray>Thông báo lỗi (nếu có) nằm phía trên dòng này trong chat.",
    "compat.advancedchests.created": "<green>Đã tạo shop trên rương AdvancedChests!",
    "compat.advancedchests.permission-denied": "<red>Bạn không có quyền tạo shop trên rương AdvancedChests!",
    "suite.dynmap.description": "Vật phẩm: {0}\nChủ: {1}\nLoại: {2} {3}\nGiá: {4}\nVị trí: {5} {6}, {7}, {8}\nChỗ trống: {9}\nCòn hàng: {10}",
    "suite.limited.command-description": "<yellow>Giới hạn số lượng mỗi người được mua trong một khoảng thời gian.",
    "suite.limited.reach-the-quota-limit": "<red>Bạn đã mua hết hạn mức ở shop này ({0}/{1}).",
    "suite.limited.quota-reset-countdown": "<yellow>Hạn mức ở shop này làm mới sau {0}.",
    "suite.limited.quota-reset-player-successfully": "<green>Đã làm mới hạn mức của {0} ở shop này.",
    "suite.limited.quota-reset-everybody-successfully": "<green>Đã làm mới hạn mức của mọi người ở shop này.",
    "suite.limited.quota-setup": "<green>Đã áp giới hạn mua cho shop này!",
    "suite.limited.quota-remove": "<green>Đã bỏ giới hạn mua của shop này!",
    "suite.limited.subtitles.title": "<green>Mua thành công",
    "suite.limited.subtitles.subtitle": "<aqua>Bạn còn mua được <yellow>{0}</yellow> ở shop này",
    "suite.list.command-description": "<yellow>Liệt kê shop của bạn hoặc của người khác.",
    "suite.list.table-prefix": "<yellow>Bạn có tổng cộng <aqua>{0}</aqua> shop trên server.",
    "suite.list.entry-syntax-fallback-name": "<yellow>Vật phẩm:{0} X:{1}, Y:{2}, Z:{3}, Thế giới: {4}",
    "suite.shopitemonly.message": "<red>Không bỏ được đồ không phải hàng của shop vào rương shop, đồ đó sẽ rơi ra chỗ bạn đứng.",
    "compatibility.elitemobs.soulbound-disallowed": "Không mua bán được đồ có phù phép Soulbound của EliteMobs.",
    "internet-paste-forbidden-privacy-reason": "<red>Không được! Theo cài đặt riêng tư, QuickShop-Hikari không được tải bản ghi lên Internet. Bật quyền DIAGNOSTIC trong cài đặt riêng tư ở config.yml hoặc dùng <aqua>/quickshop paste --file</aqua>.",
    "no-sign-type-given": "<red>Phải ghi loại biển; các loại biển có trên server: {0}",
    "sign-type-invalid": "<red><yellow>{0}</yellow> không phải loại biển hợp lệ.",
    "delete-controlpanel-button-confirm": "<red>Thật sự muốn xoá shop này? Bấm lại nút <bold>[Xoá shop]</bold> trong {0} giây để xác nhận.",
    "cannot-suggest-price": "<red>Hiện chưa có ai bán cùng mặt hàng nên chưa đủ dữ liệu để gợi ý giá.",
    "price-suggest": "<green>Theo dữ liệu từ <aqua>{0}</aqua> shop: giá cao nhất <light_purple>{1}</light_purple>, thấp nhất <light_purple>{2}</light_purple>, trung bình <light_purple>{3}</light_purple>, trung vị <light_purple>{4}</light_purple>. <newline><yellow>Nên đặt giá quanh <gold>{5}</gold>.</yellow>",
    "price-suggest-multi": "<green>Gợi ý cho loại {0}.</green>{1}",
    "suggest-wait": "<green>Đợi chút... Đang tính giá gợi ý.",
    "gui.trade.title": "<dark_aqua>Shop</dark_aqua>",
    "gui.trade.info-stock.display": "<yellow>Hàng còn</yellow>",
    "gui.trade.info-stock.lore": "<gray>Còn: <white>{0}</white></gray>",
    "gui.trade.info-price.display": "<gold>Giá</gold>",
    "gui.trade.info-price.lore": "<gray>Giá: <white>{0}</white></gray>\n<gray>Cho: <white>{1} món</white></gray>",
    "gui.trade.info-seller.display": "<aqua>Người bán</aqua>",
    "gui.trade.info-seller.lore": "<gray>Chủ: <white>{0}</white></gray>",
    "gui.trade.custom-amount.display": "<bold><blue>Tự nhập số lượng</blue></bold>",
    "gui.trade.quantity-button.display-buy": "<green>Mua x{0}</green>",
    "gui.trade.quantity-button.display-sell": "<gold>Bán x{0}</gold>",
    "gui.keeper.title": "<dark_aqua>Quản lý shop</dark_aqua>",
    "gui.keeper.change-price.display": "<bold><green>Đổi giá</green></bold>",
    "gui.keeper.change-price.lore": "<gray>Giá hiện tại: <white>{0}</white></gray>\n\n<yellow>Bấm để đổi giá</yellow>",
    "gui.keeper.display-toggle.display": "<bold><green>Bật/tắt trưng bày</green></bold>",
    "gui.keeper.display-toggle.lore": "<gray>Bật/tắt vật phẩm trưng bày trên shop</gray>\n<gray>Trạng thái: <white>{0}</white>. Bấm để đổi.</gray>",
    "gui.keeper.freeze-toggle.display": "<bold><green>Đóng băng / mở lại</green></bold>",
    "gui.keeper.freeze-toggle.lore": "<gray>Hiện đang <white>{0}</white></gray>\n<gray>Bấm để <white>{1}</white></gray>",
    "gui.keeper.mode-toggle.display": "<bold><green>Đổi chế độ</green></bold>",
    "gui.keeper.mode-toggle.lore": "<gray>Hiện tại: <white>{0}</white></gray>\n<gray>Bấm để đổi sang: <white>{1}</white></gray>",
    "gui.keeper.inventory.display": "<bold><green>Xem kho</green></bold>",
    "gui.keeper.inventory.lore": "<gray>Bấm để xem</gray>\n<gray>rương hàng của shop.</gray>",
    "gui.keeper.staff.display": "<bold><aqua>Nhân viên</aqua></bold>",
    "gui.keeper.staff.lore": "<gray>Bấm để quản lý</gray>\n<gray>nhân viên của shop.</gray>",
    "gui.keeper.history.display": "<bold><light_purple>Lịch sử giao dịch</light_purple></bold>",
    "gui.keeper.history.lore": "<gray>Bấm để xem</gray>\n<gray>lịch sử giao dịch của shop.</gray>",
    "gui.keeper.remove.display": "<bold><red>Xoá shop</red></bold>",
    "gui.keeper.remove.lore": "<gray>Bấm để xoá shop.</gray>\n<dark_gray>Phá biển/rương cũng được.</dark_gray>",
    "gui.staff.title": "<dark_aqua>Quản lý nhân viên</dark_aqua>",
    "gui.staff.search.display": "<yellow>Tìm: {0}</yellow>",
    "gui.staff.search.lore": "<gray>Chuột trái để tìm người chơi</gray>\n<gray>Chuột phải để xoá tìm kiếm</gray>",
    "gui.staff.add-staff.display": "<green>Thêm nhân viên</green>",
    "gui.staff.add-staff.lore": "<gray>Bấm để thêm</gray>\n<gray>nhân viên cho shop.</gray>",
    "gui.staff.back.display": "<white>Về shop</white>",
    "gui.browse.title": "<dark_aqua>Chợ</dark_aqua>",
    "gui.browse.shop-list.buying-label": "<#FFA500>Thu mua</#FFA500>",
    "gui.browse.shop-list.selling-label": "<green>Đang bán</green>",
    "gui.browse.shop-list.price-indicator.average": "● Trung bình",
    "gui.browse.shop-list.price-indicator.selling.great": "▼▼ Rất rẻ!",
    "gui.browse.shop-list.price-indicator.selling.good": "▼ Rẻ hơn trung bình",
    "gui.browse.shop-list.price-indicator.selling.bad": "▲▲ Đắt",
    "gui.browse.shop-list.price-indicator.selling.worst": "▲ Đắt hơn trung bình",
    "gui.browse.shop-list.price-indicator.buying.great": "▲▲ Giá thu rất tốt!",
    "gui.browse.shop-list.price-indicator.buying.good": "▲ Cao hơn trung bình",
    "gui.browse.shop-list.price-indicator.buying.bad": "▼▼ Thu giá thấp",
    "gui.browse.shop-list.price-indicator.buying.worst": "▼▼ Thấp hơn trung bình",
    "gui.browse.shop-list.lore": "<gray>Chủ: <white>{0}</white></gray>\n<gray>Loại: {1}</gray>\n<gray>Giá: {2}</gray>\n<gray><aqua>{3}</aqua></gray>\n<gray>Vị trí: <white>{4}</white></gray>\n<dark_gray>{5}</dark_gray>",
    "gui.browse.shop-list.teleport": "<newline><yellow>Bấm để dịch chuyển</yellow>",
    "gui.browse.shop-item.lore": "<green>Chủ: </green><yellow>{0}</yellow>\n<green>Vị trí: </green><yellow>{1}</yellow>\n<green>Loại: </green><yellow>{2}</yellow>\n<green>Giá: </green><yellow>{3}</yellow>\n<green>Còn hàng: </green><yellow>{4}</yellow>",
    "gui.browse.grouped-item.lore-title": "<gray>Số shop: <white>{0}</white></gray>",
    "gui.browse.grouped-item.lore-selling": "<green>▼ Đang bán ({0} shop)</green>\n<gray> Giá: <white>{1} - {2}</white></gray>\n<gray> Trung bình: <yellow>{3}</yellow></gray>\n<gray> Trung vị: <gold>{4}</gold></gray>\n<gray> Còn hàng: <aqua>{5}</aqua></gray>",
    "gui.browse.grouped-item.lore-buying": "<#FFA500>▲ Thu mua ({0} shop)</#FFA500>\n<gray> Giá: <white>{1} - {2}</white></gray>\n<gray> Trung bình: <yellow>{3}</yellow></gray>\n<gray> Trung vị: <gold>{4}</gold></gray>",
    "gui.browse.grouped-item.lore-footer": "<yellow>Bấm để xem mọi shop</yellow>",
    "gui.browse.search.display": "<yellow>Tìm: {0}</yellow>",
    "gui.browse.search.lore": "<gray>Chuột trái để tìm vật phẩm</gray>\n<gray>Chuột phải để xoá tìm kiếm</gray>",
    "gui.browse.search-item.lore": "<gray>Đang xem: {0}</gray>\n<gray>Số shop: <white>{1}</white></gray>\n<gray>Giá trung bình: <gold>{2}</gold></gray>",
    "gui.browse.sort.display": "<green>Sắp xếp: {0}</green>", "gui.browse.sort.lore": "<gray>Bấm để đổi</gray>",
    "gui.browse.sort.price-asc": "Giá ↑", "gui.browse.sort.price-desc": "Giá ↓", "gui.browse.sort.stock": "Còn hàng", "gui.browse.sort.name": "Tên",
    "gui.browse.filter.display": "<aqua>Lọc: {0}</aqua>", "gui.browse.filter.lore": "<gray>Bấm để đổi</gray>",
    "gui.browse.filter.all": "Tất cả", "gui.browse.filter.selling": "Đang bán", "gui.browse.filter.buying": "Thu mua",
    "gui.browse.filter.all-indicator": "<white>Mọi shop</white>", "gui.browse.filter.selling-indicator": "<green>Shop bán</green>",
    "gui.browse.filter.buying-indicator": "<#FFA500>Shop thu mua</#FFA500>",
    "gui.browse.stock-filter.display": "<gold>Chỉ còn hàng: {0}</gold>", "gui.browse.stock-filter.lore": "<gray>Bấm để bật/tắt</gray>",
    "gui.browse.back.display": "<white>Về chợ</white>",
    "gui.shared.next-page.display": "<white>Trang sau >></white>", "gui.shared.page-info.display": "<yellow>Trang {0}/{1}</yellow>",
    "gui.shared.back.display": "<white>Quay lại</white>", "gui.shared.close.display": "<red>Đóng</red>",
    "gui.messages.keeper.enter-price": "<yellow>Nhập giá mới vào chat.</yellow>",
    "gui.messages.keeper.confirm-delete": "<yellow>Gõ '<white>confirm</white>' vào chat để xoá shop. Gõ chữ khác để huỷ.</yellow>",
    "gui.messages.staff.enter-search": "<yellow>Nhập tên người chơi để tìm. Gõ '<white>clear</white>' để xoá, hoặc chuột phải nút tìm.</yellow>",
    "gui.messages.staff.confirm-remove": "<yellow>Gõ '<white>confirm</white>' để bỏ <red>{0}</red> khỏi nhân viên. Gõ chữ khác để huỷ.</yellow>",
    "gui.messages.staff.confirm-transfer": "<yellow>Gõ '<white>confirm</white>' để chuyển quyền chủ cho <green>{0}</green>. Gõ chữ khác để huỷ.</yellow>",
    "gui.messages.trade.enter-buy": "<yellow>Nhập số lượng muốn mua, phải là bội số của <white>{0}</white>. Nhập <white>0</white> để huỷ.</yellow>",
    "gui.messages.trade.enter-sell": "<yellow>Nhập số lượng muốn bán, phải là bội số của <white>{0}</white>. Nhập <white>0</white> để huỷ.</yellow>",
    "gui.messages.trade.invalid-multiple": "<red>Số lượng không hợp lệ! Phải là bội số của <white>{0}</white>. Nhập <white>0</white> để huỷ.</red>",
    "gui.messages.trade.invalid-stock": "<red>Số lượng không hợp lệ! Không đủ hàng. Nhập <white>0</white> để huỷ.</red>",
    "gui.messages.browse.enter-search": "<yellow>Nhập tên vật phẩm để tìm. Gõ '<white>clear</white>' để xoá, hoặc chuột phải nút tìm.</yellow>",
    "history.shop.gui-title": "Lịch sử mua bán",
    "history.shop.header-icon-multiple-shop": "<white>Kết quả của {0} shop</white>",
    "history.shop.header-icon-shop-name": "<yellow>Shop {0}</yellow>",
    "history.shop.header-icon-shop-empty-name": "<yellow>Shop ở thế giới '{0}' tại x: {1}, y: {2}, z: {3}</yellow>",
    "history.shop.header-icon-description": "<white>Loại: <yellow>{0}</yellow></white>\n<white>Chủ: <yellow>{1}</yellow></white>\n<white>Vật phẩm: <yellow>{2}</yellow></white>\n<white>Giá: <yellow>{3} <aqua>x{4}</aqua></yellow></white>\n<white>Vị trí: <yellow>{5}</yellow></white>",
    "history.shop.log-icon-title": "<reset><green>Thời gian: {0}</green>",
    "history.shop.log-icon-description": "<white>Người mua: <yellow>{0}</yellow></white>\n<white>Vật phẩm: <yellow>{1} <aqua>x{2}</aqua></yellow></white>\n<white>Số dư: <yellow>{3}</yellow></white>\n<white>Thuế: <yellow>{4}</yellow></white>",
    "history.shop.log-icon-description-with-store-name": "<white>Shop: <yellow>{0}</yellow></white>\n<white>Người mua: <yellow>{1}</yellow></white>\n<white>Vật phẩm: <yellow>{2} <aqua>x{3}</aqua></yellow></white>\n<white>Số lượng: <yellow>{4}</yellow></white>\n<white>Tổng: <yellow>{5}</yellow></white>\n<white>Thuế: <yellow>{5}</yellow></white>\n<white>Thực nhận: <yellow>{6}</yellow></white>",
    "history.shop.query-icon": "<gray>Đợi chút, đang tra...</gray>",
    "history.shop.current-page": "<white>Trang {0}</white>",
    "history.shop.multi-shops-icon-title": "<yellow>Nhiều shop</yellow>",
    "history.shop.summary-icon-title": "<yellow>Tổng hợp</yellow>",
    "history.shop.total-unique-purchasers": "<white>Số người mua khác nhau: <yellow>{0}</yellow></white>",
    "history.shop.top-n-valuable-customers-title": "<gold>Top {0} khách hàng</gold>",
    "history.shop.no-result": "<white>Không có kết quả</white>",
    "about.valid-donation-key": "<color:#00AFF1>Gắn với <color:#F9C23C>{0}</color></color>",
    "about.invalid-donation-key": "<gray>Mã ủng hộ không hợp lệ</gray>",
    "about.kofi-thanks": "<gold>Cảm ơn những người ủng hộ QuickShop trên Ko-fi :)</gold>",
    "shop-information-not-shown-due-an-internal-error": "<red>Có lỗi nội bộ, bảng thông tin shop có thể hiện thiếu. Vui lòng báo admin.",
}
# Câu dài nhiều thẻ: chỉ thay chữ, giữ nguyên thẻ.
FRAG = {
    "pagination.footer": [("Previous page", "Trang trước"), ("Next page", "Trang sau")],
    "tags.general.list-entry": [("View all shops with this tag", "Xem mọi shop có nhãn này"), ("View Shops", "Xem shop"),
                                ("Remove this tag from this shop", "Gỡ nhãn này khỏi shop"), (">Remove<", ">Gỡ<")],
    "tags.tag.shops-entry": [("Price ", "Giá "), ("View all tags on this shop", "Xem mọi nhãn của shop"), ("View {10} tags", "Xem {10} nhãn")],
    "tags.tag.entry": [("Price ", "Giá "), ("Remove this tag from this shop", "Gỡ nhãn này khỏi shop"), ("Remove Tag", "Gỡ nhãn")],
    "history.shop.summary-icon-description": [("Total unique purchasers", "Số người mua khác nhau"), ("Total purchases", "Tổng lượt mua"),
                                              ("Total turnover", "Tổng doanh thu"), (" purchases:", " lượt mua:"), (" turnover:", " doanh thu:"),
                                              ("Recent ", "Gần đây ")],
}


def flat(d, p=""):
    o = {}
    for k, v in (d or {}).items():
        key = f"{p}.{k}" if p else str(k)
        if isinstance(v, dict):
            o.update(flat(v, key))
        else:
            o[key] = v
    return o


def nest(items):
    root = {}
    for k, v in items.items():
        node, parts = root, k.split(".")
        for p in parts[:-1]:
            node = node.setdefault(p, {})
        node[parts[-1]] = v
    return root


en = flat(yaml.safe_load(open(EN, encoding="utf-8")))
lists = {k for k, v in en.items() if isinstance(v, list)}   # câu nhiều dòng lưu dạng danh sách: so/ghép bằng \n rồi tách lại
en = {k: "\n".join(map(str, v)) if isinstance(v, list) else v for k, v in en.items()}
out = dict(T)
for k, reps in FRAG.items():
    v = en[k]
    for a, b in reps:
        assert a in v, (k, a)
        v = v.replace(a, b)
    out[k] = v
bad = [k for k in out if k not in en]
ph = [k for k in out if k in en and sorted(re.findall(r"\{\d+\}", str(en[k]))) != sorted(re.findall(r"\{\d+\}", str(out[k])))]
with open(OUT, "w", encoding="utf-8") as f:
    f.write("# DuckyMoMo: ghi đè tiếng Việt cho QuickShop-Hikari (chỉ khoá đã dịch; sinh bằng tools/vi/quickshop.py)\n")
    yaml.dump(nest({k: v.split("\n") if k in lists else v for k, v in out.items()}), f, allow_unicode=True, sort_keys=False, width=10000)
print(f"ghi {len(out)} câu; khoá không có trong bản gốc: {bad}; lệch tham số: {ph}")
