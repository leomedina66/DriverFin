# DriverFin

A desktop Java application designed to provide financial control for rideshare drivers. The system calculates real net profit by tracking gross income, subtracting operational costs, and automatically provisioning a maintenance reserve based on distance driven.

## Technical Specifications

* **Language:** Java 17+
* **Framework:** JavaFX (MVC Architecture with FXML)
* **Build Tool:** Maven
* **Database:** Embedded SQLite accessed via JDBC and DAO pattern
* **Reporting:** PDF generation using OpenPDF
* **Testing:** JUnit 5 for domain logic validation

## Core Features

* **Run Tracking:** Input mechanisms for timestamps, initial and final mileage, segmented income (e.g., rideshare apps, private rides), and operational expenses (fuel, food).
* **Automated Maintenance Provisioning:** Calculates a reserve fund by multiplying total distance driven by a configurable rate per kilometer.
* **Financial KPIs:** Computes real net profit, net margin, revenue per hour, and revenue per kilometer.
* **Data Persistence:** Local storage utilizing a relational SQLite database.
* **Export:** Generates structured PDF reports summarizing daily financial performance.

*(Note: An earlier Web/SPA version of this project existed but was retired to focus entirely on this robust desktop Java architecture).*

## Getting Started

### Prerequisites
* Java Development Kit (JDK) 17 or higher
* Apache Maven

### Build Instructions
To compile the project and build the executable JAR, run:
```bash
mvn clean package
```

### Running in Development
To run the application directly from the source code via Maven:
```bash
mvn javafx:run
```

### Downloading the Release
End users do not need to build the project from source. You can download the latest pre-packaged installer (built via `jpackage` with an embedded Java Runtime) from the **[GitHub Releases](../../releases)** page.

## Testing
The core financial calculations are thoroughly tested using JUnit 5. To execute the test suite, run:
```bash
mvn test
```

## Contributing
1. Fork the project
2. Create your feature branch (`git checkout -b feature/NewFeature`)
3. Commit your changes using conventional commits (`git commit -m 'feat: add new feature'`)
4. Push to the branch (`git push origin feature/NewFeature`)
5. Open a Pull Request
