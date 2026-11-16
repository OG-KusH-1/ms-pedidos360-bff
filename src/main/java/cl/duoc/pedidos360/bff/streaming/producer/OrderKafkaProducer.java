package cl.duoc.pedidos360.bff.streaming.producer;

import cl.duoc.pedidos360.bff.entity.Order;
import cl.duoc.pedidos360.bff.streaming.dto.AnalyticsMetricEventDTO;
import cl.duoc.pedidos360.bff.streaming.dto.OrderStreamEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Productor Kafka para eventos de streaming de órdenes y métricas en tiempo real.
 * Complementa la arquitectura RabbitMQ existente (comandas/tareas) sin afectarla.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.orders-events}")
    private String ordersEventsTopic;

    @Value("${app.kafka.topics.analytics-metrics}")
    private String analyticsMetricsTopic;

    /**
     * Publica un evento de ciclo de vida de pedido en el tópico pedidos360.orders.events.
     * Usa orderId como key para garantizar orden cronológico dentro de la misma partición.
     */
    public void publishOrderEvent(Order order, String eventType, String estadoAnterior, String usuario, boolean simulateError) {
        String key = order.getId() != null ? String.valueOf(order.getId()) : UUID.randomUUID().toString();
        OrderStreamEventDTO event = OrderStreamEventDTO.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .orderId(order.getId())
                .descripcion(order.getDescripcion())
                .estadoAnterior(estadoAnterior)
                .estadoNuevo(order.getEstado())
                .usuario(usuario != null ? usuario : "sistema")
                .timestamp(Instant.now())
                .simulateError(simulateError)
                .build();

        kafkaTemplate.send(ordersEventsTopic, key, event).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[KAFKA-PRODUCER] Error enviando evento {} para orden #{}: {}", eventType, order.getId(), ex.getMessage());
            } else {
                log.info("[KAFKA-PRODUCER] Evento {} publicado en topic='{}', partition={}, offset={}, key='{}'",
                        eventType,
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        key);
            }
        });
    }

    /**
     * Publica una métrica de analítica al tópico pedidos360.analytics.metrics.
     */
    public void publishAnalyticsMetric(String category, Long entityId, String entityName, BigDecimal amount, Integer delta, String details) {
        String key = entityId != null ? "order-" + entityId : UUID.randomUUID().toString();
        AnalyticsMetricEventDTO metric = AnalyticsMetricEventDTO.builder()
                .metricId(UUID.randomUUID().toString())
                .sourceService("ms-pedidos360-bff")
                .category(category)
                .entityId(entityId)
                .entityName(entityName)
                .amount(amount != null ? amount : BigDecimal.ZERO)
                .quantityDelta(delta != null ? delta : 1)
                .details(details)
                .timestamp(Instant.now())
                .build();

        kafkaTemplate.send(analyticsMetricsTopic, key, metric).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[KAFKA-PRODUCER] Error enviando métrica {}: {}", category, ex.getMessage());
            } else {
                log.info("[KAFKA-PRODUCER] Métrica '{}' publicada en topic='{}', partition={}, offset={}",
                        category,
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
