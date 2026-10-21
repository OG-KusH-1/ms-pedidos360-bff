package cl.duoc.pedidos360.bff.controller;

import cl.duoc.pedidos360.bff.entity.Order;
import cl.duoc.pedidos360.bff.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * OrderController - REST Controller para gestion de pedidos.
 *
 * FLUJO COMPLETO DE UNA PETICION:
 * 1. Frontend Angular -> JWT en header Authorization: Bearer <token>
 * 2. AWS API Gateway -> reenvía la peticion al BFF (pasando el JWT)
 * 3. SecurityFilterChain -> valida el JWT (firma + claims de Azure)
 * 4. @PreAuthorize -> verifica que el token tenga el rol/scope requerido
 * 5. Controller -> ejecuta la logica y consulta Oracle DB via JPA
 * 6. Respuesta JSON -> devuelta al frontend
 *
 * BASE PATH: /api/orders
 */
@RestController
@RequestMapping({"/api/orders", "/api/orders/", "/orders", "/orders/", "/api/orders/api/orders"})
@RequiredArgsConstructor  // Lombok: genera constructor con todos los campos @final
@Slf4j                    // Lombok: inyecta logger SLF4J como 'log'
public class OrderController {

    private final OrderRepository orderRepository;
    private final cl.duoc.pedidos360.bff.messaging.publisher.OrderEventPublisher orderEventPublisher;

    /**
     * GET /api/orders
     *
     * Retorna todos los pedidos desde Oracle Database.
     *
     * @PreAuthorize: Autoriza si el JWT contiene ALGUNO de estos claims:
     *   - APPROLE_Operador: App Role configurado en Azure > App Registration > App Roles
     *   - SCOPE_Access: Scope expuesto en Azure > App Registration > Expose an API
     *
     * Si el JWT no tiene ninguno de estos, Spring Security retorna 403 Forbidden.
     *
     * @param jwt El JWT validado, inyectado automaticamente por Spring Security.
     *            Util para extraer claims (nombre usuario, tenant, etc.)
     */
    @GetMapping({"", "/"})
    @PreAuthorize("hasAuthority('APPROLE_Operador') or hasAuthority('SCOPE_Read')")
    public ResponseEntity<List<Order>> getAllOrders(
            @AuthenticationPrincipal Jwt jwt) {

        // Log del usuario que realiza la peticion (claim 'preferred_username' de Azure)
        String userName = jwt.getClaimAsString("preferred_username");
        String oid = jwt.getClaimAsString("oid"); // Object ID unico del usuario en Azure
        log.info("[OrderController] GET /api/orders - Usuario: {} (OID: {})", userName, oid);

        List<Order> orders = orderRepository.findAll();
        log.info("[OrderController] Retornando {} pedidos", orders.size());

        return ResponseEntity.ok(orders);
    }

    /**
     * GET /api/orders/{id}
     *
     * Retorna un pedido especifico por su ID.
     * Requiere rol Operador O scope Access.
     */
    @GetMapping({"/{id}", "/{id}/"})
    @PreAuthorize("hasAuthority('APPROLE_Operador') or hasAuthority('SCOPE_Read')")
    public ResponseEntity<Order> getOrderById(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("[OrderController] GET /api/orders/{} - Usuario: {}", id,
                 jwt.getClaimAsString("preferred_username"));

        return orderRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/orders/me
     *
     * Endpoint de utilidad: retorna informacion del usuario autenticado
     * extraida directamente del JWT. Util para verificar integracion IDaaS.
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(
            @AuthenticationPrincipal Jwt jwt) {

        Map<String, Object> userInfo = Map.of(
            "username",   jwt.getClaimAsString("preferred_username"),
            "name",       jwt.getClaimAsString("name"),
            "oid",        jwt.getClaimAsString("oid"),
            "tenant",     jwt.getClaimAsString("tid"),
            "roles",      jwt.getClaimAsStringList("roles"),
            "scopes",     jwt.getClaimAsString("scp"),
            "token_exp",  jwt.getExpiresAt()
        );

        return ResponseEntity.ok(userInfo);
    }

    /**
     * POST /api/orders
     * Crea un nuevo pedido con estado inicial CREADO.
     */
    @PostMapping({"", "/"})
    @PreAuthorize("hasAuthority('APPROLE_Operador') or hasAuthority('APPROLE_Admin') or hasAuthority('SCOPE_Write') or hasAuthority('SCOPE_Read')")
    public ResponseEntity<Order> createOrder(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {

        String descripcion = body.getOrDefault("descripcion", "Pedido creado desde frontend");
        String usuario = jwt != null ? jwt.getClaimAsString("preferred_username") : "anonimo";
        log.info("[OrderController] Creando pedido: '{}' por usuario: {}", descripcion, usuario);

        Order newOrder = Order.builder()
                .descripcion(descripcion)
                .estado("CREADO")
                .build();

        Order saved = orderRepository.save(newOrder);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(saved);
    }

    /**
     * PATCH /api/orders/{id}/status
     * Actualiza el estado de un pedido y dispara tareas asíncronas en RabbitMQ si pasa a ACEPTADO.
     * Regla clave de negocio: No se puede DESPACHADO sin haber pasado por ACEPTADO.
     */
    @PatchMapping({"/{id}/status", "/{id}/status/"})
    @PreAuthorize("hasAuthority('APPROLE_Operador') or hasAuthority('APPROLE_Admin') or hasAuthority('SCOPE_Write') or hasAuthority('SCOPE_Read')")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {

        String nuevoEstado = body.get("estado");
        if (nuevoEstado == null || nuevoEstado.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El campo 'estado' es obligatorio"));
        }
        nuevoEstado = nuevoEstado.toUpperCase();

        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        // Validación de regla de negocio del Caso Semestral: No se puede despachar sin aceptar
        if ("DESPACHADO".equals(nuevoEstado) && !"ACEPTADO".equals(order.getEstado()) && !"EN_PREPARACION".equals(order.getEstado())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Regla de negocio violada: No se puede 'DESPACHAR' un pedido que no haya sido 'ACEPTADO' previamente."
            ));
        }

        order.setEstado(nuevoEstado);
        Order updated = orderRepository.save(order);
        log.info("[OrderController] Pedido #{} actualizado a estado '{}'", id, nuevoEstado);

        // Si el pedido es ACEPTADO, disparar la mensajería asíncrona hacia RabbitMQ
        if ("ACEPTADO".equals(nuevoEstado)) {
            orderEventPublisher.publishOrderAccepted(updated);
        }

        return ResponseEntity.ok(updated);
    }
}
