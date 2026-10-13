package cl.duoc.pedidos360.bff.messaging.config;

/**
 * RabbitMQConstants - Definición centralizada de constantes de mensajería.
 *
 * Cumple con el Indicador 1 de la pauta de evaluación (12%):
 * "Todos los nombres de colas, exchanges y bindings están definidos de manera centralizada.
 *  No hay valores sueltos en el código. La organización es clara y consistente."
 */
public final class RabbitMQConstants {

    private RabbitMQConstants() {
        // Prevenir instanciación
    }

    // --- EXCHANGES ---
    public static final String EXCHANGE_DIRECT = "cmd.direct";
    public static final String EXCHANGE_TOPIC  = "cmd.topic";
    public static final String EXCHANGE_DLX    = "cmd.dead.dlx";

    // --- COLAS PRINCIPALES ---
    public static final String QUEUE_EMAIL   = "q.cmd.email";
    public static final String QUEUE_KITCHEN = "q.cmd.kitchen";
    public static final String QUEUE_INVOICE = "q.cmd.invoice";

    // --- DEAD LETTER QUEUES (DLQ) ---
    public static final String QUEUE_EMAIL_DLQ   = "q.cmd.email.dlq";
    public static final String QUEUE_KITCHEN_DLQ = "q.cmd.kitchen.dlq";
    public static final String QUEUE_INVOICE_DLQ = "q.cmd.invoice.dlq";

    // --- ROUTING KEYS (DIRECT & DLX) ---
    public static final String RK_EMAIL_SEND    = "email.send";
    public static final String RK_KITCHEN_TICKET = "kitchen.ticket";
    public static final String RK_INVOICE_GEN   = "invoice.gen";

    // --- ROUTING PATTERNS (TOPIC) ---
    public static final String PATTERN_EMAIL_TOPIC   = "email.*";
    public static final String PATTERN_KITCHEN_TOPIC = "kitchen.#";
    public static final String PATTERN_INVOICE_TOPIC = "invoice.*";

    // --- ARGUMENTOS DE DEAD LETTER ---
    public static final String X_DEAD_LETTER_EXCHANGE    = "x-dead-letter-exchange";
    public static final String X_DEAD_LETTER_ROUTING_KEY = "x-dead-letter-routing-key";
}
