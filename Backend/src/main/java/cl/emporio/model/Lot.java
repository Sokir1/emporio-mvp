package cl.emporio.model;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Table(name="lots")
public class Lot {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(optional=false) @JoinColumn(name="product_id") public Product product;
    @ManyToOne(optional=false) @JoinColumn(name="location_id") public Location location;
    @ManyToOne @JoinColumn(name="supplier_id") public Supplier supplier;
    @Column(nullable=false, length=60) public String code;
    @Column(nullable=false, precision=12, scale=3) public BigDecimal quantity;
    @Column(nullable=false, precision=12, scale=0) public BigDecimal unitCost;
    @Column(nullable=false) public LocalDate receivedOn;
    public LocalDate expiresOn;
    @Column(nullable=false, length=20) public String quality;
    public Lot() {}
    public boolean isExpired() { return expiresOn != null && expiresOn.isBefore(LocalDate.now(java.time.ZoneId.of("America/Santiago"))); }
}
