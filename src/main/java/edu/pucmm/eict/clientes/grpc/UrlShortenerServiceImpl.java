package edu.pucmm.eict.clientes.grpc;

import com.fasterxml.jackson.databind.SerializationFeature;  // Import necesario

import edu.pucmm.eict.clientes.grpc.AccessDetail;
import edu.pucmm.eict.clientes.grpc.CreateUrlRequest;
import edu.pucmm.eict.clientes.grpc.CreateUrlResponse;
import edu.pucmm.eict.clientes.grpc.ListUrlsRequest;
import edu.pucmm.eict.clientes.grpc.ListUrlsResponse;
import edu.pucmm.eict.clientes.grpc.UrlEntry;
import edu.pucmm.eict.clientes.grpc.UrlStatistics;
import edu.pucmm.eict.modelos.Url;
import edu.pucmm.eict.modelos.Usuario;
import edu.pucmm.eict.services.UrlService;
import edu.pucmm.eict.services.UserService;
import io.grpc.stub.StreamObserver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class UrlShortenerServiceImpl extends UrlShortenerServiceGrpc.UrlShortenerServiceImplBase {

    private final UrlService urlService;
    private final UserService userService;
    // Se obtiene la baseUrl vía variable de ambiente o se usa por defecto "http://localhost:7000"
    private final String baseUrl;

    public UrlShortenerServiceImpl(UrlService urlService, UserService userService) {
        this.urlService = urlService;
        this.userService = userService;
        String envBaseUrl = System.getenv("BASE_URL");
        this.baseUrl = (envBaseUrl == null || envBaseUrl.isEmpty()) ? "http://localhost:7000" : envBaseUrl;
    }

    @Override
    public void createUrl(CreateUrlRequest request, StreamObserver<CreateUrlResponse> responseObserver) {
        String originalUrl = request.getOriginalUrl();
        String username = request.getUsername();

        if (originalUrl == null || originalUrl.trim().isEmpty()) {
            responseObserver.onError(new IllegalArgumentException("Debe proporcionar una URL válida"));
            return;
        }

        // Obtener usuario mediante UserService (se asume que getUserByUsername está implementado)
        Usuario user = userService.getUserByUsername(username);
        if (user == null) {
            responseObserver.onError(new IllegalArgumentException("Usuario no encontrado o no autorizado"));
            return;
        }

        // Crear la URL usando la lógica existente en UrlService.
        Url url = urlService.saveUrl(originalUrl, user);
        String shortUrl = url.getShortUrl();
        String previewImage = getPreviewImage(originalUrl);

        Date createdDate = (url.getId() != null) ? url.getId().getDate() : new Date();
        String createdAt = createdDate.toInstant().toString();

        // Construir las estadísticas
        UrlStatistics statistics = UrlStatistics.newBuilder()
                .setAccessCount(url.getAccessCount())
                .addAllAccessTimes(url.getAccessTimes().stream()
                        .map(date -> date.toInstant().toString())
                        .collect(Collectors.toList()))
                .addAllAccessDetails(url.getAccessDetails().stream().map(detail -> {
                    return AccessDetail.newBuilder()
                            .setTimestamp(detail.getTimestamp().toInstant().toString())
                            .setBrowser(detail.getBrowser())
                            .setIp(detail.getIp())
                            .setClientDomain(detail.getClientDomain())
                            .setPlatform(detail.getPlatform())
                            .build();
                }).collect(Collectors.toList()))
                .build();

        UrlEntry entry = UrlEntry.newBuilder()
                .setOriginalUrl(url.getOriginalUrl())
                .setShortUrl(shortUrl)
                .setCreatedAt(createdAt)
                .setStatistics(statistics)
                .setPreviewImageBase64(previewImage)
                .build();

        CreateUrlResponse response = CreateUrlResponse.newBuilder().setUrl(entry).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void listUrls(ListUrlsRequest request, StreamObserver<ListUrlsResponse> responseObserver) {
        String username = request.getUsername();
        if (username == null || username.trim().isEmpty()) {
            responseObserver.onError(new IllegalArgumentException("Debe proporcionar el nombre de usuario"));
            return;
        }
        List<Url> urls = urlService.getAllUrls().stream()
                .filter(u -> u.getUser() != null && username.equals(u.getUser().getUsername()))
                .collect(Collectors.toList());

        List<UrlEntry> entries = urls.stream().map(url -> {
            String shortUrl = url.getShortUrl();
            Date createdDate = (url.getId() != null) ? url.getId().getDate() : new Date();
            String createdAt = createdDate.toInstant().toString();

            UrlStatistics.Builder statsBuilder = UrlStatistics.newBuilder()
                    .setAccessCount(url.getAccessCount())
                    .addAllAccessTimes(url.getAccessTimes().stream()
                            .map(date -> date.toInstant().toString())
                            .collect(Collectors.toList()));
            url.getAccessDetails().forEach(detail -> {
                AccessDetail ad = AccessDetail.newBuilder()
                        .setTimestamp(detail.getTimestamp().toInstant().toString())
                        .setBrowser(detail.getBrowser())
                        .setIp(detail.getIp())
                        .setClientDomain(detail.getClientDomain())
                        .setPlatform(detail.getPlatform())
                        .build();
                statsBuilder.addAccessDetails(ad);
            });
            String previewImage = getPreviewImage(url.getOriginalUrl());
            return UrlEntry.newBuilder()
                    .setOriginalUrl(url.getOriginalUrl())
                    .setShortUrl(shortUrl)
                    .setCreatedAt(createdAt)
                    .setStatistics(statsBuilder.build())
                    .setPreviewImageBase64(previewImage)
                    .build();
        }).collect(Collectors.toList());

        ListUrlsResponse response = ListUrlsResponse.newBuilder().addAllUrls(entries).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private String getPreviewImage(String originalUrl) {
        try {
            String encodedUrl = URLEncoder.encode(originalUrl, StandardCharsets.UTF_8.toString());
            String apiUrl = "https://api.microlink.io?url=" + encodedUrl + "&screenshot=true";
            URL urlObj = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String inputLine;
                StringBuilder responseContent = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    responseContent.append(inputLine);
                }
                in.close();

                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(responseContent.toString());
                String imageUrl = rootNode.path("data").path("screenshot").path("url").asText();
                if (imageUrl == null || imageUrl.isEmpty()) {
                    return "";
                }
                URL imageUrlObj = new URL(imageUrl);
                HttpURLConnection imageConn = (HttpURLConnection) imageUrlObj.openConnection();
                imageConn.setRequestMethod("GET");
                if (imageConn.getResponseCode() == 200) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    InputStream is = imageConn.getInputStream();
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, bytesRead);
                    }
                    is.close();
                    return Base64.getEncoder().encodeToString(baos.toByteArray());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }
}
