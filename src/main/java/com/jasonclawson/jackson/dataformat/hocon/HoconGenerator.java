package com.jasonclawson.jackson.dataformat.hocon;

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.StreamWriteCapability;
import com.fasterxml.jackson.core.base.GeneratorBase;
import com.fasterxml.jackson.core.io.CharTypes;
import com.fasterxml.jackson.core.io.CharacterEscapes;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.json.JsonWriteContext;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.JacksonFeatureSet;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.impl.ConfigImplUtil;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * This is an experimental implementation of a Hocon Generator<br>
 */
public class HoconGenerator extends GeneratorBase {

    /*
    /**********************************************************
    /* Constants
    /**********************************************************
     */

    /**
     * This is the default set of escape codes, over 7-bit ASCII range
     * (first 128 character codes), used for single-byte UTF-8 characters.
     */
    protected final static int[] sOutputEscapes = CharTypes.get7BitOutputEscapes();

    /**
     * Default capabilities for JSON generator implementations which do not
     * different from "general textual" defaults
     *
     * @since 2.12
     */
    protected final static JacksonFeatureSet<StreamWriteCapability> JSON_WRITE_CAPABILITIES = DEFAULT_TEXTUAL_WRITE_CAPABILITIES;

    protected final static String WRITE_ARRAY = "write an array";
    protected final static String WRITE_OBJECT = "write an object";

    /*
    /**********************************************************
    /* Configuration, basic I/O
    /**********************************************************
     */

    protected final IOContext _ioContext;

    /*
    /**********************************************************
    /* Configuration, output escaping
    /**********************************************************
     */

    /**
     * Currently active set of output escape code definitions (whether
     * and how to escape or not) for 7-bit ASCII range (first 128
     * character codes). Defined separately to make potentially
     * customizable
     */
    protected int[] _outputEscapes = sOutputEscapes;

    /**
     * Value between 128 (0x80) and 65535 (0xFFFF) that indicates highest
     * Unicode code point that will not need escaping; or 0 to indicate
     * that all characters can be represented without escaping.
     * Typically used to force escaping of some portion of character set;
     * for example to always escape non-ASCII characters (if value was 127).
     * <p>
     * NOTE: not all sub-classes make use of this setting.
     */
    protected int _maximumNonEscapedChar;

    /**
     * Definition of custom character escapes to use for generators created
     * by this factory, if any. If null, standard data format specific
     * escapes are used.
     */
    protected CharacterEscapes _characterEscapes;

    /**
     * Character used for quoting JSON Object property names
     * and String values.
     */
    protected char _quoteChar;

    /*
    /**********************************************************
    /* Configuration, other
    /**********************************************************
     */

    /**
     * Separator to use, if any, between root-level values.
     *
     * @since 2.1
     */
    protected SerializableString _rootValueSeparator
            = DefaultPrettyPrinter.DEFAULT_ROOT_VALUE_SEPARATOR;

    private ConfigRenderOptions options;
    protected Writer _writer;
    private int _previousVerifyStatus;

    public HoconGenerator(IOContext ctxt, char quoteChar, int jsonFeatures, ObjectCodec codec, Writer out) {
        super(jsonFeatures, codec);
        _ioContext = ctxt;
        _writer = out;
        _quoteChar = quoteChar;
        _previousVerifyStatus = -1;
        this.options = ConfigRenderOptions.defaults();
        _cfgPrettyPrinter = _constructDefaultPrettyPrinter();
        if (Feature.ESCAPE_NON_ASCII.enabledIn(jsonFeatures)) {
            // inlined `setHighestNonEscapedChar()`
            _maximumNonEscapedChar = 127;
        }
    }

    @Override
    public JsonGenerator setPrettyPrinter(PrettyPrinter pp) {
        if (pp == null) throw new IllegalArgumentException("Hocon requires a non null PrettyPrinter!");
        return super.setPrettyPrinter(pp);
    }

    @Override
    public JsonStreamContext getOutputContext() {
        return null;
    }

    @Override
    public void flush() throws IOException {
        _writer.flush();
    }

    @Override
    protected void _releaseBuffers() {
        // No buffers to release
    }

    @Override
    public JsonGenerator enable(Feature f) {
        return null;
    }

    @Override
    public JsonGenerator disable(Feature f) {
        return null;
    }

    @Override
    public int getFeatureMask() {
        return 0;
    }

    @Override
    public JsonGenerator useDefaultPrettyPrinter() {
        return null;
    }

    @Override
    public void writeStartArray() throws IOException {
        _verifyValueWrite(WRITE_ARRAY);
        _writeValueSeparator(false);
        _writeContext = _writeContext.createChildArrayContext();
        if (_cfgPrettyPrinter != null) {
            _cfgPrettyPrinter.writeStartArray(this);
        } else {
            _writer.write('[');
        }
    }

    @Override
    public void writeStartArray(Object forValue) throws IOException {
        _verifyValueWrite(WRITE_ARRAY);
        _writeValueSeparator(false);
        _writeContext = _writeContext.createChildArrayContext(forValue);
        if (_cfgPrettyPrinter != null) {
            _cfgPrettyPrinter.writeStartArray(this);
        } else {
            _writer.write('[');
        }
    }

    @Override
    public void writeEndArray() throws IOException {
        if (!_writeContext.inArray()) {
            _reportError("Current context not Array but " + _writeContext.typeDesc());
        }
        if (_cfgPrettyPrinter != null) {
            _cfgPrettyPrinter.writeEndArray(this, _writeContext.getEntryCount());
        } else {
            _writer.write(']');
        }
        _writeContext = _writeContext.clearAndGetParent();
    }

    @Override
    public void writeStartObject() throws IOException {
        _verifyValueWrite(WRITE_OBJECT);
        _writeValueSeparator(true);
        boolean outerBrackets = !_writeContext.inRoot() || options.getJson();
        _writeContext = _writeContext.createChildObjectContext();
        if (outerBrackets) { // Omit bracket when in root and json compatibility is disabled.
            if (_cfgPrettyPrinter != null) {
                _cfgPrettyPrinter.writeStartObject(this);
            } else {
                _writer.write('{');
            }
        }
    }

    @Override
    public void writeStartObject(Object forValue) throws IOException {
        _verifyValueWrite(WRITE_OBJECT);
        _writeValueSeparator(true);
        boolean outerBrackets = !_writeContext.inRoot() || options.getJson();
        _writeContext = _writeContext.createChildObjectContext(forValue);
        if (outerBrackets) {
            if (_cfgPrettyPrinter != null) {
                _cfgPrettyPrinter.writeStartObject(this);
            } else {
                _writer.write('{');
            }
        }
    }

    @Override
    public void writeEndObject() throws IOException {
        if (!_writeContext.inObject()) {
            _reportError("Current context not Object but " + _writeContext.typeDesc());
        }
        if (!_writeContext.inRoot() || options.getJson()) { // Omit bracket when in root and json compatibility is disabled.
            if (_cfgPrettyPrinter != null) {
                _cfgPrettyPrinter.writeEndObject(this, _writeContext.getEntryCount());
            } else {
                _writer.write('}');
            }
        }
        _writeContext = _writeContext.clearAndGetParent();
    }

    @Override
    public void writeFieldName(String name) throws IOException {
        if (_writeContext.writeFieldName(name) == JsonWriteContext.STATUS_EXPECT_VALUE) {
            _reportError("Cannot write a field name, expecting a value");
        }
        _writeString(name);
    }

    @Override
    public void writeString(String text) throws IOException {
        _verifyValueWrite(WRITE_STRING);
        _writeValueSeparator(false);
        _writeString(text);
    }

    @Override
    public void writeString(char[] buffer, int offset, int len) throws IOException {
        _verifyValueWrite(WRITE_STRING);
        _writeValueSeparator(false);
        _writer.write(buffer, offset, len);
    }

    /**
     * Internal method to write a String
     *
     * @param text the text to write
     * @throws IOException
     */
    protected void _writeString(String text) throws IOException {
        String renderedKey;
        if (options.getJson()) {
            renderedKey = ConfigImplUtil.renderJsonString(text);
        } else {
            renderedKey = _renderStringUnquotedIfPossible(text);
        }
        _writer.write(renderedKey);
    }

    /**
     * Renders the String unquoted if the options and value allow it.<br>
     * If not possible it redirects to the {@link ConfigImplUtil#renderJsonString(String)} function.<br>
     * <br>
     * This method was copied from the renderStringUnquotedIfPossible function in {@link ConfigImplUtil}!
     *
     * @param s The value to write.
     * @return The rendered String
     * @see ConfigImplUtil ConfigImplUtil#renderStringUnquotedIfPossible(String)
     */
    protected String _renderStringUnquotedIfPossible(String s) {
        if (s.length() == 0) {
            return ConfigImplUtil.renderJsonString(s);
        } else {
            int first = s.codePointAt(0);
            if (!Character.isDigit(first) && first != 45) {
                if (!s.startsWith("include") && !s.startsWith("true") && !s.startsWith("false") && !s.startsWith("null") && !s.contains("//")) {
                    for (int i = 0; i < s.length(); ++i) {
                        char c = s.charAt(i);
                        if (!Character.isLetter(c) && !Character.isDigit(c) && c != '-') {
                            return ConfigImplUtil.renderJsonString(s);
                        }
                    }
                    return s;
                } else {
                    return ConfigImplUtil.renderJsonString(s);
                }
            } else {
                return ConfigImplUtil.renderJsonString(s);
            }
        }
    }

    @Override
    public void writeNumber(int v) throws IOException {
        _verifyValueWrite(WRITE_NUMBER);
        _writeValueSeparator(false);
        writeRaw(String.valueOf(v));
    }

    @Override
    public void writeNumber(long v) throws IOException {
        _verifyValueWrite(WRITE_NUMBER);
        _writeValueSeparator(false);
        writeRaw(String.valueOf(v));
    }

    @Override
    public void writeNumber(BigInteger v) throws IOException {
        _verifyValueWrite(WRITE_NUMBER);
        _writeValueSeparator(false);
        writeRaw(String.valueOf(v));
    }

    @Override
    public void writeNumber(double v) throws IOException {
        _verifyValueWrite(WRITE_NUMBER);
        _writeValueSeparator(false);
        writeRaw(String.valueOf(v));
    }

    @Override
    public void writeNumber(float v) throws IOException {
        _verifyValueWrite(WRITE_NUMBER);
        _writeValueSeparator(false);
        writeRaw(String.valueOf(v));
    }

    @Override
    public void writeNumber(BigDecimal value) throws IOException {
        _verifyValueWrite(WRITE_NUMBER);
        _writeValueSeparator(false);
        if (value == null) {
            writeNull();
        } else {
            // BigDecimal isn't supported by the parser as is and is converted to double (See: ConfigImpl#fromAnyRef(Object object, ConfigOrigin origin,FromMapMode mapMode))
            writeNumber(value.doubleValue());
        }
    }

    @Override
    public void writeNumber(String encodedValue) throws IOException {
        _verifyValueWrite(WRITE_NUMBER);
        _writeValueSeparator(false);
        writeRaw(encodedValue);
    }

    @Override
    public void writeBoolean(boolean state) throws IOException {
        _verifyValueWrite(WRITE_BOOLEAN);
        _writeValueSeparator(false);
        _writer.write(String.valueOf(state));
    }

    @Override
    public void writeNull() throws IOException {
        _verifyValueWrite(WRITE_NULL);
        _writeValueSeparator(false);
        _writer.write("null");
    }

    @Override
    public void writeBinary(Base64Variant bv, byte[] data, int offset, int len) throws IOException {

    }

    @Override
    public void writeRaw(char c) throws IOException {
        _writer.write(c);
    }

    @Override
    public void writeRaw(String text) throws IOException {
        _writer.write(text);
    }

    @Override
    public void writeRaw(char[] text, int offset, int len) throws IOException {
        _writer.write(text, offset, len);
    }

    @Override
    public void writeRaw(String text, int offset, int len) throws IOException {
        _writer.write(text, offset, len);
    }

    @Override
    public void writeUTF8String(byte[] buffer, int offset, int len) throws IOException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRawUTF8String(byte[] buffer, int offset, int len) throws IOException {
        _reportUnsupportedOperation();
    }

    @Override
    protected void _verifyValueWrite(String typeMsg) throws IOException {
        final int status = _writeContext.writeValue();
        _previousVerifyStatus = status;
        if (_cfgPrettyPrinter != null) {
            switch (status) {
                case JsonWriteContext.STATUS_OK_AFTER_COMMA: // array
                case JsonWriteContext.STATUS_OK_AFTER_COLON:
                case JsonWriteContext.STATUS_OK_AFTER_SPACE:
                case JsonWriteContext.STATUS_OK_AS_IS:
                    break;
                case JsonWriteContext.STATUS_EXPECT_NAME:
                    _reportCantWriteValueExpectName(typeMsg);
                    break;
                default:
                    _throwInternal();
                    break;
            }
            return;
        }
        if (status == JsonWriteContext.STATUS_EXPECT_NAME) {
            _reportCantWriteValueExpectName(typeMsg);
        }
    }

    protected void _writeValueSeparator(boolean isObjectValue) throws IOException {
        if (_previousVerifyStatus == -1) {
            _throwInternal();
            return;
        }
        final int verifyStatus = _previousVerifyStatus;
        _previousVerifyStatus = -1;
        if (_cfgPrettyPrinter != null) {
            // Otherwise, pretty printer knows what to do...
            _writePPValueSeparatorFor(isObjectValue, verifyStatus);
            return;
        }
        switch (verifyStatus) {
            case JsonWriteContext.STATUS_OK_AS_IS:
            default:
                return;
            case JsonWriteContext.STATUS_OK_AFTER_COMMA:
                _writer.write(',');
                break;
            case JsonWriteContext.STATUS_OK_AFTER_COLON:
                if (!isObjectValue || options.getJson()) { // can be omitted for objects (only when json is disabled!)
                    _writer.write(':');
                }
                return; // Nothing to write otherwise
            case JsonWriteContext.STATUS_OK_AFTER_SPACE: // root-value separator
                if (_rootValueSeparator != null) {
                    writeRaw(_rootValueSeparator.getValue());
                }
        }
    }

    protected void _writePPValueSeparatorFor(boolean isObjectValue, int verifyStatus) throws IOException {
        switch (verifyStatus) {
            case JsonWriteContext.STATUS_OK_AFTER_COMMA: // array
                _cfgPrettyPrinter.writeArrayValueSeparator(this);
                break;
            case JsonWriteContext.STATUS_OK_AFTER_COLON:
                if (!isObjectValue || options.getJson()) { // can be omitted for objects (only when json is disabled!)
                    _cfgPrettyPrinter.writeObjectFieldValueSeparator(this);
                }
                break; // Nothing to write otherwise
            case JsonWriteContext.STATUS_OK_AFTER_SPACE:
                _cfgPrettyPrinter.writeRootValueSeparator(this);
                break;
            case JsonWriteContext.STATUS_OK_AS_IS:
                // First entry, but of which context?
                if (_writeContext.inArray()) {
                    _cfgPrettyPrinter.beforeArrayValues(this);
                } else if (_writeContext.inObject()) {
                    _cfgPrettyPrinter.beforeObjectEntries(this);
                }
                break;
            default: // nothing to write. Exceptions were already thrown on verify!
        }
    }

    protected void _reportCantWriteValueExpectName(String typeMsg) throws IOException {
        _reportError(String.format("Can not %s, expecting field name (context: %s)",
                typeMsg, _writeContext.typeDesc()));
    }
}
