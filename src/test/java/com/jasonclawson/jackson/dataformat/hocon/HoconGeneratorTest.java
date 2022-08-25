package com.jasonclawson.jackson.dataformat.hocon;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.Test;

public class HoconGeneratorTest {

    @Test
    public void testGenerator() throws IOException {
        ObjectMapper mapper = new ObjectMapper(new HoconFactory());
        Configuration.Lib lib = new Configuration.Lib("this is foo", "whatever this is");
        Configuration.Context context = new Configuration.Context(lib);
        Configuration configuration = new Configuration("This is something", context, 0.5f);
        String config = mapper.writeValueAsString(configuration);
        System.out.println(config);
    }

}
