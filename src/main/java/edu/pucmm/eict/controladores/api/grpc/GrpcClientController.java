package edu.pucmm.eict.controladores.api.grpc;

import edu.pucmm.eict.clientes.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.javalin.http.Handler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.javalin.http.staticfiles.Location;
import org.jetbrains.annotations.NotNull;

public class GrpcClientController {

    private final ManagedChannel channel;
    private UrlShortenerServiceGrpc.UrlShortenerServiceBlockingStub stub;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    public GrpcClientController(String host, int port) {
        // En lugar de registrar mixins para ignorar unknownFields,
        // convertiremos manualmente cada objeto a un Map con los atributos deseados.
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        // Aumenta el tamaño máximo del mensaje a, por ejemplo, 10 MB.
        this.stub = UrlShortenerServiceGrpc.newBlockingStub(channel)
                .withMaxInboundMessageSize(10 * 1024 * 1024);
    }

    // Muestra la página de login (usa la misma plantilla que el cliente REST)
    public Handler showLoginPage = ctx -> {
        ctx.render("client-login.html", Map.of("backend", "grpc"));
    };

    // Login simulado: simplemente se almacena el username en la sesión
    public Handler login = ctx -> {
        String username = ctx.formParam("username");
        String password = ctx.formParam("password");
        if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
            String errorMsg = URLEncoder.encode("Credenciales requeridas", StandardCharsets.UTF_8.toString());
            ctx.redirect("/grpc-client/login?error=" + errorMsg);
            return;
        }
        ctx.sessionAttribute("username", username);
        ctx.redirect("/grpc-client/dashboard");
    };

    // Endpoint para listar las URLs del usuario haciendo una llamada gRPC a ListUrls
    public Handler showDashboard = ctx -> {
        String username = ctx.sessionAttribute("username");
        if (username == null) {
            String errorMsg = URLEncoder.encode("Inicia sesión primero", StandardCharsets.UTF_8.toString());
            ctx.redirect("/grpc-client/login?error=" + errorMsg);
            return;
        }
        ListUrlsRequest request = ListUrlsRequest.newBuilder().setUsername(username).build();
        ListUrlsResponse response = stub.listUrls(request);
        // Convertir la lista de UrlEntry en una lista de maps (sin los campos internos)
        List<Map<String, Object>> urls = response.getUrlsList().stream()
                .map(entry -> {
                    // Convertir cada AccessDetail a un Map (para evitar serializar unknownFields)
                    List<Map<String, String>> accessDetails = entry.getStatistics().getAccessDetailsList()
                            .stream()
                            .map(ad -> Map.of(
                                    "timestamp", ad.getTimestamp(),
                                    "browser", ad.getBrowser(),
                                    "ip", ad.getIp(),
                                    "clientDomain", ad.getClientDomain(),
                                    "platform", ad.getPlatform()
                            ))
                            .collect(Collectors.toList()).reversed();

                    return Map.of(
                            "originalUrl", entry.getOriginalUrl(),
                            "shortUrl", entry.getShortUrl(),
                            "createdAt", entry.getCreatedAt(),
                            "statistics", Map.of(
                                    "accessCount", entry.getStatistics().getAccessCount(),
                                    "accessTimes", entry.getStatistics().getAccessTimesList(),
                                    "accessDetails", accessDetails
                            ),
                            "previewImage", entry.getPreviewImageBase64()
                    );
                })
                .collect(Collectors.toList());
        String urlsJson = objectMapper.writeValueAsString(urls);
        ctx.render("client-dashboard.html", Map.of(
                "urlsJson", urlsJson,
                "baseUrl", "https://bruhurl.azurewebsites.net",
                "backend", "grpc"
        ));
    };

    // Endpoint para crear URL: se invoca el método gRPC CreateUrl
    public Handler createUrl = ctx -> {
        String username = ctx.sessionAttribute("username");
        if (username == null) {
            String errorMsg = URLEncoder.encode("Inicia sesión primero", StandardCharsets.UTF_8.toString());
            ctx.redirect("/grpc-client/login?error=" + errorMsg);
            return;
        }
        String originalUrl = ctx.formParam("originalUrl");
        if (originalUrl == null || originalUrl.isEmpty()) {
            String errorMsg = URLEncoder.encode("Debe proporcionar una URL", StandardCharsets.UTF_8.toString());
            ctx.redirect("/grpc-client/dashboard?error=" + errorMsg);
            return;
        }
        CreateUrlRequest request = CreateUrlRequest.newBuilder()
                .setOriginalUrl(originalUrl)
                .setUsername(username)
                .build();
        CreateUrlResponse response = stub.createUrl(request);
        ctx.redirect("/grpc-client/dashboard");
    };

    public void shutdown() {
        if (channel != null) {
            channel.shutdown();
        }
    }
}
