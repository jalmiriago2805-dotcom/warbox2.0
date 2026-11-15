package com.warbox.creator;

import com.warbox.creator.dto.CreatorDtos.CreatorProfileRequest;
import com.warbox.creator.dto.CreatorDtos.CreatorProfileResponse;
import com.warbox.creator.dto.CreatorDtos.PublicCreatorResponse;
import com.warbox.security.AccountUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/creators")
public class CreatorController {

    private final CreatorService creators;

    public CreatorController(CreatorService creators) {
        this.creators = creators;
    }

    @GetMapping("/me")
    public CreatorProfileResponse mine(@AuthenticationPrincipal AccountUserDetails principal) {
        return creators.getMine(principal.id());
    }

    @PostMapping("/me")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatorProfileResponse activate(@AuthenticationPrincipal AccountUserDetails principal,
            @Valid @RequestBody CreatorProfileRequest body) {
        return creators.activate(principal.id(), body);
    }

    @PutMapping("/me")
    public CreatorProfileResponse update(@AuthenticationPrincipal AccountUserDetails principal,
            @Valid @RequestBody CreatorProfileRequest body) {
        return creators.update(principal.id(), body);
    }

    @GetMapping("/{handle}")
    public PublicCreatorResponse publicProfile(@PathVariable String handle) {
        return creators.getPublic(handle);
    }
}
