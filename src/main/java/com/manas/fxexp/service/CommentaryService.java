package com.manas.fxexp.service;

import com.manas.fxexp.domain.FactorType;
import com.manas.fxexp.domain.FundAggregate;
import com.manas.fxexp.domain.Trade;
import com.manas.fxexp.repo.FundAggregateRepository;
import com.manas.fxexp.repo.TradeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Generates a plain-English commentary naming a fund's five factor
 * exposures and its single largest contributing trade on whichever factor
 * is largest in magnitude. This is a deterministic template, not a call to
 * a real language model: no `claude -p` or hosted LLM credential is
 * available in this headless build environment, so the stand-in is
 * disclosed here and in the README rather than hidden. CommentaryPublishGate
 * does not care how the text was produced; it only reads the numbers back
 * out of whatever text it is given and checks them, which is exactly the
 * property that would make it work unchanged in front of a real model.
 */
@Service
public class CommentaryService {

    private final FundAggregateRepository fundAggregateRepository;
    private final TradeRepository tradeRepository;

    public CommentaryService(FundAggregateRepository fundAggregateRepository, TradeRepository tradeRepository) {
        this.fundAggregateRepository = fundAggregateRepository;
        this.tradeRepository = tradeRepository;
    }

    public String generateFundCommentary(String fundId) {
        FundAggregate agg = fundAggregateRepository.findById(fundId)
                .orElseThrow(() -> new IllegalArgumentException("no fund aggregate for " + fundId));

        FactorType biggest = FactorType.SPOT;
        BigDecimal biggestAbs = agg.exposure(FactorType.SPOT).abs();
        for (FactorType f : FactorType.values()) {
            if (agg.exposure(f).abs().compareTo(biggestAbs) > 0) {
                biggest = f;
                biggestAbs = agg.exposure(f).abs();
            }
        }

        List<Trade> trades = tradeRepository.findByFundId(fundId);
        Trade topContributor = null;
        BigDecimal topAbs = BigDecimal.valueOf(-1);
        for (Trade t : trades) {
            BigDecimal abs = t.exposure(biggest).abs();
            if (abs.compareTo(topAbs) > 0) {
                topContributor = t;
                topAbs = abs;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Fund ").append(fundId).append(" carries a spot exposure of ")
                .append(fmt(agg.getSpotExposure())).append(", a vol exposure of ")
                .append(fmt(agg.getVolExposure())).append(", a skew exposure of ")
                .append(fmt(agg.getSkewExposure())).append(", a rate exposure of ")
                .append(fmt(agg.getRateExposure())).append(", and a dividend exposure of ")
                .append(fmt(agg.getDividendExposure())).append(" across its strategies.");

        if (topContributor != null) {
            sb.append(" The largest single contributor to ").append(biggest.name().toLowerCase(Locale.ROOT))
                    .append(" exposure within the fund is trade ").append(topContributor.getExternalId())
                    .append(" (strategy ").append(topContributor.getStrategyId())
                    .append("), carrying ").append(fmt(topContributor.exposure(biggest)))
                    .append(" of that ").append(biggest.name().toLowerCase(Locale.ROOT))
                    .append(" exposure on its own.");
        }
        return sb.toString();
    }

    private static String fmt(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
