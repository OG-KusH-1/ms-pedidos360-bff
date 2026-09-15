package cl.duoc.pedidos360.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * SecurityConfig - Configuracion de Spring Security como Resource Server.
 *
 * FLUJO DE VALIDACION:
 * 1. La peticion llega con header: Authorization: Bearer <JWT>
 * 2. Spring Security extrae y valida el JWT contra Azure Entra ID:
 *    a. Descarga el JWKS (public keys) desde el issuer-uri
 *    b. Verifica la FIRMA del token
 *    c. Verifica que 'exp' no haya vencido
 *    d. Verifica que 'iss' coincida con el issuer configurado
 *    e. Verifica que 'aud' coincida con app-id-uri
 * 3. Si el token es valido, popula el SecurityContext con los claims
 * 4. @PreAuthorize en los controllers verifica roles/scopes especificos
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)  // Activa @PreAuthorize en controllers
public class SecurityConfig {

    /**
     * Configura la cadena de filtros de seguridad principal.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // --------------------------------------------------------
            // CSRF: Deshabilitado. APIs REST stateless no necesitan CSRF.
            // Las sesiones no se usan; el JWT es la unica credencial.
            // --------------------------------------------------------
            .csrf(AbstractHttpConfigurer::disable)

            // --------------------------------------------------------
            // CORS: Permitir peticiones desde el frontend Angular
            // --------------------------------------------------------
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // --------------------------------------------------------
            // SESION: STATELESS - no crear ni usar HttpSession.
            // El estado se mantiene en el JWT del cliente.
            // --------------------------------------------------------
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // --------------------------------------------------------
            // AUTORIZACION DE RUTAS:
            // - /actuator/health: publico (para health checks de AWS ALB)
            // - Todo lo demas: requiere JWT valido
            // --------------------------------------------------------
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .anyRequest().authenticated()  // TODA peticion debe llevar JWT
            )

            // --------------------------------------------------------
            // OAUTH2 RESOURCE SERVER: Configura validacion de JWT.
            // Spring usa el issuer-uri del application.properties para:
            // 1. Descargar el OIDC Discovery Document
            // 2. Obtener el JWKS endpoint
            // 3. Validar la firma del token
            // --------------------------------------------------------
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );

        return http.build();
    }

    /**
     * Converter para mapear los claims del JWT a GrantedAuthorities de Spring Security.
     *
     * Azure Entra ID incluye los roles en el claim "roles" y los scopes en "scp".
     * Este converter:
     * - Lee el claim "scp" (scopes) y los prefija con "SCOPE_"
     * - Lee el claim "roles" (App Roles) y los prefija con "APPROLE_"
     *
     * Asi, @PreAuthorize("hasAuthority('APPROLE_Operador')") funciona correctamente.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        // Converter para scopes (claim "scp")
        JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();
        scopesConverter.setAuthoritiesClaimName("scp");
        scopesConverter.setAuthorityPrefix("SCOPE_");

        // Converter para App Roles de Azure (claim "roles")
        JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
        rolesConverter.setAuthoritiesClaimName("roles");
        rolesConverter.setAuthorityPrefix("APPROLE_");

        // Combinar ambos converters
        JwtAuthenticationConverter authConverter = new JwtAuthenticationConverter();
        authConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var authorities = new java.util.ArrayList<>(scopesConverter.convert(jwt));
            authorities.addAll(rolesConverter.convert(jwt));
            return authorities;
        });

        return authConverter;
    }

    /**
     * Configuracion CORS para el frontend Angular.
     * En produccion, reemplazar allowedOrigins con el dominio real.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
            "http://localhost:4200"           // Desarrollo local
            // Agrega aqui el dominio del frontend desplegado cuando este disponible:
            // "https://mi-frontend.s3-website.us-east-1.amazonaws.com"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
