package cl.emporio.controller;

import cl.emporio.repository.UserRepository;
import cl.emporio.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import java.time.format.DateTimeFormatter;
import java.time.*;
import java.math.*;
import java.util.*;

@ControllerAdvice
public class WebAdvice {
    private final UserRepository users;
    private final BusinessClock clock;
    public WebAdvice(UserRepository users,BusinessClock clock) { this.users=users; this.clock=clock; }
    @InitBinder public void forms(WebDataBinder binder) { binder.initDirectFieldAccess(); }
    @ModelAttribute("userDisplay") public String userDisplay(Authentication auth) {
        if (auth==null) return "";
        return users.findByUsernameIgnoreCase(auth.getName()).map(u->u.displayName).orElse(auth.getName());
    }
    @ModelAttribute("today") public LocalDate today() { return clock.today(); }
    @ModelAttribute("format") public DisplayFormat format() { return new DisplayFormat(); }
    @ModelAttribute("categories") public List<String> categories() { return InventoryService.CATEGORIES; }
    @ModelAttribute("currentPath") public String path(HttpServletRequest request) { return request.getRequestURI(); }

    @ExceptionHandler(BusinessException.class)
    public ModelAndView business(BusinessException ex,HttpServletRequest request) {
        return errorPage(ex.getMessage(),HttpStatus.UNPROCESSABLE_ENTITY,request);
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ModelAndView conflict(DataIntegrityViolationException ex,HttpServletRequest request) {
        return errorPage("No se pudo guardar: verifica los valores y que el código o nombre no esté repetido.",HttpStatus.CONFLICT,request);
    }
    private ModelAndView errorPage(String message,HttpStatus status,HttpServletRequest request) {
        ModelAndView view=new ModelAndView("error"); view.setStatus(status);
        view.addObject("message",message); view.addObject("title","Revisar operación");
        view.addObject("currentPath",request.getRequestURI()); view.addObject("today",clock.today());
        view.addObject("format",new DisplayFormat());
        String name=request.getUserPrincipal()==null ? "" : request.getUserPrincipal().getName();
        view.addObject("userDisplay",users.findByUsernameIgnoreCase(name).map(u->u.displayName).orElse(name));
        return view;
    }
    public static class DisplayFormat {
        public String qty(BigDecimal value) { return value==null ? "0" : value.stripTrailingZeros().toPlainString().replace('.',','); }
        public String money(BigDecimal value) {
            return "$"+java.text.NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CL")).format(value==null ? BigDecimal.ZERO : value.setScale(0,RoundingMode.HALF_UP));
        }
        public String date(LocalDate date) { return date==null ? "Sin vencimiento" : date.format(DateTimeFormatter.ofPattern("dd MMM yyyy",Locale.forLanguageTag("es-CL"))); }
        public String time(LocalDateTime time) { return time.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")); }
        public String day(LocalDate day) { return day.format(DateTimeFormatter.ofPattern("EEE",Locale.forLanguageTag("es-CL"))); }
    }
}
