package kr.or.kosa.backend.tutor.subscription;

public interface SubscriptionTierResolver {

    SubscriptionTier resolveTier(String userId);
}
