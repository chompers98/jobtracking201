## JobTracker Mock Application

This project is a lightweight implementation of the **JobTracker** UI shown in the provided screenshots.  
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
- **Calendar (`calendar.html`)**: calendar rendering events from `/api/apps/calendar`.
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
    {
      "label": "Resume_2025.pdf",
      "url": "#"
    }
  ],
  "notes": "Interview preparation notes..."
}
```

**Valid Status Values:**
- `"Pending Apply"`
- `"Under Review"`
- `"Pending Interview / Assignment"`
- `"Offered"`
- `"Rejected"`

#### Event Object

Events can be one of three types: `application`, `interview`, or `assignment`.

**Common Fields (all event types):**
```json
{
  "id": 1,
  "type": "interview",
  "title": "Google Interview",
  "applicationId": 1,
  "notes": "Technical interview round 2",
  "color": "green",
  "createdAt": "2025-10-28"
}
```

**Type-Specific Fields:**

*Application event (deadline-based):*
```json
{
  "type": "application",
  "deadline": "2025-11-20"
}
```

*Interview event (date/time-based):*
```json
{
  "type": "interview",
  "startDate": "2025-11-15",
  "endDate": "2025-11-15",
  "startTime": "14:00",
  "endTime": "15:00",
  "location": "Mountain View Office",
  "meetingLink": "https://meet.google.com/abc-defg-hij"
}
```

*Assignment event (deadline-based):*
```json
{
  "type": "assignment",
  "deadline": "2025-11-22"
}
```

**Valid Event Types:**
- `"application"` - color default: `red`
- `"interview"` - color default: `green`
- `"assignment"` - color default: `orange`

#### Calendar Event Object

Simplified event format for calendar display:

```json
{
  "id": 1,
  "title": "Google Interview",
  "subtitle": "14:00 - 15:00",
  "date": "2025-11-15",
  "color": "green",
  "applicationId": 1,
  "type": "interview"
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
    "calendarEnabled": true
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
  "username": "John Doe"
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
  "status": "Pending Apply",    // optional, defaults to "Pending Apply"
  "deadline": "2025-11-15",     // optional
  "location": "Mountain View, CA",
  "jobType": "Full-time",       // optional, defaults to "Full-time"
  "salaryRange": "$120k - $180k",
  "jobLink": "https://...",
  "experience": "3-5 years",
  "appliedAt": "2025-10-28",    // optional, defaults to today
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
  "createdAt": "2025-11-30"
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
  "status": "Offered",
  "deadline": "2025-12-01",
  "location": "San Francisco, CA",
  "jobType": "Full-time",
  "salaryRange": "$150k - $200k",
  "jobLink": "https://...",
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
  "status": "Offered"
}
```

**Response:** `200 OK`
```json
{
  "id": 1,
  "status": "Offered",
  ...
}
```

**Error:** 
- `404 Not Found` if application doesn't exist
- `400 Bad Request` if status is not valid

---

##### `GET /api/apps/:id/events`

Get all events for a specific application.

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "type": "interview",
    "title": "Google Interview",
    "applicationId": 1,
    ...
  }
]
```

---

##### `GET /api/apps/calendar`

Get all events formatted for calendar display.

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "title": "Google Interview",
    "subtitle": "14:00 - 15:00",
    "date": "2025-11-15",
    "color": "green",
    "applicationId": 1,
    "type": "interview"
  }
]
```

---

#### Events

##### `GET /api/events`

Get all events.

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "type": "interview",
    "title": "Google Interview",
    ...
  }
]
```

---

##### `POST /api/events`

Create a new event. Automatically updates the linked application's status.

**Request Body (Application event):**
```json
{
  "type": "application",        // required
  "title": "Application Deadline", // optional
  "applicationId": 1,           // optional
  "deadline": "2025-11-20",     // required for application events
  "notes": "",                  // optional
  "color": "red"                // optional, defaults based on type
}
```

**Request Body (Interview event):**
```json
{
  "type": "interview",
  "title": "Technical Interview",
  "applicationId": 1,
  "startDate": "2025-11-15",    // required for interview events
  "endDate": "2025-11-15",      // optional, defaults to startDate
  "startTime": "14:00",         // optional
  "endTime": "15:00",           // optional
  "location": "Office",         // optional
  "meetingLink": "https://...", // optional
  "notes": "",
  "color": "green"
}
```

**Request Body (Assignment event):**
```json
{
  "type": "assignment",
  "title": "Take-home Assignment",
  "applicationId": 1,
  "deadline": "2025-11-22",     // required for assignment events
  "notes": "",
  "color": "orange"
}
```

**Response:** `201 Created`
```json
{
  "id": 3,
  "type": "interview",
  ...
  "createdAt": "2025-11-30"
}
```

**Error:** 
- `400 Bad Request` if type is invalid or required fields are missing

---

##### `GET /api/events/:id`

Get a specific event by ID.

**Response:** `200 OK`
```json
{
  "id": 1,
  "type": "interview",
  ...
}
```

**Error:** `404 Not Found` if event doesn't exist.

---

##### `PUT /api/events/:id`

Update an event. Supports partial updates. Automatically updates the linked application's status.

**Request Body:**
```json
{
  "title": "Updated Title",
  "applicationId": 2,
  "notes": "Updated notes",
  "color": "blue",
  // Include type-specific fields as needed
  "startDate": "2025-11-16",
  "startTime": "15:00"
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

**Error:** `404 Not Found` if event doesn't exist.

---

##### `DELETE /api/events/:id`

Delete an event. Automatically updates the linked application's status.

**Response:** `200 OK`
```json
{
  "success": true,
  "message": "Event deleted"
}
```

**Error:** `404 Not Found` if event doesn't exist.

---

#### Dashboard

##### `GET /api/dashboard-summary`

Get summary statistics for the dashboard.

**Response:** `200 OK`
```json
{
  "totalApplications": 3,
  "byStatus": {
    "Pending Apply": 1,
    "Under Review": 1,
    "Pending Interview / Assignment": 1,
    "Offered": 0,
    "Rejected": 0
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
  "username": "John Doe"
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

The backend automatically updates application statuses based on events:

1. **No events**: Status set to `"Pending Apply"` (if not applied) or `"Under Review"` (if applied)
2. **Interview/Assignment events exist**: Status set to `"Pending Interview / Assignment"`
3. **Offer keywords detected** (in event title/notes): Status set to `"Offered"`
4. **Rejection keywords detected** (in event title/notes): Status set to `"Rejected"`

Auto-update triggers:
- When creating a new event (`POST /api/events`)
- When updating an event (`PUT /api/events/:id`)
- When deleting an event (`DELETE /api/events/:id`)

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


