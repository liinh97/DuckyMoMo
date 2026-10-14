FROM python:3.13-alpine
# Trang giới thiệu công khai + API chỉ đọc (site/app.py). PyYAML: đọc cấu hình plugin.
RUN pip install --no-cache-dir --root-user-action=ignore "pyyaml<7"
WORKDIR /app
COPY app.py ./
COPY config ./config
USER 1000:1000
CMD ["python", "-u", "app.py"]
