package gov.brgy.ebms.security.service;

import gov.brgy.ebms.audit.aspect.Auditable;
import gov.brgy.ebms.security.dto.CreateUserRequest;
import gov.brgy.ebms.security.dto.UserResponse;
import gov.brgy.ebms.security.entity.Role;
import gov.brgy.ebms.security.entity.User;
import gov.brgy.ebms.security.repository.RoleRepository;
import gov.brgy.ebms.security.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'BARANGAY_CAPTAIN')")
    @Transactional(readOnly = true)
    public List<UserResponse> listAll() {
        return userRepository.findAll().stream()
            .map(UserResponse::from)
            .toList();
    }

    @Auditable(entityType = "USER", action = "CREATE")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'BARANGAY_CAPTAIN')")
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Username already taken: " + request.username());
        }
        if (request.email() != null && userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already in use: " + request.email());
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());

        Set<Role> roles = new HashSet<>();
        for (String roleCode : request.roles()) {
            Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + roleCode));
            roles.add(role);
        }
        user.setRoles(roles);

        return UserResponse.from(userRepository.save(user));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional
    public void deleteUser(Long id, Long deletedBy) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        user.softDelete(deletedBy);
        userRepository.save(user);
    }
}
