package gov.brgy.ebms.desktop.controller;

import gov.brgy.ebms.desktop.api.Page;
import gov.brgy.ebms.desktop.api.dto.ComplaintDto;
import gov.brgy.ebms.desktop.controller.component.PaginationBar;
import gov.brgy.ebms.desktop.core.AsyncRunner;
import gov.brgy.ebms.desktop.core.Dialogs;
import gov.brgy.ebms.desktop.core.I18n;
import gov.brgy.ebms.desktop.service.EbmsService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ComplaintListController {

    @FXML private ComboBox<String> statusFilter;
    @FXML private TableView<ComplaintDto> table;
    @FXML private TableColumn<ComplaintDto, Long> colId;
    @FXML private TableColumn<ComplaintDto, String> colCase;
    @FXML private TableColumn<ComplaintDto, String> colTitle;
    @FXML private TableColumn<ComplaintDto, String> colStatus;
    @FXML private TableColumn<ComplaintDto, String> colFiledAt;
    @FXML private VBox paginationContainer;

    private final EbmsService service = EbmsService.get();
    private final PaginationBar pagination = new PaginationBar();
    private int currentPage = 0;
    private static final int PAGE_SIZE = 20;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().id()));
        colCase.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().caseNumber()));
        colTitle.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().title()));
        colStatus.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().status()));
        colFiledAt.setCellValueFactory(c -> new SimpleObjectProperty<>(
                c.getValue().filedAt() == null ? null : c.getValue().filedAt().toString()));

        if (statusFilter != null) {
            statusFilter.getItems().setAll("", "FILED", "UNDER_MEDIATION", "RESOLVED", "ESCALATED");
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
    public void onTransition() {
        ComplaintDto selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { Dialogs.error("Select a complaint."); return; }

        ChoiceDialog<String> statusDialog = new ChoiceDialog<>("UNDER_MEDIATION",
            "FILED", "UNDER_MEDIATION", "RESOLVED", "ESCALATED");
        statusDialog.setTitle("Change Status");
        statusDialog.setHeaderText("Select new status for: " + selected.title());
        statusDialog.showAndWait().ifPresent(newStatus -> {
            TextInputDialog noteDialog = new TextInputDialog();
            noteDialog.setTitle("Note");
            noteDialog.setHeaderText("Add a note (optional):");
            noteDialog.showAndWait().ifPresent(note ->
                AsyncRunner.run(
                    () -> service.transitionComplaint(selected.id(), newStatus, note),
                    r -> load(currentPage),
                    Dialogs::handleApiError
                )
            );
        });
    }

    @FXML
    public void onDelete() {
        ComplaintDto selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { Dialogs.error("Select a complaint to delete."); return; }
        if (!Dialogs.confirm("Delete complaint " + selected.caseNumber() + "?")) return;
        AsyncRunner.runVoid(
            () -> service.deleteComplaint(selected.id()),
            () -> load(currentPage),
            Dialogs::handleApiError
        );
    }

    private void load(int page) {
        currentPage = page;
        String status = statusFilter != null ? statusFilter.getValue() : null;
        if (status != null && status.isBlank()) status = null;
        final String s = status;
        AsyncRunner.run(
            () -> service.listComplaints(s, page, PAGE_SIZE),
            this::populate,
            Dialogs::handleApiError
        );
    }

    private void populate(Page<ComplaintDto> p) {
        table.getItems().setAll(p.content());
        pagination.update(p.number(), Math.max(p.totalPages(), 1));
    }

    private void openForm(ComplaintDto dto) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/gov/brgy/ebms/desktop/fxml/complaintForm.fxml"), I18n.bundle());
            Stage modal = new Stage();
            modal.initModality(Modality.APPLICATION_MODAL);
            modal.setTitle(dto == null ? "File Complaint" : "Complaint Detail");
            modal.setScene(new Scene(loader.load(), 560, 450));
            ComplaintFormController ctrl = loader.getController();
            ctrl.setEntity(dto);
            ctrl.setOnSaved(() -> load(currentPage));
            modal.showAndWait();
        } catch (Exception ex) {
            Dialogs.error("Cannot open form: " + ex.getMessage());
        }
    }
}
