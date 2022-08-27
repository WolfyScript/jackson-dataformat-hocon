package com.jasonclawson.jackson.dataformat.hocon.generator;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jasonclawson.jackson.dataformat.hocon.Configuration;
import com.jasonclawson.jackson.dataformat.hocon.HoconFactory;
import com.jasonclawson.jackson.dataformat.hocon.HoconGenerator;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

public class HoconGeneratorTest {

    static URL url(String name) {
        return HoconGeneratorTest.class.getResource(name);
    }

    private static Configuration createConfiguration() {
        Configuration.Lib lib = new Configuration.Lib("this is foo", "whatever this is");
        Configuration.Context context = new Configuration.Context(lib);
        return new Configuration("This is something", context, 0.5f);
    }

    private static String getFileContent(URL url) throws IOException, URISyntaxException {
        return new String(Files.readAllBytes(Paths.get(url.toURI())));
    }

    @Test
    public void testFeatureOmitRootBrackets() throws IOException, URISyntaxException {
        ObjectMapper mapper = new ObjectMapper(new HoconFactory().enable(HoconGenerator.Feature.OMIT_ROOT_OBJECT_BRACKETS));
        String config = mapper.writer(new DefaultPrettyPrinter()).writeValueAsString(createConfiguration());
        Assert.assertEquals("", getFileContent(url("test_omit_root_brackets.conf")), config);
    }

    @Test
    public void testFeatureOmitObjectValue() throws IOException, URISyntaxException {
        ObjectMapper mapper = new ObjectMapper(new HoconFactory().enable(HoconGenerator.Feature.OMIT_OBJECT_VALUE_SEPARATOR));
        String config = mapper.writer(new DefaultPrettyPrinter()).writeValueAsString(createConfiguration());
        Assert.assertEquals("", getFileContent(url("test_omit_object_value_separator.conf")), config);
    }

    @Test
    public void testFeatureUnquoteText() throws IOException, URISyntaxException {
        ObjectMapper mapper = new ObjectMapper(new HoconFactory().enable(HoconGenerator.Feature.UNQUOTE_TEXT_IF_POSSIBLE));
        String config = mapper.writer(new DefaultPrettyPrinter()).writeValueAsString(createConfiguration());
        Assert.assertEquals("", getFileContent(url("test_unquote_text.conf")), config);
    }

    @Test
    public void testFeatureAll() throws IOException, URISyntaxException {
        ObjectMapper mapper = new ObjectMapper(new HoconFactory().enable(HoconGenerator.Feature.UNQUOTE_TEXT_IF_POSSIBLE).enable(HoconGenerator.Feature.OMIT_ROOT_OBJECT_BRACKETS).enable(HoconGenerator.Feature.OMIT_OBJECT_VALUE_SEPARATOR));
        String config = mapper.writer(new DefaultPrettyPrinter()).writeValueAsString(createConfiguration());
        Assert.assertEquals("", getFileContent(url("test_unquote_text.conf")), config);
    }

    @Test
    public void testFeatureUseEqualSign() throws IOException, URISyntaxException {
        ObjectMapper mapper = new ObjectMapper(new HoconFactory());
        String config = mapper.writer(new DefaultPrettyPrinter().withSeparators(Separators.createDefaultInstance().withObjectFieldValueSeparator('='))).writeValueAsString(createConfiguration());
        Assert.assertEquals("", getFileContent(url("test_equal_sign_separator.conf")), config);
    }

}
