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
        registry.add("noto.demo.username", () -> "demo");
        registry.add("noto.demo.password", () -> "demo12345");
        registry.add("noto.demo.email", () -> "demo@noto.local");
        registry.add("noto.demo.nickname", () -> "演示账号");
        registry.add("noto.demo.create-user", () -> "true");
    }

    @Test
    void configuredDemoUserShouldHaveDemoNotesAndTodos() throws Exception {
        String token = login("demo", "demo12345");

        mockMvc.perform(get("/api/v1/notes")
                        .header("Authorization", authHeader(token))
                        .param("page", "1")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(20)));

        mockMvc.perform(get("/api/v1/todos/board")
                        .header("Authorization", authHeader(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.actionTodos.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(5)));
    }

    private String login(String username, String password) throws Exception {
        var body = java.util.Map.of(
                "username", username,
                "password", password
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
