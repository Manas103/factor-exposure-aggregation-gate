package com.manas.fxexp.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Measures the two claims this gate exists to support: 120 commentaries
 * generated from real, freshly recomputed aggregates are never blocked
 * (every number in them is, by construction, correct), and 40 of those
 * same commentaries with exactly one figure corrupted are always blocked.
 * This is the actual measurement behind the README's "40 of 40" / "0 of
 * 120" numbers, not an illustrative subset.
 */
@SpringBootTest
class CommentaryGateTest {

    private static final int N_CORRECT = 120;
    private static final int N_CORRUPTED = 40;

    @Autowired
    private ExposureAggregationService exposureAggregationService;
    @Autowired
    private CommentaryService commentaryService;
    @Autowired
    private CommentaryPublishGate commentaryPublishGate;

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(-?\\d+\\.\\d+)");

    @Test
    void zeroOfOneHundredTwentyCorrectCommentariesAreBlocked() {
        List<String> fundIds = seedFunds(N_CORRECT);
        int blocked = 0;
        for (String fundId : fundIds) {
            String text = commentaryService.generateFundCommentary(fundId);
            PublishVerdict verdict = commentaryPublishGate.verifyFundCommentary(fundId, text);
            if (!verdict.allowed()) blocked++;
        }
        System.out.println(blocked + " of " + N_CORRECT + " correct commentaries blocked (claim: 0)");
        assertThat(blocked).isEqualTo(0);
    }

    @Test
    void fortyOfFortySeededWrongFiguresAreBlocked() {
        List<String> fundIds = seedFunds(N_CORRUPTED);
        Random rng = new Random(7);
        int blocked = 0;
        for (String fundId : fundIds) {
            String text = commentaryService.generateFundCommentary(fundId);
            String corrupted = corruptOneNumber(text, rng);
            PublishVerdict verdict = commentaryPublishGate.verifyFundCommentary(fundId, corrupted);
            if (!verdict.allowed()) blocked++;
        }
        System.out.println(blocked + " of " + N_CORRUPTED + " seeded wrong figures blocked (claim: "
                + N_CORRUPTED + ")");
        assertThat(blocked).isEqualTo(N_CORRUPTED);
    }

    private List<String> seedFunds(int n) {
        List<String> fundIds = new ArrayList<>();
        Random rng = new Random(123);
        for (int i = 0; i < n; i++) {
            String fundId = "FUND-" + UUID.randomUUID();
            String strategyId = "STRAT-" + UUID.randomUUID();
            int nTrades = 1 + rng.nextInt(3);
            for (int t = 0; t < nTrades; t++) {
                exposureAggregationService.applyTradeEvent("TRD-" + UUID.randomUUID(), strategyId, fundId, "INSTR",
                        randomExposure(rng), randomExposure(rng), randomExposure(rng), randomExposure(rng),
                        randomExposure(rng));
            }
            fundIds.add(fundId);
        }
        return fundIds;
    }

    private static BigDecimal randomExposure(Random rng) {
        return BigDecimal.valueOf(rng.nextInt(400_000) - 200_000).setScale(2);
    }

    /** Replaces exactly one embedded figure with a value guaranteed to
     * differ from the original (adds a fixed, non-trivial offset), the
     * same kind of "seeded wrong figure" a corrupted or hallucinated LLM
     * summary would produce. */
    private static String corruptOneNumber(String text, Random rng) {
        List<int[]> spans = new ArrayList<>();
        Matcher m = NUMBER_PATTERN.matcher(text);
        while (m.find()) spans.add(new int[]{m.start(), m.end()});
        int[] chosen = spans.get(rng.nextInt(spans.size()));
        String original = text.substring(chosen[0], chosen[1]);
        BigDecimal corrupted = new BigDecimal(original).add(BigDecimal.valueOf(999_999.99));
        return text.substring(0, chosen[0]) + corrupted.toPlainString() + text.substring(chosen[1]);
    }
}
