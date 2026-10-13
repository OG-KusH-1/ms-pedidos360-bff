package cl.duoc.pedidos360.bff.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CreateExchangeDto - DTO validado para creación dinámica de exchanges.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateExchangeDto {

    @NotBlank(message = "El nombre del exchange no puede estar vacío")
    @Pattern(
        regexp = "^[a-zA-Z0-9._-]+$",
        message = "El nombre del exchange solo puede contener caracteres alfanuméricos, puntos, guiones y guiones bajos"
    )
    private String name;

    @NotBlank(message = "El tipo de exchange es obligatorio (direct, topic, fanout, headers)")
    @Pattern(
        regexp = "^(?i)(direct|topic|fanout|headers)$",
        message = "El tipo de exchange debe ser: direct, topic, fanout o headers"
    )
    private String type;

    @Builder.Default
    private boolean durable = true;

    @Builder.Default
    private boolean autoDelete = false;
}
