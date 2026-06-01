package manga.common;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

public class JsonMessageConverter extends AbstractHttpMessageConverter<Object> {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    public JsonMessageConverter() {
        super(new MediaType("application", "json", UTF_8), new MediaType("application", "*+json", UTF_8));
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return true;
    }

    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        throw new HttpMessageNotReadableException("JSON request bodies are not supported by this converter");
    }

    @Override
    protected void writeInternal(Object object, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        outputMessage.getHeaders().setContentType(new MediaType("application", "json", UTF_8));
        String json = toJson(object, new IdentityHashMap<Object, Boolean>());
        outputMessage.getBody().write(json.getBytes(UTF_8));
    }

    private String toJson(Object value, IdentityHashMap<Object, Boolean> seen) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String || value instanceof Character) {
            return quote(String.valueOf(value));
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Enum<?>) {
            return quote(((Enum<?>) value).name());
        }
        if (value instanceof java.sql.Date) {
            return quote(value.toString());
        }
        if (value instanceof Date) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            return quote(format.format((Date) value));
        }
        if (value instanceof Map<?, ?>) {
            return mapToJson((Map<?, ?>) value, seen);
        }
        if (value instanceof Iterable<?>) {
            return iterableToJson(((Iterable<?>) value).iterator(), seen);
        }
        if (value.getClass().isArray()) {
            return arrayToJson(value, seen);
        }

        return beanToJson(value, seen);
    }

    private String mapToJson(Map<?, ?> map, IdentityHashMap<Object, Boolean> seen) {
        if (seen.containsKey(map)) {
            return "null";
        }
        seen.put(map, Boolean.TRUE);
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(quote(String.valueOf(entry.getKey()))).append(':');
            json.append(toJson(entry.getValue(), seen));
        }
        json.append('}');
        seen.remove(map);
        return json.toString();
    }

    private String iterableToJson(Iterator<?> iterator, IdentityHashMap<Object, Boolean> seen) {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        while (iterator.hasNext()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(toJson(iterator.next(), seen));
        }
        json.append(']');
        return json.toString();
    }

    private String arrayToJson(Object array, IdentityHashMap<Object, Boolean> seen) {
        StringBuilder json = new StringBuilder("[");
        int length = Array.getLength(array);
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(toJson(Array.get(array, i), seen));
        }
        json.append(']');
        return json.toString();
    }

    private String beanToJson(Object bean, IdentityHashMap<Object, Boolean> seen) {
        if (seen.containsKey(bean)) {
            return "null";
        }
        seen.put(bean, Boolean.TRUE);

        List<BeanProperty> properties = new ArrayList<BeanProperty>();
        Method[] methods = bean.getClass().getMethods();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            if (method.getParameterTypes().length != 0 || Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            String name = propertyName(method);
            if (name == null || "class".equals(name)) {
                continue;
            }
            properties.add(new BeanProperty(name, method));
        }
        sortProperties(properties);

        StringBuilder json = new StringBuilder("{");
        for (int i = 0; i < properties.size(); i++) {
            BeanProperty property = properties.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append(quote(property.name)).append(':');
            try {
                json.append(toJson(property.method.invoke(bean), seen));
            } catch (Exception ex) {
                json.append("null");
            }
        }
        json.append('}');
        seen.remove(bean);
        return json.toString();
    }

    private String propertyName(Method method) {
        String name = method.getName();
        if (name.startsWith("get") && name.length() > 3) {
            return decapitalize(name.substring(3));
        }
        if (name.startsWith("is") && name.length() > 2
                && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
            return decapitalize(name.substring(2));
        }
        return null;
    }

    private String decapitalize(String value) {
        if (value.length() == 0) {
            return value;
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private void sortProperties(List<BeanProperty> properties) {
        for (int i = 1; i < properties.size(); i++) {
            BeanProperty current = properties.get(i);
            int j = i - 1;
            while (j >= 0 && properties.get(j).name.compareTo(current.name) > 0) {
                properties.set(j + 1, properties.get(j));
                j--;
            }
            properties.set(j + 1, current);
        }
    }

    private String quote(String value) {
        StringBuilder json = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\': json.append("\\\\"); break;
                case '"': json.append("\\\""); break;
                case '\b': json.append("\\b"); break;
                case '\f': json.append("\\f"); break;
                case '\n': json.append("\\n"); break;
                case '\r': json.append("\\r"); break;
                case '\t': json.append("\\t"); break;
                default:
                    if (ch < 32) {
                        String hex = Integer.toHexString(ch);
                        json.append("\\u");
                        for (int pad = hex.length(); pad < 4; pad++) {
                            json.append('0');
                        }
                        json.append(hex);
                    } else {
                        json.append(ch);
                    }
            }
        }
        json.append('"');
        return json.toString();
    }

    private static class BeanProperty {
        private final String name;
        private final Method method;

        private BeanProperty(String name, Method method) {
            this.name = name;
            this.method = method;
        }
    }
}