package gov.brgy.ebms.desktop.controller;

import gov.brgy.ebms.desktop.api.dto.AddMemberRequest;
import gov.brgy.ebms.desktop.api.dto.HouseholdDto;
import gov.brgy.ebms.desktop.api.dto.ResidentDto;
import gov.brgy.ebms.desktop.api.dto.SetHeadRequest;
import gov.brgy.ebms.desktop.core.AsyncRunner;
import gov.brgy.ebms.desktop.core.Dialogs;
import gov.brgy.ebms.desktop.service.EbmsService;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class HouseholdMembersController {

    @FXML private Label lblHousehold;
    @FXML private TableView<ResidentDto> table;
    @FXML private TableColumn<ResidentDto, Long> colId;
    @FXML private TableColumn<ResidentDto, String> colName;
    @FXML private TableColumn<ResidentDto, String> colCode;
    @FXML private TextField residentIdField;
    @FXML private TextField relationshipField;

    private final EbmsService service = EbmsService.get();
    private HouseholdDto household;

    @FXML
    public void initialize() {
        if (colId != null) colId.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().id()));
        if (colName != null) colName.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(
                c.getValue().firstName() + " " + c.getValue().lastName()));
        if (colCode != null) colCode.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().residentCode()));
    }

    public void setHousehold(HouseholdDto dto) {
        this.household = dto;
        if (lblHousehold != null)
            lblHousehold.setText("Household: " + dto.householdCode());
        loadMembers();
    }

    private void loadMembers() {
        // Load all residents belonging to this household
        AsyncRunner.run(
            () -> service.listResidents(null, 0, 100),
            page -> {
                java.util.List<ResidentDto> members = page.content().stream()
                    .filter(r -> household.id().equals(r.householdId()))
                    .toList();
                if (table != null) table.getItems().setAll(members);
            },
            Dialogs::handleApiError
        );
    }

    @FXML
    public void onAddMember() {
        if (residentIdField == null || residentIdField.getText().isBlank()) {
            Dialogs.error("Enter a Resident ID.");
            return;
        }
        Long residentId;
        try { residentId = Long.parseLong(residentIdField.getText().trim()); }
        catch (NumberFormatException e) { Dialogs.error("Resident ID must be a number."); return; }

        String relationship = relationshipField != null ? relationshipField.getText().trim() : "MEMBER";
        if (relationship.isBlank()) relationship = "MEMBER";

        final String rel = relationship;
        final Long rid = residentId;
        AsyncRunner.runVoid(
            () -> service.addHouseholdMember(household.id(), new AddMemberRequest(rid, rel)),
            () -> { loadMembers(); if (residentIdField != null) residentIdField.clear(); },
            Dialogs::handleApiError
        );
    }

    @FXML
    public void onRemoveMember() {
        ResidentDto selected = table != null ? table.getSelectionModel().getSelectedItem() : null;
        if (selected == null) { Dialogs.error("Select a member to remove."); return; }
        if (!Dialogs.confirm("Remove " + selected.firstName() + " from this household?")) return;
        AsyncRunner.runVoid(
            () -> service.removeHouseholdMember(household.id(), selected.id()),
            this::loadMembers,
            Dialogs::handleApiError
        );
    }

    @FXML
    public void onSetHead() {
        ResidentDto selected = table != null ? table.getSelectionModel().getSelectedItem() : null;
        if (selected == null) { Dialogs.error("Select a member to set as head."); return; }
        AsyncRunner.run(
            () -> service.setHouseholdHead(household.id(), new SetHeadRequest(selected.id())),
            h -> { household = h; loadMembers(); },
            Dialogs::handleApiError
        );
    }
}
