package cl.emporio.service;

import cl.emporio.controller.Forms;
import cl.emporio.model.*;
import cl.emporio.repository.*;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional
public class InventoryService {
    public static final List<String> CATEGORIES=List.of("Frutos secos","Quesos","Semillas","Deshidratados","Otros");
    private final ProductRepository products;
    private final LotRepository lots;
    private final LocationRepository locations;
    private final SupplierRepository suppliers;
    private final SaleRepository sales;
    private final SaleLineRepository lines;
    private final MovementRepository movements;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final BusinessClock clock;
    private final EntityManager em;

    public InventoryService(ProductRepository products, LotRepository lots, LocationRepository locations,
        SupplierRepository suppliers, SaleRepository sales, SaleLineRepository lines,
        MovementRepository movements, UserRepository users, PasswordEncoder passwords,
        BusinessClock clock, EntityManager em) {
        this.products=products; this.lots=lots; this.locations=locations; this.suppliers=suppliers;
        this.sales=sales; this.lines=lines; this.movements=movements; this.users=users;
        this.passwords=passwords; this.clock=clock; this.em=em;
    }

    public Product saveProduct(Long id, Forms.ProductForm f) {
        Product p=id==null ? new Product() : lockProduct(id);
        String sku=required(f.sku,40,"Código").toUpperCase(Locale.ROOT);
        if (!sku.matches("[A-Z0-9_-]+")) throw new BusinessException("El código contiene caracteres no permitidos.");
        products.findBySkuIgnoreCase(sku).filter(other -> !Objects.equals(other.id,id))
            .ifPresent(other -> { throw new BusinessException("Ese código ya está registrado."); });
        if (!CATEGORIES.contains(f.category) || !List.of("kg","unidad").contains(f.unit))
            throw new BusinessException("Categoría o unidad no válida.");
        if (id!=null && !p.unit.equals(f.unit) && !lots.findByProductIdOrderByIdAsc(id).isEmpty())
            throw new BusinessException("No se puede cambiar la unidad de un producto que ya tiene movimientos.");
        p.sku=sku; p.name=required(f.name,120,"Nombre"); p.category=f.category; p.unit=f.unit;
        p.minimumStock=quantity(f.minimumStock,p.unit,true); p.salePrice=money(f.salePrice);
        p.description=optional(f.description,500); return products.save(p);
    }

    public void toggleProduct(Long id) {
        Product p=lockProduct(id);
        if (p.active && lots.findByProductIdOrderByIdAsc(id).stream().anyMatch(l -> l.quantity.signum()>0))
            throw new BusinessException("El producto todavía tiene existencias. Ajusta sus lotes antes de archivarlo.");
        p.active=!p.active;
    }

    public Location saveLocation(Long id, Forms.LocationForm f) {
        Location l=id==null ? new Location() : locations.findById(id).orElseThrow(() -> new BusinessException("Ubicación no encontrada."));
        String name=required(f.name,80,"Nombre");
        locations.findByNameIgnoreCase(name).filter(other -> !Objects.equals(other.id,id))
            .ifPresent(other -> { throw new BusinessException("Ya existe una ubicación con ese nombre."); });
        l.name=name; l.description=required(f.description,250,"Referencia para encontrar los productos");
        return locations.save(l);
    }

    public Supplier saveSupplier(Long id, Forms.SupplierForm f) {
        Supplier s=id==null ? new Supplier() : suppliers.findById(id).orElseThrow(() -> new BusinessException("Proveedor no encontrado."));
        s.name=required(f.name,120,"Nombre"); s.contact=optional(f.contact,100);
        s.phone=optional(f.phone,40); s.notes=optional(f.notes,250); return suppliers.save(s);
    }

    public Lot receive(Forms.ReceiptForm f, String actor) {
        Product p=lockProduct(f.productId);
        if (!p.active) throw new BusinessException("El producto está archivado.");
        if (f.receivedOn==null || f.receivedOn.isAfter(clock.today())) throw new BusinessException("La fecha de recepción no puede ser futura.");
        if (f.expiresOn!=null && (f.expiresOn.isBefore(f.receivedOn) || f.expiresOn.isBefore(clock.today())))
            throw new BusinessException("No puedes recibir un lote vencido o con vencimiento anterior a su recepción.");
        if (!List.of("APTO","RETENIDO").contains(f.quality)) throw new BusinessException("Estado de calidad no válido.");
        Lot l=new Lot(); l.product=p;
        l.location=locations.findById(f.locationId).orElseThrow(() -> new BusinessException("Ubicación no encontrada."));
        l.supplier=f.supplierId==null ? null : suppliers.findById(f.supplierId).orElseThrow(() -> new BusinessException("Proveedor no encontrado."));
        l.code=required(f.code,60,"Lote"); l.quantity=quantity(f.quantity,p.unit,false);
        l.unitCost=money(f.unitCost); l.receivedOn=f.receivedOn; l.expiresOn=f.expiresOn; l.quality=f.quality;
        lots.save(l); audit(l,"ENTRADA",l.quantity,actor,required(f.reason,250,"Referencia"),null); return l;
    }

    public void transfer(Long lotId, Long destinationId, BigDecimal amount, String reason, String actor) {
        Lot source=lockedLot(lotId);
        BigDecimal q=quantity(amount,source.product.unit,false);
        if (Objects.equals(source.location.id,destinationId)) throw new BusinessException("El destino debe ser una ubicación diferente.");
        if (q.compareTo(source.quantity)>0) throw new BusinessException("No hay suficiente stock en ese lote.");
        Location destination=locations.findById(destinationId).orElseThrow(() -> new BusinessException("Destino no encontrado."));
        reason=required(reason,250,"Motivo");
        Lot target=new Lot(); target.product=source.product; target.location=destination; target.supplier=source.supplier;
        target.code=source.code; target.quantity=q; target.unitCost=source.unitCost;
        target.receivedOn=source.receivedOn; target.expiresOn=source.expiresOn; target.quality=source.quality;
        lots.save(target); source.quantity=source.quantity.subtract(q);
        audit(source,"TRASLADO SALIDA",q.negate(),actor,reason,null);
        audit(target,"TRASLADO ENTRADA",q,actor,reason,null);
    }

    public void adjust(Long lotId, BigDecimal counted, String reason, String actor) {
        Lot l=lockedLot(lotId); BigDecimal q=quantity(counted,l.product.unit,true);
        reason=required(reason,250,"Motivo del conteo o merma");
        BigDecimal delta=q.subtract(l.quantity);
        if (delta.signum()==0) throw new BusinessException("El conteo coincide con el stock actual. No hay ajuste que registrar.");
        l.quantity=q; audit(l,"AJUSTE",delta,actor,reason,null);
    }

    public void quality(Long lotId, String quality, String reason, String actor) {
        Lot l=lockedLot(lotId);
        if (!List.of("APTO","RETENIDO").contains(quality)) throw new BusinessException("Estado de calidad no válido.");
        if (l.quality.equals(quality)) throw new BusinessException("El lote ya tiene ese estado.");
        l.quality=quality; audit(l,"CALIDAD",BigDecimal.ZERO,actor,required(reason,220,"Motivo")+" ["+quality+"]",null);
    }

    public record SaleItem(Long productId, BigDecimal quantity) {}
    public Sale sell(Long locationId, List<SaleItem> items, String payment, String actor) {
        if (!List.of("EFECTIVO","TARJETA","TRANSFERENCIA").contains(payment)) throw new BusinessException("Medio de pago no válido.");
        if (items==null || items.isEmpty() || items.size()>50) throw new BusinessException("Agrega entre 1 y 50 productos a la venta.");
        if (!locations.existsById(locationId)) throw new BusinessException("Ubicación no encontrada.");
        // Todas las escrituras de stock bloquean los productos, siempre en orden de id.
        // Así dos ventas no pueden consumir simultáneamente las mismas existencias.
        SortedMap<Long,BigDecimal> requested=new TreeMap<>();
        for (SaleItem item:items) {
            if (item.productId()==null || item.quantity()==null) throw new BusinessException("Completa cada producto y cantidad.");
            if (item.quantity().signum()<=0) throw new BusinessException("La cantidad debe ser mayor que cero.");
            requested.merge(item.productId(),item.quantity(),BigDecimal::add);
        }
        Map<Long,Product> locked=new LinkedHashMap<>();
        requested.keySet().forEach(id -> locked.put(id,lockProduct(id)));
        Sale sale=new Sale(); sale.createdAt=clock.now(); sale.actor=actor; sale.paymentMethod=payment; sale.total=BigDecimal.ZERO;
        sales.save(sale);
        for (Map.Entry<Long,BigDecimal> entry:requested.entrySet()) {
            Product p=locked.get(entry.getKey());
            if (!p.active) throw new BusinessException("El producto "+p.name+" está archivado.");
            BigDecimal remaining=quantity(entry.getValue(),p.unit,false);
            List<Lot> eligible=lots.findByProductIdOrderByIdAsc(p.id).stream()
                .filter(l -> Objects.equals(l.location.id,locationId) && available(l,clock.today()))
                .sorted(Comparator.comparing((Lot l)->l.expiresOn,Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(l->l.receivedOn).thenComparing(l->l.id)).toList();
            BigDecimal stock=eligible.stream().map(l->l.quantity).reduce(BigDecimal.ZERO,BigDecimal::add);
            if (remaining.compareTo(stock)>0) throw new BusinessException("Stock insuficiente de "+p.name+" en la ubicación elegida. Disponible: "+stock.stripTrailingZeros().toPlainString()+" "+p.unit+".");
            BigDecimal productSubtotal=remaining.multiply(p.salePrice).setScale(0,RoundingMode.HALF_UP);
            BigDecimal allocated=BigDecimal.ZERO;
            for (Lot lot:eligible) {
                if (remaining.signum()==0) break;
                BigDecimal taken=remaining.min(lot.quantity); remaining=remaining.subtract(taken);
                SaleLine line=new SaleLine(); line.sale=sale; line.lot=lot; line.quantity=taken; line.unitPrice=p.salePrice;
                // El último tramo recibe el residuo: dividir una venta entre lotes no altera su precio.
                line.subtotal=remaining.signum()==0 ? productSubtotal.subtract(allocated) : taken.multiply(p.salePrice).setScale(0,RoundingMode.DOWN);
                allocated=allocated.add(line.subtotal); lines.save(line);
                lot.quantity=lot.quantity.subtract(taken); audit(lot,"VENTA",taken.negate(),actor,"Venta #"+sale.id,sale);
            }
            sale.total=sale.total.add(productSubtotal);
        }
        return sale;
    }

    public void cancelSale(Long id, String reason, String actor) {
        Sale sale=sales.lockById(id).orElseThrow(() -> new BusinessException("Venta no encontrada."));
        if (sale.cancelled) throw new BusinessException("Esta venta ya está anulada.");
        reason=required(reason,250,"Motivo de anulación");
        List<SaleLine> saleLines=lines.findBySaleIdOrderByIdAsc(id);
        saleLines.stream().map(l -> l.lot.product.id).distinct().sorted().forEach(this::lockProduct);
        for (SaleLine line:saleLines) {
            em.refresh(line.lot);
            line.lot.quantity=line.lot.quantity.add(line.quantity);
            audit(line.lot,"ANULACIÓN",line.quantity,actor,reason,sale);
            em.flush(); // Un producto puede haber sido vendido desde varios tramos del mismo lote.
        }
        sale.cancelled=true; sale.cancellationReason=reason;
    }

    public AppUser createUser(Forms.UserForm f) {
        if (f.password==null || f.password.length()<10 || f.password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new BusinessException("La contraseña debe tener al menos 10 caracteres y hasta 72 bytes.");
        String username=required(f.username,80,"Usuario").toLowerCase(Locale.ROOT);
        if (!username.matches("[a-z0-9._-]+")) throw new BusinessException("Nombre de usuario no válido.");
        if (users.findByUsernameIgnoreCase(username).isPresent()) throw new BusinessException("El usuario ya existe.");
        if (!List.of("ADMIN","VENTAS").contains(f.role)) throw new BusinessException("Rol no válido.");
        AppUser u=new AppUser(); u.username=username; u.displayName=required(f.displayName,120,"Nombre");
        u.role=f.role; u.passwordHash=passwords.encode(f.password); return users.save(u);
    }

    public void changePassword(String username,String currentPassword,String newPassword,String confirmation) {
        AppUser u=users.findByUsernameIgnoreCase(username).orElseThrow(() -> new BusinessException("Usuario no encontrado."));
        if (currentPassword==null || !passwords.matches(currentPassword,u.passwordHash))
            throw new BusinessException("La contraseña actual no es correcta.");
        if (newPassword==null || newPassword.length()<10 || newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new BusinessException("La nueva contraseña debe tener al menos 10 caracteres y hasta 72 bytes.");
        if (!newPassword.equals(confirmation)) throw new BusinessException("Las nuevas contraseñas no coinciden.");
        if (passwords.matches(newPassword,u.passwordHash)) throw new BusinessException("Elige una contraseña diferente de la actual.");
        u.passwordHash=passwords.encode(newPassword);
    }

    private Product lockProduct(Long id) {
        if (id==null) throw new BusinessException("Selecciona un producto.");
        Product p=products.lockById(id).orElseThrow(() -> new BusinessException("Producto no encontrado."));
        em.refresh(p); return p;
    }
    private Lot lockedLot(Long id) {
        Lot l=lots.findById(id).orElseThrow(() -> new BusinessException("Lote no encontrado."));
        lockProduct(l.product.id); em.refresh(l); return l;
    }
    private void audit(Lot lot,String kind,BigDecimal q,String actor,String reason,Sale sale) {
        Movement m=new Movement(); m.lot=lot; m.kind=kind; m.quantity=q; m.createdAt=clock.now();
        m.actor=actor; m.reason=reason; m.sale=sale; movements.save(m);
    }
    public static boolean available(Lot l, LocalDate today) {
        return l.quantity.signum()>0 && "APTO".equals(l.quality) && (l.expiresOn==null || !l.expiresOn.isBefore(today));
    }
    public static BigDecimal quantity(BigDecimal n,String unit,boolean allowZero) {
        if (n==null || n.signum()<0 || (!allowZero && n.signum()==0) || n.compareTo(new BigDecimal("999999999.999"))>0)
            throw new BusinessException("Cantidad fuera de rango.");
        if (n.stripTrailingZeros().scale()>3 || ("unidad".equals(unit) && n.stripTrailingZeros().scale()>0))
            throw new BusinessException("Usa hasta 3 decimales para kg y cantidades enteras para unidades.");
        return n.setScale(3);
    }
    private static BigDecimal money(BigDecimal n) {
        if (n==null || n.signum()<0 || n.compareTo(new BigDecimal("999999999999"))>0 || n.stripTrailingZeros().scale()>0)
            throw new BusinessException("El precio debe ser un monto entero en pesos chilenos, mayor o igual a cero.");
        return n.setScale(0);
    }
    private static String required(String s,int max,String field) {
        if (s==null || s.isBlank() || s.trim().length()>max) throw new BusinessException(field+": completa un valor de hasta "+max+" caracteres.");
        return s.trim();
    }
    private static String optional(String s,int max) {
        if (s!=null && s.trim().length()>max) throw new BusinessException("El texto supera el máximo de "+max+" caracteres.");
        return s==null ? "" : s.trim();
    }
}
