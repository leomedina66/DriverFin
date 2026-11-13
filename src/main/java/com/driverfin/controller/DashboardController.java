package com.driverfin.controller;

import com.driverfin.dao.DatabaseConnection;
import com.driverfin.dao.WorkShiftDAO;
import com.driverfin.model.DaySummary;
import com.driverfin.model.FinancialSummary;
import com.driverfin.model.WorkShift;
import com.driverfin.service.SummaryService;
import com.driverfin.util.CurrencyFormat;
import com.driverfin.util.DashboardTexts;
import com.driverfin.util.DatabaseBackup;
import com.driverfin.util.DatabaseTargetException;
import com.driverfin.util.Dialogs;
import com.driverfin.util.FormMessages;
import com.driverfin.util.MaintenanceRate;
import com.driverfin.util.PdfReportExporter;
import com.driverfin.util.ShiftFilter;
import com.driverfin.util.ShiftFilter.Period;
import com.driverfin.util.ShiftMetrics;
import com.driverfin.util.TargetInUseException;
import com.driverfin.util.Styles;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.DateCell;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Callback;
import javafx.util.Duration;

import java.io.File;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

public class DashboardController {
    private static final Logger LOGGER = Logger.getLogger(DashboardController.class.getName());
    private static final String PREF_MAINT_RATE = "maintenance_rate";
    private static final String ERROR_STYLE_CLASS = "input-error";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter CHART_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yy");
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String MUTED_STYLE_CLASS = "text-muted";
    private static final String PROFIT_STYLE_CLASS = "today-profit";
    private static final String LOSS_STYLE_CLASS = "today-profit-negative";
    private static final double STATUS_SECONDS = 4;
    private static final String PRIMARY_BUTTON_CLASS = "btn-primary";
    /** Outline look: the header button when the card or the empty state holds the filled main button. */
    private static final String SECONDARY_BUTTON_CLASS = "btn-outline";

    @FXML private ComboBox<Period> periodComboBox;
    @FXML private DatePicker startDatePicker, endDatePicker;
    @FXML private TextField searchField, maintRateField;
    @FXML private Label lblGrossIncome, lblTotalCosts, lblMaintReserve, lblNetProfit;
    @FXML private Label lblProfitPerHour, lblProfitPerKm, lblGrossPerHour, lblGrossPerKm;
    @FXML private TableView<WorkShift> tableRecords;
    @FXML private TableColumn<WorkShift, LocalDate> colDate;
    @FXML private TableColumn<WorkShift, Double> colHours, colKm;
    @FXML private TableColumn<WorkShift, BigDecimal> colGross, colCosts, colMaint, colNet;
    @FXML private TableColumn<WorkShift, BigDecimal> colProfitPerHour, colProfitPerKm, colGrossPerHour, colGrossPerKm;
    @FXML private TableColumn<WorkShift, Void> colActions;
    @FXML private BarChart<String, Number> chartDailyEvolution;
    @FXML private Label lblChartEmpty;
    @FXML private Node todayCard, todayValuesBox, emptyStateBox, filtersBar, maintBar, kpiGrid, chartCard, tableCard;
    @FXML private Label lblTodayTitle, lblTodayProfit, lblTodayHours, lblTodayGrossPerHour, lblTodayEmptyText;
    @FXML private Label lblTodayGross;
    @FXML private Label emptyStateTitle, emptyStateText;
    @FXML private Label lblStatus, lblMaintHelp, lblMaintError, lblMaintSubtext, lblShortcutsHint;
    @FXML private Button btnTodayNew, btnEmptyNew, btnNewRecord;

    private final WorkShiftDAO dao;
    private final DatabaseConnection dbConn;
    private final Preferences prefs;
    private final ObservableList<WorkShift> masterList = FXCollections.observableArrayList();
    private final FilteredList<WorkShift> filteredList = new FilteredList<>(masterList);
    private final Label tablePlaceholder = new Label();
    private final Tooltip endBeforeStartTip = new Tooltip("A data final não pode ser anterior à data inicial.");
    private final PauseTransition statusTimer = new PauseTransition(Duration.seconds(STATUS_SECONDS));
    private final Clock clock;
    /** Checks the date again every minute, so the "today" card changes at midnight with the window in focus. */
    private final Timeline dayWatch = new Timeline(new KeyFrame(Duration.minutes(1), e -> refreshDayIfChanged()));
    /** The rate the screen shows now: it follows what the user types, as long as the text is a valid rate. */
    private BigDecimal currentMaintRate = MaintenanceRate.DEFAULT;
    /** The last rate the user confirmed (Enter or leaving the field), the one stored in the preferences. */
    private BigDecimal committedMaintRate = MaintenanceRate.DEFAULT;
    /** The day the "today" card shows; when the clock passes midnight with the app open it becomes outdated. */
    private LocalDate shownDay;
    /** Asks the user to confirm a deletion; replaced in tests, because a dialog would block them. */
    private Predicate<WorkShift> deleteConfirmer = this::confirmDelete;

    public DashboardController(WorkShiftDAO dao, DatabaseConnection dbConn) {
        this(dao, dbConn, Preferences.userNodeForPackage(DashboardController.class));
    }

    /** Same controller with another preferences node, so tests do not touch the real stored rate. */
    DashboardController(WorkShiftDAO dao, DatabaseConnection dbConn, Preferences prefs) {
        this(dao, dbConn, prefs, Clock.systemDefaultZone());
    }

    /** Same, with the clock that says what day it is, so tests can move time forward. */
    DashboardController(WorkShiftDAO dao, DatabaseConnection dbConn, Preferences prefs, Clock clock) {
        this.dao = dao;
        this.dbConn = dbConn;
        this.prefs = prefs;
        this.clock = clock;
        dayWatch.setCycleCount(Timeline.INDEFINITE);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    void setDeleteConfirmer(Predicate<WorkShift> confirmer) {
        this.deleteConfirmer = confirmer;
    }

    @FXML
    public void initialize() {
        committedMaintRate = readStoredMaintRate();
        currentMaintRate = committedMaintRate;
        maintRateField.setText(MaintenanceRate.format(currentMaintRate));
        setStaticTexts();

        setupTable();
        setupFilters();
        setupMaintRateField();
        setupKeyboard();
        statusTimer.setOnFinished(e -> setVisibleAndManaged(lblStatus, false));

        loadData();
        DashboardShortcuts.whenSceneAvailable(tableRecords, this::onSceneAvailable);
    }

    private void setStaticTexts() {
        btnTodayNew.setText(DashboardTexts.TODAY_NEW_BUTTON);
        lblTodayEmptyText.setText(DashboardTexts.TODAY_EMPTY);
        lblMaintHelp.setText(DashboardTexts.MAINT_HELP);
        lblMaintError.setText(DashboardTexts.MAINT_INVALID);
        lblShortcutsHint.setText(DashboardTexts.SHORTCUTS_HINT);
    }

    private void setupKeyboard() {
        DashboardShortcuts.install(tableRecords, this::handleNewRecord, this::handleBackup, this::handleExportPdf,
                this::focusSearch);
        tableRecords.addEventFilter(KeyEvent.KEY_PRESSED, this::handleTableKey);
        searchField.addEventFilter(KeyEvent.KEY_PRESSED, this::handleSearchKey);
    }

    private void onSceneAvailable(Scene scene) {
        watchWindowFocus(scene);
        dayWatch.play();
        Platform.runLater(this::focusMain);
    }

    /**
     * The day can change while the app stays open: when the window gets the focus back, and every minute while it
     * is open, check the date.
     */
    private void watchWindowFocus(Scene scene) {
        ChangeListener<Boolean> onFocus = (obs, wasFocused, focused) -> {
            if (focused) refreshDayIfChanged();
        };
        scene.windowProperty().addListener((obs, oldWindow, newWindow) -> {
            if (oldWindow != null) oldWindow.focusedProperty().removeListener(onFocus);
            if (newWindow != null) newWindow.focusedProperty().addListener(onFocus);
        });
        Window window = scene.getWindow();
        if (window != null) window.focusedProperty().addListener(onFocus);
    }

    /** When the day changed since the screen was last drawn, draws it again (the "today" card and the filters). */
    void refreshDayIfChanged() {
        if (!today().equals(shownDay)) applyFilters();
    }

    /** Enter edits the selected shift, Delete removes it; both only while the table itself has the focus. */
    private void handleTableKey(KeyEvent event) {
        if (event.getTarget() != tableRecords || event.isControlDown() || event.isAltDown() || event.isMetaDown()) return;
        WorkShift selected = tableRecords.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        if (event.getCode() == KeyCode.ENTER) {
            event.consume();
            handleEdit(selected);
        } else if (event.getCode() == KeyCode.DELETE) {
            event.consume();
            handleDelete(selected);
        }
    }

    /** Esc clears the search; when it is already empty, it gives the focus back to the table. */
    private void handleSearchKey(KeyEvent event) {
        if (event.getCode() != KeyCode.ESCAPE) return;
        event.consume();
        if (searchField.getText().isEmpty()) {
            tableRecords.requestFocus();
        } else {
            searchField.clear();
        }
    }

    private void focusSearch() {
        if (!filtersBar.isVisible()) return;
        searchField.requestFocus();
        searchField.selectAll();
    }

    /** First thing the user can act on: the big button of the empty state, otherwise the table. */
    private void focusMain() {
        (emptyStateBox.isVisible() && btnEmptyNew.isVisible() ? btnEmptyNew : tableRecords).requestFocus();
    }

    private void showStatus(String message) {
        lblStatus.setText(message);
        setVisibleAndManaged(lblStatus, true);
        statusTimer.playFromStart();
    }

    private BigDecimal readStoredMaintRate() {
        String stored = prefs.get(PREF_MAINT_RATE, MaintenanceRate.DEFAULT.toPlainString());
        return MaintenanceRate.parse(stored).orElseGet(() -> {
            LOGGER.log(Level.WARNING, "Invalid stored maintenance rate ''{0}''; using default {1}.",
                    new Object[]{stored, MaintenanceRate.DEFAULT});
            return MaintenanceRate.DEFAULT;
        });
    }

    private void setupTable() {
        colDate.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getDate()));
        colDate.setCellFactory(formattedCell(date -> date.format(DATE_FORMAT)));
        colDate.setSortType(TableColumn.SortType.DESCENDING);

        bindColumn(colHours, WorkShift::calculateHours, CurrencyFormat::oneDecimal);
        bindColumn(colKm, WorkShift::calculateKm, CurrencyFormat::oneDecimal);
        bindMoneyColumn(colGross, WorkShift::getGrossIncome);
        bindMoneyColumn(colCosts, WorkShift::getTotalCosts);
        bindMoneyColumn(colMaint, ws -> ws.calculateMaintReserve(currentMaintRate));
        bindProfitColumn(colNet, ws -> ws.calculateNetProfit(currentMaintRate));
        bindProfitColumn(colProfitPerHour, ws -> ShiftMetrics.netProfitPerHour(ws, currentMaintRate));
        bindProfitColumn(colProfitPerKm, ws -> ShiftMetrics.netProfitPerKm(ws, currentMaintRate));
        bindMoneyColumn(colGrossPerHour, WorkShift::calculateGrossPerHour);
        bindMoneyColumn(colGrossPerKm, WorkShift::calculateGrossPerKm);

        colActions.setCellFactory(param -> new TableCell<>() {
            private final Button btnEdit = new Button("Editar");
            private final Button btnDelete = new Button("Excluir");
            private final HBox pane = new HBox(5, btnEdit, btnDelete);

            {
                btnEdit.getStyleClass().addAll("btn-outline", "btn-sm");
                btnDelete.getStyleClass().addAll("btn-danger", "btn-sm");
                btnEdit.setOnAction(e -> handleEdit(getTableRow().getItem()));
                btnDelete.setOnAction(e -> handleDelete(getTableRow().getItem()));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : pane);
            }
        });

        SortedList<WorkShift> sortedList = new SortedList<>(filteredList);
        sortedList.comparatorProperty().bind(tableRecords.comparatorProperty());
        tablePlaceholder.getStyleClass().add(MUTED_STYLE_CLASS);
        tableRecords.setPlaceholder(tablePlaceholder);
        tableRecords.setItems(sortedList);
        tableRecords.getSortOrder().add(colDate);
    }

    private <T> void bindColumn(TableColumn<WorkShift, T> column, Function<WorkShift, T> value, Function<T, String> format) {
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        column.setCellFactory(formattedCell(format));
    }

    private void bindMoneyColumn(TableColumn<WorkShift, BigDecimal> column, Function<WorkShift, BigDecimal> value) {
        bindColumn(column, value, CurrencyFormat::money);
    }

    /** A profit column: money text, and the loss style class while the cell shows a loss (never accumulated). */
    private void bindProfitColumn(TableColumn<WorkShift, BigDecimal> column, Function<WorkShift, BigDecimal> value) {
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                boolean show = !empty && item != null;
                setText(show ? CurrencyFormat.money(item) : null);
                Styles.markNegative(this, show && CurrencyFormat.isLoss(item));
            }
        });
    }

    private static <T> Callback<TableColumn<WorkShift, T>, TableCell<WorkShift, T>> formattedCell(Function<T, String> format) {
        return column -> new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : format.apply(item));
            }
        };
    }

    private void setupFilters() {
        periodComboBox.setItems(FXCollections.observableArrayList(Period.values()));
        periodComboBox.setValue(Period.ALL);
        periodComboBox.valueProperty().addListener((obs, old, val) -> {
            boolean custom = val == Period.CUSTOM;
            setVisibleAndManaged(startDatePicker, custom);
            setVisibleAndManaged(endDatePicker, custom);
            applyFilters();
        });
        endDatePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                LocalDate start = startDatePicker.getValue();
                setDisable(!empty && start != null && date.isBefore(start));
            }
        });
        startDatePicker.valueProperty().addListener((obs, old, val) -> applyFilters());
        endDatePicker.valueProperty().addListener((obs, old, val) -> applyFilters());
        searchField.textProperty().addListener((obs, old, val) -> applyFilters());
    }

    private static void setVisibleAndManaged(Node node, boolean show) {
        node.setVisible(show);
        node.setManaged(show);
    }

    private void setupMaintRateField() {
        maintRateField.textProperty().addListener((obs, old, val) -> {
            var parsed = MaintenanceRate.parse(val);
            setInputError(maintRateField, parsed.isEmpty());
            parsed.ifPresent(rate -> {
                setVisibleAndManaged(lblMaintError, false);
                useMaintRate(rate);
            });
        });
        // The rate is stored only when the user is done typing, not on every key.
        maintRateField.focusedProperty().addListener((obs, wasFocused, focused) -> {
            if (!focused) commitMaintRate();
        });
        maintRateField.setOnAction(e -> commitMaintRate());
    }

    /**
     * Done typing: a readable value becomes the confirmed rate, is shown with two decimals and stored. An unreadable
     * one goes back to the confirmed rate (not to a number typed on the way, like the "20" of "200"), the screen
     * is drawn again with it, and a short message is shown next to the field.
     */
    private void commitMaintRate() {
        Optional<BigDecimal> typed = MaintenanceRate.parse(maintRateField.getText());
        typed.ifPresent(rate -> committedMaintRate = MaintenanceRate.rounded(rate));
        useMaintRate(committedMaintRate);
        maintRateField.setText(MaintenanceRate.format(committedMaintRate));
        setVisibleAndManaged(lblMaintError, typed.isEmpty());
        prefs.put(PREF_MAINT_RATE, committedMaintRate.toPlainString());
    }

    /** Makes the screen use this rate: summary, table, chart and the sort of a column that depends on it. */
    private void useMaintRate(BigDecimal rate) {
        if (currentMaintRate.compareTo(rate) == 0) return;
        currentMaintRate = rate;
        applyFilters();
        // Calculated columns changed, so a table sorted by one of them must be sorted again.
        tableRecords.sort();
    }

    private static void markInvalid(Control control, boolean invalid, Tooltip errorTip) {
        setInputError(control, invalid);
        control.setTooltip(invalid ? errorTip : null);
    }

    private static void setInputError(Control control, boolean invalid) {
        control.getStyleClass().remove(ERROR_STYLE_CLASS);
        if (invalid) control.getStyleClass().add(ERROR_STYLE_CLASS);
    }

    /** Reloads every shift from the database. Returns false (after telling the user) when that failed. */
    private boolean loadData() {
        try {
            masterList.setAll(dao.findAll());
            updateEmptyState(false);
            applyFilters();
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "DB Load Error", e);
            // Nothing loaded: the box on the screen already says so (the welcome text would claim there is no data).
            boolean nothingToShow = masterList.isEmpty();
            if (!nothingToShow) {
                Dialogs.show(Alert.AlertType.ERROR, "Erro de Banco de Dados", "Não foi possível carregar os turnos. Tente novamente.");
            }
            // The screen must not stay half drawn: show whatever list is loaded.
            updateEmptyState(nothingToShow);
            applyFilters();
            return false;
        }
    }

    /**
     * With no shift in the database only the welcome box is shown; everything else would be zeros and empty boxes.
     * When the shifts could not be read, the same box gives a neutral error text and no "new shift" button instead.
     */
    private void updateEmptyState(boolean loadFailed) {
        emptyStateTitle.setText(loadFailed ? DashboardTexts.LOAD_FAILED_TITLE : DashboardTexts.EMPTY_TITLE);
        emptyStateText.setText(loadFailed ? DashboardTexts.LOAD_FAILED_TEXT : DashboardTexts.EMPTY_TEXT);
        setVisibleAndManaged(btnEmptyNew, !loadFailed);
        boolean empty = masterList.isEmpty();
        setVisibleAndManaged(emptyStateBox, empty);
        for (Node section : List.of(todayCard, filtersBar, maintBar, kpiGrid, chartCard, tableCard)) {
            setVisibleAndManaged(section, !empty);
        }
    }

    private void applyFilters() {
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();
        markInvalid(endDatePicker, start != null && end != null && end.isBefore(start), endBeforeStartTip);
        filteredList.setPredicate(ShiftFilter.build(periodComboBox.getValue(), start, end,
                searchField.getText(), today()));
        tablePlaceholder.setText(DashboardTexts.tablePlaceholder(searchField.getText()));
        updateDashboard();
    }

    private void updateDashboard() {
        FinancialSummary summary = SummaryService.calculate(filteredList, currentMaintRate);
        lblGrossIncome.setText(CurrencyFormat.money(summary.totalGross()));
        lblTotalCosts.setText(CurrencyFormat.money(summary.totalCosts()));
        lblMaintReserve.setText(CurrencyFormat.money(summary.totalMaint()));
        lblNetProfit.setText(CurrencyFormat.money(summary.totalNet()));
        Styles.markNegative(lblNetProfit, CurrencyFormat.isLoss(summary.totalNet()));
        lblProfitPerHour.setText(CurrencyFormat.perHour(summary.avgProfitPerHour()));
        Styles.markNegative(lblProfitPerHour, CurrencyFormat.isLoss(summary.avgProfitPerHour()));
        lblProfitPerKm.setText(CurrencyFormat.perKm(summary.avgProfitPerKm()));
        Styles.markNegative(lblProfitPerKm, CurrencyFormat.isLoss(summary.avgProfitPerKm()));
        lblGrossPerHour.setText(CurrencyFormat.perHour(summary.avgGrossPerHour()));
        lblGrossPerKm.setText(CurrencyFormat.perKm(summary.avgGrossPerKm()));
        lblMaintSubtext.setText(MaintenanceRate.describe(currentMaintRate));
        tableRecords.refresh();
        updateChart();
        updateToday();
    }

    /** Today's card: all shifts dated today, whatever the period filter or the search say. */
    private void updateToday() {
        shownDay = today();
        lblTodayTitle.setText(DashboardTexts.todayTitle(shownDay));
        DaySummary day = SummaryService.forDay(masterList, shownDay, currentMaintRate);
        boolean hasShifts = day.shiftCount() > 0;
        setVisibleAndManaged(todayValuesBox, hasShifts);
        setVisibleAndManaged(lblTodayEmptyText, !hasShifts);
        setVisibleAndManaged(btnTodayNew, !hasShifts);
        setVisibleAndManaged(lblTodayGross, hasShifts);
        updateNewShiftButtonStyle();
        if (!hasShifts) return;

        BigDecimal net = day.totals().totalNet();
        lblTodayProfit.setText(DashboardTexts.profit(net));
        lblTodayProfit.getStyleClass().removeAll(PROFIT_STYLE_CLASS, LOSS_STYLE_CLASS);
        lblTodayProfit.getStyleClass().add(net.signum() < 0 ? LOSS_STYLE_CLASS : PROFIT_STYLE_CLASS);
        Styles.markNegative(lblTodayProfit, CurrencyFormat.isLoss(net));
        lblTodayHours.setText(DashboardTexts.hours(day.totals().totalHours()));
        lblTodayGrossPerHour.setText(DashboardTexts.grossPerHour(day));
        lblTodayGross.setText(DashboardTexts.todaySummary(day.totals().totalGross(), day.shiftCount()));
    }

    /** One main action per screen: the header button is secondary while the card or the empty state has its own. */
    private void updateNewShiftButtonStyle() {
        boolean otherPrimaryShown = (emptyStateBox.isVisible() && btnEmptyNew.isVisible())
                || (todayCard.isVisible() && btnTodayNew.isVisible());
        btnNewRecord.getStyleClass().removeAll(PRIMARY_BUTTON_CLASS, SECONDARY_BUTTON_CLASS);
        btnNewRecord.getStyleClass().add(otherPrimaryShown ? SECONDARY_BUTTON_CLASS : PRIMARY_BUTTON_CLASS);
    }

    private void updateChart() {
        Map<LocalDate, BigDecimal[]> byDay = new TreeMap<>();
        for (WorkShift ws : filteredList) {
            BigDecimal[] totals = byDay.computeIfAbsent(ws.getDate(),
                    d -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            totals[0] = totals[0].add(ws.getGrossIncome());
            totals[1] = totals[1].add(ws.getTotalCosts());
            totals[2] = totals[2].add(ws.calculateNetProfit(currentMaintRate));
        }

        XYChart.Series<String, Number> revenue = new XYChart.Series<>();
        revenue.setName("Faturamento");
        XYChart.Series<String, Number> costs = new XYChart.Series<>();
        costs.setName("Custos");
        XYChart.Series<String, Number> profit = new XYChart.Series<>();
        profit.setName("Lucro");
        for (Map.Entry<LocalDate, BigDecimal[]> day : byDay.entrySet()) {
            // Includes the year: "dd/MM" would merge the same day of different years into one category.
            String label = day.getKey().format(CHART_DATE_FORMAT);
            revenue.getData().add(new XYChart.Data<>(label, day.getValue()[0]));
            costs.getData().add(new XYChart.Data<>(label, day.getValue()[1]));
            profit.getData().add(new XYChart.Data<>(label, day.getValue()[2]));
        }
        chartDailyEvolution.getData().setAll(List.of(revenue, costs, profit));

        boolean hasData = !byDay.isEmpty();
        setVisibleAndManaged(chartDailyEvolution, hasData);
        setVisibleAndManaged(lblChartEmpty, !hasData);
    }

    @FXML private void handleNewRecord() { openForm(null); }

    private void handleEdit(WorkShift ws) {
        if (ws != null) openForm(ws);
    }

    private void openForm(WorkShift ws) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/driverfin/view/record-form.fxml"));
            Parent root = loader.load();
            RecordFormController controller = loader.getController();
            controller.initData(ws, currentMaintRate, dao);

            Scene scene = new Scene(root);
            Styles.apply(scene);
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(ws == null ? FormMessages.TITLE_NEW : FormMessages.TITLE_EDIT);
            stage.setScene(scene);
            stage.showAndWait();

            controller.getSavedShift().ifPresent(this::showSavedShift);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Form Error", e);
            Dialogs.show(Alert.AlertType.ERROR, "Erro", "Não foi possível abrir o formulário. Tente novamente.");
        }
    }

    /** After the form saved a shift: reload, select and show that row, and say so. */
    void showSavedShift(WorkShift saved) {
        if (!loadData()) return;
        tableRecords.getSelectionModel().clearSelection();
        for (WorkShift row : tableRecords.getItems()) {
            // When the filter hides the saved shift there is no row to select.
            if (Objects.equals(row.getId(), saved.getId())) selectRow(row);
        }
        focusMain();
        showStatus(DashboardTexts.SAVED);
    }

    private void selectRow(WorkShift row) {
        tableRecords.getSelectionModel().select(row);
        tableRecords.scrollTo(row);
    }

    void handleDelete(WorkShift ws) {
        if (ws == null || !deleteConfirmer.test(ws)) return;
        int deletedIndex = tableRecords.getItems().indexOf(ws);
        try {
            dao.delete(ws.getId());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Delete Error", e);
            Dialogs.show(Alert.AlertType.ERROR, "Erro",
                    "Não foi possível excluir o turno. Tente novamente; se continuar, reinicie o aplicativo.");
            return;
        }
        if (!loadData()) return;
        selectRowNear(deletedIndex);
        focusMain();
        showStatus(DashboardTexts.DELETED);
    }

    /** The row that took the place of the deleted one (the next), or the last one when the deleted row was last. */
    private void selectRowNear(int deletedIndex) {
        int rows = tableRecords.getItems().size();
        if (rows == 0 || deletedIndex < 0) return;
        selectRow(tableRecords.getItems().get(Math.min(deletedIndex, rows - 1)));
    }

    /** Asks for confirmation; "Cancelar" is the default button, so pressing Enter never deletes. */
    private boolean confirmDelete(WorkShift ws) {
        ButtonType delete = new ButtonType("Excluir", ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Cancelar", ButtonData.CANCEL_CLOSE);
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                DashboardTexts.deleteConfirmText(ws, currentMaintRate), delete, cancel);
        confirm.setTitle(DashboardTexts.DELETE_TITLE);
        confirm.setHeaderText(null);
        Styles.apply(confirm.getDialogPane());
        ((Button) confirm.getDialogPane().lookupButton(delete)).setDefaultButton(false);
        ((Button) confirm.getDialogPane().lookupButton(cancel)).setDefaultButton(true);
        return confirm.showAndWait().orElse(cancel) == delete;
    }

    @FXML private void handleBackup() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite DB", "*.db"));
        File dest = fc.showSaveDialog(tableRecords.getScene().getWindow());
        if (dest == null) return;
        try {
            DatabaseBackup.create(dbConn, dest.toPath());
            Dialogs.show(Alert.AlertType.INFORMATION, "Backup concluído", "Backup salvo em " + dest.getAbsolutePath() + ".");
        } catch (DatabaseTargetException e) {
            Dialogs.show(Alert.AlertType.WARNING, "Backup não realizado", DatabaseTargetException.USER_MESSAGE);
        } catch (TargetInUseException e) {
            LOGGER.log(Level.WARNING, "Backup target cannot be replaced", e);
            Dialogs.show(Alert.AlertType.WARNING, "Backup não realizado", TargetInUseException.USER_MESSAGE);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Backup Error", e);
            Dialogs.show(Alert.AlertType.ERROR, "Erro",
                    "Não foi possível fazer o backup. Escolha outra pasta e tente novamente.");
        }
    }

    @FXML private void handleExportPdf() {
        List<WorkShift> shifts = new ArrayList<>(filteredList);
        if (shifts.isEmpty()) {
            Dialogs.show(Alert.AlertType.INFORMATION, "Sem dados", "Não há turnos no período selecionado para exportar.");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        fc.setInitialFileName("relatorio-" + today().format(FILE_DATE_FORMAT) + ".pdf");
        File dest = fc.showSaveDialog(tableRecords.getScene().getWindow());
        if (dest == null) return;
        try {
            String period = ShiftFilter.describe(periodComboBox.getValue(), startDatePicker.getValue(), endDatePicker.getValue());
            PdfReportExporter.exportReport(dest, period, shifts, currentMaintRate, dbConn.getDatabasePath());
            Dialogs.show(Alert.AlertType.INFORMATION, "Relatório gerado", "Relatório salvo em " + dest.getAbsolutePath() + ".");
        } catch (DatabaseTargetException e) {
            Dialogs.show(Alert.AlertType.WARNING, "Relatório não gerado", DatabaseTargetException.USER_MESSAGE);
        } catch (TargetInUseException e) {
            LOGGER.log(Level.WARNING, "Report target cannot be replaced", e);
            Dialogs.show(Alert.AlertType.WARNING, "Relatório não gerado", TargetInUseException.USER_MESSAGE);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "PDF Export Error", e);
            Dialogs.show(Alert.AlertType.ERROR, "Erro",
                    "Não foi possível gerar o relatório PDF. Escolha outra pasta e tente novamente.");
        }
    }
}
