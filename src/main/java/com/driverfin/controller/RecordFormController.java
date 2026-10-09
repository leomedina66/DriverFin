package com.driverfin.controller;

import com.driverfin.dao.WorkShiftDAO;
import com.driverfin.model.WorkShift;
import com.driverfin.util.WorkShiftValidator;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import java.math.BigDecimal;

public class RecordFormController {
    @FXML private DatePicker datePicker;
    @FXML private TextField timeStartField, timeEndField, kmStartField, kmEndField;
    @FXML private TextField earnUberField, earn99Field, earnOthersField, costFuelField, costFoodOtherField;
    @FXML private TextArea notesArea;
    @FXML private Button btnSave, btnCancel;
    
    private WorkShift currentShift;
    private BigDecimal maintRate;
    private WorkShiftDAO dao;
    private boolean saved = false;

    public void initData(WorkShift ws, BigDecimal maintRate, WorkShiftDAO dao) {
        this.currentShift = ws;
        this.maintRate = maintRate;
        this.dao = dao;
        
        if (ws != null) {
            datePicker.setValue(ws.getDate());
            timeStartField.setText(ws.getTimeStart().toString());
            timeEndField.setText(ws.getTimeEnd().toString());
            kmStartField.setText(String.valueOf(ws.getKmStart()));
            kmEndField.setText(String.valueOf(ws.getKmEnd()));
            earnUberField.setText(ws.getUberEarnings().toString());
            earn99Field.setText(ws.getApp99Earnings().toString());
            earnOthersField.setText(ws.getOtherEarnings().toString());
            costFuelField.setText(ws.getCostFuel().toString());
            costFoodOtherField.setText(ws.getFoodAndOtherCosts().toString());
            if (notesArea != null) notesArea.setText(ws.getNotes());
        }
    }

    @FXML private void handleSave() {
        try {
            WorkShift shift = new WorkShift();
            shift.setId(currentShift != null ? currentShift.getId() : null);
            
            WorkShiftValidator.validateAndPopulate(shift, datePicker.getValue(),
                timeStartField.getText(), timeEndField.getText(),
                kmStartField.getText(), kmEndField.getText(),
                earnUberField.getText(), earn99Field.getText(), earnOthersField.getText(),
                costFuelField.getText(), costFoodOtherField.getText(),
                notesArea != null ? notesArea.getText() : ""
            );

            dao.save(shift);
            saved = true;
            closeStage();
        } catch (IllegalArgumentException e) {
            showAlert("Erro de Validação", e.getMessage());
        } catch (Exception e) {
            showAlert("Erro ao Salvar", e.getMessage());
        }
    }

    @FXML private void handleCancel() {
        closeStage();
    }

    private void closeStage() {
        ((Stage) datePicker.getScene().getWindow()).close();
    }

    private void showAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setContentText(msg);
        a.showAndWait();
    }

    public boolean isSaved() {
        return saved;
    }
}
