package edu.pucmm.eict.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.SignatureAlgorithm;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JwtUtil {
    // Llave secreta (debe mantenerse segura en producción)
    public static final String LLAVE_SECRETA = "tralalero-tralala-21-tralalero-tralala-21";
    private static final SecretKey secretKey = Keys.hmacShaKeyFor(LLAVE_SECRETA.getBytes(StandardCharsets.UTF_8));

    // Tiempo de expiración del token, por ejemplo: 1 día en milisegundos
    public static final long EXPIRATION_TIME = 86400000;


    // Genera el token incluyendo el username y rol
    public static String generateToken(String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                // Usamos signWith con la secretKey; la firma se genera usando HS256
                .signWith(secretKey)
                .compact();
    }

    // Valida el token y retorna los claims; utiliza la sintaxis de la demo con verifyWith()
    public static Claims validateToken(String token) throws JwtException {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }



    // Extrae el username del token (subject)
    public static String extractUsername(String token) {
        return validateToken(token).getSubject();
    }
}
