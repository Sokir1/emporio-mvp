package cl.emporio.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name="products")
public class Product {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false, unique=true, length=40) public String sku;
    @Column(nullable=false, length=120) public String name;
    @Column(nullable=false, length=40) public String category;
    @Column(nullable=false, length=10) public String unit;
    @Column(nullable=false, precision=12, scale=3) public BigDecimal minimumStock;
    @Column(nullable=false, precision=12, scale=0) public BigDecimal salePrice;
    @Column(nullable=false) public boolean active=true;
    @Column(length=500) public String description;
    public Product() {}
}
