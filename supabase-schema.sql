-- Health Monitor - Supabase Schema
-- Multi-patient offline-first system with "Win-Latest" conflict resolution.
-- All tables use last_modified_at (epoch-millis) for incremental sync queries
-- and is_deleted (soft delete) so deletions propagate across devices.

-- ── Patients ──────────────────────────────────────────────────────────────────
CREATE TABLE patients (
  id                 TEXT PRIMARY KEY,              -- UUID string from client
  name               TEXT NOT NULL,
  age                INTEGER,
  gender             TEXT,
  blood_type         TEXT,
  medical_conditions TEXT,
  emergency_contact  TEXT,
  emergency_phone    TEXT,
  created_at         BIGINT NOT NULL DEFAULT 0,     -- epoch millis
  last_modified_at   BIGINT NOT NULL DEFAULT 0,
  is_deleted         BOOLEAN NOT NULL DEFAULT false
);

-- ── Medical Cases ─────────────────────────────────────────────────────────────
CREATE TABLE medical_cases (
  id               TEXT PRIMARY KEY,
  patient_id       TEXT NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  title            TEXT NOT NULL,
  doctor_name      TEXT,
  status           TEXT NOT NULL DEFAULT 'OPEN',    -- 'OPEN' | 'CLOSED'
  notes            TEXT,
  created_at       BIGINT NOT NULL DEFAULT 0,
  last_modified_at BIGINT NOT NULL DEFAULT 0,
  is_deleted       BOOLEAN NOT NULL DEFAULT false
);

-- ── Medications ───────────────────────────────────────────────────────────────
CREATE TABLE medications (
  id               TEXT PRIMARY KEY,
  patient_id       TEXT NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  case_id          TEXT REFERENCES medical_cases(id) ON DELETE SET NULL,
  name             TEXT NOT NULL,
  dosage           TEXT NOT NULL,
  unit             TEXT DEFAULT 'mg',
  frequency        TEXT NOT NULL,
  notes            TEXT,
  is_active        BOOLEAN NOT NULL DEFAULT true,
  start_date       BIGINT NOT NULL DEFAULT 0,       -- epoch millis
  end_date         BIGINT,
  created_at       BIGINT NOT NULL DEFAULT 0,
  last_modified_at BIGINT NOT NULL DEFAULT 0,
  is_deleted       BOOLEAN NOT NULL DEFAULT false
);

-- ── Medication Schedules ──────────────────────────────────────────────────────
-- One row per dose-time slot; avoids embedding JSON arrays in medications.
CREATE TABLE medication_schedules (
  id               TEXT PRIMARY KEY,
  medication_id    TEXT NOT NULL REFERENCES medications(id) ON DELETE CASCADE,
  scheduled_time   TEXT NOT NULL,                   -- "HH:mm" 24-hour storage
  created_at       BIGINT NOT NULL DEFAULT 0,
  last_modified_at BIGINT NOT NULL DEFAULT 0,
  is_deleted       BOOLEAN NOT NULL DEFAULT false
);

-- ── Medication Logs ───────────────────────────────────────────────────────────
CREATE TABLE medication_logs (
  id               TEXT PRIMARY KEY,
  medication_id    TEXT NOT NULL REFERENCES medications(id) ON DELETE CASCADE,
  schedule_id      TEXT REFERENCES medication_schedules(id) ON DELETE SET NULL,
  patient_id       TEXT NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  date             BIGINT NOT NULL,                 -- start-of-day epoch millis
  scheduled_time   TEXT NOT NULL,                   -- "HH:mm"
  taken_at         BIGINT,                          -- epoch millis when taken
  taken            BOOLEAN NOT NULL DEFAULT false,
  notes            TEXT,
  created_at       BIGINT NOT NULL DEFAULT 0,
  last_modified_at BIGINT NOT NULL DEFAULT 0,
  is_deleted       BOOLEAN NOT NULL DEFAULT false
);

-- ── Blood Pressure Readings ───────────────────────────────────────────────────
CREATE TABLE blood_pressure_readings (
  id                  TEXT PRIMARY KEY,
  patient_id          TEXT NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  systolic            INTEGER NOT NULL,
  diastolic           INTEGER NOT NULL,
  pulse               INTEGER,
  oxygen_saturation   INTEGER,
  recorded_at         BIGINT NOT NULL,
  notes               TEXT,
  created_at          BIGINT NOT NULL DEFAULT 0,
  last_modified_at    BIGINT NOT NULL DEFAULT 0,
  is_deleted          BOOLEAN NOT NULL DEFAULT false
);

-- ── Symptoms ──────────────────────────────────────────────────────────────────
CREATE TABLE symptoms (
  id                        TEXT PRIMARY KEY,
  patient_id                TEXT NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  symptom_type              TEXT NOT NULL,
  severity                  TEXT NOT NULL,
  recorded_at               BIGINT NOT NULL,
  inhaler_used              BOOLEAN NOT NULL DEFAULT false,
  improvement_after_inhaler BOOLEAN,
  notes                     TEXT,
  created_at                BIGINT NOT NULL DEFAULT 0,
  last_modified_at          BIGINT NOT NULL DEFAULT 0,
  is_deleted                BOOLEAN NOT NULL DEFAULT false
);

-- ── Row Level Security ────────────────────────────────────────────────────────
ALTER TABLE patients               ENABLE ROW LEVEL SECURITY;
ALTER TABLE medical_cases          ENABLE ROW LEVEL SECURITY;
ALTER TABLE medications            ENABLE ROW LEVEL SECURITY;
ALTER TABLE medication_schedules   ENABLE ROW LEVEL SECURITY;
ALTER TABLE medication_logs        ENABLE ROW LEVEL SECURITY;
ALTER TABLE blood_pressure_readings ENABLE ROW LEVEL SECURITY;
ALTER TABLE symptoms               ENABLE ROW LEVEL SECURITY;

-- Allow all operations for authenticated users (tighten per-user once auth is added).
CREATE POLICY "allow_all_patients"               ON patients               FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "allow_all_medical_cases"          ON medical_cases          FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "allow_all_medications"            ON medications            FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "allow_all_medication_schedules"   ON medication_schedules   FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "allow_all_medication_logs"        ON medication_logs        FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "allow_all_bp_readings"            ON blood_pressure_readings FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "allow_all_symptoms"               ON symptoms               FOR ALL USING (true) WITH CHECK (true);

-- ── Indexes ───────────────────────────────────────────────────────────────────
-- Entity-relationship indexes
CREATE INDEX idx_medical_cases_patient       ON medical_cases(patient_id);
CREATE INDEX idx_medications_patient         ON medications(patient_id);
CREATE INDEX idx_medications_case            ON medications(case_id);
CREATE INDEX idx_medication_schedules_med    ON medication_schedules(medication_id);
CREATE INDEX idx_medication_logs_patient     ON medication_logs(patient_id);
CREATE INDEX idx_medication_logs_date        ON medication_logs(date);
CREATE INDEX idx_bp_readings_patient         ON blood_pressure_readings(patient_id);
CREATE INDEX idx_symptoms_patient            ON symptoms(patient_id);

-- Sync indexes — used by incremental pull queries (WHERE last_modified_at > :since)
CREATE INDEX idx_patients_modified           ON patients(last_modified_at);
CREATE INDEX idx_medical_cases_modified      ON medical_cases(last_modified_at);
CREATE INDEX idx_medications_modified        ON medications(last_modified_at);
CREATE INDEX idx_medication_schedules_modified ON medication_schedules(last_modified_at);
CREATE INDEX idx_medication_logs_modified    ON medication_logs(last_modified_at);
CREATE INDEX idx_bp_readings_modified        ON blood_pressure_readings(last_modified_at);
CREATE INDEX idx_symptoms_modified           ON symptoms(last_modified_at);
