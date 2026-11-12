
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class CurrencyConverterServer {

    public static void main(String[] args) throws IOException {

        HttpServer server = HttpServer.create(
            new InetSocketAddress(8083), 0
        );

        server.createContext("/convert", exchange -> {
            try {
                String query = exchange.getRequestURI().getQuery();

                String from = getValue(query, "from");
                String to = getValue(query, "to");
                String amountText = getValue(query, "amount");

                if (from == null || to == null ||
                    amountText == null ||
                    !from.matches("[A-Z]{3}") ||
                    !to.matches("[A-Z]{3}")) {
                    throw new IllegalArgumentException("Invalid input");
                }

                double amount = Double.parseDouble(amountText);

                if (!Double.isFinite(amount) || amount < 0) {
                    throw new IllegalArgumentException("Invalid amount");
                }

                String apiUrl =
                    "https://open.er-api.com/v6/latest/" + from;

                HttpClient client = HttpClient.newHttpClient();

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .GET()
                    .build();

                HttpResponse<String> response =
                    client.send(request,
                    HttpResponse.BodyHandlers.ofString());

                String json = response.body();

                if (response.statusCode() != 200 ||
                    !json.contains("\"result\":\"success\"")) {
                    throw new IOException("API request failed");
                }

                String marker = "\"" + to + "\":";
                int index = json.indexOf(marker);

                if (index == -1) {
                    throw new IOException("Currency not supported");
                }

                int start = index + marker.length();

                while (start < json.length() &&
                    Character.isWhitespace(json.charAt(start))) {
                    start++;
                }

                int end = start;

                while (end < json.length() &&
                    ("0123456789.eE+-".indexOf(json.charAt(end)) >= 0)) {
                    end++;
                }

                double rate = Double.parseDouble(
                    json.substring(start, end)
                );

                double converted = amount * rate;

                String result = String.format(
                    java.util.Locale.US,
                    "{\"from\":\"%s\",\"to\":\"%s\",\"amount\":%.2f,\"rate\":%.6f,\"converted\":%.2f}",
                    from, to, amount, rate, converted
                );

                sendResponse(exchange, 200, result);

            } catch (Exception e) {
                sendResponse(exchange, 400,
                    "{\"error\":\"Conversion failed. Check your input or internet connection.\"}");
            }
        });

        server.start();

        System.out.println("Currency Converter Server Started!");
        System.out.println("Open http://localhost:8083");
    }

    private static String getValue(String query, String key) {
        if (query == null) return null;

        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);

            if (pair.length == 2 && pair[0].equals(key)) {
                return pair[1];
            }
        }
        return null;
    }

    private static void sendResponse(
        HttpExchange exchange, int status, String response
    ) throws IOException {

        exchange.getResponseHeaders().set(
            "Content-Type", "application/json; charset=UTF-8"
        );

        exchange.getResponseHeaders().set(
            "Access-Control-Allow-Origin", "*"
        );

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);

        exchange.sendResponseHeaders(status, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
