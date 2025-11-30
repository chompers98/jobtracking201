## JobTracker Mock Application

This project is a lightweight implementation of the **JobTracker** UI.  
It uses:

- **Front end**: static HTML/CSS/JavaScript in the `web` folder (no framework)
- **Back end**: a minimal Flask API with in‑memory mock data in the `flaskhost` folder

The login page is intentionally left for a different teammate; all other pages (dashboard, applications list, application detail, calendar view, and create new application) are implemented.

---

## Setup Instructions

### 1. Set up and run the Flask backend

From the project root:

```bash
cd flaskhost
python3 -m venv venv
source venv/bin/activate  # On Windows use: venv\Scripts\activate
pip install -r requirements.txt
python app.py
```

The API will be available at `http://localhost:5000`.

### 2. Open the front end

Open `web/index.html` directly in a browser, or serve the `web` folder with a simple static server, e.g.:

```bash
cd web
python -m http.server 8000
```

Then visit `http://localhost:8000/index.html`.

### 3. Implemented pages

- **Dashboard (`dashboard.html`)**: small summary cards powered by `/api/dashboard-summary`.
- **Applications (`index.html`)**: table of applications with client-side search and filters; rows link to details.
- **Application Detail (`application_detail.html`)**: job details, timeline, notes, and documents using `/api/apps/:id`.
- **Calendar (`calendar.html`)**: calendar rendering reminders from `/api/apps/calendar`.
- **Create New Application (`new_application.html`)**: form that POSTs to `/api/apps` and then redirects back to the list.
- **Integrations (`integrations.html`)**: manage Google and OpenAI integrations.
- **Settings (`settings.html`)**: user profile and settings.

Most of the behavior (filtering, search, rendering, and basic validation) is implemented fully on the front end using `web/app.js`.  
The Flask backend only simulates persistence with simple in‑memory Python lists.

---

## API Documentation

Base URL: `http://localhost:5000`

### Data Models

#### Application Object

```json
{
  "id": 1,
  "company": "Google",
  "title": "Software Engineer",
  "status": "INTERVIEW",
  "deadline_at": "2025-11-15",
  "interview_at": "2025-11-15T14:00:00",
  "location": "Mountain View, CA",
  "job_type": "Full-time",
  "salary": "$120k - $180k",
  "job_link": "https://careers.google.com/jobs/12345",
  "links": {
    "job_post": "https://careers.google.com/jobs/12345"
  },
  "experience": "3-5 years",
  "created_at": "2025-10-25",
  "applied_at": "2025-10-28",
  "documents": [
    {
      "label": "Resume_2025.pdf",
      "url": "#"
    }
  ],
  "notes": "Interview preparation notes..."
}
```

**Valid Status Values:**
- `"DRAFT"`
- `"APPLIED"`
- `"INTERVIEW"`
- `"OFFER"`
- `"REJECTED"`

#### Reminder Object (formerly Event)

Reminders can be one of three kinds: `DEADLINE`, `INTERVIEW`, or `FOLLOWUP`.

**Common Fields (all reminder types):**
```json
{
  "id": 1,
  "kind": "INTERVIEW",
  "title": "Google Interview",
  "application_id": 1,
  "notes": "Technical interview round 2",
  "color": "green",
  "created_at": "2025-10-28",
  "trigger_at": "2025-11-15",
  "sent_at": null
}
```

**Type-Specific Fields:**

*Deadline reminder (deadline-based):*
```json
{
  "kind": "DEADLINE",
  "trigger_at": "2025-11-20"
}
```

*Interview reminder (date/time-based):*
```json
{
  "kind": "INTERVIEW",
  "trigger_at": "2025-11-15",
  "end_date": "2025-11-15",
  "start_time": "14:00",
  "end_time": "15:00",
  "location": "Mountain View Office",
  "meeting_link": "https://meet.google.com/abc-defg-hij"
}
```

*Follow-up reminder:*
```json
{
  "kind": "FOLLOWUP",
  "trigger_at": "2025-11-22"
}
```

**Valid Reminder Kinds:**
- `"DEADLINE"` - color default: `red`
- `"INTERVIEW"` - color default: `green`
- `"FOLLOWUP"` - color default: `orange`

#### Calendar Reminder Object

Simplified format for calendar display:

```json
{
  "id": 1,
  "title": "Google Interview",
  "subtitle": "14:00 - 15:00",
  "date": "2025-11-15",
  "color": "green",
  "application_id": 1,
  "kind": "INTERVIEW"
}
```

#### User Integration Object

```json
{
  "google": {
    "enabled": false,
    "email": "",
    "clientId": "",
    "clientSecret": "",
    "gmailEnabled": true,
    "calendarEnabled": true,
    "access_token": "",
    "refresh_token": "",
    "expires_at": ""
  },
  "openai": {
    "enabled": false,
    "apiKey": "",
    "model": ""
  }
}
```

#### User Object

```json
{
  "id": 1,
  "email": "user@example.com",
  "username": "John Doe",
  "role": "USER"
}
```

---

### API Endpoints

#### Applications

##### `GET /api/apps`

Get all applications.

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "company": "Google",
    "title": "Software Engineer",
    ...
  }
]
```

---

##### `POST /api/apps`

Create a new application.

**Request Body:**
```json
{
  "company": "Google",          // required
  "title": "Software Engineer", // required
  "status": "APPLIED",          // optional, defaults to "DRAFT"
  "deadline_at": "2025-11-15",  // optional
  "location": "Mountain View, CA",
  "job_type": "Full-time",      // optional, defaults to "Full-time"
  "salary": "$120k - $180k",
  "job_link": "https://...",
  "experience": "3-5 years",
  "applied_at": "2025-10-28",   // optional, defaults to today
  "documents": [],              // optional, defaults to []
  "notes": ""                   // optional, defaults to ""
}
```

**Response:** `201 Created`
```json
{
  "id": 4,
  "company": "Google",
  ...
  "created_at": "2025-11-30"
}
```

**Error:** `400 Bad Request` if `company` or `title` is missing.

---

##### `GET /api/apps/:id`

Get a specific application by ID.

**Response:** `200 OK`
```json
{
  "id": 1,
  "company": "Google",
  ...
}
```

**Error:** `404 Not Found` if application doesn't exist.

---

##### `PUT /api/apps/:id`

Update an application. Supports partial updates.

**Request Body:**
```json
{
  "company": "Google Inc.",
  "title": "Senior Software Engineer",
  "status": "OFFER",
  "deadline_at": "2025-12-01",
  "location": "San Francisco, CA",
  "job_type": "Full-time",
  "salary": "$150k - $200k",
  "links": { "job_post": "https://..." },
  "experience": "5+ years",
  "documents": [...],
  "notes": "Updated notes"
}
```

**Response:** `200 OK`
```json
{
  "id": 1,
  "company": "Google Inc.",
  ...
}
```

**Error:** `404 Not Found` if application doesn't exist.

---

##### `PUT /api/apps/:id/status`

Update only the status of an application.

**Request Body:**
```json
{
  "status": "OFFER"
}
```

**Response:** `200 OK`
```json
{
  "id": 1,
  "status": "OFFER",
  ...
}
```

**Error:** 
- `404 Not Found` if application doesn't exist
- `400 Bad Request` if status is not valid

---

##### `GET /api/apps/:id/reminders`

Get all reminders for a specific application.

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "kind": "INTERVIEW",
    "title": "Google Interview",
    "application_id": 1,
    ...
  }
]
```

---

##### `GET /api/apps/calendar`

Get all reminders formatted for calendar display.

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "title": "Google Interview",
    "subtitle": "14:00 - 15:00",
    "date": "2025-11-15",
    "color": "green",
    "application_id": 1,
    "kind": "INTERVIEW"
  }
]
```

---

#### Reminders (formerly Events)

##### `GET /api/reminders`

Get all reminders.

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "kind": "INTERVIEW",
    "title": "Google Interview",
    ...
  }
]
```

---

##### `POST /api/reminders`

Create a new reminder. Automatically updates the linked application's status.

**Request Body (Deadline reminder):**
```json
{
  "kind": "DEADLINE",           // required
  "title": "Application Deadline", // optional
  "application_id": 1,          // optional
  "trigger_at": "2025-11-20",   // required
  "notes": "",                  // optional
  "color": "red"                // optional, defaults based on kind
}
```

**Request Body (Interview reminder):**
```json
{
  "kind": "INTERVIEW",
  "title": "Technical Interview",
  "application_id": 1,
  "trigger_at": "2025-11-15",   // required
  "end_date": "2025-11-15",     // optional
  "start_time": "14:00",        // optional
  "end_time": "15:00",          // optional
  "location": "Office",         // optional
  "meeting_link": "https://...", // optional
  "notes": "",
  "color": "green"
}
```

**Response:** `201 Created`
```json
{
  "id": 3,
  "kind": "INTERVIEW",
  ...
  "created_at": "2025-11-30"
}
```

**Error:** 
- `400 Bad Request` if kind is invalid or required fields are missing

---

##### `GET /api/reminders/:id`

Get a specific reminder by ID.

**Response:** `200 OK`
```json
{
  "id": 1,
  "kind": "INTERVIEW",
  ...
}
```

**Error:** `404 Not Found` if reminder doesn't exist.

---

##### `PUT /api/reminders/:id`

Update a reminder. Supports partial updates. Automatically updates the linked application's status.

**Request Body:**
```json
{
  "title": "Updated Title",
  "application_id": 2,
  "notes": "Updated notes",
  "color": "blue",
  "trigger_at": "2025-11-16",
  "start_time": "15:00"
}
```

**Response:** `200 OK`
```json
{
  "id": 1,
  "title": "Updated Title",
  ...
}
```

**Error:** `404 Not Found` if reminder doesn't exist.

---

##### `DELETE /api/reminders/:id`

Delete a reminder. Automatically updates the linked application's status.

**Response:** `200 OK`
```json
{
  "success": true,
  "message": "Reminder deleted"
}
```

**Error:** `404 Not Found` if reminder doesn't exist.

---

#### Dashboard

##### `GET /api/dashboard-summary`

Get summary statistics for the dashboard.

**Response:** `200 OK`
```json
{
  "totalApplications": 3,
  "byStatus": {
    "DRAFT": 1,
    "APPLIED": 1,
    "INTERVIEW": 1,
    "OFFER": 0,
    "REJECTED": 0
  }
}
```

---

#### User & Integrations

##### `GET /api/user`

Get current user profile.

**Response:** `200 OK`
```json
{
  "id": 1,
  "email": "user@example.com",
  "username": "John Doe",
  "role": "USER"
}
```

---

##### `GET /api/user/integrations`

Get all user integration settings.

**Response:** `200 OK`
```json
{
  "google": { ... },
  "openai": { ... }
}
```

---

##### `PUT /api/user/integrations`

Update user integration settings. Supports partial updates.

**Request Body:**
```json
{
  "google": {
    "enabled": true,
    "email": "user@gmail.com",
    "clientId": "xxx.apps.googleusercontent.com",
    "clientSecret": "xxx",
    "gmailEnabled": true,
    "calendarEnabled": true
  },
  "openai": {
    "enabled": true,
    "apiKey": "sk-xxx",
    "model": "gpt-4"
  }
}
```

**Response:** `200 OK`
```json
{
  "google": { ... },
  "openai": { ... }
}
```

---

##### `GET /api/user/integrations/google`

Get Google integration settings.

**Response:** `200 OK`
```json
{
  "enabled": false,
  "email": "",
  "clientId": "",
  "clientSecret": "",
  "gmailEnabled": true,
  "calendarEnabled": true
}
```

---

##### `PUT /api/user/integrations/google`

Update Google integration settings. Supports partial updates.

**Request Body:**
```json
{
  "enabled": true,
  "email": "user@gmail.com",
  "clientId": "xxx.apps.googleusercontent.com",
  "clientSecret": "xxx"
}
```

**Response:** `200 OK`

---

##### `DELETE /api/user/integrations/google`

Reset Google integration to defaults (disabled, empty fields).

**Response:** `200 OK`
```json
{
  "enabled": false,
  "email": "",
  ...
}
```

---

##### `POST /api/user/integrations/google/validate`

Validate Google OAuth credentials format.

**Request Body:**
```json
{
  "email": "user@gmail.com",
  "clientId": "xxx.apps.googleusercontent.com",
  "clientSecret": "xxx"
}
```

**Response:** `200 OK`
```json
{
  "valid": true,
  "message": "Credentials format is valid"
}
```

**Error:** `400 Bad Request`
```json
{
  "valid": false,
  "error": "Invalid email format"
}
```

---

##### `GET /api/user/integrations/openai`

Get OpenAI integration settings.

**Response:** `200 OK`
```json
{
  "enabled": false,
  "apiKey": "",
  "model": ""
}
```

---

##### `PUT /api/user/integrations/openai`

Update OpenAI integration settings. Supports partial updates.

**Request Body:**
```json
{
  "enabled": true,
  "apiKey": "sk-xxx",
  "model": "gpt-4"
}
```

**Response:** `200 OK`

---

##### `DELETE /api/user/integrations/openai`

Reset OpenAI integration to defaults (disabled, empty fields).

**Response:** `200 OK`
```json
{
  "enabled": false,
  "apiKey": "",
  "model": ""
}
```

---

### Auto-Status Updates

The backend automatically updates application statuses based on reminders:

1. **No reminders**: Status set to `"DRAFT"` (if not applied) or `"APPLIED"` (if applied)
2. **Interview reminders exist**: Status set to `"INTERVIEW"`
3. **Offer keywords detected** (in reminder title/notes): Status set to `"OFFER"`
4. **Rejection keywords detected** (in reminder title/notes): Status set to `"REJECTED"`

Auto-update triggers:
- When creating a new reminder (`POST /api/reminders`)
- When updating a reminder (`PUT /api/reminders/:id`)
- When deleting a reminder (`DELETE /api/reminders/:id`)

---

### Error Responses

All endpoints return appropriate HTTP status codes:

- `200 OK` - Successful GET, PUT, DELETE
- `201 Created` - Successful POST
- `400 Bad Request` - Invalid request data
- `404 Not Found` - Resource not found

Error response format:
```json
{
  "error": "Error message description"
}
```
