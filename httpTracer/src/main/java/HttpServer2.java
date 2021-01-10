import com.sun.net.httpserver.HttpExchange;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.api.OpenTelemetry;
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
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;

import javax.servlet.http.HttpServletResponse;

public class HttpServer2 {

    private static final OpenTelemetry openTelemetry = initOpenTelemetry();
    private static final Tracer tracer =
            openTelemetry.getTracer("our-own-example-server","1.0.0");

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

    public static void main(String[] args) throws Exception {
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(8090);
        server.setConnectors(new Connector[]{connector});

        ServletContextHandler exchange = new ServletContextHandler(ServletContextHandler.SESSIONS);
        exchange.setContextPath("/");

//        Context context =
//                openTelemetry
//                        .getPropagators()
//                        .getTextMapPropagator()
//                        .extract(Context.current(), , getter);
//
//        Span span =
//                tracer.spanBuilder("GET /").setParent(context).setSpanKind(Span.Kind.SERVER).startSpan();
//
//        try (Scope scope = span.makeCurrent()) {
//            span.setAttribute("component", "http");
//            span.setAttribute("http.method", "GET");
//            span.setAttribute("http.scheme", "http");
//            span.setAttribute("http.host", "localhost:" + 8090);
//            span.setAttribute("http.target", "/");
//        } finally {
//            span.end();
//        }

        exchange.addServlet(ContinuingServlet.class, "/sample/demo/catalog");
        server.setHandler(exchange);
        server.start();
        System.out.println("Server running at http://localhost:8090/sample/demo/catalog");
        server.join();
    }
}
