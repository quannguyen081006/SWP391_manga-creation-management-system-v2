package manga.common.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RoleCombinationValidator {

    private static final Set<String> DUAL_ROLE_ALLOWED = new HashSet<String>(
            Arrays.asList("TANTOU_EDITOR", "EDITORIAL_BOARD"));

    private RoleCombinationValidator() {
    }

    public static void validate(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("Select at least one role");
        }
        List<String> distinct = new ArrayList<String>();
        for (String role : roles) {
            if (role == null || role.trim().isEmpty()) {
                continue;
            }
            String normalized = role.trim().toUpperCase();
            if (!distinct.contains(normalized)) {
                distinct.add(normalized);
            }
        }
        if (distinct.isEmpty()) {
            throw new IllegalArgumentException("Select at least one role");
        }
        if (distinct.size() <= 1) {
            return;
        }
        if (distinct.contains("MANGAKA") || distinct.contains("ASSISTANT")) {
            throw new IllegalArgumentException(
                    "Mangaka and Assistant accounts must have a single role only");
        }
        if (distinct.contains("ADMIN")) {
            throw new IllegalArgumentException("ADMIN cannot be combined with other roles");
        }
        if (distinct.size() > 2) {
            throw new IllegalArgumentException(
                    "Only Tantou Editor and Editorial Board can hold dual roles");
        }
        for (String role : distinct) {
            if (!DUAL_ROLE_ALLOWED.contains(role)) {
                throw new IllegalArgumentException(
                        "Only Tantou Editor and Editorial Board can hold dual roles");
            }
        }
        if (!distinct.contains("TANTOU_EDITOR") || !distinct.contains("EDITORIAL_BOARD")) {
            throw new IllegalArgumentException(
                    "Only Tantou Editor and Editorial Board can hold dual roles");
        }
    }
}
