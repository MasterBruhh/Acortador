package edu.pucmm.eict.clientes.grpc;

import edu.pucmm.eict.controladores.api.grpc.GrpcClientController;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.rendering.template.JavalinThymeleaf;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

public class GrpcClientMain {
    public static void main(String[] args) {
        // Configuración del motor de plantillas Thymeleaf para el cliente
        TemplateEngine templateEngine = new TemplateEngine();
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("/public/client_templates/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setCharacterEncoding("UTF-8");
        templateEngine.setTemplateResolver(templateResolver);

        // Crear la aplicación Javalin y configurar los archivos estáticos
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/public", Location.CLASSPATH);
            config.fileRenderer(new JavalinThymeleaf(templateEngine));
        }).start(7002); // El cliente gRPC se ejecutará en el puerto 7002

        // Instanciar el controlador del cliente gRPC, conectándose al servidor gRPC en el puerto 50051
        GrpcClientController clientController = new GrpcClientController("localhost", 50051);

        // Registro de rutas para el cliente web
        app.get("/", ctx -> ctx.redirect("/grpc-client/login"));
        app.get("/grpc-client/login", clientController.showLoginPage);
        // Se unifica la ruta POST de login para que sea '/grpc-client/login'
        app.post("/grpc-client/login", clientController.login);
        app.get("/grpc-client/dashboard", clientController.showDashboard);
        app.post("/grpc-client/createUrl", clientController.createUrl);

        System.out.println("Cliente gRPC corriendo en http://localhost:7002");
    }
}
