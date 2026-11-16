package cl.duoc.pedidos360.bff.streaming.service;

import cl.duoc.pedidos360.bff.streaming.dto.AnalyticsMetricEventDTO;
import cl.duoc.pedidos360.bff.streaming.dto.OrderStreamEventDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Servicio de dominio para Analítica en Tiempo Real (Streaming).
 * Mantiene separación clara entre la lógica de negocio/analítica y la infraestructura Kafka.
 */
@Service
@Slf4j
public class StreamingAnalyticsService {

    private final AtomicLong totalOrderEventsProcessed = new AtomicLong(0);
    private final AtomicLong totalMetricEventsProcessed = new AtomicLong(0);
    private final AtomicLong totalDltEvents = new AtomicLong(0);

    private final Map<String, AtomicLong> eventsByType = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> eventsByStatus = new ConcurrentHashMap<>();
    private final Deque<Map<String, Object>> recentEvents = new ConcurrentLinkedDeque<>();

    private static final int MAX_RECENT_EVENTS = 25;

    /**
     * Procesa un evento de dominio de orden proveniente del stream Kafka.
     */
    public void processOrderStreamEvent(OrderStreamEventDTO event, int partition, long offset) {
        if (event == null || event.getOrderId() == null || event.getEventType() == null || event.getEventType().isBlank()) {
            throw new IllegalArgumentException("Payload inválido en OrderStreamEventDTO: orderId y eventType son obligatorios");
        }
        if (event.isSimulateError()) {
            throw new IllegalStateException("Error simulado de negocio en procesamiento de stream para orden #" + event.getOrderId());
        }

        totalOrderEventsProcessed.incrementAndGet();
        eventsByType.computeIfAbsent(event.getEventType(), k -> new AtomicLong(0)).incrementAndGet();
        if (event.getEstadoNuevo() != null) {
            eventsByStatus.computeIfAbsent(event.getEstadoNuevo(), k -> new AtomicLong(0)).incrementAndGet();
        }

        recordRecentEvent(Map.of(
                "topic", "pedidos360.orders.events",
                "partition", partition,
                "offset", offset,
                "eventType", event.getEventType(),
                "orderId", event.getOrderId(),
                "summary", event.getDescripcion() != null ? event.getDescripcion() : "",
                "timestamp", (event.getTimestamp() != null ? event.getTimestamp() : Instant.now()).toString()
        ));

        log.info("[ANALYTICS-DOMAIN] Métricas actualizadas tras evento {} de orden #{} (total eventos órdenes: {})",
                event.getEventType(), event.getOrderId(), totalOrderEventsProcessed.get());
    }

    /**
     * Procesa una métrica de catálogo o negocio proveniente de pedidos360.analytics.metrics.
     */
    public void processAnalyticsMetric(AnalyticsMetricEventDTO metric, int partition, long offset) {
        if (metric == null || metric.getCategory() == null || metric.getCategory().isBlank()) {
            throw new IllegalArgumentException("Payload inválido en AnalyticsMetricEventDTO: category es obligatoria");
        }

        totalMetricEventsProcessed.incrementAndGet();
        eventsByType.computeIfAbsent(metric.getCategory(), k -> new AtomicLong(0)).incrementAndGet();

        recordRecentEvent(Map.of(
                "topic", "pedidos360.analytics.metrics",
                "partition", partition,
                "offset", offset,
                "eventType", metric.getCategory(),
                "sourceService", metric.getSourceService() != null ? metric.getSourceService() : "unknown",
                "summary", (metric.getEntityName() != null ? metric.getEntityName() : "") + " - " + (metric.getDetails() != null ? metric.getDetails() : ""),
                "timestamp", (metric.getTimestamp() != null ? metric.getTimestamp() : Instant.now()).toString()
        ));
    }

    public void registerDltEvent(String topic, int partition, long offset, String key, String detail) {
        totalDltEvents.incrementAndGet();
        recordRecentEvent(Map.of(
                "topic", topic,
                "partition", partition,
                "offset", offset,
                "eventType", "DLT_CAPTURED",
                "key", key != null ? key : "null",
                "summary", detail,
                "timestamp", Instant.now().toString()
        ));
    }

    private void recordRecentEvent(Map<String, Object> item) {
        recentEvents.addFirst(item);
        while (recentEvents.size() > MAX_RECENT_EVENTS) {
            recentEvents.removeLast();
        }
    }

    /**
     * Retorna el snapshot actual de analítica en tiempo real para el Frontend Angular.
     */
    public Map<String, Object> getAnalyticsSnapshot() {
        Map<String, Long> byType = new LinkedHashMap<>();
        eventsByType.forEach((k, v) -> byType.put(k, v.get()));

        Map<String, Long> byStatus = new LinkedHashMap<>();
        eventsByStatus.forEach((k, v) -> byStatus.put(k, v.get()));

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("totalOrderEventsProcessed", totalOrderEventsProcessed.get());
        snapshot.put("totalMetricEventsProcessed", totalMetricEventsProcessed.get());
        snapshot.put("totalDltEvents", totalDltEvents.get());
        snapshot.put("eventsByType", byType);
        snapshot.put("eventsByStatus", byStatus);
        snapshot.put("recentEvents", new ArrayList<>(recentEvents));
        return snapshot;
    }
}
