package br.com.joshua.loadgen.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class RateProfileTest {

    @Test
    void constantKeepsTheTargetRate() {
        var profile = new RateProfile(Scenario.CONSTANT, 100, 10);
        assertThat(profile.rateAt(0)).isEqualTo(100);
        assertThat(profile.rateAt(9.9)).isEqualTo(100);
    }

    @Test
    void rampUpGrowsLinearlyFromTenPercent() {
        var profile = new RateProfile(Scenario.RAMP_UP, 100, 10);
        assertThat(profile.rateAt(0)).isEqualTo(10);
        assertThat(profile.rateAt(5)).isCloseTo(55, within(1e-9));
        assertThat(profile.rateAt(10)).isEqualTo(100);
    }

    @Test
    void spikeJumpsBetweenFortyAndSixtyPercentOfDuration() {
        var profile = new RateProfile(Scenario.SPIKE, 100, 10);
        assertThat(profile.rateAt(1)).isEqualTo(20);
        assertThat(profile.rateAt(4)).isEqualTo(100);
        assertThat(profile.rateAt(5.9)).isEqualTo(100);
        assertThat(profile.rateAt(6)).isEqualTo(20);
    }

    @Test
    void lowRatesNeverDropBelowOneRequestPerSecond() {
        var profile = new RateProfile(Scenario.SPIKE, 2, 10);
        assertThat(profile.rateAt(0)).isEqualTo(1);
    }
}
