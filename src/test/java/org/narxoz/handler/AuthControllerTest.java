package org.narxoz.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.narxoz.config.Application;
import org.narxoz.security.SecurityConfig;
import org.narxoz.security.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/v1/auth/login with the real dev users, the real BCrypt check and the real signer.
 * No database: the users live in memory on the dev profile.
 */
@WebMvcTest(AuthController.class)
@ContextConfiguration(classes = Application.class)
@Import({SecurityConfig.class, SecurityErrorHandlers.class, TokenService.class})
class AuthControllerTest {
    private static final String LOGIN = "/api/v1/auth/login";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtDecoder decoder;

    @Autowired
    private ObjectMapper json;

    private static String body(String username, String password) {
        return """
                {"username": "%s", "password": "%s"}
                """.formatted(username, password);
    }

    @Test
    void rightPasswordGetsA15MinuteBearerToken() throws Exception {
        String response = mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(body("daniyar", "approver-dev")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(content().string(not(containsString("approver-dev"))))
                .andReturn().getResponse().getContentAsString();

        Jwt token = decoder.decode(json.readTree(response).get("accessToken").asText());
        assertEquals("daniyar", token.getSubject());
        assertEquals("purchases", token.getClaimAsString("iss"));
        assertEquals(List.of("APPROVER"), token.getClaimAsStringList("roles"));
        assertEquals("HS256", token.getHeaders().get("alg").toString());
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(body("daniyar", "wrong")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("bad username or password"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void unknownUserGetsTheSameMessage() throws Exception {
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(body("nobody", "approver-dev")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("bad username or password"));
    }

    @Test
    void blankUsernameIs400() throws Exception {
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(body("", "approver-dev")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.message").value("username must not be blank"));
    }

    @Test
    void blankPasswordIs400() throws Exception {
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(body("daniyar", " ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("password must not be blank"));
    }
}
