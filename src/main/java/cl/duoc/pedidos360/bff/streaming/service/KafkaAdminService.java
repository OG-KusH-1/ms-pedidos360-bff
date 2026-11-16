package cl.duoc.pedidos360.bff.streaming.service;

import cl.duoc.pedidos360.bff.streaming.dto.KafkaTopicRequestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Servicio de administración dinámica de Apache Kafka usando AdminClient.
 * Permite crear, listar, modificar configuraciones y eliminar tópicos,
 * además de inspeccionar Consumer Groups.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaAdminService {

    private final AdminClient adminClient;

    public List<Map<String, Object>> listTopics() throws ExecutionException, InterruptedException {
        ListTopicsOptions options = new ListTopicsOptions().listInternal(false);
        Set<String> topicNames = adminClient.listTopics(options).names().get();
        if (topicNames.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, TopicDescription> descriptions = adminClient.describeTopics(topicNames).allTopicNames().get();
        List<ConfigResource> resources = topicNames.stream()
                .map(name -> new ConfigResource(ConfigResource.Type.TOPIC, name))
                .collect(Collectors.toList());
        Map<ConfigResource, Config> configs = adminClient.describeConfigs(resources).all().get();

        List<Map<String, Object>> result = new ArrayList<>();
        for (String name : topicNames.stream().sorted().collect(Collectors.toList())) {
            TopicDescription desc = descriptions.get(name);
            Config cfg = configs.get(new ConfigResource(ConfigResource.Type.TOPIC, name));

            int partCount = desc != null ? desc.partitions().size() : 0;
            int repFactor = (desc != null && !desc.partitions().isEmpty()) ? desc.partitions().get(0).replicas().size() : 0;

            String retention = cfg != null && cfg.get(TopicConfig.RETENTION_MS_CONFIG) != null
                    ? cfg.get(TopicConfig.RETENTION_MS_CONFIG).value() : "604800000";
            String cleanup = cfg != null && cfg.get(TopicConfig.CLEANUP_POLICY_CONFIG) != null
                    ? cfg.get(TopicConfig.CLEANUP_POLICY_CONFIG).value() : "delete";

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", name);
            item.put("partitions", partCount);
            item.put("replicationFactor", repFactor);
            item.put("retentionMs", retention);
            item.put("cleanupPolicy", cleanup);
            result.add(item);
        }
        return result;
    }

    public void createTopic(KafkaTopicRequestDTO req) throws ExecutionException, InterruptedException {
        Map<String, String> configs = new HashMap<>();
        if (req.getRetentionMs() != null && !req.getRetentionMs().isBlank()) {
            configs.put(TopicConfig.RETENTION_MS_CONFIG, req.getRetentionMs());
        }
        if (req.getCleanupPolicy() != null && !req.getCleanupPolicy().isBlank()) {
            configs.put(TopicConfig.CLEANUP_POLICY_CONFIG, req.getCleanupPolicy());
        }

        NewTopic newTopic = new NewTopic(req.getName().trim(), req.getPartitions(), req.getReplicationFactor())
                .configs(configs);

        adminClient.createTopics(Collections.singletonList(newTopic)).all().get();
        log.info("[KAFKA-ADMIN] Tópico '{}' creado (particiones={}, replicas={}, retentionMs={}, cleanupPolicy={})",
                req.getName(), req.getPartitions(), req.getReplicationFactor(), req.getRetentionMs(), req.getCleanupPolicy());
    }

    public void updateTopicConfig(String topicName, String retentionMs, String cleanupPolicy)
            throws ExecutionException, InterruptedException {
        ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topicName);
        List<AlterConfigOp> ops = new ArrayList<>();

        if (retentionMs != null && !retentionMs.isBlank()) {
            ops.add(new AlterConfigOp(new ConfigEntry(TopicConfig.RETENTION_MS_CONFIG, retentionMs), AlterConfigOp.OpType.SET));
        }
        if (cleanupPolicy != null && !cleanupPolicy.isBlank()) {
            ops.add(new AlterConfigOp(new ConfigEntry(TopicConfig.CLEANUP_POLICY_CONFIG, cleanupPolicy), AlterConfigOp.OpType.SET));
        }

        if (!ops.isEmpty()) {
            adminClient.incrementalAlterConfigs(Map.of(resource, ops)).all().get();
            log.info("[KAFKA-ADMIN] Configuración actualizada para tópico '{}': retentionMs={}, cleanupPolicy={}",
                    topicName, retentionMs, cleanupPolicy);
        }
    }

    public void deleteTopic(String topicName) throws ExecutionException, InterruptedException {
        adminClient.deleteTopics(Collections.singletonList(topicName)).all().get();
        log.info("[KAFKA-ADMIN] Tópico '{}' eliminado exitosamente.", topicName);
    }

    public List<Map<String, Object>> listConsumerGroups() throws ExecutionException, InterruptedException {
        Collection<ConsumerGroupListing> listings = adminClient.listConsumerGroups().all().get();
        if (listings.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> groupIds = listings.stream()
                .map(ConsumerGroupListing::groupId)
                .sorted()
                .collect(Collectors.toList());

        Map<String, ConsumerGroupDescription> descriptions =
                adminClient.describeConsumerGroups(groupIds).all().get();

        List<Map<String, Object>> result = new ArrayList<>();
        for (String groupId : groupIds) {
            ConsumerGroupDescription desc = descriptions.get(groupId);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("groupId", groupId);
            item.put("state", desc.state() != null ? desc.state().toString() : "UNKNOWN");
            item.put("membersCount", desc.members().size());
            item.put("coordinator", desc.coordinator() != null ? desc.coordinator().host() + ":" + desc.coordinator().port() : "N/A");
            item.put("topics", desc.members().stream()
                    .flatMap(m -> m.assignment().topicPartitions().stream())
                    .map(tp -> tp.topic() + "[" + tp.partition() + "]")
                    .sorted()
                    .collect(Collectors.toList()));
            result.add(item);
        }
        return result;
    }
}
