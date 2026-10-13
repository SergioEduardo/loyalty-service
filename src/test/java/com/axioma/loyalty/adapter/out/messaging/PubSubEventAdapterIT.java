package com.axioma.loyalty.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.axioma.loyalty.domain.model.OutboxEvent;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.PushConfig;
import com.google.pubsub.v1.ReceivedMessage;
import com.google.pubsub.v1.SubscriptionName;
import com.google.pubsub.v1.TopicName;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PubSubEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

/** PubSubEventAdapter contra el emulador de Pub/Sub. Se omite sin Docker. */
@Testcontainers(disabledWithoutDocker = true)
class PubSubEventAdapterIT {

    private static final String PROJECT = "loyalty-test";
    private static final TopicName TOPIC = TopicName.of(PROJECT, "loyalty-events");
    private static final SubscriptionName SUBSCRIPTION = SubscriptionName.of(PROJECT, "wallet-test");
    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CARD = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Container
    static PubSubEmulatorContainer emulator = new PubSubEmulatorContainer(
            DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:583.0.0-emulators"));

    private static ManagedChannel channel;
    private static TransportChannelProvider channels;
    private static final CredentialsProvider NO_CREDENTIALS = NoCredentialsProvider.create();

    @BeforeAll
    static void topicAndSubscription() throws Exception {
        channel = ManagedChannelBuilder.forTarget(emulator.getEmulatorEndpoint()).usePlaintext().build();
        channels = FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
        try (var topics = TopicAdminClient.create(TopicAdminSettings.newBuilder()
                .setTransportChannelProvider(channels).setCredentialsProvider(NO_CREDENTIALS).build())) {
            topics.createTopic(TOPIC);
        }
        try (var subscriptions = subscriptionAdmin()) {
            subscriptions.createSubscription(SUBSCRIPTION, TOPIC, PushConfig.getDefaultInstance(), 10);
        }
    }

    @AfterAll
    static void closeChannel() {
        channel.shutdownNow();
    }

    private static SubscriptionAdminClient subscriptionAdmin() throws Exception {
        return SubscriptionAdminClient.create(SubscriptionAdminSettings.newBuilder()
                .setTransportChannelProvider(channels).setCredentialsProvider(NO_CREDENTIALS).build());
    }

    private static PubSubEventAdapter adapter(TopicName topic) throws Exception {
        return new PubSubEventAdapter(Publisher.newBuilder(topic)
                .setChannelProvider(channels)
                .setCredentialsProvider(NO_CREDENTIALS)
                .build());
    }

    @Test
    void publishesPayloadAsBodyWithTheRoutingAttributes() throws Exception {
        PubSubEventAdapter adapter = adapter(TOPIC);
        try {
            StepVerifier.create(adapter.publish(new OutboxEvent(42, TENANT, null, "TenantChanged",
                    "{\"slug\":\"mi-cafe\",\"name\":\"Mi Café\"}", 0))).verifyComplete();
            StepVerifier.create(adapter.publish(new OutboxEvent(43, TENANT, CARD, "CardChanged",
                    "{\"points\":3}", 2))).verifyComplete();
        } finally {
            adapter.destroy();
        }

        List<PubsubMessage> messages = pull(2);
        PubsubMessage tenantChanged = messages.stream()
                .filter(m -> m.getAttributesMap().get("seq").equals("42")).findFirst().orElseThrow();
        assertThat(tenantChanged.getData().toString(StandardCharsets.UTF_8))
                .isEqualTo("{\"slug\":\"mi-cafe\",\"name\":\"Mi Café\"}");
        assertThat(tenantChanged.getAttributesMap()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                "eventType", "TenantChanged", "tenantId", TENANT.toString(), "cardId", "", "seq", "42"));

        PubsubMessage cardChanged = messages.stream()
                .filter(m -> m.getAttributesMap().get("seq").equals("43")).findFirst().orElseThrow();
        assertThat(cardChanged.getAttributesMap())
                .containsEntry("eventType", "CardChanged")
                .containsEntry("cardId", CARD.toString());
    }

    @Test
    void failsWhenTheBrokerRejectsTheMessage() throws Exception {
        PubSubEventAdapter adapter = adapter(TopicName.of(PROJECT, "no-existe"));
        try {
            StepVerifier.create(adapter.publish(new OutboxEvent(1, TENANT, null, "TenantChanged", "{}", 0)))
                    .expectError()
                    .verify(Duration.ofSeconds(30));
        } finally {
            adapter.destroy();
        }
    }

    @Test
    void disabledAdapterRefusesToPublish() {
        PubSubEventAdapter disabled = PubSubEventAdapter.disabled();

        assertThat(disabled.isEnabled()).isFalse();
        StepVerifier.create(disabled.publish(new OutboxEvent(1, TENANT, null, "TenantChanged", "{}", 0)))
                .expectError(IllegalStateException.class)
                .verify();
    }

    private static List<PubsubMessage> pull(int expected) throws Exception {
        List<PubsubMessage> received = new ArrayList<>();
        try (var subscriptions = subscriptionAdmin()) {
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (received.size() < expected && System.nanoTime() < deadline) {
                var response = subscriptions.pull(SUBSCRIPTION, 10);
                for (ReceivedMessage message : response.getReceivedMessagesList()) {
                    received.add(message.getMessage());
                    subscriptions.acknowledge(SUBSCRIPTION, List.of(message.getAckId()));
                }
            }
        }
        assertThat(received).hasSize(expected);
        return received;
    }
}
