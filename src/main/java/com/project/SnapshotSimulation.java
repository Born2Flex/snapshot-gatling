package com.project;
// required for Gatling core structure DSL

import io.gatling.javaapi.core.*;

import static io.gatling.javaapi.core.CoreDsl.*;

// required for Gatling HTTP DSL
import io.gatling.javaapi.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gatling.javaapi.http.HttpDsl.*;

public class SnapshotSimulation extends Simulation {
    private static final Logger LOGGER = LoggerFactory.getLogger(SnapshotSimulation.class);
    private static final int USER_COUNT = Integer.parseInt(System.getProperty("USERS", "1"));
    private static final int RAMP_DURATION = Integer.parseInt(System.getProperty("RAMP_DURATION", "1"));

    private HttpProtocolBuilder httpProtocol =
            http.baseUrl("http://snapshot-env.eba-tkt77dky.eu-central-1.elasticbeanstalk.com"/*"http://localhost:8080"*/)
                    .acceptHeader("application/json")
                    .contentTypeHeader("application/json");

    private ChainBuilder authorisation =
            exec(http("Authentication")
                    .post("/rest/auth/authenticate")
                    .body(ElFileBody("userAuth.json"))
                    .check(jmesPath("access_token").saveAs("jwtToken")));

    private ChainBuilder userInfo =
            exec(http("Get user info")
                    .get("/rest/users/me")
                    .header("Authorization", "Bearer #{jwtToken}")
                    .check(jmesPath("id").saveAs("id")));

    private ChainBuilder profilePage =
            exec(http("Profile")
                    .get("/profile/#{id}"));

    private ChainBuilder settingsPage =
            exec(http("Settings")
                    .get("/profile/#{id}/settings"));

    private ChainBuilder interviewJournal =
            exec(http("Interview Journal")
                    .get("/profile/#{id}/interview-journal"));

    private ChainBuilder getInterview =
            exec(http("Get existing interview")
                    .get("/interview/1"));

    private ChainBuilder createInterview =
            exec(http("Create interview")
                    .post("/rest/interviews")
                    .header("Authorization", "Bearer #{jwtToken}")
                    .body(StringBody(ElFileBody("interview.json")))
                    .check(jmesPath("id").saveAs("interviewId")));

    private ChainBuilder getCreatedInterview =
            exec(http("Interview")
                    .get("/interview/#{interviewId}"));

    private ChainBuilder startInterview =
            exec(http("Interview status change")
                    .patch("/rest/interviews/status/#{interviewId}?status=ACTIVE")
                    .header("Authorization", "Bearer #{jwtToken}"));
    private ChainBuilder getQuestionsForSkill =
            exec(http("Skill questions")
                    .post("/rest/interviews/question")
                    .header("Authorization", "Bearer #{jwtToken}")
                    .body(ElFileBody("interviewQuestion.json")));
    private ChainBuilder endInterview =
            exec(http("Interview status change")
                    .patch("/rest/interviews/status/#{interviewId}?status=FINISHED")
                    .header("Authorization", "Bearer #{jwtToken}"));

    private ScenarioBuilder scenario = scenario("Test Scenario")
            .pause(3)
            .exec(authorisation, userInfo, profilePage,
                    settingsPage, interviewJournal, getInterview, createInterview,
                    interviewJournal, getCreatedInterview,

                    startInterview, getQuestionsForSkill, endInterview);

    @Override
    public void before() {
        LOGGER.info("Starting simulation");
        LOGGER.info("Running test with {} users", USER_COUNT);
        LOGGER.info("Ramping users over {} seconds", RAMP_DURATION);
    }

    {
        setUp(
                scenario.injectOpen(rampUsers(USER_COUNT).during(RAMP_DURATION))
        ).protocols(httpProtocol);
    }
}
