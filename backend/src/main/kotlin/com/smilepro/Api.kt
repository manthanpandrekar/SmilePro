package com.smilepro
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

const val ADM = "hasRole('ADMIN')"
data class Reg(@field:NotBlank val name: String, @field:Email val email: String, @field:Size(min = 6) val password: String)
data class Log(val email: String, val password: String)
data class Book(val dentistId: Long, val date: LocalDate, val time: String, val reason: String)

@SpringBootApplication class App
fun main(a: Array<String>) { runApplication<App>(*a) }

@RestController @RequestMapping("/api")
class Api(val users: UserRepo, val dentists: DentistRepo, val appts: AppointmentRepo, val enc: PasswordEncoder, val jwt: Jwt) {
    val slots = (9..16).flatMap { h -> listOf("%02d:00".format(h), "%02d:30".format(h)) }
    private fun err(s: HttpStatus, m: String): Nothing = throw ResponseStatusException(s, m)
    private fun auth(u: User) = mapOf("token" to jwt.make(u), "user" to u)
    private fun me(a: Authentication) = users.findByEmail(a.name)!!

    @GetMapping("/health") fun health() = mapOf("status" to "UP", "app" to "Smile-Pro")

    @PostMapping("/auth/register") fun reg(@Valid @RequestBody r: Reg): Map<String, Any> {
        if (users.findByEmail(r.email) != null) err(HttpStatus.CONFLICT, "Email already registered")
        return auth(users.save(User(name = r.name, email = r.email, password = enc.encode(r.password)!!)))
    }
    @PostMapping("/auth/login") fun login(@RequestBody l: Log): Map<String, Any> {
        val u = users.findByEmail(l.email)
        if (u == null || !enc.matches(l.password, u.password)) err(HttpStatus.UNAUTHORIZED, "Invalid email or password")
        return auth(u)
    }

    @GetMapping("/dentists") fun dentistList(): List<Dentist> = dentists.findAll()
    @PostMapping("/dentists") @PreAuthorize(ADM)
    fun addDentist(@RequestBody d: Dentist): Dentist {
        if (d.name.isBlank()) err(HttpStatus.BAD_REQUEST, "Name is required")
        return dentists.save(Dentist(name = d.name, specialization = d.specialization))
    }
    @DeleteMapping("/dentists/{id}") @PreAuthorize(ADM) fun delDentist(@PathVariable id: Long) = dentists.deleteById(id)

    @GetMapping("/slots") fun free(@RequestParam dentistId: Long, @RequestParam date: LocalDate): List<String> {
        val taken = appts.findByDentistIdAndDate(dentistId, date).filter { it.status != "CANCELLED" }.map { it.time }.toSet()
        return slots.filter { it !in taken }
    }

    @PostMapping("/appointments") fun book(@RequestBody b: Book, a: Authentication): Appointment {
        val u = me(a)
        val d = dentists.findById(b.dentistId).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Dentist not found") }
        if (b.date.isBefore(LocalDate.now()) || b.time !in slots || b.reason.isBlank()) err(HttpStatus.BAD_REQUEST, "Invalid date, time or reason")
        if (appts.existsByDentistIdAndDateAndTimeAndStatusNot(d.id!!, b.date, b.time, "CANCELLED"))
            err(HttpStatus.CONFLICT, "Slot ${b.time} with ${d.name} is already booked")
        return appts.save(Appointment(patientId = u.id!!, patientName = u.name, dentistId = d.id!!, dentistName = d.name,
            date = b.date, time = b.time, reason = b.reason))
    }
    @GetMapping("/appointments/my") fun mine(a: Authentication) = appts.findByPatientIdOrderByDateDesc(me(a).id!!)

    @PutMapping("/appointments/{id}/cancel") fun cancel(@PathVariable id: Long, a: Authentication): Appointment {
        val ap = appts.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Not found") }
        val u = me(a)
        if (ap.patientId != u.id && u.role != "ADMIN") err(HttpStatus.FORBIDDEN, "Not your appointment")
        ap.status = "CANCELLED"; return appts.save(ap)
    }

    @GetMapping("/admin/appointments") @PreAuthorize(ADM) fun all(): List<Appointment> = appts.findAll().sortedByDescending { it.date }
    @PutMapping("/admin/appointments/{id}/status") @PreAuthorize(ADM)
    fun setStatus(@PathVariable id: Long, @RequestBody m: Map<String, String>): Appointment {
        val s = m["status"] ?: ""
        if (s !in listOf("PENDING", "CONFIRMED", "COMPLETED", "CANCELLED")) err(HttpStatus.BAD_REQUEST, "Bad status")
        val ap = appts.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Not found") }
        ap.status = s; return appts.save(ap)
    }
    @GetMapping("/admin/stats") @PreAuthorize(ADM) fun stats(): Map<String, Any> {
        val a = appts.findAll()
        return mapOf("total" to a.size, "patients" to users.count(), "dentists" to dentists.count(),
            "byStatus" to a.groupingBy { it.status }.eachCount(), "byDentist" to a.groupingBy { it.dentistName }.eachCount())
    }

    @Bean fun seed(): CommandLineRunner = CommandLineRunner {
        users.save(User(name = "Admin", email = "admin@smilepro.com", password = enc.encode("admin123")!!, role = "ADMIN"))
        users.save(User(name = "Rahul Patil", email = "rahul@patient.com", password = enc.encode("patient123")!!))
        dentists.saveAll(listOf(Dentist(name = "Dr. Meera Shah", specialization = "Orthodontist"),
            Dentist(name = "Dr. Arjun Rao", specialization = "Endodontist"), Dentist(name = "Dr. Neha Joshi", specialization = "General Dentist")))
    }
}
