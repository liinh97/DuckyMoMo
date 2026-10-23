#!/usr/bin/env python3
# Báo cáo cân bằng (23/10, chủ server: "theo dõi tốc độ phát triển của member xem còn cân đối"). Chỉ ĐỌC dữ liệu Bang Hội Chiến:
#   giờ chơi thật (DuckyMoMoCore/hoatdong.yml, phút), xu (Essentials userdata), Thăng Cấp (capdo.json), đồ set đã rèn (log "Thợ Rèn: <tên> <mã> (+N)").
# So với mốc thiết kế: 1 giờ chơi chăm ~1.400 xu (docs/dinh-gia-xu.html); bậc set nên tới sau bao nhiêu giờ (MOC_SET).
# Chạy:  python3 tools/canbang/baocao.py            (từ thư mục DuckyMoMo, trên máy chạy server)
import glob, gzip, json, os, re, statistics, sys
import yaml

R = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
D = os.path.join(R, 'data', 'banghoi-1')
XU_GIO = 1400          # xu / giờ chơi chăm (mốc định giá 21/10)
TIEN_DAU = 500         # tiền khởi đầu
# bậc set (tiền tố mã món) -> (tên, số giờ chơi thật tối thiểu hợp lý để có món +0 của bậc đó)
MOC_SET = {'tt': ('Tân Thủ', 0), 'cb': ('Chiến Binh', 25), 'ta': ('Tinh Anh', 60), 'cc': ('Cao Cấp', 110), 'ht': ('Huyền Thoại', 180),
           'mt': ('Thần Thoại', 300), 'dv': ('Thần Thánh', 450), 'ul': ('U Linh', 0), 'tm': ('Thợ Mỏ', 0), 'np': ('Ngư Phủ', 0),
           'dh': ('Du Hiệp', 0), 'nn': ('Nông Gia', 0), 'ts': ('Thợ Săn', 0)}
THU_TU = ['tt', 'cb', 'ta', 'cc', 'ht', 'mt', 'dv']
BO_QUA = re.compile(r'^(Thu[A-Z]|town-|nation-|tax$|\.?test$)')


def load_yaml(p):
    try:
        return yaml.safe_load(open(p, encoding='utf-8')) or {}
    except OSError:
        return {}


hd = load_yaml(os.path.join(D, 'plugins/DuckyMoMoCore/hoatdong.yml'))
cap = (json.load(open(os.path.join(D, 'plugins/DuckyMoMoCore/capdo.json'))).get('nguoi') or {}) if os.path.exists(os.path.join(D, 'plugins/DuckyMoMoCore/capdo.json')) else {}
nguoi = {}
for f in glob.glob(os.path.join(D, 'plugins/Essentials/userdata/*.yml')):
    u = os.path.basename(f)[:-4]
    y = load_yaml(f)
    ten = str(y.get('last-account-name') or '')
    if not ten or BO_QUA.match(ten):
        continue
    try:
        xu = float(y.get('money') or 0)
    except ValueError:
        xu = 0
    gio = float((hd.get(u) or {}).get('tong') or 0) / 60
    nguoi[ten.lower()] = {'ten': ten, 'xu': xu, 'gio': gio, 'cap': (cap.get(u) or {}).get('cap', 0), 'set': {}}

# đồ set đã rèn: log hiện tại + log nén
LOG = re.compile(r'Thợ Rèn: (\S+) ([a-z]+)_([a-z]+)_(\d+) \(\+(\d+)\)')
for f in sorted(glob.glob(os.path.join(D, 'logs/*.log.gz'))) + [os.path.join(D, 'logs/latest.log')]:
    try:
        fh = gzip.open(f, 'rt', encoding='utf-8', errors='replace') if f.endswith('.gz') else open(f, encoding='utf-8', errors='replace')
        for line in fh:
            m = LOG.search(line)
            if m and m.group(1).lower() in nguoi:
                s = nguoi[m.group(1).lower()]['set']
                s[m.group(2)] = max(s.get(m.group(2), -1), int(m.group(5)))
    except OSError:
        pass

ds = sorted(nguoi.values(), key=lambda p: -p['gio'])
print(f"# Báo cáo cân bằng Bang Hội Chiến ({len(ds)} người chơi thật)\n")
if not ds:
    sys.exit()
tong = sum(p['xu'] for p in ds)
print(f"Tổng xu đang có: {tong:,.0f} · trung vị {statistics.median(p['xu'] for p in ds):,.0f} · tổng giờ chơi thật {sum(p['gio'] for p in ds):.1f}\n")
print(f"{'Người chơi':16} {'Giờ':>6} {'Xu':>10} {'Xu/giờ':>8} {'Cấp':>4}  Đồ set cao nhất")
canh = []
for p in ds:
    xg = (p['xu'] - TIEN_DAU) / p['gio'] if p['gio'] >= 1 else 0
    cao = [k for k in THU_TU if k in p['set']]
    setmo = ', '.join(f"{MOC_SET[k][0]} +{v}" for k, v in sorted(p['set'].items(), key=lambda kv: THU_TU.index(kv[0]) if kv[0] in THU_TU else 99))
    print(f"{p['ten']:16} {p['gio']:6.1f} {p['xu']:10,.0f} {xg:8,.0f} {p['cap']:4}  {setmo or '-'}")
    if p['gio'] >= 2 and xg > 2 * XU_GIO:
        canh.append(f"{p['ten']}: {xg:,.0f} xu/giờ, gấp {xg / XU_GIO:.1f} lần mốc {XU_GIO} (xem nguồn tiền: /bal, log bán, chợ đen)")
    if cao and p['gio'] < MOC_SET[cao[-1]][1]:
        canh.append(f"{p['ten']}: đã có {MOC_SET[cao[-1]][0]} sau {p['gio']:.1f} giờ (mốc hợp lý ≥ {MOC_SET[cao[-1]][1]} giờ) -> nguyên liệu / mảnh ấn rơi có thể quá dễ")
print('\n## Cảnh báo' if canh else '\nKhông có gì vượt mốc.')
for c in canh:
    print('- ' + c)
