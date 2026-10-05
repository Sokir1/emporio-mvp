package cl.emporio.service;

import cl.emporio.model.*;
import cl.emporio.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service @Transactional(readOnly=true)
public class InventoryQueries {
    private final ProductRepository products;
    private final LotRepository lots;
    private final SaleRepository sales;
    private final BusinessClock clock;
    public InventoryQueries(ProductRepository p,LotRepository l,SaleRepository s,BusinessClock c) { products=p; lots=l; sales=s; clock=c; }
    public record StockRow(Product product,BigDecimal physical,BigDecimal available,BigDecimal held,BigDecimal expired,String places,LocalDate nextExpiry) {
        public String status() { return available.signum()==0 ? "AGOTADO" : available.compareTo(product.minimumStock)<=0 ? "BAJO" : "NORMAL"; }
    }
    public List<StockRow> stock(String search,String category,String status) {
        String q=search==null ? "" : search.trim().toLowerCase(Locale.ROOT);
        Map<Long,List<Lot>> grouped=lots.findAll().stream().collect(Collectors.groupingBy(l->l.product.id));
        return products.findAllByOrderByNameAsc().stream().map(p -> {
            List<Lot> ls=grouped.getOrDefault(p.id,List.of());
            BigDecimal physical=sum(ls), available=sum(ls.stream().filter(l -> InventoryService.available(l,clock.today())).toList());
            BigDecimal expired=sum(ls.stream().filter(l -> l.expiresOn!=null && l.expiresOn.isBefore(clock.today())).toList());
            BigDecimal held=sum(ls.stream().filter(l -> "RETENIDO".equals(l.quality) && (l.expiresOn==null || !l.expiresOn.isBefore(clock.today()))).toList());
            String places=ls.stream().filter(l->l.quantity.signum()>0).map(l->l.location.name).distinct().sorted().collect(Collectors.joining(" · "));
            LocalDate next=ls.stream().filter(l->InventoryService.available(l,clock.today()) && l.expiresOn!=null).map(l->l.expiresOn).min(LocalDate::compareTo).orElse(null);
            return new StockRow(p,physical,available,held,expired,places,next);
        }).filter(r -> ("ARCHIVADO".equals(status) ? !r.product.active : r.product.active))
          .filter(r -> q.isEmpty() || (r.product.name+" "+r.product.sku+" "+r.places).toLowerCase(Locale.ROOT).contains(q))
          .filter(r -> category==null || category.isBlank() || r.product.category.equals(category))
          .filter(r -> status==null || status.isBlank() || "ARCHIVADO".equals(status) || r.status().equals(status)).toList();
    }
    public List<Lot> positiveLots() { return lots.findAllByOrderByIdDesc().stream().filter(l->l.quantity.signum()>0).toList(); }
    public List<Lot> expiryLots() {
        return positiveLots().stream().filter(l -> l.expiresOn!=null && !l.expiresOn.isAfter(clock.today().plusDays(14)))
            .sorted(Comparator.comparing(l->l.expiresOn)).toList();
    }
    public record Dashboard(long productCount,long lowCount,long upcomingCount,long expiredCount,BigDecimal salesToday,BigDecimal stockValue,List<StockRow> low,List<Lot> expiring,List<DayTotal> week) {}
    public record DayTotal(LocalDate date,BigDecimal total,int width) {}
    public Dashboard dashboard() {
        List<StockRow> stock=stock("","","");
        List<StockRow> low=stock.stream().filter(r -> !"NORMAL".equals(r.status())).toList();
        List<Lot> expiring=expiryLots();
        BigDecimal value=positiveLots().stream().map(l->l.quantity.multiply(l.unitCost)).reduce(BigDecimal.ZERO,BigDecimal::add);
        Map<LocalDate,BigDecimal> totals=new HashMap<>();
        sales.findAll().stream().filter(s -> !s.cancelled && !s.createdAt.toLocalDate().isBefore(clock.today().minusDays(6)))
            .forEach(s -> totals.merge(s.createdAt.toLocalDate(),s.total,BigDecimal::add));
        BigDecimal max=totals.values().stream().max(BigDecimal::compareTo).orElse(BigDecimal.ONE).max(BigDecimal.ONE);
        List<DayTotal> week=new ArrayList<>();
        for (int i=6;i>=0;i--) {
            LocalDate day=clock.today().minusDays(i); BigDecimal total=totals.getOrDefault(day,BigDecimal.ZERO);
            week.add(new DayTotal(day,total,total.multiply(new BigDecimal("100")).divide(max,0,java.math.RoundingMode.HALF_UP).intValue()));
        }
        return new Dashboard(stock.size(),low.size(),expiring.stream().filter(l->!l.expiresOn.isBefore(clock.today())).count(),
            expiring.stream().filter(l->l.expiresOn.isBefore(clock.today())).count(),totals.getOrDefault(clock.today(),BigDecimal.ZERO),value,low,expiring,week);
    }
    private static BigDecimal sum(List<Lot> lots) { return lots.stream().map(l->l.quantity).reduce(BigDecimal.ZERO,BigDecimal::add); }
}
