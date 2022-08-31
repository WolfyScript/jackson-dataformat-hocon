package com.wolfyscript.jackson.dataformat.hocon.parser.numerically_indexed_objs;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.wolfyscript.jackson.dataformat.hocon.HoconMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
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

    /*
    /**********************************************************
    /* Boolean Arrays
    /**********************************************************
     */

    @Test
    public void testBooleanSingleElement() throws IOException {
        HoconMapper mapper = new HoconMapper();
        // Can use the int file here since the values are not above the max byte value
        boolean[] stringArray = mapper.readValue(stream("boolean-single-element.conf"), boolean[].class);
        Assert.assertArrayEquals(new boolean[]{ true }, stringArray);
    }

    @Test
    public void testBooleanMultiElement() throws IOException {
        HoconMapper mapper = new HoconMapper();
        // Can use the int file here since the values are not above the max byte value
        boolean[] stringArray = mapper.readValue(stream("boolean-multi-element.conf"), boolean[].class);
        Assert.assertArrayEquals(new boolean[]{ true, false, false, true }, stringArray);
    }

    /*
    /**********************************************************
    /* Byte Arrays
    /**********************************************************
     */

    @Test
    public void testByteSingleElement() throws IOException {
        HoconMapper mapper = new HoconMapper();
        // Can use the int file here since the values are not above the max byte value
        byte[] stringArray = mapper.readValue(stream("int-single-element.conf"), byte[].class);
        Assert.assertArrayEquals(new byte[]{ 9 }, stringArray);
    }

    /*
    /**********************************************************
    /* Integer Arrays
    /**********************************************************
     */

    @Test
    public void testIntSingleElement() throws IOException {
        HoconMapper mapper = new HoconMapper();
        int[] stringArray = mapper.readValue(stream("int-single-element.conf"), int[].class);
        Assert.assertArrayEquals(new int[]{ 9 }, stringArray);
    }

    @Test
    public void testIntMultiElement() throws IOException {
        HoconMapper mapper = new HoconMapper();
        int[] stringArray = mapper.readValue(stream("int-multi-element.conf"), int[].class);
        Assert.assertArrayEquals(new int[]{ 9, 8, 5, 7 }, stringArray);
    }

    @Test
    public void testIntMultiElementInvalidKeys() throws IOException {
        HoconMapper mapper = new HoconMapper();
        int[] stringArray = mapper.readValue(stream("int-multi-element-invalid-keys.conf"), int[].class);
        Assert.assertArrayEquals(new int[]{ 9, 8, 5, 7 }, stringArray);
    }

    @Test
    public void testIntMultiElementInvalidValues() {
        HoconMapper mapper = new HoconMapper();
        Assert.assertThrows(InvalidFormatException.class, () -> mapper.readValue(stream("multi-element-invalid-values.conf"), int[].class));
    }

    /*
    /**********************************************************
    /* Short Arrays
    /**********************************************************
     */

    @Test
    public void testShortSingleElement() throws IOException {
        HoconMapper mapper = new HoconMapper();
        short[] stringArray = mapper.readValue(stream("short-single-element.conf"), short[].class);
        Assert.assertArrayEquals(new short[]{ 9 }, stringArray);
    }

    /*
    /**********************************************************
    /* Long Arrays
    /**********************************************************
     */

    @Test
    public void testLongSingleElement() throws IOException {
        HoconMapper mapper = new HoconMapper();
        long[] stringArray = mapper.readValue(stream("long-single-element.conf"), long[].class);
        Assert.assertArrayEquals(new long[]{ 9 }, stringArray);
    }

    /*
    /**********************************************************
    /* Float Arrays
    /**********************************************************
     */

    @Test
    public void testFloatSingleElement() throws IOException {
        HoconMapper mapper = new HoconMapper();
        float[] stringArray = mapper.readValue(stream("float-single-element.conf"), float[].class);
        Assert.assertArrayEquals(new float[]{ 9.4f }, stringArray, 0f);
    }

    /*
    /**********************************************************
    /* Double Arrays
    /**********************************************************
     */

    @Test
    public void testDoubleSingleElement() throws IOException {
        HoconMapper mapper = new HoconMapper();
        double[] stringArray = mapper.readValue(stream("double-single-element.conf"), double[].class);
        Assert.assertArrayEquals(new double[]{ 9.4d }, stringArray, 0f);
    }

    /*
    /**********************************************************
    /* Char Arrays
    /**********************************************************
     */

    @Test
    public void testCharSingleElement() throws IOException {
        HoconMapper mapper = new HoconMapper();
        char[] stringArray = mapper.readValue(stream("char-single-element.conf"), char[].class);
        Assert.assertArrayEquals(new char[]{ 'f' }, stringArray);
    }

    @Test
    public void testCharMultiElement() throws IOException {
        HoconMapper mapper = new HoconMapper();
        char[] stringArray = mapper.readValue(stream("char-multi-element.conf"), char[].class);
        Assert.assertArrayEquals(new char[]{ 'f', 'o', 'o', ',', 'b', 'a', 'r' }, stringArray);
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

}
