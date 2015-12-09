package org.sonar.server.computation.ws;


import com.google.gson.Gson;
import org.sonar.api.Properties;
import org.sonar.api.Property;
import org.sonar.api.config.Settings;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.ce.CeQueueDto;
import org.sonar.db.measure.MeasureDto;
import org.sonar.server.exceptions.ServerException;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Properties(
        @Property(key="sonarstatus.maxconnections", name="Max number of parallel Requests to the Sonar Status API", defaultValue="100")
)
public class StatusWsAction implements CeWsAction {
    public static final String ACTION = "status";
    public static final String PARAM_KEY = "key";

    private static final AtomicInteger CHECK_COUNT = new AtomicInteger(0);

    private final DbClient dbClient;
    private final TaskFormatter wsTaskFormatter;
    private final Gson gson;
    private final int maxConnections;

    public StatusWsAction(DbClient dbClient, TaskFormatter wsTaskFormatter, final Settings settings) {
        this.dbClient = dbClient;
        this.wsTaskFormatter = wsTaskFormatter;
        this.gson = new Gson();
        this.maxConnections = settings.getInt("sonarstatus.maxconnections");
    }

    public class Condition {
        public String actual;
        public int period;
        public String error;
        public String level;
        public String metric;
        public String op;
    }

    public class QualityGateStatus {
        public String level;
        public List<Condition> conditions;
    }

    @Override
    public void define(WebService.NewController controller) {
        WebService.NewAction action = controller.createAction(ACTION)
                .setDescription("Task status")
                .setInternal(false)
                .setResponseExample(getClass().getResource("task-example.json"))
                .setSince("5.2")
                .setHandler(this);

        action
                .createParam(PARAM_KEY)
                .setRequired(true)
                .setDescription("Project key")
                .setExampleValue("org.apache:wicket");
    }

    private boolean isAnalysisRunning(final String componentKey) {
        final DbSession dbSession = dbClient.openSession(false);
        try {
            for (final CeQueueDto task : dbClient.ceQueueDao().selectAllInAscOrder(dbSession)) {
                if(componentKey.equals(wsTaskFormatter.formatQueue(dbSession, task).getComponentKey())) {
                    return true;
                }
            }
            return false;
        } finally {
            dbClient.closeSession(dbSession);
        }
    }

    private QualityGateStatus getQualityGateStatus(final String componentKey) {
        final DbSession dbSession = dbClient.openSession(false);
        try {
            final MeasureDto measure = dbClient.measureDao().selectByComponentKeyAndMetricKey(dbSession, componentKey, "quality_gate_details");
            return this.gson.fromJson(measure.getData(), QualityGateStatus.class);
        } finally {
            dbClient.closeSession(dbSession);
        }
    }

    @Override
    public void handle(Request wsRequest, Response wsResponse) throws Exception {
        try {
            final int currentRequests = CHECK_COUNT.incrementAndGet();

            if(currentRequests > maxConnections) {
                throw new ServerException(HttpURLConnection.HTTP_UNAVAILABLE, "Too many requests for the status API!");
            }

            final String componentKey = wsRequest.mandatoryParam(PARAM_KEY);
            while (isAnalysisRunning(componentKey)) {
                Thread.sleep(1000L);
            }

            final QualityGateStatus status = getQualityGateStatus(componentKey);
            if(status.level.equals("OK")) {
                wsResponse.newJsonWriter()
                        .beginObject()
                        .prop("key", wsRequest.mandatoryParam(PARAM_KEY))
                        .prop("status", getQualityGateStatus(componentKey).level)
                        .endObject()
                        .close();
            } else {
                throw new ServerException(HttpURLConnection.HTTP_NOT_ACCEPTABLE, "Quality Gate Failed!");
            }
        } catch (InterruptedException e) {
            throw new ServerException(HttpURLConnection.HTTP_UNAVAILABLE, "Interrupted while waiting for Task to complete!");
        } finally {
            CHECK_COUNT.decrementAndGet();
        }
    }
}
