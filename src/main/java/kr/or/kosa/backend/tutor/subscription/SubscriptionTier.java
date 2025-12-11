package kr.or.kosa.backend.tutor.subscription;

public enum SubscriptionTier {
    FREE,
    BASIC,
    PRO;

    public static SubscriptionTier fromPlanCode(String planCode) {
        if (planCode == null) {
            return FREE;
        }
        return switch (planCode.toUpperCase()) {
            case "BASIC" -> BASIC;
            case "PRO" -> PRO;
            default -> FREE;
        };
    }
}
