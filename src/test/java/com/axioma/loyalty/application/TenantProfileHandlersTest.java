package com.axioma.loyalty.application;

import static com.axioma.loyalty.application.AdminFixtures.TENANT_ID;
import static com.axioma.loyalty.application.ErrorAssertions.hasCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.axioma.loyalty.adapter.out.image.ImageIoImageNormalizer;
import com.axioma.loyalty.application.command.RemoveTenantLogoCommand;
import com.axioma.loyalty.application.command.UpdateTenantIdentityCommand;
import com.axioma.loyalty.application.command.UpdateTenantSettingsCommand;
import com.axioma.loyalty.application.command.UploadTenantLogoCommand;
import com.axioma.loyalty.application.handler.LogoCleanup;
import com.axioma.loyalty.application.handler.OutboxFastPath;
import com.axioma.loyalty.application.handler.RemoveTenantLogoHandler;
import com.axioma.loyalty.application.handler.TenantChangeWriter;
import com.axioma.loyalty.application.handler.UpdateTenantIdentityHandler;
import com.axioma.loyalty.application.handler.UpdateTenantSettingsHandler;
import com.axioma.loyalty.application.handler.UploadTenantLogoHandler;
import com.axioma.loyalty.application.validator.UpdateTenantIdentityValidator;
import com.axioma.loyalty.application.validator.UpdateTenantSettingsValidator;
import com.axioma.loyalty.application.validator.UploadTenantLogoValidator;
import com.axioma.loyalty.domain.model.Tenant;
import com.axioma.loyalty.domain.model.TenantChanged;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.port.out.ImageNormalizerPort;
import com.axioma.loyalty.domain.port.out.TenantEventOutbox;
import com.axioma.loyalty.domain.port.out.TenantRepository;
import com.axioma.loyalty.support.FakeEventPublisher;
import com.axioma.loyalty.support.InMemoryLogoStorage;
import com.axioma.loyalty.support.InMemoryOutboxEvents;
import com.axioma.loyalty.support.OutboxFixtures;
import com.axioma.loyalty.support.TestImages;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

/** Casos de uso del perfil del negocio con un repositorio en memoria y un fake de LogoStoragePort. */
class TenantProfileHandlersTest {

    private final AtomicReference<Tenant> stored = new AtomicReference<>();
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final TenantEventOutbox outbox = mock(TenantEventOutbox.class);
    private final InMemoryLogoStorage storage = new InMemoryLogoStorage();
    private final ImageNormalizerPort normalizer = new ImageIoImageNormalizer(Schedulers.immediate());

    private final InMemoryOutboxEvents outboxEvents = new InMemoryOutboxEvents();
    private final FakeEventPublisher publisher = new FakeEventPublisher(true);
    private final OutboxFastPath fastPath =
            OutboxFixtures.fastPath(outboxEvents, publisher, OutboxFixtures.properties(8, 100, 5_000));

    private final TenantChangeWriter writer = new TenantChangeWriter(tenants, outbox);
    private final LogoCleanup cleanup = new LogoCleanup(storage);
    private final UpdateTenantIdentityHandler updateIdentity =
            new UpdateTenantIdentityHandler(new UpdateTenantIdentityValidator(), writer, fastPath);
    private final UpdateTenantSettingsHandler updateSettings =
            new UpdateTenantSettingsHandler(new UpdateTenantSettingsValidator(), writer, fastPath);
    private final UploadTenantLogoHandler upload =
            new UploadTenantLogoHandler(new UploadTenantLogoValidator(), normalizer, storage, writer, cleanup, fastPath);
    private final RemoveTenantLogoHandler remove = new RemoveTenantLogoHandler(writer, cleanup, fastPath);

    @BeforeEach
    void repository() {
        stored.set(AdminFixtures.tenant(true));
        when(tenants.findByIdForUpdate(any())).thenAnswer(inv ->
                Mono.justOrEmpty(stored.get()).filter(t -> t.id().equals(inv.getArgument(0))));
        when(tenants.update(any())).thenAnswer(inv -> {
            stored.set(inv.getArgument(0));
            return Mono.just(stored.get());
        });
        when(outbox.append(any())).thenAnswer(inv -> Mono.just(outboxEvents.add(
                inv.<TenantChanged>getArgument(0).tenantId(), TenantChanged.EVENT_TYPE, "{}")));
    }

    @Test
    void updateIdentityPersistsLowercaseColorAndEmitsTenantChanged() {
        StepVerifier.create(updateIdentity.handle(
                        new UpdateTenantIdentityCommand(TENANT_ID, " Café Nuevo ", "Bienvenido", "#ABCDEF")))
                .assertNext(profile -> {
                    assertThat(profile.name()).isEqualTo("Café Nuevo");
                    assertThat(profile.brandColor()).isEqualTo("#abcdef");
                    assertThat(profile.slug()).isEqualTo("mi-cafe");
                })
                .verifyComplete();

        assertThat(stored.get().brandColor()).isEqualTo("#abcdef");
        var event = ArgumentCaptor.forClass(TenantChanged.class);
        verify(outbox).append(event.capture());
        assertThat(event.getValue().name()).isEqualTo("Café Nuevo");
        assertThat(event.getValue().tenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    void sameIdentityDoesNotWriteNorNotify() {
        Tenant current = stored.get();
        StepVerifier.create(updateIdentity.handle(new UpdateTenantIdentityCommand(
                        TENANT_ID, current.name(), current.description(), current.brandColor())))
                .expectNextCount(1)
                .verifyComplete();
        verify(tenants, never()).update(any());
        verify(outbox, never()).append(any());
    }

    @Test
    void invalidIdentityTouchesNothing() {
        StepVerifier.create(updateIdentity.handle(new UpdateTenantIdentityCommand(TENANT_ID, "A", null, "#112233")))
                .expectErrorSatisfies(hasCode(ErrorCode.INVALID_TEXT))
                .verify();
        verify(tenants, never()).findByIdForUpdate(any());
    }

    @Test
    void inactiveTenantIsNotFound() {
        stored.set(AdminFixtures.tenant(false));
        StepVerifier.create(updateIdentity.handle(
                        new UpdateTenantIdentityCommand(TENANT_ID, "Mi Café", null, "#112233")))
                .expectErrorSatisfies(hasCode(ErrorCode.TENANT_NOT_FOUND))
                .verify();
    }

    @Test
    void updateSettingsEmitsTenantChanged() {
        StepVerifier.create(updateSettings.handle(new UpdateTenantSettingsCommand(TENANT_ID, 8, "Postre", 60, 120)))
                .assertNext(profile -> {
                    assertThat(profile.pointsGoal()).isEqualTo(8);
                    assertThat(profile.rewardText()).isEqualTo("Postre");
                    assertThat(profile.cooldownSeconds()).isEqualTo(60);
                    assertThat(profile.undoWindowSeconds()).isEqualTo(120);
                })
                .verifyComplete();
        var event = ArgumentCaptor.forClass(TenantChanged.class);
        verify(outbox).append(event.capture());
        assertThat(event.getValue().pointsGoal()).isEqualTo(8);
    }

    @Test
    void uploadStoresNormalizedPngUnderContentHashAndEmitsTenantChanged() {
        StepVerifier.create(upload.handle(new UploadTenantLogoCommand(TENANT_ID, TestImages.jpeg(300, 200))))
                .assertNext(profile -> assertThat(profile.logoUrl())
                        .startsWith(InMemoryLogoStorage.BASE_URL + "/tenants/" + TENANT_ID + "/logo-")
                        .endsWith(".png"))
                .verifyComplete();

        assertThat(storage.objects()).hasSize(1);
        String key = storage.objects().keySet().iterator().next();
        assertThat(key).matches("tenants/" + TENANT_ID + "/logo-[0-9a-f]{64}\\.png");
        assertThat(TestImages.pngSize(storage.objects().get(key))).containsExactly(512, 512);
        assertThat(stored.get().logoObjectKey()).isEqualTo(key);
        var event = ArgumentCaptor.forClass(TenantChanged.class);
        verify(outbox).append(event.capture());
        assertThat(event.getValue().logoUrl()).isEqualTo(InMemoryLogoStorage.BASE_URL + "/" + key);
        assertThat(storage.deleted()).isEmpty();
    }

    @Test
    void replacingLogoDeletesThePreviousObject() {
        upload.handle(new UploadTenantLogoCommand(TENANT_ID, TestImages.png(300, 200))).block();
        String first = stored.get().logoObjectKey();

        upload.handle(new UploadTenantLogoCommand(TENANT_ID, TestImages.jpeg(640, 480))).block();
        String second = stored.get().logoObjectKey();

        assertThat(second).isNotEqualTo(first);
        assertThat(storage.deleted()).containsExactly(first);
        assertThat(storage.objects()).containsOnlyKeys(second);
    }

    @Test
    void uploadingTheSameLogoAgainKeepsTheObject() {
        byte[] logo = TestImages.png(300, 200);
        upload.handle(new UploadTenantLogoCommand(TENANT_ID, logo)).block();
        upload.handle(new UploadTenantLogoCommand(TENANT_ID, logo)).block();

        assertThat(storage.deleted()).isEmpty();
        assertThat(storage.objects()).containsOnlyKeys(stored.get().logoObjectKey());
        verify(outbox, times(1)).append(any());
    }

    @Test
    void failingToDeleteThePreviousLogoDoesNotFailTheUpload() {
        upload.handle(new UploadTenantLogoCommand(TENANT_ID, TestImages.png(300, 200))).block();
        storage.failDeletes(true);

        StepVerifier.create(upload.handle(new UploadTenantLogoCommand(TENANT_ID, TestImages.jpeg(300, 200))))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void rejectedLogoIsNeitherStoredNorSaved() {
        StepVerifier.create(upload.handle(new UploadTenantLogoCommand(TENANT_ID, TestImages.svg())))
                .expectErrorSatisfies(hasCode(ErrorCode.UNSUPPORTED_IMAGE_FORMAT))
                .verify();
        StepVerifier.create(upload.handle(new UploadTenantLogoCommand(TENANT_ID, TestImages.pngBomb(8000, 8000))))
                .expectErrorSatisfies(hasCode(ErrorCode.IMAGE_DIMENSIONS_TOO_LARGE))
                .verify();
        assertThat(storage.objects()).isEmpty();
        verify(tenants, never()).update(any());
    }

    @Test
    void removeLogoDeletesObjectAndIsIdempotent() {
        upload.handle(new UploadTenantLogoCommand(TENANT_ID, TestImages.png(300, 200))).block();
        String key = stored.get().logoObjectKey();

        StepVerifier.create(remove.handle(new RemoveTenantLogoCommand(TENANT_ID))).verifyComplete();
        assertThat(stored.get().logoUrl()).isNull();
        assertThat(stored.get().logoObjectKey()).isNull();
        assertThat(storage.deleted()).containsExactly(key);

        StepVerifier.create(remove.handle(new RemoveTenantLogoCommand(TENANT_ID))).verifyComplete();
        assertThat(storage.deleted()).containsExactly(key);
        verify(outbox, times(2)).append(any()); // subida y borrado; el segundo borrado no avisa
    }

    // --- camino rápido (12.11) ---

    @Test
    void changePublishesItsEventInTheSameRequest() {
        updateIdentity.handle(new UpdateTenantIdentityCommand(TENANT_ID, "Café Nuevo", null, "#112233")).block();

        assertThat(publisher.confirmedIds()).containsExactly(1L);
        assertThat(outboxEvents.row(1).status()).isEqualTo("PUBLISHED");
    }

    @Test
    void unchangedTenantPublishesNothing() {
        Tenant current = stored.get();
        updateIdentity.handle(new UpdateTenantIdentityCommand(
                TENANT_ID, current.name(), current.description(), current.brandColor())).block();

        assertThat(publisher.attemptedIds()).isEmpty();
    }

    @Test
    void failingPubSubLeavesTheEventPendingAndTheResponseUnchanged() {
        publisher.mode(FakeEventPublisher.Mode.FAIL);

        StepVerifier.create(updateSettings.handle(new UpdateTenantSettingsCommand(TENANT_ID, 8, "Postre", 60, 120)))
                .assertNext(profile -> assertThat(profile.pointsGoal()).isEqualTo(8))
                .verifyComplete();
        assertThat(outboxEvents.row(1).status()).isEqualTo("PENDING");
        assertThat(outboxEvents.row(1).event().attempts()).isEqualTo(1);
    }

    @Test
    void slowPubSubIsCutAtTheFastPathTimeout() {
        publisher.mode(FakeEventPublisher.Mode.HANG);

        StepVerifier.create(remove.handle(new RemoveTenantLogoCommand(TENANT_ID)))
                .verifyComplete(); // sin logo no hay aviso: no espera
        StepVerifier.create(upload.handle(new UploadTenantLogoCommand(TENANT_ID, TestImages.png(300, 200))))
                .expectNextCount(1)
                .expectComplete()
                .verify(java.time.Duration.ofSeconds(5));
        assertThat(outboxEvents.row(1).status()).isEqualTo("PENDING");
        assertThat(outboxEvents.row(1).event().attempts()).isZero(); // reclamado: lo retoma el Scheduler
        assertThat(publisher.confirmed()).isEmpty();
    }
}
