package edu.pucmm.eict.controladores.api.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Base64;
import java.io.ByteArrayOutputStream;

public abstract class BaseUrlController {

    /**
     * Obtiene la imagen de vista previa de una URL usando la API de Microlink y la retorna en Base64.
     * @param originalUrl La URL original a obtener la vista previa.
     * @return Imagen en Base64 o cadena vacía si ocurre algún error.
     */
    protected String getPreviewImage(String originalUrl) {
        try {
            String encodedUrl = URLEncoder.encode(originalUrl, "UTF-8");
            String apiUrl = "https://api.microlink.io/?url=" + encodedUrl;
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() == 200) {
                InputStream is = conn.getInputStream();
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(is);
                // Estructura esperada: { "data": { "image": { "url": "..." } } }
                JsonNode data = root.get("data");
                if (data != null) {
                    JsonNode image = data.get("image");
                    if (image != null) {
                        JsonNode imageUrlNode = image.get("url");
                        if (imageUrlNode != null && !imageUrlNode.asText().isEmpty()) {
                            String imageUrl = imageUrlNode.asText();
                            return downloadImageAsBase64(imageUrl);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * Descarga la imagen desde una URL y la convierte a formato Base64.
     * @param imageUrl URL de la imagen.
     * @return Imagen en Base64 o cadena vacía en caso de error.
     */
    private String downloadImageAsBase64(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            try (InputStream is = conn.getInputStream();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                byte[] imageBytes = baos.toByteArray();
                return Base64.getEncoder().encodeToString(imageBytes);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }
}
