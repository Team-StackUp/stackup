package com.stackup.stackup.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.stackup.stackup",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    private static final String BASE_PACKAGE = "com.stackup.stackup";
    private static final String COMMON_SLICE = "common";

    @ArchTest
    static final ArchRule domain_layer_does_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..application..",
                    "..presentation..",
                    "..infrastructure.."
            );

    @ArchTest
    static final ArchRule application_layer_does_not_depend_on_presentation = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..presentation..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule presentation_layer_does_not_use_jpa_repositories_directly = noClasses()
            .that().resideInAPackage("..presentation..")
            .should().dependOnClassesThat().areAssignableTo(JpaRepository.class)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule entities_reside_in_domain_packages = classes()
            .that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage("..domain..");

    @ArchTest
    static final ArchRule entities_do_not_have_public_setters = classes()
            .that().areAnnotatedWith(Entity.class)
            .should(notHavePublicSetters());

    @ArchTest
    static final ArchRule transactional_classes_are_only_in_application_layer = classes()
            .that().areAnnotatedWith(Transactional.class)
            .should().resideInAPackage("..application..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule transactional_methods_are_only_in_application_layer = methods()
            .that().areAnnotatedWith(Transactional.class)
            .should().beDeclaredInClassesThat().resideInAPackage("..application..")
            .allowEmptyShould(true);

    @ArchTest
    static void top_level_domain_slices_are_free_of_cycles(JavaClasses importedClasses) {
        Map<String, Set<String>> dependencies = collectTopLevelDependencies(importedClasses);
        Optional<List<String>> cycle = findCycle(dependencies);

        if (cycle.isPresent()) {
            throw new AssertionError("Cycle between top-level domains found: "
                    + String.join(" -> ", cycle.get()));
        }
    }

    private static ArchCondition<JavaClass> notHavePublicSetters() {
        return new ArchCondition<>("not have public setters") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                item.getMethods().stream()
                        .filter(method -> method.getModifiers().stream()
                                .anyMatch(modifier -> modifier.name().equals("PUBLIC")))
                        .filter(method -> method.getName().matches("set[A-Z].*"))
                        .forEach(method -> events.add(SimpleConditionEvent.violated(
                                method,
                                item.getName() + " has public setter " + method.getName()
                        )));
            }
        };
    }

    private static Map<String, Set<String>> collectTopLevelDependencies(JavaClasses importedClasses) {
        Map<String, Set<String>> dependencies = new LinkedHashMap<>();

        for (JavaClass javaClass : importedClasses) {
            topLevelSlice(javaClass).ifPresent(slice -> dependencies.putIfAbsent(slice, new LinkedHashSet<>()));

            for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                Optional<String> origin = topLevelSlice(dependency.getOriginClass());
                Optional<String> target = topLevelSlice(dependency.getTargetClass());

                if (origin.isEmpty() || target.isEmpty() || origin.equals(target)) {
                    continue;
                }

                dependencies.computeIfAbsent(origin.get(), ignored -> new LinkedHashSet<>()).add(target.get());
            }
        }

        return dependencies;
    }

    private static Optional<String> topLevelSlice(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        if (!packageName.startsWith(BASE_PACKAGE + ".")) {
            return Optional.empty();
        }

        String remainder = packageName.substring((BASE_PACKAGE + ".").length());
        String slice = remainder.split("\\.")[0];
        if (slice.isBlank() || COMMON_SLICE.equals(slice)) {
            return Optional.empty();
        }

        return Optional.of(slice);
    }

    private static Optional<List<String>> findCycle(Map<String, Set<String>> dependencies) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> path = new ArrayDeque<>();

        for (String slice : dependencies.keySet()) {
            Optional<List<String>> cycle = findCycle(slice, dependencies, visiting, visited, path);
            if (cycle.isPresent()) {
                return cycle;
            }
        }

        return Optional.empty();
    }

    private static Optional<List<String>> findCycle(
            String slice,
            Map<String, Set<String>> dependencies,
            Set<String> visiting,
            Set<String> visited,
            ArrayDeque<String> path
    ) {
        if (visited.contains(slice)) {
            return Optional.empty();
        }

        if (visiting.contains(slice)) {
            return Optional.of(cyclePath(path, slice));
        }

        visiting.add(slice);
        path.addLast(slice);

        for (String dependency : dependencies.getOrDefault(slice, Set.of())) {
            Optional<List<String>> cycle = findCycle(dependency, dependencies, visiting, visited, path);
            if (cycle.isPresent()) {
                return cycle;
            }
        }

        path.removeLast();
        visiting.remove(slice);
        visited.add(slice);
        return Optional.empty();
    }

    private static List<String> cyclePath(ArrayDeque<String> path, String repeatedSlice) {
        List<String> cycle = new ArrayList<>(path)
                .stream()
                .dropWhile(slice -> !slice.equals(repeatedSlice))
                .collect(Collectors.toCollection(ArrayList::new));
        cycle.add(repeatedSlice);
        return cycle;
    }
}
