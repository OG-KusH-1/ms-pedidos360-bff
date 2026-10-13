package cl.duoc.pedidos360.bff.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CreateBindingDto - DTO validado para creación dinámica de bindings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBindingDto {

    @NotBlank(message = "El nombre de la cola destino es obligatorio")
    private String queueName;

    @NotBlank(message = "El nombre del exchange origen es obligatorio")
    private String exchangeName;

    @NotBlank(message = "La routing key o patrón de enrutamiento es obligatorio")
    private String routingKey;
}
