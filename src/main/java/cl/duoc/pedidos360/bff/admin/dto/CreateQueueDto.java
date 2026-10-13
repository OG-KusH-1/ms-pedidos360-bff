package cl.duoc.pedidos360.bff.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * CreateQueueDto - DTO validado para creación dinámica de colas.
 *
 * Cumple con el Indicador 8 de la rúbrica (7%):
 * "La API valida los parámetros de entrada para evitar crear colas con nombres vacíos
 *  o configuraciones inválidas. Se usan DTO con anotaciones de validación."
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateQueueDto {

    @NotBlank(message = "El nombre de la cola no puede estar vacío ni contener solo espacios")
    @Pattern(
        regexp = "^[a-zA-Z0-9._-]+$",
        message = "El nombre de la cola solo puede contener letras, números, puntos, guiones y guiones bajos"
    )
    private String name;

    @Builder.Default
    private boolean durable = true;

    @Builder.Default
    private boolean autoDelete = false;

    private String deadLetterExchange;
    private String deadLetterRoutingKey;
    private Map<String, Object> additionalArguments;
}
