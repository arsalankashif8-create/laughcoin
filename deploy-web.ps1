# LaughCoin Web & APK Deployment Script
# This script builds the APK and uploads it + index.html to Namecheap Hosting via FTP

$FTP_HOST = "ftp://laughcoin.online/public_html/"
$FTP_USER = "admin@laughcoin.online"
$FTP_PASS = "karsalan@2010"

Write-Host "🚀 Starting LaughCoin Deployment..." -ForegroundColor Cyan

# 1. Build the APK (Optional, skip if you just built it)
# Write-Host "🏗️ Building Release APK..." -ForegroundColor Yellow
# ./gradlew assembleRelease

# 2. Sync APK to root
Write-Host "📦 Syncing APK to root..." -ForegroundColor Yellow
$sourceApk = "app/release/app-release.apk"
$targetApk = "laughcoin.apk"

if (Test-Path $sourceApk) {
    Copy-Item $sourceApk $targetApk
    Write-Host "✅ APK renamed and moved to root." -ForegroundColor Green
} else {
    Write-Host "⚠️ Warning: app-release.apk not found. Using existing laughcoin.apk in root." -ForegroundColor DarkYellow
}

# 3. Upload to FTP
$filesToUpload = @("index.html", "laughcoin.apk")

foreach ($file in $filesToUpload) {
    if (Test-Path $file) {
        Write-Host "📤 Uploading $file..." -ForegroundColor Yellow
        $webClient = New-Object System.Net.WebClient
        $webClient.Credentials = New-Object System.Net.NetworkCredential($FTP_USER, $FTP_PASS)
        $uri = New-Object System.Uri($FTP_HOST + $file)

        try {
            $webClient.UploadFile($uri, "STOR", (Get-Item $file).FullName)
            Write-Host "✅ Successfully uploaded $file" -ForegroundColor Green
        } catch {
            Write-Host "❌ Failed to upload $file. Error: $($_.Exception.Message)" -ForegroundColor Red
        }
    } else {
        Write-Host "❌ Error: $file not found!" -ForegroundColor Red
    }
}

Write-Host "🏁 Deployment Finished! Check https://laughcoin.online/laughcoin.apk" -ForegroundColor Cyan
