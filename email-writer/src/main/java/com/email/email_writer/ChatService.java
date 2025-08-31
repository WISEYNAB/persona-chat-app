package com.email.email_writer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChatService {

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Autowired
    private ChatMessageRepository repository;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private WebClient webClient;

    public String generateChatResponse(ChatRequest chatRequest) {
        if (webClient == null) {
            webClient = webClientBuilder.build();
        }

        // 1. Get embedding for user message
        String embedding = getEmbedding(chatRequest.getMessage());

        // 2. Find similar past conversations (only if we have embeddings)
        List<ChatMessage> similarChats = List.of();
        if (!embedding.isEmpty()) {
            try {
                similarChats = repository.findSimilarMessages(embedding);
            } catch (Exception e) {
                // If vector search fails, continue without context
                System.out.println("Vector search failed: " + e.getMessage());
            }
        }

        // 3. Build context from similar chats
        String context = buildContext(similarChats);

        // 4. Generate response with context
        String response = callGemini(chatRequest.getMessage(), context);

        // 5. Save this conversation with manual SQL to handle vector casting
        try {
            if (!embedding.isEmpty()) {
                // Use native query to properly cast vector
                repository.saveWithEmbedding(
                        chatRequest.getMessage(),
                        response,
                        embedding
                );
            } else {
                // Save without embedding
                ChatMessage chatMessage = new ChatMessage();
                chatMessage.setUserMessage(chatRequest.getMessage());
                chatMessage.setBotResponse(response);
                repository.save(chatMessage);
            }
        } catch (Exception e) {
            System.out.println("Failed to save chat: " + e.getMessage());
        }

        return response;
    }

    private String getEmbedding(String text) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", "models/text-embedding-004",
                    "content", Map.of("parts", new Object[]{Map.of("text", text)})
            );

            String embeddingUrl = "https://generativelanguage.googleapis.com/v1beta/models/text-embedding-004:embedContent?key=" + geminiApiKey;

            String response = webClient.post()
                    .uri(embeddingUrl)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // Extract embedding array and convert to comma-separated string
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(response);
            JsonNode embeddingArray = rootNode.path("embedding").path("values");

            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < embeddingArray.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(embeddingArray.get(i).asDouble());
            }
            sb.append("]");
            return sb.toString();

        } catch (Exception e) {
            System.out.println("Embedding failed: " + e.getMessage());
            return "";
        }
    }

    private String buildContext(List<ChatMessage> similarChats) {
        if (similarChats.isEmpty()) {
            return "";
        }

        return similarChats.stream()
                .map(chat -> "User: " + chat.getUserMessage() + "\nYou: " + chat.getBotResponse())
                .collect(Collectors.joining("\n\n"));
    }

    private String callGemini(String userMessage, String context) {
        String prompt = buildPrompt(userMessage, context);

        Map<String, Object> requestBody = Map.of(
                "contents", new Object[]{
                        Map.of("parts", new Object[]{
                                Map.of("text", prompt)
                        })
                }
        );

        String response = webClient.post()
                .uri(geminiApiUrl + geminiApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return extractResponseContent(response);
    }

    private String buildPrompt(String userMessage, String context) {
        StringBuilder prompt = new StringBuilder();

        if (!context.isEmpty()) {
            prompt.append("You are an AI that has learned to communicate exactly like a specific person based on their chat history. ");
            prompt.append("Analyze the conversation patterns, tone, vocabulary, interests, and communication style from the following past conversations, ");
            prompt.append("then respond to the current message EXACTLY as that person would respond.\n\n");
            prompt.append("Past conversations showing this person's communication style:\n");
            prompt.append(context);
            prompt.append("\n\nBased on the above conversations, respond to this message in the SAME style, tone, and manner:\n");
        } else {
            prompt.append("You are having a casual conversation. Respond naturally and conversationally.\n\n");
        }

        prompt.append("Message: ").append(userMessage);
        prompt.append("\n\nResponse:");

        return prompt.toString();
    }

    private String extractResponseContent(String response) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(response);
            return rootNode.path("candidates")
                    .get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText();
        } catch (Exception e) {
            return "Error processing request: " + e.getMessage();
        }
    }

    public List<ChatMessage> getChatHistory() {
        return repository.findRecentMessages();
    }
}