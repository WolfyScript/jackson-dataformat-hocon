package com.jasonclawson.jackson.dataformat.hocon;

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.FormatFeature;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.base.GeneratorBase;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.json.JsonWriteContext;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.typesafe.config.impl.ConfigImplUtil;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * This is an experimental implementation of a Hocon Generator<br>
 */
public class HoconGenerator extends GeneratorBase {

    public enum Feature implements FormatFeature {

        /**
         * Omits the separator before object start bracket.
         */
        OMIT_OBJECT_VALUE_SEPARATOR(false),
        /**
         * Omits the root object brackets.
         */
        OMIT_ROOT_OBJECT_BRACKETS(false),
        /**
         * Writes Field Names and String values unquoted if possible.
         */
        UNQUOTE_TEXT_IF_POSSIBLE(false);

        protected final boolean _defaultState;
        protected final int _mask;

        /**
         * Method that calculates bit set (flags) of all features that
         * are enabled by default.
         */
        public static int collectDefaults()
        {
            int flags = 0;
            for (Feature f : values()) {
                if (f.enabledByDefault()) {
                    flags |= f.getMask();
                }
            }
            return flags;
        }

        Feature(boolean defaultState) {
            _defaultState = defaultState;
            _mask = (1 << ordinal());
        }

        @Override
        public boolean enabledByDefault() { return _defaultState; }
        @Override
        public boolean enabledIn(int flags) { return (flags & _mask) != 0; }
        @Override
        public int getMask() { return _mask; }
    }

    /*
    /**********************************************************
    /* Constants
    /**********************************************************
     */

    protected final static String WRITE_ARRAY = "write an array";
    protected final static String WRITE_OBJECT = "write an object";

    /*
    /**********************************************************
    /* Configuration, basic I/O
    /**********************************************************
     */

    protected final IOContext _ioContext;
    protected Writer _writer;

    /**
     * Character used for quoting JSON Object property names
     * and String values.
     */
    protected char _quoteChar;

    /**
     * Character to separate field and values.
     * Can either be an equal sign or column.
     */
    protected char _fieldValueSeparator;

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
    protected SerializableString _rootValueSeparator = DefaultPrettyPrinter.DEFAULT_ROOT_VALUE_SEPARATOR;

    protected int _hoconFeatures;

    /*
    /**********************************************************************
    /* Output state
    /**********************************************************************
     */

    /**
     * Caches the last value write status from {@link #_verifyValueWrite(String)}.
     * Used to write the field-name/value separator and optionally omit it before object values.
     */
    private int _previousVerifyStatus;


    public HoconGenerator(IOContext ctxt, char quoteChar, int hoconFeatures, int jsonFeatures, ObjectCodec codec, Writer out) {
        super(jsonFeatures, codec);
        _ioContext = ctxt;
        _writer = out;
        _quoteChar = quoteChar;
        _previousVerifyStatus = -1;
        _hoconFeatures = hoconFeatures;
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
    public int getFormatFeatures() {
        return _hoconFeatures;
    }

    @Override
    public HoconGenerator overrideFormatFeatures(int values, int mask) {
        _hoconFeatures = (_hoconFeatures & ~mask) | (values & mask);
        return this;
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
        boolean outerBrackets = !_writeContext.inRoot() || !Feature.OMIT_ROOT_OBJECT_BRACKETS.enabledIn(_hoconFeatures);
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
        boolean outerBrackets = !_writeContext.inRoot() || !Feature.OMIT_ROOT_OBJECT_BRACKETS.enabledIn(_hoconFeatures);
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
        int entryCount = _writeContext.getEntryCount();
        _writeContext = _writeContext.clearAndGetParent();
        if (!_writeContext.inRoot() || !Feature.OMIT_ROOT_OBJECT_BRACKETS.enabledIn(_hoconFeatures)) { // Omit bracket when in root and json compatibility is disabled.
            if (_cfgPrettyPrinter != null) {
                _cfgPrettyPrinter.writeEndObject(this, entryCount);
            } else {
                _writer.write('}');
            }
        }
    }

    @Override
    public void writeFieldName(String name) throws IOException {
        int status = _writeContext.writeFieldName(name);
        if (status == JsonWriteContext.STATUS_EXPECT_VALUE) {
            _reportError("Cannot write a field name, expecting a value");
        }
        _writeFieldName(name, (status == JsonWriteContext.STATUS_OK_AFTER_COMMA));
    }

    @Override
    public void writeFieldName(SerializableString name) throws IOException {
        int status = _writeContext.writeFieldName(name.getValue());
        if (status == JsonWriteContext.STATUS_EXPECT_VALUE) {
            _reportError("Cannot write a field name, expecting a value");
        }
        _writeFieldName(name.getValue(), (status == JsonWriteContext.STATUS_OK_AFTER_COMMA));
    }

    protected final void _writeFieldName(String name, boolean commaBefore) throws IOException {
        if (_cfgPrettyPrinter != null) {
            if (commaBefore) {
                _cfgPrettyPrinter.writeObjectEntrySeparator(this);
            } else if (!_writeContext.inRoot() && _writeContext.inObject() && (!_writeContext.getParent().inRoot() || !Feature.OMIT_ROOT_OBJECT_BRACKETS.enabledIn(_hoconFeatures))) { // Omit indentation when it is an object, the parent is in root and the root brackets are omitted.
                _cfgPrettyPrinter.beforeObjectEntries(this);
            }
            _writeString(name);
            return;
        }
        if (commaBefore) {
            _writer.write(',');
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
        if (!Feature.UNQUOTE_TEXT_IF_POSSIBLE.enabledIn(_hoconFeatures)) {
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
        if (data == null) {
            writeNull();
            return;
        }
        _verifyValueWrite(WRITE_BINARY);
        _writeValueSeparator(false);
        if (offset > 0 || (offset+len) != data.length) {
            data = Arrays.copyOfRange(data, offset, offset+len);
        }
        _writeString(bv.encode(data));
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
        if (_previousVerifyStatus == -1) { // Make sure the verify method was called beforehand!
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
                if (!isObjectValue || !Feature.OMIT_OBJECT_VALUE_SEPARATOR.enabledIn(_hoconFeatures)) { // can be omitted for objects (only when json is disabled!)
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
                if (!isObjectValue || !Feature.OMIT_OBJECT_VALUE_SEPARATOR.enabledIn(_hoconFeatures)) { // can be omitted for objects (only when json is disabled!)
                    _cfgPrettyPrinter.writeObjectFieldValueSeparator(this);
                } else {
                    _writer.write(' ');
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
