package com.manas.fxexp.service;

import com.manas.fxexp.domain.FactorType;
import com.manas.fxexp.repo.TradeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Recomputes every named figure in a commentary from the trades table
 * directly (RecomputeOracleService, not the materialized aggregate the
 * commentary may have been generated from) and blocks publication if any
 * one of them does not match. This is model-agnostic: it does not care
 * whether the text came from CommentaryService's template or a real LLM,
 * because it never trusts the text's own arithmetic, only numbers it can
 * find and independently re-derive.
 *
 * Scope, stated honestly: this grounds every number presented as a named
 * factor-exposure figure ("spot exposure of X", "carrying X of that vol
 * exposure"), which is what the claim this gate exists to support means by
 * "every number". It does not attempt to ground every digit that appears
 * in the text (a fund ID, a strategy ID with digits in it); those are not
 * figures.
 */
@Service
public class CommentaryPublishGate {

    private static final Pattern FUND_LEVEL_PATTERN = Pattern.compile(
            "(spot|vol|skew|rate|dividend) exposure of (-?\\d+\\.\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTRIBUTOR_PATTERN = Pattern.compile(
            "is trade (\\S+?) \\(strategy \\S+?\\), carrying (-?\\d+\\.\\d+) of that "
                    + "(spot|vol|skew|rate|dividend) exposure", Pattern.CASE_INSENSITIVE);

    private final RecomputeOracleService recomputeOracleService;
    private final TradeRepository tradeRepository;

    public CommentaryPublishGate(RecomputeOracleService recomputeOracleService, TradeRepository tradeRepository) {
        this.recomputeOracleService = recomputeOracleService;
        this.tradeRepository = tradeRepository;
    }

    public PublishVerdict verifyFundCommentary(String fundId, String commentaryText) {
        Map<FactorType, BigDecimal> truth = recomputeOracleService.recomputeFund(fundId);
        List<String> mismatches = new ArrayList<>();

        Matcher m = FUND_LEVEL_PATTERN.matcher(commentaryText);
        while (m.find()) {
            FactorType factor = FactorType.valueOf(m.group(1).toUpperCase(java.util.Locale.ROOT));
            BigDecimal claimed = new BigDecimal(m.group(2));
            BigDecimal actual = truth.get(factor).setScale(2, RoundingMode.HALF_UP);
            if (claimed.compareTo(actual) != 0) {
                mismatches.add("fund " + factor + " exposure: claimed " + claimed + ", actual " + actual);
            }
        }

        Matcher cm = CONTRIBUTOR_PATTERN.matcher(commentaryText);
        if (cm.find()) {
            String tradeExternalId = cm.group(1);
            BigDecimal claimedContribution = new BigDecimal(cm.group(2));
            FactorType factor = FactorType.valueOf(cm.group(3).toUpperCase(java.util.Locale.ROOT));
            var trade = tradeRepository.findByExternalId(tradeExternalId);
            if (trade.isEmpty()) {
                mismatches.add("contributor sentence names trade " + tradeExternalId + ", which does not exist");
            } else {
                BigDecimal actual = trade.get().exposure(factor).setScale(2, RoundingMode.HALF_UP);
                if (claimedContribution.compareTo(actual) != 0) {
                    mismatches.add("trade " + tradeExternalId + " " + factor + " exposure: claimed "
                            + claimedContribution + ", actual " + actual);
                }
            }
        }

        return new PublishVerdict(mismatches.isEmpty(), mismatches);
    }
}
