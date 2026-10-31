# Hẹn tắt máy khi hết người (1/11): chạy nền mỗi lần đăng nhập Windows (tác vụ "DuckyMoMo hen tat", đăng ký bằng
# dang-ky-hen-tat.ps1). Trang quản trị › Bot › "Hẹn tắt máy" ghi data/ops/tat-may.now khi đủ giờ không còn người thật;
# file này thấy thì: tắt server an toàn (ops/tat-an-toan.sh: lưu world, NPC, ghi danh sách để lần mở máy sau tự bật lại),
# tắt WSL, tắt máy sau 60 giây (huỷ kịp: shutdown /a).
$distro = "Ubuntu-22.04"
$repo = "\\wsl.localhost\$distro\home\developer\works\DuckyMoMo"
$flag = "$repo\data\ops\tat-may.now"
$log = "$repo\data\ops\hen-tat.log"
function Ghi($m) { try { Add-Content -Path $log -Value ("{0} {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $m) -Encoding UTF8 } catch {} }
Ghi "bắt đầu theo dõi"
while ($true) {
    if (Test-Path $flag) {
        Remove-Item $flag -Force -ErrorAction SilentlyContinue
        Ghi "thấy lệnh tắt máy: tắt server an toàn"
        wsl.exe -d $distro -- /home/developer/works/DuckyMoMo/ops/tat-an-toan.sh | Out-Null
        Ghi "server đã tắt, tắt WSL và tắt máy sau 60 giây"
        wsl.exe --shutdown
        shutdown.exe /s /t 60 /c "DuckyMoMo: hết người chơi, tắt máy theo hẹn (huỷ: shutdown /a)"
        exit 0
    }
    Start-Sleep -Seconds 20
}
