package gov.brgy.ebms.api;

import gov.brgy.ebms.usermgmt.UserManagementService;
import gov.brgy.ebms.usermgmt.dto.AssignRolesRequest;
import gov.brgy.ebms.usermgmt.dto.CreateUserRequest;
import gov.brgy.ebms.usermgmt.dto.UpdateUserRequest;
import gov.brgy.ebms.usermgmt.dto.UserDetailResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserManagementController {

    private final UserManagementService userManagementService;

    public UserManagementController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    public ResponseEntity<Page<UserDetailResponse>> list(Pageable pageable) {
        return ResponseEntity.ok(userManagementService.listUsers(pageable));
    }

    @PostMapping
    public ResponseEntity<UserDetailResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(userManagementService.createUser(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDetailResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(userManagementService.getUser(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDetailResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateUserRequest request
    ) {
        return ResponseEntity.ok(userManagementService.updateUser(id, request));
    }

    @PatchMapping("/{id}/roles")
    public ResponseEntity<UserDetailResponse> assignRoles(
        @PathVariable Long id,
        @Valid @RequestBody AssignRolesRequest request
    ) {
        return ResponseEntity.ok(userManagementService.assignRoles(id, request));
    }

    @PostMapping("/{id}/unlock")
    public ResponseEntity<UserDetailResponse> unlock(@PathVariable Long id) {
        return ResponseEntity.ok(userManagementService.unlockUser(id));
    }
}
