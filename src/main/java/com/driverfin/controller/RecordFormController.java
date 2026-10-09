package com.driverfin.controller;

import com.driverfin.dao.DailyRecordDAO;
import com.driverfin.model.DailyRecord;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public class RecordFormController {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @FXML private Label lblFormTitle;
    @FXML private DatePicker datePicker;
    @FXML private TextField timeStartField;
    @FXML private TextField timeEndField;
    @FXML private TextField kmStartField;
    @FXML private TextField kmEndField;
    @FXML private TextField earnUberField;
    @FXML private TextField earn99Field;
    @FXML private TextField earnOthersField;
    @FXML private TextField costFuelField;
    @FXML private TextField costFoodOtherField;
    @FXML private TextArea notesArea;

    @FXML private Label lblCalcHours;
    @FXML private Label lblCalcKm;
    @FXML private Label lblCalcGross;
    @FXML private Label lblCalcCosts;
    @FXML private Label lblCalcMaint;
    @FXML private Label lblCalcNet;

    @FXML private Button btnSave;
    @FXML private Button btnCancel;

    private final DailyRecordDAO recordDAO = new DailyRecordDAO();
    private DailyRecord recordToEdit = null;
    private double maintRatePerKm = 0.20;
    private boolean savedSuccessfully = false;

    /** Debounce para recálculo em tempo real — evita recálculos excessivos durante digitação rápida. */
    private final PauseTransition recalcDebounce = new PauseTransition(Duration.millis(50));

    @FXML
    public void initialize() {
        datePicker.setValue(LocalDate.now());

        // Formatador sequencial de data estilo caixa eletrônico (dd/MM/yyyy)
        setupDateMask(datePicker);

        // Formatadores monetários estilo caixa eletrônico (digitação em centavos com vírgula)
        setupCurrencyMask(earnUberField);
        setupCurrencyMask(earn99Field);
        setupCurrencyMask(earnOthersField);
        setupCurrencyMask(costFuelField);
        setupCurrencyMask(costFoodOtherField);

        // Formatadores de horário automáticos (HH:mm) com valor inicial
        setupTimeMask(timeStartField);
        setupTimeMask(timeEndField);

        // Formatador para KM (aceita dígitos, ponto e vírgula)
        setupKmMask(kmStartField);
        setupKmMask(kmEndField);

        // Setup recalculation listeners com debounce
        recalcDebounce.setOnFinished(e -> recalculateSummary());

        TextField[] fields = new TextField[]{
            timeStartField, timeEndField, kmStartField, kmEndField,
            earnUberField, earn99Field, earnOthersField, costFuelField, costFoodOtherField
        };

        for (TextField field : fields) {
            field.textProperty().addListener((obs, oldVal, newVal) -> recalcDebounce.playFromStart());
            field.setOnKeyPressed(this::handleKeyPressed);
        }

        datePicker.valueProperty().addListener((obs, oldVal, newVal) -> recalcDebounce.playFromStart());

        recalculateSummary();
    }

    /**
     * Máscara monetária estilo caixa eletrônico.
     * Digitação em centavos: 1 → 0,01 | 12 → 0,12 | 123 → 1,23 | 12345 → 123,45
     */
    private void setupCurrencyMask(TextField textField) {
        textField.setText("0,00");
        textField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.equals(oldVal)) return;
            
            String digits = newVal.replaceAll("\\D", "");
            if (digits.isEmpty()) {
                digits = "0";
            }
            
            // Limita o tamanho para evitar overflow (9 dígitos = até 9.999.999,99)
            if (digits.length() > 9) {
                digits = digits.substring(0, 9);
            }
            
            long centavos = Long.parseLong(digits);
            String formatted = String.format(PT_BR, "%,.2f", centavos / 100.0);
            
            if (!newVal.equals(formatted)) {
                textField.setText(formatted);
                textField.positionCaret(formatted.length());
            }
        });
    }

    /**
     * Máscara sequencial de data estilo caixa eletrônico (dd/MM/yyyy).
     * Digita 8 números sequencialmente: 3 → 00/00/0003 | 30 → 00/00/0030 | 30072026 → 30/07/2026
     * Valida corretamente meses e dias (respeitando dias por mês e anos bissextos).
     */
    private void setupDateMask(DatePicker datePicker) {
        TextField dateField = datePicker.getEditor();
        dateField.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        
        dateField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.equals(oldVal)) return;

            String digits = newVal.replaceAll("\\D", "");
            if (digits.isEmpty()) digits = "0";
            if (digits.length() > 8) digits = digits.substring(0, 8);

            // Preenche com zeros à esquerda até formar 8 dígitos (ddMMyyyy)
            String padded = String.format("%08d", Long.parseLong(digits));
            String dayStr = padded.substring(0, 2);
            String monthStr = padded.substring(2, 4);
            String yearStr = padded.substring(4, 8);

            String formatted = dayStr + "/" + monthStr + "/" + yearStr;

            if (!newVal.equals(formatted)) {
                dateField.setText(formatted);
                dateField.positionCaret(formatted.length());
            }

            try {
                int day = Integer.parseInt(dayStr);
                int month = Integer.parseInt(monthStr);
                int year = Integer.parseInt(yearStr);

                if (month >= 1 && month <= 12 && day >= 1 && year >= 1900) {
                    // Calcula o número máximo de dias para o mês e ano informados
                    int maxDay = Month.of(month).length(Year.isLeap(year));
                    int validDay = Math.min(day, maxDay);
                    LocalDate parsedDate = LocalDate.of(year, month, validDay);
                    datePicker.setValue(parsedDate);
                }
            } catch (Exception ignored) {
                // Data incompleta durante digitação — ignorar erros intermediários
            }
        });
    }

    /**
     * Máscara de horário HH:mm estilo caixa eletrônico (idêntico ao monetário).
     * O campo inicia vazio. Dígitos acumulam da direita para esquerda.
     * Para não quebrar a digitação (ex: 16:30 passa pelo estado 01:63),
     * a validação e correção para 00:00 ocorre apenas ao perder o foco (blur).
     */
    private void setupTimeMask(TextField textField) {
        textField.setPromptText("HH:mm");
        textField.setText(""); // Inicia vazio
        
        textField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.equals(oldVal)) return;

            String digits = newVal.replaceAll("\\D", "");
            
            // Se apagou tudo, deixa vazio
            if (digits.isEmpty()) {
                if (!newVal.isEmpty()) {
                    textField.setText("");
                }
                return;
            }

            // Pega os ÚLTIMOS 4 dígitos (da direita)
            if (digits.length() > 4) digits = digits.substring(digits.length() - 4);

            // Preenche com zeros à esquerda até 4 dígitos (HHmm)
            String padded = String.format("%04d", Integer.parseInt(digits));
            int hours = Integer.parseInt(padded.substring(0, 2));
            int minutes = Integer.parseInt(padded.substring(2, 4));

            // Não validamos os limites (hours > 23 ou minutes > 59) DURANTE a digitação
            // pois números como 16:30 passam por 01:63 transitoriamente.
            String formatted = String.format("%02d:%02d", hours, minutes);

            if (!newVal.equals(formatted)) {
                textField.setText(formatted);
                textField.positionCaret(formatted.length());
            }
        });

        // Valida quando o campo perde o foco
        textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                String text = textField.getText();
                if (text == null || text.isEmpty()) return;
                
                String digits = text.replaceAll("\\D", "");
                if (digits.isEmpty()) return;
                
                String padded = String.format("%04d", Integer.parseInt(digits));
                int hours = Integer.parseInt(padded.substring(0, 2));
                int minutes = Integer.parseInt(padded.substring(2, 4));

                // Aplica a regra de negócio: horários inválidos zeram para 00
                if (hours > 23) hours = 0;
                if (minutes > 59) minutes = 0;

                String formatted = String.format("%02d:%02d", hours, minutes);
                if (!text.equals(formatted)) {
                    textField.setText(formatted);
                }
            }
        });
    }

    /**
     * Máscara simples para KM — aceita apenas dígitos, ponto e vírgula.
     */
    private void setupKmMask(TextField textField) {
        textField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.equals(oldVal)) return;
            
            // Permite digitação com ponto ou vírgula mantendo apenas dígitos e vírgula/ponto
            String cleaned = newVal.replaceAll("[^0-9,.]", "");
            if (!newVal.equals(cleaned)) {
                textField.setText(cleaned);
                textField.positionCaret(cleaned.length());
            }
        });
    }

    private void handleKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            handleSave();
        }
    }

    public void setRecord(DailyRecord record, double maintRate) {
        this.maintRatePerKm = maintRate;
        this.recordToEdit = record;

        if (record != null) {
            lblFormTitle.setText("✏️ Editar Registro Diário");
            datePicker.setValue(record.getDate());
            timeStartField.setText(record.getTimeStart() != null ? record.getTimeStart().format(TIME_FORMATTER) : "");
            timeEndField.setText(record.getTimeEnd() != null ? record.getTimeEnd().format(TIME_FORMATTER) : "");
            kmStartField.setText(String.format(PT_BR, "%.1f", record.getKmStart()));
            kmEndField.setText(String.format(PT_BR, "%.1f", record.getKmEnd()));
            earnUberField.setText(String.format(PT_BR, "%.2f", record.getEarnUber()));
            earn99Field.setText(String.format(PT_BR, "%.2f", record.getEarn99()));
            earnOthersField.setText(String.format(PT_BR, "%.2f", record.getEarnOthers()));
            costFuelField.setText(String.format(PT_BR, "%.2f", record.getCostFuel()));
            costFoodOtherField.setText(String.format(PT_BR, "%.2f", record.getCostFoodOther()));
            notesArea.setText(record.getNotes() != null ? record.getNotes() : "");
        } else {
            lblFormTitle.setText("📝 Novo Registro Diário");
            datePicker.setValue(LocalDate.now());
        }

        recalculateSummary();
    }

    public boolean isSavedSuccessfully() {
        return savedSuccessfully;
    }

    private void recalculateSummary() {
        LocalTime start = parseTime(timeStartField.getText());
        LocalTime end = parseTime(timeEndField.getText());

        double hours = 0.0;
        if (start != null && end != null) {
            java.time.Duration duration = java.time.Duration.between(start, end);
            if (duration.isNegative()) {
                duration = duration.plusDays(1);
            }
            hours = duration.toMinutes() / 60.0;
        }

        double kmStart = parseKmValue(kmStartField.getText());
        double kmEnd = parseKmValue(kmEndField.getText());
        double km = Math.max(0.0, kmEnd - kmStart);

        double earnUber = parseCurrencyValue(earnUberField.getText());
        double earn99 = parseCurrencyValue(earn99Field.getText());
        double earnOthers = parseCurrencyValue(earnOthersField.getText());
        double gross = earnUber + earn99 + earnOthers;

        double costFuel = parseCurrencyValue(costFuelField.getText());
        double costFood = parseCurrencyValue(costFoodOtherField.getText());
        double costs = costFuel + costFood;

        double maint = km * maintRatePerKm;
        double net = gross - costs - maint;

        lblCalcHours.setText(String.format(PT_BR, "%.1f h", hours));
        lblCalcKm.setText(String.format(PT_BR, "%.1f km", km));
        lblCalcGross.setText(String.format(PT_BR, "R$ %,.2f", gross));
        lblCalcCosts.setText(String.format(PT_BR, "R$ %,.2f", costs));
        lblCalcMaint.setText(String.format(PT_BR, "R$ %,.2f", maint));
        lblCalcNet.setText(String.format(PT_BR, "R$ %,.2f", net));
    }

    @FXML
    private void handleSave() {
        LocalDate date = datePicker.getValue();
        if (date == null) {
            showAlert("Erro de Validação", "Selecione uma data válida.");
            return;
        }

        LocalTime timeStart = parseTime(timeStartField.getText());
        LocalTime timeEnd = parseTime(timeEndField.getText());
        if (timeStart == null || timeEnd == null) {
            showAlert("Erro de Validação", "Informe os horários no formato HH:mm (ex: 08:00).");
            return;
        }

        double kmStart = parseKmValue(kmStartField.getText());
        double kmEnd = parseKmValue(kmEndField.getText());

        if (kmStart < 0 || kmEnd < 0) {
            showAlert("Erro de Validação", "A quilometragem não pode ser negativa.");
            return;
        }

        if (kmEnd < kmStart) {
            showAlert("Erro de Validação", "O KM Final não pode ser menor que o KM Inicial.");
            return;
        }

        double earnUber = parseCurrencyValue(earnUberField.getText());
        double earn99 = parseCurrencyValue(earn99Field.getText());
        double earnOthers = parseCurrencyValue(earnOthersField.getText());
        double costFuel = parseCurrencyValue(costFuelField.getText());
        double costFoodOther = parseCurrencyValue(costFoodOtherField.getText());

        if (earnUber < 0 || earn99 < 0 || earnOthers < 0 || costFuel < 0 || costFoodOther < 0) {
            showAlert("Erro de Validação", "Valores monetários de ganhos e custos não podem ser negativos.");
            return;
        }

        String notes = notesArea.getText() != null ? notesArea.getText().trim() : "";

        if (recordToEdit == null) {
            DailyRecord newRecord = new DailyRecord(
                date, timeStart, timeEnd, kmStart, kmEnd,
                earnUber, earn99, earnOthers, costFuel, costFoodOther, notes
            );
            recordDAO.save(newRecord);
        } else {
            recordToEdit.setDate(date);
            recordToEdit.setTimeStart(timeStart);
            recordToEdit.setTimeEnd(timeEnd);
            recordToEdit.setKmStart(kmStart);
            recordToEdit.setKmEnd(kmEnd);
            recordToEdit.setEarnUber(earnUber);
            recordToEdit.setEarn99(earn99);
            recordToEdit.setEarnOthers(earnOthers);
            recordToEdit.setCostFuel(costFuel);
            recordToEdit.setCostFoodOther(costFoodOther);
            recordToEdit.setNotes(notes);
            recordDAO.update(recordToEdit);
        }

        savedSuccessfully = true;
        closeWindow();
    }

    @FXML
    private void handleCancel() {
        savedSuccessfully = false;
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) btnCancel.getScene().getWindow();
        stage.close();
    }

    /**
     * Faz parsing de texto de horário para LocalTime.
     * Aceita formatos: "08:00", "0800", "8:00".
     */
    private LocalTime parseTime(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            String sanitized = text.trim();
            if (sanitized.length() == 4 && !sanitized.contains(":")) {
                sanitized = sanitized.substring(0, 2) + ":" + sanitized.substring(2);
            }
            return LocalTime.parse(sanitized, TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Faz parsing de valores monetários formatados (vírgula como decimal, ponto como milhar).
     * Exemplo: "1.250,45" → 1250.45 | "0,00" → 0.0
     */
    private double parseCurrencyValue(String text) {
        if (text == null || text.trim().isEmpty()) return 0.0;
        try {
            // Remove separador de milhares (ponto) e converte vírgula decimal para ponto
            String cleaned = text.trim().replace(".", "").replace(",", ".");
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Faz parsing de valores de quilometragem (aceita ponto OU vírgula como decimal).
     * Exemplo: "12450.5" → 12450.5 | "12450,5" → 12450.5
     */
    private double parseKmValue(String text) {
        if (text == null || text.trim().isEmpty()) return 0.0;
        try {
            // KM pode ter vírgula ou ponto como decimal — converte vírgula para ponto
            String cleaned = text.trim().replace(",", ".");
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
