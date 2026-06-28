package gov.brgy.ebms.desktop.controller;

import gov.brgy.ebms.desktop.api.Page;
import gov.brgy.ebms.desktop.api.dto.AuditLogDto;
import gov.brgy.ebms.desktop.api.dto.HashChainVerificationResult;
import gov.brgy.ebms.desktop.controller.component.PaginationBar;
import gov.brgy.ebms.desktop.core.AsyncRunner;
import gov.brgy.ebms.desktop.core.Dialogs;
import gov.brgy.ebms.desktop.core.Session;
import gov.brgy.ebms.desktop.service.EbmsService;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class AuditLogController {

    @FXML private ComboBox<String> entityTypeFilter;
    @FXML private TextField entityIdField;
    @FXML private TextField actorIdField;
    @FXML private DatePicker fromDate;
    @FXML private DatePicker toDate;
    @FXML private Button verifyButton;
    @FXML private TableView<AuditLogDto> table;
    @FXML private TableColumn<AuditLogDto, Long> colId;
    @FXML private TableColumn<AuditLogDto, String> colEntityType;
    @FXML private TableColumn<AuditLogDto, Long> colEntityId;
    @FXML private TableColumn<AuditLogDto, String> colAction;
    @FXML private TableColumn<AuditLogDto, String> colActor;
    @FXML private TableColumn<AuditLogDto, String> colCreatedAt;
    @FXML private VBox paginationContainer;

    private final EbmsService service = EbmsService.get();
    private final PaginationBar pagination = new PaginationBar();
    private int currentPage = 0;
    private static final int PAGE_SIZE = 30;
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().id()));
        colEntityType.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().entityType()));
        colEntityId.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().entityId()));
        colAction.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().action()));
        colActor.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().actorUsername()));
        colCreatedAt.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().createdAt() != null ? c.getValue().createdAt().toString() : ""));

        if (entityTypeFilter != null) {
            entityTypeFilter.getItems().setAll("", "RESIDENT", "HOUSEHOLD", "CLEARANCE",
                "COMPLAINT", "FEE", "USER", "AUTH");
            entityTypeFilter.setValue("");
        }

        // Show verify button only for SUPER_ADMIN
        if (verifyButton != null) {
            verifyButton.setVisible(Session.get().hasAnyRole("SUPER_ADMIN"));
            verifyButton.setManaged(verifyButton.isVisible());
        }

        pagination.setOnPageChange(p -> load(p));
        if (paginationContainer != null) paginationContainer.getChildren().add(pagination);

        load(0);
    }

    @FXML
    public void onSearch() { load(0); }

    @FXML
    public void onVerify() {
        AsyncRunner.run(
            () -> service.verifyAudit(),
            this::showVerifyResult,
            Dialogs::handleApiError
        );
    }

    private void showVerifyResult(HashChainVerificationResult r) {
        String msg;
        if (r.valid()) {
            msg = "Hash chain is VALID.\nTotal rows verified: " + r.totalRows();
        } else {
            msg = "Hash chain BROKEN at row ID: " + r.brokenAtId()
                + "\nTotal rows checked: " + r.totalRows();
        }
        Dialogs.info(msg);
    }

    private void load(int page) {
        currentPage = page;
        String entityType = entityTypeFilter != null ? entityTypeFilter.getValue() : null;
        if (entityType != null && entityType.isBlank()) entityType = null;

        Long entityId = parseNullableLong(entityIdField);
        Long actorId = parseNullableLong(actorIdField);
        String from = fromDate != null && fromDate.getValue() != null
            ? fromDate.getValue().atStartOfDay().format(ISO) : null;
        String to = toDate != null && toDate.getValue() != null
            ? toDate.getValue().atTime(23, 59, 59).format(ISO) : null;

        final String et = entityType, f = from, t = to;
        AsyncRunner.run(
            () -> service.searchAudit(et, entityId, actorId, f, t, page, PAGE_SIZE),
            this::populate,
            Dialogs::handleApiError
        );
    }

    private void populate(Page<AuditLogDto> p) {
        table.getItems().setAll(p.content());
        pagination.update(p.number(), Math.max(p.totalPages(), 1));
    }

    private Long parseNullableLong(TextField field) {
        if (field == null || field.getText().isBlank()) return null;
        try { return Long.parseLong(field.getText().trim()); }
        catch (NumberFormatException e) { return null; }
    }
}
