package com.axioma.loyalty.adapter.in.web.staff;

import com.axioma.loyalty.adapter.in.web.auth.RequireRole;
import com.axioma.loyalty.adapter.in.web.auth.StaffAuthFilter;
import com.axioma.loyalty.application.command.RemoveTenantLogoCommand;
import com.axioma.loyalty.application.command.UploadTenantLogoCommand;
import com.axioma.loyalty.application.handler.GetTenantProfileHandler;
import com.axioma.loyalty.application.handler.RemoveTenantLogoHandler;
import com.axioma.loyalty.application.handler.UpdateTenantIdentityHandler;
import com.axioma.loyalty.application.handler.UpdateTenantSettingsHandler;
import com.axioma.loyalty.application.handler.UploadTenantLogoHandler;
import com.axioma.loyalty.application.query.GetTenantProfileQuery;
import com.axioma.loyalty.domain.model.StaffPrincipal;
import com.axioma.loyalty.domain.model.TenantLogo;
import com.axioma.loyalty.domain.model.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * El dueño edita la identidad, la configuración y el logo de su negocio. El negocio sale siempre
 * del token (StaffPrincipal): ninguna ruta ni cuerpo lleva slug ni tenantId.
 */
@RestController
@RequestMapping("/api/staff/tenant")
@RequireRole(UserRole.OWNER)
@RequiredArgsConstructor
public class TenantProfileController {

    private final GetTenantProfileHandler getTenantProfileHandler;
    private final UpdateTenantIdentityHandler updateTenantIdentityHandler;
    private final UpdateTenantSettingsHandler updateTenantSettingsHandler;
    private final UploadTenantLogoHandler uploadTenantLogoHandler;
    private final RemoveTenantLogoHandler removeTenantLogoHandler;

    @GetMapping
    public Mono<TenantProfileResponse> get(
            @RequestAttribute(StaffAuthFilter.PRINCIPAL_ATTRIBUTE) StaffPrincipal principal) {
        return getTenantProfileHandler.handle(new GetTenantProfileQuery(principal.tenantId()))
                .map(TenantProfileResponse::from);
    }

    @PutMapping("/identity")
    public Mono<TenantProfileResponse> updateIdentity(
            @RequestAttribute(StaffAuthFilter.PRINCIPAL_ATTRIBUTE) StaffPrincipal principal,
            @RequestBody UpdateTenantIdentityRequest request) {
        return updateTenantIdentityHandler.handle(request.toCommand(principal.tenantId()))
                .map(TenantProfileResponse::from);
    }

    @PutMapping("/settings")
    public Mono<TenantProfileResponse> updateSettings(
            @RequestAttribute(StaffAuthFilter.PRINCIPAL_ATTRIBUTE) StaffPrincipal principal,
            @RequestBody UpdateTenantSettingsRequest request) {
        return updateTenantSettingsHandler.handle(request.toCommand(principal.tenantId()))
                .map(TenantProfileResponse::from);
    }

    @PutMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<TenantProfileResponse> uploadLogo(
            @RequestAttribute(StaffAuthFilter.PRINCIPAL_ATTRIBUTE) StaffPrincipal principal,
            @RequestPart("file") Mono<FilePart> file) {
        return MultipartBytes.read(file, TenantLogo.MAX_BYTES)
                .flatMap(content -> uploadTenantLogoHandler.handle(
                        new UploadTenantLogoCommand(principal.tenantId(), content)))
                .map(TenantProfileResponse::from);
    }

    @DeleteMapping("/logo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> removeLogo(@RequestAttribute(StaffAuthFilter.PRINCIPAL_ATTRIBUTE) StaffPrincipal principal) {
        return removeTenantLogoHandler.handle(new RemoveTenantLogoCommand(principal.tenantId()));
    }
}
