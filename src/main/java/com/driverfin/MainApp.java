package com.driverfin;

import com.driverfin.dao.DatabaseConnection;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            // Initialize SQLite Database and seed sample data if empty
            DatabaseConnection.initDatabase();

            // Load Dashboard FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/driverfin/view/dashboard.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root, 1200, 800);
            
            // Apply Dark Theme CSS
            scene.getStylesheets().add(getClass().getResource("/com/driverfin/css/theme-dark.css").toExternalForm());
            primaryStage.setTitle("EcoDrive Finanças - Controle Financeiro para Motoristas");
            primaryStage.getIcons().add(new javafx.scene.image.Image(getClass().getResourceAsStream("/com/driverfin/images/icon.png")));
            primaryStage.setMinHeight(650);
            primaryStage.setMinWidth(950);
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
            // Fix #12: Feedback visual ao usuário em caso de erro de inicialização
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.ERROR
            );
            alert.setTitle("Erro de Inicialização");
            alert.setHeaderText("Não foi possível iniciar o DriverFin");
            alert.setContentText("Erro: " + e.getMessage() + "\n\nVerifique se os arquivos do aplicativo estão íntegros.");
            alert.showAndWait();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
