package edu.pucmm.eict.controladores;

import edu.pucmm.eict.modelos.Usuario;
import edu.pucmm.eict.services.UserService;
import io.javalin.http.Handler;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

public class UserController {

    private UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    public Handler listUsers = ctx -> {
        Collection<Usuario> users = userService.getAllUsers();
        ctx.render("usuarios.html", Map.of("users", users));
    };

    public Handler updateUserRole = ctx -> {
        String username = ctx.formParam("username");
        String role = ctx.formParam("role");
        if (userService.updateRole(username, role)) {
            ctx.redirect("/dashboard/users?success=Role updated successfully");
        } else {
            ctx.redirect("/dashboard/users?error=Role update failed");
        }
    };

    public Handler updateUser = ctx -> {
        // Se obtiene el username del formulario (campo oculto en el formulario de vista)
        String username = ctx.formParam("username");
        // Se obtienen los nuevos valores a actualizar
        String newPassword = ctx.formParam("password");
        String newRole = ctx.formParam("role");

        if (username == null || newPassword == null || newRole == null ||
                username.isEmpty() || newPassword.isEmpty() || newRole.isEmpty()) {
            ctx.redirect("/dashboard/users?error=Todos los campos son requeridos");
            return;
        }

        // Actualizar usuario en la base de datos
        boolean updated = userService.updateUser(username, newPassword, newRole);
        if (updated) {
            ctx.redirect("/dashboard/users?success=Usuario actualizado exitosamente");
        } else {
            ctx.redirect("/dashboard/users?error=Error al actualizar el usuario");
        }
    };


    public Handler deleteUser = ctx -> {
        String username = ctx.formParam("username");
        if (userService.deleteUser(username)) {
            ctx.redirect("/dashboard/users?success=User deleted successfully");
        } else {
            ctx.redirect("/dashboard/users?error=Cannot delete admin user");
        }
    };
}
