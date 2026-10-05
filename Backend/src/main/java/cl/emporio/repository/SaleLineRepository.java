package cl.emporio.repository;
import cl.emporio.model.SaleLine;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SaleLineRepository extends JpaRepository<SaleLine,Long> {
    List<SaleLine> findBySaleIdOrderByIdAsc(Long id);
}
