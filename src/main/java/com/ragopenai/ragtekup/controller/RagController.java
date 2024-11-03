package com.ragopenai.ragtekup.controller;

import com.ragopenai.ragtekup.service.RagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
@RequestMapping("/")
public class RagController {

    @Autowired
    private RagService ragService;
    @Autowired
    private ResourceLoader resourceLoader;
    private static final String UPLOAD_DIR = "uploads/";


    @PostMapping("/upload-pdf")
    public String uploadFile(@RequestParam("file") MultipartFile file) {

        try {
            File pdfDirectory = new File(UPLOAD_DIR);
            if (!pdfDirectory.exists()) {
                pdfDirectory.mkdirs();
            }

            if (pdfDirectory.isDirectory() && pdfDirectory.list().length > 0) {
                for (File existingFile : pdfDirectory.listFiles()) {
                    if (!"hello-world.pdf".equals(existingFile.getName())) {
                        existingFile.delete();
                    }
                }
            }

            Path filePath = Paths.get(UPLOAD_DIR, file.getOriginalFilename());
            Files.write(filePath, file.getBytes());

        } catch (IOException e) {
            throw new RuntimeException("File upload failed", e);
        }
        return "rag";
    }


    @PostMapping("/embed-text")
    public String embedText(RedirectAttributes redirectAttributes) {
        ragService.textEmbedding();
        redirectAttributes.addFlashAttribute("message", "Text embedding process completed.");
        return "rag";
    }

    @GetMapping("/rag")
    public String RAG(@RequestParam(name = "query",defaultValue = "")String query, Model model)
    {
        if(query == null || query.isEmpty())
        {
            return "rag";
        }
        String response = ragService.RAG(query);
        model.addAttribute("query", query);
        model.addAttribute("response", response);
        return "rag";
    }
}
