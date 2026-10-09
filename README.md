# RotaVerde / DriverFin

A desktop Java application designed to provide financial control for rideshare drivers. The system calculates real net profit by tracking gross income, subtracting operational costs, and automatically provisioning a maintenance reserve based on distance driven.

## Technical Specifications

* **Language:** Java 17
* **Framework:** JavaFX (MVC Architecture with FXML)
* **Build Tool:** Maven
* **Database:** Embedded SQLite accessed via JDBC and DAO pattern
* **Reporting:** PDF generation using OpenPDF
* **Deployment:** Packaged as a standalone Windows executable utilizing a bundled JRE (Java Runtime Environment) via `maven-shade-plugin` and custom batch scripts.

## Core Features

* **Run Tracking:** Input mechanisms for timestamps, initial and final mileage, segmented income (e.g., rideshare apps, private rides), and operational expenses (fuel, food).
* **Automated Maintenance Provisioning:** Calculates a reserve fund by multiplying total distance driven by a configurable rate per kilometer.
* **Financial KPIs:** Computes real net profit, net margin, revenue per hour, and revenue per kilometer.
* **Data Persistence:** Local storage utilizing a relational SQLite database (`driverfin.db`).
* **Export:** Generates structured PDF reports summarizing daily financial performance.

## Architecture Highlights

The application follows the Model-View-Controller (MVC) architectural pattern:
* **Models:** Represent the domain logic (`DailyRecord`, `FinancialSummary`).
* **Views:** FXML files defining the UI layout (`dashboard.fxml`, `record-form.fxml`).
* **Controllers:** Handle user interactions and bridge views with the database (`DashboardController`, `RecordFormController`).
* **DAO Layer:** Encapsulates all database interactions and SQL queries (`DailyRecordDAO`, `DatabaseConnection`).

## How to Run

1. Ensure the bundled runtime is available in the `dist` directory.
2. Execute the `DriverFin.bat` script to launch the application on Windows. 
   *(Note: A local Java installation is not strictly required due to the bundled runtime).*
