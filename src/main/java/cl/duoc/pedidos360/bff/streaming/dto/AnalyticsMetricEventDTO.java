package cl.duoc.pedidos360.bff.streaming.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO tipado para eventos de analítica y telemetría de catálogo/ventas en Kafka.
 * Tópico: pedidos360.analytics.metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsMetricEventDTO {

    private String metricId;
    private String sourceService; // ms-pedidos360-bff | ms-productos360
    private String category;      // ORDER_METRIC | PRODUCT_CREATED | STOCK_UPDATED
    private Long entityId;
    private String entityName;
    private BigDecimal amount;
    private Integer quantityDelta;
    private String details;
    private Instant timestamp;
}
