package com.courseservice.services;

import com.courseservice.dto.response.StreakResponse;
import com.courseservice.models.UserStreak;
import com.courseservice.repositories.UserStreakRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StreakServiceTest {

    @Mock UserStreakRepository userStreakRepository;

    @InjectMocks StreakService streakService;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    // --- recordActivity ---

    @Test
    void recordActivity_newUser_createsStreakOfOne() {
        when(userStreakRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(userStreakRepository.save(any(UserStreak.class))).thenAnswer(inv -> inv.getArgument(0));

        streakService.recordActivity(USER_ID);

        UserStreak saved = captureFirstSave();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getCurrentStreak()).isEqualTo(1);
        assertThat(saved.getLongestStreak()).isEqualTo(1);
        assertThat(saved.getLastActivityDate()).isEqualTo(TODAY);
    }

    @Test
    void recordActivity_sameDay_isIdempotent() {
        when(userStreakRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(buildStreak(TODAY, 3, 5)));

        streakService.recordActivity(USER_ID);

        verify(userStreakRepository, never()).save(any());
    }

    @Test
    void recordActivity_consecutiveDay_incrementsStreak() {
        when(userStreakRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(buildStreak(TODAY.minusDays(1), 4, 7)));
        when(userStreakRepository.save(any(UserStreak.class))).thenAnswer(inv -> inv.getArgument(0));

        streakService.recordActivity(USER_ID);

        UserStreak saved = captureFirstSave();
        assertThat(saved.getCurrentStreak()).isEqualTo(5);
        assertThat(saved.getLongestStreak()).isEqualTo(7); // still below longest
        assertThat(saved.getLastActivityDate()).isEqualTo(TODAY);
    }

    @Test
    void recordActivity_consecutiveDayBeatsLongest_updatesLongest() {
        when(userStreakRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(buildStreak(TODAY.minusDays(1), 7, 7)));
        when(userStreakRepository.save(any(UserStreak.class))).thenAnswer(inv -> inv.getArgument(0));

        streakService.recordActivity(USER_ID);

        UserStreak saved = captureFirstSave();
        assertThat(saved.getCurrentStreak()).isEqualTo(8);
        assertThat(saved.getLongestStreak()).isEqualTo(8);
    }

    @Test
    void recordActivity_missedOneDay_resetsToOne() {
        when(userStreakRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(buildStreak(TODAY.minusDays(2), 10, 15)));
        when(userStreakRepository.save(any(UserStreak.class))).thenAnswer(inv -> inv.getArgument(0));

        streakService.recordActivity(USER_ID);

        UserStreak saved = captureFirstSave();
        assertThat(saved.getCurrentStreak()).isEqualTo(1);
        assertThat(saved.getLongestStreak()).isEqualTo(15); // preserved
        assertThat(saved.getLastActivityDate()).isEqualTo(TODAY);
    }

    @Test
    void recordActivity_longGap_resetsToOne() {
        when(userStreakRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(buildStreak(TODAY.minusDays(30), 25, 25)));
        when(userStreakRepository.save(any(UserStreak.class))).thenAnswer(inv -> inv.getArgument(0));

        streakService.recordActivity(USER_ID);

        UserStreak saved = captureFirstSave();
        assertThat(saved.getCurrentStreak()).isEqualTo(1);
    }

    // --- getStreak ---

    @Test
    void getStreak_existingRecord_returnsCurrentStreakAndDate() {
        when(userStreakRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(buildStreak(TODAY, 5, 10)));

        StreakResponse response = streakService.getStreak(USER_ID);

        assertThat(response.currentStreak()).isEqualTo(5);
        assertThat(response.lastActivityDate()).isEqualTo(TODAY);
    }

    @Test
    void getStreak_noRecord_returnsZeroAndNullDate() {
        when(userStreakRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        StreakResponse response = streakService.getStreak(USER_ID);

        assertThat(response.currentStreak()).isEqualTo(0);
        assertThat(response.lastActivityDate()).isNull();
    }

    // --- helpers ---

    private UserStreak buildStreak(LocalDate lastActivityDate, int currentStreak, int longestStreak) {
        UserStreak s = new UserStreak();
        s.setId(UUID.randomUUID());
        s.setUserId(USER_ID);
        s.setCurrentStreak(currentStreak);
        s.setLongestStreak(longestStreak);
        s.setLastActivityDate(lastActivityDate);
        return s;
    }

    private UserStreak captureFirstSave() {
        ArgumentCaptor<UserStreak> captor = ArgumentCaptor.forClass(UserStreak.class);
        verify(userStreakRepository).save(captor.capture());
        return captor.getValue();
    }
}
