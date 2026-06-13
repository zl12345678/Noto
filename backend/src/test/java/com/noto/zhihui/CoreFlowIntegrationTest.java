package com.noto.zhihui;

import com.fasterxml.jackson.databind.JsonNode;
import com.noto.zhihui.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CoreFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void noteExtractTodosAndBoardFlow() throws Exception {
        String token = registerAndGetToken();
        long workspaceId = fetchFirstWorkspaceId(token);

        Map<String, Object> noteBody = Map.of(
                "title", "周会纪要",
                "content", "# 周会\n- [ ] 整理 Q2 目标\n- [ ] 周五前提交周报\n",
                "workspaceId", workspaceId
        );
        MvcResult noteResult = mockMvc.perform(post("/api/v1/notes")
                        .header("Authorization", authHeader(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noteBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long noteId = readLong(noteResult, "data.id");

        mockMvc.perform(post("/api/v1/notes/{id}/extract-todos", noteId)
                        .header("Authorization", authHeader(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.suggestions").isArray())
                .andExpect(jsonPath("$.data.suggestions.length()").value(2));

        Map<String, Object> confirmBody = Map.of(
                "items", List.of(
                        Map.of("title", "整理 Q2 目标", "completed", false),
                        Map.of("title", "周五前提交周报", "completed", false)
                )
        );
        mockMvc.perform(post("/api/v1/notes/{id}/extract-todos/confirm", noteId)
                        .header("Authorization", authHeader(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.createdCount").value(2));

        mockMvc.perform(get("/api/v1/todos/board")
                        .header("Authorization", authHeader(token))
                        .param("workspaceId", String.valueOf(workspaceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.actionTodos.length()").value(2));
    }

    @Test
    void createTodoViaApi() throws Exception {
        String token = registerAndGetToken();
        long workspaceId = fetchFirstWorkspaceId(token);

        Map<String, Object> body = Map.of(
                "workspaceId", workspaceId,
                "title", "手动创建的待办",
                "horizon", "action"
        );
        mockMvc.perform(post("/api/v1/todos")
                        .header("Authorization", authHeader(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("手动创建的待办"));
    }

    private long fetchFirstWorkspaceId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/workspaces")
                        .header("Authorization", authHeader(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").exists())
                .andReturn();
        return readLong(result, "data[0].id");
    }

    private long readLong(MvcResult result, String jsonPath) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        String[] parts = jsonPath.split("\\.");
        JsonNode current = node;
        for (String part : parts) {
            if (part.endsWith("]")) {
                int bracket = part.indexOf('[');
                String field = part.substring(0, bracket);
                int index = Integer.parseInt(part.substring(bracket + 1, part.length() - 1));
                current = current.path(field).get(index);
            } else {
                current = current.path(part);
            }
        }
        assertTrue(current.isNumber() || current.isTextual(), "Expected number or string at " + jsonPath);
        return current.isNumber() ? current.asLong() : Long.parseLong(current.asText());
    }
}
