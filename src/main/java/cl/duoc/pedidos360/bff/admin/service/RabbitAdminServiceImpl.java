package cl.duoc.pedidos360.bff.admin.service;

import cl.duoc.pedidos360.bff.admin.dto.*;
import cl.duoc.pedidos360.bff.messaging.config.RabbitMQConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * RabbitAdminServiceImpl - Implementación de operaciones de infraestructura AMQP.
 *
 * Encapsula completamente las clases y llamadas de Spring AMQP / RabbitMQ,
 * exponiendo únicamente DTOs limpios y desacoplados para la capa de controladores.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RabbitAdminServiceImpl implements RabbitAdminService {

    private final AmqpAdmin amqpAdmin;

    @Override
    public QueueInfoDto createQueue(CreateQueueDto dto) {
        log.info("[RabbitAdmin] Creando cola '{}' (Durable: {}, AutoDelete: {})",
                dto.getName(), dto.isDurable(), dto.isAutoDelete());

        Map<String, Object> arguments = new HashMap<>();
        if (dto.getAdditionalArguments() != null) {
            arguments.putAll(dto.getAdditionalArguments());
        }

        // Si se especificó Dead Letter Exchange, configurarlo en los argumentos
        if (dto.getDeadLetterExchange() != null && !dto.getDeadLetterExchange().isBlank()) {
            arguments.put(RabbitMQConstants.X_DEAD_LETTER_EXCHANGE, dto.getDeadLetterExchange());
            if (dto.getDeadLetterRoutingKey() != null && !dto.getDeadLetterRoutingKey().isBlank()) {
                arguments.put(RabbitMQConstants.X_DEAD_LETTER_ROUTING_KEY, dto.getDeadLetterRoutingKey());
            }
        }

        org.springframework.amqp.core.Queue queue = new org.springframework.amqp.core.Queue(
                dto.getName(), dto.isDurable(), false, dto.isAutoDelete(), arguments);
        String declaredName = amqpAdmin.declareQueue(queue);

        log.info("[RabbitAdmin] Cola declarada exitosamente: '{}'", declaredName);
        return getQueueInfo(dto.getName());
    }

    @Override
    public boolean deleteQueue(String queueName, boolean ifUnused, boolean ifEmpty) {
        log.info("[RabbitAdmin] Eliminando cola '{}' (ifUnused: {}, ifEmpty: {})", queueName, ifUnused, ifEmpty);
        try {
            if (ifUnused || ifEmpty) {
                amqpAdmin.deleteQueue(queueName, ifUnused, ifEmpty);
                return true;
            } else {
                return amqpAdmin.deleteQueue(queueName);
            }
        } catch (Exception e) {
            log.error("[RabbitAdmin] Error al eliminar cola {}: {}", queueName, e.getMessage());
            return false;
        }
    }

    @Override
    public QueueInfoDto getQueueInfo(String queueName) {
        Properties properties = amqpAdmin.getQueueProperties(queueName);
        if (properties == null) {
            return QueueInfoDto.builder()
                    .name(queueName)
                    .status("NO_ENCONTRADA_O_INACTIVA")
                    .messageCount(0)
                    .consumerCount(0)
                    .build();
        }

        Integer messageCount = (Integer) properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
        Integer consumerCount = (Integer) properties.get(RabbitAdmin.QUEUE_CONSUMER_COUNT);

        return QueueInfoDto.builder()
                .name(queueName)
                .messageCount(messageCount != null ? messageCount : 0)
                .consumerCount(consumerCount != null ? consumerCount : 0)
                .status("ACTIVA")
                .build();
    }

    @Override
    public List<QueueInfoDto> listConfiguredQueues() {
        List<String> systemQueues = List.of(
                RabbitMQConstants.QUEUE_EMAIL,
                RabbitMQConstants.QUEUE_KITCHEN,
                RabbitMQConstants.QUEUE_INVOICE,
                RabbitMQConstants.QUEUE_EMAIL_DLQ,
                RabbitMQConstants.QUEUE_KITCHEN_DLQ,
                RabbitMQConstants.QUEUE_INVOICE_DLQ
        );

        List<QueueInfoDto> results = new ArrayList<>();
        for (String q : systemQueues) {
            results.add(getQueueInfo(q));
        }
        return results;
    }

    @Override
    public boolean purgeQueue(String queueName) {
        log.info("[RabbitAdmin] Purgando mensajes de la cola '{}'", queueName);
        try {
            amqpAdmin.purgeQueue(queueName, false);
            return true;
        } catch (Exception e) {
            log.error("[RabbitAdmin] Error purgando cola {}: {}", queueName, e.getMessage());
            return false;
        }
    }

    @Override
    public void createExchange(CreateExchangeDto dto) {
        log.info("[RabbitAdmin] Creando exchange '{}' de tipo '{}'", dto.getName(), dto.getType());

        Exchange exchange = switch (dto.getType().toLowerCase()) {
            case "topic" -> new TopicExchange(dto.getName(), dto.isDurable(), dto.isAutoDelete());
            case "fanout" -> new FanoutExchange(dto.getName(), dto.isDurable(), dto.isAutoDelete());
            case "headers" -> new HeadersExchange(dto.getName(), dto.isDurable(), dto.isAutoDelete());
            default -> new DirectExchange(dto.getName(), dto.isDurable(), dto.isAutoDelete());
        };

        amqpAdmin.declareExchange(exchange);
        log.info("[RabbitAdmin] Exchange '{}' creado exitosamente", dto.getName());
    }

    @Override
    public boolean deleteExchange(String exchangeName) {
        log.info("[RabbitAdmin] Eliminando exchange '{}'", exchangeName);
        return amqpAdmin.deleteExchange(exchangeName);
    }

    @Override
    public void createBinding(CreateBindingDto dto) {
        log.info("[RabbitAdmin] Creando enlace entre cola '{}' y exchange '{}' con routing key '{}'",
                dto.getQueueName(), dto.getExchangeName(), dto.getRoutingKey());

        Binding binding = new Binding(
                dto.getQueueName(),
                Binding.DestinationType.QUEUE,
                dto.getExchangeName(),
                dto.getRoutingKey(),
                Collections.emptyMap()
        );

        amqpAdmin.declareBinding(binding);
        log.info("[RabbitAdmin] Binding creado exitosamente");
    }
}
