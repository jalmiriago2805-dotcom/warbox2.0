package com.warbox.creator;

import com.warbox.account.AccountService;
import com.warbox.common.ApiException;
import com.warbox.creator.dto.CreatorDtos.CreatorProfileRequest;
import com.warbox.creator.dto.CreatorDtos.CreatorProfileResponse;
import com.warbox.creator.dto.CreatorDtos.PublicCreatorResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreatorService {

    private static final String HANDLE_TAKEN_MESSAGE = "Este nome de usuário já está em uso.";

    private final CreatorProfileRepository profiles;
    private final AccountService accountService;
    private final Clock clock;

    public CreatorService(CreatorProfileRepository profiles, AccountService accountService, Clock clock) {
        this.profiles = profiles;
        this.accountService = accountService;
        this.clock = clock;
    }

    public CreatorProfileResponse activate(UUID userId, CreatorProfileRequest request) {
        accountService.requireVerifiedEmail(userId);
        if (profiles.existsByUserId(userId)) {
            throw profileExists();
        }
        if (profiles.existsByHandle(request.handle())) {
            throw handleTaken();
        }
        CreatorProfile profile = new CreatorProfile(userId, request.handle(), request.displayName(),
                request.bio(), Instant.now(clock));
        try {
            profiles.saveAndFlush(profile);
        } catch (DataIntegrityViolationException ex) {
            throw mapConflict(ex);
        }
        return toResponse(profile);
    }

    public CreatorProfileResponse update(UUID userId, CreatorProfileRequest request) {
        accountService.requireVerifiedEmail(userId);
        CreatorProfile profile = profiles.findByUserId(userId).orElseThrow(CreatorService::profileNotFound);
        if (!profile.getHandle().equals(request.handle()) && profiles.existsByHandle(request.handle())) {
            throw handleTaken();
        }
        profile.update(request.handle(), request.displayName(), request.bio(), Instant.now(clock));
        try {
            profiles.saveAndFlush(profile);
        } catch (DataIntegrityViolationException ex) {
            throw mapConflict(ex);
        }
        return toResponse(profile);
    }

    @Transactional(readOnly = true)
    public CreatorProfileResponse getMine(UUID userId) {
        return profiles.findByUserId(userId).map(CreatorService::toResponse)
                .orElseThrow(CreatorService::profileNotFound);
    }

    @Transactional(readOnly = true)
    public PublicCreatorResponse getPublic(String rawHandle) {
        String handle = HandlePolicy.normalize(rawHandle);
        if (!HandlePolicy.isValidFormat(handle)) {
            throw creatorNotFound();
        }
        return profiles.findByHandle(handle)
                .map(p -> new PublicCreatorResponse(p.getHandle(), p.getDisplayName(), p.getBio(), p.getCreatedAt()))
                .orElseThrow(CreatorService::creatorNotFound);
    }

    private static RuntimeException mapConflict(DataIntegrityViolationException ex) {
        String text = ex.getCause() instanceof ConstraintViolationException cve && cve.getConstraintName() != null
                ? cve.getConstraintName()
                : String.valueOf(ex.getMostSpecificCause().getMessage());
        if (text.contains("uk_creator_profiles_user_id")) {
            return profileExists();
        }
        if (text.contains("uk_creator_profiles_handle")) {
            return handleTaken();
        }
        return ex;
    }

    private static ApiException handleTaken() {
        return new ApiException(HttpStatus.CONFLICT, "HANDLE_TAKEN", HANDLE_TAKEN_MESSAGE,
                Map.of("handle", HANDLE_TAKEN_MESSAGE));
    }

    private static ApiException profileExists() {
        return new ApiException(HttpStatus.CONFLICT, "CREATOR_PROFILE_EXISTS", "Você já tem um perfil de criador.");
    }

    private static ApiException profileNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "CREATOR_PROFILE_NOT_FOUND",
                "Você ainda não tem um perfil de criador.");
    }

    private static ApiException creatorNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "CREATOR_NOT_FOUND", "Criador não encontrado.");
    }

    private static CreatorProfileResponse toResponse(CreatorProfile p) {
        return new CreatorProfileResponse(p.getId(), p.getHandle(), p.getDisplayName(), p.getBio(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
