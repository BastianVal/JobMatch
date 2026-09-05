package mx.jobmatch.profile.adapters.web;

import mx.jobmatch.identity.adapters.security.AccountPrincipal;
import mx.jobmatch.profile.application.ProfileService;
import mx.jobmatch.profile.domain.ProfessionalProfile;
import mx.jobmatch.profile.domain.ProfileDraft;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Profile("api")
@RestController
@RequestMapping("/api/v1/me")
public class ProfileController {
    private final ProfileService profiles;
    public ProfileController(ProfileService profiles) { this.profiles = profiles; }

    @GetMapping("/profile")
    ResponseEntity<ProfessionalProfile> get(@AuthenticationPrincipal AccountPrincipal principal) {
        return response(profiles.get(principal.accountId()));
    }

    @PutMapping("/profile")
    ResponseEntity<ProfessionalProfile> replace(@AuthenticationPrincipal AccountPrincipal principal,
                                                 @RequestHeader("If-Match") String ifMatch,
                                                 @RequestBody ProfileDraft request) {
        return response(profiles.replace(principal.accountId(), version(ifMatch), request));
    }

    @GetMapping("/target-roles")
    List<ProfileDraft.TargetRole> targetRoles(@AuthenticationPrincipal AccountPrincipal principal) {
        return profiles.get(principal.accountId()).data().targetRoles();
    }

    @PutMapping("/target-roles")
    ResponseEntity<ProfessionalProfile> targetRoles(@AuthenticationPrincipal AccountPrincipal principal,
                                                     @RequestHeader("If-Match") String ifMatch,
                                                     @RequestBody List<ProfileDraft.TargetRole> roles) {
        var current = profiles.get(principal.accountId()).data();
        return response(profiles.replace(principal.accountId(), version(ifMatch), new ProfileDraft(
                current.headline(), current.summary(), current.location(), current.seniority(), roles,
                current.preferences(), current.excludedEmployers(), current.trajectory(), current.education(),
                current.certifications(), current.languages(), current.skills())));
    }

    @GetMapping("/trajectory")
    List<ProfileDraft.TrajectoryItem> trajectory(@AuthenticationPrincipal AccountPrincipal principal) {
        return profiles.get(principal.accountId()).data().trajectory();
    }

    @PutMapping("/trajectory")
    ResponseEntity<ProfessionalProfile> trajectory(@AuthenticationPrincipal AccountPrincipal principal,
                                                    @RequestHeader("If-Match") String ifMatch,
                                                    @RequestBody List<ProfileDraft.TrajectoryItem> trajectory) {
        var current = profiles.get(principal.accountId()).data();
        return response(profiles.replace(principal.accountId(), version(ifMatch), new ProfileDraft(
                current.headline(), current.summary(), current.location(), current.seniority(), current.targetRoles(),
                current.preferences(), current.excludedEmployers(), trajectory, current.education(),
                current.certifications(), current.languages(), current.skills())));
    }

    @GetMapping("/skills")
    List<ProfileDraft.Skill> skills(@AuthenticationPrincipal AccountPrincipal principal) {
        return profiles.get(principal.accountId()).data().skills();
    }

    @PutMapping("/skills")
    ResponseEntity<ProfessionalProfile> skills(@AuthenticationPrincipal AccountPrincipal principal,
                                                @RequestHeader("If-Match") String ifMatch,
                                                @RequestBody List<ProfileDraft.Skill> skills) {
        var current = profiles.get(principal.accountId()).data();
        return response(profiles.replace(principal.accountId(), version(ifMatch), new ProfileDraft(
                current.headline(), current.summary(), current.location(), current.seniority(), current.targetRoles(),
                current.preferences(), current.excludedEmployers(), current.trajectory(), current.education(),
                current.certifications(), current.languages(), skills)));
    }

    private static ResponseEntity<ProfessionalProfile> response(ProfessionalProfile profile) {
        return ResponseEntity.ok().eTag(Long.toString(profile.version())).body(profile);
    }

    private static long version(String header) {
        try {
            String value = header.strip();
            if (value.startsWith("W/")) value = value.substring(2);
            return Long.parseLong(value.replace("\"", ""));
        } catch (RuntimeException invalid) {
            throw new mx.jobmatch.profile.application.ProfileExceptions.InvalidProfile("If-Match no es válido.");
        }
    }
}
