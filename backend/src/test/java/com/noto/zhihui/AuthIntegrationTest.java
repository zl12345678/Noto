package com.noto.zhihui;

import com.noto.zhihui.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends AbstractIntegrationTest {

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
