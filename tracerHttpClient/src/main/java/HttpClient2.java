
import async.DemoResourceAsyncServlet;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
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
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.net.HttpURLConnection;
import java.net.URLConnection;

/**
 * @author sumitmaheshwari
 * Created on 04/07/2020
 */
public class HttpClient2 {

    private static final OpenTelemetry openTelemetry = initOpenTelemetry();
    private static final Tracer tracer = openTelemetry.getTracer("tracerUpstreamDownstream", "1.0.0");
    private static final TextMapPropagator.Setter<HttpURLConnection> setter = URLConnection::setRequestProperty;

    private static OpenTelemetry initOpenTelemetry() {

        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder().build();

        ManagedChannel jaegerChannel = ManagedChannelBuilder.forAddress("localhost", 14250).usePlaintext().build();

        JaegerGrpcSpanExporter jaegerExporter = JaegerGrpcSpanExporter.builder().setServiceName("upstream vaala")
                .setChannel(jaegerChannel).setDeadlineMs(30000).build();

        sdkTracerProvider.addSpanProcessor(SimpleSpanProcessor.builder(jaegerExporter).build());

        return OpenTelemetrySdk.builder().setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance())).build();
    }

    public static void main(String[] args) throws Exception {
        Server s = new Server(new QueuedThreadPool(3, 1));

        s.setConnectors(new Connector[] { createConnector(s) });

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        context.addServlet(DemoResourceAsyncServlet.class, "/sample/demo/payment");

        s.setHandler(context);
        s.start();

        System.out.println("Server started at http://localhost:7080");
    }

    private static ServerConnector createConnector(Server s) {
        ServerConnector connector = new ServerConnector(s, 0, 1);
        //connector.setHost("localhost");
        connector.setPort(7080);
        return connector;
    }
}
