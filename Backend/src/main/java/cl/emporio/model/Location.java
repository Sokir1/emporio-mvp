package cl.emporio.model;
import jakarta.persistence.*;

@Entity @Table(name="locations")
public class Location {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false, unique=true, length=80) public String name;
    @Column(nullable=false, length=250) public String description;
    public Location() {}
}
