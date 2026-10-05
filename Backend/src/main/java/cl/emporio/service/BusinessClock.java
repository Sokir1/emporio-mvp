package cl.emporio.service;
import org.springframework.stereotype.Component;
import java.time.*;

@Component
public class BusinessClock {
    public LocalDate today() { return LocalDate.now(ZoneId.of("America/Santiago")); }
    public LocalDateTime now() { return LocalDateTime.now(ZoneId.of("America/Santiago")); }
}
