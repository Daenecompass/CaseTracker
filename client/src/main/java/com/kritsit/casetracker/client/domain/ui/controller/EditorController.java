package com.kritsit.casetracker.client.domain.ui.controller;

import com.kritsit.casetracker.client.domain.services.Export;
import com.kritsit.casetracker.client.domain.services.IEditorService;
import com.kritsit.casetracker.client.domain.services.IExportService;
import com.kritsit.casetracker.client.domain.services.IMenuService;
import com.kritsit.casetracker.client.domain.services.InputToModelParseResult;
import com.kritsit.casetracker.client.domain.model.Appointment;
import com.kritsit.casetracker.client.domain.model.Day;
import com.kritsit.casetracker.client.domain.ui.LoadingDialog;
import com.kritsit.casetracker.shared.domain.model.Case;
import com.kritsit.casetracker.shared.domain.model.Defendant;
import com.kritsit.casetracker.shared.domain.model.Evidence;
import com.kritsit.casetracker.shared.domain.model.Permission;
import com.kritsit.casetracker.shared.domain.model.Person;
import com.kritsit.casetracker.shared.domain.model.Staff;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SelectionModel;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableView;
import javafx.scene.control.TableView.TableViewSelectionModel;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.Scene;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.time.LocalDate;
import java.io.File;
import java.io.IOException;
import java.util.GregorianCalendar;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Vector;

public class EditorController implements IController {
    private final Logger logger = LoggerFactory.getLogger(EditorController.class);
    private IEditorService editorService;
    private IMenuService menuService;
    private IExportService exportService;
    private Stage stage;
    private ObservableList<Case> cases;
    private int calendarCurrentYear;
    private int calendarCurrentMonth;
    private FilteredList<Case> filteredCases;

    public void setEditorService(IEditorService editorService) {
        this.editorService = editorService;
    }

    public void setMenuService(IMenuService menuService) {
        this.menuService = menuService;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void initFrame() {
        logger.info("Initiating frame");
        initCasesTable();
        initCalendarTable();
        if (editorService.getUser().getPermission() == Permission.EDITOR) {
            initAddCaseTab();
        } else {
            logger.debug("Add case tab disabled");
            tabAddCase.setDisable(true);
            addCaseItem.setDisable(true);
        } 
    }

    public void initialize() {
        changePasswordItem.setOnAction(event->{
            menuService.changePasswordFrame();
        });

        addCaseItem.setOnAction(event->{
            SingleSelectionModel<Tab> selection = tabPane.getSelectionModel();
            selection.select(tabAddCase);
        });

        editCaseItem.setOnAction(event->{
            handleEditCaseAction(null);
        });

        logoutItem.setOnAction(event->{
            stage.close();
            menuService.restart();
        });

        exitItem.setOnAction(event->{
            menuService.closeConnection();
            stage.close();
        });

        updateItem.setOnAction(event->{
            menuService.updateFrame();
        });

        reportItem.setOnAction(event->{
            export(null, true); 
        });

        reportMyCasesItem.setOnAction(event->{
            export(editorService.getUser(), true);
        });

        pendingCasesItem.setOnAction(event->{
            export(null, false);
        });

        byRegionItem.setOnAction(event->{
            exportByRegion();
        });

        helpItem.setOnAction(event->{
            showHelpFrame();
        });

        aboutItem.setOnAction(event->{
            menuService.aboutFrame();
        });
    }

    private void showHelpFrame(){

        AnchorPane helpFrame = null;
        FXMLLoader fxmlLoader = new FXMLLoader(getClass()
                .getResource("/ui/fxml/EditorHelpFrame.fxml"));

        fxmlLoader.setRoot(helpFrame);

        try{
            helpFrame = (AnchorPane) fxmlLoader.load();
        } catch(IOException e){
            logger.error("Error loading help frame.", e);
            return;
        }

        Scene scene = new Scene(helpFrame);
        Stage stage = new Stage();
        stage.setTitle("Help");
        stage.setResizable(false);
        stage.setScene(scene);
        stage.show();
    }

    private void exportByRegion() {
        exportService = new Export();

        List<String> headers = new ArrayList<String>();
        headers.add("Number");
        headers.add("Description");
        headers.add("Investigating Officer");
        headers.add("Incident Date");
        headers.add("Type");
        headers.add("Region");

        Vector<String> uniqueRegions = new Vector<String>();
        for(Case c : filteredCases) {
            if(!(uniqueRegions.contains(c.getIncident().getRegion()))) 
                uniqueRegions.add(c.getIncident().getRegion());
        }

        List<String[]> cells = new ArrayList<String[]>();
        for(String region : uniqueRegions) {
            for(Case c : filteredCases) {
                if(!(c.getIncident().getRegion().equals(region))) continue;
                String[] row = new String[6];
                row[0] = c.getNumber();
                row[1] = c.getDescription();
                row[2] = c.getInvestigatingOfficer().getName().toString();
                row[3] = c.getIncident().getDate().toString();
                row[4] = c.getType();
                row[5] = c.getIncident().getRegion();
                cells.add(row);
            }
        }   

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save cases");
        File file = fileChooser.showSaveDialog(stage);
        if(file == null) {
            logger.info("cancelling PDF export");
            return;
        }
        if(!(file.getName().endsWith(".pdf"))){
            File fileWithExtension = new File(file.getAbsolutePath()+".pdf"); 
            exportService.exportToPDF(headers, cells, fileWithExtension);
        }
        else{
            exportService.exportToPDF(headers, cells, file);
        }
    }

    private void export(Staff user, Boolean isFollowedUp) {
        exportService = new Export();

        List<String> headers = new ArrayList<String>();
        headers.add("Number");
        headers.add("Description");
        headers.add("Investigating Officer");
        headers.add("Incident Date");
        headers.add("Type");
        if(!isFollowedUp) headers.add("Follow up date");

        List<String[]> cells = new ArrayList<String[]>();
        for(Case c : filteredCases) {
            int n = 5;
            if(user!=null) if(!c.getInvestigatingOfficer().equals(user)) continue;
            if(!isFollowedUp) {
                n=6;
                if(c.getIncident().isFollowedUp()) continue;
            }

            String[] row = new String[n];
            row[0] = c.getNumber();
            row[1] = c.getDescription();
            row[2] = c.getInvestigatingOfficer().getName().toString();
            row[3] = c.getIncident().getDate().toString();
            row[4] = c.getType();
            if(!isFollowedUp) row[5] = c.getIncident().getFollowUpDate().toString();
            cells.add(row);
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save cases");
        File file = fileChooser.showSaveDialog(stage);
        if(file == null) {
            logger.info("cancelling PDF export");
            return;
        }
        if(!(file.getName().endsWith(".pdf"))){
            File fileWithExtension = new File(file.getAbsolutePath()+".pdf"); 
            exportService.exportToPDF(headers, cells, fileWithExtension);
        }
        else{
            exportService.exportToPDF(headers, cells, file);
        }
    }

    @SuppressWarnings("unchecked")
    private void initCasesTable() {
        logger.info("Initiating case list table");
        cases = FXCollections.observableArrayList(editorService.refreshCases());
        cbxFilterCaseType.getItems().clear();
        cbxFilterCaseType.getItems().add("All");
        cbxFilterCaseType.setValue("All");
        ObservableList<String> caseTypes = FXCollections.observableArrayList(editorService.getCaseTypes());
        cbxFilterCaseType.getItems().addAll(caseTypes);
        filteredCases = new FilteredList<>(cases, p -> true);
        txfFilterCases.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredCases.setPredicate(c -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                String lowerCaseFilter = newValue.toLowerCase();
                if (c.getName().toLowerCase().contains(lowerCaseFilter)) {
                    return true;
                } else if (c.getNumber().toLowerCase().contains(lowerCaseFilter)) {
                    return true;
                }
                return false;
            });
        });

        cbxFilterCaseType.valueProperty().addListener((obs, oldValue, newValue) -> {
            filteredCases.setPredicate(c -> {
                if (newValue == null || newValue.isEmpty() || newValue.equals("All")) {
                    return true;
                }
                String lowerCaseFilter = newValue.toLowerCase();
                if (c.getType().toLowerCase().contains(lowerCaseFilter)) {
                    return true;
                }
                return false;
            });
        });

        SortedList<Case> sortedCases = new SortedList<>(filteredCases);
        sortedCases.comparatorProperty().bind(tblCases.comparatorProperty());
        tblCases.setItems(sortedCases);

        colCaseNumber.setCellValueFactory(new PropertyValueFactory("caseNumber"));
        colCaseName.setCellValueFactory(new PropertyValueFactory("caseName"));
        colCaseType.setCellValueFactory(new PropertyValueFactory("caseType"));
        colInvestigatingOfficer.setCellValueFactory((CellDataFeatures<Case, String> c) -> 
                c.getValue().getInvestigatingOfficer().nameProperty());       
        colIncidentDate.setCellValueFactory((CellDataFeatures<Case, String> c) -> 
                c.getValue().getIncident().dateProperty());

        double numberWidthPercent = 0.15;
        double nameWidthPercent = 0.25;
        double officerWidthPercent = 0.25;
        double dateWidthPercent = 0.15;
        double typeWidthPercent = 0.2;

        colCaseNumber.prefWidthProperty().bind(tblCases.widthProperty().multiply(numberWidthPercent));
        colCaseName.prefWidthProperty().bind(tblCases.widthProperty().multiply(nameWidthPercent));
        colInvestigatingOfficer.prefWidthProperty().bind(tblCases.widthProperty().multiply(officerWidthPercent));
        colIncidentDate.prefWidthProperty().bind(tblCases.widthProperty().multiply(dateWidthPercent));
        colCaseType.prefWidthProperty().bind(tblCases.widthProperty().multiply(typeWidthPercent));
        tblCases.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            updateShownCase(newSelection);
        });
        updateShownCase(null);
    }

    private void initCalendarTable() {
        logger.info("Initiating calendar");
        tblCalendar.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        tblCalendar.getSelectionModel().setCellSelectionEnabled(true);
        List<TableColumn<List<Day>, String>> columns = new ArrayList<>();
        columns.add(colMonday);
        columns.add(colTuesday);
        columns.add(colWednesday);
        columns.add(colThursday);
        columns.add(colFriday);
        columns.add(colSaturday);
        columns.add(colSunday);
        for (int i = 0; i < columns.size(); i++) {
            TableColumn<List<Day>, String> column = columns.get(i);
            setCalendarColumnWidth(column);
            setCellValueFactory(column, i);
        }

        LocalDate today = LocalDate.now();
        int month = today.getMonthValue();
        int year = today.getYear();

        for (int i = year - 10; i <= year + 10; i++) {
            cbxCalendarYear.getItems().add(Integer.valueOf(i));
        }

        calendarCurrentYear = year;
        calendarCurrentMonth = month;

        cbxCalendarYear.valueProperty().addListener((obs, oldValue, newValue) -> {
            calendarCurrentYear = newValue;
            refreshCalendarTable(calendarCurrentMonth, calendarCurrentYear);
        });

        refreshCalendarTable(month, year);
    }

    private void setCalendarColumnWidth(TableColumn column) {
        int numColumns = 7;
        column.prefWidthProperty().bind(tblCalendar.widthProperty().divide(numColumns));
    }

    private void setCellValueFactory(TableColumn<List<Day>, String> column, final int dayIndex) {
        column.setCellValueFactory((CellDataFeatures<List<Day>, String> week) -> {
            Day day = week.getValue().get(dayIndex);
            return new SimpleStringProperty(day.toString());
        });
    }

    private void initAddCaseTab() {
        logger.info("Initiating add case tab");
        ObservableList<Staff> inspectors = FXCollections.observableArrayList(
                editorService.getInspectors());
        cmbAddInvestigatingOfficer.setItems(inspectors);
        if (editorService.getUser().getPermission() == Permission.EDITOR) {
            cmbAddInvestigatingOfficer.setValue(editorService.getUser());
        }

        ObservableList<String> caseTypes = FXCollections.observableArrayList(
                editorService.getCaseTypes());
        cmbAddCaseType.setItems(caseTypes);

        ObservableList<Defendant> defendants = FXCollections.observableArrayList(
                editorService.getDefendants());
        cmbAddDefendant.setItems(defendants);

        ObservableList<Person> complainants = FXCollections.observableArrayList(
                editorService.getComplainants());
        cmbAddComplainant.setItems(complainants);

        tabAddCase.setOnSelectionChanged(t -> {
            if (tabAddCase.isSelected()) {
                txfAddCaseNumber.setText(editorService.getNextCaseNumber());
            }
        });

        cbxAddIsReturnVisit.selectedProperty().addListener((obs, oldValue, newValue) -> {
            dpkAddReturnDate.setDisable(!newValue);
        });

        txfAddAddress.textProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null || newValue.isEmpty()) {
                txfAddLongitude.setDisable(false);
                txfAddLatitude.setDisable(false);
            } else {
                txfAddLongitude.setDisable(true);
                txfAddLatitude.setDisable(true);
            }
        });

        txfAddLongitude.textProperty().addListener((obs, oldValue, newValue) -> {
            String latitude = txfAddLatitude.getText();
            if (newValue == null || newValue.isEmpty()) {
                if (latitude == null || latitude.isEmpty()) {
                    txfAddAddress.setDisable(false);
                }
            } else {
                txfAddAddress.setDisable(true);
            }
        });

        txfAddLatitude.textProperty().addListener((obs, oldValue, newValue) -> {
            String longitude = txfAddLongitude.getText();
            if (newValue == null || newValue.isEmpty()) {
                if (longitude == null || longitude.isEmpty()) {
                    txfAddAddress.setDisable(false);
                }
            } else {
                txfAddAddress.setDisable(true);
            }
        });
    }

    private void refreshCalendarTable(int currentMonth, int currentYear) {
        String[] month = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
        logger.info("Refreshing calendar to {} of {}", month[currentMonth - 1], currentYear);
        LocalDate today = LocalDate.now();
        int realYear = today.getYear();

        btnCalendarNext.setDisable(false);
        btnCalendarPrevious.setDisable(false);
        if (currentMonth == 12 && currentYear >= realYear + 10) {
            btnCalendarNext.setDisable(true);
        }
        if (currentMonth == 1 && currentYear <= realYear - 10) {
            btnCalendarPrevious.setDisable(true);
        }

        txtCalendarMonth.setText(month[currentMonth - 1]);
        cbxCalendarYear.setValue(Integer.valueOf(currentYear));

        List<List<Day>> monthAppointments = editorService.getMonthAppointments(currentMonth, currentYear);

        tblCalendar.setItems(FXCollections.observableList(monthAppointments));
    }

    private void updateShownCase(Case c) {
        logger.info("Updating displayed case to {}", c);
        if (c == null) {
            logger.debug("No case to show");
            panCaseSummary.setVisible(false);
        } else { 
            panCaseSummary.setVisible(true);
            txtSummaryCaseName.setText(c.getName());
            txtSummaryCaseNumber.setText(c.getNumber());
            txtSummaryCaseType.setText(c.getType());
            txtSummaryInvestigatingOfficer.setText(c.getInvestigatingOfficer().getName());
            txtSummaryIncidentDate.setText(c.getIncident().getDate().toString());
            txtSummaryDefendant.setText(c.getDefendant().getName());
            if (c.getNextCourtDate() != null) {
                txtSummaryCourtDate.setText(c.getNextCourtDate().toString());
            } else {
                txtSummaryCourtDate.setText("N/A");
            }
            if (c.isReturnVisit()) {
                txtSummaryReturnDate.setText(c.getReturnDate().toString());
            } else {
                txtSummaryReturnDate.setText("N/A");
            }
            if (c.getIncident().getAddress() != null) {
                txtSummaryLocation.setText("Address: ");
                txtSummaryLocationValue.setText(c.getIncident().getAddress());
                txtSummaryLatitude.setVisible(false);
                txtSummaryLatitudeValue.setVisible(false);
            } else {
                txtSummaryLocation.setText("Longitude: ");
                txtSummaryLocationValue.setText(c.getIncident().getLongitude() + "");
                txtSummaryLatitude.setVisible(true);
                txtSummaryLatitudeValue.setText(c.getIncident().getLatitude() + "");
            }
            txaSummaryDetails.setText(c.getDescription());
            if (c.getEvidence() == null) {
                lstSummaryEvidence.setItems(FXCollections.observableList(new ArrayList<Evidence>()));
            } else {
                lstSummaryEvidence.setItems(FXCollections.observableList(c.getEvidence()));
            }
        }
    }

    @FXML protected void handleFilterClearAction(ActionEvent e) {
    logger.info("Clearing case filter");
    cbxFilterCaseType.setValue("All");
    txfFilterCases.setText("");
    }

    @FXML protected void handleExportCaseToPDF(ActionEvent e) {
    TableViewSelectionModel<Case> selection =  tblCases.getSelectionModel();
    if(selection.getSelectedItem() == null) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Exporting case");
        alert.setHeaderText("Information");
        alert.setContentText("Select the case you want to export");
        alert.showAndWait();   
        return;
    }

    exportService = new Export();

    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Export a case");
    File file = fileChooser.showSaveDialog(stage);
    if(!(file.getName().endsWith(".pdf"))){
        File fileWithExtension = new File(file.getAbsolutePath()+".pdf"); 
        exportService.exportCaseToPDF(selection.getSelectedItem(), fileWithExtension);
    }
    else{
        exportService.exportCaseToPDF(selection.getSelectedItem(), file);
    }

    }

    @FXML protected void handleEditCaseAction(ActionEvent e) {
    TableViewSelectionModel<Case> selection =  tblCases.getSelectionModel();
    if(selection.getSelectedItem() == null) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Editing case");
        alert.setHeaderText("Information");
        alert.setContentText("Select a case to edit");
        alert.showAndWait();   
        return;
    }

    Case c = selection.getSelectedItem();
    EditCaseController controller = new EditCaseController(c, editorService, this);
    AnchorPane EditCasePane = null;
    FXMLLoader fxmlLoader = new FXMLLoader(getClass()
            .getResource("/ui/fxml/EditCase.fxml"));

    fxmlLoader.setController(controller);
    fxmlLoader.setRoot(EditCasePane);

    try{
        EditCasePane = (AnchorPane) fxmlLoader.load();
    } catch(IOException ex) {
        logger.error("Error loading frame to edit case.", ex);
        return;
    }

    Scene scene = new Scene(EditCasePane);
    Stage stage = new Stage();
    stage.setTitle("Editing case " + c.getNumber());
    stage.setResizable(false);
    stage.setScene(scene);
    controller.setStage(stage);
    stage.show();
    }

    @FXML protected void handleCalendarPreviousAction(ActionEvent e) {
    logger.info("Displaying previous month in the calendar");
    if (calendarCurrentMonth == 1) {
        logger.debug("Year rolled over");
        calendarCurrentMonth = 12;
        calendarCurrentYear--;
    } else {
        calendarCurrentMonth--;
    }
    refreshCalendarTable(calendarCurrentMonth, calendarCurrentYear);
    }

    @FXML protected void handleCalendarNextAction(ActionEvent e) {
    logger.info("Displaying next month in the calendar");
    if (calendarCurrentMonth == 12) {
        logger.debug("Year rolled over");
        calendarCurrentMonth = 1;
        calendarCurrentYear++;
    } else {
        calendarCurrentMonth++;
    }
    refreshCalendarTable(calendarCurrentMonth, calendarCurrentYear);
    }

    @FXML protected void handleCalendarTodayAction(ActionEvent e) {
    logger.info("Displaying today in the calendar");
    LocalDate today = LocalDate.now();
    calendarCurrentMonth = today.getMonthValue();
    calendarCurrentYear = today.getYear();
    refreshCalendarTable(calendarCurrentMonth, calendarCurrentYear);
    }

    @FXML protected void handleAddNewDefendantAction(ActionEvent e) {
    AddPersonController c = new AddPersonController(editorService, true);
    GridPane addPersonPane = null;
    FXMLLoader fxmlLoader = new FXMLLoader(getClass()
            .getResource("/ui/fxml/AddPersonFrame.fxml"));

    fxmlLoader.setController(c);
    fxmlLoader.setRoot(addPersonPane);

    try {
        addPersonPane = (GridPane) fxmlLoader.load();
    } catch (IOException ex) {
        logger.error("Error loading frame to add defendant", ex);
        return;
    }

    Scene scene = new Scene(addPersonPane);
    Stage stage = new Stage();
    stage.setTitle("Add Defendant");
    stage.setResizable(false);
    stage.setScene(scene);
    c.setStage(stage);
    stage.showAndWait();
    Defendant defendant = c.getDefendant();
    if (defendant != null) {
        cmbAddDefendant.getItems().add(defendant);
        cmbAddDefendant.setValue(defendant);
    }
    }

    @FXML protected void handleAddNewComplainantAction(ActionEvent e) {
    Stage stage = new Stage();
    AddPersonController c = new AddPersonController(editorService, false);
    GridPane addPersonPane = null;
    FXMLLoader fxmlLoader = new FXMLLoader(getClass()
            .getResource("/ui/fxml/AddPersonFrame.fxml"));

    fxmlLoader.setController(c);
    fxmlLoader.setRoot(addPersonPane);

    try {
        addPersonPane = (GridPane) fxmlLoader.load();
    } catch (IOException ex) {
        logger.error("Error loading frame to add defendant", ex);
        return;
    }

    Scene scene = new Scene(addPersonPane);
    stage.setTitle("Add Complainant");
    stage.setResizable(false);
    stage.setScene(scene);
    c.setStage(stage);
    stage.showAndWait();
    Person complainant = c.getComplainant();
    if (complainant != null) {
        cmbAddComplainant.getItems().add(complainant);
        cmbAddComplainant.setValue(complainant);
    }
    }

    @FXML protected void handleAddEvidenceAction(ActionEvent e) {
    logger.info("Adding evidence to new case");
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Add Evidence Files");
    fileChooser.getExtensionFilters().addAll(
            new ExtensionFilter("Text Files", "*.txt", "*.docx", "*.xmlx", "*.doc", "*.xml", "*.pdf"),
            new ExtensionFilter("Image Files", "*.jpg", "*.png", "*.gif", "*.jpeg"),
            new ExtensionFilter("Video Files", "*.mp4", "*.avi", "*.mkv", "*.mts"),
            new ExtensionFilter("All Files", "*.*"));
    File evidenceFile = fileChooser.showOpenDialog(stage);
    if (evidenceFile != null) {
        logger.debug("Adding selected evidence to new case");
        String name = evidenceFile.getName();
        Evidence evidence = new Evidence(-1, name, null, evidenceFile);
        lstAddEvidence.getItems().add(evidence);
    }
    }

    @FXML protected void handleEditEvidenceAction(ActionEvent e) {
    logger.info("Editing evidence attached to new case");
    Evidence evidence = lstAddEvidence.getSelectionModel().getSelectedItem();
    Evidence oldEvidence = evidence;
    if (evidence != null) {
        TextInputDialog editDialog = new TextInputDialog(evidence.getDescription());
        editDialog.setTitle("Edit Evidence");
        editDialog.setContentText("Please enter the description:");
        Optional<String> newDescription = editDialog.showAndWait();
        if (newDescription.isPresent()) {
            logger.debug("Setting new evidence description to {}", newDescription.get());
            evidence.setDescription(newDescription.get());
            int index = lstAddEvidence.getItems().indexOf(oldEvidence);
            lstAddEvidence.getItems().set(index, evidence);
        }
    } else {
        logger.debug("No evidence selected to edit");
        Alert selectionWarning = new Alert(AlertType.WARNING);
        selectionWarning.setTitle("No Evidence Selected");
        selectionWarning.setContentText("No evidence selected to edit");
        selectionWarning.showAndWait();
    }
    }

    @FXML protected void handleDeleteEvidenceAction(ActionEvent e) {
    logger.info("Deleting evidence from new case");
    Evidence evidence = lstAddEvidence.getSelectionModel().getSelectedItem();
    if (evidence != null) {
        Alert confirmationAlert = new Alert(AlertType.CONFIRMATION);
        confirmationAlert.setTitle("Confirm Deletion");
        confirmationAlert.setContentText("Are you sure you want to remove this evidence?");
        Optional<ButtonType> result = confirmationAlert.showAndWait();
        if (result.get() == ButtonType.OK) {
            logger.debug("Deleting evidence {} from new case", evidence);
            lstAddEvidence.getItems().remove(evidence);
        }
    } else {
        logger.debug("No evidence selected to delete");
        Alert selectionWarning = new Alert(AlertType.WARNING);
        selectionWarning.setTitle("No Evidence Selected");
        selectionWarning.setContentText("No evidence selected to delete");
        selectionWarning.showAndWait();
    }
    }

    @FXML protected void handleAddCaseAction(ActionEvent e) {
    logger.info("Creating new case");
    LoadingDialog loadingDialog = new LoadingDialog();
    loadingDialog.run();
    Map<String, Object> inputMap = new HashMap<>();
    inputMap.put("caseNumber", txfAddCaseNumber.getText());
    inputMap.put("incidentDate", dpkAddIncidentDate.getValue());
    inputMap.put("investigatingOfficer", cmbAddInvestigatingOfficer
            .getSelectionModel().getSelectedItem());
    inputMap.put("caseType", cmbAddCaseType.getSelectionModel().getSelectedItem()); 
    inputMap.put("isReturnVisit", cbxAddIsReturnVisit.isSelected());
    inputMap.put("returnDate", dpkAddReturnDate.getValue());
    inputMap.put("caseName", txfAddCaseName.getText());
    inputMap.put("defendant", cmbAddDefendant.getSelectionModel().getSelectedItem());
    inputMap.put("complainant", cmbAddComplainant.getSelectionModel().getSelectedItem());
    inputMap.put("address", txfAddAddress.getText());
    inputMap.put("longitude", txfAddLongitude.getText());
    inputMap.put("latitude", txfAddLatitude.getText());
    inputMap.put("region", txfAddRegion.getText());
    inputMap.put("details", txaAddDetails.getText());
    inputMap.put("animalsInvolved", txaAddAnimalsInvolved.getText());
    inputMap.put("evidence", lstAddEvidence.getItems());

    InputToModelParseResult result = editorService.addCase(inputMap);
    loadingDialog.exit();

    if (result.isSuccessful()) {
        logger.info("Case added successfully");
        resetAddCaseTab();
        refreshCaseList();
        Alert info = new Alert(AlertType.INFORMATION);
        info.setTitle("Case Added");
        info.setContentText("Case added successfully");
        info.showAndWait();
    } else {
        logger.error("Unable to add case. {}", result.getReason());
        Alert error = new Alert(AlertType.ERROR);
        error.setTitle("Error");
        error.setHeaderText("Unable to add case");
        error.setContentText(result.getReason());
        error.showAndWait();
    }
    }

    private void resetAddCaseTab() {
        txfAddCaseNumber.setText("");
        cmbAddInvestigatingOfficer.setValue(editorService.getUser());
        cmbAddCaseType.setValue("");
        cbxAddIsReturnVisit.setSelected(false);
        txfAddCaseName.setText("");
        txfAddAddress.setText("");
        txfAddLongitude.setText("");
        txfAddLatitude.setText("");
        txfAddRegion.setText("");
        txaAddDetails.setText("");
        txaAddAnimalsInvolved.setText("");
        lstAddEvidence.setItems(FXCollections.observableList(new ArrayList<Evidence>()));
    }

    public void refreshCaseList() {
        initCasesTable();
    } 

    @FXML private Button btnCalendarNext;
    @FXML private Button btnCalendarPrevious;
    @FXML private CheckBox cbxAddIsReturnVisit;
    @FXML private ChoiceBox<Integer> cbxCalendarYear;
    @FXML private ChoiceBox<String> cbxFilterCaseType;
    @FXML private ComboBox<Person> cmbAddComplainant;
    @FXML private ComboBox<Defendant> cmbAddDefendant;
    @FXML private ComboBox<Staff> cmbAddInvestigatingOfficer;
    @FXML private ComboBox<String> cmbAddCaseType;
    @FXML private DatePicker dpkAddIncidentDate;
    @FXML private DatePicker dpkAddReturnDate;
    @FXML private GridPane panCaseSummary;
    @FXML private ListView<Evidence> lstSummaryEvidence;
    @FXML private ListView<Evidence> lstAddEvidence;
    @FXML private TabPane tabPane;
    @FXML private Tab tabAddCase;
    @FXML private TableView<Case> tblCases;
    @FXML private TableView<List<Day>> tblCalendar;
    @FXML private TableColumn<Case, String> colCaseNumber;
    @FXML private TableColumn<Case, String> colCaseName;
    @FXML private TableColumn<Case, String> colInvestigatingOfficer;
    @FXML private TableColumn<Case, String> colIncidentDate;
    @FXML private TableColumn<Case, String> colCaseType;
    @FXML private TableColumn<List<Day>, String> colMonday;
    @FXML private TableColumn<List<Day>, String> colTuesday;
    @FXML private TableColumn<List<Day>, String> colWednesday;
    @FXML private TableColumn<List<Day>, String> colThursday;
    @FXML private TableColumn<List<Day>, String> colFriday;
    @FXML private TableColumn<List<Day>, String> colSaturday;
    @FXML private TableColumn<List<Day>, String> colSunday;
    @FXML private TextArea txaAddAnimalsInvolved;
    @FXML private TextArea txaAddDetails;
    @FXML private TextArea txaSummaryDetails;
    @FXML private TextField txfAddAddress;
    @FXML private TextField txfAddCaseName;
    @FXML private TextField txfAddCaseNumber;
    @FXML private TextField txfAddLatitude;
    @FXML private TextField txfAddLongitude;
    @FXML private TextField txfAddRegion;
    @FXML private TextField txfFilterCases;
    @FXML private Text txtCalendarMonth;
    @FXML private Text txtSummaryDefendant;
    @FXML private Text txtSummaryCaseName;
    @FXML private Text txtSummaryCaseNumber;
    @FXML private Text txtSummaryCaseType;
    @FXML private Text txtSummaryCourtDate;
    @FXML private Text txtSummaryLatitude;
    @FXML private Text txtSummaryLatitudeValue;
    @FXML private Text txtSummaryLocation;
    @FXML private Text txtSummaryLocationValue;
    @FXML private Text txtSummaryIncidentDate;
    @FXML private Text txtSummaryInvestigatingOfficer;
    @FXML private Text txtSummaryReturnDate;
    @FXML private MenuItem reportItem;
    @FXML private MenuItem changePasswordItem;
    @FXML private MenuItem logoutItem;
    @FXML private MenuItem exitItem;
    @FXML private MenuItem addCaseItem;
    @FXML private MenuItem editCaseItem;
    @FXML private MenuItem aboutItem;
    @FXML private MenuItem updateItem;
    @FXML private MenuItem helpItem;
    @FXML private MenuItem reportMyCasesItem;
    @FXML private MenuItem pendingCasesItem;
    @FXML private MenuItem byRegionItem;
    }
