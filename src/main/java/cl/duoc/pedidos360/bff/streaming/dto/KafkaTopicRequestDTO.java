package cl.duoc.pedidos360.bff.streaming.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO para creación o modificación de tópicos Kafka vía Admin API.
 */
@Data
public class KafkaTopicRequestDTO {

    @NotBlank(message = "El nombre del tópico es obligatorio")
    private String name;

    @Min(value = 1, message = "Debe tener al menos 1 partición")
    private int partitions = 3;

    @Min(value = 1, message = "El factor de replicación debe ser al menos 1")
    private short replicationFactor = 2;

    private String retentionMs = "604800000"; // 7 días por defecto
    private String cleanupPolicy = "delete";  // delete | compact | compact,delete
}
