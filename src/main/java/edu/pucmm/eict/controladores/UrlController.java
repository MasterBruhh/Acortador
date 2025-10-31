package edu.pucmm.eict.controladores;

import edu.pucmm.eict.modelos.AccessDetail;
import edu.pucmm.eict.modelos.Url;
import edu.pucmm.eict.modelos.Usuario;
import edu.pucmm.eict.services.UrlService;
import io.javalin.http.Handler;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;

public class UrlController {

    private UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    // Handler para crear URL (asigna usuario anónimo si no hay uno en sesión)
    public Handler createShortUrl = ctx -> {
        String originalUrl = ctx.formParam("url");
        if (originalUrl != null && !originalUrl.isEmpty()) {
            // Obtener el usuario de la sesión
            Usuario currentUser = ctx.sessionAttribute("user");
            if (currentUser == null) {
                // Si no hay usuario, se usa un identificador de sesión para crear un usuario anónimo
                String sessionId = ctx.sessionAttribute("sessionId");
                if (sessionId == null) {
                    sessionId = UUID.randomUUID().toString();
                    ctx.sessionAttribute("sessionId", sessionId);
                }
                currentUser = new Usuario();
                currentUser.setUsername("anon-" + sessionId);
                currentUser.setRole("anonymous");
            }
            Url url = urlService.saveUrl(originalUrl, currentUser);

            // Obtener el dominio base desde la variable de entorno o usar localhost
            String baseUrl = System.getenv("BASE_URL");
            if(baseUrl == null || baseUrl.isEmpty()){
                baseUrl = "http://localhost:7000";
            }

            String shortUrl = baseUrl + "/go/" + url.getShortUrl();
            try {
                String base64Image = generateQRCodeImage(shortUrl, 200, 200);
                String qrCodeDataUri = "data:image/png;base64," + base64Image;
                ctx.json(Map.of("shortUrl", shortUrl, "qrCode", qrCodeDataUri));
            } catch (WriterException | IOException e) {
                ctx.status(500).result("Error al generar el código QR.");
            }
        } else {
            ctx.status(400).result("Por favor, ingrese una URL válida.");
        }
    };

    public Handler deleteUrl = ctx -> {
        String shortUrl = ctx.formParam("shortUrl");
        // Obtener el usuario de la sesión
        Usuario currentUser = ctx.sessionAttribute("user");
        if (currentUser == null) {
            String sessionId = ctx.sessionAttribute("sessionId");
            if (sessionId != null) {
                currentUser = new Usuario();
                currentUser.setUsername("anon-" + sessionId);
                currentUser.setRole("anonymous");
            }
        }
        Url url = urlService.getUrl(shortUrl);
        if (url == null) {
            ctx.status(404).result("URL no encontrada.");
            return;
        }
        // Permitir la eliminación si el usuario es admin o es el dueño del enlace
        if (currentUser != null &&
                ("admin".equals(currentUser.getRole()) ||
                        (url.getUser() != null && url.getUser().getUsername().equals(currentUser.getUsername())))) {
            boolean deleted = urlService.deleteUrl(shortUrl);
            if (deleted) {
                ctx.redirect("/dashboard/urls");
            } else {
                ctx.status(500).result("Error al eliminar la URL.");
            }
        } else {
            ctx.status(403).result("No tienes permiso para eliminar esta URL.");
        }
    };

    public Handler redirectToOriginalUrl = ctx -> {
        String shortUrl = ctx.pathParam("shortUrl");
        Url url = urlService.getUrl(shortUrl);
        if (url != null) {
            String userAgent = ctx.header("User-Agent");
            String ip = ctx.ip();
            String clientDomain = ctx.header("Host");
            String browser = parseBrowser(userAgent);
            String platform = parsePlatform(userAgent);
            AccessDetail detail = new AccessDetail(new Date(), browser, ip, clientDomain, platform);
            urlService.recordAccess(url, detail);
            ctx.redirect(url.getOriginalUrl());
        } else {
            ctx.status(404).result("Enlace no encontrado.");
        }
    };

    public Handler listUrls = ctx -> {
        Usuario currentUser = ctx.sessionAttribute("user");
        if (currentUser == null) {
            String sessionId = ctx.sessionAttribute("sessionId");
            if (sessionId == null) {
                ctx.json(new ArrayList<>());
                return;
            }
            currentUser = new Usuario();
            currentUser.setUsername("anon-" + sessionId);
            currentUser.setRole("anonymous");
        }
        // Filtrar en el servidor
        Collection<Url> allUrls = urlService.getAllUrls();
        if (!"admin".equals(currentUser.getRole())) {
            Usuario finalCurrentUser = currentUser;
            allUrls = allUrls.stream()
                    .filter(url -> url.getUser() != null && url.getUser().getUsername().equals(finalCurrentUser.getUsername()))
                    .collect(Collectors.toList());
        }
        ctx.json(allUrls);
    };

    public Handler updateUrlRandom = ctx -> {
        // Se reciben los parámetros del formulario
        String originalShort = ctx.formParam("originalShort");
        String newShort = ctx.formParam("newShort");

        // Validar que ambos campos no estén vacíos
        if (originalShort == null || newShort == null || originalShort.isEmpty() || newShort.isEmpty()) {
            ctx.status(400).result("Todos los campos son requeridos.");
            return;
        }

        // Se actualiza el registro en la base de datos. Se asume que el metodo updateShortUrl existe en el servicio.
        boolean updated = urlService.updateShortUrl(originalShort, newShort);
        if (updated) {
            ctx.redirect("/dashboard/urls");
        } else {
            ctx.status(500).result("Error al actualizar el alias.");
        }
    };

    public Handler getAccessStats = ctx -> {
        String shortUrl = ctx.pathParam("shortUrl");
        Url url = urlService.getUrl(shortUrl);
        if (url != null) {
            Map<String, Integer> browserStats = new HashMap<>();
            url.getAccessDetails().forEach(detail -> {
                String browser = detail.getBrowser();
                browserStats.put(browser, browserStats.getOrDefault(browser, 0) + 1);
            });
            // Extraer accessTimes a partir de accessDetails
            List<String> accessTimesStr = url.getAccessDetails().stream()
                    .map(detail -> detail.getTimestamp().toInstant().toString())
                    .toList();
            // Mapear cada AccessDetail a un objeto simple para enviar
            List<Map<String, Object>> accessDetailsList = url.getAccessDetails().stream()
                    .map(detail -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("timestamp", detail.getTimestamp().toInstant().toString());
                        map.put("ip", detail.getIp());
                        map.put("browser", detail.getBrowser());
                        map.put("platform", detail.getPlatform());
                        return map;
                    })
                    .toList();
            ctx.json(Map.of(
                    "accessTimes", accessTimesStr,
                    "browserStats", browserStats,
                    "originalUrl", url.getOriginalUrl(),
                    "accessDetails", accessDetailsList
            ));
        } else {
            ctx.status(404).result("Enlace no encontrado.");
        }
    };




    public Handler previewUrl = ctx -> {
        String originalUrl = ctx.queryParam("url");
        if (originalUrl == null || originalUrl.isEmpty()) {
            ctx.status(400).result("No se proporcionó la URL para vista previa.");
            return;
        }
        try {
            String encodedUrl = URLEncoder.encode(originalUrl, "UTF-8");
            String apiUrl = "https://api.microlink.io?url=" + encodedUrl;
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String inputLine;
                StringBuilder content = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                in.close();
                ctx.contentType("application/json");
                ctx.result(content.toString());
            } else {
                ctx.status(responseCode).result("Error al obtener vista previa.");
            }
        } catch (Exception e) {
            ctx.status(500).result("Error: " + e.getMessage());
        }
    };

    // Metodo para generar el código QR en base64
    private static String generateQRCodeImage(String text, int width, int height) throws WriterException, IOException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    private String parseBrowser(String userAgent) {
        if (userAgent == null) return "Desconocido";
        if (userAgent.contains("Chrome")) return "Chrome";
        if (userAgent.contains("Firefox")) return "Firefox";
        if (userAgent.contains("Safari") && !userAgent.contains("Chrome")) return "Safari";
        if (userAgent.contains("Edge")) return "Edge";
        return "Otro";
    }

    private String parsePlatform(String userAgent) {
        if (userAgent == null) return "Desconocido";
        if (userAgent.contains("Windows")) return "Windows";
        if (userAgent.contains("Mac")) return "MacOS";
        if (userAgent.contains("X11") || userAgent.contains("Linux")) return "Linux";
        return "Otro";
    }
}
