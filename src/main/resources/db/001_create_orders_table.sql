-- ============================================================
-- SCRIPT DDL - Oracle Database
-- Proyecto: Pedidos360 - Evaluacion Parcial 1
-- Ejecutar como usuario DBA o con permisos CREATE TABLE/SEQUENCE
-- ============================================================

-- Crear esquema (si no existe)
-- CREATE USER PEDIDOS360 IDENTIFIED BY <PASSWORD>;
-- GRANT CONNECT, RESOURCE, CREATE SESSION TO PEDIDOS360;
-- GRANT UNLIMITED TABLESPACE TO PEDIDOS360;

-- Secuencia para el ID autoincremental (Oracle no tiene AUTO_INCREMENT)
CREATE SEQUENCE PEDIDOS360.ORDERS_SEQ
  START WITH 1
  INCREMENT BY 1
  NOCACHE
  NOCYCLE;

-- Tabla principal de pedidos
CREATE TABLE PEDIDOS360.ORDERS (
  ID           NUMBER(19, 0)    DEFAULT PEDIDOS360.ORDERS_SEQ.NEXTVAL NOT NULL,
  ESTADO       VARCHAR2(50)     NOT NULL,
  DESCRIPCION  VARCHAR2(500)    NOT NULL,
  FECHA_CREACION TIMESTAMP      DEFAULT SYSTIMESTAMP,
  CONSTRAINT PK_ORDERS PRIMARY KEY (ID),
  CONSTRAINT CHK_ESTADO CHECK (ESTADO IN ('PENDIENTE', 'EN_PROCESO', 'COMPLETADO', 'CANCELADO'))
);

-- Comentarios descriptivos (buena practica Oracle)
COMMENT ON TABLE  PEDIDOS360.ORDERS              IS 'Tabla principal de pedidos del sistema Pedidos360';
COMMENT ON COLUMN PEDIDOS360.ORDERS.ID           IS 'Identificador unico del pedido (PK)';
COMMENT ON COLUMN PEDIDOS360.ORDERS.ESTADO       IS 'Estado actual: PENDIENTE, EN_PROCESO, COMPLETADO, CANCELADO';
COMMENT ON COLUMN PEDIDOS360.ORDERS.DESCRIPCION  IS 'Descripcion detallada del pedido';
COMMENT ON COLUMN PEDIDOS360.ORDERS.FECHA_CREACION IS 'Timestamp de creacion del registro';

-- Datos de prueba para desarrollo/testing
INSERT INTO PEDIDOS360.ORDERS (ESTADO, DESCRIPCION) VALUES ('PENDIENTE',   'Pedido de materiales de oficina - Factura #001');
INSERT INTO PEDIDOS360.ORDERS (ESTADO, DESCRIPCION) VALUES ('EN_PROCESO',  'Pedido de equipos informaticos - Licitacion #045');
INSERT INTO PEDIDOS360.ORDERS (ESTADO, DESCRIPCION) VALUES ('COMPLETADO',  'Pedido de suministros medicos entregado correctamente');
INSERT INTO PEDIDOS360.ORDERS (ESTADO, DESCRIPCION) VALUES ('CANCELADO',   'Pedido cancelado por proveedor - Reemitir');
INSERT INTO PEDIDOS360.ORDERS (ESTADO, DESCRIPCION) VALUES ('PENDIENTE',   'Solicitud de cotizacion para servicios de limpieza Q4');

COMMIT;

-- Verificar insercion
SELECT * FROM PEDIDOS360.ORDERS ORDER BY ID;
