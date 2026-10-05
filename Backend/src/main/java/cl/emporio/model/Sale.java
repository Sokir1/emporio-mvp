package cl.emporio.model;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name="sales")
public class Sale {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false) public LocalDateTime createdAt;
    @Column(nullable=false, length=80) public String actor;
    @Column(nullable=false, length=20) public String paymentMethod;
    @Column(nullable=false, precision=14, scale=0) public BigDecimal total;
    @Column(nullable=false) public boolean cancelled;
    @Column(length=250) public String cancellationReason;
    public Sale() {}
}
