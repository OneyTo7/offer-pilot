package com.eyki.offerpilot.common.config;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

/**
 * PostgreSQL JSONB 类型处理器。
 *
 * <p>将 Java String 与 PostgreSQL JSONB 列互转。
 * MyBatis 默认把 String 参数当作 varchar 发送，PostgreSQL 不允许 String → JSONB 隐式转换，
 * 此处理器确保写入时包装为 PGobject(type=jsonb)，读取时返回字符串。</p>
 *
 * <p>使用方式：在实体类的 JSONB 映射字段上加 {@code @TableField(typeHandler = PgJsonbTypeHandler.class)}</p>
 */
@MappedTypes({String.class})
@MappedJdbcTypes({JdbcType.OTHER})
public class PgJsonbTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        PGobject obj = new PGobject();
        obj.setType("jsonb");
        obj.setValue(parameter);
        ps.setObject(i, obj);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Object obj = rs.getObject(columnName);
        return obj == null ? null : obj.toString();
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Object obj = rs.getObject(columnIndex);
        return obj == null ? null : obj.toString();
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Object obj = cs.getObject(columnIndex);
        return obj == null ? null : obj.toString();
    }
}