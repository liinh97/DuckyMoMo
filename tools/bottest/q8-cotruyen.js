// q8 cốt truyện chương 1 «Điềm Báo» (BetonQuest gói cotruyen/chuong1). NPC chưa đặt + bot qua Via không bấm được NPC Citizens:
// wrapper (q8.sh) thêm file tạm chuong1/thu-tam.yml (action conversation gia_lang/tho_ren/gac_cong), IO simple (chọn bằng số), xong gỡ.
// Bot ThuQ8 đi hết: nhận việc -> hạ 20 quái -> trả -> mang 16 sắt cho thợ rèn -> đào 24 quặng (ra Mảnh Sao) -> đưa Người Gác Cổng -> kết.
const { start, moveTo } = require('./h');
const N = 'ThuQ8';
(async () => {
  let t = await start(N, '10.9.3.1');
  const R = c => t.rc.cmd(c);
  const bal = async () => +((await R(`bal ${N}`)).replace(/.*:\s*/, '').replace(/[^\d.]/g, '') || 0);
  await t.sleep(4000);
  await R(`lp user ${N} permission set grim.exempt true`); await R(`gamemode survival ${N}`); await R(`eco set ${N} 1000`); await R(`minecraft:clear ${N}`);
  await R(`effect give ${N} minecraft:resistance 1200 4 true`);
  t = await moveTo(t, 13200, -2900, 'minecraft:overworld', '10.9.3.1');
  const m0 = await bal();
  const talk = async (npc, picks, label) => {
    const n0 = t.chat.length;
    await R(`bq action ${N} cotruyen>thu_${npc}`); await t.sleep(1800);
    for (const k of picks) { t.bot.chat(String(k)); await t.sleep(1800); }
    await t.sleep(800);
    t.log(`q8 ${label}: ${t.chat.slice(n0).filter(l => !/^\s*$/.test(l)).join(' | ').replace(/\s+/g, ' ').slice(0, 700)}`);
  };
  const tags = async () => (await R(`bq tag ${N} list`)).replace(/\s+/g, ' ').replace(/cotruyen>/g, '').slice(0, 200);
  await talk('gia_lang', [1, 1, 1], 'Già Làng lần 1 (nhận việc)');
  t.log('  tag:', await tags());
  // bước 1: hạ 20 quái
  const n1 = t.chat.length;
  // BetonQuest mobkill KHÔNG tính quái chết bằng /damage ... by (đã thử: 20 con chỉ ghi 2) -> bot chém thật quái 1 máu tới khi xong
  await R(`minecraft:item replace entity ${N} weapon.mainhand with minecraft:netherite_sword`); t.bot.setQuickBarSlot(0);
  let kills = 0;
  for (let i = 0; i < 60 && !/c1_b1x/.test(await tags()); i++) {
    await R(`execute at ${N} run summon zombie ~1.5 ~ ~ {NoAI:1b,Tags:["thuq8"],Health:1f}`); await t.sleep(700);
    const z = t.bot.nearestEntity(e => e.name === 'zombie');
    if (z) { await t.bot.lookAt(z.position.offset(0, 1, 0), true); t.bot.attack(z); kills++; }
    await t.sleep(700); await R(`execute at ${N} run minecraft:kill @e[tag=thuq8]`);
  }
  t.log('  số lần chém:', kills);
  await t.sleep(1500); await R(`execute at ${N} run minecraft:kill @e[type=item,distance=..10]`);
  t.log('  sau 20 quái:', t.chat.slice(n1).filter(l => /quái|Đủ|Già/.test(l)).slice(-2).join(' | ').slice(0, 200), '| tag:', await tags());
  await talk('gia_lang', [1, 1], 'Già Làng lần 2 (trả bước 1)');
  // bước 2: 16 sắt
  await R(`minecraft:give ${N} minecraft:iron_ingot 16`); await t.sleep(600);
  await talk('tho_ren', [1, 1], 'Bác Thợ Rèn (đưa 16 sắt)');
  t.log('  sắt còn:', (await R(`minecraft:clear ${N} minecraft:iron_ingot 0`)).trim(), '| tag:', await tags());
  // bước 3: đào 24 quặng
  await R(`minecraft:item replace entity ${N} weapon.mainhand with minecraft:diamond_pickaxe`); t.bot.setQuickBarSlot(0);
  const pos = (await R(`data get entity ${N} Pos`)).match(/\[([-\d.]+)d, ([-\d.]+)d, ([-\d.]+)d\]/).slice(1).map(Number);
  const bx = Math.floor(pos[0]) + 1, by = Math.floor(pos[1]), bz = Math.floor(pos[2]);
  const n3 = t.chat.length;
  for (let i = 0; i < 26; i++) {
    await R(`setblock ${bx} ${by} ${bz} minecraft:iron_ore`); await t.sleep(250);
    t.bot._client.write('block_dig', { status: 0, location: { x: bx, y: by, z: bz }, face: 1, sequence: 200 + i * 2 }); await t.sleep(120);
    t.bot._client.write('block_dig', { status: 2, location: { x: bx, y: by, z: bz }, face: 1, sequence: 201 + i * 2 }); await t.sleep(450);
  }
  await R(`setblock ${bx} ${by} ${bz} minecraft:air`); await R(`execute at ${N} run minecraft:kill @e[type=item,distance=..6,nbt={Item:{id:"minecraft:raw_iron"}}]`);
  t.log('  sau khi đào:', t.chat.slice(n3).filter(l => /quặng|Mảnh|lạnh|Gác/.test(l)).slice(-3).join(' | ').slice(0, 250), '| tag:', await tags());
  t.log('  Mảnh Sao trong túi:', (await R(`minecraft:clear ${N} minecraft:amethyst_shard 0`)).trim());
  await talk('gac_cong', [1], 'Người Gác Cổng (đưa mảnh sao)');
  await talk('gia_lang', [1, 1], 'Già Làng lần 3 (kết chương)');
  await talk('gia_lang', [], 'Già Làng lần 4 (đã xong)');
  t.log(`q8 tiền: ${m0} -> ${await bal()} (Cài đặt: 450+450+600+450+2250 = 4200) | tag: ${await tags()}`);
  t.log('  quyền danh phận:', (await R(`papi parse ${N} %player_has_permission_duckymomo.cotruyen.c1%`)).trim());
  await R(`lp user ${N} permission unset grim.exempt`);
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
