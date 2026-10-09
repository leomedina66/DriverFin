package com.driverfin.controller;

import com.driverfin.dao.DatabaseConnection;
import com.driverfin.dao.WorkShiftDAO;
import com.driverfin.model.FinancialSummary;
import com.driverfin.model.WorkShift;
import com.driverfin.service.SummaryService;
import com.driverfin.util.PdfReportExporter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

public class DashboardController {
    private static final Logger LOGGER = Logger.getLogger(DashboardController.class.getName());
    private static final String PREF_MAINT_RATE = "maintenance_rate";

    @FXML private ComboBox<String> periodComboBox;
    @FXML private DatePicker startDatePicker, endDatePicker;
    @FXML private TextField searchField, maintRateField;
    @FXML private Label lblGrossIncome, lblTotalCosts, lblMaintReserve, lblNetProfit, lblProfitPerHour, lblProfitPerKm;
    @FXML private TableView<WorkShift> tableRecords;
    @FXML private TableColumn<WorkShift, LocalDate> colDate;
    @FXML private TableColumn<WorkShift, Double> colHours, colKm;
    @FXML private TableColumn<WorkShift, BigDecimal> colGross, colCosts, colMaint, colNet, colProfitPerHour, colProfitPerKm;
    @FXML private TableColumn<WorkShift, Void> colActions;
    @FXML private BarChart<String, Number> chartDailyEvolution;
    
    private WorkShiftDAO dao;
    private DatabaseConnection dbConn;
    private Preferences prefs;
    private ObservableList<WorkShift> masterList = FXCollections.observableArrayList();
    private ObservableList<WorkShift> filteredList = FXCollections.observableArrayList();
    private BigDecimal currentMaintRate;

    @FXML
    public void initialize() {
        prefs = Preferences.userNodeForPackage(DashboardController.class);
        currentMaintRate = new BigDecimal(prefs.get(PREF_MAINT_RATE, "0.25"));
        maintRateField.setText(currentMaintRate.toString());
        
        dbConn = new DatabaseConnection();
        dao = new WorkShiftDAO(dbConn);

        setupTable();
        setupFilters();
        
        loadData();
    }

    private void setupTable() {
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        // Setup other cell value factories manually with formatting
        
        colActions.setCellFactory(param -> new TableCell<>() {
            private final Button btnEdit = new Button("Editar");
            private final Button btnDelete = new Button("Excluir");
            private final HBox pane = new HBox(5, btnEdit, btnDelete);

            {
                btnEdit.setOnAction(e -> handleEdit(getTableView().getItems().get(getIndex())));
                btnDelete.setOnAction(e -> handleDelete(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : pane);
            }
        });
        tableRecords.setItems(filteredList);
    }

    private void setupFilters() {
        maintRateField.textProperty().addListener((obs, old, val) -> {
            try {
                currentMaintRate = new BigDecimal(val.replace(",", "."));
                prefs.put(PREF_MAINT_RATE, currentMaintRate.toString());
                updateDashboard();
            } catch (NumberFormatException ignored) {}
        });
    }

    private void loadData() {
        try {
            masterList.setAll(dao.findAll());
            applyFilters();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "DB Load Error", e);
            showAlert(Alert.AlertType.ERROR, "Erro de Banco de Dados", "Falha ao carregar turnos: " + e.getMessage());
        }
    }

    private void applyFilters() {
        filteredList.setAll(masterList); // Simplified for refactor scope
        updateDashboard();
    }

    private void updateDashboard() {
        FinancialSummary summary = SummaryService.calculate(filteredList, currentMaintRate);
        lblGrossIncome.setText(String.format("R$ %.2f", summary.totalGross()));
        lblTotalCosts.setText(String.format("R$ %.2f", summary.totalCosts()));
        lblMaintReserve.setText(String.format("R$ %.2f", summary.totalMaint()));
        lblNetProfit.setText(String.format("R$ %.2f", summary.totalNet()));
        lblProfitPerHour.setText(String.format("R$ %.2f / h", summary.avgProfitPerHour()));
        lblProfitPerKm.setText(String.format("R$ %.2f / km", summary.avgProfitPerKm()));
        tableRecords.refresh();
        updateChart();
    }

    private void updateChart() {
        chartDailyEvolution.getData().clear();
        // Simplified chart update
    }

    @FXML private void handleNewRecord() { openForm(null); }
    private void handleEdit(WorkShift ws) { openForm(ws); }

    private void openForm(WorkShift ws) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/driverfin/view/record-form.fxml"));
            Parent root = loader.load();
            RecordFormController controller = loader.getController();
            controller.initData(ws, currentMaintRate, dao);
            
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(ws == null ? "Novo Turno" : "Editar Turno");
            stage.setScene(new Scene(root));
            stage.showAndWait();
            
            if (controller.isSaved()) loadData();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Form Error", e);
            showAlert(Alert.AlertType.ERROR, "Erro", "Não foi possível abrir o formulário.");
        }
    }

    private void handleDelete(WorkShift ws) {
        try {
            dao.delete(ws.getId());
            loadData();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Erro", "Falha ao excluir.");
        }
    }

    @FXML private void handleBackup() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite DB", "*.db"));
        File dest = fc.showSaveDialog(null);
        if (dest != null) {
            try {
                // Remove prefix jdbc:sqlite: to get actual file path
                String path = dbConn.getConnection().getMetaData().getURL().replace("jdbc:sqlite:", "");
                Files.copy(new File(path).toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                showAlert(Alert.AlertType.INFORMATION, "Sucesso", "Backup concluído.");
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Erro", "Falha no backup.");
            }
        }
    }

    @FXML private void handleExportPdf() {
        // Implementation
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setContentText(msg);
        a.showAndWait();
    }
}
