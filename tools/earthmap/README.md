# Tạo map Trái Đất (Bang Hội Chiến)

Map Trái Đất tự tạo, miễn phí, dùng được cho server có thu tiền (chỉ cần **ghi nguồn**, xem cuối file).
Công cụ: [WorldPainter](https://www.worldpainter.net/) (GPL-3.0) + script
[DerMattinger/MinecraftEarthMap](https://github.com/DerMattinger/MinecraftEarthMap) (MIT, cũng là nguồn của earth.motfe.net).

## Tỉ lệ

| `scale` | Tỉ lệ | Kích thước (block) | RAM / thời gian / dung lượng |
|---|---|---|---|
| 10 | 1:4000 | 10.752 × 5.376 | đo thực tế: chạy được với 3 GB, 13 phút, ra 1 GB |
| **20** | **1:2000** | **21.504 × 10.752** | đo thực tế: **6 GB thiếu**, 8 GB chạy được, **61 phút**, ra **4,2 GB** (đang dùng cho banghoi-1) |
| 40 | 1:1000 | 43.008 × 21.504 | tác giả đo ~19 GB trên bản 1.12, thực tế chắc nhiều hơn; máy hiện tại không đủ |

Khi tạo 1:2000 phải tắt cả network DuckyMoMo (`docker compose --profile tunnel stop`) để WSL đủ RAM. Đã kiểm tra map 1:4000 trên Paper 26.2: nạp không lỗi, biome đúng địa lý (Hà Nội rừng nhiệt đới, Sahara sa mạc, Greenland băng), dưới đất có đủ quặng (cả kim cương), deepslate, hang động.

Sửa so với script gốc (`build-earth.sh` tự làm): biome 189 → 168 (bamboo_jungle), định dạng `JAVA_ANVIL_26_1`, độ cao -64..320, thêm bước xuất. Địa hình khá thấp (Everest ~y 104 ở 1:4000) vì script map độ cao vào 0..255; muốn núi cao hơn phải chỉnh script (chưa thử).

## Chuẩn bị (một lần)
Công cụ và dữ liệu nằm trong `tools/earthmap/work/` (không commit, ~560 MB):
```bash
cd tools/earthmap && mkdir -p work && cd work
curl -fLO https://www.worldpainter.net/files/worldpainter_2.27.1.tar.gz
# đối chiếu với https://www.worldpainter.net/files/sha256sums
echo "c3eb13d42e1e7a27554cdc92e6af2d9c742043e7eb2522f4f80fff5db6d835c6  worldpainter_2.27.1.tar.gz" | sha256sum -c -
tar -xzf worldpainter_2.27.1.tar.gz
git clone --depth 1 https://github.com/DerMattinger/MinecraftEarthMap.git
```
WorldPainter cần bản **2.27 trở lên** để xuất được định dạng Minecraft 26.x.

## Tạo map
```bash
./tools/earthmap/build-earth.sh 20        # XMX=... để chỉnh RAM tối đa (mặc định 9g cho scale 20)
```
Script tạo `work/world-generated.js` từ `world.js` gốc (đổi đường dẫn, tỉ lệ, định dạng 26.x, độ cao -64..320, thêm bước xuất),
rồi chạy WorldPainter không giao diện trong Docker. Kết quả: `work/out/earth_1-2000/`.

## Lắp vào server
1. `docker compose stop banghoi-1`, đổi tên `data/banghoi-1/world` để giữ lại, chép `work/out/earth_1-2000` thành `data/banghoi-1/world` (xoá `session.lock`).
2. `docker compose up -d banghoi-1`, rồi trang Cài đặt bấm **Áp dụng lại tất cả**: viền chữ nhật ChunkyBorder 10752 × 5376, spawn.
3. Toạ độ 1:2000: tâm 0,0 = kinh độ 0, vĩ độ 0; X = kinh độ × 59,73; Z = −vĩ độ × 59,73.

## Ghi nguồn (bắt buộc khi dùng map)
Hiện ở spawn và trang web của server, ví dụ:

> Map Trái Đất tạo bằng WorldPainter và MinecraftEarthMap (DerMattinger, MIT). Dữ liệu: NASA Visible Earth (độ cao, đáy biển),
> ESA GlobCover, bản đồ khí hậu Köppen-Geiger (Beck et al.), NASA SVS (nhiệt độ mặt biển), Natural Earth / shadedrelief.com,
> USGS Major Mineral Deposits, © OpenStreetMap contributors.

Nguồn gốc từng tập dữ liệu: `work/MinecraftEarthMap/README.md`, mục "Sources".
**Chưa kiểm tra kỹ** điều khoản riêng của từng tập dữ liệu (đa số là dữ liệu công khai của NASA/USGS/ESA;
OpenStreetMap theo ODbL, yêu cầu ghi "© OpenStreetMap contributors"). Đọc lại trước khi mở công khai.
