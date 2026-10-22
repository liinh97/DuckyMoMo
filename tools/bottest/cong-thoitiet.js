// w4 thời tiết trong trận (PetCombat siege-weather): phiên đánh đang mở (/swa battlesession start), ThuW1 đứng cạnh cờ vây ThuThanhB.
// Đêm -> Bóng tối (darkness), mưa -> Chậm (slowness), nắng gắt mùa khô ban ngày -> Đói (hunger). Thử thêm: có pet Sói (mắt đêm) thì đêm được Nhìn đêm.
// Giờ và thời tiết của thế giới đổi tạm, xong trả lại giờ cũ + trời quang.
const { start, moveTo } = require('./h');
const A = 'ThuW1';
(async () => {
  let t = await start(A, '10.9.1.1');
  const R = c => t.rc.cmd(c);
  const t0 = ((await R('minecraft:time query daytime')).match(/(\d+)/) || [0, 6000])[1];
  t.log('giờ thế giới lúc đầu:', t0, '| mùa:', (await R(`papi parse ${A} %dmmmua_id%`)).trim());
  await R(`gamemode survival ${A}`); await R(`effect clear ${A}`);
  t = await moveTo(t, 13629, -3152, 'minecraft:overworld', '10.9.1.1');
  const tin = []; t.bot.on('messagestr', m => tin.push(m)); t.bot._client.on('action_bar', p => tin.push('[bar] ' + JSON.stringify(p.text).replace(/[{}"\[\]]|type:[a-z]+,|text:|value:/g, '').slice(0, 120)));
  // từng hiệu ứng bằng predicate (data get qua rcon bị cắt "…" khi có nhiều hiệu ứng)
  const eff = async () => { const o = []; for (const e of ['darkness', 'night_vision', 'slowness', 'hunger']) if (/passed/.test(await R(`execute as ${A} if predicate {condition:"minecraft:entity_properties",entity:"this",predicate:{effects:{"minecraft:${e}":{}}}}`))) o.push(e); return o.join(',') || 'không'; };
  const phase = async (label, cmds) => {
    await R(`effect clear ${A}`); for (const c of cmds) await R(c);
    const n0 = tin.length; await t.sleep(4000);
    t.log(`w4 ${label}: hiệu ứng = ${await eff()} | tin: ${tin.slice(n0).filter(x => !x.includes('❤')).join(' | ').slice(0, 250)}`);
  };
  t.log('vùng vây/phiên:', (await t.say('/sw hud ThuThanhB', 1500)).join(' | ').slice(0, 200));
  await phase('ĐÊM, trời quang', ['minecraft:weather clear', 'minecraft:time set 18000']);
  await phase('NGÀY, MƯA', ['minecraft:time set 6000', 'minecraft:weather rain']);
  await phase('NGÀY, nắng (mùa khô), không mưa', ['minecraft:weather clear', 'minecraft:time set 6000']);
  // pet Sói (mắt đêm) đi theo
  t.log('tạo pet:', (await R(`petadmin create ${A} Wolf`)).replace(/\s+/g, ' ').slice(0, 150));
  await t.sleep(2000); await R(`petcall ${A}`); await t.sleep(1500);
  await phase('ĐÊM, có pet Sói (mắt đêm)', ['minecraft:time set 18000']);
  await R(`minecraft:time set ${t0}`); await R('minecraft:weather clear'); await R(`effect clear ${A}`);
  t.log('trả lại giờ', t0, 'và trời quang');
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
