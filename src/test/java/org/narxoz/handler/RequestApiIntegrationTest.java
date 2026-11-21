package org.narxoz.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.narxoz.config.Application;
import org.narxoz.persistence.RequestJpa;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.narxoz.domain.RequestStatus.APPROVED;
import static org.narxoz.domain.RequestStatus.DRAFT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole app on the dev profile against real PostgreSQL, with real logins and real tokens:
 * the second POST of a key is 409 and the row count stays 1; REQUESTER approve is 403 and the row stays DRAFT;
 * APPROVER approve moves it to APPROVED. NO @Transactional here: counts must see committed rows.
 */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
class RequestApiIntegrationTest {
    private static final String URL = "/api/v1/purchase-requests";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RequestJpa requests;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @BeforeEach
    void empty() {
        jdbc.update("DELETE FROM purchase_request");
    }

    private static String body(String businessKey, String title) {
        return """
                {"businessKey": "%s", "title": "%s"}
                """.formatted(businessKey, title);
    }

    /** A real login through the real filter chain; returns "Bearer eyJ...". */
    private String bearer(String username, String password) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(response).get("accessToken").asText();
    }

    private String requester() throws Exception {
        return bearer("aigerim", "requester-dev");
    }

    private String approver() throws Exception {
        return bearer("daniyar", "approver-dev");
    }

    private String create(String token, String businessKey) throws Exception {
        return mvc.perform(post(URL).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(businessKey, "Laptops")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
    }

    @Test
    void secondPostOfTheSameKeyIs409AndCountStays1() throws Exception {
        String token = requester();
        String location = create(token, "PR-19");

        mvc.perform(post(URL).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body("PR-19", "Laptops again")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));

        assertEquals(1, requests.count("PR-19"));

        mvc.perform(get(location).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessKey").value("PR-19"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.title").value("Laptops"));

        assertEquals(1, requests.count("PR-19")); // GET does not change the row
    }

    @Test
    void anonymousCreateIs401AndLeavesNoRow() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("PR-22", "Laptops")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertEquals(0, requests.count("PR-22"));
    }

    @Test
    void blankTitleLeavesNoRow() throws Exception {
        mvc.perform(post(URL).header("Authorization", requester())
                        .contentType(MediaType.APPLICATION_JSON).content(body("PR-21", "")))
                .andExpect(status().isBadRequest());

        assertEquals(0, requests.count("PR-21"));
    }

    @Test
    void unknownUuidIs404() throws Exception {
        mvc.perform(get(URL + "/00000000-0000-0000-0000-000000000000").header("Authorization", requester()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void requesterApproveIs403AndTheRowStaysDraft() throws Exception {
        String token = requester();
        String location = create(token, "PR-30");

        mvc.perform(post(location + "/approve").header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertEquals(Optional.of(DRAFT), requests.findStatus("PR-30"));
    }

    @Test
    void approverApprovesThenASecondApproveIs409() throws Exception {
        String location = create(requester(), "PR-31");
        String approver = approver();

        mvc.perform(post(location + "/approve").header("Authorization", approver))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertEquals(Optional.of(APPROVED), requests.findStatus("PR-31"));

        mvc.perform(post(location + "/approve").header("Authorization", approver))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSITION_FORBIDDEN"));

        assertEquals(Optional.of(APPROVED), requests.findStatus("PR-31"));
        assertEquals(1, requests.count("PR-31")); // approve changes a status, it never deletes or adds a row
    }

    @Test
    void meShowsTheLoggedInUser() throws Exception {
        mvc.perform(get("/api/me").header("Authorization", approver()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("daniyar"))
                .andExpect(jsonPath("$.authorities", hasItems("ROLE_APPROVER")));
    }

    @Test
    void tamperedTokenIs401() throws Exception {
        String token = requester();
        String tampered = token.substring(0, token.length() - 4) + "AAAA"; // signature no longer matches

        mvc.perform(get("/api/me").header("Authorization", tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void healthIsUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void infoCarriesTheProductName() throws Exception {
        mvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.name").value("Purchase Request System"));
    }

    @Test
    void envHeapdumpAndBeansStayClosed() throws Exception {
        // not exposed (Lab 4) and now also not in the rules table: no token is 401, any token is 403
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/heapdump")).andExpect(status().isUnauthorized());
        String token = approver();
        mvc.perform(get("/actuator/env").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/heapdump").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/beans").header("Authorization", token)).andExpect(status().isForbidden());
    }

    @Test
    void authPageIsServedWithNoToken() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk());
        mvc.perform(get("/app.js")).andExpect(status().isOk());
    }

    @Test
    void springdocListsTheContract() throws Exception {
        String path = "$.paths['" + URL + "']";
        String byId = "$.paths['" + URL + "/{id}']";
        String approve = "$.paths['" + URL + "/{id}/approve']";
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(path + ".post.responses['201']").exists())
                .andExpect(jsonPath(path + ".post.responses['400']").exists())
                .andExpect(jsonPath(path + ".post.responses['409']").exists())
                .andExpect(jsonPath(byId + ".get.responses['200']").exists())
                .andExpect(jsonPath(byId + ".get.responses['404']").exists())
                .andExpect(jsonPath(approve + ".post.responses['200']").exists())
                .andExpect(jsonPath(approve + ".post.responses['401']").exists())
                .andExpect(jsonPath(approve + ".post.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/me'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/me'].get.responses['401']").exists())
                .andExpect(jsonPath("$.components.schemas.CreateRequestBody.required", hasItems("businessKey", "title")))
                .andExpect(jsonPath("$.components.schemas.RequestEntity").doesNotExist());
    }
}
