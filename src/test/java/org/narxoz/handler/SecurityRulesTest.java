package org.narxoz.handler;

import org.junit.jupiter.api.Test;
import org.narxoz.config.Application;
import org.narxoz.config.RequestService;
import org.narxoz.domain.BusinessKey;
import org.narxoz.domain.PurchaseRequest;
import org.narxoz.domain.RequestId;
import org.narxoz.domain.RequestStatus;
import org.narxoz.security.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementContextAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.servlet.ServletManagementContextAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The README rules table, row by row. Real filter chain, mocked service, no database.
 * jwt() pretends a checked token with these roles arrived; no jwt() means no token.
 */
@WebMvcTest({RequestController.class, MeController.class})
@ContextConfiguration(classes = Application.class)
@Import({SecurityConfig.class, SecurityErrorHandlers.class})
@ImportAutoConfiguration({ // just enough actuator for /actuator/health inside the slice
        EndpointAutoConfiguration.class, WebEndpointAutoConfiguration.class,
        HealthContributorAutoConfiguration.class, HealthEndpointAutoConfiguration.class,
        ManagementContextAutoConfiguration.class, ServletManagementContextAutoConfiguration.class})
class SecurityRulesTest {
    private static final String URL = "/api/v1/purchase-requests";
    private static final RequestId ID = RequestId.newId();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RequestService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static RequestPostProcessor as(String username, String role) {
        return jwt().jwt(token -> token.subject(username))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static RequestPostProcessor requester() {
        return as("aigerim", SecurityConfig.REQUESTER);
    }

    private static RequestPostProcessor approver() {
        return as("daniyar", SecurityConfig.APPROVER);
    }

    @Test
    void readWithNoTokenIs401() throws Exception {
        mvc.perform(get(URL + "/" + ID.value()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("valid token required"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());

        verifyNoInteractions(service);
    }

    @Test
    void createWithNoTokenIs401Not201() throws Exception {
        mvc.perform(post(URL).contentType("application/json")
                        .content("{\"businessKey\":\"PR-19\",\"title\":\"Laptops\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void garbageTokenIs401() throws Exception {
        when(jwtDecoder.decode("hello")).thenThrow(new org.springframework.security.oauth2.jwt.BadJwtException("bad"));

        mvc.perform(get(URL + "/" + ID.value()).header("Authorization", "Bearer hello"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(service);
    }

    @Test
    void correlationIdIsEchoedOn401() throws Exception {
        mvc.perform(get(URL + "/" + ID.value()).header("X-Correlation-Id", "web-401"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.correlationId").value("web-401"))
                .andExpect(header().string("X-Correlation-Id", "web-401"));
    }

    @Test
    void requesterMayRead() throws Exception {
        when(service.get(ID)).thenReturn(new PurchaseRequest(ID, new BusinessKey("PR-19"), RequestStatus.DRAFT, "Laptops"));

        mvc.perform(get(URL + "/" + ID.value()).with(requester()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void requesterCannotApprove() throws Exception {
        mvc.perform(post(URL + "/" + ID.value() + "/approve").with(requester()).header("X-Correlation-Id", "web-403"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("not allowed for your role"))
                .andExpect(jsonPath("$.correlationId").value("web-403"));

        verifyNoInteractions(service); // the service method never ran, so the row did not change
    }

    @Test
    void approverApprovesAndTheActorComesFromTheToken() throws Exception {
        when(service.approve(eq(ID), any()))
                .thenReturn(new PurchaseRequest(ID, new BusinessKey("PR-19"), RequestStatus.APPROVED, "Laptops"));

        mvc.perform(post(URL + "/" + ID.value() + "/approve").with(approver())
                        .contentType("application/json")
                        .content("{\"actor\":\"someone-else\",\"role\":\"APPROVER\"}")) // ignored
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(service).approve(ID, "daniyar");
    }

    @Test
    void approveWithNoTokenIs401() throws Exception {
        mvc.perform(post(URL + "/" + ID.value() + "/approve"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void unknownRoleIs403OnTheApi() throws Exception {
        mvc.perform(get(URL + "/" + ID.value()).with(as("guest", "GUEST")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void anythingElseIsDenied() throws Exception {
        mvc.perform(get("/internal/admin").with(approver()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/admin"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsOpenWithNoToken() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void meReturnsTheUserFromTheToken() throws Exception {
        mvc.perform(get("/api/me").with(approver()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("daniyar"))
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_APPROVER"));
    }

    @Test
    void meWithNoTokenIs401() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
