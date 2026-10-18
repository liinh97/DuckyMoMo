FROM python:3.13-alpine
# ruamel.yaml: đọc/ghi YAML mà giữ nguyên comment của file cấu hình plugin
# pymysql: trang Role đọc nhóm / thành viên LuckPerms trong MariaDB
RUN pip install --no-cache-dir --root-user-action=ignore "ruamel.yaml<1" "pymysql<2"
WORKDIR /app
COPY app.py taikhoan.py index.html activity.html camnang.html huongdan.html role.html thuvien.html boss.html donate.html hethong.html ui.css shell.js ./
# Cùng uid với user minecraft trong image itzg, để file cấu hình ghi ra không đổi chủ
USER 1000:1000
CMD ["python", "-u", "app.py"]
