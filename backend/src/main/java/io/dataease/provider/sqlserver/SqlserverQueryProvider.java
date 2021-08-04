package io.dataease.provider.sqlserver;

import io.dataease.base.domain.DatasetTableField;
import io.dataease.controller.request.chart.ChartExtFilterRequest;
import io.dataease.dto.chart.ChartCustomFilterDTO;
import io.dataease.dto.chart.ChartViewFieldDTO;
import io.dataease.dto.sqlObj.SQLObj;
import io.dataease.provider.QueryProvider;
import io.dataease.provider.SQLConstants;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static io.dataease.provider.SQLConstants.TABLE_ALIAS_PREFIX;

@Service("sqlserverQuery")
public class SqlserverQueryProvider extends QueryProvider {
    @Override
    public Integer transFieldType(String field) {
        switch (field) {
            case "CHAR":
            case "NCHAR":
            case "NTEXT":
            case "VARCHAR":
            case "TEXT":
            case "TINYTEXT":
            case "MEDIUMTEXT":
            case "LONGTEXT":
            case "ENUM":
            case "XML":
                return 0;// 文本
            case "DATE":
            case "TIME":
            case "YEAR":
            case "DATETIME":
            case "DATETIME2":
            case "DATETIMEOFFSET":
            case "TIMESTAMP":
                return 1;// 时间
            case "INT":
            case "MEDIUMINT":
            case "INTEGER":
            case "BIGINT":
            case "SMALLINT":
                return 2;// 整型
            case "FLOAT":
            case "DOUBLE":
            case "DECIMAL":
            case "MONEY":
            case "NUMERIC":
                return 3;// 浮点
            case "BIT":
            case "TINYINT":
                return 4;// 布尔
            default:
                return 0;
        }
    }

    private static Integer DE_STRING = 0;
    private static Integer DE_TIME = 1;
    private static Integer DE_INT = 2;
    private static Integer DE_FLOAT = 3;
    private static Integer DE_BOOL = 4;
    @Override
    public String createSQLPreview(String sql, String orderBy) {
        return "SELECT top 1000 * FROM (" + sqlFix(sql) + ") AS tmp";
    }

    @Override
    public String createQuerySQL(String table, List<DatasetTableField> fields, boolean isGroup) {
        SQLObj tableObj = SQLObj.builder()
                .tableName((table.startsWith("(") && table.endsWith(")")) ? table : String.format(SqlServerSQLConstants.KEYWORD_TABLE, table))
                .tableAlias(String.format(TABLE_ALIAS_PREFIX, 0))
                .build();
        List<SQLObj> xFields = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(fields)) {
            for (int i = 0; i < fields.size(); i++) {
                DatasetTableField f = fields.get(i);
                String originField = String.format(SqlServerSQLConstants.KEYWORD_FIX, tableObj.getTableAlias(), f.getOriginName());
                String fieldAlias = String.format(SQLConstants.FIELD_ALIAS_X_PREFIX, i);
                String fieldName = "";
                // 处理横轴字段
                if (f.getDeExtractType() == DE_TIME) { // 时间 转为 数值
                    if (f.getDeType() == DE_INT || f.getDeType() == DE_FLOAT) {
                        fieldName = String.format(SqlServerSQLConstants.UNIX_TIMESTAMP, originField);
                    } else {
                        fieldName = originField;
                    }
                } else if (f.getDeExtractType() == DE_STRING) {  //字符串转时间
                    if (f.getDeType() == DE_INT) {
                        fieldName = originField;
//                        String.format(SqlServerSQLConstants.CAST, originField, SqlServerSQLConstants.DEFAULT_INT_FORMAT);
                    } else if (f.getDeType() == DE_FLOAT) {
                        fieldName = originField;
//                        String.format(SqlServerSQLConstants.CAST, originField, SqlServerSQLConstants.DEFAULT_FLOAT_FORMAT);
                    } else if (f.getDeType() == DE_TIME) {
                        fieldName = originField;
//                        String.format(SqlServerSQLConstants.DATE_FORMAT, originField, SqlServerSQLConstants.DEFAULT_DATE_FORMAT);
                    } else {
                        fieldName = originField;
                    }
                } else {
                    if (f.getDeType() == DE_TIME) { //
                        String cast = String.format(SqlServerSQLConstants.CAST, originField, SqlServerSQLConstants.DEFAULT_INT_FORMAT) + "/1000";
                        fieldName = originField;
//                        String.format(SqlServerSQLConstants.FROM_UNIXTIME, cast, SqlServerSQLConstants.DEFAULT_DATE_FORMAT);
                    } else if (f.getDeType() == DE_INT) {
                        fieldName = originField;
//                        String.format(SqlServerSQLConstants.CAST, originField, SqlServerSQLConstants.DEFAULT_INT_FORMAT);
                    } else {
                        fieldName = originField;
                    }
                }
                xFields.add(SQLObj.builder()
                        .fieldName(fieldName)
                        .fieldAlias(fieldAlias)
                        .build());
            }
        }

        STGroup stg = new STGroupFile(SQLConstants.SQL_TEMPLATE);
        ST st_sql = stg.getInstanceOf("previewSql");
        st_sql.add("isGroup", isGroup);
        if (CollectionUtils.isNotEmpty(xFields)) st_sql.add("groups", xFields);
        if (ObjectUtils.isNotEmpty(tableObj)) st_sql.add("table", tableObj);
        return st_sql.render();
    }

    @Override
    public String createQuerySQLAsTmp(String sql, List<DatasetTableField> fields, boolean isGroup) {
        return createQuerySQL("(" + sqlFix(sql) + ")", fields, isGroup);
    }

    @Override
    public String createQuerySQLWithPage(String table, List<DatasetTableField> fields, Integer page, Integer pageSize, Integer realSize, boolean isGroup) {
        return createQuerySQL(table, fields, isGroup) + " ORDER BY " + fields.get(0).getOriginName() + " offset " + (page - 1) * pageSize + " rows fetch next " + realSize + " rows only";
    }

    @Override
    public String createQuerySQLAsTmpWithPage(String sql, List<DatasetTableField> fields, Integer page, Integer pageSize, Integer realSize, boolean isGroup) {
        return createQuerySQLAsTmp(sql, fields, isGroup) + " ORDER BY " + fields.get(0).getOriginName() + " offset " + (page - 1) * pageSize + " rows fetch next " + realSize + " rows only";
    }

    @Override
    public String createQueryTableWithLimit(String table, List<DatasetTableField> fields, Integer limit, boolean isGroup) {
        return createQuerySQL(table, fields, isGroup) + " ORDER BY " + fields.get(0).getOriginName() + " offset  0 rows fetch next " + limit  + " rows only";
    }

    @Override
    public String createQuerySqlWithLimit(String sql, List<DatasetTableField> fields, Integer limit, boolean isGroup) {
        return createQuerySQLAsTmp(sql, fields, isGroup) + " ORDER BY " + fields.get(0).getOriginName() + " offset  0 rows fetch next " + limit  + " rows only";
    }

    @Override
    public String getSQL(String table, List<ChartViewFieldDTO> xAxis, List<ChartViewFieldDTO> yAxis, List<ChartCustomFilterDTO> customFilter, List<ChartExtFilterRequest> extFilterRequestList) {
        SQLObj tableObj = SQLObj.builder()
                .tableName((table.startsWith("(") && table.endsWith(")")) ? table : String.format(SqlServerSQLConstants.KEYWORD_TABLE, table))
                .tableAlias(String.format(TABLE_ALIAS_PREFIX, 0))
                .build();
        List<SQLObj> xFields = new ArrayList<>();
        List<SQLObj> xWheres = new ArrayList<>();
        List<SQLObj> xOrders = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(xAxis)) {
            for (int i = 0; i < xAxis.size(); i++) {
                ChartViewFieldDTO x = xAxis.get(i);
                String originField = String.format(SqlServerSQLConstants.KEYWORD_FIX, tableObj.getTableAlias(), x.getOriginName());
                String fieldAlias = String.format(SQLConstants.FIELD_ALIAS_X_PREFIX, i);
                // 处理横轴字段
                xFields.add(getXFields(x, originField, fieldAlias));
                // 处理横轴过滤
//                xWheres.addAll(getXWheres(x, originField, fieldAlias));
                // 处理横轴排序
                if (StringUtils.isNotEmpty(x.getSort()) && !StringUtils.equalsIgnoreCase(x.getSort(), "none")) {
                    xOrders.add(SQLObj.builder()
                            .orderField(originField)
                            .orderAlias(fieldAlias)
                            .orderDirection(x.getSort())
                            .build());
                }
            }
        }
        List<SQLObj> yFields = new ArrayList<>();
        List<SQLObj> yWheres = new ArrayList<>();
        List<SQLObj> yOrders = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(yAxis)) {
            for (int i = 0; i < yAxis.size(); i++) {
                ChartViewFieldDTO y = yAxis.get(i);
                String originField = String.format(SqlServerSQLConstants.KEYWORD_FIX, tableObj.getTableAlias(), y.getOriginName());
                String fieldAlias = String.format(SQLConstants.FIELD_ALIAS_Y_PREFIX, i);
                // 处理纵轴字段
                yFields.add(getYFields(y, originField, fieldAlias));
                // 处理纵轴过滤
                yWheres.addAll(getYWheres(y, originField, fieldAlias));
                // 处理纵轴排序
                if (StringUtils.isNotEmpty(y.getSort()) && !StringUtils.equalsIgnoreCase(y.getSort(), "none")) {
                    yOrders.add(SQLObj.builder()
                            .orderField(originField)
                            .orderAlias(fieldAlias)
                            .orderDirection(y.getSort())
                            .build());
                }
            }
        }
        // 处理视图中字段过滤
        List<SQLObj> customWheres = transCustomFilterList(tableObj, customFilter);
        // 处理仪表板字段过滤
        List<SQLObj> extWheres = transExtFilterList(tableObj, extFilterRequestList);
        // 构建sql所有参数
        List<SQLObj> fields = new ArrayList<>();
        fields.addAll(xFields);
        fields.addAll(yFields);
        List<SQLObj> wheres = new ArrayList<>();
        wheres.addAll(xWheres);
        if (customWheres != null) wheres.addAll(customWheres);
        if (extWheres != null) wheres.addAll(extWheres);
        List<SQLObj> groups = new ArrayList<>();
        groups.addAll(xFields);
        // 外层再次套sql
        List<SQLObj> orders = new ArrayList<>();
        orders.addAll(xOrders);
        orders.addAll(yOrders);
        List<SQLObj> aggWheres = new ArrayList<>();
        aggWheres.addAll(yWheres);

        STGroup stg = new STGroupFile(SQLConstants.SQL_TEMPLATE);
        ST st_sql = stg.getInstanceOf("querySql");
        if (CollectionUtils.isNotEmpty(xFields)) st_sql.add("groups", xFields);
        if (CollectionUtils.isNotEmpty(yFields)) st_sql.add("aggregators", yFields);
        if (CollectionUtils.isNotEmpty(wheres)) st_sql.add("filters", wheres);
        if (ObjectUtils.isNotEmpty(tableObj)) st_sql.add("table", tableObj);
        String sql = st_sql.render();

        ST st = stg.getInstanceOf("querySql");
        SQLObj tableSQL = SQLObj.builder()
                .tableName(String.format(SqlServerSQLConstants.BRACKETS, sql))
                .tableAlias(String.format(TABLE_ALIAS_PREFIX, 1))
                .build();
        if (CollectionUtils.isNotEmpty(aggWheres)) st.add("filters", aggWheres);
        if (CollectionUtils.isNotEmpty(orders)) st.add("orders", orders);
        if (ObjectUtils.isNotEmpty(tableSQL)) st.add("table", tableSQL);
        return st.render();
    }


    @Override
    public String getSQLAsTmp(String sql, List<ChartViewFieldDTO> xAxis, List<ChartViewFieldDTO> yAxis, List<ChartCustomFilterDTO> customFilter, List<ChartExtFilterRequest> extFilterRequestList) {
        return getSQL("(" + sqlFix(sql) + ")", xAxis, yAxis, customFilter, extFilterRequestList);
    }

    @Override
    public String getSQLStack(String table, List<ChartViewFieldDTO> xAxis, List<ChartViewFieldDTO> yAxis, List<ChartCustomFilterDTO> customFilter, List<ChartExtFilterRequest> extFilterRequestList, List<ChartViewFieldDTO> extStack) {
        SQLObj tableObj = SQLObj.builder()
                .tableName((table.startsWith("(") && table.endsWith(")")) ? table : String.format(SqlServerSQLConstants.KEYWORD_TABLE, table))
                .tableAlias(String.format(TABLE_ALIAS_PREFIX, 0))
                .build();
        List<SQLObj> xFields = new ArrayList<>();
        List<SQLObj> xWheres = new ArrayList<>();
        List<SQLObj> xOrders = new ArrayList<>();
        List<ChartViewFieldDTO> xList = new ArrayList<>();
        xList.addAll(xAxis);
        xList.addAll(extStack);
        if (CollectionUtils.isNotEmpty(xList)) {
            for (int i = 0; i < xList.size(); i++) {
                ChartViewFieldDTO x = xList.get(i);
                String originField = String.format(SqlServerSQLConstants.KEYWORD_FIX, tableObj.getTableAlias(), x.getOriginName());
                String fieldAlias = String.format(SQLConstants.FIELD_ALIAS_X_PREFIX, i);
                // 处理横轴字段
                xFields.add(getXFields(x, originField, fieldAlias));
                // 处理横轴过滤
//                xWheres.addAll(getXWheres(x, originField, fieldAlias));
                // 处理横轴排序
                if (StringUtils.isNotEmpty(x.getSort()) && !StringUtils.equalsIgnoreCase(x.getSort(), "none")) {
                    xOrders.add(SQLObj.builder()
                            .orderField(originField)
                            .orderAlias(fieldAlias)
                            .orderDirection(x.getSort())
                            .build());
                }
            }
        }
        List<SQLObj> yFields = new ArrayList<>();
        List<SQLObj> yWheres = new ArrayList<>();
        List<SQLObj> yOrders = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(yAxis)) {
            for (int i = 0; i < yAxis.size(); i++) {
                ChartViewFieldDTO y = yAxis.get(i);
                String originField = String.format(SqlServerSQLConstants.KEYWORD_FIX, tableObj.getTableAlias(), y.getOriginName());
                String fieldAlias = String.format(SQLConstants.FIELD_ALIAS_Y_PREFIX, i);
                // 处理纵轴字段
                yFields.add(getYFields(y, originField, fieldAlias));
                // 处理纵轴过滤
                yWheres.addAll(getYWheres(y, originField, fieldAlias));
                // 处理纵轴排序
                if (StringUtils.isNotEmpty(y.getSort()) && !StringUtils.equalsIgnoreCase(y.getSort(), "none")) {
                    yOrders.add(SQLObj.builder()
                            .orderField(originField)
                            .orderAlias(fieldAlias)
                            .orderDirection(y.getSort())
                            .build());
                }
            }
        }
        List<SQLObj> stackFields = new ArrayList<>();
        List<SQLObj> stackOrders = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(extStack)) {
            for (int i = 0; i < extStack.size(); i++) {
                ChartViewFieldDTO stack = extStack.get(i);
                String originField = String.format(SqlServerSQLConstants.KEYWORD_FIX, tableObj.getTableAlias(), stack.getOriginName());
                String fieldAlias = String.format(SQLConstants.FIELD_ALIAS_X_PREFIX, i);
                // 处理横轴字段
                stackFields.add(getXFields(stack, originField, fieldAlias));
                // 处理横轴排序
                if (StringUtils.isNotEmpty(stack.getSort()) && !StringUtils.equalsIgnoreCase(stack.getSort(), "none")) {
                    stackOrders.add(SQLObj.builder()
                            .orderField(originField)
                            .orderAlias(fieldAlias)
                            .orderDirection(stack.getSort())
                            .build());
                }
            }
        }
        // 处理视图中字段过滤
        List<SQLObj> customWheres = transCustomFilterList(tableObj, customFilter);
        // 处理仪表板字段过滤
        List<SQLObj> extWheres = transExtFilterList(tableObj, extFilterRequestList);
        // 构建sql所有参数
        List<SQLObj> fields = new ArrayList<>();
        fields.addAll(xFields);
        fields.addAll(yFields);
        List<SQLObj> wheres = new ArrayList<>();
        wheres.addAll(xWheres);
        if (customWheres != null) wheres.addAll(customWheres);
        if (extWheres != null) wheres.addAll(extWheres);
        List<SQLObj> groups = new ArrayList<>();
        groups.addAll(xFields);
        // 外层再次套sql
        List<SQLObj> orders = new ArrayList<>();
        orders.addAll(xOrders);
        orders.addAll(yOrders);
        List<SQLObj> aggWheres = new ArrayList<>();
        aggWheres.addAll(yWheres);

        STGroup stg = new STGroupFile(SQLConstants.SQL_TEMPLATE);
        ST st_sql = stg.getInstanceOf("querySql");
        if (CollectionUtils.isNotEmpty(xFields)) st_sql.add("groups", xFields);
        if (CollectionUtils.isNotEmpty(yFields)) st_sql.add("aggregators", yFields);
        if (CollectionUtils.isNotEmpty(wheres)) st_sql.add("filters", wheres);
        if (ObjectUtils.isNotEmpty(tableObj)) st_sql.add("table", tableObj);
        String sql = st_sql.render();

        ST st = stg.getInstanceOf("querySql");
        SQLObj tableSQL = SQLObj.builder()
                .tableName(String.format(SqlServerSQLConstants.BRACKETS, sql))
                .tableAlias(String.format(TABLE_ALIAS_PREFIX, 1))
                .build();
        if (CollectionUtils.isNotEmpty(aggWheres)) st.add("filters", aggWheres);
        if (CollectionUtils.isNotEmpty(orders)) st.add("orders", orders);
        if (ObjectUtils.isNotEmpty(tableSQL)) st.add("table", tableSQL);
        return st.render();
    }

    @Override
    public String getSQLAsTmpStack(String table, List<ChartViewFieldDTO> xAxis, List<ChartViewFieldDTO> yAxis, List<ChartCustomFilterDTO> customFilter, List<ChartExtFilterRequest> extFilterRequestList, List<ChartViewFieldDTO> extStack) {
        return getSQLStack("(" + sqlFix(table) + ")", xAxis, yAxis, customFilter, extFilterRequestList, extStack);
    }

    @Override
    public String searchTable(String table) {
        return "SELECT table_name FROM information_schema.TABLES WHERE table_name ='" + table + "'";
    }

    @Override
    public String getSQLSummary(String table, List<ChartViewFieldDTO> yAxis, List<ChartCustomFilterDTO> customFilter, List<ChartExtFilterRequest> extFilterRequestList) {
        // 字段汇总 排序等
        SQLObj tableObj = SQLObj.builder()
                .tableName((table.startsWith("(") && table.endsWith(")")) ? table : String.format(SqlServerSQLConstants.KEYWORD_TABLE, table))
                .tableAlias(String.format(TABLE_ALIAS_PREFIX, 0))
                .build();
        List<SQLObj> yFields = new ArrayList<>();
        List<SQLObj> yWheres = new ArrayList<>();
        List<SQLObj> yOrders = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(yAxis)) {
            for (int i = 0; i < yAxis.size(); i++) {
                ChartViewFieldDTO y = yAxis.get(i);
                String originField = String.format(SqlServerSQLConstants.KEYWORD_FIX, tableObj.getTableAlias(), y.getOriginName());
                String fieldAlias = String.format(SQLConstants.FIELD_ALIAS_Y_PREFIX, i);
                // 处理纵轴字段
                yFields.add(getYFields(y, originField, fieldAlias));
                // 处理纵轴过滤
                yWheres.addAll(getYWheres(y, originField, fieldAlias));
                // 处理纵轴排序
                if (StringUtils.isNotEmpty(y.getSort()) && !StringUtils.equalsIgnoreCase(y.getSort(), "none")) {
                    yOrders.add(SQLObj.builder()
                            .orderField(originField)
                            .orderAlias(fieldAlias)
                            .orderDirection(y.getSort())
                            .build());
                }
            }
        }
        // 处理视图中字段过滤
        List<SQLObj> customWheres = transCustomFilterList(tableObj, customFilter);
        // 处理仪表板字段过滤
        List<SQLObj> extWheres = transExtFilterList(tableObj, extFilterRequestList);
        // 构建sql所有参数
        List<SQLObj> fields = new ArrayList<>();
        fields.addAll(yFields);
        List<SQLObj> wheres = new ArrayList<>();
        if (customWheres != null) wheres.addAll(customWheres);
        if (extWheres != null) wheres.addAll(extWheres);
        List<SQLObj> groups = new ArrayList<>();
        // 外层再次套sql
        List<SQLObj> orders = new ArrayList<>();
        orders.addAll(yOrders);
        List<SQLObj> aggWheres = new ArrayList<>();
        aggWheres.addAll(yWheres);

        STGroup stg = new STGroupFile(SQLConstants.SQL_TEMPLATE);
        ST st_sql = stg.getInstanceOf("querySql");
        if (CollectionUtils.isNotEmpty(yFields)) st_sql.add("aggregators", yFields);
        if (CollectionUtils.isNotEmpty(wheres)) st_sql.add("filters", wheres);
        if (ObjectUtils.isNotEmpty(tableObj)) st_sql.add("table", tableObj);
        String sql = st_sql.render();

        ST st = stg.getInstanceOf("querySql");
        SQLObj tableSQL = SQLObj.builder()
                .tableName(String.format(SqlServerSQLConstants.BRACKETS, sql))
                .tableAlias(String.format(TABLE_ALIAS_PREFIX, 1))
                .build();
        if (CollectionUtils.isNotEmpty(aggWheres)) st.add("filters", aggWheres);
        if (CollectionUtils.isNotEmpty(orders)) st.add("orders", orders);
        if (ObjectUtils.isNotEmpty(tableSQL)) st.add("table", tableSQL);
        return st.render();
    }

    @Override
    public String getSQLSummaryAsTmp(String sql, List<ChartViewFieldDTO> yAxis, List<ChartCustomFilterDTO> customFilter, List<ChartExtFilterRequest> extFilterRequestList) {
        return getSQLSummary("(" + sqlFix(sql) + ")", yAxis, customFilter, extFilterRequestList);
    }

    @Override
    public String wrapSql(String sql) {
        sql = sql.trim();
        if (sql.lastIndexOf(";") == (sql.length() - 1)) {
            sql = sql.substring(0, sql.length() - 1);
        }
        String tmpSql = "SELECT * FROM (" + sql + ") AS tmp " + " LIMIT 0";
        return tmpSql;
    }

    @Override
    public String createRawQuerySQL(String table, List<DatasetTableField> fields) {
        String[] array = fields.stream().map(f -> {
            StringBuilder stringBuilder = new StringBuilder();
            if (f.getDeExtractType() == 4) { // 处理 tinyint
                stringBuilder.append("concat(`").append(f.getOriginName()).append("`,'') AS ").append(f.getDataeaseName());
            } else {
                stringBuilder.append("`").append(f.getOriginName()).append("` AS ").append(f.getDataeaseName());
            }
            return stringBuilder.toString();
        }).toArray(String[]::new);
        return MessageFormat.format("SELECT {0} FROM {1} ORDER BY null", StringUtils.join(array, ","), table);
    }

    @Override
    public String createRawQuerySQLAsTmp(String sql, List<DatasetTableField> fields) {
        return createRawQuerySQL(" (" + sqlFix(sql) + ") AS tmp ", fields);
    }

    public String transMysqlFilterTerm(String term) {
        switch (term) {
            case "eq":
                return " = ";
            case "not_eq":
                return " <> ";
            case "lt":
                return " < ";
            case "le":
                return " <= ";
            case "gt":
                return " > ";
            case "ge":
                return " >= ";
            case "in":
                return " IN ";
            case "not in":
                return " NOT IN ";
            case "like":
                return " LIKE ";
            case "not like":
                return " NOT LIKE ";
            case "null":
                return " IN ";
            case "not_null":
                return " IS NOT NULL AND %s <> ''";
            case "between":
                return " BETWEEN ";
            default:
                return "";
        }
    }

    public List<SQLObj> transCustomFilterList(SQLObj tableObj, List<ChartCustomFilterDTO> requestList) {
        if (CollectionUtils.isEmpty(requestList)) {
            return null;
        }
        List<SQLObj> list = new ArrayList<>();
        for (ChartCustomFilterDTO request : requestList) {
            DatasetTableField field = request.getField();
            if (ObjectUtils.isEmpty(field)) {
                continue;
            }
            String value = request.getValue();
            String whereName = "";
            String whereTerm = transMysqlFilterTerm(request.getTerm());
            String whereValue = "";
            String originName = String.format(SqlServerSQLConstants.KEYWORD_FIX, tableObj.getTableAlias(), field.getOriginName());
            if (field.getDeType() == 1 && field.getDeExtractType() != 1) {
                String cast = String.format(SqlServerSQLConstants.CAST, originName, SqlServerSQLConstants.DEFAULT_INT_FORMAT) + "/1000";
                whereName = String.format(SqlServerSQLConstants.FROM_UNIXTIME, cast, SqlServerSQLConstants.DEFAULT_DATE_FORMAT);
            } else {
                whereName = originName;
            }
            if (StringUtils.equalsIgnoreCase(request.getTerm(), "null")) {
                whereValue = SqlServerSQLConstants.WHERE_VALUE_NULL;
            } else if (StringUtils.equalsIgnoreCase(request.getTerm(), "not_null")) {
                whereTerm = String.format(whereTerm, originName);
            } else if (StringUtils.containsIgnoreCase(request.getTerm(), "in")) {
                whereValue = "('" + StringUtils.join(value, "','") + "')";
            } else if (StringUtils.containsIgnoreCase(request.getTerm(), "like")) {
                whereValue = "'%" + value + "%'";
            } else {
                whereValue = String.format(SqlServerSQLConstants.WHERE_VALUE_VALUE, value);
            }
            list.add(SQLObj.builder()
                    .whereField(whereName)
                    .whereTermAndValue(whereTerm + whereValue)
                    .build());
        }
        return list;
    }

    public List<SQLObj> transExtFilterList(SQLObj tableObj, List<ChartExtFilterRequest> requestList) {
        if (CollectionUtils.isEmpty(requestList)) {
            return null;
        }
        List<SQLObj> list = new ArrayList<>();
        for (ChartExtFilterRequest request : requestList) {
            List<String> value = request.getValue();
            DatasetTableField field = request.getDatasetTableField();
            if (CollectionUtils.isEmpty(value) || ObjectUtils.isEmpty(field)) {
                continue;
            }
            String whereName = "";
            String whereTerm = transMysqlFilterTerm(request.getOperator());
            String whereValue = "";
            String originName = String.format(SqlServerSQLConstants.KEYWORD_FIX, tableObj.getTableAlias(), field.getOriginName());

            if (field.getDeType() == 1 && field.getDeExtractType() != 1) {
                String cast = String.format(SqlServerSQLConstants.CAST, originName, SqlServerSQLConstants.DEFAULT_INT_FORMAT) + "/1000";
                whereName = String.format(SqlServerSQLConstants.FROM_UNIXTIME, cast, SqlServerSQLConstants.DEFAULT_DATE_FORMAT);
            } else {
                whereName = originName;
            }


            if (StringUtils.containsIgnoreCase(request.getOperator(), "in")) {
                whereValue = "('" + StringUtils.join(value, "','") + "')";
            } else if (StringUtils.containsIgnoreCase(request.getOperator(), "like")) {
                whereValue = "'%" + value.get(0) + "%'";
            } else if (StringUtils.containsIgnoreCase(request.getOperator(), "between")) {
                if (request.getDatasetTableField().getDeType() == 1) {
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    String startTime = simpleDateFormat.format(new Date(Long.parseLong(value.get(0))));
                    String endTime = simpleDateFormat.format(new Date(Long.parseLong(value.get(1))));
                    whereValue = String.format(SqlServerSQLConstants.WHERE_BETWEEN, startTime, endTime);
                } else {
                    whereValue = String.format(SqlServerSQLConstants.WHERE_BETWEEN, value.get(0), value.get(1));
                }
            } else {
                whereValue = String.format(SqlServerSQLConstants.WHERE_VALUE_VALUE, value.get(0));
            }
            list.add(SQLObj.builder()
                    .whereField(whereName)
                    .whereTermAndValue(whereTerm + whereValue)
                    .build());
        }
        return list;
    }

    private String sqlFix(String sql) {
        if (sql.lastIndexOf(";") == (sql.length() - 1)) {
            sql = sql.substring(0, sql.length() - 1);
        }
        return sql;
    }

    //日期格式化
//    private String transDateFormat(String dateStyle, String datePattern) {
//        String split = "-";
//        if (StringUtils.equalsIgnoreCase(datePattern, "date_sub")) {
//            split = "-";
//        } else if (StringUtils.equalsIgnoreCase(datePattern, "date_split")) {
//            split = "/";
//        }
//
//        switch (dateStyle) {
//            case "y":
//                return "yyyy";
//            case "y_M":
//                return "yyyy" + split + "MM";
//            case "y_M_d":
//                return "yyyy" + split + "MM" + split + "dd";
//            case "H_m_s":
//                return "%H:%i:%S";
//            case "y_M_d_H_m":
//                return "%Y" + split + "%m" + split + "%d" + " %H:%i";
//            case "y_M_d_H_m_s":
//                return "%Y" + split + "%m" + split + "%d" + " %H:%i:%S";
//            default:
//                return "%Y-%m-%d %H:%i:%S";
//        }
//    }
    //日期格式化
    private String transDateFormat(String dateStyle, String datePattern, String originField) {
        String split = "-";
        if (StringUtils.equalsIgnoreCase(datePattern, "date_sub")) {
            split = "-";
        } else if (StringUtils.equalsIgnoreCase(datePattern, "date_split")) {
            split = "/";
        }

        switch (dateStyle) {
            case "y":
                return "CONVERT(varchar(100), datepart(yy, " + originField + "))";
            case "y_M":
                return "CONVERT(varchar(100), datepart(yy, " + originField + ")) N'" + split + "'CONVERT(varchar(100), datepart(mm, " + originField + "))";
            case "y_M_d":
                if(split.equalsIgnoreCase("-")){
                    return "CONVERT(varchar(100), " + originField + ", 23)";
                }else {
                    return "CONVERT(varchar(100), " + originField + ", 111)";
                }
            case "H_m_s":
                return "CONVERT(varchar(100), " + originField + ", 24)";
            case "y_M_d_H_m":
                if(split.equalsIgnoreCase("-")){
                    return "substring( convert(varchar," + originField + ",120),1,16)";
                }else {
                    return "replace("+ "substring( convert(varchar," + originField + ",120),1,16), '-','/')";
                }
            case "y_M_d_H_m_s":
                if(split.equalsIgnoreCase("-")){
                    return "convert(varchar," + originField + ",120)";
                }else {
                    return "replace("+ "convert(varchar," + originField + ",120), '-','/')";
                }
            default:
                return "convert(varchar," + originField + ",120)";
        }
    }

    private String transStringToDateFormat(String dateStyle, String datePattern, String originField) {
        String split = "-";
        if (StringUtils.equalsIgnoreCase(datePattern, "date_sub")) {
            split = "-";
        } else if (StringUtils.equalsIgnoreCase(datePattern, "date_split")) {
            split = "/";
        }
        switch (dateStyle) {
            case "y":
                return "CONVERT(varchar(100), datepart(yy, " + "SELECT CONVERT(datetime, " +  originField + " ,120)" + "))";
            case "y_M":
                if(split.equalsIgnoreCase("-")){
                    return "substring( convert(varchar," + "SELECT CONVERT(datetime, " +  originField + " ,120)" + ",120),1,7)";
                }else {
                    return "replace("+ "substring( convert(varchar," + "SELECT CONVERT(datetime, " +  originField + " ,120)" + ",120),1,7), '-','/')";
                }
            case "y_M_d":
                if(split.equalsIgnoreCase("-")){
                    return "CONVERT(varchar(100), " + "SELECT CONVERT(datetime, " +  originField + " ,120)" + ", 23)";
                }else {
                    return "CONVERT(varchar(100), " + "SELECT CONVERT(datetime, " +  originField + " ,120)" + ", 111)";
                }
            case "H_m_s":
                return "CONVERT(varchar(100), " + "SELECT CONVERT(datetime, " +  originField + " ,120)" + ", 24)";
            case "y_M_d_H_m":
                if(split.equalsIgnoreCase("-")){
                    return "substring( convert(varchar," + "SELECT CONVERT(datetime, " +  originField + " ,120)" + ",120),1,16)";
                }else {
                    return "replace("+ "substring( convert(varchar," + "SELECT CONVERT(datetime, " +  originField + " ,120)" + ",120),1,16), '-','/')";
                }
            case "y_M_d_H_m_s":
                if(split.equalsIgnoreCase("-")){
                    return "convert(varchar," + "SELECT CONVERT(datetime, " +  originField + " ,120)" + ",120)";
                }else {
                    return "replace("+ "convert(varchar," + "SELECT CONVERT(datetime, " +  originField + " ,120)" + ",120), '-','/')";
                }
            default:
                return "convert(varchar," + "SELECT CONVERT(datetime, " +  originField + " ,120)" + ",120)";
        }
    }

    private SQLObj getXFields(ChartViewFieldDTO x, String originField, String fieldAlias) {
        String fieldName = "";
        if (x.getDeExtractType() == DE_TIME) {
            if (x.getDeType() == DE_INT || x.getDeType() == DE_FLOAT) { //时间转数值
                fieldName = String.format(SqlServerSQLConstants.UNIX_TIMESTAMP, originField);
            } else if (x.getDeType() == DE_TIME) { //时间格式化
                fieldName = transDateFormat(x.getDateStyle(), x.getDatePattern(), originField);
            } else {
                fieldName = originField;
            }
        } else {
            if (x.getDeType() == DE_TIME) {
                if (x.getDeExtractType() == DE_STRING) {// 字符串转时间
                    String cast = String.format(SqlServerSQLConstants.STRING_TO_DATE, originField);
                    fieldName = transDateFormat(x.getDateStyle(), x.getDatePattern(), cast);
                } else {// 数值转时间
                    String cast = String.format(SqlServerSQLConstants.LONG_TO_DATE, originField+ "/1000");
                    fieldName = transDateFormat(x.getDateStyle(), x.getDatePattern(), cast);
                }
            } else {
                fieldName = originField;
            }
        }
        return SQLObj.builder()
                .fieldName(fieldName)
                .fieldAlias(fieldAlias)
                .build();
    }

    private List<SQLObj> getXWheres(ChartViewFieldDTO x, String originField, String fieldAlias) {
        List<SQLObj> list = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(x.getFilter()) && x.getFilter().size() > 0) {
            x.getFilter().forEach(f -> {
                String whereName = "";
                String whereTerm = transMysqlFilterTerm(f.getTerm());
                String whereValue = "";
                if (x.getDeType() == 1 && x.getDeExtractType() != 1) {
                    String cast = String.format(SqlServerSQLConstants.CAST, originField, SqlServerSQLConstants.DEFAULT_INT_FORMAT) + "/1000";
                    whereName = String.format(SqlServerSQLConstants.FROM_UNIXTIME, cast, SqlServerSQLConstants.DEFAULT_DATE_FORMAT);
                } else {
                    whereName = originField;
                }
                if (StringUtils.equalsIgnoreCase(f.getTerm(), "null")) {
                    whereValue = SqlServerSQLConstants.WHERE_VALUE_NULL;
                } else if (StringUtils.equalsIgnoreCase(f.getTerm(), "not_null")) {
                    whereTerm = String.format(whereTerm, originField);
                } else if (StringUtils.containsIgnoreCase(f.getTerm(), "in")) {
                    whereValue = "('" + StringUtils.join(f.getValue(), "','") + "')";
                } else if (StringUtils.containsIgnoreCase(f.getTerm(), "like")) {
                    whereValue = "'%" + f.getValue() + "%'";
                } else {
                    whereValue = String.format(SqlServerSQLConstants.WHERE_VALUE_VALUE, f.getValue());
                }
                list.add(SQLObj.builder()
                        .whereField(whereName)
                        .whereAlias(fieldAlias)
                        .whereTermAndValue(whereTerm + whereValue)
                        .build());
            });
        }
        return list;
    }

    private SQLObj getYFields(ChartViewFieldDTO y, String originField, String fieldAlias) {
        String fieldName = "";
        if (StringUtils.equalsIgnoreCase(y.getOriginName(), "*")) {
            fieldName = SqlServerSQLConstants.AGG_COUNT;
        } else if (SQLConstants.DIMENSION_TYPE.contains(y.getDeType())) {
            fieldName = String.format(SqlServerSQLConstants.AGG_FIELD, y.getSummary(), originField);
        } else {
            if (StringUtils.equalsIgnoreCase(y.getSummary(), "avg") || StringUtils.containsIgnoreCase(y.getSummary(), "pop")) {
                String cast = String.format(SqlServerSQLConstants.CAST, originField, y.getDeType() == 2 ? SqlServerSQLConstants.DEFAULT_INT_FORMAT : SqlServerSQLConstants.DEFAULT_FLOAT_FORMAT);
                String agg = String.format(SqlServerSQLConstants.AGG_FIELD, y.getSummary(), cast);
                fieldName = String.format(SqlServerSQLConstants.CAST, agg, SqlServerSQLConstants.DEFAULT_FLOAT_FORMAT);
            } else {
                String cast = String.format(SqlServerSQLConstants.CAST, originField, y.getDeType() == 2 ? SqlServerSQLConstants.DEFAULT_INT_FORMAT : SqlServerSQLConstants.DEFAULT_FLOAT_FORMAT);
                fieldName = String.format(SqlServerSQLConstants.AGG_FIELD, y.getSummary(), cast);
            }
        }
        return SQLObj.builder()
                .fieldName(fieldName)
                .fieldAlias(fieldAlias)
                .build();
    }

    private List<SQLObj> getYWheres(ChartViewFieldDTO y, String originField, String fieldAlias) {
        List<SQLObj> list = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(y.getFilter()) && y.getFilter().size() > 0) {
            y.getFilter().forEach(f -> {
                String whereTerm = transMysqlFilterTerm(f.getTerm());
                String whereValue = "";
                // 原始类型不是时间，在de中被转成时间的字段做处理
                if (StringUtils.equalsIgnoreCase(f.getTerm(), "null")) {
                    whereValue = SqlServerSQLConstants.WHERE_VALUE_NULL;
                } else if (StringUtils.equalsIgnoreCase(f.getTerm(), "not_null")) {
                    whereTerm = String.format(whereTerm, originField);
                } else if (StringUtils.containsIgnoreCase(f.getTerm(), "in")) {
                    whereValue = "('" + StringUtils.join(f.getValue(), "','") + "')";
                } else if (StringUtils.containsIgnoreCase(f.getTerm(), "like")) {
                    whereValue = "'%" + f.getValue() + "%'";
                } else {
                    whereValue = String.format(SqlServerSQLConstants.WHERE_VALUE_VALUE, f.getValue());
                }
                list.add(SQLObj.builder()
                        .whereField(fieldAlias)
                        .whereAlias(fieldAlias)
                        .whereTermAndValue(whereTerm + whereValue)
                        .build());
            });
        }
        return list;
    }
}
