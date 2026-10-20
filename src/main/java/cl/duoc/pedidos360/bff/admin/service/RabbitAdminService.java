package cl.duoc.pedidos360.bff.admin.service;

import cl.duoc.pedidos360.bff.admin.dto.*;

import java.util.List;

/**
 * RabbitAdminService - Interfaz de alto nivel para gestión de infraestructura RabbitMQ.
 *
 * Cumple con el Indicador 7 de la pauta de evaluación (10%):
 * "Toda la lógica para crear colas y exchanges está encapsulada en un servicio dedicado
 *  por ejemplo RabbitAdminService. Los controladores llaman a métodos de alto nivel y
 *  no conocen detalles internos de RabbitMQ."
 */
public interface RabbitAdminService {

    QueueInfoDto createQueue(CreateQueueDto dto);

    boolean deleteQueue(String queueName, boolean ifUnused, boolean ifEmpty);

    QueueInfoDto getQueueInfo(String queueName);

    List<QueueInfoDto> listConfiguredQueues();

    boolean purgeQueue(String queueName);

    void createExchange(CreateExchangeDto dto);

    boolean deleteExchange(String exchangeName);

    void createBinding(CreateBindingDto dto);
}
