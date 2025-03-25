package tech.buildrun.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class Handler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final String DB_URL = System.getenv("DB_URL");
    private static final String DB_USER = System.getenv("DB_USER");
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");
    private static final String API_URL = "https://w8eknaea3f.execute-api.us-east-1.amazonaws.com/tech-challenge/customers/identifyOrCreate";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        var logger = context.getLogger();
        logger.log("Request received - " + request.getBody());

        try {
            // Parse do JSON da request
            JsonNode requestBody = objectMapper.readTree(request.getBody());
            String cpf = requestBody.has("cpf") ? requestBody.get("cpf").asText() : null;

            if (cpf == null || cpf.isBlank()) {
                return createResponse(400, "{\"error\": \"CPF não fornecido\"}");
            }

            String customerData = findCustomerByCpf(cpf);
            if (customerData != null) {
                return createResponse(200, customerData);
            }

            String apiResponse = callIdentifyOrCreateEndpointApi(request.getBody());
            return createResponse(200, apiResponse);

        } catch (Exception e) {
            logger.log("Erro: " + e.getMessage());
            return createResponse(500, "{\"error\": \"Erro interno\"}");
        }
    }

    private String findCustomerByCpf(String cpf) {
        String sql = "SELECT id, name, cpf, email FROM customers WHERE cpf = ?";
        try {
            Class.forName("org.postgresql.Driver");

            try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, cpf);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return String.format("{\"id\": \"%s\", \"name\": \"%s\", \"cpf\": \"%s\", \"email\": \"%s\"}",
                                resultSet.getString("id"),
                                resultSet.getString("name"),
                                resultSet.getString("cpf"),
                                resultSet.getString("email"));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private String callIdentifyOrCreateEndpointApi(String requestBody) {
        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes());
                os.flush();
            }

            return new String(conn.getInputStream().readAllBytes());
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"error\": \"Falha ao chamar a API externa\"}";
        }
    }

    private APIGatewayProxyResponseEvent createResponse(int statusCode, String body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withBody(body)
                .withIsBase64Encoded(false);
    }
}
