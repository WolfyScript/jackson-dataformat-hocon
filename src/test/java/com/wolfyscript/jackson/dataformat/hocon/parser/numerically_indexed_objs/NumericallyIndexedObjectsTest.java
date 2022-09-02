package com.wolfyscript.jackson.dataformat.hocon.parser.numerically_indexed_objs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.wolfyscript.jackson.dataformat.hocon.HoconMapper;
import com.wolfyscript.jackson.dataformat.hocon.parser.Configuration;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;

public class NumericallyIndexedObjectsTest {

    static URL url(String name) {
        return NumericallyIndexedObjectsTest.class.getResource(name);
    }

    static InputStream stream(String name) throws IOException {
        return url(name).openStream();
    }

    private static Reader reader(InputStream is) throws IOException {
        return new InputStreamReader(is, StandardCharsets.UTF_8);
    }

    private static String string(InputStream is) {
        Scanner s = new Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    @Test
    public void testMultiElementInvalidValuesObject() throws IOException {
        HoconMapper mapper = new HoconMapper();
        Map<String, Object> objectMap = mapper.readValue(stream("multi-element-invalid-values.conf"), new TypeReference<Map<String, Object>>() {});
        Map<String, Object> expected = new HashMap<>();
        expected.put("2", 0);
        expected.put("4", "f");
        expected.put("3", 0.7);
        expected.put("1", "test");
        expected.put("0", true);
        Assert.assertEquals(expected, objectMap);
    }

    @Test
    public void testMultiElementInvalidKeysObject() throws IOException {
        HoconMapper mapper = new HoconMapper();
        Map<String, Object> objectMap = mapper.readValue(stream("integer-invalid-keys.conf"), new TypeReference<Map<String, Object>>() {});
        Map<String, Object> expected = new HashMap<>();
        expected.put("bar", null);
        expected.put("4", 7);
        expected.put("foo", "bar");
        expected.put("2", 5);
        expected.put("0", 9);
        expected.put("1", 8);
        Assert.assertEquals(expected, objectMap);
    }

    /*
    /**********************************************************
    /* Conversions from Object to Collection
    /**********************************************************
     */

    @Test
    public void testSingleElementCollection() throws IOException {
        HoconMapper mapper = new HoconMapper();

        assertCollection(mapper, "boolean-single-element.conf", new TypeReference<List<Boolean>>() {}, true);
        assertCollection(mapper, "char-single-element.conf", new TypeReference<List<Character>>() {}, 'f');

        // Integer values
        assertCollection(mapper, "integer-single-element.conf", new TypeReference<List<Integer>>() {}, 9);
        assertCollection(mapper, "integer-single-element.conf", new TypeReference<List<Long>>() {}, 9L);
        assertCollection(mapper, "integer-single-element.conf", new TypeReference<List<Short>>() {}, (short) 9);
        assertCollection(mapper, "integer-single-element.conf", new TypeReference<List<Byte>>() {}, (byte) 9);

        // Floating point values
        assertCollection(mapper, "float-single-element.conf", new TypeReference<List<Float>>() {}, 9.4f);
        assertCollection(mapper, "float-single-element.conf", new TypeReference<List<Double>>() {}, 9.4d);
    }

    @SafeVarargs
    private final <T> void assertCollection(HoconMapper mapper, String file, TypeReference<List<T>> typeReference, T... values) throws IOException {
        // TypeReference is required as a parameter so that Jackson knows the actual type!
        List<T> actual = mapper.readValue(stream(file), typeReference);
        Assert.assertEquals(values.length, actual.size());
        MatcherAssert.assertThat(actual, CoreMatchers.hasItems(values));
    }

    /*
    /**********************************************************
    /* Conversions from Object to Array
    /**********************************************************
     */

    @Test
    public void testBooleanArrays() throws IOException {
        HoconMapper mapper = new HoconMapper();

        Assert.assertArrayEquals(new boolean[]{ true }, mapper.readValue(stream("boolean-single-element.conf"), boolean[].class));
        Assert.assertArrayEquals(new boolean[]{ true, false, false, true }, mapper.readValue(stream("boolean-multi-element.conf"), boolean[].class));
    }

    @Test
    public void testByteArrays() throws IOException {
        HoconMapper mapper = new HoconMapper();

        Assert.assertArrayEquals(new byte[]{ 127 }, mapper.readValue("{ 0 = 127 }", byte[].class));
        Assert.assertArrayEquals(new byte[]{ 127, 10, 120, 70 }, mapper.readValue("{ 0 = 127, 2 = 120, 1 = 10, 4 = 70 }", byte[].class));
        Assert.assertArrayEquals(new byte[]{ 127, 10, 120, 70 }, mapper.readValue("{ 0 = 127, foo = bar, 2 = 120, bar = null, 1 = 10, 4 = 70 }", byte[].class));

        Assert.assertThrows(InvalidFormatException.class, () -> mapper.readValue("{ 0 = true, 1 = test, 2 = 0, 3 = 0.7, 4 = f }", byte[].class));
    }

    @Test
    public void testIntArrays() throws IOException {
        HoconMapper mapper = new HoconMapper();

        Assert.assertArrayEquals(new int[]{ Integer.MAX_VALUE }, mapper.readValue("{ 0 = 2147483647 }", int[].class));
        Assert.assertArrayEquals(new int[]{ 9, 8, 5, 7 }, mapper.readValue("{ 0 = 9, 2 = 5, 1 = 8, 4 = 7 }", int[].class));
        Assert.assertArrayEquals(new int[]{ 9, 8, 5, 7 }, mapper.readValue("{ 0 = 9, foo = bar, 2 = 5, bar = null, 1 = 8, 4 = 7 }", int[].class));

        Assert.assertThrows(InvalidFormatException.class, () -> mapper.readValue("{ 0 = true, 1 = test, 2 = 0, 3 = 0.7, 4 = f }", int[].class));
    }

    @Test
    public void testShortArrays() throws IOException {
        HoconMapper mapper = new HoconMapper();

        Assert.assertArrayEquals(new short[]{ Short.MAX_VALUE }, mapper.readValue("{ 0 = 32767 }", short[].class));
        Assert.assertArrayEquals(new short[]{ 9, 8, 5, 7 }, mapper.readValue("{ 0 = 9, 2 = 5, 1 = 8, 4 = 7 }", short[].class));
        Assert.assertArrayEquals(new short[]{ 9, 8, 5, 7 }, mapper.readValue("{ 0 = 9, foo = bar, 2 = 5, bar = null, 1 = 8, 4 = 7 }", short[].class));

        Assert.assertThrows(InvalidFormatException.class, () -> mapper.readValue("{ 0 = true, 1 = test, 2 = 0, 3 = 0.7, 4 = f }", short[].class));
    }

    @Test
    public void testLongArrays() throws IOException {
        HoconMapper mapper = new HoconMapper();

        Assert.assertArrayEquals(new long[]{ Long.MAX_VALUE }, mapper.readValue("{ 0 = 9223372036854775807 }", long[].class));
        Assert.assertArrayEquals(new long[]{ 9, 8, 5, 7 }, mapper.readValue("{ 0 = 9, 2 = 5, 1 = 8, 4 = 7 }", long[].class));
        Assert.assertArrayEquals(new long[]{ 9, 8, 5, 7 }, mapper.readValue("{ 0 = 9, foo = bar, 2 = 5, bar = null, 1 = 8, 4 = 7 }", long[].class));

        Assert.assertThrows(InvalidFormatException.class, () -> mapper.readValue("{ 0 = true, 1 = test, 2 = 0, 3 = 0.7, 4 = f }", long[].class));
    }

    /*
    /**********************************************************
    /* Float Arrays
    /**********************************************************
     */

    @Test
    public void testFloatArrays() throws IOException {
        HoconMapper mapper = new HoconMapper();

        Assert.assertArrayEquals(new float[]{ 9.43333f }, mapper.readValue("{ 0 = 9.43333 }", float[].class), 0f);
        Assert.assertArrayEquals(new float[]{ 9, 8, 5, 7 }, mapper.readValue("{ 0 = 9, 2 = 5, 1 = 8, 4 = 7 }", float[].class), 0f);
        Assert.assertArrayEquals(new float[]{ 9, 8.45f, 5, 7 }, mapper.readValue("{ 0 = 9, foo = bar, 2 = 5, bar = null, 1 = 8.45, 4 = 7 }", float[].class), 0f);

        Assert.assertThrows(InvalidFormatException.class, () -> mapper.readValue("{ 0 = true, 1 = test, 2 = 0, 3 = 0.7, 4 = f }", float[].class));
    }

    /*
    /**********************************************************
    /* Double Arrays
    /**********************************************************
     */

    @Test
    public void testDoubleArrays() throws IOException {
        HoconMapper mapper = new HoconMapper();

        Assert.assertArrayEquals(new double[]{ 9.4d }, mapper.readValue("{ 0 = 9.4 }", double[].class), 0d);
        Assert.assertArrayEquals(new double[]{ 9, 8, 5, 7 }, mapper.readValue("{ 0 = 9, 2 = 5, 1 = 8, 4 = 7 }", double[].class), 0d);
        Assert.assertArrayEquals(new double[]{ 9, 8.45d, 5, 7 }, mapper.readValue("{ 0 = 9, foo = bar, 2 = 5, bar = null, 1 = 8.45, 4 = 7 }", double[].class), 0f);

        Assert.assertThrows(InvalidFormatException.class, () -> mapper.readValue("{ 0 = true, 1 = test, 2 = 0, 3 = 0.7, 4 = f }", double[].class));
    }

    /*
    /**********************************************************
    /* Char Arrays
    /**********************************************************
     */

    @Test
    public void testCharArrays() throws IOException {
        HoconMapper mapper = new HoconMapper();

        Assert.assertArrayEquals(new char[]{ 'f' }, mapper.readValue(stream("char-single-element.conf"), char[].class));
        Assert.assertArrayEquals(new char[]{ 'f', 'o', 'o', ',', 'b', 'a', 'r' }, mapper.readValue(stream("char-multi-element.conf"), char[].class));
    }

    /*
    /**********************************************************
    /* String Arrays
    /**********************************************************
     */

    @Test
    public void testStringSingleElement() throws IOException {
        HoconMapper mapper = new HoconMapper();
        String[] stringArray = mapper.readValue(stream("string-single-element.conf"), String[].class);
        Assert.assertArrayEquals(new String[]{ "abc" }, stringArray);
    }

    /*
    /**********************************************************
    /* Object Arrays
    /**********************************************************
     */

    @Test
    public void testObjectSingleElement() throws IOException {
        HoconMapper mapper = new HoconMapper();
        Configuration[] configurations = mapper.readValue(stream("object-single-element.conf"), Configuration[].class);
        Configuration[] expected = new Configuration[] {
                new Configuration("something2", new Configuration.Context(new Configuration.Lib("foofoo", "randomString")), 6.9f)
        };
        Assert.assertArrayEquals(expected, configurations);
    }

}
