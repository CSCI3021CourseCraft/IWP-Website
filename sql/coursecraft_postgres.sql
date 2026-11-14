-- CourseCraft schema + sample data for PostgreSQL (Supabase)
-- Paste into Supabase: SQL Editor -> New query -> Run.
-- Safe to re-run: it drops and recreates every table.
--
-- Demo logins (password for both: demo1234):
--   demo@coursecraft.test      (2 courses, notes, quiz results)
--   example@coursecraft.test   (1 course)

DROP TABLE IF EXISTS agent_log, study_plans, quiz_results, canvas_items, notes, courses, users CASCADE;
DROP FUNCTION IF EXISTS set_updated_at();

CREATE TABLE users (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  email         VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,            -- bcrypt ($2a$...), checked with jBCrypt in LoginServlet
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE courses (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id          BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  canvas_course_id VARCHAR(50),
  name             VARCHAR(200) NOT NULL
);
CREATE INDEX idx_courses_user ON courses(user_id);

CREATE TABLE notes (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  course_id  BIGINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  title      VARCHAR(200) NOT NULL,
  body       TEXT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notes_course ON notes(course_id);

-- PostgreSQL has no "ON UPDATE CURRENT_TIMESTAMP", so a trigger keeps updated_at fresh
CREATE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN NEW.updated_at = now(); RETURN NEW; END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_notes_updated BEFORE UPDATE ON notes
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE canvas_items (
  id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  course_id BIGINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  type      VARCHAR(20) NOT NULL CHECK (type IN ('assignment','module','quiz','file')),
  title     VARCHAR(200) NOT NULL,
  due_date  DATE,
  body      TEXT
);
CREATE INDEX idx_items_course ON canvas_items(course_id, type);

CREATE TABLE quiz_results (
  id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  item_id   BIGINT NOT NULL REFERENCES canvas_items(id) ON DELETE CASCADE,
  topic     VARCHAR(200) NOT NULL,
  score     NUMERIC(6,2) NOT NULL,
  max_score NUMERIC(6,2) NOT NULL
);
CREATE INDEX idx_quiz_item ON quiz_results(item_id);

CREATE TABLE study_plans (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  course_id  BIGINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  plan_text  TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_plans_course ON study_plans(course_id, created_at);

-- One row per tool call the agent makes (written by ToolService.logCall)
CREATE TABLE agent_log (
  log_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  tool_name  VARCHAR(100) NOT NULL,
  input      TEXT NOT NULL,
  output     TEXT NOT NULL,
  success    BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_log_user_time ON agent_log(user_id, created_at);

-- ---------- Sample data ----------
INSERT INTO users (email, password_hash) VALUES
 ('demo@coursecraft.test',    '$2a$10$6NPyexI13nzYl19txTMA9us0xzmUD9BrQ0LEEQUHoMOcEDRolvlKi'),
 ('example@coursecraft.test', '$2a$10$cUe7ZCf9ovHSoF4gebHL8uTqBFqOvaC3MmUtMXFlVgHBA1oUC7FgG');

INSERT INTO courses (user_id, canvas_course_id, name) VALUES
 (1, '100234', 'CSCI 3021 Internet and Web Programming'),
 (1, '100871', 'MATH 2310 Linear Algebra'),
 (2, '100555', 'CSCI 2021 Data Structures');

INSERT INTO notes (course_id, title, body) VALUES
 (1, 'Servlet lifecycle', 'init() runs once, service() per request (doGet/doPost), destroy() on shutdown. Servlets are multi-threaded, so avoid shared mutable fields.'),
 (1, 'Sessions', 'HttpSession tracks a user across requests via the JSESSIONID cookie. Call invalidate() on logout.'),
 (1, 'JDBC basics', 'Use PreparedStatement to prevent SQL injection. Always close ResultSet, Statement, Connection in finally.'),
 (2, 'Eigenvalues', 'Av = lambda v. Solve det(A - lambda I) = 0. I am still confused about repeated eigenvalues.'),
 (2, 'Matrix rank', 'Rank = number of pivot columns after row reduction.'),
 (3, 'Big-O', 'O(n log n) sorts: merge sort, heap sort. Quick sort is O(n^2) in the worst case.');

INSERT INTO canvas_items (course_id, type, title, due_date, body) VALUES
 (1, 'assignment', 'Project Milestone: Backend and Agent', '2026-11-13', 'Servlets, DB, first LLM tool call.'),
 (1, 'module',     'Module 8: JDBC and Databases', NULL, 'Connections, PreparedStatement, transactions.'),
 (1, 'quiz',       'Quiz 3: Servlets and Sessions', '2026-10-20', NULL),
 (1, 'quiz',       'Quiz 4: JDBC', '2026-11-03', NULL),
 (2, 'assignment', 'Homework 9: Eigenvalues', '2026-11-16', 'Problems 1-12.'),
 (2, 'quiz',       'Quiz 5: Determinants and Eigenvalues', '2026-11-05', NULL),
 (3, 'assignment', 'Lab 6: Binary Trees', '2026-11-18', 'Implement insert and traversal.');

-- item_id points at the quiz rows above (ids 3, 4 and 6)
INSERT INTO quiz_results (item_id, topic, score, max_score) VALUES
 (3, 'Session tracking',   4, 5),
 (3, 'Servlet lifecycle',  5, 5),
 (3, 'Cookies',            2, 5),
 (4, 'PreparedStatement',  3, 5),
 (4, 'Transactions',       1, 5),
 (6, 'Determinants',       5, 5),
 (6, 'Eigenvalues',        2, 5);

INSERT INTO study_plans (course_id, plan_text) VALUES
 (1, 'Sample plan: review cookies and transactions before the final.');

-- Supabase exposes the public schema through its web Data API. Turning on Row Level Security
-- with no policies blocks that API from reading these tables. The servlets connect over JDBC
-- as the database owner role, which is not affected.
ALTER TABLE users        ENABLE ROW LEVEL SECURITY;
ALTER TABLE courses      ENABLE ROW LEVEL SECURITY;
ALTER TABLE notes        ENABLE ROW LEVEL SECURITY;
ALTER TABLE canvas_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE quiz_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE study_plans  ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_log    ENABLE ROW LEVEL SECURITY;
