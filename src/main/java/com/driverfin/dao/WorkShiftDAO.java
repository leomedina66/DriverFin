package com.driverfin.dao;

import com.driverfin.model.WorkShift;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;

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
        if (shift.getId() == null) {
            insert(shift);
        } else {
            update(shift);
        }
    }

    private void insert(WorkShift shift) throws SQLException {
        String sql = "INSERT INTO work_shifts (date, time_start, time_end, km_start, km_end, " +
                     "uber_earnings, app99_earnings, other_earnings, cost_fuel, food_and_other_costs, notes) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setPreparedStatementArgs(pstmt, shift);
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    shift.setId(rs.getLong(1));
                }
            }
        }
    }

    private void update(WorkShift shift) throws SQLException {
        String sql = "UPDATE work_shifts SET date=?, time_start=?, time_end=?, km_start=?, km_end=?, " +
                     "uber_earnings=?, app99_earnings=?, other_earnings=?, cost_fuel=?, food_and_other_costs=?, notes=? " +
                     "WHERE id=?";
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
        pstmt.setDouble(6, shift.getUberEarnings().doubleValue());
        pstmt.setDouble(7, shift.getApp99Earnings().doubleValue());
        pstmt.setDouble(8, shift.getOtherEarnings().doubleValue());
        pstmt.setDouble(9, shift.getCostFuel().doubleValue());
        pstmt.setDouble(10, shift.getFoodAndOtherCosts().doubleValue());
        pstmt.setString(11, shift.getNotes());
    }

    private WorkShift mapResultSetToWorkShift(ResultSet rs) throws SQLException {
        return new WorkShift(
            rs.getLong("id"),
            LocalDate.parse(rs.getString("date")),
            LocalTime.parse(rs.getString("time_start")),
            LocalTime.parse(rs.getString("time_end")),
            rs.getDouble("km_start"),
            rs.getDouble("km_end"),
            BigDecimal.valueOf(rs.getDouble("uber_earnings")),
            BigDecimal.valueOf(rs.getDouble("app99_earnings")),
            BigDecimal.valueOf(rs.getDouble("other_earnings")),
            BigDecimal.valueOf(rs.getDouble("cost_fuel")),
            BigDecimal.valueOf(rs.getDouble("food_and_other_costs")),
            rs.getString("notes")
        );
    }
}
