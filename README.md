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

* **Shift Tracking:** Input for date, start and end time, initial and final mileage, income by source (Uber, 99, other) and operational expenses (fuel, food and other costs).
* **Automated Maintenance Provisioning:** Calculates a reserve fund by multiplying total distance driven by a configurable rate per kilometer.
* **Financial KPIs:** Shows gross revenue, direct costs, maintenance reserve and real net profit, plus profit per hour and per kilometer, and average revenue (gross) per hour and per kilometer. All values follow the selected period and search filter.
* **Filters and Chart:** Filter shifts by period (today, last 7 days, this month, last month, custom range) and by notes, date or value. A daily bar chart compares revenue, costs and profit.
* **Data Persistence:** Local storage utilizing a relational SQLite database.
* **Export and Backup:** Generates PDF reports for the filtered shifts and creates a backup copy of the database.

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

## Using the App

* **Data location:** the database is stored in `%APPDATA%\DriverFin\driverfin.db`. Back it up from the app with the Backup button (it never overwrites the database in use).
* **Maintenance rate:** set in R$ per km on the dashboard (default 0,25, maximum 100) and remembered between sessions.
* **Typing numbers:** use a comma or a dot for decimals, and never a dot for thousands. Write `1000,50`, not `1.000,50`. Times use `HH:mm` (00:00 to 23:59). Money cannot be negative and is rounded to cents.
* **Old data:** if an older `driverfin.db` with a `daily_records` table is found in the folder where the app is started, it is imported once and renamed to `driverfin.db.migrated`. Before a schema upgrade, a copy named `driverfin.db.bak-v0` is saved next to the database.

## Testing
Financial calculations, filters, validation, database access and migration, backup, PDF export and the FXML bindings are tested with JUnit 5. To execute the test suite, run:
```bash
mvn test
```

## Contributing
1. Fork the project
2. Create your feature branch (`git checkout -b feature/NewFeature`)
3. Commit your changes using conventional commits (`git commit -m 'feat: add new feature'`)
4. Push to the branch (`git push origin feature/NewFeature`)
5. Open a Pull Request
