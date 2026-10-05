package cl.emporio.repository;
import cl.emporio.model.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface ProductRepository extends JpaRepository<Product,Long> {
    List<Product> findAllByOrderByNameAsc();
    Optional<Product> findBySkuIgnoreCase(String sku);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from Product p where p.id = :id")
    Optional<Product> lockById(Long id);
}
