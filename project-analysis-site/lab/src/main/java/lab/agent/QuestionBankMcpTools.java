package lab.agent;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 把"知识库题库"以 MCP 工具暴露给外部宿主（Claude Desktop、IDE 里的 Agent 等）的草图。
 *
 * <p>配合 spring-ai-starter-mcp-server-webmvc 使用：容器里的 ToolCallbackProvider Bean 会被自动注册为
 * MCP Server 的 tools，通过 Streamable HTTP 对外提供。注意边界：只暴露 ACTIVE 题目，
 * 抽题时不返回参考答案，核对答案时只返回评分要点的命中情况，避免把题库整份泄露出去。
 */
public class QuestionBankMcpTools {

  public record QuestionCard(long id, String category, String difficulty, String question, List<String> keyPoints) {
  }

  /** 生产中由 KnowledgeBaseQuestionRepository 实现，且只查询 ACTIVE 状态。 */
  public interface ActiveQuestionBank {
    List<QuestionCard> draw(String category, String difficulty, int limit);

    Optional<QuestionCard> findById(long id);
  }

  private final ActiveQuestionBank bank;

  public QuestionBankMcpTools(ActiveQuestionBank bank) {
    this.bank = bank;
  }

  @Tool(description = "按方向和难度随机抽取已启用的面试题，只返回题目 id 和题干")
  public List<Map<String, Object>> drawQuestions(
      @ToolParam(description = "方向，例如 Redis、RAG") String category,
      @ToolParam(description = "难度：junior、mid、senior") String difficulty,
      @ToolParam(description = "数量，1 到 5") int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 5));
    return bank.draw(category, difficulty, safeLimit).stream()
        .map(q -> Map.<String, Object>of("id", q.id(), "question", q.question()))
        .toList();
  }

  @Tool(description = "对某道题的回答做要点核对，返回命中与遗漏的评分要点，不返回参考答案原文")
  public Map<String, Object> checkAnswer(
      @ToolParam(description = "drawQuestions 返回的题目 id") long questionId,
      @ToolParam(description = "候选人的回答") String answer) {
    return bank.findById(questionId)
        .<Map<String, Object>>map(q -> {
          List<String> hit = q.keyPoints().stream().filter(answer::contains).toList();
          List<String> missed = q.keyPoints().stream().filter(p -> !answer.contains(p)).toList();
          return Map.of("hit", hit, "missed", missed);
        })
        .orElse(Map.of("error", "NOT_FOUND"));
  }

  @Configuration
  public static class McpToolsConfig {

    @Bean
    public ToolCallbackProvider questionBankTools(QuestionBankMcpTools tools) {
      return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
  }
}
