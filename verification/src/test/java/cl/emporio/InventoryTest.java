package cl.emporio;

import cl.emporio.controller.Forms;
import cl.emporio.model.*;
import cl.emporio.repository.*;
import cl.emporio.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class InventoryTest {
    @Autowired InventoryService inventory;
    @Autowired InventoryQueries queries;
    @Autowired ProductRepository products;
    @Autowired LotRepository lots;
    @Autowired LocationRepository locations;
    @Autowired SupplierRepository suppliers;
    @Autowired MovementRepository movements;
    @Autowired SaleLineRepository lines;
    @Autowired SaleRepository sales;
    @Autowired UserRepository users;
    @Autowired BusinessClock clock;
    @Autowired PlatformTransactionManager tm;
    @Autowired MockMvc mvc;
    Product product; Location front; Location back;

    @BeforeEach void prepare() {
        new TransactionTemplate(tm).executeWithoutResult(s -> {
            movements.deleteAllInBatch(); lines.deleteAllInBatch(); sales.deleteAllInBatch();
            lots.deleteAllInBatch(); products.deleteAllInBatch(); locations.deleteAllInBatch(); suppliers.deleteAllInBatch();
        });
        Forms.ProductForm p=new Forms.ProductForm(); p.name="Almendras"; p.sku="ALM"; p.unit="kg"; p.minimumStock=n("2"); p.salePrice=n("1001");
        product=inventory.saveProduct(null,p);
        Forms.LocationForm f=new Forms.LocationForm(); f.name="Sala"; f.description="Estante A"; front=inventory.saveLocation(null,f);
        f=new Forms.LocationForm(); f.name="Resguardo"; f.description="Estante B"; back=inventory.saveLocation(null,f);
    }
    Lot receipt(String code,String qty,LocalDate expires) { return receipt(code,qty,expires,"APTO"); }
    Lot receipt(String code,String qty,LocalDate expires,String quality) {
        Forms.ReceiptForm f=new Forms.ReceiptForm(); f.productId=product.id; f.locationId=front.id; f.code=code;
        f.quantity=n(qty); f.receivedOn=clock.today(); f.expiresOn=expires; f.quality=quality; f.unitCost=n("500");
        return inventory.receive(f,"admin");
    }
    Sale sell(String qty) { return inventory.sell(front.id,List.of(new InventoryService.SaleItem(product.id,n(qty))),"EFECTIVO","admin"); }
    static BigDecimal n(String value) { return new BigDecimal(value); }

    @Test void fefoConsumesNearestExpiryAndRoundsOncePerProduct() {
        Lot later=receipt("LARGO","1",clock.today().plusDays(30));
        Lot soon=receipt("PRONTO","0.25",clock.today().plusDays(3));
        Lot undated=receipt("SINFECHA","2",null);
        Sale sale=sell("0.5");
        assertThat(lots.findById(soon.id).orElseThrow().quantity).isEqualByComparingTo("0");
        assertThat(lots.findById(later.id).orElseThrow().quantity).isEqualByComparingTo("0.75");
        assertThat(lots.findById(undated.id).orElseThrow().quantity).isEqualByComparingTo("2");
        assertThat(sale.total).isEqualByComparingTo("501");
        assertThat(lines.findBySaleIdOrderByIdAsc(sale.id).stream().map(l->l.subtotal).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(sale.total);
    }
    @Test void failedMultilineSaleRollsBackEveryStockChangeAndAudit() {
        Lot lot=receipt("OK","2",null);
        Forms.ProductForm f=new Forms.ProductForm(); f.sku="VACIO"; f.name="Sin existencias"; Product empty=inventory.saveProduct(null,f);
        long auditBefore=movements.count();
        assertThatThrownBy(() -> inventory.sell(front.id,List.of(new InventoryService.SaleItem(product.id,n("1")),new InventoryService.SaleItem(empty.id,n("1"))),"EFECTIVO","admin")).isInstanceOf(BusinessException.class);
        assertThat(lots.findById(lot.id).orElseThrow().quantity).isEqualByComparingTo("2");
        assertThat(sales.count()).isZero(); assertThat(lines.count()).isZero(); assertThat(movements.count()).isEqualTo(auditBefore);
    }
    @Test void heldAndExpiredStockCannotBeSoldAndExpiryTodayCan() {
        Lot old=receipt("VENCE","2",clock.today()); receipt("RETENIDO","3",clock.today().plusDays(10),"RETENIDO");
        new TransactionTemplate(tm).executeWithoutResult(s -> { Lot l=lots.findById(old.id).orElseThrow(); l.expiresOn=clock.today().minusDays(1); });
        assertThatThrownBy(() -> sell("1")).isInstanceOf(BusinessException.class);
        assertThat(queries.stock("","","").get(0).available()).isEqualByComparingTo("0");
        receipt("HOY","1",clock.today()); assertThat(sell("1").total).isEqualByComparingTo("1001");
    }
    @Test void transferPreservesTraceabilityAndAdjustRequiresReason() {
        Lot lot=receipt("TRAZA","5",clock.today().plusDays(10));
        inventory.transfer(lot.id,back.id,n("2"),"Reorganizar","admin");
        List<Lot> all=lots.findByProductIdOrderByIdAsc(product.id);
        assertThat(all).hasSize(2); assertThat(all.get(0).quantity).isEqualByComparingTo("3");
        assertThat(all.get(1).code).isEqualTo("TRAZA"); assertThat(all.get(1).expiresOn).isEqualTo(lot.expiresOn);
        assertThat(all.get(1).quantity).isEqualByComparingTo("2");
        assertThatThrownBy(() -> inventory.adjust(lot.id,n("0")," ","admin")).isInstanceOf(BusinessException.class);
        inventory.adjust(lot.id,n("1.5"),"Merma identificada","admin");
        assertThat(lots.findById(lot.id).orElseThrow().quantity).isEqualByComparingTo("1.5");
        assertThat(movements.findTop200ByOrderByIdDesc().get(0).quantity).isEqualByComparingTo("-1.5");
    }
    @Test void cancellationRestoresOriginalLotsExactlyOnce() {
        Lot lot=receipt("DEV","3",null); Sale sale=sell("1.25");
        inventory.cancelSale(sale.id,"Error de registro","admin");
        assertThat(lots.findById(lot.id).orElseThrow().quantity).isEqualByComparingTo("3");
        assertThatThrownBy(() -> inventory.cancelSale(sale.id,"Otra vez","admin")).isInstanceOf(BusinessException.class);
        assertThat(queries.dashboard().salesToday()).isEqualByComparingTo("0");
    }
    @Test void unitAndDecimalValidationRejectsInvalidQuantities() {
        assertThatThrownBy(() -> InventoryService.quantity(n("0.5"),"unidad",false)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> InventoryService.quantity(n("1.0001"),"kg",false)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> InventoryService.quantity(n("-1"),"kg",true)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> receipt("FUTURA","1",clock.today().minusDays(1))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> sell("0")).isInstanceOf(BusinessException.class);
    }
    @Test void simultaneousSalesCannotOversell() throws Exception {
        receipt("ULTIMO","1",null);
        CountDownLatch go=new CountDownLatch(1); ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> action=()-> { go.await(); try { sell("1"); return true; } catch (BusinessException e) { return false; } };
            Future<Boolean> a=pool.submit(action), b=pool.submit(action); go.countDown();
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(sales.count()).isEqualTo(1);
            assertThat(lots.findByProductIdOrderByIdAsc(product.id).get(0).quantity).isEqualByComparingTo("0");
        } finally { pool.shutdownNow(); }
    }
    @Test @WithMockUser(username="admin",roles="ADMIN") void allPagesRenderWithLiveStockAndForms() throws Exception {
        receipt("PAGINA","4",clock.today().plusDays(3)); Sale sale=sell("1");
        for (String path:List.of("/","/productos","/productos/nuevo","/productos/"+product.id,"/productos/"+product.id+"/editar","/bodega","/entradas/nueva","/ventas","/ventas/nueva","/ventas/"+sale.id,"/alertas","/movimientos","/ubicaciones","/ubicaciones/"+front.id+"/editar","/proveedores","/usuarios","/mi-cuenta")) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
        mvc.perform(get("/reportes/inventario.csv")).andExpect(status().isOk()).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("inventario-")));
    }
    @Test @WithMockUser(username="vendedor",roles="VENTAS") void sellerCannotChangeWarehouseAndCsrfIsRequired() throws Exception {
        mvc.perform(get("/entradas/nueva")).andExpect(status().isForbidden());
        mvc.perform(post("/bodega/ajustar").with(csrf()).param("lotId","1").param("counted","0").param("reason","prueba")).andExpect(status().isForbidden());
        mvc.perform(post("/ventas/nueva")).andExpect(status().isForbidden());
        mvc.perform(get("/ventas/nueva")).andExpect(status().isOk());
    }
    @Test void anonymousUsersMustLogIn() throws Exception { mvc.perform(get("/")).andExpect(status().is3xxRedirection()); mvc.perform(get("/login")).andExpect(status().isOk()); }
    @Test void passwordChangeChecksCurrentPasswordAndConfirmation() {
        Forms.UserForm f=new Forms.UserForm(); f.username="passwordcheck"; f.displayName="Cuenta"; f.password="Initial-Password-456";
        if (users.findByUsernameIgnoreCase(f.username).isPresent()) users.delete(users.findByUsernameIgnoreCase(f.username).orElseThrow());
        inventory.createUser(f);
        String initial=users.findByUsernameIgnoreCase(f.username).orElseThrow().passwordHash;
        assertThatThrownBy(() -> inventory.changePassword(f.username,"incorrecta","New-Password-456","New-Password-456")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> inventory.changePassword(f.username,f.password,"New-Password-456","Diferente")).isInstanceOf(BusinessException.class);
        assertThat(users.findByUsernameIgnoreCase(f.username).orElseThrow().passwordHash).isEqualTo(initial);
        inventory.changePassword(f.username,f.password,"New-Password-456","New-Password-456");
        assertThat(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches("New-Password-456",users.findByUsernameIgnoreCase(f.username).orElseThrow().passwordHash)).isTrue();
        users.delete(users.findByUsernameIgnoreCase(f.username).orElseThrow());
    }
    @Test @WithMockUser(username="admin",roles="ADMIN") void httpSaleTokenPreventsDuplicateSubmission() throws Exception {
        receipt("TOKEN","3",null);
        var session=new org.springframework.mock.web.MockHttpSession();
        mvc.perform(get("/ventas/nueva").session(session)).andExpect(status().isOk());
        String token=(String)session.getAttribute("saleToken");
        mvc.perform(post("/ventas/nueva").session(session).with(csrf()).param("saleToken",token).param("locationId",front.id.toString()).param("productIds",product.id.toString()).param("quantities","0.250").param("payment","EFECTIVO")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/ventas/nueva").session(session).with(csrf()).param("saleToken",token).param("locationId",front.id.toString()).param("productIds",product.id.toString()).param("quantities","0.250").param("payment","EFECTIVO")).andExpect(status().isUnprocessableEntity());
        assertThat(sales.count()).isEqualTo(1);
    }
    @Test @WithMockUser(username="admin",roles="ADMIN") void httpReceiptBindsDatesAndPersistsLot() throws Exception {
        mvc.perform(post("/entradas/nueva").with(csrf()).param("productId",product.id.toString()).param("locationId",front.id.toString()).param("code","HTTP").param("quantity","1.250").param("unitCost","500").param("receivedOn",clock.today().toString()).param("expiresOn",clock.today().plusDays(5).toString()).param("quality","APTO").param("reason","Entrega de prueba")).andExpect(status().is3xxRedirection());
        assertThat(lots.findByProductIdOrderByIdAsc(product.id)).hasSize(1);
        assertThat(lots.findByProductIdOrderByIdAsc(product.id).get(0).quantity).isEqualByComparingTo("1.250");
    }
    @Test @WithMockUser(username="admin",roles="ADMIN") void validHttpProductFormPersistsAndInvalidFormShowsErrors() throws Exception {
        mvc.perform(post("/productos/nuevo").with(csrf()).param("sku","PRUEBA").param("name","Queso").param("category","Quesos").param("unit","kg").param("minimumStock","1.5").param("salePrice","9500")).andExpect(status().is3xxRedirection());
        assertThat(products.findBySkuIgnoreCase("PRUEBA")).isPresent();
        mvc.perform(post("/productos/nuevo").with(csrf()).param("sku","").param("name","")).andExpect(status().isOk()).andExpect(model().attributeHasErrors("form"));
    }
}
