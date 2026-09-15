# ms-pedidos360-bff

> **Evaluacion Parcial 1 - Cloud Native** | Backend For Frontend (BFF) en Spring Boot 3 + Oracle DB + Azure Entra ID JWT Validation

## Tecnologias

- Spring Boot 3.3.x (Java 21)
- Spring Security OAuth2 Resource Server
- Spring Cloud Azure AD Starter
- Spring Data JPA + Hibernate (Oracle Dialect)
- Oracle JDBC Driver (ojdbc11)

## Estructura del Proyecto

```
ms-pedidos360-bff/
├── src/
│   └── main/
│       ├── java/cl/duoc/pedidos360/bff/
│       │   ├── config/
│       │   │   └── SecurityConfig.java         # JWT validation + CORS + CSRF
│       │   ├── controller/
│       │   │   └── OrderController.java        # REST endpoints con @PreAuthorize
│       │   ├── entity/
│       │   │   └── Order.java                  # Entidad JPA -> tabla ORDERS (Oracle)
│       │   ├── repository/
│       │   │   └── OrderRepository.java        # JpaRepository (CRUD automatico)
│       │   └── MsPedidos360BffApplication.java # Main class
│       └── resources/
│           ├── application.properties.template # Plantilla de config (sin secretos)
│           └── db/
│               └── 001_create_orders_table.sql # DDL Oracle para crear tabla ORDERS
├── .gitignore                                  # Excluye target/, .idea/, application.properties
└── pom.xml                                     # Dependencias Maven
```

## Configuracion Rapida

### 1. Preparar application.properties

```bash
cp src/main/resources/application.properties.template \
   src/main/resources/application.properties
```

Editar `application.properties` y completar:

| Propiedad | Descripcion |
|-----------|-------------|
| `spring.datasource.url` | URL JDBC Oracle: `jdbc:oracle:thin:@//<HOST>:1521/<SERVICE>` |
| `spring.datasource.username` | Usuario Oracle |
| `spring.datasource.password` | Password Oracle |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | `https://login.microsoftonline.com/<TENANT_ID>/v2.0` |
| `spring.cloud.azure.active-directory.app-id-uri` | `api://<CLIENT_ID>` |

### 2. Crear la tabla en Oracle

```sql
-- Ejecutar el script DDL como DBA:
@src/main/resources/db/001_create_orders_table.sql
```

### 3. Ejecutar con Maven

```bash
./mvnw spring-boot:run
```

## Endpoints

| Metodo | Ruta | Autorizacion | Descripcion |
|--------|------|-------------|-------------|
| GET | `/api/orders` | APPROLE_Operador OR SCOPE_Access | Lista todos los pedidos |
| GET | `/api/orders/{id}` | APPROLE_Operador OR SCOPE_Access | Pedido por ID |
| GET | `/api/orders/me` | Cualquier JWT valido | Info del usuario autenticado |
| GET | `/actuator/health` | Publico | Health check para AWS ALB |

## Flujo de Validacion JWT

```
API Gateway -> Authorization: Bearer <JWT>
                    |
              SecurityFilterChain
                    |
         Descarga JWKS de Azure (cache)
                    |
         Valida FIRMA + exp + iss + aud
                    |
         Extrae roles/scopes del JWT
                    |
            @PreAuthorize verifica
         APPROLE_Operador o SCOPE_Access
                    |
           Consulta Oracle Database
                    |
           Retorna JSON al Frontend
```

## Notas de Seguridad

- `application.properties` esta en `.gitignore` (contiene credenciales).
- El token JWT se valida localmente usando las claves publicas de Azure (JWKS).
- No se almacena estado de sesion en el servidor (stateless).
- CSRF deshabilitado (API REST stateless no lo requiere).
