package com.resumeiq.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * The signed-in caller, injected into a controller method parameter.
 *
 * <p>A meta-annotation over {@code @AuthenticationPrincipal} so controller signatures read
 * as domain code — {@code create(@CurrentUser AuthenticatedUser caller, ...)} — rather than
 * as framework plumbing, and so the day this project needs a different principal type there
 * is one annotation to change instead of every endpoint.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal
public @interface CurrentUser {
}
