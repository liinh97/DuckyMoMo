FROM python:3.13-alpine
# ruamel.yaml: đọc/ghi YAML mà giữ nguyên comment của file cấu hình plugin
# pymysql: trang Role đọc nhóm / thành viên LuckPerms trong MariaDB
# segno: tự vẽ mã QR VietQR (PNG) cho ủng hộ, không phụ thuộc img.vietqr.io (20/10 đã đổi sang SVG)
# pywebpush: thông báo đẩy về điện thoại (PWA, Web Push VAPID), 29/10
RUN pip install --no-cache-dir --root-user-action=ignore "ruamel.yaml<1" "pymysql<2" "segno<2" "pywebpush<3"
WORKDIR /app
COPY app.py taikhoan.py vang.py thongbao.py thongbao.html sw.js manifest.webmanifest icon-192.png icon-512.png apple-touch-icon.png index.html activity.html camnang.html huongdan.html role.html thuvien.html boss.html donate.html hethong.html media.html bot.html ui.css shell.js ./
# Cùng uid với user minecraft trong image itzg, để file cấu hình ghi ra không đổi chủ
USER 1000:1000
CMD ["python", "-u", "app.py"]
