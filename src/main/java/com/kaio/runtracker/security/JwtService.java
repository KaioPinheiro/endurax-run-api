package com.kaio.runtracker.security;

import com.kaio.runtracker.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;

@Service
public class JwtService {

    private static final Key SECRET_KEY =
            Keys.secretKeyFor(SignatureAlgorithm.HS256);

    public String gerarToken(User user) {

        long agora = System.currentTimeMillis();

        return Jwts.builder()
                .setSubject(user.getEmail())
                .claim("userId", user.getId())
                .claim("nome", user.getNome())
                .claim("role", user.getRole())
                .setIssuedAt(new Date(agora))
                .setExpiration(new Date(agora + 86400000))
                .signWith(SECRET_KEY)
                .compact();
    }

    public String extrairEmail(String token) {
        return extrairTodosClaims(token).getSubject();
    }

    public boolean isTokenValido(String token) {
        try {
            extrairTodosClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims extrairTodosClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(SECRET_KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}