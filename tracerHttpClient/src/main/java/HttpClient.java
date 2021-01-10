
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.attributes.SemanticAttributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;

public class HttpClient {

    private static final TextMapPropagator.Setter<HttpURLConnection> setter = URLConnection::setRequestProperty;

    private void makeRequest(OpenTelemetry openTelemetry, Tracer tracer, Span rootSpan) throws IOException {
        int port = 8080;
        URL url = new URL("http://127.0.0.1:" + port);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        int status = 0;
        StringBuilder content = new StringBuilder();
        Span span = tracer.spanBuilder("childSpanClientSide").setParent(Context.current().with(rootSpan)).setSpanKind(Span.Kind.CLIENT).startSpan();
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute(SemanticAttributes.HTTP_METHOD, "GET");
            span.setAttribute("component", "http");
            span.setAttribute(SemanticAttributes.HTTP_URL, url.toString());
            openTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), con, setter);
            try {
                con.setRequestMethod("GET");
                status = con.getResponseCode();
                BufferedReader in =
                        new BufferedReader(
                                new InputStreamReader(con.getInputStream(), Charset.defaultCharset()));
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                in.close();
            } catch (Exception e) {
                span.setStatus(StatusCode.ERROR, "HTTP Code: " + status);
            }
        } finally {
            span.end();
        }
        System.out.println("Response Code: " + status);
        System.out.println("Response Msg: " + content);
    }

    public static void runClient(OpenTelemetry openTelemetry, Tracer tracer, Span rootSpan) {
        HttpClient httpClient = new HttpClient();
        try {
            httpClient.makeRequest(openTelemetry, tracer, rootSpan);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}

