package tech.buildrun.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.sql.Connection;
import java.sql.DriverManager;

public class Handler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final String DB_URL = System.getenv("DB_URL") ;
    private static final String DB_USER = System.getenv("DB_USER");
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request,
                                                      Context context) {
        var logger = context.getLogger();

        logger.log("Request received - " + request.getBody());

        // Teste de conexão
        String errorMessage = testDatabaseConnection();

        if (errorMessage != null) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody("""
                            {
                                "error": "Falha na conexão com o banco",
                                "message": "%s"
                            }
                            """.formatted(errorMessage))
                    .withIsBase64Encoded(false);
        } else {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody("""
                            {
                                "sucesso": "Sucesso ao conectar com o banco"
                            }
                            """)
                    .withIsBase64Encoded(false);
        }
    }

    private String testDatabaseConnection() {
        try {
            // Registra o driver
            Class.forName("org.postgresql.Driver");

            // Agora você pode tentar se conectar ao banco
            try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                if (connection.isValid(2)) {
                    return null; // Conexão bem-sucedida
                } else {
                    return "Conexão não válida";
                }
            } catch (Exception e) {
                e.printStackTrace();
                return "Erro ao conectar: " + e.getMessage();
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return "Erro: Driver PostgreSQL não encontrado";
        }
    }
}