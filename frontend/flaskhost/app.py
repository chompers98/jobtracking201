from flask import Flask, jsonify, request, abort, send_from_directory
from flask_cors import CORS
from datetime import date, datetime, timedelta
import os

app = Flask(__name__)
CORS(app)

# Path to the web directory
WEB_DIR = os.path.join(os.path.dirname(__file__), "..", "web")


# --- In-memory mock data ----------------------------------------------------

# User data structure (in a real app, this would be per authenticated user)
user_data = {
    "id": 1,
    "email": "user@example.com",
    "username": "John Doe",
    "password_hash": "pbkdf2:sha256:150000$hashedsecret",
    "role": "USER",
    "email_processing_state": {
        "last_uid": 0
    },
    "integrations": {
        "google": {
            "enabled": False,
            "email": "",
            "clientId": "",
            "clientSecret": "",
            "access_token": "",
            "refresh_token": "",
            "expires_at": "",
            "gmailEnabled": True,
            "calendarEnabled": True,
        },
        "openai": {
            "enabled": False,
            "apiKey": "",
            "model": "",
        },
    },
}

applications = [
    {
        "id": 1,
        "company": "Google",
        "title": "Software Engineer",
        "status": "INTERVIEW",
        "deadline_at": "2025-11-15",
        "interview_at": "2025-11-15T14:00:00",
        "links": {"job_post": "https://careers.google.com/jobs/12345"},
        "location": "Mountain View, CA",
        "job_type": "Full-time",
        "salary": "$120k - $180k",
        "job_link": "https://careers.google.com/jobs/12345", # Kept for backward compat
        "experience": "3-5 years",
        "created_at": "2025-10-25",
        "applied_at": "2025-10-28",
        "documents": [
            {"label": "Resume_2025.pdf", "url": "#"},
            {"label": "Cover_Letter_Google.pdf", "url": "#"},
            {"label": "Portfolio Website", "url": "#"},
        ],
        "notes": (
            "Contacted by recruiter on LinkedIn. Very interested in my experience "
            "with React and distributed systems. Prepare system design questions "
            "for technical interview."
        ),
    },
    {
        "id": 2,
        "company": "Microsoft",
        "title": "Product Manager",
        "status": "APPLIED",
        "deadline_at": "2025-11-20",
        "interview_at": None,
        "links": {"job_post": "https://careers.microsoft.com/jobs/23456"},
        "location": "Redmond, WA",
        "job_type": "Full-time",
        "salary": "$140k - $190k",
        "job_link": "https://careers.microsoft.com/jobs/23456",
        "experience": "3-5 years",
        "created_at": "2025-10-26",
        "applied_at": "2025-10-29",
        "documents": [],
        "notes": "",
    },
    {
        "id": 3,
        "company": "Amazon",
        "title": "Data Scientist",
        "status": "DRAFT",
        "deadline_at": "2025-11-18",
        "interview_at": None,
        "links": {"job_post": "https://amazon.jobs/en/jobs/34567"},
        "location": "Seattle, WA",
        "job_type": "Full-time",
        "salary": "$130k - $175k",
        "job_link": "https://amazon.jobs/en/jobs/34567",
        "experience": "2-4 years",
        "created_at": "2025-10-26",
        "applied_at": "2025-10-30",
        "documents": [],
        "notes": "",
    },
]

# Valid status values (from schema)
VALID_STATUSES = [
    "DRAFT",
    "APPLIED",
    "INTERVIEW",
    "OFFER",
    "REJECTED"
]

# Reminders storage (formerly events)
reminders = [
    {
        "id": 1,
        "kind": "INTERVIEW",  # DEADLINE, INTERVIEW, FOLLOWUP
        "title": "Google Interview",
        "application_id": 1,
        "trigger_at": "2025-11-15", # Was startDate
        "sent_at": None,
        "end_date": "2025-11-15",
        "start_time": "14:00",
        "end_time": "15:00",
        "location": "Mountain View Office",
        "meeting_link": "https://meet.google.com/abc-defg-hij",
        "notes": "Technical interview round 2",
        "color": "green",
        "created_at": "2025-10-28"
    },
    {
        "id": 2,
        "kind": "DEADLINE", # Was application type
        "title": "Microsoft Application Deadline",
        "application_id": 2,
        "trigger_at": "2025-11-20", # Was deadline
        "sent_at": None,
        "notes": "",
        "color": "red",
        "created_at": "2025-10-29"
    },
]

# Reminder ID counter
next_reminder_id = 3


def _find_reminder(reminder_id: int):
    return next((r for r in reminders if r["id"] == reminder_id), None)


def _get_reminder_color(kind: str):
    """Get default color based on reminder kind"""
    colors = {
        "DEADLINE": "red",
        "INTERVIEW": "green",
        "FOLLOWUP": "orange"
    }
    return colors.get(kind, "blue")


def _find_application(app_id: int):
    return next((a for a in applications if a["id"] == app_id), None)


def _auto_update_application_status(app_id: int):
    """Auto-update application status based on reminders"""
    app = _find_application(app_id)
    if not app:
        return None
    
    # Get all reminders for this application
    app_reminders = [r for r in reminders if r.get("application_id") == app_id]
    
    new_status = None
    
    if not app_reminders:
        # No reminders, set to DRAFT or APPLIED based on applied_at
        if app.get("applied_at"):
            new_status = "APPLIED"
        else:
            new_status = "DRAFT"
    else:
        # Check for offer/rejection keywords in titles or notes
        has_offer = False
        has_rejection = False
        
        for r in app_reminders:
            title_lower = (r.get("title") or "").lower()
            notes_lower = (r.get("notes") or "").lower()
            
            # Check for offer keywords
            if any(keyword in title_lower or keyword in notes_lower 
                   for keyword in ["offer", "accepted", "hired"]):
                has_offer = True
            
            # Check for rejection keywords
            if any(keyword in title_lower or keyword in notes_lower 
                   for keyword in ["reject", "declined", "not selected", "unsuccessful"]):
                has_rejection = True
        
        # Set status based on findings (rejection takes precedence)
        if has_rejection:
            new_status = "REJECTED"
        elif has_offer:
            new_status = "OFFER"
        else:
            # Check for interview or assignment (DEADLINE)
            has_interview = any(r.get("kind") == "INTERVIEW" for r in app_reminders)
            
            if has_interview:
                new_status = "INTERVIEW"
            elif app.get("applied_at"):
                new_status = "APPLIED"
            else:
                new_status = "DRAFT"
    
    # Update status if it changed
    if new_status and new_status != app["status"]:
        app["status"] = new_status
    
    return new_status


def generate_calendar_reminders():
    """Generate calendar events in format expected by frontend calendar"""
    # Note: The frontend calendar might still expect 'date', 'color', 'title'.
    # We will produce a view model that the frontend can consume, using snake_case where possible
    # but adhering to what the calendar might need if we didn't update it fully yet.
    # BUT I will update the frontend to use 'trigger_at' instead of 'date'.
    
    calendar_items = []
    
    for r in reminders:
        kind = r.get("kind")
        
        if kind == "DEADLINE":
            # Deadline - single date
            calendar_items.append({
                "id": r["id"],
                "title": r.get("title", "Deadline"),
                "subtitle": "",
                "date": r.get("trigger_at"), # Front end will need update
                "color": r.get("color", "red"),
                "application_id": r.get("application_id"),
                "kind": "DEADLINE"
            })
        
        elif kind == "INTERVIEW":
            # Interview
            trigger_at = r.get("trigger_at")
            time_info = ""
            if r.get("start_time"):
                time_info = r["start_time"]
                if r.get("end_time"):
                    time_info += f" - {r['end_time']}"
            
            calendar_items.append({
                "id": r["id"],
                "title": r.get("title", "Interview"),
                "subtitle": time_info,
                "date": trigger_at,
                "color": r.get("color", "green"),
                "application_id": r.get("application_id"),
                "kind": "INTERVIEW"
            })
            
        elif kind == "FOLLOWUP":
            calendar_items.append({
                "id": r["id"],
                "title": r.get("title", "Follow Up"),
                "subtitle": "",
                "date": r.get("trigger_at"),
                "color": r.get("color", "orange"),
                "application_id": r.get("application_id"),
                "kind": "FOLLOWUP"
            })
    
    return calendar_items


# --- Static file routes -----------------------------------------------------


@app.route("/")
def index():
    """Serve the main index.html page"""
    return send_from_directory(WEB_DIR, "index.html")


@app.route("/<path:filename>")
def serve_static(filename):
    """Serve static files (HTML, CSS, JS) from the web directory"""
    return send_from_directory(WEB_DIR, filename)


# --- API routes -------------------------------------------------------------


@app.get("/api/apps")
def get_applications():
    return jsonify(applications)


@app.post("/api/apps")
def create_application():
    data = request.get_json() or {}
    if "company" not in data or "title" not in data:
        abort(400, "company and title are required")

    new_id = max((a["id"] for a in applications), default=0) + 1
    today_str = date.today().isoformat()

    new_app = {
        "id": new_id,
        "company": data.get("company", ""),
        "title": data.get("title", ""),
        "status": data.get("status", "DRAFT"),
        "deadline_at": data.get("deadline_at"),
        "interview_at": data.get("interview_at"),
        "links": data.get("links", {}),
        "location": data.get("location", ""),
        "job_type": data.get("job_type", "Full-time"),
        "salary": data.get("salary", ""),
        "job_link": data.get("job_link", ""),
        "experience": data.get("experience", ""),
        "created_at": today_str,
        "applied_at": data.get("applied_at", today_str),
        "documents": data.get("documents", []),
        "notes": data.get("notes", ""),
    }
    
    # Populate links from job_link if not present
    if not new_app["links"] and new_app["job_link"]:
        new_app["links"] = {"job_post": new_app["job_link"]}
        
    applications.append(new_app)
    return jsonify(new_app), 201


@app.get("/api/apps/<int:app_id>")
def get_application(app_id: int):
    app_obj = _find_application(app_id)
    if not app_obj:
        abort(404)
    return jsonify(app_obj)


@app.put("/api/apps/<int:app_id>")
def update_application(app_id: int):
    app_obj = _find_application(app_id)
    if not app_obj:
        abort(404)
    data = request.get_json() or {}
    # Shallow merge of known fields
    for key in [
        "company",
        "title",
        "status",
        "deadline_at",
        "interview_at",
        "links",
        "location",
        "job_type",
        "salary",
        "job_link",
        "experience",
        "documents",
        "notes",
    ]:
        if key in data:
            app_obj[key] = data[key]
    return jsonify(app_obj)


@app.put("/api/apps/<int:app_id>/status")
def update_application_status(app_id: int):
    """Update application status"""
    app_obj = _find_application(app_id)
    if not app_obj:
        abort(404)
    
    data = request.get_json() or {}
    new_status = data.get("status")
    
    if new_status not in VALID_STATUSES:
        abort(400, f"Invalid status. Must be one of: {', '.join(VALID_STATUSES)}")
    
    app_obj["status"] = new_status
    
    return jsonify(app_obj)


@app.get("/api/apps/calendar")
def get_calendar_reminders_route():
    """Generate and return calendar reminders from application data"""
    calendar_items = generate_calendar_reminders()
    return jsonify(calendar_items)


@app.get("/api/reminders")
def get_reminders():
    """Get all reminders"""
    return jsonify(reminders)


@app.post("/api/reminders")
def create_reminder():
    """Create a new reminder"""
    global next_reminder_id
    data = request.get_json() or {}
    
    kind = data.get("kind")
    if kind not in ["DEADLINE", "INTERVIEW", "FOLLOWUP"]:
        abort(400, "Invalid reminder kind. Must be: DEADLINE, INTERVIEW, or FOLLOWUP")
    
    if not data.get("trigger_at"):
        abort(400, "Reminders require a trigger_at date")
    
    new_reminder = {
        "id": next_reminder_id,
        "kind": kind,
        "title": data.get("title", ""),
        "application_id": data.get("application_id"),
        "notes": data.get("notes", ""),
        "color": data.get("color", _get_reminder_color(kind)),
        "created_at": date.today().isoformat(),
        "trigger_at": data.get("trigger_at"),
        "sent_at": data.get("sent_at"),
    }
    
    # Add type-specific fields
    if kind == "INTERVIEW":
        new_reminder["end_date"] = data.get("end_date", data.get("trigger_at"))
        new_reminder["start_time"] = data.get("start_time", "")
        new_reminder["end_time"] = data.get("end_time", "")
        new_reminder["location"] = data.get("location", "")
        new_reminder["meeting_link"] = data.get("meeting_link", "")
    
    reminders.append(new_reminder)
    next_reminder_id += 1
    
    # Auto-update application status if linked to an application
    if new_reminder.get("application_id"):
        _auto_update_application_status(new_reminder["application_id"])
    
    return jsonify(new_reminder), 201


@app.get("/api/reminders/<int:reminder_id>")
def get_reminder(reminder_id: int):
    """Get a specific reminder"""
    reminder = _find_reminder(reminder_id)
    if not reminder:
        abort(404)
    return jsonify(reminder)


@app.put("/api/reminders/<int:reminder_id>")
def update_reminder(reminder_id: int):
    """Update an existing reminder"""
    reminder = _find_reminder(reminder_id)
    if not reminder:
        abort(404)
    
    data = request.get_json() or {}
    
    # Update common fields
    for key in ["title", "application_id", "notes", "color", "trigger_at", "sent_at"]:
        if key in data:
            reminder[key] = data[key]
    
    # Update kind-specific fields
    if reminder["kind"] == "INTERVIEW":
        for key in ["end_date", "start_time", "end_time", "location", "meeting_link"]:
            if key in data:
                reminder[key] = data[key]
    
    # Auto-update application status if linked to an application
    if reminder.get("application_id"):
        _auto_update_application_status(reminder["application_id"])
    
    return jsonify(reminder)


@app.delete("/api/reminders/<int:reminder_id>")
def delete_reminder(reminder_id: int):
    """Delete a reminder"""
    reminder = _find_reminder(reminder_id)
    if not reminder:
        abort(404)
    
    app_id = reminder.get("application_id")
    reminders.remove(reminder)
    
    # Auto-update application status after deletion
    if app_id:
        _auto_update_application_status(app_id)
    
    return jsonify({"success": True, "message": "Reminder deleted"}), 200


@app.get("/api/apps/<int:app_id>/reminders")
def get_application_reminders(app_id: int):
    """Get all reminders for a specific application"""
    app_reminders = [r for r in reminders if r.get("application_id") == app_id]
    return jsonify(app_reminders)


@app.get("/api/dashboard-summary")
def get_dashboard_summary():
    total = len(applications)
    by_status = {}
    for a in applications:
        by_status[a["status"]] = by_status.get(a["status"], 0) + 1
    return jsonify({"totalApplications": total, "byStatus": by_status})


@app.get("/api/user/integrations")
def get_user_integrations():
    """Get user's integration settings"""
    return jsonify(user_data["integrations"])


@app.put("/api/user/integrations")
def update_user_integrations():
    """Update user's integration settings (supports partial updates)"""
    data = request.get_json() or {}
    
    # Update Google integration if provided
    if "google" in data:
        google_data = data["google"]
        user_data["integrations"]["google"].update({
            k: v for k, v in google_data.items() 
            if k in user_data["integrations"]["google"]
        })
    
    # Update OpenAI integration if provided
    if "openai" in data:
        openai_data = data["openai"]
        user_data["integrations"]["openai"].update({
            k: v for k, v in openai_data.items() 
            if k in user_data["integrations"]["openai"]
        })
    
    return jsonify(user_data["integrations"])


@app.get("/api/user/integrations/google")
def get_google_integration():
    """Get Google integration settings"""
    return jsonify(user_data["integrations"]["google"])


@app.put("/api/user/integrations/google")
def update_google_integration():
    """Update Google integration settings"""
    data = request.get_json() or {}
    user_data["integrations"]["google"].update({
        k: v for k, v in data.items() 
        if k in user_data["integrations"]["google"]
    })
    return jsonify(user_data["integrations"]["google"])


@app.delete("/api/user/integrations/google")
def delete_google_integration():
    """Reset Google integration to defaults"""
    user_data["integrations"]["google"] = {
        "enabled": False,
        "email": "",
        "clientId": "",
        "clientSecret": "",
        "access_token": "",
        "refresh_token": "",
        "expires_at": "",
        "gmailEnabled": True,
        "calendarEnabled": True,
    }
    return jsonify(user_data["integrations"]["google"])


@app.post("/api/user/integrations/google/validate")
def validate_google_integration():
    """Validate Google OAuth credentials structure"""
    data = request.get_json() or {}
    
    email = data.get("email", "")
    client_id = data.get("clientId", "")
    client_secret = data.get("clientSecret", "")
    
    # Validate email format
    import re
    email_regex = r'^[^\s@]+@[^\s@]+\.[^\s@]+$'
    if not re.match(email_regex, email):
        return jsonify({"valid": False, "error": "Invalid email format"}), 400
    
    # Validate Google Client ID format
    if not client_id or len(client_id) < 20 or ".apps.googleusercontent.com" not in client_id:
        return jsonify({"valid": False, "error": "Invalid Client ID format"}), 400
    
    # Validate Google Client Secret format
    if not client_secret or len(client_secret) < 24:
        return jsonify({"valid": False, "error": "Invalid Client Secret format"}), 400
    
    return jsonify({
        "valid": True,
        "message": "Credentials format is valid"
    })


@app.get("/api/user/integrations/openai")
def get_openai_integration():
    """Get OpenAI integration settings"""
    return jsonify(user_data["integrations"]["openai"])


@app.put("/api/user/integrations/openai")
def update_openai_integration():
    """Update OpenAI integration settings"""
    data = request.get_json() or {}
    user_data["integrations"]["openai"].update({
        k: v for k, v in data.items() 
        if k in user_data["integrations"]["openai"]
    })
    return jsonify(user_data["integrations"]["openai"])


@app.delete("/api/user/integrations/openai")
def delete_openai_integration():
    """Reset OpenAI integration to defaults"""
    user_data["integrations"]["openai"] = {
        "enabled": False,
        "apiKey": "",
        "model": "",
    }
    return jsonify(user_data["integrations"]["openai"])


@app.get("/api/user")
def get_user_data():
    """Get user profile data"""
    return jsonify({
        "id": user_data["id"],
        "email": user_data["email"],
        "username": user_data["username"],
        "role": user_data["role"],
    })


if __name__ == "__main__":
    app.run(debug=True)
