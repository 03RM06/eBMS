package gov.brgy.ebms.desktop.controller;

import gov.brgy.ebms.desktop.api.Page;
import gov.brgy.ebms.desktop.api.dto.HouseholdDto;
import gov.brgy.ebms.desktop.controller.component.PaginationBar;
import gov.brgy.ebms.desktop.core.AsyncRunner;
import gov.brgy.ebms.desktop.core.Dialogs;
import gov.brgy.ebms.desktop.core.Session;
import gov.brgy.ebms.desktop.service.EbmsService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class HouseholdListController {

    @FXML private TableView<HouseholdDto> table;
    @FXML private TableColumn<HouseholdDto, Long> colId;
    @FXML private TableColumn<HouseholdDto, String> colCode;
    @FXML private TableColumn<HouseholdDto, String> colHouseNo;
    @FXML private TableColumn<HouseholdDto, String> colStreet;
    @FXML private TableColumn<HouseholdDto, String> colPurok;
    @FXML private VBox paginationContainer;

    private final EbmsService service = EbmsService.get();
    private final PaginationBar pagination = new PaginationBar();
    private int currentPage = 0;
    private static final int PAGE_SIZE = 20;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().id()));
        colCode.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().householdCode()));
        colHouseNo.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().houseNo()));
        colStreet.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().street()));
        colPurok.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().purokSitio()));

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
    public void onCreate() { openForm(null); }

    @FXML
    public void onMembers() {
        HouseholdDto selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { Dialogs.error("Select a household first."); return; }
        openMembers(selected);
    }

    @FXML
    public void onDelete() {
        HouseholdDto selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { Dialogs.error("Select a household to delete."); return; }
        if (!Dialogs.confirm("Delete household " + selected.householdCode() + "?")) return;
        AsyncRunner.runVoid(
            () -> service.deleteHousehold(selected.id()),
            () -> load(currentPage),
            Dialogs::handleApiError
        );
    }

    private void load(int page) {
        currentPage = page;
        AsyncRunner.run(
            () -> service.listHouseholds(page, PAGE_SIZE),
            this::populate,
            Dialogs::handleApiError
        );
    }

    private void populate(Page<HouseholdDto> p) {
        table.getItems().setAll(p.content());
        pagination.update(p.number(), Math.max(p.totalPages(), 1));
    }

    private void openForm(HouseholdDto dto) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/gov/brgy/ebms/desktop/fxml/householdForm.fxml"));
            Stage modal = new Stage();
            modal.initModality(Modality.APPLICATION_MODAL);
            modal.setTitle(dto == null ? "Create Household" : "Edit Household");
            modal.setScene(new Scene(loader.load(), 480, 320));
            HouseholdFormController ctrl = loader.getController();
            ctrl.setEntity(dto);
            ctrl.setOnSaved(() -> load(currentPage));
            modal.showAndWait();
        } catch (Exception ex) {
            Dialogs.error("Cannot open form: " + ex.getMessage());
        }
    }

    private void openMembers(HouseholdDto dto) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/gov/brgy/ebms/desktop/fxml/householdMembers.fxml"));
            Stage modal = new Stage();
            modal.initModality(Modality.APPLICATION_MODAL);
            modal.setTitle("Household Members - " + dto.householdCode());
            modal.setScene(new Scene(loader.load(), 600, 450));
            HouseholdMembersController ctrl = loader.getController();
            ctrl.setHousehold(dto);
            modal.showAndWait();
        } catch (Exception ex) {
            Dialogs.error("Cannot open members: " + ex.getMessage());
        }
    }
}
