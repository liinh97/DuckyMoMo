"""CMILib (thư viện của Jobs Reborn) tiếng Việt, 21/10/2026: nút chuyển trang, Quay lại, báo lỗi chung trong menu Jobs.
Chạy ở thư mục gốc repo:  python3 tools/jobs/cmilib-vi.py
  - đọc bản tiếng Anh tools/jobs/goc/Locale_EN.yml + config.yml mặc định tools/jobs/goc/cmilib-config.yml
  - ghi modes/banghoi/server/plugins/CMILib/Translations/Locale_VI.yml và CMILib/config.yml (Language: VI, không tự tải ngôn ngữ khác)
"""
import re
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
GOC = Path(__file__).resolve().parent / "goc"
OUT = ROOT / "modes/banghoi/server/plugins/CMILib"

VI = {
    "info.NoPermission": "{gcw}Bạn không có quyền!",
    "info.Ingame": "{gcw}Chỉ dùng được trong game!",
    "info.NoInformation": "{gcw}Không có thông tin!",
    "info.Console": "{gcp}Máy chủ",
    "info.NotOnline": "{gcw}Người chơi không online!",
    "info.NobodyOnline": "{gcw}Không có ai online!",
    "info.NoPlayer": "{gcw}Không tìm thấy người chơi tên này! ({gcs}[name]{gcw})",
    "info.NoCommand": "{gcw}Không có lệnh này!",
    "info.FeatureNotEnabled": "{gcw}Tính năng này chưa bật!",
    "info.clickToTeleport": "{gcp}Bấm để dịch chuyển",
    "info.UseInteger": "{gcd}Hãy nhập số!",
    "info.NoLessThan": "{gcd}Số không được nhỏ hơn {gcs}[amount]{gcd}!",
    "info.NoMoreThan": "{gcd}Không được lớn hơn {gcs}[amount]",
    "info.Show": "{gcp}Xem",
    "info.Remove": "{gcd}Xoá",
    "info.Back": "{gcp}Quay lại",
    "info.Forward": "{gcp}Tiếp",
    "info.Update": "{gcp}Cập nhật",
    "info.Save": "{gcp}Lưu",
    "info.Delete": "{gcd}Xoá",
    "info.Click": "{gcp}Bấm",
    "info.Preview": "{gcp}Xem trước",
    "info.ClickToConfirmDelete": "{gcp}Bấm để xác nhận xoá {gcs}[name]",
    "info.ClickToConfirm": "{gcp}Bấm để xác nhận",
    "info.teleported": "{gcp}Đã dịch chuyển.",
    "info.nothingInHand": "!actionbar!{gcp}Bạn cần cầm một món trên tay",
    "info.nothingToShow": "{gcp}Không có gì để xem",
    "info.noItem": "{gcw}Không tìm thấy món",
    "info.dontHaveItem": "{gcw}Trong túi bạn không có {gcs}[amount]x [itemName]",
    "info.wrongWorld": "{gcw}Không làm được ở thế giới này",
    "info.HaveItem": "{gcw}Trong túi bạn có {gcs}[amount]x [itemName]",
    "info.cantDoInGamemode": "{gcp}Không làm được ở chế độ chơi này",
    "info.cantDoForYourSelf": "{gcp}Không làm được với chính mình",
    "info.unknown": "không rõ",
    "info.prevPage": "{gcn}----<< {gcp}Trước ",
    "info.prevPageGui": "{gcp}Trang trước ",
    "info.prevPageClean": "{gcp}Trước ",
    "info.prevPageOff": "{gcn}----<< {gcp}Trước ",
    "info.nextPage": "{gcp} Sau {gcn}>>----",
    "info.nextPageGui": "{gcp}Trang sau",
    "info.nextPageClean": "{gcp} Sau",
    "info.nextPageOff": "{gcp} Sau {gcn}>>----",
    "info.nextPageConsole": "{gcn}Trang sau: gõ {gcs}[command]",
}


def main():
    loc = yaml.safe_load((GOC / "Locale_EN.yml").read_text(encoding="utf-8"))
    miss = []
    for path, text in VI.items():
        node = loc
        ks = path.split(".")
        for k in ks[:-1]:
            node = node.get(k, {})
        if ks[-1] in node:
            node[ks[-1]] = text
        else:
            miss.append(path)
    (OUT / "Translations").mkdir(parents=True, exist_ok=True)
    (OUT / "Translations/Locale_VI.yml").write_text("# DuckyMoMo 21/10: bản tiếng Anh CMILib + các dòng hay gặp trong menu Jobs đã Việt hoá (tools/jobs/cmilib-vi.py).\n"
                                                    + yaml.safe_dump(loc, allow_unicode=True, sort_keys=False, width=200), encoding="utf-8")
    c = (GOC / "cmilib-config.yml").read_text(encoding="utf-8")
    c2 = re.sub(r"^Language: EN$", "Language: VI", c, count=1, flags=re.M)
    c2 = re.sub(r"^LanguageDownload: true$", "LanguageDownload: false", c2, count=1, flags=re.M)
    assert c2 != c
    (OUT / "config.yml").write_text(c2, encoding="utf-8")
    print("ok, thiếu khoá:", miss)


if __name__ == "__main__":
    main()
