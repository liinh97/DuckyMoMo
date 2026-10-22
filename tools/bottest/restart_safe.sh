# Khởi động lại banghoi-1 an toàn: CÓ AI ONLINE THÌ DỪNG (21/10: script cũ chỉ in số người, đã làm văng Linh).
cd /home/developer/works/DuckyMoMo
ON=$(docker compose exec -T banghoi-1 rcon-cli list | sed 's/\x1b\[[0-9;]*m//g' | head -1)
echo "$ON"
N=$(echo "$ON" | grep -oE '[0-9]+' | head -1)
if [ "$N" != "0" ]; then echo "CÓ NGƯỜI ONLINE ($N) -> KHÔNG khởi động lại"; exit 1; fi
docker compose exec -T banghoi-1 rcon-cli "broadcast &eBang Hội Chiến khởi động lại sau 30 giây." >/dev/null
sleep 30
ON=$(docker compose exec -T banghoi-1 rcon-cli list | sed 's/\x1b\[[0-9;]*m//g' | head -1); N=$(echo "$ON" | grep -oE '[0-9]+' | head -1)
if [ "$N" != "0" ]; then echo "Có người vừa vào ($N) -> huỷ"; exit 1; fi
docker compose restart banghoi-1 2>&1 | tail -1
for i in $(seq 1 60); do st=$(docker inspect -f '{{.State.Health.Status}}' duckymomo-banghoi-1-1); [ "$st" = healthy ] && break; sleep 5; done
echo "health: $st"
