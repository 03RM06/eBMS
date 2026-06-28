package gov.brgy.ebms.desktop.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import gov.brgy.ebms.desktop.core.Config;
import gov.brgy.ebms.desktop.core.Session;

import javax.net.ssl.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class ApiClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    // Token refresh lock — prevents multiple threads refreshing at the same time
    private final ReentrantLock refreshLock = new ReentrantLock();
    private volatile int tokenVersion = 0;

    public ApiClient() {
        this.baseUrl = Config.BASE_URL;
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(TIMEOUT);
        if (Config.TRUST_ALL_CERTS) {
            builder.sslContext(buildDevSslContext());
        }
        this.httpClient = builder.build();
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // ---- SSL (dev only) -------------------------------------------------

    /** Builds a trust-all SSLContext for development self-signed certificates. */
    private static SSLContext buildDevSslContext() {
        try {
            TrustManager[] tm = new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }};
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tm, new SecureRandom());
            return ctx;
        } catch (Exception e) {
            throw new RuntimeException("Failed to build dev SSL context", e);
        }
    }

    // ---- Auth helpers ---------------------------------------------------

    /** Sends request with Authorization and Accept-Language headers, handles 401 refresh. */
    private HttpResponse<String> sendWithAuth(HttpRequest.Builder builder) throws Exception {
        HttpRequest req = builder
            .header("Authorization", "Bearer " + Session.get().getAccessToken())
            .header("Accept-Language", Session.get().getLocale())
            .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() == 401) {
            // Try single refresh attempt (thread-safe)
            int myVersion = tokenVersion;
            refreshLock.lock();
            try {
                if (tokenVersion == myVersion) {
                    // We're the first — actually do the refresh
                    doRefresh();
                    tokenVersion++;
                }
                // else another thread already refreshed — just retry with new token
            } finally {
                refreshLock.unlock();
            }
            // Retry once with new token — build a completely fresh request to avoid
            // duplicate headers (HttpRequest.Builder.header() appends, not replaces).
            HttpRequest retry = HttpRequest.newBuilder(req.uri())
                .method(req.method(), req.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()))
                .header("Authorization", "Bearer " + Session.get().getAccessToken())
                .header("Accept-Language", Session.get().getLocale())
                .header("Content-Type", "application/json")
                .timeout(req.timeout().orElse(Duration.ofSeconds(30)))
                .build();
            resp = httpClient.send(retry, HttpResponse.BodyHandlers.ofString());
        }

        if (resp.statusCode() == 401) {
            Session.get().clear();
            throw new ApiException(401, "UNAUTHORIZED", "Session expired. Please log in again.");
        }

        return resp;
    }

    private void doRefresh() throws Exception {
        String body = objectMapper.writeValueAsString(
            Map.of("refreshToken", Session.get().getRefreshToken()));
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/v1/auth/refresh"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(TIMEOUT)
            .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            Session.get().clear();
            throw new ApiException(401, "UNAUTHORIZED", "Refresh token expired.");
        }
        gov.brgy.ebms.desktop.api.dto.LoginResponse lr =
            objectMapper.readValue(resp.body(), gov.brgy.ebms.desktop.api.dto.LoginResponse.class);
        Session.get().update(lr.accessToken(), lr.refreshToken(),
            lr.userId(), lr.username(), lr.fullName(), lr.roles());
    }

    // ---- Error parsing --------------------------------------------------

    private ApiException parseError(int status, String body) {
        try {
            Map<String, Object> map = objectMapper.readValue(body, new TypeReference<>() {});
            String error = (String) map.get("error");
            String message = (String) map.get("message");

            if (map.containsKey("fieldErrors")) {
                @SuppressWarnings("unchecked")
                Map<String, String> fieldErrors = (Map<String, String>) map.get("fieldErrors");
                return new ApiException(status, "VALIDATION_FAILED",
                    message != null ? message : "Validation failed", fieldErrors);
            }
            if ("DUPLICATE_RESIDENT".equals(error)) {
                @SuppressWarnings("unchecked")
                List<?> candidates = (List<?>) map.get("duplicateCandidates");
                return new ApiException(status, error,
                    message != null ? message : "Duplicate resident detected", candidates);
            }
            return new ApiException(status,
                error != null ? error : "ERROR",
                message != null ? message : "HTTP " + status);
        } catch (Exception e) {
            return new ApiException(status, "ERROR", "HTTP " + status + ": " + body);
        }
    }

    private void checkSuccess(HttpResponse<String> resp) {
        int sc = resp.statusCode();
        if (sc < 200 || sc >= 300) {
            throw parseError(sc, resp.body());
        }
    }

    // ---- Typed helpers --------------------------------------------------

    private <T> T get(String path, TypeReference<T> type) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .GET().timeout(TIMEOUT);
        HttpResponse<String> resp = sendWithAuth(b);
        checkSuccess(resp);
        if (type == null) return null;
        String body = resp.body();
        if (body == null || body.isBlank()) return null;
        return objectMapper.readValue(body, type);
    }

    private <T> T post(String path, Object body, TypeReference<T> type) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest.Builder b = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json)).timeout(TIMEOUT);
        HttpResponse<String> resp = sendWithAuth(b);
        checkSuccess(resp);
        if (type == null) return null;
        String respBody = resp.body();
        if (respBody == null || respBody.isBlank()) return null;
        return objectMapper.readValue(respBody, type);
    }

    private <T> T put(String path, Object body, TypeReference<T> type) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest.Builder b = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(json)).timeout(TIMEOUT);
        HttpResponse<String> resp = sendWithAuth(b);
        checkSuccess(resp);
        if (type == null) return null;
        String respBody = resp.body();
        if (respBody == null || respBody.isBlank()) return null;
        return objectMapper.readValue(respBody, type);
    }

    private <T> T patch(String path, Object body, TypeReference<T> type) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest.Builder b = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .method("PATCH", HttpRequest.BodyPublishers.ofString(json)).timeout(TIMEOUT);
        HttpResponse<String> resp = sendWithAuth(b);
        checkSuccess(resp);
        if (type == null) return null;
        String respBody = resp.body();
        if (respBody == null || respBody.isBlank()) return null;
        return objectMapper.readValue(respBody, type);
    }

    private void delete(String path) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .DELETE().timeout(TIMEOUT);
        HttpResponse<String> resp = sendWithAuth(b);
        checkSuccess(resp);
    }

    // ---- Auth endpoints -------------------------------------------------

    public gov.brgy.ebms.desktop.api.dto.LoginResponse login(String username, String password)
        throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "username", username, "password", password));
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/v1/auth/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).timeout(TIMEOUT)
            .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw parseError(resp.statusCode(), resp.body());
        }
        return objectMapper.readValue(resp.body(),
            gov.brgy.ebms.desktop.api.dto.LoginResponse.class);
    }

    public void logout() throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/v1/auth/logout"))
            .POST(HttpRequest.BodyPublishers.noBody()).timeout(TIMEOUT);
        HttpResponse<String> resp = sendWithAuth(b);
        // 204 expected; ignore body
    }

    public gov.brgy.ebms.desktop.api.dto.UserResponse getMe() throws Exception {
        return get("/api/v1/auth/me",
            new TypeReference<gov.brgy.ebms.desktop.api.dto.UserResponse>() {});
    }

    public void updateLocale(String locale) throws Exception {
        put("/api/v1/auth/me/locale",
            Map.of("locale", locale), null);
    }

    public void changePassword(gov.brgy.ebms.desktop.api.dto.ChangePasswordRequest req)
        throws Exception {
        patch("/api/v1/auth/change-password", req, null);
    }

    // ---- Residents ------------------------------------------------------

    public Page<gov.brgy.ebms.desktop.api.dto.ResidentDto> listResidents(
        String q, int page, int size) throws Exception {
        String path = "/api/v1/residents?page=" + page + "&size=" + size
            + (q != null && !q.isBlank() ? "&q=" + java.net.URLEncoder.encode(q, "UTF-8") : "");
        return get(path, new TypeReference<Page<gov.brgy.ebms.desktop.api.dto.ResidentDto>>() {});
    }

    public gov.brgy.ebms.desktop.api.dto.ResidentDto getResident(Long id) throws Exception {
        return get("/api/v1/residents/" + id,
            new TypeReference<gov.brgy.ebms.desktop.api.dto.ResidentDto>() {});
    }

    public gov.brgy.ebms.desktop.api.dto.ResidentDto createResident(
        gov.brgy.ebms.desktop.api.dto.ResidentRequest req) throws Exception {
        return post("/api/v1/residents", req,
            new TypeReference<gov.brgy.ebms.desktop.api.dto.ResidentDto>() {});
    }

    public gov.brgy.ebms.desktop.api.dto.ResidentDto updateResident(
        Long id, gov.brgy.ebms.desktop.api.dto.ResidentRequest req) throws Exception {
        return put("/api/v1/residents/" + id, req,
            new TypeReference<gov.brgy.ebms.desktop.api.dto.ResidentDto>() {});
    }

    public void deleteResident(Long id) throws Exception {
        delete("/api/v1/residents/" + id);
    }

    public gov.brgy.ebms.desktop.api.dto.ResidentDto restoreResident(Long id) throws Exception {
        return post("/api/v1/residents/" + id + "/restore", null,
            new TypeReference<gov.brgy.ebms.desktop.api.dto.ResidentDto>() {});
    }

    // ---- Households -----------------------------------------------------

    public Page<gov.brgy.ebms.desktop.api.dto.HouseholdDto> listHouseholds(
        int page, int size) throws Exception {
        return get("/api/v1/households?page=" + page + "&size=" + size,
            new TypeReference<Page<gov.brgy.ebms.desktop.api.dto.HouseholdDto>>() {});
    }

    public gov.brgy.ebms.desktop.api.dto.HouseholdDto getHousehold(Long id) throws Exception {
        return get("/api/v1/households/" + id,
            new TypeReference<gov.brgy.ebms.desktop.api.dto.HouseholdDto>() {});
    }

    public gov.brgy.ebms.desktop.api.dto.HouseholdDto createHousehold(
        gov.brgy.ebms.desktop.api.dto.HouseholdRequest req) throws Exception {
        return post("/api/v1/households", req,
            new TypeReference<gov.brgy.ebms.desktop.api.dto.HouseholdDto>() {});
    }

    public gov.brgy.ebms.desktop.api.dto.HouseholdDto updateHousehold(
        Long id, gov.brgy.ebms.desktop.api.dto.HouseholdRequest req) throws Exception {
        return put("/api/v1/households/" + id, req,
            new TypeReference<gov.brgy.ebms.desktop.api.dto.HouseholdDto>() {});
    }

    public void deleteHousehold(Long id) throws Exception {
        delete("/api/v1/households/" + id);
    }

    public void addHouseholdMember(Long householdId,
        gov.brgy.ebms.desktop.api.dto.AddMemberRequest req) throws Exception {
        post("/api/v1/households/" + householdId + "/members", req, null);
    }

    public void removeHouseholdMember(Long householdId, Long residentId) throws Exception {
        delete("/api/v1/households/" + householdId + "/members/" + residentId);
    }

    public gov.brgy.ebms.desktop.api.dto.HouseholdDto setHouseholdHead(
        Long householdId, gov.brgy.ebms.desktop.api.dto.SetHeadRequest req) throws Exception {
        return put("/api/v1/households/" + householdId + "/head", req,
            new TypeReference<gov.brgy.ebms.desktop.api.dto.HouseholdDto>() {});
    }

    // ---- Clearances -----------------------------------------------------

    public Page<gov.brgy.ebms.desktop.api.dto.ClearanceDto> listClearances(
        String status, int page, int size) throws Exception {
        String path = "/api/v1/clearances?page=" + page + "&size=" + size
            + (status != null && !status.isBlank() ? "&status=" + status : "");
        return get(path, new TypeReference<Page<gov.brgy.ebms.desktop.api.dto.ClearanceDto>>() {});
    }

    public gov.brgy.ebms.desktop.api.dto.ClearanceDto getClearance(Long id) throws Exception {
        return get("/api/v1/clearances/" + id,
            new TypeReference<gov.brgy.ebms.desktop.api.dto.ClearanceDto>() {});
    }

    public gov.brgy.ebms.desktop.api.dto.ClearanceDto submitClearance(
        gov.brgy.ebms.desktop.api.dto.ClearanceRequestDto req) throws Exception {
        return post("/api/v1/clearances", req,
            new TypeReference<gov.brgy.ebms.desktop.api.dto.ClearanceDto>() {});
    }

    public gov.brgy.ebms.desktop.api.dto.ClearanceDto startClearanceReview(Long id)
        throws Exception {
        return post("/api/v1/clearances/" + id + "/review", null,
            new TypeReference<gov.brgy.ebms.desktop.api.dto.ClearanceDto>() {});
    }

    public gov.brgy.ebms.desktop.api.dto.ClearanceDto approveClearance(Long id)
        throws Exception {
        return post("/api/v1/clearances/" + id + "/approve", null,
            new TypeReference<gov.brgy.ebms.desktop.api.dto.ClearanceDto>() {});
    }

    public gov.brgy.ebms.desktop.api.dto.ClearanceDto rejectClearance(Long id, String remarks)
        throws Exception {
        String path = "/api/v1/clearances/" + id + "/reject"
            + (remarks != null && !remarks.isBlank()
                ? "?remarks=" + java.net.URLEncoder.encode(remarks, "UTF-8") : "");
        return post(path, null,
            new TypeReference<gov.brgy.ebms.desktop.api.dto.ClearanceDto>() {});
    }

    public byte[] downloadDocument(Long clearanceId) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/v1/clearances/" + clearanceId + "/document"))
            .GET().timeout(TIMEOUT);
        HttpRequest req = b
            .header("Authorization", "Bearer " + Session.get().getAccessToken())
            .header("Accept-Language", Session.get().getLocale())
            .build();
        HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new ApiException(resp.statusCode(), "ERROR",
                "Failed to download document: HTTP " + resp.statusCode());
        }
        return resp.body();
    }

    // ---- Complaints -----------------------------------------------------

    public Page<gov.brgy.ebms.desktop.api.dto.ComplaintDto> listComplaints(
        String status, int page, int size) throws Exception {
        String path = "/api/v1/complaints?page=" + page + "&size=" + size
            + (status != null && !status.isBlank() ? "&status=" + status : "");
        return get(path,
            new TypeReference<Page<gov.brgy.ebms.desktop.api.dto.ComplaintDto>>() {});
    }

    public gov.brgy.ebms.desktop.api.dto.ComplaintDto getComplaint(Long id) throws Exception {
        return get("/api/v1/complaints/" + id,
            new TypeReference<gov.brgy.ebms.desktop.api.dto.ComplaintDto>() {});
    }

    public gov.brgy.ebms.desktop.api.dto.ComplaintDto fileComplaint(
        gov.brgy.ebms.desktop.api.dto.ComplaintFilingRequest req) throws Exception {
        return post("/api/v1/complaints", req,
            new TypeReference<gov.brgy.ebms.desktop.api.dto.ComplaintDto>() {});
    }

    public gov.brgy.ebms.desktop.api.dto.ComplaintDto transitionComplaint(
        Long id, String newStatus, String note) throws Exception {
        String path = "/api/v1/complaints/" + id + "/transition?newStatus=" + newStatus
            + (note != null && !note.isBlank()
                ? "&note=" + java.net.URLEncoder.encode(note, "UTF-8") : "");
        return post(path, null,
            new TypeReference<gov.brgy.ebms.desktop.api.dto.ComplaintDto>() {});
    }

    public void deleteComplaint(Long id) throws Exception {
        delete("/api/v1/complaints/" + id);
    }

    public java.util.List<gov.brgy.ebms.desktop.api.dto.ComplaintDto> getUnresolvedComplaints(
        Long residentId) throws Exception {
        return get("/api/v1/complaints/unresolved?residentId=" + residentId,
            new TypeReference<java.util.List<gov.brgy.ebms.desktop.api.dto.ComplaintDto>>() {});
    }

    // ---- Fees -----------------------------------------------------------

    public java.util.List<gov.brgy.ebms.desktop.api.dto.FeeDto> listUnpaidFees()
        throws Exception {
        return get("/api/v1/fees/unpaid",
            new TypeReference<java.util.List<gov.brgy.ebms.desktop.api.dto.FeeDto>>() {});
    }

    public gov.brgy.ebms.desktop.api.dto.FeeDto getFee(Long id) throws Exception {
        return get("/api/v1/fees/" + id,
            new TypeReference<gov.brgy.ebms.desktop.api.dto.FeeDto>() {});
    }

    public gov.brgy.ebms.desktop.api.dto.FeeDto createFee(
        gov.brgy.ebms.desktop.api.dto.FeeRequest req) throws Exception {
        return post("/api/v1/fees", req,
            new TypeReference<gov.brgy.ebms.desktop.api.dto.FeeDto>() {});
    }

    public gov.brgy.ebms.desktop.api.dto.FeeDto payFee(Long id) throws Exception {
        return post("/api/v1/fees/" + id + "/pay", null,
            new TypeReference<gov.brgy.ebms.desktop.api.dto.FeeDto>() {});
    }

    public gov.brgy.ebms.desktop.api.dto.FeeDto waiveFee(Long id) throws Exception {
        return post("/api/v1/fees/" + id + "/waive", null,
            new TypeReference<gov.brgy.ebms.desktop.api.dto.FeeDto>() {});
    }

    // ---- Audit ----------------------------------------------------------

    public Page<gov.brgy.ebms.desktop.api.dto.AuditLogDto> searchAudit(
        String entityType, Long entityId, Long actorId,
        String from, String to, int page, int size) throws Exception {
        StringBuilder path = new StringBuilder("/api/v1/audit?page=")
            .append(page).append("&size=").append(size);
        if (entityType != null && !entityType.isBlank()) path.append("&entityType=").append(entityType);
        if (entityId != null) path.append("&entityId=").append(entityId);
        if (actorId != null) path.append("&actorId=").append(actorId);
        if (from != null && !from.isBlank()) path.append("&from=").append(from);
        if (to != null && !to.isBlank()) path.append("&to=").append(to);
        return get(path.toString(),
            new TypeReference<Page<gov.brgy.ebms.desktop.api.dto.AuditLogDto>>() {});
    }

    public gov.brgy.ebms.desktop.api.dto.HashChainVerificationResult verifyAudit()
        throws Exception {
        return get("/api/v1/audit/verify",
            new TypeReference<gov.brgy.ebms.desktop.api.dto.HashChainVerificationResult>() {});
    }

    // ---- Config ---------------------------------------------------------

    public java.util.Map<String, String> getPublicConfig() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/v1/config/public"))
            .GET().timeout(TIMEOUT).build();
        HttpResponse<String> resp = httpClient.send(req,
            HttpResponse.BodyHandlers.ofString());
        checkSuccess(resp);
        return objectMapper.readValue(resp.body(), new TypeReference<>() {});
    }
}
