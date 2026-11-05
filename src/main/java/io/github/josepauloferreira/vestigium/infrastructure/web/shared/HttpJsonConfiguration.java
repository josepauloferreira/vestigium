package io.github.josepauloferreira.vestigium.infrastructure.web.shared;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.type.LogicalType;

@Configuration(proxyBeanMethods = false)
class HttpJsonConfiguration {

  @Bean
  JsonMapperBuilderCustomizer strictInputTypes() {
    return builder ->
        builder
            .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .withCoercionConfig(
                LogicalType.Textual,
                coercion ->
                    coercion
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
            .withCoercionConfig(
                LogicalType.Float,
                coercion -> coercion.setCoercion(CoercionInputShape.String, CoercionAction.Fail));
  }
}
