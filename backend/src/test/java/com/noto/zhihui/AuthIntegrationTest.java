package com.noto.zhihui;

import com.noto.zhihui.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void configureDemoUser(DynamicPropertyRegistry registry) {
        registry.add("noto.demo.enabled", () -> "true");
        registry.add("noto.demo.username", () -> "demo");
    }

    @Test
    void registerShouldReturnToken() throws Exception {
        registerAndGetToken();
    }

    @Test
    void loginWithDefaultAdminShouldSucceed() throws Exception {
        Map<String, String> body = Map.of(
                "username", "admin",
                "password", "admin123"
        );
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value("admin"));
    }

    @Test
    void meShouldRejectWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void meShouldReturnCurrentUserWithToken() throws Exception {
        String token = registerAndGetToken();
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", authHeader(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").isNotEmpty());
    }

    @Test
    void changePasswordShouldReportWrongCurrentPassword() throws Exception {
        String token = registerAndGetToken();
        Map<String, String> body = Map.of(
                "oldPassword", "Wrong123456",
                "newPassword", "NewPass123456"
        );
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", authHeader(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40020))
                .andExpect(jsonPath("$.message").value("当前密码错误"));
    }

    @Test
    void demoUserShouldNotChangePassword() throws Exception {
        Map<String, String> registerBody = Map.of(
                "username", "demo",
                "email", "demo-auth-test@noto.test",
                "nickname", "演示账号",
                "password", "DemoPass123"
        );
        var registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();
        String token = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .path("data").path("token").asText();

        Map<String, String> body = Map.of(
                "oldPassword", "DemoPass123",
                "newPassword", "NewDemoPass123"
        );
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", authHeader(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40021))
                .andExpect(jsonPath("$.message").value("演示账号不允许修改密码"));
    }

    @Test
    void forgotPasswordEndpointShouldBeRemoved() throws Exception {
        Map<String, String> body = Map.of(
                "email", "unknown@example.com",
                "newPassword", "newpass123"
        );
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }
}
