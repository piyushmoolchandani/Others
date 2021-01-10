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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author sumitmaheshwari
 * Created on 30/06/2020
 */
public class ContinuingServlet extends HttpServlet {


//    private static final OpenTelemetry openTelemetry = initOpenTelemetry();
//    private static final Tracer tracer =
//            openTelemetry.getTracer("our-own-example-server","1.0.0");
//
//    private static final TextMapPropagator.Getter<HttpExchange> getter =
//            new TextMapPropagator.Getter<>() {
//                @Override
//                public Iterable<String> keys(HttpExchange carrier) {
//                    return carrier.getRequestHeaders().keySet();
//                }
//
//                @Override
//                public String get(HttpExchange carrier, String key) {
//                    if (carrier.getRequestHeaders().containsKey(key)) {
//                        return carrier.getRequestHeaders().get(key).get(0);
//                    }
//                    return "";
//                }
//            };
//
//    private static OpenTelemetry initOpenTelemetry() {
//        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder().build();
//
//        ManagedChannel jaegerChannel =
//                ManagedChannelBuilder.forAddress("localhost", 14250).usePlaintext().build();
//
//        JaegerGrpcSpanExporter jaegerExporter =
//                JaegerGrpcSpanExporter.builder()
//                        .setServiceName("otel-jaeger-example")
//                        .setChannel(jaegerChannel)
//                        .setDeadlineMs(30000)
//                        .build();
//
//        sdkTracerProvider.addSpanProcessor(SimpleSpanProcessor.builder(jaegerExporter).build());
//
//        return OpenTelemetrySdk.builder()
//                .setTracerProvider(sdkTracerProvider)
//                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
//                .build();
//    }

    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

//        Context context =
//                openTelemetry
//                        .getPropagators()
//                        .getTextMapPropagator()
//                        .extract(Context.current(), request, getter);
//
//        Span span =
//                tracer.spanBuilder("GET /").setParent(context).setSpanKind(Span.Kind.SERVER).startSpan();
//
//        try (Scope scope = span.makeCurrent()) {
//            span.setAttribute("component", "http");
//            span.setAttribute("http.method", "GET");
//            span.setAttribute("http.scheme", "http");
//            span.setAttribute("http.host", "localhost:" + HttpServer.port);
//            span.setAttribute("http.target", "/");
//            answer(exchange, span);
//        } finally {
//            span.end();
//        }

        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().println("{ \"status\": \"ok\"}");
        System.out.println("Received header:: " + request.getHeader("inceptionheader"));
        System.out.println("Received a call....");

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}
