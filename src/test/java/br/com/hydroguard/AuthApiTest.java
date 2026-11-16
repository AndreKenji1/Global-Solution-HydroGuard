package br.com.hydroguard;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiTest {
    private static final String TEST_PASSWORD="senha-exclusiva-teste";
    private static final String HASH=new BCryptPasswordEncoder(10).encode(TEST_PASSWORD);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("hydroguard.admin.password-hash",() -> HASH);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    private String loginBody(String password) throws Exception {
        return json.writeValueAsString(java.util.Map.of("username","admin-test","password",password));
    }
    @Test void loginRequiresCsrf() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody(TEST_PASSWORD)))
            .andExpect(status().isForbidden());
    }
    @Test void badCredentialsReturnGeneric401() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(loginBody("errada")))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message",is("Usuário ou senha inválidos")));
    }
    @Test void sessionLoginRotatesIdAndCsrfAndLogoutRevokesAccess() throws Exception {
        var first=mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var csrf=json.readTree(first.getResponse().getContentAsByteArray());
        Cookie oldCookie=first.getResponse().getCookie("SESSION");assertNotNull(oldCookie);
        var login=mvc.perform(post("/api/auth/login").cookie(oldCookie)
            .header(csrf.path("headerName").asText(),csrf.path("token").asText())
            .contentType(MediaType.APPLICATION_JSON).content(loginBody(TEST_PASSWORD)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.roles",hasItem("ADMIN"))).andReturn();
        Cookie session=login.getResponse().getCookie("SESSION");assertNotNull(session);
        assertNotEquals(oldCookie.getValue(),session.getValue());assertTrue(session.isHttpOnly());
        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.username",is("admin-test")));
        mvc.perform(get("/api/auth/me").cookie(oldCookie)).andExpect(status().isUnauthorized());
        var next=mvc.perform(get("/api/auth/csrf").cookie(session)).andExpect(status().isOk()).andReturn();
        var nextCsrf=json.readTree(next.getResponse().getContentAsByteArray());
        assertNotEquals(csrf.path("token").asText(),nextCsrf.path("token").asText());
        mvc.perform(post("/api/auth/logout").cookie(session)
            .header(nextCsrf.path("headerName").asText(),nextCsrf.path("token").asText())).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    }
    @Test void missingCredentialsAreRejected() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }
    @Test void utf8LongPasswordIsRejectedWithout500() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(loginBody("á".repeat(40))))
            .andExpect(status().isUnauthorized());
    }
}
