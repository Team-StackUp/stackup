package com.stackup.stackup.user.presentation;

import com.stackup.stackup.common.security.UserPrincipal;
import com.stackup.stackup.user.application.UserService;
import com.stackup.stackup.user.application.dto.UserProfileResult;
import com.stackup.stackup.user.presentation.dto.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "Authenticated user APIs")
public record UserController(UserService userService) {

    @Operation(operationId = "getCurrentUser", summary = "Get current authenticated user profile")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Current user profile returned"),
        @ApiResponse(responseCode = "401", description = "Authentication is required"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        Long userId = principal == null ? null : principal.userId();
        UserProfileResult result = userService.getCurrentUser(userId);
        return ResponseEntity.ok(new UserProfileResponse(
            result.id(),
            result.githubId(),
            result.githubUsername(),
            result.email(),
            result.avatarUrl()
        ));
    }
}
