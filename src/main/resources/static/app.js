const API_BASE_URL = '';

// Initialize theme immediately to prevent flash
(function initTheme() {
    const savedTheme = localStorage.getItem('theme') ||
        (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
    document.documentElement.setAttribute('data-theme', savedTheme);
})();

// Check if user is authenticated
function checkAuthentication() {
    const jwtToken = localStorage.getItem('jwtToken');
    if (!jwtToken) {
        const currentPage = window.location.pathname;
        const isAuthPage = currentPage.includes('login') || currentPage.includes('register');
        if (!isAuthPage) {
            window.location.href = '/login.html';
        }
        return false;
    }
    return true;
}

async function handleLogout(event) {
    if (event) {
        event.preventDefault();
    }

    try {
        const jwtToken = localStorage.getItem('jwtToken');
        if (jwtToken) {
            await fetch('/api/auth/logout', {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${jwtToken}`,
                },
            });
        }
    } catch (error) {
        console.error('Logout error:', error);
    } finally {
        localStorage.removeItem('jwtToken');
        localStorage.removeItem('refreshToken');
        localStorage.removeItem('user');
        localStorage.removeItem('expiresAt');
        window.location.href = '/login.html';
    }
}

function updateUserDisplay() {
    const user = JSON.parse(localStorage.getItem('user') || '{}');
    const usernameDisplay = document.getElementById('username-display');
    const avatarDisplay = document.getElementById('avatar-display');

    if (usernameDisplay) {
        usernameDisplay.textContent = user.username || 'User';
    }

    if (avatarDisplay) {
        const initials = user.username
            ? user.username.substring(0, 2).toUpperCase()
            : 'U';
        avatarDisplay.textContent = initials;
    }
}

function bindLogoutButton() {
    const logoutBtn = document.getElementById('logout-btn');
    if (!logoutBtn || logoutBtn.dataset.bound === 'true') {
        return;
    }
    logoutBtn.dataset.bound = 'true';
    logoutBtn.addEventListener('click', handleLogout);
}

function setupAuthUI() {
    updateUserDisplay();
    bindLogoutButton();
}

async function loadNavbar() {
    const container = document.getElementById('navbar-container');
    if (!container) {
        return;
    }

    try {
        const response = await fetch('/navbar.html', { cache: 'no-cache' });
        if (!response.ok) {
            throw new Error(`Navbar request failed: ${response.status}`);
        }
        const markup = await response.text();
        container.innerHTML = markup;
        setupAuthUI();
    } catch (error) {
        console.error('Failed to load navbar:', error);
    }
}

// Check for token expiration
function isTokenExpired() {
    const expiresAt = localStorage.getItem('expiresAt');
    if (!expiresAt) return true;
    return Date.now() > parseInt(expiresAt);
}

async function fetchJson(path, options = {}) {
    // Check if user is authenticated before making requests
    const jwtToken = localStorage.getItem('jwtToken');

    // Check for token expiration
    if (jwtToken && isTokenExpired()) {
        // Token expired, clear and redirect to login
        localStorage.removeItem('jwtToken');
        localStorage.removeItem('refreshToken');
        localStorage.removeItem('user');
        localStorage.removeItem('expiresAt');
        window.location.href = '/login.html';
        return;
    }

    const headers = {
        "Content-Type": "application/json",
        ...options.headers
    };

    // Add JWT token to Authorization header if available
    if (jwtToken) {
        headers['Authorization'] = `Bearer ${jwtToken}`;
    }

    const res = await fetch(`${API_BASE_URL}${path}`, {
        ...options,
        headers,
    });

    // Handle 401 Unauthorized (token invalid/expired)
    if (res.status === 401) {
        localStorage.removeItem('jwtToken');
        localStorage.removeItem('refreshToken');
        localStorage.removeItem('user');
        localStorage.removeItem('expiresAt');
        window.location.href = '/login.html';
        return;
    }

    if (!res.ok) {
        throw new Error(`Request failed: ${res.status}`);
    }

    // Handle truly empty responses (DELETE with no body, 204 No Content)
    // Only return null if explicitly no content or explicitly stated as 0
    const contentLength = res.headers.get('content-length');
    if (res.status === 204 || contentLength === '0') {
        return null;  // Explicitly empty response (DELETE, 204)
    }

    // Parse JSON response - will work for all responses with content
    try {
        return res.json();
    } catch (e) {
        // If JSON parsing fails and no content-length, return null
        // Otherwise throw the error
        if (!contentLength || contentLength === '0') {
            return null;
        }
        throw e;
    }
}

// --- Status Helpers ----------------------------------------------------------

const STATUS_LABELS = {
    "DRAFT": "Pending Apply",
    "APPLIED": "Under Review",
    "INTERVIEW": "Interview",
    "OFFER": "Offered",
    "REJECTED": "Rejected"
};

function getStatusLabel(status) {
    return STATUS_LABELS[status] || status;
}

function renderStatusTag(status) {
    const map = {
        "DRAFT": "tag-pending",
        "APPLIED": "tag-reviewing",
        "INTERVIEW": "tag-interview",
        "OFFER": "tag-offer",
        "REJECTED": "tag-rejected",
    };
    const cls = map[status] || "tag-reviewing";
    return `<span class="status-chip ${cls}">${getStatusLabel(status)}</span>`;
}

function renderStatusDropdown(currentStatus, appId) {
    const statuses = [
        "DRAFT",
        "APPLIED",
        "INTERVIEW",
        "OFFER",
        "REJECTED"
    ];

    const options = statuses.map(status =>
        `<option value="${status}" ${status === currentStatus ? 'selected' : ''}>${getStatusLabel(status)}</option>`
    ).join('');

    return `
    <select class="status-dropdown" data-app-id="${appId}">
      ${options}
    </select>
  `;
}

// --- Applications list page --------------------------------------------------

async function initApplicationsPage() {
    const tableBody = document.querySelector("#applications-tbody");
    if (!tableBody) return;

    const searchInput = document.querySelector("#search-input");
    const filterButtons = document.querySelectorAll("[data-filter]");
    const countLabel = document.querySelector("#applications-count");

    let allApps = [];
    let currentFilter = "all";

    function statusIsActive(status) {
        return status !== "OFFER" && status !== "REJECTED";
    }

    function applyFilterAndRender() {
        const term = (searchInput?.value || "").toLowerCase();
        let filtered = allApps.filter((a) => {
            const matchTerm =
                !term ||
                a.company.toLowerCase().includes(term) ||
                a.title.toLowerCase().includes(term);
            const matchFilter =
                currentFilter === "all"
                    ? true
                    : currentFilter === "active"
                        ? statusIsActive(a.status)
                        : !statusIsActive(a.status);
            return matchTerm && matchFilter;
        });

        tableBody.innerHTML = "";
        filtered.forEach((app, idx) => {
            const tr = document.createElement("tr");

            tr.innerHTML = `
        <td>${app.company}</td>
        <td>${app.title}</td>
        <td>${app.location || "-"}</td>
        <td class="link-cell"><a href="application_detail.html?id=${app.id}">View</a></td>
        <td class="status-cell">${renderStatusTag(app.status)}</td>
      `;
            tableBody.appendChild(tr);
        });

        if (countLabel) {
            countLabel.textContent = `Showing ${filtered.length} of ${allApps.length}`;
        }
    }

    try {
        allApps = await fetchJson("/api/apps");
        if (searchInput) {
            searchInput.addEventListener("input", () => applyFilterAndRender());
        }
        filterButtons.forEach((btn) =>
            btn.addEventListener("click", () => {
                currentFilter = btn.dataset.filter || "all";
                filterButtons.forEach((b) => b.classList.remove("active"));
                btn.classList.add("active");
                applyFilterAndRender();
            })
        );
        applyFilterAndRender();
    } catch (err) {
        console.error(err);
    }
}

// --- Detail page -------------------------------------------------------------

async function initDetailPage() {
    const detailRoot = document.querySelector("#detail-root");
    if (!detailRoot) return;

    const params = new URLSearchParams(window.location.search);
    const id = params.get("id");
    if (!id) return;

    try {
        const app = await fetchJson(`/api/apps/${id}`);
        const appReminders = await fetchJson(`/api/apps/${id}/reminders`);

        const titleEl = document.querySelector("#detail-title");
        const companyEl = document.querySelector("#detail-company");
        const statusEl = document.querySelector("#detail-status-tag");
        const detailGrid = document.querySelector("#detail-grid");
        const timelineList = document.querySelector("#timeline-list");
        const notesTextarea = document.querySelector("#notes-textarea");
        const docsContainer = document.querySelector("#docs-container");

        if (titleEl) titleEl.textContent = app.title;
        if (companyEl) companyEl.textContent = app.company;
        if (statusEl) {
            statusEl.innerHTML = renderStatusDropdown(app.status, app.id);

            // Add event listener for status change
            const statusDropdown = statusEl.querySelector(".status-dropdown");
            if (statusDropdown) {
                statusDropdown.addEventListener("change", async (e) => {
                    const newStatus = e.target.value;
                    try {
                        await fetchJson(`/api/apps/${app.id}/status`, {
                            method: "PUT",
                            body: JSON.stringify({ status: newStatus }),
                        });
                        // Show success feedback
                        const originalBg = statusDropdown.style.backgroundColor;
                        statusDropdown.style.backgroundColor = "#d1fae5";
                        setTimeout(() => {
                            statusDropdown.style.backgroundColor = originalBg;
                        }, 500);
                    } catch (err) {
                        console.error("Failed to update status:", err);
                        alert("Failed to update status. Please try again.");
                        // Revert dropdown
                        statusDropdown.value = app.status;
                    }
                });
            }
        }

        if (detailGrid) {
            detailGrid.innerHTML = `
        <div>
          <dt>Location</dt>
          <dd>${app.location || "-"}</dd>
        </div>
        <div>
          <dt>Job Type</dt>
          <dd>${app.job_type || "-"}</dd>
        </div>
        <div>
          <dt>Application Date</dt>
          <dd>${formatShortDate(app.applied_at)}</dd>
        </div>
        <div>
          <dt>Deadline</dt>
          <dd>${formatShortDate(app.deadline_at)}</dd>
        </div>
        <div>
          <dt>Interview Date</dt>
          <dd>${formatShortDate(app.interview_at)}</dd>
        </div>
        <div>
          <dt>Salary Range</dt>
          <dd>${app.salary || "-"}</dd>
        </div>
        <div>
          <dt>Experience</dt>
          <dd>${app.experience || "-"}</dd>
        </div>
        <div style="grid-column: 1 / -1;">
          <dt>Links</dt>
          <dd>
            ${(app.links && Object.keys(app.links).length > 0)
                ? Object.entries(app.links).map(([k, v]) => `<a href="${v}" target="_blank" rel="noreferrer" style="margin-right: 10px; text-transform: capitalize;">${k.replace('_', ' ')}</a>`).join('')
                : `<a href="${app.job_link || "#"}" target="_blank" rel="noreferrer">${app.job_link || "-"}</a>`
            }
          </dd>
        </div>
      `;
        }

        if (timelineList) {
            timelineList.innerHTML = "";

            // Combine actual reminders with auto-generated deadline event
            // Reminders have 'trigger_at'
            const allTimelineItems = [...appReminders];

            // Auto-generate Application Deadline event if application has a deadline
            if (app.deadline_at) {
                allTimelineItems.push({
                    id: null, // Virtual event, no ID
                    kind: "DEADLINE", // Was application
                    title: "Application Deadline",
                    trigger_at: app.deadline_at,
                    application_id: app.id,
                    notes: "",
                    isAutoGenerated: true
                });
            }

            // Render events as timeline items
            if (allTimelineItems.length > 0) {
                allTimelineItems
                    .sort((a, b) => {
                        const dateA = parseLocalDate(a.trigger_at);
                        const dateB = parseLocalDate(b.trigger_at);
                        return dateB - dateA;
                    })
                    .forEach((reminder, index) => {
                        // Map kind to color class
                        const dotClass = reminder.kind === "INTERVIEW" ? "green" : reminder.kind === "FOLLOWUP" ? "orange" : "red";

                        const item = document.createElement("div");
                        item.className = "timeline-item";

                        // Only make it clickable if it's not auto-generated
                        if (!reminder.isAutoGenerated) {
                            item.style.cursor = "pointer";
                        }

                        let eventDate = reminder.trigger_at;
                        let eventDescription = "";

                        if (reminder.kind === "INTERVIEW") {
                            eventDescription = reminder.location || reminder.meeting_link || "";
                            if (reminder.start_time) {
                                eventDescription = `${reminder.start_time}${reminder.end_time ? ' - ' + reminder.end_time : ''} · ${eventDescription}`;
                            }
                        } else if (reminder.notes) {
                            eventDescription = reminder.notes;
                        }

                        // Add auto-generated indicator
                        const titleSuffix = reminder.isAutoGenerated ? ' <span style="font-size: 0.75rem; color: #9ca3af;">(Auto)</span>' : '';

                        item.innerHTML = `
              <div class="timeline-dot ${dotClass}"></div>
              <div>
                <div class="timeline-content-title">${reminder.title}${titleSuffix}</div>
                <div class="timeline-content-meta">${formatLongDate(eventDate)}${eventDescription ? " · " + eventDescription : ""}</div>
              </div>
            `;

                        // Only add click handler if not auto-generated
                        if (!reminder.isAutoGenerated) {
                            item.addEventListener("click", () => {
                                openEventModal(reminder.id);
                            });
                        }

                        timelineList.appendChild(item);
                    });
            } else {
                // Show empty state when no events
                const emptyState = document.createElement("div");
                emptyState.className = "timeline-empty-state";
                emptyState.innerHTML = `<p>No reminders yet. Click the button below to add your first reminder.</p>`;
                timelineList.appendChild(emptyState);
            }

            // Add event button
            const addEventBtn = document.createElement("button");
            addEventBtn.className = "add-timeline-btn";
            addEventBtn.textContent = "+ Add Reminder";
            addEventBtn.addEventListener("click", () => {
                openEventModalForApp(parseInt(id));
            });
            timelineList.appendChild(addEventBtn);
        }

        if (notesTextarea) {
            notesTextarea.value = app.notes || "";
            notesTextarea.addEventListener("change", () => {
                // purely front-end for now; no persistence needed for homework
            });
        }

        if (docsContainer) {
            docsContainer.innerHTML = "";
            (app.documents || []).forEach((doc) => {
                const row = document.createElement("div");
                row.className = "doc-item";
                row.innerHTML = `
          <span>${doc.label}</span>
          <a href="${doc.url || "#"}" target="_blank" rel="noreferrer">Open</a>
        `;
                docsContainer.appendChild(row);
            });
        }

        // Setup modal handlers for detail page
        const eventTypeSelect = document.getElementById("event-type");
        if (eventTypeSelect) {
            eventTypeSelect.addEventListener("change", (e) => {
                updateEventFormFields(e.target.value);
            });
        }

        const modal = document.getElementById("event-modal");
        const closeBtn = document.querySelector(".modal-close");

        if (closeBtn) {
            closeBtn.addEventListener("click", closeEventModal);
        }

        if (modal) {
            modal.addEventListener("click", (e) => {
                if (e.target === modal) {
                    closeEventModal();
                }
            });
        }
    } catch (err) {
        console.error(err);
    }
}

// --- Helper Functions --------------------------------------------------------

function generateAutoDeadlineEvents(applications) {
    const autoEvents = [];

    applications.forEach(app => {
        if (app.deadline_at) {
            autoEvents.push({
                id: `auto-${app.id}`, // Virtual ID
                title: `${app.company} - Application Deadline`,
                subtitle: "",
                date: app.deadline_at, // For calendar view
                trigger_at: app.deadline_at, // For timeline view compatibility
                color: "red",
                application_id: app.id,
                kind: "DEADLINE",
                isAutoGenerated: true
            });
        }
    });

    return autoEvents;
}

// --- Calendar page -----------------------------------------------------------

async function initCalendarPage() {
    const calendarRoot = document.querySelector("#calendar-root");
    if (!calendarRoot) return;

    let currentDate = new Date();
    let currentView = "month";
    let events = [];

    try {
        // Fetch both events and applications
        const [calendarEvents, applications] = await Promise.all([
            fetchJson("/api/apps/calendar"),
            fetchJson("/api/apps")
        ]);

        // Combine real events with auto-generated deadline events
        const autoDeadlineEvents = generateAutoDeadlineEvents(applications);
        // The calendar endpoint returns objects with 'date' property already formatted for calendar
        events = [...calendarEvents, ...autoDeadlineEvents];
    } catch (err) {
        console.error("Failed to load events:", err);
    }

    // Setup event type selector listener
    const eventTypeSelect = document.getElementById("event-type");
    if (eventTypeSelect) {
        eventTypeSelect.addEventListener("change", (e) => {
            updateEventFormFields(e.target.value);
        });
    }

    // Setup modal close handlers
    const modal = document.getElementById("event-modal");
    const closeBtn = document.querySelector(".modal-close");

    if (closeBtn) {
        closeBtn.addEventListener("click", closeEventModal);
    }

    if (modal) {
        modal.addEventListener("click", (e) => {
            if (e.target === modal) {
                closeEventModal();
            }
        });
    }

    // View switching
    const viewButtons = document.querySelectorAll("[data-view]");
    viewButtons.forEach((btn) => {
        btn.addEventListener("click", () => {
            currentView = btn.dataset.view;
            viewButtons.forEach((b) => b.classList.remove("active"));
            btn.classList.add("active");
            renderCalendar();
        });
    });

    // Navigation buttons
    document.getElementById("prev-btn")?.addEventListener("click", () => {
        if (currentView === "month") {
            currentDate.setMonth(currentDate.getMonth() - 1);
        } else if (currentView === "week") {
            currentDate.setDate(currentDate.getDate() - 7);
        } else {
            currentDate.setDate(currentDate.getDate() - 1);
        }
        renderCalendar();
    });

    document.getElementById("next-btn")?.addEventListener("click", () => {
        if (currentView === "month") {
            currentDate.setMonth(currentDate.getMonth() + 1);
        } else if (currentView === "week") {
            currentDate.setDate(currentDate.getDate() + 7);
        } else {
            currentDate.setDate(currentDate.getDate() + 1);
        }
        renderCalendar();
    });

    document.getElementById("today-btn")?.addEventListener("click", () => {
        currentDate = new Date();
        renderCalendar();
    });

    function renderCalendar() {
        const monthView = document.getElementById("month-view");
        const weekView = document.getElementById("week-view");
        const dayView = document.getElementById("day-view");

        // Hide all views
        monthView.style.display = "none";
        weekView.style.display = "none";
        dayView.style.display = "none";

        if (currentView === "month") {
            monthView.style.display = "block";
            renderMonthView();
        } else if (currentView === "week") {
            weekView.style.display = "block";
            renderWeekView();
        } else {
            dayView.style.display = "block";
            renderDayView();
        }
    }

    function renderMonthView() {
        const monthLabel = document.getElementById("calendar-month-label");
        const tableBody = document.getElementById("calendar-tbody");

        const year = currentDate.getFullYear();
        const month = currentDate.getMonth();

        const monthNames = ["January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"];

        if (monthLabel) {
            monthLabel.textContent = `${monthNames[month]} ${year}`;
        }

        const firstDay = new Date(year, month, 1);
        const firstWeekday = firstDay.getDay();
        const daysInMonth = new Date(year, month + 1, 0).getDate();
        const today = new Date();
        const isCurrentMonth = today.getFullYear() === year && today.getMonth() === month;
        const todayDate = today.getDate();

        const cells = [];
        for (let i = 0; i < firstWeekday; i++) {
            cells.push(null);
        }
        for (let d = 1; d <= daysInMonth; d++) {
            cells.push(d);
        }
        while (cells.length % 7 !== 0) {
            cells.push(null);
        }

        if (tableBody) {
            tableBody.innerHTML = "";
            for (let row = 0; row < cells.length / 7; row++) {
                const tr = document.createElement("tr");
                for (let col = 0; col < 7; col++) {
                    const idx = row * 7 + col;
                    const dayNumber = cells[idx];
                    const td = document.createElement("td");

                    if (dayNumber) {
                        const isToday = isCurrentMonth && dayNumber === todayDate;

                        const daySpan = document.createElement("div");
                        daySpan.className = "calendar-day-number" + (isToday ? " today" : "");
                        daySpan.textContent = String(dayNumber);
                        td.appendChild(daySpan);

                        const dateStr = `${year}-${String(month + 1).padStart(2, "0")}-${String(dayNumber).padStart(2, "0")}`;

                        events
                            .filter((e) => e.date === dateStr)
                            .forEach((e) => {
                                const div = document.createElement("div");
                                div.className = `calendar-event ${e.color || "blue"}`;
                                div.textContent = e.subtitle ? `${e.title} ${e.subtitle}` : e.title;

                                // Only make clickable if not auto-generated
                                if (!e.isAutoGenerated) {
                                    div.style.cursor = "pointer";
                                    div.addEventListener("click", (evt) => {
                                        evt.stopPropagation();
                                        openEventModal(e.id);
                                    });
                                } else {
                                    div.style.opacity = "0.85";
                                    div.title = "Auto-generated from application deadline";
                                }

                                td.appendChild(div);
                            });
                    }

                    tr.appendChild(td);
                }
                tableBody.appendChild(tr);
            }
        }
    }

    function renderWeekView() {
        const monthLabel = document.getElementById("calendar-month-label");
        const weekDaysHeader = document.getElementById("week-days-header");
        const weekTimeLabels = document.getElementById("week-time-labels");
        const weekGrid = document.getElementById("week-grid");

        // Get start of week (Sunday)
        const startOfWeek = new Date(currentDate);
        startOfWeek.setDate(currentDate.getDate() - currentDate.getDay());

        const endOfWeek = new Date(startOfWeek);
        endOfWeek.setDate(startOfWeek.getDate() + 6);

        const monthNames = ["January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"];

        if (monthLabel) {
            const startMonth = monthNames[startOfWeek.getMonth()];
            const endMonth = monthNames[endOfWeek.getMonth()];
            const year = startOfWeek.getFullYear();

            if (startOfWeek.getMonth() === endOfWeek.getMonth()) {
                monthLabel.textContent = `${startMonth} ${startOfWeek.getDate()}-${endOfWeek.getDate()}, ${year}`;
            } else {
                monthLabel.textContent = `${startMonth} ${startOfWeek.getDate()} - ${endMonth} ${endOfWeek.getDate()}, ${year}`;
            }
        }

        // Render day headers
        const dayNames = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
        weekDaysHeader.innerHTML = "";
        for (let i = 0; i < 7; i++) {
            const date = new Date(startOfWeek);
            date.setDate(startOfWeek.getDate() + i);
            const isToday = isSameDay(date, new Date());

            const dayHeader = document.createElement("div");
            dayHeader.className = "week-day-header" + (isToday ? " today" : "");
            dayHeader.innerHTML = `
        <div class="week-day-name">${dayNames[i]}</div>
        <div class="week-day-number">${date.getDate()}</div>
      `;
            weekDaysHeader.appendChild(dayHeader);
        }

        // Render time labels (6 AM to 9 PM)
        weekTimeLabels.innerHTML = "";
        for (let hour = 6; hour <= 21; hour++) {
            const label = document.createElement("div");
            label.className = "time-label";
            label.textContent = formatHour(hour);
            weekTimeLabels.appendChild(label);
        }

        // Render grid
        weekGrid.innerHTML = "";
        const now = new Date();

        for (let i = 0; i < 7; i++) {
            const date = new Date(startOfWeek);
            date.setDate(startOfWeek.getDate() + i);
            const column = document.createElement("div");
            column.className = "week-day-column";

            // Add hour lines
            for (let hour = 6; hour <= 21; hour++) {
                const hourLine = document.createElement("div");
                hourLine.className = "hour-line";
                column.appendChild(hourLine);
            }

            // Add current time indicator if this is today
            if (isSameDay(date, now)) {
                const currentHour = now.getHours();
                const currentMinute = now.getMinutes();
                if (currentHour >= 6 && currentHour <= 21) {
                    const topPercent = ((currentHour - 6) * 60 + currentMinute) / (16 * 60) * 100;
                    const indicator = document.createElement("div");
                    indicator.className = "current-time-indicator";
                    indicator.style.top = `${topPercent}%`;
                    column.appendChild(indicator);
                }
            }

            // Add events
            const dateStr = formatDateString(date);
            events
                .filter((e) => e.date === dateStr)
                .forEach((e) => {
                    const eventEl = document.createElement("div");
                    eventEl.className = `week-event ${e.color || "blue"}`;
                    eventEl.textContent = e.subtitle ? `${e.title} ${e.subtitle}` : e.title;

                    // Only make clickable if not auto-generated
                    if (!e.isAutoGenerated) {
                        eventEl.style.cursor = "pointer";
                        eventEl.addEventListener("click", (evt) => {
                            evt.stopPropagation();
                            openEventModal(e.id);
                        });
                    } else {
                        eventEl.style.opacity = "0.85";
                        eventEl.title = "Auto-generated from application deadline";
                    }

                    // Position at 9 AM by default
                    eventEl.style.top = "18.75%";
                    column.appendChild(eventEl);
                });

            weekGrid.appendChild(column);
        }
    }

    function renderDayView() {
        const monthLabel = document.getElementById("calendar-month-label");
        const dayDateHeader = document.getElementById("day-date-header");
        const dayTimeLabels = document.getElementById("day-time-labels");
        const dayGrid = document.getElementById("day-grid");

        const monthNames = ["January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"];
        const dayNames = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];

        if (monthLabel) {
            monthLabel.textContent = `${monthNames[currentDate.getMonth()]} ${currentDate.getFullYear()}`;
        }

        const isToday = isSameDay(currentDate, new Date());

        dayDateHeader.innerHTML = `
      <div class="day-full-header ${isToday ? 'today' : ''}">
        <div class="day-name">${dayNames[currentDate.getDay()]}</div>
        <div class="day-number">${currentDate.getDate()}</div>
      </div>
    `;

        // Render time labels
        dayTimeLabels.innerHTML = "";
        for (let hour = 0; hour < 24; hour++) {
            const label = document.createElement("div");
            label.className = "time-label";
            label.textContent = formatHour(hour);
            dayTimeLabels.appendChild(label);
        }

        // Render grid
        dayGrid.innerHTML = "";
        const column = document.createElement("div");
        column.className = "day-column";

        // Add hour lines
        for (let hour = 0; hour < 24; hour++) {
            const hourLine = document.createElement("div");
            hourLine.className = "hour-line";
            column.appendChild(hourLine);
        }

        // Add current time indicator if this is today
        const now = new Date();
        if (isToday) {
            const currentHour = now.getHours();
            const currentMinute = now.getMinutes();
            const topPercent = (currentHour * 60 + currentMinute) / (24 * 60) * 100;
            const indicator = document.createElement("div");
            indicator.className = "current-time-indicator";
            indicator.style.top = `${topPercent}%`;
            column.appendChild(indicator);
        }

        // Add events
        const dateStr = formatDateString(currentDate);
        events
            .filter((e) => e.date === dateStr)
            .forEach((e) => {
                const eventEl = document.createElement("div");
                eventEl.className = `day-event ${e.color || "blue"}`;
                eventEl.textContent = e.subtitle ? `${e.title} ${e.subtitle}` : e.title;

                // Only make clickable if not auto-generated
                if (!e.isAutoGenerated) {
                    eventEl.style.cursor = "pointer";
                    eventEl.addEventListener("click", (evt) => {
                        evt.stopPropagation();
                        openEventModal(e.id);
                    });
                } else {
                    eventEl.style.opacity = "0.85";
                    eventEl.title = "Auto-generated from application deadline";
                }

                // Position at 9 AM by default
                eventEl.style.top = "37.5%";
                column.appendChild(eventEl);
            });

        dayGrid.appendChild(column);
    }

    function isSameDay(date1, date2) {
        return date1.getFullYear() === date2.getFullYear() &&
            date1.getMonth() === date2.getMonth() &&
            date1.getDate() === date2.getDate();
    }

    function formatDateString(date) {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, "0");
        const day = String(date.getDate()).padStart(2, "0");
        return `${year}-${month}-${day}`;
    }

    function formatHour(hour) {
        if (hour === 0) return "12 AM";
        if (hour < 12) return `${hour} AM`;
        if (hour === 12) return "12 PM";
        return `${hour - 12} PM`;
    }

    // Add Event button
    document.getElementById("add-event-btn")?.addEventListener("click", () => {
        openEventModal();
    });

    renderCalendar();
}

// --- Event Management Functions ---------------------------------------------

async function openEventModalForApp(appId) {
    await openEventModal(null, appId);
}

async function openEventModal(eventId = null, prefilledAppId = null) {
    const modal = document.getElementById("event-modal");
    if (!modal) return;

    const isEdit = eventId !== null;
    let event = null;

    if (isEdit) {
        try {
            event = await fetchJson(`/api/reminders/${eventId}`);
        } catch (err) {
            console.error("Failed to load reminder:", err);
            alert("Failed to load reminder details");
            return;
        }
    }

    // Populate modal title
    document.getElementById("event-modal-title").textContent = isEdit ? "Edit Reminder" : "Add Reminder";

    // Populate form
    const form = document.getElementById("event-form");
    const eventTypeSelect = document.getElementById("event-type");
    const eventTitle = document.getElementById("event-title");
    const eventApplicationDropdown = document.getElementById("event-application-dropdown");
    const eventNotes = document.getElementById("event-notes");
    const eventColor = document.getElementById("event-color");

    // Populate applications dropdown
    try {
        const applications = await fetchJson("/api/apps");

        // Clear existing options (except the first "No Application" option)
        while (eventApplicationDropdown.options.length > 1) {
            eventApplicationDropdown.remove(1);
        }

        // Add applications to dropdown
        applications.forEach(app => {
            const option = document.createElement("option");
            option.value = app.id;
            option.textContent = `${app.company} - ${app.title}`;
            eventApplicationDropdown.appendChild(option);
        });
    } catch (err) {
        console.error("Failed to load applications:", err);
    }

    if (event) {
        eventTypeSelect.value = event.kind;
        eventTitle.value = event.title || "";
        if (event.applicationId || event.application_id) {
            eventApplicationDropdown.value = event.applicationId || event.application_id || "";
        }
        eventNotes.value = event.notes || "";
        eventColor.value = event.color || "";

        // Populate type-specific fields
        const triggerAt = event.triggerAt || event.trigger_at || "";
        const endDate = event.endDate || event.end_date || "";
        const startTime = event.startTime || event.start_time || "";
        const endTime = event.endTime || event.end_time || "";
        const meetingLink = event.meetingLink || event.meeting_link || "";

        if (event.kind === "DEADLINE") {
            document.getElementById("event-deadline").value = triggerAt;
        } else if (event.kind === "INTERVIEW") {
            document.getElementById("event-start-date").value = triggerAt;
            document.getElementById("event-end-date").value = endDate;
        } else if (event.kind === "FOLLOWUP") {
            const assignmentDeadline = document.getElementById("event-assignment-deadline");
            if (assignmentDeadline) assignmentDeadline.value = triggerAt;
        }

        // Populate time fields based on event type
        if (event.kind === "INTERVIEW") {
            // Interview: populate start and end times
            document.getElementById("event-start-time").value = startTime;
            document.getElementById("event-end-time").value = endTime;
            const meetingLinkEl = document.getElementById("event-meeting-link");
            if (meetingLinkEl) meetingLinkEl.value = meetingLink;
        } else {
            // Deadline and Follow-up: single time field
            document.getElementById("event-time").value = startTime;
        }

        // Location is available for all types
        document.getElementById("event-location").value = event.location || "";
    } else {
        form.reset();
        eventTypeSelect.value = "DEADLINE"; // Default to DEADLINE
        eventApplicationDropdown.value = ""; // Reset dropdown
        if (prefilledAppId) {
            eventApplicationDropdown.value = prefilledAppId;
        }
    }

    // Show/hide fields based on event type
    updateEventFormFields(eventTypeSelect.value);

    // Add listener for event type changes
    eventTypeSelect.addEventListener("change", (e) => {
        updateEventFormFields(e.target.value);
    });

    // Handle form submission
    const submitBtn = document.getElementById("event-submit-btn");
    const newSubmitBtn = submitBtn.cloneNode(true);
    submitBtn.parentNode.replaceChild(newSubmitBtn, submitBtn);

    newSubmitBtn.addEventListener("click", async (e) => {
        e.preventDefault();
        await saveEvent(eventId);
    });

    // Handle delete button
    const deleteBtn = document.getElementById("event-delete-btn");
    if (isEdit) {
        deleteBtn.style.display = "block";
        const newDeleteBtn = deleteBtn.cloneNode(true);
        deleteBtn.parentNode.replaceChild(newDeleteBtn, deleteBtn);

        newDeleteBtn.addEventListener("click", async (e) => {
            e.preventDefault();
            if (confirm("Are you sure you want to delete this reminder?")) {
                await deleteEvent(eventId);
            }
        });
    } else {
        deleteBtn.style.display = "none";
    }

    modal.style.display = "flex";
}

function updateEventFormFields(eventType) {
    const applicationFields = document.getElementById("application-fields");
    const interviewFields = document.getElementById("interview-fields");
    const assignmentFields = document.getElementById("assignment-fields");
    const singleTimeField = document.getElementById("single-time-field");
    const interviewTimeFields = document.getElementById("interview-time-fields");

    // Show type-specific date fields
    if (applicationFields) applicationFields.style.display = eventType === "DEADLINE" ? "block" : "none";
    if (interviewFields) interviewFields.style.display = eventType === "INTERVIEW" ? "block" : "none";
    if (assignmentFields) assignmentFields.style.display = eventType === "FOLLOWUP" ? "block" : "none";

    // Show appropriate time fields based on event type
    if (eventType === "INTERVIEW") {
        if (singleTimeField) singleTimeField.style.display = "none";
        if (interviewTimeFields) interviewTimeFields.style.display = "block";
    } else {
        // For DEADLINE and FOLLOWUP: show single time field
        if (singleTimeField) singleTimeField.style.display = "block";
        if (interviewTimeFields) interviewTimeFields.style.display = "none";
    }
}

// Validate that date is within acceptable range (1900-01-01 to 3 years from now)
function validateDateRange(dateString) {
    if (!dateString) {
        return { valid: false, message: 'Date is required' };
    }

    // Parse the date string
    const date = new Date(dateString + 'T00:00:00Z');

    // Check if date is valid
    if (isNaN(date.getTime())) {
        return { valid: false, message: 'Invalid date format' };
    }

    // Set min and max dates
    const minDate = new Date('1900-01-01T00:00:00Z');
    const today = new Date();
    const maxDate = new Date(today.getFullYear() + 3, today.getMonth(), today.getDate());

    // Normalize dates for comparison
    const checkDate = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    const checkMin = new Date(1900, 0, 1);
    const checkMax = new Date(maxDate.getFullYear(), maxDate.getMonth(), maxDate.getDate());

    if (checkDate < checkMin) {
        return {
            valid: false,
            message: `Date cannot be before January 1, 1900`
        };
    }

    if (checkDate > checkMax) {
        return {
            valid: false,
            message: `Date cannot be more than 3 years in the future (max: ${checkMax.toLocaleDateString()})`
        };
    }

    return { valid: true };
}

async function saveEvent(eventId) {
    const eventType = document.getElementById("event-type").value;
    const eventTitle = document.getElementById("event-title").value;
    const eventApplicationDropdown = document.getElementById("event-application-dropdown");
    const eventApplicationId = eventApplicationDropdown.value;
    const eventNotes = document.getElementById("event-notes").value;
    const eventColor = document.getElementById("event-color").value;

    if (!eventTitle) {
        alert("Please enter a title");
        return;
    }

    // Check for any date input fields with error styling (red border)
    const dateInputsWithErrors = document.querySelectorAll('input[type="date"][style*="border-color: rgb(239, 68, 68)"]');
    if (dateInputsWithErrors.length > 0) {
        alert("Please fix the invalid dates before saving");
        return;
    }

    const payload = {
        kind: eventType,
        title: eventTitle,
        applicationId: eventApplicationId || null,
        notes: eventNotes,
        color: eventColor,
    };

    // Get location (available for all types)
    const location = document.getElementById("event-location")?.value;

    // Add type-specific fields
    if (eventType === "DEADLINE") {
        const deadline = document.getElementById("event-deadline").value;
        if (!deadline) {
            alert("Please enter a deadline");
            return;
        }

        // Validate date range
        const dateValidation = validateDateRange(deadline);
        if (!dateValidation.valid) {
            alert(dateValidation.message);
            return;
        }

        payload.triggerAt = deadline;
        // Single time field for deadline
        const time = document.getElementById("event-time")?.value;
        if (time) payload.startTime = time;
        if (location) payload.location = location;
    } else if (eventType === "INTERVIEW") {
        const startDate = document.getElementById("event-start-date").value;
        if (!startDate) {
            alert("Please enter a start date");
            return;
        }

        // Validate date range
        const dateValidation = validateDateRange(startDate);
        if (!dateValidation.valid) {
            alert(dateValidation.message);
            return;
        }

        payload.triggerAt = startDate;
        payload.endDate = document.getElementById("event-end-date").value || startDate;

        // Also validate end date if provided
        if (payload.endDate && payload.endDate !== startDate) {
            const endDateValidation = validateDateRange(payload.endDate);
            if (!endDateValidation.valid) {
                alert(endDateValidation.message);
                return;
            }
        }

        // Start and end times for interview
        payload.startTime = document.getElementById("event-start-time").value;
        payload.endTime = document.getElementById("event-end-time").value;
        const meetingLink = document.getElementById("event-meeting-link")?.value;
        if (meetingLink) payload.meetingLink = meetingLink;
        if (location) payload.location = location;
    } else if (eventType === "FOLLOWUP") {
        const deadline = document.getElementById("event-assignment-deadline").value;
        if (!deadline) {
            alert("Please enter a deadline");
            return;
        }

        // Validate date range
        const dateValidation = validateDateRange(deadline);
        if (!dateValidation.valid) {
            alert(dateValidation.message);
            return;
        }

        payload.triggerAt = deadline;
        // Single time field for follow-up
        const time = document.getElementById("event-time")?.value;
        if (time) payload.startTime = time;
        if (location) payload.location = location;
    }

    // Disable submit button to prevent double-click
    const submitBtn = document.getElementById("event-submit-btn");
    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.style.opacity = "0.6";
    }

    try {
        if (eventId) {
            await fetchJson(`/api/reminders/${eventId}`, {
                method: "PUT",
                body: JSON.stringify(payload),
            });
        } else {
            await fetchJson("/api/reminders", {
                method: "POST",
                body: JSON.stringify(payload),
            });
        }
        closeEventModal();
        window.location.reload();
    } catch (err) {
        console.error("Failed to save reminder:", err);
        alert("Failed to save reminder. Please try again.");

        // Re-enable button if there's an error
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.style.opacity = "1";
        }
    }
}

async function deleteEvent(eventId) {
    try {
        await fetchJson(`/api/reminders/${eventId}`, {
            method: "DELETE",
        });
        closeEventModal();
        window.location.reload();
    } catch (err) {
        console.error("Failed to delete reminder:", err);
        alert("Failed to delete reminder. Please try again.");
    }
}

function closeEventModal() {
    const modal = document.getElementById("event-modal");
    if (modal) {
        modal.style.display = "none";
    }
}

// --- Recommendations page ----------------------------------------------------

async function initRecommendationsPage() {
    const tableBody = document.querySelector("#recommendations-tbody");
    if (!tableBody) return;

    const searchInput = document.querySelector("#search-input");
    const countLabel = document.querySelector("#recommendations-count");

    let allJobs = [];

    function render(jobs) {
        tableBody.innerHTML = "";
        jobs.forEach((job) => {
            const tr = document.createElement("tr");
            tr.innerHTML = `
        <td>${job.company}</td>
        <td>${job.title}</td>
        <td>${job.location || "-"}</td>
        <td>${job.salary || "-"}</td>
        <td class="link-cell">
          <button class="primary-btn small" style="padding: 4px 8px; font-size: 0.8rem;" onclick="window.location.href='new_application.html?company=${encodeURIComponent(job.company)}&title=${encodeURIComponent(job.title)}&location=${encodeURIComponent(job.location||'')}&salaryRange=${encodeURIComponent(job.salary||'')}&jobLink=${encodeURIComponent(job.job_link||'')}'">
            Apply
          </button>
        </td>
      `;
            tableBody.appendChild(tr);
        });

        if (countLabel) {
            countLabel.textContent = `Showing ${jobs.length} jobs`;
        }
    }

    try {
        console.log("[Recommendations] Loading jobs from /api/jobs...");
        allJobs = await fetchJson("/api/jobs");
        console.log("[Recommendations] ✓ Loaded " + (allJobs ? allJobs.length : 0) + " jobs");

        if (!allJobs || allJobs.length === 0) {
            console.warn("[Recommendations] ⚠ No jobs returned from API");
            tableBody.innerHTML = "<tr><td colspan='5' style='text-align:center;padding:20px;'>No jobs available. Please try fetching jobs again.</td></tr>";
            if (countLabel) countLabel.textContent = "0 jobs";
            return;
        }

        render(allJobs);
        console.log("[Recommendations] ✓ Rendered jobs in table");

        if (searchInput) {
            searchInput.addEventListener("input", (e) => {
                const term = e.target.value.toLowerCase();
                const filtered = allJobs.filter(
                    (j) =>
                        j.company.toLowerCase().includes(term) ||
                        j.title.toLowerCase().includes(term)
                );
                render(filtered);
            });
        }
    } catch (err) {
        console.error("[Recommendations] ✗ Failed to load recommendations:", err);
        console.error("[Recommendations] Error stack:", err.stack);
        tableBody.innerHTML = "<tr><td colspan='5' style='text-align:center;padding:20px;color:red;'>Error loading jobs: " + err.message + "</td></tr>";
    }
}

// --- New application page ----------------------------------------------------

async function initNewApplicationPage() {
    const form = document.querySelector("#new-application-form");
    if (!form) return;

    // Handle pre-fill from query params (e.g. coming from Recommendations)
    const params = new URLSearchParams(window.location.search);
    if (params.has("company")) {
        const companyInput = form.querySelector("[name='company']");
        if (companyInput) companyInput.value = params.get("company");
    }
    if (params.has("title")) {
        const titleInput = form.querySelector("[name='title']");
        if (titleInput) titleInput.value = params.get("title");
    }
    if (params.has("location")) {
        const locationInput = form.querySelector("[name='location']");
        if (locationInput) locationInput.value = params.get("location");
    }
    if (params.has("salaryRange")) {
        const salaryInput = form.querySelector("[name='salaryRange']");
        if (salaryInput) salaryInput.value = params.get("salaryRange");
    }
    if (params.has("jobLink")) {
        const jobLinkInput = form.querySelector("[name='jobLink']");
        if (jobLinkInput) jobLinkInput.value = params.get("jobLink");
    }

    const saveBtn = form.querySelector("button[type=submit]");

    form.addEventListener("submit", async (e) => {
        e.preventDefault();
        if (!saveBtn) return;
        saveBtn.disabled = true;

        const formData = new FormData(form);
        const jobLink = formData.get("jobLink") || "";

        // Note: form fields in HTML need to match what we grab here.
        // Assuming HTML names are company, title, location, jobType, jobLink, deadline, salaryRange, status, notes
        const payload = {
            company: formData.get("company") || "",
            title: formData.get("title") || "",
            location: formData.get("location") || "",
            job_type: formData.get("jobType") || "Full-time",
            job_link: jobLink,
            links: jobLink ? { job_post: jobLink } : {},
            deadline_at: formData.get("deadline") || null,
            salary: formData.get("salaryRange") || "",
            status: formData.get("status") || "APPLIED", // Default APPLIED
            notes: formData.get("notes") || "",
        };

        // Map status from UI value (if it uses old values) to new ENUMs
        // But status select in new_application.html likely has values like "Applied"
        // We should probably update the HTML too, or map it here.
        // For now, let's assume we map common old values to new ones if needed.
        // Or if the user updates the HTML to send DRAFT/APPLIED etc.
        // The prompt said "rename fields in json... without changing appearance".
        // It didn't say update HTML values. But if HTML sends "Applied", backend expects "APPLIED".
        // I will map here to be safe.

        const statusMap = {
            "Pending Apply": "DRAFT",
            "Applied": "APPLIED", // Standard
            "Under Review": "APPLIED",
            "Pending Interview / Assignment": "INTERVIEW",
            "Pending Interview": "INTERVIEW",
            "Offered": "OFFER",
            "Rejected": "REJECTED"
        };
        if (statusMap[payload.status]) {
            payload.status = statusMap[payload.status];
        }

        if (!payload.company || !payload.title) {
            alert("Please fill in required fields: Company Name and Job Title.");
            saveBtn.disabled = false;
            return;
        }

        try {
            await fetchJson("/api/apps", {
                method: "POST",
                body: JSON.stringify(payload),
            });
            window.location.href = "index.html";
        } catch (err) {
            console.error(err);
            alert("Could not save application. Please try again.");
            saveBtn.disabled = false;
        }
    });
}

// --- Dashboard page ----------------------------------------------------------

async function initDashboardPage() {
    const dashboardRoot = document.querySelector("#dashboard-root");
    if (!dashboardRoot) return;

    try {
        // Fetch summary metrics
        const summary = await fetchJson("/api/dashboard-summary");
        const totalEl = document.querySelector("#metric-total");
        const interviewingEl = document.querySelector("#metric-interview");
        const offersEl = document.querySelector("#metric-offers");

        if (totalEl) totalEl.textContent = summary.totalApplications ?? 0;
        if (interviewingEl)
            interviewingEl.textContent = (summary.byStatus || {})["INTERVIEW"] ?? 0;
        if (offersEl)
            offersEl.textContent = (summary.byStatus || {})["OFFER"] ?? 0;

        // Fetch both events and applications
        const [calendarEvents, applications] = await Promise.all([
            fetchJson("/api/apps/calendar"),
            fetchJson("/api/apps")
        ]);

        // Combine real events with auto-generated deadline events
        const autoDeadlineEvents = generateAutoDeadlineEvents(applications);
        const allEvents = [...calendarEvents, ...autoDeadlineEvents];

        renderUpcomingEvents(allEvents);
    } catch (err) {
        console.error(err);
    }
}

function renderUpcomingEvents(events) {
    const upcomingList = document.getElementById("upcoming-events-list");
    if (!upcomingList) return;

    const now = new Date();
    now.setHours(0, 0, 0, 0); // Start of today

    // Filter future events and sort by date
    const upcomingEvents = events
        .filter((event) => {
            const eventDate = parseLocalDate(event.date);
            return eventDate && eventDate >= now;
        })
        .sort((a, b) => parseLocalDate(a.date) - parseLocalDate(b.date))
        .slice(0, 3); // Get next 3 events

    if (upcomingEvents.length === 0) {
        upcomingList.innerHTML = `
      <div class="no-upcoming-events">
        <p>No upcoming events scheduled</p>
      </div>
    `;
        return;
    }

    upcomingList.innerHTML = "";
    upcomingEvents.forEach((event) => {
        const eventDate = parseLocalDate(event.date);
        const isToday = isSameDay(eventDate, new Date());
        const daysUntil = Math.ceil((eventDate - now) / (1000 * 60 * 60 * 24));

        const eventCard = document.createElement("div");
        eventCard.className = `upcoming-event-card ${event.color || "blue"}`;

        let timeLabel = "";
        if (isToday) {
            timeLabel = "Today";
        } else if (daysUntil === 1) {
            timeLabel = "Tomorrow";
        } else if (daysUntil <= 7) {
            timeLabel = `In ${daysUntil} days`;
        } else {
            timeLabel = formatUpcomingDate(eventDate);
        }

        eventCard.innerHTML = `
      <div class="upcoming-event-indicator ${event.color || "blue"}"></div>
      <div class="upcoming-event-content">
        <div class="upcoming-event-title">${event.title}</div>
        <div class="upcoming-event-meta">
          ${event.subtitle ? `<span class="upcoming-event-time">${event.subtitle}</span>` : ""}
          <span class="upcoming-event-date">${timeLabel} · ${formatUpcomingDate(eventDate)}</span>
        </div>
      </div>
      <div class="upcoming-event-icon">
        ${getEventIcon(event.color)}
      </div>
    `;

        // Make card clickable to navigate to calendar
        eventCard.addEventListener("click", () => {
            window.location.href = "calendar.html";
        });

        upcomingList.appendChild(eventCard);
    });
}

function isSameDay(date1, date2) {
    return date1.getFullYear() === date2.getFullYear() &&
        date1.getMonth() === date2.getMonth() &&
        date1.getDate() === date2.getDate();
}

function formatUpcomingDate(date) {
    const monthNames = ["Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
    const dayNames = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
    return `${dayNames[date.getDay()]}, ${monthNames[date.getMonth()]} ${date.getDate()}`;
}

function getEventIcon(color) {
    const icons = {
        blue: `<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>`,
        green: `<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>`,
        red: `<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>`,
        orange: `<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>`,
        purple: `<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>`,
    };
    return icons[color] || icons.blue;
}

/**
 * Parse a date string as LOCAL time (not UTC).
 * Fixes the timezone bug where "2024-12-10" becomes Dec 9 in PST.
 */
function parseLocalDate(dateStr) {
    if (!dateStr) return null;
    // If it's already a Date object, return it
    if (dateStr instanceof Date) return dateStr;
    // Handle "YYYY-MM-DD" format - parse as local time
    if (typeof dateStr === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(dateStr)) {
        const [year, month, day] = dateStr.split('-').map(Number);
        return new Date(year, month - 1, day); // month is 0-indexed
    }
    // Fallback to regular Date parsing
    return new Date(dateStr);
}

function formatShortDate(iso) {
    if (!iso) return "-";
    const d = parseLocalDate(iso);
    if (!d || Number.isNaN(d.getTime())) return iso;
    const monthNames = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
    return `${monthNames[d.getMonth()]} ${String(d.getDate()).padStart(2, "0")}`;
}

function formatLongDate(iso) {
    if (!iso) return "";
    const d = parseLocalDate(iso);
    if (!d || Number.isNaN(d.getTime())) return iso;
    const monthNames = ["January","February","March","April","May","June","July","August","September","October","November","December"];
    return `${monthNames[d.getMonth()]} ${d.getDate()}, ${d.getFullYear()}`;
}

// --- Bootstrapping -----------------------------------------------------------

document.addEventListener("DOMContentLoaded", async () => {
    const isAuthed = checkAuthentication();
    await loadNavbar();

    if (isAuthed) {
        setupAuthUI();
    }

    initApplicationsPage();
    initDetailPage();
    initCalendarPage();
    initRecommendationsPage();
    initNewApplicationPage();
    initDashboardPage();
});