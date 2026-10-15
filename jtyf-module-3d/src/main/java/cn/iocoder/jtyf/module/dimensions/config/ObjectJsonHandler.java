package cn.iocoder.jtyf.module.dimensions.config;

import com.alibaba.fastjson.JSONObject;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


@MappedTypes(JSONObject.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class ObjectJsonHandler extends BaseTypeHandler<JSONObject> {
    //设置非空参数
    @Override
    public void setNonNullParameter(PreparedStatement preparedStatement, int i, JSONObject jsonObject, JdbcType jdbcType) throws SQLException {
        preparedStatement.setString(i,String.valueOf(jsonObject.toJSONString()));
    }
    //根据列名，获取可以为空的结果
    @Override
    public JSONObject getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        String sqlJson = resultSet.getString(columnName);
        if(null != sqlJson){
            return JSONObject.parseObject(sqlJson);
        }
        return null;
    }
    //根据列索引，获取可以为空的结果
    @Override
    public JSONObject getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        String sqlJson = resultSet.getString(columnIndex);
        if (null != sqlJson){
            return JSONObject.parseObject(sqlJson);
        }
        return null;
    }

    @Override
    public JSONObject getNullableResult(CallableStatement callableStatement, int columnIndex) throws SQLException {
        String sqlJson = callableStatement.getString(columnIndex);
        if (null != sqlJson){
            return JSONObject.parseObject(sqlJson);
        }
        return null;
    }
}
