package sam4c.light.model.rule;

public sealed interface SecurityRule
        permits Confidentiality, Integrity, Isolation, Authentication, Authorization {}
