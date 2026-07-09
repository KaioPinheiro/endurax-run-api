package com.kaio.runtracker.security;

import com.kaio.runtracker.entity.User;
import com.kaio.runtracker.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();

        return path.equals("/auth/login")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Extrair header Authorization
        String authorizationHeader = request.getHeader("Authorization");

        // Se não tem header ou não começa com "Bearer ", passa adiante
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extrair token removendo "Bearer "
        String token = authorizationHeader.substring(7);
        
        System.out.println("=== JWT FILTER ===");
        System.out.println("Authorization Header: " + authorizationHeader);

        // Validar token
        if (!jwtService.isTokenValido(token)) {
            System.out.println("Token inválido");
            filterChain.doFilter(request, response);
            return;
        }

        // Extrair email do token
        String email = jwtService.extrairEmail(token);

        // Buscar usuário pelo email
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Criar autenticação com o usuário
        UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
                user.getEmail(),
                null,
                List.of(
                    new SimpleGrantedAuthority("ROLE_" + user.getRole())
                )
        );

        System.out.println("TOKEN RECEBIDO: " + token);
        System.out.println("EMAIL EXTRAÍDO: " + email);
        System.out.println("USUÁRIO ENCONTRADO: " + user.getEmail());

        // Setar no SecurityContext
        SecurityContextHolder.getContext().setAuthentication(authentication);

        System.out.println(
            SecurityContextHolder.getContext().getAuthentication()
        );

        // Continuar com o request
        filterChain.doFilter(request, response);
    }
}
