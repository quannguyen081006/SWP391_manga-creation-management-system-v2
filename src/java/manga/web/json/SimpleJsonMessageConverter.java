package manga.web.json;

import manga.common.ApiResponse;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.StreamUtils;

public class SimpleJsonMessageConverter extends AbstractHttpMessageConverter<Object> {

    public SimpleJsonMessageConverter() {
        super(new MediaType("application", "json", Charset.forName("UTF-8")));
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return ApiResponse.class.isAssignableFrom(clazz);
    }

    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        throw new HttpMessageNotReadableException("JSON request body reading is not supported");
    }

    @Override
    protected void writeInternal(Object value, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        outputMessage.getHeaders().setContentType(new MediaType("application", "json", Charset.forName("UTF-8")));
        StreamUtils.copy(toJson(value), Charset.forName("UTF-8"), outputMessage.getBody());
    }

    private String toJson(Object value) {
        StringBuilder builder = new StringBuilder();
        appendJson(builder, value, new IdentityHashMap<Object, Boolean>());
        return builder.toString();
    }

    private void appendJson(StringBuilder builder, Object value, IdentityHashMap<Object, Boolean> seen) {
        if (value == null) {
            builder.append("null");
            return;
        }

        if (value instanceof String || value instanceof Character || value instanceof Enum<?>) {
            appendString(builder, String.valueOf(value));
            return;
        }
        if (value instanceof Number) {
            appendNumber(builder, (Number) value);
            return;
        }
        if (value instanceof Boolean) {
            builder.append(value);
            return;
        }
        if (value instanceof Timestamp || value instanceof Date || value instanceof java.util.Date) {
            appendString(builder, String.valueOf(value));
            return;
        }
        if (value instanceof Map<?, ?>) {
            appendMap(builder, (Map<?, ?>) value, seen);
            return;
        }
        if (value instanceof Collection<?>) {
            appendCollection(builder, ((Collection<?>) value).iterator(), seen);
            return;
        }
        if (value.getClass().isArray()) {
            appendArray(builder, value, seen);
            return;
        }

        appendBean(builder, value, seen);
    }

    private void appendNumber(StringBuilder builder, Number number) {
        if (number instanceof Double && (((Double) number).isInfinite() || ((Double) number).isNaN())) {
            builder.append("null");
        } else if (number instanceof Float && (((Float) number).isInfinite() || ((Float) number).isNaN())) {
            builder.append("null");
        } else if (number instanceof BigDecimal || number instanceof BigInteger
                || number instanceof Byte || number instanceof Short || number instanceof Integer
                || number instanceof Long || number instanceof Float || number instanceof Double) {
            builder.append(number);
        } else {
            appendString(builder, String.valueOf(number));
        }
    }

    private void appendMap(StringBuilder builder, Map<?, ?> map, IdentityHashMap<Object, Boolean> seen) {
        if (seen.containsKey(map)) {
            builder.append("null");
            return;
        }
        seen.put(map, Boolean.TRUE);
        builder.append("{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                builder.append(",");
            }
            appendString(builder, String.valueOf(entry.getKey()));
            builder.append(":");
            appendJson(builder, entry.getValue(), seen);
            first = false;
        }
        builder.append("}");
        seen.remove(map);
    }

    private void appendCollection(StringBuilder builder, Iterator<?> iterator, IdentityHashMap<Object, Boolean> seen) {
        builder.append("[");
        boolean first = true;
        while (iterator.hasNext()) {
            if (!first) {
                builder.append(",");
            }
            appendJson(builder, iterator.next(), seen);
            first = false;
        }
        builder.append("]");
    }

    private void appendArray(StringBuilder builder, Object array, IdentityHashMap<Object, Boolean> seen) {
        builder.append("[");
        int length = Array.getLength(array);
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                builder.append(",");
            }
            appendJson(builder, Array.get(array, i), seen);
        }
        builder.append("]");
    }

    private void appendBean(StringBuilder builder, Object bean, IdentityHashMap<Object, Boolean> seen) {
        if (seen.containsKey(bean)) {
            builder.append("null");
            return;
        }
        seen.put(bean, Boolean.TRUE);

        Map<String, Method> getters = new TreeMap<String, Method>();
        Method[] methods = bean.getClass().getMethods();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            if (!Modifier.isPublic(method.getModifiers()) || method.getParameterTypes().length != 0) {
                continue;
            }
            String name = propertyName(method);
            if (name != null && !"class".equals(name)) {
                getters.put(name, method);
            }
        }

        builder.append("{");
        boolean first = true;
        for (Map.Entry<String, Method> entry : getters.entrySet()) {
            try {
                Object propertyValue = entry.getValue().invoke(bean);
                if (!first) {
                    builder.append(",");
                }
                appendString(builder, entry.getKey());
                builder.append(":");
                appendJson(builder, propertyValue, seen);
                first = false;
            } catch (Exception ignored) {
                // Skip properties that cannot be read cleanly.
            }
        }
        builder.append("}");
        seen.remove(bean);
    }

    private String propertyName(Method method) {
        String name = method.getName();
        if (name.startsWith("get") && name.length() > 3 && method.getReturnType() != Void.TYPE) {
            return decapitalize(name.substring(3));
        }
        if (name.startsWith("is") && name.length() > 2
                && (method.getReturnType() == Boolean.TYPE || method.getReturnType() == Boolean.class)) {
            return decapitalize(name.substring(2));
        }
        return null;
    }

    private String decapitalize(String value) {
        if (value == null || value.length() == 0) {
            return value;
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private void appendString(StringBuilder builder, String value) {
        builder.append("\"");
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"':
                    builder.append("\\\"");
                    break;
                case '\\':
                    builder.append("\\\\");
                    break;
                case '\b':
                    builder.append("\\b");
                    break;
                case '\f':
                    builder.append("\\f");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    if (ch < 32) {
                        String hex = Integer.toHexString(ch);
                        builder.append("\\u");
                        for (int j = hex.length(); j < 4; j++) {
                            builder.append("0");
                        }
                        builder.append(hex);
                    } else {
                        builder.append(ch);
                    }
                    break;
            }
        }
        builder.append("\"");
    }
}
