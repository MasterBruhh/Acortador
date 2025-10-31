package edu.pucmm.eict.clientes.rest;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.rendering.template.JavalinThymeleaf;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

public class ClientMain {
    public static void main(String[] args) {
        // Configuración del motor de plantillas Thymeleaf para el cliente
        TemplateEngine templateEngine = new TemplateEngine();
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        // Establecemos el prefijo a la carpeta de plantillas exclusivas del cliente
        templateResolver.setPrefix("/public/client_templates/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setCharacterEncoding("UTF-8");
        templateEngine.setTemplateResolver(templateResolver);

        // Configuración y creación de la aplicación Javalin para el cliente
        Javalin app = Javalin.create(config -> {
            // Se configuran archivos estáticos desde /public (separados de los templates)
            config.staticFiles.add("/public", Location.CLASSPATH);
            config.fileRenderer(new JavalinThymeleaf(templateEngine));
        }).start(7001); // El cliente se ejecutará en el puerto 7001

        // Instanciar el controlador del cliente
        ClientController clientController = new ClientController();

        // Registro de rutas para el cliente web
        app.get("/", ctx -> ctx.redirect("/client/login"));
        app.get("/client/login", clientController.showLoginPage);
        app.post("/client/login", clientController.login);
        app.get("/client/dashboard", clientController.showDashboard);
        app.post("/client/createUrl", clientController.createUrl);

        System.out.println("Cliente corriendo en http://localhost:7001");
    }
}
