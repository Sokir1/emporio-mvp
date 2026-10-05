package cl.emporio.model;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name="movements")
public class Movement {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(optional=false) @JoinColumn(name="lot_id") public Lot lot;
    @ManyToOne @JoinColumn(name="sale_id") public Sale sale;
    @Column(nullable=false) public LocalDateTime createdAt;
    @Column(nullable=false, length=30) public String kind;
    @Column(nullable=false, precision=12, scale=3) public BigDecimal quantity;
    @Column(nullable=false, length=80) public String actor;
    @Column(nullable=false, length=250) public String reason;
    public Movement() {}
}
