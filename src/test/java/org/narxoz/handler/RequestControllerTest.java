package org.narxoz.handler;

import org.junit.jupiter.api.Test;
import org.narxoz.config.Application;
import org.narxoz.config.RequestService;
import org.narxoz.domain.BusinessKey;
import org.narxoz.domain.DuplicateRequest;
import org.narxoz.domain.PurchaseRequest;
import org.narxoz.domain.RequestId;
import org.narxoz.domain.RequestNotFound;
import org.narxoz.domain.RequestStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice: only the controller and the advice. The service is a mock,
 * no PostgreSQL, no real HTTP server. Locks 201, 400, 404, 409 and the correlation header.
 */
@WebMvcTest(RequestController.class)
@ContextConfiguration(classes = Application.class) // Application lives in config, not in a parent package
class RequestControllerTest {
    private static final String URL = "/api/v1/purchase-requests";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RequestService service;

    private static String body(String businessKey, String title) {
        return """
                {"businessKey": "%s", "title": "%s"}
                """.formatted(businessKey, title);
    }

    private PurchaseRequest stored(RequestId id, String key, String title) {
        return new PurchaseRequest(id, new BusinessKey(key), RequestStatus.DRAFT, title);
    }

    @Test
    void validBodyIs201WithLocation() throws Exception {
        RequestId id = RequestId.newId();
        when(service.register(any(), eq("PR-19"), eq("Laptops")))
                .thenReturn(stored(id, "PR-19", "Laptops"));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("PR-19", "Laptops")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(URL + "/" + id.value())))
                .andExpect(jsonPath("$.id").value(id.value()))
                .andExpect(jsonPath("$.businessKey").value("PR-19"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.title").value("Laptops"));
    }

    @Test
    void titleOf120CharactersIs201() throws Exception {
        String title = "a".repeat(120);
        RequestId id = RequestId.newId();
        when(service.register(any(), eq("PR-20"), eq(title))).thenReturn(stored(id, "PR-20", title));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("PR-20", title)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value(title));
    }

    @Test
    void blankTitleIs400AndServiceIsNotCalled() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("PR-19", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.message").value("title must not be blank"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());

        verifyNoInteractions(service);
    }

    @Test
    void titleOfOnlySpacesIs400() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("PR-19", "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("title must not be blank"));

        verifyNoInteractions(service);
    }

    @Test
    void blankBusinessKeyIs400() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("", "Laptops")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.message").value("business key must not be blank"));

        verifyNoInteractions(service);
    }

    @Test
    void titleOf121CharactersIs400() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("PR-19", "a".repeat(121))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("title")))
                .andExpect(jsonPath("$.message", containsString("size must be between 0 and 120")));

        verifyNoInteractions(service);
    }

    @Test
    void bodyThatIsNotJsonIs400() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.message").value("body must be JSON"));
    }

    @Test
    void wrongContentTypeIs415() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.TEXT_PLAIN).content("PR-19"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED"));
    }

    @Test
    void duplicateBusinessKeyIs409() throws Exception {
        when(service.register(any(), eq("PR-19"), anyString())).thenThrow(new DuplicateRequest("PR-19"));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("PR-19", "Again")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void existingIdIs200() throws Exception {
        RequestId id = RequestId.newId();
        when(service.get(id)).thenReturn(stored(id, "PR-19", "Laptops"));

        mvc.perform(get(URL + "/" + id.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessKey").value("PR-19"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void missingIdIs404() throws Exception {
        RequestId id = RequestId.newId();
        when(service.get(id)).thenThrow(new RequestNotFound(id));

        mvc.perform(get(URL + "/" + id.value()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void businessKeyInThePathIs400() throws Exception {
        mvc.perform(get(URL + "/PR-19"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.message").value("id must be a UUID"));

        verifyNoInteractions(service);
    }

    @Test
    void correlationIdFromTheCallerComesBack() throws Exception {
        mvc.perform(post(URL)
                        .header("X-Correlation-Id", "test-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("PR-19", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.correlationId").value("test-123"))
                .andExpect(header().string("X-Correlation-Id", "test-123"));
    }

    @Test
    void errorBodyHasNoStackTraceOrClassName() throws Exception {
        when(service.register(any(), anyString(), anyString())).thenThrow(new IllegalStateException("db password is css"));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("PR-30", "Laptops")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL"))
                .andExpect(jsonPath("$.message").value("unexpected error"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }
}
