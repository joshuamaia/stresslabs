package br.com.joshua.stresslabsservice.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LocalAccessFilterTest {

    @ParameterizedTest
    @CsvSource({
            "127.0.0.1, true",
            "::1, true",
            "10.0.0.5, true",
            "172.18.0.4, true",
            "192.168.1.20, true",
            "fd00::1, true",
            "8.8.8.8, false",
            "200.150.10.1, false",
            "not-an-ip, false"
    })
    void acceptsOnlyLocalAndPrivateNetworks(String address, boolean expected) {
        assertThat(LocalAccessFilter.isLocal(address)).isEqualTo(expected);
    }
}
