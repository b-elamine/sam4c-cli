package sam4c.light.metamodel;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public record MPackage(String name, String nsURI, List<MClass> classes) {

    private Map<String, MClass> index() {
        return classes.stream().collect(Collectors.toMap(MClass::name, Function.identity()));
    }

    public Optional<MClass> find(String className) {
        return Optional.ofNullable(index().get(className));
    }

    public MClass require(String className) {
        return find(className).orElseThrow(() ->
                new IllegalArgumentException("Unknown class in metamodel: " + className));
    }

    public List<MClass> concreteClasses() {
        return classes.stream().filter(c -> !c.abstractClass()).toList();
    }

    public List<MClass> abstractClasses() {
        return classes.stream().filter(MClass::abstractClass).toList();
    }

    /** All supertype names of a class, transitively. */
    public List<String> allSuperTypes(String className) {
        MClass cls = require(className);
        java.util.List<String> result = new java.util.ArrayList<>();
        for (String superType : cls.superTypes()) {
            result.add(superType);
            result.addAll(allSuperTypes(superType));
        }
        return result;
    }

    /** All attributes of a class including inherited ones. */
    public List<MAttribute> allAttributes(String className) {
        java.util.List<MAttribute> result = new java.util.ArrayList<>(require(className).attributes());
        for (String superType : allSuperTypes(className))
            result.addAll(require(superType).attributes());
        return result;
    }

    /** All references of a class including inherited ones. */
    public List<MReference> allReferences(String className) {
        java.util.List<MReference> result = new java.util.ArrayList<>(require(className).references());
        for (String superType : allSuperTypes(className))
            result.addAll(require(superType).references());
        return result;
    }

    /** Checks if className is-a targetType (direct or via inheritance). */
    public boolean isA(String className, String targetType) {
        if (className.equals(targetType)) return true;
        return allSuperTypes(className).contains(targetType);
    }
}
