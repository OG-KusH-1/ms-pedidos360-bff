package cl.duoc.pedidos360.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MsPedidos360BffApplication - Punto de entrada del microservicio BFF.
 *
 * Este servicio actua como Backend For Frontend (BFF):
 * - Valida JWT emitidos por Azure Entra ID
 * - Sirve como capa de agregacion entre el Frontend y la BD Oracle
 * - Es protegido por AWS API Gateway que actua como reverse proxy
 */
@SpringBootApplication
public class MsPedidos360BffApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsPedidos360BffApplication.class, args);
    }
}
