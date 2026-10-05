package cl.emporio.model;
import jakarta.persistence.*;

@Entity @Table(name="app_users")
public class AppUser {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false, unique=true, length=80) public String username;
    @Column(nullable=false, length=120) public String displayName;
    @Column(nullable=false, length=100) public String passwordHash;
    @Column(nullable=false, length=15) public String role;
    @Column(nullable=false) public boolean enabled=true;
    public AppUser() {}
}
