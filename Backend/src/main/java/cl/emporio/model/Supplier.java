package cl.emporio.model;
import jakarta.persistence.*;

@Entity @Table(name="suppliers")
public class Supplier {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false, length=120) public String name;
    @Column(nullable=false, length=100) public String contact;
    @Column(nullable=false, length=40) public String phone;
    @Column(nullable=false, length=250) public String notes;
    public Supplier() {}
}
