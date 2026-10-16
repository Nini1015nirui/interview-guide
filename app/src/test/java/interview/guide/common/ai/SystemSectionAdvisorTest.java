package interview.guide.common.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

@DisplayName("SystemSectionAdvisor 测试")
class SystemSectionAdvisorTest {

  @Test
  @DisplayName("before 把片段追加到系统提示词，并保留原有系统提示词和用户消息")
  void before_appendsSectionToSystemMessage() {
    ChatClientRequest request = ChatClientRequest.builder()
        .prompt(Prompt.builder().messages(
            new SystemMessage("你是面试官"),
            new UserMessage("你好")).build())
        .build();

    SystemSectionAdvisor advisor = new SystemSectionAdvisor("jd", 220, "\n\n高级 Java 工程师");
    ChatClientRequest result = advisor.before(request, null);

    assertThat(result.prompt().getSystemMessage().getText())
        .contains("你是面试官")
        .contains("高级 Java 工程师");
    assertThat(result.prompt().getUserMessage().getText()).isEqualTo("你好");
  }

  @Test
  @DisplayName("没有系统消息时 before 也能创建系统消息")
  void before_createsSystemMessageWhenAbsent() {
    ChatClientRequest request = ChatClientRequest.builder()
        .prompt(Prompt.builder().messages(new UserMessage("你好")).build())
        .build();

    ChatClientRequest result =
        new SystemSectionAdvisor("role", 200, "你是面试官").before(request, null);

    assertThat(result.prompt().getSystemMessage().getText()).contains("你是面试官");
  }
}
