import os
import sys
import zipfile
import subprocess
import json
import time

BOT_TOKEN = "8459296920:AAGq6b6sguUQx0Abk7jbWFDma30v7Ncfbhc"
CHAT_ID = "5926222376"

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

def create_source_zip(zip_name="/tmp/Zimaster_Source_Backup.zip"):
    print("Creating source zip archive...")
    exclude_dirs = {'.gradle', '.build-outputs', 'build', '.git', '.idea', '.cxx', 'bin', 'obj'}
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
    print("Starting Telegram sync bridge...")
    
    # 1. Zip the source code
    zip_path = create_source_zip()
    
    # 2. Check for APK
    apk_path = find_or_build_apk()
    
    # 3. ChangeLog
    change_log = """🚀 *تقرير مزامنة مشروع Zimaster (Z-Mastery)*

📅 *التاريخ:* 2026-08-23
📦 *الحزمة:* `com.zmastery.english` (v1.1.0)

*✨ أبرز التعديلات والإصلاحات:*
1. 🔐 *تكامل Firebase*: ربط مشروع `zmastery` (ID: 836170376747) وتوليد `google-services.json` تلقائياً.
2. 📱 *إصلاح App Widget*: حل مشكلة "لا يمكن تحميل التطبيق المصغر" وتجهيز Layout متوافق مع كافة المشغلات و HyperOS / Samsung / Pixel.
3. ⚙️ *GitHub Actions CI/CD*: تحديث سير العمل لإجراء Build كامل وتوليد Debug APK + Release APK + AAB ونشرها تلقائياً في Releases.
4. 📝 *توثيق شامل*: إنشاء ملف `README.md` احترافي للمستودع يشرح البنية المعمارية ومميزات FSRS والذكاء الاصطناعي.
5. 🛡️ *استقرار الكود*: معالجة فروع `AiService` وتحديث الاعتمادات.
"""

    send_message(change_log)
    
    # Upload Zip
    zip_ok = send_document(zip_path, caption="📦 سورس كود مشروع Zimaster كاملاً (Source Code Backup)")
    
    apk_ok = False
    if apk_path and os.path.exists(apk_path):
        apk_ok = send_document(apk_path, caption="📱 نسخة التطبيق APK")
    
    summary = f"""✅ *تمت المزامنة بنجاح!*

- 📦 *ملف السورس:* `{os.path.basename(zip_path)}` ({os.path.getsize(zip_path)/(1024*1024):.2f} MB) -> {'نجح ✅' if zip_ok else 'فشل ❌'}
"""
    if apk_path:
        summary += f"- 📱 *ملف APK:* `{os.path.basename(apk_path)}` ({os.path.getsize(apk_path)/(1024*1024):.2f} MB) -> {'نجح ✅' if apk_ok else 'فشل ❌'}\n"
    
    send_message(summary)
    print("Bridge execution completed.")

if __name__ == "__main__":
    main()

