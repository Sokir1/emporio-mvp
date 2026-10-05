package cl.emporio.repository;
import cl.emporio.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface LocationRepository extends JpaRepository<Location,Long> {
    List<Location> findAllByOrderByNameAsc();
    Optional<Location> findByNameIgnoreCase(String name);
}
