package edu.pucmm.eict;

import edu.pucmm.eict.controladores.api.rest.ApiAuthController;
import edu.pucmm.eict.controladores.AuthController;
import edu.pucmm.eict.controladores.UrlController;
import edu.pucmm.eict.controladores.UserController;
import edu.pucmm.eict.controladores.api.rest.ApiUrlController;
import edu.pucmm.eict.modelos.Url;
import edu.pucmm.eict.modelos.Usuario;
import edu.pucmm.eict.services.UrlService;
import edu.pucmm.eict.services.UserService;
import edu.pucmm.eict.util.JwtUtil;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.rendering.template.JavalinThymeleaf;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import edu.pucmm.eict.util.Database; // added
import edu.pucmm.eict.util.CsrfUtil;

public class Main {
    public static void main(String[] args) {
        // Inicializa la base de datos H2 (archivo por defecto o memoria si APP_DB_MODE=mem)
        Database.init();

        // Configuración de Thymeleaf
        TemplateEngine templateEngine = new TemplateEngine();
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("/public/templates/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setCharacterEncoding("UTF-8");
        templateEngine.setTemplateResolver(templateResolver);

        // Servicios
        UserService userService = new UserService();
        UrlService urlService = new UrlService();
        // Crear admin por defecto si no existe
        userService.createDefaultAdmin();
        // Instancia de ApiAuthController con el servicio de usuarios inyectado:
        ApiAuthController apiAuthController = new ApiAuthController(userService);


        // Controladores
        AuthController authController = new AuthController(userService);
        UserController userController = new UserController(userService);
        UrlController urlController = new UrlController(urlService);

        // Obtener el puerto de la variable de entorno PORT, o usar 7000 como default
        String portStr = System.getenv("PORT");
        int port = (portStr != null && !portStr.isEmpty()) ? Integer.parseInt(portStr) : 7000;

        // Inicialización de Javalin con el puerto configurado dinámicamente
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/public", Location.CLASSPATH);
            config.fileRenderer(new JavalinThymeleaf(templateEngine));
        }).start(port);

        // Ejemplo de middleware para validar JWT
        app.before("/api/*", ctx -> {
            if (ctx.path().equals("/api/login")) {
                return; // Permite el login sin token
            }
            String authHeader = ctx.header("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                ctx.status(401).result("No autorizado");
                return;
            }
            try {
                // Extrae el token sin espacios adicionales
                String token = authHeader.substring(7).trim();
                var claims = JwtUtil.validateToken(token);
                ctx.attribute("currentUser", claims.getSubject());
                ctx.attribute("currentRole", claims.get("role"));
                System.out.println("[TOKEN VALIDADO] currentUser=" + claims.getSubject());
            } catch (Exception e) {
                e.printStackTrace();  // Imprime el error en la consola
                ctx.status(401).result("Token inválido");
            }
        });


// Registro de rutas de la API
        ApiUrlController apiUrlController = new ApiUrlController(urlService, userService);
        app.get("/api/urls", apiUrlController.listUrlsApi);
        app.post("/api/urls", apiUrlController.createUrlApi);


        // Middleware para inyectar el usuario desde la sesión en rutas no-API
        app.before(ctx -> {
            if (!ctx.path().startsWith("/api/")) {
                Usuario currentUser = ctx.sessionAttribute("user");
                ctx.attribute("currentUser", currentUser);
                
                // CSRF Protection: Inyectar token CSRF en todas las vistas
                String csrfToken = ctx.sessionAttribute("csrfToken");
                ctx.attribute("csrfToken", csrfToken);
            }
        });

        // Rutas de autenticación (sin protección CSRF - no requieren token)
        app.get("/register", authController.showRegisterPage);
        app.get("/login", authController.showLoginPage);
        app.get("/index", authController.showIndexPage);
        app.post("/register", authController.registerUser);
        app.post("/login", authController.loginUser);
        app.get("/logout", authController.logoutUser);
        // Ruta para el login vía API
        app.post("/api/login", apiAuthController.loginUserApi);

        // Dashboard - Middleware de autenticación
        app.get("/dashboard", ctx -> {
            Usuario usuario = ctx.sessionAttribute("user");
            if (usuario != null) {
                ctx.render("dashboard.html", Map.of("usuario", usuario));
            } else {
                ctx.redirect("/login?error=Debes iniciar sesión primero");
            }
        });

        app.before("/dashboard/*", ctx -> {
            // Check if there is a logged-in user in the session.
            if (ctx.sessionAttribute("user") == null) {
                // Redirect to the login page if not authenticated.
                ctx.redirect("/login");
            }
        });

        // CSRF Protection: Middleware para validar tokens en peticiones POST a /dashboard/*
        // IMPORTANTE: Este middleware debe estar DESPUÉS de la autenticación pero ANTES de las rutas POST
        app.before("/dashboard/*", ctx -> {
            if ("POST".equals(ctx.method())) {
                // Obtener el token de la sesión
                String sessionToken = ctx.sessionAttribute("csrfToken");
                
                // Obtener el token de la petición (campo de formulario o header para AJAX)
                String requestToken = ctx.formParam("csrfToken");
                if (requestToken == null) {
                    requestToken = ctx.header("X-CSRF-Token");
                }
                
                // Validar el token usando comparación de tiempo constante
                if (!CsrfUtil.validateToken(sessionToken, requestToken)) {
                    System.out.println("[CSRF] Token inválido o ausente. Petición bloqueada.");
                    System.out.println("[CSRF]    - Path: " + ctx.path());
                    System.out.println("[CSRF]    - Origin: " + ctx.header("Origin"));
                    System.out.println("[CSRF]    - Referer: " + ctx.header("Referer"));
                    ctx.status(403).result("Acción no permitida: Token CSRF inválido o ausente");
                    return; // Bloquear la petición
                }
                
                // Validación adicional: Origin header (defensa en profundidad)
                String origin = ctx.header("Origin");
                if (origin != null && !isValidOrigin(origin)) {
                    System.out.println("[CSRF] Origen no permitido: " + origin);
                    ctx.status(403).result("Origen no permitido");
                    return;
                }
                
                System.out.println("[CSRF] Token válido para: " + ctx.path());
            }
        });

        // Rutas de usuarios
        app.get("/dashboard/users", userController.listUsers);
        app.post("/dashboard/users/updateRole", userController.updateUserRole);
        app.post("/dashboard/users/delete", userController.deleteUser);

        // Rutas de URLs: se filtran según el usuario en sesión
        app.get("/dashboard/urls", ctx -> {
            Usuario currentUser = ctx.sessionAttribute("user");
            Collection<Url> urls = urlService.getAllUrls();
            if (currentUser != null && !"admin".equals(currentUser.getRole())) {
                urls = urls.stream()
                        .filter(url -> url.getUser() != null && url.getUser().getUsername().equals(currentUser.getUsername()))
                        .collect(Collectors.toList());
            }
            ctx.render("urls.html", Map.of("urls", urls, "usuario", currentUser));
        });
        // Registra la ruta POST para acortar URL
        // Fragmento de Main.java
        app.post("/dashboard/urls/acortar", ctx -> {
            // CSRF Protection: Validar token
            String sessionToken = ctx.sessionAttribute("csrfToken");
            String requestToken = ctx.formParam("csrfToken");
            if (requestToken == null) {
                requestToken = ctx.header("X-CSRF-Token");
            }
            
            if (!CsrfUtil.validateToken(sessionToken, requestToken)) {
                System.out.println("[CSRF] Token inválido para /dashboard/urls/acortar");
                System.out.println("[CSRF]    - Origin: " + ctx.header("Origin"));
                ctx.status(403).result("Acción no permitida: Token CSRF inválido");
                return;
            }
            
            // Validación adicional: Origin header
            String origin = ctx.header("Origin");
            if (origin != null && !isValidOrigin(origin)) {
                System.out.println("[CSRF] Origen no permitido: " + origin);
                ctx.status(403).result("Origen no permitido");
                return;
            }
            
            System.out.println("[CSRF] Token válido para /dashboard/urls/acortar");
            
            // Llamamos la lógica que crea la URL (esto no cambia)
            urlController.createShortUrl.handle(ctx);

            // Detectamos si la petición vino como "AJAX" (fetch con X-Requested-With)
            boolean isAjax = "XMLHttpRequest".equals(ctx.header("X-Requested-With"));
            if (isAjax) {
                // Si es AJAX, devolvemos un JSON con status OK
                ctx.json(Map.of("status", "ok")); // 200 por defecto
            } else {
                // Si es un submit normal de formulario, redirigimos como antes
                ctx.redirect("/dashboard/urls");
            }
        });
        app.post("/dashboard/urls/delete", urlController.deleteUrl);

        app.get("/dashboard/urls-acortar", ctx -> {
            Usuario currentUser = ctx.sessionAttribute("user");
            if (currentUser != null) {
                ctx.render("urls-acortar.html", Map.of("usuario", currentUser));
            } else {
                ctx.redirect("/login?error=Debes iniciar sesión primero");
            }
        });

        // Ruta para visualizar una URL en detalle (urls-view.html)
        app.get("/dashboard/urls-view", ctx -> {
            // Se lee el parámetro shortUrl de la query
            String shortUrl = ctx.queryParam("shortUrl");
            if (shortUrl == null || shortUrl.isEmpty()) {
                ctx.redirect("/dashboard/urls");
                return;
            }
            // Se obtiene el usuario desde la sesión y se valida
            Usuario currentUser = ctx.sessionAttribute("user");
            if (currentUser == null) {
                ctx.redirect("/login?error=Debes iniciar sesión primero");
                return;
            }
            // Se obtiene el URL en cuestión a partir del shortUrl
            Url url = urlService.getUrl(shortUrl);
            if (url == null) {
                ctx.status(404).result("URL no encontrada.");
                return;
            }
            // Se renderiza la vista urls-view.html pasando el usuario y el objeto url
            ctx.render("urls-view.html", Map.of("usuario", currentUser, "url", url));
        });

        app.post("/dashboard/urls/update", urlController.updateUrlRandom);

        app.get("/dashboard/users/add", ctx -> {
            Usuario nuevoUsuario = new Usuario();
            ctx.render("usuario-form.html", Map.of("usuario", nuevoUsuario));
        });

        app.post("/dashboard/users/add", ctx -> {
            // Se obtienen los parámetros del formulario del modal
            String username = ctx.formParam("username");
            String password = ctx.formParam("password");

            // Se ejecuta la lógica general de registro (puedes usar el metodo register o registerAdmin, según convenga)
            boolean success = userService.register(username, password);

            // Obtener la lista actualizada de usuarios
            Collection<Usuario> users = userService.getAllUsers();

            // Opcional: Definir un mensaje de estado para informar al usuario
            String message = success ? "Usuario creado exitosamente." : "El usuario ya existe, por favor intente con otro nombre.";

            // Renderiza la vista de usuarios (usuarios.html) actualizada con la lista y el mensaje
            ctx.render("usuarios.html", Map.of("users", users, "message", message));
        });

        app.get("/dashboard/users/{username}", ctx -> {
            String username = ctx.pathParam("username");
            Usuario usuario = userService.getUserByUsername(username);
            if (usuario != null) {
                ctx.render("usuario-form.html", Map.of("usuario", usuario));
            } else {
                ctx.status(404).result("Usuario no encontrado");
            }
        });
        app.post("/dashboard/users/update", userController.updateUser);



        // Nueva ruta para la vista de estadísticas (la ruta no depende del dashboard, sino que se invoca directamente desde index)
        app.get("/estadisticas", ctx -> {
            String shortUrl = ctx.queryParam("shortUrl");
            if (shortUrl == null || shortUrl.isEmpty()) {
                ctx.redirect("/index");
                return;
            }
            // Obtener la clave de Google Maps del sistema (variable de ambiente)
            String googleMapsApiKey = System.getenv("GOOGLE_MAPS_API_KEY");
            if(googleMapsApiKey == null || googleMapsApiKey.isEmpty()){
                googleMapsApiKey = "TU_CLAVE_PREDETERMINADA_O_MENSAJE_DE_ERROR";
            }
            // Pasar shortUrl y googleMapsApiKey al modelo para la plantilla
            ctx.render("estadisticas.html", Map.of(
                    "shortUrl", shortUrl,
                    "googleMapsApiKey", googleMapsApiKey
            ));
        });

        // Rutas para acortar enlaces, redirigir, estadísticas y vista previa
        app.post("/acortar", urlController.createShortUrl);
        app.get("/go/{shortUrl}", urlController.redirectToOriginalUrl);
        app.get("/urls", urlController.listUrls);
        app.get("/stats/{shortUrl}", urlController.getAccessStats);
        app.get("/preview", urlController.previewUrl);
        app.get("/", ctx -> ctx.redirect("/index"));

        System.out.println("Aplicación corriendo en http://localhost:" + port);
    }

    /**
     * Método helper para validar el origen de peticiones (protección CSRF adicional).
     * Verifica que las peticiones provengan del dominio legítimo de la aplicación.
     * 
        * @param origin Header Origin de la petición HTTP
        * @return true si el origen es válido, false en caso contrario
     */
    private static boolean isValidOrigin(String origin) {
        if (origin == null) {
            return true; // Permitir si no hay header (navegación directa)
        }
        
        // Obtener el dominio permitido desde variable de entorno o default
        String baseUrl = System.getenv("BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:7000";
        }
        
        // Permitir tanto HTTP como HTTPS del mismo dominio
        return origin.startsWith(baseUrl) || 
               origin.startsWith(baseUrl.replace("http://", "https://"));
    }
}
