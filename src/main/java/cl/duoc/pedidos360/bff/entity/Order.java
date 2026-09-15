package cl.duoc.pedidos360.bff.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidad JPA que mapea la tabla ORDERS en Oracle Database.
 *
 * NOTA sobre naming en Oracle:
 * - Los nombres de tabla/columna deben estar en MAYUSCULAS (convencion Oracle)
 * - @Table(name = "ORDERS") y @Column(name = "...") evitan conflictos
 * - "ORDER" es palabra reservada en SQL, por eso usamos "ORDERS"
 */
@Entity
@Table(name = "ORDERS", schema = "PEDIDOS360")   // Esquema de Oracle (reemplazar si es distinto)
@Data                                              // Lombok: genera getters, setters, equals, hashCode, toString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    /**
     * Clave primaria autogenerada.
     * Oracle usa SEQUENCE por defecto (no AUTO_INCREMENT como MySQL).
     * GenerationType.SEQUENCE es la estrategia correcta para Oracle.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orders_seq")
    @SequenceGenerator(
        name       = "orders_seq",
        sequenceName = "ORDERS_SEQ",   // Nombre de la secuencia en Oracle (debe existir)
        allocationSize = 1              // Incremento de la secuencia
    )
    @Column(name = "ID", nullable = false)
    private Long id;

    /**
     * Estado del pedido.
     * Valores esperados: PENDIENTE, EN_PROCESO, COMPLETADO, CANCELADO
     */
    @Column(name = "ESTADO", nullable = false, length = 50)
    private String estado;

    /**
     * Descripcion o detalle del pedido.
     */
    @Column(name = "DESCRIPCION", nullable = false, length = 500)
    private String descripcion;

    /**
     * Fecha de creacion del pedido.
     * insertable=false, updatable=false: Oracle maneja el default con SYSDATE.
     */
    @Column(name = "FECHA_CREACION", insertable = false, updatable = false)
    private java.time.LocalDateTime fechaCreacion;
}
