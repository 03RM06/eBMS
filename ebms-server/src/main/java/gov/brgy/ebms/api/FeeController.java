package gov.brgy.ebms.api;

import gov.brgy.ebms.fee.dto.FeeRequest;
import gov.brgy.ebms.fee.dto.FeeResponse;
import gov.brgy.ebms.fee.service.FeeService;
import gov.brgy.ebms.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fees")
public class FeeController {

    private final FeeService feeService;

    public FeeController(FeeService feeService) {
        this.feeService = feeService;
    }

    @GetMapping("/unpaid")
    public ResponseEntity<List<FeeResponse>> listUnpaid() {
        return ResponseEntity.ok(feeService.listUnpaid());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FeeResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(feeService.findById(id));
    }

    @PostMapping
    public ResponseEntity<FeeResponse> create(@Valid @RequestBody FeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(feeService.create(request, SecurityUtils.getAuthenticatedUserId()));
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<FeeResponse> pay(@PathVariable Long id) {
        return ResponseEntity.ok(feeService.markPaid(id, SecurityUtils.getAuthenticatedUserId()));
    }

    @PostMapping("/{id}/waive")
    public ResponseEntity<FeeResponse> waive(@PathVariable Long id) {
        return ResponseEntity.ok(feeService.waive(id, SecurityUtils.getAuthenticatedUserId()));
    }
}
