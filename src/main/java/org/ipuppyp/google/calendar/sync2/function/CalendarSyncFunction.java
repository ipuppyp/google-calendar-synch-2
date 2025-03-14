package org.ipuppyp.google.calendar.sync2.function;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.ipuppyp.google.calendar.sync2.model.SyncRequest;
import org.ipuppyp.google.calendar.sync2.service.CalendarSyncFunctionService;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Optional;

import static java.lang.Boolean.parseBoolean;


@Slf4j
public class CalendarSyncFunction implements HttpFunction {

    private final CalendarSyncFunctionService service;

    public CalendarSyncFunction() {
         service = new CalendarSyncFunctionService();
    }

    @Override
    public void service(HttpRequest request, HttpResponse response) throws IOException {
        BufferedWriter writer = response.getWriter();

        log.info("*********************************");
        log.info("* STARTING SYNCHRONIZATION...   *");
        log.info("*********************************");

        try {
            String sourceCalendar = getRequestParam(request, "sourceCalendar");
            String targetCalendar = getRequestParam(request, "targetCalendar");
            String eventPrefix = getRequestParam(request, "eventPrefix");
            String eventFilter = getRequestParam(request, "eventFilter");
            boolean publicOnly = parseBoolean(getRequestParam(request, "publicOnly"));

            SyncRequest syncRequest = SyncRequest.builder()
                    .sourceCalendar(sourceCalendar)
                    .targetCalendar(targetCalendar)
                    .eventPrefix(eventPrefix)
                    .eventFilter(eventFilter)
                    .publicOnly(publicOnly)
                    .build();

            service.syncWithRetry(syncRequest);
            writer.write("DONE");
            log.info("******************************************");
            log.info("* SYNCHRONIZATION FINISHED SUCCESSFULLY. *");
            log.info("******************************************");

        }
        catch (IllegalArgumentException ex) {
            writer.write("BAD REQUEST\\n");
            writer.write(ex.getMessage());
            response.setStatusCode(400);
            log.error("Exception during synchronization", ex);
            log.error("**************************");
            log.error("* SYNCHRONIZATION FAILED *");
            log.error("**************************");
        }
        catch (Throwable ex) {
            writer.write("INTERNAL SERVER ERROR");
            response.setStatusCode(500);
            log.error("Exception during synchronization", ex);
            log.error("**************************");
            log.error("* SYNCHRONIZATION FAILED *");
            log.error("**************************");
        }
    }

    private String getRequestParam(HttpRequest request, String name) {
        Optional<String> property = request.getFirstQueryParameter(name);
        if (property.isEmpty()) {
            log.error("Missing configuration: \"{}\", please set it with a request parameter.\n", name);
            throw new IllegalArgumentException("Missing configuration: " + name);
        }
        return property.get();

    }

}