package io.irontest.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.irontest.core.runner.HTTPAPIResponse;
import io.irontest.core.runner.SQLStatementType;
import io.irontest.models.DataTable;
import io.irontest.models.DataTableColumn;
import io.irontest.models.HTTPMethod;
import io.irontest.models.UserDefinedProperty;
import io.irontest.models.teststep.HTTPHeader;
import org.antlr.runtime.ANTLRStringStream;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.*;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.jdbi.v3.core.internal.SqlScriptParser;

import javax.net.ssl.SSLContext;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;

public final class IronTestUtils {
    /**
     * @param rs
     * @return a list of lower case column names present in the result set.
     * @throws SQLException
     */
    public static List<String> getFieldsPresentInResultSet(ResultSet rs) throws SQLException {
        List<String> fieldsPresentInResultSet = new ArrayList<String>();
        ResultSetMetaData metaData = rs.getMetaData();
        for(int index =1; index <= metaData.getColumnCount(); index++) {
            fieldsPresentInResultSet.add(metaData.getColumnLabel(index).toLowerCase());
        }
        return fieldsPresentInResultSet;
    }

    public static boolean isSQLRequestSingleSelectStatement(String sqlRequest) {
        List<String> statements = getStatements(sqlRequest);
        return statements.size() == 1 && SQLStatementType.isSelectStatement(statements.get(0));
    }

    /**
     * Parse the sqlRequest to get SQL statements, trimmed and without comments.
     * @param sqlRequest
     * @return
     */
    public static List<String> getStatements(String sqlRequest) {
        final List<String> statements = new ArrayList<>();
        String lastStatement = new SqlScriptParser((t, sb) -> {
            statements.add(sb.toString().trim());
            sb.setLength(0);
        }).parse(new ANTLRStringStream(sqlRequest));
        statements.add(lastStatement.trim());
        statements.removeAll(Collections.singleton(""));   //  remove all empty statements

        return statements;
    }

    public static Map<String, String> udpListToMap(List<UserDefinedProperty> testcaseUDPs) {
        Map<String, String> result = new HashMap<>();
        for (UserDefinedProperty udp: testcaseUDPs) {
            result.put(udp.getName(), udp.getValue());
        }
        return result;
    }

    public static void checkDuplicatePropertyNameBetweenDataTableAndUPDs(Set<String> udpNames, DataTable dataTable) {
        Set<String> set = new HashSet<>();
        set.addAll(udpNames);
        for (DataTableColumn dataTableColumn : dataTable.getColumns()) {
            if (!set.add(dataTableColumn.getName())) {
                throw new RuntimeException("Duplicate property name between data table and UDPs: " + dataTableColumn.getName());
            }
        }
    }

    public static HTTPAPIResponse invokeHTTPAPI(String url, String username, String password, HTTPMethod httpMethod,
                                                List<HTTPHeader> httpHeaders, String httpBody) throws Exception {
        //  create HTTP request object and set body if applicable
        HttpUriRequest httpRequest;
        switch (httpMethod) {
            case GET:
                httpRequest = new HttpGet(url);
                break;
            case POST:
                HttpPost httpPost = new HttpPost(url);
                httpPost.setEntity(new StringEntity(httpBody, "UTF-8"));
                httpRequest = httpPost;
                break;
            case PUT:
                HttpPut httpPut = new HttpPut(url);
                httpPut.setEntity(new StringEntity(httpBody, "UTF-8"));
                httpRequest = httpPut;
                break;
            case DELETE:
                httpRequest = new HttpDelete(url);
                break;
            default:
                throw new IllegalArgumentException("Unrecognized HTTP method " + httpMethod);
        }

        //  set request HTTP headers
        for (HTTPHeader httpHeader : httpHeaders) {
            httpRequest.setHeader(httpHeader.getName(), httpHeader.getValue());
        }
        //  set HTTP basic auth
        if (!"".equals(StringUtils.trimToEmpty(username))) {
            String auth = username + ":" + password;
            String encodedAuth = Base64.encodeBase64String(auth.getBytes());
            String authHeader = "Basic " + encodedAuth;
            httpRequest.setHeader(HttpHeaders.AUTHORIZATION, authHeader);
        }

        final HTTPAPIResponse apiResponse = new HTTPAPIResponse();
        ResponseHandler<Void> responseHandler = httpResponse -> {
            apiResponse.getHttpHeaders().add(
                    new HTTPHeader("*Status-Line*", httpResponse.getStatusLine().toString()));
            Header[] headers = httpResponse.getAllHeaders();
            for (Header header: headers) {
                apiResponse.getHttpHeaders().add(new HTTPHeader(header.getName(), header.getValue()));
            }
            HttpEntity entity = httpResponse.getEntity();
            apiResponse.setHttpBody(entity != null ? EntityUtils.toString(entity) : null);
            return null;
        };

        //  build HTTP Client instance, trusting all SSL certificates
        SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial((TrustStrategy) (chain, authType) -> true).build();
        HttpClient httpClient = HttpClients.custom().setSSLContext(sslContext).build();

        //  invoke the API
        httpClient.execute(httpRequest, responseHandler);

        return apiResponse;
    }

    /**
     * Check whether the input string is potentially json or xml, and return pretty printed string accordingly.
     * If the input string is not a well formed json or xml, return it as is.
     * If the input is null, return null.
     * @param input
     * @return
     * @throws TransformerException
     */
    public static String prettyPrintJSONOrXML(String input) throws TransformerException, IOException {
        if (input == null) {
            return null;
        } else if (input.trim().startsWith("<")) {     //  potentially xml (impossible to be json)
            return XMLUtils.prettyPrintXML(input);
        } else {                     //  potentially json (impossible to be xml)
            ObjectMapper objectMapper = new ObjectMapper();
            Object jsonObject;
            try {
                jsonObject = objectMapper.readValue(input, Object.class);
            } catch (Exception e) {
                //  the input string is not well formed JSON
                return input;
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
        }
    }
}
