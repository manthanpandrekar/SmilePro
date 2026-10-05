package com.smilepro
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.Date

@Component
class Jwt(@Value("\${jwt.secret}") s: String) {
    private val key = Keys.hmacShaKeyFor(s.toByteArray())
    fun make(u: User): String = Jwts.builder().subject(u.email).claim("role", u.role)
        .expiration(Date(System.currentTimeMillis() + 86_400_000)).signWith(key).compact()
    fun parse(t: String) = Jwts.parser().verifyWith(key).build().parseSignedClaims(t).payload
}

@Component
class JwtFilter(val jwt: Jwt) : OncePerRequestFilter() {
    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        req.getHeader("Authorization")?.takeIf { it.startsWith("Bearer ") }?.let {
            try {
                val c = jwt.parse(it.substring(7))
                SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                    c.subject, null, listOf(SimpleGrantedAuthority("ROLE_" + c["role"])))
            } catch (e: Exception) { /* invalid token -> stays anonymous -> 401 */ }
        }
        chain.doFilter(req, res)
    }
}

@Configuration @EnableMethodSecurity
class SecConfig(val f: JwtFilter) {
    @Bean fun enc(): PasswordEncoder = BCryptPasswordEncoder()
    @Bean fun chain(h: HttpSecurity): SecurityFilterChain {
        h.csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/api/auth/**", "/api/health").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/dentists").permitAll()
                    .requestMatchers("/api/**").authenticated().anyRequest().permitAll()
            }
            .exceptionHandling { it.authenticationEntryPoint { _, r, _ -> r.sendError(401) } }
            .addFilterBefore(f, UsernamePasswordAuthenticationFilter::class.java)
        return h.build()
    }
}
