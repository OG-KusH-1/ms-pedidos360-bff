package cl.duoc.pedidos360.bff.repository;

import cl.duoc.pedidos360.bff.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * OrderRepository - Interfaz de acceso a datos para la entidad Order.
 *
 * Extiende JpaRepository que provee CRUD completo sin implementacion manual:
 * - findAll(), findById(), save(), delete(), count(), etc.
 *
 * Spring Data JPA genera la implementacion en tiempo de compilacion.
 * Hibernate traduce las operaciones a SQL compatible con Oracle.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Buscar pedidos por estado (generado automaticamente por Spring Data).
     * Equivale a: SELECT * FROM ORDERS WHERE ESTADO = ?
     */
    List<Order> findByEstado(String estado);

    /**
     * Buscar pedidos por estado, ordenados por fecha descendente.
     */
    List<Order> findByEstadoOrderByFechaCreacionDesc(String estado);

    /**
     * Query JPQL (no SQL nativo) para mayor portabilidad.
     * Retorna los ultimos 100 pedidos ordenados por fecha.
     */
    @Query("SELECT o FROM Order o ORDER BY o.fechaCreacion DESC")
    List<Order> findRecentOrders();
}
