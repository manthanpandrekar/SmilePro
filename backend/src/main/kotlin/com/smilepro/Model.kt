package com.smilepro
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

@Entity @Table(name = "users")
class User(@Id @GeneratedValue var id: Long? = null, var name: String = "",
    @Column(unique = true) var email: String = "", @JsonIgnore var password: String = "", var role: String = "PATIENT")

@Entity
class Dentist(@Id @GeneratedValue var id: Long? = null, var name: String = "", var specialization: String = "")

@Entity
class Appointment(@Id @GeneratedValue var id: Long? = null, var patientId: Long = 0, var patientName: String = "",
    var dentistId: Long = 0, var dentistName: String = "", var date: LocalDate = LocalDate.now(),
    var time: String = "", var reason: String = "", var status: String = "PENDING")

interface UserRepo : JpaRepository<User, Long> { fun findByEmail(e: String): User? }
interface DentistRepo : JpaRepository<Dentist, Long>
interface AppointmentRepo : JpaRepository<Appointment, Long> {
    fun findByDentistIdAndDate(d: Long, dt: LocalDate): List<Appointment>
    fun findByPatientIdOrderByDateDesc(p: Long): List<Appointment>
    fun existsByDentistIdAndDateAndTimeAndStatusNot(d: Long, dt: LocalDate, t: String, s: String): Boolean
}
