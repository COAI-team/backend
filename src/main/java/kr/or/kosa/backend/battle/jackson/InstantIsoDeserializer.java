package kr.or.kosa.backend.battle.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.Instant;

/**
 * Deserializes ISO-8601 strings to {@link Instant} using {@link Instant#parse(CharSequence)}.
 */
public class InstantIsoDeserializer extends JsonDeserializer<Instant> {
    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getValueAsString();
        if (text == null || text.isBlank()) {
            return null;
        }
        return Instant.parse(text);
    }
}
