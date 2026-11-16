package cl.duoc.pedidos360.bff.streaming.controller;

import cl.duoc.pedidos360.bff.entity.Order;
import cl.duoc.pedidos360.bff.streaming.dto.KafkaTopicRequestDTO;
import cl.duoc.pedidos360.bff.streaming.producer.OrderKafkaProducer;
import cl.duoc.pedidos360.bff.streaming.service.KafkaAdminService;
import cl.duoc.pedidos360.bff.streaming.service.StreamingAnalyticsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller Administrador de Apache Kafka y Streaming Analytics.
 * Cumple con los indicadores de administración de tópicos, configuraciones,
 * inspección de Consumer Groups y consulta de analítica en tiempo real.
 */
@RestController
@RequestMapping("/api/admin/kafka")
@RequiredArgsConstructor
@Slf4j
public class KafkaAdminController {

    private final KafkaAdminService kafkaAdminService;
    private final StreamingAnalyticsService analyticsService;
    private final OrderKafkaProducer orderKafkaProducer;

    @GetMapping("/topics")
    public ResponseEntity<?> listTopics() {
        try {
            List<Map<String, Object>> topics = kafkaAdminService.listTopics();
            return ResponseEntity.ok(topics);
        } catch (Exception e) {
            log.error("[KAFKA-ADMIN-API] Error listando tópicos: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No se pudo conectar al clúster Kafka: " + e.getMessage()));
        }
    }

    @PostMapping("/topics")
    public ResponseEntity<?> createTopic(@Valid @RequestBody KafkaTopicRequestDTO req) {
        try {
            kafkaAdminService.createTopic(req);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Tópico '" + req.getName() + "' creado exitosamente",
                    "partitions", req.getPartitions(),
                    "replicationFactor", req.getReplicationFactor(),
                    "retentionMs", req.getRetentionMs(),
                    "cleanupPolicy", req.getCleanupPolicy()
            ));
        } catch (Exception e) {
            log.error("[KAFKA-ADMIN-API] Error creando tópico '{}': {}", req.getName(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/topics/{name}/config")
    public ResponseEntity<?> updateTopicConfig(
            @PathVariable String name,
            @RequestBody Map<String, String> body) {
        try {
            String retentionMs = body.get("retentionMs");
            String cleanupPolicy = body.get("cleanupPolicy");
            kafkaAdminService.updateTopicConfig(name, retentionMs, cleanupPolicy);
            return ResponseEntity.ok(Map.of(
                    "message", "Configuración actualizada para '" + name + "'",
                    "retentionMs", retentionMs != null ? retentionMs : "sin cambio",
                    "cleanupPolicy", cleanupPolicy != null ? cleanupPolicy : "sin cambio"
            ));
        } catch (Exception e) {
            log.error("[KAFKA-ADMIN-API] Error modificando tópico '{}': {}", name, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/topics/{name}")
    public ResponseEntity<?> deleteTopic(@PathVariable String name) {
        try {
            kafkaAdminService.deleteTopic(name);
            return ResponseEntity.ok(Map.of("message", "Tópico '" + name + "' eliminado exitosamente"));
        } catch (Exception e) {
            log.error("[KAFKA-ADMIN-API] Error eliminando tópico '{}': {}", name, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/consumer-groups")
    public ResponseEntity<?> listConsumerGroups() {
        try {
            return ResponseEntity.ok(kafkaAdminService.listConsumerGroups());
        } catch (Exception e) {
            log.error("[KAFKA-ADMIN-API] Error listando consumer groups: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> getStreamingAnalytics() {
        return ResponseEntity.ok(analyticsService.getAnalyticsSnapshot());
    }

    /**
     * Endpoint de laboratorio para emitir un evento de prueba normal o simular un error
     * que active la política de reintentos (FixedBackOff) y el envío al Dead Letter Topic (DLT).
     */
    @PostMapping("/simulate")
    public ResponseEntity<?> simulateStreamEvent(@RequestBody Map<String, Object> body) {
        boolean simulateError = Boolean.TRUE.equals(body.get("simulateError"));
        Long orderId = body.get("orderId") != null ? Long.valueOf(body.get("orderId").toString()) : 777L;
        String descripcion = body.get("descripcion") != null
                ? body.get("descripcion").toString()
                : (simulateError ? "Payload defectuoso de prueba (dispara reintentos + DLT)" : "Evento de streaming simulado desde Consola Kafka");

        Order demoOrder = Order.builder()
                .id(orderId)
                .descripcion(descripcion)
                .estado(simulateError ? "ERROR_SIMULADO" : "ACEPTADO")
                .build();

        String eventType = simulateError ? "ORDER_SIMULATED_ERROR" : "ORDER_STREAM_TEST";
        orderKafkaProducer.publishOrderEvent(demoOrder, eventType, "CREADO", "admin-console", simulateError);
        if (!simulateError) {
            orderKafkaProducer.publishAnalyticsMetric("STREAM_TEST_METRIC", orderId, "Simulación #" + orderId, null, 1, descripcion);
        }

        return ResponseEntity.accepted().body(Map.of(
                "message", simulateError
                        ? "Evento con error simulado publicado en Kafka. Ejecutará 2 reintentos y aterrizará en pedidos360.orders.events.dlt"
                        : "Evento de streaming publicado correctamente en pedidos360.orders.events",
                "simulateError", simulateError,
                "orderId", orderId
        ));
    }
}
