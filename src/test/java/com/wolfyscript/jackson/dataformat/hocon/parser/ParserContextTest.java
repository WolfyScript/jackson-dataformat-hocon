package com.wolfyscript.jackson.dataformat.hocon.parser;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.wolfyscript.jackson.dataformat.hocon.HoconMapper;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;

public class ParserContextTest {

    @Test
    public void testCurrentValue() throws IOException {
        HoconMapper hoconMapper = new HoconMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Configuration.Context.class, new ContextDeserializer());
        module.addDeserializer(Configuration.Lib.class, new LibDeserializer());
        hoconMapper.registerModule(module);
        Configuration configuration = hoconMapper.readValue(HoconTreeTraversingParserTest.url("test.conf"), Configuration.class);
        HoconTreeTraversingParserTest.assertConf(configuration);
    }

    static class ContextDeserializer extends StdDeserializer<Configuration.Context> {

        public ContextDeserializer() {
            super(Configuration.Context.class);
        }

        @Override
        public Configuration.Context deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
            // Current value of Context is still null
            Assert.assertNull(p.currentValue());
            // This is the current field name
            Assert.assertEquals("context", p.currentName());
            // The parent object should at least be constructed already, even if it doesn't have all properties set yet.
            Object parentCurVal = p.getParsingContext().getParent().getCurrentValue();
            Assert.assertNotNull(parentCurVal);
            Assert.assertSame(Configuration.class, parentCurVal.getClass());

            Configuration.Context configContext = new Configuration.Context();
            configContext.lib = ctxt.readTreeAsValue(ctxt.readTree(p).get("lib"), Configuration.Lib.class);
            return configContext;
        }
    }

    static class LibDeserializer extends StdDeserializer<Configuration.Lib> {

        public LibDeserializer() {
            super(Configuration.Lib.class);
        }

        @Override
        public Configuration.Lib deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
            Assert.assertNull(p.currentName()); // Not possible as a custom deserializer is used for the parent.
            Assert.assertNull(p.currentValue()); // Shouldn't be set yet

            JsonNode values = ctxt.readTree(p);
            String foo = values.get("foo").asText();
            String whatever = values.get("whatever").asText();
            return new Configuration.Lib(foo, whatever);
        }
    }

}
