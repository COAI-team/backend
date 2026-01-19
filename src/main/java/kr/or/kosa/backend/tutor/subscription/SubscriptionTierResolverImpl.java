package kr.or.kosa.backend.tutor.subscription;

import kr.or.kosa.backend.pay.repository.SubscriptionMapper;
import kr.or.kosa.backend.pay.entity.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionTierResolverImpl implements SubscriptionTierResolver {

    private final SubscriptionMapper subscriptionMapper;

    @Override
    public SubscriptionTier resolveTier(String userId) {
        log.debug("🔍 Resolving subscription tier for userId={}", userId);

        Long userIdLong = parseUserId(userId);
        if (userIdLong == null) {
            log.warn("❌ Invalid userId format: {}", userId);
            return SubscriptionTier.FREE;
        }

        try {
            LocalDateTime now = LocalDateTime.now();

            return subscriptionMapper.findActiveSubscriptionsByUserId(userIdLong)
                    .stream()
                    .filter(subscription -> subscription != null && isWithinActivePeriod(subscription, now))
                    .sorted(Comparator.comparing(
                                    Subscription::getEndDate,
                                    Comparator.nullsLast(Comparator.naturalOrder()))
                            .reversed())
                    .map(subscription -> SubscriptionTier.fromPlanCode(subscription.getSubscriptionType()))
                    .reduce(SubscriptionTier.FREE, this::preferHigherTier);

        } catch (Exception e) {
            log.error("❌ Failed to resolve subscription tier for userId={}", userId, e);
            return SubscriptionTier.FREE;
        }
    }

    private Long parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            log.warn("Invalid userId for subscription tier resolution: {}", userId);
            return null;
        }
    }

    private boolean isWithinActivePeriod(Subscription subscription, LocalDateTime now) {
        boolean withinStart = subscription.getStartDate() == null || !subscription.getStartDate().isAfter(now);
        boolean withinEnd = subscription.getEndDate() == null || !subscription.getEndDate().isBefore(now);
        return "ACTIVE".equalsIgnoreCase(subscription.getStatus()) && withinStart && withinEnd;
    }

    private SubscriptionTier preferHigherTier(SubscriptionTier current, SubscriptionTier next) {
        if (next == SubscriptionTier.PRO) {
            return SubscriptionTier.PRO;
        }
        if (next == SubscriptionTier.BASIC && current == SubscriptionTier.FREE) {
            return SubscriptionTier.BASIC;
        }
        return current;
    }
}