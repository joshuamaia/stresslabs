package br.com.joshua.stresslabsservice.targetapi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import br.com.joshua.stresslabsservice.shared.domain.DomainValidationException;
import br.com.joshua.stresslabsservice.targetapi.domain.ProcessingWorkload.ProcessingResult;

class ProcessingWorkloadTest {

    @Test
    void executesRealDeterministicWork() {
        ProcessingResult first = ProcessingWorkload.execute(2);
        ProcessingResult second = ProcessingWorkload.execute(2);

        assertThat(first.completedUnits()).isEqualTo(2);
        assertThat(first.hashes()).isEqualTo(2L * ProcessingWorkload.HASHES_PER_UNIT);
        assertThat(first.digest()).hasSize(64).isEqualTo(second.digest());
        assertThat(first.truncated()).isFalse();
    }

    @Test
    void enforcesHardLimits() {
        assertThatThrownBy(() -> ProcessingWorkload.execute(0)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> ProcessingWorkload.execute(ProcessingWorkload.MAX_WORK_UNITS + 1))
                .isInstanceOf(DomainValidationException.class);
    }
}
