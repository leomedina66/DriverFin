package com.driverfin.controller;

import com.driverfin.dao.DailyRecordDAO;
import com.driverfin.model.DailyRecord;
import com.driverfin.util.PdfReportExporter;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DashboardController {

    private static final Logger LOGGER = Logger.getLogger(DashboardController.class.getName());
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM");

    @FXML private ComboBox<String> periodComboBox;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;

    @FXML private Button btnBackup;
    @FXML private Button btnPdfReport;
    @FXML private Button btnNewRecord;

    @FXML private TextField maintRateField;
    @FXML private TextField searchField;

    @FXML private Label lblGrossIncome;
    @FXML private Label lblTotalCosts;
    @FXML private Label lblMaintReserve;
    @FXML private Label lblNetProfit;
    @FXML private Label lblProfitPerHour;
    @FXML private Label lblProfitPerKm;

    @FXML private BarChart<String, Number> chartDailyEvolution;
    @FXML private CategoryAxis xAxis;
    @FXML private NumberAxis yAxis;

    @FXML private TableView<DailyRecord> tableRecords;
    @FXML private TableColumn<DailyRecord, String> colDate;
    @FXML private TableColumn<DailyRecord, String> colHours;
    @FXML private TableColumn<DailyRecord, String> colKm;
    @FXML private TableColumn<DailyRecord, String> colGross;
    @FXML private TableColumn<DailyRecord, String> colCosts;
    @FXML private TableColumn<DailyRecord, String> colMaint;
    @FXML private TableColumn<DailyRecord, String> colNet;
    @FXML private TableColumn<DailyRecord, String> colProfitPerHour;
    @FXML private TableColumn<DailyRecord, String> colProfitPerKm;
    @FXML private TableColumn<DailyRecord, Void> colActions;

    private final DailyRecordDAO recordDAO = new DailyRecordDAO();
    private final ObservableList<DailyRecord> masterRecordList = FXCollections.observableArrayList();
    private FilteredList<DailyRecord> filteredRecordList;
    private double currentMaintRate = 0.20;

    /** Debounce para evitar refreshes excessivos durante digitação rápida na taxa de manutenção. */
    private final PauseTransition maintRateDebounce = new PauseTransition(Duration.millis(250));

    @FXML
    public void initialize() {
        setupPeriodComboBox();
        setupMaintRateField();
        setupTableView();
        setupSearchFilter();

        loadData();
    }

    private void setupPeriodComboBox() {
        periodComboBox.setItems(FXCollections.observableArrayList(
            "Hoje",
            "Esta Semana",
            "Este Mês",
            "Todos os Tempos",
            "Personalizado"
        ));
        periodComboBox.setValue("Este Mês");

        periodComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean isCustom = "Personalizado".equals(newVal);
            startDatePicker.setVisible(isCustom);
            startDatePicker.setManaged(isCustom);
            endDatePicker.setVisible(isCustom);
            endDatePicker.setManaged(isCustom);

            if (isCustom && startDatePicker.getValue() == null) {
                startDatePicker.setValue(LocalDate.now().withDayOfMonth(1));
                endDatePicker.setValue(LocalDate.now());
            }

            refreshDashboard();
        });

        startDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            if ("Personalizado".equals(periodComboBox.getValue())) {
                refreshDashboard();
            }
        });

        endDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            if ("Personalizado".equals(periodComboBox.getValue())) {
                refreshDashboard();
            }
        });
    }

    private void setupMaintRateField() {
        maintRateField.setText("0,20");
        maintRateDebounce.setOnFinished(e -> refreshDashboard());

        maintRateField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.equals(oldVal)) return;

            String digits = newVal.replaceAll("\\D", "");
            if (digits.isEmpty()) digits = "0";
            if (digits.length() > 6) digits = digits.substring(0, 6);

            double value = Long.parseLong(digits) / 100.0;
            String formatted = String.format(PT_BR, "%.2f", value);

            if (!newVal.equals(formatted)) {
                maintRateField.setText(formatted);
                maintRateField.positionCaret(formatted.length());
            }

            currentMaintRate = value;
            // Debounce: adia o refresh para 250ms após a última tecla
            maintRateDebounce.playFromStart();
        });
    }

    private void setupTableView() {
        colDate.setCellValueFactory(cell -> {
            DailyRecord r = cell.getValue();
            return new SimpleStringProperty(r != null && r.getDate() != null ? r.getDate().format(DATE_FORMATTER) : "");
        });
        colHours.setCellValueFactory(cell -> {
            DailyRecord r = cell.getValue();
            return new SimpleStringProperty(r != null ? String.format(PT_BR, "%.1f h", r.calculateHours()) : "");
        });
        colKm.setCellValueFactory(cell -> {
            DailyRecord r = cell.getValue();
            return new SimpleStringProperty(r != null ? String.format(PT_BR, "%.1f km", r.calculateKm()) : "");
        });
        colGross.setCellValueFactory(cell -> {
            DailyRecord r = cell.getValue();
            return new SimpleStringProperty(r != null ? String.format(PT_BR, "R$ %,.2f", r.calculateGrossIncome()) : "");
        });
        colCosts.setCellValueFactory(cell -> {
            DailyRecord r = cell.getValue();
            return new SimpleStringProperty(r != null ? String.format(PT_BR, "R$ %,.2f", r.calculateTotalCosts()) : "");
        });
        colMaint.setCellValueFactory(cell -> {
            DailyRecord r = cell.getValue();
            return new SimpleStringProperty(r != null ? String.format(PT_BR, "R$ %,.2f", r.calculateMaintReserve(currentMaintRate)) : "");
        });
        colNet.setCellValueFactory(cell -> {
            DailyRecord r = cell.getValue();
            return new SimpleStringProperty(r != null ? String.format(PT_BR, "R$ %,.2f", r.calculateNetProfit(currentMaintRate)) : "");
        });
        colProfitPerHour.setCellValueFactory(cell -> {
            DailyRecord r = cell.getValue();
            if (r == null) return new SimpleStringProperty("");
            double hrs = r.calculateHours();
            double net = r.calculateNetProfit(currentMaintRate);
            double val = hrs > 0 ? net / hrs : 0.0;
            return new SimpleStringProperty(String.format(PT_BR, "R$ %,.2f/h", val));
        });
        colProfitPerKm.setCellValueFactory(cell -> {
            DailyRecord r = cell.getValue();
            if (r == null) return new SimpleStringProperty("");
            double km = r.calculateKm();
            double net = r.calculateNetProfit(currentMaintRate);
            double val = km > 0 ? net / km : 0.0;
            return new SimpleStringProperty(String.format(PT_BR, "R$ %,.2f/km", val));
        });

        // Fix #4: Usa getTableRow().getItem() em vez de getIndex() para evitar IndexOutOfBoundsException
        colActions.setCellFactory(param -> new TableCell<>() {
            private final Button btnEdit = new Button("✏️");
            private final Button btnDelete = new Button("🗑️");
            private final HBox container = new HBox(6, btnEdit, btnDelete);

            {
                btnEdit.getStyleClass().addAll("btn-secondary", "btn-sm");
                btnDelete.getStyleClass().addAll("btn-danger", "btn-sm");
                container.setAlignment(Pos.CENTER);

                btnEdit.setOnAction(event -> {
                    DailyRecord record = getTableRow() != null ? getTableRow().getItem() : null;
                    if (record != null) {
                        handleEditRecord(record);
                    }
                });

                btnDelete.setOnAction(event -> {
                    DailyRecord record = getTableRow() != null ? getTableRow().getItem() : null;
                    if (record != null) {
                        handleDeleteRecord(record);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(container);
                }
            }
        });

        filteredRecordList = new FilteredList<>(masterRecordList, p -> true);
        SortedList<DailyRecord> sortedList = new SortedList<>(filteredRecordList);
        sortedList.comparatorProperty().bind(tableRecords.comparatorProperty());
        tableRecords.setItems(sortedList);
    }

    private void setupSearchFilter() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> updateTableFilter());
    }

    private void loadData() {
        masterRecordList.setAll(recordDAO.findAll());
        refreshDashboard();
    }

    private void refreshDashboard() {
        updateTableFilter();
        updateKPICards();
        updateBarChart();

        tableRecords.refresh();
    }

    private LocalDate[] getSelectedDateRange() {
        String period = periodComboBox.getValue();
        LocalDate today = LocalDate.now();

        if ("Hoje".equals(period)) {
            return new LocalDate[]{today, today};
        } else if ("Esta Semana".equals(period)) {
            LocalDate start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate end = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            return new LocalDate[]{start, end};
        } else if ("Este Mês".equals(period)) {
            LocalDate start = today.withDayOfMonth(1);
            LocalDate end = today.with(TemporalAdjusters.lastDayOfMonth());
            return new LocalDate[]{start, end};
        } else if ("Personalizado".equals(period)) {
            LocalDate start = startDatePicker.getValue();
            LocalDate end = endDatePicker.getValue();
            if (start == null) start = LocalDate.MIN;
            if (end == null) end = LocalDate.MAX;
            return new LocalDate[]{start, end};
        }
        // "Todos os Tempos"
        return new LocalDate[]{null, null};
    }

    private void updateTableFilter() {
        LocalDate[] range = getSelectedDateRange();
        LocalDate start = range[0];
        LocalDate end = range[1];

        String query = searchField.getText() != null ? searchField.getText().toLowerCase().trim() : "";

        filteredRecordList.setPredicate(record -> {
            if (record == null) return false;

            // Date Filter
            if (start != null && end != null && record.getDate() != null) {
                if (record.getDate().isBefore(start) || record.getDate().isAfter(end)) {
                    return false;
                }
            }

            // Search Query Filter
            if (!query.isEmpty()) {
                String dateStr = record.getDate() != null ? record.getDate().format(DATE_FORMATTER).toLowerCase() : "";
                String notesStr = record.getNotes() != null ? record.getNotes().toLowerCase() : "";
                String grossStr = String.format(Locale.US, "%.2f", record.calculateGrossIncome());
                String netStr = String.format(Locale.US, "%.2f", record.calculateNetProfit(currentMaintRate));

                return dateStr.contains(query) || notesStr.contains(query) || grossStr.contains(query) || netStr.contains(query);
            }

            return true;
        });
    }

    private void updateKPICards() {
        double gross = 0.0;
        double costs = 0.0;
        double km = 0.0;
        double hours = 0.0;

        for (DailyRecord r : filteredRecordList) {
            if (r != null) {
                gross += r.calculateGrossIncome();
                costs += r.calculateTotalCosts();
                km += r.calculateKm();
                hours += r.calculateHours();
            }
        }

        double maint = km * currentMaintRate;
        double net = gross - costs - maint;
        double profitPerHour = hours > 0 ? net / hours : 0.0;
        double profitPerKm = km > 0 ? net / km : 0.0;

        lblGrossIncome.setText(String.format(PT_BR, "R$ %,.2f", gross));
        lblTotalCosts.setText(String.format(PT_BR, "R$ %,.2f", costs));
        lblMaintReserve.setText(String.format(PT_BR, "R$ %,.2f", maint));
        lblNetProfit.setText(String.format(PT_BR, "R$ %,.2f", net));
        lblProfitPerHour.setText(String.format(PT_BR, "R$ %,.2f / h", profitPerHour));
        lblProfitPerKm.setText(String.format(PT_BR, "R$ %,.2f / km", profitPerKm));
    }

    private void updateBarChart() {
        chartDailyEvolution.getData().clear();

        XYChart.Series<String, Number> seriesGross = new XYChart.Series<>();
        seriesGross.setName("Receita Bruta");

        XYChart.Series<String, Number> seriesCosts = new XYChart.Series<>();
        seriesCosts.setName("Custos Diretos");

        XYChart.Series<String, Number> seriesNet = new XYChart.Series<>();
        seriesNet.setName("Lucro Líquido");

        Map<LocalDate, List<DailyRecord>> grouped = new TreeMap<>();
        for (DailyRecord r : filteredRecordList) {
            if (r != null && r.getDate() != null) {
                grouped.computeIfAbsent(r.getDate(), k -> new ArrayList<>()).add(r);
            }
        }

        for (Map.Entry<LocalDate, List<DailyRecord>> entry : grouped.entrySet()) {
            String dateLabel = entry.getKey().format(SHORT_DATE_FORMATTER);
            double dayGross = 0.0;
            double dayCosts = 0.0;
            double dayKm = 0.0;

            for (DailyRecord r : entry.getValue()) {
                dayGross += r.calculateGrossIncome();
                dayCosts += r.calculateTotalCosts();
                dayKm += r.calculateKm();
            }

            double dayMaint = dayKm * currentMaintRate;
            double dayNet = dayGross - dayCosts - dayMaint;

            seriesGross.getData().add(new XYChart.Data<>(dateLabel, dayGross));
            seriesCosts.getData().add(new XYChart.Data<>(dateLabel, dayCosts));
            seriesNet.getData().add(new XYChart.Data<>(dateLabel, dayNet));
        }

        chartDailyEvolution.getData().addAll(seriesGross, seriesCosts, seriesNet);
    }

    @FXML
    private void handleNewRecord() {
        openRecordForm(null);
    }

    private void handleEditRecord(DailyRecord record) {
        if (record != null) {
            openRecordForm(record);
        }
    }

    private void openRecordForm(DailyRecord record) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/driverfin/view/record-form.fxml"));
            Parent root = loader.load();

            RecordFormController controller = loader.getController();
            controller.setRecord(record, currentMaintRate);

            Stage dialogStage = new Stage();
            dialogStage.setTitle(record == null ? "Novo Registro - DriverFin" : "Editar Registro - DriverFin");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/com/driverfin/css/theme-dark.css").toExternalForm());
            
            dialogStage.setScene(scene);
            dialogStage.showAndWait();

            if (controller.isSavedSuccessfully()) {
                loadData();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error opening record form dialog", e);
            showAlert(Alert.AlertType.ERROR, "Erro", "Não foi possível abrir o formulário: " + e.getMessage());
        }
    }

    private void handleDeleteRecord(DailyRecord record) {
        if (record == null || record.getId() == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar Exclusão");
        confirm.setHeaderText("Excluir registro do dia " + (record.getDate() != null ? record.getDate().format(DATE_FORMATTER) : "") + "?");
        confirm.setContentText("Esta ação não poderá ser desfeita.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            recordDAO.delete(record.getId());
            loadData();
        }
    }

    @FXML
    private void handleBackup() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Salvar Backup dos Dados");
        fileChooser.setInitialFileName("driverfin_backup_" + LocalDate.now().toString() + ".csv");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));

        Stage stage = (Stage) btnBackup.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                writer.println("ID;Data;HoraInicio;HoraFim;KMInicial;KMFinal;Uber;99;Outros;Combustivel;AlimentacaoOutros;Notas");
                for (DailyRecord r : masterRecordList) {
                    if (r == null) continue;
                    // Fix #16: Escapa ponto-e-vírgula e aspas dentro das notas para integridade do CSV
                    String safeNotes = r.getNotes() != null
                        ? r.getNotes().replace("\"", "\"\"").replace(";", ",")
                        : "";
                    writer.printf(Locale.US, "%d;%s;%s;%s;%.2f;%.2f;%.2f;%.2f;%.2f;%.2f;%.2f;\"%s\"%n",
                        r.getId() != null ? r.getId() : 0,
                        r.getDate() != null ? r.getDate().toString() : "",
                        r.getTimeStart() != null ? r.getTimeStart().toString() : "",
                        r.getTimeEnd() != null ? r.getTimeEnd().toString() : "",
                        r.getKmStart(),
                        r.getKmEnd(),
                        r.getEarnUber(),
                        r.getEarn99(),
                        r.getEarnOthers(),
                        r.getCostFuel(),
                        r.getCostFoodOther(),
                        safeNotes
                    );
                }
                showAlert(Alert.AlertType.INFORMATION, "Backup Concluído", "Dados exportados com sucesso para:\n" + file.getAbsolutePath());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error exporting CSV backup", e);
                showAlert(Alert.AlertType.ERROR, "Erro no Backup", "Falha ao exportar dados: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleExportPdf() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exportar Relatório PDF");
        fileChooser.setInitialFileName("relatorio_driverfin_" + LocalDate.now().toString() + ".pdf");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Documents (*.pdf)", "*.pdf"));

        Stage stage = (Stage) btnPdfReport.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            try {
                PdfReportExporter.exportReport(file, periodComboBox.getValue(), filteredRecordList, currentMaintRate);
                showAlert(Alert.AlertType.INFORMATION, "PDF Gerado", "Relatório PDF exportado com sucesso para:\n" + file.getAbsolutePath());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error generating PDF report", e);
                showAlert(Alert.AlertType.ERROR, "Erro no PDF", "Falha ao gerar relatório PDF: " + e.getMessage());
            }
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
