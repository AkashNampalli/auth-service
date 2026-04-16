package com.shoptalk.authservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpiry;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(String userId, String role, String email) {
        String token = Jwts.builder()
                .subject(userId)           // who this token belongs to
                .claim("role", role)// extra data inside the token
                .claim("email", email)
                .issuedAt(new Date())      // when it was created
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiry))    // when it expires
                .signWith(secretKey)       // sign with your secret
                .compact();// build the final string

        return token;
    }

    public String generateRefreshToken(String userId) {

        String token = Jwts.builder()
                .subject(userId)
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiry))
                .signWith(secretKey)
                .compact();
        return token;
    }

    public Claims extractClaims(String token) {

        Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();

        return claims;
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractClaims(token);
            // Check expiry — is the expiration date after right now?
            return claims.getExpiration().after(new Date());
            // if expiry is in the future → true (valid)
            // if expiry is in the past  → false (expired)
        } catch (Exception e) {
            return false;  // any exception means invalid — don't rethrow
        }
    }

}