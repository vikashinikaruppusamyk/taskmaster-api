package com.vikashini.taskmaster.user;

import com.vikashini.taskmaster.auth.CurrentUserId;
import com.vikashini.taskmaster.user.dto.ChangePasswordRequest;
import com.vikashini.taskmaster.user.dto.UpdateProfileRequest;
import com.vikashini.taskmaster.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
@Tag(name = "Profile")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "View my profile")
    public UserResponse me(@CurrentUserId Long userId) {
        return UserResponse.from(userService.getById(userId));
    }

    @PatchMapping
    @Operation(summary = "Update my name or bio; fields left out are kept")
    public UserResponse update(@CurrentUserId Long userId, @Valid @RequestBody UpdateProfileRequest request) {
        return UserResponse.from(userService.updateProfile(userId, request));
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change my password")
    public void changePassword(@CurrentUserId Long userId, @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userId, request);
    }
}
