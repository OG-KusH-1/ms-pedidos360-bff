package cl.duoc.pedidos360.bff.streaming.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO tipado para eventos de streaming de órdenes en Apache Kafka.
 * Tópico: pedidos360.orders.events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStreamEventDTO {

    private String eventId;
    private String eventType; // ORDER_CREATED, ORDER_ACCEPTED, ORDER_STATUS_CHANGED
    private Long orderId;
    private String descripcion;
    private String estadoAnterior;
    private String estadoNuevo;
    private String usuario;
    private Instant timestamp;
    private boolean simulateError; // Para demostrar reintentos y envío a DLT
}
