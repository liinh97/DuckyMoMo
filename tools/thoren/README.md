# Sinh món Thợ Rèn vào Thư viện đồ

Các set đồ và nguyên liệu của Thợ Rèn (/thoren) là món trong Thư viện đồ (`data/banghoi-1/plugins/DuckyMoMoCore/thuvien-do.yml`,
không commit). Dựng lại trên máy mới (chạy trong container panel vì cần ruamel, rồi nạp lại thư viện):

```bash
docker exec -i duckymomo-panel-1 python3 - < tools/thoren/gen_manh.py
docker exec -i duckymomo-panel-1 python3 - < tools/thoren/gen_sets.py
docker exec -i duckymomo-panel-1 python3 - < tools/thoren/gen_tanthu.py
docker exec duckymomo-banghoi-1-1 rcon-cli "dmm thuvien nap"
```

- `gen_manh.py`: 13 mảnh + 6 tinh thể (quầy đổi ở Kinh Đô).
- `gen_sets.py`: Chiến Binh, Tinh Anh, Cao Cấp, Thợ Mỏ, Ngư Phủ, Du Hiệp + 9 mảnh ấn dungeon + Vảy Cá Hiếm.
- `gen_tanthu.py`: mũ/quần/giày/kiếm Tân Thủ theo khung áo giáp Tân Thủ (áo `giap`, `giap_tan_thu_*` do chủ server tự làm trên web,
  script đọc khung từ đó nên phải có áo trước).
Giá, độ mòn, hiệu ứng bộ, quầy, rơi mảnh: `tho-ren` trong config DuckyMoMoCore.
