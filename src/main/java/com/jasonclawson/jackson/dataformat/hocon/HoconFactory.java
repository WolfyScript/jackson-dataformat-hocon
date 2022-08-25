package com.jasonclawson.jackson.dataformat.hocon;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.format.InputAccessor;
import com.fasterxml.jackson.core.format.MatchStrength;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.io.UTF8Writer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Files;

/**
 * This code was pretty much copied from the jackson YAMLFactory
 *
 * @author jclawson
 */
public class HoconFactory extends JsonFactory {
    private static final long serialVersionUID = 1L;

    public final static String FORMAT_NAME_HOCON = "HOCON";
    public final static char DEFAULT_QUOTE_CHAR = '"';

    private final static byte UTF8_BOM_1 = (byte) 0xEF;
    private final static byte UTF8_BOM_2 = (byte) 0xBB;
    private final static byte UTF8_BOM_3 = (byte) 0xBF;

    public HoconFactory() {
        this(null);
    }

    public HoconFactory(ObjectCodec oc) {
        super(oc);
    }

    public HoconFactory(HoconFactory src, ObjectCodec oc) {
        super(src, oc);
    }


    @Override
    public HoconFactory copy() {
        _checkInvalidCopy(HoconFactory.class);
        return new HoconFactory(this, null);
    }

    /*
    /**********************************************************
    /* Serializable overrides
    /**********************************************************
     */

    /**
     * Method that we need to override to actually make restoration go
     * through constructors etc.
     * Also: must be overridden by sub-classes as well.
     */
    @Override
    protected Object readResolve() {
        return new HoconFactory(this, _objectCodec);
    }

    /*
    /**********************************************************
    /* Versioned
    /**********************************************************
     */

//    @Override
//    public Version version() {
//        return PackageVersion.VERSION;
//    }

    /*
    /**********************************************************
    /* Format detection functionality (since 1.8)
    /**********************************************************
     */

    @Override
    public String getFormatName() {
        return FORMAT_NAME_HOCON;
    }

    /**
     * Sub-classes need to override this method (as of 1.8)
     */
    @Override
    public MatchStrength hasFormat(InputAccessor acc) throws IOException {
        if (!acc.hasMoreBytes()) {
            return MatchStrength.INCONCLUSIVE;
        }
        byte b = acc.nextByte();
        // Very first thing, a UTF-8 BOM?
        if (b == UTF8_BOM_1) { // yes, looks like UTF-8 BOM
            if (!acc.hasMoreBytes()) {
                return MatchStrength.INCONCLUSIVE;
            }
            if (acc.nextByte() != UTF8_BOM_2) {
                return MatchStrength.NO_MATCH;
            }
            if (!acc.hasMoreBytes()) {
                return MatchStrength.INCONCLUSIVE;
            }
            if (acc.nextByte() != UTF8_BOM_3) {
                return MatchStrength.NO_MATCH;
            }
            if (!acc.hasMoreBytes()) {
                return MatchStrength.INCONCLUSIVE;
            }
            b = acc.nextByte();
        }
        if (b == '{' || Character.isLetter((char) b) || Character.isDigit((char) b)) {
            return MatchStrength.WEAK_MATCH;
        }
        return MatchStrength.INCONCLUSIVE;
    }

    /*
    /**********************************************************
    /* Configuration, parser settings
    /**********************************************************
     */



    /*
    /**********************************************************
    /* Overridden parser factory methods (for 2.1)
    /**********************************************************
     */

    @SuppressWarnings("resource")
    @Override
    public HoconTreeTraversingParser createParser(String content) throws IOException, JsonParseException {
        Reader r = new StringReader(content);
        IOContext ctxt = _createContext(_createContentReference(r), true); // true->own, can close
        return _createParser(_decorate(r, ctxt), ctxt);
    }

    @SuppressWarnings("resource")
    @Override
    public HoconTreeTraversingParser createParser(File f) throws IOException, JsonParseException {
        // choosing to support hocon include instead of inputDecorator
        Config resolvedConfig = ConfigFactory.parseFile(f).resolve();
        return new HoconTreeTraversingParser(resolvedConfig.root(), _objectCodec);
    }

    @SuppressWarnings("resource")
    @Override
    public HoconTreeTraversingParser createParser(URL url) throws IOException, JsonParseException {
        // choosing to support hocon include instead of inputDecorator
        Config resolvedConfig = ConfigFactory.parseURL(url).resolve();
        return new HoconTreeTraversingParser(resolvedConfig.root(), _objectCodec);
    }

    @SuppressWarnings("resource")
    @Override
    public HoconTreeTraversingParser createParser(InputStream in) throws IOException, JsonParseException {
        IOContext ctxt = _createContext(_createContentReference(in), false);
        return _createParser(_decorate(in, ctxt), ctxt);
    }

    @SuppressWarnings("resource")
    @Override
    public JsonParser createParser(Reader r) throws IOException, JsonParseException {
        IOContext ctxt = _createContext(_createContentReference(r), false);
        return _createParser(_decorate(r, ctxt), ctxt);
    }

    @SuppressWarnings("resource")
    @Override
    public HoconTreeTraversingParser createParser(byte[] data) throws IOException, JsonParseException {
        IOContext ctxt = _createContext(_createContentReference(data), true);
        // [JACKSON-512]: allow wrapping with InputDecorator
        if (_inputDecorator != null) {
            InputStream in = _inputDecorator.decorate(ctxt, data, 0, data.length);
            if (in != null) {
                return _createParser(in, ctxt);
            }
        }
        return _createParser(data, 0, data.length, ctxt);
    }

    @SuppressWarnings("resource")
    @Override
    public HoconTreeTraversingParser createParser(byte[] data, int offset, int len) throws IOException, JsonParseException {
        IOContext ctxt = _createContext(_createContentReference(data, offset, len), true);
        // [JACKSON-512]: allow wrapping with InputDecorator
        if (_inputDecorator != null) {
            InputStream in = _inputDecorator.decorate(ctxt, data, offset, len);
            if (in != null) {
                return _createParser(in, ctxt);
            }
        }
        return _createParser(data, offset, len, ctxt);
    }

    /*
    /**********************************************************
    /* Overridden generator factory methods (2.1)
    /**********************************************************
     */

    @SuppressWarnings("resource")
    @Override
    public JsonGenerator createGenerator(OutputStream out, JsonEncoding enc) throws IOException {
        // false -> we won't manage the stream unless explicitly directed to
        IOContext ctxt = _createContext(_createContentReference(out), false);
        ctxt.setEncoding(enc);
        return _createGenerator(_createWriter(_decorate(out, ctxt), enc, ctxt), ctxt);
    }

    @SuppressWarnings("resource")
    @Override
    public JsonGenerator createGenerator(OutputStream out) throws IOException {
        // false -> we won't manage the stream unless explicitly directed to
        IOContext ctxt = _createContext(_createContentReference(out), false);
        return _createGenerator(_createWriter(_decorate(out, ctxt),
                JsonEncoding.UTF8, ctxt), ctxt);
    }

    @SuppressWarnings("resource")
    @Override
    public JsonGenerator createGenerator(Writer out) throws IOException {
        IOContext ctxt = _createContext(_createContentReference(out), false);
        return _createGenerator(_decorate(out, ctxt), ctxt);
    }

    @Override
    public JsonGenerator createGenerator(File f, JsonEncoding enc) throws IOException {
        OutputStream out = Files.newOutputStream(f.toPath());
        // true -> yes, we have to manage the stream since we created it
        IOContext ctxt = _createContext(_createContentReference(f), true);
        ctxt.setEncoding(enc);
        return _createGenerator(_createWriter(_decorate(out, ctxt), enc, ctxt), ctxt);
    }

    /*
    /******************************************************
    /* Overridden internal factory methods
    /******************************************************
     */

    //protected IOContext _createContext(Object srcRef, boolean resourceManaged)

    @SuppressWarnings("resource")
    @Override
    protected HoconTreeTraversingParser _createParser(InputStream in, IOContext ctxt) throws IOException, JsonParseException {
        Reader r = _createReader(in, null, ctxt);
        return _createParser(r, ctxt);
    }

    @Override
    protected HoconTreeTraversingParser _createParser(Reader r, IOContext ctxt) throws IOException, JsonParseException {
        Config resolvedConfig = ConfigFactory.parseReader(r).resolve();
        return new HoconTreeTraversingParser(resolvedConfig.root(), _objectCodec);
    }

    @SuppressWarnings("resource")
    @Override
    protected HoconTreeTraversingParser _createParser(byte[] data, int offset, int len, IOContext ctxt) throws IOException, JsonParseException {
        Reader r = _createReader(data, offset, len, null, ctxt);
        return _createParser(r, ctxt);
    }

    @Override
    protected HoconGenerator _createGenerator(Writer out, IOContext ctxt) throws IOException {
        return new HoconGenerator(ctxt, _quoteChar, _generatorFeatures, _objectCodec, out);
    }

    @SuppressWarnings("resource")
    @Deprecated
    @Override
    protected JsonGenerator _createUTF8Generator(OutputStream out, IOContext ctxt) throws IOException {
        // should never get called; ensure
        throw new IllegalStateException();
    }

    @Override
    protected Writer _createWriter(OutputStream out, JsonEncoding enc, IOContext ctxt) throws IOException {
        if (enc == JsonEncoding.UTF8) {
            return new UTF8Writer(ctxt, out);
        }
        return new OutputStreamWriter(out, enc.getJavaName());
    }
    
    /*
    /**********************************************************
    /* Internal methods
    /**********************************************************
     */

    protected Reader _createReader(InputStream in, JsonEncoding enc, IOContext ctxt) throws IOException {
        if (enc == null) {
            enc = JsonEncoding.UTF8;
        }
        // default to UTF-8 if encoding missing
        if (enc == JsonEncoding.UTF8) {
            boolean autoClose = ctxt.isResourceManaged() || isEnabled(JsonParser.Feature.AUTO_CLOSE_SOURCE);
            return new UTF8Reader(in, autoClose);
        }
        return new InputStreamReader(in, enc.getJavaName());
    }

    protected Reader _createReader(byte[] data, int offset, int len, JsonEncoding enc, IOContext ctxt) throws IOException {
        if (enc == null) {
            enc = JsonEncoding.UTF8;
        }
        // default to UTF-8 if encoding missing
        if (enc == null || enc == JsonEncoding.UTF8) {
            return new UTF8Reader(data, offset, len, true);
        }
        ByteArrayInputStream in = new ByteArrayInputStream(data, offset, len);
        return new InputStreamReader(in, enc.getJavaName());
    }
}

