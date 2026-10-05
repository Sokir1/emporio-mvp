package cl.emporio.config;

import cl.emporio.repository.UserRepository;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService users(UserRepository repository) {
        return name -> repository.findByUsernameIgnoreCase(name).map(u ->
            User.withUsername(u.username).password(u.passwordHash).roles(u.role).disabled(!u.enabled).build()
        ).orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a -> a
                .requestMatchers("/assets/**","/login","/error").permitAll()
                .requestMatchers("/productos/nuevo","/productos/*/editar","/productos/*/estado", "/entradas/**", "/bodega/trasladar", "/bodega/ajustar", "/bodega/calidad", "/ubicaciones/**", "/proveedores/**", "/usuarios/**", "/ventas/*/anular").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(f -> f.loginPage("/login").defaultSuccessUrl("/",true).permitAll())
            .logout(l -> l.logoutSuccessUrl("/login?logout"))
            .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'self'; form-action 'self'")))
            .build();
    }
}
