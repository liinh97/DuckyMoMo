const h = require('./h');
(async () => { const t = await h.start('ThuVang'); await t.sleep(2500); t.log('ppo reset:', await t.rc.cmd(process.argv[2] || 'ppo ThuVang reset')); await t.sleep(1500); t.log('chat:', t.chat.slice(-2).join(' / ').slice(0, 160)); await t.end(); process.exit(0); })();
