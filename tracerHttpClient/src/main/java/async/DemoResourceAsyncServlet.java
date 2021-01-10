package async;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.attributes.SemanticAttributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.nio.client.HttpAsyncClient;

import javax.servlet.AsyncContext;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author sumitmaheshwari
 * Created on 04/07/2020
 */
public class DemoResourceAsyncServlet extends HttpServlet {

    private static AtomicInteger pendingRequests = new AtomicInteger();
    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    private static String host = "localhost";
    private final List<String> tenantIds;




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








    public DemoResourceAsyncServlet(){
        tenantIds = new ArrayList<String>();
        tenantIds.add("tenant1");
        tenantIds.add("tenant2");
        tenantIds.add("tenant3");

    }
    
    @Override
    protected void doGet(final HttpServletRequest req, HttpServletResponse resp) {

        String isRunningOnContainer = System.getProperty("iscontainer");
        System.out.println("Is running on container: " + isRunningOnContainer);
        if(isRunningOnContainer != null){
            host = "catalog-service";
        }

        req.setAttribute("cartvalue", getRandomCartValue());
        long replyAfterMillis = replyAfterMillisParam(req);
        final AsyncContext context = req.startAsync();
        context.setTimeout(100000);
        final long before = System.currentTimeMillis();
        List<String> values = new ArrayList<String>();
        values.add("dummy1");
        values.add("dummy2");
        getOktaTenantId(values);

       // resp.setHeader("tenantId", getTenantId());
        executorService.schedule(new AsyncTask(before, context), replyAfterMillis, TimeUnit.MILLISECONDS);
    }

    private Tenant getOktaTenantId(List<String> values){
        System.out.println("Doing some processing");

        try {
            Thread.sleep(2000);
        } catch (InterruptedException ie) {

        }
        System.out.println("Done with processing");
        String id = generateTenantId();

        return new Tenant();
    }

    private long replyAfterMillisParam(HttpServletRequest req) {
        String replyAfterMillisParam = req.getParameter("replyAfterMillis");
        if (replyAfterMillisParam == null)
            replyAfterMillisParam = "1000";
        long replyAfterMillis = Long.parseLong(replyAfterMillisParam);
        if (replyAfterMillis <= 0)
            replyAfterMillis = 1000;
        return replyAfterMillis;
    }

    public static class AsyncTask extends TimerTask {

        private long before;
        public AsyncContext context;

        public AsyncTask(long before, AsyncContext context) {
            this.before = before;
            this.context = context;
        }

        @Override
        public void run() {

            Span rootSpan = tracer.spanBuilder("rootSpan").setSpanKind(Span.Kind.SERVER).startSpan();

            ServletResponse response = context.getResponse();
            byte[] entity = null;
            String header = null;
            try {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {

                }
                System.out.println(pendingRequests.decrementAndGet());

                try {


                    String uri = "http://" + host + ":8090/sample/demo/catalog";


                    //String uri = "http://" + host + ":8081/api";
                    //String uri = "http://google.com";
                    if (isRequestTypeException(uri)) {
                        uri = "http://getexception.com";
                    }
                    makeHttpExitCall(uri, rootSpan);
                    //HttpGet request = new HttpGet(uri);
                   // header = makeSyncHttpExitCall(request);

                } catch (Exception e) {
                    System.out.println("Unable to make exit call, " + e);
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            } finally {
                try {
                    long time = System.currentTimeMillis() - before;
                    response.setContentType("text/plain");
                    response.setCharacterEncoding("UTF-8");

                    entity = ("Successfully processed payment in " + time + " milliseconds.\n")
                            .getBytes(Charset.forName("UTF-8"));
                    response.setContentLength(entity.length);
                    response.getOutputStream().write(entity);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                context.complete();
            }
            rootSpan.end();

        }

        private String makeSyncHttpExitCall(HttpGet request) throws IOException {
            CloseableHttpClient client = HttpClients.createDefault();
            //HttpGet request = new HttpGet(uri);
            CloseableHttpResponse response = client.execute(request);
            String header = null;
            try{
                 header = request.getFirstHeader("inceptionHeader").getValue();
            }catch (Exception e){

            }
            System.out.println(response);
            return header;
        }

        private boolean isRequestTypeException(String uri) {
            String requestType = context.getRequest().getParameter("type");
            if (requestType != null && requestType.equals("exception")) {
                return true;
            }
            return false;
        }

        private void makeHttpExitCall(String uri, Span rootSpan) throws Exception {
            URL url = new URL(uri);
            HttpURLConnection client = (HttpURLConnection) url.openConnection();
            try {
//                client.start();
                HttpGet request = new HttpGet(uri);

                Span childSpan = tracer.spanBuilder("childSpanClientSide").setSpanKind(Span.Kind.CLIENT)
                        .setParent(Context.current().with(rootSpan))
                        .startSpan();


                Scope scope = childSpan.makeCurrent();
                childSpan.setAttribute(SemanticAttributes.HTTP_METHOD, "GET");
                childSpan.setAttribute("component", "http");
                childSpan.setAttribute(SemanticAttributes.HTTP_URL, uri.toString());
                openTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), client, setter);


                int responseCode = client.getResponseCode();

//                HttpResponse response = future.get();
                if(isRequestTypeException(uri)){
                    throw new ConnectionClosedException("Connection is closed already");
                }
//                System.out.println(response);
                childSpan.end();
            } finally {
                client.disconnect();
            }
        }
    }

    private int getRandomCartValue(){
        Random r = new Random();
        int low = 2000;
        int high = 10000;
        int result = r.nextInt(high-low) + low;
        return result;
    }
    private String generateTenantId() {
        Random r = new Random();
        int low = 0;
        int high = 3;
        int result = r.nextInt(high-low) + low;
        return tenantIds.get(result);
    }

    private static class Tenant {

        private String id;

        public Tenant(){

        }

        public Tenant(String id){
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

}
