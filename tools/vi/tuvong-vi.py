#!/usr/bin/env python3
"""Tạo network-core/core-paper/src/main/resources/vi_tuvong.json: bản tiếng Việt chính thức (vi_vn.json của Mojang) cho
tin tử vong + tên thực thể/vật phẩm/khối, để DeathVi.java dịch sẵn tin tử vong ở server.
Dùng: python3 tools/vi/tuvong-vi.py [phiên bản, mặc định 26.2]"""
import json, os, sys, urllib.request

ver = sys.argv[1] if len(sys.argv) > 1 else '26.2'
man = json.load(urllib.request.urlopen('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'))
v = [x for x in man['versions'] if x['id'] == ver][0]
ai = json.load(urllib.request.urlopen(json.load(urllib.request.urlopen(v['url']))['assetIndex']['url']))
h = ai['objects']['minecraft/lang/vi_vn.json']['hash']
d = json.load(urllib.request.urlopen(f'https://resources.download.minecraft.net/{h[:2]}/{h}'))
keep = {k: s for k, s in d.items()
        if k.startswith('death.') or (k.startswith(('entity.minecraft.', 'item.minecraft.', 'block.minecraft.')) and k.count('.') <= 2)}
out = os.path.join(os.path.dirname(__file__), '..', '..', 'network-core', 'core-paper', 'src', 'main', 'resources', 'vi_tuvong.json')
with open(out, 'w', encoding='utf-8') as f:
    json.dump(keep, f, ensure_ascii=False, separators=(',', ':'), sort_keys=True)
print(ver, len(keep), 'khoá ->', os.path.normpath(out))
