#!/bin/bash
# Chép cẩm nang trong game (modes/_network/huongdan.yml) sang lobby, banghoi, practice: vào thư mục cấu hình trong repo
# (để lần khởi động sau vẫn đúng) và vào data/ (có hiệu lực ngay: /huongdan đọc lại file mỗi lần mở).
# Chạy từ thư mục gốc repo:  ./ops/huongdan.sh
set -euo pipefail
cd "$(dirname "$0")/.."
SRC=modes/_network/huongdan.yml
python3 -c "import yaml,sys; d=yaml.safe_load(open('$SRC')); print(len(d['chapters']), 'chương,', sum(len(c['pages']) for c in d['chapters']), 'trang')"
for d in lobby/plugins modes/banghoi/server/plugins modes/practice/server/plugins; do
  mkdir -p "$d/DuckyMoMoCore"; cp "$SRC" "$d/DuckyMoMoCore/huongdan.yml"
done
for s in lobby banghoi-1 practice-1; do
  [ -d "data/$s/plugins/DuckyMoMoCore" ] && cp "$SRC" "data/$s/plugins/DuckyMoMoCore/huongdan.yml"
done
echo "Đã chép sang lobby, banghoi, practice."
