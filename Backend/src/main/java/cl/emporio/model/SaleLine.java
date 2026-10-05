package cl.emporio.model;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name="sale_lines")
public class SaleLine {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(optional=false) @JoinColumn(name="sale_id") public Sale sale;
    @ManyToOne(optional=false) @JoinColumn(name="lot_id") public Lot lot;
    @Column(nullable=false, precision=12, scale=3) public BigDecimal quantity;
    @Column(nullable=false, precision=12, scale=0) public BigDecimal unitPrice;
    @Column(nullable=false, precision=14, scale=0) public BigDecimal subtotal;
    public SaleLine() {}
}
