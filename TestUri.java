import org.springframework.web.util.UriComponentsBuilder;

public class TestUri {
    public static void main(String[] args) {
        String query = "{namespace=\"default\", app=\"payments-api\"}";
        try {
            var uri = UriComponentsBuilder.fromHttpUrl("http://localhost:3100")
                .path("/loki/api/v1/query_range")
                .queryParam("query", query)
                .build()
                .toUri();
            System.out.println("Success! " + uri.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
