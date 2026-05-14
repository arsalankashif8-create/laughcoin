# LaughCoin Web Deployment
# Host found via nslookup
$FTP_HOST = "ftp://198.54.114.143/public_html/"
$FTP_USER = "admin@laughcoin.online"
$FTP_PASS = "karsalan@2010"

# 1. Sync APK
if (Test-Path "app/release/app-release.apk") {
    Copy-Item "app/release/app-release.apk" "laughcoin.apk" -Force
}

# 2. Upload
$wc = New-Object System.Net.WebClient
$wc.Credentials = New-Object System.Net.NetworkCredential($FTP_USER, $FTP_PASS)

Write-Host "Uploading index.html..."
try {
    $wc.UploadFile($FTP_HOST + "index.html", "STOR", (Get-Item "index.html").FullName)
    Write-Host "Success!"
} catch {
    Write-Host "Failed index.html: $($_.Exception.Message)"
}

Write-Host "Uploading laughcoin.apk..."
try {
    $wc.UploadFile($FTP_HOST + "laughcoin.apk", "STOR", (Get-Item "laughcoin.apk").FullName)
    Write-Host "Success!"
} catch {
    Write-Host "Failed apk: $($_.Exception.Message)"
}
