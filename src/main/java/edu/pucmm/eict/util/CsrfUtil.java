package edu.pucmm.eict.util;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utilidad para protección CSRF mediante Synchronizer Token Pattern.
 * Basado en recomendaciones de OWASP CSRF Prevention Cheat Sheet.
 
 * Esta clase proporciona métodos para generar y validar tokens anti-CSRF
 * criptográficamente seguros para proteger contra ataques de falsificación
 * de peticiones entre sitios (Cross-Site Request Forgery).
  
    * @see <a href="https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html">OWASP CSRF Prevention</a>
 */
public class CsrfUtil {
    
    private static final SecureRandom secureRandom = new SecureRandom();
    
    // 24 bytes = 192 bits de entropía (suficiente para prevenir ataques de fuerza bruta)
    private static final int TOKEN_LENGTH = 24;
    
    /**
     * Genera un token CSRF criptográficamente seguro.
     
     * Utiliza SecureRandom para generar bytes aleatorios impredecibles,
     * luego los codifica en Base64 URL-safe para transmisión segura en HTML/HTTP.
      
        * @return Token aleatorio en formato Base64 URL-safe (32 caracteres aprox.)
     */
    public static String generateToken() {
        byte[] tokenBytes = new byte[TOKEN_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
    
    /**
     * Valida un token CSRF usando comparación de tiempo constante.
      
     * La comparación de tiempo constante previene timing attacks donde un atacante
     * podría inferir información sobre el token midiendo el tiempo de respuesta.
      
        * @param sessionToken Token almacenado en la sesión del servidor
        * @param requestToken Token recibido en la petición del cliente
        * @return true si los tokens coinciden exactamente, false en caso contrario
     */
    public static boolean validateToken(String sessionToken, String requestToken) {
        if (sessionToken == null || requestToken == null) {
            return false;
        }
        
        // Comparación de tiempo constante para prevenir timing attacks
        // MessageDigest.isEqual compara byte por byte sin short-circuit
        return java.security.MessageDigest.isEqual(
            sessionToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            requestToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }
}
