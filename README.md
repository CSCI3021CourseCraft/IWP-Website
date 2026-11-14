# IWP-Website - CourseCraft - (Milestone 1)

Team: Anna Zagrai, Abimael Lozano. 

Course: CSCI 3021, Fall 2026.

Repository: github.com/CSCI3021CourseCraft/IWP-Website

## Database
- **Database and tool:** Supabase (hosted PostgreSQL). We decided to use Supabase because it is hosted and shared by both teammates.
- **Driver:** PostgreSQL JDBC 42.7.4 (`org.postgresql.Driver`), added in `pom.xml`.
- **Script:** `coursecraft_postgres` creates and fills every table. Paste it into the Supabase SQL Editor and run it; it can be re-run (it drops and rebuilds the tables).
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
   - Optional: `LLM_MODEL` (default `openai/gpt-oss-120b`).
   - Nothing secret is in the repository or the zip. `src/main/resources/config.properties` is an optional alternative and is listed in `.gitignore`.
3. Build with Maven and run on Tomcat 10 (Jakarta). Open `http://localhost:8080`.
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

**Agent round trip:** `AgentServlet` sends the goal and the three tool definitions to the Groq API with `jakarta.net.http.HttpClient`, runs the one tool the model chooses through the same `ToolService` code the endpoints use (as the logged-in student), writes the call and its result to `agent_log`, and returns the result to the page.

## Who built what
- **Anna:** I built the SQL database and the AI implementation skeleton. The SQL database was built in SupaBase, since it was easier for us to implement across both of our accounts. The database is able to save Users, their Canvas information (classes by ID and deadlines, for example), Quizzes, Study plans, Notes, the Course materials, as well as the previous Agent prompts. This database layer ensures that persistent user data and structured course records are securely stored, accessed, and managed by the backend application. The AI "carcass" took a lot of programs and lots of trial and error. First off, I realized that we wouldn't be able to integrate Claude due to pricing, so we decided to go with Groq for cost efficiency. Integrated through classes like GroqClient.java and ToolDefs.java, the bot (which we named Gloik) was basically made to be an intelligent study assistant agent and helper. It processes student goals and prompt contexts alongside predefined tool definitions, deciding whether to reply conversationally or trigger specific backend functions (such as fetching notes or quiz details) to assist the user dynamically. The AI implementation relies on AgentServlet.java, which constructs a conversation history containing a system prompt with the student's courses and the user's goal. This message payload is sent to the LLM through GroqClient.java along with JSON tool definitions provided by ToolDefs.java. If the model responds with a tool call instead of plain text, the servlet extracts the function name and arguments. Finally, ToolService.java dispatches these parameters to execute the appropriate backend action and log the result. While we didn't manage to implement and test the agent out quite yet, the correct framework was built to support it. This will be the next step for me.

- 
- **Abimael:** I built the tool endpoints and wrote the documentation. The three servlets (NotesToolServlet, MaterialsToolServlet and SavePlanToolServlet) each check that the user is logged in, validate their inputs, and return JSON with a success or error field. They use JDBC PreparedStatements and close their connections in finally blocks. Reading notes and materials is read-only, and save_study_plan inserts a row into study_plans, so it changes data. I also made sure a student can only reach their own courses, which returns a 403 otherwise. I tested each endpoint against the Supabase database, covering the 400, 401 and 403 cases, and wrote the README, including the setup steps and the table of servlets and URLs.

## AI tools used
(State which AI tools helped write code)
