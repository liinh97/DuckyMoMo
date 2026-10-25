# Tắt máy an toàn (25/10): tắt đúng cách mọi server trong Docker (lưu world, NPC, DB) rồi mới tắt Windows.
# Tắt bằng Win+X → U → U thì WSL bị đóng ngang, server Minecraft như bị rút điện (đã mất 9 NPC đặt sau lần lưu cuối).
#
# Cài lối tắt "Tắt máy an toàn" ra màn hình nền (chạy 1 lần, trong PowerShell):
#   powershell -ExecutionPolicy Bypass -File \\wsl.localhost\Ubuntu-22.04\home\developer\works\DuckyMoMo\ops\windows\tat-may-an-toan.ps1 -CaiDat
# Sau đó muốn tắt máy: bấm đúp lối tắt đó. Có người đang chơi thì hỏi lại trước khi tắt.
# Khởi động lại máy thay vì tắt: thêm -KhoiDongLai (lối tắt thứ hai "Khởi động lại an toàn" được tạo cùng lúc).
# Lần đăng nhập sau, tác vụ "DuckyMoMo tu sua" (ops/windows/dang-ky-tu-sua.ps1) bật lại các container đã tắt.
param([switch]$CaiDat, [switch]$KhoiDongLai)

$distro = "Ubuntu-22.04"
$script = "/home/developer/works/DuckyMoMo/ops/tat-an-toan.sh"

if ($CaiDat) {
    $desk = [Environment]::GetFolderPath("Desktop")
    $ws = New-Object -ComObject WScript.Shell
    foreach ($item in @(@("Tắt máy an toàn", ""), @("Khởi động lại an toàn", " -KhoiDongLai"))) {
        $lnk = $ws.CreateShortcut((Join-Path $desk ($item[0] + ".lnk")))
        $lnk.TargetPath = "powershell.exe"
        $lnk.Arguments = "-ExecutionPolicy Bypass -File `"$PSCommandPath`"" + $item[1]
        $lnk.IconLocation = "shell32.dll,27"
        $lnk.Save()
    }
    Write-Host "Đã tạo 2 lối tắt ngoài màn hình nền: 'Tắt máy an toàn', 'Khởi động lại an toàn'."
    Write-Host "Nhớ chạy cả ops\windows\dang-ky-tu-sua.ps1 (nếu chưa) để lần mở máy sau tự bật lại server."
    exit 0
}

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Write-Host "Kiểm tra người đang chơi..."
$ds = wsl.exe -d $distro -- $script --kiem-tra
if ($LASTEXITCODE -eq 2) {
    Write-Host "Đang có người chơi:" -ForegroundColor Yellow
    $ds | ForEach-Object { Write-Host $_ }
    $tl = Read-Host "Vẫn tắt? (gõ c rồi Enter để tắt, Enter để huỷ)"
    if ($tl -ne "c") { Write-Host "Đã huỷ."; Start-Sleep 2; exit 1 }
}
Write-Host "Đang tắt server (tối đa khoảng 3 phút, đừng đóng cửa sổ này)..."
wsl.exe -d $distro -- $script
Write-Host "Server đã tắt xong. Tắt WSL..."
wsl.exe --shutdown
if ($KhoiDongLai) { shutdown.exe /r /t 5 } else { shutdown.exe /s /t 5 }
Write-Host "Máy sẽ tắt sau 5 giây (huỷ: shutdown /a)."
Start-Sleep 5
