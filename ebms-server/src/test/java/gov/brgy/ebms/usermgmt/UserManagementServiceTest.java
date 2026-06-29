package gov.brgy.ebms.usermgmt;

import gov.brgy.ebms.security.entity.Role;
import gov.brgy.ebms.security.entity.User;
import gov.brgy.ebms.security.repository.RoleRepository;
import gov.brgy.ebms.security.repository.UserRepository;
import gov.brgy.ebms.usermgmt.dto.AssignRolesRequest;
import gov.brgy.ebms.usermgmt.dto.CreateUserRequest;
import gov.brgy.ebms.usermgmt.dto.UpdateUserRequest;
import gov.brgy.ebms.usermgmt.dto.UserDetailResponse;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks UserManagementService userManagementService;

    @Test
    void createUser_setsEncodedPasswordAndForcedChange() {
        CreateUserRequest req = new CreateUserRequest(
            "jdelacruz", "j@test.com", "Juan Dela Cruz", "Password123", List.of(1L));
        Role staffRole = buildRole(1L, "STAFF");
        when(roleRepository.findAllById(List.of(1L))).thenReturn(List.of(staffRole));
        when(passwordEncoder.encode("Password123")).thenReturn("$2a$hashed");

        User saved = buildUser(1L, "jdelacruz");
        saved.setForcedPasswordChange(true);
        saved.setPasswordHash("$2a$hashed");
        saved.setRoles(new HashSet<>(Set.of(staffRole)));
        when(userRepository.save(any())).thenReturn(saved);

        UserDetailResponse result = userManagementService.createUser(req);

        assertThat(result.username()).isEqualTo("jdelacruz");
        assertThat(result.forcedPasswordChange()).isTrue();
        assertThat(result.roles()).contains("STAFF");
        verify(passwordEncoder).encode("Password123");
    }

    @Test
    void updateUser_disablingAccount_updatesEnabledFlag() {
        User existing = buildUser(1L, "jdelacruz");
        existing.setEnabled(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserDetailResponse result = userManagementService.updateUser(1L, new UpdateUserRequest(null, null, false));

        assertThat(result.enabled()).isFalse();
    }

    @Test
    void assignRoles_replacesExistingRoles() {
        User existing = buildUser(1L, "jdelacruz");
        existing.setRoles(new HashSet<>(Set.of(buildRole(1L, "STAFF"))));
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

        Role secretary = buildRole(2L, "SECRETARY");
        when(roleRepository.findAllById(List.of(2L))).thenReturn(List.of(secretary));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserDetailResponse result = userManagementService.assignRoles(1L, new AssignRolesRequest(List.of(2L)));

        assertThat(result.roles()).containsExactly("SECRETARY");
        assertThat(result.roles()).doesNotContain("STAFF");
    }

    @Test
    void unlockUser_clearsLockoutFields() {
        User existing = buildUser(1L, "jdelacruz");
        existing.setFailedLoginAttempts(5);
        existing.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserDetailResponse result = userManagementService.unlockUser(1L);

        assertThat(result.failedLoginAttempts()).isZero();
        assertThat(result.lockedUntil()).isNull();
    }

    @Test
    void getUser_notFound_throwsEntityNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userManagementService.getUser(99L))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("99");
    }

    private User buildUser(Long id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setFullName("Test User");
        u.setPasswordHash("$2a$hashed");
        u.setEnabled(true);
        u.setRoles(new HashSet<>());
        return u;
    }

    private Role buildRole(Long id, String code) {
        Role r = new Role();
        r.setId(id);
        r.setCode(code);
        r.setNameEn(code);
        r.setNameFil(code);
        return r;
    }
}
