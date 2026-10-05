package cl.emporio.controller;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class Forms {
    private Forms() {}
    public static class ProductForm {
        @NotBlank @Size(max=40) @Pattern(regexp="[A-Za-z0-9_-]+", message="El código solo admite letras, números, guion y guion bajo") public String sku="";
        @NotBlank @Size(max=120) public String name="";
        @NotBlank public String category="Frutos secos";
        @NotBlank public String unit="kg";
        @NotNull @DecimalMin("0") @Digits(integer=9,fraction=3) public BigDecimal minimumStock=BigDecimal.ZERO;
        @NotNull @DecimalMin("0") @Digits(integer=12,fraction=0) public BigDecimal salePrice=BigDecimal.ZERO;
        @Size(max=500) public String description="";
    }
    public static class ReceiptForm {
        @NotNull public Long productId;
        @NotNull public Long locationId;
        public Long supplierId;
        @NotBlank @Size(max=60) public String code="";
        @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=9,fraction=3) public BigDecimal quantity;
        @NotNull @DecimalMin("0") @Digits(integer=12,fraction=0) public BigDecimal unitCost=BigDecimal.ZERO;
        @NotNull public LocalDate receivedOn;
        public LocalDate expiresOn;
        @NotBlank public String quality="APTO";
        @NotBlank @Size(max=250) public String reason="Recepción de mercadería";
    }
    public static class LocationForm {
        @NotBlank @Size(max=80) public String name="";
        @NotBlank @Size(max=250) public String description="";
    }
    public static class SupplierForm {
        @NotBlank @Size(max=120) public String name="";
        @Size(max=100) public String contact="";
        @Size(max=40) public String phone="";
        @Size(max=250) public String notes="";
    }
    public static class UserForm {
        @NotBlank @Size(max=80) @Pattern(regexp="[a-zA-Z0-9._-]+") public String username="";
        @NotBlank @Size(max=120) public String displayName="";
        @Size(min=10,max=72, message="La contraseña debe tener entre 10 y 72 caracteres") public String password;
        @NotBlank public String role="VENTAS";
    }
}
