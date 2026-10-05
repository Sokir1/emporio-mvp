package cl.emporio.repository;
import cl.emporio.model.Sale;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface SaleRepository extends JpaRepository<Sale,Long> {
    List<Sale> findTop100ByOrderByIdDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from Sale s where s.id = :id")
    Optional<Sale> lockById(Long id);
}
