package gov.brgy.ebms.desktop.controller;

import gov.brgy.ebms.desktop.api.Page;
import gov.brgy.ebms.desktop.api.dto.ResidentDto;
import gov.brgy.ebms.desktop.controller.component.PaginationBar;
import gov.brgy.ebms.desktop.core.AsyncRunner;
import gov.brgy.ebms.desktop.core.Dialogs;
import gov.brgy.ebms.desktop.core.I18n;
import gov.brgy.ebms.desktop.core.Session;
import gov.brgy.ebms.desktop.service.EbmsService;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;


public class ResidentListController {

    @FXML private TextField searchField;
    @FXML private TableView<ResidentDto> table;
    @FXML private TableColumn<ResidentDto, Long> colId;
    @FXML private TableColumn<ResidentDto, String> colCode;
    @FXML private TableColumn<ResidentDto, String> colFirstName;
    @FXML private TableColumn<ResidentDto, String> colLastName;
    @FXML private TableColumn<ResidentDto, String> colSex;
    @FXML private TableColumn<ResidentDto, String> colStatus;
    @FXML private VBox paginationContainer;

    private final EbmsService service = EbmsService.get();
    private final PaginationBar pagination = new PaginationBar();
    private int currentPage = 0;
    private static final int PAGE_SIZE = 20;

    @FXML
    public void initialize() {
        // Configure columns
        colId.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().id()));
        colCode.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().residentCode()));
        colFirstName.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().firstName()));
        colLastName.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().lastName()));
        colSex.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().sex()));
        colStatus.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().civilStatus()));

        // Pagination
        pagination.setOnPageChange(p -> load(p));
        if (paginationContainer != null) paginationContainer.getChildren().add(pagination);

        // Double-click to edit
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                openForm(table.getSelectionModel().getSelectedItem());
            }
        });

        load(0);
    }

    @FXML
    public void onSearch() {
        load(0);
    }

    @FXML
    public void onCreate() {
        openForm(null);
    }

    @FXML
    public void onDelete() {
        ResidentDto selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Dialogs.error("Select a resident to delete.");
            return;
        }
        if (!Session.get().hasAnyRole("SECRETARY", "BARANGAY_CAPTAIN", "SUPER_ADMIN")) {
            Dialogs.error("You do not have permission to delete residents.");
            return;
        }
        if (!Dialogs.confirm("Delete resident " + selected.firstName() + " " + selected.lastName() + "?")) return;

        AsyncRunner.runVoid(
            () -> service.deleteResident(selected.id()),
            () -> load(currentPage),
            Dialogs::handleApiError
        );
    }

    private void load(int page) {
        currentPage = page;
        String q = searchField != null ? searchField.getText().trim() : null;
        AsyncRunner.run(
            () -> service.listResidents(q, page, PAGE_SIZE),
            this::populate,
            Dialogs::handleApiError
        );
    }

    private void populate(Page<ResidentDto> p) {
        table.getItems().setAll(p.content());
        pagination.update(p.number(), Math.max(p.totalPages(), 1));
    }

    private void openForm(ResidentDto resident) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/gov/brgy/ebms/desktop/fxml/residentForm.fxml"), I18n.bundle());
            Stage modal = new Stage();
            modal.initModality(Modality.APPLICATION_MODAL);
            modal.setTitle(resident == null ? "Create Resident" : "Edit Resident");
            modal.setScene(new Scene(loader.load(), 600, 500));
            ResidentFormController ctrl = loader.getController();
            ctrl.setEntity(resident);
            ctrl.setOnSaved(() -> load(currentPage));
            modal.showAndWait();
        } catch (Exception ex) {
            Dialogs.error("Cannot open form: " + ex.getMessage());
        }
    }
}
