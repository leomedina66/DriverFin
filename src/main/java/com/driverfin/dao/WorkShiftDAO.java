package com.driverfin.dao;

import com.driverfin.model.WorkShift;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class WorkShiftDAO {
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
                shifts.add(mapResultSetToWorkShift(rs));
            }
        }
        return shifts;
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
        pstmt.setLong(6, shift.getUberEarnings().multiply(new BigDecimal("100")).longValue());
        pstmt.setLong(7, shift.getApp99Earnings().multiply(new BigDecimal("100")).longValue());
        pstmt.setLong(8, shift.getOtherEarnings().multiply(new BigDecimal("100")).longValue());
        pstmt.setLong(9, shift.getCostFuel().multiply(new BigDecimal("100")).longValue());
        pstmt.setLong(10, shift.getFoodAndOtherCosts().multiply(new BigDecimal("100")).longValue());
        pstmt.setString(11, shift.getNotes() != null ? shift.getNotes() : "");
    }

    private WorkShift mapResultSetToWorkShift(ResultSet rs) throws SQLException {
        return new WorkShift(
            rs.getLong("id"),
            LocalDate.parse(rs.getString("date")),
            LocalTime.parse(rs.getString("time_start")),
            LocalTime.parse(rs.getString("time_end")),
            rs.getDouble("km_start"),
            rs.getDouble("km_end"),
            new BigDecimal(rs.getLong("uber_earnings")).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP),
            new BigDecimal(rs.getLong("app99_earnings")).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP),
            new BigDecimal(rs.getLong("other_earnings")).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP),
            new BigDecimal(rs.getLong("cost_fuel")).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP),
            new BigDecimal(rs.getLong("food_and_other_costs")).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP),
            rs.getString("notes")
        );
    }
}
