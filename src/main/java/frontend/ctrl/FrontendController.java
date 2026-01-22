package frontend.ctrl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import frontend.data.Sms;
import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping(path = "/sms")
public class FrontendController {

    private AtomicInteger totalRequestsSpam = new AtomicInteger(0);
    private AtomicInteger totalRequestsHam = new AtomicInteger(0);
    private AtomicInteger totalRequestsFailed = new AtomicInteger(0);

    private AtomicInteger activeRequests = new AtomicInteger(0);

    private AtomicLong requestTimeSum = new AtomicLong(0);
    private AtomicLong requestTimeCount = new AtomicLong(0);
    private Map<Double, AtomicInteger> requestTimeBuckets = new TreeMap<>();

    // Button clicked metric
    private AtomicInteger buttonClicked = new AtomicInteger(0);

    private String modelHost;

    private RestTemplateBuilder rest;

    public FrontendController(RestTemplateBuilder rest, Environment env) {
        this.rest = rest;
        this.modelHost = env.getProperty("MODEL_HOST");
        assertModelHost();

        for (double i = 100; i < 1000; i = i + 100) {
            requestTimeBuckets.put(i, new AtomicInteger(0));
        }

        requestTimeBuckets.put(Double.POSITIVE_INFINITY, new AtomicInteger(0));
    }

    private void assertModelHost() {
        if (modelHost == null || modelHost.strip().isEmpty()) {
            System.err.println("ERROR: ENV variable MODEL_HOST is null or empty");
            System.exit(1);
        }
        modelHost = modelHost.strip();
        if (modelHost.indexOf("://") == -1) {
            var m = "ERROR: ENV variable MODEL_HOST is missing protocol, like \"http://...\" (was: \"%s\")\n";
            System.err.printf(m, modelHost);
            System.exit(1);
        } else {
            System.out.printf("Working with MODEL_HOST=\"%s\"\n", modelHost);
        }
    }

    @GetMapping("")
    public String redirectToSlash(HttpServletRequest request) {
        // relative REST requests in JS will end up on / and not on /sms
        return "redirect:" + request.getRequestURI() + "/";
    }

    @GetMapping("/")
    public String index(Model m) {
        m.addAttribute("hostname", modelHost);
        return "sms/index";
    }

    @PostMapping({ "", "/" })
    @ResponseBody
    public Sms predict(@RequestBody Sms sms) {
        // Increment button clicked metric
        buttonClicked.incrementAndGet();

        System.out.printf("Requesting prediction for \"%s\" ...\n", sms.sms);
        activeRequests.incrementAndGet();

        long startTime = System.currentTimeMillis();
        sms.result = getPrediction(sms);
        System.out.printf("Prediction: %s\n", sms.result);

        long timeTaken = System.currentTimeMillis() - startTime;
        requestTimeSum.addAndGet(timeTaken);
        requestTimeCount.incrementAndGet();

        for (Map.Entry<Double, AtomicInteger> bucket : requestTimeBuckets.entrySet()) {
            if (timeTaken <= bucket.getKey()) {
                bucket.getValue().incrementAndGet();
                break;
            }
        }

        if (sms.result.equals("spam")) {
            totalRequestsSpam.incrementAndGet();
        }
        else if (sms.result.equals("ham")) {
            totalRequestsHam.incrementAndGet();
        }

        return sms;
    }

    private String getPrediction(Sms sms) {
        try {
            var url = new URI(modelHost + "/predict");
            var c = rest.build().postForEntity(url, sms, Sms.class);
            return c.getBody().result.trim();
        } catch (URISyntaxException e) {
            totalRequestsFailed.incrementAndGet();
            activeRequests.decrementAndGet();
            throw new RuntimeException(e);
        }
    }

    @GetMapping(value = "/metrics", produces = "text/plain; charset=UTF-8")
    @ResponseBody
    private String metrics() {
        StringBuilder response = new StringBuilder();

        response.append("# TYPE button_clicked counter\n");
        response.append("button_clicked ").append(buttonClicked.get()).append("\n\n");

        response.append("# TYPE total_requests counter\n");
        response.append("total_requests{result=\"ham\"} ").append(totalRequestsHam.get()).append("\n");
        response.append("total_requests{result=\"failed\"} ").append(totalRequestsFailed.get()).append("\n");
        response.append("total_requests{result=\"spam\"} ").append(totalRequestsSpam.get()).append("\n\n");

        response.append("# TYPE active_requests gauge\n");
        response.append("active_requests ").append(activeRequests.get()).append("\n\n");

        // Histogram
        response.append("# TYPE request_duration_ms histogram\n");
        for (Map.Entry<Double, AtomicInteger> bucket : requestTimeBuckets.entrySet()) {
            response.append("request_duration_ms_bucket{le=\"")
                .append(bucket.getKey().isInfinite() ? "+Inf" : bucket.getKey())
                .append("\"} ")
                .append(bucket.getValue().get())
                .append("\n");
        }

        response.append("request_duration_sum ").append(requestTimeSum.get()).append("\n");
        response.append("request_duration_count ").append(requestTimeCount.get()).append("\n");

        return response.toString();
    }
}