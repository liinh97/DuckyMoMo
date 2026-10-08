FROM python:3.13-alpine
# ruamel.yaml: đọc/ghi YAML mà giữ nguyên comment của file cấu hình plugin
RUN pip install --no-cache-dir --root-user-action=ignore "ruamel.yaml<1"
WORKDIR /app
COPY app.py index.html ./
# Cùng uid với user minecraft trong image itzg, để file cấu hình ghi ra không đổi chủ
USER 1000:1000
CMD ["python", "-u", "app.py"]
