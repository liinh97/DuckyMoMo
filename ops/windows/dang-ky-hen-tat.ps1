# Đăng ký tác vụ Windows "DuckyMoMo hen tat" (1/11): mỗi lần đăng nhập Windows chạy nền ops/windows/hen-tat-may.ps1
# (chờ lệnh "Hẹn tắt máy" từ trang quản trị › Bot). Người dùng TỰ chạy file này một lần (cài đặt hệ thống):
#   powershell -ExecutionPolicy Bypass -File \\wsl.localhost\Ubuntu-22.04\home\developer\works\DuckyMoMo\ops\windows\dang-ky-hen-tat.ps1
# Gỡ:  Unregister-ScheduledTask -TaskName "DuckyMoMo hen tat" -Confirm:$false
# Nhật ký: data/ops/hen-tat.log
$ten = "DuckyMoMo hen tat"
$script = "\\wsl.localhost\Ubuntu-22.04\home\developer\works\DuckyMoMo\ops\windows\hen-tat-may.ps1"
# conhost --headless: không bật cửa sổ (Windows 11 mặc định mở Windows Terminal, -WindowStyle Hidden không ẩn được)
$action = New-ScheduledTaskAction -Execute "conhost.exe" `
    -Argument "--headless powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$script`""
$trigger = New-ScheduledTaskTrigger -AtLogOn -User "$env:USERDOMAIN\$env:USERNAME"
$trigger.Delay = "PT3M"
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew -StartWhenAvailable
$principal = New-ScheduledTaskPrincipal -UserId "$env:USERDOMAIN\$env:USERNAME" -LogonType Interactive -RunLevel Limited
Register-ScheduledTask -TaskName $ten -Action $action -Trigger $trigger -Settings $settings -Principal $principal `
    -Description "DuckyMoMo: tắt server an toàn rồi tắt máy khi trang quản trị hẹn (hết người chơi)" -Force | Out-Null
Stop-ScheduledTask -TaskName $ten -ErrorAction SilentlyContinue
Start-ScheduledTask -TaskName $ten
Write-Host "Đã đăng ký và chạy tác vụ '$ten' (tự chạy mỗi lần đăng nhập Windows)."
