package cl.duoc.pedidos360.bff.streaming.config;

import cl.duoc.pedidos360.bff.streaming.dto.AnalyticsMetricEventDTO;
import cl.duoc.pedidos360.bff.streaming.dto.OrderStreamEventDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuración central de Apache Kafka en ms-pedidos360-bff.
 * Declara los tópicos con sus particiones, factor de replicación, retención y cleanup.policy,
 * además de AdminClient, ErrorHandler con reintentos y Dead Letter Topic (DLT).
 */
@Configuration
@Slf4j
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${app.kafka.topics.orders-events}")
    private String ordersEventsTopic;

    @Value("${app.kafka.topics.analytics-metrics}")
    private String analyticsMetricsTopic;

    @Value("${app.kafka.topics.orders-dlt}")
    private String ordersDltTopic;

    @Value("${app.kafka.topics.partitions:3}")
    private int partitions;

    @Value("${app.kafka.topics.replication-factor:2}")
    private short replicationFactor;

    @Value("${app.kafka.topics.orders-retention-ms:604800000}")
    private String ordersRetentionMs;

    @Value("${app.kafka.topics.analytics-retention-ms:2592000000}")
    private String analyticsRetentionMs;

    @Value("${app.kafka.topics.cleanup-policy-orders:delete}")
    private String cleanupPolicyOrders;

    @Value("${app.kafka.topics.cleanup-policy-analytics:compact,delete}")
    private String cleanupPolicyAnalytics;

    // =========================================================================
    // 1. DECLARACIÓN DE TÓPICOS (Particiones, Réplicas, Retención, Cleanup)
    // =========================================================================

    @Bean
    public NewTopic ordersEventsTopic() {
        return TopicBuilder.name(ordersEventsTopic)
                .partitions(partitions)
                .replicas(replicationFactor)
                .config(TopicConfig.RETENTION_MS_CONFIG, ordersRetentionMs)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, cleanupPolicyOrders)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "1")
                .build();
    }

    @Bean
    public NewTopic analyticsMetricsTopic() {
        return TopicBuilder.name(analyticsMetricsTopic)
                .partitions(partitions)
                .replicas(replicationFactor)
                .config(TopicConfig.RETENTION_MS_CONFIG, analyticsRetentionMs)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, cleanupPolicyAnalytics)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "1")
                .build();
    }

    @Bean
    public NewTopic ordersDltTopic() {
        return TopicBuilder.name(ordersDltTopic)
                .partitions(partitions)
                .replicas(replicationFactor)
                .config(TopicConfig.RETENTION_MS_CONFIG, "1209600000") // 14 días
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, "delete")
                .build();
    }

    // =========================================================================
    // 2. ADMIN CLIENT (Para Microservicio Administrador de Kafka)
    // =========================================================================

    @Bean
    public AdminClient kafkaAdminClient() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configs.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        configs.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 5000);
        return AdminClient.create(configs);
    }

    // =========================================================================
    // 3. MANEJO DE ERRORES, REINTENTOS Y DEAD LETTER TOPIC (DLT)
    // =========================================================================

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        // Si tras los reintentos sigue fallando, redirige al tópico .dlt conservando partición
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> {
                    log.error("[KAFKA-DLT-RECOVERER] Enviando mensaje fallido a DLT '{}'. Topic origen={}, Particion={}, Offset={}, Motivo={}",
                            ordersDltTopic, record.topic(), record.partition(), record.offset(), ex.getMessage());
                    return new TopicPartition(ordersDltTopic, record.partition());
                }
        );

        // 2 reintentos adicionales (3 intentos en total) con 1 segundo de pausa entre cada intento
        FixedBackOff backOff = new FixedBackOff(1000L, 2L);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("[KAFKA-RETRY] Reintento #{} para mensaje key='{}' en topic='{}' (partition={}, offset={}). Error: {}",
                        deliveryAttempt, record.key(), record.topic(), record.partition(), record.offset(), ex.getMessage())
        );

        // Excepciones de validación de payload no deben bloquear la partición: van directo a DLT tras backOff
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        return errorHandler;
    }

    // =========================================================================
    // 4. FACTORIES DE CONSUMIDORES TIPADOS CON MANUAL ACK
    // =========================================================================

    private Map<String, Object> baseConsumerProps(String groupId, Class<?> targetType) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, targetType.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return props;
    }

    @Bean
    public ConsumerFactory<String, OrderStreamEventDTO> orderStreamConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(baseConsumerProps("cg-analytics-engine", OrderStreamEventDTO.class));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderStreamEventDTO> orderKafkaListenerContainerFactory(
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, OrderStreamEventDTO> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderStreamConsumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, AnalyticsMetricEventDTO> analyticsConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(baseConsumerProps("cg-analytics-engine", AnalyticsMetricEventDTO.class));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AnalyticsMetricEventDTO> analyticsKafkaListenerContainerFactory(
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, AnalyticsMetricEventDTO> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(analyticsConsumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}
