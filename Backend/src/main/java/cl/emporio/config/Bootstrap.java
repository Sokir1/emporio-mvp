package cl.emporio.config;

import cl.emporio.controller.Forms;
import cl.emporio.repository.UserRepository;
import cl.emporio.service.InventoryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class Bootstrap implements CommandLineRunner {
    private final UserRepository users;
    private final InventoryService inventory;
    @Value("${emporio.admin.username}") String username;
    @Value("${emporio.admin.password}") String password;
    public Bootstrap(UserRepository users,InventoryService inventory) { this.users=users; this.inventory=inventory; }
    @Override public void run(String... args) {
        if (users.count()==0) {
            if (password.isBlank()) throw new IllegalStateException("Define ADMIN_PASSWORD (mínimo 10 caracteres) para crear el primer administrador.");
            Forms.UserForm u=new Forms.UserForm(); u.username=username; u.displayName="Administración"; u.password=password; u.role="ADMIN";
            inventory.createUser(u);
        }
    }
}
