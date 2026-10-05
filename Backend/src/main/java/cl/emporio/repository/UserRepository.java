package cl.emporio.repository;
import cl.emporio.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface UserRepository extends JpaRepository<AppUser,Long> {
    Optional<AppUser> findByUsernameIgnoreCase(String username);
    List<AppUser> findAllByOrderByDisplayNameAsc();
}
