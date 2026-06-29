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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

@Service
public class UserManagementService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserManagementService(
        UserRepository userRepository,
        RoleRepository roleRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN')")
    @Transactional(readOnly = true)
    public Page<UserDetailResponse> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserDetailResponse::from);
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN')")
    @Transactional
    public UserDetailResponse createUser(CreateUserRequest request) {
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setForcedPasswordChange(true);

        List<Role> roles = roleRepository.findAllById(request.roleIds());
        user.setRoles(new HashSet<>(roles));

        return UserDetailResponse.from(userRepository.save(user));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN')")
    @Transactional(readOnly = true)
    public UserDetailResponse getUser(Long id) {
        return UserDetailResponse.from(findById(id));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN')")
    @Transactional
    public UserDetailResponse updateUser(Long id, UpdateUserRequest request) {
        User user = findById(id);
        if (request.fullName() != null) user.setFullName(request.fullName());
        if (request.email() != null) user.setEmail(request.email());
        if (request.enabled() != null) user.setEnabled(request.enabled());
        return UserDetailResponse.from(userRepository.save(user));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN')")
    @Transactional
    public UserDetailResponse assignRoles(Long id, AssignRolesRequest request) {
        User user = findById(id);
        List<Role> roles = roleRepository.findAllById(request.roleIds());
        user.setRoles(new HashSet<>(roles));
        return UserDetailResponse.from(userRepository.save(user));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN')")
    @Transactional
    public UserDetailResponse unlockUser(Long id) {
        User user = findById(id);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        return UserDetailResponse.from(userRepository.save(user));
    }

    private User findById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
    }
}
