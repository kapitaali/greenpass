import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

public class ClassInspector {

    /**
     * Inspects a Java Class object and returns a string representation of its public API.
     * 
     * @param clazz The Class object to inspect (e.g., loaded via URLClassLoader).
     * @return A string containing class info, fields, constructors, and methods.
     */
    public static String inspect(Class<?> clazz) {
        if (clazz == null) return "Class is null.";

        StringBuilder sb = new StringBuilder();
        String lineSep = System.lineSeparator();

        // 1. Class Header
        sb.append("=== CLASS INSPECTION ===").append(lineSep);
        int mods = clazz.getModifiers();
        sb.append("Modifiers: ").append(Modifier.toString(mods)).append(lineSep);

        sb.append("Name: ").append(clazz.getName()).append(lineSep);
        
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null) {
            sb.append("Extends: ").append(superClass.getName()).append(lineSep);
        }

        Class<?>[] interfaces = clazz.getInterfaces();
        if (interfaces.length > 0) {
            sb.append("Implements: ");
            sb.append(Arrays.stream(interfaces)
                    .map(Class::getName)
                    .collect(Collectors.joining(", ")));
            sb.append(lineSep);
        }
        sb.append(lineSep);

        // 2. Fields (Public only)
        Field[] fields = clazz.getFields();
        if (fields.length > 0) {
            sb.append("--- PUBLIC FIELDS ---").append(lineSep);
            Arrays.sort(fields, Comparator.comparing(Field::getName));
            for (Field f : fields) {
                sb.append(formatModifiers(f.getModifiers()))
                  .append(f.getType().getTypeName()).append(" ")
                  .append(f.getName())
                  .append(lineSep);
            }
            sb.append(lineSep);
        }

        // 3. Constructors
        Constructor<?>[] constructors = clazz.getConstructors();
        if (constructors.length > 0) {
            sb.append("--- CONSTRUCTORS ---").append(lineSep);
            for (Constructor<?> c : constructors) {
                sb.append(formatModifiers(c.getModifiers()))
                  .append(clazz.getSimpleName()) // Use simple name for constructor
                  .append(formatParameters(c.getParameters()))
                  .append(lineSep);
            }
            sb.append(lineSep);
        }

        // 4. Methods
        // We use getMethods() to get public methods (including inherited ones), 
        // which is usually what you want when using a library.
        Method[] methods = clazz.getMethods();
        if (methods.length > 0) {
            sb.append("--- METHODS ---").append(lineSep);
            
            // Sort by name, then by parameter count to group overloads
            Arrays.sort(methods, Comparator.comparing(Method::getName)
                    .thenComparingInt(Method::getParameterCount));

            for (Method m : methods) {
                // Skip basic Object methods to reduce noise (optional)
                if (isCommonObjectMethod(m)) continue;

                sb.append(formatModifiers(m.getModifiers()))
                  .append(m.getReturnType().getTypeName()).append(" ")
                  .append(m.getName())
                  .append(formatParameters(m.getParameters()));
                
                // Show exception types if any
                Class<?>[] exceptions = m.getExceptionTypes();
                if (exceptions.length > 0) {
                    sb.append(" throws ")
                      .append(Arrays.stream(exceptions)
                              .map(Class::getSimpleName)
                              .collect(Collectors.joining(", ")));
                }
                sb.append(lineSep);
            }
        }

        return sb.toString();
    }

    // --- Helpers ---

    private static String formatModifiers(int modifiers) {
        String s = Modifier.toString(modifiers);
        return s.isEmpty() ? "" : s + " ";
    }

    private static String formatParameters(Parameter[] params) {
        if (params.length == 0) return "()";
        return "(" + Arrays.stream(params)
                .map(p -> p.getType().getTypeName()) // Use TypeName to handle Arrays/Generics nicely
                .collect(Collectors.joining(", ")) + ")";
    }

    // Optional: Filter out standard Object methods that clutter the view
    private static boolean isCommonObjectMethod(Method m) {
        String name = m.getName();
        return (name.equals("wait") || name.equals("notify") || 
                name.equals("notifyAll") || name.equals("getClass"));
    }
}
