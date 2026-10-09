package com.driverfin;

import com.driverfin.dao.DatabaseConnection;
import com.driverfin.dao.DatabaseInitializer;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;

public class MainApp extends Application {
    private static final Logger LOGGER = Logger.getLogger(MainApp.class.getName());

    @Override
    public void start(Stage primaryStage) {
        try {
            // Initialize Database
            DatabaseConnection dbConn = new DatabaseConnection();
            DatabaseInitializer dbInit = new DatabaseInitializer(dbConn);
            dbInit.initDatabase();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/driverfin/view/dashboard.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/com/driverfin/css/theme-dark.css").toExternalForm());

            primaryStage.setTitle("DriverFin");
            primaryStage.setScene(scene);
            
            try {
                primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/com/driverfin/images/icon.png")));
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Failed to load application icon.", ex);
            }
            
            primaryStage.show();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Critical error during application startup.", e);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
