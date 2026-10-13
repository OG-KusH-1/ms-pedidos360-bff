package cl.duoc.pedidos360.bff.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * QueueInfoDto - Información y estado de una cola en RabbitMQ.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueInfoDto {

    private String name;
    private Integer messageCount;
    private Integer consumerCount;
    private Boolean durable;
    private Boolean autoDelete;
    private Boolean exclusive;
    private Map<String, Object> arguments;
    private String status;
}
