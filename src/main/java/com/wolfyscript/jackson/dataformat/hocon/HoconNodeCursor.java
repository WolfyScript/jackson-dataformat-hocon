package com.wolfyscript.jackson.dataformat.hocon;

import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.core.JsonToken;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import java.util.Iterator;
import java.util.Map;

public abstract class HoconNodeCursor extends JsonStreamContext {

    /**
     * Parent cursor of this cursor, if any; null for root
     * cursors.
     */
    protected final HoconNodeCursor _parent;

    /**
     * Current field name
     */
    protected String _currentName;

    protected java.lang.Object _currentValue;

    public HoconNodeCursor(int contextType, HoconNodeCursor p) {
        super(contextType, -1);
        _parent = p;
    }

    /*
    /**********************************************************
    /* JsonStreamContext impl
    /**********************************************************
     */

    @Override
    public HoconNodeCursor getParent() {
        return _parent;
    }

    @Override
    public String getCurrentName() {
        return _currentName;
    }

    public void overrideCurrentName(String name) {
        _currentName = name;
    }

    @Override
    public void setCurrentValue(java.lang.Object v) {
        System.out.println("Set " + constructPath() + " >> " + (v == null ? null : v.getClass()));
        this._currentValue = v;
    }

    @Override
    public java.lang.Object getCurrentValue() {
        return _currentValue;
    }

    /*
    /**********************************************************
    /* Extended API
    /**********************************************************
     */

    /**
     * HOCON specific method to construct the path for this node. Useful for
     * interacting directly with the underlying Config instance in custom
     * deserializers.
     *
     * @return The path of this node cursor.
     */
    public String constructPath() {
        return constructPath(new StringBuilder()).toString();
    }

    private StringBuilder constructPath(StringBuilder initial) {
        if (_parent != null) {
            return _parent.constructPath(initial).append('.').append(_currentName);
        } else {
            return initial.append(_currentName);
        }
    }

    public abstract JsonToken nextToken();

    public abstract ConfigValue currentNode();

    public abstract HoconNodeCursor startObject();

    public abstract HoconNodeCursor startArray();

    protected static boolean isArray(ConfigValue value) {
        return value.valueType() == ConfigValueType.LIST;
    }

    protected static boolean isObject(ConfigValue value) {
        return value.valueType() == ConfigValueType.OBJECT;
    }

    protected static JsonToken asJsonToken(ConfigValue value) {
        return HoconTreeTraversingParser.asJsonToken(value);
    }

    /**
     * Method called to create a new context for iterating all
     * contents of the current structured value (JSON array or object)
     *
     * @return a cursor over the children of this node
     */
    public final HoconNodeCursor iterateChildren() {
        ConfigValue n = currentNode();
        if (n == null) throw new IllegalStateException("No current node");
        if (isArray(n)) { // false since we have already returned START_ARRAY
            return new Array(n, this);
        }
        if (isObject(n)) {
            return new Object(n, this);
        }
        throw new IllegalStateException("Current node of type " + n.getClass().getName());
    }

    /**
     * Context for all root-level value nodes (including Arrays and Objects):
     * only context for scalar values.
     */
    protected final static class RootValue extends HoconNodeCursor {
        protected ConfigValue _node;

        protected boolean _done = false;

        public RootValue(ConfigValue n, HoconNodeCursor p) {
            super(JsonStreamContext.TYPE_ROOT, p);
            _node = n;
        }

        @Override
        public void overrideCurrentName(String name) {
            // Cannot change name of root value
        }

        @Override
        public JsonToken nextToken() {
            if (!_done) {
                ++_index;
                _done = true;
                return asJsonToken(_node);
            }
            _node = null;
            return null;
        }

        @Override
        public ConfigValue currentNode() {
            // May look weird, but is necessary so as not to expose current node
            // before it has been traversed
            return _done ? _node : null;
        }

        @Override
        public HoconNodeCursor startObject() {
            return new Object(_node, this);
        }

        @Override
        public HoconNodeCursor startArray() {
            return new Array(_node, this);
        }

    }

    /**
     * Cursor used for traversing non-empty JSON Array nodes
     */
    protected final static class Array extends HoconNodeCursor {
        protected Iterator<ConfigValue> _contents;

        protected ConfigValue _currentNode;

        public Array(ConfigValue n, HoconNodeCursor p) {
            super(JsonStreamContext.TYPE_ARRAY, p);
            _contents = ((ConfigList) n).iterator();
        }

        @Override
        public JsonToken nextToken() {
            if (!_contents.hasNext()) {
                _currentNode = null;
                return JsonToken.END_ARRAY;
            }
            ++_index;
            _currentNode = _contents.next();
            return asJsonToken(_currentNode);
        }

        @Override
        public ConfigValue currentNode() {
            return _currentNode;
        }

        @Override
        public HoconNodeCursor startObject() {
            return new Object(_currentNode, this);
        }

        @Override
        public HoconNodeCursor startArray() {
            return new Array(_currentNode, this);
        }

    }

    /**
     * Cursor used for traversing non-empty JSON Object nodes
     */
    protected final static class Object extends HoconNodeCursor {
        protected Iterator<Map.Entry<String, ConfigValue>> _contents;
        protected Map.Entry<String, ConfigValue> _current;

        protected boolean _needEntry;

        public Object(ConfigValue n, HoconNodeCursor p) {
            super(JsonStreamContext.TYPE_OBJECT, p);
            _contents = ((ConfigObject) n).entrySet().iterator();
            _needEntry = true;
        }

        @Override
        public JsonToken nextToken() {
            // Need a new entry?
            if (_needEntry) {
                if (!_contents.hasNext()) {
                    _currentName = null;
                    _current = null;
                    return JsonToken.END_OBJECT;
                }
                ++_index;
                _needEntry = false;
                _current = _contents.next();
                _currentName = (_current == null) ? null : _current.getKey();
                return JsonToken.FIELD_NAME;
            }
            _needEntry = true;
            return asJsonToken(_current.getValue());
        }

        @Override
        public ConfigValue currentNode() {
            return (_current == null) ? null : _current.getValue();
        }

        @Override
        public HoconNodeCursor startObject() {
            return new Object(currentNode(), this);
        }

        @Override
        public HoconNodeCursor startArray() {
            return new Array(currentNode(), this);
        }

    }

}
