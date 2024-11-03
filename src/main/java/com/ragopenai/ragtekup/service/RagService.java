package com.ragopenai.ragtekup.service;


import com.ragopenai.ragtekup.exceptions.RetryDueToResponseException;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.mistralai.MistralAiChatClient;
import org.springframework.ai.mistralai.MistralAiChatOptions;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@EnableRetry
public class RagService {

    @Autowired
    VectorStore vectorStore;

    @Autowired
    JdbcTemplate jdbcTemplate;


    @Value("file:uploads/*")
    private Resource[] pdfResources;
    @Retryable(
            value = {NonTransientAiException.class, RetryDueToResponseException.class},
            maxAttempts = 20,
            backoff = @Backoff(delay = 2000)
    )
    public String RAG(String query) {
        List<Document> documentList = vectorStore.similaritySearch(SearchRequest.query(query).withTopK(50));



        String systemMessageTemplate = """
                Donner les informtaions demandées uniquement en vous basant sur le CONTEXTE fourni.
                Si les informations demandées ne se trouve pas dans le contexte, répondez "je ne sais pas".
                CONTEXT:
                {CONTEXT}
                """;
        System.out.println("RAG");
        Message systemMessage = new SystemPromptTemplate(systemMessageTemplate)
                .createMessage(Map.of("CONTEXT", documentList));
        UserMessage userMessage =new UserMessage(query);
        Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
        MistralAiApi mistralAiApi = new MistralAiApi("iXhwiDHEpDPEqJs8bzl1cisKv6hMjQQa");
        MistralAiChatOptions options = MistralAiChatOptions.builder()
                .withTemperature(0.1F).withModel("mistral-large-latest").withMaxToken(32000)
                .build();
        MistralAiChatClient mistralAiChatClient = new MistralAiChatClient(mistralAiApi, options);

        ChatResponse response = mistralAiChatClient.call(prompt);
        String result = response.getResult().getOutput().getContent().toLowerCase();
        if ("je ne sais pas.".equals(result) || "je ne sais pas".equals(result)

                || result.isEmpty())
        {
            throw new RetryDueToResponseException("Retrying because the response was: " + response.getResult().getOutput().getContent());

        }
        return response.getResult().getOutput().getContent();


    }

    public String textEmbedding() {
        jdbcTemplate.update("DELETE FROM vector_store");

        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.defaultConfig();
        StringBuilder contentBuilder = new StringBuilder();

        for (Resource resource : pdfResources) {
            PagePdfDocumentReader pdfDocumentReader = new PagePdfDocumentReader(resource, config);
            List<Document> documentList = pdfDocumentReader.get();

            documentList.forEach(doc -> {
                String trimmedContent = doc.getContent().trim().replaceAll("\\s+", " ");
                contentBuilder.append(trimmedContent).append("\n");
            });
        }

        // Split content into chunks
        String content = contentBuilder.toString().trim();
        TokenTextSplitter tokenTextSplitter = new TokenTextSplitter();
        List<String> chunks = tokenTextSplitter.split(content, content.length());

        // Convert chunks to documents and save to vector store
        List<Document> chunkDocs = chunks.stream()
                .map(Document::new)
                .collect(Collectors.toList());
        vectorStore.accept(chunkDocs);
        return "DONE";
    }


}
