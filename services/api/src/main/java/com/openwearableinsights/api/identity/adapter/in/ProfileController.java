package com.openwearableinsights.api.identity.adapter.in;

import com.openwearableinsights.api.identity.application.ProfileService;
import com.openwearableinsights.api.identity.domain.Profile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for the local account's profile — display name and
 * primary goal. See ProfileService for why this exists: previously there
 * was no visible "self" anywhere in the app.
 */
@RestController
@RequestMapping("/api/v1/profile")
@Tag(name = "Profile", description = "Local account identity — display name and primary goal")
public class ProfileController {

    private static final Long DEFAULT_ACCOUNT_ID = 1L;

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    @Operation(summary = "Get the local account's profile")
    public Profile getProfile() {
        return profileService.fetchProfile(DEFAULT_ACCOUNT_ID);
    }

    @PutMapping
    @Operation(summary = "Update the local account's profile (display name, primary goal)")
    public Profile updateProfile(@RequestBody ProfileUpdateRequest request) {
        return profileService.updateProfile(DEFAULT_ACCOUNT_ID, request.displayName(), request.primaryGoal());
    }

    @GetMapping("/goal-options")
    @Operation(summary = "Get suggested primary-goal options for the profile editor")
    public List<String> getGoalOptions() {
        return ProfileService.SUGGESTED_GOALS;
    }

    public record ProfileUpdateRequest(String displayName, String primaryGoal) {}
}
