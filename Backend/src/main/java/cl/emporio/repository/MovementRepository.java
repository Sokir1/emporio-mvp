package cl.emporio.repository;
import cl.emporio.model.Movement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface MovementRepository extends JpaRepository<Movement,Long> {
    List<Movement> findTop200ByOrderByIdDesc();
    List<Movement> findByLotProductIdOrderByIdDesc(Long id);
}
