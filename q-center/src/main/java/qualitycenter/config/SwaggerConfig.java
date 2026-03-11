package qualitycenter.config;

import org.springdoc.core.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class SwaggerConfig {

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("v1-definition")
                .pathsToMatch("/api/**")
                .build();
    }

    @Bean
    public OpenAPI openApiDefinition() {
        return new OpenAPI()
                .components(new Components())
                .info(new Info().title("Narae DataQ API")
                        .description("Narae DataQ 프로젝트 API 명세서")
                        .version("v0.0.1"));
    }
}
