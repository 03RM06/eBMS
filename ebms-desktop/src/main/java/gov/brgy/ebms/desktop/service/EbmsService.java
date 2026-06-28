package gov.brgy.ebms.desktop.service;

import gov.brgy.ebms.desktop.api.ApiClient;
import gov.brgy.ebms.desktop.api.Page;
import gov.brgy.ebms.desktop.api.dto.*;

import java.util.List;
import java.util.Map;

/**
 * Typed facade over ApiClient — one method per endpoint, returns Java DTOs.
 * All methods throw RuntimeException (wrapping ApiException) on failure.
 */
public class EbmsService {

    private static final EbmsService INSTANCE = new EbmsService();
    private final ApiClient client = new ApiClient();

    private EbmsService() {}

    public static EbmsService get() { return INSTANCE; }

    // ---- Auth -----------------------------------------------------------

    public LoginResponse login(String username, String password) {
        try { return client.login(username, password); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public void logout() {
        try { client.logout(); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public UserResponse getMe() {
        try { return client.getMe(); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public void updateLocale(String locale) {
        try { client.updateLocale(locale); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public void changePassword(ChangePasswordRequest req) {
        try { client.changePassword(req); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    // ---- Residents ------------------------------------------------------

    public Page<ResidentDto> listResidents(String q, int page, int size) {
        try { return client.listResidents(q, page, size); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public ResidentDto getResident(Long id) {
        try { return client.getResident(id); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public ResidentDto createResident(ResidentRequest req) {
        try { return client.createResident(req); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public ResidentDto updateResident(Long id, ResidentRequest req) {
        try { return client.updateResident(id, req); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public void deleteResident(Long id) {
        try { client.deleteResident(id); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public ResidentDto restoreResident(Long id) {
        try { return client.restoreResident(id); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    // ---- Households -----------------------------------------------------

    public Page<HouseholdDto> listHouseholds(int page, int size) {
        try { return client.listHouseholds(page, size); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public HouseholdDto getHousehold(Long id) {
        try { return client.getHousehold(id); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public HouseholdDto createHousehold(HouseholdRequest req) {
        try { return client.createHousehold(req); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public HouseholdDto updateHousehold(Long id, HouseholdRequest req) {
        try { return client.updateHousehold(id, req); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public void deleteHousehold(Long id) {
        try { client.deleteHousehold(id); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public void addHouseholdMember(Long householdId, AddMemberRequest req) {
        try { client.addHouseholdMember(householdId, req); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public void removeHouseholdMember(Long householdId, Long residentId) {
        try { client.removeHouseholdMember(householdId, residentId); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public HouseholdDto setHouseholdHead(Long householdId, SetHeadRequest req) {
        try { return client.setHouseholdHead(householdId, req); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    // ---- Clearances -----------------------------------------------------

    public Page<ClearanceDto> listClearances(String status, int page, int size) {
        try { return client.listClearances(status, page, size); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public ClearanceDto getClearance(Long id) {
        try { return client.getClearance(id); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public ClearanceDto submitClearance(ClearanceRequestDto req) {
        try { return client.submitClearance(req); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public ClearanceDto startClearanceReview(Long id) {
        try { return client.startClearanceReview(id); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public ClearanceDto approveClearance(Long id) {
        try { return client.approveClearance(id); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public ClearanceDto rejectClearance(Long id, String remarks) {
        try { return client.rejectClearance(id, remarks); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public byte[] downloadDocument(Long clearanceId) {
        try { return client.downloadDocument(clearanceId); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    // ---- Complaints -----------------------------------------------------

    public Page<ComplaintDto> listComplaints(String status, int page, int size) {
        try { return client.listComplaints(status, page, size); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public ComplaintDto getComplaint(Long id) {
        try { return client.getComplaint(id); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public ComplaintDto fileComplaint(ComplaintFilingRequest req) {
        try { return client.fileComplaint(req); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public ComplaintDto transitionComplaint(Long id, String newStatus, String note) {
        try { return client.transitionComplaint(id, newStatus, note); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public void deleteComplaint(Long id) {
        try { client.deleteComplaint(id); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public List<ComplaintDto> getUnresolvedComplaints(Long residentId) {
        try { return client.getUnresolvedComplaints(residentId); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    // ---- Fees -----------------------------------------------------------

    public List<FeeDto> listUnpaidFees() {
        try { return client.listUnpaidFees(); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public FeeDto getFee(Long id) {
        try { return client.getFee(id); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public FeeDto createFee(FeeRequest req) {
        try { return client.createFee(req); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public FeeDto payFee(Long id) {
        try { return client.payFee(id); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public FeeDto waiveFee(Long id) {
        try { return client.waiveFee(id); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    // ---- Audit ----------------------------------------------------------

    public Page<AuditLogDto> searchAudit(String entityType, Long entityId, Long actorId,
                                          String from, String to, int page, int size) {
        try { return client.searchAudit(entityType, entityId, actorId, from, to, page, size); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public HashChainVerificationResult verifyAudit() {
        try { return client.verifyAudit(); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    // ---- Config ---------------------------------------------------------

    public Map<String, String> getPublicConfig() {
        try { return client.getPublicConfig(); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e); }
    }
}
