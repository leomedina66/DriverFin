package com.driverfin.controller;

import com.driverfin.dao.WorkShiftDAO;
import com.driverfin.model.WorkShift;
import com.driverfin.util.CurrencyFormat;
import com.driverfin.util.Dialogs;
import com.driverfin.util.FieldError;
import com.driverfin.util.FieldFocus;
import com.driverfin.util.FieldMessage;
import com.driverfin.util.FieldValidationException;
import com.driverfin.util.FormMessages;
import com.driverfin.util.KmInput;
import com.driverfin.util.KmMask;
import com.driverfin.util.MaintenanceRate;
import com.driverfin.util.MaskResult;
import com.driverfin.util.MoneyInput;
import com.driverfin.util.MoneyMask;
import com.driverfin.util.ShiftField;
import com.driverfin.util.ShiftFormCheck;
import com.driverfin.util.ShiftFormInput;
import com.driverfin.util.ShiftWarnings;
import com.driverfin.util.Styles;
import com.driverfin.util.TimeInput;
import com.driverfin.util.TimeMask;
import com.driverfin.util.WorkShiftValidator;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class RecordFormController {
    private static final Logger LOGGER = Logger.getLogger(RecordFormController.class.getName());
    private static final String UNAVAILABLE = "—";
    private static final String SAVE_FAILED_TEXT =
            "Não foi possível salvar o turno. Tente novamente; se continuar, reinicie o aplicativo.";
    private static final DateTimeFormatter DATE_SHOWN = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    /** Day and month of one or two digits, year of exactly four digits, no sign: "+10000" and "-0005" are not dates. */
    private static final DateTimeFormatter DATE_READ = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE).appendLiteral('/')
            .appendValue(ChronoField.MONTH_OF_YEAR, 1, 2, SignStyle.NOT_NEGATIVE).appendLiteral('/')
            .appendValue(ChronoField.YEAR, 4)
            .toFormatter().withResolverStyle(ResolverStyle.STRICT);
    private static final int FIRST_CALENDAR_YEAR = 1;
    /** Fields whose message comes from {@link ShiftFormCheck} (money fields only show mask messages). */
    private static final Set<ShiftField> CHECKED_FIELDS = EnumSet.of(ShiftField.DATE, ShiftField.TIME_START,
            ShiftField.TIME_END, ShiftField.KM_START, ShiftField.KM_END, ShiftField.NOTES);

    private static final Set<ShiftField> MONEY_FIELDS = EnumSet.of(ShiftField.EARN_UBER, ShiftField.EARN_99,
            ShiftField.EARN_OTHERS, ShiftField.COST_FUEL, ShiftField.COST_FOOD_OTHER);

    @FXML private Label lblFormTitle;
    @FXML private Label lblCalcHours, lblCalcKm, lblCalcGross, lblCalcCosts, lblCalcMaint, lblCalcNet;
    @FXML private DatePicker datePicker;
    @FXML private TextField timeStartField, timeEndField, kmStartField, kmEndField;
    @FXML private TextField earnUberField, earn99Field, earnOthersField, costFuelField, costFoodOtherField;
    @FXML private TextArea notesArea;
    @FXML private Label dateError, timeStartError, timeEndError, kmStartError, kmEndError, kmStartHint, notesError;
    @FXML private Label earnUberMsg, earn99Msg, earnOthersMsg, costFuelMsg, costFoodOtherMsg;
    @FXML private Label moneyHint, turnNote, rateWarn;
    @FXML private Button btnSave, btnCancel;
    @FXML private ScrollPane formScroll;

    private final FieldFeedback feedback = new FieldFeedback();
    private final Map<ShiftField, Control> controls = new EnumMap<>(ShiftField.class);
    private final Map<ShiftField, Label> messageLabels = new EnumMap<>(ShiftField.class);
    /** Fields the user changed at least once: only those show an error when they lose focus. */
    private final Set<ShiftField> touched = EnumSet.noneOf(ShiftField.class);
    /** Fields whose message on screen comes from the form checks (it follows the text while the user types). */
    private final Set<ShiftField> showingCheckError = EnumSet.noneOf(ShiftField.class);

    private WorkShift currentShift;
    private BigDecimal maintRate = MaintenanceRate.DEFAULT;
    private WorkShiftDAO dao;
    private WorkShift savedShift;
    /** True while the form fills its own fields: those changes do not count as the user touching them. */
    private boolean filling = false;
    /** Texts of the fields at the end of initData: "changed" for the discard question means different from these. */
    private List<String> openingTexts = List.of();
    /** The latest transient message of each money mask (comma hint, maximum), combined with the high-value warning. */
    private final Map<ShiftField, FieldMessage> moneyMaskMessages = new EnumMap<>(ShiftField.class);
    private boolean kmSuggestionShown = false;
    private boolean keyboardInstalled = false;
    private BooleanSupplier discardAsker = this::askToDiscard;
    private Runnable closer = this::closeStage;

    @FXML
    public void initialize() {
        registerFields();
        datePicker.setConverter(newDateConverter());
        installMasks();
        watchCheckedFields();
        for (ShiftField field : MONEY_FIELDS) {
            ((TextField) controls.get(field)).textProperty().addListener((obs, old, val) -> {
                refreshMoneyMessage(field);
                updateCalculations();
            });
        }
        FormKeyboard.leaveWithTab(notesArea, btnCancel, costFoodOtherField, this::focusAndReveal);
        installKeyboardWhenSceneExists();
        updateCalculations();
    }

    public void initData(WorkShift ws, BigDecimal maintRate, WorkShiftDAO dao) {
        this.currentShift = ws;
        this.maintRate = maintRate;
        this.dao = dao;

        filling = true;
        try {
            if (ws != null) {
                fillFrom(ws);
            } else {
                lblFormTitle.setText(FormMessages.TITLE_NEW);
                datePicker.setValue(LocalDate.now());
                suggestKmStart();
            }
        } finally {
            filling = false;
        }
        touched.clear();
        showingCheckError.clear();
        clearCheckedMessages();
        updateCalculations();
        openingTexts = currentTexts();
        // New shift: the date is already today, so the first thing to type is the start time. Edit: from the top.
        Control firstFocus = ws == null ? timeStartField : datePicker;
        Platform.runLater(firstFocus::requestFocus);
    }

    public Optional<WorkShift> getSavedShift() {
        return Optional.ofNullable(savedShift);
    }

    public boolean isSaved() {
        return savedShift != null;
    }

    /** DatePicker converter that gives null (no date) when the typed text cannot be read, instead of throwing. */
    static StringConverter<LocalDate> newDateConverter() {
        return new StringConverter<>() {
            @Override public String toString(LocalDate date) {
                return date == null ? "" : DATE_SHOWN.format(date);
            }

            @Override public LocalDate fromString(String text) {
                if (text == null || text.isBlank()) return null;
                try {
                    LocalDate date = LocalDate.parse(text.trim(), DATE_READ);
                    return date.getYear() < FIRST_CALENDAR_YEAR ? null : date;
                } catch (DateTimeParseException unreadable) {
                    return null;
                }
            }
        };
    }

    // ---- wiring -------------------------------------------------------------------------------------------

    private void registerFields() {
        register(ShiftField.DATE, datePicker, dateError);
        register(ShiftField.TIME_START, timeStartField, timeStartError);
        register(ShiftField.TIME_END, timeEndField, timeEndError);
        register(ShiftField.KM_START, kmStartField, kmStartError);
        register(ShiftField.KM_END, kmEndField, kmEndError);
        register(ShiftField.EARN_UBER, earnUberField, earnUberMsg);
        register(ShiftField.EARN_99, earn99Field, earn99Msg);
        register(ShiftField.EARN_OTHERS, earnOthersField, earnOthersMsg);
        register(ShiftField.COST_FUEL, costFuelField, costFuelMsg);
        register(ShiftField.COST_FOOD_OTHER, costFoodOtherField, costFoodOtherMsg);
        register(ShiftField.NOTES, notesArea, notesError);
    }

    private void register(ShiftField field, Control control, Label message) {
        controls.put(field, control);
        messageLabels.put(field, message);
    }

    private void installMasks() {
        TimeInput.install(timeStartField, maskMessages(ShiftField.TIME_START));
        TimeInput.install(timeEndField, maskMessages(ShiftField.TIME_END));
        KmInput.install(kmStartField, maskMessages(ShiftField.KM_START));
        KmInput.install(kmEndField, maskMessages(ShiftField.KM_END));
        MoneyInput.install(earnUberField, moneyMessages(ShiftField.EARN_UBER));
        MoneyInput.install(earn99Field, moneyMessages(ShiftField.EARN_99));
        MoneyInput.install(earnOthersField, moneyMessages(ShiftField.EARN_OTHERS));
        MoneyInput.install(costFuelField, moneyMessages(ShiftField.COST_FUEL));
        MoneyInput.install(costFoodOtherField, moneyMessages(ShiftField.COST_FOOD_OTHER));
    }

    /** Money masks only send transient messages; the field shows the strongest of it and the high-value warning. */
    private Consumer<FieldMessage> moneyMessages(ShiftField field) {
        return message -> {
            if (message == null) moneyMaskMessages.remove(field);
            else moneyMaskMessages.put(field, message);
            refreshMoneyMessage(field);
        };
    }

    private void refreshMoneyMessage(ShiftField field) {
        OptionalLong cents = MoneyInput.readCents((TextField) controls.get(field));
        FieldMessage outOfRange = cents.isPresent() && MoneyInput.isAllowed(cents.getAsLong()) ? null
                : FieldMessage.error(FormMessages.STORED_MONEY_OUT_OF_RANGE);
        FieldMessage warning = outOfRange != null ? null
                : ShiftWarnings.highValue(cents.getAsLong()).map(FieldMessage::warning).orElse(null);
        FieldMessage fieldState = FieldMessage.strongest(outOfRange, warning);
        showMessage(field, FieldMessage.strongest(moneyMaskMessages.get(field), fieldState));
    }

    /** A money field whose text is a stored value outside what the form allows (it must be corrected to save). */
    private boolean isStoredMoneyOutOfRange(ShiftField field) {
        OptionalLong cents = MoneyInput.readCents((TextField) controls.get(field));
        return cents.isEmpty() || !MoneyInput.isAllowed(cents.getAsLong());
    }

    /**
     * Messages coming from a mask. The "clear" the mask sends on every key does not remove a form-check error that
     * is still true: the text listener decides that, once the new text is known.
     */
    private Consumer<FieldMessage> maskMessages(ShiftField field) {
        return message -> {
            if (message == null && showingCheckError.contains(field)) return;
            showingCheckError.remove(field);
            showMessage(field, message);
        };
    }

    private void watchCheckedFields() {
        watchText(ShiftField.TIME_START, timeStartField);
        watchText(ShiftField.TIME_END, timeEndField);
        watchText(ShiftField.KM_START, kmStartField);
        watchText(ShiftField.KM_END, kmEndField);
        watchText(ShiftField.NOTES, notesArea);
        datePicker.valueProperty().addListener((obs, old, val) -> {
            onUserChange(ShiftField.DATE);
            updateCalculations();
        });
        datePicker.getEditor().focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused) return;
            commitDateText();
            checkOnExit(ShiftField.DATE);
        });
    }

    private void watchText(ShiftField field, TextInputControl input) {
        input.textProperty().addListener((obs, old, val) -> {
            onUserChange(field);
            updateCalculations();
        });
        input.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) checkOnExit(field);
        });
    }

    // ---- messages under the fields ------------------------------------------------------------------------

    private void onUserChange(ShiftField field) {
        if (filling) return;
        if (field == ShiftField.KM_START) hideKmSuggestionHint();
        touched.add(field);
        refreshCheckError(field);
        if (field == ShiftField.KM_START) refreshCheckError(ShiftField.KM_END);
    }

    /** While the user types: a form-check message stays until the field is valid, then it goes away. */
    private void refreshCheckError(ShiftField field) {
        if (showingCheckError.contains(field)) runCheck(field);
    }

    /** On leaving a field: shows its error if the user touched it. Leaving KM inicial also compares KM final. */
    void checkOnExit(ShiftField field) {
        if (touched.contains(field)) runCheck(field);
        if (field == ShiftField.KM_START && !kmEndField.getText().isBlank()) runCheck(ShiftField.KM_END);
    }

    private void runCheck(ShiftField field) {
        showCheckResult(field, ShiftFormCheck.checkField(field, currentInput()));
    }

    private void showCheckResult(ShiftField field, Optional<FieldError> error) {
        if (error.isPresent()) {
            showingCheckError.add(field);
            showMessage(field, FieldMessage.error(error.get().message()));
        } else {
            showingCheckError.remove(field);
            showMessage(field, null);
        }
    }

    private void showMessage(ShiftField field, FieldMessage message) {
        boolean wasShown = messageLabels.get(field).isVisible();
        feedback.show(messageLabels.get(field), controls.get(field), message);
        // Only when the message appears (not on every key while it stays): measuring the layout is not free.
        if (message != null && !wasShown && isFocusInside(controls.get(field))) revealWithMessage(field);
        // Clearing a field's message also clears its help text; the KM hint still needs it for screen readers.
        if (field == ShiftField.KM_START && message == null && kmSuggestionShown) {
            kmStartField.setAccessibleHelp(FormMessages.KM_SUGGESTED_HINT);
        }
    }

    private void clearCheckedMessages() {
        for (ShiftField field : CHECKED_FIELDS) showMessage(field, null);
    }

    // ---- reading and filling the fields -------------------------------------------------------------------

    private void fillFrom(WorkShift ws) {
        lblFormTitle.setText(FormMessages.TITLE_EDIT);
        datePicker.setValue(ws.getDate());
        timeStartField.setText(ws.getTimeStart().toString());
        timeEndField.setText(ws.getTimeEnd().toString());
        // setText would run the paste rule on "12.450,0" and turn it back into "12450,0".
        KmInput.setDisplayed(kmStartField, KmMask.display(ws.getKmStart()));
        KmInput.setDisplayed(kmEndField, KmMask.display(ws.getKmEnd()));
        // Stored values are shown as they are, even above the ceiling or negative: never a silent zero.
        MoneyInput.setStored(earnUberField, MoneyMask.toCents(ws.getUberEarnings()));
        MoneyInput.setStored(earn99Field, MoneyMask.toCents(ws.getApp99Earnings()));
        MoneyInput.setStored(earnOthersField, MoneyMask.toCents(ws.getOtherEarnings()));
        MoneyInput.setStored(costFuelField, MoneyMask.toCents(ws.getCostFuel()));
        MoneyInput.setStored(costFoodOtherField, MoneyMask.toCents(ws.getFoodAndOtherCosts()));
        notesArea.setText(Objects.requireNonNullElse(ws.getNotes(), ""));
    }

    private ShiftFormInput currentInput() {
        return new ShiftFormInput(datePicker.getValue(), timeStartField.getText(), timeEndField.getText(),
                kmStartField.getText(), kmEndField.getText(), notesArea.getText());
    }

    /** Builds a shift from the form fields. Throws IllegalArgumentException when a field is invalid. */
    private WorkShift readShiftFromFields() {
        WorkShift shift = new WorkShift();
        shift.setId(currentShift != null ? currentShift.getId() : null);
        WorkShiftValidator.validateAndPopulate(shift, datePicker.getValue(),
                TimeMask.complete(timeStartField.getText()).text(), TimeMask.complete(timeEndField.getText()).text(),
                KmMask.toPlain(kmStartField.getText()), KmMask.toPlain(kmEndField.getText()),
                MoneyMask.displayToPlain(earnUberField.getText()), MoneyMask.displayToPlain(earn99Field.getText()),
                MoneyMask.displayToPlain(earnOthersField.getText()),
                MoneyMask.displayToPlain(costFuelField.getText()),
                MoneyMask.displayToPlain(costFoodOtherField.getText()),
                notesArea.getText());
        return shift;
    }

    private void updateCalculations() {
        updateTurnNote();
        try {
            WorkShift shift = readShiftFromFields();
            lblCalcHours.setText(CurrencyFormat.oneDecimal(shift.calculateHours()) + " h");
            lblCalcKm.setText(CurrencyFormat.oneDecimal(shift.calculateKm()) + " km");
            lblCalcGross.setText(CurrencyFormat.money(shift.getGrossIncome()));
            lblCalcCosts.setText(CurrencyFormat.money(shift.getTotalCosts()));
            lblCalcMaint.setText(CurrencyFormat.money(shift.calculateMaintReserve(maintRate)));
            BigDecimal net = shift.calculateNetProfit(maintRate);
            lblCalcNet.setText(CurrencyFormat.money(net));
            Styles.markNegative(lblCalcNet, CurrencyFormat.isLoss(net));
            showNote(rateWarn, ShiftWarnings.unusualRate(shift.calculateHours(), shift.getGrossIncome()));
        } catch (IllegalArgumentException incompleteOrInvalidInput) {
            Styles.markNegative(lblCalcNet, false);
            // Expected while the user is still typing: show dashes instead of an alert.
            for (Label label : new Label[]{lblCalcHours, lblCalcKm, lblCalcGross, lblCalcCosts, lblCalcMaint, lblCalcNet}) {
                label.setText(UNAVAILABLE);
            }
            showNote(rateWarn, Optional.empty());
        }
    }

    /** Midnight / equal-times note, only when both times are complete. Warnings never block saving. */
    private void updateTurnNote() {
        Optional<String> note = Optional.empty();
        if (TimeMask.isComplete(timeStartField.getText()) && TimeMask.isComplete(timeEndField.getText())) {
            note = ShiftWarnings.turnNote(LocalTime.parse(timeStartField.getText()),
                    LocalTime.parse(timeEndField.getText()));
        }
        showNote(turnNote, note);
    }

    private void showNote(Label label, Optional<String> warning) {
        feedback.show(label, warning.map(FieldMessage::warning).orElse(null));
    }

    // ---- save ---------------------------------------------------------------------------------------------

    @FXML void handleSave() {
        // A second Save (Enter or Alt+S pressed twice) must never write the same new shift again.
        if (savedShift != null) return;
        commitDateTextIfEditing();
        formatTextFields();
        List<FieldError> errors = ShiftFormCheck.checkAll(currentInput());
        for (ShiftField field : CHECKED_FIELDS) {
            showCheckResult(field, errors.stream().filter(error -> error.field() == field).findFirst());
        }
        Optional<ShiftField> firstProblem = Stream.concat(errors.stream().map(FieldError::field),
                MONEY_FIELDS.stream().filter(this::isStoredMoneyOutOfRange)).min(Comparator.naturalOrder());
        if (firstProblem.isPresent()) {
            focusAndSelect(firstProblem.get());
            return;
        }
        saveShift();
    }

    private void saveShift() {
        if (savedShift != null) return;
        btnSave.setDisable(true);
        try {
            WorkShift shift = readShiftFromFields();
            dao.save(shift);
            savedShift = shift;
            closeStage();
        } catch (FieldValidationException e) {
            // Safety net: the form checks passed but the validator found a problem. Show it under its field.
            LOGGER.log(Level.WARNING, "The validator rejected a shift that the form checks accepted", e);
            showingCheckError.remove(e.getField());
            showMessage(e.getField(), FieldMessage.error(e.getMessage()));
            focusAndSelect(e.getField());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to save work shift", e);
            Dialogs.show(Alert.AlertType.ERROR, FormMessages.SAVE_FAILED_TITLE, SAVE_FAILED_TEXT);
        } finally {
            if (savedShift == null) btnSave.setDisable(false);
        }
    }

    /** Same tidy-up as leaving each field, for when Save is used while the focus is still inside a field. */
    private void formatTextFields() {
        for (TextField time : List.of(timeStartField, timeEndField)) {
            MaskResult completed = TimeMask.complete(time.getText());
            if (!completed.text().equals(time.getText())) time.setText(completed.text());
        }
        for (TextField km : List.of(kmStartField, kmEndField)) {
            MaskResult formatted = KmMask.formatOnExit(km.getText());
            if (!formatted.text().equals(km.getText())) KmInput.setDisplayed(km, formatted.text());
        }
    }

    private void commitDateTextIfEditing() {
        if (datePicker.getEditor().isFocused()) commitDateText();
    }

    /** Reads the text typed in the date field (it is normally read only when the field loses focus). */
    private void commitDateText() {
        FormKeyboard.commitTypedDate(datePicker);
    }

    private void focusAndSelect(ShiftField field) {
        Control control = controls.get(field);
        control.requestFocus();
        // Focus given by code does not scroll the form by itself (and nothing changes if the field already had it).
        revealWithMessage(field);
        TextInputControl text = control instanceof DatePicker picker ? picker.getEditor()
                : control instanceof TextInputControl input ? input : null;
        if (text != null) Platform.runLater(text::selectAll);
    }

    // ---- keeping the focused field in view ----------------------------------------------------------------

    /** Focus given by code (Enter chain, Shift+Tab out of the notes): the form scrolls to the field, like Tab does. */
    private void focusAndReveal(Control control) {
        control.requestFocus();
        FieldFocus.revealInScrollPane(formScroll, control);
    }

    /** The field, then the message under it when one is shown, so an error is never left below the edge. */
    private void revealWithMessage(ShiftField field) {
        FieldFocus.revealInScrollPane(formScroll, controls.get(field));
        Label message = messageLabels.get(field);
        if (message.isVisible()) FieldFocus.revealInScrollPane(formScroll, message);
    }

    private static boolean isFocusInside(Control control) {
        Node owner = control.getScene() == null ? null : control.getScene().getFocusOwner();
        for (Node node = owner; node != null; node = node.getParent()) {
            if (node == control) return true;
        }
        return false;
    }

    @FXML private void handleCancel() {
        if (mayClose()) closer.run();
    }

    // ---- keyboard, closing and the discard question -------------------------------------------------------

    /** The keys need the Scene, which does not exist yet while the form is loaded. */
    private void installKeyboardWhenSceneExists() {
        datePicker.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene == null || keyboardInstalled) return;
            keyboardInstalled = true;
            FormKeyboard.install(scene, enterChain(), costFoodOtherField, this::handleSave, this::handleCancel,
                    datePicker::isShowing, this::focusAndReveal);
            scene.windowProperty().addListener((o, oldWindow, window) -> askBeforeWindowCloses(window));
            askBeforeWindowCloses(scene.getWindow());
        });
    }

    /** Fields in Tab order that Enter walks through (the notes area is left out: Enter is a line break there). */
    private List<Control> enterChain() {
        return List.of(datePicker, timeStartField, timeEndField, kmStartField, kmEndField, earnUberField,
                earn99Field, earnOthersField, costFuelField, costFoodOtherField);
    }

    /** The window X asks the same question as Cancel. */
    private void askBeforeWindowCloses(Window window) {
        if (window == null) return;
        window.setOnCloseRequest(event -> {
            if (!mayClose()) event.consume();
        });
    }

    /** True when nothing changed since the form opened, or the user agreed to discard the changes. */
    boolean mayClose() {
        commitDateTextIfEditing();
        return currentTexts().equals(openingTexts) || discardAsker.getAsBoolean();
    }

    /**
     * The texts that decide "did the user change something". Times and KM are compared as they would look after
     * leaving the field, so typing the same value again in another form does not trigger the discard question.
     * Never null: a stored shift without notes gives an empty text.
     */
    private List<String> currentTexts() {
        return List.of(String.valueOf(datePicker.getValue()),
                TimeMask.complete(textOf(timeStartField)).text(), TimeMask.complete(textOf(timeEndField)).text(),
                KmMask.formatOnExit(textOf(kmStartField)).text(), KmMask.formatOnExit(textOf(kmEndField)).text(),
                textOf(earnUberField), textOf(earn99Field), textOf(earnOthersField), textOf(costFuelField),
                textOf(costFoodOtherField), textOf(notesArea));
    }

    private static String textOf(TextInputControl input) {
        return Objects.toString(input.getText(), "");
    }

    /** "Descartar alterações?" with "Continuar editando" as the default button, so Enter or Esc keeps the form. */
    private boolean askToDiscard() {
        ButtonType discard = new ButtonType(FormMessages.DISCARD_CONFIRM, ButtonData.OK_DONE);
        ButtonType keep = new ButtonType(FormMessages.DISCARD_KEEP, ButtonData.CANCEL_CLOSE);
        Alert question = new Alert(Alert.AlertType.CONFIRMATION, FormMessages.DISCARD_TEXT, discard, keep);
        question.setTitle(FormMessages.DISCARD_TITLE);
        question.setHeaderText(null);
        Scene scene = datePicker.getScene();
        if (scene != null && scene.getWindow() != null) question.initOwner(scene.getWindow());
        Styles.apply(question.getDialogPane());
        ((Button) question.getDialogPane().lookupButton(discard)).setDefaultButton(false);
        ((Button) question.getDialogPane().lookupButton(keep)).setDefaultButton(true);
        return question.showAndWait().orElse(keep) == discard;
    }

    /** Test seam: replaces the dialog of the discard question. */
    void setDiscardAsker(BooleanSupplier asker) {
        this.discardAsker = asker;
    }

    /** Test seam: replaces "hide the window", which cannot be observed without a window. */
    void setCloser(Runnable closer) {
        this.closer = closer;
    }

    // ---- KM inicial suggested from the last shift ---------------------------------------------------------

    /** New shift only. A failure to read the last shift is logged: a hint must never stop the form. */
    private void suggestKmStart() {
        try {
            Optional<WorkShift> last = dao.findLastShift();
            if (last.isEmpty()) return;
            KmInput.setDisplayed(kmStartField, KmMask.display(last.get().getKmEnd()));
            kmStartField.selectAll();
            kmSuggestionShown = true;
            feedback.show(kmStartHint, kmStartField, FieldMessage.info(FormMessages.KM_SUGGESTED_HINT));
        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not read the last shift; the form opens without a KM suggestion", e);
        }
    }

    /** The hint goes away with the first change the user makes to KM inicial. */
    private void hideKmSuggestionHint() {
        if (!kmSuggestionShown) return;
        kmSuggestionShown = false;
        feedback.show(kmStartHint, kmStartField, null);
    }

    private void closeStage() {
        Scene scene = datePicker.getScene();
        Window window = scene == null ? null : scene.getWindow();
        if (window != null) window.hide();
    }
}
