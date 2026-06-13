package com.noto.zhihui;

import com.noto.zhihui.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DemoDataIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void enableDemoSeed(DynamicPropertyRegistry registry) {
        registry.add("noto.demo.enabled", () -> "true");
    }

    @Test
    void adminShouldHaveDemoNotesAndTodos() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/api/v1/notes")
                        .header("Authorization", authHeader(token))
                        .param("page", "1")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(20)))
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(50)));

        mockMvc.perform(get("/api/v1/todos/board")
                        .header("Authorization", authHeader(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.actionTodos.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(5)));
    }

    private String loginAsAdmin() throws Exception {
        var body = java.util.Map.of(
                "username", "admin",
                "password", "admin123"
        );
        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }
}
