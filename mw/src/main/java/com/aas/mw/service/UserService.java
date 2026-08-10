package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.config.AppRole;
import com.aas.mw.config.RoleResolver;
import com.aas.mw.dto.FieldsRequest;
import com.aas.mw.dto.UserCreateRequest;
import feign.FeignException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

    private static final String ADMINISTRATOR = "Administrator";

    /**
     * Party links on the ERPNext User doc. The stock User doctype has no supplier/customer
     * field, so writing those keys was silently discarded — these custom fields carry the
     * mapping the User Settings page and vendor/branch workflows expect.
     */
    public static final String SUPPLIER_FIELD = "aas_supplier";
    public static final String CUSTOMER_FIELD = "aas_customer";

    private final ErpNextClient erpNextClient;
    private final UserFeatureService userFeatureService;
    private final RoleResolver roleResolver;
    private final UserAliasService userAliasService;

    public UserService(
            ErpNextClient erpNextClient,
            UserFeatureService userFeatureService,
            RoleResolver roleResolver,
            UserAliasService userAliasService) {
        this.erpNextClient = erpNextClient;
        this.userFeatureService = userFeatureService;
        this.roleResolver = roleResolver;
        this.userAliasService = userAliasService;
    }

    public Map<String, Object> getUserProfile(String username) {
        return getUserProfileById(username);
    }

    public Map<String, Object> getUserProfileById(String userId) {
        if (userId == null || userId.isBlank()) {
            return Map.of("name", "", "full_name", "", "email", "");
        }
        Map<String, Object> user = unwrapResource(erpNextClient.getResource("User", userId));
        if (user == null || user.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
        }
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getOrDefault("name", userId));
        profile.put("name", user.getOrDefault("name", userId));
        profile.put("full_name", user.getOrDefault("full_name", userId));
        profile.put("email", user.getOrDefault("email", ""));
        profile.put("mobile_no", user.getOrDefault("mobile_no", ""));
        profile.put("location", user.getOrDefault("location", ""));
        // Prefer the AAS custom fields; the stock keys are read as a fallback only so that
        // any doc predating the custom fields still surfaces whatever it happens to carry.
        profile.put("customer", firstNonBlank(user.get(CUSTOMER_FIELD), user.get("customer")));
        profile.put("supplier", firstNonBlank(user.get(SUPPLIER_FIELD), user.get("supplier")));
        profile.put("company", user.getOrDefault("default_company", ""));
        profile.put("enabled", user.getOrDefault("enabled", 1));
        profile.put("user_image", user.getOrDefault("user_image", ""));
        List<String> erpRoles = parseRoles(user.get("roles"));
        String role = resolveAppRole(erpRoles, userId, user.get(AppRole.FIELD));
        List<String> allowFeatures = userFeatureService.parseFeatureOverrides(user.get(UserFeatureService.FEATURE_ALLOW_FIELD));
        List<String> denyFeatures = userFeatureService.parseFeatureOverrides(user.get(UserFeatureService.FEATURE_DENY_FIELD));
        profile.put("role", role);
        profile.put("erp_roles", erpRoles);
        profile.put("allow_features", allowFeatures);
        profile.put("deny_features", denyFeatures);
        profile.put("default_features", userFeatureService.featuresForRole(role));
        profile.put("features", userFeatureService.resolveFeatures(role, allowFeatures, denyFeatures));
        return profile;
    }

    public List<Map<String, Object>> listUserAccessProfiles() {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\"]");
        params.put("limit_page_length", 200);
        params.put("order_by", "full_name asc");
        List<Map<String, Object>> rows = erpNextClient.listResources("User", params);
        List<Map<String, Object>> profiles = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            String name = String.valueOf(row.getOrDefault("name", "")).trim();
            if (name.isBlank() || "Guest".equalsIgnoreCase(name)) {
                continue;
            }
            profiles.add(getUserProfileById(name));
        }
        return profiles;
    }

    /**
     * Creates an ERPNext User carrying the ERP roles that map back to the requested app role,
     * plus the feature allow/deny overrides. The payload mirrors {@code SetupService.ensureUser}
     * so seeded and admin-created users are shaped identically.
     */
    public Map<String, Object> createUser(UserCreateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User details are required.");
        }
        AppRole appRole = parseAppRole(request.getRole());
        String email = trimmed(request.getEmail()).toLowerCase(Locale.ROOT);
        if (email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required.");
        }
        String supplier = trimmed(request.getSupplier());
        String customer = trimmed(request.getCustomer());
        if (appRole == AppRole.VENDOR && supplier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A vendor user must be linked to a supplier.");
        }
        if (appRole == AppRole.SHOP && customer.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A branch user must be linked to a customer.");
        }
        if (resourceExists("User", email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A user with this email already exists.");
        }

        String fullName = trimmed(request.getFullName());
        String resolvedName = fullName.isBlank() ? email : fullName;
        String firstName = resolvedName;
        String lastName = "";
        int space = resolvedName.indexOf(' ');
        if (space > 0) {
            firstName = resolvedName.substring(0, space).trim();
            lastName = resolvedName.substring(space + 1).trim();
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("email", email);
        payload.put("first_name", firstName);
        if (!lastName.isBlank()) {
            payload.put("last_name", lastName);
        }
        payload.put("username", deriveUsername(email, firstName));
        payload.put("enabled", 1);
        payload.put("send_welcome_email", 0);
        payload.put("new_password", request.getPassword());
        payload.put("user_type", "System User");
        payload.put("mobile_no", trimmed(request.getMobileNo()));
        payload.put("location", trimmed(request.getLocation()));
        payload.put("default_company", trimmed(request.getCompany()));
        payload.put(SUPPLIER_FIELD, supplier);
        payload.put(CUSTOMER_FIELD, customer);
        payload.put("roles", roleResolver.erpRolesFor(appRole).stream()
                .map(role -> Map.of("role", role))
                .toList());
        // Authoritative for login: a Website User cannot read their own roles child table,
        // so role must be recorded on a field their own session can see.
        payload.put(AppRole.FIELD, appRole.asKey());
        payload.put(
                UserFeatureService.FEATURE_ALLOW_FIELD,
                userFeatureService.encodeFeatureOverrides(request.getAllowFeatures()));
        payload.put(
                UserFeatureService.FEATURE_DENY_FIELD,
                userFeatureService.encodeFeatureOverrides(request.getDenyFeatures()));

        erpNextClient.createResource("User", payload);
        // Refresh the alias cache so the new user can sign in by username without a restart.
        userAliasService.refreshFromPrivilegedSession();
        return getUserProfileById(email);
    }

    public Map<String, Object> setUserEnabled(String userId, boolean enabled, String actingUserId) {
        String id = trimmed(userId);
        if (id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User id is required.");
        }
        if (!enabled) {
            if (ADMINISTRATOR.equalsIgnoreCase(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The Administrator account cannot be disabled.");
            }
            if (id.equalsIgnoreCase(trimmed(actingUserId))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot disable your own account.");
            }
        }
        erpNextClient.updateResource("User", id, Map.of("enabled", enabled ? 1 : 0));
        return getUserProfileById(id);
    }

    public Map<String, Object> updateUserAccess(String userId, List<String> allowFeatures, List<String> denyFeatures) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User id is required.");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put(UserFeatureService.FEATURE_ALLOW_FIELD, userFeatureService.encodeFeatureOverrides(allowFeatures));
        payload.put(UserFeatureService.FEATURE_DENY_FIELD, userFeatureService.encodeFeatureOverrides(denyFeatures));
        erpNextClient.updateResource("User", userId, payload);
        return getUserProfileById(userId);
    }

    public Map<String, Object> updateUserProfile(String userId, FieldsRequest request) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User id is required.");
        }
        Map<String, Object> fields = request == null || request.getFields() == null ? Map.of() : request.getFields();
        Map<String, Object> payload = new HashMap<>();
        copyIfPresent(fields, payload, "full_name");
        copyIfPresent(fields, payload, "mobile_no");
        copyIfPresent(fields, payload, "location");
        copyIfPresent(fields, payload, "default_company");
        if (fields.containsKey("company")) {
            payload.put("default_company", fields.get("company"));
        }
        // The request keys stay "customer"/"supplier" for the UI; they land on the AAS fields.
        if (fields.containsKey("customer")) {
            payload.put(CUSTOMER_FIELD, fields.get("customer"));
        }
        if (fields.containsKey("supplier")) {
            payload.put(SUPPLIER_FIELD, fields.get("supplier"));
        }
        if (!payload.isEmpty()) {
            erpNextClient.updateResource("User", userId, payload);
        }
        return getUserProfileById(userId);
    }

    public boolean hasFeature(String username, String featureKey) {
        if (featureKey == null || featureKey.isBlank()) {
            return true;
        }
        Object value = getUserProfile(username).get("features");
        if (!(value instanceof java.util.Collection<?> features)) {
            return false;
        }
        return features.stream().map(String::valueOf).anyMatch(featureKey::equals);
    }

    public void requireFeature(String username, String featureKey) {
        if (!hasFeature(username, featureKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapResource(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return response;
    }

    private List<String> parseRoles(Object rawRoles) {
        if (!(rawRoles instanceof List<?> roles)) {
            return List.of();
        }
        List<String> parsed = new ArrayList<>();
        for (Object value : roles) {
            if (value instanceof Map<?, ?> roleMap) {
                Object role = roleMap.get("role");
                if (role != null) {
                    parsed.add(String.valueOf(role));
                }
            }
        }
        return List.copyOf(parsed);
    }

    /**
     * Prefers the recorded {@link AppRole#FIELD} so this agrees with what login resolves;
     * falls back to deriving from ERP roles for users provisioned before the field existed.
     */
    private String resolveAppRole(List<String> erpRoles, String userId, Object storedRole) {
        String stored = trimmed(storedRole == null ? "" : storedRole.toString());
        if (!stored.isBlank()) {
            try {
                return AppRole.valueOf(stored.toUpperCase(Locale.ROOT)).asKey();
            } catch (IllegalArgumentException ignored) {
                // Unrecognised value on the doc; fall back to the ERP roles below.
            }
        }
        try {
            return roleResolver.resolve(erpRoles).asKey();
        } catch (IllegalStateException ignored) {
            return ADMINISTRATOR.equalsIgnoreCase(userId) ? AppRole.ADMIN.asKey() : "";
        }
    }

    private AppRole parseAppRole(String role) {
        String value = trimmed(role);
        if (value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is required.");
        }
        try {
            return AppRole.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported role: " + role);
        }
    }

    /**
     * ERPNext usernames must be unique, so fall back to the email local part when the first
     * name is already taken (two "Ravi"s would otherwise collide on the second create).
     */
    private String deriveUsername(String email, String firstName) {
        String candidate = trimmed(firstName);
        int at = email.indexOf('@');
        String localPart = at > 0 ? email.substring(0, at) : email;
        if (candidate.isBlank() || usernameTaken(candidate)) {
            candidate = localPart;
        }
        return usernameTaken(candidate) ? "" : candidate;
    }

    private boolean usernameTaken(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\"]");
        params.put("filters", "[[\"User\",\"username\",\"=\",\"" + username + "\"]]");
        params.put("limit_page_length", 1);
        try {
            return !erpNextClient.listResources("User", params).isEmpty();
        } catch (RuntimeException ignored) {
            // If the lookup fails, let ERPNext be the authority on uniqueness.
            return false;
        }
    }

    private boolean resourceExists(String doctype, String name) {
        try {
            Map<String, Object> data = erpNextClient.getResource(doctype, name);
            return data != null && !data.isEmpty();
        } catch (FeignException.NotFound ignored) {
            return false;
        }
    }

    private String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(Object... values) {
        for (Object value : values) {
            String text = value == null ? "" : value.toString().trim();
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }
}
