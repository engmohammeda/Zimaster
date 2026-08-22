"""
Z-Mastery Lesson Uploader
==========================
A simple Flask web app to upload lesson JSON files and push them to
Firestore (or save locally for now).

Usage:
    pip install flask
    python app.py
    # Open http://localhost:5000 in browser
"""

import json
import os
from datetime import datetime
from flask import Flask, render_template, request, jsonify, flash, redirect

app = Flask(__name__)
app.secret_key = "zmastery-upload-tool-secret"

# Where uploaded lessons are stored (until Firestore is connected)
UPLOAD_DIR = os.path.join(os.path.dirname(__file__), "uploaded_lessons")
os.makedirs(UPLOAD_DIR, exist_ok=True)


@app.route("/")
def index():
    """Main upload page."""
    return render_template("index.html")


@app.route("/upload", methods=["POST"])
def upload():
    """Handle single or multiple JSON lesson uploads."""
    files = request.files.getlist("lessons")
    if not files or all(f.filename == "" for f in files):
        flash("لم تختر أي ملف", "error")
        return redirect("/")

    results = []
    for file in files:
        if file.filename == "":
            continue
        if not file.filename.endswith(".json"):
            results.append({"file": file.filename, "status": "error", "msg": "يجب أن يكون ملف JSON"})
            continue

        try:
            content = file.read().decode("utf-8")
            data = json.loads(content)

            # Validate basic structure
            if isinstance(data, list):
                for item in data:
                    if not isinstance(item, dict) or "metadata" not in item:
                        raise ValueError("مصفوفة JSON غير صالحة — يفتقد متادات درس")
                lesson_count = len(data)
            elif isinstance(data, dict):
                if "metadata" not in data:
                    raise ValueError("مصفوفة JSON غير صالحة — يفتقد متادات درس")
                lesson_count = 1
            else:
                raise ValueError("يجب أن يكون ملف JSON بهيكل مصفوفة أو قائمة")

            # Save to disk
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            safe_name = file.filename.replace(" ", "_").replace("/", "_")
            save_path = os.path.join(UPLOAD_DIR, f"{timestamp}_{safe_name}")
            with open(save_path, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)

            results.append({
                "file": file.filename,
                "status": "success",
                "msg": f"تم حفظ {lesson_count} درس",
                "path": save_path,
            })

        except json.JSONDecodeError as e:
            results.append({"file": file.filename, "status": "error", "msg": f"JSON غير صالح: {str(e)}"})
        except Exception as e:
            results.append({"file": file.filename, "status": "error", "msg": str(e)})

    return render_template("results.html", results=results)


@app.route("/api/upload", methods=["POST"])
def api_upload():
    """JSON API endpoint for programmatic uploads."""
    if request.is_json:
        data = request.get_json()
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        save_path = os.path.join(UPLOAD_DIR, f"{timestamp}_api_upload.json")
        with open(save_path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        return jsonify({"status": "success", "path": save_path, "lessons": len(data) if isinstance(data, list) else 1})

    files = request.files.getlist("lessons")
    results = []
    for file in files:
        if file.filename == "":
            continue
        try:
            content = file.read().decode("utf-8")
            data = json.loads(content)
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            safe_name = file.filename.replace(" ", "_").replace("/", "_")
            save_path = os.path.join(UPLOAD_DIR, f"{timestamp}_{safe_name}")
            with open(save_path, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            results.append({"file": file.filename, "status": "success", "path": save_path})
        except Exception as e:
            results.append({"file": file.filename, "status": "error", "msg": str(e)})

    return jsonify({"results": results})


@app.route("/lessons")
def list_lessons():
    """List all uploaded lessons."""
    files = []
    for f in sorted(os.listdir(UPLOAD_DIR), reverse=True):
        path = os.path.join(UPLOAD_DIR, f)
        size = os.path.getsize(path)
        files.append({"name": f, "size": f"{size/1024:.1f} KB", "path": path})
    return render_template("lessons.html", files=files)


if __name__ == "__main__":
    print("╔══════════════════════════════════════════════════╗")
    print("║  Z-Mastery Lesson Uploader                        ║")
    print("║  Open http://localhost:5000 in your browser       ║")
    print("╚══════════════════════════════════════════════════╝")
    app.run(debug=True, host="0.0.0.0", port=5000)
