package com.vijaypurohit.movietickets.shared.pagination;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PaginationConfiguration {

    @Bean
    CursorCodec cursorCodec(@Value("${app.pagination.cursor-signing-key}") String signingKey) {
        return new HmacCursorCodec(signingKey);
    }
}
