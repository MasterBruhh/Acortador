package edu.pucmm.eict.controladores.api.rest;

import edu.pucmm.eict.modelos.Usuario;
import edu.pucmm.eict.services.UserService;
import edu.pucmm.eict.util.JwtUtil;
import io.javalin.http.Handler;

import java.util.Map;

public class ApiAuthController {

    private UserService userService;

    public ApiAuthController(UserService userService) {
        this.userService = userService;
    }

    // Endpoint para autenticación vía API y emisión de token JWT
    public Handler loginUserApi = ctx -> {
        String username = ctx.formParam("username");
        String password = ctx.formParam("password");
        System.out.println("[LOGIN API] username=" + username + ", password=" + password);


        if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
            ctx.status(400).result("Faltan credenciales");
            return;
        }

        if (userService.authenticate(username, password)) {
            Usuario user = userService.getUserByUsername(username);
            // Genera el token JWT usando el username y el rol del usuario
            String token = JwtUtil.generateToken(username, user.getRole());
            ctx.json(Map.of("token", token));
        } else {
            ctx.status(401).result("Credenciales inválidas");
        }
    };
}
