import static io.opentelemetry.api.common.AttributeKey.stringKey;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;

public class HttpServer {
    private static final OpenTelemetry openTelemetry = initOpenTelemetry();
    private static final Tracer tracer =
            openTelemetry.getTracer("our-own-example-server","1.0.0");

    private static final int port = 8080;
    private final com.sun.net.httpserver.HttpServer server;

    private static final TextMapPropagator.Getter<HttpExchange> getter =
            new TextMapPropagator.Getter<>() {
                @Override
                public Iterable<String> keys(HttpExchange carrier) {
                    return carrier.getRequestHeaders().keySet();
                }

                @Override
                public String get(HttpExchange carrier, String key) {
                    if (carrier.getRequestHeaders().containsKey(key)) {
                        return carrier.getRequestHeaders().get(key).get(0);
                    }
                    return "";
                }
            };

    private HttpServer() throws IOException {
        this(port);
    }

    private HttpServer(int port) throws IOException {
        server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new HelloHandler());
        server.start();
        System.out.println("Server ready on http://127.0.0.1:" + port);
    }

    private static OpenTelemetry initOpenTelemetry() {
        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder().build();

        ManagedChannel jaegerChannel =
                ManagedChannelBuilder.forAddress("localhost", 14250).usePlaintext().build();

        JaegerGrpcSpanExporter jaegerExporter =
                JaegerGrpcSpanExporter.builder()
                        .setServiceName("otel-jaeger-example")
                        .setChannel(jaegerChannel)
                        .setDeadlineMs(30000)
                        .build();

        sdkTracerProvider.addSpanProcessor(SimpleSpanProcessor.builder(jaegerExporter).build());

        return OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
    }

    private static class HelloHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Context context =
                    openTelemetry
                            .getPropagators()
                            .getTextMapPropagator()
                            .extract(Context.current(), exchange, getter);

            Span span =
                    tracer.spanBuilder("GET /").setParent(context).setSpanKind(Span.Kind.SERVER).startSpan();

            try (Scope scope = span.makeCurrent()) {
                span.setAttribute("component", "http");
                span.setAttribute("http.method", "GET");
                span.setAttribute("http.scheme", "http");
                span.setAttribute("http.host", "localhost:" + HttpServer.port);
                span.setAttribute("http.target", "/");
                answer(exchange, span);
            } finally {
                span.end();
            }
        }

        private void answer(HttpExchange he, Span span) throws IOException {
            span.addEvent("Start Processing");
            String response = "Hello World!";
            he.sendResponseHeaders(200, response.length());
            OutputStream os = he.getResponseBody();
            os.write(response.getBytes(Charset.defaultCharset()));
            os.close();
            System.out.println("Served Client: " + he.getRemoteAddress());
            Attributes eventAttributes = Attributes.of(stringKey("answer"), response);
            span.addEvent("Finish Processing", eventAttributes);
        }
    }

    private void stop() {
        server.stop(0);
    }

    public static void main(String[] args) throws Exception {
        final HttpServer s = new HttpServer();
        Runtime.getRuntime().addShutdownHook(new Thread(s::stop));
    }
}