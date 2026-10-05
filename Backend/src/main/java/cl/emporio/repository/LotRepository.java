package cl.emporio.repository;
import cl.emporio.model.Lot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LotRepository extends JpaRepository<Lot,Long> {
    List<Lot> findByProductIdOrderByIdAsc(Long id);
    List<Lot> findAllByOrderByIdDesc();
}
