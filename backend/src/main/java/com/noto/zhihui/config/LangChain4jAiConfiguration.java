package com.noto.zhihui.config;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "noto.ai", name = "enabled", havingValue = "true")
public class LangChain4jAiConfiguration {

    @Bean
    public ChatModel qwenChatModel(NotoAiProperties properties) {
        return QwenChatModel.builder()
                .apiKey(properties.getApiKey())
                .modelName(properties.getModel())
                .temperature(0.3f)
                .build();
    }

    @Bean
    public StreamingChatModel qwenStreamingChatModel(NotoAiProperties properties) {
        return QwenStreamingChatModel.builder()
                .apiKey(properties.getApiKey())
                .modelName(properties.getModel())
                .temperature(0.3f)
                .build();
    }

    @Bean
    public EmbeddingModel qwenEmbeddingModel(NotoAiProperties properties) {
        return QwenEmbeddingModel.builder()
                .apiKey(properties.getApiKey())
                .modelName(properties.getEmbeddingModel())
                .build();
    }
}
