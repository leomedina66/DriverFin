package com.driverfin.dao;

import com.driverfin.model.WorkShift;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WorkShiftDAO {
    private static final Logger LOGGER = Logger.getLogger(WorkShiftDAO.class.getName());
    private static final BigDecimal CENTS_PER_REAL = new BigDecimal("100");
    private final DatabaseConnection dbConnection;

    public WorkShiftDAO(DatabaseConnection dbConnection) {
        this.dbConnection = dbConnection;
    }

    public List<WorkShift> findAll() throws SQLException {
        List<WorkShift> shifts = new ArrayList<>();
        String sql = "SELECT * FROM work_shifts ORDER BY date DESC, time_start DESC";
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    shifts.add(mapResultSetToWorkShift(rs));
                } catch (DateTimeException unreadable) {
                    // One damaged row must not hide all the others. Only the id is logged, never the content.
                    LOGGER.log(Level.WARNING, "Shift {0} skipped: its date or time cannot be read.", rs.getLong("id"));
                }
            }
        }
        return shifts;
    }

    /**
     * The shift with the latest date; on a tie the latest start time; on a tie the highest id (saved last).
     * ISO dates and "HH:mm" times sort correctly as text.
     */
    public Optional<WorkShift> findLastShift() throws SQLException {
        String sql = "SELECT * FROM work_shifts ORDER BY date DESC, time_start DESC, id DESC LIMIT 1";
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? Optional.of(mapResultSetToWorkShift(rs)) : Optional.empty();
        }
    }

    public void save(WorkShift shift) throws SQLException {
        if (shift.getId() == null) insert(shift);
        else update(shift);
    }

    private void insert(WorkShift shift) throws SQLException {
        String sql = "INSERT INTO work_shifts (date, time_start, time_end, km_start, km_end, uber_earnings, app99_earnings, other_earnings, cost_fuel, food_and_other_costs, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setPreparedStatementArgs(pstmt, shift);
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) shift.setId(rs.getLong(1));
            }
        }
    }

    private void update(WorkShift shift) throws SQLException {
        String sql = "UPDATE work_shifts SET date=?, time_start=?, time_end=?, km_start=?, km_end=?, uber_earnings=?, app99_earnings=?, other_earnings=?, cost_fuel=?, food_and_other_costs=?, notes=? WHERE id=?";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            setPreparedStatementArgs(pstmt, shift);
            pstmt.setLong(12, shift.getId());
            pstmt.executeUpdate();
        }
    }

    public void delete(Long id) throws SQLException {
        String sql = "DELETE FROM work_shifts WHERE id=?";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            pstmt.executeUpdate();
        }
    }

    private void setPreparedStatementArgs(PreparedStatement pstmt, WorkShift shift) throws SQLException {
        pstmt.setString(1, shift.getDate().toString());
        pstmt.setString(2, shift.getTimeStart().toString());
        pstmt.setString(3, shift.getTimeEnd().toString());
        pstmt.setDouble(4, shift.getKmStart());
        pstmt.setDouble(5, shift.getKmEnd());
        pstmt.setLong(6, toCents(shift.getUberEarnings()));
        pstmt.setLong(7, toCents(shift.getApp99Earnings()));
        pstmt.setLong(8, toCents(shift.getOtherEarnings()));
        pstmt.setLong(9, toCents(shift.getCostFuel()));
        pstmt.setLong(10, toCents(shift.getFoodAndOtherCosts()));
        pstmt.setString(11, shift.getNotes() != null ? shift.getNotes() : "");
    }

    /** Reais to cents. longValueExact throws ArithmeticException on overflow instead of wrapping silently. */
    private static long toCents(BigDecimal reais) {
        return reais.multiply(CENTS_PER_REAL).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents).divide(CENTS_PER_REAL, 2, RoundingMode.HALF_UP);
    }

    /** A missing date or time is as unreadable as a malformed one: the same exception, so the row can be skipped. */
    private static String requiredText(ResultSet rs, String column) throws SQLException {
        String text = rs.getString(column);
        if (text == null) throw new DateTimeException("Missing value in column " + column);
        return text;
    }

    private WorkShift mapResultSetToWorkShift(ResultSet rs) throws SQLException {
        return new WorkShift(
            rs.getLong("id"),
            LocalDate.parse(requiredText(rs, "date")),
            LocalTime.parse(requiredText(rs, "time_start")),
            LocalTime.parse(requiredText(rs, "time_end")),
            rs.getDouble("km_start"),
            rs.getDouble("km_end"),
            fromCents(rs.getLong("uber_earnings")),
            fromCents(rs.getLong("app99_earnings")),
            fromCents(rs.getLong("other_earnings")),
            fromCents(rs.getLong("cost_fuel")),
            fromCents(rs.getLong("food_and_other_costs")),
            rs.getString("notes")
        );
    }
}
