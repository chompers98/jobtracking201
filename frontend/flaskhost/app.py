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
    "integrations": {
        "google": {
            "enabled": False,
            "email": "",
            "clientId": "",
            "clientSecret": "",
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
        "status": "Pending Interview / Assignment",
        "deadline": "2025-11-15",
        "location": "Mountain View, CA",
        "jobType": "Full-time",
        "salaryRange": "$120k - $180k",
        "jobLink": "https://careers.google.com/jobs/12345",
        "experience": "3-5 years",
        "createdAt": "2025-10-25",
        "appliedAt": "2025-10-28",
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
        "status": "Under Review",
        "deadline": "2025-11-20",
        "location": "Redmond, WA",
        "jobType": "Full-time",
        "salaryRange": "$140k - $190k",
        "jobLink": "https://careers.microsoft.com/jobs/23456",
        "experience": "3-5 years",
        "createdAt": "2025-10-26",
        "appliedAt": "2025-10-29",
        "documents": [],
        "notes": "",
    },
    {
        "id": 3,
        "company": "Amazon",
        "title": "Data Scientist",
        "status": "Pending Apply",
        "deadline": "2025-11-18",
        "location": "Seattle, WA",
        "jobType": "Full-time",
        "salaryRange": "$130k - $175k",
        "jobLink": "https://amazon.jobs/en/jobs/34567",
        "experience": "2-4 years",
        "createdAt": "2025-10-26",
        "appliedAt": "2025-10-30",
        "documents": [],
        "notes": "",
    },
]

# Valid status values
VALID_STATUSES = [
    "Pending Apply",
    "Under Review",
    "Pending Interview / Assignment",
    "Offered",
    "Rejected"
]

# Events storage
events = [
    {
        "id": 1,
        "type": "interview",  # application, interview, assignment
        "title": "Google Interview",
        "applicationId": 1,
        "startDate": "2025-11-15",
        "endDate": "2025-11-15",
        "startTime": "14:00",
        "endTime": "15:00",
        "location": "Mountain View Office",
        "meetingLink": "https://meet.google.com/abc-defg-hij",
        "notes": "Technical interview round 2",
        "color": "green",
        "createdAt": "2025-10-28"
    },
    {
        "id": 2,
        "type": "application",
        "title": "Microsoft Application Deadline",
        "applicationId": 2,
        "deadline": "2025-11-20",
        "notes": "",
        "color": "red",
        "createdAt": "2025-10-29"
    },
]

# Event ID counter
next_event_id = 3


def _find_event(event_id: int):
    return next((e for e in events if e["id"] == event_id), None)


def _get_event_color(event_type: str):
    """Get default color based on event type"""
    colors = {
        "application": "red",
        "interview": "green",
        "assignment": "orange"
    }
    return colors.get(event_type, "blue")


def _auto_update_application_status(app_id: int):
    """Auto-update application status based on events"""
    app = _find_application(app_id)
    if not app:
        return None
    
    # Get all events for this application
    app_events = [e for e in events if e.get("applicationId") == app_id]
    
    new_status = None
    
    if not app_events:
        # No events, set to Pending Apply or Under Review based on appliedAt
        if app.get("appliedAt"):
            new_status = "Under Review"
        else:
            new_status = "Pending Apply"
    else:
        # Check for offer/rejection keywords in event titles or notes
        has_offer = False
        has_rejection = False
        
        for event in app_events:
            title_lower = (event.get("title") or "").lower()
            notes_lower = (event.get("notes") or "").lower()
            
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
            new_status = "Rejected"
        elif has_offer:
            new_status = "Offered"
        else:
            # Check for interview or assignment events
            has_interview = any(e.get("type") == "interview" for e in app_events)
            has_assignment = any(e.get("type") == "assignment" for e in app_events)
            
            if has_interview or has_assignment:
                new_status = "Pending Interview / Assignment"
            elif app.get("appliedAt"):
                new_status = "Under Review"
            else:
                new_status = "Pending Apply"
    
    # Update status if it changed
    if new_status and new_status != app["status"]:
        app["status"] = new_status
    
    return new_status


def generate_calendar_events():
    """Generate calendar events in format expected by frontend calendar"""
    calendar_events = []
    
    for event in events:
        event_type = event.get("type")
        
        if event_type == "application":
            # Application deadline - single date
            calendar_events.append({
                "id": event["id"],
                "title": event.get("title", "Application Deadline"),
                "subtitle": "",
                "date": event.get("deadline"),
                "color": event.get("color", "red"),
                "applicationId": event.get("applicationId"),
                "type": "application"
            })
        
        elif event_type == "interview":
            # Interview - has date range and time
            start_date = event.get("startDate")
            time_info = ""
            if event.get("startTime"):
                time_info = event["startTime"]
                if event.get("endTime"):
                    time_info += f" - {event['endTime']}"
            
            calendar_events.append({
                "id": event["id"],
                "title": event.get("title", "Interview"),
                "subtitle": time_info,
                "date": start_date,
                "color": event.get("color", "green"),
                "applicationId": event.get("applicationId"),
                "type": "interview"
            })
        
        elif event_type == "assignment":
            # Assignment - has deadline
            calendar_events.append({
                "id": event["id"],
                "title": event.get("title", "Assignment Due"),
                "subtitle": "",
                "date": event.get("deadline"),
                "color": event.get("color", "orange"),
                "applicationId": event.get("applicationId"),
                "type": "assignment"
            })
    
    return calendar_events


def _find_application(app_id: int):
    return next((a for a in applications if a["id"] == app_id), None)


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
        "status": data.get("status", "Pending Apply"),
        "deadline": data.get("deadline"),
        "location": data.get("location", ""),
        "jobType": data.get("jobType", "Full-time"),
        "salaryRange": data.get("salaryRange", ""),
        "jobLink": data.get("jobLink", ""),
        "experience": data.get("experience", ""),
        "createdAt": today_str,
        "appliedAt": data.get("appliedAt", today_str),
        "documents": data.get("documents", []),
        "notes": data.get("notes", ""),
    }
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
        "deadline",
        "location",
        "jobType",
        "salaryRange",
        "jobLink",
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
def get_calendar_events():
    """Generate and return calendar events from application data"""
    calendar_events = generate_calendar_events()
    return jsonify(calendar_events)


@app.get("/api/events")
def get_events():
    """Get all events"""
    return jsonify(events)


@app.post("/api/events")
def create_event():
    """Create a new event"""
    global next_event_id
    data = request.get_json() or {}
    
    event_type = data.get("type")
    if event_type not in ["application", "interview", "assignment"]:
        abort(400, "Invalid event type. Must be: application, interview, or assignment")
    
    # Validate required fields based on type
    if event_type == "application":
        if not data.get("deadline"):
            abort(400, "Application events require a deadline")
    elif event_type == "interview":
        if not data.get("startDate"):
            abort(400, "Interview events require a startDate")
    elif event_type == "assignment":
        if not data.get("deadline"):
            abort(400, "Assignment events require a deadline")
    
    new_event = {
        "id": next_event_id,
        "type": event_type,
        "title": data.get("title", ""),
        "applicationId": data.get("applicationId"),
        "notes": data.get("notes", ""),
        "color": data.get("color", _get_event_color(event_type)),
        "createdAt": date.today().isoformat()
    }
    
    # Add type-specific fields
    if event_type == "application":
        new_event["deadline"] = data.get("deadline")
    elif event_type == "interview":
        new_event["startDate"] = data.get("startDate")
        new_event["endDate"] = data.get("endDate", data.get("startDate"))
        new_event["startTime"] = data.get("startTime", "")
        new_event["endTime"] = data.get("endTime", "")
        new_event["location"] = data.get("location", "")
        new_event["meetingLink"] = data.get("meetingLink", "")
    elif event_type == "assignment":
        new_event["deadline"] = data.get("deadline")
    
    events.append(new_event)
    next_event_id += 1
    
    # Auto-update application status if linked to an application
    if new_event.get("applicationId"):
        _auto_update_application_status(new_event["applicationId"])
    
    return jsonify(new_event), 201


@app.get("/api/events/<int:event_id>")
def get_event(event_id: int):
    """Get a specific event"""
    event = _find_event(event_id)
    if not event:
        abort(404)
    return jsonify(event)


@app.put("/api/events/<int:event_id>")
def update_event(event_id: int):
    """Update an existing event"""
    event = _find_event(event_id)
    if not event:
        abort(404)
    
    data = request.get_json() or {}
    
    # Update common fields
    for key in ["title", "applicationId", "notes", "color"]:
        if key in data:
            event[key] = data[key]
    
    # Update type-specific fields based on event type
    if event["type"] == "application":
        if "deadline" in data:
            event["deadline"] = data["deadline"]
    elif event["type"] == "interview":
        for key in ["startDate", "endDate", "startTime", "endTime", "location", "meetingLink"]:
            if key in data:
                event[key] = data[key]
    elif event["type"] == "assignment":
        if "deadline" in data:
            event["deadline"] = data["deadline"]
    
    # Auto-update application status if linked to an application
    if event.get("applicationId"):
        _auto_update_application_status(event["applicationId"])
    
    return jsonify(event)


@app.delete("/api/events/<int:event_id>")
def delete_event(event_id: int):
    """Delete an event"""
    event = _find_event(event_id)
    if not event:
        abort(404)
    
    app_id = event.get("applicationId")
    events.remove(event)
    
    # Auto-update application status after deletion
    if app_id:
        _auto_update_application_status(app_id)
    
    return jsonify({"success": True, "message": "Event deleted"}), 200


@app.get("/api/apps/<int:app_id>/events")
def get_application_events(app_id: int):
    """Get all events for a specific application"""
    app_events = [e for e in events if e.get("applicationId") == app_id]
    return jsonify(app_events)


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
    
    # In a real application, you would:
    # 1. Attempt to exchange credentials with Google OAuth
    # 2. Verify the credentials are actually valid
    # For this demo, we'll just validate the format
    
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
    })


if __name__ == "__main__":
    app.run(debug=True)


