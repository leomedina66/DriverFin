package com.driverfin.controller;

import com.driverfin.dao.WorkShiftDAO;
import com.driverfin.model.WorkShift;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

public class RecordFormController {
    @FXML private DatePicker dpDate;
    @FXML private TextField txtTimeStart, txtTimeEnd, txtKmStart, txtKmEnd;
    @FXML private TextField txtUber, txtApp99, txtOthers, txtFuel, txtFood;
    
    private WorkShift currentShift;
    private BigDecimal maintRate;
    private WorkShiftDAO dao;
    private boolean saved = false;

    public void initData(WorkShift ws, BigDecimal maintRate, WorkShiftDAO dao) {
        this.currentShift = ws;
        this.maintRate = maintRate;
        this.dao = dao;
        
        if (ws != null) {
            dpDate.setValue(ws.getDate());
            txtTimeStart.setText(ws.getTimeStart().toString());
            txtTimeEnd.setText(ws.getTimeEnd().toString());
            txtKmStart.setText(String.valueOf(ws.getKmStart()));
            txtKmEnd.setText(String.valueOf(ws.getKmEnd()));
            txtUber.setText(ws.getUberEarnings().toString());
            txtApp99.setText(ws.getApp99Earnings().toString());
            txtOthers.setText(ws.getOtherEarnings().toString());
            txtFuel.setText(ws.getCostFuel().toString());
            txtFood.setText(ws.getFoodAndOtherCosts().toString());
        }
    }

    @FXML private void handleSave() {
        try {
            WorkShift shift = new WorkShift();
            shift.setId(currentShift != null ? currentShift.getId() : null);
            shift.setDate(dpDate.getValue());
            shift.setTimeStart(LocalTime.parse(txtTimeStart.getText()));
            shift.setTimeEnd(LocalTime.parse(txtTimeEnd.getText()));
            
            shift.setKmStart(Double.parseDouble(txtKmStart.getText().replace(",", ".")));
            shift.setKmEnd(Double.parseDouble(txtKmEnd.getText().replace(",", ".")));
            
            shift.setUberEarnings(new BigDecimal(txtUber.getText().replace(",", ".")));
            shift.setApp99Earnings(new BigDecimal(txtApp99.getText().replace(",", ".")));
            shift.setOtherEarnings(new BigDecimal(txtOthers.getText().replace(",", ".")));
            shift.setCostFuel(new BigDecimal(txtFuel.getText().replace(",", ".")));
            shift.setFoodAndOtherCosts(new BigDecimal(txtFood.getText().replace(",", ".")));

            if (shift.getKmEnd() < shift.getKmStart()) {
                throw new IllegalArgumentException("KM Final não pode ser menor que KM Inicial.");
            }

            dao.save(shift);
            saved = true;
            closeStage();
        } catch (NumberFormatException e) {
            showAlert("Erro de Formatação", "Certifique-se de digitar números válidos. Evite múltiplos pontos. " + e.getMessage());
        } catch (DateTimeParseException e) {
            showAlert("Erro de Horário", "Use o formato HH:mm para as horas.");
        } catch (Exception e) {
            showAlert("Erro ao Salvar", e.getMessage());
        }
    }

    @FXML private void handleCancel() {
        closeStage();
    }

    private void closeStage() {
        ((Stage) dpDate.getScene().getWindow()).close();
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
