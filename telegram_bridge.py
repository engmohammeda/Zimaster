import os
import sys
import zipfile
import subprocess
import json
import time

# SECURITY: credentials are read from the environment — NEVER hardcode them.
# The previously committed token must be considered COMPROMISED and revoked at
# @BotFather before this script is used again.
#   export TELEGRAM_BOT_TOKEN="123456:ABC..."
#   export TELEGRAM_CHAT_ID="123456789"
BOT_TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "")
CHAT_ID = os.environ.get("TELEGRAM_CHAT_ID", "")

def require_credentials():
    """Fail fast with a clear message instead of leaking or silently doing nothing."""
    missing = [name for name, value in (
        ("TELEGRAM_BOT_TOKEN", BOT_TOKEN),
        ("TELEGRAM_CHAT_ID", CHAT_ID),
    ) if not value]
    if missing:
        print(f"ERROR: missing environment variable(s): {', '.join(missing)}")
        print("Set them before running, e.g.: export TELEGRAM_BOT_TOKEN='...' TELEGRAM_CHAT_ID='...'")
        sys.exit(1)

def send_message(text):
    url = f"https://api.telegram.org/bot{BOT_TOKEN}/sendMessage"
    payload = json.dumps({"chat_id": CHAT_ID, "text": text, "parse_mode": "Markdown"})
    cmd = ["curl", "-s", "-X", "POST", url, "-H", "Content-Type: application/json", "-d", payload]
    try:
        res = subprocess.run(cmd, capture_output=True, text=True)
        return json.loads(res.stdout)
    except Exception as e:
        print(f"Error sending message: {e}")
        return None

def send_document(file_path, caption=""):
    url = f"https://api.telegram.org/bot{BOT_TOKEN}/sendDocument"
    file_name = os.path.basename(file_path)
    file_size_mb = os.path.getsize(file_path) / (1024 * 1024)
    print(f"Uploading {file_name} ({file_size_mb:.2f} MB)...")
    
    cmd = [
        "curl", "-s", "-X", "POST", url,
        "-F", f"chat_id={CHAT_ID}",
        "-F", f"document=@{file_path}",
        "-F", f"caption={caption}",
        "-F", "parse_mode=Markdown"
    ]
    
    for attempt in range(3):
        try:
            res = subprocess.run(cmd, capture_output=True, text=True, timeout=180)
            data = json.loads(res.stdout)
            if data.get("ok"):
                print(f"Successfully uploaded {file_name}")
                return True
            else:
                print(f"Failed attempt {attempt+1}: {res.stdout}")
        except Exception as e:
            print(f"Upload error (attempt {attempt+1}): {e}")
            time.sleep(2)
    return False

def create_source_zip(zip_name="/tmp/Zmastery_Source_Code.zip"):
    print("Creating source zip archive...")
    exclude_dirs = {'.gradle', '.build-outputs', 'build', '.git', '.idea', '.cxx', 'bin', 'obj', '__pycache__'}
    exclude_extensions = {'.apk', '.aab', '.zip'}
    
    with zipfile.ZipFile(zip_name, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as zipf:
        for root, dirs, files in os.walk('.'):
            dirs[:] = [d for d in dirs if d not in exclude_dirs and not d.startswith('.')]
            for file in files:
                ext = os.path.splitext(file)[1].lower()
                if ext in exclude_extensions and 'google-services' not in file:
                    continue
                if file.startswith('.'):
                    continue
                full_path = os.path.join(root, file)
                arcname = os.path.relpath(full_path, '.')
                zipf.write(full_path, arcname)
    print(f"Zip created: {zip_name} ({os.path.getsize(zip_name)/(1024*1024):.2f} MB)")
    return zip_name

def find_or_build_apk():
    # Check possible APK locations
    candidates = [
        "app/build/outputs/apk/debug/app-debug.apk",
        "app/build/outputs/apk/release/app-release.apk",
        "app/build/outputs/apk/release/app-release-unsigned.apk"
    ]
    for c in candidates:
        if os.path.exists(c):
            print(f"Found APK: {c}")
            return c
    
    apk_candidates = []
    for root, dirs, files in os.walk('.'):
        for f in files:
            if f.endswith('.apk'):
                apk_candidates.append(os.path.join(root, f))
    if apk_candidates:
        print(f"Found APKs: {apk_candidates}")
        return apk_candidates[0]
    return None

def main():
    require_credentials()
    print("Starting Telegram sync bridge...")
    
    # 1. Zip the source code
    zip_path = create_source_zip()
    
    # 2. Check for APK
    apk_path = find_or_build_apk()
    
    # 3. ChangeLog
    change_log = """🚀 *مشروع Z-Mastery English المحدّث (المعمارية النظيفة Clean Architecture + السورس كود)*

📦 *الحزمة:* `com.zmastery.english` (v1.1.0)

*✨ أهم التحديثات بعد إعادة الهيكلة والتقسيم:*
1. 🧩 *معمارية نظيفة (Clean Architecture & Modular Controllers):*
   - تقسيم الـ ViewModel إلى وحدات تحكم مستقلة (`CloudController`, `AiConfigController`, `CurriculumController`, `WordReviewController`, إلخ).
   - طبقة Domain كاملة بـ Use Cases و FSRS v5 و StreakManager بدون أي تبعيات لمنصة أندرويد.
2. 🔄 *تكامل محلي وسحابي متين:*
   - مصادقة كاملة مع Firebase و Google Sign-In.
   - حماية متقدمة للبيانات عبر `DataGuard` و `KeyProtector`.
3. 📱 *ودجت شاشة رئيسية تفاعلي وثابت:*
   - دعم كامل لـ API 24+ مع 11 مرحلة للسلسلة ونظام تعافي ذاتي ضد الانهيار.
"""

    send_message(change_log)
    
    # Upload Zip
    zip_ok = send_document(zip_path, caption="📦 سورس كود مشروع Z-Mastery المحدّث كاملاً (Source Code)")
    
    # Upload APK if found
    apk_ok = False
    if apk_path and os.path.exists(apk_path):
        apk_ok = send_document(apk_path, caption=f"📱 تطبيق Z-Mastery APK ({os.path.basename(apk_path)})")
    
    summary = f"""✅ *تم إرسال الملفات بنجاح إلى تيليجرام!*

- 📦 *سورس كود التطبيق:* `{os.path.basename(zip_path)}` ({os.path.getsize(zip_path)/(1024*1024):.2f} MB) -> {'نجح ✅' if zip_ok else 'فشل ❌'}
"""
    if apk_path:
        summary += f"- 📱 *ملف APK:* `{os.path.basename(apk_path)}` ({os.path.getsize(apk_path)/(1024*1024):.2f} MB) -> {'نجح ✅' if apk_ok else 'فشل ❌'}\n"
    
    send_message(summary)
    print("Bridge execution completed.")

if __name__ == "__main__":
    main()
