# Đăng ký tác vụ Windows "DuckyMoMo tu sua": mỗi lần đăng nhập Windows, chờ 2 phút rồi chạy
# ops/tu-sua-sau-khoi-dong.sh trong WSL (sửa cổng chết, thư mục gắn bị treo, playit).
# Người dùng TỰ chạy file này (đây là cài đặt hệ thống):
#   powershell -ExecutionPolicy Bypass -File \\wsl.localhost\Ubuntu-22.04\home\developer\works\DuckyMoMo\ops\windows\dang-ky-tu-sua.ps1
# Gỡ:  Unregister-ScheduledTask -TaskName "DuckyMoMo tu sua" -Confirm:$false
# Chạy thử ngay:  Start-ScheduledTask -TaskName "DuckyMoMo tu sua"   (nhật ký: data/ops/tu-sua.log)

$ten = "DuckyMoMo tu sua"
$distro = "Ubuntu-22.04"
$script = "/home/developer/works/DuckyMoMo/ops/tu-sua-sau-khoi-dong.sh"

# conhost --headless: không bật cửa sổ đen lên màn hình
$action = New-ScheduledTaskAction -Execute "conhost.exe" `
    -Argument "--headless wsl.exe -d $distro -- $script"
$trigger = New-ScheduledTaskTrigger -AtLogOn -User "$env:USERDOMAIN\$env:USERNAME"
$trigger.Delay = "PT2M"   # chờ Docker Desktop khởi động
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -ExecutionTimeLimit (New-TimeSpan -Minutes 30) -MultipleInstances IgnoreNew -StartWhenAvailable
$principal = New-ScheduledTaskPrincipal -UserId "$env:USERDOMAIN\$env:USERNAME" -LogonType Interactive -RunLevel Limited

Register-ScheduledTask -TaskName $ten -Action $action -Trigger $trigger -Settings $settings -Principal $principal `
    -Description "DuckyMoMo: tự sửa cổng/thư mục gắn/playit sau khi Windows khởi động lại" -Force | Out-Null
Write-Host "Đã đăng ký tác vụ '$ten' (chạy 2 phút sau mỗi lần đăng nhập Windows)."
