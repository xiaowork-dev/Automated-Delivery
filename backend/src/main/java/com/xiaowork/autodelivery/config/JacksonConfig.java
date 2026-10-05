package com.xiaowork.autodelivery.config;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
@Configuration
public class JacksonConfig {
    @Bean public Jackson2ObjectMapperBuilderCustomizer deliveryTimeCustomizer() {
        return builder -> builder.serializerByType(LocalDateTime.class,new JsonSerializer<LocalDateTime>() {
            @Override public void serialize(LocalDateTime value,JsonGenerator gen,SerializerProvider serializers)throws IOException { gen.writeString(value.atOffset(ZoneOffset.ofHours(8)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)); }
        }).deserializerByType(LocalDateTime.class,new JsonDeserializer<LocalDateTime>() {
            @Override public LocalDateTime deserialize(JsonParser parser,DeserializationContext context)throws IOException {
                String value=parser.getText();
                try { return OffsetDateTime.parse(value).atZoneSameInstant(ZoneOffset.ofHours(8)).toLocalDateTime(); }
                catch(java.time.format.DateTimeParseException ex) { return LocalDateTime.parse(value); }
            }
        });
    }
}
