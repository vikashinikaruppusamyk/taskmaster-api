package com.vikashini.taskmaster.ai;

import com.vikashini.taskmaster.auth.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Tag(name = "AI assistance")
public class AiController {

    private final AiService aiService;

    @PostMapping("/task-description")
    @Operation(summary = "Draft a task description from a title and optional notes",
            description = "Returns text only; nothing is saved. Answers 503 when ANTHROPIC_API_KEY is not set.")
    public GeneratedText describe(@Valid @RequestBody DescribeRequest request) {
        return new GeneratedText(aiService.describeTask(request.title(), request.notes()));
    }

    @PostMapping("/tasks/{taskId}/summary")
    @Operation(summary = "Summarise a task and its comments",
            description = "Returns text only; nothing is saved. Answers 503 when ANTHROPIC_API_KEY is not set.")
    public GeneratedText summarize(@CurrentUserId Long userId, @PathVariable Long taskId) {
        return new GeneratedText(aiService.summarizeTask(userId, taskId));
    }

    public record DescribeRequest(
            @NotBlank(message = "is required")
            @Size(max = 200, message = "must be at most 200 characters")
            String title,

            @Size(max = 2000, message = "must be at most 2000 characters")
            String notes) {
    }

    public record GeneratedText(String text) {
    }
}
