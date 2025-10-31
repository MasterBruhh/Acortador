package edu.pucmm.eict.controladores;

import edu.pucmm.eict.modelos.Usuario;
import edu.pucmm.eict.services.UserService;
import io.javalin.http.Handler;

import java.util.Map;

public class AuthController {

    private UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    public Handler showRegisterPage = ctx -> {
        ctx.render("home.html");
    };

    public Handler showLoginPage = ctx -> {
        ctx.render("login.html");
    };

    // Se modifica para inyectar la baseUrl en la plantilla
    public Handler showIndexPage = ctx -> {
        String baseUrl = "bruhurl.azurewebsites.net";
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:7000";
        }
        ctx.render("index.html", Map.of("baseUrl", baseUrl));
    };

    public Handler registerUser = ctx -> {
        String username = ctx.formParam("username");
        String password = ctx.formParam("password");
        if (userService.register(username, password)) {
            ctx.redirect("/login?success=Cuenta creada correctamente");
        } else {
            ctx.redirect("/register?error=El usuario ya existe");
        }
    };

    public Handler loginUser = ctx -> {
        String username = ctx.formParam("username");
        String password = ctx.formParam("password");
        if (userService.authenticate(username, password)) {
            Usuario loggedUser = userService.getUserByUsername(username);
            ctx.sessionAttribute("user", loggedUser);
            ctx.redirect("/index");
        } else {
            ctx.redirect("/login?error=Credenciales incorrectas");
        }
    };

    public Handler logoutUser = ctx -> {
        ctx.req().getSession().invalidate();
        ctx.redirect("/login");
    };
}
