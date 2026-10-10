package com.manas.fxexp.service;

import java.util.List;

/** allowed is true only if every number the gate found named a figure
 * and that figure matched the independently recomputed ground truth
 * exactly (after rounding to the same 2 decimal places the commentary
 * itself prints). mismatches names each one that did not, so a human (or
 * an audit trail) can see exactly which claimed number was wrong. */
public record PublishVerdict(boolean allowed, List<String> mismatches) {
}
