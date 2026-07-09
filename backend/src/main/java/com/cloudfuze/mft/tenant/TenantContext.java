package com.cloudfuze.mft.tenant;

/**
 * Holds the current request's tenant id in a thread-local so Hibernate's
 * {@code @TenantId} discriminator and the audit log can scope every row.
 *
 * <p>The tenant is established once per request by {@code JwtAuthFilter} (from the token)
 * or explicitly during login (from the tenant slug), and cleared at the end of the request.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(String tenantId) {
        CURRENT.set(tenantId);
    }

    /** @return the current tenant id, or {@code null} if none is bound. */
    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
