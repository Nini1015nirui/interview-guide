package lab.agent;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.IntStream;

/**
 * LLM 评委与人工评分的一致性指标：平均绝对误差、±10 分内一致率、Spearman 秩相关。
 *
 * <p>用法：固定一批"题目 + 回答 + 人工分"作为金标集，每次改评估 Prompt、换模型或换分批大小都跑一遍，
 * 指标下降就不合入。只看平均分会掩盖问题，秩相关回答的是"好答案是否排在差答案前面"。
 */
public final class JudgeAgreement {

  public record Report(int samples, double mae, double within10, double spearman) {
  }

  private JudgeAgreement() {
  }

  public static Report compare(int[] llmScores, int[] humanScores) {
    if (llmScores.length != humanScores.length || llmScores.length < 2) {
      throw new IllegalArgumentException("两组分数长度必须一致且至少 2 条");
    }
    int n = llmScores.length;
    double mae = IntStream.range(0, n).map(i -> Math.abs(llmScores[i] - humanScores[i])).average().orElse(0);
    double within10 = IntStream.range(0, n).filter(i -> Math.abs(llmScores[i] - humanScores[i]) <= 10).count()
        / (double) n;
    return new Report(n, mae, within10, pearson(ranks(llmScores), ranks(humanScores)));
  }

  /** 平均秩（并列取平均），Spearman 等于秩上的 Pearson 相关。 */
  static double[] ranks(int[] values) {
    Integer[] order = IntStream.range(0, values.length).boxed().toArray(Integer[]::new);
    Arrays.sort(order, Comparator.comparingInt(i -> values[i]));
    double[] ranks = new double[values.length];
    int i = 0;
    while (i < order.length) {
      int j = i;
      while (j + 1 < order.length && values[order[j + 1]] == values[order[i]]) {
        j++;
      }
      double avg = (i + j) / 2.0 + 1;
      for (int k = i; k <= j; k++) {
        ranks[order[k]] = avg;
      }
      i = j + 1;
    }
    return ranks;
  }

  static double pearson(double[] a, double[] b) {
    double ma = Arrays.stream(a).average().orElse(0);
    double mb = Arrays.stream(b).average().orElse(0);
    double cov = 0;
    double va = 0;
    double vb = 0;
    for (int i = 0; i < a.length; i++) {
      cov += (a[i] - ma) * (b[i] - mb);
      va += (a[i] - ma) * (a[i] - ma);
      vb += (b[i] - mb) * (b[i] - mb);
    }
    return (va == 0 || vb == 0) ? 0 : cov / Math.sqrt(va * vb);
  }
}
