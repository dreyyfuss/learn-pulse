package com.courseservice.services;

import com.courseservice.dto.response.StreakResponse;
import com.courseservice.models.UserStreak;
import com.courseservice.repositories.UserStreakRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StreakService {

    private final UserStreakRepository userStreakRepository;

    @Transactional
    public void recordActivity(UUID userId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        UserStreak streak = userStreakRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserStreak s = new UserStreak();
                    s.setUserId(userId);
                    return s;
                });

        if (today.equals(streak.getLastActivityDate())) {
            return;
        }

        if (streak.getLastActivityDate() != null
                && streak.getLastActivityDate().plusDays(1).equals(today)) {
            streak.setCurrentStreak(streak.getCurrentStreak() + 1);
        } else {
            streak.setCurrentStreak(1);
        }

        streak.setLongestStreak(Math.max(streak.getLongestStreak(), streak.getCurrentStreak()));
        streak.setLastActivityDate(today);
        userStreakRepository.save(streak);
    }

    @Transactional(readOnly = true)
    public StreakResponse getStreak(UUID userId) {
        return userStreakRepository.findByUserId(userId)
                .map(s -> new StreakResponse(s.getCurrentStreak(), s.getLastActivityDate()))
                .orElse(new StreakResponse(0, null));
    }
}
