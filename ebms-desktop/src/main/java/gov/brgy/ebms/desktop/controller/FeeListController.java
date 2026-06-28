package gov.brgy.ebms.desktop.controller;

import gov.brgy.ebms.desktop.api.dto.FeeDto;
import gov.brgy.ebms.desktop.core.AsyncRunner;
import gov.brgy.ebms.desktop.core.Dialogs;
import gov.brgy.ebms.desktop.core.I18n;
import gov.brgy.ebms.desktop.service.EbmsService;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lists unpaid fees (not paged — server returns List, not Page).
 */
public class FeeListController {

    @FXML private TableView<FeeDto> table;
    @FXML private TableColumn<FeeDto, Long> colId;
    @FXML private TableColumn<FeeDto, String> colOrRef;
    @FXML private TableColumn<FeeDto, Long> colClearanceId;
    @FXML private TableColumn<FeeDto, String> colFeeType;
    @FXML private TableColumn<FeeDto, BigDecimal> colAmount;
    @FXML private TableColumn<FeeDto, String> colStatus;

    private final EbmsService service = EbmsService.get();

    @FXML
    public void initialize() {
        colId.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().id()));
        colOrRef.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().orReference()));
        colClearanceId.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().clearanceId()));
        colFeeType.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().feeType()));
        colAmount.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().amount()));
        colStatus.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().status()));

        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                openForm(table.getSelectionModel().getSelectedItem());
            }
        });

        load();
    }

    @FXML
    public void onRefresh() { load(); }

    @FXML
    public void onCreate() { openForm(null); }

    @FXML
    public void onPay() {
        FeeDto selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { Dialogs.error("Select a fee."); return; }
        if (!Dialogs.confirm("Mark fee as PAID?")) return;
        AsyncRunner.run(
            () -> service.payFee(selected.id()),
            r -> load(),
            Dialogs::handleApiError
        );
    }

    @FXML
    public void onWaive() {
        FeeDto selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { Dialogs.error("Select a fee."); return; }
        if (!Dialogs.confirm("Waive this fee?")) return;
        AsyncRunner.run(
            () -> service.waiveFee(selected.id()),
            r -> load(),
            Dialogs::handleApiError
        );
    }

    private void load() {
        AsyncRunner.run(
            () -> service.listUnpaidFees(),
            this::populate,
            Dialogs::handleApiError
        );
    }

    private void populate(List<FeeDto> fees) {
        table.getItems().setAll(fees);
    }

    private void openForm(FeeDto dto) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/gov/brgy/ebms/desktop/fxml/feeForm.fxml"), I18n.bundle());
            Stage modal = new Stage();
            modal.initModality(Modality.APPLICATION_MODAL);
            modal.setTitle(dto == null ? "Create Fee" : "Fee Detail");
            modal.setScene(new Scene(loader.load(), 400, 280));
            FeeFormController ctrl = loader.getController();
            ctrl.setEntity(dto);
            ctrl.setOnSaved(this::load);
            modal.showAndWait();
        } catch (Exception ex) {
            Dialogs.error("Cannot open form: " + ex.getMessage());
        }
    }
}
