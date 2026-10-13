package cl.duoc.pedidos360.bff.messaging.envelope;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * OrderCommandEnvelope - Estructura estándar para tareas y comandos asíncronos.
 *
 * Cumple con el estándar de arquitectura del Caso Semestral:
 * - envelope común: type, eventId, timestamp, traceId, correlationId, payload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCommandEnvelope<T> implements Serializable {

    private String type;            // Tipo de comando (ej: CMD_EMAIL_SEND, CMD_KITCHEN_TICKET, CMD_INVOICE_GEN)
    private String eventId;         // Identificador único del evento
    private Instant timestamp;      // Marca de tiempo ISO-8601
    private String traceId;         // Trazabilidad distribuida
    private String correlationId;   // Identificador de correlación (ej: ID de la orden)
    private T payload;              // Carga útil de datos de negocio
    private boolean forceFail;      // Flag opcional para pruebas y simulación de desvío a DLQ

    public static <T> OrderCommandEnvelope<T> of(String type, String correlationId, T payload) {
        return OrderCommandEnvelope.<T>builder()
                .type(type)
                .eventId(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .traceId(UUID.randomUUID().toString())
                .correlationId(correlationId)
                .payload(payload)
                .forceFail(false)
                .build();
    }

    public static <T> OrderCommandEnvelope<T> ofFailureSimulation(String type, String correlationId, T payload) {
        return OrderCommandEnvelope.<T>builder()
                .type(type)
                .eventId(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .traceId(UUID.randomUUID().toString())
                .correlationId(correlationId)
                .payload(payload)
                .forceFail(true)
                .build();
    }
}
