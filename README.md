# Smile-Pro – Dentist Appointment System
Patients register, pick a dentist, see only free 30-minute slots (09:00–16:30) and book. Admin confirms/completes/cancels appointments, manages dentists and sees stats.

## Tech (Experiments 1–9 only)
HTML5/CSS3 (Exp 1) · REST + Spring Boot (2, 7) · React, JSX, Hooks, Context, custom `useForm` (3, 4) · Kotlin entities, data classes, collections (5, 6) · JWT + BCrypt + roles (8) · Docker + Render (9). DB: embedded H2 (3 tables: users, dentist, appointment).

## Architecture
React (built into Spring `static/`) -> `/api/*` -> JwtFilter -> Api controller -> Spring Data repos -> H2.
Rule: one dentist cannot have two non-cancelled appointments at the same date+time (HTTP 409).

## API (all under /api)
POST auth/register, auth/login · GET dentists · GET slots?dentistId&date · POST appointments · GET appointments/my · PUT appointments/{id}/cancel
Admin: POST/DELETE dentists · GET admin/appointments · PUT admin/appointments/{id}/status · GET admin/stats · GET health

## Run
Needs JDK 21, Maven, Node 20+.
    cd frontend && npm install && npm run build
    cd ../backend && mvn clean package -DskipTests
    java -jar target/smilepro.jar      # http://localhost:8080
Logins: admin@smilepro.com / admin123 · rahul@patient.com / patient123

## Test
1. Register with empty/invalid fields -> red errors. Register properly -> dashboard.
2. Patient: Book -> Dr. Meera Shah, tomorrow, slot 10:00, reason "Cleaning" -> success (PENDING).
3. Log in as another patient (register one), book same dentist/date/10:00 -> the slot is gone; API gives 409 if forced.
4. My appointments -> Cancel -> status CANCELLED; the slot becomes free again.
5. Admin: Appointments -> set CONFIRMED / COMPLETED; Dashboard counts update; Dentists -> add and delete.
6. API: patient token on POST /api/dentists -> 403; no token on /api/appointments/my -> 401.

## Deploy on Render
Push to GitHub -> Render > New > Blueprint -> select repo (reads render.yaml, builds Dockerfile). Free plan sleeps when idle and H2 data resets on restart (demo data is re-seeded).
Live URL: https://<your-service>.onrender.com
