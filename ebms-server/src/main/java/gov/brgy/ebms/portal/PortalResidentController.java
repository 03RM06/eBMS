package gov.brgy.ebms.portal;

import gov.brgy.ebms.clearance.dto.ClearanceRequestDto;
import gov.brgy.ebms.clearance.service.ClearanceService;
import gov.brgy.ebms.resident.dto.ResidentResponse;
import gov.brgy.ebms.resident.service.ResidentService;
import gov.brgy.ebms.security.entity.User;
import gov.brgy.ebms.security.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/portal/resident")
public class PortalResidentController {

    private static final Logger log = LoggerFactory.getLogger(PortalResidentController.class);

    private final ResidentService residentService;
    private final ClearanceService clearanceService;
    private final UserRepository userRepository;

    public PortalResidentController(
        ResidentService residentService,
        ClearanceService clearanceService,
        UserRepository userRepository
    ) {
        this.residentService = residentService;
        this.clearanceService = clearanceService;
        this.userRepository = userRepository;
    }

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal UserDetails principal, Model model) {
        // FIX-A: use findById() with the caller's own residentId so that RESIDENT role
        // is allowed (search() excludes RESIDENT in its @PreAuthorize).
        Long residentId = resolveResidentId(principal.getUsername());
        if (residentId != null) {
            try {
                model.addAttribute("resident", residentService.findById(residentId));
            } catch (Exception e) {
                log.warn("Could not load resident profile for {}: {}", principal.getUsername(), e.getMessage());
            }
        }
        return "portal/resident-profile";
    }

    @GetMapping("/clearances")
    public String myClearances(
        @AuthenticationPrincipal UserDetails principal,
        Model model,
        Pageable pageable
    ) {
        Long residentId = resolveResidentId(principal.getUsername());
        if (residentId != null) {
            model.addAttribute("clearances", clearanceService.listByResident(residentId, pageable));
        } else {
            model.addAttribute("clearances", Page.empty());
        }
        return "portal/clearances";
    }

    @PostMapping("/clearances")
    public String submitClearance(
        @RequestParam Long residentId,
        @RequestParam String purpose,
        @AuthenticationPrincipal UserDetails principal
    ) {
        // FIX-3 / IDOR: verify that the authenticated user owns the residentId being submitted
        Long authenticatedResidentId = resolveResidentId(principal.getUsername());
        if (authenticatedResidentId == null || !authenticatedResidentId.equals(residentId)) {
            throw new AccessDeniedException("You may only submit clearances for your own resident record");
        }
        clearanceService.submit(new ClearanceRequestDto(residentId, purpose), null);
        return "redirect:/portal/resident/clearances?submitted=true";
    }

    /**
     * Resolves the residentId linked to the given username from the users table.
     * Returns null if no user is found or no residentId is linked.
     */
    private Long resolveResidentId(String username) {
        return userRepository.findByUsername(username)
            .map(User::getResidentId)
            .orElse(null);
    }
}
