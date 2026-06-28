package gov.brgy.ebms.audit.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    String entityType();
    String action();

    /**
     * The JPA entity class to look up for before-state capture on UPDATE/DELETE.
     * Leave as default (Void.class) for CREATE operations where no before-state exists.
     */
    Class<?> entityClass() default Void.class;

    /**
     * Zero-based index of the method argument that holds the entity ID.
     * Used together with {@link #entityClass()} to fetch the entity before mutation.
     */
    int entityIdArgIndex() default 0;
}
