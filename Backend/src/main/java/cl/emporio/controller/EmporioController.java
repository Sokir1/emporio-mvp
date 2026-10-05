package cl.emporio.controller;

import cl.emporio.model.*;
import cl.emporio.repository.*;
import cl.emporio.service.*;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.util.*;
import java.nio.charset.StandardCharsets;

@Controller
public class EmporioController {
    private final InventoryService inventory;
    private final InventoryQueries queries;
    private final ProductRepository products;
    private final LotRepository lots;
    private final LocationRepository locations;
    private final SupplierRepository suppliers;
    private final SaleRepository sales;
    private final SaleLineRepository lines;
    private final MovementRepository movements;
    private final UserRepository users;
    private final BusinessClock clock;
    public EmporioController(InventoryService inventory,InventoryQueries queries,ProductRepository products,
        LotRepository lots,LocationRepository locations,SupplierRepository suppliers,SaleRepository sales,
        SaleLineRepository lines,MovementRepository movements,UserRepository users,BusinessClock clock) {
        this.inventory=inventory; this.queries=queries; this.products=products; this.lots=lots;
        this.locations=locations; this.suppliers=suppliers; this.sales=sales; this.lines=lines;
        this.movements=movements; this.users=users; this.clock=clock;
    }
    @GetMapping("/login") String login() { return "login"; }
    @GetMapping("/mi-cuenta") String account(Model m) { return page(m,"Mi cuenta","account"); }
    @PostMapping("/mi-cuenta/clave") String password(@RequestParam String currentPassword,@RequestParam String newPassword,@RequestParam String confirmation,Authentication a,HttpSession session) {
        inventory.changePassword(a.getName(),currentPassword,newPassword,confirmation);
        session.invalidate(); return "redirect:/login?passwordChanged";
    }
    @GetMapping("/") String dashboard(Model m) {
        m.addAttribute("dashboard",queries.dashboard()); m.addAttribute("recent",movements.findTop200ByOrderByIdDesc().stream().limit(6).toList());
        return page(m,"Resumen","dashboard");
    }
    @GetMapping("/productos") String products(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String category,@RequestParam(defaultValue="") String status,Model m) {
        m.addAttribute("rows",queries.stock(q,category,status)); m.addAttribute("q",q); m.addAttribute("category",category); m.addAttribute("status",status);
        return page(m,"Productos","products");
    }
    @GetMapping("/productos/{id}") String detail(@PathVariable Long id,Model m) {
        Product p=products.findById(id).orElseThrow(EmporioController::notFound);
        m.addAttribute("product",p); m.addAttribute("lots",lots.findByProductIdOrderByIdAsc(id));
        m.addAttribute("history",movements.findByLotProductIdOrderByIdDesc(id).stream().limit(100).toList());
        return page(m,p.name,"product-detail");
    }
    @GetMapping("/productos/nuevo") String newProduct(Model m) {
        m.addAttribute("form",new Forms.ProductForm()); m.addAttribute("editing",false); return page(m,"Nuevo producto","product-form");
    }
    @GetMapping("/productos/{id}/editar") String editProduct(@PathVariable Long id,Model m) {
        Product p=products.findById(id).orElseThrow(EmporioController::notFound);
        Forms.ProductForm f=new Forms.ProductForm(); f.sku=p.sku; f.name=p.name; f.category=p.category; f.unit=p.unit;
        f.minimumStock=p.minimumStock; f.salePrice=p.salePrice; f.description=p.description;
        m.addAttribute("form",f); m.addAttribute("editing",true); m.addAttribute("id",id); return page(m,"Editar producto","product-form");
    }
    @PostMapping("/productos/nuevo") String createProduct(@Valid @ModelAttribute("form") Forms.ProductForm f,BindingResult result,Model m,RedirectAttributes flash) {
        if (result.hasErrors()) { m.addAttribute("editing",false); return page(m,"Nuevo producto","product-form"); }
        Product p=inventory.saveProduct(null,f); flash.addFlashAttribute("success","Producto registrado."); return "redirect:/productos/"+p.id;
    }
    @PostMapping("/productos/{id}/editar") String updateProduct(@PathVariable Long id,@Valid @ModelAttribute("form") Forms.ProductForm f,BindingResult result,Model m,RedirectAttributes flash) {
        if (result.hasErrors()) { m.addAttribute("editing",true); m.addAttribute("id",id); return page(m,"Editar producto","product-form"); }
        inventory.saveProduct(id,f); flash.addFlashAttribute("success","Producto actualizado."); return "redirect:/productos/"+id;
    }
    @PostMapping("/productos/{id}/estado") String toggleProduct(@PathVariable Long id,RedirectAttributes f) {
        inventory.toggleProduct(id); f.addFlashAttribute("success","Estado del producto actualizado."); return "redirect:/productos/"+id;
    }
    @GetMapping("/entradas/nueva") String newReceipt(@RequestParam(required=false) Long productId,Model m) {
        Forms.ReceiptForm f=new Forms.ReceiptForm(); f.productId=productId; f.receivedOn=clock.today();
        m.addAttribute("form",f); receiptChoices(m); return page(m,"Registrar entrada","receipt-form");
    }
    @PostMapping("/entradas/nueva") String receipt(@Valid @ModelAttribute("form") Forms.ReceiptForm f,BindingResult result,Model m,Authentication auth,RedirectAttributes flash) {
        if (result.hasErrors()) { receiptChoices(m); return page(m,"Registrar entrada","receipt-form"); }
        inventory.receive(f,auth.getName()); flash.addFlashAttribute("success","Entrada registrada. El stock ya está actualizado."); return "redirect:/bodega";
    }
    private void receiptChoices(Model m) {
        m.addAttribute("products",products.findAllByOrderByNameAsc().stream().filter(p->p.active).toList());
        m.addAttribute("locations",locations.findAllByOrderByNameAsc()); m.addAttribute("suppliers",suppliers.findAllByOrderByNameAsc());
    }
    @GetMapping("/bodega") String warehouse(@RequestParam(required=false) Long locationId,@RequestParam(defaultValue="") String q,Model m) {
        String search=q.toLowerCase(Locale.ROOT);
        List<Lot> ls=queries.positiveLots().stream().filter(l->locationId==null || Objects.equals(l.location.id,locationId))
            .filter(l->(l.product.name+" "+l.product.sku+" "+l.code).toLowerCase(Locale.ROOT).contains(search)).toList();
        m.addAttribute("lots",ls); m.addAttribute("locations",locations.findAllByOrderByNameAsc()); m.addAttribute("locationId",locationId); m.addAttribute("q",q);
        return page(m,"Bodega","warehouse");
    }
    @PostMapping("/bodega/trasladar") String transfer(@RequestParam Long lotId,@RequestParam Long destinationId,@RequestParam BigDecimal quantity,@RequestParam String reason,Authentication a,RedirectAttributes f) {
        inventory.transfer(lotId,destinationId,quantity,reason,a.getName()); f.addFlashAttribute("success","Traslado registrado."); return "redirect:/bodega";
    }
    @PostMapping("/bodega/ajustar") String adjust(@RequestParam Long lotId,@RequestParam BigDecimal counted,@RequestParam String reason,Authentication a,RedirectAttributes f) {
        inventory.adjust(lotId,counted,reason,a.getName()); f.addFlashAttribute("success","Conteo registrado y stock ajustado."); return "redirect:/bodega";
    }
    @PostMapping("/bodega/calidad") String quality(@RequestParam Long lotId,@RequestParam String quality,@RequestParam String reason,Authentication a,RedirectAttributes f) {
        inventory.quality(lotId,quality,reason,a.getName()); f.addFlashAttribute("success","Estado de calidad actualizado."); return "redirect:/bodega";
    }
    @GetMapping("/alertas") String alerts(Model m) {
        m.addAttribute("low",queries.stock("","","").stream().filter(r->!r.status().equals("NORMAL")).toList());
        m.addAttribute("expiring",queries.expiryLots()); return page(m,"Alertas","alerts");
    }
    @GetMapping("/movimientos") String movements(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String kind,Model m) {
        String search=q.toLowerCase(Locale.ROOT);
        m.addAttribute("movements",movements.findTop200ByOrderByIdDesc().stream()
            .filter(v -> (v.lot.product.name+" "+v.lot.code+" "+v.actor+" "+v.reason).toLowerCase(Locale.ROOT).contains(search))
            .filter(v -> kind.isBlank() || v.kind.equals(kind)).toList());
        m.addAttribute("q",q); m.addAttribute("kind",kind); return page(m,"Movimientos","movements");
    }
    @GetMapping("/ventas") String sales(Model m) {
        m.addAttribute("sales",sales.findTop100ByOrderByIdDesc()); return page(m,"Ventas","sales");
    }
    @GetMapping("/ventas/nueva") String newSale(Model m,HttpSession session) {
        m.addAttribute("products",queries.stock("","","").stream().filter(r->r.product().active).toList());
        m.addAttribute("locations",locations.findAllByOrderByNameAsc());
        String token=UUID.randomUUID().toString(); session.setAttribute("saleToken",token); m.addAttribute("saleToken",token);
        return page(m,"Nueva venta","sale-form");
    }
    @PostMapping("/ventas/nueva") String sell(@RequestParam Long locationId,@RequestParam List<Long> productIds,@RequestParam List<BigDecimal> quantities,
        @RequestParam String payment,@RequestParam String saleToken,Authentication a,HttpSession session,RedirectAttributes flash) {
        if (productIds.size()!=quantities.size()) throw new BusinessException("Completa todas las líneas de la venta.");
        List<InventoryService.SaleItem> items=new ArrayList<>();
        for (int i=0;i<productIds.size();i++) items.add(new InventoryService.SaleItem(productIds.get(i),quantities.get(i)));
        Sale sale;
        synchronized(session) {
            if (!saleToken.equals(session.getAttribute("saleToken"))) throw new BusinessException("Esta venta ya fue enviada o su formulario venció. Abre una nueva venta.");
            sale=inventory.sell(locationId,items,payment,a.getName()); session.removeAttribute("saleToken");
        }
        flash.addFlashAttribute("success","Venta registrada y stock descontado."); return "redirect:/ventas/"+sale.id;
    }
    @GetMapping("/ventas/{id}") String saleDetail(@PathVariable Long id,Model m) {
        m.addAttribute("sale",sales.findById(id).orElseThrow(EmporioController::notFound));
        m.addAttribute("lines",lines.findBySaleIdOrderByIdAsc(id)); return page(m,"Detalle de venta #"+id,"sale-detail");
    }
    @PostMapping("/ventas/{id}/anular") String cancel(@PathVariable Long id,@RequestParam String reason,Authentication a,RedirectAttributes f) {
        inventory.cancelSale(id,reason,a.getName()); f.addFlashAttribute("success","Venta anulada. Se restituyeron las cantidades a sus lotes originales."); return "redirect:/ventas/"+id;
    }
    @GetMapping("/ubicaciones") String locations(Model m) { return locationPage(null,m); }
    @GetMapping("/ubicaciones/{id}/editar") String locationEdit(@PathVariable Long id,Model m) { return locationPage(id,m); }
    private String locationPage(Long id,Model m) {
        Forms.LocationForm f=new Forms.LocationForm();
        if (id!=null) { Location l=locations.findById(id).orElseThrow(EmporioController::notFound); f.name=l.name; f.description=l.description; }
        m.addAttribute("form",f); return catalog(m,"locations","Ubicaciones",locations.findAllByOrderByNameAsc(),id);
    }
    @PostMapping("/ubicaciones") String saveLocation(@RequestParam(required=false) Long id,@Valid @ModelAttribute("form") Forms.LocationForm f,BindingResult r,Model m,RedirectAttributes flash) {
        if (r.hasErrors()) return catalog(m,"locations","Ubicaciones",locations.findAllByOrderByNameAsc(),id);
        inventory.saveLocation(id,f); flash.addFlashAttribute("success","Ubicación guardada."); return "redirect:/ubicaciones";
    }
    @GetMapping("/proveedores") String suppliers(Model m) { return supplierPage(null,m); }
    @GetMapping("/proveedores/{id}/editar") String supplierEdit(@PathVariable Long id,Model m) { return supplierPage(id,m); }
    private String supplierPage(Long id,Model m) {
        Forms.SupplierForm f=new Forms.SupplierForm();
        if (id!=null) { Supplier s=suppliers.findById(id).orElseThrow(EmporioController::notFound); f.name=s.name; f.contact=s.contact; f.phone=s.phone; f.notes=s.notes; }
        m.addAttribute("form",f); return catalog(m,"suppliers","Proveedores",suppliers.findAllByOrderByNameAsc(),id);
    }
    @PostMapping("/proveedores") String saveSupplier(@RequestParam(required=false) Long id,@Valid @ModelAttribute("form") Forms.SupplierForm f,BindingResult r,Model m,RedirectAttributes flash) {
        if (r.hasErrors()) return catalog(m,"suppliers","Proveedores",suppliers.findAllByOrderByNameAsc(),id);
        inventory.saveSupplier(id,f); flash.addFlashAttribute("success","Proveedor guardado."); return "redirect:/proveedores";
    }
    @GetMapping("/usuarios") String users(Model m) {
        m.addAttribute("form",new Forms.UserForm()); return catalog(m,"users","Equipo",users.findAllByOrderByDisplayNameAsc(),null);
    }
    @PostMapping("/usuarios") String createUser(@Valid @ModelAttribute("form") Forms.UserForm f,BindingResult r,Model m,RedirectAttributes flash) {
        if (r.hasErrors()) return catalog(m,"users","Equipo",users.findAllByOrderByDisplayNameAsc(),null);
        inventory.createUser(f); flash.addFlashAttribute("success","Usuario creado."); return "redirect:/usuarios";
    }
    private String catalog(Model m,String type,String title,Object rows,Long id) {
        m.addAttribute("type",type); m.addAttribute("rows",rows); m.addAttribute("editId",id); return page(m,title,"catalog");
    }
    @GetMapping("/reportes/inventario.csv") ResponseEntity<byte[]> inventoryCsv() {
        StringBuilder csv=new StringBuilder("\uFEFFCódigo;Producto;Categoría;Unidad;Stock físico;Disponible;Retenido;Vencido;Mínimo;Ubicaciones;Precio CLP\r\n");
        for (InventoryQueries.StockRow r:queries.stock("","","")) {
            Product p=r.product(); csv.append(String.join(";",csv(p.sku),csv(p.name),csv(p.category),csv(p.unit),r.physical().toPlainString(),r.available().toPlainString(),r.held().toPlainString(),r.expired().toPlainString(),p.minimumStock.toPlainString(),csv(r.places()),p.salePrice.toPlainString())).append("\r\n");
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=inventario-"+clock.today()+".csv")
            .contentType(new MediaType("text","csv",StandardCharsets.UTF_8)).body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }
    private static String csv(String s) {
        s=s==null ? "" : s;
        if (s.stripLeading().matches("^[=+@-].*")) s="'"+s;
        return "\""+s.replace("\"","\"\"").replace('\r',' ').replace('\n',' ')+"\"";
    }
    private static String page(Model m,String title,String view) { m.addAttribute("title",title); return view; }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND); }
}
