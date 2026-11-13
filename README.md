# IWP-Website - CourseCraft - (Milestone 1)

Team: Anna Zagrai, Abimael Lozano. 

Course: CSCI 3021, Fall 2026.

Repository: github.com/CSCI3021CourseCraft/IWP-Website

## Database
- **Database and tool:** Supabase (hosted PostgreSQL). We decided to use Supabase because it is hosted and shared by both teammates.
- **Driver:** PostgreSQL JDBC 42.7.4 (`org.postgresql.Driver`), added in `pom.xml`.
- **Script:** [SCRIPT NAME] creates and fills every table. Paste it into the Supabase SQL Editor and run it; it can be re-run (it drops and rebuilds the tables).
- **Tables and relationships**

| Table | Purpose | Relationships |
|---|---|---|
| users | student accounts (bcrypt password hash) | has many courses and log rows |
| courses | a student's courses | FK user_id -> users |
| canvas_items | assignments, modules, quizzes, files | FK course_id -> courses |
| notes | student notes | FK course_id -> courses |
| study_plans | plans saved by the student or the agent | FK course_id -> courses |
| agent_log | one row per tool call: log_id, user_id, tool_name, input, output, created_at | FK user_id -> users |

All foreign keys use ON DELETE CASCADE. There is an index on every foreign key and on `agent_log(user_id, created_at)`. Row level security is enabled on every table so Supabase's public web API cannot read them. The servlets use JDBC.

## How to run the backend
1. Create a Supabase project, open the SQL Editor and run `AgentServlet.sql`.
2. Set the following four environment variables (Windows: Start > "Edit environment variables for your account"), then restart NetBeans so Tomcat inherits them:

| Variable | Value |
|---|---|
| `DB_URL` | `jdbc:postgresql://<session-pooler-host>:5432/postgres?sslmode=require` |
| `DB_USER` | `postgres.<project-ref>` |
| `DB_PASSWORD` | the database password (read only from this variable) |
| `LLM_API_KEY` | the Groq API key (read only from this variable) |

   - **Host:** `<fill in: aws-N-REGION.pooler.supabase.com>` (Supabase > Connect > Session pooler), **port** 5432, **database** `postgres`, **driver** `org.postgresql.Driver`.
   - Optional: `LLM_MODEL` (default `llama-3.3-70b-versatile`).
   - Nothing secret is in the repository or the zip. `src/main/resources/config.properties` is an optional alternative and is listed in `.gitignore`.
3. Build with Maven and run on Tomcat 9 (Java 17+). Open `http://localhost:8080/CourseCraft/`.
4. Check the connection at `/dbtest`, then log in as `demo@coursecraft.test` or `example@coursecraft.test` with password `demo1234`.

## Servlets and tools

| Servlet | URL | Method | Agent tool |
|---|---|---|---|
| LoginServlet | /login | POST | Creates the session |
| LogoutServlet | /logout | GET | |
| CoursesServlet | /api/courses | GET | |
| NotesToolServlet | /api/tools/get_course_notes | GET | get_course_notes |
| MaterialsToolServlet | /api/tools/get_course_materials | GET | get_course_materials |
| SavePlanToolServlet | /api/tools/save_study_plan | POST | save_study_plan (changes data) |
| AgentServlet | /api/agent | POST | one plan-act-observe cycle |
| DbTestServlet | /dbtest | GET | Connection check |

Every tool endpoint checks the session (401 if not logged in), validates its input (400), checks that the course belongs to the logged in student (403), uses `PreparedStatement`, closes its connection in `finally`, and returns JSON with `success` or `error`.

**Agent round trip:** `AgentServlet` sends the goal and the three tool definitions to the Groq API with `java.net.http.HttpClient`, runs the one tool the model chooses through the same `ToolService` code the endpoints use (as the logged-in student), writes the call and its result to `agent_log`, and returns the result to the page.

## Who built what
- **Anna:** (write one paragraph)
- **Abimael:** (write one paragraph)

## AI tools used
(State which AI tools helped write code)
