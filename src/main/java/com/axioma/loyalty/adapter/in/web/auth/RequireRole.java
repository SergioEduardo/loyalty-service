package com.axioma.loyalty.adapter.in.web.auth;

import com.axioma.loyalty.domain.model.UserRole;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exige un rol en un endpoint de staff (método o controlador). OWNER cumple cualquier rol.
 * Lo aplica StaffAuthFilter, solo en rutas de staff (/api/staff/**, /api/auth/me).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequireRole {
    UserRole value();
}
