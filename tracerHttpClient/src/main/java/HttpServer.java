
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.io.IOException;
import java.net.InetSocketAddress;

public class HttpServer {
    static OpenTelemetry openTelemetry = initOpenTelemetry();
    static Tracer tracer =
            openTelemetry.getTracer("example-client","1.0.0");

    private static final int port = 3333;
    private final com.sun.net.httpserver.HttpServer server;

    HttpServer() throws IOException {
        this(port);
    }

    HttpServer(int port) throws IOException {
        server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/initiate", new HelloHandler());
        server.start();
        System.out.println("Server ready on http://127.0.0.1:" + port);
    }

    static OpenTelemetry initOpenTelemetry() {
        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder().build();

        ManagedChannel jaegerChannel = ManagedChannelBuilder.forAddress("localhost", 14250).usePlaintext().build();

        JaegerGrpcSpanExporter jaegerExporter = JaegerGrpcSpanExporter.builder().setServiceName("ServerClientServerExample")
                        .setChannel(jaegerChannel)
                        .setDeadlineMs(3000)
                        .build();

        sdkTracerProvider.addSpanProcessor(SimpleSpanProcessor.builder(jaegerExporter).build());

        return OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
    }

    static class HelloHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            Span span =
                    tracer.spanBuilder("rootSpan").setSpanKind(Span.Kind.SERVER).startSpan();

            try (Scope scope = span.makeCurrent()) {
                span.setAttribute("component", "http");
                span.setAttribute("http.method", "GET");
                span.setAttribute("http.scheme", "http");
                span.setAttribute("http.host", "localhost:" + HttpServer.port);
                HttpClient client = new HttpClient();
                client.runClient(openTelemetry, tracer, span);
            } finally {
                span.end();
            }
        }
    }

    void stop() {
        server.stop(0);
    }

    public static void main(String[] args) throws Exception {
        final HttpServer s = new HttpServer();
        Runtime.getRuntime().addShutdownHook(new Thread(s::stop));
    }
}