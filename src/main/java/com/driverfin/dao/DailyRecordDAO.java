package com.driverfin.dao;

import com.driverfin.model.DailyRecord;
import com.driverfin.model.FinancialSummary;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object for DailyRecord entities using PreparedStatements.
 */
public class DailyRecordDAO {

    private static final Logger LOGGER = Logger.getLogger(DailyRecordDAO.class.getName());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_TIME;

    /**
     * Inserts a new DailyRecord into the database.
     * Sets the auto-generated id on the returned object.
     */
    public DailyRecord save(DailyRecord record) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return saveWithConnection(conn, record);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error inserting daily record", e);
            throw new RuntimeException("Failed to save DailyRecord", e);
        }
    }

    /**
     * Inserts a new DailyRecord using an existing connection (for transactional batches).
     * Does NOT close the connection — caller is responsible for lifecycle.
     */
    public DailyRecord saveWithConnection(Connection conn, DailyRecord record) throws SQLException {
        String sql = """
            INSERT INTO daily_records (date, time_start, time_end, km_start, km_end, earn_uber, earn_99, earn_others, cost_fuel, cost_food_other, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setStatementParameters(pstmt, record);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        record.setId(rs.getLong(1));
                    }
                }
            }
            return record;
        }
    }

    /**
     * Updates an existing DailyRecord in the database.
     * @return true if record was updated, false otherwise.
     */
    public boolean update(DailyRecord record) {
        if (record.getId() == null) {
            throw new IllegalArgumentException("Record ID cannot be null for update");
        }

        String sql = """
            UPDATE daily_records
            SET date = ?, time_start = ?, time_end = ?, km_start = ?, km_end = ?,
                earn_uber = ?, earn_99 = ?, earn_others = ?, cost_fuel = ?, cost_food_other = ?, notes = ?
            WHERE id = ?
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            setStatementParameters(pstmt, record);
            pstmt.setLong(12, record.getId());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error updating daily record ID: " + record.getId(), e);
            throw new RuntimeException("Failed to update DailyRecord", e);
        }
    }

    /**
     * Deletes a DailyRecord by its ID.
     * @return true if deleted, false otherwise.
     */
    public boolean delete(Long id) {
        String sql = "DELETE FROM daily_records WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error deleting daily record ID: " + id, e);
            throw new RuntimeException("Failed to delete DailyRecord", e);
        }
    }

    /**
     * Finds a DailyRecord by ID.
     */
    public DailyRecord findById(Long id) {
        String sql = "SELECT * FROM daily_records WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToRecord(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding daily record by ID: " + id, e);
            throw new RuntimeException("Failed to find DailyRecord", e);
        }
        return null;
    }

    /**
     * Retrieves all DailyRecords ordered by date descending.
     */
    public List<DailyRecord> findAll() {
        String sql = "SELECT * FROM daily_records ORDER BY date DESC, time_start DESC";
        List<DailyRecord> records = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                records.add(mapResultSetToRecord(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error fetching all daily records", e);
            throw new RuntimeException("Failed to fetch DailyRecords", e);
        }
        return records;
    }

    /**
     * Finds records within an inclusive date range.
     */
    public List<DailyRecord> findByDateRange(LocalDate startDate, LocalDate endDate) {
        String sql = "SELECT * FROM daily_records WHERE date >= ? AND date <= ? ORDER BY date DESC, time_start DESC";
        List<DailyRecord> records = new ArrayList<>();

        LocalDate start = startDate != null ? startDate : LocalDate.of(1970, 1, 1);
        LocalDate end = endDate != null ? endDate : LocalDate.of(2099, 12, 31);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, start.format(DATE_FORMATTER));
            pstmt.setString(2, end.format(DATE_FORMATTER));

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    records.add(mapResultSetToRecord(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error fetching daily records by date range", e);
            throw new RuntimeException("Failed to fetch DailyRecords by date range", e);
        }
        return records;
    }

    /**
     * Calculates financial summary over a date range using domain calculations.
     * @param startDate start date inclusive
     * @param endDate end date inclusive
     * @param maintRatePerKm maintenance cost reserve per kilometer (e.g., 0.20)
     */
    public FinancialSummary getSummaryByDateRange(LocalDate startDate, LocalDate endDate, double maintRatePerKm) {
        List<DailyRecord> records = findByDateRange(startDate, endDate);

        double totalGross = 0.0;
        double totalCosts = 0.0;
        double totalKm = 0.0;
        double totalHours = 0.0;

        for (DailyRecord r : records) {
            totalGross += r.calculateGrossIncome();
            totalCosts += r.calculateTotalCosts();
            totalKm += r.calculateKm();
            totalHours += r.calculateHours();
        }

        double totalMaint = totalKm * maintRatePerKm;
        double totalNet = totalGross - totalCosts - totalMaint;
        double avgProfitPerHour = totalHours > 0 ? totalNet / totalHours : 0.0;
        double avgProfitPerKm = totalKm > 0 ? totalNet / totalKm : 0.0;

        return new FinancialSummary(
            totalGross, totalCosts, totalMaint, totalNet,
            avgProfitPerHour, avgProfitPerKm, totalHours, totalKm
        );
    }

    /**
     * Overloaded method defaulting maintenance reserve rate to R$ 0.20/km.
     */
    public FinancialSummary getSummaryByDateRange(LocalDate startDate, LocalDate endDate) {
        return getSummaryByDateRange(startDate, endDate, 0.20);
    }

    // Helper Methods

    private void setStatementParameters(PreparedStatement pstmt, DailyRecord record) throws SQLException {
        pstmt.setString(1, record.getDate().format(DATE_FORMATTER));
        pstmt.setString(2, record.getTimeStart().format(TIME_FORMATTER));
        pstmt.setString(3, record.getTimeEnd().format(TIME_FORMATTER));
        pstmt.setDouble(4, record.getKmStart());
        pstmt.setDouble(5, record.getKmEnd());
        pstmt.setDouble(6, record.getEarnUber());
        pstmt.setDouble(7, record.getEarn99());
        pstmt.setDouble(8, record.getEarnOthers());
        pstmt.setDouble(9, record.getCostFuel());
        pstmt.setDouble(10, record.getCostFoodOther());
        pstmt.setString(11, record.getNotes());
    }

    private DailyRecord mapResultSetToRecord(ResultSet rs) throws SQLException {
        DailyRecord record = new DailyRecord();
        record.setId(rs.getLong("id"));
        record.setDate(LocalDate.parse(rs.getString("date"), DATE_FORMATTER));
        record.setTimeStart(LocalTime.parse(rs.getString("time_start"), TIME_FORMATTER));
        record.setTimeEnd(LocalTime.parse(rs.getString("time_end"), TIME_FORMATTER));
        record.setKmStart(rs.getDouble("km_start"));
        record.setKmEnd(rs.getDouble("km_end"));
        record.setEarnUber(rs.getDouble("earn_uber"));
        record.setEarn99(rs.getDouble("earn_99"));
        record.setEarnOthers(rs.getDouble("earn_others"));
        record.setCostFuel(rs.getDouble("cost_fuel"));
        record.setCostFoodOther(rs.getDouble("cost_food_other"));
        record.setNotes(rs.getString("notes"));
        return record;
    }
}
