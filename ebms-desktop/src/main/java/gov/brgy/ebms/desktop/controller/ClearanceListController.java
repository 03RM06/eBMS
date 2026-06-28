package gov.brgy.ebms.desktop.controller;

import gov.brgy.ebms.desktop.api.Page;
import gov.brgy.ebms.desktop.api.dto.ClearanceDto;
import gov.brgy.ebms.desktop.controller.component.PaginationBar;
import gov.brgy.ebms.desktop.core.AsyncRunner;
import gov.brgy.ebms.desktop.core.Dialogs;
import gov.brgy.ebms.desktop.service.EbmsService;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;

public class ClearanceListController {

    @FXML private ComboBox<String> statusFilter;
    @FXML private TableView<ClearanceDto> table;
    @FXML private TableColumn<ClearanceDto, Long> colId;
    @FXML private TableColumn<ClearanceDto, String> colControl;
    @FXML private TableColumn<ClearanceDto, Long> colResidentId;
    @FXML private TableColumn<ClearanceDto, String> colPurpose;
    @FXML private TableColumn<ClearanceDto, String> colStatus;
    @FXML private TableColumn<ClearanceDto, String> colCreatedAt;
    @FXML private VBox paginationContainer;

    private final EbmsService service = EbmsService.get();
    private final PaginationBar pagination = new PaginationBar();
    private int currentPage = 0;
    private static final int PAGE_SIZE = 20;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().id()));
        colControl.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().controlNumber()));
        colResidentId.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().residentId()));
        colPurpose.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().purpose()));
        colStatus.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().status()));
        colCreatedAt.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().createdAt() != null ? c.getValue().createdAt().toString() : ""));

        if (statusFilter != null) {
            statusFilter.getItems().setAll("", "PENDING", "UNDER_REVIEW", "APPROVED", "REJECTED");
            statusFilter.setValue("");
        }

        pagination.setOnPageChange(p -> load(p));
        if (paginationContainer != null) paginationContainer.getChildren().add(pagination);

        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                openForm(table.getSelectionModel().getSelectedItem());
            }
        });

        load(0);
    }

    @FXML
    public void onFilter() { load(0); }

    @FXML
    public void onCreate() { openForm(null); }

    @FXML
    public void onReview() { actOnSelected(c -> service.startClearanceReview(c.id())); }

    @FXML
    public void onApprove() { actOnSelected(c -> service.approveClearance(c.id())); }

    @FXML
    public void onReject() {
        ClearanceDto selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { Dialogs.error("Select a clearance."); return; }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Reject Clearance");
        dialog.setHeaderText("Enter rejection remarks (optional):");
        dialog.showAndWait().ifPresent(remarks ->
            AsyncRunner.run(
                () -> service.rejectClearance(selected.id(), remarks),
                r -> load(currentPage),
                Dialogs::handleApiError
            )
        );
    }

    @FXML
    public void onDownloadPdf() {
        ClearanceDto selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { Dialogs.error("Select a clearance."); return; }
        downloadPdf(selected);
    }

    private void actOnSelected(java.util.function.Function<ClearanceDto, ClearanceDto> action) {
        ClearanceDto selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { Dialogs.error("Select a clearance."); return; }
        AsyncRunner.run(
            () -> action.apply(selected),
            r -> load(currentPage),
            Dialogs::handleApiError
        );
    }

    private void downloadPdf(ClearanceDto c) {
        AsyncRunner.run(
            () -> service.downloadDocument(c.id()),
            bytes -> {
                try {
                    Path tmp = Files.createTempFile("clearance-" + c.id() + "-", ".pdf");
                    Files.write(tmp, bytes);
                    tmp.toFile().deleteOnExit();
                    java.awt.Desktop.getDesktop().open(tmp.toFile());
                } catch (Exception e) {
                    Dialogs.error("Cannot open PDF: " + e.getMessage());
                }
            },
            Dialogs::handleApiError
        );
    }

    private void load(int page) {
        currentPage = page;
        String status = statusFilter != null ? statusFilter.getValue() : null;
        if (status != null && status.isBlank()) status = null;
        final String s = status;
        AsyncRunner.run(
            () -> service.listClearances(s, page, PAGE_SIZE),
            this::populate,
            Dialogs::handleApiError
        );
    }

    private void populate(Page<ClearanceDto> p) {
        table.getItems().setAll(p.content());
        pagination.update(p.number(), Math.max(p.totalPages(), 1));
    }

    private void openForm(ClearanceDto dto) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/gov/brgy/ebms/desktop/fxml/clearanceForm.fxml"));
            Stage modal = new Stage();
            modal.initModality(Modality.APPLICATION_MODAL);
            modal.setTitle(dto == null ? "Submit Clearance" : "Clearance Detail");
            modal.setScene(new Scene(loader.load(), 480, 300));
            ClearanceFormController ctrl = loader.getController();
            ctrl.setEntity(dto);
            ctrl.setOnSaved(() -> load(currentPage));
            modal.showAndWait();
        } catch (Exception ex) {
            Dialogs.error("Cannot open form: " + ex.getMessage());
        }
    }
}
