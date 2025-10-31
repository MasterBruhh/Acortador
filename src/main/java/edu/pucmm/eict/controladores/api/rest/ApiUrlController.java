package edu.pucmm.eict.controladores.api.rest;

import edu.pucmm.eict.modelos.Url;
import edu.pucmm.eict.modelos.Usuario;
import edu.pucmm.eict.services.UrlService;
import edu.pucmm.eict.services.UserService;
import io.javalin.http.Handler;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

public class ApiUrlController extends BaseUrlController {

    private UrlService urlService;
    private UserService userService;

    // Se inyectan ambas dependencias.
    public ApiUrlController(UrlService urlService, UserService userService) {
        this.urlService = urlService;
        this.userService = userService;
    }

    /**
     * Retorna el listado de URLs publicadas por el usuario autenticado,
     * incluyendo la fecha de creación (extraída del ObjectId), estadísticas y vista previa.
     */
    public Handler listUrlsApi = ctx -> {
        String username = ctx.attribute("currentUser");
        if (username == null) {
            ctx.status(401).result("No autorizado");
            return;
        }
        // Obtiene todas las URLs y filtra las que pertenecen al usuario autenticado.
        Collection<Url> allUrls = urlService.getAllUrls();
        List<Map<String, Object>> userUrls = allUrls.stream()
                .filter(url -> url.getUser() != null && username.equals(url.getUser().getUsername()))
                .map(url -> {
                    // Extraer la fecha de creación desde el ObjectId.
                    Date createdAt = (url.getId() != null) ? url.getId().getDate() : new Date();

                    // Calculamos browserStats a partir de cada detalle de acceso.
                    Map<String, Integer> browserStats = new HashMap<>();
                    url.getAccessDetails().forEach(detail -> {
                        String browser = detail.getBrowser();
                        browserStats.put(browser, browserStats.getOrDefault(browser, 0) + 1);
                    });

                    // Extraer los accessTimes y mapear cada detalle a un objeto sencillo.
                    List<String> accessTimes = url.getAccessDetails().stream()
                            .map(detail -> detail.getTimestamp().toInstant().toString())
                            .collect(Collectors.toList());
                    List<Map<String, Object>> accessDetails = url.getAccessDetails().stream()
                            .map(detail -> {
                                Map<String, Object> map = new HashMap<>();
                                map.put("timestamp", detail.getTimestamp().toInstant().toString());
                                map.put("ip", detail.getIp());
                                map.put("browser", detail.getBrowser());
                                map.put("platform", detail.getPlatform());
                                return map;
                            })
                            .collect(Collectors.toList());

                    // Construir el objeto de estadísticas.
                    Map<String, Object> stats = Map.of(
                            "accessCount", url.getAccessCount(),
                            "accessTimes", accessTimes,
                            "accessDetails", accessDetails,
                            "browserStats", browserStats
                    );
                    // Calcular la vista previa usando la URL original.
                    String previewImage = getPreviewImage(url.getOriginalUrl());
                    return Map.of(
                            "originalUrl", url.getOriginalUrl(),
                            "shortUrl", url.getShortUrl(),
                            "createdAt", createdAt,
                            "statistics", stats,
                            "previewImage", previewImage
                    );
                })
                .collect(Collectors.toList());
        ctx.json(userUrls);
    };

    /**
     * Crea un registro de URL y retorna la estructura completa (URL original, URL acortada,
     * fecha de creación, estadísticas y vista previa).
     */
    public Handler createUrlApi = ctx -> {
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        String originalUrl = (body.get("originalUrl") != null) ? body.get("originalUrl").toString() : "";
        if (originalUrl.isEmpty()) {
            ctx.status(400).result("Debe proporcionar una URL válida");
            return;
        }
        String username = ctx.attribute("currentUser");
        if (username == null) {
            ctx.status(401).result("No autorizado");
            return;
        }
        // Obtiene el usuario completo desde UserService.
        Usuario user = userService.getUserByUsername(username);
        if (user == null) {
            ctx.status(401).result("Usuario no encontrado");
            return;
        }
        // Guarda la URL en la base de datos.
        Url url = urlService.saveUrl(originalUrl, user);
        Date createdAt = (url.getId() != null) ? url.getId().getDate() : new Date();
        String previewImage = getPreviewImage(originalUrl);

        Map<String, Integer> browserStats = new HashMap<>();
        url.getAccessDetails().forEach(detail -> {
            String browser = detail.getBrowser();
            browserStats.put(browser, browserStats.getOrDefault(browser, 0) + 1);
        });

        List<String> accessTimes = url.getAccessDetails().stream()
                .map(detail -> detail.getTimestamp().toInstant().toString())
                .collect(Collectors.toList());
        List<Map<String, Object>> accessDetails = url.getAccessDetails().stream()
                .map(detail -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("timestamp", detail.getTimestamp().toInstant().toString());
                    map.put("ip", detail.getIp());
                    map.put("browser", detail.getBrowser());
                    map.put("platform", detail.getPlatform());
                    return map;
                })
                .collect(Collectors.toList());

        Map<String, Object> stats = Map.of(
                "accessCount", url.getAccessCount(),
                "accessTimes", accessTimes,
                "accessDetails", accessDetails,
                "browserStats", browserStats
        );

        Map<String, Object> response = Map.of(
                "originalUrl", url.getOriginalUrl(),
                "shortUrl", url.getShortUrl(),
                "createdAt", createdAt,
                "statistics", stats,
                "previewImage", previewImage
        );
        ctx.json(response);
    };
}
