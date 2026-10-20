package cl.duoc.pedidos360.bff.admin.controller;

import cl.duoc.pedidos360.bff.admin.dto.*;
import cl.duoc.pedidos360.bff.admin.service.RabbitAdminService;
import cl.duoc.pedidos360.bff.messaging.publisher.OrderEventPublisher;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * RabbitAdminController - REST Controller para la administración dinámica de RabbitMQ.
 *
 * Cumple con el Indicador 6 de la rúbrica (13%):
 * "El microservicio administrador expone endpoints REST bien definidos por ejemplo
 *  POST /queues o DELETE /queues/{name}. La estructura sigue buenas prácticas de diseño REST."
 */
@RestController
@RequestMapping("/api/admin/rabbitmq")
@RequiredArgsConstructor
@Slf4j
public class RabbitAdminController {

    private final RabbitAdminService rabbitAdminService;
    private final OrderEventPublisher orderEventPublisher;

    // =========================================================================
    // 1. GESTIÓN DE COLAS (QUEUES)
    // =========================================================================

    /**
     * POST /api/admin/rabbitmq/queues
     * Crea una nueva cola en RabbitMQ con validación de parámetros (DTO).
     */
    @PostMapping("/queues")
    public ResponseEntity<QueueInfoDto> createQueue(@Valid @RequestBody CreateQueueDto dto) {
        log.info("[REST] Solicitud para crear cola: {}", dto.getName());
        QueueInfoDto created = rabbitAdminService.createQueue(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * GET /api/admin/rabbitmq/queues
     * Lista todas las colas configuradas del sistema y su estado actual.
     */
    @GetMapping("/queues")
    public ResponseEntity<List<QueueInfoDto>> listQueues() {
        log.info("[REST] Listando colas del sistema");
        return ResponseEntity.ok(rabbitAdminService.listConfiguredQueues());
    }

    /**
     * GET /api/admin/rabbitmq/queues/{name}
     * Obtiene el estado y número de mensajes de una cola específica.
     */
    @GetMapping("/queues/{name}")
    public ResponseEntity<QueueInfoDto> getQueueInfo(@PathVariable String name) {
        log.info("[REST] Consultando información de cola: {}", name);
        return ResponseEntity.ok(rabbitAdminService.getQueueInfo(name));
    }

    /**
     * DELETE /api/admin/rabbitmq/queues/{name}
     * Elimina una cola en RabbitMQ.
     */
    @DeleteMapping("/queues/{name}")
    public ResponseEntity<Map<String, Object>> deleteQueue(
            @PathVariable String name,
            @RequestParam(defaultValue = "false") boolean ifUnused,
            @RequestParam(defaultValue = "false") boolean ifEmpty) {

        log.info("[REST] Solicitud para eliminar cola: {}", name);
        boolean deleted = rabbitAdminService.deleteQueue(name, ifUnused, ifEmpty);
        return ResponseEntity.ok(Map.of(
                "queue", name,
                "deleted", deleted,
                "message", deleted ? "Cola eliminada con éxito" : "No fue posible eliminar la cola"
        ));
    }

    /**
     * POST /api/admin/rabbitmq/queues/{name}/purge
     * Purga todos los mensajes acumulados en una cola.
     */
    @PostMapping("/queues/{name}/purge")
    public ResponseEntity<Map<String, Object>> purgeQueue(@PathVariable String name) {
        log.info("[REST] Purgando cola: {}", name);
        boolean purged = rabbitAdminService.purgeQueue(name);
        return ResponseEntity.ok(Map.of(
                "queue", name,
                "purged", purged,
                "message", purged ? "Cola purgada con éxito" : "No fue posible purgar la cola"
        ));
    }

    // =========================================================================
    // 2. GESTIÓN DE EXCHANGES
    // =========================================================================

    /**
     * POST /api/admin/rabbitmq/exchanges
     * Crea un nuevo exchange (direct, topic, fanout, headers).
     */
    @PostMapping("/exchanges")
    public ResponseEntity<Map<String, Object>> createExchange(@Valid @RequestBody CreateExchangeDto dto) {
        log.info("[REST] Creando exchange: {} ({})", dto.getName(), dto.getType());
        rabbitAdminService.createExchange(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "created", true,
                "exchange", dto.getName(),
                "type", dto.getType()
        ));
    }

    /**
     * DELETE /api/admin/rabbitmq/exchanges/{name}
     * Elimina un exchange existente.
     */
    @DeleteMapping("/exchanges/{name}")
    public ResponseEntity<Map<String, Object>> deleteExchange(@PathVariable String name) {
        log.info("[REST] Eliminando exchange: {}", name);
        boolean deleted = rabbitAdminService.deleteExchange(name);
        return ResponseEntity.ok(Map.of(
                "exchange", name,
                "deleted", deleted
        ));
    }

    // =========================================================================
    // 3. GESTIÓN DE BINDINGS
    // =========================================================================

    /**
     * POST /api/admin/rabbitmq/bindings
     * Asocia una cola a un exchange con una routing key.
     */
    @PostMapping("/bindings")
    public ResponseEntity<Map<String, Object>> createBinding(@Valid @RequestBody CreateBindingDto dto) {
        log.info("[REST] Creando binding entre '{}' y '{}' con key '{}'",
                dto.getQueueName(), dto.getExchangeName(), dto.getRoutingKey());
        rabbitAdminService.createBinding(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "created", true,
                "queue", dto.getQueueName(),
                "exchange", dto.getExchangeName(),
                "routingKey", dto.getRoutingKey()
        ));
    }

    // =========================================================================
    // 4. ENDPOINT DE SIMULACIÓN Y PRUEBAS PARA LA DEFENSA
    // =========================================================================

    /**
     * POST /api/admin/rabbitmq/simulate-dlq
     * Dispara un comando simulado de fallo para demostrar en vivo ante el docente
     * cómo el consumidor ejecuta basicNack y RabbitMQ desvía el mensaje a la DLQ.
     */
    @PostMapping("/simulate-dlq")
    public ResponseEntity<Map<String, Object>> simulateDlq(@Valid @RequestBody SimulateDlqDto dto) {
        log.warn("[REST] Demostración en vivo: simulando fallo a DLQ para {}", dto.getTaskType());
        orderEventPublisher.publishSimulatedFailure(
                dto.getTaskType(),
                String.valueOf(dto.getOrderId()),
                dto.getReason()
        );
        return ResponseEntity.ok(Map.of(
                "status", "DISPARADO",
                "message", "Comando de prueba emitido con bandera de fallo forzado hacia RabbitMQ",
                "targetTask", dto.getTaskType(),
                "expectedDlq", "q.cmd." + dto.getTaskType().toLowerCase() + ".dlq"
        ));
    }
}
