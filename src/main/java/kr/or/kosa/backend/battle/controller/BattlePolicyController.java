package kr.or.kosa.backend.battle.controller;

import kr.or.kosa.backend.battle.dto.DurationPolicyResponse;
import kr.or.kosa.backend.battle.util.BattleDurationPolicy;
import kr.or.kosa.backend.commons.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/battle/policies")
@RequiredArgsConstructor
public class BattlePolicyController {

    private final BattleDurationPolicy battleDurationPolicy;

    @GetMapping("/duration")
    public ResponseEntity<ApiResponse<DurationPolicyResponse>> getDurationPolicy() {
        DurationPolicyResponse body = DurationPolicyResponse.fromPolicy(battleDurationPolicy);
        return ResponseEntity.ok(ApiResponse.success(body));
    }
}
