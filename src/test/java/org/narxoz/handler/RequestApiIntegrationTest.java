package org.narxoz.handler;

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

import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole app on the dev profile against real PostgreSQL:
 * the second POST of a key is 409 and the row count stays 1; actuator and springdoc are wired.
 * NO @Transactional here: counts must see committed rows.
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

    @BeforeEach
    void empty() {
        jdbc.update("DELETE FROM purchase_request");
    }

    private static String body(String businessKey, String title) {
        return """
                {"businessKey": "%s", "title": "%s"}
                """.formatted(businessKey, title);
    }

    @Test
    void secondPostOfTheSameKeyIs409AndCountStays1() throws Exception {
        String location = mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("PR-19", "Laptops")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("PR-19", "Laptops again")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));

        assertEquals(1, requests.count("PR-19"));

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessKey").value("PR-19"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.title").value("Laptops"));

        assertEquals(1, requests.count("PR-19")); // GET does not change the row
    }

    @Test
    void blankTitleLeavesNoRow() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("PR-21", "")))
                .andExpect(status().isBadRequest());

        assertEquals(0, requests.count("PR-21"));
    }

    @Test
    void unknownUuidIs404() throws Exception {
        mvc.perform(get(URL + "/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
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
    void envAndHeapdumpAreNotExposed() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/heapdump")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/beans")).andExpect(status().isNotFound());
    }

    @Test
    void springdocListsTheContract() throws Exception {
        String path = "$.paths['" + URL + "']";
        String byId = "$.paths['" + URL + "/{id}']";
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(path + ".post.responses['201']").exists())
                .andExpect(jsonPath(path + ".post.responses['400']").exists())
                .andExpect(jsonPath(path + ".post.responses['409']").exists())
                .andExpect(jsonPath(byId + ".get.responses['200']").exists())
                .andExpect(jsonPath(byId + ".get.responses['404']").exists())
                .andExpect(jsonPath("$.components.schemas.CreateRequestBody.required", hasItems("businessKey", "title")))
                .andExpect(jsonPath("$.components.schemas.RequestEntity").doesNotExist());
    }
}
