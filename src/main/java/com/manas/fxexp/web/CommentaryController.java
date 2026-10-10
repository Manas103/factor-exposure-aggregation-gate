package com.manas.fxexp.web;

import com.manas.fxexp.service.CommentaryPublishGate;
import com.manas.fxexp.service.CommentaryService;
import com.manas.fxexp.service.PublishVerdict;
import com.manas.fxexp.web.dto.CommentaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Generates a fund commentary and runs it through the recomputation gate
 * before returning it; `published=false` means the gate caught a mismatch
 * and the commentary was not approved to go out, with the exact mismatches
 * named. */
@RestController
public class CommentaryController {

    private final CommentaryService commentaryService;
    private final CommentaryPublishGate commentaryPublishGate;

    public CommentaryController(CommentaryService commentaryService, CommentaryPublishGate commentaryPublishGate) {
        this.commentaryService = commentaryService;
        this.commentaryPublishGate = commentaryPublishGate;
    }

    @GetMapping("/commentary/fund/{id}")
    public CommentaryResponse getFundCommentary(@PathVariable String id) {
        String text = commentaryService.generateFundCommentary(id);
        PublishVerdict verdict = commentaryPublishGate.verifyFundCommentary(id, text);
        return new CommentaryResponse(id, text, verdict.allowed(), verdict.mismatches());
    }
}
