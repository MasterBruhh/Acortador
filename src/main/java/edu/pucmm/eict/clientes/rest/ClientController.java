package edu.pucmm.eict.clientes.rest;

import io.javalin.http.Handler;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

public class ClientController {

    private static final String BASE_URL = "https://bruhurl.azurewebsites.net";
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public Handler showLoginPage = ctx -> {
        ctx.render("client-login.html");
    };

    public Handler login = ctx -> {
        String username = ctx.formParam("username");
        String password = ctx.formParam("password");
        if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
            String errorMsg = URLEncoder.encode("Credenciales requeridas", StandardCharsets.UTF_8.toString());
            ctx.redirect("/client/login?error=" + errorMsg);
            return;
        }

        // Codificar cada valor para evitar problemas en la URL
        String formData = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8.toString())
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8.toString());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/login"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            Map<String, Object> jsonMap = objectMapper.readValue(response.body(), Map.class);
            String token = (String) jsonMap.get("token");
            ctx.sessionAttribute("token", token);
            ctx.redirect("/client/dashboard");
        } else {
            String errorMsg = URLEncoder.encode("Error en autenticación", StandardCharsets.UTF_8.toString());
            ctx.redirect("/client/login?error=" + errorMsg);
        }
    };

    public Handler showDashboard = ctx -> {
        String token = ctx.sessionAttribute("token");
        if (token == null) {
            String errorMsg = URLEncoder.encode("Inicia sesión primero", StandardCharsets.UTF_8.toString());
            ctx.redirect("/client/login?error=" + errorMsg);
            return;
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/urls"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            // Se pasa el JSON de URLs y la URL de producción (por ejemplo, "https://bruhurl.azurewebsites.net")
            ctx.render("client-dashboard.html", Map.of("urlsJson", response.body(), "baseUrl", "https://bruhurl.azurewebsites.net"));
        } else {
            ctx.result("Error al obtener URLs: " + response.statusCode());
        }
    };

    public Handler createUrl = ctx -> {
        String token = ctx.sessionAttribute("token");
        if (token == null) {
            String errorMsg = URLEncoder.encode("Inicia sesión primero", StandardCharsets.UTF_8.toString());
            ctx.redirect("/client/login?error=" + errorMsg);
            return;
        }
        String originalUrl = ctx.formParam("originalUrl");
        if (originalUrl == null || originalUrl.isEmpty()) {
            String errorMsg = URLEncoder.encode("Debe proporcionar una URL", StandardCharsets.UTF_8.toString());
            ctx.redirect("/client/dashboard?error=" + errorMsg);
            return;
        }
        String jsonPayload = objectMapper.writeValueAsString(Map.of("originalUrl", originalUrl));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/urls"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200 || response.statusCode() == 201) {
            ctx.redirect("/client/dashboard");
        } else {
            ctx.result("Error al crear URL: " + response.statusCode());
        }
    };
}
