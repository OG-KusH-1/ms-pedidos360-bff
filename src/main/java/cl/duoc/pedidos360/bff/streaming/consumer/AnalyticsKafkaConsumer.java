package cl.duoc.pedidos360.bff.streaming.consumer;

import cl.duoc.pedidos360.bff.streaming.dto.AnalyticsMetricEventDTO;
import cl.duoc.pedidos360.bff.streaming.dto.OrderStreamEventDTO;
import cl.duoc.pedidos360.bff.streaming.service.StreamingAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumidor Kafka para Analítica en Tiempo Real (Consumer Group: cg-analytics-engine).
 * Mantiene la lógica de negocio desacoplada en StreamingAnalyticsService.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsKafkaConsumer {

    private final StreamingAnalyticsService analyticsService;

    @KafkaListener(
            topics = "${app.kafka.topics.orders-events}",
            groupId = "cg-analytics-engine",
            containerFactory = "orderKafkaListenerContainerFactory"
    )
    public void onOrderStreamEvent(
            @Payload OrderStreamEventDTO event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            Acknowledgment acknowledgment) {

        log.info("[KAFKA-ANALYTICS] Consumiendo evento de órdenes: partition={}, offset={}, key='{}', eventType='{}'",
                partition, offset, key, event != null ? event.getEventType() : "null");

        analyticsService.processOrderStreamEvent(event, partition, offset);
        acknowledgment.acknowledge();
    }

    @KafkaListener(
            topics = "${app.kafka.topics.analytics-metrics}",
            groupId = "cg-analytics-engine",
            containerFactory = "analyticsKafkaListenerContainerFactory"
    )
    public void onAnalyticsMetricEvent(
            @Payload AnalyticsMetricEventDTO metric,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            Acknowledgment acknowledgment) {

        log.info("[KAFKA-ANALYTICS] Consumiendo métrica: partition={}, offset={}, key='{}', category='{}'",
                partition, offset, key, metric != null ? metric.getCategory() : "null");

        analyticsService.processAnalyticsMetric(metric, partition, offset);
        acknowledgment.acknowledge();
    }

    @KafkaListener(
            topics = "${app.kafka.topics.orders-dlt}",
            groupId = "cg-dlt-monitor",
            containerFactory = "orderKafkaListenerContainerFactory"
    )
    public void onDltEvent(
            ConsumerRecord<String, Object> record,
            Acknowledgment acknowledgment) {

        String detail = "Mensaje defectuoso capturado en DLT tras agotar reintentos. Payload=" + record.value();
        log.error("[KAFKA-DLT-LISTENER] DLT recibido -> topic='{}', partition={}, offset={}, key='{}', payload={}",
                record.topic(), record.partition(), record.offset(), record.key(), record.value());

        analyticsService.registerDltEvent(record.topic(), record.partition(), record.offset(), record.key(), detail);
        acknowledgment.acknowledge();
    }
}
