package cl.duoc.pedidos360.bff.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SimulateDlqDto - DTO para simular fallo de procesamiento y desvío a DLQ.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulateDlqDto {

    @NotBlank(message = "El tipo de tarea es obligatorio ('email', 'kitchen' o 'invoice')")
    private String taskType;

    @NotNull(message = "El ID del pedido de prueba es obligatorio")
    private Long orderId;

    @Builder.Default
    private String reason = "Error simulado para demostración de Dead Letter Queue (DLQ)";
}
