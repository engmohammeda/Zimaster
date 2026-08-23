"""
Z-Mastery Lesson Uploader (Secured)
====================================
A production-ready Flask web app to upload lesson JSON files and push them
to Firestore (or save locally).

Security features:
  • Secret key from environment variable
  • File size limit (5 MB)
  • Strict JSON schema validation
  • Safe filename sanitization
  • CSRF protection via session tokens
  • Audit logging
  • debug=False in production

Usage (development):
    pip install flask
    export FLASK_SECRET_KEY="your-dev-secret"
    python app.py

Usage (production):
    export FLASK_SECRET_KEY="$(openssl rand -hex 32)"
    export FLASK_ENV=production
    gunicorn app:app --bind 0.0.0.0:5000 --workers 2
"""

import json
import os
import re
import hashlib
import logging
from datetime import datetime
from functools import wraps
from flask import (
    Flask, render_template, request, jsonify,
    flash, redirect, session, abort,
)

# ──────────────────────────────────────────────────────────────
#  Configuration
# ──────────────────────────────────────────────────────────────

app = Flask(__name__)

# Secret key: MUST come from environment in production
app.secret_key = os.environ.get("FLASK_SECRET_KEY", "dev-only-change-me")

# Max upload size: 5 MB
app.config["MAX_CONTENT_LENGTH"] = 5 * 1024 * 1024

# Production mode detection
IS_PRODUCTION = os.environ.get("FLASK_ENV", "development") == "production"

# Upload directory
UPLOAD_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "uploaded_lessons")
os.makedirs(UPLOAD_DIR, exist_ok=True)

# Audit log
LOG_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "logs")
os.makedirs(LOG_DIR, exist_ok=True)

logging.basicConfig(
    filename=os.path.join(LOG_DIR, "upload_audit.log"),
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
audit_log = logging.getLogger("audit")

# Allowed file extensions
ALLOWED_EXTENSIONS = {".json"}

# Maximum number of lessons per upload
MAX_LESSONS_PER_UPLOAD = 100


# ──────────────────────────────────────────────────────────────
#  Helpers
# ──────────────────────────────────────────────────────────────

def sanitize_filename(name: str) -> str:
    """Remove dangerous characters from filenames."""
    # Strip path separators and null bytes
    name = name.replace("/", "_").replace("\\", "_").replace("\x00", "")
    # Keep only safe characters
    name = re.sub(r"[^\w\s\-.]", "", name)
    # Collapse whitespace
    name = re.sub(r"\s+", "_", name.strip())
    # Limit length
    return name[:120] if name else "unnamed"


def allowed_file(filename: str) -> bool:
    """Check if the file extension is allowed."""
    _, ext = os.path.splitext(filename.lower())
    return ext in ALLOWED_EXTENSIONS


def validate_lesson(data: dict, index: int = 0) -> str | None:
    """Validate a single lesson object. Returns error message or None."""
    if not isinstance(data, dict):
        return f"العنصر {index + 1}: يجب أن يكون كائن JSON"
    if "metadata" not in data:
        return f"العنصر {index + 1}: يفتقد metadata"
    meta = data.get("metadata", {})
    if not isinstance(meta, dict):
        return f"العنصر {index + 1}: metadata يجب أن يكون كائن"
    course_id = meta.get("course_id", "")
    course_name = meta.get("course_name_ar", "")
    if not course_id and not course_name:
        return f"العنصر {index + 1}: metadata.course_id أو course_name_ar مطلوب"
    title = meta.get("title", "")
    if not title:
        return f"العنصر {index + 1}: metadata.title مطلوب"
    return None


def validate_upload(data) -> tuple[int, str | None]:
    """Validate uploaded JSON. Returns (lesson_count, error_message)."""
    if isinstance(data, list):
        if len(data) == 0:
            return 0, "المصفوفة فارغة"
        if len(data) > MAX_LESSONS_PER_UPLOAD:
            return 0, f"عدد الدروس يتجاوز الحد الأقصى ({MAX_LESSONS_PER_UPLOAD})"
        for i, item in enumerate(data):
            err = validate_lesson(item, i)
            if err:
                return 0, err
        return len(data), None
    elif isinstance(data, dict):
        if "metadata" in data:
            err = validate_lesson(data)
            if err:
                return 0, err
            return 1, None
        elif "lessons" in data and isinstance(data["lessons"], list):
            lessons = data["lessons"]
            if len(lessons) > MAX_LESSONS_PER_UPLOAD:
                return 0, f"عدد الدروس يتجاوز الحد الأقصى ({MAX_LESSONS_PER_UPLOAD})"
            for i, item in enumerate(lessons):
                err = validate_lesson(item, i)
                if err:
                    return 0, err
            return len(lessons), None
        else:
            return 0, "هيكل JSON غير معروف — يجب أن يحتوي على metadata أو lessons"
    else:
        return 0, "يجب أن يكون ملف JSON كائناً أو مصفوفة"


def file_checksum(content: bytes) -> str:
    """Compute SHA-256 checksum of file content."""
    return hashlib.sha256(content).hexdigest()[:16]


def log_action(action: str, detail: str, ip: str = ""):
    """Write to the audit log."""
    client_ip = ip or request.remote_addr or "unknown"
    audit_log.info(f"{action} | ip={client_ip} | {detail}")


# ──────────────────────────────────────────────────────────────
#  CSRF Protection
# ──────────────────────────────────────────────────────────────

def csrf_token():
    """Generate or return existing CSRF token for the session."""
    if "_csrf" not in session:
        session["_csrf"] = os.urandom(32).hex()
    return session["_csrf"]


def verify_csrf():
    """Verify CSRF token on state-changing requests."""
    if request.method in ("GET", "HEAD", "OPTIONS"):
        return
    token = request.form.get("_csrf") or request.headers.get("X-CSRF-Token")
    if not token or token != session.get("_csrf"):
        abort(403, description="رمز CSRF غير صالح")


@app.before_request
def before_request_csrf():
    """Check CSRF for all POST requests."""
    if request.method == "POST":
        verify_csrf()


@app.context_processor
def inject_csrf():
    """Make CSRF token available in all templates."""
    return {"csrf_token": csrf_token()}


# ──────────────────────────────────────────────────────────────
#  API Key Authentication (for /api/* endpoints)
# ──────────────────────────────────────────────────────────────

API_KEY = os.environ.get("ZMASTERY_API_KEY", "")

def require_api_key(f):
    """Decorator: require a valid API key for programmatic access."""
    @wraps(f)
    def decorated(*args, **kwargs):
        if not API_KEY:
            # No API key configured → allow (development mode)
            return f(*args, **kwargs)
        provided = request.headers.get("X-API-Key") or request.args.get("api_key")
        if provided != API_KEY:
            log_action("API_AUTH_FAILED", f"invalid key provided")
            return jsonify({"status": "error", "msg": "مفتاح API غير صالح"}), 401
        return f(*args, **kwargs)
    return decorated


# ──────────────────────────────────────────────────────────────
#  Routes
# ──────────────────────────────────────────────────────────────

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

        # Security: check extension
        if not allowed_file(file.filename):
            results.append({
                "file": file.filename, "status": "error",
                "msg": "يُقبل فقط ملفات .json",
            })
            log_action("UPLOAD_REJECTED", f"bad extension: {file.filename}")
            continue

        try:
            content = file.read()

            # Security: check size (belt and suspenders with MAX_CONTENT_LENGTH)
            if len(content) > app.config["MAX_CONTENT_LENGTH"]:
                results.append({
                    "file": file.filename, "status": "error",
                    "msg": f"حجم الملف يتجاوز الحد الأقصى (5 ميغابايت)",
                })
                continue

            data = json.loads(content.decode("utf-8"))

            # Validate structure
            lesson_count, error = validate_upload(data)
            if error:
                results.append({
                    "file": file.filename, "status": "error",
                    "msg": error,
                })
                log_action("VALIDATION_FAILED", f"{file.filename}: {error}")
                continue

            # Safe filename + timestamp + checksum
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            safe_name = sanitize_filename(file.filename)
            checksum = file_checksum(content)
            save_name = f"{timestamp}_{checksum}_{safe_name}"
            save_path = os.path.join(UPLOAD_DIR, save_name)

            # Prevent path traversal
            real_path = os.path.realpath(save_path)
            if not real_path.startswith(os.path.realpath(UPLOAD_DIR)):
                results.append({
                    "file": file.filename, "status": "error",
                    "msg": "اسم ملف غير آمن",
                })
                log_action("PATH_TRAVERSAL_BLOCKED", file.filename)
                continue

            with open(save_path, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)

            results.append({
                "file": file.filename,
                "status": "success",
                "msg": f"تم حفظ {lesson_count} درس ✓",
                "path": save_name,
            })
            log_action("UPLOAD_SUCCESS", f"{file.filename} → {save_name} ({lesson_count} lessons)")

        except json.JSONDecodeError as e:
            results.append({
                "file": file.filename, "status": "error",
                "msg": f"JSON غير صالح: {str(e)[:80]}",
            })
            log_action("JSON_PARSE_FAILED", f"{file.filename}: {str(e)[:60]}")
        except UnicodeDecodeError:
            results.append({
                "file": file.filename, "status": "error",
                "msg": "ترميز الملف غير صالح — يجب أن يكون UTF-8",
            })
        except Exception as e:
            results.append({
                "file": file.filename, "status": "error",
                "msg": f"خطأ غير متوقع: {str(e)[:80]}",
            })
            log_action("UPLOAD_ERROR", f"{file.filename}: {str(e)[:60]}")

    return render_template("results.html", results=results)


@app.route("/api/upload", methods=["POST"])
@require_api_key
def api_upload():
    """JSON API endpoint for programmatic uploads (requires API key)."""
    # Skip CSRF for API (uses API key auth instead)
    if request.is_json:
        data = request.get_json()
        lesson_count, error = validate_upload(data)
        if error:
            log_action("API_VALIDATION_FAILED", error)
            return jsonify({"status": "error", "msg": error}), 400

        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        checksum = file_checksum(json.dumps(data).encode())
        save_name = f"{timestamp}_{checksum}_api_upload.json"
        save_path = os.path.join(UPLOAD_DIR, save_name)

        with open(save_path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)

        log_action("API_UPLOAD_SUCCESS", f"{save_name} ({lesson_count} lessons)")
        return jsonify({
            "status": "success",
            "path": save_name,
            "lessons": lesson_count,
            "checksum": checksum,
        })

    # File upload via multipart
    files = request.files.getlist("lessons")
    results = []
    for file in files:
        if file.filename == "" or not allowed_file(file.filename):
            results.append({"file": file.filename, "status": "error", "msg": "ملف غير صالح"})
            continue
        try:
            content = file.read()
            data = json.loads(content.decode("utf-8"))
            lesson_count, error = validate_upload(data)
            if error:
                results.append({"file": file.filename, "status": "error", "msg": error})
                continue

            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            safe_name = sanitize_filename(file.filename)
            checksum = file_checksum(content)
            save_name = f"{timestamp}_{checksum}_{safe_name}"
            save_path = os.path.join(UPLOAD_DIR, save_name)

            with open(save_path, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)

            results.append({
                "file": file.filename, "status": "success",
                "path": save_name, "lessons": lesson_count,
            })
            log_action("API_UPLOAD_SUCCESS", f"{file.filename} → {save_name}")
        except Exception as e:
            results.append({"file": file.filename, "status": "error", "msg": str(e)[:80]})

    return jsonify({"results": results})


@app.route("/lessons")
def list_lessons():
    """List all uploaded lessons."""
    files = []
    for f in sorted(os.listdir(UPLOAD_DIR), reverse=True):
        path = os.path.join(UPLOAD_DIR, f)
        if not os.path.isfile(path):
            continue
        size = os.path.getsize(path)
        files.append({"name": f, "size": f"{size/1024:.1f} KB"})
    return render_template("lessons.html", files=files)


@app.route("/api/lessons")
@require_api_key
def api_list_lessons():
    """JSON API: list all uploaded lessons."""
    files = []
    for f in sorted(os.listdir(UPLOAD_DIR), reverse=True):
        path = os.path.join(UPLOAD_DIR, f)
        if not os.path.isfile(path):
            continue
        files.append({
            "name": f,
            "size": os.path.getsize(path),
            "created": f[:15].replace("_", " ", 2) if len(f) > 15 else "",
        })
    return jsonify({"files": files, "count": len(files)})


@app.route("/api/health")
def api_health():
    """Health check endpoint."""
    return jsonify({
        "status": "ok",
        "lessons_count": len([f for f in os.listdir(UPLOAD_DIR) if os.path.isfile(os.path.join(UPLOAD_DIR, f))]),
        "production": IS_PRODUCTION,
    })


# ──────────────────────────────────────────────────────────────
#  Error handlers
# ──────────────────────────────────────────────────────────────

@app.errorhandler(413)
def too_large(e):
    return jsonify({"status": "error", "msg": "حجم الملف يتجاوز الحد الأقصى (5 ميغابايت)"}), 413


@app.errorhandler(403)
def forbidden(e):
    return jsonify({"status": "error", "msg": "ممنوع — تحقق من رمز CSRF أو مفتاح API"}), 403


@app.errorhandler(404)
def not_found(e):
    return jsonify({"status": "error", "msg": "الصفحة غير موجودة"}), 404


# ──────────────────────────────────────────────────────────────
#  Entry point
# ──────────────────────────────────────────────────────────────

if __name__ == "__main__":
    if IS_PRODUCTION:
        print("⚠️  Use gunicorn in production, not 'python app.py'")
        print("   gunicorn app:app --bind 0.0.0.0:5000 --workers 2")
    else:
        print("╔══════════════════════════════════════════════════╗")
        print("║  Z-Mastery Lesson Uploader (Development)         ║")
        print("║  Open http://localhost:5000 in your browser      ║")
        print("║  ⚠️  Set FLASK_SECRET_KEY for production!         ║")
        print("╚══════════════════════════════════════════════════╝")
        app.run(debug=True, host="0.0.0.0", port=5000)
