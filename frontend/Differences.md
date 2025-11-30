| Feature / Component | Design Document (Needs) | Current Implementation (README.md / Mock) | Status / Difference |
| :--- | :--- | :--- | :--- |
| **Schema: Users** | `id`, `email`, `password_hash`, `role` | `id`, `email`, `username`, `role`. (Password hash added to internal mock but not API) | **Aligned**: Internal mock updated to include missing fields. |
| **Schema: Applications** | `id`, `user_id`, `company`, `title`, `status` (ENUM), `deadline_at`, `interview_at`, `links` (JSON), `created_at` | `id`, `company`, `title`, `status` (ENUM), `deadline_at`, `interview_at`, `links` (JSON), `created_at`, `applied_at`, `job_type`, `salary`, `location`, `experience`, `documents`, `notes` | **Aligned**: Mock includes all design fields + extra UI-specific fields. |
| **Schema: Reminders** | `id`, `application_id`, `kind` (ENUM), `trigger_at`, `sent_at` | `id`, `application_id`, `kind` (ENUM), `trigger_at`, `sent_at`. (Renamed from 'Events' to 'Reminders') | **Aligned**: Renamed and updated to match schema. |
| **Schema: Jobs** | `id`, `title`, `company`, `salary`, `description` | `id`, `title`, `company`, `salary`, `description`, `location`, `job_type`, `job_link`. | **Aligned**: Mock data added for recommendations. |
| **Schema: OAuth Tokens** | `id`, `user_id`, `provider`, `access_token`, `refresh_token`, `expires_at` | `user_data.integrations.google` (contains tokens). | **Partial**: Tokens stored in user object structure, not separate table entity. |
| **Schema: Email Processing** | `user_id`, `last_uid` | `user_data.email_processing_state.last_uid` | **Aligned**: Added to user mock data structure. |
| **API: Job Search** | Implied by `jobs` schema (recommendations/research). | `GET /api/jobs` endpoint implemented. | **Aligned**: Recommendations/Job listing endpoint available. |

