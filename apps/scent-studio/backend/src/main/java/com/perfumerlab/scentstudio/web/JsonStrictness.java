package com.perfumerlab.scentstudio.web;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/** 같은 이름 칸이 두 번 오거나, 글자 칸에 숫자·참거짓을 보내면 글자로 바꿔 받지 않고 400 (예: 노트 key 자리에 1). 모양이 틀린 요청을 조용히 받아 주지 않게. */
@Configuration
public class JsonStrictness {

    @Bean
    JsonMapperBuilderCustomizer noScalarToText() {
        return b -> b.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION) // 같은 이름 칸 두 번 → 400 (리뷰 묶음 4 QA-7)
                .withCoercionConfig(LogicalType.Textual, c -> c
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }
}
