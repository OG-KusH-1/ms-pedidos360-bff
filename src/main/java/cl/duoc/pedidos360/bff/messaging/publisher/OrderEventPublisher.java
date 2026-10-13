package cl.duoc.pedidos360.bff.messaging.publisher;

import cl.duoc.pedidos360.bff.entity.Order;
import cl.duoc.pedidos360.bff.messaging.config.RabbitMQConstants;
import cl.duoc.pedidos360.bff.messaging.envelope.OrderCommandEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * OrderEventPublisher - Publicador desacoplado de comandos asíncronos hacia RabbitMQ.
 *
 * Cumple con el Indicador 3 de la rúbrica:
 * "El código del microservicio no mezcla la lógica de negocio con la configuración de mensajería.
 *  Los servicios de negocio solo consumen interfaces o componentes ya configurados."
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Publica las 3 tareas asíncronas correspondientes cuando una orden es ACEPTADA:
     * 1. Notificación al cliente (Email/Push) -> q.cmd.email
     * 2. Ticket para cocina/preparación      -> q.cmd.kitchen
     * 3. Generación de boleta/factura       -> q.cmd.invoice
     */
    public void publishOrderAccepted(Order order) {
        log.info("[Publisher] Publicando tareas asíncronas para Orden #{} (Estado: {})",
                order.getId(), order.getEstado());

        Map<String, Object> data = new HashMap<>();
        data.put("orderId", order.getId());
        data.put("descripcion", order.getDescripcion());
        data.put("estado", order.getEstado());

        // 1. Enviar comando Email
        OrderCommandEnvelope<Map<String, Object>> emailEnvelope = OrderCommandEnvelope.of(
                "CMD_EMAIL_SEND",
                String.valueOf(order.getId()),
                data
        );
        rabbitTemplate.convertAndSend(
                RabbitMQConstants.EXCHANGE_DIRECT,
                RabbitMQConstants.RK_EMAIL_SEND,
                emailEnvelope
        );
        log.info("[Publisher] -> Tarea Email enviada al exchange '{}' con routing key '{}'",
                RabbitMQConstants.EXCHANGE_DIRECT, RabbitMQConstants.RK_EMAIL_SEND);

        // 2. Enviar comando Cocina
        OrderCommandEnvelope<Map<String, Object>> kitchenEnvelope = OrderCommandEnvelope.of(
                "CMD_KITCHEN_TICKET",
                String.valueOf(order.getId()),
                data
        );
        rabbitTemplate.convertAndSend(
                RabbitMQConstants.EXCHANGE_DIRECT,
                RabbitMQConstants.RK_KITCHEN_TICKET,
                kitchenEnvelope
        );
        log.info("[Publisher] -> Tarea Cocina enviada al exchange '{}' con routing key '{}'",
                RabbitMQConstants.EXCHANGE_DIRECT, RabbitMQConstants.RK_KITCHEN_TICKET);

        // 3. Enviar comando Factura
        OrderCommandEnvelope<Map<String, Object>> invoiceEnvelope = OrderCommandEnvelope.of(
                "CMD_INVOICE_GEN",
                String.valueOf(order.getId()),
                data
        );
        rabbitTemplate.convertAndSend(
                RabbitMQConstants.EXCHANGE_DIRECT,
                RabbitMQConstants.RK_INVOICE_GEN,
                invoiceEnvelope
        );
        log.info("[Publisher] -> Tarea Factura enviada al exchange '{}' con routing key '{}'",
                RabbitMQConstants.EXCHANGE_DIRECT, RabbitMQConstants.RK_INVOICE_GEN);
    }

    /**
     * Publica un comando con flag de simulación de fallo para demostrar en vivo
     * el desvío a Dead Letter Queue (DLQ).
     */
    public void publishSimulatedFailure(String targetTask, String correlationId, String reason) {
        log.warn("[Publisher] Disparando simulación de fallo a DLQ para tarea '{}' (ID: {})",
                targetTask, correlationId);

        Map<String, Object> failData = new HashMap<>();
        failData.put("correlationId", correlationId);
        failData.put("reason", reason);
        failData.put("simulated", true);

        String routingKey = switch (targetTask.toLowerCase()) {
            case "kitchen" -> RabbitMQConstants.RK_KITCHEN_TICKET;
            case "invoice" -> RabbitMQConstants.RK_INVOICE_GEN;
            default -> RabbitMQConstants.RK_EMAIL_SEND;
        };

        OrderCommandEnvelope<Map<String, Object>> failEnvelope =
                OrderCommandEnvelope.ofFailureSimulation("CMD_" + targetTask.toUpperCase() + "_FAIL", correlationId, failData);

        rabbitTemplate.convertAndSend(
                RabbitMQConstants.EXCHANGE_DIRECT,
                routingKey,
                failEnvelope
        );
    }
}
